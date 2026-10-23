<a id="back-to-top"></a>

# Chọn quan hệ bằng nhau

## Menu
- [Đối tượng giá trị và đối tượng có danh tính nghiệp vụ](#equality-semantics-001)
- [Chọn trường thực sự quyết định giá trị của đối tượng](#equality-semantics-002)
- [Phân biệt dữ liệu cốt lõi với dữ liệu suy ra hoặc dữ liệu ghi nhớ tạm](#equality-semantics-003)
- [Mã định danh chưa được gán và rủi ro thay đổi quan hệ bằng nhau](#equality-semantics-004)
- [Chuẩn hóa dữ liệu trước khi so sánh](#equality-semantics-005)
- [So sánh không phân biệt hoa thường và quy tắc nghiệp vụ tường minh](#equality-semantics-006)
- [Vì sao so sánh số thực bằng một ngưỡng sai số có thể mất tính bắc cầu](#equality-semantics-007)
- [Tách phép so khớp gần đúng khỏi equals() khi không tạo quan hệ tương đương](#equality-semantics-008)

## <a id="equality-semantics-001">Đối tượng giá trị và đối tượng có danh tính nghiệp vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Đối tượng giá trị và đối tượng có danh tính nghiệp vụ: chọn quan hệ bằng nhau trong Java

## 1. Câu hỏi trước khi viết `equals`: “bằng nhau” theo nghĩa nào?

Trong Java, `==` giữa hai biến tham chiếu cho biết chúng có trỏ đến **cùng một đối tượng** hay không. `equals(Object)` thể hiện **quy tắc bằng nhau về mặt logic** do lớp quyết định. Mặc định, `Object.equals` so sánh theo danh tính tham chiếu, nhưng lớp có thể ghi đè để diễn đạt ý nghĩa của miền nghiệp vụ.

Khi thiết kế mô hình, cần phân biệt hai khái niệm:

- **Value Object (đối tượng giá trị):** điều quan trọng là **giá trị và các thuộc tính có ý nghĩa**; hai thể hiện riêng biệt vẫn được coi là bằng nhau nếu các giá trị thành phần cần so sánh bằng nhau. Ví dụ: tọa độ, khoảng tiền đi kèm đơn vị tiền tệ, khoảng ngày, địa chỉ dùng như một thuộc tính đơn thuần.
- **Entity (thực thể có danh tính nghiệp vụ):** điều quan trọng là **đó có phải cùng một thực thể xuyên suốt vòng đời hay không**; hai bản biểu diễn cùng một thực thể vẫn được xem là bằng nhau khi trạng thái thay đổi. Ví dụ: khách hàng được nhận diện bằng `CustomerId`, đơn hàng bằng `OrderId`.

**Cùng thuộc tính không nhất thiết là cùng entity.** Hai khách hàng tên “Lan” không vì thế mà là một khách hàng. Ngược lại, một khách hàng đổi tên vẫn là chính khách hàng đó.

Đây là quyết định mô hình hóa theo ngữ cảnh. Chẳng hạn một địa chỉ giao hàng chỉ dùng để mô tả vị trí có thể là Value Object; nhưng một điểm giao nhận có mã quản lý, lịch sử và vòng đời riêng có thể là Entity.

## 2. So sánh bản chất và các hệ quả

| Khía cạnh | Value Object | Entity có danh tính nghiệp vụ |
| --- | --- | --- |
| Câu hỏi chính | “Chúng có cùng giá trị không?” | “Chúng có phải cùng thực thể không?” |
| Tiêu chí `equals` | Các thành phần giá trị có ý nghĩa | Định danh ổn định trong phạm vi đã xác định |
| Hai instance riêng biệt | Có thể `equals == true` nếu giá trị giống nhau | Có thể `equals == true` nếu cùng định danh |
| Trạng thái thay đổi | Thường tạo Value Object mới, tránh sửa tại chỗ | Cho phép trạng thái thay đổi mà danh tính giữ nguyên |
| Dữ liệu không nên dùng làm khóa bằng nhau | Trường không thuộc ngữ nghĩa giá trị | Trường thường xuyên đổi như tên, địa chỉ liên hệ, trạng thái |
| Ví dụ | `Address("Lê Lợi", "Đà Nẵng")` | `Customer("C001", "Lan")` |

Trong Java, một **`record`** thường phù hợp để biểu diễn Value Object nhỏ vì nó sinh `equals`, `hashCode` và accessor theo các component. Nhưng record không mặc nhiên đúng mọi quy tắc miền nghiệp vụ và chỉ bất biến ở mức các trường tham chiếu (`final`), **không đảm bảo bất biến sâu** nếu bên trong giữ `List`, mảng hoặc đối tượng có thể sửa.

Entity thường cần một lớp có thuộc tính `id` biểu diễn danh tính ổn định, bên cạnh các thuộc tính trạng thái có thể cập nhật. Không phải mọi đối tượng có trường `id` đều là entity; điều quyết định là ý nghĩa tồn tại và vòng đời của đối tượng trong miền nghiệp vụ.

## 3. Ví dụ Java 17+ có thể biên dịch và chạy

Ví dụ dùng `Address` là Value Object: hai bản địa chỉ có cùng đường và thành phố được coi là bằng nhau. `Customer` là Entity: hai bản có cùng `customerId` biểu diễn cùng khách hàng, dù tên hiển thị khác nhau. Ở đây `customerId` được cấp **trước khi tạo đối tượng**, luôn không rỗng và không thay đổi.

Lưu mã sau trong `ValueVsEntityDemo.java`:

```java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ValueVsEntityDemo {
    record Address(String street, String city) {
        Address {
            Objects.requireNonNull(street, "street");
            Objects.requireNonNull(city, "city");
        }
    }

    static final class Customer {
        private final String customerId;
        private String displayName;

        Customer(String customerId, String displayName) {
            if (customerId == null || customerId.isBlank()) {
                throw new IllegalArgumentException("customerId must not be blank");
            }
            this.customerId = customerId;
            this.displayName = Objects.requireNonNull(displayName);
        }

        void rename(String name) {
            this.displayName = Objects.requireNonNull(name);
        }

        String displayName() {
            return displayName;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Customer customer)) return false;
            return customerId.equals(customer.customerId);
        }

        @Override
        public int hashCode() {
            return customerId.hashCode();
        }
    }

    public static void main(String[] args) {
        Address addressA = new Address("Le Loi", "Da Nang");
        Address addressB = new Address("Le Loi", "Da Nang");
        System.out.println("same address reference: " + (addressA == addressB));
        System.out.println("same address value: " + addressA.equals(addressB));

        Customer customerA = new Customer("C001", "Lan");
        Customer customerB = new Customer("C001", "Lan Nguyen");
        Customer customerC = new Customer("C002", "Lan");
        System.out.println("same customer reference: " + (customerA == customerB));
        System.out.println("same customer identity: " + customerA.equals(customerB));
        System.out.println("different customer identity: " + customerA.equals(customerC));
        System.out.println("different display names: "
                + !customerA.displayName().equals(customerB.displayName()));

        Set<Customer> customers = new HashSet<>();
        customers.add(customerA);
        customerA.rename("Lan Tran");
        System.out.println("found after rename: " + customers.contains(customerA));
        System.out.println("found by same ID: " + customers.contains(customerB));
        System.out.println("equals null: " + customerA.equals(null));
    }
}
```

Chạy trong thư mục chứa tệp:

```bash
javac ValueVsEntityDemo.java
java ValueVsEntityDemo
```

Kết quả:

```text
same address reference: false
same address value: true
same customer reference: false
same customer identity: true
different customer identity: false
different display names: true
found after rename: true
found by same ID: true
equals null: false
```

Ở đây `Address` do `record` tạo ra so sánh từng thành phần; hai lời gọi `new` tạo hai đối tượng riêng nhưng `addressA.equals(addressB)` trả `true`. `Customer` dùng `customerId` trong cả `equals` và `hashCode`: hai đối tượng Java khác nhau đại diện cho khách hàng `C001` vẫn bằng nhau, còn tên chỉ là trạng thái. Việc đổi `displayName` không làm thay đổi mã băm, nên `HashSet` vẫn tìm được đối tượng đã thêm trước khi đổi tên.

## 4. Hợp đồng `equals` và `hashCode` vẫn bắt buộc

Bất kể chọn Value Object hay Entity, triển khai `equals` phải thỏa các nguyên tắc:

1. **Phản xạ:** `x.equals(x)` là `true` với `x` khác `null`.
2. **Đối xứng:** `x.equals(y)` và `y.equals(x)` cho cùng kết quả.
3. **Bắc cầu:** nếu `x.equals(y)` và `y.equals(z)` thì `x.equals(z)`.
4. **Nhất quán:** nếu thông tin dùng để so sánh không đổi thì kết quả không đổi giữa các lần gọi.
5. **So với `null`:** với `x` khác `null`, `x.equals(null)` phải là `false`.

Đặc biệt, nếu `a.equals(b)` là `true`, **bắt buộc** `a.hashCode() == b.hashCode()`. Chiều ngược lại không bắt buộc: hai đối tượng không bằng nhau vẫn có thể cùng mã băm. `HashMap` và `HashSet` dựa trên cả `hashCode` lẫn `equals`, nên sửa một phương thức mà bỏ phương thức còn lại sẽ gây hành vi sai.

Với Value Object, các thành phần tham gia `equals` cũng phải tương thích với `hashCode`. Với Entity, ID dùng để so sánh phải ổn định trong thời gian đối tượng nằm trong cấu trúc băm. Ví dụ trên sử dụng `final customerId` và tránh đưa `displayName` vào hai phương thức.

## 5. Các bẫy phổ biến khi quyết định quan hệ bằng nhau

### 5.1. Dùng trạng thái thay đổi được làm danh tính Entity

Giả sử viết `equals` của `Customer` dựa trên `displayName`. Khi đổi tên, thực thể bỗng “không bằng chính bản cũ”; hai khách hàng trùng tên cũng có nguy cơ bị xem là một. Nếu dùng các trường này để tính `hashCode`, thay đổi tên khi đối tượng đang nằm trong `HashSet`/`HashMap` có thể khiến việc tìm hoặc xóa theo đối tượng thất bại.

Không tự động dùng `email`, số điện thoại, tên đăng nhập hay trạng thái để nhận diện Entity: các dữ liệu này có thể đổi, tái sử dụng hoặc không duy nhất tùy miền nghiệp vụ. Chỉ một **khóa nghiệp vụ thật sự ổn định, duy nhất trong phạm vi xác định** mới phù hợp.

### 5.2. Dùng ID sinh khi lưu database mà bỏ qua vòng đời

Ví dụ một Entity mới tạo có `id == null`, sau khi `INSERT` mới nhận ID từ database. Một mẫu cài đặt sơ sài là `Objects.equals(this.id, other.id)`: nếu không xử lý, **hai Entity mới khác nhau cùng có ID `null`** bị coi là bằng nhau. Ngoài ra, nếu `hashCode()` dựa trên ID thì mã băm thay đổi sau khi lưu, dễ làm hỏng thao tác tìm kiếm trong tập hợp băm.

Có các hướng xử lý tùy kiến trúc: cấp ID bất biến ngay khi tạo Entity (ví dụ UUID do ứng dụng cấp); hoặc nếu ID chỉ có sau khi lưu, xác định rõ quy tắc so sánh Entity chưa lưu (thường chỉ cùng tham chiếu mới bằng nhau), đồng thời giữ `hashCode` phù hợp và ổn định theo vòng đời cần hỗ trợ. Không copy nguyên ví dụ `Customer` ở trên cho một Entity JPA có ID được sinh sau `persist` mà chưa xem lại chiến lược.

**ID kỹ thuật không luôn đồng nghĩa danh tính nghiệp vụ.** Nếu dùng ID database làm tiêu chí, phải xác định phạm vi duy nhất (một bảng, tenant, hệ thống) và việc Entity chưa được lưu, tách/gộp hoặc xuất sang hệ thống khác có làm thay đổi ý nghĩa danh tính hay không.

### 5.3. Dùng `==` để so sánh bản biểu diễn thay vì ý nghĩa

Hai đối tượng `new Customer("C001", ...)` được tạo riêng sẽ cho `a == b` là `false`, dù chúng cùng nhận diện khách hàng `C001` và `a.equals(b)` là `true` theo mô hình này. `==` vẫn hữu ích để so sánh tham chiếu hoặc kiểm tra `null`, nhưng không tự thay được phép so sánh logic.

### 5.4. Value Object chứa cấu trúc mutable hoặc so sánh số tiền sai ngữ nghĩa

`record` không bảo vệ nội dung một component kiểu mảng hoặc danh sách có thể sửa. Nếu phần tử đổi sau khi Value Object đã là khóa `HashMap`, giá trị băm có thể thay đổi. Hãy ưu tiên thành phần bất biến; sao chép phòng vệ với đối tượng mutable, và xác định cách so sánh thích hợp (ví dụ `Arrays.equals` khi nội dung mảng mới là ý nghĩa giá trị).

Với giá trị tiền tệ, không chỉ so sánh số mà quên đơn vị tiền. Ngoài ra, `BigDecimal.equals` phân biệt cả `scale`: `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` là `false`, dù `compareTo` trả `0`. Nếu miền nghiệp vụ coi hai lượng tiền đó là một giá trị, phải định nghĩa và chuẩn hóa quy tắc giá trị nhất quán cho **cả `equals` và `hashCode`**, thay vì sửa mỗi `equals` bằng `compareTo`.

### 5.5. Kế thừa, lớp proxy và phạm vi danh tính

Khi có kế thừa, cách so sánh lớp (`getClass()` hay `instanceof`) ảnh hưởng đến tính đối xứng/bắc cầu và ý nghĩa ID. Không để hai kiểu Entity khác nhau trở thành bằng nhau chỉ vì cùng chuỗi `"C001"` nếu chúng thuộc hai loại danh tính khác nhau. Với ORM/JPA, proxy có thể khiến so sánh lớp trực tiếp cần cân nhắc; quy tắc `equals` phải tương thích với công nghệ ánh xạ và vòng đời Entity thực tế. Ví dụ dùng lớp `Customer final` thuần Java nên không có vấn đề proxy.

## 6. Quy trình chọn `equals` khi thiết kế lớp

1. Hỏi: đối tượng đại diện cho **một giá trị** hay **một thực thể có lịch sử và vòng đời riêng**? Không quyết định chỉ bằng việc nó có ID hay có nhiều trường.
2. Nếu là Value Object, chọn các thành phần tạo nên giá trị; ưu tiên bất biến, cân nhắc dùng `record` khi quy tắc so sánh theo component phù hợp.
3. Nếu là Entity, xác định danh tính duy nhất theo miền nghiệp vụ, phạm vi định danh và thời điểm được cấp; bảo đảm thay đổi trạng thái không làm thay đổi danh tính.
4. Triển khai `equals` và `hashCode` cùng một ngữ nghĩa; xét `null`, lớp con/proxy, đối tượng chưa lưu và việc đặt trong `HashMap`/`HashSet`.
5. Kiểm thử hai instance khác nhau nhưng cùng giá trị/ID, hai instance có dữ liệu giống nhau nhưng khác ID, thay đổi trạng thái, các trường hợp `null` và sự ổn định của mã băm.

**Nguyên tắc sử dụng:** Value Object trả lời “giá trị có giống nhau?”; Entity trả lời “có phải cùng thực thể?”. Thiết kế `equals` theo câu hỏi đúng giúp các tập hợp, cache và quy tắc nghiệp vụ không nhầm trạng thái với danh tính.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equality-semantics-002">Chọn trường thực sự quyết định giá trị của đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn trường thực sự quyết định giá trị của đối tượng

## 1. Chọn trường bắt đầu từ định nghĩa “bằng nhau”

Trong Java, `equals(Object)` và `hashCode()` phải phản ánh ý nghĩa của đối tượng trong bài toán, không đơn thuần so sánh mọi trường vì chúng đang có mặt trong lớp. Câu hỏi quan trọng nhất là: **Nếu hai object khác giá trị ở trường X, chúng có còn được coi là cùng một thực thể hoặc cùng một giá trị theo quy tắc nghiệp vụ hay không?**

Ví dụ, hai tài khoản có cùng mã tài khoản nhưng tên hiển thị khác nhau có thể vẫn là cùng tài khoản. Ngược lại, hai đơn hàng có tổng tiền giống hệt nhưng mã đơn khác nhau vẫn là hai đơn hàng. Hai tọa độ `Point(2, 3)` có thể được xem là cùng giá trị dù là hai object được tạo bằng hai lệnh `new` riêng.

Vì vậy, hãy **viết một câu định nghĩa bình đẳng bằng ngôn ngữ nghiệp vụ trước khi viết code**:

- “Hai tài khoản bằng nhau nếu mã tài khoản bất biến giống nhau trong cùng phạm vi định danh.”
- “Hai số tiền bằng nhau nếu đơn vị tiền tệ và số lượng đơn vị nhỏ nhất đều giống nhau.”
- “Hai khóa đặt phòng bằng nhau nếu cùng khách sạn, ngày nhận phòng và mã phòng.”
- “Hai phiên kết nối chỉ là một nếu cùng instance tài nguyên” (trường hợp nên giữ identity mặc định).

Lớp có thể mang nhiều trường nhưng chỉ một tập con thực sự quyết định identity hoặc value. Tập con này phải được sử dụng **nhất quán giữa `equals()` và `hashCode()`**.

## 2. Ba cách nhìn đối tượng

| Mô hình | Khi nào hai object bằng nhau? | Trường nên dùng |
| --- | --- | --- |
| **Entity (thực thể)** | Đại diện cùng thực thể theo thời gian, dù trạng thái có thay đổi | Định danh ổn định, đủ duy nhất trong phạm vi nghiệp vụ |
| **Value object** | Mang cùng giá trị được mô tả bởi các thành phần | Tất cả các thuộc tính thật sự làm nên giá trị |
| **Identity object** | Chính xác là cùng instance | Có thể giữ `Object.equals()` và `Object.hashCode()` |

**Entity:** Với `Account(accountId, displayName, loginCount)`, giả sử domain quy định `accountId` là mã bất biến và duy nhất. Nếu `displayName` đổi từ “An” sang “An Nguyễn”, tài khoản không biến thành tài khoản khác. Nếu `loginCount` tăng từ 4 lên 5, identity cũng không thay đổi. Khi đó chỉ `accountId` được chọn để so sánh.

**Value object:** Với `Money(currency, minorUnits)`, cả hai thành phần quyết định giá trị: `Money("VND", 100_000)` khác `Money("USD", 100_000)`. Chỉ so số lượng tiền làm mất thông tin đơn vị; chỉ so đơn vị cũng không đủ.

**Identity mặc định:** Hai đối tượng đại diện hai session đang mở có thể cùng URL, account hay cấu hình nhưng vẫn là hai tài nguyên runtime riêng. Nếu không có nhu cầu so bình đẳng logic, giữ nguyên `Object.equals()` theo tham chiếu (`==`) là quyết định có cơ sở.

Tên lớp không tự xác định mô hình. Một lớp tên `User` có thể có ID từ khi khởi tạo, hoặc chỉ được cấp ID khi ghi database. Thiết kế equality phải hiểu vòng đời đó.

## 3. Bảng tiêu chí chọn hoặc loại từng trường

Hãy phân loại từng thuộc tính thay vì tự động chọn tất cả:

| Loại trường | Thường có tham gia equality không? | Câu hỏi để quyết định |
| --- | --- | --- |
| ID bất biến, duy nhất | Có với entity | ID có đủ duy nhất trong tenant/phạm vi đang so sánh? |
| Thành phần giá trị cốt lõi | Có với value object | Bỏ nó có làm hai giá trị khác nghĩa bị coi là một? |
| Tên hiển thị hoặc mô tả | Thường không với entity | Đổi tên có đổi identity không? |
| `updatedAt`, `lastLoginAt`, bộ đếm | Thường không với entity | Thay đổi hoạt động có tạo thực thể mới không? |
| Cache hoặc trường tính từ dữ liệu khác | Thường không | Nó có mang thông tin mới hay chỉ là dữ liệu phái sinh? |
| Collection chứa dữ liệu giá trị | Có thể có | Cần so thứ tự, tập phần tử hay snapshot nào? Nội dung có thay đổi không? |
| Khóa database được cấp sau `persist` | Cần chính sách vòng đời | Trước khi có ID, hai entity mới được so thế nào? |
| Trường trong superclass | Tùy quy tắc toàn hierarchy | Có đảm bảo đối xứng, bắc cầu khi so lớp cha và con? |

Để thử một lựa chọn, tự đặt ra hai tình huống phản chứng:

1. **Bỏ sót trường:** Giữ nguyên toàn bộ trường đã chọn, thay trường X bị loại. Có trường hợp nào hai object **phải khác nhau** mà `equals()` lại cho `true` không?
2. **Thừa trường:** Giữ nguyên định danh/các thành phần giá trị, thay trường Y đã chọn. Có trường hợp nào hai object **phải vẫn bằng nhau** mà `equals()` lại cho `false` không?

Ví dụ khóa phòng có thể là `(hotelId, roomNo)`; nếu chỉ chọn `roomNo` thì phòng “101” ở hai khách sạn bị coi là một. Một mã `accountId` chỉ duy nhất trong từng tenant cũng cần thêm `tenantId` vào khóa so sánh. Ngược lại, nếu ID đã duy nhất toàn hệ thống thì các trường mô tả có thể không cần tham gia equality.

## 4. Các hợp đồng của Java phải bảo toàn

`Object.equals()` có các yêu cầu:

1. **Reflexive:** `x.equals(x)` luôn `true` đối với `x` khác null.
2. **Symmetric:** `x.equals(y)` và `y.equals(x)` có cùng kết quả.
3. **Transitive:** nếu `x` bằng `y` và `y` bằng `z` thì `x` bằng `z`.
4. **Consistent:** các lần so sánh cho kết quả ổn định khi các thuộc tính liên quan không đổi.
5. **Non-null:** `x.equals(null)` phải `false`.

`Object.hashCode()` yêu cầu: khi `x.equals(y)` là `true`, **`x.hashCode() == y.hashCode()` cũng phải là `true`**. Chiều ngược lại không bắt buộc: trùng hash không chứng minh hai object bằng nhau. Collision là hiện tượng được phép.

Nếu `equals()` chỉ dùng `id` nhưng `hashCode()` lại dùng `id` và `displayName`, hai object bằng nhau có thể tạo hash khác nhau, vi phạm contract. Hãy tính hash từ cùng tập trường đã chọn; không thêm timestamp, tên hiển thị hay số ngẫu nhiên chỉ để “tránh trùng hash”.

Một chú ý khác: **hash của key không nên thay đổi khi key nằm trong `HashMap` hoặc `HashSet`**. Collection dùng hash để tìm bucket; nếu trường tham gia hash thay đổi sau khi insert, `contains()` hoặc `get()` có thể thất bại ngay với cùng object. `final` giúp ngăn gán lại trường, nhưng `final List<String>` vẫn cho phép thay đổi nội dung list được tham chiếu. Bất biến phải xét cả đồ thị đối tượng có liên quan.

## 5. Ví dụ Java 17+ chạy hoàn chỉnh

Chương trình dưới đây minh họa hai mô hình: entity `Account` chỉ dùng định danh, value object `Money` dùng toàn bộ thành phần giá trị. Tạo tệp `EqualityFieldsDemo.java`:

```java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class EqualityFieldsDemo {

    static final class Account {
        private final String accountId;
        private String displayName;
        private int loginCount;

        Account(String accountId, String displayName) {
            this.accountId = Objects.requireNonNull(accountId);
            if (accountId.isBlank()) {
                throw new IllegalArgumentException("accountId must not be blank");
            }
            this.displayName = Objects.requireNonNull(displayName);
        }

        void rename(String name) {
            displayName = Objects.requireNonNull(name);
        }

        void recordLogin() {
            loginCount++;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Account)) return false;
            Account that = (Account) other;
            return accountId.equals(that.accountId);
        }

        @Override
        public int hashCode() {
            return accountId.hashCode();
        }
    }

    // Cả hai thành phần của record là thành phần giá trị.
    record Money(String currency, long minorUnits) {
        Money {
            Objects.requireNonNull(currency);
            if (currency.isBlank()) {
                throw new IllegalArgumentException("currency must not be blank");
            }
        }
    }

    public static void main(String[] args) {
        Account a = new Account("ACC-01", "An");
        Account b = new Account("ACC-01", "Tên khác");
        Account c = new Account("ACC-02", "An");

        System.out.println(a.equals(b));
        System.out.println(a.equals(c));

        Set<Account> accounts = new HashSet<>();
        accounts.add(a);
        accounts.add(b);
        accounts.add(c);
        System.out.println(accounts.size());

        a.rename("An Nguyễn");
        a.recordLogin();
        System.out.println(accounts.contains(a));
        System.out.println(accounts.contains(b));

        Money m1 = new Money("VND", 100_000);
        Money m2 = new Money("VND", 100_000);
        Money m3 = new Money("USD", 100_000);
        System.out.println(m1.equals(m2));
        System.out.println(m1.equals(m3));
        System.out.println(m1.hashCode() == m2.hashCode());
        System.out.println(a.hashCode() == b.hashCode());
    }
}
```

Biên dịch và chạy:

```bash
javac EqualityFieldsDemo.java
java EqualityFieldsDemo
```

Stdout:

```text
true
false
2
true
true
true
false
true
true
```

Giải thích kết quả theo thứ tự:

- `a` và `b` khác instance và khác tên nhưng cùng `accountId`, nên `equals()` trả về `true`. `a` và `c` khác ID, nên `false`.
- `HashSet` giữ hai phần tử logic: tài khoản `ACC-01` và `ACC-02`. Việc thêm `b` không làm tăng số lượng vì `a` đã đại diện cùng identity.
- Khi đổi tên và tăng bộ đếm của `a`, key identity không đổi nên `accounts.contains(a)` vẫn `true`; một instance khác có cùng ID (`b`) cũng được nhận diện.
- `Money` là `record`. Java tự tạo `equals()` và `hashCode()` từ các record component; hai giá trị cùng mã tiền và số lượng bằng nhau, khác mã tiền thì không.
- Hai object được `equals()` coi là bằng nhau phải có cùng hash. Nhưng hai object có hash bằng nhau chưa chắc bằng nhau.

Trong ví dụ, `Account` là `final` nên không có subclass bổ sung thuộc tính rồi thay chính sách equality. `accountId` là `String` bất biến và không thay đổi sau constructor. `displayName` và `loginCount` mutable nhưng không tham gia equality. Đó là lý do chính sách giữ ổn định với `HashSet`.

**Cần hiểu giới hạn của `record`:** Record tạo equality theo **tất cả record component**, không biết component nào có vai trò nghiệp vụ. Một component là nhãn hiển thị hoặc cache nếu được đặt trong record sẽ ảnh hưởng equality mặc định. Record không tự biến object mutable được tham chiếu trong component thành immutable.

## 6. Bẫy thường gặp khi chọn trường

### 6.1. Chọn mọi trường có sẵn vì IDE đề nghị

Một class entity có 10 trường không có nghĩa tất cả 10 trường quyết định identity. `name`, `status`, `lastModifiedAt` hoặc `computedScore` thường là thông tin thay đổi, không đại diện ID. Nếu đưa chúng vào, cùng entity có thể bị coi là một entity khác sau cập nhật. Công cụ Generate `equals()/hashCode()`, Lombok hoặc IDE chỉ viết code theo danh sách bạn chọn; chúng không tự biết mô hình nghiệp vụ.

### 6.2. ID chưa được cấp khi mới khởi tạo

Nếu database cấp `id` sau khi lưu, hai entity mới có `id == null`. Mẫu sau có thể khiến chúng bị xem là bằng nhau:

```java
// Minh họa lỗi khi id có thể cùng là null.
return Objects.equals(this.id, other.id);
```

Hơn nữa, nếu `hashCode()` dựa trực tiếp vào `id`, hash sẽ thay đổi khi ID được cấp. Cần quy định hành vi trước/sau persist. Một lựa chọn là cấp định danh bất biến khi tạo object (ví dụ UUID nếu hợp lý với hệ thống). Trong hệ thống dùng ID database, có thể xem hai entity chưa có ID chỉ bằng nhau khi cùng tham chiếu, đồng thời thiết kế hash ổn định và tránh đặt entity vào collection hash trước khi vòng đời khóa rõ ràng. Với JPA/Hibernate phải cân nhắc cả proxy, kiểu ID, session và equality của đối tượng detached. **Không có một công thức dùng ID được sinh sau lưu áp dụng an toàn cho mọi hệ thống.**

### 6.3. Dùng trường mutable trong key

Ví dụ `Person(name, address)` và `equals()/hashCode()` dùng `address`, sau đó đổi địa chỉ khi object đã trong `HashSet`. Hash cũ được dùng lúc insert và hash mới lúc tra cứu khác nhau, có thể khiến `contains(person)` là `false`. Nếu address chỉ là trạng thái người dùng chứ không tạo identity, bỏ nó khỏi key. Nếu address thật sự là một phần value object, xây mô hình immutable hoặc dùng key snapshot không thay đổi.

### 6.4. Chọn sai cách so sánh cho kiểu dữ liệu

- **Array:** `Objects.equals(a, b)` với array so sánh bản thân array (thường theo tham chiếu). Dùng `Arrays.equals()` cho nội dung một chiều, `Arrays.deepEquals()` khi cần so array lồng, và tính hash tương ứng bằng `Arrays.hashCode()` hoặc `Arrays.deepHashCode()`.
- **`BigDecimal`:** `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` là `false` do khác scale, dù `compareTo()` cho kết quả `0`. Nếu domain chỉ xét giá trị số, cần quy tắc chuẩn hóa thống nhất cả so sánh và hash; không trộn `compareTo() == 0` với hash thô của `BigDecimal`.
- **`double`/`float`:** dùng ngưỡng `Math.abs(a - b) < epsilon` để làm equality có thể vi phạm tính bắc cầu: A gần B, B gần C nhưng A không gần C. Nếu cần equality theo đơn vị làm tròn, hãy chuẩn hóa thành khóa giá trị theo một quy tắc được xác định trước.
- **Collection:** `List.equals()` có xét thứ tự, `Set.equals()` không dựa vào thứ tự phần tử, `Map.equals()` dựa vào cặp key-value. Nếu nghiệp vụ có ngữ nghĩa khác, phải biểu diễn hoặc chuẩn hóa đúng loại.
- **`null`:** `Objects.equals(x, y)` hữu ích cho trường nullable; nếu null không phải trạng thái hợp lệ, nên từ chối ở constructor thay vì âm thầm cho phép hai object thiếu khóa trở nên bằng nhau.

### 6.5. Dữ liệu chuẩn hóa không nhất quán

Với mã sản phẩm hay tên khóa, có thể cần bỏ khoảng trắng hoặc thống nhất chữ hoa/thường. Nhưng không tùy tiện dùng `toLowerCase()` cho mọi email, tên người, SKU hay mã kỹ thuật: tính phân biệt hoa/thường là quy tắc của domain. Nếu cần chuẩn hóa, làm **trước khi thiết lập khóa equality** và bảo đảm cả hash lẫn equality dựa trên cùng đại diện đã chuẩn hóa. Với chuỗi Unicode, loại bỏ dấu, biến đổi kiểu chữ hoặc normalization phải theo một chính sách cụ thể và được test bằng ca biên.

### 6.6. Kế thừa có thể phá đối xứng

Lớp cha kiểm tra `instanceof Parent` rồi chỉ so trường cha, còn lớp con kiểm tra thêm trường con, có thể khiến `parent.equals(child)` khác `child.equals(parent)`. Kiểm tra `getClass()` chặt hơn nhưng có thể khiến proxy/subclass không được coi là cùng loại, dù domain muốn vậy. Với value object, `final` hoặc `record` thường đơn giản hơn. Với hierarchy mở, phải xây hợp đồng bình đẳng thống nhất cho cả lớp cha và lớp con; gọi `super.equals()` không tự giải quyết mọi xung đột.

## 7. Mẫu triển khai khi có hai trường khóa

Giả sử một lớp `final MyKey` có hai trường `keyPartA` và `keyPartB` tạo thành khóa nghiệp vụ. **Đoạn mã sau chỉ là hai phương thức nằm bên trong lớp đã khai báo trường tương ứng, không phải chương trình độc lập:**

```java
@Override
public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof MyKey)) return false;
    MyKey that = (MyKey) other;
    return Objects.equals(keyPartA, that.keyPartA)
        && Objects.equals(keyPartB, that.keyPartB);
}

@Override
public int hashCode() {
    return Objects.hash(keyPartA, keyPartB);
}
```

`Objects.hash()` tiện lợi khi viết mã nhưng không bắt buộc; với đường đi chạy nóng, cân nhắc chi phí varargs/boxing nếu profiling cho thấy đáng kể. Quan trọng hơn hiệu năng vi mô là **chọn đúng trường**. Nếu các trường không cho phép null, constructor nên kiểm tra và thiết lập invariant rõ ràng. Đối với component là array hoặc collection mutable, cần quy tắc copy và hash tương ứng.

Nếu value object giữ `final List<String> tags`, việc trường là `final` chưa đủ để ngăn `tags.add(...)`. Có thể dùng `List.copyOf(tags)` trong constructor để khóa cấu trúc của list, nhưng đó là bản sao nông: nếu phần tử là object mutable thì vẫn cần chính sách riêng. Cũng đừng đưa trực tiếp collection thay đổi theo thời gian vào key nếu yêu cầu ổn định lâu dài.

## 8. Cách kiểm chứng lựa chọn bằng test

Sau khi chọn trường, nên tạo những cặp object có ý nghĩa nghiệp vụ cụ thể:

| Tình huống | Kỳ vọng |
| --- | --- |
| Cùng một tham chiếu | `equals()` là `true` |
| Khác instance nhưng cùng identity hợp lệ | `equals()` là `true` và hash bằng nhau |
| Cùng tên hiển thị nhưng khác định danh | `equals()` là `false` |
| Đổi trường trạng thái không quyết định identity | `equals()/hashCode()` không đổi |
| Hai value object khác một thành phần cốt lõi | `equals()` là `false` |
| So với `null` hoặc object kiểu không tương thích | `equals()` là `false` |
| Ba object được định nghĩa bằng nhau | Kiểm tra tính đối xứng và bắc cầu |
| Sử dụng làm key trong `HashMap`/`HashSet` | Tra cứu vẫn ổn định khi thay trường không thuộc key |

Một thư viện kiểm tra contract equality có thể phát hiện lỗi đối xứng, bắc cầu, hash mismatch. Tuy vậy, thư viện đó không thể tự biết domain muốn hai đối tượng nào là bằng nhau. Vì thế phải kiểm thử cả **ý nghĩa nghiệp vụ** lẫn **hợp đồng Java**.

## 9. Tự kiểm tra

**1.** `Product` có `productId` bất biến, `name`, `price` và `stockCount` có thể đổi. Nếu cùng ID nghĩa là cùng sản phẩm, nên chọn gì?  
**Đáp án:** `productId` (thêm namespace nếu ID chỉ duy nhất trong phạm vi đó). Các trường còn lại là trạng thái.

**2.** `Dimension(width, height, unit)` là value object; có thể bỏ `unit` khỏi equality không?  
**Đáp án:** Không mặc định. “10 cm” khác “10 m”; chỉ bỏ unit khi đã có chính sách quy đổi/chuẩn hóa nhất quán và giá trị được biểu diễn bằng một đơn vị chuẩn.

**3.** Hai object bằng nhau nhưng hash khác nhau có hợp lệ không?  
**Đáp án:** Không. Đây là vi phạm bắt buộc của `hashCode()`.

**4.** Hai object khác nhau có thể có cùng `hashCode()` không?  
**Đáp án:** Có. Collision không nói hai object bằng nhau.

**5.** Có nên đưa `updatedAt` vào equality của entity?  
**Đáp án:** Chỉ khi miền dữ liệu thật sự coi mỗi bản snapshot/phiên bản là một giá trị khác. Nếu cùng entity qua các lần cập nhật thì không.

**6.** `final List<String> tags` đã là khóa bất biến chưa?  
**Đáp án:** Chưa. Nội dung list vẫn có thể bị sửa. Cần copy phòng vệ hoặc chính sách immutable phù hợp.

**7.** `record` tự sinh equality có luôn khớp nghiệp vụ không?  
**Đáp án:** Không. Record mặc định dùng tất cả component; domain quyết định component nào mang ý nghĩa giá trị.

Điểm thực hành quan trọng nhất: **định nghĩa equality bằng luật của domain trước; chọn những trường thực sự đại diện giá trị/định danh; bảo đảm những trường đó ổn định và được dùng nhất quán bởi `equals()` cùng `hashCode()`.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equality-semantics-003">Phân biệt dữ liệu cốt lõi với dữ liệu suy ra hoặc dữ liệu ghi nhớ tạm</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn dữ liệu cốt lõi, dữ liệu suy ra và dữ liệu ghi nhớ tạm khi thiết kế `equals()` và `hashCode()`

## 1. Vì sao phải phân loại dữ liệu trước khi định nghĩa “bằng nhau”?

Hai đối tượng Java có thể lưu rất nhiều trường, nhưng **không phải mọi trường đều quyết định hai đối tượng có được coi là cùng một giá trị hoặc cùng một thực thể hay không**. Khi override `equals(Object)`, việc đầu tiên là trả lời câu hỏi nghiệp vụ:

> Trong ngữ cảnh này, thay đổi thông tin nào sẽ khiến hai đối tượng **không còn được coi là bằng nhau**?

Chỉ sau khi trả lời, ta mới chọn những trường cần so sánh. Không thể suy ra câu trả lời một cách máy móc từ số lượng trường, từ tên biến `id` hay từ việc trường được khai báo `final`.

Một mô hình thường có ba loại dữ liệu:

1. **Dữ liệu cốt lõi cho quan hệ bằng nhau** (*equality-defining state*): thông tin trực tiếp xác định tiêu chí equality đã chọn. Nếu thông tin này khác nhau, hai đối tượng có thể không còn bằng nhau.
2. **Dữ liệu suy ra** (*derived/computed data*): kết quả có thể tính lại từ dữ liệu khác, chẳng hạn tên hiển thị ghép từ họ và tên, nhãn ghép từ mã sản phẩm và mô tả, hoặc tổng tiền tính từ số lượng và đơn giá. Giá trị suy ra có thể có ý nghĩa với ứng dụng, nhưng thường **không cần so sánh lặp lại** nếu những đầu vào đầy đủ của nó đã được so sánh.
3. **Dữ liệu ghi nhớ tạm** (*cached/memoized data*): kết quả tính toán được lưu lại nhằm tránh làm việc lặp lại, như nhãn đã tạo, kết quả phân tích cú pháp, mã băm đã tính hoặc dữ liệu truy xuất đã tải. Cache phải phản ánh trạng thái gốc; bản thân việc cache đã có hay chưa thường **không làm đổi ý nghĩa equality**.

“Cốt lõi” ở đây có nghĩa là **cốt lõi đối với quan hệ bằng nhau đang thiết kế**, không có nghĩa mọi dữ liệu khác đều vô ích. Một trường có thể quan trọng để hiển thị, tính hóa đơn hoặc kiểm toán mà vẫn không được dùng trong `equals`.

## 2. Quyết định ngữ nghĩa: cùng thực thể hay cùng giá trị?

Không có một danh sách trường `equals` dùng chung cho mọi class. Hai lối thiết kế thường gặp là:

| Quan hệ bằng nhau | Câu hỏi | Trường thường xét | Ví dụ |
| --- | --- | --- | --- |
| **Entity identity** – cùng thực thể | Đây có phải cùng một thực thể trong miền nghiệp vụ không? | Định danh ổn định, duy nhất trong phạm vi phù hợp | Hai đối tượng `Customer` cùng `customerId` được xem là một khách hàng, dù tên hiển thị thay đổi. |
| **Value equality** – cùng giá trị | Hai đối tượng có biểu diễn cùng giá trị đã được định nghĩa không? | Các thành phần quyết định toàn bộ giá trị | Hai `Money` bằng nhau khi cùng số tiền và cùng đơn vị tiền tệ theo quy ước của miền nghiệp vụ. |

Ví dụ, hai bản ghi sản phẩm có cùng SKU nhưng mô tả khác nhau **có thể** được coi là cùng thực thể nếu SKU là định danh ổn định. Ngược lại, nếu lớp đang biểu diễn một *bản chụp mô tả sản phẩm* và yêu cầu tất cả thuộc tính cấu thành bản chụp giống nhau, việc bỏ qua mô tả sẽ không phù hợp.

Điều kiện quyết định là **ngữ nghĩa miền nghiệp vụ**, không phải “mọi class có ID thì chỉ so sánh ID”. Với thực thể chưa được gán ID (ví dụ entity do ORM tạo trước khi lưu), ID có thể còn `null` hoặc thay đổi theo vòng đời; dùng nó trong equality đòi hỏi chính sách rõ ràng để tránh hai thực thể mới bị coi bằng nhau chỉ vì đều có `null`.

### Bốn câu hỏi trước khi chọn trường

1. Người dùng hoặc API coi hai đối tượng là **cùng cái gì**: cùng người dùng, cùng số tiền, cùng bản ghi, hay cùng trạng thái hiện tại?
2. Có trường nào là **định danh ổn định** không? Định danh ấy có được bảo đảm duy nhất trong phạm vi đối tượng đang xét không?
3. Trường nào chỉ là **hàm của trường khác**, hoặc chỉ khác vì chưa tính/chưa lưu cache?
4. Trường nào **có thể thay đổi sau khi đối tượng được dùng làm khóa** trong `HashMap` hoặc phần tử trong `HashSet`?

Nếu chưa xác định được ngữ nghĩa, không nên viết `equals` bằng cách tự động so sánh mọi field được IDE liệt kê.

## 3. Bảng phân loại trường trong một mô hình cụ thể

Giả sử hệ thống có lớp `InventoryItem` và quy tắc nghiệp vụ là: **SKU chuẩn hóa xác định duy nhất một mặt hàng**. Những trường sau có vai trò khác nhau:

| Trường | Ví dụ | Vai trò | Có tham gia equality theo SKU? |
| --- | --- | --- | --- |
| `sku` | `"AB-12"` | Định danh cốt lõi đã chuẩn hóa | **Có** |
| `description` | `"Bút màu xanh"` | Thông tin mô tả có thể sửa | **Không**, theo quy tắc cùng SKU là cùng mặt hàng |
| `displayLabel` | `"AB-12 - Bút màu xanh"` | Dữ liệu suy ra từ SKU và mô tả | **Không** |
| `cachedLabel` | Chuỗi `displayLabel` đã tính trước | Cache của dữ liệu suy ra | **Không** |
| `lastViewedAt` | Thời điểm UI đọc thông tin | Metadata hoạt động hoặc quan sát | **Không** |

Bảng chỉ đúng **với hợp đồng SKU đã nêu**. Nếu một class `ItemSnapshot` lại định nghĩa “bằng nhau khi mọi thuộc tính của snapshot giống nhau”, thì `description` có thể là một thành phần cốt lõi và cần được xét. Điều quan trọng là mô hình hóa chính sách trước khi viết code.

Đặc biệt:

- Dữ liệu suy ra **không tự động là dữ liệu vô nghĩa**: một giá trị tổng hợp có thể là dạng chuẩn duy nhất lưu trong hệ thống, khi đó nó cũng có thể là dữ liệu cốt lõi.
- Dữ liệu lưu cache có thể chứa chính kết quả của một hàm thuộc equality, ví dụ mã băm đã tính. Tuy nhiên **trạng thái “cache còn trống hay đã có kết quả”** không tạo ra một sự khác biệt về giá trị.
- Từ khóa Java `transient` chỉ liên quan đến cơ chế Java serialization mặc định của trường; nó **không tự động loại trường khỏi `equals`**. Ngược lại, một trường không khai báo `transient` cũng chưa chắc phải tham gia equality.

## 4. Ví dụ Java 17: mô tả và cache thay đổi, identity không đổi

Ví dụ sau định nghĩa `InventoryItem` có SKU ổn định. Chuỗi nhãn được tính từ SKU và mô tả, sau đó cache lại. Khi mô tả cập nhật, cache được xóa để tính lại. Vì quan hệ bằng nhau của mô hình này chỉ xét SKU, `equals` và `hashCode` không phụ thuộc vào mô tả hoặc cache.

```java
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class EqualityStateDemo {
    static final class InventoryItem {
        // Dữ liệu cốt lõi của equality: SKU chuẩn hóa, bất biến.
        private final String sku;

        // Dữ liệu mô tả: có ích nhưng không xác định identity theo SKU.
        private String description;

        // Dữ liệu ghi nhớ tạm: có thể bỏ đi và tính lại.
        private String cachedLabel;

        InventoryItem(String rawSku, String description) {
            String normalized = Objects.requireNonNull(rawSku, "sku")
                    .trim()
                    .toUpperCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("SKU must not be blank");
            }
            this.sku = normalized;
            this.description = Objects.requireNonNull(description, "description");
        }

        String displayLabel() {
            if (cachedLabel == null) {
                cachedLabel = sku + " - " + description;
            }
            return cachedLabel;
        }

        void updateDescription(String description) {
            this.description = Objects.requireNonNull(description, "description");
            cachedLabel = null; // Làm mới cache khi đầu vào thay đổi.
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof InventoryItem that)) {
                return false;
            }
            return sku.equals(that.sku);
        }

        @Override
        public int hashCode() {
            return sku.hashCode();
        }
    }

    public static void main(String[] args) {
        InventoryItem first = new InventoryItem(" ab-12 ", "Tên cũ");
        InventoryItem second = new InventoryItem("AB-12", "Tên khác");

        Set<InventoryItem> items = new HashSet<>();
        items.add(first);

        System.out.println(first == second);
        System.out.println(first.equals(second));
        System.out.println(first.hashCode() == second.hashCode());
        System.out.println(items.contains(second));
        System.out.println(first.displayLabel());

        first.updateDescription("Tên mới");
        System.out.println(first.displayLabel());
        System.out.println(items.contains(first));
        System.out.println(items.contains(second));
    }
}
```

Lưu tệp `EqualityStateDemo.java` bằng UTF-8 rồi chạy:

```bash
javac -encoding UTF-8 EqualityStateDemo.java
java EqualityStateDemo
```

Kết quả:

```text
false
true
true
true
AB-12 - Tên cũ
AB-12 - Tên mới
true
true
```

**Giải thích:**

- `first == second` là `false` vì hai lần `new` tạo hai đối tượng khác nhau.
- `first.equals(second)` là `true` vì đầu vào `" ab-12 "` được chuẩn hóa thành `"AB-12"` và cùng SKU với đối tượng thứ hai.
- Hai đối tượng bằng nhau cho cùng `hashCode`, nên `HashSet` có thể tra cứu `second` sau khi thêm `first`.
- Lần gọi `displayLabel()` đầu tiên sinh và lưu `"AB-12 - Tên cũ"`. Sau `updateDescription`, giá trị cache cũ bị hủy, nhãn mới là `"AB-12 - Tên mới"`.
- Kết quả `contains` vẫn đúng vì **không có thay đổi nào ở SKU**, là trường duy nhất xác định equality và hash.

Đây là ví dụ chạy một luồng. Vì `description` và `cachedLabel` thay đổi mà không có đồng bộ hóa, **không được suy ra** rằng cách cache này an toàn nếu dùng chung đối tượng giữa nhiều luồng. Nếu cần truy cập đồng thời, phải thiết kế cơ chế đồng bộ/hiển thị bộ nhớ riêng; yêu cầu đó độc lập với việc chọn trường cho equality.

## 5. Dữ liệu suy ra: so sánh nguồn hay so sánh kết quả?

Giả sử một người dùng có `firstName` và `lastName`, còn `fullName` chỉ là chuỗi ghép từ hai trường. Nếu quan hệ bằng nhau là “cùng cặp tên theo cách lưu dữ liệu hiện tại”, thì so sánh `firstName` và `lastName` đã đủ. So sánh thêm `fullName` là dư thừa, và có thể tạo lỗi nếu một cache chưa được làm mới.

Tuy nhiên, **không phải mọi dữ liệu suy ra đều có thể thay thế cho nguồn**:

- Hai cặp `("An", "Binh")` và `("An Binh", "")` có thể tạo cùng chuỗi hiển thị sau một cách nối, nhưng không nhất thiết đại diện cùng giá trị cặp tên. Nếu equality dựa vào **cặp thành phần**, chỉ so sánh `fullName` là sai về ngữ nghĩa.
- Tổng tiền có thể tính từ đơn giá × số lượng nhưng cũng chịu quy tắc làm tròn, thuế, thời điểm áp dụng tỷ giá. Nếu các đầu vào hoặc chính sách tính toán khác nhau, cùng tổng đã làm tròn chưa chắc đồng nghĩa cùng đối tượng nghiệp vụ.
- Một dữ liệu suy ra phụ thuộc đồng hồ hiện tại, cấu hình hoặc dịch vụ ngoài có thể thay đổi **dù dữ liệu gốc của object không đổi**. Ví dụ, tuổi tính từ ngày sinh thay đổi theo thời gian; không nên dùng “tuổi hôm nay” làm thành phần hash của một khóa lâu dài.

Một quy tắc thực tế: **so sánh tập dữ liệu ổn định, đủ phân biệt theo ngữ nghĩa đã định nghĩa**. Nếu hai thành phần bằng nhau luôn cho cùng kết quả suy ra, không cần dùng thêm kết quả suy ra để chứng minh equality. Nếu kết quả suy ra chứa thông tin hoặc ràng buộc riêng, cần đưa thông tin ấy vào đặc tả của mô hình và cân nhắc trực tiếp.

## 6. Cache là cơ chế hiệu năng, không phải sự thật nghiệp vụ

Cache là bản sao hoặc kết quả trung gian; nó có thể xuất hiện sau lần đọc đầu tiên, bị xóa khi cập nhật, hoặc được dựng lại sau khi deserialization.

### Sai lầm: đưa trạng thái cache vào `equals`

Đoạn dưới đây chỉ minh họa **cách thiết kế sai** bên trong một class có `sku` và `cachedLabel`:

```java
// Sai nếu cachedLabel chỉ là kết quả ghi nhớ tạm.
@Override
public boolean equals(Object other) {
    return other instanceof InventoryItem that
            && Objects.equals(sku, that.sku)
            && Objects.equals(cachedLabel, that.cachedLabel);
}
```

Hai đối tượng cùng SKU có thể khác nhau chỉ vì một bên đã gọi `displayLabel()` còn bên kia chưa gọi. Khi ấy việc **đọc nhãn hiển thị** có thể làm thay đổi kết quả equality, dù không có thay đổi thực chất nào về định danh. Nếu `hashCode` cũng đưa cache vào, mã băm có thể đổi sau lần tính nhãn đầu tiên, khiến việc dùng đối tượng làm hash key trở nên không ổn định.

Để tránh lỗi này, chọn trường gốc cho `equals` và `hashCode`; nếu cần cache chính kết quả `hashCode`, chỉ làm điều đó khi toàn bộ dữ liệu mà hash phụ thuộc vào được bảo đảm không đổi, và xử lý cả vấn đề đồng thời khi có nhiều luồng.

### Khi nào cache có thể là một phần của equality?

Khi một trường tưởng như “cache” thực chất chứa trạng thái được **cam kết là một phần giá trị** và không thể suy ra từ trường còn lại, cần xem lại tên gọi và mô hình. Đó không còn thuần túy là cache theo nghĩa “bỏ đi rồi tính lại mà không mất thông tin”. Phân loại theo **vai trò và ngữ nghĩa**, không theo tên biến.

## 7. Hệ quả của trạng thái thay đổi với `HashSet` và `HashMap`

Hợp đồng của `Object` yêu cầu:

- `equals` phải phản xạ, đối xứng, bắc cầu, nhất quán khi thông tin dùng để so sánh không đổi, và trả `false` với `null`.
- Nếu `a.equals(b)` là `true` thì `a.hashCode() == b.hashCode()` phải đúng.
- Không có yêu cầu hai đối tượng **khác nhau** phải có mã băm khác nhau; hash collision được phép.

Điều này làm nảy sinh vấn đề: nếu thay đổi một trường **tham gia equality** sau khi đối tượng đã nằm trong `HashSet` hoặc được dùng làm khóa `HashMap`, phép tra cứu có thể không còn tìm được phần tử theo cách mong đợi.

Ví dụ minh họa trường hợp nguy hiểm (đoạn code không phải chương trình độc lập):

```java
// Giả sử class MutableKey có equals/hashCode dựa trên code.
MutableKey key = new MutableKey("A");
Set<MutableKey> set = new HashSet<>();
set.add(key);
key.setCode("B"); // Đổi thông tin định vị theo hash của khóa đã lưu.
// set.contains(key) lúc này có thể không còn đúng kỳ vọng.
```

Không nên biến kết quả ví dụ thành khẳng định `contains` *luôn* trả `false`: hành vi quan sát được còn tùy mã băm trước/sau, bucket và triển khai. Vấn đề là **không còn cơ sở để dựa vào hoạt động tra cứu đúng như khi khóa không đổi**.

Đối với `InventoryItem` trong ví dụ chạy ở trên, thay đổi `description` không ảnh hưởng hợp đồng vì trường ấy không tham gia equality. Trái lại, nếu thay `sku` được, rủi ro sẽ xuất hiện. Vì vậy `sku` được khai báo `final` và chuẩn hóa ngay trong constructor.

**Bất biến theo nghĩa của equality không đòi hỏi mọi thuộc tính của object đều bất biến.** Chỉ cần dữ liệu quyết định equality và hash ổn định trong suốt vòng đời cần thiết; trong thực tế, immutable value objects thường đơn giản và an toàn hơn.

## 8. Chú ý với chuẩn hóa, cấu trúc lồng nhau và `record`

### 8.1. Chuẩn hóa đầu vào

Hai chuỗi `"ab-12"` và `"AB-12"` chỉ được coi là cùng SKU nếu quy tắc nghiệp vụ công nhận không phân biệt chữ hoa/thường. Ví dụ đã chọn `trim()` và `toUpperCase(Locale.ROOT)`; đây là **chính sách của mô hình**, không phải quy tắc Java áp cho mọi mã định danh.

Đừng tự động chuẩn hóa với mọi trường: username có thể phân biệt chữ hoa, mã sản phẩm có thể giữ dấu cách có nghĩa, còn các quy tắc Unicode hoặc ngôn ngữ tự nhiên có thể cần xử lý chuyên biệt. Nếu chuẩn hóa quyết định equality, hãy thực hiện ổn định tại ranh giới tạo đối tượng hoặc sử dụng cùng quy tắc nhất quán trong `equals` và `hashCode`.

### 8.2. Đối tượng lồng nhau và mảng

Nếu equality của một value object phụ thuộc vào một danh sách hay mảng, cần quyết định so sánh theo **nội dung**, thứ tự, hay identity của container. Với mảng Java, `Object.equals` kế thừa theo tham chiếu; có thể cần `Arrays.equals`, `Arrays.deepEquals` và hàm hash tương ứng. Nếu lưu một mảng mutable trong field `final`, từ khóa `final` chỉ ngăn gán lại tham chiếu; **phần tử mảng vẫn có thể bị sửa**. Cân nhắc defensive copy hoặc cấu trúc immutable nếu mảng tham gia equality.

Tương tự, nếu `equals` xét các trường bên trong một đối tượng con mutable, việc đối tượng cha có tất cả field `final` vẫn chưa chắc đảm bảo equality ổn định.

### 8.3. Java `record`

Một `record` Java 17 tự sinh `equals`, `hashCode` và `toString` dựa trên các record components. Điều đó thích hợp khi mọi component được chọn đều thuộc giá trị cần so sánh.

```java
record Money(long cents, String currency) {}
```

Đoạn này minh họa khai báo kiểu; có thể đặt ở mức top-level trong tệp Java phù hợp. Với chính sách “số xu và đơn vị tiền tệ cùng nhau xác định giá trị”, hai component đều có ý nghĩa với equality. Tránh thêm `cachedDisplayText` làm component chỉ để lưu cache: nó mặc nhiên sẽ tham gia các phương thức tự sinh, trừ khi định nghĩa lại hành vi theo thiết kế riêng. Việc dùng `record` cũng không tự động khiến các object mutable được truyền vào component trở nên bất biến sâu.

### 8.4. ORM và entity lifecycle

Ở các mô hình entity lưu cơ sở dữ liệu, ID phát sinh sau khi lưu, proxy, cùng entity ở các phiên làm việc khác nhau và trạng thái chuyển tiếp đều có thể ảnh hưởng cách chọn `equals`/`hashCode`. Không áp dụng máy móc ví dụ “dùng ID trong `equals`” cho entity chưa được gán ID hoặc ID có thể thay đổi. Cần xác định rõ thời điểm ID ổn định, ngữ nghĩa khi ID còn `null` và yêu cầu của framework đang dùng.

## 9. Quy trình thực hành để chọn đúng dữ liệu equality

Trước khi viết `equals` và `hashCode`, hãy làm rõ lần lượt:

1. **Phát biểu bằng một câu:** “Hai đối tượng kiểu X bằng nhau khi …”. Câu này phải dùng ngôn ngữ nghiệp vụ, không dựa vào tên trường cụ thể khi chưa cần.
2. **Lập danh sách trường:** đánh dấu trường nào trực tiếp quyết định equality, trường nào được suy ra, trường nào là cache hoặc metadata vận hành.
3. **Kiểm tra tính đủ và tính tối thiểu:** hai trạng thái khác nhau về dữ liệu cốt lõi có khi nào bị xem nhầm là bằng nhau? Có đang so sánh thêm kết quả có thể suy ra hoàn toàn từ trường đã xét không?
4. **Kiểm tra khả năng thay đổi:** dữ liệu tham gia equality có thể biến đổi không? Nếu có, class có được dùng làm key trong hash collection không?
5. **Đảm bảo `hashCode` tương thích:** mọi cặp bằng nhau phải có cùng mã băm; đồng thời chọn phân bố hash phù hợp để hạn chế va chạm không cần thiết.
6. **Đánh giá vùng biên:** `null`, đối tượng khác kiểu, kế thừa, proxy, mảng, giá trị Unicode/chuẩn hóa, trạng thái chưa có ID và concurrency nếu có.
7. **Thử các tình huống đọc và cache:** tạo hai đối tượng cùng core nhưng khác cache; gọi hàm tính dữ liệu suy ra, xóa/tái tạo cache và thay đổi dữ liệu không thuộc equality. Kết quả `equals`/`hashCode` phải giữ theo ngữ nghĩa đã công bố.

### Ma trận kiểm thử cho mô hình dùng SKU làm định danh

| Tình huống | Kỳ vọng |
| --- | --- |
| Hai đối tượng có cùng SKU sau chuẩn hóa, mô tả khác | `equals == true`; hash bằng nhau |
| Hai đối tượng có SKU khác | `equals == false`; hash **có thể** bằng nhau do va chạm |
| Một bên chưa tạo cache nhãn, bên kia đã tạo | Equality không đổi |
| Cập nhật mô tả và làm mới cache | Equality/hash không đổi |
| So sánh với chính đối tượng | `true` |
| So sánh với `null` | `false` |
| So sánh với đối tượng kiểu khác | `false` |

Các kiểm thử phải khẳng định **hành vi contract**, không khẳng định hai hash của mọi SKU khác nhau hay cấu trúc bucket của `HashSet`.

## 10. Tình huống tự luyện

**Tình huống A:** Một `OrderLine` có `productId`, `quantity`, `unitPrice`, `totalPrice = quantity × unitPrice` và `cachedFormattedTotal`. Yêu cầu là so sánh **giá trị của dòng đơn hàng** theo sản phẩm, số lượng và đơn giá. Chọn dữ liệu equality thế nào?

**Gợi ý đáp án:** Xét `productId`, `quantity` và `unitPrice` theo biểu diễn/ngữ nghĩa giá trị đã xác định. `totalPrice` là dữ liệu suy ra từ hai số đã xét nên không cần lặp lại; `cachedFormattedTotal` chỉ là cache hiển thị, không tham gia. Nếu đơn giá hoặc số lượng có thể sửa, nên tránh dùng đối tượng mutable này trực tiếp làm hash key lâu dài hoặc thiết kế immutable snapshot.

**Tình huống B:** Một `Session` có `sessionId`, `lastAccessedAt` và `cachedPermissions`. Hệ thống quy định cùng `sessionId` là cùng phiên. Một lần truy cập cập nhật `lastAccessedAt` và nạp lại quyền. `equals` có nên thay đổi?

**Gợi ý đáp án:** Không, nếu `sessionId` thực sự là định danh bất biến trong phạm vi so sánh. Hai trường còn lại là metadata vận hành/cache theo mô hình này. Dĩ nhiên, việc có cùng session ID không tự xác nhận hai cache quyền đều còn hiệu lực; tính hợp lệ của phiên là một bài toán khác equality.

**Tình huống C:** Một `Point` lưu `x`, `y` và cache `distanceFromOrigin`. Hai điểm được xem bằng nhau khi tọa độ giống nhau. Có nên so sánh cache khoảng cách?

**Gợi ý đáp án:** Không. Hai điểm khác tọa độ có thể cùng khoảng cách đến gốc; còn cache có thể chưa tính. `x` và `y` mới là dữ liệu đủ để quyết định equality. Nếu tọa độ có thể sửa thì phải làm mới cache và cân nhắc rủi ro khi dùng `Point` làm khóa.

## 11. Ghi nhớ

- **Chọn ngữ nghĩa trước, chọn trường sau.** “Dữ liệu cốt lõi” là tập trường quyết định quan hệ bằng nhau, và phụ thuộc vào việc so sánh thực thể hay giá trị.
- **Dữ liệu suy ra** thường không cần so sánh nếu được xác định hoàn toàn từ tập dữ liệu cốt lõi đã xét; tránh dùng riêng biểu diễn bị mất thông tin để thay nguồn.
- **Cache** thường được bỏ qua trong equality vì trạng thái đã tính/chưa tính không thay đổi bản chất object. Cache phải có chính sách invalidation khi nguồn thay đổi.
- **`equals` và `hashCode` phải cùng tuân theo một quan hệ bằng nhau**: bằng nhau ⇒ cùng hash; chiều ngược lại không bắt buộc.
- **Ổn định khi dùng hash collection**: hạn chế sửa dữ liệu tham gia equality/hash sau khi chèn đối tượng vào `HashSet` hoặc làm khóa `HashMap`.
- **Không đánh đồng từ khóa `final`, `transient`, tên `id` hay `record` với quyết định equality**: phải hiểu ý nghĩa và vòng đời thực tế của dữ liệu.

Đối chiếu đặc tả chính thức: [`Object.equals` và `Object.hashCode` (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html), [`HashSet`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html), [`Record`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Record.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equality-semantics-004">Mã định danh chưa được gán và rủi ro thay đổi quan hệ bằng nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Mã định danh chưa được gán và rủi ro thay đổi quan hệ bằng nhau trong Java

## 1. Vấn đề cần giải quyết

Trong một ứng dụng có **entity** (thực thể có danh tính tồn tại qua thời gian), hai object Java khác nhau có thể cùng đại diện cho một thực thể nghiệp vụ; ngược lại, hai object có nội dung trông giống nhau có thể đại diện cho hai thực thể khác nhau. Khi ID của entity được cơ sở dữ liệu cấp sau lúc tạo object, trường `id` ban đầu thường là `null`. Giai đoạn này đặt ra câu hỏi: **thế nào là bằng nhau khi ID chưa có, và điều gì xảy ra khi ID đổi từ `null` sang giá trị thật?**

Ví dụ sau chỉ minh họa trạng thái; lớp `Order` và constructor chưa được định nghĩa tại đây:

```java
Order first = new Order(null, "CUSTOMER-7");
Order second = new Order(null, "CUSTOMER-7");
```

Hai câu lệnh `new` tạo hai tham chiếu đến hai object khác nhau. Nếu đây là hai đơn hàng mới độc lập, chúng **không** nên bị xem là cùng entity chỉ vì ID đều `null` hoặc cùng mã khách hàng. Mặt khác, sau khi lưu, hai object được tải ở những thời điểm khác nhau với cùng ID có thể được coi là bằng nhau về danh tính nghiệp vụ.

Mục tiêu sau bài học:
- Chọn quy tắc `equals` phù hợp khi ID chưa được gán.
- Hiểu vì sao `Objects.equals(null, null)` thường gây lỗi trong mô hình entity có ID cấp sau.
- Nhận diện tác động khi một trường tham gia `equals`/`hashCode` thay đổi trong khi object đang nằm trong `HashSet` hoặc là khóa `HashMap`.
- Phân biệt cách dùng ID cấp sau, ID ứng dụng cấp ngay và khóa nghiệp vụ bất biến.
- Kiểm tra hành vi bằng một chương trình Java 17 độc lập, giải thích chính xác các kết quả.

Bối cảnh là **Java Core và quan hệ bằng nhau**. Với JPA/Hibernate, cách tạo proxy và quản lý entity theo từng phiên bản cần được xét thêm; ví dụ thuần Java ở đây không phải một class ORM hoàn chỉnh.

## 2. Danh tính tham chiếu, danh tính entity và giá trị

| Khái niệm | Câu hỏi | Cơ chế thường dùng |
|---|---|---|
| Object identity | Có đúng là cùng một object Java không? | `a == b` |
| Entity identity | Có đại diện cho cùng một thực thể nghiệp vụ qua các lần tải không? | ID đã xác nhận hoặc khóa nghiệp vụ được thiết kế |
| Value equality | Có cùng nội dung giá trị có ý nghĩa không? | `equals` theo các thành phần bất biến đã chọn |

`Object.equals` mặc định dùng danh tính tham chiếu. Khi ghi đè, ta phải định nghĩa một **quan hệ tương đương** có các tính chất phản xạ, đối xứng, bắc cầu; nhất quán nếu dữ liệu dùng so sánh không đổi; và `x.equals(null) == false`.

`hashCode` phải tuân thủ: nếu `a.equals(b)` thì `a.hashCode() == b.hashCode()`. Chiều đảo không bắt buộc đúng: hai entity khác nhau có thể có cùng hash. Khi một object đang được dùng làm khóa dựa trên hash, các dữ liệu làm thay đổi hash của nó không nên thay đổi trong thời gian lưu trữ.

### Vòng đời thường gặp của ID cấp bởi nơi lưu trữ

| Thời điểm | Object | ID | Ý nghĩa |
|---|---|---|---|
| Vừa tạo | `a` | `null` | Một entity mới; so bằng tham chiếu với chính nó |
| Cùng lúc tạo mới khác | `b` | `null` | Entity mới khác; `a.equals(b)` thường phải `false` |
| Sau khi được cấp ID | `a` | `101` | Danh tính bền vững bắt đầu có thể so theo ID |
| Được tải lại | `c` | `101` | `a != c` nhưng có thể `a.equals(c)` |
| Một entity khác | `d` | `102` | Không bằng entity ID `101` |

Không nên giả định ID được cấp ngay sau một lệnh `persist` hay `save`: thời điểm phụ thuộc chiến lược sinh ID, ORM, giao dịch, flush và lệnh INSERT thực tế.

## 3. Sai lầm thứ nhất: hai ID `null` được coi là bằng nhau

Một cách triển khai thiếu điều kiện:

```java
@Override
public boolean equals(Object other) {
    if (!(other instanceof Order that)) {
        return false;
    }
    return Objects.equals(id, that.id);
}
```

Đoạn này là minh họa riêng, không phải file Java độc lập. `Objects.equals(null, null)` trả `true`. Nếu `a` và `b` là hai entity mới độc lập, cả hai có `id == null`, biểu thức này sẽ khiến `a.equals(b)` trả `true` dù không có cơ sở để khẳng định chúng cùng entity.

Hậu quả khi đưa vào collection:
- `HashSet` có thể loại một object mới vì tưởng nó trùng entity đang có.
- `HashMap.put(b, value)` có thể ghi đè giá trị lưu với khóa `a`.
- Các phép kiểm tra tồn tại/loại trùng có thể làm mất dữ liệu logic dù entity chưa được lưu.

Nếu chọn chính sách **chỉ so theo ID đã gán**, cần kiểm tra ID khác `null`:

```java
@Override
public boolean equals(Object other) {
    if (this == other) {
        return true;
    }
    if (!(other instanceof Order that)) {
        return false;
    }
    return id != null && id.equals(that.id);
}
```

Ở đây `this == other` bảo đảm phản xạ ngay cả khi ID chưa được cấp. Kiểm tra kiểu phù hợp với lớp `final` minh họa. Với cây kế thừa hoặc ORM proxy, phải xác định lại cách so kiểu sao cho đối xứng, không phá identity.

## 4. Sai lầm thứ hai: hash thay đổi khi ID được gán

Nếu `hashCode` trả về `Objects.hashCode(id)`:
- ID `null` → hash bằng `0`.
- ID là `Long.valueOf(100)` → hash bằng `100`.

Đưa object vào `HashSet` khi hash là `0` rồi cập nhật ID khiến hash thành `100` có thể làm việc tìm kiếm thất bại. Collection dùng hash để định hướng bucket; nó không tự chuyển phần tử sang bucket mới khi thuộc tính của object thay đổi.

Một lỗi thường gặp khác:

```java
@Override
public int hashCode() {
    return id.hashCode();
}
```

Nếu ID chưa có thì đoạn trên ném `NullPointerException`. Đổi thành `Objects.hashCode(id)` chỉ tránh NPE, chưa giải quyết được vấn đề hash đổi khi ID xuất hiện.

Cũng không được dùng các trường mutable khác làm hash chỉ để “ổn định tạm thời” nếu chúng có thể sửa về sau, hoặc có thể khác nhau giữa hai object mang cùng ID. Nếu `equals` coi hai entity cùng ID là bằng nhau nhưng hash dựa trên trạng thái khác nhau, hợp đồng `equals`/`hashCode` bị phá.

## 5. Sai lầm thứ ba: quan hệ bằng nhau chuyển trạng thái

Đặc điểm tinh vi hơn là **kết quả `equals` giữa hai object có thể đổi theo vòng đời**. Ví dụ, trước khi ID được cấp, hai object mới `a` và `b` khác tham chiếu không bằng nhau; sau đó nếu cả hai được liên kết với cùng ID thực thể, `a.equals(b)` có thể đổi thành `true`.

Hợp đồng `equals` của Java yêu cầu nhất quán **khi thông tin dùng so sánh không bị sửa**. Do đó thay đổi quan hệ khi ID thay đổi không nhất thiết vi phạm riêng điều kiện nhất quán của `equals`. Tuy nhiên, **collection không tự tính lại các cặp bằng nhau**. Nếu hai object được thêm khi chúng còn khác nhau, rồi sau đó trở thành bằng nhau, `HashSet` có thể vẫn giữ cả hai. Hash ổn định chưa đủ để chữa loại vấn đề này.

Đây là lý do phải xét cả **thời điểm thêm object vào collection** và **liệu equality còn có thể đổi trong thời gian lưu trữ**.

## 6. Ví dụ Java 17: tái hiện lỗi và hai hướng xử lý

Tạo file **`UnassignedIdDemo.java`**. Ba lớp lồng đều `final` nhằm giữ phép so kiểu đơn giản:
- `BadOrder` minh họa lỗi `null/null` và hash thay đổi.
- `GeneratedOrder` minh họa ID cấp sau, `equals` phân biệt entity mới và hash cố định theo lớp.
- `ClientAssignedOrder` minh họa ID bất biến có ngay khi tạo object.

```java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class UnassignedIdDemo {
    public static void main(String[] args) {
        BadOrder bad = new BadOrder();
        BadOrder anotherBad = new BadOrder();

        System.out.println("bad null IDs equal: "
                + bad.equals(anotherBad));

        Set<BadOrder> badSet = new HashSet<>();
        badSet.add(bad);
        System.out.println("bad hash before: " + bad.hashCode());
        System.out.println("bad contains before: "
                + badSet.contains(bad));

        bad.assignId(100L);
        System.out.println("bad hash after: " + bad.hashCode());
        System.out.println("bad contains after: "
                + badSet.contains(bad));

        GeneratedOrder first = new GeneratedOrder();
        GeneratedOrder second = new GeneratedOrder();
        System.out.println("safe null IDs equal: "
                + first.equals(second));

        Set<GeneratedOrder> safeSet = new HashSet<>();
        safeSet.add(first);
        first.assignId(100L);
        System.out.println("safe contains after: "
                + safeSet.contains(first));

        second.assignId(100L);
        System.out.println("safe same persisted ID: "
                + first.equals(second));
        System.out.println("safe lookup by second: "
                + safeSet.contains(second));

        UUID key1 = UUID.fromString(
                "00000000-0000-0000-0000-000000000001");
        UUID key2 = UUID.fromString(
                "00000000-0000-0000-0000-000000000002");

        ClientAssignedOrder assigned1 = new ClientAssignedOrder(key1);
        ClientAssignedOrder sameId = new ClientAssignedOrder(key1);
        ClientAssignedOrder assigned2 = new ClientAssignedOrder(key2);

        System.out.println("assigned same ID: "
                + assigned1.equals(sameId));
        System.out.println("assigned different ID: "
                + assigned1.equals(assigned2));
    }

    // CỐ Ý SAI: null/null bị coi là cùng entity và hash đổi theo ID.
    static final class BadOrder {
        private Long id;

        void assignId(long value) {
            if (value <= 0) {
                throw new IllegalArgumentException("invalid ID");
            }
            if (id != null) {
                throw new IllegalStateException("ID already assigned");
            }
            id = value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof BadOrder that
                    && Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }
    }

    // Entity có ID cấp sau: null không bằng ID của instance khác.
    static final class GeneratedOrder {
        private Long id;

        void assignId(long value) {
            if (value <= 0) {
                throw new IllegalArgumentException("invalid ID");
            }
            if (id != null) {
                throw new IllegalStateException("ID already assigned");
            }
            id = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof GeneratedOrder that)) {
                return false;
            }
            return id != null && id.equals(that.id);
        }

        @Override
        public int hashCode() {
            // Cố định theo lớp thay vì ID mutable.
            return GeneratedOrder.class.hashCode();
        }
    }

    // Entity có ID ứng dụng cấp trước; ID final.
    static final class ClientAssignedOrder {
        private final UUID id;

        ClientAssignedOrder(UUID id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        static ClientAssignedOrder createNew() {
            return new ClientAssignedOrder(UUID.randomUUID());
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof ClientAssignedOrder that
                    && id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }
}
```

Chạy bằng JDK 17 hoặc mới hơn:

```bash
javac --release 17 UnassignedIdDemo.java
java UnassignedIdDemo
```

Output trên triển khai `HashSet`/`HashMap` thông dụng của JDK 17:

```text
bad null IDs equal: true
bad hash before: 0
bad contains before: true
bad hash after: 100
bad contains after: false
safe null IDs equal: false
safe contains after: true
safe same persisted ID: true
safe lookup by second: true
assigned same ID: true
assigned different ID: false
```

**Giải thích theo từng khối:**

1. `BadOrder` sai vì `Objects.equals(null, null)` là `true`. Việc gán ID `100L` làm hash đổi từ `0` sang `100`; với bucket trong bản JDK 17 thông dụng, `HashSet.contains(bad)` sau cập nhật trả `false`. Về mặt hợp đồng, tra cứu sau khi thay đổi hash của phần tử đang lưu **không được bảo đảm**; không nên suy rộng rằng mọi phiên bản hay mọi giá trị ID đều phải in `false`.
2. `GeneratedOrder` không coi hai object mới ID `null` là bằng nhau. Object so với chính nó vẫn bằng nhau nhờ `this == other`. Hash cố định theo lớp nên ID được cấp sau khi object đã nằm trong `HashSet` không làm đổi bucket.
3. `first` và `second` sau khi cùng nhận ID `100` đại diện cùng entity trong tình huống mô phỏng. `equals` trả `true` và hash như nhau, nên có thể tra phần tử trong `safeSet` bằng `second`.
4. `ClientAssignedOrder` dùng ID `UUID` là trường `final`, có nghĩa là hash ổn định. Hai object mang cùng ID được coi là bằng nhau; hai ID xác định khác nhau thì khác nhau. `createNew()` dùng `UUID.randomUUID()` khi tạo entity thực tế nhưng không dùng trong output để chương trình luôn in kết quả xác định.

### Hash cố định có thật sự đúng không?

Với `GeneratedOrder` là lớp `final`, mọi instance trả `GeneratedOrder.class.hashCode()`. Nếu hai entity bằng nhau thì hash chắc chắn bằng nhau, nên hợp đồng hash vẫn được tuân thủ. Tuy nhiên, tất cả instance cùng lớp có cùng hash, gây nhiều collision và có thể làm thao tác collection chậm khi có số lượng lớn. Đây là lựa chọn có đánh đổi, không phải đáp án tối ưu trong mọi bối cảnh.

Giá trị `Class.hashCode()` ở đây được dùng làm hash trong **bộ nhớ JVM**, không phải ID có thể lưu hoặc truyền giữa các tiến trình. Không được dựa vào giá trị hash cụ thể để nhận diện bản ghi.

### Hash cố định chưa giữ được tính duy nhất nếu equality đổi

Giả sử thêm hai object `GeneratedOrder` mới khác nhau vào `HashSet` khi ID đều `null`. Tập sẽ có hai phần tử vì chúng không bằng nhau. Nếu một lỗi đồng bộ khiến cả hai sau đó mang cùng ID, chúng trở thành bằng nhau nhưng tập không tự loại phần tử trùng. Do đó, lựa chọn hash cố định chỉ giải quyết **hash bucket ổn định**; ứng dụng vẫn cần quản lý thời điểm ID xuất hiện và thời điểm object được đưa vào collection.

## 7. Ba chiến lược triển khai thực tế

### A. Cấp ID bất biến ngay khi tạo object

Nếu kiến trúc cho phép, ứng dụng có thể cấp UUID ngay trong constructor/factory. `equals` và `hashCode` cùng dựa trên ID bất biến; vì vậy chúng ổn định ngay cả trước khi entity được lưu. Object tải lại phải giữ **đúng ID đã có**, không sinh ID mới.

Cần tính đến giới hạn duy nhất và thiết kế lưu trữ: UUID ngẫu nhiên có xác suất va chạm rất nhỏ nhưng không có bảo đảm toán học tuyệt đối; ID phải tương thích schema và toàn bộ dịch vụ có liên quan.

### B. ID do nơi lưu trữ cấp sau

Khi ID ban đầu `null`, chính sách thường dùng với lớp Java `final` là:

1. Nếu cùng tham chiếu (`this == other`), trả `true`.
2. Nếu không cùng kiểu entity, trả `false`.
3. Nếu ID chưa có, không coi bằng một object khác.
4. Nếu ID đã có, so ID theo ngữ nghĩa nghiệp vụ.
5. Chọn hash ổn định với vòng đời dự kiến hoặc trì hoãn việc đưa object vào hash collection cho tới khi danh tính ổn định.

Cần cân nhắc chi phí của hash cố định và nguy cơ equality đổi với object khác. Một lựa chọn dễ kiểm soát là chỉ tạo khóa hash từ ID **sau khi ID được xác nhận**.

### C. Dùng khóa nghiệp vụ bất biến, duy nhất

Một cặp dữ liệu như `tenantId + externalOrderId` có thể nhận diện entity từ trước lúc database cấp khóa kỹ thuật, **nếu** miền nghiệp vụ thật sự bảo đảm không đổi và duy nhất trong phạm vi đã định nghĩa. Đừng lấy tên khách hàng, email, ghi chú, trạng thái hoặc một thuộc tính có thể sửa làm khóa chỉ vì nó hiện có vẻ duy nhất.

Nếu nghiệp vụ cần sửa khóa, phải tính lại quy tắc identity và hệ quả với collection; khi đó một khóa kỹ thuật cấp ngay có thể phù hợp hơn.

| Tiêu chí | ID ứng dụng cấp ngay | ID database cấp sau + hash cố định | Khóa nghiệp vụ bất biến |
|---|---|---|---|
| ID/khóa sẵn khi tạo | Có | Chưa | Có nếu nghiệp vụ cung cấp |
| Có thể giữ hash ổn định | Có | Có, với hash không dựa ID | Có nếu trường khóa không đổi |
| Equality giữa hai representation | Theo cùng ID | Theo ID sau khi cấp | Theo khóa nghiệp vụ |
| Rủi ro chính | Phân phối/lưu/tái tạo ID không đúng | Collision và equality đổi theo lifecycle | Uniqueness hoặc tính bất biến không như giả định |

## 8. Riêng với ORM/JPA/Hibernate

Không thể bê nguyên `GeneratedOrder` ở trên vào mọi entity ORM: một số provider dùng proxy/subclass, yêu cầu constructor và quy tắc truy cập nhất định. `getClass()` có thể trả lớp proxy, trong khi `instanceof` có thể dẫn đến so sánh không phù hợp trong cây kế thừa. Quy tắc so kiểu phải được thiết kế nhất quán và thử trên provider cụ thể.

Những tình huống cần kiểm tra:
- Entity mới chưa có ID, hai instance mới khác nhau.
- Entity được cấp ID sau khi insert/flush theo chiến lược cụ thể.
- Hai representation của cùng bản ghi được nạp ở những session khác nhau.
- Detached entity và entity sau merge.
- Proxy và non-proxy của cùng entity, nếu provider dùng proxy.
- Các quan hệ lazy hoặc mutable không nên bị kéo vào equality theo cách gây vòng lặp hay truy cập database ngoài ý muốn.

Không suy từ một ví dụ Java thuần ra rằng `persist` luôn cấp ID tức thì, `final` luôn hợp lệ với proxy ORM, hoặc mọi lớp entity đều dùng một mẫu `hashCode` duy nhất.

## 9. Bài tập kiểm tra quan hệ bằng nhau

### Trường hợp cần dự đoán

| Đầu vào | Kết quả theo chính sách ID cấp sau |
|---|---|
| `a.id == null`, so `a.equals(a)` | `true` |
| `a != b`, cả hai ID `null` | `false` |
| `a.id == 101`, `b.id == 101`, cùng kiểu entity | `true` |
| `a.id == 101`, `b.id == 102` | `false` |
| `a.id == 101`, `b.id == null` | `false` |
| `a.equals(null)` | `false` |
| `a.equals(b)` là `true` | `a.hashCode() == b.hashCode()` bắt buộc đúng |
| Object đã lưu trong `HashSet`, sau đó đổi trường tham gia hash | Lookup không còn đáng tin |
| Hai object ở trong `HashSet`, sau đó equality giữa chúng đổi thành `true` | Collection không tự gộp phần tử |

### Các bài tập thực hành

1. Viết các phép kiểm tra phản xạ, đối xứng, bắc cầu cho ba object mang ID `101` và các object còn `null`. Đừng quên so với `null` và so với object kiểu khác.
2. Đổi `GeneratedOrder.hashCode()` sang `Objects.hashCode(id)` trong bản thử riêng. Chạy lại, quan sát thay đổi; giải thích vì sao chỉ nhìn một lần chạy chưa đủ chứng minh thiết kế an toàn.
3. Dùng một object `BadOrder` làm khóa `HashMap` trước khi cấp ID; thử `get` với chính object đó trước và sau khi gán ID. Giải thích vì sao giá trị có thể không tìm lại được.
4. Thêm hai `GeneratedOrder` đang thiếu ID vào một `HashSet`, rồi trong tình huống giả lập gán cùng ID cho chúng. Kiểm tra kích thước tập trước/sau và giải thích vì sao hash cố định không bảo đảm tập tự làm sạch.
5. Viết một value object `OrderKey` bất biến gồm `tenantId` và `orderId` đã có; so sánh độ ổn định khi dùng `OrderKey` làm khóa `Map` so với dùng entity có ID cấp sau.
6. Nếu ứng dụng dùng ORM, viết integration test cho hai session và proxy thực tế, không chỉ unit test với POJO đơn giản.

Một đoạn kiểm tra ý nghĩa equality (dùng lớp lồng `GeneratedOrder` của `UnassignedIdDemo` trong chính file demo, hoặc sửa tên đủ điều kiện khi chuyển ra file khác):

```java
GeneratedOrder a = new GeneratedOrder();
GeneratedOrder b = new GeneratedOrder();

assert a.equals(a);
assert !a.equals(b);
assert !a.equals(null);

a.assignId(101L);
b.assignId(101L);
assert a.equals(b);
assert b.equals(a);
assert a.hashCode() == b.hashCode();
```

Đây là đoạn minh họa **không chạy độc lập**. Trong Java, các câu lệnh `assert` mặc định bị tắt nếu không bật `-ea`; trong dự án thật nên dùng bộ assertion của JUnit hoặc công cụ kiểm thử tương ứng để kết quả luôn được kiểm tra.

## 10. Checklist trước khi đưa entity vào collection

- Danh tính nghiệp vụ có thật sự do ID quyết định không, hay có khóa bất biến khác?
- ID có thể là `null` tại thời điểm đang tạo entity hoặc tạo khóa collection không?
- Hai object mới độc lập có bị đánh đồng chỉ vì cùng ID `null` không?
- `equals` có xét `this == other` để giữ tính phản xạ cho entity chưa có ID không?
- `hashCode` có phụ thuộc vào ID hoặc bất kỳ trường nào còn có thể thay đổi không?
- Hai object đang nằm trong collection có thể chuyển từ khác nhau thành bằng nhau không?
- Khi nạp lại object, có giữ nguyên identity thay vì cấp một ID mới không?
- Nếu dùng ORM, việc so sánh kiểu thực thể và xử lý proxy có làm hỏng đối xứng/bắc cầu hoặc gây truy vấn ngầm không?
- Có thể dùng một khóa bất biến riêng thay vì trực tiếp dùng entity làm khóa `HashMap` không?

**Điểm cần nhớ:** `null` nghĩa là **chưa được gán ID**, không phải một ID chung cho mọi entity mới. Khi ID xuất hiện, quan hệ bằng nhau và mã băm có thể thay đổi; cần chủ động chọn chính sách identity, thiết kế `equals` và `hashCode` nhất quán, và quản lý thời điểm sử dụng entity trong các collection.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equality-semantics-005">Chuẩn hóa dữ liệu trước khi so sánh</a>

<details>
<summary>Xem chi tiết</summary>

# Chuẩn hóa dữ liệu trước khi so sánh trong Java

## 1. Vì sao cần chuẩn hóa?

Khi xác định **quan hệ bằng nhau** giữa hai đối tượng Java, ta cần trả lời trước: “Hai cách biểu diễn khác nhau này có được xem là cùng một giá trị theo nghiệp vụ không?”. Câu trả lời phụ thuộc vào loại dữ liệu và mục đích so sánh.

Ví dụ:

- `"  Alice "` và `"alice"` có thể là cùng một **tên đăng nhập** nếu hệ thống quy định bỏ khoảng trắng đầu/cuối và không phân biệt chữ hoa/thường; nhưng với một mật khẩu, hai chuỗi đó **không** nên bị tự động xem là như nhau.
- `new BigDecimal("1.0")` và `new BigDecimal("1.00")` có cùng giá trị số, nhưng `BigDecimal.equals()` trả `false` vì khác `scale`.
- Hai cách mã hóa Unicode như `"é"` (ký tự dựng sẵn) và `"e\u0301"` (chữ e cộng dấu kết hợp) có thể trông giống nhau, nhưng `String.equals()` trực tiếp sẽ trả `false` nếu chuỗi mã điểm khác nhau.
- `2026-01-01T00:00:00Z` và `2026-01-01T07:00:00+07:00` biểu diễn cùng một thời điểm tuyệt đối; nếu bài toán quan tâm “cùng thời điểm”, nên so biểu diễn đã chuyển thành `Instant` thay vì so chuỗi gốc.

**Chuẩn hóa (normalization/canonicalization)** là biến đổi biểu diễn đầu vào theo **quy tắc đã chọn**, tạo ra một dạng chuẩn (canonical form) để những biểu diễn được phép xem là tương đương trở thành cùng một giá trị so sánh.

Một mô hình thường gặp:

```text
Dữ liệu gốc A ──chuẩn hóa N──► N(A) ─┐
                                   ├── so sánh theo giá trị ──► bằng/khác
Dữ liệu gốc B ──chuẩn hóa N──► N(B) ─┘
```

Ở đây `N` phải là quy tắc của chính miền dữ liệu. Không có một hàm chuẩn hóa chung áp dụng đúng cho mọi chuỗi, số, tiền tệ, thời gian hay định danh.

## 2. Chọn quan hệ bằng nhau trước khi chọn phép biến đổi

Phải xác định điều gì **có ý nghĩa về mặt nghiệp vụ**:

| Miền dữ liệu | Ví dụ quan hệ bằng nhau | Cần cẩn trọng |
| --- | --- | --- |
| Username cho phép không phân biệt hoa/thường | `" ALICE "` và `"alice"` có thể coi là một, nếu chính sách cho phép bỏ khoảng trắng và không phân biệt hoa/thường | Quy tắc Unicode và ký tự hợp lệ phải được công bố, ổn định. |
| Mật khẩu/bí mật | Chỉ bằng nhau theo cơ chế xác minh mật khẩu đã thiết kế | Không tự `trim()`, hạ chữ hay bỏ dấu làm mất thông tin người dùng. |
| Mã sản phẩm, mã kho | Tùy đặc tả: có thể cần giữ nguyên hoa/thường và số 0 đầu | `"00012"` không mặc nhiên là `"12"`. |
| Số thập phân mang ý nghĩa giá trị số | `1.0` và `1.00` bằng nhau nếu chỉ xét trị số | `BigDecimal.equals()` phân biệt `scale`; trị số và độ chính xác biểu diễn có thể khác ý nghĩa. |
| Số tiền | Cùng giá trị **và** cùng đơn vị tiền tệ | `100 VND` khác `100 USD`; không chỉ so phần số. |
| Thời gian sự kiện | Hai đầu vào cùng `Instant` | Không đánh đồng “cùng giờ địa phương” với “cùng thời điểm”. |
| Định danh đã cấp phát | Thường phải trùng định danh theo hệ thống phát hành | Không tùy tiện bỏ dấu phân cách, số 0 đầu, tiền tố. |

Trong Java, chuẩn hóa **không tự động** xảy ra khi dùng `==` hay `equals()`:

- `==` giữa hai tham chiếu kiểm tra **danh tính đối tượng**, hoàn toàn không chuẩn hóa nội dung.
- `String.equals()` so chuỗi theo đơn vị ký tự UTF-16 một cách phân biệt hoa/thường và không tự chuẩn hóa Unicode.
- `Objects.equals(a, b)` chỉ giúp so an toàn với `null`; khi cả hai không null, nó gọi `a.equals(b)`.
- Lớp tự định nghĩa `equals()` có thể so theo dạng chuẩn nếu đó là một phần hợp đồng bằng nhau của lớp.

Kết luận thiết kế cần rõ ràng: **chuẩn hóa chỉ được áp dụng khi đó là ý nghĩa đã chọn của quan hệ bằng nhau**.

## 3. Một dạng chuẩn tốt cần những tính chất nào?

### 3.1. Nhất quán và xác định

Cùng đầu vào, cùng chính sách phải cho cùng dạng chuẩn. Không nên để `equals()` phụ thuộc vào ngôn ngữ mặc định của máy, thời điểm hiện tại, cấu hình thay đổi ngẫu nhiên hay dữ liệu có thể biến đổi từ bên ngoài.

### 3.2. Có tính ổn định khi áp dụng lại

Thông thường mong muốn tính **idempotent**:

```text
N(N(x)) = N(x)
```

Ví dụ, một tên đăng nhập đã được loại khoảng trắng hai đầu, chuyển chữ thường và chuẩn hóa NFC thì xử lý lại theo cùng quy tắc không nên tiếp tục thay đổi kết quả. Quy tắc phải được kiểm chứng với tập đầu vào mà hệ thống chấp nhận.

### 3.3. Không hợp nhất những giá trị phải phân biệt

Nếu `N(a) == N(b)` thì hệ thống sẽ có xu hướng coi `a` và `b` bằng nhau. Điều đó chỉ đúng khi nghiệp vụ thực sự cho phép. Ví dụ, tự bỏ mọi dấu tiếng Việt có thể đánh đồng những tên hoặc mã khác nhau; tự xóa mọi khoảng trắng có thể biến `"A B"` thành `"AB"` dù đó là hai định danh độc lập.

Đây là thay đổi **ngữ nghĩa**, không phải công việc làm đẹp chuỗi.

### 3.4. Xử lý dữ liệu không hợp lệ và null rõ ràng

Xác định trước liệu đầu vào `null`, chuỗi rỗng hoặc chỉ chứa khoảng trắng có được chấp nhận. Có thể từ chối ngay trong constructor bằng `Objects.requireNonNull` và `IllegalArgumentException`. Nếu đầu vào có thể `null`, cần quy định null là thiếu dữ liệu hay một giá trị đặc biệt; không lặng lẽ chuyển `null` thành `""` nếu hai trạng thái mang ý nghĩa khác nhau.

### 3.5. Bảo toàn hợp đồng `equals` và `hashCode`

Nếu một lớp so sánh theo `canonicalValue` thì phải tính `hashCode()` theo **cùng các thành phần chuẩn hóa**.

```text
a.equals(b) == true  ⇒  a.hashCode() == b.hashCode()
```

Chiều ngược lại không bắt buộc vì mã hash có thể trùng nhau. Đồng thời, các thành phần dùng để so sánh và tạo hash nên **bất biến trong thời gian đối tượng được sử dụng làm khóa** trong `HashMap` hoặc phần tử của `HashSet`.

## 4. Chuẩn hóa chuỗi: phải quyết định từng bước

### 4.1. Bỏ khoảng trắng ở biên

- `String.trim()` chỉ loại các ký tự đầu/cuối có mã không vượt quá U+0020.
- `String.strip()` (Java 11+) xét khoảng trắng theo `Character.isWhitespace`, thường thích hợp hơn cho nhiều loại văn bản Unicode; tuy vậy **không xử lý tất cả ký tự trông giống dấu cách**, ví dụ một số ký tự no-break space.
- Không tự xóa hoặc gộp khoảng trắng **ở giữa** chuỗi nếu nghiệp vụ chưa quy định. `"Van An"` khác `"VanAn"` trong nhiều hệ thống.

### 4.2. Chữ hoa và chữ thường

`toLowerCase(Locale.ROOT)` hữu ích khi quy tắc định danh cần hạ chữ **không phụ thuộc locale mặc định** của môi trường. Dùng `toLowerCase()` không đối số có thể dẫn tới thay đổi theo locale máy chạy, đặc biệt với những trường hợp như chữ I trong tiếng Thổ Nhĩ Kỳ.

Tuy nhiên, hạ chữ bằng `Locale.ROOT` **không phải full Unicode case folding** cho mọi ngôn ngữ; việc so khớp tên người, chuỗi tìm kiếm theo ngôn ngữ tự nhiên hay chuẩn giao thức có thể cần quy tắc riêng (`Collator`, chuẩn Unicode tương ứng hoặc thư viện chuyên dụng).

### 4.3. Unicode normalization

`java.text.Normalizer` hỗ trợ:

- **NFC:** dạng canonical composition; thường hữu ích khi muốn các cách biểu diễn Unicode tương đương về chuẩn tắc so bằng nhau.
- **NFD:** dạng canonical decomposition.
- **NFKC/NFKD:** dạng compatibility normalization, có thể gộp thêm những ký tự chỉ khác về cách biểu diễn tương thích; đây là thay đổi mạnh hơn, có thể làm mất phân biệt có ý nghĩa.

Ví dụ với username cho phép không phân biệt hoa/thường và chấp nhận các biểu diễn Unicode canonical-equivalent:

```java
static String normalizeHandle(String input) {
    String trimmed = input.strip();
    String lowered = trimmed.toLowerCase(java.util.Locale.ROOT);
    return java.text.Normalizer.normalize(
            lowered, java.text.Normalizer.Form.NFC);
}
```

Đoạn trên là **hàm minh họa**, được lồng vào chương trình đầy đủ ở phần 6. Thứ tự các phép biến đổi là một phần chính sách và cần giữ nhất quán ở mọi đường nhập, truy vấn và cập nhật dữ liệu. Chỉ áp dụng khi ứng dụng đã chọn chính sách username này; nó không phải thuật toán chuẩn hóa toàn diện cho email, URL hay tên người.

**Lưu ý bảo mật:** NFC không loại bỏ các ký tự nhìn giống nhau nhưng khác mã Unicode (confusables), và cũng không tự bảo vệ trước mọi trường hợp giả mạo tên. Hệ thống danh tính cần chính sách ký tự được phép và kiểm tra uniqueness phù hợp.

## 5. Chuẩn hóa các kiểu dữ liệu khác

### 5.1. `BigDecimal`: trị số và scale

`BigDecimal` lưu trị số thập phân cùng `scale`:

```java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

// a.equals(b) => false: khác scale
// a.compareTo(b) == 0 => true: cùng trị số
```

Đây là đoạn minh họa cần `import java.math.BigDecimal;`. Nếu quan hệ bằng nhau mong muốn **chỉ so trị số**, có thể:

1. So trực tiếp bằng `a.compareTo(b) == 0` tại nơi cần quan hệ bằng nhau về số.
2. Tạo canonical value bằng `stripTrailingZeros()` rồi dùng `equals()/hashCode()` trên dạng chuẩn trong một value object.

`stripTrailingZeros()` có thể tạo dạng biểu diễn theo lũy thừa của 10, ví dụ từ `600.0` thành dạng có scale âm; đừng dựa vào chuỗi in ra để đánh giá. Với số không, có thể quy về `BigDecimal.ZERO`. Không tự làm tròn trước so sánh nếu quy tắc miền dữ liệu không cho phép; làm tròn có thể gộp những giá trị vốn phải khác nhau.

### 5.2. Số thực `double` và sai số

Không đưa mặc định quy tắc “`Math.abs(a-b) < epsilon`” vào `equals()` của value object: kiểu gần bằng nhau này có thể **không bắc cầu**. Chẳng hạn với `epsilon = 0.1`, `a = 0.00` gần `b = 0.06` và `b` gần `c = 0.12`, nhưng `a` không gần `c`. Điều đó vi phạm hợp đồng `equals`.

Nếu bài toán yêu cầu dung sai, hãy dùng một phương thức mang tên riêng như `approximatelyEqual(a, b, tolerance)` cho thuật toán số; hoặc thiết kế phép lượng tử hóa theo ranh giới cố định nếu **thực sự** muốn quan hệ bằng nhau dạng nhóm. Khi so số thực, cũng cần xác định quy tắc cho `NaN` và `-0.0` vì `Double.equals()` có ngữ nghĩa riêng so với `==` trên primitive.

### 5.3. Ngày và giờ

Nếu yêu cầu là “cùng thời điểm trên dòng thời gian”, chuyển thông tin có offset/zone sang `Instant`:

```java
Instant a = OffsetDateTime.parse(
        "2026-01-01T00:00:00Z").toInstant();
Instant b = OffsetDateTime.parse(
        "2026-01-01T07:00:00+07:00").toInstant();

// a.equals(b) => true
```

Đoạn minh họa trên cần `import java.time.Instant;` và `import java.time.OffsetDateTime;`. Trái lại, lịch hẹn “9 giờ sáng tại Hà Nội” hay một `LocalDate` có thể cần so theo ngày/giờ địa phương, không nhất thiết theo `Instant`. Không tự áp timezone khi chưa hiểu dữ liệu gốc.

### 5.4. Định danh và dữ liệu có định dạng riêng

Với số điện thoại, email, URL, đường dẫn tệp, mã khách hàng hoặc mã quốc tế, các biến đổi “trông hợp lý” có thể tạo sai:

- Email: phần local part và chính sách của nhà cung cấp có những ràng buộc khác nhau; không mặc nhiên xóa dấu chấm hay phần “+tag” cho mọi địa chỉ.
- Số điện thoại: muốn quy về định dạng quốc tế phải biết quốc gia và quy tắc đánh số; xóa hết ký tự không phải chữ số không đủ để xác định số.
- URL/đường dẫn: chữ hoa/thường, dấu slash, percent-encoding, tham số truy vấn hay đường dẫn tương đối có thể thay đổi định danh tài nguyên.
- Mã sản phẩm: dấu gạch, tiền tố, số 0 ở đầu có thể là một phần mã.

Chỉ dùng chuẩn hóa được xác định bởi đặc tả/miền dữ liệu và các thư viện thích hợp.

## 6. Ví dụ Java 17+ chạy độc lập: canonical username và số thập phân

Tạo tệp **`NormalizationEqualityDemo.java`**:

```java
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class NormalizationEqualityDemo {

    static final class UserHandle {
        private final String submitted;
        private final String canonical;

        UserHandle(String submitted) {
            this.submitted = Objects.requireNonNull(
                    submitted, "submitted");
            this.canonical = normalizeHandle(submitted);
            if (canonical.isEmpty()) {
                throw new IllegalArgumentException("Blank handle");
            }
        }

        private static String normalizeHandle(String input) {
            String trimmed = input.strip();
            String lower = trimmed.toLowerCase(Locale.ROOT);
            return Normalizer.normalize(lower, Normalizer.Form.NFC);
        }

        String displayValue() {
            return submitted;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof UserHandle handle)) {
                return false;
            }
            return canonical.equals(handle.canonical);
        }

        @Override
        public int hashCode() {
            return canonical.hashCode();
        }
    }

    static BigDecimal normalizeAmount(BigDecimal input) {
        Objects.requireNonNull(input, "input");
        if (input.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return input.stripTrailingZeros();
    }

    public static void main(String[] args) {
        String decomposed = "  CAFE\u0301  ";
        String composed = "café";

        System.out.println("raw text equal: "
                + decomposed.equals(composed));

        UserHandle first = new UserHandle(decomposed);
        UserHandle second = new UserHandle(composed);
        Set<UserHandle> handles = new HashSet<>();
        handles.add(first);

        System.out.println("same reference: " + (first == second));
        System.out.println("same normalized value: "
                + first.equals(second));
        System.out.println("same hash: "
                + (first.hashCode() == second.hashCode()));
        System.out.println("set contains second: "
                + handles.contains(second));

        BigDecimal a = new BigDecimal("1.0");
        BigDecimal b = new BigDecimal("1.00");
        System.out.println("raw BigDecimal equals: " + a.equals(b));
        System.out.println("numeric compareTo zero: "
                + (a.compareTo(b) == 0));
        System.out.println("canonical BigDecimal equals: "
                + normalizeAmount(a).equals(normalizeAmount(b)));

        System.out.println("null safe equal: "
                + Objects.equals(null, null));
    }
}
```

Biên dịch, chạy bằng JDK 17 trở lên:

```bash
javac NormalizationEqualityDemo.java
java NormalizationEqualityDemo
```

Kết quả chuẩn:

```text
raw text equal: false
same reference: false
same normalized value: true
same hash: true
set contains second: true
raw BigDecimal equals: false
numeric compareTo zero: true
canonical BigDecimal equals: true
null safe equal: true
```

**Vì sao đúng?**

1. `"  CAFE\u0301  "` và `"café"` không giống nhau theo `String.equals()` vì khác khoảng trắng, hoa/thường và cách biểu diễn dấu Unicode.
2. Constructor `UserHandle` chuẩn hóa một lần, lưu `canonical` dưới dạng `final`. Sau `strip()`, hạ chữ với `Locale.ROOT` rồi NFC, cả hai nhận dạng chuẩn `"café"`.
3. Hai lần `new UserHandle(...)` tạo hai đối tượng khác nhau (`==` là `false`), nhưng `equals()` của lớp so `canonical` nên trả `true`.
4. `hashCode()` cũng dựa trên `canonical` nên bằng nhau. Vì vậy `HashSet.contains(second)` trả `true` sau khi thêm `first`.
5. Hai `BigDecimal` cùng trị số nhưng khác scale nên `equals()` trực tiếp trả `false`; `compareTo` và hàm chuẩn hóa `stripTrailingZeros()` thể hiện quy tắc “cùng trị số”.
6. `Objects.equals(null, null)` trả `true`; đây là tiện ích xử lý null chứ không tự chuẩn hóa giá trị.

Trong chương trình, `displayValue()` cho phép giữ dữ liệu người dùng nhập để hiển thị; trong hệ thống thật, có thể lưu cả giá trị gốc và dạng chuẩn cho tìm kiếm/uniqueness. Không nên hiển thị mã chuẩn thay cho bản gốc nếu việc đó làm mất định dạng mà người dùng muốn giữ.

## 7. Nên chuẩn hóa ở đâu trong ứng dụng?

**Tại biên nhập dữ liệu:** đọc, kiểm tra tính hợp lệ và chuyển về dạng chuẩn trước khi tạo value object hay lưu cơ sở dữ liệu. Dạng chuẩn nên bất biến trong đối tượng; `equals()` và `hashCode()` trở nên ngắn gọn, nhanh và nhất quán.

**Tại điểm tra cứu:** chuyển truy vấn bằng **chính cùng quy tắc** đã dùng khi lưu. Nếu lưu username theo dạng NFC + chữ thường nhưng truy vấn theo cách khác, một giá trị hợp lệ có thể không tìm thấy.

**Tại tầng dữ liệu:** đồng bộ quy tắc ứng dụng với unique index, collation hoặc canonical key trong database. `String.equals()` của Java và phép so chuỗi của database có thể có ngữ nghĩa khác; chỉ kiểm tra trùng ở Java mà không có cơ chế nhất quán tại database có thể phát sinh trùng dữ liệu khi có truy cập đồng thời.

**Khi chính sách thay đổi:** phiên bản của phép chuẩn hóa có thể ảnh hưởng dữ liệu cũ, trùng khóa hoặc khả năng đăng nhập. Nếu thay đổi quy tắc Unicode, phân biệt chữ hoa/thường hay cách xử lý khoảng trắng, cần lập kế hoạch chuyển đổi và phát hiện xung đột, thay vì âm thầm thay đổi `equals()`.

### Vì sao không chuẩn hóa lại tùy ý bên trong mỗi lần gọi `equals()`?

Về lý thuyết có thể chuẩn hóa trong `equals()`, nhưng cách đó dễ tốn chi phí, lặp logic và quên đồng bộ `hashCode()`. Nếu phép biến đổi dựa vào locale/config thay đổi theo môi trường hoặc dữ liệu mutable, kết quả bằng nhau và mã hash còn có thể không ổn định. Với đối tượng dùng làm khóa trong collection, nên tính `canonical` một lần theo chính sách cố định trong constructor/nhà máy tạo đối tượng.

## 8. Lỗi thường gặp và cách sửa

| Lỗi | Hậu quả | Cách làm |
| --- | --- | --- |
| Chỉ `trim()/lowercase` một phía | Hai dữ liệu tương đương có thể vẫn khác | Chuẩn hóa nhất quán cả hai đầu vào hoặc lưu dạng chuẩn. |
| `equals()` so canonical, `hashCode()` dùng chuỗi gốc | Phá hợp đồng hash; `HashSet`/`HashMap` hành xử sai kỳ vọng | Cả hai dùng cùng canonical fields. |
| Dùng `toLowerCase()` không tham số | Kết quả có thể đổi theo locale mặc định | Dùng `Locale.ROOT` nếu nghiệp vụ cần so không phụ thuộc locale. |
| Chọn NFKC hay xóa dấu để “so cho dễ” | Vô tình hợp nhất các giá trị cần phân biệt | Chỉ áp dụng phép biến đổi mà đặc tả cho phép; thử các trường hợp biên. |
| Gộp null và chuỗi rỗng | Mất ý nghĩa “chưa cung cấp” khác với “có cung cấp nhưng rỗng” | Định nghĩa rõ chính sách validation/null. |
| So `BigDecimal.equals()` khi nghiệp vụ chỉ xét trị số | `1.0` và `1.00` bị coi khác nhau | Dùng `compareTo` hoặc canonical amount theo chính sách. |
| Dùng epsilon trong `equals()` | Có thể vi phạm tính bắc cầu | Dùng hàm so gần bằng nhau riêng hoặc lượng tử hóa đã thiết kế. |
| Chỉ kiểm tra unique trên ứng dụng | Hai request đồng thời có thể tạo dữ liệu trùng về ngữ nghĩa | Dùng unique constraint/index phù hợp với canonical key. |
| Sửa giá trị canonical sau khi đưa vào `HashMap` | Không tìm lại được khóa trong bucket dự kiến | Thiết kế immutable value object hoặc khóa bất biến. |

## 9. Tự kiểm tra

**Câu 1.** Nếu hệ thống coi username không phân biệt hoa/thường nhưng mật khẩu phân biệt mọi ký tự, có được áp dụng cùng phép `strip().toLowerCase(Locale.ROOT)` cho cả hai không?

**Đáp án:** Không. Quan hệ bằng nhau do miền dữ liệu quyết định; chuẩn hóa mật khẩu theo quy tắc username có thể đổi ý nghĩa và làm mất thông tin.

**Câu 2.** Vì sao hai đối tượng có `canonical` bằng nhau cần `hashCode()` dựa trên canonical?

**Đáp án:** Vì hợp đồng Java bắt buộc hai đối tượng `equals()` bằng nhau phải có cùng `hashCode()`. Hash collection dựa vào điều này để tra cứu chính xác.

**Câu 3.** `new BigDecimal("10.0").equals(new BigDecimal("10.00"))` trả gì? Nếu chỉ quan tâm trị số, cần so thế nào?

**Đáp án:** `false` vì khác `scale`. Có thể dùng `a.compareTo(b) == 0` hoặc so canonical BigDecimal theo quy tắc đã chọn.

**Câu 4.** Chuẩn hóa Unicode NFC có làm mọi cặp ký tự trông giống nhau trở thành bằng nhau không?

**Đáp án:** Không. NFC xử lý tương đương Unicode theo chuẩn tắc, không xử lý mọi ký tự nhìn giống nhau hay mọi yêu cầu so khớp ngôn ngữ.

**Câu 5.** Có thể sử dụng `Math.abs(a - b) < 0.1` làm triển khai chung của `Object.equals()` cho mọi số thực không?

**Đáp án:** Không. Quan hệ gần bằng nhau có thể không bắc cầu, vi phạm hợp đồng `equals()`.

**Ghi nhớ:** **Xác định ngữ nghĩa bằng nhau trước; chuẩn hóa theo đúng ngữ nghĩa ấy; bảo đảm `equals()` và `hashCode()` sử dụng cùng dạng chuẩn bất biến.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equality-semantics-006">So sánh không phân biệt hoa thường và quy tắc nghiệp vụ tường minh</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh không phân biệt hoa thường và quy tắc nghiệp vụ tường minh

## 1. Vấn đề: “bằng nhau” phụ thuộc vào quy tắc nào?

Trong Java, hai chuỗi có cùng chữ nhưng khác chữ hoa/chữ thường có thể được xem là bằng nhau hoặc khác nhau **tùy ngữ cảnh nghiệp vụ**:

- Mã coupon `Save10` và `SAVE10` có thể là **hai mã khác nhau** nếu hệ thống quy định mã phân biệt hoa/thường.
- Tên đăng nhập `Alice_7` và `alice_7` có thể được xem là **cùng một username** nếu hệ thống công bố username không phân biệt hoa/thường.
- Nhãn tìm kiếm `Java` và `JAVA` có thể được khớp không phân biệt hoa/thường, nhưng thao tác “khớp khi tìm kiếm” chưa chắc đã là quy tắc để xác định **danh tính** của đối tượng.
- Tên người, văn bản tự nhiên và từ ngữ đa ngôn ngữ có thể cần quy tắc phụ thuộc ngôn ngữ, dấu, cách viết và mục đích sử dụng.

Vì vậy, câu hỏi đầu tiên không phải “dùng `equalsIgnoreCase` hay `equals`?” mà là **trường dữ liệu này cần quan hệ bằng nhau nào?** Chỉ sau khi xác định quy tắc nghiệp vụ mới chọn API Java, bước chuẩn hóa và cách lưu/truy vấn.

Điểm giới hạn quan trọng: `String.equals(Object)` vẫn phân biệt hoa/thường. Gọi `equalsIgnoreCase` cho một thao tác không làm thay đổi định nghĩa `equals` của `String`; muốn lớp giá trị tự coi hai biểu diễn là bằng nhau, phải thiết kế `equals` và `hashCode` của **lớp đó** một cách nhất quán.

## 2. Những công cụ Java thường gặp

| Công cụ | Điều nó kiểm tra | Phù hợp khi nào |
| --- | --- | --- |
| `left.equals(right)` | Nội dung chuỗi bằng nhau theo `String.equals`, phân biệt hoa/thường. | Mã, khóa, giá trị có quy tắc khớp chính xác. |
| `left.equalsIgnoreCase(right)` | So sánh ký tự không phân biệt hoa/thường theo quy tắc Unicode mà Java áp dụng, **không dựa vào `Locale` được truyền vào**. | So khớp chuỗi không phân biệt hoa/thường khi quy tắc Java này đúng với nghiệp vụ. |
| `left.toLowerCase(Locale.ROOT)` | Tạo một chuỗi đã chuyển về chữ thường theo quy tắc locale trung lập. | Một bước của chiến lược tạo **khóa chuẩn hóa** được thiết kế trước. |
| `String.CASE_INSENSITIVE_ORDER` | `Comparator<String>` sắp thứ tự chuỗi không phân biệt hoa/thường theo quy tắc tương thích với `compareToIgnoreCase`. | Tập/cây sắp xếp có chính sách coi chữ hoa/thường là cùng khóa. |
| `Collator` | So sánh theo quy tắc đối chiếu ngôn ngữ, locale và mức phân biệt (strength). | Sắp xếp hoặc tìm kiếm văn bản theo ngôn ngữ của người dùng. |
| `Normalizer.normalize(text, Form.NFC)` | Chuẩn hóa những biểu diễn Unicode tương đương về một dạng chuẩn. | Khi dữ liệu có thể chứa ký tự Unicode dạng tổ hợp/dạng dựng sẵn. |

Các công cụ trên **không hoàn toàn thay thế được nhau**. Ví dụ, chuyển hai chuỗi sang chữ thường rồi gọi `equals` không luôn trùng với `equalsIgnoreCase` trên mọi ký tự Unicode. `Collator` còn có thể xem hai chuỗi chỉ khác dấu là tương đương ở một mức so sánh, điều mà một chính sách “bỏ qua chữ hoa/thường nhưng giữ dấu” không mong muốn.

### `equals` so với `equalsIgnoreCase`

```java
"Java".equals("JAVA");            // false
"Java".equalsIgnoreCase("JAVA");  // true
"Java".equalsIgnoreCase(null);    // false
```

Đây là **các biểu thức minh họa**, không phải chương trình Java độc lập. Nếu tham chiếu bên trái là `null` thì cả lời gọi `left.equals(...)` lẫn `left.equalsIgnoreCase(...)` đều gây `NullPointerException`; phải quy định rõ xử lý đầu vào `null` và dùng kiểm tra hoặc tiện ích phù hợp.

Không nhầm “không phân biệt hoa/thường” với “không phân biệt dấu”, “bỏ khoảng trắng”, “không phân biệt cách viết Unicode” hoặc “gần giống khi tìm kiếm”. Mỗi yêu cầu là một quy tắc riêng.

## 3. Thiết kế quy tắc nghiệp vụ một cách tường minh

Trước khi lập trình, cần trả lời các câu hỏi sau cho **từng loại dữ liệu**:

1. **Đối tượng so sánh là gì?** Username, coupon, SKU, mã đơn hàng, nhãn hiển thị hay từ khóa tìm kiếm? Hai trường trong cùng một đối tượng có thể có quy tắc khác nhau.
2. **Phân biệt chữ hoa/thường hay không?** Nếu không, dùng so khớp không phân biệt case trực tiếp hay tạo khóa chuẩn hóa để lưu trữ?
3. **Có bỏ khoảng trắng đầu/cuối không?** Nếu có, bỏ theo quy tắc nào? `strip()` và `trim()` không đồng nghĩa trên mọi ký tự; đừng tự loại bỏ khoảng trắng bên trong.
4. **Chấp nhận ký tự nào?** Chỉ ASCII (`A-Z`, `a-z`, số, dấu gạch dưới) hay Unicode nhiều ngôn ngữ? Miền ký tự quyết định độ phức tạp của chuẩn hóa.
5. **Có xét dấu và chuẩn hóa Unicode không?** `é` dựng sẵn và `e` + dấu sắc kết hợp có thể biểu diễn cùng văn bản nhưng là hai dãy mã khác nhau.
6. **Locale có ảnh hưởng không?** Khớp mã máy thường cần quy tắc độc lập với locale; tên hoặc văn bản hiển thị có thể cần locale người dùng.
7. **`null`, chuỗi rỗng và chuỗi chỉ có khoảng trắng có hợp lệ không?** Trả về `false`, coi cả hai `null` là bằng nhau hay từ chối đầu vào đều là lựa chọn nghiệp vụ cần công bố.
8. **Định nghĩa danh tính có ổn định không?** Nếu giá trị quyết định `equals`/`hashCode` được phép thay đổi, đối tượng đang làm khóa `HashMap`/`HashSet` sẽ có rủi ro mất khả năng tra cứu.

Ví dụ một đặc tả username có thể viết rõ:

> “Chấp nhận 3–20 ký tự ASCII, chỉ gồm chữ cái, chữ số và `_`. Bỏ khoảng trắng đầu/cuối trước khi kiểm tra. Username là duy nhất không phân biệt chữ hoa/chữ thường ASCII. Khóa lưu/truy vấn là chữ thường theo `Locale.ROOT`. Không chấp nhận các kiểu chữ Unicode khác hoặc cách viết có dấu.”

Đây chỉ là **ví dụ về chính sách**. Nó cho kết quả dự đoán được vì dữ liệu hợp lệ chỉ nằm trong ASCII; hệ thống hỗ trợ username quốc tế cần đặc tả, kiểm thử và chính sách Unicode riêng.

Chính sách tên đăng nhập không tự áp dụng cho email, tên khách hàng hoặc mã hàng. Với địa chỉ email, phần domain không phân biệt hoa/thường, còn quy tắc xử lý phần local có thể tùy nhà cung cấp và ứng dụng; không được tự động xóa dấu chấm, dấu cộng hoặc đổi toàn bộ địa chỉ thành chữ thường rồi coi đó là danh tính chuẩn cho mọi hệ thống.

## 4. Ví dụ Java 17+ chạy độc lập

Chương trình sau minh họa **ba chính sách tách biệt**:

- `sameTag` cho phép khớp nhãn không phân biệt hoa/thường bằng `equalsIgnoreCase`.
- `sameCouponCode` dùng khớp chính xác, phân biệt hoa/thường.
- `Username` là **lớp giá trị bất biến**; `equals`/`hashCode` cùng dựa trên một khóa username ASCII đã chuẩn hóa. Đây là ví dụ miền giá trị, không mặc định rằng danh tính của mọi tài khoản người dùng phải dựa trên username.

Lưu đúng tệp `CasePolicyDemo.java`:

```java
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class CasePolicyDemo {
    static boolean sameTag(String left, String right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        return left.equalsIgnoreCase(right);
    }

    static boolean sameCouponCode(String left, String right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        return left.equals(right);
    }

    static String canonicalUsername(String raw) {
        Objects.requireNonNull(raw, "raw");

        String cleaned = raw.strip();
        if (!cleaned.matches("[A-Za-z0-9_]{3,20}")) {
            throw new IllegalArgumentException("Invalid username");
        }
        return cleaned.toLowerCase(Locale.ROOT);
    }

    static final class Username {
        private final String key;

        Username(String raw) {
            this.key = canonicalUsername(raw);
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof Username that
                    && key.equals(that.key));
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }

    public static void main(String[] args) {
        System.out.println("tag ignore case: "
                + sameTag("Java", "JAVA"));
        System.out.println("coupon exact: "
                + sameCouponCode("Save10", "SAVE10"));
        System.out.println("canonical username: "
                + canonicalUsername("  Alice_7  "));

        Username a = new Username("Alice_7");
        Username b = new Username("alice_7");
        Username c = new Username("ALICE_7");
        System.out.println("username equal: " + a.equals(b));
        System.out.println("reflexive: " + a.equals(a));
        System.out.println("symmetric: " + (a.equals(b) == b.equals(a)));
        System.out.println("transitive: "
                + (!a.equals(b) || !b.equals(c) || a.equals(c)));
        System.out.println("hash agreement: "
                + (a.hashCode() == b.hashCode()));

        Set<Username> usernames = new HashSet<>();
        usernames.add(a);
        System.out.println("HashSet contains b: " + usernames.contains(b));

        Set<String> exactStrings = new HashSet<>();
        exactStrings.add("Java");
        exactStrings.add("JAVA");

        Set<String> ignoreCaseStrings =
                new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        ignoreCaseStrings.add("Java");
        ignoreCaseStrings.add("JAVA");

        System.out.println("HashSet strings size: " + exactStrings.size());
        System.out.println("TreeSet strings size: " + ignoreCaseStrings.size());
        System.out.println("sharp-s expansion: "
                + "straße".equalsIgnoreCase("STRASSE"));
    }
}
```

Biên dịch và chạy trên JDK 17 trở lên:

```bash
javac -encoding UTF-8 CasePolicyDemo.java
java CasePolicyDemo
```

Đầu ra:

```text
tag ignore case: true
coupon exact: false
canonical username: alice_7
username equal: true
reflexive: true
symmetric: true
transitive: true
hash agreement: true
HashSet contains b: true
HashSet strings size: 2
TreeSet strings size: 1
sharp-s expansion: false
```

**Giải thích:**

- `sameTag("Java", "JAVA")` trả về `true` vì thao tác so nhãn được phép bỏ qua chữ hoa/thường. `sameCouponCode("Save10", "SAVE10")` trả về `false` vì chính sách coupon phân biệt case.
- `canonicalUsername` loại bỏ khoảng trắng ở biên theo chính sách ví dụ, sau đó **kiểm tra miền ASCII trước khi đổi chữ thường**. Vì chỉ cho phép ASCII, thao tác `toLowerCase(Locale.ROOT)` không gặp các tình huống mở rộng Unicode khó kiểm soát.
- `Username` là lớp `final` với trường `key` bất biến. Ba đối tượng `a`, `b`, `c` có cùng khóa chuẩn hóa, nên `equals` phản xạ, đối xứng và bắc cầu trong bộ dữ liệu ví dụ. Kết quả in ra chỉ kiểm tra bộ dữ liệu này; việc đúng hợp đồng còn dựa vào định nghĩa khóa và các phép thử khác.
- `hashCode` tính từ cùng `key` như `equals`. Vì thế `HashSet` có thể nhận ra `b` là cùng giá trị với `a` đã lưu.
- `HashSet<String>` dùng `String.equals`/`hashCode`, nên chứa cả `Java` và `JAVA`. `TreeSet<String>` ở đây dùng comparator không phân biệt case, nên chỉ giữ một phần tử đối với cặp đó. Comparator có quan hệ tương đương **khác với `String.equals`**; đây là quyết định về semantics của tập, cần biết trước khi dùng.
- `"straße"` và `"STRASSE"` có số ký tự khác nhau, nên `equalsIgnoreCase` trả về `false`. Nó không thực hiện mọi phép chuyển đổi nhiều ký tự được dùng trong một số khái niệm Unicode case folding.

## 5. Vì sao chuẩn hóa khóa giúp thiết kế `equals` đúng?

Giả sử chỉ so sánh hai đối tượng `Username` theo chuỗi gốc bằng `String.equals`: `"Alice_7"` và `"alice_7"` khác nhau. Nhưng nghiệp vụ đã xác nhận chúng là cùng username. Vì thế lớp giá trị lưu **khóa đại diện cho quan hệ bằng nhau**, ví dụ `"alice_7"`, và dùng khóa đó nhất quán.

Nếu mọi đầu vào được chấp nhận đi qua **cùng một hàm chuẩn hóa xác định**, ta định nghĩa:

```text
x ~ y  khi và chỉ khi  canonical(x).equals(canonical(y))
```

Đó là phép bằng nhau của các khóa chuỗi chuẩn hóa; nó có phản xạ, đối xứng và bắc cầu. Quy tắc này dễ kiểm chứng hơn một loạt điều kiện “gần giống” được viết vào `equals`. Nhớ rằng quan hệ tương đương chỉ xét **các giá trị hợp lệ trong miền dữ liệu**; đầu vào không hợp lệ nên được xử lý theo hợp đồng constructor/API.

Quy tắc quan trọng nhất khi triển khai value object:

1. **Định nghĩa một hàm chuẩn hóa thống nhất** cho mọi đường tạo dữ liệu, nhập khẩu, API, batch job và tra cứu.
2. **Không tách tiêu chí `equals` và `hashCode`.** Nếu `equals` bỏ qua chữ hoa/thường còn `hashCode` dựa vào chuỗi gốc, hai đối tượng bằng nhau có thể sinh hash khác nhau và vi phạm hợp đồng.
3. **Khóa phải ổn định** khi đối tượng được dùng làm phần tử `HashSet` hoặc khóa `HashMap`. Trường `final` của lớp giá trị là một lựa chọn an toàn.
4. **Định nghĩa rõ miền kiểu**. Ví dụ `Username` chỉ bằng một `Username` khác; nó không so bằng một `String` dù nội dung giống nhau. Việc cho phép `Username.equals("alice_7")` thường phá đối xứng vì `String.equals(Username)` trả về `false`.
5. **Cùng quy tắc ở ứng dụng và nơi lưu trữ**. Nếu ứng dụng chuẩn hóa username nhưng database index chỉ kiểm tra chuỗi gốc, hai yêu cầu tạo username có thể vượt qua kiểm tra rồi gây trùng danh tính. Dùng khóa đã chuẩn hóa, unique constraint và thao tác ghi phù hợp để bảo đảm duy nhất, kể cả có request đồng thời.

Chú ý: một **thực thể tài khoản** có thể cần định danh bằng `userId` ổn định chứ không phải username nếu tên đăng nhập được phép đổi. Lớp `Username` ở ví dụ là *value object đại diện cho tên đăng nhập*, không phải toàn bộ thực thể người dùng. Quy tắc `equals` của entity phải phản ánh danh tính thực tế của entity đó.

## 6. Unicode, locale và những trường hợp dễ hiểu sai

### 6.1. Không phân biệt hoa/thường không phải full Unicode case folding

`String.equalsIgnoreCase` là phép so sánh không phân biệt chữ hoa/thường theo cách Java quy định. Nó **không tự động** chuẩn hóa Unicode, không chạy quy trình full Unicode case folding trên chuỗi và không dùng một locale do người dùng chỉ định.

Ví dụ `"straße".equalsIgnoreCase("STRASSE")` trả về `false` vì độ dài hai chuỗi khác nhau. Không được suy từ ví dụ ASCII `"java"` so với `"JAVA"` rằng mọi cặp cách viết tương đương theo ngôn ngữ tự nhiên đều được Java xem là giống nhau.

Nếu nghiệp vụ hỗ trợ username hoặc khóa Unicode quốc tế, cần có **chính sách canonicalization riêng, phiên bản quy tắc rõ ràng và test với dữ liệu đại diện**. `toLowerCase(Locale.ROOT)` đơn lẻ không tương đương với một thuật toán nhận diện danh tính Unicode được chuẩn hóa đầy đủ.

### 6.2. Các dạng Unicode tổ hợp và dựng sẵn

Ví dụ chữ `é` có thể biểu diễn bằng một code point dựng sẵn hoặc bằng `e` cộng dấu sắc kết hợp. Trong Java, hai chuỗi ấy khác dãy mã và thường không bằng nhau qua `String.equals` hoặc `equalsIgnoreCase`. Khi chính sách cho phép coi chúng là cùng văn bản, có thể chuẩn hóa về **NFC** trước khi so sánh:

```java
import java.text.Normalizer;

String composed = "\u00E9";    // é dựng sẵn
String decomposed = "e\u0301"; // e và dấu sắc kết hợp

boolean before = composed.equals(decomposed); // false
boolean after = Normalizer.normalize(
        composed, Normalizer.Form.NFC
).equals(Normalizer.normalize(
        decomposed, Normalizer.Form.NFC
)); // true
```

Đây là **đoạn minh họa**, không phải một chương trình độc lập. `NFC` bảo toàn phân biệt những văn bản khác nhau ngoài các trường hợp tương đương chuẩn; `NFKC` còn chuẩn hóa một số khác biệt tương thích và có thể hợp nhất những ký tự vốn được hiển thị hay dùng với ý nghĩa khác. Chỉ dùng `NFKC` nếu đặc tả nghiệp vụ cho phép. Chuẩn hóa Unicode cũng không có nghĩa tự động “bỏ dấu tiếng Việt”.

### 6.3. Locale ảnh hưởng đến cách đổi hoa/thường

Java có các phép đổi case phụ thuộc locale: `toLowerCase()` không truyền tham số sử dụng locale mặc định của môi trường, có thể tạo ra kết quả khác khi đổi cấu hình máy. Một ví dụ quen thuộc là quy tắc chữ `I`/`i` trong tiếng Thổ Nhĩ Kỳ.

- Với khóa **máy** độc lập vị trí/ngôn ngữ, hãy xét `Locale.ROOT` khi chính sách dùng chuyển case. Trong ví dụ username ASCII, điều này cho khóa có thể dự đoán được.
- Với **văn bản dành cho con người**, hãy chọn locale của dữ liệu/người dùng và hành vi tìm kiếm được đặc tả. Tránh giả định mọi ngôn ngữ có quy tắc hoa/thường giống tiếng Anh.
- `equalsIgnoreCase` không nhận tham số `Locale`, nên không thể điều chỉnh chính xác cho từng ngôn ngữ chỉ bằng lời gọi này.

### 6.4. Sắp xếp ngôn ngữ với `Collator`

Khi so sánh tên hoặc văn bản hiển thị theo ngôn ngữ, `java.text.Collator` cho phép chọn locale và độ phân biệt. Ví dụ **đoạn mã minh họa**:

```java
import java.text.Collator;
import java.util.Locale;

Collator collator =
        Collator.getInstance(Locale.forLanguageTag("vi-VN"));
collator.setStrength(Collator.SECONDARY);

boolean sameForCollation = collator.compare(left, right) == 0;
```

Trong nhiều bộ quy tắc collation, mức `SECONDARY` bỏ qua khác biệt case nhưng còn phân biệt dấu. Tuy nhiên kết quả chi tiết phụ thuộc locale, dữ liệu ngôn ngữ và nhà cung cấp collation của môi trường. Không thay `equals` của khóa lưu trữ bằng một `Collator` tùy biến mà chưa xác định chính xác hợp đồng, phiên bản và tính ổn định giữa các lần chạy.

`Collator` hữu ích cho hiển thị và tìm kiếm theo ngôn ngữ; việc dùng nó định nghĩa danh tính lâu dài trong database đòi hỏi chính sách và kiểm chứng riêng.

## 7. Tách “so khớp tìm kiếm” khỏi “bằng nhau của đối tượng”

Một hệ thống có thể đồng thời có ba câu hỏi khác nhau:

| Tình huống | Câu hỏi nghiệp vụ | Ví dụ về API/quy tắc |
| --- | --- | --- |
| Tìm nhãn trong giao diện | “`Java` và `JAVA` có được hiển thị như cùng kết quả khớp?” | `equalsIgnoreCase` hoặc tìm kiếm dùng analyzer/collation phù hợp. |
| Xác định mã coupon | “`SAVE10` có phải mã `Save10`?” | `equals` nếu mã phân biệt hoa/thường; lựa chọn này được ghi trong đặc tả. |
| Ngăn trùng tên đăng nhập | “`ALICE_7` có tạo username mới so với `alice_7`?” | Dùng khóa chuẩn hóa bất biến, unique constraint trên khóa, `equals`/`hashCode` tương ứng cho value object. |

Đừng triển khai một phương thức tên chung `equalsIgnoreCaseForEverything` rồi dùng cho mọi trường. Một hàm tên theo ý nghĩa như `canonicalUsername`, `sameCouponCode` hoặc `matchesSearchLabel` làm chính sách rõ ràng tại điểm gọi; khi yêu cầu thay đổi, có thể sửa theo phạm vi nghiệp vụ mà không vô tình đổi ý nghĩa các trường khác.

Đối với dữ liệu lưu lâu dài, quy tắc tạo khóa và version/migration cần được xem như một phần của hợp đồng dữ liệu. Khi đổi chính sách chuẩn hóa, hai khóa trước đây khác nhau có thể trở thành một; cần kiểm tra và giải quyết trùng lặp trước khi chuyển index/constraint hoặc tái tạo khóa.

## 8. Những sai lầm thường gặp

| Sai lầm | Hậu quả | Cách khắc phục |
| --- | --- | --- |
| Gọi `equalsIgnoreCase` cho tất cả chuỗi “vì thân thiện” | Có thể làm hai mã bảo mật, coupon hoặc sản phẩm khác nhau bị gộp sai. | Xác định policy theo từng trường và từng thao tác. |
| Dùng `String.equals` rồi kỳ vọng username không phân biệt case | `Alice_7` và `alice_7` bị coi là hai khóa. | Chuẩn hóa theo policy và so sánh các khóa đã chuẩn hóa. |
| Dùng `toLowerCase()` không truyền locale cho khóa hệ thống | Khóa có thể phụ thuộc locale mặc định của môi trường. | Truyền `Locale.ROOT` khi tương ứng policy; giới hạn tập ký tự hoặc định nghĩa Unicode canonicalization kỹ hơn. |
| Tưởng `equalsIgnoreCase` xử lý mọi phép tương đương Unicode | Không xử lý mọi biểu diễn tổ hợp hoặc phép mở rộng nhiều ký tự. | Đặc tả normalization/case folding và test Unicode nếu dữ liệu cho phép. |
| Chỉ normalize bên lúc đăng ký, quên normalize lúc đăng nhập | Một username có thể được lưu nhưng không tìm lại được. | Dùng một đường chuẩn hóa cho tạo, tra cứu và kiểm tra uniqueness. |
| `equals` dùng khóa chuẩn hóa, `hashCode` dùng chuỗi gốc | Đối tượng bằng nhau có hash khác nhau, làm hỏng logic collection băm. | Cùng dùng một trường key đã chuẩn hóa, bất biến. |
| Dùng `String.CASE_INSENSITIVE_ORDER` mà tưởng `TreeSet` có quan hệ bằng nhau y như `HashSet` | Hai tập khác nhau về số phần tử với `Java`/`JAVA`. | Hiểu `TreeSet` xác định trùng bằng comparator (`compare == 0`); chọn cấu trúc/comparator theo semantics mong muốn. |
| Coi username và tài khoản có cùng khái niệm danh tính | Đổi username có thể làm đổi quan hệ bằng nhau của account. | Phân biệt value object username với entity account định danh bằng ID ổn định. |
| Bỏ qua trùng lặp sau chuẩn hóa trong DB | Có thể tạo hai bản ghi có khóa nghiệp vụ giống nhau, đặc biệt dưới truy cập đồng thời. | Unique constraint theo khóa chuẩn hóa đã chọn và xử lý va chạm rõ ràng. |
| Tự ý bỏ dấu hoặc xóa dấu chấm/dấu cộng trong email | Gộp những địa chỉ không được bảo đảm là cùng mailbox. | Theo policy email được xác minh cho hệ thống; không áp quy tắc nhà cung cấp cụ thể lên mọi email. |

## 9. Bộ kiểm tra nên có khi đưa vào ứng dụng

Với bất kỳ quy tắc so sánh case-insensitive nào, hãy xây dựng test với **giá trị thật của miền nghiệp vụ**:

- **ASCII case:** `Alice_7`, `alice_7`, `ALICE_7` có được coi là một khóa? Kiểm tra cả tạo và tra cứu.
- **Phân biệt quy tắc:** `Save10` và `SAVE10` khác nhau đối với coupon nếu policy quy định chính xác, dù tag `Java` và `JAVA` có thể so khớp.
- **Khoảng trắng:** `" Alice_7 "` có được chuẩn hóa thành `Alice_7`? Khoảng trắng giữa chuỗi hoặc ký tự không hợp lệ có bị từ chối đúng không?
- **`null`/rỗng:** API có từ chối hoặc xử lý theo hợp đồng? Đừng để một đường gọi trả `false` còn đường khác vô tình gây lỗi không mong muốn.
- **Unicode và locale:** Thử dữ liệu có dấu, chuỗi dựng sẵn/tổ hợp, chữ `I` trong locale đặc biệt, chữ `ß`, và xác nhận kết quả phù hợp phạm vi ký tự hỗ trợ.
- **Hợp đồng `equals`:** Với ba value object tương đương, thử phản xạ, đối xứng, bắc cầu; với cặp bằng nhau, bảo đảm `hashCode` giống nhau; với khác kiểu và `null`, bảo đảm hành vi đúng.
- **Tính duy nhất khi lưu trữ:** Hai yêu cầu đồng thời tạo `Alice_7` và `ALICE_7` phải được kiểm soát bằng quy tắc uniqueness ở nơi lưu, không chỉ bằng kiểm tra trước khi insert.
- **Tính ổn định:** Kiểm tra dữ liệu cũ sau khi cập nhật Unicode rules, locale provider, database collation hoặc chính sách chuẩn hóa; nếu có thay đổi, cân nhắc migration.

Có thể viết các phép kiểm tra dạng property-based tests cho rất nhiều bộ ba đầu vào, đặc biệt với Unicode. Tuy nhiên một test trả về `true` với ba chuỗi cụ thể không tự chứng minh chính sách đúng với toàn miền dữ liệu; cơ sở của tính bắc cầu phải đến từ định nghĩa quan hệ bằng nhau nhất quán.

## 10. Ghi nhớ

“So sánh không phân biệt hoa thường” là **một lựa chọn nghiệp vụ có phạm vi**, không phải thuộc tính mặc nhiên của mọi chuỗi. `equalsIgnoreCase` là công cụ tiện cho một số phép khớp; chuẩn hóa khóa cho phép biểu diễn rõ danh tính của value object; `Collator` phục vụ so sánh văn bản theo ngôn ngữ. Hãy công bố miền ký tự, cách xử lý case, khoảng trắng, Unicode, locale và `null`, sau đó dùng cùng policy ở mọi đường tạo/truy vấn dữ liệu. Nếu policy trở thành `equals` của một lớp giá trị, `hashCode` bắt buộc dùng cùng khóa tương đương và khóa đó nên bất biến.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equality-semantics-007">Vì sao so sánh số thực bằng một ngưỡng sai số có thể mất tính bắc cầu</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao so sánh số thực bằng một ngưỡng sai số có thể mất tính bắc cầu?

## 1. Vấn đề: “gần bằng” có thực sự là “bằng nhau” không?

Khi làm việc với số thực trong Java, ta thường gặp sai số biểu diễn và sai số tính toán của kiểu `double`/`float`. Ví dụ, phép cộng `0.1 + 0.2` không nhất thiết cho cùng giá trị `double` với literal `0.3`. Một kỹ thuật phổ biến để kiểm tra hai kết quả số học có đủ gần nhau là dùng **ngưỡng sai số** (tolerance, hay epsilon):

```java
static boolean approximatelyEqual(double x, double y, double epsilon) {
    return Math.abs(x - y) <= epsilon;
}
```

Đây là một **đoạn minh họa**, chưa xử lý `NaN`, vô cực, tham số không hợp lệ hoặc tràn trong phép trừ. Ý nghĩa thông thường: khoảng cách giữa hai số không lớn hơn `epsilon`.

Vấn đề xuất hiện khi ta đem kết quả “gần bằng” này làm định nghĩa cho `equals()`. Theo hợp đồng `Object.equals(Object)`, tính bắc cầu (transitivity) bắt buộc thỏa:

> Nếu `a.equals(b)` đúng và `b.equals(c)` đúng thì `a.equals(c)` cũng phải đúng, với mọi bộ ba đối tượng `a`, `b`, `c`.

Quan hệ `|x - y| <= epsilon` với `epsilon > 0` **không bảo đảm tính bắc cầu**, mặc dù trông giống phép so sánh bằng nhau. Vì thế một hàm “gần bằng” có thể hữu ích cho việc kiểm tra kết quả đo, nhưng không thích hợp để thay thế `equals()`.

## 2. Phản ví dụ cụ thể trên ba số

Chọn `epsilon = 0.125` và ba giá trị:

- `a = 1.0`;
- `b = 1.09375`;
- `c = 1.1875`.

Các giá trị trên cùng `0.125` đều biểu diễn **chính xác** trong số thực nhị phân (IEEE 754 binary floating-point), nên ta không cần dựa vào sai số làm tròn để có phản ví dụ.

| Phép so sánh | Khoảng cách tuyệt đối | Nhỏ hơn hoặc bằng `0.125`? |
| --- | ---: | --- |
| `a` với `b` | `|1.0 - 1.09375| = 0.09375` | Có, `true` |
| `b` với `c` | `|1.09375 - 1.1875| = 0.09375` | Có, `true` |
| `a` với `c` | `|1.0 - 1.1875| = 0.1875` | Không, `false` |

Kết quả:

```text
a ~ b: true
b ~ c: true
a ~ c: false
```

Dấu `~` ở đây chỉ quan hệ “gần bằng theo epsilon”, **không phải toán tử của Java**.

Vì `a ~ b` và `b ~ c` đều đúng nhưng `a ~ c` sai, quan hệ này không bắc cầu. Đây là lý do cốt lõi: **“gần” theo từng cặp không có nghĩa các điểm ở hai đầu của một chuỗi đều gần nhau**.

### Giải thích bằng bất đẳng thức tam giác

Với số thực, ta có:

```text
|a - c| <= |a - b| + |b - c|
```

Nếu mỗi khoảng `|a-b|` và `|b-c|` không vượt `epsilon`, ta chỉ suy ra:

```text
|a - c| <= 2 * epsilon
```

Ta **không** suy ra được `|a-c| <= epsilon`. Trong ví dụ, hai bước dài `0.09375` gộp lại thành `0.1875`; mỗi bước đạt điều kiện, nhưng tổng vượt ngưỡng `0.125`.

Lập luận áp dụng ngay cả khi các số là số thực toán học với phép tính chính xác tuyệt đối. Sai số làm tròn của `double` có thể làm việc kiểm tra epsilon phức tạp thêm, nhưng **không phải nguyên nhân gốc** của việc mất tính bắc cầu.

## 3. Thử trực tiếp với Java 17+

Lưu chương trình hoàn chỉnh dưới đây thành `FloatingToleranceDemo.java`:

```java
public class FloatingToleranceDemo {
    private static final double EPSILON = 0.125;

    // Đây là phép kiểm "gần bằng" dành cho số đo hữu hạn.
    // Cố ý KHÔNG dùng hàm này làm Object.equals().
    static boolean near(double x, double y) {
        return Double.isFinite(x)
                && Double.isFinite(y)
                && Math.abs(x - y) <= EPSILON;
    }

    // Một khóa phân nhóm minh họa quan hệ bằng nhau đúng nghĩa.
    record BucketKey(int index) {}

    static BucketKey fixedBucket(double value) {
        // Giới hạn miền chỉ để ví dụ chuyển sang int an toàn.
        if (!Double.isFinite(value) || value < -1000 || value > 1000) {
            throw new IllegalArgumentException("Value outside supported range");
        }
        return new BucketKey((int) Math.floor(value / 0.5));
    }

    public static void main(String[] args) {
        double a = 1.0;
        double b = 1.09375;
        double c = 1.1875;

        System.out.println("epsilon: " + EPSILON);
        System.out.println("|a-b|: " + Math.abs(a - b));
        System.out.println("|b-c|: " + Math.abs(b - c));
        System.out.println("|a-c|: " + Math.abs(a - c));
        System.out.println("near(a,b): " + near(a, b));
        System.out.println("near(b,c): " + near(b, c));
        System.out.println("near(a,c): " + near(a, c));

        // Điều kiện tiền đề đúng, nhưng kết luận sai.
        boolean chainIsTransitive =
                !(near(a, b) && near(b, c)) || near(a, c);
        System.out.println("near transitive for this triple: "
                + chainIsTransitive);

        BucketKey ka = fixedBucket(a);
        BucketKey kb = fixedBucket(b);
        BucketKey kc = fixedBucket(c);
        System.out.println("bucket(a): " + ka.index());
        System.out.println("bucket(b): " + kb.index());
        System.out.println("bucket(c): " + kc.index());
        System.out.println("bucket a=b: " + ka.equals(kb));
        System.out.println("bucket b=c: " + kb.equals(kc));
        System.out.println("bucket a=c: " + ka.equals(kc));

        System.out.println("near(NaN,NaN): "
                + near(Double.NaN, Double.NaN));
        System.out.println("near(+inf,+inf): "
                + near(Double.POSITIVE_INFINITY,
                       Double.POSITIVE_INFINITY));
    }
}
```

Lệnh biên dịch và chạy:

```bash
javac FloatingToleranceDemo.java
java FloatingToleranceDemo
```

Kết quả stdout chính xác:

```text
epsilon: 0.125
|a-b|: 0.09375
|b-c|: 0.09375
|a-c|: 0.1875
near(a,b): true
near(b,c): true
near(a,c): false
near transitive for this triple: false
bucket(a): 2
bucket(b): 2
bucket(c): 2
bucket a=b: true
bucket b=c: true
bucket a=c: true
near(NaN,NaN): false
near(+inf,+inf): false
```

Những điều cần quan sát:

1. `near(a, b)` và `near(b, c)` đều `true`, nhưng `near(a, c)` là `false`: một phản ví dụ đủ để bác bỏ phát biểu “quan hệ gần bằng này luôn bắc cầu”.
2. Biểu thức `!(P && Q) || R` kiểm tra mệnh đề `(P && Q) => R` cho **một bộ ba**. Kết quả `false` chứng minh bộ ba đã phá vỡ bắc cầu; kết quả `true` trên một bộ ba khác chưa đủ chứng minh hợp đồng đúng trên toàn miền.
3. `BucketKey` là `record` có `equals()`/`hashCode()` dựa trên `index`. Khi `index(a) = index(b)` và `index(b) = index(c)` thì `index(a) = index(c)`; vì vậy quan hệ bằng nhau theo khóa nhóm là bắc cầu. Ba giá trị ở đây đều thuộc nhóm có chỉ số `2`.
4. Nhóm có biên: chẳng hạn `1.49` và `1.51` rất gần nhưng thuộc hai nhóm khác nhau (`Math.floor(x / 0.5)` cho `2` và `3`). **Bằng nhau theo nhóm không tương đương “chênh không quá epsilon”**; chỉ dùng khi nghiệp vụ thật sự định nghĩa các khoảng cố định.
5. `near` chủ ý từ chối `NaN` và vô cực để thể hiện miền dữ liệu là số đo hữu hạn. Đây không phải thiết kế `equals()` vì `equals()` phải phản xạ đối với mọi đối tượng, kể cả đối tượng chứa dữ liệu đặc biệt.

## 4. Tại sao chọn epsilon nhỏ hơn vẫn không giải quyết được?

Giảm epsilon có thể làm số lượng cặp được chấp nhận ít đi, nhưng với **mọi epsilon dương** trên một miền đủ rộng, vẫn có thể tạo ba giá trị:

```text
a = 0
b = 0.75 * epsilon
c = 1.50 * epsilon

|a-b| = 0.75 * epsilon <= epsilon
|b-c| = 0.75 * epsilon <= epsilon
|a-c| = 1.50 * epsilon >  epsilon
```

Đây là chứng minh toán học cho số thực; khi hiện thực bằng `double`, phải tính tới phạm vi, độ phân giải biểu diễn và các phép làm tròn. Vì vậy phát biểu “**bất kỳ epsilon dương đều chắc chắn cho phản ví dụ với mọi tập giá trị double**” là quá mạnh: trên một miền rời rạc hoặc cực nhỏ, có thể không tìm được ba phần tử như vậy. Điều đúng là công thức khoảng cách theo ngưỡng **không đảm bảo bắc cầu theo nguyên lý**, nên không được dùng làm cơ sở tổng quát của `equals()`.

Đổi phép so sánh `<= epsilon` thành `< epsilon` cũng không chữa được. Chọn các khoảng nhỏ hơn epsilon nhưng tổng của chúng vượt epsilon vẫn tạo phản ví dụ. Tương tự, thay ngưỡng tuyệt đối bằng ngưỡng tương đối theo độ lớn `max(|x|, |y|)` có thể hữu ích cho tính toán số, nhưng **không tự đem lại tính bắc cầu**.

## 5. Phân biệt ba nhu cầu khác nhau

| Nhu cầu | Cách làm phù hợp | Tính chất cần lưu ý |
| --- | --- | --- |
| Hai kết quả tính toán/đo lường có đủ gần nhau không? | Phương thức riêng `near`/`withinTolerance` | Quan hệ gần bằng có thể không bắc cầu |
| Hai giá trị mang cùng bản sắc logic trong một đối tượng Java không? | `equals()` và `hashCode()` dựa trên tiêu chí ổn định, nhất quán | Phải tuân thủ phản xạ, đối xứng, bắc cầu, nhất quán, không bằng `null` |
| Hai số thuộc cùng một khoảng phân nhóm nghiệp vụ không? | Chuẩn hóa thành khóa nhóm rồi so sánh khóa | Bằng nhau theo khóa bắc cầu, nhưng có ranh giới cứng |

### a. Khi chỉ cần so sánh gần bằng

Một API rõ ý định có thể nhận tolerance tại thời điểm gọi:

```java
static boolean nearFinite(double x, double y, double epsilon) {
    if (!Double.isFinite(epsilon) || epsilon < 0) {
        throw new IllegalArgumentException("Invalid epsilon");
    }
    return Double.isFinite(x)
            && Double.isFinite(y)
            && Math.abs(x - y) <= epsilon;
}
```

Đây là **phương thức minh họa độc lập**, chưa phải chương trình có `main`. Nó phù hợp khi miền sử dụng là các số hữu hạn, phép trừ không gây tràn và khoảng cách tuyệt đối là tiêu chí đúng. Ví dụ: đánh giá kết quả thuật toán trên dữ liệu có thang đo cố định, kiểm tra sensor trong một dung sai được chỉ định, hoặc so sánh sai số đầu ra trong bài test.

Đối với giá trị có độ lớn rất khác nhau, có thể cần dung sai tương đối, dung sai kết hợp tuyệt đối và tương đối, hoặc kiểm tra ULP (đơn vị của độ chính xác biểu diễn tại vùng giá trị đang xét). Việc chọn cách nào phụ thuộc bài toán số học; **tất cả vẫn là cách kiểm tra độ gần**, không phải lời giải thay thế hợp đồng `equals()`.

Lưu ý trường hợp cực trị: `Math.abs(x-y)` có thể thành `Infinity` khi hai số hữu hạn rất lớn trái dấu làm phép trừ tràn. Một triển khai thư viện số đáng tin cần xác định hành vi với overflow, dưới ngưỡng, `NaN`, `Infinity`, `+0.0`/`-0.0` và epsilon âm/không hữu hạn.

### b. Khi cần định nghĩa `equals()`

Không triển khai kiểu:

```java
// PHẢN VÍ DỤ về cách thiết kế; không chép vào lớp production:
@Override
public boolean equals(Object obj) {
    if (!(obj instanceof Measurement other)) {
        return false;
    }
    return Math.abs(this.value - other.value) <= EPSILON;
}
```

Fragment trên giả định đang ở trong lớp có kiểu `Measurement`, trường `value` và hằng `EPSILON`. Nó cố ý minh họa quy tắc sai, **không phải file chạy độc lập**. Ngoài lỗi bắc cầu, phép so sánh không được quy định rõ cho `NaN`/vô cực và cách tính `hashCode()` có thể vi phạm thêm hợp đồng.

Thay vào đó, trước tiên xác định bản sắc logic: cùng mã định danh? cùng các thành phần giá trị? cùng đơn vị đã chuẩn hóa? cùng khóa đã được lượng tử hóa theo quy tắc nghiệp vụ? Sau đó so sánh **khóa xác định ổn định**. Một lớp bất biến hoặc `record` với các thành phần có quan hệ bằng nhau hợp lệ thường là lựa chọn dễ kiểm soát.

Các giá trị cần gần bằng có thể để nguyên trong record và được so sánh qua phương thức khác, chẳng hạn `measurement.closeTo(other, tolerance)`. Đừng buộc khái niệm “đủ gần” thành “cùng đối tượng logic”.

### c. Khi dùng phân nhóm/lượng tử hóa

Nếu nghiệp vụ cố định giá trị về bội số của bước đo, ví dụ phân vào các vùng `[0.0, 0.5)`, `[0.5, 1.0)`, `[1.0, 1.5)`, hãy tính khóa nhóm trước rồi chỉ so sánh khóa. Các đối tượng được phân loại cùng một khóa tạo thành một **lớp tương đương**.

Tuy nhiên, vị trí gần biên có thể bị chia nhóm dù chênh lệch rất nhỏ; bước nhóm và chính sách làm tròn phải được quyết định theo nghiệp vụ. Trong code dùng `double`, cần nêu giới hạn miền, xử lý giá trị đặc biệt và kiểm soát độ chính xác phép chia/nhân khi tính khóa; không mặc định `Math.round(x / step)` chính xác cho mọi số cực lớn.

## 6. Liên hệ với `hashCode()` và các collection

Các collection như `HashSet` và `HashMap` giả định đối tượng tuân thủ hợp đồng bằng nhau. Nếu đưa epsilon vào `equals()`:

- Một đối tượng `a` có thể được coi bằng `b`, `b` bằng `c` nhưng `a` không bằng `c`. Khái niệm “trùng giá trị” khi tìm kiếm, thêm, xóa và khử trùng lặp sẽ mất tính nhất quán.
- `hashCode()` còn phải thỏa: khi `x.equals(y)` là `true` thì `x.hashCode() == y.hashCode()`. Lấy hash trực tiếp từ số `double` thường không thỏa với các cặp được xem là gần bằng theo epsilon. Làm tròn để hash vào bucket cố định cũng không cứu được quan hệ bằng nhau theo **khoảng cách từng cặp** tại ranh giới bucket.
- Cho mọi đối tượng cùng một hash có thể thỏa điều kiện “bằng nhau thì hash giống nhau” một cách hình thức, nhưng gây nhiều va chạm và **vẫn không sửa được lỗi bắc cầu**.

Tương tự, `TreeSet`/`TreeMap` dùng thứ tự theo `Comparator`. Một comparator trả về `0` khi hai số chỉ “đủ gần” cũng có thể gây quan hệ thứ tự không nhất quán, vi phạm yêu cầu của một thứ tự hợp lệ. Khi cần sắp xếp hoặc tạo khóa, hãy định nghĩa thứ tự/khóa rõ ràng thay vì “gần nhau thì coi như bằng” tùy cặp.

## 7. Những nhầm lẫn Java thường gặp

1. **`==` giữa `double` và `Double.equals` không có cùng hành vi ở mọi giá trị đặc biệt.** Với nguyên thủy `double`, `Double.NaN == Double.NaN` là `false` và `+0.0 == -0.0` là `true`. Với `Double.equals`, các đối tượng chứa giá trị `NaN` được xem bằng nhau và `+0.0` khác `-0.0`. Hai cách có quy tắc khác nhau; tự chọn ngữ nghĩa phù hợp thay vì trộn lẫn chúng.
2. **`Double.compare(x, y) == 0` là so sánh theo thứ tự toàn phần được định nghĩa cho giá trị `double`**, khác kiểm tra gần bằng với epsilon. Nó phân biệt hai dạng zero theo dấu và xếp các dạng NaN vào cùng một nhóm so sánh; nếu dùng kết quả này làm quan hệ bằng nhau, tính bắc cầu được bảo đảm bởi tiêu chí so sánh nhất quán.
3. **Một bộ test ngắn không chứng minh được đúng với mọi bộ ba.** Cần lập luận từ chính quy tắc so sánh; với dung sai, phản ví dụ ba phần tử đã đủ chứng minh sự cố.
4. **Lỗi bắc cầu khác sai số làm tròn.** Cùng một ví dụ có thể dùng các số nhị phân biểu diễn chính xác. Thay thuật toán `double` bằng `BigDecimal` vẫn không khiến “chênh không quá epsilon” trở thành quan hệ bắc cầu.
5. **Bằng nhau theo số học và bằng nhau theo biểu diễn có thể khác.** Với `BigDecimal`, `compareTo` có thể coi `1.0` và `1.00` cùng giá trị số, trong khi `equals` phân biệt scale. Hai tiêu chí khác nhau không tự chứng minh một trong số đó thiếu tính bắc cầu; quan trọng là dùng nhất quán một tiêu chí cho từng quan hệ.

## 8. Câu hỏi tự kiểm tra

**Câu 1.** Với `epsilon = 0.2`, `a = 2.0`, `b = 2.15`, `c = 2.3`, ba phép kiểm gần bằng sẽ ra sao?

Đáp án theo khoảng cách lý tưởng: `|a-b| = 0.15 <= 0.2`, `|b-c| = 0.15 <= 0.2`, nhưng `|a-c| = 0.3 > 0.2`. Đây vẫn là phản ví dụ bắc cầu. Khi chạy `double`, giá trị gần đúng có thể không hiển thị đúng những số thập phân trên, nhưng các khoảng được chọn cách đủ xa ngưỡng để kết quả boolean vẫn như mô tả.

**Câu 2.** Đổi thành `epsilon = 0.001` có giải quyết tận gốc không?

Không. Với miền số thực đủ rộng, ta vẫn có thể chọn hai bước riêng rẽ không vượt `0.001` nhưng tổng vượt `0.001`. Giảm tolerance chỉ thay đổi việc cặp nào được chấp nhận.

**Câu 3.** Vì sao so sánh khóa nhóm hợp lệ về bắc cầu dù đôi khi hai số rất gần lại nằm ở hai nhóm khác nhau?

Vì quan hệ được định nghĩa là **“có cùng khóa”**, không phải “gần nhau”. Phép bằng nhau của khóa ổn định và bắc cầu; ranh giới nhóm là đánh đổi về ngữ nghĩa cần chấp nhận rõ ràng.

**Câu 4.** Khi kiểm thử phép tính dấu phẩy động, có nên luôn dùng `Double.compare(x, y) == 0` không?

Không nhất thiết. Nếu mục tiêu là đánh giá kết quả gần một giá trị kỳ vọng trong giới hạn sai số, dùng phép kiểm dung sai tường minh phù hợp hơn. Nhưng nếu định nghĩa bản sắc logic của đối tượng hoặc khóa collection, không nên sử dụng quan hệ dung sai thiếu bắc cầu.

## Kết luận

**So sánh số thực với ngưỡng epsilon chỉ đảm bảo hai giá trị gần nhau theo từng cặp.** Hai cặp liên tiếp đều đạt ngưỡng vẫn có thể khiến cặp đầu–cuối vượt ngưỡng (`0.09375 + 0.09375 > 0.125`). Đây là đặc tính của quan hệ khoảng cách, không phải lỗi riêng của cách Java làm tròn `double`. Hãy dùng phương thức riêng cho kiểm tra xấp xỉ và dùng một tiêu chí bằng nhau ổn định, có tính bắc cầu, khi thiết kế `equals()`, `hashCode()` hoặc khóa collection.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equality-semantics-008">Tách phép so khớp gần đúng khỏi equals() khi không tạo quan hệ tương đương</a>

<details>
<summary>Xem chi tiết</summary>

# Tách phép so khớp gần đúng khỏi `equals()` khi không tạo quan hệ tương đương

Trong Java, `equals(Object)` biểu thị **bằng nhau về mặt logic** theo hợp đồng của `Object`. Còn các câu hỏi như “hai số đo lệch nhau không quá 1°C?”, “hai tọa độ cách nhau dưới 10 mét?”, “hai chuỗi có độ tương tự trên 90%?” thường là **phép so khớp gần đúng**. Chúng có thể hữu ích trong nghiệp vụ, nhưng không nhất thiết tạo ra quan hệ tương đương. Nếu đưa chúng vào `equals()`, kết quả của `HashSet`, `HashMap` và các phép so sánh trong chương trình có thể khó hiểu hoặc sai.

**Nguyên tắc:** giữ `equals()` cho một định nghĩa bằng nhau ổn định, có tính tương đương; thể hiện phép gần đúng qua API riêng như `isCloseTo`, `matchesWithinTolerance` hoặc `isSimilarTo`, với quy tắc và ngưỡng rõ ràng.

## 1. Thế nào là một quan hệ tương đương?

Một quan hệ `~` trên các đối tượng là *quan hệ tương đương* khi thỏa ba tính chất:

| Tính chất | Diễn đạt | Ví dụ với đối tượng `a`, `b`, `c` |
| --- | --- | --- |
| Phản xạ (*reflexive*) | Mỗi đối tượng bằng chính nó | `a ~ a` |
| Đối xứng (*symmetric*) | Nếu `a` bằng `b` thì `b` bằng `a` | `a ~ b ⇒ b ~ a` |
| Bắc cầu (*transitive*) | Nếu `a` bằng `b` và `b` bằng `c`, thì `a` phải bằng `c` | `a ~ b` và `b ~ c` ⇒ `a ~ c` |

Hợp đồng `Object.equals(Object)` trong Java đòi hỏi các tính chất này. Nó còn yêu cầu **nhất quán** (khi thông tin dùng để so sánh không đổi, gọi lặp lại phải cho cùng kết quả) và `x.equals(null)` luôn trả `false`. Khi ghi đè `equals()`, phải đồng thời bảo đảm: nếu `a.equals(b)` là `true` thì `a.hashCode() == b.hashCode()`. Chiều ngược lại không bắt buộc.

Quan hệ tương đương chia tập đối tượng thành các nhóm bằng nhau rõ ràng: hai đối tượng trong cùng nhóm phải được xem là bằng nhau, bất kể thứ tự so sánh. Điều này đặc biệt quan trọng với các collection tổ chức phần tử theo tính bằng nhau.

## 2. Vì sao so khớp bằng khoảng sai số thường không bắc cầu?

Giả sử hai nhiệt độ “gần nhau” nếu độ chênh lệch tuyệt đối không quá `1.0`:

~~~java
static boolean close(double x, double y, double tolerance) {
    return Math.abs(x - y) <= tolerance;
}
~~~

Đây chỉ là **đoạn minh họa quy tắc**, chưa có kiểm tra `NaN`, vô cực và ngưỡng đầu vào; ví dụ chạy đầy đủ ở phần sau sẽ có kiểm tra đó.

Xét ba giá trị:

- `a = 0.0` và `b = 0.6`: chênh lệch `0.6`, nên `a ~ b` là `true`.
- `b = 0.6` và `c = 1.2`: chênh lệch `0.6`, nên `b ~ c` là `true`.
- `a = 0.0` và `c = 1.2`: chênh lệch `1.2`, nên `a ~ c` là `false`.

Ta có `a ~ b` và `b ~ c` nhưng **không có** `a ~ c`. Như vậy, quy tắc “chênh lệch ≤ ngưỡng” **không có tính bắc cầu** với ngưỡng dương nói chung. Dù quy tắc này có phản xạ và đối xứng cho số hữu hạn và ngưỡng hợp lệ, chỉ cần hỏng một trong ba tính chất là nó không còn là quan hệ tương đương.

Đó là lý do **không nên** viết:

~~~java
// Sai về hợp đồng equals: minh họa điều KHÔNG nên làm.
@Override
public boolean equals(Object obj) {
    if (!(obj instanceof Temperature other)) {
        return false;
    }
    return Math.abs(celsius - other.celsius) <= 1.0;
}
~~~

Mảnh mã trên giả định có lớp `Temperature` và trường `celsius`; đây không phải lớp hoàn chỉnh để biên dịch độc lập. Kể cả khi viết một `hashCode()` trả cùng hằng số cho mọi đối tượng, việc vi phạm tính bắc cầu **vẫn còn nguyên**. Cố tìm công thức hash không thể sửa một quan hệ không hợp lệ.

Ngưỡng còn có thể phụ thuộc người dùng, phép đo, màn hình hoặc thời điểm. Nếu lấy một ngưỡng thay đổi theo ngữ cảnh để cài đặt `equals()`, cùng cặp đối tượng có thể lúc được xem bằng nhau, lúc không, ngay cả khi bản thân đối tượng không đổi. Điều này cũng trái kỳ vọng về tính nhất quán của `equals()`.

## 3. Thiết kế đúng: `equals()` diễn đạt danh tính; API riêng diễn đạt độ gần

Giả sử mỗi lần đọc cảm biến có một **mã lần đọc** (`readingId`) duy nhất, cố định. Hai đối tượng cùng mã đại diện cho cùng một lần đọc logic, nên có thể coi là bằng nhau theo danh tính nghiệp vụ. Giá trị nhiệt độ (`celsius`) chỉ dùng để hỏi “hai lần đọc có gần nhau về nhiệt độ không?”.

Thiết kế khi đó có hai phép độc lập:

- `equals(Object)`: so sánh `readingId`, trả lời **“cùng lần đọc?”**; đây là quan hệ tương đương nếu mã ổn định và được gán nhất quán.
- `isCloseTo(Reading other, double tolerance)`: so sánh nhiệt độ trong ngưỡng, trả lời **“hai số đo gần nhau?”**; không tuyên bố hai đối tượng bằng nhau.

Một đối tượng có thể **khác** đối tượng kia theo `equals()`, nhưng **gần** nhau theo `isCloseTo()`. Điều đó không mâu thuẫn: hai phương thức trả lời hai câu hỏi nghiệp vụ khác nhau.

### Ví dụ Java 17 hoàn chỉnh

Tạo tệp **`ApproximateMatchDemo.java`** với nội dung sau:

~~~java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ApproximateMatchDemo {

    static final class Reading {
        private final String readingId;
        private final double celsius;

        Reading(String readingId, double celsius) {
            this.readingId = Objects.requireNonNull(readingId, "readingId");
            if (!Double.isFinite(celsius)) {
                throw new IllegalArgumentException("celsius must be finite");
            }
            this.celsius = celsius;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Reading other)) {
                return false;
            }
            return readingId.equals(other.readingId);
        }

        @Override
        public int hashCode() {
            return readingId.hashCode();
        }

        public boolean isCloseTo(Reading other, double tolerance) {
            if (!Double.isFinite(tolerance) || tolerance < 0.0) {
                throw new IllegalArgumentException(
                    "tolerance must be finite and >= 0");
            }
            return other != null
                && Math.abs(celsius - other.celsius) <= tolerance;
        }
    }

    public static void main(String[] args) {
        Reading a = new Reading("A", 0.0);
        Reading b = new Reading("B", 0.6);
        Reading c = new Reading("C", 1.2);
        Reading copyOfA = new Reading("A", 0.0);

        double tolerance = 1.0;

        System.out.println("a close b: " + a.isCloseTo(b, tolerance));
        System.out.println("b close c: " + b.isCloseTo(c, tolerance));
        System.out.println("a close c: " + a.isCloseTo(c, tolerance));

        System.out.println("a equals b: " + a.equals(b));
        System.out.println("a equals copyOfA: " + a.equals(copyOfA));
        System.out.println("same hash for equal: "
            + (a.hashCode() == copyOfA.hashCode()));
        System.out.println("a close null: " + a.isCloseTo(null, tolerance));

        Set<Reading> readings = new HashSet<>();
        readings.add(a);
        readings.add(b);
        readings.add(c);
        readings.add(copyOfA);
        System.out.println("HashSet size: " + readings.size());
    }
}
~~~

Biên dịch/chạy với JDK 17 trở lên:

~~~bash
javac ApproximateMatchDemo.java
java ApproximateMatchDemo
~~~

**Kết quả mong đợi:**

~~~text
a close b: true
b close c: true
a close c: false
a equals b: false
a equals copyOfA: true
same hash for equal: true
a close null: false
HashSet size: 3
~~~

Giải thích:

1. Ba lệnh `close` cố ý tái hiện phản ví dụ không bắc cầu. Đây là hành vi **đúng** của phép so khớp gần đúng, vì phương thức này không được dùng làm `equals()`.
2. `a.equals(b)` là `false` vì hai mã lần đọc khác nhau, dù nhiệt độ đủ gần.
3. `a.equals(copyOfA)` là `true` vì chúng mang cùng `readingId`; `hashCode()` dựa trên cùng mã nên tuân thủ quy tắc hash.
4. `isCloseTo(null, tolerance)` chọn trả `false` theo thiết kế API này; `equals(null)` cũng trả `false` theo hợp đồng `Object`.
5. `HashSet` có ba phần tử: `A`, `B`, `C`. Bản sao của `A` không thêm phần tử thứ tư, và ngưỡng so nhiệt độ không ảnh hưởng đến membership.

**Giới hạn có chủ đích:** lớp `Reading` là `final`, các trường là `final` và `readingId` không đổi sau khi tạo. Điều này giúp định nghĩa bằng nhau dựa trên mã ổn định. Quy tắc “cùng `readingId` là cùng một lần đọc logic” chỉ đúng khi hệ thống thực sự bảo đảm ý nghĩa đó; không nên tự áp dụng cùng cách cho mọi mô hình dữ liệu. `isCloseTo` kiểm tra nhiệt độ và ngưỡng đều hữu hạn; nếu ngưỡng âm, `NaN` hoặc vô cực, phương thức ném `IllegalArgumentException`.

## 4. Tác hại của việc nhét phép gần đúng vào `equals()`

### Collection dựa vào hash

`HashSet` và `HashMap` dùng `hashCode()` để xác định vùng tra cứu rồi dùng `equals()` để xác định phần tử hoặc khóa bằng nhau. Nếu `equals()` không có tính bắc cầu, việc coi một giá trị là trùng lặp có thể phụ thuộc các phần tử đã được thêm và thứ tự thao tác. Không còn một cách phân hoạch nhất quán thành những nhóm bằng nhau.

Ví dụ với `a = 0.0`, `b = 0.6`, `c = 1.2`: nếu cho `b` đại diện cả `a` lẫn `c` theo ngưỡng `1.0`, thì `a` và `c` lại không bằng nhau theo cùng quy tắc. Không thể coi “tất cả các giá trị gần nhau” là một khóa hash logic mà vẫn giữ đúng ý nghĩa của phép gần nhau trực tiếp.

**Không đưa quan hệ gần đúng làm khóa đồng nhất của `Map`, điều kiện loại trùng của `Set` hoặc cơ sở cho `hashCode()`.** Hãy lưu đối tượng theo khóa định danh/giá trị chính xác và tìm các đối tượng gần nhau bằng thao tác riêng.

### So sánh để sắp xếp

Tránh viết `Comparator` trả `0` cho hai giá trị chỉ vì chúng cách nhau dưới ngưỡng: một quy tắc kiểu đó thường phá vỡ yêu cầu sắp thứ tự nhất quán. `TreeSet` và `TreeMap` dựa vào kết quả so sánh để xác định thứ tự và việc hai khóa có được coi là tương đương trong cấu trúc cây. Nếu cần sắp xếp, hãy dùng một thứ tự rõ ràng (chẳng hạn `Double.compare` trên số đo hợp lệ), rồi thực hiện tìm kiếm trong khoảng sai số riêng.

### Sai số tương đối và phép so khớp bất đối xứng

Không phải công thức “gần đúng” nào cũng tự động đối xứng. Chẳng hạn `abs(a-b) / abs(a) <= p` phụ thuộc chọn `a` làm mẫu số; đổi chiều hai đối số có thể cho kết quả khác. Cần xác định rõ ý nghĩa của ngưỡng tuyệt đối, ngưỡng tương đối, đơn vị đo và cách xử lý số 0. Ngay cả khi sửa công thức để đối xứng, vấn đề **không bắc cầu** nhìn chung vẫn có thể tồn tại.

## 5. Nếu thật sự cần nhóm bằng nhau theo độ chính xác thì sao?

Có lúc yêu cầu nghiệp vụ thực sự là **xem các giá trị thuộc cùng một mức độ phân giải là bằng nhau**, ví dụ chỉ quan tâm nhiệt độ đã được ghi nhận tới phần mười độ. Khi đó có thể **chuẩn hóa thành một giá trị đại diện ổn định trước khi so sánh**, rồi định nghĩa bằng nhau bằng sự bằng nhau của giá trị đại diện.

Ví dụ minh họa ý tưởng:

~~~java
// Sau khi đã xác thực đầu vào hữu hạn và miền giá trị phù hợp:
long tenthDegrees = Math.round(celsius * 10.0);
~~~

Nếu một kiểu dữ liệu lưu khóa chuẩn hóa `tenthDegrees` và dùng `Long.equals`/so sánh `long` (cùng `hashCode` tương ứng), quan hệ bằng nhau theo khóa **có tính bắc cầu**: cùng khóa thì cùng nhóm. Tuy nhiên, đây là một **quy tắc khác** phép `Math.abs(a - b) <= 0.1`: hai số rất gần nhưng nằm hai phía ranh giới làm tròn có thể khác nhóm, còn hai số cùng nhóm được xem là bằng nhau theo mức phân giải đã chọn. Phải chấp nhận ranh giới này ở cấp yêu cầu nghiệp vụ, không giả vờ hai quy tắc tương đương.

Cách chọn tùy ý nghĩa dữ liệu:

| Yêu cầu thật | Thiết kế phù hợp |
| --- | --- |
| “Cùng một bản ghi/lần đo?” | `equals()` dựa trên ID ổn định của bản ghi |
| “Cùng giá trị đã chuẩn hóa đến độ phân giải cố định?” | Chuẩn hóa rồi so khóa chính xác; đồng bộ `equals()` và `hashCode()` |
| “Hai phép đo cách nhau không quá ε?” | `isCloseTo(other, epsilon)` / `matchesWithinTolerance` |
| “Các điểm đo hình thành nhóm liên thông theo khoảng cách?” | Thuật toán gom cụm/đồ thị riêng, mô tả rõ quy tắc thành viên |
| “Cần tìm mọi giá trị trong khoảng gần X?” | Lọc/tìm trong khoảng theo tiêu chí rõ ràng, không giả lập bằng `equals()` |

Với gom cụm theo quan hệ gần, các phần tử cùng cụm qua chuỗi nối không nhất thiết phải gần nhau trực tiếp. Ví dụ `a` và `c` có thể nằm cùng thành phần liên thông thông qua `b`, dù `a` không gần `c`. Vì thế cần đặt tên và kiểm thử đúng quan hệ mà sản phẩm muốn biểu diễn.

## 6. Checklist thiết kế và kiểm thử

Trước khi định nghĩa `equals()` cho một lớp, hãy tự trả lời:

1. “Bằng nhau” có nghĩa là cùng danh tính, cùng giá trị hay cùng khóa chuẩn hóa? Quy tắc có **phản xạ, đối xứng, bắc cầu** không?
2. Dữ liệu dùng cho `equals()` và `hashCode()` có ổn định trong thời gian đối tượng được dùng làm khóa `HashMap` hoặc phần tử `HashSet` không?
3. Ngưỡng “gần”, đơn vị đo, thuật toán độ tương tự hoặc ý kiến người dùng có đang bị trộn vào `equals()` không? Nếu có, tách thành API chuyên biệt.
4. Với API gần đúng, đã định nghĩa rõ hành vi cho `null`, ngưỡng âm, `NaN`, vô cực, giới hạn sai số và ranh giới chưa?
5. Đã kiểm thử bộ ba `a, b, c` mà `a` gần `b`, `b` gần `c` nhưng `a` không gần `c` chưa? Trường hợp này phải **được phép** ở API gần đúng, nhưng sẽ tố cáo lỗi nếu xảy ra ở `equals()`.

**Kết luận học tập:** `equals()` nói về *quan hệ bằng nhau logic* và phải tuân thủ hợp đồng của `Object`. Một phép so khớp theo ngưỡng có thể rất đúng về nghiệp vụ mà vẫn không phải quan hệ tương đương. Hãy đặt tên riêng cho nó, truyền ngưỡng rõ ràng, kiểm thử các trường hợp biên và giữ `equals()`/`hashCode()` dựa trên định nghĩa bằng nhau ổn định.

</details>

- [Quay lại đầu trang](#back-to-top)
