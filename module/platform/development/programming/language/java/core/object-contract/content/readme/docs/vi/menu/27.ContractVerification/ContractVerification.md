<a id="back-to-top"></a>

# Kiểm chứng hợp đồng

## Menu
- [Tạo các đối tượng cùng tham chiếu, cùng giá trị và khác giá trị](#contract-verification-001)
- [Kiểm thử tính phản xạ, đối xứng và bắc cầu của equals()](#contract-verification-002)
- [Kiểm thử nhất quán, null và đối tượng khác kiểu](#contract-verification-003)
- [Kiểm thử equals() true luôn kéo theo hashCode() bằng nhau](#contract-verification-004)
- [Không viết kiểm thử đòi mọi đối tượng khác nhau phải có hash code khác](#contract-verification-005)
- [Kiểm thử bộ ba cho compareTo() và Comparator](#contract-verification-006)
- [Kiểm thử tra cứu bằng đối tượng bằng nhau trong HashMap và HashSet](#contract-verification-007)
- [Kiểm thử null, NaN, +0.0, -0.0, mảng và cấu trúc lồng](#contract-verification-008)
- [Kiểm thử kế thừa, thay đổi trạng thái và độc lập sau sao chép](#contract-verification-009)
- [Phân biệt kiểm thử tính đúng với đánh giá phân bố hoặc hiệu năng băm](#contract-verification-010)

## <a id="contract-verification-001">Tạo các đối tượng cùng tham chiếu, cùng giá trị và khác giá trị</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo các đối tượng cùng tham chiếu, cùng giá trị và khác giá trị để kiểm chứng hợp đồng Object

## 1. Mục tiêu của phép kiểm chứng

Khi kiểm tra hợp đồng `equals()` và `hashCode()` trong Java, cần tạo **có chủ đích** những đối tượng thuộc ba tình huống khác nhau:

1. **Cùng tham chiếu (same reference):** hai biến trỏ tới chính xác **cùng một đối tượng**; `a == b` là `true`.
2. **Cùng giá trị nhưng khác tham chiếu (equal values, distinct instances):** hai đối tượng được tạo riêng, mang cùng các thuộc tính dùng để định nghĩa tính bằng nhau; `a == b` là `false`, nhưng `a.equals(b)` phải là `true` nếu lớp có ngữ nghĩa so sánh giá trị.
3. **Khác giá trị (different values):** hai đối tượng có ít nhất một thuộc tính tham gia `equals()` khác nhau; `a.equals(b)` phải là `false` với quy tắc bằng nhau đã chọn.

Ba tình huống này là **dữ liệu kiểm thử**, không phải ba loại đối tượng đặc biệt của Java. Tình huống thứ ba nên dùng hai instance khác nhau; nếu giữ nguyên một instance rồi thay đổi trạng thái, bài kiểm chứng còn phát sinh vấn đề về tính biến đổi và vòng đời đối tượng.

Điểm cần phân biệt: Java có **đồng nhất đối tượng** (*identity*) và **bằng nhau về mặt logic** (*logical equality*). Toán tử `==` trên hai biến tham chiếu kiểm tra identity; `equals()` kiểm tra theo triển khai của lớp. `Object.equals()` mặc định cũng so sánh identity cho đến khi lớp ghi đè phương thức này.

## 2. Thiết lập đúng ba nhóm dữ liệu

Giả sử khóa định danh `UserKey` có hai thuộc tính: `id` và `version`; **cả hai** tham gia phép so sánh bằng nhau. Sử dụng các giá trị:

| Biến | Cách tạo | Nội dung | Quan hệ với `first` |
| --- | --- | --- | --- |
| `first` | `new UserKey("U-01", 1)` | `("U-01", 1)` | Đối tượng gốc |
| `alias` | `alias = first` | Cùng dữ liệu và cùng instance | `alias == first` |
| `equalValue` | `new UserKey("U-01", 1)` | `("U-01", 1)` | Khác instance, bằng giá trị |
| `differentValue` | `new UserKey("U-02", 1)` | `("U-02", 1)` | Khác instance, khác giá trị |
| `thirdEqual` | `new UserKey("U-01", 1)` | `("U-01", 1)` | Instance thứ ba bằng giá trị; dùng kiểm tra tính bắc cầu |

Không viết `UserKey equalValue = first;` để kiểm thử **bằng giá trị nhưng khác tham chiếu**: câu lệnh ấy chỉ tạo alias. Ngược lại, `new UserKey(...)` tạo instance riêng biệt nên so sánh `==` với instance cũ là `false` (khi cả hai cùng đang tồn tại).

Để tránh nhầm lẫn do tự động gộp chuỗi/interning hoặc cache của boxed number, ở đây tạo các instance `UserKey` tường minh bằng `new`. Cần định nghĩa `equals()` để xác định chính xác “cùng giá trị” nghĩa là gì trong miền nghiệp vụ.

## 3. Chương trình Java 17+ đầy đủ, chạy được

Lưu mã dưới đây thành tệp `ObjectRelationsDemo.java`. Lớp `UserKey` là immutable: trường `final`, không có setter; `equals()` xét hai thuộc tính và `hashCode()` dùng đúng hai thuộc tính ấy.

```java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ObjectRelationsDemo {
    static final class UserKey {
        private final String id;
        private final int version;

        UserKey(String id, int version) {
            this.id = Objects.requireNonNull(id, "id");
            this.version = version;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof UserKey other)) {
                return false;
            }
            return version == other.version && id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, version);
        }
    }

    private static void check(String name, boolean condition) {
        if (!condition) {
            throw new AssertionError("FAILED: " + name);
        }
        System.out.println("PASS: " + name);
    }

    public static void main(String[] args) {
        UserKey first = new UserKey("U-01", 1);
        UserKey alias = first;
        UserKey equalValue = new UserKey("U-01", 1);
        UserKey differentValue = new UserKey("U-02", 1);
        UserKey thirdEqual = new UserKey("U-01", 1);

        check("same reference", first == alias);
        check("equal values, different references",
                first != equalValue && first.equals(equalValue));
        check("different values", !first.equals(differentValue));

        check("reflexive", first.equals(first));
        check("symmetric",
                first.equals(equalValue) && equalValue.equals(first));
        check("transitive",
                first.equals(equalValue)
                        && equalValue.equals(thirdEqual)
                        && first.equals(thirdEqual));
        check("not equal to null", !first.equals(null));
        check("equal implies same hash",
                first.hashCode() == equalValue.hashCode());

        Set<UserKey> keys = new HashSet<>();
        keys.add(first);
        keys.add(alias);
        keys.add(equalValue);
        keys.add(differentValue);
        check("HashSet deduplicates equal keys", keys.size() == 2);
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac ObjectRelationsDemo.java
java ObjectRelationsDemo
```

Kết quả chuẩn:

```text
PASS: same reference
PASS: equal values, different references
PASS: different values
PASS: reflexive
PASS: symmetric
PASS: transitive
PASS: not equal to null
PASS: equal implies same hash
PASS: HashSet deduplicates equal keys
```

Hàm `check` ném `AssertionError` nếu bất kỳ kiểm tra nào thất bại. Vì vậy các dòng `PASS` là bằng chứng từng điều kiện được thỏa mãn; ví dụ **không phụ thuộc** vào cờ `-ea` của `assert` trong Java.

### Giải thích kết quả

- `alias = first` sao chép **giá trị tham chiếu**, không nhân bản đối tượng. Do đó `first == alias` đúng và `first.equals(alias)` cũng đúng.
- `equalValue = new UserKey("U-01", 1)` tạo instance khác, nên `first != equalValue`; tuy nhiên hai thuộc tính được so sánh bằng nhau, nên `equals()` đúng.
- `differentValue` có `id` khác: `first.equals(differentValue)` sai. Nếu lớp xét `version` trong `equals()`, thay `version` cũng là cách tạo dữ liệu khác giá trị.
- Ba đối tượng `first`, `equalValue`, `thirdEqual` kiểm tra tính **bắc cầu**: khi đối tượng thứ nhất bằng thứ hai và thứ hai bằng thứ ba, đối tượng thứ nhất phải bằng thứ ba.
- Hai đối tượng bằng nhau có hash code bằng nhau theo hợp đồng; ví dụ chỉ khẳng định mệnh đề này, **không** đòi hỏi hai đối tượng khác nhau phải có hash khác nhau.
- `HashSet` giữ hai giá trị logic: khóa `("U-01", 1)` và khóa `("U-02", 1)`; các tham chiếu alias và instance bằng giá trị không tạo thêm phần tử riêng.

## 4. Bảng phân biệt `==`, `equals()` và `hashCode()`

| Cặp so sánh | `==` | `equals()` | Kết luận |
| --- | --- | --- | --- |
| `first` và `alias` | `true` | `true` | Cùng instance; chắc chắn cùng trạng thái hiện tại |
| `first` và `equalValue` | `false` | `true` | Khác instance nhưng bằng logic theo hai thuộc tính |
| `first` và `differentValue` | `false` | `false` | Khác instance và khác logic theo hai thuộc tính |
| `first` và `thirdEqual` | `false` | `true` | Cung cấp đối tượng thứ ba để kiểm chứng tính bắc cầu |
| `first` và `null` | `false` | `false` | `first.equals(null)` phải trả về `false` |

**Lưu ý về hash:** `equals(x, y) == true` kéo theo `x.hashCode() == y.hashCode()`. Chiều ngược lại **không đúng**: hash collision là hợp lệ. Do đó không dùng `hashCode()` làm phép kiểm tra identity hoặc điều kiện đủ cho `equals()`, và không kiểm thử `first.hashCode() != differentValue.hashCode()` như một điều kiện bắt buộc.

Toán tử `==` cũng có thể áp dụng cho **giá trị primitive** như `int`, khi đó nó so sánh giá trị primitive; các ví dụ ở bài này áp dụng `==` cho **biến tham chiếu**.

## 5. Kiểm chứng đầy đủ hợp đồng `equals()` / `hashCode()`

Kiểm thử bằng ba nhóm dữ liệu ở trên tạo nền tảng cho các tính chất của `equals()`:

| Tính chất | Điều kiện cần kiểm tra | Dữ liệu phù hợp |
| --- | --- | --- |
| Phản xạ (*reflexive*) | `x.equals(x)` luôn `true` khi `x != null` | `first` với chính nó |
| Đối xứng (*symmetric*) | `x.equals(y)` và `y.equals(x)` cho cùng kết quả | `first`, `equalValue` |
| Bắc cầu (*transitive*) | `x=y`, `y=z` ⇒ `x=z` theo `equals()` | `first`, `equalValue`, `thirdEqual` |
| Nhất quán (*consistent*) | Không đổi thông tin tham gia so sánh thì kết quả phải ổn định | So lại nhiều lần khi trạng thái không đổi |
| Khác `null` | `x.equals(null)` là `false` với `x != null` | `first`, `null` |
| Hash tương ứng | `x.equals(y)` đúng ⇒ `x.hashCode() == y.hashCode()` | `first`, `equalValue` |
| Hash ổn định | Không đổi thông tin dùng bởi `equals()`, gọi `hashCode()` nhiều lần trong một lần chạy cho kết quả ổn định | `first` bất biến |

Các dòng bổ sung có thể đặt bên trong `main` của chương trình trên để kiểm chứng tính nhất quán (đây là phần **chèn thêm**, không phải một tệp Java riêng):

```java
boolean before = first.equals(equalValue);
boolean after = first.equals(equalValue);
check("consistent equals", before == after);

int firstHash = first.hashCode();
check("stable hash", firstHash == first.hashCode());
```

Dù ví dụ lặp hai lần là phép kiểm tra thực hành dễ hiểu, hai lời gọi đều đúng **không thể chứng minh tuyệt đối** mọi trường hợp của mọi lớp. Kiểm chứng hợp đồng nên chạy với các tập dữ liệu đa dạng, đặc biệt các giá trị biên, `null` nếu được cho phép trong thuộc tính, và các trạng thái có thể thay đổi. Định nghĩa `equals()` phải phản ánh chủ đích của lớp, không chỉ phù hợp một ví dụ.

### Vì sao phải ghi đè cả `equals()` và `hashCode()`?

Các cấu trúc dựa trên hash, như `HashSet` và `HashMap`, dùng hash code để phân nhóm trước khi xác nhận bằng `equals()`. Nếu hai instance được `equals()` coi là bằng nhau nhưng tạo ra hash code khác nhau, tập hợp/bản đồ có thể không tìm thấy phần tử hoặc lưu những phần tử logic bị trùng.

Để giảm sai sót, `UserKey.hashCode()` tính từ đúng `id` và `version`, chính là hai trường được `equals()` xét. Ngược lại, nếu đưa thêm trường không tham gia `equals()` vào `hashCode()`, ta có nguy cơ làm hai đối tượng bằng nhau có hash khác nhau và vi phạm hợp đồng.

## 6. Khi lớp không ghi đè `equals()`

Hai instance chứa các trường giống nhau **không tự động bằng nhau** chỉ vì chúng được tạo bằng cùng tham số:

```java
final class PlainKey {
    final String id;

    PlainKey(String id) {
        this.id = id;
    }
}

PlainKey a = new PlainKey("U-01");
PlainKey b = new PlainKey("U-01");

// a == b            -> false
// a.equals(b)       -> false (kế thừa Object.equals)
// a.equals(a)       -> true
```

Đây là đoạn minh họa đặt trong ngữ cảnh mã Java phù hợp (các câu lệnh tạo biến phải ở bên trong phương thức, constructor hoặc initializer). Vì `PlainKey` không ghi đè `equals()`, biểu thức `a.equals(b)` dùng ngữ nghĩa identity mặc định của `Object`. Nếu mục tiêu là so sánh `id`, hãy triển khai `equals()` cùng `hashCode()`, hoặc dùng kiểu dữ liệu cung cấp so sánh giá trị sẵn có.

### Tùy chọn gọn bằng `record` (Java 17)

`record` tự sinh `equals()` và `hashCode()` dựa trên các thành phần khai báo:

```java
record UserKeyRecord(String id, int version) {}

UserKeyRecord x = new UserKeyRecord("U-01", 1);
UserKeyRecord alias = x;
UserKeyRecord sameValue = new UserKeyRecord("U-01", 1);
UserKeyRecord other = new UserKeyRecord("U-02", 1);

// x == alias                 -> true
// x == sameValue             -> false
// x.equals(sameValue)        -> true
// x.equals(other)            -> false
```

Đây cũng là **mã minh họa**, giả sử các khai báo biến được đặt trong một phương thức. Record không mặc nhiên bảo đảm mọi thành phần đều bất biến sâu: nếu chứa array hoặc đối tượng có thể thay đổi, cần hiểu rõ cách các thành phần ấy thực hiện `equals()` / `hashCode()`. Ví dụ dùng `String` và `int` để hành vi bằng nhau rõ ràng, ổn định.

## 7. Sai lầm thường gặp khi dựng đối tượng kiểm thử

**Nhầm alias với instance bằng giá trị**

```java
UserKey b = a; // a == b là true; chưa hề kiểm thử hai đối tượng khác nhau.
```

Muốn kiểm tra ngữ nghĩa `equals()` giữa hai instance riêng thì tạo `new UserKey(...)` lần thứ hai.

**Dùng `String` literal hoặc boxed number để suy ra identity**

```java
String s1 = "java";
String s2 = "java";
// s1 == s2 có thể là true nhờ string interning;
// kết quả không đại diện cho ngữ nghĩa == của hai đối tượng tùy ý.
```

String literal cùng nội dung được intern theo quy tắc Java. Một số boxed value có thể dùng đối tượng cache, khiến `==` đôi khi đúng. Để kiểm chứng hợp đồng của **kiểu đang học**, hãy tạo instance của đúng kiểu đó, không suy từ hành vi cache của kiểu khác.

**Đổi các trường tham gia hash sau khi thêm vào `HashSet`**

Khi đối tượng dùng làm key bị thay đổi `equals()` / `hashCode()` trong lúc đang nằm trong `HashSet` hoặc `HashMap`, việc tìm kiếm/xóa có thể trở nên sai lệch. Ưu tiên key bất biến như `UserKey` trong chương trình đầy đủ. Nếu cần đối tượng thay đổi, phải thiết kế cách sử dụng trong collection phù hợp.

**So sánh hash như chứng cứ duy nhất**

`hashCode` trùng nhau không chứng minh hai đối tượng bằng nhau; khác hash chứng minh hai đối tượng **không thể bằng nhau nếu lớp tuân thủ hợp đồng**. Không dùng hash để xác định hai biến trỏ cùng instance; `System.identityHashCode()` cũng có thể trùng giữa hai đối tượng khác nhau và không thay cho `==`.

**Quên trường quan trọng trong bộ dữ liệu**

Nếu `equals()` xét `id` và `version`, hãy tạo thêm trường hợp chỉ khác `version`; ngược lại, có thể bỏ sót lỗi `equals()` vô tình so sánh mỗi `id`. Chẳng hạn:

```java
UserKey differentVersion = new UserKey("U-01", 2);
check("different version", !first.equals(differentVersion));
```

Đây là đoạn bổ sung vào `main` chương trình đầy đủ. Đừng viết test đòi hỏi khác giá trị từ một trường **không được quy định** tham gia `equals()`.

## 8. Bộ dữ liệu tối thiểu nên có trong test thật

| Nhóm dữ liệu | Tạo mẫu | Phép kiểm tra chính |
| --- | --- | --- |
| Cùng đối tượng | `alias = first` | `first == alias`, `first.equals(alias)` |
| Hai instance bằng giá trị | `equalValue = new UserKey("U-01", 1)` | `first != equalValue`, `first.equals(equalValue)` |
| Khác trường `id` | `new UserKey("U-02", 1)` | `!first.equals(...)` |
| Khác trường `version` | `new UserKey("U-01", 2)` | `!first.equals(...)` |
| Ba instance bằng giá trị | `first`, `equalValue`, `thirdEqual` | Tính bắc cầu |
| `null` | `first.equals(null)` | Trả `false`, không ném lỗi |
| Collection dựa trên hash | Thêm alias, đối tượng bằng giá trị, đối tượng khác giá trị | Không lưu khóa logic bị trùng |

Không cần kết luận rằng mọi lớp Java phải so sánh theo giá trị. Với đối tượng đại diện một thực thể có identity riêng, `equals()` có thể được thiết kế khác. **Định nghĩa hợp đồng trước, dựng bộ dữ liệu theo hợp đồng đó, rồi kiểm chứng ở cả ba quan hệ tham chiếu/giá trị.**

## 9. Câu hỏi tự kiểm tra

1. Có hai biến `a` và `b` được gán `b = a`. Có thể dùng cặp này để kiểm chứng `equals()` nhận ra hai instance có giá trị giống nhau không? **Không**, vì chỉ có một instance.
2. Hai đối tượng được tạo bằng `new` với cùng thuộc tính. `==` có trả về `true` không? **Không**, chúng là hai instance khác nhau (giả định cả hai tồn tại).
3. Hai instance cùng giá trị có luôn `equals()` bằng `true` không? **Không**: phụ thuộc triển khai; `Object.equals()` mặc định so sánh identity.
4. Nếu `x.equals(y)` đúng, có bắt buộc `x.hashCode() == y.hashCode()` không? **Có**, đó là hợp đồng.
5. Nếu hai hash code bằng nhau, có bắt buộc hai đối tượng bằng nhau không? **Không**, có thể xảy ra collision.
6. Vì sao thêm ba instance bằng giá trị lại hữu ích? **Để kiểm tra tính bắc cầu**, ngoài phản xạ và đối xứng.

**Kết luận thực hành:** dựng ba kiểu quan hệ bằng cách (1) gán alias, (2) tạo một instance mới với cùng các trường quyết định `equals()`, và (3) tạo một instance mới có trường quyết định `equals()` khác đi. Sau đó kiểm tra **`==` để xác định identity**, **`equals()` để xác định bằng logic**, và **`hashCode()` để bảo vệ hợp đồng khi làm việc với các cấu trúc dựa trên hash**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-verification-002">Kiểm thử tính phản xạ, đối xứng và bắc cầu của equals()</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử tính phản xạ, đối xứng và bắc cầu của `equals()` trong Java

`equals(Object)` là phương thức của `java.lang.Object`, được các lớp ghi đè (*override*) khi cần diễn đạt khái niệm **hai đối tượng bằng nhau về mặt giá trị**. Nếu không ghi đè, hành vi `Object.equals()` mặc định tương đương so sánh cùng tham chiếu (`==`). Khi ghi đè, lớp phải tuân theo hợp đồng của `equals()` để kết quả so sánh ổn định và tương thích với các cấu trúc như `HashSet`, `HashMap`.

Ba tính chất cần kiểm thử ở bài này là **phản xạ** (*reflexive*), **đối xứng** (*symmetric*) và **bắc cầu** (*transitive*). Việc thử bằng cả đối tượng giống và khác giá trị, cùng những trường hợp ở ranh giới kiểu kế thừa, quan trọng hơn việc chỉ kiểm tra một cặp đối tượng.

## 1. Phát biểu chính xác ba tính chất

Với các tham chiếu đối tượng khác `null` là `x`, `y`, `z`:

| Tính chất | Quy tắc bắt buộc | Câu hỏi kiểm thử |
| --- | --- | --- |
| **Phản xạ** | `x.equals(x) == true` | Một đối tượng có bằng chính nó không? |
| **Đối xứng** | `x.equals(y) == y.equals(x)` | Đổi chiều so sánh có làm thay đổi kết quả không? |
| **Bắc cầu** | Nếu `x.equals(y)` và `y.equals(z)` đều `true` thì `x.equals(z)` phải `true` | Một chuỗi các đối tượng bằng nhau có nhất quán không? |

Đối xứng có nghĩa **hai chiều cho cùng giá trị Boolean**, kể cả khi cả hai là `false`. Bắc cầu là phép kéo theo: nó chỉ yêu cầu `x.equals(z)` là `true` khi **cả hai điều kiện tiền đề** đều `true`. Không được diễn dịch sai thành “nếu `x` không bằng `y` và `y` không bằng `z` thì `x` phải không bằng `z`”.

Hai đối tượng có giá trị bằng nhau không bắt buộc là cùng tham chiếu: `new Key("A", 1)` và một đối tượng `new Key("A", 1)` khác có thể thỏa `equals() == true` trong khi `==` là `false`.

Hợp đồng `equals()` còn quy định **tính nhất quán** khi thông tin dùng để so sánh chưa thay đổi và `x.equals(null) == false` với mọi `x` khác `null`. Khi `equals()` trả về `true`, `hashCode()` của hai đối tượng cũng phải bằng nhau; chiều ngược lại không bắt buộc đúng. Vì vậy, bài kiểm thử tốt thường kiểm tra cả những điều kiện liên quan này.

## 2. Thiết kế bộ dữ liệu kiểm thử

Không nên chỉ tạo một đối tượng rồi kiểm tra `a.equals(a)`; phép thử đó chưa chạm tới hành vi so sánh **hai instance độc lập**. Với lớp khóa giá trị `Key(code, version)`, nên có ít nhất:

- Ba instance **khác tham chiếu nhưng bằng giá trị**: `a = Key("A", 1)`, `b = Key("A", 1)`, `c = Key("A", 1)`. Nhóm này tạo ra chuỗi `a = b`, `b = c` để kiểm tra bắc cầu thực sự.
- Một instance khác thuộc tính thứ nhất hoặc thứ hai, ví dụ `Key("A", 2)` và `Key("B", 1)`, nhằm bảo đảm việc so sánh cũng trả `false` đúng lúc.
- Trường hợp `null`, giá trị biên (chuỗi rỗng, số 0, số âm) nếu chúng được miền dữ liệu cho phép; với lớp **không final**, bổ sung các kiểu con liên quan.
- Nếu có nhiều trường tạo nên danh tính đối tượng, thay đổi **từng trường** giữa hai mẫu để phát hiện trường bị quên trong `equals()`.

Các phép thử không thể chứng minh một phương thức đúng với mọi đầu vào vô hạn, nhưng chúng xác nhận các quy tắc trên một tập mẫu có chủ đích. Với tập nhỏ, duyệt toàn bộ **cặp** và **bộ ba** làm tăng đáng kể khả năng phát hiện vi phạm so với vài assertion rời rạc.

## 3. Ví dụ Java 17+ độc lập, chạy được

Tạo file **`EqualsContractDemo.java`** với nội dung sau. Lớp `Key` là kiểu giá trị bất biến, khai báo `final` để không phát sinh trường hợp lớp con thay đổi định nghĩa bình đẳng. Phần `NearNumber` cố tình sai hợp đồng để minh họa lỗi bắc cầu.

```java
import java.util.List;
import java.util.Objects;

public class EqualsContractDemo {
    static final class Key {
        private final String code;
        private final int version;

        Key(String code, int version) {
            this.code = Objects.requireNonNull(code);
            this.version = version;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Key other)) {
                return false;
            }
            return version == other.version && code.equals(other.code);
        }

        @Override
        public int hashCode() {
            return 31 * code.hashCode() + version;
        }
    }

    // Cố tình vi phạm tính bắc cầu: gần nhau tối đa 1 đơn vị thì "bằng nhau".
    static final class NearNumber {
        private final int number;

        NearNumber(int number) {
            this.number = number;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof NearNumber other
                    && Math.abs((long) number - other.number) <= 1;
        }

        @Override
        public int hashCode() {
            return 0; // Cùng hash là hợp lệ, nhưng hiệu năng tra cứu có thể kém.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void verify(List<Key> values) {
        Key a = values.get(0);
        Key b = values.get(1);
        Key c = values.get(2);

        check(a != b && b != c && a != c,
                "Fixtures must be three distinct instances");
        check(a.equals(b) && b.equals(c),
                "Fixtures must include an equal chain");
        check(!a.equals(values.get(3)), "Version difference not detected");
        check(!a.equals(values.get(4)), "Code difference not detected");
        System.out.println("PASS fixtures");

        for (Key x : values) {
            check(x.equals(x), "Reflexive property failed");
        }
        System.out.println("PASS reflexive");

        int pairCount = 0;
        for (Key x : values) {
            for (Key y : values) {
                pairCount++;
                check(x.equals(y) == y.equals(x), "Symmetry failed");
            }
        }
        System.out.println("PASS symmetric (" + pairCount + " pairs)");

        int tripleCount = 0;
        for (Key x : values) {
            for (Key y : values) {
                for (Key z : values) {
                    tripleCount++;
                    if (x.equals(y) && y.equals(z)) {
                        check(x.equals(z), "Transitivity failed");
                    }
                }
            }
        }
        System.out.println("PASS transitive (" + tripleCount + " triples)");

        for (Key x : values) {
            check(!x.equals(null), "Null comparison failed");
            for (Key y : values) {
                if (x.equals(y)) {
                    check(x.hashCode() == y.hashCode(),
                            "Equal objects must share hashCode");
                }
            }
        }
        System.out.println("PASS null/hashCode");
    }

    public static void main(String[] args) {
        List<Key> values = List.of(
                new Key("A", 1),
                new Key("A", 1),
                new Key("A", 1),
                new Key("A", 2),
                new Key("B", 1));

        verify(values);

        NearNumber x = new NearNumber(1);
        NearNumber y = new NearNumber(2);
        NearNumber z = new NearNumber(3);
        check(x.equals(y) && y.equals(z) && !x.equals(z),
                "Broken example must violate transitivity");
        System.out.println("Broken transitivity: "
                + x.equals(y) + ", " + y.equals(z) + ", " + x.equals(z));
    }
}
```

Biên dịch với JDK 17 trở lên:

```bash
javac -encoding UTF-8 --release 17 EqualsContractDemo.java
java EqualsContractDemo
```

Kết quả stdout mong đợi:

```text
PASS fixtures
PASS reflexive
PASS symmetric (25 pairs)
PASS transitive (125 triples)
PASS null/hashCode
Broken transitivity: true, true, false
```

### Giải thích từng nhóm kiểm thử

**`PASS fixtures`:** ba đối tượng `a`, `b`, `c` có cùng `code/version` nhưng mỗi đối tượng được tạo bằng một lệnh `new` riêng. Việc xác nhận tiền đề này đặc biệt hữu ích: nếu không có ba đối tượng bằng nhau, vòng lặp bắc cầu có thể “đỗ” mà chưa thực sự khảo sát chuỗi các đối tượng khác tham chiếu.

**`PASS reflexive`:** vòng lặp gọi `x.equals(x)` cho cả 5 mẫu, nên một override trả sai khi đối tượng gặp chính nó sẽ bị phát hiện.

**`PASS symmetric (25 pairs)`:** 5 × 5 cặp **có thứ tự** được kiểm tra bằng `x.equals(y) == y.equals(x)`. Đối với một cặp vừa đổi chiều, một phía trả `true` và phía kia trả `false` sẽ gây `AssertionError`. Bao gồm các cặp khác giá trị giúp phát hiện kiểu so sánh bất đối xứng do lớp kế thừa hoặc so sánh sai thuộc tính.

**`PASS transitive (125 triples)`:** 5 × 5 × 5 bộ ba **có thứ tự** được xét. Mỗi khi `x.equals(y) && y.equals(z)` là đúng, kiểm thử yêu cầu `x.equals(z)` đúng. Điều kiện `if` là **định nghĩa logic của hợp đồng**, không phải việc “bỏ qua” kiểm thử; `fixtures` đã bảo đảm có chuỗi dương khác tham chiếu để kiểm tra.

**`PASS null/hashCode`:** thử `x.equals(null)` cho tất cả mẫu và kiểm tra mỗi cặp bằng nhau đều có cùng `hashCode()`. Đây là hai điều kiện hỗ trợ, không thay thế ba quy tắc cốt lõi.

**`Broken transitivity: true, true, false`:** `NearNumber(1)` được xem là bằng `NearNumber(2)`, `NearNumber(2)` bằng `NearNumber(3)`, nhưng `NearNumber(1)` không bằng `NearNumber(3)`. “Gần nhau trong sai số 1” là một **quan hệ không bắc cầu**, vì vậy không thích hợp làm định nghĩa `equals()`. Hàm `hashCode()` trả hằng 0 trong ví dụ này nhằm không tạo thêm một vi phạm `hashCode`; nó vẫn không thể chữa lỗi bắc cầu.

## 4. Những trường hợp dễ khiến `equals()` vi phạm hợp đồng

### 4.1. Bất đối xứng giữa lớp cha và lớp con

Một lỗi quen thuộc: lớp cha cho phép so sánh với mọi lớp con bằng `instanceof Parent`, nhưng lớp con lại chỉ chấp nhận chính kiểu lớp con (ví dụ vì thêm một trường `color`).

```java
// Minh họa hai đoạn điều kiện trong equals(); không phải chương trình độc lập.
// Parent.equals:
return other instanceof Parent p && this.id == p.id;

// Child.equals:
return other instanceof Child c
        && super.equals(other)
        && this.color.equals(c.color);
```

Nếu `p` là `Parent(id=7)` và `c` là `Child(id=7, color="red")`, thì `p.equals(c)` có thể `true` nhưng `c.equals(p)` là `false`. Đây là **vi phạm đối xứng**. Cách tránh đơn giản nếu lớp được thiết kế là kiểu giá trị không cần kế thừa: khai báo lớp `final`. Nếu có kế thừa, phải thiết kế chính sách so sánh kiểu cẩn thận (ví dụ dùng `getClass()` nhất quán theo thiết kế) và thử mọi chiều giữa các kiểu thực tế.

### 4.2. Dùng “gần bằng” làm `equals()`

Làm tròn, so sánh với epsilon, hoặc ngưỡng khoảng cách dễ phá tính bắc cầu. Khi cần so sánh số thực gần đúng, hãy đặt phép so sánh vào **phương thức riêng** như `approximatelyEqual`, nói rõ sai số; đừng mặc nhiên dùng làm `Object.equals()`. Trường hợp `double` còn cần xác định rõ ý nghĩa của `NaN`, `+0.0` và `-0.0`; dùng chiến lược so sánh nhất quán với hàm băm.

### 4.3. Quên trường hoặc phụ thuộc dữ liệu có thể thay đổi

Nếu `equals()` dựa trên `id` và `version` nhưng chỉ so sánh `id`, hai đối tượng lẽ ra khác nhau lại bị coi là bằng. Nếu `equals()` dựa vào các trường có thể thay đổi, kết quả so sánh giữa cùng hai đối tượng có thể thay đổi khi trạng thái đổi. Hợp đồng nhất quán chỉ yêu cầu kết quả ổn định **khi dữ liệu liên quan chưa thay đổi**; tuy nhiên sử dụng đối tượng làm khóa `HashMap` hoặc phần tử `HashSet` rồi thay đổi trường tham gia `equals/hashCode` có thể làm việc tìm kiếm thất bại.

### 4.4. Kiểm thử nhầm chiều hoặc nhầm điều kiện

- Chỉ kiểm tra `assertTrue(a.equals(b))` chưa kiểm tra `b.equals(a)`. Cần đo **cả hai chiều**, nhất là khi các object có kiểu runtime khác nhau.
- Chỉ lặp `x=y=z` cùng một tham chiếu không chứng minh kiểm thử đã phủ bắc cầu qua ba instance riêng.
- Không được đòi `x.equals(z)` khi `x.equals(y)` hoặc `y.equals(z)` là `false`; tiền đề bắc cầu không xảy ra.
- `Objects.equals(a,b)` giúp so sánh an toàn khi có `null`, nhưng bản thân tiện ích này **không sửa** được `equals()` bất đối xứng hoặc không bắc cầu của các đối tượng.
- Không dùng mỗi `assertEquals(a,b)` để kết luận đối xứng: framework sẽ đi qua một lệnh so sánh mà không tự động kiểm tra cả hai chiều theo hợp đồng.

## 5. Đưa vào bộ kiểm thử dự án

Trong JUnit 5, ba tính chất có thể diễn đạt bằng các test riêng sử dụng cùng fixture (ví dụ `List<Key> samples`), cùng tư duy như vòng lặp thuần Java ở trên:

```java
// Đoạn minh họa bên trong một lớp test JUnit 5 đã có samples.
@Test
void reflexive() {
    for (Key x : samples) {
        assertTrue(x.equals(x));
    }
}

@Test
void symmetric() {
    for (Key x : samples) {
        for (Key y : samples) {
            assertEquals(x.equals(y), y.equals(x));
        }
    }
}

@Test
void transitive() {
    for (Key x : samples) {
        for (Key y : samples) {
            for (Key z : samples) {
                if (x.equals(y) && y.equals(z)) {
                    assertTrue(x.equals(z));
                }
            }
        }
    }
}
```

Đây là **đoạn minh họa**, cần import `org.junit.jupiter.api.Test` và các assertion từ `org.junit.jupiter.api.Assertions`, khai báo `Key`/`samples` trong lớp test và cấu hình JUnit 5 trong dự án. Chương trình ở mục 3 là ví dụ **đầy đủ, không cần thư viện ngoài** để biên dịch và chạy ngay.

Với lớp thực tế có nhiều dạng đầu vào, tăng độ phủ bằng kiểm thử tham số hóa hoặc sinh dữ liệu (*property-based testing*): kiểm tra các đối tượng được tạo độc lập, mọi biến thể trường tạo danh tính, giá trị biên, kiểu con (nếu có) và các lần gọi lặp lại khi trạng thái không đổi. Luôn thêm ít nhất một fixture bảo đảm hai instance độc lập **được kỳ vọng bằng nhau**, và một bộ ba độc lập **có tiền đề bắc cầu đúng**; nếu không, nhiều assertion có thể đúng một cách rỗng.

## 6. Checklist khi review `equals()`

1. `x.equals(x)` luôn trả `true` với `x != null`?
2. `x.equals(y)` và `y.equals(x)` luôn cùng kết quả, kể cả khi hai đối tượng thuộc các kiểu có quan hệ kế thừa?
3. Khi `x.equals(y)` và `y.equals(z)` là `true`, `x.equals(z)` có `true`? Đã thử **ba đối tượng khác tham chiếu** chưa?
4. `x.equals(null)` trả `false`, kết quả có ổn định khi dữ liệu không đổi, và các cặp bằng nhau có cùng `hashCode()`?
5. Bộ fixture đã phủ cả trường hợp **bằng** và **khác**, sự thay đổi từng thuộc tính quan trọng và các trường hợp biên của bài toán?

Một kiểm thử `equals()` đáng tin cậy phải bắt được cả lỗi “hai đối tượng bằng nhau bị xem là khác” lẫn lỗi vi phạm logic của quan hệ bằng nhau; đồng thời không nhầm tính bắc cầu với phép so sánh gần đúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-verification-003">Kiểm thử nhất quán, null và đối tượng khác kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử `equals()`: nhất quán, `null` và đối tượng khác kiểu

## 1. Mục tiêu và bối cảnh

Trong Java, `Object.equals(Object)` trả lời câu hỏi hai đối tượng có **bằng nhau theo quan hệ equality mà lớp định nghĩa** hay không. Bản triển khai mặc định của `Object` kiểm tra danh tính; các lớp giá trị thường ghi đè để so sánh dữ liệu. Một phương thức `equals()` chạy được với hai đối tượng giống nhau vẫn có thể vi phạm hợp đồng khi gặp `null`, đối tượng khác kiểu hoặc khi được gọi nhiều lần.

Bài này tập trung vào ba phép kiểm chứng dễ bị bỏ sót:

1. **Nhất quán (consistency):** cùng một cặp đối tượng, nếu thông tin tham gia so sánh không đổi, các lần gọi `equals()` phải cho cùng kết quả.
2. **So sánh với `null`:** với mọi đối tượng `x` khác `null`, `x.equals(null)` phải trả về `false`, không ném `NullPointerException`.
3. **Đối tượng khác kiểu:** thử các giá trị thuộc kiểu không tương thích với mô hình equality của lớp; với lớp giá trị `AccountKey` trong bài, kết quả phải là `false`, không ném `ClassCastException`.

Ba phép kiểm tra thuộc hợp đồng chung của `Object.equals(Object)`. Để thiết kế đáng tin cậy, còn phải kiểm tra **phản xạ** (`x.equals(x)`), **đối xứng** (`x.equals(y) == y.equals(x)`) và **bắc cầu** (nếu `x == y` và `y == z` theo `equals` thì `x == z`). Dấu `==` trong câu diễn giải này là quan hệ logic, không phải thay thế cho việc gọi `equals()` trong mã.

Nếu một lớp ghi đè `equals()`, hãy rà soát `hashCode()` cùng lúc: **hai đối tượng bằng nhau phải có cùng hash code**. Hai đối tượng không bằng nhau vẫn được phép có hash code trùng nhau.

## 2. Hiểu đúng các tình huống cần kiểm thử

### 2.1. Tính nhất quán có điều kiện

Hợp đồng Java quy định các lần gọi `x.equals(y)` phải cho cùng kết quả **miễn là thông tin được dùng để so sánh trên `x` và `y` chưa thay đổi**. Vì vậy cần kiểm thử cả cặp bằng nhau lẫn cặp không bằng nhau, lặp lại nhiều lần trong cùng trạng thái.

Ví dụ, nếu hai khóa tài khoản cùng `username` và `tenantId`, `equals()` không được phụ thuộc thời gian hiện tại, số ngẫu nhiên, trạng thái bên ngoài hoặc thứ tự gọi phương thức. Nếu một trường dùng trong `equals()` bị sửa, kết quả có thể thay đổi hợp lệ; đó không tự động là vi phạm consistency. Tuy nhiên, đặt một đối tượng có trường khóa thay đổi được trong `HashSet` hoặc làm key của `HashMap` rất nguy hiểm vì vị trí hash có thể không còn phù hợp.

Một phép thử lặp 100 lần giúp phát hiện lỗi phổ biến, nhưng **không thể chứng minh tính đúng đắn cho mọi trạng thái và mọi tương tác**. Thiết kế bất biến và kiểm thử nhiều bộ dữ liệu cung cấp bảo đảm thực tế tốt hơn.

### 2.2. `null` là một đối số hợp lệ của phép so sánh

`a.equals(null)` phải là `false` với `a != null`. Điều này khác với gọi `null.equals(a)`: biểu thức thứ hai không thể dispatch phương thức và sẽ gây `NullPointerException`.

`Objects.equals(a, b)` là tiện ích hỗ trợ hai tham chiếu có thể `null`:

- `Objects.equals(null, null)` trả `true`.
- `Objects.equals(null, a)` trả `false` nếu `a` khác `null`.
- Khi `a` khác `null`, `Objects.equals(a, b)` sử dụng `a.equals(b)`.

Để trực tiếp kiểm tra hợp đồng của phương thức được ghi đè, hãy viết `!a.equals(null)`. Chỉ kiểm tra `Objects.equals(null, a)` sẽ **không gọi** `a.equals(...)` và không phát hiện được lỗi trong phương thức đó.

### 2.3. Đối tượng khác kiểu

Với một value class chỉ chấp nhận cùng kiểu, các lời gọi như `key.equals("alice")` và `key.equals(new Object())` phải trả `false` một cách an toàn. Bước `instanceof` trước khi ép kiểu tránh `ClassCastException`; biểu thức `null instanceof AccountKey` cũng là `false`.

**Lưu ý phạm vi:** hợp đồng `Object.equals()` không bắt buộc *mọi* cặp đối tượng khác lớp đều khác nhau. Một thiết kế đa hình có thể cho phép equality giữa hai lớp khác nhau nếu định nghĩa chặt chẽ và vẫn giữ được đối xứng, bắc cầu, nhất quán. Với khóa tài khoản `final` ở dưới, lựa chọn đúng là chỉ bằng đối tượng `AccountKey`. Không sao chép kỳ vọng “khác class luôn false” sang một thiết kế có yêu cầu equality liên kiểu mà chưa phân tích.

## 3. Ma trận kiểm thử đề xuất

| Trường hợp | Dữ liệu thử | Kết quả mong đợi |
| --- | --- | --- |
| Phản xạ | `a.equals(a)` | `true` |
| Cùng giá trị | `a.equals(b)` với `a` và `b` được tạo riêng nhưng có cùng trường định danh | `true` |
| Khác giá trị | `a.equals(d)` với `tenantId` khác | `false` |
| Nhất quán khi bằng | Lặp `a.equals(b)` khi không đổi trạng thái | Luôn `true` |
| Nhất quán khi không bằng | Lặp `a.equals(d)` khi không đổi trạng thái | Luôn `false` |
| `null` | `a.equals(null)` | `false`, không ngoại lệ |
| Khác kiểu: String | `a.equals("alice")` | `false`, không ngoại lệ |
| Khác kiểu: Object | `a.equals(new Object())` | `false`, không ngoại lệ |
| Đối xứng | `a.equals(b)` và `b.equals(a)` | Hai giá trị giống nhau |
| Bắc cầu | `a.equals(b)`, `b.equals(c)`, `a.equals(c)` | Cả ba là `true` |
| Hash tương thích | `a.equals(b)` là `true` | `a.hashCode() == b.hashCode()` |
| Tích hợp collection | Thêm `a` vào `HashSet`, tra cứu `b` | `contains(b)` là `true` |

Các giá trị `a`, `b`, `c`, `d` phải được dựng rõ ràng, có chủ đích. Kiểm tra một đối tượng với chính nó không đủ phát hiện lỗi so sánh giữa **hai instance khác nhau nhưng bằng giá trị**.

## 4. Ví dụ Java 17+ hoàn chỉnh, có kiểm tra thực thi

`AccountKey` biểu diễn khóa theo tên tài khoản và tenant. Lớp được đánh dấu `final` để quy tắc kiểm tra kiểu bằng `instanceof AccountKey` không phát sinh trường hợp lớp con thay đổi định nghĩa equality. Các trường `final` giữ thông tin tham gia so sánh ổn định sau khi tạo.

```java
// File: EqualsContractTest.java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class EqualsContractTest {
    private static final class AccountKey {
        private final String username;
        private final int tenantId;

        AccountKey(String username, int tenantId) {
            this.username = Objects.requireNonNull(username, "username");
            this.tenantId = tenantId;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof AccountKey other)) {
                return false;
            }
            return tenantId == other.tenantId
                    && username.equals(other.username);
        }

        @Override
        public int hashCode() {
            return Objects.hash(username, tenantId);
        }
    }

    private static void check(String name, boolean passed) {
        if (!passed) {
            throw new AssertionError("FAIL - " + name);
        }
        System.out.println("PASS - " + name);
    }

    public static void main(String[] args) {
        AccountKey a = new AccountKey("alice", 7);
        AccountKey b = new AccountKey("alice", 7);
        AccountKey c = new AccountKey("alice", 7);
        AccountKey d = new AccountKey("alice", 8);

        check("phan xa", a.equals(a));
        check("doi xung", a.equals(b) && b.equals(a));
        check("bac cau",
                a.equals(b) && b.equals(c) && a.equals(c));

        boolean equalBefore = a.equals(b);
        boolean differentBefore = a.equals(d);
        boolean consistent = true;
        for (int i = 0; i < 100; i++) {
            if (a.equals(b) != equalBefore
                    || a.equals(d) != differentBefore) {
                consistent = false;
                break;
            }
        }
        check("nhat quan 100 lan",
                equalBefore && !differentBefore && consistent);

        check("khong bang null", !a.equals(null));
        check("khac kieu String", !a.equals("alice"));
        check("khac kieu Object", !a.equals(new Object()));
        check("khac gia tri", !a.equals(d));

        check("hashCode tuong thich",
                a.hashCode() == b.hashCode());

        Set<AccountKey> keys = new HashSet<>();
        keys.add(a);
        check("HashSet tim thay khoa bang nhau", keys.contains(b));
    }
}
```

Biên dịch và chạy trên JDK 17 trở lên:

```bash
javac EqualsContractTest.java
java EqualsContractTest
```

Đầu ra chính xác với mã trên:

```text
PASS - phan xa
PASS - doi xung
PASS - bac cau
PASS - nhat quan 100 lan
PASS - khong bang null
PASS - khac kieu String
PASS - khac kieu Object
PASS - khac gia tri
PASS - hashCode tuong thich
PASS - HashSet tim thay khoa bang nhau
```

Nếu bất cứ điều kiện nào sai, `check(...)` ném `AssertionError` và chương trình kết thúc thất bại, nên kết quả không phụ thuộc việc bật cờ `-ea`. `a`, `b` và `c` là **ba đối tượng riêng**; `equals()` so sánh giá trị, còn `a == b` là `false`. `d` khác tenant nên không bằng `a`.

Trong `equals(Object)`, `this == obj` là tối ưu hợp lệ cho cùng tham chiếu; `instanceof` xử lý cả `null` lẫn kiểu khác; `hashCode()` sử dụng đúng hai trường mà `equals()` sử dụng. Không cần test rằng hai khóa khác nhau bắt buộc cho hash khác nhau: trùng hash là hợp lệ.

## 5. Các lỗi triển khai mà bộ kiểm thử cần bắt được

### Lỗi 1: Ép kiểu trước khi kiểm tra

Ví dụ **sai** (chỉ là fragment minh họa, không phải chương trình độc lập):

```java
@Override
public boolean equals(Object obj) {
    AccountKey other = (AccountKey) obj;
    return username.equals(other.username)
            && tenantId == other.tenantId;
}
```

Khi `obj` là `null`, truy cập `other.username` ném `NullPointerException`; khi đối tượng khác kiểu, ép kiểu gây `ClassCastException`. Kiểm tra `instanceof` hoặc kiểm tra `null` và class trước khi ép kiểu là cách sửa.

### Lỗi 2: Vô tình overload thay vì override

```java
// SAI: chữ ký khác equals(Object), không ghi đè Object.equals.
public boolean equals(AccountKey other) {
    return other != null
            && username.equals(other.username)
            && tenantId == other.tenantId;
}
```

`a.equals(b)` có thể gọi overload `equals(AccountKey)` và có vẻ đúng, nhưng collection hoặc biểu thức tham chiếu `Object` vẫn dùng `Object.equals(Object)`. Luôn dùng chính xác `public boolean equals(Object obj)` và ghi `@Override`; annotation này giúp trình biên dịch phát hiện chữ ký không ghi đè được phương thức dự kiến.

### Lỗi 3: Bỏ qua một trường định danh hoặc dùng trạng thái thay đổi

Nếu `equals()` chỉ so `username` mà nghiệp vụ yêu cầu thêm `tenantId`, hai tài khoản thuộc tenant khác nhau có thể bị xem là bằng nhau. Kiểm thử `a.equals(d)` trong ví dụ sẽ phát hiện lỗi.

Không tính các trường biến thiên như thời gian hiện tại, lần truy cập gần nhất, bộ đếm hay giá trị ngẫu nhiên vào equality nếu chúng không phải một phần định danh ổn định. Đối tượng dùng làm key trong `HashMap` hoặc phần tử của `HashSet` nên có các trường liên quan equality bất biến trong thời gian lưu trữ.

### Lỗi 4: `equals()` và `hashCode()` bất đồng

Nếu `equals()` cho hai đối tượng khác instance bằng nhau nhưng `hashCode()` vẫn lấy giá trị mặc định từ `Object`, việc tra cứu bằng đối tượng tương đương trong cấu trúc hash có thể thất bại. Hãy kiểm cả quy tắc hash và hành vi `HashSet.contains`/`HashMap.get`, vì đó là nơi lỗi dễ lộ ra nhất.

### Lỗi 5: Thử nghiệm không đủ đường đi

Chỉ gọi `equals()` một lần hoặc chỉ so sánh `a.equals(a)` là chưa đủ. Khi thiết kế test, hãy có ít nhất một cặp bằng theo giá trị nhưng khác tham chiếu, một cặp không bằng, các đối số `null`, nhiều kiểu không tương thích, và các lần gọi lặp.

Đối với lớp có nhiều nhánh hoặc trường nullable, mở rộng ma trận bằng `null` trong từng trường cho phép, mọi tổ hợp giá trị biên, dữ liệu khác biệt tại từng trường, và chiều so sánh đảo ngược. Có thể chuyển các nhóm kiểm tra thành unit test JUnit riêng hoặc parameterized test để khi thất bại biết rõ tình huống.

## 6. Phân biệt các khái niệm thường nhầm

- **`==` với reference:** so danh tính tham chiếu, không tự so giá trị của hai object riêng biệt.
- **`equals(Object)`:** quan hệ equality do lớp cung cấp; mặc định của `Object` gắn với danh tính.
- **`Objects.equals(a, b)`:** tiện ích so sánh null-safe; có thể bỏ qua lời gọi `a.equals` khi `a` là `null`.
- **`hashCode()`:** giá trị phục vụ cấu trúc hash; phải nhất quán với equality, không phải mã định danh duy nhất.
- **`compareTo()`/`Comparator`:** diễn tả thứ tự; tính tương thích với `equals()` cần xét riêng theo nhu cầu của cấu trúc dữ liệu và nghiệp vụ.

**Checklist trước khi chấp nhận một implementation `equals()`:** đúng chữ ký `equals(Object)`; các quy tắc phản xạ–đối xứng–bắc cầu; nhất quán nếu dữ liệu so sánh không đổi; `equals(null) == false`; xử lý an toàn các kiểu không được hỗ trợ; `hashCode` phù hợp; kiểm tra thực tế với collection.

## 7. Tài liệu chuẩn

- [Java SE 26 — `Object.equals(Object)` và `hashCode()`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/Object.html#equals(java.lang.Object))
- [Java SE 26 — `Objects.equals(Object,Object)`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/Objects.html#equals(java.lang.Object,java.lang.Object))
- [Java SE 26 — `HashSet`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/HashSet.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-verification-004">Kiểm thử equals() true luôn kéo theo hashCode() bằng nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử `equals() == true` luôn kéo theo `hashCode()` bằng nhau trong Java

## 1. Điều cần chứng minh

Trong Java, hai đối tượng **khác `null`** mà `a.equals(b)` trả về `true` bắt buộc có cùng kết quả `hashCode()` tại thời điểm kiểm tra, khi các dữ liệu tham gia vào `equals()` và `hashCode()` không bị thay đổi:

~~~text
Nếu a.equals(b) == true thì a.hashCode() == b.hashCode().
~~~

Đây là hợp đồng của `java.lang.Object`. Nó quan trọng vì các cấu trúc dựa trên băm như `HashMap` và `HashSet` dùng hash code để xác định vị trí tìm kiếm rồi mới xét bình đẳng. Nếu đối tượng bằng nhau mà hash khác nhau, việc tra cứu bằng một đối tượng tương đương có thể thất bại.

**Chiều ngược lại không được đảm bảo:** cùng hash code **không có nghĩa** hai đối tượng bằng nhau. Va chạm băm (*hash collision*) là hợp lệ; hash code là số `int`, trong khi số lượng giá trị đối tượng có thể lớn hơn rất nhiều.

| Quan hệ quan sát | Kết luận về hợp đồng |
| --- | --- |
| `a.equals(b) == true` và hash của hai bên bằng nhau | Đạt điều kiện đang kiểm thử |
| `a.equals(b) == true` nhưng hash khác nhau | **Vi phạm** hợp đồng |
| `a.equals(b) == false` và hash bằng nhau | Hợp lệ; đây có thể là va chạm |
| `a.equals(b) == false` và hash khác nhau | Hợp lệ |
| `a == b` | Cùng đối tượng; hash hai lần phải ổn định nếu dữ liệu liên quan không đổi |

Chú ý: một cặp đối tượng vượt qua điều kiện này **chưa chứng minh toàn bộ `equals()` đúng**. `equals()` còn phải có tính phản xạ, đối xứng, bắc cầu và tính nhất quán; `x.equals(null)` phải là `false`. Một bộ kiểm thử tốt nên kiểm tra các tính chất đó, nhưng trọng tâm ở bài này là mệnh đề kéo theo từ `equals()` sang `hashCode()`.

## 2. Vì sao phải kiểm thử nhiều đối tượng bằng nhau nhưng khác tham chiếu?

Kiểm thử chỉ với `a.equals(a)` quá yếu: khi gọi `hashCode()` hai lần trên cùng đối tượng không thay đổi, kết quả vốn phải giống nhau. Cần tạo **ít nhất hai instance riêng biệt nhưng bình đẳng về mặt logic**.

Ví dụ một khóa khách hàng được nhận diện bằng `tenant` và `customerId`. Hai `CustomerKey` có cặp trường ấy bằng nhau phải được xem là bằng nhau, dù được tạo ở hai nơi. Cùng một tập dữ liệu sẽ quyết định **cả** `equals()` lẫn `hashCode()`:

~~~java
@Override
public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof CustomerKey other)) return false;
    return customerId == other.customerId && tenant.equals(other.tenant);
}

@Override
public int hashCode() {
    return Objects.hash(tenant, customerId);
}
~~~

Đoạn trên chỉ là hai phương thức minh họa bên trong lớp có các trường `tenant` và `customerId`; chương trình đầy đủ, chạy độc lập nằm ở phần tiếp theo. Điều thiết yếu không phải bắt buộc dùng `Objects.hash()` mà là **hai phép tính có cùng chính sách bình đẳng**. Có thể viết thuật toán hash khác miễn mọi cặp bằng nhau vẫn có cùng hash.

## 3. Chương trình Java 17: trường hợp đúng và trường hợp sai

Ví dụ có hai lớp khóa:

- `CustomerKey` thực hiện hợp đồng đúng: cả `equals()` và `hashCode()` dùng `tenant`, `customerId`; các trường là `final`.
- `BrokenKey` cố ý sai: `equals()` so sánh tên không phân biệt chữ hoa/chữ thường, nhưng `hashCode()` lại băm chuỗi gốc có phân biệt hoa/thường.

Lưu tệp **HashContractDemo.java** với toàn bộ nội dung sau:

~~~java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class HashContractDemo {

    static final class CustomerKey {
        private final String tenant;
        private final int customerId;

        CustomerKey(String tenant, int customerId) {
            this.tenant = Objects.requireNonNull(tenant);
            this.customerId = customerId;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CustomerKey other)) {
                return false;
            }
            return customerId == other.customerId
                    && tenant.equals(other.tenant);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenant, customerId);
        }
    }

    static final class BrokenKey {
        private final String label;

        BrokenKey(String label) {
            this.label = Objects.requireNonNull(label);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof BrokenKey other)) {
                return false;
            }
            return label.equalsIgnoreCase(other.label);
        }

        @Override
        public int hashCode() {
            return label.hashCode(); // Cố ý sai với equalsIgnoreCase().
        }
    }

    static int verifyAllPairs(CustomerKey[] samples) {
        int checks = 0;
        for (CustomerKey left : samples) {
            for (CustomerKey right : samples) {
                checks++;
                if (left.equals(right)
                        && left.hashCode() != right.hashCode()) {
                    throw new AssertionError(
                            "Equal keys must have equal hash codes");
                }
            }
        }
        return checks;
    }

    public static void main(String[] args) {
        CustomerKey a = new CustomerKey("VN", 42);
        CustomerKey b = new CustomerKey(new String("VN"), 42);
        CustomerKey c = new CustomerKey("VN", 43);
        CustomerKey d = new CustomerKey("SG", 42);

        System.out.println("Different objects: " + (a != b));
        System.out.println("Equal keys: " + a.equals(b));
        System.out.println("Equal hash codes: "
                + (a.hashCode() == b.hashCode()));

        Set<CustomerKey> goodSet = new HashSet<>();
        goodSet.add(a);
        System.out.println("HashSet finds equal key: "
                + goodSet.contains(b));

        int checks = verifyAllPairs(
                new CustomerKey[] { a, b, c, d });
        System.out.println("Pair checks passed: " + checks);

        BrokenKey x = new BrokenKey("A");
        BrokenKey y = new BrokenKey("a");
        System.out.println("Broken equals: " + x.equals(y));
        System.out.println("Broken hash codes equal: "
                + (x.hashCode() == y.hashCode()));

        Set<BrokenKey> badSet = new HashSet<>();
        badSet.add(x);
        System.out.println("Broken HashSet finds equal key: "
                + badSet.contains(y));

        System.out.println("Unequal strings can collide: "
                + ("Aa".hashCode() == "BB".hashCode()));
    }
}
~~~

Chạy với JDK 17 trở lên:

~~~bash
javac HashContractDemo.java
java HashContractDemo
~~~

Kết quả chuẩn:

~~~text
Different objects: true
Equal keys: true
Equal hash codes: true
HashSet finds equal key: true
Pair checks passed: 16
Broken equals: true
Broken hash codes equal: false
Broken HashSet finds equal key: false
Unequal strings can collide: true
~~~

**Phân tích:**

1. `a != b` xác nhận hai khóa là hai object riêng. `new String("VN")` còn tạo một đối tượng chuỗi riêng để kiểm tra bình đẳng theo **giá trị**, thay vì vô tình dựa vào cùng reference.
2. `a.equals(b)` đúng vì `tenant` và `customerId` giống nhau; `Objects.hash(tenant, customerId)` đưa hai khóa bằng nhau đến cùng hash.
3. `goodSet.contains(b)` thành công sau khi thêm `a`, đúng với điều người dùng collection kỳ vọng.
4. Hàm `verifyAllPairs` duyệt 4 × 4 = **16 cặp có thứ tự**. Các cặp bằng nhau phải có hash giống nhau; các cặp khác nhau không bị bắt buộc có hash khác nhau. Kiểm thử này bao gồm cả chiều `(a,b)` và `(b,a)`.
5. Với `BrokenKey("A")` và `BrokenKey("a")`, `equalsIgnoreCase()` trả `true` nhưng `String.hashCode()` của `"A"` và `"a"` khác nhau. `HashSet` có thể không tìm thấy khóa tương đương; trong chương trình này kết quả là `false`.
6. `"Aa"` và `"BB"` là hai chuỗi không bằng nhau nhưng có cùng Java string hash code (2112). Va chạm này hợp lệ và minh họa vì sao **không được viết test yêu cầu hai object khác nhau phải có hash khác nhau**.

`BrokenKey` là **phản ví dụ chủ ý**, không được dùng làm khóa trong hệ thống thực tế.

## 4. Sửa nguyên nhân sai: cùng chuẩn hóa cho equality và hashing

Sai lầm thường gặp là định nghĩa `equals()` không phân biệt hoa/thường nhưng lại băm dữ liệu thô. Không nên sửa bằng cách ngẫu nhiên thay `hashCode()` thành một biểu thức khác mà chưa xác nhận nó tương ứng với cùng quan hệ bình đẳng.

Nếu domain muốn khóa **không phân biệt hoa/thường theo quy tắc chuẩn hóa đã chọn**, có thể chuyển dữ liệu thành một dạng chuẩn tại constructor, rồi để **cả equals và hashCode xử lý chính giá trị chuẩn hóa ấy**:

~~~java
import java.util.Locale;
import java.util.Objects;

final class NormalizedKey {
    private final String normalized;

    NormalizedKey(String raw) {
        normalized = Objects.requireNonNull(raw)
                .toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof NormalizedKey other
                && normalized.equals(other.normalized);
    }

    @Override
    public int hashCode() {
        return normalized.hashCode();
    }
}
~~~

Đây là **lớp minh họa**, chưa phải chương trình `main` độc lập. Trong đó, `new NormalizedKey("API")` và `new NormalizedKey("api")` bằng nhau và có hash bằng nhau. `Locale.ROOT` tránh phụ thuộc locale mặc định của máy khi chuẩn hóa. Quy tắc `toLowerCase(Locale.ROOT)` định nghĩa bình đẳng theo chuỗi sau chuẩn hóa; nó **không nhất thiết tương đương `String.equalsIgnoreCase()` cho mọi Unicode**. Nếu sản phẩm cần so sánh Unicode phức tạp, domain phải chốt rõ thuật toán chuẩn hóa/case folding và dùng **cùng một biểu diễn chuẩn** cho cả hai phương thức.

Một lớp dữ liệu định danh dạng `record` cũng thuận tiện nếu tất cả component có semantics bình đẳng phù hợp:

~~~java
record CustomerId(String tenant, int number) {
    CustomerId {
        Objects.requireNonNull(tenant);
    }
}
~~~

Record Java tự sinh `equals()` và `hashCode()` nhất quán với các component. Tuy nhiên, nếu component là mutable array hay mutable object, record không tự bảo đảm tính bất biến sâu; quy tắc so sánh component, defensive copy và việc thay đổi dữ liệu sau khi làm key vẫn phải xem xét.

## 5. Viết phép kiểm thử đúng cách

### Test một cặp bằng nhau

Một kiểm thử hợp đồng tốt có thứ tự:

1. Chuẩn bị hai instance với cùng dữ liệu định danh, **khác reference**.
2. Xác minh tiền điều kiện: `assertNotSame(a, b)` và `assertEquals(a, b)`. Nếu thiếu `assertEquals(a,b)`, phép kéo theo có thể vượt qua một cách **vô nghĩa** vì hai đối tượng không bằng nhau.
3. Xác minh kết luận: `assertEquals(a.hashCode(), b.hashCode())`.
4. Kiểm thêm `HashSet`/`HashMap` để xác nhận hành vi tích hợp mà hợp đồng này bảo vệ.

Với JUnit Jupiter (JUnit 5), ví dụ sau dùng lớp `HashContractDemo.CustomerKey` của chương trình Java ở trên; cần đưa JUnit Jupiter vào test classpath bằng Maven/Gradle hoặc cấu hình dự án tương ứng:

~~~java
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;

class CustomerKeyContractTest {
    @Test
    void equalObjectsMustHaveSameHashCode() {
        var a = new HashContractDemo.CustomerKey("VN", 42);
        var b = new HashContractDemo.CustomerKey("VN", 42);

        assertNotSame(a, b);
        assertEquals(a, b); // Xác minh tiền đề equals() == true.
        assertEquals(a.hashCode(), b.hashCode());

        Set<HashContractDemo.CustomerKey> keys = new HashSet<>();
        keys.add(a);
        assertTrue(keys.contains(b));
    }
}
~~~

Đây là **test class dùng JUnit**, không chạy trực tiếp bằng `java CustomerKeyContractTest`; nó được test runner chạy sau khi thêm dependency. Trường hợp JUnit 5 hiện đại, project có thể dùng nền tảng JUnit Platform thông qua Maven Surefire hoặc Gradle `test`.

### Kiểm thử bằng bảng trường hợp

Đừng chỉ dùng một cặp “happy path”. Chuẩn bị một tập nhỏ nhưng bao quát:

| Trường hợp | Kết quả `equals()` kỳ vọng | Hash assertion |
| --- | --- | --- |
| Hai object riêng, đủ tất cả trường định danh giống nhau | `true` | **Phải bằng nhau** |
| Cùng giá trị tạo từ đường khởi tạo khác (builder, constructor, parser) | `true` nếu domain tương đương | **Phải bằng nhau** |
| Khác một trường được `equals()` xét | `false` | Không áp đặt khác nhau |
| Một trường bị `equals()` bỏ qua | `true` nếu tất cả trường quan trọng giống | **Phải bằng nhau** |
| Chuỗi khác chữ hoa/thường nhưng domain chuẩn hóa chung | `true` theo quy tắc domain | **Phải bằng nhau** |
| Trường có thể `null` | Theo chính sách bình đẳng cho `null` | Nếu bằng nhau thì hash phải bằng |
| Đối tượng bên phải là `null` | `false` | **Không gọi `null.hashCode()`** |
| Hai instance có cùng giá trị, một instance là bản sao sâu | `true` nếu equality theo value | **Phải bằng nhau** |
| Hai đối tượng không bằng nhau nhưng tình cờ cùng hash | `false` | Hợp lệ, không fail |

Có thể duyệt mọi cặp trong tập mẫu, như `verifyAllPairs()` ở chương trình, để dễ phát hiện bất nhất theo tổ hợp. Với project lớn, bổ sung property-based testing: sinh nhiều giá trị domain, **tạo cặp tương đương có chủ đích** và kiểm tra implication; bộ sinh hoàn toàn ngẫu nhiên thường hầu như không tạo được hai khóa bằng nhau nên kiểm thử yếu.

### Đọc đúng mệnh đề kéo theo

Khi lập trình test theo logic:

~~~java
if (a.equals(b)) {
    assert a.hashCode() == b.hashCode();
}
~~~

Đoạn này chỉ minh họa, phụ thuộc bật Java assertions bằng `-ea` và giả định cả hai tham chiếu khác `null`. Nếu `a.equals(b)` luôn là `false` do bug khác, nhánh kiểm tra không chạy mà test vẫn “pass”. Vì thế phải có **test chủ động tạo cặp bằng nhau**, khẳng định `equals() == true` trước rồi mới kiểm hash.

Trong test suite thực tế, dùng `assertEquals(expected, actual)` của framework hoặc ném `AssertionError`; Java keyword `assert` bị vô hiệu mặc định nếu không khởi chạy JVM với `-ea`.

## 6. Những lỗi thiết kế dễ làm test thất bại

### `equals()` xét một tập field, `hashCode()` xét tập field khác

Ví dụ equality so sánh `id` nhưng hash lại tính cả `displayName`. Hai object trùng `id`, khác tên sẽ bằng nhau theo `equals()` mà hash có thể khác. Nếu một field **không tham gia equality** nhưng tham gia hashing, đây là dấu hiệu báo động; thay cả hai theo một khóa logic được thiết kế thống nhất.

Không nhất thiết mọi field `equals()` dùng đều phải xuất hiện trực tiếp trong công thức hash. Một hàm hash luôn trả `0` vẫn bảo toàn điều kiện “equal → same hash”, nhưng gây rất nhiều va chạm và làm giảm hiệu suất bảng băm. Mục tiêu là **đúng hợp đồng trước**, rồi phân phối hash hợp lý.

### Mutable keys trong `HashMap`/`HashSet`

Giả sử khóa có trường `id` mutable và `hashCode()` phụ thuộc `id`. Thêm khóa vào `HashSet` với `id=1`, rồi sửa thành `id=2`. Khi tìm lại, bảng băm có thể tra một bucket khác và không thấy phần tử đã thêm. Đây là lỗi ngay cả khi `equals()` và `hashCode()` **hoàn toàn nhất quán tại từng thời điểm**.

- Ưu tiên key bất biến (`final` fields với immutable values hoặc defensive copies của mutable state).
- Nếu buộc phải sửa dữ liệu định danh, hãy loại key khỏi collection **trước khi sửa** và thêm lại khi đã ở trạng thái mới, với điều kiện quy trình nhất quán và được đồng bộ nếu nhiều luồng cùng truy cập.
- Tránh để code ngoài có thể âm thầm sửa array/list bên trong một key sau khi insert.

Kiểm thử implication trên các instance **ổn định** không thay thế kiểm thử vòng đời của mutable key.

### Mảng và collections

Array Java dùng `Object.equals()` và `Object.hashCode()` nếu không được xử lý đặc biệt, tức chủ yếu theo identity. Nếu một lớp quyết định equality dựa trên **nội dung mảng**, hashing cũng phải dựa trên nội dung tương ứng:

~~~java
import java.util.Arrays;

@Override
public boolean equals(Object obj) {
    return obj instanceof ByteKey other
            && Arrays.equals(bytes, other.bytes);
}

@Override
public int hashCode() {
    return Arrays.hashCode(bytes);
}
~~~

Hai phương thức trên là minh họa trong lớp `ByteKey` có trường `byte[] bytes`; muốn dùng làm key ổn định còn phải copy mảng trong constructor và bảo vệ getter. Với array nhiều chiều, tùy kiểu dữ liệu và semantics cần xét `Arrays.deepEquals()` và `Arrays.deepHashCode()` tương ứng. Với collection, equality và hashing của các implementation chuẩn thường theo nội dung nhưng các phần tử mutable vẫn có thể làm thay đổi cả hai kết quả.

### Kế thừa và so sánh khác kiểu

Một lớp cơ sở cho phép subclass bằng nhau theo `instanceof`, trong khi subclass lại thêm field vào `equals()`, có thể vi phạm đối xứng/bắc cầu. Nếu đang sửa hợp đồng, kiểm tra cả **ai được xem là bằng nhau** lẫn cách tính hash. Với value class `final`, record hoặc quy tắc so kiểu rõ ràng, thiết kế thường dễ kiểm chứng hơn. Đừng chỉ thêm một kiểm thử hash mà bỏ qua tiền đề equality vốn đã sai.

### Số thực, `BigDecimal` và chuẩn hóa

- `BigDecimal("1.0")` và `BigDecimal("1.00")` khác nhau theo `equals()` vì scale khác nhau; `compareTo() == 0` **không đồng nghĩa** `equals() == true`. Viết equality dựa vào `compareTo` nhưng để hash theo `BigDecimal.hashCode()` có thể vi phạm hợp đồng. Nếu domain coi các cách biểu diễn tương đương, cần xác định cách canonicalize cho cả equality lẫn hashing.
- Với `double`/`float`, `NaN`, `+0.0` và `-0.0` có những quy tắc so sánh cần được định nghĩa nhất quán; nên dùng semantics của `Double.equals()/Double.hashCode()` hoặc một quy tắc domain thống nhất, tránh dùng so sánh `==` tùy tiện rồi áp dụng hash khác.
- Với text Unicode, chuẩn hóa, khoảng trắng và phân biệt hoa/thường là **quy tắc nghiệp vụ**. Chỉ thay riêng một bên sẽ dễ tạo cặp equality đúng nhưng hash sai.

## 7. Phân biệt kiểm thử hợp đồng với kiểm thử collision

Đây là hai khía cạnh riêng:

- **Tính đúng:** nếu `equals()` trả `true` thì `hashCode()` phải bằng nhau, không có ngoại lệ.
- **Chất lượng phân phối:** các đối tượng khác nhau lý tưởng phân bố đều để giảm collision, nhưng **không phải yêu cầu logic** của `Object`.

Không nên dùng `assertNotEquals(a.hashCode(), b.hashCode())` để kiểm tra hai object `a` và `b` khác nhau. Bài test đó sẽ có thể thất bại với implementation hoàn toàn hợp lệ. Chẳng hạn `"Aa"` và `"BB"` đã có cùng hash.

Với hệ thống lưu trữ và phân vùng dữ liệu, cũng không nên giả định `Object.hashCode()` là mã định danh ổn định xuyên process/JVM/phiên bản ứng dụng hoặc là hàm băm bảo mật. `hashCode()` phục vụ hợp đồng của đối tượng và collection trong quá trình thực thi, không thay thế canonical ID, checksum hay cryptographic hash.

## 8. Bộ tiêu chí QA khi review `equals()`/`hashCode()`

1. **Đối tượng nào được xem là bằng nhau?** Ghi rõ những field nào tạo thành identity/value equality và quy tắc `null`, chuẩn hóa, kiểu lớp.
2. **Có instance tương đương độc lập?** Tạo `a`, `b` cùng giá trị từ constructor/factory khác nhau; kiểm `a != b` và `a.equals(b)` trước.
3. **Hash của mọi cặp bằng nhau có bằng nhau?** Chạy test ở cả chiều và qua nhiều bộ dữ liệu, gồm giá trị biên, chuỗi Unicode nếu domain hỗ trợ, và trường tùy chọn `null`.
4. **Phần khác nhau có được kiểm đúng?** Khác một field tham gia equality phải khiến `equals()` phản ánh yêu cầu; tuyệt đối không đòi hash phải khác.
5. **Hash có ổn định khi dữ liệu logic không đổi?** Hai lần gọi liên tiếp trên object ổn định cho cùng `int`. Các field dùng làm key tránh mutation.
6. **Tương tác hash collection có đúng?** Sau `set.add(a)`, `set.contains(b)` phải đúng nếu `a.equals(b)`, trong điều kiện keys không bị sửa và collection được dùng đúng hợp đồng.
7. **Có alias đến dữ liệu mutable không?** Array/list/child mutable phải được copy hoặc khóa quyền sửa nếu hash phụ thuộc nội dung.
8. **Các tính chất khác của `equals()`?** Kiểm tra phản xạ, đối xứng, bắc cầu, nhất quán và `equals(null) == false`; hợp đồng hash không thể cứu một equality bị thiết kế sai.

## 9. Bài tập tự kiểm tra

**Bài 1.** Hai object trả `equals() == false` nhưng có cùng `hashCode()`. Có vi phạm hợp đồng không?  
**Đáp án:** Không. Collision hợp lệ; điều kiện chỉ có chiều “equal → same hash”.

**Bài 2.** Một test dùng `if (a.equals(b))` để so hash, nhưng `equals()` bị lỗi và luôn trả `false`. Test đã chứng minh hợp đồng thực tế đúng chưa?  
**Đáp án:** Chưa. Nó chỉ kiểm tra mệnh đề kéo theo theo cách rỗng, chưa xác nhận quan hệ bình đẳng dự kiến. Cần tạo cặp phải bằng nhau và `assertEquals(a,b)` trước.

**Bài 3.** `equals()` dùng `id` còn `hashCode()` dùng cả `id` và `name`. Trường hợp nào sẽ bắt lỗi?  
**Đáp án:** Hai instance có cùng `id` nhưng tên khác nhau. Tiền đề `equals() == true` xảy ra, trong khi hash có thể khác.

**Bài 4.** Vì sao key mutable làm `HashSet.contains()` thất bại dù implementation `equals()` và `hashCode()` nhất quán?  
**Đáp án:** Bảng băm tổ chức mục theo hash tại lúc insert; nếu field định danh đổi sau đó, hash mới có thể dẫn đến vị trí tìm kiếm khác. Cần dùng key bất biến hoặc quản lý remove/update/reinsert an toàn.

**Bài 5.** `String` `"Aa"` và `"BB"` có cùng hash. Có cần sửa Java String không?  
**Đáp án:** Không. Chúng không bằng nhau theo `equals()` nên hash bằng nhau là collision hợp lệ.

**Kết luận thực hành:** Kiểm thử mạnh nhất cho điều kiện này là **tạo nhiều cặp object khác identity nhưng bằng nhau theo domain, xác minh tiền đề `equals() == true`, rồi bắt buộc `hashCode()` bằng nhau**. Kết hợp kiểm thử bảng băm và kiểm soát mutation để phát hiện cả lỗi hợp đồng lẫn hậu quả thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-verification-005">Không viết kiểm thử đòi mọi đối tượng khác nhau phải có hash code khác</a>

<details>
<summary>Xem chi tiết</summary>

# Không viết kiểm thử đòi mọi đối tượng khác nhau phải có hash code khác

**Nguyên tắc:** Trong Java, **hai đối tượng bằng nhau theo `equals()` bắt buộc có cùng `hashCode()`; hai đối tượng không bằng nhau hoàn toàn được phép có cùng `hashCode()`**. Vì thế, kiểm thử kiểu `assertNotEquals(a.hashCode(), b.hashCode())` cho mọi cặp `a`, `b` khác nhau là kiểm thử sai hợp đồng. Một hàm hash đúng vẫn có thể tạo ra **hash collision** (va chạm mã băm).

Nội dung này nằm trong **kiểm chứng hợp đồng đối tượng**: mục đích là xác định tính đúng đắn của `equals()`/`hashCode()` và hành vi của các tập hợp dựa trên hash, thay vì áp đặt yêu cầu duy nhất không có trong đặc tả Java.

## 1. Phân biệt đối tượng khác nhau, không bằng nhau và hash khác nhau

Có ba phép so sánh cần phân biệt:

| Biểu thức | Câu hỏi được trả lời | Có bắt buộc kéo theo hash khác nhau? |
| --- | --- | --- |
| `a != b` | Hai biến có trỏ tới **hai đối tượng khác nhau** không? | **Không.** Hai instance riêng vẫn có thể bằng nhau về giá trị hoặc trùng hash. |
| `!a.equals(b)` | Hai đối tượng có **không bằng nhau theo hợp đồng `equals`** không? | **Không.** Hai giá trị khác nhau được phép trùng hash. |
| `a.hashCode() != b.hashCode()` | Hai kết quả hash kiểu `int` có khác nhau không? | Nếu hai hash khác nhau thì `a` và `b` không thể bằng nhau theo một triển khai tuân thủ hợp đồng; chiều ngược lại không đúng. |

Lưu ý `a.equals(b)` chỉ nên gọi khi `a` không phải `null`; nếu cần so sánh có thể `null`, dùng `Objects.equals(a, b)`. Không gọi `hashCode()` trên `null`.

Một trường hợp quen thuộc:

```java
String a = new String("java");
String b = new String("java");
// a != b là true vì khác instance.
// a.equals(b) là true vì cùng nội dung.
// a.hashCode() == b.hashCode() là true theo hợp đồng.
```

Ngược lại, khi `!a.equals(b)` đúng, **không thể kết luận** `a.hashCode() != b.hashCode()`.

## 2. Hợp đồng `hashCode()` yêu cầu điều gì?

Theo API của `java.lang.Object`, kiểm thử nên tập trung vào những điều có tính bắt buộc:

1. **Ổn định trong một lần chạy khi trạng thái dùng cho `equals` không thay đổi:** nhiều lần gọi `hashCode()` trên cùng đối tượng phải trả cùng một số nguyên nếu không có thay đổi ở thông tin sử dụng trong phép so sánh bằng nhau. Không suy ra rằng hash phải cố định giữa hai lần chạy ứng dụng.
2. **Các đối tượng bằng nhau phải có hash bằng nhau:** nếu `a.equals(b)` là `true`, thì `a.hashCode() == b.hashCode()` phải là `true`. Đây là chiều **bắt buộc** của hợp đồng.
3. **Các đối tượng không bằng nhau có thể trùng hash:** nếu `a.equals(b)` là `false`, Java **không yêu cầu** hash phải khác nhau. Hash khác nhau thường giúp hiệu năng bảng băm, nhưng không phải điều kiện hợp lệ.

Viết bằng logic, với `a`, `b` khác `null` và các phương thức hoạt động đúng hợp đồng:

```text
a.equals(b)                         => a.hashCode() == b.hashCode()   [bắt buộc]
a.hashCode() != b.hashCode()         => !a.equals(b)                  [suy ra]
!a.equals(b)                        =/> a.hashCode() != b.hashCode()  [sai]
a.hashCode() == b.hashCode()         =/> a.equals(b)                   [sai]
```

Hợp đồng `equals()` còn yêu cầu **phản xạ, đối xứng, bắc cầu, nhất quán** và `x.equals(null)` là `false`. Khi kiểm chứng hợp đồng đối tượng, các tính chất ấy nên có những kiểm thử riêng; việc hai đối tượng khác nhau có hash khác nhau không nằm trong danh sách.

### Vì sao va chạm không thể bị cấm?

`hashCode()` trả về một `int` 32 bit, nên chỉ có tối đa **2³² giá trị hash có thể biểu diễn**. Trong khi đó, miền các đối tượng hoặc chuỗi có thể có nhiều giá trị phân biệt hơn số đó. Theo nguyên lý chuồng bồ câu, không thể ánh xạ không va chạm tất cả giá trị phân biệt của một miền lớn hơn 2³² vào 2³² kết quả.

Ngay cả trên miền nhỏ, một thuật toán hash bình thường vẫn có thể cho hai giá trị khác nhau cùng mã băm. **Collision không tự động là lỗi**. Ví dụ mã băm hằng số có thể tuân thủ nghĩa vụ `equals/hashCode` nhưng thường phân bố kém và làm giảm hiệu năng.

## 3. Phản ví dụ thật với `String.hashCode()`

Java định nghĩa thuật toán `String.hashCode()` theo các đơn vị mã UTF-16 của chuỗi với hệ số 31. Hai chuỗi:

```text
"Aa": 31 × 65 + 97 = 2112
"BB": 31 × 66 + 66 = 2112
```

Vì vậy `"Aa".equals("BB")` là `false` trong khi cả hai có `hashCode() == 2112`. Đây là va chạm hợp lệ của lớp thư viện chuẩn; không phải bug trong `String`.

**Kiểm thử sai** dưới đây sẽ thất bại mặc dù `String` hoạt động đúng:

```java
// Sai: giả định mọi chuỗi không bằng nhau đều có hash khác nhau.
if ("Aa".hashCode() == "BB".hashCode()) {
    throw new AssertionError("Hash collision bi coi sai thanh loi");
}
```

Kiểm thử đúng có thể xác nhận cả việc hai chuỗi không bằng nhau và việc collision không làm lẫn lộn hai phần tử trong tập hợp. Không được biến điều kiện “không có collision” thành tiêu chuẩn bắt buộc để chấp nhận một triển khai.

## 4. Ví dụ Java 17+ hoàn chỉnh và chạy được

Lưu mã sau thành **`HashCodeContractDemo.java`**. Chương trình cố tình định nghĩa kiểu `Key` có hash luôn là `7` để kiểm chứng rằng các đối tượng không bằng nhau vẫn hoạt động đúng trong `HashSet` và `HashMap`.

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HashCodeContractDemo {
    // Record sinh equals() theo giá trị 'id'.
    // Hash hằng số gây nhiều collision nhưng KHÔNG vi phạm hợp đồng.
    private record Key(String id) {
        @Override
        public int hashCode() {
            return 7;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        String firstString = "Aa";
        String secondString = "BB";

        check(!firstString.equals(secondString),
                "Hai chuoi phai khac nhau");
        check(firstString.hashCode() == secondString.hashCode(),
                "Hai chuoi phai co collision theo String.hashCode");

        Key a = new Key("alpha");
        Key equalCopy = new Key("alpha");
        Key b = new Key("beta");

        check(a != equalCopy, "Hai record duoc tao rieng");
        check(a.equals(equalCopy), "Hai key cung id phai bang nhau");
        check(!a.equals(b), "Hai key khac id phai khong bang nhau");

        // Kiem tra bat buoc cua hop dong.
        check(a.hashCode() == equalCopy.hashCode(),
                "Equal objects must have equal hash codes");
        check(a.hashCode() == a.hashCode(),
                "Hash code phai on dinh khi trang thai khong doi");

        // Hai key khac nhau van co the trung hash.
        check(a.hashCode() == b.hashCode(),
                "Vi du co y dung constant hash");

        Set<Key> keys = new HashSet<>();
        keys.add(a);
        keys.add(equalCopy); // Trung theo equals => khong them phan tu moi.
        keys.add(b);         // Khac theo equals => them phan tu thu hai.
        check(keys.size() == 2, "HashSet phan biet theo equals");

        Map<Key, String> map = new HashMap<>();
        map.put(a, "value-alpha");
        map.put(b, "value-beta");
        check(map.size() == 2, "HashMap giu ca hai key khac nhau");
        check("value-alpha".equals(map.get(equalCopy)),
                "Key bang nhau phai tim duoc value");
        check("value-beta".equals(map.get(b)),
                "Key khac nhau phai doc dung value");

        System.out.println("Aa va BB equals: " +
                firstString.equals(secondString));
        System.out.println("Aa hash: " + firstString.hashCode());
        System.out.println("BB hash: " + secondString.hashCode());
        System.out.println("a equals equalCopy: " + a.equals(equalCopy));
        System.out.println("a equals b: " + a.equals(b));
        System.out.println("a va b trung hash: " +
                (a.hashCode() == b.hashCode()));
        System.out.println("HashSet size: " + keys.size());
        System.out.println("HashMap[alpha]: " + map.get(equalCopy));
        System.out.println("HashMap[beta]: " + map.get(b));
    }
}
```

Biên dịch và chạy:

```bash
javac HashCodeContractDemo.java
java HashCodeContractDemo
```

**Standard output chính xác:**

```text
Aa va BB equals: false
Aa hash: 2112
BB hash: 2112
a equals equalCopy: true
a equals b: false
a va b trung hash: true
HashSet size: 2
HashMap[alpha]: value-alpha
HashMap[beta]: value-beta
```

**Phân tích:** `a` và `b` có hash cùng bằng `7` nhưng khác nhau theo `equals()`. `HashSet` lưu cả hai, còn `HashMap` trả về đúng giá trị cho từng khóa. Bản sao `equalCopy` có cùng `id` với `a` nên được nhận là khóa bằng nhau; thêm bản sao vào `HashSet` không tăng kích thước. Ví dụ này phân biệt chính xác **tính đúng đắn** với **chất lượng phân bố hash**. Mã băm hằng số dễ làm bảng băm chậm, nhưng không làm hợp đồng tự mâu thuẫn.

Chương trình có thể được chạy với Java 17 hoặc phiên bản mới hơn hỗ trợ `record`. Tất cả phép `check` được thực thi bình thường, kể cả khi JVM không bật cờ `-ea`; khi điều kiện sai sẽ ném `AssertionError`.

## 5. Sửa kiểm thử JUnit 5 theo đúng hợp đồng

Giả sử mã sản phẩm có lớp `UserId`, trong đó `equals` dùng giá trị mã người dùng và `hashCode` triển khai tương ứng. Hai phép kiểm thử dưới đây **không hợp lệ về nguyên tắc**:

```java
// Sai: "khac object" (a != b) khong bat buoc "khac hash".
assertNotEquals(a.hashCode(), b.hashCode());

// Sai: "khac gia tri" (!a.equals(b)) van co the trung hash.
assertNotEquals(x.hashCode(), y.hashCode());
```

Thay bằng việc kiểm tra **hệ quả bắt buộc** cùng với hành vi của collection:

```java
// Dung: hai doi tuong bang nhau => cung hash.
assertEquals(sameValueA, sameValueB);
assertEquals(sameValueA.hashCode(), sameValueB.hashCode());

// Dung: hai doi tuong khong bang nhau => equals phai phan biet.
assertNotEquals(differentValueA, differentValueB);

// Dung: neu su dung HashSet, phai luu duoc ca hai gia tri khac nhau.
Set<UserId> uniqueIds = new HashSet<>();
uniqueIds.add(differentValueA);
uniqueIds.add(differentValueB);
assertEquals(2, uniqueIds.size());
```

Đây là **mã minh họa trong lớp kiểm thử**: cần `UserId`, các biến được khởi tạo và JUnit Jupiter 5 cùng `java.util.Set/HashSet`. Các câu lệnh trên không phải một chương trình Java độc lập. Trong đó, `assertNotEquals(objectA, objectB)` của JUnit kiểm tra bình đẳng đối tượng, **không** đòi hỏi hash khác.

Một lớp JUnit 5 nhỏ hoàn chỉnh cho hiện tượng collision (giả sử dự án đã cấu hình thư viện JUnit Jupiter):

```java
import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HashCollisionTest {
    private record Key(String value) {
        @Override
        public int hashCode() {
            return 42;
        }
    }

    @Test
    void equalKeysMustShareHash() {
        Key first = new Key("A");
        Key equalCopy = new Key("A");

        assertNotSame(first, equalCopy);
        assertEquals(first, equalCopy);
        assertEquals(first.hashCode(), equalCopy.hashCode());
    }

    @Test
    void unequalKeysMayShareHash() {
        Key first = new Key("A");
        Key second = new Key("B");

        assertNotEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode()); // Collision chu dong.
    }

    @Test
    void hashSetMustRetainBothUnequalKeysDespiteCollision() {
        Key first = new Key("A");
        Key equalCopy = new Key("A");
        Key second = new Key("B");

        Set<Key> set = new HashSet<>();
        set.add(first);
        set.add(equalCopy);
        set.add(second);

        assertEquals(2, set.size());
        assertTrue(set.contains(first));
        assertTrue(set.contains(second));
    }
}
```

Trong ví dụ JUnit, `assertEquals(first.hashCode(), second.hashCode())` là **đặc tả riêng của fixture cố tình trả hash hằng số**, nhằm tạo tình huống collision để thử collection. **Không được sao chép assertion ấy làm hợp đồng chung cho mọi lớp**: đối tượng không bằng nhau *được phép* trùng hash, chứ không *bắt buộc* trùng hash.

## 6. Nên kiểm thử những thuộc tính nào?

| Thuộc tính | Cách kiểm chứng đúng | Điều không nên làm |
| --- | --- | --- |
| Phản xạ của `equals` | `assertTrue(a.equals(a))` | Giả sử phép so sánh với `null` hợp lệ khi gọi từ `null`. |
| Đối xứng | Với cặp phù hợp, kiểm tra `a.equals(b) == b.equals(a)` | Chỉ kiểm tra một chiều. |
| Bắc cầu | Nếu `a` bằng `b` và `b` bằng `c`, kiểm tra `a` bằng `c` | Tạo dữ liệu thử không thỏa tiền đề rồi yêu cầu bằng nhau. |
| Nhất quán | Với trạng thái không đổi, gọi lại `equals` và `hashCode` cho kết quả nhất quán | Yêu cầu hash không đổi khi những trường tham gia `equals` đã bị sửa. |
| `null` | Với `a != null`, `assertFalse(a.equals(null))` | Gọi `null.equals(a)`. |
| Equal ⇒ same hash | Tạo **hai instance bằng nhau theo giá trị**, kiểm tra `equals` và hash bằng nhau | Chỉ so cùng một tham chiếu, khiến phép thử quá yếu. |
| Unequal | Tạo giá trị thực sự không bằng nhau, kiểm tra `!equals` | Đòi hash nhất thiết khác nhau. |
| Hành vi hash collection | Thêm hai key không bằng nhau, kiểm tra chúng đều tồn tại; thêm key bằng nhau, kiểm tra không nhân bản | Dùng hash làm tiêu chuẩn duy nhất xác định hai key trùng nhau. |

Khi lớp có những trường **có thể thay đổi** tham gia `equals/hashCode`, tránh sửa khóa sau khi đã đưa vào `HashMap/HashSet`. Việc thay đổi ấy có thể khiến phép tìm kiếm thất bại vì hash hoặc quan hệ bằng nhau của khóa đã thay đổi. Đây là lỗi về cách sử dụng khóa có thể biến đổi, **khác với collision hợp lệ**.

### Nếu viết kiểm thử dựa trên nhiều dữ liệu hoặc property-based testing

Hãy sinh nhiều cặp đối tượng và dùng quy tắc sau:

```java
// Gia ma trong vong lap, a va b khong null:
if (a.equals(b)) {
    if (a.hashCode() != b.hashCode()) {
        throw new AssertionError("Vi pham hop dong equals/hashCode");
    }
}
// Khong co assertion chung nao ve hash neu !a.equals(b).
```

Để phép thử đủ mạnh, nên có các cặp **bằng nhau nhưng khác instance**, các cặp **khác nhau**, và các trường hợp **collision có chủ đích** nếu đối tượng được dùng làm key trong hash collection. Không thể kết luận triển khai chính xác chỉ từ vài ví dụ, nhưng những nhóm dữ liệu ấy giúp phát hiện lỗi hợp đồng thường gặp.

Nếu mục tiêu là đánh giá **hiệu năng hoặc chất lượng hàm băm**, hãy lập kiểm thử khác với dữ liệu đại diện cho ứng dụng, đo tần suất collision, phân bố trong bucket hoặc thời gian thao tác. Đó là tiêu chí hiệu năng có điều kiện, không phải hợp đồng cấm mọi collision. Không biến mục tiêu “phân bố đủ tốt” thành assertion “không bao giờ trùng hash”.

## 7. Các hiểu lầm thường gặp

**“Hai đối tượng khác nhau phải có hash khác nhau vì `hashCode` định danh đối tượng.”** Không đúng. `hashCode()` là mã băm kiểu `int`, không phải định danh duy nhất. Ngay cả `System.identityHashCode(obj)` cũng không hứa trả giá trị duy nhất cho mọi đối tượng.

**“`HashMap` chỉ dùng `hashCode()` để phân biệt key.”** Không đúng. Hash giúp tìm vị trí ứng viên, sau đó collection dùng so sánh phù hợp (trong đó có `equals()`) để nhận diện khóa bằng nhau. Khi hai key không bằng nhau có cùng hash, `HashMap` vẫn có thể lưu cả hai. Cách xử lý nội bộ, như cấu trúc bucket, là chi tiết triển khai không cần thiết để chứng minh hợp đồng.

**“Nếu hash bằng nhau thì `equals()` phải trả true.”** Không đúng. Hash bằng chỉ báo rằng có khả năng cùng giá trị hoặc có collision; phải dùng `equals()` để phân biệt.

**“Một kiểm thử tạo 1.000 đối tượng khác nhau và yêu cầu 1.000 hash khác nhau sẽ phát hiện bug.”** Kiểm thử ấy có thể thất bại trên một triển khai đúng hợp đồng. Nếu phép đo phân bố nằm trong yêu cầu hiệu năng riêng, cần tiêu chí định lượng và dữ liệu phù hợp thay vì quy tắc tuyệt đối.

**“Hai `new Object()` sẽ có hash khác nhau.”** Java không cam kết điều đó, dù nhiều JVM thường tạo các giá trị khác nhau trong ví dụ nhỏ. Không dùng tính quan sát thường gặp làm hợp đồng kiểm thử.

**“Hash giống nhau nghĩa là dữ liệu hai key đã mất.”** Không đúng: collection được thiết kế để xử lý collision. Khi có lỗi mất/truy xuất nhầm key, cần xem lại `equals/hashCode`, trạng thái có thể biến đổi và cách truy cập, thay vì chỉ nhìn việc collision xảy ra.

## 8. Tình huống tự kiểm tra

**Tình huống 1.** `a.equals(b) == true` nhưng `a.hashCode() == 3` và `b.hashCode() == 5`. Có vi phạm không?

**Có.** Hai đối tượng bằng nhau phải có cùng hash. Đây là lỗi hợp đồng.

**Tình huống 2.** `a.equals(b) == false` nhưng `a.hashCode() == b.hashCode() == 7`. Có vi phạm không?

**Không.** Đây là collision hợp lệ. Không viết kiểm thử để buộc hai hash phải khác nhau.

**Tình huống 3.** `a != b` nhưng `a.equals(b) == true`. Nên kiểm tra hash thế nào?

**Kiểm tra bằng nhau.** Hai đối tượng khác tham chiếu có thể bằng nhau theo giá trị; khi bằng nhau thì hash buộc giống nhau.

**Tình huống 4.** Hai chuỗi `"Aa"` và `"BB"` có hash như nhau; đưa vào `HashSet<String>` thì kích thước phải là bao nhiêu?

**Là 2.** Hai chuỗi không bằng nhau theo `String.equals()`, nên collision không hợp nhất chúng thành một phần tử.

**Tình huống 5.** Một lớp luôn trả `hashCode() == 1`, còn `equals()` thực hiện đúng quy tắc bằng nhau. Có được phép không?

**Có về mặt hợp đồng**, vì mọi cặp bằng nhau đều có hash như nhau. Tuy nhiên hash quá kém phân biệt có thể làm giảm hiệu năng các collection dựa trên hash.

## 9. Ghi nhớ và tài liệu chuẩn

Quy tắc ngắn nhất để thiết kế kiểm thử:

- **`equals == true` ⇒ bắt buộc `hashCode` bằng nhau.**
- **`equals == false` ⇒ `hashCode` có thể bằng hoặc khác.**
- **`hashCode` khác nhau ⇒ đối tượng không thể `equals` theo một triển khai tuân thủ hợp đồng.**
- **Collision là hợp lệ; phát hiện lỗi bằng hợp đồng và hành vi truy xuất, không bằng yêu cầu hash duy nhất.**

Tham khảo đặc tả API Java 17:

- [`Object.hashCode()` và `Object.equals()` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html)
- [`String.hashCode()` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#hashCode())
- [`HashMap` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html)
- [`HashSet` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-verification-006">Kiểm thử bộ ba cho compareTo() và Comparator</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử bộ ba cho `compareTo()` và `Comparator`

## Mục tiêu và phạm vi

Một phép so sánh có vẻ đúng khi thử riêng hai giá trị vẫn có thể **sai khi xét ba giá trị**. Bài này hướng dẫn kiểm chứng hợp đồng sắp thứ tự của `Comparable.compareTo()` và `Comparator.compare()` trong Java: đối dấu khi đảo hai toán hạng, tính bắc cầu, và tính nhất quán của những giá trị được coi là bằng nhau theo thứ tự. Trọng tâm là cách tạo và kiểm thử các **bộ ba `(a, b, c)`**, tìm phản ví dụ, rồi sửa bộ so sánh để các thao tác sort, `TreeSet` và `TreeMap` hoạt động có thể dự đoán.

Các kiểm thử ở đây kiểm chứng **hợp đồng thứ tự**, không thay thế kiểm thử `equals()`/`hashCode()`; tuy vậy phải hiểu quan hệ giữa `compare(x, y) == 0` và `equals()` khi dùng tập hoặc map có sắp xếp.

## 1. Hiểu đúng kết quả so sánh

Với hai đối tượng `x`, `y`:

- `x.compareTo(y) < 0` hoặc `comparator.compare(x, y) < 0`: `x` đứng trước `y`.
- Kết quả bằng `0`: `x` và `y` **tương đương theo thứ tự** đang xét.
- Kết quả `> 0`: `x` đứng sau `y`.

Hợp đồng **chỉ quy định dấu**, không bắt trả về chính xác `-1`, `0` hay `1`. Vì vậy các phép thử nên chuẩn hóa với `Integer.signum(result)`, tránh viết `compare(x, y) == -1` hoặc lấy số đối của kết quả thô (có thể tràn số khi kết quả là `Integer.MIN_VALUE`).

`Comparable<T>` định nghĩa **thứ tự tự nhiên** của một kiểu thông qua `compareTo(T other)`. `Comparator<T>` cho phép định nghĩa thứ tự bên ngoài đối tượng, như sắp `Person` theo tuổi, rồi theo mã. Hai cơ chế có cùng các quy tắc kiểm chứng quan trọng dưới đây. Một `Comparator` có thể chấp nhận `null` nếu chủ động định nghĩa cách xử lý; `compareTo(null)` của đối tượng khác `null` phải ném `NullPointerException` theo hợp đồng `Comparable`.

Ký hiệu dùng trong bài: `c(x,y)` là kết quả `x.compareTo(y)` hoặc `comparator.compare(x,y)`; `sgn(v)` là dấu của số nguyên `v`.

## 2. Ba nhóm điều kiện phải kiểm chứng

### 2.1. Đảo toán hạng: cặp `(a, b)`

Với mọi phần tử thuộc miền so sánh hợp lệ:

```text
sgn(c(a,b)) == -sgn(c(b,a))
c(a,a) == 0
```

Nếu `a < b` thì chiều ngược lại phải là `b > a`. Nếu `a` và `b` được xem là cùng vị trí (`c(a,b)==0`) thì chiều ngược lại cũng bằng `0`. Kiểm tra `c(a,a)==0` là phép thử đơn giản, bổ trợ cho tính đối dấu.

**Lỗi dễ gặp:** dùng `return a - b;` với số nguyên; phép trừ có thể tràn và làm hai chiều đều âm. Hãy dùng `Integer.compare(a,b)`, `Long.compare(a,b)` hoặc `Comparator.comparingInt(...)` tùy ngữ cảnh.

### 2.2. Bắc cầu: bộ ba `(a, b, c)`

Nếu `a > b` và `b > c`, bắt buộc `a > c`; tương tự với chiều nhỏ hơn:

```text
c(a,b) > 0  &&  c(b,c) > 0   =>   c(a,c) > 0
c(a,b) < 0  &&  c(b,c) < 0   =>   c(a,c) < 0
```

Đây là lý do cần **kiểm thử bộ ba**: kiểm tra mỗi cặp đơn lẻ không phát hiện được vòng mâu thuẫn.

Ví dụ bộ so sánh tùy biến mô tả vòng `0 < 1`, `1 < 2`, nhưng `2 < 0`. Mỗi cặp đều có một chiều trước/sau rõ ràng và đối dấu đúng. Tuy nhiên bộ ba `(0,1,2)` chứng minh sai: từ `0 < 1` và `1 < 2` phải suy ra `0 < 2`, trái với kết quả `0 > 2`. Một bộ so sánh như vậy không tạo ra thứ tự nhất quán.

### 2.3. Bằng 0 phải nhất quán với phần tử thứ ba

Nếu `a` và `b` được xem là tương đương theo thứ tự, thì đối với **mọi `c`** chúng phải có cùng quan hệ đứng trước/đứng sau/đồng hạng:

```text
c(a,b) == 0  =>  sgn(c(a,c)) == sgn(c(b,c))
```

Kết hợp với quy tắc đảo toán hạng, quan hệ này cũng đúng khi phần tử thứ ba đứng ở phía bên trái. Nó bảo đảm các nhóm đồng hạng (`compare == 0`) hành xử như các lớp tương đương hợp lệ; chẳng hạn nếu `a ~ b` và `b ~ c`, thì `a ~ c`.

Phản ví dụ: bộ so sánh coi `0` và `2` là bằng nhau, nhưng những cặp còn lại so theo thứ tự số nguyên. Khi xét `(a,b,c)=(0,2,1)`, ta có `c(0,2)==0`, nhưng `c(0,1)<0` trong khi `c(2,1)>0`. Nhóm đồng hạng này không nhất quán. Kiểm thử chỉ tính bắc cầu ở các cặp **so sánh khác 0** là chưa đủ để giải thích toàn bộ hợp đồng.

## 3. Viết bộ kiểm chứng độc lập bằng Java 17+

Ví dụ hoàn chỉnh dưới đây duyệt toàn bộ cặp và bộ ba trên một tập mẫu nhỏ. Nó dùng **cùng một hàm kiểm chứng** cho cả `compareTo()` (truyền method reference) lẫn `Comparator`. Bốn tình huống thể hiện hai thứ tự đúng và hai thứ tự cố ý sai.

```java
// OrderingTriplesDemo.java
import java.util.Comparator;
import java.util.List;

public final class OrderingTriplesDemo {
    private static int sign(int value) {
        return Integer.signum(value);
    }

    private static <T> void checkContract(
            List<T> samples, Comparator<? super T> comparator) {

        // Kiểm tra phản xạ và tính đối dấu trên mọi cặp.
        for (T a : samples) {
            if (sign(comparator.compare(a, a)) != 0) {
                throw new AssertionError("reflexivity");
            }
            for (T b : samples) {
                int ab = sign(comparator.compare(a, b));
                int ba = sign(comparator.compare(b, a));
                if (ab != -ba) {
                    throw new AssertionError("opposite signs");
                }
            }
        }

        // Kiểm tra mọi bộ ba có thứ tự (a, b, c).
        for (T a : samples) {
            for (T b : samples) {
                for (T c : samples) {
                    int ab = sign(comparator.compare(a, b));
                    int bc = sign(comparator.compare(b, c));
                    int ac = sign(comparator.compare(a, c));

                    if (ab == 0 &&
                            ac != sign(comparator.compare(b, c))) {
                        throw new AssertionError("zero-class consistency");
                    }
                    if ((ab > 0 && bc > 0 && ac <= 0) ||
                            (ab < 0 && bc < 0 && ac >= 0)) {
                        throw new AssertionError("transitivity");
                    }
                }
            }
        }
    }

    private static void run(
            String label, List<Integer> samples,
            Comparator<Integer> comparator) {
        try {
            checkContract(samples, comparator);
            System.out.println(label + ": PASS");
        } catch (AssertionError error) {
            System.out.println(label + ": FAIL - " + error.getMessage());
        }
    }

    public static void main(String[] args) {
        Comparator<Integer> byAbsoluteValue =
                Comparator.comparingLong(x -> Math.abs((long) x));

        // Phản ví dụ 1: 0 < 1 < 2 < 0, vi phạm bắc cầu.
        Comparator<Integer> cyclic = (a, b) -> {
            if (a.equals(b)) {
                return 0;
            }
            return (a + 1) % 3 == b ? -1 : 1;
        };

        // Phản ví dụ 2: coi 0 ~ 2 nhưng so cặp khác theo số học.
        Comparator<Integer> merged = (a, b) -> {
            if ((a == 0 && b == 2) || (a == 2 && b == 0)) {
                return 0;
            }
            return Integer.compare(a, b);
        };

        run("Integer.compareTo", List.of(-2, -1, 0, 1, 2),
                Integer::compareTo);
        run("Comparator abs", List.of(-2, -1, 0, 1, 2),
                byAbsoluteValue);
        run("Comparator cycle", List.of(0, 1, 2), cyclic);
        run("Comparator merged", List.of(0, 2, 1), merged);
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 OrderingTriplesDemo.java
java OrderingTriplesDemo
```

Đầu ra chính xác:

```text
Integer.compareTo: PASS
Comparator abs: PASS
Comparator cycle: FAIL - transitivity
Comparator merged: FAIL - zero-class consistency
```

**Cách đọc kết quả:**

- `Integer::compareTo` dùng thứ tự tự nhiên, nên vượt qua các mẫu kiểm thử.
- So sánh theo trị tuyệt đối tạo ra nhóm `-2 ~ 2`, `-1 ~ 1`. Đây vẫn là thứ tự hợp lệ: các phần tử bằng nhau theo trị tuyệt đối có quan hệ giống nhau với mọi phần tử thứ ba. Nhóm đồng hạng **không buộc** các đối tượng phải `equals()`.
- `cyclic` giữ tính đối dấu cho từng cặp nhưng tạo chu trình so sánh.
- `merged` coi `0` và `2` đồng hạng không đúng cách; chọn thứ tự mẫu `[0,2,1]` để bộ kiểm tra đưa ra phản ví dụ `zero-class consistency` trước các dạng vi phạm khác.

Các comparator `cyclic` và `merged` **chỉ dùng để minh họa lỗi**, không sử dụng để sắp xếp dữ liệu thực. Hàm `checkContract` kiểm tra hữu hạn các mẫu, nên `PASS` không chứng minh đúng với mọi giá trị có thể có; cần cả lập luận về logic sắp thứ tự và tập test đại diện.

## 4. Phân biệt tính nhất quán với `equals()`

Hợp đồng trên không yêu cầu `compare(a,b)==0` luôn đồng nghĩa `a.equals(b)`: đây là khuyến nghị rất mạnh của `Comparable` và điều cần xét cho `Comparator`, **không phải yêu cầu bắt buộc trong mọi thiết kế thứ tự**. Một bộ so sánh theo độ dài chuỗi, theo tuổi hoặc trị tuyệt đối có thể hợp lệ mà vẫn nhóm nhiều đối tượng `equals()` khác nhau vào một vị trí.

Tuy nhiên `TreeSet` và `TreeMap` dùng **kết quả comparator hoặc `compareTo` để quyết định tính trùng khóa/phần tử**. Vì thế khi comparator trả về 0 cho hai đối tượng khác nhau, collection có thể giữ một phần tử hay một entry cho cả hai khóa. Đây không phải lỗi của collection mà là hệ quả của thứ tự đã chọn.

Ví dụ Java 17+:

```java
// TreeSetOrderingDemo.java
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

public class TreeSetOrderingDemo {
    record Member(int age, String code) {}

    public static void main(String[] args) {
        Member a = new Member(30, "A");
        Member b = new Member(30, "B");

        Comparator<Member> byAge =
                Comparator.comparingInt(Member::age);
        Set<Member> onlyAge = new TreeSet<>(byAge);
        onlyAge.add(a);
        onlyAge.add(b);

        Comparator<Member> byAgeThenCode = byAge
                .thenComparing(Member::code);
        Set<Member> ageAndCode = new TreeSet<>(byAgeThenCode);
        ageAndCode.add(a);
        ageAndCode.add(b);

        System.out.println(a.equals(b));
        System.out.println(onlyAge.size());
        System.out.println(ageAndCode.size());
    }
}
```

```bash
javac TreeSetOrderingDemo.java
java TreeSetOrderingDemo
```

```text
false
1
2
```

`record Member` có `equals()` xét cả tuổi và mã. Comparator chỉ theo tuổi coi hai phần tử đồng hạng; comparator có `thenComparing(Member::code)` dùng thêm tiêu chí phân biệt. Phải chọn khóa và tiêu chí phụ theo yêu cầu nghiệp vụ: đôi khi cố ý gộp nhiều phần tử là đúng, nhưng phải biết rõ tác động lên cấu trúc có sắp xếp.

## 5. Cách chọn bộ ba để kiểm thử có giá trị

Một tập dữ liệu thử nên gồm **nhiều lớp tương đương, điểm biên và những giá trị dễ tạo mâu thuẫn**. Chọn không chỉ các chuỗi tăng đều `(1,2,3)` mà cả bộ ba có một hoặc nhiều cặp `compare==0`.

| Nhóm mẫu | Ví dụ | Vấn đề cần bắt |
| --- | --- | --- |
| Ba phần tử khác nhau | `(a,b,c)` với `a<b<c` và mọi hoán vị | Bắc cầu ở cả hai hướng, vòng so sánh |
| Hai phần tử đồng hạng | `compare(a,b)==0` nhưng `a` và `b` là hai đối tượng khác nhau | Quan hệ với `c` phải giống nhau |
| Ba phần tử đồng hạng | `a~b`, `b~c` | Tính bắc cầu của lớp đồng hạng |
| Biên số nguyên | `Integer.MIN_VALUE`, `-1`, `0`, `Integer.MAX_VALUE` | Tràn số khi trừ, đổi dấu `MIN_VALUE` |
| Nhiều khóa sắp xếp | Cùng điểm, khác tuổi/mã; hoặc cùng khóa chính và khóa phụ | `thenComparing`, xử lý tie |
| Chuỗi, chữ hoa/thường | `"a"`, `"A"`, chuỗi rỗng, chữ có dấu | Chuẩn hóa, locale, phép so sánh có tính bắc cầu |
| `null` (nếu comparator hỗ trợ) | `(null,a,b)` | Chính sách `nullsFirst`/`nullsLast` nhất quán |
| Số thực | `-0.0`, `+0.0`, `NaN`, vô cực | Ngữ nghĩa của `Double.compare` khác các phép toán `<`/`>` thông thường |

Khi kiểm thử `Comparable`, chỉ tạo bộ ba gồm các đối tượng thực sự có thể so sánh theo hợp đồng kiểu dữ liệu. Khi kiểm thử một comparator có thể ném exception theo thiết kế với một số input, tách rõ nhóm input hợp lệ và trường hợp phải ném lỗi. Không “sửa” phép kiểm bằng cách nuốt mọi exception rồi đánh dấu PASS.

### Quy trình thực hành

1. **Xác định miền hợp lệ và chính sách tie:** trường nào được so sánh, có cho `null` không, comparator có được nhóm đối tượng khác `equals()` không.
2. **Kiểm tra từng cặp:** `c(a,a)==0`; hai chiều có dấu đối nhau. Dùng `signum`, không so khớp giá trị trả về cụ thể.
3. **Kiểm tra mọi bộ ba có thứ tự:** cả hai hướng bắc cầu và trường hợp `c(a,b)==0`. Không chỉ kiểm tra một cách sắp `(a,b,c)`: đổi vị trí có thể làm lộ lỗi mới.
4. **Giữ lại bộ ba phản ví dụ tối giản:** ghi ba giá trị đầu vào, dấu `c(a,b)`, `c(b,c)`, `c(a,c)` và lý do sai. Điều này giúp sửa điều kiện so sánh chính xác.
5. **Bổ sung phép thử dài hạn:** khi thêm một trường vào `compareTo`, đổi comparator, hoặc thay đổi chính sách `null`, chạy lại test với tập mẫu biên và các trường hợp từng gây lỗi.

Với `n` phần tử mẫu, kiểm tra mọi cặp cần khoảng `O(n²)` phép thử, mọi bộ ba cần `O(n³)` lần duyệt. Thích hợp để chạy đầy đủ trên **tập mẫu nhỏ có chọn lọc**. Với miền lớn, có thể phát sinh nhiều bộ ba ngẫu nhiên bằng seed cố định và sử dụng property-based testing; khi có lỗi, thu nhỏ (shrink) về bộ ba dễ hiểu. Phương pháp ngẫu nhiên là bổ sung, không thay thế các mẫu biên hay lập luận về tính đúng đắn.

## 6. Những cách viết comparator dễ phá hợp đồng

### Dùng phép trừ thay `Integer.compare`

```java
// Sai khi tràn int:
Comparator<Integer> unsafe = (a, b) -> a - b;

// An toàn với toàn miền int:
Comparator<Integer> safe = Integer::compare;
```

Với `a = Integer.MAX_VALUE`, `b = -1`, cả `a-b` và `b-a` đều có thể nhận `Integer.MIN_VALUE` vì tràn số; hai chiều cùng âm, vi phạm đối dấu. Đừng dùng `Math.abs(a-b)` hay lấy `-compare(b,a)` trực tiếp để xác định dấu kỳ vọng.

### Lắp ghép quy tắc ưu tiên tạo vòng

Các câu điều kiện kiểu “A ưu tiên B, B ưu tiên C, C ưu tiên A” có thể thỏa mãn mọi kiểm thử cặp và vẫn phá bắc cầu. Nếu thứ tự nghiệp vụ thực sự tuần hoàn, nó **không thích hợp làm Comparator** cho một thứ tự tổng quát. Cần mô hình hóa luật xếp lịch/ưu tiên dưới dạng quan hệ riêng, hoặc chọn một khóa có thứ tự toàn phần để sort.

### Trả về 0 tùy tiện cho “không biết so thế nào”

`0` phải biểu thị cùng hạng theo quan hệ thứ tự nhất quán, **không có nghĩa là bỏ qua phép so sánh**. Nếu chỉ đôi lúc trả 0 cho hai giá trị để “tránh xử lý”, bộ ba với phần tử thứ ba có thể mâu thuẫn. Hãy định nghĩa tiêu chí phụ hợp lệ hoặc chính sách cho input không thể so sánh.

### Phụ thuộc trạng thái thay đổi theo thời gian

Nếu comparator đọc `System.currentTimeMillis()`, `Random`, dữ liệu có thể thay đổi, hoặc trường mutable bị chỉnh trong lúc phần tử đang nằm trong `TreeSet`/`TreeMap`, hai lần so sánh cùng cặp có thể cho kết quả khác nhau. Các phép kiểm bộ ba giả định **cùng trạng thái so sánh ổn định**. Nên sắp theo khóa bất biến, tạo snapshot, hoặc xây lại collection khi khóa ảnh hưởng thứ tự đã thay đổi.

### Thiếu tiêu chí phụ hoặc xử lý kiểu đặc biệt

Với sắp xếp nhiều khóa, dùng `Comparator.comparing(...).thenComparing(...)` để tạo thứ tự từ các phép so sánh hợp lệ. Với số thực, dùng `Double.compare` hoặc `comparingDouble` và kiểm thử rõ `NaN`/`±0.0`; không tự suy diễn chỉ từ các biểu thức boolean `a < b`, `a > b`. Với chuỗi quốc tế, nếu dùng `Collator` thì thống nhất locale, quy tắc chuẩn hóa và cấu hình trong suốt quá trình so sánh.

## 7. Đưa bộ kiểm chứng vào unit test

Hàm `checkContract` ở ví dụ có thể đưa vào tiện ích test và gọi trong test case bằng một tập mẫu cố định. Khi dùng JUnit, chỉ cần để `AssertionError` lan ra; test runner sẽ báo FAIL. Nên có **ít nhất một bộ ba đã biết sẽ thất bại** cho mỗi loại comparator lỗi được sửa, nhằm tránh chỉ kiểm thử tình huống dễ.

Có thể kiểm tra thêm các thuộc tính ở mức dữ liệu thực:

- Kết quả so sánh lặp lại cùng cặp không đổi khi khóa chưa đổi.
- Sau khi sort, mọi cặp liên tiếp không giảm theo comparator; đồng thời kiểm tra bộ ba vì thứ tự đầu ra “trông đã sort” không chứng minh comparator hợp lệ.
- Nếu ứng dụng yêu cầu `compare(a,b)==0` tương đương `a.equals(b)`, kiểm tra **điều kiện nghiệp vụ bổ sung này** riêng biệt. Một comparator đúng hợp đồng thứ tự vẫn có thể cố ý không nhất quán với `equals()`.

Khi một bộ ba thất bại, sửa chính thuật toán so sánh: quy tắc so khóa, tie-breaker, xử lý `null`, tràn số, hoặc nguồn dữ liệu mutable. Thay đổi thứ tự duyệt mẫu để “test xanh” không giải quyết lỗi.

## 8. Checklist tự kiểm tra

- Nếu `compare(a,b)=42` thì có hợp lệ không? **Có**, miễn là dấu và tất cả điều kiện hợp đồng đúng; không cần là `1`.
- Nếu `a<b`, `b<c` nhưng `a>c`, vi phạm gì? **Bắc cầu**, cần bộ ba để phát hiện.
- Nếu `compare(a,b)==0`, có bắt buộc `a.equals(b)` không? **Không trong mọi trường hợp**, nhưng quan hệ so sánh của cả hai với `c` phải cùng dấu; `TreeSet`/`TreeMap` xem chúng là cùng khóa theo thứ tự.
- Tại sao dùng `Integer.signum`? Để kiểm chứng **dấu**, tránh giả định giá trị `±1` và tránh tràn khi đổi dấu số nguyên thô.
- Vì sao `PASS` với 5 phần tử chưa đủ kết luận comparator luôn đúng? Tất cả bộ ba chỉ được kiểm tra trong **tập 5 mẫu**; lỗi có thể xuất hiện tại giá trị biên hoặc lớp dữ liệu chưa xét.

**Ghi nhớ:** để tin cậy một `compareTo()` hay `Comparator`, hãy kiểm tra cả **cặp đảo chiều** và **mọi bộ ba đại diện**, đặc biệt những bộ ba chứa hai phần tử `compare == 0`. Kết quả sắp xếp nhìn hợp lý trên vài ví dụ là chưa đủ để chứng minh hợp đồng thứ tự.

## Tham khảo

- [Java SE 17 — Comparable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Comparable.html)
- [Java SE 17 — Comparator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html)
- [Java SE 17 — TreeSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html)
- [Java SE 17 — TreeMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-verification-007">Kiểm thử tra cứu bằng đối tượng bằng nhau trong HashMap và HashSet</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử tra cứu bằng đối tượng bằng nhau trong HashMap và HashSet

## 1. Mục tiêu học tập

Giả sử một hệ thống lưu trạng thái tài khoản trong HashMap theo khóa gồm mã tổ chức và mã người dùng. Khi đã lưu với đối tượng **new UserKey("team-a", 42)**, hệ thống phải tra cứu được bằng **một đối tượng UserKey khác có đúng các giá trị đó**. Tương tự, HashSet phải nhận ra hai đối tượng khác tham chiếu nhưng bằng nhau về mặt khóa logic là cùng một phần tử.

Bài kiểm thử không dừng ở **map.get()** hoặc **set.contains()** đơn lẻ; cần chứng minh mối quan hệ giữa **identity**, **equals()**, **hashCode()**, hành vi tra cứu/cập nhật và tính duy nhất, đồng thời xác nhận các khóa khác nhau thực sự không bị nhận nhầm.

Các khái niệm cần phân biệt:

- **a == b:** hai biến tham chiếu cùng một object.
- **a.equals(b):** hai object được xem là bằng nhau theo hợp đồng equality do lớp định nghĩa.
- **a.hashCode():** số nguyên dùng phục vụ phân vùng hash khi tổ chức dữ liệu. Hai đối tượng bằng nhau theo equals() bắt buộc trả cùng hashCode().
- **Khóa logic:** danh tính nghiệp vụ do các field tham gia equals/hashCode xác định. Hai object riêng biệt có thể biểu diễn cùng khóa logic.

Một chương trình chỉ thêm một đối tượng a rồi tra cứu lại cũng bằng **chính a** là kiểm thử yếu: lớp kế thừa equals/hashCode mặc định của Object cũng có thể vượt qua. Hãy luôn tạo **b** độc lập với a nhưng bằng nhau theo giá trị.

## 2. HashMap và HashSet nhận diện đối tượng bằng nhau thế nào?

**HashMap<K, V>** dùng hash của key để xác định vùng tìm kiếm phù hợp và so sánh key bằng quan hệ equals để tìm entry. Khi **put(a, "ACTIVE")**, lệnh **get(b)** và **containsKey(b)** có thể tìm đúng entry nếu b bằng a và đáp ứng hợp đồng hash. Khi tiếp tục **put(b, "UPDATED")**, Map phải cập nhật cùng khóa logic, không sinh một entry mới.

**HashSet<E>** lưu các phần tử duy nhất theo equality/hash. Sau **add(a)**, lệnh **contains(b)** phải trả true và **add(b)** phải trả false nếu b bằng a; kích thước Set không tăng.

| Phép kiểm | Kết quả mong đợi với a và b bằng nhau nhưng khác tham chiếu |
| --- | --- |
| **a != b** | true |
| **a.equals(b)** và **b.equals(a)** | true |
| **a.hashCode() == b.hashCode()** | true |
| **map.put(a, v); map.get(b)** | v |
| **map.containsKey(b)** | true |
| **map.put(b, v2)** | cập nhật value cho cùng khóa logic; Map vẫn có 1 entry nếu ban đầu rỗng |
| **set.add(a); set.contains(b)** | true khi contains |
| **set.add(a); set.add(b)** | lần thêm đầu true, lần sau false; Set vẫn có 1 phần tử |

Không được kết luận hai object bằng nhau chỉ vì hash trùng: **collision được phép**. HashMap/HashSet phải phân biệt hai đối tượng có equals() false dù hashCode bằng nhau.

## 3. Hợp đồng equals() và hashCode()

**equals()** phải thỏa các điều kiện, giả sử trạng thái có liên quan không thay đổi:

1. **Reflexive:** a.equals(a) là true.
2. **Symmetric:** a.equals(b) và b.equals(a) cho cùng kết quả.
3. **Transitive:** nếu a bằng b, b bằng c, thì a bằng c.
4. **Consistent:** so sánh lặp lại trên những object có trạng thái không đổi cho cùng kết quả.
5. **Non-null:** a.equals(null) là false với a khác null.

**hashCode()** yêu cầu:

- Lặp lại trên cùng đối tượng với thông tin equality không đổi trong một lần thực thi phải trả cùng kết quả.
- **Nếu a.equals(b) == true thì a.hashCode() == b.hashCode().**
- Nếu a không bằng b, hash có thể trùng hoặc khác; không bắt buộc khác nhau.

Nếu override equals() để so sánh theo field nhưng không override hashCode() tương thích, hai instance có thể equals true nhưng có hash khác nhau, dẫn tới tra cứu lỗi trong HashMap/HashSet. Hãy dùng cùng các thành phần xác định khóa logic cho cả hai phương thức. Với khóa cần ổn định lâu dài trong collection, nên chọn dữ liệu immutable; nếu field tham gia hash bị sửa sau khi thêm, collection không tự tổ chức lại theo hash mới.

## 4. Chương trình Java 17+ chạy độc lập

Lớp UserKey được khai báo **final**, trường **tenantId** và **userId** cũng final. Phương thức equals() so sánh hai trường; hashCode() tính từ cùng hai trường. Mã dưới đây kiểm tra bằng một object mới cùng giá trị, chèn Map/Set, cập nhật bằng object tương đương, và từ chối khóa khác.

Tạo tệp **EqualKeyLookupDemo.java**:

~~~java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class EqualKeyLookupDemo {
    static final class UserKey {
        private final String tenantId;
        private final int userId;

        UserKey(String tenantId, int userId) {
            this.tenantId = Objects.requireNonNull(tenantId);
            this.userId = userId;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof UserKey other)) {
                return false;
            }
            return userId == other.userId
                    && tenantId.equals(other.tenantId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, userId);
        }
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        UserKey first = new UserKey("team-a", 42);
        UserKey equivalent = new UserKey("team-a", 42);
        UserKey different = new UserKey("team-a", 43);

        System.out.println("Khác tham chiếu: "
                + (first != equivalent));
        System.out.println("Bằng nhau theo equals: "
                + first.equals(equivalent));
        System.out.println("Hash bằng nhau: "
                + (first.hashCode() == equivalent.hashCode()));

        require(first != equivalent, "Phải tạo hai instance khác nhau");
        require(first.equals(equivalent)
                        && equivalent.equals(first),
                "equals phải nhất quán hai chiều");
        require(first.hashCode() == equivalent.hashCode(),
                "Đối tượng bằng nhau phải cùng hash");

        Map<UserKey, String> map = new HashMap<>();
        map.put(first, "ACTIVE");

        System.out.println("Map tra bằng đối tượng khác: "
                + map.get(equivalent));
        System.out.println("Map containsKey: "
                + map.containsKey(equivalent));

        require("ACTIVE".equals(map.get(equivalent)),
                "Map không tìm được khóa bằng nhau");
        require(map.containsKey(equivalent),
                "Map không nhận ra khóa bằng nhau");

        String oldValue = map.put(equivalent, "UPDATED");
        System.out.println("Giá trị cũ khi put: " + oldValue);
        System.out.println("Số entry của Map: " + map.size());
        System.out.println("Giá trị bằng khóa ban đầu: "
                + map.get(first));

        require("ACTIVE".equals(oldValue), "Put không cập nhật đúng");
        require(map.size() == 1, "Map sinh khóa logic trùng");
        require("UPDATED".equals(map.get(first)),
                "Map không trả value đã cập nhật");

        Set<UserKey> set = new HashSet<>();
        boolean addedFirst = set.add(first);
        boolean addedAgain = set.add(equivalent);

        System.out.println("Set thêm ban đầu: " + addedFirst);
        System.out.println("Set thêm đối tượng bằng nhau: "
                + addedAgain);
        System.out.println("Set contains: "
                + set.contains(equivalent));
        System.out.println("Số phần tử của Set: " + set.size());

        require(addedFirst && !addedAgain,
                "Set phải ngăn phần tử bằng nhau");
        require(set.contains(equivalent) && set.size() == 1,
                "Set không tìm thấy phần tử tương đương");

        System.out.println("Map chứa khóa khác: "
                + map.containsKey(different));
        System.out.println("Set chứa khóa khác: "
                + set.contains(different));

        require(!map.containsKey(different), "Map nhầm khóa khác");
        require(!set.contains(different), "Set nhầm khóa khác");

        System.out.println("Tất cả kiểm thử thành công.");
    }
}
~~~

Lệnh biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 EqualKeyLookupDemo.java
java EqualKeyLookupDemo
~~~

**Stdout mong đợi chính xác:**

~~~text
Khác tham chiếu: true
Bằng nhau theo equals: true
Hash bằng nhau: true
Map tra bằng đối tượng khác: ACTIVE
Map containsKey: true
Giá trị cũ khi put: ACTIVE
Số entry của Map: 1
Giá trị bằng khóa ban đầu: UPDATED
Set thêm ban đầu: true
Set thêm đối tượng bằng nhau: false
Set contains: true
Số phần tử của Set: 1
Map chứa khóa khác: false
Set chứa khóa khác: false
Tất cả kiểm thử thành công.
~~~

Giải thích: **first** và **equivalent** có identity khác, nhưng bằng nhau theo đúng hai field làm khóa. HashMap tra cứu được bằng equivalent, cập nhật bằng equivalent giữ đúng một entry; HashSet nhận ra giá trị tương đương và không thêm trùng. **different** không bằng first vì userId khác. Hàm **require(...)** ném AssertionError ngay khi điều kiện không đạt, khác với câu lệnh **assert** của Java có thể bị vô hiệu hóa khi thiếu cờ **-ea**.

Bài kiểm thử không in toàn bộ HashMap/HashSet để so thứ tự phần tử vì **không có bảo đảm về thứ tự lặp** của hai collection này.

## 5. Viết kiểm thử JUnit Jupiter (JUnit 5)

Trong ứng dụng thực tế, hãy dùng assertion có thông báo rõ ràng. Đoạn dưới là **mã test bổ sung**, yêu cầu dependency JUnit 5 và lớp EqualKeyLookupDemo ở cùng package, không phải chương trình độc lập chạy chỉ bằng lệnh java.

~~~java
import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EqualKeyLookupDemoTest {

    @Test
    void equalDistinctKeysFindOneMapEntry() {
        var a = new EqualKeyLookupDemo.UserKey("team-a", 42);
        var b = new EqualKeyLookupDemo.UserKey("team-a", 42);

        assertNotSame(a, b);
        assertEquals(a, b);
        assertEquals(b, a);
        assertEquals(a.hashCode(), b.hashCode());

        Map<EqualKeyLookupDemo.UserKey, String> map = new HashMap<>();
        map.put(a, "ACTIVE");

        assertTrue(map.containsKey(b));
        assertEquals("ACTIVE", map.get(b));
        assertEquals("ACTIVE", map.put(b, "UPDATED"));
        assertEquals(1, map.size());
        assertEquals("UPDATED", map.get(a));
    }

    @Test
    void equalDistinctElementsCountOnceInSet() {
        var a = new EqualKeyLookupDemo.UserKey("team-a", 42);
        var b = new EqualKeyLookupDemo.UserKey("team-a", 42);

        Set<EqualKeyLookupDemo.UserKey> set = new HashSet<>();
        assertTrue(set.add(a));
        assertTrue(set.contains(b));
        assertFalse(set.add(b));
        assertEquals(1, set.size());
    }

    @Test
    void differentKeysDoNotMatch() {
        var a = new EqualKeyLookupDemo.UserKey("team-a", 42);
        var otherId = new EqualKeyLookupDemo.UserKey("team-a", 43);
        var otherTenant = new EqualKeyLookupDemo.UserKey("team-b", 42);

        assertNotEquals(a, otherId);
        assertNotEquals(a, otherTenant);
        assertNotEquals(a, null);

        Map<EqualKeyLookupDemo.UserKey, String> map = new HashMap<>();
        map.put(a, "ACTIVE");
        assertFalse(map.containsKey(otherId));
        assertFalse(map.containsKey(otherTenant));

        Set<EqualKeyLookupDemo.UserKey> set = new HashSet<>();
        set.add(a);
        assertFalse(set.contains(otherId));
        assertFalse(set.contains(otherTenant));
    }

    @Test
    void equalityContract() {
        var a = new EqualKeyLookupDemo.UserKey("team-a", 42);
        var b = new EqualKeyLookupDemo.UserKey("team-a", 42);
        var c = new EqualKeyLookupDemo.UserKey("team-a", 42);

        assertEquals(a, a);
        assertEquals(a, b);
        assertEquals(b, a);
        assertEquals(b, c);
        assertEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(b.hashCode(), c.hashCode());
    }
}
~~~

Khi xây dựng test riêng cho class của dự án, thay UserKey bằng khóa thực tế. Nếu khóa gồm nhiều field, phải kiểm thử ít nhất một trường hợp **khác ở từng field tham gia equality**; nếu không, equals() quá rộng có thể xem nhầm hai khóa khác nhau là cùng khóa.

## 6. Phân biệt collision với equality

Hai đối tượng **không bằng nhau nhưng có cùng hashCode** vẫn phải được HashMap/HashSet lưu và tra cứu độc lập. Có thể dựng collision cố ý bằng một lớp mà hashCode() luôn trả 1. Điều này **vẫn thỏa hợp đồng** (các khóa bằng nhau chắc chắn có hash như nhau) nhưng kém hiệu năng nghiêm trọng với tập lớn.

Ví dụ bổ sung minh họa, giả định có import **java.util.Map** và **java.util.HashMap**:

~~~java
record CollisionKey(int id) {
    @Override
    public int hashCode() {
        return 1;
    }
}

Map<CollisionKey, String> map = new HashMap<>();
map.put(new CollisionKey(1), "ONE");
map.put(new CollisionKey(2), "TWO");

// Hai khóa có hash trùng nhưng equals khác.
if (!"ONE".equals(map.get(new CollisionKey(1)))
        || !"TWO".equals(map.get(new CollisionKey(2)))
        || map.size() != 2) {
    throw new AssertionError("Hash collision bị xử lý sai");
}
~~~

Đoạn minh họa này không phải tệp Java độc lập: đặt record và các statement vào ngữ cảnh khai báo phù hợp, chẳng hạn record ở mức lớp và các statement trong test method. Một collision không chứng minh lỗi equals/hashCode. **Không dùng hash hằng trong ứng dụng sản xuất** chỉ để đơn giản hóa việc tính hash.

## 7. Các tình huống lỗi thường gặp

### 7.1. Override equals() mà quên hashCode()

Giả sử lớp chỉ ghi đè equals() để so sánh tenantId và userId nhưng để hashCode() kế thừa Object. Hai instance khác nhau có thể equals true nhưng hashCode khác, khiến HashMap/HashSet tìm sai vùng và trả kết quả không mong muốn.

**Không nên cố tình viết test luôn mong đợi lỗi tra cứu** cho kiểu lớp hỏng này, vì identity hash có thể trùng tình cờ; sự thất bại không được bảo đảm trên mọi lần chạy. Test phải khẳng định trực tiếp **a.equals(b) ⇒ a.hashCode()==b.hashCode()** cùng với hành vi tìm kiếm.

### 7.2. Dùng == để so sánh String trong equals()

**tenantId == other.tenantId** so sánh tham chiếu, trong khi hai String khác object có thể chứa cùng nội dung. Dùng **tenantId.equals(other.tenantId)** khi chắc chắn non-null; dùng **Objects.equals(...)** khi cần hỗ trợ null.

### 7.3. Không thống nhất tiêu chí equality và hash

Nếu equals() xem hai khóa cùng userId là bằng nhau bất kể tenantId, nhưng hashCode() đưa cả tenantId vào tính toán, hai khóa được equals xem là bằng nhau có thể có hash khác: sai hợp đồng. Ngược lại, nếu hashCode() chỉ dùng userId còn equals() dùng cả tenantId và userId thì hợp đồng có thể vẫn đúng, nhưng dễ tạo nhiều collision hơn.

### 7.4. Sửa khóa mutable sau khi chèn

Nếu equals/hashCode dựa trên trường **code** mutable, lệnh **map.put(key, value)** lưu entry theo hash ban đầu. Sau **key.code = "new-code"**, gọi **map.containsKey(key)** bằng chính tham chiếu cũ vẫn **có thể thất bại** vì collection không tự di chuyển entry đến vùng hash mới. HashSet gặp rủi ro tương tự.

Ưu tiên dùng **final field với thành phần immutable**, hoặc một record chỉ chứa các component an toàn. Nếu thực sự phải thay khóa, quản lý thao tác xóa khóa ở trạng thái cũ và thêm khóa mới có kiểm soát; tránh để đối tượng bên ngoài sửa trực tiếp dữ liệu làm khóa.

### 7.5. equals() bất đối xứng giữa lớp cha và lớp con

Lớp cha có thể cho rằng nó bằng một instance lớp con, nhưng lớp con yêu cầu thêm field và không coi là bằng lớp cha. Khi **a.equals(b) != b.equals(a)**, hợp đồng bị phá vỡ và tra cứu trong collection có thể phụ thuộc hướng so sánh. Với kiểu khóa giá trị, **final class** hoặc **record** thường giúp diễn đạt equality minh bạch. Nếu cần kế thừa, phải kiểm thử đối xứng và bắc cầu giữa mọi kiểu liên quan.

### 7.6. Dùng map.get() == null để kết luận khóa không tồn tại

**HashMap cho phép value null**: một khóa b có thể tồn tại, **map.containsKey(b)==true**, nhưng **map.get(b)==null**. Nếu bài toán cần phân biệt khóa chưa chèn và khóa ánh xạ tới null, hãy kiểm tra **containsKey**. HashSet.contains trực tiếp trả boolean.

### 7.7. Kiểm thử chỉ bằng một instance

Chèn a rồi gọi get(a) không chứng minh được lookup bằng khóa tương đương. Test phải tạo b khác identity, assert chúng bằng nhau và tra bằng b. Kiểm tra chỉ có cùng hash cũng không đủ để chứng minh equality.

### 7.8. Nhầm record với bất biến sâu

Một **record** tự sinh equals/hashCode dựa vào các component, nhưng component có thể là List, array hoặc một object mutable. Nếu component có trạng thái thay đổi và tham gia hashCode, khóa record vẫn có thể mất tính ổn định. Record đơn giản với String và primitive an toàn hơn; với component mutable cần hợp đồng defensive copy và equality phù hợp.

## 8. Ma trận ca kiểm thử quan trọng

| Trường hợp | Dữ liệu | Kết quả cần chứng minh |
| --- | --- | --- |
| Positive equality | Hai instance khác nhau, cùng mọi trường khóa | Map get/containsKey và Set contains tìm thấy |
| Put/update trùng khóa logic | put(a), sau đó put(b) | Map chỉ có một entry và giữ giá trị mới |
| Add trùng phần tử | add(a), sau đó add(b) | add(b) false và size không tăng |
| Negative theo mã người dùng | Cùng tenant, userId khác | Không khớp |
| Negative theo tổ chức | Cùng userId, tenant khác | Không khớp |
| Phản xạ/đối xứng/bắc cầu | Tạo a, b, c bằng nhau | Tất cả tính chất equals đều đúng |
| Hash contract | a.equals(b) true | hashCode(a)==hashCode(b) |
| Collision | Hai khóa khác nhau, cố ý cùng hash | Collection vẫn phân biệt |
| Null value trong Map | put(a, null) | containsKey(b) true dù get(b) null |
| Mutable key | Field hash thay đổi sau khi chèn | Nhận diện nguy cơ và sửa thiết kế khóa |
| Kế thừa / proxy | Các kiểu khóa liên quan theo thiết kế | Không phá symmetry/transitivity |
| Nhiều phần tử | Thêm nhiều khóa có trùng và không trùng | Số lượng và giá trị đúng; không dựa vào thứ tự |

**Không nên khẳng định các hashCode của object khác nhau bắt buộc khác nhau** hoặc Map/Set luôn có một thứ tự duyệt cụ thể. Những kỳ vọng đó nằm ngoài hợp đồng Java.

## 9. Dùng record để biểu diễn khóa theo giá trị

Với Java 17+, record gọn khi các component đúng là thông tin nhận diện khóa logic. Ví dụ sau là **mảnh mã minh họa**, cần các import Map, HashMap tương ứng và đặt các statement trong method:

~~~java
record UserKey(String tenantId, int userId) {}

UserKey first = new UserKey("team-a", 42);
UserKey equivalent = new UserKey("team-a", 42);
Map<UserKey, String> map = new HashMap<>();
map.put(first, "ACTIVE");

// map.get(equivalent) trả "ACTIVE".
// map.containsKey(equivalent) trả true.
~~~

Record tự cung cấp equals/hashCode theo các component; vẫn phải kiểm tra rằng **toàn bộ component** thực sự thuộc khóa logic. Nếu một trường chỉ là dữ liệu mô tả không nên tham gia equality, hãy định nghĩa kiểu khóa tách biệt chứa đúng các field cần thiết, thay vì dùng record bao gồm cả trường mô tả. Việc khai báo record không bảo đảm các object do component trỏ tới là bất biến sâu.

## 10. Checklist đánh giá PASS

Một bộ kiểm thử nên kết luận PASS cho hợp đồng lookup khi:

- Hai **đối tượng riêng biệt** có cùng nội dung khóa được chứng minh khác identity nhưng bằng nhau cả hai chiều equals().
- equals() phản xạ, đối xứng, bắc cầu, nhất quán và trả false với null; các đối tượng bằng nhau có cùng hashCode().
- Map đã lưu a vẫn tìm bằng b, cập nhật bằng b không tăng số khóa logic và đọc bằng a thấy value mới.
- Set đã chứa a vẫn tìm bằng b; thêm b không tăng số phần tử.
- Các khóa không bằng nhau không bị nhầm, kể cả khi trùng hash.
- Không dùng field mutable làm thay đổi nghĩa/hash của khóa sau khi chèn; nếu có chia sẻ trạng thái thì thiết kế xử lý theo hợp đồng.
- Assertions thực sự được thực thi; kết quả không phụ thuộc thứ tự HashMap/HashSet hay identity hash may rủi.

**Kết luận:** phép thử quyết định là **thêm một instance vào HashMap/HashSet rồi tra cứu bằng instance khác nhưng bằng nhau theo equals() và có hashCode tương thích**. Kết hợp phép thử đó với cập nhật/chống trùng, negative case, collision và sự ổn định của khóa để kiểm chứng đúng hợp đồng đối tượng trong ứng dụng Java.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-verification-008">Kiểm thử null, NaN, +0.0, -0.0, mảng và cấu trúc lồng</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử `null`, `NaN`, `+0.0`, `-0.0`, mảng và cấu trúc lồng trong hợp đồng đối tượng Java

Một phương thức `equals()` trông đúng với dữ liệu thông thường vẫn có thể sai khi gặp `null`, số thực đặc biệt, mảng hoặc đối tượng lồng nhau. Sai lệch thường kéo theo `hashCode()` không nhất quán, hoặc `compareTo()`/`Comparator` coi hai khóa như nhau trong khi `equals()` không coi chúng bằng nhau. Hậu quả có thể là `HashSet` chứa phần tử trùng về mặt nghiệp vụ, `HashMap` tra khóa thất bại hoặc `TreeSet` loại bỏ dữ liệu không mong muốn.

Bài này tập trung **cách xác định kỳ vọng, triển khai và kiểm thử hợp đồng** trong Java 17+: phân biệt toán tử `==` với giá trị bằng nhau, lựa chọn ngữ nghĩa cho `double`/`float`, so sánh mảng đúng chiều sâu, và giữ hợp đồng ổn định với cấu trúc lồng. Không có một phép so sánh nào đúng cho mọi mô hình: phải xác định "bằng nhau" có nghĩa gì với kiểu đối tượng cụ thể, rồi dùng cùng một quy tắc cho `equals()`, `hashCode()` và quan hệ thứ tự nếu cần.

## 1. Các bất biến phải kiểm chứng trước trường hợp biên

Với các đối tượng không `null` là `x`, `y`, `z`:

1. **Phản xạ:** `x.equals(x)` là `true`.
2. **Đối xứng:** `x.equals(y) == y.equals(x)`.
3. **Bắc cầu:** nếu `x.equals(y)` và `y.equals(z)` thì `x.equals(z)`.
4. **Nhất quán:** kết quả không đổi giữa các lần gọi nếu trạng thái tham gia so sánh không thay đổi.
5. **Với `null`:** `x.equals(null)` phải là `false`.
6. **Ràng buộc băm:** nếu `x.equals(y)` thì **bắt buộc** `x.hashCode() == y.hashCode()`. Hai đối tượng không bằng nhau **vẫn có thể** cùng hash; không dùng điều ngược lại làm tiêu chuẩn.
7. **Ràng buộc thứ tự:** `compareTo`/`Comparator` phải nhất quán về dấu khi đảo đối số, có tính bắc cầu và nhất quán theo lớp tương đương của phép so sánh. Với `TreeSet`/`TreeMap`, hai khóa có kết quả `compare(...) == 0` được xem là cùng khóa. Nếu loại dữ liệu kỳ vọng tính duy nhất theo `equals`, nên thiết kế comparator **consistent with equals**, tức `compare(x,y)==0` khi và chỉ khi `x.equals(y)`.

Kiểm thử cần tạo **hai instance độc lập nhưng cùng giá trị**, vì `x.equals(x)` luôn đúng do nhánh `this == other` có thể che lỗi so sánh trường. Với mỗi cặp đã cho là bằng nhau, kiểm tra cả hai hướng `equals` và hash tương ứng; với mỗi giá trị biên, kiểm tra khác biệt có chủ đích.

### Bảng ca kiểm thử tối thiểu

| Ca | Điều cần kiểm tra | Kỳ vọng nền tảng Java |
| --- | --- | --- |
| Đối tượng so với `null` | `x.equals(null)` | `false` |
| Hai tham chiếu đều `null` | `Objects.equals(null, null)` | `true` |
| Thuộc tính nullable | Hai đối tượng có trường `null` cùng vị trí | Bằng nhau nếu các trường khác cùng giá trị |
| `NaN` với toán tử số học | `Double.NaN == Double.NaN` | `false` |
| Hai số `NaN` dưới `Double.equals` | Bao gồm các payload bit khác nhau | `true` |
| `+0.0` với `-0.0` dùng `==` | `+0.0 == -0.0` | `true` |
| `+0.0` với `-0.0` dùng `Double.equals` | So sánh hai wrapper | `false` |
| Mảng nguyên thủy cùng phần tử | `Arrays.equals(a,b)` | `true` |
| Hai mảng lồng cùng dữ liệu | `Arrays.deepEquals(a,b)` | `true` với cấu trúc tương thích, không có chu trình |
| Collection lồng có mảng bên trong | `List.equals` trên phần tử kiểu mảng | **Không** tự trở thành so sánh theo nội dung mảng |
| Đối tượng tùy biến | `equals` cùng `hashCode` | Hai đối tượng bằng nhau có hash bằng nhau |
| Khóa trong sorted collection | `compare(x,y)==0` | `TreeSet`/`TreeMap` xem là một khóa |

## 2. `null`: kiểm tra cả receiver lẫn trường thành phần

`null` không phải một instance để gọi phương thức. Biểu thức `null.equals(x)` không hợp lệ về ngữ nghĩa và nếu biến tham chiếu bằng `null` rồi gọi `reference.equals(x)` sẽ ném `NullPointerException`. Sử dụng `Objects.equals(a,b)` cho phép so sánh hai tham chiếu an toàn:

```java
Objects.equals(null, null);      // true
Objects.equals(null, "value");   // false
Objects.equals("x", "x");        // true
Objects.hashCode(null);          // 0
```

Đoạn trên là các biểu thức minh họa, cần `import java.util.Objects;` khi đặt trong một lớp Java.

Trong một lớp giá trị có trường `name` có thể `null`, nên viết `Objects.equals(name, that.name)` thay cho `name.equals(that.name)`. Trong một mảng/collection, phân biệt rõ **tham chiếu collection bằng `null`**, **collection rỗng** và **collection chứa phần tử `null`**. Đây là ba trạng thái khác nhau tùy hợp đồng nghiệp vụ.

Đừng tự biến mọi `null` thành giá trị mặc định nếu mô hình không yêu cầu. Nếu constructor cấm `null`, dùng `Objects.requireNonNull(...)` và kiểm thử nó ném lỗi ở biên tạo đối tượng; nếu cho phép `null`, `equals`/`hashCode` phải xử lý nhất quán.

Với so sánh có thứ tự, `Comparator.naturalOrder()` không chấp nhận `null`; `Comparator.nullsFirst(...)` hoặc `nullsLast(...)` đưa ra quy tắc rõ ràng khi thứ tự cần chứa `null`. `equals()` của một đối tượng không được coi bản thân bằng `null` chỉ vì comparator đặt `null` ở đầu/cuối.

## 3. `NaN` và số thực: `==` khác `Double.equals()`

Theo IEEE 754 và ngữ nghĩa Java, `NaN` không bằng bất kỳ giá trị nào khi so sánh bằng toán tử `==`, kể cả chính nó:

```java
double a = Double.NaN;
double b = Double.longBitsToDouble(0x7ff8000000000001L);

// a == b               => false
// Double.isNaN(a)       => true
// Double.isNaN(b)       => true
// Double.compare(a, b)  => 0
// Double.valueOf(a).equals(Double.valueOf(b)) => true
```

`b` có payload NaN khác `a` ở dạng bit thô. `Double.equals` dùng `Double.doubleToLongBits` để xét bit với **các giá trị NaN được canonicalize**. Vì vậy nó coi mọi NaN bằng nhau. `Double.hashCode` dùng cách diễn giải bit tương thích, nên những giá trị NaN được `Double.equals` coi bằng nhau cũng có hash giống nhau. `Float.equals`/`Float.hashCode` áp dụng nguyên tắc tương ứng với `float`.

**Không dùng `x == y` cho `double` trong một `equals()` rồi dùng `Double.hashCode(x)` nếu dự định coi các NaN là bằng nhau**: hai đối tượng có NaN tương đương theo mô hình sẽ bị `equals` từ chối, trong khi các thành phần khác có thể cùng. Điều này có thể không vi phạm mệnh đề một chiều về hash, nhưng lại **khác ngữ nghĩa mong muốn**. Để thực hiện ngữ nghĩa kiểu `Double`, dùng `Double.compare(a,b)==0` và `Double.hashCode(a)` hoặc `Double.doubleToLongBits(a)==Double.doubleToLongBits(b)` cùng phép băm tương ứng.

### `+0.0` và `-0.0`: cùng toán tử nhưng khác wrapper

```java
// +0.0 == -0.0                                      => true
// Double.compare(-0.0, +0.0) < 0                     => true
// Double.valueOf(+0.0).equals(Double.valueOf(-0.0)) => false
// Double.hashCode(+0.0) == Double.hashCode(-0.0)    => false
```

Lý do: `+0.0` và `-0.0` có biểu diễn bit khác nhau, dù `==` coi chúng bằng nhau. `Double.compare` định nghĩa một thứ tự toàn phần tiện cho sắp xếp: `-0.0` đứng trước `+0.0`; các NaN đứng sau mọi giá trị số khác và được so sánh như cùng một nhóm. Kiểm thử `Double.compare(a,b)` tại `-Infinity`, số hữu hạn, `-0.0`, `+0.0`, `+Infinity` và `NaN` khi lớp cần thứ tự.

Nếu nghiệp vụ muốn **coi hai số không là cùng một giá trị**, có thể chuẩn hóa cả hai trước so sánh **và trước hash**, ví dụ:

```java
static double normalizeZero(double value) {
    return value == 0.0d ? 0.0d : value;
}

static boolean sameMetric(double left, double right) {
    return Double.compare(normalizeZero(left), normalizeZero(right)) == 0;
}

static int metricHash(double value) {
    return Double.hashCode(normalizeZero(value));
}
```

Đây là các phương thức minh họa đặt bên trong một lớp. Quy tắc này giữ NaN theo cách của `Double.compare`, gộp `+0.0` với `-0.0`, và tránh vi phạm hợp đồng hash. Chọn cách này **chỉ khi phù hợp đặc tả đối tượng**; không thay đổi quy tắc ngầm trong một codebase đã phụ thuộc vào phân biệt signed zero. Khi số thực đại diện cho phép đo, cần quyết định riêng có chấp nhận sai số hay không; **không tùy tiện dùng phép so sánh gần đúng `abs(a-b) < epsilon` cho `equals`**, vì quan hệ đó nói chung không bắc cầu (A gần B, B gần C, nhưng A không gần C), đồng thời làm khóa hash khó đúng.

## 4. Mảng: `equals()` của chính mảng, `Arrays.equals` và `Arrays.deepEquals`

Mảng trong Java **không ghi đè `Object.equals`**, do đó `a.equals(b)` so sánh danh tính đối tượng. Hai mảng khác nhau nhưng chứa cùng phần tử vẫn trả `false`. Sử dụng `Arrays.equals` để so sánh phần tử ở một chiều:

```java
int[] a = {1, 2};
int[] b = {1, 2};

a.equals(b);        // false
Arrays.equals(a,b); // true
Arrays.hashCode(a) == Arrays.hashCode(b); // true
```

Cần `import java.util.Arrays;` nếu đưa các biểu thức vào lớp hoàn chỉnh. `Arrays.equals` có các overload cho mảng nguyên thủy và mảng đối tượng. Với `Object[]`, phép so sánh phần tử dùng ngữ nghĩa `equals` của từng phần tử; nếu một phần tử lại là mảng, lần so sánh đó vẫn là **so sánh tham chiếu**.

Với cấu trúc nhiều tầng, dùng `Arrays.deepEquals(Object[],Object[])` và `Arrays.deepHashCode(Object[])`:

```java
Object[] first = {new int[]{1, 2}, new Object[]{"A", new String[]{"B"}}};
Object[] second = {new int[]{1, 2}, new Object[]{"A", new String[]{"B"}}};

Arrays.equals(first, second);     // false: phần tử kiểu mảng là tham chiếu khác
Arrays.deepEquals(first, second); // true: so sánh mảng con theo nội dung
Arrays.deepHashCode(first) == Arrays.deepHashCode(second); // true
```

Mảng hai chiều kiểu `int[][]` cũng là mảng các tham chiếu `int[]`, do đó cần `deepEquals/deepHashCode` khi kỳ vọng so sánh nội dung mọi tầng. `Arrays.deepEquals` áp dụng cho `Object[]` ở lớp ngoài, **không** gọi trực tiếp `Arrays.deepEquals(int[], int[])` vì không có overload như vậy. Với mảng nguyên thủy một chiều, chọn đúng `Arrays.equals(int[],int[])` và `Arrays.hashCode(int[])`.

Hai tham chiếu mảng đều `null` được `Arrays.equals` coi là bằng nhau, còn một bên `null` và một bên mảng rỗng là khác nhau. `Arrays.deepEquals` cũng xử lý hai tham chiếu mảng `Object[]` đều `null`. Cấu trúc tự chứa chu trình (mảng tham chiếu lại chính nó, hoặc hai mảng tham chiếu vòng) **không thích hợp cho phép đệ quy sâu này**; thiết kế so sánh theo đồ thị có cơ chế phát hiện chu trình riêng khi dữ liệu có vòng.

**Đừng ghép `Arrays.deepEquals(...)` với `Arrays.hashCode(...)` một cách máy móc.** Nếu bằng nhau được xét ở mọi tầng, phép hash cũng phải xét ở mọi tầng; ngược lại có thể làm hai đối tượng bằng nhau tạo hash khác nhau.

### `record` và mảng: tự sinh `equals` chưa chắc là so sánh sâu

Java `record` tự sinh `equals`/`hashCode` theo các component, nhưng component kiểu mảng vẫn dùng đặc tính so sánh danh tính của mảng. Do đó:

```java
record IntBundle(int[] values) {}

new IntBundle(new int[]{1, 2})
    .equals(new IntBundle(new int[]{1, 2})); // false
```

Đó là lý do không giả định `record` tự làm deep equality cho mọi kiểu component. Nếu mảng là một phần danh tính giá trị, cần viết hợp đồng tùy chỉnh và sao chép phòng vệ phù hợp, hoặc biểu diễn bằng kiểu collection bất biến có ngữ nghĩa nội dung.

## 5. Danh sách, tập hợp, bản đồ và cấu trúc lồng

`List.equals` so sánh theo thứ tự từng phần tử; `Set.equals` so theo tập phần tử không quan tâm thứ tự; `Map.equals` so theo ánh xạ khóa–giá trị. Những hợp đồng này dựa trên `equals/hashCode` của phần tử hoặc khóa/giá trị, vì vậy **nội dung bên trong không được so sánh sâu tự động nếu chính kiểu phần tử thiếu hợp đồng giá trị**.

```java
List<List<String>> left = List.of(List.of("a", "b"), List.of("c"));
List<List<String>> right = List.of(List.of("a", "b"), List.of("c"));
// left.equals(right) => true: List lồng có equality theo phần tử.

List<int[]> leftArrays = List.of(new int[]{1, 2});
List<int[]> rightArrays = List.of(new int[]{1, 2});
// leftArrays.equals(rightArrays) => false: phần tử int[] so sánh danh tính.
```

Đây là các biểu thức minh họa bên trong một lớp có `import java.util.List;`. Với nested objects, xác định rõ tầng nào tham gia nhận diện đối tượng và tầng nào được bỏ qua. Một đối tượng bao gồm `List<List<String>>` sẽ so sánh nội dung xuyên các tầng List theo hợp đồng sẵn có, nhưng khi danh sách chứa `int[]` hay đối tượng tự định nghĩa không ghi đè `equals` thì cần xử lý thêm.

Với `Map` có key là mảng hoặc đối tượng mutable, dễ xảy ra lỗi khó thấy: thay đổi trường tham gia `equals/hashCode` **sau khi đưa vào `HashMap`/`HashSet`** có thể khiến không tìm lại được khóa vì bucket hash lúc tra đã khác bucket khi chèn. Giải pháp là thiết kế khóa có trạng thái liên quan đến equality được cố định hợp lý, và **sao chép phòng vệ** khi chứa hoặc trả mảng/collection mutable.

Nếu cần so sánh một cấu trúc lồng pha trộn `Map`, `List`, mảng, và đối tượng tùy biến, không thể gọi duy nhất `Objects.deepEquals` rồi mặc định có một hợp đồng deep equality tổng quát cho mọi loại collection. `Objects.deepEquals` chỉ có xử lý mảng đặc biệt khi cả hai đối số là mảng; nó không đệ quy hiểu quy tắc nghiệp vụ của một đồ thị đối tượng bất kỳ.

## 6. Ví dụ Java 17 hoàn chỉnh: kiểm chứng equality, hash và dữ liệu lồng

Tạo **`ObjectContractEdgeCases.java`**. `Snapshot` là một lớp giá trị có `name` nullable, số `double`, mảng nguyên thủy và `List<List<String>>`. Constructor tạo bản sao của mảng và các List để không cho caller thay đổi trạng thái tham gia `equals/hashCode` sau khi tạo. Trong ví dụ, List bên trong phải không chứa phần tử `null`; List bên ngoài cũng không được chứa List `null` khi không `null` toàn bộ.

```java
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ObjectContractEdgeCases {
    private static final class Snapshot {
        private final String name;
        private final double metric;
        private final int[] samples;
        private final List<List<String>> groups;

        Snapshot(String name, double metric, int[] samples,
                 List<List<String>> groups) {
            this.name = name;
            this.metric = metric;
            this.samples = samples == null ? null : samples.clone();
            this.groups = groups == null ? null
                    : groups.stream().map(List::copyOf).toList();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Snapshot that)) {
                return false;
            }
            return Objects.equals(name, that.name)
                    && Double.compare(metric, that.metric) == 0
                    && Arrays.equals(samples, that.samples)
                    && Objects.equals(groups, that.groups);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(name, Double.hashCode(metric), groups);
            return 31 * result + Arrays.hashCode(samples);
        }
    }

    record IntBundle(int[] values) {}

    private static void check(boolean result, String description) {
        if (!result) {
            throw new AssertionError(description);
        }
    }

    public static void main(String[] args) {
        double otherNaN = Double.longBitsToDouble(0x7ff8000000000001L);
        int[] a = {1, 2};
        int[] b = {1, 2};

        Object[] deepA = {new int[]{1, 2},
                new Object[]{"A", new String[]{"B"}}};
        Object[] deepB = {new int[]{1, 2},
                new Object[]{"A", new String[]{"B"}}};

        List<List<String>> groupsA = List.of(List.of("a", "b"), List.of("c"));
        List<List<String>> groupsB = List.of(List.of("a", "b"), List.of("c"));

        Snapshot first = new Snapshot(null, Double.NaN, a, groupsA);
        Snapshot second = new Snapshot(null, otherNaN, b, groupsB);
        Snapshot plusZero = new Snapshot(null, +0.0, a, groupsA);
        Snapshot minusZero = new Snapshot(null, -0.0, b, groupsB);
        Snapshot emptyA = new Snapshot(null, 0.0, null, null);
        Snapshot emptyB = new Snapshot(null, 0.0, null, null);

        // Kiểm tra hợp đồng, không phụ thuộc việc chạy java -ea.
        check(!first.equals(null), "non-null object must differ from null");
        check(first.equals(first), "reflexivity");
        check(first.equals(second) && second.equals(first), "symmetry");
        check(first.hashCode() == second.hashCode(), "equal objects' hashes");
        check(!plusZero.equals(minusZero), "signed zeros are distinct");
        check(emptyA.equals(emptyB), "null fields must compare safely");
        check(Arrays.equals(a, b), "primitive array contents");
        check(Arrays.deepEquals(deepA, deepB), "nested array contents");
        check(Arrays.deepHashCode(deepA) == Arrays.deepHashCode(deepB),
                "nested array hash");

        Set<Snapshot> snapshots = new HashSet<>();
        snapshots.add(first);
        snapshots.add(second);

        System.out.println("Objects.equals(null,null): "
                + Objects.equals(null, null));
        System.out.println("snapshot.equals(null): " + first.equals(null));
        System.out.println("null fields equal: " + emptyA.equals(emptyB));
        System.out.println("NaN == NaN: " + (Double.NaN == Double.NaN));
        System.out.println("raw NaN bits differ: "
                + (Double.doubleToRawLongBits(Double.NaN)
                    != Double.doubleToRawLongBits(otherNaN)));
        System.out.println("Double.equals(NaN payloads): "
                + Double.valueOf(Double.NaN).equals(Double.valueOf(otherNaN)));
        System.out.println("+0.0 == -0.0: " + (+0.0 == -0.0));
        System.out.println("Double.equals(+0.0,-0.0): "
                + Double.valueOf(+0.0).equals(Double.valueOf(-0.0)));
        System.out.println("Double.compare(-0.0,+0.0)<0: "
                + (Double.compare(-0.0, +0.0) < 0));
        System.out.println("array Object.equals: " + a.equals(b));
        System.out.println("Arrays.equals: " + Arrays.equals(a, b));
        System.out.println("nested Arrays.equals: "
                + Arrays.equals(deepA, deepB));
        System.out.println("Arrays.deepEquals: "
                + Arrays.deepEquals(deepA, deepB));
        System.out.println("Arrays.deepHashCode match: "
                + (Arrays.deepHashCode(deepA) == Arrays.deepHashCode(deepB)));
        System.out.println("record array equals: "
                + new IntBundle(new int[]{1, 2})
                    .equals(new IntBundle(new int[]{1, 2})));
        System.out.println("nested List.equals: "
                + groupsA.equals(groupsB));
        System.out.println("NaN snapshots equal: " + first.equals(second));
        System.out.println("NaN snapshot hashes equal: "
                + (first.hashCode() == second.hashCode()));
        System.out.println("signed-zero snapshots equal: "
                + plusZero.equals(minusZero));
        System.out.println("HashSet size: " + snapshots.size());
    }
}
```

Biên dịch/chạy với JDK 17+:

```bash
javac ObjectContractEdgeCases.java
java ObjectContractEdgeCases
```

Kết quả chuẩn:

```text
Objects.equals(null,null): true
snapshot.equals(null): false
null fields equal: true
NaN == NaN: false
raw NaN bits differ: true
Double.equals(NaN payloads): true
+0.0 == -0.0: true
Double.equals(+0.0,-0.0): false
Double.compare(-0.0,+0.0)<0: true
array Object.equals: false
Arrays.equals: true
nested Arrays.equals: false
Arrays.deepEquals: true
Arrays.deepHashCode match: true
record array equals: false
nested List.equals: true
NaN snapshots equal: true
NaN snapshot hashes equal: true
signed-zero snapshots equal: false
HashSet size: 1
```

Giải thích ý nghĩa kiểm chứng:

- `first` và `second` dùng hai NaN khác payload bit, mảng `int[]` độc lập và hai cấu trúc `List<List<String>>` độc lập. Chúng vẫn bằng nhau vì `Snapshot.equals` xử lý từng kiểu đúng cách; `hashCode` cũng cho cùng kết quả và `HashSet` chỉ giữ một instance theo equality.
- `plusZero` và `minusZero` khác nhau vì chủ động theo ngữ nghĩa của `Double.compare`, phù hợp với thành phần băm `Double.hashCode`. Đây là một quyết định thiết kế, không phải bắt buộc mọi đối tượng số đều phải phân biệt hai số không.
- `emptyA`/`emptyB` kiểm tra `name`, `samples` và `groups` có thể `null`. `Arrays.equals(null,null)` và `Objects.equals(null,null)` hỗ trợ quy tắc này mà không ném `NullPointerException`.
- Hai `IntBundle` record mang mảng nội dung giống nhau vẫn không bằng nhau, vì mảng so sánh theo danh tính.
- `Snapshot` phòng vệ ở constructor, không cung cấp getter trả thẳng mảng mutable. Nếu cần getter, phải trả `samples.clone()` hoặc view/copy không sửa được; đồng thời cần bảo đảm phần tử lồng không phải đối tượng mutable làm thay đổi equality.

Ví dụ dùng dữ liệu không có chu trình và các danh sách bên trong không chứa `null`. Nếu hợp đồng đầu vào cho phép List con `null` hoặc element `null`, phải chọn chiến lược copy và định nghĩa equality tương ứng trước khi dùng trong sản phẩm.

## 7. Kiểm thử so sánh có thứ tự: tránh comparator làm mất khóa

`Comparable`/`Comparator` là hợp đồng **thứ tự**, không phải bản sao của `equals`. Ví dụ `Comparator.comparingDouble(...)` dùng thứ tự theo `Double.compare`, nên phân biệt signed zero và nhóm các NaN. Tuy nhiên, nếu comparator chỉ xét `metric` trong khi `Snapshot.equals` còn xét `name`, `samples` và `groups`, hai `Snapshot` khác nhau có thể bị xếp kết quả `0`. Với `TreeSet`/`TreeMap`, điều đó khiến một trong hai bị coi là trùng khóa.

Một comparator phù hợp cần xét đủ các thành phần nhận diện theo cách nhất quán với `equals`, hoặc tài liệu hóa rằng sorted collection đang dùng **khái niệm khóa khác**. Đối với mảng và cấu trúc lồng, việc định nghĩa thứ tự từ điển phải có quy tắc rõ ràng (thứ tự phần tử, độ dài, `null`, signed zero, NaN), tránh chỉ dựa `toString` hay giá trị hash.

Bộ ca kiểm thử cho comparator nếu được sử dụng:

1. `sign(compare(x,y)) == -sign(compare(y,x))`, với cách kiểm tra dấu an toàn bằng `Integer.signum`.
2. Nếu `compare(x,y)>0` và `compare(y,z)>0` thì `compare(x,z)>0`; tương tự với `<0`.
3. Nếu `compare(x,y)==0` thì dấu của `compare(x,z)` và `compare(y,z)` phải giống nhau với mọi `z`.
4. Nếu cần consistent with equals: `compare(x,y)==0` đúng chính xác khi `x.equals(y)`; đặc biệt kiểm tra `null` (khi comparator hỗ trợ), NaN, signed zero, mảng trùng nội dung và nested fields khác nhau.
5. Với `TreeSet`, thử thêm hai instance bằng nhau; và hai instance khác nhau theo đặc tả để chắc không bị gộp nhầm.

Không viết comparator dạng `(int)(a-b)` cho số thực hay số nguyên rộng: có thể tràn số, mất phần lẻ, biến NaN thành giá trị sai và phá tính bắc cầu. Dùng `Double.compare`, `Integer.compare` hoặc comparator chuẩn phù hợp.

## 8. Thiết kế dữ liệu kiểm thử và test tự động

Để kiểm chứng hợp đồng một cách có hệ thống, lập ma trận ca thay vì chỉ thử một cặp:

| Nhóm dữ liệu | Trường hợp đại diện | Điều cần assert |
| --- | --- | --- |
| `null` | đối tượng `null`; trường `null`/không `null`; mảng `null`/rỗng | Không ném lỗi bất ngờ, đúng quy tắc equality |
| Số thực | số thường, hai NaN khác bit, `±0.0`, `±Infinity` | Equality, hash, ordering đúng theo đặc tả |
| Mảng | cùng tham chiếu, hai mảng độc lập cùng phần tử, phần tử khác, độ dài khác | Đúng nông/sâu; hash tương ứng |
| Nested | `List<List<T>>`, mảng trong `List`, mảng trong `Map`, object con | Đúng ngữ nghĩa từng tầng |
| Tính ổn định | Đầu vào mutable bị sửa sau constructor | Trạng thái khóa không đổi nếu lớp cam kết immutable |
| Tính tương đương | ba đối tượng `x,y,z` cùng nội dung và dữ liệu biên | Phản xạ, đối xứng, bắc cầu, hash |
| Collections | `HashSet`/`HashMap` và `TreeSet`/`TreeMap` nếu có comparator | Không gộp nhầm/không mất khả năng tra khóa |

Nên dùng test có tên chỉ rõ tình huống, chẳng hạn `equalInstancesWithDistinctNaNPayloadsShareHash`, `negativeZeroAndPositiveZeroFollowChosenPolicy`, `nestedPrimitiveArraysUseDeepEquality` và `defensiveCopyKeepsHashStable`. Với JUnit 5, có thể tổ chức `@ParameterizedTest` cùng `@MethodSource` để chạy bảng cặp giá trị, và gọi các assertion như `assertEquals`/`assertNotEquals`/`assertTrue`. Cần nhớ `assertEquals(double, double)` trong thư viện kiểm thử có quy tắc riêng với kiểu số thực; khi muốn kiểm tra **chính xác hợp đồng do bạn xây**, hãy assert biểu thức `Double.compare(...)`, `x.equals(y)`, `Objects.equals(...)`, `Arrays.equals(...)` hoặc `Arrays.deepEquals(...)` cụ thể, không suy diễn mọi overload `assertEquals` có cùng logic với primitive `==`.

Với property-based testing (như jqwik hoặc QuickTheories) nếu dự án có thư viện, tạo giá trị gồm `null` có kiểm soát, kích cỡ mảng biến đổi, nested collections và những mẫu bit tạo NaN khác nhau; kiểm tra các bất biến ở mục 1 thay vì chỉ đầu ra cố định. Với dữ liệu đệ quy, đặt giới hạn độ sâu và tránh chu trình khi hợp đồng chưa hỗ trợ chu trình.

### Những lỗi phổ biến cần chủ động phát hiện

- Dùng `a.equals(b)` với `a` nullable gây `NullPointerException`; dùng `Objects.equals` hoặc bảo đảm non-null trước.
- Dùng `==` cho trường `double` nhưng mong mọi NaN bằng nhau; dùng `Double.compare`/`Double.hashCode` theo cùng chính sách.
- Coi `+0.0` và `-0.0` bằng nhau trong `equals` nhưng dùng `Double.hashCode` gốc trong `hashCode`; sẽ vi phạm hợp đồng hash. Phải chuẩn hóa cùng cách trước cả hai thao tác.
- Dùng `Arrays.equals` cho `int[][]` hoặc `Object[]` chứa mảng con rồi tưởng được so sâu; cần `Arrays.deepEquals` và `deepHashCode` đúng cặp.
- Để record tự sinh equality cho component là mảng trong khi mong so sánh nội dung; phải thay kiểu representation hoặc cài hợp đồng rõ ràng.
- Dùng mảng/collection mutable làm trường hash key nhưng cho bên ngoài sửa được; thiết kế bản sao phòng vệ và API bất biến.
- Viết comparator chỉ so một phần của đối tượng rồi sử dụng `TreeSet` như thể nó thực hiện uniqueness theo toàn bộ `equals`.
- Kiểm thử chỉ cùng tham chiếu, bỏ qua hai instance độc lập, các giá trị biên và quan hệ hash.

**Tự kiểm tra:** (1) Hai `double` NaN khác payload bit có thể bằng nhau theo `Double.equals` không? Có. (2) `+0.0 == -0.0` có đúng không, trong khi `Double.equals` khác nhau? Đúng. (3) Hai `int[][]` độc lập có dữ liệu giống nhau nên dùng `Arrays.equals` hay `Arrays.deepEquals`? `deepEquals`. (4) Nếu chuẩn hóa signed zero trong `equals` thì có phải chuẩn hóa khi băm không? Bắt buộc. (5) `HashMap` và `TreeMap` có cùng định nghĩa khóa trùng không? Không: `HashMap` dựa vào equality/hash, còn `TreeMap` dựa vào comparator hoặc natural order.

**Nguyên tắc cốt lõi:** xác định cùng một ngữ nghĩa giá trị xuyên suốt **so sánh – băm – thứ tự – tính bất biến của khóa**, rồi kiểm thử riêng `null`, NaN, hai dấu của số không, mảng một/nhiều chiều và từng tầng dữ liệu lồng. Chính các ca biên này mới làm lộ một hợp đồng đối tượng tưởng như đúng nhưng không đủ vững khi dùng với collection Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-verification-009">Kiểm thử kế thừa, thay đổi trạng thái và độc lập sau sao chép</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử kế thừa, thay đổi trạng thái và độc lập sau sao chép

## 1. Vì sao ba nhóm kiểm thử này thuộc hợp đồng đối tượng?

Khi triển khai các phương thức của `Object` hoặc xây dựng một lớp có ngữ nghĩa giá trị, cần kiểm chứng hành vi của đối tượng **trong nhiều tình huống**, không chỉ trường hợp hai đối tượng có dữ liệu giống nhau. Ba nhóm trường hợp dễ làm lộ lỗi là:

1. **Kế thừa:** lớp con bổ sung thuộc tính hay thay đổi `equals()`/`hashCode()` có thể phá tính đối xứng, bắc cầu hoặc quan hệ giữa hai phương thức.
2. **Thay đổi trạng thái:** một đối tượng đổi thuộc tính sau khi tạo có thể thay đổi kết quả so sánh và mã băm; điều này đặc biệt nguy hiểm nếu đối tượng đang là khóa của `HashMap` hoặc phần tử của `HashSet`.
3. **Sao chép:** đối tượng mới có thể chỉ là một bản sao nông, vẫn dùng chung danh sách hoặc đối tượng con với bản gốc. Kiểm thử phải xác định rõ mức độ độc lập mà API cam kết.

Không có một hợp đồng duy nhất buộc **mọi** lớp phải so sánh theo giá trị hoặc phải sao chép sâu toàn bộ đồ thị đối tượng. Trước khi viết test, phải xác định lớp mang **identity** hay **value semantics**, các thuộc tính nào tham gia `equals()`/`hashCode()`, trường nào được phép thay đổi, và tài liệu API hứa chia sẻ hay sao chép trạng thái nào.

## 2. Những bất biến cần kiểm chứng

Với các tham chiếu không null `x`, `y`, `z`, hợp đồng của `Object.equals()` yêu cầu:

| Thuộc tính | Phép kiểm | Ý nghĩa |
| --- | --- | --- |
| Phản xạ | `x.equals(x)` là `true` | Một đối tượng bằng chính nó |
| Đối xứng | `x.equals(y) == y.equals(x)` | Đổi chiều so sánh không đổi kết quả |
| Bắc cầu | Nếu `x.equals(y)` và `y.equals(z)`, thì `x.equals(z)` | Ba đối tượng tương đương nhất quán |
| Nhất quán | Nhiều lần so sánh cho cùng kết quả khi trạng thái liên quan không đổi | Không phát sinh kết quả bất ngờ |
| Khác null | `x.equals(null)` là `false` | Không coi null là một đối tượng bình thường |

Quan hệ giữa `equals()` và `hashCode()`:

- Nếu `x.equals(y)` là `true`, **bắt buộc** `x.hashCode() == y.hashCode()`.
- Hai đối tượng không bằng nhau **vẫn có thể** có cùng hash code; không viết test yêu cầu mã băm của mọi giá trị khác nhau đều khác nhau.
- Nếu trạng thái liên quan tới `equals()` không đổi, các lần gọi `hashCode()` trong cùng một lần chạy phải nhất quán. Giá trị hash code không nhất thiết giữ nguyên giữa các lần chạy JVM khác nhau.

Cần kiểm thử cả **trường hợp phủ định** (khác kiểu, khác thuộc tính có ý nghĩa, giá trị null phù hợp). Trường hợp thành công riêng lẻ không chứng minh hợp đồng đúng với toàn bộ không gian đầu vào, nên chọn tập dữ liệu đại diện, các trường hợp biên và trường hợp kế thừa.

Với sao chép, hãy kiểm riêng các tính chất:

- **Không cùng tham chiếu:** `copy != original`.
- **Dữ liệu ban đầu đúng:** nội dung copy giống dữ liệu được hứa sao chép; nếu lớp dùng value equality thì có thể kiểm `copy.equals(original)` ban đầu.
- **Độc lập sau sửa đổi:** thay đổi thành phần mutable ở một bên không làm thay đổi bên kia **nếu** API cam kết copy độc lập. Kiểm tra cả chiều sửa original lẫn chiều sửa copy.
- **Tính toàn vẹn của quan hệ chia sẻ có chủ ý:** nếu tài liệu quy định một thành phần dùng chung, cần kiểm tra sự chia sẻ đó thay vì mặc nhiên coi là lỗi.

## 3. Kiểm thử kế thừa: bẫy đối xứng và bắc cầu

### 3.1. Lớp cha dùng `instanceof`, lớp con thêm trường có nghĩa

Giả sử `BaseKey` chỉ lưu `id` và coi mọi đối tượng thuộc `BaseKey` hoặc lớp con có cùng `id` là bằng nhau:

```java
@Override
public boolean equals(Object other) {
    return other instanceof BaseKey key && id == key.id;
}
```

Sau đó `TaggedKey extends BaseKey` thêm trường `tag`, và `equals()` của lớp con yêu cầu cả `id` và `tag` bằng nhau.

Khi đó, với `base = new BaseKey(7)` và `child = new TaggedKey(7, "red")`:

```text
base.equals(child)  → true
child.equals(base)  → false
```

Tính đối xứng đã hỏng. Nếu lớp con tính hash cả `tag`, cặp đối tượng mà lớp cha coi là bằng nhau còn có thể có mã băm khác nhau, vi phạm quy tắc hash. Chỉ kiểm `child.equals(base)` mà quên kiểm chiều ngược sẽ bỏ sót lỗi.

### 3.2. Cố sửa đối xứng nhưng làm hỏng bắc cầu

Một giải pháp thiếu an toàn là: lớp con bỏ qua `tag` khi so sánh với lớp cha, nhưng so sánh `tag` khi hai phía đều là lớp con. Xét:

- `red = TaggedKey(7, "red")`
- `base = BaseKey(7)`
- `blue = TaggedKey(7, "blue")`

Nếu hai lớp được viết để `red.equals(base)` và `base.equals(blue)` đều đúng, nhưng `red.equals(blue)` sai, hợp đồng **bắc cầu** bị phá vỡ. Một test với bộ ba này sẽ phát hiện lỗi mà kiểm thử từng cặp giống kiểu không nhận ra.

Không thể tùy tiện bổ sung thành phần giá trị ở lớp con trong khi lớp cha vẫn cho phép so sánh ngang kiểu mà luôn giữ được các quy tắc `equals()`. Cách thiết kế phù hợp tùy miền dữ liệu:

- Dùng lớp giá trị `final` để không cho lớp con thay đổi ý nghĩa equality.
- Dùng **composition** để thêm metadata, thay vì kế thừa một lớp giá trị đã có equality.
- Dùng `getClass()` để equality chỉ hoạt động giữa hai đối tượng có cùng runtime class, nếu ngữ nghĩa miền chấp nhận sự phân biệt này; khi lớp con override thì vẫn phải duy trì hợp đồng.
- Một số hierarchy dùng giao thức `canEqual` được thiết kế cẩn thận; không mặc định đổi `instanceof` sang `getClass()` là phù hợp với mọi ứng dụng.

Test cần mô tả **quyết định thiết kế về cross-type equality**: một lớp cha và lớp con cùng dữ liệu có *được phép* bằng nhau không? Nếu không, kiểm `!parent.equals(child)` và `!child.equals(parent)`. Nếu có, kiểm đủ các quy tắc và các cặp/bộ ba lớp con khác nhau, thay vì kiểm từng override độc lập.

## 4. Kiểm thử thay đổi trạng thái

### 4.1. Trạng thái tham gia equality và hash

Một lớp mutable có thể tính `equals()`/`hashCode()` từ trường thay đổi được. Việc kết quả hai hàm thay đổi theo trạng thái tự nó không nhất thiết vi phạm hợp đồng: hợp đồng nhất quán áp dụng **khi thông tin dùng trong phép so sánh không bị sửa đổi**.

Tuy nhiên, đưa một đối tượng vào `HashSet` hoặc làm khóa `HashMap` rồi sửa trường tham gia mã băm có thể khiến tìm kiếm thất bại: đối tượng được đặt theo hash cũ, trong khi lần tìm sau dùng hash mới. Với những cấu trúc này, tốt nhất dùng **khóa bất biến**.

Một kiểm thử giá trị nên diễn ra qua các mốc:

1. Tạo `a` và `b` cùng giá trị; xác nhận `a.equals(b)` và hash bằng nhau.
2. Sửa trường tham gia equality trên `a`; xác nhận phép so sánh phản ánh giá trị mới.
3. Sửa `b` sang cùng giá trị mới; xác nhận hai đối tượng lại bằng nhau và hash bằng nhau.
4. Nếu trường có thể trở lại giá trị cũ, kiểm kết quả cũng trở lại như thiết kế.
5. Nếu lớp được dùng trong hash collection, kiểm hoặc quy định **không sửa khóa sau khi chèn**, hoặc dùng một thiết kế khóa bất biến; không biến lỗi tra cứu thành kỳ vọng chức năng của ứng dụng.

Phân biệt **mutable field dùng để hiển thị** với **field tham gia logical identity**. Chỉ kiểm rằng hash thay đổi với mọi setter có thể sai: nếu một thuộc tính không thuộc equality, nó thường **không nên** làm thay đổi hash.

### 4.2. Trạng thái kế thừa

Nếu lớp con có thêm trường mutable tham gia equality, kiểm các chuyển trạng thái ở **cả trường lớp cha lẫn lớp con**, với nhiều đối tượng cùng lớp con. Đảm bảo override `hashCode()` theo đúng những thành phần `equals()` sử dụng. Không cần giả định mọi lớp con đều được phép mở rộng equality; có trường metadata không tham gia equality.

## 5. Kiểm thử tính độc lập sau sao chép

### 5.1. Sao chép nông và sao chép sâu

Với đối tượng `Profile` chứa `Address` và `List<String>` mutable:

```text
Original Profile ── address ──> Address("Hue")
                 └─ skills  ──> ["java"]

Shallow copy     ── address ──> cùng Address
                 └─ skills  ──> cùng List

Deep copy        ── address ──> Address mới ("Hue")
                 └─ skills  ──> List mới ["java"]
```

Một bản sao nông có thể tạo ra **đối tượng ngoài mới** nhưng vẫn chia sẻ tham chiếu đến các đối tượng con. Vì thế chỉ kiểm `assertNotSame(original, copy)` là chưa đủ.

Bản sao sâu không bắt buộc nhân bản mọi đối tượng bất biến như `String`, và thường không nên sao chép các resource như socket, lock hoặc thread. Mức độ sâu phụ thuộc **hợp đồng của lớp** và ranh giới ownership, đặc biệt khi đồ thị có vòng tham chiếu hoặc các phần tử được chia sẻ chủ ý.

### 5.2. Ma trận kiểm thử độc lập

| Thao tác | Bản sao nông chia sẻ tham chiếu | Bản sao sâu đúng hợp đồng |
| --- | --- | --- |
| Sửa `original.address.city` | `shallow.address.city` thay đổi theo | `deep.address.city` không đổi |
| Thêm phần tử vào `original.skills` | `shallow.skills` nhìn thấy phần tử mới | `deep.skills` không thay đổi |
| Sửa `deep.address.city` | Không liên quan | `original.address.city` không đổi |
| Thêm vào `deep.skills` | Không liên quan | `original.skills` không đổi |
| So sánh object ngoài | Bản sao khác tham chiếu với original | Bản sao khác tham chiếu với original |
| So sánh object con mutable | Có thể **cùng** tham chiếu | Phải **khác** tham chiếu nếu cam kết copy độc lập |

Nếu API dùng `clone()` theo `Object.clone()`, nhớ rằng hành vi mặc định sao chép các field và **không tự deep-copy đối tượng được tham chiếu**. Một lớp có thể cần override `clone()`, copy constructor hoặc factory để sao chép sâu có kiểm soát. Không đánh đồng việc implement `Cloneable` với bảo đảm toàn bộ trạng thái độc lập.

## 6. Ví dụ Java 17+ chạy độc lập: phát hiện lỗi và kiểm chứng hành vi

Ví dụ cố ý đặt một lớp equality bị lỗi để **test phát hiện lỗi**; các kiểm thử còn lại minh họa cách xác minh thiết kế đúng. Lưu mã thành tệp `ObjectContractVerification.java`.

```java
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ObjectContractVerification {
    // Thiết kế lỗi: lớp cha xem lớp con cùng id là bằng nhau.
    static class BaseKey {
        final int id;

        BaseKey(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof BaseKey key && id == key.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    static final class TaggedKey extends BaseKey {
        final String tag;

        TaggedKey(int id, String tag) {
            super(id);
            this.tag = tag;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TaggedKey key
                    && super.equals(key)
                    && tag.equals(key.tag);
        }

        @Override
        public int hashCode() {
            return 31 * super.hashCode() + tag.hashCode();
        }
    }

    // Thiết kế nhất quán: chỉ so sánh value object cùng runtime class.
    static class StrictKey {
        final int id;

        StrictKey(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object other) {
            return other != null
                    && getClass() == other.getClass()
                    && id == ((StrictKey) other).id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    static final class StrictTaggedKey extends StrictKey {
        final String tag;

        StrictTaggedKey(int id, String tag) {
            super(id);
            this.tag = tag;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof StrictTaggedKey key
                    && super.equals(key)
                    && tag.equals(key.tag);
        }

        @Override
        public int hashCode() {
            return 31 * super.hashCode() + tag.hashCode();
        }
    }

    static final class MutableKey {
        String code;

        MutableKey(String code) {
            this.code = code;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MutableKey key
                    && Objects.equals(code, key.code);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(code);
        }
    }

    static final class Address {
        String city;

        Address(String city) {
            this.city = city;
        }
    }

    static final class Profile {
        final String name;
        final Address address;
        final List<String> skills;

        Profile(String name, Address address, List<String> skills) {
            this.name = name;
            this.address = address;
            this.skills = skills;
        }

        Profile shallowCopy() {
            return new Profile(name, address, skills);
        }

        Profile deepCopy() {
            return new Profile(
                    name,
                    new Address(address.city),
                    new ArrayList<>(skills)
            );
        }
    }

    public static void main(String[] args) {
        BaseKey parent = new BaseKey(7);
        TaggedKey child = new TaggedKey(7, "red");
        boolean badSymmetry = parent.equals(child) == child.equals(parent);
        System.out.println("Broken inheritance symmetry valid: " + badSymmetry);

        StrictKey strictParent = new StrictKey(7);
        StrictTaggedKey s1 = new StrictTaggedKey(7, "red");
        StrictTaggedKey s2 = new StrictTaggedKey(7, "red");
        StrictTaggedKey s3 = new StrictTaggedKey(7, "red");

        boolean strictSymmetry =
                strictParent.equals(s1) == s1.equals(strictParent);
        boolean transitive =
                s1.equals(s2) && s2.equals(s3) && s1.equals(s3);
        boolean equalHash =
                s1.equals(s2) && s1.hashCode() == s2.hashCode();

        System.out.println("Strict inheritance symmetry valid: " + strictSymmetry);
        System.out.println("Strict value transitivity valid: " + transitive);
        System.out.println("Equal strict values share hash: " + equalHash);

        MutableKey first = new MutableKey("A");
        MutableKey second = new MutableKey("A");
        boolean equalBefore = first.equals(second);
        int initialHash = first.hashCode();

        Set<MutableKey> set = new HashSet<>();
        set.add(first);
        first.code = "B";

        System.out.println("Mutable values equal before: " + equalBefore);
        System.out.println("Mutable values equal after: " + first.equals(second));
        System.out.println("Mutable hash changed: "
                + (first.hashCode() != initialHash));
        System.out.println("Mutated key found in HashSet: " + set.contains(first));

        Profile original = new Profile(
                "Mai", new Address("Hue"), new ArrayList<>(List.of("java")));
        Profile shallow = original.shallowCopy();
        Profile deep = original.deepCopy();

        original.address.city = "Da Nang";
        original.skills.add("spring");

        System.out.println("Shallow shares nested state: "
                + (shallow.address.city.equals("Da Nang")
                && shallow.skills.contains("spring")));
        System.out.println("Deep preserves initial state: "
                + (deep.address.city.equals("Hue")
                && deep.skills.equals(List.of("java"))));
        System.out.println("Deep owns separate mutable references: "
                + (deep.address != original.address
                && deep.skills != original.skills));

        deep.address.city = "Ha Noi";
        deep.skills.add("testing");

        System.out.println("Reverse mutation independent: "
                + (original.address.city.equals("Da Nang")
                && original.skills.equals(List.of("java", "spring"))));
    }
}
```

Biên dịch và chạy:

```bash
javac ObjectContractVerification.java
java ObjectContractVerification
```

Đầu ra trên OpenJDK/Java 17 với mã ví dụ trên:

```text
Broken inheritance symmetry valid: false
Strict inheritance symmetry valid: true
Strict value transitivity valid: true
Equal strict values share hash: true
Mutable values equal before: true
Mutable values equal after: false
Mutable hash changed: true
Mutated key found in HashSet: false
Shallow shares nested state: true
Deep preserves initial state: true
Deep owns separate mutable references: true
Reverse mutation independent: true
```

**Diễn giải kết quả:**

- Dòng đầu là `false` **có chủ đích** để chứng minh test bắt được một lớp thiết kế sai. Không được coi đó là kết quả mong muốn của một triển khai sản xuất.
- Với `StrictKey`, lớp cha và con không bao giờ bằng nhau khác runtime class; hai đối tượng `StrictTaggedKey` có `id`/`tag` giống nhau vẫn bằng nhau và có cùng hash. Đây là **một lựa chọn thiết kế**, không phải cách duy nhất giải quyết equality trong kế thừa.
- Sau khi đổi `"A"` thành `"B"`, `MutableKey` đổi hash; trên `HashSet` của Java 17, lookup trong ví dụ này trả về `false`. Đây là minh họa nguy cơ do phá giả định về hash key ổn định, **không phải** một lời hứa rằng mọi hash collection hay mọi cặp giá trị sẽ luôn trả `false` sau mọi lần sửa.
- Bản sao nông nhìn thấy thay đổi ở `Address` và `List`, trong khi bản sao sâu giữ giá trị ban đầu và cũng không ảnh hưởng original khi được thay đổi ở chiều ngược lại.

Lưu ý ví dụ kiểm thử một số trường hợp đại diện và in kết quả để quan sát. Trong test suite thật, hãy dùng assertion để **fail ngay khi hợp đồng mong đợi bị phá**, đồng thời lưu các ca lỗi có chủ đích thành test xác minh bug thay vì test thành công của sản phẩm.

## 7. Chuyển thành kiểm thử tự động có assertion

Một helper không phụ thuộc thư viện ngoài có thể khẳng định các đặc tính quan trọng (đặt trong lớp test):

```java
static void checkEqualityContract(Object a, Object b, Object c) {
    if (!a.equals(a) || !b.equals(b) || !c.equals(c)) {
        throw new AssertionError("Reflexivity failed");
    }
    if (a.equals(null) || b.equals(null) || c.equals(null)) {
        throw new AssertionError("Null equality failed");
    }
    if (a.equals(b) != b.equals(a)
            || b.equals(c) != c.equals(b)
            || a.equals(c) != c.equals(a)) {
        throw new AssertionError("Symmetry failed");
    }
    if (a.equals(b) && b.equals(c) && !a.equals(c)) {
        throw new AssertionError("Transitivity failed");
    }
    if (a.equals(b) && a.hashCode() != b.hashCode()) {
        throw new AssertionError("Equal objects have different hashes");
    }
}
```

Đây là **đoạn helper minh họa**, không phải một chương trình Java độc lập. Nó chỉ kiểm đúng các đối tượng được truyền vào, nên vẫn cần chọn dữ liệu phản ánh cấu trúc kế thừa và các trạng thái quan trọng. Trong JUnit 5 có thể thay bằng `assertEquals`, `assertFalse`, `assertNotSame` và `assertAll`; kiểm thử equality đặc biệt nên bao gồm ba đối tượng và cả hai chiều so sánh.

Với code có `clone()` hoặc copy constructor, một test tốt thường theo dạng:

1. Tạo object chứa **ít nhất hai tầng tham chiếu mutable**, không chỉ chuỗi/primitive.
2. Sao chép và so sánh dữ liệu lúc ban đầu; phân biệt `same object` với `same value`.
3. Sửa nested state bản gốc, khẳng định bản sao vẫn đúng theo hợp đồng.
4. Sửa nested state bản sao, khẳng định bản gốc vẫn đúng.
5. Nếu có collection, sửa cả **cấu trúc** (thêm/xóa) và **phần tử mutable bên trong**, vì sao chép một `ArrayList` mới vẫn có thể dùng chung các phần tử trong danh sách.
6. Với kiểu có equality theo giá trị, thử `equals()`/`hashCode()` trước và sau các thay đổi; không so sánh hash giữa hai trạng thái khác nhau như một bất biến.

## 8. Các lỗi kiểm thử hay bỏ sót

| Thiếu sót | Hậu quả | Bổ sung |
| --- | --- | --- |
| Chỉ gọi `parent.equals(child)` | Bỏ sót đối xứng bị phá ở chiều ngược | Kiểm cả hai chiều |
| Chỉ kiểm hai đối tượng | Bỏ sót vi phạm bắc cầu qua đối tượng thứ ba | Tạo bộ ba đại diện |
| Cho rằng hai hash khác nhau khi hai object không bằng nhau | Đặt yêu cầu sai với `hashCode()` | Chỉ bắt buộc hash bằng khi `equals()` là `true` |
| Chỉ thử trạng thái ban đầu | Không bắt được lỗi khi field mutable đổi | Test chuyển trạng thái |
| Sửa hash key ngay trong `HashMap` rồi đổ lỗi collection | Bỏ qua điều kiện sử dụng khóa có hash ổn định | Đóng băng khóa hoặc không sửa sau khi chèn |
| Chỉ kiểm `copy != original` | Không phát hiện alias của nested mutable fields | Kiểm tham chiếu con và thay đổi hai chiều |
| Chỉ copy lại container danh sách | Các phần tử mutable có thể còn dùng chung | Kiểm sâu tới tầng cần độc lập |
| Bắt buộc mọi bản copy phải `equals(original)` | Sai nếu lớp định nghĩa equality theo identity | Xác nhận ngữ nghĩa equality trước |
| Coi `clone()` mặc định là deep copy | Sao chép shallow làm lộ shared state | Đọc hợp đồng và viết test tương ứng |
| Chỉ test một subclass | Khó phát hiện cross-type equality sai khi hierarchy mở rộng | Test nhiều kiểu runtime và cặp/triple tương ứng |

## 9. Tiêu chí hoàn thành

Một bộ kiểm chứng đáng tin cho hợp đồng đối tượng cần trả lời được:

- **Kế thừa:** Đối tượng lớp cha/lớp con có được bằng nhau không, và hai chiều so sánh có thống nhất không? Bộ ba đối tượng có bảo đảm tính bắc cầu? Hash có đồng nhất khi equals đúng?
- **Trạng thái:** Field nào tạo nên logical identity? Setter nào được phép làm thay đổi equality/hash? Có đang dùng một key mutable trong hash collection không?
- **Sao chép:** Bản sao nông hay sâu theo hợp đồng? Mức nào của đồ thị mutable cần độc lập? Thay đổi ở **cả hai chiều** có tác động ngoài ý muốn không?

Từ các câu trả lời đó, hãy xây test dùng assertion và dữ liệu đại diện cho từng quyết định thiết kế. **Không suy ra tính đúng từ một phép so sánh thành công hay một bản sao có tham chiếu ngoài khác nhau**: chính các tình huống kế thừa, đổi trạng thái và chia sẻ tham chiếu mới thường phơi bày lỗ hổng trong hợp đồng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-verification-010">Phân biệt kiểm thử tính đúng với đánh giá phân bố hoặc hiệu năng băm</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt kiểm thử tính đúng với đánh giá phân bố hoặc hiệu năng băm

Khi viết `equals()` và `hashCode()` trong Java, có hai câu hỏi khác nhau:

1. **Tính đúng:** hai đối tượng được coi là bằng nhau có tuân thủ hợp đồng của `equals()` và `hashCode()` không? `HashSet`/`HashMap` có tra cứu được key tương đương đúng cách không?
2. **Phân bố và hiệu năng:** mã băm có trải các key của một tập dữ liệu thực tế tương đối đều không? Số va chạm, thời gian tra cứu/chèn/xóa, chi phí tính hash và bộ nhớ ra sao?

**Một hàm băm hợp lệ chưa chắc phân bố tốt; một hàm băm có phân bố trông đẹp chưa chắc đúng hợp đồng.** Kiểm thử tính đúng là điều kiện bắt buộc trước khi tối ưu hiệu năng.

## 1. Hai loại đánh giá đo những thứ khác nhau

| Khía cạnh | Kiểm thử tính đúng (correctness) | Đánh giá phân bố/hiệu năng |
| --- | --- | --- |
| Câu hỏi | Quan hệ bằng nhau và hash có tuân thủ hợp đồng Java? | Hash trải key ra sao; các thao tác trên collection có nhanh không? |
| Đơn vị kiểm tra | Đối tượng, cặp/nhóm đối tượng, hành vi tra cứu theo key | Tập dữ liệu đủ lớn, nhóm bucket mô phỏng, workload đo thời gian |
| Kết quả | PASS/FAIL, có phản ví dụ rõ ràng | Số liệu định lượng phụ thuộc dữ liệu, kích thước và môi trường |
| Tiêu chí cốt lõi | `a.equals(b) ⇒ a.hashCode() == b.hashCode()` | Tỷ lệ bucket trống, tải mỗi bucket, va chạm, throughput, độ trễ |
| Va chạm hash | **Được phép** với hai đối tượng không bằng nhau | Nhiều va chạm có thể làm giảm hiệu năng |
| Công cụ | Unit/property-based tests, assertion, `HashSet`/`HashMap` | Phân tích histogram; benchmark có warmup và kiểm soát workload, thường bằng JMH |
| Lỗi điển hình | Key bằng nhau nhưng có hash khác nhau; `equals()` bất đối xứng | Tất cả key vào một bucket; tạo hash quá tốn CPU |

Một giá trị `hashCode()` là `int` 32 bit nên không thể có mã riêng cho mọi đối tượng có thể tồn tại. Va chạm là bình thường. Hợp đồng không yêu cầu hai key khác nhau phải sinh hai mã băm khác nhau.

## 2. Hợp đồng nào phải được kiểm thử?

### 2.1. Hợp đồng `equals()`

Với những tham chiếu không `null`, `equals()` phải thể hiện quan hệ tương đương:

- **Phản xạ:** `x.equals(x)` là `true`.
- **Đối xứng:** `x.equals(y)` và `y.equals(x)` có cùng kết quả.
- **Bắc cầu:** nếu `x.equals(y)` và `y.equals(z)` đều đúng thì `x.equals(z)` phải đúng.
- **Nhất quán:** các lời gọi lặp lại cho cùng hai đối tượng phải cho cùng kết quả khi thông tin dùng để so sánh không thay đổi.
- **Với null:** `x.equals(null)` là `false`.

Đối với một kiểu dữ liệu theo giá trị, cần quyết định rõ **trường nào xác định bằng nhau**. Ví dụ, với `UserKey(tenantId, userId)`, nếu cả hai trường cùng tham gia `equals()`, thì `hashCode()` phải tương thích với chính tiêu chí đó.

### 2.2. Hợp đồng `hashCode()`

- **Điều kiện bắt buộc:** nếu `x.equals(y)` thì `x.hashCode() == y.hashCode()`.
- **Nhất quán theo trạng thái:** khi các thuộc tính dùng trong `equals()` chưa thay đổi, các lần gọi `hashCode()` trong một lần chạy Java phải cho cùng giá trị.
- **Không yêu cầu chiều ngược lại:** `x.hashCode() == y.hashCode()` **không** suy ra `x.equals(y)`. Hai đối tượng không bằng nhau được phép va chạm.

Viết gọn quan hệ cần kiểm tra:

```text
equals(x, y) == true  =>  hash(x) == hash(y)      BẮT BUỘC
hash(x) == hash(y)    =>  equals(x, y) == true    KHÔNG ĐÚNG TỔNG QUÁT
equals(x, y) == false =>  hash(x) != hash(y)      KHÔNG BẮT BUỘC
```

Nếu override `equals()` để thay đổi khái niệm bằng nhau thì thông thường cũng phải override `hashCode()` tương ứng. Không viết một bài test sai như `assertNotEquals(a.hashCode(), b.hashCode())` chỉ vì `a` và `b` khác nhau: phép kiểm này không nằm trong hợp đồng.

### 2.3. Kiểm chứng ngữ nghĩa trong collection

Đối với hai instance khác nhau nhưng bằng nhau theo `equals()`, cần thử ít nhất:

- Thêm `a` vào `HashSet`, sau đó `contains(b)` phải đúng, và `add(b)` không làm số phần tử tăng.
- Gán `map.put(a, value)` thì `map.get(b)` phải trả đúng value; gán tiếp một key tương đương phải cập nhật entry tương ứng.
- Một key không tương đương không được bị coi nhầm là cùng key do `equals()` quá rộng. **Va chạm hash tự nó không làm `HashMap` gộp hai key không bằng nhau**: collection còn đối chiếu bằng `equals()`.

Những kiểm thử này bổ sung cho kiểm tra hợp đồng trực tiếp. Chúng không thay thế việc xét đối xứng, bắc cầu và các biên kiểu dữ liệu.

## 3. Ví dụ Java 17+ chạy được: đúng hợp đồng nhưng phân bố rất kém

Chương trình bên dưới có ba kiểu key:

- `GoodKey`: so sánh theo `id`; hash theo `id`. Tập ID liên tiếp trong ví dụ trải đều trên mô hình 16 bucket.
- `ConstantHashKey`: cũng so sánh theo `id`, nhưng mọi instance đều có hash bằng 7. **Hợp đồng vẫn đúng**, song tất cả phần tử va chạm.
- `BrokenKey`: so sánh chỉ theo `id`, trong khi hash trả về `salt`; hai key bằng nhau có thể mang hash khác nhau. **Vi phạm hợp đồng**, bất kể phân bố có vẻ tốt đến đâu.

Lưu thành tệp **`HashCorrectnessVsDistribution.java`**:

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HashCorrectnessVsDistribution {
    record GoodKey(int id) {
        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    record ConstantHashKey(int id) {
        @Override
        public int hashCode() {
            return 7;
        }
    }

    static final class BrokenKey {
        final int id;
        final int salt;

        BrokenKey(int id, int salt) {
            this.id = id;
            this.salt = salt;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof BrokenKey key && id == key.id;
        }

        @Override
        public int hashCode() {
            return salt; // SAI: salt không tham gia equals().
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static int occupied(int[] counts) {
        int result = 0;
        for (int count : counts) {
            if (count > 0) {
                result++;
            }
        }
        return result;
    }

    static int maxLoad(int[] counts) {
        int max = 0;
        for (int count : counts) {
            max = Math.max(max, count);
        }
        return max;
    }

    static void checkGoodKeyContract() {
        GoodKey a = new GoodKey(42);
        GoodKey b = new GoodKey(42);
        GoodKey c = new GoodKey(42);
        GoodKey distinct = new GoodKey(99);

        check(a.equals(a), "reflexive");
        check(a.equals(b) && b.equals(a), "symmetric");
        check(a.equals(b) && b.equals(c) && a.equals(c),
                "transitive");
        check(!a.equals(null), "null");
        check(!a.equals(distinct), "different ids");
        check(a.hashCode() == b.hashCode(), "hash agreement");

        Set<GoodKey> set = new HashSet<>();
        check(set.add(a), "first insert");
        check(set.contains(b), "lookup equal key");
        check(!set.add(b) && set.size() == 1, "no duplicate");

        Map<GoodKey, String> map = new HashMap<>();
        map.put(a, "found");
        check("found".equals(map.get(b)), "map lookup");
        System.out.println("GoodKey contract: PASS");
    }

    static void checkConstantHashContract() {
        ConstantHashKey a = new ConstantHashKey(42);
        ConstantHashKey b = new ConstantHashKey(42);
        check(a.equals(b), "equal ids");
        check(a.hashCode() == b.hashCode(), "same hash");

        Set<ConstantHashKey> set = new HashSet<>();
        for (int i = 0; i < 64; i++) {
            check(set.add(new ConstantHashKey(i)), "distinct ids");
        }
        check(set.size() == 64, "collision is not equality");
        check(set.contains(new ConstantHashKey(42)),
                "lookup despite collisions");
        System.out.println("ConstantHashKey contract: PASS");
    }

    static void showBrokenContract() {
        BrokenKey a = new BrokenKey(42, 1);
        BrokenKey b = new BrokenKey(42, 2);

        check(a.equals(b) && b.equals(a), "equal ids");
        check(a.hashCode() != b.hashCode(), "different salts");
        System.out.println(
                "BrokenKey: equal=true, hashesEqual=false -> VIOLATION");
    }

    static void showBucketModel() {
        int[] good = new int[16];
        int[] constant = new int[16];

        for (int id = 0; id < 64; id++) {
            int h1 = new GoodKey(id).hashCode();
            int h2 = new ConstantHashKey(id).hashCode();
            good[Math.floorMod(h1, good.length)]++;
            constant[Math.floorMod(h2, constant.length)]++;
        }

        System.out.println("Model 64 keys / 16 buckets:");
        System.out.println("GoodKey used=" + occupied(good)
                + ", max=" + maxLoad(good));
        System.out.println("ConstantHashKey used=" + occupied(constant)
                + ", max=" + maxLoad(constant));
    }

    public static void main(String[] args) {
        checkGoodKeyContract();
        checkConstantHashContract();
        showBrokenContract();
        showBucketModel();
    }
}
```

Lệnh chạy:

```bash
javac HashCorrectnessVsDistribution.java
java HashCorrectnessVsDistribution
```

Kết quả:

```text
GoodKey contract: PASS
ConstantHashKey contract: PASS
BrokenKey: equal=true, hashesEqual=false -> VIOLATION
Model 64 keys / 16 buckets:
GoodKey used=16, max=4
ConstantHashKey used=1, max=64
```

**Vì sao đây là ví dụ đáng chú ý?**

1. `ConstantHashKey` cho kết quả PASS: 64 key khác nhau vẫn được lưu riêng trong `HashSet`, và có thể tìm theo một instance key tương đương vừa tạo mới. Collisions làm collection phải phân biệt key bằng `equals()`, nên không tự gây sai kết quả.
2. Trên **mô hình minh họa** 16 bucket, `GoodKey` với id 0–63 lấp 16 bucket, mỗi bucket 4 phần tử. `ConstantHashKey` dồn cả 64 phần tử vào một bucket. Đây là sự khác biệt về phân bố, không phải hợp đồng.
3. `BrokenKey(42,1)` và `BrokenKey(42,2)` bằng nhau theo `equals()` nhưng có hash lần lượt bằng 1 và 2, nên sai điều kiện bắt buộc. Không cần đo thời gian hoặc dựa vào hành vi ngẫu nhiên của `HashMap` để chứng minh vi phạm.
4. Phép `Math.floorMod(hash,16)` ở đây là một **mô hình chia bucket đơn giản**, **không phải mô tả chính xác cách `HashMap` Java 17 trộn bit, tính chỉ số bucket, resize hoặc xử lý collision**. Không dùng các con số của mô hình làm benchmark `HashMap`.

Bởi vậy, một báo cáo kiểm thử có thể ghi đồng thời: “hợp đồng PASS, chất lượng phân bố trên workload này kém”. Hai kết luận không mâu thuẫn.

## 4. Thiết kế bộ kiểm thử correctness có sức phát hiện lỗi

### 4.1. Chọn dữ liệu có chủ đích

Đừng chỉ tạo 100 đối tượng ngẫu nhiên rồi xác nhận không có exception. Hãy chuẩn bị nhóm đối tượng đúng theo **ngữ nghĩa bình đẳng** mà lớp khai báo:

| Tình huống | Kết quả mong đợi |
| --- | --- |
| Cùng một reference `a` | `a.equals(a)` đúng |
| Hai instance độc lập nhưng cùng tập trường định danh | `equals()` đúng và hash giống nhau |
| Khác đúng một trường **có** tham gia `equals()` | Thường `equals()` sai theo quy tắc lớp |
| Khác trường **không** tham gia `equals()` | `equals()` không được thay đổi chỉ vì trường này |
| Đối tượng khác kiểu, lớp cha/con nếu có kế thừa | Không phá đối xứng hoặc bắc cầu |
| Giá trị `null`, rỗng, Unicode, số âm, biên số nguyên | Không sai hành vi hoặc ném lỗi ngoài dự kiến |
| Tạo nhiều instance có cùng dữ liệu, rồi sử dụng làm key | Set/map vẫn tìm được key tương đương |
| Các key khác nhau nhưng hash trùng | Set/map vẫn giữ chúng là các key riêng nếu không `equals()` |

Nếu `equals()` phụ thuộc mảng, chú ý `array.equals(otherArray)` mặc định dùng danh tính mảng, còn `Arrays.equals`/`Arrays.deepEquals` so sánh nội dung theo cách riêng. Quy tắc so sánh và băm phải tương thích, ví dụ `Arrays.equals` đi cùng `Arrays.hashCode` với mảng một chiều thích hợp.

Nếu key sử dụng `double`, `BigDecimal`, hoặc lớp có quy tắc bằng nhau đặc biệt, hãy thử NaN, giá trị `-0.0`/`0.0`, hay scale khác nhau khi chúng có liên quan đến tiêu chí equals của kiểu cụ thể. Không giả định tất cả kiểu số có chung một định nghĩa “bằng nhau”.

### 4.2. Property-based test và quy tắc khi sinh dữ liệu

Với một bộ sinh đối tượng, có thể kiểm tra nhiều bộ ba `(a,b,c)` để tìm phản ví dụ về đối xứng/bắc cầu. Tuy nhiên, random thuần thường hiếm khi tạo ra hai instance bằng nhau. Cần **chủ động sao chép cùng khóa ngữ nghĩa**, sinh biến thể khác một trường định danh, và sinh biến thể khác trường không tham gia `equals()`.

Pseudo-code kiểm tra cặp:

```text
for each pair (a, b):
    assert a.equals(b) == b.equals(a)
    if a.equals(b):
        assert a.hashCode() == b.hashCode()
    // Không assert hash khác nhau khi equals false.

for each triple (a, b, c):
    if a.equals(b) and b.equals(c):
        assert a.equals(c)
```

Thêm kiểm tra `equals(null)`, phản xạ, việc gọi lặp lại khi trạng thái không đổi, và hành vi thực tế của map/set. Nếu sinh nhiều dữ liệu, hãy lưu seed và phản ví dụ tối giản để lỗi tái hiện được.

**Giới hạn bằng chứng:** test chạy hết các trường hợp đã chọn chỉ chứng minh hành vi trong miền kiểm thử. Nó không chứng minh tuyệt đối mọi quan hệ bằng nhau hợp lệ với mọi giá trị có thể có. Sự chính xác của định nghĩa `equals()` vẫn cần được xem xét ở mức thiết kế.

### 4.3. Bẫy key mutable

Ngay cả khi `equals()`/`hashCode()` được viết nhất quán ở từng thời điểm, thay đổi trường tham gia phép bằng nhau sau khi đưa instance vào `HashSet` hay làm key `HashMap` có thể khiến việc tìm kiếm thất bại: key đã nằm ở vị trí dựa vào hash cũ. Bài test nên bảo đảm một trong hai quy tắc thiết kế:

- Ưu tiên key bất biến, chẳng hạn `record` với các thành phần phù hợp và không để lộ trạng thái mutable ảnh hưởng tới phép so sánh.
- Hoặc bảo đảm ứng dụng không sửa trạng thái tham gia equality/hash trong suốt thời gian key đang nằm trong cấu trúc băm; muốn đổi key thì xóa rồi thêm lại một cách có kiểm soát.

Với một `record`, các thành phần reference tới danh sách/mảng có thể vẫn trỏ tới dữ liệu mutable. `record` không tự động làm toàn bộ đồ thị đối tượng bên trong trở nên bất biến.

## 5. Đánh giá phân bố băm: đo đúng đối tượng và dữ liệu

Sau khi correctness PASS, đánh giá chất lượng hash trên **tập key thực sự dự kiến sử dụng**. Một hàm hash có phân bố tốt trên các ID liên tiếp có thể không tốt trên các ID có cấu trúc đặc biệt, chẳng hạn phần thấp luôn bằng 0; tương tự một benchmark trên vài chục key không đại diện cho hệ thống hàng triệu key.

Có thể làm một thí nghiệm bucket mô phỏng theo các bước:

1. Tạo dữ liệu đầu vào có ý nghĩa: ID liên tiếp, ID tăng theo bước cố định, chuỗi với tiền tố chung, dữ liệu ngẫu nhiên được lưu seed, và phân bố key gần giống thực tế.
2. Chọn số bucket cho mô hình và quy tắc ánh xạ `hash → bucket`, công bố rõ quy tắc ấy. Phân biệt mô hình với nội bộ `HashMap`.
3. Đếm số phần tử trong mỗi bucket; tính số bucket có ít nhất một key, tải trung bình `N / B`, tải lớn nhất và histogram tải bucket.
4. Tính số va chạm **theo định nghĩa đang dùng**, chẳng hạn số phần tử vượt phần tử đầu trong mỗi bucket `sum(max(0, bucketSize - 1))`. Không đánh đồng “mã hash trùng nhau” với “rơi cùng bucket sau ánh xạ”.
5. Lặp lại ở nhiều kích thước và kiểu dữ liệu; so sánh với cùng một tập key, cùng cách ánh xạ và cùng số bucket.

Ví dụ khi có 64 key, 16 bucket:

- `GoodKey` trong chương trình: 16 bucket có dữ liệu, tải trung bình 4, tải lớn nhất 4; số phần tử vượt vị trí đầu theo định nghĩa trên là `64 - 16 = 48`.
- `ConstantHashKey`: 1 bucket có dữ liệu, tải trung bình trên toàn bộ 16 bucket vẫn là 4 nhưng tải lớn nhất 64; số phần tử vượt vị trí đầu là `64 - 1 = 63`.

**Tải trung bình giống nhau không đồng nghĩa phân bố giống nhau.** Cần xem histogram/độ lệch/tải cực đại. Một mã hash `int` phân bố khá đều vẫn có collision bucket vì số bucket nhỏ hơn không gian hash và số phần tử.

Nếu muốn kiểm tra `HashMap`/ `HashSet` thực tế, chỉ số bucket đơn giản không đủ. Cấu trúc bảng, dung lượng, hệ số tải, phép trộn bit và xử lý va chạm phụ thuộc triển khai và phiên bản JDK. Không truy cập các chi tiết nội bộ chưa được bảo đảm rồi coi đó là hợp đồng API Java.

## 6. Đo hiệu năng collection: bài toán khác với phân bố

Một histogram tốt có thể **gợi ý** tốc độ tra cứu tốt hơn, nhưng không tự chứng minh nó. Chi phí thực tế còn có tính toán `hashCode()`, `equals()`, cấp phát, locality của bộ nhớ, kích thước bảng, thao tác insert/get/remove, GC, JIT và đặc tính dữ liệu.

Đối với `HashMap` hoặc `HashSet`, hãy xác định:

- **Workload:** chèn bao nhiêu key, tỷ lệ đọc/ghi, tỷ lệ tra cứu thành công/thất bại, số phần tử đồng thời và sự phân bố các key truy cập.
- **Đại lượng:** throughput (thao tác/giây), độ trễ mỗi thao tác, phân vị như p50/p95/p99 nếu có ý nghĩa, cùng cấp phát/GC khi cần.
- **Thiết lập:** JDK và cấu hình JVM, máy, kích thước heap, khả năng chạy cạnh các tiến trình khác, chế độ warmup, nhiều lượt/fork và khoảng biến thiên kết quả.
- **So sánh công bằng:** giữ cùng tập key, cùng semantics của `equals()`, cùng số lượng phần tử và trình tự thao tác. Nếu một phương án cho kết quả sai hợp đồng, loại phương án đó khỏi so sánh tối ưu.
- **Benchmark đáng tin:** ưu tiên JMH, thiết kế `@State`, `@Setup`, `@Benchmark` và các chế độ đo thích hợp. Tránh kết luận bằng một phép `System.nanoTime()` bao quanh một vòng lặp duy nhất.

Ví dụ một phép đo bị nhiễu là tạo `new HashMap<>()`, cấp phát hàng nghìn key, chèn key và đo tổng thời gian rồi gọi đó là “thời gian hashCode”. Nó đang trộn cả cấp phát, resize, duyệt collection và chạy bytecode/JIT; kết quả không trả lời đúng câu hỏi.

Với Java `HashMap`, trong điều kiện băm và tải phù hợp, get/put thường có thời gian kỳ vọng gần hằng số trên mỗi thao tác; va chạm nghiêm trọng có thể làm chậm đáng kể. Hành vi cụ thể của một bucket va chạm còn tùy key, thứ tự chèn, kích thước và chi tiết triển khai, nên không khẳng định một hệ số hoặc thời gian cụ thể khi chưa đo.

Một hàm hash tốn nhiều phép toán hơn nhưng phân bố tốt hơn có thể nhanh hơn hoặc chậm hơn tổng thể, tùy tần suất tra cứu và kích thước collection. **Chỉ số phân bố và benchmark phải được đọc cùng với nhau**, chứ không dùng một chỉ số thay tất cả.

## 7. Thứ tự kiểm chứng được khuyến nghị

```text
Định nghĩa semantics của key
             |
             v
Kiểm thử equals + hashCode contract
             |
       FAIL? +-- Có --> Sửa định nghĩa/triển khai rồi kiểm lại
             |
            Không
             v
Kiểm thử hành vi HashSet / HashMap
             |
             v
Phân tích phân bố trên dữ liệu đại diện
             |
             v
Đo JMH trên workload quan trọng
             |
             v
Đánh giá trade-off: độ đúng, tốc độ, bộ nhớ, độ phức tạp
```

Khi xem kết quả, diễn giải từng tình huống:

| Correctness | Phân bố/hiệu năng | Hành động |
| --- | --- | --- |
| FAIL | Bất kỳ | Sửa hợp đồng trước. Benchmark không thể hợp thức hóa key sai. |
| PASS | Tốt với workload mục tiêu | Giữ thiết kế, theo dõi khi workload thay đổi. |
| PASS | Kém trên workload thực tế | Thử hash khác nhưng phải giữ cùng semantics `equals()` và chạy lại toàn bộ correctness tests. |
| PASS | Chưa đo | Chưa đủ bằng chứng để tuyên bố hash nhanh/chậm; chỉ kết luận đúng hợp đồng trên dữ liệu kiểm thử. |

Không tối ưu để “mọi đối tượng đều có hash khác nhau”: điều này không phải yêu cầu của Java, và không khả thi cho miền key lớn hơn số lượng giá trị `int`. Mục tiêu có ý nghĩa là **đảm bảo đúng hợp đồng** và sau đó đạt hiệu năng phù hợp với dữ liệu, workload thực tế.

## 8. Những sai lầm thường gặp

| Sai lầm | Vì sao sai | Cách làm đúng |
| --- | --- | --- |
| Assert hai key không bằng nhau phải có hash khác nhau | Hash collision là hợp lệ | Chỉ bắt buộc hai key bằng nhau có hash bằng nhau |
| Kiểm tra histogram đẹp rồi kết luận `equals()` đúng | Phân bố không xác thực phản xạ/đối xứng/bắc cầu | Viết test hợp đồng độc lập |
| `hashCode()` luôn trả cùng hằng số nên gọi là bug chức năng | Nó có thể hoàn toàn đúng hợp đồng | Gắn nhãn rủi ro hiệu năng, kiểm tra workload |
| Mỗi lần `hashCode()` trả random để có vẻ “đều” | Không ổn định, phá hợp đồng và lookup | Hash phải ổn định khi trạng thái bằng nhau không đổi |
| Dùng key mutable rồi sửa trường định danh khi ở trong map | Tra cứu có thể thất bại dù code có vẻ đúng trước đó | Key bất biến hoặc remove/reinsert đúng quy trình |
| Benchmark một lần không warmup | JIT, GC, cấp phát và nhiễu hệ thống chi phối | Dùng JMH, thiết lập và lặp lại hợp lý |
| Đo bucket modulo rồi khẳng định số liệu nội bộ `HashMap` | Mô hình không phản ánh đầy đủ implementation | Ghi rõ đó là histogram minh họa |
| Tối ưu hash rồi bỏ qua test equality | Có thể làm mất tương thích giữa `equals` và hash | Chạy lại correctness suite sau mỗi thay đổi |
| Xem throughput một mình là mọi thứ | Có thể che độ trễ xấu, bộ nhớ hoặc GC | Đo chỉ số phù hợp mục tiêu sử dụng |

## 9. Checklist trước khi kết luận

1. Đã viết rõ **khóa nghiệp vụ** và trường nào thuộc `equals()`?
2. Đã kiểm tra phản xạ, đối xứng, bắc cầu, null, consistency, các trường hợp biên và cặp key bằng nhau theo giá trị nhưng là hai instance khác nhau?
3. Đã kiểm tra `equals ⇒ same hash`, đồng thời **không bắt** các key khác nhau phải có hash khác nhau?
4. Đã xác nhận `HashSet`/`HashMap` nhận diện và truy xuất đúng một key tương đương, kể cả khi có collision?
5. Đã xử lý nguy cơ sửa key sau khi đưa vào collection?
6. Nếu nói về chất lượng phân bố: đã xác định rõ dataset, mô hình bucket, metric và sự khác biệt với nội bộ `HashMap`?
7. Nếu nói về hiệu năng: đã có benchmark đáng tin, workload đại diện, số lần đo, môi trường, warmup và kết quả so sánh công bằng?

**Ghi nhớ:** kiểm thử `equals()`/`hashCode()` trả lời **“có đúng hay không?”**; phân tích phân bố và benchmark trả lời **“tốt đến mức nào trên dữ liệu và môi trường này?”**. Việc một hàm băm có nhiều va chạm chưa tự làm sai hợp đồng, còn một hàm băm nhìn có vẻ phân bố tốt không thể bù đắp quan hệ bằng nhau bị định nghĩa sai.


</details>

- [Quay lại đầu trang](#back-to-top)
