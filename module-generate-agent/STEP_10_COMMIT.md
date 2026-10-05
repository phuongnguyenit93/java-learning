# Step 10 - Commit / Push / Pull Request / Merge / Cleanup Rules

Step 10 là bước cuối cùng sau khi module đã hoàn tất và validation đã pass:

```text
commit
→ push module branch
→ create Pull Request / Merge Request vào main
→ verify remote source branch contains the final commit
→ accept / merge Pull Request / Merge Request vào main
→ verify merge succeeded + remote main contains final change
→ cleanup local module worktree + local module branch
→ sync local main with origin/main
```

## CLI-only execution contract

Toàn bộ Step 10 phải được thực hiện bằng **terminal / CLI**. Không được dùng browser UI, desktop GUI automation hoặc thao tác click thủ công trên Git hosting để hoàn thành bất kỳ phần nào của workflow.

Các thao tác thuộc contract này bao gồm toàn bộ chuỗi:

```text
inspect git state
→ commit
→ push
→ create Pull Request / Merge Request
→ inspect checks / mergeability
→ accept / merge when Step 10 has been explicitly requested
→ verify remote branch / remote main
→ delete remote source branch khi cần
→ cleanup local branch / worktree
→ pull / fast-forward local main from origin/main
```

Dùng CLI phù hợp với môi trường, ví dụ `git` cho Git operations và Git-hosting CLI như `gh`, `glab` hoặc CLI tương đương cho Pull Request / Merge Request operations.

**Không được fallback sang browser** nếu CLI không khả dụng.

Nếu tại bất kỳ thời điểm nào một thao tác bắt buộc của Step 10 không thể thực hiện bằng CLI vì CLI chưa cài, chưa authenticate, thiếu permission, command/capability không được hỗ trợ, required checks/branch protection/conflict đang block, hoặc lỗi môi trường khác:

```text
STOP Step 10 tại trạng thái an toàn hiện tại
→ không mở browser để tiếp tục
→ không giả vờ workflow đã hoàn tất
→ không cleanup branch/worktree nếu merge chưa được verify
→ report chính xác phần đã hoàn thành
→ report current branch / commit / remote state / PR-MR state khi xác định được
→ report blocker CLI cụ thể và bước còn lại chưa thực hiện
```

Nếu một CLI command có thể yêu cầu mở browser cho interactive authentication, không dùng browser-auth fallback trong Step 10. Authentication cần được setup sẵn bằng phương thức CLI phù hợp; nếu chưa có thì stop và report blocker.

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

## Pull Request / Merge Request

Sau khi push branch thành công, tạo Pull Request / Merge Request:

```text
source branch
→ current module branch

target branch
→ main
```

PR/MR title dùng cùng format với commit message:

```text
<MODULE> generate content - GPT Sol 5.6
```

### Step 10 authorization

Khi user **explicitly yêu cầu thực hiện / tiến hành Step 10** hoặc một instruction tương đương rõ ràng yêu cầu final delivery của module, instruction đó authorize toàn bộ Step 10 contract:

```text
commit
→ push
→ create PR/MR
→ merge khi hosting/checks cho phép
→ verify
→ cleanup
→ sync local main
```

Không cần dừng lại xin approve merge thêm một lần nữa sau khi user đã yêu cầu Step 10.

Ngược lại, việc agent chỉ đọc file Step 10, tự động đi tới cuối canonical sequence, hoặc thấy module đã pass Step 9 **không tự tạo merge authorization**. Nếu chưa có instruction thực sự yêu cầu Step 10/final delivery, không merge.

Chỉ merge khi Git hosting cho phép và các required checks / branch protection / conflict conditions đã pass.

Nếu required checks chỉ đang **pending/running**, dùng CLI để inspect/watch chúng cho tới khi có terminal result khi việc chờ là hợp lý. Pending không tự động được coi là failure. Nếu checks fail, branch protection từ chối merge, có conflict, hoặc CLI không thể tiếp tục theo dõi/thực hiện an toàn, Step 10 chưa hoàn tất; report exact state/blocker và giữ module worktree/branch để có thể sửa hoặc resume tiếp.

