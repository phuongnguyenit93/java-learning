<a id="back-to-top"></a>

# Azure Repos và mô hình cộng tác trên Git

## Menu
- [Azure DevOps và Azure Repos: khái niệm, phạm vi dịch vụ](#azure-devops-vs-azure-repos)
- [Vai trò của nền tảng cộng tác bổ sung cho Git](#why-hosted-collaboration)
- [Ranh giới giữa lịch sử Git và quản trị cộng tác Azure Repos](#git-versus-azure-repos-ownership)
- [Organization, project, repository và contributor: thuật ngữ nền](#organization-project-repo-vocabulary)
- [Pull request: khái niệm, mục đích và nhánh nguồn/đích](#what-is-a-pull-request)
- [Reviewer, branch policy và mô hình điều kiện hợp nhất PR](#reviewer-policy-and-merge-relation)
- [Ranh giới Azure Repos với Azure Boards và Azure Pipelines](#boards-and-pipelines-boundary)
- [Dấu vết và trạng thái cộng tác của một thay đổi trên Azure Repos](#trace-collaboration-surface)

## <a id="azure-devops-vs-azure-repos">Azure DevOps và Azure Repos: khái niệm, phạm vi dịch vụ</a>

<details>
<summary>Xem chi tiết</summary>

**Azure DevOps** là bộ dịch vụ cộng tác phát triển phần mềm; **Azure Repos** là thành phần quản lý mã nguồn, hỗ trợ Git repositories (và TFVC ở những bối cảnh dùng hệ thống cũ). Chương này tập trung vào Azure Repos Git: nơi lưu repo, phân quyền, mở pull request (PR), xem xét thay đổi và áp dụng branch policy. Không đồng nhất Azure DevOps với Azure Cloud subscription: quyền trong tổ chức DevOps không tự cấp quyền tài nguyên Azure.

Một nhóm có thể lưu lịch sử bằng Git local, nhưng để hai người cùng đề xuất sửa đổi lên `main` và có bằng chứng duyệt thay đổi, họ cần thêm một bề mặt cộng tác như Azure Repos.

**Lộ trình học:** trước hết phân biệt Azure DevOps, Azure Repos và Git, nhận diện organization → project → repository, người đóng góp và pull request (PR). Sau đó học cách tổ chức repository, cấp access level và security permissions, đóng góp từ nhánh chung hoặc fork; tiếp theo review PR, kiểm tra branch policies/quyền bypass và hoàn tất PR. Chương cuối nối toàn bộ hành trình từ sửa lỗi đến nguồn chung được chấp nhận. Azure Boards và Pipelines có vai trò liên quan nhưng cách quản lý backlog/build/deploy thuộc bài học riêng.

### Tài liệu tham khảo

- [Microsoft Learn — Azure Repos, Git và TFVC](https://learn.microsoft.com/en-us/azure/devops/repos/get-started/what-is-repos?view=azure-devops)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="why-hosted-collaboration">Vai trò của nền tảng cộng tác bổ sung cho Git</a>

<details>
<summary>Xem chi tiết</summary>

Git ghi commit và đồng bộ object, nhưng Git **không tự trả lời** ai có quyền đọc repo riêng tư, ai phải duyệt sửa đổi hoặc điều kiện kiểm tra nào phải đạt trước khi merge. Azure Repos bổ sung repository hosting, nhóm bảo mật, PR discussions, reviewer votes và branch policies để quyết định thay đổi nào được nhận vào nhánh chung.

Ví dụ hai kỹ sư cùng sửa quy tắc tính tiền: chỉ thấy hai commit Git không chứng minh có người kiểm tra trường hợp hoàn tiền. PR mô tả mục đích, diff và ý kiến reviewer cung cấp dấu vết quyết định. Việc có PR không thay thế bài test hay trách nhiệm của người review.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="git-versus-azure-repos-ownership">Ranh giới giữa lịch sử Git và quản trị cộng tác Azure Repos</a>

<details>
<summary>Xem chi tiết</summary>

**Git sở hữu cơ chế:** commit ID, nhánh/ref, `fetch`, `push`, merge, conflict và rebase. **Azure Repos sở hữu quy trình lưu trữ:** cấp Read/Contribute, hiển thị PR, phiếu đánh giá, kiểm tra nhánh đích và ghi trạng thái PR. Các lệnh Git vẫn chạy với repo được Azure Repos lưu trữ; dịch vụ nhận/từ chối push theo quyền và chính sách.

Ví dụ một push bị chặn có thể vì thiếu quyền hoặc branch policy dù commit trên máy vẫn hợp lệ. Chẩn đoán đầu tiên: Git local có commit chưa, URL remote đúng không, rồi xem quyền/policy trên Azure Repos. Cơ chế Git chuyên sâu thuộc module Git.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="organization-project-repo-vocabulary">Organization, project, repository và contributor: thuật ngữ nền</a>

<details>
<summary>Xem chi tiết</summary>

**Organization** là đơn vị quản trị thành viên và dịch vụ Azure DevOps; **project** gom nhóm sản phẩm/nhóm làm việc và cấp ranh giới quản trị; **Git repository** là nơi lưu lịch sử một cây mã nguồn trong project; **contributor** là người tham gia đề xuất hoặc cập nhật mã tùy quyền. Một project có thể chứa nhiều Git repo.

Theo ví dụ giả định xuyên module `RetailCo → Checkout → checkout-api`, Alice sửa nhánh trong `checkout-api` và Bob xem xét PR. Đây là các tên minh họa, không phải tổ chức thật; repository không đồng nghĩa project, và người thuộc project chưa chắc có mọi quyền thao tác trên mọi repository hoặc branch.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="what-is-a-pull-request">Pull request: khái niệm, mục đích và nhánh nguồn/đích</a>

<details>
<summary>Xem chi tiết</summary>

**Pull request (PR)** là yêu cầu xem xét và hợp nhất thay đổi từ **source branch** vào **target branch**, có thể từ cùng repo hoặc fork được hỗ trợ. PR không phải một loại object trong Git: nó là bản ghi cộng tác trên Azure Repos chứa mô tả, diff, reviewers, threads và trạng thái kiểm tra.

Ví dụ source `feature/tax-fix` nhắm target `main`. Nếu chọn nhầm target `release/1.x`, quy tắc áp dụng và đích lịch sử đều thay đổi. Vì vậy trước khi gửi PR phải kiểm tra tên repo và cả hai branch, không chỉ xem tiêu đề PR.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reviewer-policy-and-merge-relation">Reviewer, branch policy và mô hình điều kiện hợp nhất PR</a>

<details>
<summary>Xem chi tiết</summary>

**Reviewer** kiểm tra thay đổi và đưa phiếu phản hồi; **branch policy** là tập điều kiện gắn với nhánh **đích** như số người duyệt, bình luận cần được giải quyết và kết quả validation; **completion** là thao tác đưa thay đổi vào đích khi quyền và điều kiện cho phép. Phiếu Approve không tự làm PR hoàn tất.

Minh họa PR có một reviewer đồng ý nhưng policy yêu cầu hai người thì chưa đủ; nếu validation thất bại thì vẫn bị chặn dù đủ phiếu. Cần đọc PR Overview/Policies và phân biệt quyền người dùng với tình trạng các checks.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boards-and-pipelines-boundary">Ranh giới Azure Repos với Azure Boards và Azure Pipelines</a>

<details>
<summary>Xem chi tiết</summary>

Azure DevOps gồm các dịch vụ khác: **Azure Boards** quản lý work items như bug/task; **Azure Pipelines** có thể chạy build/test và đưa kết quả validation về PR. Azure Repos chỉ gắn work item hoặc tiêu thụ kết quả trạng thái để đánh giá điều kiện merge, không tự mô tả cách cấu hình pipeline hay quy trình quản lý sprint.

Ví dụ PR sửa lỗi số thuế liên kết work item `Bug 104` để người duyệt biết vì sao phải sửa; check “build validation failed” chỉ dẫn đến kết quả pipeline, chi tiết YAML và agent thuộc bài học CI/CD.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="trace-collaboration-surface">Dấu vết và trạng thái cộng tác của một thay đổi trên Azure Repos</a>

<details>
<summary>Xem chi tiết</summary>

Dấu vết của một thay đổi thường gồm: repo và source/target branch, ID/tác giả PR, các commit được đề xuất, người review và phiếu, thread đã/ chưa giải quyết, kết quả policies/checks và trạng thái **Active, Abandoned hoặc Completed**. Từng trường trả lời câu hỏi khác nhau về quyền, nội dung, trách nhiệm và tiến độ.

Tình huống kiểm chứng: Alice push branch rồi mở PR; Bob Approve, nhưng policy kiểm tra chưa đạt. Trên trang PR sẽ còn trạng thái chưa thể complete; `git log` local chỉ chứng minh commit tồn tại, không chứng minh PR đã duyệt/hợp nhất.

</details>

- [Quay lại đầu trang](#back-to-top)
