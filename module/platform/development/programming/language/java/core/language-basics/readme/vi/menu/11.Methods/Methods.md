# Phương thức và lựa chọn lời gọi

Phương thức đặt tên cho một hành vi có thể tái sử dụng. Nhưng một lời gọi Java không chỉ là “tìm phương thức cùng tên”: trình biên dịch còn phải xét chữ ký, các phép chuyển đổi và quy tắc chọn phương thức nạp chồng.

## <a id="method-signature">Khai báo và chữ ký phương thức</a>

Trong ngữ cảnh nạp chồng, chữ ký phương thức chủ yếu gồm **tên phương thức + kiểu tham số**. Chỉ khác kiểu trả về thì không đủ để tạo một phương thức nạp chồng khác.

```java
int parse(String value) { return Integer.parseInt(value); }
long parse(String value) { return Long.parseLong(value); } // vẫn không hợp lệ: chỉ khác kiểu trả về
```

Tham số là **biến tham số của từng lần gọi**, nhận bản sao giá trị đối số và có phạm vi sử dụng trong thân phương thức theo quy tắc của Java. `return` kết thúc phương thức và cung cấp kết quả nếu kiểu trả về không phải `void`.

### Cấu trúc của một phương thức

```java
public int add(int left, int right) {
    return left + right;
}
```

Có thể đọc theo thứ tự:

```text
modifier → kiểu trả về → tên phương thức → danh sách tham số → thân phương thức
```

Tham số phương thức khai báo **hợp đồng đầu vào ở mức kiểu**. Kiểu trả về khai báo loại giá trị mà bên gọi nhận lại. `void` nghĩa là phương thức không trả về một giá trị kết quả; nó không có nghĩa phương thức không thể tạo tác dụng phụ.

### Phương thức khác `void` không được kết thúc bình thường mà thiếu giá trị

Nếu phương thức khai báo kiểu trả về khác `void`, trình biên dịch phải chứng minh thân phương thức **không thể kết thúc bình thường mà không trả giá trị**:

```java
int find(boolean found) {
    if (found) {
        return 1;
    }
    // lỗi biên dịch: đường này có thể đi tới cuối phương thức mà không return int
}
```

Sửa bằng cách bảo đảm mọi đường thực thi kết thúc bình thường đều trả giá trị:

```java
int find(boolean found) {
    if (found) {
        return 1;
    }
    return 0;
}
```

Một đường thực thi kết thúc bằng `throw` không cần `return` tiếp theo vì chương trình không kết thúc bình thường trên đường đó:

```java
int requireValue(boolean valid) {
    if (!valid) {
        throw new IllegalStateException("invalid");
    }
    return 1;
}
```

Đây là cùng kiểu **phân tích luồng điều khiển ở thời điểm biên dịch** đã gặp trong definite assignment: trình biên dịch phân tích các đường thực thi, không đoán ý định ở thời điểm chạy.

### `return` kết thúc phương thức hiện tại

`return` không chỉ có thể cung cấp một giá trị trả về; nó còn kết thúc việc thực thi phương thức ngay tại vị trí đó:

```java
if (!valid) {
    return;
}

process(); // chỉ chạy khi valid
```

So với `break` và `continue` đã học ở phần Luồng điều khiển, `return` có phạm vi thoát lớn hơn: nó rời khỏi **toàn bộ phương thức hiện tại**.

Java không cho phép nạp chồng chỉ bằng cách đổi tên tham số hoặc kiểu trả về:

```java
void save(String name) { }
// void save(String value) { } // cùng chữ ký
```

## <a id="method-invocation-conversion">Chuyển đổi khi gọi phương thức</a>

Để một phương thức ứng viên có thể áp dụng, đối số có thể trải qua các chuyển đổi mà Java cho phép trong ngữ cảnh gọi phương thức, như:

- chuyển đổi đồng nhất (identity conversion);
- mở rộng kiểu nguyên thủy;
- mở rộng kiểu tham chiếu;
- đóng hộp/mở hộp trong giai đoạn phù hợp;
- chuyển đổi `varargs` ở giai đoạn cuối.

Không phải mọi phép ép kiểu hợp lệ đều được trình biên dịch tự thực hiện trong lời gọi phương thức.

Ví dụ:

```java
void use(long value) { }
use(10); // int được mở rộng thành long

void boxed(Integer value) { }
boxed(10); // đóng hộp
```

Nhưng trình biên dịch không tự thu hẹp kiểu tùy ý:

```java
void useByte(byte value) { }
int x = 1;
// useByte(x); // không biên dịch được
useByte((byte) x);
```

Điều này quan trọng vì quá trình chọn phương thức nạp chồng dùng **các phép chuyển đổi mà ngôn ngữ cho phép**, không dựa vào việc “giá trị ở thời điểm chạy tình cờ vẫn an toàn”.

## <a id="overload-resolution-phases">Các bước chọn phương thức nạp chồng</a>

