<a id="back-to-top"></a>

# Theo dõi Issues và công bố Releases

## Menu
- [GitLab Issues: mô tả, phân công và theo dõi công việc nhóm](#gitlab-issue-purpose)
- [Assignee, labels và milestones để tổ chức Issues](#issue-assignees-labels-milestones)
- [Liên kết Issue với merge request và điều kiện tự đóng trên nhánh mặc định](#issues-linked-to-merge-requests)
- [Git tag và GitLab Release: hai đối tượng khác nhau](#git-tag-vs-gitlab-release)
- [Ghi chú, tài nguyên Release và ranh giới với quản lý phiên bản/triển khai](#release-notes-assets-boundary)

## <a id="gitlab-issue-purpose">GitLab Issues: mô tả, phân công và theo dõi công việc nhóm</a>

<details>
<summary>Xem chi tiết</summary>

GitLab Issue lưu một lỗi, yêu cầu hoặc công việc cần theo dõi trong project. Issue chứa mô tả, bình luận và trạng thái; nó có thể được giải quyết bằng MR nhưng **Issue không phải là commit hay bằng chứng đã triển khai**. Việc diễn đạt mục tiêu trước khi sửa giúp reviewer đánh giá mã theo quy tắc nghiệp vụ thay vì chỉ nhìn diff.

Ví dụ Issue #42 ghi 'giá trị âm phải bị từ chối', kèm bước tái hiện, đầu vào -1 và kết quả mong đợi. Nhóm thảo luận và phân công trước khi mở MR !57. Khi đọc Issue hãy tách yêu cầu, tình trạng xử lý và liên kết MR; Closed chưa đủ để nói người dùng đã nhận bản sửa.

### Tài liệu tham khảo
- [GitLab Docs — Issues](https://docs.gitlab.com/user/project/issues/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="issue-assignees-labels-milestones">Assignee, labels và milestones để tổ chức Issues</a>

<details>
<summary>Xem chi tiết</summary>

Assignee biểu thị ai đang chịu trách nhiệm xử lý Issue; labels phân loại như bug, enhancement hoặc priority; milestones gom Issues và MRs vào mục tiêu hoặc thời điểm. Chúng giúp quản trị công việc nhưng không bảo đảm mã đã merge hoặc phát hành. Một nhãn 'ready' là thông tin do nhóm đặt, không phải quyền GitLab cho phép tự động merge.

Ví dụ Issue #42 gán cho An, label bug và milestone v1.4. Nhóm lọc các Issue chưa đóng thuộc milestone để xem việc tồn đọng. Khi đổi người phụ trách, cập nhật assignee và lý do thay đổi, không viết lại commit history.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="issues-linked-to-merge-requests">Liên kết Issue với merge request và điều kiện tự đóng trên nhánh mặc định</a>

<details>
<summary>Xem chi tiết</summary>

Issue có thể liên kết trực tiếp với MR hoặc dùng **closing pattern** trong mô tả MR, chẳng hạn `Closes #42`, để GitLab xử lý trạng thái Issue khi MR đủ điều kiện hợp nhất. Cơ chế tự đóng thường gắn với **default branch**; đừng mặc định MR nhắm release branch cũng tự đóng Issue theo cùng cách. Link theo dõi và tự đóng là hai hành vi cần quan sát riêng.

Ví dụ MR !57 nhắm main của gateway và ghi `Closes #42`; sau Merged, kiểm tra Issue có được đóng, nguyên nhân nào được ghi trong timeline. Nếu MR chỉ đóng không merge hoặc nhắm một branch khác, không kết luận Issue đã hoàn tất khi chưa xem kết quả thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="git-tag-vs-gitlab-release">Git tag và GitLab Release: hai đối tượng khác nhau</a>

<details>
<summary>Xem chi tiết</summary>

**Git tag** là tham chiếu có tên trong lịch sử Git, thường dùng đánh dấu commit phiên bản. **GitLab Release** là hồ sơ công bố gắn với tag, có tên, ngày, ghi chú và có thể có assets. Tag không tự bảo đảm có Release; Release công bố trên GitLab không đồng nghĩa mã đã được triển khai production.

Ví dụ commit sửa lỗi được đánh dấu v1.4.0, sau đó Maintainer tạo Release v1.4.0 mô tả Issue #42. Khi kiểm chứng, đối chiếu tag, commit được chỉ định và các ghi chú; phần thao tác Git tạo tag thuộc module Git, còn quy tắc chọn số phiên bản thuộc quản lý phát hành.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="release-notes-assets-boundary">Ghi chú, tài nguyên Release và ranh giới với quản lý phiên bản/triển khai</a>

<details>
<summary>Xem chi tiết</summary>

Release notes nên nêu thay đổi người dùng cần biết: tính năng, sửa lỗi, không tương thích và liên kết tài liệu. Assets có thể là tệp đính kèm hoặc liên kết phân phối, khác bản nguồn được duyệt trong MR. GitLab có giao diện ghi nhận Release nhưng việc đóng gói, ký, đẩy artifact và triển khai thuộc pipeline hoặc hệ thống phát hành khác.

Tình huống Release v1.4.0 liệt kê lỗi giá âm đã sửa, link MR !57 và một gói tải. Người kiểm tra cần xác minh notes phù hợp kết quả merge và tag; không suy rằng file xuất hiện nghĩa người dùng hiện chạy v1.4.0. Nếu thiếu Release, ghi 'merged, not published' thay vì ngầm tuyên bố đã ship.

### Tài liệu tham khảo
- [GitLab Docs — Releases](https://docs.gitlab.com/user/project/releases/)

</details>

- [Quay lại đầu trang](#back-to-top)
