<a id="back-to-top"></a>

# Kiểm thử và lỗi

## Menu
- [Viết mẫu mã phải biên dịch và mẫu mã phải bị từ chối](#generics-testing-and-diagnostics-001)
- [Bật javac -Xlint:unchecked, -Xlint:rawtypes và -Xlint:varargs](#generics-testing-and-diagnostics-002)
- [Đọc lỗi incompatible types, capture và incompatible bounds](#generics-testing-and-diagnostics-003)
- [Kiểm thử ranh giới raw type, reflection và ép kiểu](#generics-testing-and-diagnostics-004)
- [Kiểm thử heap pollution và thời điểm phát sinh ClassCastException](#generics-testing-and-diagnostics-005)
- [Đối chiếu chữ ký generic, chữ ký đã xóa và bridge trong class file](#generics-testing-and-diagnostics-006)
- [Giới hạn @SuppressWarnings và yêu cầu giải thích tính an toàn](#generics-testing-and-diagnostics-007)
- [Chi phí boxing và vì sao generics không tự chuyên biệt hóa mã cho primitive](#generics-testing-and-diagnostics-008)

## <a id="generics-testing-and-diagnostics-001">Viết mẫu mã phải biên dịch và mẫu mã phải bị từ chối</a>

<details>
<summary>Xem chi tiết</summary>

# Viết mẫu mã phải biên dịch và mẫu mã phải bị từ chối — kiểm thử hợp đồng Java Generics

## 1. Mục tiêu

Một API generic tốt không chỉ chạy đúng với đầu vào hợp lệ, mà còn **ngăn caller viết những lời gọi sai kiểu ngay ở thời điểm biên dịch**. Vì vậy, khi kiểm thử generics, cần xây **hai nhóm mẫu mã đối chứng**:

- **Positive compilation test:** Đoạn mã sử dụng đúng hợp đồng phải được `javac` chấp nhận; nếu có `main` hoặc assertions thì chạy thêm để kiểm tra kết quả thực.
- **Negative compilation test:** Đoạn mã cố ý vi phạm một luật generic phải bị `javac` **từ chối**. Đây không phải test ném exception trong runtime: source sai không trở thành chương trình hợp lệ.
- **Warning/policy test:** Một số thao tác raw/unchecked chỉ phát **warning**, vẫn compile khi không bật `-Werror`. Cần tách khỏi những luật cấm do ngôn ngữ.
- **Runtime test:** Những vấn đề như kiểm tra dữ liệu từ legacy API, `Class.cast` hoặc heap pollution có thể chỉ bộc lộ khi chạy; phải viết test riêng.

Bài này thuộc **Java Core – Generics / Kiểm thử và lỗi**. Các chương trình đầy đủ dưới đây đã được kiểm chứng với **Java 17**, `javac --release 17 -Xlint:all -Werror` và `java`. Bộ harness dùng `javax.tools.JavaCompiler` đã xác nhận **2 mẫu được chấp nhận, 6 mẫu bị từ chối**; các ví dụ raw type cũng được kiểm tra riêng với và không có `-Werror`.

Sau khi hoàn thành, bạn sẽ biết chọn kiểu test đúng, tạo cặp code phải compile / không được compile cho invariance, wildcards, bounds, generic arrays, erasure, type variables; kiểm tra exit code/diagnostics; tự động hóa bằng Compiler API; và tránh false positive trong CI.

## 2. Bốn loại kết quả cần phân biệt

| Ví dụ | Kết quả biên dịch | Khi chạy | Loại test |
| --- | --- | --- | --- |
| `List<String>` thêm chuỗi | Thành công | Bình thường | Positive compilation + runtime |
| `List<String>` gán cho `List<Object>` | **Lỗi biên dịch** | Không chạy | Negative compilation |
| Thêm chuỗi qua `List<?>` | **Lỗi biên dịch** | Không chạy | Negative compilation |
| `new List<String>[2]` | **Lỗi biên dịch** | Không chạy | Negative compilation |
| Raw `List` rồi `raw.add(42)` | Có thể thành công **kèm warning** | Có thể gây heap pollution | Warning + runtime |
| Raw `List` và bật `-Werror` | Build không thành công **do warning** | Không chạy binary của build mới | Policy test |
| `String.class.cast(42)` | Thành công | `ClassCastException` | Runtime rejection |
| Adapter nhận `List<?>` và kiểm tra phần tử | Thành công | Có thể báo lỗi nghiệp vụ | Runtime validation |

**Không được dùng lẫn thuật ngữ:** `javac` từ chối do type-checking khác với Java code được chấp nhận rồi ném `ClassCastException` khi chạy. `-Werror` còn là **chính sách build**, không biến raw type thành cú pháp bị cấm tuyệt đối.

## 3. Tiêu chí PASS/FAIL của một compilation test

**Mã được phép biên dịch** phải thỏa:

1. Đúng tên tệp/lớp, imports, classpath, target Java version và các dependencies cần thiết.
2. `javac` trả **exit code 0**.
3. Nếu dự án yêu cầu sạch warning, biên dịch với `-Xlint:all -Werror` và vẫn thành công.
4. Với chương trình runnable, `java` trả 0 và output/assertions đúng kỳ vọng.
5. Không dựa trên class files của lần compile trước đã tồn tại.

**Mã bắt buộc bị từ chối** phải thỏa:

1. Một fixture chỉ cố ý chứa **một lỗi generic cốt lõi** (hoặc tập lỗi được mô tả rõ).
2. Dù đã cung cấp đúng imports, classpath và Java baseline, `javac` trả **exit code khác 0**.
3. Có diagnostics mức **ERROR**.
4. Lỗi liên quan đến **quy tắc generic dự định kiểm tra**, không phải lỗi tên file, package hay thiếu dependency.
5. Không cố chạy fixture sau khi compile thất bại.

Với JavaCompiler API, kết quả `task.call()` là `true` khi compile thành công và `false` khi không thành công. Một negative test nên xác minh thêm `Diagnostic.Kind.ERROR`, không chỉ tìm chuỗi "error" trong console.

## 4. Cặp 1 — Invariance của generic container

**Phải được chấp nhận:**

```java
import java.util.List;

class ValidWildcard {
    static Object first(List<?> items) {
        return items.get(0);
    }

    static void run() {
        List<String> words = List.of("Java");
        List<?> values = words;
        Object item = first(values);
    }
}
```

**Phải bị từ chối:**

```java
import java.util.List;

class InvalidInvariant {
    void run() {
        List<String> words = List.of("Java");
        List<Object> objects = words;
    }
}
```

Một `String` là một `Object`, nhưng `List<String>` **không** là subtype của `List<Object>`. Nếu có thể gán, caller có thể thêm một `Integer` qua `objects`, làm hỏng danh sách vốn được tin là chỉ chứa chuỗi. `List<?>` là lựa chọn phù hợp khi chỉ cần đọc phần tử mà không đặt tên kiểu element cụ thể.

Đây là một ví dụ **negative language rule**, không phải warning hay runtime exception.

## 5. Cặp 2 — Không được ghi giá trị tùy ý vào `List<?>`

**Phải biên dịch:**

```java
import java.util.List;

class ValidRead {
    static int count(List<?> items) {
        return items.size();
    }
}
```

**Phải bị từ chối:**

```java
import java.util.List;

class InvalidWildcardWrite {
    static void add(List<?> items) {
        items.add("abc");
    }
}
```

Ở đây compiler không biết `?` có phải `String` hay không. Nó có thể là `List<Integer>` hoặc `List<Number>`, nên thêm `"abc"` không được chứng minh an toàn. Chẩn đoán của `javac` có thể nhắc đến **capture of ?** / `CAP#1`.

Đừng hiểu `List<?>` là immutable tuyệt đối: những method như `clear` có thể compile; nếu list là mutable, chúng vẫn có thể sửa list. Wildcard hạn chế **loại phần tử được phép ghi qua tham chiếu ấy**, không tự cấm tất cả mutation.

## 6. Cặp 3 — Giới hạn kiểu `T extends Number`

**Phải biên dịch:**

```java
class ValidBound {
    static <T extends Number> double number(T value) {
        return value.doubleValue();
    }

    static void run() {
        double x = number(Integer.valueOf(7));
    }
}
```

**Phải bị từ chối:**

```java
class InvalidBound {
    static <T extends Number> void take(T value) {}

    void run() {
        take("x");
    }
}
```

`Integer` thỏa bound `Number`; `String` không thỏa. Những lỗi ở đây xuất hiện khi giải các ràng buộc type inference. Không cần chạy method để biết một `String` không phù hợp với tham số kiểu bị giới hạn bởi `Number`.

## 7. Cặp 4 — Generic array creation và reifiability

**Phải biên dịch:**

```java
import java.util.List;

class ValidWildcardArray {
    List<?>[] groups = new List<?>[2];
}
```

**Phải bị từ chối:**

```java
import java.util.List;

class InvalidGenericArray {
    List<String>[] groups = new List<String>[2];
}
```

Mảng Java có runtime component type; `List<String>` là non-reifiable do type erasure nên không được dùng làm component khi tạo mảng bằng cú pháp này. `List<?>[]` là dạng hợp lệ, nhưng **không khẳng định** mọi list trong mảng chứa chuỗi.

Nếu một generic varargs array xuất hiện trong Java source, compiler có thể cảnh báo về **possible heap pollution** thay vì từ chối ngay toàn bộ method; đó là một kịch bản warning riêng, không nên trộn với luật `new List<String>[2]`.

## 8. Cặp 5 — Erasure làm hai overload đụng nhau

**Phải biên dịch:**

```java
import java.util.List;

class ValidNames {
    void showStrings(List<String> items) {}
    void showNumbers(List<Integer> items) {}
}
```

**Phải bị từ chối:**

```java
import java.util.List;

class InvalidErasureOverload {
    void show(List<String> items) {}
    void show(List<Integer> items) {}
}
```

Generic arguments bị xóa trong method descriptor. Hai overload tên `show` với tham số erased đều là `List` không thể tạo hai chữ ký khác nhau ở JVM; `javac` từ chối do name clash. Đổi tên method hoặc dùng các tham số thực sự phân biệt về erased types thay vì mong runtime phân biệt `List<String>` và `List<Integer>`.

## 9. Cặp 6 — `T.class` không được phép với biến kiểu generic

**Phải biên dịch:**

```java
class ValidTypeToken {
    static <T> boolean matches(Object value, Class<T> type) {
        return type.isInstance(value);
    }
}
```

**Phải bị từ chối:**

```java
class InvalidTypeVariableClass<T> {
    Class<?> token() {
        return T.class;
    }
}
```

`T` trong generic class không là một runtime class literal. Muốn kiểm tra class ở runtime, hãy **truyền token `Class<T>`** vào API thay vì tưởng rằng compiler luôn giữ lại actual type arguments của mọi object.

## 10. Positive runnable demo — nhiều hợp đồng generic trong một chương trình

Tạo tệp **`GenericsContractDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class GenericsContractDemo {
    static <T> void copy(
            List<? extends T> source,
            List<? super T> target) {
        for (T item : source) target.add(item);
    }

    static double sum(List<? extends Number> numbers) {
        double result = 0;
        for (Number n : numbers) result += n.doubleValue();
        return result;
    }

    static void swapAny(List<?> items, int i, int j) {
        swapCaptured(items, i, j);
    }

    private static <T> void swapCaptured(
            List<T> items, int i, int j) {
        T old = items.get(i);
        items.set(i, items.get(j));
        items.set(j, old);
    }

    static <T extends Comparable<? super T>> T maximum(
            List<? extends T> items) {
        if (items.isEmpty())
            throw new IllegalArgumentException("empty");
        T result = items.get(0);
        for (T item : items)
            if (item.compareTo(result) > 0) result = item;
        return result;
    }

    static <T, R> List<R> map(
            List<? extends T> items,
            Function<? super T, ? extends R> mapper) {
        List<R> result = new ArrayList<>();
        for (T item : items) result.add(mapper.apply(item));
        return List.copyOf(result);
    }

    public static void main(String[] args) {
        List<Integer> numbers = List.of(5, 8);
        List<Number> output = new ArrayList<>();
        copy(numbers, output);
        System.out.println("copied: " + output);
        System.out.println("sum: " + sum(numbers));

        List<String> words =
                new ArrayList<>(List.of("left", "right"));
        swapAny(words, 0, 1);
        System.out.println("swapped: " + words);
        System.out.println("maximum: "
                + maximum(List.of("apple", "pear")));

        Function<CharSequence, StringBuilder> upper =
                s -> new StringBuilder(
                        s.toString().toUpperCase(java.util.Locale.ROOT));
        List<CharSequence> result = GenericsContractDemo
                .<CharSequence, CharSequence>map(
                        List.of("java", "type"), upper);
        System.out.println("mapped: " + result);
    }
}
```

Chạy:

```bash
javac --release 17 -Xlint:all -Werror GenericsContractDemo.java
java GenericsContractDemo
```

**Stdout đã xác minh:**

```text
copied: [5, 8]
sum: 13.0
swapped: [right, left]
maximum: pear
mapped: [JAVA, TYPE]
```

Các khía cạnh được bao phủ:

- `copy(List<? extends T>, List<? super T>)`: source sản xuất `T`, destination tiêu thụ `T` (**PECS**).
- `sum(List<? extends Number>)`: chấp nhận `List<Integer>` khi chỉ cần đọc như `Number`.
- `swapAny(List<?>)` + private `swapCaptured(List<T>)`: compiler capture một element type chưa biết nhưng thống nhất để hoán đổi dữ liệu của **cùng list**.
- `maximum`: bound `T extends Comparable<? super T>` cho phép so sánh và giữ kiểu phần tử trong return type.
- `map`: callback nhận supertype của `T` và tạo subtype của `R`; ví dụ function nhận `CharSequence`, trả `StringBuilder` trong một lời gọi yêu cầu kết quả `CharSequence`.

Đây là **positive compile test** có ích vì đã thử cả kiểu con/kiểu cha và wildcard capture thay vì chỉ chấp nhận một trường hợp `List<String>` đơn giản.

Không kết luận từ đó rằng tất cả list mutable. `swapAny` được thử trên `ArrayList`; nếu dùng list bất biến từ `List.of(...)`, phép `set` có thể ném `UnsupportedOperationException` dù type checking hợp lệ.

## 11. Runtime demo — kiểm tra dữ liệu không nằm trong tầm nhìn compiler

Tạo tệp **`CheckedBoundaryDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CheckedBoundaryDemo {
    static <T> List<T> checkedCopy(
            List<?> source, Class<T> token) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(token, "token");

        List<T> result = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            Object value = source.get(i);
            if (!token.isInstance(value)) {
                throw new IllegalArgumentException(
                        "index=" + i);
            }
            result.add(token.cast(value));
        }
        return List.copyOf(result);
    }

    public static void main(String[] args) {
        System.out.println("accepted: "
                + checkedCopy(
                        List.of("An", "Binh"), String.class));
        try {
            checkedCopy(List.of("An", 42), String.class);
        } catch (IllegalArgumentException ex) {
            System.out.println("rejected at runtime: "
                    + ex.getMessage());
        }
        System.out.println("list token not enough: "
                + List.class.isInstance(List.of(42)));
    }
}
```

Lệnh:

```bash
javac --release 17 -Xlint:all -Werror CheckedBoundaryDemo.java
java CheckedBoundaryDemo
```

**Stdout đã xác minh:**

```text
accepted: [An, Binh]
rejected at runtime: index=1
list token not enough: true
```

Đây là ví dụ **compile thành công**, sau đó **chủ động từ chối dữ liệu sai ở runtime** bằng `IllegalArgumentException`. Nó không phải negative compilation test.

`List.class` không giữ type argument `String` ở runtime. Một `List<Integer>` vẫn thỏa `List.class.isInstance(...)`. Muốn phát hành một `List<String>` đáng tin từ `List<?>` lấy ở ranh giới dữ liệu, cần duyệt kiểm tra từng phần tử và tạo danh sách typed mới như ví dụ.

## 12. Viết các mẫu âm thành những fixture độc lập

Một cách tổ chức hợp lý:

```text
project/
  src/main/java/                   # Production code: phải compile
  src/test/java/                   # Harness, JUnit test: phải compile
  src/test/resources/
    compile-positive/
      ValidWildcard.java
      ValidBound.java
    compile-negative/
      InvalidInvariant.java
      InvalidWildcardWrite.java
      InvalidBound.java
      InvalidGenericArray.java
      InvalidErasureOverload.java
      InvalidTypeVariableClass.java
```

Các tên thư mục là gợi ý, không phải quy định của Gradle/Maven. Quan trọng nhất: **không để các tệp cố ý không compile nằm trong một source set mà compiler build chính luôn xử lý**. Thay vào đó, một compiler harness đọc fixtures và compile mỗi fixture riêng.

Khi đặt tên fixture, chọn tên luật đang bị vi phạm thay vì `Bad1`, `Bad2`. Mỗi negative fixture nên có **positive counterpart**, để chứng minh môi trường compile không bị lỗi trước khi đi tới quy tắc kiểm thử.

## 13. Java Compiler API — một harness chạy cả mẫu dương và âm

JDK cung cấp `javax.tools.JavaCompiler`. API này có thể biên dịch những source strings trong bộ nhớ, trả kết quả thành công/thất bại và cung cấp diagnostics. Mẫu dưới đây là **một chương trình độc lập**, đã chạy thực tế với Java 17.

Tạo tệp **`CompileContractHarness.java`**:

```java
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class CompileContractHarness {
    record Case(String name, String source, boolean shouldCompile) {}

    static final class Source extends SimpleJavaFileObject {
        private final String code;

        Source(String className, String code) {
            super(URI.create("string:///" + className
                    + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(
                boolean ignoreEncodingErrors) {
            return code;
        }
    }

    static void check(Case test, Path output) throws IOException {
        JavaCompiler compiler =
                ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("A JDK is required");
        }

        DiagnosticCollector<JavaFileObject> diagnostics =
                new DiagnosticCollector<>();

        try (StandardJavaFileManager manager =
                compiler.getStandardFileManager(
                        diagnostics, null, null)) {

            List<String> options = List.of(
                    "--release", "17",
                    "-Xlint:all", "-Werror", "-proc:none",
                    "-d", output.toString());

            JavaCompiler.CompilationTask task =
                    compiler.getTask(null, manager, diagnostics,
                            options, null,
                            List.of(new Source(
                                    test.name(), test.source())));

            boolean actual = Boolean.TRUE.equals(task.call());
            long errors = diagnostics.getDiagnostics().stream()
                    .filter(d ->
                            d.getKind() == Diagnostic.Kind.ERROR)
                    .count();

            if (actual != test.shouldCompile()
                    || (!test.shouldCompile() && errors == 0)) {
                throw new AssertionError(
                        "Unexpected compile result: " + test.name());
            }

            System.out.println(
                    (actual ? "COMPILES: " : "REJECTED: ")
                    + test.name());
        }
    }

    public static void main(String[] args) throws IOException {
        Path classes = Files.createTempDirectory(
                "generic-compile-contracts");
        List<Case> cases = List.of(
            new Case("ValidWildcard",
                    "class ValidWildcard {"
                    + " static int size(java.util.List<?> x)"
                    + " { return x.size(); } }",
                    true),
            new Case("ValidBound",
                    "class ValidBound {"
                    + " static <T extends Number> double x(T n)"
                    + " { return n.doubleValue(); } }",
                    true),
            new Case("InvalidInvariant",
                    "class InvalidInvariant { void f() {"
                    + " java.util.List<String> a="
                    + "java.util.List.of(\"x\");"
                    + " java.util.List<Object> b=a; } }",
                    false),
            new Case("InvalidWildcardWrite",
                    "class InvalidWildcardWrite {"
                    + " void f(java.util.List<?> a)"
                    + " { a.add(\"x\"); } }",
                    false),
            new Case("InvalidBound",
                    "class InvalidBound {"
                    + " static <T extends Number> void take(T x) {}"
                    + " void f() { take(\"x\"); } }",
                    false),
            new Case("InvalidGenericArray",
                    "class InvalidGenericArray {"
                    + " java.util.List<String>[] xs ="
                    + " new java.util.List<String>[2]; }",
                    false),
            new Case("InvalidErasureOverload",
                    "class InvalidErasureOverload {"
                    + " void f(java.util.List<String> x) {}"
                    + " void f(java.util.List<Integer> x) {} }",
                    false),
            new Case("InvalidTypeVariableClass",
                    "class InvalidTypeVariableClass<T> {"
                    + " Class<?> token() { return T.class; } }",
                    false)
        );

        for (Case test : cases) check(test, classes);
        System.out.println(
                "ALL COMPILE CONTRACTS PASSED: " + cases.size());
    }
}
```

Chạy:

```bash
javac --release 17 -Xlint:all -Werror CompileContractHarness.java
java CompileContractHarness
```

**Stdout đã kiểm chứng:**

```text
COMPILES: ValidWildcard
COMPILES: ValidBound
REJECTED: InvalidInvariant
REJECTED: InvalidWildcardWrite
REJECTED: InvalidBound
REJECTED: InvalidGenericArray
REJECTED: InvalidErasureOverload
REJECTED: InvalidTypeVariableClass
ALL COMPILE CONTRACTS PASSED: 8
```

Giải thích từng thành phần:

- `Source extends SimpleJavaFileObject` tạo một Java source trong bộ nhớ với URI `string:///TênLớp.java`. Tên của source và tên class nhất quán để lỗi về tên file không làm test âm thành công giả.
- `JavaCompiler.getTask` thực hiện một lần compile riêng cho từng case. Mỗi case dùng `DiagnosticCollector` riêng để không lẫn lỗi cũ.
- `--release 17` khóa mức Java source và platform APIs được compiler sử dụng theo baseline Java 17.
- `-Xlint:all -Werror` áp dụng chính sách sạch warning cho mẫu dương; với mẫu âm, vẫn cần kiểm tra nguyên nhân thất bại.
- `-proc:none` tắt annotation processing tránh phụ thuộc ngoài ý muốn. Nếu dự án cần processors, cấu hình một compile-testing task thích hợp thay vì tắt cơ học.
- `-d` đưa output classes vào thư mục tạm thay vì ghi đè production/test classes. Mẫu demo không dọn thư mục tạm; trong test framework thực nên quản lý vòng đời bằng cleanup.
- `getSystemJavaCompiler()` có thể trả `null` khi không chạy trên môi trường JDK có compiler; harness báo lỗi rõ ràng trong trường hợp đó.

**Giới hạn của harness:** một negative compile test có `ERROR` **chưa đủ xác nhận luật mục tiêu** nếu code cũng có lỗi khác. Bộ fixture đã dùng rất nhỏ, độc lập và được kiểm thử, nhưng trong CI nghiêm ngặt còn nên xác minh chẩn đoán gắn với biểu thức cần kiểm tra (file/dòng/mã lỗi phù hợp) và luôn có positive counterpart. Không nên so khớp cứng **toàn bộ nội dung tiếng Anh** của error message vì nó có thể khác JDK/locale.

## 14. Cảnh báo raw type không phải lỗi biên dịch bắt buộc

Tạo tệp **`RawWarningDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class RawWarningDemo {
    public static void main(String[] args) {
        List<String> typed = new ArrayList<>();
        List raw = typed;
        raw.add(123);

        System.out.println("stored: " + typed);
        try {
            String value = typed.get(0);
            System.out.println(value);
        } catch (ClassCastException ex) {
            System.out.println("runtime: "
                    + ex.getClass().getSimpleName());
        }
    }
}
```

**Không bật `-Werror`:**

```bash
javac -Xlint:rawtypes -Xlint:unchecked RawWarningDemo.java
java RawWarningDemo
```

`javac` đã được kiểm chứng trả exit code **0** nhưng phát **hai warnings**: `[rawtypes]` ở `List raw` và `[unchecked]` ở `raw.add(123)`.

Stdout chương trình:

```text
stored: [123]
runtime: ClassCastException
```

Raw alias và typed alias tham chiếu cùng một `ArrayList`, nhưng raw alias chèn `Integer`; khi đọc như `String`, runtime cast bị lỗi. Đây là bằng chứng về **heap pollution và hậu quả runtime**, không phải bằng chứng Java compiler cấm raw type.

**Bật `-Werror`:**

```bash
javac -Xlint:rawtypes -Xlint:unchecked -Werror RawWarningDemo.java
```

Ở lượt kiểm chứng, lệnh trả exit code **1** và có thông báo:

```text
error: warnings found and -Werror specified
```

Đây là **policy rejection**. Để kiểm thử đúng, ghi rõ compiler flags và tách case raw warning khỏi negative tests của invariance/generic array/bounds.

## 15. Kiểm tra exit code đúng cách trong terminal

### Bash

```bash
# Chương trình hợp lệ: muốn javac thành công.
if ! javac --release 17 -Xlint:all -Werror GenericsContractDemo.java; then
  echo "FAIL: valid code did not compile"
  exit 1
fi
java GenericsContractDemo || exit 1

# Chương trình cố ý sai: muốn javac thất bại.
if javac --release 17 InvalidInvariant.java; then
  echo "FAIL: invalid code unexpectedly compiled"
  exit 1
else
  echo "PASS: invalid code was rejected"
fi
```

### Windows PowerShell

```powershell
javac --release 17 -Xlint:all -Werror GenericsContractDemo.java
if ($LASTEXITCODE -ne 0) {
    throw "Valid code unexpectedly failed"
}
java GenericsContractDemo
if ($LASTEXITCODE -ne 0) {
    throw "Valid program unexpectedly failed"
}

javac --release 17 InvalidInvariant.java
if ($LASTEXITCODE -eq 0) {
    throw "Invalid code unexpectedly compiled"
}
Write-Output "PASS: invalid source rejected"
```

Ở PowerShell 5.1, một external command trả nonzero **không phải lúc nào** khiến toàn bộ script tự dừng; cần đọc `$LASTEXITCODE` ngay sau lệnh. Đừng tiếp tục chạy `java` sau một lần compile thất bại, vì có thể còn class file cũ.

Trong pipeline thực, tốt nhất dùng output folder riêng/sạch cho từng case, quản lý classpath theo fixture và xóa artifacts sau test. Dù test manual hay automated, **sự thành công của shell script** không có giá trị nếu nó vô tình bỏ qua exit code của `javac`.

## 16. Phân biệt cảnh báo và chẩn đoán trong compiler API

Compiler trả những loại diagnostics khác nhau, ví dụ:

- `Diagnostic.Kind.ERROR` — lỗi khiến source không được chấp nhận theo luật được áp dụng.
- `Diagnostic.Kind.WARNING` / `MANDATORY_WARNING` — cảnh báo về nguy cơ/chính sách.
- `Diagnostic.Kind.NOTE` — thông tin giải thích thêm.

Trong một negative test, hãy kiểm tra `ERROR`; nhưng nếu đang bật `-Werror`, có thể nhận lỗi tổng quát do warning policy. Nếu mục tiêu là chứng minh **Java language type rule**, có thể chạy fixture đó **không có `-Werror`** để tách policy khỏi type-system rejection.

Các tùy chọn `-Xdiags:verbose` và `-Xlint` có ích trong lúc điều tra lỗi, nhưng chuỗi text có thể thay đổi giữa compiler/version. Với test dạy kiến thức, mô tả **bản chất** và minh họa excerpt là đủ. Với compiler regression test thực sự, cần khóa toolchain và chọn mức chẩn đoán phù hợp.

## 17. Cách ngăn false-positive negative tests

**Sai lầm 1 — Lỗi do thiếu import hoặc tên file:** `javac` trả nonzero nhưng chẳng chứng minh điều gì về generics. Dùng fully qualified names cho fixture in-memory hoặc imports đúng; đặt tên file/class nhất quán.

**Sai lầm 2 — Một file chứa nhiều lỗi không liên quan:** chỉ cần một lỗi phụ cũng khiến case bị từ chối. Tách thành từng nguồn nhỏ, tương ứng từng luật.

**Sai lầm 3 — Không có positive control:** nếu một file hợp lệ cùng dependencies cũng không compile được, nguyên nhân có thể là cấu hình JDK/classpath chứ không phải luật generic.

**Sai lầm 4 — So khớp stdout hoặc message đúng từng ký tự:** compiler diagnostics có khác biệt về đường dẫn, locale, line numbers và cách diễn đạt. Ưu tiên `Kind.ERROR` và điều kiện vị trí/nội dung được chuẩn hóa.

**Sai lầm 5 — Nhầm warning-as-error với lỗi ngôn ngữ:** raw types có thể compile khi tắt `-Werror`. Test policy cần tên và flags khác test negative compile.

**Sai lầm 6 — Chạy class cũ sau compile thất bại:** dùng output folder tạm riêng và chỉ chạy positive binary nếu compile hiện tại thành công.

**Sai lầm 7 — Dùng unchecked cast để ép positive example compile:** làm thế có thể che lỗi cần phát hiện, đẩy `ClassCastException` sang runtime. Mẫu dương nên sạch raw/unchecked khi có thể.

**Sai lầm 8 — Chỉ kiểm tra source đúng một phiên bản JDK:** các đặc tính ngôn ngữ và thông báo lỗi có thể thay đổi. Dùng `--release`/toolchains và ghi rõ baseline.

**Sai lầm 9 — Coi một runtime `ClassCastException` là compile-time rejection:** chúng thuộc hai tầng kiểm thử; cần một fixture âm độc lập cho luật type checker.

**Sai lầm 10 — Không thử subtypes/supertypes:** một generic API có thể bác bỏ caller hợp lệ do chữ ký quá chặt. Positive test với `List<Integer>` → `List<Number>` và `Function<CharSequence,StringBuilder>` là bài kiểm tra hữu ích về độ linh hoạt.

**Sai lầm 11 — Negative test đúng luật nhưng đặt trong production source set:** làm hỏng build toàn project. Dùng test/resources hoặc compiler-in-memory fixtures.

**Sai lầm 12 — Kiểm thử fail với `-Werror` nhưng không đọc diagnostics:** có thể lỗi bắt nguồn từ một warning không liên quan. Với regression nghiêm túc, ghi nhận nguyên nhân cụ thể.

## 18. Quan sát tác động của erasure với `javap`

Sau khi compile chương trình dương, chạy:

```bash
javap -p -s GenericsContractDemo
```

Trích đoạn đã kiểm chứng:

```text
static <T> void copy(
    java.util.List<? extends T>, java.util.List<? super T>);
  descriptor: (Ljava/util/List;Ljava/util/List;)V

static double sum(java.util.List<? extends java.lang.Number>);
  descriptor: (Ljava/util/List;)D

static <T extends java.lang.Comparable<? super T>>
    T maximum(java.util.List<? extends T>);
  descriptor: (Ljava/util/List;)Ljava/lang/Comparable;

static <T, R> java.util.List<R> map(
    java.util.List<? extends T>,
    java.util.function.Function<? super T, ? extends R>);
  descriptor: (Ljava/util/List;Ljava/util/function/Function;)Ljava/util/List;
```

`javap` trình bày generic signature khi metadata có sẵn, còn JVM descriptor sử dụng erased types: `List`, `Function`, `Comparable`. Đây là cơ sở giải thích vì sao hai overload chỉ khác `List<String>` và `List<Integer>` có cùng erased signature.

**Không suy luận rằng JVM descriptors giống nhau tức mọi hợp đồng generic đều giống nhau:** compiler vẫn kiểm tra wildcard/bounds ở source trước khi tạo bytecode. `javap -v` có thể hiển thị thuộc tính `Signature` khi cần nghiên cứu sâu hơn.

## 19. Bổ sung regression tests khi sửa API công khai

Nếu thay một chữ ký:

```java
// Ban đầu:
static <T> void copy(List<T> source, List<T> target)

// Linh hoạt hơn:
static <T> void copy(
        List<? extends T> source,
        List<? super T> target)
```

hãy kiểm thử **cả hướng dương và hướng âm**:

1. Caller mới hợp lệ (ví dụ `List<Integer>` → `List<Number>`) có biên dịch không?
2. Caller sai kiểu thật sự (ví dụ nguồn chuỗi sang đích số) có vẫn bị chặn không?
3. Các method references, overloaded calls, inference với lambda có còn compile khi sử dụng public API không?
4. Với ABI đã phát hành, các erased descriptors/bridge methods có đổi không?
5. Framework đọc generic `Signature` bằng reflection có phụ thuộc vào chữ ký trước không?

Đôi khi refactor vẫn giữ descriptor erased nhưng **thay đổi source-level contract**, nên không nên chỉ kiểm tra binary link hoặc runtime unit tests. Một suite compiler contracts giúp bạn nhìn thấy những lời gọi sai đột nhiên được chấp nhận, hay những lời gọi hợp lệ bị từ chối sau khi chỉnh wildcard/bounds.

## 20. Khi nào dùng Compiler API, khi nào dùng `javac` CLI?

| Tình huống | Công cụ phù hợp | Lý do |
| --- | --- | --- |
| Học một quy tắc, chỉ 1–2 snippets | `javac` CLI | Dễ quan sát lỗi ngay |
| Nhiều positive/negative fixtures trong CI | `JavaCompiler` hoặc compile-testing framework | Tự động hóa, thu diagnostics có cấu trúc |
| Kiểm tra chương trình chạy và stdout | `javac` + `java` hoặc test framework | Compilation thôi chưa đo được behavior |
| Enforce zero warnings | `-Xlint:all -Werror` theo policy | Phân biệt lint policy với language constraints |
| Kiểm tra API baseline | `--release` và toolchain | Tránh phụ thuộc tính năng của JDK mới hơn |
| Kiểm tra overloads và erasure | `javac` + `javap` | Xem cả type-checking và erased descriptor |
| Kiểm tra dữ liệu runtime từ legacy | Unit/integration test + validation | Compiler không biết dữ liệu thật |

Đối với chương trình kiểm thử dương, có thể dùng JUnit nếu project đã có. Những đoạn minh họa `@Test` đòi imports và dependencies JUnit, nên không được quảng bá như source độc lập chạy chỉ với JDK. Harness `CompileContractHarness` ở trên **không cần JUnit**.

## 21. Bài tập và đáp án

### Bài 1 — Phân loại lỗi

`List<String> names = List.of("a"); List<Object> target = names;` thuộc loại test nào?

**Đáp án:** Negative compilation test do invariance. Nó phải bị `javac` từ chối trước runtime.

### Bài 2 — Wildcard

Vì sao `List<?>` đọc được một `Object` nhưng `list.add("x")` bị từ chối?

**Đáp án:** kiểu phần tử không được đặt tên; khi đọc có thể dùng `Object`, khi thêm `String` không chắc hợp element type thực.

### Bài 3 — Bounded generic

`take(Integer.valueOf(5))` hợp lệ còn `take("5")` bị từ chối khi `take` khai báo `<T extends Number>`, đúng không?

**Đáp án:** đúng. `Integer` là subtype `Number` còn `String` không phải.

### Bài 4 — Array generic

`new List<String>[1]` và `new List<?>[1]` có cùng kết quả compile không?

**Đáp án:** không. Loại đầu bị từ chối vì generic array creation; unbounded wildcard array type là hợp lệ.

### Bài 5 — Chạy negative fixture?

Một fixture không compile được. Có nên chạy `java Fixture` rồi kiểm tra nó ném exception?

**Đáp án:** không. Test kiểm tra compiler result và diagnostics; không chạy chương trình không hợp lệ. Một class file cũ không thuộc lần compile đó cũng không có giá trị.

### Bài 6 — Raw warning

`List raw = ...; raw.add(42);` phát warning với `-Xlint` nhưng compile thành công. Có được gọi là “mẫu phải bị từ chối bởi ngôn ngữ” không?

**Đáp án:** không. Với `-Werror` đó là policy rejection; nếu không có flag, compiler có thể tạo bytecode. Có thể kiểm tra hậu quả runtime riêng.

### Bài 7 — Lỗi wrong reason

Một test “phải fail vì invariance” lại fail do thiếu classpath. Test PASS chứ?

**Đáp án:** không; phải sửa setup và kiểm tra lỗi đúng mục tiêu. Đây là lý do cần negative fixture tối giản và positive counterpart.

### Bài 8 — JavaCompiler diagnostics

`CompilationTask.call()` trả `false`. Đã đủ chứng minh generic bound test đúng chưa?

**Đáp án:** chưa. Cần xác minh ít nhất một diagnostic ERROR và nguyên nhân liên quan tới bound, không phải lỗi khác.

### Bài 9 — Type erasure

Hai overload `f(List<String>)` và `f(List<Integer>)` có thể cùng tồn tại không?

**Đáp án:** không, vì erasure làm parameter signatures đụng nhau.

### Bài 10 — Runtime list validation

`List.class.isInstance(List.of(42))` trả gì? Nó có chứng minh input là `List<String>` không?

**Đáp án:** trả `true`; không chứng minh element type `String`. Cần kiểm tra từng phần tử nếu dữ liệu không đáng tin.

### Bài 11 — Bài test về API PECS

Một method có `List<? extends T> source` và `List<? super T> destination`, dùng `List<Integer>` làm nguồn, `List<Number>` làm đích có hợp lệ không?

**Đáp án:** hợp lệ. Source cung cấp `Integer` như `T`, destination chấp nhận `T` hoặc supertype.

### Bài 12 — Mục tiêu `-Werror`

Nếu muốn zero warnings trên JDK 17, nên dùng lệnh nào?

**Đáp án:** `javac --release 17 -Xlint:all -Werror File.java` và kiểm tra exit code 0. Chỉ thêm `-Werror` khi đó là chính sách phù hợp, không coi tất cả warnings là lỗi cú pháp của Java.

## 22. Checklist cuối bài

- [ ] Mỗi luật có một **positive fixture** và một **negative fixture** liên quan.
- [ ] Mẫu âm thất bại vì **đúng quy tắc**; không vì lỗi import, tên class/file hay classpath.
- [ ] Mẫu dương biên dịch bằng JDK baseline và không có unchecked/raw warning ngoài chủ đích.
- [ ] Mọi lệnh `javac`/`java` đều được kiểm tra exit code.
- [ ] Không chạy một `.class` cũ sau lần compile thất bại.
- [ ] Warning-as-error được phân biệt với language rejection.
- [ ] Runtime validation được kiểm tra riêng với compile-time constraints.
- [ ] Nếu dùng `JavaCompiler`, kiểm tra `Diagnostic.Kind.ERROR` cho negative cases.
- [ ] Fixtures cố ý sai không xuất hiện trong source set production.
- [ ] Generic signatures được đánh giá với subtype/supertype, wildcard và bounds phù hợp.
- [ ] Output của các chương trình được đối chiếu và không bị suy đoán.
- [ ] Khi thay generic API, rerun cả dương và âm để bắt regression.

## Kết luận

**Viết mã mẫu phải biên dịch và mã mẫu phải bị từ chối** là cách kiểm thử đúng bản chất nhất của Java Generics: những lời gọi đáp ứng hợp đồng được chấp nhận, còn những lời gọi không type-safe bị ngăn ở compile time. Hãy tạo cặp đối chứng, kiểm tra exit code và diagnostics, cô lập đúng lý do compiler từ chối, và ghi rõ JDK baseline.

Kết hợp compile tests với runtime tests cho dữ liệu mà type erasure hoặc raw/unchecked API khiến compiler không thể xác minh. Khi đó, một thay đổi về `<T>`, wildcard, bound hoặc generic overload được đánh giá bằng **hợp đồng kiểu thật sự mà caller có thể hoặc không thể biên dịch**, không chỉ bằng vài test runtime tình cờ đi qua.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-testing-and-diagnostics-002">Bật javac -Xlint:unchecked, -Xlint:rawtypes và -Xlint:varargs</a>

<details>
<summary>Xem chi tiết</summary>

# Bật `javac -Xlint:unchecked`, `-Xlint:rawtypes` và `-Xlint:varargs` để kiểm tra Java Generics

## 1. Mục tiêu học tập

Java Generics giúp compiler phát hiện sớm nhiều sai lệch về kiểu. Tuy nhiên, khi code dùng **raw types**, thực hiện **unchecked casts/conversions** hoặc khai báo **varargs có kiểu phần tử non-reifiable**, compiler không thể chứng minh đầy đủ an toàn kiểu của mọi thao tác. Một số đoạn code vẫn biên dịch được, nhưng phát cảnh báo thay vì lỗi.

Để nhìn rõ những rủi ro này, hãy chủ động bật các nhóm cảnh báo khi biên dịch:

```bash
javac -Xlint:unchecked -Xlint:rawtypes -Xlint:varargs Example.java
```

Hoặc viết gọn ba key trong một option:

```bash
javac -Xlint:unchecked,rawtypes,varargs Example.java
```

Các lệnh trên dùng cho Java compiler `javac` của JDK, không phải lệnh chạy chương trình `java`. Ba nhóm lint có ý nghĩa khác nhau:

| Tùy chọn | Phạm vi quan sát chủ yếu | Ví dụ rủi ro |
|---|---|---|
| `-Xlint:rawtypes` | Sử dụng tên generic type nhưng **không có type arguments** | `List raw = new ArrayList();` |
| `-Xlint:unchecked` | Phép toán liên quan generic mà compiler **không thể kiểm tra đầy đủ** | `raw.add(123)`, gán raw `List` cho `List<String>` |
| `-Xlint:varargs` | Thao tác tiềm ẩn không an toàn với **varargs parameter** có element type non-reifiable, đặc biệt trong method đã gắn `@SafeVarargs` | Làm lộ alias hoặc ghi vào generic varargs array |

**Không nên hiểu ba cảnh báo là ba loại lỗi độc lập tuyệt đối.** Một đoạn code có thể đồng thời phát nhiều cảnh báo, còn generic varargs cũng có thể phát cảnh báo thuộc nhóm `unchecked`. Vị trí và cách diễn đạt diagnostic cụ thể tùy mã nguồn và phiên bản `javac`.

Bài này cung cấp cú pháp lệnh, giải thích cơ chế, ví dụ Java 17 chạy được với stdout chính xác, cách sửa code, cách review ngoại lệ hợp lý và quy trình kiểm soát cảnh báo trong build/CI.

## 2. Bắt đầu bằng `javac` và phân biệt thời điểm compile/run

Giả sử có tệp `Hello.java`:

```java
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
```

Các lệnh:

```bash
javac Hello.java
java Hello
```

Stdout của **chương trình chạy**:

```text
Hello
```

Thông báo **compiler** nếu có được sinh ở bước `javac`; chúng không phải những dòng do `System.out.println` của chương trình tạo ra.

Khi thêm các lint flags:

```bash
javac -Xlint:unchecked,rawtypes,varargs Hello.java
java Hello
```

Đoạn `Hello.java` không có raw/unchecked/generic varargs, nên không có lý do để các nhóm lint này cảnh báo nó.

Lưu ý:

- `javac` phân tích và biên dịch **mã nguồn**.
- `java` chạy class đã được tạo.
- Cảnh báo không đồng nghĩa lệnh compile thất bại; mặc định nhiều cảnh báo vẫn cho phép tạo `.class`.
- Một lỗi biên dịch (*error*) khác với warning.
- Một warning báo hiệu **vấn đề có khả năng gây rủi ro**, không có nghĩa chương trình chắc chắn sẽ ném exception ngay khi chạy.

## 3. Cú pháp chính xác của các tùy chọn

Dạng tách rời:

```bash
javac -Xlint:unchecked -Xlint:rawtypes -Xlint:varargs Main.java
```

Dạng kết hợp:

```bash
javac -Xlint:unchecked,rawtypes,varargs Main.java
```

Dạng bật tất cả các nhóm lint mà phiên bản compiler hỗ trợ:

```bash
javac -Xlint:all Main.java
```

Dạng xem thêm các tùy chọn compiler có sẵn:

```bash
javac --help-extra
```

Các ví dụ dùng `javac` trong JDK 17. Danh sách các lint keys và chi tiết diagnostic có thể thay đổi giữa các phiên bản JDK; khi thiết lập CI, cần cố định JDK hoặc chủ động kiểm tra output trên JDK mục tiêu.

`-Xlint:all` rộng hơn ba nhóm đang học, nên có thể cảnh báo thêm về deprecation, serial, cast, fallthrough hoặc những chủ đề khác. Khi mới rà soát một module Generics, bật đúng ba nhóm sẽ giúp tập trung.

Bạn có thể thêm `-Xdiags:verbose` khi cần diagnostic chi tiết:

```bash
javac -Xdiags:verbose -Xlint:unchecked,rawtypes,varargs Main.java
```

Tùy chọn này không tự sửa chương trình; nó giúp trình bày thông tin lỗi/cảnh báo có thể hữu ích khi điều tra.

## 4. `rawtypes`: raw type là gì?

Raw type là cách dùng một generic class/interface **mà không ghi type arguments**.

Ví dụ:

```java
List raw = new ArrayList();
```

Nếu có import `java.util.List` và `java.util.ArrayList`, dòng trên thường hợp lệ vì Java hỗ trợ raw types để tương thích với mã trước Generics.

Dạng typed tương ứng:

```java
List<String> typed = new ArrayList<>();
```

Điểm khác:

- `List<String>` thông báo cho compiler rằng phần tử cần là `String`.
- `List` raw bỏ thông tin element type trong nhiều phép kiểm tra.
- `new ArrayList()` là raw class instance creation.
- `new ArrayList<>()` dùng **diamond inference**, không phải raw constructor.
- Raw `List` không đồng nghĩa `List<Object>` hoặc `List<?>`.

Bật `-Xlint:rawtypes` để compiler chỉ ra những nơi ta sử dụng tên generic type dưới dạng raw.

**Cảnh báo `rawtypes` không bảo đảm có exception runtime.** Nó nhắc rằng ta đã bước ra khỏi hợp đồng generic rõ ràng và dễ mất kiểm tra kiểu ở những thao tác tiếp theo.

## 5. Ví dụ Java 17: raw `List` gây heap pollution

Tạo tệp **`RawTypesWarningDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class RawTypesWarningDemo {
    public static void main(String[] args) {
        List<String> words = new ArrayList<>();
        words.add("safe");

        List raw = words;
        raw.add(100);

        System.out.println(words.size());

        try {
            String second = words.get(1);
            System.out.println(second);
        } catch (ClassCastException ex) {
            System.out.println("ClassCastException");
        }
    }
}
```

Biên dịch có bật lint:

```bash
javac -Xlint:unchecked,rawtypes,varargs RawTypesWarningDemo.java
java RawTypesWarningDemo
```

Stdout của **chương trình**:

```text
2
ClassCastException
```

Điều xảy ra:

1. `words` được khai báo `List<String>`.
2. `List raw = words` tạo một tham chiếu raw đến **cùng object list**, không tạo bản sao.
3. `raw.add(100)` thực hiện lời gọi `add` qua raw type; ở runtime `ArrayList` nhận `Integer`.
4. `words.size()` vẫn trả `2`, vì việc đếm phần tử không yêu cầu phần tử thứ hai là `String`.
5. `words.get(1)` được dùng ở vị trí `String`; object thực là `Integer`, nên có `ClassCastException`.

Về cảnh báo:

- Dòng `List raw = words` là một raw type usage thuộc phạm vi `rawtypes`.
- Lời gọi `raw.add(100)` thường phát **unchecked call warning** từ `unchecked`.
- Cả hai có thể được nhìn thấy khi bật hai nhóm lint; `varargs` không phải vấn đề của ví dụ này.

**Heap pollution** là trạng thái khi một biến có parameterized type được sử dụng như chứa dữ liệu không thỏa generic contract. Raw operations là một trong các đường dẫn tạo trạng thái ấy.

## 6. Sửa raw type theo mục đích thật của API

Không có một phép thay raw duy nhất đúng cho mọi chương trình.

Nếu list chỉ được dùng để lưu chuỗi:

```java
List<String> words = new ArrayList<>();
words.add("safe");
// words.add(100); // Cố ý sai: compiler từ chối.
```

Nếu thật sự muốn chứa nhiều loại object:

```java
List<Object> mixed = new ArrayList<>();
mixed.add("safe");
mixed.add(100);
```

Nếu code chỉ cần **đọc từ một list với element type chưa biết**:

```java
static void showAll(List<?> values) {
    for (Object value : values) {
        System.out.println(value);
    }
}
```

Method minh họa cần đặt trong class và import `List`.

Phân biệt quan trọng:

| Ý định | Dạng đề xuất | Đặc điểm |
|---|---|---|
| Chỉ lưu chuỗi | `List<String>` | Compiler từ chối thêm số |
| Lưu nhiều object type | `List<Object>` | Element type thực sự là `Object` |
| Chấp nhận list bất kỳ, chủ yếu đọc | `List<?>` | Element type chưa biết; hạn chế thêm tùy ý |
| Tương thích API raw không thể đổi ngay | Khoanh vùng raw/unchecked boundary | Kiểm tra dữ liệu và dần thay thế |

Chuyển raw `List` thành `List<Object>` không phải lúc nào cũng hợp lệ: `List<String>` **không** là subtype của `List<Object>` vì generic Java invariant. `List<?>` thường là lựa chọn tốt hơn khi API chỉ cần đọc.

## 7. `unchecked`: compiler không chứng minh được an toàn kiểu

Cảnh báo nhóm `unchecked` xuất hiện khi một phép toán được phép thực hiện vì tính tương thích ngược, nhưng **độ an toàn generic không thể được xác minh hoàn toàn lúc biên dịch**.

Các ví dụ thường gặp:

```java
List raw = new ArrayList();
List<String> typed = raw;
raw.add("A");
```

Đây là đoạn minh họa, cần import `List` và `ArrayList`.

Những trường hợp đáng chú ý:

- **Unchecked conversion:** chuyển từ raw `List` thành `List<String>`.
- **Unchecked method invocation:** gọi generic method thông qua raw receiver.
- **Unchecked cast:** ép một object/raw container thành parameterized type mà runtime không có đủ thông tin generic.
- **Generic varargs:** ở một số khai báo/lời gọi varargs có component non-reifiable, compiler có thể phát cảnh báo thuộc `unchecked`.

`-Xlint:unchecked` giúp hiện rõ những đoạn mà type erasure hoặc raw API làm mất bằng chứng kiểu.

Không nên hiểu mọi cảnh báo `unchecked` đều là bug đang chạy. Một số ranh giới interoperating với legacy API là cần thiết; nhiệm vụ của người viết là **chứng minh và khoanh vùng** chúng, thay vì giấu đi.

## 8. Ví dụ: unchecked conversion từ legacy API

Tạo tệp **`UncheckedConversionDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class UncheckedConversionDemo {

    static List legacy() {
        List result = new ArrayList();
        result.add("hello");
        return result;
    }

    public static void main(String[] args) {
        List<String> names = legacy();

        System.out.println(names.size());
        System.out.println(names.get(0).toUpperCase());
    }
}
```

Biên dịch và chạy:

```bash
javac -Xlint:unchecked,rawtypes,varargs UncheckedConversionDemo.java
java UncheckedConversionDemo
```

Stdout:

```text
1
HELLO
```

Compiler có thể cảnh báo:

- Raw return type `List` trong chữ ký `legacy`.
- Raw local `List` và raw `new ArrayList()`.
- Unchecked invocation ở `result.add("hello")`.
- Unchecked conversion khi gán kết quả `legacy()` cho `List<String>`.

Dữ liệu demo tình cờ toàn chuỗi nên chương trình chạy tốt, nhưng chữ ký `legacy()` **không cam kết** rằng một caller trong tương lai sẽ chỉ nhận chuỗi. Nếu sửa `legacy()` để thêm số, người gọi có thể nhận `ClassCastException` sau này.

Bài học: **chạy được trên bộ dữ liệu hiện tại không chứng minh một unchecked conversion an toàn.**

## 9. Sửa unchecked conversion bằng việc thay đổi API

Khi có quyền sửa legacy API, giải pháp tốt nhất thường là định kiểu ngay ở nguồn:

```java
static List<String> names() {
    List<String> result = new ArrayList<>();
    result.add("hello");
    return result;
}
```

Method này cần đặt trong class, import `List` và `ArrayList`.

Caller:

```java
List<String> values = names();
```

không cần unchecked conversion. Compiler có thể chặn việc thêm một `Integer` vào `result`.

Nếu không thể sửa API vì thuộc thư viện legacy, hãy tạo một **adapter** nhận `List<?>`, kiểm tra phần tử, rồi tạo một list typed mới:

```java
static <T> List<T> checkedCopy(
        List<?> source,
        Class<T> elementType) {

    List<T> copy = new ArrayList<>();

    for (Object element : source) {
        copy.add(elementType.cast(element));
    }

    return copy;
}
```

Adapter này kiểm tra class runtime của từng phần tử. Khi `elementType = String.class`, một `Integer` sẽ bị từ chối ngay lúc copy.

`Class<T>` chỉ kiểm tra **class cấp phần tử** được truyền; nếu phần tử là `List<String>` hoặc kiểu tham số hóa sâu hơn, cần validator có cấu trúc phù hợp.

## 10. Ví dụ đầy đủ: adapter kiểm tra dữ liệu thay vì unchecked cast

Tệp **`CheckedAdapterDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class CheckedAdapterDemo {

    static <T> List<T> checkedCopy(
            List<?> source, Class<T> elementType) {

        List<T> result = new ArrayList<>();
        for (Object value : source) {
            result.add(elementType.cast(value));
        }
        return List.copyOf(result);
    }

    public static void main(String[] args) {
        List<String> accepted =
                checkedCopy(List.of("A", "B"), String.class);

        System.out.println(accepted);

        try {
            checkedCopy(List.of("A", 7), String.class);
        } catch (ClassCastException ex) {
            System.out.println("ClassCastException");
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -Xlint:unchecked,rawtypes,varargs CheckedAdapterDemo.java
java CheckedAdapterDemo
```

Stdout:

```text
[A, B]
ClassCastException
```

Kỹ thuật này tránh việc giả vờ rằng một raw list *đã chắc chắn* là `List<String>`. Nó kiểm tra dữ liệu và **sao chép** qua ranh giới.

Hai giới hạn:

- `List.copyOf` từ chối phần tử `null`; cần chính sách khác nếu nguồn cho phép `null`.
- Bản sao list là **shallow copy**: đối tượng phần tử mutable vẫn có thể bị sửa từ những tham chiếu khác.

## 11. Unchecked cast: một warning có thể báo lỗi sẽ đến muộn

Đặt trong method có import `java.util.List`:

```java
Object input = List.of("Java", 42);
List<String> words = (List<String>) input; // unchecked cast
System.out.println(words.size());         // 2
// String second = words.get(1);         // Khi chạy sẽ ném ClassCastException.
```

Với `javac -Xlint:unchecked,rawtypes,varargs`, dòng cast có thể phát **unchecked warning**; nếu không bật `-Werror`, chương trình vẫn được phép biên dịch. JVM chỉ kiểm tra `input` có phải `List` ở mức runtime class, không xác thực type argument `String`. Do đó `size()` đọc được, nhưng khi giá trị `Integer` bị sử dụng như `String`, một cast cụ thể mới có thể thất bại.

Đừng xem một chương trình chưa ném lỗi là bằng chứng warning vô hại. Nếu dữ liệu chưa tin cậy, nhận `List<?>`, kiểm tra từng phần tử rồi tạo bản sao có kiểu. Bài kiểm thử heap pollution phân tích chi tiết các thời điểm ghi sai, đọc như `Object`, đọc typed và điểm `checkcast` bằng một bộ test độc lập; tại đây trọng tâm là **cảnh báo `unchecked` xuất hiện lúc biên dịch và không được JVM tự biến thành lỗi ngay lập tức**.


## 12. Vì sao Generics và unchecked casts liên hệ với type erasure?

Java thực thi Generics thông thường bằng **type erasure**. Ví dụ, `List<String>` và `List<Integer>` không sinh ra hai runtime class riêng của `List`.

Hệ quả:

- Compiler kiểm tra `List<String>` ở compile time.
- Runtime object list không tự được gắn một class token đặc biệt cho `<String>`.
- JVM có thể kiểm tra `object instanceof List<?>` hoặc cast object thành raw `List`, nhưng không xác minh `<String>` chỉ từ class runtime.
- Nếu có unchecked cast hoặc raw operations, trạng thái chứa dữ liệu sai hợp đồng vẫn có thể xuất hiện.

Cảnh báo không chỉ là vấn đề “code style”; chúng chỉ tới những **ranh giới mà compile-time guarantees yếu đi**.

Khi một unchecked warning thực sự có lý do hợp lệ, hãy nêu rõ invariant được bảo đảm ở đâu. Nếu không thể chứng minh invariant, nên thay bằng một checked adapter hoặc redesign API.

## 13. `varargs`: varargs là mảng ở mức phương thức

Với:

```java
static void print(String... values) {
    for (String value : values) {
        System.out.println(value);
    }
}
```

Đây là method minh họa cần đặt trong class.

`String...` có dạng **variable arity parameter**, nhưng trong thân phương thức `values` là một `String[]`.

Khi phần tử varargs là generic type **non-reifiable**, như:

```java
static void display(List<String>... groups) {
    // ...
}
```

runtime không có class component chuyên biệt `List<String>`. Với lời gọi dạng variable arity, compiler có thể tạo một array có runtime component là raw `List`; từ đây xuất hiện nguy cơ *heap pollution* nếu code làm lộ hoặc ghi array không an toàn.

Cần phân biệt:

- `-Xlint:unchecked`: có thể báo generic varargs có parameterized element type không reifiable tại khai báo/lời gọi.
- `-Xlint:varargs`: tập trung những thao tác **potentially unsafe varargs** trong các ngữ cảnh được compiler nhận diện, đặc biệt ở phần thân khai báo `@SafeVarargs`.
- `@SafeVarargs`: một cam kết của người viết rằng implementation dùng varargs an toàn, **không** làm JVM kiểm tra được generic argument.

Một số cảnh báo generic varargs liên quan đến cả hai nhóm; không nên chỉ bật `varargs` mà tắt `unchecked`.

## 14. Ví dụ Java 17: heap pollution qua alias `Object[]`

Tạo tệp **`UnsafeVarargsDemo.java`**:

```java
import java.util.List;

public class UnsafeVarargsDemo {

    static void unsafe(List<String>... groups) {
        Object[] alias = groups;

        alias[0] = List.of(99);

        System.out.println(groups[0].size());

        try {
            String text = groups[0].get(0);
            System.out.println(text);
        } catch (ClassCastException ex) {
            System.out.println("ClassCastException");
        }
    }

    public static void main(String[] args) {
        unsafe(List.of("safe"));
    }
}
```

Biên dịch/chạy:

```bash
javac -Xlint:unchecked,rawtypes,varargs UnsafeVarargsDemo.java
java UnsafeVarargsDemo
```

Stdout của chương trình:

```text
1
ClassCastException
```

Phân tích:

1. `groups` được nhìn là `List<String>[]`.
2. `Object[] alias = groups` tạo một tham chiếu kiểu cha đến cùng object array.
3. `alias[0] = List.of(99)` chèn một `List<Integer>`, nhưng runtime component type chỉ cần object thuộc raw `List`, nên **không** chặn phép ghi này.
4. `groups[0].size()` không lấy dữ liệu dưới dạng String, nên vẫn chạy.
5. Khi đọc phần tử và gán cho `String`, runtime cast gặp `Integer`, gây `ClassCastException`.

Compiler có thể cảnh báo tại **khai báo method** và **lời gọi generic varargs**. Không nên chép diagnostic compiler thành “stdout cố định”: số lượng cảnh báo, cột chỉ vị trí và cách diễn đạt phụ thuộc phiên bản JDK.

Ví dụ này cố ý không an toàn để học; không dùng như implementation production.

## 15. `@SafeVarargs`: khi nào nên dùng?

Nếu một generic varargs method chỉ duyệt input và sao chép dữ liệu vào collection mới, không thay slot của array, không để array alias thoát ra nơi có thể sửa, có thể cân nhắc `@SafeVarargs`.

Ví dụ:

```java
@SafeVarargs
static <T> List<T> merge(List<? extends T>... groups) {
    List<T> result = new ArrayList<>();
    for (List<? extends T> group : groups) {
        result.addAll(group);
    }
    return List.copyOf(result);
}
```

Đây là một method minh họa; cần đặt trong class và import `List`, `ArrayList`.

Trong Java 17, annotation `@SafeVarargs` được phép ở các khai báo varargs thích hợp như:

- `static` methods;
- `final` instance methods;
- `private` instance methods;
- constructors.

Nó không được đặt tùy ý lên một public instance method có thể override.

**Quan trọng:** Annotation là tuyên bố của người viết về an toàn implementation; compiler không chứng minh toàn bộ đường dữ liệu là an toàn chỉ vì thấy annotation.

Không gắn `@SafeVarargs` lên method cố tình dùng `Object[]` alias để ghi danh sách sai generic argument, hoặc chuyển array cho helper có khả năng ghi sai kiểu.

## 16. Chương trình `@SafeVarargs` hợp lệ và dễ review

Tạo tệp **`SafeVarargsMergeDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class SafeVarargsMergeDemo {

    @SafeVarargs
    static <T> List<T> merge(List<? extends T>... groups) {
        List<T> result = new ArrayList<>();

        for (List<? extends T> group : groups) {
            result.addAll(group);
        }

        return List.copyOf(result);
    }

    public static void main(String[] args) {
        List<String> names =
                merge(List.of("An"), List.of("Binh", "Chi"));

        List<Number> numbers =
                merge(List.of(1, 2), List.of(3.5));

        System.out.println(names);
        System.out.println(numbers);
    }
}
```

Lệnh:

```bash
javac -Xlint:unchecked,rawtypes,varargs SafeVarargsMergeDemo.java
java SafeVarargsMergeDemo
```

Stdout:

```text
[An, Binh, Chi]
[1, 2, 3.5]
```

Ở đây:

- `groups` chỉ được đọc.
- Không thay array slot bằng `List` có generic argument khác.
- Không trả về `groups` hoặc đưa reference của nó ra bên ngoài.
- Tạo `List<T>` mới, rồi trả một list unmodifiable.

`List.copyOf` từ chối `null` nếu dữ liệu chứa null. Trong ví dụ, các input không có null.

Nếu method chuyển array sang một helper, phải kiểm toán cả helper trước khi khẳng định an toàn. Không chỉ dựa vào việc “trong method này không thấy toán tử `=` trên array”.

## 17. Vì sao `-Xlint:varargs` vẫn có giá trị khi dùng `@SafeVarargs`?

Một hiểu nhầm phổ biến là: **gắn `@SafeVarargs` thì tất cả cảnh báo đều biến mất, nên không cần review nữa**.

Thực tế:

- Annotation có thể suppress một số unchecked warnings ở khai báo và lời gọi generic varargs, vì người viết cam kết implementation an toàn.
- Compiler vẫn có thể đưa ra **cảnh báo ở phần thân method** về những thao tác potentially unsafe trên non-reifiable varargs array khi bật `-Xlint:varargs`.
- Ngay cả khi không có warning ở một đoạn cụ thể, annotation cũng không biến thao tác heap pollution thành hợp lệ về mặt an toàn dữ liệu.

Ví dụ cố ý nguy hiểm:

```java
// Đoạn học thuật: KHÔNG áp dụng @SafeVarargs như mẫu này.
@SafeVarargs
static void unsafeAnnotated(List<String>... groups) {
    Object[] alias = groups;
    alias[0] = List.of(123);
}
```

Vì method có `static`, annotation có thể được đặt theo quy tắc vị trí nhưng **cam kết là sai**. Biên dịch với `-Xlint:varargs` giúp lộ các thao tác rủi ro mà compiler có thể nhận diện; không có nghĩa compiler phát hiện được mọi đường alias và mọi helper.

Cách sửa đúng là **loại bỏ thao tác nguy hiểm** hoặc thiết kế lại API, không chỉ chuyển sang `@SuppressWarnings`.

## 18. Chọn `List<T>`/`Collection<T>` thay cho generic varargs khi phù hợp

Nếu mục tiêu là xử lý một nhóm danh sách đã tồn tại, API nhận collection có thể đơn giản hơn:

```java
static <T> List<T> mergeGroups(
        Collection<? extends List<? extends T>> groups) {

    List<T> result = new ArrayList<>();

    for (List<? extends T> group : groups) {
        result.addAll(group);
    }

    return List.copyOf(result);
}
```

Đây là method minh họa; cần import `Collection`, `List`, `ArrayList` và đặt trong class.

Caller dùng:

```java
List<String> merged =
        mergeGroups(List.of(List.of("A"), List.of("B")));
```

Ưu điểm:

- Không cần varargs array có component non-reifiable tại API boundary.
- Generic type information được compiler theo dõi qua `Collection`/`List` trong mã nguồn.
- Method có thể đọc/copy dữ liệu mà không phải giữ một mảng varargs.
- API rõ ràng khi caller đã có collection chứa các nhóm.

Đổi lại, cú pháp gọi có thể dài hơn `merge(group1, group2)`. Nếu varargs thực sự giúp API dễ dùng và implementation đã được chứng minh an toàn, `@SafeVarargs` là lựa chọn hợp lý.

## 19. So sánh ba nhóm lint bằng một ví dụ kết hợp

Tạo tệp **`CombinedWarningsDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class CombinedWarningsDemo {

    static void acceptLists(List<String>... groups) {
        for (List<String> group : groups) {
            System.out.println(group.size());
        }
    }

    public static void main(String[] args) {
        List raw = new ArrayList();
        raw.add("A");

        List<String> typed = raw;

        acceptLists(typed);
    }
}
```

Biên dịch/chạy:

```bash
javac -Xlint:unchecked,rawtypes,varargs CombinedWarningsDemo.java
java CombinedWarningsDemo
```

Stdout:

```text
1
```

Chỗ nào thuộc nhóm nào?

| Dòng/kiểu thao tác | Nhóm thường liên quan | Lý do |
|---|---|---|
| `List raw` | `rawtypes` | Tên generic type không có argument |
| `new ArrayList()` | `rawtypes` | Dùng raw generic class ở biểu thức tạo |
| `raw.add("A")` | `unchecked` | Invocation qua raw receiver |
| `List<String> typed = raw` | `unchecked` | Unchecked conversion |
| `List<String>... groups` | Thường `unchecked` | Non-reifiable varargs element type |
| `acceptLists(typed)` | Có thể `unchecked` | Variable-arity invocation có generic array component |
| Thân `acceptLists` chỉ đọc | Không nhất thiết cảnh báo `varargs` | Không có unsafe alias/write được chỉ ra |

Không nên tuyên bố `-Xlint:varargs` **chắc chắn** phải phát cảnh báo trong mọi phương thức có dấu `...`. Nó phụ thuộc thao tác, annotation, type reifiability và các luật của compiler.

## 20. Đọc và phân loại một compiler warning

Diagnostics của `javac` thường cho biết:

- File và vị trí line/column liên quan.
- Key hoặc nội dung mô tả loại warning.
- Operation bị coi là raw/unchecked/unsafe.
- Đôi khi thêm thông tin **required/found** hoặc compiler note.

Một ví dụ hình thức thông báo có thể mang dạng:

```text
Example.java:12: warning: [unchecked] unchecked call to ...
```

Đây là **mẫu hình minh họa**, không phải output bắt buộc của một chương trình cụ thể.

Quy trình đọc:

1. Nhìn **source line** trước: thực sự đang gọi method, gán biến, cast hay khai báo varargs?
2. Xác định **nguồn mất generic information**: raw receiver, raw return type, reflection, varargs array.
3. Xác định **hợp đồng cần giữ**: `List<String>` chỉ chứa String, hay chỉ cần `List<?>`?
4. Kiểm tra **nơi dữ liệu vào**: ai có quyền thêm phần tử sai kiểu?
5. Tìm cách làm compiler chứng minh được an toàn: đổi chữ ký typed, wildcard, adapter, factory hoặc redesign.
6. Chỉ dùng suppression khi có invariant rõ và có test.

Cảnh báo tại nơi đọc một giá trị chưa chắc là gốc vấn đề. Có thể bug xảy ra trước đó ở nơi raw `add` chèn dữ liệu sai kiểu.

## 21. `-Werror`: biến warnings thành lỗi build

Đối với codebase đã sạch warning hoặc đang quản lý warning như nợ kỹ thuật, có thể dùng:

```bash
javac -Xlint:unchecked,rawtypes,varargs -Werror Main.java
```

`-Werror` yêu cầu compiler coi việc có warnings là tình huống khiến compilation không thành công. Nó **không** thay đổi ý nghĩa của Generics và không biến các thao tác raw/unchecked thành phép kiểm tra runtime mới.

Một quy trình hợp lý:

1. Bật lint trước để xem cảnh báo hiện có.
2. Phân loại và sửa các trường hợp thực sự nguy hiểm.
3. Ghi lại những ranh giới legacy chưa sửa được.
4. Khoanh vùng suppression có lý do.
5. Khi đã kiểm soát cảnh báo, thêm `-Werror` để ngăn warning mới lọt vào.

Nếu dự án đang có quá nhiều warnings, bật `-Werror` đột ngột có thể làm CI thất bại hàng loạt mà chưa giúp sửa thiết kế. Nên dùng giai đoạn baseline/triage hoặc áp dụng trên module mới trước khi siết toàn hệ thống.

## 22. Ví dụ về quy trình compile và không nhầm stdout

Với `RawTypesWarningDemo.java` đã tạo ở phần trước:

```bash
javac -Xlint:rawtypes RawTypesWarningDemo.java
```

Compiler tập trung vào raw usages. Với:

```bash
javac -Xlint:unchecked RawTypesWarningDemo.java
```

Compiler tập trung vào unchecked operations. Với:

```bash
javac -Xlint:varargs RawTypesWarningDemo.java
```

Không có generic varargs trong file demo, nên nhóm `varargs` không phải trọng tâm.

Với:

```bash
javac -Xlint:unchecked,rawtypes,varargs RawTypesWarningDemo.java
```

ta nhìn tập cảnh báo rộng hơn. Việc gom các nhóm lint **không thay đổi logic chương trình**, nên nếu biên dịch thành công, bước:

```bash
java RawTypesWarningDemo
```

vẫn in:

```text
2
ClassCastException
```

Nếu thêm `-Werror`, bước `javac` có thể thất bại vì warnings, nghĩa là không nên tự chạy một `.class` cũ và tưởng đó là kết quả vừa compile. Hãy phân biệt **compiler build result** với **program run result**.

## 23. `@SuppressWarnings("unchecked")`: chỉ dùng có chứng minh

Có những điểm giao tiếp với legacy API hoặc generic array reflection cần unchecked cast mà compiler không thể chứng minh. Khi đó có thể cân nhắc `@SuppressWarnings("unchecked")` ở phạm vi **nhỏ nhất có thể**.

Ví dụ một helper dựa trên class token:

```java
@SuppressWarnings("unchecked")
static <T> T[] newArray(Class<T> type, int length) {
    return (T[]) java.lang.reflect.Array.newInstance(type, length);
}
```

Đây là method minh họa cần đặt trong class. Khi caller truyền một **reference class token** tương ứng với `T`, array được tạo có runtime component type bằng token ấy, nên invariant hỗ trợ cho cast.

Nhưng cần cân nhắc các trường hợp token primitive, `void.class`, null, hoặc API dùng raw/unchecked để đưa token không đúng. Một thư viện production nên xác định điều kiện đầu vào thay vì coi annotation là bằng chứng tuyệt đối.

Ngược lại, đoạn sau **không có chứng minh an toàn**:

```java
@SuppressWarnings("unchecked")
List<String> names = (List<String>) someUnknownObject;
```

Nếu `someUnknownObject` chứa `List<Integer>`, suppression chỉ giấu warning; lỗi runtime vẫn có thể xảy ra.

Nguyên tắc: **sửa tận gốc nếu có thể; nếu bất khả kháng, khoanh vùng và viết chú thích nêu invariant cùng kiểm thử.**

## 24. `@SuppressWarnings("rawtypes")` và `@SafeVarargs` không thay nhau

Ba công cụ có phạm vi khác nhau:

| Công cụ | Vai trò | Không làm được |
|---|---|---|
| `@SuppressWarnings("rawtypes")` | Suppress một số warnings do raw type usages | Không biến raw `List` thành `List<String>` |
| `@SuppressWarnings("unchecked")` | Suppress warnings do unchecked operations | Không tăng runtime type safety |
| `@SafeVarargs` | Cam kết varargs implementation an toàn tại khai báo hợp lệ | Không tự chứng minh tránh heap pollution |
| `-Xlint:varargs` | Yêu cầu compiler nêu warnings liên quan unsafe varargs operations được nhận diện | Không phải static analyzer toàn năng |
| `-Werror` | Khiến warnings ảnh hưởng kết quả build | Không sửa underlying code |

Có thể một method vừa dùng raw type vừa có unchecked call. Tắt `rawtypes` không nhất thiết tắt `unchecked` và ngược lại.

**Đừng gắn annotation lên toàn bộ class** chỉ để “sạch log”, trừ khi có một rationale rất cụ thể. Nếu cảnh báo mới phát sinh ở đoạn khác trong class, suppression rộng sẽ làm nó khó được phát hiện.

## 25. Ví dụ suppression có phạm vi hẹp: legacy boundary

Giả sử legacy API trả raw `List` và hợp đồng ngoài Java type system bảo đảm nó chỉ chứa `String`. Nếu không thể đổi signature, adapter vẫn nên kiểm tra dữ liệu:

```java
static List<String> legacyStrings(List<?> input) {
    List<String> copy = new ArrayList<>();
    for (Object item : input) {
        copy.add(String.class.cast(item));
    }
    return List.copyOf(copy);
}
```

Không cần unchecked cast cho thiết kế trên. Compiler có thể kiểm tra các loại object được đưa vào bản sao.

Nếu một API thực sự trả một typed object từ một unchecked path mà bạn buộc phải cast, suppression nên đặt sát **biểu thức duy nhất cần cast**, thay vì trên cả file. Ví dụ:

```java
@SuppressWarnings("unchecked")
List<String> result = (List<String>) rawValue;
```

Nhưng không coi đây là code mẫu an toàn cho input chưa kiểm tra; `rawValue` phải đã có **invariant độc lập** bảo đảm element type là `String`, và nên ghi invariant ấy trong comment/tài liệu của wrapper.

Trong mọi trường hợp, lint warnings là công cụ giúp bạn quyết định **có thể tin dữ liệu tới đâu**, không phải bài tập để làm compiler im lặng bằng mọi giá.

## 26. Generic varargs: review các đường alias và escape

Một method có thể không trực tiếp viết:

```java
values[0] = ...;
```

nhưng vẫn nguy hiểm nếu nó chuyển mảng varargs cho code khác có thể ghi sai kiểu hoặc lưu reference ấy.

Checklist review varargs array:

- Có phép `Object[] alias = values` không?
- Có `Arrays.fill(values, ...)`, `System.arraycopy` hoặc helper nào sửa slot không?
- Có lưu `values` vào field, static state hoặc callback không?
- Có trả lại nguyên array hoặc một view backed by array không?
- Có helper nhận raw `Object[]`/raw array để sửa phần tử không?
- Có thao tác cast tới array type khác rồi ghi không?
- Có annotation `@SafeVarargs` không? Nếu có, hợp đồng an toàn đã được chứng minh chưa?

Một method an toàn thường **chỉ đọc varargs parameter** và sao chép nội dung cần giữ sang cấu trúc khác, không đưa alias array ra nơi không kiểm soát được.

Không đánh đồng **sửa object đang nằm trong slot** với **thay slot của mảng**. Cả hai có thể có rủi ro tùy loại object, nhưng heap pollution do generic array alias chủ yếu nói về việc viết một giá trị sai generic argument vào array slot.

## 27. Test chạy được để phát hiện hệ quả của unchecked path

Tạo tệp **`LintRegressionDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class LintRegressionDemo {

    static List<String> safeStrings(List<?> source) {
        List<String> copy = new ArrayList<>();

        for (Object value : source) {
            copy.add(String.class.cast(value));
        }

        return List.copyOf(copy);
    }

    public static void main(String[] args) {
        System.out.println(
                safeStrings(List.of("A", "B")));

        try {
            safeStrings(List.of("A", 1));
        } catch (ClassCastException ex) {
            System.out.println("Rejected mixed types");
        }

        System.out.println(
                safeStrings(List.of()).isEmpty());
    }
}
```

Lệnh:

```bash
javac -Xlint:unchecked,rawtypes,varargs LintRegressionDemo.java
java LintRegressionDemo
```

Stdout:

```text
[A, B]
Rejected mixed types
true
```

Ở đây adapter có kiểm tra từng phần tử và không cần unchecked cast. Bài test gồm đường dữ liệu đúng, đường dữ liệu sai và list rỗng.

Lint giúp phát hiện các ranh giới không được compiler chứng minh; tests cần kiểm tra **hành vi runtime** với dữ liệu hợp lệ và dữ liệu phá invariant. Hai cách kiểm tra hỗ trợ nhau, không thay thế nhau.

## 28. Hướng dẫn dùng compiler warnings trong dự án nhiều module

Đối với một codebase lớn:

1. **Chuẩn hóa JDK/compiler version** để diagnostic ổn định giữa máy phát triển và CI.
2. **Bật cả ba nhóm** khi compile module xử lý Generics.
3. **Ghi nhận baseline** warnings hiện tại nếu đang có nhiều nợ kỹ thuật.
4. **Ưu tiên lỗi dữ liệu**: raw write gây heap pollution, unchecked conversion từ input không tin cậy, generic varargs làm lộ array.
5. **Tách warnings mới và warnings cũ** nếu cần lộ trình migrate.
6. **Đưa typed adapters** vào ranh giới legacy thay vì lan truyền raw types qua toàn hệ thống.
7. **Chỉ cho phép suppression có rationale**; review từng suppression như một ngoại lệ có kiểm chứng.
8. **Cân nhắc `-Werror` cho module sạch** để ngăn regression.
9. **Giữ test runtime** cho dữ liệu trộn, null, boundary, exceptions.
10. **Theo dõi thay đổi dependencies**: một thư viện cập nhật signature có thể thay lượng raw/unchecked warnings trong caller.

`javac` compile những `.java` nào còn phụ thuộc cách bạn gọi build và classpath/modulepath. Nếu chỉ compile một file demo thì nó không tự rà soát toàn bộ repository; trong dự án thực, phải cấu hình lint flags vào công cụ build đang dùng.

## 29. Cấu hình Maven/Gradle ở mức khái niệm

Trong Maven, plugin chịu trách nhiệm biên dịch Java là **Maven Compiler Plugin**. Các flags có thể được truyền dưới dạng compiler arguments hoặc cấu hình lint tương ứng của phiên bản plugin đang sử dụng.

Trong Gradle, tác vụ kiểu `JavaCompile` có thể nhận các compiler arguments:

```groovy
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs.addAll([
        '-Xlint:unchecked',
        '-Xlint:rawtypes',
        '-Xlint:varargs'
    ])
}
```

Đây là đoạn **Gradle Groovy DSL minh họa**, cần đặt trong build script và áp dụng ở dự án dùng Java plugin thích hợp. Không phải một chương trình Java độc lập.

Đối với build scripts Kotlin DSL, cú pháp cấu hình khác; không sao chép nguyên Groovy DSL rồi mong nó chạy trong `.gradle.kts`.

Nếu Maven/Gradle đã quản lý một JDK toolchain, nên thống nhất toolchain với JDK chạy lint local. Muốn triển khai `-Werror`, trước tiên hãy bảo đảm warnings được hiểu và xử lý; không nên suppress đại trà chỉ để build xanh.

Không cần đoán một phiên bản plugin Maven hay Gradle cụ thể nếu dự án đã có build conventions. Chỉ cần yêu cầu quan trọng là flags thực sự đến được **`javac`** trong quá trình compile.

## 30. Khi nào warning có thể bị bỏ sót dù flags đã bật?

Bật `-Xlint` không bảo đảm tìm mọi lỗi Generic.

Ví dụ:

- Dữ liệu sai có thể đến từ runtime reflection hoặc deserialization và chưa gặp cast.
- Một lời gọi qua API typed nhưng internal implementation có bug có thể chưa kích hoạt warning ở caller.
- `@SafeVarargs` tuyên bố an toàn sai nhưng compiler không nhận ra toàn bộ đường alias qua helper.
- Generic element type bị erasure; JVM không tự validate nội dung mọi `List<String>`.
- `@SuppressWarnings` có thể ẩn diagnostic ở đoạn code rủi ro.
- Nếu một file/module không được compile trong lệnh build đó, compiler không thể báo lỗi từ nó.
- Warnings từ thư viện đã biên dịch trước không được tái tạo chỉ bằng compile caller nếu không có source của thư viện.

Vì vậy quy trình chất lượng cần:

**Static type checking + lint warnings + code review + tests runtime + hợp đồng API rõ ràng.**

Các công cụ phân tích bổ sung có thể tìm thêm loại bug khác, nhưng không nên xem một danh sách flags là chứng minh formal correctness.

## 31. Những hiểu nhầm thường gặp

**Hiểu nhầm 1 — `-Xlint:rawtypes` làm raw type không biên dịch.** Không. Nó phát cảnh báo; để warnings làm build fail, thêm `-Werror` hoặc chính sách build phù hợp.

**Hiểu nhầm 2 — `-Xlint:unchecked` và `-Xlint:rawtypes` giống nhau.** Không. Một nhóm chú ý raw usage, nhóm kia chú ý các phép toán không kiểm tra đầy đủ. Một đoạn code có thể thuộc cả hai.

**Hiểu nhầm 3 — `-Xlint:varargs` sẽ cảnh báo mọi method có `...`.** Không. Những varargs với reifiable element type như `String...` thường không có rủi ro generic heap pollution đang xét.

**Hiểu nhầm 4 — Compiler warning là stdout của chương trình.** Không. Diagnostics xuất hiện lúc compile; stdout minh họa là từ `java ClassName`.

**Hiểu nhầm 5 — Dùng `@SuppressWarnings("unchecked")` sửa lỗi.** Không. Nó chỉ tắt warning, không thêm runtime generic validation.

**Hiểu nhầm 6 — `@SafeVarargs` làm JVM biết `List<String>`.** Không. Erasure vẫn áp dụng và mảng có runtime component raw `List`.

**Hiểu nhầm 7 — Đặt `@SafeVarargs` lên method bất kỳ.** Không. Java giới hạn vị trí hợp lệ của annotation.

**Hiểu nhầm 8 — `List<Object>` thay được mọi raw List trong mọi API.** Không. Java Generics invariant; `List<String>` không phải `List<Object>`.

**Hiểu nhầm 9 — `List<?>` giống `List<Object>`.** Không. Wildcard chưa biết hạn chế các thao tác ghi.

**Hiểu nhầm 10 — Có `Class<List<String>>` token từ `List<String>.class`.** Không. Parameterized type đó không có class literal.

**Hiểu nhầm 11 — Unchecked cast qua `List<String>` kiểm tra từng String.** Không. Nó chỉ có thể kiểm tra raw runtime class `List`.

**Hiểu nhầm 12 — `javac -Xlint:all` chỉ bật ba nhóm Generics.** Không. Nó bật nhiều lint categories hơn.

**Hiểu nhầm 13 — Có warning nhưng test pass nghĩa warning vô hại.** Không. Test chỉ chạy trên tập dữ liệu được chọn; type pollution có thể nằm ở đường chưa thử.

**Hiểu nhầm 14 — Không có warning nghĩa không thể có `ClassCastException`.** Không. Warnings không bảo đảm phát hiện mọi nguồn dữ liệu sai.

**Hiểu nhầm 15 — `-Werror` tạo runtime checks mới.** Không. Nó chỉ tác động kết quả build khi có compiler warnings.

**Hiểu nhầm 16 — `javac -Xlint` sẽ rà soát toàn dự án dù chỉ truyền một file.** Không. Chỉ source được compiler xử lý mới có diagnostics trong lần compile ấy.

**Hiểu nhầm 17 — Hễ `varargs` là xóa an toàn bằng cách thêm `@SafeVarargs`.** Không. Phải chứng minh body và helper không gây heap pollution.

**Hiểu nhầm 18 — Dùng raw type để tiết kiệm ký tự.** Dùng diamond hoặc wildcard thường giữ code ngắn mà vẫn có type checks tốt hơn.

## 32. Bài tập có đáp án

### Câu 1 — Nhận diện raw type

```java
List list = new ArrayList();
```

Bật lint key nào để phát hiện raw usage?

**Đáp án:** `-Xlint:rawtypes`.

### Câu 2 — Nhận diện unchecked call

```java
List raw = new ArrayList();
raw.add(123);
```

Lời gọi `add` thường thuộc nhóm cảnh báo nào?

**Đáp án:** `-Xlint:unchecked`, vì method invocation qua raw receiver không được kiểm tra đầy đủ.

### Câu 3 — Unchecked conversion

```java
List<String> strings = raw;
```

Nếu `raw` có kiểu raw `List`, compiler có thể phát cảnh báo gì?

**Đáp án:** Unchecked conversion warning.

### Câu 4 — Heap pollution

Một `List<String>` bị ghi `Integer` qua raw alias rồi đọc như `String` có thể gây lỗi gì?

**Đáp án:** `ClassCastException` ở nơi lấy phần tử Integer và dùng như String.

### Câu 5 — `@SafeVarargs`

Annotation này có tự làm varargs method an toàn không?

**Đáp án:** Không. Nó là cam kết của người viết và không thay đổi runtime component type hay erasure.

### Câu 6 — `-Xlint:varargs`

Có phải mọi method `String... values` sẽ bị cảnh báo không?

**Đáp án:** Không. `String` là reifiable component type; loại rủi ro generic varargs đang xét không tự xuất hiện.

### Câu 7 — Giải pháp cho raw receiver

Nếu API muốn nhận list bất kỳ chỉ để đọc, thay raw `List` bằng kiểu gì?

**Đáp án:** `List<?>` thường phù hợp.

### Câu 8 — `List<Object>` và `List<String>`

Có thể gán `List<String>` cho `List<Object>` không?

**Đáp án:** Không, do invariance của generic Java.

### Câu 9 — `Class.cast`

`String.class.cast(123)` có thể giúp gì ở adapter?

**Đáp án:** Nó phát hiện sai class ở runtime bằng `ClassCastException`, thay vì tin một unchecked cast.

### Câu 10 — Warnings và build

Lệnh nào bật ba nhóm lint và yêu cầu compiler thất bại khi có warning?

**Đáp án:**

```bash
javac -Xlint:unchecked,rawtypes,varargs -Werror Main.java
```

### Câu 11 — Cảnh báo khác error

Warning có luôn chặn việc tạo `.class` không?

**Đáp án:** Không theo mặc định. `-Werror` có thể biến sự hiện diện warnings thành build failure.

### Câu 12 — stdout

Nếu compiler cảnh báo lúc chạy `javac`, warning ấy có thuộc stdout do `System.out.println` của chương trình không?

**Đáp án:** Không. Cần phân biệt hai bước compile và run.

### Câu 13 — Varargs alias

Vì sao `Object[] alias = groups` có thể nguy hiểm với `List<String>... groups`?

**Đáp án:** Alias có thể dùng để ghi `List<Integer>` vào mảng runtime component raw `List`, làm sai hợp đồng generic.

### Câu 14 — Kiểm thử

Sau khi loại warnings, có nên bỏ test kiểm tra dữ liệu sai kiểu?

**Đáp án:** Không. Lint không chứng minh mọi đường runtime và không thay thế kiểm thử.

### Câu 15 — Suppression

Một unchecked cast được cho là an toàn cần điều gì?

**Đáp án:** Invariant có thể giải thích và kiểm chứng, suppression phạm vi hẹp, và test tại ranh giới dữ liệu.

## 33. Checklist thực hành trước khi merge

1. Build bằng đúng **JDK mục tiêu**.
2. Bật `-Xlint:unchecked,rawtypes,varargs`.
3. Ghi nhận toàn bộ warnings phát sinh, phân loại theo vị trí.
4. Đổi raw types thành parameterized types, wildcards hoặc typed adapters khi phù hợp.
5. Tìm unchecked casts/conversions từ legacy input, reflection, collection không tin cậy.
6. Kiểm tra khả năng dữ liệu sai generic argument lọt qua cast hoặc raw alias.
7. Với generic varargs, kiểm tra các đường ghi, truyền helper, aliasing và escape.
8. Chỉ dùng `@SafeVarargs` khi implementation thực sự an toàn và vị trí annotation hợp lệ.
9. Không dùng suppression rộng để che lỗi chưa phân tích.
10. Đối với suppression bắt buộc, ghi lý do và test.
11. Đảm bảo kết quả compile và runtime được kiểm tra riêng.
12. Test với dữ liệu hợp lệ, phần tử sai kiểu, `null` và các boundary cases.
13. Cân nhắc bật `-Werror` khi baseline warnings đã được kiểm soát.
14. Giữ cấu hình compiler flags nhất quán giữa máy phát triển và CI.
15. Nếu build đa module, xác nhận từng module Java cần thiết thực sự được compile với flags.
16. Xem warning mới khi thay dependency hoặc JDK như một tín hiệu cần điều tra.
17. Không mặc định “compile thành công” là “generic API type-safe”.
18. Ưu tiên loại nguyên nhân hơn việc giảm số dòng warning.

## 34. Tổng kết và tài liệu chuẩn

Bật các nhóm lint sau là một bước quan trọng khi học và rà soát Java Generics:

```bash
javac -Xlint:unchecked,rawtypes,varargs Main.java
```

- **`rawtypes`** phát hiện những nơi dùng generic types nhưng thiếu type arguments.
- **`unchecked`** phát hiện các phép toán mà compiler không thể chứng minh đầy đủ về kiểu, như raw calls/conversions hoặc casts với parameterized types.
- **`varargs`** hỗ trợ phát hiện thao tác potentially unsafe trên varargs arrays có generic component non-reifiable, nhất là khi code dùng `@SafeVarargs`.

Đây là **cơ chế cảnh báo ở compile time**, không phải bộ runtime type checks. Những vấn đề về erasure, heap pollution, cast muộn, null, aliasing hoặc API legacy vẫn cần được xử lý bằng thiết kế kiểu rõ ràng và kiểm thử.

Hướng giải quyết đúng là: **bật cảnh báo → đọc nguyên nhân → sửa generic signature hoặc adapter → kiểm toán varargs → test runtime → kiểm soát warning mới trong build/CI**. `@SuppressWarnings`, `@SafeVarargs` và `-Werror` có tác dụng cụ thể nhưng không thể thay thế việc chứng minh an toàn kiểu.

Nguồn tra cứu chính thức:

- [JDK 17 `javac` tool documentation](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html): `-Xlint`, `-Werror`, compiler options.
- [JLS §4.8 — Raw Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.8): raw types và unchecked behavior.
- [JLS §4.5 — Parameterized Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5): type arguments và generics.
- [JLS §4.12.2 — Variables of Reference Type](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.12.2): heap pollution.
- [JLS §4.7 — Reifiable Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.7): giới hạn kiểu runtime.
- [JLS §8.4.1 — Formal Parameters](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.1): variable arity parameters.
- [JLS §9.6.4.7 — `@SafeVarargs`](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6.4.7): vị trí annotation và cam kết an toàn.
- [Java SE 17 `java.lang.SafeVarargs`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/SafeVarargs.html): annotation API.
- [Java SE 17 `java.lang.SuppressWarnings`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/SuppressWarnings.html): phạm vi suppression.
- [Java SE 17 `java.util.Collections`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html): checked collections wrappers và legacy boundaries.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-testing-and-diagnostics-003">Đọc lỗi incompatible types, capture và incompatible bounds</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc lỗi incompatible types, capture và incompatible bounds trong Java Generics

## 1. Mục tiêu học tập

Các thông báo lỗi Generics của `javac` thường chứa những từ như `incompatible types`, `capture of ?`, `CAP#1`, `inference variable T has incompatible bounds`, `equality constraints`, `upper bounds` hay `lower bounds`. Chúng trông phức tạp, nhưng phần lớn diễn đạt một câu hỏi rất cụ thể:

> Trình biên dịch có đủ bằng chứng rằng giá trị đang được truyền, gán hoặc trả về thỏa **mọi ràng buộc kiểu** tại vị trí sử dụng hay không?

Muốn sửa đúng, cần xác định lỗi do **quan hệ subtype không hợp lệ**, **wildcard chưa biết bị capture**, hay **hệ suy luận generic không tìm ra một kiểu thỏa đồng thời các điều kiện**. Ba tình huống này liên quan nhau, nhưng không có cùng cách khắc phục.

Bài này giúp bạn:

- Tách một diagnostic `javac` thành vị trí lỗi, `required`, `found`, nguyên nhân và phần `where`.
- Nhận ra Java Generics **invariant**: `List<Integer>` không phải `List<Number>`.
- Hiểu `CAP#1`/`CAP#2` là các kiểu chưa biết được tạo bởi *capture conversion*, không phải một class runtime tên CAP.
- Biết tại sao `List<? extends Number>` đọc Number an toàn nhưng không thêm Integer tùy ý.
- Dùng helper method `<T>` khi cần giữ cùng một kiểu phần tử chưa biết bên trong **một** List.
- Dùng `? extends T` và `? super T` khi chuyển dữ liệu giữa hai collection với quan hệ kiểu rõ ràng.
- Đọc các ràng buộc `equality`, `upper` và `lower` khi compiler báo `incompatible bounds`.
- Phân biệt lỗi generic ở compile time với `ClassCastException`/`ArrayStoreException` phát sinh ở runtime.
- Kiểm tra các ví dụ đúng và sai trên Java 17+, không đoán chừng output hoặc thông điệp compiler.

## 2. Bản đồ ba nhóm lỗi

| Nhóm | Dấu hiệu diagnostic thường gặp | Bản chất |
| --- | --- | --- |
| Không tương thích kiểu | `incompatible types: X cannot be converted to Y` | Kiểu X không thể gán vào vị trí đòi Y theo các phép chuyển đổi được Java cho phép. |
| Wildcard capture | `CAP#1`, `CAP#2`, `capture of ?`, `capture of ? extends Number` | Compiler biểu diễn một kiểu thực nhưng chưa biết phía sau wildcard. |
| Ràng buộc suy luận xung đột | `inference variable T has incompatible bounds` hoặc `inferred type does not conform to upper bound(s)` | Không tìm được type argument T đáp ứng cùng lúc các điều kiện từ target, đối số và bounds. |

Một diagnostic có thể xuất hiện dưới nhiều lớp thông báo. Ví dụ lời gọi `accepts(xs, 10)` có thể bắt đầu bằng “method cannot be applied to given types”, nhưng nguyên nhân bên trong là “inference variable T has incompatible bounds” và còn nhắc `CAP#1`. Vì vậy đừng chỉ đọc dòng đầu hoặc chỉ nhìn từ `error`.

### Cách tiếp cận chung

Đọc lỗi theo thứ tự:

1. **Dòng được chỉ ra:** phép gán, lời gọi, biểu thức trả về hay thao tác `add/set` nào thất bại?
2. **Kiểu đích:** compiler yêu cầu gì (`required`, target type, kiểu của biến)?
3. **Kiểu nguồn:** compiler có gì (`found`, actual argument, expression type)?
4. **Quan hệ cần chứng minh:** subtype, invariant generic, wildcard bound, hoặc type-variable constraint?
5. **Thông tin bổ sung:** `where`, `equality constraints`, `upper bounds`, `lower bounds`, `CAP#n`.
6. **Ý định nghiệp vụ:** muốn đọc, ghi, chuyển kiểu, hay liên kết hai input? Chọn sửa chữ ký đúng mục tiêu, không cast mù quáng.

Để javac đưa thêm bằng chứng, dùng `-Xdiags:verbose`. Các mẫu lỗi ở phần sau đã được chạy bằng:

```bash
javac --release 17 -Xdiags:verbose FileName.java
```

Nội dung chi tiết của thông báo có thể thay đổi giữa các bản JDK; hãy đọc **quan hệ kiểu** mà diagnostic thể hiện, không phụ thuộc vào số thứ tự CAP hoặc cách xuống dòng.

## 3. `incompatible types`: bắt đầu từ quan hệ gán

### 3.1. Invariance: List Integer không phải List Number

**Tệp `IncompatibleInvariant.java` — cố ý không biên dịch:**

```java
import java.util.ArrayList;
import java.util.List;

class IncompatibleInvariant {
    List<Number> values = new ArrayList<Integer>();
}
```

`javac --release 17 -Xdiags:verbose IncompatibleInvariant.java` đã trả về thông báo chính:

```text
error: incompatible types:
ArrayList<Integer> cannot be converted to List<Number>
```

**Tại sao?** Integer là subtype của Number, nhưng generic types thường **invariant**: `List<Integer>` không tự là subtype của `List<Number>`.

Nếu phép gán trên được cho phép, một biến `List<Number>` có thể thêm Double vào đối tượng thật sự là `ArrayList<Integer>`, phá vỡ hợp đồng phần tử Integer:

```java
// Giả sử phép gán bị cấm phía trên được phép:
List<Number> wider = new ArrayList<Integer>();
wider.add(3.14);  // Sẽ làm hỏng danh sách Integer.
```

Đây là mã minh họa *cố ý không hợp lệ*, không phải chương trình có thể chạy.

### 3.2. Cách sửa tùy mục tiêu

**Mục tiêu A — muốn một danh sách có thể chứa mọi Number:**

```java
List<Number> numbers = new ArrayList<>();
numbers.add(10);
numbers.add(3.14);
```

Ở đây object được tạo là `ArrayList<Number>` theo target typing, nên thêm cả Integer và Double là hợp lệ.

**Mục tiêu B — chỉ cần đọc các Number từ List Integer:**

```java
List<Integer> integers = List.of(1, 2);
List<? extends Number> source = integers;
Number first = source.get(0);
```

Wildcard cho phép đọc Number mà **không hứa** rằng caller được thêm một Number tùy ý vào list nguồn.

**Mục tiêu C — cần một bản sao có khả năng thêm các Number khác:**

```java
List<Integer> integers = List.of(1, 2);
List<Number> copy = new ArrayList<>(integers);
copy.add(3.14);
```

Bạn đã tạo một list mới với kiểu phần tử Number. Đây không phải ép `List<Integer>` thành `List<Number>`; dữ liệu được sao chép vào container có contract khác.

### 3.3. Vì sao `List<Number> x = List.of(1, 2)` có thể hợp lệ?

Cần phân biệt:

```java
List<Number> a = List.of(1, 2);             // Hợp lệ.
List<Number> b = new ArrayList<>(List.of(1, 2)); // Hợp lệ.
// List<Number> c = new ArrayList<Integer>();   // Không hợp lệ.
```

Ở hai dòng đầu, compiler có thể dùng **kiểu đích** để suy luận generic arguments của `List.of` hoặc diamond sao cho kết quả có kiểu phù hợp `List<Number>`. Ở dòng cuối, kiểu tham số hóa cụ thể `ArrayList<Integer>` đã được viết tường minh, nên không thể chuyển nó thành `List<Number>`.

Một lỗi debug phổ biến là thấy hai dòng đều “chứa Integer” rồi kết luận compiler hành xử không nhất quán. Thực tế, một bên là **generic method/constructor inference** và bên kia là **invariance của một kiểu đã xác định**.

## 4. Return type cũng có thể tạo `incompatible bounds`

**Tệp `IncompatibleReturn.java` — cố ý không biên dịch:**

```java
import java.util.List;

class IncompatibleReturn {
    static List<String> values() {
        return List.of(1, 2);
    }
}
```

Khi chạy `javac --release 17 -Xdiags:verbose`, thông báo chính đã quan sát:

```text
error: incompatible types: inference variable E has incompatible bounds
    equality constraints: String
    lower bounds: Integer
```

Tại sao nó không chỉ báo đơn giản “Integer cannot be converted to String”?

`List.of(E,E)` là **generic method**. Compiler phải tìm E đáp ứng hai nguồn ràng buộc:

- Giá trị trả về được mong đợi là `List<String>`; điều này ép cấu trúc `List<E>` phù hợp với kiểu đích, tạo điều kiện đòi E=String trong ngữ cảnh này.
- Hai đối số `1`, `2` có kiểu Integer sau boxing; E phải có thể nhận Integer.

Không có E vừa thỏa yêu cầu tương ứng String vừa chứa Integer. Compiler trình bày bài toán như tập ràng buộc **incompatible bounds**.

Cách sửa là đổi **dữ liệu**, **kiểu trả về**, hoặc thực hiện **chuyển đổi có chủ đích**:

```java
static List<String> strings() {
    return List.of("1", "2");
}

static List<Integer> integers() {
    return List.of(1, 2);
}

static List<String> convertNumbers() {
    return List.of(1, 2).stream().map(String::valueOf).toList();
}
```

Không được dùng unchecked cast để biến List Integer thành List String. Nếu nghiệp vụ cần String, phải tạo các String thực.

## 5. Capture conversion: `CAP#1` không phải class runtime

### 5.1. Wildcard biểu diễn một kiểu chưa biết

Khai báo:

```java
List<? extends Number> numbers;
```

có nghĩa là: một List có phần tử thuộc **một subtype cụ thể nào đó của Number mà phía sử dụng chưa biết**. Nó có thể đang là `List<Integer>`, `List<Double>` hoặc một subtype Number khác.

Compiler thực hiện *capture conversion*, gán một tên nội bộ cho kiểu chưa biết ấy, ví dụ `CAP#1`. Tên này chỉ xuất hiện trong chẩn đoán của compiler; nó không tồn tại như một class Java mà bạn có thể `new CAP#1()` hoặc `instanceof CAP#1`.

Với `List<? extends Number>`, compiler biết **CAP là subtype của Number**. Nó **không biết CAP chính là Integer**, dù code vừa đọc ra một Integer từ một đường khác.

### 5.2. Đọc Number được, ghi Integer không chắc an toàn

**Tệp `CaptureAdd.java` — cố ý không biên dịch:**

```java
import java.util.List;

class CaptureAdd {
    static void add(List<? extends Number> numbers) {
        numbers.add(42);
    }
}
```

Chạy:

```bash
javac --release 17 -Xdiags:verbose CaptureAdd.java
```

Đoạn chẩn đoán đã kiểm chứng:

```text
error: no suitable method found for add(int)
method List.add(CAP#1) is not applicable
(argument mismatch; int cannot be converted to CAP#1)
CAP#1 extends Number from capture of ? extends Number
```

Điều này là hợp lý. Caller có thể truyền vào `List<Double>`; nếu method được phép thêm Integer 42, contract Double sẽ bị phá vỡ.

Đọc vẫn được:

```java
static Number firstNumber(List<? extends Number> list) {
    return list.get(0);
}
```

Vì CAP là subtype của Number, giá trị đọc được có thể dùng dưới loại Number.

**Ngoại lệ cần nhớ:** Java cho phép truyền `null` vào nhiều thao tác add với wildcard capture, vì null tương thích mọi reference type. Nhưng điều đó **không** có nghĩa có thể thêm một giá trị non-null tùy ý vào `List<? extends Number>`. Với một API chỉ đọc, đừng dựa vào khả năng thêm null để sửa dữ liệu.

## 6. Tại sao `List<?>.set(index, list.get(...))` vẫn có thể lỗi?

Một tình huống nhìn có vẻ vô lý: cùng một list, lấy phần tử ra rồi gán vào vị trí khác, tại sao compiler lại báo CAP?

**Tệp `CaptureSet.java` — cố ý không biên dịch:**

```java
import java.util.List;

class CaptureSet {
    static void move(List<?> values) {
        values.set(1, values.get(0));
    }
}
```

Với `-Xdiags:verbose`, compiler đã báo:

```text
method set in interface List<E> cannot be applied to given types
required: int,CAP#1
found:    int,CAP#2
reason: argument mismatch; Object cannot be converted to CAP#1
CAP#1 extends Object from capture of ?
CAP#2 extends Object from capture of ?
```

**Cốt lõi:** Hai lần sử dụng một biểu thức có wildcard có thể được capture riêng, nên compiler không có chứng cứ rằng `CAP#1` và `CAP#2` đồng nhất cho thao tác set. Dù con người nhìn thấy cùng một biến `values`, hệ thống kiểu không thể dựa vào hai capture độc lập để tự cho phép ghi.

### 6.1. Cách sửa đúng: generic capture helper

Chuyển đầu vào wildcard **một lần** vào method có một type variable T:

```java
static void replaceSecondUnknown(List<?> values) {
    replaceSecondWithFirst(values);
}

private static <T> void replaceSecondWithFirst(List<T> values) {
    values.set(1, values.get(0));
}
```

Trong helper:

- T đại diện cho **kiểu phần tử chưa biết nhưng nhất quán của một List cụ thể**.
- `get(0)` trả T.
- `set(1, T)` nhận chính T.
- Compiler có bằng chứng đầy đủ để cho phép phép gán.

Caller không cần biết T là gì. Khi gọi helper, capture conversion và generic inference dùng một T để diễn đạt sự nhất quán.

**Điều kiện:** List có ít nhất hai phần tử và phải hỗ trợ `set`. Ví dụ `List.of("A","B")` không cho sửa và sẽ ném `UnsupportedOperationException` ở runtime; helper chỉ giải quyết **vấn đề kiểu**, không bảo đảm collection mutable hoặc chỉ số hợp lệ.

Một test hợp lệ:

```java
List<String> words = new ArrayList<>(List.of("A", "B"));
replaceSecondUnknown(words);
System.out.println(words); // [A, A]
```

Chương trình Java 17 hoàn chỉnh ở phần 11 chứa chính helper và test này.

### 6.2. Khi nào cần helper, khi nào không?

Dùng helper khi:

- Một phương thức public có lý do chính đáng để nhận `List<?>`.
- Bên trong cần làm thao tác kiểu-safe mà các vị trí phải dùng **cùng element type chưa biết**.
- Truyền cả list cho một generic method riêng có thể capture nó một lần thành `List<T>`.

Không cần helper chỉ để đọc các phần tử thành Object hoặc xem `size()`:

```java
static void printSize(List<?> list) {
    System.out.println(list.size());
}
```

Không dùng helper như một phép ép kiểu “giải quyết mọi CAP error”: chỉ nên dùng khi có quan hệ T thực sự có thể chứng minh.

## 7. Hai danh sách wildcard là hai kiểu chưa biết

**Tệp `CaptureAcross.java` — cố ý không biên dịch:**

```java
import java.util.List;

class CaptureAcross {
    static void copy(List<?> source, List<?> destination) {
        destination.add(source.get(0));
    }
}
```

Compiler đã báo dạng:

```text
no suitable method found for add(CAP#1)
method List.add(CAP#2) is not applicable
Object cannot be converted to CAP#2
```

`source` có thể là `List<String>`, còn `destination` là `List<Integer>`. Hai wildcard `?` **không đồng nghĩa cùng một loại**. Không có lý do ngôn ngữ nào cho phép chuyển phần tử từ list nguồn sang list đích vô điều kiện.

Không nên “sửa” bằng cast raw:

```java
// Anti-pattern không an toàn:
((List) destination).add(source.get(0));
```

Cast raw làm compiler bớt kiểm tra, nhưng có thể tạo *heap pollution* và gây `ClassCastException` khi code khác đọc theo generic contract.

### 7.1. PECS: mô tả quan hệ nguồn và đích

Nếu mục tiêu là copy từ subtype T vào container nhận T hoặc supertype T, hãy viết:

```java
static <T> void transferFirst(
        List<? extends T> source,
        List<? super T> target) {
    target.add(source.get(0));
}
```

Đây là công thức **Producer Extends, Consumer Super**:

- `source` sản xuất giá trị T ⇒ `? extends T`.
- `target` tiêu thụ giá trị T ⇒ `? super T`.
- T đặt ở cấp method để **liên kết hai tham số**.

Caller:

```java
List<Integer> source = List.of(4, 7);
List<Number> target = new ArrayList<>(List.of(1.5));

transferFirst(source, target);
System.out.println(target); // [1.5, 4]
```

Đây là copy an toàn: Integer là Number và `List<Number>` có thể nhận Integer. Chú ý `target` mutable; nếu dùng `List.of(...)` làm đích, lỗi runtime về mutability vẫn có thể xảy ra.

### 7.2. Không gán bừa cùng một T cho hai container

Một cách viết khác:

```java
static <T> void transferExact(List<T> source, List<T> target) {
    target.add(source.get(0));
}
```

Phương thức có T chung thật, nhưng nó hạn chế hơn với nhu cầu lấy từ List<Integer> và ghi vào List<Number> vì generics invariant. PECS thể hiện quan hệ rộng hơn mà vẫn kiểm tra được.

**Nguyên tắc:** Capture helper khắc phục vấn đề *một wildcard list cần dùng lại một element type*. PECS giải quyết *hai container có quan hệ subtype/supertype rõ ràng*. Đừng trộn hai bài toán.

## 8. `incompatible bounds`: compiler đang giải một hệ ràng buộc

Một generic method có thể tạo nhiều yêu cầu về một biến kiểu T. Compiler phải tìm type arguments thỏa tất cả cùng lúc.

Có thể hiểu trực quan ba nhóm ràng buộc:

| Diagnostic | Cách đọc trực giác | Ví dụ |
| --- | --- | --- |
| `equality constraints: String` | Trong ngữ cảnh đó, T bị buộc phải bằng String về quan hệ kiểu tham số hóa. | Kiểu đích `List<String>` với generic return `List<E>`. |
| `upper bounds: Number` | T bị ràng buộc phải là subtype của Number (hoặc thoả một giới hạn trên tương ứng). | `<T extends Number>`. |
| `lower bounds: Integer` | T phải là supertype của Integer để chấp nhận một giá trị Integer làm T. | Lời gọi generic nhận Integer. |

Đây là cách đọc hữu ích khi debug; thuật toán inference đầy đủ của JLS còn xử lý compatibility constraints, wildcard bounds, capture, intersection types và nhiều tình huống phức tạp khác. Không nên coi bảng như một trình mô phỏng đầy đủ mọi quy tắc inference.

### 8.1. Ràng buộc từ method bound và đối số xung đột

**Tệp `BoundedWrong.java` — cố ý không biên dịch:**

```java
import java.util.List;

class BoundedWrong {
    static <T extends Number> void use(List<T> numbers) {}

    static void demo() {
        use(List.of("A"));
    }
}
```

Chạy:

```bash
javac --release 17 -Xdiags:verbose BoundedWrong.java
```

Compiler đã in phần trọng tâm:

```text
required: List<T>
found:    List<String>
reason: inference variable E has incompatible bounds
upper bounds: Number,Object
lower bounds: String
```

Method yêu cầu T extends Number, nhưng caller đưa List<String>. String không phải subtype Number. Dù compiler có suy luận E từ `List.of`, không có cách suy luận hợp lệ nào biến String thành Number.

Sửa đúng tùy bài toán:

```java
use(List.of(1, 2));           // T tương thích Integer.
use(List.<Number>of(1, 2));   // Chỉ rõ Number nếu cần.
```

Hoặc nếu method thực sự cần xử lý String, hãy thay đổi bound/thuật toán của method. Đừng ép `(List<Number>)(List<?>) List.of("A")`; cast đó không biến String thành Number.

### 8.2. Ràng buộc từ target type và nhiều đối số

**Tệp `IncompatibleBounds.java` — cố ý không biên dịch:**

```java
class IncompatibleBounds {
    static <T> T first(T a, T b) {
        return a;
    }

    static void demo() {
        String value = first("A", 42);
    }
}
```

Trong Java 17 đã kiểm chứng, diagnostic bắt đầu:

```text
incompatible types: inferred type does not conform to upper bound(s)
inferred: INT#1
upper bound(s): String,Object
```

Tiếp theo là chi tiết về *intersection type* `INT#1` và `INT#2`. Điều cần nắm là compiler có thể suy luận một common supertype cho hai đối số String và Integer, nhưng biểu thức kết quả lại bị yêu cầu là String. Không có bằng chứng rằng kết quả của phương thức tổng quát đó phải là String trong trường hợp pha loại.

Các cách sửa:

```java
String a = first("A", "B"); // Hai đối số String.
Object b = first("A", 42);  // Kết quả dùng dưới Object.
```

Không nên đổi `String value` sang raw Object rồi ép String ở bước sau nếu nghiệp vụ thực sự đòi String; cần xác định chính xác đầu vào và output contract mong muốn.

**Nuance quan trọng:** `<T> T first(T a,T b)` không bắt buộc hai argument có cùng runtime class. Compiler có thể suy luận một supertype chung. Vì vậy loại `Object` trong ví dụ sửa hợp lệ không phải “Java bỏ kiểm tra generics”, mà là một phép suy luận khác phù hợp kiểu đích.

## 9. `CAP#1` và `incompatible bounds` có thể cùng xuất hiện

**Tệp `MethodCaptureWrong.java` — cố ý không biên dịch:**

```java
import java.util.List;

class MethodCaptureWrong {
    static <T> void accepts(List<T> values, T item) {}

    static void demo(List<? extends Number> xs) {
        accepts(xs, 10);
    }
}
```

Chương trình tạo một trường hợp có **cả capture và generic inference**. Diagnostic Java 17 chứa:

```text
required: List<T>,T
found:    List<CAP#1>,int
reason: inference variable T has incompatible bounds
equality constraints: CAP#1
lower bounds: Integer
CAP#1 extends Number from capture of ? extends Number
```

Giải thích:

1. Tham số đầu `List<T>` được nối với `List<? extends Number>`, làm inference có một T gắn với captured element type, gọi là CAP#1.
2. Tham số thứ hai `T item` lại nhận Integer (boxing từ `10`).
3. Compiler không thể chứng minh rằng Integer là chính CAP#1. CAP#1 có thể là Double hoặc subtype Number khác.

Đừng chỉ xem dòng “incompatible bounds” rồi tùy tiện bỏ `extends Number`. Vấn đề là **một type chưa biết đã bị gắn với list, trong khi bạn đòi chèn một Integer không có bằng chứng cùng type**.

### Sửa thế nào?

Nếu method chỉ cần **đọc** list, thay contract:

```java
static void inspect(List<? extends Number> xs, Number other) {
    Number first = xs.get(0);
    System.out.println(first + "," + other);
}
```

Nếu cần thêm Integer vào một list, cần một **consumer**:

```java
static void addInteger(List<? super Integer> destination) {
    destination.add(10);
}
```

Nếu method bắt buộc nhận List<T> cùng một giá trị T, caller phải cung cấp một loại có quan hệ đồng nhất thật, ví dụ:

```java
static <T> void accepts(List<T> values, T item) {
    values.add(item);
}

List<Integer> values = new ArrayList<>();
accepts(values, 10);
```

Mỗi thiết kế tương ứng một nghiệp vụ khác nhau. Không có “một cast chữa mọi incompatible bounds”.

## 10. Hiểu đúng `extends` và `super`

| Tham số | Đọc an toàn | Ghi an toàn | Mục đích |
| --- | --- | --- | --- |
| `List<T>` | T | T | Một loại T cụ thể được đặt tên trong method/class. |
| `List<? extends T>` | T | Chủ yếu không thêm non-null T tùy ý | Producer: cung cấp các subtype của T. |
| `List<? super T>` | Object | T và subtype của T | Consumer: nhận các giá trị T. |
| `List<?>` | Object | Không thêm non-null tùy ý | Không biết element type; phù hợp khi quan sát chung. |

Ví dụ:

```java
List<? extends Number> input = List.of(1, 2);
Number n = input.get(0);       // Hợp lệ.
// input.add(3);               // Không biên dịch.

List<? super Integer> sink = new ArrayList<Number>();
sink.add(3);                   // Hợp lệ.
Object obj = sink.get(0);      // Đọc dưới Object.
```

Đừng nói `List<? extends Number>` “là List Number” theo nghĩa được thêm bất kỳ Number nào: nó có thể được tạo từ một `List<Integer>` không cho phép Double.

Đừng nói `List<? super Integer>` đọc luôn ra Integer: container có thể thật là `List<Object>`, và compiler chỉ bảo đảm kết quả đọc được dưới Object.

## 11. Chương trình Java 17 hoàn chỉnh

**Tệp `GenericDiagnosticsDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GenericDiagnosticsDemo {
    static class Animal {}
    static class Dog extends Animal {}
    static class Cat extends Animal {}

    static <T> void replaceSecondWithFirst(List<T> list) {
        list.set(1, list.get(0));
    }

    static void replaceSecondUnknown(List<?> list) {
        replaceSecondWithFirst(list);
    }

    static <T> void transferFirst(
            List<? extends T> source, List<? super T> target) {
        target.add(source.get(0));
    }

    static <T extends Number & Comparable<T>> T max(T left, T right) {
        return left.compareTo(right) >= 0 ? left : right;
    }

    static <T> T first(T a, T b) {
        return a;
    }

    static void showBounds(List<? extends Number> list) {
        Number value = list.get(0);
        System.out.println("readNumber=" + value);
    }

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>(List.of(new Dog()));
        List<? extends Animal> animals = dogs;
        System.out.println("extendsReadable=" + (animals.get(0) instanceof Dog));

        List<? super Dog> dogSink = new ArrayList<Animal>();
        dogSink.add(new Dog());
        System.out.println("superSize=" + dogSink.size());

        List<String> words = new ArrayList<>(List.of("A", "B"));
        replaceSecondUnknown(words);
        System.out.println("captureHelper=" + words);

        List<Integer> source = List.of(4, 7);
        List<Number> target = new ArrayList<>(List.of(1.5));
        transferFirst(source, target);
        System.out.println("transfer=" + target);

        showBounds(List.of(42));
        System.out.println("boundedMax=" + max(3, 8));
        System.out.println("targetTyped=" + first("A", "B"));
        System.out.println("wildcardCount="
                + Collections.frequency(List.of(1, 1, 2), 1));
    }
}
```

Lệnh biên dịch và chạy:

```bash
javac --release 17 -Xlint:all GenericDiagnosticsDemo.java
java GenericDiagnosticsDemo
```

**Stdout đã kiểm chứng:**

```text
extendsReadable=true
superSize=1
captureHelper=[A, A]
transfer=[1.5, 4]
readNumber=42
boundedMax=8
targetTyped=A
wildcardCount=2
```

Chương trình này biên dịch bằng `javac --release 17 -Xlint:all` mà không có warning trong phép thử.

### 11.1. Producer/consumer với kế thừa

`List<Dog>` có thể được dùng dưới `List<? extends Animal>`; đọc được Animal, nhưng không thêm Cat tùy ý. `List<? super Dog>` nhận Dog an toàn, tuy nhiên đọc lại chỉ bảo đảm Object.

### 11.2. Capture helper hoạt động vì chỉ có một T

`replaceSecondUnknown` gọi `replaceSecondWithFirst`. Trong helper, cả giá trị lấy từ `get(0)` và tham số `set(1,...)` cùng một T, nên kết quả `[A,A]` hợp lệ.

### 11.3. PECS chuyển Integer sang Number

`transferFirst` đọc Integer từ nguồn rồi thêm vào một `List<Number>`. Không cần cast, không làm ô nhiễm heap.

### 11.4. Bound của T trong `max`

`<T extends Number & Comparable<T>>` yêu cầu cả hành vi Number và khả năng tự so sánh theo Comparable<T>. Gọi `max(3,8)` cho kết quả 8; không phải mọi cặp Number khác subtype đều thỏa cùng một T. Khi type arguments khác nhau, có thể cần thuật toán/Comparator khác.

### 11.5. Generic method có target type hợp lệ

`first("A","B")` trả giá trị String dưới target String; không mâu thuẫn inference. Đối chiếu trường hợp `String s = first("A",42)` ở phần 8, compiler không có cùng đảm bảo.

## 12. Bảng các tệp lỗi cố ý để luyện đọc

Mỗi ví dụ sau là **một tệp riêng biệt**, không ghép với `GenericDiagnosticsDemo.java` rồi mong build vẫn thành công.

| Tên tệp | Dòng gây lỗi | Chẩn đoán trung tâm |
| --- | --- | --- |
| `IncompatibleInvariant.java` | `List<Number> x = new ArrayList<Integer>();` | `incompatible types ... cannot be converted` |
| `IncompatibleReturn.java` | `return List.of(1,2)` khi return `List<String>` | `inference variable E has incompatible bounds`; equality String, lower Integer |
| `CaptureAdd.java` | `List<? extends Number>.add(42)` | `int cannot be converted to CAP#1` |
| `CaptureSet.java` | `List<?>.set(1,list.get(0))` | `required CAP#1, found CAP#2` |
| `CaptureAcross.java` | `destination.add(source.get(0))` với hai `List<?>` | Hai capture độc lập không chứng minh tương thích |
| `IncompatibleBounds.java` | `String s = first("A",42)` | `inferred type does not conform to upper bound(s)` |
| `BoundedWrong.java` | `use(List.of("A"))` với `T extends Number` | Upper Number xung đột lower String |
| `MethodCaptureWrong.java` | `accepts(xs,10)` với `xs:List<? extends Number>` | Equality CAP#1 xung đột lower Integer |

Một bài test negative compile nên đảm bảo javac **trả mã lỗi khác 0** và message thuộc loại kỳ vọng, chứ không chỉ kiểm tra chương trình “không in gì”. Các diagnostic trên đã được tái hiện với Java 17 và chế độ verbose.

## 13. Lệnh compiler giúp phân tích tốt hơn

### 13.1. Bật diagnostics chi tiết

```bash
javac --release 17 -Xdiags:verbose IncompatibleBounds.java
```

Chế độ verbose thường hiển thị `required/found`, các capture variables và nhiều chi tiết constraint hơn.

### 13.2. Bật cảnh báo generics

```bash
javac --release 17 -Xlint:unchecked,rawtypes GenericDiagnosticsDemo.java
```

Nếu nguồn có raw types hoặc unchecked casts, cảnh báo có thể giúp tìm nơi quan hệ generic bị mất. Trong chương trình hoàn chỉnh phần 11, những lỗi minh họa đã được sửa mà không cần unchecked cast.

### 13.3. Phân biệt lỗi và warning

- `incompatible types`, `generic array creation`, `name clash` và một số lỗi `capture` ở compile time **chặn build**.
- `unchecked cast`/`rawtypes` thường là **cảnh báo** trong ngữ cảnh được Java cho phép.
- `ClassCastException` và `ArrayStoreException` là **ngoại lệ runtime**, thường sau khi có raw alias/unchecked operation hoặc ghi sai vào mảng covariant.

Không nên dùng `@SuppressWarnings("unchecked")` như “cách sửa” cho một lời gọi `add` không hợp lệ. Annotation không biến CAP#1 thành Integer, không tạo quan hệ subtype còn thiếu và không làm thay đổi dữ liệu.

### 13.4. Dùng negative tests đúng cách

Một cách học có hệ thống:

1. Lưu mỗi đoạn lỗi vào tệp mang tên lớp đúng như trong bảng.
2. Biên dịch riêng một tệp.
3. Ghi phần `required`, `found` và `where` hoặc constraints.
4. Chỉ sửa **một yếu tố hợp đồng** mỗi lần: target type, type bound, wildcard hay generic helper.
5. Biên dịch lại để xác nhận.
6. Thêm test chạy cho trường hợp sau sửa, nhất là với collection mutation.

Không đặt nhiều lỗi độc lập vào cùng một file rồi kết luận từ lỗi đầu tiên; lỗi đầu có thể làm compiler bỏ qua hoặc suy luận khác với những dòng sau.

## 14. Khi nào nên sửa chữ ký method, khi nào nên sửa caller?

### 14.1. Lỗi ở caller

Nếu API yêu cầu `List<Number>` vì bên trong cần thêm cả Integer và Double, caller nên truyền một list Number có thật:

```java
List<Number> values = new ArrayList<>();
values.add(10);
values.add(3.5);
```

Không nới API thành `List<? extends Number>` nếu method thật sự **cần ghi Number** vào list. Wildcard extends sẽ cấm hoạt động đó.

### 14.2. Lỗi ở API

Nếu API chỉ tính tổng phần tử Number nhưng lại yêu cầu `List<Number>`, chữ ký có thể quá chặt:

```java
static double sum(List<? extends Number> values) {
    double total = 0;
    for (Number x : values) {
        total += x.doubleValue();
    }
    return total;
}
```

Phương thức này nhận được `List<Integer>`, `List<Double>` và `List<Number>`. Nó không ghi nên wildcard extends diễn đạt chính xác mục đích.

### 14.3. Lỗi do hợp đồng mơ hồ

Nếu method cố gắng chèn một đối tượng Integer vào `List<? extends Number>`, bạn cần quyết định:

- Chỉ đọc? Không gọi `add`.
- Ghi Integer? Nhận `List<? super Integer>`.
- Ghi một T phù hợp với list? Dùng `List<T>` và tham số T liên quan, bắt caller cung cấp list typed đúng.
- Sao chép phần tử giữa hai container? Dùng `List<? extends T>` và `List<? super T>`.

Sửa generic error không chỉ là làm compiler im lặng; đó là **xác định quyền đọc/ghi và mức đảm bảo dữ liệu của API**.

## 15. Một số thông điệp trông giống nhau nhưng nguyên nhân khác nhau

### “Found List<String>, required List<T>”

Có thể là:

- T đã bị bound `extends Number`.
- T bị ràng buộc bởi một argument khác.
- T được suy luận từ target return type.
- CAP của một wildcard yêu cầu kiểu chưa biết nhất quán.

Hãy đọc `where` và các constraints trước khi thêm một cast.

### “Required CAP#1, found CAP#2”

Có thể là hai wildcard độc lập hoặc hai capture của những biểu thức wildcard mà compiler không chứng minh là cùng kiểu. Nếu chỉ thao tác với **một list**, generic capture helper có thể tạo một T thống nhất. Nếu chuyển dữ liệu giữa **hai list**, cần quan hệ nguồn/đích được diễn đạt bằng chữ ký và đôi khi không có cách an toàn để làm thao tác mà caller yêu cầu.

### “Inference variable T has incompatible bounds”

Các ràng buộc đang xung đột. Không nhất thiết là “compiler Java quá yếu” hoặc “generic phức tạp nên bỏ qua”. Hãy vẽ ba nguồn constraint:

```text
Target / return context
        |
        v
Inference variable T <---- actual arguments
        ^
        |
Declared bounds / wildcards

T phải thỏa tất cả các mũi tên ràng buộc.
```

Nếu chúng không thể cùng đúng, cần đổi một yêu cầu hoặc đổi dữ liệu — ví dụ không thể yêu cầu List<String> từ `List.of(1,2)` mà không chuyển Integer thành String.

## 16. Common supertype và giao cắt kiểu (*intersection types*)

Trong `String s = first("A",42)`, `javac` có thể in `INT#1`, `INT#2` cùng các interface như `Serializable` và `Comparable`. Đây là mô tả **kiểu giao cắt do suy luận** tạo ra để biểu diễn một kiểu chung phức tạp của các argument. Nó không có nghĩa runtime tạo một class tên `INT#1`.

Đừng cố học thuộc danh sách interface mà compiler in ra ở từng JDK. Phần quan trọng là:

- Generic method muốn tìm một T cho cả String và Integer.
- Target yêu cầu kết quả được dùng dưới String.
- Common supertype suy luận không nhất thiết là subtype của String.
- Do đó phép gán bị chặn.

Một giải pháp đọc được và trung thực là đổi target thành Object nếu nghiệp vụ cho phép, hoặc làm đồng nhất loại đầu vào. Một cast String chỉ có thể là một quyết định runtime có rủi ro, không thay thế được bằng chứng compile-time.

## 17. Capture conversion không phải wildcard covariance tùy ý

Wildcard cho phép một số quan hệ gán:

```java
List<Integer> a = List.of(1, 2);
List<? extends Number> b = a;
Number n = b.get(0);
```

Nhưng khi đã dùng b, compiler cần nhớ “một loại cụ thể nào đó extends Number” thay vì giả định nó là Number. Capture conversion là cách formal hóa điều này.

Tương tự:

```java
List<Number> sink = new ArrayList<>();
List<? super Integer> consumer = sink;
consumer.add(5);
Object readBack = consumer.get(0);
```

`? super Integer` cho phép ghi Integer, nhưng loại đọc tổng quát là Object vì container thật có thể là List<Object>.

Không nhầm wildcard với array covariance: `Number[]` có thể tham chiếu một `Integer[]`, nhưng JVM kiểm tra array store và có thể ném `ArrayStoreException`; generics chủ yếu bảo vệ qua compiler, và subtype relation giữa `List<Integer>` với `List<Number>` không hoạt động như array covariance.

## 18. Lỗi hiểu nhầm thường gặp

| Nhận định | Vì sao sai | Cách sửa |
| --- | --- | --- |
| “Integer extends Number nên List<Integer> extends List<Number>.” | Generic invariance. | Đọc qua `List<? extends Number>` hoặc copy vào List<Number>. |
| “`? extends Number` có nghĩa có thể thêm mọi Number.” | Element type có thể là Integer/Double cụ thể chưa biết. | Chỉ đọc Number, hoặc đổi API thành consumer. |
| “CAP#1 là class có thể cast tới.” | CAP là biến nội bộ của capture conversion. | Diễn đạt quan hệ bằng helper `<T>` hoặc bounds. |
| “Hai `List<?>` có cùng dấu ? nên cùng kiểu.” | Mỗi wildcard có thể chỉ một element type khác. | Không copy tùy ý; dùng PECS nếu có subtype relation. |
| “`List<?>.set(i,list.get(j))` chắc chắn compile.” | Các capture có thể khác nhau tại hai biểu thức. | Capture helper lấy List<T> một lần. |
| “Generic helper sẽ giải quyết mọi CAP error.” | Không thể chứng minh hai wildcard list độc lập đồng kiểu. | Dùng typed source/destination phù hợp hoặc đổi nghiệp vụ. |
| “`lower bounds: Integer` nghĩa T phải là Integer.” | Nó thường đòi T chấp nhận Integer, không nhất thiết chính là Integer. | Xem cả equality/upper bounds. |
| “Thêm cast unchecked là giải pháp.” | Cast làm mất kiểm tra, không làm dữ liệu đổi kiểu. | Sửa API/target hoặc chuyển đổi giá trị thật. |
| “Incompatible bounds luôn do sai `extends` trong khai báo.” | Constraints còn đến từ return target, đối số và wildcard. | Đọc toàn bộ diagnostic. |
| “Build chạy thì giá trị generic luôn đúng runtime.” | Raw types/unchecked operations có thể gây heap pollution. | Giữ warnings và kiểm tra boundaries. |
| “Mọi list thêm được khi generic compile.” | List immutable có thể ném UnsupportedOperationException. | Tách type safety khỏi mutability contract. |

## 19. Quy trình debug thực tế

1. **Cô lập tệp và biểu thức** làm compiler báo lỗi. Đừng sửa đồng loạt tất cả generic parameters.
2. **Chạy `javac --release 17 -Xdiags:verbose`** để xem các phần constraint đầy đủ.
3. **Xác định kiểu tĩnh** của mọi đối số, biến đích và kiểu trả về method.
4. **Kiểm tra invariance:** Có đang cố đặt `List<Subclass>` vào `List<Superclass>` không?
5. **Tìm wildcard:** Nếu có `? extends`, `? super` hoặc `?`, xác định method đang đọc hay ghi.
6. **Đọc CAP:** Các CAP có đến từ cùng một List cần capture helper, hay hai List chưa có quan hệ được chứng minh?
7. **Đọc constraints:** T bị equality, upper và lower bounds gì từ từng vị trí?
8. **Tìm xung đột thực:** String không extends Number; Integer không thể được bảo đảm phù hợp với một CAP có thể là Double.
9. **Chọn sửa theo nghiệp vụ:** Đổi target, đổi dữ liệu, chuyển đổi phần tử, dùng wildcard, helper generic, PECS hoặc class token khi thật sự có kiểm tra runtime.
10. **Biên dịch lại cả trường hợp hợp lệ và không hợp lệ.** Phải chắc lỗi đã biến mất đúng lý do, không do unchecked warning bị che.
11. **Chạy test mutability và logic.** Một method compile hợp lệ vẫn có thể bị `IndexOutOfBoundsException` hoặc `UnsupportedOperationException`.
12. **Review public API:** Giữ generic contract rõ và đơn giản; đừng buộc caller ép raw type để gọi.

## 20. Bài tập và đáp án

### Bài 1: Vì sao không gán trực tiếp?

```java
List<Number> values = new ArrayList<Integer>();
```

**Đáp án:** Generic invariance. `ArrayList<Integer>` không phải subtype của `List<Number>`; nếu được gán, caller có thể thêm Double vào danh sách Integer.

### Bài 2: Khi nào `List<? extends Number>` phù hợp?

**Đáp án:** Khi method chỉ cần **đọc** các giá trị có thể dùng dưới Number và không thêm Number bất kỳ vào nguồn.

### Bài 3: `CAP#1` nghĩa là gì?

**Đáp án:** Một type variable mới do compiler sinh khi capture một wildcard; ví dụ một subtype chưa biết của Number. Nó không phải class runtime.

### Bài 4: `List<?>` lấy một phần tử rồi set lại tại slot khác bị lỗi, nên làm gì?

**Đáp án:** Truyền List<?> vào một helper `<T>` nhận `List<T>`; bên trong helper, get và set cùng dùng T. Đảm bảo list mutable và chỉ số hợp lệ.

### Bài 5: Có thể copy từ `List<?>` sang một `List<?>` khác không?

**Đáp án:** Không tổng quát. Hai wildcard có thể đại diện hai kiểu khác nhau. Cần nguồn `? extends T` và đích `? super T` hoặc một hợp đồng typed khác.

### Bài 6: `lower bounds: Integer` có nghĩa T đúng bằng Integer không?

**Đáp án:** Không nhất thiết. Nó thường yêu cầu T là supertype đủ để nhận một Integer; phải xem thêm equality và upper constraints.

### Bài 7: `<T extends Number> void use(List<T>)` có nhận List String không?

**Đáp án:** Không. String không subtype Number, nên compiler có thể báo incompatible bounds.

### Bài 8: `String s = first("A",42)` khó suy luận vì sao?

**Đáp án:** Hai đầu vào khác kiểu có thể dẫn compiler đến common supertype không bảo đảm là String, trong khi target lại yêu cầu String.

### Bài 9: `List<String> values() { return List.of(1,2); }` cần sửa gì?

**Đáp án:** Trả List Integer nếu đúng dữ liệu, trả các chuỗi String, hoặc chuyển Integer sang String thực sự. Không dùng unchecked cast.

### Bài 10: Khi nào dùng `List<? super Integer>`?

**Đáp án:** Khi API cần **ghi Integer** vào một collection có thể là List<Integer>, List<Number> hoặc List<Object>. Khi đọc kết quả nói chung chỉ bảo đảm Object.

### Bài 11: Helper generic có giải quyết `accepts(xs,10)` với xs `List<? extends Number>` không?

**Đáp án:** Không tự động. Kiểu phần tử của xs có thể là Double; không có bằng chứng Integer thuộc đúng captured type ấy. Cần thay contract hoặc chọn list/giá trị tương thích.

### Bài 12: Vì sao cần test runtime sau khi sửa lỗi compile?

**Đáp án:** Type safety không bảo đảm list hỗ trợ sửa, có đủ phần tử hoặc dữ liệu đầu vào thỏa hợp đồng nghiệp vụ. `List.of` vẫn có thể ném UnsupportedOperationException khi set/add.

## 21. Tổng kết

Ba nhóm thông điệp lớn đều bắt nguồn từ một nguyên tắc: **compiler chỉ cho phép thao tác khi có thể chứng minh các quan hệ kiểu cần thiết.**

- `incompatible types`: quan hệ gán/return không hợp lệ, chẳng hạn `List<Integer>` không thể dùng như `List<Number>`.
- `capture of ?` và `CAP#n`: kiểu phần tử tồn tại nhưng chưa biết; không tự coi mọi wildcard là một kiểu giống nhau.
- `incompatible bounds`: generic inference phải thỏa cùng lúc target, arguments và bounds; khi constraints xung đột, không có type argument hợp lệ.

Chọn hướng sửa theo **quyền đọc/ghi và quan hệ thật sự của dữ liệu**: wildcard extends cho producer, super cho consumer, generic helper cho một capture thống nhất, PECS để chuyển giữa hai collection có quan hệ subtype, và chuyển đổi dữ liệu thật khi muốn đổi element type. Đừng lấy unchecked cast làm phương án mặc định.

**Ghi nhớ:** Khi thấy `CAP#1` hay “incompatible bounds”, hãy hỏi *compiler đang thiếu bằng chứng về quan hệ kiểu nào?* — rồi sửa chính quan hệ đó trong thiết kế API.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-testing-and-diagnostics-004">Kiểm thử ranh giới raw type, reflection và ép kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử ranh giới raw type, reflection và ép kiểu trong Java Generics

Java Generics chủ yếu bảo vệ **tính đúng kiểu ở thời điểm biên dịch**. Khi chương trình đi qua **raw type**, **unchecked cast**, **reflection** hoặc các API nhận dữ liệu dạng `Object`, một phần bảo đảm của compiler có thể bị bỏ qua. Đây chính là các *ranh giới kiểu* cần kiểm thử có chủ đích: không chỉ thử rằng dữ liệu đúng hoạt động, mà còn chứng minh dữ liệu sai bị từ chối ở đâu, hoặc sẽ gây lỗi muộn ở đâu nếu không có kiểm tra.

Mục tiêu của bài:

- Phân biệt **lỗi bị javac từ chối**, **cảnh báo unchecked nhưng code vẫn chạy**, và **exception xảy ra khi runtime tiêu thụ dữ liệu sai kiểu**.
- Tạo bài kiểm thử có cả *positive cases* và *negative cases*, thay vì chỉ chạy dữ liệu hợp lệ.
- Hiểu rằng một cast sang `List<String>` không xác minh từng phần tử của danh sách.
- Chứng minh `Field.set` và `Method.invoke` có thể bỏ qua **ràng buộc type argument** của generic declaration do erasure, dù vẫn thực hiện các runtime checks cần thiết đối với kiểu đã xóa.
- Kiểm tra các biện pháp phòng vệ như `Class<T>.cast`, `Collections.checkedList`, typed copy/validator và kiểm soát các đường ghi.
- Phân biệt array runtime checks với generic type checks và giải thích đúng `ClassCastException`, `ArrayStoreException`, `IllegalArgumentException`, `InvocationTargetException`.
- Tạo một chương trình Java 17+ độc lập, có kiểm tra kết quả **không phụ thuộc `-ea`**, in số bài kiểm thử thành công và làm chương trình thất bại khi điều kiện kỳ vọng không đúng.

**Nguyên tắc:** “Code chạy qua unchecked cast” không đồng nghĩa “đã chứng minh an toàn”. Một bài kiểm thử tốt phải hỏi **bất biến kiểu còn đúng sau khi đi qua mỗi ranh giới hay không**, và exception xuất hiện ngay ở biên hay chỉ khi một caller đọc kết quả với type argument cụ thể.

## 1. Ba tầng cần tách trong kế hoạch kiểm thử

| Tầng | Ví dụ | Kiểm tra bằng gì? | Kết quả đáng kỳ vọng |
| --- | --- | --- | --- |
| **Compile-time type checking** | `List<String>.add(42)` khi caller typed | `javac` | Lỗi biên dịch |
| **Compile-time warning** | `List raw = typed; raw.add(42)` | `javac -Xlint:rawtypes -Xlint:unchecked` | Biên dịch có cảnh báo về raw/unchecked |
| **Runtime ngoại lệ muộn** | Đọc một `Integer` ô nhiễm như `String` | Bài test chạy | `ClassCastException` khi giá trị được tiêu thụ theo kiểu cụ thể |
| **Runtime validation chủ động** | `String.class.cast(42)` | Bài test chạy | `ClassCastException` ngay ở biên kiểm tra |
| **Reflection trên generic declaration** | `Field.set` gán `List<Integer>` vào field khai báo `List<String>` | Reflection + đọc lại | Gán có thể thành công; lỗi khi đọc phần tử như `String` |
| **Array runtime check** | `Object[] a = new String[1]; a[0] = 42` | Bài test chạy | `ArrayStoreException` ngay khi ghi |
| **Wrong array cast** | `(String[]) new Object[]{"x"}` | Bài test chạy | `ClassCastException` vì runtime class của array là `Object[]` |

Ở tầng compile-time, compiler chặn thao tác trước khi có chương trình chạy. Ở tầng runtime, kết quả phụ thuộc **runtime class thực tế**, **kiểm tra do API áp dụng**, và **thời điểm caller sử dụng giá trị bị ô nhiễm**.

Không nên gom mọi hiện tượng thành một nhãn “Java generic không an toàn”: một code path có thể được bảo vệ tốt ở compile time, một đường khác bị raw type phá hợp đồng, trong khi reflection vẫn có những kiểm tra **erased parameter type** của riêng nó.

## 2. Xác định những ranh giới đáng kiểm thử

Một generic API thường có các vị trí rủi ro:

1. **Biên nhập dữ liệu:** `Object`, `List<?>`, dữ liệu parse từ JSON, input từ database/legacy API; caller có thể tuyên bố type argument mà không xác thực nội dung.
2. **Biên gọi bằng raw type:** compiler biết `List` nhưng không giữ quan hệ `List<String>`, nên một lời gọi `add(Integer)` có thể đi qua với unchecked warning.
3. **Biên reflection:** `Field.set`, `Method.invoke`, `Constructor.newInstance` dùng các runtime classes ở descriptor sau erasure; không thể tự xác minh mọi parameterized arguments.
4. **Biên ép kiểu:** `(T)`, `(List<T>)`, `Class<T>.cast`; cần phân biệt cast được JVM kiểm tra thật với cast mà compiler chỉ cảnh báo.
5. **Biên container/array:** `Object[]`, `T[]`, `List<T>`, arrays covariant và generic collections invariant; runtime kiểm tra mỗi loại theo cơ chế khác nhau.
6. **Biên trả kết quả:** API trả `List<T>`, `T`, `T[]`; cast có thể chưa thất bại tại lúc return nhưng lỗi xuất hiện ở caller.
7. **Biên mutation/alias:** một giá trị đã từng đúng kiểu có thể bị thay sau đó qua raw alias, backing list hoặc thread khác.

Với từng biên, hãy viết rõ ba yếu tố: **đầu vào không đáng tin là gì**, **nghĩa vụ của API là gì**, và **test phải quan sát điều gì**. Ví dụ một adapter `Object -> List<String>` có thể có hợp đồng “từ chối ngay mọi phần tử không phải `String`”; không thể chỉ kiểm tra rằng cast ngoài thành công.

## 3. Bài test compile-time: những thao tác Java phải từ chối

Các đoạn ở mục này **cố ý không biên dịch** và phải đặt trong file kiểm tra riêng; không đưa vào chương trình runnable sau đó.

```java
import java.util.*;

class CompileTimeFailures {
    static void test() {
        List<String> names = new ArrayList<>();

        // Sai kiểu phần tử: javac từ chối.
        names.add(42);

        // Generic invariance: List<String> không là List<Object>.
        List<Object> objects = names;

        // Java Generics không nhận primitive type argument.
        List<int> numbers = new ArrayList<>();

        // Không có generic class literal List<String>.
        Class<?> token = List<String>.class;

        Object input = names;
        // Không xác minh được type argument trong Object -> List<String>.
        boolean ok = input instanceof List<String>;
    }
}
```

Dùng `javac CompileTimeFailures.java` để xác nhận compiler báo lỗi. Thông điệp cụ thể có thể khác giữa các phiên bản JDK, nhưng **kết quả mong muốn là biên dịch không thành công**.

Một bộ test không nên chỉ “chạy đoạn sai và chờ `ClassCastException`”: những dòng trên **không tạo bytecode hợp lệ** nên không có runtime behavior để quan sát. Tách *negative compilation tests* khỏi *runtime exception tests*.

### Vì sao nên có negative compilation tests?

Một thư viện generic có thể vô tình làm yếu public API từ `void add(T)` thành `void add(Object)`, hoặc trả `Object` thay `T`. Các test chỉ chạy dữ liệu đúng có thể vẫn pass, nhưng những thao tác trước kia **bị compiler chặn** nay được chấp nhận. Negative compilation tests giúp phát hiện sự thay đổi hàng rào type safety ở **compile time**.

Khi xây CI, có thể đặt fixtures cố ý không compile trong một nhóm riêng, xác minh **exit code không bằng 0** và kiểm tra mẫu diagnostic cần thiết. Không trộn chúng vào lệnh build toàn bộ source chính.

## 4. Raw type: tạo heap pollution bằng đường ghi mà compiler cảnh báo

Ví dụ:

```java
List<String> names = new ArrayList<>();
names.add("ok");

List raw = names; // Raw type: bỏ qua type argument String.
raw.add(42);      // Unchecked call, thường vẫn biên dịch.

// String second = names.get(1); // Runtime ClassCastException.
```

Ở chỗ gọi `raw.add(42)`, method `add` được nhìn qua raw `List`. Mặc dù cùng object sau đó vẫn được dùng dưới biến `List<String>`, phần tử `Integer` đã lọt vào danh sách.

Hiện tượng một biến tham số hóa tuyên bố `List<String>` nhưng vùng dữ liệu nó tham chiếu chứa một phần tử không thỏa `String` gọi là **heap pollution**.

Bài test nên kiểm tra hai điểm riêng biệt:

- **Đường ghi raw có thể đi qua:** không nhất thiết ném exception ngay khi thêm vào `ArrayList`.
- **Đường đọc typed mới có thể thất bại:** `String value = names.get(1)` yêu cầu một `String`; compiler có thể chèn `checkcast String` tại điểm tiêu thụ kết quả.

Không viết test kiểu `Object o = names.get(1)` rồi kết luận “không có lỗi”: gán vào `Object` có thể không cần cast sang `String` nên chưa chạm tới điều kiện đang muốn kiểm tra.

### Raw type không nên là một phần API sản xuất

Raw types thường xuất hiện ở mã cũ hoặc đoạn minh họa. Với code mới, giữ `List<String>`, `List<?>`, hoặc `List<T>` theo đúng nghĩa. Các test raw alias cần **cô lập trong fixture dành riêng cho hành vi không an toàn** để cảnh báo không lan ra code nghiệp vụ.

Chạy:

```bash
javac -Xlint:rawtypes -Xlint:unchecked GenericBoundaryTests.java
```

Trong ví dụ đầy đủ có raw aliases **cố ý**, nên warning tại các dòng đó là điều được chờ đợi. Không dùng `@SuppressWarnings("unchecked")` ở cấp class chỉ để build test im lặng; mục đích của test là chứng minh compiler đang cảnh báo ở đúng biên.

## 5. Unchecked cast của cả `List<T>`: kiểm tra vỏ ngoài không kiểm tra phần tử

Với dữ liệu chưa biết:

```java
Object outside = List.of("one", 7);

@SuppressWarnings("unchecked")
List<String> claimed = (List<String>) outside;
```

Cast này có thể đi qua vì object thực hiện `List`; Java không có runtime class chuyên biệt cho `List<String>` để quét và chứng minh type argument `String`.

Hai bài test khác nhau:

```java
// Thành công: chỉ xác nhận kích thước.
boolean hasTwo = claimed.size() == 2;

// Thất bại: đọc phần tử Integer và yêu cầu String.
// String second = claimed.get(1);
```

Hành vi cần nhớ:

- **`List<String>` ở source code** là một hợp đồng generic mà compiler áp dụng cho các lời gọi typed.
- **`List` ở runtime** là interface/container bên ngoài; các type arguments như `String` đã bị erasure.
- **Unchecked cast** không chứng minh phần tử thực, và runtime exception có thể xuất hiện ở vị trí rất xa dòng cast.

Một test chỉ kiểm tra `claimed != null` hoặc `claimed.size()` **không đủ** để chứng minh giá trị trả về có thể được dùng như `List<String>`.

### Vấn đề `List<?> -> List<String>`

`List<?>` nói rõ “đây là list của một kiểu chưa biết”. Nó cho phép đọc từng phần tử như `Object`, không cho phép giả định tất cả là `String`. Nếu cần nâng contract lên `List<String>`, hãy **xác thực từng phần tử** hoặc có một proof đáng tin cậy về nguồn dữ liệu.

## 6. Reflection trên field generic: erased type và generic signature là hai việc khác nhau

Định nghĩa:

```java
static final class Document {
    public List<String> tags = new ArrayList<>(List.of("start"));
}
```

Đọc metadata:

```java
Field field = Document.class.getDeclaredField("tags");

Class<?> erased = field.getType();           // List.class
Type declared = field.getGenericType();     // ParameterizedType List<String>
```

`Field.getType()` trả `Class` ứng với kiểu trường ở runtime: `List`. `Field.getGenericType()` có thể trả `ParameterizedType` chứa `String` từ **chữ ký khai báo**. Metadata của khai báo **không phải** một runtime validator quét phần tử.

Vì vậy:

```java
Document document = new Document();
field.set(document, List.of(99)); // Object là List, nên có thể được gán.

// String tag = document.tags.get(0); // ClassCastException.
```

Reflection vẫn kiểm tra ràng buộc cần thiết của `Field.set` đối với **field type có ở runtime** và khả năng truy cập. Nó **không tự xác minh** `List<String>` bằng việc duyệt cả list. Việc gán `List<Integer>` vào field khai báo `List<String>` có thể thành công nếu object là `List` hợp lệ ở mức erased type.

Test tốt phải xác nhận:

1. Metadata generic thật sự cho thấy khai báo `List<String>`.
2. Runtime type của field là `List`.
3. Một giá trị `List<Integer>` đi vào được qua reflection khi điều kiện truy cập và kiểu erasure cho phép.
4. Khi caller sau đó đọc phần tử như `String`, invariant bị vi phạm và gây `ClassCastException`.

Nếu phản xạ gán một `String` không phải `List` vào field runtime `List`, `Field.set` sẽ từ chối vì **không tương thích erased field type**. Đừng suy luận reflection “chấp nhận mọi thứ”.

## 7. Reflection trên method generic: tìm theo kiểu tham số đã xóa

Một lớp:

```java
static final class Box<T> {
    private T value;

    Box(T value) {
        this.value = value;
    }

    public void set(T next) {
        this.value = next;
    }

    T get() {
        return value;
    }
}
```

Vì `T` không có upper bound tường minh, erasure của `T` là `Object`. Phương thức reflection có thể được tìm theo:

```java
Method setter = Box.class.getDeclaredMethod("set", Object.class);
Box<String> box = new Box<>("first");

setter.invoke(box, 123); // Object argument hợp erased parameter.
```

Một phương thức `set(T)` trong Java source **không tự kiểm tra `T = String` ở runtime** khi lời gọi qua reflection dùng erased parameter `Object`. Vì thế, giá trị `Integer` có thể được lưu, và:

```java
// String value = box.get(); // ClassCastException.
```

sẽ bộc lộ heap pollution.

**Phân biệt hai loại ngoại lệ của reflection:**

- Nếu lời gọi `Method.invoke` có đối số **không phù hợp với parameter types ở runtime**, reflection có thể ném `IllegalArgumentException` ở biên invoke.
- Nếu **chính thân phương thức được gọi ném exception**, `Method.invoke` thường bọc nó trong `InvocationTargetException`. Test muốn xác minh ngoại lệ từ body nên kiểm tra `getCause()`.
- Nếu invocation thành công nhưng dữ liệu bị sai contract generic, lỗi có thể chỉ xuất hiện ở **lần đọc typed tiếp theo**, như ví dụ `Box<String>`.

Đừng viết test chỉ kiểm tra `setter.invoke` trả `null` với method `void` rồi coi dữ liệu hợp lệ: `void` có thể trả `null` qua reflection dù method đã ghi một giá trị sai kiểu.

### Bound sẽ ảnh hưởng erased signature

Với `class NumericBox<T extends Number>`, `set(T)` có erased parameter type `Number`, không phải `Object`. Reflection có thể từ chối một `String` không thuộc `Number` khi invoke, nhưng vẫn không thể tự xác minh chính xác mọi subtype `T` mà client kỳ vọng, ví dụ `Integer` so với `Double`.

## 8. `Class<T>.cast`: kiểm tra class runtime có thể chứng minh được

Một giá trị `Object` có thể được kiểm tra bằng class token cụ thể:

```java
String text = String.class.cast("safe");

// String bad = String.class.cast(7); // ClassCastException.
```

So với `(T)` trong một generic method không có runtime token, `Class<T>.cast` biết class được yêu cầu ở runtime. Cũng có `Class.isInstance`:

```java
boolean yes = String.class.isInstance("Java"); // true
boolean no = String.class.isInstance(7);       // false
```

Những góc cạnh cần thử:

- `String.class.isInstance(null)` trả `false`.
- `String.class.cast(null)` trả `null`; nếu hợp đồng muốn cấm null, cần dùng `Objects.requireNonNull` riêng.
- `Class<T>.cast` không có sẵn `List<String>.class` để xác minh type arguments lồng nhau. `List.class.cast(value)` chỉ biết object ngoài có là `List` hay không.

Một API adapter từ `Object` thành `List<T>` có thể **duyệt từng phần tử** và gọi `elementType.cast` trước khi sao chép sang một danh sách mới. Với dữ liệu nested, phải kiểm tra các tầng theo schema tương ứng, không chỉ dùng cast ngoài.

## 9. `Collections.checkedList`: runtime guard khi ghi qua wrapper

`Collections.checkedList(backing, String.class)` tạo một wrapper kiểm tra runtime class của phần tử đối với thao tác thêm/sửa **đi qua wrapper**:

```java
List<String> protectedList =
        Collections.checkedList(new ArrayList<>(), String.class);

List rawProtected = protectedList;

// rawProtected.add(4); // ClassCastException tại thao tác ghi.
```

Đây là một kiểm thử rất có giá trị vì nó cho thấy một cách kiểm tra chủ động **ngay tại biên ghi**, khác hẳn đọc muộn từ raw-polluted `ArrayList`.

Nhưng một `checkedList` **không** tự làm sạch phần tử sai kiểu đã tồn tại trước khi bọc. Nếu còn một alias khác tham chiếu **trực tiếp backing list** và ghi sai kiểu ở đó, wrapper không có cách chặn thao tác không đi qua nó.

Thử nghiệm nên bao gồm:

1. Ghi giá trị đúng kiểu qua wrapper thành công.
2. Ghi giá trị sai qua raw alias **của wrapper** bị chặn.
3. Backing không thay đổi sau lần ghi bị chặn.
4. Nếu thiết kế có thể để lộ alias backing, xác định test riêng cho đường bypass đó — hoặc thay đổi API để không lộ alias.

`checkedList` cũng không tự bảo đảm thread safety, immutability hay deep validation của generic nested elements.

## 10. Array covariance và runtime component type: hai lỗi khác nhau

Arrays được Java kiểm tra bằng **runtime component type**, khác với erased generic arguments của `List<String>`.

Trường hợp ghi sai:

```java
Object[] strings = new String[] { "x" };
strings[0] = 8; // ArrayStoreException.
```

Tham chiếu có kiểu `Object[]` nhưng object thật là `String[]`. JVM biết component type là `String` và chặn việc lưu `Integer`.

Trường hợp cast sai cả mảng:

```java
Object[] data = new Object[] { "x" };
// String[] actual = (String[]) data; // ClassCastException.
```

Mặc dù toàn bộ phần tử hiện có là `String`, object mảng vẫn có runtime class `Object[]`. Ép tham chiếu sang `String[]` không biến đổi object thành một mảng có component type khác.

Trường hợp cast đúng:

```java
Object[] data = new String[] { "x" };
String[] actual = (String[]) data; // Thành công: runtime class là String[].
```

Trong test, luôn phân biệt:

- `ArrayStoreException` là **thất bại khi ghi một phần tử** vào array có runtime component type không phù hợp.
- `ClassCastException` là **thất bại khi cast tham chiếu** tới một array class không phù hợp.

Một generic API dùng backing `new Object[n]` không thể hứa `T[]` typed thật chỉ nhờ unchecked cast `(T[]) data`. Nếu cần `String[]` đúng runtime type, phải tạo mảng đó qua `String[]::new` hoặc token/factory rồi sao chép phần tử.

## 11. Chương trình Java 17+ hoàn chỉnh: 17 bài kiểm thử độc lập

Lưu nguyên chương trình sau vào file **`GenericBoundaryTests.java`**. Nó chỉ dùng JDK chuẩn, không cần JUnit.

```java
import java.lang.reflect.*;
import java.util.*;

public class GenericBoundaryTests {
    private static int passed = 0;

    static final class Box<T> {
        private T value;

        Box(T value) {
            this.value = value;
        }

        public void set(T next) {
            this.value = next;
        }

        T get() {
            return value;
        }
    }

    static final class Document {
        public List<String> tags =
                new ArrayList<>(List.of("start"));
    }

    static void check(String label, boolean condition) {
        if (!condition) {
            throw new AssertionError(label);
        }

        passed++;
        System.out.println("PASS " + label);
    }

    static void fails(
            String label,
            Class<? extends Throwable> expected,
            Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            if (!expected.isInstance(error)) {
                throw new AssertionError(
                        label + " had "
                        + error.getClass().getSimpleName(),
                        error);
            }

            passed++;
            System.out.println(
                    "PASS " + label + " -> "
                    + error.getClass().getSimpleName());
            return;
        }

        throw new AssertionError(label + " did not throw");
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        List<String> typed = new ArrayList<>();
        typed.add("ok");

        check("typed write/read",
                typed.get(0).equals("ok"));

        List raw = typed;
        raw.add(42);

        fails("raw alias causes late failure",
                ClassCastException.class, () -> {
                    String value = typed.get(1);
                    System.out.println(value);
                });

        Object outside = List.of("one", 7);

        // Cố ý minh họa một cast không kiểm tra phần tử.
        @SuppressWarnings("unchecked")
        List<String> claimed = (List<String>) outside;

        check("unchecked outer cast does not inspect elements",
                claimed.size() == 2);

        fails("unchecked element read",
                ClassCastException.class, () -> {
                    String value = claimed.get(1);
                    System.out.println(value);
                });

        check("class token accepts String",
                String.class.isInstance("safe"));

        check("class token rejects Integer",
                !String.class.isInstance(7));

        fails("checked token cast",
                ClassCastException.class,
                () -> String.class.cast(7));

        Document document = new Document();
        Field field = Document.class.getDeclaredField("tags");

        check("field erased type",
                field.getType() == List.class);

        check("field generic signature",
                field.getGenericType()
                        instanceof ParameterizedType);

        field.set(document, List.of(99));

        fails("reflection field pollution",
                ClassCastException.class, () -> {
                    String value = document.tags.get(0);
                    System.out.println(value);
                });

        Box<String> box = new Box<>("first");
        Method setter = Box.class.getDeclaredMethod(
                "set", Object.class);

        check("generic method erased parameter",
                setter.getParameterTypes()[0] == Object.class);

        setter.invoke(box, 123);

        fails("reflection method pollution",
                ClassCastException.class, () -> {
                    String value = box.get();
                    System.out.println(value);
                });

        List<String> protectedList =
                Collections.checkedList(
                        new ArrayList<>(), String.class);

        List rawProtected = protectedList;

        fails("checkedList guards write",
                ClassCastException.class,
                () -> rawProtected.add(4));

        check("checkedList remains empty",
                protectedList.isEmpty());

        Object[] stringArray = new String[] { "x" };

        check("cast actual String[]",
                ((String[]) stringArray)[0].equals("x"));

        fails("array store guard",
                ArrayStoreException.class,
                () -> stringArray[0] = 8);

        Object[] objectArray = new Object[] { "x" };

        fails("Object[] is not String[]",
                ClassCastException.class, () -> {
                    String[] nope = (String[]) objectArray;
                    System.out.println(nope.length);
                });

        System.out.println("TOTAL PASS: " + passed);
    }
}
```

Biên dịch và chạy bằng Java 17+:

```bash
javac -encoding UTF-8 -Xlint:rawtypes -Xlint:unchecked GenericBoundaryTests.java
java GenericBoundaryTests
```

**Standard output** (không bao gồm compiler warnings):

```text
PASS typed write/read
PASS raw alias causes late failure -> ClassCastException
PASS unchecked outer cast does not inspect elements
PASS unchecked element read -> ClassCastException
PASS class token accepts String
PASS class token rejects Integer
PASS checked token cast -> ClassCastException
PASS field erased type
PASS field generic signature
PASS reflection field pollution -> ClassCastException
PASS generic method erased parameter
PASS reflection method pollution -> ClassCastException
PASS checkedList guards write -> ClassCastException
PASS checkedList remains empty
PASS cast actual String[]
PASS array store guard -> ArrayStoreException
PASS Object[] is not String[] -> ClassCastException
TOTAL PASS: 17
```

**Vì sao test runner này không cần `-ea`?** `check` và `fails` **tự ném `AssertionError`** nếu điều kiện không thỏa. Toán tử `assert` của Java có thể bị tắt trừ khi JVM được chạy với `-ea`; harness ở đây luôn hoạt động.

**Cách đọc các dòng PASS:** mỗi dòng xác nhận **hành vi kỳ vọng của đúng một tình huống**. Dòng có `-> ClassCastException` là một **negative test thành công**, không phải lỗi ngoài ý muốn của toàn chương trình.

Trong code có hai raw aliases và hai unchecked calls cố ý; `-Xlint` sẽ phát cảnh báo. Chúng thuộc **fixture kiểm thử**, không phải mẫu mã nên đưa trực tiếp vào ứng dụng sản xuất.

## 12. Vì sao kiểm tra exception phải đủ chính xác?

Một bài test kém có thể viết:

```java
try {
    suspiciousOperation();
} catch (Exception ex) {
    System.out.println("pass");
}
```

Nếu `suspiciousOperation` ném `NullPointerException` do bug khác thay vì `ClassCastException` cần kiểm tra, test vẫn báo pass. Một bài test khác còn yếu hơn là bắt mọi exception nhưng **không fail khi không có exception**.

Trong chương trình đầy đủ, helper `fails(label, expected, action)` có hai điều kiện:

1. **Đúng loại exception**: dùng `expected.isInstance(error)`. Nếu exception sai loại, ném `AssertionError`.
2. **Bắt buộc phải có exception**: nếu `action.run()` không ném, helper cũng ném `AssertionError` thay vì im lặng pass.

Với các trường hợp kiểm tra rất chặt, bạn có thể so sánh `error.getClass() == expected` để yêu cầu class chính xác. `isInstance` cho phép subtype của exception mong đợi, phù hợp khi test chỉ cần bảo đảm một loại lỗi khái quát.

**Không nên khẳng định vị trí stack trace/line number cố định** nếu code có thể thay đổi hoặc được biên dịch bằng các JDK khác nhau. Test nên giữ kỳ vọng vào loại exception và hợp đồng thực sự quan trọng.

### Vì sao không chỉ kiểm tra `ClassCastException` mà bỏ qua dữ liệu?

Một negative test phải thường xuyên kiểm tra **trạng thái sau lỗi**. Ví dụ wrapper `checkedList` từ chối thêm một phần tử sai: ngoài exception, hãy xác nhận list không chứa phần tử đó. Nếu một API hứa transaction-like behavior, các test còn phải xác minh trạng thái không bị thay đổi một phần; nhiều collection API **không** hứa atomicity cho thao tác hàng loạt.

## 13. Negative compilation tests và cảnh báo `javac`: đừng phụ thuộc thông điệp chính xác

Với compile-time failures, nên tạo các fixture riêng:

```java
// File riêng dành cho kiểm tra compiler, không thuộc file runnable.
import java.util.*;

class WrongUse {
    void addWrongValue() {
        List<String> names = new ArrayList<>();
        names.add(3); // Sai kiểu: compiler từ chối.
    }
}
```

Khi xây kiểm thử tự động, hãy kiểm tra exit code của `javac` là thất bại. Nếu bạn cần xác minh **loại diagnostic**, có thể kiểm tra các từ khóa ổn định tương đối (như kiểu thực và kiểu yêu cầu) nhưng nên tránh so khớp toàn bộ dòng lỗi, dấu câu, đường dẫn tệp hay số dòng nếu phải hỗ trợ nhiều JDK.

Đối với một fixture raw alias được **phép biên dịch với warning**, có thể kiểm thử rằng `javac -Xlint:unchecked -Xlint:rawtypes` vẫn phát thông báo. Đừng nhầm **lỗi** và **cảnh báo**: `-Werror` có thể biến warnings thành lỗi build, nhưng đó là chính sách CI được cấu hình thêm, không có nghĩa lời gọi raw vốn dĩ bị JLS cấm trong mọi cấu hình.

Một chiến lược CI hợp lý có ba nhóm:

- **Compile-positive:** các lời gọi generic đúng phải compile.
- **Compile-negative:** các lời gọi sai type contract phải bị từ chối.
- **Runtime-boundary:** fixture cố ý dùng raw/reflection/unchecked, xác nhận exception và bất biến mong muốn.

Nhóm thứ ba có thể cho phép warnings có chủ đích mà không để các cảnh báo ấy tràn sang source chính.

## 14. Chọn test input để chạm đúng biên thay vì chỉ thử “happy path”

Đối với một API generic, nên có test cases như sau:

| Biên | Positive case | Negative/edge case |
| --- | --- | --- |
| `List<String>` typed | `add("A")`, `get(0)` | `add(42)` phải fail compile |
| Raw alias | Không dùng raw trong code thường | Raw `add(42)` làm lỗi lúc đọc typed |
| `Object -> List<String>` unchecked | List thực chỉ chứa String | List hỗn hợp String/Integer vẫn cast ngoài được, lỗi khi đọc |
| `Class<String>` | `cast("A")` | `cast(42)`; xét thêm null policy |
| `Field.set` | Gán list đúng kiểu | Gán `List<Integer>` vào field `List<String>` qua reflection |
| `Method.invoke` | Invoke với erased parameter đúng và dữ liệu hợp đồng đúng | Erased parameter cho phép dữ liệu trái type argument |
| `checkedList` | `add("A")` qua wrapper | Raw alias của wrapper `add(42)` bị từ chối |
| Mảng | `String[]` qua tham chiếu `Object[]` | Ghi Integer gây `ArrayStoreException` |
| Cast mảng | Runtime object thật `String[]` | Runtime object `Object[]` dù toàn String vẫn gây `ClassCastException` |
| Generic container mutable | Dùng API typed, không lộ backing | Raw alias hoặc mutation qua backing alias phá invariant |
| Null | Hợp đồng cho phép hoặc cấm rõ ràng | `Class.cast(null)` và `isInstance(null)` khác nhau |
| Empty container | Không phát sinh lỗi giả do không có phần tử | Kiểm tra quy tắc empty/vacuous truth; không nhầm thành bằng chứng mọi phần tử thuộc một `T` cụ thể |

**Chú ý:** một danh sách rỗng thỏa `stream().allMatch(String.class::isInstance)` do tính đúng rỗng (*vacuous truth*). Điều này không khôi phục được type argument `String` trên runtime class của list; nếu cần kiểm tra danh sách không rỗng, đặt thêm điều kiện `!list.isEmpty()`.

## 15. Reflection exceptions: kiểm tra đúng tầng

Ngoài trường hợp field/method generic bị ô nhiễm, reflection có các failure modes riêng.

### 15.1. `Field.set` với erased type không tương thích

Một field kiểu `List<String>` có erased type `List`. Nếu gọi:

```java
Field field = Document.class.getDeclaredField("tags");
// field.set(document, "not a list"); // IllegalArgumentException.
```

Reflection có quyền từ chối object **không phải `List`**. Nó không bắt buộc quét nội dung danh sách khi gán một object `List` hợp lệ.

### 15.2. `Method.invoke` và `InvocationTargetException`

Nếu phương thức phản chiếu tự ném `IllegalStateException` từ thân method, một lời gọi `invoke` có thể ném **`InvocationTargetException`**. Khi test, kiểm tra:

```java
try {
    method.invoke(target, arguments);
    throw new AssertionError("Expected target failure");
} catch (InvocationTargetException ex) {
    Throwable cause = ex.getCause();
    if (!(cause instanceof IllegalStateException)) {
        throw new AssertionError("Wrong target cause", cause);
    }
}
```

Đoạn này là **mẫu trong ngữ cảnh** có sẵn `method`, `target`, `arguments`, không phải chương trình độc lập. `InvocationTargetException` thuộc cách reflection **đóng gói ngoại lệ của code được gọi**, còn `ClassCastException` sau khi caller đọc dữ liệu sai generic contract là một tình huống khác.

Nếu tham số invoke không tương thích với **erased descriptor**, exception có thể được ném ngay từ `invoke` thay vì chạy vào method body.

### 15.3. Quyền truy cập và module system

`Field.getDeclaredField` không tự nghĩa là có quyền truy cập mọi private field. `IllegalAccessException`, `InaccessibleObjectException` hoặc chính sách module encapsulation có thể ngăn reflection tùy theo target và môi trường.

Để test **generic boundary** thay vì access control, fixture nên dùng declaration được truy cập hợp lệ, như trong chương trình trên. Nếu muốn kiểm tra module security/access control, tạo một nhóm test riêng; không diễn giải một `IllegalAccessException` là bằng chứng compiler/JVM đã bảo vệ `List<String>` ở mức phần tử.

## 16. Bất biến an toàn kiểu cần test được phát biểu thành lời

Giả sử thư viện tự cài `Store<T>` bằng `private Object[] slots`:

```java
class Store<T> {
    private Object[] slots = new Object[8];
    private int size;

    void add(T value) {
        slots[size++] = value;
    }

    T get(int index) {
        @SuppressWarnings("unchecked")
        T value = (T) slots[index];
        return value;
    }
}
```

Đây là snippet minh họa **chưa kiểm tra capacity/index**. Khi review bản hoàn chỉnh, invariant cần gồm:

1. `0 <= size <= slots.length`.
2. Mọi `slots[i]` thuộc vùng `0 <= i < size` đã được ghi qua các đường được kiểm soát theo `T`.
3. Storage không thoát ra ngoài dưới dạng alias có thể sửa trực tiếp.
4. Resize và các phương thức bulk update không bỏ qua kiểm tra kiểu hoặc làm hỏng ranh giới `size`.
5. Nếu cho phép null, quy tắc null được thực thi nhất quán; nếu không, chặn trước khi ghi.
6. Mọi caller phải tuân thủ hợp đồng typed, hoặc lớp phải có thêm runtime token/validator khi cần chống raw callers.
7. Nếu dùng giữa nhiều thread, cần cơ chế đồng bộ phù hợp; một invariant đúng trong single-thread code không tự chứng minh thread safety.

Đối với cast `(T) slots[index]`, một test chỉ đọc phần tử hợp lệ không đủ. Cần kiểm tra **mọi đường ghi có thể làm hỏng invariant**: raw alias, reflection, backing alias, `set`, `addAll`, deserialization và mutation cạnh tranh nếu API cho phép.

Nếu muốn bảo vệ ngay cả trước raw callers, có thể giữ một `Class<T>` token trong instance và kiểm tra `elementType.cast(value)` ở biên ghi. Điều này giúp chống những đối tượng runtime class sai, nhưng không tự xác thực nested generic type arguments như `List<String>`.

### Vì sao lỗi thường xuất hiện ở caller?

Với `T` không bound, erasure của `T` là `Object`. Một `get()` trả `T` có thể trở thành descriptor trả `Object` ở bytecode; caller cần `String` thì phải chuyển từ `Object` sang `String` theo kiểu tĩnh. Vì vậy `ClassCastException` thường xảy ra **khi caller sử dụng giá trị**, không nhất thiết trong `get` hoặc lúc ghi bằng raw type.

Khi test, phải buộc kết quả vào một biến/hoạt động thật sự yêu cầu kiểu mong muốn:

```java
String name = polluted.get(0); // Chạm kiểm tra kiểu String.
```

Nếu chỉ gọi `polluted.get(0)` rồi bỏ kết quả, có thể chưa quan sát được lỗi mà bài test nhằm chứng minh.

## 17. Cách sửa: chuyển từ “cast trước, lỗi sau” sang “validate ở biên”

Một adapter an toàn hơn cho input `Object`:

```java
static <T> List<T> validatedCopy(
        Object candidate, Class<T> elementType) {
    Objects.requireNonNull(elementType, "elementType");

    if (!(candidate instanceof List<?> source)) {
        throw new IllegalArgumentException("not a List");
    }

    List<T> result = new ArrayList<>(source.size());
    for (Object value : source) {
        result.add(elementType.cast(
                Objects.requireNonNull(value, "element")));
    }

    return List.copyOf(result);
}
```

Ưu điểm:

- `instanceof List<?>` kiểm tra cấu trúc ngoài.
- `Class<T>.cast` kiểm tra từng phần tử theo runtime class do caller cung cấp.
- `requireNonNull` diễn đạt null policy một cách chủ động.
- Copy từng phần tử sang `List<T>` mới giúp tránh alias cấu trúc với source sau khi kiểm tra.
- Không cần unchecked cast của cả `List<T>`.

Nhưng **chú ý giới hạn**: `List.copyOf` tạo kết quả unmodifiable về cấu trúc, không deep-copy object phần tử. Nếu nguồn mutable bị sửa **trong quá trình duyệt**, code không tự cung cấp snapshot atomic; cần đồng bộ hoặc hợp đồng concurrency phù hợp. `Class<T>` cũng chỉ kiểm tra một class cụ thể chứ không xác minh `List<Map<String,Integer>>` lồng nhau.

Bộ test cho adapter nên thử đủ: input không phải list, thiếu token, list đúng, list sai phần tử, list chứa null, list rỗng, source mutate sau copy, và các trường hợp concurrent mutation nếu API tuyên bố hỗ trợ.

## 18. Dùng test framework chuyên dụng mà không đánh mất ý nghĩa bài kiểm thử

Chương trình thuần JDK phía trên đã giúp hiểu cơ chế, không cần dependency. Trong codebase dùng **JUnit 5**, có thể chuyển helper thành:

```java
// Snippet JUnit 5, cần dependency org.junit.jupiter:junit-jupiter:
import static org.junit.jupiter.api.Assertions.*;

@Test
void rawAliasPollutionIsDetectedAtRead() {
    List<String> typed = new ArrayList<>();
    List raw = typed;
    raw.add(42);

    assertThrows(ClassCastException.class, () -> {
        String value = typed.get(0);
        System.out.println(value);
    });
}
```

Cần import `org.junit.jupiter.api.Test` và cài JUnit Jupiter trong project. Snippet này là **mẫu đưa vào test class có dependency phù hợp**, không thuộc chương trình thuần JDK ở mục 11.

Lợi ích của framework: test cases độc lập, có tên rõ, dễ chạy song song/chọn lọc, và báo lỗi theo từng method. Nhưng một `assertThrows` cũng không tự bảo đảm proof: vẫn phải cho **action cụ thể** chạm đúng cast hoặc biên kiểu, và kiểm tra state sau exception khi cần.

Với dự án có code coverage, hãy dùng các test boundary để bảo đảm đúng những đường mà generic type checks bị bỏ qua; đừng xem tỉ lệ coverage cao là bằng chứng tự thân rằng API type-safe.

## 19. Những lỗi thiết kế bài kiểm thử thường gặp

**Lỗi 1 — “Cast `List<String>` thành công nên đã kiểm tra kiểu phần tử.”** Không. Unchecked cast có thể chỉ xác nhận cấu trúc ngoài; đọc typed mới phát hiện sai.

**Lỗi 2 — “Raw type viết được nên Java không có type safety.”** Không. Java vẫn có type system mạnh tại code typed; raw types là một vùng tương thích cũ cần kiểm soát.

**Lỗi 3 — “Reflection bỏ qua toàn bộ mọi kiểu.”** Không. Reflection vẫn kiểm tra erased field/parameter types, truy cập và các điều kiện runtime khác; nó không xác thực tự động mọi generic type arguments.

**Lỗi 4 — “Field.getGenericType() trả `List<String>` nên Field.set kiểm tra từng phần tử.”** Không. Metadata của khai báo và validator dữ liệu là hai việc khác nhau.

**Lỗi 5 — “Method.invoke bị lỗi ở đâu cũng thành ClassCastException.”** Không. Có thể là `IllegalArgumentException` ở biên invoke, `InvocationTargetException` khi target method ném lỗi, hoặc `ClassCastException` khi caller đọc dữ liệu ô nhiễm về sau.

**Lỗi 6 — “`Class.cast(null)` luôn ném.”** Không. Nó trả `null`; null policy phải kiểm tra riêng.

**Lỗi 7 — “CheckedList bảo vệ cả backing alias.”** Không. Nó bảo vệ các thao tác qua wrapper, không ngăn mã khác sửa trực tiếp backing list.

**Lỗi 8 — “Mảng `Object[]` có toàn String thì cast được sang `String[]`.”** Không. Runtime class của array mới quyết định phép cast.

**Lỗi 9 — “`ArrayStoreException` và `ClassCastException` là một.”** Không. Một bên thường xảy ra khi ghi phần tử mảng sai component type; bên kia khi cast tham chiếu không phù hợp.

**Lỗi 10 — “Test pass nếu bắt được bất cứ Exception nào.”** Sai. Cần đúng exception type và fail nếu không ném.

**Lỗi 11 — “Chỉ cần test input đúng.”** Không. Ranh giới raw/reflection/cast quan trọng chính ở hành vi với input sai hoặc alias không đáng tin.

**Lỗi 12 — “Warning được suppress nghĩa là an toàn.”** Không. `@SuppressWarnings` chỉ thay đổi diagnostics; không tạo runtime checks.

**Lỗi 13 — “List rỗng qua allMatch chứng minh class T cụ thể.”** Không. Điều kiện đúng rỗng không tạo ra type token runtime.

**Lỗi 14 — “Test không ném exception khi bỏ kết quả `get()` là chứng cứ an toàn.”** Không. Có thể chưa xảy ra cast sang type argument cụ thể ở caller.

## 20. Checklist cho code review và CI

Một nhóm kiểm thử ranh giới generics có thể dùng checklist sau:

1. **Liệt kê mọi trust boundary:** raw, reflection, unchecked cast, Object input, native/deserialization, array/varargs.
2. **Với mỗi boundary, có positive + negative case không?** Ghi dữ liệu đúng thành công và sai bị xử lý theo đúng hợp đồng.
3. **Có negative compilation tests?** Những lời gọi sai generic type phải bị compiler từ chối.
4. **Có bật `-Xlint:unchecked -Xlint:rawtypes`?** Cảnh báo bất ngờ trong source chính cần được xem xét.
5. **Có kiểm tra erased descriptor?** Với reflection, phân biệt `getType/getGenericType`, `getParameterTypes/getGenericParameterTypes`.
6. **Exception kỳ vọng đúng lớp chưa?** Không bắt rộng rồi pass; với `InvocationTargetException` kiểm tra cause.
7. **Có ép typed read thật?** Một bài test heap pollution phải buộc compiler/runtime kiểm tra kiểu mong muốn tại điểm tiêu thụ.
8. **Có thử runtime token/validator?** `Class.cast`, null, wrong class và nested data.
9. **Có kiểm tra state sau thất bại?** Wrapper hoặc adapter đã thay đổi container trước khi từ chối hay chưa?
10. **Có kiểm tra alias/mutation?** Backing list private không lộ ra, raw caller có thể phá invariant hay không, source có thể bị sửa sau validation không?
11. **Arrays được thử cả cast và store?** Phân biệt `ClassCastException` với `ArrayStoreException`.
12. **Có tránh unchecked casts không cần thiết?** Ưu tiên `List<?>`, typed copy, token, hoặc API generic chuẩn.
13. **Có tách raw-fixture khỏi production code?** Không đặt `@SuppressWarnings` quá rộng để che các warning mới.
14. **Có chạy trên phiên bản Java mục tiêu?** Chẳng hạn dùng `javac --release 17` khi yêu cầu tương thích Java 17.
15. **Có xác minh cả hành vi và tài liệu?** Javadoc phải nêu null policy, loại exception, ownership, mutability, và lúc nào validation diễn ra.

## 21. Kết luận

Kiểm thử raw type, reflection và ép kiểu là cách nhìn rõ **ranh giới của bảo đảm compile-time trong Java Generics**. Mã typed thông thường được compiler bảo vệ; raw aliases và unchecked casts có thể phá hợp đồng; reflection thường kiểm tra **kiểu đã xóa** nhưng không kiểm tra từng generic type argument; các container và array có runtime guards khác nhau.

Bộ test tốt phải chứng minh ba điều: **compiler chặn lời gọi không hợp lệ ở nơi có đủ thông tin kiểu**, **runtime phát hiện hoặc không phát hiện sai kiểu tại đúng ranh giới theo thiết kế**, và **API có cách ngăn dữ liệu chưa đáng tin lan sang vùng typed**, chẳng hạn `Class<T>.cast`, validated copy hoặc wrapper có runtime checks. Quan trọng hơn cả, mỗi unchecked operation phải đi cùng một bất biến an toàn có thể phát biểu và kiểm thử, thay vì chỉ được che cảnh báo.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-testing-and-diagnostics-005">Kiểm thử heap pollution và thời điểm phát sinh ClassCastException</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử heap pollution và thời điểm phát sinh ClassCastException trong Java Generics

## 1. Tại sao code generic có thể biên dịch, ghi dữ liệu sai kiểu mà chưa ném lỗi?

Java Generics giúp kiểm tra kiểu ở **compile time**, nhưng phần lớn generic type arguments bị xóa bởi **type erasure**. Khi chương trình dùng raw types, unchecked casts, varargs có component không reifiable hoặc các đường alias không an toàn, một collection có thể chứa dữ liệu **không phù hợp với kiểu mà chương trình đang tin tưởng**.

Hiện tượng đó gọi là **heap pollution**. Một ví dụ quan trọng:

~~~java
List<String> names = new ArrayList<>(List.of("safe"));

List raw = names;
raw.add(42); // Biên dịch có warning; thường chưa ném exception.

Object value = names.get(1); // Đọc dưới Object: có thể thành công.
String name = names.get(1);  // ClassCastException khi dùng như String.
~~~

Điểm bất ngờ: không nhất thiết xảy ra lỗi tại `raw.add(42)`. Raw alias cho phép gọi `add` mà không có ràng buộc String ở compile time. Đối tượng thực được thêm là Integer, và `ArrayList` không kiểm tra generic argument String ở runtime.

Lỗi xuất hiện khi một chỗ dùng tiếp theo **đòi coi Integer đó là String**. Javac thường chèn `checkcast java/lang/String` ở đường đọc typed value; JVM thực hiện cast này và phát `ClassCastException`.

Vì thế khi kiểm thử generic code cần tách ít nhất bốn thời điểm:

1. **Biên dịch:** có rawtypes/unchecked warnings hay compile errors?
2. **Tạo đường alias/cast:** phép cast kiểm tra runtime class nào, và đã kiểm tra generic arguments chưa?
3. **Ghi dữ liệu:** array/list implementation có chặn kiểu phần tử ở đây không?
4. **Đọc/sử dụng typed value:** JVM có cần thực hiện cast xuống kiểu cụ thể và ném ClassCastException không?

**Mục tiêu kiểm thử không phải chỉ hỏi “có ném ClassCastException không?” mà phải xác định “ở thao tác nào, vì cơ chế kiểm tra kiểu nào, và với điều kiện gì?”**

## 2. Ba khái niệm không nên trộn lẫn

| Khái niệm | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| **Unchecked warning** | Compiler không chứng minh được an toàn generic của một phép gán/gọi/cast. | `raw.add(42)`; `(List<String>) unknown` |
| **Heap pollution** | Một tham chiếu kiểu tham số hóa được dùng với object/dữ liệu không thỏa cam kết kiểu. | `List<String>` bị ghi Integer qua raw alias. |
| **ClassCastException** | Runtime cast tới class/interface đích thất bại. | Đọc Integer thành String. |

Một **warning** chưa chứng minh thực tế đã có dữ liệu sai. Một **unchecked cast** có thể thành công về mặt runtime class nhưng vẫn tiềm ẩn sai generic argument. Một list đã bị pollution cũng có thể tiếp tục hoạt động ở một số phép đọc như `size()` hoặc đọc trực tiếp vào Object, rồi chỉ thất bại nhiều bước sau khi giá trị phải trở thành String.

Bởi vậy:
- **Compile success** không chứng minh dữ liệu hợp kiểu.
- **Không thấy exception khi ghi** không chứng minh phép ghi an toàn.
- **Có warning** không chứng minh tất cả lời gọi chắc chắn ném exception.
- **Có ClassCastException** không nhất thiết chứng minh nguyên nhân là heap pollution; một checked cast sai lớp thông thường cũng gây ClassCastException.

Các khẳng định trong bài được minh họa và kiểm chứng qua chương trình Java 17 đầy đủ ở phần 9.

## 3. Type erasure giải thích điều gì?

Với một collection:

~~~java
List<String> strings = new ArrayList<>();
List<Integer> numbers = new ArrayList<>();
~~~

cả hai có thể dùng cùng runtime class `java.util.ArrayList`. JVM không tạo `ArrayList<String>` và `ArrayList<Integer>` thành hai class runtime độc lập tương ứng với type arguments.

`List<E>.get(int)` về bytecode trả đối tượng theo kiểu erased `Object`. Khi caller viết:

~~~java
String s = strings.get(0);
~~~

javac biết ở source rằng `strings` là List<String>, nên có thể tạo code tương ứng:

~~~text
invokeinterface java/util/List.get:(I)Ljava/lang/Object;
checkcast java/lang/String
astore ...
~~~

Đây là minh họa **dạng bytecode**, không phải offset lệnh cố định. Cast bổ sung sau lời gọi `get` kiểm tra object thực sự có thể dùng như String không.

Trong khi đó:

~~~java
Object o = strings.get(0);
~~~

không cần cast từ Object xuống String để đáp ứng biến đích. Nếu phần tử là Integer do pollution, phép đọc thành Object vẫn hợp lệ.

**Thời điểm lỗi phụ thuộc vào ngữ cảnh cần kiểu đích.** Một phép gọi method, assignment, return, method reference hoặc lambda có thể buộc compiler/JVM kiểm tra kiểu khác nhau. Đừng hứa rằng “mỗi lần gọi get đều ném ngay” hoặc rằng “mọi lời gọi get đều an toàn”.

### Vì sao ArrayList.add không tự kiểm tra generic E?

Ở bytecode, generic `E` đã bị erasure (thường là Object trong Collection). `ArrayList.add(Object)` xử lý object reference; nó không có runtime class token String gắn riêng với mỗi List<String> để kiểm tra phần tử thêm vào.

Ngược lại, một wrapper được tạo với token `String.class` có thể chủ động kiểm tra lúc ghi; xem `Collections.checkedList` ở phần 12.

## 4. Kịch bản 1: pollution thông qua raw List

Một raw alias làm mất type arguments:

~~~java
List<String> names = new ArrayList<>(List.of("safe"));

List raw = names;
raw.add(42);
~~~

Compiler, khi bật `-Xlint:rawtypes,unchecked`, có thể báo:
- `[rawtypes] found raw type: List` tại khai báo `List raw`.
- `[unchecked] unchecked call to add(E)` tại `raw.add(42)`.

Nhưng Java giữ khả năng dùng raw types để tương thích với code cũ. Compiler warning không biến thành runtime guard tự động.

Sau phép ghi:

~~~java
System.out.println(names.size()); // 2
~~~

vẫn có thể chạy bình thường vì tính độ dài không đòi đọc Integer dưới dạng String.

Một phép đọc:

~~~java
Object second = names.get(1); // Integer object được nhìn như Object.
~~~

cũng có thể thành công.

Nhưng:

~~~java
String second = names.get(1); // ClassCastException.
~~~

khi JVM cần cast kết quả về String, Integer không tương thích và exception xuất hiện.

**Test đúng:** đặt assertion/exception expectation tại typed read, đồng thời kiểm tra các bước trước thực sự đã xảy ra mà không ném exception. Nếu test chỉ bọc cả phương thức từ đầu đến cuối bằng `expectClassCast`, ta biết có lỗi nhưng không biết nó xuất hiện ngay lúc write hay sau một read.

### 4.1. Chỗ sai nằm ở đâu?

Lỗi thiết kế nằm ở đường **ghi không an toàn qua raw alias**. Typed read là điểm lỗi **bộc lộ**. Stack trace thường trỏ tới typed read, nhưng hành vi gây pollution có thể đã diễn ra nhiều lời gọi trước và ở một lớp khác.

Khi debug, tìm cả nơi exception xuất hiện lẫn đường dữ liệu được thêm vào collection. Fix thực tế nên chặn raw/unchecked writes ở biên, không chỉ catch ClassCastException tại mọi nơi đọc.

## 5. Kịch bản 2: unchecked cast kiểm tra raw class, không kiểm tra type argument

Ví dụ:

~~~java
Object unknown = List.of(7);

List<String> forged = (List<String>) unknown;
~~~

Với `-Xlint:unchecked`, compiler báo **unchecked cast**. Ở runtime, object là một List nên bước kiểm tra class/interface List thành công. JVM **không duyệt danh sách để xác minh các phần tử là String**.

Những thao tác sau vẫn có thể chạy:

~~~java
int size = forged.size();     // 1.
Object first = forged.get(0); // Integer nhưng đích là Object.
~~~

Trong khi:

~~~java
String first = forged.get(0); // ClassCastException.
~~~

Cần phân biệt hai điều:
- Một cast từ Object sang `List<String>` **vẫn có thể ném ClassCastException ngay tại cast** nếu object thực tế hoàn toàn không phải List, ví dụ `"hello"`.
- Nếu object thực tế là List nhưng có phần tử không phải String, phần **raw class check** có thể qua, còn generic element type chưa được xác minh. Lỗi sau đó xảy ra khi phần tử bị dùng như String.

Vì vậy câu “unchecked cast **luôn** đợi đến lúc đọc phần tử mới lỗi” cũng sai. Thời điểm còn phụ thuộc **object có vượt qua kiểm tra raw type hay không**.

### 5.1. List<?> không khẳng định type argument String

Khi chưa xác minh dữ liệu, có thể giữ nó trong một biến với hợp đồng trung thực hơn:

~~~java
if (unknown instanceof List<?> candidates) {
    for (Object candidate : candidates) {
        System.out.println(candidate);
    }
}
~~~

`instanceof List<?>` kiểm tra runtime List thích hợp, nhưng **không chứng minh List<String>**. Cần duyệt và kiểm tra từng phần tử nếu sẽ công bố kết quả dưới dạng List<String>.

## 6. Kịch bản 3: generic varargs và array backing

Varargs `T...` được biểu diễn bằng một **array**. Vấn đề xuất hiện với kiểu phần tử không reifiable, ví dụ `List<String>`:

~~~java
static void unsafeVarargs(List<String>... groups) {
    Object[] alias = groups;
    alias[0] = List.of(99);
}
~~~

Java không cho tạo một generic array trực tiếp bằng biểu thức:

~~~java
// List<String>[] array = new List<String>[2];
// Compile error: generic array creation.
~~~

Tuy nhiên, khai báo `List<String>...` hợp lệ và lời gọi:

~~~java
unsafeVarargs(List.of("before"));
~~~

được phép có **unchecked varargs warnings**. Compiler chuẩn bị một array có runtime component type `List`, không thể tạo một component class List<String> riêng vì type arguments không reifiable.

Trong method:
- `groups` có kiểu tĩnh `List<String>[]`.
- `groups.getClass().getComponentType()` có thể là `List.class` khi compiler tạo backing `List[]`.
- `Object[] alias = groups` hợp lệ vì Java arrays covariant.
- `alias[0] = List.of(99)` có thể qua runtime array-store check vì giá trị vẫn là List.
- `groups[0].get(0)` trả Integer thực tế, nhưng typed read như String về sau có thể gây ClassCastException.

Một phép kiểm tra như `groups[0].size()` hoặc `Object x = groups[0].get(0)` không buộc có cast xuống String, nên có thể chạy trước khi lỗi xảy ra.

### 6.1. Vì sao đây là heap pollution đặc trưng của generic varargs?

Java arrays **reifiable** ở mức runtime component class: List[] biết “mỗi phần tử phải là List”, nhưng không biết phần tử thứ nhất phải là List<String> và phần tử thứ hai cũng phải là List<String>.

Do đó array backing cho `List<String>...` cho phép một object `List<Integer>` vượt qua kiểm tra component List. Lỗi không nằm ở `List.of(99)` tự nó; đây là một List<Integer> hoàn toàn hợp lệ. Lỗi ở việc gán nó vào vị trí mà chương trình đang đối xử như List<String>.

### 6.2. Cảnh báo compiler nào cần xem?

Với:

~~~bash
javac --release 17 -Xlint:rawtypes,unchecked,varargs HeapPollutionTest.java
~~~

Ở ví dụ không đánh dấu an toàn, javac phát các thông điệp đại ý:

~~~text
[unchecked] Possible heap pollution from parameterized vararg type List<String>
[unchecked] unchecked generic array creation for varargs parameter of type List<String>[]
~~~

Hai warning này thuộc category `unchecked`, dù tên có chữ varargs. `-Xlint:varargs` cũng hữu ích khi kiểm tra những thao tác dùng backing array trong method gắn `@SafeVarargs` nhưng vẫn làm lộ array. Đừng nhầm tên thông điệp với tên category.

## 7. Kịch bản 4: tại sao String[] phát ArrayStoreException sớm hơn?

Một Java array cụ thể **biết runtime component class**:

~~~java
String[] textArray = {"ok"};
Object[] alias = textArray;

alias[0] = 99; // ArrayStoreException ngay ở phép ghi.
~~~

Ở runtime, object array thật là `String[]`. JVM kiểm tra phần tử sắp ghi có tương thích với String không, nên từ chối Integer ngay tại `aastore`.

So sánh:

| Trường hợp | Component runtime | Ghi sai | Lỗi thường xuất hiện |
| --- | --- | --- | --- |
| `String[]` qua `Object[]` alias | `String` | Ghi Integer | **ArrayStoreException tại write**. |
| `List<String>[]` backing từ varargs | `List` | Ghi `List<Integer>` | Có thể **không lỗi tại write**; typed read về sau có thể CCE. |
| `List<String>` qua raw `List` alias | Không có runtime String element type trên ArrayList | `add(Integer)` | Có thể **không lỗi tại write**; typed read về sau có thể CCE. |
| `Collections.checkedList(...,String.class)` qua chính wrapper | Wrapper mang `String.class` token | `add(Integer)` | **ClassCastException tại write qua wrapper**. |

Vì sao array generic không được tạo trực tiếp theo kiểu `new List<String>[2]`? Nếu nó được cho phép như một array thật nhưng chỉ reify raw List, những cam kết về generic argument có thể dễ dàng bị phá bởi array covariance. Việc cấm generic array creation với non-reifiable component type giúp giảm các đường type-unsafe, trong khi cơ chế varargs vẫn cần hỗ trợ source syntax và có cảnh báo thích hợp.

## 8. Thiết kế test: xác nhận cả **không lỗi sớm** lẫn **lỗi đúng điểm**

Một test không tốt chỉ viết:

~~~java
try {
    doEverything();
} catch (ClassCastException e) {
    // coi là pass
}
~~~

Cách này không biết exception xảy ra tại:
- Cast ban đầu;
- Thao tác ghi qua wrapper;
- Typed read;
- Một method reference/lambda;
- Một bridge method;
- Hay một đoạn code hoàn toàn không liên quan.

Một test tốt cần chia thành các assertions rõ:
1. Ghi qua raw alias đã thực hiện.
2. Kích thước/data thực tế cho thấy có phần tử sai kiểu.
3. Đọc như Object thành công.
4. Chính assignment vào String phát ClassCastException.
5. Với array String[], ghi qua Object[] phát ArrayStoreException **ngay tại write**.
6. Với checkedList, ghi sai qua wrapper phát ClassCastException **ngay tại write**.
7. Validator ở biên từ chối dữ liệu sai và chỉ trả typed copy sau kiểm tra.

Dùng một helper dạng `expect(Class<? extends Throwable>,Runnable,String)` có thể phân biệt chính xác exception type và vị trí được bọc. Không chỉ kiểm tra “có Exception nào đó”: ClassCastException, ArrayStoreException, IllegalArgumentException và AssertionError mang ý nghĩa khác nhau.

### 8.1. Test nên fail khi không có exception như mong đợi

Một helper đáng tin phải:
- Gọi đúng operation cần thử.
- Nếu operation **không ném**, ném AssertionError để fail test.
- Nếu ném **đúng expected class**, báo test pass.
- Nếu ném loại khác, **không được nuốt lỗi** mà phải fail.

Trong ví dụ hoàn chỉnh dưới đây, helper so sánh bằng `expected.isInstance(thrown)` và tăng bộ đếm cho từng phép kiểm tra thành công. Nó không phụ thuộc vào Java assertions, vốn bị tắt mặc định nếu không bật `-ea`.

## 9. Chương trình kiểm thử Java 17 đầy đủ, không cần dependency

Lưu nguyên chương trình sau thành **HeapPollutionTest.java**:

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HeapPollutionTest {
    private static int checks;

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
        checks++;
        System.out.println("PASS " + label);
    }

    private static void expect(
            Class<? extends Throwable> expected,
            Runnable action,
            String label) {

        try {
            action.run();
            throw new AssertionError(
                    label + ": expected " + expected.getSimpleName()
            );
        } catch (Throwable thrown) {
            if (!expected.isInstance(thrown)) {
                throw thrown;
            }
            checks++;
            System.out.println(
                    "PASS " + label + " -> "
                            + thrown.getClass().getSimpleName()
            );
        }
    }

    static List<String> validateStrings(List<?> candidates) {
        List<String> clean = new ArrayList<>();

        for (Object value : candidates) {
            if (!(value instanceof String text)) {
                throw new IllegalArgumentException(
                        "Non-string element"
                );
            }
            clean.add(text);
        }

        return List.copyOf(clean);
    }

    // Không an toàn: cố ý minh họa pollution qua List[] backing.
    static void unsafeVarargs(List<String>... groups) {
        check(
                groups.getClass().getComponentType() == List.class,
                "varargs runtime component is List"
        );

        Object[] alias = groups;
        alias[0] = List.of(99);

        check(
                groups[0].size() == 1,
                "varargs polluted without immediate failure"
        );

        Object rawRead = groups[0].get(0);
        check(
                rawRead.equals(99),
                "varargs reading as Object succeeds"
        );

        expect(ClassCastException.class, () -> {
            String value = groups[0].get(0);
            System.out.println(value);
        }, "varargs typed read");
    }

    public static void main(String[] args) {
        // 1. Raw alias: ghi sai nhưng chưa lỗi, typed read mới lỗi.
        List<String> names =
                new ArrayList<>(List.of("safe"));
        List raw = names;
        raw.add(42);

        check(
                names.size() == 2,
                "raw insertion did not throw"
        );

        Object second = names.get(1);
        check(
                second.equals(42),
                "Object read skips String checkcast"
        );

        expect(ClassCastException.class, () -> {
            String text = names.get(1);
            System.out.println(text);
        }, "raw typed read");

        // 2. Unchecked cast từ Object chỉ xác nhận raw List.
        Object unknown = List.of(7);
        List<String> forged = (List<String>) unknown;

        check(
                forged.size() == 1,
                "unchecked cast checked only raw List"
        );

        expect(ClassCastException.class, () -> {
            String text = forged.get(0);
            System.out.println(text);
        }, "unchecked cast typed read");

        // 3. List<String>... mang backing List[].
        unsafeVarargs(List.of("before"));

        // 4. Array reification: lỗi ngay ở phép ghi vào String[].
        String[] textArray = {"ok"};
        Object[] arrayAlias = textArray;

        expect(ArrayStoreException.class, () ->
                        arrayAlias[0] = 99,
                "reified String array write"
        );

        check(
                "ok".equals(textArray[0]),
                "reified array unchanged"
        );

        // 5. Duyệt/kiểm tra ở biên và tạo typed copy.
        expect(IllegalArgumentException.class,
                () -> validateStrings(names),
                "boundary validator rejects corrupted list"
        );

        check(
                validateStrings(List.of("A", "B"))
                        .equals(List.of("A", "B")),
                "validator makes typed copy"
        );

        // 6. Runtime-checked wrapper chặn sai kiểu tại write.
        List<String> guarded = Collections.checkedList(
                new ArrayList<>(), String.class
        );
        List rawGuarded = guarded;

        expect(ClassCastException.class,
                () -> rawGuarded.add(99),
                "checkedList rejects invalid insertion"
        );

        check(
                guarded.isEmpty(),
                "checkedList backing unchanged"
        );

        System.out.println("TOTAL=" + checks);
    }
}
~~~

Biên dịch với các compiler warnings cần quan sát, sau đó chạy:

~~~bash
javac --release 17 -Xlint:rawtypes,unchecked,varargs HeapPollutionTest.java
java HeapPollutionTest
~~~

Chương trình **được phép có các unchecked/rawtypes warnings** vì nó cố ý dựng những trường hợp không an toàn nhằm kiểm thử. Trong một ứng dụng production, chúng không phải warning để bỏ qua; trong một bài test giáo dục này, chúng là một phần của đối tượng nghiên cứu.

**Stdout chính xác khi chạy:**

~~~text
PASS raw insertion did not throw
PASS Object read skips String checkcast
PASS raw typed read -> ClassCastException
PASS unchecked cast checked only raw List
PASS unchecked cast typed read -> ClassCastException
PASS varargs runtime component is List
PASS varargs polluted without immediate failure
PASS varargs reading as Object succeeds
PASS varargs typed read -> ClassCastException
PASS reified String array write -> ArrayStoreException
PASS reified array unchanged
PASS boundary validator rejects corrupted list -> IllegalArgumentException
PASS validator makes typed copy
PASS checkedList rejects invalid insertion -> ClassCastException
PASS checkedList backing unchanged
TOTAL=15
~~~

Chương trình dùng **explicit checks và expect** thay cho `assert` nên không cần thêm `-ea`. Nếu một kỳ vọng sai, chương trình ném AssertionError hoặc exception không mong đợi và test thất bại.

### 9.1. Thứ tự 15 lần kiểm tra

**Nhóm raw alias (3 checks):**
1. Raw add không ném.
2. Đọc phần tử Integer qua biến Object thành công.
3. Đọc phần tử đó vào String ném ClassCastException.

**Nhóm unchecked cast (2 checks):**
4. Cast object có runtime type List thành List<String> không xác nhận type argument.
5. Đọc Integer trong list đã cast như String ném ClassCastException.

**Nhóm generic varargs (4 checks):**
6. Runtime array component là List.
7. Đưa List<Integer> vào List[] không ném tức thời.
8. Đọc Integer từ group như Object thành công.
9. Đọc cùng phần tử như String ném ClassCastException.

**Nhóm array reification (2 checks):**
10. Ghi Integer vào runtime String[] qua Object[] ném ArrayStoreException.
11. Array gốc vẫn chứa "ok".

**Nhóm validation (2 checks):**
12. Validator từ chối danh sách đã bị hỏng.
13. Validator chấp nhận và sao chép danh sách toàn String.

**Nhóm checkedList (2 checks):**
14. Raw add sai kiểu qua checked wrapper ném ClassCastException tại write.
15. Wrapper không chứa phần tử sau phép ghi thất bại.

Đây là **một chương trình có các test độc lập ở cấp thao tác**, không phải 15 tệp test riêng. Việc gom test trong một chương trình giúp so sánh thời điểm phát sinh exception bằng cùng một môi trường Java.

## 10. Cảnh báo biên dịch là bằng chứng cần lưu trong kiểm thử

Lệnh:

~~~bash
javac --release 17 -Xlint:rawtypes,unchecked,varargs HeapPollutionTest.java
~~~

có thể in những diagnostics với nội dung tiêu biểu:

~~~text
[unchecked] Possible heap pollution from parameterized vararg type List<String>
[rawtypes] found raw type: List
[unchecked] unchecked call to add(E) as a member of the raw type List
[unchecked] unchecked cast
[unchecked] unchecked generic array creation for varargs parameter of type List<String>[]
~~~

Với bản chương trình này, hai khai báo raw List và hai lời gọi add qua raw receiver làm số warnings nhiều hơn số dòng thông điệp đại diện bên trên. Số thứ tự dòng, đường dẫn và chi tiết tổng số warnings phụ thuộc nguồn và compiler version; hãy quan sát **category và vị trí thao tác** chứ không hard-code toàn bộ compiler stderr như stdout ổn định.

**Phân biệt stderr của javac và stdout của java:**
- Compiler warnings xuất hiện trong giai đoạn **build**.
- Các dòng `PASS` là **kết quả thực thi test**.
- Có warnings chưa có nghĩa javac đã thất bại; bài test này chủ động tạo code compile được nhưng unsafe.
- Nếu dự án bật `-Werror`, warnings khiến build thất bại theo chính sách quality gate; nên tách bài test minh họa unsafe code khỏi production build hoặc sử dụng chiến lược test riêng phù hợp.

Trong môi trường dùng nhiều compiler/language release, việc kiểm tra cả compile diagnostics lẫn runtime behavior giúp phát hiện thay đổi về warning location và logic test.

## 11. Dùng javap tìm điểm checkcast được chèn

Biên dịch:

~~~bash
javac --release 17 -Xlint:unchecked HeapPollutionTest.java
~~~

Kiểm tra bytecode:

~~~bash
javap -c -p HeapPollutionTest
~~~

Những đoạn tương ứng với typed read có thể chứa:

~~~text
invokeinterface java/util/List.get:(I)Ljava/lang/Object;
checkcast     java/lang/String
~~~

Đây là **mẫu lệnh quan trọng**, không phải chuỗi đầy đủ để dò chính xác từng offset trên mọi javac. Chương trình dùng lambda, nên một số `checkcast` có thể nằm trong **synthetic lambda methods** xuất hiện trong output `javap -p -c`.

### 11.1. Phân biệt compile-time generic type và runtime cast

`List<String>` bảo đảm compiler chỉ cho dùng kết quả `get` như String trong code thông thường đã được kiểm tra. Nhưng phần tử thật có thể sai nếu collection bị pollution qua raw/unchecked alias. Khi biến đích hoặc phương thức đòi String, bytecode có `checkcast String`, và JVM kiểm tra dữ liệu thực.

Vì vậy các dòng:

~~~java
Object rawRead = names.get(1);
String typedRead = names.get(1);
~~~

khác nhau về **kiểu đích**. Việc cùng gọi `get` không có nghĩa cùng một loại checkcast được thực hiện.

### 11.2. Stack trace thường chỉ cho biết chỗ lỗi phát hiện

Nếu một List<String> được truyền qua nhiều service rồi mới typed read, stack trace ở ClassCastException thường trỏ tới lúc đọc/cast. Nó chưa trả lời “ai đưa Integer vào List<String>?”. Kiểm thử nên mô hình hóa **đường ghi gây pollution** và **điểm đọc phát hiện sai** như hai sự kiện độc lập.

## 12. Collections.checkedList: chặn lỗi ở biên ghi, nhưng có giới hạn

Java cung cấp một wrapper cho runtime type checking:

~~~java
List<String> checked = Collections.checkedList(
        new ArrayList<>(),
        String.class
);
~~~

Nó giữ một runtime class token String và kiểm tra những element mới được thêm qua **checked wrapper**. Ví dụ:

~~~java
List raw = checked;
raw.add(99); // ClassCastException ngay tại write qua wrapper.
~~~

Trong chương trình hoàn chỉnh, phép thêm này được bọc bằng `expect(ClassCastException.class, ...)` và sau đó test xác nhận checked list vẫn rỗng.

### 12.1. Wrapper không thể loại bỏ mọi alias nguy hiểm

Ví dụ:

~~~java
List<String> backing = new ArrayList<>();
List<String> guarded = Collections.checkedList(
        backing, String.class
);

// Nếu một người giữ raw alias đến chính backing list,
// họ có thể bypass wrapper và đưa object sai vào backing.
~~~

Một checked wrapper chỉ giám sát các mutation đi **qua chính wrapper**. Nó không có khả năng ngăn đường truy cập khác sửa trực tiếp backing collection. Nó cũng không tự sửa phần tử sai kiểu đã có từ trước khi tạo wrapper, và không deep-validate mọi cấu trúc generic lồng nhau.

Bởi vậy nếu dữ liệu đầu vào không đáng tin, tốt hơn nên **kiểm tra và tạo một collection mới** tại ranh giới tin cậy, đồng thời không phát tán mutable alias của collection gốc.

### 12.2. CheckedList là runtime checking, không phải reified List<String>

`Collections.checkedList` dùng token `Class<E>` để kiểm tra đối tượng thêm trực tiếp. Nó không biến toàn bộ Java generics thành reified. Với kiểu phần tử lồng nhau như `List<List<String>>`, chỉ có raw `List.class` không đủ để kiểm tra mọi String trong các list con.

Nếu cần xác thực lồng nhau, hãy tự duyệt các tầng hoặc dùng validator phù hợp với schema nghiệp vụ.

## 13. Xác thực dữ liệu tại biên tốt hơn unchecked cast

Một boundary method có thể nhận `List<?>` và tạo `List<String>` mới:

~~~java
static List<String> validateStrings(List<?> candidates) {
    List<String> clean = new ArrayList<>();

    for (Object value : candidates) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(
                    "Non-string element"
            );
        }
        clean.add(text);
    }

    return List.copyOf(clean);
}
~~~

Đây là Java 17 code: pattern matching `instanceof String text` giúp kiểm tra runtime class của từng phần tử. Nếu phần tử không phải String (kể cả null), method ném IllegalArgumentException theo chính sách đã chọn.

**Tại sao chỉ cast hoặc copy chưa đủ?**

~~~java
// Không xác minh String elements:
List<String> forged = (List<String>) unknown;

// Sao chép qua constructor/copyOf không tự xác minh từng E:
// nếu nguồn đã bị pollution, chỉ copy có thể giữ dữ liệu sai.
~~~

Một constructor collection chỉ sao chép phần tử theo kiểu runtime Object của implementation; nó không có class token String tự động trong generics. Đối với boundary chưa tin cậy, **validate từng phần tử rồi mới xây typed copy**.

### 13.1. Quy tắc của List.copyOf

`List.copyOf(clean)` trả List không cho sửa đổi qua API thông thường. Nó là một **shallow copy**: nếu element là object mutable, copy không tự làm các object bên trong trở thành immutable. `List.copyOf` cũng từ chối null elements theo hợp đồng. Trong ví dụ trên, null đã bị loại bởi pattern check trước đó.

Không nên nói `List.copyOf` tự động “sửa heap pollution”. Nó hữu ích để ngắt đường chia sẻ mutable alias **sau khi dữ liệu đã được xác thực**.

## 14. @SafeVarargs không phải cách sửa một method thực sự unsafe

Một số APIs varargs generic là an toàn nếu chúng chỉ đọc các phần tử đúng cách và không ghi/làm lộ mảng backing. Java cho phép tác giả đặt `@SafeVarargs` trên các khai báo phù hợp (constructors, static/final/private varargs methods) để cam kết triển khai không thực hiện thao tác varargs unsafe.

Ví dụ phương thức có thể chứng minh an toàn khi chỉ đọc và tạo List mới:

~~~java
@SafeVarargs
static <T> List<T> copyValues(T... values) {
    List<T> result = new ArrayList<>();
    for (T value : values) {
        result.add(value);
    }
    return List.copyOf(result);
}
~~~

Đoạn code giả định import `java.util.List` và `java.util.ArrayList`; phương thức có thể đặt trong một class. Nó không làm lộ backing array và không ghi loại khác vào mảng.

Nhưng phương thức `unsafeVarargs` trong chương trình test cố ý thực hiện:

~~~java
Object[] alias = groups;
alias[0] = List.of(99);
~~~

Đặt `@SafeVarargs` lên method đó **không biến nó thành an toàn**; chỉ có thể giấu một số warnings ở call sites và tuyên bố sai hợp đồng. Runtime vẫn có thể xảy ra heap pollution và ClassCastException.

Nếu muốn test được sự phát hiện lỗi, hãy giữ unsafe version riêng trong test code, đừng annotate nó để làm compile output đẹp.

## 15. Khi nào exception có thể xuất hiện sớm hoặc muộn hơn?

Một bảng thời điểm phát hiện:

| Thao tác | Điều kiện | Kết quả cần kiểm thử |
| --- | --- | --- |
| Cast Object sang String | Object thật là Integer | ClassCastException **ngay tại cast**. |
| Cast Object chứa một List<Integer> sang List<String> | Object runtime là List | Raw List cast có thể qua; generic elements chưa được xác thực. |
| Raw `add(Integer)` vào ArrayList có typed alias List<String> | Không có checked wrapper/token | Có thể qua; typed read về sau mới fail. |
| `String s = names.get(1)` sau pollution | Phần tử thực là Integer | ClassCastException tại typed read/inserted checkcast. |
| `Object o = names.get(1)` | Phần tử thực là Integer | Không cần cast thành String; có thể qua. |
| `alias[0] = 99` với alias trỏ tới String[] | Runtime component String | ArrayStoreException **ngay tại write**. |
| `alias[0] = List.of(99)` với alias trỏ tới List[] | Runtime component List | Qua array store check; typed read sau có thể fail. |
| Raw add qua Collections.checkedList với token String | Wrapper thật kiểm tra từng insertion | ClassCastException **ngay tại write qua wrapper**. |
| Validate List<?> với từng phần tử | Encounter Integer khi yêu cầu String | Validator có thể ném IllegalArgumentException theo chính sách tại biên. |

Không mặc định **mọi ClassCastException đều đến sau**; cast thành một runtime class không tương thích có thể thất bại ngay. Điều được nghiên cứu là **generic type argument check bị trì hoãn/không có ở chỗ tạo alias hoặc chỗ ghi**, sau đó một typed use mới phát hiện mismatch.

### 15.1. Nếu code không bao giờ đọc theo String thì sao?

Một polluted List<String> có thể tồn tại lâu mà chưa gây ClassCastException nếu chỉ:
- Kiểm tra `size()`.
- Di chuyển reference.
- Xóa phần tử theo chỉ số.
- Đọc một giá trị vào Object rồi không ép thành String.

Nhưng dữ liệu vẫn không thỏa cam kết API. **Không thấy exception không biến cấu trúc đó thành an toàn.** Một thay đổi code sau này thêm map(String::length) hoặc đọc vào String có thể kích hoạt lỗi đã bị che giấu từ trước.

### 15.2. Đọc qua lambda, iterator hay stream cũng có thể là điểm phát hiện

Một Consumer/String lambda có kiểu đầu vào String. Khi dùng nó với polluted List<String>, điểm xử lý phần tử có thể buộc một cast thành String. Do đó exception không nhất thiết xuất hiện trực tiếp ở source có dạng `String s = list.get(0)`; nó có thể nằm trong generated lambda adapter hoặc code đọc của một thư viện.

Để chẩn đoán tốt, tìm chỗ **kiểu đích được yêu cầu** và đối chiếu với generic source data. Javap và stack trace hỗ trợ tìm vị trí nhưng vẫn cần truy đường ghi.

## 16. Phân biệt heap pollution với một cast sai thông thường

Một ClassCastException thuần túy:

~~~java
Object value = Integer.valueOf(10);
String text = (String) value; // ClassCastException trực tiếp.
~~~

Không có generic collection hay raw type; đây là một runtime cast sai kiểu bình thường. Không nên gắn nhãn heap pollution chỉ vì thấy ClassCastException.

Ngược lại, **heap pollution** mô tả trạng thái một generic reference/data structure không còn bảo đảm type arguments như khai báo, có thể tồn tại trước khi phát sinh lỗi.

Ví dụ cảnh báo:

~~~java
Object unknown = List.of("A");
List<String> typed = (List<String>) unknown; // Unchecked warning.
String value = typed.get(0);                 // Thành công với dữ liệu đúng.
~~~

Unchecked cast là một **đường rủi ro**, chưa đủ chứng minh danh sách thực sự có phần tử sai trong trường hợp này. Viết tests cho cả **dữ liệu hợp lệ** lẫn **dữ liệu sai** sẽ giúp phân biệt phương thức xác thực có hành vi đúng.

## 17. Test cho pipeline nhiều lớp: đặt kiểm tra ở ranh giới và nơi tiêu thụ

Một hệ thống thực tế có thể chuyển dữ liệu qua nhiều lớp:

~~~text
Nguồn bên ngoài / legacy API
       │ trả List raw / Object
       ▼
Repository hoặc adapter
       │ unchecked cast
       ▼
Service giữ List<String>
       │ gọi size(), truyền list qua nhiều methods
       ▼
Consumer / renderer
       │ String s = list.get(index)
       ▼
ClassCastException
~~~

Một bộ kiểm thử tốt nên có:

1. **Boundary test:** đầu vào List<Integer> không được âm thầm trở thành List<String>.
2. **Contract test:** adapter trả List<String> phải chứa String thực, hoặc fail sớm có chẩn đoán rõ.
3. **Mutation test:** raw aliases không được sửa dữ liệu mà API đã hứa chỉ chứa String.
4. **Consumer test:** logic đòi String không phải là chỗ duy nhất phát hiện dữ liệu bị ô nhiễm.
5. **Regression test:** sau khi sửa bug, input sai bị chặn ở điểm phù hợp và input hợp lệ vẫn được xử lý.

Ở unit tests, tạo dữ liệu sai qua raw aliases là hợp lý để tái hiện lỗ hổng. Nhưng trong production code, tránh cố ý giữ raw aliases chỉ vì test đã mô phỏng đường đó.

## 18. Cách kiểm thử với JUnit 5 nếu dự án đã sử dụng framework

Chương trình ở phần 9 không cần thư viện ngoài. Nếu dự án có **JUnit Jupiter**, có thể chuyển một trường hợp sang `assertThrows`:

~~~java
@Test
void pollutedListFailsWhenReadAsString() {
    List<String> values = new ArrayList<>(List.of("safe"));
    List raw = values;
    raw.add(42);

    assertEquals(2, values.size());

    Object value = values.get(1);
    assertEquals(42, value);

    assertThrows(ClassCastException.class, () -> {
        String text = values.get(1);
        System.out.println(text);
    });
}
~~~

Đây là **mảnh mã dành cho một lớp test JUnit 5**, không phải file Java chạy độc lập. Nó yêu cầu dependency JUnit Jupiter và các imports cần thiết:

~~~java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
~~~

Điểm quan trọng không phải tên framework mà là **ranh giới của assertThrows**: chỉ bọc phép đọc typed value, sau khi test đã xác nhận raw mutation trước đó thành công.

Một assertion quá rộng:

~~~java
// assertThrows(ClassCastException.class, () -> {
//     setup();
//     insertWrongValue();
//     consumeValue();
// });
~~~

có thể tạo **false confidence**: bất kỳ bước nào trong khối ném đúng loại exception cũng làm test “pass” dù thời điểm không đúng như bug muốn tái hiện.

### 18.1. Kiểm thử negative path phải chặt về loại exception

Nếu yêu cầu API từ chối dữ liệu không đúng schema bằng IllegalArgumentException ngay ở boundary, đừng viết `assertThrows(Exception.class, ...)`. Một ClassCastException xảy ra rất muộn cũng là Exception, nên expectation quá rộng có thể che failure mode mà test đáng lẽ phải phát hiện.

Dùng loại cụ thể cùng assertions trước/sau thao tác để kiểm tra thời điểm và hậu điều kiện.

## 19. Không phụ thuộc vào thông điệp nguyên văn của ClassCastException

Thông điệp có thể khác theo JDK, classloaders, module names, proxy classes và cách bytecode được sinh:

~~~text
class java.lang.Integer cannot be cast to class java.lang.String
~~~

Đây chỉ là một dạng thông điệp minh họa. Khi test:
- Ưu tiên assert **exception class** và đúng operation.
- Nếu API yêu cầu thông điệp nghiệp vụ ổn định, assert thông điệp do **chính API tạo**.
- Tránh so khớp tuyệt đối toàn bộ JVM-generated ClassCastException message vì nó không phải contract ổn định qua môi trường.
- Có thể lưu cause/stack trace và context để debug, nhưng test logic không nên bị ràng buộc vào offset bytecode hoặc đường dẫn source.

Chương trình `HeapPollutionTest` chỉ in `ClassCastException`, `ArrayStoreException` hoặc `IllegalArgumentException` từ `getClass().getSimpleName()` để stdout ổn định.

## 20. Những lỗi thường gặp khi thiết kế bài test

### Lỗi A — test chỉ assert exception ở cuối toàn bộ workflow

Nó không phân biệt exception do cast, write, read, lambda hay bước khác. Hãy test từng operation và xác nhận các bước trước.

### Lỗi B — nghĩ rawtypes warning là exception

Warnings là diagnostics của compiler, không được JVM tự biến thành lỗi runtime. Test cần cả compile diagnostics và runtime assertions khi muốn dạy học cơ chế này.

### Lỗi C — tưởng cast sang List<String> đã validate phần tử

`(List<String>) unknown` chỉ có khả năng kiểm tra raw runtime class List, không có kiểm tra tự động tất cả String elements. Duyệt `List<?>` để validate.

### Lỗi D — dùng `new List<String>[n]` trong ví dụ runnable

Java cấm generic array creation với non-reifiable List<String>. Dùng varargs hoặc mô tả một raw array **chỉ khi muốn chủ động minh họa rủi ro**; không lén ép unchecked vào production code để lách.

### Lỗi E — cho rằng ArrayStoreException và ClassCastException xảy ra cùng một nơi

ArrayStoreException có thể xuất hiện ở **write vào reified array**. ClassCastException có thể xuất hiện ở **cast/typed read**, hoặc tại runtime-checked wrapper write. Phải xem mechanism cụ thể.

### Lỗi F — giả định `List.copyOf` tự xác minh String

`List.copyOf` không có token String để inspect generic arguments. Validator phải duyệt và kiểm tra từng element trước khi công bố type cụ thể.

### Lỗi G — đặt @SafeVarargs lên unsafe method

Annotation là lời cam kết của tác giả API, không phải một compiler proof hay runtime guard. Nếu method đưa List<Integer> vào List<String>[] backing, lời cam kết đó sai.

### Lỗi H — tắt hết warnings bằng @SuppressWarnings

Suppress rộng có thể che thêm raw aliases/unchecked casts mới. Ưu tiên loại bỏ nguyên nhân và cô lập thao tác phải tương thích legacy kèm invariant được giải thích.

### Lỗi I — chỉ test happy path

Một List<String> thật sự chứa toàn String sẽ vượt qua cast và đọc bình thường. Phải thử List<Integer>/mixed data để xác thực đường chặn sai kiểu.

### Lỗi J — chỉ nhìn tên class implementation của List

`names.getClass()` trả ArrayList (hoặc một class khác), không chứng minh type argument String. Runtime class có thể giống nhau với những List<E> khác E.

### Lỗi K — dùng `assert` của Java mà quên -ea

Các Java assert statements có thể bị tắt mặc định. Nếu mục tiêu là test harness đơn giản, dùng checks chủ động ném AssertionError như phần 9 hoặc chạy với `java -ea` có chủ đích.

### Lỗi L — bỏ qua shared mutable aliases

Một checked wrapper hoặc defensive copy chỉ có ý nghĩa nếu đường ghi không an toàn thực sự bị kiểm soát. Nếu vẫn giữ raw alias tới backing list và cho sửa trực tiếp, invariant có thể bị phá từ đường khác.

## 21. Chiến lược sửa lỗi production

Một quy trình từ test đến sửa:

1. **Tái hiện chính xác** đường tạo dữ liệu sai kiểu bằng test riêng; ghi nhận compiler warnings.
2. **Xác định nơi pollution xảy ra**, không chỉ nơi ClassCastException được ném.
3. **Thay raw types bằng parameterized types** hoặc `List<?>` nếu loại phần tử chưa biết.
4. **Không dùng unchecked cast như validation**; xác minh dữ liệu từ bên ngoài từng phần tử khi cần.
5. **Cân nhắc defensive copy** để cắt mutable alias giữa boundary và core domain.
6. **Nếu dùng generic varargs**, không ghi qua Object[] alias, không trả/làm lộ mảng backing khi không chứng minh được an toàn.
7. **Chỉ dùng @SafeVarargs** khi contract của method đã được xem xét và an toàn.
8. **Nếu cần runtime insertion checks**, dùng API như `Collections.checkedList` với token phù hợp và bảo đảm không lộ backing reference.
9. **Đặt test negative ở boundary** để sai kiểu bị báo sớm với exception hợp lý theo API, thay vì để CCE rơi ra ngẫu nhiên ở renderer.
10. **Giữ regression test cho typed read** để bảo đảm lỗi cũ không tái xuất hiện khi code legacy được chỉnh sửa.
11. **Dùng javap khi cần**, tìm compiler-inserted checkcast để hiểu vì sao stack trace trỏ đến một dòng tưởng như chỉ gọi `get`.
12. **Không thay một lỗi bằng unchecked suppression**; thông điệp compiler có thể được tắt nhưng dữ liệu vẫn sai.

### 21.1. API boundary nên nhận List<?> hay List<String>?

Nếu caller là mã typed Java được compiler kiểm tra đầy đủ và không có nguồn raw/unchecked không tin cậy, `List<String>` là hợp đồng hữu ích.

Nếu đối số đến từ deserialization, reflection, legacy raw API hoặc một object không chắc chắn, `List<?>`/`Object` có thể là kiểu đầu vào trung thực hơn; sau khi xác thực thì mới trả `List<String>`. Quyết định phụ thuộc nơi nào giữ trách nhiệm chứng minh invariant.

Đừng vô cớ làm mọi method nhận Object nếu code đã có type safety đúng; cách xử lý linh hoạt nên nằm ở **ranh giới rủi ro**, còn core code giữ generic types chặt chẽ.

## 22. Checklist review code và test

- [ ] Phân biệt rawtypes warning, unchecked warning, heap pollution và ClassCastException.
- [ ] Test xác định **thao tác ghi sai** có ném exception hay chưa.
- [ ] Test đọc dưới `Object` và đọc dưới `String` bằng **hai assertions riêng**.
- [ ] Test cast sang `List<String>` từ Object là unchecked, không xác minh phần tử.
- [ ] Với varargs generic, kiểm tra runtime component List[] và array covariance.
- [ ] Với String[], kiểm tra ArrayStoreException xuất hiện ngay ở write.
- [ ] Với Collections.checkedList, kiểm tra invalid insertion qua wrapper thất bại ngay.
- [ ] Kiểm tra liệu mutable raw alias đến backing collection còn tồn tại không.
- [ ] Tạo validated typed copy tại nơi nhận dữ liệu không tin cậy.
- [ ] Không suppress compiler warnings mà không có chứng minh an toàn.
- [ ] Dùng `javac --release 17 -Xlint:rawtypes,unchecked,varargs` để xem diagnostics.
- [ ] Dùng `javap -c -p` khi cần tìm inserted `checkcast`.
- [ ] Bọc `assertThrows`/`expect` càng sát **thao tác muốn thử** càng tốt.
- [ ] Không hard-code đầy đủ message của JVM exception nếu không phải contract ứng dụng.
- [ ] Test cả dữ liệu đúng kiểu và dữ liệu sai kiểu.
- [ ] Đảm bảo tests fail khi **không xảy ra exception** như mong đợi.
- [ ] Đánh giá thêm mutability, null, aliasing và concurrency nếu dữ liệu được chia sẻ nhiều lớp.

## 23. Câu hỏi tự kiểm tra

**1.** Vì sao `List raw = names; raw.add(42);` có thể chạy mà chưa ném ClassCastException?

**Đáp án:** Raw List không giữ type argument String trong lời gọi add, và ArrayList không có runtime check String cho element type của một List<String> thông thường. Compiler báo unchecked warning, nhưng phép ghi có thể được chấp nhận.

**2.** Nếu sau đó `Object value = names.get(1)` chạy được, danh sách đã an toàn chưa?

**Đáp án:** Chưa. Đọc như Object không chứng minh element là String. Một typed read `String value = ...` vẫn có thể fail.

**3.** Điểm JVM thường phát hiện Integer không phải String trong typed read là gì?

**Đáp án:** `checkcast java/lang/String` do compiler chèn khi cần dùng kết quả generic get như String.

**4.** Một unchecked cast `(List<String>) unknown` có luôn ném ngay không?

**Đáp án:** Không. Nếu unknown là List<Integer>, raw List check có thể qua; nhưng nếu unknown là một object không phải List, cast có thể ném ngay ở đó.

**5.** Vì sao một backing varargs `List<String>[]` có runtime component List?

**Đáp án:** Generic argument String không reifiable ở component type List<String>; compiler có thể tạo List[] khi chuẩn bị generic varargs call.

**6.** Ghi `List.of(99)` vào backing List[] qua Object[] có bị ArrayStoreException không?

**Đáp án:** Không nhất thiết; List<Integer> vẫn là một List đối với runtime component check. Lỗi có thể xuất hiện về sau khi đọc Integer như String.

**7.** Vì sao `String[]` qua Object[] alias chặn Integer ngay tại write?

**Đáp án:** String[] là reifiable array có runtime component String; JVM thực hiện array-store check và ném ArrayStoreException.

**8.** `Collections.checkedList(new ArrayList<>(),String.class)` giúp chặn gì?

**Đáp án:** Nó dùng String.class để kiểm tra runtime type của phần tử được chèn qua wrapper; raw add(Integer) qua wrapper ném ClassCastException ngay tại write.

**9.** Checked wrapper có bảo vệ được khi ai đó còn raw alias tới backing list không?

**Đáp án:** Không. Đường bypass wrapper vẫn có thể sửa backing list trực tiếp và làm hỏng dữ liệu.

**10.** Vì sao `List.copyOf` không đủ làm validator cho List<?> chưa tin cậy?

**Đáp án:** Nó sao chép elements nhưng không tự kiểm tra từng Object là String theo một generic type argument không có class token. Cần validate từng element rồi mới công bố List<String>.

**11.** Bài test chỉ viết `assertThrows(ClassCastException.class, () -> doEverything())` thiếu điều gì?

**Đáp án:** Không xác định exception phát sinh ở thao tác cụ thể nào; các lỗi từ bước khác có thể khiến test pass nhầm.

**12.** Một List<String> đã có Integer nhưng chỉ gọi `size()` có nhất thiết ném CCE không?

**Đáp án:** Không. size không cần typed element cast. Pollution có thể tồn tại lâu trước khi được phát hiện.

**13.** ClassCastException có nhất thiết là do heap pollution không?

**Đáp án:** Không. Một checked cast sai như `(String) Integer.valueOf(1)` cũng phát ClassCastException mà không có generic data structure.

**14.** Tại sao compiler warnings được xem là phần của kiểm thử dù chương trình vẫn chạy?

**Đáp án:** Chúng chỉ ra các đường không được kiểm tra đầy đủ; nếu chỉ chạy happy path thì có thể bỏ qua rủi ro tiềm ẩn. Test bao gồm cả diagnostics và runtime behavior.

**15.** Với nguồn Object từ bên ngoài, cách trả List<String> đáng tin cậy là gì?

**Đáp án:** Trước tiên xác nhận object là List<?>; duyệt từng element và kiểm tra `instanceof String`; tạo collection mới có type argument String và tránh chia sẻ raw mutable alias.

**16.** Vì sao thêm `@SafeVarargs` vào method cố tình thay `groups[0]` bằng `List.of(99)` không sửa bug?

**Đáp án:** Annotation không thêm runtime validation và không chứng minh implementation an toàn; backing List[] vẫn có thể bị heap pollution.

**17.** Kết quả `TOTAL=15` trong chương trình test chứng minh điều gì?

**Đáp án:** Tất cả 15 assertions đã qua và những exception cụ thể xuất hiện ở đúng operation được test bọc. Nó không phải chứng minh rằng mọi trường hợp heap pollution khác đều đã được loại trừ khỏi ứng dụng.

## 24. Tổng kết

Với Java Generics, **heap pollution là trạng thái dữ liệu/kiểu đã bị phá vỡ**, còn **ClassCastException là một cách lỗi được phát hiện lúc runtime cần cast về class cụ thể**. Hai sự kiện đó có thể xảy ra ở **hai thời điểm rất xa nhau**.

~~~text
Raw alias / unchecked generic conversion / generic varargs
                       │
                       ▼
            Dữ liệu sai generic type
                       │
                 Chưa chắc lỗi
                       │
          ┌────────────┴────────────┐
          ▼                         ▼
     size(), Object read       Typed read / cast
       có thể chạy             yêu cầu String
                                    │
                                    ▼
                             ClassCastException

Ngoại lệ cần phân biệt:
String[] + Object[] alias + ghi Integer → ArrayStoreException tại write
CheckedList<String> + ghi Integer qua wrapper → ClassCastException tại write
~~~

Muốn kiểm thử đúng, phải xác nhận **thứ tự sự kiện**: compiler warning, cast/alias, mutation, safe read, typed read, và expected exception tại đúng điểm. Dùng chương trình kiểm thử tự chứa hoặc `assertThrows` sát thao tác, xem `checkcast` bằng `javap` nếu cần, và sửa nguyên nhân ở đường raw/unchecked alias hoặc boundary validation thay vì chỉ bắt ClassCastException ở nơi lỗi bộc lộ.

**Ghi nhớ:** Java Generics bảo vệ rất tốt khi toàn bộ luồng dữ liệu tuân thủ type checking. Khi thoát khỏi vùng an toàn bằng raw types, unchecked casts hay non-reifiable varargs, chương trình phải tự quản lý invariant — nếu không, lỗi có thể đến muộn và xa nguồn gây ra nó.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-testing-and-diagnostics-006">Đối chiếu chữ ký generic, chữ ký đã xóa và bridge trong class file</a>

<details>
<summary>Xem chi tiết</summary>

# Đối chiếu chữ ký generic, chữ ký đã xóa và bridge trong Java class file

## 1. Mục tiêu: hiểu cùng một phương thức ở ba lớp biểu diễn

Khi đọc Java Generics, bạn thường thấy các chữ ký giàu thông tin kiểu ở **source code**:

```java
class Holder<T> {
    T get() { return null; }
    void set(T value) {}
}
```

Nhưng sau `javac`, JVM làm việc với **method descriptors** mà type variables đã bị *erasure*. Trong class file có thể còn một thuộc tính `Signature` lưu cách khai báo generic cho các công cụ như Reflection. Khi có overriding sau khi thay type argument hoặc khi có covariant return type, compiler còn có thể tạo thêm **bridge method**.

Bài này hướng dẫn đối chiếu một cách có hệ thống:

1. **Chữ ký generic tại source**: type variables `<T>`, bounds, `List<String>`, overriding và inference.
2. **Chữ ký đã xóa ở JVM descriptor**: những kiểu JVM dùng để định danh field/method và thực hiện linkage.
3. **Generic `Signature` attribute**: metadata mô tả type parameters và generic arguments mà Java Reflection có thể đọc.
4. **Bridge/synthetic methods**: những method compiler sinh để giữ overriding/dispatch đúng sau erasure hoặc khi return type covariant.
5. **Kết quả thực nghiệm**: `javac --release 17`, `javap -p -s -v -c` và Reflection `Method.isBridge()`, `Method.isSynthetic()`, `getGenericReturnType()`.

Sau bài học, bạn có thể nhìn vào một class file và trả lời: vì sao source chỉ có `String get()` mà bytecode xuất hiện cả `Object get()`; vì sao `get()` trên superclass trả `Object` sau erasure nhưng `StringHolder` vẫn override đúng; vì sao Reflection thấy những phương thức không viết trong source; và khi nào xung đột sau erasure là lỗi compile time thay vì được compiler sửa bằng bridge.

## 2. Ba thứ dễ nhầm: Java method signature, JVM descriptor và generic Signature

Chữ “signature” có nhiều cách dùng; phải tách đúng ngữ cảnh.

| Khái niệm | Dùng để làm gì? | Ví dụ |
| --- | --- | --- |
| **Java source method declaration** | Biểu diễn API và quy tắc type checking | `public T get()` |
| **Java method signature theo JLS** | Phục vụ nhận diện phương thức/overloading ở mức ngôn ngữ; tập trung vào tên và parameter types/type parameters | `get()` |
| **JVM method descriptor** | Mã hóa parameter types **và return type** ở JVM | `()Ljava/lang/Object;` |
| **Class-file `Signature` attribute** | Ghi metadata generic khi cần | `()TT;` |
| **Bridge method** | Method compiler phát sinh để giữ quan hệ overriding/dispatch | `Object get()` gọi `String get()` |
| **Cờ method `ACC_BRIDGE`** | Đánh dấu method bridge | `0x0040` |
| **Cờ method `ACC_SYNTHETIC`** | Đánh dấu member compiler sinh/không viết trực tiếp trong source | `0x1000` |

**Điểm quan trọng:** trong Java source, chỉ đổi kiểu trả về thường **không tạo một overload hợp lệ**. Nhưng ở class file, một class có thể chứa bridge `Object get()` và implementation `String get()` cùng tên, cùng danh sách tham số, **khác descriptor ở return type**. Đây là mã compiler tạo theo quy tắc JVM, không phải lời mời để bạn tự khai báo hai method Java chỉ khác return type.

### 2.1. Descriptor dùng cú pháp JVM, không dùng tên generic ở source

Ví dụ:

```java
T get();
void set(T value);
```

nếu `T` không có bound cụ thể, được erase thành `Object`. Descriptors tương ứng:

```text
()Ljava/lang/Object;
(Ljava/lang/Object;)V
```

Cú pháp rút gọn:

- `(` ... `)` là danh sách parameter descriptors.
- `V` là `void` return.
- `I` là `int`; `J` là `long`; `Z` là `boolean`.
- `Lpkg/ClassName;` là kiểu tham chiếu; dùng dấu `/` trong internal name.
- `[` đứng trước descriptor của thành phần mảng.
- Descriptor **không chứa** `<String>`, `? extends` hoặc type variable `T`.

Ví dụ khác:

```java
String get();
void set(String value);
int count();
String[] names();
```

descriptors:

```text
()Ljava/lang/String;
(Ljava/lang/String;)V
()I
()[Ljava/lang/String;
```

Dòng `()[Ljava/lang/String;` nghĩa là method không đối số, trả mảng `String[]`.

### 2.2. `Signature` attribute dùng grammar riêng

Ví dụ:

```text
()TT;
```

Trong generic signature grammar, `TT;` mô tả type variable tên `T`. Chú ý ký tự `T` đầu tiên là marker kiểu biến và `T` thứ hai là tên biến.

Với:

```java
List<String> tags();
```

class file có thể có:

```text
descriptor: ()Ljava/util/List;
Signature: ()Ljava/util/List<Ljava/lang/String;>;
```

JVM cần `List` ở descriptor; `Signature` giữ type argument `String` cho công cụ generic-aware.

Không diễn giải `Signature` là JVM descriptor dùng để dispatch. Chúng có mục đích khác nhau.

### 2.3. JVM erasure và Reflection không mâu thuẫn

Một câu thường nghe là “Java Generics bị xóa khi chạy”. Câu đúng hơn:

- **Type arguments của object instances không được reify thành các runtime classes riêng** như `ArrayList<String>` và `ArrayList<Integer>`.
- **Method descriptors** dùng kiểu đã erase.
- Compiler có thể giữ **generic declaration metadata** trong `Signature` attribute, nên Reflection trả được `TypeVariable`, `ParameterizedType`, v.v.

Không thể lấy `list.getClass()` rồi mặc định biết chính xác generic argument của biến đã tạo list. Nhưng có thể đọc generic declaration trên class/field/method có metadata tương ứng.

## 3. Type erasure chuyển kiểu như thế nào?

Giả sử:

```java
class Holder<T> {
    private T value;

    T get() {
        return value;
    }

    void set(T value) {
        this.value = value;
    }
}
```

`T` không có bound viết tường minh, nên upper bound mặc định là `Object`. Erasure của `T` là `Object`.

| Khai báo source | Kiểu sau erasure |
| --- | --- |
| Field `T value` | Field `Object value` |
| Return `T get()` | Return `Object get()` |
| Parameter `void set(T)` | Parameter `void set(Object)` |
| `List<String>` | `List` |
| `Map<String,Integer>` | `Map` |
| `T[]` khi T không bound | `Object[]` |
| `T extends Number` | `Number` |

### 3.1. Bound đầu tiên ảnh hưởng erasure

Với một generic method:

```java
public <N extends Number> N echo(N number) {
    return number;
}
```

Erasure của `N` là `Number`, **không phải Object**. Descriptor:

```text
(Ljava/lang/Number;)Ljava/lang/Number;
```

Generic `Signature` tương ứng:

```text
<N:Ljava/lang/Number;>(TN;)TN;
```

Nó cho biết có method type variable `N` với bound Number, tham số N và return N. Nếu chỉ xem descriptor, bạn mất quan hệ “đầu vào và đầu ra dùng cùng N” nhưng vẫn biết cả hai có raw type Number.

Với multiple bounds, erasure phụ thuộc bound đầu theo quy tắc Java. Vì thế cần kiểm tra declaration thật, không suy đoán mọi type variable đều erase thành Object.

### 3.2. Kiểu tham số hóa bị erase về raw type

```java
public List<String> tags() {
    return List.of("java", "generics");
}
```

Descriptor:

```text
()Ljava/util/List;
```

`Signature`:

```text
()Ljava/util/List<Ljava/lang/String;>;
```

Method nào chỉ nhìn descriptor sẽ không biết element type String. Công cụ đọc `Signature` mới thấy generic argument từ declaration.

### 3.3. Type erasure không làm `List<Integer>` biến thành `List<String>`

Ở compile time:

```java
List<String> names = new ArrayList<>();

// Không biên dịch:
// List<Integer> numbers = names;
```

Invariance/type checking vẫn có hiệu lực ở source. Erasure xảy ra như một phần biên dịch/JVM representation; không có nghĩa những phép gán sai kiểu trở nên hợp lệ.

Ngược lại, raw types và unchecked conversions có thể tạo dữ liệu sai, dẫn đến lỗi muộn như `ClassCastException`. Bridge method không chữa heap pollution do raw types gây ra.

## 4. Vì sao compiler cần bridge khi override generic superclass?

Một ví dụ:

```java
class Holder<T> {
    public T get() {
        return null;
    }
}

class StringHolder extends Holder<String> {
    @Override
    public String get() {
        return "Java";
    }
}
```

Trong Java source, `StringHolder.get()` **override** `Holder<String>.get()` vì T được thay bằng String trong quan hệ kế thừa.

Nhưng ở JVM:

- Erased `Holder.get()` có descriptor `()Ljava/lang/Object;`.
- `StringHolder.get()` do bạn viết có descriptor `()Ljava/lang/String;`.

Nếu JVM chỉ có `String get()` và lời gọi từ biến `Holder<String>` tham chiếu tới descriptor `Object get()`, cơ chế dispatch sẽ thiếu implementation tương ứng ở subclass theo erased descriptor.

Vì vậy compiler có thể sinh bridge:

```java
// Pseudocode giải thích bytecode, KHÔNG phải source tự viết:
public Object get() {
    return this.get(); // Gọi String get() của class con.
}
```

Ở JVM, hai `get` phân biệt bằng descriptor return type. Method bridge có kiểu trả về Object đúng với erased superclass API và ủy quyền cho implementation String.

### 4.1. Với parameter `set(T)`, bridge cần cast

Source:

```java
class Holder<T> {
    public void set(T value) {
    }
}

class StringHolder extends Holder<String> {
    @Override
    public void set(String value) {
    }
}
```

Erased superclass method nhận Object, còn StringHolder source method nhận String. Compiler có thể tạo thêm:

```java
// Pseudocode bridge, không khai báo thủ công:
public void set(Object value) {
    set((String) value);
}
```

Method này nhận Object tại erased JVM boundary, thực hiện `checkcast String`, rồi gọi typed implementation `set(String)`.

**Hệ quả:** đi qua một raw/reflection boundary và truyền Integer vào bridge `set(Object)` có thể ném `ClassCastException`. Bridge giữ dispatch và type safety trong những ràng buộc mà JVM có thể kiểm tra, không khiến Integer biến thành String.

### 4.2. Bridge là implementation detail của class file

Người viết source **không** khai báo bridge bằng tay và không nên gọi nó qua một API Java bình thường như một overload độc lập. Compiler quyết định nơi cần method để bảo toàn quan hệ override.

Không phải **mọi** method generic đều có bridge. Bridge thường chỉ xuất hiện khi có nhu cầu tương thích descriptor từ quan hệ kế thừa/interface hoặc covariant return.

## 5. Ví dụ đầy đủ: superclass, subclass, interface và bridge

Tạo file **`GenericBytecodeDemo.java`**:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class GenericBytecodeDemo {
    static class Holder<T> {
        private T value;

        Holder(T value) {
            this.value = value;
        }

        public T get() {
            return value;
        }

        public void set(T value) {
            this.value = value;
        }

        public <N extends Number> N echo(N number) {
            return number;
        }

        public List<String> tags() {
            return List.of("java", "generics");
        }
    }

    static final class StringHolder extends Holder<String> {
        StringHolder(String value) {
            super(value);
        }

        @Override
        public String get() {
            return super.get().toUpperCase();
        }

        @Override
        public void set(String value) {
            super.set(value.trim());
        }
    }

    interface Mapper<T> {
        T apply(T value);
    }

    static final class Uppercase implements Mapper<String> {
        @Override
        public String apply(String value) {
            return value.toUpperCase();
        }
    }

    public static void main(String[] args) throws Exception {
        Holder<String> holder = new StringHolder(" java ");
        holder.set(" bridge ");
        System.out.println("dispatch=" + holder.get());
        System.out.println("generic-method=" + holder.echo(7));

        System.out.println("generic-field="
                + Holder.class.getDeclaredField("value")
                      .getGenericType().getTypeName());

        System.out.println("generic-super="
                + StringHolder.class.getGenericSuperclass()
                      .getTypeName());

        Method[] methods = StringHolder.class.getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::toString));

        for (Method method : methods) {
            System.out.println("method=" + method.getName()
                    + ",parameter="
                    + Arrays.toString(method.getParameterTypes())
                    + ",returns="
                    + method.getReturnType().getSimpleName()
                    + ",bridge=" + method.isBridge()
                    + ",synthetic=" + method.isSynthetic());
        }

        Method getTyped =
                StringHolder.class.getDeclaredMethod("get");
        Method setBridge =
                StringHolder.class.getDeclaredMethod(
                        "set", Object.class);

        System.out.println("get-by-object-method="
                + (getTyped.getReturnType() == String.class));
        System.out.println("set-object-bridge="
                + setBridge.isBridge());

        try {
            setBridge.invoke(holder, 42);
        } catch (InvocationTargetException ex) {
            System.out.println("invalid-bridge-arg="
                    + ex.getCause().getClass().getSimpleName());
        }

        System.out.println("mapper="
                + new Uppercase().apply("hi"));
        System.out.println("mapper-bridge="
                + Uppercase.class
                  .getDeclaredMethod("apply", Object.class)
                  .isBridge());
    }
}
```

Biên dịch và chạy với Java 17:

```bash
javac --release 17 -Xlint:all GenericBytecodeDemo.java
java GenericBytecodeDemo
```

Chương trình đã được biên dịch sạch với `-Xlint:all`. Stdout trên JDK kiểm thử:

```text
dispatch=BRIDGE
generic-method=7
generic-field=T
generic-super=GenericBytecodeDemo$Holder<java.lang.String>
method=get,parameter=[],returns=Object,bridge=true,synthetic=true
method=get,parameter=[],returns=String,bridge=false,synthetic=false
method=set,parameter=[class java.lang.Object],returns=void,bridge=true,synthetic=true
method=set,parameter=[class java.lang.String],returns=void,bridge=false,synthetic=false
get-by-object-method=true
set-object-bridge=true
invalid-bridge-arg=ClassCastException
mapper=HI
mapper-bridge=true
```

Kết quả `get-by-object-method=true` phản ánh lựa chọn return type cụ thể hơn của Reflection khi tra `getDeclaredMethod("get")` trong ví dụ; khi phân tích class file nhiều methods cùng tên/parameters mà khác return type, hãy **duyệt `getDeclaredMethods()` và kiểm tra return type/cờ bridge**, đừng tự đồng nhất mọi `get` thành một method.

### 5.1. Vì sao `dispatch=BRIDGE`?

Biến `holder` có kiểu tĩnh `Holder<String>`, nhưng object thật là StringHolder. Lời gọi set/get có typed contract String; sau erasure, JVM call site có thể dùng descriptors của Holder với Object. Bridge methods giúp dispatch tới implementation `StringHolder`, và StringHolder biến chuỗi `" bridge "` thành `"bridge"`, rồi uppercase thành `"BRIDGE"`.

Điều này minh họa **override hoạt động đúng** qua erased JVM descriptors. Không phải runtime biết `Holder<String>` như một specialization class mới.

### 5.2. Vì sao `generic-field=T`?

`Field.getGenericType()` đọc generic metadata của declaration `private T value`. Nó trả một `TypeVariable` tên T. Nếu dùng `Field.getType()` thay vì `getGenericType()`, erased field type là `Object.class`.

### 5.3. Vì sao `generic-super=Holder<String>`?

`StringHolder` được khai báo `extends Holder<String>`, và generic superclass metadata còn trong class file. `getGenericSuperclass()` trả `ParameterizedType` mô tả đúng declaration đó.

Reflection không tự biến generic field `T` trong Holder thành String khi bạn gọi thẳng `Holder.class.getDeclaredField("value").getGenericType()`. Để suy ra T=String trong ngữ cảnh StringHolder, resolver phải xem cả quan hệ `StringHolder extends Holder<String>`.

### 5.4. Vì sao có bốn dòng `method=`?

Trong StringHolder:

- Typed `String get()` là method do bạn viết; `bridge=false`, `synthetic=false`.
- Bridge `Object get()` là method compiler sinh; `bridge=true`, `synthetic=true`.
- Typed `void set(String)` là method do bạn viết.
- Bridge `void set(Object)` là method compiler sinh.

Output được sắp theo `Method.toString()` trong chương trình để kiểm thử dễ đối chiếu; Reflection không hứa `getDeclaredMethods()` có thứ tự ổn định nếu không tự sắp xếp.

### 5.5. Vì sao `invalid-bridge-arg=ClassCastException`?

Reflection gọi `set(Object)` của StringHolder với Integer `42`. Bridge tồn tại và nhận một Object theo erased descriptor, nhưng bên trong nó phải cast Object thành String trước khi gọi `set(String)`. Cast thất bại, Reflection đóng gói exception trong `InvocationTargetException`; chương trình lấy nguyên nhân bằng `getCause()` để in ClassCastException.

Đây là minh chứng cho cơ chế runtime của bridge, **không phải lời khuyên gọi bridge bằng Reflection trong application code**.

### 5.6. Vì sao `mapper-bridge=true`?

`Mapper<T>` khai báo `T apply(T value)`, khi erase có descriptor `(Ljava/lang/Object;)Ljava/lang/Object;`.

`Uppercase implements Mapper<String>` cài đặt `String apply(String value)`, với descriptor `(Ljava/lang/String;)Ljava/lang/String;`.

Compiler sinh `Object apply(Object)` làm bridge để các lời gọi qua `Mapper` interface vẫn dispatch tới implementation String đúng cách. Cơ chế này giống generic superclass, nhưng xuất hiện ở **generic interface implementation**.

## 6. Quan sát class file bằng `javap`

Sau khi biên dịch `GenericBytecodeDemo.java`, chạy từ đúng thư mục chứa các `.class`:

```bash
javap -p -s 'GenericBytecodeDemo$Holder'
javap -p -s 'GenericBytecodeDemo$StringHolder'
javap -p -s 'GenericBytecodeDemo$Uppercase'
```

Trong một số shell Windows, dùng:

```powershell
javap -p -s 'GenericBytecodeDemo$Holder'
javap -p -s 'GenericBytecodeDemo$StringHolder'
javap -p -s 'GenericBytecodeDemo$Uppercase'
```

Dùng dấu nháy quanh tên chứa `$` để tránh shell xử lý sai tên nested class; trong Bash dùng nháy đơn hoặc escape `$`. Nếu chạy từ thư mục khác, thêm `-classpath path/to/classes`.

Các flags:

- `-p` hiển thị cả private members.
- `-s` in descriptors.
- `-v` in verbose class-file details, bao gồm attributes và flags.
- `-c` disassemble Code attribute thành các JVM instructions.

### 6.1. `Holder<T>` dưới `javap -p -s`

Trích đoạn đã được kiểm chứng:

```text
class GenericBytecodeDemo$Holder<T> {
  private T value;
    descriptor: Ljava/lang/Object;

  GenericBytecodeDemo$Holder(T);
    descriptor: (Ljava/lang/Object;)V

  public T get();
    descriptor: ()Ljava/lang/Object;

  public void set(T);
    descriptor: (Ljava/lang/Object;)V

  public <N extends java.lang.Number> N echo(N);
    descriptor: (Ljava/lang/Number;)Ljava/lang/Number;

  public java.util.List<java.lang.String> tags();
    descriptor: ()Ljava/util/List;
}
```

`javap` hiển thị cú pháp generic dễ đọc nhờ metadata, **nhưng các dòng `descriptor:` vẫn là kiểu đã erase**.

Điều cần quan sát:

- Field `T` → descriptor `Object`.
- Constructor nhận `T` → descriptor nhận `Object`.
- `T get()` → descriptor return `Object`.
- `void set(T)` → descriptor nhận `Object`.
- Bounded `N extends Number` → descriptor parameter và return Number.
- `List<String>` → descriptor chỉ là List.

### 6.2. `StringHolder` dưới `javap -p -s`

Trích đoạn:

```text
final class GenericBytecodeDemo$StringHolder
    extends GenericBytecodeDemo$Holder<java.lang.String> {

  GenericBytecodeDemo$StringHolder(java.lang.String);
    descriptor: (Ljava/lang/String;)V

  public java.lang.String get();
    descriptor: ()Ljava/lang/String;

  public void set(java.lang.String);
    descriptor: (Ljava/lang/String;)V

  public void set(java.lang.Object);
    descriptor: (Ljava/lang/Object;)V

  public java.lang.Object get();
    descriptor: ()Ljava/lang/Object;
}
```

Hai dòng `set` và hai dòng `get` **không cùng nguồn source**. `javap` cho thấy methods compiler tạo thêm trong class file. Với `-s` bạn xác nhận descriptors chính xác; với `-v` bạn kiểm tra cờ bridge/synthetic.

### 6.3. `Uppercase` dưới `javap -p -s`

Trích đoạn:

```text
final class GenericBytecodeDemo$Uppercase
    implements GenericBytecodeDemo$Mapper<java.lang.String> {

  public java.lang.String apply(java.lang.String);
    descriptor: (Ljava/lang/String;)Ljava/lang/String;

  public java.lang.Object apply(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Ljava/lang/Object;
}
```

Đây là dạng điển hình khi implements một generic interface bằng một type argument cụ thể. Typed method trả String; bridge giữ erased interface descriptor Object→Object.

### 6.4. `-s` không phải là `Signature` attribute

Dễ bị nhầm vì option `-s` in **descriptors** còn attribute có tên **`Signature`**. Muốn thấy trường riêng `Signature:` trong verbose output:

```bash
javap -p -v 'GenericBytecodeDemo$Holder'
```

Kết quả dài, vì có constant pool, attributes, bytecode và nhiều metadata khác. Hãy tìm những từ khóa:

```text
descriptor:
Signature:
ACC_BRIDGE
ACC_SYNTHETIC
```

Dùng một đoạn trích có context thay vì chỉ tìm một dòng rồi gán nhầm Signature cho method bên cạnh.

## 7. Đọc class file verbose: descriptor khác Signature ở đâu?

Với `Holder<T>`, `javap -p -v` có các mục dạng:

```text
private T value;
  descriptor: Ljava/lang/Object;
  Signature: #...    // TT;
```

Với `get()`:

```text
public T get();
  descriptor: ()Ljava/lang/Object;
  Signature: #...    // ()TT;
```

Với `set(T)`:

```text
public void set(T);
  descriptor: (Ljava/lang/Object;)V
  Signature: #...    // (TT;)V
```

Với method bound:

```text
public <N extends java.lang.Number> N echo(N);
  descriptor: (Ljava/lang/Number;)Ljava/lang/Number;
  Signature: #...    // <N:Ljava/lang/Number;>(TN;)TN;
```

Với `tags()`:

```text
public java.util.List<java.lang.String> tags();
  descriptor: ()Ljava/util/List;
  Signature: #...    // ()Ljava/util/List<Ljava/lang/String;>;
```

Các chỉ số `#...` là constant-pool references và **thay đổi theo biên dịch/JDK**. Phần có ý nghĩa là nội dung `// ...` của generic signature và sự khác biệt với descriptor.

### 7.1. `TT;` nghĩa là gì?

Trong `Signature` grammar:

```text
T<name>;
```

biểu diễn một type variable mang tên đó. Ví dụ tên biến `T` tạo:

```text
TT;
```

và tên biến `N` tạo:

```text
TN;
```

Đừng xem đây là descriptor tham chiếu tới class tên `T` hay `N`; type variables là metadata generic, còn JVM method descriptor đã dùng erasure.

### 7.2. Generic method `<N extends Number>` có hai tầng thông tin

Ở source, compiler bảo đảm method `echo` nhận và trả **cùng một N** cho từng generic invocation:

```java
Integer integer = holder.echo(7);
Double decimal = holder.echo(1.5);
```

Nhưng JVM descriptor chỉ có `Number -> Number`. Metadata `Signature` giữ quan hệ `(TN;)TN;` cho compiler/tools khi cần đọc declaration.

Một call site có thể có casts được compiler chèn sau khi đọc kết quả từ descriptor đã erase. Việc có cast cụ thể phụ thuộc bytecode do compiler sinh và kiểu được sử dụng tại call site.

### 7.3. Khi nào không có Signature?

Không phải **mọi** method hoặc field trong class file đều cần `Signature`. Ví dụ method chỉ dùng kiểu concrete không generic như `void ping()` có thể chỉ có descriptor.

Không nên coi `Signature` là bắt buộc trên từng method hay là thứ JVM phải kiểm tra để dispatch. Descriptor và JVM verification/linkage vận hành dựa trên những thông tin phù hợp với JVM, còn `Signature` phục vụ generic reflection/metadata.

## 8. Kiểm tra cờ ACC_BRIDGE và ACC_SYNTHETIC

Lệnh:

```bash
javap -p -v 'GenericBytecodeDemo$StringHolder'
```

Trong class file kiểm thử có hai bridge methods:

```text
public void set(java.lang.Object);
  descriptor: (Ljava/lang/Object;)V
  flags: (0x1041) ACC_PUBLIC, ACC_BRIDGE, ACC_SYNTHETIC
```

```text
public java.lang.Object get();
  descriptor: ()Ljava/lang/Object;
  flags: (0x1041) ACC_PUBLIC, ACC_BRIDGE, ACC_SYNTHETIC
```

Ý nghĩa:

- `ACC_PUBLIC`: access flag của method.
- `ACC_BRIDGE`: bridge method do compiler sinh để giữ tính đúng đắn của quan hệ method.
- `ACC_SYNTHETIC`: method không được khai báo trực tiếp theo cách source-level ordinary member.

Cả hai bridge trong ví dụ đều được đánh dấu `ACC_BRIDGE` và `ACC_SYNTHETIC`. Tuy nhiên **synthetic không đồng nghĩa luôn là bridge**. Compiler có thể sinh các synthetic members khác vì những yêu cầu biên dịch khác; cần dùng chính cờ `ACC_BRIDGE` hoặc `Method.isBridge()` để nhận diện bridge.

### 8.1. Đọc bytecode của bridge `set(Object)`

Chạy:

```bash
javap -p -c 'GenericBytecodeDemo$StringHolder'
```

Phần bridge set(Object) có dạng gần như:

```text
public void set(java.lang.Object);
  Code:
     0: aload_0
     1: aload_1
     2: checkcast     #... // class java/lang/String
     5: invokevirtual #... // Method set:(Ljava/lang/String;)V
     8: return
```

Các constant pool indices và offsets có thể thay đổi. Cốt lõi là:

1. Nạp `this`.
2. Nạp argument Object.
3. `checkcast` thành String.
4. Gọi method typed `set(String)`.
5. Trả về.

Nếu Object thật không phải String, `checkcast` ném `ClassCastException`.

### 8.2. Đọc bytecode của bridge `get()`

Bridge Object get() thường có cấu trúc gần:

```text
public java.lang.Object get();
  Code:
     0: aload_0
     1: invokevirtual #... // Method get:()Ljava/lang/String;
     4: areturn
```

Không cần cast xuống String ở phần return của bridge: kết quả String vốn có thể dùng như Object. JVM nhận một reference phù hợp Object.

Không nhầm phần bridge này với các `checkcast` khác có thể xuất hiện trong **typed `StringHolder.get()`** khi nó gọi `super.get()` trả Object và cần dùng kết quả như String.

### 8.3. Không phụ thuộc bytecode offset cụ thể

Compiler/JDK có thể thay đổi instruction layout, số constant-pool entries hoặc cách in javap. Khi kiểm thử, xác minh **descriptor, method flags và quan hệ ủy quyền/cast**, thay vì assert nguyên xi mọi bytecode offset hoặc constant pool index.

## 9. Dùng Reflection để đối chiếu source, erasure và generic metadata

Java Reflection cung cấp nhiều cặp API có ý nghĩa khác nhau.

### 9.1. Erased type với `getReturnType()`

```java
Method method = Holder.class.getDeclaredMethod("get");

Class<?> erased = method.getReturnType();
```

Với `Holder<T>.get()`, `erased` là `Object.class`. Đây là kiểu return phù hợp với erased JVM descriptor.

### 9.2. Generic declaration với `getGenericReturnType()`

```java
Type generic = method.getGenericReturnType();
```

Với Holder<T>, kết quả là một `TypeVariable` tên `T`. Nếu in `getTypeName()`, thường thấy `T`.

`Method.getGenericParameterTypes()` tương tự cho parameter types: method `set(T)` có generic parameter T, trong khi `getParameterTypes()` cho Object.

### 9.3. Field Reflection cũng phân biệt hai kiểu

```java
Field field = Holder.class.getDeclaredField("value");

Class<?> erased = field.getType();
Type generic = field.getGenericType();
```

Kết quả lần lượt mô tả Object và T. Đây là cách xác định generic metadata còn giữ tại declaration.

### 9.4. Generic superclass từ subclass cụ thể

```java
Type parent = StringHolder.class.getGenericSuperclass();
```

Nó mô tả `Holder<String>`, thường dưới dạng `ParameterizedType`.

Cần phân biệt:

```java
Holder.class.getTypeParameters()
```

trả declaration type variable T, còn:

```java
StringHolder.class.getGenericSuperclass()
```

mô tả việc một subclass cụ thể thay T bằng String ở immediate superclass declaration.

Muốn giải kiểu của field T **trong ngữ cảnh** subclass có nhiều tầng kế thừa, một type resolver phải ghép các mappings type variables, không chỉ lấy một Field object rồi tin T tự thành String.

### 9.5. Nhận diện bridge bằng Reflection

```java
for (Method method : StringHolder.class.getDeclaredMethods()) {
    System.out.println(method.getName()
            + ":" + method.isBridge()
            + ":" + method.isSynthetic());
}
```

`getDeclaredMethods()` có thể trả nhiều methods cùng tên và cùng parameter list nhưng khác return type, vì JVM class file có bridge. Để test ổn định, hãy lọc/sắp xếp và kiểm tra thêm return type/parameter types.

Cờ:

```java
method.isBridge()
method.isSynthetic()
```

là hai phép hỏi **riêng**.

### 9.6. Đừng thiết kế reflection framework bằng cách coi mọi method đều là API source

Nếu framework scan `getDeclaredMethods()` rồi đăng ký tất cả như những user-defined handlers, bridge methods có thể gây **đăng ký trùng**: `get()` typed và `get()` bridge cùng được thấy.

Một chiến lược phổ biến là loại các methods với `isBridge()` khi bạn chỉ muốn source-level handlers, nhưng không áp dụng mù quáng trong mọi framework: có bài toán cần bridge để phân tích đường dispatch và tương thích. Hãy định nghĩa mục tiêu scan trước khi lọc.

## 10. Bridge có thể xuất hiện do covariant return dù không có Generics

Đây là điểm thường bị bỏ sót.

Tạo file **`CovariantBridge.java`**:

```java
class CovariantBridge {
    static class Parent {
        Number value() {
            return 1;
        }
    }

    static class Child extends Parent {
        @Override
        Integer value() {
            return 2;
        }
    }

    public static void main(String[] args) {
        System.out.println(new Child().value());

        for (var method : Child.class.getDeclaredMethods()) {
            System.out.println(method.getName()
                    + ":" + method.getReturnType().getSimpleName()
                    + ":" + method.isBridge()
                    + ":" + method.isSynthetic());
        }
    }
}
```

Lệnh:

```bash
javac --release 17 -Xlint:all CovariantBridge.java
java CovariantBridge
```

Output của lần kiểm thử:

```text
2
value:Integer:false:false
value:Number:true:true
```

Ví dụ này có thứ tự Reflection đúng như lần chạy kiểm chứng, nhưng `getDeclaredMethods()` không bảo đảm thứ tự giữa các môi trường. Nếu dùng làm automated test, hãy sắp xếp trước khi so sánh.

`javap -p -s 'CovariantBridge$Child'` hiển thị:

```text
java.lang.Integer value();
  descriptor: ()Ljava/lang/Integer;

java.lang.Number value();
  descriptor: ()Ljava/lang/Number;
```

Child source trả `Integer`, Parent source trả `Number`. JVM descriptors khác nhau do return type, nên compiler sinh bridge trả Number để bảo toàn dispatch. **Không có `<T>` nào trong ví dụ này.**

Kết luận: bridge thường liên quan Java Generics, nhưng **không độc quyền của Generics**. Có thể xuất hiện do covariance của return type.

## 11. Phân biệt bridge, overload, override và synthetic

| Tình huống | Do ai khai báo/tạo? | Ý nghĩa |
| --- | --- | --- |
| `String get()` override `T get()` với T=String | Người viết Java source | Override theo quy tắc Generics của Java |
| `Object get()` có `ACC_BRIDGE` | Compiler | Cầu nối erased superclass descriptor tới typed override |
| `void set(Object)` có `ACC_BRIDGE` | Compiler | Cast Object sang String rồi gọi `set(String)` |
| Hai source methods `process(String)` và `process(Integer)` | Người viết Java source | Overloading hợp lệ, không tự là bridge |
| `Integer value()` override `Number value()` | Người viết Java source | Covariant-return overriding |
| `Number value()` synthetic bridge | Compiler | Giữ descriptor của superclass không generic |
| Một synthetic method khác | Compiler/tooling | Có thể không phải bridge |

### 11.1. Không coi mọi method trùng tên là bridge

Một class có hai overloads hợp lệ:

```java
void log(String value) {}
void log(Integer value) {}
```

chúng có parameter descriptors khác nhau và có thể đều là ordinary source methods; `isBridge()` thường false.

Bridge xuất phát từ quan hệ overriding/erasure/covariant return, không phải từ việc tên method bị trùng đơn giản.

### 11.2. Không tự gọi bridge như một overload bình thường

Trong source `StringHolder`, bạn không viết:

```java
// Không thể dùng đây để tuyên bố một source overload
// song song chỉ khác return type:
// public Object get() { ... }
```

JVM class file có thể chứa những method compiler tạo mà bạn không thể định nghĩa cùng signature ở Java source. Dùng typed API bình thường, để compiler/JVM thực hiện phần cầu nối.

## 12. Name clash sau erasure: compiler không tự tạo bridge để sửa mọi lỗi

Một lỗi phổ biến khi người học thấy bridge là nghĩ “nếu hai methods erase giống nhau, compiler luôn tự thêm bridge”. Thực tế **không**.

### 12.1. Hai overload chỉ khác generic argument

Tạo file **`ErasureClash.java`**:

```java
import java.util.List;

class ErasureClash {
    void process(List<String> names) {
    }

    void process(List<Integer> numbers) {
    }
}
```

Chạy:

```bash
javac --release 17 ErasureClash.java
```

Compiler báo:

```text
name clash: process(List<Integer>) and
process(List<String>) have the same erasure
```

Hai overload có thể trông khác nhau ở generic source, nhưng erasure của cả hai danh sách tham số đều là raw `List`.

Bạn phải sửa **API declaration**, ví dụ:

```java
void processNames(List<String> names) {}
void processNumbers(List<Integer> numbers) {}
```

hoặc một generic method chung nếu implementation thực sự giống nhau. Cast ở call site hoặc gọi `javap` không thể cứu source declaration sai.

### 12.2. Xung đột với generic superclass nhưng không phải override

Tạo file **`BadOverride.java`**:

```java
class BadOverride {
    static class Parent<T> {
        void set(T value) {
        }
    }

    static class Child extends Parent<String> {
        void set(Object value) {
        }
    }
}
```

Compiler Java 17 báo:

```text
name clash: set(Object) in Child and
set(String) in Parent have the same erasure,
yet neither overrides the other
```

Child khai báo `set(Object)` trong khi method được kế thừa sau thay T ở quan hệ generic là `set(String)`. Việc erasure cùng descriptor không biến method `set(Object)` thành một override hợp lệ theo Java source rules.

Cách đúng là:

```java
static class Child extends Parent<String> {
    @Override
    void set(String value) {
    }
}
```

Rồi để compiler sinh bridge nếu cần. Đừng tự thay parameter từ String sang Object với kỳ vọng “đúng bytecode descriptor là đủ”.

### 12.3. `@Override` giúp kiểm tra sớm

Luôn cân nhắc `@Override` trên method dự định override. Nếu vô tình sai parameter type hoặc khai báo một overload mới, compiler thường chỉ rõ rằng method không override đúng.

Bridge method được tạo từ **quan hệ override hợp lệ**, không phải là phép nới lỏng quy tắc override.

## 13. Đối chiếu từ source tới class file theo một ma trận

Xét class Holder<T>, StringHolder và Mapper<String> của chương trình chính.

| Vị trí | Java source declaration | JVM descriptor sau erasure/cụ thể hóa | Generic Signature hoặc vai trò |
| --- | --- | --- | --- |
| `Holder.value` | `T value` | `Ljava/lang/Object;` | `TT;` |
| `Holder.get` | `T get()` | `()Ljava/lang/Object;` | `()TT;` |
| `Holder.set` | `void set(T)` | `(Ljava/lang/Object;)V` | `(TT;)V` |
| `Holder.echo` | `<N extends Number> N echo(N)` | `(Ljava/lang/Number;)Ljava/lang/Number;` | `<N:Ljava/lang/Number;>(TN;)TN;` |
| `Holder.tags` | `List<String> tags()` | `()Ljava/util/List;` | `()Ljava/util/List<Ljava/lang/String;>;` |
| `StringHolder.get` | `String get()` | `()Ljava/lang/String;` | Typed implementation |
| `StringHolder` bridge get | Không viết | `()Ljava/lang/Object;` | `ACC_BRIDGE`, `ACC_SYNTHETIC` |
| `StringHolder.set` | `void set(String)` | `(Ljava/lang/String;)V` | Typed implementation |
| `StringHolder` bridge set | Không viết | `(Ljava/lang/Object;)V` | Cast rồi delegate |
| `Uppercase.apply` | `String apply(String)` | `(Ljava/lang/String;)Ljava/lang/String;` | Typed interface implementation |
| `Uppercase` bridge apply | Không viết | `(Ljava/lang/Object;)Ljava/lang/Object;` | Bridge cho Mapper<String> |

Bảng là bản đồ để bạn so output javap và Reflection. Không phải mọi non-generic method có thêm Signature attribute; đừng tự bịa một generic signature cho những method không cần metadata ấy.

## 14. Cách phân tích lỗi class file thực tế

### 14.1. Bước 1: Biên dịch sạch và xác định version

```bash
javac -version
javac --release 17 -Xlint:all GenericBytecodeDemo.java
```

`--release 17` đặt mức ngôn ngữ/API/class-file target theo Java 17 khi compiler đang chạy hỗ trợ mức này. Đây là cách tái lập ví dụ đáng tin hơn chỉ dùng `-source 17` trong những tình huống cần kiểm soát cả API target.

### 14.2. Bước 2: Xem descriptor bằng `-s`

```bash
javap -p -s 'GenericBytecodeDemo$Holder'
javap -p -s 'GenericBytecodeDemo$StringHolder'
```

Ghi ra các cặp method name + descriptor. Đừng vội phân tích chữ ký generic được javap in ở phần tiêu đề mà bỏ qua dòng descriptor.

### 14.3. Bước 3: Xem `Signature` và flags bằng `-v`

```bash
javap -p -v 'GenericBytecodeDemo$Holder'
javap -p -v 'GenericBytecodeDemo$StringHolder'
```

Tìm `Signature:`, `ACC_BRIDGE`, `ACC_SYNTHETIC`. Lưu ý một class có thể có Signature ở **class-level** cho superclass generic, trong khi từng method lại có hoặc không có Signature riêng.

### 14.4. Bước 4: Xem lệnh cast/delegation bằng `-c`

```bash
javap -p -c 'GenericBytecodeDemo$StringHolder'
```

Kiểm tra bridge `set(Object)` có `checkcast String`, rồi gọi typed set; bridge get(Object) ủy quyền typed get(String). Nếu cần hiểu tại sao xuất hiện `ClassCastException`, đây là bằng chứng trực tiếp.

### 14.5. Bước 5: Đối chiếu Reflection

Dùng:

```java
Class<?> type = StringHolder.class;

for (Method method : type.getDeclaredMethods()) {
    System.out.println(method.toGenericString()
            + " bridge=" + method.isBridge()
            + " synthetic=" + method.isSynthetic());
}
```

Reflection cho cái nhìn runtime; `javap` cho cái nhìn class file. Khi cả hai cùng xác nhận bridge flags và signatures, bạn tránh nhầm output bị compiler/tool khác tạo ra với source.

### 14.6. Bước 6: Kiểm tra dynamic dispatch từ biến superclass/interface

```java
Holder<String> ref = new StringHolder("hi");
String value = ref.get();
```

Lời gọi được biên dịch trong bối cảnh Holder<String> mà runtime dispatch vẫn phải đến StringHolder override. Kết quả đúng là tiêu chuẩn quan trọng, không phải chỉ việc có cờ bridge.

### 14.7. Bước 7: Không dựa hoàn toàn vào thứ tự Reflection

`Class.getDeclaredMethods()` không bảo đảm thứ tự. Nếu viết assertions, hãy lọc theo tên, parameter types, return type và flags, hoặc sắp xếp kết quả.

Một method bridge có thể cùng tên/danh sách parameters với typed method nếu chỉ khác return type. Do đó một test chỉ dùng `getDeclaredMethod("get")` mà không kiểm tra cờ/return có thể bỏ sót bridge. Hãy duyệt toàn bộ declared methods khi cần audit.

## 15. Reflection và framework: khi nào bridge gây duplicate?

Một dependency injection container, serializer, annotation scanner hoặc RPC framework có thể duyệt method declarations bằng Reflection. Nếu nó coi mọi `getDeclaredMethods()` là source-level APIs, class có cả:

```text
String get()
Object get()  [bridge]
```

có thể bị nhận thành hai handlers, hai bean properties hoặc hai operations ngoài ý muốn.

Một hướng xử lý thường gặp là:

```java
if (method.isBridge()) {
    continue;
}
```

khi mục tiêu của scanner là **phương thức do lập trình viên khai báo ở source**. Tuy nhiên:

- Có frameworks cần bridge để liên kết annotations/overridden method metadata.
- Một bridge có thể không mang cùng metadata/annotations như typed implementation.
- Các công cụ cần phân biệt override tree và generic declaration có thể phải **resolve method tương ứng**, không chỉ bỏ mọi bridge.

Vì thế đừng biến “luôn bỏ bridge” thành luật toàn cục. Hãy xác định scanner cần thực sự đại diện cho *source-level method*, *all JVM methods*, hay *overridden generic contract*.

### 15.1. `isSynthetic()` không phải bộ lọc tương đương `isBridge()`

Một method synthetic có thể tồn tại vì compiler-generated artifact không liên quan bridge. Nếu chỉ muốn lọc bridge, dùng `isBridge()`. Nếu muốn lọc mọi compiler-generated methods, đó là chính sách khác và cần cân nhắc các trường hợp đặc biệt.

### 15.2. Generic metadata không tự validate runtime data

Reflection thấy `List<String>` ở Signature không nghĩa mọi phần tử trong một list runtime là String. Raw/unchecked operations có thể đưa dữ liệu sai vào. Type token và generic signatures cho biết **kiểu đã khai báo**, không tự hoàn thành kiểm tra object graph.

Framework deserialize thực sự phải dùng metadata để chuyển đổi/kiểm tra dữ liệu, không chỉ cast một raw List và coi như đã xong.

## 16. `javap` và Java Reflection trả lời những câu hỏi khác nhau

| Câu hỏi | Công cụ phù hợp |
| --- | --- |
| JVM method descriptor là gì? | `javap -s`, `javap -v` |
| Class file có generic Signature nào? | `javap -v` |
| Method có ACC_BRIDGE/ACC_SYNTHETIC không? | `javap -v` |
| Bridge có cast/delegate như thế nào? | `javap -c` |
| Method ở runtime có `isBridge()` không? | Java Reflection |
| Erased parameter/return types trong Reflection? | `getParameterTypes()`, `getReturnType()` |
| Generic parameter/return metadata? | `getGenericParameterTypes()`, `getGenericReturnType()` |
| Generic superclass đã khai báo? | `Class.getGenericSuperclass()` |
| Source đã khai báo method nào? | Source code + đối chiếu reflection/javap |
| Một field value thực có đúng type arguments không? | Validation dữ liệu thật, không chỉ Reflection Signature |

Làm đúng thường cần kết hợp **source, javap, Reflection và runtime test**. Chỉ đọc một lớp biểu diễn có thể khiến bạn kết luận sai.

## 17. Vì sao có thể thấy class file methods “không thể viết” trong Java source?

JVM bytecode format và Java source rules không hoàn toàn giống nhau.

Java method overloading ở source dựa trên method signatures theo JLS, không cho chỉ khác return type để thành overload. Nhưng method descriptor của JVM có return descriptor, và `javac` có thể phát sinh bridge để giữ binary-level dispatch.

Do đó trong bytecode:

```text
get:()Ljava/lang/String;
get:()Ljava/lang/Object;
```

có thể cùng xuất hiện. Java source không cho bạn khai báo hai methods `String get()` và `Object get()` cạnh nhau như hai overload thông thường.

**Bài học lớn:** không nên suy luận ngược rằng mọi class-file method là một Java source method viết tay. Metadata `ACC_BRIDGE`, `ACC_SYNTHETIC` và việc đọc source giúp phân biệt.

### 17.1. Bridge cũng ảnh hưởng quan sát của static analyzers

Nếu một công cụ chỉ đếm public methods trong class file, nó có thể đếm cả bridge và typed implementation, cho số lượng lớn hơn source. Khi xây rule về API surface, cần quyết định có tính bridge hay không.

### 17.2. Không phải cứ có `Signature` là có bridge

Một `List<String> tags()` có `Signature` giữ type argument nhưng thường không cần bridge nếu không tham gia một quan hệ override đòi descriptor cầu nối.

Một class generic `Holder<T>` có nhiều Signature entries, nhưng **Holder tự nó** không nhất thiết chứa bất kỳ bridge nào. Bridge ở subclass hoặc interface implementor được sinh khi thực sự cần.

## 18. Class-file metadata và binary compatibility

Type erasure giúp Java Generics duy trì tương thích với các thư viện trước thời Generics trong nhiều trường hợp, nhưng đừng suy ra **mọi thay đổi generic signature đều binary-compatible**.

Ví dụ thay raw parameter type `List` thành `Collection` đổi JVM descriptor; client bytecode cũ có thể không liên kết tới method mới. Ngược lại, chỉ thay generic type argument trong `Signature` mà không đổi descriptor có thể không thay JVM-level method identity, nhưng source type checking vẫn có thể thay đổi và tạo lỗi khi biên dịch lại.

Bridge là một phần cơ chế giữ overriding/binary method lookup khi subclass specialized method có descriptor khác do erasure hoặc return covariance.

### 18.1. Khi sửa generic superclass hoặc interface

Một thay đổi từ:

```java
interface Mapper<T> {
    T apply(T input);
}
```

sang một contract có bound/return/parameter khác có thể đổi descriptor sau erasure, khiến các classes đã biên dịch trước đó gặp vấn đề tương thích. Hãy đánh giá:

- Source compatibility khi client biên dịch lại.
- Binary compatibility với compiled clients.
- Override/bridge method được compiler tạo lại.
- Reflection-based frameworks có đang phụ thuộc Signature/bridge metadata không.

Đừng xóa synthetic bridge methods bằng bytecode post-processing chỉ vì “source không khai báo”; điều đó có thể làm mất đường dispatch cần thiết.

### 18.2. Bytecode tools nên bảo toàn thuộc tính thích hợp

Nếu một build step tối ưu/biến đổi class file, việc bỏ Signature không nhất thiết phá JVM linkage ngay, nhưng có thể làm mất thông tin generic cần cho Reflection, serializer hoặc framework. Việc bỏ bridges có thể nguy hiểm hơn vì chúng có thể được dùng làm method dispatch entry points.

Khi debug sự khác nhau giữa build thường và build đã qua obfuscation/shading/instrumentation, kiểm tra cả descriptors, Signature, flags và code của bridge.

## 19. Bẫy thường gặp khi học từ `javap`

### Bẫy 1 — Nhìn `javap` in `T` rồi nói JVM thật sự gọi method nhận T

Không chính xác. `javap` có thể dựng chữ ký generic dễ đọc từ metadata, nhưng descriptor cho method có bound mặc định vẫn là Object. JVM không có parameter type `T` trong descriptor.

### Bẫy 2 — Cho rằng `Signature` và descriptor là cùng một dữ liệu

Sai. Descriptor dùng cho JVM-level type/linkage; Signature lưu metadata generic theo grammar khác.

### Bẫy 3 — Nghĩ Signature giữ mọi type argument của object instance

Sai. Nó mô tả **declarations**, không phải type argument của mỗi instance lúc chạy.

### Bẫy 4 — Nghĩ chỉ generic methods mới cần bridge

Sai. Một subclass override generic superclass/interface bằng concrete typed method có thể cần bridge; covariant returns không generic cũng có thể tạo bridge.

### Bẫy 5 — Nghĩ mọi method generic sinh bridge

Sai. Bridge được sinh khi có nhu cầu giữ overriding/dispatch. Một generic method độc lập như `Holder.echo` có thể có Signature nhưng không cần bridge.

### Bẫy 6 — Tưởng `synthetic=true` luôn bằng `bridge=true`

Sai. Bridge thường synthetic trong output javac kiểm chứng, nhưng synthetic members còn có thể phục vụ mục đích khác.

### Bẫy 7 — Tự tạo `void set(Object)` để override `void set(T)` đã specialized

Có thể gây name clash “same erasure, yet neither overrides”. Định nghĩa override đúng typed parameter theo source rules, compiler sẽ tự sinh bridge.

### Bẫy 8 — Overload `process(List<String>)` và `process(List<Integer>)`

Không hợp lệ do cùng erasure. Bridge không phải cơ chế sửa overload declarations có name clash.

### Bẫy 9 — Duyệt Reflection không sắp xếp và assert thứ tự

`getDeclaredMethods()` không có ordering guarantee. Sắp xếp/lọc theo đầy đủ dấu hiệu cần thiết.

### Bẫy 10 — Framework đăng ký bridge như một handler thứ hai

Có thể xảy ra khi quét mọi declared methods mà không phân biệt source API và synthetic compiler artifacts.

### Bẫy 11 — Xem cast trong bridge là lỗi compiler

`checkcast` trong `set(Object)` là cách giữ type safety/ủy quyền cho method nhận String. Nếu Reflection/raw code gọi sai kiểu, `ClassCastException` là kết quả có thể đúng.

### Bẫy 12 — Cố so constant-pool indices giữa các compiler

Các số `#...`, offsets và chi tiết formatting không ổn định. Hãy so semantics của descriptor, Signature, flags và đường delegate.

### Bẫy 13 — Dùng Reflection generic type để khẳng định runtime collection an toàn

Generic metadata không kiểm tra elements. Nếu code dùng raw/unchecked casts, heap pollution vẫn có thể xảy ra.

### Bẫy 14 — Nhầm method descriptor với Java overloading rule

Trong descriptor JVM, return type được mã hóa. Nhưng Java source không cho overload chỉ dựa trên return type. Bridge được compiler tạo dưới luật riêng.

### Bẫy 15 — Chỉ chạy `javap` trên subclass

Bạn cần xem **cả superclass/interface erased descriptor** và **subclass implementation/bridge** để giải thích vì sao cầu nối tồn tại.

## 20. Checklist khi gặp bridge hoặc lỗi generic erasure

1. Xác định compiler/JDK version và compile target; với bài này dùng `--release 17`.
2. Mở source declaration của superclass/interface và subclass; tìm type parameters/bounds.
3. Ghi chữ ký source sau khi **substitute type arguments**, ví dụ `T = String`.
4. Tính erasure của từng method theo bounds, không mặc định Object nếu có bound.
5. Dùng `javap -p -s` xác nhận descriptors của superclass/interface và subclass.
6. Dùng `javap -p -v` tìm `Signature:` ở class/fields/methods cần đọc.
7. Với methods bổ sung không thấy ở source, kiểm tra `ACC_BRIDGE`, `ACC_SYNTHETIC`.
8. Dùng `javap -p -c` xem bridge delegate method nào và cast gì.
9. Dùng Reflection `getDeclaredMethods()`, `isBridge()`, `isSynthetic()` và return/parameter types để xác nhận từ runtime.
10. Không dựa vào ordering của Reflection; sắp xếp hoặc lọc trước khi assert.
11. Kiểm tra dynamic dispatch qua biến có kiểu superclass/interface.
12. Với lỗi `name clash`, phân biệt declaration conflict với trường hợp override hợp lệ có bridge.
13. Với generic field/return, tách `getType()`/`getReturnType()` khỏi `getGenericType()`/`getGenericReturnType()`.
14. Khi thay đổi API hoặc xử lý class file, đánh giá source/binary compatibility và framework Reflection.
15. Đừng dùng raw/unchecked code như cách chứng minh bridge hoạt động; nếu cần test runtime error, khoanh nó trong ví dụ kiểm thử.
16. Ghi nhận rõ output phụ thuộc compiler/class-file tool ở những chi tiết như constant-pool indices.

## 21. Bài tập thực hành và đáp án

### Bài 1 — Erasure của type variable không bound

Cho:

```java
class Box<T> {
    T value();
}
```

**Câu hỏi:** Return descriptor của `value()` là gì nếu T không có bound tường minh?

<details>
<summary>Đáp án</summary>

Erasure của T là Object, nên method descriptor là `()Ljava/lang/Object;`. Nếu class file giữ generic method Signature thì phần return dùng `TT;`, ví dụ `()TT;`.

</details>

### Bài 2 — Bound thay đổi descriptor

Cho:

```java
<N extends Number> N twice(N n);
```

**Câu hỏi:** Erased parameter/return types là gì?

<details>
<summary>Đáp án</summary>

Cả hai erase về Number, nên descriptor là `(Ljava/lang/Number;)Ljava/lang/Number;`. Generic Signature giữ quan hệ kiểu N ở cả input/output.

</details>

### Bài 3 — `List<String>` ở return

```java
List<String> names();
```

**Câu hỏi:** Vì sao descriptor không chứa String?

<details>
<summary>Đáp án</summary>

Parameterized type `List<String>` erase thành List, nên descriptor là `()Ljava/util/List;`. Generic `Signature` có thể ghi `()Ljava/util/List<Ljava/lang/String;>;`.

</details>

### Bài 4 — Vì sao StringHolder cần Object get()?

Cho:

```java
class Base<T> {
    T get() { return null; }
}
class Derived extends Base<String> {
    @Override String get() { return "x"; }
}
```

<details>
<summary>Đáp án</summary>

Erased Base.get descriptor trả Object, còn typed Derived.get trả String. Compiler có thể tạo bridge Object get() trong Derived để các lời gọi qua erased Base method descriptor dispatch tới typed override.

</details>

### Bài 5 — Vì sao bridge `set(Object)` có cast?

<details>
<summary>Đáp án</summary>

Method typed implementation nhận String, trong khi erased superclass method nhận Object. Bridge phải cast argument Object thành String trước khi delegate. Nếu caller qua raw/reflection boundary truyền Integer, cast có thể ném ClassCastException.

</details>

### Bài 6 — Cờ Bridge và Synthetic có giống nhau không?

<details>
<summary>Đáp án</summary>

Không. `ACC_BRIDGE` nhận diện bridge; `ACC_SYNTHETIC` đánh dấu compiler-generated member rộng hơn. Trong ví dụ javac Java 17, bridge có cả hai cờ, nhưng không phải mọi synthetic method đều là bridge.

</details>

### Bài 7 — Covariant return không generic

Nếu Parent trả Number và Child override trả Integer, bridge có thể xuất hiện không?

<details>
<summary>Đáp án</summary>

Có. Trong ví dụ `CovariantBridge`, compiler sinh Integer value() typed implementation và Number value() bridge. Đây là cầu nối do covariant return, ngay cả khi không khai báo generic type parameters.

</details>

### Bài 8 — Lỗi overload sau erasure

```java
void save(List<String> names) {}
void save(List<Integer> counts) {}
```

**Câu hỏi:** Compiler có tự sinh bridge để phân biệt hai method không?

<details>
<summary>Đáp án</summary>

Không. Cả hai có cùng erasure của tham số là List, dẫn tới name clash. Phải đổi chữ ký/tên hoặc tái thiết kế API.

</details>

### Bài 9 — Reflection thấy hai get methods

Nếu Reflection trên subclass liệt kê một typed get và một bridge get, làm thế nào scanner chỉ đăng ký method người dùng khai báo?

<details>
<summary>Đáp án</summary>

Một hướng là lọc `method.isBridge()` rồi xem xét `isSynthetic()` và context của framework. Cần kiểm tra return types vì JVM có thể chứa cùng tên/parameters với return descriptors khác nhau. Nếu scanner cần liên kết generic override metadata, có thể cần resolve bridge thay vì đơn giản bỏ qua.

</details>

### Bài 10 — Generic superclass và generic field

`StringHolder extends Holder<String>` nhưng `Holder.value` có generic type T. Tại sao `Holder.class.getDeclaredField("value").getGenericType()` không tự trả String.class?

<details>
<summary>Đáp án</summary>

Field declaration của Holder vẫn dùng type variable T. StringHolder's generic superclass metadata lưu mapping T→String, nhưng phép Reflection trên chính Holder field không mang sẵn context của subclass. Cần resolver kết hợp hai nguồn metadata.

</details>

### Bài 11 — `javap -s` hay `javap -v`?

<details>
<summary>Đáp án</summary>

`-s` in descriptors. `-v` hiển thị verbose attributes/flags, trong đó có `Signature` và `ACC_BRIDGE`. Dùng `-c` để xem JVM instructions và `-p` để xem private members.

</details>

### Bài 12 — Bridge có bảo đảm List<String> thật sự chỉ chứa String không?

<details>
<summary>Đáp án</summary>

Không. Bridge giữ dispatch khi generic overriding/covariant return cần descriptor phù hợp. Nó không tự kiểm tra từng phần tử bên trong generic collections hoặc khắc phục heap pollution do raw/unchecked operations.

</details>

## 22. Tổng kết

Để đọc đúng Generics trong class file, hãy nhìn **cả ba tầng**:

1. **Source generic declaration** cho compiler hiểu quan hệ kiểu: `T get()`, `void set(T)`, `<N extends Number> N echo(N)`, `List<String> tags()`.
2. **Erased JVM descriptor** để runtime định danh/call method: Object thay T không bound, Number thay N bound, List thay List<String>.
3. **Generic `Signature` metadata** giúp công cụ phục hồi khai báo kiểu: `()TT;`, `(TT;)V`, `<N:Ljava/lang/Number;>(TN;)TN;`, các nested generic type arguments.

Khi một subclass specialized generic override hoặc dùng covariant return có descriptor khác, compiler có thể sinh **bridge method**. Trên Java 17, `javap -v` cho thấy cờ `ACC_BRIDGE, ACC_SYNTHETIC`; Reflection xác nhận bằng `Method.isBridge()` và `isSynthetic()`. Bridge chỉ nối các descriptors/đường dispatch theo quan hệ hợp lệ, **không** sửa name clash declarations và **không** reify toàn bộ generic types của object instances.

Quy trình đáng tin là: **đọc source → tính erasure → xem `javap -s` → kiểm tra `Signature`/flags với `javap -v` → xem bridge code bằng `javap -c` → đối chiếu Reflection và runtime dispatch**. Khi nắm quy trình này, bạn sẽ phân biệt được API người dùng khai báo, metadata Generics, và những bytecode methods compiler bắt buộc phải phát sinh để Java overriding hoạt động đúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-testing-and-diagnostics-007">Giới hạn @SuppressWarnings và yêu cầu giải thích tính an toàn</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn `@SuppressWarnings` và yêu cầu giải thích tính an toàn trong Java Generics

## 1. Vấn đề cốt lõi: hết cảnh báo không có nghĩa là an toàn kiểu

Java Generics cho phép compiler kiểm tra nhiều quan hệ kiểu ngay trong mã nguồn, nhưng vẫn tồn tại các ranh giới mà thông tin generic bị thiếu hoặc không thể kiểm chứng hoàn toàn. Ví dụ, một thư viện cũ trả raw `List`, một phép cast từ `Object` sang `List<String>`, hoặc một helper tạo mảng `T[]` bằng reflection có thể phát cảnh báo khi bật `javac -Xlint:unchecked`.

Java cung cấp annotation `@SuppressWarnings` để **yêu cầu compiler bỏ qua một số nhóm cảnh báo tại vị trí được chú thích**:

```java
@SuppressWarnings("unchecked")
List<String> names = (List<String>) unknown;
```

Đây chỉ là mảnh code minh họa với biến `unknown` giả định. Annotation có thể làm biến mất cảnh báo ở một phép cast, nhưng **không thêm kiểm tra element type ở runtime**. Nếu `unknown` thực sự là list chứa `Integer`, lỗi vẫn có thể phát sinh khi đọc một phần tử như `String`.

Bởi vậy, trong thiết kế API nghiêm túc, `@SuppressWarnings` phải đi kèm **lập luận về tính an toàn**:

1. Dữ liệu đến từ đâu, ai kiểm soát nó?
2. Compiler không chứng minh được điều gì?
3. Tại sao invariant cần thiết vẫn đúng?
4. Còn đường ghi nào có thể phá invariant sau khi cast không?
5. Phép kiểm thử hoặc cách tổ chức API nào bảo vệ điều đã khẳng định?

**Nguyên tắc:** Trước tiên cố gắng **loại bỏ cảnh báo bằng thiết kế có kiểm tra kiểu**. Chỉ suppression khi đã biết rõ lý do compiler không thể chứng minh và có thể giải thích vì sao operation đó vẫn an toàn theo hợp đồng cụ thể.

## 2. `@SuppressWarnings` là gì, và nó không phải gì?

`java.lang.SuppressWarnings` là annotation chuẩn, có dạng:

```java
@SuppressWarnings("unchecked")
```

hoặc nhiều key:

```java
@SuppressWarnings({"unchecked", "rawtypes"})
```

Những chuỗi như `"unchecked"` và `"rawtypes"` chỉ định **loại compiler warnings**, không phải các Java exception classes.

### Nó có thể làm gì?

- Yêu cầu compiler không báo một nhóm warning trên phần tử được chú thích và phạm vi liên quan.
- Giữ log build gọn hơn tại một điểm đã được chứng minh an toàn.
- Cho phép khoanh vùng các ranh giới legacy, reflection hoặc generic arrays mà compiler hiện không biểu diễn được đầy đủ invariant.
- Tạo một dấu hiệu để người review hỏi: vì sao code ở đây cần vượt qua kiểm tra generic thông thường?

### Nó không thể làm gì?

- Không sửa sai generic type argument.
- Không biến `List<Integer>` thành `List<String>`.
- Không tạo ra runtime `List<String>.class`.
- Không kiểm tra từng phần tử trong một list.
- Không chặn `ClassCastException` hoặc `ArrayStoreException`.
- Không đảm bảo dữ liệu thread-safe, immutable hoặc đúng nghiệp vụ.
- Không biến một **compile error** thành mã hợp lệ.
- Không thay thế code review hay runtime tests.

Một chương trình có annotation nhưng sai kiểu vẫn có thể thất bại khi chạy. Và một chương trình không còn warnings vẫn có thể có bug nằm ngoài khả năng phát hiện của compiler.

## 3. Cú pháp và phạm vi áp dụng

Ví dụ về những declaration có thể được chú thích:

```java
@SuppressWarnings("unchecked")
static <T> T uncheckedHelper(Object input) {
    return (T) input;
}
```

Method trên là **mẫu không an toàn để thảo luận**, không phải lời khuyên triển khai: cast tới `T` bị type erasure nên annotation không làm input thành đúng `T`.

Có thể đặt annotation ở **local variable declaration**:

```java
@SuppressWarnings("unchecked")
List<String> names = (List<String>) source;
```

Hoặc ở một class:

```java
@SuppressWarnings("rawtypes")
class LegacyBridge {
    // ...
}
```

Các mảnh này cần đặt trong class/method phù hợp nếu muốn biên dịch.

### Phạm vi suppression

`@SuppressWarnings` tác động đến declaration được chú thích và những phần tử nằm trong phạm vi theo quy tắc của Java. Ví dụ, một annotation đặt ở cấp class có thể ẩn warnings trong nhiều methods và field declarations bên trong.

Một cảnh báo mới trong tương lai có thể bị **ẩn ngoài ý muốn** nếu phạm vi annotation quá rộng. Vì thế nên chọn phạm vi nhỏ nhất đủ giải quyết một warning đã được phân tích.

Ví dụ nên ưu tiên:

```java
@SuppressWarnings("unchecked")
T[] array = (T[]) java.lang.reflect.Array.newInstance(type, length);
```

hơn việc đặt `@SuppressWarnings("unchecked")` lên cả class khi chỉ có một phép cast thực sự cần giải thích.

`@SuppressWarnings` được dành cho **declaration**, không phải một cách gắn tùy ý vào mọi subexpression; muốn giới hạn warning ở một phép gán, hãy đặt một local variable declaration riêng có tên rõ ràng.

## 4. Các warning key liên quan Java Generics

Dùng `javac` của JDK 17 trở lên để thấy cảnh báo:

```bash
javac -Xlint:unchecked,rawtypes,varargs Example.java
```

Một số key thường gặp:

| Key | Tình huống | Hướng xử lý ưu tiên |
|---|---|---|
| `unchecked` | Cast/conversion/method invocation không thể kiểm tra generic đầy đủ | Đổi API typed, thêm checked adapter hoặc chứng minh invariant |
| `rawtypes` | Dùng raw `List`, `Map`, `Class`, v.v. | Thêm type arguments hoặc wildcard |
| `varargs` | Thao tác tiềm ẩn không an toàn trên varargs parameter có non-reifiable component | Viết lại method an toàn; cân nhắc `@SafeVarargs` khi đủ điều kiện |
| `deprecation` | Gọi API deprecated | Nâng cấp API hoặc ghi rõ lý do tương thích |
| `removal` | Dùng API đánh dấu để loại bỏ | Lập kế hoạch migration |

`@SuppressWarnings` nhận chuỗi cảnh báo. Các key được compiler nhận biết có thể phụ thuộc compiler và phiên bản. Không nên nghĩ các key là một enum cố định do JVM thực thi tại runtime.

Xem các tùy chọn compiler thực tế:

```bash
javac --help-extra
```

Nếu cần build không được phép còn warning, có thể dùng:

```bash
javac -Xlint:unchecked,rawtypes,varargs -Werror Example.java
```

`-Werror` yêu cầu warning ảnh hưởng kết quả biên dịch, **không** bổ sung kiểm tra kiểu runtime. Nếu code vẫn có `@SuppressWarnings` che một thao tác rủi ro, `-Werror` không tự vạch ra thao tác ấy.

## 5. Compile error khác với warning có thể suppression

Xem đoạn:

```java
import java.util.ArrayList;
import java.util.List;

class NotFixedBySuppression {
    @SuppressWarnings("unchecked")
    static void demo() {
        List<String> words = new ArrayList<>();
        words.add(123); // CỐ Ý KHÔNG BIÊN DỊCH.
    }
}
```

`words.add(123)` sai type argument `String`: compiler có bằng chứng rõ ràng rằng `Integer` không tương thích với `String`. Đây là **compile-time error**. Annotation không thể thay đổi hợp đồng `List<String>` để chấp nhận số.

Ngược lại:

```java
Object unknown = java.util.List.of("A", 10);

@SuppressWarnings("unchecked")
java.util.List<String> words =
        (java.util.List<String>) unknown;
```

Ép kiểu tổng quát này có thể biên dịch nhưng không thể xác minh type argument `String` ở runtime. Compiler sẽ báo unchecked warning nếu không có suppression. Cảnh báo nói rằng bằng chứng kiểu **chưa đầy đủ**, không phải compiler đã chứng minh object là đúng hoặc sai.

Đây chính là lúc người viết phải cung cấp một invariant ngoài khả năng chứng minh tĩnh của compiler, hoặc thay bằng một API kiểm tra thực tế.

## 6. Ví dụ chạy được: suppression không ngăn `ClassCastException`

Tạo tệp **`SuppressionDoesNotMakeSafeDemo.java`**:

```java
import java.util.List;

public class SuppressionDoesNotMakeSafeDemo {
    public static void main(String[] args) {
        Object input = List.of("A", 42);

        @SuppressWarnings("unchecked")
        List<String> words = (List<String>) input;

        System.out.println(words.size());

        try {
            String second = words.get(1);
            System.out.println(second);
        } catch (ClassCastException ex) {
            System.out.println("ClassCastException");
        }
    }
}
```

Biên dịch và chạy bằng Java 17:

```bash
javac -Xlint:unchecked,rawtypes,varargs SuppressionDoesNotMakeSafeDemo.java
java SuppressionDoesNotMakeSafeDemo
```

Stdout:

```text
2
ClassCastException
```

Phân tích:

1. `input` là một `List` thực, nhưng phần tử thứ hai là `Integer`.
2. `(List<String>) input` không thể xác minh `<String>` tại runtime vì **type erasure**.
3. `@SuppressWarnings("unchecked")` chỉ ẩn diagnostic của compiler ở declaration `words`.
4. `words.size()` không truy cập phần tử với kiểu `String`, nên trả `2`.
5. `words.get(1)` được dùng như `String`: phép kiểm tra kiểu khi sử dụng kết quả gặp object `Integer` và ném `ClassCastException`.

Ví dụ minh chứng một anti-pattern: **làm im compiler không thể là định nghĩa của type safety**.

## 7. `rawtypes` và `unchecked` không giống nhau

Raw types có thể làm suy giảm kiểm tra kiểu:

```java
List raw = new ArrayList();
raw.add("hello");
List<String> typed = raw;
```

Đoạn này cần import `java.util.List` và `java.util.ArrayList`.

Những vấn đề khác nhau:

- `List raw` và `new ArrayList()` là các raw type usages, liên quan `rawtypes`.
- `raw.add("hello")` gọi method qua raw receiver, có thể sinh warning `unchecked`.
- `List<String> typed = raw` dùng unchecked conversion từ raw `List` sang parameterized `List<String>`.

Một người có thể viết:

```java
@SuppressWarnings({"rawtypes", "unchecked"})
```

nhưng chỉ nên dùng khi có bằng chứng giải thích cho **cả hai** loại warning. Nếu có thể thay `List raw` bằng `List<?>`, đừng giữ raw type chỉ vì annotation đã che được.

Đối với API chỉ đọc dữ liệu chưa biết element type:

```java
static void display(List<?> source) {
    for (Object item : source) {
        System.out.println(item);
    }
}
```

Wildcard vừa giúp biểu diễn chính xác ý định vừa giữ compile-time checking tốt hơn raw type.

## 8. Thay suppression bằng generic API có quan hệ kiểu rõ ràng

Nhiều warnings được giải quyết đúng bằng cách sửa chữ ký method.

Thiết kế yếu:

```java
static List copy(List source) {
    return new ArrayList(source);
}
```

Đây là mảnh minh họa có raw types.

Thiết kế typed:

```java
static <T> List<T> copy(List<? extends T> source) {
    return new ArrayList<>(source);
}
```

Với `List<String>`, compiler suy luận `T` phù hợp mà không cần cast không được kiểm tra.

Điều này cho thấy khác biệt:

- **Suppression**: giữ operation mà compiler không thể chứng minh, chỉ ẩn warning.
- **Redesign bằng Generics**: cung cấp nhiều quan hệ kiểu hơn để compiler tự chứng minh đúng trong code bình thường.

Cần lưu ý: nếu dữ liệu **đến từ raw legacy boundary đã không an toàn**, chỉ đổi chữ ký bên ngoài thành `List<T>` bằng unchecked cast sẽ không thực sự kiểm tra nội dung. Đôi khi phải thêm bước validation khi đi qua boundary đó.

## 9. Adapter kiểm tra từng phần tử: loại bỏ cast mù

Một adapter nhận `List<?>` và `Class<T>` có thể tạo ra list mới sau khi kiểm tra từng phần tử bằng runtime class token.

Tệp **`CheckedCopyInsteadOfSuppressionDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class CheckedCopyInsteadOfSuppressionDemo {

    static <T> List<T> checkedCopy(
            List<?> source, Class<T> elementType) {

        List<T> copy = new ArrayList<>();

        for (Object element : source) {
            copy.add(elementType.cast(element));
        }

        return List.copyOf(copy);
    }

    public static void main(String[] args) {
        List<String> good =
                checkedCopy(List.of("red", "blue"), String.class);

        System.out.println(good);

        try {
            checkedCopy(List.of("red", 9), String.class);
        } catch (ClassCastException ex) {
            System.out.println("Rejected invalid element");
        }
    }
}
```

Lệnh:

```bash
javac -Xlint:unchecked,rawtypes,varargs CheckedCopyInsteadOfSuppressionDemo.java
java CheckedCopyInsteadOfSuppressionDemo
```

Stdout:

```text
[red, blue]
Rejected invalid element
```

Ở đây không cần `@SuppressWarnings`:

1. `List<?>` cho phép nhận danh sách chưa biết element type mà không khai báo một type argument giả.
2. `Class<T>` là class token do caller cung cấp, ví dụ `String.class`.
3. `elementType.cast(element)` xác minh từng runtime object.
4. Chỉ dữ liệu qua kiểm tra mới được thêm vào `List<T>`.
5. `List.copyOf` tạo list unmodifiable nhưng từ chối `null`; adapter phải thay đổi nếu muốn hỗ trợ null.

Bản sao là **shallow copy**: các object phần tử mutable vẫn có thể bị thay đổi từ tham chiếu bên ngoài. Và `Class<T>` chỉ xác minh class phần tử trực tiếp; với các kiểu như `List<String>` lồng nhau, cần validator có cấu trúc sâu hơn.

## 10. Khi thật sự cần suppression: invariant phải được viết rõ

Không phải warning nào cũng loại bỏ được mà vẫn giữ API hiện có. Trong một số generic helpers, compiler thiếu cách biểu diễn một mối liên hệ mà programmer biết từ construction path.

Ví dụ một method tạo array theo class token:

```java
static <T> T[] newArray(Class<T> componentType, int length) {
    // Runtime array được cấp với đúng componentType.
}
```

Compiler không thể tự chứng minh một object trả về từ `Array.newInstance` có static type `T[]`; API reflection trả `Object`. Ta có thể cần một cast đến `T[]`.

**Nhưng** trước khi suppression, phải chứng minh:

- `componentType` là class token thích hợp và không null.
- Component được dùng **trực tiếp để cấp phát array**, không lấy từ một object không tin cậy.
- Không có raw alias/unchecked path thay component type sau khi cấp phát.
- Không đưa một `Class<?>` không được kiểm tra vào vị trí `Class<T>` bằng cast sai.
- Các trường hợp primitive hoặc `void` token được xử lý theo hợp đồng.

Khi đầy đủ điều kiện, suppression có cơ sở hơn so với một cast tùy tiện từ input bất kỳ sang `List<String>`.

## 11. Ví dụ hoàn chỉnh: generic array helper có invariant

Tạo tệp **`JustifiedArrayCastDemo.java`**:

```java
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Objects;

public class JustifiedArrayCastDemo {

    static <T> T[] createArray(Class<T> componentType, int length) {
        Objects.requireNonNull(componentType, "componentType");

        if (componentType.isPrimitive()
                || componentType == Void.TYPE) {
            throw new IllegalArgumentException(
                    "A reference component class is required");
        }

        // Safety: Array.newInstance tạo mảng có runtime component
        // chính là componentType; không có Object[] alias ghi sai kiểu.
        @SuppressWarnings("unchecked")
        T[] result = (T[]) Array.newInstance(componentType, length);

        return result;
    }

    public static void main(String[] args) {
        String[] words = createArray(String.class, 2);

        words[0] = "A";
        words[1] = "B";

        System.out.println(Arrays.toString(words));
        System.out.println(
                words.getClass().getComponentType().getName());
    }
}
```

Biên dịch và chạy:

```bash
javac -Xlint:unchecked,rawtypes,varargs JustifiedArrayCastDemo.java
java JustifiedArrayCastDemo
```

Stdout:

```text
[A, B]
java.lang.String
```

Tại sao suppression có thể chấp nhận ở đây?

1. Runtime array được tạo bằng **chính `componentType` đã truyền vào**.
2. Method có generic signature `<T> T[]` và không công khai `Object[]` alias cho code khác tùy ý ghi.
3. Token primitive bị từ chối để tránh sự khác biệt giữa `int[]` và reference array `Integer[]`.
4. Type erasure vẫn khiến compiler không thể chứng minh `Object` mà reflection trả về phù hợp với static type `T[]`.
5. Suppression đặt tại **một local variable** là chính phép cast cần giải thích.

Cần hiểu giới hạn: `List<String>.class` không tồn tại, nên mẫu này không khởi tạo specialized array `List<String>[]`. Và người gọi vẫn phải tránh tự phá invariant qua raw/unchecked code bên ngoài.

Đây là một **local proof dựa trên construction**, không phải bằng chứng mọi generic array cast đều an toàn.

## 12. Tránh cả suppression bằng array factory khi phù hợp

Nếu caller đã biết cách tạo array, có thể truyền một factory function thay vì runtime reflection:

Tệp **`ArrayFactoryWithoutCastDemo.java`**:

```java
import java.util.Arrays;
import java.util.function.IntFunction;

public class ArrayFactoryWithoutCastDemo {

    static <T> T[] create(
            int size, IntFunction<T[]> factory) {
        return factory.apply(size);
    }

    public static void main(String[] args) {
        String[] words = create(3, String[]::new);

        words[0] = "Java";
        words[1] = "17";
        words[2] = "Generics";

        System.out.println(Arrays.toString(words));
    }
}
```

Biên dịch/chạy:

```bash
javac -Xlint:unchecked,rawtypes,varargs ArrayFactoryWithoutCastDemo.java
java ArrayFactoryWithoutCastDemo
```

Stdout:

```text
[Java, 17, Generics]
```

`String[]::new` là array constructor reference được compiler kiểm tra; helper không cần unchecked cast hoặc reflection.

Chọn `Class<T>` khi API cần runtime class token; chọn `IntFunction<T[]>` khi mục tiêu chỉ là cấp phát array và caller có thể cung cấp constructor reference. Không có lý do bắt buộc phải duy trì warning nếu một thiết kế typed đơn giản hơn diễn đạt được hợp đồng.

## 13. Class-key registry: dùng `Class.cast` thay vì suppression

Một kho lưu trữ nhiều kiểu có thể dùng field `Map<Class<?>, Object>` và method-level generics:

```java
<T> void put(Class<T> type, T value);
<T> T get(Class<T> type);
```

Ta không cần unchecked cast trong getter nếu dùng `type.cast`.

Tệp **`ClassTokenRegistryWithoutSuppressionDemo.java`**:

```java
import java.util.HashMap;
import java.util.Map;

public class ClassTokenRegistryWithoutSuppressionDemo {

    static final class Registry {
        private final Map<Class<?>, Object> values = new HashMap<>();

        <T> void put(Class<T> type, T value) {
            values.put(type, type.cast(value));
        }

        <T> T get(Class<T> type) {
            return type.cast(values.get(type));
        }
    }

    public static void main(String[] args) {
        Registry registry = new Registry();

        registry.put(String.class, "Hello");
        registry.put(Integer.class, 17);

        String greeting = registry.get(String.class);
        Integer version = registry.get(Integer.class);

        System.out.println(greeting);
        System.out.println(version);
        System.out.println(registry.get(Boolean.class) == null);
    }
}
```

Lệnh:

```bash
javac -Xlint:unchecked,rawtypes,varargs ClassTokenRegistryWithoutSuppressionDemo.java
java ClassTokenRegistryWithoutSuppressionDemo
```

Stdout:

```text
Hello
17
true
```

Lợi ích:

- `Map<Class<?>, Object>` là cấu trúc heterogeneous hợp lệ.
- `Class<T>` cùng `T` trong method signature giúp compiler kiểm tra caller.
- `type.cast` kiểm tra runtime class ở boundary.
- Không cần `@SuppressWarnings("unchecked")` ở getter.

Giới hạn: registry này không phân biệt `List<String>` và `List<Integer>` bởi chúng cùng runtime token `List.class`. Nếu dùng parameterized types làm key, cần mô hình `Type` và validation tương ứng chứ không chỉ thêm unchecked cast.

## 14. Raw types ở legacy boundary: chưa chắc cần suppress

Có thể một thư viện cũ trả raw `List`. Nếu ta chỉ muốn duyệt như các object không biết kiểu, method mới có thể tiếp nhận theo wildcard và **kiểm tra từng phần tử**.

Thay vì:

```java
// CỐ Ý không an toàn:
@SuppressWarnings("unchecked")
List<String> names = (List<String>) legacyList;
```

hãy cân nhắc:

```java
List<?> unknown = legacyList;
List<String> names = checkedCopy(unknown, String.class);
```

`legacyList` và `checkedCopy` ở đây là các biến/helper theo ngữ cảnh minh họa.

Việc chuyển raw list sang `List<?>` vẫn có thể là một điểm tiếp xúc với legacy types ở compile time, nhưng nội dung element type chưa được giả định là `String`. Sau đó adapter cung cấp runtime check.

Nếu phải duy trì một API raw vì tương thích nhị phân hoặc signature của framework, đặt phạm vi raw/unchecked boundary vào **một adapter có quyền truy cập hạn chế**, không lan truyền raw `List` qua nhiều lớp nghiệp vụ.

### Review lời giải thích an toàn

Không đủ để ghi “unchecked nhưng chúng tôi tin là List<String>”. Cần chỉ rõ **ai xây list**, **cách ngăn thêm phần tử sai loại**, **cách xử lý null**, và **thời gian object còn có thể bị sửa qua alias**.

## 15. `@SuppressWarnings("rawtypes")` chỉ che việc dùng raw type

Một ví dụ:

```java
@SuppressWarnings("rawtypes")
private List legacyItems;
```

Đây là field minh họa cần import `java.util.List` và đặt trong class. Annotation cho thấy raw type usage được người viết nhận biết, nhưng không nói gì về dữ liệu thực sự có trong field.

Nếu mục tiêu là list có element type chưa biết:

```java
private List<?> items;
```

thường mô tả chính xác hơn.

Nếu field luôn chứa chuỗi:

```java
private List<String> items;
```

mới là khai báo phù hợp với invariant.

Điểm quan trọng: `rawtypes` warning và `unchecked` warning thuộc **hai nhóm riêng**. Suppress raw type usage không đảm bảo những unchecked calls/conversions trong các method được ẩn; cách compiler báo từng vị trí phụ thuộc phạm vi annotation.

Không nên gắn `@SuppressWarnings({"rawtypes","unchecked"})` lên class chỉ để không còn dòng warning trong log. Hãy xác định từng hành vi và sửa từng ranh giới.

## 16. `@SafeVarargs` và `@SuppressWarnings("varargs")` có khác nhau?

Có. Chúng mang ý nghĩa và hợp đồng khác nhau.

- **`@SafeVarargs`** là annotation dành cho những variable-arity method/constructor hợp lệ mà người viết cam kết **không thực hiện thao tác có thể làm mất an toàn kiểu** trên varargs parameter.
- **`@SuppressWarnings("varargs")`** yêu cầu compiler bỏ qua một nhóm warnings liên quan, nhưng bản thân nó không chứa một lời hứa có ngữ nghĩa được thiết kế riêng như `@SafeVarargs`.
- **`@SuppressWarnings("unchecked")`** có thể che một số warnings ở generic varargs declarations/invocations, nhưng không chứng minh body an toàn.

Trong Java 17, `@SafeVarargs` được chấp nhận với **static**, **final** hoặc **private instance** varargs methods, và **constructors** theo quy tắc của ngôn ngữ. Không áp dụng tùy tiện cho public instance method có thể override.

Những varargs có component type **non-reifiable** như `List<String>...` phải được review kỹ vì runtime array component không thể chứa đầy đủ argument `<String>`.

Ngay cả khi một method đã gắn `@SafeVarargs`, `-Xlint:varargs` vẫn có thể nêu thao tác potentially unsafe mà compiler phát hiện trong thân method. Không nên cho rằng annotation chứng minh cả chương trình an toàn.

## 17. Ví dụ an toàn: chứng minh lời hứa `@SafeVarargs` tại chính method

Một method chỉ đọc những list được truyền qua generic varargs và tạo một list mới có thể có invariant đủ rõ để dùng `@SafeVarargs`:

```java
// Method đặt trong class có import List, ArrayList.
@SafeVarargs
static <T> List<T> concatenate(List<? extends T>... groups) {
    List<T> result = new ArrayList<>();
    for (List<? extends T> group : groups) {
        result.addAll(group);
    }
    return List.copyOf(result);
}
```

Ví dụ `concatenate(List.of("A"), List.of("B", "C"))` trả `[A, B, C]`; khi target là `List<Number>`, method có thể nhận cả nhóm Integer và Double. Phần thiết yếu của chứng minh không phải output mà là **không ghi vào slot mảng `groups`**, **không làm lộ alias mảng**, **không chuyển mảng tới helper có thể làm ô nhiễm**, và **chỉ sao chép các phần tử theo hợp đồng `T`**. `List.copyOf` còn yêu cầu phần tử không null.

Ví dụ chạy trọn vẹn và các nhóm compiler warnings khi có/không có `@SafeVarargs` đã được dạy trong bài lint. Bài này dùng chính implementation để giải thích vì sao suppression/annotation chỉ hợp lý khi có invariant có thể review. Ví dụ phản chứng tiếp theo cố ý phá invariant ấy.


## 18. Phản ví dụ: annotation hợp lệ cú pháp nhưng cam kết sai

Mẫu dưới đây **cố ý không an toàn**:

```java
@SafeVarargs
static void unsafe(List<String>... groups) {
    Object[] alias = groups;
    alias[0] = List.of(123);
}
```

Method minh họa cần nằm trong class và import `List`.

Điều nguy hiểm:

1. `List<String>... groups` được biểu diễn bởi một array runtime có component raw `List`.
2. `Object[] alias` trỏ tới cùng array.
3. `List.of(123)` là một `List<Integer>` nhưng vẫn phù hợp runtime raw component `List`.
4. Nếu caller sau đó đọc phần tử theo `String`, có thể gặp `ClassCastException`.

`@SafeVarargs` ở đây **không làm body an toàn**. Nó chỉ khiến lời hứa của người viết không khớp với implementation.

Nếu `-Xlint:varargs` phát hiện một unsafe alias/operation, cần sửa body hoặc thiết kế API. Nếu compiler không báo cho một đường dữ liệu phức tạp, đó cũng không phải bằng chứng về tính an toàn.

## 19. Ví dụ chạy được: heap pollution vẫn xảy ra khi cố tình suppress

Tệp **`UnsafeVarargsRuntimeDemo.java`**:

```java
import java.util.List;

public class UnsafeVarargsRuntimeDemo {

    @SafeVarargs
    static void unsafe(List<String>... groups) {
        Object[] alias = groups;
        alias[0] = List.of(100);

        try {
            String first = groups[0].get(0);
            System.out.println(first);
        } catch (ClassCastException ex) {
            System.out.println("ClassCastException");
        }
    }

    public static void main(String[] args) {
        unsafe(List.of("safe"));
    }
}
```

Biên dịch/chạy:

```bash
javac -Xlint:unchecked,rawtypes,varargs UnsafeVarargsRuntimeDemo.java
java UnsafeVarargsRuntimeDemo
```

Stdout **của chương trình**, nếu biên dịch thành công:

```text
ClassCastException
```

Compiler vẫn có thể báo warnings nhóm `varargs` với `-Xlint:varargs`, dù method đã có annotation. Diagnostic xuất hiện **trong bước `javac`**, không phải stdout của bước `java`.

Đừng biến ví dụ này thành một helper production. Mục đích là chứng minh: annotation không thay JVM array store checks và không bảo đảm generic element types được xác minh.

## 20. Một chú thích “Safety:” tốt cần chứa gì?

Một chú thích có ích không chỉ viết “safe” hoặc “đã test”. Nó nên nêu **mối quan hệ compiler không chứng minh được** và **lý do cụ thể mối quan hệ ấy luôn đúng**.

Ví dụ cho generic reflective array:

```java
// Safety: runtime component type của mảng được tạo trực tiếp
// từ chính Class<T> componentType đã kiểm tra. Không tạo alias
// cho phép ghi element sai kiểu trước khi trả kết quả.
@SuppressWarnings("unchecked")
T[] result = (T[]) Array.newInstance(componentType, length);
```

Đây là mảnh code minh họa, cần import `Array` và một ngữ cảnh có `T`, `componentType`, `length`.

Một chứng minh có thể được cấu trúc thành bốn phần:

| Phần | Câu hỏi phải trả lời |
|---|---|
| **Nguồn** | Dữ liệu hoặc token đến từ đâu? Caller có thể đưa thông tin sai qua unchecked code không? |
| **Invariant** | Điều gì luôn đúng, ví dụ “mọi phần tử đều qua `String.class.cast` trước khi lưu”? |
| **Bảo toàn** | Những thao tác/alias nào có thể phá invariant và đã được ngăn bằng cách nào? |
| **Thất bại** | Nếu vi phạm, API ném exception ở đâu và test nào xác nhận? |

Tính an toàn phải dựa trên logic chương trình, không dựa trên sự may mắn của input hiện tại. Các **comment “Safety:”** là tài liệu cho con người review, không phải từ khóa mà compiler tự hiểu.

## 21. Nên khoanh vùng suppression hẹp đến mức nào?

Một quy tắc review thực dụng là: **annotation chỉ nên bao phủ nơi tạo ra phép toán thực sự cần chứng minh**.

Mức nên ưu tiên:

1. **Local variable declaration** cho một unchecked cast đơn lẻ.
2. **Helper method private nhỏ** nếu nhiều bước tạo ra cùng một invariant chặt chẽ.
3. **Constructor/field** trong tình huống thực sự cần nhưng có cơ chế bảo vệ ownership.
4. **Class-level suppression** chỉ khi có một lý do bao phủ mọi warning của class và việc ẩn warning tương lai được chấp nhận có chủ đích.

Ví dụ:

```java
@SuppressWarnings("unchecked")
List<String> trusted = (List<String>) verified;
```

Nếu đây là một phương thức lớn, annotation ở biến tốt hơn:

```java
@SuppressWarnings("unchecked")
void processEverything() {
    // Hàng trăm dòng và nhiều operations khác...
}
```

vì dạng thứ hai có thể giấu các unchecked warnings **mới** ở những dòng không liên quan.

Đừng suppress chỉ vì CI báo đỏ. Nếu lựa chọn phạm vi quá rộng, ta sẽ đánh mất một phần chức năng kiểm soát regression của compiler.

## 22. Khi cần nhiều key, ghi đúng điều đang suppress

Ví dụ:

```java
@SuppressWarnings({"rawtypes", "unchecked"})
```

Dạng này không phải chỉ định một cảnh báo kết hợp “rawtypes+unchecked”; nó chỉ định **hai warning categories**.

Dùng cả hai chỉ khi thực sự có hai loại thao tác cần giữ. Khi sửa một raw type thành parameterized type, hãy rà soát và **xóa key suppression không còn cần thiết**.

Một kế hoạch cleanup:

1. Compile với các lint categories bật.
2. Loại bỏ annotation hoặc thu hẹp tạm thời trên một nhánh code.
3. Quan sát warning cụ thể xuất hiện.
4. Sửa generic signature, wildcard, checked adapter hoặc construction invariant.
5. Nếu vẫn cần suppression, chỉ giữ key tương ứng và chú thích rationale.
6. Giữ test bảo vệ điều kiện an toàn ấy.

Đừng dùng một chú thích rộng dạng `"all"` hay danh sách nhiều key không liên quan để “không còn compiler output”. Tập key compiler nhận biết tùy toolchain, và suppression rộng che giấu tín hiệu hữu ích.

## 23. Annotation không còn trong runtime API như một type guarantee

`@SuppressWarnings` có mục đích phục vụ compiler, không phải runtime type-checking API. Nó có **SOURCE retention** theo khai báo Java chuẩn.

Hệ quả:

- Không thể trông đợi JVM đọc annotation này để tự thực thi `Class.cast`.
- Không thể coi một field đã được annotate là một dữ liệu đáng tin hơn field không annotate.
- Không thể dùng reflection trên một object bất kỳ để suy ra dữ liệu generic bên trong đã được chứng minh an toàn vì compiler trước đây thấy annotation.
- Không thể dùng annotation này làm một loại schema validation khi nhận JSON, dữ liệu database hoặc plugin configuration.

Một thư viện cần runtime validation phải sử dụng **thao tác kiểm tra dữ liệu thực tế**, như `Class.cast`, kiểm tra từng phần tử, hoặc một decoder/schema có hợp đồng rõ.

Ngược lại, một static-analysis tool riêng có thể đọc mã nguồn và đưa ra chính sách review về suppression. Đó là quy trình công cụ phát triển, không phải tính năng runtime của annotation.

## 24. Kiểm thử phải bảo vệ invariant, không chỉ kiểm tra case thành công

Khi một helper chứa `@SuppressWarnings("unchecked")`, test nên đi qua các ranh giới có thể làm vỡ invariant.

Ví dụ với checked list adapter:

- Đầu vào hợp lệ chỉ chứa `String`.
- Đầu vào có một `Integer` ở vị trí đầu hoặc giữa.
- List rỗng.
- Phần tử `null` theo chính sách.
- Một reference khác sửa nguồn sau thời điểm copy.
- Runtime subtype hợp lệ, nếu class token cho phép.
- Dữ liệu lồng nhau, nếu hợp đồng thật sự hứa kiểm tra nested generics.

Không nên chỉ test:

```java
List<String> valid = List.of("A", "B");
```

rồi cho rằng một unchecked cast đã an toàn trong mọi trường hợp.

Tối thiểu, test phải xác minh **lỗi sai kiểu xuất hiện ở boundary được thiết kế**, thay vì âm thầm lưu dữ liệu sai rồi phát nổ ở một nơi khác nhiều bước sau.

### Tại sao cần cả lint và tests?

- Lint phát hiện cấu trúc mã mà compiler không chứng minh được.
- Runtime tests kiểm tra cách implementation xử lý những input được lựa chọn.
- Code review đánh giá invariant và các alias paths chưa được test.
- Không công cụ nào trong ba công cụ này một mình chứng minh toàn bộ chương trình không có lỗi.

## 25. Chương trình kiểm thử ranh giới không cần unchecked cast

Tệp **`BoundarySafetyRegressionDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class BoundarySafetyRegressionDemo {

    static <T> List<T> validated(
            List<?> input, Class<T> elementType) {

        List<T> result = new ArrayList<>();

        for (Object element : input) {
            result.add(elementType.cast(element));
        }

        return List.copyOf(result);
    }

    public static void main(String[] args) {
        System.out.println(
                validated(List.of("A", "B"), String.class));

        try {
            validated(List.of("A", 123), String.class);
        } catch (ClassCastException ex) {
            System.out.println("Invalid element rejected");
        }

        System.out.println(
                validated(List.of(), String.class).isEmpty());
    }
}
```

Biên dịch/chạy:

```bash
javac -Xlint:unchecked,rawtypes,varargs BoundarySafetyRegressionDemo.java
java BoundarySafetyRegressionDemo
```

Stdout:

```text
[A, B]
Invalid element rejected
true
```

Code này tránh unchecked cast, và test minh họa ba hướng: đầu vào đúng, đầu vào sai và rỗng.

Đối với code legacy không thể tránh suppression, hãy dùng **cùng tư duy test**, đồng thời thử mọi đường ghi có thể phá invariant sau khi giá trị được lưu, không chỉ đường đọc thông thường.

## 26. Phân biệt “warning đã ẩn” với “warning đã sửa”

Một hệ thống CI chỉ đếm số warnings có thể bị đánh lừa:

```text
Trước sửa: 3 unchecked warnings
Sau khi thêm @SuppressWarnings ở cấp class: 0 warnings
```

Số `0` ở ví dụ thứ hai không đồng nghĩa ba nguyên nhân đã được loại bỏ. Compiler chỉ không báo chúng trong phạm vi suppression.

Một review chất lượng nên trả lời:

- Có giảm số **unchecked operations thực** không?
- Có chuyển dữ liệu sang typed API hoặc kiểm tra runtime tại boundary không?
- Các suppression mới có ràng buộc rõ và test đi kèm không?
- Phạm vi annotation có thể ẩn warning mới của code sau này không?
- Những suppression cũ có thể xóa sau khi nâng cấp library/dependency không?

Khi quản lý nợ kỹ thuật, có thể thống kê cả **số lượng và vị trí suppressions** trong repository, không chỉ số warning của một lần compile. Một suppression là ngoại lệ cần bảo trì, không nên là tiêu chuẩn bình thường.

## 27. So sánh các hướng xử lý cùng một vấn đề

| Vấn đề | Cách không nên vội dùng | Cách ưu tiên |
|---|---|---|
| Raw `List` không rõ phần tử | Suppress `rawtypes` cho mọi caller | Đổi thành `List<?>` hoặc generic API rõ |
| Cast `Object` sang `List<String>` | Suppress `unchecked` tại nhiều nơi | Kiểm tra phần tử và tạo bản sao typed |
| Lấy value từ registry class-key | `(T) map.get(token)` và suppress | `token.cast(map.get(token))` |
| Cần `T[]` từ `Class<T>` | Cast ở mọi caller | Helper nhỏ dùng `Array.newInstance` có invariant |
| Caller biết array constructor | Reflection + unchecked cast | `IntFunction<T[]>` như `String[]::new` |
| Generic varargs an toàn thật | Suppress mọi varargs warnings | Review body, dùng `@SafeVarargs` khi hợp lệ |
| Generic varargs không an toàn | Thêm `@SafeVarargs` rồi bỏ qua | Bỏ alias/write hoặc nhận Collection thay varargs |
| Bắt warning mới trong CI | Tắt lint | Giữ lint bật và cân nhắc `-Werror` sau khi cleanup |
| Lo thiếu generic type ở runtime | Dùng unchecked cast và tin tưởng | Class token, Type schema, validation tùy nhu cầu |

Phần lớn thời gian, cách tốt hơn `@SuppressWarnings` là **biểu diễn đúng invariant trong API**. Chỉ những chỗ compiler thực sự không thể diễn đạt được invariant mới cần suppression có chủ đích.

## 28. Cẩn thận với reflection, `Type` và nested Generics

Nếu muốn kiểm tra một object có phải `String`, `String.class.cast(object)` là phương thức có runtime proof.

Nhưng nếu muốn kiểm tra `List<String>`, ta không thể lấy `List<String>.class`. `List.class` chỉ xác minh object thuộc `List`, không kiểm tra phần tử.

Một wrapper có thể dùng `java.lang.reflect.Type` hoặc `ParameterizedType` để **mô tả** cấu trúc `List<String>`, nhưng `Type` không cung cấp một phép cast tổng quát xác minh nội dung theo kiểu ấy.

Vì vậy đoạn:

```java
@SuppressWarnings("unchecked")
List<String> names = (List<String>) rawValue;
```

không thể được coi là an toàn chỉ vì chỗ khác có metadata “`List<String>`”.

Một framework có thể chứng minh bằng cách:

1. Duyệt từng phần tử và xác minh `String`.
2. Tạo bản sao được kiểm soát.
3. Ngăn raw aliases hoặc giữ object immutable.
4. Thỏa chính sách `null`.
5. Giải quyết generic structures sâu hơn bằng validator/schema tương ứng.

**Metadata generic không phải runtime validation.** Đây là ranh giới cần nói rõ trong mọi rationale của suppression liên quan reflection.

## 29. Generic arrays và tính nguy hiểm của alias sau cast

Ngay cả nếu một cast tới generic array được chứng minh tại thời điểm tạo object, tính an toàn về **nội dung** vẫn phụ thuộc những thao tác tiếp theo.

Ví dụ một array object có runtime component là raw `List` có thể nhận hai parameterizations khác nhau:

```java
List<?>[] groups = new List<?>[2];

Object[] alias = groups;
alias[0] = List.of("A");
alias[1] = List.of(123);
```

Đây là một đoạn Java hợp lệ trong method có import `List`.

Array này có thể chứa hai lists có element types khác nhau vì component `List<?>` chỉ yêu cầu một `List` object. Điều đó không sai nếu static type được giữ là `List<?>[]`.

Nguy cơ xuất hiện nếu ép array đó thành `List<String>[]` bằng unchecked cast rồi **tuyên bố mọi slot chỉ chứa List<String>**. Mảng runtime không cung cấp đủ bằng chứng.

Một lời giải thích “tôi đã tạo `new List<?>[n]`” **không đủ** để chứng minh cast sang `List<String>[]` an toàn. Phải kiểm soát nội dung, alias và đường cập nhật suốt vòng đời object.

Nếu nghiệp vụ không thực sự cần array, `List<List<String>>` hoặc một collection generic có API không để lộ array covariance thường rõ ràng hơn.

## 30. Review API không an toàn: ai có quyền ghi?

Giả sử method dùng suppression để tin rằng một `List<?>` chỉ chứa chuỗi. Ngay cả khi tại thời điểm check mọi phần tử đều là `String`, một reference khác tới **cùng mutable list** có thể chèn số sau đó.

Do đó, lời chứng minh phải xét **ownership**:

- Input do method sở hữu độc quyền hay caller giữ alias?
- Có một raw alias nào trong hệ thống không?
- Có thể mutate object sau khi validation không?
- Bản sao là mutable hay unmodifiable?
- Có thread khác cùng sửa collection không?
- Một iterator/view có mở ra đường ghi khác không?
- Kiểu phần tử có thể tự thay đổi trạng thái làm invalid một business invariant không?

`List.copyOf` giúp tạo một list unmodifiable ở cấp collection và loại bỏ alias đến backing mutable list theo hợp đồng snapshot phù hợp, nhưng nó không deep-copy các object phần tử. Nếu các phần tử mutable, trạng thái của chúng vẫn có thể thay đổi.

Dùng một unchecked cast không gắn với ownership policy có thể bị lỗi không phải ngay trong test đầu tiên, mà ở lần dùng thứ hai sau khi dữ liệu đã đổi.

## 31. `@SuppressWarnings` và cảnh báo tại chỗ gọi generic method

Không phải mọi warning đều nằm ngay trong helper. Một method generic varargs có element type non-reifiable có thể gây unchecked warning tại **lời gọi**, vì compiler phải tạo array trong variable-arity invocation.

Nếu helper thật sự an toàn, `@SafeVarargs` được thiết kế để diễn đạt cam kết ở **khai báo method** cho những nơi hợp lệ và ảnh hưởng tới các cảnh báo gắn với varargs invocation.

Đừng yêu cầu mọi caller thêm:

```java
@SuppressWarnings("unchecked")
```

để làm im hàng loạt warnings phát sinh từ một API generic varargs do chính thư viện kiểm soát. Hãy review và sửa API tại **nguồn**.

Tuy nhiên, `@SafeVarargs` không dành cho mọi trường hợp. Khi method có thể override, khi body có unsafe array escape, hoặc khi construction thật sự có rủi ro, nên ưu tiên một overload nhận `Collection`/`List` hoặc thiết kế khác không dựa vào generic array.

Một suppression ở caller chỉ được biện minh nếu chính caller đang thực hiện một thao tác có invariant riêng và không có cách thay thế hợp lý.

## 32. Khi nào suppression có thể chấp nhận trong thư viện?

Có thể chấp nhận khi đáp ứng đồng thời những tiêu chí sau:

1. API cần duy trì một hợp đồng chính đáng; compiler Java không thể thể hiện hoặc xác minh invariant đầy đủ.
2. Suppression liên quan **một thao tác cụ thể**, không phải một nhóm lỗi chưa điều tra.
3. Có lời giải thích dựa trên **type relationships, construction path, ownership hoặc validation**.
4. Input cần thiết được kiểm tra hoặc giới hạn rõ.
5. Không có alias nguy hiểm bỏ qua invariant.
6. Đầu ra có hợp đồng chính xác về `null`, mutability và exceptions.
7. Tests đi qua trường hợp thành công và những boundary failures quan trọng.
8. Phạm vi annotation nhỏ và có thể tìm/kiểm toán được.
9. Compiler vẫn chạy với lint flags cho phần mã còn lại.
10. Rationale được cập nhật khi API, compiler hoặc dependency thay đổi.

Nếu không thể đưa ra một invariant đủ chắc, **đừng dùng suppression để xuất API typed giả tạo**. Thà để API trả `Object`, `List<?>` hoặc một result type thể hiện sự không chắc chắn, rồi buộc caller thực hiện kiểm tra dữ liệu hợp lý.

## 33. Những lỗi phổ biến

**Lỗi 1 — Tưởng suppression làm cast an toàn.** Nó chỉ ẩn warning, không kiểm tra generic argument.

**Lỗi 2 — Dùng annotation để “sửa” compile error.** Lỗi kiểu rõ ràng như thêm `Integer` vào `List<String>` vẫn không biên dịch.

**Lỗi 3 — Gắn annotation lên class rất lớn.** Những warnings mới có thể bị ẩn ngoài ý muốn.

**Lỗi 4 — Suppress cả `unchecked` lẫn `rawtypes` khi chỉ có một nhóm cần thiết.** Annotation càng rộng càng khó bảo trì.

**Lỗi 5 — Ghi comment “compiler sai”.** Compiler không chứng minh được là khác với compiler sai; cần invariant cụ thể.

**Lỗi 6 — Không kiểm tra alias của collection hoặc array.** Dữ liệu có thể bị phá hợp đồng sau cast.

**Lỗi 7 — Coi test pass là chứng minh formal cho unchecked cast.** Test chỉ bao phủ input đã chọn.

**Lỗi 8 — Tin `List.class` xác minh `List<String>`.** Runtime Class token chỉ biết raw class.

**Lỗi 9 — Dùng `@SafeVarargs` như một phiên bản khác của `@SuppressWarnings("unchecked")`.** Nó mang cam kết an toàn riêng và chỉ hợp lệ ở một số declarations.

**Lỗi 10 — Không bật `-Xlint:varargs` khi review method đã gắn `@SafeVarargs`.** Compiler có thể chỉ ra potentially unsafe operations còn sót.

**Lỗi 11 — Suppress để lách raw type thay vì thay bằng `List<?>`.** Mất cơ hội dùng type system.

**Lỗi 12 — Cho rằng `-Werror` tự bảo vệ khỏi warnings đã suppress.** Chúng đã bị ẩn nên không thể chỉ dựa trên warning count.

**Lỗi 13 — Cho rằng `@SuppressWarnings` tồn tại như một runtime security policy.** Nó có mục đích compile-time.

**Lỗi 14 — Tạo `T[]` từ một `Class<T>` nhưng không xét primitive token.** Có thể tạo array runtime khác kỳ vọng tham chiếu.

**Lỗi 15 — Tạo unchecked `List<String>[]` từ `List<?>[]` rồi tuyên bố an toàn.** Runtime component raw `List` không xác minh element type.

**Lỗi 16 — Không ghi chính sách null khi kiểm tra list.** `Class.cast(null)` chấp nhận null, còn `List.copyOf` từ chối null.

**Lỗi 17 — Bỏ qua thread safety.** Kiểu đúng ở một thời điểm không bảo đảm một mutable container giữ invariant nếu concurrent code có thể thay đổi không kiểm soát.

**Lỗi 18 — Giữ suppression cũ sau khi đã sửa API.** Annotation không còn cần thiết vẫn có thể che warnings tương lai.

## 34. Bài tập tự kiểm tra và đáp án

### Bài 1 — Annotation có làm runtime cast an toàn?

```java
@SuppressWarnings("unchecked")
List<String> s = (List<String>) someObject;
```

**Đáp án:** Không. Nếu `someObject` là list chứa phần tử sai kiểu, runtime vẫn có thể ném `ClassCastException` khi đọc theo `String`.

### Bài 2 — Warning hay error?

```java
List<String> names = new ArrayList<>();
names.add(10);
```

**Đáp án:** Lỗi compile-time. `@SuppressWarnings("unchecked")` không hợp thức hóa lời gọi `add`.

### Bài 3 — Giới hạn phạm vi

Chỉ có một unchecked cast trong một method dài. Nên đặt suppression ở đâu?

**Đáp án:** Trên local variable declaration chứa phép cast, hoặc trong helper private nhỏ nếu cần cô lập thao tác.

### Bài 4 — Phân biệt lint keys

Raw `List` và unchecked conversion là cùng một warning key?

**Đáp án:** Không. Thường lần lượt thuộc `rawtypes` và `unchecked`.

### Bài 5 — Checked adapter

Muốn biến một `List<?>` nhận từ input sang `List<String>` có kiểm tra, cần làm gì?

**Đáp án:** Kiểm tra từng phần tử qua `String.class.cast`, rồi tạo một list typed mới phù hợp null/mutability contract.

### Bài 6 — Reflection array

Vì sao helper `Array.newInstance(componentType,n)` đôi khi phải có một unchecked cast sang `T[]`?

**Đáp án:** Reflection API trả `Object`, trong khi compiler không có cách chứng minh tĩnh runtime component class luôn tương ứng với type variable `T`. Cần construction invariant và suppression hẹp nếu dùng cast.

### Bài 7 — Varargs

`@SafeVarargs` có đủ để kết luận body an toàn không?

**Đáp án:** Không. Phải kiểm tra array alias, ghi sai slot, escape tới helper, và data flow.

### Bài 8 — `-Werror`

`javac -Xlint:unchecked -Werror ...` có phát hiện mọi bug Generics không?

**Đáp án:** Không. Nó khiến warnings phát hiện được ảnh hưởng kết quả compile; nó không thay runtime validation hay tests.

### Bài 9 — `List.class`

Có thể lấy `List<String>.class` và dùng `Class.cast` để chứng minh mọi phần tử là String không?

**Đáp án:** Không. `List<String>.class` không hợp lệ; `List.class` không kiểm tra type argument.

### Bài 10 — Null

`String.class.cast(null)` có ném exception không?

**Đáp án:** Không. Nó trả `null`; nếu API cấm null, phải kiểm tra riêng.

### Bài 11 — Invariant và alias

Một list đã được validate lúc tạo nhưng caller giữ raw mutable alias. Có đủ chứng minh nó luôn là `List<String>` không?

**Đáp án:** Không. Caller có thể thêm phần tử sai sau validation; cần ownership/copy hoặc chặn đường ghi.

### Bài 12 — Suppression không còn cần

Sau khi sửa helper để dùng `Class.cast` và không còn warning, nên làm gì với annotation cũ?

**Đáp án:** Xóa nó; annotation thừa che warnings mới trong tương lai.

### Bài 13 — Source retention

Có thể kỳ vọng runtime reflection đọc `@SuppressWarnings` như schema kiểm tra dữ liệu không?

**Đáp án:** Không. Annotation được dùng phục vụ compiler, không phải runtime validation contract.

### Bài 14 — Lời giải thích có giá trị

Comment “đã dùng trong nhiều năm và chưa lỗi” đủ lý do cho unchecked cast chưa?

**Đáp án:** Chưa. Cần giải thích nguồn dữ liệu, invariant được duy trì, những đường alias/mutation có thể có và cách kiểm thử.

## 35. Checklist review trước khi merge

1. Bật `javac -Xlint:unchecked,rawtypes,varargs` trên đúng JDK mục tiêu.
2. Xác định nguyên nhân từng warning; không chỉ đếm dòng diagnostic.
3. Tìm cách sửa type arguments/wildcards/generic signatures trước suppression.
4. Với dữ liệu không tin cậy, ưu tiên checked conversion và defensive copy.
5. Nếu giữ unchecked cast, nêu rõ class/element invariant phải đúng.
6. Kiểm tra raw aliases, mutable backing collection và thread boundaries.
7. Với mảng generic, kiểm tra runtime component class và mọi đường ghi array slot.
8. Với generic varargs, review alias, helper calls, returned/stored arrays.
9. Chỉ đặt `@SafeVarargs` khi vị trí hợp lệ và implementation thật sự an toàn.
10. Đặt `@SuppressWarnings` ở phạm vi declaration nhỏ nhất có thể.
11. Chỉ dùng các warning keys cần thiết; xóa key thừa.
12. Ghi rationale cụ thể thay vì comment “compiler không hiểu”.
13. Test input đúng, input sai, empty và null theo hợp đồng.
14. Test failure xảy ra ở boundary mong đợi, không quá muộn.
15. Review ownership và API có lộ backing map/list/array không.
16. Đảm bảo suppression không kéo dài qua code không liên quan.
17. Cân nhắc `-Werror` trên những module đã kiểm soát warning baseline.
18. Cập nhật rationale khi nâng cấp JDK, refactor hoặc đổi dependency.
19. Kiểm tra các suppression mới như một ngoại lệ trong code review.
20. Không coi build sạch warnings là bằng chứng đủ về type safety.

## 36. Tổng kết và tài liệu tham khảo

`@SuppressWarnings` là công cụ hợp lệ của Java, nhưng **chỉ nên dùng để che một warning đã được hiểu và chứng minh không gây mất an toàn trong hoàn cảnh cụ thể**. Với Generics, trường hợp điển hình là compiler không thể tự xác minh một invariant do type erasure, reflection hoặc legacy API. Annotation không thêm runtime checks và không sửa các lỗi type mismatch thật sự.

Cách làm đúng:

- **Ưu tiên typed API**: parameterized types, wildcards, generic methods và `Class.cast`.
- **Nếu phải unchecked cast**, khoanh vùng nhỏ, nêu construction/validation invariant và kiểm tra aliasing.
- **Nếu dùng `@SafeVarargs`**, chứng minh không tạo heap pollution dù có varargs array với non-reifiable component.
- **Luôn giữ lint và tests**: `-Xlint:unchecked,rawtypes,varargs`, review, tests đầu vào sai, và `-Werror` khi phù hợp.
- **Xem suppression là khoản nợ cần kiểm soát**, không phải tiêu chí cho việc đã sửa bug.

Nguồn chính thức để đối chiếu:

- [Java SE 17 — `SuppressWarnings`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/SuppressWarnings.html): định nghĩa annotation và tác dụng của suppression.
- [JDK 17 `javac` documentation](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html): `-Xlint`, `-Werror` và compiler options.
- [Java Language Specification §9.6.4.5 — `@SuppressWarnings`](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6.4.5): cú pháp và semantics của annotation.
- [Java Language Specification §4.8 — Raw Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.8): raw type compatibility.
- [Java Language Specification §4.12.2 — Variables of Reference Type](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.12.2): heap pollution.
- [Java Language Specification §4.7 — Reifiable Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.7): runtime type information.
- [Java Language Specification §9.6.4.7 — `@SafeVarargs`](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6.4.7): cam kết an toàn varargs.
- [Java SE 17 — `SafeVarargs`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/SafeVarargs.html): vị trí annotation hợp lệ.
- [Java SE 17 — `Class.cast`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#cast(java.lang.Object)): runtime class validation.
- [Java SE 17 — `Array.newInstance`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Array.html#newInstance(java.lang.Class,int)): tạo array theo runtime component class.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-testing-and-diagnostics-008">Chi phí boxing và vì sao generics không tự chuyên biệt hóa mã cho primitive</a>

<details>
<summary>Xem chi tiết</summary>

# Chi phí boxing và vì sao Java Generics không tự chuyên biệt hóa mã cho primitive

## Mục tiêu học tập

Trong Java, `List<Integer>` là hợp lệ nhưng `List<int>` không hợp lệ. Điều này có hệ quả trực tiếp đối với bộ nhớ, cấp phát và hiệu năng: khi một giá trị nguyên thủy như `int` đi vào cấu trúc generic cần **reference**, Java thường phải **boxing** nó thành một đối tượng wrapper `Integer`; khi dùng trở lại trong phép tính số học, Java thường **unboxing** về `int`.

Bài học này giải thích **cả hai mặt** của vấn đề:

1. **Ngôn ngữ và JVM:** Vì sao generics Java 17 chủ yếu hoạt động với reference types, tại sao erasure dùng `Object`/upper bound thay cho mỗi phiên bản `int`, và vì sao compiler không tự sinh một phiên bản thuật toán generic riêng cho từng primitive.
2. **Chi phí và kiểm thử:** Khi boxing/unboxing phát sinh về mặt ngữ nghĩa, rủi ro `NullPointerException` và identity, overhead bộ nhớ/GC có thể xuất hiện, khi nào cần `int[]` / `IntStream` / primitive functional interface, và cách đo hiệu năng mà không bịa con số.

Sau bài, bạn có thể:

- Phân biệt primitive `int` với reference `Integer`; phân biệt boxing, unboxing, widening và generic erasure.
- Viết được positive/negative compilation tests cho `List<Integer>`, `List<int>`, `Box<int>`, arithmetic trên `T extends Number`, `new T[]` và `T.class`.
- Chứng minh bằng `javap -s` rằng field/method của `Box<T>` sau erasure không trở thành field/method primitive cho `T=Integer`.
- Đọc một vòng lặp `for (int n : List<Integer>)` và nhận ra vị trí unboxing.
- Biết lý do dữ liệu có `null` có thể ném `NullPointerException` tại điểm unboxing.
- Hiểu giá trị số và **object identity** là hai khái niệm khác nhau đối với wrapper.
- So sánh `int[]`, `List<Integer>`, `IntStream`, `Stream<Integer>`, `IntUnaryOperator`, `OptionalInt` và các phương án thiết kế API.
- Phân biệt chi phí tiềm tàng do boxing với số liệu đo đã được kiểm chứng; tránh kết luận “mỗi boxing chắc chắn cấp phát một object” hoặc “IntStream luôn nhanh hơn”.

Các chương trình chạy hoàn chỉnh sử dụng **JDK 17 trở lên**. Mã đã được kiểm chứng với `javac --release 17 -Xlint:all -Werror`, kết quả stdout được ghi đúng theo lượt thử. Những đoạn cố tình không biên dịch được đánh dấu rõ; không đưa chúng vào cùng source set với chương trình hợp lệ.

## 1. Primitive và wrapper: hai kiểu dữ liệu khác nhau

Java có các primitive types:

| Primitive | Wrapper reference |
| --- | --- |
| `boolean` | `Boolean` |
| `byte` | `Byte` |
| `short` | `Short` |
| `char` | `Character` |
| `int` | `Integer` |
| `long` | `Long` |
| `float` | `Float` |
| `double` | `Double` |

`int` là một primitive value type của Java; `Integer` là một **class**, object reference có thể tham chiếu một wrapper instance hoặc `null`. Wrapper cung cấp API như `intValue()`, `parseInt(...)`, `compare(...)` và nhiều API khác.

Đọc sự khác biệt:

```java
int primitive = 42;
Integer wrapper = Integer.valueOf(42);
```

- Biến `primitive` chứa giá trị primitive `int` theo semantics của Java.
- Biến `wrapper` là một reference tới một `Integer` object; reference ấy có thể `null`.
- `Integer` đại diện cho một giá trị số nguyên, nhưng **không đồng nghĩa** kiểu khai báo là `int`.
- `Integer` là subtype của `Number` và `Object`. Primitive `int` **không** phải subtype của `Object` hoặc `Number`.

Một biểu thức gán:

```java
Integer x = 42;
int y = x;
```

được compiler chấp nhận nhờ conversion rules (boxing và unboxing), **không** vì `int` và `Integer` là cùng một kiểu generic hay một quan hệ thừa kế.

## 2. Boxing là gì?

**Boxing conversion** biến một giá trị primitive thành giá trị của wrapper tương ứng. Ví dụ:

```java
int value = 42;
Integer boxed = value; // Autoboxing.
```

Có thể hiểu về mặt ý nghĩa gần như:

```java
Integer boxed = Integer.valueOf(value);
```

Compiler thường chèn lời gọi wrapper factory thích hợp trong bytecode, chẳng hạn `Integer.valueOf(int)`. Đây là lời giải thích về semantics/bytecode thường gặp, không phải cam kết mọi lệnh boxing luôn cấp phát một object mới.

Wrapper factories có thể **cache** hoặc tái sử dụng object theo quy tắc triển khai và tiêu chuẩn. Trình tối ưu JIT cũng có thể loại bỏ những boxing objects không cần phải materialize ra heap khi chương trình giữ nguyên ngữ nghĩa quan sát được.

**Vì vậy:** đánh giá đúng là “boxing *có khả năng* gây allocation và tăng áp lực bộ nhớ/GC”, không phải “mỗi lần autoboxing chắc chắn tạo một object heap mới”.

### Những chỗ thường gặp boxing

```java
List<Integer> numbers = new ArrayList<>();
numbers.add(10);                // int -> Integer.
Integer result = 5 + 7;         // int expression -> Integer.
Object asObject = 12;           // int -> Integer -> Object.
Integer viaFactory = Integer.valueOf(13);
```

Đoạn trên cần imports `java.util.List` và `java.util.ArrayList`, đặt trong method hợp lệ. `List<Integer>` cần phần tử reference, vì thế `numbers.add(10)` phải chuyển đối số primitive thành wrapper compatible.

Khi viết `numbers.add(Integer.valueOf(10))`, boxing được biểu diễn tường minh. Nó **không** làm phần tử trong list trở thành primitive: kiểu element vẫn là `Integer`.

## 3. Unboxing là gì?

**Unboxing conversion** lấy giá trị primitive từ wrapper. Ví dụ:

```java
Integer boxed = 42;
int number = boxed; // Unboxing.
```

Một cách hiểu đơn giản là:

```java
int number = boxed.intValue();
```

Các phép tính primitive thường cần unboxing:

```java
Integer a = 3;
Integer b = 4;
int sum = a + b;
```

Ở đây cả hai wrappers được sử dụng như `int` trong phép `+`. Nếu muốn diễn đạt một phép toán generic hỗ trợ mọi `Number`, không thể giả định rằng `+` tự định nghĩa trên các generic type variables.

### Null unboxing: lỗi runtime thường bị bỏ qua

```java
Integer missing = null;
int value = missing; // Ném NullPointerException khi chạy.
```

Dòng gán **biên dịch được**: type checker thấy một `Integer` có thể unbox. Nhưng runtime reference là `null`, nên không thể gọi thao tác lấy primitive value.

Lỗi tương tự xảy ra trong `for (int n : listOfInteger)`, `Integer` arithmetic, hoặc `mapToInt(Integer::intValue)` nếu dữ liệu thực chứa `null`. Đây là **runtime test case**, không phải mẫu compiler phải từ chối.

## 4. Vì sao `List<int>` bị từ chối?

Một generic class trong Java 17 nhận **reference types** làm type arguments. `int` là primitive nên không phù hợp.

**Phải biên dịch:**

```java
import java.util.List;

class ValidListOfWrappers {
    List<Integer> numbers = List.of(1, 2, 3);
}
```

**Phải bị từ chối:**

```java
import java.util.List;

class InvalidPrimitiveTypeArgument {
    List<int> numbers;
}
```

Chạy riêng negative fixture:

```bash
javac --release 17 InvalidPrimitiveTypeArgument.java
```

Compiler trong lượt thử trả exit code khác 0 với chẩn đoán về loại `int` không phải một `reference` type argument. Không có chương trình hợp lệ để chạy từ fixture này.

Điều tương tự áp dụng:

```java
class Box<T> {}
class InvalidPrimitiveGenericUse {
    Box<int> value; // Compile-time error.
}
```

Java compiler **không** tự diễn giải `Box<int>` thành `Box<Integer>` chỉ vì autoboxing tồn tại ở expressions/method invocations. Hãy dùng `Box<Integer>`, hoặc nếu API yêu cầu dữ liệu số primitive hiệu quả thì viết/đưa vào một abstraction primitive riêng.

## 5. Generics được biên dịch bằng type erasure

Xét generic holder:

```java
static final class Box<T> {
    private final T value;
    Box(T value) { this.value = value; }
    T get() { return value; }
}
```

Với `T` không có explicit bound, erasure thông thường dùng `Object` tại các vị trí liên quan. Trong file class JVM:

- Field `T value` có descriptor `Ljava/lang/Object;`.
- Constructor `Box(T)` có descriptor `(Ljava/lang/Object;)V`.
- Method `T get()` có descriptor `()Ljava/lang/Object;`.

Compiler vẫn ghi generic `Signature` metadata khi thích hợp để công cụ hiểu khai báo `Box<T>`. Nhưng method descriptor ở mức JVM không trở thành `()I` chỉ vì có caller sử dụng `Box<Integer>`.

Ví dụ:

```java
Box<Integer> box = new Box<>(42);
Integer result = box.get();
```

`42` có thể được boxing để được truyền vào constructor nhận reference theo erasure. Khi caller lấy `Integer`, compiler có thể chèn một type cast hợp lý theo generic contract; nếu caller tiếp tục dùng `int`, có thể thêm unboxing.

**Hai bước khác nhau:**

1. **Erasure** giúp một generic implementation phục vụ nhiều reference type arguments.
2. **Boxing/unboxing** giúp một primitive đi qua vị trí yêu cầu wrapper reference.

Không nên gộp chúng thành một khái niệm. Type erasure không có nghĩa là compiler tự sinh những bản mã máy specialized cho `int` và `long`.

## 6. Vì sao không tự có một `Box<int>` riêng?

Trong Java 17:

- Type arguments của generic classes/methods không bao gồm primitive types.
- Các vị trí sử dụng một unbounded `T` ở mức JVM dựa trên `Object`; với bound khác, erasure dựa trên first bound thích hợp.
- Generic implementation thường **được chia sẻ** giữa `Box<String>` và `Box<Integer>`, không tự có một Java bytecode class `Box$int` do compiler tạo.
- Boxing giúp các giá trị primitive tham gia API chỉ nhận wrapper/reference types, nhưng tạo một **semantic conversion** chứ không tạo specialized generic class.

Nếu muốn field và method dùng **descriptor primitive `I`**, bạn cần code mang primitive kiểu đó thật sự, chẳng hạn:

```java
static final class IntBox {
    private final int value;
    IntBox(int value) { this.value = value; }
    int get() { return value; }
}
```

`Box<Integer>` và `IntBox` có thể cung cấp ý tưởng API giống nhau nhưng **khác representation và chữ ký bytecode**. Cái sau lưu field `int` và trả về `int`; cái trước phải tuân theo generic erasure/reference semantics của `Box<T>`.

Điều này không phải đánh giá “generic luôn xấu”: với thuật toán chủ yếu xử lý references, generics giúp tránh cast thủ công và giữ type safety tốt. Vấn đề boxing đáng cân nhắc khi thao tác **rất nhiều số primitive** trên đường xử lý nóng.

## 7. Chương trình A — Quan sát boxing, unboxing và erasure

Tạo tệp **`BoxingMechanicsDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class BoxingMechanicsDemo {
    static final class Box<T> {
        private final T value;
        Box(T value) { this.value = value; }
        T get() { return value; }
    }

    static final class IntBox {
        private final int value;
        IntBox(int value) { this.value = value; }
        int get() { return value; }
    }

    public static void main(String[] args) {
        int primitive = 42;
        Integer boxed = primitive;
        int unboxed = boxed;
        System.out.println("boxed type: "
                + boxed.getClass().getSimpleName());
        System.out.println("unboxed: " + unboxed);

        List<Integer> values = new ArrayList<>();
        for (int i = 1; i <= 3; i++) values.add(i);
        int sum = 0;
        for (int value : values) sum += value;
        System.out.println("generic list: " + values);
        System.out.println("generic sum: " + sum);

        Box<Integer> generic = new Box<>(42);
        IntBox specialized = new IntBox(42);
        System.out.println("generic holder: " + generic.get());
        System.out.println("primitive holder: " + specialized.get());

        Integer cachedA = 127;
        Integer cachedB = 127;
        System.out.println("small boxed identity: "
                + (cachedA == cachedB));
        System.out.println("numeric comparison: "
                + Integer.valueOf(1000)
                         .equals(Integer.valueOf(1000)));

        Integer missing = null;
        try {
            int impossible = missing;
            System.out.println(impossible);
        } catch (NullPointerException ex) {
            System.out.println("null unboxing: "
                    + ex.getClass().getSimpleName());
        }
    }
}
```

Lệnh:

```bash
javac --release 17 -Xlint:all -Werror BoxingMechanicsDemo.java
java BoxingMechanicsDemo
```

**Stdout đã xác minh:**

```text
boxed type: Integer
unboxed: 42
generic list: [1, 2, 3]
generic sum: 6
generic holder: 42
primitive holder: 42
small boxed identity: true
numeric comparison: true
null unboxing: NullPointerException
```

Chương trình biên dịch sạch dưới `-Xlint:all -Werror`.

### Diễn giải chính xác

- `Integer boxed = primitive`: conversion primitive → wrapper.
- `int unboxed = boxed`: wrapper → primitive.
- `values.add(i)`: một `int` được thêm vào `List<Integer>` nên boxing diễn ra theo ngữ nghĩa.
- `for (int value : values)`: khi duyệt `List<Integer>`, từng element reference cần được unbox để gán vào biến `int`.
- `Box<Integer>` không tự chuyển field `T` thành `int`. Nó là generic reference container.
- `IntBox` viết riêng dùng primitive field và primitive getter; so với `Box<Integer>`, khác ở representation.
- `cachedA == cachedB` với cùng hằng `127` cho `true` theo quy tắc boxed integer cache nhỏ; **không** dùng `==` để so sánh giá trị wrappers tổng quát.
- `Integer.valueOf(1000).equals(Integer.valueOf(1000))` so sánh **giá trị**, không yêu cầu hai references là cùng object.
- `missing` unboxing ném `NullPointerException` khi chạy.

Đừng đọc output trên thành benchmark. Chúng chỉ xác minh **behavior và các rủi ro type/runtime**, không đo số allocations hay nanoseconds.

## 8. Kiểm chứng bằng `javap`: reference so với primitive descriptors

Sau khi biên dịch file trên, kiểm tra hai nested classes:

```bash
javap -p -s 'BoxingMechanicsDemo$Box'
javap -p -s 'BoxingMechanicsDemo$IntBox'
```

Trên Windows PowerShell hoặc Bash, dấu nháy đơn quanh tên có `$` giúp tránh shell hiểu nhầm phần sau ký tự ấy.

**Kết quả rút gọn đã xác minh:**

```text
final class BoxingMechanicsDemo$Box<T> {
  private final T value;
    descriptor: Ljava/lang/Object;
  BoxingMechanicsDemo$Box(T);
    descriptor: (Ljava/lang/Object;)V
  T get();
    descriptor: ()Ljava/lang/Object;
}

final class BoxingMechanicsDemo$IntBox {
  private final int value;
    descriptor: I
  BoxingMechanicsDemo$IntBox(int);
    descriptor: (I)V
  int get();
    descriptor: ()I
}
```

Quan sát:

| Vị trí | `Box<T>` không bound | `IntBox` chuyên biệt thủ công |
| --- | --- | --- |
| Field | `Ljava/lang/Object;` | `I` |
| Constructor | `(Ljava/lang/Object;)V` | `(I)V` |
| Getter | `()Ljava/lang/Object;` | `()I` |

**Đây là bằng chứng về dạng mã JVM được phát sinh**, không phải kết quả đo mức độ tối ưu JIT hoặc bộ nhớ thực tế của một benchmark. JVM có thể tối ưu rất mạnh trong những ngữ cảnh cụ thể, nhưng Java compiler vẫn không làm `Box<Integer>` thành một class Java generic instantiation có primitive field.

Để xem call sites chứa boxing/unboxing, dùng:

```bash
javap -p -c BoxingMechanicsDemo
```

Tìm các phương thức như `Integer.valueOf`, `Integer.intValue` và các thao tác class generic/primitive. Bytecode là nơi dễ xác minh **conversion nào được biểu diễn**, còn việc JIT có xóa allocation nào thì cần công cụ khác.

## 9. Có bao nhiêu object được cấp phát khi boxing?

Đây là câu hỏi **không thể trả lời bằng một hằng số chung**.

Với:

```java
Integer a = 42;
Integer b = 42;
```

wrapper instances có thể được **tái sử dụng** theo quy tắc cache. Trên Java, các giá trị integer boxing nhỏ trong khoảng ít nhất `-128..127` có yêu cầu identity nhất định trong ngữ cảnh boxing được quy định; `Integer.valueOf` cũng có cache tối thiểu cho dải này.

Với:

```java
Integer x = Integer.valueOf(1000);
Integer y = Integer.valueOf(1000);
```

không nên viết test **đòi `x != y`** để chứng minh allocation. Triển khai có thể mở rộng cache hoặc JVM có thể tối ưu. Và ngay cả khi hai references khác nhau, vẫn không suy ra chi phí thời gian chính xác của toàn chương trình.

Ngoài caching, compiler/JIT có thể giảm hoặc loại bỏ wrapper allocations nếu object không escape và không có quan sát nào buộc phải materialize object. Ngược lại, lưu hàng triệu boxed values trong một collection có thể khiến nhiều wrapper objects cần được giữ lại.

**Kết luận chuẩn:** boxing có overhead *tiềm tàng* về CPU, memory footprint, object indirection và garbage collection; số overhead thực tế phụ thuộc dữ liệu, môi trường và tối ưu runtime. Chỉ kết luận số lượng allocations/latency bằng profiling hoặc benchmark phù hợp.

## 10. Chi phí bộ nhớ của `List<Integer>` so với `int[]`

Xét hai cấu trúc chứa N số:

```java
int[] primitiveNumbers = {1, 2, 3, 4};
List<Integer> wrappedNumbers = List.of(1, 2, 3, 4);
```

Ở mức khái niệm:

- `int[]` có một array object chứa các phần tử primitive `int` liên tiếp trong phần lưu trữ của array.
- `List<Integer>` cần một collection implementation và các **element references** tới các giá trị wrapper `Integer` (hoặc những objects được tái sử dụng). Cấu trúc backing cụ thể phụ thuộc implementation, không được suy rằng mọi `List` đều dùng `ArrayList`.
- Wrapper `Integer` là object reference value, có thể mang object header/alignment/đường truy cập gián tiếp, nếu thực sự materialize object.
- Số references và object allocations ảnh hưởng locality/cache behavior, áp lực lên garbage collector và số byte phải quản lý.

Do đó, với dữ liệu số lớn và xử lý lặp đi lặp lại, primitive array **thường** là lựa chọn tiết kiệm overhead representation hơn.

Tuy nhiên, **không gán một con số byte cố định cho mỗi `Integer` trong mọi JVM**. Nó phụ thuộc JDK, kiến trúc 32/64 bit, compressed references, object layout, alignment, collections, caching và escape analysis.

### Một cách ước lượng mang tính mô hình

Nếu N rất lớn:

```text
int[]:
  array header + N × kích thước phần tử int (về representation primitive)

Reference-backed List<Integer>:
  collection/backing structure
  + N × kích thước element reference
  + các wrapper objects thực sự cần tồn tại
  + metadata/overhead tương ứng
```

Mô hình này không phải phép đo trực tiếp. **Đừng** lấy đó làm lời hứa “List<Integer> luôn dùng X lần RAM của int[]”. Khi chọn cấu trúc, hãy profile thực tế, đo heap retained bytes/GC nếu cần, và cân nhắc các API mà phần còn lại của chương trình đang sử dụng.

## 11. Không thể dùng toán tử `+` với mọi `T extends Number`

Có người thử viết:

```java
class InvalidGenericArithmetic<T extends Number> {
    T add(T left, T right) {
        return left + right; // Compile-time error.
    }
}
```

Tệp này **cố ý không biên dịch**:

```bash
javac --release 17 InvalidGenericArithmetic.java
```

Kết quả kiểm chứng là nonzero exit code và diagnostic `bad operand types for binary operator '+'`.

**Vì sao?**

- Generic `T extends Number` chỉ bảo đảm type `T` có các member thừa kế từ `Number` (ví dụ `doubleValue()`).
- Nó không định nghĩa một toán tử static `+` trên mọi subtype `T` và không nói kiểu trả về phép cộng là cùng `T`.
- `Integer`, `Double`, `BigDecimal`, `BigInteger` có semantics số học khác nhau: overflow, precision, rounding, scale, phép chia, v.v.
- Java Generics không tự tạo một phép cộng `T + T` chỉ vì biết upper bound `Number`.

Có thể viết:

```java
static double sumAsDouble(List<? extends Number> source) {
    double sum = 0;
    for (Number n : source) sum += n.doubleValue();
    return sum;
}
```

Nhưng đây **không phải** một generic exact-number adder giữ nguyên subtype `T`. Việc ép số về `double` có thể làm mất precision với số lớn hoặc loại số có độ chính xác cao. Nếu yêu cầu tính toán chính xác trên `BigDecimal`, phải chọn API và quy tắc rounding đúng, không dựa vào `doubleValue()` tùy tiện.

## 12. Tránh boxing ở hot path bằng primitive arrays

Nếu workload chủ yếu là số học trên số `int` và không cần polymorphic collections, `int[]` là lựa chọn tự nhiên:

```java
int[] values = {1, 2, 3, 4};
int total = 0;
for (int value : values) {
    total += value;
}
```

Không có chuyển đổi `int -> Integer -> int` do generic collection tại các thao tác truy cập `values`. Các phần tử là primitive values.

### Nhưng còn điều gì cần kiểm tra?

- `int` arithmetic vẫn có thể **overflow**. Dùng `long` accumulation hoặc `Math.addExact` nếu muốn phát hiện overflow thay vì chấp nhận wraparound.
- Array có kích thước cố định, API thường ít tiện hơn collection đối với chèn/xóa/biến đổi cấu trúc.
- Array vẫn là object runtime và có bộ nhớ header; “primitive array” không có nghĩa là hoàn toàn không allocation.
- Nếu phải chuyển sang `List<Integer>` để tương thích API khác, boxing có thể lại xuất hiện tại ranh giới.
- Kiểm thử độ chính xác và overflow quan trọng không kém kiểm thử hiệu năng.

Không có một kiểu data structure “tốt nhất” cho mọi use case. Hãy chọn primitive array khi điều đó diễn đạt rõ **khối lượng dữ liệu số đồng nhất** mà API thực sự cần.

## 13. Primitive streams: `IntStream` khác `Stream<Integer>`

Thư viện chuẩn cung cấp các stream chuyên biệt:

- `IntStream` cho `int`.
- `LongStream` cho `long`.
- `DoubleStream` cho `double`.

Với primitive streams, các thao tác như `map(IntUnaryOperator)`, `sum()`, `average()`, `max()` và `forEach(IntConsumer)` được định nghĩa theo primitive values, **không yêu cầu mỗi phần tử đi qua một `Integer` trong API stream**.

Đối chiếu:

```java
int[] numbers = {1, 2, 3, 4};
int a = java.util.Arrays.stream(numbers).sum();
int b = java.util.stream.IntStream.of(numbers).sum();
```

Cả hai cách xử lý một sequence `int` và trả tổng `int`. Trái lại:

```java
List<Integer> boxed = List.of(1, 2, 3, 4);
int total = boxed.stream()
        .mapToInt(Integer::intValue)
        .sum();
```

`boxed.stream()` là `Stream<Integer>`. Chuyển sang `IntStream` bằng `mapToInt` có thao tác lấy primitive values khỏi các wrapper elements. Nếu collection có `null`, method reference sẽ gặp lỗi unboxing/dereference khi xử lý phần tử đó.

### Chuyển từ primitive stream sang generic stream

```java
List<Integer> list = java.util.stream.IntStream
        .rangeClosed(1, 4)
        .boxed()
        .toList();
```

`boxed()` yêu cầu tạo một **`Stream<Integer>`** ở API level. Việc chuyển từ primitive path sang reference path có thể làm mất ưu thế tránh boxing; thời gian/allocations thực tế cần được đo với workload.

Các bước `boxed()` / `mapToObj(...)` / đưa kết quả vào `List<Integer>` là **ranh giới chuyển biểu diễn** quan trọng khi phân tích performance.

## 14. Primitive functional interfaces: không dùng generic `Function<Integer,Integer>` khi chỉ cần `int -> int`

Một `Function<Integer,Integer>` biểu diễn phép biến đổi nhận/trả **reference wrapper types**. Nếu dữ liệu đầu vào/đầu ra vốn là primitive, thư viện có interface tương ứng:

| Nhu cầu | API generic wrapper | Primitive specialization |
| --- | --- | --- |
| Hàm `int -> int` | `Function<Integer,Integer>` | `IntUnaryOperator` |
| Nhận một `int` | `Consumer<Integer>` | `IntConsumer` |
| Test `int -> boolean` | `Predicate<Integer>` | `IntPredicate` |
| Sinh một `int` | `Supplier<Integer>` | `IntSupplier` |
| Đọc một số có thể vắng | `Optional<Integer>` | `OptionalInt` |
| Duyệt số `int` | `Stream<Integer>` | `IntStream` |

Lưu ý: đây là **những interface và classes chuyên biệt được thư viện viết/định nghĩa tường minh**, không phải việc compiler tự phát sinh `Function<int,int>`.

Ví dụ:

```java
java.util.function.IntUnaryOperator doubleIt = x -> x * 2;
int value = doubleIt.applyAsInt(7); // 14
```

`IntUnaryOperator.applyAsInt` dùng `int` ở chữ ký. So với `Function<Integer,Integer>.apply`, interface này không yêu cầu wrapper type ở tham số/return contract.

**Nhưng:** lambda allocation, virtual dispatch, inlining và optimization có thể ảnh hưởng hiệu năng tổng thể. “Primitive interface” giúp tránh wrapper representations ở biên API, chứ không hứa mọi lời gọi đều nhanh hơn trong mọi benchmark.

## 15. Chương trình B — So sánh primitive paths và các chuyển đổi

Tạo tệp **`PrimitiveAlternativesDemo.java`**:

```java
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PrimitiveAlternativesDemo {
    public static void main(String[] args) {
        int[] source = {1, 2, 3, 4};
        System.out.println("array sum: "
                + Arrays.stream(source).sum());
        System.out.println("int stream sum: "
                + IntStream.of(source).sum());
        System.out.println("squares sum: "
                + IntStream.of(source)
                           .map(x -> x * x).sum());

        List<Integer> boxed =
                IntStream.of(source).boxed().toList();
        System.out.println("boxed list: " + boxed);
        System.out.println("list sum: "
                + boxed.stream()
                       .mapToInt(Integer::intValue).sum());

        IntUnaryOperator doubleIt = x -> x * 2;
        System.out.println("primitive function: "
                + doubleIt.applyAsInt(7));

        OptionalInt maximum = IntStream.of(source).max();
        OptionalInt absent = IntStream.empty().max();
        System.out.println("max: " + maximum.orElseThrow());
        System.out.println("empty max present: "
                + absent.isPresent());

        try {
            Stream.of(1, null, 3)
                  .mapToInt(Integer::intValue).sum();
        } catch (NullPointerException ex) {
            System.out.println("null stream unboxing: "
                    + ex.getClass().getSimpleName());
        }
    }
}
```

Lệnh:

```bash
javac --release 17 -Xlint:all -Werror PrimitiveAlternativesDemo.java
java PrimitiveAlternativesDemo
```

**Stdout đã xác minh:**

```text
array sum: 10
int stream sum: 10
squares sum: 30
boxed list: [1, 2, 3, 4]
list sum: 10
primitive function: 14
max: 4
empty max present: false
null stream unboxing: NullPointerException
```

Khi xử lý `int[]` và `IntStream`, nhiều toán tử vận hành trực tiếp với `int`. Khi dùng `boxed()`, API kết quả trở thành `Stream<Integer>` và sau `toList()` là `List<Integer>`. `mapToInt` chuyển về primitive path.

### `OptionalInt` khác `Optional<Integer>` ra sao?

`OptionalInt` là một container chuyên biệt cho **một giá trị `int` có thể vắng**, tránh yêu cầu wrapper `Integer` trong API chứa value. `IntStream.max()` trả `OptionalInt` vì stream có thể rỗng. Gọi `orElseThrow()` với stream không rỗng sẽ lấy `int`; với stream rỗng nó có thể ném `NoSuchElementException`.

Không nên biến `OptionalInt` thành một phương án thay thế tổng quát cho tất cả `Optional<T>`. Nó dành cho primitive `int` và có API tương ứng, không chứa arbitrary reference type.

## 16. Những chuyển đổi boxing có thể bị che bởi cú pháp

Java có autoboxing/unboxing để code dễ đọc, nhưng mỗi vị trí chuyển đổi vẫn đáng quan sát khi debug:

```java
Integer objectNumber = 5;  // Boxing.
int plain = objectNumber;   // Unboxing.

List<Integer> values = new ArrayList<>();
values.add(plain);          // Boxing để lưu dưới Integer.

int accumulator = 0;
for (int value : values) {  // Unboxing từng phần tử.
    accumulator += value;
}
```

Không phải mọi phép `+` luôn unbox trong mọi ngữ cảnh. Ví dụ nối chuỗi có quy tắc chuyển đổi sang biểu diễn văn bản, và overload resolution có thể chọn method khác. Muốn biết conversion cụ thể, kiểm tra **kiểu tĩnh** của toán hạng, context của biểu thức và bytecode được compiler sinh ra.

Tuy nhiên, đừng tối ưu một đoạn chỉ bằng cách nhìn thấy `Integer.valueOf` trong bytecode. JIT có thể inline/eliminate nhiều chi phí; cần đo trên workload thực để xác định hot spot.

## 17. Primitive arrays và `List<Integer>`: khác semantics nữa, không chỉ performance

Khi thay một `List<Integer>` bằng `int[]`, bạn thay cả hợp đồng:

| Thuộc tính | `int[]` | `List<Integer>` |
| --- | --- | --- |
| Phần tử | `int` | `Integer` references |
| Có thể chứa null? | Không | Tùy implementation, có thể có |
| Kích thước | Cố định | Tùy implementation/API |
| Generic collection APIs | Không trực tiếp implement `List` | Có |
| Truy cập theo chỉ số | `a[i]` | `list.get(i)` |
| Runtime element type | Primitive component type `int` | Runtime generic argument bị erasure |
| Boxing khi lưu giá trị `int` | Không do element representation | Có conversion sang wrapper ở API |
| API stream chuyên biệt | `Arrays.stream(a)` → `IntStream` | `list.stream()` → `Stream<Integer>` |
| Dữ liệu số lớn | Thường ít overhead representation | Có thể chịu overhead wrappers/references |

Nếu nghiệp vụ cần **null** để biểu diễn “chưa có số”, `int[]` không tự mô tả trạng thái đó. Bạn cần thêm bitset/sentinel/schema hoặc dùng một reference representation. Không thay bằng primitive chỉ để giảm allocation rồi làm mất semantics của dữ liệu.

Nếu API cần chèn/xóa tùy ý và tích hợp nhiều collection utilities, một `List<Integer>` có thể thuận tiện hơn; việc chuyển đổi/trả thêm chi phí đôi khi là lựa chọn chấp nhận được.

## 18. Bridge method do erasure không phải primitive specialization

Khi subclass ghi đè một generic method đã được chuyên biệt hóa bằng **reference type**:

```java
class Parent<T> {
    T echo(T value) { return value; }
}
class StringChild extends Parent<String> {
    @Override String echo(String value) { return "v=" + value; }
}
```

`javac` có thể sinh một bridge để lời gọi theo descriptor đã xóa `echo(Object):Object` dispatch đến implementation `echo(String):String`. Bridge có nhiệm vụ bảo toàn overriding sau erasure, **không** cấp phát một class `Parent<int>` và không loại bỏ boxing khỏi `Parent<Integer>`.

## 19. Đối chiếu bridge với API primitive thật

Hai descriptor quan trọng trên class `StringChild` đã biên dịch là:

```text
echo:(Ljava/lang/String;)Ljava/lang/String;   // Implementation
echo:(Ljava/lang/Object;)Ljava/lang/Object;   // Bridge
```

Trong khi đó, một functional interface được **khai báo riêng cho primitive** thật sự có descriptor dùng `I`:

```java
java.util.function.IntUnaryOperator twice = x -> x * 2;
int result = twice.applyAsInt(7); // 14, method nhận/trả primitive int
```

`IntUnaryOperator.applyAsInt(int)` có descriptor `(I)I`, còn `Function<Integer,Integer>.apply` dùng reference-erased descriptor `(Ljava/lang/Object;)Ljava/lang/Object;`. Sự khác nhau đến từ **API primitive được thiết kế tường minh**, không phải compiler tự specialize một generic instantiation. Bài đối chiếu class file/bridge trong chương cung cấp chương trình Java 17 hoàn chỉnh, `javap -s/-v` và Reflection để tìm `ACC_BRIDGE`, `ACC_SYNTHETIC`; ở đây chỉ cần phân biệt đúng với boxing.


## 20. Các mẫu mã bắt buộc bị từ chối bởi compiler

Bên cạnh các chương trình dương, hãy kiểm tra mỗi luật bằng **một fixture âm độc lập**. Năm tệp dưới đây đã được thử bằng `javac --release 17`: tất cả đều trả exit code khác 0 và có compiler error về đúng quy tắc được nêu.

### 20.1. Không dùng primitive type argument

Tệp `InvalidPrimitiveTypeArgument.java`:

```java
import java.util.List;

class InvalidPrimitiveTypeArgument {
    List<int> values;
}
```

Kỳ vọng: **REJECTED** với chẩn đoán cần reference type nhưng gặp `int`.

### 20.2. Không tự tạo generic specialization cho primitive

Tệp `InvalidPrimitiveGenericBox.java`:

```java
class InvalidPrimitiveGenericBox<T> {}

class InvalidPrimitiveGenericUse {
    InvalidPrimitiveGenericBox<int> value;
}
```

Kỳ vọng: **REJECTED**. Cần dùng `InvalidPrimitiveGenericBox<Integer>` hoặc một class primitive-specialized riêng nếu đó là contract mong muốn.

### 20.3. Không giả định toán tử cộng trên `T extends Number`

Tệp `InvalidGenericArithmetic.java`:

```java
class InvalidGenericArithmetic<T extends Number> {
    T add(T left, T right) {
        return left + right;
    }
}
```

Kỳ vọng: **REJECTED** do toán tử `+` không áp dụng trực tiếp cho hai expressions kiểu `T`.

### 20.4. Không tạo array từ type variable tùy ý

Tệp `InvalidGenericArray.java`:

```java
class InvalidGenericArray<T> {
    T[] newItems(int size) {
        return new T[size];
    }
}
```

Kỳ vọng: **REJECTED** vì generic array creation. Dùng cấu trúc/thư viện phù hợp hoặc truyền array factory/token theo contract, chứ không tự tạo `T[]` khi element type chưa reifiable.

### 20.5. Không lấy `T.class` từ type variable

Tệp `InvalidTypeLiteral.java`:

```java
class InvalidTypeLiteral<T> {
    Class<?> token() {
        return T.class;
    }
}
```

Kỳ vọng: **REJECTED** với lỗi tương đương `cannot select from a type variable`. Cần truyền `Class<T>` token hoặc mô tả type thích hợp khi runtime type thông tin là một yêu cầu thực tế.

### Cách chạy một mẫu âm trong Bash

```bash
if javac --release 17 InvalidPrimitiveTypeArgument.java; then
    echo "FAIL: invalid code unexpectedly compiled"
    exit 1
else
    echo "PASS: invalid code rejected"
fi
```

Trong PowerShell 5.1:

```powershell
javac --release 17 InvalidPrimitiveTypeArgument.java
if ($LASTEXITCODE -eq 0) {
    throw "Invalid source unexpectedly compiled"
}
Write-Output "PASS: invalid source rejected"
```

Đừng gọi `java` trên một fixture mà compiler đã từ chối. Nếu thư mục có class files cũ, phải tránh chạy nhầm bản build cũ. Một negative compilation test thành công ở cấp **compiler exit status và diagnostic**, không phải stdout của một chương trình runtime.

## 21. Có thể dùng `T extends Number` để tránh boxing không?

Không. Một bound như:

```java
static <T extends Number> double sum(
        java.util.List<T> values) {
    double result = 0;
    for (T value : values) {
        result += value.doubleValue();
    }
    return result;
}
```

vẫn nhận **reference type arguments** trong Java 17. Nếu caller đưa `List<Integer>`, phần tử vẫn là `Integer` reference; bound chỉ cho phép dùng những member như `doubleValue()`.

Ở bytecode, erasure của `T extends Number` dùng bound `Number` tại vị trí tương ứng, không biến thành primitive `int`. Thêm nữa:

- `doubleValue()` không đồng nghĩa số học chính xác trên mọi subtype.
- `List<Double>` có thể chứa `null` nếu implementation cho phép; gọi `doubleValue()` trên null gây `NullPointerException`.
- Nếu muốn giữ exact numeric type `BigDecimal`, cần toán tử/API dành cho `BigDecimal` thay vì generic bound `Number`.

Bound cung cấp **khả năng gọi method của supertype**, không cung cấp specialized operators hoặc memory layout primitive.

## 22. Boxing trong Map keys, Optional và callback

Boxing không chỉ xuất hiện ở `List`:

```java
java.util.Map<Integer, String> names =
        new java.util.HashMap<>();
names.put(7, "seven"); // int được boxing thành Integer key.

java.util.Optional<Integer> boxedOption =
        java.util.Optional.of(5);
java.util.OptionalInt primitiveOption =
        java.util.OptionalInt.of(5);
```

Với `Map<Integer,V>`, key là reference `Integer`. Không tự giả định HashMap có primitive-specialized storage chỉ vì call site truyền literal `int`.

Nếu truy cập số liệu trên quy mô lớn, các primitive-specialized collections từ thư viện khác có thể cung cấp representations phù hợp, nhưng cần kiểm tra API, licenses, thread-safety, null/sentinel semantics và benchmarking của thư viện đã chọn. Không yêu cầu phải thêm thư viện ngoài chỉ để học generics.

Nếu code chỉ thao tác với một vài số lượng nhỏ, `List<Integer>` và `Map<Integer,V>` có thể đủ tốt, giúp API đơn giản và dễ bảo trì hơn so với các primitive-specific abstractions.

## 23. Giải quyết chi phí qua API design, không chỉ sửa một dòng code

Trước khi tối ưu, xác định **đường đi dữ liệu**:

```text
Nguồn số primitive int[]
        |
        v
   IntStream / int operators  --- primitive path --->
        |
        | boxed(), mapToObj(Integer::valueOf),
        | add vào List<Integer>, map key Integer...
        v
   Stream<Integer> / List<Integer> / generic APIs
        |
        | mapToInt(Integer::intValue),
        | iterator/for(int x : boxedList)...
        v
   int operations lần nữa
```

Điểm cắt giữa hai biểu diễn có thể có boxing/unboxing. Một giải pháp tốt thường tránh **lặp đi lặp lại** các conversions ở một đoạn phải xử lý lượng dữ liệu lớn.

Ví dụ: nếu input là `int[]` và đầu ra chỉ cần tổng, trả primitive `int` hoặc `long` thích hợp, thay vì xây `List<Integer>` trung gian không cần thiết.

Nếu phần còn lại của ứng dụng yêu cầu `List<Integer>`, đôi khi một lần boxing tại ranh giới rõ ràng là hợp lý. Đừng tự viết cả framework specialized chỉ để tránh một vài wrapper values không gây vấn đề measurable.

## 24. Benchmark thế nào mới đáng tin?

**Không có số “boxing chậm hơn X lần” chung cho mọi JDK.** Một benchmark nghiêm túc phải phân biệt:

1. **Allocation rate:** Có bao nhiêu objects thực sự materialize/escape, không chỉ số `Integer.valueOf` trong bytecode?
2. **Throughput/latency:** Tổng thời gian của workload đại diện, không phải thời gian của một vòng lặp quá ngắn.
3. **GC pressure:** Số collections/pause times trong tải thực.
4. **Retained heap memory:** Dữ liệu boxed được giữ trong collection có chiếm thêm bộ nhớ không?
5. **JIT effects:** Inlining, escape analysis, scalar replacement, vectorization và warmup có thể thay đổi kết quả.
6. **Data characteristics:** Kích thước input, range values (liên quan caching), số null, cấu trúc phân bố dữ liệu.
7. **Comparability:** Hai phiên bản phải tính **cùng kết quả** với cùng semantics như overflow, precision và xử lý null.

### Khuyến nghị về công cụ

Dùng profiler cho workload thật; khi cần microbenchmark JVM, ưu tiên **JMH** với warmup, nhiều forks, iterations, tiêu thụ kết quả hợp lệ để tránh JIT xóa công việc. Nếu đo object layout/heap, dùng công cụ phù hợp thay vì suy luận theo một con số cố định cho object header.

Không dùng một chương trình `System.nanoTime()` đo một lượt như bằng chứng thuyết phục rằng boxing luôn tốn số nano giây cụ thể.

### Một mẫu thiết kế benchmark hợp lý

So sánh ba đường đi với cùng số lượng dữ liệu và cùng toán:

- Vòng lặp `for` trên `int[]`.
- Vòng lặp `for` trên `List<Integer>` đã được tạo **trước phần đo**, nếu chỉ muốn đo truy cập/unboxing khi đọc.
- `IntStream` trên `int[]` và `Stream<Integer>.mapToInt` trên list, nếu muốn đo overhead pipeline stream.

Nếu muốn đo cả **chi phí tạo boxed collection**, tạo list bên trong workload được đo; nhưng **đừng** so điều ấy với một `int[]` đã tạo sẵn rồi cho rằng hai bài toán tương đương. Hãy tách chi phí **xây dựng dữ liệu** và **xử lý dữ liệu**.

Đầu ra benchmark phải được kiểm tra giống nhau trước khi công bố kết quả.

## 25. Những trường hợp tối ưu có thể phản tác dụng

- **Chuyển mọi List thành array:** có thể mất null semantics, flexibility, APIs, data ownership và tăng chi phí conversion ở các ranh giới khác.
- **Thay mọi stream bằng loop:** loop có thể nhanh trong một số workload nhưng stream có thể rõ hơn, và JVM tối ưu khác nhau; đừng kết luận thiếu dữ liệu.
- **Tin rằng boxed values luôn tạo objects mới:** cache, JIT optimization và reuse làm mệnh đề này sai.
- **Tin rằng boxed values không có overhead vì JVM tối ưu:** nếu references escape và phải được lưu giữ, overhead vẫn có thể đáng kể.
- **Dùng `==` để kiểm tra Integer equality:** object identity phụ thuộc cache/reuse và không phải so sánh giá trị. Dùng `equals` hoặc unbox có chủ đích.
- **Unbox giá trị có thể null mà không kiểm tra:** có thể phát sinh `NullPointerException` trong vòng lặp tính tổng hoặc `mapToInt`.
- **Dùng `doubleValue()` để cộng mọi `Number`:** có thể làm mất độ chính xác/semantics.
- **Dùng `Integer` thay `int` trong mọi hot loop chỉ vì generic:** API type-safety vẫn có thể giữ ở boundary, còn core numeric path có thể dùng primitives.
- **Dùng primitive array khi dữ liệu yêu cầu heterogeneous types:** mất hợp đồng nghiệp vụ và buộc viết logic bù trừ phức tạp.

Mục tiêu của tối ưu là giữ nguyên **tính đúng + semantics** và giảm một chi phí đã được xác minh, không chỉ làm code trông “ít generic hơn”.

## 26. Bài tập thực hành có đáp án

### Bài 1 — Type argument primitive

Có thể khai báo `List<int>` trên Java 17 không? Nếu không, cách dùng collection chuẩn là gì?

**Đáp án:** không. Generic type arguments cần reference types; dùng `List<Integer>`. Nếu muốn primitive representation, cân nhắc `int[]`, `IntStream` hoặc cấu trúc primitive chuyên biệt.

### Bài 2 — Boxing ở đâu?

```java
List<Integer> list = new java.util.ArrayList<>();
int x = 5;
list.add(x);
```

**Đáp án:** `x` là primitive `int`; method `List<Integer>.add` nhận `Integer` nên có boxing conversion.

### Bài 3 — Unboxing ở đâu?

```java
int sum = 0;
for (int n : list) sum += n;
```

Với `list: List<Integer>`, **đáp án:** mỗi element `Integer` cần unbox sang `int` để gán cho biến `n`. Giá trị null có thể gây `NullPointerException`.

### Bài 4 — Bound có làm toán tử cộng hoạt động?

```java
<T extends Number> T add(T a, T b) {
    return a + b;
}
```

**Đáp án:** không biên dịch. `Number` không khai báo operator `+` trả về `T`. Cần API toán học cụ thể hoặc một conversion/strategy tường minh với semantics đã chọn.

### Bài 5 — Identity của wrappers

`Integer a=127, b=127;` và `Integer x=1000, y=1000;`: có nên so `==` như kiểm tra numeric equality không?

**Đáp án:** không. `==` trên hai references là identity test; với số nhỏ có caching nhưng không được phụ thuộc vào identity cho mọi value. Dùng `a.equals(b)` hoặc các phép so sánh primitive có chủ đích.

### Bài 6 — `OptionalInt`

Khi tính `IntStream.empty().max()`, method trả gì?

**Đáp án:** một `OptionalInt` rỗng; `isPresent()` là `false`. Nó không tạo một `Integer` null để biểu diễn missing value.

### Bài 7 — Erasure descriptor

Vì sao `Box<T>.get()` có `()Ljava/lang/Object;` nhưng `IntBox.get()` có `()I`?

**Đáp án:** `T` unbounded erased thành `Object` trong generic bytecode; `IntBox` được khai báo tường minh dùng primitive `int` nên descriptor trả `int`.

### Bài 8 — Có tự động specialize `Box<Integer>` không?

**Đáp án:** không theo cách biến toàn bộ generic code thành `Box<int>` trên Java 17. `Box<Integer>` dùng cùng generic class representation tương ứng, có wrapper/conversions khi đi qua primitive boundary.

### Bài 9 — So sánh hai benchmark

Benchmark A tạo `List<Integer>` trong phần đo còn benchmark B đọc `int[]` tạo sẵn. Có thể nói chi phí runtime của hai kiểu phần tử đã được so công bằng không?

**Đáp án:** chưa. A bao gồm chi phí tạo cấu trúc còn B không. Phải định nghĩa workloads tương đương và tách creation khỏi processing.

### Bài 10 — Null runtime

`Stream.of(1,null,3).mapToInt(Integer::intValue).sum()` có bị compiler từ chối không?

**Đáp án:** không, mã compile. Nhưng khi pipeline xử lý `null`, gọi `intValue` gây `NullPointerException` tại runtime.

### Bài 11 — Bridge methods

Bridge `echo(Object):Object` do override `echo(String):String` là dấu hiệu compiler đã sinh primitive specialized version không?

**Đáp án:** không. Bridge giữ polymorphic dispatch sau generic erasure; không liên quan trực tiếp tới `int`-specialization.

### Bài 12 — Chọn representation theo mục tiêu

Một xử lý hàng triệu số nguyên chỉ cần tính tổng, output là `long`. Thiết kế nào đáng xem trước?

**Đáp án:** dùng `int[]`/primitive loop hoặc `IntStream` trên primitive source, cộng dồn vào `long` nếu cần hạn chế overflow so với `int`. Đo workload thực trước khi khẳng định cách nào nhanh hơn.

## 27. Checklist thiết kế và kiểm thử

- [ ] Biết biến nào là `int`, biến nào là `Integer`; không xem wrapper là primitive.
- [ ] Không dùng primitive `int` làm generic type argument của Java 17.
- [ ] Nhận ra conversion khi thêm primitive vào `List<Integer>` và lấy lại dưới dạng `int`.
- [ ] Có chính sách null rõ trước khi unbox wrappers hoặc gọi `Integer::intValue`.
- [ ] Không dùng `==` trên wrappers để kiểm tra numeric equality tổng quát.
- [ ] Phân biệt generic erasure `Object`/bound với primitive descriptors `I`, `J`, `D`.
- [ ] Không kỳ vọng `T extends Number` tự cung cấp `+` hoặc specialized primitive operators.
- [ ] Đã thử các negative source tests bằng compiler và không chạy chúng như runtime programs.
- [ ] Nếu cần tối ưu đường xử lý số, đã cân nhắc `int[]`/`IntStream`/`IntUnaryOperator`/`OptionalInt`.
- [ ] Không đưa boxing/`boxed()` vào các pipeline lớn nếu mục tiêu chỉ cần primitive output.
- [ ] Giữ đúng null semantics, overflow, precision và data ownership khi thay representation.
- [ ] Không khẳng định mỗi boxing luôn allocation hoặc tối ưu JIT xóa mọi boxing.
- [ ] Đo allocation, GC, retained heap và throughput trên workload/JDK thực.
- [ ] Không gọi một lần `System.nanoTime()` là benchmark đáng tin.
- [ ] Khi nghi ngờ, dùng `javap -p -s/-c` để kiểm tra descriptors và conversions trước khi kết luận.

## Kết luận

Trên Java 17, **generics không tự tạo primitive specialization**. `List<Integer>` và `Box<Integer>` vẫn là APIs reference-generic, còn `List<int>`/`Box<int>` bị compiler từ chối. Khi primitive values đi qua APIs wrapper, boxing/unboxing có thể xảy ra; null unboxing là một rủi ro runtime, và wrappers/references có thể tăng chi phí memory, CPU và GC trong các workloads lớn.

Thay vì tuyên bố generics “chậm”, hãy chọn **representation đúng vai trò**: generics cho type-safe object APIs, primitive arrays/streams/functions khi cần xử lý số nguyên thủy, và conversion ở những ranh giới thực sự cần. Đối chiếu erasure bằng `javap`, kiểm tra positive/negative compilation, rồi đo hiệu năng thật bằng profiler/JMH trước khi tối ưu. Đó là cách tránh cả **lỗi kiểu** lẫn những kết luận performance thiếu căn cứ.


</details>

- [Quay lại đầu trang](#back-to-top)
