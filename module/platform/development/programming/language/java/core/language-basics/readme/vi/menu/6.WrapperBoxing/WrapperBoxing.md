# Kiểu bao, đóng hộp và mở hộp

Kiểu nguyên thủy không phải đối tượng. Nhưng nhiều Java API, đặc biệt collection generic, làm việc với kiểu tham chiếu. Kiểu bao tạo cầu nối như `int ↔ Integer`, `double ↔ Double`.

## <a id="wrapper-types">Kiểu bao là gì?</a>

Mỗi kiểu nguyên thủy có kiểu bao tương ứng:

```text
byte    ↔ Byte
short   ↔ Short
int     ↔ Integer
long    ↔ Long
float   ↔ Float
double  ↔ Double
char    ↔ Character
boolean ↔ Boolean
```

Kiểu bao là đối tượng bất biến với ngữ nghĩa giá trị riêng. Vì là kiểu tham chiếu, kiểu bao có thể là `null`, tham gia API generic và có tính đồng nhất đối tượng khác với giá trị nguyên thủy.

### Vì sao kiểu bao tồn tại?

Nhiều API Java cần **kiểu tham chiếu**, trong khi kiểu nguyên thủy không phải đối tượng. Tham số kiểu generic là ví dụ quen thuộc:

```java
List<Integer> numbers = new ArrayList<>();
// List<int> không hợp lệ
```

Kiểu bao còn cung cấp API tiện ích như parse, chuyển đổi và các hằng:

```java
int value = Integer.parseInt("42");
Integer boxed = Integer.valueOf(42);
```

Khi API không cần ngữ nghĩa đối tượng/có thể `null`, kiểu nguyên thủy thường đơn giản hơn và tránh chi phí cấp phát/boxing không cần thiết.

## <a id="boxing-unboxing">Đóng hộp và mở hộp</a>

**Boxing** chuyển giá trị nguyên thủy sang kiểu bao; **unboxing** lấy giá trị nguyên thủy từ kiểu bao.

```java
Integer boxed = 10; // tự động đóng hộp
int value = boxed;  // mở hộp
```

Trình biên dịch chèn chuyển đổi tương ứng trong ngữ cảnh hợp lệ.

Tự động đóng hộp tiện nhưng không làm kiểu nguyên thủy và kiểu bao trở thành cùng một kiểu. Việc chọn overload, `null`, tính đồng nhất và hiệu năng vẫn có thể khác.

### Đóng hộp có thể xảy ra ở đâu?

```java
Integer a = 10;          // ngữ cảnh phép gán
List<Integer> xs = List.of(1, 2, 3); // đối số nguyên thủy được đóng hộp

void accept(Integer x) { }
accept(10);              // ngữ cảnh gọi phương thức
```

Mở hộp thường xuất hiện khi kiểu bao được dùng trong biểu thức cần giá trị nguyên thủy:

```java
Integer count = 10;
int next = count + 1; // count được mở hộp trước phép toán
```

Vì chuyển đổi có thể được trình biên dịch chèn ngầm, hãy đặc biệt cẩn thận ở ranh giới có `null`, overload hoặc đoạn mã lặp nhiều lần cần hiệu năng cao.

## <a id="wrapper-caching">Bộ nhớ đệm của kiểu bao</a>

Một số giá trị kiểu bao có thể được cache, khiến ví dụ dùng `==` đôi khi cho kết quả bất ngờ:

```java
Integer a = 100;
Integer b = 100;
a == b // true: 100 nằm trong khoảng cache bắt buộc
```

nhưng với giá trị khác:

```java
Integer x = 1000;
Integer y = 1000;
x == y // không nên dựa vào kết quả tính đồng nhất
```

Khi muốn so sánh giá trị kiểu bao, dùng `equals` hoặc mở hộp theo hợp đồng phù hợp; không dựa vào tính đồng nhất do cache.

Đặc biệt, với `Integer`, Java bảo đảm cache ít nhất cho khoảng `-128..127` khi boxing theo các API/quy tắc chuẩn liên quan. Tuy nhiên mã nghiệp vụ không nên phụ thuộc vào tính đồng nhất do cache:

```java
Integer a = 100;
Integer b = 100;
System.out.println(a == b);      // true theo cache bắt buộc
System.out.println(a.equals(b)); // true theo giá trị
```

Mô hình tư duy đúng là: kiểu bao là đối tượng có ngữ nghĩa giá trị; `==` trên hai tham chiếu kiểm tra tính đồng nhất, không phải so sánh bằng theo giá trị số.

## <a id="unboxing-null">Mở hộp giá trị `null`</a>

Kiểu bao có thể là `null`:

```java
Integer boxed = null;
int value = boxed; // NullPointerException
```

Mở hộp cần một đối tượng kiểu bao thực sự để lấy giá trị nguyên thủy. Vì vậy API dùng kiểu bao có thể `null` có thêm một kiểu lỗi mà kiểu nguyên thủy không có.

Rủi ro này có thể ẩn trong biểu thức tưởng như vô hại:

```java
Integer count = null;

// cả ba đều có thể kích hoạt unboxing
int x = count;
int y = count + 1;
if (count > 0) { }
```

Nếu `null` mang ý nghĩa nghiệp vụ hợp lệ, mã nên xử lý trạng thái vắng mặt trước khi mở hộp. Nếu `null` không hợp lệ, kiểm tra sớm tại ranh giới thay vì để NPE xuất hiện xa nguồn.

Chương tiếp theo xem các toán tử kết hợp giá trị và những chuyển đổi ngầm có thể xảy ra trong biểu thức.
