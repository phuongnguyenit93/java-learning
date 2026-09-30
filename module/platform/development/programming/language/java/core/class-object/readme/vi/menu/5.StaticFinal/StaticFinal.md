# Thành viên cấp lớp, thành viên cấp đối tượng và final

`static` và `final` giải quyết hai câu hỏi khác nhau. `static` nói **thành viên thuộc lớp hay từng đối tượng**; `final` giới hạn việc gán lại hoặc ghi đè tùy vị trí sử dụng. Phân biệt hai trục này giúp tránh nhầm “dùng chung ở cấp lớp” với “không thể thay đổi”.

## <a id="static-vs-instance">Thành viên static và thành viên của đối tượng</a>

Trường và phương thức của đối tượng gắn với từng đối tượng cụ thể:

```java
bankAccount.balance
bankAccount.withdraw(...)
```

Trường và phương thức `static` gắn với lớp thay vì một đối tượng cụ thể:

```java
BankAccount.MAX_LIMIT
BankAccount.createDefault()
```

Phương thức `static` không có `this` vì không có đối tượng hiện tại mặc định.

## <a id="final-variable-reference">Ý nghĩa của final</a>

Một biến `final` chỉ được nhận giá trị **một lần** theo quy tắc gán chắc chắn của Java. Giá trị có thể được gán ngay khi khai báo hoặc được gán sau đó đúng một lần trên mọi đường khởi tạo hợp lệ.

```java
final int x = 10;
final List<String> names = new ArrayList<>();
```

Với biến tham chiếu, `final` ngăn `names` trỏ sang danh sách khác, nhưng **không làm danh sách phía sau trở thành bất biến**:

```java
names.add("A"); // vẫn có thể hợp lệ
```

Đây là điểm phân biệt quan trọng khi học đối tượng bất biến (immutable object) sau này.

`final` còn có ý nghĩa khác tùy loại khai báo:

```text
biến final
→ giá trị/tham chiếu không được gán lại sau lần gán hợp lệ duy nhất

phương thức final
→ lớp con không được ghi đè phương thức đó

lớp final
→ không thể tạo lớp con từ lớp đó
```

Chương này chỉ cần thiết lập ranh giới khái niệm; ghi đè phương thức và thiết kế kế thừa được học sâu trong mô-đun OOP.

## <a id="static-initialization">Khởi tạo static</a>

Phần khởi tạo trường `static` và khối khởi tạo `static` chạy trong quá trình khởi tạo lớp theo thứ tự được xác định bởi vị trí trong mã nguồn và vòng đời của lớp cha.

Trạng thái `static` được dùng chung giữa mọi đối tượng, nên trạng thái `static` có thể thay đổi sẽ tạo phụ thuộc toàn cục và vấn đề đồng thời lớn hơn trạng thái riêng của từng đối tượng.

## <a id="constants-design">Hằng số và hằng số tại thời điểm biên dịch</a>

Không phải mọi `static final` đều là hằng số tại thời điểm biên dịch.

```java
static final int MAX_DAILY_WITHDRAWALS = 3;          // compile-time constant
static final int CONFIGURED_LIMIT = Integer.parseInt("3"); // không phải compile-time constant
```

Giá trị đầu là kiểu nguyên thủy được khởi tạo từ biểu thức hằng nên có thể được chèn trực tiếp vào bytecode của mã sử dụng. Giá trị thứ hai cần gọi phương thức nên chỉ được xác định khi chương trình chạy.

Hằng số tại thời điểm biên dịch phải đáp ứng các quy tắc cụ thể về kiểu nguyên thủy/`String` và biểu thức hằng. Điều này ảnh hưởng tới việc chèn trực tiếp giá trị và một số hành vi khi thư viện thay đổi hằng số nhưng mã sử dụng chưa được biên dịch lại.

Tên `UPPER_SNAKE_CASE` là quy ước phổ biến cho hằng số, nhưng ngữ nghĩa của hằng số quan trọng hơn kiểu đặt tên.

Chương tiếp theo đi vào các khối khởi tạo nằm ngoài thân hàm khởi tạo.
