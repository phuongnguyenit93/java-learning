# Giới hạn của Generics và thiết kế API

Chương này thuộc **lượt học sâu** nhiều hơn là cú pháp cần dùng mỗi ngày. Người mới nên biết các giới hạn tồn tại và hiểu nguyên nhân chính; không cần học thuộc từng trường hợp biên trước khi bắt đầu sử dụng Generics.

Sau khi hiểu `T`, wildcard và erasure, một loạt câu hỏi thường xuất hiện:

```java
// List<int> numbers;             // tại sao không được?
// T value = new T();             // tại sao không được?
// T[] values = new T[10];        // tại sao không được?
// Object value; value instanceof List<String> // tại sao không được?
```

Nếu chỉ học từng dòng như một “luật cấm”, Generics rất dễ biến thành một đống quy tắc phải học thuộc.

Chương này gom chúng lại theo **nguyên nhân**.

Nhiều giới hạn có thể hiểu từ sự kết hợp của các yếu tố sau, nhưng không phải mọi quy tắc đều có đúng một nguyên nhân duy nhất:

```text
Generics chủ yếu được kiểm tra khi biên dịch
        +
type erasure làm mất một phần danh tính kiểu generic ở runtime
        +
một số cơ chế của JVM cần biết kiểu thật ở runtime
```

Khi các ý này đã rõ, nhiều giới hạn như dùng kiểu nguyên thủy làm type argument, `new T()`, mảng generic hay một số giới hạn runtime sẽ bớt “vô lý”. Những quy tắc khác, như phạm vi của type parameter trong ngữ cảnh `static` hoặc giới hạn với `Throwable`, còn phụ thuộc vào chính mô hình kiểu và quy tắc ngôn ngữ Java.

## <a id="no-generic-primitives">Không dùng kiểu nguyên thủy làm đối số kiểu</a>

Không hợp lệ:

```java
// List<int> numbers;
// Box<double> value;
```

Type argument phải là kiểu tham chiếu (reference type):

```java
List<Integer> numbers = new ArrayList<>();
Box<Double> value = new Box<>();
```

Autoboxing giúp cú pháp sử dụng thuận tiện:

```java
numbers.add(1); // int -> Integer
```

Nhưng wrapper vẫn là object, nên có khác biệt về cấp phát bộ nhớ, định danh, khả năng nhận `null` và chi phí so với primitive. Generics không biến `List<Integer>` thành một `int[]` chuyên biệt cho primitive.

## <a id="no-new-type-parameter">Không thể new T trực tiếp</a>

Không hợp lệ:

```java
static <T> T create() {
    // return new T();
}
```

Sau erasure, `T` không mang một class cụ thể đủ để JVM thực hiện `new T()`. Ngoài ra, khai báo `<T>` cũng không hề yêu cầu type argument phải có một constructor cụ thể.

Mã generic phải nhận **chiến lược tạo object từ bên ngoài** thay vì tự đoán constructor. Ta có thể diễn tả ý tưởng chỉ bằng những kiến thức đã học:

```java
interface Factory<T> {
    T create();
}

static <T> T create(Factory<T> factory) {
    return factory.create();
}
```

Bên gọi quyết định cách tạo `T`; phương thức generic chỉ giữ quan hệ kiểu. Những cách ngắn hơn bằng lambda hoặc cách dựa vào `Class<T>`/Reflection thuộc các module sau.

## <a id="generic-array-limit">Giới hạn của mảng Generic</a>

Không thể tạo:

```java
// T[] values = new T[10];
// List<String>[] lists = new List<String>[10];
```

Mảng là reified và hiệp biến (covariant): runtime biết kiểu phần tử và kiểm tra khi ghi. Parameterized type như `List<String>` lại non-reifiable, nên JVM không thể thực hiện đầy đủ phép kiểm tra mà hợp đồng của mảng yêu cầu.

`List<?>[]` có thể tạo vì `List<?>` là reifiable:

```java
List<?>[] lists = new List<?>[10];
```

Trong code thực tế, sau khi học module Collection, bạn sẽ thường thấy collection được ưu tiên hơn mảng generic khi kích thước động và API không bắt buộc dùng mảng. Nếu tối ưu nội bộ hoặc API bắt buộc dùng mảng, unchecked cast phải được cô lập và điều kiện bất biến phải rất rõ.

## <a id="runtime-type-information-limit">Giới hạn của thông tin kiểu lúc chạy</a>

Erasure giới hạn những câu hỏi có thể trả lời trực tiếp ở runtime về `T` hoặc một type argument cụ thể.

Ví dụ, không thể dùng type parameter như một class literal:

```java
static <T> void inspect() {
    // Class<?> type = T.class; // compile error
}
```

Và với một tham chiếu quá rộng, runtime không thể xác minh tùy ý type argument cụ thể:

```java
Object value = new ArrayList<String>();
// value instanceof List<String> // compile error
```

Tương tự, không thể kiểm tra trực tiếp `instanceof T` vì `T` không còn là một danh tính class độc lập ở runtime sau erasure:

```java
// if (value instanceof T) { } // compile error
```

