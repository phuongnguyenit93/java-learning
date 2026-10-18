# Xử lý Exception với try/catch/finally

Sự lan truyền cho phép lỗi đi lên ngăn xếp lời gọi. `try/catch/finally` cho phép một tầng quyết định ba việc khác nhau:

```text
try
→ vùng mã nguồn có thể phát sinh lỗi

catch
→ xử lý một nhóm lỗi cụ thể

finally
→ thực hiện công việc bắt buộc khi rời vùng try/catch
```

Điểm khó không nằm ở cú pháp, mà ở **thứ tự luồng điều khiển** khi chạy thành công, phát sinh exception, `return` và dọn dẹp tương tác với nhau.

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

Nếu exception không khớp `catch`, nó tiếp tục lan truyền lên trên.

### Bắt Exception không đồng nghĩa với phục hồi

Đây là một nhầm lẫn phổ biến:

```java
catch (Exception ex) {
    System.out.println("error");
}
```

Việc “đã bắt được exception” chỉ có nghĩa quá trình lan truyền bị chặn ở đó. Nó **không tự động làm hệ thống trở lại trạng thái hợp lệ**.

Khối `catch` nên có một trách nhiệm có ý nghĩa, ví dụ:

- phục hồi bằng phương án thay thế;
- chuyển đổi exception sang mức trừu tượng phù hợp;
- bổ sung ngữ cảnh rồi ném lại;
- kết thúc thao tác có chủ ý;
- tại ranh giới cuối, ghi nhận/ghi log lỗi.

Nếu không có việc hợp lý để làm, để exception tiếp tục lan truyền thường tốt hơn bắt rồi nuốt lỗi.

### Khối `catch` càng rộng càng cần lý do rõ

`catch (Exception ex)` không luôn sai. Nó có thể hợp lý ở một ranh giới của ứng dụng cần chuyển mọi lỗi ứng dụng thành một kết quả thống nhất.

Nhưng ở tầng giữa, catch quá rộng dễ:

- bắt cả lỗi mà tầng này không hiểu;
- vô tình che bug;
- làm mất chính sách xử lý riêng theo kiểu exception;
- khiến ý nghĩa thành công/thất bại trở nên mơ hồ.

## <a id="multi-catch">Multi-catch</a>

Nếu nhiều exception cần **cùng một chính sách xử lý**, Java cho phép:

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

### Các kiểu trong multi-catch không được có quan hệ subtype với nhau

Không hợp lệ:

```java
catch (FileNotFoundException | IOException ex) {
    ...
}
```

vì `FileNotFoundException` đã là subtype của `IOException`. Quy tắc này áp dụng cho **bất kỳ quan hệ cha-con nào** giữa các alternative, không chỉ trường hợp kế thừa trực tiếp.

### Đừng gom chỉ để giảm số dòng

Nếu hai lỗi có chính sách xử lý khác:

```text
FileNotFoundException
→ yêu cầu người dùng chọn file khác

SQLException
→ báo hệ thống lưu trữ không khả dụng
```

thì việc nhét cả hai vào một multi-catch chỉ vì phần xử lý hiện tại giống nhau có thể làm mất ý nghĩa.

Sau khi hiểu cách một hoặc nhiều loại exception được bắt, phần tiếp theo đi vào `finally`: công việc nào vẫn phải diễn ra khi luồng điều khiển rời `try/catch`?

## <a id="finally-semantics">finally</a>

`finally` được thiết kế cho công việc phải xảy ra khi luồng điều khiển rời `try/catch`.

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
- exception tiếp tục lan truyền;
- `return`;
- `break`/`continue` rời vùng liên quan.

Mô hình cần nhớ:

```text
try/catch quyết định kết quả xử lý
        ↓
trước khi luồng điều khiển thực sự rời cấu trúc
        ↓
finally chạy
```

### `finally` không phải bảo đảm tuyệt đối của thế giới bên ngoài

Không nên hiểu “finally luôn chạy” theo nghĩa vật lý tuyệt đối. Nếu tiến trình/JVM bị kết thúc cưỡng bức, gặp sự cố hoặc môi trường mất nguồn, mã nguồn dọn dẹp không thể được đảm bảo.

Trong luồng điều khiển bình thường của JVM, `finally` là cơ chế ngôn ngữ để thực hiện dọn dẹp khi rời khối lệnh.

### Với tài nguyên, ưu tiên try-with-resources

Dùng `finally` thủ công từng là cách phổ biến:

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

Nhưng đoạn mã này có nhiều trường hợp biên khi phần thân và `close()` cùng thất bại. Với `AutoCloseable`, `try-with-resources` thường biểu diễn quyền sở hữu tài nguyên an toàn và rõ hơn.

## <a id="return-finally">return/throw bên trong finally</a>

Đây là nơi `finally` có thể trở nên nguy hiểm.

### Biểu thức `return` được tính trước, nhưng finally chạy trước khi phương thức thoát

```java
int value() {
    try {
        return 1;
    } finally {
        System.out.println("cleanup");
    }
}
```

Kết quả vẫn là `1`, nhưng phần dọn dẹp chạy trước khi bên gọi nhận giá trị.

### `return` trong finally ghi đè giá trị trả về đang chờ

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

Luồng:

```text
try chuẩn bị return 1
        ↓
finally chạy
        ↓
finally tạo return mới = 2
        ↓
return 1 bị thay thế
```

`return` trong `finally` còn nguy hiểm hơn khi trước đó đang có một exception chờ được ném:

```java
int dangerous() throws IOException {
    try {
        throw new IOException("original");
    } finally {
        return 0;
    }
}
```

Kết quả là phương thức trả về `0`; `IOException` đang chờ bị thay thế bởi lý do hoàn thành mới từ `finally`.

### `throw` trong finally có thể che exception gốc

```java
try {
    throw new IllegalStateException("original");
} finally {
    throw new RuntimeException("cleanup failed");
}
```

Exception `cleanup failed` thoát ra, còn `original` có thể bị che mất nếu ta quản lý việc dọn dẹp thủ công.

Quy tắc tổng quát là: nếu `finally` tự **hoàn thành bất thường** bằng `return`, `throw`, hoặc một `break`/`continue` hợp lệ trong ngữ cảnh của nó, lý do hoàn thành mới đó thay thế kết quả hoặc exception đang chờ từ `try`/`catch`.

Đây chính là một trong các vấn đề mà `try-with-resources` giải quyết tốt hơn bằng **ngoại lệ được giữ lại (suppressed exception)**.

Quy tắc thực tế:

```text
finally
→ dọn dẹp

tránh
→ return trong finally
→ ném lỗi mới từ `finally` dù có cách quản lý an toàn hơn
```

### GHI NHỚ

```text
try
→ vùng mã nguồn có thể kết thúc bất thường

catch
→ nơi có chính sách xử lý cụ thể

multi-catch
→ nhiều kiểu, một chính sách xử lý thật sự chung

finally
→ dọn dẹp khi rời cấu trúc
```

Chương tiếp theo thay việc dọn dẹp tài nguyên thủ công bằng `try-with-resources`, nơi vòng đời và quyền sở hữu tài nguyên được đưa trực tiếp vào cú pháp.
