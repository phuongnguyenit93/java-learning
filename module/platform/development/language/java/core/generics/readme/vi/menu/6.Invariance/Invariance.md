# Tính bất biến của Generic Type

Đây là một trong những chỗ dễ gây khó chịu nhất khi mới học Generics:

```java
Dog dog = new Dog();
Animal animal = dog; // hoàn toàn hợp lệ

List<Dog> dogs = new ArrayList<>();
// List<Animal> animals = dogs; // tại sao lại compile error?
```

Nếu chỉ nhìn quan hệ `Dog extends Animal`, việc compiler từ chối có vẻ vô lý.

Nhưng `List<Animal>` không chỉ **đọc** Animal; nó còn được phép **ghi bất kỳ Animal nào vào**. Nếu một `List<Dog>` có thể giả làm `List<Animal>`, code khác có thể nhét một `Cat` hoặc `Animal` thường vào danh sách vốn hứa rằng mọi phần tử đều là `Dog`.

Java vì thế làm generic type **invariant theo mặc định**:

```text
Dog là subtype của Animal
không kéo theo
List<Dog> là subtype của List<Animal>
```

Wildcard là công cụ để mở lại đúng phần linh hoạt mà API thật sự cần mà không phá guarantee này.

## <a id="generic-declaration-subtyping">Generic Type vẫn có Inheritance</a>

“Generics invariant” **không có nghĩa generic class/interface mất quan hệ kế thừa**.

Nếu declaration có inheritance:

```java
interface Container<T> {}

class ArrayContainer<T> implements Container<T> {}
```

thì với **cùng type argument**, quan hệ subtype vẫn được giữ:

```java
ArrayContainer<String> child = new ArrayContainer<>();
Container<String> parent = child; // OK
```

Điều invariance ngăn là tự tạo subtype relationship chỉ vì **type argument** có quan hệ:

```java
Dog <: Animal

nhưng

Container<Dog> </: Container<Animal>
```

Hai câu phải được giữ cùng lúc:

```text
declaration inheritance vẫn hoạt động
+
type-argument inheritance không tự được truyền qua generic type
```

## <a id="generic-invariance">Generic Invariance</a>

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

Nếu cần một view để đọc `Animal` từ list các subtype:

```java
List<? extends Animal> animals = dogs;
```

Nếu cần một target có thể nhận `Dog`:

```java
List<? super Dog> target = new ArrayList<Animal>();
```

Wildcard cung cấp **use-site variance** có kiểm soát thay vì biến generic type thành covariant toàn cục.

## <a id="variance-vs-arrays">Generics và Array Covariance</a>

Array trong Java là covariant:

```java
Integer[] integers = {1, 2};
Number[] numbers = integers; // compile được
```

Nhưng runtime phải kiểm tra khi ghi:

```java
numbers[0] = 3.14; // ArrayStoreException
```

Array giữ component type ở runtime nên JVM có thể phát hiện việc ghi sai kiểu.

Generics chọn hướng khác:

```java
List<Integer> integers = new ArrayList<>();
// List<Number> numbers = integers; // compile error
```

Type system chặn quan hệ nguy hiểm sớm, thay vì cho phép rồi dựa vào runtime store check.

## <a id="variance-safety">Vì sao Invariance bảo vệ Type Safety?</a>

Giả sử `List<Dog>` có thể gán cho `List<Animal>`:

```java
List<Dog> dogs = new ArrayList<>();
List<Animal> animals = dogs; // giả định
animals.add(new Animal());

Dog dog = dogs.get(0); // guarantee đã bị phá
```

Vấn đề xuất hiện vì `List` vừa **produce** vừa **consume** element. Covariance toàn cục sẽ làm thao tác ghi không an toàn.

Mô hình:

```text
exact generic type
→ invariant mặc định

? extends T
→ view thiên về producer

? super T
→ view thiên về consumer
```

Invariance không phải hạn chế tùy ý; nó là nền để compiler giữ consistency của mutable generic structures.

Tiếp theo ta xem một con đường có thể **bỏ qua** các guarantee này vì lý do tương thích với code Java cũ: raw types.
