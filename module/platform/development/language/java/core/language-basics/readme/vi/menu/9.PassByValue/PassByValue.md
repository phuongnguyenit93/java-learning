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
void use(User user) { ... }
```

parameter `user` cũng nhận một bản copy — nhưng bản copy này là reference tới cùng object.

## <a id="reference-copy-mutation">Copy Reference vẫn thấy Mutation</a>

```java
void rename(User user) {
    user.setName("B");
}
```

bên gọi thấy object bị đổi vì cả bên gọi reference và parameter reference đều trỏ tới cùng `User` object.

Điều này **không biến Java thành pass-by-reference**. Method không nhận quyền thay đổi variable slot của bên gọi; nó nhận một reference value được copy.

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

chương tiếp theo dùng cùng value/reference model cho array.
