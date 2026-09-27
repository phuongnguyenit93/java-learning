# PECS

Sau khi biết `? extends T` và `? super T`, vấn đề thực tế thường không còn là cú pháp mà là:

```text
"Lúc nào dùng extends?
Lúc nào dùng super?
Tại sao?"
```

Hãy nhìn một thao tác copy:

```java
static <T> void copy(
        List<? extends T> source,
        List<? super T> target) {
    for (T value : source) {
        target.add(value);
    }
}
```

`source` là nơi method **đọc T ra**. `target` là nơi method **ghi T vào**.

**PECS — Producer Extends, Consumer Super** — chỉ là một cách nhớ hướng đó:

```text
nguồn PRODUCE dữ liệu cho mình đọc  → ? extends T
đích CONSUME dữ liệu mình ghi vào   → ? super T
```

PECS không phải một feature mới của Java; nó là heuristic giúp đọc và thiết kế wildcard signature đúng hơn.

## <a id="pecs-rule">Quy tắc PECS</a>

Hãy hỏi parameter đang làm gì với giá trị kiểu `T`:

```text
parameter chủ yếu PRODUCE T cho method đọc
→ ? extends T

parameter chủ yếu CONSUME T từ method ghi vào
→ ? super T

parameter cần vừa đọc chính xác T vừa ghi T
→ thường dùng type chính xác, không wildcard
```

Ví dụ copy:

```java
static <T> void copy(
        List<? extends T> source,
        List<? super T> target) {
    for (T value : source) {
        target.add(value);
    }
}
```

## <a id="read-from-producer">Đọc từ Producer — extends</a>

`List<? extends Animal>` có thể là:

```java
List<Animal>
List<Dog>
List<Cat>
```

Method có thể đọc:

```java
Animal animal = source.get(0);
```

Vì mọi khả năng đều sinh ra ít nhất một `Animal`.

Nhưng không thể ghi một `Dog`:

```java
// source.add(new Dog()); // compile error
```

List thật có thể là `List<Cat>`.

Do đó “extends = read-only” chỉ là cách nhớ gần đúng. API vẫn có thể thực hiện operation không phụ thuộc element type như `clear()` hoặc remove theo index. Điều bị hạn chế là **ghi một giá trị element cụ thể** khi type chính xác chưa biết.

## <a id="write-to-consumer">Ghi vào Consumer — super</a>

`List<? super Dog>` có thể là:

```java
List<Dog>
List<Animal>
List<Object>
```

Ghi `Dog` là an toàn:

```java
target.add(new Dog());
```

Khi đọc, chỉ có thể chắc chắn:

```java
Object value = target.get(0);
```

Vì list có thể chứa các giá trị khác nếu type thật là `Animal` hoặc `Object`.

## <a id="pecs-api-design">Áp dụng PECS trong API Design</a>

Signature hẹp:

```java
static void moveDogs(List<Dog> source, List<Dog> target)
```

chỉ dùng được khi cả hai phía chính xác là `List<Dog>`.

Signature linh hoạt:

```java
static void moveDogs(
        List<? extends Dog> source,
        List<? super Dog> target)
```

cho phép source chứa subtype của `Dog` và target là container của `Dog` hoặc supertype.

JDK dùng pattern này rộng rãi. Ví dụ tư duy của các API như `Collections.copy` là source produce dữ liệu còn destination consume dữ liệu.

Không nên áp PECS máy móc:

- nếu parameter cần cả đọc và ghi cùng một `T`, exact `List<T>` có thể rõ hơn;
- nếu wildcard làm signature khó hiểu nhưng không tăng khả năng tái sử dụng, không cần thêm;
- named type parameter thích hợp khi return type phải liên hệ trực tiếp với input type.

Đặc biệt, thường **tránh wildcard ở return type public** nếu có thể trả về một type cụ thể hơn. Return type như `List<? extends Animal>` buộc caller tiếp tục mang unknown type và xử lý wildcard ở phía của họ. Wildcard thường hữu ích nhất ở **input boundary**, nơi API muốn chấp nhận nhiều parameterized type hợp lệ.

PECS giải quyết flexibility, nhưng câu hỏi sâu hơn vẫn còn: **vì sao ta cần wildcard để tạo flexibility thay vì để `List<Dog>` tự là subtype của `List<Animal>`?** Chương sau trả lời bằng invariance.
