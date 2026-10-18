# Tổng hợp nền tảng ngôn ngữ Java

## <a id="language-basics-synthesis">Ghép các nền tảng thành một mô hình</a>

Sau từng chương riêng lẻ, bước cuối là nối chúng thành một chuỗi suy luận thống nhất. Khi đọc một đoạn Java, có thể đi theo thứ tự:

```text
Mã này nằm ở đâu trong cấu trúc chương trình?
        ↓
Biểu thức/biến đang làm việc với giá trị và kiểu nào?
        ↓
Tên này có nằm trong phạm vi sử dụng không?
        ↓
Có chuyển đổi kiểu, đóng hộp/mở hộp hoặc `null` nào tham gia không?
        ↓
Toán tử và luồng điều khiển quyết định đường chạy nào?
        ↓
Mảng hoặc đối số phương thức đang mang những giá trị nào?
        ↓
Trình biên dịch đã quyết định điều gì?
        ↓
Khi chạy còn điều kiện nào cần kiểm tra?
```

Mô hình này quan trọng hơn việc nhớ một danh sách cú pháp rời rạc, vì nó giúp giải thích cả trường hợp thành công lẫn lỗi lúc biên dịch/lúc chạy.

### Một chuỗi suy luận khi đọc mã Java

Khi gặp một biểu thức khó, hỏi theo thứ tự:

1. Biến/biểu thức đang có **kiểu tĩnh** gì?
2. Giá trị đang là nguyên thủy hay tham chiếu?
3. Đang ở ngữ cảnh chuyển đổi nào: phép gán, toán tử, lời gọi phương thức hay ép kiểu tường minh?
4. Trình biên dịch quyết định gì ngay lúc biên dịch?
5. Thời điểm chạy còn cần biết đối tượng/giá trị thực tế nào?
6. Thay đổi đang tác động lên ô biến hay lên đối tượng được nhiều tham chiếu cùng chia sẻ?

Chuỗi câu hỏi này nối gần như toàn bộ `language-basics` thành một mô hình tư duy thống nhất thay vì các quy tắc rời rạc.

Kết thúc mô-đun, hãy giữ chuỗi tư duy:

```text
cấu trúc mã nguồn
→ giá trị nguyên thủy / giá trị tham chiếu
→ biến / phạm vi / vòng đời
→ null
→ kiểu bao / đóng hộp / mở hộp
→ biểu thức / toán tử / ép kiểu
→ luồng điều khiển
→ mảng
→ phương thức / varargs / truyền bằng giá trị
→ package / import
→ kiểu lúc biên dịch / kiểu lúc chạy
→ mô hình tổng hợp xuyên suốt
```

## <a id="end-to-end-value-flow">Theo dõi một giá trị xuyên suốt chương trình</a>

Hãy lấy một giá trị và theo dõi nó qua các bước: được viết dưới dạng literal hoặc sinh ra từ biểu thức, được gán vào biến, có thể được chuyển đổi/đóng hộp, được đặt vào mảng, truyền vào phương thức bằng pass-by-value, rồi ảnh hưởng đến trạng thái hoặc nhánh luồng điều khiển.

Ví dụ cụ thể:

```java
static void addBonus(int[] scores, int bonus) {
    if (scores.length > 0) {
        scores[0] = scores[0] + bonus;
    }
}

int base = 10;                 // literal 10 → biến base kiểu int
int bonus = base + 5;          // biểu thức tạo giá trị int 15
int[] scores = {base};         // giá trị 10 được đặt vào phần tử mảng
addBonus(scores, bonus);       // sao chép giá trị tham chiếu của scores và giá trị int 15
boolean passed = scores[0] > 20; // thay đổi thấy được ở bên gọi → biểu thức boolean → có thể điều khiển nhánh
```

Theo dõi chuỗi này:

```text
literal 10
→ base = 10
→ base + 5 tạo bonus = 15
→ scores[0] ban đầu = 10
→ lời gọi sao chép tham chiếu của scores + giá trị 15 vào tham số
→ phương thức thay đổi đối tượng mảng: scores[0] thành 25
→ bên gọi vẫn trỏ cùng mảng nên quan sát 25
→ scores[0] > 20 tạo boolean true
→ boolean đó có thể quyết định nhánh luồng điều khiển tiếp theo
```

Ví dụ này nối trực tiếp khai báo, biểu thức, thay đổi mảng, truyền dữ liệu qua phương thức, pass-by-value và kết quả điều khiển luồng trong một đường đi duy nhất.

Với giá trị tham chiếu, luôn tách hai câu hỏi:

1. biến đang giữ **giá trị tham chiếu nào**;
2. đối tượng được tham chiếu có **trạng thái nào**.

Sự tách biệt này giải thích phép gán, aliasing, thay đổi trạng thái, `null`, tính hiệp biến của mảng, ép kiểu và nhiều hành vi lúc chạy mà người mới thường nhầm lẫn.

## <a id="language-basics-next-boundaries">Ranh giới với các mô-đun tiếp theo</a>

Language Basics chỉ xây dựng nền tảng. Các chủ đề tiếp theo sẽ mở rộng những ranh giới đã được giới thiệu:

- **Lớp và đối tượng / OOP**: trạng thái, hành vi, kế thừa, đa hình và phân phối lời gọi động;
- **Generics**: an toàn kiểu và kiểu có tham số ở thời điểm biên dịch;
- **Collections**: cấu trúc dữ liệu động và hợp đồng của collection;
- **Exception**: mô hình hóa và xử lý luồng lỗi;
- **Numbers**: overflow, số chấm động, BigInteger, BigDecimal và làm tròn;
- **String**: mô hình và API xử lý dữ liệu văn bản.

Nếu một nội dung ở Language Basics chỉ được giới thiệu như một ranh giới, hãy giữ đúng mức nền tảng và chuyển sang mô-đun chuyên trách khi cần đào sâu.
