# Raw Types

Bạn có thể gặp cả hai dạng code sau:

```java
List<String> names = new ArrayList<>();
List names = new ArrayList();
```

Dòng thứ hai nhìn giống như “Generics nhưng bỏ phần `<String>`”. Tại sao Java vẫn cho phép?

Lý do là **Generics được thêm vào Java sau khi rất nhiều code Java đã tồn tại**. Java cần một con đường để code generic mới vẫn gọi được thư viện/code cũ chưa có type argument.

`List`, `Box`, `Map`... khi dùng tên generic type mà **không có type argument** được gọi là **raw type**.

Raw type là một **cầu tương thích với legacy code**. Đổi lại, compiler mất bớt khả năng bảo vệ type safety. Vì vậy mục tiêu của chương này không phải “học cách dùng raw type”, mà là **nhận ra nó, hiểu rủi ro và cô lập nó khỏi code mới**.

## <a id="raw-type-compatibility">Raw Type và Legacy Compatibility</a>

Với generic declaration:

```java
class Box<T> {
    private T value;
    void set(T value) { this.value = value; }
    T get() { return value; }
}
```

`Box` không có type argument là raw type:

```java
Box raw = new Box();
```

Trong code mới, nên dùng:

```java
Box<String> typed = new Box<>();
Box<?> unknown = typed;
```

`Box<?>` và raw `Box` không tương đương. Wildcard nói “có một type argument cụ thể nhưng tôi không biết nó”; raw type bỏ qua phần generic checking để tương thích legacy code.

## <a id="unchecked-warning">Unchecked Warning</a>

Raw usage thường làm compiler không thể chứng minh type safety:

```java
List raw = new ArrayList<String>();
raw.add(123); // unchecked call warning
```

Warning không phải “compiler khó tính”. Nó báo rằng một generic guarantee đang bị xuyên qua.

Java có cơ chế để suppress compiler warning, nhưng syntax annotation cụ thể được học ở module Annotation. Ở đây điều quan trọng là: **đừng che warning trước khi hiểu vì sao compiler không chứng minh được type safety**.

Hãy xác định:

- warning đến từ legacy boundary nào;
- có thể đổi API sang parameterized type không;
- nếu bắt buộc cast, có điều kiện runtime nào chứng minh cast an toàn không.

## <a id="heap-pollution">Heap Pollution</a>

Đây là phần **đào sâu nguyên nhân của unchecked safety failure**. Beginner nên nhận diện được raw type và hiểu unchecked warning trước; chi tiết heap pollution quan trọng hơn khi tích hợp legacy API, generic varargs hoặc debug lỗi type xuất hiện xa nguồn gây ra.

Heap pollution xảy ra khi một variable của parameterized type tham chiếu tới object không phù hợp với parameterized type đó.

Ví dụ qua raw type:

```java
List<String> names = new ArrayList<>();
List raw = names;
raw.add(123); // unchecked

String name = names.get(0); // ClassCastException tại cast compiler chèn
```

Điểm nguy hiểm là lỗi thường xuất hiện **xa điểm gây pollution**.

Heap pollution cũng có thể liên quan generic varargs hoặc unchecked casts. Module `language-basics → Varargs` sở hữu đầy đủ semantics của varargs; module Annotation sở hữu annotation liên quan. Ở đây chỉ cần nhớ rằng non-reifiable element types làm một số boundary không thể được compiler kiểm tra hoàn toàn.

## <a id="raw-type-boundary">Cô lập Raw-Type Boundary</a>

Nếu phải tích hợp legacy API:

```java
static List<String> legacyNames() {
    List raw = LegacyApi.loadNames();

    for (Object value : raw) {
        if (!(value instanceof String)) {
            throw new IllegalStateException("Unexpected value: " + value);
        }
    }

    return (List<String>) raw;
}
```

Cast ở return vẫn có thể tạo unchecked warning. Điểm của ví dụ không phải “làm warning biến mất”, mà là **dồn phần không thể chứng minh vào một boundary nhỏ sau khi đã validate dữ liệu**.

Pattern tốt:

```text
legacy/raw source
        ↓
boundary nhỏ
        ↓
validate / convert
        ↓
typed code phía trong ứng dụng
```

Nếu sau này cần suppress warning, đặt suppression ở phạm vi nhỏ nhất và giải thích invariant đã được kiểm tra.

Raw types cho thấy generic safety chủ yếu là compile-time mechanism. Chương tiếp theo giải thích cơ chế nền làm compatibility này khả thi: **type erasure**.
