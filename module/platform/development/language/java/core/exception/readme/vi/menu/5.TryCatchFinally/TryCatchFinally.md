# try, catch và finally

Propagation cho phép failure đi lên call stack. `try/catch/finally` cho phép một tầng quyết định ba việc khác nhau:

```text
try
→ đường chạy có thể fail

catch
→ xử lý một nhóm failure cụ thể

finally
→ thực hiện công việc bắt buộc khi rời vùng try/catch
```

Điểm khó không nằm ở cú pháp, mà ở **thứ tự control flow** khi success, exception, `return` và cleanup tương tác với nhau.

## <a id="try-catch-flow">Luồng try/catch</a>

Ví dụ:

```java
try {
    load();
    process();
} catch (IOException ex) {
    recover(ex);
}
```

Nếu `load()` thành công:

```text
load
→ process
→ bỏ qua catch
→ tiếp tục sau try/catch
```

Nếu `load()` ném `IOException`:

```text
load
→ throw
→ process không chạy
→ tìm catch tương thích
→ recover
→ tiếp tục sau try/catch nếu catch hoàn thành bình thường
```

Nếu exception không khớp `catch`, nó tiếp tục propagate.

### Catch không đồng nghĩa với recovery

Đây là một nhầm lẫn phổ biến:

```java
catch (Exception ex) {
    System.out.println("error");
}
```

Việc “đã catch” chỉ có nghĩa propagation bị chặn ở đó. Nó **không tự động làm hệ thống trở lại trạng thái hợp lệ**.

Catch nên có một trách nhiệm có ý nghĩa, ví dụ:

- recover bằng phương án thay thế;
- translate sang abstraction phù hợp;
- bổ sung context rồi rethrow;
- kết thúc operation có chủ ý;
- tại boundary cuối, ghi nhận/log failure.

Nếu không có việc hợp lý để làm, để exception propagate thường tốt hơn catch rồi nuốt.

### Catch càng rộng càng cần lý do rõ

`catch (Exception ex)` không luôn sai. Nó có thể hợp lý ở một application boundary cần chuyển mọi application failure thành một kết quả thống nhất.

Nhưng ở tầng giữa, catch quá rộng dễ:

- bắt cả failure mà tầng này không hiểu;
- vô tình che bug;
- làm mất type-specific policy;
- khiến success/failure semantics mơ hồ.

## <a id="finally-semantics">finally</a>

`finally` được thiết kế cho công việc phải xảy ra khi control flow rời `try/catch`.

```java
try {
    useResource();
} finally {
    cleanup();
}
```

Nó thường chạy khi đường thoát là:

- chạy bình thường;
- exception được catch;
- exception tiếp tục propagate;
- `return`;
- `break`/`continue` rời vùng liên quan.

Mental model:

```text
try/catch quyết định outcome
        ↓
trước khi control flow thực sự rời cấu trúc
        ↓
finally chạy
```

### `finally` không phải bảo đảm tuyệt đối của thế giới bên ngoài

Không nên hiểu “finally luôn chạy” theo nghĩa vật lý tuyệt đối. Nếu process/JVM bị kết thúc cưỡng bức, crash hoặc môi trường mất nguồn, code cleanup không thể được đảm bảo.

Trong normal JVM control flow, `finally` là cơ chế ngôn ngữ để thực hiện cleanup khi rời block.

### Với resource, ưu tiên try-with-resources

Manual `finally` từng là cách phổ biến:

```java
InputStream in = null;
try {
    in = Files.newInputStream(path);
    ...
} finally {
    if (in != null) {
        in.close();
    }
}
```

Nhưng code này có nhiều edge case khi body và `close()` cùng fail. Với `AutoCloseable`, `try-with-resources` thường biểu diễn ownership an toàn và rõ hơn.

## <a id="return-finally">return/throw bên trong finally</a>

Đây là nơi `finally` có thể trở nên nguy hiểm.

### Return expression được tính trước, nhưng finally chạy trước khi method thoát

```java
int value() {
    try {
        return 1;
    } finally {
        System.out.println("cleanup");
    }
}
```

Kết quả vẫn là `1`, nhưng `cleanup` chạy trước khi caller nhận giá trị.

### Return trong finally ghi đè return đang chờ

```java
int dangerous() {
    try {
        return 1;
    } finally {
        return 2;
    }
}
```

Kết quả là `2`.

Flow:

```text
try chuẩn bị return 1
        ↓
finally chạy
        ↓
finally tạo return mới = 2
        ↓
return 1 bị thay thế
```

### Throw trong finally có thể che exception gốc

```java
try {
    throw new IllegalStateException("original");
} finally {
    throw new RuntimeException("cleanup failed");
}
```

Exception `cleanup failed` thoát ra, còn `original` có thể bị che mất nếu ta quản lý cleanup thủ công.

Đây chính là một trong các vấn đề mà `try-with-resources` giải quyết tốt hơn bằng **suppressed exceptions**.

Quy tắc thực tế:

```text
finally
→ cleanup

tránh
→ return trong finally
→ throw failure mới từ finally nếu có cách quản lý an toàn hơn
```

## <a id="multi-catch">Multi-catch</a>

Nếu nhiều exception cần **cùng một policy xử lý**, Java cho phép:

```java
try {
    importData();
} catch (IOException | SQLException ex) {
    auditAndAbort(ex);
}
```

### VÌ SAO?

Không có multi-catch, ta dễ lặp:

```java
catch (IOException ex) {
    auditAndAbort(ex);
} catch (SQLException ex) {
    auditAndAbort(ex);
}
```

### Các alternative không được có quan hệ subtype trực tiếp

Không hợp lệ:

```java
catch (FileNotFoundException | IOException ex) {
    ...
}
```

vì `FileNotFoundException` đã là subtype của `IOException`.

### Đừng gom chỉ để giảm số dòng

Nếu hai failure có policy khác:

```text
FileNotFoundException
→ yêu cầu user chọn file khác

SQLException
→ báo storage unavailable
```

thì việc nhét cả hai vào một multi-catch chỉ vì body handler đang giống nhau có thể làm mất meaning.

### GHI NHỚ

```text
try
→ vùng code có thể abrupt completion

catch
→ nơi có policy xử lý cụ thể

finally
→ cleanup khi rời cấu trúc

multi-catch
→ nhiều type, một handling policy thật sự chung
```

Chương tiếp theo thay manual cleanup bằng `try-with-resources`, nơi lifecycle của resource được đưa trực tiếp vào cú pháp.