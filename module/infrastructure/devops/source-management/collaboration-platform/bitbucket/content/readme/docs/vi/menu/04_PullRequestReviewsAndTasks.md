<a id="back-to-top"></a>

# Đánh giá pull request và xử lý phản hồi

## Menu
- [Mục đích đánh giá mã và trách nhiệm các bên](#review-purpose-and-participants)
- [Đọc phần khác biệt và bình luận theo tệp hoặc dòng](#diff-comments-and-discussions)
- [Góp ý, yêu cầu chỉnh sửa và tác vụ đánh giá: Vai trò riêng](#feedback-versus-review-tasks)
- [Trạng thái phê duyệt so với điều kiện hợp nhất](#review-status-versus-merge-eligibility)
- [Cập nhật thay đổi và yêu cầu đánh giá lại](#revisions-and-re-review)

## <a id="review-purpose-and-participants">Mục đích đánh giá mã và trách nhiệm các bên</a>

<details>
<summary>Xem chi tiết</summary>

**Code review** biến đề xuất một chiều thành cuộc đối thoại có trách nhiệm: tác giả giải thích mục đích, reviewer đánh giá tính đúng và rủi ro, người có thẩm quyền mới quyết định việc merge. Mục tiêu không chỉ tìm lỗi cú pháp, mà còn xác nhận thay đổi giải quyết yêu cầu và không làm hỏng hành vi liên quan.

Trong Orchid, An biết quy định thuế mới, Bình nắm logic làm tròn, Mai quản trị quyền. Bình có thể đề nghị thêm ví dụ hóa đơn có số lẻ và chỉ ra kịch bản bỏ sót. Mai không phải người duy nhất đủ khả năng review chỉ vì có quyền Admin. Một PR cũng có thể cần nhiều reviewer thuộc các nhóm chuyên môn khác nhau.

**Bài tập:** với bản sửa thuế ảnh hưởng kế toán và API, hãy chọn hai trách nhiệm cần đại diện trong review và nêu bằng chứng từng người phải xem trước khi approval. Chương này tập trung hoạt động review trên Bitbucket, không dạy cơ chế Git merge.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="diff-comments-and-discussions">Đọc phần khác biệt và bình luận theo tệp hoặc dòng</a>

<details>
<summary>Xem chi tiết</summary>

Bitbucket cho reviewer xem **Files changed** để đọc phần khác biệt, chọn tệp hoặc dòng cụ thể rồi bình luận. Thảo luận gắn đúng ngữ cảnh giúp tránh câu mơ hồ “đoạn này sai” trong chat. Khu vực hoạt động PR giữ bình luận và phản hồi; tùy giao diện người dùng có thể xem các thay đổi mới so với lần review trước.

Trong ví dụ, Bình bình luận cạnh phép làm tròn: “Với thuế 10%, hóa đơn 100,05 sẽ làm tròn vào bước nào?” Câu hỏi nói rõ dữ liệu đầu vào, vùng code liên quan và hành vi cần xác minh. An có thể trả lời bằng ví dụ hoặc cập nhật mã; bình luận đơn thuần không nhất thiết là tác vụ còn mở hay một yêu cầu bắt buộc.

**Thực hành:** viết một nhận xét ở mức dòng code chứa *hiện trạng quan sát — rủi ro — ví dụ kiểm tra*. Sau đó phân biệt nội dung nào chỉ cần thảo luận và nội dung nào nên chuyển thành task có thể theo dõi.

### Tài liệu tham khảo
- [Atlassian — Review code in a pull request](https://support.atlassian.com/bitbucket-cloud/docs/review-code-in-a-pull-request/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="feedback-versus-review-tasks">Góp ý, yêu cầu chỉnh sửa và tác vụ đánh giá: Vai trò riêng</a>

<details>
<summary>Xem chi tiết</summary>

Bitbucket có nhiều cách phản hồi. **Bình luận** nêu câu hỏi hoặc đề xuất; **task** là mục việc có thể giao và theo dõi đến khi giải quyết; trạng thái **Changes requested** thể hiện người review chưa hài lòng với bản đang xem. Chúng không phải cùng một đối tượng, và một bình luận không tự động biến thành task.

Ví dụ Bình nhận xét về cách đặt tên biến nhưng tạo task riêng “Bổ sung hóa đơn có số lẻ” để An biết đây là yêu cầu cần xử lý. Khi An cập nhật ví dụ, task được giải quyết theo quy trình review. Trong Bitbucket, người có quyền phù hợp có thể tạo task từ comment; vai trò người được sửa hoặc xóa task khác nhau tùy quyền. Việc task đã đóng không tự bảo đảm approval hoặc merge nếu còn điều kiện khác.

**Kiểm chứng:** PR có một bình luận giải thích và hai task, trong đó một task chưa giải quyết. **Bài tập:** viết danh sách việc An phải hoàn tất và phân biệt trạng thái nhiệm vụ với quyết định review.

### Tài liệu tham khảo
- [Atlassian — Review code in a pull request (tạo và quản lý task)](https://support.atlassian.com/bitbucket-cloud/docs/review-code-in-a-pull-request/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="review-status-versus-merge-eligibility">Trạng thái phê duyệt so với điều kiện hợp nhất</a>

<details>
<summary>Xem chi tiết</summary>

**Approval** cho biết một reviewer đã chấp thuận bản được xem, nhưng **merge eligibility** còn phụ thuộc nhiều điều kiện: quyền đối với nhánh đích, số approval cần thiết, task chưa xử lý, trạng thái yêu cầu sửa và các merge checks đang áp dụng. Vì thế dấu “đã duyệt” không phải giấy phép hợp nhất vô điều kiện.

Bitbucket có thể hiển thị merge checks ở gói Free/Standard như **cảnh báo** dù điều kiện chưa đạt; muốn hệ thống **ngăn merge** do kiểm tra chưa đạt phải dùng khả năng enforcement trên Premium và bật tùy chọn tương ứng. Trong khi đó branch restriction hạn chế quyền ghi/hợp nhất là loại kiểm soát riêng. Không được suy từ một biểu tượng xanh rằng cả nhánh lẫn chính sách đã hợp lệ.

**Bài tập:** PR của An có một approval nhưng task thử số tiền âm chưa hoàn thành. Hãy liệt kê những chứng cứ và thiết lập Mai cần kiểm tra trước khi tuyên bố PR sẵn sàng.

### Tài liệu tham khảo
- [Atlassian — Suggest or require checks before a merge](https://support.atlassian.com/bitbucket-cloud/docs/suggest-or-require-checks-before-a-merge/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="revisions-and-re-review">Cập nhật thay đổi và yêu cầu đánh giá lại</a>

<details>
<summary>Xem chi tiết</summary>

Sau khi An sửa theo góp ý, nội dung PR có thể đã khác với bản Bình phê duyệt trước đó. **Review lại** giúp xác nhận yêu cầu đã được giải quyết mà không tạo lỗi mới. Bitbucket cho người xem phân biệt những thay đổi xuất hiện kể từ lần review trước và theo dõi hoạt động trên PR.

Ví dụ An bổ sung kiểm thử cho giá trị 100,05 nhưng đồng thời chỉnh một hàm tính giảm giá không nằm trong phạm vi yêu cầu. Bình cần xem lại cả phần vừa đổi, không chỉ đóng task bằng niềm tin. Một số khả năng Premium còn có điều kiện yêu cầu approval mới sau khi nhánh nguồn thay đổi; loại kiểm tra này phải được cấu hình đúng chứ không mặc nhiên áp dụng trên mọi gói.

**Thực hành:** mô tả quy trình 4 bước *An cập nhật — chỉ ra diff mới — Bình đối chiếu bằng chứng — xác nhận task/trạng thái*. Nêu vì sao việc “đã được duyệt hôm qua” không bảo đảm nội dung hôm nay đã được duyệt.

</details>

- [Quay lại đầu trang](#back-to-top)
