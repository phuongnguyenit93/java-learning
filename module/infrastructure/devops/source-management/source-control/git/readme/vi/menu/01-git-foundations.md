<a id="back-to-top"></a>

# Nền tảng Git: repository, trạng thái và snapshot

## Menu
- [Git: khái niệm và vai trò kiểm soát phiên bản phân tán](#what-is-git)
- [Bản chụp trạng thái (snapshot) và lý do Git lưu lịch sử theo phiên bản](#why-git-snapshots)
- [Git repository và working tree: khái niệm và phạm vi](#repository-and-working-tree)
- [Index (staging area) trong mô hình ba vùng của Git](#index-and-three-areas)
- [Git objects, snapshots và quan hệ giữa các commit](#git-objects-and-commits)
- [Trạng thái tracked, untracked, modified, staged và committed](#file-tracking-and-states)
- [Khởi tạo repository và sao chép lịch sử bằng clone](#initialize-and-clone)
- [Tình huống theo dõi trạng thái tệp qua ba vùng của Git](#trace-file-across-areas)
- [Bảng tra cứu lệnh Git cơ bản và tài liệu tham khảo chính thức](#git-command-reference-and-docs)

## <a id="what-is-git">Git: khái niệm và vai trò kiểm soát phiên bản phân tán</a>

<details>
<summary>Xem chi tiết</summary>

Git là **hệ thống kiểm soát phiên bản phân tán (DVCS)**: mỗi bản clone thông thường có cơ sở dữ liệu commit để đọc lịch sử và tạo commit ngay cả khi không kết nối máy chủ. Nó giải quyết bài toán nhiều bản sửa tệp không thể truy vết: ai đã ghi nhận trạng thái nào, lịch sử phân nhánh ở đâu, và có thể quay về mốc nào. Git quản lý **phiên bản mã nguồn**, không tự phê duyệt pull request hay triển khai ứng dụng.

Để bắt đầu, cần biết thao tác với thư mục/terminal và tệp mã nguồn; không cần tài khoản GitHub. Sau này ta sẽ quan sát ba vùng của Git, ghi snapshot rồi trao đổi commit giữa các kho.

**Lộ trình học:** chương này giới thiệu repository, working tree, index, commit và các trạng thái tệp. Tiếp đó ta thực hành ghi commit và đọc lịch sử, học nhánh/HEAD/tag, hợp nhất và giải quyết xung đột, rồi trao đổi lịch sử với remote. Sau khi nắm những cơ chế đó mới xét rebase, hoàn tác và phục hồi; chương cuối nối chúng trong một tình huống Git hoàn chỉnh. Việc ai duyệt PR và khi nào phát hành thuộc nền tảng cộng tác/chính sách nhóm, không phải lệnh Git.

```bash
git --version
git help -a
```

### Tài liệu tham khảo

- [Git official documentation](https://git-scm.com/book/en/v2/Getting-Started-What-is-Git%3F)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="why-git-snapshots">Bản chụp trạng thái (snapshot) và lý do Git lưu lịch sử theo phiên bản</a>

<details>
<summary>Xem chi tiết</summary>

Git chủ yếu biểu diễn một commit như **ảnh chụp cây tệp được theo dõi tại một thời điểm**, không chỉ là nhật ký thao tác sửa dòng. Hai commit có thể dùng lại cùng đối tượng nội dung khi tệp không đổi; lịch sử kết nối các snapshot qua quan hệ cha. Vì vậy có thể xác định phiên bản hoàn chỉnh, kiểm tra khác biệt, và khôi phục trạng thái có chủ đích.

Ví dụ, khi sửa `app.txt` ở hai bước, hai commit ghi hai trạng thái có thể đọc độc lập. `git show HEAD:app.txt` đọc nội dung được lưu ở commit hiện tại; nó không nhất thiết giống tệp đang mở nếu bạn đã sửa sau lần commit đó.

```bash
git log --oneline -3
git show HEAD:app.txt
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="repository-and-working-tree">Git repository và working tree: khái niệm và phạm vi</a>

<details>
<summary>Xem chi tiết</summary>

**Repository (kho Git)** chứa dữ liệu đối tượng, tham chiếu và cấu hình thường nằm trong `.git/` của một working tree thông thường. **Working tree** là các tệp được checkout để bạn đọc và chỉnh sửa. Một tệp thay đổi trên đĩa chưa đồng nghĩa commit đã đổi; lịch sử được lưu riêng khỏi bản đang chỉnh sửa.

Chạy `git rev-parse --show-toplevel` từ thư mục con của repository để tìm thư mục gốc. `git status` cho biết working tree/index có khác commit hiện tại không. Trường hợp bare repository có thể **không có working tree**, thường dùng để trao đổi lịch sử trên server.

```bash
git rev-parse --show-toplevel
git status --short
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="index-and-three-areas">Index (staging area) trong mô hình ba vùng của Git</a>

<details>
<summary>Xem chi tiết</summary>

Mô hình thực dụng của Git gồm **working tree → index (staging area) → repository**. Working tree là bản đang sửa; index chọn chính xác snapshot sẽ được commit; repository giữ các commit đã tạo. `git add` chép nội dung phiên bản hiện tại của đường dẫn vào index, còn `git commit` ghi snapshot từ index chứ không tự lấy mọi thay đổi trên đĩa.

Nếu chạy `git add notes.txt` rồi tiếp tục sửa `notes.txt`, index vẫn chứa phiên bản ở thời điểm `add`. `git diff` quan sát phần chưa staged; `git diff --cached` quan sát phần chuẩn bị commit.

```bash
git add notes.txt
git diff -- notes.txt
git diff --cached -- notes.txt
```

### Tài liệu tham khảo

- [Git official documentation](https://git-scm.com/book/en/v2/Git-Tools-Reset-Demystified)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="git-objects-and-commits">Git objects, snapshots và quan hệ giữa các commit</a>

<details>
<summary>Xem chi tiết</summary>

Git dùng **blob** để lưu nội dung tệp, **tree** để mô tả đường dẫn/tên và trỏ tới blob hoặc tree con, **commit** để tham chiếu tree, cha và metadata tác giả/thời gian/thông điệp. Một commit thông thường có một parent; commit merge thường có hai hoặc nhiều parent; commit gốc không có parent. Nội dung và quan hệ này tạo thành đồ thị commit.

Bạn có thể kiểm tra `git cat-file -t HEAD` thấy loại đối tượng commit, `git cat-file -p HEAD` thấy tree và parent của commit; hash định danh phụ thuộc nội dung đối tượng nên thay parent/thông điệp tạo ID khác. Không sửa trực tiếp `.git/objects` để học cơ chế.

```bash
git cat-file -t HEAD
git cat-file -p HEAD
```

### Tài liệu tham khảo

- [Git official documentation](https://git-scm.com/book/en/v2/Git-Internals-Git-Objects)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-tracking-and-states">Trạng thái tracked, untracked, modified, staged và committed</a>

<details>
<summary>Xem chi tiết</summary>

Tệp **untracked** chưa nằm trong index; tệp **tracked** đã được index/Git quản lý. Tệp tracked có thể bị sửa trong working tree (**modified**) hoặc có phiên bản chờ commit trong index (**staged**). **Committed** nghĩa là trạng thái index đã thành snapshot; một tệp vẫn có thể staged và modified *cùng lúc* nếu sửa tiếp sau staging.

Trong `git status --short`, cột đầu phản ánh chênh lệch index với HEAD, cột sau phản ánh working tree với index; `??` chỉ tệp untracked. Vì vậy không nên gộp mọi thay đổi thành một nhãn “đã sửa”.

```bash
git status --short
git status
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="initialize-and-clone">Khởi tạo repository và sao chép lịch sử bằng clone</a>

<details>
<summary>Xem chi tiết</summary>

`git init` khởi tạo dữ liệu Git trong thư mục hiện có nhưng **chưa tự tạo commit**. `git clone <url> <thư-mục>` tải lịch sử/refs từ repository có sẵn và tạo working tree khi phù hợp; remote mặc định thường tên `origin`. Hai lựa chọn khác mục đích: khởi tạo dự án mới và tham gia lịch sử đã tồn tại.

Thực hành an toàn trong thư mục thử mới: tạo `demo`, chạy `git init`, thêm tệp, `git add` và commit sau khi cấu hình danh tính. Đừng chạy `init` vào mã nguồn thật khi bạn chưa chắc nó vốn thuộc repository nào.

```bash
mkdir demo
cd demo
git init
git status
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="trace-file-across-areas">Tình huống theo dõi trạng thái tệp qua ba vùng của Git</a>

<details>
<summary>Xem chi tiết</summary>

Xét `notes.txt` mới: ban đầu `git status --short` báo `?? notes.txt`. Sau `git add notes.txt`, cột index thể hiện thêm tệp; sau `git commit` lịch sử có snapshot đầu tiên. Khi bạn sửa tệp rồi chỉ staged một lần, phần thay đổi tiếp theo vẫn tồn tại ngoài index cho đến khi staged lại.

Để quan sát có thể tái hiện trong kho thử: kiểm tra `status` ở từng bước; trước commit so `git diff --cached`; sau commit đọc `git show HEAD:notes.txt`. Điều được kiểm chứng là **ba vùng có thể giữ ba phiên bản khác nhau của cùng đường dẫn**.

```bash
git status --short
git add notes.txt
git diff --cached -- notes.txt
git commit -m "Record notes"
git show HEAD:notes.txt
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="git-command-reference-and-docs">Bảng tra cứu lệnh Git cơ bản và tài liệu tham khảo chính thức</a>

<details>
<summary>Xem chi tiết</summary>

Đây là **bảng tra nhanh**, không thay thế hiểu ba vùng hay phân loại rủi ro. **Các hàng là lựa chọn độc lập, không phải script chạy từ trên xuống.** Đầu tiên đọc `status`, `diff`, `log` và xác định commit đã được chia sẻ hay chưa; chỉ dùng lệnh thay đổi trạng thái sau khi hiểu tác động.

| Mục đích | Lệnh | Lưu ý |
| --- | --- | --- |
| Xem tình trạng | `git status -sb` | Chỉ đọc |
| Xem diff chưa staged / staged | `git diff` / `git diff --cached` | Hai vùng khác nhau |
| Lưu snapshot | `git add <path>`, `git commit -m "message"` | Commit lấy từ index |
| Lịch sử và tham chiếu | `git log --oneline --graph --decorate` | Chỉ đọc |
| Tạo/chuyển nhánh | `git switch -c topic`, `git switch main` | Kiểm tra thay đổi chưa lưu |
| Cập nhật remote | `git fetch origin`, `git push -u origin topic` | Push tác động tới người khác |
| Bỏ stage nhưng giữ nội dung working tree | `git restore --staged -- <path>` | Đổi index; không xóa bản đang sửa |
| Bỏ sửa đổi tracked chưa stage | `git restore -- <path>` | **Ghi đè** nội dung working tree; sao lưu trước |
| Di chuyển nhánh/HEAD và điều chỉnh index | `git reset --soft <commit>` / `git reset --mixed <commit>` | Dùng cho lịch sử local chưa chia sẻ; `soft` giữ index, `mixed` reset index |
| Đặt cả HEAD, index, working tree về commit | `git reset --hard <commit>` | **Nguy cơ mất dữ liệu chưa commit**; có thể ghi đè untracked cản đường |
| Đảo thay đổi đã chia sẻ mà giữ commit cũ | `git revert <commit>` | Tạo commit đảo ngược **mới**, có thể conflict; kiểm tra merge-parent khi revert merge |
| Tìm vị trí ref cũ để cứu commit | `git reflog` | Chỉ có ở local và có thời hạn; **không** khôi phục tệp chưa từng được ghi |
| Phát lại commit lên base mới | `git rebase main` trên nhánh topic | Có thể đổi commit ID; tránh rebase commit người khác đang dùng |

**Chọn theo vùng dữ liệu:** `restore --staged` cho index; `restore` cho working tree; `reset` di chuyển ref (và có thể index/tree); `revert` ghi commit mới, không viết lại lịch sử đã chia sẻ; `reflog` chỉ tra mốc ref từng tồn tại; `rebase` phát lại commit và có thể đổi SHA. Tra cú pháp/phiên bản thực tế tại `git help <command>`; trước thao tác phá hủy, hãy có bản sao an toàn.

```bash
git help status
git help restore
```

### Tài liệu tham khảo

- [Git official documentation](https://git-scm.com/docs)
- [git-reset: các chế độ và giới hạn dữ liệu](https://git-scm.com/docs/git-reset)
- [git-revert: commit đảo thay đổi](https://git-scm.com/docs/git-revert)
- [git-reflog: lịch sử di chuyển tham chiếu](https://git-scm.com/docs/git-reflog)
- [git-rebase: phát lại commit](https://git-scm.com/docs/git-rebase)

</details>

- [Quay lại đầu trang](#back-to-top)
