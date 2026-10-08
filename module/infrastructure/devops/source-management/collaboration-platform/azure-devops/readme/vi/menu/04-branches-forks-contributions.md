<a id="back-to-top"></a>

# Đường đóng góp qua nhánh chung và fork

## Menu
- [Hai đường đóng góp: nhánh trong repository chung và fork](#shared-branch-versus-fork)
- [Fork là repository riêng và quan hệ với nguồn upstream](#fork-origin-upstream)
- [Quyền của fork và chính sách PR tại repository đích](#fork-permissions-and-policies)
- [Repository/nhánh nguồn và repository/nhánh đích của PR](#pull-request-source-target)
- [Đề xuất PR có mục đích, mô tả và bối cảnh thay đổi](#create-contribution-request)
- [Draft PR và trạng thái sẵn sàng review](#draft-vs-ready-pull-request)
- [Bằng chứng tác giả, quyền và nhánh đích của đường đóng góp](#verify-contribution-path)

## <a id="shared-branch-versus-fork">Hai đường đóng góp: nhánh trong repository chung và fork</a>

<details>
<summary>Xem chi tiết</summary>

Hai cách đóng góp: **nhánh trong repository chung** khi người viết có quyền tạo/push nhánh ở repo gốc; hoặc **fork** là repo riêng khi muốn tách quyền và vòng làm việc. Cả hai đều có thể gửi PR tới nhánh đích theo quyền sản phẩm, nhưng fork không phải chỉ một branch có tên khác.

Ví dụ đối tác không nên có Contribute lên repo nội bộ: đội có thể cấp quyền đọc/đóng góp qua fork theo chính sách tổ chức, rồi reviewer hợp nhất vào repo gốc bằng PR. Không chọn fork chỉ vì một lệnh Git nhanh hơn; xét boundary quản trị.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-origin-upstream">Fork là repository riêng và quan hệ với nguồn upstream</a>

<details>
<summary>Xem chi tiết</summary>

**Fork** có danh tính repository và lịch sử riêng, có quan hệ nguồn với repo **upstream**. Thay đổi trong fork không tự xuất hiện trên nhánh của repo gốc; tác giả phải đẩy branch lên fork rồi đề xuất PR vào upstream. Tên remote local `origin`/ `upstream` chỉ là quy ước cấu hình của Git clone.

Ví dụ `CheckoutFork/feature` và `Checkout/ main` là hai đối tượng repository/branch khác nhau. Khi kiểm tra màn hình PR phải xem cả source repository chứ không chỉ source branch `feature`.

### Tài liệu tham khảo

- [Microsoft Learn — fork origin upstream](https://learn.microsoft.com/en-us/azure/devops/repos/git/forks?view=azure-devops)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fork-permissions-and-policies">Quyền của fork và chính sách PR tại repository đích</a>

<details>
<summary>Xem chi tiết</summary>

Khi Azure Repos fork một repo, **permissions, policies và build pipelines của repo gốc không được tự sao chép sang fork**. Fork owner có thể quản trị fork trong quyền được cấp, nhưng khi PR nhắm `upstream/main`, policy áp dụng theo **nhánh đích** của repo gốc. Theo hướng dẫn fork của Microsoft, để **mở** PR vào upstream cần thuộc **Project Valid Users** và có **Read** ở repository đích; để **complete**, vẫn cần quyền phù hợp và các required reviewers/target policies. Fork owner không tự nhận các quyền ấy từ fork.

Ví dụ fork cho phép commit không cần reviewer không đồng nghĩa PR vào upstream được merge tự do. Kiểm tra security của **fork source**, sau đó kiểm tra target repository + branch policies tại upstream. Không xem “fork thành công” như bằng chứng quyền merge ở repo đích.

### Tài liệu tham khảo

- [Microsoft Learn — fork permissions and policies](https://learn.microsoft.com/en-us/azure/devops/repos/git/forks?view=azure-devops)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pull-request-source-target">Repository/nhánh nguồn và repository/nhánh đích của PR</a>

<details>
<summary>Xem chi tiết</summary>

PR mô tả một ánh xạ **source repository + source branch → target repository + target branch**. Với nhánh nội bộ source/target có thể cùng repo; với fork, thường là hai repo. Điều kiện reviewers/checks chủ yếu được xác định từ **target branch**, không phải tên source branch.

Ví dụ nếu source từ fork đúng nhưng target là `develop` thay vì `main`, PR có thể đi qua một policy khác. Trước khi Create PR xem phần chọn source/target, diff so với base và tên project. Sai target cần sửa theo khả năng giao diện hoặc tạo lại PR đúng; không merge trước rồi mới kiểm tra.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="create-contribution-request">Đề xuất PR có mục đích, mô tả và bối cảnh thay đổi</a>

<details>
<summary>Xem chi tiết</summary>

PR tốt giải thích **vấn đề**, **phạm vi sửa**, cách kiểm chứng, ảnh hưởng tương thích và work item liên quan nếu có. Azure Repos cho chọn source/target, tiêu đề, description, reviewers và work items; tạo PR không phải commit hay push mới. Người khác cần biết tại sao thay đổi đáng được chấp nhận, không chỉ nhìn tên `fix bug`.

Tình huống chỉnh quy tắc hoàn tiền: mô tả trước/sau, case edge, ảnh hưởng API, kết quả test và link work item; đặt người review hiểu lĩnh vực thanh toán. Không đưa credential/PII vào nội dung PR hay screenshot.

### Tài liệu tham khảo

- [Microsoft Learn — create contribution request](https://learn.microsoft.com/en-us/azure/devops/repos/git/pull-requests?view=azure-devops)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="draft-vs-ready-pull-request">Draft PR và trạng thái sẵn sàng review</a>

<details>
<summary>Xem chi tiết</summary>

**Draft PR** báo rằng tác giả đang thu thập phản hồi sớm hoặc còn hoàn thiện, không nên hiểu như tuyên bố sẵn sàng merge; **ready for review** là trạng thái tác giả mời đánh giá chính thức. Reviewer, quyền và chính sách vẫn phải kiểm tra độc lập: bật “ready” không biến failing check thành passing check.

Ví dụ Alice mở Draft PR để cho đồng nghiệp xem hướng thiết kế; khi test và mô tả đủ, Alice chuyển sang ready rồi mời reviewers. Quan sát nhãn Draft/Active trên trang PR; không dùng status Draft để lách yêu cầu quyền truy cập vào source.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="verify-contribution-path">Bằng chứng tác giả, quyền và nhánh đích của đường đóng góp</a>

<details>
<summary>Xem chi tiết</summary>

Checklist bằng chứng của đường đóng góp: tác giả, repo/branch nguồn, repo/branch đích, trạng thái Draft/Active, nhóm quyền của người tạo, và policy trên nhánh đích. Từ đây tách ba câu hỏi: đã **tạo được PR** chưa, người dùng có **quyền push source** không, PR có **đủ điều kiện complete** chưa.

Trong ví dụ fork, mở trang PR để thấy repo nguồn là fork, rồi Project settings → Repositories/Security tại fork và repo đích để so quyền. Nếu PR mở được mà complete bị chặn, đừng kết luận fork không hợp lệ; xem số approvals, checks và quyền completion trước.

</details>

- [Quay lại đầu trang](#back-to-top)
