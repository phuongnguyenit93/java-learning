# String Pool

Vì String immutable, JVM có thể an toàn chia sẻ một số String có cùng nội dung thay vì luôn tạo object mới. Đây là ý tưởng nền tảng của **String pool**.

## <a id="string-pool-model">String Pool là gì?</a>

String pool lưu các reference chuẩn hóa cho một số String, đặc biệt là String literal và giá trị được `intern()`.

### WHY — vì sao pool tồn tại?

String xuất hiện cực nhiều trong source code: class name, key, message, path, token, constant... Nếu literal giống nhau luôn tạo object mới, runtime sẽ giữ nhiều object có cùng content mà không mang thêm semantic value.

Vì String immutable, Java có thể canonicalize một số value:

```text
equal immutable String values
        ↓
chia sẻ một canonical identity an toàn
        ↓
String pool
```

```java
String a = "java";
String b = "java";
```

Vì hai literal có cùng nội dung được intern, `a` và `b` tham chiếu cùng canonical pooled String object.

Với String literal, đây không chỉ là một optimization ngẫu nhiên: literal và constant String được intern theo contract của Java. Cách JVM lưu pool ở đâu trong memory là implementation detail; learner không nên gắn mental model với PermGen hay một data structure nội bộ cụ thể.

Điểm cần nhớ là pool là **identity optimization/canonicalization**, không thay đổi hợp đồng so sánh text: muốn so nội dung vẫn dùng `equals`.

Pool cũng không phải một `Map` public mà application có thể iterate, remove hay mutate. Hãy dùng nó như mental model cho canonical identity.

## <a id="literal-vs-new">Literal và new String</a>

```java
String a = "java";
String b = new String("java");
```

`a` trỏ tới pooled literal, còn `new String(...)` yêu cầu tạo String object mới.

Vì vậy:

```java
a == b      // false
a.equals(b) // true
```

Không dùng `new String("...")` nếu chỉ cần một literal bình thường; nó thường thêm object không cần thiết.

`new` không làm String trở nên mutable:

```java
String x = new String("java");
x.toUpperCase();

System.out.println(x); // java
```

Constructor chỉ thay đổi cách object được tạo, không thay đổi immutability contract.

## <a id="pool-identity">String Pool và Identity</a>

Compile-time constant concatenation có thể được gộp thành cùng pooled literal:

```java
String a = "ja" + "va";
String b = "java";
```

Trong khi concatenation phụ thuộc runtime value không có cùng guarantee về identity.

### final chưa chắc là compile-time constant

Một `final` variable chỉ tham gia constant expression khi nó thực sự là **constant variable**:

```java
final String prefix = "ja";
String a = prefix + "va";
String b = "java";

System.out.println(a == b); // true
```

Nhưng value chỉ biết ở runtime thì khác:

```java
final String prefix = args.length > 0 ? args[0] : "ja";
String a = prefix + "va";
String b = "java";

System.out.println(a.equals(b)); // content question
```

`final` nghĩa là reference không được gán lại; nó không tự động biến mọi runtime expression thành compile-time constant.

### Rule thực dụng

```text
cùng object?
→ ==

cùng text?
→ equals

cần canonical pooled reference có chủ ý?
→ intern(), với trade-off rõ ràng
```

Đừng viết business logic dựa trên pooled identity. Pooling là optimization/runtime hành vi; **text equality vẫn là `equals`**.

chương tiếp theo tập trung trực tiếp vào quy tắc so sánh String.
