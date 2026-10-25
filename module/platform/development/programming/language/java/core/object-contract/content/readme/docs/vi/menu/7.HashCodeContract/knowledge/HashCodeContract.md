<a id="back-to-top"></a>

# Hợp đồng hashCode

## Menu
- [hashCode() trả về int để hỗ trợ cấu trúc dữ liệu băm](#hash-code-contract-001)
- [Hai đối tượng equals() phải có cùng hash code](#hash-code-contract-002)
- [Cùng hash code không suy ra equals()](#hash-code-contract-003)
- [Đối tượng không bằng nhau vẫn được phép trùng hash code](#hash-code-contract-004)
- [Tính ổn định trong một lần chạy khi thông tin dùng trong equals() không đổi](#hash-code-contract-005)
- [Không có yêu cầu giữ cùng hash code giữa các lần chạy chương trình](#hash-code-contract-006)
- [Vì sao ghi đè equals() thường phải ghi đè hashCode() cùng lúc](#hash-code-contract-007)
- [Hash code hằng có thể đúng hợp đồng nhưng phân bố kém](#hash-code-contract-008)
- [Không dùng hashCode() làm mã định danh duy nhất hoặc mã bảo mật](#hash-code-contract-009)

## <a id="hash-code-contract-001">hashCode() trả về int để hỗ trợ cấu trúc dữ liệu băm</a>

<details>
<summary>Xem chi tiết</summary>

# `hashCode()` trả về `int` để hỗ trợ cấu trúc dữ liệu băm

## 1. `hashCode()` là gì và vì sao kiểu trả về là `int`?

Trong Java, mọi lớp kế thừa phương thức `public int hashCode()` từ `java.lang.Object` (có thể ghi đè). Phương thức trả về một **giá trị băm 32-bit có dấu** để các cấu trúc dữ liệu dựa trên bảng băm, như `HashMap` và `HashSet`, tổ chức và tra cứu phần tử hiệu quả.

- **Kiểu `int` là một phần của chữ ký phương thức theo Java**, không phải lựa chọn tuỳ ý ở lớp con. Miền giá trị là từ `-2_147_483_648` đến `2_147_483_647` (tổng cộng `2^32` mẫu bit). Số âm, số 0 và số dương đều là kết quả hợp lệ.
- **Hash code không phải ID duy nhất.** Không gian 32-bit hữu hạn, nên những đối tượng khác nhau hoàn toàn có thể cùng hash code (collision, hay đụng độ băm).
- **Hash code không phải chỉ số ngăn (bucket) trực tiếp.** Cấu trúc băm còn phải chuyển đổi giá trị băm sang vị trí trong vùng lưu trữ hiện tại. Thuật toán chuyển đổi và số bucket phụ thuộc cách triển khai; chúng có thể thay đổi khi cấu trúc mở rộng.
- **Hash code cũng không phải địa chỉ bộ nhớ được bảo đảm.** `Object.hashCode()` mặc định gắn với sự bằng nhau theo danh tính đối tượng, nhưng Java không cam kết nó là địa chỉ, duy nhất hay giữ nguyên giữa những lần chạy JVM.

Trả về một `int` tạo ra giao diện chung để các thư viện Java có thể băm mọi đối tượng. Giá trị này giúp *thu hẹp phạm vi ứng viên cần kiểm tra*; việc xác định hai khoá có thực sự bằng nhau vẫn dựa trên `equals()`.

## 2. `HashMap` và `HashSet` sử dụng giá trị băm như thế nào?

Khi thêm và tìm một khoá khác `null` trong `HashMap`, có thể hiểu ở mức khái niệm như sau:

1. Gọi `hashCode()` của khoá để lấy số nguyên băm; triển khai có thể trộn thêm các bit.
2. Chuyển giá trị băm thành vị trí bucket trong bảng đang dùng.
3. Tìm trong bucket các mục ứng viên. Khi gặp hash tương ứng, so sánh tính bằng nhau của khoá, thường qua `equals()`; nếu nhiều khoá rơi vào cùng bucket, cấu trúc phải phân biệt chúng.
4. Nếu tìm thấy khoá tương đương theo `equals()`, `put` cập nhật giá trị; nếu chưa có, thêm mục mới. Khi `get`, chỉ trả về giá trị ứng với khoá phù hợp.

`HashSet` có ý tưởng tương tự để quyết định phần tử đã có trong tập hợp hay chưa; triển khai chuẩn dựa trên một `HashMap` nội bộ. Đặc biệt, **trùng hash code không đồng nghĩa trùng phần tử**. Hai khoá khác nhau có cùng hash vẫn được lưu riêng nếu `equals()` trả `false`.

Chi tiết bucket, cơ chế xử lý va chạm và chính sách mở rộng là vấn đề triển khai; không nên dựa vào công thức bucket cụ thể, thứ tự duyệt hay vị trí cố định của phần tử trong chương trình.

Với hàm băm phân phối hợp lý và mức tải thích hợp, thao tác thông thường của bảng băm có chi phí trung bình gần `O(1)`. Nếu quá nhiều khoá va chạm hoặc phân phối kém, chi phí xử lý trong bucket tăng; `hashCode()` không bảo đảm mỗi thao tác luôn `O(1)`.

## 3. Hợp đồng bắt buộc giữa `equals()` và `hashCode()`

Theo hợp đồng của `Object`:

- Trong một lần thực thi ứng dụng Java, khi dữ liệu dùng để xác định `equals()` của một đối tượng không thay đổi, các lần gọi `hashCode()` trên đối tượng đó phải trả về cùng kết quả.
- **Nếu `a.equals(b)` là `true`, thì `a.hashCode() == b.hashCode()` phải là `true`** (giả sử `a`, `b` khác `null`).
- Nếu `a.equals(b)` là `false`, **không có yêu cầu** hai hash code phải khác nhau. Va chạm hợp lệ.
- Hai lần chạy độc lập của cùng chương trình không nhất thiết tạo cùng hash code cho mọi đối tượng.

Vì vậy, **lớp ghi đè `equals()` cần ghi đè `hashCode()` nhất quán**. Nếu hai khoá được xem là bằng nhau nhưng cho hash khác nhau, `HashMap.get()` hoặc `HashSet.contains()` có thể không tìm thấy phần tử đã lưu.

Chiều ngược lại không đúng:

```text
a.equals(b) == true  =>  a.hashCode() == b.hashCode()
a.hashCode() == b.hashCode()  =/=>  a.equals(b) == true
```

## 4. Chương trình Java 17+ chứng minh va chạm băm

Lưu chương trình hoàn chỉnh dưới tên `HashCodeDemo.java`:

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class HashCodeDemo {
    static final class Key {
        private final String value;

        Key(String value) {
            this.value = Objects.requireNonNull(value);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Key other)) return false;
            return value.equals(other.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    public static void main(String[] args) {
        Key first = new Key("FB");
        Key second = new Key("Ea");

        Map<Key, String> map = new HashMap<>();
        map.put(first, "first");
        map.put(second, "second");

        System.out.println("hash(FB): " + first.hashCode());
        System.out.println("hash(Ea): " + second.hashCode());
        System.out.println("first.equals(second): " + first.equals(second));
        System.out.println("map.size(): " + map.size());
        System.out.println("lookup new FB: " + map.get(new Key("FB")));
        System.out.println("lookup new Ea: " + map.get(new Key("Ea")));
    }
}
```

Biên dịch và chạy:

```bash
javac HashCodeDemo.java
java HashCodeDemo
```

Kết quả chính xác:

```text
hash(FB): 2236
hash(Ea): 2236
first.equals(second): false
map.size(): 2
lookup new FB: first
lookup new Ea: second
```

Giải thích:

- Trong Java, `String.hashCode()` tính theo công thức tích luỹ `31 * h + ký_tự`. `"FB"` và `"Ea"` đều cho `2236`: `70 * 31 + 66 = 2236`, `69 * 31 + 97 = 2236`.
- Hai `Key` dùng `value.hashCode()` nên cũng cho cùng hash code. Tuy nhiên `value.equals(...)` trả `false` vì `"FB"` khác `"Ea"`.
- `HashMap` giữ **hai** khoá riêng. Một khoá `new Key("FB")` mới có cùng ý nghĩa với `first` theo `equals()` và có cùng hash code, nên tìm lại được giá trị `"first"`.
- Không có gì trong ví dụ yêu cầu biết bucket nội bộ của `HashMap`; kết quả chỉ chứng minh hash code dùng để tìm ứng viên còn `equals()` quyết định tính bằng nhau.

## 5. Thiết kế `hashCode()` cho lớp tự định nghĩa

Để giữ đúng hợp đồng, chọn các trường dữ liệu tham gia `equals()` làm cơ sở tính băm. Ví dụ dưới đây là một đoạn lớp minh hoạ độc lập về mặt cú pháp lớp (không có `main`):

```java
import java.util.Objects;

final class ProductKey {
    private final String sku;
    private final int version;

    ProductKey(String sku, int version) {
        this.sku = Objects.requireNonNull(sku);
        this.version = version;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ProductKey other)) return false;
        return version == other.version && sku.equals(other.sku);
    }

    @Override
    public int hashCode() {
        int result = sku.hashCode();
        return 31 * result + Integer.hashCode(version);
    }
}
```

Trong ví dụ, `equals()` xét `sku` và `version`; `hashCode()` cũng xét cả hai. Phép nhân/cộng `int` có thể tràn số theo quy tắc số nguyên Java, điều đó **được phép** đối với hash code. Đây là công thức kết hợp thông dụng, không phải công thức duy nhất.

Các công cụ hỗ trợ:

- `Objects.hash(sku, version)` giúp kết hợp nhiều trường; có tạo mảng varargs và thao tác đóng hộp trong một số trường hợp, vì vậy cân nhắc khi ở đường xử lý hiệu năng cao.
- `Objects.hashCode(reference)` trả về `0` nếu đối số là `null`, còn khác `null` thì gọi `reference.hashCode()`.
- Nếu trường là mảng và muốn so sánh theo **nội dung mảng**, dùng cặp `Arrays.equals(...)` / `Arrays.hashCode(...)` (hoặc biến thể sâu phù hợp với mảng lồng nhau); bản thân mảng không tự so sánh nội dung bằng `Object.equals()`.
- Java `record` tự sinh `equals()` và `hashCode()` phù hợp dựa trên các thành phần record. Có thể ưu tiên record cho đối tượng giá trị khi đáp ứng yêu cầu thiết kế.

## 6. Các lỗi phổ biến và cách tránh

| Sai lầm | Hậu quả hoặc cách hiểu đúng |
| --- | --- |
| Chỉ ghi đè `equals()`, bỏ `hashCode()` | Hai đối tượng bằng nhau có thể có hash khác nhau; tra cứu trong cấu trúc băm sai |
| Nghĩ hash trùng nghĩa là đối tượng bằng nhau | Hash trùng là va chạm bình thường; vẫn cần `equals()` để phân biệt |
| Dùng số băm làm ID toàn cục hoặc khoá cơ sở dữ liệu duy nhất | `int` hữu hạn, va chạm được phép, không bảo đảm ổn định qua nhiều lần chạy |
| Luôn trả về cùng một số (chẳng hạn `1`) | Không nhất thiết vi phạm hợp đồng nhưng làm phân phối rất kém và giảm hiệu suất |
| Thay đổi trường tham gia `equals()` / `hashCode()` sau khi đưa khoá vào `HashMap` | Khoá có thể được tìm ở bucket khác với lúc lưu, khiến `get`/`containsKey` thất bại |
| Cho rằng hash code luôn không âm | Hash code `int` có dấu; thư viện băm phải xử lý cả giá trị âm |
| Giả định thứ tự duyệt `HashMap` do hash quyết định ổn định | `HashMap` không cam kết thứ tự duyệt, kể cả sau khi thêm/xoá hoặc resize |

**Khuyến nghị:** Dùng khoá bất biến (các trường tham gia so sánh là `final` và các giá trị thành phần không bị thay đổi). Kiểm thử hai chiều: các đối tượng bằng nhau phải có cùng hash; các đối tượng khác nhau vẫn được cấu trúc băm quản lý đúng ngay cả khi vô tình trùng hash.

## 7. Tự kiểm tra kiến thức

1. Hai đối tượng khác nhau cùng trả về hash `42`. `HashSet` có bắt buộc chỉ giữ một đối tượng? **Không**; nó phải xét `equals()` để xác định phần tử bằng nhau.
2. Hai đối tượng được `equals()` xem là bằng nhau nhưng có hash khác nhau. Có hợp lệ? **Không**; đó là vi phạm hợp đồng và có thể làm hỏng logic tra cứu.
3. Có thể nhận hash âm từ một đối tượng hợp lệ? **Có**; `hashCode()` trả về kiểu `int` có dấu.
4. Có thể suy ra bucket từ chính `hashCode()` để lưu ngoài `HashMap`? **Không nên**; bucket phụ thuộc dung lượng và cách triển khai, không phải phần của hợp đồng `Object.hashCode()`.

**Ghi nhớ:** `hashCode()` trả `int` để hỗ trợ ánh xạ nhanh từ khoá tới nhóm ứng viên; `equals()` xác định khoá có thực sự bằng nhau. **Bằng nhau ⇒ cùng hash; cùng hash ⇏ bằng nhau.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-code-contract-002">Hai đối tượng equals() phải có cùng hash code</a>

<details>
<summary>Xem chi tiết</summary>

# Hai đối tượng `equals()` phải có cùng hash code

## 1. Quy tắc bắt buộc của hợp đồng `hashCode()`

Trong Java, nếu hai đối tượng được xem là bằng nhau theo `equals()` thì **giá trị `hashCode()` của chúng phải bằng nhau**. Với hai tham chiếu khác `null` là `a` và `b`:

~~~text
a.equals(b) == true  =>  a.hashCode() == b.hashCode()
~~~

Dấu `=>` thể hiện quan hệ kéo theo trong logic, không phải toán tử Java. Đây là quy tắc của **hợp đồng `Object.hashCode()`**. Nó áp dụng khi hai tham chiếu chỉ đến cùng một đối tượng, và quan trọng hơn, cả khi hai đối tượng **riêng biệt** được lớp của chúng coi là bằng nhau theo dữ liệu.

**Không được đảo chiều quy tắc:**

~~~text
a.hashCode() == b.hashCode()  không suy ra  a.equals(b) == true
~~~

Hai đối tượng có thể nhận cùng mã băm nhưng `equals()` trả về `false`. Điều này gọi là **va chạm băm** (*hash collision*) và hoàn toàn hợp lệ. `hashCode()` trả về một số nguyên `int` 32 bit, không thể gán một mã duy nhất cho tất cả giá trị và đối tượng có thể tồn tại.

Ba trường hợp cần phân biệt:

- `a.equals(b) == true`: **bắt buộc** hai mã băm bằng nhau.
- `a.equals(b) == false`: mã băm có thể bằng hoặc khác nhau.
- `a == b`: hai biến trỏ cùng một thể hiện; nếu trạng thái quyết định `equals()` không thay đổi, các lần gọi `hashCode()` phải nhất quán.

Ví dụ, nếu lớp `CustomerKey` xác định hai khách hàng bằng cùng `customerId`, hai đối tượng `new CustomerKey("C1")` riêng biệt phải trả về cùng mã băm khi `equals()` trả về `true`. Chúng không nhất thiết là cùng một đối tượng trong bộ nhớ.

## 2. Ba phần của hợp đồng `Object.hashCode()`

1. **Nhất quán trong một lần thực thi.** Gọi `hashCode()` nhiều lần trên một đối tượng phải cho cùng kết quả trong chừng mực dữ liệu tham gia `equals()` không đổi. Không có cam kết mã băm phải giữ nguyên giữa hai lần chạy ứng dụng.
2. **Bằng nhau thì cùng mã băm.** Nếu `a.equals(b)` là `true`, hai lời gọi `a.hashCode()` và `b.hashCode()` phải trả về cùng `int`.
3. **Không bằng nhau vẫn được phép trùng mã băm.** Không bắt buộc hai đối tượng khác nhau theo `equals()` phải có mã băm khác nhau. Một hàm băm phân bố tốt giúp giảm va chạm và cải thiện hiệu năng, nhưng tính duy nhất không phải yêu cầu của hợp đồng.

Bên cạnh đó, `equals()` cần tuân thủ tính **phản xạ** (`a.equals(a)`), **đối xứng** (nếu `a.equals(b)` thì `b.equals(a)`), **bắc cầu**, **nhất quán**, và `a.equals(null)` là `false` với `a` khác `null`. Mã băm chỉ có ý nghĩa ổn định khi định nghĩa “hai đối tượng bằng nhau” cũng nhất quán.

> Quy tắc thực hành: **Ghi đè `equals()` để so sánh theo giá trị thì phải ghi đè `hashCode()` tương ứng.** Dựa hai phương thức trên cùng khái niệm “bằng nhau”.

## 3. Vì sao các cấu trúc băm phụ thuộc quy tắc này?

`HashMap` và `HashSet` dùng mã băm để định hướng nơi tra cứu các khóa hoặc phần tử, rồi kiểm tra tính bằng nhau khi cần phân biệt các ứng viên. Có thể hình dung:

1. Thêm đối tượng `a`: cấu trúc tính `a.hashCode()` và chọn vùng lưu trữ ứng viên.
2. Tra cứu bằng đối tượng `b` mà `b.equals(a) == true`: cấu trúc tính `b.hashCode()` để tìm đúng nơi có ứng viên tương ứng.
3. Nếu mã băm của `a` và `b` không tương thích, phép tra cứu có thể đi sai vùng, không bao giờ so sánh hai đối tượng với nhau, dù `equals()` nói rằng chúng bằng nhau.
4. Nếu hai mã băm bằng nhau nhưng `equals()` khác nhau, cấu trúc vẫn có thể giữ hai khóa. Đây là tình huống va chạm bình thường.

Đó là mô hình khái niệm; `HashMap` có thể xử lý hoặc trộn các bit của mã băm và dùng các cấu trúc khác nhau bên trong. Hợp đồng của khóa quan trọng hơn chi tiết triển khai.

Vi phạm quy tắc bằng nhau ⇒ cùng mã băm có thể dẫn đến `HashSet.contains(b)` trả về `false` dù `a` tương đương đã được thêm, hoặc `HashMap.get(b)` không tìm được giá trị lưu dưới `a`. Không nên khẳng định một lần tra cứu cụ thể chắc chắn thất bại trên mọi JVM, bởi cách bố trí vùng băm và khả năng va chạm ảnh hưởng biểu hiện; lỗi cốt lõi là **không bảo đảm đúng hợp đồng**.

## 4. Ví dụ Java 17+ biên dịch và chạy được

Lớp `AccountId` trong ví dụ có một trường `value` bất biến. Cả `equals()` và `hashCode()` đều sử dụng cùng giá trị, nên hai đối tượng khác tham chiếu nhưng cùng tài khoản được công nhận là một khóa logic.

Tạo tệp `HashContractDemo.java`:

~~~java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class HashContractDemo {
    static final class AccountId {
        private final String value;

        AccountId(String value) {
            this.value = Objects.requireNonNull(value);
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof AccountId id
                        && value.equals(id.value));
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    public static void main(String[] args) {
        AccountId first = new AccountId("ACC-100");
        AccountId equivalent = new AccountId("ACC-100");
        AccountId different = new AccountId("ACC-200");

        System.out.println("same reference: "
                + (first == equivalent));
        System.out.println("equal values: "
                + first.equals(equivalent));
        System.out.println("equal objects have same hash: "
                + (first.hashCode() == equivalent.hashCode()));
        System.out.println("different values: "
                + first.equals(different));

        Set<AccountId> ids = new HashSet<>();
        System.out.println("add first: " + ids.add(first));
        System.out.println("contains equivalent: "
                + ids.contains(equivalent));
        System.out.println("add equivalent: "
                + ids.add(equivalent));
        System.out.println("set size: " + ids.size());

        Map<AccountId, String> owners = new HashMap<>();
        owners.put(first, "Linh");
        System.out.println("lookup with equivalent: "
                + owners.get(equivalent));

        System.out.println("Aa equals BB: " + "Aa".equals("BB"));
        System.out.println("Aa and BB same hash: "
                + ("Aa".hashCode() == "BB".hashCode()));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac HashContractDemo.java
java HashContractDemo
~~~

**Kết quả đầu ra xác định:**

~~~text
same reference: false
equal values: true
equal objects have same hash: true
different values: false
add first: true
contains equivalent: true
add equivalent: false
set size: 1
lookup with equivalent: Linh
Aa equals BB: false
Aa and BB same hash: true
~~~

**Giải thích kết quả:**

- `first` và `equivalent` là hai đối tượng từ hai lệnh `new` khác nhau, nên `first == equivalent` là `false`. Chúng mang cùng `value`, nên `first.equals(equivalent)` là `true`.
- Hai phương thức `hashCode()` dùng cùng giá trị chuỗi `"ACC-100"`; kết quả băm phải bằng nhau. Không cần in con số cụ thể để kiểm tra hợp đồng.
- `HashSet` thêm `first` thành công, tìm thấy khóa tương đương bằng `contains(equivalent)`, và từ chối thêm khóa tương đương lần nữa. Kích thước còn `1`.
- `HashMap` lấy ra `"Linh"` bằng một đối tượng khóa khác tham chiếu nhưng cùng giá trị logic.
- `"Aa"` và `"BB"` không bằng nhau theo `String.equals()`, tuy nhiên `String.hashCode()` của cả hai là `2112`. Đây là va chạm được phép, chứng minh không được suy ra `equals() == true` chỉ từ việc băm bằng nhau.

## 5. Lỗi thường gặp: ghi đè `equals()` nhưng quên `hashCode()`

Ví dụ **đoạn mã minh họa** sau chỉ ghi đè `equals()`, không phải một chương trình độc lập:

~~~java
final class BrokenKey {
    private final String code;

    BrokenKey(String code) {
        this.code = code;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof BrokenKey key
                    && java.util.Objects.equals(code, key.code));
    }

    // Thiếu hashCode(): vẫn kế thừa Object.hashCode().
}
~~~

Hai đối tượng `new BrokenKey("A")` khác nhau có `equals() == true`, nhưng `Object.hashCode()` mặc định gắn với danh tính nên **không có bảo đảm** mã băm của chúng bằng nhau. Do đó lớp này không thỏa mãn hợp đồng cần thiết của các tập hợp theo băm.

Không thể mặc định khẳng định mỗi cặp đối tượng khác nhau luôn có mã băm danh tính khác nhau; va chạm vẫn có thể xảy ra. Lỗi là việc triển khai **không bảo đảm** mệnh đề “bằng nhau thì có mã băm bằng nhau”.

Cách sửa là ghi đè thêm `hashCode()` để sử dụng cùng trường `code`:

~~~java
@Override
public int hashCode() {
    return java.util.Objects.hashCode(code);
}
~~~

Đây là **phương thức bổ sung cho lớp `BrokenKey`**, không phải mã chạy độc lập. `Objects.hashCode(code)` trả về `0` nếu `code` là `null`, và nếu khác `null` thì sử dụng `code.hashCode()`. Cơ chế này tương thích với `Objects.equals(code, key.code)` trong phương thức `equals()` bên trên.

## 6. Chọn trường nào để viết `hashCode()`?

Hãy bắt đầu từ định nghĩa `equals()`. Ví dụ, giả sử đối tượng khóa được xác định bởi **cặp** `tenantId` và `orderNumber`. Một thiết kế tương thích thường so sánh đúng hai trường trong `equals()` và kết hợp chúng trong `hashCode()`.

Đây là **cặp phương thức minh họa**, giả định nằm trong lớp `OrderKey` có hai trường nêu trên và đã import `java.util.Objects`:

~~~java
@Override
public boolean equals(Object other) {
    return this == other
            || (other instanceof OrderKey key
                && Objects.equals(tenantId, key.tenantId)
                && Objects.equals(orderNumber, key.orderNumber));
}

@Override
public int hashCode() {
    return Objects.hash(tenantId, orderNumber);
}
~~~

Vì hai khóa có cùng `tenantId` và `orderNumber` sẽ sinh cùng mã băm, điều kiện hợp đồng được đáp ứng. `Objects.hash(...)` **không bảo đảm** tạo mã khác nhau cho mọi cặp giá trị; va chạm vẫn hợp lệ.

**Một sắc thái quan trọng:** Không phải cứ bỏ bớt một trường khỏi `hashCode()` là vi phạm hợp đồng. Nếu `equals()` so sánh hai trường và `hashCode()` chỉ trả mã băm từ trường thứ nhất, mọi cặp bằng nhau vẫn có trường thứ nhất bằng nhau và vì thế có mã băm bằng nhau. Vấn đề ở đây là **phân bố băm kém**, có thể làm giảm hiệu năng. Ngược lại, nếu `hashCode()` dựa vào trường thay đổi độc lập mà `equals()` không xét, hai đối tượng bằng nhau có thể cho mã băm khác nhau: đó là lỗi đúng/sai về hợp đồng.

### Thận trọng với đối tượng có thể thay đổi

Ngay cả khi `equals()` và `hashCode()` dùng đúng các trường, việc thay đổi những trường đó khi đối tượng đang làm khóa của `HashMap` hoặc phần tử trong `HashSet` vẫn có thể gây lỗi tra cứu. Khóa được lưu theo mã băm cũ; sau khi giá trị thay đổi, tìm bằng mã băm mới có thể không tìm thấy nó.

Do đó, nên ưu tiên **khóa bất biến**: trường `final`, dữ liệu bên trong cũng không thay đổi theo cách ảnh hưởng đến `equals()`. Một trường tham chiếu khai báo `final` nhưng trỏ đến cấu trúc dữ liệu có thể sửa vẫn cần được xem xét.

### Kiểu dữ liệu có sẵn

- `String`, `Integer`, `Long` và nhiều kiểu giá trị chuẩn đã triển khai `equals()`/`hashCode()` nhất quán.
- `record` trong Java hiện đại tự sinh `equals()` và `hashCode()` dựa trên các thành phần; cần cân nhắc nếu thành phần chứa dữ liệu có thể thay đổi.
- Mảng trong Java mặc định kế thừa so sánh theo danh tính từ `Object`. Nếu muốn so sánh theo nội dung mảng, dùng cặp `Arrays.equals(...)` và `Arrays.hashCode(...)` tương ứng; với mảng lồng nhau có `Arrays.deepEquals(...)` và `Arrays.deepHashCode(...)`.
- Với lớp có kế thừa, thiết kế `equals()` giữa lớp cha và lớp con cần bảo đảm tính đối xứng và bắc cầu. Lỗi này cũng có thể ảnh hưởng đến hợp đồng `hashCode()`.

## 7. Phân biệt các nhận định dễ nhầm

| Nhận định | Kết luận | Giải thích |
| --- | --- | --- |
| `a.equals(b)` đúng ⇒ mã băm bằng nhau | **Bắt buộc** | Hợp đồng `Object.hashCode()` |
| Mã băm bằng nhau ⇒ `a.equals(b)` đúng | **Sai** | Va chạm băm được phép |
| `a.equals(b)` sai ⇒ mã băm phải khác nhau | **Sai** | Không có điều kiện bắt buộc này |
| `a == b` ⇒ mã băm nhất quán nếu dữ liệu liên quan không đổi | **Đúng** | Cùng một đối tượng |
| `a.equals(b)` đúng ⇒ `a == b` | **Sai** | Hai thể hiện riêng có thể bằng nhau theo giá trị |
| Mọi `hashCode()` trả về một hằng số là vi phạm | **Sai** | Hợp đồng có thể đúng nhưng rất nhiều va chạm |
| Ghi đè `equals()` theo giá trị rồi giữ `Object.hashCode()` luôn an toàn | **Sai** | Không bảo đảm mã băm bằng nhau cho hai thể hiện tương đương |
| Mã băm danh tính `System.identityHashCode()` phải giống nhau cho hai đối tượng `equals()` | **Sai** | Nó phản ánh danh tính, không theo tính bằng nhau của giá trị |

Hàm `hashCode()` luôn trả về `0` cho mọi đối tượng có thể đáp ứng điều kiện **bằng nhau ⇒ cùng mã băm**, nhưng sẽ phân bố rất kém và giảm hiệu năng bảng băm. Một mã băm phức tạp phân bố tốt nhưng không tương thích với `equals()` mới là lỗi hợp đồng.

## 8. Checklist triển khai và kiểm thử

1. Định nghĩa rõ “bằng nhau”: dựa trên những thuộc tính nào, có thể có hai thể hiện khác nhau nhưng tương đương hay không?
2. Triển khai `equals()` phản xạ, đối xứng, bắc cầu, nhất quán, và xử lý `null` đúng.
3. Triển khai `hashCode()` từ các giá trị tương thích với `equals()`. Với nhiều trường, cân nhắc `Objects.hash(...)` hoặc kết hợp băm có kiểm soát.
4. Tạo hai thể hiện **khác nhau về tham chiếu nhưng bằng nhau theo `equals()`**, và xác nhận hai mã băm bằng nhau.
5. Kiểm tra trường hợp hai giá trị khác nhau nhưng mã băm trùng; cấu trúc băm vẫn phải phân biệt bằng `equals()`.
6. Kiểm tra `HashSet.add`/`contains` và `HashMap.put`/`get` bằng các thể hiện khóa tương đương.
7. Giữ ổn định các trường quyết định `equals()`/`hashCode()` trong suốt thời gian khóa nằm trong cấu trúc băm.

## 9. Tự kiểm tra

**Câu 1.** Hai thể hiện `new String("java")` khác nhau có `equals()` bằng `true`. Mã băm của chúng có bắt buộc bằng nhau?

**Đáp án:** Có. `String.hashCode()` dựa trên nội dung chuỗi. Hai đối tượng vẫn có thể khác tham chiếu.

**Câu 2.** `"Aa"` và `"BB"` cùng mã băm. Có phải Java vi phạm hợp đồng?

**Đáp án:** Không. Hai chuỗi không bằng nhau, nên việc mã băm trùng hoàn toàn hợp lệ.

**Câu 3.** Nếu `equals()` chỉ so sánh `id` nhưng `hashCode()` lấy mã tạo ngẫu nhiên riêng cho từng thể hiện, điều gì sai?

**Đáp án:** Hai thể hiện có cùng `id` có thể `equals() == true` nhưng trả về mã băm khác nhau, vi phạm hợp đồng và có thể khiến phép tra cứu dựa trên băm sai.

**Câu 4.** Hai khóa khác nhau có mã băm bằng nhau có tự động bị `HashSet` gộp làm một không?

**Đáp án:** Không. Mã băm giúp định hướng tra cứu; cấu trúc còn phải xét tính bằng nhau để phân biệt các khóa va chạm.

**Ghi nhớ:** `equals()` bằng nhau **bắt buộc** `hashCode()` bằng nhau. Mã băm bằng nhau **không chứng minh** hai đối tượng bằng nhau. Thiết kế hai phương thức nhất quán là điều kiện quan trọng để dùng đúng `HashMap` và `HashSet`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-code-contract-003">Cùng hash code không suy ra equals()</a>

<details>
<summary>Xem chi tiết</summary>

# Cùng hash code không suy ra `equals()`

## Mục tiêu bài học

Trong Java, `hashCode()` và `equals(Object)` cùng tham gia xác định cách đối tượng hoạt động trong các cấu trúc dựa trên băm như `HashMap` và `HashSet`. Tuy nhiên, **hai đối tượng có cùng mã băm chưa chắc bằng nhau**. Đây là chiều suy luận rất dễ bị đảo ngược khi đọc hợp đồng `hashCode`.

Sau bài này, bạn có thể:

- Phát biểu chính xác chiều **bắt buộc** và chiều **không được suy ra** giữa `equals()` và `hashCode()`.
- Giải thích vì sao collision (đụng độ băm) là hiện tượng hợp lệ và không đồng nghĩa với lỗi.
- Dự đoán kết quả của các ví dụ `String` có cùng hash nhưng `equals() == false`.
- Hiểu vì sao `HashMap`/`HashSet` vẫn có thể chứa hai khóa hoặc phần tử khác nhau dù có cùng `hashCode()`.
- Cài đặt `equals`/`hashCode` nhất quán, nhận diện lỗi ở chiều bắt buộc và tránh các giả định sai về tính duy nhất của hash.

## 1. Hai chiều suy luận không tương đương

Hợp đồng `Object.hashCode()` quy định: **nếu hai đối tượng bằng nhau theo `equals(Object)`, chúng bắt buộc có cùng mã băm**.

Viết dưới dạng mệnh đề:

```text
a.equals(b) == true
           ↓ bắt buộc
a.hashCode() == b.hashCode()
```

Chiều ngược lại **không được bảo đảm**:

```text
a.hashCode() == b.hashCode()
           ↓ không đủ kết luận
a.equals(b) có thể true hoặc false
```

Bảng dưới đây giúp phân biệt:

| `a.equals(b)` | `a.hashCode() == b.hashCode()` | Hợp đồng |
| --- | --- | --- |
| `true` | `true` | Đúng và bắt buộc |
| `true` | `false` | **Sai:** vi phạm hợp đồng `hashCode` |
| `false` | `true` | **Hợp lệ:** hai đối tượng khác nhau nhưng trùng hash |
| `false` | `false` | Hợp lệ |

Cách nói thường dùng là **“equal objects must have equal hash codes”**, chứ không phải “equal hash codes mean equal objects”.

Điều này cũng có nghĩa là từ `a.hashCode() != b.hashCode()` có thể kết luận `a.equals(b)` **không thể** là `true` nếu cả hai lớp tuân thủ hợp đồng. Dù vậy, trong logic nghiệp vụ bạn vẫn nên dùng `equals()` khi câu hỏi thực sự là “hai đối tượng có bằng nhau hay không”.

Lưu ý điều kiện vận hành: so sánh và hash cần được xét trong trạng thái nhất quán của các đối tượng, theo hợp đồng `equals`/`hashCode`, thay vì giả định kết quả vẫn có nghĩa sau khi thay đổi các trường tham gia tính bằng nhau.

## 2. Vì sao trùng hash là điều không tránh khỏi?

`hashCode()` trả về một số kiểu `int`, tức giá trị 32-bit có dấu. Trong khi đó, số lượng chuỗi, tổ hợp trường và trạng thái đối tượng khác nhau có thể lớn hơn nhiều so với số giá trị `int` khả dụng. Vì vậy, **không thể gán một mã băm duy nhất cho mọi giá trị có thể có**.

Khi hai đối tượng khác nhau cho cùng kết quả từ hàm băm, ta gọi đó là **hash collision**. Một collision chỉ có nghĩa hai đối tượng cùng giá trị mã băm; nó **không tự chứng minh** hai đối tượng có cùng nội dung, cùng danh tính hay cùng ý nghĩa nghiệp vụ.

Ví dụ dễ hiểu: giả sử mã băm tưởng tượng chỉ là phần dư khi chia mã số khách hàng cho 10. Khách hàng số 21 và 31 đều có hash 1, nhưng họ vẫn là hai khách hàng khác nhau. Đây là ví dụ ý tưởng, không phải thuật toán của `Object.hashCode()` hay `HashMap`.

Ngay cả khi một lớp dùng hàm băm chất lượng tốt, collision vẫn có thể xảy ra. Chất lượng hash chủ yếu ảnh hưởng đến mức độ phân bố khóa và hiệu năng, **không thay đổi định nghĩa bằng nhau** của đối tượng.

## 3. Collision có thể tái hiện với `String`

`String.hashCode()` có thuật toán được đặc tả trong Java, dựa trên dãy đơn vị mã UTF-16. Hai chuỗi khác nhau hoàn toàn có thể tạo ra cùng số băm. Cặp `"FB"` và `"Ea"` là một ví dụ nổi tiếng:

```java
String first = "FB";
String second = "Ea";

System.out.println(first.hashCode());           // 2236
System.out.println(second.hashCode());          // 2236
System.out.println(first.hashCode()
        == second.hashCode());                   // true
System.out.println(first.equals(second));       // false
System.out.println(first == second);            // false
```

Đây là đoạn minh họa đặt trong một phương thức Java, không phải chương trình độc lập. `String.equals()` kiểm tra nội dung ký tự, vì thế `"FB"` và `"Ea"` không bằng nhau. Còn `==` trên hai tham chiếu hỏi có cùng đối tượng hay không; hai literal nội dung khác nhau này không trỏ cùng đối tượng. Hai kết quả hash bằng nhau **không can thiệp** vào hai phép so sánh đó.

Có thể tự tính kết quả hash theo công thức của `String` cho hai ký tự:

```text
"FB": 'F' = 70, 'B' = 66  → 70 × 31 + 66 = 2236
"Ea": 'E' = 69, 'a' = 97  → 69 × 31 + 97 = 2236
```

Hai cặp ký tự khác nhau nhưng cùng kết quả là collision hợp lệ của `String.hashCode()`. Không cần dùng `new String(...)` để tạo ví dụ này, cũng không cần dựa vào một môi trường JVM có cách phân bổ đối tượng đặc biệt.

**Điểm cần nhớ:** ngay cả mã băm của một lớp chuẩn như `String` cũng không dùng làm định danh độc nhất hay bằng chứng để khẳng định hai chuỗi bằng nhau.

## 4. Ví dụ Java 17+ hoàn chỉnh: hai đối tượng cùng hash nhưng không `equals`

Trong lớp `Customer` dưới đây, hai trường `id` và `name` cùng tham gia xác định bằng nhau. Tuy nhiên, `hashCode()` cố ý chỉ trả về mã băm của `id` để minh họa một trường hợp collision dễ dự đoán.

Đây là một cách cài đặt **hợp lệ về hợp đồng nhưng phân bố hash có thể kém** nếu nhiều khách hàng trùng `id`. Ở ứng dụng thực tế, thường nên tính hash từ đầy đủ các trường tham gia `equals` để giảm những collision không cần thiết.

Lưu tệp thành `HashCollisionDemo.java`:

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class HashCollisionDemo {
    static final class Customer {
        private final int id;
        private final String name;

        Customer(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Customer other)) {
                return false;
            }
            return id == other.id
                    && Objects.equals(name, other.name);
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    public static void main(String[] args) {
        Customer a = new Customer(7, "Lan");
        Customer b = new Customer(7, "Mai");
        Customer copyOfA = new Customer(7, "Lan");

        System.out.println("a hash: " + a.hashCode());
        System.out.println("b hash: " + b.hashCode());
        System.out.println("same hash a/b: "
                + (a.hashCode() == b.hashCode()));
        System.out.println("a equals b: " + a.equals(b));
        System.out.println("a equals copy: " + a.equals(copyOfA));
        System.out.println("same hash a/copy: "
                + (a.hashCode() == copyOfA.hashCode()));

        String first = "FB";
        String second = "Ea";
        System.out.println("string hashes: "
                + first.hashCode() + ", " + second.hashCode());
        System.out.println("string equals: " + first.equals(second));

        Set<Customer> set = new HashSet<>();
        set.add(a);
        set.add(b);
        set.add(copyOfA);
        System.out.println("set size: " + set.size());

        Map<Customer, String> map = new HashMap<>();
        map.put(a, "first");
        map.put(b, "second");
        System.out.println("map size: " + map.size());
        System.out.println("lookup by equal key: " + map.get(copyOfA));
        System.out.println("lookup colliding key: " + map.get(b));
    }
}
```

Biên dịch và chạy với JDK 17 hoặc mới hơn:

```bash
javac HashCollisionDemo.java
java HashCollisionDemo
```

Kết quả chính xác:

```text
a hash: 7
b hash: 7
same hash a/b: true
a equals b: false
a equals copy: true
same hash a/copy: true
string hashes: 2236, 2236
string equals: false
set size: 2
map size: 2
lookup by equal key: first
lookup colliding key: second
```

Giải thích:

1. `a` và `b` có cùng `id == 7` nên có cùng hash `7`, nhưng tên khác nhau, vì thế `a.equals(b)` là `false`.
2. `a` và `copyOfA` là hai đối tượng được tạo riêng. Dù `a == copyOfA` là `false`, `a.equals(copyOfA)` là `true` vì các trường tham gia `equals` có cùng giá trị; do đó hash của chúng bắt buộc bằng nhau.
3. `"FB"` và `"Ea"` có cùng hash `2236` nhưng khác nội dung nên `equals()` là `false`.
4. `HashSet` giữ `a` và `b` như hai phần tử khác nhau. `copyOfA` bằng `a` nên không tạo phần tử thứ ba.
5. `HashMap` lưu `a` và `b` như hai khóa khác nhau. Tra bằng `copyOfA` vẫn tìm được giá trị của `a` vì `copyOfA.equals(a)` là `true` và hash khớp.

Ví dụ in các phép tra cứu trực tiếp, không duyệt bộ sưu tập, nên không phụ thuộc thứ tự lặp của `HashMap` hoặc `HashSet`.

## 5. `HashMap` xử lý collision thế nào?

Có thể hình dung thao tác tra cứu bằng khóa `key` trong một cấu trúc dựa trên băm qua các bước khái niệm:

1. Lấy mã băm của khóa; cấu trúc có thể xử lý thêm các bit và ánh xạ đến vị trí lưu trữ.
2. Xác định nơi có thể chứa mục nhập cần tìm dựa vào mã băm.
3. Nếu nhiều khóa cùng rơi vào cùng vị trí, cấu trúc phải **phân biệt khóa** bằng kiểm tra tính bằng nhau, chứ không tự coi mọi khóa có cùng hash là một khóa.
4. Trả về mục nhập ứng với khóa bằng nhau, hoặc kết luận không có khóa phù hợp.

Đây là sơ đồ khái niệm, không phải mã nội bộ bắt buộc của mọi phiên bản triển khai `HashMap`:

```text
Khóa cần tra
    │
    ▼
Tính/đối chiếu hash
    │
    ▼
Xác định nhóm ứng viên
    │
    ▼
Đối chiếu khóa trong nhóm
    │
    ├─ equals == true  → cùng khóa logic
    │
    └─ equals == false → khóa khác, dù có thể cùng hash
```

Lưu ý kỹ hơn: triển khai `HashMap` của Java có thể dùng cả kiểm tra **cùng tham chiếu** (`==`) để nhận diện nhanh chính khóa đó, rồi mới gọi `equals()` khi cần. Chi tiết tổ chức bucket, danh sách hay cây là vấn đề triển khai và hiệu năng, không làm thay đổi quy tắc quan trọng: **chỉ trùng hash chưa đủ để coi hai khóa bằng nhau**.

Trong ví dụ, hai `Customer` có hash 7 vẫn cùng tồn tại trong `HashMap` vì `a.equals(b) == false`. Nếu bảng chỉ dùng hash làm bằng chứng duy nhất của bằng nhau, dữ liệu của `a` và `b` sẽ bị gộp sai.

`HashSet` dựa trên cơ chế băm và kiểm tra bằng nhau tương tự để xác định phần tử đã tồn tại. Vì vậy, hai phần tử có cùng hash nhưng `equals() == false` vẫn được thêm vào tập hợp.

## 6. Phân biệt collision hợp lệ và vi phạm hợp đồng

Hai lỗi suy luận thường bị lẫn với nhau.

### Trường hợp A — Collision hợp lệ

```text
a.equals(b) == false
a.hashCode() == b.hashCode()
```

Đây **không phải lỗi**. Bộ sưu tập vẫn phải phân biệt `a` và `b`. Số collision cao có thể ảnh hưởng hiệu năng, nhưng tự nó không chứng minh `equals()` hoặc `hashCode()` được viết sai.

### Trường hợp B — Vi phạm hợp đồng

```text
a.equals(b) == true
a.hashCode() != b.hashCode()
```

Đây là lỗi cài đặt thật sự. Khi hai khóa được đánh giá bằng nhau nhưng mã băm không khớp, cấu trúc dựa trên băm có thể tìm khóa ở những vị trí không tương thích, khiến `contains`, `get` hoặc thao tác thêm phần tử có kết quả trái mong đợi.

Ví dụ lỗi thiết kế (đoạn minh họa trong một lớp có trường `id` và `name`):

```java
@Override
public boolean equals(Object obj) {
    if (this == obj) {
        return true;
    }
    if (!(obj instanceof Customer other)) {
        return false;
    }
    return id == other.id; // Chỉ id quyết định bằng nhau
}

@Override
public int hashCode() {
    return Objects.hashCode(name); // SAI: lại phụ thuộc name
}
```

Đoạn trên không phải chương trình độc lập. Nó chỉ ra một sai lầm: nếu hai `Customer` có cùng `id` nhưng `name` khác nhau, `equals()` cho `true` trong khi `hashCode()` có thể khác. Tham khảo như một **phản ví dụ**, không sao chép để dùng trong ứng dụng.

Trong một triển khai hợp lệ cho trường hợp chỉ `id` xác định tính bằng nhau, nên tính `hashCode` dựa trên `id`, chẳng hạn `Integer.hashCode(id)`. Khi cả `id` và `name` quyết định `equals`, cách tính `Objects.hash(id, name)` là lựa chọn tự nhiên hơn so với hash chỉ từ `id`.

## 7. Những yêu cầu thật sự của `hashCode()`

Ngoài chiều bắt buộc “`equals` bằng nhau ⇒ hash bằng nhau”, hợp đồng `hashCode()` còn yêu cầu:

- Với cùng đối tượng, khi không có thông tin nào dùng cho phép so sánh `equals` thay đổi, các lần gọi `hashCode()` trong một lần thực thi chương trình phải trả về giá trị nhất quán. Giá trị không bắt buộc giống nhau giữa các lần chạy riêng biệt của ứng dụng.
- Nếu `a.equals(b) == true`, chúng phải có cùng `hashCode()`.
- Nếu `a.equals(b) == false`, không bắt buộc mã băm khác nhau. **Phân biệt được nhiều hash hơn có thể giúp hiệu năng tốt hơn**, nhưng không phải điều kiện đúng/sai của hợp đồng.

Vì vậy, một cài đặt đơn giản như:

```java
@Override
public int hashCode() {
    return 1;
}
```

vẫn có thể **đúng hợp đồng** khi kết hợp với `equals()` hợp lệ và ổn định: mọi đối tượng đều có cùng hash, kể cả các đối tượng bằng nhau. Tuy nhiên, đây gần như luôn là một **lựa chọn hiệu năng tệ** khi nhiều đối tượng khác nhau cùng được đưa vào `HashMap`/`HashSet` vì tạo collision liên tục. Mục đích ví dụ là chứng minh “hash khác nhau” không phải yêu cầu cho các đối tượng không bằng nhau.

Ngược lại, `hashCode()` biến thiên ngẫu nhiên giữa những lần gọi trên cùng đối tượng không còn tuân thủ hợp đồng nhất quán và có thể làm bộ sưu tập băm hoạt động sai.

## 8. Nên dùng trường nào khi cài đặt?

Với lớp giá trị đơn giản có các trường bất biến:

```java
private final int id;
private final String name;

@Override
public boolean equals(Object obj) {
    if (this == obj) {
        return true;
    }
    if (!(obj instanceof Customer other)) {
        return false;
    }
    return id == other.id && Objects.equals(name, other.name);
}

@Override
public int hashCode() {
    return Objects.hash(id, name);
}
```

Đây là các thành viên minh họa bên trong lớp `Customer`; `Objects` cần import `java.util.Objects` và mẫu `instanceof` sử dụng cú pháp được hỗ trợ trong Java 17.

**Quy tắc thực hành:** xác định trước tập trường nào tham gia `equals()` rồi tính hash theo cùng thông tin quan trọng ấy. `Objects.hash(id, name)` có lợi thế dễ đọc và giúp bảo đảm các đối tượng bằng nhau nhận cùng hash khi dữ liệu trường và hợp đồng của chúng đúng. Nó **không hứa hẹn** rằng các đối tượng khác nhau sẽ có hash khác nhau; collision vẫn có thể xảy ra.

Không cần các hàm `equals` và `hashCode` phải có cùng số phép so sánh hay tính toán. Điều quan trọng là mối quan hệ kết quả của chúng đúng. Trong ví dụ `Customer` đầu tiên, `equals` dùng cả `id` và `name` nhưng `hashCode` chỉ dùng `id` vẫn hợp lệ, vì khi hai đối tượng bằng nhau thì chắc chắn chúng có cùng `id`. Việc chỉ dùng ít trường hơn có thể làm tăng collision.

Không sử dụng `System.identityHashCode(obj)` để thay cho `hashCode()` theo nội dung ở lớp giá trị có `equals` tùy chỉnh: mã băm danh tính không được thiết kế để bảo đảm hai đối tượng có nội dung bằng nhau sẽ có hash giống nhau. Ngay cả mã băm danh tính cũng không được bảo đảm là duy nhất cho mỗi đối tượng.

### Trường dùng làm khóa cần ổn định

Nếu dùng đối tượng làm khóa của `HashMap` hoặc phần tử `HashSet`, cần đặc biệt thận trọng với những trường có thể sửa đổi mà lại tham gia `equals`/`hashCode`.

Giả sử một khóa được thêm vào `HashSet` khi có hash là 7. Sau đó chương trình sửa dữ liệu làm hash của chính khóa đó thành 8; thao tác `contains` có thể tra ở nhóm khác và không tìm được phần tử đang lưu. Đây là lỗi do **thay đổi trạng thái liên quan đến hash sau khi chèn**, không phải collision.

Ưu tiên đối tượng khóa bất biến hoặc quản lý vòng đời khóa sao cho các trường quyết định bằng nhau và hash không thay đổi trong lúc khóa nằm trong bộ sưu tập băm.

## 9. Những nhầm lẫn dễ gặp

1. **“Cùng hash là cùng đối tượng.”** Sai: `hashCode()` không phải địa chỉ hay ID duy nhất. Hai đối tượng khác nhau có thể nhận cùng hash.
2. **“Cùng hash thì `equals` luôn trả về `true`.”** Sai: `"FB"` và `"Ea"` đều có hash `2236` nhưng `String.equals` trả về `false`.
3. **“Hash collision làm `HashMap` ghi đè nhầm khóa.”** Sai đối với một `HashMap` hoạt động đúng và các khóa tuân thủ hợp đồng. Hai khóa khác nhau theo `equals` có thể cùng hash mà vẫn là hai mục nhập độc lập.
4. **“Hai đối tượng không bằng nhau bắt buộc hash khác nhau.”** Không có yêu cầu này trong hợp đồng.
5. **“Miễn `equals` đúng thì không cần quan tâm `hashCode`.”** Sai: `equals` bằng nhau bắt buộc kéo theo hash bằng nhau, đặc biệt khi làm việc với bộ sưu tập băm.
6. **“Tất cả khóa cùng hash nghĩa là chương trình sai.”** Không nhất thiết sai về logic, nhưng phân bố hash quá kém có thể làm giảm hiệu năng.
7. **“Một lần kiểm tra `hashCode` bằng nhau là đủ thay cho `equals`.”** Sai. Chỉ xem hash để thu hẹp ứng viên; dùng phép so sánh logic thích hợp để kết luận bằng nhau.
8. **“`Objects.hash(...)` tạo mã băm không bao giờ trùng.”** Sai. Đây là cách tiện lợi để xây dựng hash từ các trường, vẫn trả về một `int` với số lượng kết quả hữu hạn.

## 10. Tự kiểm tra

Hãy cho biết từng nhận định là **đúng** hay **sai**, rồi đối chiếu giải thích:

| Nhận định | Đáp án | Giải thích |
| --- | --- | --- |
| Nếu `x.equals(y)` là `true` thì `x.hashCode() == y.hashCode()`. | Đúng | Đây là chiều bắt buộc của hợp đồng. |
| Nếu `x.hashCode() == y.hashCode()` thì `x.equals(y)` phải là `true`. | Sai | Collision có thể xảy ra. |
| Nếu `x.hashCode() != y.hashCode()` thì `x.equals(y)` có thể `true` trong một lớp tuân thủ hợp đồng. | Sai | Bằng nhau phải kéo theo hash bằng nhau. |
| Hai `Customer` có cùng `id` nhưng khác `name` vẫn có thể cùng tồn tại trong `HashSet`, khi `equals` so sánh cả hai trường. | Đúng | Hash có thể trùng; `equals` phân biệt phần tử. |
| Ghi đè `hashCode()` luôn trả về 1 chắc chắn làm `equals()` sai. | Sai | Hash cố định vẫn đáp ứng chiều bắt buộc nhưng phân bố rất kém. |
| Dùng `Objects.hash(id, name)` bảo đảm mọi cặp `Customer` khác nhau đều có hash khác nhau. | Sai | Không có bảo đảm không collision. |

Thử tự sửa ví dụ `HashCollisionDemo`: đổi `hashCode()` của `Customer` sang `Objects.hash(id, name)` và chạy lại. Khi đó `a` và `copyOfA` vẫn phải cho hash bằng nhau; `a` và `b` có thể cho hash khác nhau. **Không lấy việc hash khác nhau của cặp cuối làm điều kiện đúng đắn**: phép `equals` và hợp đồng mới là nền tảng.

## Ghi nhớ

**`equals(a, b) == true` bắt buộc dẫn đến cùng `hashCode`; cùng `hashCode` không thể đảo ngược để suy ra `equals(a, b) == true`.** Collision là hiện tượng bình thường của hàm băm trả về `int`. `HashMap` và `HashSet` có cơ chế phân biệt những khóa/phần tử khác nhau dù chúng chung mã băm, miễn hợp đồng `equals`/`hashCode` của khóa được tuân thủ. Khi cần kết luận bằng nhau theo dữ liệu, hãy dựa trên `equals()` và hợp đồng của kiểu, không coi hash như danh tính hoặc dấu vân tay duy nhất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-code-contract-004">Đối tượng không bằng nhau vẫn được phép trùng hash code</a>

<details>
<summary>Xem chi tiết</summary>

# Đối tượng không bằng nhau vẫn được phép trùng hash code

Trong Java, `hashCode()` trả về một số nguyên kiểu `int` dùng để hỗ trợ các cấu trúc tra cứu dựa trên băm, chẳng hạn `HashMap` và `HashSet`. **Hash code không phải mã định danh duy nhất của một đối tượng.** Hai đối tượng **không bằng nhau theo `equals`** vẫn hoàn toàn có thể nhận **cùng một hash code**. Hiện tượng này gọi là **hash collision** (va chạm băm).

Điều bắt buộc của hợp đồng `hashCode` là chiều ngược lại: nếu `x.equals(y)` trả `true`, thì `x.hashCode()` **phải bằng** `y.hashCode()`. Phân biệt chính xác hai chiều suy luận này là điều kiện để cài đặt `equals`, `hashCode` và sử dụng Java collections đúng.

## 1. Hợp đồng `hashCode`: yêu cầu gì và không yêu cầu gì?

Với hai đối tượng không `null` là `x` và `y`, điều kiện thiết yếu là:

~~~text
x.equals(y) == true  =>  x.hashCode() == y.hashCode()
~~~

Đó là **mệnh đề một chiều**. Bảng sau phân biệt các trường hợp:

| `x.equals(y)` | `x.hashCode() == y.hashCode()` | Có hợp lệ không? | Ý nghĩa |
| --- | --- | --- | --- |
| `true` | `true` | **Bắt buộc** | Bằng nhau theo `equals` thì phải cùng hash code. |
| `true` | `false` | **Sai hợp đồng** | Hai giá trị bằng nhau lại có mã băm khác nhau. |
| `false` | `true` | **Hợp lệ** | Hai giá trị khác nhau trùng hash code: một collision. |
| `false` | `false` | **Hợp lệ** | Hai giá trị khác nhau và có hash code khác nhau. |

Không được suy luận:

~~~text
x.hashCode() == y.hashCode()  =>  x.equals(y)
~~~

Suy luận này **sai**. Hash bằng nhau chỉ nói rằng hai đối tượng có cùng kết quả của hàm băm, **không** xác nhận chúng bằng nhau theo nghiệp vụ hay có cùng danh tính.

Bên cạnh đó, hợp đồng `hashCode` còn yêu cầu nhiều lần gọi `hashCode()` trên một đối tượng trong cùng một lần chạy Java phải cho cùng kết quả **nếu thông tin dùng trong phép so sánh `equals` không bị thay đổi**. Java không yêu cầu một đối tượng luôn có cùng hash code giữa các lần chạy JVM khác nhau; một số kiểu như `String` có thuật toán hash được xác định, nhưng không nên suy rộng bảo đảm đó sang mọi lớp.

### Vì sao va chạm băm là điều không thể tránh nói chung?

`hashCode()` trả về một `int` 32 bit, tức chỉ có **2³² giá trị bit khác nhau**. Trong khi đó số lượng giá trị hay trạng thái đối tượng có thể lớn hơn rất nhiều. Khi ánh xạ nhiều giá trị đầu vào hơn số kết quả có thể có vào một số nguyên 32 bit, theo nguyên lý chuồng bồ câu, ít nhất hai giá trị đầu vào sẽ phải cùng hash code.

Vì thế, **hàm băm tốt nhằm phân bố giá trị hiệu quả và nhất quán với `equals`**, chứ không thể hứa rằng mọi đối tượng không bằng nhau đều nhận một hash khác nhau.

## 2. Ví dụ xác định được: `"Aa"` và `"BB"`

Java `String.hashCode()` sử dụng phép tích lũy theo hệ số 31 trên các giá trị `char` UTF-16. Với hai chuỗi gồm hai ký tự:

~~~text
"Aa": 31 × 65 + 97 = 2112
"BB": 31 × 66 + 66 = 2112
~~~

Dù cùng mã băm `2112`, `"Aa".equals("BB")` vẫn là `false` vì nội dung chuỗi khác nhau.

Tệp **`StringCollisionDemo.java`** (Java 17+):

~~~java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StringCollisionDemo {
    public static void main(String[] args) {
        String first = "Aa";
        String second = "BB";

        System.out.println(first.equals(second));
        System.out.println(first.hashCode());
        System.out.println(second.hashCode());
        System.out.println(first.hashCode() == second.hashCode());

        Set<String> unique = new HashSet<>();
        unique.add(first);
        unique.add(second);
        unique.add(new String("Aa"));
        System.out.println(unique.size());
        System.out.println(unique.contains(new String("BB")));

        Map<String, Integer> scores = new HashMap<>();
        scores.put(first, 10);
        scores.put(second, 20);
        System.out.println(scores.size());
        System.out.println(scores.get("Aa"));
        System.out.println(scores.get("BB"));

        scores.put(new String("Aa"), 99);
        System.out.println(scores.size());
        System.out.println(scores.get("Aa"));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac StringCollisionDemo.java
java StringCollisionDemo
~~~

Kết quả chính xác:

~~~text
false
2112
2112
true
2
true
2
10
20
2
99
~~~

Phân tích:

1. `first.equals(second)` là `false`: hai chuỗi có nội dung khác nhau.
2. Hai lệnh `hashCode()` đều trả `2112`: đã xuất hiện **collision**.
3. `HashSet` nhận cả `"Aa"` và `"BB"`, vì chúng **không bằng nhau theo `equals`**; số phần tử sau hai lần thêm là hai. Thêm `new String("Aa")` không tạo giá trị phân biệt mới theo `String.equals`, nên kích thước vẫn là `2`.
4. `HashSet.contains(new String("BB"))` là `true`: tuy tham chiếu khác, đối tượng truy vấn bằng `"BB"` theo `equals`.
5. `HashMap` giữ hai ánh xạ riêng `"Aa" → 10` và `"BB" → 20`. Chúng cùng hash nhưng **khác key** theo `equals`; một entry không ghi đè entry kia.
6. Đặt lại giá trị với `new String("Aa")` thay thế giá trị của key bằng `"Aa"`, nên kích thước map vẫn là `2`, và `get("Aa")` trả `99`.

Ví dụ cố ý in ra kích thước, kiểm tra chứa và tra cứu theo key. **Không in toàn bộ `HashSet`/`HashMap` để suy luận thứ tự**, bởi thứ tự duyệt của các cấu trúc này không được hợp đồng API đảm bảo.

## 3. `HashSet` và `HashMap` xử lý collision như thế nào?

Một cách hiểu thực dụng về tra cứu dựa trên hash:

1. Collection lấy `hashCode()` của key hoặc phần tử.
2. Nó sử dụng thông tin hash để xác định một vị trí/nhóm ứng viên trong cấu trúc nội bộ.
3. Nếu có nhiều ứng viên trùng vị trí, nó vẫn phải phân biệt **key nào bằng nhau theo `equals`**.
4. Nếu hai key bằng nhau, `HashMap.put` cập nhật ánh xạ hiện có và `HashSet.add` không bổ sung phần tử mới. Nếu hai key không bằng nhau, cả hai có thể tồn tại đồng thời dù cùng hash code.

Có thể hình dung khái niệm (đây là mô hình giải thích, không phải mô tả chính xác từng bước cài đặt của mọi phiên bản JVM):

~~~text
                       hashCode()
                     /            \
               giá trị A        giá trị B
                     \            /
                 Cùng nhóm băm
                   /          \
               equals?       equals?
               /    \         /    \
             true  false    true   false
             cùng  khác     cùng   khác
             key   key      key    key
~~~

**Va chạm hash code** nghĩa là hai đối tượng có cùng giá trị `hashCode()`. Còn **va chạm bucket** nghĩa là chúng được ánh xạ vào cùng vùng lưu trữ nội bộ. Do bảng băm có số bucket giới hạn, hai hash code **khác nhau** vẫn có thể rơi vào cùng bucket. Đừng đánh đồng hai trường hợp; Java có thể trộn bit và thay đổi cách tổ chức bucket tùy phiên bản/điều kiện.

Các triển khai `HashMap` hiện đại có cơ chế quản lý các nhóm va chạm, trong một số tình huống có thể chuyển cấu trúc danh sách trong bucket sang cây. Đó là **chi tiết triển khai**, không phải cam kết rằng mọi va chạm luôn được xử lý trong một số phép so sánh cố định. Chất lượng phân bố hash vẫn ảnh hưởng hiệu năng.

### Hai ý cần nhớ với collection

- **Tính đúng:** cùng hash code **không đủ để kết luận trùng key**. Collection còn cần áp dụng quy tắc bằng nhau của key.
- **Hiệu năng:** nhiều key khác nhau bị dồn vào cùng nhóm có thể làm tăng số lần kiểm tra hoặc chi phí tra cứu. Mã băm có phân bố tốt thường giúp `HashMap`/`HashSet` hoạt động hiệu quả hơn.

Vì vậy, một collision riêng lẻ **không phải bug**. Bug xuất hiện khi `equals`/`hashCode` vi phạm hợp đồng, hoặc lập trình viên dùng hash như phép kiểm tra bằng nhau duy nhất.

## 4. Ví dụ lớp tự định nghĩa: hash cố tình trùng nhưng `equals` vẫn đúng

Để thấy tính hợp lệ của collision không phụ thuộc `String`, ta tạo một value object `TicketKey`. Hai ticket bằng nhau khi có cùng `code`. Ví dụ sau cố ý trả cùng mã băm `1` cho **mọi** `TicketKey`.

Tệp **`ConstantHashDemo.java`**:

~~~java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ConstantHashDemo {
    static final class TicketKey {
        private final String code;

        TicketKey(String code) {
            this.code = Objects.requireNonNull(code);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TicketKey that)) {
                return false;
            }
            return code.equals(that.code);
        }

        @Override
        public int hashCode() {
            // Chỉ để minh họa collision: phân bố rất kém.
            return 1;
        }
    }

    public static void main(String[] args) {
        TicketKey a = new TicketKey("A");
        TicketKey sameValue = new TicketKey("A");
        TicketKey b = new TicketKey("B");

        System.out.println(a == sameValue);
        System.out.println(a.equals(sameValue));
        System.out.println(a.equals(b));
        System.out.println(a.hashCode() == b.hashCode());

        Set<TicketKey> tickets = new HashSet<>();
        tickets.add(a);
        tickets.add(sameValue);
        tickets.add(b);

        System.out.println(tickets.size());
        System.out.println(tickets.contains(new TicketKey("B")));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ConstantHashDemo.java
java ConstantHashDemo
~~~

Kết quả chính xác:

~~~text
false
true
false
true
2
true
~~~

`a` và `sameValue` là hai instance nhưng bằng nhau theo `equals`, và cùng hash code `1`: **đúng hợp đồng**. `a` và `b` không bằng nhau nhưng cùng hash code `1`: **cũng đúng hợp đồng**. `HashSet` vẫn phân biệt hai giá trị `"A"` và `"B"`; không cần hash code phải khác nhau để lưu hai phần tử.

Tuy nhiên, **không nên dùng hash hằng số trong ứng dụng thực tế**, vì dồn mọi key vào cùng nhóm băm làm giảm hiệu quả tra cứu khi có nhiều phần tử. Cách triển khai phù hợp hơn với định nghĩa của `TicketKey` là:

~~~java
@Override
public int hashCode() {
    return code.hashCode();
}
~~~

Do constructor đã chặn `null` và `code` là `String` bất biến, cách viết trên dễ hiểu, ổn định và tương ứng với `equals` dùng `code.equals(that.code)`. Nhưng **ngay cả hàm băm tốt hơn cũng không đảm bảo không có collision**: `"Aa"` và `"BB"` vẫn là minh chứng.

## 5. Phân biệt hash code, bằng nhau về giá trị và danh tính

| Khái niệm | Cách kiểm tra/thể hiện | Có đảm bảo duy nhất không? |
| --- | --- | --- |
| Cùng instance | `x == y` với hai tham chiếu | Xác định hai biến có trỏ tới đúng cùng một đối tượng tại thời điểm so sánh. |
| Bằng nhau theo định nghĩa của kiểu | `x.equals(y)`, hoặc `Objects.equals(x, y)` khi cần xử lý `null` | Phụ thuộc cách triển khai `equals` của kiểu. |
| Cùng hash code | `x.hashCode() == y.hashCode()` | **Không.** Hai đối tượng không bằng nhau vẫn có thể trùng hash. |
| Mã băm theo danh tính | `System.identityHashCode(x)` | **Không.** Không phải định danh duy nhất và không phải địa chỉ bộ nhớ được đảm bảo. |

`Object.equals` mặc định so danh tính. Một lớp không override `equals` có thể coi hai instance chứa cùng các trường là khác nhau; đó là ngữ nghĩa mặc định, không tự động là lỗi. Khi muốn giá trị bằng nhau, hãy định nghĩa `equals` cho đúng kiểu và duy trì `hashCode` tương ứng. Ngay cả khi có `equals` đúng, không được xem kết quả `hashCode` như một ID ổn định dùng để nhận dạng đối tượng xuyên suốt các lần chạy.

Các hàm như `Objects.hash(...)`, `Arrays.hashCode(...)` hay `String.hashCode()` đều vẫn trả về `int`; chúng **không loại bỏ khả năng collision**. Chúng hỗ trợ tính nhất quán, khả năng bảo trì và phân bố hash phù hợp với dữ liệu, không thay thế `equals`.

## 6. Lỗi thường gặp

### Lỗi 1: Coi hai hash giống nhau là hai giá trị giống nhau

~~~java
// SAI: collision làm hai chuỗi khác nhau bị xem là bằng nhau.
boolean same = first.hashCode() == second.hashCode();

// ĐÚNG với String: so nội dung bằng equals.
boolean sameValue = first.equals(second);
~~~

Nếu hai giá trị thật sự bằng nhau, hash phải giống nhau; nhưng từ hash giống nhau **không thể suy ngược** rằng hai giá trị bằng nhau. Đặc biệt, không nên chỉ so hash khi viết `equals`, lọc trùng dữ liệu hay xác định cache entry duy nhất.

### Lỗi 2: Nghĩ rằng `HashSet` sẽ tự gộp mọi key cùng hash

`HashSet` loại bỏ các phần tử trùng **theo `equals`**, không phải trùng hash. Trong ví dụ đầu, `"Aa"` và `"BB"` đều được giữ. Nếu hai đối tượng thực sự bằng nhau nhưng có hash code khác nhau do cài đặt lỗi, tập hợp có thể hoạt động sai kỳ vọng.

### Lỗi 3: Override `equals` nhưng bỏ quên `hashCode`

~~~java
// Cài equals theo code nhưng giữ hashCode kế thừa từ Object:
// hai instance có cùng code có thể có hash khác nhau.
@Override
public boolean equals(Object other) {
    return this == other ||
           (other instanceof TicketKey that && code.equals(that.code));
}
~~~

Đoạn trên chỉ minh họa **lỗi thiếu override `hashCode`**, không phải một định nghĩa lớp hoàn chỉnh. Nếu lập trình viên triển khai `equals` dựa trên `code` như vậy, họ **phải** triển khai `hashCode` tương ứng. Chỉ làm một nửa có thể khiến `HashMap.get` không tìm thấy key bằng nhau hoặc `HashSet` nhận phần tử trùng giá trị.

### Lỗi 4: Dùng đối tượng mutable làm key rồi thay đổi dữ liệu so sánh

Nếu một trường tham gia `equals` và `hashCode` thay đổi sau khi đối tượng đã được đưa vào `HashMap`/`HashSet`, vị trí lưu trữ có thể không còn phù hợp với mã băm mới. Khi đó `contains`, `get`, `remove` có thể không hoạt động như mong muốn. Với key/value object, ưu tiên các trường bất biến và sao chép bảo vệ mảng hoặc collection mutable nếu chúng tham gia so sánh.

### Lỗi 5: Cho rằng “hash càng lớn” hoặc “không trùng trong dữ liệu thử” là đủ

Giá trị hash thuộc miền `int`, có thể âm, bằng 0 hoặc dương; độ lớn tuyệt đối không cho biết chất lượng. Một bộ test nhỏ không gặp collision cũng **không chứng minh** hàm băm tránh được collision. Điều nên đánh giá là sự nhất quán với `equals`, khả năng phân bố dữ liệu thực tế và hiệu năng phù hợp.

## 7. Thiết kế `hashCode` sao cho đúng và hữu ích

Khi cần viết `equals`/`hashCode` cho một lớp biểu diễn giá trị, có thể thực hiện theo trình tự:

1. Xác định **các trường quyết định sự bằng nhau**; nếu chúng khác nhau theo nghiệp vụ thì `equals` phải trả `false`.
2. Dùng cùng thông tin đó để tính `hashCode`. Có thể dùng `Objects.hash(a, b)` cho các trường thông thường, `Arrays.hashCode(array)` hoặc `Arrays.deepHashCode(array)` cho mảng, tùy cách `equals` so mảng.
3. Đảm bảo **hai đối tượng được `equals` coi là bằng nhau luôn cùng hash**, kể cả chúng là hai instance khác nhau.
4. Chấp nhận rằng **hai đối tượng khác nhau có thể cùng hash**; mục tiêu hợp lý là phân bố tốt, chứ không cần chứng minh hash duy nhất.
5. Giữ dữ liệu ảnh hưởng đến hash ổn định khi đối tượng là key trong hash collection.
6. Kiểm tra bằng `HashSet`/`HashMap` với ba ca: hai instance bằng nhau, hai instance khác nhau nhưng hash trùng, và hai instance khác nhau có hash khác. Đặc biệt kiểm tra việc thêm, tra cứu và cập nhật key.

Ví dụ một lớp `Point(int x, int y)` dùng cả `x` và `y` để xác định bằng nhau có thể viết:

~~~java
record Point(int x, int y) {}
~~~

Record Java sinh `equals` và `hashCode` nhất quán dựa trên các thành phần; nhưng nó **không cam kết mã băm duy nhất cho từng cặp tọa độ**. Ngoài ra, nếu record chứa thành phần kiểu mảng, phép so sánh được sinh cho thành phần mảng không tự trở thành so sánh sâu theo nội dung; cần thiết kế riêng nếu ngữ nghĩa giá trị cần điều đó.

## 8. Câu hỏi tự kiểm tra

1. `"Aa".equals("BB")` là `false` nhưng hai chuỗi có `hashCode() == 2112`. Điều này vi phạm hợp đồng Java không? Giải thích bằng mệnh đề một chiều.
2. Nếu đưa cả `"Aa"` và `"BB"` vào `HashSet<String>`, tập có bao nhiêu phần tử? Vì sao?
3. Nếu dùng hai key không bằng nhau nhưng cùng hash code trong `HashMap`, việc gọi `put` với key thứ hai có bắt buộc ghi đè giá trị của key thứ nhất không?
4. Một lớp có `equals` theo `id` nhưng `hashCode` trả hằng số `7` cho mọi instance. Hợp đồng có bị vi phạm không? Vấn đề chính của cách này là gì?
5. Một lớp có `equals` theo `id` nhưng kế thừa `Object.hashCode`. Tại sao hai instance có `id` giống nhau có thể gây lỗi khi được dùng làm key của `HashMap`?
6. Vì sao kiểm tra `hashCode` giống nhau không thể thay thế việc so sánh `equals` khi kiểm tra hai đối tượng có cùng giá trị?

**Ghi nhớ:** **Bằng nhau ⇒ cùng hash code** là quy tắc bắt buộc. **Cùng hash code ⇏ bằng nhau** vì collision được phép và thường không thể loại bỏ hoàn toàn. `HashMap` và `HashSet` vẫn phải phân biệt key theo `equals`; nhiệm vụ của `hashCode` là hỗ trợ tra cứu hiệu quả, không cấp danh tính duy nhất cho từng đối tượng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-code-contract-005">Tính ổn định trong một lần chạy khi thông tin dùng trong equals() không đổi</a>

<details>
<summary>Xem chi tiết</summary>

# Tính ổn định của `hashCode()` trong một lần chạy khi thông tin dùng trong `equals()` không đổi

## 1. Quy tắc chính xác của hợp đồng `hashCode()`

Trong Java, phương thức `Object.hashCode()` trả về một số nguyên kiểu `int` dùng chủ yếu để hỗ trợ các cấu trúc dữ liệu dựa trên bảng băm, chẳng hạn `HashMap` và `HashSet`. Một yêu cầu cốt lõi của hợp đồng là:

> Trong một lần thực thi ứng dụng Java, nếu không có thông tin nào được dùng trong các phép so sánh `equals()` trên đối tượng bị thay đổi, thì mỗi lần gọi `hashCode()` trên đối tượng đó phải nhất quán trả về cùng một số nguyên.

Có thể diễn đạt bằng điều kiện:

```text
Nếu thông tin quyết định equals(x, ...) không đổi trong khoảng T,
thì mọi lần gọi x.hashCode() trong khoảng T phải có cùng kết quả.
```

Đây là **tính ổn định có điều kiện**, không phải lời hứa rằng mã băm không bao giờ đổi trong suốt vòng đời đối tượng.

Ví dụ với đối tượng đại diện cho khách hàng có `customerId` quyết định `equals()`:

- Nếu `customerId` không thay đổi, `hashCode()` phải giữ nguyên trong cùng lần chạy.
- Nếu `customerId` **thực sự thay đổi**, hợp đồng nhất quán nói trên không bắt buộc giá trị băm cũ phải giữ nguyên; tuy nhiên, việc thay đổi khóa sau khi đưa vào bảng băm có thể khiến tra cứu không tìm thấy.
- Nếu đổi `displayName` nhưng `equals()` **không** dùng `displayName`, thì chỉ việc đổi tên hiển thị không phải lý do hợp lệ để `hashCode()` đổi.

Cụm **“trong một lần chạy”** rất quan trọng: hợp đồng Java không yêu cầu `hashCode()` của cùng một đối tượng, hoặc cùng một giá trị tái tạo, phải cho một số nguyên giống nhau giữa các lần khởi động JVM khác nhau. Đừng lưu `hashCode()` như mã định danh bền vững trong database, URL hoặc file để sử dụng ở lần chạy sau.

## 2. Quan hệ với `equals()`

Tính ổn định chỉ là **một** phần của hợp đồng `hashCode()`. Phần còn lại liên quan trực tiếp đến `equals()`:

1. **Nhất quán theo thời gian có điều kiện:** nếu thông tin dùng để so sánh bằng nhau không đổi, nhiều lần gọi `x.hashCode()` trong cùng lần chạy phải cho cùng số nguyên.
2. **Các đối tượng bằng nhau phải có cùng mã băm:** nếu `x.equals(y)` là `true`, thì `x.hashCode() == y.hashCode()` phải là `true`.
3. **Các đối tượng không bằng nhau được phép trùng mã băm:** khi `x.equals(y)` là `false`, hai mã băm có thể khác **hoặc giống nhau**. Trùng mã băm là *collision*, không tự nó vi phạm hợp đồng.

Ví dụ: `hashCode()` luôn trả về `7` là ổn định và bảo đảm các đối tượng bằng nhau có cùng mã băm. Nhưng cách này làm mọi đối tượng rơi vào cùng giá trị băm, có thể khiến bảng băm kém hiệu quả. Vì vậy cần phân biệt **đúng hợp đồng** với **chất lượng phân bố mã băm**.

Một lớp override `equals()` theo giá trị thì phải override `hashCode()` bằng những thông tin phù hợp với tiêu chí bình đẳng ấy. Tránh dùng `System.identityHashCode(this)` khi `equals()` đã định nghĩa hai đối tượng *khác tham chiếu* là bằng nhau: mã băm danh tính không bảo đảm bằng nhau cho hai đối tượng cùng giá trị.

## 3. `hashCode()` ổn định phụ thuộc vào những gì?

Xét lớp `AccountKey` có `tenantId`, `accountId` và `description`:

- Nếu `equals()` chỉ so sánh `tenantId` và `accountId`, đây là các thông tin xác định bình đẳng.
- Một cách cài đặt `hashCode()` phù hợp là tính từ **cùng bộ khóa logic** `tenantId` và `accountId`.
- `description` được dùng để hiển thị, không tham gia `equals()`; thay đổi nó không được làm mã băm của khóa thay đổi nếu thông tin bình đẳng vẫn như cũ.

“Dùng cùng bộ khóa logic” là nguyên tắc thiết kế thực tế, không phải yêu cầu rằng công thức trong `hashCode()` phải gọi đúng từng getter mà `equals()` gọi. Điều bắt buộc là **bất kỳ hai đối tượng được `equals()` xem là bằng nhau đều có cùng mã băm**, và điều kiện ổn định ở mục 1 được bảo toàn.

Hợp đồng không yêu cầu kết quả băm có tính ngẫu nhiên, phải dương, duy nhất, hoặc phản ánh địa chỉ bộ nhớ. `int` âm, `0` hay collision đều hợp lệ.

## 4. Ví dụ chạy được với Java 17

Chương trình sau minh họa ba trường hợp có tính chất khác nhau:

- `StableKey`: tiêu chí bình đẳng bất biến, mã băm ổn định.
- `BrokenHashKey`: dữ liệu bình đẳng không đổi nhưng mỗi lần băm lại trả về một số khác, **vi phạm hợp đồng**.
- `MutableKey`: công thức `equals()`/`hashCode()` nhất quán, nhưng trường thuộc khóa bị sửa sau khi đưa vào `HashMap`; đây là rủi ro của **khóa mutable**, và tính ổn định có điều kiện không còn được bảo đảm sau khi sửa trường khóa.

Lưu thành tệp **`HashCodeStabilityDemo.java`**:

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class HashCodeStabilityDemo {
    static final class StableKey {
        private final String id;

        StableKey(String id) {
            this.id = Objects.requireNonNull(id);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof StableKey that
                    && id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    static final class BrokenHashKey {
        private final String id;
        private int hashCalls;

        BrokenHashKey(String id) {
            this.id = Objects.requireNonNull(id);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof BrokenHashKey that
                    && id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return ++hashCalls; // SAI: mã băm tùy số lần gọi.
        }
    }

    static final class MutableKey {
        private String id;

        MutableKey(String id) {
            this.id = Objects.requireNonNull(id);
        }

        void setId(String id) {
            this.id = Objects.requireNonNull(id);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof MutableKey that
                    && id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    public static void main(String[] args) {
        StableKey first = new StableKey("A");
        StableKey second = new StableKey("A");
        System.out.println("stable hash #1: " + first.hashCode());
        System.out.println("stable hash #2: " + first.hashCode());
        System.out.println("equal objects: " + first.equals(second));
        System.out.println("equal hashes: "
                + (first.hashCode() == second.hashCode()));

        BrokenHashKey broken = new BrokenHashKey("A");
        System.out.println("broken hash #1: " + broken.hashCode());
        System.out.println("broken hash #2: " + broken.hashCode());

        Map<BrokenHashKey, String> badMap = new HashMap<>();
        badMap.put(broken, "value");
        System.out.println("broken map get: " + badMap.get(broken));

        MutableKey mutable = new MutableKey("A");
        Map<MutableKey, String> mutableMap = new HashMap<>();
        mutableMap.put(mutable, "saved");
        System.out.println("mutable before: " + mutableMap.get(mutable));
        mutable.setId("B");
        System.out.println("mutable after: " + mutableMap.get(mutable));
        System.out.println("mutable hash after: " + mutable.hashCode());
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 HashCodeStabilityDemo.java
java HashCodeStabilityDemo
```

Kết quả:

```text
stable hash #1: 65
stable hash #2: 65
equal objects: true
equal hashes: true
broken hash #1: 1
broken hash #2: 2
broken map get: null
mutable before: saved
mutable after: null
mutable hash after: 66
```

**Giải thích kết quả:**

1. `String.hashCode()` của `"A"` bằng `65` và `"B"` bằng `66`. `StableKey` giữ `id` dưới dạng `final String`, nên dữ liệu quyết định bình đẳng không thay đổi; hai lần gọi `first.hashCode()` đều trả `65`. Hai đối tượng `StableKey("A")` khác tham chiếu nhưng bằng nhau và có mã băm bằng nhau.
2. `BrokenHashKey.hashCode()` trả `1`, rồi `2` dù `id` chưa từng thay đổi. Đây là **vi phạm rõ ràng** quy tắc ổn định. Khi `HashMap.put` gọi `hashCode()`, bộ đếm tăng lên `3`; `get` tăng lên `4`. Giá trị băm dùng cho việc tìm khác giá trị băm đã lưu lúc thêm khóa, nên ví dụ không tìm thấy entry.
3. `MutableKey("A")` được thêm vào bản đồ với mã băm `65` và truy xuất được. Sau khi `setId("B")`, mã băm mới là `66`; `HashMap` không tự di chuyển entry đã được lưu sang nhóm băm mới. Vì vậy `get(mutable)` trả `null`. **Lỗi sử dụng là sửa khóa khi nó đang nằm trong bản đồ**, còn bản thân công thức băm `id.hashCode()` vẫn nhất quán với các trường dùng trong `equals()` ở từng trạng thái.
4. Ví dụ sử dụng `HashMap` mặc định của Java 17 với các giá trị `65` và `66`, nên các thao tác tra cứu nêu trên có kết quả xác định. Điều quan trọng về nguyên lý không phải giá trị số cụ thể, mà là mã băm khác nhau khiến quá trình tra cứu đến vị trí khác.

Lưu ý: hai lớp có tên `BrokenHashKey` và `MutableKey` được viết **để minh họa lỗi và rủi ro**, không nên sao chép như khóa dùng trong ứng dụng.

## 5. Tại sao việc đổi mã băm làm `HashMap` và `HashSet` khó sử dụng?

Tại thời điểm `put(key, value)`, `HashMap` lấy mã băm của khóa để định vị nơi lưu entry. `get(key)` lại lấy mã băm hiện tại để tìm vị trí cần tra cứu, sau đó so sánh khóa khi cần thiết. `HashSet` sử dụng cơ chế bảng băm tương tự để quản lý phần tử.

Vì vậy một khóa có mã băm thay đổi trong khi vẫn nằm trong bảng băm có thể gây ra:

- `map.get(key)` trả về `null` dù bạn vừa thêm chính đối tượng ấy.
- `map.containsKey(key)`, `set.contains(key)` hoặc `set.remove(key)` thất bại ngoài dự kiến.
- Một khóa tương đương có thể không tìm được entry cũ nếu `equals()`/`hashCode()` của nó hoặc khóa đã lưu không còn cùng cách diễn giải.
- Dữ liệu vẫn có thể hiện diện khi duyệt các entry nhưng không tìm thấy bằng thao tác tra cứu thông thường.

Không phải **mọi** thay đổi giá trị băm đều khiến lookup thất bại trong mọi cấu hình: bucket, collision và cơ chế nội bộ có thể tác động đến kết quả. Thiết kế đúng không được phụ thuộc vào việc một tình huống tình cờ còn tra cứu được. Hãy giữ nguyên các thuộc tính dùng làm khóa trong toàn bộ thời gian đối tượng nằm trong cấu trúc băm.

Việc `equals()` trả về `true` khi một đối tượng được so với chính nó cũng không cứu được khóa có mã băm sai: quá trình `HashMap` có thể không tới bước so sánh `equals()` nếu mã băm hiện tại khác mã băm entry đã lưu.

## 6. Trường hợp nào mã băm có thể thay đổi mà không tự động vi phạm hợp đồng?

Hợp đồng nói rõ **“nếu không có thông tin dùng trong `equals()` bị sửa”**. Ví dụ:

```java
final class Task {
    private String taskCode;

    Task(String taskCode) {
        this.taskCode = taskCode;
    }

    void changeTaskCode(String newCode) {
        this.taskCode = newCode;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof Task that
                        && Objects.equals(taskCode, that.taskCode);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(taskCode);
    }
}
```

Đây là **mảnh code minh họa**, cần `import java.util.Objects;` khi đặt vào tệp Java. Khi `taskCode` được thay đổi, thông tin quyết định `equals()` đã thay đổi. Do đó mã băm đổi theo không tự động vi phạm điều khoản ổn định có điều kiện. Tuy nhiên, tránh sửa `taskCode` nếu `Task` đang được dùng làm khóa `HashMap` hoặc phần tử `HashSet`.

Ngược lại, nếu lớp còn trường `lastViewedAt` **không tham gia `equals()`**, `hashCode()` không nên sử dụng giá trị trường ấy để thay đổi mã băm. Làm vậy có thể vi phạm cả tính ổn định lẫn quy tắc các đối tượng bằng nhau phải có cùng mã băm.

Có thể lựa chọn thiết kế đối tượng mutable nhưng **cố định khóa logic**: các thuộc tính hiển thị được phép sửa, trong khi những trường dùng cho `equals()` và `hashCode()` được giữ bất biến.

## 7. Những cách cài đặt dễ phá vỡ tính ổn định

### 7.1. Gọi thời gian thực, random hoặc biến đếm trong `hashCode()`

Các cách viết như:

```java
@Override
public int hashCode() {
    return (int) System.currentTimeMillis(); // SAI
}
```

hoặc:

```java
@Override
public int hashCode() {
    return ++counter; // SAI nếu counter không phải dữ liệu equals thay đổi có chủ đích
}
```

đều không thích hợp cho mã băm của đối tượng khi thông tin bình đẳng giữ nguyên. Đây là các **mảnh mã lỗi minh họa**, không phải chương trình độc lập.

Ngay cả khi hai lần gọi có thể tình cờ trả cùng một giá trị, tính ngẫu nhiên hoặc thời gian không bảo đảm sự nhất quán cần có. Tránh truy vấn trạng thái cấu hình, dữ liệu bên ngoài hoặc dịch vụ mạng để tính băm nếu chúng không phải giá trị danh tính ổn định của đối tượng.

### 7.2. Tính băm từ nhiều trường hơn `equals()`

Giả sử `equals()` coi hai sản phẩm có cùng `sku` là bằng nhau, nhưng `hashCode()` dùng cả `sku` và `price`. Hai đối tượng cùng `sku` nhưng khác `price` có thể có hai mã băm khác nhau, vi phạm quy tắc **bằng nhau thì cùng băm**. Ngoài ra, khi `price` thay đổi, băm có thể đổi dù thông tin dùng cho `equals()` giữ nguyên.

Cách sửa: dùng cùng tiêu chí danh tính logic ở hai phương thức; nếu `price` không quyết định `equals()`, loại nó khỏi công thức `hashCode()`.

### 7.3. Trường là mảng hoặc collection mutable

Khai báo `final int[] values` chỉ cố định **tham chiếu tới mảng**, không khiến các phần tử bên trong bất biến. Khi sử dụng `Arrays.equals(values, other.values)` cùng `Arrays.hashCode(values)`, sửa phần tử sau khi thêm khóa vào `HashMap` vẫn có thể đổi mã băm.

Với collection mutable cũng tương tự: các thay đổi phần tử có thể thay đổi `equals()`/`hashCode()`. Cân nhắc sao chép phòng vệ, kiểu bất biến, hoặc giữ danh tính theo một mã ID bất biến.

Đặc biệt, `Object.hashCode()` mặc định của mảng xét theo danh tính đối tượng mảng, còn `Arrays.hashCode(array)` tính theo nội dung. Cần chọn phương pháp phù hợp với cách so sánh bằng nhau đang dùng.

### 7.4. Cache mã băm không hợp lệ

Cache `hashCode()` là một tối ưu hóa **có điều kiện**: nó phù hợp khi các giá trị tham gia bình đẳng bất biến và phép băm được tính đúng từ chúng. Với trường có thể thay đổi, cache cũ có thể trở nên sai hoặc không khớp với `equals()` sau khi cập nhật.

Nếu chọn cache cho đối tượng có trường mutable, phải thiết kế cơ chế vô hiệu hóa cache và bảo đảm tính đúng đắn khi truy cập đồng thời; giải pháp đơn giản và ít rủi ro hơn thường là dùng immutable value object.

### 7.5. Nhầm giá trị băm với danh tính lâu dài

`hashCode()` không phải UUID, khóa database, mã bảo mật hay checksum chống giả mạo. Collision được phép xảy ra và hợp đồng không yêu cầu ổn định giữa các lần chạy. Nếu cần định danh lưu trữ, hãy dùng một ID nghiệp vụ được xác định rõ (ví dụ UUID), sau đó có thể tính `equals()`/`hashCode()` từ ID đó.

## 8. Thiết kế khuyến nghị và kiểm thử

Đối với khóa bản đồ, mô hình dễ duy trì là value object bất biến:

```java
import java.util.Objects;

public record CustomerKey(String tenantId, long customerId) {
    public CustomerKey {
        Objects.requireNonNull(tenantId);
    }
}
```

Đây là khai báo record Java 17 hợp lệ; khi đưa vào tệp `CustomerKey.java`, nó biên dịch được. Java tự tạo `equals()` và `hashCode()` phù hợp với các record component. Vì `String` và `long` đều là giá trị ổn định và các component của record không thể gán lại sau khi tạo, kiểu này là lựa chọn phù hợp làm khóa.

**Record không bảo đảm bất biến sâu:** một record chứa `List` mutable, mảng hoặc object mutable vẫn có thể chứng kiến nội dung của component bị sửa sau khi tạo. Vì vậy hãy đánh giá cả *kiểu dữ liệu của từng component*, không chỉ từ khóa `record`.

Khi review hoặc viết test cho lớp đã override `equals()`/`hashCode()`, nên kiểm tra các trường hợp sau:

1. **Lặp lại không đổi:** tạo `x`, gọi `x.hashCode()` nhiều lần khi dữ liệu dùng cho `equals()` không đổi; mọi kết quả phải giống nhau.
2. **Bằng nhau thì cùng băm:** tạo `x` và `y` khác tham chiếu nhưng `x.equals(y)` trả `true`; xác nhận `x.hashCode() == y.hashCode()`.
3. **Trường không tham gia bình đẳng:** sửa một thuộc tính được phép thay đổi nhưng không thuộc `equals()`; kết quả băm phải vẫn phù hợp hợp đồng, trong đó tính ổn định không được phá vỡ.
4. **Trường quyết định bình đẳng:** nếu kiểu cho phép sửa trường khóa, đánh giá công thức băm sau thay đổi và không dùng đối tượng làm khóa trong bảng băm khi đang sửa.
5. **Tương tác collection:** dùng một khóa cùng giá trị để `put/get` hoặc `add/contains`; xác nhận hành vi ổn định khi khóa không thay đổi.
6. **Nguồn dữ liệu bên ngoài:** tìm lời gọi đồng hồ, random, biến toàn cục, network, getter có side effect hoặc cache không an toàn trong đường tính băm.

Một phép thử nhỏ có thể viết như sau (đoạn mã nằm trong phương thức của lớp kiểm thử, không phải tệp độc lập):

```java
int expected = key.hashCode();

for (int i = 0; i < 100; i++) {
    if (key.hashCode() != expected) {
        throw new AssertionError("hashCode khong on dinh");
    }
}
```

Đảm bảo `key` khác `null` và thông tin dùng trong `equals()` không thay đổi trong quá trình thử. Phép thử lặp giúp bắt nhiều lỗi, nhưng không thể chứng minh tuyệt đối mọi nhánh tính toán hoặc tình huống đa luồng. Cần kết hợp đọc code và kiểm tra thiết kế.

## 9. Những điều cần nhớ

- `hashCode()` phải trả **cùng một số nguyên trong cùng lần chạy** khi thông tin dùng để so sánh `equals()` không đổi.
- Nếu hai đối tượng bằng nhau theo `equals()`, mã băm của chúng **phải** bằng nhau; đối tượng không bằng nhau **có thể** trùng mã băm.
- Không cần cùng mã băm qua nhiều lần chạy JVM, và mã băm không phải định danh lưu trữ lâu dài.
- Nếu thay đổi trường dùng trong `equals()`, mã băm có thể thay đổi mà không tự động vi phạm điều khoản ổn định có điều kiện. Dù vậy, **đừng sửa khóa khi nó đang nằm trong `HashMap` hoặc `HashSet`**.
- Tránh dùng thời gian hiện tại, random, số lần gọi, trường không tham gia bình đẳng hoặc dữ liệu bên ngoài biến động để quyết định mã băm.
- Ưu tiên khóa logic bất biến, thiết kế `equals()`/`hashCode()` tương ứng và kiểm thử cả tính ổn định lẫn cách collection tra cứu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-code-contract-006">Không có yêu cầu giữ cùng hash code giữa các lần chạy chương trình</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao `hashCode()` không bắt buộc giữ nguyên giữa các lần chạy chương trình?

## 1. Phát biểu đúng của hợp đồng

Trong Java, một đối tượng có thể trả về cùng mã băm nhiều lần khi ứng dụng đang chạy, nhưng mã băm đó **không bắt buộc phải giữ nguyên sau khi kết thúc và chạy lại ứng dụng**.

Tài liệu `java.lang.Object.hashCode()` (Java SE 17) nêu ba ràng buộc chính:

1. **Nhất quán trong một lần thực thi:** Khi gọi `hashCode()` nhiều lần trên cùng đối tượng trong **một lần chạy ứng dụng Java**, phương thức phải trả về cùng một số nguyên **miễn là thông tin được dùng trong các phép so sánh `equals` của đối tượng không thay đổi**. Đây không phải cam kết bất biến khi nội dung dùng để xác định equality đã đổi.
2. **Tương thích với `equals()`:** Nếu `a.equals(b)` là `true` thì `a.hashCode() == b.hashCode()` bắt buộc là `true`.
3. **Cho phép va chạm:** Nếu `a.equals(b)` là `false`, Java **không bắt buộc** hai hash code khác nhau. Hai đối tượng không bằng nhau vẫn có thể cùng mã băm.

Sau các ràng buộc trên, tài liệu còn nói rõ rằng số nguyên do `hashCode` trả về **không cần duy trì nhất quán từ một lần thực thi ứng dụng sang một lần thực thi khác**.

Có thể ghi nhớ bằng hai dòng:

- **Trong cùng lần chạy + dữ liệu equality không đổi:** cùng đối tượng ⇒ cùng mã băm.
- **Qua lần chạy khác:** bản thân contract chung của `Object` **không yêu cầu** mã băm cũ phải xuất hiện lại.

Chữ “không yêu cầu” rất quan trọng. Nó **không có nghĩa** Java bắt buộc mỗi lần khởi động phải sinh hash mới. Một class có thể cho cùng mã băm giữa nhiều lần chạy vì thuật toán của nó cố định; điều ấy vẫn hợp lệ. Một class khác có thể cho hash khác mà vẫn tuân thủ contract chung.

## 2. “Một lần chạy” và “hai lần chạy” khác nhau ở đâu?

Ví dụ tình huống:

- Lúc 9 giờ, ứng dụng khởi động; tạo đối tượng `UserKey("U-42")` và gọi `hashCode()`.
- Trong tiến trình đó, ứng dụng gọi lại `hashCode()` nhiều lần mà không thay đổi dữ liệu được dùng trong `equals`.
- Ứng dụng dừng hoàn toàn.
- Lúc 10 giờ, ứng dụng được khởi động lại; tạo một đối tượng mới cũng biểu diễn `UserKey("U-42")` và gọi `hashCode()`.

Hợp đồng yêu cầu các lần gọi thuộc **bước thứ hai** nhất quán. Hợp đồng chung **không buộc** kết quả lúc 10 giờ trùng với kết quả lúc 9 giờ.

Tại sao? `hashCode` được thiết kế như một số nguyên hỗ trợ cấu trúc dữ liệu dựa trên hash **trong lúc thực thi**, không phải định danh lưu trữ vĩnh viễn. Java không công bố một thuật toán hash duy nhất bắt mọi class phải dùng. Cách triển khai của class và môi trường thực thi có thể quyết định giá trị số cụ thể.

Bởi vậy, không nên suy luận “tôi vừa chạy hai lần đều được cùng hash nên Java đảm bảo mọi lần sau cũng vậy”. Quan sát lặp lại trong một môi trường là **bằng chứng về cách triển khai hiện tại**, chưa đủ để chứng minh contract chung.

## 3. Ví dụ Java 17: hash phụ thuộc một giá trị được tạo lúc khởi động

Class sau so sánh khóa phiên theo `id`. Nó sinh một số `RUN_SALT` đúng một lần khi class được khởi tạo trong lần chạy ứng dụng. Trong lần chạy đó, `RUN_SALT` không đổi; mỗi đối tượng có cùng `id` luôn có cùng mã băm. Nếu chạy lại chương trình, giá trị ngẫu nhiên có thể khác nên hash của cùng `id` cũng có thể khác.

```java
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class HashAcrossRunsDemo {
    static final class SessionKey {
        private static final int RUN_SALT = new SecureRandom().nextInt();

        private final String id;

        SessionKey(String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SessionKey that)) {
                return false;
            }
            return id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return 31 * id.hashCode() + RUN_SALT;
        }
    }

    public static void main(String[] args) {
        SessionKey first = new SessionKey("U-42");
        SessionKey second = new SessionKey("U-42");
        Set<SessionKey> keys = new HashSet<>();
        keys.add(first);

        int before = first.hashCode();
        int after = first.hashCode();

        System.out.println(first.equals(second));
        System.out.println(before == after);
        System.out.println(first.hashCode() == second.hashCode());
        System.out.println(keys.contains(second));
    }
}
```

Lưu mã bằng UTF-8 vào tệp `HashAcrossRunsDemo.java` và chạy:

```bash
javac -encoding UTF-8 HashAcrossRunsDemo.java
java HashAcrossRunsDemo
```

**Đầu ra xác định của ví dụ:**

```text
true
true
true
true
```

Giải thích:

1. `first.equals(second)` trả về `true` vì hai đối tượng có cùng `id`.
2. `before == after` là `true` vì `id` và `RUN_SALT` không đổi trong lần chạy hiện tại.
3. Hai đối tượng bằng nhau có cùng hash vì biểu thức tính hash dùng cùng `id` và cùng `RUN_SALT`.
4. `HashSet.contains(second)` trả `true` dù tập hợp lưu đối tượng `first`, nhờ hợp đồng `equals`/`hashCode` được giữ.

Chạy lại `java HashAcrossRunsDemo` vẫn cho **bốn dòng `true`**, nhưng giá trị số nguyên nội bộ của `hashCode()` **có thể** đã khác. Ví dụ cố ý không in số ngẫu nhiên, vì không thể dự đoán một giá trị cụ thể làm đầu ra chuẩn. Dùng `SecureRandom` ở đây chỉ để minh họa rằng việc chọn một tham số theo lần khởi động vẫn có thể đáp ứng contract; nó **không biến `hashCode` thành mã băm mật mã** hay kỹ thuật quản lý phiên an toàn.

Một khả năng nhỏ là hai lần khởi động ngẫu nhiên nhận cùng `RUN_SALT`. Điều đó không phủ định bài học: Java cho phép cả trùng lẫn khác giữa các lần chạy.

### Vì sao class có thể thêm “salt” mà vẫn đúng?

Vì contract chỉ cần:

- Trong một lần chạy, cùng đối tượng và cùng dữ liệu equality ⇒ cùng kết quả.
- Hai đối tượng có cùng `id` ⇒ cùng kết quả hash.
- Không cần đảm bảo hai `id` khác nhau nhận hai hash khác nhau.

Biểu thức `31 * id.hashCode() + RUN_SALT` thỏa ba điều kiện. Số 31 chỉ là lựa chọn của ví dụ, không phải hệ số Java yêu cầu cho mọi class. Phép cộng/tràn số nguyên `int` theo ngữ nghĩa Java không làm sai các tính chất nói trên.

## 4. Đừng hiểu thành “mọi hashCode đều thay đổi khi restart”

Các kiểu dữ liệu cụ thể **có thể công bố hợp đồng mạnh hơn** hợp đồng chung của `Object`.

Ví dụ quan trọng là `String.hashCode()`. Javadoc của `String` định nghĩa thuật toán dựa trên các ký tự trong chuỗi, sử dụng lũy thừa của 31 theo thứ tự ký tự. Vì thế, cùng nội dung chuỗi sẽ cho cùng mã băm theo đặc tả của `String`, kể cả khi tạo chuỗi ở những lần chạy khác nhau trên môi trường tuân thủ API.

Ví dụ:

```java
String a = "java";
String b = new String("java");

System.out.println(a.equals(b));              // true
System.out.println(a.hashCode() == b.hashCode()); // true
```

Đây là **đoạn mã minh họa đặt trong phương thức**, không phải một tệp Java độc lập. Việc hash bằng nhau ở ví dụ này có thể suy ra từ hợp đồng `equals` và `hashCode` của `String`; và do `String.hashCode` được đặc tả theo nội dung, kết quả số cũng được xác định theo chuỗi. Không được từ đó kết luận rằng **mọi class** có thuật toán cố định tương tự.

Bảng phân biệt:

| Đối tượng/phương thức | Có thể dựa vào điều gì? | Không nên suy ra điều gì? |
| --- | --- | --- |
| `Object.hashCode()` (quy tắc chung) | Nhất quán trong lần chạy nếu dữ liệu equality không đổi; bằng nhau thì cùng hash | Giá trị số cụ thể phải trùng giữa mọi lần chạy |
| `String.hashCode()` | Thuật toán được Javadoc đặc tả theo nội dung chuỗi | Hash là duy nhất cho mỗi chuỗi; hai chuỗi khác nhau vẫn có thể trùng hash |
| Class tự viết với thuật toán hoàn toàn dựa trên dữ liệu ổn định | Có thể chủ động cung cấp hash ổn định qua lần chạy | Tính ổn định đó tự động áp dụng cho mọi class khác |
| Class dùng salt riêng theo lần khởi động | Có thể thay hash giữa các lần chạy mà vẫn giữ contract trong một lần chạy | Hash sẽ **chắc chắn** khác mỗi lần |
| `System.identityHashCode(obj)` | Lấy mã băm theo cơ chế identity, bất kể class override `hashCode` ra sao | Giá trị phải dựa trực tiếp trên địa chỉ bộ nhớ hoặc có tính bền vững qua khởi động |

**Phân biệt bảo đảm của Java và bảo đảm riêng của API.** Nếu cần dựa trên một giá trị hash ổn định vì lý do giao thức/lưu trữ, phải có đặc tả cho **chính thuật toán và biểu diễn đầu vào**. Hợp đồng mặc định của `Object.hashCode` chưa đủ.

## 5. `Object.hashCode` và `System.identityHashCode` không phải mã định danh bền vững

Khi một class không override `hashCode`, nó sử dụng implementation kế thừa từ `Object`. Hành vi này gắn với nhận diện đối tượng trong môi trường thực thi; không nên coi số nguyên nhận được là khóa định danh lưu trữ.

`System.identityHashCode(x)` cung cấp giá trị hash theo identity ngay cả khi class của `x` override `hashCode()`. Tuy nhiên:

- Hai đối tượng khác nhau **vẫn có thể** có cùng identity hash code; số nguyên hash không phải mã định danh duy nhất tuyệt đối.
- Không có bảo đảm mã hash identity chính là địa chỉ RAM hoặc là số thứ tự cấp phát.
- Không có bảo đảm một đối tượng được tái tạo sau restart sẽ có hash identity cũ.
- Nếu `x` và `y` bằng nhau theo giá trị do override `equals`, `System.identityHashCode(x)` và `System.identityHashCode(y)` không nhất thiết bằng nhau. Bởi vậy **không dùng `System.identityHashCode` làm implementation cho `hashCode()` của một class có value equality**, trừ khi có cơ chế khác bảo đảm tương thích.

Cả `Object.hashCode` lẫn identity hash đều phù hợp cho những nhu cầu nhất định trong quá trình chạy, nhưng không thay thế được ID nghiệp vụ, ID cơ sở dữ liệu hoặc khóa được quản lý theo một hợp đồng lâu dài.

## 6. Hệ quả khi lưu hash xuống cơ sở dữ liệu, tệp hoặc cache ngoài JVM

Một lỗi thiết kế thường gặp là lưu `object.hashCode()` như “mã cố định” của bản ghi:

```java
// Ví dụ thiết kế không an toàn khi cần định danh bền vững:
int persistedId = customer.hashCode();
```

Dòng này chỉ là đoạn mã minh họa, không phải chương trình độc lập. Nếu `customer.hashCode()` phụ thuộc implementation hoặc dữ liệu có thể thay đổi, giá trị lưu có thể không còn thích hợp trong lần chạy sau. Ngay cả khi thuật toán ổn định, **hai đối tượng khác nhau vẫn có thể cùng hash**; vì vậy hash code không đáp ứng yêu cầu định danh duy nhất.

Các tình huống rủi ro:

1. **Lưu khóa database bằng hash code:** Có thể gây va chạm và không bảo đảm ổn định khi class/JDK thay đổi.
2. **Ghi hash vào tệp rồi so sánh sau khi restart:** Hai giá trị có thể khác dù dữ liệu nghiệp vụ giống hệt nhau.
3. **Chia shard hoặc phân phối dữ liệu bằng `Object.hashCode()` tùy ý:** Nếu các tiến trình hoặc phiên bản code tính hash khác nhau, cùng khóa logic có thể được gán sang vị trí khác. Nếu chia shard cần tính ổn định, giao thức phân phối phải đặc tả thuật toán hash và dạng chuẩn của khóa.
4. **Tra cứu bản ghi dựa vào hash lưu từ lần chạy trước:** Hash có thể thay đổi theo execution hoặc phiên bản; ngay cả khi không đổi thì collision vẫn khiến phép tìm kiếm thiếu an toàn.
5. **Xây checksum hoặc chữ ký bảo mật từ `hashCode()`:** Mã băm `int` của `Object` không phải digest mật mã, không bảo đảm chống đụng độ và không xác nhận tính toàn vẹn chống giả mạo.

Nếu muốn lưu định danh, hãy dùng một ID được quy định rõ (chẳng hạn khóa nghiệp vụ hoặc UUID phù hợp với thiết kế). Nếu muốn phát hiện thay đổi dữ liệu, chọn thuật toán digest phù hợp nhu cầu cùng quy tắc mã hóa/serialize ổn định; nếu cần chống giả mạo, xem xét HMAC hoặc chữ ký số với cơ chế quản lý khóa phù hợp. Những mục tiêu này khác với mục tiêu của `Object.hashCode()`.

## 7. `HashMap` và `HashSet` cần gì từ hash trong một lần chạy?

`HashMap` và `HashSet` sử dụng hash để tổ chức và tìm phần tử. Trong một lần thực thi, nếu một khóa được chèn và sau đó tìm lại bằng một khóa khác **bằng nhau theo `equals`**, cả hai phải cho cùng hash để bảo toàn ngữ nghĩa tra cứu.

Không cần các bucket nội bộ hoặc hash trước/sau restart trùng nhau. Khi khởi động ứng dụng mới, collection trong bộ nhớ cũng được xây dựng lại. Mỗi lần thêm khóa vào collection mới, cơ chế hash của lần chạy đó được áp dụng nhất quán.

Ví dụ, class `SessionKey` phía trên hoàn toàn có thể dùng làm khóa trong `HashMap` của một lần chạy. Nếu ứng dụng dừng rồi khởi động lại, việc dựng một `HashMap` mới từ các khóa nghiệp vụ vẫn hợp lệ dù salt/hash mới khác. Điều quan trọng là việc phục hồi dữ liệu dựa trên **ID thật hoặc dữ liệu giá trị**, không dựa trên bucket cũ hay con số hash cũ.

### Lỗi khác cần tách bạch: đổi dữ liệu tham gia equality

Nếu một khóa mutable được thêm vào `HashSet` rồi người dùng sửa trường tham gia `equals`/`hashCode`, mã băm **trong cùng lần chạy** có thể đổi và phép `contains` có thể thất bại. Đây không phải vấn đề “hash không ổn định qua restart”; đây là vấn đề **biến đổi trạng thái khi khóa đang nằm trong hash collection**.

Nên thiết kế những trường quyết định equality của khóa là bất biến, hoặc đảm bảo chúng không đổi trong suốt thời gian khóa được dùng trong collection.

## 8. Những nhận định dễ nhầm

| Nhận định | Đánh giá | Giải thích |
| --- | --- | --- |
| “Với cùng đối tượng, gọi `hashCode` hai lần trong một lần chạy phải cho cùng số nếu dữ liệu equality không đổi.” | **Đúng** | Đúng contract nhất quán. |
| “`hashCode` của cùng giá trị phải giữ nguyên qua mọi lần khởi động JVM.” | **Sai nếu phát biểu như quy tắc chung** | `Object` không yêu cầu vậy; class cụ thể có thể công bố mạnh hơn. |
| “Java bắt buộc hash thay đổi mỗi lần restart.” | **Sai** | Không yêu cầu giống nhau **cũng không yêu cầu khác nhau**. |
| “Nếu hai đối tượng bằng nhau theo `equals` thì hash bằng nhau.” | **Đúng, bắt buộc** | Dù class chọn thuật toán nào. |
| “Nếu hai hash bằng nhau thì hai đối tượng bằng nhau.” | **Sai** | Hash collision được phép. |
| “`String.hashCode` có thuật toán theo nội dung đã đặc tả.” | **Đúng** | Đây là cam kết riêng của `String`, không phủ lên mọi class. |
| “`System.identityHashCode` là địa chỉ bộ nhớ.” | **Không có bảo đảm** | API không hứa quan hệ trực tiếp như vậy. |
| “Có thể dùng hash code như ID duy nhất trong database.” | **Không an toàn** | Vừa có khả năng va chạm vừa thiếu bảo đảm bền vững chung. |
| “Một class có thể thêm salt cố định cho mỗi lần chạy khi tính hash.” | **Có thể đúng** | Nếu bảo đảm nhất quán trong lần chạy và tương thích `equals`. |

## 9. Quy trình chọn kỹ thuật hash theo nhu cầu thực tế

**Khi cần `equals`/`hashCode` cho Java collection trong bộ nhớ:**

1. Định nghĩa rõ những trường quyết định hai đối tượng bằng nhau.
2. Đảm bảo hai đối tượng bằng nhau cho cùng hash, kể cả khi chúng là hai instance khác nhau.
3. Giữ kết quả ổn định trong thời gian dữ liệu equality không đổi.
4. Chọn phân bố hash hợp lý để tránh nhiều collision không cần thiết.
5. Tránh đột biến trường equality khi đối tượng đang là hash key.

**Khi cần mã ổn định qua restart, qua phiên bản dịch vụ hoặc giữa máy khác nhau:**

1. Xác định rõ mục đích: định danh, phân vùng, checksum hay bảo mật.
2. Chọn thuật toán và dạng chuẩn hóa đầu vào được đặc tả tường minh cho mục đích đó.
3. Quy định encoding, thứ tự byte, cách serialize và versioning (nếu dữ liệu phức tạp).
4. Thiết kế cách xử lý va chạm/di chuyển dữ liệu nếu cần.
5. Kiểm thử qua nhiều tiến trình và phiên bản, **không phụ thuộc ngầm vào `Object.hashCode()`**.

Với dữ liệu cần so sánh giữa các tiến trình, ngay cả khi có thuật toán hash ổn định, một mã băm nhỏ vẫn có nguy cơ collision; khi tính đúng đòi hỏi phân biệt tuyệt đối, phải kết hợp kiểm tra khóa/dữ liệu đầy đủ hoặc một định danh có bảo đảm phù hợp hơn.

## 10. Câu hỏi tự luyện

**Câu 1.** Chạy một ứng dụng hai lần, cùng tạo object đại diện cho mã `"A-01"`. Lần đầu được hash `123`, lần sau được hash `456`. Có thể kết luận vi phạm `Object.hashCode()` không?

**Đáp án:** Không thể kết luận như vậy. Contract chung không yêu cầu cùng hash giữa hai lần chạy. Muốn xác định đúng/sai phải kiểm tra contract cụ thể của class và các điều kiện liên quan, nhất là trong **mỗi** lần chạy, các đối tượng bằng nhau có cùng hash và hash không tự thay đổi khi dữ liệu equality không đổi.

**Câu 2.** Chạy hai lần và nhận hash `123` ở cả hai lần. Có thể kết luận Java bảo đảm các lần chạy sau vẫn là `123` không?

**Đáp án:** Không. Hai quan sát trùng nhau không tạo thành bảo đảm chung. Chỉ khi API cụ thể định nghĩa thuật toán/đầu vào và các điều kiện phù hợp mới có cơ sở dựa vào kết quả qua các lần chạy.

**Câu 3.** Trong **một lần chạy**, hai đối tượng `UserKey("U-42")` trả `true` khi gọi `equals`, nhưng hash lần lượt là `101` và `202`. Có hợp lệ không?

**Đáp án:** Không. Đây là vi phạm ràng buộc bắt buộc “bằng nhau theo `equals` thì có cùng hash code”.

**Câu 4.** Một class lưu mã băm của `String code` bằng `code.hashCode()`. Vì `String.hashCode()` được đặc tả, có thể dùng số đó làm khóa chính duy nhất cho mọi chuỗi không?

**Đáp án:** Không. Thuật toán xác định không đồng nghĩa với tính duy nhất. Hai chuỗi khác nhau có thể có cùng hash. Khóa chính phải xử lý vấn đề uniqueness theo yêu cầu dữ liệu.

**Câu 5.** Một ứng dụng lưu `hashCode()` của các object để phân phối bản ghi tới máy chủ. Sau deploy, logic triển khai hash thay đổi nhưng equality vẫn giữ nguyên. Điều gì có thể xảy ra?

**Đáp án:** Hash và vị trí phân vùng của cùng khóa logic có thể đổi, dù implementation mới vẫn đúng `Object` contract. Phân phối ổn định qua triển khai cần giao thức băm được kiểm soát và cơ chế di chuyển dữ liệu, không dựa vào hash code triển khai tùy ý.

## 11. Ghi nhớ

- `Object.hashCode()` **phải nhất quán trong một lần chạy** khi dữ liệu dùng trong `equals` không đổi.
- `a.equals(b) == true` ⇒ `a.hashCode() == b.hashCode()`, nhưng cùng hash **không** chứng minh hai đối tượng bằng nhau.
- **Không có yêu cầu giữ cùng giá trị hash giữa các lần chạy chương trình**; cũng không có yêu cầu bắt buộc phải thay đổi.
- `String.hashCode()` là ví dụ một API cụ thể công bố thuật toán theo nội dung; luôn phân biệt cam kết của class cụ thể với contract chung.
- `hashCode()` phục vụ equality và cấu trúc dữ liệu dựa trên hash trong bộ nhớ, **không thay thế** ID bền vững, checksum có đặc tả hay mã băm mật mã.
- Đừng nhầm lỗi do *restart* với lỗi do thay đổi trường tham gia equality của một khóa đang nằm trong `HashMap`/`HashSet`.

Tài liệu đối chiếu: [`Object.hashCode()` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#hashCode()), [`String.hashCode()` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#hashCode()), [`System.identityHashCode()` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#identityHashCode(java.lang.Object)) và [`HashMap` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-code-contract-007">Vì sao ghi đè equals() thường phải ghi đè hashCode() cùng lúc</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao ghi đè `equals()` thường phải ghi đè `hashCode()` cùng lúc?

## 1. Vấn đề cốt lõi

Trong Java, `equals(Object)` trả lời câu hỏi **hai đối tượng có được coi là bằng nhau theo định nghĩa của lớp hay không**. `hashCode()` tạo ra một số nguyên `int` dùng làm mã băm, đặc biệt quan trọng đối với các cấu trúc như `HashMap` và `HashSet`.

Khi bạn ghi đè `equals()` từ so sánh cùng tham chiếu sang so sánh nội dung hoặc mã định danh, hai instance **khác nhau trong bộ nhớ** có thể trở thành bằng nhau. Nếu vẫn để nguyên `hashCode()` kế thừa từ `Object`, bạn không bảo đảm hai instance bằng nhau có cùng mã băm. Điều này vi phạm **hợp đồng của `hashCode()`**, khiến những bộ sưu tập dựa trên hash có thể không tìm thấy khóa, giữ lại phần tử trùng hoặc xử lý khóa không như mong muốn.

Mệnh đề bắt buộc cần nhớ:

**Nếu `a.equals(b)` là `true` thì `a.hashCode() == b.hashCode()` phải là `true`.**

Đảo chiều là sai: hai đối tượng có hash bằng nhau **không bắt buộc** phải bằng nhau theo `equals()`; đó có thể là một lần va chạm hash.

Vì thế, khi thay đổi định nghĩa “bằng nhau” của một lớp, cần xét **đồng thời** cả hai phương thức. Trong cách triển khai thường dùng, bạn ghi đè cả `equals()` và `hashCode()` dựa trên cùng những thuộc tính có ý nghĩa đối với sự bằng nhau.

## 2. Hợp đồng chính xác của hai phương thức

### 2.1. `equals()` phải là một quan hệ tương đương

Với các tham chiếu không `null` và khi những thông tin dùng trong phép so sánh không đổi, một triển khai `equals()` cần bảo đảm:

- **Phản xạ:** `x.equals(x)` luôn là `true`.
- **Đối xứng:** `x.equals(y)` và `y.equals(x)` có cùng kết quả.
- **Bắc cầu:** nếu `x.equals(y)` và `y.equals(z)` đều `true` thì `x.equals(z)` phải `true`.
- **Nhất quán:** gọi lại nhiều lần với cùng thông tin liên quan cho cùng kết quả.
- **So với `null`:** `x.equals(null)` là `false`.

`Object.equals()` mặc định có hành vi so sánh đồng nhất tham chiếu, tương tự `==` với hai tham chiếu đối tượng. Lớp con có thể ghi đè để so theo dữ liệu. Việc ghi đè như vậy mở rộng phạm vi các đối tượng được coi là bằng nhau, nhưng kéo theo nghĩa vụ cập nhật hash tương ứng.

### 2.2. `hashCode()` có những cam kết gì?

Theo hợp đồng Java:

1. Với **cùng một đối tượng**, nếu các thông tin dùng trong `equals()` không bị sửa, nhiều lần gọi `hashCode()` trong **một lần thực thi** phải cho cùng kết quả. Không yêu cầu mã hash giống nhau qua các lần chạy chương trình khác nhau.
2. Nếu `x.equals(y)` trả về `true` thì **bắt buộc** `x.hashCode() == y.hashCode()`.
3. Nếu `x.equals(y)` trả về `false` thì **không bắt buộc** hai hash phải khác nhau. Hash phân biệt tốt giúp hiệu năng nhưng không phải điều kiện đúng/sai của equality.

Viết dưới dạng kéo theo:

```text
equals(a, b) == true  =>  hashCode(a) == hashCode(b)    [BẮT BUỘC]
hashCode(a) == hashCode(b) => equals(a, b) == true      [KHÔNG ĐÚNG]
equals(a, b) == false => hashCode(a) != hashCode(b)     [KHÔNG BẮT BUỘC]
```

`hashCode()` chỉ tạo số nguyên 32-bit và có thể va chạm. Bản thân hash **không phải** ID duy nhất, không phải địa chỉ bộ nhớ và không nên dùng làm định danh nghiệp vụ.

## 3. Vì sao chỉ ghi đè `equals()` dễ làm sai hợp đồng?

Giả sử có một lớp đại diện cho khóa theo `tenantId` và `documentId`:

```java
import java.util.Objects;

// Mảnh code minh họa, chưa phải chương trình độc lập.
final class OnlyEqualsKey {
    private final String tenantId;
    private final String documentId;

    OnlyEqualsKey(String tenantId, String documentId) {
        this.tenantId = tenantId;
        this.documentId = documentId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OnlyEqualsKey that)) return false;
        return Objects.equals(tenantId, that.tenantId)
            && Objects.equals(documentId, that.documentId);
    }

    // Không ghi đè hashCode(): vẫn kế thừa Object.hashCode().
}
```

Giả sử `first` và `second` là hai instance `OnlyEqualsKey` khác nhau nhưng cùng hai trường:

```java
OnlyEqualsKey first = new OnlyEqualsKey("T1", "D-10");
OnlyEqualsKey second = new OnlyEqualsKey("T1", "D-10");
// first.equals(second) == true
```

Lời gọi `first.equals(second)` cho `true` theo nội dung. Tuy nhiên `Object.hashCode()` mặc định dựa trên danh tính đối tượng. Java **không cam kết** hai instance khác nhau có cùng mã hash; chúng thường cho giá trị khác nhau nhưng va chạm vẫn có thể xảy ra. Vì vậy class này **không bảo đảm** hệ quả bắt buộc “bằng nhau thì hash bằng nhau”.

Đừng viết ví dụ dựa trên kết luận chắc chắn rằng `first.hashCode() != second.hashCode()`: Java không bảo đảm các hash identity luôn khác nhau. Để thấy lỗi bằng kết quả **xác định được**, ví dụ chạy được ở phần sau dùng một kiểu khóa cố ý trả về hai hash khác nhau cho hai đối tượng mà `equals()` coi bằng nhau.

## 4. Cấu trúc `HashMap` và `HashSet` dựa vào cả hash lẫn equality

Ở mức khái niệm:

1. Khi thêm một khóa, cấu trúc băm dùng `hashCode()` để định vị vùng lưu trữ/bucket thích hợp.
2. Trong vùng được xem xét, cấu trúc cần phân biệt những khóa có mã hash trùng bằng quan hệ equality (hoặc trường hợp cùng tham chiếu).
3. Khi truy xuất với một khóa tương đương, nó phải đi tới vị trí tương thích với khóa đã lưu. Nếu hai khóa được coi bằng nhau nhưng có mã hash khác nhau, việc tìm kiếm có thể đi lệch và không bao giờ kiểm tra được phần tử cần tìm.

Chi tiết bucket, thao tác nội bộ và mức độ xử lý va chạm là triển khai; điều phải dựa vào khi lập trình là **hợp đồng `equals/hashCode`**.

| Thao tác | Khi hợp đồng đúng | Khi `equals` nói bằng nhau nhưng hash khác nhau |
| --- | --- | --- |
| `HashSet.add(second)` sau `add(first)` | Phần tử bằng nhau được coi trùng; không tăng số phần tử | Có thể nhận thêm “bản sao” thành phần tử mới |
| `HashSet.contains(second)` | Có thể tìm phần tử tương đương đã thêm | Có thể trả `false` |
| `HashMap.get(second)` sau `put(first, value)` | Tìm ra giá trị của khóa tương đương | Có thể trả `null` dù khóa bằng nhau tồn tại |
| `HashMap.put(second, value2)` | Cập nhật mapping của khóa tương đương | Có thể tạo hai entry cho các khóa được coi bằng nhau |

Các bộ sưu tập dựa trên danh sách như `ArrayList.contains` và các phép so sánh trực tiếp thường dùng `equals()` mà không dựa vào mã băm của phần tử; vì vậy có thể cho kết quả đúng ngay cả khi một lớp có `hashCode()` sai. Không vì vậy mà coi lớp đó hợp lệ: lỗi sẽ xuất hiện khi đổi sang `HashSet` hoặc `HashMap`.

## 5. Chương trình Java 17 hoàn chỉnh: sai rồi sửa

Ví dụ dưới đây trình bày hai lớp khóa.

- `BrokenKey` cố ý **vi phạm hợp đồng**: `equals()` so `code` nhưng `hashCode()` trả về trường `slot` mà equality bỏ qua. Hai instance có cùng `code` và `slot` khác nhau là bằng nhau nhưng có hash khác nhau. Đây là phép mô phỏng **xác định** để dễ quan sát lỗi.
- `CorrectKey` dùng cùng tổ hợp `tenantId` và `code` trong `equals()` lẫn `hashCode()`. `label` chỉ là thông tin mô tả và bị loại khỏi cả hai.

Lưu tệp **`EqualsHashCodeContractDemo.java`**:

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class EqualsHashCodeContractDemo {
    static final class BrokenKey {
        private final String code;
        private final int slot;

        BrokenKey(String code, int slot) {
            this.code = Objects.requireNonNull(code);
            this.slot = slot;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof BrokenKey that)) return false;
            return code.equals(that.code); // "slot" không thuộc equality
        }

        @Override
        public int hashCode() {
            return slot; // SAI: field bị equals bỏ qua
        }
    }

    static final class CorrectKey {
        private final String tenantId;
        private final String code;
        private String label;

        CorrectKey(String tenantId, String code, String label) {
            this.tenantId = Objects.requireNonNull(tenantId);
            this.code = Objects.requireNonNull(code);
            this.label = label;
        }

        void rename(String label) {
            this.label = label;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof CorrectKey that)) return false;
            return tenantId.equals(that.tenantId)
                && code.equals(that.code);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, code);
        }
    }

    public static void main(String[] args) {
        BrokenKey badA = new BrokenKey("X-7", 1);
        BrokenKey badB = new BrokenKey("X-7", 2);
        System.out.println("Bad equals: " + badA.equals(badB));
        System.out.println("Bad hashes equal: "
            + (badA.hashCode() == badB.hashCode()));

        Set<BrokenKey> brokenSet = new HashSet<>();
        brokenSet.add(badA);
        brokenSet.add(badB);
        System.out.println("Broken HashSet size: " + brokenSet.size());

        Map<BrokenKey, String> brokenMap = new HashMap<>();
        brokenMap.put(badA, "saved");
        System.out.println("Broken HashMap lookup: " + brokenMap.get(badB));

        CorrectKey a = new CorrectKey("T1", "X-7", "Ban dau");
        CorrectKey b = new CorrectKey("T1", "X-7", "Ban tai lai");
        CorrectKey c = new CorrectKey("T2", "X-7", "Tenant khac");

        System.out.println("Good equals: " + a.equals(b));
        System.out.println("Good hashes equal: "
            + (a.hashCode() == b.hashCode()));
        System.out.println("Different tenant equal: " + a.equals(c));

        Set<CorrectKey> correctSet = new HashSet<>();
        correctSet.add(a);
        correctSet.add(b);
        correctSet.add(c);
        System.out.println("Correct HashSet size: " + correctSet.size());

        Map<CorrectKey, String> correctMap = new HashMap<>();
        correctMap.put(a, "saved");
        System.out.println("Correct HashMap lookup: " + correctMap.get(b));

        a.rename("Da doi ten");
        System.out.println("Contains after rename: " + correctSet.contains(a));
    }
}
```

Biên dịch/chạy từ thư mục chứa tệp bằng **JDK 17 hoặc mới hơn**:

```bash
javac EqualsHashCodeContractDemo.java
java EqualsHashCodeContractDemo
```

Kết quả:

```text
Bad equals: true
Bad hashes equal: false
Broken HashSet size: 2
Broken HashMap lookup: null
Good equals: true
Good hashes equal: true
Different tenant equal: false
Correct HashSet size: 2
Correct HashMap lookup: saved
Contains after rename: true
```

**Giải thích kết quả, không phụ thuộc mã hash ngẫu nhiên:**

1. Hai `BrokenKey` bằng nhau vì cùng `code == "X-7"` về mặt nội dung, nhưng hash lần lượt bằng `1` và `2`. Đây là trường hợp cố ý vi phạm điều kiện “equal implies same hash”.
2. `HashSet` giữ cả hai object vi phạm làm hai phần tử vì chúng có hash khác nhau và được đưa vào vị trí khác nhau; `HashMap.get(badB)` không tìm được entry được thêm bằng `badA`. Không nên suy rộng giá trị hash 1, 2 này thành hành vi chuẩn của `Object.hashCode()`; ví dụ là **một cách tái hiện hậu quả** của lớp có hợp đồng sai.
3. Hai `CorrectKey` cùng `tenantId` và `code` bằng nhau dù `label` khác nhau; hash của chúng cùng được tính bằng `Objects.hash(tenantId, code)`.
4. Object `c` cùng `code` nhưng khác tenant nên **không bằng** `a`. Bỏ `tenantId` khỏi `equals` trong miền này sẽ gộp nhầm hai khóa.
5. `HashSet` đúng chỉ chứa hai khóa khác nhau: `(T1, X-7)` và `(T2, X-7)`. `HashMap.get(b)` tìm ra giá trị đã lưu bằng `a`.
6. Đổi `label` của `a` không làm thay đổi định nghĩa bằng nhau hoặc hash nên khóa vẫn tìm thấy trong tập. Nếu `label` có mặt trong `hashCode()`, đổi tên có thể làm hỏng việc tra cứu khóa.

## 6. Cách triển khai `equals()` và `hashCode()` khớp nhau

### 6.1. Chọn tập trường theo **định nghĩa bằng nhau**

Không tự động đưa tất cả field vào cả hai phương thức. Ví dụ trong `CorrectKey`, khóa là `(tenantId, code)` nên hai phương thức đều được viết theo **hai field này**; `label` không tham gia.

Quy tắc thiết kế:

- **Value object** như cặp tọa độ `(x, y)`: thường so toàn bộ thuộc tính làm nên giá trị.
- **Entity** có ID nghiệp vụ ổn định: có thể so ID; ID có thể là tổ hợp `(tenantId, localId)` khi tính duy nhất có phạm vi.
- **Metadata/cache/log/field mô tả**: không tự động trở thành một phần của equality.
- **Thuộc tính dễ thay đổi**: cân nhắc loại khỏi equality/hash nếu đối tượng sẽ dùng làm khóa trong cấu trúc băm.

Không có yêu cầu rằng `hashCode()` phải sử dụng **mọi** field mà `equals()` dùng; về mặt hợp đồng, có thể trả về mã hash hằng số (mọi object đều hash bằng nhau) và vẫn thỏa “hai object bằng nhau có hash bằng nhau”. Tuy nhiên điều này có thể tạo quá nhiều va chạm, làm giảm hiệu năng. Cách lập trình thực tế thường tính hash từ chính các trường tham gia equality để mã hash hữu ích, rõ ràng và dễ bảo trì. Điều tối quan trọng là **không để những trường mà `equals()` bỏ qua làm thay đổi hash của hai object bằng nhau**.

### 6.2. Lựa chọn công cụ theo kiểu dữ liệu

| Kiểu dữ liệu | So sánh equality phù hợp | Thành phần hash tương ứng |
| --- | --- | --- |
| `int`, `long`, `boolean` | `==` | `Integer.hashCode`, `Long.hashCode`, `Boolean.hashCode` |
| `String`/value object có thể `null` | `Objects.equals(a, b)` | `Objects.hashCode(value)` hoặc hash tổng hợp |
| Nhiều reference có thể `null` | Kết hợp `Objects.equals(...)` bằng `&&` | `Objects.hash(field1, field2, ...)` |
| `float`/`double` | Chọn `Float.compare`/`Double.compare` để khớp chính sách IEEE được định nghĩa; chú ý NaN và zero có dấu | `Float.hashCode`/`Double.hashCode` tương ứng |
| Mảng một chiều | `Arrays.equals(a, b)` | `Arrays.hashCode(array)` |
| Mảng đa chiều khi cần deep equality | `Arrays.deepEquals(a, b)` | `Arrays.deepHashCode(array)` |
| `BigDecimal` | `BigDecimal.equals` theo value **và scale**, hoặc chính sách canonicalization riêng | `BigDecimal.hashCode` nếu dùng `equals` gốc; hash theo giá trị chuẩn hóa nếu dùng equality đã chuẩn hóa |

`Objects.hash(...)` là phương thức varargs; nó hữu ích cho ít trường nhưng có thể gây thêm cấp phát mảng/boxing trong đường chạy nóng. Chỉ tối ưu bằng phép nhân `31` và hàm hash kiểu tương ứng khi có lý do và đã kiểm chứng tính đúng. Ví dụ tổng hợp thủ công:

```java
@Override
public int hashCode() {
    int result = tenantId.hashCode();
    result = 31 * result + code.hashCode();
    return result;
}
```

Mảnh code này thuộc class có hai field `tenantId`, `code` đều được xác nhận không `null`, giống `CorrectKey`; không phải chương trình độc lập. Phép nhân/tràn số nguyên `int` trong tính hash là bình thường và cho kết quả xác định theo Java.

### 6.3. Khi không cần tự triển khai?

Một `record` của Java thường có `equals()` và `hashCode()` được sinh nhất quán từ **các component** của record. Với record làm value object và tất cả component thật sự thuộc định nghĩa bằng nhau, đây là một lựa chọn ngắn gọn. Nhưng nếu component là mảng, logic bằng nhau mặc định không tự trở thành phép so nội dung sâu; nếu một component không thuộc equality nghiệp vụ, code sinh mặc định có thể không phù hợp.

Một lớp không ghi đè **cả** `equals()` **lẫn** `hashCode()` và kế thừa hành vi identity từ `Object` vẫn có hợp đồng nhất quán. Vấn đề xuất hiện khi thay đổi một phía nhưng không đảm bảo phía còn lại tương ứng.

## 7. Những sai lầm tinh vi cần tránh

### 7.1. `hashCode()` không phải phép kiểm tra bằng nhau

Không bao giờ thay `a.equals(b)` bằng `a.hashCode() == b.hashCode()` để quyết định cùng thực thể. Hai object khác nhau có thể đụng hash; Java chỉ cung cấp không gian `int` hữu hạn. Bảng băm vẫn cần đối chiếu khóa theo equality khi có ứng viên trùng hash.

### 7.2. Cùng mã hash không đồng nghĩa cùng đối tượng Java

`System.identityHashCode(obj)` tạo mã hash dựa trên danh tính, không phải địa chỉ RAM và không bảo đảm duy nhất. Trong khi đó `Object.hashCode()` có thể được override bởi lớp con. Để kiểm tra có cùng instance, dùng `a == b` với tham chiếu; để kiểm tra cùng giá trị theo miền, dùng `a.equals(b)` khi lớp thiết kế đúng.

### 7.3. Thay đổi field tham gia hash sau khi đưa vào `HashSet`/`HashMap`

Giả sử `hashCode()` dùng `status` và `status` bị sửa sau `set.add(object)`. Khi `contains(object)` gọi hash mới, việc truy xuất có thể tìm sai vị trí so với lúc thêm. Tương tự với field tham gia `equals()`. Hãy ưu tiên khóa/value object bất biến hoặc chỉ đưa các trường ổn định vào equality/hash.

Từ khóa `final` trên tham chiếu không bảo đảm object được tham chiếu bất biến: `final List<String>` vẫn có thể bị thay đổi các phần tử. Nếu trường là mảng/collection mutable và thực sự tham gia equality, cần phòng vệ bằng sao chép, immutable wrapper hoặc thiết kế phù hợp. 

### 7.4. ID database được gán trễ

Một entity mới có `id == null`. Nếu `equals()` chỉ dùng `Objects.equals(id, that.id)`, hai entity mới khác nhau đều có `null` có thể bị coi bằng nhau; nếu `hashCode()` dựa vào ID rồi ID được gán sau `insert`, hash thay đổi khi đang là khóa. Cần thiết kế nhất quán cho trạng thái trước/sau lưu, tùy hệ thống ORM và chiến lược cấp ID. Đừng cho rằng “đã override đủ hai hàm” thì tự động đúng mọi vòng đời.

### 7.5. Chuẩn hóa không giống nhau giữa equality và hash

Ví dụ `equals()` dùng `code.equalsIgnoreCase(other.code)` nhưng `hashCode()` dùng `code.hashCode()` của chuỗi gốc. Với `"abc"` và `"ABC"`, equality trả `true` nhưng hash gốc có thể khác: hợp đồng bị phá. Nên chuẩn hóa mã trong constructor theo quy tắc miền (chẳng hạn `toUpperCase(Locale.ROOT)` đối với định danh kỹ thuật nếu phù hợp), rồi dùng **cùng biểu diễn chuẩn** trong cả hai phương thức.

Với `BigDecimal`, `new BigDecimal("1.0").compareTo(new BigDecimal("1.00")) == 0` nhưng `equals` mặc định trả `false` vì `scale` khác. Nếu đổi equality sang so trị số bằng `compareTo`, cũng phải tính hash theo chính sách chuẩn hóa cùng trị số; giữ hash `BigDecimal` gốc là nguy hiểm.

### 7.6. Kế thừa phá quy tắc đối xứng

Nếu class cha so hai field, class con lại so ba field, `parent.equals(child)` có thể khác `child.equals(parent)`. Một cách đơn giản cho các class đại diện giá trị là `final class` để cố định quan hệ bằng nhau. Với kế thừa/ORM proxy, cần chiến lược kiểu class nhất quán và phù hợp framework. Tự sinh `hashCode()` không cứu được một triển khai `equals()` mất đối xứng.

### 7.7. Tưởng rằng hash ổn định qua nhiều lần chạy

Hợp đồng chỉ đòi hỏi mã hash nhất quán **trong một lần thực thi** khi các thông tin equality liên quan không đổi. Không nên ghi `hashCode()` hoặc `identityHashCode()` xuống database để làm ID bền vững, không dùng làm khóa chia sẻ liên tiến trình nếu không có hợp đồng thuật toán và dữ liệu rõ ràng. Sử dụng ID miền nghiệp vụ ổn định cho việc đó.

## 8. Cách kiểm tra khi review một lớp

Trước khi chấp nhận một class đã override `equals()`:

1. **Đọc định nghĩa bằng nhau bằng lời:** “Hai instance bằng nhau khi ...”; ghi rõ đó là cùng giá trị hay cùng thực thể theo ID.
2. **Kiểm tra danh sách field được so trong `equals()`:** có bỏ sót namespace, tenant, loại thực thể không? Có đưa nhầm tên hiển thị/cache/trạng thái dễ đổi không?
3. **Kiểm tra hash theo cặp đối tượng bằng nhau:** mọi pair `a.equals(b) == true` bắt buộc cho `a.hashCode() == b.hashCode()`. Tập trường hash không cần giống hệt về cú pháp nhưng phải **tương thích logic**.
4. **Kiểm tra null, kiểu khác, tham chiếu cùng object:** `equals(null)` là `false`, `equals(this)` là `true`; không làm mất đối xứng/bắc cầu.
5. **Kiểm tra tính ổn định:** sau khi đưa object vào `HashSet` hoặc làm key `HashMap`, trường ảnh hưởng equality/hash có bị sửa không?
6. **Kiểm tra bằng collection thật:** thêm hai object bằng nhau vào `HashSet` phải cho một phần tử duy nhất; `HashMap.get(equalKey)` phải trả giá trị đã lưu. Thử với các field khác nhau và hash collision khi phù hợp.
7. **Kiểm tra generated code:** IDE, Lombok và record có thể giúp tránh lỗi gõ, nhưng không tự hiểu quy tắc nghiệp vụ; luôn review trường nào được include/exclude.

Ví dụ những xác nhận tối thiểu (mảnh code áp dụng cho class `CorrectKey` ở trên):

```java
CorrectKey x = new CorrectKey("T1", "D1", "A");
CorrectKey y = new CorrectKey("T1", "D1", "B");
CorrectKey z = new CorrectKey("T2", "D1", "A");

assert x.equals(y);
assert y.equals(x);
assert x.hashCode() == y.hashCode();
assert !x.equals(z);
assert !x.equals(null);
```

Các câu lệnh `assert` chỉ được JVM thực thi khi bật assertions (ví dụ chạy `java -ea ...`); trong kiểm thử tự động nên dùng framework assertion phù hợp. Đây là mảnh minh họa đặt trong phạm vi có thể tham chiếu `CorrectKey`, không phải chương trình Java độc lập.

## 9. Tự kiểm tra

**Câu 1.** `a.equals(b)` trả `true`, nhưng `a.hashCode()` và `b.hashCode()` khác nhau. Chuyện gì sai và ảnh hưởng ở đâu?

**Đáp án:** Lớp vi phạm hợp đồng `hashCode()`. `HashSet` có thể chứa hai phần tử tương đương; `HashMap.get(b)` có thể không tìm được giá trị lưu bằng `a`.

**Câu 2.** Hai object không bằng nhau nhưng hash giống nhau. Có nhất thiết là lỗi không?

**Đáp án:** Không. Va chạm hash được cho phép; hash tốt giảm va chạm vì hiệu năng. Cấu trúc băm vẫn dùng so sánh khóa bằng equality để giải quyết.

**Câu 3.** Nếu không override phương thức nào và dùng `Object.equals/hashCode` mặc định, có vi phạm hợp đồng không?

**Đáp án:** Không. Vì `Object.equals` mặc định chỉ coi cùng instance là bằng nhau, cách hash mặc định tương thích với identity equality. Vấn đề thường đến khi override `equals()` để xem hai instance khác nhau là bằng nhau mà không override hash tương thích.

**Câu 4.** Có thể dùng `hashCode() { return 42; }` cho mọi object không?

**Đáp án:** Về mặt hợp đồng, có: mọi pair bằng nhau chắc chắn cùng hash. Nhưng hiệu năng cấu trúc băm có thể kém đáng kể do mọi khóa dồn vào cùng hash, nên thực tế nên tính hash từ các thành phần equality có ý nghĩa.

**Câu 5.** Một class có `equals()` dựa vào `id` và `hashCode()` dựa vào `(id, lastModified)`. Hai instance có cùng ID nhưng timestamp khác nhau gây lỗi gì?

**Đáp án:** `equals()` nói bằng nhau nhưng hash có thể khác nhau, vi phạm điều kiện bắt buộc. Trường `lastModified` không được ảnh hưởng hash nếu `equals()` bỏ qua nó.

**Câu 6.** Khi override `hashCode()` nhưng giữ `equals()` mặc định, có tự động vi phạm hợp đồng không?

**Đáp án:** Không tự động. Equality theo identity chỉ yêu cầu cùng một instance trả hash nhất quán trong lần thực thi khi thông tin dùng cho equality không đổi. Tuy nhiên, nếu thay đổi hash theo trạng thái mutable trong quá trình đối tượng làm khóa, việc tra cứu vẫn có thể hỏng; và khi đã thiết kế equality theo nội dung thì cần triển khai cả hai một cách đồng bộ.

---

**Ghi nhớ:** `equals()` định nghĩa **ai được coi là bằng ai**; `hashCode()` phải cho cùng giá trị đối với những cặp đó. Ghi đè `equals()` thường buộc bạn ghi đè `hashCode()` để không phá hợp đồng kế thừa từ `Object`. Viết và review hai phương thức như một cặp, chọn tập trường theo ngữ nghĩa, giữ các thành phần đó ổn định và kiểm tra thực tế bằng `HashSet`/`HashMap`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-code-contract-008">Hash code hằng có thể đúng hợp đồng nhưng phân bố kém</a>

<details>
<summary>Xem chi tiết</summary>

# Hash code hằng có thể đúng hợp đồng nhưng phân bố kém

## 1. Ý nghĩa của một hash code

Trong Java, phương thức `hashCode()` trả về một số nguyên `int` đại diện cho kết quả băm của đối tượng. Các cấu trúc dựa trên băm như `HashMap` và `HashSet` sử dụng kết quả này để định hướng việc tìm kiếm, chèn và xóa phần tử. **Hash code không phải mã định danh duy nhất** và không tự quyết định hai đối tượng có bằng nhau hay không: `equals(Object)` mới là phép kiểm tra bình đẳng cuối cùng khi các đối tượng có hash liên quan trùng nhau.

Hợp đồng của `Object.hashCode()` có các điểm quan trọng:

1. Nếu gọi `hashCode()` nhiều lần trên cùng một đối tượng trong một lần thực thi ứng dụng, khi dữ liệu dùng bởi `equals` không thay đổi thì kết quả phải nhất quán.
2. **Nếu `a.equals(b)` là `true`, `a.hashCode() == b.hashCode()` bắt buộc cũng phải là `true`.** Đây là ràng buộc quan trọng nhất khi ghi đè cả hai phương thức.
3. Nếu `a.equals(b)` là `false`, hai hash code **được phép bằng nhau**. Trả về hash khác nhau cho các đối tượng không bằng nhau là mục tiêu hiệu năng, không phải điều kiện bắt buộc để đúng hợp đồng.

Do đó, một lớp có thể định nghĩa:

~~~java
@Override
public int hashCode() {
    return 1;
}
~~~

Đây là **đoạn phương thức minh họa**, không phải một chương trình độc lập. Với mọi cặp đối tượng bằng nhau, hai giá trị hash đều là `1`, nên điều kiện bắt buộc được thỏa mãn. Nếu lớp có `equals` hợp lệ, một hash code hằng và ổn định có thể **đúng về ngữ nghĩa hợp đồng**, dù lựa chọn đó thường rất kém khi dùng trong bảng băm.

Điều cần phân biệt là **đúng hợp đồng** và **phân bố tốt**: hai yêu cầu này liên quan nhưng không đồng nhất.

## 2. Hash code hằng làm phân bố kém như thế nào?

Một bảng băm tổ chức các mục vào những vị trí gọi là *bucket* (nhóm lưu trữ). `HashMap` áp dụng thao tác trộn bit lên hash code rồi dùng kết quả cùng kích thước bảng để xác định bucket. Các hash code khác nhau có thể vẫn rơi vào cùng bucket, nhưng **mọi khóa có cùng hash code sẽ đi vào cùng bucket** tại một trạng thái bảng nhất định.

Nếu mọi khóa trả về `1`:

- `new Key(101)`, `new Key(102)` và `new Key(103)` có thể đều khác nhau theo `equals`.
- Chúng vẫn có cùng hash code, vì `hashCode()` luôn trả `1`.
- Khi lưu trong `HashMap` hoặc `HashSet`, các mục liên tục tranh cùng một bucket. Cấu trúc phải phân biệt chúng bằng kiểm tra khóa (bao gồm `equals`) thay vì nhanh chóng thu hẹp sang nhiều bucket khác nhau.

Trong bảng được phân bố tốt, nhiều thao tác `put`, `get`, `containsKey` hay `contains` có chi phí trung bình gần `O(1)` trong điều kiện thông thường. Khi một bucket có rất nhiều phần tử, thao tác có thể phải xem xét nhiều ứng viên hơn; với cách xử lý va chạm bằng danh sách, quét một bucket có `n` phần tử có thể tốn `O(n)`. Những phiên bản Java hiện đại có cơ chế chuyển một số bucket lớn của `HashMap` thành cấu trúc cây để giảm tác động trong các điều kiện phù hợp, nhưng điều đó **không khiến chiến lược hash code hằng trở nên tốt**. Hành vi chi tiết phụ thuộc số phần tử, năng lực của bảng và hình dạng các khóa.

`HashSet` được triển khai dựa trên `HashMap`, nên cũng chịu tác động tương tự của việc nhiều phần tử có chung hash code.

**Va chạm hash (collision) không làm mất phần tử.** Một bộ sưu tập băm triển khai đúng vẫn lưu được hai khóa khác nhau cùng hash; khi cần xác định khóa chính xác, nó kiểm tra bình đẳng. Vấn đề chủ yếu là số ứng viên phải xét tăng lên và hiệu năng dự kiến có thể suy giảm.

## 3. Ví dụ Java 17: đúng kết quả nhưng cách băm khác nhau

Ví dụ sử dụng hai loại khóa có cùng quy tắc bình đẳng: hai đối tượng **trong cùng loại khóa** được xem là bằng nhau nếu có cùng `id`. Loại `ConstantKey` trả về `7` cho mọi đối tượng; loại `IdKey` sử dụng `Integer.hashCode(id)` nên phản ánh giá trị `id`.

Lưu chương trình trong tệp **ConstantHashDemo.java**:

~~~java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ConstantHashDemo {
    static final class ConstantKey {
        private final int id;

        ConstantKey(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ConstantKey that)) {
                return false;
            }
            return id == that.id;
        }

        @Override
        public int hashCode() {
            return 7;
        }
    }

    static final class IdKey {
        private final int id;

        IdKey(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof IdKey that)) {
                return false;
            }
            return id == that.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    public static void main(String[] args) {
        ConstantKey one = new ConstantKey(1);
        ConstantKey sameOne = new ConstantKey(1);
        ConstantKey two = new ConstantKey(2);

        System.out.println("equal keys: " + one.equals(sameOne));
        System.out.println("equal keys share hash: "
                + (one.hashCode() == sameOne.hashCode()));
        System.out.println("different keys: " + one.equals(two));
        System.out.println("different keys collide: "
                + (one.hashCode() == two.hashCode()));

        Set<ConstantKey> constantSet = new HashSet<>();
        constantSet.add(one);
        constantSet.add(sameOne);
        constantSet.add(two);
        System.out.println("constant set size: " + constantSet.size());
        System.out.println("constant set contains id=2: "
                + constantSet.contains(new ConstantKey(2)));

        Map<ConstantKey, String> constantMap = new HashMap<>();
        constantMap.put(one, "one");
        constantMap.put(two, "two");
        System.out.println("constant map lookup id=2: "
                + constantMap.get(new ConstantKey(2)));

        IdKey idOne = new IdKey(1);
        IdKey idTwo = new IdKey(2);
        System.out.println("id hashes: "
                + idOne.hashCode() + ", " + idTwo.hashCode());

        Set<IdKey> idSet = new HashSet<>();
        idSet.add(idOne);
        idSet.add(idTwo);
        idSet.add(new IdKey(1));
        System.out.println("id set size: " + idSet.size());
        System.out.println("id set contains id=2: "
                + idSet.contains(new IdKey(2)));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac ConstantHashDemo.java
java ConstantHashDemo
~~~

Kết quả chuẩn:

~~~text
equal keys: true
equal keys share hash: true
different keys: false
different keys collide: true
constant set size: 2
constant set contains id=2: true
constant map lookup id=2: two
id hashes: 1, 2
id set size: 2
id set contains id=2: true
~~~

Những điều rút ra từ kết quả:

1. `one` và `sameOne` bằng nhau theo `equals` và cùng hash `7`. Hợp đồng được giữ đúng.
2. `one` và `two` không bằng nhau nhưng vẫn cùng hash `7`. Đây là **va chạm hợp lệ**, không phải một vi phạm hợp đồng.
3. `HashSet` chứa hai phần tử khác nhau, thay vì gộp tất cả đối tượng cùng hash thành một. Thêm `sameOne` không tăng kích thước vì nó bằng `one`.
4. `HashMap.get(new ConstantKey(2))` vẫn lấy được giá trị `"two"`, chứng tỏ tra cứu vẫn **đúng** khi có va chạm.
5. `IdKey` tạo hash `1` và `2` cho hai `id` tương ứng; đây là tín hiệu phân biệt tốt hơn với tập khóa này. Tuy nhiên, **hai hash khác nhau chưa bảo đảm sẽ thuộc hai bucket khác nhau**, vì bucket còn phụ thuộc cách tổ chức bảng.

Ví dụ trên minh họa **tính đúng đắn**, không phải bài đo tốc độ. Không thể kết luận chênh lệch thời gian từ output: một phép đo đáng tin cần số lượng khóa phù hợp, warm-up JVM, lặp lại nhiều lần và tránh nhiễu từ GC, JIT cùng môi trường chạy. Khi phân tích hiệu năng Java nghiêm túc, có thể dùng công cụ benchmark chuyên dụng như JMH thay vì suy luận từ một lần chạy `System.nanoTime()`.

## 4. Phân biệt phép bằng và mã băm trong mọi trường hợp

| Trạng thái hai đối tượng | Điều kiện về hashCode | Có thể xem bằng nhau không? |
| --- | --- | --- |
| `a.equals(b)` là `true` | **Phải** có `a.hashCode() == b.hashCode()` | Có |
| `a.equals(b)` là `false`, hash khác nhau | Hoàn toàn hợp lệ | Không |
| `a.equals(b)` là `false`, hash giống nhau | Hoàn toàn hợp lệ: va chạm | Không |
| Hash giống nhau nhưng chưa gọi `equals` | Không đủ thông tin để xác định bình đẳng | Phải dùng `equals` theo hợp đồng |

Không được thay `equals` bằng phép so sánh hash code:

~~~java
// Sai: cùng hash code không chứng minh hai đối tượng bằng nhau.
return this.hashCode() == other.hashCode();
~~~

Đây là **biểu thức minh họa lỗi trong phương thức equals**, không phải chương trình độc lập. Nó còn thiếu bước kiểm tra `other` khác `null`, đúng kiểu và có thể dẫn đến sai nghĩa bình đẳng. Với lớp trả về hash code hằng, cách triển khai này khiến mọi đối tượng khác nhau của lớp bị coi là bằng nhau, phá hỏng ý nghĩa của `equals`.

Cũng không nên viết kiểm thử kiểu `assertNotEquals(a.hashCode(), b.hashCode())` cho mọi `a` và `b` khác nhau. Hợp đồng không đòi hỏi hash code khác nhau cho mọi đối tượng không bằng nhau. Thay vào đó, kiểm thử tính đúng đắn của `equals`, tính nhất quán `hashCode` khi bằng nhau, và những thuộc tính phân bố đủ tốt đối với tập dữ liệu thực tế khi hiệu năng là yêu cầu.

## 5. Chọn hashCode tốt hơn mà vẫn giữ hợp đồng

### 5.1. Dùng đúng các trường quyết định bình đẳng

Nếu `equals` dựa trên `id` kiểu `int`, có thể dùng:

~~~java
@Override
public int hashCode() {
    return Integer.hashCode(id);
}
~~~

Đây là **phương thức có thể đặt trong IdKey**, không phải một chương trình độc lập. Với `int`, `Integer.hashCode(id)` chính là giá trị `id`. Cùng `id` thì vừa bằng nhau vừa cho cùng hash; nhiều `id` khác nhau tạo ra hash khác nhau, giảm va chạm trong tập khóa thông dụng so với trả về hằng số.

Nếu `equals` dựa trên hai trường `code` và `version`, một cách diễn đạt đơn giản là:

~~~java
@Override
public int hashCode() {
    return java.util.Objects.hash(code, version);
}
~~~

Đây là **đoạn phương thức minh họa trong lớp có hai trường tương ứng**. `Objects.hash` hỗ trợ các trường có thể là `null` và kết hợp hash theo thứ tự đối số. Nó tiện đọc, nhưng có thể phát sinh chi phí do varargs/boxing trong những đường xử lý rất nóng; nếu cần tối ưu thật sự, đo lường trước rồi cân nhắc cách kết hợp hash thủ công.

Một mẫu kết hợp cho hai trường tham chiếu là:

~~~java
@Override
public int hashCode() {
    int result = java.util.Objects.hashCode(code);
    result = 31 * result + java.util.Objects.hashCode(version);
    return result;
}
~~~

Đoạn này cũng cần đặt trong lớp có `code` và `version`. `Objects.hashCode(field)` trả `0` khi trường `null`, tránh `NullPointerException`. Giá trị `31` là một hệ số phổ biến, không phải quy định bắt buộc của hợp đồng. Phép nhân và cộng `int` có thể tràn số theo ngữ nghĩa Java mà vẫn tạo hash code hợp lệ. Dù dùng cách nào, **tập trường quyết định `hashCode` phải phù hợp với tiêu chí bình đẳng của `equals`**.

### 5.2. Tránh các chiến lược băm không đáng tin cậy

- **Hằng số:** đúng hợp đồng nếu ổn định và `equals` đúng, nhưng tạo va chạm có hệ thống giữa mọi khóa.
- **Số ngẫu nhiên mỗi lần gọi:** vi phạm tính nhất quán ngay cả khi đối tượng không đổi; tra cứu sau khi đưa vào bảng băm có thể thất bại.
- **Thời gian hiện tại hoặc bộ đếm toàn cục thay đổi:** gây biến thiên giữa các lần gọi và không phản ánh các trường quyết định bình đẳng.
- **`System.identityHashCode(this)` cho đối tượng bình đẳng theo giá trị:** hai đối tượng khác tham chiếu nhưng bằng nhau theo `equals` có thể có identity hash khác nhau, dẫn đến vi phạm hợp đồng.
- **Trường có thể sửa đổi trong lúc là khóa `HashMap`/phần tử `HashSet`:** hash có thể thay đổi sau khi đối tượng đã nằm trong bucket cũ, làm `get`, `contains` hoặc `remove` không còn tìm thấy đối tượng đó theo cách mong đợi.

Ưu tiên dùng các thuộc tính mang tính định danh hoặc giá trị **ổn định, phù hợp với `equals`**. Đối tượng khóa bất biến có trường `final` thường giúp bảo đảm tính nhất quán dễ dàng hơn. Nếu trường tham gia `equals` được phép thay đổi, phải quản lý việc dùng đối tượng làm khóa thật cẩn thận.

## 6. Vì sao không tối ưu hashCode bằng mọi giá?

Một hash code có chất lượng phân bố tốt là hash code giúp các khóa thực tế được trải tương đối đều trên các bucket, hạn chế các bucket quá đông. Tuy vậy, không tồn tại hàm băm `int` duy nhất cho mọi đối tượng: không gian khóa có thể lớn hơn số lượng giá trị `int`, nên về nguyên tắc **va chạm là không tránh khỏi**. Mục tiêu là giảm số va chạm có thể tránh được và tránh phân bố xấu trên dữ liệu sẽ dùng, chứ không đòi hỏi mọi khóa một mã duy nhất.

Chất lượng băm phải được cân bằng với chi phí tính toán. Một phép băm cực kỳ phức tạp cho vài khóa có thể tốn thời gian hơn lợi ích giảm va chạm. Với các lớp thông thường, sử dụng `Integer.hashCode`, `String.hashCode`, `Objects.hash` hoặc các phương thức băm thích hợp khác của kiểu dữ liệu thường là điểm khởi đầu hợp lý. Không nên tự xây dựng hàm băm tùy biến khi chưa có nhu cầu và bằng chứng.

Ngoài ra, các kiểu dữ liệu có biểu diễn băm riêng có thể xuất hiện va chạm khác nhau, ví dụ `String.hashCode()` không cam kết không trùng nhau. Sự tồn tại của va chạm đơn lẻ không làm hàm băm đó sai. Chỉ khi phân bố thường xuyên dẫn đến nhiều mục cùng bucket thì hiệu năng mới bị ảnh hưởng đáng kể.

## 7. Checklist kiểm tra một triển khai hashCode

Trước khi sử dụng một lớp làm phần tử của `HashSet` hoặc khóa của `HashMap`, hãy kiểm tra:

1. Mọi cặp đối tượng **bằng nhau theo `equals`** có **cùng hash code** không?
2. Những lần gọi `hashCode()` trên đối tượng không đổi có cùng kết quả trong lần thực thi không?
3. Hàm băm có dựa trên các trường xác định bình đẳng và không phụ thuộc số ngẫu nhiên, thời gian hay thông tin biến thiên ngoài ý muốn không?
4. Các trường nullable có được xử lý an toàn không?
5. Đối tượng có bị sửa những trường liên quan đến `equals`/`hashCode` trong thời gian nằm trong collection không?
6. Với nhiều khóa thực tế không bằng nhau, liệu số lượng mã băm trùng nhau có đang cao bất thường không? Nếu có, cần xem lại cách phối hợp trường và đo hiệu năng thay vì kết luận dựa vào một ví dụ nhỏ.

**Kết luận cần nhớ:** Hàm `hashCode()` luôn trả về cùng một số có thể **đúng hợp đồng** vì các đối tượng bằng nhau chắc chắn có cùng hash. Nhưng mọi khóa cũng trở thành trường hợp va chạm, làm mất lợi thế phân bố của cấu trúc băm. Hãy phân biệt **đúng kết quả** với **hiệu năng tốt**, và thiết kế `hashCode` nhất quán với `equals` từ những dữ liệu ổn định của đối tượng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-code-contract-009">Không dùng hashCode() làm mã định danh duy nhất hoặc mã bảo mật</a>

<details>
<summary>Xem chi tiết</summary>

# Không dùng `hashCode()` làm mã định danh duy nhất hoặc mã bảo mật

Trong Java, `hashCode()` trả về một số nguyên `int` dùng chủ yếu để hỗ trợ các cấu trúc dữ liệu dựa trên băm như `HashMap` và `HashSet`. Số này giúp phân phối và tra cứu phần tử hiệu quả, nhưng **không phải mã định danh duy nhất**, **không phải bí mật**, và **không phải hàm băm mật mã**. Hai đối tượng khác nhau có thể có cùng `hashCode()`, còn một hash dễ tính lại không chứng minh được ai đã tạo ra dữ liệu.

Bài học nằm trong **hợp đồng `hashCode` của Java**: hiểu chính xác điều gì được bảo đảm, vì sao dùng hash làm ID hoặc cơ chế bảo mật là sai, và chọn công cụ thay thế theo mục đích.

## 1. Hợp đồng `hashCode()` thực sự nói gì?

Mỗi lớp Java đều có `hashCode()` kế thừa từ `Object`, và lớp có thể ghi đè phương thức đó. Hợp đồng cốt lõi là:

1. Khi trạng thái tham gia `equals` không thay đổi trong **cùng một lần thực thi ứng dụng**, các lần gọi `hashCode()` trên một đối tượng phải cho kết quả nhất quán.
2. **Nếu `a.equals(b)` là `true`, thì `a.hashCode() == b.hashCode()` bắt buộc đúng.**
3. Nếu `a.equals(b)` là `false`, hai hash code **được phép bằng nhau**. Sinh hai giá trị khác nhau thường giúp hiệu năng tốt hơn, nhưng không phải yêu cầu bắt buộc.

Diễn đạt bằng logic:

```text
a.equals(b) == true  => a.hashCode() == b.hashCode()
a.hashCode() == b.hashCode() =/=> a.equals(b) == true
```

Chiều suy luận thứ hai **không hợp lệ**. Hash là công cụ thu hẹp các khả năng tra cứu, không thay thế bước kiểm tra bằng nhau.

Một lưu ý về `null`: không được gọi `null.hashCode()`. Nếu cần hash an toàn với `null`, dùng `Objects.hashCode(x)` (trả về `0` với `null`) hoặc `Objects.hash(...)` khi tính băm kết hợp nhiều trường.

## 2. Vì sao hash code không thể được coi là mã duy nhất?

### 2.1. Miền giá trị `int` có giới hạn

`hashCode()` trả về **`int` 32 bit**, tức chỉ có `2^32` (4.294.967.296) mẫu bit khác nhau, bao gồm cả số âm. Tập đối tượng hoặc giá trị đầu vào có thể cần phân biệt không bị giới hạn bởi số mẫu bit của một `int`. Theo nguyên lý chuồng bồ câu, nếu số giá trị cần gán vượt số hash khả dụng, **va chạm (collision) là điều không tránh khỏi**.

Ngay cả trước khi đạt giới hạn trên, một thuật toán băm cụ thể đã có thể tạo va chạm. Không có hợp đồng nào nói phải dùng hết `2^32` giá trị hoặc phân phối các giá trị một cách hoàn hảo.

### 2.2. Hai giá trị khác nhau có thể cho hash giống nhau

Trong Java, hai chuỗi **`"FB"` và `"Ea"`** có nội dung khác nhau nhưng có cùng `String.hashCode()`:

```text
"FB": 31 * 'F' + 'B' = 31 * 70 + 66 = 2236
"Ea": 31 * 'E' + 'a' = 31 * 69 + 97 = 2236
```

Với chuỗi hai ký tự trên, phép tính tuân theo thuật toán `String.hashCode()` được Java quy định; đây là **va chạm xác định**, không phải điều cần chờ ngẫu nhiên xảy ra. `"FB".equals("Ea")` vẫn là `false`.

Lớp tự định nghĩa `hashCode()` cũng có thể trả về cùng một số cho mọi đối tượng. Điều đó gây hiệu năng băm kém nhưng **không tự nó vi phạm hợp đồng** nếu `equals` và `hashCode` vẫn nhất quán.

### 2.3. Hash trùng không có nghĩa là cùng instance

`System.identityHashCode(obj)` bỏ qua cách lớp ghi đè `hashCode()` và lấy hash theo ngữ nghĩa danh tính mặc định của `Object`. Tuy nhiên, **identity hash code vẫn có thể va chạm** và không phải địa chỉ bộ nhớ. Nếu cần kiểm tra hai tham chiếu có trỏ tới cùng một đối tượng trong Java hay không, dùng `a == b`.

Không được tạo một bản đồ `Map<Integer, Object>` với `obj.hashCode()` làm khóa duy nhất rồi kết luận hash trùng là cùng đối tượng. Cách làm này có thể ghi đè nhầm bản ghi của đối tượng khác.

## 3. Ví dụ Java 17+: HashMap xử lý va chạm bằng `equals`

Chương trình dưới đây kết hợp va chạm có thật của `String` với một lớp cố tình trả về cùng một hash, để làm rõ rằng **băm trùng không xóa bỏ tính khác nhau của các khóa**.

Lưu thành `HashCodeNotIdentityDemo.java`:

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class Key {
    private final String id;

    Key(String id) {
        this.id = Objects.requireNonNull(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Key other)) {
            return false;
        }
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return 7; // Hợp đồng vẫn đúng nhưng mọi Key đều va chạm.
    }
}

public class HashCodeNotIdentityDemo {
    public static void main(String[] args) {
        String first = "FB";
        String second = "Ea";

        System.out.println("FB hash: " + first.hashCode());
        System.out.println("Ea hash: " + second.hashCode());
        System.out.println("FB equals Ea: " + first.equals(second));

        Set<String> words = new HashSet<>();
        words.add(first);
        words.add(second);
        System.out.println("HashSet size: " + words.size());

        Key a1 = new Key("A");
        Key a2 = new Key("A");
        Key b = new Key("B");
        System.out.println("A equals B: " + a1.equals(b));
        System.out.println("A equals another A: " + a1.equals(a2));
        System.out.println("A/B hashes equal: "
                + (a1.hashCode() == b.hashCode()));

        Map<Key, String> labels = new HashMap<>();
        labels.put(a1, "first");
        labels.put(b, "second");
        System.out.println("HashMap size: " + labels.size());
        System.out.println("Lookup new A: " + labels.get(a2));
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac HashCodeNotIdentityDemo.java
java HashCodeNotIdentityDemo
```

Đầu ra **xác định**:

```text
FB hash: 2236
Ea hash: 2236
FB equals Ea: false
HashSet size: 2
A equals B: false
A equals another A: true
A/B hashes equal: true
HashMap size: 2
Lookup new A: first
```

Các điều cần hiểu từ kết quả:

- `"FB"` và `"Ea"` có cùng hash 2236, nhưng `equals` trả `false`, nên `HashSet` lưu được cả hai.
- `Key.hashCode()` cố ý trả `7` cho mọi instance; `Key("A")` và `Key("B")` vẫn là hai khóa khác nhau vì `Key.equals` so sánh `id`.
- Hai `Key("A")` có cùng giá trị `id` và cùng hash nên `labels.get(a2)` tìm được bản ghi đặt bằng `a1`, dù `a1` và `a2` là hai instance.
- `HashMap` sử dụng hash để chọn vùng tra cứu, rồi dùng cơ chế so khớp khóa (trong đó có `equals`) để phân biệt phần tử va chạm. **Tự lấy `hashCode` làm ID và bỏ qua `equals` sẽ làm mất bước phân biệt quan trọng này.**
- Trả một hằng số trong `Key.hashCode()` chỉ nhằm minh họa tính đúng đắn khi có va chạm; **không phải cách cài đặt được khuyến nghị** vì nhiều va chạm làm giảm hiệu năng. Thực tế nên băm các trường tham gia `equals` bằng `Objects.hash(...)` hoặc thuật toán nhất quán phù hợp.

## 4. Vì sao không dùng `hashCode()` làm ID lưu trữ lâu dài?

Ngay cả khi tạm thời chưa quan sát va chạm, một hash code vẫn thiếu những tính chất cần có ở mã định danh.

| Yêu cầu đối với ID | Giới hạn của `hashCode()` |
| --- | --- |
| Phân biệt duy nhất các thực thể trong phạm vi hệ thống | Hash có thể va chạm, kể cả hai đối tượng không bằng nhau |
| Giữ ổn định qua vòng đời lưu trữ, tái khởi động và triển khai | `Object.hashCode()`/identity hash không có bảo đảm ổn định giữa các lần chạy; cách lớp ghi đè hash cũng có thể đổi |
| Không thay đổi khi thuộc tính trình bày thay đổi | Hash dựa trên các trường có thể thay đổi sẽ đổi theo trạng thái |
| Tương thích giữa các dịch vụ hoặc ngôn ngữ | Không có hợp đồng chung đảm bảo thuật toán `hashCode` tự định nghĩa tương thích giữa các hệ thống |
| Không để lộ một giá trị có thể đoán trước trong tình huống nhạy cảm | `hashCode()` không được thiết kế có tính bí mật hoặc khó dự đoán |

Cần phân biệt hai trường hợp:

1. **`String.hashCode()`** được quy định theo nội dung ký tự, nên với cùng chuỗi, thuật toán cho cùng kết quả qua các lần chạy Java. Điều này **không biến nó thành ID duy nhất hoặc bảo mật**: chuỗi khác nhau vẫn có thể trùng hash và ai cũng có thể tính lại kết quả.
2. **`Object.hashCode()` mặc định** gắn với cơ chế hashing danh tính của JVM, không mang hợp đồng ổn định xuyên các lần chạy. Không thể coi mã hash đó là khóa database hay địa chỉ đối tượng.

Nếu lớp override `hashCode()` bằng các trường mutable, giá trị băm còn có thể thay đổi khi trường thay đổi. Việc dùng đối tượng ấy làm khóa `HashMap` trước và sau khi thay đổi thường dẫn tới lỗi tra cứu. Hãy ưu tiên giá trị khóa bất biến, và cấp **ID riêng không phụ thuộc hash** khi đối tượng cần được nhận diện lâu dài.

### Chọn ID thay thế theo phạm vi

- **ID cơ sở dữ liệu:** khóa chính được database quản lý, như `BIGINT` sinh theo sequence/identity hoặc UUID; áp đặt ràng buộc `PRIMARY KEY`/`UNIQUE` để ngăn bản ghi trùng trong phạm vi quy định.
- **ID trao đổi giữa nhiều tiến trình hoặc dịch vụ:** UUID do hệ thống cấp phát (ví dụ `UUID.randomUUID()`) là lựa chọn thông dụng khi không có bộ cấp phát tập trung. UUID ngẫu nhiên giảm xác suất trùng xuống rất thấp trong điều kiện sử dụng phù hợp, nhưng **không phải bảo đảm toán học tuyệt đối**; nơi lưu trữ vẫn nên kiểm tra duy nhất.
- **Chỉ cần kiểm tra cùng đối tượng trong một JVM:** dùng `==` trên các reference; với map cần phân biệt instance, cân nhắc `IdentityHashMap` nếu đúng ngữ nghĩa bài toán.
- **ID tăng tuần tự trong một tiến trình:** một bộ đếm như `AtomicLong` có thể cấp các số phân biệt khi không vượt phạm vi, nhưng phải tự quản lý khởi tạo lại, tràn số và tính duy nhất giữa các tiến trình. Đây là giải pháp cấp ID có chủ đích, không phải `hashCode()`.

Đừng dùng `Integer.toHexString(obj.hashCode())` rồi coi chuỗi hệ 16 là một ID mới; chuyển cách biểu diễn **không tăng thông tin hoặc ngăn va chạm**.

## 5. Vì sao `hashCode()` không phải mã bảo mật?

Một hàm băm dùng cho collection và một công cụ mật mã có mục tiêu khác nhau:

| Tính chất | `hashCode()` Java | Công cụ phù hợp với bảo mật |
| --- | --- | --- |
| Mục tiêu chính | Phân phối khóa, hỗ trợ tra cứu | Chống giả mạo, dẫn xuất khóa, lưu mật khẩu hoặc tạo token tùy mục đích |
| Đầu ra | `int` 32 bit | Kích thước và thuật toán tùy yêu cầu, thường lớn hơn nhiều |
| Chống tìm giá trị đầu vào khi biết hash | Không bảo đảm | Password hashing/KDF chậm, có salt và chi phí tính toán |
| Chống kẻ tấn công cố tìm va chạm | Không bảo đảm | Hàm băm mật mã khi cần khả năng chống va chạm |
| Xác thực nguồn gốc dữ liệu | Không | HMAC hoặc chữ ký số dựa trên khóa đúng cách |
| Tạo chuỗi bí mật khó đoán để xác thực | Không | Bộ sinh số ngẫu nhiên mật mã như `SecureRandom` |

Các sai lầm thường gặp:

- **Lưu mật khẩu thành `password.hashCode()`:** miền 32 bit rất nhỏ, thuật toán rẻ, không salt và dễ tính thử hàng loạt. Khi cần lưu mật khẩu, dùng thuật toán chuyên dụng như **Argon2id**, **scrypt**, **bcrypt** hoặc **PBKDF2**, với salt ngẫu nhiên, tham số chi phí phù hợp và thư viện được bảo trì.
- **Dùng `userId.hashCode()` làm session ID, mã khôi phục mật khẩu, mã xác nhận hoặc API token:** đầu vào có thể đoán được và hash có thể bị tính lại. Kẻ tấn công không cần biết bí mật để tạo lại giá trị.
- **Gắn `message.hashCode()` để chứng minh dữ liệu không bị sửa trái phép:** bất kỳ ai sửa dữ liệu cũng có thể tính lại hash; hơn nữa va chạm rất dễ xuất hiện. Để kiểm chứng tính toàn vẹn và nguồn gốc trong mô hình có khóa chia sẻ, dùng **HMAC**; trong mô hình khóa công khai, cân nhắc **chữ ký số**.
- **Cho rằng chỉ cần chuyển hash sang hex hoặc Base64 sẽ an toàn:** đây chỉ là mã hóa biểu diễn, không tạo tính bí mật hoặc xác thực.

`MessageDigest` với SHA-256 cung cấp **hàm băm mật mã**, nhưng bản thân SHA-256 không phải cơ chế lưu mật khẩu tối ưu (vì quá nhanh), cũng không phải cơ chế xác thực người gửi khi thiếu khóa. Mỗi nhu cầu bảo mật cần đúng primitive.

### Ví dụ tạo token bí mật với `SecureRandom`

Để có thể biên dịch và chạy độc lập, lưu ví dụ Java 17 sau vào `SecureTokenDemo.java`. Chương trình chỉ in **độ dài** của token, không làm lộ token bí mật ra stdout.

```java
import java.security.SecureRandom;
import java.util.Base64;

public class SecureTokenDemo {
    public static void main(String[] args) {
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[32]; // 256 bit ngẫu nhiên
        secureRandom.nextBytes(bytes);

        String token = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
        System.out.println("token length = " + token.length());
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 -encoding UTF-8 SecureTokenDemo.java
java SecureTokenDemo
```

Stdout (256 bit = 32 byte, Base64 URL-safe không padding nên có 43 ký tự):

```text
token length = 43
```

`SecureRandom` tạo byte khó đoán theo cơ chế mật mã; Base64 URL-safe chỉ biểu diễn số byte đó thành chuỗi thuận tiện để gửi qua HTTP. Token là một **bí mật truy cập**: cần truyền qua HTTPS, giới hạn thời gian và quyền sử dụng, bảo vệ khi lưu trữ và không ghi nguyên token vào log. Không có giá trị stdout cố định vì token được sinh ngẫu nhiên. Không nên thay `SecureRandom` bằng `hashCode()`, `System.currentTimeMillis()` hay `java.util.Random` khi cần bí mật.

## 6. Phân biệt các API dễ bị nhầm

- **`obj.hashCode()`:** theo cài đặt của lớp; với value object thường phụ thuộc các trường có ý nghĩa trong `equals`.
- **`Objects.hashCode(obj)`:** tiện ích an toàn `null`, trả `0` cho `null`, còn lại gọi `obj.hashCode()`.
- **`Objects.hash(a, b, ...)`:** hash tổng hợp nhiều giá trị, hữu ích khi tự viết `hashCode()`. Vẫn chỉ là băm dạng `int`, không duy nhất và không có bảo đảm mật mã.
- **`System.identityHashCode(obj)`:** hash theo danh tính mặc định, kể cả khi lớp override `hashCode`; vẫn có va chạm, không phải địa chỉ bộ nhớ hay ID ổn định xuyên phiên chạy.
- **`MessageDigest` (ví dụ SHA-256):** công cụ băm mật mã cho dữ liệu byte; không thay `equals` trong collection và không tự tạo bí mật.
- **`SecureRandom`:** sinh giá trị ngẫu nhiên mật mã để tạo token/nonce phù hợp. Mã định danh ngẫu nhiên cũng cần quy trình kiểm soát va chạm và vòng đời.
- **`UUID.randomUUID()`:** tiện ích sinh UUID phiên bản 4; hữu ích cho ID phân tán, nhưng UUID hiển thị công khai không mặc nhiên đóng vai trò mật khẩu hoặc quyền truy cập.

## 7. Checklist thiết kế

Trước khi chọn một trường làm khóa, hãy tự hỏi:

1. **Cần so sánh bằng nhau hay cần duy nhất?** Nếu dùng collection dựa trên hash, viết `equals`/`hashCode` đúng hợp đồng; collection tự xử lý va chạm. Nếu cần ID duy nhất, cấp và quản lý ID riêng.
2. **Khóa có vượt phạm vi một lần chạy JVM không?** Nếu có, đừng dựa vào identity hash hoặc bất kỳ hash code nào không có hợp đồng ổn định phù hợp.
3. **Dữ liệu được dùng để xác thực hay ngăn giả mạo không?** Nếu có, dùng API mật mã tương ứng; `hashCode` không cung cấp bảo vệ này.
4. **Có cần giữ bí mật một token không?** Nếu có, token phải khó đoán, đủ entropy và có vòng đời phù hợp; không suy từ thông tin công khai bằng `hashCode()`.
5. **Các trường tham gia `equals`/`hashCode` có thay đổi không?** Nếu thay đổi sau khi đối tượng được dùng làm key, cơ chế tra cứu có thể hỏng; ưu tiên bất biến hoặc chọn khóa khác.

**Ghi nhớ:** `hashCode()` là một **gợi ý phân nhóm/tra cứu**; nhiều đối tượng khác nhau có thể dùng chung một số băm. Nó không chứng minh danh tính, không thay thế ID lưu trữ và không đáp ứng yêu cầu mật mã. Hãy dùng `equals` khi cần xác định bằng nhau, cấp ID theo phạm vi dữ liệu, và chọn primitive bảo mật chuyên dụng cho xác thực hoặc tạo bí mật.


</details>

- [Quay lại đầu trang](#back-to-top)
