---
video:
  url: ""
---

# Bức tranh tổng thể về nền tảng ngôn ngữ Java

<!--
VIDEO SCRIPT FORMAT

Section rules:
- Each H2 is one video section/chapter.
- The first section requires at least 1 Scene.
- From the second section onward, each section requires at least 1 Transition + 1 Scene.
- Step 6 may add more Scenes/Transitions when the Knowledge content needs them.

TRANSITION FORMAT

### Transition

**Time:** `MM:SS–MM:SS`

**Visual:**

Describe the transition, title card, or screen change.

**Script:**

Write the short bridge from the previous section to the current section.

**Purpose:**

Explain why this transition exists.

SCENE FORMAT

### Scene N — <optional scene title>

**Time:** `MM:SS–MM:SS`

**Visual:**

Describe what the viewer sees: slide, diagram, source code, terminal, API request/response, runtime output, highlight, or callout.

**Script:**

Write the narration/presentation script for this scene.

**Purpose:**

Explain what this scene teaches, demonstrates, or proves.
-->

## Nền tảng ngôn ngữ Java học gì và để làm gì?

<!-- VIDEO_SECTION -->

### Scene 1 — Java cơ bản không chỉ là học cú pháp

**Time:** `00:00–00:40`

**Visual:**

Mở IntelliJ với một file Java rất ngắn:

```java
public class Main {
    public static void main(String[] args) {
        int age = 20;

        if (age >= 18) {
            System.out.println("Adult");
        }
    }
}
```

Lần lượt highlight `int`, `age`, `20`, `if`, `System.out.println(...)` rồi zoom out toàn bộ chương trình.

**Script:**

Khi mới học Java, chúng ta rất dễ nghĩ rằng Java cơ bản chỉ là học cú pháp: khai báo biến thế nào, viết `if` ra sao, gọi một phương thức bằng cách nào.

Nhưng nếu chỉ nhớ cú pháp thì chúng ta mới biết cách gõ code, chưa chắc đã hiểu chương trình đang làm gì. Ngay trong ví dụ rất nhỏ này đã có nhiều câu hỏi: `20` là loại giá trị gì, tại sao biến `age` phải có kiểu `int`, điều kiện `age >= 18` tạo ra giá trị gì, và vì sao chương trình chỉ chạy câu lệnh bên trong `if` khi điều kiện đúng?

Mục tiêu của module Language Basics là xây dựng mô hình tư duy để trả lời được những câu hỏi đó, thay vì học từng keyword một cách rời rạc.

**Purpose:**

Đặt vấn đề cho toàn module: chuyển từ tư duy “nhớ cú pháp” sang “hiểu mô hình hoạt động của một chương trình Java”.

### Scene 2 — Năm câu hỏi nền tảng của một chương trình Java

**Time:** `00:40–01:35`

**Visual:**

Giữ đoạn code trên màn hình và lần lượt xuất hiện năm callout:

```text
1. Giá trị được biểu diễn như thế nào?
2. Java gắn kiểu cho dữ liệu ra sao?
3. Tên và biến tồn tại ở đâu?
4. Luồng thực thi được điều khiển như thế nào?
5. Dữ liệu đi qua lời gọi phương thức ra sao?
```

Mỗi callout highlight phần code tương ứng.

**Script:**

Để hiểu một chương trình Java cơ bản, mình sẽ luôn quay về năm câu hỏi lớn.

Thứ nhất, Java đang thao tác trên loại giá trị nào: số, ký tự, boolean hay một tham chiếu tới đối tượng. Thứ hai, giá trị đó có kiểu gì và compiler cho phép chúng ta làm gì với kiểu đó. Thứ ba, một biến hoặc một cái tên có thể được nhìn thấy ở phạm vi nào và tồn tại trong bao lâu. Thứ tư, chương trình quyết định chạy nhánh nào hoặc lặp bao nhiêu lần bằng cách nào. Và cuối cùng, khi gọi một phương thức, dữ liệu thực sự được truyền từ caller sang method như thế nào.

Những câu hỏi này liên kết với nhau. Vì vậy module này không được xem như một danh sách các bài học độc lập.

**Purpose:**

Tạo mental model cấp module dựa trên các vấn đề mà Java Language Basics phải giải quyết.

### Scene 3 — Lộ trình học là một chuỗi câu hỏi nối tiếp

**Time:** `01:35–03:05`

**Visual:**

Chuyển sang một sơ đồ dọc và reveal từng bước theo lời nói:

```text
Cấu trúc chương trình Java
        ↓
Primitive / Reference
        ↓
Variable / Scope / Lifetime
        ↓
null
        ↓
Wrapper / Boxing / Unboxing
        ↓
Expression / Operator / Casting
        ↓
Control Flow
        ↓
Array
        ↓
Method / Varargs / Pass-by-Value
        ↓
package / import
        ↓
Compile-time / Runtime Type Boundary
        ↓
Synthesis
```

