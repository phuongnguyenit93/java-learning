# Wildcard, PECS và chiều đọc/ghi dữ liệu

Chương trước đã giải thích vì sao `List<Dog>` không tự trở thành `List<Animal>`. Quy tắc đó giữ an toàn kiểu, nhưng API thực tế vẫn cần một cách chấp nhận **một họ kiểu generic liên quan** mà không phá quy tắc bất biến.

Đến đây ta cũng biết `List<String>` và `List<Integer>` là hai kiểu được tham số hóa (parameterized type) khác nhau. Bây giờ xuất hiện một nhu cầu mới:

```text
"Phương thức này không quan tâm List chứa chính xác kiểu gì.
Nó chỉ cần thực hiện một thao tác áp dụng được cho mọi List."
```

Ví dụ phương thức chỉ cần lấy kích thước:

```java
static int sizeOf(List<?> values) {
    return values.size();
}
```

Ta không cần đặt tên kiểu phần tử là `T`, vì phương thức không cần liên kết kiểu đó với tham số hay giá trị trả về nào khác.

**Wildcard `?`** có thể hiểu là: “ở đây có một type argument cụ thể, nhưng đoạn mã này không cần biết chính xác nó là kiểu nào”.

Wildcard tồn tại để làm **ranh giới API linh hoạt hơn** mà vẫn giữ an toàn kiểu của Generics. Các dạng `? extends ...` và `? super ...` tiếp tục nói rõ kiểu chưa biết đó nằm ở phía nào trong hệ phân cấp kiểu.

Kết nối trực tiếp với chương Invariance:

```java
List<Dog> dogs = new ArrayList<>();

List<? extends Animal> animals = dogs;
List<? super Dog> target = new ArrayList<Animal>();
```

Wildcard cung cấp **biến thiên tại nơi sử dụng (use-site variance)** có kiểm soát thay vì biến kiểu generic thành hiệp biến toàn cục.

Chương Invariance trước đó đã giới thiệu mô hình `List<E>` tối thiểu (`get` để đọc, `add` để ghi, `size` để lấy số phần tử). Chương này dùng lại đúng mô hình đó, không yêu cầu thêm kiến thức Collection.

## <a id="unbounded-wildcard">Wildcard không giới hạn — ?</a>

```java
static int sizeOf(List<?> values) {
    return values.size();
}
```

`List<?>` nghĩa là “một `List` của kiểu nào đó, nhưng phương thức này không biết kiểu đó là gì”.

Nó khác raw `List`:

- `List<?>` vẫn giữ các kiểm tra an toàn kiểu của Generics;
- raw `List` làm mất một phần kiểm tra generic và có thể sinh cảnh báo unchecked.

Từ `List<?>`, có thể đọc phần tử an toàn dưới dạng `Object`:

```java
Object value = values.get(0);
```

Không thể thêm một object cụ thể vì compiler không biết kiểu phần tử thật:

```java
// values.add("x"); // compile error
```

`null` là ngoại lệ về mặt hệ thống kiểu, nhưng việc thêm `null` hiếm khi là lý do thiết kế một API wildcard.

## <a id="wildcard-vs-object">List&lt;?&gt; khác List&lt;Object&gt; thế nào?</a>

Đây là nhầm lẫn rất phổ biến:

```java
List<?> unknown;
List<Object> objects;
```

`List<Object>` có nghĩa **kiểu phần tử chính xác là `Object`**. Vì vậy có thể thêm `String`, `Integer`, `User`... vì tất cả đều là subtype của `Object`:

```java
objects.add("java");
objects.add(123);
```

`List<?>` lại có nghĩa **kiểu phần tử chính xác tồn tại nhưng ta không biết nó là gì**. Biến này có thể tham chiếu `List<String>`, `List<Integer>`, `List<User>`...

```java
List<String> names = new ArrayList<>();
List<?> unknown = names; // OK
```

Do compiler không biết kiểu thật bị ẩn phía sau wildcard là gì, ta không thể thêm một giá trị cụ thể tùy ý:

```java
// unknown.add("java"); // compile error
```

Mô hình tư duy:

