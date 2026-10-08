<a id="back-to-top"></a>

# Thực hành một vòng thay đổi Git hoàn chỉnh

## Menu
- [Tình huống theo dõi tệp từ lần sửa tới commit](#trace-change-to-commit)
- [Tình huống theo dõi commit qua nhánh và remote-tracking reference](#follow-branch-and-remote)
- [So sánh cơ chế tích hợp bằng merge và rebase](#select-merge-or-rebase-mechanics)
- [Chẩn đoán lịch sử phân kỳ và xung đột](#diagnose-divergence-and-conflict)
- [Lựa chọn hoàn tác theo trạng thái chia sẻ của commit](#choose-safe-undo-in-context)
- [Đối chiếu status, diff, lịch sử và tham chiếu sau một vòng thay đổi](#verify-end-to-end-evidence)
- [Ranh giới giữa cơ chế Git, review nền tảng và chiến lược nhánh](#handoff-to-team-collaboration)

## <a id="trace-change-to-commit">Tình huống theo dõi tệp từ lần sửa tới commit</a>

<details>
<summary>Xem chi tiết</summary>

Tình huống trong **repo thử**: trước khi chạy khối lệnh bên dưới, tạo **một thư mục thực hành trống riêng biệt**, vào đó chạy `git init`, đặt `git config user.name "Practice User"` và `git config user.email "practice@example.invalid"` **chỉ trong kho thử**, rồi dùng trình soạn thảo tạo `notes.txt` chứa một dòng văn bản. Không dùng kho dự án đang làm việc. Lúc này `git status --short` báo untracked; stage tệp, xem `git diff --cached` để kiểm tra đúng ý, rồi commit. Sửa tiếp nội dung mà chưa stage: `git diff` sẽ thể hiện phần mới trong working tree, còn `git show HEAD:notes.txt` vẫn trả bản đã ghi.

Chuỗi bằng chứng `status → diff --cached → log -1 → show HEAD:path` chứng minh thay đổi đi qua ba vùng. Nếu commit thất bại vì chưa cấu hình danh tính, đặt local user.name/user.email trước thay vì bỏ bước.

```bash
git status --short
git add notes.txt
git diff --cached
git commit -m "Add notes"
git show HEAD:notes.txt
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="follow-branch-and-remote">Tình huống theo dõi commit qua nhánh và remote-tracking reference</a>

<details>
<summary>Xem chi tiết</summary>

Sau commit local trên `topic`, `git log --decorate` đặt nhánh topic tại commit mới; `origin/topic` (nếu từng fetch/push) có thể vẫn ở commit cũ. Push `git push -u origin topic` đến server đã xác nhận quyền sẽ cập nhật nhánh từ xa; fetch ở bản clone khác làm remote-tracking ref của clone đó tiến lên.

Quan sát `git branch -avv` trước/sau; `git ls-remote origin refs/heads/topic` đọc trạng thái server. Không thể kết luận có review chỉ từ thành công của push.

```bash
git branch -avv
git push -u origin topic
git ls-remote origin refs/heads/topic
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="select-merge-or-rebase-mechanics">So sánh cơ chế tích hợp bằng merge và rebase</a>

<details>
<summary>Xem chi tiết</summary>

Ví dụ `main` và `topic` đều tiến lên: `git merge topic` trên main tích hợp lịch sử, có thể tạo merge commit nhiều cha; `git rebase main` trên topic phát lại commit topic với ID khác. Cả hai có thể tạo kết quả tệp tương tự nhưng **đồ thị và rủi ro chia sẻ** khác nhau.

**Đây là hai quy trình THAY THẾ, không chạy nối tiếp**: với merge, chuyển sang `main` rồi tích hợp `topic`; với rebase, ở `topic` chưa chia sẻ, phát lại lên `main`, rồi mới quyết định cách đưa nhánh đó vào đích. Merge tránh rewrite ID commit cũ đang được người khác dùng. Sau mỗi phương án so `git log --graph` và test nội dung, không lựa chọn chỉ vì hình đồ thị đẹp.

```bash
# Cach A (merge): git switch main; git merge topic
# Cach B (rebase tren topic CHUA chia se): git switch topic; git rebase main
git log --oneline --graph --all
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="diagnose-divergence-and-conflict">Chẩn đoán lịch sử phân kỳ và xung đột</a>

<details>
<summary>Xem chi tiết</summary>

Nếu push bị từ chối hoặc merge dừng vì conflict, đừng vội force. Trước tiên `git fetch origin`, xem `git log --left-right --graph HEAD...origin/main` để thấy hai phía, rồi chọn merge hoặc rebase theo trạng thái chia sẻ. Khi thao tác dừng, `git status` chỉ path unmerged; phải sửa nội dung có ý nghĩa, stage và tiếp tục đúng loại thao tác.

Dấu xung đột là bằng chứng cần quyết định nghiệp vụ, không phải lỗi Git “không biết làm việc”. Sau giải quyết, chạy test và xem diff/status để chắc không bỏ sót thay đổi.

```bash
git fetch origin
git log --left-right --graph --oneline HEAD...origin/main
git status
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="choose-safe-undo-in-context">Lựa chọn hoàn tác theo trạng thái chia sẻ của commit</a>

<details>
<summary>Xem chi tiết</summary>

Một học viên stage nhầm `config.txt`: dùng `git restore --staged -- config.txt`, working tree vẫn còn. Nếu muốn bỏ sửa đổi working tree đã backup, `git restore -- config.txt`. Nếu commit local sai nội dung và chưa chia sẻ, xem xét `git commit --amend`; nếu commit đã được chia sẻ, `git revert <commit>` bảo tồn lịch sử cũ.

Cùng là “undo” nhưng tác động vào vùng dữ liệu khác nhau. Trước mỗi lệnh, đối chiếu `git status`, hai loại `diff`, và `git log` để không làm mất dữ liệu chưa có snapshot.

```bash
git status -sb
git diff -- config.txt
git diff --cached -- config.txt
git log -3 --oneline
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="verify-end-to-end-evidence">Đối chiếu status, diff, lịch sử và tham chiếu sau một vòng thay đổi</a>

<details>
<summary>Xem chi tiết</summary>

Kiểm chứng một vòng Git cần nhiều quan sát độc lập: `git status -sb` cho trạng thái working/index và ahead/behind, `git diff --cached` cho snapshot sắp commit, `git log --graph --decorate` cho quan hệ commit, `git branch -avv` cho refs và upstream, `git ls-remote` cho ref thật trên server. Không lệnh đơn nào chứng minh tất cả.

Sau merge/rebase, đối chiếu nội dung file và chạy test; sau push đối chiếu server ref. **Một commit đã tạo ≠ đã push ≠ đã được review ≠ đã được triển khai**: mỗi bước thuộc một hệ thống/quy trình khác nhau.

```bash
git status -sb
git diff --cached
git log --oneline --graph --decorate --all
git branch -avv
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="handoff-to-team-collaboration">Ranh giới giữa cơ chế Git, review nền tảng và chiến lược nhánh</a>

<details>
<summary>Xem chi tiết</summary>

Git chịu trách nhiệm tạo object, di chuyển refs, merge/rebase và trao đổi commit. **Pull request/merge request** là cơ chế trên nền tảng lưu trữ để nhóm mô tả đề xuất, bình luận, phân quyền, phê duyệt trước khi hợp nhất; nó không phải object Git có sẵn. **Chiến lược nhánh** là quyết định của nhóm về vòng đời nhánh, tần suất tích hợp, không phải lệnh kỹ thuật.

Để tìm hiểu sâu hơn, học module GitHub, GitLab, Bitbucket hoặc Azure DevOps cho giao diện review/quyền; module branching-strategy cho lựa chọn trunk-based/Git Flow, và module monorepo-polyrepo cho tổ chức kho. Đây là lời dẫn bằng văn bản, không biến Git thành lớp quản trị hay CI/CD.

```bash
git help log
git help push
```

</details>

- [Quay lại đầu trang](#back-to-top)
