<a id="back-to-top"></a>

# Vận dụng quy trình Azure Repos từ đầu đến cuối

## Menu
- [Sơ đồ organization, project, repository và nhóm chịu trách nhiệm](#map-org-project-repo)
- [Lựa chọn nhánh chung hoặc fork theo quyền đóng góp](#choose-contribution-path)
- [Chuyển đổi Draft PR sang trạng thái sẵn sàng review](#open-draft-and-ready-pr)
- [Theo dõi phiếu đánh giá và điều kiện branch policy](#review-and-apply-policies)
- [Hoàn tất PR và bằng chứng liên kết work item](#complete-with-work-item-trace)
- [Tình huống PR bị chặn hoàn tất và cách xác định nguyên nhân](#investigate-blocked-workflow)
- [Bằng chứng cuối quy trình: quyền, reviewer, check và trạng thái PR](#verify-end-to-end-evidence)
- [Bàn giao sang Git mechanics, branching strategy, Boards và Pipelines](#handoff-to-neighboring-modules)

## <a id="map-org-project-repo">Sơ đồ organization, project, repository và nhóm chịu trách nhiệm</a>

<details>
<summary>Xem chi tiết</summary>

Bài thực hành tổng hợp sử dụng giả lập `RetailCo → Checkout → checkout-api`. **Project Administrators** chịu trách nhiệm cấu hình dịch vụ/project, nhóm Maintainers quản trị repository theo quyền thực tế, Contributors tạo nhánh đề xuất và Reviewers duyệt mã. Tên vai trò của nhóm tùy tổ chức; không mặc định nhóm tự đặt có quyền hệ thống.

Trước thay đổi, ghi rõ organization/project/repo, nhánh `main`, quyền đọc/đóng góp được gán và các policies áp dụng. Bản đồ này giúp tránh mở PR vào repo sai hoặc cấp quyền nhóm ngoài phạm vi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="choose-contribution-path">Lựa chọn nhánh chung hoặc fork theo quyền đóng góp</a>

<details>
<summary>Xem chi tiết</summary>

Giả sử Alice thuộc Contributors của `checkout-api`: nếu có quyền tạo/push nhánh feature, dùng nhánh chung; nếu là đối tác cần cách ly thay đổi, cân nhắc **fork** theo policy tổ chức. Với fork, kiểm tra quyền repo nguồn và nhánh đích của repo upstream riêng biệt vì **fork không nhận tự động policies/permissions** từ nguồn.

Bằng chứng lựa chọn là đường source repository/source branch, quyền hợp lệ và target `checkout-api/main`, không phải tên branch đơn thuần.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="open-draft-and-ready-pr">Chuyển đổi Draft PR sang trạng thái sẵn sàng review</a>

<details>
<summary>Xem chi tiết</summary>

Alice chuẩn bị sửa logic timeout, mở **Draft PR** từ `feature/timeout` vào `main`, mô tả lý do và test dự kiến, liên kết `Bug 104`. Draft giúp xin góp ý sớm nhưng chưa tuyên bố code sẵn sàng. Khi code/test hoàn chỉnh, Alice push phiên bản mới và chuyển **Ready for review**, mời reviewer thích hợp.

Theo dõi trạng thái PR, source/target, diff và thời gian thay đổi; trước khi chuyển ready cần rà lại phạm vi sửa để không đưa credential hay output build nhạy cảm vào diff.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="review-and-apply-policies">Theo dõi phiếu đánh giá và điều kiện branch policy</a>

<details>
<summary>Xem chi tiết</summary>

Bob xem Files và test evidence, chọn **Approve**; reviewer bắt buộc Carla chọn **Wait for author** do một trường hợp lỗi chưa được giải quyết. `main` yêu cầu hai approvals, comment resolution và build validation. Vì vậy PR vẫn blocked dù có một vote tích cực; khi Alice sửa và push, phải xem policy có reset votes hay không rồi yêu cầu review lại.

Dấu vết gồm reviewer names/roles, current votes, active threads, số phiếu được tính và kết quả check. Không ghi “đã pass review” khi một required reviewer vẫn đang đợi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="complete-with-work-item-trace">Hoàn tất PR và bằng chứng liên kết work item</a>

<details>
<summary>Xem chi tiết</summary>

Khi Carla Approve, threads đã resolved và build required pass, người có quyền lựa chọn **Complete PR** (hoặc đã cấu hình auto-complete) sau khi kiểm tra merge type và target. PR đổi sang **Completed** và nhánh đích phản ánh commit tích hợp. Work item `Bug 104` vẫn được liên kết cho việc truy vết; trạng thái Bug có thể còn Active chờ xác minh triển khai.

Chứng cứ cần thu: PR ID/status, target branch, reviewer/check summary, phương thức merge, linked work item và commit trên target. Không coi trạng thái hoàn tất PR là bằng chứng deploy thành công.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="investigate-blocked-workflow">Tình huống PR bị chặn hoàn tất và cách xác định nguyên nhân</a>

<details>
<summary>Xem chi tiết</summary>

Biến thể điều tra: người tạo PR thấy Bob Approve nhưng **Complete** bị vô hiệu. Nếu Policies chỉ “Required reviewer waiting”, chuyển cho Carla; nếu “Build validation failed”, chuyển cho tác giả/pipeline owner xem kết quả; nếu “Merge conflict”, tác giả dùng Git cập nhật source; nếu “permission denied”, quản trị viên kiểm tra effective rights của người complete.

Nếu source ở fork, đừng sửa policies trên fork để mở khóa target `main`. Ghi **nguyên nhân → bằng chứng UI → người chịu trách nhiệm → thao tác an toàn**; không kết luận lỗi bằng cách chỉ thêm quyền bypass.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="verify-end-to-end-evidence">Bằng chứng cuối quy trình: quyền, reviewer, check và trạng thái PR</a>

<details>
<summary>Xem chi tiết</summary>

Bảng kiểm thực chứng cuối luồng: **identity** (organization/project/repo), **access** (Basic và quyền effective), **proposal** (source/target/Draft/Active), **review** (required reviewers/votes/threads), **policy** (blocking checks), **completion** (Completed, merge method, target commit), **traceability** (work item). Đối chiếu các trường này trên đúng phiên bản PR.

Nếu tất cả pass nhưng bug production chưa đóng, đó là vấn đề xác minh triển khai thuộc workflow khác. Không dùng `git log` để thay thế bằng chứng review; ngược lại UI Approved không chứng minh target đã nhận mã.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="handoff-to-neighboring-modules">Bàn giao sang Git mechanics, branching strategy, Boards và Pipelines</a>

<details>
<summary>Xem chi tiết</summary>

Sau bài học này, **Git** là nơi học cơ chế commit graph, merge, fetch/push và giải quyết conflict; **branching-strategy** giúp chọn trunk-based/Git Flow và vòng đời nhánh; **monorepo-polyrepo** thảo luận chia tách kho. **Azure Boards** sở hữu mô hình work item/sprint, còn **Azure Pipelines** sở hữu CI/CD và xây dựng checks.

Azure Repos kết nối những hệ thống đó ở mức PR, quyền, policy và dấu vết. Khi giải thích tại sao một PR không complete, chỉ đi sâu đủ để định vị “quyền, vote, policy, code conflict hay check”; không dạy chi tiết triển khai pipeline hoặc vận hành board trong chương Git collaboration.

</details>

- [Quay lại đầu trang](#back-to-top)