```text
List<Object>
→ biết chính xác element type = Object

List<?>
→ biết đây là List của MỘT type nào đó
→ nhưng không biết type đó là gì
```

## <a id="wildcard-placement">Wildcard được dùng ở đâu?</a>

Wildcard là **type argument ở nơi sử dụng (use site)**, không phải cách khai báo một type parameter mới.

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

Tương tự, wildcard không được dùng làm type argument trực tiếp trong `extends` / `implements` của một khai báo:

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

## <a id="wildcard-vs-type-parameter">Wildcard hay tham số kiểu có tên?</a>

`?` và `<T>` đều giúp API tránh khóa cứng vào một kiểu cụ thể, nhưng chúng giải quyết **hai nhu cầu khác nhau**.

Khi API chỉ cần nói “đây là một giá trị thuộc một họ kiểu nào đó” và không cần nhắc lại chính kiểu đó ở vị trí khác, wildcard thường đủ:

```java
static int sizeOf(List<?> values) {
    return values.size();
}
```

Khi nhiều vị trí phải cùng nói về **một kiểu duy nhất**, tham số kiểu có tên giúp giữ quan hệ đó:

```java
static <T> T first(List<T> values) {
    return values.get(0);
}

static <T> void copyOne(T value, List<? super T> target) {
    target.add(value);
}
```

Mô hình tư duy:

```text
?
→ một type argument chưa biết ở nơi sử dụng
→ không cần đặt tên để dùng lại ở vị trí khác

<T>
→ một type variable có tên
→ dùng khi API cần giữ cùng một danh tính kiểu qua nhiều vị trí
```

Đây không phải quy tắc “wildcard chỉ để đọc, `<T>` chỉ để ghi”. Câu hỏi đúng là: **API có cần giữ và tái sử dụng cùng một quan hệ kiểu ở nhiều vị trí hay không?**

## <a id="extends-wildcard">Wildcard giới hạn trên — ? extends</a>

```java
static double sum(List<? extends Number> values) {
    double total = 0;
    for (Number value : values) {
        total += value.doubleValue();
    }
    return total;
}
```

Phương thức chấp nhận:

```java
List<Integer>
List<Double>
List<BigDecimal>
```

Ta biết phần tử là **một subtype chưa biết của `Number`**, nên đọc ra dưới dạng `Number` là an toàn.

Nhưng không thể:

```java
// values.add(1);   // compile error
// values.add(2.5); // compile error
```

Vì list thật có thể là `List<Double>`; thêm `Integer` sẽ phá an toàn kiểu.

## <a id="super-wildcard">Wildcard giới hạn dưới — ? super</a>

```java
static void addDefaults(List<? super Integer> target) {
    target.add(1);
    target.add(2);
}
```

Phương thức có thể nhận `List<Integer>`, `List<Number>` hoặc `List<Object>`.

Ta được phép ghi `Integer` vì mọi khả năng trên đều có thể chứa `Integer`.

Khi đọc:

```java
Object value = target.get(0);
```

Ta chỉ biết chắc `Object`, vì list thật có thể là `List<Object>`.

### Từ Wildcard đến PECS

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

`source` là nguồn mà phương thức **đọc `T` ra**. `target` là đích mà phương thức **ghi `T` vào**.

**PECS — Producer Extends, Consumer Super** — chỉ là một cách nhớ hướng đó:

```text
nguồn CUNG CẤP (Producer) dữ liệu cho mình đọc → ? extends T
đích TIẾP NHẬN (Consumer) dữ liệu mình ghi vào → ? super T
```

PECS không phải một tính năng mới của Java; nó là **quy tắc gợi nhớ** giúp đọc và thiết kế chữ ký wildcard đúng hơn.

## <a id="pecs-rule">Quy tắc PECS</a>

Hãy hỏi tham số đang làm gì với giá trị kiểu `T`:

