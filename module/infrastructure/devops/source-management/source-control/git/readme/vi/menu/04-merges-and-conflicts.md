<a id="back-to-top"></a>

# Hợp nhất nhánh và giải quyết xung đột

## Menu
- [Phân kỳ lịch sử khi các nhánh cùng phát triển](#why-branches-diverge)
- [Fast-forward merge và chuyển dịch tham chiếu nhánh](#fast-forward-merge)
- [Merge base và cơ chế hợp nhất ba chiều](#merge-base-and-three-way)
- [Merge commit và nhiều quan hệ commit cha](#merge-commit-and-parents)
- [Dấu hiệu xung đột tệp và trạng thái merge đang diễn ra](#recognize-merge-conflicts)
- [Quy trình giải quyết, hoàn tất hoặc hủy một merge](#resolve-or-abort-merge)
- [Cherry-pick từng commit so với merge cả nhánh](#cherry-pick-vs-merge)
- [Bằng chứng về nội dung và đồ thị commit sau merge](#verify-merged-outcome)

## <a id="why-branches-diverge">Phân kỳ lịch sử khi các nhánh cùng phát triển</a>

<details>
<summary>Xem chi tiết</summary>

Hai nhánh bắt đầu từ cùng commit cơ sở có thể nhận các commit khác nhau: lịch sử **phân kỳ (divergence)** tạo hai đỉnh không nằm trên cùng một đường parent. Không phải lỗi; đó là cơ chế phát triển song song. Thách thức chỉ xuất hiện khi cần tổng hợp các thay đổi hoặc cập nhật nhánh từ một remote đã tiến lên.

Dùng `git log --oneline --graph --all` để thấy chỗ rẽ. Không suy ra có xung đột nội dung chỉ vì đồ thị phân kỳ: hai nhánh có thể sửa tệp khác nhau và merge tự động được.

```bash
git log --oneline --graph --all --decorate
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fast-forward-merge">Fast-forward merge và chuyển dịch tham chiếu nhánh</a>

<details>
<summary>Xem chi tiết</summary>

Nếu tip của nhánh đích là **tổ tiên** của nhánh nguồn, Git có thể thực hiện **fast-forward**: dịch ref đích lên commit nguồn, không cần merge commit mới. Lệnh `git merge --ff-only topic` trong nhánh đích vừa kiểm tra vừa yêu cầu điều kiện này; nếu hai nhánh đã phân kỳ, nó từ chối.

Để nhìn kết quả, ghi `git rev-parse HEAD` trước/sau và xem `git log --graph`: parent graph không có node merge hai cha mới. Đừng dùng `--ff-only` để mong Git giải quyết xung đột từ hai nhánh phân kỳ.

```bash
git merge --ff-only topic
git log --oneline --graph -6
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="merge-base-and-three-way">Merge base và cơ chế hợp nhất ba chiều</a>

<details>
<summary>Xem chi tiết</summary>

Khi hai nhánh phân kỳ, Git thường xác định **merge base** (tổ tiên chung thích hợp), so thay đổi từ base tới mỗi tip, rồi kết hợp bằng merge ba chiều. Nếu cùng dòng mã bị chỉnh không tương thích, Git không đoán ý định và báo conflict; nếu thay đổi độc lập, Git có thể tự hợp nhất.

`git merge-base main topic` cho commit tổ tiên dùng để suy luận; trong lịch sử phức tạp có thể có nhiều base ứng viên. Quy trình kỹ thuật thuộc Git, còn quy định nhánh nào được merge lúc nào thuộc module branching-strategy.

```bash
git merge-base main topic
git diff main...topic --stat
```

### Tài liệu tham khảo

- [Git documentation](https://git-scm.com/docs/git-merge-base)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="merge-commit-and-parents">Merge commit và nhiều quan hệ commit cha</a>

<details>
<summary>Xem chi tiết</summary>

Merge không fast-forward thông thường tạo **merge commit** có ít nhất hai parent: parent thứ nhất là tip của nhánh đang checkout trước merge, parent kế là tip được merge. Tree kết quả mô tả phiên bản hợp nhất. Thông điệp merge ghi dấu sự kiện tích hợp, nhưng không thay thế việc kiểm tra các dòng mã.

Sau merge, `git rev-list --parents -n 1 HEAD` hiển thị ID merge và danh sách parent. `git show --no-patch --pretty=raw HEAD` cho metadata; đừng giả định `git show HEAD` thông thường luôn hiện toàn bộ diff của merge giống commit đơn.

```bash
git rev-list --parents -n 1 HEAD
git show --no-patch --pretty=raw HEAD
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="recognize-merge-conflicts">Dấu hiệu xung đột tệp và trạng thái merge đang diễn ra</a>

<details>
<summary>Xem chi tiết</summary>

Khi Git không thể tự hợp nhất nội dung, `git status` có các **unmerged paths**. Tệp văn bản có thể chứa dấu `<<<<<<<`, `=======`, `>>>>>>>`: đây là các phiên bản cần người phát triển tổng hợp theo ý nghĩa nghiệp vụ, không phải đoạn để xóa ngẫu nhiên. `git diff --name-only --diff-filter=U` liệt kê các path còn xung đột.

`git ls-files -u` cho các stage index của base/ours/theirs ở path unmerged. Với lỗi binary hoặc rename, marker có thể không xuất hiện; hãy tin trạng thái Git chứ không chỉ tìm marker.

```bash
git status
git diff --name-only --diff-filter=U
git ls-files -u
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resolve-or-abort-merge">Quy trình giải quyết, hoàn tất hoặc hủy một merge</a>

<details>
<summary>Xem chi tiết</summary>

Trong merge có xung đột: đọc trạng thái, mở tệp cần sửa, tổng hợp nội dung đúng, chạy kiểm thử phù hợp rồi `git add -- path` để đánh dấu đã xử lý. Khi không còn unmerged paths, hoàn tất bằng `git commit` (hoặc `git merge --continue` khi phiên bản hỗ trợ). Nếu không muốn tiếp tục, `git merge --abort` cố phục hồi trạng thái trước merge.

**Cảnh báo:** `--abort` có thể khó khôi phục chính xác thay đổi chưa commit đã đan xen; nên bắt đầu merge từ working tree sạch. Sau hoàn tất phải kiểm tra test/diff vì Git hợp nhất được về cú pháp không chứng minh logic đúng.

```bash
git status
git add -- src.txt
git diff --cached --check
git merge --continue
```

### Tài liệu tham khảo

- [Git documentation](https://git-scm.com/book/en/v2/Git-Branching-Basic-Branching-and-Merging)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cherry-pick-vs-merge">Cherry-pick từng commit so với merge cả nhánh</a>

<details>
<summary>Xem chi tiết</summary>

`git merge topic` tích hợp lịch sử nhánh theo quan hệ cha. `git cherry-pick <commit>` lấy thay đổi mà một commit gây ra và **tạo commit mới trên nhánh hiện tại**; không nhập toàn bộ lịch sử topic. Dùng khi chỉ muốn một sửa lỗi cụ thể, nhưng commit ID mới thường khác bản gốc.

Khi cherry-pick xung đột, sửa và stage path, sau đó `git cherry-pick --continue`, hoặc `--abort` nếu bỏ. Nếu commit nguồn là merge commit, việc cherry-pick cần chọn mainline parent bằng `-m`; không đoán `-m 1` khi chưa hiểu đồ thị.

```bash
git cherry-pick <commit-id>
git cherry-pick --continue
git cherry-pick --abort
```

### Tài liệu tham khảo

- [Git documentation](https://git-scm.com/docs/git-cherry-pick)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="verify-merged-outcome">Bằng chứng về nội dung và đồ thị commit sau merge</a>

<details>
<summary>Xem chi tiết</summary>

Sau merge, chứng cứ cần cả **nội dung** lẫn **đồ thị**: `git status` để chắc không còn merge dở, `git log --graph --decorate` để thấy lịch sử, `git diff <base> HEAD --stat` để tóm lược nội dung và test dự án để xác nhận hành vi. Có thể dùng `git merge-base --is-ancestor topic HEAD` để kiểm tra commit topic đã thuộc ancestry của HEAD (exit 0 nếu đúng).

Một cherry-pick có thể mang cùng patch mà **không** mang ancestry nguồn; đừng dùng kiểm tra tổ tiên để kết luận cherry-pick không thành công.

```bash
git status
git log --graph --oneline --decorate -12
git merge-base --is-ancestor topic HEAD
```

</details>

- [Quay lại đầu trang](#back-to-top)
