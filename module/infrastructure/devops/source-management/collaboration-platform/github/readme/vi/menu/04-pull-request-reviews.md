<a id="back-to-top"></a>

# Đánh giá mã nguồn và quyết định pull request

## Menu
- [Ngữ cảnh review: mô tả, khác biệt, thảo luận và kết quả kiểm tra](#pr-context-diffs-and-checks)
- [Bình luận, phê duyệt và yêu cầu sửa trong pull request](#review-comments-decisions)
- [Phản hồi góp ý, cập nhật thay đổi và yêu cầu xem xét lại](#change-request-rework)
- [Yêu cầu reviewer, nhóm đánh giá và cơ chế CODEOWNERS](#reviewer-requests-codeowners)
- [CODEOWNERS ở nhánh đích yêu cầu review khi PR sẵn sàng, khác phê duyệt bắt buộc](#code-owner-approval-vs-request)

## <a id="pr-context-diffs-and-checks">Ngữ cảnh review: mô tả, khác biệt, thảo luận và kết quả kiểm tra</a>

<details>
<summary>Xem chi tiết</summary>

Review không bắt đầu bằng việc đọc từng dòng diff một cách tách rời. Trước hết xác định **mục tiêu PR**, hành vi trước/sau, phạm vi tệp và tác động tới người dùng; sau đó đối chiếu Files changed với mô tả, commit và phần thảo luận. Các **checks** có thể phản ánh kết quả chạy từ hệ thống khác; trạng thái pass chỉ chứng minh điều kiện được kiểm tra đã đạt, không chứng minh toàn bộ nghiệp vụ đúng.

Ví dụ sửa giảm giá: reviewer xem yêu cầu Issue, đối chiếu công thức ở diff và yêu cầu ví dụ khi số tiền bằng 0 hoặc âm. Người đánh giá có thể đánh dấu từng file Viewed để theo dõi tiến độ. Khi hoàn thành, họ gửi một review có kết luận thay vì coi mọi bình luận rời là phê duyệt.

### Tài liệu tham khảo
- [GitHub Docs — Reviewing proposed changes](https://docs.github.com/en/pull-requests/how-tos/review-pull-requests/reviewing-proposed-changes-in-a-pull-request)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="review-comments-decisions">Bình luận, phê duyệt và yêu cầu sửa trong pull request</a>

<details>
<summary>Xem chi tiết</summary>

GitHub phân biệt **Comment** (góp ý, không đưa ra kết luận chấp thuận), **Approve** (đồng ý với thay đổi được xem) và **Request changes** (yêu cầu sửa). Một bình luận theo dòng không tự động biến thành review bị chặn. Khi repository áp dụng yêu cầu review, trạng thái review và quyền người đánh giá mới liên quan tới điều kiện hợp nhất.

Ví dụ reviewer ghi 'Tên biến khó hiểu' dưới dạng Comment: tác giả có thể sửa nhưng PR chưa chắc bị policy chặn. Nếu reviewer Submit review với Request changes, PR có thể bị chặn cho tới khi yêu cầu được xử lý theo cơ chế bảo vệ; khi họ Approve, vẫn có thể còn status checks hoặc approvals khác thiếu. Hãy xem Reviews và merge box thay vì chỉ đếm số bình luận.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="change-request-rework">Phản hồi góp ý, cập nhật thay đổi và yêu cầu xem xét lại</a>

<details>
<summary>Xem chi tiết</summary>

Khi nhận góp ý, tác giả nên phân loại lỗi chức năng, yêu cầu kiểm thử, vấn đề thiết kế và đề xuất tùy chọn. Cập nhật nhánh head sẽ làm Files changed của PR đổi theo; tác giả cần giải thích đã sửa gì và **yêu cầu xem lại** thay vì giả định reviewer thấy toàn bộ cập nhật. Quy tắc bảo vệ có thể loại bỏ approvals cũ khi có commit mới hoặc khi nội dung thay đổi; hành vi phụ thuộc cài đặt.

Tình huống reviewer yêu cầu thêm kiểm tra âm tiền: tác giả cập nhật mã, trả lời ngay trên thread với kết quả kiểm chứng và re-request review. Reviewer xác nhận thay đổi trong diff mới trước khi Approve. Bằng chứng nên gồm phản hồi đã giải quyết, review cuối và merge conditions, tránh đóng thread chỉ để làm đẹp giao diện.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reviewer-requests-codeowners">Yêu cầu reviewer, nhóm đánh giá và cơ chế CODEOWNERS</a>

<details>
<summary>Xem chi tiết</summary>

Có thể yêu cầu review từ cá nhân hoặc team phù hợp. **CODEOWNERS** là tệp trong repository ánh xạ đường dẫn tệp tới người/nhóm chịu trách nhiệm, dùng để GitHub tự định tuyến yêu cầu review khi PR thay đổi phạm vi đó. CODEOWNERS không mặc nhiên trao quyền cho người được ghi tên; owner cần có quyền write phù hợp, và team cần đáp ứng điều kiện hiển thị/quyền.

Ví dụ dòng '/pricing/ @acme/payments' hướng việc sửa đường dẫn pricing tới nhóm payments. GitHub đọc CODEOWNERS ở **base branch** của PR, không lấy phiên bản chưa được merge từ head để đổi người kiểm duyệt. Hãy kiểm tra vị trí tệp hợp lệ, pattern khớp và quyền team nếu không thấy review request như dự kiến.

### Tài liệu tham khảo
- [GitHub Docs — About code owners](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-code-owners)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="code-owner-approval-vs-request">CODEOWNERS ở nhánh đích yêu cầu review khi PR sẵn sàng, khác phê duyệt bắt buộc</a>

<details>
<summary>Xem chi tiết</summary>

Hai cơ chế cần tách rõ: **CODEOWNERS tự động đề nghị người phụ trách review**; **branch protection/ruleset** có thể yêu cầu có phê duyệt code owner trước khi merge. Chỉ có file CODEOWNERS mà không bật điều kiện bắt buộc thì request review không tương đương một khóa chặn merge. Một trong các code owner hợp lệ của pattern có thể đủ để đáp ứng yêu cầu owner approval, không nhất thiết mọi người trong danh sách cùng phê duyệt.

**Ngoại lệ quan trọng:** Draft PR không tự động request code owners. Khi tác giả đánh dấu Ready for review, GitHub mới tạo yêu cầu phù hợp. Với PR sửa pricing, kiểm tra ba điều độc lập: CODEOWNERS ở base có khớp không; PR đã Ready chưa; và Settings/rules có yêu cầu owner approval không. Nếu không đạt, merge box chỉ rõ lý do cần xử lý.

</details>

- [Quay lại đầu trang](#back-to-top)
