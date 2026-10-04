# Step 10 - Commit / Push / Merge Request Rules

Step 10 là bước cuối cùng sau khi module đã hoàn tất và validation đã pass:

```text
commit
→ push module branch
→ create Merge Request vào main
```

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
