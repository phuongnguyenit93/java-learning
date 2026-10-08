<a id="back-to-top"></a>

# Ghi nhận thay đổi và kiểm tra lịch sử commit

## Menu
- [Kiểm tra trạng thái thay đổi bằng git status](#inspect-status-before-staging)
- [Quy tắc .gitignore và ranh giới theo dõi tệp](#ignore-and-track-files)
- [Khác biệt giữa nội dung working tree và index](#working-tree-vs-staged-diff)
- [Chọn lọc nội dung cần ghi nhận bằng staging](#stage-intentional-changes)
- [Commit: bản chụp có chủ đích và thông điệp thay đổi](#commit-snapshot-and-message)
- [Lịch sử git log, commit ID và quan hệ cha–con](#read-log-and-parents)
- [So sánh các mốc commit và phiên bản tệp](#compare-history-points)
- [Bằng chứng phân biệt nội dung đã commit và thay đổi chưa ghi nhận](#verify-a-recorded-change)

## <a id="inspect-status-before-staging">Kiểm tra trạng thái thay đổi bằng git status</a>

<details>
<summary>Xem chi tiết</summary>

Trước khi lưu commit, dùng `git status` để phân loại **untracked / modified / staged** và biết tên nhánh. `--short` dùng hai cột trạng thái; `-sb` thêm branch và tracking information khi có. Lệnh này **không tự stage**, nên là bước chẩn đoán an toàn trước mọi thay đổi.

Trong kho thử, sửa một tệp tracked và tạo tệp khác chưa theo dõi; `git status -sb` phân biệt hai trường hợp. Sau `git add`, chạy lại; chỉ khi thấy đúng nội dung đã staged mới nên commit.

```bash
git status -sb
git status --short
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ignore-and-track-files">Quy tắc .gitignore và ranh giới theo dõi tệp</a>

<details>
<summary>Xem chi tiết</summary>

Tệp cấu hình máy cá nhân, output build và bí mật không nên tự động thành nội dung lịch sử. `.gitignore` loại trừ **tệp chưa được track** khớp mẫu; nó không ngừng track một tệp đã ở index. Muốn bỏ track nhưng giữ file trên đĩa, cân nhắc `git rm --cached -- path`, rồi commit thay đổi index; với dữ liệu nhạy cảm đã từng commit, thao tác này **không xóa lịch sử cũ**.

Dùng `git check-ignore -v -- file.log` để xác định mẫu ignore đang có hiệu lực (đối với tệp untracked). `git status --ignored` giúp xem ignore nhưng phải thận trọng với tệp chứa token đã từng được commit.

```bash
git check-ignore -v -- build.log
git status --ignored --short
```

### Tài liệu tham khảo

- [Git official documentation](https://git-scm.com/docs/gitignore)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="working-tree-vs-staged-diff">Khác biệt giữa nội dung working tree và index</a>

<details>
<summary>Xem chi tiết</summary>

`git diff` mặc định so **working tree với index**: thay đổi chưa được staged. `git diff --cached` (tương đương `--staged`) so **index với HEAD**: điều sẽ được ghi vào commit. Trong repo chưa có HEAD commit, hãy kiểm tra hướng dẫn lệnh tương ứng thay vì giả định phép so HEAD luôn tồn tại.

Nếu `git add src.txt` rồi sửa tiếp `src.txt`, cả hai diff có thể đều có nội dung nhưng khác đoạn. Đây là cách phát hiện vô tình commit phiên bản cũ hơn phiên bản đang nhìn trong editor.

```bash
git diff -- src.txt
git diff --cached -- src.txt
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stage-intentional-changes">Chọn lọc nội dung cần ghi nhận bằng staging</a>

<details>
<summary>Xem chi tiết</summary>

Thao tác `git add path` cập nhật index với trạng thái hiện tại của đường dẫn; `git add -p` cho phép chọn từng hunk của tệp tracked (tương tác và cần đọc kỹ). `git add -A` đưa nhiều thay đổi trong phạm vi hiện tại vào index, dễ vô tình thêm file không mong muốn. Mục tiêu staging là **một commit chứa một ý tưởng đủ nhỏ để review và hoàn tác**.

Trước commit, xem `git diff --cached --stat` và `git diff --cached` để kiểm tra file/dòng. Muốn bỏ một đường dẫn khỏi staging nhưng giữ working tree, dùng `git restore --staged -- path`.

```bash
git add -p -- src.txt
git diff --cached --stat
git restore --staged -- src.txt
```

### Tài liệu tham khảo

- [Git official documentation](https://git-scm.com/book/en/v2/Git-Basics-Recording-Changes-to-the-Repository)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="commit-snapshot-and-message">Commit: bản chụp có chủ đích và thông điệp thay đổi</a>

<details>
<summary>Xem chi tiết</summary>

Commit ghi **snapshot của index** kèm thông điệp và metadata tác giả/committer. Trước lần commit đầu hãy cấu hình danh tính `user.name`, `user.email` (có thể chọn `--local` cho repo học). Thông điệp nên mô tả *lý do thay đổi* như “Validate empty email” thay vì “update”. Commit có thể tạo hash mới ngay cả khi sửa lại cùng nội dung do metadata/parent khác.

`git commit -m "Validate empty email"` chỉ thành công khi có thay đổi staged phù hợp; `git status` sau đó có thể vẫn báo modified nếu bạn đã sửa thêm ngoài index. Không dùng `git commit -a` như một thay thế tổng quát của staging: nó không tự thêm file hoàn toàn mới chưa tracked.

```bash
git config --local user.name "Student"
git config --local user.email "student@example.invalid"
git commit -m "Validate empty email"
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="read-log-and-parents">Lịch sử git log, commit ID và quan hệ cha–con</a>

<details>
<summary>Xem chi tiết</summary>

`git log --oneline --graph --decorate --all` hiển thị lịch sử và nhãn nhánh/tag. **Commit ID** nhận diện một đối tượng commit; hash viết tắt chỉ tiện khi không nhập nhằng. `HEAD~1` chỉ parent thứ nhất của HEAD; đối với merge commit có thể có nhiều parent như `HEAD^1`, `HEAD^2`.

Chạy `git show --no-patch --pretty=raw HEAD` để thấy parent header, message và metadata; đồ thị không phải thứ tự thời gian tuyến tính khi các nhánh phát triển độc lập.

```bash
git log --oneline --graph --decorate --all
git show --no-patch --pretty=raw HEAD
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compare-history-points">So sánh các mốc commit và phiên bản tệp</a>

<details>
<summary>Xem chi tiết</summary>

So hai snapshot bằng `git diff HEAD~1 HEAD -- path` khi HEAD đã có parent: nó thể hiện chênh lệch giữa phiên bản tệp ở hai commit. `git show --stat <commit>` cho phạm vi thay đổi của một commit; `git show <commit>:path` đọc nội dung tại snapshot mà không checkout.

Lưu ý `A..B` trong `git log` là phép chọn commit reachable từ B nhưng không reachable từ A; trong `git diff A B` hai đối số chỉ hai snapshot. Không nhầm **diff giữa file states** với **tập commit lịch sử**.

```bash
git diff HEAD~1 HEAD -- src.txt
git log --oneline HEAD~3..HEAD
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="verify-a-recorded-change">Bằng chứng phân biệt nội dung đã commit và thay đổi chưa ghi nhận</a>

<details>
<summary>Xem chi tiết</summary>

Bằng chứng một thay đổi đã commit không phải thông báo “save” trong editor mà là **object có thể đọc lại**. Sau commit, `git log -1 --oneline` cho commit mới; `git show --stat HEAD` cho các đường dẫn ghi nhận; `git status` cho biết còn thay đổi chưa commit hay không.

Để tránh suy luận sai, đối chiếu `git diff --cached` và `git diff` trước khi commit, rồi xem `git show HEAD:file` sau đó. Status sạch chỉ nói working tree/index đang khớp HEAD; nó **không** chứng minh đã push lên remote hoặc được review.

```bash
git log -1 --oneline
git show --stat HEAD
git status --short
```

</details>

- [Quay lại đầu trang](#back-to-top)
