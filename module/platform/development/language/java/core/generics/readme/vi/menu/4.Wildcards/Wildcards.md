# Wildcards

Đến đây ta đã biết `List<String>` và `List<Integer>` là hai parameterized type khác nhau. Bây giờ xuất hiện một nhu cầu mới:

```text
"Method này không quan tâm List chứa chính xác kiểu gì.
Nó chỉ cần làm một việc áp dụng được cho mọi List."
```

Ví dụ method chỉ cần lấy kích thước:

```java
static int sizeOf(List<?> values) {
    return values.size();
}
```

Ta không cần đặt tên element type là `T`, vì method không cần liên kết kiểu đó với parameter hay return value nào khác.

**Wildcard `?`** có thể hiểu là: “ở đây có một type argument cụ thể, nhưng đoạn code này không cần biết chính xác nó là kiểu nào”.

Wildcard tồn tại để làm **API boundary linh hoạt hơn** mà vẫn giữ generic type safety. Các dạng `? extends ...` và `? super ...` tiếp tục nói rõ unknown type đó nằm ở phía nào của một type hierarchy.

### MENTAL MODEL TỐI THIỂU VỀ `List<E>` DÙNG TRONG MODULE NÀY

Module Collection nằm ngay sau Generics, nên ở đây **không học implementation hay performance của List**. Chỉ cần coi:

```text
List<E>
→ một container chứa nhiều giá trị kiểu E

get(...)
→ đọc một E ra

add(E)
→ ghi một E vào

size()
→ lấy số phần tử
```

Những ví dụ wildcard/PECS/invariance phía sau chỉ dùng đúng mental model đọc/ghi đơn giản này.

## <a id="unbounded-wildcard">Unbounded Wildcard — ?</a>

```java
static int sizeOf(List<?> values) {
    return values.size();
}
```

`List<?>` nghĩa là “một `List` của kiểu nào đó, nhưng method này không biết kiểu đó là gì”.

Nó khác raw `List`:

- `List<?>` vẫn giữ generic type safety;
- raw `List` bỏ qua phần lớn kiểm tra generic và có thể sinh unchecked warning.

Từ `List<?>`, đọc element an toàn dưới dạng `Object`:

```java
Object value = values.get(0);
```

Không thể thêm một object cụ thể vì compiler không biết element type thật:

```java
// values.add("x"); // compile error
```

`null` là ngoại lệ về mặt type system, nhưng việc thêm `null` hiếm khi là lý do thiết kế một API wildcard.

## <a id="wildcard-vs-object">List&lt;?&gt; khác List&lt;Object&gt; thế nào?</a>

Đây là nhầm lẫn rất phổ biến:

```java
List<?> unknown;
List<Object> objects;
```

`List<Object>` có nghĩa **element type chính xác là Object**. Vì vậy có thể thêm `String`, `Integer`, `User`... vì tất cả đều là subtype của `Object`:

```java
objects.add("java");
objects.add(123);
```

`List<?>` lại có nghĩa **element type chính xác tồn tại nhưng mình không biết nó là gì**. Variable này có thể tham chiếu `List<String>`, `List<Integer>`, `List<User>`...

```java
List<String> names = new ArrayList<>();
List<?> unknown = names; // OK
```

Do compiler không biết unknown type thật là gì, ta không thể thêm một concrete value tùy ý:

```java
// unknown.add("java"); // compile error
```

Mental model:

```text
List<Object>
→ biết chính xác element type = Object

List<?>
→ biết đây là List của MỘT type nào đó
→ nhưng không biết type đó là gì
```

## <a id="wildcard-placement">Wildcard được dùng ở đâu?</a>

Wildcard là **type argument ở nơi sử dụng**, không phải cách khai báo một type parameter mới.

Các dạng sau là hợp lệ:

```java
List<?> values;
List<? extends Number> numbers;
List<? super Integer> targets;
```

Nhưng không thể khai báo generic class bằng wildcard:

```java
// class Box<?> { } // compile error
```

Và không thể dùng wildcard trực tiếp làm type argument của object đang được tạo:

```java
// new ArrayList<?>(); // compile error
```

Tương tự, wildcard không được dùng làm type argument trực tiếp trong `extends` / `implements` của declaration:

```java
// class MyList extends ArrayList<?> { } // compile error
```

Mental model:

```text
<T>
→ khai báo một type variable có tên

?
→ mô tả một type argument chưa biết ở use site
```

## <a id="extends-wildcard">Upper-Bounded Wildcard — ? extends</a>

```java
static double sum(List<? extends Number> values) {
    double total = 0;
    for (Number value : values) {
        total += value.doubleValue();
    }
    return total;
}
```

Method chấp nhận:

```java
List<Integer>
List<Double>
List<BigDecimal>
```

Ta biết element là **một subtype chưa biết của `Number`**, nên đọc ra dưới dạng `Number` là an toàn.

Nhưng không thể:

```java
// values.add(1);   // compile error
// values.add(2.5); // compile error
```

Vì list thật có thể là `List<Double>`; thêm `Integer` sẽ phá type safety.

## <a id="super-wildcard">Lower-Bounded Wildcard — ? super</a>

```java
static void addDefaults(List<? super Integer> target) {
    target.add(1);
    target.add(2);
}
```

Method có thể nhận `List<Integer>`, `List<Number>` hoặc `List<Object>`.

Ta được phép ghi `Integer` vì mọi khả năng trên đều có thể chứa `Integer`.

Khi đọc:

```java
Object value = target.get(0);
```

Ta chỉ biết chắc `Object`, vì list thật có thể là `List<Object>`.

## <a id="wildcard-capture">Wildcard Capture</a>

Đây là **phần nâng cao**. Nếu đang học lần đầu, điều bắt buộc là hiểu `?`, `? extends`, `? super` và PECS. Wildcard capture chủ yếu cần khi compiler không cho một thao tác dù ta biết unknown type phải được giữ nhất quán.

Đôi khi compiler cần “đặt một tên tạm” cho unknown type của wildcard.

Ví dụ API public:

```java
static void swapFirstTwo(List<?> values) {
    swapHelper(values);
}
```

Helper generic capture type:

```java
private static <T> void swapHelper(List<T> values) {
    T first = values.get(0);
    values.set(0, values.get(1));
    values.set(1, first);
}
```

`List<?>` không có nghĩa mọi element là `Object` để có thể ghi tùy ý. Nó có một element type cụ thể nhưng **chưa biết**. Capture cho compiler một type variable nội bộ nhất quán để thao tác read/write đúng kiểu.

Quy tắc thiết kế hữu ích:

- dùng wildcard khi type identity chỉ cần mô tả ở boundary;
- dùng named type parameter khi cần liên kết nhiều parameter/return value bằng cùng một kiểu.

Chương tiếp theo chuyển ba dạng wildcard thành một heuristic dễ áp dụng khi thiết kế API: **PECS**.
