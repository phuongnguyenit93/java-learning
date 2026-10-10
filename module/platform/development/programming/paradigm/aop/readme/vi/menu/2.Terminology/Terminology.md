<a id="back-to-top"></a>

# Mô hình cốt lõi của AOP: Khía cạnh và điểm thực thi

## Menu
- [Aspect: Đơn vị mô-đun hóa mối quan tâm xuyên suốt](#aop-aspect)
- [Join point: Điểm có ý nghĩa trong mô hình thực thi](#aop-join-point)
- [Pointcut: Quy tắc lựa chọn join point](#aop-pointcut)
- [Advice: Hành vi bổ sung tại điểm được chọn](#aop-advice)
- [Target: Thành phần hoặc hành vi gốc](#aop-target)
- [Weaving: Ghép aspect với target](#aop-weaving)
- [Các vị trí thực thi của before, after và around advice](#aop-advice-kinds)
- [Luồng thực thi hiệu lực, kết quả và ngoại lệ khi áp dụng advice](#aop-effective-execution)
- [Ví dụ nối concern, aspect, join point, pointcut và advice](#aop-selection-example)

## <a id="aop-aspect">Aspect: Đơn vị mô-đun hóa mối quan tâm xuyên suốt</a>

<details>
<summary>Xem chi tiết</summary>

**Aspect** là đơn vị mô-đun hóa **mối quan tâm xuyên suốt và quy tắc chọn nơi áp dụng**, chứ không chỉ là một nhóm hàm tiện ích. Ví dụ, `TimingConcern` vừa mô tả phép đo thời gian vừa xác định nhóm lần thực thi cần đo; đổi cách ghi nhận thời lượng không buộc sửa từng dịch vụ.

Không nên đặt quy tắc phê duyệt nghiệp vụ của mọi phòng ban vào một aspect “tiện dụng”: những quyết định khác nhau cần đúng chủ sở hữu. Trong ví dụ đang học, aspect chỉ nắm phép đo và tiêu chí áp dụng; target vẫn quyết định kết quả chuyển tiền.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-join-point">Join point: Điểm có ý nghĩa trong mô hình thực thi</a>

<details>
<summary>Xem chi tiết</summary>

**Join Point** là một điểm có ý nghĩa trong execution model nơi behavior bổ sung có thể tham gia.

Tùy implementation, join point có thể là method execution, constructor, field access hoặc một loại execution event khác.

Hãy hình dung một lần `transferFunds` thực thi là **một sự kiện trong dòng chạy**, khác với chính đoạn mã khai báo phương thức. Nếu mô hình triển khai hỗ trợ, điểm đó có thể nhận hành vi bổ sung. AspectJ có mô hình join point phong phú hơn, bao gồm cả một số thao tác truy cập trường; Spring AOP thì chỉ hỗ trợ **thực thi phương thức trên bean được quản lý**.

Đừng nhầm “phương thức được định nghĩa” với “mọi đường gọi vào phương thức đều bị chặn”. Join point phải nằm trong ranh giới mà cơ chế ghép hoặc trung gian quan sát được. Đây là nền tảng để hiểu lỗi self-invocation ở chương sau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-pointcut">Pointcut: Quy tắc lựa chọn join point</a>

<details>
<summary>Xem chi tiết</summary>

**Pointcut** là rule chọn một tập join point.

Nói ngắn gọn:

```text
Join Point = nơi có thể can thiệp
Pointcut   = rule chọn nơi cần can thiệp
```

Nếu join point là những vị trí có thể gắn hành vi, pointcut là **điều kiện chọn tập con** của chúng. Ví dụ quy tắc theo phạm vi dịch vụ thanh toán có thể chọn `transferFunds` mà không chọn `formatMoney`. Một lần thực thi không khớp quy tắc thì advice tương ứng không được áp dụng.

Thiết kế pointcut tốt dựa trên **ý nghĩa và ranh giới ổn định**, không dựa bừa vào mẫu tên quá rộng như mọi phương thức trong ứng dụng. Khi đổi tên/gom module, hãy kiểm tra tập được chọn thực tế, kể cả phương thức vô tình lọt vào hoặc bị bỏ sót.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-advice">Advice: Hành vi bổ sung tại điểm được chọn</a>

<details>
<summary>Xem chi tiết</summary>

**Advice** là hành vi bổ sung tại join point được chọn. Tùy mô hình, advice chạy trước, sau hoặc bao quanh hành vi chính. Trong ví dụ chuyển tiền, advice ghi thời điểm bắt đầu và thời lượng, còn target chịu trách nhiệm chuyển tiền; nhờ tách hai vai trò, ta kiểm tra riêng phép đo và phạm vi áp dụng.

Advice không mặc nhiên vô hại. Nó có thể phát sinh ngoại lệ, thay đổi kết quả hoặc — với `around` — bỏ qua target. Vì vậy policy về lỗi, dữ liệu nhạy cảm và số lần target thực thi cần được xem là hành vi có thể kiểm thử chứ không phải chỉ là “trang trí”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-target">Target: Thành phần hoặc hành vi gốc</a>

<details>
<summary>Xem chi tiết</summary>

**Target** là thành phần hoặc hành vi gốc được áp dụng chính sách xuyên suốt, ví dụ đối tượng xử lý chuyển tiền. Target không cần biết aspect nào đang đo thời gian của mình. Trong mô hình proxy, bên gọi có thể cầm đối tượng trung gian, còn phía sau mới là target thực hiện công việc chính.

Ranh giới trách nhiệm cần được giữ: nếu không có aspect thì target vẫn phải bảo vệ bất biến nghiệp vụ của chính nó. Việc ghi audit bên ngoài không được xem là thay thế kiểm tra số dư hoặc tính nhất quán bên trong target.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-weaving">Weaving: Ghép aspect với target</a>

<details>
<summary>Xem chi tiết</summary>

**Weaving** là quá trình ghép aspect với target để hình thành hành vi thực thi cuối cùng. Thời điểm và cơ chế không cố định: AspectJ có thể ghép khi biên dịch hoặc nạp lớp, còn Spring AOP thường đưa advice vào đường gọi qua proxy khi chạy. Vì thế, weaving không đồng nghĩa với một lệnh biên dịch duy nhất.

Ở mức quan sát, hãy hỏi “lời gọi nào đi qua hành vi bổ sung?” thay vì chỉ hỏi “aspect được khai báo ở đâu?”. Một khai báo đúng cú pháp nhưng không nằm trên đường thực thi có thể không tạo ra hiệu ứng mong đợi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-advice-kinds">Các vị trí thực thi của before, after và around advice</a>

<details>
<summary>Xem chi tiết</summary>

**Before** chạy trước điểm được chọn; nếu nó trả về bình thường thì bản thân nó không chọn bỏ qua target, nhưng có thể ngăn bằng cách ném lỗi. **After returning** chỉ chạy sau khi target hoàn tất bình thường; **after throwing** dùng đường lỗi phù hợp; **after/finally** dùng lúc thoát theo hai đường. Đó là các vị trí khác nhau, không phải các tên đồng nghĩa của “chạy sau”.

**Around** bao cả phần thực thi còn lại và thường nhận một thao tác kiểu `proceed`. Nó có thể gọi tiếp, không gọi tiếp, hoặc xử lý kết quả/lỗi. Dùng around khi thực sự cần quyền này; đo kết quả bình thường có thể chỉ cần after returning để tránh rủi ro quên chạy tiếp. Xem [Spring AOP Concepts](https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-effective-execution">Luồng thực thi hiệu lực, kết quả và ngoại lệ khi áp dụng advice</a>

<details>
<summary>Xem chi tiết</summary>

Với một around advice đo thời gian, đường thành công dễ nhìn như sau:

```text
caller → around: start
       → proceed → target transferFunds → result
       → around: record duration → caller receives result
```

Nếu target ném lỗi, đoạn đo đặt **chỉ sau `proceed`** có thể không chạy; dùng luồng dọn dẹp tương đương `finally` khi cần đo cả lần thất bại. Nếu around **không `proceed`**, lời gọi không vào target và có thể trả một kết quả thay thế hoặc ném lỗi; hãy nêu rõ đó là thay đổi ngữ nghĩa. Nhiều advice còn có thứ tự ưu tiên và cấu trúc lồng nhau; không nên giả định mọi `after` đều nằm ở cuối chuỗi.

Trong các mô hình hỗ trợ gọi `proceed` nhiều lần, around advice còn có thể khiến target chạy lặp lại. Điều đó đặc biệt nguy hiểm với `transferFunds`: hai lần thực hiện có thể dẫn tới hai lần chuyển tiền. Vì vậy, **số lần gọi tiếp** phải là một phần của hợp đồng hành vi, không phải chi tiết vô hại.

| Số lần gọi `proceed` | Việc chuyển tiền thực tế | Rủi ro cần kiểm chứng |
| --- | --- | --- |
| 0 | Không chạy thao tác đích; advice tự trả kết quả hoặc lỗi | Bên gọi không được nhầm “đã bỏ qua” với “đã chuyển tiền” |
| 1 | Chạy thao tác đích một lần | Giữ đúng kết quả, lỗi và thời lượng |
| 2 | Có thể chạy thao tác đích hai lần | Hai lần chuyển tiền hoặc hai tác động bên ngoài, không chỉ hai dòng log |

Đây là **khả năng của advice bao quanh**, không phải lời khuyên tự động thử lại thanh toán. Việc thử lại phải có quy tắc an toàn nghiệp vụ riêng; nếu chỉ để đo thời gian thì thông thường cần gọi tiếp đúng một lần và giữ nguyên kết quả hoặc lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-selection-example">Ví dụ nối concern, aspect, join point, pointcut và advice</a>

<details>
<summary>Xem chi tiết</summary>

Xét `transferFunds(100)`: concern là đo thời gian, aspect là `ServiceTiming`, join point là **lần thực thi** xử lý chuyển tiền có thể quan sát được, pointcut chọn các thao tác dịch vụ thanh toán và advice ghi thời gian quanh lần thực thi ấy. Target vẫn nhận yêu cầu và trả kết quả chuyển tiền. Thay `transferFunds` bằng `renderPage` ở module khác thì pointcut không nên khớp.

```text
transferFunds(100):  pointcut match → timing → target → timing result
renderPage():       no match      → target only
```

Kiểm thử phải xem **cả trường hợp trúng và trượt pointcut**, cùng đường ném lỗi; chỉ thấy log xuất hiện một lần chưa đủ chứng minh phạm vi chọn chính xác.

</details>

- [Quay lại đầu trang](#back-to-top)
