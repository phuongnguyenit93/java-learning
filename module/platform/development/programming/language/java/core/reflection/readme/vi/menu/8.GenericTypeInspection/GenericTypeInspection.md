# Siêu dữ liệu Generics còn lại sau xóa kiểu

Sau khi Reflection đã lấy được siêu dữ liệu của class/thành phần, một vấn đề khác xuất hiện: generic trong Java bị **xóa kiểu (type erasure)**, vậy lúc chạy còn biết gì về `List<String>`, `T extends Number` hay `? super Integer`?

Câu trả lời cần tách làm hai phần. JVM thường không mang đối số kiểu của từng đối tượng để thực thi như một kiểu generic riêng ở lúc chạy, nhưng trình biên dịch có thể ghi **siêu dữ liệu chữ ký generic** vào class file. Reflection đọc siêu dữ liệu của khai báo đó qua `Type` và các subtype của nó.

Chương này giả định người học đã đi qua mô-đun Generics. Chỉ cần nhớ bốn ý để nối sang Reflection: **xóa kiểu (erasure)** loại bỏ phần lớn đối số kiểu khỏi kiểu dùng để thực thi; **biến kiểu (type variable)** là biến như `T`; **giới hạn kiểu (bound)** quy định `T` được phép đại diện cho gì; **wildcard** như `? extends X` / `? super X` mô tả quan hệ kiểu trong khai báo. Ở đây ta không học lại Generics — ta học **Reflection biểu diễn những khai báo đó như thế nào bằng siêu dữ liệu lúc chạy**.

Mô hình ví dụ xuyên suốt vẫn là miền thanh toán. Ví dụ một field generic tự nhiên có thể là:

```java
class PaymentService {
    private List<PaymentRequest> recentRequests;
}
```

Reflection có thể thấy kiểu thô là `List.class` nhưng khai báo generic vẫn mô tả `List<PaymentRequest>` nếu siêu dữ liệu chữ ký được giữ lại.

`PaymentService` không tự nhiên chứa đủ mọi dạng kiểu generic. Vì vậy từ đây chương dùng thêm `Repository<T ...>` **chỉ như ví dụ phụ** để quan sát `TypeVariable`, wildcard và mảng generic mà không làm méo mô hình thanh toán chính:

```java
class Repository<T extends Number & Comparable<T>> {
    List<? extends T> values;
    List<? super Integer> sinks;
    T[] buffer;
}
```

Mô hình này vẫn thuộc cùng cách hiểu về Reflection: khai báo trong mã nguồn được trình biên dịch biến thành siêu dữ liệu class, rồi Reflection dựng lại một mô hình để công cụ/framework đọc lúc chạy.

## <a id="type-interface">Type và mô hình siêu dữ liệu của khai báo generic</a>

`java.lang.reflect.Type` là interface chung cho các dạng kiểu mà Reflection có thể trả về. Điều quan trọng là đừng mặc định mọi `Type` đều là `Class<?>`.

Các dạng chính gồm:

- `Class<?>`: kiểu được JVM biểu diễn trực tiếp bằng `Class`, ví dụ `String`, raw `List`, `int`, `String[]`;
- `ParameterizedType`: kiểu có đối số kiểu cụ thể trong khai báo, ví dụ `List<String>`;
- `TypeVariable<?>`: biến kiểu như `T` trong `Repository<T>`;
- `WildcardType`: wildcard như `? extends T` hoặc `? super Integer`;
- `GenericArrayType`: mảng có kiểu phần tử là cấu trúc generic không thể biểu diễn chỉ bằng một `Class<?>`, ví dụ `T[]`.

Ví dụ:

```java
for (Field field : Repository.class.getDeclaredFields()) {
    Type raw = field.getType();
    Type generic = field.getGenericType();

    System.out.println(field.getName());
    System.out.println("raw     = " + raw.getTypeName());
    System.out.println("generic = " + generic.getTypeName());
}
```

Với `values`, `getType()` trả `List.class`, còn `getGenericType()` trả siêu dữ liệu tương ứng `List<? extends T>`. Với `buffer`, kiểu thô sau xóa kiểu là `Number[]`, trong khi siêu dữ liệu generic mô tả `T[]` dưới dạng `GenericArrayType`.

Đây là lý do API Reflection cho generic trả `Type`: một `Class<?>` không đủ để biểu diễn cấu trúc generic lồng nhau.

## <a id="parameterized-type">ParameterizedType và đối số kiểu</a>

`ParameterizedType` biểu diễn một khai báo như `List<String>`, `Map<String, PaymentRequest>` hoặc `List<? extends T>`.

```java
Field field = Repository.class.getDeclaredField("values");
Type type = field.getGenericType();

if (type instanceof ParameterizedType parameterized) {
    System.out.println(parameterized.getRawType());

    for (Type argument : parameterized.getActualTypeArguments()) {
        System.out.println(argument.getTypeName());
    }
}
```

Ở đây:

```text
kiểu thô        → java.util.List
đối số thực tế  → ? extends T
```

`getActualTypeArguments()` có thể trả tiếp một `Class`, `ParameterizedType`, `TypeVariable`, `WildcardType` hoặc cấu trúc kiểu khác. Vì vậy mã framework thường phải duyệt `Type` như một cây, thay vì ép thẳng mọi đối số về `Class<?>`.

`ParameterizedType` còn có `getOwnerType()` cho trường hợp kiểu lồng nhau/thành phần có kiểu sở hữu (owner) được tham số hóa. Nó không phải phần chính của ví dụ này, nhưng nhắc ta rằng chữ ký generic có thể là cấu trúc nhiều tầng.

