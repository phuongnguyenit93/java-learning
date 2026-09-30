# Kiểu thô (Raw Types) và ranh giới mã cũ

Bạn có thể gặp cả hai dạng mã sau:

```java
List<String> names = new ArrayList<>();
List names = new ArrayList();
```

Dòng thứ hai nhìn giống như “Generics nhưng bỏ phần `<String>`”. Tại sao Java vẫn cho phép?

Lý do là **Generics được thêm vào Java sau khi rất nhiều mã Java đã tồn tại**. Java cần một con đường để mã generic mới vẫn gọi được thư viện hoặc mã cũ chưa có type argument.

`List`, `Box`, `Map`... khi dùng tên generic type mà **không có type argument** được gọi là **raw type**.

Raw type là một **cầu tương thích với mã cũ (legacy code)**. Đổi lại, compiler mất bớt khả năng bảo vệ an toàn kiểu. Vì vậy mục tiêu của chương này không phải “học cách dùng raw type”, mà là **nhận ra nó, hiểu rủi ro và cô lập nó khỏi mã mới**.

## <a id="raw-type-compatibility">Kiểu thô và khả năng tương thích với mã cũ</a>

Với một khai báo generic:

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

Trong mã mới, nên dùng:

```java
Box<String> typed = new Box<>();
Box<?> unknown = typed;
```

`Box<?>` và raw `Box` không tương đương. Wildcard nói “có một type argument cụ thể nhưng tôi không biết nó”; raw type làm suy yếu một phần kiểm tra generic để tương thích với mã cũ.

## <a id="unchecked-warning">Cảnh báo không được kiểm tra đầy đủ (Unchecked Warning)</a>

Việc dùng raw type thường làm compiler không thể chứng minh đầy đủ an toàn kiểu:

```java
List raw = new ArrayList<String>();
raw.add(123); // unchecked call warning
```

Cảnh báo không phải “compiler khó tính”. Nó báo rằng một đảm bảo generic đang bị vượt qua mà compiler không thể kiểm chứng đầy đủ.

Java có cơ chế để bỏ qua có chủ đích (suppress) cảnh báo của compiler, nhưng cú pháp annotation cụ thể được học ở module Annotation. Ở đây điều quan trọng là: **đừng che cảnh báo trước khi hiểu vì sao compiler không chứng minh được an toàn kiểu**.

Hãy xác định:

- cảnh báo đến từ ranh giới mã cũ nào;
- có thể đổi API sang parameterized type không;
- nếu bắt buộc ép kiểu, có điều kiện lúc chạy nào chứng minh phép ép kiểu an toàn không.

## <a id="heap-pollution">Ô nhiễm heap (Heap Pollution)</a>

Đây là phần **đào sâu nguyên nhân của lỗi an toàn kiểu không thể được compiler kiểm tra đầy đủ**. Người mới nên nhận diện được raw type và hiểu cảnh báo unchecked trước; chi tiết heap pollution quan trọng hơn khi tích hợp API cũ, generic varargs hoặc gỡ lỗi kiểu xuất hiện xa nguồn gây ra.

Heap pollution xảy ra khi một biến của parameterized type tham chiếu tới object không phù hợp với parameterized type đó.

Ví dụ qua raw type:

```java
List<String> names = new ArrayList<>();
List raw = names;
raw.add(123); // unchecked

String name = names.get(0); // ClassCastException tại phép ép kiểu compiler chèn
```

Điểm nguy hiểm là lỗi thường xuất hiện **xa điểm gây heap pollution**.

Heap pollution cũng có thể liên quan generic varargs hoặc phép ép kiểu unchecked. Module `language-basics → Varargs` sở hữu đầy đủ ngữ nghĩa của varargs; module Annotation sở hữu annotation liên quan. **Lý do sâu hơn liên quan lượng thông tin kiểu còn lại ở runtime** sẽ được giải thích ở chương Type Erasure. Ở đây chỉ cần nhớ: một thao tác unchecked có thể tạo ra trạng thái mà compiler không còn chứng minh đầy đủ được an toàn kiểu.

## <a id="raw-type-boundary">Cô lập ranh giới dùng kiểu thô</a>

Nếu phải tích hợp legacy API:

```java
static List<String> legacyNames() {
    List raw = LegacyApi.loadNames();
    List<String> result = new ArrayList<>();

    for (Object value : raw) {
        if (!(value instanceof String)) {
            throw new IllegalStateException("Unexpected value: " + value);
        }
        result.add((String) value);
    }

    return result;
}
```

Cách này kiểm tra từng phần tử rồi **sao chép sang một `List<String>` mới**. Nhờ đó phần còn lại của ứng dụng không còn giữ một tham chiếu tới chính danh sách thô và không phụ thuộc vào việc mã cũ có tiếp tục chèn sai kiểu sau lần kiểm tra hay không.

Nếu vì hiệu năng hoặc yêu cầu giữ nguyên **danh tính đối tượng** mà bắt buộc phải dùng chính danh sách cũ:

```java
return (List<String>) raw;
```

thì phép ép kiểu đó vẫn là unchecked. Khi đó **ranh giới tích hợp** phải bảo đảm một điều kiện bất biến mạnh hơn: không chỉ dữ liệu hiện tại đúng kiểu, mà **không còn tham chiếu raw nào có thể thay đổi danh sách bằng giá trị sai kiểu về sau**. Nếu không bảo đảm được điều đó, sao chép/chuyển đổi là lựa chọn an toàn hơn.

Mẫu xử lý tốt:

```text
nguồn legacy/raw
        ↓
ranh giới nhỏ
        ↓
kiểm tra / chuyển đổi
        ↓
mã có kiểu rõ ràng phía trong ứng dụng
```

Nếu sau này cần bỏ qua cảnh báo có chủ đích (suppress warning), hãy đặt suppression ở phạm vi nhỏ nhất và giải thích rõ điều kiện bất biến nào khiến thao tác unchecked đó an toàn.

Raw type cho thấy phần lớn bảo vệ của Generics diễn ra khi biên dịch. Chương tiếp theo giải thích cơ chế nền giúp khả năng tương thích này tồn tại: **type erasure**.
