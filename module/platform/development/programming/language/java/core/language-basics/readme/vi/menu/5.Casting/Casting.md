# Casting

Casting là việc yêu cầu Java chuyển hoặc nhìn một value qua type khác trong phạm vi các quy tắc của ngôn ngữ. Primitive casting và reference casting có mô hình tư duy khác nhau.

## <a id="primitive-casting">Casting Primitive</a>

**Widening primitive conversion** thường chuyển sang type có phạm vi biểu diễn rộng hơn và thường không cần explicit cast:

```java
int x = 10;
long y = x;
```

**Narrowing chuyển đổi** có thể mất thông tin và thường cần cast:

```java
long x = 1000L;
int y = (int) x;
```

Nếu value không nằm trong phạm vi target type, bit/value có thể bị truncate hoặc wrap theo quy tắc conversion; cast không tự kiểm tra “an toàn theo domain”.

### Widening không đồng nghĩa luôn giữ nguyên precision

Nhiều widening conversion không cần explicit cast vì target type biểu diễn được phạm vi rộng hơn theo language rule, nhưng điều đó không có nghĩa mọi value đều giữ precision tuyệt đối:

```java
long exact = 9_007_199_254_740_993L;
double approximate = exact;
```

`long -> double` là widening conversion, nhưng `double` không thể biểu diễn chính xác mọi `long` lớn. Vì vậy "widening" là khái niệm type-conversion, không phải guarantee domain-level về precision.

### Narrowing có thể đổi value

```java
int large = 130;
byte small = (byte) large; // -126
```

Cast nói với compiler rằng conversion được phép; nó không tự validate range. Nếu range có ý nghĩa business, hãy kiểm tra trước hoặc dùng API kiểm tra overflow/range phù hợp.

### Constant expression là một ngoại lệ hữu ích

```java
byte a = 100;       // compile: constant value fit byte
int x = 100;
// byte b = x;      // không compile dù runtime x đang là 100
```

Compiler có thể chứng minh compile-time constant, nhưng không giả định arbitrary variable luôn nằm trong range.

## <a id="reference-upcast-downcast">Upcast và Downcast Reference</a>

Upcast từ subtype lên supertype thường implicit:

```java
Dog dog = new Dog();
Animal animal = dog;
```

Object không thay đổi; chỉ static type của reference trở nên tổng quát hơn.

Downcast cần explicit cast:

```java
Dog dogAgain = (Dog) animal;
```

Cast hợp lệ ở compile time chưa đảm bảo runtime object thật sự thuộc target type.

### Cast reference không biến object thành type khác

```java
Animal animal = new Dog();
Dog dog = (Dog) animal;
```

Không có object mới và object `Dog` không bị "convert". Cast chỉ yêu cầu Java kiểm tra rằng runtime object có thể được nhìn qua target reference type.

`null` có thể cast sang reference type mà không gây `ClassCastException`:

```java
Dog dog = (Dog) null; // hợp lệ, dog == null
```

Lỗi chỉ xuất hiện khi dereference `dog` hoặc khi cast một non-null object không tương thích.

## <a id="instanceof-safe-cast">instanceof và Safe Cast</a>

`instanceof` cho phép kiểm tra runtime type trước khi downcast. Pattern matching còn có thể gộp check và binding:

```java
if (animal instanceof Dog dog) {
    dog.bark();
}
```

Tuy nhiên nếu mã phải `instanceof` liên tục để chọn hành vi theo subtype, hãy kiểm tra lại abstraction/polymorphism design.

`instanceof` với `null` luôn cho `false`, vì `null` không phải instance của class nào:

```java
Object value = null;
System.out.println(value instanceof String); // false
```

Pattern variable chỉ tồn tại trong region mà compiler biết check đã thành công, giúp tránh cast lặp lại và giảm mismatch giữa check/cast.

### Pattern matching không chỉ thay thế explicit cast

Điểm quan trọng hơn cú pháp ngắn là compiler liên kết **type test + binding + control flow**:

```java
if (animal instanceof Dog dog && dog.isReady()) {
    dog.bark();
}
```

`dog` dùng được ở vế phải của `&&` vì vế đó chỉ chạy sau khi `animal instanceof Dog dog` thành công.

Ngược lại, pattern variable không thể dùng ở nơi compiler không guarantee pattern đã match. Chi tiết flow scope được nối với chapter Variables & Scope; ở đây mental model cần giữ là:

```text
runtime type test succeeds
        ↓
compiler allows typed binding
        ↓
binding exists only where success is guaranteed
```

## <a id="class-cast-failure">ClassCastException</a>

Nếu reference trỏ tới object không tương thích target type, downcast fail ở runtime:

```java
Animal animal = new Cat();
Dog dog = (Dog) animal; // ClassCastException
```

Compiler chỉ biết relationship giữa các type; runtime check mới biết object thực tế.

Mental model:

```text
compile time
→ cast có hợp lệ về mặt type relationship không?

runtime
→ object thật có tương thích target type không?
```

Nếu bạn đang dùng cast để "ép cho compile", đó thường là dấu hiệu cần dừng lại kiểm tra model. Cast nên diễn đạt knowledge thật sự về type relationship, không phải che lỗi thiết kế.

chương tiếp theo chuyển từ type chuyển đổi sang **control flow: điều gì được chạy tiếp theo?**
