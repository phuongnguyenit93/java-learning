<a id="back-to-top"></a>

# Branch policies, điều kiện merge và quyền bypass

## Menu
- [Mục đích bảo vệ các nhánh đích quan trọng của PR](#why-protect-target-branches)
- [Branch permissions và branch policies: phạm vi khác biệt](#branch-permissions-vs-policies)
- [Chính sách merge bắt buộc và kiểm tra tùy chọn](#required-vs-optional-policies)
- [Số lượt phê duyệt tối thiểu và giới hạn tự phê duyệt](#minimum-reviewers-and-self-approval)
- [Required reviewers và yêu cầu giải quyết comment](#required-reviewers-and-comment-resolution)
- [Build validation, status checks và điểm bàn giao CI/CD](#build-validation-status-checks)
- [Kiểu hợp nhất được phép khi hoàn tất PR](#permitted-merge-types)
- [Quyền bypass policies khi complete PR và khi push: hai cơ chế riêng](#bypass-on-pr-versus-push)
- [Chẩn đoán policy/check chưa đạt và quyền bypass còn thiếu](#diagnose-policy-failures)

## <a id="why-protect-target-branches">Mục đích bảo vệ các nhánh đích quan trọng của PR</a>

<details>
<summary>Xem chi tiết</summary>

Nhánh như `main` thường dùng làm nguồn ổn định cho nhiều người; một push lỗi có thể ảnh hưởng cả nhóm. **Branch policies** yêu cầu đề xuất thay đổi qua PR, reviewers và checks trước khi cập nhật nhánh được bảo vệ. Đây là cơ chế quản trị **nhánh đích**, không chỉ là một template nội dung PR.

Ví dụ thanh toán yêu cầu ít nhất hai người duyệt và build validation trước merge vào `main`. Khi bật policy bắt buộc, Azure Repos có thể yêu cầu cập nhật nhánh qua PR thay vì push trực tiếp, trừ người có quyền bypass riêng.

### Tài liệu tham khảo

- [Microsoft Learn — why protect target branches](https://learn.microsoft.com/en-us/azure/devops/repos/git/branch-policies?view=azure-devops)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="branch-permissions-vs-policies">Branch permissions và branch policies: phạm vi khác biệt</a>

<details>
<summary>Xem chi tiết</summary>

**Branch permission** trả lời *ai được* Read, Contribute, Force push, Edit policies hoặc bypass; **branch policy** trả lời *điều kiện nào* phải đáp ứng cho PR nhắm vào branch đó. Người có Contribute ở repo không tự có quyền merge khi policy đang chặn; người có quyền Edit policies cũng chưa chắc có quyền bypass.

Ví dụ khi Alice không mở được PR do quyền, xem Repository/Branch Security; khi PR mở được nhưng báo “minimum reviewers failed”, xem Branch policies của **target**. Tách hai bề mặt kiểm tra giúp không cấp quyền admin chỉ để sửa số phiếu.

### Tài liệu tham khảo

- [Microsoft Learn — branch permissions vs policies](https://learn.microsoft.com/en-us/azure/devops/repos/git/branch-permissions?view=azure-devops)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="required-vs-optional-policies">Chính sách merge bắt buộc và kiểm tra tùy chọn</a>

<details>
<summary>Xem chi tiết</summary>

Một **blocking/required policy** chưa đạt sẽ cản PR complete thông thường; **optional/advisory check** cung cấp tín hiệu đánh giá nhưng không tự chặn theo cùng cách. “Enabled” và “Blocking/required” không phải lúc nào là một: cần xem cấu hình cụ thể của loại policy và phiên bản Azure DevOps đang sử dụng.

Ví dụ status scan bảo mật hiển thị warning nhưng được đặt optional, còn Build validation đặt required. PR có thể qua warning tùy policy nhưng phải vượt build; reviewer vẫn cần quyết định nội dung có an toàn hay không. Không tắt blocking check chỉ để PR nhanh xanh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="minimum-reviewers-and-self-approval">Số lượt phê duyệt tối thiểu và giới hạn tự phê duyệt</a>

<details>
<summary>Xem chi tiết</summary>

**Minimum number of reviewers** là số lượt duyệt cần có theo cấu hình nhánh đích, có tùy chọn tính phiếu tác giả, cho phép/không cho downvotes và reset votes khi source được cập nhật. Không giả định “hai người trong danh sách” bằng hai Approve có hiệu lực; cần xem chính sách và phiếu hiện tại.

Ví dụ cần hai người duyệt khác tác giả: Alice tự Approve và Bob Approve vẫn có thể chưa đủ nếu self-approval không được tính. Kiểm tra Policies/Reviewers và phần policy settings thay vì mời thêm reviewer mà không hiểu nguyên nhân.

### Tài liệu tham khảo

- [Microsoft Learn — minimum reviewers and self approval](https://learn.microsoft.com/en-us/azure/devops/repos/git/branch-policies?view=azure-devops)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="required-reviewers-and-comment-resolution">Required reviewers và yêu cầu giải quyết comment</a>

<details>
<summary>Xem chi tiết</summary>

**Required reviewers** có thể được thêm tự động theo repo/nhánh/đường dẫn thay đổi; họ không thể bị thay thế đơn giản bằng một người tùy chọn. Chính sách **check for comment resolution** yêu cầu các thread còn hoạt động được xử lý trước completion nếu bật ở chế độ blocking. Hai yêu cầu này độc lập với số phiếu tối thiểu.

Ví dụ PR sửa `/payments/` tự thêm nhóm sở hữu payment review. Khi reviewer required Wait for author và một comment chưa resolved, tác giả phải sửa/giải thích và được đánh giá lại, không xóa bình luận để giả vờ đạt kiểm tra.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="build-validation-status-checks">Build validation, status checks và điểm bàn giao CI/CD</a>

<details>
<summary>Xem chi tiết</summary>

**Build validation** yêu cầu kết quả build/test hợp lệ cho thay đổi trước merge; **status checks** có thể nhận kết quả từ dịch vụ bên ngoài như quét bảo mật. Azure Repos kiểm tra status theo policy trên target, nhưng việc viết pipeline YAML, thiết lập agent và cấu hình service kết nối thuộc lĩnh vực Azure Pipelines/CI/CD.

Ví dụ required build đỏ vì unit test thất bại: PR chưa đủ điều kiện, dù reviewers Approve. Người học cần biết mở kết quả check để xem identity, thời điểm và pass/fail; cách viết test/pipeline không nằm trong chapter này.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="permitted-merge-types">Kiểu hợp nhất được phép khi hoàn tất PR</a>

<details>
<summary>Xem chi tiết</summary>

Azure Repos có policy giới hạn **merge strategies** khi complete PR: ví dụ **basic merge** có merge commit, **squash merge** gộp thành commit mới trên target, các kiểu **rebase** phát lại commit và có/không tạo merge commit tùy lựa chọn. Dù khác hình dạng lịch sử, tất cả đều cần đáp ứng quyền và chính sách PR.

Ví dụ nhóm muốn mỗi PR tạo một commit trên `main` thì có thể cho squash và hạn chế kiểu khác; điều đó không tự sửa conflict hay chứng minh test pass. Cơ chế commit graph chi tiết thuộc Git, chính sách chọn chiến lược theo nhóm thuộc branching-strategy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bypass-on-pr-versus-push">Quyền bypass policies khi complete PR và khi push: hai cơ chế riêng</a>

<details>
<summary>Xem chi tiết</summary>

Azure Repos phân biệt **Bypass policies when completing pull requests** (người có quyền chọn **Override branch policies** khi complete PR, tức bypass có chủ ý) với **Bypass policies when pushing** (khi push phù hợp, policy được bypass **tự động**, không có bước opt-in tương tự). Đây là **hai quyền riêng**; bypass khi push **không tự cấp Contribute hay quyền ghi nhánh còn thiếu**. Chức danh admin và quyền Contribute thông thường không mặc định đồng nghĩa được bypass cả hai.

Ví dụ release khẩn: người trực sự cố được cấp PR bypass hạn chế có thể complete PR với lý do rõ ràng nhưng chưa chắc được direct push main. Cấp quyền ở scope hẹp, lưu audit/lý do và thu hồi sau sự cố; bypass không làm code tự an toàn.

### Tài liệu tham khảo

- [Microsoft Learn — bypass on pr versus push](https://learn.microsoft.com/en-us/azure/devops/repos/git/branch-policies?view=azure-devops)
- [Microsoft Learn — Branch permissions and separate bypass behavior](https://learn.microsoft.com/en-us/azure/devops/repos/git/branch-permissions?view=azure-devops)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="diagnose-policy-failures">Chẩn đoán policy/check chưa đạt và quyền bypass còn thiếu</a>

<details>
<summary>Xem chi tiết</summary>

Khi Complete bị vô hiệu hoặc policy báo lỗi, đọc chính xác **target branch**, trạng thái PR Draft/Active, minimum reviewers, required reviewers, comment resolution, build/status checks, merge conflicts và **effective permission** của người thao tác. Không phải mọi lỗi đều sửa bằng cách thêm Approve hoặc cấp bypass.

Một PR có hai Approve nhưng vẫn blocked vì build required thất bại: mở Policies → build result rồi yêu cầu sửa code/test; nếu policy thành công nhưng user không được complete, kiểm tra quyền repo/branch. Với fork, chỉ xem policy của **upstream target**, không dùng settings fork làm bằng chứng.

</details>

- [Quay lại đầu trang](#back-to-top)
