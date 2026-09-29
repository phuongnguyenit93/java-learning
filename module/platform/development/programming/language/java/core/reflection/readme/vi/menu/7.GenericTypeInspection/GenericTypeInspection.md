# Đọc Generic Type bằng Reflection

Sau khi Reflection đã lấy được class/member metadata, một vấn đề khác xuất hiện: generic trong Java bị **type erasure**, vậy runtime còn biết gì về `List<String>`, `T extends Number` hay `? super Integer`?

Câu trả lời cần tách làm hai phần. JVM thường không mang type argument của từng object để thực thi như một runtime generic type riêng, nhưng trình biên dịch có thể ghi **generic signature metadata** vào class file. Reflection đọc metadata của khai báo đó qua `Type` và các subtype của nó.

Chương này giả định learner đã đi qua module Generics. Chỉ cần nhớ bốn ý để nối sang Reflection: **erasure** xóa phần lớn type argument khỏi runtime execution type; **type variable** là biến kiểu như `T`; **bound** giới hạn `T` được phép đại diện cho gì; **wildcard** như `? extends X` / `? super X` mô tả quan hệ type ở declaration. Ở đây ta không học lại Generics — ta học **Reflection biểu diễn những declaration đó như thế nào ở runtime metadata**.

Running model vẫn là payment domain. Ví dụ một field generic tự nhiên có thể là:

```java
class PaymentService {
    private List<PaymentRequest> recentRequests;
}
```

Reflection có thể thấy raw type là `List.class` nhưng generic declaration vẫn mô tả `List<PaymentRequest>` nếu signature metadata được giữ lại.

`PaymentService` không tự nhiên chứa đủ mọi dạng generic type. Vì vậy từ đây chương dùng thêm `Repository<T ...>` **chỉ như specimen phụ** để quan sát `TypeVariable`, wildcard và generic array mà không làm méo model payment chính:

```java
class Repository<T extends Number & Comparable<T>> {
    List<? extends T> values;
    List<? super Integer> sinks;
    T[] buffer;
}
```

Mô hình này vẫn thuộc cùng cách hiểu về Reflection: khai báo trong mã nguồn được trình biên dịch biến thành class metadata, rồi Reflection dựng lại một mô hình để công cụ/framework đọc ở runtime.

## <a id="type-interface">Type là mô hình metadata của generic declaration</a>

`java.lang.reflect.Type` là interface chung cho các dạng type mà Reflection có thể trả về. Điều quan trọng là đừng mặc định mọi `Type` đều là `Class<?>`.

Các dạng chính gồm:

- `Class<?>`: type bình thường hoặc raw/reifiable type như `String`, `List`, `int[]`;
- `ParameterizedType`: type có type argument cụ thể trong declaration, ví dụ `List<String>`;
- `TypeVariable<?>`: biến type như `T` trong `Repository<T>`;
- `WildcardType`: wildcard như `? extends T` hoặc `? super Integer`;
- `GenericArrayType`: array có component type không phải reifiable class, ví dụ `T[]`.

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

Với `values`, `getType()` trả `List.class`, còn `getGenericType()` trả metadata tương ứng `List<? extends T>`. Với `buffer`, raw type sau erasure là `Number[]`, trong khi generic metadata mô tả `T[]` dưới dạng `GenericArrayType`.

Đây là lý do API generic reflection trả `Type`: một `Class<?>` không đủ để biểu diễn cấu trúc generic lồng nhau.

## <a id="parameterized-type">ParameterizedType và type argument</a>

`ParameterizedType` biểu diễn một declaration như `List<String>`, `Map<String, PaymentRequest>` hoặc `List<? extends T>`.

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
raw type        → java.util.List
actual argument → ? extends T
```

`getActualTypeArguments()` có thể trả tiếp một `Class`, `ParameterizedType`, `TypeVariable`, `WildcardType` hoặc cấu trúc type khác. Vì vậy code framework thường phải duyệt `Type` như một cây, thay vì cast thẳng mọi argument về `Class<?>`.

`ParameterizedType` còn có `getOwnerType()` cho trường hợp nested/member type có owner parameterized. Nó không phải phần chính của ví dụ này, nhưng nhắc ta rằng generic signature có thể là cấu trúc nhiều tầng.

## <a id="type-variable-bounds">TypeVariable và bounds</a>

Trong `Repository<T extends Number & Comparable<T>>`, `T` là một `TypeVariable` do class `Repository` khai báo.

```java
TypeVariable<Class<Repository>> variable = Repository.class.getTypeParameters()[0];

