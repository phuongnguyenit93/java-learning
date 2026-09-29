# Pass-by-Value

Java **luôn truyền tham số bằng giá trị (pass-by-value)**. Sự nhầm lẫn xuất hiện vì với object, giá trị được copy là **reference value**.

## <a id="java-pass-by-value">Java luôn Pass-by-Value</a>

Khi gọi method, parameter nhận một bản copy của đối số value.

```java
void increment(int x) {
    x++;
}
```

bên gọi primitive không đổi vì parameter `x` là một bản copy.

Với object reference:

```java
void use(User user) { }
```

parameter `user` cũng nhận một bản copy — nhưng bản copy này là reference tới cùng object.

### Mental model: caller slot không được truyền vào method

```text
caller variable                    parameter variable
┌──────────────┐     copy value    ┌──────────────┐
│ reference R  │ ───────────────→  │ reference R  │
└──────────────┘                   └──────────────┘
        │                                  │
        └──────────────→ User object ←─────┘
```

Method nhận **một variable mới của riêng call frame**. Nếu argument là primitive, primitive value được copy. Nếu argument là reference, reference value được copy. Không trường hợp nào method nhận trực tiếp variable slot của caller.

Đây là lý do Java được mô tả nhất quán là pass-by-value, không cần ngoại lệ "primitive by value, object by reference".

## <a id="reference-copy-mutation">Copy Reference vẫn thấy Mutation</a>

```java
void rename(User user) {
    user.setName("B");
}
```

bên gọi thấy object bị đổi vì cả bên gọi reference và parameter reference đều trỏ tới cùng `User` object.

Điều này **không biến Java thành pass-by-reference**. Method không nhận quyền thay đổi variable slot của bên gọi; nó nhận một reference value được copy.

Array cũng theo cùng quy tắc:

```java
void changeFirst(int[] values) {
    values[0] = 99;
}
```

Caller thấy element đổi vì caller và parameter cùng nhận diện một array object. Điều này là **shared mutation qua alias**, không phải pass-by-reference.

Immutable object giúp quan sát dễ hơn:

```java
void append(String text) {
    text = text + "!";
}
```

`String` immutable nên expression tạo value/object khác và reassign parameter; caller không thấy variable của mình đổi.

## <a id="reassignment-vs-mutation">Reassignment khác Mutation</a>

```java
void replace(User user) {
    user = new User("new");
}
```

Reassign parameter chỉ đổi local parameter variable. bên gọi reference vẫn trỏ object cũ.

So sánh:

```text
user.setName(...)
→ mutate object chung

user = new User(...)
→ chỉ reassign parameter local
```

Đây là mô hình tư duy nền tảng cho aliasing, defensive copy và method API design.

### Ví dụ `swap` kinh điển

```java
void swap(User a, User b) {
    User temp = a;
    a = b;
    b = temp;
}
```

Method chỉ swap hai **parameter local**. Hai variable của caller không đổi reference.

### Implication cho API design

Khi method nhận mutable object, hãy làm rõ contract:

- method chỉ đọc object;
- method mutate object caller truyền vào;
- method tạo và trả object mới;
- method giữ reference để dùng lâu dài.

Nếu không muốn caller và callee chia sẻ mutable state, defensive copy có thể là boundary phù hợp. Nhưng copy policy thuộc về API contract, không phải cơ chế pass-by-value tự động của Java.

chương tiếp theo dùng cùng value/reference model cho array.
