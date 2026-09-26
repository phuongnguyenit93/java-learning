# Casting

Casting là việc yêu cầu Java xem/chuyển một value qua một type khác trong phạm vi language các quy tắc. Primitive casting và reference casting có mô hình tư duy khác nhau.

## <a id="primitive-casting">Casting Primitive</a>

**Widening primitive chuyển đổi** thường chuyển sang type có phạm vi biểu diễn rộng hơn và thường không cần cast explicit:

```java
int x = 10;
long y = x;
```

**Narrowing chuyển đổi** có thể mất thông tin và thường cần cast:

```java
long x = 1000L;
int y = (int) x;
```

Nếu value không fit target phạm vi, bit/value có thể bị truncate/wrap theo chuyển đổi các quy tắc; cast không tự kiểm tra “an toàn theo domain”.

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

## <a id="instanceof-safe-cast">instanceof và Safe Cast</a>

`instanceof` cho phép kiểm tra runtime type trước khi downcast. Pattern matching còn có thể gộp check và binding:

```java
if (animal instanceof Dog dog) {
    dog.bark();
}
```

Tuy nhiên nếu mã phải `instanceof` liên tục để chọn hành vi theo subtype, hãy kiểm tra lại abstraction/polymorphism design.

## <a id="class-cast-failure">ClassCastException</a>

Nếu reference trỏ tới object không tương thích target type, downcast fail ở runtime:

```java
Animal animal = new Cat();
Dog dog = (Dog) animal; // ClassCastException
```

Compiler chỉ biết relationship giữa các type; runtime check mới biết object thực tế.

chương tiếp theo chuyển từ type chuyển đổi sang **control flow: điều gì được chạy tiếp theo?**
