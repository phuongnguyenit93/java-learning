<a id="back-to-top"></a>

# Bảo toàn trạng thái

## Menu
- [Tính nguyên tử khi thất bại và nguy cơ thay đổi dở dang](#exception-safety-001)
- [Kiểm tra điều kiện trước khi thay đổi trạng thái](#exception-safety-002)
- [Tính toán trên dữ liệu tạm rồi mới công bố kết quả](#exception-safety-003)
- [Khôi phục trạng thái và giới hạn của rollback](#exception-safety-004)
- [Ngoại lệ không tự hoàn tác file, dữ liệu hay tác động bên ngoài](#exception-safety-005)
- [Đóng tài nguyên và hủy đăng ký theo quyền sở hữu](#exception-safety-006)
- [Lock.lock kết hợp unlock trong finally](#exception-safety-007)
- [Phân biệt giải phóng khóa synchronized với bảo đảm bất biến dữ liệu](#exception-safety-008)
- [Xử lý lỗi cleanup mà vẫn giữ thông tin lỗi ban đầu](#exception-safety-009)

## <a id="exception-safety-001">Tính nguyên tử khi thất bại và nguy cơ thay đổi dở dang</a>

<details>
<summary>Xem chi tiết</summary>

# Tính nguyên tử khi thất bại và nguy cơ thay đổi dở dang trong Java

## 1. Vấn đề: một phương thức có thể ném exception sau khi đã thay đổi dữ liệu

Giả sử một thao tác thêm hàng vào giỏ phải đồng thời cập nhật danh sách sản phẩm và tổng số lượng. Nếu phương thức thêm vào danh sách trước, rồi mới kiểm tra số lượng có hợp lệ hay không, exception sẽ ngắt luồng thực thi giữa chừng. Người gọi nhận được thông báo “thêm thất bại” nhưng giỏ đã chứa một dòng không hợp lệ. Những thao tác sau đó có thể đọc một trạng thái mâu thuẫn, dù exception đã được bắt và chương trình vẫn tiếp tục chạy.

**Tính nguyên tử khi thất bại** (failure atomicity) là cam kết ở cấp độ thao tác: **hoặc thao tác thành công và để lại trạng thái hoàn chỉnh, hoặc thao tác thất bại và trạng thái quan sát được vẫn như trước khi thao tác bắt đầu**. Đây là một mục tiêu thiết kế API quan trọng khi phương thức có thể ném exception trong lúc cập nhật đối tượng.

Để đánh giá một thao tác, cần xác định rõ:

- **Trạng thái trước**: những giá trị và tài nguyên nào được phép thay đổi bởi thao tác?
- **Bất biến** (invariant): điều kiện nào luôn phải đúng, chẳng hạn mọi số lượng đều dương và tổng bằng tổng các dòng?
- **Điểm có thể thất bại**: kiểm tra đầu vào, phép toán tràn số, cấp phát bộ nhớ, callback, ghi tệp, truy vấn dữ liệu, hoặc gọi dịch vụ bên ngoài?
- **Trạng thái sau thất bại**: đối tượng còn hợp lệ hay phải giống hệt trạng thái trước? Hành động bên ngoài có thể quan sát một phần thay đổi hay không?

Một exception **không tự khôi phục** biến, collection, tệp hay dữ liệu đã ghi. Lệnh **catch** chỉ bắt luồng điều khiển; **finally** và try-with-resources xử lý dọn dẹp tài nguyên. Không cơ chế nào trong số đó mặc nhiên đảo ngược mọi thay đổi đã xảy ra.

## 2. Phân biệt những mức bảo đảm an toàn trước exception

Các mức bảo đảm thường dùng để mô tả thao tác thay đổi trạng thái:

| Mức bảo đảm | Khi thao tác ném exception | Ví dụ ý nghĩa |
| --- | --- | --- |
| Không có bảo đảm rõ ràng | Trạng thái có thể không hợp lệ hoặc không còn dùng an toàn | Danh sách đã thêm dòng mới nhưng biến tổng chưa cập nhật |
| **Basic guarantee** | Bất biến được giữ; đối tượng vẫn hợp lệ, nhưng có thể đã thay đổi một phần | Một số mục hợp lệ đã được thêm trước khi thao tác dừng |
| **Strong guarantee / failure atomicity** | Trạng thái thuộc phạm vi cam kết được giữ như trước thao tác | Phép thêm thất bại thì cả danh sách lẫn tổng không đổi |
| **No-throw guarantee** | Thao tác được thiết kế không phát sinh exception trong phạm vi bảo đảm | Một phép gán tham chiếu đã được chuẩn bị sẵn, xét trong điều kiện vận hành thông thường |

**Basic guarantee** đủ cho một số thao tác xử lý tuần tự: nếu thất bại ở bản ghi thứ ba, hai bản ghi đầu có thể đã được xử lý nhưng toàn bộ đối tượng vẫn nhất quán. **Strong guarantee** là yêu cầu mạnh hơn khi API mang nghĩa “thành công toàn bộ hoặc thất bại toàn bộ”.

Không được suy ra mức bảo đảm từ việc phương thức khai báo **throws**, từ loại checked/unchecked exception, hoặc từ việc mã có **try/catch**. Mức bảo đảm phụ thuộc vào thứ tự thay đổi dữ liệu, thiết kế trạng thái và những tác dụng phụ thực sự.

Khái niệm này cũng khác hai nghĩa thường gặp của “atomic”:

- **Atomicity trong concurrency**: các luồng khác có nhìn thấy trạng thái trung gian hay không. Một thao tác có thể được khóa đồng bộ nhưng vẫn để lại dữ liệu dở dang sau exception.
- **Atomicity trong giao dịch lưu trữ**: hệ quản trị cơ sở dữ liệu hoặc cơ chế ghi tệp cung cấp các quy tắc riêng. Một phép gán tham chiếu trong RAM không tự biến hai lần ghi tệp hoặc hai lời gọi HTTP thành một giao dịch.

## 3. Ví dụ Java 17+: thấy rõ trạng thái dở dang và cách khắc phục

Ví dụ sau dùng snapshot bất biến gồm danh sách số lượng và tổng số lượng. Hai phương thức nhận cùng đầu vào nhưng khác thứ tự cập nhật:

- **addBroken** công bố danh sách mới trước rồi mới kiểm tra điều kiện và tính tổng. Khi ném exception, người gọi vẫn nhìn thấy danh sách đã đổi.
- **addSafe** kiểm tra đầu vào, tính toán, xây dựng toàn bộ snapshot mới trước; chỉ khi không có exception mới thay một tham chiếu trạng thái.

Lưu chương trình sau vào tệp **FailureAtomicityDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class FailureAtomicityDemo {
    static final class Cart {
        record Snapshot(List<Integer> quantities, int total) {}

        private volatile Snapshot state =
                new Snapshot(List.of(), 0);

        Snapshot snapshot() {
            return state;
        }

        synchronized void addBroken(int quantity) {
            Snapshot before = state;
            List<Integer> updated = new ArrayList<>(before.quantities());
            updated.add(quantity);

            // Công bố một phần thay đổi quá sớm.
            state = new Snapshot(List.copyOf(updated), before.total());

            if (quantity <= 0) {
                throw new IllegalArgumentException(
                        "quantity must be positive");
            }

            int newTotal = Math.addExact(before.total(), quantity);
            state = new Snapshot(List.copyOf(updated), newTotal);
        }

        synchronized void addSafe(int quantity) {
            // Những bước có thể thất bại đều diễn ra trước commit.
            if (quantity <= 0) {
                throw new IllegalArgumentException(
                        "quantity must be positive");
            }

            Snapshot before = state;
            int newTotal = Math.addExact(before.total(), quantity);
            List<Integer> updated = new ArrayList<>(before.quantities());
            updated.add(quantity);
            Snapshot candidate =
                    new Snapshot(List.copyOf(updated), newTotal);

            // Một bước công bố trạng thái hoàn chỉnh.
            state = candidate;
        }
    }

    public static void main(String[] args) {
        Cart broken = new Cart();
        broken.addBroken(2);

        try {
            broken.addBroken(-1);
        } catch (IllegalArgumentException e) {
            System.out.println("Broken rejected: " + e.getMessage());
        }

        System.out.println("Broken: "
                + broken.snapshot().quantities()
                + ", total=" + broken.snapshot().total());

        Cart safe = new Cart();
        safe.addSafe(2);
        Cart.Snapshot beforeFailure = safe.snapshot();

        try {
            safe.addSafe(-1);
        } catch (IllegalArgumentException e) {
            System.out.println("Safe rejected: " + e.getMessage());
        }

        System.out.println("Safe: "
                + safe.snapshot().quantities()
                + ", total=" + safe.snapshot().total());

        if (!beforeFailure.equals(safe.snapshot())) {
            throw new AssertionError("State changed after failure");
        }

        try {
            safe.addSafe(Integer.MAX_VALUE);
        } catch (ArithmeticException e) {
            System.out.println("Overflow rejected: " + e.getMessage());
        }

        System.out.println("After overflow: "
                + safe.snapshot().quantities()
                + ", total=" + safe.snapshot().total());
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~bash
javac FailureAtomicityDemo.java
java FailureAtomicityDemo
~~~

Kết quả chuẩn:

~~~text
Broken rejected: quantity must be positive
Broken: [2, -1], total=2
Safe rejected: quantity must be positive
Safe: [2], total=2
Overflow rejected: integer overflow
After overflow: [2], total=2
~~~

**Vì sao kết quả khác nhau?**

Ở phiên bản lỗi, **state** được thay bằng snapshot có danh sách **[2, -1]** nhưng tổng vẫn bằng **2**. Sau đó điều kiện **quantity <= 0** ném exception, bỏ qua cập nhật tổng cuối cùng. Dữ liệu còn lại vừa chứa số lượng không hợp lệ, vừa không còn thỏa bất biến “tổng bằng tổng các phần tử”.

Ở phiên bản an toàn, nhánh số lượng âm thất bại **trước khi** thay **state**. Nhánh tràn số dùng **Math.addExact**, vì vậy không silently wrap số nguyên và cũng không cập nhật trạng thái. Câu kiểm tra bằng **equals** xác minh snapshot sau exception bằng snapshot trước đó.

Có ba chi tiết thiết kế quan trọng:

1. **List.copyOf** tạo danh sách không thể thay đổi qua API của **List** và từ chối phần tử **null**. Chỉ nên gọi đây là snapshot bất biến sâu nếu chính các phần tử bên trong cũng bất biến; ở đây các phần tử là **Integer**.
2. **volatile** giúp luồng đọc nhìn thấy snapshot đã công bố; **synchronized** tuần tự hóa các lần ghi, tránh hai luồng cùng đọc trạng thái cũ rồi ghi đè mất cập nhật của nhau. Chỉ dùng **volatile** mà bỏ cơ chế phối hợp giữa nhiều writer thì chưa đủ.
3. Việc xây dựng snapshot mới có thể ném exception trước khi gán **state**. Trong phạm vi thao tác trên trạng thái bộ nhớ này, một phép gán tham chiếu cuối cùng là điểm công bố trạng thái hoàn chỉnh. Ví dụ **không** tuyên bố có thể phục hồi từ mọi lỗi nghiêm trọng của VM, như **OutOfMemoryError** xuất hiện ở vị trí bất kỳ.

Ví dụ cố ý giữ hai phương thức trong cùng một class để so sánh; trong API thực tế chỉ nên xuất bản biến thể đáp ứng hợp đồng đã chọn.

## 4. Mẫu thiết kế: chuẩn bị trước, công bố sau

Đối với một aggregate có thể biểu diễn bằng một giá trị trạng thái, cách làm đáng tin cậy là:

1. **Định nghĩa bất biến và phạm vi cam kết**: những trường, collection và quan hệ nào phải đồng thời nhất quán?
2. **Kiểm tra điều kiện tiên quyết** trước đột biến: đối số, quyền, quy tắc nghiệp vụ và khả năng thực hiện.
3. **Tính toán kết quả có thể thất bại**: chẳng hạn **Math.addExact**, parse, chuyển đổi, tạo bản sao, cấp phát collection, ánh xạ dữ liệu.
4. **Tạo bản trạng thái hoàn chỉnh** bằng giá trị cục bộ hoặc đối tượng mới, chưa công bố cho người dùng khác.
5. **Commit tại một điểm rõ ràng**: gán một snapshot đã xây xong, hoặc dùng cơ chế giao dịch phù hợp với dữ liệu.
6. **Quy định hệ quả sau commit**: các callback, thông báo, ghi log, phát sự kiện nếu thất bại thì phương thức sẽ phản ánh thế nào?

Sơ đồ luồng có thể hình dung như sau:

~~~text
Trạng thái S0
    |
    v
Kiểm tra và chuẩn bị ứng viên S1
    |               |
  Lỗi             Thành công
    |               |
    v               v
Giữ S0         Commit S1 một lần
                    |
                    v
                  Kết thúc
~~~

Đừng hiểu “validate trước” là giải pháp toàn năng. Nếu hai phép ghi thật sự độc lập đều có thể thất bại, việc kiểm tra đầu vào sớm vẫn không ngăn lỗi ở phép ghi thứ hai. Khi đó cần thay đổi biểu diễn trạng thái, staging, giao dịch, hoặc một hợp đồng rõ ràng chỉ bảo đảm mức cơ bản.

### Khi một đối tượng chứa nhiều trường phụ thuộc nhau

Với trạng thái như **balance**, **reserved** và **available**, cập nhật từng trường riêng lẻ sẽ tạo khoảng thời gian bất nhất nếu một bước ném exception. Đóng gói chúng vào một record bất biến, xây record mới rồi công bố một tham chiếu giúp tránh những trạng thái phối hợp dở dang trong RAM.

Đối với collection, trình tự **clear → addAll → sửa tổng** vẫn là nhiều bước đột biến. Việc tính xong một collection tạm chưa đủ nếu sau đó lại sao chép nó vào collection công khai bằng nhiều lời gọi có thể lỗi. Cần để trạng thái cũ tồn tại nguyên vẹn đến khi có thể thay bằng tham chiếu trỏ đến collection mới.

### Khi có nhiều writer và reader

Một bản sao được tính từ S0 có thể trở nên cũ trong lúc writer khác đã commit S1. Có thể đồng bộ toàn bộ chuỗi đọc–tính–ghi, hoặc dùng **AtomicReference** và compare-and-set theo vòng lặp khi phù hợp. Với compare-and-set, mọi lần thử phải tính lại từ snapshot đang còn hiệu lực; không chạy tác dụng phụ không thể lặp an toàn ở trong vòng lặp retry. Nếu mỗi lần tính mới gọi một dịch vụ thu phí hay gửi email, một vòng lặp CAS thất bại có thể tạo tác dụng phụ trùng lặp.

Ngược lại, **AtomicInteger**, **volatile** hay **synchronized** không tự bảo đảm rollback khi code bên trong đã sửa dữ liệu trước lúc exception. Chúng giải quyết một số vấn đề quan sát và cạnh tranh giữa luồng, còn failure atomicity đòi hỏi thiết kế đường đi khi lỗi.

## 5. Trường hợp chuyển tiền: hai thay đổi cần cùng cam kết

Giả sử hai số dư **source** và **destination** được quản lý trong cùng một đối tượng. Mã dạng sau có nguy cơ cập nhật dở dang:

~~~java
// Mã minh họa; source và destination là hai trường
// số dư mutable đã được khai báo ở nơi khác.
source -= amount;
destination = Math.addExact(destination, amount);
~~~

Nếu phép cộng tại đích gây **ArithmeticException**, tiền đã bị trừ ở nguồn. Đổi thứ tự hai dòng không giải quyết triệt để; dòng cập nhật thứ hai vẫn có thể thất bại.

Nếu toàn bộ số dư được sở hữu bởi một aggregate trong bộ nhớ, có thể chuẩn bị kết quả dưới dạng một snapshot chung:

~~~java
record Balances(long source, long destination) {}

// Minh họa bên trong phương thức có quyền ghi trạng thái.
// before là snapshot bất biến hiện tại.
if (amount <= 0 || amount > before.source()) {
    throw new IllegalArgumentException("invalid amount");
}

long nextSource = Math.subtractExact(before.source(), amount);
long nextDestination = Math.addExact(before.destination(), amount);
Balances next = new Balances(nextSource, nextDestination);

// Commit: thay một tham chiếu chung từ before sang next;
// cần đồng bộ writer hoặc dùng CAS phù hợp.
balances = next;
~~~

Hai đoạn trên là **mã minh họa theo ngữ cảnh**, không phải chương trình độc lập. Chúng nêu điểm khác biệt về thứ tự thay đổi, không thay thế ví dụ runnable ở mục 3.

Nếu nguồn và đích nằm trong **hai bản ghi cơ sở dữ liệu**, thao tác phải dùng transaction thực sự và có đường rollback thích hợp theo hệ lưu trữ. Nếu chúng nằm ở **hai dịch vụ hoặc hai cơ sở dữ liệu độc lập**, một transaction cục bộ không tạo ra tính nguyên tử toàn cục. Cần thiết kế quy trình phân tán, idempotency, trạng thái chuyển tiếp và xử lý thất bại; những kỹ thuật như saga/compensation có thể phục hồi nghiệp vụ nhưng không đồng nghĩa mọi bên ngoài chưa từng thấy thay đổi trung gian.

## 6. Vì sao rollback thủ công bằng catch có thể vẫn sai?

Mẫu dưới đây trông như đã xử lý lỗi nhưng chưa đủ để khẳng định tính nguyên tử:

~~~java
// Mã minh họa: undo() cũng có thể thất bại.
applyFirstChange();
try {
    applySecondChange();
} catch (RuntimeException failure) {
    undoFirstChange();
    throw failure;
}
~~~

Các tình huống cần xem xét:

- **applyFirstChange** hoàn tất một phần rồi tự ném exception trước khi vào khối **try**; không có nhánh hoàn tác nào chạy.
- **undoFirstChange** có thể ném exception khác, khiến trạng thái chưa được phục hồi và exception gốc bị che khuất.
- Một observer khác đã thấy thay đổi thứ nhất; hoàn tác sau đó không thể xóa trải nghiệm hoặc tác dụng phụ đã quan sát.
- Lời gọi bên ngoài đã gửi email, phát sự kiện hoặc chuyển tiền thật; gọi hàm nghịch đảo không chắc làm mọi thứ như chưa từng xảy ra.

Nếu phải rollback, hãy ghi rõ phạm vi, thứ tự đảo ngược, tình huống rollback thất bại, cách bảo tồn exception gốc và cơ chế phục hồi tiếp theo. Đối với tài nguyên có giao dịch thật, ưu tiên dùng giao dịch của hệ đó. Đối với trạng thái trong bộ nhớ, thường dễ kiểm chứng hơn khi tránh sửa trạng thái hiện hành cho đến khi hoàn tất chuẩn bị.

**finally** và try-with-resources rất cần để đóng stream, trả connection và giải phóng khóa theo hợp đồng. Chúng bảo đảm xử lý vòng đời tài nguyên ở đường thành công/lỗi, **không tự undo** nội dung đã ghi, request đã gửi hay object đã bị sửa.

## 7. Điểm khó thường bị bỏ qua trong API thực tế

### Callback và thông báo sau commit

Giả sử một phương thức cập nhật thành công rồi gọi listener:

~~~java
state = candidate;                 // Đã commit.
listener.onChanged(candidate);     // Có thể ném exception.
~~~

Nếu listener ném lỗi, người gọi nhận exception nhưng trạng thái đã đổi. Phương thức đó **không đáp ứng** lời hứa “bất kỳ exception nào cũng giữ trạng thái cũ”. Có thể chọn một hợp đồng khác: commit thành công, lỗi thông báo được báo/ghi nhận riêng, retry độc lập hoặc qua outbox. Điều cốt yếu là hợp đồng API và đường xử lý phải khớp với thực tế.

Gọi listener **trước commit** cũng chưa chắc an toàn: listener có thể phát sinh tác dụng phụ hoặc giữ tham chiếu đến dữ liệu ứng viên. Hãy phân biệt giai đoạn chuẩn bị thuần túy với các thao tác tạo ra tác dụng phụ bên ngoài.

### Ghi tệp và thay thế dữ liệu

Ghi đè tệp đích trực tiếp có thể để lại nội dung dở dang nếu quá trình ghi lỗi. Một hướng thiết kế là tạo **tệp tạm trong cùng hệ tệp**, ghi đủ dữ liệu, đóng tài nguyên và chỉ sau đó thay thế đường dẫn đích bằng thao tác move nguyên tử **khi hệ tệp hỗ trợ**. Trong Java, **Files.move** với **StandardCopyOption.ATOMIC_MOVE** có thể ném **AtomicMoveNotSupportedException**; phải quyết định có chấp nhận fallback kém bảo đảm hơn hay không. Tính nguyên tử khi đổi tên cũng khác độ bền sau mất điện; để có cam kết durability cần xem xét flush/fsync và hệ tệp cụ thể.

### Database, mạng và sự kiện

- Transaction của database chỉ bảo vệ những thao tác thực sự tham gia transaction theo đúng cơ chế của database. Một request HTTP hay email nằm ngoài transaction ấy.
- Timeout mạng không chứng minh tác vụ từ xa thất bại; máy chủ có thể đã commit nhưng phản hồi bị mất.
- Khi retry một thao tác có tác dụng phụ, hãy xét khóa idempotency, deduplication, trạng thái kết quả và quy tắc retry.
- Không phát thông điệp “thành công” từ trạng thái chưa commit; nếu cần bảo đảm phối hợp giữa dữ liệu và sự kiện, xem xét transactional outbox ở tầng kiến trúc thích hợp.

### Alias, mutable objects và phạm vi snapshot

Danh sách mới nhưng chứa cùng các phần tử mutable vẫn có thể bị thay đổi qua tham chiếu khác. **List.copyOf** chỉ bảo vệ cấu trúc danh sách, không biến mọi object con thành bất biến sâu. Nếu caller nhận trực tiếp mutable object nội bộ, họ có thể sửa dữ liệu mà không đi qua đường commit. Muốn cam kết snapshot đáng tin, cần kiểm soát quyền sở hữu, copy phù hợp hoặc dùng value object bất biến xuyên suốt.

### Loại lỗi nào nằm trong hợp đồng?

Tài liệu API nên xác định các lỗi được bảo đảm xử lý, chẳng hạn **IllegalArgumentException**, **ArithmeticException** hoặc lỗi I/O đã được xét trong thiết kế. Không nên hứa rollback tuyệt đối khi process bị kill, máy mất điện, VM gặp lỗi nghiêm trọng hoặc các hệ bên ngoài không có giao dịch. Có thể áp dụng failure atomicity mạnh trong một phạm vi rất cụ thể, như “snapshot RAM của Cart không đổi khi phương thức addSafe ném exception trước commit”, và một mức bảo đảm khác cho mạng hay storage.

## 8. Cách kiểm thử đường thất bại

Bài kiểm thử phải **chứng minh trạng thái sau lỗi**, thay vì chỉ kiểm tra rằng exception có xảy ra. Một quy trình hữu ích:

1. Thiết lập trạng thái hợp lệ S0 và chụp snapshot đầy đủ những phần thuộc cam kết.
2. Kích hoạt lỗi tại từng điểm có thể thất bại: tham số không hợp lệ, tràn số, bước tính toán, cấp phát, bộ lưu trữ giả lập, callback.
3. Bắt **đúng loại exception dự kiến**, sau đó kiểm tra trạng thái hiện hành so với S0 theo mức bảo đảm đã công bố.
4. Thực hiện thêm một thao tác hợp lệ sau lỗi để chứng minh đối tượng tiếp tục hoạt động đúng.
5. Kiểm thử đồng thời riêng nếu nhiều thread có thể đọc/ghi; kiểm thử failure atomicity tuần tự chưa chứng minh tính linearizable giữa các luồng.

Trong ví dụ runnable, câu **beforeFailure.equals(safe.snapshot())** kiểm tra strong guarantee cho đầu vào âm. Lần gọi với **Integer.MAX_VALUE** xác minh thêm một đường lỗi khác: tràn số số học. Trong dự án thật có thể bổ sung fault injection để ép lỗi ở các dependency; tránh viết mock chỉ để tái khẳng định logic của mock thay vì quan sát trạng thái ứng dụng.

Một bảng kiểm tình huống thường hữu ích hơn chỉ có happy path:

| Điểm thất bại | Kỳ vọng với strong guarantee của aggregate |
| --- | --- |
| Kiểm tra đối số không đạt | Không có thay đổi nào được công bố |
| Tính toán gây tràn số | Snapshot hiện tại giữ nguyên |
| Tạo collection/record mới thất bại trước commit | Snapshot cũ vẫn còn |
| Hai writer cùng cập nhật | Không mất cập nhật; mỗi commit dựa trên trạng thái hợp lệ |
| Callback sau commit thất bại | Phải có hợp đồng riêng; không thể mặc nhiên gọi đây là strong guarantee toàn phương thức |
| Tác vụ storage/network bên ngoài thất bại | Cần đánh giá cơ chế transaction, idempotency và phạm vi cam kết tương ứng |

## 9. Sai lầm phổ biến và cách nhận diện

**“Đã ném exception nghĩa là chưa có gì xảy ra.”** Sai: exception chỉ mô tả luồng thực thi đã rẽ sang đường lỗi. Hãy kiểm tra tất cả thay đổi được thực hiện trước điểm ném lỗi.

**“Bọc synchronized thì thao tác là atomic.”** Khóa có thể chặn các thread khác quan sát trạng thái trung gian khi cùng tuân thủ khóa, nhưng không khôi phục dữ liệu nếu lệnh cuối ném lỗi; sau khi khóa nhả, trạng thái sai có thể lộ ra.

**“Đặt validate ở đầu là đủ.”** Không đủ nếu sau validate còn gọi phép tính, allocator, callback hoặc I/O có thể thất bại sau đột biến.

**“Đã sao chép collection nên chắc chắn atomic.”** Nếu công bố bản sao quá sớm hoặc lại dùng **clear/addAll** để thay collection gốc, thao tác vẫn có nhiều điểm cập nhật.

**“Cứ catch rồi bỏ qua để ứng dụng chạy tiếp.”** Nuốt lỗi khiến trạng thái dở dang khó phát hiện. Chỉ recover khi có kế hoạch thực sự; nếu không, ném lại với ngữ cảnh phù hợp và để tầng chịu trách nhiệm xử lý.

**“Rollback thủ công luôn đưa thế giới về trạng thái cũ.”** Hoàn tác cũng có thể lỗi và không thu hồi những tác dụng phụ mà người khác đã nhìn thấy.

**“Tất cả exception phải bảo đảm không đổi trạng thái.”** Một API có thể cố ý cung cấp basic guarantee hoặc báo trạng thái partial progress. Điều cần thiết là hợp đồng nêu rõ và triển khai nhất quán.

## 10. Áp dụng trong thiết kế phương thức Java

Trước khi viết một phương thức thay đổi trạng thái có nhiều bước, tự trả lời:

- Nếu exception xuất hiện sau từng dòng có khả năng thất bại, dữ liệu đang ở trạng thái nào?
- Những trường nào tạo thành **một bất biến chung**? Có thể lưu chung vào một snapshot bất biến không?
- Các thao tác chuẩn bị có hoàn toàn nội bộ và chưa phát sinh side effect không?
- Có thể tạo một **điểm commit duy nhất** không? Nếu không, cơ chế giao dịch thực sự là gì?
- Khi retry, có nguy cơ chạy lặp một side effect hay mất cập nhật giữa các writer không?
- Khi người gọi nhận exception, tài liệu API hứa “không thay đổi”, “vẫn hợp lệ” hay “có thể hoàn thành một phần”?
- Kiểm thử nào so sánh trạng thái trước–sau, kể cả đường tràn số, I/O và cạnh tranh giữa luồng?

**Điểm cần ghi nhớ:** Failure atomicity là bảo đảm về **trạng thái có thể quan sát sau khi một thao tác thất bại**, không phải đặc tính tự động của exception handling. Với dữ liệu trong bộ nhớ, cách tiếp cận dễ kiểm chứng là **kiểm tra → tính toán và xây trạng thái ứng viên → commit một lần**. Khi thao tác đi qua database, file hoặc dịch vụ bên ngoài, phải xác định lại phạm vi cam kết và sử dụng cơ chế giao dịch/phục hồi tương ứng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-safety-002">Kiểm tra điều kiện trước khi thay đổi trạng thái</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra điều kiện trước khi thay đổi trạng thái trong Java

> **Chủ đề:** Java Core — Exception · **Nhóm kiến thức:** Bảo toàn trạng thái. Mục tiêu là thiết kế phương thức sao cho lỗi đầu vào hoặc điều kiện nghiệp vụ không hợp lệ được phát hiện trước khi đối tượng bị cập nhật dở dang; từ đó giữ các bất biến và cung cấp hợp đồng thất bại dễ dự đoán.

## 1. Vì sao phải kiểm tra trước khi thay đổi trạng thái?

Một thao tác có thể thất bại do tham số sai, trạng thái hiện tại không đáp ứng yêu cầu, phép tính tràn số, tài nguyên hết chỗ, hoặc lời gọi phụ thuộc bên ngoài ném ngoại lệ. Nếu phương thức sửa một phần dữ liệu rồi mới phát hiện lỗi, đối tượng có thể ở trạng thái khó hiểu: số dư đã trừ nhưng lịch sử giao dịch chưa ghi, một mặt hàng đã giảm tồn kho nhưng mặt hàng thứ hai vẫn giữ nguyên, hoặc cấu hình chỉ được cập nhật một nửa.

Với đối tượng có trạng thái, cần phân biệt ba khái niệm:

- **Tiền điều kiện (precondition):** điều kiện cần thỏa mãn *trước thao tác*, ví dụ số lượng đặt hàng phải lớn hơn 0, mã sản phẩm phải tồn tại, tài khoản đích hợp lệ.
- **Bất biến (invariant):** điều kiện phải đúng ở mọi trạng thái có thể quan sát hợp lệ, ví dụ tồn kho không âm, tổng số dư giữa các tài khoản không bị tự sinh hoặc mất đi khi chuyển tiền nội bộ, danh sách mục và tổng tiền nhất quán.
- **Hậu điều kiện (postcondition):** điều kiện đúng khi thao tác thành công, ví dụ lượng tồn kho giảm chính xác theo yêu cầu.

**Quy tắc thực hành:** xác minh tiền điều kiện và tính trước những gì có thể thất bại, sau đó mới công bố trạng thái mới. Nếu ném ngoại lệ ở đường kiểm tra, trạng thái quan sát được của đối tượng nên giữ nguyên, trừ khi API công bố rõ một bảo đảm yếu hơn.

Thứ tự có ý nghĩa vì `throw` lập tức chuyển điều khiển ra khỏi luồng thông thường. Những phép gán đã xảy ra **không tự hoàn tác** chỉ vì mã đi vào `catch` hoặc `finally`.

## 2. Ví dụ phản diện: cập nhật một phần rồi mới phát hiện lỗi

Giả sử một giỏ hàng lưu số lượng và tổng tiền, có bất biến `quantity >= 0` và `totalCents >= 0`. Đoạn **minh họa chưa hoàn chỉnh** sau rất dễ mắc lỗi:

~~~java
// Minh họa sai: chỉ là phần thân phương thức của một lớp có trường quantity, totalCents.
void add(int amount, long unitPriceCents) {
    quantity += amount;                      // Trạng thái bị đổi trước
    if (amount <= 0) {
        throw new IllegalArgumentException("amount must be positive");
    }
    if (unitPriceCents < 0) {
        throw new IllegalArgumentException("negative price");
    }
    totalCents += amount * unitPriceCents;  // Có thể tràn long
}
~~~

Có ít nhất ba nguy cơ:

1. `amount <= 0` bị phát hiện **sau khi** `quantity` đã thay đổi.
2. `unitPriceCents < 0` làm phương thức dừng sau một phép cập nhật nhưng trước phép cập nhật tiếp theo.
3. Phép nhân hoặc cộng trên kiểu `long` tràn theo số học Java mà **không tự ném ngoại lệ**; kết quả có thể sai và khiến trạng thái lệch với quy tắc miền nghiệp vụ.

Đưa mọi `if` lên trước hai phép gán cải thiện thiết kế, nhưng chưa đủ nếu sau đó còn phép tính, cấp phát, callback hoặc I/O có thể thất bại. Phải xem toàn bộ các bước có khả năng ném ngoại lệ, không chỉ những dòng `throw` được viết tường minh.

## 3. Mô hình triển khai: kiểm tra → chuẩn bị → ghi nhận

Một cách thiết kế dễ kiểm soát là phân biệt rõ ba giai đoạn:

1. **Validate — kiểm tra:** xác định đầu vào đúng kiểu, không null, nằm trong miền giá trị cho phép; xác nhận trạng thái hiện tại đủ điều kiện; kiểm tra quyền, ràng buộc quan hệ và mọi điều kiện có thể biết trước.
2. **Prepare — chuẩn bị:** tính toán kết quả, sao chép hoặc xây dựng trạng thái mới trong biến cục bộ, có thể phát hiện tràn số bằng `Math.addExact`/`Math.multiplyExact`. Nếu bước này lỗi, trạng thái đang dùng vẫn nguyên vẹn.
3. **Commit — ghi nhận:** đưa trạng thái đã hợp lệ vào sử dụng ở một điểm rõ ràng, ví dụ gán một tham chiếu đến một giá trị bất biến mới. Nếu cập nhật nhiều nơi, cần cơ chế bảo đảm tính nguyên tử thích hợp.

~~~text
đầu vào + trạng thái hiện tại
            |
       kiểm tra hợp lệ?
         /        \
       không       có
        |           |
   ném exception  dựng trạng thái đề xuất
     không sửa       |
    trạng thái    chuẩn bị thành công?
                  /          \
                không         có
                 |             |
             ném exception    commit
             không sửa          |
            trạng thái      trạng thái mới hợp lệ
~~~

Một quy tắc hữu ích: **cố gắng chuyển những thao tác có thể lỗi sang trước thời điểm commit**, còn thao tác commit nên ngắn, đơn giản và không gọi mã người dùng tùy ý. Đừng nhầm “đã kiểm tra hết `if`” với “không còn câu lệnh nào có thể ném ngoại lệ”.

### Các kiểm tra thường cần

| Loại ràng buộc | Ví dụ Java / cách đánh giá | Hậu quả nếu bỏ qua |
|---|---|---|
| Null | `Objects.requireNonNull(value, "value")` | NPE xảy ra giữa chừng thay vì tại ranh giới API |
| Miền số | `amount > 0`, `discount <= 100` | Cập nhật giá trị âm/vượt giới hạn |
| Chỉ số | `index >= 0 && index < size` | `IndexOutOfBoundsException` sau tác dụng phụ khác |
| Quan hệ giữa tham số | `start <= end`, số lượng và đơn giá phù hợp | Dữ liệu nội bộ bất nhất |
| Trạng thái đối tượng | đủ tồn kho, đang mở, còn quyền thao tác | Thay đổi trong lúc trạng thái không cho phép |
| Ràng buộc trên cả tập hợp | tất cả phần tử hợp lệ; mã không trùng; tổng nhu cầu không vượt ngân sách | Cập nhật từng phần tử rồi phát hiện phần tử lỗi |
| Tràn số | `Math.addExact`, `Math.subtractExact`, `Math.multiplyExact` | Số học bị vòng và làm sai invariant |
| Dữ liệu đầu vào biến đổi được | tạo bản sao hợp lý trước khi duyệt; quy định caller không sửa đồng thời | Mỗi vòng kiểm tra thấy một nội dung khác nhau |

Kiểm tra cần dựa vào **trạng thái cùng thời điểm sẽ ghi**, nhất là khi có nhiều luồng. Kiểm tra sớm ở tầng giao diện hữu ích cho thông báo lỗi nhưng không thay thế được kiểm tra nhất quán tại tầng quản lý trạng thái.

## 4. Ví dụ Java 17+ chạy độc lập: đặt tồn kho theo lô

Yêu cầu: kho lưu số lượng hiện có theo mã hàng; một lần `reserve` có thể yêu cầu nhiều mặt hàng. Nếu **bất kỳ** mã hàng nào không tồn tại, số lượng không hợp lệ hoặc không đủ hàng, toàn bộ lời gọi phải thất bại mà **không giảm tồn kho của mặt hàng nào**.

Thiết kế dùng một `Map` làm trạng thái riêng tư. Phương thức tạo một bản đề xuất trong bộ nhớ, kiểm tra tất cả yêu cầu, tính lượng tồn mới và chỉ gán lại trường trạng thái **sau cùng**. Tất cả phương thức quan sát và cập nhật sử dụng cùng khóa `synchronized` để tránh luồng khác chen vào giữa kiểm tra và commit của đối tượng này.

Lưu chương trình dưới tên **`ValidateBeforeMutationDemo.java`**:

~~~java
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ValidateBeforeMutationDemo {
    static final class Inventory {
        private Map<String, Integer> stock;

        Inventory(Map<String, Integer> initial) {
            Objects.requireNonNull(initial, "initial");
            Map<String, Integer> copy = new HashMap<>();

            for (Map.Entry<String, Integer> entry : initial.entrySet()) {
                String sku = Objects.requireNonNull(entry.getKey(), "sku");
                Integer count = Objects.requireNonNull(entry.getValue(), "count");
                if (count < 0) {
                    throw new IllegalArgumentException("negative initial stock: " + sku);
                }
                copy.put(sku, count);
            }

            stock = copy;
        }

        public synchronized void reserve(Map<String, Integer> request) {
            Objects.requireNonNull(request, "request");

            // Giai đoạn 1: kiểm tra toàn bộ yêu cầu trước khi đổi trạng thái thật.
            Map<String, Integer> validated = new HashMap<>();
            for (Map.Entry<String, Integer> entry : request.entrySet()) {
                String sku = Objects.requireNonNull(entry.getKey(), "sku");
                Integer quantity = Objects.requireNonNull(entry.getValue(), "quantity");
                if (quantity <= 0) {
                    throw new IllegalArgumentException("quantity must be positive");
                }

                Integer available = stock.get(sku);
                if (available == null) {
                    throw new IllegalArgumentException("unknown SKU: " + sku);
                }
                if (quantity > available) {
                    throw new IllegalArgumentException("insufficient stock: " + sku);
                }

                validated.put(sku, quantity);
            }

            // Giai đoạn 2: mọi cập nhật chỉ tác động lên bản sao cục bộ.
            Map<String, Integer> next = new HashMap<>(stock);
            for (Map.Entry<String, Integer> entry : validated.entrySet()) {
                String sku = entry.getKey();
                next.put(sku, next.get(sku) - entry.getValue());
            }

            // Giai đoạn 3: công bố trạng thái mới trong một phép gán.
            stock = next;
        }

        public synchronized int available(String sku) {
            Objects.requireNonNull(sku, "sku");
            Integer count = stock.get(sku);
            if (count == null) {
                throw new IllegalArgumentException("unknown SKU: " + sku);
            }
            return count;
        }
    }

    public static void main(String[] args) {
        Inventory inventory = new Inventory(Map.of("book", 4, "pen", 3));

        try {
            inventory.reserve(Map.of("book", 2, "pen", 5));
        } catch (IllegalArgumentException ex) {
            System.out.println("Rejected: " + ex.getMessage());
        }

        System.out.printf("After failure: book=%d, pen=%d%n",
                inventory.available("book"), inventory.available("pen"));

        inventory.reserve(Map.of("book", 2, "pen", 1));

        System.out.printf("After success: book=%d, pen=%d%n",
                inventory.available("book"), inventory.available("pen"));
    }
}
~~~

Biên dịch và chạy (JDK 17 hoặc mới hơn):

~~~bash
javac ValidateBeforeMutationDemo.java
java ValidateBeforeMutationDemo
~~~

**Standard output chính xác:**

~~~text
Rejected: insufficient stock: pen
After failure: book=4, pen=3
After success: book=2, pen=2
~~~

Tại lần đặt đầu tiên, mặt hàng `pen` yêu cầu 5 nhưng chỉ còn 3; yêu cầu `book` dù hợp lệ cũng **không làm giảm** hàng tồn vì trạng thái `stock` chưa được gán lại. Thứ tự duyệt của `Map.of` không được bảo đảm, nhưng mẫu này chỉ có một mặt hàng sai nên thông báo ngoại lệ vẫn ổn định. Lần gọi thứ hai thành công và cập nhật cùng lúc hai mã hàng ở mức trạng thái quan sát được qua API của `Inventory`.

**Vì sao cách này vững hơn việc chỉ thêm `if`?** Sau giai đoạn kiểm tra vẫn còn hai bước có thể gặp sự cố: tạo bản sao `HashMap` và ghi các giá trị vào bản sao đó. Cả hai diễn ra trước `stock = next`; nếu lỗi được ném trong các thao tác đó, trường `stock` vẫn trỏ đến bản cũ. Đối tượng không làm lộ `Map` bên trong cho bên ngoài sửa trực tiếp.

**Giới hạn đã chọn:** các lời gọi truy cập `Inventory` phải đi qua những phương thức đồng bộ nói trên. Caller không được sửa `request` đồng thời khi `reserve` đang duyệt; muốn hỗ trợ đầu vào thay đổi đồng thời phải thiết kế thêm cơ chế snapshot hoặc đồng bộ hóa với chủ sở hữu đầu vào. Ví dụ hoạt động trong bộ nhớ một JVM; nó không tự cung cấp giao dịch cho cơ sở dữ liệu hay nhiều tiến trình.

## 5. Trạng thái cần đúng sau khi ném ngoại lệ đến mức nào?

Các thuật ngữ sau giúp diễn đạt **mức bảo đảm** của API. Đây là cách mô tả về tính an toàn khi có ngoại lệ, không phải phân loại cú pháp `throws` của Java.

| Mức bảo đảm | Khi thao tác thất bại | Ví dụ |
|---|---|---|
| Không có bảo đảm rõ ràng | Đối tượng có thể không còn thỏa bất biến | Trừ số dư rồi ném lỗi trước khi hoàn thành cập nhật |
| **Bảo đảm cơ bản (basic)** | Đối tượng vẫn hợp lệ theo invariant, nhưng một phần thay đổi có thể đã xảy ra | Bộ sưu tập bổ sung được một số phần tử, rồi thao tác tiếp theo lỗi |
| **Bảo đảm mạnh (strong)** | Lỗi không làm thay đổi trạng thái có thể quan sát của đối tượng (*all-or-nothing*) | Dựng bản sao và chỉ thay tham chiếu sau khi chuẩn bị thành công |
| Không thất bại trong phạm vi đã cam kết | Bước thao tác không phát sinh lỗi thuộc phạm vi hợp đồng xác định | Phép gán tham chiếu đơn giản ở commit, trong điều kiện runtime bình thường |

Đối với thao tác nhiều bước mang ý nghĩa một giao dịch, **bảo đảm mạnh** thường là mục tiêu hợp lý: thành công thì áp dụng toàn bộ, thất bại thì không áp dụng gì. Tuy nhiên, không được hứa bảo đảm tuyệt đối cho các lỗi hạ tầng như JVM bị dừng đột ngột, hết bộ nhớ, mất nguồn hoặc hệ thống bên ngoài đã nhận một phần yêu cầu. Xác định phạm vi trạng thái và loại thất bại mà API cam kết bảo toàn.

Đừng suy ra bảo đảm mạnh chỉ từ việc mọi nhánh sai đầu vào đều có `throw` trước khi cập nhật. Một `OutOfMemoryError`, một setter gọi callback, một phép tính chưa kiểm tra hoặc một lời gọi I/O vẫn có thể làm thao tác thất bại giữa quá trình cập nhật.

## 6. Kiểm tra phép tính trước commit: tránh tràn số âm thầm

Kiểm tra `amount >= 0` không có nghĩa `balance + amount` vẫn nằm trong miền số nguyên. Với `long`, phép cộng tràn sẽ tạo kết quả khác theo quy tắc số học cố định của Java, **không** tự phát sinh exception.

Cách kiểm soát khi cập nhật một trường:

~~~java
// Trích đoạn trong lớp có trường private long balance;
// yêu cầu balance >= 0 và không nhận số tiền âm.
public void deposit(long amount) {
    if (amount < 0) {
        throw new IllegalArgumentException("negative amount");
    }
    long next = Math.addExact(balance, amount);
    balance = next;
}
~~~

`Math.addExact` ném `ArithmeticException` nếu `long` bị tràn, và phép gán không được thực hiện. Ở hệ thống nhiều luồng, cả đọc `balance`, kiểm tra, tính toán và gán phải được bảo vệ bằng cùng cơ chế đồng bộ; đoạn này chỉ minh họa logic cập nhật trong ngữ cảnh đơn luồng hoặc khi đã giữ khóa.

Với phép nhân giá × số lượng, tính trước:

~~~java
// Trích đoạn: amount và unitPriceCents đã được kiểm tra hợp lệ.
long addedCents = Math.multiplyExact((long) amount, unitPriceCents);
long nextTotal = Math.addExact(totalCents, addedCents);
int nextQuantity = Math.addExact(quantity, amount);

// Chỉ commit sau khi cả ba phép tính thành công.
totalCents = nextTotal;
quantity = nextQuantity;
~~~

Hai phép gán cuối là hai cập nhật riêng biệt. Với một đối tượng chỉ được quan sát khi đang giữ cùng khóa, thao tác có thể đáp ứng tính nguyên tử ở mức API; nếu có callback, getter không đồng bộ hoặc quan sát trực tiếp giữa hai lần gán, cần thiết kế lại để bảo vệ snapshot nhất quán. Một cách rõ ràng hơn là đóng gói số lượng và tổng tiền trong một đối tượng giá trị bất biến rồi thay một tham chiếu tới đối tượng đó.

## 7. Phân biệt kiểm tra tham số và kiểm tra trạng thái nghiệp vụ

**Tham số sai:** các giá trị tự thân không phù hợp với hợp đồng, chẳng hạn số lượng âm, mã đầu vào `null` hoặc chỉ số vượt miền hợp lệ. Trong nhiều API Java, `IllegalArgumentException`, `NullPointerException` (qua `Objects.requireNonNull`) và `IndexOutOfBoundsException` là những lựa chọn rõ nghĩa.

**Trạng thái không cho phép thao tác:** tham số có thể hợp lệ nhưng đối tượng đang đóng, đơn hàng đã xác nhận hoặc giao dịch đã hoàn tất. `IllegalStateException` thường thích hợp cho vi phạm trạng thái sử dụng API.

**Từ chối theo luật nghiệp vụ bình thường:** ví dụ hết hàng hoặc vượt hạn mức tín dụng. Với hệ thống cần phân loại và xử lý điều kiện này, một exception nghiệp vụ riêng (hoặc kiểu kết quả thể hiện thất bại dự kiến) có thể rõ hơn. Không nhất thiết coi mọi kết quả từ chối như lỗi lập trình. Việc chọn checked hay unchecked phụ thuộc khả năng khôi phục của caller và hợp đồng public đã công bố.

Ví dụ:

~~~java
// Trích đoạn kiểm tra đầu vào và trạng thái trong một phương thức.
Objects.requireNonNull(orderId, "orderId");
if (quantity <= 0) {
    throw new IllegalArgumentException("quantity must be positive");
}
if (closed) {
    throw new IllegalStateException("order is already closed");
}
if (quantity > availableStock) {
    throw new InsufficientStockException(orderId);
}
~~~

Đây là **mã minh họa**, giả định các biến và lớp `InsufficientStockException` đã có trong ứng dụng. Kiểu ngoại lệ và thông điệp lỗi nên là phần có tài liệu của API khi client cần dựa vào chúng.

Trước khi cho phép thao tác thay đổi trạng thái, việc kiểm tra nên xuất phát từ **nguồn sự thật hiện tại**. Với database, giá trị đọc trước đó ở UI hoặc cache có thể đã lỗi thời; với một đối tượng, trạng thái phải được bảo vệ khỏi cập nhật cạnh tranh.

## 8. Tránh lỗi “check-then-act” khi có nhiều luồng

Mẫu không an toàn:

~~~java
// Sai nếu nhiều luồng cùng truy cập mà không đồng bộ.
if (account.balance() >= amount) {
    account.withdraw(amount);
}
~~~

Giữa lần `balance()` và lần `withdraw()`, một luồng khác có thể đã rút tiền. Kiểm tra đúng tại thời điểm đọc nhưng không còn đúng tại thời điểm ghi: đây là lỗi *time-of-check to time-of-use* (TOCTOU).

Giải pháp là đưa **kiểm tra và cập nhật vào cùng một thao tác nguyên tử theo phạm vi trạng thái liên quan**:

- Trong một đối tượng JVM: bảo vệ đọc-kiểm tra-tính-toán-ghi bằng cùng lock, hoặc dùng kỹ thuật CAS với `AtomicReference` tới trạng thái bất biến nếu phù hợp.
- Trong cơ sở dữ liệu: sử dụng transaction cùng mức cô lập thích hợp, khóa hàng hoặc câu lệnh cập nhật có điều kiện; kiểm tra số dòng cập nhật để biết điều kiện còn đúng ở lúc commit.
- Với nhiều đối tượng/tài nguyên: xác định phạm vi khóa và thứ tự khóa nhất quán để tránh deadlock; đừng cho rằng `synchronized` trên một đối tượng tự bảo vệ các đối tượng khác.
- Với input mutable do caller sở hữu: cần bản sao/snapshot hoặc hợp đồng không sửa đồng thời; khóa trên đối tượng đích không tự khóa một `Map` mà caller vẫn đang thay đổi.

**Lưu ý:** `volatile` giúp quy định khả năng nhìn thấy và thứ tự bộ nhớ của một số truy cập nhưng không biến một chuỗi lệnh “kiểm tra rồi cập nhật” thành nguyên tử. Đối với mẫu thay thế một snapshot bất biến, `volatile` có thể giúp công bố tham chiếu an toàn; vẫn cần khóa hoặc CAS để ngăn các writer ghi đè kết quả lẫn nhau.

## 9. Cập nhật nhiều trường, nhiều đối tượng hoặc tài nguyên bên ngoài

### 9.1. Nhiều trường trong một đối tượng

Có thể kiểm tra hết rồi mới thực hiện hai phép gán; nhưng nếu hai trường phải đồng bộ về mặt ngữ nghĩa, hãy ưu tiên gộp thành trạng thái bất biến:

~~~java
// Trích đoạn độc lập về ý tưởng; MoneyState là một record bất biến.
record MoneyState(long cents, int version) {}

private MoneyState state = new MoneyState(0L, 0);

public synchronized void add(long cents) {
    if (cents < 0) {
        throw new IllegalArgumentException("negative cents");
    }
    long nextCents = Math.addExact(state.cents(), cents);
    int nextVersion = Math.addExact(state.version(), 1);
    state = new MoneyState(nextCents, nextVersion);
}
~~~

Trích đoạn nằm trong một lớp chứa `state` và `add`, không phải file Java độc lập. Cả `MoneyState` và giá trị thay thế đều được tạo trước phép gán; API đọc `state` cần có chính sách đồng bộ/công bố nhất quán. Kiểu `record` là một công cụ hữu ích để biểu diễn dữ liệu bất biến khi các thành phần của nó cũng không làm lộ trạng thái mutable.

### 9.2. Hai đối tượng hoặc nhiều tài nguyên

Chuyển tiền từ tài khoản A sang B gồm ít nhất một lần trừ và một lần cộng. Kiểm tra A đủ tiền là cần thiết, nhưng **nếu trừ A thành công rồi ghi B thất bại**, việc kiểm tra trước đó không cứu được tính toàn vẹn. Cần kết hợp kiểm tra trước với một cơ chế commit nguyên tử cho cả hai tài khoản, như khóa cả hai trong cùng JVM theo thứ tự khóa ổn định, hoặc transaction khi dữ liệu ở database.

### 9.3. I/O, mạng và side effect

Ghi file, gửi email, thanh toán, publish event hoặc gọi API từ xa có thể đã gây tác dụng phụ trước khi client nhận được lỗi timeout. Chưa nhận được phản hồi thành công **không chứng minh** thao tác bên kia chưa xảy ra. Vì vậy:

- Kiểm tra dữ liệu trước khi tạo side effect.
- Khi có transaction database, đặt các cập nhật cần nhất quán trong cùng transaction.
- Khi gửi sự kiện cần đi cùng commit dữ liệu, cân nhắc *transactional outbox*.
- Với lệnh có thể gửi lại, thiết kế *idempotency key* để giảm rủi ro thực hiện trùng.
- Xác định trạng thái “đã commit nhưng thông báo thất bại” và cách hòa giải; `finally` chỉ hữu ích cho cleanup, không tự rollback tác dụng phụ.

Tách **lỗi xác thực điều kiện** khỏi **lỗi thực thi sau commit** trong hợp đồng API. Nếu listener hoặc callback được gọi trước khi commit, chúng có thể nhìn thấy trạng thái cũ hoặc ném lỗi làm hỏng quá trình; nếu gọi sau commit, phải tài liệu hóa khả năng “trạng thái đã đổi nhưng thông báo lỗi”.

## 10. Những sai lầm thường gặp

1. **Sửa dữ liệu trước rồi mới `throw`:** nhánh lỗi bỏ lại đối tượng dở dang. Hãy chuyển các kiểm tra và tính toán lên trước commit.
2. **Kiểm tra từng phần tử rồi sửa ngay:** phần tử thứ ba sai nhưng hai phần tử đầu đã đổi. Hãy xác nhận toàn bộ batch hoặc dựng bản kết quả riêng.
3. **Kiểm tra số học nhưng bỏ qua overflow:** `int`/`long` có thể vòng; dùng `Math.*Exact` hoặc giới hạn miền dữ liệu đúng theo nghiệp vụ.
4. **Rely on `assert` for input validation:** `assert` có thể bị tắt khi chạy JVM; không dùng nó làm cơ chế kiểm tra hợp đồng public hay đầu vào không tin cậy.
5. **Giữ tham chiếu mutable của caller:** caller có thể sửa collection sau validation. Sao chép phòng vệ hoặc dùng value object bất biến; nếu có đồng thời phải quy định snapshot.
6. **Cho getter/setter phụ tác dụng phụ vào giai đoạn kiểm tra:** những lời gọi này có thể ném exception hoặc đổi trạng thái. Giảm phụ thuộc vào callback và mã ngoài trước commit.
7. **Ghi đè exception gốc bằng lỗi xử lý lỗi:** rollback, cleanup hay logging cũng có thể lỗi. Bảo toàn `cause`, suppressed exceptions khi phù hợp; không nuốt lỗi gốc.
8. **Bắt `Exception` và giả vờ đã thành công:** nếu không rollback được hoặc không thể chứng minh invariant, phải báo thất bại đúng cách.
9. **Kiểm tra ngoài khóa, cập nhật trong khóa:** kết quả kiểm tra có thể đã lỗi thời; kiểm tra lại bên trong cùng vùng đồng bộ.
10. **Coi việc ném `RuntimeException` là bảo đảm rollback:** loại exception không quyết định trạng thái được hoàn tác. Cần thiết kế thứ tự thao tác và phạm vi giao dịch.

## 11. Cách kiểm thử hợp đồng bảo toàn trạng thái

Thiết kế test từ góc nhìn trạng thái quan sát được **trước và sau lỗi**, thay vì chỉ viết `assertThrows`:

| Kịch bản | Điều cần xác minh |
|---|---|
| Tham số `null`, số âm, vượt giới hạn | Exception đúng loại; trạng thái cũ không thay đổi |
| Một phần tử không hợp lệ ở đầu/giữa/cuối batch | Không có phần tử hợp lệ nào bị commit trước |
| Thiếu tài nguyên, hết hạn mức, trạng thái đã đóng | Invariant vẫn đúng, thông báo đủ rõ để caller quyết định |
| Phép tính tràn | Không commit kết quả số học bị sai |
| Bước chuẩn bị ném lỗi | Trạng thái live vẫn trỏ đến snapshot cũ |
| Hai thao tác cạnh tranh trên cùng tài nguyên | Không phát sinh mất cập nhật, lượng âm hoặc vi phạm điều kiện |
| Lỗi I/O/transaction/callback | Quan sát và ghi nhận chính xác trạng thái đã commit hay chưa |
| Thành công | Hậu điều kiện chính xác; trạng thái mới thỏa tất cả invariant |

Với `Inventory` ở ví dụ đầy đủ, một kiểm thử tối thiểu kiểm tra cả **thất bại và thành công**: thử `reserve(book=2, pen=5)`, xác nhận `book=4`, `pen=3`; sau đó thử `reserve(book=2, pen=1)` và xác nhận `book=2`, `pen=2`. Nếu test chỉ kiểm tra có `IllegalArgumentException`, nó có thể bỏ sót lỗi đã giảm `book` trước khi ném ngoại lệ.

Với hệ thống nhiều luồng, bổ sung test tranh chấp có kiểm soát (barrier/latch), kiểm tra invariant cuối cùng và xem xét các kỹ thuật kiểm thử concurrency thích hợp. Một lần test chạy đúng không chứng minh thuật toán không có race; thiết kế khóa/CAS và lập luận về điểm commit vẫn rất quan trọng.

## 12. Checklist áp dụng khi thiết kế phương thức mới

- **Định nghĩa invariant:** trạng thái thế nào mới hợp lệ, bên ngoài quan sát qua API nào?
- **Viết tiền điều kiện:** thông số, quyền, trạng thái nghiệp vụ; chỉ ra ngoại lệ hoặc kết quả khi sai.
- **Đánh dấu từng tác dụng phụ:** phép gán, sửa collection, ghi DB, I/O, gọi callback, publish event.
- **Xác định bước có thể lỗi:** cấp phát bộ nhớ, phép tính, chuyển đổi kiểu, dịch vụ phụ thuộc, validation có side effect.
- **Chọn điểm commit:** ưu tiên thay snapshot hợp lệ; với nhiều tài nguyên dùng transaction hoặc cơ chế phối hợp phù hợp.
- **Giữ kiểm tra và commit nhất quán khi có concurrency:** cùng lock, CAS, hoặc transaction có điều kiện.
- **Không để thoát tham chiếu mutable trước khi commit:** tránh bên ngoài thấy bản đang chỉnh sửa.
- **Kiểm thử đường lỗi:** sau mỗi ngoại lệ có thể gặp, invariant và mức bảo đảm đã cam kết có còn đúng?
- **Tài liệu hóa các trường hợp không thể rollback:** đặc biệt với mạng, file, thanh toán hoặc sự kiện đã phát.

## 13. Ghi nhớ cốt lõi

**Kiểm tra điều kiện trước khi thay đổi trạng thái** là bước đầu của thiết kế API an toàn khi gặp ngoại lệ. Để có bảo đảm mạnh, cần mở rộng thành quy trình **validate → prepare → commit**, trong đó các phép tính và thay đổi có thể thất bại được thực hiện trên dữ liệu tạm trước khi trạng thái thật trở nên quan sát được. Với đồng thời hoặc tài nguyên bên ngoài, điểm commit phải được bảo vệ bằng khóa, CAS, transaction hay một giao thức tương ứng. Quy tắc quan trọng nhất để tự đánh giá là: *Nếu thao tác ném ngoại lệ tại từng bước, caller sẽ nhìn thấy trạng thái nào?*

### Tham khảo

- [Java SE 17 — `Objects.requireNonNull`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html#requireNonNull(T))
- [Java SE 17 — `Math.addExact` và các phép toán kiểm tra tràn](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html)
- [Java SE 17 — `synchronized` và Java Memory Model (JLS §17)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html)
- [Java SE 17 — `AtomicReference`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-safety-003">Tính toán trên dữ liệu tạm rồi mới công bố kết quả</a>

<details>
<summary>Xem chi tiết</summary>

# Tính toán trên dữ liệu tạm rồi mới công bố kết quả

## 1. Vì sao nên chuẩn bị kết quả trước khi thay đổi trạng thái?

Một phương thức Java có thể thực hiện nhiều công việc liên tiếp: kiểm tra đầu vào, tính giá trị mới, chuyển đổi collection, tạo đối tượng, rồi cập nhật trạng thái đang được các phương thức khác sử dụng. Nếu trạng thái đang hoạt động bị sửa ngay từ đầu, bất kỳ exception nào xuất hiện giữa các bước đều có thể khiến dữ liệu chỉ được cập nhật một phần.

Ví dụ, một đối tượng quản lý cấu hình lưu **quota** và danh sách **topics**. Yêu cầu nghiệp vụ là mỗi lần thêm một topic thì quota cũng tăng tương ứng. Cách làm thiếu an toàn là thêm vào danh sách trước, sau đó mới tính quota mới:

~~~java
// Mã minh họa trong một lớp đang có các trường mutable:
// List<String> topics; int quota;
topics.add(topic);
quota = Math.addExact(quota, increment);
~~~

Nếu **Math.addExact** phát hiện tràn số, nó ném **ArithmeticException** sau khi danh sách đã thay đổi. Người gọi biết lời gọi thất bại, nhưng nội dung đối tượng không còn là trạng thái trước đó. Ngay cả khi câu lệnh thứ hai không thất bại, một luồng đọc không được đồng bộ thích hợp cũng có thể nhìn thấy những lần cập nhật rời rạc.

Mẫu **tính toán trên dữ liệu tạm rồi mới công bố kết quả** giải quyết vấn đề về thứ tự thay đổi:

1. Đọc trạng thái hiện hành **S0**.
2. Kiểm tra điều kiện và tính toán trên biến cục bộ hoặc một bản sao riêng.
3. Tạo trạng thái ứng viên **S1** đã hoàn chỉnh, thỏa các bất biến.
4. Nếu một bước chuẩn bị thất bại, trả lỗi và vẫn giữ **S0**.
5. Nếu mọi bước chuẩn bị thành công, **công bố S1 tại một điểm commit rõ ràng**.

Ở đây, **công bố** (publish/commit) nghĩa là khiến trạng thái mới trở thành trạng thái được các phương thức khác sử dụng. Với một đối tượng trong bộ nhớ, điểm công bố thường là thay một tham chiếu trỏ đến snapshot bất biến. Với database, file hoặc hệ thống phân tán, điểm công bố cần cơ chế riêng của hệ tương ứng.

Mục tiêu chủ yếu là **bảo toàn trạng thái quan sát được khi lỗi xảy ra trước commit**. Cách tổ chức này giúp hiện thực mức bảo đảm **failure atomicity** cho phạm vi trạng thái cụ thể: một thao tác hoặc hoàn tất với kết quả mới, hoặc thất bại mà trạng thái cũ vẫn nguyên vẹn. Tuy nhiên, tính chất đó chỉ đúng khi mọi thay đổi thuộc phạm vi cam kết thực sự được trì hoãn đến điểm công bố.

## 2. Phân biệt dữ liệu tạm và trạng thái đã công bố

| Thành phần | Vai trò | Yêu cầu |
| --- | --- | --- |
| Trạng thái hiện hành S0 | Nguồn dữ liệu đang có hiệu lực | Không bị sửa trong giai đoạn chuẩn bị |
| Biến trung gian | Lưu kết quả tính toán như tổng, giới hạn, dữ liệu đã parse | Chưa tạo thay đổi mà người ngoài quan sát được |
| Bản sao / builder | Chứa cấu trúc dữ liệu mới đang xây dựng | Không chia sẻ các phần mutable có thể bị thay đổi ngoài ý muốn |
| Trạng thái ứng viên S1 | Đối tượng mới chứa **toàn bộ** kết quả hợp lệ | Được xác minh bất biến trước commit |
| Điểm công bố | Thao tác khiến S1 thay cho S0 | Rõ ràng, không phụ thuộc vào nhiều bước có thể lỗi |

Một giá trị **temporary** có thể là **int** cục bộ, **ArrayList** mới tạo, một **record**, một object bất biến hoặc kết quả tính toán được giữ trong transaction chưa commit. Không nhất thiết mọi bài toán đều phải tạo object mới; điều bắt buộc là công việc có thể thất bại không được làm hỏng trạng thái đang được sử dụng.

Cần phân biệt ba thao tác:

- **Sao chép:** tạo nơi chứa dữ liệu mới; có thể là shallow copy.
- **Hoàn thiện và đóng băng ứng viên:** kiểm tra tính hợp lệ, tránh để caller sửa nội dung ứng viên sau khi công bố.
- **Công bố:** thay đổi trạng thái mà phần còn lại của chương trình quan sát được.

Gọi **new ArrayList<>(oldList)** chỉ tạo một danh sách mới. Nếu phần tử của danh sách là object mutable và người khác cũng đang giữ tham chiếu đến chúng, thay đổi nội dung phần tử vẫn ảnh hưởng đến dữ liệu cũ. Một ứng viên an toàn cần kiểm soát cả alias và quyền sở hữu dữ liệu.

## 3. Ví dụ Java 17+ chạy được: chuẩn bị toàn bộ rồi thay snapshot

Ví dụ dưới đây xây một bộ cấu hình có hai thông tin gắn bó với nhau: **quota** và **topics**. Mỗi thao tác sửa cấu hình phải giữ cả hai thành phần nhất quán.

Trạng thái được gói trong **record Snapshot**. Record chứa **List<String>** được phòng vệ bằng **List.copyOf** để người gọi không thể thêm/xóa topic qua danh sách đang công bố. Phương thức **revise** dùng biến cục bộ để kiểm tra, tính toán và xây ứng viên. Chỉ câu lệnh cuối cùng **state = candidate** công bố toàn bộ kết quả.

Tạo tệp **PublishCandidateDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PublishCandidateDemo {
    record Snapshot(int quota, List<String> topics) {
        Snapshot {
            if (quota < 0) {
                throw new IllegalArgumentException("quota cannot be negative");
            }
            topics = List.copyOf(topics);
        }
    }

    static final class TopicSettings {
        private volatile Snapshot state =
                new Snapshot(3, List.of("core"));

        Snapshot snapshot() {
            return state;
        }

        synchronized void revise(int addedQuota, String topic) {
            Objects.requireNonNull(topic, "topic");
            String cleaned = topic.strip();
            if (cleaned.isEmpty()) {
                throw new IllegalArgumentException("topic must not be blank");
            }

            Snapshot before = state;
            int nextQuota = Math.addExact(before.quota(), addedQuota);
            if (nextQuota < 0) {
                throw new IllegalArgumentException("quota cannot be negative");
            }

            List<String> nextTopics = new ArrayList<>(before.topics());
            nextTopics.add(cleaned);
            Snapshot candidate = new Snapshot(nextQuota, nextTopics);
            state = candidate;
        }
    }

    public static void main(String[] args) {
        TopicSettings settings = new TopicSettings();
        System.out.println("Initial: " + settings.snapshot());
        Snapshot beforeInvalid = settings.snapshot();

        try {
            settings.revise(2, "   ");
        } catch (IllegalArgumentException ex) {
            System.out.println("Validation failed: " + ex.getMessage());
        }
        System.out.println("Unchanged after validation: "
                + beforeInvalid.equals(settings.snapshot()));

        settings.revise(2, "  async  ");
        System.out.println("Committed: " + settings.snapshot());
        Snapshot beforeOverflow = settings.snapshot();

        try {
            settings.revise(Integer.MAX_VALUE, "more");
        } catch (ArithmeticException ex) {
            System.out.println("Arithmetic failed: " + ex.getMessage());
        }
        System.out.println("Unchanged after overflow: "
                + beforeOverflow.equals(settings.snapshot()));
        System.out.println("Final: " + settings.snapshot());
    }
}
~~~

Biên dịch và chạy với JDK 17 hoặc mới hơn:

~~~bash
javac --release 17 PublishCandidateDemo.java
java PublishCandidateDemo
~~~

Kết quả đầu ra:

~~~text
Initial: Snapshot[quota=3, topics=[core]]
Validation failed: topic must not be blank
Unchanged after validation: true
Committed: Snapshot[quota=5, topics=[core, async]]
Arithmetic failed: integer overflow
Unchanged after overflow: true
Final: Snapshot[quota=5, topics=[core, async]]
~~~

Đầu ra này minh họa hai đường thất bại và một đường thành công:

- **Đầu vào không hợp lệ:** chuỗi topic chỉ chứa khoảng trắng bị phát hiện khi chuẩn bị. **state** chưa được gán nên snapshot trước/sau lỗi bằng nhau.
- **Thành công:** topic được chuẩn hóa bằng **strip**, quota được tính thành **5**, danh sách ứng viên hoàn chỉnh thành **[core, async]**, sau đó cả hai được công bố cùng một lần thay tham chiếu.
- **Tràn số:** **Math.addExact(5, Integer.MAX_VALUE)** ném **ArithmeticException** trước khi ứng viên hoàn chỉnh được công bố; snapshot hiện hành vẫn là **Snapshot[quota=5, topics=[core, async]]**.

Trong chương trình, **record** tự sinh **toString** và **equals** theo giá trị các thành phần, nên output và phép so sánh snapshot rõ ràng. **List.copyOf** chặn việc sửa cấu trúc danh sách qua snapshot, và **String** bất biến giúp snapshot này không chia sẻ các phần tử mutable. Khi đổi sang danh sách object mutable, cần thêm biện pháp copy/thiết kế bất biến cho phần tử.

Đoạn ví dụ đã được kiểm tra với **javac --release 17** và chạy trên JDK tương thích; output phía trên là kết quả chạy thực tế của chương trình.

## 4. Điểm nào có thể ném exception trong giai đoạn chuẩn bị?

Không chỉ lệnh **throw** tự viết mới tạo đường lỗi. Trong **revise** có nhiều điểm có thể thất bại:

| Bước chuẩn bị | Khả năng thất bại | Trạng thái đã công bố |
| --- | --- | --- |
| **Objects.requireNonNull** | **NullPointerException** nếu topic là null | Giữ S0 |
| **strip** và kiểm tra rỗng | Từ chối topic không hợp lệ | Giữ S0 |
| **Math.addExact** | **ArithmeticException** khi tràn int | Giữ S0 |
| Kiểm tra quota âm | **IllegalArgumentException** | Giữ S0 |
| Sao chép danh sách | Có thể thất bại khi tạo dữ liệu ứng viên | Giữ S0 trong phạm vi thiết kế thông thường |
| Tạo **Snapshot** | Constructor kiểm tra bất biến hoặc từ chối thành phần không hợp lệ | Giữ S0 |
| **state = candidate** | Thay tham chiếu tới kết quả hoàn chỉnh | S1 trở thành trạng thái hiện hành |

Không nên diễn giải bảng như một lời bảo đảm phục hồi khỏi mọi **Error** nghiêm trọng, sự cố JVM hoặc process bị dừng bất kỳ lúc nào. Bảng mô tả những đường exception thông thường trong mô hình trạng thái RAM.

Điểm thiết yếu: các lệnh có thể ném lỗi nằm **trước** việc công bố. Nếu đổi vị trí thành **state = candidate** rồi mới gọi callback có thể ném exception, hợp đồng “mọi exception đều giữ S0” không còn đúng. Callback thất bại sau commit có thể cần được báo là lỗi thông báo riêng, chứ không phải lỗi của thao tác cập nhật chính.

## 5. Tại sao nên dùng một snapshot chung thay vì cập nhật từng trường?

Giả sử class có hai trường độc lập:

~~~java
// Mã minh họa bên trong một class chứa các trường mutable.
int quota = 3;
List<String> topics = new ArrayList<>(List.of("core"));

void reviseUnsafe(int increment, String topic) {
    topics.add(topic); // Trạng thái thật đã thay đổi.
    quota = Math.addExact(quota, increment); // Có thể ném lỗi.
}
~~~

Với cách lưu hai trường mutable riêng, phải tính toán đúng thứ tự và xử lý cả việc ghi hai thành phần. Kể cả khi kiểm tra phép cộng trước, vẫn cần cân nhắc lỗi hoặc observer xuất hiện giữa các cập nhật. Nếu muốn tất cả reader nhìn thấy một cặp **quota + topics** nhất quán, việc gộp chúng vào một snapshot giúp biến một thao tác cập nhật nhiều trường thành một thao tác công bố tham chiếu.

Chuyển sang mô hình:

~~~text
Trước chuẩn bị:  current ──► Snapshot(quota=3, topics=[core])
                              |
Chuẩn bị riêng:              ▼
                       candidate(quota=5, topics=[core, async])
                              |
Commit một lần: current ─────┘
~~~

Sơ đồ tập trung vào **quyền quan sát**: ứng viên chưa được gắn vào **current** và cũng chưa bị rò rỉ qua API công khai. Nếu tất cả dữ liệu ứng viên đều được giữ riêng, exception trong giai đoạn tạo ứng viên không thay đổi snapshot hiện hành.

Đổi lại, cách này có thể phải cấp phát object và copy collection mỗi lần cập nhật. Với trạng thái lớn và cập nhật tần suất cao, cần cân nhắc cấu trúc dữ liệu bền vững (persistent), chia nhỏ aggregate có lý do nghiệp vụ, hoặc transaction/khóa phù hợp. Đừng phá bảo đảm nhất quán chỉ để tránh một lần cấp phát khi chưa đo được vấn đề hiệu năng thực tế.

## 6. Bảo vệ dữ liệu tạm khỏi alias và rò rỉ tham chiếu

Một bản sao chỉ có ích nếu việc sửa nó không làm thay đổi trạng thái gốc. Các tình huống sau cần phân biệt:

### 6.1 Sao chép phần vỏ nhưng dùng chung phần tử mutable

~~~java
// Mã minh họa: Task là một object mutable có setStatus(...).
List<Task> candidate = new ArrayList<>(currentTasks);
candidate.get(0).setStatus("DONE");
~~~

Danh sách mới vẫn trỏ tới cùng **Task** như danh sách cũ. **setStatus** làm thay đổi object được chia sẻ; trạng thái gốc đã thay đổi dù chưa commit. Muốn chuẩn bị riêng cần dùng value object bất biến, defensive copy từng phần tử, hoặc biểu diễn kết quả bằng các giá trị mới không đụng vào object đang được dùng chung.

### 6.2 Công bố một collection mà người khác còn quyền sửa

~~~java
// Mã minh họa: saved là cùng tham chiếu của mutable list.
List<String> candidate = new ArrayList<>(List.of("A", "B"));
this.currentList = candidate;
candidate.add("C");
~~~

Danh sách được công bố tiếp tục thay đổi sau điểm commit. Việc gán tham chiếu không tự làm collection bất biến. Có thể công bố **List.copyOf(candidate)** khi các phần tử đã phù hợp và không có mutable object chia sẻ cần bảo vệ. Trong ví dụ chạy được, record **Snapshot** luôn sao chép phòng vệ ở constructor.

### 6.3 Dữ liệu tạm vô tình được đưa ra bên ngoài

Tránh gọi listener, cache khác, logger có callback tùy biến hoặc trao tham chiếu ứng viên cho plugin trong giai đoạn chuẩn bị nếu các bên đó có thể lưu giữ/sửa dữ liệu hoặc tạo side effect. Một ứng viên không còn riêng tư thì **chưa gán state** chưa chắc nghĩa là **chưa có ai quan sát**.

### 6.4 Immutable wrapper không tương đương deep immutable

**Collections.unmodifiableList(list)** tạo view không cho sửa qua wrapper, nhưng thay đổi trực tiếp vào danh sách gốc có thể xuất hiện qua view. **List.copyOf(list)** tạo danh sách không thể sửa qua API và không phụ thuộc vào cấu trúc mutable ban đầu, nhưng vẫn không tự sao chép sâu object bên trong. Chọn cách làm dựa trên tính chất của phần tử và quyền sở hữu tham chiếu.

## 7. Công bố kết quả an toàn trong chương trình nhiều luồng

Cần giải quyết hai câu hỏi riêng:

1. **Mỗi reader có thấy snapshot hoàn chỉnh và được công bố đúng quy tắc bộ nhớ không?**
2. **Hai writer cạnh tranh có thể ghi đè mất thay đổi của nhau không?**

Trong ví dụ **PublishCandidateDemo**:

- **volatile Snapshot state** cung cấp quan hệ visibility khi một thread gán tham chiếu và thread khác đọc nó. Kết hợp snapshot bất biến, reader lấy một trạng thái hoàn chỉnh tại thời điểm đọc.
- **synchronized void revise(...)** khiến các lần cập nhật đi qua phương thức này tuần tự hóa với nhau. Mỗi writer dùng trạng thái trước đó rồi xây trạng thái mới trong cùng một vùng khóa.
- **snapshot()** trả về một đối tượng tham chiếu được đọc một lần. Nếu cần nhiều trường cùng thời điểm, lưu kết quả trả về vào một biến cục bộ rồi đọc các thuộc tính từ cùng snapshot; tránh gọi **snapshot()** nhiều lần và vô tình kết hợp dữ liệu ở hai phiên bản khác nhau.

Lưu ý: **volatile** cho phép công bố và đọc tham chiếu, nhưng **không** tự biến chuỗi “đọc S0 → tính S1 → gán S1” thành cập nhật không mất dữ liệu khi có nhiều writer. Hai writer có thể cùng đọc S0, mỗi người tính một phiên bản rồi người ghi sau ghi đè người ghi trước.

### Dùng AtomicReference và CAS khi cần cập nhật lạc quan

Một cách khác là dùng **AtomicReference<State>** với ứng viên được tính từ snapshot mỗi lần thử và chỉ commit nếu snapshot ấy còn hiệu lực. Phần mã sau là **minh họa theo ngữ cảnh**, giả sử **State** là record bất biến có các thành phần **quota**, **topics**, **ref** là **AtomicReference<State>**, và **topic** đã được kiểm tra:

~~~java
ref.updateAndGet(before -> {
    int quota = Math.addExact(before.quota(), increment);
    if (quota < 0) {
        throw new IllegalArgumentException("negative quota");
    }

    List<String> next = new ArrayList<>(before.topics());
    next.add(topic);
    return new State(quota, List.copyOf(next));
});
~~~

Hàm cập nhật có thể được gọi **nhiều lần** khi CAS không thắng do writer khác đã sửa trạng thái trước. Vì vậy hàm tính ứng viên phải **không có side effect**: đừng gửi email, gọi thanh toán, tăng counter bên ngoài, ghi tệp hoặc thay object mutable dùng chung bên trong lambda. Những tác dụng phụ đó có thể xảy ra lặp lại dù chỉ một lần cập nhật cuối cùng được công bố.

Nếu lambda ném exception trong lúc chuẩn bị, **updateAndGet** không thực hiện commit thành công cho lần gọi thất bại đó. Cũng cần ghi nhớ cập nhật của **writer khác** có thể đã xảy ra đồng thời; không được hiểu “lần gọi của tôi thất bại” là “mọi trạng thái toàn cục đều đứng yên”.

Đối với khóa thông thường, một phương thức **synchronized** như ví dụ chính dễ đọc hơn khi chi phí tính toán nhỏ. Khi dùng lock-free/CAS, đánh giá retry, pure function và nội dung công bố bằng kiểm thử đồng thời thực tế.

## 8. Quy trình thiết kế một thao tác chuẩn bị–công bố

### Bước 1: Xác định các bất biến

Viết rõ trạng thái hợp lệ: quota không âm; danh sách topic không chứa null; một cấu hình mới phải thay cả quota và topics; dữ liệu đã công bố không thể bị mutate qua tham chiếu của caller.

Nếu bất biến liên quan nhiều trường, cân nhắc biểu diễn bằng một object/record thay vì từng trường công khai.

### Bước 2: Xác định tất cả điểm có thể thất bại

Liệt kê validation, parse, tính toán có thể tràn, các constructor, copy collection, callback và I/O. Không chỉ tính những câu lệnh có từ khóa **throw**. Nếu giai đoạn tưởng là “prepare” đã gọi hệ thống bên ngoài có side effect, hãy tách side effect đó sang bài toán giao dịch riêng.

### Bước 3: Đọc và giữ một snapshot S0

Nếu trạng thái đã được gói vào một object bất biến, chỉ giữ tham chiếu **before**. Nếu các trường mutable còn rời rạc, việc đọc phải theo cơ chế đồng bộ phù hợp để không trộn dữ liệu ở nhiều thời điểm.

### Bước 4: Xây S1 bằng dữ liệu riêng

Dùng biến cục bộ; copy dữ liệu cần sửa; tạo object kết quả mới. Không cập nhật trực tiếp S0. Tránh gọi hàm chưa biết side effect lên object gốc.

### Bước 5: Xác minh S1 trước commit

Bảo đảm tất cả điều kiện nghiệm thu đã được kiểm tra. Constructor của immutable object là một vị trí tốt để bảo vệ invariant, vì không thể tạo một instance được công bố ở trạng thái sai bằng đường thông thường.

### Bước 6: Công bố một lần

Dùng một phép thay tham chiếu được đồng bộ thích hợp, hoặc compare-and-set nếu có writer cạnh tranh. Không chia một thay đổi logic thành nhiều phép gán độc lập mà reader có thể nhìn thấy từng phần.

### Bước 7: Định nghĩa hành vi sau commit

Một lần gửi notification hoặc callback sau commit có thể thất bại. Phân biệt **kết quả cập nhật trạng thái** với **kết quả thông báo** để người gọi không hiểu nhầm rằng một exception sau commit chứng minh thao tác chưa xảy ra.

## 9. Các thao tác không thể quy về một phép gán trong RAM

Mẫu này dễ áp dụng nhất khi trạng thái cần công bố nằm trong một aggregate do một nơi sở hữu. Với nguồn dữ liệu bên ngoài, việc chuẩn bị sẵn vẫn có ích nhưng không thay thế bảo đảm của công cụ lưu trữ.

### Ghi file

Nếu ghi thẳng lên file đích và quá trình ghi bị ngắt, file có thể chứa dữ liệu mới một phần. Một chiến lược là ghi đầy đủ vào **file tạm**, đóng/kiểm tra thành công, rồi thay tên/di chuyển vào vị trí đích bằng cơ chế nguyên tử nếu hệ file hỗ trợ. Java NIO cung cấp **Files.move** với **StandardCopyOption.ATOMIC_MOVE**; khi không được hỗ trợ, thao tác có thể ném **AtomicMoveNotSupportedException**. Không tự động fallback sang thao tác không nguyên tử nếu hợp đồng đòi hỏi atomic replacement. Tính nguyên tử khi thay file cũng khác bảo đảm dữ liệu tồn tại sau mất điện: durability có yêu cầu riêng về flush và hệ thống file.

### Cơ sở dữ liệu

Tính các giá trị tạm ở tầng Java không đảm bảo hai lệnh **UPDATE** cùng thành công. Dùng transaction của database nếu cả hai lệnh phải commit hoặc rollback cùng nhau, và kiểm tra phạm vi transaction, isolation, constraint và các thao tác thực sự tham gia transaction.

Tình huống điển hình là chuyển số dư giữa hai tài khoản. Nếu chỉ trừ số dư A rồi tăng số dư B, lỗi ở bước thứ hai sẽ để lại trạng thái nửa chừng. “Tính trước” giúp phát hiện lỗi đầu vào, nhưng không xử lý lỗi I/O xuất hiện giữa hai câu lệnh ghi riêng. Transaction phù hợp mới cung cấp ranh giới commit/rollback cho dữ liệu đó.

### HTTP, hàng đợi và service khác

Một request gửi đi không còn là dữ liệu tạm riêng của Java process. Timeout không chứng minh phía nhận chưa thực hiện thao tác. Retry có thể gửi lặp và gây duplicate side effect. Cần cơ chế như idempotency key, xác nhận trạng thái, outbox, quy trình bù trừ và hợp đồng thành công một phần tùy kiến trúc. Các biện pháp này có phạm vi bảo đảm khác việc gán một snapshot trong bộ nhớ.

## 10. Khi nào dùng builder, copy và mutable staging?

Không phải bài toán nào cũng cần tạo record mới cho từng phép cộng:

| Tình huống | Cách chuẩn bị phù hợp | Điểm cần lưu ý |
| --- | --- | --- |
| Tính một giá trị đơn | Biến cục bộ kiểu primitive hoặc immutable | Tính đủ và validate trước khi gán trường |
| Sửa cấu hình có nhiều thuộc tính | Builder/record ứng viên bất biến | Constructor kiểm tra bất biến; không để lộ builder |
| Cập nhật một collection | Copy collection, sửa copy, công bố copy bất biến | Shallow copy và mutable element |
| Thay graph object phức tạp | Copy theo quyền sở hữu hoặc persistent data structure | Không còn alias có thể mutate S0 |
| Nhiều writer trên cùng một snapshot | synchronized/lock hoặc AtomicReference CAS | Tránh lost update, retry side effect |
| Ghi nhiều bản ghi DB | Transaction và constraint của DB | Java staging không thay transaction |
| Thay nội dung file | File tạm + atomic move khi được hỗ trợ | Xét durability, cleanup và giới hạn file system |

Đôi khi mutable staging hợp lý để giảm số lần cấp phát: dùng một **ArrayList** tạm để nối, sắp xếp hoặc lọc; chỉ **List.copyOf** khi hoàn thiện. Điều đó an toàn khi staging list là tài sản riêng của thao tác, chưa được gắn vào trạng thái hiện hành và không có reference nào bên ngoài sửa nó trong thời gian chuẩn bị.

Một **builder** cũng chỉ thật sự là staging nếu method **build()** tạo kết quả độc lập hoặc immutable. Nếu builder chỉ chứa tham chiếu trỏ lại tới các collection mutable của trạng thái hiện hành, gọi **build()** không tự mang lại strong guarantee.

## 11. Kiểm thử bảo toàn trạng thái trước–sau exception

Một test tốt phải kiểm tra hai vấn đề: **đường thành công công bố đúng kết quả** và **đường lỗi không công bố ứng viên dở dang**. Trong chương trình chạy được ở trên, phép so sánh **beforeInvalid.equals(settings.snapshot())** và **beforeOverflow.equals(settings.snapshot())** trực tiếp chứng minh các snapshot có cùng nội dung ở hai đường thất bại.

Khi viết unit test thật, có thể tổ chức theo ma trận:

| Kịch bản | Kết quả mong đợi |
| --- | --- |
| Topic hợp lệ và phép tính hợp lệ | Công bố chính xác một snapshot mới |
| Topic null | **NullPointerException**; trạng thái giữ nguyên |
| Topic chỉ có khoảng trắng | **IllegalArgumentException**; trạng thái giữ nguyên |
| Kết quả quota âm | **IllegalArgumentException**; trạng thái giữ nguyên |
| Cộng quota vượt phạm vi **int** | **ArithmeticException**; trạng thái giữ nguyên |
| Lỗi trong constructor ứng viên | Không công bố ứng viên lỗi |
| Caller cố sửa danh sách của snapshot | Thao tác sửa bị từ chối; snapshot không đổi |
| Hai writer đồng thời cập nhật | Không có lost update và không có snapshot chứa nửa kết quả |
| Callback sau commit báo lỗi | Hợp đồng phải chỉ rõ trạng thái đã commit hay chưa |

Nếu đang dùng test framework, đặt snapshot trước lời gọi, assert exception với **assertThrows**, rồi assert toàn bộ trạng thái sau lỗi. Không dừng ở **assertThrows**: một thao tác có thể ném đúng exception nhưng vẫn làm hỏng dữ liệu.

Kiểm tra bất biến cũng chưa luôn đủ để chứng minh **không đổi**. Một đối tượng vẫn có thể hợp lệ nhưng đã thay đổi một phần; hãy phân biệt yêu cầu **basic guarantee** (“vẫn hợp lệ”) và **strong guarantee** (“giữ nguyên trạng thái thuộc phạm vi cam kết”). Với hoạt động nhiều luồng, cần kiểm thử concurrency hoặc phân tích linearizability riêng; unit test tuần tự chỉ chứng minh các đường được kích hoạt.

## 12. Những lỗi thiết kế thường gặp

**Chuẩn bị bằng cách sửa trực tiếp S0.** Lệnh **clear**, **add**, **remove** hay setter gọi trên dữ liệu gốc đã là thay đổi, dù biến trung gian mang tên **candidate**. Hãy tạo object/collection riêng trước khi mutate.

**Gán trạng thái mới quá sớm.** Một lời gọi callback, phép tính hay validate có thể ném exception sau lệnh gán; người gọi có thể nhận lỗi nhưng thay đổi đã được công bố. Đẩy mọi bước có thể thất bại thuộc giai đoạn chuẩn bị lên trước commit và xác định hợp đồng riêng cho bước hậu commit.

**Chỉ sao chép shallow object graph.** Copy **List<Task>** nhưng sửa chính các **Task** chia sẻ với S0 làm hỏng tính độc lập của candidate. Dùng immutable element hoặc copy theo chiều sâu thực sự cần.

**Dùng volatile mà quên nhiều writer.** Việc công bố tham chiếu đúng visibility không giải quyết lost update. Dùng khóa hoặc CAS tùy đặc điểm contention và correctness.

**Giả định exception tự rollback.** **try/catch/finally** điều khiển đường lỗi và cleanup tài nguyên, không tự khôi phục bộ nhớ, database hay file. Muốn khôi phục phải có chiến lược trạng thái/giao dịch đã được thiết kế.

**Cho lambda CAS làm side effect.** Hàm tính ứng viên có thể chạy lặp khi retry. Lambda phải thuần túy theo nghĩa không tạo tác dụng phụ được quan sát bên ngoài và không mutate snapshot nguồn.

**Hiểu nhầm atomic publication là transaction toàn hệ thống.** Một phép thay tham chiếu duy nhất chỉ là điểm commit của aggregate trong RAM. Nó không đồng thời commit các ghi file, database, message broker hoặc request mạng.

**Không nêu rõ phạm vi bảo đảm.** Nếu sự kiện gửi sau commit có thể thất bại, hoặc thao tác có chấp nhận partial progress, API phải mô tả chính xác tác động sau exception để caller xử lý đúng.

## 13. Bảng kiểm khi áp dụng vào mã Java

Trước khi kết thúc một phương thức cập nhật trạng thái, kiểm tra lần lượt:

1. **Trạng thái:** đã gom các dữ liệu phải nhất quán vào một phạm vi rõ ràng chưa?
2. **Đường lỗi:** đã liệt kê mọi lệnh có thể ném exception trước khi hoàn tất chưa?
3. **Không mutate sớm:** quá trình chuẩn bị có giữ nguyên S0 và các mutable object mà S0 trỏ tới không?
4. **Đóng băng:** candidate có được bảo vệ khỏi sửa đổi qua alias, collection hoặc builder bị rò rỉ không?
5. **Bất biến:** đã validate đầy đủ trước khi công bố chưa?
6. **Điểm commit:** có thể xác định một hành động rõ ràng khiến S1 trở thành hiện hành không?
7. **Đồng thời:** đã đảm bảo visibility và tránh lost update nếu nhiều thread ghi chưa?
8. **Side effect:** notification, I/O, gửi event và callback đã có hợp đồng riêng cho lỗi sau commit chưa?
9. **Kiểm thử:** đã so sánh snapshot trước/sau mỗi đường lỗi và xác minh đường thành công chưa?

Kỹ thuật này chuyển phần lớn rủi ro exception sang giai đoạn **xây trạng thái ứng viên**. Nhờ giữ trạng thái cũ còn nguyên cho đến thời điểm công bố, phương thức dễ lập luận, dễ kiểm thử và dễ duy trì hơn. Giới hạn quan trọng nhất là phạm vi: chỉ có thể khẳng định bảo toàn trạng thái khi toàn bộ dữ liệu và side effect thuộc lời hứa thực sự được kiểm soát bởi điểm commit tương ứng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-safety-004">Khôi phục trạng thái và giới hạn của rollback</a>

<details>
<summary>Xem chi tiết</summary>

# Khôi phục trạng thái và giới hạn của rollback khi xử lý exception trong Java

## 1. Vì sao khôi phục trạng thái quan trọng?

Một phương thức có thể gồm nhiều bước: kiểm tra đầu vào, sửa đối tượng, ghi database, gọi dịch vụ khác và trả kết quả. Nếu exception xuất hiện sau khi một vài bước đã hoàn thành thì chương trình có thể để lại dữ liệu mới **dù lời gọi báo thất bại**. Bởi vậy, xử lý exception đúng không dừng ở `try/catch`; còn phải quyết định trạng thái nào còn lại và ai chịu trách nhiệm phục hồi.

**Rollback** là hoàn tác các thay đổi trong một **phạm vi có thể kiểm soát** về trạng thái nhất quán được hợp đồng cam kết. Cần phân biệt:

- **Rollback bộ nhớ:** trả giá trị trường hoặc collection về trước thao tác; hoặc tốt hơn, chuẩn bị một trạng thái độc lập rồi chỉ công bố khi toàn bộ phần việc an toàn đã xong.
- **Rollback transaction:** hủy các thay đổi chưa commit trong transaction của một hệ thống hỗ trợ giao dịch, chẳng hạn database thông qua JDBC.
- **Compensation (bù trừ):** tạo thao tác nghiệp vụ mới để bù hiệu ứng của một thao tác đã thực hiện, ví dụ hoàn tiền sau khi khoản thu đã được ghi nhận. Nó không xóa được toàn bộ lịch sử hay bảo đảm người khác chưa thấy hiệu ứng ban đầu.
- **Recovery (phục hồi):** quá trình đưa hệ thống trở lại hoạt động đúng sau lỗi, có thể gồm rollback, replay log, đối soát, retry an toàn hoặc bù trừ.

Exception **không tự động rollback** dữ liệu Java, file, database hay dịch vụ mạng. `throw` chỉ chuyển luồng điều khiển; mọi thay đổi đã thực hiện vẫn có thể tồn tại.

## 2. Các mức bảo đảm an toàn exception

Khi thiết kế hoặc review API, nên hỏi “sau khi phương thức thất bại, caller quan sát thấy điều gì?” thay vì chỉ hỏi “lỗi được catch ở đâu?”.

| Mức bảo đảm | Trạng thái sau khi thất bại | Ví dụ |
| --- | --- | --- |
| **Strong exception guarantee** | Trong phạm vi đã công bố, thao tác hoặc thành công toàn bộ, hoặc trạng thái quan sát được không đổi | Tính toàn bộ dữ liệu trên bản sao, xác thực rồi thay một tham chiếu |
| **Basic exception guarantee** | Đối tượng vẫn giữ bất biến, không rò rỉ tài nguyên nhưng một phần giá trị có thể đã đổi | Một bộ xử lý lưu tiến độ từng bước và duy trì trạng thái hợp lệ khi lỗi |
| **Không có bảo đảm** | Trạng thái có thể nửa chừng, không nhất quán hoặc không rõ kết quả | Sửa nhiều trường rồi ném lỗi giữa chừng mà không có xử lý phục hồi |
| **No-throw guarantee** | Một thao tác được thiết kế không ném exception trong các điều kiện và phạm vi hợp đồng xác định | Thay tham chiếu đã chuẩn bị sẵn trong vùng đồng bộ thích hợp, không gọi I/O |

Các mức này là **bảo đảm về trạng thái**, không phải loại exception Java. Một ngoại lệ checked như `IOException` không tự có “rollback tốt hơn” `RuntimeException`. Mức *no-throw* cũng không có nghĩa mọi JVM `Error` hay lỗi hạ tầng đều không thể xảy ra; nó đòi hỏi tiền đề và phạm vi cụ thể.

Từ “trạng thái không đổi” phải có **ranh giới**: một object, một aggregate, một transaction DB, hay cả quy trình đa dịch vụ? API có thể bảo đảm mạnh đối với bộ nhớ cục bộ trong khi việc gửi message bên ngoài đã hoàn thành. Không nên ghi “atomic” hoặc “rollback hoàn toàn” nếu chưa xác định phạm vi.

## 3. Điều `try/catch/finally` thực sự làm

Ví dụ dưới đây là mảnh mã bên trong một phương thức đã có biến `balance` và hàm `notifyRemote()`:

```java
balance -= 20;       // Nếu trước đó là 100, bây giờ là 80
notifyRemote();      // Có thể ném IOException
```

Nếu `notifyRemote()` ném lỗi, `balance` **vẫn là 80** trừ khi có logic riêng để phục hồi. Java không tạo snapshot tự động cho trường đối tượng.

Những cơ chế thường bị nhầm với rollback:

- `catch` quyết định cách xử lý hoặc chuyển tiếp exception; không undo bất kỳ phép gán nào.
- `finally` chạy khi rời khối để dọn tài nguyên và trạng thái phụ trợ; tự nó không có semantics giao dịch.
- `try-with-resources` gọi `close()` theo hợp đồng của tài nguyên. Đóng file không xóa byte đã ghi, đóng connection không thay thế việc thiết kế `commit()/rollback()`.
- `synchronized` tự nhả monitor khi khối/method kết thúc vì exception, nhưng các cập nhật đã thực hiện không bị hoàn tác.
- `Thread.currentThread().interrupt()` liên quan truyền tín hiệu hủy/chờ, không phải khôi phục dữ liệu.

Muốn thiết kế phục hồi đúng, trước hết liệt kê mọi **tác dụng phụ** và xác định thời điểm chúng có thể xảy ra.

## 4. Khi nào chuẩn bị độc lập thay được rollback thủ công?

Nếu toàn bộ thay đổi nằm trong **một snapshot bộ nhớ do phương thức sở hữu**, tốt nhất là ngăn trạng thái dở dang xuất hiện ngay từ đầu: chuẩn bị giá trị ứng viên rồi thay bản đã công bố chỉ sau khi mọi kiểm tra thành công.

~~~java
// Minh họa trong lớp có current: Map<String, Integer>.
Map<String, Integer> candidate = new HashMap<>(current);
candidate.put("items",
        Math.addExact(candidate.get("items"), increment));
Map<String, Integer> ready = Map.copyOf(candidate);
current = ready; // Điểm công bố trong phạm vi một writer.
~~~

Nếu `Math.addExact` hoặc tạo `ready` thất bại **trước phép gán cuối**, `current` vẫn tham chiếu bản cũ. Nhưng `HashMap` chỉ sao chép nông: không được mutate các value object dùng chung trong lúc chuẩn bị. Với nhiều writer, cần khóa/CAS phù hợp để tránh mất cập nhật; `volatile` một mình không làm thao tác đọc–tính–ghi thành nguyên tử.

**Ranh giới quan trọng với rollback:** cách này **tránh phải undo** các phép gán nội bộ trước commit; nó không khôi phục DB, file, network request hoặc callback đã gây side effect. Khi đã có thay đổi ngoài snapshot, cần transaction/undo/compensation có hợp đồng riêng và phải tính cả khả năng **thao tác hoàn tác thất bại**. Vì vậy hãy thử prepare–publish trước, chỉ chọn rollback khi phạm vi dữ liệu và cơ chế phục hồi thực sự cho phép.

## 5. Ví dụ Java 17+ chạy được: trước commit và sau commit

Chương trình tạo sổ số dư trong JVM. Thao tác `transfer` tính trên bản sao và chỉ công bố bản mới ở cuối. Thao tác `transferThenNotify` chứng minh **lỗi sau commit** không tự trả số dư về trạng thái cũ.

Lưu file tên **`StateRollbackDemo.java`**:

```java
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class StateRollbackDemo {
    @FunctionalInterface
    interface Notifier {
        void send() throws IOException;
    }

    static final class Ledger {
        private volatile Map<String, Long> balances =
                Map.of("A", 100L, "B", 50L);

        synchronized void transfer(
                String from, String to, long amount, boolean failBeforeCommit)
                throws IOException {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            if (from.equals(to) || amount <= 0) {
                throw new IllegalArgumentException("invalid transfer");
            }

            Map<String, Long> next = new HashMap<>(balances);
            Long source = next.get(from);
            Long target = next.get(to);
            if (source == null || target == null) {
                throw new IllegalArgumentException("unknown account");
            }
            if (source < amount) {
                throw new IllegalArgumentException("insufficient funds");
            }

            next.put(from, source - amount);
            next.put(to, Math.addExact(target, amount));
            if (failBeforeCommit) {
                throw new IOException("simulated failure");
            }

            balances = Map.copyOf(next);
        }

        void transferThenNotify(String from, String to, long amount,
                                Notifier notifier) throws IOException {
            Objects.requireNonNull(notifier, "notifier");
            transfer(from, to, amount, false);
            notifier.send();
        }

        @Override
        public String toString() {
            Map<String, Long> snapshot = balances;
            return "A=" + snapshot.get("A") + ", B=" + snapshot.get("B");
        }
    }

    public static void main(String[] args) throws IOException {
        Ledger ledger = new Ledger();
        System.out.println("Initial: " + ledger);

        try {
            ledger.transfer("A", "B", 20, true);
        } catch (IOException ex) {
            System.out.println("Before commit: " + ex.getMessage());
        }
        System.out.println("After failure: " + ledger);

        ledger.transfer("A", "B", 20, false);
        System.out.println("After success: " + ledger);

        try {
            ledger.transferThenNotify("A", "B", 5, () -> {
                throw new IOException("delivery timeout");
            });
        } catch (IOException ex) {
            System.out.println("Notify failure: " + ex.getMessage());
        }
        System.out.println("After notify failure: " + ledger);
    }
}
```

Biên dịch và chạy trên JDK 17 trở lên:

```bash
javac -encoding UTF-8 StateRollbackDemo.java
java StateRollbackDemo
```

Standard output:

```text
Initial: A=100, B=50
Before commit: simulated failure
After failure: A=100, B=50
After success: A=80, B=70
Notify failure: delivery timeout
After notify failure: A=75, B=75
```

**Phân tích:**

- Sau khi xây trạng thái dự kiến `A=80, B=70`, nhánh tiêm lỗi ném `IOException` trước phép gán `balances`. Vì vậy người đọc vẫn thấy `100, 50`.
- Lượt không lỗi hoàn tất phép gán map mới; trạng thái đã công bố là `80, 70`.
- Lượt thứ ba chuyển thêm `5` và **commit nội bộ thành công**; rồi notifier ném `IOException`. Dữ liệu sổ vẫn là `75, 75`. Việc caller nhận exception không cho phép suy luận “chuyển tiền chưa diễn ra”; gọi lại toàn bộ phương thức có thể trừ thêm tiền.

Map được công bố bất biến và chứa `Long` bất biến. `synchronized` tuần tự hóa writer `transfer`; `volatile` công bố tham chiếu an toàn cho reader `toString()` đọc cùng một snapshot. Tuy nhiên ví dụ chỉ gồm hai tài khoản trong bộ nhớ, không dùng DB hay mạng thật, không thay thế thiết kế cho tiền thật. Cờ `failBeforeCommit` chỉ là công cụ tiêm lỗi. `Math.addExact` có thể ném lỗi tràn số trước commit nên không làm thay đổi bản công bố.

## 6. Rollback thủ công bằng snapshot: dùng được nhưng dễ thiếu

Với thay đổi nhỏ, có thể ghi nhớ giá trị cũ:

```java
int oldBalance = balance;
try {
    balance -= amount;
    performNextStep();
} catch (Exception original) {
    balance = oldBalance;
    throw original;
}
```

Đây là **đoạn minh họa trong một phương thức** đã có `balance`, `amount`, `performNextStep()`; không biên dịch độc lập. Nó chỉ an toàn khi gán lại `oldBalance` thật sự phục hồi **mọi trạng thái quan sát được** theo hợp đồng. Một số vấn đề thường gặp:

- Cùng thao tác đã sửa nhiều trường, object con, file hoặc gọi dịch vụ ngoài; chỉ gán lại `balance` không đủ.
- Snapshot nông có thể chứa các tham chiếu tới object mutable đã bị sửa.
- Một luồng khác có thể vừa cập nhật hợp lệ; rollback bằng giá trị cũ sẽ **ghi đè cập nhật của luồng đó**.
- Phép hoàn tác cũng có thể thất bại, nhất là khi cần gọi hệ thống phụ thuộc đang gặp sự cố.
- Nếu một bước đã gửi email/thanh toán, nó không có “phép gán đảo” trong JVM.
- `catch (Exception)` không bao gồm `Error`. Không nên mở rộng tùy tiện thành `catch (Throwable)` chỉ để tuyên bố hỗ trợ rollback cho mọi sự cố.

Khi nhiều bước có hàm undo riêng, thường cần hoàn tác **theo thứ tự ngược**: `A → B → C`, rồi `undo(C) → undo(B) → undo(A)`. Nhưng thứ tự ngược **chỉ là điều kiện tổ chức**, chưa bảo đảm undo sẽ thành công hoặc không tạo thêm tác dụng phụ. Một số hành động, ví dụ gửi thông báo, không có thao tác đảo đúng nghĩa.

Nếu có thể, giữ trạng thái cũ cho đến điểm commit và tránh cần rollback thủ công. Nếu bắt buộc dùng undo log, phải ghi chính xác những bước đã hoàn thành, idempotency của thao tác undo và trạng thái nào được ghi bền vững để phục hồi sau crash.

## 7. Transaction JDBC: rollback chỉ trong phạm vi transaction

Khi nhiều lệnh SQL phải cùng thành công hoặc cùng thất bại, dùng **một connection và một transaction**, thay vì dựa vào exception Java. Với JDBC, quy trình cơ bản là tắt auto-commit, chạy tất cả lệnh, `commit()` khi thành công; nếu có `SQLException` trước commit thì thử `rollback()`.

Ví dụ dưới đây là **một phương thức minh họa JDBC**, cần `DataSource` đã cấu hình, driver, bảng `accounts(id, balance)` và DB hỗ trợ transaction. Nó **không phải chương trình độc lập**. Giả định mỗi `id` là khóa duy nhất, số dư dùng kiểu dữ liệu phù hợp, và mỗi cập nhật thành công ảnh hưởng đúng một hàng.

```java
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;

public static void moveMoney(DataSource dataSource,
                             long fromId, long toId, long amount)
        throws SQLException {
    if (amount <= 0 || fromId == toId) {
        throw new IllegalArgumentException("invalid transfer");
    }

    try (Connection conn = dataSource.getConnection()) {
        conn.setAutoCommit(false);
        try {
            try (PreparedStatement debit = conn.prepareStatement(
                    "UPDATE accounts SET balance = balance - ? " +
                    "WHERE id = ? AND balance >= ?")) {
                debit.setLong(1, amount);
                debit.setLong(2, fromId);
                debit.setLong(3, amount);
                if (debit.executeUpdate() != 1) {
                    throw new SQLException("source missing or insufficient funds");
                }
            }

            try (PreparedStatement credit = conn.prepareStatement(
                    "UPDATE accounts SET balance = balance + ? WHERE id = ?")) {
                credit.setLong(1, amount);
                credit.setLong(2, toId);
                if (credit.executeUpdate() != 1) {
                    throw new SQLException("destination missing");
                }
            }

            conn.commit();
        } catch (SQLException original) {
            try {
                conn.rollback();
            } catch (SQLException rollbackFailure) {
                original.addSuppressed(rollbackFailure);
            }
            throw original;
        }
    }
}
```

Nếu cập nhật đích gặp lỗi **trước `commit()`** và DB cho phép rollback thành công, cập nhật trừ ở nguồn cùng transaction cũng bị hủy. Điều kiện `balance >= ?` đặt trực tiếp trong lệnh `UPDATE` giúp tránh lỗi đọc số dư ở Java rồi bị thay đổi bởi transaction cạnh tranh trước khi ghi. Tuy nhiên hệ thống thật còn cần thiết kế isolation, locking, deadlock retry, constraints, overflow, độ chính xác số tiền và connection pool.

**Các giới hạn quan trọng:**

1. `rollback()` tác động đến **transaction của connection đó**. Nó không xóa email đã gửi, giao dịch đã ghi ở DB khác, file đã ghi hay HTTP request đã được xử lý.
2. Một số DB, storage engine hoặc câu lệnh DDL có semantics commit riêng; kiểm tra tài liệu DB cụ thể.
3. Nếu rollback ném exception, hệ thống chưa thể tuyên bố đã phục hồi thành công. `original.addSuppressed(rollbackFailure)` lưu thông tin lỗi thứ hai.
4. Nếu `commit()` ném exception do mất kết nối, kết quả **có thể không xác định từ phía ứng dụng**: DB có thể đã commit rồi nhưng phản hồi bị mất. Thử rollback sau đó không chứng minh transaction chưa commit.
5. `try-with-resources` đóng `Connection` và `PreparedStatement`; nó không thay thế việc thiết kế ranh giới transaction và rollback rõ ràng.

Trong Java SE/JDBC, ném exception từ mã gọi **không mặc nhiên gọi rollback cho mọi connection**. Framework transaction có thể cung cấp tự động rollback theo luật riêng, nhưng vẫn phải biết phương thức nào mở transaction, lúc nào commit, exception có bị nuốt hay không, và tài nguyên nào thật sự tham gia.

## 8. Rollback một file: đóng stream không xóa dữ liệu đã ghi

Đang ghi giữa file mà phát sinh lỗi có thể để lại nội dung dở dang; `try-with-resources` chỉ quản lý việc đóng handle. Khi thay **toàn bộ nội dung của một file**, phương pháp thường dùng là:

1. Tạo file tạm ở cùng filesystem/thư mục với file đích.
2. Viết và đóng file tạm; chỉ tiếp tục khi đã có nội dung mới đầy đủ.
3. Thay thế file đích bằng thao tác move/rename nguyên tử nếu filesystem hỗ trợ.
4. Nếu phát sinh exception trước thay thế, xóa file tạm, giữ file đích cũ.

Ví dụ mảnh mã trong phương thức đã có biến `Path target`, `String newContent`, khai báo `throws IOException` và import `java.nio.file.Files`, `Path`, `StandardCopyOption`, `java.nio.charset.StandardCharsets`:

```java
Path absolute = target.toAbsolutePath();
Path directory = absolute.getParent();
Path temporary = Files.createTempFile(directory, ".staging-", ".tmp");
try {
    Files.writeString(temporary, newContent, StandardCharsets.UTF_8);
    Files.move(temporary, absolute,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
} finally {
    Files.deleteIfExists(temporary);
}
```

Đây là **mảnh minh họa**, không phải chương trình độc lập. `ATOMIC_MOVE` có thể không được filesystem hỗ trợ và có thể ném `AtomicMoveNotSupportedException`; hành vi thay file đã tồn tại khi move nguyên tử còn phụ thuộc implementation. *Atomic visibility* cũng **khác durability sau mất điện**; hệ thống yêu cầu durability cao phải cân nhắc flush/sync file và directory theo nền tảng. Dọn file tạm trong `finally` cũng có thể thất bại. Không âm thầm fallback sang copy không nguyên tử nếu hợp đồng đòi bảo đảm mạnh.

Ngay cả khi thay file thành công nguyên tử, nó vẫn **không cùng transaction** với một DB hoặc HTTP service mà phương thức có thể đã gọi.

## 9. Vì sao rollback không bao quát hệ thống phân tán?

Mỗi hệ thống có một trạng thái và ranh giới commit riêng. Nếu A đã commit rồi B lỗi, rollback B không thể tự đảo kết quả A. Những sự kiện đã được bên ngoài quan sát cũng không thể “chưa từng xảy ra”.

| Thao tác ngoài JVM | Điều rollback cục bộ không giải quyết | Cơ chế cân nhắc |
| --- | --- | --- |
| Gửi email/SMS/push | Người nhận có thể đã thấy thông báo | Outbox, trạng thái gửi, đính chính khi cần |
| Gọi API thanh toán | Bên nhận có thể đã xử lý nhưng ACK bị mất | Idempotency key, tra cứu trạng thái, bù trừ nghiệp vụ |
| DB A commit rồi DB B lỗi | Hai DB không tự dùng chung transaction | Distributed transaction khi được hỗ trợ, hoặc Saga/outbox |
| Ghi file rồi rollback DB | File ngoài phạm vi transaction DB | Kế hoạch commit, khôi phục và reconciliation |
| Gửi message rồi lỗi | Consumer có thể đã nhận/xử lý | Bảo đảm gửi, deduplication, idempotent consumer |

**Timeout không đồng nghĩa thất bại.** Client có thể đã gửi lệnh; server commit; đường truyền đứt trước phản hồi. Nếu client gửi lại **lệnh mới** vì nghĩ chưa thực hiện, có thể tạo tác dụng phụ hai lần. Với nghiệp vụ quan trọng, cần trạng thái “**chưa rõ kết quả**”, mã request/transaction ổn định và cách tra cứu trạng thái.

### Saga và compensation

Saga chia luồng nhiều hệ thống thành các giao dịch cục bộ và hành động bù trừ tương ứng. Ví dụ đặt chỗ: **giữ chỗ → thu tiền → xác nhận**. Nếu thu tiền không thành công, có thể giải phóng chỗ; nếu đã thu tiền nhưng bước xác nhận lỗi, có thể cần hoàn tiền. Mỗi hành động bù trừ cũng có thể thất bại, nên cần quy tắc retry có giới hạn, ghi tiến độ bền vững, idempotency, giám sát và đối soát. Saga thường dẫn đến *eventual consistency*, **không tự tạo ACID toàn hệ thống**.

Hoàn tiền **không xóa lịch sử thu tiền**; nó là giao dịch mới, có thể phát sinh phí và sự kiện khác. Không nên gọi compensation là “rollback nguyên tử” nếu các bên ngoài vẫn thấy trạng thái trung gian.

### Transactional outbox

Nếu nghiệp vụ cần vừa cập nhật DB vừa phát sự kiện, một cách là ghi **dữ liệu nghiệp vụ và bản ghi outbox trong cùng transaction DB**. Sau commit, một tiến trình đọc outbox và gửi message. Cách này giúp tránh trường hợp DB commit nhưng chương trình chết trước khi ghi ý định gửi; tuy nhiên consumer vẫn có thể nhận cùng message nhiều lần nếu phía gửi mất ACK. Vì vậy cần consumer idempotent và xử lý trùng. Outbox là cơ chế phối hợp có phạm vi cụ thể, **không phải exactly-once end-to-end mặc định**.

## 10. Khi rollback cũng ném lỗi: giữ nguyên nhân gốc

Một lỗi thường gặp là đang xử lý `IOException` thì tác vụ undo cũng ném `IOException` và làm mất lỗi ban đầu. Có thể giữ thông tin bằng `addSuppressed`:

```java
try {
    performOperation();
} catch (IOException original) {
    try {
        undoOperation();
    } catch (IOException undoFailure) {
        original.addSuppressed(undoFailure);
    }
    throw original;
}
```

Đây là **đoạn trong phương thức khai báo `throws IOException`** với hai hàm `performOperation()` và `undoOperation()` đã được định nghĩa. `addSuppressed` chỉ giữ thông tin điều tra, không tự phục hồi dữ liệu. Nếu rollback không thành công, trạng thái phải được ghi nhận **chưa xác minh hoặc cần xử lý tiếp** theo hợp đồng; không được âm thầm báo “đã khôi phục”.

`try-with-resources` cũng có thể gắn exception khi `close()` thất bại vào lỗi chính thông qua cơ chế `getSuppressed()`. Điều này giúp chẩn đoán, nhưng đóng tài nguyên không đồng nghĩa thao tác nghiệp vụ đã được hoàn tác.

Nếu cần thao tác với `InterruptedException`, giữ hoặc khôi phục trạng thái interrupt đúng hợp đồng; đừng gom mọi exception và error thành một “rollback failed” chung làm mất nguyên nhân quan trọng.

## 11. Retry sau lỗi: phân biệt trước commit, sau commit và chưa rõ

| Thời điểm phát sinh lỗi | Điều thường biết | Hành động phù hợp |
| --- | --- | --- |
| Sai tham số trước khi sửa dữ liệu | Chưa thực hiện nghiệp vụ | Sửa đầu vào; không retry nguyên dữ liệu |
| Validation lỗi trên bản staged | Trạng thái công bố không đổi | Sửa nguyên nhân; retry có điều kiện |
| Lỗi DB trước commit, rollback xác nhận thành công | Transaction được hủy trong phạm vi DB đó | Retry nếu lỗi tạm thời và thao tác an toàn |
| Lỗi trong lúc commit/mất phản hồi | Kết quả có thể không rõ | Đối chiếu trạng thái theo transaction/request ID |
| Lỗi notify sau commit | Nghiệp vụ chính đã thực hiện | Không tự gọi lại cả quy trình; phục hồi riêng bước notify |
| Lỗi trong lúc compensation | Hiệu ứng bù trừ có thể chưa rõ | Ghi tiến độ, kiểm tra, retry có giới hạn hoặc can thiệp |

Đặt timeout hợp lý, retry budget, backoff/jitter và idempotency trong các tác vụ có tác dụng phụ. Với thanh toán/chuyển tiền, tránh retry bằng một lệnh mới nếu chưa biết lệnh cũ đã được ghi nhận hay chưa. Không có chính sách retry “an toàn cho mọi exception”.

## 12. Thiết kế tài liệu hợp đồng khi phương thức ném exception

Bên cạnh `@throws`, API cần ghi rõ **trạng thái sau lỗi**. Chẳng hạn:

> Nếu lỗi xuất hiện trước khi cập nhật trạng thái sổ, số dư được công bố không thay đổi. Nếu cập nhật thành công rồi gửi thông báo lỗi, số dư đã đổi; caller phải kiểm tra trạng thái trước khi thử lại.

Nội dung hợp đồng cần trả lời được:

1. **Phạm vi phục hồi** là object, collection, DB transaction hay nhiều hệ thống?
2. **Điểm commit** cụ thể là gì? Có nhiều điểm commit ở các phụ thuộc khác nhau không?
3. Điều gì có thể bị caller hoặc luồng khác quan sát trước commit?
4. Khi lỗi, trạng thái được bảo đảm **không đổi**, **vẫn hợp lệ nhưng có thể thay đổi**, hay **chưa xác định**?
5. Ai là bên sở hữu transaction và có quyền quyết định rollback?
6. Bằng cách nào caller biết nên retry, tra cứu trạng thái hay thực hiện compensation?

Một exception có tên chính xác là hữu ích, nhưng **không thay thế mô tả trạng thái thực**. Nếu bọc ngoại lệ ở ranh giới tầng, giữ `cause` để truy vết và không biến mọi lỗi hạ tầng thành lỗi tham số hay trạng thái.

## 13. Kiểm thử điều kiện phục hồi, không chỉ kiểm tra exception

Một test chỉ assert `assertThrows` chưa chứng minh sự an toàn. Test nên kiểm tra **trạng thái trước/sau**, số lần gọi phụ thuộc, tài nguyên còn mở, dấu vết transaction và các tác dụng phụ.

| Tình huống | Kỳ vọng quan sát |
| --- | --- |
| Tham số không hợp lệ | Không đổi trạng thái, không gọi phụ thuộc |
| Lỗi khi tính bản staged | Bản công bố giữ nguyên, không rò reference bản dở |
| Lỗi giữa hai bước thay đổi có transaction | Rollback đủ những cập nhật cùng transaction; invariant DB giữ đúng |
| Rollback cũng lỗi | Giữ lỗi chính, có thông tin lỗi rollback; trạng thái chưa xác minh |
| Mất kết nối trong `commit()` | Không tự kết luận chưa commit; có quy trình đối chiếu |
| Gửi message lỗi sau commit | Transaction chính vẫn tồn tại, retry không gây cập nhật lặp |
| Hai request đồng thời thay đổi dữ liệu | Không lost update, invariant không bị phá |
| JVM chết ở các thời điểm khác nhau | Phục hồi từ dữ liệu bền vững, không dựa vào biến RAM |

Với chương trình `StateRollbackDemo`, hai assertion trọng tâm là: sau lỗi tiêm **trước commit** vẫn thấy `A=100, B=50`; sau exception notifier thì thấy `A=75, B=75`. Điều này tách rõ **lỗi của bước tổng thể** khỏi **trạng thái phần việc đã commit**.

Trong kiểm thử tích hợp, tiêm lỗi ở đúng ranh giới: trước ghi, sau ghi nhưng trước commit, trong commit, sau commit trước ACK, lúc rollback và khi khởi động lại. Mock exception trong cùng JVM không đủ chứng minh khả năng phục hồi sau mất điện hoặc mất kết nối thật.

## 14. Lỗi phổ biến khi triển khai rollback

- **Tin rằng `catch` tự đảo phép gán:** exception chỉ chuyển luồng điều khiển.
- **Dùng `finally` để phục hồi mọi thứ:** thường chỉ nên dùng cho cleanup; undo có điều kiện và khả năng lỗi riêng.
- **Chụp shallow copy rồi sửa object con:** snapshot không thật sự tách biệt.
- **Gán giá trị cũ khi đa luồng cùng sửa:** dễ ghi đè cập nhật hợp lệ của luồng khác.
- **Rollback một DB rồi tuyên bố toàn bộ request được hoàn tác:** phụ thuộc ngoài transaction không bị ảnh hưởng.
- **Nuốt exception khi rollback thất bại:** làm mất dấu vết, gây kết luận sai về dữ liệu.
- **Coi timeout `commit()` là chắc chắn chưa commit:** có thể gây transaction lặp.
- **Coi compensation là xóa toàn bộ lịch sử:** bù trừ là tác vụ mới, có rủi ro riêng.
- **Retry toàn bộ quy trình sau lỗi gửi thông báo:** có thể thực hiện nghiệp vụ chính thêm lần nữa.
- **Chỉ test happy path:** những bảo đảm rollback cần được thử ở điểm lỗi cụ thể.

## 15. Checklist triển khai thực tế

1. Vẽ thứ tự các bước có tác dụng phụ và các resource tham gia.
2. Ghi rõ **mức bảo đảm sau lỗi** cho từng resource và phạm vi quan sát.
3. Đẩy những thao tác có thể thất bại về trước điểm commit khi có thể.
4. Với trạng thái Java, ưu tiên copy/staging + công bố một lần; bảo vệ đồng thời và object con mutable.
5. Với DB, định nghĩa transaction boundary, isolation, locking và rollback đủ rõ.
6. Với file, xác định yêu cầu atomicity, durability và kế hoạch xử lý file tạm.
7. Với nhiều dịch vụ, thiết kế outbox, idempotency, compensation hoặc cơ chế coordination phù hợp.
8. Giữ lỗi gốc và thông tin lỗi phục hồi; ghi nhận trạng thái khi kết quả chưa rõ.
9. Đối chiếu trước retry trong trường hợp commit hoặc ACK không chắc chắn.
10. Kiểm thử cả trạng thái và side effect ở các mốc trước/sau commit, khi undo lỗi và khi ứng dụng crash.

**Nguyên tắc then chốt:** xử lý exception đúng phải bảo đảm **trạng thái sau lỗi đúng hợp đồng**. Rollback chỉ có ý nghĩa trong phạm vi có thể kiểm soát; sau khi một tác dụng phụ đã được hệ thống ngoài ghi nhận, giải pháp thường là đối chiếu và bù trừ có kiểm soát, không phải một lệnh `catch` có thể “quay ngược thời gian”.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-safety-005">Ngoại lệ không tự hoàn tác file, dữ liệu hay tác động bên ngoài</a>

<details>
<summary>Xem chi tiết</summary>

# Ngoại lệ không tự hoàn tác file, dữ liệu hay tác động bên ngoài

## 1. Hiểu đúng điều mà exception thực hiện

Trong Java, **ném ngoại lệ (`throw`) làm thay đổi luồng điều khiển**, không tự động quay ngược các thay đổi đã xảy ra. JVM tìm `catch` phù hợp bằng cách tháo stack; các khối `finally` liên quan được thực thi theo quy tắc ngôn ngữ, và `try-with-resources` cố đóng tài nguyên được quản lý. Không có bước mặc định nào của Java thực hiện “undo” cho các phép ghi file, thay đổi đối tượng, lệnh SQL đã commit, HTTP request, email hoặc thông điệp đã gửi.

Giả sử một thao tác gồm:

1. Ghi thêm nội dung vào file.
2. Gửi lệnh ghi đơn hàng đến cơ sở dữ liệu.
3. Gọi dịch vụ thanh toán bên ngoài.
4. Ném `IllegalStateException` do bước cuối kiểm tra thất bại.

Exception ở bước 4 **không chứng minh** rằng bước 1–3 đã được hoàn tác. Muốn biết trạng thái thực sau thất bại, phải xét riêng từng hiệu ứng: ghi file đã tới đâu, SQL đang trong transaction nào, bên thanh toán đã nhận/xử lý yêu cầu chưa và chương trình đã làm gì để khôi phục.

Bài này tập trung vào bảo toàn trạng thái khi có exception: phân biệt **giải phóng tài nguyên**, **khôi phục thay đổi**, **tính nguyên tử**, **giao dịch**, và **bù trừ tác động ngoài hệ thống**; thực hành những mẫu phù hợp trong Java 17+.

## 2. Phân biệt năm hành động rất dễ bị nhầm

| Hành động | Mục đích | Tự đảo ngược nội dung đã thay đổi? |
| --- | --- | --- |
| `throw` một exception | Báo rằng luồng xử lý bình thường không thể tiếp tục | **Không** |
| `catch` exception | Cho caller cơ hội xử lý, quyết định tiếp tục hay lan truyền | **Không**, trừ khi mã trong `catch` chủ động khôi phục |
| `finally` | Thực thi tác vụ dọn dẹp khi rời khối `try` trong các tình huống thông thường | **Không**; muốn hoàn tác phải viết logic thích hợp |
| `try-with-resources` | Gọi `close()` đối với tài nguyên khai báo trong ngoặc `try` | **Không mặc định**; `close()` không đồng nghĩa rollback |
| `rollback()` của transaction | Yêu cầu hệ thống giao dịch huỷ các thay đổi chưa commit thuộc transaction đó | **Có trong phạm vi transaction được hỗ trợ**, không bao trùm file, email, HTTP hay giao dịch đã commit |

Ví dụ `FileOutputStream.close()` giải phóng tài nguyên file, nhưng không làm biến mất bytes đã được ghi. Tương tự, `BufferedWriter.close()` có thể flush phần dữ liệu đang đệm; thao tác đóng thậm chí có thể khiến dữ liệu được ghi ra trong quá trình xử lý exception. Ngược lại, kết nối JDBC đóng lại **không phải hợp đồng rollback có thể tin tưởng cho mọi trường hợp**: quản lý transaction cần được thực hiện tường minh hoặc giao cho transaction manager.

Ngoại lệ có thể xuất hiện **sau khi một hiệu ứng thành công**; vì thế “phương thức ném exception” và “phương thức không làm thay đổi gì” là hai khẳng định hoàn toàn khác nhau.

## 3. Trạng thái trong bộ nhớ cũng không tự trở lại như cũ

Java không lưu bản sao đối tượng trước khi đi vào `try`. Nếu đã thay đổi một `List`, `Map`, field của object hoặc một đối tượng chia sẻ giữa các thread, ném exception không hủy phép gán trước đó.

~~~java
import java.util.ArrayList;
import java.util.List;

class MemoryStateExample {
    public static void main(String[] args) {
        List<String> steps = new ArrayList<>();
        steps.add("created");

        try {
            steps.add("charged");
            throw new IllegalStateException("report failed");
        } catch (IllegalStateException e) {
            System.out.println(e.getMessage());
        }

        System.out.println(steps);
    }
}
~~~

Lưu thành `MemoryStateExample.java` rồi chạy:

~~~bash
javac --release 17 MemoryStateExample.java
java MemoryStateExample
~~~

Stdout:

~~~text
report failed
[created, charged]
~~~

Đây là ví dụ Java 17 độc lập: phép `steps.add("charged")` tồn tại sau `catch`. `finally`, nếu được thêm, cũng sẽ không tự xóa phần tử này.

Khi cần cam kết **hoặc thay đổi toàn bộ hoặc giữ nguyên trạng thái cũ**, cách thường rõ hơn là kiểm tra trước, xây dựng kết quả mới trong biến cục bộ rồi cập nhật trạng thái dùng chung tại một điểm cuối có kiểm soát. Ví dụ tính một `List.copyOf(...)` hoàn chỉnh trước khi thay tham chiếu state. Cần nhớ rằng phép gán tham chiếu đơn lẻ không biến toàn bộ chuỗi hiệu ứng đa tài nguyên thành một transaction, và xử lý concurrency vẫn cần cơ chế đồng bộ phù hợp.

## 4. Ghi file: dữ liệu có thể đã thay đổi trước khi exception được ném

### 4.1. Ví dụ Java 17 chạy độc lập: ghi dở và ghi qua file tạm

Ví dụ sau cố tình ném exception **sau khi ghi thêm vào file** để chứng minh dữ liệu không tự hoàn tác. Sau đó nó tạo file ứng viên riêng và cố tình từ chối trước bước xuất bản, để cho thấy thao tác trên **file đích** chưa xảy ra. Cuối cùng nó minh họa lần thay thế bình thường.

Lưu thành **`ExceptionSideEffectDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public class ExceptionSideEffectDemo {
    static void appendThenFail(Path file) throws IOException {
        Files.writeString(file, "|append", StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
        throw new IllegalStateException("failure after append");
    }

    static void stagedReplace(Path target, String next, boolean reject)
            throws IOException {
        Path candidate = Files.createTempFile(
                target.getParent(), "candidate-", ".txt");
        try {
            Files.writeString(candidate, next, StandardCharsets.UTF_8);
            if (reject) {
                throw new IllegalArgumentException("rejected before publish");
            }
            Files.move(candidate, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(candidate);
        }
    }

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("exception-state-");
        Path file = dir.resolve("data.txt");
        try {
            Files.writeString(file, "initial", StandardCharsets.UTF_8);
            try {
                appendThenFail(file);
            } catch (IllegalStateException e) {
                System.out.println("1. " + e.getMessage());
            }
            System.out.println("2. " + Files.readString(file));

            try {
                stagedReplace(file, "candidate", true);
            } catch (IllegalArgumentException e) {
                System.out.println("3. " + e.getMessage());
            }
            System.out.println("4. " + Files.readString(file));

            stagedReplace(file, "published", false);
            System.out.println("5. " + Files.readString(file));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}
~~~

Biên dịch và chạy từ thư mục chứa file với JDK 17 trở lên:

~~~bash
javac --release 17 ExceptionSideEffectDemo.java
java ExceptionSideEffectDemo
~~~

Stdout khi các thao tác file được hệ điều hành cho phép:

~~~text
1. failure after append
2. initial|append
3. rejected before publish
4. initial|append
5. published
~~~

**Vì sao có kết quả như vậy?**

- Ban đầu file `data.txt` chứa `initial`.
- `appendThenFail` thực hiện `Files.writeString(..., APPEND)` và sau đó ném `IllegalStateException`. Sau khi `catch` nhận lỗi, dữ liệu đã trở thành `initial|append`, chứng tỏ exception không đảo ngược việc append.
- `stagedReplace(..., true)` ghi dữ liệu mới vào một file tạm **khác**; nó ném trước `Files.move`. `finally` xóa file tạm, còn file đích vẫn chứa `initial|append`.
- `stagedReplace(..., false)` đi tới `Files.move` để thay file đích bằng nội dung `published`. Đây chỉ là minh họa đường thành công; lệnh `Files.move` có `REPLACE_EXISTING` **không bảo đảm thay thế nguyên tử trong mọi tình huống**, vì chưa dùng `ATOMIC_MOVE`.

Ví dụ cố ý dùng thư mục tạm và xóa trong `finally` để không để lại dữ liệu sau khi chạy thành công. Ở môi trường mà việc tạo, ghi, chuyển hoặc xóa file bị từ chối, chương trình có thể ném `IOException` và không in được toàn bộ stdout. Những exception từ việc dọn dẹp cũng cần được cân nhắc riêng trong mã production.

### 4.2. Tại sao ngay cả phương thức ghi bị lỗi vẫn không chứng minh file còn nguyên?

Ghi file không nhất thiết là một thao tác “tất cả hoặc không có gì”. Một vòng lặp ghi nhiều khối có thể ghi thành công vài khối rồi gặp `IOException`; nội dung file đích có thể đã thay đổi. `Files.writeString` hoặc `OutputStream.write` không cung cấp một hợp đồng transaction cho nhiều thao tác ghi liên tiếp. Buffer trong bộ nhớ, page cache của hệ điều hành và dữ liệu bền vững trên thiết bị lưu trữ còn là những tầng khác nhau.

Ví dụ sai:

~~~java
// Anti-pattern: ghi trực tiếp file đích rồi hy vọng exception tự khôi phục.
Files.writeString(target, generatedText);
runValidationThatMayThrow();
~~~

Đây là đoạn minh họa giả định `target`, `generatedText` và hàm validation đã tồn tại. Nếu validation ném exception, file có thể đã bị thay nội dung.

Cần phân biệt:
- **Trước bước công bố:** tạo kết quả mới trong file tạm, kiểm tra tính hợp lệ, checksum/độ dài hoặc định dạng, rồi mới xét công bố.
- **Bước công bố:** nếu yêu cầu thay thế nguyên tử và file system hỗ trợ, có thể dùng `Files.move(candidate, target, ATOMIC_MOVE)`; bắt `AtomicMoveNotSupportedException` để áp dụng chính sách rõ ràng.
- **Sau công bố:** nếu đã công bố file mới rồi một thao tác khác thất bại, không thể cho rằng file đã quay về bản cũ.

Trong JDK, `ATOMIC_MOVE` là tùy chọn yêu cầu phép move như **một thao tác nguyên tử của file system**, nhưng nhà cung cấp có thể không hỗ trợ và ném `AtomicMoveNotSupportedException`. Khi chỉ định `ATOMIC_MOVE`, hành vi thay thế đích đang tồn tại phụ thuộc implementation và các tùy chọn khác bị bỏ qua theo hợp đồng API; không hứa thay thế đích trên mọi file system. Không âm thầm fallback sang non-atomic move nếu tính nguyên tử là yêu cầu bắt buộc. Thực thi an toàn có thể phải fail-fast hoặc thiết kế một chiến lược phục hồi riêng theo môi trường triển khai.

Ngay cả atomic rename/move cũng **không đồng nghĩa bảo đảm dữ liệu tồn tại sau mất điện**. Cần đánh giá việc flush dữ liệu, `FileChannel.force`, metadata thư mục và bảo đảm của file system/hệ điều hành nếu tính bền vững trước sự cố máy là yêu cầu. Đặc biệt, ghi hai file riêng rồi move từng file không tạo một transaction nguyên tử cho cả hai file.

### 4.3. Phục hồi bằng file tạm có những giới hạn nào?

Thứ tự điển hình:

~~~text
Đọc phiên bản hiện tại
    -> tạo phiên bản mới trong file tạm
    -> xác thực phiên bản mới
    -> công bố phiên bản mới
    -> dọn file tạm/ghi nhận kết quả
~~~

Chiến lược này giới hạn thời gian file đích chịu tác động, giúp lỗi phát sinh **trước khi công bố** không phá dữ liệu đích. Tuy nhiên cần xử lý:
- Lỗi ngay lúc công bố, đặc biệt khi file system không hỗ trợ atomic replacement.
- Tiến trình chết khiến file tạm chưa được xóa; nên có cơ chế dọn file mồ côi an toàn.
- Nhiều writer cạnh tranh: không để phiên bản cũ ghi đè phiên bản mới nếu không được phép; cân nhắc khóa, version/checksum hoặc giao thức đồng bộ phù hợp.
- Quyền truy cập, ownership, attributes và symlink: thay file có thể ảnh hưởng metadata; đừng giả định mọi thuộc tính giữ nguyên.
- Lỗi **sau công bố**: file mới vẫn có thể tồn tại dù caller nhận exception từ bước dọn dẹp hoặc thao tác tiếp theo.

Vì vậy cần định nghĩa rõ **điểm công bố/commit** để có thể trả lời: “Nếu lỗi xảy ra trước và sau điểm này thì trạng thái ra sao?”.

## 5. `finally` và try-with-resources chỉ giải quyết một phần bài toán

`finally` rất phù hợp cho việc thả khóa, dọn file tạm, reset một trạng thái tạm, hoặc giải phóng tài nguyên. Nhưng nó không thể tự suy ra phép hoàn tác hợp lệ. Nếu mã trong `try` đã gửi email, gọi `close()` trên client cũng không thu hồi email.

Ví dụ:

~~~java
try (var writer = Files.newBufferedWriter(path)) {
    writer.write("updated");
    throw new IllegalStateException("later step failed");
}
~~~

Đây là đoạn minh họa không độc lập (giả định `path` đã khai báo và được phép ghi). Khi rời `try` do exception, `writer.close()` được gọi; nó không có nghĩa file trở lại như trước. Với `newBufferedWriter(path)` dùng tùy chọn mặc định, file có thể đã được tạo hoặc bị truncate ngay khi mở. Việc close thường flush dữ liệu đang đệm; không phải rollback.

**Nếu đóng tài nguyên cũng phát sinh lỗi**, try-with-resources áp dụng quy tắc ngoại lệ bị ức chế (*suppressed exceptions*): khi body đã ném exception và `close()` cũng ném, lỗi đóng được gắn vào lỗi chính qua `getSuppressed()`. Với logic `finally` viết thủ công, exception trong `finally` có thể che mất exception ban đầu nếu không xử lý cẩn thận.

Do đó cần tách hai mục tiêu:
- **Resource safety:** tài nguyên được đóng, file tạm được dọn, lock được thả.
- **State/transaction safety:** thay đổi nghiệp vụ có được commit đầy đủ, rollback có thể thực hiện và giới hạn phạm vi phục hồi là gì?

Một chương trình có thể làm tốt resource safety nhưng thất bại về state safety, hoặc ngược lại.

## 6. Database: chỉ transaction đúng phạm vi mới có thể rollback

Với JDBC, mỗi `Connection` mới thường ở chế độ `autoCommit = true`. Trong chế độ này, từng câu lệnh SQL hoàn tất theo cơ chế commit của JDBC/database; một exception ném **sau câu lệnh đã commit** không tự hủy thay đổi. Để nhiều câu lệnh tham gia cùng transaction, cần quản lý `autoCommit(false)`, `commit()` và `rollback()` thích hợp, hoặc sử dụng transaction manager/framework được cấu hình đúng.

Sơ đồ:

~~~text
BEGIN transaction
    UPDATE accounts SET balance = ... WHERE ...
    INSERT INTO ledger (...)
    kiểm tra invariant cuối
    +-- thành công ------> COMMIT
    +-- lỗi trước commit -> ROLLBACK
~~~

Đây là sơ đồ khái niệm, không phải mã SQL có thể chạy nguyên trạng. Khi database hỗ trợ transaction phù hợp và các lệnh tham gia đúng transaction, rollback có thể hủy thay đổi **chưa commit** của các lệnh đó. Nếu đã `commit()` thành công, `rollback()` sau đó không thu hồi commit trước.

Một mẫu JDBC cần bảo vệ nhiều điểm:
1. Chỉ tắt `autoCommit` khi thực sự sở hữu phạm vi transaction; tránh vô tình can thiệp connection do pool/framework quản lý.
2. Thực thi các statement liên quan qua **cùng transaction phù hợp**; không giả định nhiều connection tự tham gia transaction chung.
3. `commit()` sau khi tất cả điều kiện cần cho giao dịch nội bộ hoàn tất.
4. Nếu thất bại trước commit, gọi `rollback()` và cố giữ exception ban đầu; nếu rollback cũng lỗi thì ghi nhận nó như lỗi phụ, không âm thầm nuốt.
5. Phục hồi/đóng connection theo hợp đồng thư viện hoặc trả về pool; tránh để trạng thái transaction bị rò rỉ cho request kế tiếp.

Cần thêm hai ngoại lệ quan trọng đối với suy luận:
- **Không phải mọi storage/DDL/engine đều rollback giống nhau.** Cơ chế transaction và khả năng rollback tùy database, loại lệnh, engine và mức isolation. Không đưa một lệnh bên ngoài phạm vi transaction rồi khẳng định nó sẽ được hoàn tác.
- **`commit()` ném lỗi không phải lúc nào cũng chứng minh transaction chưa commit.** Ví dụ mất kết nối đúng lúc nhận phản hồi có thể tạo trạng thái kết quả không chắc chắn; cần truy vấn lại theo mã giao dịch/định danh có tính idempotent hoặc theo quy trình phục hồi thiết kế trước.

### 6.1. Bẫy “đã commit rồi mới có exception”

~~~text
BEGIN DB transaction
    INSERT order
COMMIT thành công
gửi email xác nhận
email service ném exception
catch -> gọi rollback của DB
~~~

`rollback` ở cuối **không hoàn tác đơn hàng đã commit**. Ngược lại, nếu gửi email trước khi commit và commit thất bại, người dùng có thể nhận “đã đặt hàng” dù cơ sở dữ liệu không ghi nhận đơn. Hai tài nguyên độc lập không tự nằm trong một transaction chung chỉ vì chúng được gọi trong cùng một phương thức Java.

Đối với Spring hay các framework khác, annotation/quy tắc transaction chỉ áp dụng khi transaction manager thật sự bao bọc các resource có hỗ trợ và các điều kiện gọi phù hợp. Ví dụ, transaction DB không tự mở rộng tới HTTP, filesystem hoặc email. Không giả định mọi unchecked/checked exception đều dẫn đến rollback trong mọi cấu hình framework; quy tắc rollback phải được kiểm chứng theo công nghệ sử dụng.

## 7. Tác động bên ngoài: không có nút undo chung

Một lời gọi có side effect bên ngoài có thể là:
- HTTP POST tạo một đơn hàng ở dịch vụ khác;
- gửi email hoặc tin nhắn;
- đẩy event lên message broker;
- trừ tiền, tạo yêu cầu thanh toán hoặc lưu một đối tượng ở remote storage;
- gửi lệnh đến thiết bị hoặc một tiến trình khác.

Ngay khi lời gọi trả thành công hoặc phía nhận thực thi lệnh, một exception Java tại caller **không gửi yêu cầu hủy mặc định**. Thậm chí khi caller thấy timeout, phía nhận có thể đã xử lý thành công rồi nhưng phản hồi bị thất lạc. Đây là **kết quả chưa xác định** chứ không đương nhiên là “thất bại, không phát sinh tác động”.

### 7.1. Retry cần có idempotency và trạng thái truy vết

Ví dụ một API thanh toán nhận khóa idempotency `paymentRequestId` cố định theo **một yêu cầu logic**. Khi HTTP timeout, client có thể truy vấn trạng thái theo định danh hoặc retry **cùng khóa** nếu dịch vụ thật sự cam kết idempotent: các yêu cầu trùng khóa được diễn giải là cùng một tác vụ, không tạo lần trừ tiền thứ hai.

Chỉ đặt header có tên “Idempotency-Key” mà phía server không hỗ trợ lưu dấu và chống race condition thì **chưa có bảo đảm**. Server thường cần cơ chế ghi nhận kết quả theo key, ràng buộc uniqueness và quy tắc về vòng đời key/payload. Với thao tác không idempotent, không retry tự động khi không biết request đầu đã được áp dụng hay chưa.

### 7.2. Transactional outbox

Khi phải cập nhật database **và** gửi event, một chiến lược phổ biến:

~~~text
Trong 1 DB transaction:
    INSERT/UPDATE dữ liệu nghiệp vụ
    INSERT bản ghi outbox
    COMMIT

Sau commit:
    worker đọc outbox
    gửi event
    ghi nhận đã gửi hoặc retry
~~~

Điểm mạnh là dữ liệu nghiệp vụ và bản ghi “cần gửi event” được commit cùng một transaction **trong cùng database/phạm vi giao dịch**. Tránh tình huống nghiệp vụ commit nhưng hoàn toàn mất dấu ý định phát event. Tuy nhiên worker có thể gửi thành công rồi chết trước khi đánh dấu đã gửi, dẫn đến **gửi lại**. Vì thế consumer vẫn cần xử lý idempotent/deduplicate. Outbox **không bảo đảm exactly-once end-to-end** chỉ bằng việc có bảng outbox.

### 7.3. Compensation/Saga

Nếu quy trình đã tạo đơn, giữ hàng và yêu cầu thanh toán ở nhiều hệ thống độc lập, thất bại bước sau có thể kích hoạt hành động bù trừ như hủy giữ hàng hoặc phát lệnh hoàn tiền. Đây là **thao tác mới**, không phải JVM quay ngược thời gian hay một rollback transaction toàn cục. Compensation cũng có thể thất bại và cần retry, giám sát, đối soát; không phải hiệu ứng nào cũng có phép đảo ngược (email không thể “rút lại” đáng tin cậy, sự kiện đã tới người nghe có thể đã kích hoạt tác động tiếp).

Cần thiết kế:
- Định danh giao dịch và trạng thái tiến trình (`PENDING`, `CONFIRMED`, `COMPENSATING`, `FAILED` hoặc trạng thái phù hợp);
- Thứ tự và điều kiện thực thi bước tiếp;
- Hành động bù trừ cụ thể, tính idempotency của từng bước;
- Chính sách retry, giới hạn retry, dead-letter/đối soát thủ công khi cần;
- Quy tắc cập nhật trạng thái khi một kết quả remote không chắc chắn.

Không dùng compensation như lý do để bỏ qua transaction nội bộ đơn giản có thể dùng được.

## 8. Cam kết an toàn dựa trên điểm xảy ra side effect

Với file hoặc dịch vụ bên ngoài, đánh giá mức bảo đảm theo **thời điểm lỗi** thay vì coi mọi exception giống nhau:

| Lỗi xuất hiện ở đâu? | Điều có thể khẳng định | Điều không được suy ra |
| --- | --- | --- |
| Trước khi gửi lệnh/ghi dữ liệu đích | Chưa có side effect **từ bước ấy**; trạng thái nội bộ có thể vẫn cần kiểm tra | Toàn bộ hệ thống chắc chắn chưa thay đổi |
| Khi đang ghi file trực tiếp | File có thể chứa dữ liệu dở dang | File đã tự rollback vì có exception |
| Sau khi thay thế thành công file ứng viên | File đích có thể đã là phiên bản mới | Exception ở bước thông báo sẽ khôi phục file |
| Sau khi gửi yêu cầu remote nhưng trước ACK | Kết quả remote **chưa chắc chắn**; cần hỏi trạng thái hoặc đối soát | Remote chắc chắn chưa xử lý, nên retry vô điều kiện |
| Trong cleanup hoặc compensation | Có thể đã xảy ra một lỗi phụ cần lưu bằng `suppressed`/log | Undo chắc chắn thành công chỉ vì code đã vào `finally` |

Trong phạm vi một object, **basic guarantee** giữ invariant dù giá trị có thể đã đổi; **strong guarantee** giữ nguyên trạng thái quan sát được nếu thất bại trong phạm vi công bố. Đối với mạng, file và nhiều resource, hai thuật ngữ này **không tự mở rộng** thành cam kết toàn ứng dụng.

Một hậu điều kiện rõ ràng hơn câu “exception sẽ rollback tất cả” là:

> Nếu validation từ chối trước publish, file đích không bị thay bằng bản ứng viên. Nếu file đã publish nhưng gửi thông báo thất bại, file đích vẫn có thể là bản mới; hệ thống ghi nhận trạng thái thông báo để xử lý lại có kiểm soát.

Vì vậy, trạng thái **đã xảy ra**, **chưa xảy ra** và **chưa biết** phải được biểu diễn riêng khi tích hợp bên ngoài; một exception tự nó không thể phân biệt đầy đủ ba trường hợp.

## 9. Thiết kế một thao tác an toàn theo từng giai đoạn

Giả sử cần cập nhật cấu hình JSON và phát sự kiện thay đổi cấu hình:

1. **Xác định invariant và phạm vi:** JSON phải hợp lệ, có version mới, dữ liệu hiện tại phải đọc được; event có thể gửi lại an toàn theo version.
2. **Chuẩn bị:** tạo JSON mới trong bộ nhớ; kiểm tra schema, kích thước, checksum/logic liên quan trước khi sửa file đích.
3. **Stage:** ghi một file tạm trong vị trí phù hợp; xác nhận đã ghi đầy đủ. Nếu lỗi giai đoạn này, xóa ứng viên.
4. **Publish:** dùng cơ chế thay thế file phù hợp file system và yêu cầu atomicity; nếu `ATOMIC_MOVE` là yêu cầu, phải xử lý khi không được hỗ trợ.
5. **Ghi nhận phiên bản đã publish:** tránh tình trạng sau crash không biết version nào là phiên bản hiện tại; cần có cách đọc lại trạng thái thực.
6. **Phát event:** chọn cơ chế phù hợp với phạm vi bền vững, ví dụ outbox nếu dữ liệu chính nằm trong DB; thiết kế version/idempotency để consumer tránh xử lý trùng.
7. **Dọn và giám sát:** xóa file tạm không còn dùng, ghi nhận lỗi phục hồi, phân biệt trạng thái “thất bại trước publish” và “publish xong nhưng thông báo lỗi”.

Với file + database + broker là **ba tài nguyên riêng**, quy trình trên không tự trở thành transaction toàn cục. Nếu hệ thống đòi hỏi tính nguyên tử xuyên cả ba, phải thiết kế giao thức nhất quán và trạng thái khôi phục; không giải quyết được bằng một `try/catch` duy nhất.

### 9.1. Tại sao thứ tự side effect ảnh hưởng đến phục hồi?

So sánh hai quy trình:

~~~text
Phương án A:
    gửi email "đã thành công"
    -> ghi dữ liệu
    -> ghi dữ liệu thất bại
Kết quả: email đã gửi, dữ liệu chưa có.

Phương án B:
    commit dữ liệu và outbox trong cùng transaction
    -> worker gửi email dựa trên outbox
    -> gửi thất bại tạm thời, worker retry
Kết quả: dữ liệu đã commit và vẫn giữ yêu cầu gửi để khôi phục.
~~~

Phương án B phù hợp khi dữ liệu và outbox nằm trong transaction có hỗ trợ, worker xử lý idempotent và có cơ chế giám sát. Nó vẫn không thể cam kết email được phát chính xác một lần trong mọi tình huống. Thông điệp gửi đi cần phản ánh trạng thái thực tế: ví dụ sự kiện “đơn đã ghi nhận” khác “thanh toán chắc chắn đã hoàn tất”.

## 10. Các anti-pattern trong xử lý exception và trạng thái

### 10.1. Nuốt exception và thông báo thành công

~~~java
try {
    writeToDisk();
} catch (Exception ignored) {
    // Không báo lỗi, nhưng phía trên vẫn nghĩ toàn bộ thao tác đã thành công.
}
return true;
~~~

Đoạn minh họa không độc lập: `writeToDisk` và phương thức bao quanh chỉ là giả định. Nếu lệnh ghi làm thay đổi một phần file rồi ném exception, trả `true` vừa che lỗi vừa che trạng thái không chắc chắn. Nên truyền lỗi, trả trạng thái có cấu trúc hoặc khởi động phục hồi theo đúng hợp đồng.

### 10.2. Gọi rollback sau mọi exception dù đã commit

~~~text
try:
    commit thành công
    gọi bên ngoài (ném lỗi)
catch:
    rollback và tuyên bố tất cả đã hủy
~~~

Rollback không tác động đến transaction **đã commit**. Cần phân biệt lỗi trước commit, lỗi sau commit và lỗi không rõ commit đã thành công hay chưa.

### 10.3. Dùng `finally` để luôn xóa file đích

~~~java
try {
    produceOutput();
} finally {
    Files.deleteIfExists(target);
}
~~~

Fragment giả định biến và hàm có sẵn. Nếu `target` là sản phẩm chính thức của một thao tác đã thành công, việc xóa trong `finally` lại phá kết quả. Chỉ dọn **tài nguyên tạm thuộc sở hữu của lần gọi**; không xóa dữ liệu chính hoặc dữ liệu do request khác sở hữu.

### 10.4. Retry POST sau timeout mà không có idempotency

Một lần timeout có thể xảy ra **sau khi server đã tạo tài nguyên**. Retry không kiểm soát có thể tạo đơn/trừ tiền hai lần. Dùng mã yêu cầu ổn định và cơ chế tra trạng thái hoặc idempotency được server hỗ trợ.

### 10.5. Tưởng `close()` chính là rollback

Đóng writer/stream không thu hồi bytes đã ghi; đóng client mạng không thu hồi request đã tới server. Kết nối JDBC phải được quản lý transaction đúng hợp đồng; không ủy thác tính chính xác cho giả định về hành vi `close`.

### 10.6. Khôi phục bằng thao tác mới nhưng không chuẩn bị khi thao tác đó thất bại

Ví dụ một API trừ tồn kho, sau lỗi gọi API cộng lại số lượng. Cả hai thao tác có thể mất kết nối hoặc cạnh tranh với giao dịch khác; phép cộng lại cũng có thể sai nếu áp dụng hai lần. Phép bù trừ cần tính idempotency, định danh nguyên nhân và giới hạn concurrency, hoặc một transaction đúng nghĩa nếu có thể.

## 11. Kiểm thử theo điểm lỗi, không chỉ theo loại exception

Để chứng minh lời hứa về bảo toàn trạng thái, chỉ assert `assertThrows(...)` là chưa đủ. Cần kiểm tra **trạng thái sau từng điểm có thể thất bại**.

| Vị trí tiêm lỗi | Cần xác nhận |
| --- | --- |
| Trước khi thay đổi state | File/database/đối tượng vẫn nguyên |
| Sau khi append một phần | Dữ liệu có thể đã thay; hợp đồng có báo đúng trạng thái không |
| Khi tạo file tạm/ghi file tạm | Không ảnh hưởng file đích; cleanup file tạm đúng mức |
| Khi validation từ chối trước publish | Version cũ vẫn tồn tại và có thể đọc |
| Trong khi move/publish | Hệ thống phát hiện failure, đọc lại trạng thái thực, không giả định chắc chắn chưa publish |
| Ngay sau khi commit DB | Rollback tiếp theo không được coi là đã đảo commit |
| Trong lúc gửi request remote | Phân biệt “phản hồi thất bại” và “server chưa thực hiện” |
| Khi cleanup/close lỗi | Exception chính và lỗi phụ không bị đánh đồng; tài nguyên/trạng thái được ghi nhận |
| Khi retry cùng idempotency key | Không tạo thêm hiệu ứng ngoài số lần mà dịch vụ cam kết |

Có thể dùng file tạm thật cho test I/O nhỏ; dùng fake/stub có khả năng ném lỗi **trước/sau khi thay đổi trạng thái** cho service khác; dùng transaction test với database thật khi xác minh rollback. Khi test timeout remote, mô phỏng trường hợp **đã xử lý nhưng phản hồi bị mất**, chứ không chỉ trường hợp server từ chối ngay lập tức.

Một số invariant cần assert:
- Không phát thông báo “thành công” trước khi kết quả thật sự đạt điều kiện công bố.
- Phần dữ liệu chưa commit có thể rollback theo engine và transaction; dữ liệu đã commit không bị nhận nhầm là đã hủy.
- Tệp đích không bị sửa bởi lỗi xảy ra trong bước stage theo hợp đồng.
- Mọi bước có thể lặp lại đều có idempotency hoặc điều kiện an toàn được kiểm chứng.
- Khi không chắc kết quả, hệ thống có trạng thái để điều tra/đối soát, thay vì trả về `false` và coi như không có side effect.

## 12. Checklist khi viết API Java có side effect

1. **Công bố phạm vi bất biến:** nếu phương thức ném exception, phần nào có thể đã thay đổi? Phần nào được bảo đảm giữ nguyên?
2. **Phân biệt resource cleanup với rollback:** `finally`/try-with-resources đóng và dọn; rollback chỉ tồn tại khi tài nguyên/giao thức thực sự hỗ trợ.
3. **Tối thiểu hóa side effect trước validation:** kiểm tra và tính toán trước, ghi qua staging nếu phù hợp.
4. **Định nghĩa điểm commit/publish:** lỗi trước/sau mốc này có hậu quả khác nhau.
5. **Giữ nguyên exception và cause:** lỗi trong cleanup/compensation cần được ghi nhận, tránh che lỗi đầu tiên và tránh nuốt exception.
6. **Dùng transaction đúng ranh giới:** cùng transaction DB thì rollback được theo bảo đảm của DB; giao dịch ngoài DB không tự tham gia.
7. **Chấp nhận trạng thái không chắc chắn từ hệ thống xa:** timeout không chứng minh chưa xử lý; dùng status query, idempotency và đối soát.
8. **Thiết kế bù trừ và retry có kiểm soát:** hành động bù trừ là side effect mới, có thể lỗi và có thể cần chạy lại.
9. **Test trạng thái sau lỗi:** thử lỗi trước ghi, sau ghi, sau commit, sau publish, trong cleanup và khi mất phản hồi.
10. **Không hứa rollback toàn bộ khi không có bằng chứng:** tài liệu API nên nói rõ trường hợp nào bất biến, trường hợp nào thay đổi một phần và cách caller khôi phục.

### Bài tập vận dụng

Thiết kế một phương thức `saveReportAndNotify(Path path, Report report)` có nhiệm vụ sinh nội dung báo cáo, lưu vào file và gửi thông báo qua dịch vụ remote. Hãy nêu trạng thái của file và thông báo khi lỗi xảy ra tại ba vị trí: (a) trước khi ghi ứng viên, (b) sau khi publish file nhưng trước khi gửi thông báo, (c) sau khi dịch vụ remote thực thi thông báo nhưng client bị timeout. Sau đó đề xuất một cơ chế ghi nhận version/trạng thái và retry không tạo thông báo trùng. Nêu rõ bảo đảm nào chỉ áp dụng cho **một file**, bảo đảm nào áp dụng cho **một transaction DB**, và bảo đảm nào cần giao thức riêng cho **tác động bên ngoài**.

Điểm cần nhớ: **exception nói rằng luồng xử lý Java bị gián đoạn; nó không nói rằng thế giới bên ngoài đã quay về trạng thái trước khi phương thức được gọi.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-safety-006">Đóng tài nguyên và hủy đăng ký theo quyền sở hữu</a>

<details>
<summary>Xem chi tiết</summary>

# Đóng tài nguyên và hủy đăng ký theo quyền sở hữu

Khi một thao tác Java mở file, kết nối, stream, socket, lock hoặc đăng ký listener/callback, hệ thống thường cần thêm một thao tác đối xứng: đóng tài nguyên, trả kết nối, giải phóng khóa, hủy subscription hoặc gỡ listener. Nếu cleanup sai thời điểm hoặc sai người chịu trách nhiệm, chương trình có thể rò rỉ tài nguyên, xử lý sự kiện lặp, giữ đối tượng không còn dùng, hoặc làm hỏng tài nguyên đang được một thành phần khác sử dụng.

**Nguyên tắc trung tâm: thành phần nhận quyền sở hữu (owner) chịu trách nhiệm kết thúc vòng đời tài nguyên hoặc đăng ký do mình sở hữu.** Mã chỉ mượn tài nguyên (borrower) không được tự ý đóng tài nguyên ấy. Cách thiết kế này đặc biệt quan trọng khi có exception: luồng điều khiển có thể rời khỏi phương thức trước khi đi qua đoạn cleanup viết ở cuối thân hàm.

Bài này đặt việc đóng tài nguyên và hủy đăng ký trong mục **Bảo toàn trạng thái** của Java Exception: một thao tác thất bại vẫn phải để hệ thống trong trạng thái vòng đời hợp lệ, tránh tiếp tục giữ tài nguyên, callback hoặc tham chiếu không cần thiết.

## 1. Resource ownership là gì?

*Resource ownership* là quy ước xác định đối tượng hoặc phạm vi nào có quyền và nghĩa vụ kết thúc việc sử dụng một tài nguyên. Đây là **hợp đồng thiết kế**, không phải cơ chế mà Java tự gán bằng từ khóa riêng.

Các vai trò phổ biến:

| Vai trò | Ví dụ | Nghĩa vụ |
| --- | --- | --- |
| Owner tạo và giữ tài nguyên | Service mở một `InputStream` để đọc tệp | Đóng stream khi kết thúc phạm vi sử dụng, kể cả khi có exception. |
| Borrower sử dụng tạm | Hàm nhận `InputStream` từ caller chỉ để phân tích | Không đóng stream trừ khi hợp đồng ghi rõ quyền sở hữu được chuyển. |
| Owner của registration | Component gọi `eventBus.subscribe(listener)` và nhận một handle | Hủy đăng ký khi component dừng, bị thay thế hoặc không cần sự kiện nữa. |
| Owner chuyển giao quyền sở hữu | Factory tạo stream rồi trả ra để caller quản lý | Nêu rõ caller có trách nhiệm đóng; factory không đóng trước khi trả. |
| Owner tổng hợp | Một đối tượng tạo ba tài nguyên con và quản lý chúng | Đóng theo thứ tự phù hợp, xử lý cả trường hợp khởi tạo mới được một phần. |

Một ví dụ **minh họa API**, chưa phải chương trình chạy độc lập:

```java
// Caller sở hữu stream được truyền vào.
int parse(InputStream borrowedStream) throws IOException;

// Factory giao quyền sở hữu stream vừa tạo cho caller.
InputStream openReport() throws IOException;

// Đối tượng đăng ký thuộc caller cho đến khi caller close().
Registration subscribe(Listener listener);
```

Hai phương thức đầu đều có `InputStream` nhưng quy tắc đóng hoàn toàn khác nhau. Điều đó phải được nêu trong tên phương thức, Javadoc, tài liệu hợp đồng và/hoặc kiểu đối tượng trả về.

Không phải cứ giữ một biến tham chiếu là trở thành owner. Nhiều đối tượng có thể cùng tham chiếu một tài nguyên nhưng **chỉ nên có một cơ chế xác định rõ lúc nào tài nguyên được giải phóng**. Nếu thiết kế dùng shared ownership hoặc reference counting thì cần mô tả rõ cách phối hợp, không để mỗi thành phần đóng tự phát.

## 2. Vì sao cleanup ở cuối phương thức không đủ?

Xét một đoạn Java **minh họa vấn đề**:

```java
InputStream input = Files.newInputStream(path);
process(input);   // Nếu process ném exception...
input.close();    // ...dòng này không được chạy.
```

Không có gì bảo đảm rằng `process` sẽ hoàn thành bình thường. IOException, lỗi kiểm tra dữ liệu, exception do callback hay các lỗi khác có thể khiến luồng thực thi rời khỏi đoạn code trước `close()`.

Một biến thể dùng `finally` đúng hơn:

```java
InputStream input = Files.newInputStream(path);
try {
    process(input);
} finally {
    input.close();
}
```

`finally` thông thường được thực hiện khi rời khối `try`, kể cả khi có `return` hoặc exception. Tuy nhiên, ngoại lệ xảy ra ở `close()` có thể che khuất exception gốc nếu viết sơ sài; hệ thống cũng không bảo đảm chạy `finally` trong mọi sự cố ngoài tầm kiểm soát của JVM (ví dụ tiến trình bị cưỡng bức kết thúc).

Với `AutoCloseable` và `Closeable`, Java cung cấp **try-with-resources**, thường là lựa chọn tốt nhất cho tài nguyên sở hữu trong phạm vi rõ ràng.

## 3. Try-with-resources: tự động đóng khi rời phạm vi

Cú pháp cơ bản:

```java
try (InputStream input = Files.newInputStream(path)) {
    process(input);
}
```

Đoạn này minh họa việc đọc tệp; `path` và `process` giả định đã được khai báo. Sau khi tài nguyên được tạo thành công, khi rời khối `try`, Java sẽ gọi `input.close()`. Điều đó áp dụng cả khi phần thân thành công lẫn khi nó ném exception.

Đặc điểm quan trọng:

- Biến trong phần khai báo tài nguyên phải có kiểu triển khai `AutoCloseable`; `Closeable` cũng mở rộng `AutoCloseable`.
- Java đóng tài nguyên theo **thứ tự ngược lại với thứ tự khai báo**. Điều này hữu ích khi tài nguyên mở sau phụ thuộc vào tài nguyên mở trước.
- Nếu khởi tạo tài nguyên thứ hai thất bại, tài nguyên thứ nhất đã tạo thành công vẫn được đóng.
- Nếu phần thân và `close()` cùng ném exception, Java ưu tiên exception của phần thân; exception từ `close()` được gắn vào `getSuppressed()` của exception chính.
- Nếu phần thân thành công nhưng `close()` ném exception, exception lúc đóng có thể truyền ra ngoài.
- Try-with-resources không tự biết một tài nguyên là “borrowed” hay “owned”. **Đưa borrowed resource vào danh sách tài nguyên sẽ khiến nó bị đóng**, có thể vi phạm hợp đồng.

Ví dụ với hai tài nguyên **minh họa**:

```java
try (Resource first = openFirst();
     Resource second = openSecond(first)) {
    runOperation(first, second);
}
```

Ở đây các khai báo `Resource`, `openFirst`, `openSecond` và `runOperation` là giả định. Nếu tạo đủ hai tài nguyên, Java đóng `second` trước rồi `first`. Nếu tạo `second` thất bại, `first` vẫn được đóng. Thứ tự này tránh đóng nền tảng của một đối tượng bọc trước khi đóng đối tượng bọc, với điều kiện quan hệ phụ thuộc được thiết kế tương ứng.

### Java 9+ và tài nguyên có sẵn

Từ Java 9, một biến resource đã khai báo ở ngoài, nếu là `final` hoặc **effectively final**, có thể được dùng trực tiếp trong phần resource của `try`:

```java
InputStream owned = Files.newInputStream(path);
try (owned) {
    // Đọc stream do chính phạm vi này sở hữu.
}
```

Đây vẫn là **một thao tác đóng** đối với `owned`. Cú pháp không chứng minh quyền sở hữu; trách nhiệm của lập trình viên là chỉ đưa resource vào `try` khi phạm vi đó được phép đóng nó.

## 4. Khi nào người nhận tham số KHÔNG được close?

Giả sử API nhận một stream do caller tạo:

```java
public static int countBytes(InputStream borrowed) throws IOException {
    byte[] buffer = new byte[1024];
    int total = 0;
    int count;
    while ((count = borrowed.read(buffer)) != -1) {
        total += count;
    }
    return total;
}
```

Đây là **phương thức minh họa**, không chứa lớp hoàn chỉnh để chạy riêng. Nó đọc dữ liệu nhưng **không gọi `close()`**, vì caller là owner. Caller có thể tiếp tục thao tác với stream sau khi gọi, nếu đặc tính stream và vị trí đọc cho phép.

Tài liệu API nên giải thích:

> Phương thức chỉ mượn `input` trong thời gian gọi và không đóng stream. Caller giữ trách nhiệm đóng stream.

Nếu API quyết định tiếp nhận quyền sở hữu thì ghi rõ:

> Phương thức nhận quyền sở hữu stream và sẽ đóng stream sau khi hoàn thành hoặc thất bại.

Tuy nhiên, giao quyền sở hữu tại lời gọi có thể tạo câu hỏi: nếu validation thất bại trước khi API tiếp nhận, ai đóng? Hợp đồng phải xác định **thời điểm chuyển giao quyền sở hữu**. Đối với API khó mô tả, thường dễ bảo trì hơn nếu caller vẫn sở hữu tài nguyên và tự bao lời gọi trong `try-with-resources`.

Cũng đừng tự động đóng `System.in`, `System.out`, shared connection, context dùng chung hoặc stream do framework quản lý. Đóng một tài nguyên borrowed có thể làm hỏng các thao tác không liên quan phía sau.

## 5. Hủy đăng ký listener cũng là cleanup tài nguyên

Một đăng ký listener/subscriber thường tạo liên kết từ publisher tới callback. Publisher có thể giữ tham chiếu mạnh tới listener; listener lại có thể giữ component UI, service, cache hoặc đối tượng lớn. Nếu component biến mất nhưng không hủy registration, callback vẫn có thể được gọi và đồ thị đối tượng vẫn có thể được giữ sống.

Một thiết kế rõ quyền sở hữu trả về **handle** khi đăng ký:

```java
Registration registration = eventHub.subscribe(listener);
try {
    doWork();
} finally {
    registration.close();
}
```

Đây là minh họa với `Registration` giả định là `AutoCloseable` có `close()` không khai báo checked exception. Handle đại diện cho **quyền hủy đúng một đăng ký**; code sở hữu handle phải hủy khi hết nhu cầu.

Có hai kiểu API phổ biến:

| API | Cách cleanup | Rủi ro dễ gặp |
| --- | --- | --- |
| `addListener(listener)` / `removeListener(listener)` | Owner phải gọi `removeListener` với đúng listener instance | Dùng lambda mới lúc remove nên không trùng object đã add. |
| `subscribe(listener)` trả `Registration` hoặc `Subscription` | Owner giữ handle và gọi `close` / `unsubscribe` / `cancel` tương ứng | Bỏ quên handle hoặc nhầm quyền sở hữu khi chia sẻ handle. |

Đối với lambda:

```java
// Dễ sai: hai biểu thức lambda không phải một registration handle.
publisher.addListener(event -> handle(event));
publisher.removeListener(event -> handle(event));
```

Trong đoạn **minh họa**, `publisher` và `handle` chưa định nghĩa. Điều cần nhớ là không được dựa vào hai lần viết cùng mã lambda để đảm bảo xóa đúng đăng ký. Hãy lưu instance:

```java
Listener listener = event -> handle(event);
publisher.addListener(listener);
try {
    // Vòng đời của subscriber.
} finally {
    publisher.removeListener(listener);
}
```

Nếu API có handle độc lập, thường nên giữ handle thay vì phụ thuộc vào đối chiếu callback.

## 6. Thiết kế handle có thể dùng try-with-resources

Khi cần một đăng ký chỉ tồn tại trong phạm vi ngắn, có thể cho `Registration` triển khai `AutoCloseable`:

```java
public interface Registration extends AutoCloseable {
    @Override
    void close(); // Thu hẹp chữ ký: không khai báo checked Exception.
}
```

Đây là **định nghĩa interface độc lập**, không phải ví dụ chương trình có `main`. Nếu `subscribe` trả `Registration`, caller viết:

```java
try (Registration owned = eventHub.subscribe(listener)) {
    eventHub.publish("hello");
}
```

Tài nguyên registration được hủy ngay cả khi callback hoặc code bên trong `try` ném exception. Điều này giúp cleanup gắn với phạm vi lexical.

Lưu ý: `AutoCloseable.close()` có thể khai báo `throws Exception`; một override như trên thu hẹp thành **không ném checked exception**, khiến caller không phải xử lý `Exception` chỉ để hủy một đăng ký đơn giản. Đặc tả `AutoCloseable` **không bắt buộc** mọi implementation phải idempotent. Nếu thiết kế registration cho phép `close()` nhiều lần, cần cài đặt và ghi rõ điều đó.

Khi đăng ký sống lâu hơn một lệnh gọi — ví dụ listener của một component tồn tại nhiều phút — owner nên giữ handle trong field và đóng nó ở phương thức `stop()`/`close()` của component, thay vì tạo một try-with-resources ngắn khiến callback vừa đăng ký đã bị hủy quá sớm.

## 7. Ví dụ hoàn chỉnh Java 17: owner, borrowed resource và registration

Ví dụ mô phỏng ba tình huống:

1. Hai tài nguyên được tạo trong một phạm vi; thao tác thất bại và hai tài nguyên vẫn được đóng theo thứ tự ngược.
2. Caller tạo một tài nguyên, truyền cho hàm chỉ mượn; hàm mượn sử dụng nhưng không đóng.
3. Caller đăng ký một listener, sử dụng handle để hủy, gọi `close()` hai lần theo đúng hợp đồng idempotent của **handle trong ví dụ**, rồi xác nhận không còn subscriber.

Tạo tệp `OwnershipCleanupDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class OwnershipCleanupDemo {

    private static final List<String> EVENTS = new ArrayList<>();

    private interface Registration extends AutoCloseable {
        @Override
        void close();
    }

    private static final class Resource implements AutoCloseable {
        private final String name;
        private boolean closed;

        Resource(String name) {
            this.name = name;
            EVENTS.add("open " + name);
        }

        void work(boolean fail) {
            if (closed) {
                throw new IllegalStateException("resource closed");
            }
            if (fail) {
                throw new IllegalStateException("work failed");
            }
            EVENTS.add("work " + name);
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                EVENTS.add("close " + name);
            }
        }
    }

    private static final class EventHub {
        private final List<Consumer<String>> listeners = new ArrayList<>();

        Registration subscribe(Consumer<String> listener) {
            Objects.requireNonNull(listener, "listener");
            listeners.add(listener);

            return new Registration() {
                private boolean closed;

                @Override
                public void close() {
                    if (!closed) {
                        closed = true;
                        listeners.remove(listener);
                        EVENTS.add("unsubscribe");
                    }
                }
            };
        }

        void publish(String value) {
            for (Consumer<String> listener : List.copyOf(listeners)) {
                listener.accept(value);
            }
        }

        int subscriberCount() {
            return listeners.size();
        }
    }

    private static void failDuringWork() {
        try (Resource first = new Resource("first");
             Resource second = new Resource("second")) {
            second.work(true);
        }
    }

    // Borrower: tuyệt đối không đóng tài nguyên do caller sở hữu.
    private static void useBorrowed(Resource borrowed) {
        borrowed.work(false);
    }

    public static void main(String[] args) {
        try {
            failDuringWork();
        } catch (IllegalStateException ex) {
            EVENTS.add("caught: " + ex.getMessage());
        }

        // Phạm vi này là owner của Resource.
        try (Resource callerOwned = new Resource("caller-owned")) {
            useBorrowed(callerOwned);
        }

        EventHub hub = new EventHub();
        Registration ownedRegistration =
                hub.subscribe(value -> EVENTS.add("event: " + value));

        hub.publish("one");
        ownedRegistration.close();
        ownedRegistration.close(); // Idempotent theo code của ví dụ.
        hub.publish("two");
        EVENTS.add("subscribers: " + hub.subscriberCount());

        EVENTS.forEach(System.out::println);
    }
}
```

Chạy với Java 17+:

```bash
javac -encoding UTF-8 OwnershipCleanupDemo.java
java OwnershipCleanupDemo
```

Kết quả chuẩn:

```text
open first
open second
close second
close first
caught: work failed
open caller-owned
work caller-owned
close caller-owned
event: one
unsubscribe
subscribers: 0
```

Tại sao kết quả như vậy?

- `first` mở trước `second`. Exception xảy ra trong `second.work(true)`, nhưng try-with-resources vẫn đóng `second` rồi `first`, trước khi exception tới `catch` bên ngoài.
- `useBorrowed` chỉ gọi `work(false)`. `callerOwned` được đóng bởi `try` của **caller**, không bởi hàm mượn.
- `subscribe` thêm đúng một listener. Sau khi nhận sự kiện `one`, owner đóng registration. Sự kiện `two` không còn được chuyển cho listener nên không có dòng `event: two`.
- Lần `close()` thứ hai không thay đổi trạng thái do implementation của ví dụ đã kiểm tra `closed`; chỉ xuất hiện một dòng `unsubscribe`.
- `subscriberCount()` bằng 0 chứng minh ở ví dụ đồng bộ này danh sách listener đã được dọn. Trong hệ thống thật, cần xem xét luồng đồng thời và callback đang chạy.

**Giới hạn của ví dụ:** `EventHub` và `Resource` dùng `ArrayList` và cờ boolean để trình diễn thứ tự một luồng; chúng **không thread-safe**. Không dùng nguyên mẫu này làm event bus đa luồng. Trong thực tế, cần chính sách đồng bộ, vòng đời và điều kiện hủy phù hợp.

## 8. Exception xảy ra trong quá trình close: getSuppressed()

Một vấn đề tinh tế: phần thân thao tác ném lỗi A; trong khi dọn dẹp, `close()` lại ném lỗi B. Exception nào nên được truyền ra?

Với try-with-resources, exception từ phần thân là exception chính; exception xảy ra khi đóng được **suppressed** vào exception chính. Điều này bảo toàn cả hai nguyên nhân mà không để lỗi cleanup che mất lỗi ban đầu.

Tạo file `SuppressedCleanupDemo.java`:

```java
import java.io.IOException;

public class SuppressedCleanupDemo {

    private static final class FailingClose implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    public static void main(String[] args) {
        try (FailingClose resource = new FailingClose()) {
            throw new IOException("work failed");
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
            System.out.println(ex.getSuppressed().length);
            System.out.println(ex.getSuppressed()[0].getMessage());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 SuppressedCleanupDemo.java
java SuppressedCleanupDemo
```

Kết quả chuẩn:

```text
work failed
1
close failed
```

`ex.getSuppressed()` là mảng các exception bị suppressed. Trong ví dụ, thao tác chính tạo một `IOException` và `close` tạo một `IOException` khác. Nếu chỉ `close()` lỗi trong khi thân `try` thành công, lỗi từ `close()` có thể trở thành exception chính.

Không nên “sửa” bằng cách nuốt tất cả lỗi `close()`. Lỗi đóng có thể làm mất dữ liệu hoặc cho biết thao tác hoàn tất không chắc chắn (ví dụ khi flush buffer). Cần xác định chính sách cụ thể: caller có cần biết lỗi đóng không, trạng thái có thể khôi phục không, log nên ghi ở đâu.

Một `catch` bên ngoài chỉ nhận exception cuối cùng; nếu muốn điều tra đầy đủ, ghi nhận cả cause và suppressed exceptions bằng hệ thống logging thích hợp.

## 9. Cleanup trong finally, trạng thái và thứ tự ưu tiên lỗi

`finally` vẫn cần thiết cho các cơ chế không biểu diễn tự nhiên bằng `AutoCloseable` hoặc khi phải thực hiện hành động điều kiện. Nhưng hãy tránh cấu trúc:

```java
try {
    doWork();
} finally {
    resource.close(); // Có thể ném và che exception từ doWork().
}
```

Đây là mảnh minh họa, với `doWork` và `resource` được định nghĩa ở nơi khác. Nếu `doWork` ném exception A và `close()` ném exception B, câu lệnh `finally` ở đây có thể khiến exception B thay thế A. Nếu resource triển khai `AutoCloseable`, try-with-resources thường giải quyết việc bảo toàn cả hai tốt hơn.

Khi dùng `finally` với callback registration, nếu `unsubscribe()` cũng có thể ném, phải xác định cách giữ lỗi ban đầu và báo lỗi cleanup. Đừng mặc định swallow exception vì “chỉ là giải phóng”; ngược lại, không nên để lỗi cleanup không quan trọng làm hỏng luồng xử lý nghiệp vụ nếu hợp đồng cleanup thực sự bảo đảm an toàn khi bỏ qua. Quyết định phải dựa trên semantics của API.

Nếu nhiều bước cleanup thủ công, nên bảo đảm một bước thất bại không vô tình ngăn bước giải phóng tài nguyên độc lập còn lại. Try-with-resources hỗ trợ tự động đóng nhiều tài nguyên ngay cả khi việc đóng một tài nguyên ném exception.

## 10. Khởi tạo một phần cũng cần cleanup

Giả sử component cần ba registration. Nếu bước thứ ba thất bại, hai bước đầu vẫn đang tồn tại. Nếu chỉ đóng các handle sau khi tất cả đã khởi tạo thành công, chương trình có thể rò rỉ ngay trong constructor/startup.

Các cách thiết kế:

1. **Tạo và đăng ký trong phạm vi có thể rollback.** Khi thao tác tiếp theo thất bại, đóng những tài nguyên/handle đã tạo trước đó.
2. **Chỉ chuyển quyền sở hữu sau khi khởi tạo thành công.** Factory có thể giữ các tài nguyên trong phạm vi tạm; khi xây dựng xong mới giao một đối tượng composite có `close()` quản lý toàn bộ.
3. **Làm rõ mỗi điểm lỗi.** Nếu bước mở kết nối thứ hai ném, kết nối thứ nhất có được đóng không? Nếu callback đã đăng ký mà phần còn lại fail, handle cũ có bị hủy không?

Đoạn dưới minh họa một composite scope khi subscription trả handle `AutoCloseable`. Đây là **mã minh họa theo ý tưởng**, chưa bao gồm `subscribeA`, `subscribeB`, `run` và kiểu handle thực tế:

```java
try (Registration first = subscribeA();
     Registration second = subscribeB()) {
    run();
}
```

Nếu `subscribeB()` ném exception, `first` được đóng. Nhưng nếu `subscribeB()` tạo đăng ký rồi ném **trước khi trả handle**, code tại caller chưa nhận quyền hủy đăng ký đó. Do vậy, chính implementation của `subscribeB()` phải cleanup mọi phần tài nguyên mà nó đã tạo trước khi báo lỗi. Đây là điểm phân biệt giữa **cleanup của caller sau khi nhận handle** và **cleanup nội bộ khi factory thất bại giữa chừng**.

### Giao quyền sở hữu sau khi hoàn tất xây dựng

Một composite có thể sở hữu nhiều resource con và đóng chúng theo thứ tự ngược. Owner composite cần giữ trạng thái `closed` và xác định có thể gọi `close` nhiều lần hay không. Nếu người dùng không thể tạo composite hợp lệ do ném exception giữa chừng, factory/builder có trách nhiệm đóng phần đã tạo; không được đẩy trách nhiệm sang caller khi caller chưa nhận được đối tượng.

## 11. Hủy đăng ký và cạnh tranh đồng thời

Với publisher đa luồng, cần phân biệt hai mệnh đề:

1. **Không nhận callback mới sau khi hủy.**
2. **Không còn callback nào đang chạy khi lời gọi hủy trả về.**

Một API có thể bảo đảm (1) cho các sự kiện bắt đầu sau thời điểm hủy, nhưng **không tự động bảo đảm (2)**. Callback đã được xếp hàng hoặc bắt đầu chạy trước khi gọi `unsubscribe()` có thể tiếp tục sau khi hủy, tùy implementation.

Ví dụ trình tự:

```text
Thread A: publisher đã chọn listener để gọi
Thread B: owner gọi unsubscribe()
Thread B: unsubscribe() trả về
Thread A: callback có thể vẫn chạy, nếu API không chờ callback đang chạy
```

Để tránh dùng trạng thái đã đóng:

- Callback có thể kiểm tra trạng thái lifecycle trước khi truy cập dữ liệu nhạy cảm.
- Publisher phải có cơ chế đồng bộ, phiên bản hoặc generation token khi cần loại bỏ sự kiện cũ.
- Nếu API hứa rằng hủy xong thì mọi callback đã dừng, cần cơ chế chờ/drain phù hợp; không thể chỉ xóa một phần tử khỏi list.
- Khi cleanup callback cũng cần dừng thread, executor, timer hoặc network listener, phải phân biệt **cancel request** với **await completion** theo hợp đồng của từng API.
- Không đặt lock giữ lâu rồi gọi callback tùy ý nếu có nguy cơ deadlock/reentrant call; thiết kế cụ thể phụ thuộc concurrency model.

Tài liệu `close()`/`unsubscribe()` nên nói rõ bảo đảm về callback in-flight và thread safety. Ví dụ `EventHub` ở mục trước chỉ chứng minh hành vi trong một luồng, không mang những bảo đảm đồng thời này.

## 12. Cancellation, interruption và quyền sở hữu

Không phải đăng ký nào cũng dùng `close()`. Một số API trả `Future`, `Subscription` hoặc token có phương thức `cancel()`. Owner của tác vụ/subscription cần quyết định khi nào hủy, nhưng semantics không giống hệt đóng stream.

- `Future.cancel(boolean mayInterruptIfRunning)` là yêu cầu hủy. Giá trị trả về cho biết yêu cầu hủy có thành công theo trạng thái của `Future`; nó không chứng minh tác vụ đã dừng ngay lập tức.
- Với API Reactive Streams, `Subscription.cancel()` yêu cầu ngừng truyền tín hiệu tiếp theo theo contract của giao thức; vẫn phải hiểu các race và tín hiệu in-flight.
- Khi một thread bị ngắt qua `InterruptedException`, không vô tình làm mất cờ interrupt nếu bắt exception rồi chuyển nghĩa. Thông thường cần `Thread.currentThread().interrupt()` trước khi truyền lỗi ở dạng khác, trừ khi phương thức đã truyền tiếp chính `InterruptedException` hoặc có chính sách khác rõ ràng.
- Hủy subscription không mặc nhiên đóng producer hoặc tài nguyên chia sẻ với subscriber khác. Owner phải chỉ hủy thứ mình sở hữu.

Ví dụ **mảnh minh họa**, giả định có kiểu `TaskCancelledException` riêng:

```java
try {
    waitForWork();
} catch (InterruptedException cause) {
    Thread.currentThread().interrupt();
    throw new TaskCancelledException("Tác vụ bị ngắt", cause);
} finally {
    registration.close();
}
```

Ở đây `waitForWork`, `TaskCancelledException` và `registration` là các ký hiệu minh họa chưa khai báo đầy đủ. Nếu `registration.close()` có thể ném, cần áp dụng chính sách giữ exception gốc ở mục 8–9. Việc khôi phục interrupt giúp logic phía trên còn nhận biết yêu cầu ngắt khi exception không tiếp tục được ném với kiểu `InterruptedException`.

## 13. Đóng tài nguyên không đồng nghĩa với rollback

Cleanup trả lại tài nguyên và hủy liên kết, nhưng **không tự đảo ngược mọi thay đổi nghiệp vụ**.

Ví dụ:

- Đóng `FileOutputStream` không xóa dữ liệu đã ghi.
- Đóng JDBC connection không phải lúc nào cũng tương đương rollback theo hợp đồng ứng dụng; quản lý transaction phải có quy tắc commit/rollback riêng.
- Hủy listener không rút lại các sự kiện mà callback đã xử lý.
- Cancel một request HTTP không chứng minh server chưa ghi dữ liệu.
- Đóng client mạng có thể hủy các request khác nếu client là tài nguyên chia sẻ.

Vì vậy, cần tách ba nhiệm vụ:

| Nhiệm vụ | Mục tiêu | Ví dụ |
| --- | --- | --- |
| Cleanup vòng đời | Giải phóng tài nguyên/registration thuộc sở hữu | `close()`, `unsubscribe()`, `removeListener()`. |
| Rollback/compensation | Khôi phục hoặc bù các thay đổi nghiệp vụ | Rollback transaction, xóa bản ghi tạm hoặc thực hiện thao tác bù. |
| Bảo toàn lỗi | Giữ thông tin lỗi chính và lỗi cleanup | `getCause()`, `getSuppressed()`, logging có ngữ cảnh. |

Khi thao tác cần cam kết **strong exception safety** (thất bại không đổi trạng thái quan sát được), phải thiết kế staging, transaction hoặc rollback thực sự; chỉ thêm `finally { close(); }` chưa đủ.

Với thao tác có **basic exception safety**, trạng thái có thể thay đổi một phần nhưng invariant của đối tượng vẫn đúng và không rò rỉ tài nguyên. Cần xác định mức bảo đảm phù hợp thay vì suy đoán.

## 14. Thiết kế vòng đời và hợp đồng public API

Một API quản lý tài nguyên tốt trả lời rõ:

- **Ai tạo?** Ai có quyền sở hữu handle?
- **Ai đóng?** Caller, callee, container/framework hay lifecycle manager?
- **Đóng lúc nào?** Hết phương thức, kết thúc transaction, dừng component, hết ứng dụng?
- **Close lần hai ra sao?** Idempotent, ném lỗi hay không xác định? Không giả định từ `AutoCloseable`.
- **Close có thể ném gì?** Lỗi cleanup có quan trọng với caller? Có thể còn pending write hay không?
- **Sau close được gọi lại phương thức khác không?** Nếu không, exception nào báo trạng thái không hợp lệ?
- **Có callback in-flight không?** Hủy subscription có đợi callback đang chạy kết thúc không?
- **Tài nguyên có được chia sẻ không?** Nếu có, một borrower có thể vô tình làm hỏng người khác khi đóng không?
- **Khởi tạo lỗi giữa chừng thì sao?** Có dọn đủ resource tạo thành công trước đó không?

Javadoc minh họa hợp đồng cho handle registration (chưa có implementation):

```java
/**
 * Quyền sở hữu một đăng ký listener.
 *
 * <p>Được tạo bởi {@code subscribe}. Caller phải đóng khi không
 * còn cần nhận sự kiện. Đóng nhiều lần không tạo tác dụng bổ sung.
 * Sau khi close trả về, không có callback mới được bắt đầu;
 * callback đã chạy trước đó có thể tiếp tục hoàn thành.
 */
public interface Registration extends AutoCloseable {
    /**
     * Hủy đăng ký do handle này đại diện.
     */
    @Override
    void close();
}
```

Đoạn Javadoc này chỉ phù hợp với **một implementation thực sự bảo đảm đúng các điều đã mô tả**. Không sao chép vào mọi event bus; đặc biệt, lời hứa “không có callback mới” cần đồng bộ thích hợp khi có concurrency.

Nếu phương thức trả handle theo cách chuyển quyền sở hữu, có thể ghi:

```java
/**
 * Đăng ký listener và chuyển quyền sở hữu registration cho caller.
 *
 * @param listener callback nhận sự kiện, không được null
 * @return handle phải được caller đóng khi không còn cần sự kiện
 * @throws NullPointerException nếu {@code listener == null}
 */
Registration subscribe(Listener listener);
```

Đây là đoạn chữ ký minh họa, `Listener` chưa được khai báo. API thực tế có thể dùng callback type khác.

## 15. Các lỗi thiết kế thường gặp

**1. Dùng `try-with-resources` với một tài nguyên được mượn.** Cú pháp đúng nhưng ý nghĩa sai: nó gọi `close()` lên resource của caller. Xác định owner trước khi viết `try`.

**2. Đóng quá sớm.** Subscribe trong một `try` kết thúc ngay sau khi tạo UI; khi UI thật sự hoạt động, listener đã bị hủy. Phạm vi cleanup phải cùng vòng đời với nhu cầu sử dụng, không chỉ phạm vi lexical ngắn nhất.

**3. Không đóng khi constructor hoặc startup thất bại.** Mới tạo được một số tài nguyên nhưng không trả object về caller; phần triển khai phải tự rollback phần acquisition đã thành công.

**4. Không hủy đăng ký callback.** Publisher giữ listener mãi, dẫn đến callback sau khi component dừng, giữ tham chiếu và đôi khi xử lý sự kiện nhiều lần.

**5. Dùng lambda mới để remove listener cũ.** Lưu đúng instance hoặc giữ handle registration do API trả về.

**6. Swallow mọi lỗi close.** Đặc biệt với stream có flush, close thất bại có thể là dấu hiệu dữ liệu chưa được ghi đầy đủ. Phân biệt cleanup không thể thất bại với cleanup có hậu quả.

**7. Giả định mọi close là idempotent.** `AutoCloseable` không bảo đảm điều này cho mọi implementation. Thiết kế tự định nghĩa hoặc tuân theo contract cụ thể của thư viện.

**8. Chỉ cleanup thành công, bỏ nhánh exception.** Sử dụng try-with-resources hoặc finally phù hợp; kiểm tra nhánh thất bại trước/sau acquisition.

**9. Giả định unsubscribe dừng ngay callback đa luồng.** Tùy API, callback đã chạy hoặc được xếp hàng có thể còn tiếp tục. Cần lifecycle synchronization nếu muốn cam kết mạnh hơn.

**10. Nhầm cleanup với rollback.** Đóng resource không phục hồi dữ liệu đã thay đổi. Nếu API hứa trạng thái không đổi khi thất bại, cần transaction/compensation riêng.

## 16. Kiểm thử exception safety của cleanup

Nên thử các tình huống gần ranh giới vòng đời:

| Tình huống | Điều cần khẳng định |
| --- | --- |
| Thao tác thành công | Resource owner đóng đúng một lần theo contract, hoặc đúng số lần logic yêu cầu. |
| Thân `try` ném exception | Resource vẫn được đóng; exception chính không mất. |
| Lỗi khi tạo resource thứ hai | Resource thứ nhất đã mở được đóng, không còn dangling registration. |
| Cả xử lý và `close()` đều ném | Primary exception còn nguyên; lỗi close nằm trong `getSuppressed()`. |
| Hàm chỉ mượn resource | Resource của caller vẫn mở sau lời gọi, caller là bên đóng. |
| Subscribe rồi cleanup | Listener không nhận sự kiện mới theo bảo đảm của API; handle không bị bỏ quên. |
| Close handle hai lần | Kết quả đúng chính sách idempotency đã tài liệu hóa. |
| Unsubscribe đồng thời với publish | Không có data race/deadlock; kết quả theo contract về callback in-flight. |
| Thao tác ghi thất bại giữa chừng | Xác nhận side effect/rollback theo bảo đảm API, tách khỏi việc giải phóng tài nguyên. |

Với ví dụ `OwnershipCleanupDemo`, kiểm tra danh sách `EVENTS` cho biết thứ tự đóng, và `subscribers: 0` cho thấy registration được gỡ. Với ứng dụng thực, cần test ở cấp adapter hoặc integration để bắt rò rỉ file handle, connection, listener và task còn chạy.

Hãy ưu tiên **kiểm tra hành vi** hơn là assert chi tiết implementation. Ví dụ, nếu hợp đồng chỉ hứa tài nguyên cuối cùng được đóng, không cần buộc implementation phải có đúng một lần gọi nội bộ `close` nếu cơ chế idempotent được cho phép.

## 17. Bài tập và câu hỏi tự kiểm tra

**Tình huống A:** Hàm `parse(InputStream input)` nhận stream từ caller. Nó có nên viết `try (input)` không?

**Đáp án:** Chỉ khi hợp đồng trao quyền sở hữu cho `parse`. Nếu API chỉ mượn stream, hàm không được tự đóng. Caller nên bao việc tạo và gọi trong `try-with-resources`.

**Tình huống B:** Component đăng ký ba listener, nhưng đăng ký thứ ba ném exception. Ai hủy hai listener đầu?

**Đáp án:** Thành phần đang sở hữu hai registration đầu trong giai đoạn khởi tạo phải hủy chúng trước khi báo thất bại. Nếu factory chưa trả component về caller, caller không thể thực hiện cleanup nội bộ này.

**Tình huống C:** Trong try-with-resources, phần xử lý ném `IOException("write")` và `close()` ném `IOException("close")`. Caller thường nhận gì?

**Đáp án:** Nhận exception chính `"write"`; exception `"close"` nằm trong `getSuppressed()`. Với một `finally` tự viết gọi `close()` trực tiếp, lỗi close có thể che mất lỗi xử lý.

**Tình huống D:** `unsubscribe()` trả về nhưng callback đã bắt đầu ở thread khác vẫn đang chạy. Có chắc API sai không?

**Đáp án:** Chưa chắc. Phải xem contract có bảo đảm chờ callback in-flight hay chỉ ngăn đăng ký nhận sự kiện mới. Nếu cần chặn cả callback đang chạy, phải thiết kế cơ chế đồng bộ hoặc đợi hoàn thành tương ứng.

**Tình huống E:** Một đối tượng shared HTTP client được dùng bởi mười request. Mỗi request có nên gọi `client.close()` không?

**Đáp án:** Không, nếu client thuộc sở hữu của application/lifecycle manager và request chỉ mượn. Mỗi request chỉ cleanup tài nguyên mình sở hữu (ví dụ body/response riêng theo contract thư viện). Owner của shared client đóng nó ở cuối vòng đời ứng dụng.

## 18. Nguyên tắc áp dụng

Quy tắc dễ kiểm tra nhất là vẽ một cặp **acquire → release** cho mỗi tài nguyên, subscription hoặc tác vụ: ai nhận trách nhiệm, thời điểm giải phóng, đường lỗi nào đi qua cleanup, và chuyện gì xảy ra nếu cleanup cũng thất bại. Đưa tài nguyên sở hữu trong phạm vi ngắn vào try-with-resources; dùng handle/lifecycle rõ ràng cho đăng ký sống lâu; giữ borrowed resource nguyên vẹn; xét cả khởi tạo một phần và cạnh tranh đồng thời. Khi làm được vậy, exception có thể làm thao tác thất bại mà vẫn không biến failure thành rò rỉ tài nguyên hoặc trạng thái vòng đời sai.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-safety-007">Lock.lock kết hợp unlock trong finally</a>

<details>
<summary>Xem chi tiết</summary>

# Lock.lock kết hợp unlock trong finally — bảo toàn khóa và trạng thái trong Java

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Giải thích vì sao một vùng tới hạn dùng <code>Lock</code> phải có đường giải phóng khóa đáng tin cậy cả khi code ném ngoại lệ.
- Viết đúng mẫu <code>lock.lock()</code> → <code>try</code> → <code>finally</code> → <code>lock.unlock()</code>.
- Phân biệt **giải phóng khóa** với **bảo toàn bất biến dữ liệu** khi thao tác cập nhật có thể thất bại.
- Hiểu quyền sở hữu khóa, khả năng khóa lại (reentrancy), và vì sao chỉ luồng giữ khóa mới được gọi <code>unlock()</code>.
- Sử dụng đúng biến thể <code>tryLock()</code>, <code>tryLock(timeout, unit)</code> và <code>lockInterruptibly()</code> khi có nhu cầu.
- Nhận biết lỗi như quên <code>finally</code>, nhả khóa sai luồng, bắt ngoại lệ rồi bỏ qua và làm hỏng trạng thái trước khi nhả khóa.

Phạm vi bài tập trung vào <code>java.util.concurrent.locks.Lock</code> và triển khai thường gặp <code>java.util.concurrent.locks.ReentrantLock</code> trong Java 17+.

## 2. Vấn đề: ngoại lệ xảy ra khi đang giữ khóa

Một vùng tới hạn (*critical section*) chứa thao tác đọc/sửa dữ liệu chia sẻ cần được bảo vệ khỏi các luồng khác đang sử dụng **cùng khóa**. Với <code>ReentrantLock</code>, một luồng gọi <code>lock()</code> để lấy quyền truy cập và phải gọi <code>unlock()</code> đủ số lần để giải phóng quyền đó.

Cách viết thiếu an toàn:

~~~java
lock.lock();
updateSharedState();
lock.unlock();
~~~

Đoạn trên là **mã minh họa**, giả định <code>lock</code> và <code>updateSharedState()</code> đã tồn tại. Nếu <code>updateSharedState()</code> ném <code>RuntimeException</code>, <code>IOException</code> được chuyển tiếp qua một lời gọi liên quan, hoặc code thoát bằng <code>return</code> trước dòng cuối, lời gọi <code>unlock()</code> có thể không bao giờ chạy.

Hệ quả:

- Khóa vẫn bị giữ bởi luồng đã lấy nó.
- Các luồng khác cần cùng khóa có thể phải chờ vô thời hạn hoặc tới khi luồng giữ khóa giải phóng nó ở nơi khác.
- Một thao tác tiếp theo của **chính luồng đó** đôi khi vẫn chạy được do <code>ReentrantLock</code> là khóa có thể lấy lại nhiều lần; điều này dễ che giấu lỗi rò rỉ khóa.
- Nếu thao tác cập nhật dở dang, trạng thái chia sẻ cũng có thể không nhất quán dù sau đó khóa được nhả.

Phương pháp chuẩn của Java:

~~~java
lock.lock();
try {
    updateSharedState();
} finally {
    lock.unlock();
}
~~~

Khi <code>lock.lock()</code> trả về thành công, luồng đang giữ khóa trước khi vào khối <code>try</code>. Nếu code trong <code>try</code> hoàn thành bình thường, <code>return</code>, hoặc ném ngoại lệ, Java sẽ thực hiện <code>finally</code> khi thoát khối theo ngữ nghĩa thông thường.

**Điểm cốt lõi:** <code>finally</code> bảo vệ **lệnh giải phóng khóa**. Nó không tự hoàn tác những thay đổi dữ liệu đã được thực hiện trong <code>try</code>.

## 3. Cấu trúc chuẩn và thứ tự thực hiện

~~~text
Luồng gọi lock.lock()
          |
          v
  Đợi đến khi giữ được khóa
          |
          v
       vào try
          |
          +---- hoàn tất bình thường ----+
          |                              |
          +---- return/throw -----------+----> finally: lock.unlock()
                                                  |
                                                  v
                                       tiếp tục/return/ném ngoại lệ
~~~

### Vì sao đặt lock.lock() trước try?

Mẫu khuyến nghị:

~~~java
lock.lock();
try {
    // Các thao tác cần giữ khóa.
} finally {
    lock.unlock();
}
~~~

Nhờ vậy <code>finally</code> gắn với phần thực thi **sau khi đã lấy khóa**. Nếu một cách lấy khóa có thể thất bại hoặc ném ngoại lệ **trước khi sở hữu khóa**, code sẽ không đi vào khối <code>try</code>, nên không nhả một khóa mà mình chưa giữ.

Với <code>ReentrantLock.lock()</code>, thao tác bình thường sẽ đợi cho tới khi lấy được khóa; nó không khai báo <code>InterruptedException</code>. Việc ngắt luồng khi đang đợi không có cùng cách xử lý với <code>lockInterruptibly()</code>; không được giả định rằng mọi phương thức lấy khóa đều có cùng hợp đồng.

Trong phương thức <code>tryLock()</code> trả về <code>false</code>, còn rõ hơn: **chưa lấy được khóa thì không được gọi <code>unlock()</code>**.

### Vì sao finally thay vì catch?

Một khối <code>catch</code> chỉ chạy với những loại ngoại lệ nó bắt được. Nếu ta viết:

~~~java
lock.lock();
try {
    updateSharedState();
} catch (IllegalArgumentException e) {
    lock.unlock();
    throw e;
}
lock.unlock();
~~~

thì những loại ngoại lệ khác và một số đường thoát sớm vẫn có thể làm mất lời gọi nhả khóa. Đoạn này là **mã minh họa không độc lập** và cố ý thể hiện cấu trúc dễ sai.

<code>finally</code> giúp tập trung thao tác giải phóng vào đúng một vị trí. Nếu cần <code>catch</code> để bổ sung ngữ cảnh hoặc chuyển ngoại lệ, có thể đặt nó **bên trong cùng khối <code>try</code> hoặc bao quanh vùng khóa**, nhưng vẫn giữ <code>finally</code> làm nơi chịu trách nhiệm nhả khóa.

Trong các sự cố đặc biệt như JVM bị dừng đột ngột hoặc tiến trình bị kết thúc, Java không bảo đảm mọi <code>finally</code> đều được thực thi. Cơ chế này đáp ứng việc quản lý khóa ở đường thực thi Java thông thường, không phải bảo đảm phục hồi hệ thống sau mọi sự cố vật lý.

## 4. Ví dụ Java 17 độc lập: ngoại lệ không làm rò rỉ ReentrantLock

Ví dụ dưới đây kết hợp ba tình huống:

1. Cập nhật số dư hợp lệ.
2. Thao tác bị từ chối bằng ngoại lệ ngay trong vùng khóa; một luồng khác vẫn lấy lại được khóa sau đó.
3. Bốn luồng cùng tăng một bộ đếm; việc nhả khóa nhất quán giúp mọi lần tăng được thực hiện an toàn.

Tạo tệp **LockFinallyDemo.java**:

~~~java
import java.util.concurrent.locks.ReentrantLock;

public class LockFinallyDemo {

    static final class Wallet {
        private final ReentrantLock lock = new ReentrantLock();
        private int balance;

        Wallet(int initialBalance) {
            if (initialBalance < 0) {
                throw new IllegalArgumentException("Số dư ban đầu âm");
            }
            balance = initialBalance;
        }

        void deposit(int amount) {
            lock.lock();
            try {
                if (amount <= 0) {
                    throw new IllegalArgumentException("Số tiền phải dương");
                }
                balance = Math.addExact(balance, amount);
            } finally {
                lock.unlock();
            }
        }

        void withdraw(int amount) {
            lock.lock();
            try {
                if (amount <= 0) {
                    throw new IllegalArgumentException("Số tiền phải dương");
                }
                if (amount > balance) {
                    throw new IllegalStateException("Không đủ số dư");
                }
                balance -= amount;
            } finally {
                lock.unlock();
            }
        }

        int balance() {
            lock.lock();
            try {
                return balance;
            } finally {
                lock.unlock();
            }
        }

        // Chỉ phục vụ minh họa/kiểm thử quyền sở hữu khóa.
        boolean probeLock() {
            if (!lock.tryLock()) {
                return false;
            }
            try {
                return true;
            } finally {
                lock.unlock();
            }
        }
    }

    static final class Counter {
        private final ReentrantLock lock = new ReentrantLock();
        private int value;

        void increment() {
            lock.lock();
            try {
                value++;
            } finally {
                lock.unlock();
            }
        }

        int get() {
            lock.lock();
            try {
                return value;
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Wallet wallet = new Wallet(100);
        wallet.deposit(50);

        try {
            wallet.withdraw(200);
        } catch (IllegalStateException e) {
            System.out.println("Giao dịch bị từ chối: " + e.getMessage());
        }

        boolean[] probeResult = {false};
        Thread probe = new Thread(
                () -> probeResult[0] = wallet.probeLock());
        probe.start();
        probe.join();

        System.out.println("Luồng khác lấy được khóa: " + probeResult[0]);

        wallet.withdraw(20);
        System.out.println("Số dư cuối: " + wallet.balance());

        Counter counter = new Counter();
        Thread[] workers = new Thread[4];

        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(() -> {
                for (int n = 0; n < 5_000; n++) {
                    counter.increment();
                }
            });
        }

        for (Thread worker : workers) {
            worker.start();
        }
        for (Thread worker : workers) {
            worker.join();
        }

        System.out.println("Tổng số lần tăng: " + counter.get());
    }
}
~~~

Biên dịch và chạy bằng Java 17+:

~~~bash
javac -encoding UTF-8 LockFinallyDemo.java
java LockFinallyDemo
~~~

Stdout:

~~~text
Giao dịch bị từ chối: Không đủ số dư
Luồng khác lấy được khóa: true
Số dư cuối: 130
Tổng số lần tăng: 20000
~~~

### Phân tích kết quả

- Ban đầu ví có 100 đơn vị; nạp thêm 50 thành 150.
- <code>withdraw(200)</code> ném <code>IllegalStateException</code> **trước khi trừ số dư**. Luồng thoát khối <code>try</code>, chạy <code>finally</code> rồi ngoại lệ đi tới <code>catch</code> ở <code>main</code>.
- Phép thử khóa chạy ở **luồng khác**, vì <code>ReentrantLock</code> cho phép **chính luồng đang sở hữu khóa** gọi <code>tryLock()</code> thành công dù nó chưa nhả hết số lần giữ khóa. Kiểm tra lại bằng cùng luồng sẽ không chứng minh đã nhả khóa.
- Chương trình gọi <code>probe.join()</code> trước khi đọc mảng kết quả, nên công việc của luồng kiểm tra đã kết thúc và thay đổi của nó được nhìn thấy sau <code>join()</code>.
- Rút tiếp 20 cho số dư 130. Trong ví dụ này, thao tác sai không làm thay đổi dữ liệu; <code>finally</code> bảo đảm không giữ khóa khi ném lỗi.
- Bốn luồng tăng <code>Counter</code> mỗi luồng 5.000 lần, tạo đúng 20.000 lượt tăng. Các luồng được <code>join()</code> trước khi lấy tổng, nên kết quả không phụ thuộc thứ tự lập lịch.

Trong triển khai thực tế, một phương thức <code>probeLock()</code> chỉ nhằm quan sát khóa thường không nên là một phần của API nghiệp vụ; nó hiện diện ở đây để thể hiện một phép kiểm tra cụ thể.

## 5. finally bảo toàn khóa, nhưng chưa đủ để bảo toàn trạng thái

Giả sử một giỏ hàng lưu hai trường:

- <code>stock</code>: số lượng hàng còn có thể bán.
- <code>reserved</code>: số lượng đã được giữ chỗ.

Một thao tác giữ chỗ phải vừa giảm <code>stock</code> vừa tăng <code>reserved</code>. Nếu code thay đổi trường đầu tiên rồi ném ngoại lệ trước khi thay đổi trường thứ hai, trạng thái có thể trở nên không nhất quán **dù khóa được nhả trong finally**.

Ví dụ thiết kế nguy hiểm:

~~~java
lock.lock();
try {
    stock -= amount;
    callPotentiallyFailingOperation();
    reserved += amount;
} finally {
    lock.unlock();
}
~~~

Đây là **mã minh họa**, giả định các biến và phương thức tương ứng đã tồn tại. Nếu <code>callPotentiallyFailingOperation()</code> ném lỗi, <code>stock</code> đã bị giảm nhưng <code>reserved</code> chưa tăng. <code>finally</code> chỉ nhả khóa; nó không thể biết cần phục hồi dữ liệu như thế nào.

**Nguyên tắc thiết kế:** bảo vệ hai mục tiêu riêng biệt:

1. **An toàn khóa:** mọi đường thoát sau khi lấy khóa đều nhả khóa đúng cách.
2. **An toàn trạng thái:** mỗi thao tác hoàn tất hoặc thất bại theo quy tắc đã định, không công bố trạng thái vi phạm bất biến.

### Cách ưu tiên: kiểm tra trước, thay đổi sau

Nếu có thể, hãy thực hiện toàn bộ kiểm tra có thể thất bại **trước các phép biến đổi trạng thái** rồi cập nhật các trường bằng thao tác xác định, nhỏ và không gọi code bên ngoài ở giữa.

Ví dụ dưới đây là **đoạn đặt trong một lớp có các trường <code>lock</code>, <code>stock</code>, <code>reserved</code> kiểu phù hợp**:

~~~java
void reserve(int amount) {
    lock.lock();
    try {
        if (amount <= 0) {
            throw new IllegalArgumentException("Số lượng phải dương");
        }
        if (amount > stock) {
            throw new IllegalStateException("Không đủ hàng");
        }
        int newReserved = Math.addExact(reserved, amount);

        stock -= amount;
        reserved = newReserved;
    } finally {
        lock.unlock();
    }
}
~~~

Các phép kiểm tra yêu cầu dương, đủ hàng và tràn số nguyên đều hoàn thành trước khi thay đổi dữ liệu. Sau đó chỉ còn hai phép gán trên biến nguyên thủy, không gọi code tùy ý ở giữa. Nếu dự án có bất biến khác, những bất biến đó cũng phải được kiểm tra.

Nếu cập nhật nhiều đối tượng hoặc thực hiện thao tác I/O, cách “kiểm tra trước” có thể chưa đủ: code bên ngoài vẫn có thể thất bại giữa chuỗi bước. Khi ấy có thể cần chuẩn bị trạng thái mới trong bộ nhớ trước khi công bố, phương án rollback đáng tin cậy, giao dịch cơ sở dữ liệu, hoặc một giao thức nhất quán được thiết kế riêng. Khóa trong bộ nhớ **không tự tạo ra database transaction** và không hoàn tác tác dụng phụ bên ngoài.

### Tình huống Math.addExact trong demo

Dòng:

~~~java
balance = Math.addExact(balance, amount);
~~~

có tính chất hữu ích: nếu tổng vượt phạm vi <code>int</code>, <code>Math.addExact</code> ném <code>ArithmeticException</code> trước khi phép gán cập nhật <code>balance</code>. <code>finally</code> vẫn nhả khóa, còn số dư giữ nguyên. Điều này tốt hơn phép cộng <code>int</code> không kiểm tra tràn trong một miền giá trị yêu cầu số dư hợp lệ.

## 6. Bốn nguyên tắc khi thao tác với ReentrantLock

### 6.1. Mỗi lần lấy khóa thành công phải có lần nhả tương ứng

<code>ReentrantLock</code> là khóa **reentrant**: cùng một luồng có thể gọi <code>lock()</code> nhiều lần mà không tự khóa chết. Mỗi lần lấy khóa thành công làm tăng số lần giữ khóa (*hold count*). Mỗi lần <code>unlock()</code> tương ứng làm giảm số lần đó; quyền sở hữu thực sự được giải phóng khi số lần giữ trở về 0.

Ví dụ đoạn minh họa trong một phương thức:

~~~java
lock.lock();       // Hold count: 1
try {
    lock.lock();   // Hold count: 2
    try {
        doWork();
    } finally {
        lock.unlock(); // Hold count: 1
    }
} finally {
    lock.unlock();     // Hold count: 0
}
~~~

Giả định <code>doWork()</code> và <code>lock</code> tồn tại. Nếu quên một <code>unlock()</code>, luồng vẫn giữ khóa, dù đã chạy một khối <code>finally</code>. Trong code thực tế, việc lồng lấy cùng khóa không cần thiết nên được hạn chế để dễ review.

### 6.2. Chỉ luồng sở hữu khóa mới được unlock

Với <code>ReentrantLock</code>, gọi <code>unlock()</code> từ luồng không sở hữu khóa sẽ ném <code>IllegalMonitorStateException</code>. Một luồng khác không thể “dọn dẹp giúp” khóa mà mình không nắm giữ. Do đó, hãy đặt <code>unlock()</code> trong <code>finally</code> của **chính luồng và chính luồng điều khiển đã lấy khóa**.

Không chia tách “thread A lấy khóa; thread B nhả khóa” khi dùng <code>ReentrantLock</code>. Nếu cần điều phối công việc giữa luồng, hãy thiết kế cơ chế đồng bộ phù hợp với yêu cầu đó, thay vì vi phạm quyền sở hữu khóa.

### 6.3. Mọi truy cập trạng thái chia sẻ cần tuân thủ cùng giao thức khóa

Khóa chỉ có tác dụng đồng bộ hóa những thao tác tham gia cùng giao thức. Ví dụ, <code>Counter.increment()</code> có khóa nhưng một chỗ khác lại đọc thẳng trường <code>value</code> không được bảo vệ thì người đọc không được hưởng cùng bảo đảm quan sát nhất quán.

Với <code>Lock</code> cùng đối tượng khóa, việc <code>unlock()</code> và lần <code>lock()</code> thành công sau đó có quan hệ đồng bộ hóa giúp bảo đảm nhìn thấy các thay đổi bộ nhớ được công bố dưới khóa. Muốn đọc/ghi nhất quán, phải giữ quy ước đó ở **mọi lối truy cập** tới dữ liệu cần bảo vệ.

### 6.4. Không giữ khóa lâu hơn cần thiết

Nếu vùng khóa thực hiện network I/O, ghi tệp chậm, chờ người dùng hoặc gọi callback không kiểm soát được, các luồng khác sẽ phải đợi lâu. Vì vậy, nên rút gọn vùng khóa quanh thao tác cần bảo vệ, nhưng chỉ chuyển công việc ra ngoài sau khi xem xét bất biến và thứ tự cần thiết.

Không được tùy tiện chuyển phép kiểm tra và cập nhật sang hai vùng khóa khác nhau:

~~~java
lock.lock();
try {
    if (balance < amount) {
        throw new IllegalStateException("Không đủ số dư");
    }
} finally {
    lock.unlock();
}

// Luồng khác có thể thay đổi balance ở khoảng trống này.

lock.lock();
try {
    balance -= amount;
} finally {
    lock.unlock();
}
~~~

Đây là **mã minh họa không độc lập**. Nó tạo lỗi *check-then-act*: hai đoạn riêng lẻ có khóa nhưng toàn bộ thao tác không còn nguyên tử. Kiểm tra điều kiện và cập nhật phụ thuộc điều kiện thường phải thuộc cùng một lần giữ khóa.

## 7. Khi không chắc có lấy được khóa: tryLock

Phương thức <code>tryLock()</code> trên <code>Lock</code> trả về <code>true</code> nếu lời gọi lấy được khóa ngay tại thời điểm đó, hoặc <code>false</code> nếu không lấy được theo hợp đồng của cách triển khai. Với <code>ReentrantLock</code>, cùng luồng đang giữ khóa vẫn có thể gọi <code>tryLock()</code> thành công nhờ reentrancy.

Mẫu an toàn:

~~~java
boolean acquired = lock.tryLock();
if (!acquired) {
    return false;
}
try {
    updateSharedState();
    return true;
} finally {
    lock.unlock();
}
~~~

Đoạn này là **mã minh họa nằm trong một phương thức trả <code>boolean</code>**. Nhánh <code>false</code> thoát trước khi vào <code>try</code>, vì nó chưa sở hữu khóa. Nếu thành công, <code>finally</code> sẽ nhả khóa ngay cả khi <code>updateSharedState()</code> ném ngoại lệ hoặc code <code>return</code>.

Đừng viết:

~~~java
try {
    if (lock.tryLock()) {
        updateSharedState();
    }
} finally {
    lock.unlock();
}
~~~

Đây là **mã cố ý sai**: nếu <code>tryLock()</code> trả <code>false</code>, <code>finally</code> vẫn gọi <code>unlock()</code> khi luồng chưa sở hữu khóa và <code>ReentrantLock</code> có thể ném <code>IllegalMonitorStateException</code>.

Trong mã thực tế, biến tên <code>acquired</code> giúp review chính xác điều kiện nhả khóa, nhất là khi có nhiều đường thoát.

## 8. Chờ khóa có thể hủy: lockInterruptibly và tryLock có thời hạn

### 8.1. lockInterruptibly()

<code>lockInterruptibly()</code> cho phép luồng đang chờ khóa đáp ứng tín hiệu ngắt theo hợp đồng <code>Lock</code> và khai báo <code>throws InterruptedException</code>. Cấu trúc chuẩn khi phương thức cho phép chuyển tiếp ngoại lệ:

~~~java
void update() throws InterruptedException {
    lock.lockInterruptibly();
    try {
        updateSharedState();
    } finally {
        lock.unlock();
    }
}
~~~

Đoạn minh họa giả định <code>lock</code> và <code>updateSharedState()</code> đã tồn tại. Nếu luồng bị ngắt **trước khi lấy khóa**, <code>lockInterruptibly()</code> có thể ném <code>InterruptedException</code> và code không đi vào <code>try</code>: không gọi <code>unlock()</code>. Nếu đã lấy khóa và exception phát sinh **bên trong <code>try</code>**, <code>finally</code> sẽ nhả khóa theo đường thực thi thông thường.

Nếu không thể khai báo <code>throws InterruptedException</code> và buộc phải đổi sang unchecked exception, code xử lý thường cần khôi phục cờ ngắt bằng <code>Thread.currentThread().interrupt()</code>, đồng thời chuyển tiếp ngoại lệ gốc thành cause. Tuy nhiên cách xử lý cụ thể còn phụ thuộc hợp đồng của tác vụ.

### 8.2. tryLock(timeout, unit)

Một phương thức chờ tối đa trong một khoảng thời gian có thể viết:

~~~java
boolean acquired = lock.tryLock(200, java.util.concurrent.TimeUnit.MILLISECONDS);
if (!acquired) {
    return false;
}
try {
    updateSharedState();
    return true;
} finally {
    lock.unlock();
}
~~~

Đoạn này là **mã minh họa thuộc phương thức trả <code>boolean</code> và khai báo hoặc xử lý <code>InterruptedException</code>**. Không được quên:

- Trả <code>false</code> vì hết thời gian hoặc không lấy được khóa có nghĩa là **không nhả khóa**.
- Phương thức có thể ném <code>InterruptedException</code> khi đang chờ; khi đó chưa có quyền sở hữu khóa từ lời gọi này.
- Chỉ sau khi <code>acquired == true</code> mới đi vào khối có <code>finally</code> tương ứng.

Nếu <code>tryLock</code> từ một luồng đã giữ <code>ReentrantLock</code> trước đó, số lần giữ khóa có thể tăng; lời gọi <code>unlock()</code> trong phương thức này chỉ nhả phần mà lời gọi hiện tại đã lấy, không giải phóng mọi lần giữ từ các ngữ cảnh bên ngoài.

## 9. return, break và exception trong try

Java chạy <code>finally</code> trước khi hoàn tất một đường thoát khỏi khối <code>try</code> do <code>return</code>, <code>break</code>, <code>continue</code> hoặc ngoại lệ theo quy tắc bình thường. Bởi vậy có thể trả kết quả ngay trong vùng khóa:

~~~java
int readBalance() {
    lock.lock();
    try {
        return balance;
    } finally {
        lock.unlock();
    }
}
~~~

Mã minh họa trong lớp có <code>lock</code> và <code>balance</code>. Biểu thức <code>return balance</code> được đánh giá trước khi phương thức thực sự thoát; khối <code>finally</code> nhả khóa trước khi người gọi nhận kết quả.

Tuy nhiên, **không viết <code>return</code> hoặc ném ngoại lệ mới vô cớ bên trong <code>finally</code>**. Một lời gọi <code>return</code> trong <code>finally</code> có thể thay thế giá trị trả về hoặc che mất ngoại lệ của <code>try</code>.

Ví dụ sai:

~~~java
lock.lock();
try {
    return readData();
} finally {
    lock.unlock();
    return null;
}
~~~

Đây là mã minh họa trong một phương thức trả về kiểu tham chiếu. <code>return null</code> của <code>finally</code> có thể che khuất kết quả hoặc ngoại lệ của <code>readData()</code>. <code>finally</code> nên dành cho thao tác giải phóng bắt buộc, không thay đổi cách phương thức báo cáo kết quả.

### Ngoại lệ từ unlock() thì sao?

<code>ReentrantLock.unlock()</code> có thể ném <code>IllegalMonitorStateException</code> nếu gọi khi luồng không sở hữu khóa. Nếu <code>finally</code> ném một ngoại lệ mới, ngoại lệ đó có thể **che mất** ngoại lệ đang được ném từ <code>try</code>. Với mẫu đúng, mỗi lần lấy khóa thành công chỉ được nhả một lần bởi chính luồng sở hữu, nên lỗi này không phải đường xử lý thường lệ.

Đừng thêm <code>catch (Exception ignored)</code> quanh <code>unlock()</code> chỉ để che một lỗi quản lý quyền sở hữu. Cần sửa nguyên nhân nhả khóa sai, vì nó phản ánh sự cố logic đồng bộ hóa.

## 10. Ngoại lệ trong vùng khóa: nên xử lý ở đâu?

Mẫu dưới đây vừa giữ giải phóng khóa vừa cho lỗi có ý nghĩa lan truyền:

~~~java
lock.lock();
try {
    validateRequest();
    changeState();
} finally {
    lock.unlock();
}
~~~

Đây là **đoạn mã minh họa** có sẵn <code>lock</code>, <code>validateRequest()</code> và <code>changeState()</code>.

Nếu tầng API cần chuyển một ngoại lệ kỹ thuật thành ngoại lệ phù hợp hơn, có thể bắt và chuyển đổi nhưng vẫn giữ nguyên tắc nhả khóa:

~~~java
lock.lock();
try {
    return dataStore.load();
} catch (java.io.IOException original) {
    throw new DataAccessException("Không thể tải dữ liệu", original);
} finally {
    lock.unlock();
}
~~~

Đây là **mã minh họa** trong phương thức trả về kiểu phù hợp; giả định <code>dataStore.load()</code> khai báo <code>IOException</code> và có constructor <code>DataAccessException(String, Throwable)</code>. <code>finally</code> vẫn chạy trước khi ngoại lệ mới thoát ra. Truyền <code>original</code> làm cause giúp giữ được dấu vết I/O để chẩn đoán.

Về thiết kế, nếu thao tác I/O có thể chạy lâu, cân nhắc liệu thật sự cần giữ khóa xuyên suốt lần gọi đó không. Có thể tách bước chuẩn bị và công bố kết quả để giảm thời gian giữ khóa, với điều kiện vẫn bảo toàn thứ tự và bất biến dữ liệu.

### Có nên nuốt ngoại lệ?

Không nên viết một khối <code>catch</code> rỗng rồi nhả khóa như thể thao tác đã thành công. Nếu đã khôi phục hoàn toàn lỗi theo hợp đồng, ứng dụng có thể tiếp tục; nếu chưa, cần báo lỗi thích hợp. Cũng không nên biến mọi lỗi thành một giá trị thành công giả (<code>0</code>, <code>null</code>, chuỗi rỗng) nếu API không công bố cách biểu đạt lỗi đó.

## 11. Khi cần nhiều khóa: finally vẫn chưa phòng được deadlock

Giả sử giao dịch giữa hai tài khoản cần lấy khóa của cả hai. Nếu luồng A lấy khóa tài khoản X trước rồi chờ Y, trong khi luồng B lấy khóa Y trước rồi chờ X, hệ thống có thể deadlock.

Ngay cả khi cả hai luồng đều viết:

~~~java
first.lock();
try {
    second.lock();
    try {
        transfer();
    } finally {
        second.unlock();
    }
} finally {
    first.unlock();
}
~~~

đoạn minh họa này **vẫn có thể deadlock** nếu các luồng lấy cùng hai khóa theo thứ tự khác nhau. <code>finally</code> chỉ thực thi khi chương trình rời khỏi các khối đang chạy; nó không giúp một luồng thoát khỏi lời gọi <code>second.lock()</code> đang chờ vô thời hạn.

Hướng thiết kế phổ biến:

- Định nghĩa thứ tự lấy khóa cố định và nhất quán cho mọi luồng.
- Giảm số khóa cần giữ đồng thời khi có thể.
- Xem xét <code>tryLock</code> có giới hạn và cách hoàn trả khóa đã lấy nếu không lấy được khóa tiếp theo.
- Không tự cho rằng tăng thời gian chờ hoặc thêm <code>finally</code> là đủ để giải quyết deadlock.

**Không được tự nhả khóa đang do luồng khác sở hữu** để “gỡ deadlock”; quyền sở hữu của <code>ReentrantLock</code> không cho phép cách làm đó.

## 12. Condition.await() và việc tạm nhả khóa

<code>Condition</code> thường được tạo từ <code>ReentrantLock.newCondition()</code>. Các phương thức chờ như <code>await()</code> có cơ chế đặc biệt: khi chờ, luồng **tạm nhả khóa gắn với condition** và khi kết thúc chờ theo hợp đồng, nó **lấy lại khóa trước khi trở về hoặc báo <code>InterruptedException</code>**.

Một mẫu điển hình:

~~~java
lock.lock();
try {
    while (!ready) {
        condition.await();
    }
    consumeReadyData();
} finally {
    lock.unlock();
}
~~~

Đây là đoạn **minh họa** trong phương thức khai báo/xử lý <code>InterruptedException</code>, với <code>lock</code>, <code>condition</code>, <code>ready</code> và <code>consumeReadyData()</code> đã được xác định. Vòng <code>while</code> cần thiết vì điều kiện có thể không còn đúng sau khi tỉnh, và <code>await()</code> có thể thức dậy dù không có tín hiệu tương ứng.

Nguyên tắc <code>finally</code> vẫn áp dụng: sau khi <code>lock.lock()</code> thành công, luồng kết thúc đoạn xử lý bằng <code>unlock()</code> đúng cách. Việc <code>await()</code> tạm nhả/lấy lại khóa là một phần hợp đồng của <code>Condition</code>, không có nghĩa người dùng được gọi <code>unlock()</code> tùy ý ngay sau <code>await()</code>.

Không gọi <code>await()</code> hoặc <code>signal()</code> của condition trên một <code>ReentrantLock</code> khi chưa giữ khóa tương ứng; với triển khai này, điều đó dẫn tới <code>IllegalMonitorStateException</code>.

## 13. So sánh synchronized và Lock

Java cũng có cú pháp:

~~~java
synchronized (monitor) {
    updateSharedState();
}
~~~

Mã minh họa trên dùng monitor object đã khai báo. Khi luồng rời khỏi khối <code>synchronized</code>, JVM tự giải phóng monitor theo ngữ nghĩa ngôn ngữ, kể cả khi khối ném ngoại lệ. Với <code>Lock</code>, việc nhả khóa là **trách nhiệm tường minh của lập trình viên**, vì đó là API phương thức.

| Khía cạnh | <code>synchronized</code> | <code>ReentrantLock</code> |
| --- | --- | --- |
| Vào vùng khóa | <code>synchronized (monitor)</code> | <code>lock.lock()</code> |
| Ra khỏi vùng khóa | JVM tự nhả monitor khi thoát khối | Phải gọi <code>unlock()</code>, thường trong <code>finally</code> |
| Thử lấy khóa ngay | Không có API tương đương trực tiếp như <code>tryLock()</code> | Có <code>tryLock()</code> |
| Chờ có thể ngắt khi lấy khóa | Không có thao tác monitor-enter tương đương <code>lockInterruptibly()</code> | Có <code>lockInterruptibly()</code> |
| Condition | <code>wait/notify/notifyAll</code> gắn monitor | <code>Condition</code> do lock tạo |

Không phải cứ dùng <code>ReentrantLock</code> là tốt hơn <code>synchronized</code>. Nếu chỉ cần bảo vệ một vùng ngắn và không cần các tính năng lấy khóa linh hoạt, <code>synchronized</code> có thể đơn giản hơn. Khi đã chọn <code>Lock</code>, quy tắc <code>try/finally</code> là phần bắt buộc của thiết kế an toàn.

## 14. Kiểm thử bảo đảm giải phóng khóa

Kiểm thử nên đánh giá **đường thoát qua ngoại lệ**, không chỉ đường thành công.

| Tình huống kiểm thử | Kỳ vọng |
| --- | --- |
| Vùng tới hạn hoàn thành bình thường | Khóa được nhả |
| Kiểm tra đầu vào trong vùng khóa ném ngoại lệ | Ngoại lệ vẫn được báo và khóa được nhả |
| Code ném <code>ArithmeticException</code> trước phép gán | Dữ liệu chưa bị thay đổi, khóa được nhả |
| Có <code>return</code> bên trong <code>try</code> | Giá trị được trả đúng và khóa được nhả |
| <code>tryLock()</code> trả <code>false</code> | Không được gọi <code>unlock()</code> |
| <code>lockInterruptibly()</code> bị ngắt trước khi sở hữu khóa | Ngoại lệ ngắt được truyền/xử lý, không nhả khóa chưa sở hữu |
| Nhiều luồng cùng thay đổi dữ liệu | Kết quả và bất biến được giữ dưới cùng giao thức khóa |

Để kiểm tra lỗi rò rỉ trên <code>ReentrantLock</code>, nên kiểm tra từ **một luồng khác**. Nếu chỉ gọi lại <code>lock()</code> trên cùng luồng, cơ chế reentrancy có thể khiến kiểm thử tưởng khóa đã được nhả dù số lần giữ khóa còn dương.

Trong các bài kiểm thử có concurrency, tránh để một lỗi khóa khiến test treo vô thời hạn. Có thể sử dụng giới hạn thời gian, <code>tryLock(timeout, unit)</code>, hoặc cơ chế đồng bộ trong test để chỉ tiếp tục khi trạng thái đã được thiết lập. Đừng khẳng định tính đúng đắn dựa trên một lần chạy “không bị kẹt”; kiểm thử nên kiểm tra bất biến, kết quả và đường ngoại lệ có chủ đích.

Đối với lớp có nhiều trường chia sẻ, thêm kiểm thử rằng sau mỗi lỗi dự kiến, trạng thái vẫn hợp lệ. Một phép kiểm tra “khóa đã nhả” không đủ chứng minh dữ liệu còn nhất quán.

## 15. Các lỗi thường gặp khi review

1. **Đặt <code>unlock()</code> sau khối thao tác mà không có <code>finally</code>:** lỗi, <code>return</code> hoặc đường thoát sớm có thể bỏ qua nó.
2. **Nhả khóa dù chưa lấy được:** thường gặp với <code>tryLock()</code> hoặc <code>lockInterruptibly()</code> khi đặt sai ranh giới <code>try</code>.
3. **Gọi <code>unlock()</code> hai lần cho một lần lấy khóa:** có thể dẫn đến <code>IllegalMonitorStateException</code> hoặc làm thay đổi hold count không đúng.
4. **Nhả khóa ở luồng khác:** trái hợp đồng quyền sở hữu của <code>ReentrantLock</code>.
5. **Bọc toàn bộ <code>finally</code> bằng catch rỗng:** che giấu lỗi quản lý khóa và làm khó chẩn đoán.
6. **Cho rằng finally tự rollback:** khóa được nhả nhưng trạng thái có thể đã bị cập nhật một phần.
7. **Kiểm tra rồi cập nhật trong hai vùng khóa khác nhau:** tạo khoảng trống cho luồng khác thay đổi dữ liệu.
8. **Quên khóa khi đọc:** giá trị đọc có thể không đồng bộ với những thao tác ghi được bảo vệ bằng khóa.
9. **Gọi công việc chậm hoặc callback không kiểm soát trong vùng khóa:** kéo dài thời gian chờ và tăng nguy cơ deadlock.
10. **Dùng nhiều khóa với thứ tự khác nhau:** <code>finally</code> không sửa được deadlock do đợi khóa kế tiếp.
11. **Dùng <code>return</code> trong finally:** có thể che mất kết quả hoặc ngoại lệ của vùng tới hạn.
12. **Kiểm tra nhả khóa từ cùng luồng:** reentrancy khiến <code>tryLock()</code> vẫn có thể thành công dù khóa bị giữ lại.
13. **Bắt và nuốt tất cả ngoại lệ:** dễ tiếp tục với trạng thái không hợp lệ, mặc dù đã nhả khóa.
14. **Bỏ qua ngắt khi chờ khóa:** khiến cơ chế hủy thao tác khó hoạt động như mong đợi.

## 16. Checklist áp dụng

Trước khi hoàn tất một phương thức có <code>Lock</code>, hãy tự trả lời:

- Phương thức đã lấy khóa **thành công** trước khi vào <code>try</code> chưa?
- Có đúng một đường <code>finally</code> nhả lần lấy khóa đó trong cùng luồng không?
- Với <code>tryLock()</code> và <code>lockInterruptibly()</code>, các đường không lấy được khóa có tránh <code>unlock()</code> không?
- Khi code ném ngoại lệ, người gọi có nhận được lỗi thích hợp, và khóa đã được nhả chưa?
- Khi lỗi xảy ra giữa cập nhật, dữ liệu có còn thỏa mãn bất biến không? Cần chuẩn bị trước, rollback hay transaction không?
- Mọi luồng đọc/ghi dữ liệu chia sẻ có sử dụng cùng giao thức khóa không?
- Có gọi network, I/O hoặc callback lâu trong vùng khóa mà không cần thiết không?
- Nếu có nhiều khóa, thứ tự lấy khóa giữa các luồng có nhất quán không?
- Kiểm thử đã mô phỏng exception và xác nhận **một luồng khác** vẫn lấy khóa được chưa?

**Kết luận:** Mẫu <code>lock.lock()</code> đặt trước <code>try</code> và <code>lock.unlock()</code> trong <code>finally</code> là cách quản lý <code>Lock</code> căn bản để tránh giữ khóa sau đường lỗi. Để thật sự **bảo toàn trạng thái**, còn cần bảo vệ bất biến, cập nhật dữ liệu theo một quy tắc nhất quán và xử lý ngoại lệ đúng hợp đồng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-safety-008">Phân biệt giải phóng khóa synchronized với bảo đảm bất biến dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt giải phóng khóa `synchronized` với bảo đảm bất biến dữ liệu trong Java

> **Phạm vi:** Java Core → Exception → Bảo toàn trạng thái. Bài học giải thích vì sao một ngoại lệ có thể khiến khối `synchronized` kết thúc và monitor được giải phóng đúng cách, trong khi dữ liệu mà đoạn mã đã sửa vẫn ở trạng thái không hợp lệ. Mục tiêu là phân biệt trách nhiệm của cơ chế khóa với trách nhiệm thiết kế thao tác cập nhật an toàn khi thất bại.

## 1. Hai khái niệm khác nhau

Trong Java, `synchronized` giúp các luồng phối hợp quyền truy cập vào một **monitor** gắn với một đối tượng. Khi luồng vào một khối/phương thức `synchronized`, nó phải giữ monitor tương ứng; khi luồng thoát khỏi vùng đó theo cách thông thường hoặc do ngoại lệ, JVM giải phóng monitor do lần vào vùng đồng bộ đó nắm giữ.

**Bất biến dữ liệu (data invariant)** là điều kiện phải đúng đối với trạng thái mà hệ thống cho phép quan sát. Ví dụ:

- Số dư tài khoản không âm.
- Khi chuyển tiền trong nội bộ hai tài khoản, tổng tiền không đổi nếu không có phí hoặc dòng tiền bên ngoài.
- `size` phải khớp với số phần tử lưu thực tế.
- Một đơn hàng `PAID` phải có thông tin thanh toán tương ứng.
- Các trường `start`, `end` phải thỏa `start <= end` theo hợp đồng của đối tượng.

Monitor đảm bảo **quyền truy cập/đồng bộ hóa**; nó không biết số dư, hóa đơn, tồn kho hoặc invariant miền nghiệp vụ. Khi một phương thức ném ngoại lệ sau khi đã sửa một trường, JVM không ghi nhớ giá trị cũ của trường để tự khôi phục.

> **Mấu chốt:** “Khóa được nhả” là một quy tắc của `synchronized`. “Trạng thái còn hợp lệ sau lỗi” là một cam kết mà **mã ứng dụng phải tự thiết kế và kiểm chứng**.

## 2. Điều gì xảy ra khi `synchronized` gặp ngoại lệ?

Một khối đồng bộ có dạng:

~~~java
// Mã minh họa bên trong một phương thức đã có biến lock.
synchronized (lock) {
    changeState();
}
~~~

Các tình huống có liên quan:

1. Luồng gọi phải lấy monitor của `lock` trước khi chạy phần thân.
2. Nếu `changeState()` hoàn tất, luồng ra khỏi khối và nhả monitor của lần vào đó.
3. Nếu `changeState()` ném ngoại lệ chưa bị xử lý trong khối, ngoại lệ lan ra, khối kết thúc bất thường và monitor vẫn được nhả.
4. Nếu có `catch` **bên trong** khối, việc bắt lỗi không đồng nghĩa đã thoát khối; monitor tiếp tục được giữ cho đến lúc thực sự ra khỏi vùng `synchronized`.
5. Nếu ngoại lệ được bắt ở **bên ngoài** khối, luồng đã rời vùng đồng bộ trước khi chạy handler ngoài.

Tương tự, một phương thức instance `synchronized` dùng monitor của `this`; phương thức `static synchronized` dùng monitor của đối tượng `Class` tương ứng. Chúng có thể là **những monitor khác nhau**, vì vậy chỉ khai báo `synchronized` cho mọi phương thức chưa đủ nếu mỗi phương thức bảo vệ trạng thái bằng một khóa khác.

### Khi nào khóa chưa được nhả?

- Khối còn đang chạy, kể cả đang thực hiện lệnh trong `catch` hoặc `finally` nằm **bên trong** khối.
- Một luồng đang giữ nhiều monitor: rời một khối chỉ nhả monitor tương ứng; các monitor của vùng đồng bộ bao ngoài vẫn có thể được giữ.
- Java cho phép **reentrant synchronization**: cùng luồng có thể vào lại monitor đang giữ; mỗi lần vào cần một lần thoát tương ứng trước khi monitor hoàn toàn được giải phóng cho luồng khác.
- `Object.wait()` có hành vi đặc biệt: khi được gọi đúng trên monitor đang giữ, nó tạm nhả monitor của **đối tượng được wait** trong lúc chờ và phải lấy lại monitor ấy trước khi quay về; nó không tự nhả mọi monitor khác mà luồng giữ.
- `Thread.sleep()` không tự nhả monitor đang giữ. `notify()`/`notifyAll()` cũng không làm luồng gọi nhả monitor ngay lập tức.

Quy tắc tự động nhả khóa của `synchronized` nói về **việc đi vào/ra monitor**, không hứa tiến độ trong các trường hợp deadlock, vòng lặp không kết thúc hoặc JVM bị dừng. Không nên diễn đạt rằng mọi lời gọi `synchronized` đều chắc chắn sẽ “cuối cùng nhả khóa” bất kể chương trình có thoát vùng đồng bộ hay không.

## 3. Vì sao monitor được nhả vẫn để lại dữ liệu lỗi?

Xét thao tác chuyển 30 đơn vị từ tài khoản A sang B, ban đầu:

~~~text
A = 100
B = 50
Tổng = 150
~~~

Một phương thức có thể làm như sau:

~~~java
// Mã minh họa nằm trong lớp có trường a và b.
public synchronized void transfer(long amount) {
    a -= amount;                 // A thành 70
    callRiskyOperation();       // Ném exception
    b += amount;                 // Không được thực hiện
}
~~~

Khi `callRiskyOperation()` ném ngoại lệ, luồng rời phương thức; monitor của `this` được nhả. Nhưng lúc đó A bằng 70 và B vẫn bằng 50, tổng còn 120. Bất biến “tổng tiền không đổi” bị phá hủy mặc dù không còn luồng nào giữ khóa.

Một luồng khác đến sau, lấy **cùng monitor** và đọc trạng thái có thể thấy chính xác cặp giá trị sai này. Khóa giúp nó thấy cập nhật đã công bố đúng quy tắc bộ nhớ; khóa không biến giá trị đó thành hợp lệ.

Có hai cấp độ cần phân biệt:

| Câu hỏi | `synchronized` làm được gì? | Điều vẫn phải thiết kế |
|---|---|---|
| Hai luồng có đồng thời chạy mã được bảo vệ bởi **cùng monitor** không? | Ngăn chặn thực thi đồng thời trong các vùng bảo vệ bằng monitor đó | Mọi lối truy cập liên quan phải dùng cùng quy tắc khóa |
| Sau khi luồng trước thoát, luồng sau có thấy các ghi xảy ra trước lúc nhả monitor không? | Quy tắc *happens-before* của unlock → lock tiếp theo trên cùng monitor hỗ trợ tính nhìn thấy/thứ tự | Dữ liệu được nhìn thấy phải đúng theo invariant |
| Ngoại lệ thoát khỏi vùng đồng bộ có làm monitor bị kẹt mãi không? | JVM nhả monitor của vùng đồng bộ đã thoát | Không gọi nhầm `Lock.lock()` thủ công rồi quên `unlock()` |
| Những phép gán đã xảy ra có tự rollback không? | **Không** | Chuẩn bị trước, commit an toàn, rollback có kiểm soát hoặc transaction |
| Nhiều trường có tự cập nhật kiểu all-or-nothing không? | **Không mặc định** | Thiết kế điểm commit, bảo vệ mọi reader, hoặc dùng một snapshot bất biến |
| Ngoài tiến trình/JVM có thể đã chịu tác dụng phụ chưa? | Monitor Java không kiểm soát I/O hay dịch vụ bên ngoài | Transaction, idempotency, outbox hoặc cơ chế phối hợp |

## 4. Chương trình Java 17 chạy được: khóa được nhả nhưng invariant có thể hỏng

Ví dụ sau tạo hai sổ số dư độc lập. Phiên bản `unsafeTransfer` trừ tài khoản A rồi cố tình ném ngoại lệ trước khi cộng B. Phiên bản `safeTransfer` chuẩn bị giá trị mới và kiểm tra lỗi **trước khi** thay hai trường. Luồng quan sát khác được khởi động sau khi lời gọi thất bại và gọi `snapshot()` (cũng là `synchronized`), chứng minh nó vẫn lấy được monitor đã giải phóng.

Lưu thành tệp **`SynchronizedInvariantDemo.java`**:

~~~java
public class SynchronizedInvariantDemo {
    record Snapshot(long a, long b) {
        @Override
        public String toString() {
            return "A=" + a + ", B=" + b + ", total=" + (a + b);
        }
    }

    static final class Ledger {
        private long a;
        private long b;

        Ledger(long a, long b) {
            if (a < 0 || b < 0) {
                throw new IllegalArgumentException("negative initial balance");
            }
            this.a = a;
            this.b = b;
        }

        public synchronized void unsafeTransfer(long amount, boolean simulateFailure) {
            if (amount <= 0 || amount > a) {
                throw new IllegalArgumentException("invalid transfer amount");
            }

            a -= amount; // Trạng thái thật đã thay đổi.
            if (simulateFailure) {
                throw new IllegalStateException("simulated failure");
            }
            b = Math.addExact(b, amount); // Có thể lỗi sau khi A đã giảm.
        }

        public synchronized void safeTransfer(long amount, boolean simulateFailure) {
            if (amount <= 0 || amount > a) {
                throw new IllegalArgumentException("invalid transfer amount");
            }

            long nextA = Math.subtractExact(a, amount);
            long nextB = Math.addExact(b, amount);

            // Mọi nhánh ném lỗi dự kiến đều chạy trước khi commit.
            if (simulateFailure) {
                throw new IllegalStateException("simulated failure");
            }

            a = nextA;
            b = nextB;
        }

        public synchronized Snapshot snapshot() {
            return new Snapshot(a, b);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Ledger unsafe = new Ledger(100, 50);
        try {
            unsafe.unsafeTransfer(30, true);
        } catch (IllegalStateException ex) {
            System.out.println("Unsafe rejected: " + ex.getMessage());
        }

        Thread firstObserver = new Thread(
                () -> System.out.println("Unsafe observer: " + unsafe.snapshot()));
        firstObserver.start();
        firstObserver.join();

        Ledger safe = new Ledger(100, 50);
        try {
            safe.safeTransfer(30, true);
        } catch (IllegalStateException ex) {
            System.out.println("Safe rejected: " + ex.getMessage());
        }

        Thread secondObserver = new Thread(
                () -> System.out.println("Safe observer: " + safe.snapshot()));
        secondObserver.start();
        secondObserver.join();

        safe.safeTransfer(30, false);
        System.out.println("After safe success: " + safe.snapshot());
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac SynchronizedInvariantDemo.java
java SynchronizedInvariantDemo
~~~

Kết quả stdout:

~~~text
Unsafe rejected: simulated failure
Unsafe observer: A=70, B=50, total=120
Safe rejected: simulated failure
Safe observer: A=100, B=50, total=150
After safe success: A=70, B=80, total=150
~~~

**Giải thích kết quả:**

- `unsafeTransfer` đi ra do `IllegalStateException`; nếu monitor không được nhả, luồng `firstObserver` sẽ không thể vào `snapshot()` rồi hoàn tất `join()`. Nhưng monitor đã được nhả và observer đọc được A=70/B=50, tức **một trạng thái có thể nhìn thấy nhưng vi phạm bất biến**.
- `safeTransfer` ném cùng loại ngoại lệ trước bất cứ phép gán nào vào `a`/`b`. Luồng khác vào `snapshot()` vẫn đọc 100/50.
- Lần `safeTransfer(30, false)` hoàn thành, số dư trở thành 70/80 với tổng 150.
- `Math.addExact` phát hiện tràn số `long` thay vì cho phép số học vòng âm thầm; vì phép tính xảy ra trước commit trong bản safe, lỗi tràn cũng không làm A bị trừ dở.
- Việc observer dùng một luồng khác và `join()` giúp đầu ra có thứ tự xác định. Đây là chương trình minh họa **sự khác biệt về ngữ nghĩa**, không phải công cụ chứng minh mọi lịch trình luồng trong chương trình thực tế.

**Giới hạn của mẫu `safeTransfer`:** hai phép gán `a = nextA` và `b = nextB` vẫn là hai câu lệnh. Trong mẫu đã cho, không có lời gọi mã người dùng hoặc phép tính có khả năng ném exception nằm giữa hai phép gán, và mọi lối đọc công khai qua `snapshot()` dùng cùng monitor. Để có bảo đảm trạng thái nhất quán mạnh hơn trong thiết kế phức tạp, nên biểu diễn cặp số dư bằng một snapshot bất biến và commit bằng một lần thay tham chiếu (xem phần 7).

## 5. Java Memory Model: thấy dữ liệu mới không đồng nghĩa dữ liệu đúng

Theo quy tắc *happens-before* của Java Memory Model, một thao tác **unlock** trên monitor xảy ra trước một thao tác **lock tiếp theo** trên cùng monitor. Các thao tác ghi trước unlock được công bố thích hợp tới luồng vào sau nếu chương trình tuân theo quy tắc đồng bộ.

Điều này giải quyết bài toán **visibility** và hỗ trợ suy luận về thứ tự thực thi. Nhưng JVM không kiểm tra bất biến nghiệp vụ:

~~~text
Thread 1                            Thread 2
--------                            --------
lock(this)
a = 70
throw exception
unlock(this)
                                    lock(this)
                                    read a = 70
                                    read b = 50
                                    unlock(this)
~~~

Luồng 2 có thể nhận được **một ảnh chụp nhất quán về mặt bộ nhớ** của trạng thái mà luồng 1 để lại, trong khi trạng thái đó **sai về mặt nghiệp vụ**. `volatile`, memory barrier hoặc cơ chế khóa không tự cung cấp tính rollback.

Đừng lẫn ba mục tiêu:

1. **Mutual exclusion:** các vùng dùng cùng monitor không cùng chạy tại một thời điểm.
2. **Visibility/order:** các luồng phối hợp nhìn thấy thay đổi theo quy tắc mô hình bộ nhớ.
3. **Logical atomicity / invariant preservation:** thao tác nghiệp vụ chỉ công bố trạng thái hợp lệ theo hợp đồng, kể cả nếu thất bại.

`synchronized` đóng góp trực tiếp cho hai mục đầu. Mục thứ ba phụ thuộc **thuật toán và ranh giới cập nhật**; khóa có thể là một phần của giải pháp nhưng không thay thế giải pháp đó.

## 6. Ba mức bảo đảm khi có ngoại lệ

| Mức bảo đảm | Trạng thái sau khi thao tác thất bại | Liên quan tới `synchronized` |
|---|---|---|
| **Không có bảo đảm hữu ích** | Có thể đã vi phạm invariant, cần sửa chữa hoặc bỏ đối tượng | Khóa vẫn có thể đã nhả đúng; `unsafeTransfer` là ví dụ |
| **Basic exception safety** | Đối tượng vẫn hợp lệ, không rò rỉ tài nguyên, nhưng có thể đã thay đổi một phần theo hợp đồng | Cần đảm bảo mọi điểm dừng do lỗi vẫn giữ invariant; mutex chỉ bảo vệ quyền truy cập |
| **Strong exception safety** | Thao tác thất bại để lại trạng thái quan sát được như trước (*commit toàn bộ hoặc không commit*) | Thường dùng validate → prepare → commit, snapshot bất biến, rollback/transaction |

Cụm “không ném ngoại lệ” là một mục tiêu khác và phải nêu rõ phạm vi, vì lỗi hạ tầng nghiêm trọng hay tiến trình bị dừng có thể nằm ngoài bảo đảm hợp lý của API. Một phương thức `synchronized` không mặc nhiên đạt cả **basic guarantee**. Nếu nó để invariant sai sau khi lỗi, mức basic cũng thất bại dù khóa đã được giải phóng.

## 7. Thiết kế an toàn hơn: validate → prepare → commit

Để giữ invariant, hãy sắp xếp các thao tác theo ba pha:

- **Validate:** kiểm tra tham số, trạng thái hiện tại và các ràng buộc; thất bại trước khi thay đổi dữ liệu live.
- **Prepare:** tính toàn bộ giá trị dự kiến, kiểm tra tràn số, xây dựng snapshot mới trong biến cục bộ; nếu thất bại, bản đang sử dụng không đổi.
- **Commit:** công bố trạng thái mới qua thao tác càng đơn giản càng tốt và không gọi callback hoặc I/O có thể lỗi ở giữa.

Một lớp cặp số dư có thể sử dụng một `record` bất biến:

~~~java
// Trích đoạn khai báo trong một lớp, không phải chương trình Java độc lập.
record Balances(long a, long b) {}

private Balances balances = new Balances(100, 50);

public synchronized void transfer(long amount) {
    Balances current = balances;
    if (amount <= 0 || amount > current.a()) {
        throw new IllegalArgumentException("invalid transfer amount");
    }

    long nextA = Math.subtractExact(current.a(), amount);
    long nextB = Math.addExact(current.b(), amount);
    Balances next = new Balances(nextA, nextB);

    balances = next; // Một điểm công bố snapshot mới.
}

public synchronized Balances snapshot() {
    return balances;
}
~~~

Trong đoạn này, cặp `a`/`b` được chuẩn bị hoàn toàn trước khi công bố. Nếu kiểm tra, số học hoặc tạo snapshot ném ngoại lệ, `balances` vẫn giữ tham chiếu cũ. `Balances` chỉ chứa hai giá trị nguyên thủy nên không làm lộ dữ liệu mutable bên trong; nếu record chứa `List`, `Map` hoặc array có thể thay đổi, cần tiếp tục thiết kế bất biến sâu/sao chép phòng vệ.

**Vì sao vẫn dùng `synchronized`?** Để các writer không cùng đọc một snapshot cũ rồi lần lượt ghi đè lẫn nhau (*lost update*), và để reader thấy cập nhật theo cùng cơ chế khóa. Một phép gán tham chiếu đơn lẻ không tự giải quyết việc nhiều luồng cùng tranh chấp hoặc việc công bố an toàn khi reader bỏ qua khóa.

Nếu cần không dùng monitor, có thể biểu diễn trạng thái bất biến bằng `AtomicReference<Balances>` và vòng lặp `compareAndSet` cho các cập nhật tranh chấp. Điều kiện và dữ liệu mới phải được tính từ snapshot đọc được trong từng lượt CAS; khi CAS thất bại phải đọc lại và kiểm tra lại. Đây là một lựa chọn thiết kế concurrency, không phải biện pháp tự rollback cho side effect bên ngoài.

## 8. Đặt vị trí bắt exception ở đâu?

### 8.1. `catch` ở bên ngoài khối đồng bộ

~~~java
// Mã minh họa; lock và doWork() được khai báo ở nơi khác.
try {
    synchronized (lock) {
        doWork();
    }
} catch (RuntimeException ex) {
    reportFailure(ex);
}
~~~

Nếu `doWork()` ném lỗi, khối `synchronized` đã thoát và monitor của lần vào đó đã được nhả trước khi `reportFailure` chạy. Tuy nhiên, việc catch nằm ngoài **không khôi phục** các trường mà `doWork` đã chỉnh sửa.

### 8.2. `catch` ở bên trong khối đồng bộ

~~~java
synchronized (lock) {
    try {
        doWork();
    } catch (RuntimeException ex) {
        repairIfPossible(ex);
    }
    // Vẫn đang giữ monitor lock ở vị trí này.
}
~~~

Handler chạy khi monitor vẫn được giữ (trừ hành vi đặc biệt của các thao tác như `wait` nếu bản thân handler gọi). Điều này có thể hữu ích nếu có thể phục hồi invariant **ngay dưới cùng khóa** trước khi luồng khác được phép đọc. Nhưng rollback phải thực sự chính xác; nuốt ngoại lệ rồi tiếp tục với trạng thái chưa khôi phục có thể gây lỗi kín đáo hơn.

Nếu `repairIfPossible` tự ném ngoại lệ, JVM vẫn nhả monitor khi thoát khối, nhưng trạng thái có thể dở dang. Mọi con đường ném lỗi, kể cả đường khắc phục lỗi, phải được xét.

### 8.3. `finally` và bảo toàn trạng thái

`finally` thường được dùng cho cleanup, nhưng `synchronized` **không cần một `finally` thủ công để nhả monitor**. Ví dụ:

~~~java
synchronized (lock) {
    try {
        performWork();
    } finally {
        closeTemporaryResource();
    }
}
~~~

Dù `performWork()` ném lỗi, `finally` được thực hiện trước khi khối đồng bộ kết thúc bình thường hoặc bất thường. Nếu `closeTemporaryResource()` cũng ném lỗi, nó có thể thay thế ngoại lệ ban đầu nếu không được xử lý thích hợp; hãy thiết kế cleanup và báo lỗi có chủ đích. Việc cleanup/rollback tài nguyên là **trách nhiệm khác** với nhả monitor.

## 9. Phân biệt `synchronized` với `Lock` dùng thủ công

`synchronized` được ngôn ngữ và JVM bảo đảm giải phóng monitor khi thoát vùng đồng bộ. `ReentrantLock` và các `Lock` khác cung cấp API lấy/nhả khóa **tường minh**; ứng dụng phải gọi `unlock()` đúng cách.

Mẫu phổ biến với `ReentrantLock`:

~~~java
// Trích đoạn trong lớp có:
 // private final java.util.concurrent.locks.ReentrantLock lock =
 //         new java.util.concurrent.locks.ReentrantLock();
lock.lock();
try {
    validateAndUpdate();
} finally {
    lock.unlock();
}
~~~

Nếu `validateAndUpdate()` ném ngoại lệ, `finally` bảo đảm lời gọi `unlock()` được thực hiện trên đường thoát này. Nhưng `unlock()` cũng chỉ nhả khóa, **không hoàn tác** những gì `validateAndUpdate()` đã ghi.

Nếu lấy khóa bằng `lockInterruptibly()` hoặc `tryLock()`, chỉ gọi `unlock()` khi đã thực sự lấy được khóa. `ReentrantLock` có thêm tính năng so với `synchronized` (ví dụ thử lấy khóa, chờ có thể bị ngắt, `Condition`), nhưng không cung cấp transaction miền nghiệp vụ tự động.

## 10. Những nơi có thể làm thất bại khi đã cầm khóa

Không nên chỉ tìm câu lệnh `throw` viết sẵn trong phương thức; ngoại lệ có thể phát sinh tại:

| Vị trí | Khả năng lỗi | Hướng giảm rủi ro |
|---|---|---|
| Gọi `Math.addExact`/`subtractExact` | `ArithmeticException` do overflow | Tính trước vào biến cục bộ |
| Thao tác với collection | `NullPointerException`, `IllegalArgumentException`, thay đổi cấu trúc hoặc lỗi cấp phát | Sao chép dữ liệu, xác thực toàn bộ, áp dụng vào bản tạm |
| `new`, tạo cấu trúc dữ liệu | Lỗi cấp phát, constructor ném exception | Chuẩn bị đối tượng mới trước khi thay tham chiếu live |
| Setter, listener hoặc callback | Có thể ném ngoại lệ hay sửa ngược trạng thái | Giảm callback trong vùng commit; xác định rõ trạng thái khi callback lỗi |
| Ghi file/cơ sở dữ liệu/network | IOException, timeout, lỗi dịch vụ; có thể có side effect | Phân biệt transaction và thao tác ngoài; xác định cơ chế bù hoặc idempotency |
| Ghi log, thông báo sự kiện | Logger/appender/listener có thể lỗi | Không để lỗi phụ che mất lỗi chính hoặc làm mơ hồ tình trạng commit |
| Cơ chế retry | Có thể thực hiện lại phần công việc đã hoàn tất | Thiết kế idempotency và đánh dấu tiến độ theo hợp đồng |

Nếu một phương thức viết:

~~~java
synchronized (lock) {
    decrementInventory();
    sendPaymentRequest();
    saveOrder();
}
~~~

thì khóa chỉ điều phối luồng Java dùng monitor `lock`. Nó **không** biến ba lời gọi thành một transaction và không làm bên thanh toán hoàn tiền nếu `saveOrder()` thất bại. Trong phần lỗi liên hệ dịch vụ ngoài, ngay cả câu hỏi “bên kia đã xử lý chưa?” đôi khi không thể trả lời chỉ bằng exception do client nhận được.

## 11. `wait`, `notify` và invariant tạm thời

Đây là trường hợp quan trọng vì quy tắc “đang trong `synchronized` thì luôn giữ khóa cho đến cuối khối” có ngoại lệ về **`Object.wait()`**.

Ví dụ một phương thức chờ điều kiện:

~~~java
// Trích đoạn dùng lock, ready và currentValue đã khai báo ở nơi khác.
synchronized (lock) {
    while (!ready) {
        lock.wait(); // Nhả monitor lock tạm thời khi chờ.
    }
    use(currentValue);
}
~~~

`wait()` chỉ được gọi khi luồng đang giữ monitor của `lock`; nếu không, `IllegalMonitorStateException` có thể xảy ra. Trong lúc chờ, luồng đã nhả monitor tương ứng để luồng khác có thể cập nhật `ready` rồi thông báo. Trước khi `wait()` trả về (kể cả khi bị ngắt và ném `InterruptedException`), luồng phải lấy lại monitor đó.

Vì một luồng khác có thể thay đổi điều kiện, luôn kiểm tra lại điều kiện trong **vòng `while`**, không giả định cứ được đánh thức là dữ liệu đủ điều kiện. Java cho phép *spurious wakeup*; `notify` cũng không bảo đảm điều kiện nghiệp vụ trở thành đúng.

**Liên hệ bất biến:** trước khi thực sự vào trạng thái chờ và nhả monitor, cấu trúc dữ liệu chia sẻ phải nằm ở trạng thái các luồng khác được phép nhìn thấy. Nếu bạn chỉnh một nửa cấu trúc rồi gọi `wait()`, luồng khác có thể lấy monitor và thấy dữ liệu dở dang. Cũng cần tránh giữ các monitor khác vô tình gây deadlock khi chờ.

## 12. Bảo đảm bất biến khi có nhiều đối tượng hoặc nhiều khóa

Ví dụ có hai `Account` khác nhau:

~~~java
// Mã minh họa về điều kiện chưa đủ để an toàn.
synchronized (source) {
    source.debit(amount);
}
synchronized (destination) {
    destination.credit(amount);
}
~~~

Hai khối sử dụng hai monitor khác nhau và có một khoảng trống giữa chúng. Nếu phần ghi tài khoản đích lỗi, phần ghi nguồn vẫn tồn tại. Ngay cả khi lồng hai khóa:

~~~java
// Mã minh họa: chỉ cho thấy cùng giữ khóa, chưa bảo đảm rollback.
synchronized (source) {
    synchronized (destination) {
        source.debit(amount);
        destination.credit(amount);
    }
}
~~~

một exception từ `credit` vẫn có thể khiến nguồn đã bị trừ. Ngoài ra, nếu luồng khác khóa `destination` rồi `source`, chương trình có nguy cơ deadlock do thứ tự khóa đảo ngược.

Để thiết kế một thao tác chuyển tiền đáng tin cậy cần xem xét đồng thời:

1. Khóa/transaction bao phủ **tất cả** phần trạng thái phải thay đổi cùng nhau.
2. Kiểm tra đủ tiền và điều kiện của tài khoản đích dưới cùng cơ chế bảo vệ trạng thái.
3. Tính trước kết quả, kiểm tra overflow và mọi lỗi dự kiến trước commit.
4. Chọn điểm commit hoặc cơ chế rollback mà reader không thể thấy trạng thái trung gian ngoài hợp đồng.
5. Định nghĩa thứ tự lấy khóa ổn định nếu phải lấy nhiều lock; cân nhắc transaction DB cho dữ liệu lưu bền vững.
6. Không giả định hệ thống ngoài JVM hoặc ngoài transaction địa phương chịu sự kiểm soát của monitor Java.

Lưu ý: nguyên tắc **bảo vệ cùng monitor** chỉ có hiệu quả khi toàn bộ đường truy cập và thay đổi trạng thái liên quan đều tuân thủ. Một getter không đồng bộ hoặc trả trực tiếp danh sách nội bộ mutable có thể làm lộ trạng thái trung gian; `synchronized` ở writer không tự khóa getter đó.

## 13. Thiết kế API exception và rollback đúng mức

Tài liệu API nên nói rõ cả **điều kiện ném ngoại lệ** và **trạng thái sau thất bại**. Những cách diễn đạt có ý nghĩa khác nhau:

- “Nếu `amount` không hợp lệ, ném `IllegalArgumentException` và không thay đổi số dư”: cam kết rõ cho lỗi kiểm tra đầu vào.
- “Nếu thao tác thất bại, đối tượng vẫn thỏa mọi invariant nhưng các phần cập nhật trước đó có thể còn hiệu lực”: basic guarantee.
- “Nếu thao tác thất bại trước commit, không có thay đổi trạng thái nào được công bố”: cam kết strong trong phạm vi trước commit.
- “Nếu gửi yêu cầu thanh toán thất bại, trạng thái xử lý thanh toán có thể cần tra soát”: tránh hứa rollback khi lỗi mạng không xác định trạng thái hệ thống ngoài.

Nếu chủ động rollback trong `catch`, phải tính đến khả năng rollback cũng thất bại. Ví dụ rollback “cộng lại số tiền đã trừ” không phải lúc nào cũng đảo ngược chính xác các cập nhật khác hoặc tương tác song song. Cần tính toán sự toàn vẹn qua nhiều loại ngoại lệ, kể cả ngoại lệ từ cleanup. `try-with-resources` có cơ chế ghi nhận một số lỗi đóng tài nguyên qua `getSuppressed()`, nhưng nó không tự rollback dữ liệu nghiệp vụ.

Với `InterruptedException`, không được tùy tiện nuốt tín hiệu hủy tác vụ chỉ để “đi đến cuối `synchronized`”. Nếu cần truyền tiếp, khai báo `throws InterruptedException`; nếu bắt và chuyển sang unchecked trong tình huống phù hợp, cân nhắc khôi phục cờ interrupt bằng `Thread.currentThread().interrupt()` và giữ `cause`. Dù lựa chọn nào, invariant cần được duy trì trên đường thoát đó.

## 14. Những hiểu lầm phổ biến và cách sửa

1. **“Có `synchronized` thì lỗi sẽ tự rollback.”** Sai: khóa quản lý monitor. Khắc phục bằng thiết kế validate → prepare → commit, snapshot bất biến hoặc transaction.
2. **“Ném exception trong `synchronized` sẽ làm khóa bị kẹt.”** Sai khi ngoại lệ khiến vùng đồng bộ kết thúc; JVM nhả monitor. Kẹt thật có thể do deadlock, vòng lặp chờ vô hạn hoặc code chưa thoát vùng đồng bộ.
3. **“Nếu luồng sau lấy được khóa thì dữ liệu chắc chắn đúng.”** Sai: luồng sau có thể đọc một trạng thái sai được công bố đầy đủ.
4. **“Chỉ cần `catch` và `return` là đã khôi phục.”** Sai: phải xác minh mọi bất biến sau nhánh xử lý lỗi.
5. **“Đặt `finally` trong `synchronized` thì tự rollback.”** Sai: `finally` chạy mã đã viết; nếu không có rollback hoặc cleanup đúng, nó không tự hoàn tác.
6. **“Hai phép gán trong một method `synchronized` luôn nguyên tử với mọi reader.”** Chỉ đúng theo nghĩa reader phối hợp qua **cùng monitor** không chen vào vùng đồng bộ; đường đọc không hợp tác vẫn có thể quan sát dữ liệu khác biệt.
7. **“`volatile` thay được `synchronized` để cập nhật nhiều trường.”** Sai: visibility của trường không biến nhiều phép gán thành một transaction.
8. **“`wait()` cứ ở trong `synchronized` nên các luồng khác không thể vào.”** Sai: `wait()` tạm nhả monitor của đối tượng được chờ.
9. **“Một transaction DB đồng nghĩa mọi email/thanh toán đi kèm cũng rollback.”** Sai: side effect ngoài ranh giới transaction cần cơ chế riêng.
10. **“Mọi lỗi đều khôi phục được bằng phép gán ngược.”** Sai khi có side effect, callback, dữ liệu biến đổi cạnh tranh, hoặc bản gốc không còn đầy đủ.

## 15. Kiểm thử đúng cả quyền truy cập và bất biến

Một bộ kiểm thử tốt phải kiểm tra **hai nhóm độc lập**, vì thành công ở một nhóm không kéo theo nhóm còn lại.

| Nhóm | Kịch bản | Kết quả mong đợi |
|---|---|---|
| Monitor | Phương thức `synchronized` ném `RuntimeException`, một luồng khác gọi phương thức cùng monitor sau đó | Luồng sau vào được vùng đồng bộ, không bị khóa kẹt do ngoại lệ |
| Invariant | Ngoại lệ phát sinh sau phép cập nhật đầu tiên | Đánh giá có vi phạm invariant không; với hợp đồng strong, trạng thái phải nguyên vẹn |
| Input | Số tiền âm, quá số dư, tham số null | Ném đúng loại lỗi trước khi thay đổi |
| Overflow | Tổng tiền hoặc chỉ số phiên bản vượt giới hạn `long`/`int` | Không commit giá trị sai; exception được truyền đúng |
| Snapshot | Lỗi khi dựng trạng thái đề xuất | Bản trạng thái đang sử dụng không đổi |
| Concurrency | Hai luồng cùng yêu cầu thay đổi, có cùng cơ chế lock/CAS | Không mất cập nhật, không phá invariant |
| Visibility | Reader được/không được đồng bộ theo hợp đồng | Đánh giá trường hợp đọc trạng thái trung gian |
| Wait | Điều kiện chờ thay đổi hoặc đánh thức giả | `while` kiểm tra lại điều kiện; invariant vẫn đúng |
| Side effect | I/O, DB hoặc callback thất bại sau một bước thành công | Tài liệu và hành vi thống nhất về việc có rollback được không |

Trong ví dụ `SynchronizedInvariantDemo`, đầu ra “Unsafe observer” vừa chứng minh được **monitor được nhả**, vừa cho thấy **invariant bị phá**. Một test chỉ kiểm tra `assertThrows` hoặc chỉ kiểm tra luồng quan sát không bị treo sẽ bỏ sót vấn đề dữ liệu.

Lưu ý một test concurrency chạy thành công một vài lần không phải chứng minh đầy đủ không có race/deadlock. Cần lập luận về monitor được giữ, thời điểm lấy/nhả, mọi đường đọc/ghi, point of commit và các đường ngoại lệ; test giúp xác nhận những lập luận đó bằng tình huống thực tế.

## 16. Quy trình rà soát một phương thức `synchronized`

Khi đọc mã có trạng thái mutable và xử lý exception, áp dụng lần lượt các câu hỏi sau:

1. **Monitor nào được lấy?** `this`, `Class` hay một object lock riêng? Các phương thức liên quan có dùng cùng monitor không?
2. **Invariant là gì?** Viết thành mệnh đề cụ thể: tổng, miền giá trị, quan hệ giữa các trường, điều kiện đối tượng hợp lệ.
3. **Điểm đầu tiên sửa trạng thái live nằm ở đâu?** Đánh dấu từng phép gán, sửa collection hoặc side effect.
4. **Sau mỗi điểm sửa, câu lệnh nào có thể ném ngoại lệ?** Tính toán, callback, I/O, cấp phát, logging và các lần gọi phụ.
5. **Nếu thoát ngay tại điểm đó, invariant còn đúng không?** Nếu không, cần đổi thứ tự thao tác hoặc chuẩn bị trạng thái mới trước khi commit.
6. **Reader có thể thấy trạng thái trung gian không?** Xét cùng monitor, reader không hợp tác, snapshot mutable, `wait()` và những tài nguyên khác.
7. **Khả năng rollback thực sự đến đâu?** Đối tượng JVM khác với DB transaction và lệnh mạng; định nghĩa rõ đường không thể hoàn tác.
8. **Hợp đồng thất bại được ghi trong API chưa?** Loại exception, `cause`, mức bảo đảm khi thất bại và cách caller khôi phục.
9. **Test đã gây lỗi ở đúng bước nhạy cảm chưa?** Bố trí thử lỗi trước commit, trong lúc chuẩn bị và sau side effect; kiểm tra cả state lẫn khả năng lấy monitor tiếp theo.

## 17. Tổng kết kiến thức

- `synchronized` khiến JVM tự giải phóng monitor khi thoát vùng đồng bộ bình thường hoặc bất thường do ngoại lệ. Quy tắc này **không tự hoàn tác phép gán hoặc side effect**.
- Việc giải phóng monitor cung cấp điểm phối hợp và *happens-before* cho luồng khác lấy cùng monitor; luồng đó vẫn có thể nhìn thấy dữ liệu sai nếu luồng trước bỏ lại invariant bị phá.
- Giữ dữ liệu hợp lệ sau khi thao tác thất bại phải được thiết kế bằng kiểm tra điều kiện trước, chuẩn bị dữ liệu mới, xác định commit point, đồng bộ đúng phạm vi và transaction/rollback khi cần.
- `catch`/`finally` thay đổi luồng xử lý lỗi; chúng không thay thế bằng chứng rằng mọi nhánh thoát đều bảo toàn trạng thái.
- Khi đánh giá một phương thức, luôn hỏi **hai câu riêng**: “Ngoại lệ có giải phóng khóa không?” và “Dữ liệu mà luồng sau đọc được còn hợp lệ không?”

### Tham khảo chuẩn Java

- [JLS §14.19 — The synchronized Statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.19)
- [JLS §17.1 — Synchronization](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.1)
- [JLS §17.4.5 — Happens-before Order](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.5)
- [Java SE 17 — `Object.wait()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#wait())
- [Java SE 17 — `ReentrantLock`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-safety-009">Xử lý lỗi cleanup mà vẫn giữ thông tin lỗi ban đầu</a>

<details>
<summary>Xem chi tiết</summary>

# Xử lý lỗi cleanup mà vẫn giữ thông tin lỗi ban đầu

## 1. Khi công việc chính và cleanup cùng thất bại

Trong Java, một phương thức thường vừa làm công việc chính, vừa quản lý tài nguyên mà nó sử dụng. Công việc chính có thể là đọc file, ghi dữ liệu, thực thi truy vấn, gọi dịch vụ hoặc xử lý nghiệp vụ. Cleanup có thể là đóng stream, trả connection, giải phóng khóa, dừng session hoặc thu hồi tài nguyên tạm.

Một tình huống khó xảy ra khi **cả công việc chính và cleanup đều ném exception**. Chẳng hạn, thao tác ghi file thất bại vì lỗi I/O; trong lúc rời phương thức, thao tác đóng stream cũng thất bại. Khi đó có hai thông tin riêng cần giữ lại:

- **Primary exception (lỗi ban đầu)**: lỗi từ thao tác chính, giải thích vì sao công việc đang thực hiện không hoàn tất.
- **Cleanup exception (lỗi dọn dẹp)**: lỗi phát sinh khi cố hoàn tất hoặc giải phóng tài nguyên, cho biết một bước thu hồi cũng không thành công.

Nếu exception cleanup thay thế exception chính, lập trình viên có thể chỉ thấy “close failed” và không biết nguyên nhân thật là “write failed”. Nếu nuốt lỗi cleanup, người vận hành lại mất thông tin rằng việc đóng/hoàn tất tài nguyên có vấn đề.

**Mục tiêu thiết kế:** Khi công việc chính đã thất bại, tiếp tục giữ exception ban đầu làm exception được truyền ra và đính kèm exception cleanup để chẩn đoán. Khi công việc chính thành công nhưng cleanup thất bại, chính cleanup exception thường là lỗi của lời gọi.

Bảo toàn exception là một phần của **bảo toàn trạng thái**: nó giúp hệ thống quyết định kết quả, ghi nhận sự cố và xử lý hậu quả đúng. Tuy nhiên, giữ exception **không đồng nghĩa** tự rollback dữ liệu đã ghi hoặc phục hồi tài nguyên.

## 2. Vì sao finally có thể che mất lỗi gốc?

Khối **finally** được chạy khi luồng điều khiển rời **try/catch** trong điều kiện thông thường, kể cả khi thân **try** ném exception. Vì vậy nó thường được dùng cho cleanup. Vấn đề là **nếu finally ném exception khác, exception đang chờ được ném từ try có thể bị thay thế**.

Mã minh họa sau không phải chương trình Java độc lập:

~~~java
void save() throws java.io.IOException {
    try {
        throw new java.io.IOException("write failed");
    } finally {
        throw new java.io.IOException("close failed");
    }
}
~~~

Người gọi nhận lỗi **close failed**; lỗi **write failed** không tự xuất hiện trong **getCause()** hoặc **getSuppressed()**. Chỉ bọc **close()** vào **finally** chưa đủ để giữ thông tin lỗi chính.

Một biến thể còn nguy hiểm là **return trong finally**:

~~~java
// Minh họa phản mẫu; không phải chương trình độc lập.
try {
    performWork();  // Có thể ném exception.
} finally {
    return;         // Có thể che khuất exception đang chờ.
}
~~~

Nếu finally kết thúc bằng return, exception đang chờ có thể bị bỏ qua. Vì thế, tránh **return** trong finally và tránh cho lỗi cleanup bất ngờ che lấp lỗi cần báo ra.

Ngược lại, mẫu này cũng có vấn đề:

~~~java
// Minh họa phản mẫu; resource đã được khai báo ở nơi khác.
try {
    resource.close();
} catch (Exception ignored) {
    // Không còn bằng chứng về việc close() thất bại.
}
~~~

Catch rồi bỏ qua toàn bộ lỗi cleanup làm mất dữ liệu chẩn đoán; trong một số hệ thống, đó còn là dấu hiệu connection hoặc giao dịch đã ở trạng thái không đáng tin.

## 3. Cơ chế chuẩn: try-with-resources

Java cung cấp **try-with-resources** từ Java 7. Mọi tài nguyên triển khai **java.lang.AutoCloseable** có thể được khai báo trong dấu ngoặc của try, để Java tự gọi **close()** khi rời khối:

~~~java
// Minh họa cú pháp; openResource() trả về AutoCloseable.
try (var resource = openResource()) {
    performWork(resource);
}
~~~

Khi phần thân try và **close()** cùng ném exception, quy tắc là:

1. Giữ exception của thân try làm **primary exception**.
2. Gọi close theo quy tắc vòng đời tài nguyên.
3. Đưa exception phát sinh từ close vào danh sách **suppressed exceptions** trên chính primary.
4. Ném primary ra cho người gọi. Có thể lấy danh sách lỗi cleanup bằng **primary.getSuppressed()**.

Nếu phần thân không lỗi nhưng close lỗi, lỗi từ close trở thành exception chính. Java không tạo một exception phần thân giả để làm primary.

**Try-with-resources là giải pháp ưu tiên** khi cleanup chủ yếu là gọi close trên những tài nguyên AutoCloseable. Nó tránh phải tự viết nhiều nhánh catch/finally và giảm nguy cơ mất lỗi gốc.

### Một số điểm quan trọng về vòng đời

- Các tài nguyên trong try-with-resources được đóng theo **thứ tự ngược với thứ tự khai báo**.
- Nếu phần thân thất bại và nhiều close cũng thất bại, lỗi close lần lượt được suppressed vào exception phần thân.
- Nếu khởi tạo tài nguyên thứ hai thất bại sau khi tài nguyên thứ nhất đã được mở, Java vẫn cố đóng tài nguyên thứ nhất.
- Nếu thân try đang chuẩn bị **return**, close vẫn được thực hiện trước khi phương thức hoàn tất; close thất bại có thể khiến phương thức ném exception thay vì trả về giá trị.
- Try-with-resources đảm nhiệm việc gọi close và bảo toàn exception; nó **không bảo đảm** mọi tác dụng phụ trước đó được hoàn tác.

## 4. Ví dụ Java 17+ có thể biên dịch và chạy

Chương trình sau minh họa bốn trường hợp: cả thân và hai close đều thất bại, chỉ close thất bại, finally che lỗi gốc, và cleanup thủ công giữ lỗi gốc bằng **addSuppressed**.

Tạo file **CleanupFailureDemo.java**:

~~~java
import java.io.IOException;

public class CleanupFailureDemo {
    static final class DemoResource implements AutoCloseable {
        private final String name;
        private final boolean failOnClose;

        DemoResource(String name, boolean failOnClose) {
            this.name = name;
            this.failOnClose = failOnClose;
        }

        @Override
        public void close() throws IOException {
            System.out.println("close " + name);
            if (failOnClose) {
                throw new IOException("close " + name + " failed");
            }
        }
    }

    static void badFinally() throws IOException {
        try {
            throw new IOException("operation failed");
        } finally {
            throw new IOException("cleanup failed");
        }
    }

    static void manualPreserve() throws IOException {
        DemoResource resource = new DemoResource("manual", true);
        IOException primary = null;

        try {
            throw new IOException("operation failed");
        } catch (IOException original) {
            primary = original;
            throw original;
        } finally {
            try {
                resource.close();
            } catch (IOException cleanup) {
                if (primary != null) {
                    primary.addSuppressed(cleanup);
                } else {
                    throw cleanup;
                }
            }
        }
    }

    static void printFailure(IOException failure) {
        System.out.println("primary: " + failure.getMessage());
        Throwable[] suppressed = failure.getSuppressed();
        for (int i = 0; i < suppressed.length; i++) {
            System.out.println("suppressed " + (i + 1) + ": "
                    + suppressed[i].getMessage());
        }
    }

    public static void main(String[] args) {
        System.out.println("== Work and two close failures ==");
        try (DemoResource a = new DemoResource("A", true);
             DemoResource b = new DemoResource("B", true)) {
            throw new IOException("operation failed");
        } catch (IOException failure) {
            printFailure(failure);
        }

        System.out.println("== Close failure only ==");
        try (DemoResource r = new DemoResource("single", true)) {
            System.out.println("operation succeeded");
        } catch (IOException failure) {
            printFailure(failure);
        }

        System.out.println("== Finally masks primary ==");
        try {
            badFinally();
        } catch (IOException failure) {
            printFailure(failure);
        }

        System.out.println("== Manual cleanup preserves primary ==");
        try {
            manualPreserve();
        } catch (IOException failure) {
            printFailure(failure);
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac --release 17 CleanupFailureDemo.java
java CleanupFailureDemo
~~~

Kết quả stdout đã kiểm tra trực tiếp với **javac --release 17** và **java**:

~~~text
== Work and two close failures ==
close B
close A
primary: operation failed
suppressed 1: close B failed
suppressed 2: close A failed
== Close failure only ==
operation succeeded
close single
primary: close single failed
== Finally masks primary ==
primary: cleanup failed
== Manual cleanup preserves primary ==
close manual
primary: operation failed
suppressed 1: close manual failed
~~~

### Phân tích đường chạy

**Thân try và hai close đều lỗi:** phần thân ném **IOException("operation failed")**. Khi rời try, B được đóng trước A. Cả hai lần đóng ném IOException; cả hai được lưu trong suppressed của lỗi phần thân. Vì vậy output vẫn có **primary: operation failed**, sau đó mới liệt kê **close B failed**, **close A failed**.

**Thân try thành công, close lỗi:** chương trình in **operation succeeded** trước khi đóng single. Do close thất bại và không có exception từ phần thân, **close single failed** trở thành primary; không có suppressed khác.

**Finally che lỗi ban đầu:** badFinally ném lỗi công việc trước và lỗi cleanup sau. Exception thoát ra từ phương thức là **cleanup failed**. Đây là phản ví dụ cho việc cleanup thủ công bất cẩn.

**Cleanup thủ công bảo toàn lỗi ban đầu:** manualPreserve ghi nhớ đối tượng IOException gốc rồi ném lại. Trong finally, nếu close lỗi, nó được thêm vào chính exception gốc qua **addSuppressed**; exception của phần thân tiếp tục được truyền ra.

Ví dụ **manualPreserve chỉ xử lý đường lỗi IOException**: nếu thân có thể ném RuntimeException hoặc Error, catch(IOException) sẽ không ghi nhận chúng; lỗi close phát sinh từ finally có thể che mất một lỗi khác. Không nên sao chép mẫu thủ công này như một cơ chế tổng quát cho mọi Throwable. Với tài nguyên AutoCloseable, **try-with-resources giải quyết trọn hơn** những trường hợp đó.

## 5. Suppressed exception là gì?

Mỗi đối tượng **Throwable** có thể giữ danh sách các exception bị suppressed. Hai phương thức chính:

- **addSuppressed(Throwable other)**: gắn một lỗi phụ vào Throwable hiện tại.
- **getSuppressed()**: lấy mảng những Throwable đã được gắn, theo thứ tự được thêm.

Ví dụ, bên ngoài một khối try-with-resources:

~~~java
// Ví dụ minh họa trong một catch block.
catch (java.io.IOException failure) {
    System.err.println("Primary: " + failure);
    for (Throwable cleanup : failure.getSuppressed()) {
        System.err.println("Cleanup: " + cleanup);
    }
    throw failure;
}
~~~

Đoạn trên chỉ là mã minh họa cho phần thân catch, không phải chương trình độc lập; catch phải đi kèm với một try phù hợp.

**Suppressed không phải cause.** Suppressed mô tả lỗi phụ xuất hiện khi một lỗi khác đang được xử lý hoặc hoàn tất tài nguyên. Cause mô tả chuỗi nguyên nhân của một exception được bọc hoặc chuyển đổi sang mức trừu tượng khác.

| Quan hệ | Ý nghĩa | Cách truy cập |
| --- | --- | --- |
| **Primary** | Lỗi chính được ném ra cho người gọi | Exception mà caller bắt được |
| **Cause** | Lỗi cấp dưới là nguyên nhân của lỗi cấp trên | **getCause()** |
| **Suppressed** | Lỗi phụ được giữ cùng primary, thường từ cleanup | **getSuppressed()** |

Ví dụ, tầng service bọc IOException thành IllegalStateException có cause. Nếu IOException đó mang suppressed của lỗi close thì caller cần đi theo **getCause()** để tìm suppressed trên IOException. Không có quy tắc tự động sao chép danh sách suppressed của cause lên object wrapper.

~~~java
// Minh họa trong một catch hợp lệ:
catch (java.io.IOException failure) {
    throw new IllegalStateException("Unable to save data", failure);
}
~~~

Cách này giữ lỗi ban đầu làm **cause**. Ngược lại, tạo exception mới chỉ với **failure.getMessage()** làm mất chuỗi cause và thông tin stack trace của original.

### Ràng buộc của addSuppressed

**Throwable.addSuppressed(null)** ném NullPointerException. Gắn chính Throwable vào nó sẽ ném IllegalArgumentException do self-suppression. Thông thường không được gắn cùng lỗi chính vào danh sách suppressed của chính nó. Ngoài ra, một số lớp Throwable đặc biệt có thể xây dựng với suppression bị vô hiệu hóa; khi đó addSuppressed không thực sự lưu lỗi. Vì thế việc ghi nhận suppression cần tuân thủ hợp đồng của throwable và không thay thế cơ chế logging/chẩn đoán khác trong mọi trường hợp.

## 6. Đóng nhiều tài nguyên theo thứ tự ngược

Giả sử có nhiều tài nguyên phụ thuộc nhau:

~~~java
// Ví dụ minh họa: cả hai lớp đều thực thi AutoCloseable.
try (ConnectionLike connection = openConnection();
     ReaderLike reader = openReader(connection)) {
    process(reader);
}
~~~

Java đóng **reader trước connection**, vì reader có thể còn cần connection trong quá trình close. Nếu phần thân lỗi và cả reader.close lẫn connection.close đều lỗi, cả hai được suppressed vào primary theo thứ tự close phát sinh.

Khi phân tích thứ tự đóng, cần xét:

1. **Thứ tự tạo** tài nguyên từ trái sang phải.
2. **Thứ tự đóng** từ phải sang trái.
3. **Đường lỗi trong lúc khởi tạo**: những tài nguyên đã tạo trước đó vẫn được đóng.
4. **Đường lỗi khi đóng**: các tài nguyên còn lại vẫn được thử đóng theo cơ chế try-with-resources.
5. **Bảo toàn primary**: lỗi phần thân hoặc lỗi khởi tạo có quyền ưu tiên so với lỗi close.

Một tài nguyên khai báo trong phần đầu nhưng trả về null sẽ không được gọi close. Trong thiết kế API, nên tránh sử dụng null làm tín hiệu tài nguyên hợp lệ khi có thể thiết kế hàm mở tài nguyên trả về object hợp lệ hoặc ném exception rõ ràng.

### Nếu khởi tạo một tài nguyên ở giữa thất bại

Hình dung thứ tự mở A, rồi mở B:

~~~text
open A: thành công
open B: ném exception E
close A: ném exception C

Exception thoát ra: E
Suppressed trên E: C
~~~

B chưa khởi tạo thành công nên không được đóng; A đã mở thành công được đóng. Vì E xảy ra trước trong công việc thiết lập tài nguyên, C được giữ như suppressed thay vì thay thế E.

Đây là một khác biệt lớn giữa try-with-resources và cleanup thủ công sơ sài: mã tự quản lý rất dễ quên đóng tài nguyên đã mở trước khi một tài nguyên sau thất bại.

## 7. Closeable, AutoCloseable và kiểu exception

**AutoCloseable** định nghĩa close có thể khai báo **throws Exception**. **java.io.Closeable** kế thừa AutoCloseable nhưng phương thức close khai báo **throws IOException**. Do đó loại tài nguyên quyết định checked exception nào code phải bắt hoặc khai báo.

Các class JDK thường gặp như InputStream, OutputStream, Reader, Writer, nhiều đối tượng JDBC đều hỗ trợ vòng đời thích hợp cho try-with-resources. Các API khác có thể ném SQLException hoặc exception riêng. Một class tự viết cũng có thể triển khai AutoCloseable và định nghĩa hành vi close theo tài nguyên mà nó sở hữu.

Ví dụ đọc file:

~~~java
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// Phương thức minh họa theo ngữ cảnh; chưa phải file chương trình đầy đủ.
static String readFirstLine(Path path) throws IOException {
    try (BufferedReader reader = Files.newBufferedReader(path)) {
        return reader.readLine();
    }
}
~~~

Nếu readLine gây IOException và reader.close cũng gây IOException, lỗi đọc vẫn là primary. Nếu readLine hoàn tất nhưng close lỗi, readFirstLine có thể ném lỗi đóng thay vì trả về dòng đã đọc. Lệnh return trong thân try không bỏ qua quy trình đóng tài nguyên.

Từ Java 9, cú pháp try-with-resources có thể dùng biến tài nguyên **final hoặc effectively final** được khai báo trước khối:

~~~java
// Ví dụ minh họa, reader đã được khởi tạo và không bị gán lại.
try (reader) {
    process(reader);
}
~~~

Cú pháp này không biến một biến bất kỳ thành AutoCloseable; kiểu của reader vẫn phải phù hợp và biến phải thỏa điều kiện final/effectively final. Sau khi rời khối, tài nguyên đã được đóng, dù biến tham chiếu vẫn còn trong phạm vi.

Không nên giả định mọi AutoCloseable đều cho phép gọi close nhiều lần an toàn. Tính idempotent thuộc hợp đồng cụ thể của từng tài nguyên. Hạn chế đóng thủ công cùng resource bên trong thân try-with-resources nếu không có lý do rõ ràng.

## 8. Khi nào cần cleanup thủ công?

Không phải mọi cleanup đều tương đương một lần AutoCloseable.close. Có thể cần rollback transaction, khôi phục thông tin tạm, hủy yêu cầu, thu hồi một lock không bọc được thuận tiện, hoặc thực hiện nhiều thao tác có quy tắc phụ thuộc.

Nếu phải tự cleanup, cần thiết kế rõ ràng:

1. Lưu **primary** ngay tại nơi công việc chính thất bại.
2. Thử cleanup trong đường điều khiển đảm bảo phù hợp, thường là finally.
3. Nếu cleanup lỗi mà **chưa có primary**, truyền cleanup exception theo hợp đồng.
4. Nếu cleanup lỗi mà **đã có primary**, ghi nó vào suppressed (hoặc cơ chế quan sát có cấu trúc khác), đồng thời giữ primary để caller nhận.
5. Không nuốt **Error** hoặc RuntimeException ngoài ý muốn chỉ để tiếp tục chạy như thể thành công.
6. Nếu cleanup lỗi làm trạng thái hạ tầng không xác định, đánh dấu/loại bỏ tài nguyên không còn đáng tin.

Ví dụ helper hạn chế, chỉ minh họa việc gắn lỗi cleanup:

~~~java
// Mã minh họa: gọi khi caller đang quản lý primary Throwable.
static void closePreserving(AutoCloseable resource,
                            Throwable primary) throws Exception {
    if (resource == null) {
        return;
    }
    try {
        resource.close();
    } catch (Exception cleanup) {
        if (primary == null) {
            throw cleanup;
        }
        if (primary != cleanup) {
            primary.addSuppressed(cleanup);
        }
    }
}
~~~

Đoạn này **không phải** một giải pháp thay thế đầy đủ cho try-with-resources. Nó chỉ bắt Exception từ close, không bắt Error; nó không tự chạy phần thân, không tự ném lại primary và không giải quyết mọi nhánh của mã gọi. Nếu primary đã là Error hoặc một RuntimeException, phần gọi bên ngoài phải xử lý việc bảo toàn và ném lại đúng theo hợp đồng. Dùng try-with-resources khi có thể để Java lo các đường thông thường.

### Cleanup gồm nhiều bước

Giả sử phải đóng stream, connection và xóa file tạm. Nếu một bước cleanup đầu tiên ném exception mà mã không bắt, các bước cleanup sau có thể không được thử. Hãy quyết định bước nào độc lập và vẫn nên thử sau lỗi. Nếu cleanup bước sau nguy hiểm khi bước trước thất bại, không được tiếp tục chỉ để thu thêm exception.

Với nhiều tài nguyên AutoCloseable độc lập, một khối try-with-resources là lựa chọn thường tốt nhất: nó thử đóng các tài nguyên đúng thứ tự và tập hợp các exception phụ. Với các bước cleanup nghiệp vụ không có cùng mô hình, cần một chiến lược riêng và test những trường hợp lỗi kép, lỗi ba bước, lỗi giữa quá trình khởi tạo.

## 9. Lưu cause và suppressed khi chuyển exception qua các tầng

Một API có thể chuyển IOException thấp thành exception mức nghiệp vụ. Quan trọng là **không đánh mất original exception**:

~~~java
// Minh họa bên trong một phương thức thích hợp.
try {
    storeRecord();
} catch (java.io.IOException failure) {
    throw new IllegalStateException("Store operation failed", failure);
}
~~~

Constructor nhận cause giữ được cây exception. Nếu IOException có suppressed do resource.close thì những suppressed ấy vẫn nằm trên IOException trong cây cause; caller hoặc logger có thể duyệt cause rồi xem suppressed.

Phản mẫu:

~~~java
// Minh họa phản mẫu.
catch (java.io.IOException failure) {
    throw new IllegalStateException(failure.getMessage());
}
~~~

Cách này không truyền cause; thông tin lớp lỗi, vị trí phát sinh và suppressed của IOException ban đầu bị tách khỏi exception mới. Người đọc log khó xác định thao tác thực sự hỏng ở đâu.

Khi cần thêm ngữ cảnh, ưu tiên wrap bằng constructor có cause. Nếu không cần chuyển đổi abstraction, giữ và ném lại chính original. Không dùng suppressed để biểu diễn một lỗi thực sự là cause; hai quan hệ có ngữ nghĩa khác nhau.

## 10. Ghi log lỗi cleanup mà không che khuất lỗi ban đầu

Mục tiêu ghi log là cho người vận hành thấy được toàn bộ **Throwable tree**: primary, cause, suppressed và stack trace. Chỉ gọi **getMessage()** rồi in một chuỗi thường bỏ sót các phần quan trọng.

Nếu logging framework có API nhận Throwable, thường có thể truyền throwable trực tiếp:

~~~java
// Minh họa với logger hỗ trợ chữ ký error(String, Throwable).
logger.error("Operation failed; cleanup may also have failed", failure);
~~~

Đây là đoạn minh họa phụ thuộc logger cụ thể, không phải Java SE chạy độc lập. Với logger hay pipeline JSON tùy biến, cần kiểm tra formatter thực sự ghi cause/suppressed hay chỉ xuất message. Một số công cụ thu thập log rút gọn stack trace hoặc bỏ danh sách suppressed nếu cấu hình chưa đúng.

Bản stack trace đầy đủ thường có hình thức:

~~~text
java.io.IOException: operation failed
    ... stack trace of original failure ...
    Suppressed: java.io.IOException: close B failed
        ... stack trace ...
    Suppressed: java.io.IOException: close A failed
        ... stack trace ...
~~~

Đây là **định dạng minh họa**, khác với stdout chính xác ở ví dụ mục 4. Địa chỉ dòng mã cụ thể phụ thuộc chương trình và cách log hiển thị.

Một lưu ý khác: hệ thống ghi nhận sự cố cũng có thể thất bại. Nếu một callback ghi log tự ném exception khi đang xử lý primary, cần tránh để lỗi ghi log che lấp thông tin chính. Thiết kế đường báo lỗi nên đơn giản, có quan sát phù hợp và không tự động thay thế Throwable có giá trị chẩn đoán.

## 11. Cleanup liên quan gì tới bảo toàn trạng thái?

Cleanup thành công không có nghĩa dữ liệu chắc chắn đã được rollback:

- **Đóng OutputStream** không tự xóa các byte đã ghi trước đó. Một phần dữ liệu có thể đã được lưu, và lỗi khi đóng có thể liên quan tới flush hoặc hoàn tất ghi.
- **Đóng connection** không mặc nhiên xác nhận mọi thao tác SQL đã rollback theo ý người lập trình. Cần hiểu transaction mode và API đang dùng.
- **Release lock** giải phóng cơ chế đồng bộ nhưng không đảo ngược các biến hoặc collection đã sửa khi giữ lock.
- **Rollback** là thao tác khôi phục ở tầng giao dịch; rollback cũng có thể thất bại và cần được ghi nhận.
- **Hủy request từ xa** có thể không thu hồi tác dụng phụ nếu phía nhận đã xử lý.

Ví dụ một service ghi database bị lỗi, rồi gọi rollback nhưng rollback tiếp tục ném exception. Thông tin lỗi ghi vẫn cần được giữ vì nó giải thích lý do giao dịch đi vào đường thất bại; lỗi rollback cần được đính kèm vì nó cho biết tình trạng khôi phục không chắc chắn. **Việc thêm lỗi rollback vào suppressed không chứng minh rollback đã thành công.** Ứng dụng có thể phải loại bỏ connection, đánh dấu trạng thái chưa xác định, hoặc dùng cơ chế phục hồi được thiết kế riêng.

Ngược lại, **commit thất bại** có thể là lỗi chính của một thao tác nghiệp vụ dù diễn ra gần cuối phương thức. Không đánh đồng mọi thao tác ở cuối với cleanup có thể xem nhẹ. Hợp đồng nghiệp vụ và cơ chế lưu trữ quyết định việc nào thuộc công việc chính, việc nào chỉ là dọn dẹp vòng đời tài nguyên.

Trong các chương trình có side effect bên ngoài, thông tin suppressed giúp chẩn đoán nhưng không thể thay thế transaction, idempotency, hay cơ chế bù trừ (compensation) phù hợp.

## 12. Checked exception, RuntimeException, Error và interrupt

Quy tắc suppressed của try-with-resources áp dụng cho **Throwable**, không chỉ IOException. Nếu phần thân ném RuntimeException và close ném IOException, RuntimeException vẫn có thể là primary, còn IOException đóng tài nguyên được suppressed. Nếu thân ném Error, Java vẫn thực hiện nguyên tắc dọn dẹp và suppression; tuy nhiên ứng dụng không nên lạm dụng **catch(Throwable)** để nuốt lỗi nghiêm trọng.

Khi cleanup thủ công, cần hiểu chính xác loại exception mà code bắt:

- **catch(IOException)** bỏ sót RuntimeException và Error.
- **catch(Exception)** bắt checked exception và RuntimeException nhưng không bắt Error.
- **catch(Throwable)** bắt cả Error; chỉ dùng nếu thực sự cần cho bảo toàn thông tin và phải ném lại hoặc xử lý đúng bản chất, không im lặng phục hồi như thể mọi lỗi đều bình thường.

Một trường hợp đặc biệt là **InterruptedException**. Nếu code bắt và quyết định không ném lại InterruptedException, thường cần cân nhắc **Thread.currentThread().interrupt()** để giữ tín hiệu gián đoạn. Nhưng việc reset interrupt phụ thuộc hợp đồng của API và trạng thái thread; không nên biến thành thao tác máy móc trong mọi catch. Khi một cleanup gặp gián đoạn, phải xét cả lỗi chính và ý nghĩa của việc hủy tác vụ.

Không giả định rằng close chắc chắn không lỗi vì nó nằm ở finally; cũng không mặc định rằng AutoCloseable.close idempotent hoặc mọi lời gọi close an toàn để retry. Quyết định retry cleanup cần dựa trên tài nguyên cụ thể và tác dụng phụ của lần gọi trước.

## 13. Kiểm thử trường hợp cleanup lỗi và lỗi kép

Một test chỉ kiểm tra **assertThrows(IOException.class, ...)** là chưa đủ. Hai exception đều thuộc IOException nhưng exception thứ nhất giải thích thao tác hỏng, còn exception thứ hai chỉ giải thích cleanup. Nếu mã accidentally che primary, test chỉ so loại exception vẫn có thể PASS.

Bộ kiểm thử nên bao phủ ma trận:

| Công việc chính | Cleanup | Exception truyền ra | Suppressed |
| --- | --- | --- | --- |
| Thành công | Thành công | Không có | Không có |
| Thất bại A | Thành công | A | Không có |
| Thành công | Thất bại B | B | Không có |
| Thất bại A | Thất bại B | A | B |
| Thất bại A | B và C đều thất bại | A | B, C theo thứ tự đóng |
| Khởi tạo resource sau lỗi | Đóng resource trước cũng lỗi | Lỗi khởi tạo | Lỗi đóng resource trước |

Với try-with-resources, một kiểm thử mạnh nên:

1. Tạo một instance exception A xác định cho thao tác chính.
2. Tạo một instance exception B xác định cho close.
3. Cho thân try ném đúng object A, cho close ném object B.
4. Bắt từ bên ngoài và xác minh exception nhận được **chính là A** (so sánh tham chiếu, không chỉ message).
5. Xác minh **getSuppressed()** chứa đúng object B, đúng số lượng và đúng thứ tự.
6. Xác minh close được gọi ngay cả khi thân thất bại.
7. Thử thêm đường công việc thành công nhưng close lỗi để bảo đảm B được truyền ra.

Khi có nhiều tài nguyên, kiểm tra thứ tự close ngược với khai báo. Khi một constructor hoặc factory mở resource thất bại ở giữa, kiểm tra các tài nguyên đã mở trước được cleanup. Nếu cleanup có liên quan I/O/database thật, thêm integration test về trạng thái ngoài bộ nhớ; exception tree chính xác không thay cho việc kiểm tra kết quả transaction hay dữ liệu ghi.

Chương trình Java ở mục 4 mô phỏng các nhánh cần thiết bằng tài nguyên tự tạo, nên kết quả deterministic, dễ quan sát và không phụ thuộc file/network thật.

## 14. Những lỗi phổ biến

**Lỗi 1 — finally làm mất exception ban đầu.** Lệnh close trong finally có thể ném exception mới che lỗi phần thân. Dùng try-with-resources hoặc gắn lỗi cleanup bằng addSuppressed khi phải xử lý thủ công.

**Lỗi 2 — nuốt mọi exception cleanup.** Khó biết stream, connection hay rollback đã thực sự hoàn tất chưa. Nếu hợp đồng cho phép bỏ qua một lỗi cụ thể, phải có lý do; tránh bỏ qua mọi Exception.

**Lỗi 3 — chỉ log getMessage().** Thông điệp không chứa đầy đủ cause, stack trace và suppressed. Truyền Throwable vào logger có khả năng hiển thị cây lỗi.

**Lỗi 4 — xem suppressed giống cause.** Cause là quan hệ nguyên nhân giữa các lớp lỗi; suppressed là lỗi phụ được giữ cùng primary. Dùng getSuppressed để kiểm tra lỗi cleanup của try-with-resources.

**Lỗi 5 — nghĩ rằng cleanup thất bại luôn có thể bỏ qua.** Nếu thân thành công nhưng close thất bại, close exception thường là lỗi chính của cả lời gọi.

**Lỗi 6 — chỉ đóng tài nguyên cuối cùng.** Nếu mở nhiều resource rồi khởi tạo một resource sau thất bại, các resource đã mở trước cần được đóng. Try-with-resources giải quyết đường này theo quy tắc của Java.

**Lỗi 7 — helper cleanup chỉ catch một kiểu.** RuntimeException hoặc Error từ thân có thể bị che khi finally ném checked exception. Hiểu đầy đủ những loại Throwable có thể phát sinh.

**Lỗi 8 — tạo exception mới mà không truyền cause.** Khi wrap, luôn giữ exception gốc trong cause nếu nó là nguyên nhân thực sự, để các suppressed đi cùng được truy xuất.

**Lỗi 9 — gọi close hai lần không kiểm tra hợp đồng.** Không phải mọi AutoCloseable đều idempotent. Tránh tự close trong thân rồi tiếp tục để cơ chế tự động close nữa nếu không cần thiết.

**Lỗi 10 — nhầm giữ lỗi với phục hồi trạng thái.** Suppressed có thể cho biết rollback/close hỏng, nhưng không khiến dữ liệu tự trở về trạng thái trước thao tác.

## 15. Bảng kiểm khi thiết kế mã Java

Trước khi hoàn thiện phương thức có cleanup, kiểm tra:

1. **Loại tài nguyên:** có thể sử dụng AutoCloseable và try-with-resources không?
2. **Đường lỗi chính:** những lệnh nào trong công việc chính có thể ném exception?
3. **Đường lỗi cleanup:** close/rollback/release có thể ném checked, unchecked hay Error nào?
4. **Primary:** nếu hai bước đều lỗi, caller còn nhận exception đầu tiên của công việc chính không?
5. **Suppressed:** các lỗi cleanup được gắn vào primary và có thể truy xuất bằng getSuppressed không?
6. **Close-only:** nếu công việc chính thành công nhưng cleanup lỗi, caller có thấy lỗi cleanup theo đúng hợp đồng không?
7. **Nhiều tài nguyên:** thứ tự đóng, trường hợp khởi tạo lỗi giữa chừng và cleanup lỗi tiếp đã được xét chưa?
8. **Logs:** có giữ cause, suppressed và stack trace, đồng thời bảo vệ thông tin nhạy cảm không?
9. **Trạng thái:** nếu cleanup hoặc rollback lỗi, hệ thống có cần đánh dấu tài nguyên không còn đáng tin không?
10. **Tests:** test có kích hoạt lỗi kép, xác minh identity của primary và nội dung suppressed không?

**Ghi nhớ:** Với tài nguyên AutoCloseable, **try-with-resources** là cách chuẩn để cleanup mà vẫn bảo toàn lỗi công việc chính. Nếu cả thân try và close thất bại, lỗi thân là **primary**, lỗi close nằm trong **suppressed**. Khi phải cleanup thủ công, cần chủ động bảo vệ exception ban đầu, giữ thông tin lỗi phụ và xác định rõ trạng thái dữ liệu/tài nguyên sau đường thất bại.

</details>

- [Quay lại đầu trang](#back-to-top)
