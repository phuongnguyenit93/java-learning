# Truyền dữ liệu bằng giá trị

Java **luôn truyền tham số bằng giá trị (pass-by-value)**. Sự nhầm lẫn xuất hiện vì với đối tượng, giá trị được sao chép là **giá trị tham chiếu**.

## <a id="java-pass-by-value">Java luôn truyền bằng giá trị</a>

Khi gọi phương thức, tham số nhận một bản sao của giá trị đối số.

```java
void increment(int x) {
    x++;
}
```

Bên gọi không thay đổi vì tham số `x` chỉ là một bản sao của giá trị nguyên thủy.

Với tham chiếu đối tượng:

```java
void use(User user) { }
```

tham số `user` cũng nhận một bản sao — nhưng bản sao này là tham chiếu tới cùng đối tượng.

### Mô hình tư duy: ô biến của bên gọi không được truyền vào phương thức

```text
biến bên gọi                       biến tham số
┌──────────────┐     sao chép      ┌──────────────┐
│ reference R  │ ───────────────→  │ reference R  │
└──────────────┘                   └──────────────┘
        │                                  │
        └──────────────→ đối tượng User ←─────┘
```

Phương thức nhận **một biến mới trong frame của lời gọi**. Nếu đối số là giá trị nguyên thủy, giá trị đó được sao chép. Nếu đối số là tham chiếu, giá trị tham chiếu được sao chép. Không trường hợp nào phương thức nhận trực tiếp ô biến của bên gọi.

Đây là lý do Java được mô tả nhất quán là pass-by-value, không cần ngoại lệ “primitive by value, object by reference”.

## <a id="reference-copy-mutation">Sao chép tham chiếu và thay đổi đối tượng</a>

```java
void rename(User user) {
    user.setName("B");
}
```

Bên gọi thấy đối tượng bị đổi vì cả tham chiếu của bên gọi và tham chiếu của tham số đều trỏ tới cùng đối tượng `User`.

Điều này **không biến Java thành pass-by-reference**. Phương thức không nhận quyền thay đổi ô biến của bên gọi; nó chỉ nhận một giá trị tham chiếu được sao chép.

Mảng cũng theo cùng quy tắc:

```java
void changeFirst(int[] values) {
    values[0] = 99;
}
```

Bên gọi thấy phần tử đổi vì bên gọi và tham số cùng nhận diện một đối tượng mảng. Đây là **thay đổi dùng chung qua alias**, không phải pass-by-reference.

Đối tượng bất biến giúp quan sát dễ hơn:

```java
void append(String text) {
    text = text + "!";
}
```

`String` bất biến nên biểu thức tạo giá trị/đối tượng khác và gán lại tham số; bên gọi không thấy biến của mình đổi.

## <a id="reassignment-vs-mutation">Gán lại biến khác thay đổi đối tượng</a>

```java
void replace(User user) {
    user = new User("new");
}
```

Gán lại tham số chỉ đổi biến tham số cục bộ. Tham chiếu của bên gọi vẫn trỏ đối tượng cũ.

So sánh:

```text
user.setName(...)
→ thay đổi đối tượng dùng chung

user = new User(...)
→ chỉ gán lại tham số cục bộ
```

Đây là mô hình tư duy nền tảng cho aliasing, sao chép phòng vệ và thiết kế API phương thức.

### Ví dụ `swap` kinh điển

```java
void swap(User a, User b) {
    User temp = a;
    a = b;
    b = temp;
}
```

Phương thức chỉ hoán đổi hai **tham số cục bộ**. Hai biến của bên gọi không đổi tham chiếu.

### Hệ quả đối với thiết kế API

Khi phương thức nhận đối tượng có thể thay đổi, hãy làm rõ hợp đồng:

- phương thức chỉ đọc đối tượng;
- phương thức thay đổi đối tượng bên gọi truyền vào;
- phương thức tạo và trả đối tượng mới;
- phương thức giữ tham chiếu để dùng lâu dài.

Nếu không muốn bên gọi và bên được gọi chia sẻ trạng thái có thể thay đổi, sao chép phòng vệ có thể là ranh giới phù hợp. Nhưng chính sách sao chép thuộc về hợp đồng API, không phải cơ chế pass-by-value tự động của Java.

Chương tiếp theo chuyển từ dữ liệu và lời gọi sang cách Java tổ chức tên kiểu bằng `package` và `import`.
