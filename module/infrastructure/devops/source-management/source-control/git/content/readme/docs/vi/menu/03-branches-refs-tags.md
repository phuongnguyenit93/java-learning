<a id="back-to-top"></a>

# Nhánh, HEAD, tham chiếu và tag

## Menu
- [Đồ thị commit và khái niệm tham chiếu Git](#commit-graph-and-references)
- [Nhánh cục bộ và HEAD trong mô hình tham chiếu](#branches-and-head)
- [Vòng thao tác tạo, kiểm tra và xóa nhánh cục bộ](#create-inspect-delete-branches)
- [Chuyển nhánh với thay đổi chưa commit và các giới hạn an toàn](#switch-and-uncommitted-changes)
- [Detached HEAD: trạng thái và rủi ro tham chiếu không gắn nhánh](#detached-head)
- [Tag cố định và nhánh di chuyển theo commit](#tags-vs-branches)
- [Bằng chứng vị trí HEAD, nhánh và tag trong lịch sử](#verify-branch-tag-positions)

## <a id="commit-graph-and-references">Đồ thị commit và khái niệm tham chiếu Git</a>

<details>
<summary>Xem chi tiết</summary>

Sau một commit, Git có đồ thị các commit nối bởi quan hệ cha. **Reference (ref)** là tên trỏ tới một object ID, thường là commit; ví dụ `refs/heads/main` hoặc `refs/tags/v1.0`. Dùng ref giúp không phải nhớ hash dài và hiểu nhánh nào đang chứa một commit.

`git log --graph --all --decorate` cho thấy đường đi parent và tên ref trang trí tại đỉnh; `git show-ref` liệt kê ref thật có trong repository. Đồ thị chỉ quan hệ lịch sử, không tự biểu thị một chính sách nhóm.

```bash
git log --graph --all --oneline --decorate
git show-ref
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="branches-and-head">Nhánh cục bộ và HEAD trong mô hình tham chiếu</a>

<details>
<summary>Xem chi tiết</summary>

**Nhánh cục bộ** là ref trong `refs/heads/`, thường di chuyển khi bạn tạo commit trên nhánh đó. **HEAD** thường là symbolic ref trỏ vào tên nhánh hiện tại; mỗi commit mới đưa nhánh hiện tại đến ID mới, còn các nhánh khác vẫn ở commit cũ.

`git symbolic-ref --short HEAD` cho tên nhánh nếu HEAD đang gắn vào nhánh; `git rev-parse HEAD` cho commit ID. Nếu HEAD detached, lệnh symbolic-ref không trả tên nhánh và Git báo lỗi — đó là một trạng thái hợp lệ, không phải tự động hỏng repository.

```bash
git symbolic-ref --short HEAD
git rev-parse HEAD
git branch -vv
```

### Tài liệu tham khảo

- [Git documentation](https://git-scm.com/book/en/v2/Git-Branching-Branches-in-a-Nutshell)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="create-inspect-delete-branches">Vòng thao tác tạo, kiểm tra và xóa nhánh cục bộ</a>

<details>
<summary>Xem chi tiết</summary>

Tạo `topic` từ commit hiện tại bằng `git switch -c topic`; Git tạo ref và checkout nó. Xem vị trí, quan hệ upstream bằng `git branch -vv`; chuyển về nhánh có sẵn bằng `git switch main` (chỉ dùng nếu repo thực sự có `main`).

Xóa an toàn `git branch -d topic` khi nhánh đã được tích hợp phù hợp; `-D` ép xóa ref ngay cả khi còn commit chưa merge, nên phải xác nhận hash cần giữ trước. Lệnh xóa nhánh **không lập tức xóa toàn bộ object**, nhưng không nên dựa vào garbage collection/reflog để cứu nhầm.

```bash
git switch -c topic
git branch -vv
git switch main
git branch -d topic
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="switch-and-uncommitted-changes">Chuyển nhánh với thay đổi chưa commit và các giới hạn an toàn</a>

<details>
<summary>Xem chi tiết</summary>

Chuyển nhánh thay đổi tệp đã checkout để phù hợp tree ở commit đích. Nếu thao tác sẽ ghi đè thay đổi working tree/index chưa commit, Git thường từ chối thay vì tự xóa; nhưng **không** giả định mọi thay đổi được giữ nguyên theo mong muốn. Luôn xem `git status` trước.

Cách an toàn là commit phần hoàn chỉnh, `git stash push` phần đang làm, hoặc dùng một working tree riêng khi phù hợp. `git switch -` trở lại nhánh checkout trước đó; sau chuyển kiểm tra trạng thái vì untracked file có thể gây xung đột tên đường dẫn.

```bash
git status --short
git switch -
git status --short
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="detached-head">Detached HEAD: trạng thái và rủi ro tham chiếu không gắn nhánh</a>

<details>
<summary>Xem chi tiết</summary>

**Detached HEAD** nghĩa HEAD trỏ trực tiếp tới một commit thay vì nhánh. Hữu ích khi xem hoặc chạy thử phiên bản cũ: `git switch --detach <commit>`. Nếu commit mới trong trạng thái này, nó tồn tại nhưng không có tên nhánh tự tăng theo; khi chuyển đi, có thể mất ref dễ tìm đến commit vừa tạo.

Trước khi rời detached HEAD với công việc cần giữ, tạo nhánh `git switch -c saved-work`. `git status -sb` giúp nhận diện HEAD detached; không xem đây là lỗi khi chỉ đọc lịch sử.

```bash
git switch --detach HEAD~1
git status -sb
git switch -c saved-work
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tags-vs-branches">Tag cố định và nhánh di chuyển theo commit</a>

<details>
<summary>Xem chi tiết</summary>

**Tag** đánh dấu một đối tượng/mốc phát hành và bình thường không di chuyển tự động theo commit mới. **Lightweight tag** là ref trực tiếp; **annotated tag** có object tag riêng với người tạo/thông điệp (và có thể chữ ký). Nhánh thường tiến lên khi thêm commit, tag vẫn ở mốc đã đánh dấu trừ khi ai đó cố ý sửa tag.

Dùng `git tag v1.0` hoặc `git tag -a v1.0 -m "Release v1.0"` trong repo thử sau khi có commit. `git show-ref --tags` xác nhận đích; xóa/chuyển tag đã công bố có thể gây sai lệch giữa bản clone.

```bash
git tag -a v1.0 -m "Release v1.0"
git show-ref --tags
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="verify-branch-tag-positions">Bằng chứng vị trí HEAD, nhánh và tag trong lịch sử</a>

<details>
<summary>Xem chi tiết</summary>

Đối chiếu `git branch --show-current`, `git rev-parse HEAD`, `git show-ref --heads --tags` và `git log --graph --decorate` để xác nhận nhánh/tag nào đang ở cùng commit. Nếu tạo commit trên nhánh hiện tại, commit ID mới và branch ref di chuyển, trong khi tag cũ đứng yên.

Một hash trùng ở hai ref chỉ nói chúng đang cùng trỏ đến một object, không chứng minh quyền truy cập hay đã push. Tham chiếu tại local là dữ liệu local cho đến khi trao đổi với remote.

```bash
git branch --show-current
git show-ref --heads --tags
git log --oneline --decorate -5
```

</details>

- [Quay lại đầu trang](#back-to-top)
