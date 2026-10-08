<a id="back-to-top"></a>

# Nền tảng cộng tác mã nguồn trên Bitbucket Cloud

## Menu
- [Bitbucket Cloud: Khái niệm và vai trò trong cộng tác mã nguồn](#bitbucket-cloud-purpose)
- [Nhu cầu lưu trữ và cộng tác mã nguồn trong nhóm](#why-hosted-source-collaboration)
- [Git và Bitbucket Cloud: Phân định trách nhiệm](#git-versus-bitbucket-cloud)
- [Cấu trúc workspace, project và repository trên Bitbucket Cloud](#workspace-project-repository-model)
- [Thành viên, quản trị viên và kho mã dùng chung](#shared-source-members-and-administrators)
- [Pull Request: Khái niệm, tác giả, reviewer và nhánh đích](#pull-request-foundation)
- [Hành trình thay đổi mã nguồn trong Bitbucket Cloud](#bitbucket-contribution-overview)

## <a id="bitbucket-cloud-purpose">Bitbucket Cloud: Khái niệm và vai trò trong cộng tác mã nguồn</a>

<details>
<summary>Xem chi tiết</summary>

**Bitbucket Cloud** là dịch vụ Atlassian lưu trữ repository Git và cung cấp nơi để nhóm đề xuất, thảo luận, đánh giá rồi tiếp nhận mã nguồn. Khi hai lập trình viên cùng sửa nghiệp vụ tính hóa đơn, một kho Git chỉ giúp lưu lịch sử; nhóm vẫn cần địa điểm chung, quyền phù hợp và bằng chứng quyết định thay đổi nào được chấp nhận.

Module này đi từ tổ chức workspace đến quyền, pull request, review và kiểm soát hợp nhất. Bitbucket Cloud **không thay thế Git** và việc mã đã xuất hiện trên dịch vụ không có nghĩa đã được review. Trong ví dụ xuyên bài, nhóm *Orchid* duy trì repository `invoice-api`, An sửa thuế và Bình phụ trách kiểm tra tác động làm tròn.

**Tự thực hành:** viết ra phần nào Git có thể làm trên máy An và phần nào cần thỏa thuận nhóm hoặc khả năng cộng tác của Bitbucket. Sản phẩm tạo nơi phối hợp; chất lượng của quyết định vẫn thuộc con người.

### Tài liệu tham khảo
- [Atlassian — What is a workspace?](https://support.atlassian.com/bitbucket-cloud/docs/what-is-a-workspace/)
- [Atlassian — Use pull requests for code review](https://support.atlassian.com/bitbucket-cloud/docs/use-pull-requests-for-code-review/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="why-hosted-source-collaboration">Nhu cầu lưu trữ và cộng tác mã nguồn trong nhóm</a>

<details>
<summary>Xem chi tiết</summary>

Không có điểm cộng tác chung, An có thể gửi `invoice-final.zip` trong chat còn Bình giữ `invoice-final-2.zip`. Cả hai bản đều có vẻ hợp lệ nhưng nhóm không biết ai đã đánh giá, thay đổi nào được chọn và liệu người mới có quyền nhìn thấy nguồn hay không. **Lưu trữ có quản trị** giải quyết vấn đề phối hợp, không chỉ vấn đề dung lượng ổ đĩa.

Bitbucket cung cấp repository để chia sẻ lịch sử, pull request để trình bày khác biệt và bình luận/tác vụ để xử lý ý kiến. Phân quyền và chính sách nhánh giúp giới hạn người được tác động đến nguồn chính. Các lớp này bổ sung cho nhau: repository có lịch sử nhưng thiếu review có thể chứa một lỗi nghiệp vụ; review tốt nhưng cấp quyền quá rộng vẫn để lại rủi ro.

**Bằng chứng kiểm tra được:** với một thay đổi đã tiếp nhận, nhóm tìm được người đề xuất, người review, nội dung chênh lệch và điều kiện hợp nhất. **Thực hành:** mô tả hậu quả nếu bỏ từng lớp *lịch sử — review — quyền*.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="git-versus-bitbucket-cloud">Git và Bitbucket Cloud: Phân định trách nhiệm</a>

<details>
<summary>Xem chi tiết</summary>

**Git** là hệ thống kiểm soát phiên bản phân tán; commit, nhánh và trao đổi lịch sử là khả năng của Git. **Bitbucket Cloud** lưu repository Git và gắn quanh chúng quy trình cộng tác như pull request, người đánh giá, quyền thành viên và kiểm tra trước khi hợp nhất.

Ví dụ An có thể ghi nhận thay đổi `taxRate` vào lịch sử cục bộ mà không cần đăng nhập Bitbucket. Khi An muốn nhóm tiếp nhận thay đổi, Bitbucket cung cấp bối cảnh đề xuất và phương tiện review. Nút *Merge* trên giao diện đại diện cho quyết định tích hợp vào đích; cách Git xây dựng lịch sử khi merge thuộc module Git, không phải nội dung triển khai ở đây.

**Thử phân loại:** `tạo commit`, `gắn reviewer`, `đọc diff trong pull request`, `thiết lập quyền repository`. Hai mục đầu/cuối thuộc các lớp khác nhau dù cùng một dự án. Để học sâu cơ chế, tham khảo module Git; bài này chỉ giải thích cầu nối.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="workspace-project-repository-model">Cấu trúc workspace, project và repository trên Bitbucket Cloud</a>

<details>
<summary>Xem chi tiết</summary>

Bitbucket Cloud tổ chức nguồn theo **workspace → project → repository**. Workspace là không gian thuộc nhóm/tổ chức để tạo và truy cập kho; project gom các repository có liên quan để nhóm dễ quản trị; repository giữ nội dung và lịch sử Git của một dự án cụ thể. Một project có thể chứa nhiều repository, nhưng không có nghĩa mọi repository được gộp thành một lịch sử.

Nhóm Orchid có workspace `orchid-team`, project `Billing`, và repository `invoice-api` cùng `billing-docs`. Trong URL Bitbucket, workspace ID và repository slug xác định kho; tên project phục vụ tổ chức và quyền. Kho ở project nào ảnh hưởng đến quyền cấp ở project đó, điều sẽ được học tại chương 2.

**Vẽ lại mô hình:** một ô workspace chứa một ô project, trong project có hai repository. Đừng đặt nhánh hay pull request ngang cấp với project: chúng là đối tượng hoạt động gắn với repository/luồng đóng góp.

### Tài liệu tham khảo
- [Atlassian — What is a workspace?](https://support.atlassian.com/bitbucket-cloud/docs/what-is-a-workspace/)
- [Atlassian — Create a project](https://support.atlassian.com/bitbucket-cloud/docs/create-a-project/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="shared-source-members-and-administrators">Thành viên, quản trị viên và kho mã dùng chung</a>

<details>
<summary>Xem chi tiết</summary>

Một repository dùng chung vẫn cần phân biệt người **đóng góp**, người **đánh giá** và người **quản trị**. Thành viên được cấp quyền phù hợp có thể xem hoặc cập nhật nội dung; reviewer kiểm tra đề xuất; quản trị viên duy trì cấu hình quyền, phạm vi và quy tắc cộng tác. Một người có thể đảm nhiệm nhiều vai trò, nhưng chúng không tự tương đương nhau.

Ở Orchid, An gửi sửa thuế; Bình được yêu cầu review; Mai chịu trách nhiệm quản trị project. Mai có thể quản lý một số quyền mà An không có, nhưng quyền quản trị không chứng minh Mai đã đánh giá nghiệp vụ. Một thành viên workspace không nhất thiết có quyền truy cập tất cả repository riêng tư nếu chưa được cấp quyền qua nhóm, project hoặc repository.

**Bài tập:** lập bảng An/Bình/Mai theo *cần đọc nguồn? cần đề xuất? cần xét duyệt? cần sửa quyền?* và chọn nguyên tắc ít quyền nhất. Chương sau làm rõ phạm vi quyền thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pull-request-foundation">Pull Request: Khái niệm, tác giả, reviewer và nhánh đích</a>

<details>
<summary>Xem chi tiết</summary>

**Pull request (PR)** là đối tượng cộng tác trong Bitbucket mô tả đề xuất lấy thay đổi từ một nhánh/repository **nguồn** đưa vào nhánh/repository **đích**. Tác giả (author) trình bày mục đích; reviewer đọc khác biệt và phản hồi; nhánh đích là trạng thái nhóm muốn cập nhật sau khi đáp ứng yêu cầu.

An làm việc ở nhánh riêng để chỉnh `invoice-api` và mở PR hướng tới nhánh chung. Bình được chọn làm reviewer để kiểm tra hóa đơn có số lẻ. Một PR có tiêu đề, mô tả, danh sách thay đổi và trao đổi — nhưng chỉ **tồn tại PR** không phải bằng chứng đã được chấp thuận.

**Thực hành:** điền vào câu “An đề xuất thay đổi ___ từ ___ vào ___, vì ___; Bình cần kiểm tra ___”. Các chương 3–5 sẽ mở rộng lần lượt quá trình đề xuất, review và kiểm soát việc merge.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bitbucket-contribution-overview">Hành trình thay đổi mã nguồn trong Bitbucket Cloud</a>

<details>
<summary>Xem chi tiết</summary>

Hành trình đóng góp kết nối các khái niệm vừa học: thành viên có quyền truy cập repository, chuẩn bị một thay đổi trong Git, chia sẻ để Bitbucket tạo PR, nhận phản hồi, giải quyết tác vụ, thỏa điều kiện merge và cuối cùng cập nhật nhánh đích. Mỗi bước có người chịu trách nhiệm và dấu vết kiểm chứng khác nhau.

Trong câu chuyện Orchid, An không nên gọi sửa thuế là “đã vào sản phẩm” chỉ vì đã chia sẻ PR. Bình còn có thể yêu cầu bằng chứng về cách làm tròn. Nếu check chỉ cảnh báo và nhóm không có chính sách chặn bắt buộc, vẫn cần quyết định của con người; không được nhầm cảnh báo với chấp thuận.

**Bản đồ học:** ghi sáu cột *truy cập — đề xuất — review — tác vụ — kiểm tra — merge*; đánh dấu chương sẽ giải thích kỹ mỗi cột. Kết quả chương đầu là hiểu nơi thực hiện quyết định, chưa phải biết cấu hình từng màn hình.

</details>

- [Quay lại đầu trang](#back-to-top)
