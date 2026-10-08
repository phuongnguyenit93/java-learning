<a id="back-to-top"></a>

# Remote, nhánh theo dõi và đồng bộ lịch sử

## Menu
- [Remote repository: vai trò chia sẻ và trao đổi lịch sử Git](#remote-repository-purpose)
- [Cấu hình và kiểm tra remote như origin](#configure-and-inspect-remotes)
- [Nhánh cục bộ, nhánh trên máy chủ và remote-tracking references](#local-remote-and-tracking-refs)
- [Quan hệ upstream của nhánh cục bộ](#upstream-relationship)
- [Khác biệt giữa git fetch và git pull trong việc đồng bộ lịch sử](#fetch-vs-pull)
- [git push, cập nhật nhánh từ xa và trường hợp bị từ chối](#push-and-rejected-updates)
- [Hòa giải lịch sử phân kỳ trước khi chia sẻ thay đổi](#reconcile-divergent-history)
- [Bằng chứng tham chiếu trước và sau đồng bộ lịch sử](#verify-local-and-remote-history)

## <a id="remote-repository-purpose">Remote repository: vai trò chia sẻ và trao đổi lịch sử Git</a>

<details>
<summary>Xem chi tiết</summary>

Remote là repository Git khác, thường nằm trên máy chủ, được đặt tên cục bộ để trao đổi commit/refs. Git vẫn tạo commit **cục bộ** trước; remote cung cấp nơi chia sẻ và nhận lịch sử, không tự kiểm duyệt code. Một repo có thể có nhiều remote, tên `origin` chỉ là quy ước.

`git remote -v` cho danh sách tên và URL fetch/push; URL có thể là HTTPS hoặc SSH. Không chia sẻ token trong URL; quyền repository trên dịch vụ hosting thuộc nền tảng cộng tác chứ không phải cơ chế ref Git.

```bash
git remote -v
git remote show origin
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configure-and-inspect-remotes">Cấu hình và kiểm tra remote như origin</a>

<details>
<summary>Xem chi tiết</summary>

Dùng `git remote add origin <url>` khi repo chưa có remote tên đó; `git remote set-url origin <url>` thay địa chỉ của tên sẵn có. `git remote -v` chỉ đọc cấu hình, không chứng minh server còn truy cập được; `git ls-remote --heads origin` hỏi server về các nhánh nếu có quyền/kết nối.

Không thêm remote trùng tên hoặc đổi URL vào server lạ trước khi kiểm tra nguồn. Khi dùng mirror/nhánh riêng, xác định rõ nơi fetch và nơi push để tránh công bố nhầm lịch sử.

```bash
git remote -v
git ls-remote --heads origin
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="local-remote-and-tracking-refs">Nhánh cục bộ, nhánh trên máy chủ và remote-tracking references</a>

<details>
<summary>Xem chi tiết</summary>

Nhánh `main` local (`refs/heads/main`) khác với ref **remote-tracking** `origin/main` (`refs/remotes/origin/main`), vốn là lần quan sát gần nhất của Git cục bộ về nhánh trên remote. `origin/main` không cập nhật tức thời khi người khác push; `git fetch origin` mới cập nhật các refs theo fetch refspec.

`git branch -avv` hiển thị cả nhánh local và remote-tracking refs. Lưu ý remote-tracking ref không phải “nhánh đang chạy trên server”: server có ref riêng, local chỉ lưu bản theo dõi.

```bash
git branch -avv
git show-ref --heads
git show-ref
```

### Tài liệu tham khảo

- [Git documentation](https://git-scm.com/book/en/v2/Git-Branching-Remote-Branches)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="upstream-relationship">Quan hệ upstream của nhánh cục bộ</a>

<details>
<summary>Xem chi tiết</summary>

Upstream là quan hệ cấu hình cho nhánh local, cho biết Git so với nhánh nào khi tính ahead/behind và mặc định của một số thao tác pull/push tùy cấu hình. Thiết lập thông thường bằng `git push -u origin topic` khi công bố lần đầu; hoặc `git branch --set-upstream-to=origin/main main` nếu ref tồn tại.

`git branch -vv` hiển thị tracking và số commit ahead/behind tính theo thông tin đã fetch. Số liệu có thể cũ nếu chưa fetch; upstream **không** tự động chuyển commit local lên server.

```bash
git branch -vv
git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fetch-vs-pull">Khác biệt giữa git fetch và git pull trong việc đồng bộ lịch sử</a>

<details>
<summary>Xem chi tiết</summary>

`git fetch origin` tải objects và cập nhật các remote-tracking refs, **không tự tích hợp vào nhánh đang checkout**. `git pull` thực chất fetch rồi tích hợp vào nhánh hiện tại theo cấu hình hoặc đối số: có thể merge, rebase hoặc chỉ fast-forward; Git hiện đại có thể yêu cầu chọn cách xử lý khi lịch sử phân kỳ.

Để chủ động, chạy `git fetch origin`, xem `git log --left-right --oneline HEAD...origin/main`, rồi `git merge origin/main` hoặc rebase theo ngữ cảnh. Đừng mặc định `pull` luôn “chỉ tải xuống”.

```bash
git fetch origin
git log --left-right --oneline HEAD...origin/main
```

### Tài liệu tham khảo

- [Git documentation](https://git-scm.com/docs/git-pull)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="push-and-rejected-updates">git push, cập nhật nhánh từ xa và trường hợp bị từ chối</a>

<details>
<summary>Xem chi tiết</summary>

`git push origin topic` đề nghị server cập nhật ref nhánh `topic` theo commit local, sau khi upload objects cần thiết. Push có thể bị từ chối do thiếu quyền, rule server, hoặc **non-fast-forward**: nhánh server đã có commit mà local chưa chứa và cập nhật sẽ làm mất vị trí lịch sử.

Cách an toàn khi non-fast-forward: fetch, xem commit hai phía, tích hợp thay đổi và push lại. `--force` có thể thay thế lịch sử người khác; `--force-with-lease` có điều kiện bảo vệ tốt hơn nhưng vẫn là thao tác viết lại remote, không dùng như giải pháp mặc định.

```bash
git fetch origin
git log --oneline --left-right origin/topic...topic
git push origin topic
```

### Tài liệu tham khảo

- [Git documentation](https://git-scm.com/docs/git-push)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reconcile-divergent-history">Hòa giải lịch sử phân kỳ trước khi chia sẻ thay đổi</a>

<details>
<summary>Xem chi tiết</summary>

Khi local và `origin/main` đều có commit riêng, đồ thị phân kỳ. Kiểm tra `git merge-base main origin/main` rồi `git log --left-right --graph main...origin/main`. Với lịch sử đã chia sẻ, merge thường giữ nguyên commit IDs của hai phía; rebase thay ID commit local khi phát lại, phù hợp hơn với commit chưa công bố.

Trước tích hợp bảo đảm working tree sạch và đã biết upstream/branch đích. Sau xử lý conflict nếu có, kiểm tra status, log và test; không tự động force-push chỉ vì đã rebase.

```bash
git merge-base main origin/main
git log --left-right --graph --oneline main...origin/main
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="verify-local-and-remote-history">Bằng chứng tham chiếu trước và sau đồng bộ lịch sử</a>

<details>
<summary>Xem chi tiết</summary>

Trước fetch, `origin/main` phản ánh **lần quan sát trước**. Sau fetch, `git rev-parse origin/main` có thể thay ID nếu server tiến lên; ref `main` local không tự đổi. Sau merge/pull, `git rev-parse main` và graph cho thấy đã tích hợp hay chưa.

`git status -sb` ghi ahead/behind theo upstream đã biết; muốn biết server hiện thực là gì, dùng `git ls-remote origin refs/heads/main` khi trực tuyến rồi đối chiếu hash. Đừng nhầm hai lệnh đọc local và server.

```bash
git rev-parse main
git rev-parse origin/main
git ls-remote origin refs/heads/main
```

</details>

- [Quay lại đầu trang](#back-to-top)
