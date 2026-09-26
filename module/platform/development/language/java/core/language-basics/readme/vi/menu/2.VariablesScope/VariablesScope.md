# Biến và Phạm vi

Hiểu type/value chưa đủ; cần biết **tên biến nhìn thấy ở đâu và trạng thái tồn tại trong ngữ cảnh nào**. Scope là quy tắc của mã nguồn; lifetime của object/reference lại là một câu hỏi khác.

## <a id="variable-kinds-and-lifetime">Các loại Variable</a>

Những loại variable thường gặp:

```text
local variable
→ khai báo trong block/method

parameter
→ nhận value khi method/constructor được gọi

instance field
→ trạng thái của từng object

static field
→ trạng thái gắn với class
```

Local/parameter tồn tại theo execution frame/ngữ cảnh của lời gọi. Field tồn tại như một phần trạng thái của object/class.

Đừng đồng nhất lifetime của một **reference variable** với lifetime của object mà nó trỏ tới. Object có thể còn reachable từ nơi khác sau khi local variable ra khỏi scope.

## <a id="scope-and-shadowing">Scope và Shadowing</a>

Scope quyết định tên nào có thể được dùng tại một vị trí mã nguồn.

Một tên ở scope bên trong có thể che một tên khác ở scope ngoài trong những trường hợp Java cho phép. Ví dụ parameter có thể shadow field:

```java
void setName(String name) {
    this.name = name;
}
```

`this.name` là field; `name` là parameter.

Shadowing hợp lệ không có nghĩa luôn dễ đọc. Nếu nhiều scope lồng nhau dùng cùng tên với ý nghĩa khác, mã dễ gây nhầm.

## <a id="definite-assignment">Definite Assignment</a>

Compiler phải chứng minh local variable đã được gán trên mọi control-flow path trước khi đọc:

```java
int x;
if (condition) {
    x = 1;
}
System.out.println(x); // có thể không compile
```

Đây là **compile-time guarantee**, không phải runtime initialization giống field default value.

chương tiếp theo nối primitive với object-oriented APIs thông qua wrapper type và boxing.
