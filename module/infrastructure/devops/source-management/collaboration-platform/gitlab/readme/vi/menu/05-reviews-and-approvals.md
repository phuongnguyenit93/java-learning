<a id="back-to-top"></a>

# Review, phản hồi và phê duyệt merge request

## Menu
- [Ngữ cảnh review: diffs, thảo luận và bình luận theo dòng](#review-context-diff-discussions)
- [Reviewer, assignee và người đủ điều kiện phê duyệt](#mr-reviewers-vs-assignees)
- [Nhận xét, đề xuất sửa và phê duyệt trong merge request](#review-comments-vs-approvals)
- [Request changes có thể chặn merge: điều kiện Premium/Ultimate](#request-changes-tier-gate)
- [Approval rules, số lượng phê duyệt và điều kiện theo gói dịch vụ](#approval-rules-required-approvers)
- [Phạm vi Code Owners so với approval rules tổng quát](#code-owners-vs-approval-rules)
- [Xử lý góp ý, cập nhật merge request và yêu cầu đánh giá lại](#follow-up-reviews)

## <a id="review-context-diff-discussions">Ngữ cảnh review: diffs, thảo luận và bình luận theo dòng</a>

<details>
<summary>Xem chi tiết</summary>

Review MR không nên bắt đầu bằng việc đếm dòng mã. Reviewer xác định **vấn đề nghiệp vụ**, mô tả trước/sau, Changes/diff, commit và discussion threads trước, rồi mới đánh giá chi tiết. Bình luận theo dòng giúp gắn nhận xét với vị trí cụ thể; toàn bộ MR vẫn cần một kết luận review tổng thể. Check xanh có thể chỉ chứng minh điều kiện kiểm thử đã chạy, không bảo đảm logic tính tiền đúng.

Ví dụ MR !57 sửa làm tròn nhưng mô tả Issue #42 yêu cầu từ chối số âm: reviewer dùng diff và ví dụ -1 để phát hiện khác biệt. Hãy quan sát phần Changes và Discussions, đối chiếu phản hồi với phiên bản mã mới nhất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mr-reviewers-vs-assignees">Reviewer, assignee và người đủ điều kiện phê duyệt</a>

<details>
<summary>Xem chi tiết</summary>

**Reviewer** được yêu cầu xem xét chất lượng mã; **assignee** chịu trách nhiệm điều phối MR. **Eligible approver** là người có quyền và điều kiện hợp lệ để một approval được tính theo rule; không phải bất kỳ người để lại Comment đều thỏa rule. Việc chỉ định reviewer không tự tạo phiếu phê duyệt và một người có thể xuất hiện ở nhiều vai trò.

Ví dụ An là assignee, Bình là reviewer, Chi là người phê duyệt bắt buộc thuộc nhóm bảo mật nếu policy quy định. Xem widget Approvals, reviewer state và người được chỉ định, thay vì chỉ đếm avatar. Eligibility và các cách chọn reviewer nâng cao có thể khác nhau theo tier.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="review-comments-vs-approvals">Nhận xét, đề xuất sửa và phê duyệt trong merge request</a>

<details>
<summary>Xem chi tiết</summary>

Một review có thể ghi Comment, đề xuất sửa hoặc **Approve**. Comment làm rõ vấn đề nhưng không mặc nhiên là xác nhận chấp thuận; suggestion là đoạn chỉnh sửa cụ thể có thể được tác giả áp dụng. Approve phản ánh quyết định của reviewer, nhưng MR vẫn có thể bị chặn do protected branch hoặc các yêu cầu khác. GitLab Free hỗ trợ review/feedback, không vì thế mặc định có tất cả cơ chế enforcement của Premium.

Tình huống Bình nhận xét thiếu kiểm tra giá trị âm: An sửa và gửi lại, Bình kiểm tra bản mới rồi Approve. Khi quan sát, phân biệt nội dung comment, nút Approve, số approval rule đã đủ và trạng thái merge.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="request-changes-tier-gate">Request changes có thể chặn merge: điều kiện Premium/Ultimate</a>

<details>
<summary>Xem chi tiết</summary>

**Request changes** có thể thể hiện rằng reviewer chưa chấp nhận bản sửa. Khả năng dùng yêu cầu này **để chặn merge** thuộc GitLab **Premium/Ultimate**, theo tài liệu review hiện hành; không nên dạy như một điều kiện bắt buộc của mọi project Free. Dù có quyền Request changes, người review cần nêu lỗi, bằng chứng và cách xác nhận khi đã sửa, không chỉ dùng trạng thái phản đối.

Ví dụ Bình chọn Request changes vì cách làm tròn sai; An cập nhật MR và yêu cầu Bình đánh giá lại. Hãy so sánh hai dự án/tier: ở nơi có enforcement, merge box chỉ ra blocker review; ở nơi không hỗ trợ, phải dựa vào quy trình nhóm và các cơ chế khả dụng khác.

### Tài liệu tham khảo
- [GitLab Docs — Merge request reviews](https://docs.gitlab.com/user/project/merge_requests/reviews/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="approval-rules-required-approvers">Approval rules, số lượng phê duyệt và điều kiện theo gói dịch vụ</a>

<details>
<summary>Xem chi tiết</summary>

**Approval rule** cấu hình số approval cần có và nhóm/người được tính; theo GitLab Docs hiện tại, rule cấu hình thuộc **Premium/Ultimate**, trên GitLab.com, Self-Managed và Dedicated. Rule có thể áp dụng mặc định project hoặc theo MR; quy định số lượng >0 mới tạo yêu cầu bắt buộc, còn 0 có thể là optional. Thay đổi rule hoặc giới hạn tác giả tự approve phụ thuộc setting có sẵn.

**Điều kiện thành viên không đồng nhất với quyền xem project:** với nhóm được chọn làm approver, chỉ thành viên **trực tiếp** của nhóm được tính, không phải người chỉ kế thừa từ nhóm khác. Vai trò Planner/Reporter chỉ có thể approve trong approval rule thông thường nếu đã bật quyền tương ứng và đáp ứng điều kiện thành viên nhóm được chia sẻ; **Code Owner approval** đòi vai trò Developer, Maintainer hoặc Owner. Vì vậy cần xem danh sách *eligible approvers*, không suy luận từ việc một người đọc được MR.

Ví dụ project cần hai người từ nhóm payments trước merge vào main. Dù An nhận một Approve, widget vẫn báo còn một approval. Khi chẩn đoán, kiểm tra eligible approvers, số lượng yêu cầu, target branch và trạng thái rule thay vì giả định ai có quyền comment đều được tính.

### Tài liệu tham khảo
- [GitLab Docs — Approval rules](https://docs.gitlab.com/user/project/merge_requests/approvals/rules/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="code-owners-vs-approval-rules">Phạm vi Code Owners so với approval rules tổng quát</a>

<details>
<summary>Xem chi tiết</summary>

Tệp **CODEOWNERS** ánh xạ các vùng mã như `/pricing/` tới người hoặc nhóm chịu trách nhiệm; đó là cách diễn đạt ownership theo tệp, khác rule phê duyệt rộng hơn theo MR/project. Khi protected target branch yêu cầu Code Owner approval (tính năng có giới hạn tier), thay đổi trong vùng được chỉ định cần người đủ điều kiện. Không coi bất kỳ ai được nhắc tới trong tệp là có quyền đọc/approve nếu membership không hợp lệ.

Ví dụ MR !57 chạm `/pricing/` nên Chi từ nhóm payments phải xem theo rule đang bật; một Approval của người ngoài nhóm có thể không thỏa Code Owner requirement. Kiểm tra pattern, branch đích, eligible owner và trạng thái approval.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="follow-up-reviews">Xử lý góp ý, cập nhật merge request và yêu cầu đánh giá lại</a>

<details>
<summary>Xem chi tiết</summary>

Sau feedback, tác giả nên giải thích mình đã sửa gì, thêm minh chứng và yêu cầu reviewer xem **phiên bản mới nhất**. Commit bổ sung có thể làm approvals cũ mất hiệu lực nếu setting đặt vậy; việc mark resolved một discussion không thay thế kiểm chứng hành vi. Đóng thảo luận khi ý kiến đã được xử lý, không phải để làm sạch giao diện.

Tình huống An thêm kiểm thử âm tiền rồi trả lời thread của Bình với đầu vào/đầu ra; Bình mở diff mới và đưa review cuối. Hãy đối chiếu số commit, các unresolved discussions, approval widget và mergeability. Nếu MR vẫn blocked, xác định một blocker rõ ràng để xử lý ở chương protected branches.

</details>

- [Quay lại đầu trang](#back-to-top)
