# Lớp (class) và đối tượng (object)

Java dùng `class` để mô tả **một loại thực thể có trạng thái và hành vi chung**, còn `object` là một đối tượng cụ thể được tạo khi chương trình chạy. Cách tổ chức này giúp dữ liệu đi cùng với những hành vi và quy tắc chịu trách nhiệm bảo vệ dữ liệu đó, thay vì để các giá trị và thao tác liên quan tồn tại rời rạc.

Trong mô-đun này, hãy giữ mô hình `BankAccount` làm ví dụ xuyên suốt: lớp định nghĩa cấu trúc và quy tắc chung; mỗi đối tượng tài khoản có danh tính và trạng thái riêng. Các chương sau tiếp tục cùng mô hình đó khi học khởi tạo, quá trình tạo đối tượng, sao chép, dùng chung tham chiếu và tính bất biến.

Mạch học của phần Kiến thức bám theo lộ trình như sau. Chặng về khởi tạo được triển khai qua ba chương riêng để tách rõ cơ chế, thứ tự và toàn bộ quá trình tạo đối tượng:

```text
Lớp khác đối tượng thế nào?
Lớp và đối tượng
        ↓
Đối tượng được tạo với trạng thái hợp lệ bằng cách nào?
Hàm khởi tạo và trạng thái hợp lệ
        ↓
this / super đại diện cho ngữ cảnh nào?
this, super và chuỗi khởi tạo
        ↓
Ai được phép truy cập thành viên?
Phạm vi truy cập thành viên
        ↓
Thành viên nào thuộc lớp, thành viên nào thuộc từng đối tượng?
Thành viên cấp lớp, thành viên cấp đối tượng và final
        ↓
Khởi tạo lớp và từng đối tượng diễn ra ở đâu, theo thứ tự nào?
Khối khởi tạo → Thứ tự khởi tạo → Quá trình tạo đối tượng
        ↓
Lớp lồng nhau và lớp nội bộ giữ ngữ cảnh gì?
Lớp lồng nhau và lớp nội bộ
        ↓
Mọi lớp Java thông thường có lớp gốc chung nào?
Object – lớp gốc chung
        ↓
Một tập giá trị hữu hạn có kiểu rõ ràng được mô hình hóa ra sao?
Kiểu liệt kê (enum)
        ↓
Sao chép đối tượng thực sự sao chép điều gì?
Cách sao chép đối tượng
        ↓
Nhiều tham chiếu tới cùng một đối tượng có thể thay đổi gây gì?
Dùng chung tham chiếu và trạng thái có thể thay đổi
        ↓
Làm sao tạo đối tượng an toàn hơn khi chia sẻ?
Tính bất biến và sao chép phòng vệ
        ↓
Làm sao nối toàn bộ các khái niệm thành một mô hình thống nhất?
Tổng hợp mô hình lớp và đối tượng
```

Các thuật ngữ chính liên hệ với nhau như sau:

```text
class
→ định nghĩa kiểu, cấu trúc trạng thái và hành vi

đối tượng / object / instance
→ một thực thể cụ thể tồn tại khi chương trình chạy

hàm khởi tạo / constructor
→ khai báo đặc biệt tham gia quá trình tạo đối tượng và thiết lập trạng thái ban đầu

khởi tạo / initialization
→ quá trình thiết lập giá trị hoặc trạng thái ban đầu cho lớp hay đối tượng

this / super
→ biểu diễn ngữ cảnh của đối tượng hiện tại và lớp cha

static / instance
→ phân biệt trạng thái/hành vi thuộc lớp với trạng thái/hành vi thuộc từng đối tượng

tham chiếu / dùng chung tham chiếu / sao chép / tính bất biến
→ giải thích đối tượng được tham chiếu, dùng chung, sao chép hoặc bảo vệ khỏi thay đổi như thế nào
```

## <a id="class-object-model">Lớp và đối tượng là gì?</a>

### KHÁI NIỆM

`class` là định nghĩa kiểu và phần triển khai chung: trường dữ liệu, phương thức, hàm khởi tạo, kiểu lồng nhau và logic khởi tạo.

`object` là một thể hiện của lớp khi chương trình chạy, có:

- danh tính riêng;
- trạng thái riêng của từng đối tượng;
- hành vi được định nghĩa bởi lớp và kiểu thực tế khi chạy.

```java
BankAccount first = new BankAccount("A-01");
BankAccount second = new BankAccount("A-02");
```

`first` và `second` dùng cùng định nghĩa `BankAccount` nhưng là hai đối tượng có danh tính riêng và có thể mang trạng thái khác nhau.

### VÌ SAO GOM TRẠNG THÁI VÀ HÀNH VI VÀO ĐỐI TƯỢNG?

Giả sử số dư tài khoản chỉ là một biến số nguyên bị nhiều đoạn mã sửa trực tiếp. Mỗi nơi sử dụng đều phải tự nhớ các quy tắc như “không được rút quá số dư”, và chỉ cần một nơi quên là trạng thái có thể mất hợp lệ. Lớp cho phép đặt trạng thái cùng các hành vi bảo vệ điều kiện hợp lệ vào một ranh giới rõ ràng:

```java
class BankAccount {
    private int balance;

    void withdraw(int amount) {
        if (amount <= 0 || amount > balance) {
            throw new IllegalArgumentException();
        }
        balance -= amount;
    }
}
```

Điểm quan trọng không chỉ là Java có cú pháp `class`. Đối tượng trở thành nơi sở hữu trạng thái và những quy tắc giữ cho trạng thái đó có ý nghĩa. Mô-đun OOP sẽ đi sâu hơn về đóng gói và thiết kế hướng đối tượng; mô-đun này trước hết xây nền về mô hình lớp/đối tượng và quá trình một đối tượng được tạo, sử dụng và chia sẻ.

## <a id="fields-methods-state">Trạng thái, hành vi và danh tính</a>

Trường của đối tượng biểu diễn trạng thái riêng của từng đối tượng. Phương thức của đối tượng làm việc trên đối tượng hiện tại thông qua tham chiếu ngầm `this`.

Thành viên `static` thuộc về lớp, không phải trạng thái riêng của từng đối tượng.

Hai đối tượng có thể có cùng giá trị trường dữ liệu nhưng vẫn có danh tính khác nhau. Đây là lý do “cùng một đối tượng” và “hai đối tượng có giá trị bằng nhau” là hai câu hỏi khác nhau; mô-đun `object-contract` đi sâu vào phần đó.

## <a id="object-reference-lifecycle">Tham chiếu và thời gian tồn tại của đối tượng</a>

Biến kiểu tham chiếu giữ **giá trị tham chiếu**, không chứa đối tượng “bên trong biến”. Nhiều biến có thể cùng tham chiếu một đối tượng.

Đối tượng có thể tiếp tục được chương trình truy cập sau khi một biến cục bộ ra khỏi phạm vi nếu vẫn còn tham chiếu khác dẫn tới nó. Garbage Collector quyết định khả năng thu hồi bộ nhớ dựa trên tính còn-truy-cập-được (reachability), không dựa trên việc một tên biến vừa hết phạm vi.

Vòng đời tài nguyên là vấn đề khác: file/socket phải được đóng có chủ ý; không chờ GC để giải phóng tài nguyên bên ngoài JVM.

Chương tiếp theo bắt đầu từ thời điểm một đối tượng được tạo: **hàm khởi tạo làm gì để thiết lập trạng thái hợp lệ?**
