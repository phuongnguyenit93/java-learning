<a id="back-to-top"></a>

# Ngoại lệ và hợp đồng

## Menu
- [Điều kiện trước, điều kiện sau và bất biến của chương trình](#aspect-exception-contracts-001)
- [Bảo toàn kiểu trả về và ý nghĩa giá trị trả về](#aspect-exception-contracts-002)
- [Giữ nguyên hoặc chuyển đổi ngoại lệ có chủ đích](#aspect-exception-contracts-003)
- [Rủi ro nuốt lỗi và che khuất nguyên nhân gốc](#aspect-exception-contracts-004)
- [Dọn dẹp tài nguyên khi advice hoặc mã gốc thất bại](#aspect-exception-contracts-005)
- [Tác dụng phụ và tính nguyên tử của thao tác](#aspect-exception-contracts-006)
- [Khả năng thay thế của đối tượng sau khi áp dụng aspect](#aspect-exception-contracts-007)
- [Quy định hành vi khi chức năng cắt ngang không khả dụng](#aspect-exception-contracts-008)
- [Ghi nhận hợp đồng của aspect cho bên sử dụng](#aspect-exception-contracts-009)

## <a id="aspect-exception-contracts-001">Điều kiện trước, điều kiện sau và bất biến của chương trình</a>

<details>
<summary>Xem chi tiết</summary>

# Điều kiện trước, điều kiện sau và bất biến của chương trình trong AOP

## 1. Mục tiêu bài học

**Design by Contract (DbC)** xem một thao tác như một thỏa thuận giữa **bên gọi** và **bên cung cấp**:

- **Điều kiện trước (precondition):** những gì phải đúng trước khi thao tác bắt đầu trong phạm vi hợp đồng.
- **Điều kiện sau (postcondition):** những gì được bảo đảm khi thao tác kết thúc theo đường kết quả mà hợp đồng mô tả.
- **Bất biến (invariant):** tính chất cần được duy trì tại các điểm quan sát hợp lệ trong vòng đời của đối tượng/hệ thống.

Trong **Aspect-Oriented Programming (AOP)**, **aspect** cho phép gắn logic xuyên suốt vào các **join point** được **pointcut** lựa chọn; **advice** có thể quan sát hoặc thay đổi đường vào, đường ra và đường lỗi. Vì thế AOP vừa là một công cụ để **kiểm tra hợp đồng một cách tập trung**, vừa có thể là **nguồn phá vỡ hợp đồng** khi advice đổi tham số, kết quả, ngoại lệ hoặc trạng thái.

Ví dụ: một thao tác cộng số dư có hợp đồng:

```text
Pre:  delta > 0
Post: newBalance == oldBalance + delta
Inv:  balance >= 0
```

Nếu một around advice trả `oldBalance` thay vì kết quả mới mà không gọi phần gốc, caller có thể nhận một giá trị đúng kiểu Java nhưng **sai hậu điều kiện**. Nếu after advice ném lỗi sau khi nghiệp vụ đã cập nhật số dư, caller nhận exception nhưng trạng thái vẫn có thể đã thay đổi.

Bài tập trung vào **AOP nói chung**, không phụ thuộc một framework cụ thể. Cú pháp kiểu `around/proceed` ở các phần khái niệm là **mã giả**; khả năng join point, thứ tự advice, ngoại lệ và weaving phải đối chiếu với công cụ thực tế.

Kết thúc bài, người học cần:

1. Phân biệt precondition, postcondition và invariant với input validation hoặc monitoring đơn thuần.
2. Biết điểm kiểm tra hợp đồng khi có before, after returning, after throwing và around advice.
3. Nhận ra cách một advice có thể làm hợp đồng mạnh hơn, yếu đi hoặc sai lệch.
4. Thiết kế hợp đồng cho nhánh thành công **và** nhánh ngoại lệ.
5. Hiểu giới hạn proxy, weaving, lời gọi nội bộ và dữ liệu bất đồng bộ.
6. Viết được kiểm thử phát hiện vi phạm thay vì chỉ xác nhận method trả đúng kiểu.
7. Dùng Java 17 để tái hiện đầy đủ các đường hợp đồng bình thường và bất thường.

## 2. Hợp đồng phương thức dưới dạng bộ ba Hoare

Một mô hình kinh điển là:

```text
{ P } Operation { Q }
```

Trong đó:

- `P` là điều kiện trước.
- `Operation` là thao tác đang xét.
- `Q` là điều kiện sau của **đường kết thúc được giả thiết**, thường là kết thúc bình thường nếu không định nghĩa riêng nhánh exception.

Ý nghĩa cơ bản: **nếu `P` đúng trước khi chạy, và thao tác kết thúc bình thường theo các giả thiết phù hợp, thì `Q` phải đúng sau thao tác**.

Ký hiệu này **không tự bảo đảm** chương trình sẽ kết thúc, không tự ngụ ý rollback khi ném exception và không bảo đảm mọi side effect bên ngoài đều được hoàn tác.

### 2.1. Ví dụ trạng thái

Một đối tượng `Counter` có giá trị `balance`:

```text
pre_add(delta): delta > 0
post_add(old, delta, result, current):
    result == old + delta
    AND current == result
invariant: current >= 0
```

Điều kiện trước ràng buộc **trách nhiệm caller**. Hậu điều kiện và bất biến công khai ràng buộc **trách nhiệm implementation** khi caller đã đáp ứng tiền điều kiện.

Với số nguyên hữu hạn của Java, còn phải định nghĩa **chính sách tràn số**. Nếu dùng `int`, phép cộng có thể overflow; một thiết kế production có thể dùng `Math.addExact` hoặc ràng buộc đầu vào sao cho không vượt miền giá trị. Ví dụ chạy bên dưới dùng số nhỏ để không chạm tình huống ấy, nhưng không nên coi đây là chi tiết có thể bỏ qua khi thiết kế hợp đồng thật.

### 2.2. Trách nhiệm khi tiền điều kiện sai

Nếu `delta <= 0`, hợp đồng trên không cam kết thực hiện phép cộng thành công. API có thể chọn ném `IllegalArgumentException` hay một lỗi riêng, nhưng phải **nêu rõ trong hợp đồng**, thay vì để caller đoán.

Không nên đồng nhất kiểm tra precondition với một cơ chế bảo vệ hoàn chỉnh. Với một bất biến bảo mật quan trọng, việc xác thực tại ranh giới tin cậy vẫn cần thiết, kể cả khi một caller “được kỳ vọng” cung cấp dữ liệu hợp lệ.

## 3. Phân biệt ba loại khẳng định

| Tiêu chí | Precondition | Postcondition | Invariant |
| --- | --- | --- | --- |
| Được kiểm tra khi nào? | Trước khi vào thao tác được bảo vệ | Sau khi thao tác hoàn tất theo nhánh được mô tả | Ở các ranh giới trạng thái quan sát được phù hợp |
| Dựa trên dữ liệu gì? | Tham số, quyền, trạng thái ban đầu | Kết quả, trạng thái mới, trạng thái cũ | Trạng thái tổng quát của đối tượng/hệ thống |
| Ai chịu trách nhiệm chính? | Caller đáp ứng hợp đồng | Implementation/advice có trách nhiệm bảo đảm | Thành phần quản lý vòng đời và các phép biến đổi |
| Ví dụ | Số tiền dương | Số dư mới đúng bằng số dư cũ cộng tiền | Số dư không âm |
| Vi phạm thường gây gì? | Từ chối lời gọi/lỗi đầu vào | Lỗi correctness sau một thao tác | Phát hiện object đang ở trạng thái không hợp lệ |
| Điều gì không suy ra được? | Không xác nhận thao tác đã chạy | Không bảo đảm rollback nếu sai | Không nhất thiết đúng tại mọi instruction nội bộ |

**Bất biến không có nghĩa mọi trạng thái trung gian đều hợp lệ.** Một thuật toán có thể tạm thay đổi nhiều field trong một vùng xử lý được đồng bộ hóa rồi khôi phục tính nhất quán trước khi công bố kết quả. Ranh giới kiểm tra invariant phải xác định trước, không tự áp đặt kiểm tra tại từng dòng nguồn.

## 4. Vì sao AOP hữu ích khi kiểm tra hợp đồng?

Một số hợp đồng xuất hiện lặp lại ở nhiều thao tác:

- Tham số không null, nằm trong miền hợp lệ.
- Đối tượng đang ở một trạng thái vòng đời cho phép thực hiện operation.
- Kết quả không null với nhóm API cam kết không null.
- Thời lượng hoặc số lần gọi nằm trong một giới hạn chính sách được xác định.
- Sau một thao tác, invariant của một tập object còn đúng.
- Khi kết thúc bằng exception, trạng thái được giữ trong tập trạng thái hợp lệ cho phép.

Một aspect có thể đặt các kiểm tra này ở những join point phù hợp mà không lặp lại từng khối mã trong mọi method.

Tuy nhiên, **điểm chọn sai hoặc thứ tự sai** có thể biến kiểm tra hợp đồng thành lỗi khó bảo trì:

- Pointcut quá rộng kiểm tra method không thuộc hợp đồng.
- Before advice nhìn dữ liệu đã bị một advice khác thay đổi thay vì input gốc.
- After advice kiểm tra kết quả **trước khi** một advice khác biến đổi kết quả.
- Một around advice bỏ `proceed` nhưng vẫn trả giá trị giả thành công.
- Hợp đồng chỉ chạy qua proxy; lời gọi trực tiếp target đi vòng.
- Kiểm tra invariant dùng getter mà getter cũng thuộc pointcut, gây vòng lặp advice.

AOP giúp **tách riêng** một phần kiểm tra, nhưng không biến tính đúng đắn thành hệ quả tự động của việc khai báo pointcut.

## 5. Đặt precondition trong chuỗi before advice

Mô hình đơn giản:

```text
Caller
  -> ContractBefore
       -> OriginalMethod
```

Mã giả:

```text
around add(delta):
    require delta > 0
    return proceed(delta)
```

Khi `delta <= 0`, một contract advice có thể chặn ngay **trước khi chạy target**, nếu nó đã là lớp ngoài thích hợp.

### 5.1. Thứ tự với advice biến đổi input

Giả sử có hai advice:

- `NormalizeAdvice` sửa dữ liệu đầu vào.
- `ContractAdvice` kiểm tra precondition.

Hai thứ tự khác nhau:

```text
Contract(Normalize(Business))
Normalize(Contract(Business))
```

Nếu normalization thay đổi tham số, `Contract` ở ngoài kiểm tra **input do caller cung cấp**; ở trong nó kiểm tra **input sau normalization**. Cả hai có thể hợp lý tùy hợp đồng, nhưng không tương đương.

Ví dụ:

```text
Caller passes rawId = "   A   "
Normalize trims to "A"

Contract outer: kiểm tra input thô
Contract inner: kiểm tra input đã chuẩn hóa
```

Phải chọn ngữ nghĩa rõ ràng: **ai chịu trách nhiệm làm dữ liệu hợp lệ?** Nếu hợp đồng của public API cho phép khoảng trắng và chủ động chuẩn hóa, kiểm tra giá trị thô bằng điều kiện quá mạnh có thể từ chối một input hợp lệ.

### 5.2. Không biến precondition thành một lỗi quan sát phụ

Nếu advice trước chỉ dùng telemetry, đừng để lỗi telemetry trở thành “không đáp ứng precondition” trừ khi hợp đồng thật yêu cầu. Precondition thất bại thường cần **thông báo đúng loại**, tách khỏi lỗi của cơ chế ghi log.

## 6. Kiểm tra postcondition sau khi trả kết quả bình thường

Một hậu điều kiện có thể phụ thuộc:

- Giá trị trả về.
- Trạng thái mới của object.
- Tham số đã được truyền.
- Trạng thái trước khi vào operation (ký hiệu `old`).
- Tác dụng phụ được cam kết trong ranh giới quan sát được.

Mã giả:

```text
around add(delta):
    old = readBalance()
    result = proceed(delta)
    require result == old + delta
    require readBalance() == result
    return result
```

Đây là mã giả không thuộc một DSL cụ thể. `require` sau `proceed` biểu diễn **kiểm tra postcondition**, không phải precondition về mặt ý nghĩa.

### 6.1. Vì sao cần `old`?

Nếu chỉ xem trạng thái sau:

```text
result >= 0
```

ta chưa chứng minh phép cộng đã thêm đúng `delta`. Hợp đồng mạnh hơn so sánh trạng thái cũ và mới:

```text
newBalance == oldBalance + delta
```

Phần `oldBalance` cần được ghi lại **trước khi operation tạo side effect**, và phải bảo đảm snapshot đại diện đúng đối tượng/invocation được kiểm tra.

### 6.2. Chụp snapshot không miễn phí

Sao chép toàn bộ object graph chỉ để xác minh hậu điều kiện có thể tốn bộ nhớ, thời gian hoặc tạo vòng lặp khi có object tham chiếu lẫn nhau. Với API lớn, chọn **những thuộc tính cần thiết** và dùng cơ chế snapshot/differencing có hợp đồng rõ ràng.

Cần phân biệt **bản sao giá trị** với reference đến object mutable. Lưu `oldList = currentList` rồi sửa `currentList` sẽ không cho một bản chụp độc lập để kiểm tra thay đổi.

### 6.3. Postcondition chỉ kiểm được phần có thể quan sát

Nếu operation gửi message sang hệ thống ngoài rồi trả `accepted`, việc kiểm tra một biến cục bộ không chứng minh phía nhận đã xử lý message. Hợp đồng nên tách “đã gửi”, “đã được broker chấp nhận” và “đã được consumer xử lý”, vì đây là các ranh giới khác nhau.

## 7. Bất biến của đối tượng và các ranh giới kiểm tra

Một **class invariant** diễn tả trạng thái hợp lệ của một object tại các điểm công khai được xác định.

Ví dụ:

```text
Account:
    balance >= 0
    currency in supportedCurrencies
    accountId != null
```

Một chiến lược kiểm tra:

1. Kiểm invariant khi nhận lời gọi vào public operation.
2. Kiểm precondition của operation.
3. Thực hiện operation.
4. Kiểm postcondition trên đường trả bình thường.
5. Kiểm invariant trước khi trả control cho caller.
6. Với exception, áp dụng **hợp đồng trạng thái lỗi** (nếu có) thay vì mặc định mọi hậu điều kiện thông thường vẫn đúng.

**Điểm quan trọng:** không phải invariant nào cũng phải đúng ngay trong constructor trước khi toàn bộ field được thiết lập. Các public method được gọi trong constructor còn có rủi ro “object chưa hoàn tất khởi tạo”.

### 7.1. Invariant và thao tác private/helper

Một public method có thể gọi các private helper làm trạng thái tạm thời không nhất quán rồi phục hồi trước khi return. Nếu một aspect weave vào mọi method execution (bao cả helper), việc áp invariant tại từng helper có thể **báo false positive**.

Cần định nghĩa hợp đồng ở đúng boundary (public operation, transaction boundary, quiescent state...) và hiểu khả năng can thiệp nội bộ của công cụ.

### 7.2. Shared state và tính nhất quán

Trong hệ thống concurrent, đọc từng field không có đồng bộ thích hợp có thể thấy **snapshot không nhất quán**. Một invariant có thể đúng ở cả trước và sau update nhưng bộ kiểm tra đọc xen vào giữa và cho kết quả sai.

Các lựa chọn cần xét: lock, immutable snapshot, versioning, atomic state, transaction isolation hoặc quy tắc thread confinement. Kiểm tra hợp đồng không thay thế cơ chế đồng bộ và còn có thể ảnh hưởng lịch chạy của thread.

## 8. Đường lỗi: hợp đồng nào còn có hiệu lực?

Một điểm dễ sai là áp postcondition của đường thành công sau **mọi** ngoại lệ.

Ví dụ:

```text
Pre: transfer amount > 0
Normal Post: debit completed AND credit completed
Exceptional Guarantee: không có chuyển khoản được xác nhận thành công
Invariant: tổng số dư nội bộ thỏa điều kiện nhất quán
```

Nếu transfer ném lỗi mạng ở giữa, **không thể** suy rằng Normal Post vẫn phải đúng. Thay vào đó, hợp đồng cần nói rõ:

- Exception nào có thể thoát?
- Trạng thái nào vẫn được phép sau exception?
- Có bảo đảm rollback hay không?
- Có thể đã tạo side effect ở hệ thống ngoài không?
- Caller được phép retry trong điều kiện nào?
- Trạng thái có thể xác minh lại qua request ID/idempotency key không?

### 8.1. Ba mức bảo đảm an toàn khi exception

Trong thiết kế exception-safety thường phân biệt những mục tiêu:

| Bảo đảm | Nghĩa khái quát | Không đồng nghĩa |
| --- | --- | --- |
| **No-throw guarantee** | Thao tác được thiết kế không cho exception thoát trong phạm vi công bố | Không phải mọi hoạt động đều chắc chắn thành công nếu có kênh báo lỗi khác |
| **Strong guarantee** | Khi thao tác thất bại, trạng thái liên quan được giữ như trước, theo phạm vi xác định | Không có nghĩa mọi side effect từ xa cũng rollback |
| **Basic guarantee** | Sau lỗi, đối tượng vẫn ở trạng thái hợp lệ, không rò tài nguyên, nhưng một phần trạng thái có thể thay đổi | Không phải operation đã hoàn tất |

Các từ trên là **mức bảo đảm theo hợp đồng thiết kế**, không phải tự động có sẵn do AOP hay Java exception. Một thao tác có thể chọn bảo đảm phù hợp với miền nghiệp vụ.

### 8.2. After throwing có thể kiểm exceptional invariant

Một after-throwing advice có thể ghi nguyên nhân rồi kiểm trạng thái còn hợp lệ:

```text
try:
    result = proceed()
    checkNormalPost(result)
    return result
catch original:
    checkExceptionalInvariant()
    throw original
```

Đây là mã giả. Cẩn thận: nếu `checkNormalPost` nằm trong `try`, lỗi do **chính kiểm tra hậu điều kiện** cũng đi vào catch và bị gán nhầm là lỗi từ `proceed`. Nên thiết kế phạm vi `try/catch` theo đúng **nguồn lỗi muốn phân loại**.

## 9. Thứ tự advice quyết định hợp đồng đang kiểm tra cái gì

Giả sử:

```text
ContractAdvice(
    NormalizeAdvice(
        Business
    )
)
```

Contract ngoài nhìn thấy input thô và kết quả **đã đi qua Normalize**.

Nếu đảo lại:

```text
NormalizeAdvice(
    ContractAdvice(
        Business
    )
)
```

Contract trong có thể nhìn input đã biến đổi, nhưng chưa thấy biến đổi kết quả do Normalize ngoài thực hiện sau khi tiếp tục.

Từ góc độ correctness, cần quyết định **hợp đồng của tầng nào**:

- Contract của **target gốc**.
- Contract của **public facade/proxy** mà caller nhìn thấy.
- Contract của **một advice** độc lập.
- Contract của **composition** gồm tất cả advice.

Một hệ thống có thể cần nhiều lớp kiểm tra khác nhau để bảo đảm **mỗi tầng** và **kết quả cuối cùng** đều đúng.

### 9.1. Chỉ kiểm target không đủ

Nếu target trả kết quả đúng hậu điều kiện nhưng after advice cộng thêm phí hoặc đổi đối tượng trả về, hợp đồng public của caller có thể bị vi phạm **sau khi target kết thúc**.

### 9.2. Chỉ kiểm kết quả cuối cũng chưa đủ

Nếu một advice bên trong gây side effect không phù hợp rồi advice ngoài sửa lại output, caller có thể nhận kết quả hợp lệ nhưng hệ thống đã thay đổi trạng thái sai.

Khi cần tính đúng đắn nội bộ, phải có invariant/contract tương ứng ở tầng phát sinh side effect, không chỉ ở lớp ngoài cùng.

## 10. Around advice có thể phá vỡ hợp đồng thế nào?

Around advice có thể thực hiện các thay đổi mạnh:

1. **Không gọi proceed:** chặn operation.
2. **Gọi proceed một lần:** phần gốc hoạt động bình thường.
3. **Gọi proceed nhiều lần:** retry/repetition, nếu công cụ cho phép.
4. **Thay tham số:** phần gốc nhận input khác input caller.
5. **Thay kết quả:** caller nhận result khác target tạo.
6. **Bắt và đổi exception:** caller quan sát kiểu lỗi khác.
7. **Trả fallback:** một lỗi từ phần gốc biến thành kết quả bình thường.

Mỗi thay đổi phải có **hợp đồng công khai** và kiểm thử tương ứng.

### 10.1. Short-circuit và postcondition

Nếu một around advice không gọi `proceed` nhưng trả `0`, trong khi hợp đồng của operation yêu cầu:

```text
result == old + delta
```

thì có thể vi phạm postcondition, dù Java type-check hoàn toàn hợp lệ.

Nếu yêu cầu cho phép trả fallback, **hợp đồng nên mô tả trạng thái fallback** và bảo đảm giá trị trả về không giả thành công. Ví dụ “trả bản cache có độ mới tối đa 30 giây” là một nhánh hợp đồng khác với “đã cập nhật cơ sở dữ liệu”.

### 10.2. Retry và hậu điều kiện

Nếu `proceed` chạy hai lần, invariant có thể được giữ sau mỗi lần nhưng **hậu điều kiện của operation logic một lần** bị sai (số dư tăng gấp đôi). Một around advice retry phải hiểu operation có idempotent không và thử lại có tái thực thi side effect không.

### 10.3. Exception translation

Đổi `IOException` thành `DomainUnavailable` có thể hợp đồng public, nhưng nếu không giữ `cause` sẽ khó truy vết nguyên nhân. Nếu error taxonomy làm caller retry sai, đó cũng là một vấn đề hợp đồng, không chỉ logging.

## 11. Mã Java 17 chạy được: pre, post, invariant và exception path

Java không có cú pháp AOP weaving chung tích hợp trong ngôn ngữ. Ví dụ dùng **JDK dynamic proxy** làm một lớp interceptor để minh họa việc kiểm tra hợp đồng tại **lời gọi qua giao diện**.

Chương trình có bốn trường hợp:

- **`ok`:** precondition, postcondition và invariant đều đúng.
- **`invalid`:** input vi phạm precondition, target không được gọi thêm.
- **`broken-post`:** target thực sự thay đổi state nhưng trả kết quả sai hậu điều kiện.
- **`throw-after-mutation`:** target thay đổi state rồi ném exception; invariant vẫn đúng nhưng không có strong rollback guarantee.

Tạo tệp `AopContractDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class AopContractDemo {

    interface Counter {
        int add(int amount);
        int value();
    }

    static final class CoreCounter implements Counter {
        private int total;
        private final boolean wrongResult;
        private final boolean failAfterMutation;

        CoreCounter(int initial, boolean wrongResult,
                    boolean failAfterMutation) {
            this.total = initial;
            this.wrongResult = wrongResult;
            this.failAfterMutation = failAfterMutation;
        }

        @Override
        public int add(int amount) {
            total += amount;
            System.out.println("business:add:" + amount + ":total:" + total);

            if (failAfterMutation) {
                throw new IllegalStateException("storage unavailable");
            }

            return wrongResult ? total + 1 : total;
        }

        @Override
        public int value() {
            return total;
        }
    }

    static Counter withContract(Counter target) {
        return (Counter) Proxy.newProxyInstance(
                Counter.class.getClassLoader(),
                new Class<?>[] {Counter.class},
                (Object proxy, Method method, Object[] args) -> {
                    if (!method.getName().equals("add")) {
                        return method.invoke(target, args);
                    }

                    int amount = (Integer) args[0];
                    System.out.println("contract:pre:" + amount);

                    if (amount <= 0) {
                        throw new IllegalArgumentException(
                                "amount must be positive");
                    }

                    int oldValue = target.value();
                    if (oldValue < 0) {
                        throw new IllegalStateException(
                                "entry invariant violated");
                    }

                    System.out.println("advice:before");

                    try {
                        Object raw;
                        try {
                            raw = method.invoke(target, args);
                        } catch (InvocationTargetException wrapped) {
                            Throwable original = wrapped.getCause();
                            System.out.println("advice:throw:"
                                    + original.getClass().getSimpleName());

                            int current = target.value();
                            System.out.println("contract:exceptional-state:"
                                    + current);

                            if (current < 0) {
                                throw new IllegalStateException(
                                        "exceptional invariant violated",
                                        original);
                            }
                            throw original;
                        }

                        int result = (Integer) raw;
                        System.out.println("contract:post:expected:"
                                + (oldValue + amount) + ":actual:" + result);
                        if (result != oldValue + amount) {
                            throw new IllegalStateException(
                                    "postcondition violated");
                        }

                        int current = target.value();
                        System.out.println("contract:invariant:" + current);
                        if (current < 0) {
                            throw new IllegalStateException(
                                    "exit invariant violated");
                        }

                        System.out.println("advice:after");
                        return result;
                    } finally {
                        System.out.println("advice:finally");
                    }
                }
        );
    }

    static void execute(String label, Counter counter, int amount) {
        System.out.println("case:" + label);
        try {
            System.out.println("caller:result:" + counter.add(amount));
        } catch (RuntimeException error) {
            System.out.println("caller:error:"
                    + error.getClass().getSimpleName()
                    + ":" + error.getMessage());
        }
        System.out.println("caller:state:" + counter.value());
    }

    public static void main(String[] args) {
        Counter normal = withContract(new CoreCounter(10, false, false));
        Counter incorrect = withContract(new CoreCounter(10, true, false));
        Counter exceptional = withContract(new CoreCounter(10, false, true));

        execute("ok", normal, 5);
        execute("invalid", normal, 0);
        execute("broken-post", incorrect, 5);
        execute("throw-after-mutation", exceptional, 5);
    }
}
```

Biên dịch/chạy với JDK 17 trở lên:

```bash
javac AopContractDemo.java
java AopContractDemo
```

**Đầu ra chính xác:**

```text
case:ok
contract:pre:5
advice:before
business:add:5:total:15
contract:post:expected:15:actual:15
contract:invariant:15
advice:after
advice:finally
caller:result:15
caller:state:15
case:invalid
contract:pre:0
caller:error:IllegalArgumentException:amount must be positive
caller:state:15
case:broken-post
contract:pre:5
advice:before
business:add:5:total:15
contract:post:expected:15:actual:16
advice:finally
caller:error:IllegalStateException:postcondition violated
caller:state:15
case:throw-after-mutation
contract:pre:5
advice:before
business:add:5:total:15
advice:throw:IllegalStateException
contract:exceptional-state:15
advice:finally
caller:error:IllegalStateException:storage unavailable
caller:state:15
```

### 11.1. Trường hợp `ok`

Input `amount=5` thỏa precondition. Snapshot `oldValue=10`. Target tăng `total` thành 15 và trả 15, nên postcondition `result == old+amount` đúng. Invariant `total>=0` vẫn đúng. Caller nhận kết quả 15.

### 11.2. Trường hợp `invalid`

`amount=0` vi phạm precondition. Handler ném `IllegalArgumentException` **trước khi vào khối chứa lời gọi target**; vì vậy không có dòng `business:add`, `advice:before` hay `advice:finally` cho ca này. State vẫn là 15 sau ca trước.

Đây là ví dụ cho thấy **không phải mọi finally của mọi advice đều bắt buộc chạy khi precondition thất bại**: điều đó còn phụ thuộc phạm vi try/finally đã được thiết lập.

### 11.3. Trường hợp `broken-post`

Target tăng state từ 10 lên 15 nhưng cố ý trả 16. Handler phát hiện hậu điều kiện sai rồi ném `IllegalStateException`.

**Quan sát quan trọng:** caller nhận exception **nhưng state đã là 15**. Phát hiện postcondition sai **không tự hoàn tác mutation**. Nếu cần strong exception guarantee, cơ chế transaction/compensation hoặc thiết kế phép biến đổi phải thực sự cung cấp bảo đảm ấy.

### 11.4. Trường hợp `throw-after-mutation`

Target cập nhật state lên 15 rồi ném `IllegalStateException("storage unavailable")`. Handler kiểm **exceptional invariant** `total>=0` và truyền lỗi gốc lên caller.

Ở đây **basic guarantee** về invariant vẫn có thể đúng trong khi **strong guarantee** không đúng: state thay đổi so với trước khi gọi.

### 11.5. Giới hạn ví dụ

- Đây là **wrapper qua JDK proxy**, không phải công cụ tự phân tích pointcut hay weave bytecode.
- Chỉ invocation đi qua `Counter` proxy mới kích hoạt các kiểm tra; gọi trực tiếp `CoreCounter` sẽ bỏ qua handler.
- Handler chỉ áp hợp đồng riêng cho `add`; `value` được ủy quyền như getter để đọc state phục vụ ví dụ.
- `Method.invoke` bọc exception do target ném trong `InvocationTargetException`; code gỡ wrapper để giữ cause từ target.
- Ví dụ xử lý `int` với số nhỏ; bản production cần xử lý overflow và concurrent updates.
- Hợp đồng đang được kiểm tra tại **ranh giới proxy**, không chứng minh mọi method nội bộ hoặc lời gọi ngoài proxy đều được bảo vệ.

## 12. Vì sao phạm vi try/catch khi kiểm postcondition phải rõ?

Một cách viết dễ nhầm:

```text
try:
    result = proceed()
    checkPostcondition(result)
    logSuccess()
    return result
catch error:
    logBusinessFailure(error)
    throw error
```

Nếu `checkPostcondition` ném `ContractViolation`, catch cũng gắn nhãn “business failure”, mặc dù phần gốc có thể đã hoàn tất. Nếu `logSuccess` ném lỗi, tình trạng tương tự xảy ra.

Một tổ chức ranh giới dễ hiểu hơn:

```text
try:
    result = proceed()
catch continuationError:
    checkExceptionalGuarantee(continuationError)
    throw continuationError

checkNormalPostcondition(result)
checkInvariant()
return result
```

Cần định nghĩa chính sách cho **lỗi của chính phép kiểm tra hợp đồng**. Nó không phải lúc nào cũng nên bị xử lý như lỗi từ `proceed`.

### 12.1. Thứ tự giữa kiểm postcondition và kiểm invariant

Một thiết kế có thể chọn:

```text
Business return
  -> check normal postcondition
  -> check invariant
  -> return caller
```

Thiết kế khác có thể kiểm invariant trước postcondition. **Không có thứ tự được mọi AOP engine bảo đảm**. Nếu một phép kiểm tra ném lỗi, các phép kiểm tra sau có thể không chạy, khiến hệ thống chỉ báo một vi phạm trong nhiều vi phạm.

Hãy xác định thứ tự phục vụ **ngữ nghĩa và chẩn đoán**, tránh dựa vào thứ tự tình cờ giữa nhiều advice chưa khai báo precedence.

## 13. Hợp đồng của advice: aspect cũng là thành phần có trách nhiệm

Không chỉ method nghiệp vụ mới có hợp đồng. Một advice cũng có thể yêu cầu:

- Đầu vào context phải có thuộc tính cần thiết.
- Không được ghi dữ liệu nhạy cảm trong telemetry.
- Nếu chỉ đo thời gian, phải giữ nguyên return value/exception theo chính sách.
- Nếu có quyền retry, phải tôn trọng giới hạn attempt và idempotency.
- Nếu tự thay kết quả, kết quả thay phải thỏa hợp đồng public.
- Khi cleanup thất bại, phải xử lý primary/suppressed theo chính sách.
- Không giữ mutable invocation state giữa hai request độc lập.
- Không gọi `proceed` nếu điều kiện bảo mật từ chối.

### 13.1. Advice quan sát có “hợp đồng không can thiệp”

Với aspect chỉ logging, một yêu cầu tự nhiên:

```text
Nếu target returns R: caller vẫn nhận R.
Nếu target throws E: caller vẫn nhận E.
Số lần target chạy không bị thay đổi.
Advice không sửa args hoặc domain state.
```

Đây là **hợp đồng về tính trong suốt quan sát**. Nhưng chính logger có thể ném exception hoặc thay timing/locking, nên cần thiết kế cách xử lý telemetry failure và kiểm thử thực tế.

### 13.2. Advice làm chính sách cần hợp đồng mạnh hơn

Với authorization advice, **thay đổi hành vi** là có chủ đích: nó có thể không gọi `proceed` khi không được phép. Khi đó postcondition của nghiệp vụ gốc **không phải điều kiện phải đúng cho nhánh bị từ chối**. Thay vào đó cần một hợp đồng riêng:

```text
Nếu không được phép:
    target invocation count == 0
    caller nhận lỗi từ chối phù hợp
    không có side effect bị cấm
```

Đây là cách hợp đồng được mô tả theo **nhánh thực thi**, tránh coi mọi return/throw giống nhau.

## 14. Thiết kế hợp đồng theo lớp: target, aspect, public API

Trong hệ thống có nhiều aspect:

```text
Caller
  -> PublicContract
       -> Normalization
            -> Authorization
                 -> Business
```

Ta cần phân biệt:

1. **Target contract:** điều kiện mà Business cam kết khi được gọi đúng cách.
2. **Aspect contract:** hành vi mà Normalization/Authorization cam kết.
3. **Composed contract:** điều kiện toàn bộ chuỗi cung cấp cho caller.

Một target có thể chấp nhận chuỗi đã chuẩn hóa; public API có thể chấp nhận input chưa chuẩn hóa rồi normalize. Nếu dời precondition của target ra ngoài Normalization mà không điều chỉnh, ta có thể **vô tình siết chặt public precondition**.

Tương tự, nếu Business trả một ID nội bộ nhưng public API trả token khác, postcondition public cần kiểm tra **token**, không chỉ ID ở lớp trong.

### 14.1. Chịu trách nhiệm khi vi phạm

Khi một precondition public thất bại, caller có thể sai đầu vào. Khi target postcondition thất bại dù precondition đúng, implementation hoặc một advice bên trong có thể sai. Khi chỉ public postcondition thất bại, nguyên nhân có thể do advice biến đổi kết quả.

**Đừng chỉ in “contract violated”** rồi mất thông tin. Nên ghi rõ:

- Hợp đồng ở ranh giới nào.
- Điều kiện nào không thỏa.
- Trạng thái/giá trị liên quan đã được phép ghi.
- Aspect/operation/invocation ID tương ứng.
- Phân biệt vi phạm trước, sau hay bất biến.
- Cause chain nếu có lỗi khác kèm theo.

## 15. Subtyping và nguyên tắc thay thế theo hợp đồng

Khi một class triển khai interface hoặc một class con thay thế class cha, **nguyên tắc thay thế** khuyến khích:

- **Không tăng yêu cầu lên caller** một cách bất ngờ: subtype không nên đòi tiền điều kiện mạnh hơn hợp đồng supertype.
- **Không giảm bảo đảm cho caller**: subtype không nên hứa hậu điều kiện yếu hơn hợp đồng đã công bố.
- Giữ những bất biến có liên quan đến hợp đồng thay thế công khai.

Ví dụ interface cho phép `amount>=0`, nhưng một proxy subclass đột ngột chỉ cho `amount>100` mà caller không được thông báo. Nó có thể từ chối các lời gọi hợp lệ theo interface, làm phá khả năng thay thế.

### 15.1. Làm yếu precondition và làm mạnh postcondition

Nếu implementation chấp nhận **nhiều input hợp lệ hơn** và vẫn đáp ứng bảo đảm cũ, đó thường là mở rộng an toàn hơn. Nếu implementation cung cấp **bảo đảm mạnh hơn** cho kết quả đúng tiền điều kiện, caller cũ vẫn có thể hoạt động.

Ngược lại, giới hạn input đột ngột hoặc hạ bảo đảm đầu ra có thể phá hợp đồng mà caller dựa vào.

### 15.2. Aspect chính sách có thể thay đổi hợp đồng có chủ đích

Một security aspect có thể cố ý từ chối operation cho người dùng không có quyền. Điều này phải là **một phần công khai của hợp đồng public** hoặc boundary tin cậy, không bí mật xem như một before advice “không thay đổi API”.

**Khi advice thay đổi hợp đồng, hãy phiên bản hóa và mô tả thay đổi như thay đổi API/behavior**, không chỉ một cấu hình kỹ thuật.

## 16. Ngoại lệ và bất biến không hoàn toàn độc lập

Một lời gọi có thể thất bại ở nhiều giai đoạn:

| Giai đoạn | Ví dụ lỗi | Điều cần kiểm |
| --- | --- | --- |
| Kiểm precondition | Input không hợp lệ | Target đã chạy chưa? |
| Entry invariant | Object đã có state sai trước lời gọi | Ai đã làm sai và còn an toàn để xử lý không? |
| Before advice phụ | Logger không sẵn sàng | Có được phép che/chặn nghiệp vụ không? |
| Business body | I/O hoặc domain failure | Exceptional guarantee và state hiện tại |
| Normal postcondition | Result/state không như cam kết | Có side effect cần phục hồi không? |
| Exit invariant | Object rời operation với state sai | Có thể cô lập object hoặc chặn công bố không? |
| After advice phụ | Metrics/audit lỗi sau business | Caller thấy lỗi gì, có được retry không? |
| Cleanup/finally | Giải phóng tài nguyên thất bại | Giữ primary error/cause/suppressed đúng chính sách |

Nếu postcondition ném exception, lỗi ấy có thể lan lên after-throwing advice của **lớp ngoài**, mặc dù business đã return bình thường. Vì thế phân loại “thrown branch” ở một lớp không đồng nghĩa body nghiệp vụ ném exception.

### 16.1. Exception của check không phải một phép rollback

Một `ContractViolation` có thể xuất hiện **sau khi state đã bị thay đổi**. Muốn rollback cần cơ chế transaction hoặc compensation có hợp đồng riêng. Đừng viết test “thấy lỗi thì state không đổi” trừ khi operation cam kết strong guarantee và có implementation đúng.

## 17. Tác động của around advice và thứ tự nhiều aspect

Giả sử có:

```text
ContractAround(
    RetryAround(
        Business
    )
)
```

Contract ngoài chụp snapshot trước **toàn bộ retry** và kiểm tra trạng thái cuối. Nếu retry khiến Business chạy hai lần, postcondition của “một operation logic” có thể sai.

Đảo thứ tự:

```text
RetryAround(
    ContractAround(
        Business
    )
)
```

Contract trong có thể kiểm tra **mỗi attempt**, nhưng tổng thể retry vẫn có thể tạo side effect sai cho caller.

Do đó không chỉ quan tâm “ai chạy trước”; phải biết:

- Contract đo theo invocation logic hay theo từng attempt?
- Snapshot được lấy khi nào?
- Có cách nhận diện idempotent request không?
- Exception của chính ContractAround có bị RetryAround bắt và retry không?
- Retry có được phép xử lý **contract violation** không? Thường không nên thử lại bug logic chỉ vì một exception là unchecked.

### 17.1. Lỗi từ hợp đồng cần loại khác lỗi tạm thời

Nếu retry bắt toàn bộ `RuntimeException`, một `PostconditionViolation` có thể khiến target chạy lại mặc dù lần đầu đã hoàn tất mutation. **Chỉ retry nhóm lỗi đã được phân loại là có thể retry**, và luôn xét idempotency/trạng thái thực tế.

### 17.2. Aspect nào quan sát kết quả cuối?

Một contract checker đặt **bên trong** result-transforming advice có thể kiểm kết quả trước khi transform. Một checker **bên ngoài** có thể kiểm API đã transform. Khi cần cả hai, phải định nghĩa hai contract khác nhau thay vì cho một công thức áp dụng mù quáng.

## 18. AOP qua proxy và lỗ hổng phạm vi hợp đồng

Một proxy checker có thể áp đặt tiền điều kiện ở public interface. Nhưng nếu ứng dụng có:

```text
Caller A -> Proxy -> Contract -> Target
Caller B ---------------------> Target
```

thì Caller B **không chạy contract advice**.

Các đường dễ bypass:

- Caller dùng `new Target()` thay vì factory trả proxy.
- Một reference tới target gốc bị lưu trong cache/registry.
- Target gọi `this.otherMethod()` mà không quay qua proxy.
- Thư viện ngoài tự tạo object.
- Callback giữ raw target.
- Method final/private/static không thuộc cơ chế override mong đợi.

**Không suy rằng contract chỉ tồn tại ở proxy là một bảo đảm an toàn tuyệt đối.** Với invariant hoặc quyền bảo mật trọng yếu, cần bảo vệ ở boundary đáng tin và kiểm thử đường trực tiếp. Nếu muốn can thiệp vào method body hoặc call sites rộng hơn, phải xem khả năng weaving của công cụ thực tế.

### 18.1. Kiểm invariant tại public boundary khác kiểm mọi helper

Một method private có thể được gọi nội bộ trong lúc object đang tạm biến đổi trạng thái. Dệt kiểm invariant lên mọi helper có thể báo lỗi sai, vì hợp đồng invariant thường được xác định tại những trạng thái quan sát được từ bên ngoài.

Điểm kiểm tra phải **khớp abstraction boundary**, không chạy ở mọi vị trí chỉ vì công cụ có thể weave.

## 19. Contract assertions khác `assert` của Java

Java cung cấp câu lệnh:

```java
assert amount > 0 : "amount must be positive";
```

Đây là mảnh code Java. Các **assertion Java thường bị tắt mặc định**, được bật theo cấu hình runtime như `-ea`; ngoài ra assertion có ngữ nghĩa riêng và khi sai ném `AssertionError`.

Vì thế:

- Đừng dùng `assert` làm **cơ chế kiểm tra đầu vào/bảo mật bắt buộc** nếu cấu hình runtime có thể tắt.
- Một AOP contract checker chủ động ném `IllegalArgumentException` hoặc lỗi riêng có thể vẫn chạy khi Java assertion bị tắt, nhưng lại phụ thuộc việc invocation có đi qua interceptor và advice được bật hay không.
- Một chương trình Java không tự động có DbC đầy đủ chỉ vì có `assert`.
- Nếu checker được disable ở production, cần quyết định những invariant nào vẫn phải được bảo vệ trong code nghiệp vụ.

### 19.1. Contract checking trong production

Có thể phân loại:

1. **Validation bắt buộc tại trust boundary:** không thể tùy ý tắt.
2. **Internal correctness checks:** có thể bật ở test/staging và một phần production theo chính sách.
3. **Diagnostics đắt tiền:** có thể kiểm theo sampling hoặc chế độ điều tra, nếu không ảnh hưởng cam kết bắt buộc.

Không gọi mọi hợp đồng là “chỉ để debug”. Nếu caller được hứa rằng một mutation không bao giờ làm state âm, việc vô hiệu hóa checker không làm lời hứa hết hiệu lực; implementation vẫn phải đúng.

## 20. Contract checks nên ít side effect và không tái nhập

Một phương thức kiểm postcondition có thể gọi getter. Nếu getter lại khớp pointcut giống method gốc, checker có thể vô tình tái kích hoạt chính aspect:

```text
ContractAdvice -> checkInvariant() -> getState()
                    ^                   |
                    +---- interception--+
```

Các kỹ thuật tránh:

- Giới hạn pointcut loại trừ những method của checker.
- Định nghĩa contract trên các API đọc trạng thái đã được thiết kế không tái nhập.
- Quản lý reentrancy theo invocation phù hợp.
- Không dùng cờ global đơn giản làm tắt checker của request khác.
- Không ghi log toàn bộ object bằng `toString()` nếu `toString` có side effect hoặc tự bị intercept.
- Kiểm tra những giá trị được truy cập có phù hợp visibility/module và concurrency model không.

**Một phép kiểm tra lý tưởng nên có tính xác định và ít side effect.** Nếu chính checker thay đổi trạng thái rồi làm invariant sai, nó không còn là một công cụ quan sát đáng tin.

## 21. Điều kiện trước/sau với API bất đồng bộ

Ví dụ một method Java trả `CompletableFuture<Result>`:

```text
caller -> method executes -> returns Future
                            |
                            +-- task continues
                            |
                            +-- Future completes later
```

Nếu advice kiểm postcondition **ngay sau khi method trả Future**, nó có thể chỉ kiểm **hợp đồng trả handle**, không thể kết luận tác vụ đã hoàn tất.

Phải định nghĩa hai ranh giới:

- **Synchronous method postcondition:** Future không null, được đăng ký/có ID phù hợp.
- **Asynchronous completion contract:** khi Future hoàn tất bình thường, kết quả đáp ứng Q; khi hoàn tất đặc biệt, lỗi và trạng thái đáp ứng exceptional guarantee.

Một after throwing bao method execution có thể không nhận lỗi xảy ra **về sau** trong task. Kiểm completion cần API stage/handler tương ứng.

### 21.1. Callback kiểm hợp đồng có thể tự tạo lỗi

Nếu thêm `handle`, `thenApply` hoặc `whenComplete` vào một stage, callback có thể ném exception. Nó có thể làm stage tiếp theo thất bại hoặc làm thay đổi đường kết thúc tùy API và loại callback.

Cần phân biệt lỗi business với lỗi do **contract checker trong completion handler**; không chỉ gộp mọi exceptional completion thành “business threw”.

### 21.2. Context và snapshot qua thread

Nếu postcondition dùng `oldBalance` được lưu trong ThreadLocal, task chạy trên thread khác có thể không có snapshot đúng. Nên mang context theo invocation/task với vòng đời rõ, không phụ thuộc rằng thread ID đại diện cho request.

## 22. Hợp đồng trong hệ thống nhiều luồng

Một contract:

```text
post: value == oldValue + delta
```

không thể được kiểm đúng nếu **một thread khác cũng thay đổi value giữa lúc lấy snapshot và lúc kiểm**, trừ khi operation/hợp đồng có mô hình đồng bộ thích hợp.

Ví dụ:

```text
T1: old = 10
T1: add(5) -> total = 15
T2: add(2) -> total = 17
T1: check total == 15 -> false
```

Nếu cả hai phép cộng đều hợp lệ, checker của T1 có thể báo **false positive** chỉ vì snapshot và post-check không thuộc một vùng atomic.

Giải pháp tùy miền:

- Khóa để gắn pre/body/post vào critical section thích hợp.
- Cấu trúc immutable/atomic với linearization point rõ.
- So sánh theo version hoặc operation result thay vì trạng thái toàn cục sau khi thread khác chạy.
- Hợp đồng mô tả tính nhất quán theo event/session, không giả định chạy tuần tự.
- Tách hợp đồng thread-safe API khỏi hợp đồng truy cập dữ liệu nội bộ.

Một aspect tự động bao method **không tạo ra atomicity** chỉ nhờ thêm các phép đọc trước/sau. Nếu advice cầm lock, phải đánh giá deadlock, thứ tự khóa và latency.

## 23. Hợp đồng phân tán và bất biến liên đối tượng

Một invariant đơn giản `balance>=0` có thể kiểm cục bộ. Nhưng invariant:

```text
totalDebits == totalCredits
```

trên nhiều kho dữ liệu/dịch vụ có thể cần cách thiết kế khác.

Không nên dùng một after advice kiểm hai cơ sở dữ liệu tại một thời điểm rồi kết luận toàn bộ hệ thống “vi phạm” nếu hệ thống được thiết kế **eventual consistency** và cho phép trạng thái trung gian.

Cần xác định:

1. Invariant là **tức thời** hay **cuối cùng phải đạt**?
2. Điểm quan sát hợp lệ là sau commit, sau message ack hay sau saga completion?
3. Có operation bù (compensation) nếu một bước thất bại không?
4. Có correlation ID để ghép các sự kiện liên quan không?
5. Có timeout và trạng thái không xác định không?
6. Có contract riêng cho mỗi service/boundary không?

AOP có thể ghi telemetry hoặc kiểm guard tại từng API, nhưng **không tự chuyển hệ thống phân tán thành giao dịch nguyên tử**.

## 24. Contract violation phải mang thông tin chẩn đoán phù hợp

Khi một check thất bại, nên phân biệt rõ:

| Trường | Ý nghĩa |
| --- | --- |
| Contract kind | Precondition, normal postcondition, exceptional condition, invariant |
| Boundary | Public proxy, target method, advice hoặc async completion |
| Operation | Loại join point và chữ ký có ý nghĩa |
| Invocation ID | Phân biệt các lần gọi lồng nhau/đa luồng |
| Violation code | Mã ổn định cho automation/monitoring |
| Expected predicate | Điều kiện muốn giữ, không cần in bí mật |
| Observed state | Những giá trị đã được phép ghi |
| Cause | Lỗi gốc nếu một checker chuyển đổi/bao lỗi |
| Build/config ID | Biết chính sách pointcut/advice nào đang áp dụng |

Không nên log toàn bộ `args` hoặc domain object theo mặc định. Contract context có thể chứa token, thông tin tài chính hoặc dữ liệu người dùng. Dùng danh sách trường cho phép, che dữ liệu nhạy cảm và giới hạn thông tin công bố bên ngoài.

### 24.1. Lỗi của logging không nên vô tình che vi phạm gốc

Nếu checker phát hiện `PostconditionViolation` rồi logging ném `MetricUnavailable`, caller có thể chỉ thấy lỗi logger. Cần xác định primary/secondary error, có thể giữ lỗi phụ bằng `suppressed` khi phù hợp.

Nhưng đừng coi mọi audit failure là lỗi phụ có thể bỏ qua: yêu cầu pháp lý hoặc an ninh có thể quy định hành vi khác.

## 25. Ma trận kiểm thử Design by Contract qua AOP

| Trường hợp | Precondition | Business/advice | Postcondition / invariant | Kỳ vọng |
| --- | --- | --- | --- | --- |
| Input hợp lệ | Đúng | Business chạy 1 lần | Đúng | Trả kết quả |
| Input sai | Sai | Business không chạy | Không kiểm normal post | Lỗi đầu vào rõ |
| Business trả sai giá trị | Đúng | Body đã chạy | Post sai | Báo violation, kiểm state thực |
| Business ném trước mutate | Đúng | Ném exception | Exceptional guarantee | Lỗi gốc/contract đúng |
| Business ném sau mutate | Đúng | Một phần state thay đổi | Basic invariant có thể còn đúng | Không mặc định rollback |
| Advice sửa args trước target | Tùy ranh giới | Input tới target khác | Contract public/target có thể khác | Test hai lớp contract |
| Advice trả fallback | Có thể đúng | Target có thể không chạy | Contract nhánh fallback | Không trả thành công giả |
| Advice đổi result sau body | Đúng | Body đã hoàn tất | Public post có thể sai | Checker đúng lớp ngoài phát hiện |
| Advice sau body tự ném | Đúng | Body đã thành công | Không có return bình thường ở lớp ngoài | Không nhầm lỗi business |
| Retry operation | Đúng | Body có thể chạy nhiều lần | Post cho operation logic | Không nhân đôi side effect |
| Direct target call | Không qua proxy | Body chạy | Proxy checker bị bypass | Architecture test phát hiện |
| Self-invocation | Tùy receiver | Lời gọi nội bộ | Checker method trong có thể bypass | Test đúng proxy/weaving |
| Concurrent updates | Đúng | Thread khác xen kẽ | Snapshot có thể cũ | Không báo sai vì race |
| Future trả trước completion | Đúng | Task chạy sau | Completion contract riêng | Không kết luận từ method return |
| Cleanup cùng lỗi gốc | Đúng | Business hoặc checker ném | Lỗi phụ cũng phát sinh | Cause/suppressed theo policy |

### 25.1. Kiểm thử cả đường không được chọn

Không nên chỉ assert checker chạy khi method khớp pointcut. Cần có **negative tests**:

- Method không thuộc scope không bị can thiệp.
- Constructor/helper nội bộ không bị check invariant ở thời điểm tạm mất nhất quán ngoài chủ đích.
- DTO/value object không cần contract advice không bị wrap chỉ vì có tên method giống nhau.
- Caller đi qua target trực tiếp được nhận diện là bypass nếu chính sách bắt buộc không cho phép.

### 25.2. Kiểm tra thứ tự và identity của exception

Nếu thiết kế truyền nguyên `IllegalStateException` từ target, có thể kiểm tra identity `caught == original` khi mô hình cho phép. Nếu advice wrap, kiểm tra `getCause()`, contract loại lỗi và những trường được phép công bố.

### 25.3. Kiểm tra không lặp side effect

Khi checker phát hiện postcondition sai sau mutation, một retry mechanism bên ngoài không được tự động chạy lại phép biến đổi nếu chưa chứng minh an toàn. Test nên đếm số lần business chạy và trạng thái cuối.

## 26. Quy trình áp dụng hợp đồng vào một mã nguồn AOP thật

1. **Viết hợp đồng trước:** đâu là input hợp lệ, normal post, exceptional guarantee và invariant?
2. **Chọn boundary:** public API, method execution, call site, task completion hay transaction?
3. **Xác định pointcut:** chỉ chọn những operation có contract tương ứng, tránh wildcard quá rộng.
4. **Phân loại advice:** trước, sau returning, sau throwing, finally hoặc quanh.
5. **Vẽ precedence:** checker nhìn input/result ở tầng nào, trước hay sau normalization/error translation?
6. **Chọn snapshot tối thiểu:** cần “old” ở những field nào, có an toàn với mutable/concurrent state không?
7. **Thiết kế loại lỗi vi phạm:** phân biệt caller violation, implementation violation, exceptional invariant.
8. **Giữ thông tin nguyên nhân:** cause/suppressed/trace ID; bảo vệ dữ liệu nhạy cảm.
9. **Kiểm thử đường thành công và thất bại:** bao gồm lỗi do advice tự tạo.
10. **Đo chi phí runtime:** reflection, snapshot, I/O, allocation, locking.
11. **Kiểm thử đường bypass:** direct target, self-invocation, external objects, async tasks.
12. **Đối chiếu yêu cầu production:** checker nào bắt buộc, checker nào chỉ debug, ai có quyền disable?
13. **Đánh giá khả năng phục hồi:** phát hiện vi phạm có cần cô lập object, rollback hay báo trạng thái chưa xác định?
14. **Theo dõi khi thay đổi API:** nếu public contract đổi, caller và test cũng phải được xem xét.

Một contract aspect ngắn không thay thế được bước **xác định nghĩa của contract**. Nếu `oldState`, outcome hay transaction boundary mơ hồ, tự động hóa kiểm tra sẽ tạo kết quả khó tin.

## 27. Những hiểu nhầm thường gặp

**“Precondition chỉ là if-check trước method.”** Không đủ. Nó còn là một phần của hợp đồng caller/supplier và phụ thuộc vị trí trong chuỗi advice.

**“Postcondition đúng nghĩa là chương trình chắc chắn không có bug.”** Không. Nó chứng minh một số thuộc tính được kiểm ở một boundary cụ thể, không bảo đảm mọi thuộc tính và side effect khác.

**“Invariant phải đúng sau từng instruction.”** Không nhất thiết. Nhiều invariant được quy định tại trạng thái công khai/quiescent.

**“Nếu có exception, tất cả hậu điều kiện vẫn phải đúng.”** Sai. Normal post và exceptional guarantee cần tách riêng.

**“Postcondition violation sẽ rollback state.”** Sai. Exception từ checker không tự thực hiện compensation.

**“Dùng after returning là luôn kiểm kết quả cuối caller nhận.”** Chưa chắc. Một advice ngoài có thể tiếp tục đổi kết quả.

**“Kiểm contract chỉ ở target đủ cho mọi aspect.”** Không khi advice thay đổi args/result/error hoặc side effect.

**“Aspect đặt ở proxy sẽ bao toàn bộ lời gọi method của class.”** Không nếu caller bypass proxy hoặc lời gọi nội bộ không đi qua interceptor.

**“Dùng Java assert là luôn kiểm hợp đồng trong production.”** Sai: assertion thường tắt mặc định, và không thay thế validation/bảo mật bắt buộc.

**“Một snapshot reference tới object mutable tương đương old-value.”** Không nếu object tiếp tục thay đổi.

**“Racing concurrent check chứng minh invariant sai.”** Chưa đủ nếu phép đọc không atomic hoặc không theo consistency model.

**“Advice chỉ logging không thể phá contract.”** Có thể ném lỗi, thay timing, reentrancy hoặc lộ dữ liệu.

**“Retry mọi ContractViolation sẽ tự sửa lỗi.”** Nguy hiểm: có thể nhân đôi side effect, che bug hoặc làm trạng thái lệch hơn.

**“Future trả bình thường đồng nghĩa task thành công.”** Không. Method return và completion là hai thời điểm khác nhau.

**“Checker kiểm đúng một thuộc tính nghĩa hệ thống phân tán đã nhất quán.”** Không. Phải xác định consistency window và phạm vi bảo đảm.

## 28. Bài tập luyện tập có đáp án

### Bài 1. Precondition ở ngoài hay trong normalization?

Public API cho phép `id = "  A  "` và cam kết tự trim. Target yêu cầu id đã trim. Đặt một contract check đòi “không có khoảng trắng” ở ngoài normalization có đúng không?

**Đáp án:** Không phù hợp public contract đã nêu. Public checker nên chấp nhận input được hứa hỗ trợ, còn target checker có thể yêu cầu giá trị đã normalize.

### Bài 2. Normal post sau exception?

Một method cam kết normal post `result == old + delta` nhưng ném `IOException` sau khi đã cập nhật state. Có bắt buộc normal post đúng không?

**Đáp án:** Không thể suy ra. Cần đọc exceptional guarantee. Có thể chỉ cam kết invariant cơ bản; một exception không tự rollback mutation.

### Bài 3. Lỗi do after advice?

Target trả result đúng, nhưng after advice đổi result thành một giá trị sai. Checker ở trong after advice ấy có phát hiện được sai phạm ở caller không?

**Đáp án:** Chưa chắc. Checker trong có thể chỉ thấy result trước biến đổi. Cần checker tại public boundary hoặc contract riêng cho advice đổi result.

### Bài 4. Retry sau postcondition violation?

Target đã ghi giao dịch xong, checker phát hiện postcondition sai và ném exception. Retry ngoài có nên gọi lại không?

**Đáp án:** Không tự động. Phải xác minh trạng thái và idempotency; ContractViolation thường chỉ ra lỗi cần sửa/điều tra chứ không phải transient failure mặc định.

### Bài 5. Bất biến trong helper?

Một helper tạm để `a=0` trước khi cập nhật `b`, sau đó khôi phục invariant `a+b==10` trước khi method public return. Dệt checker vào mọi helper exit có thể báo gì?

**Đáp án:** Có thể báo false positive nếu contract chỉ yêu cầu invariant tại public boundary/quiescent state.

### Bài 6. Race condition?

T1 đọc old=10, cộng 5 xong; T2 cộng 2 trước khi T1 đọc new=17. T1 kiểm `new==old+5` thất bại. Có thể kết luận implementation của T1 sai không?

**Đáp án:** Chưa thể. Snapshot/post-check không nằm trong ranh giới atomic; cần consistency model hoặc synchronization thích hợp.

### Bài 7. Caller bypass proxy?

Một class có contract advice trên interface proxy nhưng caller gọi target tạo bằng `new`. Advice có chắc chạy không?

**Đáp án:** Không trong mô hình JDK proxy delegation; lời gọi không qua handler. Một công cụ weaving body có thể khác.

### Bài 8. After throwing đọc gì?

Một around advice trong biến `IOException` thành `DomainUnavailable`, ngoài nó có checker after throwing. Checker ngoài nhìn exception nào?

**Đáp án:** Nó có thể nhìn `DomainUnavailable` vì đó là outcome của continuation ở lớp ngoài. Muốn tìm `IOException` có thể cần `getCause()` nếu lỗi gốc được bảo toàn.

### Bài 9. Java `assert` bị tắt?

Nếu code chỉ dùng `assert amount > 0` và runtime không bật assertion, có được bảo đảm input luôn được từ chối khi `amount=0` không?

**Đáp án:** Không. Validation bắt buộc phải dùng cơ chế luôn được thực thi ở boundary phù hợp.

### Bài 10. Fallback có hợp đồng không?

Một phương thức cập nhật trả `receiptId`. Around advice bắt mọi exception và trả `"OK"`. Đúng kiểu `String` nghĩa đã đúng hợp đồng chưa?

**Đáp án:** Chưa. Receipt phải có ý nghĩa xác nhận hợp lệ; lỗi có thể xảy ra sau side effect hoặc trước commit. Cần contract rõ cho nhánh fallback hoặc không cung cấp fallback giả thành công.

## 29. Checklist trước khi đưa Contract Aspect vào sử dụng

- [ ] Có văn bản precondition của public API.
- [ ] Có normal postcondition với kết quả/trạng thái và dữ liệu old cần thiết.
- [ ] Có invariant ở đúng boundary, không kiểm bừa trạng thái trung gian.
- [ ] Có exceptional guarantee riêng, không giả định rollback.
- [ ] Đã phân biệt target contract, advice contract và composed/public contract.
- [ ] Biết advice nào có thể sửa args, result, exception hoặc trạng thái.
- [ ] Đã vẽ thứ tự/precedence giữa contract checker và các aspect khác.
- [ ] Không vô tình kiểm hậu điều kiện trước khi advice ngoài sửa result.
- [ ] Không retry ContractViolation trừ khi có lý do và cơ chế an toàn cụ thể.
- [ ] Có snapshot old-state không bị thay đổi và đúng concurrency model.
- [ ] Kiểm tra tràn số/precision/timezone khi predicate liên quan dữ liệu ấy.
- [ ] Contract check ít side effect, không gây reentrancy ngoài ý muốn.
- [ ] Không dùng một global mutable state để giữ context giữa invocation.
- [ ] Có test direct target, self-invocation và phạm vi weaving/proxy.
- [ ] Có test lỗi business, lỗi advice, lỗi post và lỗi cleanup.
- [ ] Có test violation sau mutation để thấy state không tự rollback.
- [ ] Có test concurrent/async nếu ranh giới hoạt động liên quan.
- [ ] Có chính sách error type, cause/suppressed và telemetry an toàn.
- [ ] Bảo vệ thông tin nhạy cảm khi log contract context.
- [ ] Có cơ chế xử lý object ở trạng thái không hợp lệ sau violation.
- [ ] Có đo overhead snapshot/checker trên workload thực tế.
- [ ] Không dựa vào Java assertions có thể tắt cho validation bắt buộc.
- [ ] Có quy trình đổi contract cùng version và regression tests.
- [ ] Xác định quyền tắt/bật checker cho từng loại hợp đồng.
- [ ] Bảo đảm invariant cốt lõi vẫn được bảo vệ ở trust boundary đáng tin.

## 30. Tổng kết

**Precondition nói khi nào thao tác được phép chạy; postcondition nói thao tác cam kết điều gì sau khi hoàn tất theo nhánh được định nghĩa; invariant nói trạng thái nào được coi là hợp lệ ở các ranh giới quan sát thích hợp.**

Trong AOP, các điều kiện ấy phải được xem là **hợp đồng của cả đường thực thi**, không chỉ hợp đồng của thân phương thức nghiệp vụ. Before advice có thể từ chối trước target; around advice có thể thay input, bỏ `proceed`, thử lại hoặc trả fallback; after advice có thể đổi result hoặc ném lỗi sau khi trạng thái đã thay đổi. Bởi vậy thứ tự advice, phạm vi try/catch, nơi kiểm tra old/new state và loại exception đều ảnh hưởng tính đúng đắn.

Một giải pháp bền vững cần kết hợp **hợp đồng được viết rõ**, **kiểm tra ở ranh giới phù hợp**, **các bảo đảm riêng cho đường ngoại lệ**, **kiểm thử cả đường bypass và side effect**, cùng hiểu biết về concurrent/async execution. Việc phát hiện vi phạm là bằng chứng cần xử lý — **không phải bằng chứng thao tác được hoàn tác**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-exception-contracts-002">Bảo toàn kiểu trả về và ý nghĩa giá trị trả về</a>

<details>
<summary>Xem chi tiết</summary>

# Bảo toàn kiểu trả về và ý nghĩa giá trị trả về

## 1. Vì sao “return đúng kiểu” vẫn chưa đủ?

Trong **Aspect-Oriented Programming (AOP)**, advice can thiệp vào các **join point** được pointcut chọn. Đặc biệt với **around advice**, cơ chế có thể cho phép advice nhận kết quả của `proceed()`, trả kết quả đó về caller, thay thế nó, hoặc không gọi continuation mà tự quyết định giá trị trả về.

Đây là quyền rất mạnh, nhưng cũng là nguồn lỗi khó nhận ra: một method có thể **trả về một giá trị đúng kiểu ở mức ngôn ngữ** nhưng **sai hợp đồng nghiệp vụ**. Ví dụ một method trả `int` để biểu diễn số lượng:

- Trả `0` có thể nghĩa là *không có bản ghi phù hợp*.
- Trả `-1` có thể là một mã trạng thái hợp lệ theo hợp đồng cũ.
- Trả `7` có thể là số phần tử thực.
- Ném exception có thể nghĩa là *không thể tính kết quả*, hoàn toàn khác với trả `0`.

Nếu một advice gặp lỗi rồi tự ý trả `0`, kiểu `int` vẫn hợp lệ nhưng caller có thể tưởng rằng phép tính thành công và kết quả thực là không có bản ghi. Một lời gọi được **che lỗi bằng giá trị hợp lệ về mặt cú pháp** có thể nguy hiểm hơn việc ném lỗi rõ ràng.

Bài này làm rõ hai câu hỏi tách biệt:

1. **Type correctness:** giá trị do advice trả có phù hợp với declared return type, runtime cast/unboxing, generic contract và abstraction của method không?
2. **Semantic correctness:** giá trị ấy có giữ đúng ý nghĩa success, absence, failure, identity, side effect và lifecycle mà caller được hứa không?

**Nguyên tắc trung tâm:** Một advice quan sát thuần túy nên **giữ nguyên result và error của target**, trừ khi chính sách biến đổi đã được định nghĩa và kiểm thử rõ. Một advice có chủ đích thay kết quả phải giữ cả **hợp đồng kiểu lẫn hợp đồng ý nghĩa**, không chỉ làm cho compiler hết báo lỗi.

### Mục tiêu học tập

Sau bài bạn có thể:

1. Phân biệt **declared return type**, kiểu runtime, generic type, dữ liệu được trả và ý nghĩa nghiệp vụ.
2. Phân tích around advice, after-returning, short-circuit, caching, fallback và retry trên kết quả trả về.
3. Hiểu các trường hợp **primitive, boxed type, `void`, `null`, `Optional<T>`, collection, generic, covariant return**.
4. Phân biệt **trả handle bất đồng bộ** với **task completion**, giữ semantics của `CompletionStage` và callback.
5. Nhận diện lỗi do **unboxing null**, **runtime cast**, **generic erasure**, **nuốt exception**, **đổi identity** và **làm mất chính sách lỗi**.
6. Hiểu giới hạn và hành vi của **JDK dynamic proxy/reflection**, nhưng không nhầm nó với mọi engine AOP.
7. Thiết kế quy tắc **transparency** cho advice quan sát và **explicit transformation** cho advice thay kết quả.
8. Chạy ví dụ Java 17 có đầu ra xác định để thấy primitive, `Optional`, `void`, future và các lỗi kiểu.
9. Xây ma trận kiểm thử bảo toàn result/error trên happy path, failure, cache hit, async và concurrency.

Bài thuộc AOP nói chung, không dựa vào cú pháp của một sản phẩm cụ thể. Cách một engine kiểm tra type hoặc cho phép `proceed`/return transformation có thể khác nhau.

## 2. Hợp đồng trả về thực sự gồm những lớp nào?

Một method có thể được mô tả ở nhiều tầng:

| Tầng hợp đồng | Câu hỏi | Ví dụ |
| --- | --- | --- |
| **Declared type** | Chữ ký phương thức công bố kiểu gì? | `int count()`, `Optional<Item> find()`. |
| **Runtime representation** | Caller/proxy nhận object nào, có cast/unbox không? | `int` có thể được biểu diễn bằng `Integer` khi đi qua reflection. |
| **Nullability** | `null` có hợp lệ không? | `String` có thể là nullable theo hợp đồng; `Optional<T>` thường không nên là `null`. |
| **Generic shape** | Phần tử/giá trị bên trong có kiểu hợp lệ không? | `List<String>` không nên chứa `Integer`. |
| **Domain meaning** | Mỗi giá trị có ý nghĩa gì? | `0` = không có kết quả, không phải request thất bại. |
| **Identity/mutability** | Có phải cùng object/reference hoặc phải bảo toàn trạng thái không? | Stream, entity, iterator, mutable DTO. |
| **Side effects** | Target được chạy bao nhiêu lần, làm gì? | Advice cache có thể bỏ qua target; retry có thể chạy lại. |
| **Error contract** | Lỗi là exception đồng bộ hay được mã hóa trong result? | Throw, `Result.failure`, exceptional future. |
| **Temporal contract** | Khi nào result “sẵn sàng”? | Future return trước khi task complete. |
| **Ownership/lifecycle** | Ai đóng, hủy hoặc giải phóng? | Stream, cursor, publisher, resource handle. |

Một advice chỉ giữ được **declared type** chưa chắc giữ được các tầng còn lại.

### 2.1. Ví dụ: cùng kiểu `boolean`, nghĩa khác nhau

Một method `boolean transfer(...)` có thể quy định:
- `true`: chuyển thành công;
- `false`: yêu cầu được xử lý nhưng không đủ điều kiện;
- exception: hệ thống chưa biết liệu thao tác có commit hay không.

Nếu advice bắt mọi lỗi rồi trả `false`, nó đã đánh đồng **bị từ chối có chủ đích** với **hệ thống không thể xác nhận kết quả**. Compiler không phát hiện sai lệch ngữ nghĩa này.

### 2.2. Ví dụ: cùng kiểu `List<Item>`, số lượng và thứ tự khác nhau

Advice trả `Collections.emptyList()` thay cho một danh sách có dữ liệu: đúng kiểu tham chiếu nhưng sai nội dung. Advice sắp xếp lại danh sách: phần tử có thể vẫn đúng nhưng hợp đồng “giữ thứ tự nhập” bị vi phạm. Advice trả **cùng reference** thay vì bản sao hoặc ngược lại có thể ảnh hưởng caller khi collection mutable.

### 2.3. Không phải mọi biến đổi đều sai

Một **mapping/normalization advice** có thể được thiết kế để:
- Loại dữ liệu nhạy cảm theo chính sách;
- Chuyển một kết quả thành dạng chuẩn;
- Trả dữ liệu từ cache;
- Áp quy tắc fallback rõ ràng;
- Bọc kết quả thành một abstraction khác nếu method signature cho phép.

Nhưng ở đó, **thay đổi result chính là hành vi có chủ đích**, cần được công bố, định nghĩa thứ tự và kiểm thử, không được gọi nhầm là “advice chỉ quan sát”.

## 3. Before, after và around khác nhau ở quyền xử lý return

### 3.1. Before advice

Before advice chạy trước phần tiếp theo. Trong một mô hình đơn giản, nó **không trực tiếp nhận result của target đã hoàn thành**, bởi result chưa tồn tại. Nó có thể chuẩn bị context, kiểm tra điều kiện hoặc ném lỗi, tùy khả năng công cụ.

Nếu before advice ném lỗi trước `proceed`, target có thể chưa chạy. Đó là thay đổi **execution contract**, dù không trực tiếp đổi kiểu của một giá trị trả về.

### 3.2. After-returning advice

After-returning quan sát một lần kết thúc bình thường. Nó có thể được dùng để log kiểu, tính metric hoặc phân loại success. Tuy nhiên, một số AOP API chỉ **cung cấp quyền quan sát**, không cho phép thay result chỉ bằng cách gán lại parameter nhận được. Một engine khác có thể hỗ trợ transformation rõ ràng.

**Không suy:** “đổi biến `result` bên trong after-returning thì caller chắc chắn nhận giá trị mới”. Nếu muốn thay kết quả, phải sử dụng đúng abstraction mà engine bảo đảm có tác động lên caller, và test thực tế.

### 3.3. After-throwing advice

Nếu target ném exception đồng bộ, after-throwing có thể quan sát exception thoát khỏi join point. Nhưng nếu around advice phía trong đã bắt lỗi rồi trả fallback, tầng ngoài thấy **normal return**, không tự thấy lỗi gốc.

Khi method trả future, một exception được báo **bên trong future ở completion về sau** cũng không tự trở thành exception đồng bộ thoát khỏi method invocation.

### 3.4. Around advice

Một around advice có thể mô tả logic:

~~~text
around:
    before()
    try:
        original = proceed()
        observeResult(original)
        return original
    catch failure:
        observeError(failure)
        throw sameFailure
    finally:
        cleanup()
~~~

Đây là **giả mã**, không phải cú pháp chung. Nó minh họa **transparent observer**: result được trả nguyên, lỗi được ném lại, cleanup không cố tình biến đổi outcome.

Nếu thay `return original` bằng `return newValue`, ta phải trả lời:
- `newValue` có hợp lệ với declared return type?
- `null` có hợp lệ với caller?
- Tính bất biến/thứ tự/identity có đổi không?
- Có làm mất lỗi hoặc tạo false success không?
- Nếu target trả stream/future, có giữ lifecycle và cancellation không?
- Caller đã được thông báo việc transform như một phần hợp đồng chưa?

### 3.5. `proceed()` không phải phép bảo toàn ngữ nghĩa tự động

Một số around advice **có thể không gọi `proceed`** (cache hit, rate limit, fallback) hoặc gọi nhiều lần (retry, nếu engine cho phép). Một advice khác có thể sửa arguments trước khi gọi tiếp. Vì vậy ngay cả khi giá trị cuối đúng kiểu, **số lần target thực thi và side effects** cũng có thể thay đổi.

Một advice retry không được giả định mọi method idempotent. Hai lần gọi có thể tạo hai giao dịch hoặc hai thông báo dù cuối cùng trả về cùng một `String` thành công.

## 4. Primitive và boxed type: đúng giá trị, đúng cách biểu diễn

Java có kiểu nguyên thủy như `boolean`, `byte`, `short`, `int`, `long`, `float`, `double`, `char`. Reflection/proxy thường truyền các giá trị primitive thông qua **wrapper objects** như `Integer` hoặc `Boolean` tại tầng `Object`.

Với method:

~~~java
interface Counter {
    int getCount();
}
~~~

Một `InvocationHandler` của **JDK dynamic proxy** có thể trả `Integer.valueOf(5)` để biểu diễn `int 5`. Generated proxy thực hiện cast/unboxing phù hợp khi trả về caller. Nhưng:

- Trả `null` cho method `int` làm quá trình unboxing thất bại với `NullPointerException`.
- Trả `String "5"` cho method `int` không tự parse thành số; có thể gây `ClassCastException`.
- Trả một wrapper **không đúng kiểu** như `Long` cho method `int` cũng không tự được coi là phép đổi kiểu primitive hợp lệ ở boundary JDK proxy.
- Trả `Integer.valueOf(0)` là **hợp lệ về kiểu**, nhưng `0` có đúng ý nghĩa không vẫn do domain contract quyết định.

Các điểm trên được nêu chính xác trong phạm vi **JDK dynamic proxy**. Một bytecode weaver khác có thể kiểm tra type tại compile time hoặc từ chối một transformation theo cơ chế khác; không suy rằng mọi engine đều báo đúng loại exception ở cùng chỗ.

### 4.1. Cẩn thận với kiểu số

`int` và `long` khác nhau về miền giá trị. Nếu advice tự chuyển `long` thành `int`, dữ liệu có thể tràn/thu hẹp. Nếu advice dùng `double` để thay `BigDecimal`, mất độ chính xác có thể làm sai nghiệp vụ tài chính. Chữ ký compile-time phù hợp không tự bảo đảm precision, rounding và unit semantics.

### 4.2. `boolean` không đồng nghĩa “không throw”

Trả `false` thay exception có thể đánh đồng một kết quả hợp lệ với việc hệ thống thất bại. Nếu API định nghĩa một sentinel như `-1`, không được đoán sentinel mới từ primitive default chỉ vì advice không biết trả gì.

### 4.3. Giữ nguyên `0`, `false` và `-1` khi quan sát thuần túy

Một advice metrics có thể cần phân biệt:
- Target trả `0`: bình thường, có kết quả bằng 0;
- Target trả `-1`: bình thường nếu domain có sentinel đó;
- Target ném lỗi: không có giá trị trả về bình thường.

Không tự đặt cùng nhãn “error” cho mọi số không dương, trừ khi hợp đồng method nói rõ.

## 5. `void`: không trả giá trị nghiệp vụ vẫn có hợp đồng

Một method `void notifyUser(...)` **không cung cấp giá trị return cho caller**. Nhưng nó có thể có side effects: ghi dữ liệu, phát event, gửi email hoặc thay đổi trạng thái.

Trong JDK reflection, `Method.invoke` trả `null` khi method trả `void` và hoàn tất bình thường. Trong JDK dynamic proxy, `InvocationHandler` có kiểu `Object invoke(...)`; trả `null` cho `void` là cách tự nhiên.

**Không được suy:** `null` trong tầng reflection có nghĩa target “trả một dữ liệu null”. Với `void`, caller không nhận bất kỳ giá trị nghiệp vụ nào để đọc.

### 5.1. `void` vẫn có success/failure

Nếu method `void save()` ném `IOException`, around advice quan sát cần ném lại đúng lỗi theo hợp đồng. Nếu advice nuốt lỗi và return bình thường, caller có thể tưởng side effect đã thành công dù thực tế không hoàn tất.

### 5.2. Không nhầm “không có result” với “không có side effect”

Một cache advice không thể mặc định bỏ qua một method `void` và trả ngay chỉ vì không có result cần cache. Target có thể đang thực hiện side effect quan trọng; skip là hành vi nghiệp vụ, không phải phép tối ưu an toàn mặc định.

## 6. Reference type và `null`: có thể hợp kiểu nhưng vi phạm contract

Với kiểu tham chiếu `String`, `Customer`, `List<Item>`, `Optional<Item>` hay `CompletionStage<T>`, `null` là một reference value có thể đi qua runtime cast mà không gây `ClassCastException` ngay lập tức.

Điều này **không chứng minh null hợp lệ về mặt API**.

Ví dụ một API:

~~~java
Optional<String> find(String id);
~~~

Có thể công bố:

> “Luôn trả một `Optional` không-null; `Optional.empty()` nghĩa là không tìm thấy.”

Nếu advice khi cache miss trả `null`, lời gọi **vẫn có thể qua boundary kiểu** vì `Optional` là reference type. Nhưng caller sau đó gọi `result.isPresent()` sẽ gặp `NullPointerException` hoặc phải viết phòng vệ trái với hợp đồng. `null` và `Optional.empty()` không tương đương.

### 6.1. `Optional.empty()` không phải error

Một `Optional.empty()` thường biểu diễn **không có giá trị thành công** theo API; một exception biểu diễn **không thực hiện được việc tìm kiếm** có thể là trạng thái khác. Advice không nên chuyển mọi exception thành empty chỉ vì method trả Optional.

Ví dụ việc tra tài khoản:
- `Optional.empty()`: tài khoản không tồn tại.
- Exception từ storage: không thể xác định tồn tại hay không.
- `Optional.of(account)`: có tài khoản hợp lệ.

Che lỗi storage bằng empty có thể dẫn đến quyết định sai như “tạo tài khoản mới vì chưa tồn tại”.

### 6.2. Nullable contract phải được ghi rõ

Một method `String title()` có thể cho phép `null` nếu không có tên, hoặc có thể hứa không-null. Advice muốn biến đổi dữ liệu phải biết điều đó; annotation nullability (nếu có) cũng chỉ là một phần của hợp đồng, cần được công cụ/QA thật sự sử dụng.

### 6.3. `null` có thể là dữ liệu hợp lệ trong một abstraction

Không nên cực đoan “không bao giờ trả null”. Với API legacy, `null` có thể là sentinel được công bố. Một advice transparent observer phải **giữ nguyên null hợp lệ**; tự thay thành chuỗi rỗng hoặc object mặc định cũng là đổi semantics.

## 7. Generics, type erasure và kiểu phần tử

Java generics thường có **type erasure** tại runtime. Method:

~~~java
List<String> names();
~~~

có thể bị một around advice/reflective interceptor trả một `List<Integer>` ở tầng raw `Object` mà không có cast kiểm tra từng phần tử ngay tại boundary. Caller sau đó đọc phần tử dưới giả định `String` mới gặp lỗi.

~~~java
// Minh họa không độc lập.
@SuppressWarnings({"rawtypes", "unchecked"})
List<String> incorrect = (List) List.of(123);
String first = incorrect.get(0); // có thể ClassCastException lúc đọc
~~~

Đoạn này chỉ minh họa cơ chế erasure và unchecked conversion, **không phải khuyến nghị** dùng raw types. Sự tồn tại của generics trong chữ ký không bảo đảm một proxy runtime trả object đúng nội dung nếu advice cố ý dùng cast không an toàn.

### 7.1. Generic container có nhiều tầng cần bảo toàn

Với `Map<String,List<Order>>`, hợp đồng bao gồm:
- Key thực là `String`.
- Values chứa `List<Order>`.
- Quy tắc null key/value nếu được cho phép.
- Thứ tự duyệt nếu API hứa.
- Mutability/immutability.
- Khả năng serialization.
- Các invariant của `Order`.

Không thể chứng minh đầy đủ bằng một `instanceof Map` duy nhất.

### 7.2. Type parameters phụ thuộc context

Một method generic như `<T> T decode(byte[], Class<T>)` cần duy trì quan hệ giữa đối số `Class<T>` và result `T`. Nếu advice trả object sai kiểu từ cache do dùng key chỉ theo bytes mà bỏ qua requested type, lỗi có thể xuất hiện về sau ở caller.

### 7.3. Covariant return types

Java cho phép override method với **return type là subtype hợp lệ**. Ví dụ interface công bố `Number read()`, implementation trả `Integer`. Một advice có thể quan sát result dưới kiểu `Number` nhưng nếu lời gọi thật yêu cầu một API trả `Integer` thì không được thay bằng `Double` chỉ vì cả hai đều là `Number`.

Khi viết interceptor/reflection matcher, chú ý chữ ký method quan sát, bridge methods và kiểu trả về thực; type matching của engine phụ thuộc loại join point và declaration mà nó nhìn thấy.

## 8. Identity, equality, mutability và ownership

Ngay cả khi two values có cùng kiểu và `equals()`, caller vẫn có thể yêu cầu điều kiện mạnh hơn.

### 8.1. Identity có thể là một phần của hợp đồng

Ví dụ method `getBuffer()` trả một **buffer dùng chung do owner quản lý**. Advice tạo một buffer mới cùng nội dung thay vì trả reference gốc có thể khiến code cần identity hoặc mutation sharing bị sai.

Ngược lại, API có thể yêu cầu **defensive copy** để caller không sửa state nội bộ. Advice thay result thành reference gốc sẽ phá tính cách ly.

**Câu hỏi cần kiểm tra:** caller hứa nhận cùng instance, một bản sao độc lập, một immutable view hay chỉ một giá trị tương đương?

### 8.2. Collection rỗng cũng có thể khác nhau

`List.of()` và `new ArrayList<>()` đều là `List` rỗng, nhưng mutability khác nhau. Nếu caller được phép thêm phần tử, thay một mutable list bằng immutable list có thể tạo `UnsupportedOperationException` sau khi method return.

### 8.3. Không lén sửa object của target trong một “observer”

Nếu advice log result rồi đồng thời gọi `result.clear()` hoặc mutate một DTO, dù advice cuối cùng `return result`, nghĩa của result đã đổi. “Trả đúng reference” **không đủ** để chứng minh trong suốt nếu advice làm biến đổi trạng thái của object ấy.

### 8.4. Resource ownership

Một result như `InputStream`, cursor, iterator hoặc file handle có thể cần caller đóng. Advice không nên tự đóng trong `finally` ngay sau khi target return nếu caller còn cần đọc; cũng không nên giữ result/reference vô hạn trong logger/cache. Lifetime và ownership là một phần của hợp đồng trả về.

## 9. Proxy và reflection: nơi type error thật sự xảy ra

Với **JDK dynamic proxy**, một `InvocationHandler` thường nhận:
- Object proxy.
- `Method` đại diện invocation.
- Mảng arguments.
- Kết quả ở dạng `Object` mà handler phải trả.

Nhiều interface methods có primitive return types. Generated proxy kiểm tra/cast/unbox kết quả `Object` theo declared return type khi trả cho caller.

Mô hình khái niệm:

~~~text
caller calls proxy.number()
    |
    v
InvocationHandler.invoke()
    |
    v
reflection calls target.number()
    |
    +--> target returns int 7
             |
             v
          Method.invoke returns Integer(7)
             |
             v
          handler returns Integer(7)
             |
             v
          proxy unboxes to int 7
             |
             v
          caller sees 7
~~~

### 9.1. Reflective invocation bọc exception

Khi `Method.invoke(target,args)` khiến target ném exception, reflection thường ném `InvocationTargetException`, chứa exception gốc qua `getCause()` hoặc `getTargetException()`. Một handler quan sát trung thực thường **gỡ wrapper reflection** và truyền lại lỗi gốc theo quy định interface/proxy.

Nếu handler tự ý ném một checked exception **không được khai báo** trong throws clause của interface method, JDK proxy có thể bọc bằng `UndeclaredThrowableException`. Đây là một phần của **exception contract**; ví dụ dưới chỉ dùng unchecked exceptions để tránh rối phần checked throws.

### 9.2. Sai kiểu của handler có thể bị phát hiện sau khi handler return

Nếu handler trả `"7"` cho method `int number()`, handler tự nó có thể hoàn tất bình thường, nhưng generated proxy gặp cast không phù hợp. Trong ví dụ Java, caller bắt `ClassCastException`.

Nếu handler trả `null`, generated proxy unbox thành primitive thất bại; caller bắt `NullPointerException`.

Nếu handler trả `null` cho `Optional<String>`, runtime cast có thể thành công vì null là một reference hợp lệ về mặt JVM, nhưng **null phá vỡ semantic contract** API mong đợi một Optional object.

### 9.3. Không đồng nhất JDK proxy với mọi cơ chế AOP

Một weaver làm việc tại source/bytecode có thể dùng typed advice API, kiểm tra lúc compile/load, chèn cast hoặc tạo bridge khác. Nếu công cụ cấm một loại kết quả, lỗi có thể xuất hiện sớm hơn. Nguyên tắc chung vẫn là **phải giữ kiểu và hợp đồng mà caller được hứa**, nhưng hình thức kiểm tra và loại lỗi phải dựa trên engine thật.

## 10. Java 17 runnable: bảo toàn và vi phạm hợp đồng ở proxy boundary

Chương trình sau dùng một interface có bốn kiểu return:

- `int number(String)`: primitive, `0` là giá trị thành công hợp lệ.
- `Optional<String> find(String)`: luôn trả `Optional` không-null, empty biểu diễn không tìm thấy.
- `CompletionStage<String> loadAsync(String)`: trả một handle, outcome có thể xuất hiện sau method return.
- `void emit(String)`: không có result nghiệp vụ, nhưng target có side effect.

`transparent` handler gọi đúng target, log giá trị/kiểu, đăng ký quan sát async mà **trả stage gốc**, và gỡ `InvocationTargetException` để giữ lỗi đồng bộ. Cuối chương trình dùng ba proxy cố ý sai để thấy `null` cho primitive, sai wrapper type và `null` cho `Optional`.

Tạo file **`AdviceReturnContractDemo.java`**:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

public class AdviceReturnContractDemo {
    interface Service {
        int number(String key);
        Optional<String> find(String key);
        CompletionStage<String> loadAsync(String key);
        void emit(String event);
    }

    static final class RealService implements Service {
        private CompletableFuture<String> pending;

        @Override
        public int number(String key) {
            System.out.println("TARGET number " + key);
            if ("BAD".equals(key)) {
                throw new IllegalArgumentException("illegal key");
            }
            return "ZERO".equals(key) ? 0 : 7;
        }

        @Override
        public Optional<String> find(String key) {
            System.out.println("TARGET find " + key);
            if ("MISSING".equals(key)) {
                return Optional.empty();
            }
            return Optional.of("DATA:" + key);
        }

        @Override
        public CompletionStage<String> loadAsync(String key) {
            System.out.println("TARGET loadAsync " + key);
            pending = new CompletableFuture<>();
            return pending;
        }

        @Override
        public void emit(String event) {
            System.out.println("TARGET emit " + event);
        }

        CompletableFuture<String> pending() {
            return pending;
        }

        void completeSuccess(String value) {
            CompletableFuture<String> stage = pending;
            pending = null;
            stage.complete(value);
        }

        void completeFailure() {
            CompletableFuture<String> stage = pending;
            pending = null;
            stage.completeExceptionally(
                    new IllegalStateException("remote failed"));
        }
    }

    static Service transparent(Service target) {
        return (Service) Proxy.newProxyInstance(
                Service.class.getClassLoader(),
                new Class<?>[] { Service.class },
                (proxy, method, arguments) -> {
                    System.out.println("ADVICE enter " + method.getName());
                    try {
                        Object value = method.invoke(target, arguments);

                        if (value instanceof CompletionStage<?> stage) {
                            stage.whenComplete((result, failure) -> {
                                if (failure == null) {
                                    System.out.println(
                                            "ADVICE async success " + result);
                                } else {
                                    System.out.println(
                                            "ADVICE async failure "
                                            + failure.getClass().getSimpleName());
                                }
                            });
                        }

                        String type = method.getReturnType() == void.class
                                ? "void"
                                : value == null
                                    ? "null"
                                    : value.getClass().getSimpleName();
                        System.out.println("ADVICE return "
                                + method.getName() + " type=" + type);
                        return value;
                    } catch (InvocationTargetException wrapped) {
                        Throwable cause = wrapped.getCause();
                        System.out.println("ADVICE throw "
                                + method.getName() + " "
                                + cause.getClass().getSimpleName());
                        throw cause;
                    }
                }
        );
    }

    static Service alwaysReturn(Object result) {
        return (Service) Proxy.newProxyInstance(
                Service.class.getClassLoader(),
                new Class<?>[] { Service.class },
                (proxy, method, args) -> result
        );
    }

    public static void main(String[] args) {
        RealService target = new RealService();
        Service service = transparent(target);

        System.out.println("CALLER number=" + service.number("ZERO"));
        System.out.println("CALLER missing=" + service.find("MISSING"));
        System.out.println("CALLER found=" + service.find("YES"));

        service.emit("EVT");

        CompletionStage<String> task = service.loadAsync("JOB");
        System.out.println("CALLER original stage="
                + (task == target.pending()));
        target.completeSuccess("READY");
        System.out.println("CALLER async result="
                + task.toCompletableFuture().join());

        CompletionStage<String> failed = service.loadAsync("ASYNC_BAD");
        target.completeFailure();
        try {
            failed.toCompletableFuture().join();
        } catch (CompletionException error) {
            System.out.println("CALLER async failure cause="
                    + error.getCause().getClass().getSimpleName());
        }

        try {
            service.number("BAD");
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER sync failure=" + error.getMessage());
        }

        try {
            alwaysReturn(null).number("ZERO");
        } catch (NullPointerException error) {
            System.out.println("BAD primitive=null -> NullPointerException");
        }

        try {
            alwaysReturn("not-an-integer").number("ZERO");
        } catch (ClassCastException error) {
            System.out.println("BAD wrong type -> ClassCastException");
        }

        System.out.println("BAD Optional returned null="
                + (alwaysReturn(null).find("MISSING") == null));
    }
}
~~~

Biên dịch/chạy bằng **JDK 17 trở lên**:

~~~bash
javac AdviceReturnContractDemo.java
java AdviceReturnContractDemo
~~~

**Stdout kỳ vọng:**

~~~text
ADVICE enter number
TARGET number ZERO
ADVICE return number type=Integer
CALLER number=0
ADVICE enter find
TARGET find MISSING
ADVICE return find type=Optional
CALLER missing=Optional.empty
ADVICE enter find
TARGET find YES
ADVICE return find type=Optional
CALLER found=Optional[DATA:YES]
ADVICE enter emit
TARGET emit EVT
ADVICE return emit type=void
ADVICE enter loadAsync
TARGET loadAsync JOB
ADVICE return loadAsync type=CompletableFuture
CALLER original stage=true
ADVICE async success READY
CALLER async result=READY
ADVICE enter loadAsync
TARGET loadAsync ASYNC_BAD
ADVICE return loadAsync type=CompletableFuture
ADVICE async failure IllegalStateException
CALLER async failure cause=IllegalStateException
ADVICE enter number
TARGET number BAD
ADVICE throw number IllegalArgumentException
CALLER sync failure=illegal key
BAD primitive=null -> NullPointerException
BAD wrong type -> ClassCastException
BAD Optional returned null=true
~~~

### 10.1. Đường `int number("ZERO")`

Target trả **`int 0`**. Vì `Method.invoke` biểu diễn primitive qua wrapper ở tầng `Object`, handler ghi `Integer`, rồi trả lại đúng object ấy. Proxy unbox thành `int 0`; caller in `CALLER number=0`.

**Điều được bảo toàn:** số 0 không bị coi là “không có result” hoặc lỗi. Advice không ép thành `null` và không tự thay `0` bằng một số khác.

### 10.2. Đường `Optional.empty()`

Target trả một object `Optional.empty()`, handler thấy kiểu runtime `Optional` rồi trả nguyên. Caller nhận đúng abstraction để biểu diễn absence.

Với `find("YES")`, result là `Optional[DATA:YES]`. **Hai kết quả đều là return bình thường**, không đồng nghĩa một cái thành công và cái còn lại thất bại. Cách diễn giải absence/failure thuộc hợp đồng method.

### 10.3. Đường `void emit("EVT")`

Target in `TARGET emit EVT`, tức đã thực thi side effect minh họa. Reflection trả `null` cho method `void`, nhưng handler in `type=void` dựa vào **declared return type**, không gắn nhầm nhãn “target trả null như một giá trị nghiệp vụ”.

### 10.4. Đường `CompletionStage` thành công

`loadAsync` tạo `CompletableFuture` còn pending. Handler đăng ký `whenComplete` **chỉ để quan sát** và trả `value` gốc. Caller so sánh identity `task == target.pending()` và in `true`. Lúc `target.completeSuccess("READY")`, callback log `ADVICE async success READY`, sau đó caller `join()` đọc kết quả.

Trong demo tuần tự, callback không dùng hậu tố `Async` và completion do `main` kích hoạt nên stdout có thứ tự nêu trên. Không suy ra mọi callback `whenComplete` trong ứng dụng đa luồng luôn chạy trên caller thread hoặc luôn sắp trước một log ở thread khác.

### 10.5. Đường `CompletionStage` thất bại

`loadAsync("ASYNC_BAD")` **return bình thường** một `CompletableFuture`. Exception xuất hiện khi `completeExceptionally` được gọi. Handler callback ghi failure, còn caller bắt `CompletionException` lúc `join()`.

Đây **không** phải một synchronous throw của method `loadAsync`. Around advice chỉ catch `InvocationTargetException` của `Method.invoke`; nó không tự biến completion failure thành lỗi ném trước return.

### 10.6. Đường lỗi đồng bộ của `number("BAD")`

Target ném `IllegalArgumentException`. Reflection bọc lỗi trong `InvocationTargetException`. Handler gỡ `getCause()`, log `ADVICE throw number IllegalArgumentException` và ném lại cause. Caller thấy `IllegalArgumentException` kèm message `illegal key`.

Đây là **bảo toàn exception contract**, cũng là một phần của hợp đồng return/outcome. Nếu handler tự ý return `0`, caller sẽ mất khả năng phân biệt lỗi này với `number("ZERO")` hợp lệ.

### 10.7. Ba advice cố ý sai

**Sai primitive null:** handler cố ý trả `null` cho `int`; proxy unboxing gặp `NullPointerException`.

**Sai primitive wrapper type:** handler trả `String` cho `int`; generated proxy cast thất bại với `ClassCastException`.

**Sai `Optional` semantics:** handler trả `null` cho `Optional<String>`. Không có cast error ngay tại boundary vì đây là reference type, nên chương trình chứng minh `BAD Optional returned null=true`. Đây vẫn là **vi phạm hợp đồng** nếu API hứa luôn trả `Optional` không-null.

**Lưu ý giới hạn:** các bad proxies trong demo không gọi target, nên ngoài lỗi kiểu, chúng còn thay đổi side-effect semantics. Chúng được tạo tách biệt chỉ để minh họa vi phạm return contract.

### 10.8. Giới hạn của mã runnable

- `RealService.pending` chỉ phục vụ **một future pending tại một thời điểm** trong demo tuần tự. Không dùng cấu trúc đó làm job registry production concurrent.
- Các callbacks chỉ log, chưa có chính sách error handling cho chính instrumentation.
- Handler không xử lý chi tiết `equals`, `hashCode`, `toString` và các default interface methods; chỉ dùng bốn methods đã công bố.
- Không có bytecode weaving, pointcut DSL, cache/retry implementation hay kiểm chứng dynamic class loading.
- Code dùng `Optional<String>`, `CompletableFuture` và reflection theo đúng API Java 17, nhưng không giả định một engine AOP nào có cùng thứ tự advice hay behavior riêng.
- Mục đích ví dụ là phân biệt **runtime type safety** với **semantic safety**, không chứng minh GC hoặc thread-safe state.

## 11. Short-circuit, cache hit và chính sách trả kết quả

Một around advice có thể **không gọi `proceed`** khi cache hit:

~~~text
around:
    key = makeKey(arguments)
    if cache.contains(key):
        return cache.get(key)
    value = proceed()
    cache.put(key, value)
    return value
~~~

Đây là giả mã. Dù cache trả về một object đúng kiểu, vẫn phải xem:

1. Cache key có chứa đủ arguments, type information, tenant và security scope không?
2. Giá trị đã stale chưa?
3. Nếu original target return `null` hợp lệ, cache có phân biệt “không có entry” với “entry có null” không?
4. Nếu target trả mutable object, caller có làm thay đổi cached instance không?
5. Nếu target có side effects, bỏ qua target có được cho phép không?
6. Nếu result là stream/future/iterator sống theo invocation, cache handle gốc có an toàn không?
7. Cache có phân biệt success value với exception hoặc failure state không?
8. Nếu authorization advice nằm bên trong cache và cache short-circuit, có tạo đường bỏ qua policy không?

### 11.1. Không cache lỗi như giá trị thành công khi chưa có chính sách

Một API phân biệt `Optional.empty()` với exception lỗi storage; nếu advice cache ghi empty khi gặp exception, những lần sau sẽ thấy “không tồn tại” dù hệ thống chỉ vừa không truy cập được storage. Nếu muốn negative caching, phải định nghĩa rõ trạng thái nào được phép cache, TTL và invalidation.

### 11.2. Không cache resource handle tùy tiện

Một `InputStream` có thể đã được caller đọc hết/đóng; trả lại cùng stream instance từ cache cho caller tiếp theo thường phá lifecycle. Tương tự, một future đã failed không tự trở thành một task mới chỉ vì caller gọi lại method; caching future có thể cố tình chia sẻ một computation, nhưng phải là hành vi được thiết kế.

### 11.3. Identity và key equality

Nếu API hứa singleton-like identity cho một entity, cache có thể chủ đích giữ cùng instance. Nếu API hứa bản sao tách biệt cho mỗi caller, trả cùng mutable instance là sai dù nội dung ban đầu giống nhau.

## 12. Fallback và chuyển đổi lỗi thành giá trị

Một advice fallback có thể thay exception bằng result. Đó là một **thay đổi hợp đồng**, cần được biện minh.

Ví dụ ba mức:

~~~text
(1) Target returns 0
    -> successful outcome value 0

(2) Target throws IllegalArgumentException
    -> synchronous exceptional outcome

(3) Target returns CompletionStage that later fails
    -> normal method return of handle;
       exceptional completion later
~~~

Nếu fallback đưa (2) hoặc (3) về `0`, caller mất thông tin khác biệt, trừ khi API **cố ý định nghĩa** fail-closed/default result và đã ghi nhận error bằng kênh khác.

### 12.1. Phân loại failure trước khi fallback

Một fallback hợp lý có thể áp riêng cho:
- Lỗi tạm thời từ dependency có kết quả dự phòng được công bố.
- Cache stale được phép dùng trong một khoảng.
- Trường hợp giá trị không có nhưng API rõ ràng coi đó là absence hợp lệ.

Nó không nên ngẫu nhiên bắt mọi `Throwable` và trả default. Những lỗi lập trình, hỏng invariant, authentication failures hoặc commit ambiguities có semantics riêng.

### 12.2. Giữ nguyên cause khi ném lại

Nếu advice chỉ log exception rồi rethrow, ưu tiên giữ **type, cause và stack information** theo hợp đồng. Nếu chính sách phải chuyển exception, tài liệu hóa mapping và giữ cause khi phù hợp. Tránh biến một lỗi domain có thể xử lý thành một unchecked generic error vô nghĩa.

### 12.3. Checked exceptions qua proxy

Một interface method khai báo `throws IOException` không đồng nghĩa handler có thể tự ném **bất kỳ checked exception** nào và caller luôn nhận trực tiếp đúng loại ấy. Với JDK dynamic proxy, checked exception không khai báo/không hợp lệ qua method signature có thể được bọc trong `UndeclaredThrowableException`. Cần test thực tế nếu advice biến đổi checked errors.

## 13. Retry: cuối cùng trả đúng giá trị vẫn có thể sai side effects

Giả sử advice retry gọi `proceed()` ba lần cho cùng logical operation và cuối cùng trả `"OK"`. Kiểu `String` hợp lệ, giá trị cuối trông đúng, nhưng có thể đã:
- Gửi ba thông báo.
- Ghi ba bản ghi.
- Tạo nhiều giao dịch.
- Thực hiện một remote action đã commit trước khi response timeout.

Do đó **return contract bao gồm quan hệ giữa result và những tác động được phép**. Chỉ nên retry khi xác định idempotency, attempt boundary, timeout tổng, backoff, loại lỗi được phép thử lại và tác vụ nền có thực sự được hủy hay không.

### 13.1. Số lượng advice calls thay đổi theo thứ tự

Với chuỗi:

~~~text
Retry(
    MetricsAttempt(
        Target
    )
)
~~~

MetricsAttempt có thể chạy **mỗi attempt**. Với:

~~~text
MetricsLogical(
    Retry(
        Target
    )
)
~~~

MetricsLogical có thể chạy **một lần cho logical operation**, nếu engine cho phép Retry gọi continuation nhiều lần. Lỗi nội bộ đã được Retry xử lý có thể không thoát ra MetricsLogical; không gán nhầm một success cuối với zero attempt failures.

### 13.2. Retry future khác retry synchronous throw

Nếu method trả một future trước khi tác vụ lỗi, catch quanh `proceed()` không tự nhận completion failure. Để retry logical async work, cần compose theo future completion, tạo **attempt mới** có chủ đích, không chỉ `join()` lại cùng future đã fail.

## 14. `CompletionStage`: kiểu handle không nói hết outcome

Một method:

~~~java
CompletionStage<Receipt> submitAsync(Order order);
~~~

khai báo trả `CompletionStage<Receipt>`. Hợp đồng thường tách hai giai đoạn:

1. **Invocation outcome:** method có return một stage hay ném lỗi ngay?
2. **Task outcome:** stage hoàn tất success với `Receipt`, exceptionally, cancellation hoặc timeout?

Một handler around trả **đúng stage object** vẫn có thể là một transparent observer ở method boundary. Nếu muốn quan sát task outcome, nó cần completion hook tương ứng.

### 14.1. Stage gốc và stage phụ thuộc không luôn tương đương

Trong Java:

~~~java
// Mã minh họa: giả định stage và observe(...) đã được định nghĩa.
CompletionStage<Receipt> original = stage;
original.whenComplete((value, failure) -> observe(value, failure));
return original;
~~~

Trong ví dụ này, observer đăng ký một callback và **trả stage gốc**. Method `whenComplete` lại **tạo một dependent stage**; code bỏ qua dependent stage trong minh họa.

Nếu đổi thành:

~~~java
// Mã minh họa: return một dependent stage.
return stage.whenComplete((value, failure) -> observe(value, failure));
~~~

caller nhận **dependent stage**, không phải original. Nếu `observe` ném lỗi, outcome của dependent stage có thể thay đổi theo hợp đồng `whenComplete`. Đây không chỉ là chuyện kiểu vì cả hai cùng là `CompletionStage<Receipt>`; **identity và completion semantics có thể khác**.

### 14.2. `handle` có thể chuyển failure thành success

~~~java
// Mã minh họa.
return stage.handle((value, failure) -> {
    if (failure != null) {
        return fallbackReceipt;
    }
    return value;
});
~~~

Đây là một transformation có chủ đích. Nếu nghiệp vụ cho phép fallback, cần công bố nghĩa của `fallbackReceipt`; nếu không, nó che failure. Ngoài ra, generic inference phải bảo đảm result type và mọi code paths cùng thuộc hợp đồng `Receipt`.

### 14.3. Không block để “biến async thành sync” khi chưa được phép

Một advice gọi `stage.toCompletableFuture().join()` chỉ để lấy result rồi trả value có thể:
- Block thread không được phép block.
- Phá latency và scheduling của caller.
- Biến lỗi future thành `CompletionException` đồng bộ.
- Không khớp declared return type nếu method vẫn khai báo `CompletionStage<T>`.
- Gây deadlock trong mô hình event loop hoặc pool cạn thread.

Nếu API đã hứa async handle, một advice quan sát thuần không nên tự biến nó thành synchronous wait.

### 14.4. Cancellation và timeout

Đổi future gốc thành dependent stage có thể làm **cancellation propagation** khác đi tùy abstraction. Cancel handle không tự bảo đảm worker I/O đã ngừng. Nếu caller có quyền cancel, time out, hoặc so identity của handle, một replacement cần được thiết kế theo hợp đồng đầy đủ chứ không chỉ generic type.

## 15. Stream, iterator, publisher: return type còn là giao thức sử dụng

Một method `Stream<Row> streamRows()` không nhất thiết tính toàn bộ dữ liệu trước khi return. Một `Iterator<T>` chỉ cung cấp phần tử khi caller gọi `next()`. Một publisher có thể lazy, chạy khi có subscriber/demand.

### 15.1. Không tiêu thụ result chỉ để log

Một advice muốn log “toàn bộ rows trả về” rồi tự thực thi terminal operation trên `Stream` có thể **tiêu thụ stream**, khiến caller không thể duyệt lại. Nó có thể giữ resource hoặc làm thay đổi side effects. Tương tự gọi `Iterator.next()` để kiểm tra có dữ liệu có thể làm mất phần tử đầu.

### 15.2. Biến stream thành list làm đổi semantics

Nếu advice gọi `toList()` rồi tạo stream mới:
- Lazy có thể thành eager.
- Bộ nhớ tiêu thụ khác.
- Timing và exception xuất hiện tại thời điểm khác.
- Resource close semantics có thể khác.
- Trật tự và parallel behavior có thể khác.

Chỉ làm nếu đó là transformation được API cho phép và có test.

### 15.3. Publisher không có một return value duy nhất

Publisher có thể phát nhiều values, onComplete/onError hoặc cancellation. Around advice chỉ bao method tạo publisher chưa chắc bao toàn bộ subscription lifecycle. Muốn bảo toàn contract phải tính cả số lần subscribe, backpressure, ordering, terminal signals và resource cleanup.

## 16. Thứ tự nhiều aspect và ý nghĩa result

Hai around advices có thể tương tác:

~~~text
Normalize(
    Cache(
        Target
    )
)
~~~

Hoặc:

~~~text
Cache(
    Normalize(
        Target
    )
)
~~~

Nếu Normalize thay đổi result, vị trí của Cache quyết định nó giữ **raw value** hay **normalized value**. Nếu cache key phụ thuộc arguments đã biến đổi hoặc identity của principal, còn nhiều ràng buộc nữa.

### 16.1. Authorization trước short-circuit

Nếu một cache advice ngoài trả ngay mà không vào authorization advice bên trong, chính sách quyền có thể bị skip. Không được kết luận một ordering đủ đảm bảo bảo mật nếu caller có đường bypass proxy/target; cần trusted boundary và test.

### 16.2. Exception mapping và logging

Nếu một advice trong chuyển exception `E` thành fallback value, advice ngoài nhận **normal return**. Một after-throwing của tầng ngoài không tự thấy `E`. Nếu hệ thống cần metric attempt failures, phải thu ở vị trí còn quan sát được lỗi gốc.

### 16.3. Giữ logical outcome nhất quán

Khi nhiều advice cùng biến đổi result, cần thiết lập thứ tự tường minh, đơn vị success/failure, và chính sách ai có quyền sửa:
- Result normalization.
- Cache.
- Retry/fallback.
- Security masking.
- Auditing.
- Metrics/tracing.

Một pipeline biến đổi mà mỗi advice nghĩ mình là “observer” rất dễ tạo semantics không dự đoán được.

## 17. Thiết kế contract cho advice quan sát thuần

Một **transparent observer** có thể được đánh giá theo các invariant sau:

1. **Return identity:** nếu target trả object `R`, advice trả đúng `R` trừ khi API cho phép tạo equivalent wrapper.
2. **Primitive value:** giữ nguyên `0`, `false`, negative values và mọi sentinel hợp lệ.
3. **Nullability:** giữ `null` nếu đó là result hợp lệ; không tự chèn `null` vào API hứa non-null.
4. **Reference content:** không mutate result/arguments ngoài mục tiêu quan sát.
5. **Exceptional behavior:** không tự swallow, map hoặc che lỗi gốc nếu không được phép.
6. **Execution count:** gọi continuation đúng một lần nếu observer không có chức năng skip/retry.
7. **Temporal behavior:** không biến future/stream lazy thành synchronous/eager ngoài ý định.
8. **Ownership:** không đóng result resource trước khi caller dùng xong.
9. **Thread/context:** không giữ request context lâu vô hạn; dọn scope đúng khi throw/return.
10. **Instrumentation failure:** chính sách rõ khi logger/metric sink lỗi; không vô tình thay outcome của nghiệp vụ.
11. **Security/privacy:** không log dữ liệu nhạy cảm chỉ để ghi kiểu/giá trị.
12. **Concurrency:** không giữ `lastResult` hoặc `startTime` trong shared mutable field gây race.
13. **Performance:** không deep-copy/serialize toàn object graph nếu chỉ cần một metric.
14. **Engine contract:** kiểm tra kiểu advice được phép thay result và những method/join point nó thực sự nhận.

### 17.1. Một observer không nhất thiết tuyệt đối “không có side effect”

Logging và metrics vốn là side effects quan sát. Cụm “transparent” ở đây nghĩa là **không cố tình đổi outcome/side effects nghiệp vụ theo hợp đồng đã công bố**. Vẫn phải xử lý rủi ro log I/O blocking, privacy, exception do instrumentation và resource usage.

### 17.2. Nếu cần thay result, công bố transformation rõ

Một advice thay result nên có hợp đồng riêng:
- Điều kiện được thay.
- Mapping từ original → transformed.
- Cách xử lý `null`, absence, exception.
- Identity/mutability/ordering.
- Async/cancellation/stream lifecycle.
- Failure path khi transformation tự lỗi.
- Testing chứng minh caller nhận đúng semantics.

## 18. Kiểm thử: không chỉ assert kiểu trả về

### 18.1. Ma trận test cơ bản

| Nhóm | Target | Expected từ observer | Lỗi cần phát hiện |
| --- | --- | --- | --- |
| Primitive zero | `int 0` | Caller nhận `0`. | Thay bằng null hoặc default khác. |
| Primitive false | `boolean false` | Caller nhận `false`. | Coi false là lỗi và throw. |
| Primitive negative | `int -1` hợp lệ theo domain | Caller nhận `-1`. | Tự normalize thành 0. |
| Primitive wrong wrapper | `int` nhưng handler trả `String` | Test xác nhận proxy báo type error. | Tin rằng `"7"` tự cast thành int. |
| Nullable reference | `null` được phép | Giữ null. | Thay thành empty string trái contract. |
| Non-null Optional | `Optional.empty()` | Giữ Optional object, không null. | Trả null rồi lỗi về sau. |
| Generic collection | `List<String>` | Giữ kiểu phần tử, thứ tự và mutability. | Raw list chứa Integer. |
| Identity-sensitive | Trả object dùng chung | Giữ same instance nếu API hứa. | Tạo copy khác identity. |
| Defensive-copy API | Trả bản sao | Vẫn không lộ internal mutable reference. | Advice trả state gốc. |
| Void | Side effect rồi normal return | Không invent result hay skip side effect. | Nuốt lỗi hoặc bỏ qua target. |
| Sync exception | Target throw loại E | Caller thấy E theo contract. | Swallow/map vô cớ. |
| Async pending | Trả stage còn pending | Caller nhận handle đúng, chưa báo task success. | Chặn join hoặc kết luận success sớm. |
| Async exceptional | Stage fail sau return | Callback quan sát lỗi; caller nhận failure. | Biến failure thành fake success. |
| Async identity | Stage original được hứa | Giữ original khi observer. | Trả dependent stage sai contract. |
| Short-circuit cache | Có cached value hợp lệ | Không chạy target theo policy, value đúng. | Cache sai key/identity/permissions. |
| Retry | Nhiều attempts | Kết quả cuối và side effects theo policy. | Lặp tác dụng không idempotent. |
| Stream/publisher | Lazy resource | Giữ thứ tự/lifecycle/terminal signals. | Advice tiêu thụ trước caller. |

### 18.2. Test với cùng input hai đường gọi

Nếu có một proxy/decorator và target gốc, test so sánh:
- Result của `target.method(args)`.
- Result của `proxy.method(args)`.
- Exception/cause nếu target throw.
- Số lần target side effects xảy ra.
- Nếu contract yêu cầu identity, dùng `==`; nếu yêu cầu value equivalence, kiểm `equals` và semantic constraints.

Đối với cache/retry policy cố ý làm khác target, thay vì so tất cả bằng nhau, ghi **expected transformation** rõ theo từng nhánh.

### 18.3. Test golden path chưa đủ

Một advice có thể pass với giá trị `7` nhưng fail với `0`, `null`, empty collection, exception hoặc future exceptional. Cần test **điểm biên** và negative cases.

### 18.4. Property-based/metamorphic tests

Nếu observer phải trong suốt, có thể kiểm tra trên nhiều inputs:
- Khi target return bình thường, proxy return cùng value/reference theo policy.
- Khi target throw, proxy truyền cùng exception type/cause.
- Target được gọi đúng một lần.
- Advice không mutate arguments hoặc result.
- Nullable/generic contract được giữ.

Nếu targets có nondeterminism (thời gian, random, network), dùng controlled doubles thay vì so hai lời gọi thật có thể có side effects khác nhau.

### 18.5. Concurrency và async tests

Dùng `CompletableFuture` chưa hoàn tất rồi tự `complete`/`completeExceptionally` để test chính xác method-return và task-completion. Với threads, dùng synchronization primitives thay `Thread.sleep` đoán thời gian. Đảm bảo completion callback không giữ raw request/args lâu hơn nhu cầu, không trộn result của invocation A với B.

## 19. Lỗi phổ biến khi xử lý result trong advice

**“Chỉ cần return cùng kiểu Java là an toàn.”** Sai. Có thể phá nullability, sentinel, identity, side effects và error meaning.

**“Primitive int có thể được thay bằng null khi không có dữ liệu.”** Sai đối với JVM return primitive của proxy; unboxing null có thể gây NPE.

**“Trả String `"7"` cho method int là Java tự đổi kiểu.”** Sai trong JDK dynamic proxy; có thể ClassCastException.

**“`Optional.empty()` và null là như nhau.”** Sai. Optional là abstraction có hợp đồng không-null tùy API, null có thể phá caller.

**“Method trả Optional mà gặp exception thì trả empty cho đơn giản.”** Không nếu exception nghĩa là kết quả chưa biết do storage failure.

**“Void nghĩa là không có việc gì để bảo toàn.”** Sai. Side effects và exception contract vẫn tồn tại.

**“Một `List<String>` luôn được runtime kiểm tra từng phần tử ngay khi proxy return.”** Sai; type erasure có thể đẩy lỗi tới lúc đọc phần tử.

**“Result `equals()` nhau nghĩa là việc đổi reference luôn vô hại.”** Sai nếu identity, mutability hoặc ownership có ý nghĩa.

**“Advice trả chính result reference thì không thể đổi dữ liệu.”** Sai nếu advice đã mutate object trước return.

**“Dùng `join()` trên future trong around sẽ luôn an toàn.”** Không; có thể block, deadlock hoặc đổi thời điểm lỗi.

**“Stage gốc và stage từ `whenComplete` luôn có cùng hợp đồng.”** Không mặc định; callback error và identity có thể khác.

**“After-returning cho phép thay return bằng cách gán một biến local.”** Không phải mọi engine đều có semantics đó.

**“Bắt mọi lỗi rồi return `0` là bảo toàn kiểu.”** Có thể hợp kiểu nhưng thay lỗi thành false success.

**“Cache hit được phép skip mọi advice bên trong.”** Về cơ chế có thể skip; về bảo mật/semantics phải bảo đảm policy không bị bypass.

**“Retry chỉ ảnh hưởng thời gian chờ.”** Sai; có thể lặp side effects và thay số invocation.

**“Stream chỉ là một List lười, đọc thử để log cũng được.”** Không; có thể tiêu thụ stream và làm hỏng lifecycle.

**“Một lần pass integration test chứng minh AOP tương thích mọi caller.”** Không; còn direct-target bypass, self-invocation, overload, generic bridges và classloader/pointcut scopes.

## 20. Bài tập vận dụng và đáp án

### Bài 1 — Giá trị `0`

`countActive()` trả `0` khi không có mục nào hoạt động. Around advice trả `null` để biểu diễn “không có dữ liệu”. Có đúng không?

**Đáp án:** Không. Method trả primitive int; trong JDK proxy có thể NPE do unboxing. Quan trọng hơn, `0` là **giá trị hợp lệ** phải được giữ.

### Bài 2 — Che lỗi bằng `false`

`boolean isAuthorized()` ném exception vì permission service không khả dụng. Advice bắt lỗi rồi trả `false`. Điều gì thay đổi?

**Đáp án:** Trả `false` có thể biểu diễn từ chối có căn cứ, còn exception nghĩa là không xác minh được. Đó là mapping policy phải có chủ đích, không phải observer transparent.

### Bài 3 — `Optional` và mất dữ liệu

Một `Optional<Account>` được trả từ DB, DB lỗi nhưng advice return `Optional.empty()`. Caller có thể làm sai điều gì?

**Đáp án:** Caller tưởng tài khoản không tồn tại và thực hiện thao tác tạo mới hoặc xử lý sai khác. Phải phân biệt absence với inability-to-query.

### Bài 4 — Raw list

Advice trả `List<Integer>` qua interface khai báo `List<String>` bằng raw cast. Tại sao caller có thể chỉ lỗi khi đọc phần tử?

**Đáp án:** Java generics erasure; cast container không bảo đảm kiểm từng element.

### Bài 5 — Identity

Method hứa trả cùng một token object cho các lời gọi; advice clone object mỗi lần. Kiểu có đúng không và hợp đồng ra sao?

**Đáp án:** Kiểu có thể đúng nhưng identity promise bị phá. Cần bảo toàn same reference nếu được API yêu cầu.

### Bài 6 — Void method bị skip

Một advice cache thấy method return void nên quyết định không gọi `proceed`. Rủi ro?

**Đáp án:** Side effect của method có thể bị bỏ qua hoàn toàn; return type void không làm side effects trở nên tùy chọn.

### Bài 7 — Two futures

Một handler đăng ký `whenComplete` rồi trả **dependent stage** thay stage gốc, callback đôi khi ném lỗi. Tại sao caller có thể thấy outcome khác?

**Đáp án:** Dependent stage có thể exceptional do callback; dù generic return type giống nhau, stage identity và completion semantics khác.

### Bài 8 — Synchronous vs asynchronous failure

Method async trả future bình thường rồi future fail sau 1 giây. After-throwing dành cho invocation method có tự nhận lỗi đó không?

**Đáp án:** Không. Method invocation đã return handle bình thường; cần quan sát completion nếu muốn thấy lỗi task.

### Bài 9 — Retry “thành công”

Advice retry ghi dữ liệu hai lần vì lần đầu response timeout sau commit. Caller cuối nhận OK. Type/result đã bảo toàn toàn bộ contract chưa?

**Đáp án:** Không chắc; có thể duplicate side effects. Cần idempotency/transaction guarantees.

### Bài 10 — Stream log

Advice nhận `Stream<Order>`, gọi `count()` rồi return stream gốc. Caller gọi terminal operation sau. Rủi ro gì?

**Đáp án:** Stream đã bị tiêu thụ, thường không thể dùng terminal operation lần hai. Advice cần quan sát mà không tiêu thụ pipeline, hoặc dùng abstraction được thiết kế rõ.

### Bài 11 — Fallback checked exception

Interface method khai `throws IOException`, handler tự ném một checked exception khác không khai báo. Có bảo đảm caller thấy trực tiếp loại mới không?

**Đáp án:** Không với JDK dynamic proxy; có thể xuất hiện `UndeclaredThrowableException`. Checked error mapping phải theo method signature/proxy contract.

### Bài 12 — Mutation dù cùng reference

Around advice nhận `List<String>`, gọi `clear()` rồi return chính list đó. Có phải transparent observer vì `return original`?

**Đáp án:** Không. State của result đã bị thay đổi; cùng identity không có nghĩa cùng semantic value.

## 21. Checklist trước khi triển khai advice thao tác với result

1. **Signature:** Method declared return type là gì: primitive, reference, void, generic, future hay stream?
2. **Type validation:** Engine/proxy kiểm tra runtime cast/unboxing ở đâu?
3. **Nullability:** `null` hợp lệ không, Optional/Result có nghĩa riêng không?
4. **Domain meaning:** `0`, `false`, empty, sentinel và absence được định nghĩa ra sao?
5. **Failure:** Exception là state khác return value không, có nguyên nhân cần giữ không?
6. **Identity:** Caller cần same instance, snapshot, defensive copy hay chỉ value equivalence?
7. **Mutability:** Advice có mutate arguments/result hay thay ordering của collection không?
8. **Execution count:** Advice có skip hoặc retry khiến side effects thay đổi không?
9. **Cache:** Key đầy đủ, security scope/tenant, TTL và ownership có đúng không?
10. **Async:** Method return-handle và task completion có phân biệt không?
11. **Future stage:** Caller nhận original hay dependent stage; cancellation/callback failure thế nào?
12. **Lazy result:** Stream/publisher/iterator có bị tiêu thụ trước caller không?
13. **Resources:** Ai sở hữu handle; khi nào được close/dispose?
14. **Exception mapping:** Có vô tình thay checked type hoặc cause không?
15. **Ordering:** Các advice khác có transform result/failure trước hoặc sau advice này không?
16. **Interception:** Caller có thể bypass proxy hoặc join point nằm ngoài weaving scope không?
17. **Privacy:** Có log raw result chứa dữ liệu nhạy cảm không?
18. **Thread safety:** Result/context có bị giữ trong shared aspect field không?
19. **Performance:** Có deep-copy/serialize/block I/O chỉ để quan sát không?
20. **QA:** Có test zero/false/null/empty, error, generic, identity, cache hit, async và stream?
21. **Production evidence:** Có integration tests với đúng engine/JDK/classloader và artifact được triển khai không?

## Kết luận

Trong AOP, **bảo toàn kiểu trả về** là yêu cầu tối thiểu để caller tiếp tục sử dụng method theo chữ ký, nhưng **bảo toàn ý nghĩa giá trị trả về** mới là yêu cầu đầy đủ của hợp đồng. Một advice có thể sai dù return đúng generic container, đúng class, hoặc đúng primitive wrapper: nó vẫn có thể che exception, biến `Optional.empty()` thành `null`, phá identity/mutability, làm mất side effect, tiêu thụ stream, hoặc thay future gốc bằng một dependent stage có lifecycle khác.

Với **advice quan sát thuần túy**, ưu tiên **trả nguyên result, bảo toàn lỗi, không mutate dữ liệu và không thay số lần target chạy**. Với **advice được phép transform** như cache, fallback, retry hay normalization, phải công bố chính sách về type, nullability, error, identity, side effects, resource ownership và completion. Hãy kiểm chứng các mốc có thể thất bại — primitive unboxing, generics erasure, checked exceptions, async completion — bằng test thực tế thay vì chỉ nhìn compiler pass. Một thiết kế AOP tốt không chỉ giữ cho method “trả được gì đó”, mà giữ nguyên điều **caller có quyền tin** khi nhận kết quả ấy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-exception-contracts-003">Giữ nguyên hoặc chuyển đổi ngoại lệ có chủ đích</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ nguyên hoặc chuyển đổi ngoại lệ có chủ đích trong AOP

## 1. Ngoại lệ là một phần của hợp đồng

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming – AOP)**, *advice* có thể quan sát hoặc bao quanh một **join point** được **pointcut** lựa chọn. Khi phương thức thành công, caller nhận **giá trị trả về**; khi thất bại, caller nhận **ngoại lệ** hoặc một cách biểu diễn thất bại khác. Cả hai cùng thuộc **hợp đồng hành vi**.

Một aspect có thể ghi nhận lỗi, truyền tiếp nguyên trạng, đổi lỗi hạ tầng thành lỗi domain, thử lại hoặc trả kết quả dự phòng. Nhưng việc “bắt được exception” **không tạo quyền thay đổi ngữ nghĩa của API tùy ý**. Nếu method vốn báo `IOException` mà advice biến thành `null` hoặc một exception khác không được hợp đồng chấp nhận, caller có thể xử lý sai dữ liệu, sai quyền hoặc sai trạng thái giao dịch.

**Nguyên tắc:** **Giữ nguyên ngoại lệ gốc** là lựa chọn mặc định khi không có lý do cụ thể để thay. **Chuyển đổi** chỉ nên xảy ra tại ranh giới trừu tượng có chính sách rõ, giữ được chẩn đoán, kiểu dữ liệu và những điều caller có quyền kỳ vọng.

> Bài này trình bày **AOP cơ bản và nâng cao nói chung**, không phụ thuộc bất kỳ công cụ cụ thể nào. “Around”, “after-throwing”, “proceed”, “method call/execution” là khái niệm AOP; API và quyền thay đổi outcome của từng công cụ có thể khác. Java 17+ được dùng để minh họa những giới hạn kiểu ngoại lệ có thể kiểm chứng.

## 2. Năm quyết định khác nhau đối với một Throwable

| Quyết định | Outcome tới caller | Khi phù hợp |
| --- | --- | --- |
| **Preserve** | Chính ngoại lệ gốc được throw | Lỗi vẫn đúng contract, advice chỉ quan sát |
| **Observe and rethrow** | Lỗi gốc sau khi log/metric/audit | Giữ failure semantics, thêm observability |
| **Translate / map** | Ngoại lệ mới có ngữ nghĩa khác, chứa `cause` gốc | Đi qua ranh giới storage → domain hoặc domain → API |
| **Recover** | Giá trị fallback hợp lệ thay cho exception | Contract cho phép phục hồi từ loại lỗi cụ thể |
| **Retry** | Nhiều lần `proceed` rồi return/throw cuối | Lỗi tạm thời, thao tác an toàn lặp, có giới hạn |

Một advice cũng có thể **chủ động ném lỗi mới** để chặn thao tác theo policy, nhưng đó là loại quyết định khác với việc target đã ném lỗi và advice chỉ đang xử lý nó.

### Mô hình outcome

```text
                   proceed()
                       |
                +------+------+
                |             |
         Return(value)    Throw(original)
                |             |
          preserve/map    preserve/map
              value       recover/retry
                |             |
                +------+------+
                       |
             Final outcome to caller
```

Việc thay `Throw(error)` bằng `Return(value)` là một thay đổi lớn của **hợp đồng nghiệp vụ**, ngay cả khi `value` đúng Java type.

## 3. Đọc hợp đồng của phương thức trước khi xử lý exception

Xét:

```java
// Khai báo minh họa, không phải chương trình độc lập.
String readDocument(String id) throws java.io.IOException;
```

Caller có thể dựa vào các điều kiện: dữ liệu trả về phải hợp lệ; lỗi I/O được truyền bằng `IOException` hoặc subtype; một tài liệu không tồn tại có thể có nghĩa riêng; side effect không được giả vờ hoàn tất.

**`throws` chỉ mô tả một phần contract.** Java checked exception kiểm tra compile-time cho một số loại lỗi; nó không mô tả đầy đủ nullability, semantic fallback, tính idempotent hay lỗi runtime.

| Nhóm Throwable | Ví dụ | Lưu ý |
| --- | --- | --- |
| Checked exception | `IOException`, `SQLException` | Cần tính tương thích với declared throws và caller |
| Unchecked exception | `IllegalArgumentException`, `IllegalStateException` | Không cần kê `throws`, nhưng vẫn thuộc behavior contract |
| Error | `OutOfMemoryError`, `LinkageError` | Không nên recover thành success trong một catch tổng quát |

Đừng dùng `catch (Throwable)` để biến mọi `Error` thành giá trị dự phòng. Một around advice có thể bắt `Throwable` nhằm **cleanup và rethrow đúng object**, nhưng quyền quan sát rộng không có nghĩa mọi lỗi đều phục hồi an toàn.

## 4. Giữ nguyên bằng `throw original`

Một advice chỉ quan sát thất bại có thể viết:

```java
// Pseudocode của around advice; proceed là API giả định của AOP engine.
try {
    return proceed();
} catch (Throwable original) {
    recordFailure(original);
    throw original;
}
```

Nếu `recordFailure` không ném lỗi khác, `throw original` bảo toàn:

- Loại Throwable và object identity.
- Message và `cause`.
- Stack trace đã gắn với original.
- Danh sách suppressed exceptions.
- Metadata riêng trên Throwable object.

Không tạo exception mới chỉ để thêm một đoạn message nếu caller đang cần đúng subtype hoặc identity gốc.

### Những phép biến đổi khác nhau

```java
// Minh họa đặt trong scope thích hợp.
throw original; // Giữ chính object gốc.

// Các biểu thức sau CHỈ nên dùng khi có quyết định mapping rõ:
// throw new IOException("mapped", original);
// throw new IllegalStateException("domain failure", original);
```

`new IOException("mapped", original)` giữ cause nhưng tạo **object exception mới** với stack trace của điểm mapping. `new IllegalStateException(...)` còn đổi nhóm checked/unchecked mà caller nhìn thấy.

**Không nhầm “có cause” với “không đổi ngoại lệ”.**

## 5. Ghi log rồi truyền tiếp: phân biệt observability và outcome

```text
Target throws IOException
          |
          v
Advice records event
          |
          v
Caller receives IOException
```

Một logging advice đúng kiểu quan sát **không bắt buộc làm thay đổi** return/throw. Nhưng bản thân logger hoặc audit sink có thể ném lỗi. Nếu nó thất bại ở `catch` hoặc `finally`, caller có thể thấy lỗi sink **thay vì ngoại lệ target**.

Hãy có chính sách:

- Với metric/log phụ trợ, có thể cần cô lập lỗi sink theo contract.
- Với audit bắt buộc, lỗi audit có thể phải làm thao tác thất bại.
- Không im lặng nuốt mọi Throwable, nhất là lỗi nghiêm trọng.
- Không log argument/return chứa mật khẩu, token hoặc dữ liệu nhạy cảm một cách vô điều kiện.
- Ghi riêng **lỗi gốc** và **lỗi ghi nhận** khi chẩn đoán.

### `finally` không bảo đảm preserve nếu chính nó throw

```text
Target throws OriginalError
    |
finally cleanup throws CleanupError
    |
Caller may receive CleanupError
```

**Dù có `finally`, outcome vẫn có thể bị thay đổi.** Cần thiết kế cleanup đúng, tránh che thất bại chính bằng một lỗi phụ không mong đợi.

## 6. Translation tại đúng abstraction boundary

Một chuyển đổi hợp lý:

```text
Storage method
  -> IOException("connection reset")
Domain boundary
  -> DocumentUnavailableException(cause=IOException)
Application caller
  -> xử lý "tài liệu chưa thể truy cập"
```

Một domain caller có thể không nên phụ thuộc vào chi tiết filesystem hoặc driver. Nhưng conversion phải có policy rõ:

1. Lỗi gốc nào thuộc category này?
2. Exception mới thể hiện ý nghĩa nghiệp vụ gì?
3. Caller biết bắt loại mới không?
4. Có cần giữ subtype phân biệt not-found, timeout, access-denied?
5. Cause chain có còn nguyên không?
6. Error code/retryability có được bảo toàn?
7. Message public có lộ thông tin hạ tầng không?
8. Có aspect nào đã map trước đó không?

### Mapping tốt giữ `cause`

```java
// Ví dụ Java trong method được phép throws IOException.
try {
    return storageRead();
} catch (java.io.IOException original) {
    throw new java.io.IOException(
            "Cannot load requested document",
            original);
}
```

Hàm `storageRead()` là hàm nghiệp vụ giả định; đoạn trên minh họa phép translation, chưa phải một chương trình độc lập. `getCause()` trên exception mới dẫn về original.

**Đừng chỉ copy chuỗi `original.getMessage()` rồi bỏ object Throwable.** Message có thể đổi theo locale hoặc version và không thay được cause.

## 7. Duy trì subtype và phân loại lỗi

`FileNotFoundException` là subtype của `IOException`. Nếu map tất cả `IOException` thành superclass `IOException` mới:

- Caller vẫn có thể bắt `IOException`.
- Nhưng caller bắt riêng `FileNotFoundException` có thể không còn bắt được loại đã phát sinh từ target.
- Các policy retry và metric dựa vào subtype có thể thay đổi.

Giữ cùng superclass **không nhất thiết giữ nguyên hợp đồng chi tiết**. Nếu domain cần phân biệt not-found, forbidden, transient outage và corrupt data, mapping nên giữ phân biệt ấy bằng một taxonomy rõ.

Không phân loại theo `getMessage().contains("timeout")` nếu có type/error code đáng tin cậy; chuỗi message không phải giao thức ổn định.

## 8. `cause` và suppressed exceptions

**Cause chain** mô tả quan hệ “ngoại lệ mới được tạo vì lỗi nào”. **Suppressed exceptions** mô tả những lỗi bổ sung, thường thấy khi dọn resource bằng `try-with-resources`.

```java
// Ví dụ ý tưởng; Resource là AutoCloseable do ứng dụng định nghĩa.
try (Resource resource = open()) {
    work(resource);
} // Nếu work và close đều lỗi, close có thể được suppressed.
```

Nếu mapping tạo `DomainError(message, original)`, suppressed exception gắn với `original` vẫn có thể được truy vết **qua cause chain**. Không mặc định `mapped.getSuppressed()` tự chứa cùng danh sách với `original.getSuppressed()`.

Một phép chuyển đổi giữ cause nhưng **không tự đảm bảo** cùng object identity hay suppressed trực tiếp. Khi cần thông tin chẩn đoán, đọc đúng từng tầng Throwable; đừng tự copy/cắt chuỗi stack trace.

## 9. Khả năng thay lỗi của từng loại advice

| Advice | Thích hợp | Điều không nên mặc định |
| --- | --- | --- |
| Before | Precondition, policy chặn trước | Đã có lỗi target để quan sát |
| After-returning | Quan sát thành công | Có thể thay lỗi target bằng return bất kỳ |
| After-throwing | Log/metric/phân loại lỗi ném ra | Có thể suppress lỗi chỉ bằng `return` của advice |
| After/finally | Dọn context, đo duration | Cleanup không bao giờ làm thay outcome |
| Around | Bao quanh `proceed`, preserve/map/recover/retry nếu được hỗ trợ | Mọi engine cho thay return/checked exception vô hạn |

**After-throwing thường chỉ được quyền quan sát lỗi**, còn **around** hay hook điều khiển outcome mới phù hợp khi cần biến throw thành return. Có engine cho advice ném exception mới; khả năng cụ thể phải kiểm theo công cụ.

**Thứ tự advice** cũng ảnh hưởng: nếu một outer around không `proceed`, những advice bên trong có thể không chạy và target có thể chưa hề phát sinh lỗi.

## 10. Ví dụ Java 17+ chạy được: preserve, map, recover và sai checked contract

Ví dụ dùng **Java Dynamic Proxy** để mô phỏng interceptor AOP, không dùng thư viện ngoài. Nó làm rõ:

- **PRESERVE:** caller nhận đúng object `IOException` gốc.
- **MAP_IO:** tạo `IOException` mới, giữ nguyên `cause`.
- **RECOVER_MISSING:** chỉ phục hồi loại lỗi tài liệu không tồn tại bằng một `String` dự phòng.
- **BAD_CHECKED:** handler ném `SQLException` không thuộc declared throws; Java Dynamic Proxy bọc thành `UndeclaredThrowableException`.

`BAD_CHECKED` là tình huống **cố tình vi phạm** để học về contract, không phải phương pháp triển khai nên dùng.

### 10.1. Tệp `AdviceExceptionContractDemo.java`

```java
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import java.sql.SQLException;

public class AdviceExceptionContractDemo {
    private static final IOException SENTINEL = new IOException("known");

    interface DocumentPort {
        String load(String key) throws IOException;
        String parse(String input);
    }

    static final class Documents implements DocumentPort {
        @Override
        public String load(String key) throws IOException {
            System.out.println("target.load " + key);
            return switch (key) {
                case "missing" ->
                        throw new FileNotFoundException("missing: " + key);
                case "offline" -> throw new IOException("offline");
                case "same" -> throw SENTINEL;
                default -> "DOC:" + key;
            };
        }

        @Override
        public String parse(String input) {
            System.out.println("target.parse " + input);
            if ("bad".equals(input)) {
                throw new IllegalArgumentException("invalid input");
            }
            return input.trim();
        }
    }

    enum Policy { PRESERVE, MAP_IO, RECOVER_MISSING, BAD_CHECKED }

    static final class Advice implements InvocationHandler {
        private final DocumentPort target;
        private final Policy policy;

        Advice(DocumentPort target, Policy policy) {
            this.target = target;
            this.policy = policy;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            String name = method.getName();
            System.out.println("advice.before " + name);
            try {
                try {
                    Object value = method.invoke(target, args);
                    System.out.println("advice.return " + name);
                    return value;
                } catch (InvocationTargetException wrapped) {
                    Throwable original = wrapped.getCause();
                    System.out.println("advice.throw "
                            + original.getClass().getSimpleName());

                    if (policy == Policy.MAP_IO
                            && original instanceof IOException) {
                        throw new IOException(
                                "mapped storage failure", original);
                    }

                    if (policy == Policy.RECOVER_MISSING
                            && original instanceof FileNotFoundException
                            && "load".equals(name)) {
                        System.out.println("advice.recover missing");
                        return "DEFAULT";
                    }

                    if (policy == Policy.BAD_CHECKED
                            && original instanceof IOException) {
                        throw new SQLException(
                                "different checked", original);
                    }

                    throw original;
                }
            } finally {
                System.out.println("advice.finally " + name);
            }
        }
    }

    static DocumentPort wrap(DocumentPort target, Policy policy) {
        return (DocumentPort) Proxy.newProxyInstance(
                DocumentPort.class.getClassLoader(),
                new Class<?>[]{DocumentPort.class},
                new Advice(target, policy));
    }

    public static void main(String[] args) throws IOException {
        DocumentPort target = new Documents();

        System.out.println("== preserve ==");
        DocumentPort keep = wrap(target, Policy.PRESERVE);
        System.out.println("caller " + keep.load("ok"));

        try {
            keep.load("same");
        } catch (IOException error) {
            System.out.println(
                    "caller sameIdentity " + (error == SENTINEL));
        }

        try {
            keep.parse("bad");
        } catch (IllegalArgumentException error) {
            System.out.println("caller runtime " + error.getMessage());
        }

        System.out.println("== map ==");
        try {
            wrap(target, Policy.MAP_IO).load("offline");
        } catch (IOException error) {
            System.out.println(
                    "caller mapped " + error.getMessage());
            System.out.println(
                    "caller cause " + error.getCause().getMessage());
        }

        System.out.println("== recover ==");
        System.out.println("caller "
                + wrap(target, Policy.RECOVER_MISSING).load("missing"));

        System.out.println("== bad checked ==");
        try {
            wrap(target, Policy.BAD_CHECKED).load("offline");
        } catch (UndeclaredThrowableException error) {
            System.out.println("caller wrapper "
                    + error.getClass().getSimpleName());
            System.out.println("caller underlying "
                    + error.getUndeclaredThrowable()
                            .getClass().getSimpleName());
        }
    }
}
```

### 10.2. Biên dịch và chạy

```bash
javac --release 17 -encoding UTF-8 AdviceExceptionContractDemo.java
java AdviceExceptionContractDemo
```

**Standard output đã được đối chiếu bằng JDK:**

```text
== preserve ==
advice.before load
target.load ok
advice.return load
advice.finally load
caller DOC:ok
advice.before load
target.load same
advice.throw IOException
advice.finally load
caller sameIdentity true
advice.before parse
target.parse bad
advice.throw IllegalArgumentException
advice.finally parse
caller runtime invalid input
== map ==
advice.before load
target.load offline
advice.throw IOException
advice.finally load
caller mapped mapped storage failure
caller cause offline
== recover ==
advice.before load
target.load missing
advice.throw FileNotFoundException
advice.recover missing
advice.finally load
caller DEFAULT
== bad checked ==
advice.before load
target.load offline
advice.throw IOException
advice.finally load
caller wrapper UndeclaredThrowableException
caller underlying SQLException
```

### 10.3. Đọc nhánh preserve

`Documents.load("same")` ném **`SENTINEL`**. Reflection bọc lỗi target bằng `InvocationTargetException`; handler lấy `wrapped.getCause()` rồi `throw original`. Vì caller method khai báo `throws IOException`, proxy truyền Throwable `IOException` phù hợp. Phép so sánh `error == SENTINEL` trả `true`.

**SENTINEL chỉ dùng cho bài kiểm thử identity.** Trong sản xuất, không nên tái sử dụng một object Throwable chung cho những lỗi độc lập vì stack trace/state/diagnostics có thể gây nhầm.

### 10.4. Đọc nhánh mapping

Policy `MAP_IO` nhận `IOException("offline")` và tạo `IOException("mapped storage failure", original)`. `IOException` mới vẫn phù hợp declared throws, nhưng **khác object gốc**. `getCause()` dẫn về lỗi ban đầu, giúp caller/diagnostics biết nguyên nhân.

### 10.5. Đọc nhánh recover

`FileNotFoundException` là subtype của `IOException`. Advice chỉ recover khi error đúng subtype và method có tên `load`, rồi trả `"DEFAULT"`. Đây là một **quyết định nghiệp vụ giả định**. Nếu contract coi “thiếu tài liệu” là lỗi bắt buộc hoặc fallback có thể gây hiểu nhầm, policy này **không được dùng**, dù Java type vẫn hợp lệ.

### 10.6. Đọc nhánh sai checked exception

Policy `BAD_CHECKED` cố ném `SQLException` từ handler cho `DocumentPort.load`. Method interface chỉ khai báo `throws IOException`, không khai báo `SQLException`. **Java Dynamic Proxy** sẽ bọc checked exception không được khai báo tương thích trong `UndeclaredThrowableException`.

Đây là **quy tắc của Java Dynamic Proxy**, không phải lời khẳng định mọi kỹ thuật bytecode weaving có cùng cách bọc. Cần kiểm thử engine/interceptor thực tế.

### 10.7. Vì sao `finally` xuất hiện trên mọi đường?

`finally` của handler chạy khi target return, khi propagate/map và khi recover. Nó chỉ in log để minh họa. Nếu `finally` ném một exception khác, Throwable caller thấy có thể bị thay — do đó code sản xuất phải thiết kế cleanup và logging failure policy có chủ đích.

### 10.8. Giới hạn demo

Đây là proxy trên interface. **Lời gọi trực tiếp vào target bỏ qua handler**. Ví dụ không triển khai pointcut DSL, không weave bytecode target, không retry, không xử lý async completion. Nó chứng minh contract trong một cơ chế Java SE cụ thể, không đại diện mọi engine AOP.

## 11. Checked exception và giới hạn của API

`String load(String) throws IOException` cho phép caller bắt `IOException` và subtype. Một handler khai báo `throws Throwable` **không tự mở rộng danh sách checked exceptions** của method interface.

Trong Java:

- `IOException` có thể được ném từ `load`.
- `FileNotFoundException` có thể được ném vì thuộc `IOException`.
- `SQLException` không thuộc hệ `IOException`.
- `RuntimeException`/`Error` không phải declared checked exceptions.
- Override method chịu quy tắc Java riêng về danh sách checked exceptions được khai báo.
- Caller có thể viết `catch` cho subtype có ý nghĩa cụ thể.

Nếu map `FileNotFoundException` thành superclass `IOException` khác, caller bắt subtype có thể ngừng hoạt động như trước. **Phải phân biệt hợp lệ tại proxy boundary với bảo toàn toàn bộ contract đã được tài liệu hóa.**

## 12. Wrapper exception: unwrap có chọn lọc

| Wrapper | Nơi thường gặp | Cần lưu ý |
| --- | --- | --- |
| `InvocationTargetException` | `Method.invoke` hoặc constructor reflection | `getCause()` là Throwable target thực sự ném |
| `UndeclaredThrowableException` | Java Dynamic Proxy với checked exception ngoài declared throws | `getUndeclaredThrowable()` lấy lỗi bên trong |
| `ExecutionException` | `Future.get()` | Lỗi tác vụ nằm trong `getCause()` |
| `CompletionException` | Completion stage hoặc `join()` | Có thể là lớp bọc cho thất bại async |
| Domain exception wrapper | Một boundary do ứng dụng quy định | Có thể là chính hợp đồng public, không nên tự unwrap để bỏ domain semantics |

Một helper Reflection hợp lý:

```java
// Ví dụ minh họa Java, đặt ở class phù hợp.
static Object invokeTarget(
        java.lang.reflect.Method method,
        Object target, Object[] args) throws Throwable {
    try {
        return method.invoke(target, args);
    } catch (java.lang.reflect.InvocationTargetException wrapped) {
        throw wrapped.getCause();
    }
}
```

Không viết quy tắc chung “lặp `getCause()` tới tận cuối rồi ném root cause” cho mọi boundary. Wrapper có thể mang **ngữ nghĩa contract**, và unwrap hết có thể đổi taxonomy caller nhận. Lỗi do chính Reflection sai quyền truy cập/đối số cũng phải được phân biệt với lỗi target.

## 13. Không nuốt lỗi bằng fallback tùy tiện

```java
// Pseudocode KHÔNG khuyến nghị.
try {
    return proceed();
} catch (Exception ignored) {
    return null;
}
```

Những vấn đề có thể xảy ra:

- `null` vi phạm contract non-null.
- Method trả primitive có thể gây lỗi unboxing.
- Lỗi mất quyền hoặc hạ tầng offline bị hiểu thành “không có dữ liệu”.
- Side effects có thể đã chạy nhưng caller tưởng thành công.
- Monitoring không thấy lỗi, stack trace bị mất.
- Một ngoại lệ từ logger/cleanup vẫn có thể che hoặc tạo lỗi khác.

Một fallback hợp lý chỉ được áp dụng **cho loại lỗi và method cụ thể**, với dữ liệu dự phòng được xác thực. Trả `Optional.empty()` chỉ đúng nếu “không tìm thấy” là trạng thái hợp lệ, không phải cách che mọi lỗi kết nối.

## 14. Phân loại lỗi theo ngữ nghĩa nghiệp vụ

Những loại lỗi cần được phân biệt:

1. **Validation:** input hoặc invariant sai; thường không retry.
2. **Not-found:** resource không tồn tại; có thể map hoặc recover theo contract.
3. **Authorization/security:** bị từ chối; không recover thành “cho phép”.
4. **Transient infrastructure:** có thể retry sau backoff nếu thao tác idempotent.
5. **Permanent infrastructure:** cấu hình/permission/format sai, retry vô ích.
6. **Programming bug:** cast sai, trạng thái null bất hợp lệ; không giấu thành lỗi “tạm thời”.
7. **Cancellation/interruption:** tín hiệu điều phối task, cần xử lý riêng.
8. **VM/system failure:** `Error` hoặc lỗi nghiêm trọng, thường cần propagate.

Một advice chung bắt mọi `Exception` rồi biến thành một `DomainException` vô danh làm mất thông tin phân loại. Nếu public boundary cần ẩn chi tiết hạ tầng, nên cung cấp **error code/taxonomy ổn định** thay cho việc chỉ copy message.

## 15. Retry có điều kiện: khác với mapping

Một around advice có thể gọi `proceed` nhiều lần khi error được xác định là tạm thời:

```java
// Pseudocode minh họa; API proceed và exception tự định nghĩa.
int attempt = 0;
while (true) {
    attempt++;
    try {
        return proceed();
    } catch (TransientFailure error) {
        if (attempt >= maxAttempts) {
            throw error;
        }
        waitBeforeRetry(attempt);
    }
}
```

Các yêu cầu:

- **Attempt count** thuộc invocation hiện tại, không là field shared trên singleton aspect.
- Có giới hạn số lần thử và deadline.
- Có backoff hợp lý.
- Không retry lỗi validation/authorization.
- Phải hiểu liệu target **đã tạo side effect** trước khi throw.
- Có idempotency key hoặc cơ chế chống lặp cho thao tác quan trọng.
- Cần chính sách xử lý cancellation/interrupt.
- Cuối cùng preserve hay map exception cũng phải đúng contract caller.

Retry thành công **không có nghĩa các attempt trước không có side effect**. Một thao tác thanh toán có thể đã ghi nhận debit nhưng timeout trước khi phản hồi; retry bừa có nguy cơ ghi hai lần.

### Advice ordering ảnh hưởng retry

```text
OuterMapping
    |
    Retry
      |
      Target throws IOException
```

Retry ở trong mapper có thể thấy raw `IOException`. Đảo thứ tự có thể khiến retry chỉ thấy domain exception đã map. Điều kiện chọn retry vì vậy phụ thuộc **precedence** của các around advice.

## 16. Async: lỗi sau khi method đã return

`CompletableFuture<String> fetchAsync()` có thể trả một **future object bình thường**; task phía sau mới complete exceptionally. Một after-throwing advice **đồng bộ** quanh `fetchAsync()` chưa chắc thấy lỗi completion.

```text
Method call -> Return(CompletableFuture<String>)
                    |
                    v
          Later completion:
           value | error | cancel
```

### `exceptionally` là recovery

```java
// Minh họa khi original là CompletableFuture<String>.
java.util.concurrent.CompletableFuture<String> recovered =
        original.exceptionally(error -> "DEFAULT");
```

Stage mới có thể trở thành success với `"DEFAULT"`, do đó **làm thay đổi outcome**. Chỉ dùng khi contract async cho phép fallback.

### `whenComplete` dùng để quan sát nhưng vẫn cần thận trọng

```java
// Minh họa khi original là CompletableFuture<String>.
java.util.concurrent.CompletableFuture<String> observed =
        original.whenComplete((value, error) -> {
            if (error != null) {
                recordFailure(error);
            }
        });
```

`whenComplete` trả stage phụ. Nếu callback `recordFailure` ném lỗi, semantics completion của stage mới có thể bị ảnh hưởng. Nếu chỉ cần quan sát, phải kiểm soát lỗi sink và cancellation.

`join()` thường có thể báo thất bại qua `CompletionException`; `get()` thông thường qua `ExecutionException`. Đừng nhầm wrapper async với exception target ném đồng bộ trước lúc method trả future.

## 17. Interrupt và cancellation không phải lỗi retry thông thường

Nếu gặp `InterruptedException` trong blocking operation:

- Có thể **propagate** theo checked contract.
- Nếu bắt và không propagate vì API boundary yêu cầu conversion, thường cần **khôi phục interrupt flag** khi phù hợp: `Thread.currentThread().interrupt()`.
- Không tiếp tục retry vô hạn hoặc chuyển thành success giả.
- Hành vi cancellation của future/task có semantics riêng, không tự có nghĩa “dữ liệu không tồn tại”.

```java
// Đoạn Java minh họa trong method thích hợp.
try {
    performBlockingWork();
} catch (InterruptedException original) {
    Thread.currentThread().interrupt();
    throw new IllegalStateException("Operation interrupted", original);
}
```

`performBlockingWork()` là tên hàm giả định phục vụ giải thích, không phải API Java SE. Đây là **một quyết định conversion**; không áp dụng máy móc nếu method đã có thể khai báo `throws InterruptedException` và caller cần tín hiệu checked gốc.

## 18. `Error` và cleanup

Advice có thể bắt `Throwable` để đảm bảo dọn resource hoặc truyền tiếp nguyên trạng, nhưng không nên mặc nhiên recover `OutOfMemoryError`, `LinkageError`, `StackOverflowError` thành giá trị bình thường. Hệ thống có thể đang ở trạng thái không an toàn để chạy tiếp.

Trong tình huống nghiêm trọng:

- Tránh logging/serialization tạo quá nhiều resource mới.
- Giữ cleanup tối thiểu theo khả năng thực tế.
- Không làm mất Throwable nghiêm trọng bởi một exception mới không cần thiết.
- Propagate theo chính sách runtime phù hợp.
- Không dùng blanket catch để che lỗi trong production.

## 19. Nhiều aspect nhìn thấy những lỗi khác nhau

Một chuỗi:

```text
AuditAround
  |
  RetryAround
    |
    MappingAround
      |
      Target
```

Một lần target thất bại không có nghĩa tất cả advice nhìn thấy cùng exception:

- Target ném `IOException`.
- Mapper biến thành `DocumentUnavailable`.
- Retry ở ngoài thấy `DocumentUnavailable` chứ không phải lỗi gốc.
- Audit ở ngoài ghi lỗi cuối cùng hoặc success nếu outer recover.
- Nếu một around ngoài **không proceed**, những advice bên trong không chạy.

Cần xác định **ai sở hữu mapping, ai sở hữu retry, ai ghi raw failure và ai ghi final caller outcome**. Nếu mọi aspect đều log full stack trace hoặc map lại, dễ có log trùng và cause chain nhiễu.

### Double wrapping

```text
DomainUnavailable("outer")
  caused by DomainUnavailable("inner")
    caused by IOException("offline")
```

Nếu hai layer cùng map một lỗi vào cùng domain taxonomy mà không thêm semantics mới, chuỗi wrapper có thể vô ích. Hãy map tại **boundary có chủ đích**, không “mỗi method đều thêm một wrapper”.

## 20. Lỗi target, lỗi advice và lỗi infrastructure

Một exception có thể phát sinh ở:

- **Target:** business/dependency thực sự lỗi.
- **Before advice:** validation hoặc authorization từ chối.
- **Around advice:** policy retry, mapping hoặc bug.
- **After/finally advice:** logging, cleanup hoặc serialization hỏng.
- **Proxy/Reflection infrastructure:** invoke sai method, access, cast/checked compatibility.
- **Bytecode weaving/module loading:** verify/link/runtime helper không phù hợp.
- **Async continuation:** executor/callback/stage thất bại về sau.

Không map lỗi `IllegalAccessException` do interceptor cấu hình sai thành “tài liệu không tồn tại”. Khi debug, cần xác định **điểm exception được tạo**, số lần `proceed`, thứ tự advice, cause chain và giá trị cuối caller quan sát.

## 21. Return contract khi recover

Một advice có thể trả fallback nếu API cho phép, nhưng phải bảo toàn **return type** lẫn nghiệp vụ:

| Return type | Fallback có thể phù hợp | Rủi ro |
| --- | --- | --- |
| `String` | Chuỗi hợp lệ theo contract | `null` hoặc text mặc định làm sai nghĩa |
| `int` | Primitive đúng miền giá trị | `null` qua proxy gây unboxing error; `0` có thể nghĩa “hết hàng” |
| `Optional<T>` | `Optional.empty()` khi thật sự not-found | Không thay database outage bằng empty |
| `List<T>` | Danh sách đã xác thực | List rỗng có thể che timeout |
| `CompletableFuture<T>` | Stage/future đúng kiểu | Không trả trực tiếp object `T` cho caller |
| `void` | Không có giá trị caller nhận | Không thể báo thành công bằng một object return |
| Generic `T` | Giá trị phù hợp kiểu caller dùng | Type erasure có thể hoãn lỗi cast |

Một fallback **hợp lệ về Java type** vẫn có thể sai về authorization, tính toàn vẹn hoặc side effect. Với yêu cầu bảo mật, không bao giờ recover lỗi từ chối quyền thành success.

## 22. Bảo vệ dữ liệu khi chuyển đổi và log exception

Message/cause có thể chứa:

- Đường dẫn file hoặc host/port nội bộ.
- Query/cấu hình hạ tầng.
- Token, password, credential.
- Identifier khách hàng hoặc dữ liệu cá nhân.
- Internal implementation details.

Ở public API boundary có thể cần message và error code an toàn; diagnostics nội bộ có thể giữ cause với **quyền truy cập phù hợp**. Nhưng giữ cause **không cho phép tự serialize toàn bộ chain ra public response**.

Một error taxonomy đáng tin nên có **mã lỗi ổn định**, retryability và correlation ID phù hợp; caller không nên parse ngẫu nhiên exception message để ra quyết định nghiệp vụ.

## 23. Kiểm thử exception contract, không chỉ handler logic

| Ca kiểm thử | Điều phải chứng minh |
| --- | --- |
| Target return bình thường | Advice không tạo lỗi mới |
| Preserve | Caller nhận Throwable đúng loại/object nếu yêu cầu |
| Map cùng family checked | Cause gốc còn, type mới hợp contract |
| Map mất subtype | Test phát hiện caller bắt subtype bị ảnh hưởng |
| Checked không khai báo ở Java Dynamic Proxy | `UndeclaredThrowableException` theo semantics proxy |
| Unchecked exception | Propagate/map phù hợp |
| Error nghiêm trọng | Không bị swallow thành success |
| Throwable có suppressed exceptions | Có thể truy vết đầy đủ qua cause |
| Not-found | Recover đúng khi contract cho phép |
| Storage offline | Không giả làm dữ liệu “không tìm thấy” |
| Before advice throw | Target có chạy không, after nào được gọi? |
| Logging/cleanup throw | Có che lỗi gốc không? |
| Around skip proceed | Outcome cuối và side effects đúng |
| Retry nhiều lần | Đúng attempt count/idempotency/deadline |
| Mapping và retry đổi thứ tự | Lỗi ngoài cùng có thể khác |
| Future complete lỗi | Phân biệt lỗi sync và lỗi async |
| Interrupt/cancellation | Không mất tín hiệu dừng |
| Raw target bypass proxy | Policy AOP chỉ áp dụng đúng đường được intercept |
| Fallback cho primitive/generic/future | Không vi phạm type/caller contract |
| Sensitive error | Không lộ bí mật ở log/public response |

Cần **unit test taxonomy**, **integration test qua AOP engine thật**, và **contract test phía caller**. Unit test một hàm `Throwable map(Throwable)` có thể pass nhưng khi đi qua Java proxy lại gặp lỗi checked exception hoặc type không tương thích.

## 24. Checklist quyết định preserve/map/recover

1. Join point thực sự là call hay execution, và lời gọi có đi qua AOP không?
2. Method khai báo return type và checked throws như thế nào?
3. Throwable đến từ target, advice khác, hay infrastructure?
4. Error type và cause cụ thể là gì?
5. Lỗi này expected domain, transient hay bug/security/system?
6. Nếu chỉ quan sát, có thể giữ nguyên object không?
7. Nếu map, mapping nằm tại abstraction boundary nào?
8. New Throwable có cause và taxonomy/error code đúng không?
9. Caller nào phụ thuộc vào subtype checked exception cũ?
10. Nếu recover, fallback có đúng kiểu và semantics?
11. Target có side effect nào đã chạy trước failure?
12. Nếu retry, có idempotency, backoff, limit và cancellation không?
13. Trong chuỗi advice, mapping nằm trong hay ngoài retry/audit?
14. Logger/cleanup có thể che lỗi chính không?
15. Với async, đang xử lý synchronous throw hay future completion?
16. Có test xác minh **caller thấy gì**, không chỉ advice in gì không?

Chỉ khi trả lời rõ những câu hỏi phù hợp với hệ thống mới nên biến đổi outcome của target.

## 25. Những sai lầm thường gặp

1. **Bắt mọi `Exception` rồi trả null.** Che lỗi và có thể phá return contract.
2. **Tưởng log xong là giữ nguyên lỗi.** Nếu không rethrow/đường finally throw khác, caller có thể không thấy original.
3. **Đổi mọi lỗi thành `RuntimeException`.** Phá subtype và checked semantics.
4. **Chỉ sao chép message.** Làm mất cause chain.
5. **Giữ cause nghĩa là preserve exception identity.** Không: wrapper là object mới.
6. **After-throwing chắc chắn recover được.** Quyền thay outcome tùy engine.
7. **Handler `throws Throwable` nên proxy truyền được mọi checked type.** Interface method contract vẫn áp dụng.
8. **`UndeclaredThrowableException` là loại lỗi nghiệp vụ.** Nó có thể là wrapper kỹ thuật của Java Dynamic Proxy.
9. **Bất kỳ `IOException` nào cũng có thể retry.** Có lỗi permanent và side effects.
10. **Lỗi tài liệu thiếu giống storage offline.** Khác semantics.
11. **`finally` không thể thay outcome.** Exception cleanup có thể che original.
12. **Suppressed exceptions luôn nằm trực tiếp trên mapped Throwable.** Phải xem object và cause chain.
13. **Nên unwrap tất cả Throwable tới root.** Có thể xóa domain boundary có ý nghĩa.
14. **Completion future thành công nếu method return được future.** Task có thể thất bại sau đó.
15. **`exceptionally` chỉ ghi log.** Nó có thể biến failure thành fallback success.
16. **Interrupt/cancel nên retry ngay.** Cần giữ tín hiệu điều phối.
17. **Có fallback đúng kiểu thì đúng nghiệp vụ.** Phải xét tính toàn vẹn, quyền và consistency.
18. **Mapped exception có thể public nguyên cause vô điều kiện.** Cause/message có thể lộ thông tin.
19. **Mọi aspect nhìn thấy cùng lỗi gốc.** Precedence/mapping/recovery làm thay đổi outcome.
20. **Unit test mapper đủ đảm bảo caller contract.** Proxy boundary, weaving, async và type checks cần integration test.

## 26. Bài tập

1. Chạy `AdviceExceptionContractDemo.java`, đối chiếu toàn bộ stdout.
2. Vì sao `caller sameIdentity true`? Nếu map thành exception mới, phép `==` có còn true không?
3. Với `MAP_IO`, kiểm tra message, cause và stack trace ngoài cùng của mapped exception.
4. Thay `FileNotFoundException` thành `IOException` mới; caller bắt subtype bị ảnh hưởng thế nào?
5. Đổi `BAD_CHECKED` từ `SQLException` sang `IllegalStateException`; wrapper proxy có giống trước không?
6. Thêm method interface không khai báo checked throws; thử handler ném `IOException`.
7. Thêm policy chỉ ghi audit rồi `throw original`, so sánh với policy nuốt lỗi.
8. Cho audit sink ném lỗi trong `finally` và giải thích Throwable caller thấy.
9. Tạo một `try-with-resources` mà work và `close` cùng lỗi; quan sát suppressed, sau đó map có cause.
10. Tạo hai around advice: retry và mapping; đảo thứ tự, đối chiếu lỗi mỗi layer nhận.
11. Viết policy chỉ retry một subtype lỗi transient, với giới hạn attempt/deadline.
12. Mô phỏng charge bị timeout sau khi có side effect; vì sao retry cần idempotency key?
13. Dùng `Optional.empty()` cho not-found nhưng propagate lỗi network; giải thích.
14. Trả null cho một proxy method trả `int` và kiểm tra lỗi unboxing.
15. Tạo future hoàn tất lỗi, so sánh `get()` và `join()`.
16. Gắn `exceptionally(error -> "DEFAULT")` rồi xác định khi nào caller không thấy lỗi nữa.
17. Gắn `whenComplete` có callback ném exception; kiểm tra semantics stage mới.
18. Xây taxonomy error code cho ba loại: not-found, forbidden, unavailable, tránh parse message.
19. Viết test rằng raw target không qua proxy mapping còn wrapped target có policy.
20. Thiết kế metric tách số attempt thất bại, số caller invocation thất bại và số recovery thành công.

**Tự kiểm tra:** Preserve khác map ở object identity như thế nào? Khi nào nên giữ subtype? Vì sao Java Dynamic Proxy có thể sinh `UndeclaredThrowableException`? Recovery có thể vi phạm non-null/primitive contract ra sao? Cause và suppressed khác nhau thế nào? Khi future return bình thường nhưng completion thất bại, advice nào quan sát được? Retry có thể làm side effect trùng không?

## Kết luận

**Ngoại lệ là đầu ra có ý nghĩa của một phương thức, không phải thứ advice được phép xóa tùy tiện.** Trong AOP, hãy mặc định **giữ nguyên Throwable gốc** nếu chỉ quan sát. Khi có abstraction boundary rõ, có thể **chuyển đổi có chủ đích**, giữ cause và taxonomy hợp lệ; khi business contract cho phép, có thể **recover** bằng fallback đúng nghĩa; retry chỉ khi có tính idempotent và kiểm soát deadline/cancellation.

Cần phân biệt lỗi target, lỗi advice, wrapper kỹ thuật, lỗi cleanup và thất bại bất đồng bộ. Quyết định cuối phải được xác nhận bằng **giá trị hoặc ngoại lệ mà caller thật sự quan sát**, kể cả qua proxy/weaving, chứ không chỉ bằng dòng log trong advice.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-exception-contracts-004">Rủi ro nuốt lỗi và che khuất nguyên nhân gốc</a>

<details>
<summary>Xem chi tiết</summary>

# Rủi ro nuốt lỗi và che khuất nguyên nhân gốc trong AOP

> **Mục tiêu:** Hiểu tại sao advice của lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP) có thể làm mất thông tin lỗi từ phần nghiệp vụ gốc; phân biệt nuốt ngoại lệ, thay ngoại lệ, đánh mất nguyên nhân, làm sai stack trace và che lỗi bằng cleanup; biết bảo toàn hợp đồng return/throw khi kết hợp nhiều aspect. Nội dung thuộc AOP tổng quát, áp dụng cho những mô hình weaving và interception hỗ trợ các điểm can thiệp tương ứng, không giả định một framework riêng.

## 1. Khi nghiệp vụ thất bại nhưng caller tưởng thành công

Một phương thức có thể báo lỗi bằng exception:

~~~text
Caller -> Around advice -> Original operation
                              |
                              +-- throws IOException
~~~

Nếu một advice chỉ có nhiệm vụ ghi log mà bắt lỗi rồi trả về giá trị bình thường:

~~~text
try:
    return proceed()
catch exception:
    log("failure")
    return "SUCCESS"
~~~

thì caller không còn biết rằng thao tác thật sự thất bại. Đây là trường hợp **swallowing**: lỗi bị bắt nhưng tín hiệu lỗi không đi tiếp theo hợp đồng.

Nguy cơ đặc biệt lớn khi caller sử dụng exception để quyết định retry, rollback, ghi audit hoặc ngắt workflow. Một return bình thường không đồng nghĩa dữ liệu/thao tác đã thành công về mặt nghiệp vụ.

Tuy nhiên, có **recovery hợp lệ**: một policy có thể chuyển sang nguồn dữ liệu dự phòng rồi trả kết quả được định nghĩa rõ. Khác biệt nằm ở **hợp đồng của thao tác** và việc fallback có thật sự đáp ứng hợp đồng, không phải chỉ ở sự hiện diện của catch.

## 2. Hợp đồng phương thức gồm cả đường thất bại

Một phương thức Java có thể kết thúc theo hai kiểu:

- **Normal completion:** trả một giá trị hoặc kết thúc bình thường với void.
- **Abrupt completion:** ném exception hoặc Error.

Ví dụ chữ ký:

~~~java
interface Reader {
    String read() throws java.io.IOException;
}
~~~

Đây là mảnh khai báo minh họa, chưa phải chương trình độc lập.

Caller của Reader.read có thể phân biệt một chuỗi hợp lệ với IOException để thực hiện policy khác nhau. Nếu advice biến IOException thành String hợp lệ về kiểu nhưng sai ý nghĩa, caller sẽ không thể thực hiện nhánh xử lý lỗi cần thiết.

Trong AOP, advice không chỉ được phép quan sát log: một số quanh advice có quyền thay return, bỏ qua proceed hoặc chuyển ngoại lệ. Quyền năng đó làm **đường return/throw** trở thành thành phần quan trọng của hợp đồng khi kết hợp aspect.

## 3. Bốn khái niệm dễ bị gộp nhầm

| Hiện tượng | Mô tả | Rủi ro |
|---|---|---|
| **Swallowing** | Bắt exception rồi không đưa thất bại tiếp đến boundary cần thiết | Caller tin rằng normal completion xảy ra |
| **Masking** | Một exception mới thay thế exception ban đầu | Caller thấy sai điểm thất bại quan trọng |
| **Lost cause** | Tạo wrapper nhưng bỏ tham chiếu nguyên nhân | Không truy được chuỗi nguyên nhân |
| **Lost stack context** | Ném một lỗi mới hoặc làm thay stack context không hợp lý | Stack trace hướng về mã advice thay vì nguồn lỗi |

Một advice có thể gặp nhiều hiện tượng cùng lúc. Ví dụ: Original ném IOException, advice ghi audit bằng một formatter, formatter ném IllegalStateException rồi được truyền ra ngoài mà không gắn cause. Lỗi Original bị che, và log cấp trên chỉ có thông tin về formatter.

Để phân tích đúng cần hỏi: **Lỗi đầu tiên là gì? Lỗi nào caller nhìn thấy? Ai thay đổi luồng giữa hai thời điểm đó?**

## 4. Phân biệt loại lỗi Java

Trong Java có ba nhóm quan trọng:

1. **Checked exception:** các Exception phải tuân thủ quy tắc kiểm tra của compiler, trừ những nhánh unchecked.
2. **Unchecked exception:** RuntimeException và các subclass, không bắt buộc khai báo bằng throws.
3. **Error:** các trường hợp từ java.lang.Error và subclass, thường chỉ tình trạng nghiêm trọng hoặc lỗi nền tảng.

Một advice bắt Exception không bao phủ mọi Error. Một advice bắt Throwable bao phủ cả Exception và Error, nhưng **bắt được không có nghĩa phục hồi được**.

Đặc biệt, không nên bắt Throwable rồi tự trả fallback như thành công, bởi nó có thể che OutOfMemoryError, StackOverflowError hoặc các lỗi nghiêm trọng khác.

Một số hạ tầng cần finally hoặc catch(Throwable) để **dọn trạng thái và ném lại cùng lỗi**; đó là nhu cầu khác với nuốt tất cả exception. Không có catch-all nào bảo đảm logger vẫn hoạt động an toàn trong mọi lỗi của JVM.

## 5. Advice có thể thay đường điều khiển của phần gốc

Sơ đồ:

~~~text
Caller
  |
  Trace around
    |
    Audit around
      |
      Recovery around
        |
        Original
~~~

Nếu Original ném IOException:

- Recovery có thể ném lại IOException.
- Recovery có thể chuyển thành một lỗi cấp cao có cause.
- Recovery có thể thực hiện fallback thật và trả bình thường.
- Recovery cũng có thể nuốt IOException rồi trả giá trị sai.

Trace và Audit ở ngoài **chỉ thấy kết quả từ các lớp bên trong**, không mặc định biết exception đầu tiên của Original.

Cùng một lỗi có thể được nhiều advice ghi nhận ở những thời điểm khác nhau mà caller chỉ thấy một lỗi hoặc thậm chí không thấy lỗi nào.

## 6. Short-circuit và swallowing không phải một

Short-circuit là khi một advice **không gọi phần còn lại**:

~~~text
Guard rejects input
  |
  returns REJECTED
  |
  Original never runs
~~~

Không có exception từ Original bị nuốt, vì Original chưa chạy.

Swallowing có thể xảy ra khi Original đã chạy và ném lỗi:

~~~text
Original executes
  |
  throws IOException
  |
  Recovery catches and returns REJECTED
~~~

Hai đường đều có thể trả REJECTED, nhưng chỉ đường thứ hai đã tạo ra lỗi từ phần gốc. Phân biệt này quan trọng với kiểm toán, transaction và việc đo số lần thực thi.

Khi debug, nên có cách xác định **Original đã được vào hay chưa**, thay vì suy từ dòng log của advice bên ngoài.

## 7. Nuốt exception làm hỏng retry

Caller có thể retry khi nhận một IOException tạm thời:

~~~text
try call operation
if transient IOException:
    retry within bounded policy
~~~

Nếu advice nằm bên trong bắt IOException rồi trả bình thường, caller **không nhận exception** và bỏ qua logic retry. Một hệ thống có thể âm thầm sử dụng kết quả thiếu dữ liệu.

Ngược lại, advice tự retry cũng có thể sai nếu caller hoặc hệ thống đã có một policy retry khác. Tác dụng phụ có thể lặp khi attempt đầu thực sự đã thay đổi trạng thái ngoài hệ thống nhưng bị mất phản hồi.

Không dùng nuốt lỗi hay retry như thủ thuật làm giảm log. Cần hiểu **loại lỗi, số lần Original chạy và tính idempotent**.

## 8. Nuốt lỗi và transaction

Trong nhiều thiết kế, kết thúc normal hoặc exceptional là tín hiệu quan trọng cho thành phần quản lý một đơn vị công việc. Nếu advice chuyển lỗi thành return, thành phần phía ngoài có thể quyết định dựa trên kết quả sai.

~~~text
Begin unit of work
    Original throws E
    Advice catches E and returns OK
Commit decision observes normal return
~~~

Sơ đồ này mô tả rủi ro trong **những hệ dùng return/throw làm tín hiệu**, không phải quy tắc commit/rollback chung của mọi transaction manager.

Ngoài ra, Original có thể đã tạo một phần side effect trước khi ném lỗi. Việc bắt rồi trả OK không khôi phục trạng thái, không tự rollback và không bảo đảm dữ liệu nhất quán.

## 9. Exception masking trong finally

Trong Java, finally thường được dùng để cleanup. Nhưng nếu finally ném lỗi mới khi try đã ném lỗi chính, lỗi mới có thể **thay thế lỗi chính** truyền ra caller.

~~~text
try:
    throw PrimaryIOException
finally:
    throw CleanupIllegalStateException
~~~

Caller có thể chỉ bắt CleanupIllegalStateException. Nếu exception gốc không được lưu trong cause hoặc suppressed, chuỗi truy nguyên có thể bị mất.

Đây là tình huống đặc biệt dễ gặp trong AOP, vì after/finally advice thường gọi logger, serializer, metric sink hoặc đóng resource. Nếu chính thao tác cleanup/observability thất bại, lỗi thứ cấp có thể che lỗi mà advice định quan sát.

## 10. Finally không có khả năng miễn lỗi

Cần xác định rõ:

- Nếu Original lỗi rồi cleanup lỗi, lỗi nào được ưu tiên?
- Lỗi cleanup có được gắn làm suppressed không?
- Nếu Original thành công nhưng cleanup lỗi, caller phải nhận tín hiệu nào?
- Cleanup là bắt buộc hay best-effort?
- Có nguy cơ lỗi formatter, serializer hoặc log sink che lỗi gốc?
- Liệu việc dùng finally ở một advice có thay đổi hợp đồng Error không?

Một observer chỉ đo thời gian thường muốn bảo toàn lỗi của Original, nhưng một resource bắt buộc đóng thành công có thể có hợp đồng xử lý lỗi khác. Không tự động catch mọi lỗi cleanup rồi im lặng khi resource đóng là điều kiện nghiệp vụ bắt buộc.

## 11. Cause và suppressed: hai quan hệ khác nhau

Trong Java, Throwable có thể giữ **cause**:

~~~text
DomainFailure
    caused by
IOException from storage
~~~

Cause nói rằng một lỗi cấp thấp dẫn tới lỗi được báo ở cấp cao.

Throwable cũng có thể giữ **suppressed exceptions**:

~~~text
Primary IOException
    suppressed:
      CleanupIllegalStateException
~~~

Suppressed hữu ích khi lỗi thứ cấp phát sinh trong lúc đang xử lý/đóng resource nhưng cần giữ lỗi chính làm lỗi truyền ra.

Không nên gộp mọi lỗi vào suppressed không suy nghĩ; cách lưu phải phản ánh ngữ nghĩa. Nhưng nếu một cleanup thất bại sau khi Original đã thất bại, hãy chủ động tránh **vứt mất cả primary lẫn secondary information**.

## 12. Bảo toàn stack trace và danh tính exception

Nếu advice chỉ quan sát, một lựa chọn thường tốt là:

~~~text
catch original E:
    record observation
    throw E
~~~

Việc ném lại cùng Throwable giúp giữ danh tính và thông tin mà Throwable đang chứa, thay vì tạo exception mới vô cớ.

Khi cần chuyển lỗi cấp thấp thành một ngoại lệ domain, nên có constructor nhận cause:

~~~java
final class DataFailure extends RuntimeException {
    DataFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
~~~

Đây là khai báo Java minh họa.

Caller thấy DataFailure, nhưng đường getCause vẫn dẫn về lỗi cấp thấp. Ngược lại, new DataFailure("failed", null) chỉ cung cấp message mới mà có thể làm mất liên hệ tới IOException ban đầu.

### 12.1. Không biến việc thêm log thành việc thay exception

Một advice không nên tạo RuntimeException mới chỉ để ghi thêm một message trong khi hợp đồng caller cần IOException nguyên bản.

Nếu cần thay loại lỗi, đó phải là **quy tắc mapping được thiết kế, tài liệu hóa và kiểm thử**, không phải một phản ứng tùy ý tại catch.

## 13. Checked exceptions và hợp đồng throws

Xét API:

~~~java
interface Reader {
    String read() throws java.io.IOException;
}
~~~

Caller của API này có thể xử lý IOException. Một wrapper triển khai Reader không thể tùy tiện mở rộng throws checked exception thành Exception chung trong khai báo override.

Một aspect được weave vào đường thực thi cũng cần tôn trọng hợp đồng của ngôn ngữ/cơ chế được dùng. Nếu advice chuyển checked IOException thành unchecked RuntimeException, caller có thể bỏ lỡ nhánh xử lý IOException vốn đúng.

Một lỗi chuyển đổi có thể không bị compiler Java thông thường bắt nếu xảy ra qua reflection/proxy, nhưng nó vẫn làm thay đổi **API contract được quan sát**.

## 14. Java Dynamic Proxy và ngoại lệ bọc

Java Dynamic Proxy chuyển method invocation tới InvocationHandler. Nếu handler sử dụng **Method.invoke**, exception của method đích thường được bọc trong **InvocationTargetException**.

Ví dụ đoạn handler **chỉ là mảnh minh họa**:

~~~java
(proxy, method, args) -> {
    try {
        return method.invoke(target, args);
    } catch (java.lang.reflect.InvocationTargetException wrapped) {
        throw wrapped.getCause();
    }
}
~~~

Việc ném lại nguyên nhân gốc giúp tránh trả một wrapper reflection không được dự kiến. Nhưng phải nhớ: nếu handler đưa ra một checked exception **không tương thích throws của method giao diện**, Java Dynamic Proxy có thể bọc nó trong **UndeclaredThrowableException**.

Đó là quy tắc của một cơ chế proxy cụ thể, không phải quy tắc chung của mọi loại weaving AOP.

## 15. Ví dụ Java 17 số 1: lỗi được bảo toàn và lỗi bị nuốt

Ví dụ này lắp thủ công một chuỗi **Trace → Audit → Original**, sau đó thêm một **Swallow** ở giữa. Nó minh họa cách advice ngoài nhìn thấy exception hay normal return theo kết quả của advice trong.

**Đây là chương trình Java thuần mô phỏng around advice, không phải cú pháp của một công cụ weaving tự động.**

Lưu file **AdviceExceptionMaskingDemo.java**:

~~~java
import java.io.IOException;

public class AdviceExceptionMaskingDemo {
    @FunctionalInterface
    interface Next {
        String proceed() throws IOException;
    }

    interface Around {
        String invoke(Next next) throws IOException;
    }

    static final class Trace implements Around {
        @Override
        public String invoke(Next next) throws IOException {
            System.out.println("[trace] enter");
            try {
                String result = next.proceed();
                System.out.println("[trace] return " + result);
                return result;
            } catch (IOException error) {
                System.out.println("[trace] io " + error.getMessage());
                throw error;
            } catch (RuntimeException error) {
                System.out.println("[trace] runtime "
                        + error.getMessage());
                throw error;
            } finally {
                System.out.println("[trace] finally");
            }
        }
    }

    static final class Audit implements Around {
        @Override
        public String invoke(Next next) throws IOException {
            System.out.println("[audit] enter");
            try {
                String result = next.proceed();
                System.out.println("[audit] return " + result);
                return result;
            } catch (IOException error) {
                System.out.println("[audit] io " + error.getMessage());
                throw error;
            } catch (RuntimeException error) {
                System.out.println("[audit] runtime "
                        + error.getMessage());
                throw error;
            } finally {
                System.out.println("[audit] finally");
            }
        }
    }

    static final class Swallow implements Around {
        @Override
        public String invoke(Next next) throws IOException {
            try {
                return next.proceed();
            } catch (IOException error) {
                System.out.println("[bad] swallowed "
                        + error.getMessage());
                return "FALLBACK";
            }
        }
    }

    static String original(String mode) throws IOException {
        System.out.println("[business] " + mode);
        return switch (mode) {
            case "ok" -> "OK";
            case "io" -> throw new IOException("disk offline");
            case "runtime" ->
                    throw new IllegalArgumentException("invalid amount");
            default -> throw new IllegalArgumentException("unknown mode");
        };
    }

    static void runSafe(String mode) {
        System.out.println("== safe " + mode + " ==");
        try {
            String result = new Trace().invoke(
                    () -> new Audit().invoke(
                            () -> original(mode)));
            System.out.println("[caller] " + result);
        } catch (IOException | RuntimeException error) {
            System.out.println("[caller] "
                    + error.getClass().getSimpleName()
                    + ": " + error.getMessage());
        }
    }

    static void runSwallow() {
        System.out.println("== swallow io ==");
        try {
            String result = new Trace().invoke(
                    () -> new Swallow().invoke(
                            () -> new Audit().invoke(
                                    () -> original("io"))));
            System.out.println("[caller] " + result);
        } catch (IOException error) {
            System.out.println("[caller] IOException: "
                    + error.getMessage());
        }
    }

    public static void main(String[] args) {
        runSafe("ok");
        runSafe("io");
        runSafe("runtime");
        runSwallow();
    }
}
~~~

Biên dịch và chạy trên JDK 17 hoặc mới hơn:

~~~bash
javac AdviceExceptionMaskingDemo.java
java AdviceExceptionMaskingDemo
~~~

**Stdout chính xác:**

~~~text
== safe ok ==
[trace] enter
[audit] enter
[business] ok
[audit] return OK
[audit] finally
[trace] return OK
[trace] finally
[caller] OK
== safe io ==
[trace] enter
[audit] enter
[business] io
[audit] io disk offline
[audit] finally
[trace] io disk offline
[trace] finally
[caller] IOException: disk offline
== safe runtime ==
[trace] enter
[audit] enter
[business] runtime
[audit] runtime invalid amount
[audit] finally
[trace] runtime invalid amount
[trace] finally
[caller] IllegalArgumentException: invalid amount
== swallow io ==
[trace] enter
[audit] enter
[business] io
[audit] io disk offline
[audit] finally
[bad] swallowed disk offline
[trace] return FALLBACK
[trace] finally
[caller] FALLBACK
~~~

### 15.1. Đường thành công

Trace vào trước Audit. Original trả OK; Audit ghi return/finally, Trace ghi return/finally, Caller nhận OK. Với observer thuần, các advice không cần thay kết quả.

### 15.2. Đường checked IOException

Original ném IOException. Audit và Trace đều ghi nhận rồi ném lại **cùng exception object**. Caller nhận IOException với message disk offline.

Điều đó không có nghĩa lỗi xảy ra nhiều lần. Một lỗi được quan sát ở hai boundary nên có hai thông điệp, nhưng chỉ một Original invocation đã ném lỗi.

### 15.3. Đường unchecked RuntimeException

Original ném IllegalArgumentException. Audit và Trace đều giữ nguyên loại và message. Caller có thể tiếp tục phân loại chính xác.

### 15.4. Đường Swallow

Original ném IOException, Audit ghi rồi ném lại. Swallow bắt và trả FALLBACK, nên Trace ở ngoài chỉ thấy normal return. Caller không còn nhận IOException.

Nếu FALLBACK là một kết quả đã được API định nghĩa là cách phục hồi hợp lệ, hành vi có thể đúng. Nếu Caller tưởng thao tác I/O thành công, nó là **che lỗi nguy hiểm**.

### 15.5. Giới hạn của ví dụ

Chương trình chỉ điều khiển một chain thủ công, không giải thích engine AOP nào chọn pointcut hoặc đặt precedence. Các catch minh họa IOException/RuntimeException, không catch Error. Trong hệ thật, lỗi logger, retry, transaction, async và chính sách phân quyền phải được kiểm thử riêng.

## 16. Một fallback chỉ hợp lệ khi đáp ứng hợp đồng

Không phải cứ bắt rồi trả kết quả là sai. Nhưng để gọi là **recovery**, cần ít nhất:

- API cho phép giá trị thay thế và caller nhận biết được semantics của nó.
- Chỉ những lỗi có thể phục hồi được bắt.
- Hành vi sau khi Original đã có một phần side effect được xác định.
- Không có yêu cầu rollback/retry ở ngoài bị phá.
- Có thông tin quan sát cần thiết về lần thất bại.
- Fallback không làm lộ hoặc biến dạng dữ liệu.
- Lỗi từ chính nguồn fallback được truyền/báo đúng.
- Không nuốt Error như một loại normal failure.

Một advice chỉ ghi metrics thường không sở hữu quyết định fallback. Một chính sách fallback nên là thành phần có **quyền và thông tin ngữ nghĩa đầy đủ**.

## 17. Try-with-resources và suppressed exceptions

Java có cơ chế **try-with-resources** giúp tránh lỗi đóng resource che lỗi chính trong một số trường hợp.

Khi thân try ném IOException và AutoCloseable.close cũng ném IllegalStateException, IOException được giữ làm **primary exception**; lỗi close được thêm vào danh sách **suppressed**.

Đây là cách tư duy tốt khi thiết kế cleanup trong advice: không để một lỗi phụ vô tình xóa thông tin về lỗi đã xảy ra trước.

Tuy nhiên không phải mọi resource có thể chuyển thành AutoCloseable, và không phải mọi thất bại thứ cấp nên được xử lý như nhau. Cần quyết định theo ranh giới trách nhiệm.

## 18. Ví dụ Java 17 số 2: masking và suppressed

Ví dụ dùng hai phương thức độc lập:

- **unsafe:** finally ném exception mới, che primary IOException.
- **safe:** try-with-resources giữ primary IOException và gắn lỗi close vào suppressed.

Lưu file **ExceptionSuppressionDemo.java**:

~~~java
import java.io.IOException;

public class ExceptionSuppressionDemo {
    @FunctionalInterface
    interface Action {
        void run() throws IOException;
    }

    static final class FailingResource implements AutoCloseable {
        @Override
        public void close() {
            System.out.println("[resource] close");
            throw new IllegalStateException("close failed");
        }
    }

    static void unsafe() throws IOException {
        try {
            System.out.println("[business] fail");
            throw new IOException("primary");
        } finally {
            System.out.println("[cleanup] fail");
            throw new IllegalStateException("cleanup failed");
        }
    }

    static void safe() throws IOException {
        try (FailingResource resource = new FailingResource()) {
            System.out.println("[business] fail");
            throw new IOException("primary");
        }
    }

    static void show(String name, Action action) {
        System.out.println("== " + name + " ==");
        try {
            action.run();
        } catch (Exception error) {
            System.out.println("[caller] "
                    + error.getClass().getSimpleName()
                    + ": " + error.getMessage());
            Throwable[] secondary = error.getSuppressed();
            System.out.println("[caller] suppressed="
                    + secondary.length);
            for (int i = 0; i < secondary.length; i++) {
                Throwable value = secondary[i];
                System.out.println("[caller] suppressed[" + i + "] "
                        + value.getClass().getSimpleName()
                        + ": " + value.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        show("unsafe", ExceptionSuppressionDemo::unsafe);
        show("resource", ExceptionSuppressionDemo::safe);
    }
}
~~~

Chạy với JDK 17:

~~~bash
javac ExceptionSuppressionDemo.java
java ExceptionSuppressionDemo
~~~

**Stdout chính xác:**

~~~text
== unsafe ==
[business] fail
[cleanup] fail
[caller] IllegalStateException: cleanup failed
[caller] suppressed=0
== resource ==
[business] fail
[resource] close
[caller] IOException: primary
[caller] suppressed=1
[caller] suppressed[0] IllegalStateException: close failed
~~~

### 18.1. Vì sao unsafe làm mất primary?

Thân try chuẩn bị ném IOException, nhưng finally ném IllegalStateException. Lỗi finally được truyền ra. Trong code này, Throwable truyền ra không giữ IOException như cause hoặc suppressed, nên Caller không thể truy từ lỗi cuối về primary.

### 18.2. Vì sao safe giữ được primary?

Câu lệnh try-with-resources có quy tắc ưu tiên primary exception từ thân try và ghi lỗi close thành suppressed. Caller bắt IOException, còn Throwable.getSuppressed trả ra IllegalStateException do close.

### 18.3. Áp dụng cho advice

Nếu một advice xử lý resource khi Original lỗi, cần tránh một finally chứa I/O/serialization có thể tự ném và che nguyên nhân. Khi cleanup cũng có khả năng lỗi, nên chọn pattern bảo toàn primary và ghi nhận secondary phù hợp.

Nếu Original thành công nhưng cleanup thất bại, tùy hợp đồng cleanup failure có thể trở thành lỗi chính của toàn thao tác. Không tự nuốt lỗi của resource bắt buộc.

## 19. Logging rồi rethrow khác logging rồi return

Hai pattern khái niệm:

~~~text
Observer:
    catch E
    log E
    throw E
~~~

và:

~~~text
Swallowing:
    catch E
    log E
    return success-shaped value
~~~

Khác biệt là Caller có còn tín hiệu thất bại hay không.

Một dòng log không thay thế exception contract; caller có thể dựa vào exception type để retry, abort hoặc đánh dấu giao dịch thất bại.

Nếu policy cho phép return fallback, đừng dùng “OK” không phân biệt; nên có API hoặc kết quả thể hiện rõ trạng thái recovered/fallback.

## 20. Log đầy đủ ở mọi advice có tốt không?

Một IOException có thể đi qua nhiều quanh advice. Nếu Trace, Audit, Metrics và Caller đều in full stack trace, vận hành có thể thấy bốn log cho **một lỗi** và đếm sai tần suất lỗi.

Các vai trò nên được xác định:

- Aspect gần method có thể tăng metric theo loại lỗi.
- Aspect ở boundary có thể ghi một event lỗi đã chuẩn hóa.
- Boundary có quyền quan sát chi tiết có thể ghi stack trace đầy đủ.
- External API có thể gửi mã lỗi an toàn và correlation ID.

Không có một quy tắc chung cho mọi hệ rằng “chỉ log một lần”. Nhưng phải biết **mỗi log đại diện cho error occurrence, advice observation hay attempt**.

### 20.1. Stack trace có thể chứa dữ liệu nhạy cảm

Exception message có thể chứa đường dẫn, URL, identifier hoặc chuỗi từ input. Không tự động in raw arguments hoặc Throwable ở mọi sink. Cần phân loại dữ liệu, redaction, giới hạn kích thước và quyền truy cập phù hợp.

## 21. After-returning và after-throwing theo boundary riêng

Một after-returning advice bên ngoài Recovery có thể được kích hoạt khi Recovery chuyển IOException thành fallback, vì từ góc nhìn bên ngoài có **normal completion**.

Một after-throwing advice bên ngoài không nhất thiết thấy lỗi Original nếu lỗi đã được catch ở tầng trong.

~~~text
Original throws IOException
Inner returns FALLBACK
Outer after-returning observes FALLBACK
~~~

Hoặc:

~~~text
Original throws IOException
Inner converts to DomainFailure
Outer after-throwing observes DomainFailure
~~~

Điều này không chứng minh pointcut sai; đó là **hệ quả của exception flow** qua các lớp advice.

## 22. Advice có thể thất bại trước Original

Một before advice có thể ném lỗi khi chuẩn bị logging hoặc trích xuất ngữ cảnh. Khi đó Original chưa chắc được gọi.

~~~text
Before advice
   |
   +-- formatter throws
   |
   +-- Original not reached
~~~

Nếu Caller nhận IllegalArgumentException từ formatter, đó là lỗi ở advice, không phải bằng chứng nghiệp vụ gốc đã ném cùng loại lỗi.

Để phân biệt, có thể đặt một bộ đếm **Original invocation** trong ca thử tối thiểu; log của advice không đủ chứng minh Original đã chạy.

## 23. Retry và multiple proceed

Một quanh advice có thể retry bằng cách gọi continuation nhiều lần khi công cụ cho phép:

~~~text
Outer Trace
   Retry
     Attempt 1 -> Original throws E
     Attempt 2 -> Original returns OK
Outer Trace sees OK
~~~

Trace ở ngoài có thể ghi một lần normal return, trong khi một aspect nằm **bên trong Retry** ghi một attempt failure và một attempt success.

Do đó cần phân biệt:

- Số lời gọi của Caller.
- Số lần advice ngoài được vào.
- Số lần continuation của Retry được gọi.
- Số lần Original thực sự chạy.
- Số lần xảy ra side effect.

Retry không tự là swallowing, vì attempt sau có thể thành công thật. Nhưng retry thiếu idempotency và giới hạn lỗi có thể nguy hiểm như nuốt lỗi.

## 24. Async errors không xuất hiện trong cùng call stack

Một method Java trả **CompletableFuture** có thể return bình thường nhưng future hoàn thành exceptionally sau đó:

~~~text
submitAsync() returns future normally
                    |
                    +-- later: task fails
~~~

Một quanh advice đo tới lúc submitAsync return không nhất thiết thấy exception xảy ra lúc tác vụ chạy.

Các lỗi của CompletableFuture có thể được bọc trong CompletionException khi join, hoặc ExecutionException khi get. Cần kiểm tra **cause chain** để phân biệt wrapper và lỗi gốc.

Nếu một stage exceptionally bắt lỗi rồi trả fallback, stage sau có thể hoàn thành bình thường. Đây cũng có thể là recovery hợp lệ hoặc nuốt lỗi, tùy hợp đồng.

### 24.1. Không tin rằng fire-and-forget đã được quan sát

Nếu advice tạo một tác vụ nền và không quan sát future/completion, exception trong task có thể không truyền về Caller. Cần một policy failure sink hoặc completion callback có vòng đời phù hợp.

ThreadLocal dùng trong advice đồng bộ cũng không tự lan truyền tới mọi async continuation, nên correlation và exception mapping cần thiết kế riêng.

## 25. Fail-open và fail-closed của observability

Khi logging/metrics/audit sink thất bại, chính sách có thể:

- **Fail-open:** cho nghiệp vụ tiếp tục, có thể phù hợp telemetry best-effort.
- **Fail-closed:** không cho nghiệp vụ hoàn tất nếu không đáp ứng được yêu cầu quan sát/kiểm soát bắt buộc.

Không có đáp án phổ quát. Điều quan trọng là quyết định có căn cứ:

1. Event có bắt buộc không?
2. Người gọi cần tín hiệu lỗi nào?
3. Có thể ghi buffer hoặc retry sink không?
4. Rủi ro mất audit có được chấp nhận không?
5. Khi nghiệp vụ cũng lỗi, giữ primary như thế nào?
6. Có giới hạn thời gian hoặc tài nguyên cho việc quan sát không?

Một metrics advice không nên tự fail-closed chỉ vì logger ném RuntimeException bất ngờ; ngược lại một policy bảo mật bắt buộc không nên luôn fail-open chỉ để tránh exception.

## 26. Không tự addSuppressed mọi exception

Suppressed rất hữu ích nhưng phải dùng có chủ đích.

- Một Throwable không thể suppress chính nó.
- Một số Throwable có thể tắt suppression theo constructor.
- Một danh sách hàng nghìn suppressed errors có thể gây tăng bộ nhớ hoặc log khó đọc.
- Những lỗi retry khác nhau có thể cần báo dưới một aggregate exception riêng.

Nếu tạo domain failure từ một low-level failure, cause thường diễn đạt nguyên nhân tốt hơn. Nếu resource close lỗi trong lúc primary IOException đã tồn tại, suppressed có thể phù hợp hơn.

Chọn cấu trúc lỗi theo **quan hệ thực tế**, không theo cách làm cho stack trace trông dài hơn.

## 27. Không nhầm lỗi weaving với lỗi runtime advice

Một hệ AOP có thể thất bại ở nhiều tầng:

- Parser/validator pointcut từ chối biểu thức.
- Bộ dệt không thể xử lý class hoặc bytecode.
- JVM không nạp/liên kết được class sau biến đổi.
- Advice tự ném lỗi ở runtime.
- Original ném lỗi nghiệp vụ.
- Logger/cleanup của advice tạo lỗi thứ cấp.

Một RuntimeException hiện ở stack trace có tên interceptor chưa chứng minh interceptor gây ra lỗi; nó có thể chỉ là khung bao qua đó lỗi Original được truyền.

Cần truy cause/suppressed, vị trí phát sinh lỗi đầu tiên và số lần Original được vào.

## 28. Các kiểm thử tối thiểu cho hợp đồng ngoại lệ

| Ca thử | Điều cần xác minh |
|---|---|
| Original trả bình thường | Observer không thay return |
| Original ném IOException | Caller nhận đúng loại hoặc mapping được hợp đồng cho phép |
| Original ném RuntimeException | Không tự trả success giả |
| Original ném Error | Không bị catch-all nuốt |
| Advice lỗi trước proceed | Original chưa chạy và lỗi được phân loại đúng |
| Advice cleanup lỗi trong finally | Primary không bị che ngoài chính sách |
| Try-with-resources lỗi kép | Secondary nằm trong suppressed |
| Inner Recovery trả fallback | Outer thấy normal return có ý nghĩa rõ |
| Inner tạo domain exception | Cause chain giữ original |
| Multiple proceed do Retry | Original count và advice count đúng |
| Java Dynamic Proxy | Reflection wrapper và checked contract đúng |
| Future hoàn thành exceptionally | Lỗi được quan sát ở completion |
| Log sink thất bại | Fail-open/fail-closed đúng hợp đồng |
| Hai aspect cùng ghi lỗi | Không nhân số lỗi nghiệp vụ chỉ vì nhiều logs |

Không chỉ test success path. Các lỗi swallowing/masking thường chỉ lộ khi **Original và advice cùng đi qua exceptional flow**.

## 29. Kiểm thử exception identity, cause và suppressed

Trong một unit test, ta có thể tạo trước một exception E cho Original ném ra, rồi kiểm tra Caller nhận **cùng object** nếu advice chỉ quan sát.

Pseudocode:

~~~text
Original throws E
Caller catches C
assert C is same object as E
~~~

Nếu một policy chuyển lỗi thành D:

~~~text
Caller catches D
assert D.getCause() is E
~~~

Nếu cleanup thứ cấp ném S:

~~~text
Caller catches E
assert E.getSuppressed() contains S
~~~

Những kiểm thử này mạnh hơn việc chỉ kiểm tra một dòng “[ERROR]” có xuất hiện. Chúng giúp chứng minh **hợp đồng của lỗi tại ranh giới Caller**.

## 30. Quy trình điều tra khi mất nguyên nhân gốc

1. Bắt đầu từ exception **Caller thực sự nhận**.
2. Kiểm tra getCause và getSuppressed đầy đủ.
3. Xác định Original có thật sự chạy hay không.
4. Liệt kê các advice và thứ tự bao ngoài/bên trong.
5. Kiểm tra các catch quanh continuation.
6. Tìm catch kết thúc bằng return fallback.
7. Tìm catch tạo exception mới nhưng không truyền cause.
8. Tìm finally, resource close, serializer hoặc logger có thể ném.
9. Xác định exception đầu tiên phát sinh ở Original hay advice.
10. Kiểm tra reflection/proxy có wrapper không.
11. Kiểm tra retry, số lần proceed và số lần Original.
12. Kiểm tra async completion nếu phương thức trả future.
13. Đối chiếu throws contract với policy domain-level.
14. Tái hiện trường hợp lỗi kép (Original + cleanup).
15. Viết regression tests cho normal, checked, unchecked và Error paths.

Không thay đồng thời precedence, catch, retry và fallback. Khi đổi nhiều biến một lúc khó chỉ ra thay đổi nào làm lỗi không còn bị che.

## 31. Những lỗi thường gặp

**Lỗi 1 — catch(Exception) rồi trả mặc định:** che thất bại và làm Caller tưởng thành công.

**Lỗi 2 — finally ném lỗi phụ:** thay primary bằng lỗi cleanup.

**Lỗi 3 — Tạo RuntimeException không cause:** mất thông tin nguyên nhân.

**Lỗi 4 — Catch Throwable rồi nuốt Error:** có thể che sự cố JVM nghiêm trọng.

**Lỗi 5 — Chỉ in message:** không đủ stack/cause để chẩn đoán trong điều kiện cần thiết.

**Lỗi 6 — In full stack trace ở mọi aspect:** một lỗi gây nhiều log trùng và lộ dữ liệu.

**Lỗi 7 — Không giải bọc InvocationTargetException:** lỗi reflection che lỗi target.

**Lỗi 8 — Đưa checked exception ra ngoài chữ ký hợp đồng:** có thể gây wrapper proxy không dự kiến.

**Lỗi 9 — Retry không kiểm soát:** lặp side effect khi lần đầu thất bại sau khi đã thực hiện tác dụng phụ.

**Lỗi 10 — Coi Future return là task success:** bỏ sót lỗi async xảy ra sau.

**Lỗi 11 — Logger failure được coi là lỗi nghiệp vụ:** không phân biệt nguồn lỗi.

**Lỗi 12 — Catch rồi printStackTrace nhưng không ném lại:** logging không thay exception contract.

**Lỗi 13 — Chỉ unit test từng advice:** không kiểm tra kết hợp nhiều aspect, precedence và masking.

**Lỗi 14 — Không định nghĩa fail-open/fail-closed:** hệ thống phản ứng ngẫu nhiên khi sink lỗi.

## 32. Checklist trước khi dùng catch trong advice

- [ ] Advice này là observer hay có quyền phục hồi?
- [ ] Original có hợp đồng throws rõ ràng?
- [ ] Catch có quá rộng không?
- [ ] Có nuốt Error hoặc IOException quan trọng?
- [ ] Return fallback có ý nghĩa đúng trong API?
- [ ] Nếu chuyển exception, cause có giữ không?
- [ ] Nếu cleanup cũng lỗi, primary có bị che không?
- [ ] Có dùng suppressed thích hợp cho secondary failure?
- [ ] Có xử lý checked exception đúng khi qua proxy?
- [ ] Có giải bọc InvocationTargetException khi cần?
- [ ] Có theo dõi lỗi async ở đúng completion boundary?
- [ ] Có phân biệt attempt failure với request outcome?
- [ ] Có test số lần Original trong retry/short-circuit?
- [ ] Có quyết định fail-open/fail-closed cho logging và audit?
- [ ] Có giới hạn dữ liệu nhạy cảm trong exception/log?
- [ ] Có integration test nhiều aspect ở cả normal và exceptional paths?

## 33. Bài tập tự kiểm tra

### Câu 1. Bắt rồi trả OK

Original ném IOException, advice bắt rồi trả “OK”. Caller còn bắt IOException được không?

**Đáp án:** không, nếu advice kết thúc bình thường. Đây là swallowing nếu API không cho phép recovery kiểu đó.

### Câu 2. Lỗi kép trong finally

Original ném IOException A, finally ném IllegalStateException B. Caller có thể thấy lỗi nào?

**Đáp án:** B có thể che A nếu code không bảo toàn primary.

### Câu 3. Cause và suppressed

Một domain exception được sinh ra bởi IOException; quan hệ nào thường phù hợp? Cleanup cùng lúc thất bại thì sao?

**Đáp án:** cause cho chuỗi nguyên nhân; suppressed phù hợp để giữ lỗi cleanup thứ cấp khi primary vẫn được ném.

### Câu 4. Outer after-throwing

Inner bắt IOException và trả fallback. Outer after-throwing có chắc nhận IOException?

**Đáp án:** không. Outer có thể chỉ thấy normal return.

### Câu 5. Checked exception qua dynamic proxy

Handler ném thẳng một checked exception ngoài throws của interface method. Có thể xảy ra điều gì?

**Đáp án:** cơ chế Java Dynamic Proxy có thể dùng UndeclaredThrowableException để bọc lỗi.

### Câu 6. Catch Throwable

Một around advice catch Throwable và trả default trong mọi trường hợp. Nguy cơ chính?

**Đáp án:** nuốt cả Error, che tình trạng nghiêm trọng và báo success giả.

### Câu 7. Retry

Một lời gọi thất bại sau khi đã ghi một side effect; retry có an toàn mặc định không?

**Đáp án:** không. Cần idempotency, transaction semantics và giới hạn loại lỗi.

### Câu 8. Future

Method trả CompletableFuture bình thường nhưng task sau đó lỗi. After-returning quanh method có chắc quan sát lỗi task?

**Đáp án:** không, nếu chỉ quan sát thời điểm method return.

### Câu 9. Logger trong finally

Original ném IOException; logger trong finally ném RuntimeException. Vì sao Caller nhận lỗi logger?

**Đáp án:** exception mới từ finally có thể che primary.

### Câu 10. Hai log khác nhau

Audit trong ghi attempt failure, Trace ngoài ghi request success sau recovery. Có chắc thông tin mâu thuẫn?

**Đáp án:** không. Chúng quan sát hai ranh giới và hai loại outcome khác nhau.

## 34. Kết luận

**Swallowing** khiến Caller không còn tín hiệu thất bại mà phần gốc đã tạo. **Masking** khiến lỗi mới hoặc wrapper che đi nguyên nhân gốc. Trong AOP, nguy cơ tăng lên vì nhiều advice có thể cùng bao một join point và thay đổi đường return/throw trước khi Caller nhìn thấy kết quả.

Các nguyên tắc cốt lõi:

- Một observer không nên tự chuyển lỗi thành success-shaped return.
- Log không thay được exception contract.
- Mỗi advice chỉ nhìn thấy trạng thái mà phần bên trong truyền ra.
- Finally/cleanup có thể che primary nếu tự ném lỗi.
- Cause và suppressed giúp bảo toàn quan hệ giữa các lỗi khác vai trò.
- Java checked exceptions và proxy/reflection cần được xử lý đúng hợp đồng.
- Không catch Throwable rồi giả vờ thành công.
- Retry/short-circuit thay đổi số lần Original chạy và lỗi được nhìn thấy.
- Async failure phải được quan sát tại completion boundary.
- Kiểm thử đường lỗi quan trọng không kém kiểm thử đường thành công.

**Câu hỏi cuối cùng khi viết catch trong advice:** *Tôi đang thực sự phục hồi lỗi này theo hợp đồng, hay chỉ nên quan sát và giữ nguyên tín hiệu thất bại cho thành phần chịu trách nhiệm tiếp theo?*


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-exception-contracts-005">Dọn dẹp tài nguyên khi advice hoặc mã gốc thất bại</a>

<details>
<summary>Xem chi tiết</summary>

# Dọn dẹp tài nguyên khi advice hoặc mã gốc thất bại trong AOP

## 1. Tại sao cleanup là một phần của hợp đồng?

Trong **Aspect-Oriented Programming (AOP)**, một lời gọi nghiệp vụ có thể đi qua nhiều **advice**: before, after returning, after throwing, after/finally và around. Advice có thể mở kết nối, file, trace span, lock, resource lease, buffer, transaction context hoặc một scope dữ liệu theo thread. Mã nghiệp vụ gốc cũng có thể mở tài nguyên riêng.

Nếu lời gọi thất bại ở bất kỳ mắt xích nào, control flow không tiếp tục theo đường bình thường. Tài nguyên đã mở phải được đóng theo **ownership/lifecycle contract** ngay cả khi target throw, advice throw, hoặc chính bước cleanup gặp lỗi.

~~~text
Caller
  |
  v
Around A: acquire resource A
  |
  v
Around B: acquire resource B
  |
  v
Before advice -> Target method -> After advice
                     |
                 return / throw
                     |
                     v
                 B cleanup
                     |
                     v
                 A cleanup
                     |
                     v
               Caller result/error
~~~

Năm bất biến cần thiết:

1. **Không leak:** tài nguyên đã acquire phải được release hoặc chuyển giao ownership rõ ràng.
2. **Không double-release:** không đóng hai lần nếu resource không hỗ trợ.
3. **Không close khi chưa acquire:** lỗi trong acquisition cần chỉ dọn những thành phần đã thành công.
4. **Không làm mất primary exception:** lỗi cleanup thứ cấp cần có chỗ ghi nhận, ví dụ suppressed exceptions.
5. **Không thay đổi ngữ nghĩa nghiệp vụ vô tình:** logging, metrics hoặc trace cleanup không được tùy tiện đổi return/exception.

Mục tiêu không chỉ là viết finally, mà là **định nghĩa chính xác ai sở hữu resource, resource sống đến khi nào, exception nào caller nhìn thấy và các lỗi cleanup được bảo tồn như thế nào**.

Bài tập trung AOP cơ bản và nâng cao nói chung. Ví dụ Java 17 dùng các hàm bảo vệ mô phỏng các advice scopes, không tự triển khai compiler hay bytecode weaving. Các quy tắc cụ thể của một ngôn ngữ AOP và thứ tự advice cần được kiểm tra theo công cụ thực tế.

## 2. Đâu là vị trí có thể phát sinh lỗi?

| Vị trí | Dạng lỗi | Câu hỏi cleanup |
|---|---|---|
| Before advice | Authorization/validation throw | Tài nguyên trước đó có được release không? |
| Acquire resource | Mở file/kết nối thất bại | Những resources đã mở trước đó được dọn chưa? |
| Around trước proceed | Setup trace/context fail | Target chưa chạy, outer resources có owner không? |
| Target/proceed | Business exception | Resources trong mọi nested scopes có release không? |
| Around sau proceed | Convert result / telemetry throw | Target đã return nhưng caller thấy exception? |
| After returning | Reporter hoặc metric fail | Có đổi normal return thành error? |
| After throwing | Logger error | Có che exception gốc? |
| After/finally | Close/unlock throw | Giữ lỗi chính hay để lỗi cleanup thay thế? |
| Transaction completion | Commit/rollback fail | Có xác định được durable outcome không? |
| Async completion | Task fail sau khi method return | Có đóng resource quá sớm không? |
| Retry | Nhiều lần proceed | Resource cần sống per attempt hay per request? |

Một operation có thể có **hơn một lỗi**. Ví dụ target throw X, inner resource close throw Y, outer resource close throw Z. Không thể chỉ chọn ngẫu nhiên exception cuối cùng; phải xác định **primary/secondary policy**.

## 3. Một lời gọi, nhiều scopes và cleanup LIFO

Với around composition:

~~~text
A(B(Target))
~~~

A acquire resource A trước; B acquire resource B sau. Khi unwind, B cleanup trước rồi A cleanup.

~~~text
open A
  open B
    target
  close B
close A
~~~

Đây là mô hình **last-in, first-out (LIFO)**, tương thích nested try/finally hoặc try-with-resources.

**Đặt close sau proceed không đủ:**

~~~text
resource = acquire()
result = proceed()    // có thể throw
resource.close()      // sẽ bị bỏ qua khi throw
return result
~~~

Mẫu an toàn hơn:

~~~text
resource = acquire()
try:
    return proceed()
finally:
    resource.close()
~~~

Finally có cơ hội chạy khi return/throw theo control-flow thông thường, nhưng **close tự nó vẫn có thể throw**, hoặc process có thể crash trước khi cleanup. Không được biến “finally đã bắt đầu” thành “resource release đã thành công”.

### 3.1. Trường hợp outer/inner advice cùng throw

Giả sử B.close throw trong khi Target đã throw. Outer A vẫn phải chạy cleanup nếu nó có finally thích hợp. Nếu A.close cũng throw, phải giữ đủ thông tin của tất cả lỗi cần chẩn đoán. Ngôn ngữ Java cho phép lưu các lỗi thứ cấp qua **suppressed exceptions**, nhưng một try/finally viết tay không tự bảo đảm điều đó.

### 3.2. Không có thứ tự áp dụng AOP phổ quát

Thứ tự trước/sau/around giữa nhiều aspect phụ thuộc **precedence**, loại join point và engine. Không suy ra một outer advice cố định chỉ từ tên class, thứ tự file hay annotation. Bài dùng mô hình A(B(Target)) để làm rõ semantics, không khẳng định đây là thứ tự mặc định của mọi bộ dệt.

## 4. Before advice có thể làm mọi inner advice biến mất

Before advice có thể ném exception **trước khi proceed hoặc target bắt đầu**. Khi ấy các advice nằm sâu bên trong có thể chưa từng được kích hoạt.

~~~text
Outer around: acquire A
  Before security: throw AccessDenied
Outer around: finally close A
Caller receives AccessDenied
~~~

Nếu trước khi throw, before advice tự acquire resource và không có local finally thì tài nguyên có thể rò. Không được mặc định after returning/after throwing của một join point sẽ bù lại lỗi phát sinh **trong chính before advice trước khi join point chạy**; semantics thật phụ thuộc cấu trúc.

Thiết kế tốt là bao các thao tác dễ lỗi bằng **một around scope có cleanup rõ**, hoặc bảo vệ từng resource do before advice acquire tại chỗ.

### 4.1. Nếu before advice chưa acquire?

Không close tài nguyên chưa tồn tại. Nếu before validation throw trước khi mở file, close(file) sẽ không có ý nghĩa. Nếu acquire thất bại giữa chừng, code tạo resource phải dọn những phần đã sở hữu trước khi lỗi phát ra.

## 5. After returning, after throwing và after/finally khác nhau

**After returning** thích hợp để ghi nhận một join point được quan sát đã trả bình thường. Nó **không** bảo đảm chạy khi target throw, nên không đủ cho cleanup tài nguyên cần dọn trên mọi đường.

**After throwing** ghi nhận exception thoát khỏi join point; nó **không** dọn trên normal return, vì thế cũng không đủ làm cleanup duy nhất.

**After/finally** thường bao cả hai đường exit theo semantics engine, phù hợp cho thao tác cần chạy dù thành công hay thất bại. Tuy nhiên nếu chính finally advice throw, nó có thể che lỗi cũ nếu không có cơ chế giữ secondary error.

**Around** bao toàn bộ continuation và có thể kiểm soát vòng đời acquire/use/release. Nó còn có thể short-circuit hoặc proceed nhiều lần (tùy engine); do đó cần test resource lifetime cho từng kiểu flow.

### 5.1. Không đồng nhất after returning với operation success

Business method có thể return OK nhưng outer transaction còn chưa commit, hoặc resource close về sau throw. Một after returning gắn tại method body không chứng minh **toàn bộ transaction/operation hoàn tất**.

Ghi log rõ mốc **business-returned**, **resource-closed** hay **transaction-committed**, thay vì dùng một nhãn SUCCESS mơ hồ.

## 6. Java try/finally: bảo đảm gọi cleanup theo control-flow

~~~java
Lease lease = acquire();
try {
    return execute();
} finally {
    lease.close();
}
~~~

Đây là snippet minh họa, không độc lập vì chưa định nghĩa Lease/acquire/execute. Khi execute return hoặc throw thông thường, finally chạy trước khi control flow rời operation.

**Nhưng nếu finally throw, nó có thể thay thế exception gốc**:

~~~java
try {
    throw new IllegalStateException("core-failed");
} finally {
    throw new IllegalStateException("cleanup-failed");
}
~~~

Caller có thể chỉ thấy cleanup-failed. Lỗi core-failed không tự trở thành suppressed trong dạng finally này. Đây là lý do nên xem xét try-with-resources hoặc một cơ chế ghi nhận primary/secondary được viết/test đúng.

Không cam kết finally luôn chạy dưới mọi tình huống vật lý. JVM/process crash, termination đột ngột, lỗi hệ thống hoặc mất điện có thể cắt ngang cleanup.

## 7. Java try-with-resources và AutoCloseable

Try-with-resources gọi close() trên tài nguyên **AutoCloseable** khi kết thúc block. Những resources đã khai báo được đóng theo thứ tự ngược lúc khởi tạo thành công.

~~~java
try (Lease lease = acquire()) {
    return execute();
}
~~~

Nếu body throw X và close throw Y:

- **X là primary exception**.
- **Y được lưu trong X.getSuppressed()**.
- Caller có thể phân tích cả hai mà không mất lỗi gốc.

Nếu body bình thường nhưng close throw Y:

- Toàn operation thoát bằng **Y**, không phải normal return.
- Một đoạn trong body ghi “business returned” có thể đã chạy, nhưng caller không nhận được kết quả bình thường.

Nếu acquire nhiều resources rồi bước khởi tạo resource sau thất bại, các resources đã mở thành công trước đó được đóng theo semantics try-with-resources. Nếu bản thân một constructor acquire tài nguyên nội bộ rồi throw trước khi trả object AutoCloseable, constructor/factory phải dọn phần nó đã sở hữu.

### 7.1. AutoCloseable có luôn idempotent?

Không được giả định. Một resource có thể cho phép close nhiều lần; một resource khác có thể báo lỗi hoặc sinh side effect nếu close lặp. Đọc hợp đồng implementation và tránh double ownership.

## 8. Primary, suppressed và cause: đừng nhầm

**Primary exception:** lỗi chính đi ra khỏi phạm vi.

**Suppressed exception:** lỗi phụ được gắn qua Throwable.addSuppressed/getSuppressed, thường do close thất bại khi body đã throw.

**Cause:** lý do gây ra một exception wrapper, được gắn bằng cause chain, không đồng nghĩa suppressed.

~~~text
IllegalStateException: core-failed
  suppressed:
    IllegalStateException: close-failed
~~~

Một try-with-resources có nhiều AutoCloseable có thể chứa **nhiều suppressed errors**. Không chỉ log primary rồi bỏ suppressed, vì đó có thể là dấu hiệu rò connection hoặc failure trong cleanup.

### 8.1. Những điều không nên giả định

- Primary luôn từ target: before, around, after hoặc close cũng có thể là primary.
- Finally throw tự động bị suppressed: không đúng với plain try/finally.
- Close throw chứng minh resource chưa từng được dọn: chỉ biết close đã báo lỗi, trạng thái cuối phụ thuộc resource.
- Close return bình thường chứng minh đã flush bền vững ra remote system: còn phụ thuộc contract I/O và durability.

## 9. Ví dụ Java 17 chạy được: sáu đường lỗi

Chương trình sau mô phỏng advice-like guard, before, target, after-like observation, Lease.close và outer finally. Dùng JDK 17 thuần; không phải một bytecode weaver hay một bộ công cụ AOP.

Tạo file **AdviceCleanupDemo.java**:

~~~java
public class AdviceCleanupDemo {
    static final class Lease implements AutoCloseable {
        private final String id;
        private final boolean failClose;

        Lease(String id, boolean failClose) {
            this.id = id;
            this.failClose = failClose;
            System.out.println("resource:open " + id);
        }

        @Override
        public void close() {
            System.out.println("resource:close " + id);
            if (failClose) {
                throw new IllegalStateException("close-failed");
            }
        }
    }

    static String operation(String id, boolean failBefore,
                            boolean failCore, boolean failClose,
                            boolean failAfter) {
        System.out.println("guard:enter");
        try {
            System.out.println("advice:before");
            if (failBefore) {
                throw new IllegalArgumentException("before-failed");
            }

            try (Lease lease = new Lease(id, failClose)) {
                System.out.println("core:execute");
                if (failCore) {
                    throw new IllegalStateException("core-failed");
                }
                System.out.println("advice:observed-core-return");
                if (failAfter) {
                    throw new IllegalStateException("after-failed");
                }
                return "OK";
            }
        } finally {
            System.out.println("guard:finally");
        }
    }

    static void scenario(String id, boolean before, boolean core,
                         boolean close, boolean after) {
        System.out.println("=== " + id + " ===");
        try {
            System.out.println("caller:result " +
                operation(id, before, core, close, after));
        } catch (RuntimeException error) {
            System.out.println("caller:error " + error.getMessage());
            for (Throwable suppressed : error.getSuppressed()) {
                System.out.println("caller:suppressed " + suppressed.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        scenario("success", false, false, false, false);
        scenario("before-fails", true, false, false, false);
        scenario("core-fails", false, true, false, false);
        scenario("core-and-close-fail", false, true, true, false);
        scenario("close-fails", false, false, true, false);
        scenario("after-and-close-fail", false, false, true, true);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 AdviceCleanupDemo.java
java AdviceCleanupDemo
~~~

**Stdout chính xác đã được kiểm chứng:**

~~~text
=== success ===
guard:enter
advice:before
resource:open success
core:execute
advice:observed-core-return
resource:close success
guard:finally
caller:result OK
=== before-fails ===
guard:enter
advice:before
guard:finally
caller:error before-failed
=== core-fails ===
guard:enter
advice:before
resource:open core-fails
core:execute
resource:close core-fails
guard:finally
caller:error core-failed
=== core-and-close-fail ===
guard:enter
advice:before
resource:open core-and-close-fail
core:execute
resource:close core-and-close-fail
guard:finally
caller:error core-failed
caller:suppressed close-failed
=== close-fails ===
guard:enter
advice:before
resource:open close-fails
core:execute
advice:observed-core-return
resource:close close-fails
guard:finally
caller:error close-failed
=== after-and-close-fail ===
guard:enter
advice:before
resource:open after-and-close-fail
core:execute
advice:observed-core-return
resource:close after-and-close-fail
guard:finally
caller:error after-failed
caller:suppressed close-failed
~~~

### 9.1. Success

Resource được mở, core chạy, đoạn quan sát core return chạy, close thực thi rồi outer finally chạy. Caller nhận OK.

### 9.2. Before fail

Before ném lỗi **trước khi Lease tồn tại**, vì vậy không có open/close. Outer finally vẫn chạy. Nếu before tự acquire một resource phần nào rồi fail, nó vẫn phải cleanup phần đó, nhưng demo không có phần acquire trước lỗi này.

### 9.3. Core fail

Core throw **core-failed**, try-with-resources vẫn close và finally vẫn chạy. Đoạn quan sát core return không chạy.

### 9.4. Core fail và close fail

Core error là primary. Close error được giữ trong **getSuppressed()**, nên caller in cả core-failed và close-failed.

### 9.5. Core thành công nhưng close fail

Đoạn after-like nhận thấy core return, nhưng close throw và caller không nhận OK. Điều này cho thấy **body returned không đồng nghĩa overall operation returned**.

### 9.6. After-like và close cùng fail

After-like ném **after-failed** trước lúc rời try-with-resources. Close throw tiếp, nhưng after-failed là primary; close-failed suppressed.

**Lưu ý:** after-like là code Java đặt trong try block để minh họa, không phải một advice implementation có semantics chung cho mọi engine AOP.

## 10. Ownership: phân biệt own và borrow

**Owner của resource** là scope/component có trách nhiệm cuối cùng để release. Một resource nên có ownership rõ, thay vì mọi advice có reference đều nghĩ có quyền close.

| Resource | Owner ví dụ | Cleanup |
|---|---|---|
| File mở trong advice | Advice đó | try-with-resources |
| Temporary buffer | Scope tạo nó | release/recycle nếu contract yêu cầu |
| Trace span mở tại boundary | Scope tạo span | mark error/end |
| Lock do advice acquire | Advice giữ lock | unlock đúng thread/scope |
| Transaction context | Boundary transaction | commit/rollback/restore/release |
| Connection do caller cấp cho target | Caller hoặc owner đã thỏa thuận | Advice chỉ borrow |
| Stream được trả cho caller | Caller sau khi ownership transfer | Caller close theo API |

**Mượn resource không đồng nghĩa sở hữu nó.** Nếu advice chỉ nhận connection từ transaction đang hoạt động, đóng connection trước khi transaction owner kết thúc có thể phá operation.

### 10.1. Khi resource được trả ra ngoài

Nếu target trả một InputStream để caller đọc tiếp, advice không được mở stream rồi **close ngay trong finally trước khi caller dùng**, trừ khi API cố ý trả dữ liệu đã đọc vào memory hoặc một abstraction khác. Vòng đời stream cần kéo dài qua caller usage hoặc ownership phải được chuyển cho caller.

### 10.2. Ownership transfer và close-once

Chuyển ownership cần một hợp đồng chính xác: khi nào transfer thành công, nếu transfer fail ai close, có còn holder cũ sử dụng hay không. Một wrapper close-once không tự sửa lỗi race hoặc transfer không rõ.

## 11. Resource acquisition có thể thất bại từng phần

Giả sử một aspect:

1. Mở connection A.
2. Mở file B.
3. Đăng ký listener C.

Nếu bước 2 throw, A cần release. Nếu bước 3 throw, cả A/B cần cleanup.

~~~text
acquire A
try:
    acquire B
    try:
        acquire C
        try:
            proceed
        finally:
            release C
    finally:
        release B
finally:
    release A
~~~

Đây là giả mã minh họa LIFO, không phải Java chạy độc lập.

Nested try-with-resources có thể thực hiện việc đóng những AutoCloseable đã được khởi tạo thành công khi resource khai báo sau thất bại. Nhưng nếu constructor/factory cấp phát một số native handles rồi throw trước khi trả object, phải tự dọn những handles đó bên trong acquisition implementation.

## 12. Khi resource close cũng thất bại

Một lỗi cleanup có hai chiều:

- **Độ tin cậy:** tài nguyên có thể đã/ chưa được release, cần cảnh báo/đo trạng thái.
- **Ngoại lệ:** caller có thể cần xem failure theo error contract.

### 12.1. Target đã throw

Ưu tiên giữ target exception khi nó là lỗi nghiệp vụ chính, đính kèm close failure thành secondary/suppressed. Try-with-resources cung cấp cấu trúc phù hợp cho AutoCloseable.

### 12.2. Target bình thường

Nếu close là điều kiện để operation hoàn tất, close throw có thể là primary và caller không nhận normal result. Ví dụ flush buffer hoặc close stream có thể phát hiện lỗi I/O sau khi body đã ghi dữ liệu.

### 12.3. Logger fail trong cleanup

Nếu mục tiêu của logger chỉ là observability best-effort, lỗi exporter thường không nên vô tình che business error. Với audit bắt buộc, failure có thể yêu cầu fail-closed, nhưng phải là chính sách rõ, không do IOException tình cờ từ reporter.

### 12.4. Cleanup failure không luôn retry được

Nếu close có thể commit/flush tác vụ đã xảy ra, retry toàn bộ business operation khi close throw có thể tạo duplicate. Hãy xác minh resource state, idempotency và error recovery semantics.

## 13. Lock và Semaphore: release chỉ khi đã acquire

Với lock, resource contract khác file:

~~~java
lock.lock();
try {
    return proceed();
} finally {
    lock.unlock();
}
~~~

Đây là snippet minh họa, chưa có lock/proceed. Cách đặt try ngay sau lock.lock phù hợp nếu lock() chỉ return sau khi acquire thành công theo contract. Với tryLock trả boolean hoặc lockInterruptibly ném InterruptedException, cần kiểm tra có thực sự acquire trước khi unlock.

Với Semaphore, chỉ **release permit đã acquire**. Nếu acquire throw/interrupted trước khi lấy permit mà finally vẫn release, số permit có thể sai.

### 13.1. Thread affinity

Một số lock chỉ được unlock bởi thread owner. Nếu advice mở resource ở thread A nhưng cleanup callback chạy ở thread B, có thể vi phạm contract. **Không di chuyển cleanup sang thread khác** chỉ vì dùng future callback mà chưa xác minh resource hỗ trợ.

## 14. ThreadLocal context: restore thay vì clear tùy tiện

Một nested advice có thể ghi trace/security context vào ThreadLocal:

~~~text
Trước inner advice: Context = A
Trong inner advice: Context = B
Sau inner advice: Context phải trở về A
~~~

Nếu inner finally chỉ **remove** mà không biết A tồn tại, nó phá outer context. Với scope lồng nhau, hãy lưu previous value và restore trong finally.

Với một request scope do chính bạn tạo mới, remove có thể đúng nếu không có prior value. Hãy tài liệu hóa rõ khác biệt.

Không để context từ request cũ rò sang request mới trên worker thread tái sử dụng. Các thread-local state có thể ảnh hưởng tracing, security, locale hoặc transactional routing; lỗi cleanup context là lỗi correctness, không chỉ sai log.

## 15. Retry làm thay đổi resource lifetime

Xét:

~~~text
Retry(Resource(Target))
~~~

Nếu Retry gọi proceed ba lần, mỗi attempt có thể vào Resource mới, acquire và close mới. Điều này phù hợp nếu mỗi attempt cần một transaction/connection scope độc lập.

Ngược lại:

~~~text
Resource(Retry(Target))
~~~

Có thể chỉ mở một resource rồi chạy nhiều attempts trong cùng scope. Điều này có thể không phù hợp nếu resource đã ở trạng thái lỗi hoặc transaction bị đánh dấu rollback-only.

### 15.1. Idempotency khi retry

Một attempt có thể tạo side effect rồi throw lúc close/commit. Caller không biết outcome cuối có thành công không. Retry mà không có idempotency key/reconciliation có thể nhân đôi order, payment hoặc message.

### 15.2. Lỗi của các attempts trước

Nếu chỉ ném exception của attempt cuối, lỗi cleanup ở các attempts trước có thể bị mất. Logging/trace có thể giữ attempt ID và secondary diagnostics, hoặc error contract có thể tổng hợp các lỗi; nhưng không được tự tạo cause/suppressed cycle.

## 16. Async và cancellation

Một around advice đơn giản:

~~~text
resource = open()
try:
    return proceed()  // trả CompletableFuture rất nhanh
finally:
    resource.close()
~~~

Nếu future vẫn đang chạy và cần resource ấy, close đã xảy ra **quá sớm**. Cần thiết kế vòng đời resource theo **actual task completion**, không chỉ thời điểm method trả handle.

Nhưng không được tùy tiện chuyển lock/transaction/thread-bound context sang async callback: thread affinity và resource manager constraints có thể khiến việc dọn trên thread khác sai.

### 16.1. Cancellation không đồng nghĩa task đã dừng

Cancel request có thể tới khi task đang thực thi, hoặc khi child operations vẫn giữ resource. Hãy xác định:

- Khi nào task thật ngừng sử dụng resource?
- Ai được quyền cancel và release?
- Release có được gọi đúng một lần qua race giữa complete/error/cancel?
- Cleanup có blocking không?
- Có cần interrupt signal không?
- Context/correlation ID được truyền bằng cách nào?

### 16.2. InterruptedException

Nếu code bị InterruptedException, thường cần rethrow khi API cho phép hoặc restore interrupt flag nếu phải catch và tiếp tục theo policy; không nuốt interruption để che cancellation. Resource cleanup nên chạy trong finally nếu đã acquire.

## 17. Reflection và hợp đồng exception

Proxy/interceptor qua reflection có thể nhận **InvocationTargetException** bọc lỗi target. Nếu handler chuyển thẳng wrapper, caller có thể nhận loại exception khác với hợp đồng method.

Khi cần preserve target error, lấy cause theo contract, đồng thời bảo toàn suppressed exceptions và stack/context cần thiết. Không catch Throwable rồi biến tất cả thành một RuntimeException không có cause.

**Checked exceptions** còn phụ thuộc method signature và invocation API; với JDK dynamic proxy, undeclared checked exception có quy tắc wrapping riêng. Hãy kiểm tra interface contract trước khi translate error.

### 17.1. Error nghiêm trọng

Finally thường được thực hiện trong stack unwinding thông thường kể cả khi một Error được throw, nhưng các tình huống process termination/crash không cho bảo đảm. Không tự swallow mọi Error rồi giả vờ resource cleanup làm chương trình an toàn để tiếp tục.

## 18. Resource management và transaction

Transaction aspect có thể phải phân biệt:

~~~text
begin transaction
try:
    result = proceed()
    commit
    return result
catch:
    rollback nếu policy cho phép
    rethrow
finally:
    restore transaction context
    release connection
~~~

Commit, rollback, context restore và connection release là những bước **khác nhau**.

- Business throw trước commit: rollback có thể được thực hiện theo policy.
- Commit throw: durable outcome có thể không chắc chắn; rollback attempt không chứng minh rollback đã thành công.
- Rollback throw: phải giữ thông tin về business error và rollback error.
- Release connection throw: có thể khiến caller nhìn thấy lỗi sau khi transaction đã xong.
- Logging trong finally throw: có thể che lỗi quan trọng.

Không coi close() của một connection bất kỳ là đồng nghĩa commit/rollback. Khi transaction manager có state machine riêng, phải tuân thủ API và visibility của nó.

### 18.1. Side effects ngoài transaction

Một email gửi đi, HTTP call hoặc event publish trước khi target throw không tự được hoàn tác bằng database rollback. Cleanup resource đúng **không tạo atomicity cho mọi side effect**. Outbox, idempotency hoặc compensation cần được thiết kế theo nghiệp vụ.

## 19. Reentrancy và các advice tự gọi chính nó

Một logging advice có thể gọi logger.write. Nếu pointcut chọn cả logger.write, hệ thống có thể rơi vào recursion:

~~~text
logging advice
  -> logger.write
      -> logging advice
          -> logger.write
              -> ...
~~~

Các hướng phòng tránh:

- Exclude infrastructure methods khỏi pointcut phù hợp.
- Dùng reentrancy guard có scope đúng nếu engine hỗ trợ.
- Restore guard trong finally dù logger throw.
- Tránh global mutable guard không thread-safe.
- Test nested calls, multi-thread và trường hợp logger throw.

Nested advice hợp lệ vẫn có thể cần nhiều scopes. Phân biệt nested business operations có chủ ý với recursion vô hạn do instrumentation.

## 20. Observability: đo cleanup đúng tầng

Nếu chỉ log **finally entered**, không chứng minh resource release thành công. Theo dõi:

| Sự kiện | Ý nghĩa |
|---|---|
| acquire.start | Bắt đầu lấy quyền sở hữu |
| acquire.success | Resource thuộc ownership của scope |
| release.attempt | Cleanup được gọi |
| release.success | Cleanup báo thành công |
| release.failure | Cleanup báo lỗi |
| operation.primary.failure | Lỗi chính |
| operation.suppressed.failure | Secondary error |
| operation.completed | Scope hoàn tất theo định nghĩa |

Có thể gắn **requestId, attemptId, resourceId, ownerScope, joinPointKind, exceptionType**. Không lưu credentials, token hoặc dữ liệu nhạy cảm trong exception messages.

Số acquire bằng số release **attempts** chỉ chứng minh release được gọi, không chứng minh hệ thống đã giải phóng tài nguyên vật lý. Phân biệt success/failure/unknown theo contract.

## 21. Ma trận kiểm thử

| Tình huống | Cleanup | Caller/diagnostic |
|---|---|---|
| Before fail trước acquire | Không close resource chưa có | Before error |
| Acquire một phần rồi fail | Dọn phần đã acquire | Acquire error, close secondary nếu có |
| Target return | Close đúng một lần | Return khi close success |
| Target throw | Close đúng một lần | Target primary |
| Target throw + close throw | Attempt close | Target primary, close suppressed |
| Target return + close throw | Attempt close | Close error có thể là primary |
| After-like throw | Outer cleanup chạy | After primary |
| After-like + close throw | Attempt close | After primary, close suppressed |
| Hai resources lồng nhau | Đóng inner trước outer | Giữ primary và các suppressed |
| Advice short-circuit | Không proceed | Cleanup resource đã mở |
| Retry ba attempts | Acquire/release đúng scope | Attempt metrics/exception chain |
| Async chưa hoàn tất | Không close quá sớm | Future completion semantics |
| Cancellation | Không use-after-close | Cancel/error contract |
| ThreadLocal nested | Restore giá trị trước | Không context leak |
| Logger throw trong catch | Không vô tình che lỗi gốc | Original error còn truy vết |
| Commit/rollback error | Cleanup context theo policy | Không đoán outcome |
| Lock không acquire | Không unlock sai | Permit/ownership invariant |
| Multi-thread | Không release sai thread/race | Test concurrency |

### 21.1. Fault injection

Hãy cố ý gây lỗi tại các giai đoạn: trước acquire, giữa nhiều acquires, trong target, trong after-like, trong inner close, trong outer close, khi logging failure, trong retry và khi async callback xử lý. Với từng ca kiểm tra:

1. Resource nào đã thật sự được mở.
2. Resource nào đã được close attempt.
3. Thứ tự close có đúng ownership/LIFO không.
4. Caller nhận primary exception nào.
5. Suppressed/cause có còn đầy đủ.
6. Không có double close/use-after-close.
7. ThreadLocal/lock state được restore.
8. Không có side effect trái policy.

## 22. Những lỗi thường gặp

**Lỗi 1 — Đặt close sau proceed.** Proceed throw sẽ bỏ qua close.

**Lỗi 2 — Dọn chỉ trong after returning.** Target throw làm mất cleanup.

**Lỗi 3 — Chỉ dùng after throwing.** Normal return vẫn cần release.

**Lỗi 4 — Finally throw che lỗi gốc.** Cần policy primary/secondary, try-with-resources khi phù hợp.

**Lỗi 5 — Cho rằng finally luôn chạy dù JVM/process crash.** Không đúng.

**Lỗi 6 — Gọi close dù acquire fail trước đó.** Có thể NPE hoặc release sai resource.

**Lỗi 7 — Không dọn khi acquire được một phần.** Tài nguyên mở trước bước lỗi có thể leak.

**Lỗi 8 — Close resource chỉ được mượn.** Gây use-after-close cho owner thực.

**Lỗi 9 — Close hai lần và cho rằng idempotent.** Không được giả định từ AutoCloseable chung.

**Lỗi 10 — Coi suppressed là cause.** Hai quan hệ exception khác nhau.

**Lỗi 11 — Logging failure làm thay exception nghiệp vụ.** Có thể ảnh hưởng rollback/caller.

**Lỗi 12 — Return stream đã bị finally close.** Resource lifetime không khớp ownership transfer.

**Lỗi 13 — Retry trong một resource scope đã hỏng.** Có thể dùng lại transaction rollback-only.

**Lỗi 14 — Async method trả future nên close ngay.** Task có thể vẫn cần resource.

**Lỗi 15 — Cleanup lock trên thread khác.** Có thể vi phạm thread ownership.

**Lỗi 16 — Remove ThreadLocal phá outer context.** Nested scope cần restore previous value.

**Lỗi 17 — Audit chỉ đo finally entered.** Không biết release có thành công.

**Lỗi 18 — Logger bị pointcut intercept chính nó.** Gây reentrancy/recursion.

**Lỗi 19 — Coi close/rollback như nhau.** Transaction outcome khác connection/resource cleanup.

**Lỗi 20 — Tin cleanup đúng nghĩa mọi side effect được undo.** External effects cần idempotency/compensation/outbox.

## 23. Checklist triển khai

1. **Liệt kê resource** mà mỗi advice và target có thể acquire.
2. **Chỉ định owner** cụ thể, phân biệt borrow/transfer.
3. **Chọn lifetime** theo invocation, request, attempt hoặc async completion.
4. **Đọc contract** của close/unlock/commit/rollback/context restore.
5. **Dùng scope cleanup đúng**: try-with-resources hoặc finally phù hợp.
6. **Bảo vệ acquire một phần** và chỉ release các resources đã có.
7. **Quy định primary/secondary exceptions** và logging không che lỗi.
8. **Kiểm tra advice precedence**, short-circuit và proceed nhiều lần.
9. **Xem xét concurrency/async** và thread affinity.
10. **Xác định retry/idempotency** cho failure tại close/commit.
11. **Kiểm tra reentrancy** nếu advice gọi infrastructure.
12. **Viết tests success, target throw, advice throw và close throw**.
13. **Theo dõi metrics release success/failure**, không chỉ finally count.
14. **Tài liệu hóa giới hạn** của engine AOP và tài nguyên.

## 24. Câu hỏi ôn tập

**Câu 1.** Vì sao resource.close sau proceed mà không có finally có thể leak?

**Đáp:** Nếu proceed throw, control flow bỏ qua close.

**Câu 2.** Finally có đảm bảo close thành công không?

**Đáp:** Không, nó chỉ tạo cơ hội gọi cleanup trên đường rời scope thông thường; close có thể throw.

**Câu 3.** Body throw X, close throw Y trong try-with-resources: caller thấy gì?

**Đáp:** X là primary, Y thường ở X.getSuppressed().

**Câu 4.** Body return OK, close throw Y: caller nhận OK không?

**Đáp:** Không; operation thoát bằng Y.

**Câu 5.** Before throw trước khi Lease được tạo: có close Lease không?

**Đáp:** Không. Chưa acquire thì không close.

**Câu 6.** Acquire A thành công, acquire B throw: A phải thế nào?

**Đáp:** A cần cleanup theo ownership contract; nested try-with-resources giúp việc này.

**Câu 7.** After returning có thể thay thế finally để release resource không?

**Đáp:** Không, vì nó không bao đường target throw.

**Câu 8.** Tại sao logging exception trong finally nguy hiểm?

**Đáp:** Nó có thể che primary và thay outcome của tầng ngoài.

**Câu 9.** Retry(Resource(Target)) khác Resource(Retry(Target)) ra sao?

**Đáp:** Cấu trúc đầu có thể acquire/close mỗi attempt, cấu trúc sau dùng một scope cho nhiều attempts.

**Câu 10.** CompletableFuture chưa completed nhưng around finally close resource ngay: có nguy cơ gì?

**Đáp:** Async task có thể use-after-close; cần scope theo lifetime thực.

**Câu 11.** Có thể unlock bất kể lock.lockInterruptibly có thành công không?

**Đáp:** Không; chỉ unlock sau khi đã acquire hợp lệ.

**Câu 12.** Nested ThreadLocal scope chỉ remove trong finally có luôn đúng?

**Đáp:** Không, nếu outer scope có context cũ thì phải restore nó.

**Câu 13.** Suppressed exceptions có phải cause chain không?

**Đáp:** Không, suppressed lưu lỗi thứ cấp trong lúc unwind/cleanup.

**Câu 14.** Commit throw có đồng nghĩa database chắc đã rollback không?

**Đáp:** Không; outcome có thể uncertain.

**Câu 15.** Aspect trả InputStream đã đóng do finally thì sai ở đâu?

**Đáp:** Resource ownership/lifetime không phù hợp với API caller cần sử dụng.

**Câu 16.** Vì sao cần đếm release.success riêng release.attempt?

**Đáp:** Bước release có thể chạy nhưng throw; không được kết luận resource đã dọn thành công.

## 25. Sơ đồ ghi nhớ

~~~text
Caller
  |
  v
Outer around: acquire A
  |
  +-- try
       |
       v
     Inner around: acquire B
       |
       +-- try-with-resources
              |
              v
          Before / Target / After-like
                 |
               return / throw
                 |
                 v
            close B (can throw)
                 |
                 v
            primary + suppressed
       |
       v
     outer finally: cleanup A
  |
  v
Caller result/error

Bất biến:
- Owner rõ, scope rõ.
- Dọn những resource đã acquire.
- Đóng inner trước outer khi phù hợp.
- Không vô ý che primary exception.
- Đừng coi cleanup attempt là cleanup success.
~~~

**Kết luận:** khi advice hoặc mã gốc thất bại, việc dọn tài nguyên phải được thiết kế theo **ownership, scope, exception contract và precedence**. Với Java, dùng try/finally và try-with-resources đúng chỗ, tận dụng suppressed exceptions để bảo tồn lỗi cleanup thứ cấp. Với retry, transaction, thread-local context và async, hãy mở rộng phân tích từ method return tới **vòng đời thật của tài nguyên** và kiểm thử fault injection đầy đủ.

## Tài liệu đối chiếu

- [Java SE 17 — AutoCloseable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html): vòng đời close và AutoCloseable contract.
- [Java Language Specification SE 17 — try](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20): try/finally và try-with-resources.
- [Java SE 17 — Throwable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): suppressed exceptions và cause.
- [Java SE 17 — InvocationTargetException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationTargetException.html): reflection exception wrapper.
- [Java SE 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): completion/cancellation trong async APIs.
- [AspectJ Programming Guide](https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html): mô hình join point, advice và precedence ở một ngôn ngữ AOP cụ thể.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-exception-contracts-006">Tác dụng phụ và tính nguyên tử của thao tác</a>

<details>
<summary>Xem chi tiết</summary>

# Tác dụng phụ và tính nguyên tử của thao tác

## 1. Mục tiêu: một lời gọi thất bại không có nghĩa mọi thay đổi được hoàn tác

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP)**, **pointcut** chọn những *join point* thuộc mô hình mà công cụ hỗ trợ, còn **advice** bổ sung hành vi như audit, logging, đo lường, kiểm quyền, retry hoặc caching. Một aspect có thể ảnh hưởng không chỉ kết quả trả về, mà còn **các tác dụng phụ** trước, trong và sau lần thực thi target.

Ví dụ:

~~~text
Caller -> AuditBefore -> Target
           |              |
        ghi sự kiện     tăng số dư
                          |
                          +-> ném ngoại lệ
                  |
Caller nhận ngoại lệ, nhưng audit và số dư đã thay đổi
~~~

**Java exception không tự rollback state.** Một `catch` nhìn thấy lỗi không chứng minh rằng nghiệp vụ chưa thực hiện thao tác ghi. Một `after-returning` chạy cũng không chứng minh mọi thay đổi đã commit bền vững, bởi giao dịch có thể nằm ở lớp bao ngoài hoặc tác vụ bất đồng bộ còn tiếp tục sau khi method return.

Bài này tập trung vào ba câu hỏi:

1. **Tác dụng phụ nào đã xảy ra?** DB update, cache, file, network request, event delivery, metrics, field mutable?
2. **Ai có trách nhiệm đảm bảo tính nguyên tử?** Advice, tài nguyên giao dịch, domain service hay workflow phối hợp nhiều hệ thống?
3. **Caller thực sự được hứa điều gì?** Trả result, ném exception, trạng thái sau lỗi, khả năng retry, idempotency và quyền quan sát?

Không có một “atomicity mặc định của AOP”. Around advice có thể đặt ranh giới transaction hoặc điều phối bù trừ, nhưng phải có **cơ chế tài nguyên thực sự** để đảm bảo thuộc tính được tuyên bố.

## 2. Các thuật ngữ cốt lõi

| Thuật ngữ | Diễn giải |
| --- | --- |
| **Side effect** | Thay đổi quan sát được ngoài giá trị trả về thuần của hàm |
| **Join point** | Vị trí/sự kiện chương trình mà mô hình AOP cho phép can thiệp |
| **Advice** | Hành vi trước, sau, khi lỗi hoặc bao quanh một điểm được chọn |
| **Proceed** | Thao tác chuyển tiếp từ around advice sang lớp bên trong/target, theo API của engine |
| **Atomicity** | Một tập thay đổi nằm trong ranh giới được áp dụng như một đơn vị theo bảo đảm đã xác định |
| **Commit** | Thời điểm cơ chế lưu trữ/giao dịch xác nhận trạng thái đã được ghi nhận |
| **Rollback** | Hủy thay đổi chưa được commit trong phạm vi giao dịch hỗ trợ |
| **Compensation** | Một thao tác nghiệp vụ mới bù lại tác dụng đã xảy ra |
| **Idempotency** | Lặp lại cùng logical request không nhân đôi hiệu quả đã được định nghĩa |
| **Unknown outcome** | Caller chưa xác định được commit đã xảy ra hay chưa |
| **Transactional outbox** | Ghi ý định phát event cùng transaction với thay đổi dữ liệu tại một DB |
| **Audit event** | Sự kiện quan sát có nghĩa cụ thể như attempted, committed, delivered |
| **Exception safety** | Mức bảo toàn trạng thái/invariant khi thao tác ném lỗi |

**Tác dụng phụ không nhất thiết có hại**: ghi audit, cập nhật giao dịch và gửi thông báo là nhu cầu thật. Rủi ro đến từ việc gắn sai **thời điểm và phạm vi** cho một hiệu quả, rồi diễn giải `return/throw` thành lời bảo đảm không tồn tại.

### 2.1. Các loại tác dụng phụ điển hình

- **Trong heap:** tăng counter, sửa field, thêm vào collection, cập nhật static cache.
- **Tài nguyên local:** ghi file, đóng/mở socket, khóa, thread, subscription.
- **Lưu trữ nghiệp vụ:** insert/update/delete bản ghi, đổi số dư, đặt trạng thái đơn.
- **Hệ thống khác:** HTTP call, gửi email/SMS, cổng thanh toán, message broker.
- **Quan sát:** ghi log, audit, metrics, trace, phát thông báo.
- **Điều khiển luồng:** retry khiến mục tiêu chạy nhiều lần, cache không gọi target, fallback biến lỗi thành return thành công.

Tác dụng phụ cuối cùng cũng có thể được tạo bởi chính **advice**, không chỉ target.

## 3. Atomicity không giống isolation, durability hay idempotency

Không được gọi tất cả các phẩm chất sau bằng từ “nguyên tử”:

| Thuộc tính | Hợp đồng | Không chứng minh |
| --- | --- | --- |
| **Atomicity** | Các thay đổi trong phạm vi commit cùng nhau hoặc không thay đổi theo contract | Không tự bao các resource bên ngoài phạm vi |
| **Consistency** | Invariant miền vẫn hợp lệ | Không thay thế validation đúng |
| **Isolation** | Các thao tác đồng thời không tạo quan sát trái mức cô lập đã hứa | Atomic commit chưa chắc serializable |
| **Durability** | Thay đổi đã commit tồn tại qua những sự cố được bảo đảm | State RAM và Java lock không tự bền vững |
| **Idempotency** | Lặp logical request không nhân đôi kết quả được định nghĩa | Không có nghĩa operation thực thi vật lý đúng một lần |
| **Compensation** | Thêm một nghiệp vụ bù trạng thái | Không tương đương undo atomically/invisibly |

Một `synchronized` method giúp nhiều thread vào cùng object theo một thứ tự có kiểm soát, nhưng **không tạo transaction database, không rollback ghi trước đó khi throw và không bảo vệ nhiều node**.

Nếu nói “cập nhật số dư và audit là atomic”, phải hỏi **chúng nằm cùng database transaction nào, cùng object snapshot nào, hay chỉ cùng một Java call stack?** Chỉ trường hợp có cơ chế liên kết thực sự mới có bảo đảm.

## 4. Ngoại lệ qua advice không tự undo tác dụng phụ

Một chuỗi minh họa:

~~~text
Caller
  |
  +-> BEFORE: ghi "attempted"
       |
       +-> AROUND: mở scope
             |
             +-> TARGET: mutate state
                   |
                   +-> throw error
             |
             +-> AFTER_THROWING: ghi failure
       |
  +-> FINALLY: close scope
Caller <- exception
~~~

Trong chuỗi này có thể tồn tại ít nhất **ba tác dụng phụ**: audit attempted, mutation của target và event failure. Nếu target ném lỗi, chúng **không tự biến mất**.

**After-throwing** có thể biết exceptional completion ở một ranh giới, nhưng không có cơ chế thần kỳ để đảo ngược DB write, external HTTP call hay email đã gửi.

**Finally** thường phù hợp để giải phóng lock, restore ThreadLocal hoặc đóng resource. Nó cũng **không tự là rollback**. Nếu `close()` tự ném lỗi, caller có thể thấy lỗi cleanup thay vì nguyên nhân từ nghiệp vụ.

### 4.1. Before advice gây tác dụng phụ

Nếu `before` ghi `ORDER_SUCCESS` trước khi mục tiêu chạy, event không phản ánh sự thật khi target throw. Nếu nó ghi `ORDER_ATTEMPTED`, ý nghĩa có thể đúng. **Tên và phase của event phải phản ánh thời điểm xảy ra**, không suy diễn từ dự đoán outcome.

### 4.2. After-returning chưa chắc là sau commit

Một target return result bình thường nhưng outer transaction wrapper chưa commit. Nếu after-returning advice ghi `BUSINESS_COMMITTED` trước outer commit, event có thể bị sai nếu commit thất bại.

Ngược lại, một advice nằm **ngoài** transaction wrapper có thể chạy sau commit; nếu nó gửi external audit và lỗi, caller nhìn thấy exception **sau khi nghiệp vụ đã commit**.

### 4.3. After-throwing chưa chắc luôn được vào

Inner around advice có thể catch target exception và return fallback, khiến outer advice thấy normal return. Do đó một outer after-throwing không nhất thiết chạy dù target đã thất bại; điều này phụ thuộc **ranh giới quan sát**.

### 4.4. Return trong finally rất nguy hiểm

~~~java
try {
    throw new IllegalStateException("business failed");
} finally {
    // Không trả result ở đây: return sẽ che lỗi trước đó.
}
~~~

Đây là fragment minh họa chưa phải chương trình độc lập. Nếu `finally` có `return` hoặc ném exception mới, kết quả caller nhận có thể khác hẳn lỗi gốc.

## 5. Các mức bảo đảm an toàn khi ném ngoại lệ

Có thể mô tả bốn mức:

1. **No guarantee:** khi lỗi, state có thể không hợp lệ hoặc không biết đã xảy ra những gì.
2. **Basic guarantee:** invariant tối thiểu và ownership còn hợp lệ; state có thể thay đổi.
3. **Strong guarantee:** trong phạm vi xác định, thao tác lỗi không để lại thay đổi quan sát; tương tự một “commit hoặc không gì cả” ở phạm vi ấy.
4. **No-throw guarantee:** thao tác không để exception thoát ra theo hợp đồng của nó.

Không tự gán bất cứ mức nào cho một AOP engine. Mỗi advice phải được thiết kế:

- Một metrics observer có thể đặt mục tiêu **không ảnh hưởng kết quả nghiệp vụ** khi sink metrics lỗi.
- Một authorization advice có thể chọn **fail-closed** khi không xác thực được.
- Một transaction advice có thể cung cấp rollback cho tài nguyên được **enlist** đúng, không cho tất cả side effects ngoài transaction.
- Một around retry có thể làm target chạy nhiều lần: strong guarantee cho mỗi attempt **không tự đủ** nếu retry lặp external side effects.

Một invariant cơ bản cho advice chỉ quan sát là: **caller result/exception và business execution count vẫn theo hợp đồng**, trừ những side effects quan sát có chủ đích.

## 6. Nhìn theo vị trí phát sinh lỗi

| Vị trí lỗi | Target có thể đã chạy? | Side effects có thể tồn tại? |
| --- | --- | --- |
| Trước `proceed` | Chưa, nếu around chưa chuyển tiếp | Có thể từ before advice |
| Trong target trước mutation | Có | Có thể từ outer advice |
| Trong target sau mutation | Có | Rất có thể cả business mutation |
| Inner around ném sau target return | Đã có thể chạy thành công | Có thể target đã commit |
| Outer audit sink ném sau commit | Có thể đã commit | Business effect và audit intent có thể tồn tại |
| `finally` close ném | Tùy đường trước đó | Cleanup exception có thể che outcome |
| Network timeout khi commit | Không chắc caller biết | Có thể đã commit hoặc chưa |
| Future completes exceptionally sau method return | Method đã return | Async side effects có thể đã xảy ra một phần |

Không thể viết logic `catch (Exception e) { retry(); }` an toàn nếu không phân loại **lỗi và thời điểm commit**.

## 7. Around advice và `proceed()` thay đổi số lần tác dụng phụ

Một around advice có thể:

- Gọi `proceed` **một lần**: thông thường target chạy một lần nếu không có wrapper bên trong điều khiển thêm.
- **Không gọi**: cache hit, authorization deny hoặc circuit breaker short-circuit, target có thể không chạy.
- Gọi **nhiều lần**: retry/fan-out hoặc bug có thể làm side effects lặp.
- Catch rồi return fallback: caller có vẻ thành công dù target trước đó có thể đã sửa state rồi throw.
- Transform exception/result: thay hợp đồng mà caller quan sát.

Ví dụ:

~~~text
RETRY around
  attempt #1 -> TARGET commits -> response timeout
  attempt #2 -> TARGET commits again?
~~~

Nếu operation không idempotent, số lần gọi `proceed` có thể gây trùng thanh toán hoặc trùng bản ghi. Retry không phải một thao tác “chỉ thêm log”; nó thay luồng chạy và cần kiểm tra idempotency.

### 7.1. Transaction wrapper giả định

~~~text
beginTransaction
try:
    result = proceed
    commitTransaction
    return result
catch originalError:
    rollbackTransactionIfPossible
    rethrow originalError
finally:
    releaseResources
~~~

Đây là **pseudocode**, không phải API hoặc recipe đúng cho mọi bộ máy giao dịch. Cần xét những trường hợp:

- Target có dùng **cùng connection/transaction** hay không.
- Commit thất bại làm state unknown.
- Rollback cũng có thể thất bại.
- Nested transactions hoặc savepoints có semantic nào.
- Side effects ngoài transaction đã xảy ra chưa.
- Advice order khiến audit trong hay ngoài transaction.

Chỉ vì mã trông như `begin/proceed/commit` không có nghĩa atomicity đã tồn tại.

## 8. Ví dụ Java 17: khẳng định lỗi không tự rollback

Ví dụ bên dưới là chương trình **chạy độc lập bằng Java 17** không cần thư viện ngoài và **không mô phỏng rằng nó đã weave bytecode**. Nó kiểm thử ba cấp độ khác nhau:

1. **Unsafe mutation:** state thay đổi rồi target ném exception; caller thấy lỗi nhưng tác dụng phụ còn.
2. **In-memory snapshot publication:** chuẩn bị state mới rồi công bố cùng lúc `balance`, `idempotency keys` và `audit events` của một object dưới lock; lỗi trước publication không thay state.
3. **External side effect ngoài commit:** external audit sink lỗi sau khi Ledger đã cập nhật nội bộ; caller nhận lỗi nhưng dữ liệu nghiệp vụ vẫn ở trạng thái đã commit **trong RAM**.

**Ranh giới chính xác:** snapshot trên heap và `synchronized` minh họa atomic publication với những người quan sát qua object Ledger; không bảo đảm **durability sau khi process crash**, không bảo đảm **distributed atomicity** với remote sink và không phải database transaction.

Tạo file `AdviceAtomicityDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AdviceAtomicityDemo {
    static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("expected=" + expected + ", actual=" + actual);
        }
    }

    static final class UnsafeState {
        int balance;
        final List<String> audit = new ArrayList<>();

        void runThenFail() {
            audit.add("AUDIT begin U-1");
            balance += 10;
            throw new IllegalStateException("target failed");
        }
    }

    record Snapshot(int balance, Map<String, Integer> applied,
                    List<String> events) {}

    static final class Ledger {
        private Snapshot state = new Snapshot(0, Map.of(), List.of());

        synchronized String submit(String id, int amount, boolean failBeforeCommit) {
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
            Integer previous = state.applied().get(id);
            if (previous != null) {
                if (previous != amount) {
                    throw new IllegalArgumentException("idempotency key conflict");
                }
                return "duplicate:" + id;
            }
            if (failBeforeCommit) {
                throw new IllegalStateException("planned failure");
            }
            Map<String, Integer> applied = new HashMap<>(state.applied());
            applied.put(id, amount);
            List<String> events = new ArrayList<>(state.events());
            events.add("AUDIT committed " + id);
            state = new Snapshot(state.balance() + amount,
                    Map.copyOf(applied), List.copyOf(events));
            return "accepted:" + id;
        }

        synchronized Snapshot view() {
            return state;
        }
    }

    static void sendToExternalAudit(String id) {
        throw new IllegalStateException("external sink unavailable for " + id);
    }

    public static void main(String[] args) {
        UnsafeState unsafe = new UnsafeState();
        try {
            unsafe.runThenFail();
            throw new AssertionError("expected failure");
        } catch (IllegalStateException error) {
            equal("target failed", error.getMessage());
        }
        equal(10, unsafe.balance);
        equal(List.of("AUDIT begin U-1"), unsafe.audit);
        System.out.println("UNSAFE balance=10 audit=1 after exception");

        Ledger ledger = new Ledger();
        try {
            ledger.submit("A-1", 10, true);
            throw new AssertionError("expected failure");
        } catch (IllegalStateException error) {
            equal("planned failure", error.getMessage());
        }
        equal(0, ledger.view().balance());
        equal(0, ledger.view().events().size());
        System.out.println("PRECOMMIT failure balance=0 audit=0");

        equal("accepted:A-1", ledger.submit("A-1", 10, false));
        equal(10, ledger.view().balance());
        equal(List.of("AUDIT committed A-1"), ledger.view().events());
        System.out.println("COMMIT accepted:A-1 balance=10 audit=1");

        equal("duplicate:A-1", ledger.submit("A-1", 10, false));
        equal(10, ledger.view().balance());
        equal(1, ledger.view().events().size());
        System.out.println("RETRY duplicate:A-1 balance=10 audit=1");

        try {
            ledger.submit("A-1", 11, false);
            throw new AssertionError("expected conflict");
        } catch (IllegalArgumentException error) {
            equal("idempotency key conflict", error.getMessage());
        }
        System.out.println("CONFLICT rejected balance=10 audit=1");

        try {
            equal("accepted:B-2", ledger.submit("B-2", 5, false));
            sendToExternalAudit("B-2");
        } catch (IllegalStateException error) {
            equal("external sink unavailable for B-2", error.getMessage());
            System.out.println("EXTERNAL ERROR " + error.getMessage());
        }
        equal(15, ledger.view().balance());
        equal(2, ledger.view().events().size());
        System.out.println("AFTER EXTERNAL failure balance=15 internalAudit=2");
        equal("duplicate:B-2", ledger.submit("B-2", 5, false));
        equal(15, ledger.view().balance());
        System.out.println("RETRY duplicate:B-2 balance=15 internalAudit=2");
        System.out.println("ALL ASSERTIONS PASSED");
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 AdviceAtomicityDemo.java
java AdviceAtomicityDemo
~~~

Đầu ra **đã kiểm chứng bằng biên dịch và chạy thực**:

~~~text
UNSAFE balance=10 audit=1 after exception
PRECOMMIT failure balance=0 audit=0
COMMIT accepted:A-1 balance=10 audit=1
RETRY duplicate:A-1 balance=10 audit=1
CONFLICT rejected balance=10 audit=1
EXTERNAL ERROR external sink unavailable for B-2
AFTER EXTERNAL failure balance=15 internalAudit=2
RETRY duplicate:B-2 balance=15 internalAudit=2
ALL ASSERTIONS PASSED
~~~

### 8.1. Nhánh unsafe: exception nhưng state đã đổi

`UnsafeState.runThenFail()` ghi audit và tăng balance trước khi ném `IllegalStateException`. Caller bắt đúng lỗi nhưng balance vẫn là `10`. Đây là bằng chứng thực tế rằng **throw không tự rollback**.

### 8.2. Lỗi trước commit giả lập

`Ledger.submit("A-1",10,true)` kiểm validation, sau đó ném `planned failure` **trước khi xây và công bố snapshot mới**. Vì thế `balance=0` và audit events trống.

Đây là một bảo đảm strong ở **phạm vi state của Ledger** cho kịch bản đã xét. Nếu code đã gọi network hoặc sửa một resource khác trước thời điểm đó, snapshot commit không đảo ngược chúng.

### 8.3. Công bố `balance` và audit trong một snapshot

`Ledger.submit` tạo hai copies `Map` và `List`, rồi đặt chúng cùng `balance` vào một `Snapshot`. Sự gán `state = new Snapshot(...)` xảy ra **một lần dưới synchronized monitor**; `view()` cũng dùng cùng monitor.

Bởi vậy caller đọc qua `Ledger.view()` sẽ thấy toàn bộ snapshot trước hoặc sau, chứ không thấy một balance mới kết hợp với danh sách audit cũ của cùng object. Điều này minh họa **atomic publication in-memory**, không phải disk durability hay DB ACID transaction.

### 8.4. Idempotency đơn giản

Khi `A-1` được gửi lần hai với amount bằng `10`, Ledger trả `duplicate:A-1`, không tăng balance và không thêm audit. Với cùng key `A-1` nhưng amount `11`, Ledger trả lỗi `idempotency key conflict`.

Trong hệ thống thật, key phải được gắn với **logical operation, tenant/actor scope, request fingerprint và vòng đời lưu key**. Một map in-memory của một process không đủ cho restart hay nhiều node.

### 8.5. External audit failure sau commit

`Ledger.submit("B-2",5,false)` đã cập nhật state nội bộ; tiếp đó `sendToExternalAudit` cố tình ném `IllegalStateException`. Caller thấy lỗi external sink **sau khi balance là 15**.

Retry `B-2` với cùng key không cộng balance nữa, nhưng external event vẫn chưa được gửi thành công. Muốn tin cậy phải có cơ chế xử lý delivery độc lập như **durable outbox + publisher + consumer idempotency**, không chỉ bảo vệ phần nghiệp vụ.

### 8.6. Giới hạn ví dụ cần nhớ

- Dữ liệu trong RAM **mất khi process chết**; `Snapshot` không thay thế database.
- `synchronized` chỉ bảo vệ các lời gọi qua cùng object và monitor; không bao external sink.
- `Map.copyOf`/`List.copyOf` tạo collection không cho sửa trực tiếp ở đây; kiểu `record` không bảo đảm mọi object graph luôn deep immutable.
- Số dư kiểu `int` chỉ phục vụ minh họa; production cần domain money type, kiểm overflow và định dạng chính xác.
- Test dùng một luồng, không kiểm throughput hay race đa node.
- Hàm remote sink trong demo cố tình luôn lỗi để cho thấy post-commit failure; không phải client thật.

## 9. Tại sao bố trí advice liên quan transaction lại khó?

So hai thứ tự:

~~~text
A: TRANSACTION( AUDIT( TARGET ) )
B: AUDIT( TRANSACTION( TARGET ) )
~~~

Trong A, audit nằm **bên trong wrapper transaction về mặt call stack**, nhưng chỉ thực sự tham gia transaction nếu nó dùng cùng resource/session/connection hoặc cơ chế enlistment được hỗ trợ. Trong B, audit bao ngoài transaction; code after của audit có thể chạy sau khi inner transaction đã commit.

Do đó **nested precedence** rất quan trọng nhưng chưa đủ. Cần trả lời:

- Transaction thật được mở ở đâu?
- Commit thực xảy ra ở đâu?
- Audit ghi vào cùng cơ sở dữ liệu hay gửi HTTP khác?
- Nếu audit ở trong transaction wrapper, có dùng đúng transaction context không?
- Nếu audit gửi message trước commit, consumer có nhận một sự kiện chưa commit?
- Nếu audit chạy sau commit và lỗi, caller nhận outcome gì?
- Nếu retry bao ngoài transaction, có tạo mỗi attempt một transaction riêng không?
- Nếu transaction bao ngoài retry, các attempts có share một transaction bị đánh dấu rollback-only không?

Đừng áp một sơ đồ duy nhất cho mọi backend. Engine AOP chỉ quyết định đường can thiệp; semantics transaction nằm ở tài nguyên thực.

### 9.1. Một vòng lifecycle ý niệm

~~~text
begin unit of work
  validate
  mutate business rows
  insert audit/outbox row in same transaction
commit
  |
  +-> publisher gửi event sau commit
~~~

Đây là **mẫu kiến trúc**, không phải câu lệnh của một công nghệ cụ thể. Nếu business rows và outbox row cùng một transaction thực, chúng có thể commit/rollback cùng nhau theo bảo đảm của DB. Nhưng delivery tới broker/email **không nằm trong transaction DB** nếu không có cơ chế phối hợp riêng.

## 10. Transactional outbox giải quyết điều gì?

**Dual-write problem** xảy ra khi một operation phải vừa cập nhật database vừa phát event sang một hệ thống khác:

~~~text
write DB
publish message
~~~

Nếu DB commit rồi publish lỗi, DB đã đổi nhưng message thiếu. Nếu publish trước commit, consumer có thể thấy event của dữ liệu cuối cùng rollback. Chỉ dùng `try/catch` không loại bỏ được cả hai rủi ro.

Một phương án là:

~~~text
BEGIN DB TRANSACTION
   update business data
   insert outbox(eventId, payload, status)
COMMIT

INDEPENDENT PUBLISHER:
   read committed outbox
   publish event
   record/ack delivery progress
~~~

Điểm mạnh: **business data và ý định publish được commit cùng một DB transaction**, nếu thật sự cùng phạm vi. Nhưng:

- Publisher có thể crash sau khi gửi nhưng trước khi ghi ACK; sẽ **gửi lại**.
- Consumer cần xử lý idempotent theo event ID hoặc business key thích hợp.
- Event đã lưu ở outbox chưa có nghĩa remote effect đã diễn ra.
- Cần theo dõi backlog, retry policy, dead letters, ordering và poison messages.
- Không tự tuyên bố exactly-once end-to-end chỉ vì có outbox.
- Giao dịch có thể có **unknown commit outcome**, cần kiểm chứng qua key/state trước khi retry.
- Một notification đã gửi khó “hoàn tác” chỉ bằng rollback DB.

Nếu một audit là điều kiện bắt buộc của commit, việc ghi **audit intent** cùng transaction là lựa chọn cần cân nhắc. Việc gửi external audit thực có thể là **eventual delivery**, với độ trễ và đường khôi phục được thiết kế.

## 11. Compensation khác rollback

Rollback thông thường đảo/hủy **các thay đổi chưa được commit trong cùng transaction hỗ trợ**. **Compensation** là một hành động nghiệp vụ mới nhằm bù hậu quả **đã xảy ra**.

Ví dụ:

~~~text
reserve inventory  -> thành công
charge account     -> thất bại
release inventory  -> compensation
~~~

Trong khoảng giữa hai việc, hàng hóa có thể đã được giữ chỗ. `release inventory` có thể lỗi, cần retry hoặc đối soát. Nó không phải “quay về quá khứ”: audit/log và tác động lên bên ngoài có thể đã xảy ra.

Một `after-throwing` advice có thể kích hoạt compensation, nhưng phải quản lý:

- Biết bước nào đã commit thành công.
- Idempotency của action và compensation.
- Số lần retry/bộ đếm/timeout.
- Trạng thái `PENDING_COMPENSATION` hoặc `MANUAL_REVIEW` khi bù thất bại.
- Giữ lại causal chain của exception gốc và lỗi bù.
- Không tuyên bố toàn workflow đã atomic chỉ vì gọi một hàm `undo`.

Đặc biệt với thanh toán, “refund” có thể là giao dịch mới với thời gian xử lý và phí, không tương đương giao dịch chưa từng xảy ra.

## 12. Retry và lỗi “không biết đã commit hay chưa”

Một request remote có thể commit nhưng client nhận timeout **trước khi ACK về**:

~~~text
Client          Remote service
   |  request       |
   |--------------->|
   |             COMMIT
   |                 |
   |      response lost
   |<--- timeout -----|
~~~

Nếu around advice retry mù, remote service có thể thực hiện **hai lần** cùng một logical operation.

Các chiến lược:

1. Tạo **idempotency key** nhất quán trước mọi attempt của cùng logical operation.
2. Tại nơi commit, lưu key và outcome cùng transaction đủ mạnh, dùng uniqueness/concurrency control.
3. Nếu ACK mất, truy vấn trạng thái/outcome theo key hoặc retry idempotently.
4. Phân biệt cùng key **cùng payload** với cùng key **khác payload**.
5. Không retry validation/authorization failure như transient server error.
6. Giới hạn attempts, backoff và deadline.
7. Đối với external audit delivery, retry delivery **độc lập với business operation** khi phù hợp.

### 12.1. Cần xác định key scope

Ví dụ một key đơn giản `request-123` có thể va chạm giữa hai tenants nếu không namespace phù hợp. Trong production, nên xét `tenant`, `actor/authority`, `operationType`, fingerprint/payload hash, trạng thái pending/committed và thời hạn giữ key.

Với hai requests đồng thời mang cùng key, chỉ kiểm key trong một `HashMap` trên từng JVM là chưa đủ nếu ứng dụng chạy nhiều nodes. Có thể cần database unique constraint, compare-and-set hoặc cơ chế phối hợp có hợp đồng cụ thể.

### 12.2. Không đánh đồng retry với double weaving

Một lời around advice có thể được vào một lần nhưng gọi `proceed` nhiều lần; target chạy nhiều attempts. Đây không nhất thiết là double instrumentation. Dùng các counters độc lập: `logicalRequests`, `attempts`, `businessExecutions`, `businessCommits`, `adviceEntries`.

## 13. Short-circuit, cache và side effects

Một cache advice có thể return mà **không gọi `proceed`**. Do đó target không chạy trên đường ấy, nhưng advice ngoài cache vẫn có thể ghi audit/metrics.

Cần định nghĩa event:

| Tên sự kiện | Ý nghĩa đúng |
| --- | --- |
| `REQUEST_ATTEMPTED` | Caller thử thực hiện thao tác |
| `REQUEST_DENIED` | Policy từ chối |
| `CACHE_HIT` | Phục vụ dữ liệu từ cache, không nhất thiết chạy target |
| `TARGET_RETURNED` | Method target hoàn tất bình thường |
| `BUSINESS_COMMITTED` | Hiệu quả nghiệp vụ đã commit ở ranh giới xác định |
| `DELIVERY_ENQUEUED` | Ý định gửi đã lưu, chưa hứa remote nhận |
| `DELIVERY_CONFIRMED` | Đã có ACK theo hợp đồng bên nhận, chưa tự đủ để chứng minh exactly-once side effects |

Nếu audit cần ghi **mỗi API request**, nên đặt sao cho cache hit vẫn được quan sát. Nếu audit cần ghi **mỗi business commit**, đừng ghi lại commit event chỉ vì cache trả kết quả cũ.

Tương tự, authorization cần bao mọi đường truy cập dữ liệu quan trọng: cache hit không được bypass quyền chỉ vì cache nằm ngoài lớp kiểm soát.

## 14. Async: method return không bằng task completion

Một method Java có thể trả một `CompletableFuture` chưa hoàn tất. Advice `after-returning` ở ranh giới method có thể chạy **trước** khi task commit hoặc throw.

~~~java
CompletableFuture<String> future = startAsync();
return future;
~~~

Đoạn này là ví dụ không độc lập vì `startAsync()` chưa được định nghĩa.

Nếu audit cần biết **completion của tác vụ**, phải gắn vào lifecycle của Future/task:

~~~java
future.whenComplete((value, error) -> {
    if (error == null) recordCompletion(value);
    else recordFailure(error);
});
~~~

Hai hàm `recordCompletion` và `recordFailure` là pseudocode chưa được định nghĩa. Cần xem semantics callback, cancellation, exceptional completion, thread switching và lỗi của chính callback.

**Đặc biệt:** task có thể đã phát external side effect rồi caller cancel Future, vì cancellation không phải phép undo mọi network operation. Một advice dùng `finally` khi method return cũng **không** kéo dài transaction tự động đến khi task thật hoàn tất.

## 15. Concurrency và phạm vi isolation

Một `synchronized` quanh thao tác đọc/cập nhật trong **một object** có thể giúp đồng bộ đúng giữa các thread gọi cùng object. Nhưng:

- Nó không đồng bộ các JVM/process khác.
- Nó không tạo tính bền vững qua crash.
- Nó không bảo vệ network calls hoặc database transaction ở tài nguyên khác.
- Một ThreadLocal transaction context **không tự lan truyền qua mọi executor/async callback**.
- Nếu target dùng connection khác không enlist, outer transaction có thể không bao được ghi của target.

Các lỗi concurrency cần test:

- **Lost update** khi read-modify-write trên shared state.
- **Duplicate key race** khi hai request cùng idempotency key tới đồng thời.
- **Cache stampede** khi nhiều threads cùng miss một key và gọi target nhiều lần.
- **Write skew** nếu ràng buộc dữ liệu trải nhiều rows dưới isolation không phù hợp.
- **Deadlock** nếu hai advices khóa resources theo thứ tự ngược.
- **Duplicate event delivery** nếu publisher retry sau khi ACK thất lạc.
- **Context leak** khi ThreadLocal không được restore/remove trong finally.

Không dùng test chỉ với một thread để kết luận hệ thống chịu tải đồng thời an toàn.

## 16. Exception propagation và lỗi advice có thể che lỗi gốc

Nếu target ném `E1`, inner advice catch rồi ném `E2`, outer advice/caller có thể nhìn thấy `E2`. Nếu `E2` là lỗi sink metrics không liên quan, việc thay thế này có thể làm mất tín hiệu rollback/retry quan trọng.

Các nguyên tắc:

1. Advice chỉ quan sát nên ưu tiên **không làm đổi lỗi/result nghiệp vụ** trừ khi policy bắt buộc.
2. Nếu chuyển exception, giữ causal chain khi phù hợp.
3. Nếu cleanup cũng lỗi, đảm bảo hiểu primary/suppressed exception.
4. Không `return` từ `finally` vì có thể che lỗi/result.
5. Không catch mọi `Throwable` rồi im lặng bỏ qua các lỗi nghiêm trọng.
6. Không ghi `BUSINESS_FAILED` chỉ vì metrics sink lỗi sau commit; phải phân biệt **target outcome** và **observation outcome**.
7. Nếu transaction commit lỗi với outcome chưa rõ, ghi trạng thái **unknown/pending reconciliation** thay vì khẳng định rollback.

### 16.1. Fail-open hay fail-closed?

| Concern | Phương án có thể phù hợp | Điều kiện |
| --- | --- | --- |
| Metrics/tracing best-effort | Lỗi sink không phá nghiệp vụ | Cần buffer/drop policy và giám sát |
| Authorization bắt buộc | Lỗi kiểm quyền có thể deny | Không để target chạy khi policy không xác minh được |
| Audit compliance bắt buộc | Ghi audit intent trong transaction hoặc chặn trước mutation | Định nghĩa đúng durability và trạng thái commit |
| Email notification | Queue để gửi sau commit | Chấp nhận eventual delivery và retry |
| Cache | Cache lỗi có thể bỏ cache rồi gọi target | Phải giữ auth/consistency |
| Retry | Chỉ retry những lỗi cho phép | Idempotency và giới hạn attempts |

Không có một lựa chọn chung cho mọi hệ thống. Nếu audit bắt buộc fail-closed mà code chỉ ném lỗi **sau khi target đã commit**, đó không phải “chặn toàn bộ giao dịch”.

## 17. Kiểm thử tích hợp các side effects và tính nguyên tử

Một unit test `AuditAdvice.afterThrowing()` có thể chứng minh hàm ghi đúng log khi được gọi. Nhưng nó chưa chứng minh **đường gọi thật**, thứ tự advice, transaction boundary hay việc caller nhìn thấy gì.

### 17.1. Ma trận tình huống

| Tình huống | Cần kiểm chứng |
| --- | --- |
| Validation error trước mutate | Không có business commit |
| Lỗi sau mutation nhưng trước commit | Rollback/strong guarantee theo contract |
| Success commit | Business state và audit/outbox intent nhất quán |
| Before advice throw | Target không chạy; effects của before được tính |
| After advice throw | Caller outcome và state commit có thể khác |
| Cleanup throw | Primary exception và suppressed errors đúng |
| Retry cùng key/payload | Không nhân đôi committed business effect |
| Retry cùng key nhưng payload khác | Reject conflict |
| Commit outcome unknown | Reconcile/idempotency, không blind retry |
| Cache hit | Target không chạy; audit đúng đơn vị |
| Short-circuit deny | Target không chạy và không có side effect trái quyền |
| Retry nhiều attempts | Counts của target, commit, audit được tách biệt |
| Concurrent same key | Chống duplicate commit theo contract |
| Publisher crash sau publish trước ACK | Consumer idempotent hoặc dedup |
| Async task fails sau method return | Không ghi committed khi chỉ mới trả Future |
| Multi-resource workflow | Compensation/recovery nếu resource không có chung transaction |
| Agent/proxy bypass | Xác minh advice chạy tại join point thực sự |
| Restart/redeploy | Kiểm tra durability và recovery bằng hạ tầng thích hợp |

### 17.2. Fault injection vào đúng thời điểm

Một test mạnh nên cố ý tạo lỗi tại:

~~~text
before any business effect
after first stage, before commit
during commit
after commit, before response ACK
before external publish
after publish, before delivery ACK
during rollback
during compensation
during resource cleanup
~~~

Với mỗi điểm, phải nêu expected **caller outcome, state lưu trữ, audit/outbox, side effects remote, khả năng retry**. Chỉ assert exception type không đủ.

### 17.3. Đếm nhiều tầng

Phân biệt:

- `adviceEntries`: số lần một advice được vào.
- `businessExecutions`: số lần target chạy.
- `businessCommits`: số lần hiệu quả nghiệp vụ được commit.
- `outboxInsertions`: số event intents được lưu.
- `deliveryAttempts`: số lần gửi event.
- `consumerEffects`: số side effects tại consumer.

Một request có thể có hai delivery attempts nhưng một business commit; hoặc hai advice entries nhưng target chỉ thực thi một lần. Không dùng chung một metric `count` không rõ nghĩa.

## 18. Kiểm thử thật qua proxy/weaving

Trong AOP, còn một bước khác trước khi xem transaction correctness: **advice có thật sự được gắn vào đúng join point không?**

- Với **interface proxy**, caller phải giữ proxy. Gọi thẳng target có thể bỏ qua advice.
- Với **subclass proxy**, method `final`/`private` không thể override như method bình thường.
- Với **bytecode weaving**, phải kiểm tra class/pointcut đã được dệt vào artifact thực.
- Với **load-time weaving**, agent/transformer phải chạy đúng lúc, classloader scope phải đúng; không mặc định mọi class đã load đều được instrument.
- Với **nhiều aspect**, precedence quyết định transaction nằm ngoài hay trong audit/retry.

Một test gọi trực tiếp `advice.handle()` rồi thấy PASS **không phải integration test của AOP**. Cần test đường caller thật, cả negative coverage, return/exception và side effect counts.

**Kết nối với ví dụ Java ở mục 8:** chương trình đó minh họa nguyên lý state/commit và retry, **không thực hiện bytecode weaving hoặc dynamic proxy**, nên không thể dùng riêng nó để chứng minh một pointcut hay transaction aspect thực tế đã được áp dụng.

## 19. Quy trình thiết kế thực tế

1. **Liệt kê side effects** của tất cả target và advice: heap, DB, network, audit, cache, message.
2. **Định nghĩa ranh giới atomicity**: những effects nào phải cùng commit/rollback?
3. **Vẽ chain outer→inner**: auth, retry, transaction, cache, audit và business nằm ở đâu?
4. **Xác định commit point thực**: before/after returning xảy ra trước hay sau commit?
5. **Chọn failure policy**: metrics fail-open, auth fail-closed, audit durable hay eventual?
6. **Chọn cơ chế resource**: transaction DB, staged state, outbox, compensation.
7. **Đặt idempotency key** theo logical request; kiểm same key/different payload và concurrency.
8. **Phân biệt attempts và commits**; không tính success chỉ theo normal method return.
9. **Dự trù unknown outcome** khi remote timeout và response lost.
10. **Thiết kế retry riêng cho event delivery** nếu business đã commit.
11. **Xử lý cleanup/finally** mà không che lỗi gốc.
12. **Đối với async**, quản lý completion và context propagation, không chỉ method return.
13. **Viết fault-injection tests** trước/sau commit, publish/ACK, rollback và compensation.
14. **Integration test trên artifact thật**, đúng proxy/weaver, pointcut scope và precedence.
15. **Giám sát vận hành**: outbox backlog, duplicate keys, failed compensation, unknown commits và event delivery lag.

## 20. Những sai lầm phổ biến

1. **“Target throw là tất cả side effects đã được rollback.”** Sai nếu không có mechanism thực.
2. **“Một around advice tự làm mọi thao tác atomic.”** Sai: cần transaction/resource boundary.
3. **“`synchronized` = ACID database transaction.”** Không; lock chỉ có phạm vi JVM/object.
4. **“Audit success trong before advice là đủ.”** Sai nếu target có thể thất bại.
5. **“After-returning là chắc chắn sau commit.”** Không nếu commit ở outer layer.
6. **“After-throwing tự bù được mọi thay đổi.”** Không đối với external side effects đã xảy ra.
7. **“Finally rollback được email đã gửi.”** Không.
8. **“Publish event trong DB transaction sẽ rollback theo DB.”** Không nếu broker không cùng transaction.
9. **“Commit timeout = chắc chắn chưa commit.”** Không; có thể lost ACK.
10. **“Retry mọi exception là an toàn.”** Không: business side effects có thể lặp.
11. **“Dùng cùng key khác payload thì cứ coi duplicate.”** Phải phát hiện conflict.
12. **“Map idempotency trong RAM đủ cho nhiều nodes.”** Không.
13. **“Outbox tự bảo đảm consumer chạy exactly-once.”** Không; cần dedup/idempotency downstream.
14. **“Compensation tương đương undo nguyên tử.”** Không; đó là business effect mới.
15. **“Chỉ test return value là đủ.”** Không; phải test committed state và side effects.
16. **“Advice chạy cùng call stack thì các resource cùng transaction.”** Không.
17. **“Cùng exception đồng nghĩa state chưa đổi.”** Không.
18. **“Cache hit là một lần target commit.”** Không nhất thiết target có chạy.
19. **“Future return bình thường chứng minh task thành công.”** Không.
20. **“Catch mọi Throwable giúp tăng ổn định.”** Có thể che lỗi nghiêm trọng.
21. **“Logging/metrics là hoàn toàn side-effect-free.”** Chúng có I/O, có thể block, throw hoặc lộ dữ liệu.
22. **“Đổi order các aspects chỉ đổi thứ tự log.”** Có thể đổi transaction scope và bảo mật.
23. **“ThreadLocal transaction tự theo mọi async thread.”** Không.
24. **“Một Java lock bảo vệ hệ thống phân tán.”** Không.
25. **“Lỗi audit sau commit nghĩa là business thất bại.”** Không nhất thiết; cần phân biệt outcome.
26. **“Rollback hoặc cleanup không bao giờ throw.”** Thực tế phải test cả lỗi thứ cấp.
27. **“Điểm `after-returning` là điểm ghi BUSINESS_COMMITTED mặc định.”** Chỉ đúng nếu contract thực sự đặt sau commit.

## 21. Câu hỏi tự kiểm tra

**Câu 1.** Target thay đổi `balance` rồi throw. Caller catch exception. Balance có tự quay về không?  
**Đáp án:** Không. Phải có rollback/undo/transaction thực sự bao thay đổi ấy.

**Câu 2.** `synchronized` quanh Ledger bảo đảm gì và không bảo đảm gì?  
**Đáp án:** Bảo vệ các truy cập đi qua cùng monitor trong process; không đảm bảo durability, database atomicity hoặc multi-node consistency.

**Câu 3.** Tại sao một external audit failure sau commit nguy hiểm?  
**Đáp án:** Caller có thể nhận exception dù business đã commit rồi retry gây side effects trùng nếu không idempotent.

**Câu 4.** Có thể lưu business row cùng outbox record trong một DB transaction để làm gì?  
**Đáp án:** Để commit cả state nghiệp vụ và ý định publish cùng một ranh giới, không tự bảo đảm delivery downstream exactly-once.

**Câu 5.** Khi publisher đã gửi event nhưng ACK thất lạc, có thể xảy ra gì?  
**Đáp án:** Publisher có thể gửi lại; consumer cần dedup/idempotency phù hợp.

**Câu 6.** Compensation khác rollback ở chỗ nào?  
**Đáp án:** Compensation là nghiệp vụ mới bù hậu quả đã xảy ra, có thể thất bại và cần retry; rollback hủy changes trong phạm vi transaction hỗ trợ.

**Câu 7.** Tại sao `after-returning` chưa chắc là sau business commit?  
**Đáp án:** Vì transaction có thể do wrapper ngoài kiểm soát hoặc công việc async chưa xong.

**Câu 8.** Retry cùng idempotency key nhưng amount khác nên làm gì?  
**Đáp án:** Từ chối conflict theo hợp đồng, không âm thầm coi là cùng request.

**Câu 9.** `finally` tự throw có thể ảnh hưởng caller thế nào?  
**Đáp án:** Có thể che exception/result trước đó, cần chính sách bảo toàn lỗi chính.

**Câu 10.** Trong demo, vì sao `PRECOMMIT failure` không đổi balance nhưng `UNSAFE` lại đổi?  
**Đáp án:** Demo safe chuẩn bị rồi chỉ công bố snapshot sau các điều kiện; unsafe ghi state trước khi throw.

**Câu 11.** Một task trả `CompletableFuture` pending, có nên ghi `BUSINESS_COMMITTED` ngay khi method return không?  
**Đáp án:** Không, cần chứng cứ commit/completion theo contract.

**Câu 12.** Test advice method riêng có chứng minh transaction pointcut thật đã hoạt động không?  
**Đáp án:** Không; cần integration test qua đúng proxy/weaver và resource transaction thật.

## 22. Tổng kết

**Tác dụng phụ và tính nguyên tử là một phần của hợp đồng tổng thể khi AOP can thiệp vào ứng dụng.** Advice có thể ghi audit trước target, retry làm target chạy lại, catch rồi fallback, hoặc ném lỗi sau khi nghiệp vụ đã commit. **Exception không tự xóa tác dụng phụ** và một `around` advice không tự tạo giao dịch nguyên tử.

Thiết kế đáng tin cần xác định **phạm vi atomicity** cụ thể, đặt transaction và advice theo thứ tự phù hợp, sử dụng idempotency cho retry, dùng outbox hoặc cơ chế phối hợp khi gửi event ra ngoài, và compensation/reconciliation khi side effects đã xảy ra ở nhiều tài nguyên độc lập. Cần phân biệt **attempt, method return, business commit và event delivery**, rồi kiểm thử bằng cách cố ý gây lỗi trước/sau commit và kiểm cả caller outcome lẫn state thực tế. Chỉ khi đó các aspect mới bổ sung hành vi cắt ngang mà không làm lệch kết quả và bảo đảm nghiệp vụ ngoài ý định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-exception-contracts-007">Khả năng thay thế của đối tượng sau khi áp dụng aspect</a>

<details>
<summary>Xem chi tiết</summary>

# Khả năng thay thế của đối tượng sau khi áp dụng aspect

## 1. Vấn đề cốt lõi: đối tượng “đã áp dụng aspect” còn dùng thay đối tượng gốc được không?

Trong **Aspect-Oriented Programming (AOP)**, một aspect có thể được kết hợp với mã bằng **weaving** hoặc được áp dụng thông qua **proxy/decorator/interceptor**. Mục tiêu phổ biến là thêm logging, tracing, metrics, quản lý tài nguyên, kiểm soát quyền, retry hoặc caching mà không làm thay đổi logic chính.

Tuy nhiên, áp dụng aspect có thể tác động đến **kết quả trả về**, **ngoại lệ**, **thời điểm thực thi**, **số lần thực hiện side effect**, **identity của object**, **kiểu runtime**, **khả năng ép kiểu**, **đồng thời**, **thứ tự quan sát** và cả **lifecycle**. Vì vậy, không thể chỉ thấy “code vẫn compile” hoặc “proxy có cùng interface” rồi kết luận đối tượng sau khi áp dụng aspect **thay thế hoàn toàn** đối tượng ban đầu.

Câu hỏi trung tâm:

> **Với những client chỉ dựa vào hợp đồng đã công bố, việc thay một đối tượng gốc bằng đối tượng/cơ chế đã áp dụng aspect có còn bảo toàn các hành vi quan sát được mà client được quyền trông đợi không?**

Đây là vấn đề **substitutability** (khả năng thay thế), liên quan đến nguyên tắc **Liskov Substitution Principle — LSP**, thiết kế theo hợp đồng, tính trong suốt của advice và ranh giới abstraction. AOP có thể tăng chất lượng hệ thống, nhưng cũng có thể làm sai các giả định vốn hợp lệ trước khi áp dụng aspect.

### Bạn sẽ học được gì?

1. Phân biệt **khả năng gán theo kiểu** và **khả năng thay thế về hành vi**.
2. Phân tích **precondition**, **postcondition**, **invariant**, kết quả, ngoại lệ, side effect và điều kiện thời gian.
3. Hiểu khác biệt giữa **proxy**, **decorator** và **weaving trực tiếp** đối với class/type/identity.
4. Đánh giá `equals`, `hashCode`, `toString`, `getClass`, reflection, serialization và các kiểm tra kiểu cụ thể.
5. Hiểu khi nào advice quan sát có thể được thiết kế **transparent**, và khi nào retry/cache/fallback/authorization cố ý thay đổi hợp đồng.
6. Phân biệt **result đúng kiểu** với **result đúng ý nghĩa**, nhất là `null`, `Optional`, primitive, checked exceptions, future và stream.
7. Nhận diện giới hạn của **self-invocation** hoặc đường gọi bypass proxy đối với lời hứa “mọi invocation đều được can thiệp”.
8. Thiết kế integration tests dùng cùng bộ **contract tests** cho đối tượng gốc và đối tượng được bọc.
9. Chạy ví dụ **Java 17** chứng minh một proxy giữ được hành vi của interface nhưng **khác identity và class runtime**, cùng một proxy sai bảo toàn kiểu nhưng phá semantics.

**Phạm vi:** Bài nói về AOP cơ bản và nâng cao nói chung. Không giả định mọi engine AOP dùng một cơ chế proxy hoặc cùng một bộ quy tắc weaving.

## 2. Ba tầng “thay thế được” rất khác nhau

### 2.1. Tầng 1 — Gán được vào một biến/interface

Ví dụ:

~~~java
interface Store {
    String load(String key);
}
~~~

Nếu có object `wrapped` triển khai `Store`, caller có thể viết:

~~~java
Store store = wrapped;
~~~

Đây là **tương thích tại ranh giới kiểu**. Nó chỉ chứng minh object được gán hợp lệ vào kiểu `Store`, không chứng minh method `load` trả nội dung, exception và side effects theo hợp đồng.

Một proxy có thể implements cùng interface mà trả chuỗi rỗng cho mọi key, nuốt lỗi storage hoặc ghi đè dữ liệu. Code vẫn compile nhưng không đạt substitutability.

### 2.2. Tầng 2 — Giữ đúng hợp đồng hành vi của abstraction

Giả sử hợp đồng `Store.load("A")` hứa:
- Trả dữ liệu tương ứng nếu tồn tại.
- Ném `NotFound` khi key hợp lệ nhưng không có.
- Ném `Unavailable` khi storage lỗi, không biến thành `NotFound`.
- Không tự thay đổi dữ liệu.
- Không làm lộ key bí mật qua log.

Một advice logging **có thể** bảo toàn contract nếu nó chỉ quan sát đúng giới hạn, tiếp tục đúng một lần, trả nguyên result hoặc rethrow lỗi theo hợp đồng. Ngược lại, fallback “trả empty string cho mọi exception” đã phá phân biệt giữa *không tồn tại*, *storage không truy cập được* và *giá trị rỗng hợp lệ*.

### 2.3. Tầng 3 — Không phân biệt được qua mọi phép quan sát

Đây là mức mạnh hơn nhiều. Một interface proxy có thể hoạt động giống target ở các method của `Store`, nhưng:
- `wrapped == target` là `false`.
- `wrapped.getClass()` cho class proxy, không phải concrete target.
- Cast `(ConcreteStore) wrapped` có thể thất bại.
- `equals`, `hashCode` và `toString` có thể khác.
- Reflection/serialization annotations hoặc serialization format có thể khác.
- Stack traces và timing/logging có thể khác vì thêm interceptor.

Vì thế “thay thế được” phải được phát biểu **theo góc nhìn của client và những quan sát được hợp đồng cho phép**. Không hứa một proxy là bản sao không thể phân biệt với target dưới mọi phép quan sát Java.

## 3. LSP và design by contract khi có aspect

**Liskov Substitution Principle** được hiểu ở đây như yêu cầu: một implementation mới hoặc một đối tượng được bọc phải có thể được dùng tại chỗ abstraction hiện hành **mà không làm client tuân thủ hợp đồng trở nên sai**.

Các yếu tố thường cần kiểm:

| Phần của hợp đồng | Đối tượng gốc | Điều advice/proxy phải xem xét |
| --- | --- | --- |
| **Precondition** | Điều kiện caller phải thỏa trước invocation. | Có tự ý yêu cầu điều kiện mạnh hơn không? |
| **Postcondition** | Điều kiện được bảo đảm khi method hoàn tất thành công. | Return, state và side effects có còn đúng không? |
| **Invariant** | Tính chất phải giữ qua các thao tác hợp lệ. | Retry/cache/fallback có gây trạng thái không nhất quán không? |
| **Failure contract** | Ngoại lệ, mã lỗi, exceptional completion. | Có nuốt, thay, bọc hoặc phát sinh lỗi mới ngoài ý định không? |
| **Temporal contract** | Khi nào kết quả và side effects được xem là hoàn tất. | Có block/hứa success sớm/đổi async lifecycle không? |
| **Ownership** | Ai sở hữu resource hoặc result. | Advice có đóng sớm, giữ reference lâu hoặc trả shared resource sai không? |
| **Concurrency** | Điều kiện thread safety/linearizability/reentrancy. | State advice có data race, lock hoặc deadlock không? |
| **Identity/representation** | Caller được dựa vào interface hay concrete class? | Proxy có làm sai các giả định được công bố không? |

### 3.1. Không được tăng precondition ngoài ý định

Một method `read(key)` chấp nhận mọi key không-null. Nếu một aspect mới chỉ cho phép key bắt đầu `"admin/"`, ta đã **thu hẹp miền inputs hợp lệ**. Có thể đây là một security policy được chủ đích bổ sung, nhưng không còn là một replacement trong suốt đối với client đã được hứa đọc những key khác.

Nếu API đã công bố quyền đọc tùy authorization của caller, advice kiểm soát quyền có thể là cách thực thi chính xác contract hiện có. Tính thay thế phụ thuộc **hợp đồng được công bố**, không phụ thuộc ấn tượng “thêm security là tốt”.

### 3.2. Không được làm yếu postcondition

Nếu `deposit(5)` phải tăng balance đúng 5 và trả balance mới, một advice trả `999` mà không gọi target vi phạm postcondition dù return type vẫn là `int`.

Một advice cache trả balance cũ có thể vi phạm freshness/invariant; một advice retry làm deposit hai lần có thể vi phạm quy tắc “chỉ tăng đúng một lần”.

### 3.3. Giữ trạng thái quan sát được, không chỉ result

Một method `void publish(Event e)` không có return value nhưng có hợp đồng “đăng event đúng một lần khi thành công”. Một advice return bình thường mà skip `proceed()` không thể được coi là substitutable chỉ vì method trả `void`.

Tương tự, nếu method cập nhật DB rồi trả `true`, caller có thể quan sát DB bên ngoài; result đúng nhưng side effect sai vẫn là vi phạm.

### 3.4. Hợp đồng có thể cho phép nhiều implementation khác nhau

LSP không yêu cầu mọi implementation có cùng thuật toán, thời gian CPU hoặc cách logging. Nếu một interface chỉ hứa `get(id)` trả dữ liệu đúng và không công bố identity của object, một cache có thể thay đổi performance hoặc source của dữ liệu mà vẫn đáp ứng contract. Nếu interface hứa “đọc trực tiếp nguồn authoritative, luôn mới”, cache stale có thể không thay thế được.

## 4. Target, proxy và aspect: object nào thật sự được thay?

Các kỹ thuật AOP có **dấu vết runtime** rất khác nhau.

### 4.1. Delegating interface proxy

Một proxy implement interface và giữ một target:

~~~text
Caller -> InterfaceProxy -> InvocationHandler/advice -> Target
~~~

**Tính thay thế:** caller dựa vào interface có thể dùng proxy nếu operations, exceptions và state semantics được bảo toàn. Nhưng proxy thường **không phải instance** của concrete target class và không có cùng identity.

### 4.2. Decorator

Một decorator triển khai cùng abstraction rồi chuyển tiếp tới target:

~~~text
Caller -> ObservedStore implements Store -> RealStore
~~~

Mặc dù decorator được viết thủ công và có thể không cần runtime code generation, các vấn đề về identity, ownership, nested calls và result transformation vẫn tồn tại. Tên gọi “decorator” không tự bảo đảm substitutability.

### 4.3. Class-based proxy

Một số công cụ tạo subclass hoặc generated class. Proxy có thể assignable tới target base class, nhưng khả năng intercept còn bị ràng buộc bởi `final`, visibility, constructors, method dispatch và chi tiết engine. Subclass relationship không tự bảo đảm equals/hashCode, state initialization hoặc invariants của target.

**Không đưa ra quy tắc chung** như “class proxy luôn thay được concrete class” hoặc “self-invocation của mọi class proxy luôn bypass”. Những chi tiết này phải được xác minh trên implementation thật.

### 4.4. Source/bytecode weaving

Một engine có thể **dệt logic vào method execution/call site** thay vì tạo một wrapper object mới. Khi đó class identity của target có thể vẫn là chính class ấy, nhưng code/behavior đã thay đổi.

Đây không phải bảo đảm substitutability tuyệt đối: weaving có thể đổi exception, result, lock contention, timing, stack trace, thread context hoặc side effects. Dù object identity không đổi, **behavioral contract** vẫn có thể bị phá.

### 4.5. Runtime instrumentation/load-time weaving

Class có thể được biến đổi khi nạp hoặc trong quá trình runtime nếu công cụ cho phép. Thời điểm và phạm vi biến đổi ảnh hưởng classloader, module và những instances hiện có, nhưng vẫn phải kiểm tra các hợp đồng quan sát được. Không coi “biến đổi được bytecode” là bằng chứng giữ nguyên behavior.

## 5. Interface compatibility không đồng nghĩa concrete compatibility

Giả sử:

~~~java
interface CounterPort {
    int add(int step);
}

final class CounterImpl implements CounterPort {
    @Override
    public int add(int step) { /* implementation */ return 0; }
}
~~~

Nếu một JDK dynamic proxy implements `CounterPort`, caller chỉ biết `CounterPort` có thể gọi `add`. Nhưng:

~~~java
CounterPort wrapped = createProxySomehow(); // mã minh họa
CounterImpl concrete = (CounterImpl) wrapped; // runtime có thể lỗi cast
~~~

Đây là **đoạn mô tả**, chưa phải chương trình chạy đầy đủ. Phép cast không được bảo đảm vì một JDK proxy là object khác, không kế thừa `CounterImpl`.

### 5.1. “Programming to an interface” giúp đến đâu?

Nếu mọi client hợp lệ chỉ dùng `CounterPort`, không đòi concrete class, proxy có biên tương thích thuận lợi hơn. Nhưng trong dự án thực tế có thể có:
- Reflection đọc annotations trên implementation.
- Serialization cần concrete type.
- Framework hoặc native library cần exact class.
- Code dựa vào identity hoặc class name.
- Overload resolution hay API dùng concrete type.
- Method không nằm trong interface.
- Contract về finalizers/constructors/resource ownership.

Khi thay bằng interface proxy, phải **rà những yêu cầu ấy**, không chỉ sửa compiler errors.

### 5.2. Một interface có thể quá hẹp cho nhu cầu

Nếu client cần một method `close()` vốn chỉ có trên concrete target, một interface proxy không tự phơi bày method đó trừ khi interface/wrapper công bố. Khi design proxy boundary, cần bảo đảm interface chứa **đúng các operations có trong hợp đồng**, kể cả lifecycle operations.

### 5.3. Runtime type và generic type khác nhau

Một `List<Store>` có thể chứa target hoặc proxy cùng interface. Nhưng khi caller lấy ra và cast tới `ConcreteStore`, vẫn có thể lỗi. Generics chỉ đảm bảo những điều compiler có thể biết về kiểu công bố; không biến proxy thành subtype concrete tự động.

## 6. Identity, equals, hashCode, toString và Object methods

Đối với Java, nhiều client không chỉ gọi business methods. Chúng có thể:
- Dùng object làm key trong `HashMap`/`HashSet`.
- So sánh `==` hoặc `equals()`.
- Ghi log `toString()`.
- Lấy `System.identityHashCode()`.
- Gọi `getClass()` hoặc reflection để tra annotations.
- Khóa trên monitor của object.
- Dùng object identity như một token/correlation key.

Áp dụng proxy có thể thay những hành vi này.

### 6.1. `==` không thể được giả lập cho hai object riêng

Nếu `proxy` và `target` là hai object khác nhau thì:

~~~text
proxy == target  -> false
~~~

Một method `equals()` tự viết có thể quyết định hai object tương đương ở mức giá trị, nhưng **không thể làm toán tử `==` coi chúng là cùng reference**. Nếu contract của client phụ thuộc identity của target, một delegating proxy riêng biệt không đạt sự trong suốt tuyệt đối đối với phép quan sát ấy.

### 6.2. `equals()` phải giữ đối xứng, bắc cầu và nhất quán

Nếu proxy tự trả `true` khi so với target, nhưng `target.equals(proxy)` trả `false`, cặp đối tượng vi phạm **symmetry** của hợp đồng `Object.equals`. Điều này có thể gây lỗi trong collections và cache.

Đừng sửa đơn giản bằng “proxy.equals(target) luôn true”. Phải thiết kế equality strategy cho toàn bộ abstraction, cân nhắc symmetry/transitivity/consistency và việc code khác có thể vẫn dùng target gốc.

### 6.3. `hashCode()` phải phù hợp `equals()`

Nếu hai objects được coi là equal thì hash codes phải bằng nhau theo hợp đồng Java. Nếu proxy dùng một hash code mới trong khi `equals` cố liên kết nó với target, map/set behavior có thể bị lỗi.

Một chiến lược là **proxy identity semantics**: proxy chỉ equal chính nó, `hashCode` theo identity của proxy. Điều này có thể đúng về mặt Java nhưng **khác** cách client so sánh target theo giá trị hoặc identity gốc. Chọn đúng theo hợp đồng.

### 6.4. `toString()` và log cũng là quan sát được

Nếu caller kỳ vọng một chuỗi debug của target, trả chuỗi `"Proxy@..."` có thể làm thay đổi log hoặc snapshots. Nếu `toString` của target có side effect hoặc truy cập dữ liệu nhạy cảm, tự động delegate trong proxy cũng không hẳn an toàn.

### 6.5. JDK proxy xử lý Object methods như thế nào?

Một `InvocationHandler` của JDK dynamic proxy có thể nhận invocation của `equals`, `hashCode`, `toString`. Handler cần chọn chính sách rõ cho các method đó, không mặc định delegate mù quáng mọi `Method` đến target.

Riêng `getClass()` là method final của `Object` và phản ánh **runtime class thực của receiver**. Nó không biến một proxy thành target class. Một số Object methods khác có ý nghĩa monitor/lifecycle riêng không nên bị đánh đồng với business interception.

### 6.6. Equality của proxy và target: có thể không có đáp án thỏa mọi client

Nếu một nhóm clients yêu cầu proxy có identity riêng để là một component lifecycle mới, nhóm khác yêu cầu proxy và target equal, còn nhóm thứ ba dùng `==`, ta có những yêu cầu xung đột. Cần thiết kế **abstraction boundary** thay vì hứa “mọi phép so sánh đều tương đương”.

## 7. Runtime state, ownership và referential transparency

Một delegating proxy có thể giữ **strong reference** tới target. Nếu proxy sống suốt ứng dụng, target cũng có thể sống lâu hơn dự định. Đây là khác biệt lifecycle mà một client sử dụng resource có thể quan sát được.

### 7.1. Ai sở hữu target?

Câu hỏi cần làm rõ:
- Caller có được giữ và gọi target trực tiếp không?
- Proxy có phải owner duy nhất của target không?
- Khi proxy bị close, target có phải close theo không?
- Nếu một target được bọc trong nhiều proxy, proxy nào có quyền đóng resource?
- Nếu target là singleton shared, đóng một decorator có được phép đóng target không?

Không được chỉ vì wrapper triển khai `AutoCloseable` mà tự ý đóng một resource thuộc owner khác.

### 7.2. Aspect không nên giữ result/request vô hạn

Một shared aspect lưu `lastArguments`, `lastResult`, `lastException` hoặc `lastTarget` có thể giữ cả object graph lớn. Substitutability còn có khía cạnh hiệu năng/tài nguyên: target trước đây được giải phóng đúng scope nhưng sau khi gắn aspect thì memory retention tăng không kiểm soát.

Observer nên lưu dữ liệu tối thiểu, giới hạn cache cardinality, dọn `ThreadLocal` và unsubscribe listeners/callbacks khi scope kết thúc.

### 7.3. Một wrapper có thể làm thay đổi khả năng thu hồi classloader

Với hệ plugin, registry từ host giữ proxy/aspect metadata của module cũ có thể giữ classloader cũ reachable. Không giả định việc unload plugin tương đương mọi object đã được GC. Cần kiểm tra retained paths và lifecycle removal rõ ràng.

### 7.4. Referential transparency không phải yêu cầu của mọi method

Một pure function có thể có tính chất cùng input → cùng result và không side effect; một command thay trạng thái thì không. Không nên áp quy tắc pure-function lên mọi method để đánh giá substitutability.

Điều cần giữ là **những điều client có quyền dựa vào**: với method pure, việc thay result hay có side effect mới có thể phá contract; với command, việc làm đúng side effects là một phần của contract.

## 8. Bảo toàn return value và exception contract

### 8.1. Primitive và sentinel

Method trả `int` có thể dùng `0`, `-1` hoặc một khoảng giá trị đặc biệt theo hợp đồng. Một advice tự trả `0` khi lỗi để “đảm bảo có int” có thể biến failure thành success. Với JDK interface proxy, handler trả `null` cho primitive còn có thể gây lỗi unboxing.

### 8.2. `Optional.empty()` khác exception

`Optional.empty()` có thể nghĩa là **không tìm thấy**, trong khi exception nghĩa là **không thể tra cứu**. Advice fallback thay storage failure bằng empty có thể khiến caller thực hiện hành động sai.

### 8.3. `void` vẫn có postcondition

`void commit()` không trả result, nhưng phải bảo toàn trạng thái commit, số lần tác động và error handling. Một advice skip target rồi return bình thường có thể vi phạm contract nghiêm trọng.

### 8.4. Exception type, cause và timing

Nếu target ném `IllegalArgumentException` với một điều kiện input sai, proxy nên truyền lại đúng loại lỗi theo hợp đồng nếu chỉ quan sát. Nếu around advice wrap mọi lỗi trong `RuntimeException`, caller bắt riêng `IllegalArgumentException` có thể bị hỏng.

Trong JDK reflection, `Method.invoke` bọc exception ném từ target trong `InvocationTargetException`; handler quan sát thường cần lấy `getCause()` rồi ném lại. Với checked exceptions qua proxy, những loại không hợp lệ theo interface method signature có thể bị bọc trong `UndeclaredThrowableException`. Đừng dựa vào kiểu lỗi runtime “tình cờ” khi không có hợp đồng rõ.

### 8.5. Return identity và mutability

Một advice tạo bản sao của result có thể phá contract “trả cùng instance”; một advice trả cùng mutable instance cho nhiều caller có thể phá contract “defensive copy”. Kể cả `equals` trả true, identity/ownership có thể khác.

### 8.6. Generic erasure

Một interceptor trả `List<Integer>` cho API `List<String>` bằng raw cast có thể qua ranh giới `Object` nhưng gây `ClassCastException` khi caller đọc phần tử. Substitutability đòi giữ **kiểu dữ liệu thực bên trong**, không chỉ runtime container type.

### 8.7. Biến đổi result phải được công bố

Advice normalization, masking, cache hoặc fallback có thể đổi return hợp lệ nếu **đó là hợp đồng mới được công bố**. Khi ấy test dựa trên hợp đồng mới, không gọi advice “hoàn toàn trong suốt” nữa.

## 9. Java 17 runnable: tương thích interface khác với tương thích concrete class

Ví dụ sau dùng **JDK dynamic proxy** quanh `BalancePort`. Một proxy `audited` giữ nguyên hành vi business khi caller chỉ dùng interface: gọi target **một lần**, trả nguyên result và ném lại lỗi gốc. Handler định nghĩa `equals/hashCode/toString` theo **identity riêng của proxy**.

Sau đó một proxy `broken` cố ý trả `999` cho `deposit(5)` mà không gọi target. Result có đúng kiểu `int`, nhưng không thực hiện side effect ghi balance và sai postcondition.

Tạo file **`AopSubstitutabilityDemo.java`**:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Optional;

public class AopSubstitutabilityDemo {
    interface BalancePort {
        int deposit(int amount);
        int balance();
        Optional<String> owner();
        void reset();
    }

    static final class Ledger implements BalancePort {
        private int cents = 10;

        @Override
        public int deposit(int amount) {
            System.out.println("TARGET deposit " + amount);
            if (amount < 0) {
                throw new IllegalArgumentException("negative amount");
            }
            cents += amount;
            return cents;
        }

        @Override
        public int balance() {
            System.out.println("TARGET balance");
            return cents;
        }

        @Override
        public Optional<String> owner() {
            System.out.println("TARGET owner");
            return Optional.of("A");
        }

        @Override
        public void reset() {
            System.out.println("TARGET reset");
            cents = 0;
        }
    }

    static BalancePort audited(BalancePort target) {
        return (BalancePort) Proxy.newProxyInstance(
                BalancePort.class.getClassLoader(),
                new Class<?>[] { BalancePort.class },
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == args[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> "AuditedBalanceProxy";
                            default -> throw new UnsupportedOperationException(
                                    method.getName());
                        };
                    }

                    System.out.println("ADVICE before " + method.getName());
                    try {
                        Object result = method.invoke(target, args);
                        System.out.println("ADVICE return " + method.getName());
                        return result;
                    } catch (InvocationTargetException wrapped) {
                        Throwable cause = wrapped.getCause();
                        System.out.println("ADVICE throw " + method.getName()
                                + " " + cause.getClass().getSimpleName());
                        throw cause;
                    }
                }
        );
    }

    static BalancePort broken(BalancePort target) {
        return (BalancePort) Proxy.newProxyInstance(
                BalancePort.class.getClassLoader(),
                new Class<?>[] { BalancePort.class },
                (proxy, method, args) -> {
                    if ("deposit".equals(method.getName())) {
                        System.out.println("BROKEN skip deposit");
                        return 999;
                    }
                    return method.invoke(target, args);
                }
        );
    }

    static void runClient(BalancePort port) {
        System.out.println("CLIENT deposit=" + port.deposit(5));
        System.out.println("CLIENT balance=" + port.balance());
    }

    public static void main(String[] args) {
        Ledger target = new Ledger();
        BalancePort proxy = audited(target);

        System.out.println("TYPE interface=" + (proxy instanceof BalancePort));
        System.out.println("TYPE concrete=" + (proxy instanceof Ledger));
        System.out.println("IDENTITY same=" + (proxy == target));
        System.out.println("EQUALS reflexive=" + proxy.equals(proxy));
        System.out.println("EQUALS target=" + proxy.equals(target));
        System.out.println("STRING " + proxy);

        System.out.println("CLIENT owner=" + proxy.owner());
        runClient(proxy);

        try {
            proxy.deposit(-2);
        } catch (IllegalArgumentException error) {
            System.out.println("CLIENT rejected=" + error.getMessage());
        }

        proxy.reset();
        System.out.println("CLIENT resetState=" + proxy.balance());

        BalancePort invalid = broken(target);
        System.out.println("BROKEN returned=" + invalid.deposit(5));
        System.out.println("TARGET actualBalance=" + target.balance());

        try {
            Ledger concrete = (Ledger) proxy;
            System.out.println(concrete);
        } catch (ClassCastException error) {
            System.out.println("CAST to Ledger failed");
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac AopSubstitutabilityDemo.java
java AopSubstitutabilityDemo
~~~

**Stdout dự kiến, đúng theo thứ tự:**

~~~text
TYPE interface=true
TYPE concrete=false
IDENTITY same=false
EQUALS reflexive=true
EQUALS target=false
STRING AuditedBalanceProxy
ADVICE before owner
TARGET owner
ADVICE return owner
CLIENT owner=Optional[A]
ADVICE before deposit
TARGET deposit 5
ADVICE return deposit
CLIENT deposit=15
ADVICE before balance
TARGET balance
ADVICE return balance
CLIENT balance=15
ADVICE before deposit
TARGET deposit -2
ADVICE throw deposit IllegalArgumentException
CLIENT rejected=negative amount
ADVICE before reset
TARGET reset
ADVICE return reset
ADVICE before balance
TARGET balance
ADVICE return balance
CLIENT resetState=0
BROKEN skip deposit
BROKEN returned=999
TARGET balance
TARGET actualBalance=0
CAST to Ledger failed
~~~

### 9.1. “TYPE interface=true” nhưng “TYPE concrete=false”

`proxy` là một object JDK-generated proxy triển khai `BalancePort`, nên có thể truyền vào `runClient(BalancePort)`. Nó **không phải instance** của `Ledger`. Lời gọi `(Ledger) proxy` thất bại với `ClassCastException`.

Điều này minh họa hai phạm vi compatibility:
- **Client chỉ biết interface:** có thể dùng proxy khi behavior được giữ.
- **Client yêu cầu concrete class:** JDK interface proxy không thể tự là cùng subtype.

Một engine subclass proxy khác có khả năng assignability khác, nhưng vẫn phải test behavior chứ không chỉ cast.

### 9.2. Proxy khác identity của target

`proxy == target` là false. Handler cho `equals` trả `true` khi proxy so với chính nó và `false` khi so với target. `hashCode` theo identity của proxy; `toString` dùng một nhãn riêng.

Chiến lược này **có chủ đích** và hợp lý cho identity semantics của proxy, nhưng **không làm proxy hoàn toàn tương đương target** nếu client trước đó dựa vào target identity hoặc representation. Đó là lý do tài liệu của abstraction cần nói rõ caller được dựa vào điều gì.

### 9.3. Owner và kết quả primitive được giữ

`proxy.owner()` trả `Optional.of("A")` từ target. Handler không thay bằng `null` hay `Optional.empty()`. Với `deposit(5)`, target tăng balance từ 10 lên 15 và trả `int 15`, handler chuyển tiếp nguyên kết quả. Client nhận `15`, rồi `balance()` trả `15` — cả result và state postcondition đồng nhất.

Nếu chỉ assert kiểu `int` của kết quả deposit, ta có thể không phát hiện một proxy trả 999. Phải assert **state sau thao tác** nữa.

### 9.4. Lỗi negative amount được giữ nguyên

Target in `TARGET deposit -2` rồi ném `IllegalArgumentException("negative amount")`. `Method.invoke` bọc lỗi vào `InvocationTargetException`; handler lấy cause, ghi loại lỗi và ném lại. Caller bắt đúng loại đã công bố.

Handler không thay lỗi thành `0`, `false` hoặc generic error; advice quan sát giữ contract lỗi.

### 9.5. `void reset()` vẫn bảo toàn side effect

Method `reset()` không có result nhưng target thay balance về 0. Handler chạy trước/sau, caller gọi `balance()` thấy 0. Đây là ví dụ cho thấy **substitutability của command** không thể đánh giá chỉ bằng return type.

### 9.6. Proxy cố ý sai: đúng kiểu nhưng không đúng hợp đồng

`broken` gặp `deposit` thì in `BROKEN skip deposit` và trả `int 999` **mà không gọi target**. Cùng interface, cùng primitive type, không có exception, nhưng:
- Target balance không tăng 5.
- Result không phải balance mới.
- Client có thể tưởng mutation đã thành công.
- Postcondition và side effect contract đều sai.

Stdout chứng minh `BROKEN returned=999` nhưng `TARGET actualBalance=0`. Đây là một replacement **không hợp lệ về hành vi**.

### 9.7. Điều ví dụ không chứng minh

- Không phải một AOP engine source/bytecode weaving.
- Không có pointcut DSL hoặc matching annotations.
- Không chứng minh mọi class-based proxy có cast behavior giống JDK dynamic proxy.
- Không có concurrency; trường `cents` không được bảo vệ cho multi-thread access.
- Không xử lý checked exceptions ở interface.
- Không chứng minh tất cả Object methods/reflective/serialization behavior tương đương với target.
- `broken` chỉ dùng để dạy vi phạm semantic contract; không phải ví dụ thiết kế advice production.
- Không có transaction thực hoặc remote effects; kết quả in stdout là mô hình tuần tự có kiểm soát.

## 10. Tính thay thế khi advice có thể short-circuit

Một around advice có thể bỏ qua target:

~~~text
around load(key):
    if cache.hasValidEntry(key):
        return cache.value(key)
    return proceed()
~~~

Điều này **không tự sai**. Nếu interface contract chỉ yêu cầu trả giá trị hợp lệ, cache có thể đáp ứng. Nhưng cần kiểm tra các điều kiện:

- Có cho phép dữ liệu stale không?
- Có thay đổi theo quyền của user/tenant không?
- Có sự kiện phải xảy ra ở target mỗi lần không?
- Cache hit có bỏ qua authorization hoặc audit bắt buộc không?
- Value có thuộc về caller hay là shared mutable instance?
- Cache key có chứa đầy đủ loại đối số và principal context không?
- Khi target lỗi, có phân biệt exception với giá trị “not found” không?
- Nếu method trả stream/future/resource, caching **handle** có hợp lệ không?

### 10.1. Cache một future khác cache một value

Một `CompletionStage<T>` không chỉ là container giá trị. Nó biểu diễn một hoạt động có lifecycle: pending, success, exceptional completion, cancellation. Trả cùng future từ cache có thể cố tình chia sẻ một computation giữa callers, nhưng khác việc chạy target một lần mới cho mỗi request.

Nếu caller được hứa một fresh task/attempt, cache future có thể vi phạm contract dù generic type vẫn đúng.

### 10.2. Negative caching và failure semantics

Một method trả `Optional<Item>` có thể trả empty cho absent, ném lỗi khi DB unavailable. Nếu cache lưu empty khi DB lỗi, mọi caller sau có thể tin rằng item chắc chắn không tồn tại. Đó là thay đổi semantics cần policy riêng, không phải transparent optimization.

### 10.3. Cache ngoài security boundary

Nếu Cache nằm ngoài Authorization trong chuỗi advice, một cache hit có thể return mà không gọi authorization phía trong. Nếu policy bắt buộc kiểm quyền theo request, đây là vấn đề. Không dựa vào một thứ tự advice như biện pháp security duy nhất; cần trusted boundary mà mọi entry path phải đi qua.

## 11. Retry: cùng result cuối nhưng khác lịch sử thực thi

Một retry advice có thể biến chuỗi hai attempts `FAIL, OK` thành một logical success. Nếu method **đọc idempotent**, điều này có thể phù hợp contract. Nhưng nếu method **thay đổi trạng thái** hoặc phát external side effect, retry có thể thực hiện hai lần dù caller chỉ thấy một return.

Ví dụ một chuyển khoản đã commit nhưng response timeout. Advice tự retry có thể tạo duplicate payment nếu API không có idempotency key hoặc cơ chế kiểm tra outcome.

Vì vậy substitutability còn hỏi:
- Có được phép chạy target nhiều lần không?
- Attempt nào có side effects?
- Có timeout/deadline tổng và retry budget không?
- Exception nào được phép thử lại?
- Caller có thể phân biệt “đã commit nhưng response thất lạc” với “chưa commit” không?
- Có backoff hay circuit breaker làm thay đổi timing SLA?
- Lỗi cuối có còn type/cause theo contract?

### 11.1. Retry không phải “bảo toàn behavior” miễn phí

Một proxy retry đúng kiểu `Receipt` có thể trả một receipt hợp lệ nhưng đã gửi hàng hai lần. Nếu API hứa “chính xác một thao tác” thì failure. Nếu API công bố at-least-once behavior, client có trách nhiệm idempotency tương ứng. Đánh giá theo hợp đồng đã ký kết, không theo trực giác.

### 11.2. Nhiều aspect thay đổi đơn vị quan sát

Với `Metrics(Retry(Target))`, metric ngoài có thể đếm một logical call. Với `Retry(Metrics(Target))`, metric trong có thể đếm các attempts. Không nhất thiết có lỗi, nhưng đổi thứ tự thay dữ liệu thống kê mà operations consumers trông đợi.

## 12. Fallback: che lỗi có thể phá substitutability

Một advice:

~~~text
try:
    return proceed()
catch anyError:
    return defaultValue
~~~

rất dễ tạo **false success**. Với `int count()`, mặc định 0 có thể hợp kiểu nhưng “không có bản ghi” khác “không thể truy vấn”. Với `boolean authorized()`, mặc định false là một quyết định từ chối có thể phù hợp **fail-closed policy** nhưng không nhất thiết phù hợp contract “phân biệt lỗi service”.

Một fallback có thể được chấp nhận khi:
1. API công bố rõ trạng thái degrade/fallback.
2. Fallback chỉ áp cho những loại lỗi được phép.
3. Không làm sai invariant hoặc che outcome mơ hồ của side effect.
4. Có metric/alert về degraded mode theo policy.
5. Không trả result trái nullability, identity hoặc freshness promise.
6. Caller có cách hiểu success/failure hợp lệ với kiểu result.

Không tự bắt mọi `Throwable` để trả mặc định. `Throwable` còn gồm những `Error` mà việc nuốt bỏ có thể làm hệ thống ở trạng thái nguy hiểm.

## 13. Thay đổi điều kiện thời gian là thay đổi hợp đồng khi timing có ý nghĩa

Một advice tracing có thể thêm ít chi phí CPU; điều đó không nhất thiết vi phạm contract nếu API không công bố timing chặt. Nhưng:
- Synchronous API bị biến thành asynchronous API.
- Async API bị block cho tới completion.
- Một method cần phản hồi trước deadline bị advice I/O block.
- Một callback được chuyển sang thread khác ngoài thread-affinity promise.
- Một iterator lazy bị advice tiêu thụ eagerly.
- Một resource bị đóng trước khi caller có cơ hội dùng.

Những thay đổi này có thể phá contract dù **giá trị cuối cùng** giống nhau.

### 13.1. `CompletionStage`: method return khác task completion

Với `CompletionStage<Receipt> submitAsync(...)`, method return một handle trước khi task hoàn tất. Around advice chỉ đo/try-catch `proceed()` không tự quan sát exceptional completion xảy ra sau đó.

Một observer trong suốt có thể đăng ký completion callback để đo nhưng phải cân nhắc:
- Trả stage **gốc** hay dependent stage?
- Callback có ném lỗi vào dependent stage không?
- Cancellation propagation có thay đổi không?
- Caller có so identity hoặc dùng API riêng của original stage không?
- Context có bị giữ quá lâu qua completion callback không?

### 13.2. `whenComplete` cũng là phép biến đổi nếu thay handle

`stage.whenComplete(...)` tạo một dependent stage. Nếu callback ném lỗi, dependent stage có thể có outcome khác original. Một advice chỉ muốn log mà return dependent stage cần chứng minh semantics phù hợp, không chỉ rằng cả hai có kiểu `CompletionStage<T>`.

### 13.3. Không coi async success khi chỉ nhận được future

Method `submitAsync` trả handle thành công nhưng future fail sau đó. Nếu advice báo “transaction success” ngay khi nhận handle, nó đã thay ý nghĩa success signal theo contract. Muốn audit completion, cần hook đúng thời điểm hoàn thành thật.

## 14. Stream, iterator và resource trả về

Một `Stream<Row>`, `Iterator<T>`, cursor hoặc publisher có hành vi xảy ra **sau khi method return**.

Một logging advice gọi `stream.count()` để ghi số phần tử rồi trả stream gốc có thể đã tiêu thụ stream. Caller gặp lỗi khi dùng terminal operation tiếp theo. Một advice chuyển stream thành list có thể thay lazy/eager semantics, memory footprint, thứ tự, timing exception và resource cleanup.

### 14.1. Ownership quyết định close

Nếu method trả stream mà caller chịu trách nhiệm `close()`, một advice không nên tự close trong `finally` ngay sau `proceed()` return. Advice chỉ kết thúc invocation, không có nghĩa caller đã tiêu thụ result.

### 14.2. Publisher có protocol riêng

Với reactive/streaming publisher, hợp đồng có thể gồm:
- Khi nào subscribe thật sự kích hoạt work?
- Số lần subscribe hợp lệ.
- Backpressure/demand.
- Ordering và terminal events.
- Error/cancellation propagation.

Một proxy chỉ giữ “return cùng publisher type” không chứng minh nó bảo toàn mọi protocol event, nhất là khi advice wrap publisher và tự subscribe.

## 15. Concurrency, locks và reentrancy

Một aspect dùng chung có thể có mutable fields như `lastStartNanos`, `lastResult`, `currentUser`. Nhiều invocations đồng thời có thể ghi đè chúng và làm result/logging sai, đồng thời giữ references ngoài ý định.

### 15.1. Shared advice state và data race

Một timer aspect lưu thời điểm bắt đầu vào field chung có thể tính nhầm duration khi thread B overwrite giá trị của A. Dùng state local theo invocation hoặc cơ chế context/synchronization phù hợp thay vì trộn những requests.

### 15.2. Per-target không đồng nghĩa thread-safe

Một target singleton có thể nhận nhiều threads. Aspect state per-target cũng được nhiều threads dùng chung. `int count++` không atomic; cần `AtomicInteger`, `LongAdder` hoặc synchronized invariant tùy yêu cầu.

### 15.3. Locking behavior có thể khác giữa proxy và target

`synchronized(proxy)` và `synchronized(target)` là hai monitors khác nhau nếu chúng là hai objects riêng. Nếu client trước đây phối hợp threads bằng target monitor, thay reference bằng proxy có thể phá assumptions về synchronization.

Tương tự, một synchronized method trên target chỉ khóa **target monitor** khi body chạy; handler có thể làm việc trước/sau ngoài lock đó. Không khẳng định một proxy giữ nguyên atomicity toàn bộ advice+method nếu không có synchronization contract.

### 15.4. Reentrancy và self-invocation

Một target method có thể gọi `this.otherMethod()`. Với delegating proxy, internal call không nhất thiết đi qua handler, nên một policy như metrics/security/retry gắn ở proxy inner có thể không chạy.

Ngược lại, advice gọi vào chính các method được pointcut chọn có thể tạo vòng đệ quy instrumentation. Substitutability đòi kiểm tra **đường call thực**, không chỉ matcher có thể chọn method theo tên.

### 15.5. ThreadLocal và async context

Một advice lưu principal/trace context trong `ThreadLocal` nhưng quên restore/remove có thể để request sau trên cùng pool thread nhìn nhầm dữ liệu cũ. Một async callback ở thread khác không tự được thừa hưởng context. Đó là tác động tới behavior/privacy dù return value có thể đúng.

## 16. Type visibility, reflection, annotation và serialization

### 16.1. Exact class checks

Một code:

~~~java
if (object.getClass() == Ledger.class) {
    // một nhánh chỉ chạy với đúng concrete class
}
~~~

không đi vào nhánh với JDK interface proxy, dù proxy thực hiện cùng interface. Đây là ranh giới hợp đồng: nếu client được phép đòi exact class, proxy có thể không thay thế được.

### 16.2. `instanceof` cũng phụ thuộc proxy mechanism

JDK proxy implements declared interfaces; nó có thể `instanceof Interface` nhưng không phải `instanceof ConcreteTarget`. Class-based proxy có thể là subclass tương thích với concrete class khi kỹ thuật đó được phép; binary weaving có thể giữ type identity. Phải kiểm tra **artifact và engine cụ thể**.

### 16.3. Reflection tìm annotation

Annotation có thể đặt trên interface, implementation method, class hoặc inheritance hierarchy. Handler của JDK proxy thường nhận một `Method` liên quan tới interface invocation; reflection trên proxy class có thể thấy metadata khác implementation. Một annotation được đọc ở nhầm nơi có thể làm policy đổi behavior dù method vẫn trả đúng result.

### 16.4. Serialization

Nếu caller serialize object theo concrete class schema hoặc record mapping, thay target bằng proxy có thể:
- Làm kiểu runtime trong payload khác.
- Thêm handler/interceptor references vào graph.
- Không còn thỏa yêu cầu serializable.
- Kéo theo phiên bản code và classloader khác.
- Làm thay đổi equality/identity sau deserialize.

Không cho rằng “proxy có cùng interface” nghĩa là JSON/object serialization luôn tương đương. Cần test round-trip và schema ở consumer boundary.

### 16.5. Native/AOT và runtime code generation

Trong môi trường AOT/native, runtime code generation hoặc reflection có thể bị giới hạn. Một JDK proxy hoặc agent instrument hoạt động trong JVM thường không bảo đảm tương thích tự động với mọi runtime đóng. Đây là một điều kiện triển khai cần xác minh chứ không phải phần cốt lõi của substitutability.

## 17. Những biến đổi advice có chủ đích và tiêu chí chấp nhận

Thay kết quả hoặc hành vi không tự “vi phạm LSP” nếu abstraction **đã được định nghĩa** để cho phép chính sách ấy. Câu hỏi là **hợp đồng ở biên nào**.

| Loại advice | Có thể thay đổi gì? | Điều cần công bố/kiểm |
| --- | --- | --- |
| **Logging/tracing** | Thời gian, I/O telemetry, context. | Không che lỗi business, không rò dữ liệu, bounded overhead. |
| **Metrics** | Shared counters, sampling, callback. | Không mutate result, thread-safe, đúng đơn vị đo. |
| **Authorization** | Từ chối một số inputs/callers. | Điều kiện quyền là một phần contract, không có bypass. |
| **Cache** | Bỏ qua target, trả giá trị lưu. | Freshness, identity, tenant isolation, side effects. |
| **Retry** | Nhiều target attempts, delays. | Idempotency, attempt limits, timeout, lỗi được retry. |
| **Fallback** | Exception thành value. | Domain meaning, degraded mode, không false success. |
| **Normalization** | Kết quả/arguments đổi dạng. | Postcondition, type, ordering, nullability. |
| **Transaction boundary** | Commit/rollback behavior. | Resource scope, propagation, failure semantics. |
| **Audit** | External write/notification. | Thời điểm success, khả năng lưu bền, privacy. |
| **Resource management** | Open/close/release. | Ownership, async/lazy result lifecycle. |

### 17.1. Tách API contract với instrumentation policy

Một interface có thể công bố rằng tất cả operations được audit; khi đó audit không phải chi tiết “vô hình”. Một interface khác chỉ hứa kết quả nghiệp vụ và coi telemetry là implementation detail. Nên mô tả những effects được bảo đảm để tránh lời hứa substitutability mơ hồ.

### 17.2. Bảo mật là bài toán trust boundary

Nếu authorization phụ thuộc một proxy nhưng caller có thể giữ target gốc hoặc tự gọi nội bộ bypass handler, enforcement có thể thiếu độ phủ. Bảo vệ invariant quan trọng ở trusted boundary thích hợp, sau đó dùng aspect hỗ trợ nhất quán và observability. Đừng tuyên bố mọi lời gọi đã được bảo vệ chỉ vì pointcut có tên `secure*`.

### 17.3. Advice ordering là một phần của hợp đồng quan sát

`Auth(Cache(Target))` và `Cache(Auth(Target))` không nhất thiết tương đương khi Cache short-circuit. `Retry(Metrics(Target))` và `Metrics(Retry(Target))` cũng khác đơn vị đếm. Phải khai precedence tường minh và test đường return/throw/skip.

## 18. Ma trận contract tests cho đối tượng gốc và đối tượng đã áp dụng aspect

Để kiểm tra substitutability, viết **một bộ hợp đồng** dùng được cho nhiều implementations, gồm raw target, decorator, proxy hoặc woven variant (trong phạm vi kỹ thuật).

### 18.1. Những test về kiểu và method result

| Test | Cần xác minh |
| --- | --- |
| Interface compatibility | Client chỉ biết interface có gọi đủ API được không? |
| Primitive `0`, `false`, `-1` | Advice giữ đúng sentinel hợp lệ? |
| `null` cho reference | Nullability contract đúng không? |
| `Optional.empty()` | Có bị đổi thành null hoặc false success không? |
| Collections/generics | Kiểu phần tử, order, mutability đúng? |
| Identity | `==` có được contract cho phép khác không? |
| Concrete cast | Client có yêu cầu concrete type không? |
| Equality/hashing | Reflexive/symmetric/transitive/hashCode consistent? |
| `toString`/reflection | Những quan sát được hứa có giữ không? |
| `void` | Side effect và error contract có giữ không? |

### 18.2. Exception và failure tests

- Target throw đúng loại; advice truyền lỗi gốc hay mapping được công bố.
- Exception có cause/diagnostic metadata cần thiết.
- Checked exceptions qua proxy không bị bọc ngoài ý định.
- Target lỗi trước side effect và sau side effect được phân loại đúng.
- Timeout/cancellation ở async API không bị biến thành “success”.
- Lỗi logger/metrics không che nghiệp vụ nếu policy là best effort.

### 18.3. State and side-effect tests

- Một lần deposit tăng đúng lượng và result bằng state sau mutation.
- Retry không nhân side effects trái hợp đồng.
- Cache hit không bỏ qua một side effect được bảo đảm.
- Caching mutable result không làm caller B nhìn thay đổi bất hợp pháp của caller A.
- Các concurrent increments/updates thỏa contract thread safety đã công bố.
- Resource owner đóng đúng thời điểm; advice không giữ target/stream quá lâu.

### 18.4. Call-path tests

- Caller gọi qua proxy.
- Caller giữ target trực tiếp.
- Target gọi `this.inner()`.
- Callback/method reference capture proxy hay target.
- Classloader/module/pointcut include/exclude đủ điều kiện.
- Nhiều aspects áp cùng join point theo thứ tự đã định.

Nếu một policy là **bắt buộc**, test phải chứng minh đường bypass không thể vi phạm invariant; không chỉ chứng minh một happy path qua proxy chạy thành công.

### 18.5. Async và lazy result tests

- Method trả stage còn pending; không block.
- Thành công/exception/cancellation sau method return.
- Original/dependent stage identity nếu được công bố.
- Streaming iterator chưa bị consumed trước caller.
- Resource close theo ownership, không theo lúc around advice return.
- Completion callbacks lỗi không phá outcome nếu chúng chỉ quan sát.

### 18.6. Snapshot so sánh raw và proxied có giới hạn

Một cách tiếp cận là khởi tạo hai targets tương đương, chạy cùng sequence commands trên raw và proxied, rồi so **observations theo contract**.

Nhưng đừng so trực tiếp results khi method dùng time, randomness, network hoặc side effects không thể tái chạy: cần test doubles, fixed clocks, deterministic schedulers và stable fixtures. Nếu proxy được thiết kế có retry/cache/fallback, phải đưa **khác biệt được phép** vào oracle, thay vì yêu cầu mọi output byte-for-byte giống nhau.

## 19. Tiêu chí kết luận “substitutable” ở từng ranh giới

Một checklist phê duyệt thực tế:

1. **Client abstraction:** client đang giữ interface/base class hay concrete implementation?
2. **Methods available:** proxy/decorator expose đủ operations được phép không?
3. **Type contract:** primitive/ref/null/generic tương thích không?
4. **Semantic return:** giá trị cùng ý nghĩa, kể cả sentinel và absence không?
5. **Side effects:** số lần và thứ tự thay đổi trạng thái có đúng không?
6. **Exception contract:** loại/cause/timing lỗi có giữ không?
7. **Identity/equality:** caller có dựa vào `==`, `equals`, `hashCode`, `getClass` không?
8. **Lifecycle:** resource ownership, close/dispose, GC retention có bị đổi không?
9. **Concurrency:** thread-safety, synchronized/monitor, reentrancy và ThreadLocal có phù hợp không?
10. **Async:** future identity, completion, cancellation và thread context đúng không?
11. **Lazy results:** stream/publisher/iterator có giữ protocol không?
12. **Proxy coverage:** mọi caller cần policy đi qua interceptor đúng không?
13. **Weaving coverage:** class và join point đã thật sự được weave đúng chưa?
14. **Ordering:** retry/cache/auth/metrics/transaction lồng nhau theo ngữ nghĩa nào?
15. **Security:** trusted boundary và các invariant bắt buộc được bảo vệ ở đâu?
16. **Performance budget:** startup/latency/cpu/memory giới hạn đã được đo không?
17. **Compatibility:** JDK, classloader, serialization, build/runtime toolchain?
18. **Tests:** cả happy path, empty, failure, direct-call, concurrent và async đều PASS?
19. **Rollback:** nếu một aspect làm sai contract, có thể vô hiệu hóa/rollback artifact an toàn không?
20. **Documentation:** các khác biệt cố ý so với raw target có được công bố rõ không?

### 19.1. Không đòi “giống hệt” nếu contract không yêu cầu

Một proxy tăng vài dòng log có thể vẫn thay thế hợp lệ nếu logging không phá chính sách latency/privacy và caller không được quyền dựa vào stack trace/identity cụ thể. Mục tiêu không phải loại mọi khác biệt vật lý, mà là bảo toàn **tập thuộc tính đã cam kết với client**.

### 19.2. Một aspect không “transparent” vẫn có thể có giá trị

Một retry hoặc authorization aspect có thể cố ý làm hành vi khác raw target. Khi đó nó phải được xem là một **biến thể có hợp đồng rõ**, không được quảng cáo như bản thay thế hoàn toàn không quan sát được sự khác biệt. Client phải biết outcome và boundary nào được bảo đảm.

## 20. Các lỗi tư duy thường gặp

**“Proxy implement cùng interface nghĩa là thay thế được hoàn toàn.”** Sai. Kiểu không chứng minh semantics, identity hoặc side effects.

**“`instanceof Interface` true thì `instanceof ConcreteTarget` cũng true.”** Không với JDK interface proxy.

**“`equals` và `hashCode` không quan trọng vì chỉ là Object methods.”** Sai khi object dùng làm key trong collections hoặc làm identity token.

**“Làm proxy.equal(target) true chắc chắn tốt hơn.”** Không nếu target.equal(proxy) false hoặc hash codes khác.

**“Giữ nguyên return value bảo đảm hành vi giữ nguyên.”** Không nếu advice đã mutate result hoặc chạy target nhiều lần.

**“Một result cùng kiểu thì contract được bảo toàn.”** Sai nếu `0`, empty, null, exception và future biểu diễn các outcome khác nhau.

**“`void` không có contract trả về nên skip target vô hại.”** Sai: side effects và exception vẫn là hợp đồng.

**“Retry thành công cuối cùng nghĩa là toàn bộ operation đúng.”** Không nếu đã lặp external side effects.

**“Cache hit có thể skip mọi inner advice mà không ảnh hưởng gì.”** Không nếu nó bỏ qua quyền/audit được bảo đảm hoặc trả dữ liệu stale trái contract.

**“Object proxy và target khóa trên cùng một monitor.”** Không nếu là hai objects riêng biệt.

**“Một method async return stage tức toàn bộ task thành công.”** Không; completion có thể fail về sau.

**“Serialization của target và proxy luôn tương đương.”** Không; runtime type và object graph khác nhau.

**“Bytecode weaving không tạo proxy nên chắc chắn không phá substitutability.”** Sai; code được dệt vẫn có thể thay error/return/ordering/timing.

**“Cứ test proxy happy path là đủ.”** Không; các vi phạm thường nằm ở negative paths, async, identity, retries, concurrency hoặc resource lifetime.

**“Chỉ cần đảo priority để sửa đường gọi trực tiếp target.”** Không; ordering không bù cho đường bypass interception.

## 21. Bài tập tự đánh giá có đáp án

### Câu 1 — Interface và concrete class

Một `Ledger` được bọc bằng JDK proxy triển khai `BalancePort`. Caller dùng `BalancePort` ổn, nhưng caller cast sang `Ledger` lỗi. Có mâu thuẫn không?

**Đáp án:** Không. Proxy có khả năng thay thế ở **interface boundary** khi behavior đúng, không phải concrete subtype của target.

### Câu 2 — Cùng kiểu, sai state

`deposit(5)` trả `int 999`, balance không đổi. Method signature hợp lệ, vậy là substitutable?

**Đáp án:** Không. Postcondition balance tăng 5 và return balance mới bị vi phạm.

### Câu 3 — Equality bất đối xứng

Proxy.equals(target) true nhưng target.equals(proxy) false. Hợp đồng nào sai?

**Đáp án:** Tính đối xứng của `Object.equals`; nếu hash codes không khớp còn có lỗi liên quan `hashCode`.

### Câu 4 — `Optional.empty()` và storage error

Advice bắt lỗi DB rồi trả empty Optional. Vì sao có thể sai?

**Đáp án:** Empty nghĩa là không có dữ liệu, còn lỗi DB nghĩa là không biết. Caller có thể thực hiện một hành động sai dựa trên absence giả.

### Câu 5 — Side effect khi retry

Một lệnh external `charge()` có response timeout sau commit, advice retry lần hai rồi trả OK. Kiểu đúng có đủ không?

**Đáp án:** Không; duplicate charge có thể phá đúng-một-lần, cần idempotency hoặc reconciliation.

### Câu 6 — Monitor

Caller từng dùng `synchronized(target)`; bây giờ dùng `synchronized(proxy)` trong khi worker khác vẫn khóa target. Có chắc cùng lock?

**Đáp án:** Không nếu target/proxy là hai objects khác nhau. Cần thiết kế synchronization boundary rõ.

### Câu 7 — Async identity

Advice trả `stage.whenComplete(callback)` thay stage gốc. Cùng `CompletionStage<T>`, vậy hợp đồng chắc chắn giống?

**Đáp án:** Không. Dependent stage có identity và error/cancellation behavior khác nếu callback hoặc policy ảnh hưởng completion.

### Câu 8 — Self-invocation

Pointcut chọn method inner, nhưng target.outer gọi `this.inner()` không qua proxy. Có phải pointcut sai?

**Đáp án:** Không nhất thiết. Trong mô hình delegating proxy, handler không nhận inner invocation; đó là khác biệt coverage/call path.

### Câu 9 — Stream được đo bằng count

Advice nhận stream, gọi `count()` để log rồi return cùng stream. Vì sao có thể không substitutable?

**Đáp án:** Terminal operation có thể tiêu thụ stream, caller không còn dùng được theo hợp đồng lazy.

### Câu 10 — Authorization

Một aspect chặn unauthorized callers, làm behavior của raw target khác đi. Có được dùng không?

**Đáp án:** Có thể, nếu authorization là **một phần của API/security contract** và được bảo đảm tại trusted boundary; không gọi nó hoàn toàn transparent đối với tất cả inputs.

### Câu 11 — Concrete annotations

Một tool đọc annotation trên implementation class; thay target bằng JDK proxy rồi reflection trên `proxy.getClass()` không thấy annotation đó. Bài học?

**Đáp án:** Runtime class của proxy khác implementation; metadata lookup phải theo nơi annotation thực sự khai báo và theo hợp đồng công cụ.

### Câu 12 — Contract tests

Một proxy trả đúng mọi happy-path values, nhưng khi target ném lỗi thì handler nuốt lỗi và return 0. Nên bổ sung test nào?

**Đáp án:** Negative tests cho exception type/cause và phân biệt exception với sentinel `0`, kết hợp assert state/side effects.

## Kết luận

**Khả năng thay thế của đối tượng sau khi áp dụng aspect** phải được xét ở **ranh giới hợp đồng mà client thực sự sử dụng**. Một interface proxy có thể thay thế target rất tốt cho các operations của interface, nhưng không tự có cùng concrete class, identity, equality, reflection hay lifecycle. Một engine weave trực tiếp có thể giữ nguyên object identity mà vẫn làm sai kết quả, ngoại lệ, side effects hoặc thời gian hoàn thành.

Với aspect quan sát, hãy ưu tiên **bảo toàn result/error, số lần tiếp tục, trạng thái nghiệp vụ và quyền sở hữu resource**, đồng thời tránh rò dữ liệu/context. Với aspect can thiệp chủ động như cache, retry, fallback và authorization, cần **công bố rõ khác biệt được phép** rồi xác minh toàn bộ precondition, postcondition, invariant, temporal behavior và failure contract. Một thiết kế AOP tốt không chỉ khiến caller có thể **gọi được** object đã bọc, mà khiến caller tiếp tục **tin đúng những điều abstraction đã hứa**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-exception-contracts-008">Quy định hành vi khi chức năng cắt ngang không khả dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Quy định hành vi khi chức năng cắt ngang không khả dụng trong AOP

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**), **aspect** bổ sung những chính sách cắt ngang như đo thời gian, tracing, ghi log, kiểm toán, kiểm tra quyền, giao dịch, cache hoặc retry vào các **join point** được **pointcut** lựa chọn. Những chức năng này không phải lúc nào cũng sẵn sàng: máy chủ ghi log có thể chậm, bộ thu metrics ngừng nhận dữ liệu, bộ kiểm tra quyền có thể không phản hồi, nơi lưu audit có thể đầy, hoặc một interceptor không khởi tạo được.

Câu hỏi quan trọng không phải chỉ là “bắt ngoại lệ nào?”, mà là: **khi chức năng cắt ngang không khả dụng, nghiệp vụ có được tiếp tục không, tiếp tục trong điều kiện nào, và người gọi phải nhận kết quả gì?**

Nếu cơ chế **quan sát tùy chọn** không khả dụng, có những hệ thống được thiết kế để **tiếp tục nghiệp vụ** và báo tình trạng suy giảm. Nhưng nếu một chính sách **bảo mật bắt buộc** không thể xác minh quyền, tiếp tục như người dùng đã được phép có thể tạo lỗ hổng. Nếu **ghi nhận kiểm toán bền vững** là điều kiện trước một tác dụng phụ quan trọng, việc không ghi được có thể bắt buộc **từ chối trước khi thay đổi dữ liệu**. Nếu lỗi chỉ lộ ra **sau khi nghiệp vụ đã cam kết hiệu ứng**, kết luận “không thành công, cứ retry” có thể tạo hiệu ứng trùng.

Không có một câu trả lời `fail-open` hay `fail-closed` đúng cho mọi aspect. Phải định nghĩa **hợp đồng suy giảm (degradation contract)** dựa trên tính bắt buộc của chính sách, ranh giới tin cậy, khả năng xác nhận trạng thái nghiệp vụ và những đường có thể bypass.

Bài này trình bày **Aspect/AOP cơ bản và nâng cao nói chung**. Cú pháp và loại join point được một công cụ hỗ trợ có thể khác nhau; ví dụ Java 17 dùng JDK dynamic proxy để minh họa một đường triển khai và chủ động ghi rõ các giới hạn của nó.

## 1. Mục tiêu học tập

Sau bài học, bạn có thể:

1. Phân biệt **chức năng nghiệp vụ chính** với **chức năng cắt ngang**, rồi đánh giá chính sách nào chỉ hỗ trợ quan sát và chính sách nào là điều kiện an toàn bắt buộc.
2. Mô tả những trạng thái **AVAILABLE, UNAVAILABLE, UNKNOWN, DEGRADED** và hậu quả của việc diễn giải một lỗi kết nối thành quyết định nghiệp vụ.
3. Chọn và ghi thành hợp đồng hành vi **fail-open, fail-closed, fail-safe, fallback** và **degrade-with-signal** trong từng tình huống.
4. Tách lỗi xảy ra **trước khi target chạy**, **trong target**, **sau khi target tạo hiệu ứng** và lỗi trong **cleanup/advice**.
5. Xác định rõ chính sách về **kết quả trả về, ngoại lệ, retry, idempotency, audit và dữ liệu điều khiển**.
6. Hiểu vai trò của **thứ tự aspect**, đặc biệt khi một interceptor có thể chặn không cho `proceed`.
7. Thiết kế cơ chế dự phòng đúng phạm vi, health check, timeout, circuit breaker, backpressure và phục hồi mà không khiến hệ thống che giấu lỗi quan trọng.
8. Đọc và chạy ví dụ Java 17 về observer có thể suy giảm, authorization bắt buộc, audit trước hiệu ứng và bảo toàn ngoại lệ.
9. Xây dựng ma trận kiểm thử lỗi bao gồm nguồn lỗi cắt ngang, nguồn lỗi nghiệp vụ, sự không chắc chắn sau commit và đường bypass AOP.
10. Quy định trách nhiệm xử lý thông báo, dấu vết vận hành và phục hồi theo hợp đồng.

## 2. Hiểu đúng “không khả dụng”

“Không khả dụng” có thể là nhiều hiện tượng khác nhau:

| Hiện tượng | Ví dụ | Điều không được suy ra |
| --- | --- | --- |
| **Dependency down** | Metrics sink không nhận kết nối | Không suy rằng target nghiệp vụ cũng lỗi |
| **Timeout** | Policy service không trả trong thời hạn | Không suy rằng người dùng được phép hay bị từ chối |
| **Initialization failure** | Aspect/interceptor không tạo được | Không suy rằng hệ thống có thể chạy bỏ qua an toàn |
| **Configuration invalid** | Thiếu khóa cấu hình audit | Không nên im lặng tắt audit bắt buộc |
| **Capacity exhausted** | Queue telemetry đầy, audit store hết dung lượng | Không giả định thêm retry sẽ giải quyết |
| **Partial failure** | Tạo dữ liệu xong nhưng không ghi audit | Không suy rằng hiệu ứng nghiệp vụ chưa xảy ra |
| **Stale state** | Cache quyền còn dữ liệu cũ | Không mặc định dữ liệu hết hạn là quyết định hiện tại |
| **Path not intercepted** | Caller gọi target gốc ngoài proxy | Không coi đó là backend aspect bị down |
| **Advice bug** | `NullPointerException` trong logger | Không nên tự xếp mọi lỗi code vào “dịch vụ không khả dụng” |
| **Shutdown/reload** | Runtime đóng interceptor hoặc đổi cấu hình | Phải xác định policy đang có hiệu lực ở giai đoạn nào |

Một trạng thái **UNKNOWN** có thể quan trọng hơn cả “down”: ví dụ request kiểm tra quyền đã gửi nhưng phản hồi bị mất. Đó **không phải bằng chứng cho phép**. Với thao tác có tác dụng phụ, timeout sau khi đã gửi lệnh cũng **không chứng minh hiệu ứng chưa xảy ra**.

### 2.1. Tách lỗi của advice khỏi lỗi của target

Tại một join point, cần hỏi:

- Lỗi do advice/đối tác của aspect tạo ra?
- Lỗi do chính target nghiệp vụ ném?
- Lỗi do proxy/reflection chuyển đổi hoặc bọc?
- Lỗi do lời gọi `proceed` bị bỏ qua, gọi hai lần, hoặc từ chối?
- Lỗi do xử lý sau target, ví dụ ghi audit hoàn tất?
- Lỗi do `finally` khi giải phóng tài nguyên?

Nếu một interceptor `catch (Exception)` rồi mặc định tiếp tục, nó có thể vô tình **nuốt cả lỗi nghiệp vụ**. Nếu một interceptor quanh target `catch (Throwable)` rồi trả `null`, nó còn có thể che lỗi nghiêm trọng hoặc gây lỗi khác ở phương thức trả primitive.

**Chỉ xử lý những lỗi được hợp đồng của từng chính sách cho phép xử lý**; truyền tiếp các lỗi còn lại.

## 3. Phân loại bắt buộc trước khi viết fallback

Để quyết định ứng dụng có thể chạy khi aspect không khả dụng, hãy phân loại **giá trị nghiệp vụ và nghĩa vụ pháp lý/kỹ thuật** của từng chính sách.

### 3.1. Nhóm quan sát hỗ trợ

Có thể gồm một số metric, trace hoặc debug logging **không bắt buộc** để thực hiện giao dịch. Hệ thống có thể chấp nhận mất mẫu quan sát trong thời gian ngắn nếu:

- Mất dữ liệu không vi phạm nghĩa vụ kiểm toán/bảo mật;
- Lỗi observer không sửa input, output hoặc số lần chạy của target;
- Tình trạng degraded được đo/hiển thị theo cách độc lập có thể kiểm chứng;
- Có giới hạn buffer, retry và thời gian chờ;
- Có kế hoạch phục hồi và hậu kiểm chất lượng quan sát.

**Không phải mọi logging đều tùy chọn.** Nhật ký phục vụ tuân thủ, chống gian lận hoặc điều tra bắt buộc có thể thuộc nhóm khác.

### 3.2. Nhóm kiểm soát bắt buộc

Thường gồm kiểm tra quyền trước thao tác nhạy cảm, chính sách bắt buộc để chấp nhận đơn, yêu cầu kiểm toán bền vững hoặc điều kiện bảo toàn nhất quán trước commit. Nếu thiếu kết quả đáng tin, hệ thống cần **từ chối hoặc chuyển sang đường dự phòng được xác thực**.

Một policy service down không phải lý do để diễn giải `UNKNOWN` thành `ALLOW`. Một fallback chỉ được xem là an toàn khi bản thân nó bảo đảm được **cùng bất biến**, ví dụ quyết định đã ký còn hiệu lực theo quy tắc kiểm chứng chặt, chứ không phải đơn giản trả `true`.

### 3.3. Nhóm tối ưu hóa

Cache, batch/aggregation hoặc một số cơ chế rate-control phụ trợ có thể có fallback về đường xử lý gốc **nếu** việc bỏ qua không làm mất bất biến. Ví dụ cache đọc dữ liệu có thể rơi về nguồn chính khi cache down; còn cache đang giữ dữ liệu quyền hoặc một cơ chế chặn giới hạn bắt buộc có thể **không** được bypass.

Chỉ gọi một tính năng là “tối ưu hóa” không đủ: phải xem nó có là **nguồn duy nhất của một kiểm soát** hay không.

### 3.4. Nhóm bảo toàn vòng đời

Aspect mở transaction, lock, tracing scope hay tài nguyên phiên gọi có trách nhiệm giải phóng theo đúng hợp đồng. Nếu không mở được một scope bắt buộc, thường không thể chạy tiếp như scope đã mở. Nếu đã mở rồi mà cleanup lỗi, cần xử lý **lỗi gốc, tài nguyên còn tồn đọng và trạng thái không chắc chắn**, không bỏ qua bằng `catch` rộng.

## 4. Fail-open, fail-closed và fail-safe

| Chiến lược | Ý nghĩa | Tình huống phù hợp theo hợp đồng |
| --- | --- | --- |
| **Fail-open** | Cho phép một đường nghiệp vụ tiếp tục khi policy phụ không phục vụ được | Telemetry phụ trợ không phải điều kiện an toàn |
| **Fail-closed** | Không cho hành động bảo vệ tiếp tục nếu không xác nhận chính sách bắt buộc | Không xác minh được quyền |
| **Degrade-with-signal** | Tiếp tục với chất lượng thấp hơn và phát tín hiệu rõ | Mất metrics sampling, không ảnh hưởng effect |
| **Verified fallback** | Dùng đường dự phòng có cùng cam kết bắt buộc | Chính sách quyền dự phòng đủ tin cậy |
| **Queue/defer** | Tạm nhận yêu cầu ở trạng thái chưa hoàn tất với cam kết ghi nhận phù hợp | Chỉ khi có durable acceptance hợp lệ |
| **Read-only mode** | Chặn ghi nhưng có thể cho một số thao tác đọc an toàn | Mất khả năng xác minh yêu cầu cho thao tác ghi |
| **Hard stop** | Không khởi động/không phục vụ nếu điều kiện bắt buộc sai | Thiếu khóa cấu hình audit bắt buộc |

**Fail-open không đồng nghĩa “bỏ qua mọi exception của advice”.** Nó chỉ được áp dụng cho **loại lỗi và chính sách đã phân loại là cho phép tiếp tục**. Nếu lỗi là vi phạm bất biến hoặc sai thiết kế, chuyển nó thành tín hiệu “observer tạm ngừng” sẽ làm khó chẩn đoán và có thể tạo dữ liệu sai.

**Fail-closed không đồng nghĩa luôn ném một `RuntimeException` chung.** Hợp đồng API có thể trả mã lỗi xác định, trạng thái pending, một kết quả từ chối có kiểu hoặc ngoại lệ chuyên biệt. Điểm cốt lõi là **không thực hiện hành động không còn được phép bảo đảm**.

### 4.1. Ma trận quyết định mẫu

| Chức năng cắt ngang không khả dụng | Có tiếp tục target không? | Ghi chú |
| --- | --- | --- |
| Debug trace tùy chọn | Có thể, nếu được phép mất trace | Ghi degraded signal, không chặn target |
| Metrics collector phụ trợ | Có thể, nếu không là nghĩa vụ bắt buộc | Buffer có giới hạn hoặc drop có kiểm soát |
| Authorization bắt buộc | Không, nếu không có đường kiểm chứng tin cậy khác | UNKNOWN ≠ ALLOW |
| Admission audit bắt buộc trước effect | Không, nếu không có durable fallback tương đương | Chặn trước target |
| Audit đã thất bại **sau effect** | Không thể hoàn tác giả định | Cần trạng thái in-doubt, đối soát/outbox |
| Cache cho phép bỏ qua | Có thể gọi nguồn chính | Không bypass validation/security |
| Rate-limit bắt buộc | Tùy chính sách, thường chặn hoặc dự phòng mạnh | Không vô hiệu quota âm thầm |
| Transaction scope không tạo được | Thường không chạy effect cần transaction | Bảo vệ atomicity |
| Trace span đã mở nhưng đóng lỗi | Xử lý lỗi quan sát theo hợp đồng | Không che ngoại lệ target trái quy tắc |
| Config policy không hợp lệ ở startup | Thường fail startup | Không mặc định tắt guard |

Đây là **ma trận thiết kế**, không phải luật mặc định cho mọi nghiệp vụ. Đặc biệt audit và log có thể là bắt buộc hoặc tùy chọn tùy mức rủi ro và cam kết.

## 5. Viết một hợp đồng suy giảm có thể kiểm chứng

Một hợp đồng vận hành tốt nên ghi:

1. **Chính sách nào?** Tên và trách nhiệm riêng: telemetry, authorization, audit, cache, transaction...
2. **Chức năng đó có bắt buộc cho thao tác nào?** Một request đọc và một giao dịch ghi có thể khác nhau.
3. **Không khả dụng nghĩa gì?** Down, timeout, trả lỗi, invalid config, không xác định, overload...
4. **Lỗi xảy ra ở mốc nào?** Trước khi target chạy, trong target, sau commit, khi cleanup hay lúc khởi tạo.
5. **Có đường fallback hợp lệ nào không?** Tiêu chuẩn xác thực, độ tươi dữ liệu, giới hạn thời gian.
6. **Kết quả người gọi nhận gì?** Giá trị, ngoại lệ, error code, pending/in-doubt, idempotency token.
7. **Tác dụng phụ có thể đã xảy ra không?** Nếu có, không retry thiếu bằng chứng.
8. **Ai có quyền retry?** Aspect, caller, orchestrator hay worker phục hồi.
9. **Giới hạn retry là gì?** Deadline, attempts, backoff, bulkhead, circuit breaker và budget.
10. **Tín hiệu degraded nào được tạo?** Metric nội bộ, readiness/health, alert, event vận hành.
11. **Làm thế nào phục hồi?** Khi backend quay lại, backlog, tái đồng bộ, đối soát và reset state.
12. **Kiểm thử thế nào?** Tất cả nhánh, bao gồm đường bypass và trường hợp chỉ một nửa thành công.

Ví dụ bản hợp đồng ngắn:

```text
Policy: OptionalMeasurement
Failure: exporter timeout / queue full
Action: keep one target invocation, do not alter result/exception
Buffer: bounded; drop samples after budget exhausted
Signal: exporter_degraded, dropped_samples
Recovery: resume on successful health probe

Policy: MandatoryAuthorization
Failure: denied -> reject; unknown/unreachable -> unavailable error
Action: do not execute sensitive effect
Fallback: only independently verified authorization decision
Signal: policy_unavailable; no private data in event
Recovery: health check then allow new verified decisions
```

Đây là **mẫu mô tả hợp đồng**, không phải cú pháp cấu hình AOP chung.

## 6. Mốc lỗi tương đối với `proceed`

Một chuỗi interceptor có thể hình dung:

```text
Caller
  |
  v
Telemetry (optional)
  |
  v
Authorization (mandatory)
  |
  v
AdmissionAudit (mandatory)
  |
  v
Target (effect)
  |
  v
Completion processing
```

Điểm phân biệt quan trọng là **lỗi xảy ra trước hay sau khi lời gọi đã vượt qua ranh giới tạo hiệu ứng**.

### 6.1. Lỗi trước khi chuyển tiếp

Một authorization interceptor không thể xác minh quyền:

```text
before target:
    decision = UNKNOWN
    -> reject without proceed()
```

Nếu target chưa chạy, việc chặn tương đối rõ: không có hiệu ứng của target **qua đường đang xét**. Nhưng phải đảm bảo không có đường bypass khác.

### 6.2. Lỗi ở lời gọi target

Nếu target ném `IllegalArgumentException`, một telemetry interceptor ở ngoài **không được tự coi đây là lỗi của telemetry backend** rồi nuốt lỗi. Cần phân biệt đường throw của nghiệp vụ và lỗi khi thực hiện chính logic quan sát.

Khi dùng reflection, `Method.invoke` có thể bọc lỗi do target ném trong `InvocationTargetException`. Một proxy/interceptor cần bóc đúng `getCause()` khi muốn bảo toàn loại lỗi gốc.

### 6.3. Lỗi sau khi effect đã xảy ra

Một trình ghi audit hậu xử lý hoặc exporter có thể lỗi **sau khi target đã cam kết**:

```text
Target creates effect -> returns successful result
After-advice sends audit -> network timeout
```

Nếu after-advice ném lỗi ra client, client thấy thất bại dù effect có thể đã hoàn thành. Nếu client retry ngay mà không có cơ chế idempotency, có thể tạo **effect thứ hai**. Nếu advice nuốt lỗi để trả success nhưng audit thực sự là bắt buộc, hệ có thể **vi phạm cam kết ghi nhận**.

Giải pháp thường cần điều chỉnh **ranh giới commit, durable audit/outbox, giao thức xác nhận hoặc trạng thái in-doubt**, không chỉ thêm một `catch` trong after advice.

### 6.4. Lỗi trong cleanup

Nếu target đã ném lỗi gốc và `finally` của một advice lại ném lỗi khác, caller có thể chỉ thấy lỗi cleanup. Với aspect chỉ quan sát, quy tắc thường mong muốn là **bảo toàn lỗi gốc** và báo sự cố quan sát trên kênh riêng.

Với tài nguyên bắt buộc, cleanup failure có thể làm trạng thái giao dịch không chắc chắn. Không được luôn nuốt lỗi cleanup nếu điều đó che việc lock/transaction chưa được giải phóng.

## 7. Thứ tự aspect thay đổi ý nghĩa khi một thành phần hỏng

Xét:

```text
Cache(Authorization(Target))
```

Nếu Cache hit và không gọi `proceed`, Authorization bên trong không chạy. Nếu dữ liệu cần được kiểm quyền mỗi lần đọc, thứ tự này có thể cho phép đường truy cập trái hợp đồng khi cache không yêu cầu quyền.

Ngược lại:

```text
Authorization(Cache(Target))
```

giúp bảo đảm auth check chạy trước đường cache trong mô hình chuỗi, **nhưng** còn phải bảo vệ quyền ở tất cả đường gọi khác và thiết kế cache key gắn với tenant/user đúng cách.

### 7.1. Telemetry ngoài guard

```text
Telemetry(Authorization(Target))
```

Nếu Telemetry xử lý lỗi của **chính exporter** và tiếp tục, guard vẫn chạy. Nếu Telemetry dùng `catch (Throwable)` bao cả `proceed` và coi mọi lỗi là “exporter offline”, nó có thể nuốt luôn ngoại lệ **từ Authorization hoặc Target**. Đây là lỗi phân loại **nguồn lỗi**, không phải chính sách fail-open hợp lệ.

### 7.2. Retry bao ngoài guard

```text
Retry(Authorization(Target))
```

Một Retry rộng bắt mọi `RuntimeException` có thể retry ngay cả **SecurityException** hoặc lỗi input. Điều này không tạo quyền; chỉ lặp kiểm tra vô ích hoặc gây tải lớn. Thường phải phân loại lỗi có thể retry, tổng deadline và giới hạn attempts.

### 7.3. Rate-limit, fallback và short-circuit

Một fallback có thể trả kết quả mà không `proceed`. Nếu một chính sách bắt buộc nằm bên trong, fallback ấy có thể bỏ qua guard. Một interceptor rate-limit nếu mất backend phải có quyết định đã được phê chuẩn: chặn, dùng quota cục bộ an toàn hoặc cho tiếp tục với rủi ro đã được chấp nhận bằng hợp đồng.

Không thể định nghĩa “tạm tắt toàn bộ chain khi một interceptor lỗi” rồi coi là đáp ứng đầy đủ AOP.

## 8. Mô hình trạng thái của dependency

Không chỉ có biến Boolean `available` trong sản xuất. Một dependency có thể trải qua:

```text
HEALTHY
   |
   v
SUSPECT (timeouts, increased latency)
   |
   v
UNAVAILABLE / CIRCUIT_OPEN
   |
  probes
   v
RECOVERING (half-open / controlled traffic)
   |
   v
HEALTHY
```

Đây là sơ đồ minh họa; circuit breaker có nhiều biến thể và không phải thành phần bắt buộc của mọi kiến trúc AOP.

### 8.1. Lỗi policy không được biến thành `ALLOW` qua timeout

Một API quyết định có thể trả `ALLOW`, `DENY` hoặc `UNKNOWN`. `UNKNOWN` thường là **không đủ bằng chứng**; nó không được chuyển thành `ALLOW` trong thao tác bắt buộc kiểm soát. Một cache có quyết định đã ký còn hiệu lực có thể là fallback được phê duyệt, nhưng cần kiểm tra expiry, revocation, scope, đối tượng, thao tác và rủi ro.

### 8.2. Timeout phải có ngân sách

Nếu target xử lý tối đa 100 ms nhưng mỗi aspect có timeout 1 giây, đường gọi thực tế có thể đắt gấp nhiều lần. Nên dùng **deadline tổng** và phân chia budget cho từng dependency.

Timeout không chứng minh bên kia chưa thực thi một thao tác. Đặc biệt trong audit và commit, phải giữ khả năng **trạng thái chưa biết** để đối soát.

### 8.3. Backpressure và hàng đợi

Một observer tùy chọn có thể đẩy sự kiện vào queue bounded. Khi queue đầy, hợp đồng cần nói rõ:

- Drop event nào, có giới hạn;
- Có block caller hay không và tối đa bao lâu;
- Có sampling và loại sự kiện không được drop không;
- Ai đo được số event mất;
- Có bảo vệ dữ liệu nhạy cảm trước khi xếp hàng không.

Không dùng queue vô hạn để “đảm bảo không mất log”; khi downstream hỏng lâu, queue có thể giữ request/payload trong bộ nhớ và gây lỗi hệ thống rộng hơn.

## 9. Ví dụ Java 17: ba chính sách, ba cách xử lý lỗi

Chương trình sau dùng **JDK dynamic proxy** để minh họa:

- **Observer tùy chọn**: khi sink không khả dụng, ghi `OBSERVE degraded` rồi cho phép đường gọi tiếp tục, không đổi return/throw của target.
- **Authorization bắt buộc**: khi không xác minh được quyền, chặn **trước target**; khi bị từ chối, trả lỗi rõ.
- **Admission audit bắt buộc trước effect**: nếu không ghi nhận được **ý định xử lý** trong mô hình, chặn **trước target**.
- **Target**: tạo một hiệu ứng minh họa có bộ đếm; một mã không hợp lệ làm target ném lỗi trước khi tăng counter.
- Các trạng thái dependency được thay đổi có chủ ý trong `main` để kiểm thử từng nhánh.

**Giới hạn an toàn:** `AUDIT intent` chỉ biểu diễn một sự kiện được ghi trước khi target xử lý; nó **không** tuyên bố effect đã commit. Ví dụ in ra console, không lưu audit bền vững và không có xác thực thật. Bộ kiểm tra token `"approved"` chỉ dùng trong chương trình học tập. Muốn có kiểm toán giao dịch thật phải dùng một cơ chế durable/transactional phù hợp; muốn kiểm soát quyền thật phải bảo vệ effect ở ranh giới đáng tin cậy.

Lưu thành tệp `CrosscutFailureContractDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class CrosscutFailureContractDemo {
    interface OrderPort {
        String submit(String principal, String id);
    }

    static final class OrderService implements OrderPort {
        int effects;

        @Override
        public String submit(String principal, String id) {
            if ("bad".equals(id)) {
                throw new IllegalArgumentException("invalid order");
            }
            effects++;
            System.out.println("EFFECT " + id + " #" + effects);
            return "OK:" + id;
        }
    }

    static final class Dependencies {
        boolean observerUp = true;
        boolean policyUp = true;
        boolean auditUp = true;
    }

    static final class OptionalObserver {
        void emit(Dependencies deps, String event) {
            try {
                if (!deps.observerUp) {
                    throw new IllegalStateException("observer offline");
                }
                System.out.println("OBSERVE " + event);
            } catch (IllegalStateException unavailable) {
                System.out.println("OBSERVE degraded");
            }
        }
    }

    static final class RequiredAuthorization {
        void check(Dependencies deps, String principal) {
            if (!deps.policyUp) {
                System.out.println("POLICY unavailable");
                throw new IllegalStateException("policy unavailable");
            }
            if (!"approved".equals(principal)) {
                System.out.println("POLICY denied");
                throw new SecurityException("denied");
            }
            System.out.println("POLICY allowed");
        }
    }

    static final class RequiredAdmissionAudit {
        void recordIntent(Dependencies deps, String id) {
            if (!deps.auditUp) {
                System.out.println("AUDIT unavailable");
                throw new IllegalStateException("audit unavailable");
            }
            System.out.println("AUDIT intent " + id);
        }
    }

    static Object callTarget(Method method, Object target, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapper) {
            throw wrapper.getCause();
        }
    }

    static OrderPort wrap(OrderPort target, Dependencies deps) {
        OptionalObserver observer = new OptionalObserver();
        RequiredAuthorization authorization = new RequiredAuthorization();
        RequiredAdmissionAudit audit = new RequiredAdmissionAudit();

        return (OrderPort) Proxy.newProxyInstance(
                OrderPort.class.getClassLoader(),
                new Class<?>[] { OrderPort.class },
                (proxy, method, args) -> {
                    String principal = (String) args[0];
                    String id = (String) args[1];

                    observer.emit(deps, "start:" + id);
                    authorization.check(deps, principal);
                    audit.recordIntent(deps, id);

                    try {
                        Object result = callTarget(method, target, args);
                        observer.emit(deps, "return:" + id);
                        return result;
                    } catch (Throwable targetFailure) {
                        observer.emit(deps, "throw:" + id);
                        throw targetFailure;
                    }
                });
    }

    static void attempt(OrderPort port, String principal, String id) {
        try {
            System.out.println("CALL " + port.submit(principal, id));
        } catch (RuntimeException error) {
            System.out.println("CALL_ERROR "
                    + error.getClass().getSimpleName() + ":"
                    + error.getMessage());
        }
    }

    public static void main(String[] args) {
        OrderService service = new OrderService();
        Dependencies deps = new Dependencies();
        OrderPort protectedPort = wrap(service, deps);

        System.out.println("CASE healthy");
        attempt(protectedPort, "approved", "A");

        deps.observerUp = false;
        System.out.println("CASE observer-down");
        attempt(protectedPort, "approved", "B");

        deps.policyUp = false;
        System.out.println("CASE policy-down");
        attempt(protectedPort, "approved", "C");

        deps.policyUp = true;
        deps.auditUp = false;
        System.out.println("CASE audit-down");
        attempt(protectedPort, "approved", "D");

        deps.auditUp = true;
        System.out.println("CASE denied");
        attempt(protectedPort, "guest", "E");

        System.out.println("CASE target-error");
        attempt(protectedPort, "approved", "bad");

        System.out.println("TOTAL_EFFECTS " + service.effects);
    }
}
```

Biên dịch và chạy với Java 17 trở lên:

```bash
javac -encoding UTF-8 CrosscutFailureContractDemo.java
java CrosscutFailureContractDemo
```

Kết quả stdout dự kiến:

```text
CASE healthy
OBSERVE start:A
POLICY allowed
AUDIT intent A
EFFECT A #1
OBSERVE return:A
CALL OK:A
CASE observer-down
OBSERVE degraded
POLICY allowed
AUDIT intent B
EFFECT B #2
OBSERVE degraded
CALL OK:B
CASE policy-down
OBSERVE degraded
POLICY unavailable
CALL_ERROR IllegalStateException:policy unavailable
CASE audit-down
OBSERVE degraded
POLICY allowed
AUDIT unavailable
CALL_ERROR IllegalStateException:audit unavailable
CASE denied
OBSERVE degraded
POLICY denied
CALL_ERROR SecurityException:denied
CASE target-error
OBSERVE degraded
POLICY allowed
AUDIT intent bad
OBSERVE degraded
CALL_ERROR IllegalArgumentException:invalid order
TOTAL_EFFECTS 2
```

### 9.1. Vì sao observer-down vẫn tạo hiệu ứng B?

`OptionalObserver.emit` chỉ bắt lỗi dự kiến của **chính observer**. Khi observer down, nó in `OBSERVE degraded`, sau đó Authorization và Audit vẫn hoạt động bình thường. Target tạo hiệu ứng B và caller nhận `OK:B`.

Điều này thể hiện một **hợp đồng cho phép suy giảm** được thiết kế trước. Trong chương trình thật, phải có kênh giám sát suy giảm độc lập hoặc counter drop có giới hạn; in `System.out` trong ví dụ chỉ để dễ quan sát.

### 9.2. Vì sao policy-down và audit-down không có effect?

Cả hai điều kiện bắt buộc nằm **trước** `callTarget`. Khi Authorization không khả dụng, nó ném `IllegalStateException("policy unavailable")`, không gọi tiếp. Khi audit không khả dụng, quá trình kiểm quyền đã qua nhưng audit intent **chưa được chấp nhận**, nên `recordIntent` ném lỗi và target vẫn chưa chạy.

Các yêu cầu C và D không tăng bộ đếm. Lỗi cụ thể được truyền ra caller; không trả `OK` giả.

### 9.3. Vì sao guest không được phép?

Với principal `guest`, policy có phản hồi xác định **DENY**, khác trạng thái policy không khả dụng. Nó ném `SecurityException("denied")`; không gọi audit intent theo thứ tự đã thiết kế và không gọi target.

Nếu chính sách yêu cầu ghi nhận cả những yêu cầu bị từ chối, phải có **denial audit** riêng đặt trước/ngoài guard và có hợp đồng fail-safe thích hợp. Không tráo `admission audit` với `denial audit` để suy rằng trường hợp từ chối không cần báo cáo.

### 9.4. Vì sao target-error vẫn có dòng `AUDIT intent bad`?

Audit ở ví dụ ghi **ý định được chấp nhận để thử xử lý**, không phải “giao dịch đã commit”. Sau đó target thấy ID `bad` và ném `IllegalArgumentException` trước khi tăng bộ đếm. Observer ghi nhánh `throw`; caller nhận lỗi gốc nhờ `InvocationTargetException.getCause()`.

Do đó `TOTAL_EFFECTS 2`, chỉ hai yêu cầu A và B đã thực hiện effect. Không nên dùng một sự kiện intent làm căn cứ kết luận nghiệp vụ thành công.

### 9.5. Điều gì chưa được ví dụ giải quyết?

- Observer thực tế có thể lỗi vì nguyên nhân bất ngờ, và không phải mọi `IllegalStateException` nên tự được nuốt. Bản học tập dùng đúng một loại lỗi để đơn giản; sản xuất cần phân loại lỗi cụ thể từ API sink.
- Không có timeout hoặc async; trạng thái down được đổi tuần tự giữa những lời gọi.
- Không có durable audit transaction. In ra console không tạo nghĩa vụ lưu trữ đáng tin.
- Không có bảo vệ ranh giới khi caller giữ đối tượng `OrderService` gốc và gọi trực tiếp; proxy có thể bị bypass.
- Không có logic retry hoặc request idempotency; cố tình tránh tạo một ví dụ gây nhân đôi effect.
- Không có kiểm tra overload vì interface chỉ có một phương thức `submit`.
- Cờ trạng thái Boolean không an toàn để thay đổi concurrent ngoài hợp đồng thread-safety; chương trình chỉ dùng một thread.
- Observer sau return trong ví dụ chỉ báo hoàn tất của phương thức đồng bộ; với `CompletableFuture`, return future không phải completion của tác vụ.

## 10. Không nuốt ngoại lệ của target khi xử lý lỗi telemetry

Một kiểu viết sai thường gặp:

```java
try {
    observeBefore();
    return invocation.proceed();
} catch (Exception any) {
    return fallback;
}
```

Đây là **minh họa**, với tên và biến chưa định nghĩa. `catch` bao cả `proceed`, nên nó có thể bắt lỗi của **nghiệp vụ hoặc guard bên trong**, không chỉ lỗi của `observeBefore`. Nếu tiếp tục trả fallback, hành vi bảo mật và hợp đồng ngoại lệ có thể thay đổi.

Một cách phân tách ý tưởng tốt hơn:

```text
try:
    emitOptionalBefore()
catch EXPECTED_OBSERVER_FAILURE:
    markObserverDegraded()

try:
    result = proceed()  // không nuốt lỗi target
catch targetFailure:
    try:
        emitOptionalFailure(targetFailure)
    catch EXPECTED_OBSERVER_FAILURE:
        markObserverDegraded()
    rethrow SAME targetFailure

try:
    emitOptionalReturn(result)
catch EXPECTED_OBSERVER_FAILURE:
    markObserverDegraded()

return result
```

Đây là **giả mã mô hình điều khiển**, không phải một API AOP chuẩn. Phần quan trọng là phạm vi `catch` dành cho lỗi của observer **không ôm cả `proceed`**.

Với chức năng bắt buộc, không thay `markObserverDegraded()` bằng “cho qua”: phải áp đúng hợp đồng của chức năng ấy.

### 10.1. Lỗi logging có thể che lỗi nghiệp vụ

Nếu một advice cố ghi log một exception và logger lại lỗi, kết quả sẽ tùy cách xử lý của advice. Với observer chỉ hỗ trợ, nên bảo toàn nguyên trạng lỗi nghiệp vụ, ghi sự cố quan sát theo đường đã định hoặc bổ sung thông tin chẩn đoán mà không che nguyên nhân gốc.

Với tài nguyên và giao dịch bắt buộc, có thể cần dùng **suppressed exceptions** hoặc một đối tượng kết quả mô tả hai lỗi. Không có quy tắc “luôn nuốt mọi cleanup error” chung cho mọi chính sách.

## 11. Khi lỗi xảy ra sau commit: hợp đồng trạng thái chưa chắc chắn

Một bài toán khó:

```text
1. Receive request X
2. Target commits effect X
3. Audit/response transport fails
4. Caller sees timeout or error
5. Caller retries X
```

Nếu không có bảo vệ, bước 5 có thể tạo effect thứ hai. Đây là lỗi kiến trúc thường bị nhầm là “chức năng cắt ngang down nên retry”.

### 11.1. Ba mức kết quả cần phân biệt

- **REJECTED BEFORE EFFECT:** biết chắc hành động không được thực hiện qua đường đó.
- **CONFIRMED COMMITTED:** có bằng chứng nghiệp vụ đã hoàn tất theo mức cam kết của hệ thống.
- **IN_DOUBT:** không có đủ bằng chứng để khẳng định đã hoặc chưa tạo effect; cần đối soát.

Không đồng nhất `IN_DOUBT` với `REJECTED`. Cũng không trả `SUCCESS` giả chỉ để tránh người gọi retry.

### 11.2. Idempotency và đối soát

Với thao tác có hiệu ứng lâu dài, có thể cần:

- **Idempotency key** gắn với logical request, ổn định qua các lần thử;
- Ràng buộc key với nội dung yêu cầu phù hợp;
- Một nơi tin cậy ghi nhận kết quả đã cam kết;
- Khả năng trả lại kết quả cũ thay vì tạo effect mới;
- Quy trình phát hiện và giải quyết trạng thái `IN_DOUBT`;
- TTL/lifetime key đủ cho cửa sổ retry theo hợp đồng;
- Phương án xử lý concurrent duplicates.

Không phải mọi thao tác đều cần tầng idempotency như nhau. Nhưng khi retry sau một lỗi transport, cần đặc biệt hỏi **side effect có thể đã xảy ra chưa?**.

### 11.3. Durable outbox

Nếu nghiệp vụ cần **đồng thời ghi trạng thái và bảo đảm sau này có event/audit về sự kiện đó**, một phương án là ghi **outbox record trong cùng ranh giới transaction đáng tin** với cập nhật nghiệp vụ, rồi một worker phát đi sau. Như vậy “backend audit/transport down” không cần làm mất thông tin đã được chấp nhận bền vững.

Đây là **mẫu kiến trúc**, không phải khả năng mặc định của AOP hay bảo đảm exactly-once delivery. Consumer và phát lại thường vẫn cần idempotency.

Nếu ngay nơi lưu outbox bắt buộc cũng không ghi được, **không thể tuyên bố đã chấp nhận yêu cầu bền vững**. Cần chặn hoặc trả trạng thái đúng hợp đồng.

## 12. Retry chỉ cho những lỗi đủ điều kiện

Một retry aspect phải phân biệt:

| Loại lỗi | Có retry tự động không? | Lưu ý |
| --- | --- | --- |
| Logger tùy chọn down | Thường không retry toàn nghiệp vụ | Giảm quan sát hoặc retry telemetry độc lập |
| Kiểm tra quyền `DENY` | Không | Không biến từ chối thành tải lặp |
| Authorization `UNKNOWN` | Có thể thử lại **quyết định kiểm tra** trong deadline, không chạy target | Không cho phép khi chưa xác minh |
| Validation input sai | Không | Người gọi cần sửa dữ liệu |
| Network transient trước khi gửi effect | Có thể nếu chứng minh chưa tạo effect | Hợp đồng transport quan trọng |
| Timeout sau gửi lệnh có effect | Không retry mù | Idempotency và đối soát |
| Audit sink đầy | Có thể backoff riêng nếu đã có durable buffer | Không queue vô hạn |
| Transaction conflict | Có thể retry nếu toàn thao tác retry-safe | Scope transaction và side effect |
| JVM `Error` nghiêm trọng | Không bắt rồi coi là hỏng logger | Giữ ngữ nghĩa lỗi phù hợp |

### 12.1. Giới hạn retry

Một chính sách retry phải có số attempts tối đa, deadline tổng, delay/backoff hợp lý, giới hạn tải và điều kiện kết thúc. Nếu nhiều aspect lồng nhau đều retry, số lần target chạy có thể là **tích các mức retry**, lớn hơn dự kiến.

Ví dụ `RetryOuter(RetryInner(Target))` với mỗi tầng thử hai lần có thể dẫn tới **tối đa bốn target attempts** trong những đường lỗi phù hợp. Không kết luận số lần theo tên aspect mà phải kiểm tra cấu trúc `proceed` thực tế.

### 12.2. Circuit breaker không thay authorization

Circuit breaker có thể giảm tải tới một dependency đang down, nhưng nếu dependency là bộ kiểm tra quyền bắt buộc, trạng thái circuit open phải dẫn tới **từ chối hoặc fallback kiểm chứng hợp lệ**, không có đường tự động cho phép.

Một circuit breaker không tự đảm bảo chính sách bảo mật đúng. Nó quản lý **khả năng gọi dependency**, không sinh quyết định quyền đáng tin.

## 13. Startup, runtime và phục hồi

Chức năng cắt ngang có thể không khả dụng ở nhiều giai đoạn:

### 13.1. Startup

Nếu hệ thống **bắt buộc** kiểm tra quyền hoặc ghi audit trước khi phục vụ, cấu hình không hợp lệ có thể là lý do **không khởi động ở chế độ sẵn sàng phục vụ**. Cơ chế readiness/health phải phản ánh điều kiện thực.

Một ứng dụng vẫn có thể khởi chạy tiến trình để tiếp tục phục hồi, nhưng không nên tự công bố `READY` cho những thao tác vi phạm hợp đồng.

### 13.2. Khi đang chạy

Một optional observer down có thể làm hệ chuyển sang trạng thái `DEGRADED`; một guard bắt buộc down có thể khiến một nhóm endpoint trả unavailable hoặc chuyển read-only theo chính sách.

**Mức độ suy giảm có thể theo thao tác**, không nhất thiết một cờ cho toàn ứng dụng. Ví dụ đọc dữ liệu công khai có thể tiếp tục trong khi thao tác chuyển tiền cần xác nhận quyền bắt buộc bị khóa.

### 13.3. Khi phục hồi

Khi dependency hoạt động trở lại, cần kiểm tra:

- Kết quả health probe có đủ tin cậy chưa?
- Có backlog event cần gửi lại không?
- Các yêu cầu `IN_DOUBT` được đối soát thế nào?
- Quy tắc cache quyền stale có được vô hiệu đúng chưa?
- Có đang gây burst tải lớn vì đồng loạt retry không?
- Những trạng thái local/context cũ có cần đóng và khởi tạo lại không?

Việc một socket kết nối lại không chứng minh toàn bộ dữ liệu đã nhất quán. Phục hồi cần **định nghĩa điều kiện kết thúc**, không chỉ một nút “Enable aspect”.

## 14. Quan sát suy giảm mà không phụ thuộc hoàn toàn vào chính tính năng hỏng

Một nghịch lý: nếu bộ thu metrics down, gửi metric “metrics down” tới đúng bộ thu ấy có thể không đến nơi. Vì vậy trạng thái degraded cần có một hoặc nhiều đường phù hợp khác:

- Health/readiness nội bộ;
- Counter/bounded state trong tiến trình;
- Structured diagnostic tối thiểu tới kênh khác;
- Alert dựa trên heartbeat/absence detection ở phía giám sát;
- Status endpoint được kiểm soát quyền;
- Bản ghi theo lô bền vững khi hợp đồng yêu cầu.

Không nên spam stack trace ở mỗi request khi một dependency down kéo dài. Việc ghi log lỗi chính nó có thể tạo tải, rò dữ liệu và tăng retention. Dùng sampling, rate limit và mốc chuyển trạng thái nếu phù hợp.

### 14.1. Ghi mất dữ liệu quan sát thành số liệu đúng nghĩa

Khi cho phép drop một event, có thể ghi số `droppedSamples` ở một counter giới hạn và xuất tổng hợp khi phục hồi. Cần phân biệt:

- **Request processed:** nghiệp vụ đã đi qua đường hợp lệ;
- **Advice attempted:** đã thử ghi telemetry;
- **Telemetry delivered:** có xác nhận nhận dữ liệu;
- **Telemetry dropped:** bị loại do budget hoặc lỗi;
- **Business effect committed:** nghiệp vụ thực sự có effect.

Không dùng một chỉ số làm đại diện cho tất cả. Một lần advice được gọi có thể không tạo thành một bản ghi đã nhận.

## 15. Lỗi khi thay đổi hợp đồng ngoại lệ

Một aspect quanh có thể xử lý `Throwable` và làm thay đổi:

- Loại ngoại lệ;
- Chuỗi cause;
- Suppressed exceptions;
- Thời điểm caller nhìn thấy lỗi;
- Quy tắc retry của caller;
- Mức độ biết chắc về effect.

Ví dụ nếu target ném `IllegalArgumentException` và observer down, **observer không được đổi kết quả thành `OK`** khi hợp đồng chỉ là quan sát. Nếu bắt buộc chuyển đổi ngoại lệ, hãy định nghĩa rõ kiểu kết quả và giữ nguyên thông tin cause/biên trạng thái.

### 15.1. Checked exception qua JDK dynamic proxy

JDK dynamic proxy có hợp đồng về những ngoại lệ checked được phép theo chữ ký phương thức giao diện. Một checked exception không nằm trong khai báo hợp lệ có thể bị bọc trong `UndeclaredThrowableException`. Khi dạy hoặc viết advice, không được giả định ném tùy ý mọi `Throwable` sẽ tới caller dưới cùng kiểu.

Ví dụ runnable ở trên dùng những ngoại lệ unchecked và bóc lỗi reflection để giữ kết quả minh bạch.

### 15.2. `void`, `null` và primitive

Một interceptor fail-open không thể mặc định trả `null` cho mọi phương thức. Nếu method trả `int`, proxy có thể cần unbox và việc trả null gây `NullPointerException`. Nếu method trả `void`, một fallback “giả thành công” có thể che thao tác không được thực hiện. Cần xem chính xác chữ ký và hợp đồng trước khi trả fallback.

## 16. Coverage: AOP không tự bảo đảm chính sách có hiệu lực ở mọi đường

Một JDK dynamic proxy chỉ can thiệp những lời gọi đi **qua proxy và giao diện được đại diện**. Lời gọi trực tiếp target, self-invocation hay đường ở lớp không được proxy cung cấp có thể bypass advice.

Một bộ weaver bytecode có phạm vi khác nhưng vẫn phải kiểm tra **mã nào được weave, loại join point nào được hỗ trợ và artifact nào đang chạy**. Không thể dựa trên việc có một aspect Authorization để tuyên bố mọi effect đã được bảo vệ.

**Với chính sách bắt buộc, hãy đặt hoặc xác minh ranh giới kiểm soát đáng tin cậy**, kiểm thử các đường bypass, và bảo đảm fallback không tạo thêm đường vào không được phép.

Một failure mode riêng là **lỗi khởi tạo proxy/interceptor** khiến ứng dụng âm thầm trả đối tượng gốc. Với chức năng tùy chọn, có thể có chế độ degraded hợp lệ; với guard bắt buộc, đó thường phải được coi là **không sẵn sàng phục vụ** chứ không âm thầm “gỡ aspect cho chạy”.

## 17. Ma trận kiểm thử hợp đồng khi chức năng cắt ngang hỏng

| Kịch bản | Kỳ vọng cần kiểm tra |
| --- | --- |
| Observer khỏe | Event được thử/ghi, target chạy đúng một lần |
| Observer down | Nếu optional: target vẫn chạy, return/throw giữ nguyên |
| Logger của observer ném lỗi | Không nuốt lỗi target hoặc guard |
| Authorization `ALLOW` | Có thể tiếp tục tới các điều kiện bắt buộc kế tiếp |
| Authorization `DENY` | Không gọi target, không trả success giả |
| Authorization `UNKNOWN`/timeout | Không mặc định cho phép |
| Audit intent bắt buộc down trước target | Không tạo effect qua đường đó |
| Audit “committed” down sau effect | Không retry mù; trạng thái/đối soát đúng |
| Target ném validation error | Lỗi gốc được truyền, không bị coi là telemetry lỗi |
| Cleanup của aspect lỗi | Xử lý theo policy, không vô tình che lỗi gốc |
| Cache down | Chỉ fallback về target sau guard bắt buộc |
| Cache hit | Guard không bị short-circuit trái hợp đồng |
| Retry nhiều tầng | Tổng số attempts có giới hạn và quan sát được |
| Circuit breaker open | Quyết định cho phép/không cho phép vẫn đúng |
| Dependency phục hồi | Bỏ degraded theo điều kiện rõ, backlog hợp lệ được giải quyết |
| Queue telemetry đầy | Không tăng bộ nhớ vô hạn |
| Lời gọi bypass proxy | Phát hiện/loại bỏ đường không được guard |
| Advice chưa được weave | Không tuyên bố policy bắt buộc đã áp dụng |
| Request đồng thời | Không lẫn authorization state hoặc context |
| Lời gọi trả future | Tách synchronous return với async completion |
| Quy tắc idempotency | Retry không tạo effect trùng ngoài hợp đồng |

### 17.1. Kiểm thử lỗi ở đúng mốc

Không đủ để chỉ mock “dependency throws”. Cần inject lỗi:

1. **Trước khi giao dịch bắt đầu**;
2. **Trước khi target được phép chạy**;
3. **Ngay trong target trước effect**;
4. **Ngay sau effect nhưng trước response**;
5. **Trong after advice**;
6. **Trong finally/close**;
7. **Trong consumer/background task**;
8. **Trong quá trình khởi tạo và phục hồi**.

Các mốc có thể cho hành vi đúng **khác nhau**, dù cùng một tên exception.

### 17.2. Assert về tác dụng phụ

Một test chỉ assert caller nhận `IllegalStateException` có thể đạt dù target đã tăng counter trước đó. Phải assert **effect count, dữ liệu được cam kết và số lần gọi target**, ngoài status/exception.

Trong ví dụ Java 17, dòng `TOTAL_EFFECTS 2` là một tín hiệu hữu ích: các đường policy-down, audit-down, denied và target-error đều không tạo effect trong mô hình. Ở hệ thật, cần kiểm tra trạng thái persisted, không chỉ counter trong bộ nhớ.

## 18. Những sai lầm phổ biến

| Sai lầm | Hậu quả | Cách tránh |
| --- | --- | --- |
| “Bất kỳ advice nào down đều bỏ qua” | Bypass quyền/audit bắt buộc | Phân loại chính sách, fail closed khi cần |
| Catch tất cả `Throwable` quanh `proceed` | Nuốt target errors và lỗi nghiêm trọng | Thu hẹp phạm vi catch cho nguồn lỗi |
| Observer tự tạo `NullPointerException` | Lỗi phụ trợ che nghiệp vụ | Kiểm tra binding, chính sách lỗi riêng |
| Trả `null` làm fallback chung | Sai primitive/void, giả success | Thiết kế kết quả theo chữ ký |
| Policy timeout được hiểu là ALLOW | Vượt kiểm soát quyền | UNKNOWN khác ALLOW |
| Audit intent nhầm với committed effect | Báo cáo sai trạng thái | Tách admitted, committed, in-doubt |
| Retry toàn giao dịch khi after-audit fail | Dễ tạo effect trùng | Outbox/idempotency/đối soát |
| Queue telemetry không giới hạn | Cạn heap khi downstream down | Buffer bounded/drop/backpressure |
| Health check đơn giản coi là dữ liệu đúng | Bỏ sót trạng thái lỗi/stale | Probe và hợp đồng nhất quán |
| Cache permission stale được dùng không kiểm soát | Quyết định quyền lỗi thời | TTL, revocation, verified fallback |
| Log degraded bằng kênh đang hỏng | Không có tín hiệu hữu ích | Kênh giám sát độc lập |
| Đảo Cache/Authorization | Short circuit bỏ qua guard | Precedence/coverage test |
| Proxy down thì trả target gốc | Bypass chính sách bắt buộc | Fail startup/readiness hoặc trusted route |
| Tự retry vô hạn | Thêm tải và giữ tài nguyên | Deadline, attempt budget, circuit breaker |
| Dùng static `currentRequest` cho state | Lẫn dữ liệu đa luồng, giữ object | Context theo invocation/lifecycle |
| Dùng status HTTP lỗi để suy chưa commit | Retry nhầm sau hiệu ứng | Trạng thái in-doubt và idempotency |
| Không test startup/recovery | Cấu hình sai vẫn phục vụ | Readiness, fault injection, rollback |

## 19. Checklist áp dụng vào kiến trúc AOP

1. Liệt kê những aspect liên quan và loại join point thực sự được cơ chế hỗ trợ.
2. Phân loại mỗi chính sách là optional, mandatory, optimization hay resource lifecycle; xem theo từng thao tác.
3. Định nghĩa lỗi riêng của từng dependency và phân biệt **DOWN, DENY, UNKNOWN, INVALID, IN_DOUBT**.
4. Ghi rõ có được `proceed` khi policy không khả dụng không; không để một advice tùy ý đổi quyết định.
5. Xác định kết quả caller: success, denied, unavailable, pending hay trạng thái chưa chắc chắn.
6. Đặt guard bắt buộc trước mọi đường effect cần kiểm soát; xác minh các đường bypass.
7. Tách try/catch lỗi observer khỏi vùng `proceed` để không che lỗi target.
8. Quy định thứ tự với cache, rate-limit, retry, transaction, audit và fallback.
9. Xác định mốc commit và bảo vệ retry bằng idempotency/đối soát khi cần.
10. Giới hạn timeout, queue, retry, trạng thái cache và tài nguyên; chuẩn bị chế độ degrade rõ.
11. Có health/readiness, alert và số liệu dropped events độc lập với dependency đang hỏng.
12. Kiểm thử fault injection tại trước/giữa/sau effect, cả return, throw, cleanup, startup, recovery.
13. Kiểm chứng action và effect thực, không chỉ exception/status, bằng artifact và đường runtime thật.
14. Viết runbook cho quá trình khôi phục, giải quyết backlog và trạng thái `IN_DOUBT`.

## 20. Bài tập vận dụng có đáp án

### Bài 1 — Observer phụ trợ

Một aspect ghi metrics gặp lỗi backend. Chính sách metrics được xác định là tùy chọn và yêu cầu không làm đổi kết quả nghiệp vụ. Hành vi phù hợp?

**Đáp án:** Bắt riêng lỗi đã được xác định của exporter, giới hạn thời gian chờ, đánh dấu degraded/drop theo ngân sách rồi tiếp tục đường nghiệp vụ. Không bao `proceed` bằng catch rộng có thể nuốt target errors.

### Bài 2 — Kiểm tra quyền không xác định

Authorization trả `UNKNOWN` vì timeout. Có thể mặc định cho phép để giữ uptime không?

**Đáp án:** Không, nếu chính sách xác minh quyền là bắt buộc. Cần chặn hoặc dùng một fallback kiểm chứng đáng tin được quy định trước; UNKNOWN không phải ALLOW.

### Bài 3 — Audit trước effect

Một hợp đồng bắt buộc có admission audit thành công trước khi target chạy. Audit backend down, có thể cứ chạy target và ghi lại sau bằng memory list không giới hạn?

**Đáp án:** Không. Memory list không giới hạn và không bền vững không cung cấp cam kết tương đương. Phải có durable fallback theo hợp đồng hoặc từ chối trước effect.

### Bài 4 — Lỗi sau commit

Target đã lưu một khoản thanh toán, nhưng after-advice gửi audit bị timeout. Caller thấy lỗi. Có được retry toàn bộ thanh toán ngay không?

**Đáp án:** Không retry mù. Effect có thể đã cam kết. Cần idempotency key, ranh giới giao dịch/outbox hoặc truy vấn/đối soát trước khi quyết định thử lại.

### Bài 5 — Lỗi từ target bị nuốt

Interceptor optional `catch(Exception)` bao cả `proceed` rồi trả `null`. Target ném `IllegalArgumentException`. Điều gì sai?

**Đáp án:** Interceptor đã đổi hợp đồng lỗi nghiệp vụ. Nó chỉ nên xử lý lỗi của chính chức năng optional trong phạm vi catch hẹp, còn lỗi target phải được truyền đúng.

### Bài 6 — Cache và guard

Cache nằm ngoài authorization, có cache hit thì không gọi tiếp. Tại sao đây có thể là lỗi?

**Đáp án:** Authorization bên trong có thể không được thực hiện. Nếu dữ liệu cần kiểm quyền ở mọi lần, phải đặt guard ở ranh giới phù hợp và xây cache theo scope quyền đúng.

### Bài 7 — Circuit breaker

Một circuit breaker mở vì policy service down. Nó có quyết định người dùng được phép không?

**Đáp án:** Không. Circuit breaker chỉ quyết định có nên gọi dependency. Khi không gọi được policy bắt buộc, phải từ chối hoặc dùng fallback có cùng cam kết an toàn.

### Bài 8 — Output ví dụ

Trong chương trình Java 17, vì sao `TOTAL_EFFECTS` bằng 2 dù có sáu lượt `attempt`?

**Đáp án:** Chỉ A và B được phép qua các điều kiện bắt buộc và không lỗi ở target. C/D bị chặn vì policy/audit không khả dụng; E bị từ chối; `bad` ném lỗi trước khi tăng counter. Observer-down không ngăn B.

### Bài 9 — Future

Một phương thức trả `CompletableFuture` bình thường, after-returning audit ghi “đã hoàn thành”. Tại sao có thể sai?

**Đáp án:** Return của phương thức chỉ là trả future; tác vụ có thể chưa chạy xong hoặc thất bại sau đó. Audit committed phải gắn với completion/commit thực theo hợp đồng.

### Bài 10 — Khôi phục

Observer phục hồi sau 30 phút. Các event đã bị drop trong lúc down có thể được tuyên bố đã gửi đầy đủ không?

**Đáp án:** Không. Nếu hợp đồng cho phép drop, phải ghi đúng số liệu mất mẫu và không tuyên bố dữ liệu đầy đủ. Nếu hợp đồng yêu cầu durable delivery, cần buffer bền vững và cơ chế phát lại/đối soát.

## 21. Ghi nhớ

- **Chính sách cắt ngang nào không khả dụng phải được xử lý theo hợp đồng riêng**, không áp một catch/fallback chung cho mọi aspect.
- **Fail-open** chỉ hợp lệ khi chính sách cho phép; **fail-closed** bảo vệ những bất biến bắt buộc khi thiếu thông tin đáng tin.
- **Lỗi từ observer khác lỗi từ target**, và timeout/UNKNOWN khác DENY, khác trạng thái đã commit hay chưa biết.
- Xác định **thời điểm lỗi so với effect** trước khi quyết định trả lỗi, retry hoặc báo success.
- **Thứ tự interceptor và short-circuit** có thể khiến một chính sách bắt buộc bị bỏ qua; phải kiểm tra coverage cả đường proxy/weaving.
- Observer tùy chọn nên hạn chế tác động lên return/exception, còn guard/audit bắt buộc phải có fallback đủ tin cậy hoặc chặn trước tác dụng phụ.
- Lỗi **sau effect** đòi hợp đồng idempotency, outbox hoặc đối soát khi phù hợp; không retry toàn nghiệp vụ mù.
- Độ sẵn sàng, queue/backpressure, health, phục hồi và test fault injection là một phần **hợp đồng vận hành của AOP**, không phải phần phụ của vài dòng advice.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-exception-contracts-009">Ghi nhận hợp đồng của aspect cho bên sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Ghi nhận hợp đồng của aspect cho bên sử dụng trong AOP

## 1. Vì sao một aspect cần hợp đồng dành cho người sử dụng?

Trong **Aspect-Oriented Programming (AOP)**, một **aspect** đóng gói logic xuyên suốt như kiểm toán, xác thực quyền, đo thời gian, kiểm tra bất biến, retry, ghi log hoặc dịch ngoại lệ. **Pointcut** xác định những **join point** thuộc phạm vi cơ chế AOP; **advice** thực hiện hành vi tại các điểm được chọn.

Nếu một thư viện chứa method `publish(String,String)`, người gọi thường đọc chữ ký, Javadoc và kiểu ngoại lệ để biết mình có thể mong đợi điều gì. Nhưng khi AOP tham gia, **hành vi mà người gọi quan sát không chỉ do thân phương thức quyết định**:

- Một before advice có thể từ chối input trước khi thân method chạy.
- Một around advice có thể không gọi tiếp, thay tham số, trả fallback hoặc thực hiện retry.
- Một after-returning advice có thể kiểm tra, ghi audit hoặc vô tình gây lỗi sau khi method gốc đã thành công.
- Một after-throwing advice có thể ghi nhận, chuyển đổi hoặc lan truyền lỗi.
- Nhiều aspect có thể cùng tham gia, tạo thứ tự tác động quan trọng.
- Một lời gọi đi trực tiếp vào target có thể bỏ qua proxy-based advice.

Vì vậy, bên sử dụng cần **hợp đồng của hành vi đã kết hợp** (*composed contract*), không chỉ hợp đồng của mã nghiệp vụ ban đầu. “Ghi nhận hợp đồng” nghĩa là mô tả công khai và có thể kiểm chứng: **khi nào aspect có hiệu lực, người dùng phải đáp ứng gì, aspect bảo đảm gì, nó có thể thay đổi điều gì và những giới hạn nào vẫn tồn tại**.

Bài này hướng dẫn xây tài liệu hợp đồng cho AOP **nói chung, từ cơ bản đến nâng cao**; không giả định một framework, compiler, proxy manager hoặc DSL cụ thể.

## 2. Phân biệt hợp đồng nghiệp vụ, hợp đồng aspect và hợp đồng cuối cùng

Có thể xem hệ thống gồm ba lớp:

```text
Caller
  |
  v
Public API + configured aspects
  |
  v
Business operation
```

**Hợp đồng nghiệp vụ (target contract)** mô tả việc `publish` cần đầu vào nào, khi nào ghi dữ liệu và trả giá trị gì.

**Hợp đồng aspect (aspect contract)** mô tả logic được chèn: chính sách chọn điểm, kiểm tra, ghi log, quyền chuyển lỗi, thứ tự, chi phí và yêu cầu về ngữ cảnh.

**Hợp đồng hợp thành (composed/public contract)** mô tả điều caller **thực sự nhìn thấy** sau khi các aspect được áp dụng. Khi các lớp thay input/output hoặc exception, hợp đồng này có thể khác hợp đồng target.

Ví dụ:

```text
Target.publish(id, body):
    Khi thành công, trả receipt:id.

ValidationAspect:
    Từ chối id rỗng trước khi gọi target.

AuditAspect:
    Ghi event ở đường thành công hoặc lỗi.

Public publish contract:
    Nếu id rỗng, ném IllegalArgumentException,
    không thực hiện target.
    Nếu hợp lệ, áp dụng audit ở phạm vi đã khai báo.
    Kết quả thành công là receipt:id.
```

Đoạn trên là mô tả hợp đồng, không phải cú pháp một ngôn ngữ pointcut cụ thể.

### 2.1. Hợp đồng không phải tên advice

Một dòng như “dùng before advice để validate” **không đủ** cho consumer. Nó chưa trả lời method nào được áp dụng, lỗi gì được ném, phương thức gốc có chạy không và behavior khác nhau ra sao giữa môi trường sử dụng proxy và gọi trực tiếp.

### 2.2. Tài liệu aspect cần có hai đối tượng đọc

- **Người gọi/API consumer:** muốn biết input hợp lệ, output, lỗi, side effect, độ trễ và tính nhất quán.
- **Người cấu hình/vận hành:** muốn biết phạm vi weaving, precedence, configuration, đường có thể bypass, cách debug và hậu quả khi disable aspect.

Hai nhóm thông tin nên liên kết nhưng có thể được trình bày trong các mục riêng để tránh bắt consumer hiểu mọi chi tiết implementation.

## 3. Những mục tối thiểu trong hợp đồng aspect

Một bản mô tả hữu ích nên trả lời:

| Nhóm | Câu hỏi cần ghi nhận |
| --- | --- |
| Danh tính | Aspect tên gì, phiên bản nào, chủ sở hữu bảo trì là ai? |
| Mục đích | Nó bảo vệ/quan sát/chuyển đổi hành vi nào? |
| Phạm vi | Những loại join point, class/method, package, metadata nào được chọn? |
| Ranh giới | Compile/load-time weaving, proxy interception hay hình thức khác? |
| Tiền điều kiện | Caller phải cung cấp dữ liệu/ngữ cảnh gì trước lời gọi? |
| Bảo đảm thành công | Khi trả bình thường, kết quả và trạng thái nào được cam kết? |
| Bảo đảm đường lỗi | Loại lỗi nào có thể xuất hiện, được giữ/chuyển đổi thế nào? |
| Side effect | Advice có log, gửi audit, cập nhật cache, retry hay gọi mạng không? |
| Thứ tự | Precedence với các aspect khác; lớp ngoài/trong? |
| Continuation | Around có thể không `proceed`, gọi nhiều lần hay thay args/result không? |
| Bất biến | Tính chất hệ thống nào phải được giữ sau những ranh giới cụ thể? |
| Giới hạn | Lời gọi nào có thể bypass; final/private/static, internal calls, async? |
| Quan sát | Log/event/metric/tracing được sinh ra; quy tắc dữ liệu nhạy cảm? |
| Vận hành | Điều kiện bật/tắt, chi phí, fallback khi phụ trợ lỗi, rollout? |
| Phiên bản | Thay đổi chính sách có ảnh hưởng hợp đồng đã công bố không? |
| Kiểm chứng | Positive/negative/exception tests nào chứng minh các cam kết? |

Không cần đưa mọi chi tiết bytecode vào tài liệu dành cho consumer. Nhưng những giới hạn có thể **thay đổi điều consumer quan sát** phải được ghi nhận rõ.

## 4. Ghi phạm vi áp dụng bằng ngôn ngữ có thể kiểm chứng

Một câu như “aspect tự động áp dụng cho các service” thường quá mơ hồ. Cần định nghĩa:

- **Join-point kind:** method call, method execution, constructor, field access, hoặc loại được engine hỗ trợ.
- **Selection:** chữ ký, kiểu khai báo, modifier, annotation, pattern hoặc metadata.
- **Inclusion:** vùng class/module hoặc tập operation cụ thể.
- **Exclusion:** các class, method hoặc trường hợp không áp dụng.
- **Runtime guards:** tham số, target type, control flow, identity/ngữ cảnh.
- **Coverage boundary:** mã nào đã được weave, instance nào phải đi qua proxy.

Ví dụ mô tả **trung lập công cụ**:

```text
Aspect: DocumentAudit
Scope:
  - method execution của Publication.publish(String,String)
  - chỉ khi invocation được thực thi qua điểm can thiệp đã đăng ký
  - không áp dụng cho Publication.publishedCount()
Runtime condition:
  - áp dụng cho mọi publish invocation đi qua boundary
Non-coverage:
  - caller gọi trực tiếp raw target sẽ không đi qua proxy audit
```

Một số cơ chế weaving trực tiếp vào method body có thể bao phủ các object được tạo bằng `new`, trong khi proxy delegation chỉ nhìn thấy lời gọi qua proxy. **Không được biến giới hạn của một implementation thành quy tắc chung cho tất cả AOP.**

### 4.1. “Các method được chọn” khác “các invocation thực sự đi qua”

Một class có chữ ký khớp pointcut không tự chứng minh advice chạy. Có thể lớp không nằm trong weaving scope, instance được tạo bên ngoài cơ chế proxy, điều kiện runtime sai hoặc một advice ưu tiên cao hơn chặn `proceed`.

Hợp đồng cần nêu cả **quy tắc matching** và **đường thực thi được bảo đảm**.

### 4.2. Lời gọi nội bộ và self-invocation

Trong mẫu **interface proxy ủy quyền vào target riêng**, `target.outer()` gọi `this.inner()` thường không đi qua proxy, dù `inner()` tự nó là method hợp lệ theo pointcut. Subclass proxy hoặc bytecode weaving có thể có hành vi khác.

Khi tài liệu nói “áp dụng cho mọi invocation”, phải kiểm chứng được self-invocation và các đường khác; nếu không, hãy giới hạn lời cam kết.

## 5. Ghi điều kiện trước của aspect

**Precondition** xác định điều bên gọi phải thỏa để nhận được bảo đảm thành công. Ví dụ:

```text
publish(id, content)
Precondition:
  id != null
  id.isBlank() == false
  content != null
  content.isBlank() == false
```

Nếu aspect được thiết kế để kiểm tra precondition, tài liệu phải nói rõ:

1. Việc kiểm tra nằm **trước hay sau** chuẩn hóa dữ liệu.
2. Loại lỗi/violation khi điều kiện sai.
3. Target có bị gọi hay không khi bị từ chối.
4. Có ghi audit cho request bị từ chối không.
5. Có thông tin nhạy cảm bị đưa vào thông báo lỗi/log không.
6. Caller có được phép sửa input và thử lại không.
7. Điều kiện có áp dụng nhất quán trên mọi entry point không.

### 5.1. Không tự siết chặt public contract

Giả sử public API trước đây cho phép id chứa khoảng trắng rồi tự trim, nay aspect mới từ chối mọi chuỗi có khoảng trắng. Consumer hợp lệ trước đây có thể bị hỏng.

Điều kiện trước quá mạnh có thể vi phạm nguyên tắc thay thế (*behavioral substitutability*) và là **thay đổi hợp đồng API**, dù chữ ký method không đổi.

### 5.2. Validation khác authorization

Input có đúng định dạng **không đồng nghĩa** người gọi có quyền thực hiện operation. Authorization phụ thuộc actor, resource và policy có thể thay đổi theo thời gian.

Nếu một aspect kiểm quyền, contract phải nêu boundary tin cậy, điều gì xảy ra khi không thể xác minh quyền, và việc gọi trực tiếp target có vượt qua kiểm tra không. Với invariant bảo mật cốt lõi, không được chỉ dựa vào một proxy có đường bypass.

## 6. Ghi điều kiện sau: kết quả và trạng thái được bảo đảm

Một **postcondition** có thể liên hệ kết quả với input và trạng thái trước lời gọi:

```text
normal_post(id, oldCount, result, newCount):
  result == "receipt:" + id
  newCount == oldCount + 1
```

Nếu aspect chỉ logging, một contract phù hợp thường là **không sửa** return value hoặc domain state. Nếu aspect làm result transformation, contract phải công bố cách transform.

### 6.1. Kết quả nội bộ và kết quả bên ngoài có thể khác

```text
Target returns internalOrderId
  -> RedactionAdvice returns maskedId
  -> Caller sees maskedId
```

Postcondition target liên quan `internalOrderId`; postcondition public liên quan `maskedId`. Tài liệu của aspect cần ghi chính sách che dữ liệu và giới hạn thông tin, không đơn giản nói “kết quả trả về giống target”.

### 6.2. Side effect đã thực hiện trước khi trả về

Nếu after advice ném exception sau khi target đã ghi dữ liệu, caller có thể nhận lỗi dù trạng thái đã thay đổi. Contract phải chỉ ra **exception có bảo đảm hoàn tác hay không**. Không có rollback tự động chỉ vì advice phát hiện lỗi hậu điều kiện.

### 6.3. Không thể bảo đảm trạng thái trong mọi môi trường đồng thời

Nếu một postcondition đọc `newCount` sau khi thread khác đã thay đổi cùng đối tượng, kết quả so sánh `oldCount + 1` có thể là false positive. Muốn công bố bảo đảm nhất quán phải mô tả **atomicity, isolation hoặc observation boundary** phù hợp.

## 7. Bất biến: công bố đúng ranh giới chứ không kiểm mọi dòng

Một **invariant** là tính chất phải đúng tại những điểm hợp lệ của vòng đời:

```text
publishedCount >= 0
activeResources <= configuredLimit
closed == true  =>  no further successful writes
```

Bản mô tả cần nói rõ:

- Bất biến áp dụng cho từng object, một request hay toàn hệ thống?
- Nó phải đúng tại public method entry/exit, sau commit hay sau completion?
- Có trạng thái trung gian được cho phép bên trong implementation không?
- Nếu kiểm tra phát hiện lỗi, object có bị cô lập hoặc đánh dấu không sử dụng tiếp?
- Trong concurrency, snapshot có nhất quán/atomic không?
- Khi exception, invariant nào vẫn phải giữ?

Một method có thể tạm thay đổi nhiều field rồi khôi phục tính nhất quán trước khi return. Weaving vào mọi private helper để kiểm class invariant có thể tạo lỗi giả nếu bất biến chỉ cam kết ở public boundary.

## 8. Ghi hợp đồng khi có ngoại lệ

Hợp đồng đầy đủ **không chỉ nói về đường thành công**. Cần chỉ rõ:

| Nhóm lỗi | Nguồn có thể có | Cam kết cần công bố |
| --- | --- | --- |
| Precondition violation | Checker từ chối input | Target có chạy không, lỗi gì được ném? |
| Authorization failure | Policy trước target | Có side effect bị cấm không, audit thế nào? |
| Business exception | Target ném | Aspect giữ, dịch hay che lỗi? |
| Postcondition violation | Checker sau target phát hiện sai | State đã đổi chưa, caller xử lý thế nào? |
| Audit/metrics failure | Advice phụ trợ ném | Fail-open hay fail-closed? |
| Cleanup failure | Advice finally/dọn tài nguyên | Có thể che lỗi gốc không? |
| Async exceptional completion | Task kết thúc lỗi sau method return | Ai quan sát, trên stage nào? |

### 8.1. Giữ nguyên lỗi hay chuyển đổi?

Có ba lựa chọn thường thấy:

- **Rethrow:** lan truyền nguyên ngoại lệ gốc khi hợp đồng cho phép.
- **Translate:** tạo một lỗi domain/public mới, giữ lỗi gốc trong `cause` phù hợp.
- **Fallback:** chuyển lỗi thành một giá trị bình thường đã được contract mô tả.

Ví dụ mã Java minh họa (không phải chương trình độc lập):

```java
throw new IllegalStateException("operation unavailable", original);
```

Nếu translation bỏ `cause`, chẩn đoán lỗi sẽ khó hơn. Nếu fallback trả kết quả giả thành công cho thao tác chưa hoàn tất, consumer có thể tin sai vào trạng thái hệ thống.

### 8.2. Phân biệt basic và strong exception guarantee

- **Basic guarantee:** sau lỗi, trạng thái vẫn hợp lệ theo bất biến liên quan nhưng có thể đã đổi.
- **Strong guarantee:** nếu thao tác thất bại, trạng thái thuộc phạm vi cam kết được giữ như trước.
- **No-throw guarantee:** thao tác/phase cụ thể được thiết kế không cho exception thoát theo hợp đồng.

Những bảo đảm này **không tự xuất hiện nhờ AOP**. Thư viện, transaction manager hoặc operation phải thực sự hiện thực hành vi đã hứa.

### 8.3. Caller được phép retry khi nào?

Nếu exception đến từ `AuditAdvice.afterReturning` sau khi target đã hoàn thành ghi, retry toàn bộ operation có thể tạo bản ghi trùng. Hợp đồng cần phân loại lỗi **có thể retry** so với **vi phạm hợp đồng/bug** hoặc “trạng thái chưa xác định”.

Một API ghi dữ liệu nên mô tả idempotency key, cách kiểm tra kết quả đã hoàn tất hoặc cơ chế tránh lặp nếu retry là kịch bản hợp lệ.

## 9. Ghi thứ tự khi có nhiều aspect

Nếu hai aspect cùng áp dụng ở một join point, kết quả có thể phụ thuộc thứ tự:

```text
Validation(
    Authorization(
        Transaction(
            Business
        )
    )
)
```

Với nesting trên:

- Validation có cơ hội từ chối trước khi Authorization chạy.
- Authorization có thể từ chối trước khi transaction bắt đầu.
- Transaction có thể tạo một boundary khi Business thực thi.
- Hành vi thoát sẽ diễn ra từ trong ra ngoài theo đường kết quả và phạm vi các finally tương ứng.

Nhưng **không có một thứ tự cụ thể áp dụng cho mọi công cụ AOP**. Mỗi engine có quy tắc precedence và behavior riêng. Hợp đồng consumer nên công bố những thứ tự **ảnh hưởng correctness**; tài liệu kỹ thuật nên chỉ ra cấu hình thực thi và test xác nhận chúng.

### 9.1. Precedence là quyền bao phần tiếp tục

Một around advice ngoài có thể không gọi `proceed`, khiến advice trong và business không chạy. Vì thế “các aspect A, B, C được cấu hình” chưa đủ để bảo đảm tất cả thực thi ở mọi invocation.

### 9.2. Lỗi của advice trong thành lỗi của advice ngoài

Một target có thể return thành công, rồi after advice trong ném exception. Advice ngoài trên nhánh lỗi có thể nhìn lỗi từ **advice trong** chứ không phải lỗi target.

Hợp đồng public cần mô tả đúng đường lỗi mà caller thấy và tránh gắn mọi thrown exception thành “business failed”.

## 10. Ghi rõ quyền của around advice

Trong một số mô hình AOP, around advice có thể:

- **Không gọi continuation** và từ chối yêu cầu.
- **Gọi một lần** để tiếp tục bình thường.
- **Gọi nhiều lần** để retry, nếu DSL/engine cho phép.
- Thay tham số truyền xuống.
- Thay kết quả trả lên.
- Bắt, dịch, nuốt hoặc tạo ngoại lệ.
- Gọi các công việc phụ trước/sau/trong continuation.

Các quyền này phải được mô tả theo **hành vi mà consumer cần dựa vào**, ví dụ:

```text
Aspect: CachedLookup
If cache has a valid entry:
  target is not called
  a cached result satisfying the published staleness limit is returned
Else:
  target is called once
Errors:
  cache failure is treated as miss, unless configured fail-closed
```

Đây là hợp đồng minh họa, không phải lời hứa mặc định của một cơ chế caching AOP nào.

Nếu không biết aspect có quyền gọi lại operation hay không, consumer không thể lập luận về số lần side effect.

## 11. Ghi side effect của aspect

Một aspect có thể trông như “phụ trợ” nhưng vẫn có side effect đáng kể:

- Gửi event audit.
- Ghi log có dữ liệu nhạy cảm.
- Gọi network hoặc storage.
- Tăng counter.
- Ghi cache hoặc invalidate cache.
- Thiết lập/giải phóng transaction context.
- Thay security principal.
- Đặt hoặc xóa thread/request context.
- Thực thi retry hoặc compensation.
- Giữ lock và thay đổi thời gian scheduling.

Mỗi side effect cần ghi:

1. Xảy ra ở phase nào?
2. Là synchronous hay asynchronous?
3. Có bảo đảm **at-most-once**, **at-least-once** hay exactly-once trong phạm vi nào không?
4. Nếu thất bại, caller có nhận lỗi không?
5. Có bị lặp khi retry/reentrancy không?
6. Thông tin nào được lưu, trong bao lâu và ai có quyền truy cập?
7. Có thể làm thay đổi kết quả/ngoại lệ mà caller quan sát không?

**Không nên hứa exactly-once** khi không có bằng chứng thiết kế tương ứng. Ví dụ một log line chỉ cho biết handler được gọi, không chứng minh một event đã được ghi bền vững đúng một lần ở hệ thống từ xa.

## 12. Mô tả khả năng observability mà không hứa quá mức

Một contract observability có thể nói:

```text
At proxy entry:
  emit audit.attempt with operation ID

After normal return:
  emit audit.success

After thrown exception:
  emit audit.failure with error class

On audit sink failure:
  preserve the primary operation outcome when policy is best-effort
```

Nhưng cần xác định:

- “emit” nghĩa là đưa vào bộ đệm hay lưu bền vững?
- Log có thể bị sampling không?
- Log của việc bị từ chối ở before advice được ghi ở đâu?
- Nếu consumer gọi raw target, event có xuất hiện không?
- Một async operation được log “success” khi method return hay khi Future complete?
- Nếu nhiều advice lồng nhau, có một event hay nhiều event cho mỗi attempt?

### 12.1. Correlation và invocation identity

Một method có thể chạy hàng nghìn lần. Chỉ ghi method name không đủ ghép event. Có thể dùng **trace ID**, **invocation/span ID** và **build/config ID** khi phù hợp.

Không nên gán trace ID bằng một shared mutable field cho mọi thread. Đối với callback/async, context cần theo tác vụ và phải được dọn đúng vòng đời.

### 12.2. Bảo vệ dữ liệu nhạy cảm

Không mặc định serialize toàn bộ args/result để “ghi nhận hợp đồng”. Aspect thường nhìn dữ liệu ở ranh giới nhạy cảm trước khi che hoặc mã hóa.

Nên công bố danh sách field được phép log, quy tắc mask/redact, retention và giới hạn quyền truy cập. Log có thể chứa thông tin nhận dạng/ủy quyền, nên dữ liệu chẩn đoán cũng cần hợp đồng bảo mật.

## 13. Hiệu năng và tài nguyên là một phần của hợp đồng vận hành

Một aspect có thể thêm:

- Một dispatcher/proxy hop.
- Reflection hoặc logic binding ngữ cảnh.
- Kiểm tra điều kiện động.
- Cấp phát object/exception/trace context.
- Network round trip để kiểm quyền/audit.
- Lock hoặc thao tác transaction.
- Retry làm tăng số lần gọi target.
- Async callback hoặc queue.

Không phải mọi overhead đều đo được bằng “bao nhiêu nano giây mỗi proxy call”. Contract vận hành có thể công bố:

- Giới hạn timeout và điều gì xảy ra khi timeout.
- Chính sách backpressure/rate limit.
- Cách xử lý khi sink audit không khả dụng.
- Số attempt tối đa khi retry.
- Có khả năng block thread hay không.
- Loại metric chính và phân biệt request với attempt.
- SLO mong muốn nếu có số đo thực tế và một hệ thống cam kết cụ thể.

**Không tự đặt một ngưỡng latency hay exactly-once guarantee nếu chưa đo/thiết kế.** Với tài liệu học tập, chỉ đưa các trường cần được định nghĩa; giá trị số phải xuất phát từ hệ thống được vận hành.

## 14. Tài liệu không nên phụ thuộc vào chi tiết implementation không ổn định

Để consumer dễ sử dụng, mô tả **hợp đồng quan sát được** trước:

```text
Nếu id không hợp lệ:
  publish ném IllegalArgumentException
  target không được gọi thông qua boundary đã quản lý
```

Sau đó mới mô tả **cơ chế hiện thực**:

```text
Một interface proxy kiểm tra đầu vào trước khi ủy quyền cho target.
```

Tách hai tầng giúp thay proxy bằng bytecode weaving hoặc facade mà không buộc phải viết lại mọi mô tả API, miễn behavior thật vẫn tương thích.

Tuy vậy, những chi tiết mechanism có tác động **phạm vi bảo đảm** (như self-invocation bypass) phải được ghi công khai, không giấu dưới nhãn “internal detail”.

## 15. Ví dụ tài liệu hợp đồng cho một aspect kiểm soát publication

Một bản hợp đồng độc lập có thể trình bày:

### 15.1. Danh tính và phạm vi

```text
Aspect ID: publication-contract
Applies to: Publication.publish(String,String)
Boundary: caller -> managed Publication proxy -> target
Excluded: Publication.publishedCount()
Non-covered: calls made directly to raw target
```

### 15.2. Điều kiện trước

```text
id and body must be non-null, nonblank.
On failure:
  IllegalArgumentException
  target.publish is not invoked.
```

### 15.3. Điều kiện sau

```text
For normal completion:
  result == "receipt:" + id
  publishedCount increases by exactly one
```

### 15.4. Khi có lỗi

```text
If target throws:
  original exception is propagated through this proxy boundary.
  No strong rollback guarantee is asserted by this aspect.
  Postcondition for normal completion is not applied.
```

### 15.5. Side effect và khả năng bypass

```text
The aspect prints demonstration events at entry, success,
failure and exit.
This is an educational example, not a durable audit log.
Caller using target directly does not run these checks.
```

**Chú ý:** nội dung trên chỉ mô tả ví dụ Java ngay sau đây. Với một hệ thống production, hợp đồng phải được thay bằng đúng policy, version, failure mode và phạm vi thực tế — không sao chép nguyên mà coi là bảo đảm.

## 16. Ví dụ Java 17 chạy được: hợp đồng được thực thi tại proxy

Ví dụ sử dụng **JDK dynamic proxy**. Nó không phải engine AOP đầy đủ: không phân tích pointcut DSL và không biến đổi bytecode target. Nhưng nó thể hiện rõ **hợp đồng dành cho consumer ở ranh giới proxy**, cách xử lý return/exception và một đường bypass có thể kiểm chứng.

Tạo file `AspectConsumerContractDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class AspectConsumerContractDemo {

    interface Publication {
        String publish(String id, String body);
        int publishedCount();
    }

    static final class Repository implements Publication {
        private int count;

        @Override
        public String publish(String id, String body) {
            System.out.println("business:publish:" + id + ":" + body);
            if ("FAIL".equals(body)) {
                throw new IllegalStateException("repository unavailable");
            }
            count++;
            return "receipt:" + id;
        }

        @Override
        public int publishedCount() {
            return count;
        }
    }

    static Publication withContract(Repository target) {
        return (Publication) Proxy.newProxyInstance(
                Publication.class.getClassLoader(),
                new Class<?>[] { Publication.class },
                (Object proxy, Method method, Object[] args) -> {
                    if (!method.getName().equals("publish")) {
                        return method.invoke(target, args);
                    }

                    System.out.println("contract:enter:publish");
                    try {
                        String id = (String) args[0];
                        String body = (String) args[1];
                        if (id == null || id.isBlank()) {
                            throw new IllegalArgumentException("id required");
                        }
                        if (body == null || body.isBlank()) {
                            throw new IllegalArgumentException("body required");
                        }

                        int old = target.publishedCount();
                        String result;
                        try {
                            result = (String) method.invoke(target, args);
                        } catch (InvocationTargetException wrapped) {
                            Throwable original = wrapped.getCause();
                            System.out.println("contract:failure:"
                                    + original.getClass().getSimpleName());
                            if (target.publishedCount() < 0) {
                                throw new IllegalStateException(
                                        "invariant violated", original);
                            }
                            throw original;
                        }

                        if (!result.equals("receipt:" + id)
                                || target.publishedCount() != old + 1) {
                            throw new IllegalStateException(
                                    "postcondition violated");
                        }

                        System.out.println("contract:success:" + result);
                        return result;
                    } finally {
                        System.out.println("contract:exit:publish");
                    }
                }
        );
    }

    static void tryPublish(String label, Publication p,
                           String id, String body) {
        System.out.println("case:" + label);
        try {
            System.out.println("caller:" + p.publish(id, body));
        } catch (RuntimeException error) {
            System.out.println("caller:error:"
                    + error.getClass().getSimpleName()
                    + ":" + error.getMessage());
        }
    }

    public static void main(String[] args) {
        Repository target = new Repository();
        Publication managed = withContract(target);

        tryPublish("ok", managed, "D1", "hello");
        tryPublish("invalid", managed, " ", "hello");
        tryPublish("failure", managed, "D2", "FAIL");
        tryPublish("bypass", target, "D3", "direct");

        System.out.println("caller:count:" + managed.publishedCount());
        System.out.println("caller:same-reference:" + (managed == target));
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac AspectConsumerContractDemo.java
java AspectConsumerContractDemo
```

**Đầu ra dự kiến:**

```text
case:ok
contract:enter:publish
business:publish:D1:hello
contract:success:receipt:D1
contract:exit:publish
caller:receipt:D1
case:invalid
contract:enter:publish
contract:exit:publish
caller:error:IllegalArgumentException:id required
case:failure
contract:enter:publish
business:publish:D2:FAIL
contract:failure:IllegalStateException
contract:exit:publish
caller:error:IllegalStateException:repository unavailable
case:bypass
business:publish:D3:direct
caller:receipt:D3
caller:count:2
caller:same-reference:false
```

### 16.1. Trường hợp thành công

`managed.publish("D1","hello")` đi qua handler:

1. Ghi sự kiện `enter`.
2. Kiểm tra precondition.
3. Lưu `oldCount`.
4. Ủy quyền tới target; target tăng counter và trả receipt.
5. Kiểm postcondition `receipt:D1` và count tăng đúng một.
6. Ghi `success`, chạy `finally` và trả giá trị tới caller.

### 16.2. Trường hợp input sai

`id = " "` làm `isBlank() == true`; handler ném `IllegalArgumentException("id required")` **trước khi gọi target**. Do khối `finally` đã được vào, dòng `contract:exit` vẫn xuất hiện.

Contract nói chính xác **target không được gọi khi precondition không hợp lệ**. Có thể kiểm bằng counter hoặc absence of business event, không chỉ assert loại lỗi.

### 16.3. Trường hợp target ném lỗi

`body = "FAIL"` làm target ném `IllegalStateException`. Reflection chuyển lỗi sang `InvocationTargetException`; handler lấy `getCause()` rồi ném lại exception gốc. Nó không chạy normal postcondition.

Checker exceptional ở ví dụ chỉ kiểm count không âm; nó **không khẳng định strong rollback guarantee** cho tất cả trường hợp lỗi. Nếu target có thể thay đổi state trước khi ném, tài liệu phải mô tả điều đó hoặc có cơ chế hoàn tác thực sự.

### 16.4. Trường hợp bypass

`tryPublish("bypass", target, ...)` gọi **thẳng target**. Đường này **không có bất kỳ dòng `contract:*` nào**, mặc dù target có cùng interface và method. Đây là giới hạn của interface proxy delegation trong ví dụ.

Khi contract được xem là bảo vệ bắt buộc, ứng dụng phải thiết kế sao cho consumer có trách nhiệm không lấy raw target hoặc áp dụng bảo vệ ở ranh giới không thể bypass.

### 16.5. Những điều ví dụ không chứng minh

- Không tự tạo pointcut parser, join point shadow hoặc weaving compiler.
- Không chặn mọi internal call, constructor, static field/method hoặc bytecode.
- Không bảo đảm an toàn đồng thời cho counter trong multi-threading.
- Không cung cấp transaction, rollback, durable audit hay exactly-once semantics.
- Không thay thế một API contract riêng cho production.
- Không nói rằng một công cụ AOP khác chắc chắn có cùng thứ tự advice.

Mục đích của ví dụ là làm **hợp đồng cụ thể, quan sát được và có kiểm thử**, chứ không mô phỏng mọi chức năng AOP.

## 17. Cách đưa hợp đồng vào tài liệu của API

Một consumer thường không mở file cấu hình weaving để đọc trước mỗi lời gọi. Tài liệu phù hợp có thể gồm:

1. **API documentation**: ở nơi mô tả phương thức/operation công khai.
2. **Aspect contract document**: một trang theo danh tính/chính sách aspect.
3. **Configuration reference**: phạm vi, precedence, các switch/option.
4. **Release notes**: mọi thay đổi behavior từ phiên bản trước.
5. **Architecture decision record**: tại sao chọn interception boundary, giới hạn và lựa chọn thay thế.
6. **Test suite**: các lời cam kết quan trọng được xác nhận tự động.

Nếu dùng Javadoc, có thể ghi thông tin consumer quan trọng trực tiếp trên interface:

```java
/**
 * Công bố tài liệu.
 *
 * <p>Trong cấu hình API có contract interceptor:
 * id và body phải có nội dung; đầu vào sai bị từ chối
 * trước khi gọi target.
 *
 * <p>Khi kết thúc bình thường, trả "receipt:" + id.
 * Khi thất bại, xem exceptional guarantee được công bố
 * cho operation; không mặc định có rollback.
 */
String publish(String id, String body);
```

Đây là **mảnh Javadoc minh họa**, không phải class độc lập. Tài liệu cần phân biệt hợp đồng **luôn áp dụng của API** với hợp đồng **chỉ áp dụng trong cấu hình có interceptor**, tránh làm consumer hiểu sai khi dùng implementation thô.

### 17.1. Ghi phiên bản của hợp đồng, không chỉ phiên bản mã

Cấu hình pointcut hoặc thứ tự advice có thể thay đổi behavior mà không sửa public method signature. Do đó release notes nên nêu:

- Method nào mới thuộc phạm vi can thiệp?
- Method nào bị loại khỏi chính sách?
- Precondition có mạnh hơn/yếu đi không?
- Return value hoặc exception taxonomy có đổi không?
- Retry/timeout/audit guarantees có đổi không?
- Có thay đổi từ best-effort sang fail-closed hoặc ngược lại không?
- Có thay đổi đường bypass và yêu cầu cách khởi tạo object không?

Một thay đổi chỉ ở config **vẫn có thể là breaking change** đối với consumer.

## 18. Mẫu Markdown hợp đồng aspect tái sử dụng

Mẫu dưới đây dùng các trường **cần điền bằng thông tin thật của hệ thống**; đây không phải cấu hình chạy được:

```markdown
# Aspect Contract: <Tên chính sách>

## Identity
- Contract version:
- Owner:
- Purpose:

## Applicability
- Join point kinds:
- Included operations:
- Excluded operations:
- Weaving/proxy boundary:
- Dynamic guards:
- Known bypass paths:

## Caller obligations
- Input preconditions:
- Required context:
- Concurrency/identity assumptions:

## Normal guarantees
- Return value:
- State postcondition:
- Invariants:
- Side effects:

## Exceptional guarantees
- Expected exception classes:
- Wrapping/translation:
- Basic or strong guarantee:
- Retry/idempotency guidance:
- Cleanup/audit failure policy:

## Interaction
- Ordering/precedence:
- Short-circuit/fallback behavior:
- Maximum invocation count:
- Async completion boundary:

## Operations
- Enable/disable behavior:
- Timeout/failure mode:
- Logs/metrics/tracing:
- Sensitive data handling:

## Compatibility and verification
- Breaking changes:
- Consumer migration:
- Positive and negative tests:
- Evidence of interception coverage:
```

**Chú ý:** `<Tên chính sách>` trong mẫu là placeholder **cho người áp dụng template**, không phải một phần của bài Java chạy được. Với artifact sản xuất, phải thay các trường này bằng giá trị/những câu cam kết thật trước khi công bố. Đừng để một trường trống được hiểu thành bảo đảm mặc định.

### 18.1. Mẫu hợp đồng là một phần của quy trình review

Nên để cả người sở hữu business API và người sở hữu aspect cùng review. Bên cung cấp aspect biết implementation; bên sở hữu API biết semantic contract mà consumer phụ thuộc. Một phía tự viết không kiểm soát đầy đủ thay đổi trên đường call cuối.

## 19. Cách diễn đạt quyền và nghĩa vụ sao cho không mơ hồ

So sánh:

**Mơ hồ:**

> Aspect sẽ xử lý exception phù hợp.

**Có thể kiểm chứng:**

> Khi `publish` qua managed proxy ném `IllegalStateException` từ target, interceptor ghi sự kiện failure và lan truyền cùng loại exception; không trả fallback.

**Mơ hồ:**

> Aspect chạy với tất cả service.

**Có thể kiểm chứng:**

> Chỉ những invocation của `Publication.publish(String,String)` đi qua `Publication` proxy được cấp bởi factory mới nhận các kiểm tra hợp đồng trong phiên bản này.

**Mơ hồ:**

> Aspect không ảnh hưởng nghiệp vụ.

**Có thể kiểm chứng:**

> Aspect chỉ ghi event và đo thời lượng; với sink best-effort, lỗi gửi metric không được thay return/exception của target và không làm target chạy thêm lần nào.

Một phát biểu càng cụ thể, càng dễ biến thành test. Khi chưa có bảo đảm, nên ghi **“không cam kết”** thay vì viết một câu chung gây cảm giác an toàn giả.

## 20. Phân biệt lỗi caller, lỗi implementation và lỗi của aspect

Trong Design by Contract:

- Vi phạm **precondition** có thể chỉ ra caller cung cấp dữ liệu/ngữ cảnh không hợp lệ.
- Vi phạm **normal postcondition** với precondition đúng có thể chỉ ra implementation hoặc advice biến đổi kết quả sai.
- Vi phạm **invariant** có thể liên quan lifecycle, concurrency hoặc bug state management.
- Lỗi **advice** có thể do audit sink, logger, timeout, cache hoặc exception translation.

Nếu mọi thứ đều trở thành một `RuntimeException("AOP failed")`, consumer khó quyết định có retry hay không.

Nên công bố taxonomy lỗi ổn định ở mức API, giữ `cause` cho chẩn đoán khi phù hợp và tránh để lỗi phụ trợ che lỗi chính ngoài ý định.

### 20.1. Checked và unchecked exceptions trong Java

Với JDK dynamic proxy, một checked exception do `InvocationHandler` ném nhưng **không tương thích với `throws` của method interface** có thể bị runtime bọc trong `UndeclaredThrowableException`. Vì thế contract phải tương thích với chữ ký public API, không mặc định rằng mọi lỗi từ target sẽ luôn xuất hiện nguyên dạng.

Ví dụ chương trình trên chủ yếu dùng unchecked exceptions, tránh tình huống checked exception ngoài chữ ký. Khi xây API thật, phải kiểm tra đường chuyển tiếp lỗi theo interface và engine cụ thể.

## 21. Thứ tự và tương thích khi nhiều aspect được thêm hoặc bỏ

Một aspect mới có thể biến API cũ thành API khác về hành vi, dù target không đổi.

Ví dụ:

```text
Before change:
  Caller -> Target

After change:
  Caller -> Retry -> Audit -> Target
```

Khả năng thay đổi:

- Target có thể được gọi nhiều lần do retry.
- Audit có thể ghi một event mỗi attempt thay vì mỗi request.
- Caller có thể thấy thời lượng lớn hơn.
- Một exception có thể được chuyển thành fallback.
- Thứ tự log có thể khác.
- Side effect có thể lặp khi operation không idempotent.

Bản ghi hợp đồng nên ghi rõ **số lần thực thi tối đa của operation**, nếu số lần có ý nghĩa. Nếu không biết chắc, không hứa “gọi đúng một lần”.

### 21.1. Kiểm tra backward compatibility

Một thay đổi có thể là breaking change khi:

- Thắt chặt precondition.
- Làm yếu bảo đảm postcondition.
- Bỏ một loại lời gọi khỏi interception.
- Đổi fail-open thành fail-closed hoặc ngược lại.
- Thay quy tắc audit/durable logging mà consumer/vận hành dựa vào.
- Thêm retry vào operation có side effect không idempotent.
- Đổi loại exception public.
- Đổi thời điểm success của async task.
- Bắt consumer sử dụng proxy factory thay vì direct construction.

Đánh giá tương thích **theo hành vi bên ngoài**, không chỉ theo compile-time API.

## 22. Contract với thao tác bất đồng bộ

Một method có thể trả `CompletableFuture<Receipt>`. Hợp đồng phải tách:

1. **Method-return contract:** trả một Future hợp lệ, đăng ký công việc đúng cách.
2. **Completion contract:** nếu Future hoàn tất bình thường, Receipt đáp ứng postcondition; nếu exceptional, lỗi và state thỏa exceptional guarantee.
3. **Cancellation/timeout contract:** cancel có ngăn side effect chưa chạy không, hay chỉ dừng chờ?
4. **Context contract:** trace/actor/transaction context được chuyển và xóa như thế nào?

Một after-returning advice ở method execution thường chỉ nhìn việc **trả Future**, không tự chứng minh task đã hoàn tất. Nếu consumer nhận tài liệu “after success đã kiểm” mà checker chỉ chạy khi Future được tạo, đó là mô tả gây hiểu nhầm.

### 22.1. Đừng hứa callback order khi không có ràng buộc

Hai callback async được đăng ký theo thứ tự A rồi B không đương nhiên thực thi theo A rồi B trong mọi scheduler/API. Nếu contract yêu cầu ordering, phải dùng cơ chế sequencing phù hợp và kiểm thử trên môi trường đích.

## 23. Contract khi có đồng thời, đệ quy và reentrancy

Một interceptor có thể được dùng đồng thời bởi nhiều thread. Nếu nó lưu `currentRequestId` trong một field mutable chung mà không quản lý scope, event của hai invocation có thể gắn sai request.

Cần ghi nhận:

- Handler/advice có thread-safe không?
- State nào là singleton, state nào per invocation?
- Có lock hay không; lock order, nguy cơ deadlock?
- Một operation có thể gọi lại chính nó không?
- Advice có thể tự kích hoạt vì logger/cache/client cũng khớp pointcut không?
- Một invocation có thể được thực hiện lại bởi retry?
- Có bảo đảm snapshot/invariant atomic hay chỉ best-effort monitoring?

Một contract “thread-safe” chỉ nên công bố khi implementation được thiết kế và kiểm thử tương ứng. AOP weaving hoặc dynamic proxy **không tự tạo thread safety** cho target.

### 23.1. Tránh vòng lặp interception

```text
AuditAdvice
   -> auditLogger.log()
       -> cùng pointcut
           -> AuditAdvice ...
```

Nếu logger cũng bị can thiệp, có thể xảy ra recursion hoặc số lần advice tăng bất thường. Tài liệu kỹ thuật cần nêu exclusion và chính sách reentrancy; consumer cần biết những thay đổi observability/performance có thể quan sát được.

## 24. Contract của việc bật, tắt và đổi cấu hình

Một aspect đôi khi được bật theo profile, feature flag hoặc module deployment. Ghi hợp đồng cần tránh tuyên bố behavior “luôn có” khi có thể bị tắt ở runtime.

Cần phân biệt:

- **Public invariant không thể vô hiệu hóa:** ví dụ giới hạn dữ liệu bắt buộc.
- **Policy tùy cấu hình:** logging/metrics/tracing hoặc kiểm thử nâng cao.
- **Fail-open/fail-closed:** kết quả khi dependency của aspect không khả dụng.
- **Hot reload:** thay đổi cấu hình có áp cho invocation đang chạy hay chỉ invocation mới?
- **Rollout/rollback:** làm sao biết request nào đã đi qua chính sách phiên bản nào?
- **Consistency:** cùng một request đi qua nhiều instance có thể gặp config khác nhau không?

Nếu một bảo đảm nghiệp vụ chỉ được thực thi bởi advice mà có thể tắt, consumer **không nên được hứa** bảo đảm tuyệt đối mà không nêu điều kiện.

### 24.1. Khả năng kiểm toán cấu hình

Nên có version/build/config ID để liên kết event runtime và tài liệu contract đang có hiệu lực. Một lần chạy có thể dùng artifact cũ hoặc một class đã được nạp trước khi weaving được kích hoạt; không chỉ nhìn source hiện tại để suy ra hành vi production.

## 25. Đưa hợp đồng vào kiểm thử tự động

Một bảng tiêu chí:

| Tình huống | Điều consumer phải nhận thấy |
| --- | --- |
| Input hợp lệ, method được quản lý | Advice phù hợp chạy; target chạy đúng số lần cam kết |
| Input vi phạm precondition | Lỗi công bố; target không chạy nếu policy hứa vậy |
| Normal return | Kết quả và trạng thái đáp ứng public postcondition |
| Target ném lỗi | Lỗi được giữ/translate theo contract |
| Advice sau target ném lỗi | Không gán nhãn sai thành lỗi business; side effect được ghi nhận |
| Around trả fallback | Giá trị fallback có hợp đồng riêng, không giả success |
| Around không proceed | Target/inner advice không chạy như tài liệu |
| Retry | Số attempt, lỗi được phép retry, idempotency đúng |
| Không khớp pointcut | Không có advice ngoài phạm vi |
| Direct target call | Được nhận diện là bypass nếu cơ chế proxy |
| Self-invocation | Đúng phạm vi loại proxy/weaving thật |
| Concurrent invocations | Không trộn context, state và correlation |
| Future completion | Không nhầm method return với task completion |
| Audit sink lỗi | Fail-open/fail-closed đúng cam kết |
| Disable policy | Behavior phù hợp contract của chế độ disabled |
| Upgrade aspect | Kiểm tra breaking changes về scope/exception/result |

### 25.1. Kiểm chứng contract bằng sự kiện chứ không chỉ bằng return value

Khi target trả đúng result ở cả direct call và proxy call, chỉ assert result sẽ bỏ sót việc aspect không chạy. Có thể dùng event collector/counter trong test để xác nhận:

- `before` chạy bao nhiêu lần?
- `afterReturning`/`afterThrowing` chạy ở nhánh nào?
- Target đã thực thi hay chưa?
- Snapshot pre/post được lấy đúng invocation không?
- Advice có chạy cho một method nằm ngoài phạm vi không?

### 25.2. Test với môi trường đúng ranh giới

Một unit test tạo target bằng `new` không giống integration test nhận managed proxy. Khi contract yêu cầu interception, test setup phải mô phỏng **đường cấp phát/reference và cấu hình thực tế**; đồng thời có negative test cho bypass.

## 26. Những lỗi ghi tài liệu thường gặp

**“Aspect chạy trước mọi method.”** Quá rộng, không có giới hạn join point, scope, guard và bypass.

**“Đã chạy audit nghĩa là event đã được lưu vĩnh viễn.”** Không chắc nếu chỉ đưa vào queue hoặc buffer.

**“Khi lỗi, thao tác không thực hiện.”** Có thể sai nếu lỗi phát sinh sau side effect hoặc khi commit trạng thái đã mơ hồ.

**“Các aspect đều trong suốt.”** Sai khi có short-circuit, retry, result translation, security rejection hoặc exception masking.

**“Kết quả luôn giống target.”** Không đúng nếu có advice thay result hoặc trả fallback.

**“Chữ ký không đổi nên hợp đồng không đổi.”** Không đúng nếu pointcut, precedence hoặc configuration đổi.

**“Proxy bảo vệ toàn bộ class.”** Không nếu caller gọi raw target hoặc self-invocation không đi qua interceptor.

**“After returning nghĩa tác vụ async thành công.”** Có thể chỉ là method trả Future.

**“Lỗi logging không bao giờ ảnh hưởng business.”** Chỉ đúng nếu implementation/failure policy thật sự bảo đảm điều đó.

**“ContractViolation luôn nghĩa caller sai.”** Vi phạm postcondition hoặc invariant có thể là lỗi implementation/advice.

**“Có exception là state sẽ rollback.”** Không; rollback cần một cơ chế và phạm vi bảo đảm thực tế.

**“Đã có doc là không cần test.”** Không; contract là lời hứa, test và vận hành là chứng cứ thực hiện.

## 27. Câu hỏi tự luyện

### 27.1. Pointcut đúng nhưng advice không chạy

Một `Publication.publish` có chữ ký khớp, nhưng caller dùng `new Repository()` và gọi trực tiếp. Tài liệu nào cần bổ sung?

**Đáp án:** Ranh giới interception và đường bypass. Nếu chính sách chỉ áp qua proxy, phải công bố rằng caller cần dùng managed proxy/factory để nhận bảo đảm.

### 27.2. Audit lỗi sau khi business thành công

Target đã ghi dữ liệu, after advice audit ném lỗi. Caller nhận exception. Có thể công bố “không có dữ liệu được ghi khi exception” không?

**Đáp án:** Không nếu không có strong guarantee thật sự bao cả trường hợp ấy. Cần mô tả side effect đã có thể xảy ra và chiến lược kiểm tra trạng thái/retry.

### 27.3. Siết precondition

API trước đây chấp nhận chuỗi id có khoảng trắng, sau khi thêm checker chỉ cho chuỗi đã trim. Có phải thay đổi backward compatible?

**Đáp án:** Không nhất thiết; có thể là breaking behavioral change vì caller hợp lệ trước đây bị từ chối.

### 27.4. Retry và number of invocations

Around advice thử lại tối đa ba lần. Có thể viết “target được gọi đúng một lần” trong contract không?

**Đáp án:** Không. Phải ghi số attempt tối đa và điều kiện retry, đặc biệt khi target có side effect.

### 27.5. Exception translation

Target ném `IOException` nhưng aspect public hóa `DomainUnavailable`. Consumer cần biết gì?

**Đáp án:** Loại exception public, dữ liệu/cause có được giữ không, trạng thái sau lỗi và quy tắc retry/cancellation.

### 27.6. Future completion

Method trả `CompletableFuture`; after returning advice ghi “success”. Có bảo đảm operation hoàn tất thành công chưa?

**Đáp án:** Không. Cần hợp đồng completion riêng và giám sát stage/task thực.

### 27.7. Bất biến giữa nhiều aspect

Một aspect trong sửa state rồi aspect ngoài sửa result. Checker đặt trong cùng một method body chỉ kiểm state ban đầu. Có đủ chứng minh public contract không?

**Đáp án:** Không. Phải kiểm composed contract ở đúng boundary và có kiểm tra riêng cho side effect/invariant cần giữ.

### 27.8. Tắt checker

Một cấu hình cho phép disable contract aspect trong production. Có được ghi “mọi lời gọi luôn được validate bởi aspect” không?

**Đáp án:** Không. Phải nêu điều kiện bật/tắt hoặc đặt validation bắt buộc tại boundary không bị vô hiệu hóa.

### 27.9. Concurrent state

Hai thread sửa counter khi checker đang lấy `old` và `new`. Một phép so sánh sai có đủ chứng minh bug nghiệp vụ không?

**Đáp án:** Chưa. Contract phải chỉ rõ tính nhất quán/atomicity của snapshot và phép biến đổi.

### 27.10. Tính trong suốt

Aspect chỉ ghi metric nhưng thỉnh thoảng tự ném exception do sink không khả dụng. Có còn đạt hợp đồng “không ảnh hưởng kết quả nghiệp vụ” không?

**Đáp án:** Không nếu exception phụ trợ thoát tới caller thay vì được xử lý theo best-effort policy được công bố.

## 28. Checklist công bố hợp đồng cho consumer

- [ ] Có tên, version, owner và mục đích aspect.
- [ ] Nêu rõ join point kind, inclusion/exclusion và runtime guard.
- [ ] Nêu rõ weaving/proxy boundary và các đường bypass.
- [ ] Phân biệt hợp đồng target, aspect và composed/public API.
- [ ] Viết precondition theo input và ngữ cảnh caller quan sát.
- [ ] Nêu rõ cách từ chối, error type và việc target có được gọi không.
- [ ] Viết normal postcondition có thể kiểm chứng.
- [ ] Viết exceptional guarantee riêng, không hứa rollback mặc định.
- [ ] Nêu invariant và thời điểm nó phải đúng.
- [ ] Ghi rõ aspect có sửa args/result/error hay không.
- [ ] Ghi rõ quanh-advice có short-circuit, retry hoặc fallback không.
- [ ] Ghi precedence của những aspect có phụ thuộc.
- [ ] Ghi loại side effect và semantics thất bại của chúng.
- [ ] Phân biệt audit/log “đã gửi” với “đã lưu bền vững”.
- [ ] Có chính sách bảo vệ dữ liệu nhạy cảm trong trace/log.
- [ ] Phân biệt method return với completion bất đồng bộ.
- [ ] Nêu các bảo đảm concurrency/reentrancy thực sự có.
- [ ] Ghi cấu hình bật/tắt, fail-open/fail-closed và giới hạn vận hành.
- [ ] Có kiểm thử positive, negative, bypass, error, retry và multi-aspect.
- [ ] Có release notes cho mọi thay đổi behavior có thể ảnh hưởng consumer.
- [ ] Không hứa số liệu hiệu năng chưa được đo.
- [ ] Kiểm tra chữ ký `throws` và exception wrapping với proxy Java.
- [ ] Có bằng chứng trên đường gọi thật, không chỉ pointcut matching tĩnh.
- [ ] Không chỉ dựa vào tài liệu để bảo vệ chính sách bảo mật bắt buộc.
- [ ] Consumer có thể tìm đúng contract version áp dụng trong môi trường của mình.

## 29. Kết luận

**Ghi nhận hợp đồng của aspect cho bên sử dụng** là công bố hành vi của **toàn bộ đường thực thi được kết hợp**, bao gồm những gì caller phải đáp ứng, những gì hệ thống bảo đảm, những thay đổi do advice gây ra, và các giới hạn của phạm vi can thiệp.

Tài liệu tốt không dừng ở “aspect này dùng để logging” hoặc “pointcut chọn publish”. Nó phải trả lời bằng điều kiện có thể kiểm thử: **khi nào chạy, ai được gọi, kết quả/exception nào được thấy, side effect nào xảy ra, có thể retry hay bypass không, và chính sách còn đúng khi nhiều aspect cùng tham gia hay không**.

Khi aspect có thể đổi tham số, chặn thao tác, trả fallback, thay kết quả hoặc lỗi, đó là **một phần của hợp đồng API**, cần được review, version và kiểm thử tương tự mã nghiệp vụ. Một contract đúng đắn giúp consumer dùng hệ thống an toàn, vận hành dễ truy vết và nâng cấp AOP mà không gây breaking change ngoài dự kiến.


</details>

- [Quay lại đầu trang](#back-to-top)