Nhưng từ Java 16, một số parameterized `instanceof` vẫn hợp lệ khi kiểu tĩnh cho phép phép ép kiểu được kiểm tra, như `List<Integer> → ArrayList<Integer>`. Giới hạn thật sự không phải “mọi parameterized type đều bị cấm với instanceof”, mà là runtime **không thể tự xác minh tùy ý một type argument cụ thể đã bị erasure**.

Khi mã generic thực sự cần một runtime type token, API thường nhận nó từ bên gọi, chẳng hạn `Class<T>`, thay vì cố suy ra từ `T`.

## <a id="static-type-parameter-limit">Ngữ cảnh static và tham số kiểu của lớp</a>

```java
class Box<T> {
    // static T cached; // compile error
}
```

`static` member chỉ tồn tại một lần cho class, trong khi `Box<String>`, `Box<Integer>`... không tạo các class runtime riêng có trạng thái static riêng cho từng type argument.

Phương thức static vẫn có thể tự generic:

```java
class Box<T> {
    static <U> U identity(U value) {
        return value;
    }
}
```

`U` thuộc phương thức, không thuộc parameterization của `Box`.

## <a id="erasure-signature-clash">Xung đột chữ ký sau xóa kiểu</a>

Không thể overload chỉ dựa vào generic arguments nếu các chữ ký sau erasure giống nhau:

```java
// void process(List<String> values) {}
// void process(List<Integer> values) {} // name clash sau erasure
```

## <a id="generic-exception-limit">Giới hạn của Generics với Exception</a>

Không thể tạo subclass generic của `Throwable`:

```java
// class Problem<T> extends Exception {} // compile error
```

Không thể dùng type parameter làm kiểu trong `catch`:

```java
// catch (T ex) { ... }
```

Nhưng type parameter có thể xuất hiện trong `throws` khi bound phù hợp:

```java
static <E extends Exception> void run(ThrowingTask<E> task) throws E {
    task.run();
}

interface ThrowingTask<E extends Exception> {
    void run() throws E;
}
```

Điều này cho phép API truyền thông tin kiểu của checked exception qua hợp đồng lúc biên dịch mà không tạo một class exception generic.

## <a id="generic-api-design">Thiết kế API Generic</a>

Generic type nên biểu diễn **một quan hệ kiểu có ý nghĩa**, không chỉ thay `Object` một cách máy móc.

Ví dụ repository đơn giản:

```java
interface Repository<ID, T> {
    T findById(ID id);
    void save(T value);
}
```

Chữ ký cho bên gọi biết:

- kiểu id là gì;
- repository đọc/trả về kiểu gì;
- giá trị nào được phép ghi.

Một số nguyên tắc thực tế:

- dùng type parameter khi nhiều member cần chia sẻ cùng một danh tính kiểu;
- tránh trả `Object` nếu API thật sự biết kiểu;
- giữ số type parameter vừa đủ để chữ ký còn đọc được;
- nếu bên gọi chỉ cần “một kiểu chưa biết nào đó”, wildcard thường hợp lý hơn việc thêm một type parameter không tạo quan hệ nào;
- đừng dùng raw type để “cho ngắn”.

Có thể tổng hợp lựa chọn thiết kế bằng bảng sau:

| Nhu cầu của API | Công cụ thường phù hợp |
| --- | --- |
| Nhiều vị trí phải giữ **cùng một kiểu** | Type parameter có tên như `<T>` |
| `T` phải cung cấp một khả năng tối thiểu | Bounded type parameter, ví dụ `<T extends Number>` |
| Chỉ cần chấp nhận “một kiểu nào đó” và không cần đặt tên lại | Wildcard `?` |
| Chủ yếu đọc `T` từ tham số | `? extends T` |
| Chủ yếu ghi `T` vào tham số | `? super T` |
| Cần vừa đọc vừa ghi chính xác cùng `T` | Kiểu chính xác như `List<T>` thường rõ hơn wildcard |
| Phải đi qua API cũ dùng raw type | Cô lập ở một ranh giới nhỏ, kiểm tra/chuyển đổi rồi quay lại mã có kiểu rõ ràng |
| Cần tạo object hoặc biết class thật ở runtime | Nhận factory, `Class<T>` hoặc một type token phù hợp từ bên gọi |

Điểm quan trọng là không chọn cú pháp theo độ “nâng cao”. Hãy bắt đầu từ **quan hệ kiểu mà API thực sự cần biểu diễn**, sau đó dùng cơ chế đơn giản nhất vẫn giữ được quan hệ đó và an toàn kiểu.

### Tổng kết mô hình Generics

```text
Generic Type / Method
→ tạo quan hệ kiểu tái sử dụng được

Bounds
→ yêu cầu khả năng tối thiểu

Invariance
→ ngăn suy diễn subtype không an toàn giữa các parameterized type; đặc biệt quan trọng với cấu trúc mutable

Wildcards + PECS
→ mở rộng ranh giới API theo hướng đọc/ghi

Raw Types
→ lối tương thích với mã cũ nhưng dễ làm suy yếu an toàn kiểu

Type Erasure
→ nền triển khai và nguồn của nhiều giới hạn
```

Khi thiết kế API generic, mục tiêu không phải dùng cú pháp phức tạp nhất mà là **diễn đạt đúng quan hệ kiểu để compiler bảo vệ bên gọi và phần triển khai càng sớm càng tốt**.
