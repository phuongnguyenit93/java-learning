# Tính bất biến và quan hệ kiểu con

Đây là một trong những chỗ dễ gây khó chịu nhất khi mới học Generics. Ta đã biết:

```java
Dog dog = new Dog();
Animal animal = dog; // hoàn toàn hợp lệ
```

`Dog` là subtype của `Animal`. Câu hỏi mới là: **quan hệ đó có tự động truyền qua một kiểu generic hay không?**

Java vì thế làm kiểu generic **bất biến (invariant) theo mặc định**:

```text
Dog là subtype của Animal
không kéo theo
Box<Dog> là subtype của Box<Animal>
```

Wildcard là công cụ sẽ được học ở chương sau để mở lại đúng phần linh hoạt mà API thật sự cần mà không phá đảm bảo này.

## <a id="generic-invariance-intro">Từ quan hệ kiểu con đến Invariance</a>

Giả sử:

```java
class Animal {}
class Dog extends Animal {}
```

`Dog` là subtype của `Animal`, nhưng:

```java
Box<Dog> dogs = new Box<>();
// Box<Animal> animals = dogs; // compile error
```

`Box<Dog>` **không** tự động là subtype của `Box<Animal>`.

Lý do trực giác:

```java
Box<Animal> animals = dogs; // giả sử được phép
animals.set(new Animal());  // hợp lệ theo Box<Animal>
```

Khi đó một `Box<Dog>` lại có thể chứa `Animal` không phải `Dog`, phá vỡ đảm bảo ban đầu.

Đây là **tính bất biến (invariance)**. Chương Wildcard tiếp theo sẽ cung cấp cách diễn tả quan hệ linh hoạt hơn mà không phá an toàn kiểu.

## <a id="generic-declaration-subtyping">Kiểu generic vẫn giữ quan hệ kế thừa</a>

“Kiểu generic là invariant” **không có nghĩa class/interface generic mất quan hệ kế thừa đã khai báo**.

Nếu chính khai báo generic có quan hệ kế thừa:

```java
interface Container<T> {}

class ArrayContainer<T> implements Container<T> {}
```

thì với **cùng type argument**, quan hệ kiểu con vẫn được giữ:

```java
ArrayContainer<String> child = new ArrayContainer<>();
Container<String> parent = child; // OK
```

Điều tính bất biến ngăn là tự tạo quan hệ kiểu con chỉ vì **type argument** có quan hệ:

```java
Dog <: Animal

nhưng

Container<Dog> </: Container<Animal>
```

Hai câu phải được giữ cùng lúc:

```text
quan hệ kế thừa của khai báo vẫn hoạt động
+
quan hệ kiểu con của type argument không tự được truyền qua kiểu generic
```

### MÔ HÌNH TƯ DUY TỐI THIỂU VỀ `List<E>` DÙNG TỪ ĐÂY TRỞ ĐI

Module Collection nằm ngay sau Generics, nên ở đây **không học cách triển khai hay hiệu năng của `List`**. Chỉ cần coi:

```text
List<E>
→ một cấu trúc chứa nhiều giá trị kiểu E

get(...)
→ đọc một E ra

add(E)
→ ghi một E vào

size()
→ lấy số phần tử
```

Từ đây, các ví dụ Invariance/Wildcard/PECS chỉ dùng đúng mô hình đọc/ghi tối thiểu này.

## <a id="generic-invariance">Tính bất biến của kiểu generic</a>

Quy tắc vừa thấy với `Box<T>` áp dụng tương tự cho `List<T>`:

```java
class Animal {}
class Dog extends Animal {}

List<Dog> dogs = new ArrayList<>();
// List<Animal> animals = dogs; // compile error
```

Dù `Dog <: Animal`, không có quan hệ:

```text
List<Dog> <: List<Animal>
```

Đây là **cùng một quy tắc invariance**, chỉ được đặt vào `List` để chuẩn bị cho Wildcard/PECS ở chương sau.

## <a id="variance-vs-arrays">Generics và tính hiệp biến của mảng</a>

Mảng trong Java là hiệp biến (covariant):

```java
Integer[] integers = {1, 2};
Number[] numbers = integers; // compile được
```

Nhưng JVM phải kiểm tra kiểu khi ghi ở lúc chạy:

```java
numbers[0] = 3.14; // ArrayStoreException
```

Mảng giữ kiểu phần tử (component type) ở lúc chạy nên JVM có thể phát hiện việc ghi sai kiểu.

Generics chọn hướng khác:

```java
List<Integer> integers = new ArrayList<>();
// List<Number> numbers = integers; // compile error
```

Hệ thống kiểu chặn quan hệ nguy hiểm ngay khi biên dịch, thay vì cho phép rồi dựa vào kiểm tra lúc ghi ở runtime.

## <a id="variance-safety">Vì sao tính bất biến bảo vệ an toàn kiểu?</a>

Giả sử `List<Dog>` có thể gán cho `List<Animal>`:

```java
List<Dog> dogs = new ArrayList<>();
List<Animal> animals = dogs; // giả định
animals.add(new Animal());

Dog dog = dogs.get(0); // đảm bảo kiểu đã bị phá
```

Vấn đề xuất hiện vì `List` vừa **cung cấp** vừa **tiếp nhận** phần tử. Nếu mọi kiểu generic đều hiệp biến thì thao tác ghi sẽ không còn an toàn.

Ví dụ mutable `List` cho thấy rất rõ **vì sao covariance tự động có thể nguy hiểm**, nhưng quy tắc của Java còn rộng hơn: generic class/interface là invariant theo mặc định ngay cả khi một API cụ thể chỉ đọc dữ liệu. Java không tự phân tích từng declaration để suy ra variance từ các method của nó; nếu cần linh hoạt ở nơi sử dụng, wildcard sẽ diễn tả điều đó một cách tường minh.

Vì vậy invariance không phải hạn chế tùy ý; nó ngăn Java tự suy diễn các quan hệ subtype giữa những parameterized type chỉ từ quan hệ của type argument.

Tiếp theo ta dùng chính vấn đề vừa thấy để trả lời câu hỏi: **làm sao chấp nhận một họ kiểu generic liên quan mà vẫn không phá an toàn kiểu?** Đó là vai trò của wildcard.