## Merge + local cleanup

Sau khi PR/MR được merge thành công, Step 10 phải verify remote result rồi cleanup local workspace của module ngay trong cùng workflow.

```text
final local module HEAD
→ push module branch
→ verify remote source branch tip == final local HEAD
→ create Pull Request / Merge Request
→ accept / merge into main
→ verify PR/MR status == merged
→ verify remote main contains the merged module change
→ switch to main worktree / safe cwd
→ remove module worktree
→ safely delete local module branch
→ safely delete remote source branch if it still exists
→ git worktree prune
→ git fetch --prune
→ pull / fast-forward local main from origin/main
→ verify local main == origin/main
```

Không thay đổi repository-level hosting settings chỉ để bật auto-delete source branch. Nếu hosting đã tự xóa remote source branch sau merge thì chỉ cần prune refs.

Nếu remote source branch vẫn còn, trước khi delete thủ công bằng CLI phải fetch/re-check rằng remote branch tip vẫn đúng **recorded final source commit / merged PR-MR head**. Nếu branch tip đã di chuyển sau merge, **không delete branch đó**; preserve và report vì có commit mới không thuộc merged result.

Thao tác remove module worktree phải chạy từ main worktree hoặc một working directory khác, không chạy từ chính worktree đang bị remove. Vì vậy sau merge + remote verification, session phải switch khỏi module worktree trước khi cleanup.

Trước khi cleanup phải verify:

```text
git status
→ module worktree clean

local HEAD
→ final Step 10 commit

Pull Request / Merge Request
→ merged successfully

remote main
→ contains the merged module change
```

Sau khi verify thành công và đã switch ra khỏi module worktree:

```text
git worktree remove <module-worktree-path>
→ git branch -d module/<module-name>
→ nếu -d fail chỉ vì local main chưa fast-forward,
  và merge + remote-main verification đã pass:
  git branch -D module/<module-name>
→ nếu remote source branch còn tồn tại:
  verify remote tip == recorded final source commit
  rồi mới git push origin --delete module/<module-name>
→ git worktree prune
→ git fetch --prune
→ git checkout main / bảo đảm đang ở original main worktree
→ git pull --ff-only origin main
→ verify local main HEAD == origin/main
```

`git branch -D` chỉ là fallback exception có chủ đích. Luôn thử `git branch -d` trước. Không dùng `-D` nếu merge/remote-main verification chưa pass hoặc nếu local branch có commit chưa được xác nhận là nằm trong merged result.

Final local-`main` sync là **bước bắt buộc của Step 10**. Chỉ dùng fast-forward pull:

```text
git pull --ff-only origin main
```

Không reset, discard, overwrite hoặc auto-stash unrelated changes trong main worktree để ép pull thành công. Nếu local main có working-tree changes hoặc state khác làm `--ff-only` không thể hoàn tất an toàn, dừng tại đó và report:

```text
remote merge/cleanup
→ already complete

local main sync
→ blocked
→ exact blocker + current local main HEAD + origin/main HEAD
```

## Sau khi Pull Request / Merge Request được merge

Khi Step 10 đã được user authorize, Pull Request / Merge Request phải được merge **trong chính Step 10** nếu hosting/checks cho phép; sau đó cleanup và local-main sync cũng phải được thực hiện trước khi Step 10 được coi là hoàn tất.

Remote source branch:

```text
PR/MR merged
→ verify remote main
→ delete source branch only when tip-safety guard passes
→ cleanup local module workspace
→ pull local main from origin/main
→ verify local main == origin/main
```

Kết thúc Step 10 thành công khi không còn disposable local module worktree/local branch, remote source branch đã được cleanup an toàn khi applicable, và local `main` đã fast-forward tới current `origin/main`.
