<a id="back-to-top"></a>

# Thảo luận và đánh giá pull request

## Menu
- [Mô tả PR và phần khác biệt tệp mã nguồn](#pr-diff-and-description)
- [Reviewer được chỉ định và bối cảnh cần đánh giá](#reviewer-assignment-and-context)
- [Bình luận theo dòng và luồng thảo luận trong PR](#line-comments-discussion-threads)
- [Phiếu Approve so với Approve with suggestions](#approve-versus-approve-suggestions)
- [Wait for author và Reject: ý nghĩa phản hồi đánh giá](#wait-for-author-versus-reject)
- [Sự khác biệt giữa review vote và hoàn tất pull request](#review-vote-vs-pr-completion)
- [Cập nhật của tác giả, xử lý phản hồi và review lại](#author-revisions-and-re-review)
- [Bằng chứng reviewer, thảo luận và trạng thái phiếu đánh giá](#verify-review-state)

## <a id="pr-diff-and-description">Mô tả PR và phần khác biệt tệp mã nguồn</a>

<details>
<summary>Xem chi tiết</summary>

Đánh giá PR phải đọc **mục đích** trong title/description cùng **Files** diff: tệp được thêm, xóa hoặc chỉnh, và tác động ngoài phạm vi mô tả. Diff PR phụ thuộc source/target và có thể đổi khi cả hai nhánh có commit mới, nên không coi một screenshot cũ là ảnh chụp cố định.

Ví dụ PR “fix timeout” đồng thời sửa phân quyền repo trong config: reviewer cần hỏi tại sao. Đối chiếu danh sách commits, changed files, test evidence và work item; không chỉ đọc mô tả tác giả rồi Approve.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reviewer-assignment-and-context">Reviewer được chỉ định và bối cảnh cần đánh giá</a>

<details>
<summary>Xem chi tiết</summary>

**Reviewer** là người được mời đánh giá tính đúng và rủi ro thay đổi; **required reviewer** có thể do policy chỉ định, khác với người được tác giả mời tùy chọn. Reviewer nên có hiểu biết về module và có quyền cần thiết để đọc/đánh giá PR; quyền tham gia PR không đồng nghĩa quyền quản trị nhánh.

Nếu sửa xử lý thanh toán, chọn người hiểu luồng hoàn tiền thay vì chỉ người gần đây commit nhiều. Quan sát reviewers trên Overview và đánh dấu required/optional nếu hiển thị; khi thiếu người bắt buộc, tác giả không giải quyết bằng cách tự mời một người tùy chọn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="line-comments-discussion-threads">Bình luận theo dòng và luồng thảo luận trong PR</a>

<details>
<summary>Xem chi tiết</summary>

Bình luận **inline** gắn với đường dẫn/dòng trong diff; **discussion thread** giữ đối thoại giữa tác giả và reviewer, với trạng thái còn mở hoặc đã được giải quyết. Đây là nơi ghi nguyên nhân, đề xuất sửa và xác nhận tác giả đã phản hồi; comment được “resolved” không tự chứng minh code đã tốt nếu reviewer chưa kiểm tra lại.

Ví dụ reviewer yêu cầu xử lý `null` ở phần hoàn tiền, tác giả push commit và trả lời bằng kết quả test. Reviewer mở diff mới rồi giải quyết thread. Nếu target policy yêu cầu **comment resolution**, thread đang active có thể chặn complete PR dù đủ approvals.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="approve-versus-approve-suggestions">Phiếu Approve so với Approve with suggestions</a>

<details>
<summary>Xem chi tiết</summary>

**Approve** là phiếu đồng ý với thay đổi được đề xuất; **Approve with suggestions** cũng là một hình thức đồng ý nhưng vẫn gắn khuyến nghị cải thiện. Cả hai là **review votes**, không phải lệnh merge. Điều kiện minimum reviewers của nhánh đích cùng lựa chọn policy cụ thể mới quyết định phiếu nào được tính.

Tình huống Bob Approve with suggestions vì tên hàm chưa rõ: PR không tự sửa tên hàm; Alice phải cân nhắc đề xuất, và branch policy/test vẫn có thể chưa đạt. Đọc nội dung comment để biết có việc cần sửa thực sự trước khi hoàn tất.

### Tài liệu tham khảo

- [Microsoft Learn — approve versus approve suggestions](https://learn.microsoft.com/en-us/azure/devops/repos/git/review-pull-requests?view=azure-devops)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="wait-for-author-versus-reject">Wait for author và Reject: ý nghĩa phản hồi đánh giá</a>

<details>
<summary>Xem chi tiết</summary>

**Wait for author** báo reviewer cần tác giả xử lý hoặc giải thích phản hồi trước khi review lại; **Reject** thể hiện không chấp nhận thay đổi ở trạng thái đang xét. Khi một **required reviewer** đặt hai phiếu này, kết quả có thể chặn việc phê duyệt; với reviewer tùy chọn, ảnh hưởng còn tùy cấu hình chính sách như cho phép downvotes.

Ví dụ một PR thiếu kiểm tra bảo mật: reviewer chọn Wait for author và nêu trường hợp cần test. Khi tác giả cập nhật, reviewer phải kiểm tra lại và bỏ phiếu mới; không kỳ vọng cập nhật commit tự chuyển Reject thành Approve.

### Tài liệu tham khảo

- [Microsoft Learn — wait for author versus reject](https://learn.microsoft.com/en-us/azure/devops/repos/git/review-pull-requests?view=azure-devops)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="review-vote-vs-pr-completion">Sự khác biệt giữa review vote và hoàn tất pull request</a>

<details>
<summary>Xem chi tiết</summary>

Bỏ phiếu là **đánh giá của cá nhân**, còn **Complete PR** là hành động cập nhật nhánh đích theo quyền và policy. Một PR có tất cả reviewer đồng ý vẫn có thể chưa merge nếu build validation thất bại, comment chưa giải quyết hoặc người thao tác thiếu quyền; ngược lại người có quyền đặc biệt có thể bypass một số policies có điều kiện.

Minh chứng: trang Overview hiển thị Approve của Bob, nhưng mục Policies còn màu đỏ và nút Complete chưa thể dùng. Không được ghi “đã merged” vào work item chỉ vì đã đủ votes.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="author-revisions-and-re-review">Cập nhật của tác giả, xử lý phản hồi và review lại</a>

<details>
<summary>Xem chi tiết</summary>

Tác giả có thể push thêm commit lên **source branch** của PR đang Active; Azure Repos cập nhật diff/commits và có thể chạy lại checks, còn phiếu reviewer có được reset hay không tùy **cấu hình minimum-reviewers policy**. Không mặc định mọi push giữ hoặc xóa hết approvals. Bối cảnh thảo luận cần được xem lại khi thay đổi ảnh hưởng phần đã duyệt.

Ví dụ sửa bug lần hai chạm sang quy tắc tính phí: tác giả báo phạm vi mới và mời review lại, reviewer đọc diff hiện tại. Dấu vết cần giữ là thời điểm update source, trạng thái thread và vote sau update.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="verify-review-state">Bằng chứng reviewer, thảo luận và trạng thái phiếu đánh giá</a>

<details>
<summary>Xem chi tiết</summary>

Để kết luận PR đã được đánh giá đúng, thu thập **danh sách reviewers**, phiếu hiện tại, ai là required reviewer, thread cần giải quyết, thời điểm/cập nhật source và trạng thái Policies. Một nhãn Approved riêng lẻ không giải thích toàn bộ quyết định merge.

Thực hành: mở PR trên Repos → Pull requests, xem Files, Overview/Reviewers và Policies rồi mô tả “đã có hai Approve nhưng Wait for author của required reviewer chưa được thay đổi”; đây là bằng chứng cụ thể hơn “PR gần xong”. Không chụp thông tin cá nhân nhạy cảm/tokens trong báo cáo.

</details>

- [Quay lại đầu trang](#back-to-top)