## <a id="type-variable-bounds">TypeVariable và giới hạn kiểu</a>

Trong `Repository<T extends Number & Comparable<T>>`, `T` là một `TypeVariable` do class `Repository` khai báo.

```java
TypeVariable<Class<Repository>> variable = Repository.class.getTypeParameters()[0];

System.out.println(variable.getName()); // T

for (Type bound : variable.getBounds()) {
    System.out.println(bound.getTypeName());
}
```

Các giới hạn phản ánh ràng buộc của khai báo:

```text
java.lang.Number
java.lang.Comparable<T>
```

Nếu mã nguồn chỉ viết `<T>` thì giới hạn trên ngầm là `Object`. `TypeVariable` cũng biết khai báo generic nào sở hữu nó qua `getGenericDeclaration()`.

Biến kiểu không chỉ thuộc class. Generic method và generic constructor cũng có thể khai báo biến kiểu riêng vì cả `Method` và `Constructor` đều là `Executable`:

```java
class Converter {
    public <R> R convert(Object source, Class<R> targetType) {
        return targetType.cast(source);
    }
}

Method convert = Converter.class.getMethod(
        "convert",
        Object.class,
        Class.class
);

TypeVariable<Method>[] methodVariables = convert.getTypeParameters();
System.out.println(methodVariables[0].getGenericDeclaration() == convert); // true
```

`getGenericDeclaration()` vì thế cho biết `T`/`R` thuộc khai báo nào, chứ không chỉ cho biết tên của biến kiểu.

Một lỗi tư duy phổ biến là nhìn thấy `T` rồi cố tìm “class thật của T ở lúc chạy”. Reflection đang mô tả **biến kiểu trong khai báo**, không tự động biết một đối tượng cụ thể đã được tạo với `T = Integer` hay `T = Long`.

## <a id="wildcard-reflection">Wildcard qua Reflection</a>

Wildcard biểu diễn quan hệ variance tại thời điểm biên dịch và siêu dữ liệu giữ lại các giới hạn của nó.

Với field:

```java
List<? extends T> values;
```

đối số kiểu là `WildcardType` có giới hạn trên `T` và không có giới hạn dưới hữu ích. Với:

```java
List<? super Integer> sinks;
```

wildcard có giới hạn dưới `Integer`; giới hạn trên của nó vẫn là `Object`.

Ta có thể quan sát như sau:

```java
ParameterizedType listType = (ParameterizedType)
        Repository.class.getDeclaredField("sinks").getGenericType();

WildcardType wildcard = (WildcardType) listType.getActualTypeArguments()[0];

System.out.println(Arrays.toString(wildcard.getUpperBounds()));
System.out.println(Arrays.toString(wildcard.getLowerBounds()));
```

Điều Reflection cung cấp là siêu dữ liệu về **ràng buộc đã khai báo**. Nó không biến wildcard thành một class cụ thể ở lúc chạy. Nếu framework cần phân giải cây kiểu, nó phải hiểu ý nghĩa của giới hạn trên/dưới và tiếp tục phân giải `Type` lồng nhau khi cần.

## <a id="erasure-vs-signature-metadata">Xóa kiểu và siêu dữ liệu chữ ký generic</a>

Xóa kiểu (type erasure) nghĩa là nhiều khác biệt generic không tồn tại như định danh kiểu riêng khi chương trình thực thi. Ví dụ:

```java
Repository<Integer> integers = new Repository<>();
Repository<Long> longs = new Repository<>();

System.out.println(integers.getClass() == longs.getClass()); // true
```

Cả hai đối tượng có cùng `Class` lúc chạy: `Repository.class`. Reflection không thể hỏi một đối tượng `Repository` bất kỳ rồi luôn nhận lại “đối tượng này là `Repository<Integer>`”. Thông tin đó thường đã bị xóa.

Thông tin generic vẫn có thể tồn tại ở **vị trí khai báo**. Ví dụ field `List<PaymentRequest> requests`, method trả `List<String>`, superclass `BaseRepository<PaymentRequest>` hoặc khai báo `Repository<T extends Number>` có thể mang siêu dữ liệu chữ ký. API như `getGenericType()`, `getGenericReturnType()`, `getGenericParameterTypes()` và `getGenericSuperclass()` đọc phần siêu dữ liệu này.

Vì vậy cần phân biệt:

```text
kiểu dùng khi thực thi
→ chủ yếu dùng Class/JVM descriptor sau xóa kiểu

siêu dữ liệu chữ ký generic
→ mô tả khai báo generic để trình biên dịch/công cụ/framework có thể đọc
```

Một khai báo class ẩn danh hoặc class con đôi khi giữ đối số kiểu cụ thể trong chữ ký generic của superclass, nên các thư viện có thể dùng mẫu “type token”. Nhưng đó là vì **khai báo của class con giữ siêu dữ liệu**, không phải vì JVM âm thầm nhớ đối số kiểu cho mọi đối tượng generic.

Reflection cũng không nên dùng chữ ký generic như một cơ chế bảo đảm an toàn kiểu generic lúc chạy. Nó giúp serializer, dependency injection container, schema generator và framework hiểu khai báo tốt hơn; việc kiểm tra và hành vi lúc chạy vẫn phải được thiết kế riêng.

Sau chương này ta đã biết Reflection có thể mang theo cấu trúc kiểu khá phong phú. Bước tiếp theo trong ROADMAP là **Proxy động và chặn lời gọi**: một trường hợp tích hợp nơi siêu dữ liệu `Method` được chuyển qua `InvocationHandler` để tạo hành vi trung gian lúc chạy.
