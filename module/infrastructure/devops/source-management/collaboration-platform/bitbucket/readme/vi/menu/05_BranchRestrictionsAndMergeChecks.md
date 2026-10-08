<a id="back-to-top"></a>

# Bảo vệ nhánh và kiểm tra trước hợp nhất

## Menu
- [Mục đích bảo vệ các nhánh quan trọng](#why-protect-important-branches)
- [Quyền ghi lên nhánh so với quyền hợp nhất](#branch-write-versus-merge-permissions)
- [Quy tắc cấp project/repository và mẫu tên nhánh](#project-repository-and-branch-pattern-rules)
- [Merge Checks: Các điều kiện kiểm tra trước khi hợp nhất](#merge-check-concepts-and-examples)
- [Cảnh báo merge check và chặn hợp nhất trên Premium](#advisory-versus-premium-blocking)
- [Tình huống: cùng một merge check chưa đạt trên gói thường và Premium](#merge-check-plan-scenario)
- [Quy tắc chồng lấn và cách tìm nguyên nhân bị chặn](#overlapping-restrictions-and-diagnostics)

## <a id="why-protect-important-branches">Mục đích bảo vệ các nhánh quan trọng</a>

<details>
<summary>Xem chi tiết</summary>

Một nhánh dùng làm nguồn chung cần được bảo vệ khỏi việc sửa nhầm hoặc tiếp nhận thay đổi thiếu kiểm tra. Trong Bitbucket Cloud, **branch restrictions** giới hạn một số hành động đối với nhánh theo người hoặc nhóm được phép; **merge checks** đánh giá điều kiện liên quan đến PR. Chúng giải quyết hai câu hỏi khác nhau: *ai được thao tác* và *đề xuất đã đạt yêu cầu chưa*.

Orchid chọn nhánh `main` của `invoice-api` làm nguồn công nhận. An có thể phát triển trên nhánh công việc nhưng nhóm muốn Bình review phép làm tròn trước khi đưa sửa thuế vào `main`. Đặt quy tắc phù hợp giảm nguy cơ ghi đè vô ý và minh bạch hóa quyết định hợp nhất; không có quy tắc nào bảo đảm nghiệp vụ luôn đúng.

**Thực hành:** nêu một rủi ro khi ai cũng được ghi thẳng lên `main`, một rủi ro khi người được merge bỏ qua tác vụ review, rồi chỉ ra mỗi rủi ro tương ứng lớp kiểm soát nào.

### Tài liệu tham khảo
- [Atlassian — Configure a project's branch restrictions](https://support.atlassian.com/bitbucket-cloud/docs/configure-a-projects-branch-restrictions/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="branch-write-versus-merge-permissions">Quyền ghi lên nhánh so với quyền hợp nhất</a>

<details>
<summary>Xem chi tiết</summary>

**Quyền ghi lên nhánh** liên quan đến khả năng đẩy thay đổi trực tiếp; **quyền hợp nhất** liên quan đến việc tích hợp đề xuất vào nhánh đích. Hai quyền có thể được giới hạn khác nhau theo cấu hình branch restrictions. Có quyền Write ở repository không bảo đảm được phép cập nhật trực tiếp mọi nhánh quan trọng hoặc merge bất cứ PR nào.

Ở Orchid, An có quyền đóng góp vào repository nhưng quy tắc nhánh đích chỉ cho một số người được ghi/hợp nhất. Mai cần xem cả quyền project/repository lẫn branch restriction hiện hữu. Nếu An gặp từ chối khi merge, việc chỉ cấp thêm Write có thể không giải quyết được và làm tăng quyền không cần thiết.

**Bảng đối chiếu:** thử ba hành động *đọc diff*, *ghi trực tiếp lên nhánh quan trọng*, *merge PR vào nhánh đó*. Ghi quyền và quy tắc cần xem xét, rồi lý giải vì sao một thông báo “permission denied” không đủ để xác định loại quyền thiếu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="project-repository-and-branch-pattern-rules">Quy tắc cấp project/repository và mẫu tên nhánh</a>

<details>
<summary>Xem chi tiết</summary>

Quy tắc có thể được đặt ở **repository** hoặc **project** để áp dụng thống nhất cho nhiều repository. Khi dùng **mẫu tên nhánh (branch pattern)**, quy tắc có thể áp dụng cho nhiều nhánh có tên phù hợp mà không cần cấu hình riêng từng nhánh. Cách này hữu ích cho những nhánh như `release/*`, nhưng cần xác định mẫu nào thực sự khớp tên nhánh đích.

Orchid có project Billing với `invoice-api` và `billing-docs`. Một giới hạn cấp project cho `main` có thể ảnh hưởng cả hai repository, dù mục tiêu ban đầu chỉ là API. Repository-level rules bổ sung thêm ngữ cảnh. Người quản trị phải kiểm tra danh sách các quy tắc đang áp dụng chứ không nhìn một thiết lập đơn lẻ.

**Thực hành:** lập bảng *quy tắc — phạm vi — mẫu nhánh — người được phép*. Với PR vào `main` và `release/2026-10`, xác định quy tắc nào cần kiểm tra; tránh tự suy diễn kết quả khi chưa biết cách đối chiếu pattern.

### Tài liệu tham khảo
- [Atlassian — Configure a project's branch restrictions](https://support.atlassian.com/bitbucket-cloud/docs/configure-a-projects-branch-restrictions/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="merge-check-concepts-and-examples">Merge Checks: Các điều kiện kiểm tra trước khi hợp nhất</a>

<details>
<summary>Xem chi tiết</summary>

**Merge check** là điều kiện đánh giá trước khi PR được hợp nhất, chẳng hạn số lượng người chấp thuận, tình trạng **Changes requested**, tác vụ chưa giải quyết hoặc trạng thái kiểm tra tự động. Nó tạo tín hiệu nhóm đã đạt tiêu chí chất lượng nào, không tự thay thế việc hiểu yêu cầu hay tìm lỗi nghiệp vụ.

Trong Orchid, nhóm muốn hai điều: có approval của Bình và không còn task kiểm thử hóa đơn số lẻ. Nếu PR chỉ có một approval nhưng task chưa hoàn tất, check có thể báo chưa đạt. Một loại check dựa trên kết quả build/test chỉ sử dụng **trạng thái kết quả** do hệ thống khác cung cấp; việc viết hoặc chạy pipeline thuộc module CI/CD chứ không thuộc phần này.

**Quan sát:** trang PR hiển thị từng check đạt/chưa đạt để người review biết công việc tồn. **Thực hành:** đề xuất hai merge checks phù hợp cho lỗi tính tiền và giải thích bằng chứng người review cần xác minh ngoài trạng thái check.

### Tài liệu tham khảo
- [Atlassian — Suggest or require checks before a merge](https://support.atlassian.com/bitbucket-cloud/docs/suggest-or-require-checks-before-a-merge/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advisory-versus-premium-blocking">Cảnh báo merge check và chặn hợp nhất trên Premium</a>

<details>
<summary>Xem chi tiết</summary>

Một nhầm lẫn nguy hiểm là cho rằng có merge check thì hệ thống luôn chặn merge. Atlassian phân biệt **cảnh báo/khuyến nghị** và **chặn bắt buộc**: trên gói Free/Standard, các merge checks thông thường có thể hiện điều kiện chưa đạt nhưng người có quyền vẫn có thể merge. Trên **Premium**, khi cấu hình **Prevent a merge with unresolved merge checks**, Bitbucket mới ngăn merge theo các check được áp dụng còn chưa giải quyết.

Việc mua Premium **không tự kích hoạt** cơ chế chặn nếu chưa thiết lập. Ngược lại, branch restrictions kiểm soát quyền có thể là rào cản riêng, nên ngay cả khi check chỉ cảnh báo cũng không có nghĩa mọi người đều được merge. Mai phải xác minh gói dịch vụ, cấu hình yêu cầu, quy tắc trên nhánh và quyền thực tế.

**Thực hành:** với PR của An thiếu approval, hãy trả lời hai câu riêng: *giao diện cảnh báo gì?* và *hệ thống thực sự ngăn thao tác merge hay không?* — không dùng câu trả lời cho câu trước để suy ra câu sau.

### Tài liệu tham khảo
- [Atlassian — Suggest or require checks before a merge](https://support.atlassian.com/bitbucket-cloud/docs/suggest-or-require-checks-before-a-merge/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="merge-check-plan-scenario">Tình huống: cùng một merge check chưa đạt trên gói thường và Premium</a>

<details>
<summary>Xem chi tiết</summary>

Cùng một PR Orchid thiếu task “kiểm tra hóa đơn âm”, giả sử nhóm đã chọn merge check yêu cầu giải quyết hết task. **Trên Standard**, hệ thống có thể cảnh báo còn task; người đủ quyền vẫn có thể merge nếu không có giới hạn khác chặn. Đây là lý do nhóm phải duy trì trách nhiệm review và chính sách không bỏ qua cảnh báo.

**Trên Premium**, nếu Mai đã bật cơ chế ngăn hợp nhất khi check còn tồn và check áp dụng cho nhánh đích, nút merge bị chặn cho đến khi task được xử lý. Nếu chỉ nâng cấp nhưng không bật chế độ ngăn, hành vi có thể vẫn chỉ là cảnh báo. Thông báo cụ thể có thể thay đổi theo giao diện; phải đối chiếu cài đặt hiện hành.

**Bài tập tình huống:** lập hai cột Standard/Premium với ba dòng *task chưa giải quyết, cảnh báo, khả năng merge của người có quyền*. Viết thêm điều kiện **Premium đã cấu hình enforcement**; nếu thiếu điều kiện này, bảng sẽ mô tả sai sản phẩm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overlapping-restrictions-and-diagnostics">Quy tắc chồng lấn và cách tìm nguyên nhân bị chặn</a>

<details>
<summary>Xem chi tiết</summary>

Khi nhiều quy tắc cùng nhắm một nhánh, người dùng có thể thấy PR bị chặn dù điều kiện đang nhìn ở một màn hình dường như đã đạt. Nguyên nhân có thể nằm ở **quyền repository/project**, **branch restriction**, **merge check** hoặc một check/tác vụ còn mở. Không nên giả định “cấp Admin” hoặc “tắt toàn bộ rule” là giải pháp hợp lý.

Quy trình chẩn đoán của Mai bắt đầu bằng việc ghi nhận nhánh đích, người thực hiện và thông báo đang hiện. Tiếp đó xem quyền hữu hiệu, những quy tắc phù hợp từ project và repository, trạng thái reviewer/tasks và thiết lập enforcement của gói dịch vụ. Điều này phân biệt **không được phép merge** với **được phép nhưng check chưa đạt** và **chỉ nhận cảnh báo**.

**Thực hành:** PR đã đủ approval nhưng vẫn bị từ chối. Đề xuất ít nhất ba giả thuyết khác nhau và loại bằng chứng cần thu để bác bỏ từng giả thuyết, tránh sửa cấu hình trước khi hiểu vấn đề.

</details>

- [Quay lại đầu trang](#back-to-top)
