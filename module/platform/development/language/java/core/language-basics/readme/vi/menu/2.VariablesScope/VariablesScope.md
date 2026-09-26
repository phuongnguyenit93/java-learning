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

### Declaration, initialization và assignment

Ba khái niệm này liên quan nhưng không giống nhau:

```java
int count;      // declaration
count = 10;     // assignment đầu tiên / initialization theo nghĩa sử dụng
count = 20;     // reassignment

int size = 5;   // declaration + initializer
```

Với field, JVM/object initialization cung cấp default value trước khi constructor logic chạy. Với local variable, compiler yêu cầu chương trình tự chứng minh rằng value đã được gán trước khi đọc.

### `var` vẫn là static typing

Từ Java 10, local variable có initializer có thể dùng `var` để compiler **suy ra static type**:

```java
var count = 10;             // int
var name = "Java";          // String
var user = new User("A");   // User
```

`var` không biến Java thành dynamic typing và không có nghĩa variable "không có type". Sau khi compiler suy ra type, variable vẫn tuân theo type đó:

```java
var value = "Java";
// value = 10; // không compile: value có static type String
```

Compiler cần initializer đủ thông tin để suy ra type:

```java
// var x;        // không được: không có initializer
// var y = null; // không được: không có type cụ thể để suy ra
```

`var` chủ yếu là cú pháp cho **local variable inference**. Nó không thay thế type ở field, return type hoặc ordinary method parameter:

```java
class Sample {
    // var field;            // không hợp lệ
    // var create() { }      // không hợp lệ
    // void use(var value) { } // ordinary method parameter không hợp lệ
}
```

Practical rule: dùng `var` khi initializer làm type đủ rõ và tên variable diễn đạt intent tốt; giữ explicit type khi inference khiến người đọc phải đoán.

### Scope khác lifetime

```java
User saved;
{
    User local = new User("A");
    saved = local;
}
// tên local đã out of scope,
// nhưng object vẫn reachable qua saved
```

`scope` trả lời **tên có thể được nhắc tới ở đâu trong source**. `lifetime/reachability` trả lời **state/object còn tồn tại hoặc còn reachable trong runtime tới lúc nào**. Không nên dùng hai từ này thay thế nhau.

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

### Block scope

Một local variable chỉ visible trong block mà nó được khai báo và các nested block hợp lệ sau điểm khai báo:

```java
if (condition) {
    int result = 10;
    System.out.println(result);
}
// result không còn trong scope ở đây
```

Loop initializer cũng có scope riêng:

```java
for (int i = 0; i < 3; i++) {
    System.out.println(i);
}
// i không tồn tại ở đây
```

### Shadowing và readability

```java
class User {
    private String name;

    void setName(String name) {
        this.name = name;
    }
}
```

Trường hợp parameter shadow field là phổ biến và `this` làm intent rõ. Nhưng việc tái dùng cùng tên cho nhiều local ở nested scope thường làm người đọc phải liên tục xác định "name nào" đang được dùng.

### Flow scope của pattern variable

Pattern variable không chỉ phụ thuộc block `{}`; compiler còn dùng control flow để xác định nơi variable chắc chắn đã được bind:

```java
if (value instanceof String text) {
    System.out.println(text.length());
}
// text không tồn tại ở đây
```

Với `&&`, vế phải chỉ chạy khi pattern match nên pattern variable có thể dùng ở đó:

```java
if (value instanceof String text && !text.isBlank()) {
    System.out.println(text);
}
```

Guard clause cũng có thể mở rộng flow scope:

```java
if (!(value instanceof String text)) {
    return;
}

System.out.println(text.length());
```

Ở dòng cuối, compiler biết execution chỉ đi tiếp nếu pattern đã match. Đây là ví dụ quan trọng cho thấy **scope có thể phụ thuộc control-flow guarantee**, không chỉ vị trí dấu ngoặc nhọn.

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

Compiler phân tích các path có thể đi qua:

```java
int x;
if (condition) {
    x = 1;
} else {
    x = 2;
}
System.out.println(x); // hợp lệ: mọi path đều gán x
```

Ngược lại:

```java
int x;
while (condition) {
    x = 1;
}
// System.out.println(x); // loop có thể không chạy lần nào
```

Điểm quan trọng là compiler không "đoán" intent runtime. Nó cần một guarantee theo control-flow rules.

chương tiếp theo nối primitive với object-oriented APIs thông qua wrapper type và boxing.
