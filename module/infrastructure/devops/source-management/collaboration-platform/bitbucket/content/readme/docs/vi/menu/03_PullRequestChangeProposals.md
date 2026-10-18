<a id="back-to-top"></a>

# Đề xuất thay đổi qua pull request

## Menu
- [Pull Request như một đề xuất thay đổi có thể đánh giá](#what-is-bitbucket-pull-request)
- [Repository, nhánh nguồn/đích và phần khác biệt](#source-destination-and-diff)
- [Mô tả đề xuất, bối cảnh và người đánh giá](#review-context-description-and-reviewers)
- [Thay đổi đã chia sẻ so với thay đổi được nhóm chấp nhận](#shared-versus-accepted-changes)

## <a id="what-is-bitbucket-pull-request">Pull Request như một đề xuất thay đổi có thể đánh giá</a>

<details>
<summary>Xem chi tiết</summary>

**Pull request (PR)** biến sửa đổi cá nhân thành một **đề xuất có thể đánh giá**, thay vì yêu cầu nhóm tin vào tệp đính kèm. Nó tạo trang thảo luận gắn với nhánh nguồn và đích, trình bày thay đổi, mời người review và giữ dấu vết quyết định. PR không phải một commit đặc biệt và không tự bảo đảm mã đã được chấp nhận.

Trong Orchid, An thay đổi logic thuế trên nhánh công việc rồi mở PR vào nhánh dùng chung của `invoice-api`. Bình cần xem cả nghiệp vụ và tác động đến làm tròn. Một PR nên đủ nhỏ để người review hiểu được nhưng đủ bối cảnh để nhận diện rủi ro; sửa nhiều vấn đề không liên quan khiến quyết định khó hơn.

**Bằng chứng:** trang PR thể hiện tác giả, mục đích, nhánh nguồn/đích và trạng thái hiện tại. **Thực hành:** viết tiêu đề và hai câu mô tả PR sửa thuế sao cho người chưa đọc code vẫn hiểu điều cần xác nhận.

### Tài liệu tham khảo
- [Atlassian — Create a pull request for review](https://support.atlassian.com/bitbucket-cloud/docs/create-a-pull-request-for-review/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="source-destination-and-diff">Repository, nhánh nguồn/đích và phần khác biệt</a>

<details>
<summary>Xem chi tiết</summary>

Mỗi PR cần xác định **nguồn (source repository/branch)** là nơi chứa thay đổi được đề xuất và **đích (destination repository/branch)** là nơi nhóm muốn nhận thay đổi. Hai nhánh có thể thuộc cùng repository hoặc hai repository khác nhau tùy cách đóng góp. **Diff** trình bày phần khác biệt để reviewer đánh giá tác động.

Ví dụ An đề xuất `orchid-team/invoice-api` nhánh `fix/tax` vào nhánh đích `main`. Trước khi tạo PR, hãy đối chiếu nguồn và đích để không gửi bản vá tới nhánh bảo trì hoặc repository không định cập nhật. Phần khác biệt giúp Bình thấy sửa thuế đã vô tình chạm vào hàm làm tròn hay chưa.

**Bài tập:** mô tả một PR có hai tệp thay đổi nhưng chỉ một tệp liên quan đến thuế; tệp không liên quan là tín hiệu phải hỏi lại phạm vi, không phải tự động phán đoán PR sai. Chi tiết Git diff và merge mechanics thuộc module Git.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="review-context-description-and-reviewers">Mô tả đề xuất, bối cảnh và người đánh giá</a>

<details>
<summary>Xem chi tiết</summary>

Một PR chỉ có tiêu đề `fix bug` buộc reviewer đoán mục tiêu. Mô tả nên chỉ rõ **vấn đề trước sửa**, **quy tắc sau sửa**, **ảnh hưởng dự kiến** và **cách xác minh**. Với thay đổi thuế, An có thể gắn Jira work item nếu đã liên kết hệ thống, thêm ví dụ hóa đơn và chọn Bình làm người đánh giá vì Bình hiểu nghiệp vụ làm tròn.

**Reviewer** là người được đề nghị kiểm tra mã, không nhất thiết là quản trị viên repository. Bitbucket có cấu hình **default reviewers** để tự thêm người phù hợp, nhưng việc có người trong danh sách không chứng minh họ đã review hoặc approval đã đủ theo chính sách. Cần phân biệt người nhận thông báo, người phản hồi và người có quyền quyết định merge.

**Thực hành:** viết mẫu PR gồm *mục tiêu, thay đổi, nguy cơ, kiểm chứng, reviewer*. Hãy nêu một lý do khiến người chỉ quen tầng giao diện không phải reviewer duy nhất phù hợp cho sửa thuật toán tiền.

### Tài liệu tham khảo
- [Atlassian — Use pull requests for code review](https://support.atlassian.com/bitbucket-cloud/docs/use-pull-requests-for-code-review/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="shared-versus-accepted-changes">Thay đổi đã chia sẻ so với thay đổi được nhóm chấp nhận</a>

<details>
<summary>Xem chi tiết</summary>

Khi An đã cập nhật nhánh trên kho từ xa và tạo PR, thay đổi **đã được chia sẻ** nhưng chưa chắc **được chấp nhận**. Bình có thể yêu cầu bổ sung kiểm thử số tiền âm, task còn mở hoặc một merge check chưa đạt. Chỉ khi PR được tiếp nhận theo quyền và điều kiện áp dụng thì nhánh đích mới phản ánh kết quả được nhóm công nhận.

Đừng dùng “đã push”, “đã mở PR”, “đã được comment” và “đã merge” như từ đồng nghĩa. Review có thể từ chối đề xuất; có thể có cảnh báo kiểm tra mà hệ thống vẫn cho merge nếu chưa bật cơ chế chặn Premium. Người làm dự án phải hiểu cả trạng thái Bitbucket lẫn chính sách nhóm.

**Minh chứng:** PR của An tồn tại và reviewer đã đọc nhưng chưa approval; trạng thái nhánh đích không chứa bản vá. **Bài tập:** lập bốn cột *ghi nhận — chia sẻ — review — tiếp nhận* và đánh dấu điểm nào chứng minh bản vá đã trở thành mã chung.

</details>

- [Quay lại đầu trang](#back-to-top)