Không cần mở source code trong scene này; tập trung vào flow và highlight node đang được nói tới.

**Script:**

Thứ tự của module được thiết kế như một chuỗi câu hỏi nối tiếp nhau.

Đầu tiên, chúng ta phải biết một chương trình Java được tổ chức ra sao. Sau đó mới đi vào loại giá trị mà Java thao tác: kiểu nguyên thủy và kiểu tham chiếu.

Khi đã có giá trị, câu hỏi tiếp theo là chúng được giữ trong biến như thế nào, biến nhìn thấy ở đâu và sống trong bao lâu. Với kiểu tham chiếu, chúng ta phải hiểu thêm `null` — tức là trường hợp một tham chiếu không trỏ tới đối tượng nào.

Sau đó là wrapper, boxing và unboxing để hiểu cách giá trị nguyên thủy tham gia vào những API dựa trên object. Từ đây chúng ta mới ghép giá trị thành biểu thức, dùng toán tử, thực hiện chuyển đổi kiểu và ép kiểu.

Khi đã tạo được biểu thức, chương trình cần quyết định đường chạy bằng `if`, `switch`, vòng lặp và các cơ chế điều khiển luồng. Tiếp theo là mảng để biểu diễn một dãy giá trị có kích thước cố định.

Cuối cùng, chúng ta tổ chức hành vi bằng phương thức, hiểu `varargs`, hiểu Java truyền bằng giá trị, rồi học cách `package` và `import` tổ chức tên. Tất cả được nối lại bằng một câu hỏi quan trọng: điều gì compiler có thể quyết định trước, và điều gì vẫn phải được kiểm tra khi chương trình đang chạy.

**Purpose:**

Cho người xem thấy thứ tự học có quan hệ nguyên nhân–kết quả, không phải một danh sách chapter tùy ý.

### Scene 4 — Compiler và runtime là hai góc nhìn xuyên suốt

**Time:** `03:05–04:00`

**Visual:**

Hiện sơ đồ hai cột:

```text
COMPILE TIME                     RUNTIME

Kiểu khai báo                    Giá trị thực tế
Khả năng gán                     Nhánh thực sự được chạy
Kiểm tra cú pháp / kiểu           Exception có thể xảy ra
Chọn overload                    Trạng thái chương trình
```

Sau đó show hai snippet ngắn:

```java
int number = "10";   // compile-time error
```

```java
Integer number = null;
int value = number;  // runtime failure
```

**Script:**

Một mental model sẽ xuất hiện lặp đi lặp lại trong toàn module là ranh giới giữa compile time và runtime.

Ví dụ, nếu mình gán một `String` vào biến `int`, compiler có thể từ chối ngay trước khi chương trình chạy. Nhưng có những trường hợp code vẫn compile hợp lệ mà lỗi chỉ xuất hiện khi chạy, chẳng hạn unboxing một `Integer` đang có giá trị `null`.

Khi hiểu được ranh giới này, chúng ta không còn nhìn lỗi Java chỉ dưới dạng “đoạn code này chạy hay không”, mà sẽ hỏi chính xác hơn: lỗi này có thể được phát hiện ở thời điểm biên dịch, hay chỉ khi runtime có dữ liệu và trạng thái thực tế?

**Purpose:**

Giới thiệu sớm trục tư duy compile-time/runtime sẽ được dùng lại ở casting, array, method overload, wrapper và type-system boundaries.

### Scene 5 — Kết quả cuối cùng của module

**Time:** `04:00–04:45`

**Visual:**

Quay lại sơ đồ tổng hợp, nhưng lần này gom thành một flow ngắn:

```text
Value
  ↓
Variable
  ↓
Expression
  ↓
Control Flow
  ↓
Array
  ↓
Method Call
  ↓
Name Organization
  ↓
Type Checks
```

Ở cuối màn hình hiện:

```text
Next: Source Code Structure
```

**Script:**

Sau khi hoàn thành Language Basics, mục tiêu không phải là nhớ hết mọi cú pháp Java. Mục tiêu là có thể theo dõi một chương trình từ giá trị, biến, biểu thức, luồng điều khiển, mảng, lời gọi phương thức, cách tổ chức tên cho tới những kiểm tra kiểu mà compiler và runtime thực hiện.

Khi có nền móng này, các module tiếp theo như OOP, Generics, Collections hay Exception sẽ không còn xuất hiện như những chủ đề hoàn toàn mới. Chúng sẽ mở rộng trực tiếp những câu hỏi mà chúng ta đã đặt ra ở đây.

Ở phần tiếp theo, chúng ta bắt đầu từ thứ cơ bản nhất: một file Java thực sự được tổ chức như thế nào và các thành phần trong source code liên hệ với nhau ra sao.

**Purpose:**

Chốt learning outcome của chapter mở đầu và tạo handoff tự nhiên sang `SourceCodeStructure`.