Trình biên dịch xét các phương thức nạp chồng theo những giai đoạn ưu tiên. Một mô hình tư duy hữu ích:

```text
1. số lượng tham số cố định, chưa cần boxing/varargs
        ↓ nếu chưa có ứng viên phù hợp
2. cho phép boxing/unboxing phù hợp
        ↓ nếu vẫn chưa có
3. dùng varargs như phương án cuối
```

Đặc tả chi tiết hơn mô hình này, nhưng thứ tự trên giải thích được nhiều câu hỏi kiểu “mở rộng kiểu, boxing hay varargs được ưu tiên?”.

Ví dụ kinh điển:

```java
void pick(long x) { System.out.println("long"); }
void pick(Integer x) { System.out.println("Integer"); }
void pick(int... x) { System.out.println("varargs"); }

pick(1); // long
```

`int -> long` được giải quyết ở giai đoạn số lượng tham số cố định mà chưa cần boxing, nên được chọn trước boxing và varargs.

## <a id="most-specific-overload">Phương thức nạp chồng cụ thể nhất</a>

Nếu nhiều ứng viên cùng áp dụng, trình biên dịch cố chọn phương thức **cụ thể hơn** theo các quy tắc về kiểu.

```java
void print(Object x) { }
void print(String x) { }

print("java"); // chọn overload nhận String
```

Không phải “phương thức khai báo sau” hay “phương thức có thân tốt hơn” sẽ thắng; đây là quyết định ở thời điểm biên dịch dựa trên kiểu.

Nếu trình biên dịch không thể tìm một ứng viên hợp lệ cụ thể hơn, lời gọi sẽ trở nên mơ hồ thay vì “chọn đại”:

```java
void use(CharSequence x) { }
void use(Serializable x) { }

String value = "x";
// use(value); // mơ hồ vì String phù hợp cả hai và không overload nào cụ thể hơn overload kia
```

## <a id="null-overload-ambiguity">`null` và lời gọi nạp chồng mơ hồ</a>

Literal `null` tương thích với kiểu tham chiếu. Nếu các overload không có quan hệ “cụ thể hơn” rõ ràng:

```java
void print(String x) { }
void print(Integer x) { }

print(null); // mơ hồ
```

trình biên dịch không thể chọn một overload cụ thể.

Ép kiểu tường minh có thể loại bỏ sự mơ hồ nếu đó thực sự là ý định của mã.

Nếu các overload có quan hệ subtype rõ ràng, trình biên dịch vẫn có thể chọn phương thức cụ thể nhất:

```java
void print(Object x) { }
void print(String x) { }

print(null); // chọn String vì String cụ thể hơn Object
```

Vì vậy `null` không tự động gây mơ hồ; vấn đề chỉ xuất hiện khi không có ứng viên nào thắng rõ theo quy tắc chọn phương thức cụ thể nhất.

## <a id="method-call-evaluation">Thứ tự đánh giá đối số</a>

Java đánh giá các biểu thức đối số từ trái sang phải trước khi thân phương thức chạy.

Tác dụng phụ trong đối số vẫn có thể làm mã khó hiểu:

```java
call(i++, update(i));
```

Khi thứ tự có ý nghĩa nghiệp vụ, tách phép tính ra biến riêng thường rõ hơn.

Đối tượng nhận lời gọi (receiver) và các biểu thức đối số được đánh giá trước khi thân phương thức chạy. Phương thức chỉ nhận **giá trị kết quả** của các biểu thức đó; nó không nhận chính biểu thức hay “ô biến” của bên gọi.

Điều này nối trực tiếp sang chương truyền bằng giá trị.

## <a id="recursion-stack">Đệ quy và ngăn xếp lời gọi</a>

Phương thức đệ quy gọi lại chính nó hoặc tham gia một chu kỳ lời gọi. Mỗi lần gọi cần một frame mới trên ngăn xếp.

Nếu không có điều kiện dừng hoặc độ sâu quá lớn, chương trình có thể gặp `StackOverflowError`.

Đệ quy phù hợp tự nhiên với một số bài toán cây hoặc chia để trị, nhưng vòng lặp có thể đơn giản hơn và an toàn hơn về ngăn xếp cho quá trình lặp tuyến tính dài.

Ví dụ:

```java
int factorial(int n) {
    if (n <= 1) {
    return 1;          // điều kiện dừng
    }
    return n * factorial(n - 1); // bước đệ quy
}
```

Mô hình tư duy:

```text
factorial(3)
→ frame n=3 chờ factorial(2)
→ frame n=2 chờ factorial(1)
→ frame n=1 trả 1
→ lần lượt quay lui: 2 → 6
```

Mỗi lời gọi giữ trạng thái riêng. Java không bảo đảm tối ưu hóa tail-call, nên độ sâu đệ quy vẫn là một giới hạn thực tế ở thời điểm chạy.

Chương tiếp theo xem cú pháp đặc biệt cho phương thức nhận số đối số thay đổi: `varargs`.