System.out.println(variable.getName()); // T

for (Type bound : variable.getBounds()) {
    System.out.println(bound.getTypeName());
}
```

Bounds phản ánh ràng buộc của khai báo:

```text
java.lang.Number
java.lang.Comparable<T>
```

Nếu mã nguồn chỉ viết `<T>` thì upper bound ngầm là `Object`. `TypeVariable` cũng biết generic declaration nào sở hữu nó qua `getGenericDeclaration()`.

Type variable không chỉ thuộc class. Generic method và generic constructor cũng có thể khai báo type variable riêng vì cả `Method` và `Constructor` đều là `Executable`:

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

`getGenericDeclaration()` vì thế cho biết `T`/`R` thuộc declaration nào, chứ không chỉ cho biết tên của biến type.

Một lỗi tư duy phổ biến là nhìn thấy `T` rồi cố tìm “runtime class thật của T”. Reflection đang mô tả **biến type trong khai báo**, không tự động biết một object cụ thể đã được tạo với `T = Integer` hay `T = Long`.

## <a id="wildcard-reflection">Wildcard qua Reflection</a>

Wildcard biểu diễn quan hệ variance tại compile time và metadata giữ lại bounds của nó.

Với field:

```java
List<? extends T> values;
```

type argument là `WildcardType` có upper bound `T` và không có lower bound hữu ích. Với:

```java
List<? super Integer> sinks;
```

wildcard có lower bound `Integer`; upper bound của nó vẫn là `Object`.

Ta có thể quan sát như sau:

```java
ParameterizedType listType = (ParameterizedType)
        Repository.class.getDeclaredField("sinks").getGenericType();

WildcardType wildcard = (WildcardType) listType.getActualTypeArguments()[0];

System.out.println(Arrays.toString(wildcard.getUpperBounds()));
System.out.println(Arrays.toString(wildcard.getLowerBounds()));
```

Điều Reflection cung cấp là metadata về **ràng buộc đã khai báo**. Nó không biến wildcard thành một runtime class cụ thể. Nếu framework cần resolve cây type, nó phải hiểu ý nghĩa của upper/lower bounds và tiếp tục resolve `Type` lồng nhau khi cần.

## <a id="erasure-vs-signature-metadata">Erasure và generic signature metadata</a>

Type erasure nghĩa là nhiều khác biệt generic không tồn tại như type identity riêng khi chương trình thực thi. Ví dụ:

```java
Repository<Integer> integers = new Repository<>();
Repository<Long> longs = new Repository<>();

System.out.println(integers.getClass() == longs.getClass()); // true
```

Cả hai object có cùng runtime `Class`: `Repository.class`. Reflection không thể hỏi một object `Repository` bất kỳ rồi luôn nhận lại “object này là `Repository<Integer>`”. Thông tin đó thường đã bị erasure.

Thông tin generic vẫn có thể tồn tại ở **vị trí khai báo**. Ví dụ field `List<PaymentRequest> requests`, method trả `List<String>`, superclass `BaseRepository<PaymentRequest>` hoặc khai báo `Repository<T extends Number>` có thể mang signature metadata. API như `getGenericType()`, `getGenericReturnType()`, `getGenericParameterTypes()` và `getGenericSuperclass()` đọc phần metadata này.

Vì vậy cần phân biệt:

```text
runtime execution type
→ chủ yếu dùng erased Class/JVM descriptors

generic signature metadata
→ mô tả generic declaration để compiler/tool/framework có thể đọc
```

Một khai báo anonymous/subclass đôi khi giữ type argument cụ thể trong generic superclass signature, nên các thư viện có thể dùng pattern “type token”. Nhưng đó là vì **khai báo của subclass giữ metadata**, không phải vì JVM âm thầm nhớ type argument cho mọi generic object.

Reflection cũng không nên dùng generic signature như một cơ chế bảo đảm generic safety ở runtime. Nó giúp serializer, dependency injection container, schema generator và framework hiểu khai báo tốt hơn; việc kiểm tra và hành vi runtime vẫn phải được thiết kế riêng.

Sau chương này ta đã biết Reflection có thể mang theo cấu trúc type khá phong phú. Bước tiếp theo là nhìn vào lúc metadata được dùng để thật sự gọi method: dynamic invocation phải xử lý target, chuyển đổi argument và lỗi theo cách mà lời gọi Java trực tiếp thường được trình biên dịch chuẩn bị từ trước.
