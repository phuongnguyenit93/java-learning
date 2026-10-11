<a id="back-to-top"></a>

# Nền tảng OOP: Vì sao cần đối tượng và chúng cộng tác ra sao

## Menu
- [Lập trình hướng đối tượng (OOP): Khái niệm và phạm vi](#oop-what)
- [Từ thủ tục và dữ liệu tách rời đến nhu cầu phân chia trách nhiệm](#oop-before)
- [Mục đích của OOP: Đưa quy tắc về đúng đối tượng chịu trách nhiệm](#oop-why)
- [Bản đồ khái niệm OOP: Đối tượng, nguyên lý và quan hệ](#oop-solution)
- [Object và Class — Đối tượng khác với bản mô tả tạo đối tượng](#oop-class-vs-object)
- [Tư duy OOP và cơ chế class, interface trong Java](#oop-boundary)
- [Điểm xuất phát và lộ trình từ đối tượng đến thiết kế](#oop-learning-path)

## <a id="oop-what">Lập trình hướng đối tượng (OOP): Khái niệm và phạm vi</a>

<details>
<summary>Xem chi tiết</summary>

**Lập trình hướng đối tượng** (*Object-Oriented Programming*, viết tắt là **OOP**) là cách tổ chức chương trình xoay quanh **các đối tượng** (*object*). Mỗi đối tượng có **dữ liệu riêng**, **những hành động nó có thể thực hiện** và **trách nhiệm** đối với phần việc mà nó quản lý.

Ví dụ, trong một ứng dụng ngân hàng, ta có thể hình dung các đối tượng như `BankAccount` (tài khoản), `Customer` (khách hàng), `Transaction` (giao dịch). Thay vì để một nơi xử lý mọi dữ liệu, ta giao cho mỗi đối tượng quản lý công việc phù hợp với nó. Những đối tượng này phối hợp với nhau thông qua các hành động mà chúng cung cấp.

> **Ý tưởng cốt lõi:** Khi cần làm một việc, hãy hỏi “Đối tượng nào chịu trách nhiệm thực hiện việc này?” và “Các đối tượng cần phối hợp với nhau như thế nào?”.

### 1. Bắt đầu bằng một tình huống quen thuộc

Giả sử bạn lập trình chức năng **rút tiền từ tài khoản ngân hàng**. Chương trình cần biết số dư hiện tại, số tiền người dùng muốn rút và quy tắc xác định giao dịch có hợp lệ hay không.

Nếu chỉ nhìn theo trình tự xử lý, ta nghĩ: kiểm tra số tiền → kiểm tra số dư → trừ tiền. Khi suy nghĩ theo OOP, ta hỏi thêm: **Ai nên chịu trách nhiệm bảo đảm số dư tài khoản luôn hợp lệ?**

Câu trả lời hợp lý là **chính đối tượng tài khoản**. Phần giao diện gửi yêu cầu `account.withdraw(amount)`; tài khoản tự kiểm tra yêu cầu và chỉ thay đổi số dư khi được phép.

```mermaid
flowchart LR
    A[Người dùng yêu cầu rút tiền] --> B[Ứng dụng ngân hàng]
    B --> C[account.withdraw(amount)]
    C --> D{Yêu cầu hợp lệ và đủ tiền?}
    D -->|Có| E[Trừ số dư và trả thành công]
    D -->|Không| F[Giữ nguyên số dư và trả thất bại]
```

Ứng dụng ngân hàng không cần tự sửa số dư bên trong tài khoản. Nó chỉ biết cách **gửi yêu cầu** và **nhận kết quả**. Quy tắc bảo vệ số dư tập trung ở đối tượng thực sự sở hữu trách nhiệm đó.

### 2. Một đối tượng có những đặc điểm nào?

Xét tài khoản của Lan đang có 100.000 đồng:

| Thành phần | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| **Trạng thái (*state*)** | Dữ liệu mô tả tình trạng hiện tại | Chủ tài khoản: Lan; số dư: 100.000 đồng |
| **Hành vi (*behavior*)** | Những việc đối tượng có thể thực hiện | `deposit(soTien)`, `withdraw(soTien)` |
| **Định danh (*identity*)** | Tính riêng biệt của từng đối tượng | Tài khoản Lan và tài khoản Minh là hai tài khoản khác nhau |
| **Trách nhiệm (*responsibility*)** | Điều đối tượng cần quản lý và bảo đảm | Không cho rút quá số dư; không nhận số tiền rút âm |

Định danh rất quan trọng: hai tài khoản có thể **cùng số dư** nhưng vẫn là **hai đối tượng khác nhau**. Khi Lan rút tiền, số dư của Minh không vì thế mà thay đổi.

#### Class và object khác nhau ra sao?

- **Class (lớp):** bản mô tả cấu trúc và hành vi chung cho một loại đối tượng, ví dụ `BankAccount`.
- **Object (đối tượng):** một thể hiện cụ thể được tạo từ lớp, ví dụ `lanAccount` và `minhAccount`.

Bạn có thể hình dung class như **bản thiết kế**, còn object như **mỗi sản phẩm được tạo theo bản thiết kế đó**. Mỗi sản phẩm có trạng thái riêng. Việc hai đối tượng cùng một lớp không có nghĩa là chúng dùng chung một biến số dư.

### 3. Ví dụ Java: đối tượng tự bảo vệ trạng thái

Đoạn chương trình dưới đây tạo tài khoản của Lan với 100.000 đồng, rút 30.000 đồng, sau đó thử rút tiếp 120.000 đồng.

```java
public class Main {
    public static void main(String[] args) {
        BankAccount account = new BankAccount("Lan", 100_000);

        System.out.println(account.withdraw(30_000));
        System.out.println(account.getBalance());

        System.out.println(account.withdraw(120_000));
        System.out.println(account.getBalance());
    }
}

class BankAccount {
    private final String owner;
    private long balance;

    public BankAccount(String owner, long openingBalance) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Tên chủ tài khoản không hợp lệ");
        }
        if (openingBalance < 0) {
            throw new IllegalArgumentException("Số dư ban đầu không thể âm");
        }
        this.owner = owner;
        this.balance = openingBalance;
    }

    public boolean withdraw(long amount) {
        if (amount <= 0 || amount > balance) {
            return false;
        }
        balance -= amount;
        return true;
    }

    public long getBalance() {
        return balance;
    }

    public String getOwner() {
        return owner;
    }
}
```

**Kết quả:**

```text
true
70000
false
70000
```

| Thời điểm | Điều xảy ra | Số dư sau xử lý |
| --- | --- | ---: |
| Ban đầu | Tài khoản Lan được tạo | 100.000 |
| Rút 30.000 | Số tiền hợp lệ, tài khoản cho phép rút | 70.000 |
| Rút 120.000 | Vượt số dư, tài khoản từ chối | **70.000 (không đổi)** |

Quan sát đoạn mã, bạn sẽ thấy:

1. `balance` là **trạng thái** thuộc tài khoản.
2. `withdraw()` là **hành vi** kiểm tra yêu cầu rồi quyết định có thay đổi trạng thái không.
3. `balance` được khai báo `private`, nên nơi khác không thể tùy ý gán `account.balance = -5000`.

Quy tắc “số dư không âm” là một ví dụ về **bất biến** (*invariant*): điều phải tiếp tục đúng sau mỗi thao tác hợp lệ. Đây là ví dụ giản lược để hiểu trách nhiệm của đối tượng; hệ thống ngân hàng thật còn phải xử lý lưu trữ, giao dịch và các yêu cầu chạy đồng thời.

### 4. Vì sao OOP hữu ích khi chương trình lớn dần?

Tưởng tượng cùng chức năng rút tiền được sử dụng tại **ATM**, **ứng dụng điện thoại** và **quầy giao dịch**. Nếu mỗi nơi tự viết cách trừ tiền, có nơi sẽ kiểm tra thiếu một trường hợp; khi quy tắc thay đổi, ta phải tìm và sửa nhiều chỗ.

Khi quy tắc được đặt trong `BankAccount.withdraw()`, các nơi sử dụng đều gọi cùng một hành vi. Trách nhiệm trở nên rõ ràng hơn: màn hình xử lý việc nhập và hiển thị, còn tài khoản chịu trách nhiệm kiểm tra và cập nhật số dư.

Nhờ cách tổ chức đó, OOP có thể giúp:

- **Dễ hiểu và bảo trì:** biết đối tượng nào chịu trách nhiệm cho từng quy tắc.
- **Giới hạn ảnh hưởng khi thay đổi:** sửa xử lý bên trong một đối tượng mà những nơi gọi qua giao diện ổn định ít bị ảnh hưởng.
- **Tái sử dụng:** nhiều phần của chương trình cùng sử dụng những đối tượng và hợp đồng chung.
- **Dễ mở rộng và kiểm thử:** thay thế thành phần cụ thể hoặc kiểm tra riêng một trách nhiệm.

Các lợi ích này phụ thuộc vào **chất lượng thiết kế**. Có thật nhiều class chưa chắc đã tạo ra chương trình OOP tốt.

### 5. Bốn khái niệm thường được nhắc đến trong OOP

Bạn chưa cần học sâu cả bốn khái niệm để hiểu ý tưởng ban đầu, nhưng nên nhận biết chúng dùng để giải quyết vấn đề gì.

| Khái niệm | Câu hỏi nó giúp giải quyết | Ví dụ |
| --- | --- | --- |
| **Encapsulation — Đóng gói** | Ai được phép thay đổi trạng thái, theo quy tắc nào? | `balance` là `private`; rút tiền thông qua `withdraw()` |
| **Abstraction — Trừu tượng hóa** | Người dùng thành phần cần biết gì, chi tiết nào có thể che đi? | Có thể gọi `withdraw()` mà không cần biết bên trong nó kiểm tra bằng câu lệnh gì |
| **Inheritance — Kế thừa** | Khi tồn tại quan hệ “là một loại của”, lớp con có thể nhận và mở rộng đặc điểm chung ra sao? | `SavingsAccount` có thể là một loại `BankAccount` nếu nghiệp vụ phù hợp |
| **Polymorphism — Đa hình** | Làm sao gọi một hành vi chung nhưng mỗi loại đối tượng thực hiện theo cách riêng? | Gọi `pay()` để thanh toán bằng thẻ hoặc ví điện tử |

#### Ví dụ ngắn về đa hình và sự phối hợp

Giả sử cửa hàng hỗ trợ nhiều phương thức thanh toán. Thay vì buộc quy trình đặt hàng biết chi tiết của từng nhà cung cấp, ta đặt ra **hợp đồng chung**:

```java
interface PaymentMethod {
    boolean pay(long amount);
}

class CheckoutService {
    boolean checkout(PaymentMethod paymentMethod, long total) {
        return paymentMethod.pay(total);
    }
}
```

Đối tượng `CardPayment` và `EWalletPayment` có thể cùng triển khai `PaymentMethod`, nhưng mỗi đối tượng có logic riêng cho `pay()`. `CheckoutService` chỉ cần biết nó đang cộng tác với một đối tượng có khả năng thanh toán theo hợp đồng.

```text
                  PaymentMethod
                       |
CheckoutService -> pay(total)
                       |
             +---------+---------+
             |                   |
        CardPayment       EWalletPayment
           (thẻ)             (ví điện tử)
```

Ví dụ cho thấy OOP không chỉ là dữ liệu và phương thức nằm chung một lớp. Một hệ thống còn được hình thành từ **nhiều đối tượng gửi yêu cầu cho nhau**, mỗi đối tượng quản lý phần trách nhiệm của mình. Việc ghép những đối tượng cộng tác như vậy được gọi là *composition*; bạn không nhất thiết phải xây dựng cây kế thừa phức tạp.

### 6. OOP và lập trình theo thủ tục khác nhau như thế nào?

Cả hai cách tiếp cận đều có thể giải quyết cùng một bài toán. Điểm khác biệt thường nằm ở **cách tổ chức dữ liệu và trách nhiệm**.

| Góc nhìn | Theo thủ tục | Theo hướng đối tượng |
| --- | --- | --- |
| Câu hỏi thiết kế | “Cần hàm nào, thực hiện các bước ra sao?” | “Đối tượng nào chịu trách nhiệm, chúng cộng tác ra sao?” |
| Cách tổ chức | Hàm xử lý dữ liệu được truyền vào | Đối tượng quản lý trạng thái và công bố hành vi |
| Minh họa cách gọi | `withdraw(accountData, amount)` | `account.withdraw(amount)` |
| Thế mạnh thường gặp | Luồng xử lý tuần tự, đơn giản, rõ ràng | Ranh giới trách nhiệm giữa nhiều thành phần |

Chỉ đổi cú pháp từ `withdraw(accountData, amount)` sang `account.withdraw(amount)` **không tự động tạo ra một thiết kế tốt**. Điều đáng quan tâm là các quy tắc nghiệp vụ có được đặt đúng chỗ, trạng thái có được bảo vệ và trách nhiệm có hợp lý không.

Lập trình theo thủ tục vẫn phù hợp với nhiều chương trình, nhất là tác vụ nhỏ hoặc quy trình tính toán tuyến tính. OOP là một lựa chọn tổ chức phần mềm, không phải lời giải bắt buộc cho mọi bài toán.

### 7. Những hiểu lầm thường gặp

- **“OOP có nghĩa là mọi thứ đều phải là class.”** Không. Hãy tạo lớp khi cần một trách nhiệm rõ ràng; không cần thêm lớp cho mọi hàm nhỏ.
- **“Đối tượng phải là vật tồn tại ngoài đời.”** Không. `Validator`, `PaymentPolicy` hoặc `Scheduler` đều có thể là các đối tượng hữu ích.
- **“Đóng gói chỉ là thêm từ khóa `private`.”** `private` là công cụ. Mục tiêu thật sự là bảo vệ trạng thái và quy tắc của đối tượng.
- **“Đã học OOP thì bắt buộc phải dùng kế thừa.”** Không. Giao diện và composition thường rất hữu ích; chỉ kế thừa khi quan hệ thực sự phù hợp.
- **“Viết theo OOP thì tự động an toàn khi nhiều luồng cùng chạy.”** Không. Nếu đối tượng có trạng thái dùng chung, vẫn phải thiết kế cách xử lý truy cập đồng thời.

### 8. Thử tự trả lời ba câu hỏi

Giả sử `lanAccount` và `minhAccount` đều được tạo từ `BankAccount`, mỗi tài khoản có 100.000 đồng.

1. **Lan rút 20.000 đồng thì Minh có bị giảm tiền không?** Không, vì hai tài khoản là hai đối tượng có trạng thái riêng.
2. **Ai phải từ chối khi số tiền rút lớn hơn số dư?** Đối tượng tài khoản, vì nó chịu trách nhiệm bảo vệ số dư của mình.
3. **Màn hình có cần biết tên biến lưu số dư và cách cập nhật nó không?** Không cần; màn hình sử dụng hành vi `withdraw()` và kết quả được trả về.

Khi bắt đầu thiết kế một hệ thống theo OOP, hãy tự hỏi: **Có những đối tượng nào? Mỗi đối tượng chịu trách nhiệm về điều gì? Chúng cần gọi nhau ra sao để hoàn thành một công việc?** Đó là điểm xuất phát để quyết định nên tạo class, thuộc tính, phương thức hay giao diện nào.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-before">Từ thủ tục và dữ liệu tách rời đến nhu cầu phân chia trách nhiệm</a>

<details>
<summary>Xem chi tiết</summary>

Lập trình thủ tục có thể tổ chức chương trình thành các thủ tục xử lý và cấu trúc dữ liệu riêng biệt. Cách này vẫn hiệu quả trong nhiều bài toán, không phải một lựa chọn “thấp hơn” OOP.

OOP hữu ích hơn khi bài toán có nhiều thực thể hoặc đối tượng cộng tác, mỗi bên có vòng đời, trách nhiệm và hành vi tương tác đáng kể.

Lối thủ tục có thể biểu diễn `AccountData` riêng và gọi `withdraw(account, amount)` để xử lý; đây là phương án sáng sủa cho nhiều bài toán. OOP đặc biệt có ích khi tài khoản, lịch sử, chính sách thanh toán và thông báo phát triển thành những trách nhiệm cần cộng tác, mỗi phần có cam kết riêng.

**So sánh trên cùng một nhiệm vụ, không so sánh nhãn ngôn ngữ.** Ở phương án thủ tục có kiểm soát, chương trình giữ `AccountData` và một hàm `withdraw(account, amount)`; tất cả nơi gọi dùng chung hàm. A=100, rút30 vẫn cho 70 và cách này hoàn toàn có thể đúng, dễ kiểm thử. Vấn đề chỉ xuất hiện nếu màn hình, tác vụ nền và thanh toán **mỗi bên tự viết** kiểm tra đủ tiền: khi quy tắc đổi từ cho phép số dư còn 0 sang phải giữ tối thiểu 20, người sửa có ba điểm dễ bỏ sót.

Phương án hướng đối tượng đặt `withdraw` sau ranh giới trách nhiệm của Account: các bên gửi yêu cầu và nhận kết quả. Khi thay quy tắc, bên sở hữu hành vi được sửa, bên gọi không cần đọc cấu trúc số dư. **Không kết luận OOP luôn ít code hơn:** nếu một hàm thủ tục duy nhất đã tập trung và bảo vệ quy tắc tốt, chuyển nó thành class chỉ để đổi cú pháp không tự mang lại lợi ích. **Tự kiểm tra:** đâu là nguyên nhân làm ba nơi phải sửa: “thủ tục” hay “quy tắc bị nhân bản”?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-why">Mục đích của OOP: Đưa quy tắc về đúng đối tượng chịu trách nhiệm</a>

<details>
<summary>Xem chi tiết</summary>

Khi dữ liệu và các quy tắc xử lý liên quan nằm rải rác khắp hệ thống, một thay đổi nghiệp vụ có thể ảnh hưởng tới nhiều thành phần không đáng phải sửa.

OOP hướng tới đặt trạng thái và hành vi liên quan trong đúng ranh giới trách nhiệm, giúp khoanh vùng tác động của thay đổi.

Khi quy tắc rút tiền được sao chép vào màn hình, dịch vụ và tác vụ nền, một thay đổi giới hạn số dư phải sửa ở nhiều nơi. Đặt trách nhiệm kiểm tra rút tiền tại đối tượng quản lý tài khoản giúp các bên gọi cùng một hợp đồng thay vì tự viết lại quy tắc.

**Theo dấu nguyên nhân và hậu quả.** Giả sử ban đầu chính sách cho phép rút đến số dư bằng 0; A đang có 100. Khi yêu cầu mới đòi luôn để lại ít nhất 20, rút 90 **trước đây được chấp nhận**, nay **phải từ chối**. Nếu màn hình cập nhật điều kiện còn tác vụ nền vẫn kiểm tra theo quy tắc cũ, cùng yêu cầu 90 có thể nhận hai kết quả trái nhau. Sai khác xảy ra vì *quyền quyết định* đã bị phân tán, không phải vì phép trừ 100−90 khó.

Cho Account quản lý quy tắc giữ số dư khiến màn hình, batch và Checkout hỏi cùng một hành vi. Account kiểm tra `100 - 90 >= 20` là sai, trả từ chối, giữ 100; các bên không tự quyết định phép trừ. Mục tiêu OOP ở đây là làm **nơi quyết định và tác động của thay đổi** dễ xác định, không hứa mọi hệ thống sẽ tự hết lỗi. **Tự kiểm tra:** nếu thêm một màn hình mới, thành phần nào nên quyết định yêu cầu rút 90 hợp lệ?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-solution">Bản đồ khái niệm OOP: Đối tượng, nguyên lý và quan hệ</a>

<details>
<summary>Xem chi tiết</summary>

```text
trạng thái + hành vi
      ↓
đối tượng chịu trách nhiệm
      ↓
cộng tác qua hợp đồng
```

Những khái niệm tiếp theo gồm đóng gói (encapsulation), trừu tượng hóa (abstraction), hợp thành (composition), kế thừa và kiểu con (inheritance/subtyping), cùng đa hình (polymorphism).

Trong mô hình tài khoản, **danh tính** xác định tài khoản nào, **trạng thái** có thể gồm số dư, **hành vi** như nạp/rút quyết định thay đổi hợp lệ và **trách nhiệm** quy định ai được quyết định. Đối tượng thanh toán yêu cầu tài khoản thực hiện hành vi thay vì tùy ý sửa trường số dư.

**Đọc bản đồ theo từng cấp, không như bốn “thành phần bắt buộc”.** Cấp **mô hình đối tượng** nói tới *A là tài khoản nào, đang giữ trạng thái gì và đáp ứng yêu cầu nào*. Cấp **ranh giới thiết kế** nói *ai có quyền đổi số dư, điều kiện hợp lệ nào được bảo vệ, điều gì được công bố hoặc che giấu*. Cấp **cộng tác/quan hệ** nói *Checkout nhờ Account xử lý, rồi nhờ Notifier báo kết quả như thế nào*. Cấp **biến thể hành vi** nói *một yêu cầu thanh toán có thể do ví hay chuyển khoản đáp ứng theo cùng một cam kết*. Những cấp này liên quan nhưng **không phải các loại đối tượng ngang hàng**.

Ví dụ: `Account A` có danh tính riêng; `withdraw(30)` là hành vi; `balance >= 0` là quy tắc nhất quán; `Checkout → Account` là quan hệ yêu cầu–phản hồi; `WalletPayment` và `BankPayment` là hai cách đáp ứng yêu cầu thanh toán. **Class** chỉ là một cơ chế mô tả/tạo đối tượng ở ngôn ngữ dựa trên lớp, không nằm cùng cấp khái niệm với việc một yêu cầu có thành công hay không. **Tự kiểm tra:** “đa hình” thuộc biến thể hành vi hay thuộc dữ liệu số dư?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-class-vs-object">Object và Class — Đối tượng khác với bản mô tả tạo đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

**Object (đối tượng)** là một thực thể cụ thể trong lúc chương trình hoạt động: tài khoản A có mã riêng, số dư 100 và có thể nhận yêu cầu rút tiền. **Class (lớp)** trong một ngôn ngữ *dựa trên lớp* là **bản mô tả** những thuộc tính và hành vi mà các đối tượng kiểu đó có thể có. Hãy hình dung một bản mẫu “Tài khoản” và **hai tài khoản thực tế A, B** được tạo theo bản mẫu ấy: cả hai có thể bắt đầu với số dư 100 nhưng vẫn là hai đối tượng khác danh tính.

Theo dõi một thao tác: gửi yêu cầu rút 30 tới **A** → A kiểm tra đủ tiền → A còn **70** → **B vẫn 100**. Bản mô tả class không phải một trong hai số dư đang được thay đổi. Nếu có hai cách gọi cùng tham chiếu đến *chính A*, thì thao tác qua cách gọi thứ nhất khiến cách gọi thứ hai nhìn thấy A=70; đó là **hai cách trỏ tới một đối tượng**, khác với hai đối tượng A và B.

Không phải mọi kiểu OOP trên thế giới đều bắt buộc có class: một số mô hình đối tượng dùng cơ chế khác, chẳng hạn đối tượng làm nguyên mẫu. Bài này chỉ dạy sự khác biệt **bản mô tả ↔ thực thể đang tham gia xử lý**; cú pháp tạo instance, constructor hay quản lý tham chiếu Java thuộc Java Core. **Tự kiểm tra:** tạo hai tài khoản từ cùng một class có làm chúng trở thành một tài khoản không?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-boundary">Tư duy OOP và cơ chế class, interface trong Java</a>

<details>
<summary>Xem chi tiết</summary>

Module này giảng dạy **mô hình tư duy hướng đối tượng không phụ thuộc ngôn ngữ**.

Từ khóa, quyền truy cập, cơ chế lớp/giao diện và cách Java lựa chọn phương thức lúc chạy thuộc `programming/language/java/core/oop` và các module Java Core liên quan.

Các từ `class`, `interface` hay `private` là cơ chế có thể dùng trong Java để hiện thực đối tượng, không phải toàn bộ định nghĩa OOP. Chúng ta tập trung vào việc xác định trách nhiệm, hợp đồng, tính nhất quán và cộng tác trước; chi tiết kế thừa, ghi đè và phân phối lời gọi trong Java thuộc Java Core.

**Cùng một quyết định, hai tầng câu hỏi.** Ở tầng tư duy OOP, ta phát biểu: “Account nhận `withdraw(30)`, kiểm tra điều kiện, trả kết quả và giữ số dư hợp lệ; Checkout không tự sửa số dư.” Chưa cần chọn ngôn ngữ vẫn nhận xét được trách nhiệm đúng hay sai. Ở tầng Java, ta mới hỏi **khai báo class thế nào**, field có quyền truy cập gì, interface biểu diễn hợp đồng ra sao, lời gọi được chọn phương thức nào lúc chạy.

Thêm `private balance` vào một class không tự bảo đảm mô hình đúng: nếu một phương thức công khai `setBalance(-20)` cho phép phá quy tắc, ranh giới hành vi vẫn yếu. Tương tự, một `interface` không tự định nghĩa điều gì xảy ra khi thanh toán thất bại; ta phải giải thích lời hứa hành vi trước. Các mô tả ở đây là **ví dụ khái niệm**, không phải code Java chạy được. **Tự kiểm tra:** “ai được quyết định rút tiền?” và “Java dùng từ khóa nào?” là cùng một câu hỏi hay hai tầng khác nhau?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-learning-path">Điểm xuất phát và lộ trình từ đối tượng đến thiết kế</a>

<details>
<summary>Xem chi tiết</summary>

Người học cần quen với biến, thao tác và dữ liệu đơn giản; không cần thành thạo Java class. Hãy theo dõi tài khoản từ **danh tính/trạng thái**, qua **đóng gói và điều kiện nhất quán**, tới **trách nhiệm và hợp đồng**, sau đó học **hợp thành, kiểu con, đa hình**, cuối cùng đánh giá thiết kế và rủi ro.

**Điểm bắt đầu:** chỉ cần biết một biến giữ giá trị, một điều kiện quyết định nhánh và một hàm/thủ tục nhận yêu cầu. Không cần biết `class`, `interface` hay Java trước khi học mô hình. **Đích đến:** nhìn một yêu cầu thanh toán nhỏ và giải thích được ai giữ quy tắc, đối tượng nào cộng tác, điều gì xảy ra khi thành công/thất bại, thêm phương thức thanh toán mới ảnh hưởng chỗ nào.

Bắt đầu với **danh tính và mô hình object/class**: vẽ A và B cùng 100, rút ở A mà B không đổi. Tiếp theo học **đóng gói và invariant**: từ 100 thử rút 30 được 70, thử rút 120 bị từ chối và vẫn 100. Khi hiểu *ai giữ trạng thái hợp lệ*, học **trách nhiệm và hợp đồng**: viết lời hứa `withdraw` trước khi điều phối. Rồi chuyển sang **cộng tác**: Checkout nhận kết quả, Notifier chỉ gửi biên nhận thành công sau xác nhận; thay chính sách phí vẫn giữ hợp đồng. Cuối cùng học **đa hình và khả năng thay thế**: ví và chuyển khoản cùng trả kết quả đúng hợp đồng; đánh giá lúc nào lớp trung gian hoặc kế thừa khiến thiết kế nặng hơn lợi ích.

**Tự đánh giá tiến độ:** bạn có thể (1) phân biệt hai đối tượng cùng giá trị; (2) tự lần vết nhánh từ chối giữ trạng thái; (3) kể đúng trách nhiệm của Checkout, Account, Notifier; (4) giải thích cách thêm BankPayment mà Checkout không phải hỏi loại? Nếu được, bạn đã có nền tảng để phân tích một thiết kế OOP nhỏ, trước khi học cơ chế Java.

</details>

- [Quay lại đầu trang](#back-to-top)
