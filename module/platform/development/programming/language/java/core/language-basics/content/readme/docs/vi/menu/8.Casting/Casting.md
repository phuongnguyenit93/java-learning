# Chuyển đổi và ép kiểu

Ép kiểu (casting) là việc yêu cầu Java chuyển một giá trị hoặc nhìn một giá trị tham chiếu qua kiểu khác trong phạm vi các quy tắc của ngôn ngữ. Ép kiểu nguyên thủy và ép kiểu tham chiếu có mô hình tư duy khác nhau.

## <a id="primitive-casting">Ép kiểu nguyên thủy</a>

**Mở rộng kiểu nguyên thủy (widening primitive conversion)** thường chuyển sang kiểu có phạm vi biểu diễn rộng hơn và thường không cần ép kiểu tường minh:

```java
int x = 10;
long y = x;
```

**Thu hẹp kiểu (narrowing conversion)** có thể mất thông tin và thường cần ép kiểu:

```java
long x = 1000L;
int y = (int) x;
```

Nếu giá trị không nằm trong phạm vi kiểu đích, bit/giá trị có thể bị cắt bớt hoặc wrap theo quy tắc chuyển đổi; phép ép kiểu không tự kiểm tra “an toàn theo nghiệp vụ”.

### Mở rộng kiểu không đồng nghĩa luôn giữ nguyên độ chính xác

Nhiều phép mở rộng không cần ép kiểu tường minh vì kiểu đích biểu diễn được phạm vi rộng hơn theo quy tắc ngôn ngữ, nhưng điều đó không có nghĩa mọi giá trị đều giữ độ chính xác tuyệt đối:

```java
long exact = 9_007_199_254_740_993L;
double approximate = exact;
```

`long -> double` là phép mở rộng kiểu, nhưng `double` không thể biểu diễn chính xác mọi `long` lớn. Vì vậy “widening” là khái niệm chuyển đổi kiểu, không phải bảo đảm ở mức nghiệp vụ về độ chính xác.

### Thu hẹp kiểu có thể đổi giá trị

```java
int large = 130;
byte small = (byte) large; // -126
```

Ép kiểu nói với trình biên dịch rằng phép chuyển đổi được phép; nó không tự kiểm tra phạm vi. Nếu phạm vi có ý nghĩa nghiệp vụ, hãy kiểm tra trước hoặc dùng API kiểm tra overflow/phạm vi phù hợp.

### Biểu thức hằng là một ngoại lệ hữu ích

```java
byte a = 100;       // hợp lệ: giá trị hằng nằm trong phạm vi byte
int x = 100;
// byte b = x;      // không biên dịch dù lúc chạy x đang là 100
```

Trình biên dịch có thể chứng minh một hằng ở thời điểm biên dịch, nhưng không giả định một biến bất kỳ luôn nằm trong phạm vi.

## <a id="reference-upcast-downcast">Upcast và downcast tham chiếu</a>

Upcast từ kiểu con lên kiểu cha thường được thực hiện ngầm:

```java
Dog dog = new Dog();
Animal animal = dog;
```

Đối tượng không thay đổi; chỉ kiểu tĩnh của tham chiếu trở nên tổng quát hơn.

Downcast cần ép kiểu tường minh:

```java
Dog dogAgain = (Dog) animal;
```

Ép kiểu hợp lệ lúc biên dịch chưa bảo đảm đối tượng thực tế lúc chạy thuộc kiểu đích.

### Ép kiểu tham chiếu không biến đối tượng thành kiểu khác

```java
Animal animal = new Dog();
Dog dog = (Dog) animal;
```

Không có đối tượng mới và đối tượng `Dog` không bị “chuyển thành” kiểu khác. Phép ép kiểu chỉ yêu cầu Java kiểm tra rằng đối tượng thực tế có thể được nhìn qua kiểu tham chiếu đích.

`null` có thể ép sang kiểu tham chiếu mà không gây `ClassCastException`:

```java
Dog dog = (Dog) null; // hợp lệ, dog == null
```

Lỗi chỉ xuất hiện khi truy cập qua `dog` hoặc khi ép một đối tượng khác `null` nhưng không tương thích.
