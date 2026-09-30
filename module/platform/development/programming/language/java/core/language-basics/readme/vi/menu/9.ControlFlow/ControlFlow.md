# Luồng điều khiển chương trình

Sau khi có giá trị và biểu thức, chương trình cần quyết định **câu lệnh nào chạy, chạy bao nhiêu lần và khi nào dừng**. Đó là vai trò của luồng điều khiển.

## <a id="branching-model">Rẽ nhánh với `if` và `switch`</a>

`if/else` phù hợp khi điều kiện là một biểu thức boolean linh hoạt:

```java
if (score >= 90) {
    grade = "A";
} else if (score >= 80) {
    grade = "B";
}
```

`switch` phù hợp khi một giá trị chọn (selector) được đối chiếu với tập `case` rõ ràng. Với Java hiện đại, `switch` có thể là câu lệnh hoặc biểu thức.

Đừng chọn chỉ vì cú pháp ngắn hơn; hãy chọn cấu trúc làm mô hình quyết định dễ đọc nhất.

### `if/else`: điều kiện phải thực sự là boolean

Java không có cơ chế tự coi số hay tham chiếu là true/false:

```java
int count = 1;
// if (count) { } // không biên dịch được

if (count > 0) { }
```

Điều này làm điều kiện tường minh và giúp trình biên dịch kiểm tra kiểu.

### Câu lệnh `switch` và fall-through

`switch` dạng dấu `:` có thể fall-through nếu không `break`:

```java
switch (level) {
    case 1:
        enableBasic();
        break;
    case 2:
        enableAdvanced();
        break;
    default:
        disableAll();
}
```

Fall-through đôi khi có chủ ý, nhưng phải rất rõ. Dạng mũi tên hiện đại tránh fall-through ngoài ý muốn và phù hợp khi mỗi `case` độc lập.

## <a id="loop-control">Vòng lặp, `break` và `continue`</a>

Các vòng lặp chính:

```text
for
→ phù hợp khi có phần khởi tạo/điều kiện/cập nhật rõ

while
→ lặp khi điều kiện còn đúng

do-while
→ thân vòng lặp chạy ít nhất một lần
```

`break` không nhãn thoát khỏi vòng lặp hoặc câu lệnh `switch` gần nhất có thể nhận nó; `continue` bỏ phần còn lại của lượt lặp hiện tại và chuyển sang lần tiếp theo của vòng lặp.

`break/continue` có nhãn tồn tại nhưng thường chỉ nên dùng khi nó làm ý định của vòng lặp lồng nhau rõ hơn; nếu luồng quá khó theo dõi, tách thành phương thức nhỏ có thể tốt hơn.

### Chọn vòng lặp theo mục đích

```java
for (int i = 0; i < 3; i++) {
    System.out.println(i);
} // số lần lặp và biến đếm rõ ràng

int count = 3;
while (count > 0) {
    count--;
} // lặp theo điều kiện

do {
    attempt();
} while (retry); // body phải chạy ít nhất một lần
```

### Vòng lặp thực sự chạy theo thứ tự nào?

`for` cổ điển có chu kỳ thực thi:

```text
khởi tạo             // một lần
      ↓
điều kiện
 ├─ false → thoát vòng lặp
 └─ true
      ↓
thân vòng lặp
      ↓
cập nhật
      ↓
quay lại điều kiện
```

Vì vậy `continue` trong `for` cổ điển chuyển luồng tới **biểu thức cập nhật**, rồi mới kiểm tra điều kiện lần tiếp theo:

```java
for (int i = 0; i < 3; i++) {
    if (i == 1) {
        continue; // vẫn chạy i++ trước lần kiểm tra tiếp theo
    }
    System.out.println(i);
}
```

`while` kiểm tra điều kiện trước thân vòng lặp, nên thân có thể chạy 0 lần. `do-while` chạy thân trước rồi mới kiểm tra điều kiện, nên thân chạy ít nhất 1 lần.

