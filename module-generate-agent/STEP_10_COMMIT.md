# Step 10 - Commit / Push / Merge Request Rules

Step 10 là bước cuối cùng sau khi module đã hoàn tất và validation đã pass:

```text
commit
→ push module branch
→ create Merge Request vào main
```

## CLI-only execution contract

Toàn bộ Step 10 phải được thực hiện bằng **terminal / CLI**. Không được dùng browser UI, desktop GUI automation hoặc thao tác click thủ công trên Git hosting để hoàn thành bất kỳ phần nào của workflow.

Các thao tác thuộc contract này bao gồm:

```text
inspect git state
→ commit
→ push
→ create Pull Request / Merge Request
→ inspect checks / mergeability
→ accept / merge khi workflow đã được authorize
→ verify remote branch / remote main
→ delete remote source branch khi cần
→ cleanup local branch / worktree
```

Dùng CLI phù hợp với môi trường, ví dụ `git` cho Git operations và Git-hosting CLI như `gh`, `glab` hoặc CLI tương đương cho Pull Request / Merge Request operations.

**Không được fallback sang browser** nếu CLI không khả dụng.

Nếu một thao tác bắt buộc của Step 10 không thể thực hiện bằng CLI vì CLI chưa cài, chưa authenticate, thiếu permission, command/capability không được hỗ trợ, required checks/branch protection/conflict đang block, hoặc lỗi môi trường khác:

```text
STOP Step 10 tại trạng thái an toàn hiện tại
→ không mở browser để tiếp tục
→ không giả vờ workflow đã hoàn tất
→ không cleanup branch/worktree nếu merge chưa được verify
→ report chính xác phần đã hoàn thành
→ report current branch / commit / remote state / PR-MR state khi xác định được
→ report blocker CLI cụ thể và bước còn lại chưa thực hiện
```

Nếu CLI yêu cầu browser để interactive authentication, không dùng browser-auth fallback trong Step 10. Authentication cần được setup sẵn bằng phương thức CLI phù hợp; nếu chưa có thì stop và report blocker.

Rule CLI-only này áp dụng bất kể hosting gọi artifact là **Pull Request (GitHub)** hay **Merge Request (GitLab)**.

## Commit message

Commit message phải dùng đúng format:

```text
<MODULE> generate content - GPT Sol 5.6
```

Trong đó:

```text
<MODULE>
→ tên module đang được generate/refactor
```

Ví dụ:

```text
Language Basics generate content - GPT Sol 5.6
```

Trước khi commit, chỉ include các thay đổi thuộc đúng module/task đang thực hiện; không sweep unrelated working-tree changes vào commit.

## Push branch

Sau khi commit thành công, push **current module branch** lên remote.

Expected branch:

```text
module/<module-name>
```

Không push module-generation commit trực tiếp lên `main`.

## Merge Request

Sau khi push branch thành công, tạo Merge Request:

```text
source branch
→ current module branch

target branch
→ main
```

MR title dùng cùng format với commit message:

```text
<MODULE> generate content - GPT Sol 5.6
```

Step 10 chỉ tạo Merge Request vào `main`; không auto-merge nếu user chưa yêu cầu merge.

## Sau khi Merge Request đã được merge

Khi Merge Request của module đã được merge xong vào `main`, session hiện tại phải quay lại **main worktree ban đầu**.

```text
module worktree
→ MR merged into main
→ session returns to main worktree
```

Sau thời điểm này, module worktree không còn là workspace active của task vừa hoàn tất.

Việc quay lại main worktree không đồng nghĩa tự động xóa module worktree hoặc branch. Cleanup worktree/branch là thao tác riêng và chỉ thực hiện khi phù hợp với workflow hiện tại hoặc khi user yêu cầu.
