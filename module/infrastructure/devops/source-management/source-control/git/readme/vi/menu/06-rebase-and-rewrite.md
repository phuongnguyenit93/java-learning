<a id="back-to-top"></a>

# Rebase và tác động khi viết lại lịch sử

## Menu
- [Lịch sử tạo bởi merge so với rebase](#merge-versus-rebase-outcomes)
- [Rebase: phát lại commit và thay đổi commit ID](#rebase-replay-and-commit-ids)
- [Rebase chuỗi commit cục bộ lên base mới](#rebase-local-work)
- [Xử lý xung đột và trạng thái tiếp tục hoặc hủy rebase](#resolve-continue-or-abort-rebase)
- [Amend commit cục bộ và hệ quả tới lịch sử](#amend-a-local-commit)
- [Rủi ro viết lại commit đã được chia sẻ](#shared-history-rewrite-risk)
- [Bằng chứng thay đổi commit graph và nội dung sau rebase](#verify-history-after-rebase)

## <a id="merge-versus-rebase-outcomes">Lịch sử tạo bởi merge so với rebase</a>

<details>
<summary>Xem chi tiết</summary>

Cùng một thay đổi có thể tích hợp bằng **merge** hoặc **rebase**, nhưng đồ thị lịch sử khác. Merge tạo điểm hợp nhất giữ nguyên các commit gốc khi hai phía phân kỳ; rebase phát lại commit của nhánh đề xuất trên base mới, tạo chuỗi mới có ID khác, thường nhìn tuyến tính hơn.

Tính tuyến tính không mặc nhiên tốt hơn: merge bảo tồn lịch sử ban đầu, rebase làm lịch sử dễ đọc theo một tuyến nhưng có chi phí khi các commit đã được chia sẻ. Quyết định team policy thuộc module branching-strategy, ở đây chỉ so cơ chế.

```bash
git log --oneline --graph --all
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rebase-replay-and-commit-ids">Rebase: phát lại commit và thay đổi commit ID</a>

<details>
<summary>Xem chi tiết</summary>

`git rebase main` khi đang ở topic tìm phần commit riêng của topic rồi lần lượt phát lại bản vá của chúng trên tip `main` (hành vi chính xác phụ thuộc tùy chọn và lịch sử). Commit mới có parent khác nên **ID khác** dù patch nhìn gần giống. Commit cũ có thể tạm còn trong reflog nhưng không nên trông chờ bảo tồn mãi.

Trước rebase, ghi `git log --oneline --graph --all`; sau đó so ID và tree/diff với target. Git có thể bỏ commit trùng thay đổi, nên số lượng commit không phải luôn bằng ban đầu.

```bash
git switch topic
git log --oneline --graph --all
git rebase main
```

### Tài liệu tham khảo

- [Git documentation](https://git-scm.com/book/en/v2/Git-Branching-Rebasing)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rebase-local-work">Rebase chuỗi commit cục bộ lên base mới</a>

<details>
<summary>Xem chi tiết</summary>

Ví dụ topic phát triển trên `main` cũ và chưa push. Sau khi `main` nhận commit mới, chuyển về `topic` với working tree sạch rồi `git rebase main` để đặt chuỗi công việc của topic lên base mới. Đây là cách làm **trên commit local chưa chia sẻ**; không nên thao tác trực tiếp trên nhánh đang dùng chung bởi nhiều người.

Kiểm tra trước: `git status` sạch và `git log main..topic` liệt kê commit bạn kỳ vọng được phát lại. Sau rebase, chạy test và xem diff của topic so với main để chắc nội dung vẫn đúng.

```bash
git status
git log --oneline main..topic
git rebase main
git diff --stat main...topic
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resolve-continue-or-abort-rebase">Xử lý xung đột và trạng thái tiếp tục hoặc hủy rebase</a>

<details>
<summary>Xem chi tiết</summary>

Rebase có thể dừng ở commit không áp dụng sạch. `git status` báo đang rebase; sửa file xung đột theo nội dung mong muốn, `git add -- path`, `git rebase --continue` để phát lại các commit còn lại. Nếu toàn bộ phép áp dụng hiện tại không phù hợp, `git rebase --abort` trở về vị trí trước thao tác (trừ các thay đổi ngoài dự kiến đã xen vào).

`git rebase --skip` **bỏ hẳn commit đang áp dụng** nên dễ làm mất tính năng; chỉ dùng khi có chứng cứ nội dung đã được áp dụng hoặc commit không còn cần thiết. **Continue và abort là hai ngả xử lý thay thế nhau**, không thực thi lần lượt.

```bash
git status
git add -- src.txt
git rebase --continue
# Neu muon HUY thay vi continue: git rebase --abort
```

### Tài liệu tham khảo

- [Git documentation](https://git-scm.com/docs/git-rebase)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="amend-a-local-commit">Amend commit cục bộ và hệ quả tới lịch sử</a>

<details>
<summary>Xem chi tiết</summary>

`git commit --amend` tạo commit thay thế HEAD bằng snapshot index và metadata/message đã chỉnh; **không sửa tại chỗ commit bất biến**, mà tạo ID mới và di chuyển nhánh. Dùng để sửa typo hoặc bổ sung phần còn thiếu vào commit **chưa được chia sẻ**, sau khi xem staged diff.

Nếu chỉ sửa thông điệp, `git commit --amend -m "New message"` cũng thay commit ID. Không amend commit đã được đồng nghiệp fetch khi chưa thống nhất hệ quả rewrite.

```bash
git diff --cached
git commit --amend -m "Clarify validation"
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="shared-history-rewrite-risk">Rủi ro viết lại commit đã được chia sẻ</a>

<details>
<summary>Xem chi tiết</summary>

Rebase/amend các commit đã push tạo object ID mới; nhánh remote và clone đồng nghiệp vẫn có ID cũ. Một lần force-push có thể khiến người khác phải rebase lại, mất dấu review hoặc ghi đè refs. Vì vậy mặc định **không rewrite lịch sử đã chia sẻ**; xem `git branch -vv` và `git log @{upstream}..HEAD` để hiểu phần local (khi có upstream).

Trong trường hợp bắt buộc rewrite nhánh riêng, phải phối hợp với người dùng nhánh và hiểu `--force-with-lease`; nó giúp từ chối nếu remote không ở trạng thái mình kỳ vọng nhưng không bảo đảm mọi workflow/PR an toàn.

```bash
git branch -vv
git log --oneline '@{upstream}'..HEAD
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="verify-history-after-rebase">Bằng chứng thay đổi commit graph và nội dung sau rebase</a>

<details>
<summary>Xem chi tiết</summary>

Trước/sau rebase, lưu đồ thị (hoặc tên ref thử nghiệm) để so commit IDs; sau thao tác, `git log --graph --decorate` cho thấy topic nằm trên base mới. Kiểm tra `git diff main...topic` hoặc test để xác nhận **nội dung** thay đổi mong muốn vẫn tồn tại.

Nếu cần đánh giá từng commit trước và sau, `git range-diff <old-base>..<old-tip> <new-base>..<new-tip>` hữu ích khi có các tên ref/ID đã lưu. Không dùng “đồ thị thẳng” như bằng chứng không mất logic.

```bash
git log --graph --oneline --all
git diff main...topic
git range-diff <old-base>..<old-tip> <new-base>..<new-tip>
```

</details>

- [Quay lại đầu trang](#back-to-top)
