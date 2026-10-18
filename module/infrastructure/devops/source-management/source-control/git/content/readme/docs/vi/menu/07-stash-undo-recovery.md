<a id="back-to-top"></a>

# Stash, hoàn tác và phục hồi an toàn

## Menu
- [Phân loại thay đổi chưa commit và lịch sử đã chia sẻ trước khi hoàn tác](#classify-uncommitted-and-shared-work)
- [Stash: tạm cất và khôi phục công việc chưa commit](#stash-work-in-progress)
- [Phạm vi stash mặc định và tùy chọn cho tệp untracked hoặc ignored](#stash-tracked-and-untracked)
- [git restore và ảnh hưởng lên working tree, index](#restore-worktree-and-index)
- [Các chế độ git reset và rủi ro mất dữ liệu](#reset-modes-and-risk)
- [git revert để hoàn tác lịch sử đã chia sẻ mà không viết lại commit cũ](#revert-shared-commit)
- [Reflog cục bộ, thời hạn lưu và giới hạn khôi phục](#reflog-and-expiration)
- [Tiêu chí chọn cách khôi phục theo trạng thái Git](#safe-recovery-decision)

## <a id="classify-uncommitted-and-shared-work">Phân loại thay đổi chưa commit và lịch sử đã chia sẻ trước khi hoàn tác</a>

<details>
<summary>Xem chi tiết</summary>

Trước hoàn tác phải hỏi: thay đổi đang nằm ở **working tree**, **index**, **commit cục bộ chưa push**, hay **commit đã chia sẻ**? Mỗi loại cần cơ chế khác: `restore` cho tệp/index, `reset` cho ref/index/working tree, `revert` cho lịch sử đã chia sẻ. Chọn sai có thể xóa thay đổi không có trong commit.

`git status -sb`, `git diff`, `git diff --cached` và `git log @{upstream}..HEAD` (khi có upstream) cho bằng chứng trước khi chọn. Nếu chưa chắc đã push hay chưa, hãy fetch và so refs; **backup thay đổi quan trọng trước thao tác phá hủy**.

```bash
git status -sb
git diff
git diff --cached
git branch -vv
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stash-work-in-progress">Stash: tạm cất và khôi phục công việc chưa commit</a>

<details>
<summary>Xem chi tiết</summary>

`git stash push -m "WIP: parsing"` cất thay đổi tracked chưa commit vào stash để working tree trở lại trạng thái thích hợp; sau đó `git stash list` xem danh sách, `git stash show -p stash@{0}` xem patch, `git stash apply stash@{0}` thử áp dụng mà vẫn giữ stash, còn `git stash pop` áp dụng và thường xóa stash nếu thành công.

Stash có thể xung đột khi áp dụng lên trạng thái đã đổi. Không thay thế stash bằng backup lâu dài; trước khi bỏ stash dùng `show` xác nhận dữ liệu cần giữ.

```bash
git stash push -m "WIP: parsing"
git stash list
git stash show -p 'stash@{0}'
git stash apply 'stash@{0}'
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stash-tracked-and-untracked">Phạm vi stash mặc định và tùy chọn cho tệp untracked hoặc ignored</a>

<details>
<summary>Xem chi tiết</summary>

Mặc định `git stash push` lưu các thay đổi tracked (index và working tree), **không tự bao gồm tệp untracked**; `-u` thêm untracked không bị ignore, `-a` còn bao gồm ignored. Nếu có dữ liệu build/secret trong thư mục ignored, `-a` có thể cất dữ liệu không mong muốn hoặc tốn dung lượng.

Thử trong repo học: tạo tệp untracked, stash mặc định rồi xem `git status --short` — tệp untracked thường vẫn còn. Chỉ dùng `git stash push -u` khi đã hiểu các tệp sẽ được lưu.

```bash
git status --short
git stash push -u -m "include untracked"
git stash list
```

### Tài liệu tham khảo

- [Git documentation](https://git-scm.com/book/en/v2/Git-Tools-Stashing-and-Cleaning)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="restore-worktree-and-index">git restore và ảnh hưởng lên working tree, index</a>

<details>
<summary>Xem chi tiết</summary>

`git restore -- path` mặc định **khôi phục nội dung working tree từ index**, vứt thay đổi chưa staged ở path tracked; không đổi index. `git restore --staged -- path` đặt index về nội dung HEAD cho path đó, **giữ working tree**, rất hữu ích khi stage nhầm. Khi cần nguồn rõ ràng, `git restore --source=<commit> -- path` đọc bản đã lưu từ commit đó vào working tree mặc định.

Thao tác ghi đè working tree là nguy hiểm với dữ liệu chưa backup. Dùng `git diff -- path` trước khi restore; đừng xem `restore` là lệnh undo chung cho mọi trường hợp.

```bash
git diff -- src.txt
git restore --staged -- src.txt
git restore -- src.txt
```

### Tài liệu tham khảo

- [Git documentation](https://git-scm.com/docs/git-restore)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reset-modes-and-risk">Các chế độ git reset và rủi ro mất dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

`git reset --soft <commit>` di chuyển HEAD/nhánh nhưng giữ index và working tree; `--mixed` (mặc định) di chuyển ref và reset index, giữ working tree; `--hard` đồng bộ **ref + index + working tree** về commit đích, có thể mất sửa đổi tracked chưa commit. Reset chỉ nên dùng khi đã kiểm chứng trạng thái chia sẻ và cần thay đổi lịch sử cục bộ.

Ví dụ trước commit mới tạo chưa push, `git reset --soft HEAD~1` giữ nội dung staged để sửa commit; `--mixed` lại bỏ stage. **Các chế độ là lựa chọn loại trừ nhau, không chạy cả ba trên cùng kho**. Không dùng `--hard` trên dự án có sửa đổi không được backup. Untracked files có thể vẫn tồn tại, hoặc bị ghi đè khi cản checkout path.

```bash
git reset --soft HEAD~1
# Thay cho --soft: git reset --mixed HEAD~1
# Canh bao, KHONG chay khi chua backup: git reset --hard <commit-id>
```

### Tài liệu tham khảo

- [Git documentation](https://git-scm.com/book/en/v2/Git-Tools-Reset-Demystified)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="revert-shared-commit">git revert để hoàn tác lịch sử đã chia sẻ mà không viết lại commit cũ</a>

<details>
<summary>Xem chi tiết</summary>

Nếu commit đã chia sẻ cần đảo ngược, `git revert <commit>` mặc định tạo **commit mới** áp dụng thay đổi ngược, nên giữ nguyên ID các commit cũ và lịch sử không bị ép viết lại. Trong một repo đang sạch, kiểm tra `git show <commit>` trước; sau revert, test và xem log/diff để xác nhận hành vi mong muốn.

Revert có thể xung đột nếu các commit sau phụ thuộc vào thay đổi cũ; khi đó xử lý và `git revert --continue` hoặc hủy bằng `git revert --abort`. Revert một merge commit cần `-m <parent-number>` và cân nhắc lịch sử tương lai, không chọn tùy tiện.

```bash
git show --stat <commit-id>
git revert <commit-id>
git log -3 --oneline
```

### Tài liệu tham khảo

- [Git documentation](https://git-scm.com/docs/git-revert)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflog-and-expiration">Reflog cục bộ, thời hạn lưu và giới hạn khôi phục</a>

<details>
<summary>Xem chi tiết</summary>

**Reflog** ghi các lần cập nhật tham chiếu cục bộ, gồm chuyển nhánh/reset/rebase, nên có thể giúp tìm ID commit cũ bằng `git reflog` và tạo nhánh cứu hộ `git branch rescue <hash>`. Reflog của local **không đồng bộ** qua push/fetch; các mục có thời hạn và objects không còn tham chiếu có thể bị garbage collection.

`git reflog show HEAD` cho lịch sử di chuyển HEAD; nếu thấy ID cần giữ, tạo ref trước khi hết hạn. Không mô tả reflog là backup vĩnh viễn hoặc phương án chắc chắn cứu nội dung chưa bao giờ staged/committed.

```bash
git reflog -10
git reflog show HEAD
git branch rescue <old-commit-id>
```

### Tài liệu tham khảo

- [Git documentation](https://git-scm.com/docs/git-reflog)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="safe-recovery-decision">Tiêu chí chọn cách khôi phục theo trạng thái Git</a>

<details>
<summary>Xem chi tiết</summary>

Thứ tự quyết định khi sự cố: **đọc** status/diff/log → xác định có commit hay chỉ thay đổi tệp → kiểm tra đã chia sẻ chưa → chọn hành động ít phá hủy nhất. Stage nhầm dùng `git restore --staged`; sửa working tree không muốn giữ dùng `git restore` sau backup; sửa commit mới chưa push có thể amend/soft reset; commit đã công bố thường dùng revert.

Nếu vừa reset/rebase nhầm, xem reflog trước khi objects hết hạn. Không tự động chạy `git clean -fd` hay `reset --hard`: những lệnh này có thể xóa nội dung untracked/tracked và không được bảo vệ bởi reflog nếu chưa ghi object.

```bash
git status -sb
git diff
git diff --cached
git reflog -8
```

</details>

- [Quay lại đầu trang](#back-to-top)