Mô hình này quan trọng hơn việc chỉ nhớ cú pháp, vì nó giải thích `continue`, điều kiện kết thúc và tác dụng phụ trong phần điều kiện/cập nhật.

### Definite assignment qua nhánh và vòng lặp

Khi có nhiều đường thực thi, trình biên dịch phải chứng minh biến cục bộ đã được gán trên **mọi đường có thể đi tới điểm đọc**:

```java
int x;
if (condition) {
    x = 1;
} else {
    x = 2;
}
System.out.println(x); // hợp lệ: mọi nhánh đều gán x
```

Ngược lại, thân `while` có thể không chạy lần nào:

```java
int x;
while (condition) {
    x = 1;
}
// System.out.println(x); // không bảo đảm x đã được gán
```

Đây là phần mở rộng của quy tắc definite assignment đã học ở chương Biến, phạm vi và vòng đời.

## <a id="switch-expression">Biểu thức `switch` và `yield`</a>

Biểu thức `switch` trả về giá trị:

```java
String label = switch (status) {
    case NEW -> "new";
    case DONE -> "done";
    default -> "other";
};
```

Với khối `case` cần nhiều câu lệnh, `yield` trả giá trị cho biểu thức `switch`:

```java
int result = switch (code) {
    case 1 -> 10;
    default -> {
        int computed = compute();
        yield computed;
    }
};
```

Dạng mũi tên tránh fall-through ngoài ý muốn của `switch` dạng dấu `:`.

Biểu thức `switch` phải có khả năng tạo ra một giá trị cho mọi đường thực thi cần thiết. Điều này khiến bảng quyết định rõ hơn và trình biên dịch có thể kiểm tra tính đầy đủ trong các trường hợp phù hợp.

```java
int fee = switch (type) {
    case BASIC -> 10;
    case PREMIUM -> 0;
    default -> throw new IllegalArgumentException("unsupported");
};
```

`yield` chỉ dùng để trả giá trị từ khối của biểu thức `switch`; nó không giống `return` khỏi phương thức.

### Java 21 còn mở rộng `switch`

Java 21 còn cho phép `switch` phân loại giá trị tham chiếu bằng pattern. Ở chặng này chỉ cần nhận biết khả năng đó; **type pattern, kiểu lúc chạy và phạm vi của biến pattern** được giải thích sau khi người học đã có mô hình hệ thống kiểu ở chương Ranh giới giữa kiểm tra lúc biên dịch và lúc chạy.

## <a id="control-flow-pitfalls">Giữ luồng điều khiển dễ đọc</a>

Các dấu hiệu luồng điều khiển khó bảo trì:

- `if` lồng quá sâu;
- điều kiện boolean dài có tác dụng phụ;
- `switch` fall-through không chủ ý;
- vòng lặp có quá nhiều `break/continue` ở nhiều tầng;
- cùng một điều kiện được lặp ở nhiều nơi.

Guard clause, tách phương thức hoặc mô hình dữ liệu/đa hình tốt hơn đôi khi làm luồng rõ hơn.

### Guard clause để giảm lồng nhau

```java
if (user == null) {
    return;
}
if (!user.isActive()) {
    return;
}
process(user);
```

So với nhiều tầng `if`, guard clause thường đặt đường không hợp lệ/thoát lên trước và giữ đường xử lý chính phẳng hơn.

### Điều kiện kết thúc vòng lặp phải nhìn thấy được

Với `while`/`do-while`, hãy xác định rõ trạng thái nào làm điều kiện thay đổi. Vòng lặp vô hạn không phải luôn sai, nhưng nếu có chủ ý thì thường cần cơ chế dừng/ngắt rõ ở tầng phù hợp.

Chương tiếp theo giới thiệu mảng; tại đó enhanced-for sẽ được đặt trong ngữ cảnh một tập phần tử cụ thể.