```text
tham số chủ yếu CUNG CẤP T cho phương thức đọc
→ ? extends T

tham số chủ yếu TIẾP NHẬN T do phương thức ghi vào
→ ? super T

tham số cần vừa đọc chính xác T vừa ghi T,
hoặc nhiều vị trí phải giữ cùng một quan hệ kiểu
→ thường dùng kiểu chính xác hoặc tham số kiểu có tên
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

## <a id="read-from-producer">Đọc từ bên cung cấp (Producer) — extends</a>

`List<? extends Animal>` có thể là:

```java
List<Animal>
List<Dog>
List<Cat>
```

Phương thức có thể đọc:

```java
Animal animal = source.get(0);
```

Vì mọi khả năng đều sinh ra ít nhất một `Animal`.

Nhưng không thể ghi một `Dog`:

```java
// source.add(new Dog()); // compile error
```

List thật có thể là `List<Cat>`.

Do đó “extends = read-only” chỉ là cách nhớ gần đúng. API vẫn có thể thực hiện thao tác không phụ thuộc kiểu phần tử như `clear()` hoặc xóa theo chỉ số. Điều bị hạn chế là **ghi một giá trị phần tử cụ thể** khi kiểu chính xác chưa biết.

## <a id="write-to-consumer">Ghi vào bên tiếp nhận (Consumer) — super</a>

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

## <a id="pecs-api-design">Áp dụng PECS khi thiết kế API</a>

Chữ ký hẹp:

```java
static void moveDogs(List<Dog> source, List<Dog> target)
```

chỉ dùng được khi cả hai phía chính xác là `List<Dog>`.

Chữ ký linh hoạt:

```java
static void moveDogs(
        List<? extends Dog> source,
        List<? super Dog> target)
```

cho phép nguồn chứa subtype của `Dog` và đích là cấu trúc chứa `Dog` hoặc một supertype của `Dog`.

JDK dùng mẫu này rộng rãi. Ví dụ, `Collections.copy` coi nguồn là nơi cung cấp dữ liệu và đích là nơi tiếp nhận dữ liệu.

Không nên áp PECS máy móc:

- nếu tham số cần cả đọc và ghi cùng một `T`, `List<T>` chính xác hoặc tham số kiểu có tên có thể rõ hơn;
- nếu wildcard làm chữ ký khó hiểu nhưng không tăng khả năng tái sử dụng, không cần thêm;
- tham số kiểu có tên thích hợp khi kiểu trả về hoặc nhiều đầu vào phải liên hệ trực tiếp với nhau.

Đặc biệt, thường **tránh wildcard ở kiểu trả về công khai** nếu có thể trả về một kiểu cụ thể hơn. Kiểu trả về như `List<? extends Animal>` buộc bên gọi tiếp tục mang kiểu chưa biết và xử lý wildcard ở phía của họ. Wildcard thường hữu ích nhất ở **ranh giới đầu vào**, nơi API muốn chấp nhận nhiều parameterized type hợp lệ.

## <a id="wildcard-capture">Bắt giữ Wildcard (Wildcard Capture)</a>

Đây là **phần nâng cao**. Nếu đang học lần đầu, điều bắt buộc là hiểu `?`, `? extends`, `? super` và PECS. Wildcard capture chủ yếu cần khi compiler không cho một thao tác dù ta biết kiểu chưa biết đó phải được giữ nhất quán.

Đôi khi compiler cần “đặt một tên tạm” cho kiểu chưa biết của wildcard.

Ví dụ API công khai:

```java
static void swapFirstTwo(List<?> values) {
    swapHelper(values);
}
```

Phương thức hỗ trợ dùng generic để capture kiểu:

```java
private static <T> void swapHelper(List<T> values) {
    T first = values.get(0);
    values.set(0, values.get(1));
    values.set(1, first);
}
```

`List<?>` không có nghĩa mọi phần tử là `Object` để có thể ghi tùy ý. Nó có một kiểu phần tử cụ thể nhưng **chưa biết**. Capture cho compiler một type variable nội bộ nhất quán để liên kết thao tác đọc/ghi đúng kiểu.

Quy tắc thiết kế hữu ích:

- dùng wildcard khi danh tính kiểu chỉ cần mô tả ở ranh giới API;
- dùng tham số kiểu có tên khi cần liên kết nhiều tham số/giá trị trả về bằng cùng một kiểu.

Wildcard + PECS đã hoàn tất phần linh hoạt ở nơi sử dụng. Chương tiếp theo chuyển sang ranh giới tương thích với mã Java cũ: **raw type**.
