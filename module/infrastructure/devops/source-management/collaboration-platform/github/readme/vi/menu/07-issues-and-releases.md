<a id="back-to-top"></a>

# Theo dõi Issues và thông tin Releases trên GitHub

## Menu
- [GitHub Issues: đơn vị theo dõi công việc và thảo luận](#issues-as-work-items)
- [Assignees, labels và milestones để phân loại và theo dõi Issues](#issue-assignees-labels-milestones)
- [Liên kết Issue với pull request và kết quả thay đổi](#linking-issues-prs)
- [Git tag và GitHub Release: hai đối tượng khác nhau](#git-tags-vs-github-releases)
- [Ghi chú phát hành, tài nguyên đính kèm và phạm vi quản lý phiên bản](#release-notes-and-assets)

## <a id="issues-as-work-items">GitHub Issues: đơn vị theo dõi công việc và thảo luận</a>

<details>
<summary>Xem chi tiết</summary>

GitHub Issue là hồ sơ mô tả lỗi, ý tưởng hoặc việc cần làm trong ngữ cảnh repository. Issue có tiêu đề, mô tả, thảo luận, trạng thái và có thể được liên kết với PR. **Issue không phải một commit**: nó giải thích nhu cầu; PR ghi lại đề xuất thay đổi để giải quyết nhu cầu đó.

Ví dụ Issue #42: 'Giảm giá âm gây tổng tiền sai' nên nêu cách tái hiện, kết quả hiện tại và kết quả mong muốn. Nhóm có thể thảo luận nghiệp vụ trước khi sửa; nếu yêu cầu đổi, lịch sử trao đổi vẫn còn. Thử đọc một Issue và phân biệt sự kiện Open/Closed với việc mã đã merge hay phát hành.

### Tài liệu tham khảo
- [GitHub Docs — About issues](https://docs.github.com/en/issues/tracking-your-work-with-issues/learning-about-issues/about-issues)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="issue-assignees-labels-milestones">Assignees, labels và milestones để phân loại và theo dõi Issues</a>

<details>
<summary>Xem chi tiết</summary>

**Assignee** biểu thị người chịu trách nhiệm theo dõi Issue, **label** hỗ trợ phân loại như bug hoặc priority, **milestone** gom công việc theo mốc mục tiêu. Chúng hỗ trợ tổ chức nhưng không tự bảo đảm hoàn thành kỹ thuật. Một Issue có label 'ready' vẫn cần bằng chứng chấp nhận thay đổi; milestone hiển thị tiến độ không đồng nghĩa bản phát hành đã được triển khai.

Ví dụ nhóm gán Issue #42 cho An, label bug và milestone 1.4. Người quản lý lọc các Issue còn mở trong milestone để phát hiện việc tồn đọng. Khi thay đổi người phụ trách, cập nhật assignee thay vì sửa lịch sử code. Hãy ghi lại vì sao từng trường giúp ích cho phối hợp nhóm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="linking-issues-prs">Liên kết Issue với pull request và kết quả thay đổi</a>

<details>
<summary>Xem chi tiết</summary>

Liên kết PR với Issue cho phép người theo dõi thấy công việc nào đang được sửa. GitHub hỗ trợ tạo link thủ công hoặc dùng từ khóa đóng Issue trong mô tả PR. **Từ khóa tự đóng Issue có điều kiện liên quan đến nhánh mặc định**: PR nhắm nhánh khác có thể không tạo liên kết bằng từ khóa và không đóng Issue theo cách đó.

Ví dụ mô tả PR vào main chứa 'Fixes #42', sau khi PR hợp nhất vào default branch, Issue liên quan có thể được đóng tự động. Nếu PR nhắm release/1.3, đừng mặc định cùng từ khóa sẽ có tác dụng tương tự. Kiểm tra phần Development trên Issue/PR và trạng thái Issue sau merge; link và việc đóng Issue cũng chưa chứng minh bản sửa đã được phát hành.

### Tài liệu tham khảo
- [GitHub Docs — Linking a pull request to an issue](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="git-tags-vs-github-releases">Git tag và GitHub Release: hai đối tượng khác nhau</a>

<details>
<summary>Xem chi tiết</summary>

**Git tag** là tên tham chiếu tới một đối tượng trong lịch sử Git, thường dùng chỉ một phiên bản. **GitHub Release** là thông tin công bố gắn với tag, có tên, ghi chú, trạng thái draft/prerelease và có thể có assets. Có tag không tự tạo Release; tạo Release cũng không tự chứng minh việc triển khai phần mềm đã xong.

Ví dụ tag v1.4.0 đánh dấu mã được chọn; GitHub Release v1.4.0 có thể giải thích lỗi #42 đã sửa và cung cấp gói tải. Người kiểm tra nên đối chiếu tag, commit mục tiêu, nội dung ghi chú và thời điểm công bố. Cơ chế tạo tag và chiến lược phiên bản được học chuyên sâu ngoài module GitHub cộng tác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="release-notes-and-assets">Ghi chú phát hành, tài nguyên đính kèm và phạm vi quản lý phiên bản</a>

<details>
<summary>Xem chi tiết</summary>

Release notes tóm tắt thay đổi đáng chú ý cho người dùng: tính năng, sửa lỗi, thay đổi tương thích và đường dẫn tài liệu. Assets là tệp đính kèm như bản phân phối hoặc tài liệu, khác các tệp nguồn GitHub tự tạo để tải từ tag. Tính năng tạo notes tự động có thể hỗ trợ nhưng nội dung vẫn cần được người phụ trách kiểm tra.

Ví dụ Release v1.4.0 gồm ghi chú sửa lỗi làm tròn, liên kết PR và file ZIP đóng gói. Người đọc nên so sánh release notes với PR Merged và tag, không suy 'tải được file' tức là người dùng đang chạy phiên bản đó. Nếu bật immutable releases, chỉnh sửa assets sau khi công bố có thể bị hạn chế: cần chuẩn bị bản nháp và xác nhận các tệp trước khi publish.

### Tài liệu tham khảo
- [GitHub Docs — Managing releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)

</details>

- [Quay lại đầu trang](#back-to-top)
