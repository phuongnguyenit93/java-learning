<a id="back-to-top"></a>

# Kế thừa và hợp đồng

## Menu
- [Checked exception trong phương thức ghi đè](#exception-inheritance-001)
- [Không mở rộng tập checked exception được phép ném](#exception-inheritance-002)
- [Thu hẹp hoặc bỏ khai báo checked exception](#exception-inheritance-003)
- [Unchecked exception khi ghi đè](#exception-inheritance-004)
- [Một phương thức thực thi nhiều interface có throws khác nhau](#exception-inheritance-005)
- [Overload, kiểu khai báo của tham chiếu và tập ngoại lệ cần xử lý](#exception-inheritance-006)
- [Checked exception của constructor lớp cha](#exception-inheritance-007)
- [Checked exception trong instance initializer và các constructor](#exception-inheritance-008)
- [Static initializer, checked exception và lỗi khởi tạo lớp](#exception-inheritance-009)

## <a id="exception-inheritance-001">Checked exception trong phương thức ghi đè</a>

<details>
<summary>Xem chi tiết</summary>

# Checked exception trong phương thức ghi đè (`override`) của Java

## 1. Mục tiêu học tập

Khi một lớp con **ghi đè phương thức** của lớp cha hoặc một lớp **triển khai phương thức interface**, Java không chỉ kiểm tra tên, tham số, quyền truy cập và kiểu trả về: nó còn kiểm tra **hợp đồng checked exception**. Phương thức ghi đè **không được tự ý khai báo một checked exception mới hoặc rộng hơn** những ngoại lệ mà khai báo được ghi đè cho phép.

Ví dụ, nếu lớp cha khai báo `read() throws IOException` thì lớp con có thể giữ `IOException`, thu hẹp thành `FileNotFoundException`, hoặc bỏ checked exception. Lớp con **không** được đổi thành `throws Exception` hay `throws SQLException`, vì bên gọi đang dựa trên hợp đồng cũ và không phải chuẩn bị cho các checked exception ngoài hợp đồng đó.

Sau bài này bạn sẽ có thể:

- Hiểu quy tắc `throws` khi `@Override` đối với class, abstract class và interface.
- Phân biệt ba lựa chọn hợp lệ: **giữ**, **thu hẹp** hoặc **bỏ** checked exception.
- Nhận ra trường hợp **mở rộng** hoặc **thêm checked exception khác nhánh** gây lỗi biên dịch.
- Biết vì sao checked exception vẫn phải được xử lý nếu lời gọi có **kiểu tĩnh là lớp cha/interface**, dù implementation không ném lỗi ấy.
- Phân biệt kiểm tra checked exception với `RuntimeException` và `Error` (unchecked).
- Hiểu các ràng buộc khi một lớp triển khai **hai interface** có phương thức cùng chữ ký nhưng khác `throws`.
- Xem đúng vai trò của `throws`, `throw`, `try/catch`, `instanceof`, và kiểu tham số tổng quát trong hợp đồng exception.
- Thiết kế API bảo toàn khả năng thay thế đối tượng (*substitutability*), không làm caller phải thay đổi ngoài ý muốn.

Ví dụ tương thích Java 17+, các chương trình hoàn chỉnh có tên tệp, lệnh `javac`/`java` và kết quả `stdout` ổn định.

## 2. Quy tắc cốt lõi: phương thức ghi đè không mở rộng checked exception

Xem hợp đồng của lớp cha:

```java
class BaseReader {
    String read() throws java.io.IOException {
        return "data";
    }
}
```

Một phương thức override ở lớp con phải tương thích với hợp đồng ấy. Về checked exception, các khai báo dưới đây là **hợp lệ khi xét riêng điều kiện `throws`**:

```java
class SameReader extends BaseReader {
    @Override
    String read() throws java.io.IOException {
        return "same";
    }
}

class SpecificReader extends BaseReader {
    @Override
    String read() throws java.io.FileNotFoundException {
        return "specific";
    }
}

class CachedReader extends BaseReader {
    @Override
    String read() {
        return "cached";
    }
}
```

Ba class trên là **mã khai báo minh họa**, chưa có `main`. Điều kiện hợp lệ:

- `IOException` là chính kiểu checked exception mà `BaseReader.read()` cho phép.
- `FileNotFoundException` là **lớp con của `IOException`**, nên thu hẹp hợp đồng.
- Không khai báo checked exception là hợp lệ nếu thân phương thức không truyền checked exception trái phép ra ngoài.

Ngược lại, hai khai báo sau là **không hợp lệ** nếu dùng để override `BaseReader.read()`:

```java
class TooBroadReader extends BaseReader {
    @Override
    String read() throws Exception {
        return "invalid"; // LỖI BIÊN DỊCH: Exception rộng hơn IOException.
    }
}

class WrongBranchReader extends BaseReader {
    @Override
    String read() throws java.sql.SQLException {
        return "invalid"; // LỖI BIÊN DỊCH: SQLException không thuộc IOException.
    }
}
```

Hai class này chỉ dùng để giải thích lỗi; **không đưa vào cùng dự án đang muốn build thành công**. Cả `Exception` và `SQLException` đều là checked exception, nhưng không được hợp đồng của `BaseReader.read()` bao phủ.

### Câu hỏi cần tự đặt

Nếu chương trình bên gọi chỉ biết một đối tượng dưới kiểu `BaseReader`, trình biên dịch đã buộc nó xử lý hoặc khai báo `IOException`. Nếu lớp con bất ngờ được phép ném `SQLException` hoặc `Exception` rộng hơn, caller có thể gặp một checked exception mà chữ ký lớp cha không báo trước. Java ngăn tình huống này ở **compile time**.

Quy tắc có thể hình dung:

```text
BaseReader.read() throws IOException
                  |
                  v
        override được phép:
          IOException
          FileNotFoundException
          không checked exception
                  |
                  x
        override không được:
          Exception
          SQLException
          ParseException
```

Đây là sơ đồ minh họa những kiểu ngoại lệ tiêu biểu. Quy tắc chính xác phải xét **quan hệ kiểu kế thừa**, không đơn thuần dựa vào tên ngoại lệ.

## 3. Checked và unchecked exception khác nhau thế nào?

Java phân loại:

```text
Throwable
├── Error                    (unchecked)
└── Exception
    ├── IOException          (checked)
    │   └── FileNotFoundException (checked)
    ├── SQLException         (checked)
    ├── ParseException       (checked)
    └── RuntimeException    (unchecked)
        ├── IllegalArgumentException
        └── IllegalStateException
```

Đây là sơ đồ **rút gọn**. Checked exception là các kiểu `Throwable` không thuộc `RuntimeException` hoặc `Error` (và các lớp con của chúng).

Với **checked exception**, phương thức override không được thêm kiểu nằm ngoài phạm vi cho phép của khai báo bị ghi đè. Với **unchecked exception**, compiler không buộc override phải giới hạn tương tự. Điều này có nghĩa:

```java
class BaseService {
    void execute() {
        // Không khai báo checked exception.
    }
}

class StrictService extends BaseService {
    @Override
    void execute() throws IllegalStateException {
        throw new IllegalStateException("invalid state");
    }
}
```

Đây là các class minh họa hợp lệ. `IllegalStateException` kế thừa `RuntimeException` nên dù phương thức cha không khai báo nó, lớp con vẫn được phép `throw` và tùy chọn công bố trong `throws`.

Tuy nhiên, **hợp lệ về cú pháp không có nghĩa thiết kế API đã tốt**: thêm những unchecked exception bất ngờ vẫn có thể gây lỗi runtime và vi phạm kỳ vọng nghiệp vụ. Quy tắc chỉ nói rằng compiler không áp hạn chế checked exception tương ứng.

### Tại sao `Error` không bị kiểm tra giống checked exception?

Các loại `Error` (ví dụ `OutOfMemoryError`) thuộc nhánh unchecked và có thể phát sinh ngay cả khi không nằm trong `throws`. Đừng viết `catch (Throwable)` để “sửa” hợp đồng override hoặc cố coi mọi `Error` là lỗi có thể phục hồi. Nguyên tắc xử lý checked/unchecked và chính sách khôi phục là hai chủ đề khác nhau.

## 4. Ví dụ chạy được: lớp con bỏ checked exception

Tạo file **`OverrideNoCheckedDemo.java`**:

```java
import java.io.IOException;

class ParentReader {
    String read() throws IOException {
        return "parent";
    }
}

class MemoryReader extends ParentReader {
    @Override
    String read() {
        return "memory";
    }
}

public class OverrideNoCheckedDemo {
    public static void main(String[] args) throws IOException {
        ParentReader parentView = new MemoryReader();
        MemoryReader childView = new MemoryReader();

        System.out.println("parent type=" + parentView.read());
        System.out.println("child type=" + childView.read());
    }
}
```

Biên dịch/chạy:

```bash
javac OverrideNoCheckedDemo.java
java OverrideNoCheckedDemo
```

`stdout` chính xác:

```text
parent type=memory
child type=memory
```

Giải thích:

1. `ParentReader.read()` khai báo `throws IOException`.
2. `MemoryReader.read()` ghi đè hợp lệ vì **không khai báo checked exception**.
3. Tại runtime, cả `parentView.read()` và `childView.read()` đều chạy implementation của `MemoryReader` do dynamic dispatch.
4. Tuy nhiên, `parentView` có **kiểu tĩnh `ParentReader`**, nên trình biên dịch xem lời gọi đó có thể ném `IOException` theo hợp đồng lớp cha. `main` khai báo `throws IOException` để thỏa điều kiện.
5. `childView` có kiểu tĩnh `MemoryReader`; chỉ xét lời gọi này, compiler không yêu cầu bắt `IOException` vì phương thức trong kiểu được nhìn thấy không khai báo checked exception đó.

**Phân biệt quan trọng:** phương thức thực sự chạy được chọn theo **kiểu runtime**, còn nghĩa vụ checked exception tại chỗ gọi dựa trên **kiểu tĩnh và khai báo phương thức được compiler chọn**.

### Không được xóa nghĩa vụ chỉ vì biết đối tượng thật là lớp con

Giả sử đoạn mã:

```java
ParentReader reader = new MemoryReader();
System.out.println(reader.read());
```

Nếu đặt trong một phương thức **không có `throws IOException` và không có `try/catch`**, nó sẽ **không biên dịch**. Biết được implementation ở runtime không xóa hợp đồng `throws IOException` của kiểu `ParentReader` tại compile time.

Ngược lại:

```java
MemoryReader reader = new MemoryReader();
System.out.println(reader.read());
```

Đây là mảnh minh họa được phép dùng mà không cần checked handler cho `IOException` từ lời gọi `read()` đó, vì chữ ký của `MemoryReader.read()` không khai báo checked exception.

## 5. Ví dụ chạy được: thu hẹp từ `IOException` xuống `FileNotFoundException`

Tạo tệp **`OverrideNarrowThrowsDemo.java`**:

```java
import java.io.FileNotFoundException;
import java.io.IOException;

class FileProvider {
    void open() throws IOException {
        throw new IOException("base IO failure");
    }
}

class MissingFileProvider extends FileProvider {
    @Override
    void open() throws FileNotFoundException {
        throw new FileNotFoundException("missing.txt");
    }
}

public class OverrideNarrowThrowsDemo {
    public static void main(String[] args) {
        FileProvider parentView = new MissingFileProvider();
        MissingFileProvider childView = new MissingFileProvider();

        try {
            parentView.open();
        } catch (IOException failure) {
            System.out.println("parent catch="
                    + failure.getClass().getSimpleName());
        }

        try {
            childView.open();
        } catch (FileNotFoundException failure) {
            System.out.println("child catch="
                    + failure.getClass().getSimpleName());
        }
    }
}
```

```bash
javac OverrideNarrowThrowsDemo.java
java OverrideNarrowThrowsDemo
```

Kết quả:

```text
parent catch=FileNotFoundException
child catch=FileNotFoundException
```

- Khi nhìn qua `FileProvider`, bên gọi phải chuẩn bị cho `IOException`.
- Khi nhìn qua `MissingFileProvider`, bên gọi có thể bắt cụ thể `FileNotFoundException`.
- Trong cả hai lần chạy, implementation thực là `MissingFileProvider.open()` và ném cùng kiểu `FileNotFoundException`.
- `FileNotFoundException` có quan hệ kế thừa với `IOException`, nên hợp đồng lớp con không làm bên gọi của lớp cha bất ngờ với một checked exception ngoài phạm vi.

**Lưu ý:** `catch (IOException)` có thể bắt cả `FileNotFoundException`. Nhưng với `childView.open()`, `catch (FileNotFoundException)` cho phép xử lý chi tiết hơn dựa trên chữ ký đã thu hẹp.

## 6. Mở rộng checked exception: lỗi biên dịch dù thân không `throw`

Một quan niệm sai thường gặp: “Lớp con chỉ **khai báo** `throws Exception` nhưng thân không thực sự ném lỗi, vậy chắc hợp lệ.” Không đúng.

Ví dụ **cố ý không biên dịch**, nếu nằm trong cùng package/lớp phù hợp:

```java
import java.io.IOException;

class Parent {
    void run() throws IOException {
    }
}

class InvalidChild extends Parent {
    @Override
    void run() throws Exception {
        System.out.println("always succeeds");
    }
}
```

Compiler từ chối `InvalidChild.run()` vì `Exception` rộng hơn `IOException`, bất kể phần thân có ném hay không.

Lý do là **hợp đồng method declaration**, không phải thống kê một lần chạy. Khi thiết kế phương thức ghi đè, hãy đọc `throws` của các phương thức cha/interface liên quan thay vì chỉ nhìn vào những thao tác xảy ra trong implementation hiện tại.

### Cách sửa tùy mục đích

Nếu lớp con không ném checked exception, bỏ `throws Exception`:

```java
@Override
void run() {
    System.out.println("always succeeds");
}
```

Nếu thực sự cần ném `FileNotFoundException`:

```java
@Override
void run() throws java.io.FileNotFoundException {
    throw new java.io.FileNotFoundException("missing");
}
```

Cả hai đoạn chỉ là **mảnh phương thức minh họa** cho override của `Parent.run()`.

Nếu lỗi thực tế thuộc kiểu checked **khác nhánh** (chẳng hạn `SQLException`), bạn cần chọn một chiến lược hợp đồng hợp lệ:

- Xử lý lỗi SQL ngay tại implementation nếu có thể phục hồi.
- Chuyển đổi có ý nghĩa sang một **kiểu checked được hợp đồng lớp cha cho phép**, đồng thời giữ ngoại lệ SQL ban đầu làm `cause`.
- Thiết kế một API khác nếu bản chất thao tác đã thay đổi đến mức không còn thỏa hợp đồng ban đầu.
- Chỉ bọc thành unchecked exception khi phù hợp với semantics và chính sách ứng dụng; không chỉ để “lách” compile-time.

Không có cách viết `throws SQLException` trực tiếp trên override khi parent chỉ cho phép `IOException`.

## 7. Lớp cha không khai báo checked exception

Nếu lớp cha có:

```java
class AccountService {
    void update() {
        // ...
    }
}
```

Thì lớp con **không thể** ghi đè như sau:

```java
class CheckedAccountService extends AccountService {
    @Override
    void update() throws java.io.IOException {
        // Không hợp lệ: thêm checked IOException.
    }
}
```

Đây là ví dụ **cố ý không biên dịch**. Không có checked exception nào được khai báo trong `AccountService.update()`, nên override không được tự mở rộng thành `IOException`.

Nhưng lớp con có thể ném unchecked exception:

```java
class ValidatingAccountService extends AccountService {
    @Override
    void update() throws IllegalArgumentException {
        throw new IllegalArgumentException("account invalid");
    }
}
```

Đây là class minh họa hợp lệ. `IllegalArgumentException` thuộc `RuntimeException` nên không bị ràng buộc checked exception của override.

### Trường hợp cần gọi API có checked exception trong thân override

Giả sử bạn phải gọi một API I/O bên trong `update()`, trong khi phương thức cha không khai báo checked exception. Bạn không thể đẩy checked `IOException` ra ngoài trực tiếp bằng `throws`; cần **bắt và xử lý** hoặc **chuyển đổi** theo hợp đồng đã được chọn.

Đừng âm thầm ghi log rồi trả về thành công nếu thao tác cập nhật bắt buộc đã thất bại. Khi thật sự cần một khả năng thất bại mới, có thể phải thay đổi thiết kế interface ở tầng trừu tượng cao hơn thay vì chỉ sửa lớp con.

## 8. Override trong interface: cùng quy tắc

Interface có thể công bố checked exception:

```java
interface DataSource {
    String load() throws java.io.IOException;
}
```

Lớp triển khai có thể giữ, thu hẹp hoặc bỏ checked exception. Ví dụ **chạy được**:

Tạo file **`InterfaceOverrideDemo.java`**:

```java
import java.io.IOException;

interface DataSource {
    String load() throws IOException;
}

class InMemoryDataSource implements DataSource {
    @Override
    public String load() {
        return "cached data";
    }
}

public class InterfaceOverrideDemo {
    public static void main(String[] args) throws IOException {
        DataSource source = new InMemoryDataSource();
        InMemoryDataSource direct = new InMemoryDataSource();

        System.out.println("interface=" + source.load());
        System.out.println("concrete=" + direct.load());
    }
}
```

```bash
javac InterfaceOverrideDemo.java
java InterfaceOverrideDemo
```

`stdout`:

```text
interface=cached data
concrete=cached data
```

Hai điểm bổ sung:

- Phương thức triển khai interface `public String load()` phải giữ quyền truy cập `public` đúng quy tắc Java. Việc bỏ `throws IOException` vẫn hợp lệ.
- `source` có kiểu tĩnh `DataSource` nên `main` phải bắt/khai báo `IOException`. `direct` có kiểu tĩnh lớp cụ thể không khai báo checked exception.

### Abstract class cũng vậy

Một lớp abstract có thể khai báo:

```java
abstract class Task {
    abstract void execute() throws java.io.IOException;
}

class PureTask extends Task {
    @Override
    void execute() {
        System.out.println("no IO");
    }
}
```

Đây là các class minh họa hợp lệ. Không có thân cho phương thức abstract, nhưng chữ ký `throws IOException` vẫn là **hợp đồng**. Class con được thu hẹp hoặc bỏ checked exception, nhưng không mở rộng ra loại checked khác.

## 9. Ghi đè một phương thức khai báo **nhiều** checked exception

Khi lớp cha khai báo:

```java
void process()
        throws java.io.IOException, java.text.ParseException {
    // ...
}
```

Phương thức con có thể giữ cả hai, giữ một trong hai, dùng các subtype hợp lệ, hoặc bỏ checked exception. Điều kiện quan trọng: **mỗi checked exception mà phương thức con khai báo phải được bao phủ bởi ít nhất một kiểu thích hợp trong tập ngoại lệ của phương thức cha**.

Ví dụ quan hệ:

```text
Parent: throws IOException, ParseException
  |
  +-- Child A: throws IOException
  +-- Child B: throws FileNotFoundException
  +-- Child C: throws ParseException
  +-- Child D: throws FileNotFoundException, ParseException
  +-- Child E: không có checked exception
  |
  x-- Child F: throws SQLException
  x-- Child G: throws Exception
```

`FileNotFoundException` là con của `IOException`. `SQLException` không phải con của `IOException` hay `ParseException`. `Exception` rộng hơn hai nhánh kia, không hợp lệ nếu thêm thành kiểu checked cho override.

**Không có ràng buộc rằng lớp con phải giữ đủ số lượng loại lỗi**. Tập ngoại lệ được phép có thể nhỏ hơn nhiều khi implementation sử dụng cache, dữ liệu trong bộ nhớ hoặc đã xử lý lỗi ở bên trong.

### Ví dụ chạy được với hai kiểu checked

Tạo file **`MultipleCheckedOverrideDemo.java`**:

```java
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;

class Importer {
    void load(String source) throws IOException, ParseException {
        throw new ParseException("bad data", 0);
    }
}

class FileImporter extends Importer {
    @Override
    void load(String source) throws FileNotFoundException {
        if ("missing".equals(source)) {
            throw new FileNotFoundException(source);
        }
        System.out.println("loaded " + source);
    }
}

public class MultipleCheckedOverrideDemo {
    public static void main(String[] args) {
        Importer parentView = new FileImporter();
        FileImporter childView = new FileImporter();

        try {
            parentView.load("missing");
        } catch (IOException | ParseException failure) {
            System.out.println("parent="
                    + failure.getClass().getSimpleName());
        }

        try {
            childView.load("ok");
        } catch (FileNotFoundException failure) {
            System.out.println("child error");
        }
    }
}
```

```bash
javac MultipleCheckedOverrideDemo.java
java MultipleCheckedOverrideDemo
```

`stdout`:

```text
parent=FileNotFoundException
loaded ok
```

Phân tích:

- `Importer.load(...)` công bố hai kiểu checked `IOException` và `ParseException`.
- `FileImporter.load(...)` chỉ khai báo `FileNotFoundException`, là subtype hợp lệ của `IOException`, và không ném `ParseException`.
- `parentView` được nhìn qua kiểu `Importer` nên handler có thể phải bao phủ **cả hai kiểu**.
- `childView` được nhìn qua lớp cụ thể nên handler có thể tập trung vào `FileNotFoundException`.
- Phương thức trong lớp con thực sự chỉ ném lỗi file ở đường `missing` hoặc in thành công ở đường `ok`; nó không bắt buộc ném mọi kiểu của lớp cha.

Không dùng dấu `|` trong `throws`: khai báo nhiều kiểu sử dụng dấu phẩy. `|` được sử dụng trong `multi-catch` như `catch (IOException | ParseException e)`.

## 10. Một lớp triển khai **hai interface** có cùng phương thức nhưng khác `throws`

Trường hợp này thường gây bất ngờ. Giả sử:

```java
interface FileAction {
    void run() throws java.io.IOException;
}

interface SqlAction {
    void run() throws java.sql.SQLException;
}
```

Một lớp vừa implements `FileAction`, vừa implements `SqlAction` phải cung cấp một phương thức `run()` tương thích **đồng thời với cả hai hợp đồng**.

Nếu viết:

```java
class InvalidCombined implements FileAction, SqlAction {
    @Override
    public void run() throws java.io.IOException {
        // LỖI: SQLException contract của SqlAction không cho phép IOException.
    }
}
```

Đây là class **cố ý không biên dịch**: dù `IOException` được `FileAction` cho phép, nó không được `SqlAction` bao phủ. Tương tự, chỉ khai báo `SQLException` cũng không tương thích với `FileAction`.

Trong trường hợp hai nhánh `IOException` và `SQLException` **không có subtype checked exception chung**, implementation không thể khai báo checked exception thuộc một trong hai nhánh; nhưng vẫn có thể **không khai báo checked exception**.

### Ví dụ hoàn chỉnh: cùng implementation, ba kiểu tham chiếu

Tạo tệp **`MultipleInterfacesThrowsDemo.java`**:

```java
import java.io.IOException;
import java.sql.SQLException;

interface FileAction {
    void run() throws IOException;
}

interface SqlAction {
    void run() throws SQLException;
}

class CombinedAction implements FileAction, SqlAction {
    @Override
    public void run() {
        System.out.println("run");
    }
}

public class MultipleInterfacesThrowsDemo {
    public static void main(String[] args)
            throws IOException, SQLException {
        CombinedAction direct = new CombinedAction();
        FileAction asFile = direct;
        SqlAction asSql = direct;

        direct.run();
        asFile.run();
        asSql.run();
    }
}
```

```bash
javac MultipleInterfacesThrowsDemo.java
java MultipleInterfacesThrowsDemo
```

`stdout`:

```text
run
run
run
```

Chú ý:

- `CombinedAction.run()` không công bố checked exception; nó tương thích với **cả hai interface**.
- `direct.run()` nhìn qua lớp cụ thể nên không phát sinh nghĩa vụ checked `IOException`/`SQLException` từ chữ ký.
- `asFile.run()` nhìn qua `FileAction`, vẫn có thể ném `IOException` theo hợp đồng tĩnh.
- `asSql.run()` nhìn qua `SqlAction`, vẫn có thể ném `SQLException` theo hợp đồng tĩnh.
- Vì `main` gọi cả ba, chữ ký `main throws IOException, SQLException` là hợp lệ.

**Khái niệm then chốt:** khi một phương thức ghi đè/triển khai nhiều phương thức cha, ràng buộc checked exception cần tương thích với **từng khai báo bị ghi đè**, không lấy hợp của các danh sách `throws` rồi tự ý ném bất kỳ loại nào trong đó.

### Nếu hai interface khai báo cùng kiểu cha?

Ví dụ `A.run() throws IOException` và `B.run() throws Exception`: một implementation `run() throws IOException` có thể hợp lệ về exception, vì `IOException` nằm trong cả phạm vi `IOException` và `Exception`. Điều kiện là kiểu checked exception của override phải thỏa đồng thời những hợp đồng liên quan.

## 11. `RuntimeException` vẫn có thể xuất hiện dù interface không khai báo lỗi

Ví dụ interface `Runnable` có phương thức:

```java
public abstract void run();
```

Trong Java, `Runnable.run()` không khai báo checked exception. Vì vậy một implementation không được tự thêm `throws IOException`:

```java
// Không hợp lệ nếu dùng để triển khai Runnable.run():
@Override
public void run() throws java.io.IOException {
    // ...
}
```

Đây là **mảnh cố ý không biên dịch**. Trường hợp thường gặp là một worker cần gọi API I/O có checked exception. Người viết có thể **bắt và xử lý** I/O tại worker, hoặc **bọc thành unchecked exception** mà vẫn giữ cause theo hợp đồng ứng dụng.

### Ví dụ chạy được: bọc checked exception cho `Runnable`

Tạo file **`RunnableOverrideDemo.java`**:

```java
import java.io.IOException;

public class RunnableOverrideDemo {
    static class TaskFailure extends RuntimeException {
        TaskFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static void readData() throws IOException {
        throw new IOException("source unavailable");
    }

    static class ReaderTask implements Runnable {
        @Override
        public void run() {
            try {
                readData();
            } catch (IOException original) {
                throw new TaskFailure("task failed", original);
            }
        }
    }

    public static void main(String[] args) {
        Runnable task = new ReaderTask();

        try {
            task.run();
        } catch (TaskFailure failure) {
            System.out.println("outer=" + failure.getMessage());
            System.out.println("cause="
                    + failure.getCause().getClass().getSimpleName());
        }
    }
}
```

```bash
javac RunnableOverrideDemo.java
java RunnableOverrideDemo
```

`stdout`:

```text
outer=task failed
cause=IOException
```

`ReaderTask.run()` không khai báo checked `IOException`; nó bắt lỗi I/O và **bọc** bằng `TaskFailure` (unchecked) trong khi giữ đối tượng nguyên nhân làm cause.

**Giới hạn ví dụ:** chương trình gọi `task.run()` **trực tiếp trên luồng `main`** để có output xác định. Nếu dùng `new Thread(task).start()`, ngoại lệ không được bắt trong worker sẽ đi theo cơ chế lỗi không được bắt của thread đó, **không tự ném lại** vào luồng main. Với `ExecutorService.submit(...)`, lỗi task có thể nằm trong `Future` và được quan sát qua `get()`. Không nhầm chính sách exception của các luồng khác với quy tắc override `throws`.

**Không dùng unchecked wrapper chỉ để che lỗi:** đây là quyết định API. Nếu caller có thể phục hồi từ lỗi đọc, hãy cung cấp một interface chức năng/phương thức có hợp đồng checked exception phù hợp thay vì ép tất cả lỗi sang RuntimeException.

## 12. Phương thức override không đồng nghĩa phương thức overload

Overriding là cung cấp implementation mới cho một chữ ký kế thừa phù hợp. Overloading là có các phương thức **khác danh sách tham số**.

Hai phương thức sau không tạo overload chỉ vì `throws` khác nhau:

```java
void load() throws java.io.IOException { }
void load() throws java.sql.SQLException { }
```

Đây là **mã cố ý không biên dịch** nếu đặt cả hai trong cùng một class: danh sách `throws` **không** tạo chữ ký overload khác nhau.

Ngược lại:

```java
void load(String key) throws java.io.IOException { }
void load(int id) throws java.sql.SQLException { }
```

Đây là hai method minh họa có **tham số khác nhau**, nên có thể là overload. Chúng không ghi đè nhau chỉ vì cùng tên. Mỗi lời gọi overload có hợp đồng exception riêng dựa trên phương thức được compiler chọn.

### Không nhầm phương thức `static` với override

Phương thức static cùng chữ ký ở lớp con **ẩn** phương thức static của lớp cha (*method hiding*), không tham gia dynamic dispatch như override. JLS vẫn đặt ràng buộc phù hợp về checked exception đối với static method hiding.

Không thể dùng `@Override` cho method static. Khi phân tích ngoại lệ của lời gọi static, cần xét phương thức được compiler chọn từ kiểu chứa/chữ ký, không mô tả đó là “ghi đè runtime”.

### Constructor không được override

Constructor có thể khai báo `throws` của riêng nó, và lời gọi `super(...)` có thể phát sinh checked exception. Nhưng constructor **không được kế thừa hoặc ghi đè**, vì vậy không áp quy tắc `@Override` của phương thức lên constructor theo cách máy móc. Có các quy tắc khởi tạo/checked exception riêng cần kiểm tra nếu constructor lớp cha ném lỗi.

## 13. Generic và kiểu ngoại lệ trong `throws`

Java cho phép một số API generic khai báo exception type parameter. Khi triển khai/gắn một tham số kiểu cụ thể, hợp đồng throws cũng được chuyên biệt hóa.

Ví dụ:

```java
interface CheckedTask<E extends Exception> {
    void execute() throws E;
}
```

Một implementation với tham số `IOException` có thể thu hẹp hơn, chẳng hạn `FileNotFoundException`. Điều này giống nguyên tắc lớp cha thường: kiểu checked được ném phải nằm trong phạm vi mà contract generic sau khi thay thế tham số kiểu cho phép.

### Ví dụ chạy được: generic checked exception

Tạo file **`GenericThrowsOverrideDemo.java`**:

```java
import java.io.FileNotFoundException;
import java.io.IOException;

interface CheckedTask<E extends Exception> {
    void execute() throws E;
}

class FileCheckTask implements CheckedTask<IOException> {
    @Override
    public void execute() throws FileNotFoundException {
        throw new FileNotFoundException("missing resource");
    }
}

public class GenericThrowsOverrideDemo {
    public static void main(String[] args) {
        CheckedTask<IOException> task = new FileCheckTask();

        try {
            task.execute();
        } catch (IOException failure) {
            System.out.println(
                    failure.getClass().getSimpleName());
        }
    }
}
```

```bash
javac GenericThrowsOverrideDemo.java
java GenericThrowsOverrideDemo
```

`stdout`:

```text
FileNotFoundException
```

`CheckedTask<IOException>` được nhìn qua kiểu interface có `execute() throws IOException` sau chuyên biệt hóa type parameter. Implementation chỉ ném subtype `FileNotFoundException` nên hợp lệ. `catch (IOException)` tại caller đủ để xử lý ngoại lệ.

**Ghi chú nâng cao:** Khi dùng generic methods, type erasure, hoặc nhiều khai báo được kế thừa cùng chữ ký, JLS quy định chi tiết cách đánh giá tính tương thích checked exception (bao gồm các điều kiện với erasure). Trước khi thiết kế một API generic phức tạp, nên đọc quy tắc chính thức chứ không suy luận bằng so sánh văn bản đơn giản của các danh sách `throws`.

## 14. Các lỗi biên dịch hay gặp và cách sửa

| Khai báo lớp cha/interface | Override hoặc implementation | Hợp lệ? | Giải thích |
| --- | --- | --- | --- |
| `throws IOException` | `throws IOException` | Có | Giữ nguyên loại checked |
| `throws IOException` | `throws FileNotFoundException` | Có | Thu hẹp sang subtype |
| `throws IOException` | Không có `throws` | Có | Bỏ checked exception |
| `throws IOException` | `throws Exception` | **Không** | `Exception` rộng hơn `IOException` |
| `throws IOException` | `throws SQLException` | **Không** | Khác nhánh checked |
| `throws IOException` | `throws IOException, SQLException` | **Không** | Thêm `SQLException` ngoài phạm vi |
| Không có checked exception | `throws IOException` | **Không** | Thêm checked exception |
| Không có checked exception | `throws IllegalStateException` | Có | Unchecked |
| `throws Exception` | `throws IOException` | Có | Thu hẹp từ `Exception` |
| `throws FileNotFoundException` | `throws IOException` | **Không** | Nới rộng loại checked |
| `throws IOException, ParseException` | `throws FileNotFoundException` | Có | Được bao phủ bởi `IOException` |
| `throws IOException, ParseException` | `throws SQLException` | **Không** | Không nằm trong hai loại được phép |

Bảng xét **riêng quy tắc checked exception khi ghi đè**, giả định phần còn lại của khai báo (kiểu trả về, tham số, quyền truy cập, kế thừa) đã hợp lệ. Một override vẫn có thể không biên dịch vì lý do khác: quyền truy cập bị giảm, tên/tham số không khớp, kiểu trả về không tương thích, hoặc cố override phương thức `final`.

### Ba cách sửa phổ biến

**Cách 1 — Thu hẹp `throws` đúng kiểu subtype:** Nếu API trong lớp con thực sự ném `FileNotFoundException`, hãy khai báo loại này thay vì `Exception`.

**Cách 2 — Bắt và xử lý tại lớp con:** Nếu lớp con gọi API có checked exception ngoài hợp đồng, xử lý theo chính sách có khả năng phục hồi trước khi thoát phương thức.

**Cách 3 — Chuyển đổi lỗi hợp lý và giữ cause:** Khi phải chuyển tầng, sử dụng một loại lỗi được hợp đồng cho phép hoặc unchecked exception có ngữ nghĩa rõ ràng và giữ `cause` gốc. Không xóa chi tiết nguồn lỗi bằng cách chỉ in `getMessage()` rồi trả về thành công giả.

Không tùy tiện sửa lớp cha bằng cách thêm `throws Exception` nếu đây là API công khai đã có caller. Mở rộng hợp đồng của lớp cha có thể làm **mọi bên gọi** phải bắt hoặc khai báo lỗi rộng hơn, gây tác động tương thích nguồn (*source compatibility*).

## 15. Thiết kế API: đừng biến `throws` thành danh sách dự phòng

### 15.1. Khai báo kiểu cụ thể nhất có ích cho caller

Nếu một phương thức chỉ làm đọc tệp, `IOException` thường có nghĩa hơn `Exception`. Lớp con có thể thu hẹp thành `FileNotFoundException` nếu bản chất thao tác của nó cho phép. Với interface dùng nhiều implementation, nên chọn hợp đồng phù hợp với **mọi implementation hợp lệ** mà bên gọi cần hỗ trợ.

### 15.2. Đừng lạm dụng `throws Exception` để “dễ code”

`throws Exception` cho phép nhiều loại checked exception, nhưng khiến bên gọi khó xử lý có chọn lọc. Trong quá trình override, nó còn không hợp lệ nếu lớp cha chỉ công bố loại hẹp hơn. Việc khai báo rộng ngay từ interface ban đầu nên là một quyết định API có cân nhắc, không phải mặc định.

### 15.3. Không ném lỗi để thực hiện nhánh điều khiển thông thường

Nếu tình huống “không tìm thấy giá trị” là kết quả bình thường, có thể cân nhắc `Optional` hoặc kiểu kết quả chuyên biệt thay vì exception (tùy API). Nhưng nếu lỗi I/O thực sự ngăn thao tác hoàn thành, không nên giả vờ thành công chỉ để bỏ `throws` ở lớp con.

### 15.4. Bảo toàn nguyên nhân khi đổi loại lỗi

Ví dụ mảnh mã:

```java
try {
    readFromDatabase();
} catch (java.sql.SQLException original) {
    throw new IllegalStateException(
            "Cannot load current configuration", original);
}
```

`readFromDatabase()` ở đây là phương thức minh họa chưa khai báo. `IllegalStateException` là unchecked, nên không trái quy tắc compile-time checked exception của override. Nhưng ứng dụng vẫn phải có chính sách xử lý khi lỗi xảy ra; không xem wrapper như cách “xử lý xong” sự cố.

Khi wrapper là checked exception, nó vẫn phải thuộc tập kiểu mà phương thức cha/interface cho phép.

### 15.5. Bảo đảm thay thế được lớp con cho lớp cha

Một caller dùng `BaseReader` không nên phải được viết lại chỉ vì lúc chạy đối tượng là `SomeSubclass`. Hạn chế checked exception của `@Override` giúp các implementation không phá vỡ hợp đồng bắt/khai báo checked exception mà caller đã tin tưởng.

## 16. Câu hỏi tự kiểm tra

1. **Parent khai báo `throws IOException`, Child có thể khai báo `throws FileNotFoundException` không?** Có, vì là subtype.
2. **Parent khai báo `throws IOException`, Child khai báo `throws Exception` hợp lệ?** Không, vì rộng hơn.
3. **Parent không khai báo checked exception, Child thêm `throws IOException` hợp lệ?** Không.
4. **Parent không khai báo checked exception, Child ném `IllegalStateException` được không?** Có về quy tắc checked exception, vì unchecked.
5. **Child không còn khai báo `IOException`, caller tham chiếu kiểu Parent có phải bắt IOException không?** Có, nếu khai báo Parent tại vị trí gọi còn `throws IOException`.
6. **Hai interface cùng `run()`, một throws IOException, một throws SQLException: implementation có được throws IOException không?** Không, vì không tương thích với interface thứ hai.
7. **Implementation của `Runnable.run()` có thể khai báo `throws IOException` không?** Không.
8. **Nếu cần gọi API I/O trong `Runnable.run()`, làm gì?** Bắt và xử lý hoặc đổi thành unchecked wrapper có cause theo hợp đồng.
9. **`throw` và `throws` khác nhau thế nào?** `throw` thực sự ném đối tượng; `throws` khai báo kiểu lỗi được phép truyền ra.
10. **Đổi thứ tự các loại trong danh sách `throws` có sửa được lỗi override không?** Không. Quy tắc là quan hệ kiểu, không phải thứ tự.
11. **Có thể overload hai phương thức chỉ khác `throws` không?** Không.
12. **Constructor có chịu quy tắc `@Override` của method không?** Không, constructor không được override.
13. **Implementation chỉ dùng bộ nhớ, không thể phát sinh IOException: có bắt buộc giữ `throws IOException` của interface không?** Không; có thể bỏ.
14. **Phương thức con `throws IOException` khi parent `throws FileNotFoundException` có hợp lệ?** Không, vì mở rộng từ subtype lên supertype.
15. **Khi parent `throws IOException, ParseException`, child chỉ `throws FileNotFoundException` có hợp lệ không?** Có.
16. **Nếu child phải báo SQLException nhưng parent chỉ IOException, có được khai báo trực tiếp SQLException không?** Không; cần thiết kế xử lý hoặc chuyển đổi phù hợp.
17. **Runtime chọn override nào, compiler kiểm tra checked exception theo điều gì?** Runtime dispatch chọn implementation; compiler kiểm tra theo khai báo phương thức ứng với kiểu tĩnh tại lời gọi.
18. **`@Override` có ý nghĩa gì?** Giúp compiler xác nhận method thực sự override/implement đúng chữ ký; nó không nới lỏng hợp đồng throws.
19. **Nếu lời gọi qua kiểu interface ném một unchecked exception không được công bố, Java có cho chạy?** Có thể xảy ra ở runtime; kiểm tra checked exception không ngăn unchecked exceptions.
20. **Nên làm gì trước khi đổi `throws` của một API công khai?** Xem callers và implementations hiện có, yêu cầu tương thích nguồn và chính sách xử lý lỗi.

## 17. Tài liệu Java chính thức

- [Java Language Specification 17 — `8.4.6 Method Throws](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.6): mệnh đề throws, checked exception và exception type parameter.
- [Java Language Specification 17 — `8.4.8.3 Requirements in Overriding and Hiding](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.8.3): giới hạn checked exception khi override và hide method.
- [Java Language Specification 17 — `9.4.1 Interface Method Body](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.4.1): các quy tắc liên quan phương thức của interface.
- [Java Language Specification 17 — `11.2 Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2): nghĩa vụ bắt hoặc khai báo checked exception.
- [Java Language Specification 17 — `15.12 Method Invocation Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.12): kiểm tra lời gọi, kiểu biên dịch và phương thức được chọn.
- [Java SE 17 API — IOException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/IOException.html), [FileNotFoundException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/FileNotFoundException.html): quan hệ ngoại lệ cha–con.
- [Java SE 17 API — Runnable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Runnable.html): interface với `run()` không khai báo checked exception.
- [Java SE 17 API — RuntimeException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/RuntimeException.html): nhánh unchecked không chịu cùng hạn chế checked throws của override.

**Ghi nhớ:** Khi `@Override`, phương thức con được **giữ, thu hẹp hoặc bỏ** checked exception trong hợp đồng phương thức cha/interface, nhưng **không được thêm hoặc mở rộng** loại checked exception ngoài phạm vi ấy. `RuntimeException` và `Error` thuộc nhánh unchecked nên không chịu cùng kiểm tra biên dịch. Nghĩa vụ `try/catch` hoặc `throws` tại bên gọi phụ thuộc **kiểu tĩnh của tham chiếu**; implementation thực tế vẫn được chọn bằng dynamic dispatch.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-inheritance-002">Không mở rộng tập checked exception được phép ném</a>

<details>
<summary>Xem chi tiết</summary>

# Không mở rộng tập checked exception được phép ném khi kế thừa và override trong Java

Khi một lớp con **override** phương thức của lớp cha, Java không cho phép nó tự ý khai báo thêm **checked exception mới hoặc rộng hơn** so với hợp đồng `throws` của phương thức được override. Quy tắc này còn áp dụng khi một lớp triển khai phương thức của interface, và khi một phương thức phải thỏa đồng thời nhiều hợp đồng thừa kế.

Ví dụ lớp cha:

~~~java
class Parent {
    void read() throws java.io.IOException {
        // Thao tác có thể lỗi I/O.
    }
}
~~~

Lớp con **được phép** thu hẹp xuống `FileNotFoundException` (lớp con của `IOException`), hoặc bỏ hoàn toàn khai báo checked exception:

~~~java
class Child extends Parent {
    @Override
    void read() throws java.io.FileNotFoundException {
        // Hợp lệ: FileNotFoundException extends IOException.
    }
}
~~~

Nhưng lớp con **không được phép** đổi sang `SQLException` hoặc mở rộng thành `Exception`:

~~~java
// KHÔNG BIÊN DỊCH với @Override.
class InvalidChild extends Parent {
    @Override
    void read() throws java.sql.SQLException {
        // SQLException không phải lớp con của IOException.
    }
}
~~~

Điểm cốt lõi: **phương thức override không được mở rộng tập kiểu checked exception mà caller có thể phải xử lý so với hợp đồng đã công bố qua lớp cha/interface**. Quy tắc này là ràng buộc *compile time*, không phải cơ chế runtime cấm phát sinh mọi loại ngoại lệ; **unchecked exception** (`RuntimeException` và `Error` cùng các lớp con) không bị hạn chế tương tự.

Bài học tập trung vào điều kiện hợp lệ của mệnh đề `throws` khi override, interface, nhiều interface và lời gọi qua kiểu cha; cách xử lý khi cần phát sinh lỗi checked mới; phân biệt với overload, constructor, unchecked exception và lý do thiết kế của Java. Các chương trình hoàn chỉnh chạy với Java 17+.

## 1. Nhắc lại checked và unchecked exception

Phân cấp liên quan:

~~~text
java.lang.Throwable
├── java.lang.Error                         unchecked
│   └── ...
└── java.lang.Exception
    ├── java.io.IOException                 checked
    │   ├── java.io.FileNotFoundException   checked
    │   └── java.io.EOFException            checked
    ├── java.sql.SQLException               checked
    └── java.lang.RuntimeException          unchecked
        ├── IllegalArgumentException       unchecked
        ├── IllegalStateException          unchecked
        └── ...
~~~

**Checked exception** gồm những loại `Throwable` không thuộc nhánh `RuntimeException` hoặc `Error`. Ví dụ `IOException`, `SQLException`, `FileNotFoundException`, `InterruptedException` là checked; `IllegalStateException` và `NullPointerException` là unchecked.

Với checked exception, compiler áp dụng quy tắc **catch-or-declare**: một phương thức có đường thực thi có thể ném checked exception thoát ra phải xử lý thích hợp hoặc khai báo được phép trong `throws`.

~~~java
import java.io.IOException;

// Minh họa trong một lớp.
static void read() throws IOException {
    throw new IOException("không đọc được");
}
~~~

Nếu caller không bắt `IOException`, chính phương thức chứa lời gọi có thể cần `throws IOException`. Đó là nghĩa vụ phát sinh từ **loại checked exception và đường thực thi**.

Khi override, còn một ràng buộc thứ hai: **chữ ký override phải tương thích với `throws` của phương thức cha**. Vì vậy không phải lúc nào chỉ cần thêm `throws SomeCheckedException` là có thể biên dịch.

## 2. Vì sao Java không cho mở rộng checked exception ở phương thức con?

Xét mã sử dụng qua kiểu lớp cha:

~~~java
class ReaderService {
    String load() throws java.io.IOException {
        return "data";
    }
}

class FileReaderService extends ReaderService {
    @Override
    String load() throws java.io.FileNotFoundException {
        return "file-data";
    }
}
~~~

Caller có thể viết:

~~~java
ReaderService service = new FileReaderService();
try {
    System.out.println(service.load());
} catch (java.io.IOException ex) {
    System.err.println(ex.getMessage());
}
~~~

**Kiểu tĩnh** của biến `service` là `ReaderService`, nên compiler kiểm tra lời gọi theo hợp đồng `ReaderService.load() throws IOException`. Khi chạy, phương thức thực tế có thể là `FileReaderService.load()` do dispatch động.

Nếu lớp con có thể tự khai báo một `SQLException` checked hoàn toàn không nằm trong hợp đồng cha, caller hợp lệ vừa viết ở trên sẽ **không bị compiler yêu cầu xử lý** lỗi SQL. Điều này phá vỡ bảo đảm catch-or-declare mà API lớp cha đã cung cấp.

Do đó Java yêu cầu phương thức con không mở rộng các checked exception được phép thoát ra qua chữ ký override.

**Ý nghĩa thiết kế:** đối tượng lớp con phải dùng được ở những vị trí trông đợi lớp cha, ít nhất đối với phần cam kết compile-time về các loại checked exception. Đây là một phần của hợp đồng thay thế (substitutability), nhưng không phải bảo đảm rằng mọi hành vi khác của lớp con đều đúng tự động.

## 3. Quy tắc ngắn gọn cho một lớp cha và một lớp con

Giả sử phương thức cha:

~~~java
public void execute() throws IOException;
~~~

Trong ví dụ này, `IOException` là checked exception đã công bố. Với override tương ứng:

| Khai báo trong lớp con | Hợp lệ? | Lý do |
| --- | --- | --- |
| `void execute()` | Có | Không công bố checked exception nào. |
| `void execute() throws FileNotFoundException` | Có | `FileNotFoundException` là lớp con của `IOException`. |
| `void execute() throws EOFException` | Có | `EOFException` là lớp con của `IOException`. |
| `void execute() throws IOException` | Có | Cùng kiểu checked với lớp cha. |
| `void execute() throws FileNotFoundException, EOFException` | Có | Mỗi loại đều được bao phủ bởi `IOException`. |
| `void execute() throws SQLException` | Không | `SQLException` không thuộc nhánh `IOException`. |
| `void execute() throws Exception` | Không | `Exception` rộng hơn `IOException`. |
| `void execute() throws Throwable` | Không | `Throwable` checked theo phân loại này và rộng hơn. |
| `void execute() throws IOException, SQLException` | Không | Có thêm `SQLException` mới, không được lớp cha cho phép. |
| `void execute() throws IllegalStateException` | Có | `IllegalStateException` unchecked. |
| `void execute() throws IOException, IllegalStateException` | Có | Checked phần `IOException` hợp lệ; unchecked không bị giới hạn tương tự. |

Bảng chỉ đánh giá **ràng buộc checked exception khi override**; code đầy đủ còn phải thỏa các điều kiện khác như visibility, return type, static/instance và khả năng truy cập của các kiểu.

Đặc biệt, `throws FileNotFoundException, EOFException` không phải “mở rộng” chỉ vì số lượng kiểu trong danh sách nhiều hơn. Vấn đề là **mỗi kiểu checked được khai báo có nằm trong vùng kiểu đã được hợp đồng cha bao phủ hay không**.

## 4. Thu hẹp, giữ nguyên hoặc bỏ `throws` checked

Một phương thức override có ba chiến lược thường gặp:

### 4.1. Giữ nguyên kiểu checked được phép

~~~java
// Đoạn minh họa trong lớp con của lớp có method load() throws IOException.
@Override
String load() throws java.io.IOException {
    return java.nio.file.Files.readString(path);
}
~~~

Cách này hợp lệ nếu lớp cha có hợp đồng tương ứng, `path` là biến `Path` và code khác thỏa điều kiện biên dịch.

### 4.2. Thu hẹp xuống subtype cụ thể

~~~java
// Đoạn minh họa trong một lớp con.
@Override
String load() throws java.io.FileNotFoundException {
    if (missing) {
        throw new java.io.FileNotFoundException("không thấy tệp");
    }
    return "OK";
}
~~~

Ở đây `missing` là điều kiện giả định. Cách thu hẹp làm hợp đồng của phương thức lớp con cụ thể hơn. Tuy nhiên nếu phần triển khai gọi một API có thể ném `IOException` chung, bạn vẫn phải xử lý loại lỗi rộng đó trong body, không thể dùng `throws FileNotFoundException` để công bố mọi `IOException`.

### 4.3. Bỏ khai báo checked exception

~~~java
// Đoạn minh họa một implementation đọc từ bộ nhớ.
@Override
String load() {
    return cachedValue;
}
~~~

Cách này hợp lệ nếu code trong body không làm thoát checked exception chưa được xử lý. Thực tế một implementation cache có thể không cần I/O nên không cần công bố `IOException`.

**Quy tắc cốt lõi:** lớp con có thể **làm ít lời hứa lỗi checked hơn** so với lớp cha (khai báo ít kiểu hơn hoặc kiểu con cụ thể hơn), nhưng không tự ý thêm một nhóm checked exception mà caller của lớp cha không được thông báo.

## 5. Chương trình Java 17+ hoàn chỉnh: override hợp lệ

Lưu tệp `OverrideCheckedDemo.java`:

~~~java
import java.io.FileNotFoundException;
import java.io.IOException;

public class OverrideCheckedDemo {
    static class Loader {
        String load(String name) throws IOException {
            throw new IOException("Lỗi đọc " + name);
        }
    }

    static final class LocalLoader extends Loader {
        @Override
        String load(String name) throws FileNotFoundException {
            if (!"known".equals(name)) {
                throw new FileNotFoundException("Thiếu " + name);
            }
            return "OK";
        }
    }

    static final class CachedLoader extends Loader {
        @Override
        String load(String name) {
            return "cache:" + name;
        }
    }

    public static void main(String[] args) {
        Loader first = new LocalLoader();
        try {
            first.load("missing");
        } catch (IOException ex) {
            System.out.println("Lỗi qua kiểu cha: " + ex.getClass().getSimpleName());
        }

        Loader second = new CachedLoader();
        try {
            System.out.println("Dữ liệu: " + second.load("x"));
        } catch (IOException ex) {
            System.out.println("Không chạy");
        }

        CachedLoader concrete = new CachedLoader();
        System.out.println("Trực tiếp: " + concrete.load("y"));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 OverrideCheckedDemo.java
java OverrideCheckedDemo
~~~

Stdout chính xác:

~~~text
Lỗi qua kiểu cha: FileNotFoundException
Dữ liệu: cache:x
Trực tiếp: cache:y
~~~

### Vì sao `main` có hai kiểu lời gọi khác nhau?

- `LocalLoader.load()` khai báo `FileNotFoundException` (checked subtype), hợp lệ vì `Loader.load()` đã khai báo `IOException`.
- `CachedLoader.load()` không công bố checked exception nào; đây là một cách **thu hẹp về tập ngoại lệ được khai báo** hoàn toàn hợp lệ.
- `first` có kiểu tĩnh `Loader`, nên lời gọi `first.load(...)` được kiểm tra theo `throws IOException` của `Loader.load`; `catch (IOException)` xử lý được `FileNotFoundException`.
- `second` cũng có kiểu tĩnh `Loader`, dù đối tượng thực sự là `CachedLoader`; compiler vẫn yêu cầu xử lý `IOException` khi gọi `second.load("x")` qua kiểu cha.
- Biến `concrete` có kiểu tĩnh `CachedLoader`, nên lời gọi `concrete.load("y")` **không cần `try/catch IOException`**, vì khai báo của chính `CachedLoader.load` không có checked exception.
- Việc `catch` ở `second` không chạy là đặc tính thực thi của ví dụ, không phải lý do compiler bỏ quy tắc checked exception khi gọi qua kiểu cha.

Đây là nơi cần phân biệt **kiểu tham chiếu tại compile time** với **implementation được chọn tại runtime**. Dynamic dispatch quyết định thân phương thức chạy; quy tắc kiểm tra checked exception ở lời gọi dựa trên chữ ký được phép theo kiểu tĩnh và ngữ cảnh.

## 6. Ví dụ sai: đổi `IOException` thành `SQLException`

Lưu tệp sau thành `InvalidOverride.java`. Nó được viết **cố ý sai để quan sát lỗi biên dịch**:

~~~java
import java.io.IOException;
import java.sql.SQLException;

class InvalidOverride {
    static class Parent {
        void execute() throws IOException { }
    }

    static class Child extends Parent {
        @Override
        void execute() throws SQLException { }
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 InvalidOverride.java
~~~

`javac` của JDK 17 có thông báo cốt lõi:

~~~text
execute() in Child cannot override execute() in Parent
overridden method does not throw SQLException
~~~

Đường dẫn, số dòng, cột báo lỗi và dấu con trỏ trong thông báo có thể khác nhau. Lý do không thay đổi: `SQLException` là checked exception **không được `Parent.execute()` cho phép**, vì nó không phải lớp con của `IOException`.

**Không có cách sửa bằng việc thêm `@Override` hoặc thay thứ tự trong mệnh đề `throws`.** Annotation `@Override` giúp compiler xác minh ý định override; nó không thay đổi hợp đồng ngoại lệ.

Nếu sửa lớp con thành:

~~~java
// Minh họa thay phần Child ở trên.
static class Child extends Parent {
    @Override
    void execute() throws java.io.FileNotFoundException { }
}
~~~

thì riêng ràng buộc checked exception sẽ hợp lệ. Nếu code thật sự cần thao tác cơ sở dữ liệu và có thể thất bại bằng `SQLException`, cần chọn một trong các cách xử lý ở mục tiếp theo.

## 7. Nếu implementation thực sự gọi API có thể ném checked exception mới thì sao?

Giả sử lớp cha định nghĩa:

~~~java
interface Processor {
    void process() throws java.io.IOException;
}
~~~

Implementation muốn gọi một thư viện cơ sở dữ liệu có thể ném `SQLException`:

~~~java
// KHÔNG HỢP LỆ nếu phương thức lớp con trực tiếp khai báo SQLException.
@Override
public void process() throws java.sql.SQLException {
    databaseOperation();
}
~~~

Đây chỉ là minh họa: `databaseOperation()` cần được định nghĩa, và nếu implementation có `SQLException` thoát ra, compiler sẽ từ chối vì interface không công bố loại đó.

Có một số hướng thiết kế, tùy trách nhiệm của tầng:

### 7.1. Bắt lỗi SQL và xử lý thật sự trong implementation

~~~java
@Override
public void process() throws java.io.IOException {
    try {
        databaseOperation();
    } catch (java.sql.SQLException ex) {
        recoverDatabaseOperation(ex);
    }
}
~~~

Đoạn trên phụ thuộc `databaseOperation()` và `recoverDatabaseOperation(...)`. Chỉ hợp lệ nếu `recoverDatabaseOperation` giải quyết đúng vấn đề, không để checked exception không tương thích thoát ra. **Không được nuốt lỗi** rồi giả vờ hoàn thành thành công nếu dữ liệu hoặc trạng thái đã không còn đúng.

### 7.2. Chuyển lỗi thành loại đã được hợp đồng cho phép, giữ cause

~~~java
@Override
public void process() throws java.io.IOException {
    try {
        databaseOperation();
    } catch (java.sql.SQLException ex) {
        throw new java.io.IOException("Không thể xử lý dữ liệu", ex);
    }
}
~~~

Về mặt kiểu, `IOException` được interface cho phép, nên ràng buộc compile-time hợp lệ. Nhưng cách mapping chỉ nên áp dụng nếu `IOException` **thật sự có ý nghĩa trong hợp đồng**. Không nên bọc mọi lỗi SQL thành “lỗi I/O” chỉ để qua compiler khi abstraction của API không xem đó là tình huống hợp lý.

### 7.3. Chuyển sang custom unchecked exception ở đúng abstraction

~~~java
@Override
public void process() {
    try {
        databaseOperation();
    } catch (java.sql.SQLException ex) {
        throw new ProcessingUnavailableException(
                "Không thể thực hiện thao tác", ex);
    }
}
~~~

Ví dụ này cần định nghĩa `ProcessingUnavailableException extends RuntimeException` với constructor nhận `(String, Throwable)`. Vì unchecked, nó không vi phạm giới hạn `throws` của override.

**Nhưng unchecked không tự động là cách tốt nhất.** Chuyển checked sang unchecked làm caller không còn bị compiler bắt xử lý; nếu lỗi thực sự cần được khôi phục bắt buộc, cần xem lại chính hợp đồng interface.

### 7.4. Sửa hợp đồng cha nếu bạn kiểm soát API

Nếu hợp đồng `Processor` được thiết kế cho cả I/O và SQL, có thể sửa để công bố một checked exception chung có nghĩa phù hợp với abstraction, hoặc định nghĩa một exception nghiệp vụ dùng làm hợp đồng chung. Thay đổi như vậy có thể buộc caller cũ sửa `catch`/`throws`; cần đánh giá khả năng tương thích trước khi sửa public API.

Không nên chỉ mở rộng `throws Exception` để tiện mọi implementation. Cách đó làm hợp đồng mơ hồ và đẩy nhiều nghĩa vụ xử lý không cần thiết lên caller.

## 8. Checked exception và unchecked exception có quy tắc khác nhau

Giới hạn `throws` khi override ở bài này **chỉ nhắm vào checked exception**. Một phương thức con có thể khai báo loại unchecked mới:

~~~java
class Parent {
    void execute() throws java.io.IOException { }
}

class Child extends Parent {
    @Override
    void execute() throws IllegalStateException {
        throw new IllegalStateException("không hợp lệ ở trạng thái hiện tại");
    }
}
~~~

Đây là đoạn minh họa hợp lệ xét ràng buộc `throws`: lớp con không khai báo checked exception mới, và `IllegalStateException` là unchecked.

Thậm chí nếu method cha không có `throws`:

~~~java
interface Greeter {
    String greet(String name);
}

class StrictGreeter implements Greeter {
    @Override
    public String greet(String name) throws IllegalArgumentException {
        if (name == null) {
            throw new IllegalArgumentException("name không được null");
        }
        return "Xin chào " + name;
    }
}
~~~

Java vẫn cho phép checked/unchecked policy như vậy, vì `IllegalArgumentException` là unchecked. Tuy nhiên **ngôn ngữ cho phép không có nghĩa hợp đồng hành vi luôn tốt**: implementation không nên tự ý tăng tiền điều kiện gây bất ngờ cho caller của interface.

Các lớp con `Error` cũng thuộc unchecked theo ngôn ngữ, nhưng **không nên dùng `Error` để biểu diễn lỗi nghiệp vụ thông thường**. Đừng hiểu “không bị kiểm tra khi override” là lời khuyên khai báo `throws Error` để giải quyết mọi ràng buộc.

## 9. Interface cũng là hợp đồng checked exception

Một interface có thể công bố:

~~~java
import java.io.IOException;

interface DataReader {
    String read() throws IOException;
}
~~~

Implementation:

~~~java
class InMemoryReader implements DataReader {
    @Override
    public String read() {
        return "memory";
    }
}
~~~

Hoàn toàn hợp lệ: implementation không ném checked exception, dù interface cho phép `IOException`.

Implementation khác:

~~~java
class FileDataReader implements DataReader {
    @Override
    public String read() throws java.io.FileNotFoundException {
        throw new java.io.FileNotFoundException("không thấy dữ liệu");
    }
}
~~~

Cũng hợp lệ: `FileNotFoundException` nằm trong họ `IOException`. Ngược lại, implementation không thể trực tiếp khai báo `throws SQLException` ở cùng phương thức vì interface không cho phép.

**Một phương thức trong interface có thể không có thân**, nhưng `throws` của nó vẫn là ràng buộc thật đối với những lớp triển khai. Nếu một interface có `default` method, lớp triển khai override default method vẫn phải tuân thủ hợp đồng checked exception của phương thức được override.

Java không buộc implementation phải liệt kê **y hệt** danh sách `throws` trong interface. Số lượng hoặc thứ tự kiểu không quan trọng bằng **quan hệ kiểu subtype** của checked exception đã công bố.

## 10. Khi một phương thức phải triển khai hai interface có hợp đồng khác nhau

Trường hợp nâng cao nhưng rất thực tế:

~~~java
import java.io.IOException;
import java.sql.SQLException;

interface TextFromFile {
    String load() throws IOException;
}

interface TextFromDatabase {
    String load() throws SQLException;
}
~~~

Một lớp có thể `implements TextFromFile, TextFromDatabase` nếu cung cấp **một implementation chung** của `load()`. Nhưng implementation đó phải thỏa **cả hai hợp đồng**.

Chương trình hoàn chỉnh `TwoInterfacesDemo.java`:

~~~java
import java.io.IOException;
import java.sql.SQLException;

public class TwoInterfacesDemo {
    interface TextFromFile {
        String load() throws IOException;
    }

    interface TextFromDatabase {
        String load() throws SQLException;
    }

    static class Combined implements TextFromFile, TextFromDatabase {
        @Override
        public String load() {
            return "kết quả chung";
        }
    }

    public static void main(String[] args) {
        Combined x = new Combined();
        System.out.println("Gọi implementation: " + x.load());

        TextFromFile fileView = x;
        try {
            System.out.println("Gọi interface: " + fileView.load());
        } catch (IOException ex) {
            System.out.println("Không chạy");
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 TwoInterfacesDemo.java
java TwoInterfacesDemo
~~~

Stdout chính xác:

~~~text
Gọi implementation: kết quả chung
Gọi interface: kết quả chung
~~~

### Vì sao phải bỏ `throws` checked ở implementation này?

`IOException` không phải subtype của `SQLException`, và `SQLException` cũng không phải subtype của `IOException`. Vì một method `Combined.load()` phải thỏa cả hai interface, nó **không thể tự công bố `throws IOException` hoặc `throws SQLException`**. Bỏ khai báo checked exception đáp ứng cả hai hợp đồng.

Mô hình:

~~~text
TextFromFile.load()       throws IOException
          \                    /
           \                  /
            Combined.load()  không khai báo checked
           /                  \
          /                    \
TextFromDatabase.load()   throws SQLException
~~~

Đây là ví dụ minh họa hai nhánh checked không có subtype chung phù hợp. Một implementation nói chung **có thể công bố một kiểu checked cụ thể nếu nó thỏa đồng thời tất cả hợp đồng** (ví dụ một subtype đặc biệt nếu hệ thống có phân cấp kiểu phù hợp). Không nên hiểu rằng mọi method triển khai nhiều interface đều bị cấm khai báo mọi checked exception.

**Khi gọi qua `fileView`**, compiler vẫn dùng chữ ký `TextFromFile.load() throws IOException`, nên `main` phải xử lý checked exception phù hợp, dù implementation thực tế `Combined.load()` không ném loại ấy. Khi gọi trực tiếp qua biến `Combined`, không cần `catch`.

## 11. Ví dụ sai khi kết hợp hai interface

Tệp `InvalidIntersection.java` dưới đây **cố ý không biên dịch**:

~~~java
import java.io.IOException;
import java.sql.SQLException;

class InvalidIntersection {
    interface A {
        void execute() throws IOException;
    }

    interface B {
        void execute() throws SQLException;
    }

    static class Both implements A, B {
        @Override
        public void execute() throws IOException { }
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 InvalidIntersection.java
~~~

Phần quan trọng của thông báo `javac` Java 17:

~~~text
execute() in Both cannot implement execute() in B
overridden method does not throw IOException
~~~

Lớp `Both` thỏa interface A về ngoại lệ, nhưng **không thỏa interface B**. Compiler kiểm tra mọi hợp đồng mà cùng một phương thức phải thực hiện, không chỉ hợp đồng bạn nghĩ đến đầu tiên.

Cách sửa đơn giản cho ví dụ không cần ngoại lệ checked là bỏ `throws IOException` khỏi `Both.execute()`. Nếu body có API thực sự ném `IOException`, cần xử lý nội bộ, bọc thành ngoại lệ phù hợp, hoặc thiết kế lại các interface một cách có chủ đích.

**Đừng sửa bằng `throws Exception`.** `Exception` còn rộng hơn cả `IOException` lẫn `SQLException`, không giải quyết ràng buộc nào của hai hợp đồng.

## 12. Những loại ngoại lệ checked được phép khi cha khai báo nhiều loại

Giả sử:

~~~java
class Parent {
    void operate() throws java.io.IOException, java.sql.SQLException {
        // ...
    }
}
~~~

Lớp con có thể khai báo:

~~~java
class Child extends Parent {
    @Override
    void operate() throws java.io.FileNotFoundException {
        // ...
    }
}
~~~

Vì `FileNotFoundException` là subtype của `IOException`, chỉ cần được **ít nhất một nhánh checked mà cha cho phép** bao phủ, miễn các hợp đồng override liên quan khác cũng được thỏa.

Cũng có thể giữ cả `IOException` và `SQLException`, hoặc chỉ `SQLException` nếu implementation phù hợp. Không bắt buộc lớp con phải khai báo đủ tất cả kiểu đã có trong cha.

Tuy nhiên:

~~~java
// Không hợp lệ nếu method cha chỉ cho IOException và SQLException.
@Override
void operate() throws java.text.ParseException {
    // ...
}
~~~

`ParseException` là checked exception khác, không thuộc `IOException` hoặc `SQLException`, nên không thể được bổ sung trong lời khai báo override.

Tóm lại, với một hợp đồng cha thông thường, **mỗi kiểu checked mà override liệt kê phải được một loại checked trong `throws` của cha bao phủ**. Với nhiều hợp đồng thừa kế, cần kiểm tra tương thích theo từng hợp đồng, không chỉ gộp danh sách `throws` của tất cả cha thành một tập cho phép tùy ý.

## 13. Một phương thức override không có `throws` có thể ném checked exception bên trong không?

**Có thể tạo hoặc nhận checked exception trong body**, nhưng nó **không thể để checked exception không tương thích thoát ra**. Ví dụ một implementation có thể bắt `IOException` hoàn toàn:

~~~java
import java.io.IOException;

// Minh họa nằm trong một lớp đã implements interface phù hợp.
@Override
public String load() {
    try {
        return readData();
    } catch (IOException ex) {
        return "giá trị dự phòng";
    }
}
~~~

Cần định nghĩa `readData()` với `throws IOException`; đoạn này không độc lập. Nếu trả dữ liệu dự phòng hợp lệ theo hợp đồng, implementation có thể kết thúc bình thường mà không cần `throws IOException`.

Ngược lại, đoạn sau không hợp lệ khi cha không công bố `IOException`:

~~~java
@Override
public String load() {
    throw new java.io.IOException("lỗi đọc"); // Lỗi biên dịch.
}
~~~

Thông báo thường liên quan **unreported exception IOException; must be caught or declared to be thrown**. Nhưng nếu bạn thêm `throws IOException` vào override trong khi cha/interface không cho phép, lại gặp **lỗi ràng buộc override**.

Hai yêu cầu compile-time tồn tại đồng thời:

1. **Body phải xử lý hoặc khai báo checked exception có thể thoát.**
2. **Mệnh đề `throws` của override không được mở rộng checked exception so với hợp đồng cha.**

Không thể “lách” hai yêu cầu này chỉ bằng sửa từ khóa mà không thay đổi cách xử lý hoặc thiết kế.

## 14. `throw` và `throws` khác nhau

`throw` là câu lệnh **ném một đối tượng Throwable thực tế**:

~~~java
throw new java.io.FileNotFoundException("missing");
~~~

`throws` trong chữ ký **khai báo loại ngoại lệ có thể truyền ra khỏi phương thức**:

~~~java
void read() throws java.io.IOException {
    throw new java.io.FileNotFoundException("missing");
}
~~~

Ở phương thức trên, kiểu thực tế được ném là `FileNotFoundException`; `throws IOException` là hợp lệ vì nó bao phủ kiểu con.

Trong ràng buộc override, compiler kiểm tra **loại checked được khai báo** và những ngoại lệ body có thể ném. Việc bạn chỉ ném `FileNotFoundException` ở một nhánh không cho phép khai báo rộng thành `throws Exception` nếu cha chỉ `throws IOException`.

**Khai báo rộng quá mức cũng có thể làm chương trình không biên dịch**, dù trong triển khai thực tế không hề có trường hợp ném loại rộng đó. Vì vậy hãy chọn danh sách `throws` phản ánh đúng hợp đồng và giới hạn được thừa kế.

## 15. Kế thừa constructor không giống override phương thức

Constructor **không được override như phương thức instance**. Lớp con có thể có constructor riêng khai báo checked exception cần thiết cho quá trình tạo đối tượng, nhưng khi gọi constructor cha phải tuân theo kiểm tra checked exception của lời gọi `super(...)`.

Ví dụ:

~~~java
import java.io.IOException;
import java.sql.SQLException;

class Parent {
    Parent() throws IOException {
        // ...
    }
}

class Child extends Parent {
    Child() throws IOException, SQLException {
        super();
        // Có thể có bước riêng cần SQLException.
    }
}
~~~

Đoạn này hợp lệ xét ràng buộc checked exception của constructor: `Child()` không “override `Parent()`”; nó là constructor độc lập của lớp con, khai báo cả `IOException` từ `super()` và `SQLException` riêng nếu phù hợp.

Điểm cần nhớ: **đừng mang nguyên quy tắc throws khi override method áp dụng lên constructor**. Constructor được kiểm tra theo các quy tắc gọi và khởi tạo, không phải theo quan hệ override cùng chữ ký.

## 16. Overload cũng không phải override

Xét:

~~~java
class Parent {
    void save(String value) throws java.io.IOException { }
}

class Child extends Parent {
    void save(int value) throws java.sql.SQLException { }
}
~~~

`Child.save(int)` là một phương thức **overload**, vì danh sách tham số khác với `Parent.save(String)`. Nó không override `save(String)`, nên không bị ràng buộc bởi `throws IOException` của phương thức kia.

Nhưng nếu đổi thành `Child.save(String) throws SQLException`, đó là ứng viên override đúng chữ ký và vi phạm quy tắc checked exception.

**Dùng `@Override` để kiểm tra ý định.** Nếu bạn vô tình đổi tên phương thức hoặc kiểu tham số làm nó thành overload, compiler sẽ báo khi annotation không trỏ đến một phương thức được override phù hợp. Điều này giúp tránh lỗi tinh vi khi tưởng đã thay thế logic lớp cha.

Tương tự, phương thức `static` tham gia **method hiding**, không phải dynamic override như instance method, nhưng Java vẫn có các ràng buộc tương thích của chữ ký, gồm checked exception, đối với những phương thức static có quan hệ hiding phù hợp. Không nên dùng `static` như một cách tránh hợp đồng.

## 17. Quy tắc override còn liên quan những thành phần nào?

`throws` chỉ là **một phần** của điều kiện override. Một phương thức con còn phải thỏa:

- **Chữ ký hợp lệ:** cùng tên và tham số theo các quy tắc override, gồm các trường hợp generic/subsignature được JLS xác định.
- **Return type tương thích:** Java hỗ trợ **covariant return type** trong những điều kiện phù hợp; quy tắc trả về không phải quy tắc checked exception.
- **Không thu hẹp visibility trái phép:** ví dụ không thể override `public` method bằng `private` method.
- **`final`:** phương thức instance `final` không thể bị override.
- **Static vs instance:** không thể tùy ý chuyển một phương thức instance thành static để override.
- **Các generic throws/type variables:** với kiểu tham số hóa và type variable, JLS có quy tắc chi tiết về substitution, erasure và khả năng phát sinh cảnh báo unchecked.

Trong bài cơ bản với `IOException`, `SQLException` và lớp con của chúng, nguyên tắc “checked exception không rộng hơn hợp đồng cha” là công cụ chủ đạo. Khi xây API generic phức tạp, đừng áp dụng một phép so sánh chuỗi tên kiểu đơn giản thay cho kiểm tra kiểu đầy đủ của JLS.

Có thể gặp mã legacy dùng raw types hoặc các quan hệ generic tạo **unchecked warning** về `throws`. Cảnh báo kiểu không đồng nghĩa Java cho phép một override tùy ý ném bất kỳ checked exception nào; cần xem đúng quy tắc từ JLS và sửa cấu trúc generic khi cần.

## 18. Khi gọi qua interface và lớp cụ thể, `catch` có thể khác

Chú ý:

~~~java
interface Service {
    void run() throws java.io.IOException;
}

class FastService implements Service {
    @Override
    public void run() {
        System.out.println("OK");
    }
}
~~~

Nếu code gọi:

~~~java
Service service = new FastService();
try {
    service.run();
} catch (java.io.IOException ex) {
    // Hợp lệ: kiểu Service công bố IOException.
}
~~~

compiler dùng `Service.run() throws IOException`, vì biến có kiểu tĩnh `Service`.

Nếu code gọi:

~~~java
FastService concrete = new FastService();
concrete.run(); // Không cần try/catch IOException.
~~~

compiler dùng `FastService.run()`, không khai báo checked exception. Đây là hành vi quan trọng khi API thiết kế để mọi implementation có thể cung cấp tính năng khác nhau, nhưng caller chung phải xử lý theo một hợp đồng nhất quán.

Không nên giải thích rằng compiler “kiểm tra class object thật tại runtime để biết ngoại lệ nào cần bắt”. Quy tắc checked exception là **kiểm tra tại compile time**, trong khi Java chọn implementation instance method tại runtime.

## 19. Không tự động mở rộng hợp đồng bằng đổi `throws` ở override

Đôi khi bạn muốn thêm một đường lỗi mới sau khi đổi implementation, ví dụ phiên bản sau chuyển từ đọc cache sang gọi database. Nếu method của interface đang có chữ ký:

~~~java
String find() throws java.io.IOException;
~~~

bạn **không thể chỉ sửa implementation** thành:

~~~java
@Override
public String find() throws java.sql.SQLException {
    // ...
}
~~~

Cách đó phá ràng buộc compile-time. Cần quyết định ở **ranh giới thiết kế API**:

1. Lỗi SQL có được xử lý/retry cục bộ ở implementation để không thoát ra không?
2. Có một loại ngoại lệ thuộc hợp đồng hiện có và hợp ngữ nghĩa để bọc lỗi SQL không?
3. API có nên được chỉnh sửa công khai để caller nhận một abstraction lỗi mới không?
4. Nếu thay đổi public checked exception, caller hiện tại và source compatibility bị ảnh hưởng ra sao?
5. Có cần chia nhỏ interface khi các implementation có hợp đồng lỗi khác nhau về căn bản không?

Với code thư viện, thêm `throws Exception` vào lớp cha không phải giải pháp mặc định vì ép mọi caller xử lý một loại quá rộng. Thay đổi hợp đồng cần tài liệu, test và kế hoạch nâng cấp rõ ràng.

## 20. Liên hệ với nguyên tắc thay thế và thiết kế API

Một lớp con dùng thay lớp cha có thể đảm bảo “không gây thêm những loại lỗi checked bắt buộc xử lý”. Điều này cho phép caller viết code dựa trên abstraction và compiler kiểm tra được nghĩa vụ ngoại lệ.

Tuy nhiên, quy tắc Java **không giải quyết toàn bộ vấn đề substitutability**:

- Lớp con vẫn có thể ném một unchecked exception mới và compiler chấp nhận.
- Lớp con có thể thay đổi kết quả, điều kiện đầu vào hoặc side effect theo cách làm hỏng kỳ vọng caller.
- Một method không khai báo checked exception vẫn có thể thất bại ở runtime do lỗi môi trường, bug hoặc logic.

Vì vậy khi thiết kế override, cần xem xét cả **hợp đồng ngữ nghĩa** bên cạnh `throws`: tiền điều kiện, hậu điều kiện, trạng thái sau thất bại và cách ứng dụng phục hồi.

Một subclass không nên dùng unchecked exception như công cụ để **lách có hệ thống** một checked exception mà caller thực sự cần xử lý. Nếu mọi implementation hợp lệ đều cần công bố lỗi mới, có thể vấn đề nằm ở thiết kế abstraction.

## 21. Các lỗi biên dịch thường gặp và cách sửa

| Lỗi hoặc hiểu nhầm | Vì sao xảy ra | Cách sửa |
| --- | --- | --- |
| Override `throws SQLException` trong khi cha chỉ `throws IOException` | Thêm checked exception ngoài hợp đồng cha | Bắt, bọc đúng abstraction, hoặc chỉnh thiết kế API |
| Override `throws Exception` khi cha chỉ `throws IOException` | Kiểu checked rộng hơn | Giữ `IOException` hoặc subtype thích hợp |
| Override `throws IOException` khi cha không có checked `throws` | Không có checked exception tương ứng được cho phép | Xử lý nội bộ hoặc chọn cơ chế lỗi khác đúng hợp đồng |
| Nghĩ cứ là lớp con thì được thêm loại checked bất kỳ | Phá hợp đồng caller dùng kiểu cha | Mỗi checked exception override phải được hợp đồng cho phép |
| Nghĩ bỏ `throws` đồng nghĩa body không thể dùng API checked | Body có thể gọi rồi bắt lỗi | Xử lý đầy đủ trước khi phương thức kết thúc |
| Hai interface khai báo checked khác nhau, implementation chọn một bên | Phải thỏa **cả hai**, không chỉ một | Bỏ/bắt checked nếu không có subtype chung hợp lệ |
| Caller qua kiểu cha không bắt IOException vì biết object thật là class cache | Compiler kiểm tra theo **kiểu tĩnh** của lời gọi | Bắt/khai báo theo hợp đồng của kiểu tĩnh |
| Nghĩ unchecked cũng phải là subtype của các checked trong cha | Unchecked không bị ràng buộc tương tự | Vẫn cần thiết kế ngữ nghĩa hợp lý |
| Sửa signature thành `throws RuntimeException` để qua compiler | Có thể che đường lỗi thực sự caller cần xử lý | Cân nhắc hợp đồng thay vì lách bằng unchecked |
| Nhầm `throw` với `throws` | Một bên ném đối tượng, một bên khai báo khả năng lỗi | Phân biệt statement và method declaration |
| Nhầm constructor kế thừa override | Constructor không phải phương thức override | Áp dụng quy tắc constructor riêng |
| Đổi tham số thành overload rồi tưởng đã override | Method khác chữ ký không thay thế method cũ | Dùng `@Override`, kiểm tra signature |
| Chỉ xử lý lỗi compiler bằng việc bỏ `throws` | Checked exception có thể vẫn thoát từ body | Sửa đường xử lý hoặc truyền lỗi hợp lệ |

## 22. Checklist trước khi khai báo `throws` cho một override

Khi kiểm tra một lớp con hoặc implementation, hãy thực hiện lần lượt:

1. **Xác định chính xác phương thức cha/interface được override**, không nhầm overload.
2. **Đọc danh sách checked exception đã công bố** ở từng phương thức cha liên quan.
3. **Phân loại từng loại được nêu ở `throws` của lớp con**: checked hay unchecked?
4. Với **mỗi checked exception lớp con**, kiểm tra nó có được các hợp đồng cha liên quan cho phép theo quan hệ subtype và quy tắc JLS không.
5. Nếu có nhiều interface, **kiểm tra từng hợp đồng**; đừng đơn giản hợp nhất danh sách.
6. Đọc **body thực tế** để xác định checked exception có thể thoát từ API được gọi hoặc `throw`.
7. Nếu có checked exception mới, quyết định xử lý/recover, bọc có cause hoặc thay đổi abstraction.
8. Kiểm tra **lời gọi qua kiểu cha** có được caller bắt/khai báo đúng không.
9. Giữ `@Override` để tránh nhầm chữ ký và kiểm tra visibility, static/instance, return type.
10. Biên dịch ít nhất một đường **thành công** và một đường **thất bại**; với API công khai, thêm test dùng biến kiểu interface/lớp cha.
11. Tài liệu hóa điều kiện `@throws` cần thiết; không dùng Javadoc để thay chữ ký checked exception bắt buộc.
12. Tránh bọc lỗi thành unchecked chỉ để lặng lẽ đổi nghĩa vụ caller.

## 23. Tự kiểm tra

**Câu 1.** Parent khai báo `void read() throws IOException`. Child có được khai báo `throws FileNotFoundException` không?

**Đáp án:** Có. `FileNotFoundException` là subtype checked của `IOException`.

**Câu 2.** Cùng parent đó, child khai báo `throws Exception` có hợp lệ không?

**Đáp án:** Không. `Exception` rộng hơn `IOException`.

**Câu 3.** Child bỏ hoàn toàn `throws` có hợp lệ không?

**Đáp án:** Có, nếu body không để checked exception chưa được xử lý thoát ra.

**Câu 4.** Child khai báo `throws IllegalStateException` khi parent không khai báo checked exception nào có hợp lệ không?

**Đáp án:** Có xét ràng buộc checked `throws`, vì `IllegalStateException` là unchecked.

**Câu 5.** Nếu biến có kiểu tĩnh Parent nhưng chứa object Child đã bỏ `throws IOException`, caller của `variable.read()` phải xử lý IOException theo kiểu nào?

**Đáp án:** Theo chữ ký Parent khi kiểm tra compile-time; caller vẫn phải bắt hoặc khai báo `IOException` nếu phương thức cha công bố loại đó.

**Câu 6.** Hai interface cùng có `load()`, interface A `throws IOException` còn B `throws SQLException`. Implementation không có checked `throws` có hợp lệ không?

**Đáp án:** Có, miễn body không để checked exception trái hợp đồng thoát ra và các quy tắc override khác thỏa.

**Câu 7.** Trong ví dụ hai interface trên, implementation khai báo `throws IOException` có hợp lệ không?

**Đáp án:** Không; nó không thỏa hợp đồng B vì `IOException` không là subtype của `SQLException`.

**Câu 8.** Nếu body override gọi API ném `SQLException` nhưng cha chỉ cho `IOException`, có thể bỏ `throws SQLException` rồi để exception tự thoát không?

**Đáp án:** Không. Checked exception phải được bắt hoặc công bố bằng một kiểu được phép; không thể để nó thoát không xử lý chỉ vì bỏ khai báo.

**Câu 9.** Quy tắc này áp dụng y hệt lên constructor của lớp con không?

**Đáp án:** Không. Constructor không override; nó có chữ ký và nghĩa vụ checked riêng khi gọi `super(...)` hoặc các thao tác trong constructor.

**Câu 10.** Nếu lớp con đặt một phương thức cùng tên nhưng khác tham số, có phải override không?

**Đáp án:** Thường là overload; `@Override` giúp compiler kiểm tra xem có thật là override hợp lệ không.

**Câu 11.** Tại sao không nên đổi mọi `SQLException` thành `IOException` chỉ để biên dịch?

**Đáp án:** Vì quan hệ kiểu ở ngôn ngữ không thay thế tính đúng đắn của abstraction; mapping sai sẽ làm caller hiểu nhầm bản chất lỗi. Chỉ bọc khi loại đích có nghĩa hợp đồng phù hợp, và cần giữ `cause`.

**Câu 12.** Một method của interface khai báo `throws Exception`. Implementation có được dùng `throws IOException` không?

**Đáp án:** Có, vì `IOException` là subtype checked của `Exception` (giả sử không có hợp đồng override khác chặt hơn).

## 24. Tài liệu đối chiếu

- [Java Language Specification 17 — §8.4.8.3, Requirements in Overriding and Hiding](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.8.3): giới hạn checked exception trong phương thức override/hide, cùng các điều kiện về return và chữ ký.
- [Java Language Specification 17 — §8.4.6, Method Throws](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.6): ý nghĩa của mệnh đề `throws` và phân loại exception.
- [Java Language Specification 17 — §9.4.1, Inheritance and Overriding](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.4.1): quan hệ phương thức của interface và các quy tắc kế thừa.
- [Java Language Specification 17 — §11.2, Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2): catch-or-declare và ngoại lệ có thể phát sinh từ body.
- [Java SE 17 API — `IOException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/IOException.html) và [`FileNotFoundException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/FileNotFoundException.html): đối chiếu quan hệ subtype.
- [Java SE 17 API — `SQLException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/SQLException.html): checked exception ở nhánh khác với `IOException`.

**Kết luận:** khi một phương thức **override** phương thức cha/interface, danh sách `throws` của nó không được công bố checked exception mới hoặc rộng hơn ngoài hợp đồng được kế thừa. Bạn có thể **thu hẹp, giữ nguyên hoặc bỏ** các checked exception được phép; unchecked exception không bị giới hạn tương tự. Nếu implementation cần phát sinh checked exception khác, hãy **xử lý, bọc đúng abstraction với cause, hoặc xem lại hợp đồng API**, thay vì chỉ sửa chữ ký để qua compiler.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-inheritance-003">Thu hẹp hoặc bỏ khai báo checked exception</a>

<details>
<summary>Xem chi tiết</summary>

# Thu hẹp hoặc bỏ khai báo checked exception khi ghi đè phương thức trong Java

Khi một lớp con **ghi đè (override)** phương thức của lớp cha hoặc **triển khai (implements)** một phương thức từ interface, Java cho phép phương thức mới **khai báo ít checked exception hơn**, khai báo **loại checked exception hẹp hơn**, hoặc **không khai báo checked exception nào**. Ngược lại, phương thức ghi đè không được tự ý mở rộng hợp đồng bằng một checked exception mới/rộng hơn mà lời khai báo ở kiểu cha không cho phép.

Đây là quy tắc quan trọng để bảo đảm **tính thay thế trong kế thừa**: nơi đang làm việc với đối tượng kiểu cha không phải bất ngờ xử lý thêm một loại lỗi checked hoàn toàn mới chỉ vì runtime chọn triển khai lớp con.

Ví dụ cốt lõi:

```java
interface DataSource {
    String read(String key) throws java.io.IOException;
}

class FileDataSource implements DataSource {
    @Override
    public String read(String key)
            throws java.io.FileNotFoundException {
        if ("missing".equals(key)) {
            throw new java.io.FileNotFoundException(key);
        }
        return "value:" + key;
    }
}

class MemoryDataSource implements DataSource {
    @Override
    public String read(String key) {
        return "value:" + key;
    }
}
```

Đây là **các khai báo minh họa** cần được đặt trong tệp/package Java hợp lệ. Hai lớp triển khai đều đúng hợp đồng `DataSource.read() throws IOException`:

- `FileNotFoundException` là **lớp con của `IOException`**, nên lớp triển khai được khai báo `throws FileNotFoundException`.
- `MemoryDataSource.read()` **bỏ hẳn `throws IOException`**, nên caller qua kiểu concrete không buộc phải bắt checked `IOException`.
- Tuy nhiên, nếu caller giữ tham chiếu kiểu `DataSource`, trình biên dịch vẫn nhìn thấy **hợp đồng kiểu cha `throws IOException`**, ngay cả khi đối tượng runtime là `MemoryDataSource`.

**Ghi nhớ:** quy tắc này liên quan tới **checked exception trong hợp đồng phương thức**, không phải việc runtime “đổi” kiểu đối tượng ngoại lệ hay bảo đảm lớp con sẽ không bao giờ gặp lỗi.

## 1. Phân biệt checked và unchecked exception

Trước khi hiểu thu hẹp `throws`, phải phân biệt hai nhóm theo quy tắc kiểm tra của Java.

| Nhóm | Các ví dụ | Trình biên dịch bắt buộc bắt/khai báo? |
| --- | --- | --- |
| **Checked exception** | `IOException`, `FileNotFoundException`, `SQLException`, `ParseException` | Có, khi một checked exception có thể thoát khỏi phương thức và chưa được xử lý |
| **Unchecked exception** | `RuntimeException` và các lớp con như `IllegalArgumentException`, `IllegalStateException` | Không |
| **Unchecked `Error`** | `Error` và các lớp con như `AssertionError`, `OutOfMemoryError` | Không, thường không phải lỗi nghiệp vụ có thể phục hồi |

Một phần cây kiểu:

```text
Throwable
├── Error                               (unchecked)
└── Exception
    ├── RuntimeException                (unchecked)
    │   ├── IllegalArgumentException
    │   └── IllegalStateException
    └── IOException                     (checked)
        ├── FileNotFoundException       (checked)
        └── EOFException                (checked)
```

Cây chỉ là một **trích đoạn minh họa**, không liệt kê tất cả loại ngoại lệ. `SQLException` là một nhánh checked khác thuộc `Exception`, không nằm dưới `IOException`.

Khi nói “override không được khai báo exception rộng hơn”, cần nói đầy đủ hơn:

> **Không được mở rộng tập checked exception theo cách vi phạm hợp đồng của phương thức được override.** Một phương thức override vẫn có thể khai báo hoặc ném **unchecked exceptions**; trình biên dịch không áp dụng giới hạn checked exception lên chúng theo cùng cách.

Ví dụ, phương thức cha không khai báo checked exception; phương thức con vẫn có thể viết `throws IllegalStateException` dù đây thường là khai báo không bắt buộc. Tuy nhiên, điều đó không có nghĩa tùy ý biến điều kiện thành công bình thường thành lỗi runtime là một thiết kế tốt.

## 2. Quy tắc override: cùng loại, hẹp hơn hoặc không có checked `throws`

Cho phương thức cha:

```java
class Parent {
    public void execute() throws java.io.IOException {
        // ...
    }
}
```

Khi một lớp con override `execute()`, những khai báo dưới đây **hợp lệ** nếu thân phương thức tuân thủ chúng.

### A. Giữ nguyên checked exception

```java
class KeepContract extends Parent {
    @Override
    public void execute() throws java.io.IOException {
        // Có thể phát sinh IOException.
    }
}
```

Lớp con giữ nguyên phạm vi lỗi I/O mà caller đã biết từ lớp cha.

### B. Thu hẹp sang lớp con của checked exception

```java
class NarrowContract extends Parent {
    @Override
    public void execute()
            throws java.io.FileNotFoundException {
        // Chỉ khai báo một loại checked exception hẹp hơn.
    }
}
```

`FileNotFoundException extends IOException`, nên caller được bảo vệ theo hợp đồng `IOException` của `Parent` vẫn đủ khả năng bắt ngoại lệ con.

### C. Bỏ hoàn toàn checked `throws`

```java
class NoCheckedContract extends Parent {
    @Override
    public void execute() {
        // Không để checked exception thoát ra.
    }
}
```

Đây là lựa chọn hợp lệ. Việc phương thức cha khai báo `throws IOException` **không buộc lớp con phải giữ nguyên danh sách `throws`**.

### D. Bổ sung unchecked exception

```java
class RuntimeFailureContract extends Parent {
    @Override
    public void execute() throws IllegalStateException {
        throw new IllegalStateException("resource not ready");
    }
}
```

Ví dụ này hợp lệ về luật checked exception: `IllegalStateException` là unchecked. Nhưng caller phải xem xét ý nghĩa API và điều kiện lỗi thực tế, không lạm dụng unchecked exception để né ràng buộc thiết kế.

### E. Mở rộng checked exception — không hợp lệ

```java
// Cố ý KHÔNG BIÊN DỊCH.
class InvalidWiderContract extends Parent {
    @Override
    public void execute() throws Exception {
        // ...
    }
}
```

`Exception` bao trùm nhiều checked exception ngoài `IOException`, nên không thể thay thế hợp đồng đã có bằng một khai báo rộng hơn.

### F. Thêm một loại checked exception khác nhánh — không hợp lệ

```java
// Cố ý KHÔNG BIÊN DỊCH.
class InvalidNewChecked extends Parent {
    @Override
    public void execute() throws java.sql.SQLException {
        // ...
    }
}
```

`SQLException` không phải lớp con của `IOException`, nên caller qua `Parent` không được chuẩn bị để xử lý loại checked exception mới đó.

**Nguyên tắc tổng quát:** đối với mỗi loại checked exception được khai báo bởi override, phải có một loại checked exception tương ứng trong các khai báo được phép của phương thức bị override sao cho loại của override **cùng kiểu hoặc hẹp hơn**. Đồng thời, nếu thân override phát sinh checked exception khác không được bắt, bản thân thân phương thức vẫn phải tuân thủ danh sách `throws` đã khai báo.

## 3. Vì sao luật này bảo vệ khả năng thay thế kiểu cha?

Xét mã tầng gọi:

```java
static void process(Parent operation) {
    try {
        operation.execute();
    } catch (java.io.IOException ex) {
        System.out.println("I/O problem");
    }
}
```

Đây là phương thức minh họa, dựa trên lớp `Parent` có `throws IOException`. Tầng gọi nhận một biến kiểu `Parent` và biết rằng lỗi checked cần quan tâm thuộc nhóm `IOException`.

Giả sử lớp con được phép override thành `throws SQLException`. Khi runtime chọn lớp con:

1. Caller chỉ có `catch (IOException)` vì đó là hợp đồng công khai của `Parent`.
2. Lớp con có thể để `SQLException` đi ra.
3. Caller không hề được yêu cầu bắt/khai báo `SQLException` khi biên dịch theo kiểu `Parent`.
4. Hợp đồng về khả năng thay thế bị phá vỡ.

Bởi vậy Java cấm việc **mở rộng checked exception** trong override. Thu hẹp thì an toàn hơn: `catch (IOException)` của caller vẫn bắt được `FileNotFoundException` là lớp con, và nếu lớp con không ném checked exception nào thì càng không có thêm gánh nặng.

### “Lớp con hứa ít lỗi hơn” không có nghĩa “lớp con không thể hỏng”

Một implementation không khai báo `throws` vẫn có thể:

- Ném `IllegalArgumentException` khi tham số sai.
- Ném `IllegalStateException` khi trạng thái đối tượng không hợp lệ.
- Gặp `NullPointerException` do lỗi lập trình.
- Bị ảnh hưởng bởi `Error` nghiêm trọng của JVM.
- Chuyển một lỗi checked đã bắt thành unchecked wrapper nếu hợp đồng API cho phép.

Do đó, bỏ checked `throws` là **thay đổi về hợp đồng kiểm tra ở compile time**, không phải bảo đảm toàn bộ thực thi không bao giờ có ngoại lệ.

## 4. Java kiểm tra theo kiểu tĩnh của lời gọi, không chỉ theo đối tượng runtime

Một điểm dễ nhầm là cùng một đối tượng có thể được gọi qua hai loại tham chiếu khác nhau:

```java
DataSource source = new MemoryDataSource();
MemoryDataSource memory = new MemoryDataSource();
```

Đây là hai khai báo minh họa với các kiểu đã định nghĩa ở đầu bài.

Khi gọi:

```java
source.read("item");
```

trình biên dịch xem `source` có **kiểu tĩnh là `DataSource`**, nên lời gọi có hợp đồng `throws IOException`, dù đối tượng thật là `MemoryDataSource`.

Trong khi đó:

```java
memory.read("item");
```

trình biên dịch thấy phương thức được khai báo bởi `MemoryDataSource` **không có checked `throws`**. Vì vậy caller cụ thể này không phải bắt/khai báo `IOException` do lời gọi đó.

Tương tự nếu lớp con chỉ khai báo `throws FileNotFoundException`:

- Gọi qua **kiểu interface hoặc cha** có `throws IOException`: caller cần xử lý hoặc khai báo `IOException`.
- Gọi trực tiếp qua **kiểu lớp con** có `throws FileNotFoundException`: caller cần xử lý hoặc khai báo `FileNotFoundException` (hoặc một kiểu cha phù hợp).
- Đối tượng được ném tại runtime vẫn có **lớp thực tế**, không bị Java chuyển thành kiểu khai báo rộng hơn.

### Ví dụ không biên dịch nếu chỉ dựa vào subtype runtime

```java
// Cố ý KHÔNG BIÊN DỊCH nếu DataSource.read khai báo throws IOException.
static void incorrect(DataSource source) {
    try {
        source.read("x");
    } catch (java.io.FileNotFoundException ex) {
        System.out.println("missing");
    }
}
```

Chỉ bắt `FileNotFoundException` không đủ để bao phủ các `IOException` khác mà interface cho phép. Sửa bằng `catch (IOException)` sau nhánh cụ thể hoặc khai báo `throws IOException` ở phương thức gọi.

**Điều phải nhớ:** Java bảo vệ hợp đồng theo **kiểu nhìn thấy ở thời điểm biên dịch**, chứ không cần chạy chương trình trước để biết implementation nào sẽ được gọi.

## 5. Ví dụ Java 17+ hoàn chỉnh: interface với override hẹp và override không checked exception

Lưu toàn bộ nội dung sau vào **`NarrowedThrowsDemo.java`**:

```java
import java.io.FileNotFoundException;
import java.io.IOException;

public class NarrowedThrowsDemo {

    interface DocumentSource {
        String load(String id) throws IOException;
    }

    static final class LocalSource implements DocumentSource {
        @Override
        public String load(String id) throws FileNotFoundException {
            if ("missing".equals(id)) {
                throw new FileNotFoundException("absent: " + id);
            }
            return "local:" + id;
        }
    }

    static final class MemorySource implements DocumentSource {
        @Override
        public String load(String id) {
            return "memory:" + id;
        }
    }

    static void viaInterface(DocumentSource source, String id) {
        try {
            System.out.println("interface value=" + source.load(id));
        } catch (IOException ex) {
            System.out.println(
                    "interface error=" + ex.getClass().getSimpleName());
        }
    }

    static void viaLocal(LocalSource source, String id) {
        try {
            System.out.println("local value=" + source.load(id));
        } catch (FileNotFoundException ex) {
            System.out.println("local error=" + ex.getMessage());
        }
    }

    static void viaMemory(MemorySource source, String id) {
        // Không có checked exception trong khai báo concrete này.
        System.out.println("memory value=" + source.load(id));
    }

    public static void main(String[] args) {
        LocalSource local = new LocalSource();
        MemorySource memory = new MemorySource();

        viaInterface(local, "missing");
        viaLocal(local, "missing");
        viaInterface(memory, "ok");
        viaMemory(memory, "ok");
    }
}
```

Biên dịch và chạy với **JDK 17 trở lên**:

```bash
javac -encoding UTF-8 NarrowedThrowsDemo.java
java NarrowedThrowsDemo
```

**Stdout chính xác:**

```text
interface error=FileNotFoundException
local error=absent: missing
interface value=memory:ok
memory value=memory:ok
```

### Phân tích kết quả

**`viaInterface(local, "missing")`:** biến `source` trong helper có kiểu tĩnh `DocumentSource`, nên handler `catch (IOException)` phù hợp. Thực thi động chọn `LocalSource.load`, ném đối tượng `FileNotFoundException`; handler lớp cha bắt được và `getClass().getSimpleName()` cho biết kiểu thực tế.

**`viaLocal(local, "missing")`:** helper nhận kiểu cụ thể `LocalSource`. Trình biên dịch biết `load` chỉ khai báo checked `FileNotFoundException`; handler `catch (FileNotFoundException)` là đủ. Message gốc được in mà không bị đổi kiểu.

**`viaInterface(memory, "ok")`:** caller vẫn dùng kiểu interface có `throws IOException` và phải xét checked exception theo hợp đồng, nhưng implementation `MemorySource.load` chạy thành công và không ném checked exception.

**`viaMemory(memory, "ok")`:** caller dùng kiểu concrete, phương thức không khai báo checked exception, nên lời gọi không cần `try-catch` hay `throws IOException`.

Ví dụ cố tình không đọc file thật, nhờ đó kết quả ổn định giữa các máy và tập trung đúng quy tắc override.

## 6. Khi nào bỏ `throws` là đúng, khi nào là cách che lỗi?

Bỏ `throws IOException` khỏi override là hợp lệ **nếu phần thân không để checked `IOException` thoát ra**. Ví dụ một implementation lưu hoàn toàn trong bộ nhớ không dùng I/O checked, nên bỏ `throws` là tự nhiên.

Ngược lại, nếu lớp con thực sự gọi một API có thể ném `IOException`:

```java
class RealFileSource implements DataSource {
    @Override
    public String read(String key) {
        return java.nio.file.Files.readString(
                java.nio.file.Path.of(key));
    }
}
```

Đây là **ví dụ cố ý KHÔNG BIÊN DỊCH**: `Files.readString` có thể ném checked `IOException`, nhưng phương thức override không bắt lỗi và cũng không khai báo loại checked exception đó.

Có hai hướng sửa hợp lệ tùy thiết kế:

**Giữ khai báo checked exception phù hợp:**

```java
@Override
public String read(String key) throws java.io.IOException {
    return java.nio.file.Files.readString(
            java.nio.file.Path.of(key));
}
```

**Hoặc xử lý lỗi checked bên trong, không để nó đi ra:**

```java
@Override
public String read(String key) {
    try {
        return java.nio.file.Files.readString(
                java.nio.file.Path.of(key));
    } catch (java.io.IOException cause) {
        throw new java.io.UncheckedIOException(
                "Cannot read required data", cause);
    }
}
```

Các đoạn trên là **phần phương thức minh họa** cho một lớp triển khai `DataSource`; không phải hai phương thức cùng chữ ký để đặt đồng thời trong một lớp.

Chú ý: lựa chọn thứ hai **không biến lỗi I/O thành thành công**. Nó chỉ chuyển một checked `IOException` thành unchecked `UncheckedIOException`, có giữ `cause`. Việc chuyển như vậy **có chủ đích** khi hợp đồng API chấp nhận unchecked failure; không nên chỉ bọc để tránh yêu cầu của compiler mà không xét trách nhiệm caller.

### Không thay bằng `catch (IOException ex) { return null; }` vô điều kiện

Một cách bỏ `throws` sai về nghiệp vụ:

```java
@Override
public String read(String key) {
    try {
        return java.nio.file.Files.readString(
                java.nio.file.Path.of(key));
    } catch (java.io.IOException ex) {
        return null;
    }
}
```

Dù đoạn này **có thể biên dịch**, caller không biết `null` là nội dung hợp lệ, không tìm thấy file hay lỗi quyền truy cập. Lỗi I/O bị mất và chương trình có thể tiếp tục trong trạng thái sai.

**Bỏ khai báo checked exception phải phù hợp với hành vi và hợp đồng**, không đồng nghĩa nuốt ngoại lệ rồi coi như công việc thành công.

## 7. Override một phương thức của abstract class

Quy tắc áp dụng giống nhau nếu phương thức được khai báo trong **abstract class**:

```java
abstract class BaseReader {
    abstract String read()
            throws java.io.IOException;
}

final class ConstantReader extends BaseReader {
    @Override
    String read() {
        return "fixed";
    }
}

final class MissingReader extends BaseReader {
    @Override
    String read()
            throws java.io.FileNotFoundException {
        throw new java.io.FileNotFoundException("record absent");
    }
}
```

Đây là các khai báo minh họa hợp lệ trong tệp Java phù hợp. `BaseReader.read` cho phép `IOException`; lớp concrete có thể không ném checked exception hoặc khai báo subtype `FileNotFoundException`.

Không có ngoại lệ đặc biệt chỉ vì phương thức cha `abstract`. Nó vẫn công bố hợp đồng mà các lớp kế thừa phải tôn trọng.

### Một phương thức `final` thì không thể override

Nếu lớp cha đánh dấu phương thức `final`:

```java
class Base {
    public final void run() throws java.io.IOException {}
}
```

thì lớp con **không thể ghi đè** `run()` để đổi danh sách checked exception, bất kể định thu hẹp hay mở rộng. Bởi vì phương thức đã bị cấm override, các quy tắc cho override không còn tình huống để áp dụng.

## 8. Một phương thức có nhiều checked exception

Giả sử lớp cha khai báo hai nhánh lỗi:

```java
class Importer {
    public void importData()
            throws java.io.IOException, java.sql.SQLException {
        // ...
    }
}
```

Lớp con có thể:

- Giữ cả hai checked exception nếu thực sự cần.
- **Bỏ một nhánh** và giữ nhánh còn lại.
- Thu hẹp nhánh I/O từ `IOException` thành `FileNotFoundException`.
- Thu hẹp nhánh SQL từ `SQLException` thành `SQLTimeoutException` nếu phần thân chỉ phát sinh loại này.
- **Bỏ cả hai** nếu không để checked exception nào lan ra.

Ví dụ override hợp lệ:

```java
class SpecializedImporter extends Importer {
    @Override
    public void importData()
            throws java.io.FileNotFoundException,
                   java.sql.SQLTimeoutException {
        // ...
    }
}
```

Đây là ví dụ minh họa; hai loại được khai báo đều là subtype của hai nhánh checked mà lớp cha cho phép.

Override **không hợp lệ**:

```java
// Cố ý KHÔNG BIÊN DỊCH.
class WrongImporter extends Importer {
    @Override
    public void importData()
            throws java.text.ParseException {
        // ...
    }
}
```

`ParseException` là checked exception không được một trong hai nhánh của lớp cha bao phủ. Việc “có nhiều loại checked exception” trong cha không tạo quyền thêm bất kỳ loại checked exception mới nào.

### Có cần lặp lại subtype lẫn supertype?

Ví dụ `throws IOException, FileNotFoundException` là cú pháp có thể viết được nhưng `FileNotFoundException` đã được bao phủ bởi `IOException`, nên danh sách thường **dư thừa**. Tài liệu JavaDoc có thể mô tả chi tiết subtype phát sinh trong điều kiện nào mà không cần làm chữ ký dài hơn.

## 9. Một lớp triển khai nhiều interface cùng chữ ký

Đây là tình huống quan trọng để hiểu giới hạn **không mở rộng checked exception**.

Giả sử hai interface độc lập có cùng phương thức:

```java
interface FileSync {
    void sync() throws java.io.IOException;
}

interface DatabaseSync {
    void sync() throws java.sql.SQLException;
}
```

Một lớp triển khai cả hai:

```java
class HybridSync implements FileSync, DatabaseSync {
    @Override
    public void sync() {
        System.out.println("sync complete");
    }
}
```

**Hợp lệ.** Phương thức `sync()` của `HybridSync` không khai báo checked exception nào; vì vậy nó đáp ứng **đồng thời** hợp đồng của `FileSync` và `DatabaseSync`.

Nhưng thử khai báo:

```java
// Cố ý KHÔNG BIÊN DỊCH.
class InvalidHybridSync implements FileSync, DatabaseSync {
    @Override
    public void sync() throws java.io.IOException {
        // ...
    }
}
```

Không hợp lệ vì `DatabaseSync.sync()` **không cho phép checked `IOException`**. Tương tự, `throws SQLException` không phù hợp với `FileSync.sync()`.

Với hai loại checked exception không có quan hệ kế thừa như `IOException` và `SQLException`, implementation thỏa cả hai hợp đồng thường **không thể khai báo loại checked exception nào thuộc riêng một nhánh**. Nó phải xử lý checked exception trong thân hoặc lựa chọn thiết kế interface phù hợp.

### Quy tắc thực hành

Khi nhiều hợp đồng có phương thức cùng signature được một implementation thỏa đồng thời, checked exception của implementation phải nằm trong phần **giao nhau về những loại lỗi được phép**, chứ không phải phép **hợp** của danh sách `throws` từ tất cả interface.

Nếu các loại ở interface có quan hệ cha/con, implementation có thể khai báo loại phù hợp với **cả hai**. Nếu hai nhóm hoàn toàn độc lập và không có subtype checked chung thì khả năng bỏ hết checked `throws` có thể là cách hợp lệ duy nhất theo chữ ký.

## 10. Ví dụ Java 17+ hoàn chỉnh: thực hiện hai hợp đồng interface

Lưu tệp **`MultipleInterfaceThrowsDemo.java`**:

```java
import java.io.IOException;
import java.sql.SQLException;

public class MultipleInterfaceThrowsDemo {

    interface FileSync {
        void sync() throws IOException;
    }

    interface DatabaseSync {
        void sync() throws SQLException;
    }

    static final class HybridSync
            implements FileSync, DatabaseSync {

        @Override
        public void sync() {
            System.out.println("hybrid: synced");
        }
    }

    static void viaFile(FileSync source) {
        try {
            source.sync();
            System.out.println("file: completed");
        } catch (IOException ex) {
            System.out.println("file: I/O error");
        }
    }

    static void viaDatabase(DatabaseSync source) {
        try {
            source.sync();
            System.out.println("database: completed");
        } catch (SQLException ex) {
            System.out.println("database: SQL error");
        }
    }

    static void viaHybrid(HybridSync source) {
        source.sync();
        System.out.println("hybrid: completed");
    }

    public static void main(String[] args) {
        HybridSync hybrid = new HybridSync();

        viaFile(hybrid);
        viaDatabase(hybrid);
        viaHybrid(hybrid);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 MultipleInterfaceThrowsDemo.java
java MultipleInterfaceThrowsDemo
```

**Stdout chính xác:**

```text
hybrid: synced
file: completed
hybrid: synced
database: completed
hybrid: synced
hybrid: completed
```

### Vì sao code biên dịch?

- `HybridSync.sync()` không có checked `throws`, phù hợp với cả hai interface.
- `viaFile` nhận **kiểu tĩnh `FileSync`**, nên trình biên dịch yêu cầu xử lý hoặc khai báo `IOException`.
- `viaDatabase` nhận **kiểu tĩnh `DatabaseSync`**, nên phải xét `SQLException`.
- `viaHybrid` nhận **kiểu tĩnh `HybridSync`**, nên không cần bắt checked exception nào từ lời gọi `sync()`.
- Việc cả ba lời gọi cùng chạy đúng implementation là quyết định **dispatch ở runtime**; các nghĩa vụ checked exception vẫn được kiểm tra theo **khai báo kiểu ở điểm gọi**.

Ví dụ thể hiện rõ rằng **một implementation không cần sao chép `throws` của interface**, và caller có thể thấy hợp đồng khác nhau qua những kiểu tham chiếu khác nhau.

## 11. Nếu lớp cha không khai báo checked exception thì sao?

Nếu lớp cha đã có phương thức không khai báo checked exception:

```java
class BaseTask {
    public void run() {
        System.out.println("base task");
    }
}
```

Override hợp lệ:

```java
class FastTask extends BaseTask {
    @Override
    public void run() {
        System.out.println("fast task");
    }
}
```

Override không hợp lệ:

```java
// Cố ý KHÔNG BIÊN DỊCH.
class RiskyTask extends BaseTask {
    @Override
    public void run() throws java.io.IOException {
        throw new java.io.IOException("network unavailable");
    }
}
```

Lớp cha không công bố checked `IOException`, nên lớp con không thể tự mở rộng hợp đồng này. Nếu triển khai mới thực sự gọi API ném checked exception, phải tự bắt và xử lý hoặc chuyển sang unchecked exception có chủ đích; không thể đơn giản thêm `throws IOException` ở override.

Nếu muốn nhiều implementation có thể để lỗi I/O checked thoát ra, **hợp đồng cha/interface cần được thiết kế phù hợp từ đầu**. Thay đổi hợp đồng đã công khai có thể ảnh hưởng tới tất cả callers và implementers hiện hữu.

## 12. Checked exception từ thân phương thức vẫn phải được xử lý đúng

Quy tắc cho phép **bỏ** checked `throws` **không miễn** trách nhiệm kiểm tra exception trong thân override.

Ví dụ:

```java
@Override
public void save() {
    writeFile(); // writeFile() throws IOException
}
```

Đây là **ví dụ cố ý chưa hợp lệ**, giả định `writeFile()` ném checked `IOException` và `save` không được khai báo checked exception.

Muốn biên dịch:

- Thêm `throws IOException` nếu **hợp đồng cha cho phép**.
- Bắt `IOException` và thực sự phục hồi trong thân.
- Bọc thành một ngoại lệ unchecked phù hợp có `cause` nếu thiết kế API đòi hỏi.
- Thay đổi phân tách trách nhiệm hoặc thiết kế lại interface, nếu checked error thực sự thuộc hợp đồng không thể bỏ qua.

Không nên chỉ viết:

```java
catch (java.io.IOException ignored) {
}
```

Handler rỗng là một cách lách compiler nhưng thường che khuất thất bại. Đặc biệt với thao tác ghi dữ liệu, caller có thể nhận tín hiệu thành công sai.

## 13. Checked exception được kiểm tra theo chữ ký gọi, không theo nội dung `catch` bên trong

Một lớp con không khai báo checked exception có thể dùng `try-catch` trong thân:

```java
@Override
public void run() {
    try {
        externalOperation();
    } catch (java.io.IOException ex) {
        reportFailure(ex);
    }
}
```

Đây là mã minh họa; giả định `externalOperation` có thể ném checked `IOException`, `reportFailure` xử lý theo hợp đồng và không để checked error mới thoát ra.

Vì `IOException` đã được bắt và xử lý trong override, caller không bắt buộc phải bắt nó. Nhưng điều này chỉ đúng nếu handler **thực hiện hành động có nghĩa** hoặc diễn đạt lại thất bại theo kiểu kết quả hợp đồng, không tự động biến mọi lỗi thành thành công.

### Catch đã xử lý lỗi, Java không bắt bạn khai báo nó

`throws` là khai báo cho checked exception **có thể thoát khỏi phương thức**. Nếu mọi đường checked exception đều được xử lý thích hợp bên trong, danh sách `throws` có thể rỗng. Điều này giúp lớp con cung cấp triển khai ổn định hơn hợp đồng cha mà không gây thêm gánh nặng cho người gọi.

## 14. Unchecked exception: được phép nhưng vẫn cần hợp đồng

Giả sử phương thức cha:

```java
interface Processor {
    void process() throws java.io.IOException;
}
```

Một lớp triển khai có thể viết:

```java
class FailingProcessor implements Processor {
    @Override
    public void process() throws IllegalStateException {
        throw new IllegalStateException("processor not initialized");
    }
}
```

Đây là đoạn Java minh họa. `IllegalStateException` thuộc `RuntimeException` và là unchecked. Java cho phép khai báo dù interface không liệt kê nó.

Tuy nhiên, không nên kết luận “unchecked exception hoàn toàn không phải hợp đồng”. Ví dụ một thư viện có thể ghi rõ `IllegalArgumentException` cho tham số sai và `IllegalStateException` cho trạng thái không cho phép. Caller có thể quyết định xử lý những lỗi này, dù compiler không buộc.

**Đừng đổi checked thành unchecked chỉ để vượt qua lỗi biên dịch** nếu tầng gọi vẫn cần khôi phục hợp lý. Việc chọn loại lỗi là một quyết định thiết kế API.

## 15. Ghi đè, overload, constructor và method reference không phải một việc

### Override khác overload

Hai phương thức chỉ khác danh sách `throws` **không tạo thành overload**:

```java
// Cố ý KHÔNG BIÊN DỊCH nếu hai khai báo trong cùng một lớp.
void fetch() throws java.io.IOException {}
void fetch() throws java.io.FileNotFoundException {}
```

Java không dùng `throws` để phân biệt chữ ký overload. Override dựa trên tên/phần tham số và các quy tắc signature/subsignature của ngôn ngữ, không dựa vào việc danh sách `throws` có giống hệt hay không.

Một method overload mới với **danh sách tham số khác** có thể có hợp đồng checked exception riêng; quy tắc “không mở rộng `throws`” ở override không được áp dụng máy móc cho mọi overload.

### Constructor không được override

Một **constructor** không được kế thừa/override như phương thức thông thường. Constructor của lớp con có thể có danh sách `throws` riêng và phải tuân thủ checked exception phát sinh từ lời gọi `super(...)` cũng như phần khởi tạo của chính nó.

Vì vậy đừng áp đặt quy tắc override method một cách nguyên xi lên constructor của lớp con. Nếu constructor cha ném checked exception, constructor con phải xử lý/khai báo theo quy tắc khởi tạo đối tượng tương ứng.

### Method reference và lambda dùng hợp đồng của functional interface

Ví dụ:

```java
@FunctionalInterface
interface Loader {
    String load() throws java.io.IOException;
}
```

Một lambda triển khai `Loader` có thể không ném checked exception:

```java
Loader loader = () -> "cached";
```

Đây là các khai báo minh họa. Nhưng caller qua biến `Loader` vẫn có nghĩa vụ checked `IOException` khi gọi `loader.load()` theo khai báo của interface.

Ngược lại, lambda không thể tự ý ném một checked exception không tương thích với `throws` của functional method. Quy tắc phù hợp với hợp đồng của kiểu mục tiêu, dù lambda không có dòng khai báo `throws` riêng.

## 16. Vì sao nên đánh dấu `@Override`?

`@Override` giúp trình biên dịch xác nhận rằng phương thức thực sự ghi đè một khai báo hợp lệ. Điều này đặc biệt hữu ích nếu người viết đổi tên hoặc sai tham số:

```java
class Parent {
    void load(String path) throws java.io.IOException {}
}

class Child extends Parent {
    @Override
    void load() {
        // Sai: không override load(String).
    }
}
```

Đây là **mã cố ý không biên dịch**, bởi `load()` khác chữ ký của `load(String)`.

Nếu bỏ `@Override`, `Child.load()` có thể trở thành một overload độc lập, khiến người viết tưởng đã bỏ `throws IOException` của phương thức cha trong khi thực ra **chưa override phương thức cần sửa**.

Vì thế khi thu hẹp hoặc bỏ checked `throws`, dùng `@Override` là thói quen quan trọng để chắc chắn bạn đang sửa đúng hợp đồng kế thừa.

### Quyền truy cập cũng phải tương thích

Một override không được làm quyền truy cập hẹp hơn so với phương thức mà nó ghi đè. Chẳng hạn một phương thức `public` từ interface phải được triển khai bằng phương thức `public`. Đây là ràng buộc **khác** với checked exception nhưng thường xuất hiện đồng thời khi viết code override.

## 17. Lỗi thường gặp trong thiết kế hợp đồng checked exception

| Sai lầm | Vì sao không đúng | Cách sửa |
| --- | --- | --- |
| Lớp con khai báo `throws Exception` khi cha chỉ cho `IOException` | Mở rộng checked exception | Giữ `IOException` hoặc subtype phù hợp |
| Lớp con khai báo `throws SQLException` khi cha chỉ cho `IOException` | Thêm checked exception khác nhánh | Bắt/đổi lỗi trong implementation hoặc thiết kế lại hợp đồng |
| Nghĩ lớp con bắt buộc giữ `throws IOException` từ interface | Override có thể thu hẹp hoặc bỏ checked `throws` | Chỉ khai báo những checked exception còn được để thoát ra |
| Bỏ `throws` nhưng vẫn gọi `Files.readString` không bắt lỗi | Checked `IOException` chưa được xử lý | Bắt, khai báo trong phạm vi cha cho phép hoặc chuyển lỗi có chủ đích |
| Bắt lỗi rồi trả `null` không có hợp đồng | Che thất bại, dễ gây lỗi ở tầng gọi | Phục hồi có nghĩa hoặc báo lỗi tường minh |
| Nghĩ gọi object qua interface đã biết implementation không ném thì bỏ `catch` được | Compile-time kiểm tra kiểu interface | Xử lý theo `throws` của kiểu tĩnh |
| Nghĩ unchecked exception cũng bị cấm tuyệt đối khi override | Ràng buộc chính áp dụng cho checked exception | Hiểu `RuntimeException` và `Error` |
| Nghĩ nhiều interface được phép hợp tất cả danh sách `throws` | Implementation phải thỏa đồng thời mọi hợp đồng | Dùng tập checked exception phù hợp với tất cả |
| Nghĩ constructor chịu cùng quy tắc override | Constructor không được override | Kiểm tra quy tắc khởi tạo và `super(...)` |
| Nghĩ thay `throws` tạo overload khác | `throws` không nằm trong tiêu chí phân biệt overload | Phân biệt tên và danh sách tham số |
| Không viết `@Override` rồi vô tình overload | Có thể không ghi đè phương thức dự kiến | Thêm `@Override` để compiler kiểm tra |
| Đổi checked exception thành unchecked tùy tiện | Có thể làm caller khó phục hồi hợp lý | Thiết kế hợp đồng và cause rõ ràng |
| Chỉ dựa vào runtime type mà bỏ trách nhiệm checked exception | Compiler xét kiểu tĩnh | Đọc chữ ký của phương thức được gọi |

## 18. Checklist khi override một phương thức có `throws`

Trước khi sửa chữ ký trong lớp con:

1. **Xác định phương thức được ghi đè** và thêm `@Override`.
2. **Đọc `throws` ở lớp cha/interface**: những loại checked exception nào được cho phép?
3. **Xác định lỗi thực tế của implementation**: I/O, SQL, parse hay không có lỗi checked nào?
4. **Nếu giữ checked `throws`**, xác nhận từng loại là cùng kiểu hoặc subtype được hợp đồng cha bao phủ.
5. **Nếu bỏ `throws`**, bảo đảm mọi checked exception phát sinh trong thân đã được xử lý có nghĩa hoặc không còn có thể thoát ra.
6. **Nếu dùng nhiều interface**, kiểm tra implementation thỏa đồng thời mọi khai báo cùng signature.
7. **Đọc kiểu tĩnh của caller**: qua interface/cha hay kiểu concrete? Nghĩa vụ bắt/khai báo có thể khác nhau.
8. **Xét unchecked exception** có thể xảy ra: quy tắc compiler khác nhưng trách nhiệm tài liệu hóa API vẫn còn.
9. **Đừng nuốt lỗi để được biên dịch**: fallback phải có hợp đồng, không trả thành công giả.
10. **Nếu bọc lỗi checked sang unchecked**, cân nhắc giữ `cause` và chọn subtype thích hợp với tầng nghiệp vụ.
11. **Kiểm tra access và chữ ký**: không giảm phạm vi truy cập, không vô tình overload.
12. **Dùng test/compile thực tế**: thử lời gọi qua cả kiểu cha và kiểu con, kể cả trường hợp có lỗi.

## 19. Câu hỏi tự kiểm tra

**Câu 1.** Cha khai báo `void work() throws IOException`. Con khai báo `void work() throws FileNotFoundException` có hợp lệ không?

**Có.** `FileNotFoundException` là subtype của `IOException`.

**Câu 2.** Con bỏ hoàn toàn `throws` có được không?

**Có**, nếu thân override không để checked exception chưa được xử lý thoát ra.

**Câu 3.** Cha khai báo `throws IOException`; con khai báo `throws Exception` có hợp lệ không?

**Không.** `Exception` rộng hơn `IOException` và có thể cho phép checked exception mới.

**Câu 4.** Cha khai báo `throws IOException`; con khai báo `throws SQLException` có hợp lệ không?

**Không.** Hai loại checked exception ở hai nhánh độc lập.

**Câu 5.** Cha không khai báo checked exception; con khai báo `throws IllegalStateException` được không?

**Được**, vì `IllegalStateException` là unchecked. Nhưng thiết kế hành vi vẫn phải hợp lý.

**Câu 6.** Interface khai báo `load() throws IOException`. Lớp concrete override không có `throws`. Caller giữ biến kiểu interface có được bỏ hoàn toàn việc bắt/khai báo `IOException` khi gọi `load()` không?

**Không.** Compiler nhìn hợp đồng của kiểu interface ở điểm gọi.

**Câu 7.** Lớp concrete không khai báo checked exception; caller chỉ gọi qua kiểu concrete. Compiler có buộc bắt `IOException` không?

**Không**, nếu đó là lời gọi duy nhất và không có nguồn checked `IOException` khác trong biểu thức/phạm vi cần xét.

**Câu 8.** Hai interface có cùng `sync()`, một bên `throws IOException`, bên kia `throws SQLException`. Implementation không khai báo checked exception có hợp lệ không?

**Có.** Nó thỏa cả hai hợp đồng.

**Câu 9.** Nếu phương thức con muốn ném checked `ParseException` nhưng cha chỉ cho `IOException`, nên làm gì?

**Không** thể thêm trực tiếp `throws ParseException` vào override. Cần xử lý trong thân, chuyển sang kiểu được hợp đồng cho phép khi có ý nghĩa, hoặc thiết kế lại API ở một ranh giới có thẩm quyền.

**Câu 10.** Thêm `throws RuntimeException` có làm caller bắt buộc `catch RuntimeException` không?

**Không.** `RuntimeException` là unchecked, dù có được ghi tường minh trong `throws`.

**Câu 11.** Hai phương thức cùng tên và cùng tham số, một `throws IOException` và một `throws FileNotFoundException`, có tạo overload hợp lệ không?

**Không.** Chỉ khác `throws` không tạo hai chữ ký overload khác nhau.

**Câu 12.** Constructor lớp con có “override constructor cha” và phải khai báo checked exception hẹp hơn không?

**Không.** Constructor không được override; kiểm tra lỗi của constructor con theo luật khởi tạo và `super(...)`.

## 20. Hướng dẫn thiết kế API để không phải “lách” checked exception

### Chọn mức trừu tượng của loại lỗi

Một interface có nhiều implementation — file, network, memory — có thể chọn `throws IOException` nếu tất cả thao tác đều được mô hình hóa nhất quán thành I/O. Nhưng nếu chỉ có một implementation truy cập database còn interface không phải hợp đồng lưu trữ chung, thêm `throws SQLException` ở lớp cụ thể không tự tương thích với interface cũ.

Ở bước thiết kế, hãy hỏi:

- Caller có thực sự có khả năng **phục hồi** lỗi checked này không?
- Hợp đồng của kiểu cha có dự kiến **nhiều implementation** không?
- Có cần một exception trừu tượng theo domain để tách caller khỏi chi tiết file/SQL không?
- Một lỗi có phải failure có thể đoán trước theo nghiệp vụ hay là bug/precondition không?
- Có phù hợp khi dùng dạng kết quả như `Optional`, `Result` hoặc status rõ ràng thay vì checked exception cho một kết quả “không tìm thấy” bình thường không?

Trả lời những câu này giúp giảm việc phải bắt rồi nuốt hoặc bọc lỗi chỉ vì khác biệt chữ ký.

### Thay đổi hợp đồng công khai cần đánh giá tương thích

Thu hẹp `throws` ở một override cụ thể thường không tạo nghĩa vụ checked mới cho caller, nhưng **đổi `throws` của interface hoặc phương thức cha** có thể ảnh hưởng các implementation khác. Nếu bỏ `throws IOException` ở interface, những lớp triển khai còn khai báo `throws IOException` sẽ có nguy cơ không biên dịch sau thay đổi đó.

Vì vậy phải phân biệt:

- **Thu hẹp ở override của lớp con:** thường là lựa chọn hợp lệ trong giới hạn của hợp đồng cha hiện có.
- **Thay đổi hợp đồng ở lớp cha/interface:** là thay đổi API rộng hơn, cần xem xét toàn bộ implementers/callers và tương thích của thư viện.

Đừng áp dụng quy tắc thu hẹp ở một phương thức lớp con để kết luận việc xóa checked `throws` ở interface luôn an toàn cho toàn hệ thống.

## Kết luận

Khi ghi đè một phương thức trong Java, lớp con có thể **giữ nguyên**, **thu hẹp** hoặc **bỏ hoàn toàn** những checked exception mà lớp cha/interface cho phép, nhưng **không được mở rộng sang một loại checked exception mới hoặc rộng hơn**. Quy tắc này bảo vệ người gọi sử dụng kiểu cha: implementation mới không được tạo thêm nghĩa vụ checked exception mà hợp đồng gốc chưa công bố.

Hãy luôn phân biệt **hợp đồng kiểu tĩnh ở caller**, **kiểu runtime của ngoại lệ**, và **ngoại lệ thực sự thoát khỏi thân override**. Việc giảm danh sách `throws` phải phản ánh hành vi xử lý thật, chứ không phải nuốt lỗi hoặc bọc thành unchecked exception một cách tùy tiện. Với nhiều interface cùng chữ ký, implementation phải thỏa **đồng thời** mọi ràng buộc checked exception, còn constructor, overload và các ngoại lệ unchecked có những quy tắc riêng cần xem đúng ngữ cảnh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-inheritance-004">Unchecked exception khi ghi đè</a>

<details>
<summary>Xem chi tiết</summary>

# Unchecked exception khi ghi đè phương thức trong Java

> **Java Core – Exception** · Nhóm **Kế thừa và hợp đồng** · Chủ đề: phương thức override được khai báo hoặc ném unchecked exception như thế nào, so sánh với giới hạn của checked exception và ảnh hưởng tới caller. Các ví dụ hoàn chỉnh dùng **Java 17+**.

## 1. Kết luận quan trọng nhất

Khi một phương thức **ghi đè (override)** phương thức của lớp cha hoặc triển khai phương thức của interface, Java giới hạn **checked exception** được khai báo trong mệnh đề `throws`, nhưng **không áp đặt cùng giới hạn đối với unchecked exception**.

Ví dụ:

~~~java
interface Job {
    void execute();
}

class RemoteJob implements Job {
    @Override
    public void execute() throws IllegalStateException {
        throw new IllegalStateException("service unavailable");
    }
}
~~~

Đây là hai khai báo kiểu Java hợp lệ, có thể đặt trong một file riêng nếu không có quy định khác về tên public class. Phương thức của interface `Job.execute()` **không khai báo ngoại lệ nào**, trong khi phiên bản override:

- Được phép khai báo `throws IllegalStateException`.
- Được phép thực sự ném `IllegalStateException`.
- Không buộc caller viết `try/catch` hoặc `throws` chỉ vì loại ngoại lệ này.
- Vẫn có thể làm lời gọi **kết thúc bất thường tại runtime** nếu exception không được xử lý.

Lý do: `IllegalStateException extends RuntimeException`, thuộc nhóm **unchecked**.

**Đối chiếu:** Nếu đổi `throws IllegalStateException` ở trên thành `throws IOException`, trình biên dịch sẽ **từ chối**, vì `IOException` là checked exception mới mà hợp đồng phương thức `Job.execute()` không cho phép.

Điều quan trọng cho thiết kế là **Java cho phép ở compile time không đồng nghĩa với thay đổi hành vi luôn hợp lý**. Một subclass có thể ném unchecked exception mới nhưng vẫn phải tôn trọng những điều kiện và bảo đảm đã cam kết cho caller trong hợp đồng lớp cha/interface.

## 2. Hiểu đúng quan hệ giữa checked, unchecked và throws

### 2.1. Phân cấp các lớp ngoại lệ

~~~text
java.lang.Throwable
├── java.lang.Error
│   └── Các lớp con Error                       ← unchecked
└── java.lang.Exception
    ├── java.io.IOException                     ← checked
    │   └── java.io.FileNotFoundException       ← checked
    └── java.lang.RuntimeException              ← unchecked
        ├── java.lang.IllegalArgumentException
        ├── java.lang.IllegalStateException
        ├── java.lang.NullPointerException
        └── java.io.UncheckedIOException
~~~

*Ghi chú:* Sơ đồ chỉ thể hiện một phần phân cấp Java chuẩn, không phải liệt kê mọi lớp con.

**Checked exceptions** thường là các lớp con của `Exception` nhưng không thuộc nhánh `RuntimeException`. Compiler yêu cầu các checked exception có khả năng thoát ra từ một phương thức phải được **bắt hoặc khai báo** đúng điều kiện của ngôn ngữ.

**Unchecked exceptions** gồm `RuntimeException` và các lớp con, cùng `Error` và các lớp con. Java **không yêu cầu** khai báo `throws` hoặc bắt những ngoại lệ này để mã biên dịch.

Vì vậy:

~~~java
void run() {
    throw new IllegalStateException("bad state");
}
~~~

là phương thức hợp lệ dù không có mệnh đề `throws`. Ngược lại:

~~~java
// CỐ Ý KHÔNG BIÊN DỊCH:
void read() {
    throw new java.io.IOException("read failed");
}
~~~

sẽ bị compiler từ chối vì `IOException` chưa được bắt hoặc khai báo. Dòng minh họa sai không thuộc chương trình runnable trong bài.

### 2.2. throws không phải danh sách mọi lỗi có thể xảy ra

Mệnh đề `throws` có hai vai trò:

1. Đáp ứng quy tắc compile-time đối với **checked exception**.
2. Truyền đạt thông tin API cho người đọc và công cụ khi kiểu ngoại lệ được khai báo.

Tuy nhiên, `throws` **không liệt kê hết mọi unchecked exception có thể xảy ra**. Phương thức có thể ném `NullPointerException`, `IllegalArgumentException` hoặc `IllegalStateException` ngay cả khi chữ ký không đề cập chúng.

Có thể khai báo unchecked exception tường minh:

~~~java
void execute() throws IllegalStateException {
    // ...
}
~~~

nhưng việc khai báo đó **không biến nó thành checked** và cũng không làm caller bắt buộc phải catch. Trong JavaDoc, chú thích `@throws IllegalStateException` thường giúp ghi rõ **trong điều kiện nào** method có thể ném lỗi.

## 3. Quy tắc override về throws: nhìn từ phương thức cha

Theo [Java Language Specification Java 17 mục 8.4.8.3](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.8.3), khi ghi đè một phương thức, tập **checked exception** được phép khai báo không thể tự do mở rộng so với phương thức cha.

### 3.1. Phương thức cha không khai báo checked exception

~~~java
class Parent {
    void save() {
        System.out.println("saved");
    }
}

class Child extends Parent {
    @Override
    void save() throws IllegalStateException {
        throw new IllegalStateException("storage unavailable");
    }
}
~~~

**Hợp lệ:** `IllegalStateException` là unchecked.

Ví dụ sau **cố ý sai**:

~~~java
class Parent {
    void save() { }
}

class Child extends Parent {
    @Override
    void save() throws java.io.IOException { } // Lỗi biên dịch.
}
~~~

Trong trường hợp thứ hai, `Child.save()` thêm một checked exception mới mà chữ ký ở `Parent.save()` không cho phép. Compiler thường báo đại ý *overridden method does not throw IOException*.

Ngay cả khi thân phương thức con đang rỗng và thực tế chưa ném IOException, riêng **khai báo `throws IOException` không hợp lệ** đã đủ gây lỗi compile-time.

### 3.2. Phương thức cha khai báo một checked exception

~~~java
import java.io.FileNotFoundException;
import java.io.IOException;

class Parent {
    void read() throws IOException { }
}

class Child extends Parent {
    @Override
    void read() throws FileNotFoundException {
        throw new FileNotFoundException("missing.txt");
    }
}
~~~

**Hợp lệ:** `FileNotFoundException extends IOException`, nên đây là **thu hẹp** loại checked exception được công khai bởi override.

Các lựa chọn hợp lệ khác là giữ `throws IOException` hoặc **không khai báo checked exception nào**, nếu phần thân xử lý được mọi checked exception liên quan.

Ngược lại:

~~~java
class Parent {
    void read() throws java.io.IOException { }
}

class Child extends Parent {
    @Override
    void read() throws Exception { } // CỐ Ý SAI.
}
~~~

**Không hợp lệ:** `Exception` rộng hơn `IOException` nên không thể dùng nó để mở rộng nghĩa vụ của caller trong override.

### 3.3. Unchecked exception không bị giới hạn theo cùng quy tắc

Giả sử phương thức cha khai báo `IOException`:

~~~java
class Parent {
    void fetch() throws java.io.IOException { }
}

class Child extends Parent {
    @Override
    void fetch() throws java.io.FileNotFoundException,
            IllegalStateException {
        throw new IllegalStateException("temporarily unavailable");
    }
}
~~~

Mã **hợp lệ**:

- `FileNotFoundException` là checked subtype của `IOException`, nên được phép.
- `IllegalStateException` là unchecked, nên không phải chịu giới hạn về kiểu checked của phương thức cha.

Mệnh đề `throws` có thể liệt kê cả checked và unchecked exception, nhưng chúng được kiểm tra khác nhau.

**Tóm tắt nhanh:**

| Chữ ký phương thức cha | Chữ ký override | Hợp lệ? | Lý do |
| --- | --- | --- | --- |
| Không `throws` | Không `throws` | Có | Không thêm nghĩa vụ checked |
| Không `throws` | `throws IllegalStateException` | Có | Unchecked được phép |
| Không `throws` | `throws IOException` | Không | Thêm checked exception |
| `throws IOException` | `throws FileNotFoundException` | Có | Checked exception được thu hẹp |
| `throws IOException` | `throws IOException` | Có | Giữ loại checked đã cho phép |
| `throws IOException` | Không `throws` | Có | Loại bỏ khai báo checked |
| `throws IOException` | `throws Exception` | Không | Mở rộng checked exception |
| `throws IOException` | `throws IllegalStateException` | Có | Không còn checked, thêm unchecked |

Bảng xét các ví dụ đơn giản không dùng generic exception type parameter; trong Java có thêm các quy tắc kỹ thuật về erasure khi override phương thức generic. Đừng suy rộng thành quy tắc cho mọi chữ ký phức tạp mà bỏ qua JLS.

## 4. Có nhất thiết viết throws cho unchecked trong override?

**Không.** Hai cách sau đều hợp lệ:

~~~java
class ServiceA {
    void process() {
        throw new IllegalStateException("not ready");
    }
}

class ServiceB extends ServiceA {
    @Override
    void process() {
        throw new IllegalArgumentException("invalid operation");
    }
}
~~~

Và:

~~~java
class ServiceB extends ServiceA {
    @Override
    void process() throws IllegalArgumentException {
        throw new IllegalArgumentException("invalid operation");
    }
}
~~~

Hai khối code **chỉ minh họa các phương án riêng**, không đặt đồng thời hai khai báo trùng tên `ServiceB` trong cùng một file. Dù có viết `throws IllegalArgumentException`, caller vẫn không bắt buộc catch vì kiểu đó unchecked.

**Vì sao đôi khi vẫn khai báo?** Nó có thể giúp người đọc nhận ra điều kiện lỗi được dự kiến trong hợp đồng. Nhưng cách trình bày tốt hơn thường là JavaDoc mô tả **điều kiện phát sinh**, vì tên loại ngoại lệ một mình không nói rõ cách sử dụng API:

~~~java
/**
 * @throws IllegalArgumentException if the input is blank
 * @throws IllegalStateException if the service is unavailable
 */
@Override
public String execute(String input) {
    // ...
    return input;
}
~~~

Đây là đoạn minh họa thuộc một class có phương thức cha phù hợp, không phải một file độc lập.

Điều này đặc biệt quan trọng với interface mà phương thức chưa có khai báo unchecked exception: triển khai có thể ném những lỗi phù hợp với hợp đồng mà không phải làm mọi caller đổi chữ ký.

## 5. Chương trình Java 17 hoàn chỉnh: override, đa hình và checked/unchecked

Lưu tệp **`OverrideUncheckedDemo.java`**. Ví dụ gồm ba phần:

1. Interface `Job` không khai báo checked exception; subclass `RemoteJob` override và khai báo `throws IllegalStateException`.
2. Interface `FileTask` khai báo `IOException`; implementation có thể thu hẹp thành `FileNotFoundException` hoặc bỏ khai báo checked hoàn toàn.
3. Abstract class `Gateway` không khai báo checked exception; implementation bắt `IOException` từ thao tác kỹ thuật rồi **bọc có cause** thành `UncheckedIOException`.

~~~java
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;

public class OverrideUncheckedDemo {
    interface Job {
        /** Input must be non-null and non-blank. */
        String execute(String input);
    }

    static class BaseJob implements Job {
        @Override
        public String execute(String input) {
            validate(input);
            return "base:" + input;
        }
    }

    static final class RemoteJob extends BaseJob {
        @Override
        public String execute(String input) throws IllegalStateException {
            validate(input);
            if ("busy".equals(input)) {
                throw new IllegalStateException("service unavailable");
            }
            return "remote:" + input;
        }
    }

    private static void validate(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input required");
        }
    }

    interface FileTask {
        void open() throws IOException;
    }

    static class MissingFileTask implements FileTask {
        @Override
        public void open() throws FileNotFoundException {
            throw new FileNotFoundException("missing.txt");
        }
    }

    static class WorkingFileTask implements FileTask {
        @Override
        public void open() {
            System.out.println("open=ok");
        }
    }

    static abstract class Gateway {
        abstract String load();
    }

    static final class CheckedToUncheckedGateway extends Gateway {
        @Override
        String load() {
            try {
                return fetch();
            } catch (IOException ex) {
                throw new UncheckedIOException("cannot load data", ex);
            }
        }

        private String fetch() throws IOException {
            throw new IOException("upstream offline");
        }
    }

    public static void main(String[] args) {
        System.out.println("== unchecked through interface ==");
        Job job = new RemoteJob();
        System.out.println("result=" + job.execute("ok"));

        try {
            job.execute("busy");
        } catch (IllegalStateException ex) {
            System.out.println("caught=" + ex.getClass().getSimpleName()
                    + ": " + ex.getMessage());
        }

        try {
            job.execute("");
        } catch (IllegalArgumentException ex) {
            System.out.println("input=" + ex.getMessage());
        }

        System.out.println("== checked narrowing ==");
        FileTask missing = new MissingFileTask();
        try {
            missing.open();
        } catch (IOException ex) {
            System.out.println("checked=" + ex.getClass().getSimpleName()
                    + ": " + ex.getMessage());
        }

        FileTask working = new WorkingFileTask();
        try {
            working.open();
        } catch (IOException ex) {
            System.out.println("unexpected=" + ex.getMessage());
        }

        System.out.println("== checked wrapped as unchecked ==");
        Gateway gateway = new CheckedToUncheckedGateway();
        try {
            gateway.load();
        } catch (UncheckedIOException ex) {
            System.out.println("wrapper=" + ex.getClass().getSimpleName());
            System.out.println("cause=" + ex.getCause().getClass().getSimpleName()
                    + ": " + ex.getCause().getMessage());
        }
    }
}
~~~

### Biên dịch và chạy

Sử dụng JDK 17 trở lên:

~~~bash
javac --release 17 -encoding UTF-8 OverrideUncheckedDemo.java
java OverrideUncheckedDemo
~~~

**Stdout chính xác đã được biên dịch và chạy kiểm chứng:**

~~~text
== unchecked through interface ==
result=remote:ok
caught=IllegalStateException: service unavailable
input=input required
== checked narrowing ==
checked=FileNotFoundException: missing.txt
open=ok
== checked wrapped as unchecked ==
wrapper=UncheckedIOException
cause=IOException: upstream offline
~~~

Chương trình dùng `IOException` được tạo có chủ đích, không phụ thuộc file ngoài hoặc mạng, nên thông điệp lỗi và stdout ổn định. Mỗi ngoại lệ dự kiến được bắt ở đúng boundary của phần minh họa.

## 6. Đọc kỹ chương trình: phần unchecked của Job

### 6.1. Interface không có throws

~~~java
interface Job {
    String execute(String input);
}
~~~

Ở compile time, code dùng biến kiểu `Job` thấy chữ ký **không khai báo checked exception**. Vì vậy caller không buộc viết try/catch nếu chỉ gọi `job.execute("ok")`.

Tuy vậy một implementation vẫn có thể ném unchecked exception. Điều đó không thay đổi kiểu trả về và chữ ký lời gọi ở phía compiler.

### 6.2. Subclass override và khai báo unchecked

~~~java
static final class RemoteJob extends BaseJob {
    @Override
    public String execute(String input) throws IllegalStateException {
        validate(input);
        if ("busy".equals(input)) {
            throw new IllegalStateException("service unavailable");
        }
        return "remote:" + input;
    }
}
~~~

Lớp `RemoteJob` vừa ghi đè implementation của `BaseJob`, vừa thỏa interface `Job`. Từ khóa `@Override` yêu cầu compiler kiểm tra đây thật sự là override, tránh một số lỗi gõ nhầm tên/tham số.

Đoạn `throws IllegalStateException` **không vi phạm** hợp đồng checked exception của cả lớp cha và interface. Nó cũng không đòi hỏi caller khai báo `throws IllegalStateException`.

Nhưng khi `input="busy"`, lời gọi ném `IllegalStateException` và sẽ lan đến caller nếu không được bắt.

### 6.3. Đa hình: kiểu biến khác kiểu object runtime

~~~java
Job job = new RemoteJob();
job.execute("busy");
~~~

Biểu thức gọi dựa trên **kiểu tĩnh `Job`** để compiler kiểm tra chữ ký và checked exceptions. Lúc chạy, Java dùng **dynamic dispatch** để chọn implementation `RemoteJob.execute` trên đối tượng thực tế.

Do đó method được thực thi có thể ném `IllegalStateException`, mặc dù kiểu interface không khai báo `throws`. Runtime không cấm việc này chỉ vì caller nhìn thấy method qua kiểu cha.

Nói cách khác:

- **Kiểu tĩnh/chữ ký**: quyết định các nghĩa vụ compile-time về checked exception.
- **Implementation runtime**: quyết định mã nào chạy và exception thực tế được ném.
- **Catch**: nếu có, chọn handler phù hợp theo kiểu runtime của exception.

Đây là lý do các giao diện callback và framework có thể để unchecked exception lan xuyên qua nhiều tầng không khai báo `throws`.

### 6.4. Unchecked exception khác nhau vẫn có thể biểu diễn hai lỗi khác nhau

Trong `RemoteJob`, `validate()` có thể ném `IllegalArgumentException` khi input null hoặc blank. Khi input là `busy`, implementation ném `IllegalStateException`.

Cả hai đều unchecked, nhưng ý nghĩa khác:

- `IllegalArgumentException`: tham số không đáp ứng tiền điều kiện của API.
- `IllegalStateException`: đối tượng/dịch vụ không ở trạng thái cho phép hoàn tất thao tác.

Caller có thể bắt riêng từng kiểu khi thực sự có cách khắc phục phù hợp. Không nên coi mọi `RuntimeException` là lỗi nhập liệu để “bắt cho hết”.

## 7. Đọc kỹ phần checked narrowing

Trong chương trình, interface định nghĩa:

~~~java
interface FileTask {
    void open() throws IOException;
}
~~~

Implementation thứ nhất:

~~~java
class MissingFileTask implements FileTask {
    @Override
    public void open() throws FileNotFoundException {
        throw new FileNotFoundException("missing.txt");
    }
}
~~~

Đây là **thu hẹp checked exception hợp lệ**. Caller giữ reference kiểu interface:

~~~java
FileTask missing = new MissingFileTask();
try {
    missing.open();
} catch (IOException ex) {
    System.out.println(ex.getClass().getSimpleName());
}
~~~

Nó phải xử lý hoặc khai báo `IOException` theo **kiểu tĩnh `FileTask`**, dù implementation runtime ném subtype `FileNotFoundException`.

Implementation thứ hai:

~~~java
class WorkingFileTask implements FileTask {
    @Override
    public void open() {
        System.out.println("open=ok");
    }
}
~~~

được phép **bỏ hẳn khai báo checked exception** trong override. Nhưng trong chương trình chính:

~~~java
FileTask working = new WorkingFileTask();
try {
    working.open();
} catch (IOException ex) {
    System.out.println("unexpected=" + ex.getMessage());
}
~~~

catch `IOException` vẫn hợp lệ và hợp lý xét từ **chữ ký kiểu interface**.

Nếu thay kiểu biến thành đúng `WorkingFileTask` thì compiler biết chính implementation đó không khai báo checked IOException. Viết một catch `IOException` trực tiếp quanh lời gọi vốn không thể ném checked IOException có thể bị báo lỗi *exception is never thrown in body of corresponding try statement*:

~~~java
// CỐ Ý KHÔNG BIÊN DỊCH:
WorkingFileTask working = new WorkingFileTask();
try {
    working.open();
} catch (IOException ex) {
    System.out.println("unreachable for this static call");
}
~~~

Sự khác biệt ở đây là **kiểu tĩnh của biểu thức gọi**, không phải Java “phán đoán trước” kết quả thực thi của implementation.

## 8. Khi override cần gọi mã ném checked exception nhưng cha không cho phép

Một implementation có thể phải gọi API I/O có khai báo `throws IOException`, trong khi phương thức cha/interface **không cho phép thêm checked exception**. Có hai hướng chính:

1. **Xử lý và phục hồi tại chỗ** nếu có cách hợp lệ.
2. **Chuyển sang unchecked exception có ý nghĩa, giữ nguyên cause**, nếu lỗi cần được truyền tới tầng khác và hợp đồng phương thức không cho checked exception thoát ra.

Ví dụ trong chương trình chính:

~~~java
static final class CheckedToUncheckedGateway extends Gateway {
    @Override
    String load() {
        try {
            return fetch();
        } catch (IOException ex) {
            throw new UncheckedIOException("cannot load data", ex);
        }
    }

    private String fetch() throws IOException {
        throw new IOException("upstream offline");
    }
}
~~~

`Gateway.load()` không khai báo checked exception. `CheckedToUncheckedGateway.load()` vẫn override hợp lệ vì nó **bắt** IOException trong thân method, rồi ném `UncheckedIOException`.

`UncheckedIOException` là lớp chuẩn được cung cấp từ Java 8, kế thừa `RuntimeException` và thiết kế để mang nguyên nhân là `IOException`. Đoạn:

~~~java
throw new UncheckedIOException("cannot load data", ex);
~~~

giữ thông tin nguồn qua `getCause()`; nó **không xóa lỗi gốc** để thay bằng một message thiếu ngữ cảnh.

**Không phải mọi checked exception đều nên gói thành `UncheckedIOException`**. Lớp này chuyên cho `IOException`. Với loại checked khác, chọn một unchecked exception có ý nghĩa với hợp đồng API, ví dụ `IllegalStateException("context", cause)` hoặc custom runtime exception phù hợp.

**Không dùng việc wrap để né thiết kế:** nếu caller bắt buộc phải giải quyết một lỗi nghiệp vụ dự kiến, chỉ bọc thành unchecked để compiler im lặng có thể khiến caller quên xử lý. Phải xác định rõ loại lỗi có phục hồi được hay không trước khi quyết định.

## 9. Có thể ném unchecked exception mới, nhưng phải giữ hợp đồng hành vi

Ở mức ngôn ngữ, subclass được phép khai báo/ném unchecked exception mới. Ở mức thiết kế, **subclass vẫn phải có khả năng thay thế superclass/interface trong những bối cảnh hợp lệ** (*substitutability*).

Ví dụ API:

~~~java
class Formatter {
    /** Chấp nhận mọi String khác null, kể cả String dài 1 ký tự. */
    String format(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value required");
        }
        return "ok:" + value;
    }
}
~~~

Một override không nên tự ý nâng điều kiện tối thiểu thành “phải có ít nhất 3 ký tự”:

~~~java
class StrictFormatter extends Formatter {
    @Override
    String format(String value) {
        if (value.length() < 3) {
            throw new IllegalArgumentException("too short");
        }
        return super.format(value);
    }
}
~~~

Hai class trên là **đoạn minh họa**, chưa có `main`; chúng có thể biên dịch nhưng override có **vấn đề về hợp đồng**:

- Theo hợp đồng của `Formatter`, giá trị `"A"` là input hợp lệ.
- Với `StrictFormatter`, giá trị `"A"` lại gây unchecked `IllegalArgumentException`.
- Caller dùng reference kiểu `Formatter` có thể bất ngờ thất bại với một input trước đó được bảo đảm hợp lệ.
- Override còn thiếu kiểm tra null nhất quán: với null, biểu thức `value.length()` gây NPE thay vì cùng IllegalArgumentException như parent.

**Compiler vẫn chấp nhận**, vì đây không phải lỗi về quy tắc checked exception hoặc chữ ký override. Nhưng thiết kế có thể vi phạm hành vi mà API đã cam kết.

Giải pháp:

- Không **tăng tiền điều kiện** đối với những input mà hợp đồng lớp cha đã hứa chấp nhận.
- Không **giảm hậu điều kiện** hoặc phá invariant mà caller được quyền dựa vào.
- Ghi rõ các unchecked exception **hợp lệ** theo API (ví dụ input null, trạng thái service không sẵn sàng).
- Với biến thể thực sự có hợp đồng khác, xem xét **API/method/type riêng**, thay vì ép override làm hành vi khó đoán.

Đây là tinh thần áp dụng **Liskov Substitution Principle**, dù JLS chỉ cưỡng chế một phần các ràng buộc đó.

## 10. Hợp đồng ngoại lệ của interface cần mô tả hành vi kỳ vọng

Một interface có thể không khai báo checked exception nhưng vẫn cần JavaDoc mô tả lỗi unchecked:

~~~java
interface Job {
    /**
     * Thực hiện tác vụ với chuỗi đầu vào hợp lệ.
     *
     * @param input chuỗi khác null và không được blank
     * @return kết quả xử lý
     * @throws IllegalArgumentException nếu input không hợp lệ
     * @throws IllegalStateException nếu dịch vụ chưa sẵn sàng
     */
    String execute(String input);
}
~~~

Đây là một interface hợp lệ để minh họa hợp đồng. Một implementation nên tuân những điều kiện đã cam kết, dù nó có khai báo `throws IllegalStateException` trong mã hay không.

Khi viết override, các trường hợp sau cần xem xét kỹ:

| Hành vi override | Compiler có cho phép? | Tương thích hợp đồng? |
| --- | --- | --- |
| Ném `IllegalArgumentException` khi input null và contract cấm null | Có | Thường có |
| Ném `IllegalStateException` khi service chưa sẵn sàng như contract mô tả | Có | Thường có |
| Ném `IllegalArgumentException` với input mà contract đã bảo đảm hợp lệ | Có | Có thể vi phạm |
| Ném unchecked từ bug nội bộ chưa được xử lý | Có thể biên dịch | Không nên coi là kết quả nghiệp vụ được bảo đảm |
| Thêm `throws IOException` vào override của method không có checked throws | Không | Không có kết quả runtime vì không biên dịch |
| Bắt `IOException` rồi bọc thành `UncheckedIOException` có cause | Có | Phụ thuộc hợp đồng và chính sách phục hồi |

Không cần liệt kê mọi NullPointerException có thể do bug vào JavaDoc; nên ưu tiên điều kiện lỗi được **dự kiến và có ý nghĩa đối với caller**.

## 11. Thứ tự catch khi xử lý unchecked exception

Một lệnh gọi qua biến cha vẫn có thể ném các runtime subtype khác nhau. Nếu chọn bắt chúng, Java yêu cầu đặt catch **cụ thể trước tổng quát**:

~~~java
try {
    job.execute(input);
} catch (IllegalArgumentException ex) {
    handleInvalidInput(ex);
} catch (IllegalStateException ex) {
    handleUnavailable(ex);
} catch (RuntimeException ex) {
    reportUnexpected(ex);
}
~~~

Đây là cấu trúc minh họa với các hàm handler cần định nghĩa. `IllegalArgumentException` và `IllegalStateException` là hai subtype ngang hàng, không cần thứ tự kế thừa giữa chúng; nhưng cả hai phải đứng trước `RuntimeException` nếu xuất hiện ở cùng chuỗi catch.

Viết ngược:

~~~java
// CỐ Ý KHÔNG BIÊN DỊCH:
try {
    job.execute(input);
} catch (RuntimeException ex) {
    reportUnexpected(ex);
} catch (IllegalStateException ex) {
    handleUnavailable(ex);
}
~~~

làm catch thứ hai bị che vì `RuntimeException` đã bắt mọi `IllegalStateException`.

**Không nên bắt `RuntimeException` ở mọi tầng rồi cho chạy tiếp**. Bắt rộng đôi khi phù hợp ở ranh giới tác vụ/web request để log và chuyển thành lỗi hệ thống, nhưng cần chính sách rõ để tránh che bug.

## 12. Checked exception có thể vẫn đến từ reference kiểu cha

Điều này là khác biệt dễ nhầm giữa Java compiler và runtime:

~~~java
interface ReaderTask {
    String read() throws IOException;
}

class ConstantReader implements ReaderTask {
    @Override
    public String read() {
        return "fixed data";
    }
}
~~~

Đây là khai báo minh họa, cần import `IOException`.

- Biến kiểu `ReaderTask` khi gọi `read()` vẫn có hợp đồng compile-time `throws IOException`.
- Biến kiểu `ConstantReader` gọi chính override không có `throws IOException`, nên không phải khai báo checked IOException chỉ vì interface có khai báo.
- Nếu caller gặp một implementation khác ném `IOException` hợp lệ, việc bắt/xử lý exception sẽ dựa trên đối tượng thực tế tại runtime.

Như vậy **override được phép thu hẹp checked throws** và **unchecked có thể phát sinh ngoài danh sách checked**. Hai quy tắc không mâu thuẫn nhau.

## 13. Khi một class triển khai nhiều interface có throws khác nhau

Giả sử hai interface có method trùng chữ ký:

~~~java
interface LocalAction {
    void execute() throws java.io.IOException;
}

interface DatabaseAction {
    void execute() throws java.sql.SQLException;
}
~~~

Một class triển khai cả hai phải có implementation override hợp lệ đối với **cả hai hợp đồng**. Chẳng hạn:

~~~java
class UnifiedAction implements LocalAction, DatabaseAction {
    @Override
    public void execute() throws IllegalStateException {
        throw new IllegalStateException("not ready");
    }
}
~~~

Đoạn này **hợp lệ**: implementation không khai báo checked exception nào và chỉ khai báo unchecked `IllegalStateException`. Vì một loại checked exception chung phải tương thích với cả hai phương thức được kế thừa, không thể tùy ý thêm `throws IOException` hoặc `throws SQLException` vào override chung khi chỉ một phía khai báo loại tương ứng.

Đây là trường hợp mở rộng để thấy vì sao **không có checked throws** thường dễ triển khai nhiều interface có hợp đồng khác nhau; nhưng unchecked exception vẫn cần tôn trọng các bảo đảm hành vi của **từng** interface.

Nếu một class bị buộc thực hiện thao tác I/O ném checked exception, nó vẫn có thể xử lý tại chỗ hoặc bọc exception sau khi cân nhắc thiết kế.

## 14. Ngoại lệ Error có phải cũng được phép trong override?

Về định nghĩa ngôn ngữ, `Error` và các lớp con cũng là **unchecked exception**. Compiler không yêu cầu khai báo `throws Error` và không giới hạn chúng theo quy tắc checked throws của override.

Tuy nhiên, **không nên sử dụng `Error` để đại diện lỗi nghiệp vụ** hoặc dùng nó như cách né compiler. Các lớp `Error` thường dành cho tình huống nghiêm trọng như lỗi liên kết class hoặc lỗi về tài nguyên ở mức JVM; ứng dụng bình thường không nên thiết kế luồng phục hồi chung quanh chúng.

Nếu cần biểu diễn một điều kiện lỗi ứng dụng unchecked, thường dùng subtype của `RuntimeException` với thông điệp và cause thích hợp.

## 15. Phân biệt các khái niệm dễ lẫn khi ghi đè

### 15.1. Override khác overload

~~~java
class Parent {
    void work(String value) { }
}

class Child extends Parent {
    @Override
    void work(String value) throws IllegalStateException { }

    void work(int value) throws java.io.IOException { }
}
~~~

Đây là các class minh họa **có thể biên dịch**, trong đó:

- `work(String)` ở Child **override** phương thức của Parent, nên áp dụng giới hạn checked throws của phương thức cha.
- `work(int)` ở Child là một **overload khác chữ ký**, không override Parent.work(String). Nó có thể khai báo `IOException` vì không bị ràng buộc bởi throws của Parent.work(String), nhưng caller phải xử lý checked IOException nếu gọi overload này.

`@Override` giúp kiểm tra nhầm lẫn. Nếu gắn `@Override` vào `work(int)` trong ví dụ này, compiler sẽ báo lỗi vì method đó không override method nào tương ứng.

### 15.2. Không phải mọi phương thức trùng tên đều là override

- Phương thức `static` cùng chữ ký ở lớp con là **method hiding**, không phải override động.
- Phương thức `private` ở lớp cha không được kế thừa như một method override.
- Phương thức `final` không được phép override.
- Constructor không được override.

Các trường hợp này tuân những quy tắc ngôn ngữ khác nhau; đừng áp dụng máy móc lập luận về overriding exceptions cho mọi method cùng tên.

### 15.3. Chữ ký throws không quyết định catch runtime

Một phương thức có thể khai báo `throws IllegalStateException` nhưng thực tế ném `IllegalArgumentException` hoặc không ném gì cả. Catch phù hợp tại runtime dựa trên **kiểu đối tượng Throwable thực sự được ném**, không đơn giản là tên kiểu trong mệnh đề `throws`.

Vì thế `throws RuntimeException` không phải một "cửa chắn" đảm bảo chỉ RuntimeException mới có thể phát sinh trong mọi hoàn cảnh; các lỗi `Error` cũng có thể xảy ra, và checked exception chỉ có thể lan theo quy tắc hợp lệ của ngôn ngữ hoặc cơ chế ngoài thông thường.

## 16. Cách review một override có exception

Khi thấy một class con ghi đè method, kiểm tra theo thứ tự:

1. **Xác định đúng method cha/interface** mà chữ ký đang override; có `@Override` không?
2. **Ghi lại checked throws của method cha**, phân biệt với unchecked throws.
3. **Phân loại từng loại trong throws của override:** checked hay unchecked?
4. **Nếu checked**, kiểm tra nó có hợp lệ theo hợp đồng cha không; không thêm checked type rộng hơn hoặc mới.
5. **Nếu unchecked**, nhớ compile-time cho phép nhưng phải đọc JavaDoc/tiền điều kiện/hậu điều kiện để xác định tính tương thích hành vi.
6. **Đọc phần thân:** có gọi API checked exception không? Nếu có, đã catch/declare hợp lệ chưa? Có bọc thành unchecked và giữ cause không?
7. **Xem kiểu tĩnh của caller:** gọi qua interface hoặc qua implementation dẫn đến phân tích checked exception khác nhau.
8. **Kiểm thử bằng runtime polymorphism:** biến cha trỏ đến instance con, có bắt đúng ngoại lệ và giữ được hành vi cam kết không?
9. **Kiểm tra ranh giới catch:** không catch quá rộng và bỏ qua lỗi lập trình; ghi log exception object thay vì chỉ message.
10. **Kiểm tra chính sách chuyển đổi lỗi:** không gói một checked exception nghiệp vụ cần phục hồi thành RuntimeException chỉ để né `throws`.

Khi mọi bước đã hợp lệ, một override có thể khai báo thêm unchecked exception về mặt ngôn ngữ và vẫn duy trì một hợp đồng API dễ sử dụng.

## 17. Những hiểu nhầm thường gặp

- **“Method cha không có throws nên method con không thể ném bất cứ exception nào.”** Sai: unchecked exception vẫn có thể được ném.
- **“Method con có throws RuntimeException thì caller buộc phải catch.”** Sai: unchecked không tạo nghĩa vụ compile-time.
- **“Chỉ RuntimeException mới unchecked.”** Chưa đủ: `Error` và các lớp con cũng unchecked.
- **“Method con được mở rộng checked throws như overload bình thường.”** Sai: override có ràng buộc checked throws theo method cha.
- **“Base throws IOException thì child có thể throws Exception.”** Sai: mở rộng checked throws không hợp lệ.
- **“Child throws FileNotFoundException thay IOException là sai.”** Sai: FileNotFoundException là subtype của IOException.
- **“Interface không khai báo exception thì implementation không thể ném IllegalStateException.”** Sai: đó là unchecked exception.
- **“Đặt biến kiểu interface rồi gọi implementation không thay đổi xử lý lỗi.”** Cần phân biệt kiểm tra compile-time qua kiểu tĩnh với dynamic dispatch runtime.
- **“Nếu implementation bỏ throws IOException, caller qua interface không cần xử lý IOException.”** Sai: lời gọi qua kiểu interface vẫn theo checked signature của interface.
- **“Bọc IOException thành UncheckedIOException là mất nguyên nhân gốc.”** Sai nếu dùng constructor nhận cause; hãy kiểm tra `getCause()`.
- **“Bọc checked thành unchecked luôn là quyết định tốt.”** Sai: phụ thuộc hợp đồng và khả năng phục hồi của caller.
- **“Compiler cho phép một unchecked exception mới nên luôn tương thích LSP.”** Sai: override có thể tăng tiền điều kiện hoặc phá bảo đảm hành vi.
- **“Catch RuntimeException trước IllegalStateException vẫn ổn.”** Sai: handler cụ thể bị che, không biên dịch.
- **“throws IllegalStateException có nghĩa method chỉ ném loại đó.”** Sai: mệnh đề throws không mô tả hết mọi lỗi runtime.
- **“Đổi input contract trong subclass cũng chỉ là chi tiết implementation.”** Sai nếu khiến caller hợp lệ của superclass không còn hoạt động đúng.
- **“@Override ảnh hưởng exception runtime.”** Không: annotation này giúp compiler xác thực ghi đè; cơ chế throw/catch runtime không đổi.

## 18. Câu hỏi tự luyện

**Câu 1.** Interface `Action { void run(); }`; implementation override khai báo `throws IllegalArgumentException`. Có biên dịch được không?

*Đáp án:* Có. IllegalArgumentException là unchecked. Caller không buộc catch do khai báo đó.

**Câu 2.** Đổi khai báo của implementation sang `throws IOException` thì sao?

*Đáp án:* Không biên dịch nếu interface/base không cho phép checked IOException ở method bị override.

**Câu 3.** Base method khai báo `throws IOException`. Child override khai báo `throws FileNotFoundException` có hợp lệ?

*Đáp án:* Có. FileNotFoundException là checked subtype của IOException, tức thu hẹp throws.

**Câu 4.** Base `throws IOException`, child `throws Exception` có hợp lệ?

*Đáp án:* Không. Exception là checked supertype rộng hơn IOException.

**Câu 5.** Vì sao biến `FileTask task = new WorkingFileTask();` vẫn cần handle IOException khi gọi `task.open()`?

*Đáp án:* Compiler kiểm tra lời gọi qua kiểu tĩnh FileTask, có khai báo throws IOException, dù implementation WorkingFileTask không khai báo.

**Câu 6.** Nếu override muốn gọi `Files.readString(path)` trong method cha không khai báo checked throws, phải làm gì?

*Đáp án:* Catch/handle IOException bên trong override, hoặc chuyển đổi thành unchecked phù hợp với hợp đồng và giữ cause. Không được chỉ thêm `throws IOException` trái quy tắc override.

**Câu 7.** Vì sao thêm unchecked IllegalArgumentException khi input `"A"` vốn được base cho phép lại có vấn đề?

*Đáp án:* Compiler cho phép nhưng hành vi override có thể làm mạnh thêm tiền điều kiện, vi phạm khả năng thay thế và hợp đồng caller.

**Câu 8.** Có cần ghi `throws IllegalStateException` để ném IllegalStateException không?

*Đáp án:* Không. Đó là khai báo tùy chọn, không làm exception trở thành checked.

**Câu 9.** Một class triển khai hai interface cùng method `execute()`, interface thứ nhất `throws IOException`, interface thứ hai `throws SQLException`, implementation không throws checked nhưng có `throws IllegalStateException`. Có hợp lệ?

*Đáp án:* Có. Unchecked exception không bị ràng buộc bởi checked throws của hai interface.

**Câu 10.** Nếu catch RuntimeException rồi bỏ qua exception để tiếp tục nghiệp vụ, có luôn an toàn?

*Đáp án:* Không. Nó có thể che bug hoặc một lỗi trạng thái nghiêm trọng. Catch rộng chỉ nên có ở ranh giới với chính sách xử lý rõ ràng.

## 19. Tài liệu chuẩn

- [JLS Java SE 17 mục 8.4.8.3 – Requirements in Overriding and Hiding](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.8.3): giới hạn checked exception khi method override.
- [JLS Java SE 17 mục 9.4.1.3 – Requirements in Overriding and Hiding](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.4.1.3): điều kiện override/implement interface methods.
- [JLS Java SE 17 mục 8.4.6 – Method Throws](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.6): ý nghĩa mệnh đề throws của method.
- [JLS Java SE 17 chương 11 – Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html): phân loại checked/unchecked và cơ chế kiểm tra exception.
- [Java SE 17 API – RuntimeException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/RuntimeException.html): nhánh unchecked cho lỗi thời gian chạy.
- [Java SE 17 API – UncheckedIOException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/UncheckedIOException.html): wrapper unchecked giữ nguyên nhân IOException.
- [Java SE 17 API – Throwable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): message, cause và stack trace.

**Ghi nhớ:** **Override được phép thêm khai báo hoặc ném unchecked exception**, ngay cả khi method cha/interface không có mệnh đề `throws`. **Checked exception phải tuân hợp đồng phương thức cha**, không được tự ý thêm hoặc mở rộng. Nhưng một override được compiler chấp nhận chưa chắc đúng thiết kế: hãy giữ tiền điều kiện/hậu điều kiện, tài liệu hóa lỗi dự kiến, bảo toàn cause khi wrap và xét cách caller dùng method qua kiểu cha.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-inheritance-005">Một phương thức thực thi nhiều interface có throws khác nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Một phương thức thực thi nhiều interface có `throws` khác nhau trong Java

> **Chủ đề:** Java Core → Exception → Kế thừa và hợp đồng. Một lớp Java có thể `implements` nhiều interface chứa **cùng một phương thức trừu tượng** (cùng tên và danh sách tham số) nhưng khai báo **checked exception khác nhau** trong mệnh đề `throws`. Khi đó, **một phương thức implementation phải tuân thủ hợp đồng của tất cả phương thức mà nó override**. Nó không được tùy tiện gộp các ngoại lệ thành `throws A, B` nếu một interface không cho phép loại checked tương ứng.

## 1. Vấn đề cốt lõi: một implementation, nhiều hợp đồng

Xem hai interface:

```java
interface FileAction {
    void execute() throws java.io.IOException;
}

interface DatabaseAction {
    void execute() throws java.sql.SQLException;
}
```

Cả hai khai báo cùng một phương thức `void execute()`, nhưng khác `throws`:

- `FileAction.execute()` cho phép checked `IOException`.
- `DatabaseAction.execute()` cho phép checked `SQLException`.
- Một lớp thực thi **cả hai** phải có `execute()` hợp lệ khi được gọi qua **bất kỳ interface nào**.

Đây là implementation hợp lệ:

```java
final class CombinedAction implements FileAction, DatabaseAction {
    @Override
    public void execute() {
        System.out.println("Hoàn tất");
    }
}
```

Vì implementation **không khai báo checked exception nào**, nó không làm người dùng của interface nào phải xử lý thêm loại checked exception nằm ngoài hợp đồng của interface đó.

Đây lại là implementation **không hợp lệ**:

```java
// CỐ Ý SAI, KHÔNG BIÊN DỊCH:
final class BadAction implements FileAction, DatabaseAction {
    @Override
    public void execute()
            throws java.io.IOException, java.sql.SQLException {
        // ...
    }
}
```

**Lý do:** người dùng `FileAction` chỉ được hứa khả năng checked `IOException`, không bị yêu cầu xử lý checked `SQLException`. Tương tự, người dùng `DatabaseAction` không được bất ngờ nhận `IOException` trong hợp đồng checked. Implementation phải thỏa **đồng thời**, không được lấy **hợp** hai danh sách `throws`.

### 1.1. Quy tắc ghi nhớ

**Với mỗi checked exception được khai báo bởi phương thức implementation, loại đó phải được cho phép bởi từng khai báo phương thức bị override.** “Được cho phép” nghĩa là nó thuộc kiểu checked được khai báo, hoặc là **subtype** của một kiểu checked mà interface tương ứng khai báo.

Với hai interface độc lập nêu trên, `IOException` và `SQLException` nằm ở hai nhánh lớp khác nhau. Không có lớp checked exception thông thường nào đồng thời là subtype của cả hai (Java chỉ cho một superclass trực tiếp), nên implementation **không được khai báo checked exception nào thuộc hai nhánh đó**.

**Ngoại lệ quan trọng:** quy tắc giới hạn này nói về **checked exception**. Method implementation vẫn có thể ném `RuntimeException`/`Error` và các subtype dù interface không khai báo; điều này đúng cú pháp nhưng không bảo đảm cách xử lý đó hợp lý về nghiệp vụ.

## 2. Phân biệt `throws` với `throw` để tránh hiểu sai

`throws` xuất hiện trong khai báo:

```java
void load() throws java.io.IOException;
```

Nó công bố những **kiểu checked exception có thể thoát khỏi method** và ảnh hưởng nghĩa vụ compile-time của caller.

`throw` là câu lệnh thực thi:

```java
throw new java.io.IOException("Đọc lỗi");
```

Nếu implementation bị hạn chế không được khai báo `throws IOException`, nó cũng **không thể để checked IOException thoát ra** từ thân method đó. Nó phải thực sự xử lý checked exception, hoặc đổi cấu trúc API. Bỏ từ khóa `throws` khỏi chữ ký **không tự xử lý** một lời gọi I/O ném IOException trong thân.

Cần giữ riêng ba câu hỏi:

1. Interface công bố những loại checked nào?
2. Implementation muốn để những loại checked nào thoát?
3. Caller có kiểu tham chiếu tĩnh là interface nào khi biên dịch?

Ba câu hỏi liên quan nhưng không đồng nhất. `throws` của một interface không quyết định ngoại lệ nào **chắc chắn** sẽ xuất hiện ở một lần chạy.

## 3. Bảng các trường hợp cơ bản

Giả sử cả hai interface có cùng `void run()`:

| Interface A | Interface B | `throws` hợp lệ ở implementation | Ghi chú |
| --- | --- | --- | --- |
| `throws IOException` | `throws SQLException` | Không có checked exception nào thuộc hai nhánh riêng | Hai loại không liên quan |
| `throws IOException` | `throws FileNotFoundException` | `throws FileNotFoundException` hoặc subtype của nó | `FileNotFoundException extends IOException` |
| `throws Exception` | `throws IOException` | `throws IOException` hoặc checked subtype phù hợp | `Exception` ở A bao phủ `IOException` |
| `throws IOException` | Không khai báo checked exception | Không khai báo checked exception | Interface B nghiêm ngặt hơn |
| `throws FileNotFoundException` | `throws EOFException` | Không có checked exception thuộc cả hai nhánh | Hai subtype khác nhau của `IOException` |
| `throws Exception` | `throws Exception` | `throws Exception` hoặc subtype checked phù hợp | Hai hợp đồng tương thích |
| Không khai báo checked exception | Không khai báo checked exception | Không khai báo checked exception | Unchecked exceptions vẫn có thể xảy ra |

Ở bảng này, “không khai báo checked exception” **không** có nghĩa method không bao giờ thất bại; nó chỉ nói về ràng buộc `throws` đối với checked exceptions.

### 3.1. Cách xét một loại exception cụ thể

Giả sử implementation muốn khai báo `throws FileNotFoundException`:

- Đối với interface A `throws IOException`: **hợp lệ**, vì `FileNotFoundException extends IOException`.
- Đối với interface B `throws FileNotFoundException`: **hợp lệ**, vì loại trùng khớp.
- Vì cả hai đều cho phép, implementation có thể khai báo `throws FileNotFoundException`.

Nếu implementation muốn khai báo `throws IOException` thì thất bại trước B: `IOException` **rộng hơn** `FileNotFoundException`, nghĩa là nó có thể bao gồm những lỗi B chưa hứa cho caller.

### 3.2. Vì sao không phải “lấy giao nguyên văn hai danh sách”?

Một cách nhớ ngắn là tập checked exception hợp lệ của implementation phải nằm trong **phần chung của các hợp đồng**, nhưng đừng hiểu đó là phép giao chuỗi tên:

```text
A: throws IOException
B: throws FileNotFoundException
```

Hai danh sách tên **không giống nhau**, nhưng implementation vẫn được khai báo `throws FileNotFoundException` bởi **quan hệ kế thừa**. Phải xét **tập các subtype được từng hợp đồng bao phủ**, chứ không so sánh hai văn bản `throws`.

## 4. Cơ chế override với nhiều interface

Trong Java, hai method instance có cùng tên và cùng signature tham số được một class thực thi bằng **một** implementation phù hợp nếu thỏa các quy tắc về kiểu trả về, khả năng truy cập và exception.

Ví dụ:

```java
interface First {
    String query() throws java.io.IOException;
}

interface Second {
    String query() throws java.sql.SQLException;
}

final class QueryService implements First, Second {
    @Override
    public String query() {
        return "OK";
    }
}
```

Đây là các khai báo kiểu hợp lệ, nhưng chưa có `main`. Một `QueryService.query()` thỏa cả hai:

- `public` là cần thiết vì các method của interface ở đây là `public`.
- `String` là kiểu trả về tương thích với cả hai khai báo.
- Không có checked `throws`, nên không vi phạm hợp đồng nào.
- `@Override` giúp compiler xác nhận đây là implementation của method được override.

**Đừng tạo hai phương thức phân biệt chỉ bằng `throws`:**

```java
// CỐ Ý SAI: không thể overload chỉ bằng throws.
public void query() throws java.io.IOException { }
public void query() throws java.sql.SQLException { }
```

`throws` không nằm trong **method signature dùng để phân biệt overload**. Hai khai báo trên có cùng tên/danh sách tham số, gây lỗi khai báo trùng phương thức. Muốn hai hành vi độc lập có checked exceptions khác nhau, cân nhắc **đổi tên hoặc tham số có ý nghĩa**, hoặc thiết kế hai adapter/interface khác nhau.

### 4.1. Cùng tên nhưng khác tham số thì không phải tình huống này

```java
interface ById {
    void load(int id) throws java.io.IOException;
}

interface ByName {
    void load(String name) throws java.sql.SQLException;
}
```

Đây là hai signature khác nhau: `load(int)` và `load(String)`. Một lớp có thể triển khai **hai method overload độc lập** với hai `throws` khác nhau, miễn mỗi method đáp ứng đúng hợp đồng của nó. Không cần ép cả hai danh sách `throws` vào cùng một implementation.

### 4.2. `@Override` không “gộp” các ngoại lệ

Annotation `@Override` chỉ yêu cầu compiler kiểm tra đúng việc override/implementation. Nó **không** cho phép bỏ qua quy tắc checked exception. Dù bỏ annotation, declaration không tương thích `throws` vẫn bị từ chối.

## 5. Ví dụ Java 17 hoàn chỉnh: hai interface có checked exception không liên quan

Chương trình này chứng minh một implementation không khai báo checked exception vẫn được gọi qua **hai interface công bố những kiểu checked khác nhau**.

Lưu thành **`DisjointThrowsDemo.java`**:

```java
import java.io.IOException;
import java.sql.SQLException;

public class DisjointThrowsDemo {
    interface FileAction {
        String execute() throws IOException;
    }

    interface DatabaseAction {
        String execute() throws SQLException;
    }

    static final class CombinedAction
            implements FileAction, DatabaseAction {
        @Override
        public String execute() {
            return "OK";
        }
    }

    static void fromFile(FileAction action) {
        try {
            System.out.println("File: " + action.execute());
        } catch (IOException e) {
            System.out.println("File lỗi: " + e.getMessage());
        }
    }

    static void fromDatabase(DatabaseAction action) {
        try {
            System.out.println("DB: " + action.execute());
        } catch (SQLException e) {
            System.out.println("DB lỗi: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        CombinedAction implementation = new CombinedAction();

        System.out.println("Impl: " + implementation.execute());
        fromFile(implementation);
        fromDatabase(implementation);
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac -encoding UTF-8 DisjointThrowsDemo.java
java DisjointThrowsDemo
```

Stdout:

```text
Impl: OK
File: OK
DB: OK
```

**Điều đáng chú ý:**

1. `CombinedAction.execute()` không khai báo `throws`, nhưng vẫn thực thi hai interface.
2. `implementation.execute()` ở `main` có kiểu receiver `CombinedAction`, nên **không cần** bắt checked `IOException` hoặc `SQLException` cho riêng lời gọi đó.
3. `fromFile(FileAction action)` gọi qua kiểu tĩnh `FileAction`; compiler thấy `throws IOException` nên cần bắt hoặc khai báo.
4. `fromDatabase(DatabaseAction action)` gọi qua kiểu tĩnh `DatabaseAction`; compiler thấy `throws SQLException`.
5. Cùng **một object runtime** có thể được gọi qua các kiểu receiver khác nhau, nên **nghĩa vụ checked lúc biên dịch khác nhau** trong khi kết quả thực thi ở ví dụ đều là `"OK"`.

Điểm này rất quan trọng: `throws` ở **khai báo nhìn thấy tại call site** là một phần hợp đồng API; implementation hẹp hơn không khiến caller dùng interface tự biết và bỏ nghĩa vụ checked.

## 6. Khi hai interface có ngoại lệ kiểu cha–con

Đây là trường hợp hai hợp đồng **tương thích một phần**.

```java
interface GeneralReader {
    void open() throws java.io.IOException;
}

interface FileReaderContract {
    void open() throws java.io.FileNotFoundException;
}
```

`FileNotFoundException` là subtype của `IOException`. Implementation có thể:

- Không khai báo checked exception nào; hoặc
- Khai báo `throws FileNotFoundException` (hoặc checked subtype phù hợp nếu có).

Nó **không** được khai báo `throws IOException`, bởi `FileReaderContract.open()` không cho phép loại rộng như vậy.

### 6.1. Ví dụ Java 17 hoàn chỉnh

Tạo **`CompatibleThrowsDemo.java`**:

```java
import java.io.FileNotFoundException;
import java.io.IOException;

public class CompatibleThrowsDemo {
    interface GeneralReader {
        void open() throws IOException;
    }

    interface NarrowReader {
        void open() throws FileNotFoundException;
    }

    static final class MissingReader
            implements GeneralReader, NarrowReader {
        @Override
        public void open() throws FileNotFoundException {
            throw new FileNotFoundException("Không thấy tệp");
        }
    }

    public static void main(String[] args) {
        MissingReader implementation = new MissingReader();

        try {
            implementation.open();
        } catch (FileNotFoundException e) {
            System.out.println("Concrete: " + e.getMessage());
        }

        GeneralReader broadReference = implementation;
        try {
            broadReference.open();
        } catch (IOException e) {
            System.out.println("General: "
                    + e.getClass().getSimpleName());
        }

        NarrowReader narrowReference = implementation;
        try {
            narrowReference.open();
        } catch (FileNotFoundException e) {
            System.out.println("Narrow: " + e.getMessage());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 CompatibleThrowsDemo.java
java CompatibleThrowsDemo
```

Stdout:

```text
Concrete: Không thấy tệp
General: FileNotFoundException
Narrow: Không thấy tệp
```

Phân tích:

- Implementation `MissingReader.open()` khai báo `throws FileNotFoundException`, hợp lệ với cả hai interface.
- Khi gọi qua `GeneralReader`, caller phải xử lý checked `IOException` theo chữ ký interface, dù đối tượng runtime tạo `FileNotFoundException`.
- Khi gọi qua `NarrowReader`, caller biết hợp đồng checked hẹp hơn.
- `getClass().getSimpleName()` phản ánh **kiểu runtime của exception**, không bị biến thành `IOException` khi lời gọi được thực hiện qua `GeneralReader`.

**Lỗi thường gặp:** nghĩ “`IOException` chứa `FileNotFoundException` nên muốn ghi `throws IOException` trong implementation cũng được”. Chiều subtype ở đây ngược lại: interface hẹp chỉ cho phép **FileNotFoundException**, nên implementation không được mở rộng lên IOException.

## 7. Trường hợp một interface không khai báo checked exception

```java
interface Alpha {
    void run() throws java.io.IOException;
}

interface Beta {
    void run();
}
```

Với một implementation của cả hai, `Beta.run()` là hợp đồng **không cho checked exception thoát ra**. Vì vậy:

```java
final class Both implements Alpha, Beta {
    @Override
    public void run() {
        System.out.println("Run");
    }
}
```

là hợp lệ, còn:

```java
// CỐ Ý SAI:
final class InvalidBoth implements Alpha, Beta {
    @Override
    public void run() throws java.io.IOException {
    }
}
```

không hợp lệ do xung đột với `Beta.run()`.

Từ góc nhìn caller:

- `Alpha alpha = new Both();` → lời gọi `alpha.run()` chịu hợp đồng checked `IOException`.
- `Beta beta = new Both();` → lời gọi `beta.run()` không cần bắt checked IOException do khai báo của `Beta`.
- `Both both = new Both();` → lời gọi `both.run()` cũng không cần bắt checked IOException do khai báo thực thi.

**Đây không phải compiler suy đoán runtime instance ở mọi nơi.** Nó tuân theo phương thức được xác định bằng **kiểu tĩnh của biểu thức receiver** tại vị trí gọi.

## 8. Unchecked exception vẫn có thể xảy ra

Giới hạn của checked `throws` **không** ngăn implementation ném:

- `IllegalStateException`;
- `IllegalArgumentException`;
- `NullPointerException`;
- các `RuntimeException` subtype khác;
- `Error` subtype (dù không nên dùng `Error` cho lỗi nghiệp vụ).

Ví dụ hoàn chỉnh giúp thấy hai interface không liên quan vẫn được một implementation thỏa mãn dù method ném unchecked error trong đường chạy cụ thể.

Lưu **`UncheckedAcrossInterfacesDemo.java`**:

```java
import java.io.IOException;
import java.sql.SQLException;

public class UncheckedAcrossInterfacesDemo {
    interface FileStep {
        void process() throws IOException;
    }

    interface SqlStep {
        void process() throws SQLException;
    }

    static final class SharedStep implements FileStep, SqlStep {
        @Override
        public void process() {
            throw new IllegalStateException("Trạng thái không hợp lệ");
        }
    }

    public static void main(String[] args) {
        SharedStep implementation = new SharedStep();

        FileStep asFile = implementation;
        try {
            asFile.process();
        } catch (IOException e) {
            System.out.println("Checked I/O");
        } catch (IllegalStateException e) {
            System.out.println("File ref: "
                    + e.getClass().getSimpleName());
        }

        SqlStep asSql = implementation;
        try {
            asSql.process();
        } catch (SQLException e) {
            System.out.println("Checked SQL");
        } catch (IllegalStateException e) {
            System.out.println("SQL ref: "
                    + e.getClass().getSimpleName());
        }

        try {
            implementation.process();
        } catch (IllegalStateException e) {
            System.out.println("Concrete: "
                    + e.getClass().getSimpleName());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 UncheckedAcrossInterfacesDemo.java
java UncheckedAcrossInterfacesDemo
```

Stdout:

```text
File ref: IllegalStateException
SQL ref: IllegalStateException
Concrete: IllegalStateException
```

**Hai bài học riêng:**

1. `SharedStep.process()` không khai báo checked exceptions nên hợp lệ với cả hai interface; unchecked `IllegalStateException` không bị quy tắc override checked `throws` cấm.
2. Đây **không** phải khuyến nghị đổi mọi I/O/SQL failure thành `IllegalStateException`. Nếu chuyển loại exception, cần có chính sách hợp đồng, giữ `cause` và không làm caller hiểu nhầm thao tác thành công.

### 8.1. Vì sao `catch (IOException)` ở lời gọi qua `FileStep` hợp lệ?

Biến `asFile` có kiểu tĩnh `FileStep`, nơi phương thức công bố `throws IOException`. Compiler chấp nhận handler checked đó ngay cả khi implementation `SharedStep.process()` không ném IOException.

Nếu đổi thành:

```java
SharedStep concrete = new SharedStep();
try {
    concrete.process();
} catch (java.io.IOException e) {
    // CỐ Ý SAI nếu trong try không còn nguồn IOException khác.
}
```

thì `catch(IOException)` bị từ chối vì compile-time nhìn thấy chữ ký implementation **không khai báo checked IOException**. Đây là tác động của **kiểu receiver**, không phải của giá trị runtime.

## 9. Nếu thân implementation thực sự cần gọi API ném checked exception?

Đây mới là bài toán kỹ thuật quan trọng trong dự án. Xét `CombinedAction.execute()` phải dùng một hàm đọc cấu hình khai báo `throws IOException`. Vì hai interface chỉ cho những tập checked không tương thích, nó **không thể đơn giản thêm `throws IOException` vào implementation**.

Bạn có vài hướng lựa chọn:

1. **Xử lý tại implementation** khi có cách phục hồi thật: thử nguồn khác, ghi kết quả thất bại có nghĩa hoặc bỏ thao tác theo hợp đồng.
2. **Chuyển sang unchecked exception phù hợp** và giữ `cause`, nhưng chỉ khi hợp đồng API chấp nhận cách báo lỗi đó; caller vẫn cần biết có thể thất bại.
3. **Tái thiết kế interface** để tránh ép một method có hai nghĩa/hợp đồng xung đột vào một chữ ký duy nhất.
4. **Tách adapter**: mỗi interface có một implementation riêng, cả hai dùng chung một service nội bộ, nhưng mỗi adapter tự quyết định cách xử lý checked exception.
5. **Dùng result type** khi thất bại là một kết quả dự kiến thay vì điều kiện phải unwind bằng exception.

Không có một cách sửa tự động đúng cho mọi hệ thống. **Che lỗi hoặc trả thành công giả** không phải lựa chọn hợp lệ chỉ vì compiler cấm `throws` rộng hơn.

### 9.1. Ví dụ Java 17: chuyển checked I/O thành unchecked có cause

`java.io.UncheckedIOException` là wrapper unchecked chuyên dành cho `IOException`. Trong ví dụ, adapter buộc giữ chữ ký không có checked exception nhưng vẫn có thể truyền nguyên nhân ra caller.

Tạo **`HandledCheckedInsideDemo.java`**:

```java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.SQLException;

public class HandledCheckedInsideDemo {
    interface FileTask {
        void perform() throws IOException;
    }

    interface DatabaseTask {
        void perform() throws SQLException;
    }

    static final class CombinedTask
            implements FileTask, DatabaseTask {
        @Override
        public void perform() {
            try {
                readConfiguration();
            } catch (IOException original) {
                throw new UncheckedIOException(
                        "Không nạp được cấu hình", original);
            }
        }
    }

    static void readConfiguration() throws IOException {
        throw new IOException("Nguồn cấu hình lỗi");
    }

    public static void main(String[] args) {
        CombinedTask task = new CombinedTask();

        try {
            task.perform();
        } catch (UncheckedIOException e) {
            System.out.println("Exception: "
                    + e.getClass().getSimpleName());
            System.out.println("Cause: "
                    + e.getCause().getClass().getSimpleName());
            System.out.println("Message: " + e.getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 HandledCheckedInsideDemo.java
java HandledCheckedInsideDemo
```

Stdout:

```text
Exception: UncheckedIOException
Cause: IOException
Message: Không nạp được cấu hình
```

Giải thích:

- `readConfiguration()` thực sự ném checked `IOException`.
- `CombinedTask.perform()` **bắt** loại đó, rồi ném wrapper unchecked `UncheckedIOException`.
- `perform()` không khai báo `throws IOException` hoặc `throws SQLException`, nên hợp lệ với hai interface.
- `getCause()` vẫn giữ đối tượng `IOException` gốc.
- Việc đổi checked thành unchecked **không phục hồi** lỗi cấu hình và cũng không làm side effect đã xảy ra biến mất; nó chỉ thay đổi hợp đồng báo lỗi ở biên này.

**Cảnh báo:** nếu hai interface đại diện hai ý nghĩa thực sự khác nhau (đọc file và ghi database), dùng một implementation cho cùng tên method có thể là thiết kế gây hiểu nhầm. Đừng biến mọi checked exception thành unchecked chỉ để “qua compiler”; khi có thể, thiết kế hai operation khác nhau rõ hơn.

### 9.2. Tại sao `throw new RuntimeException(e.getMessage())` không tốt?

Nó làm mất đối tượng gốc và stack trace liên kết `cause`. Nếu wrapper hợp lý, dùng constructor giữ nguyên nhân (ví dụ `new UncheckedIOException(message, e)` hoặc custom exception có `super(message, e)`). Ngoài ra không nên log rồi nuốt lỗi chỉ để giữ method không có `throws`.

## 10. Caller nhìn thấy `throws` qua kiểu tham chiếu tĩnh

Đây là một phần quan trọng nhất của bài, nên xét rõ từng kiểu receiver.

Cho:

```java
interface A {
    void run() throws java.io.IOException;
}
interface B {
    void run() throws java.sql.SQLException;
}
final class C implements A, B {
    @Override
    public void run() {
        System.out.println("OK");
    }
}
```

Đây là khai báo lớp/interface minh họa, không có `main`. Ở call site:

```java
C c = new C();
c.run(); // Không yêu cầu xử lý checked exception.

A a = c;
// a.run(); // Phải bắt/khai báo IOException.

B b = c;
// b.run(); // Phải bắt/khai báo SQLException.
```

Dù cả ba biến đều trỏ tới cùng một `C`, compiler xác định phương thức được gọi qua **kiểu tĩnh** của receiver:

- `C.run()` không khai báo checked exception.
- `A.run()` khai báo `IOException`.
- `B.run()` khai báo `SQLException`.

**Đừng nhầm** “implementation không ném checked exception” với “caller gọi qua interface không cần xử lý checked exception”. Caller được biên dịch theo **API nó nhìn thấy**, giúp code tương thích khi sau này thay implementation bằng loại khác vẫn hợp lệ với cùng interface.

### 10.1. `catch` sai nguồn checked exception

Đoạn sau cố ý **không biên dịch**:

```java
A a = new C();
try {
    a.run();
} catch (java.sql.SQLException e) {
    System.out.println("SQL lỗi");
}
```

Với giả định `try` chỉ có lời gọi `a.run()`, checked `SQLException` không có nguồn khai báo tương thích. `A.run()` công bố `IOException`, nên phải xử lý hoặc khai báo `IOException`.

Thêm một multi-catch `catch (IOException | SQLException e)` cũng không khắc phục sự thiếu nguồn cho checked `SQLException` ở `try` này. Multi-catch không tự mở rộng hợp đồng nguồn lỗi.

### 10.2. `throws` là hợp đồng cho compiler, không phải danh sách toàn bộ lỗi runtime

`A.run() throws IOException` **không** cam kết phương thức không bao giờ ném `RuntimeException`. Một `NullPointerException` hoặc `IllegalStateException` có thể xảy ra theo đường runtime mà không được kê trong `throws`. Người sử dụng API vẫn cần chính sách phù hợp cho lỗi không dự kiến ở tầng ranh giới của hệ thống.

## 11. Thực thi phương thức đã kế thừa từ superclass

Một lớp có thể `extends` superclass đồng thời `implements` nhiều interface. Khi đó, method public được kế thừa từ superclass đôi khi đủ để thực hiện interface — **nhưng vẫn phải thỏa tất cả hợp đồng checked `throws`**.

Xem:

```java
class BaseService {
    public void sync() throws java.io.IOException {
        System.out.println("Base");
    }
}

interface NetworkContract {
    void sync() throws java.io.IOException;
}

interface SqlContract {
    void sync() throws java.sql.SQLException;
}
```

`BaseService.sync() throws IOException` không thể dùng nguyên trạng để đáp ứng `SqlContract.sync() throws SQLException` vì hai loại checked không tương thích. Lớp con có thể **override và thu hẹp** hợp đồng:

```java
final class UnifiedService
        extends BaseService
        implements NetworkContract, SqlContract {
    @Override
    public void sync() {
        System.out.println("Unified");
    }
}
```

### 11.1. Java 17 — ví dụ superclass, hai interface, một override

Tạo **`InheritedThrowsDemo.java`**:

```java
import java.io.IOException;
import java.sql.SQLException;

public class InheritedThrowsDemo {
    static class BaseService {
        public void sync() throws IOException {
            System.out.println("Base");
        }
    }

    interface NetworkContract {
        void sync() throws IOException;
    }

    interface SqlContract {
        void sync() throws SQLException;
    }

    static final class UnifiedService extends BaseService
            implements NetworkContract, SqlContract {
        @Override
        public void sync() {
            System.out.println("Unified");
        }
    }

    public static void main(String[] args) {
        UnifiedService concrete = new UnifiedService();
        concrete.sync();

        BaseService asBase = concrete;
        try {
            asBase.sync();
        } catch (IOException e) {
            System.out.println("I/O: " + e.getMessage());
        }

        SqlContract asSql = concrete;
        try {
            asSql.sync();
        } catch (SQLException e) {
            System.out.println("SQL: " + e.getMessage());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 InheritedThrowsDemo.java
java InheritedThrowsDemo
```

Stdout:

```text
Unified
Unified
Unified
```

Giải thích:

1. `UnifiedService.sync()` override method từ `BaseService`, thực thi cả hai interface và bỏ checked `throws`.
2. Khi gọi qua `UnifiedService`, compiler không yêu cầu xử lý checked.
3. Khi gọi qua `BaseService`, compiler yêu cầu xét `IOException` vì đó là chữ ký cấp cha.
4. Khi gọi qua `SqlContract`, compiler yêu cầu xét `SQLException`.
5. Dispatch runtime vẫn gọi **`UnifiedService.sync()`** trong cả ba trường hợp, nên đều in `Unified`.

Nếu bỏ override trong `UnifiedService`, method kế thừa từ `BaseService` có `throws IOException` không đáp ứng hợp đồng của `SqlContract`; class sẽ không biên dịch thành một implementation hợp lệ.

## 12. Checked exception là ràng buộc override, không phải biến thể overload

Nhìn một tập quy tắc đối chiếu:

| Tình huống | Quy tắc |
| --- | --- |
| Cùng tên, cùng tham số, khác `throws` | Không tạo overload; phải đáp ứng override/implementation |
| Cùng tên, khác tham số | Có thể là overload; mỗi method xét hợp đồng của signature riêng |
| Một implementation của hai interface cùng signature | Thỏa đồng thời các mệnh đề checked `throws` |
| Implementation khai báo **ít** checked exception hơn interface | Hợp lệ |
| Implementation khai báo subtype checked của loại interface cho phép | Hợp lệ |
| Implementation khai báo checked rộng hơn bất kỳ interface liên quan | Không hợp lệ |
| Implementation ném unchecked `RuntimeException` | Không bị hạn chế bởi khai báo checked `throws` của interface |
| Caller gọi qua một interface | Nghĩa vụ checked dựa trên method được xác định ở compile-time qua interface đó |
| Caller gọi qua class implementation | Nghĩa vụ checked dựa trên chữ ký method tương ứng của class |
| Interface có generic method/return type khác biệt | Ngoài checked `throws` còn phải thỏa quy tắc signature, type parameter, return-type substitutability |

Đừng suy luận rằng hai method có `throws` khác nhau thì compiler sẽ tạo hai implementation tách biệt. Java dispatch dựa trên method signature và receiver, không chọn method theo loại checked exception được kê trong `throws`.

## 13. Những ví dụ cố ý sai và cách đọc thông báo compiler

### 13.1. Sai vì thêm hai checked exception không tương thích

```java
interface Readable {
    void process() throws java.io.IOException;
}
interface Writable {
    void process() throws java.sql.SQLException;
}
class Invalid implements Readable, Writable {
    @Override
    public void process()
            throws java.io.IOException, java.sql.SQLException {
    }
}
```

**Không biên dịch:** implementation khai báo những checked exception mà **một interface tương ứng không cho phép**.

Giải pháp tùy ngữ cảnh: bỏ các checked `throws` và xử lý lỗi trong method; dùng unchecked wrapper có cause khi phù hợp; hoặc đổi thiết kế API để phân biệt hai hợp đồng.

### 13.2. Sai vì implementation khai báo kiểu rộng hơn hợp đồng hẹp

```java
interface Narrow {
    void open() throws java.io.FileNotFoundException;
}
interface Broad {
    void open() throws java.io.IOException;
}
class TooBroad implements Narrow, Broad {
    @Override
    public void open() throws java.io.IOException {
    }
}
```

**Không biên dịch:** `Narrow.open()` chỉ cho checked `FileNotFoundException`, không cho mọi `IOException`.

Đổi thành `throws FileNotFoundException` hoặc không khai báo checked exception nào.

### 13.3. Sai vì chỉ bỏ `throws` mà không xử lý lời gọi

```java
interface A {
    void load() throws java.io.IOException;
}
interface B {
    void load() throws java.sql.SQLException;
}
class ForgotHandling implements A, B {
    @Override
    public void load() {
        java.nio.file.Files.readString(
                java.nio.file.Path.of("config.txt"));
    }
}
```

**Không biên dịch:** `Files.readString(...)` công bố checked `IOException`, nhưng `load()` không bắt/khai báo phù hợp. Thêm `throws IOException` vào `load()` lại xung đột với `B`. Phải xử lý checked exception trong thân hoặc thiết kế lại hợp đồng.

### 13.4. Sai vì quyền truy cập không đủ

```java
interface PublicTask {
    void execute();
}
class Hidden implements PublicTask {
    void execute() {
    }
}
```

**Không biên dịch:** method implementation phải là `public`, không được hạ mức truy cập từ method `public` của interface. Dù bài tập chính là `throws`, đừng quên đây là **một ràng buộc override độc lập**.

### 13.5. Không lạm dụng `throws Exception`

Một lớp có thể khai báo `throws Exception` khi cả hai interface đều cho phép `Exception`. Nhưng đó là hợp đồng rộng, ép caller xử lý nhiều loại checked không cụ thể. Nếu API có thể diễn đạt chính xác bằng `IOException` hoặc kiểu tùy chỉnh hẹp, nên cân nhắc mô tả cụ thể để caller hiểu mình có thể phục hồi ra sao.

Đặt `throws Exception` ở implementation **không sửa được** tình huống interface A chỉ cho `IOException` còn interface B chỉ cho `SQLException`; nó càng làm hợp đồng rộng hơn và không hợp lệ.

## 14. Có thể gộp hai interface thành một interface con không?

Một interface có thể `extends` nhiều interface:

```java
interface FileStep {
    void process() throws java.io.IOException;
}

interface SqlStep {
    void process() throws java.sql.SQLException;
}

interface CombinedStep extends FileStep, SqlStep {
}
```

Đây là **khai báo hợp lệ** khi hai method được kế thừa có cùng signature và kiểu trả về tương thích. Việc kế thừa không tự tạo một method “`throws IOException, SQLException`” như một hợp đồng rộng hơn để implementation dùng tùy ý.

Một class thực thi `CombinedStep` vẫn phải thỏa **cả hai** hợp đồng checked exception. Thông thường nó dùng `public void process()` không khai báo checked, như trường hợp `implements FileStep, SqlStep` trực tiếp.

### 14.1. Redeclare trong interface con để làm rõ hợp đồng

Có thể khai báo lại method với checked `throws` phù hợp:

```java
interface CombinedStep extends FileStep, SqlStep {
    @Override
    void process();
}
```

Đây là ví dụ interface minh họa. Khai báo lại không checked `throws` giúp người đọc biết hợp đồng của `CombinedStep.process()`. Nó **không** sửa chữ ký `FileStep.process()` hoặc `SqlStep.process()`; lời gọi qua từng interface gốc vẫn chịu checked exception mà interface gốc công bố.

Nếu đặt `throws IOException, SQLException` lên `CombinedStep.process()` để nới rộng, nó vi phạm quy tắc override đối với các interface cha.

### 14.2. Nếu chỉ muốn một chính sách thống nhất

Thay vì ép hai nghĩa khác nhau vào cùng `void process()`, cân nhắc:

```java
interface FileService {
    void readFile() throws java.io.IOException;
}

interface DatabaseService {
    void queryDatabase() throws java.sql.SQLException;
}
```

Một class có thể thực thi **cả hai**, với **hai method có tên khác nhau** và hai hợp đồng phù hợp. Đó thường là thiết kế rõ ràng hơn nếu một operation là đọc file và operation kia là truy vấn database.

## 15. Hệ quả đối với kiểm thử và thiết kế thư viện

### 15.1. Kiểm thử lời gọi theo từng kiểu tĩnh

Một test ở mức interface nên gọi qua interface thực sự được dùng trong production, không chỉ qua class implementation. Điều này giúp kiểm tra khả năng thay thế implementation và hợp đồng caller mong đợi.

Với `DisjointThrowsDemo`, thử viết hai hàm `fromFile(FileAction)` và `fromDatabase(DatabaseAction)` như ví dụ; mỗi hàm xử lý checked exception theo interface mình biết. Nếu sau này thay implementation, code caller vẫn đúng với hợp đồng đó.

### 15.2. Kiểm thử checked `throws` không giống kiểm thử exception runtime

Một method `FileAction.execute() throws IOException` có thể không ném lỗi khi chạy. Điều đó **không** nghĩa chữ ký vô dụng hoặc test đã chứng minh không bao giờ có IOException. Nó chỉ là hợp đồng cho nhiều implementation khác nhau.

Dùng test để xác minh hành vi runtime (thành công, lỗi có điều kiện, state sau lỗi), còn compiler xác minh các ràng buộc checked ở mỗi vị trí gọi. Các ví dụ “cố ý không biên dịch” ở trên được dùng để học quy tắc ngôn ngữ, không nên đặt chung vào source của những chương trình runnable.

### 15.3. Không tự suy luận một ngoại lệ “không thể xảy ra” vì implementation hiện tại

Nếu một biến được khai báo kiểu `FileAction`, compiler phải cho phép khả năng implementation hợp lệ khác của `FileAction` có thể ném `IOException`. Việc object cụ thể hiện tại là `CombinedAction` không khiến code dùng kiểu `FileAction` được miễn nghĩa vụ checked tại compile-time.

### 15.4. API thư viện nên tránh hợp đồng mâu thuẫn

Nếu hai interface do bạn kiểm soát cùng mô tả một operation nhưng `throws` không tương thích, hãy hỏi liệu chúng thực sự có nên dùng **cùng một method signature** hay không. Đôi khi đặt tên khác, đưa lỗi thành result type chung, hoặc tách một interface chức năng rõ ràng là giải pháp tốt hơn việc chuyển mọi checked exception thành unchecked.

## 16. Lưu ý về default method, generics và các quy tắc khác

Bài này chủ yếu xét **hai khai báo abstract instance method** có cùng signature và kiểu trả về tương thích. Java còn có các quy tắc độc lập:

- Nếu nhiều interface cung cấp **default method xung đột**, class thường phải override để giải quyết. Đó là bài toán **thừa kế implementation** chứ không chỉ `throws`.
- Khi kết hợp method trong interface có **kiểu trả về không tương thích**, một implementation có thể không tồn tại dù `throws` đã hợp lệ.
- Khi method generic hoặc erasure khiến nhiều khai báo đụng signature, compiler còn kiểm tra type parameters, method subsignature và name clash.
- Static/private method của interface không tạo hợp đồng abstract instance method như những ví dụ trong bài; không nên nhập chúng vào phép so sánh `throws` của cùng một override.
- Nếu class kế thừa method từ superclass và cũng implements interface, method kế thừa phải hợp đồng; nếu không, class cần override như `InheritedThrowsDemo`.

**Đừng lạm dụng quy tắc về `throws` để giải thích mọi lỗi compiler khi implements nhiều interface.** Đọc thông báo cụ thể và kiểm tra từng ràng buộc: tên/tham số, kiểu trả về, phạm vi truy cập, checked exception, default methods, generic erasure.

## 17. Một quy trình giải bài theo từng bước

Khi gặp yêu cầu “một class implements nhiều interface có `throws` khác nhau”, hãy thực hiện:

1. **Xác định đúng cùng signature:** tên method và kiểu tham số có thực sự khớp hay chỉ là overload?
2. **Đọc từng hợp đồng:** ghi lại các kiểu checked được khai báo ở mỗi interface. Bỏ qua sự khác biệt message và import alias.
3. **Vẽ quan hệ kế thừa:** ví dụ `FileNotFoundException <: IOException <: Exception`.
4. **Tìm các loại checked được mọi hợp đồng cho phép:** xét subtype, không chỉ giao hai danh sách tên.
5. **Chọn `throws` hẹp nhất cần thiết:** có thể bỏ hoàn toàn checked `throws` nếu implementation xử lý nội bộ.
6. **Xem thân method có thể ném gì:** kiểm tra lời gọi I/O/SQL, constructor/resource close, `throw` trực tiếp.
7. **Nếu không thể để checked exception thoát:** xử lý đúng ý nghĩa, bọc unchecked có cause khi hợp đồng cho phép, hoặc thiết kế lại API.
8. **Kiểm tra caller:** lời gọi qua A, B hay class implementation có thể có nghĩa vụ checked khác nhau.
9. **QA:** bảo đảm `public`, return type tương thích, `@Override` và không có lỗi do default/generics ngoài phạm vi.
10. **Test runtime:** xác nhận lỗi được báo thật sự, không nuốt, không trả thành công giả và không làm sai trạng thái dữ liệu.

Cách làm này thường hiệu quả hơn việc thử thay `throws Exception` khắp code cho đến khi compiler im lặng.

## 18. Những nhầm lẫn phổ biến

| Nhầm lẫn | Điều đúng |
| --- | --- |
| “Implementation được `throws IOException, SQLException` vì A cho IO còn B cho SQL.” | **Không**. Mỗi checked exception phải phù hợp với **tất cả** các contract mà method override. |
| “Chỉ việc giữ nguyên `throws` của interface thứ nhất.” | Phải xét cả interface còn lại. |
| “Khác `throws` nghĩa là hai overload khác nhau.” | `throws` không phân biệt overload; cùng tên/tham số thường vẫn là một signature. |
| “Không khai báo checked exception thì method không thể xảy ra lỗi.” | Vẫn có thể ném unchecked hoặc xử lý checked nội bộ. |
| “Implementation có thể ném `IOException` dù interface khác không cho vì method body thực sự gọi file.” | Compiler yêu cầu xử lý checked bên trong hoặc thay thiết kế; không cho vi phạm interface. |
| “Nếu A throws IOException, B throws FileNotFoundException thì implementation phải bỏ hết.” | Có thể khai báo `FileNotFoundException` vì đây là subtype của `IOException`. |
| “Implementation được ghi `throws IOException` vì B cho FileNotFoundException.” | Sai chiều subtype: IOException rộng hơn. |
| “Gọi qua kiểu class hay interface đều chịu cùng `throws`.” | Nghĩa vụ compile-time dựa trên khai báo method nhìn thấy qua **kiểu tĩnh** của receiver. |
| “Bắt IOException ở lời gọi qua class không `throws` luôn hợp lệ.” | Một handler checked cụ thể có thể bị compiler từ chối nếu `try` không có nguồn exception phù hợp. |
| “`@Override` giúp bỏ qua xung đột checked exception.” | `@Override` chỉ yêu cầu compiler kiểm tra quan hệ override. |
| “Biến checked thành unchecked luôn là cách thiết kế tốt.” | Chỉ hợp lý khi có hợp đồng và chính sách xử lý rõ, giữ cause, không che lỗi. |
| “Có thể bắt `Exception` rồi bỏ qua để thỏa cả hai interface.” | Có thể che thất bại nghiệp vụ và side effect; bắt phải đi kèm xử lý thật. |
| “Một class chỉ implements được một interface có method `run()`.” | Có thể implements nhiều interface chung signature nếu các hợp đồng tương thích. |
| “Superclass đã có method thì chắc chắn satisfies mọi interface.” | Method kế thừa vẫn phải thỏa checked `throws` của tất cả; có thể cần override. |
| “Đổi sang `throws Throwable` sẽ giải quyết xung đột.” | Càng rộng, thường bị từ chối và là hợp đồng không phù hợp. |
| “Các lỗi default method/generic đều do `throws`.” | Còn nhiều quy tắc độc lập về return type, signature, erasure, implementation conflict. |

## 19. Bài tập thực hành

### Bài 1 — Xung đột hai checked exception không liên quan

Cho hai interface:

```java
interface A {
    void run() throws java.io.IOException;
}
interface B {
    void run() throws java.sql.SQLException;
}
```

Hãy viết `class C implements A, B` với một `public void run()` hợp lệ. Nếu code bên trong cần gọi API `throws IOException`, nêu hai lựa chọn có trách nhiệm: xử lý I/O nội bộ hoặc chuyển sang unchecked có cause theo hợp đồng. Vì sao thêm `throws IOException` trực tiếp lại sai?

### Bài 2 — Tìm kiểu checked hợp lệ

A khai báo `throws IOException`; B khai báo `throws FileNotFoundException`. Đánh giá implementation với: (a) không `throws`, (b) `throws FileNotFoundException`, (c) `throws IOException`, (d) `throws Exception`. **Đáp án:** (a) và (b) hợp lệ về mệnh đề throws; (c), (d) không hợp lệ so với B.

### Bài 3 — Nghĩa vụ khi upcast

Chạy `DisjointThrowsDemo`. Thay biến `CombinedAction implementation` ở `main` bằng `FileAction implementation`, rồi thử gọi `implementation.execute()` mà không catch/declare. Compiler phản hồi gì? Đổi tiếp kiểu tham chiếu thành `DatabaseAction` và so sánh.

### Bài 4 — Unchecked ngoại lệ không bị interface giới hạn

Chạy `UncheckedAcrossInterfacesDemo`. Vì sao `IllegalStateException` lọt qua cả hai hợp đồng checked `throws`? Có nên dùng `IllegalStateException` cho mọi lỗi I/O/SQL? **Không**; chọn exception dựa trên ngữ nghĩa, cách phục hồi và cause.

### Bài 5 — Superclass và interface

Trong `InheritedThrowsDemo`, bỏ override `UnifiedService.sync()`. Giải thích lỗi compile-time. Khôi phục override không `throws` và xác nhận code gọi qua `BaseService`, `SqlContract`, `UnifiedService` có nghĩa vụ checked khác nhau.

### Bài 6 — Đổi API để giảm mâu thuẫn

Giả sử hai interface đều có `void save()` nhưng một cái “save file”, một cái “save database”. Thiết kế lại thành `saveToFile() throws IOException` và `saveToDatabase() throws SQLException`. Phân tích vì sao hai method tách nghĩa dễ bảo trì hơn một `save()` phải chuyển loại lỗi.

### Bài 7 — Thứ tự handler không làm thay đổi nghĩa vụ `throws`

Một implementation thỏa cả hai interface bằng cách không khai báo checked. Caller `FileAction file = implementation;` có nên viết `catch(SQLException)` đơn độc quanh `file.execute()` không? **Không**, vì chữ ký nhìn thấy tại call site công bố `IOException`. Viết handler đúng rồi mô tả ngoại lệ runtime nếu implementation ném `IllegalStateException`.

### Bài 8 — Thiết kế adapter

Viết một service nội bộ `StorageService.read() throws IOException`. Tạo một adapter thực thi interface `Task.run()` không `throws`: adapter bắt `IOException` và trả status thất bại có cấu trúc (nếu giao diện cho phép), hoặc ném một unchecked wrapper với cause. Đảm bảo không trả kết quả thành công giả và không làm mất lỗi gốc.

### Bài 9 — Exception kiểu cha và con

Interface A `throws Exception`, interface B `throws IOException`. Implementation có thể `throws IOException`? **Có**. Có thể `throws SQLException`? **Không**, vì B không cho phép. Có thể `throws FileNotFoundException`? **Có**, vì nó là subtype của IOException.

### Bài 10 — Phân biệt method signature với `throws`

Cho hai method `load(int) throws IOException` và `load(String) throws SQLException`. Vì sao đây là overload hợp lệ, trong khi hai method `load(int)` chỉ khác `throws` lại không hợp lệ? Trình bày vai trò danh sách tham số và lý do return type/throws không tạo overload độc lập.

## 20. Checklist trước khi hoàn thành

- [ ] Tôi xác định đúng các method cùng signature và có thực sự phải chung implementation chưa?
- [ ] Đã đọc **tất cả** interface mà method implementation override chưa?
- [ ] Đã phân biệt checked `Exception` với unchecked `RuntimeException`/`Error` chưa?
- [ ] Mỗi checked exception trong `throws` của implementation đều được **mọi** hợp đồng cho phép chưa?
- [ ] Tôi đã xét quan hệ subtype, không chỉ so sánh danh sách tên exception chưa?
- [ ] Khi hai interface cho IOException và SQLException không liên quan, tôi có biết implementation không được khai báo cả hai không?
- [ ] Khi một interface cho IOException, interface kia cho FileNotFoundException, tôi chọn được throws hẹp không?
- [ ] Tôi đã kiểm tra checked exception phát sinh trực tiếp/gián tiếp trong thân implementation chưa?
- [ ] Nếu phải xử lý trong thân, tôi có tránh nuốt lỗi và giữ `cause` khi chuyển loại không?
- [ ] Tôi nhớ nghĩa vụ checked tại call site được xác định bởi **kiểu tĩnh của receiver** chưa?
- [ ] Tôi biết implementation không khai báo checked vẫn có thể ném unchecked exception chưa?
- [ ] Tôi đã để method thực thi interface ở mức truy cập `public` và dùng `@Override` chưa?
- [ ] Tôi không cố tạo overload chỉ khác `throws` chưa?
- [ ] Nếu có superclass, tôi đã kiểm tra method kế thừa và override bổ sung chưa?
- [ ] Tôi đã phân biệt lỗi `throws` với lỗi xung đột default method, generic erasure, kiểu trả về chưa?
- [ ] Test có kiểm tra cả đường thành công, lỗi thực tế và trạng thái sau thất bại chưa?

**Kết luận:** Khi một phương thức thực thi nhiều interface với các mệnh đề `throws` khác nhau, **implementation phải thỏa tất cả các hợp đồng checked exception cùng lúc**. Với `IOException` và `SQLException` không liên quan, method triển khai không được khai báo checked exception từ hai nhánh đó; với `IOException` và `FileNotFoundException`, có thể khai báo loại hẹp `FileNotFoundException`. Caller vẫn bị kiểm tra theo kiểu tham chiếu tĩnh của mình. Nếu implementation cần thao tác có checked exception nằm ngoài phần hợp đồng chung, phải xử lý có trách nhiệm hoặc thiết kế lại API, không chỉ “thêm `throws`” cho qua compiler.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-inheritance-006">Overload, kiểu khai báo của tham chiếu và tập ngoại lệ cần xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# Overload, kiểu khai báo của tham chiếu và tập ngoại lệ cần xử lý trong Java

Khi một lớp Java có nhiều phương thức cùng tên nhưng khác danh sách tham số (*method overloading*), lập trình viên dễ cho rằng JVM sẽ chọn phiên bản theo **kiểu thực tế của đối tượng** và từ đó quyết định ngoại lệ phải bắt. Thực tế, Java có hai bước tách biệt:

1. **Lúc biên dịch:** compiler chọn **overload** áp dụng được và cụ thể nhất theo tên phương thức, các phương thức nhìn thấy từ **kiểu tĩnh của biểu thức nhận** (*receiver*), số lượng và **kiểu ở thời điểm biên dịch** của các đối số. Việc phương thức được chọn khai báo `throws IOException` hay `throws SQLException` **không phải tiêu chí chọn overload**.
2. **Sau khi chọn overload:** compiler xác định **các checked exception có thể phát sinh từ lời gọi** theo hợp đồng của phương thức được chọn, và yêu cầu bắt hoặc khai báo `throws` cho những kiểu checked chưa được xử lý. Khi chương trình chạy, **override** có thể điều phối đến phần thân phương thức ở lớp con nhưng không tự chuyển lời gọi sang một overload khác.

Đó là lý do cùng một đối tượng `String` có thể đi vào hai overload khác nhau nếu được chứa trong biến kiểu `String` hoặc `Object`, và vì sao cùng một đối tượng lớp con có thể bị kiểm tra `throws IOException` khác nhau tùy tham chiếu đang được khai báo kiểu lớp cha hay lớp con.

Bài này giải thích có ví dụ Java 17+ chạy được: overload resolution, kiểu tĩnh và runtime, override, checked exception checking, `null` và overload mơ hồ, primitive widening/boxing, generics, constructor overload, giới hạn khi override và cách thiết kế API ngoại lệ dễ dùng.

## 1. Phân biệt overload và override

Hai khái niệm có thể xuất hiện trong cùng một chương trình nhưng giải quyết hai việc khác nhau:

| Tiêu chí | Overload | Override |
| --- | --- | --- |
| Bản chất | Nhiều phương thức cùng tên nhưng **khác chữ ký tham số** | Lớp con định nghĩa lại phương thức kế thừa với chữ ký override-compatible |
| Thời điểm chọn chữ ký | **Compile-time** | Chữ ký lời gọi vẫn được xác định ở compile-time |
| Chọn phần thân thực thi | Phần thân của overload đã chọn, có thể còn chịu dispatch nếu overload đó bị override | **Runtime dispatch** cho instance method có thể chọn implementation ở lớp con |
| Vai trò của `throws` | Không phân biệt hai overload | Override **không được mở rộng checked exceptions** ngoài hợp đồng phương thức cha |
| Vai trò của kiểu tĩnh tham chiếu | Quyết định tập phương thức ứng viên nhìn thấy | Quyết định hợp đồng compile-time tại nơi gọi |
| Vai trò của kiểu runtime đối tượng | Không tự chọn overload khác | Có thể quyết định implementation thực sự của chữ ký đã chọn |

Ví dụ khai báo overload:

```java
void load(Object value) throws java.io.IOException { /* ... */ }

void load(String value) throws java.text.ParseException { /* ... */ }
```

Hai phương thức trên khác danh sách kiểu tham số: `Object` so với `String`, nên Java cho phép chúng có các mệnh đề `throws` khác nhau. Ngược lại, hai phương thức **chỉ khác `throws`** mà danh sách tham số giống hệt nhau **không tạo thành overload hợp lệ**.

Trong một chương trình Java, `throws` đóng vai trò mô tả **hợp đồng ngoại lệ checked của phương thức** chứ không phải thành phần làm độc nhất chữ ký overload.

## 2. Ví dụ hoàn chỉnh: cùng đối tượng `String`, hai kiểu tham chiếu đối số

Lưu thành `OverloadCheckedDemo.java`:

```java
import java.io.IOException;
import java.text.ParseException;

public class OverloadCheckedDemo {
    static void inspect(Object value) throws IOException {
        System.out.println("selected=Object");
        throw new IOException("object-path");
    }

    static void inspect(String value) throws ParseException {
        System.out.println("selected=String");
        throw new ParseException("string-path", 0);
    }

    public static void main(String[] args) {
        String narrow = "hello";
        Object broad = narrow;

        try {
            inspect(narrow);
        } catch (ParseException ex) {
            System.out.println("caught=" + ex.getMessage());
        }

        try {
            inspect(broad);
        } catch (IOException ex) {
            System.out.println("caught=" + ex.getMessage());
        }

        try {
            inspect((String) broad);
        } catch (ParseException ex) {
            System.out.println("caught=" + ex.getMessage());
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 OverloadCheckedDemo.java
java OverloadCheckedDemo
```

Kết quả:

```text
selected=String
caught=string-path
selected=Object
caught=object-path
selected=String
caught=string-path
```

Ba lời gọi minh họa rõ:

1. `inspect(narrow)`: biểu thức đối số có kiểu tĩnh `String`. Cả `inspect(Object)` và `inspect(String)` đều áp dụng được, nhưng `String` cụ thể hơn nên compiler chọn phiên bản `String` và cần xử lý `ParseException`.
2. `inspect(broad)`: cùng đối tượng runtime nhưng biểu thức đối số có kiểu tĩnh `Object`. `inspect(String)` không áp dụng được cho đối số kiểu `Object` nếu không ép kiểu; compiler chọn `inspect(Object)` và cần xử lý `IOException`.
3. `inspect((String) broad)`: ép kiểu làm biểu thức đối số có kiểu tĩnh `String`. Lời gọi lại chọn overload `inspect(String)` và cần xử lý `ParseException`. Ép kiểu chỉ an toàn khi đối tượng thực tế tương thích; với đối tượng không phải `String`, phép cast có thể ném `ClassCastException`.

**Kết luận:** ngoại lệ checked tại vị trí gọi được xác định từ **overload đã được compiler chọn**. Đừng quyết định `catch` dựa trên `broad.getClass()` hoặc việc biết đối tượng runtime chứa văn bản.

## 3. Compiler lựa chọn overload theo các bước nào?

Theo JLS §15.12, việc xử lý lời gọi phương thức gồm các bước lớn:

1. Xác định **kiểu cần tìm phương thức** và tên phương thức. Đối với lời gọi `receiver.method(...)`, kiểu khai báo của `receiver` giới hạn tập phương thức có thể nhìn thấy tại nơi gọi.
2. Xác định các phương thức có tên phù hợp, số đối số và khả năng áp dụng theo **kiểu tĩnh của biểu thức đối số**, generic type inference và các quy tắc chuyển đổi hợp lệ.
3. Ưu tiên các pha áp dụng: lời gọi chặt (*strict invocation*), lời gọi cho phép chuyển đổi rộng hơn (*loose invocation*, gồm boxing/unboxing), rồi lời gọi varargs khi cần.
4. Chọn phương thức **cụ thể nhất** trong tập ứng viên áp dụng của pha được sử dụng. Nếu không có một lựa chọn duy nhất phù hợp, lời gọi có thể mơ hồ và không biên dịch.
5. Sau khi có khai báo được chọn, kiểm tra các điều kiện của lời gọi, gồm kiểm tra khả năng phát sinh checked exceptions.
6. Khi chạy, đánh giá đối tượng nhận và các đối số; nếu là instance method có khả năng override, JVM tìm phần thân triển khai thích hợp cho **chữ ký đã được chọn**.

Mệnh đề `throws` không được dùng để “bỏ phiếu” cho overload nào an toàn hơn. Ví dụ, dù người gọi đã viết `catch (IOException)` thì compiler **không vì thế** ưu tiên `inspect(Object)` trước `inspect(String)`.

**Lưu ý:** độ cụ thể của overload không luôn chỉ là “kiểu con thắng kiểu cha”. Java còn có generic methods, boxing/unboxing, varargs và target typing. Khi lời gọi phức tạp, cần áp dụng đúng các pha của JLS §15.12 thay vì suy luận bằng tên biến hay kiểu runtime.

## 4. `throws` không tham gia phân biệt chữ ký overload

Mã sau **cố ý không biên dịch**:

```java
import java.io.IOException;
import java.sql.SQLException;

class InvalidOverloadByThrows {
    void work(String input) throws IOException { }

    void work(String input) throws SQLException { }
    // Lỗi: cùng tên và cùng danh sách tham số.
}
```

Hai khai báo có cùng chữ ký; chỉ khác danh sách ngoại lệ checked. Java không xem chúng là hai overload hợp lệ. Tương tự, chỉ thay đổi **kiểu trả về** cũng không tạo overload khác:

```java
class InvalidOverloadByReturnType {
    String convert(int value) { return "text"; }

    int convert(int value) { return value; }
    // Lỗi: không thể phân biệt bằng return type.
}
```

Cách sửa là chọn tham số khác (nếu thực sự là hai thao tác khác nhau) hoặc đặt tên phương thức khác thể hiện ngữ nghĩa khác nhau:

```java
void readFromFile(String path) throws IOException { }

void readFromDatabase(String query) throws SQLException { }
```

Ba đoạn trong phần này dùng để **minh họa quy tắc khai báo**; đoạn thứ ba muốn biên dịch độc lập phải đặt trong một lớp và thêm import phù hợp.

Hãy nhớ: **đối với overload, tham số phân biệt phương thức; `throws` mô tả nghĩa vụ xử lý lỗi sau khi đã chọn phương thức**.

## 5. Kiểu tĩnh của receiver giới hạn những overload nào nhìn thấy

Không chỉ kiểu của **đối số**, mà kiểu tĩnh của **đối tượng nhận lời gọi** cũng rất quan trọng.

Lưu thành `ReceiverTypeDemo.java`:

```java
import java.io.IOException;
import java.sql.SQLException;

public class ReceiverTypeDemo {
    static class Parent {
        void send(Object value) throws IOException {
            System.out.println("Parent.send(Object)");
        }
    }

    static class Child extends Parent {
        @Override
        void send(Object value) throws IOException {
            System.out.println("Child.send(Object)");
        }

        void send(String value) throws SQLException {
            System.out.println("Child.send(String)");
        }
    }

    public static void main(String[] args) {
        Parent parentView = new Child();
        Child childView = new Child();

        try {
            parentView.send("abc");
        } catch (IOException ex) {
            System.out.println("parent-view error");
        }

        try {
            childView.send("abc");
        } catch (SQLException ex) {
            System.out.println("child-view error");
        }
    }
}
```

```bash
javac -encoding UTF-8 ReceiverTypeDemo.java
java ReceiverTypeDemo
```

Kết quả:

```text
Child.send(Object)
Child.send(String)
```

Giải thích chi tiết:

- `parentView` có kiểu **khai báo `Parent`**, dù đối tượng được tạo bằng `new Child()`. Compiler tìm trong API của `Parent` và chỉ thấy `send(Object)`. Vì vậy `parentView.send("abc")` chọn chữ ký `send(Object)` và phải xử lý `IOException`.
- Khi chạy, đối tượng runtime là `Child` nên phần thân **override `Child.send(Object)`** được thực thi. Nó in `Child.send(Object)`; Java **không chuyển sang `Child.send(String)`**.
- `childView` có kiểu khai báo `Child`, nên compiler thấy cả `send(Object)` và `send(String)`. Với đối số literal `"abc"`, nó chọn overload `send(String)` cụ thể hơn. Tại nơi gọi, nghĩa vụ checked exception là `SQLException`.

Đây là ví dụ then chốt về **hai vai trò của kiểu tĩnh**: kiểu receiver xác định overload có thể thấy, còn kiểu đối số xác định overload nào được chọn. Runtime dispatch chỉ ảnh hưởng implementation của phương thức đã chọn khi có override.

## 6. Override: vì sao tham chiếu lớp cha có thể cần `catch` dù lớp con không ném checked exception?

Xét lớp cha khai báo `throws IOException`, nhưng implementation lớp con thu hẹp hợp đồng và không ném checked exception.

Lưu thành `OverrideThrowsDemo.java`:

```java
import java.io.IOException;

public class OverrideThrowsDemo {
    static class Parent {
        void fetch() throws IOException {
            System.out.println("parent fetch");
        }
    }

    static class Child extends Parent {
        @Override
        void fetch() {
            System.out.println("child fetch");
        }
    }

    public static void main(String[] args) {
        Parent asParent = new Child();
        Child asChild = new Child();

        try {
            asParent.fetch();
        } catch (IOException ex) {
            System.out.println("caught I/O");
        }

        asChild.fetch();
    }
}
```

```bash
javac -encoding UTF-8 OverrideThrowsDemo.java
java OverrideThrowsDemo
```

Kết quả:

```text
child fetch
child fetch
```

Cả hai lời gọi đều chạy phần thân `Child.fetch()`, vì đối tượng runtime là `Child` và phương thức `fetch()` được override. Nhưng:

- `asParent.fetch()` được kiểm tra theo **khai báo `Parent.fetch() throws IOException`**. Vì vậy caller vẫn phải xử lý hoặc khai báo `IOException`, dù bạn biết lần này đối tượng là Child.
- `asChild.fetch()` được kiểm tra theo **khai báo `Child.fetch()`** không có checked exception, nên không cần `catch(IOException)`.

**Nghĩa vụ checked exception tại điểm gọi không đổi tùy lớp runtime của đối tượng.** Hợp đồng kiểu tĩnh cho phép thay thế implementation mà vẫn bảo đảm code caller đã được kiểm tra đúng.

Ngược lại, lớp con không được override và khai báo checked exception **rộng hơn** phương thức cha. Ví dụ, nếu cha khai báo `throws IOException`, lớp con không thể override cùng chữ ký bằng `throws Exception` hoặc thêm `SQLException` độc lập.

## 7. Ranh giới override: cùng chữ ký, không mở rộng checked exception

Mã **cố ý không biên dịch**:

```java
import java.io.IOException;
import java.sql.SQLException;

class ParentService {
    void execute() throws IOException { }
}

class InvalidChildService extends ParentService {
    @Override
    void execute() throws IOException, SQLException { }
    // SQLException không được khai báo bởi ParentService.execute().
}
```

Khi override phương thức có `throws IOException`, implementation có thể:

- Giữ `throws IOException`.
- Khai báo loại hẹp hơn, ví dụ `throws FileNotFoundException`.
- Không khai báo checked exception nào.
- Vẫn có khả năng ném unchecked `RuntimeException`, không bị giới hạn giống checked exception.

Nhưng không thể thêm `SQLException` (không thuộc `IOException`) hoặc `Exception` rộng hơn hợp đồng cha. Quy tắc này áp dụng tương tự khi triển khai phương thức interface.

**Khác với overload:** hai overload có chữ ký tham số khác nhau có thể khai báo các checked exceptions hoàn toàn khác nhau. Giới hạn thu hẹp checked exceptions là quy tắc của **override**, không phải quy tắc chung cho mọi phương thức trùng tên.

## 8. Tập ngoại lệ checked mà compiler yêu cầu được xác định thế nào?

Khi compiler thấy lời gọi:

```java
service.load(argument);
```

nó phân tích các phần theo thứ tự:

1. Xác định kiểu tĩnh của `service` và `argument`.
2. Chọn khai báo overload theo JLS §15.12.
3. Xác định **các checked exception mà lời gọi này có thể ném** từ kiểu lời gọi/khai báo đã chọn theo JLS §11.2.1.
4. Phân tích các `try-catch` bao quanh, mệnh đề `throws` của phương thức chứa, các kiểu cha/con của ngoại lệ và các quy tắc unreachable checked `catch`.
5. Nếu một checked exception có thể thoát ra ngoài mà không được xử lý hoặc khai báo phù hợp, báo lỗi biên dịch.

Ví dụ với hai overload:

```java
void load(Object source) throws IOException { /* ... */ }

void load(String source) throws SQLException { /* ... */ }
```

Khi gọi `load("abc")`, overload `String` được chọn. Việc caller có sẵn `catch(IOException)` **không làm Java chọn phương thức `load(Object)`**.

Đây là đoạn **cố ý không biên dịch** khi đặt trong lớp có hai overload trên:

```java
try {
    load("abc");  // Overload String: declares SQLException.
} catch (IOException ex) {
    System.out.println("caught I/O");
}
```

Một vấn đề là `SQLException` checked chưa được bắt hoặc khai báo. Đồng thời, với một `try` không có khả năng ném `IOException` checked theo phân tích của Java, nhánh `catch(IOException)` không phù hợp và có thể bị compiler từ chối với chẩn đoán “never thrown”.

Cách đúng là bắt hoặc khai báo **SQLException** của overload String:

```java
try {
    load("abc");
} catch (SQLException ex) {
    System.out.println("caught SQL");
}
```

Đây là mẫu trong thân phương thức với import và chữ ký hai overload đã có; việc thay `IOException` bằng `SQLException` **không thay đổi overload được chọn**, chỉ đáp ứng đúng hợp đồng ngoại lệ.

## 9. Không phải `catch` nào cũng cần và không phải `catch` nào cũng hợp lệ

Compiler không yêu cầu bắt mọi ngoại lệ được liệt kê ở mọi overload cùng tên. Nó xem xét ngoại lệ có thể phát sinh từ **đường lời gọi đang được biên dịch**.

Một vài nguyên tắc:

| Tình huống | Nghĩa vụ |
| --- | --- |
| Đã chọn overload `load(String) throws SQLException` | Bắt hoặc khai báo `SQLException`, nếu nó có thể thoát |
| Overload khác là `load(Object) throws IOException` nhưng không được chọn | Không tự phát sinh nghĩa vụ `IOException` từ lời gọi String |
| Phương thức được chọn chỉ khai báo `RuntimeException` | Không có nghĩa vụ checked exception tương ứng |
| Một `catch` bắt kiểu cha như `Exception` | Có thể bao quát nhiều checked exception, nhưng hợp đồng rộng hơn và kém cụ thể |
| Một overload khai báo `throws IOException` dù implementation hiện tại không ném | Caller vẫn phải dựa trên hợp đồng compile-time |
| Một `try` chứa nhiều lời gọi tới các overload khác nhau | Tập ngoại lệ cần xử lý có thể là hợp của các kiểu checked từ mọi lời gọi đó |

Ví dụ có hai lời gọi:

```java
try {
    load((Object) "file");
    load("database");
} catch (IOException ex) {
    handleIo(ex);
} catch (SQLException ex) {
    handleDatabase(ex);
}
```

Đây là mẫu minh họa, giả định overload và hàm xử lý đã khai báo. Lời gọi đầu chọn `load(Object)` và có thể ném `IOException`; nếu nó hoàn tất bình thường, lời gọi thứ hai chọn `load(String)` và có thể ném `SQLException`. Hai nhánh `catch` là hợp lý vì **cả hai loại đều có thể xuất hiện từ thân `try`**.

**Đừng kiểm tra checked exceptions bằng việc chạy thử một nhánh dữ liệu duy nhất.** Compiler phân tích hợp đồng có thể xảy ra, không chỉ sự kiện đã quan sát ở lần chạy đó.

## 10. `null`: chọn overload cụ thể nhất hoặc gặp mơ hồ

Giá trị `null` không có lớp runtime để JVM dùng như một đối tượng thông thường. Trong overload resolution, nó có thể tương thích với nhiều kiểu tham chiếu và compiler chọn phiên bản cụ thể nhất khi có thể.

### Có quan hệ kế thừa: `String` cụ thể hơn `Object`

```java
static void show(Object x) throws IOException { /* ... */ }

static void show(String x) throws SQLException { /* ... */ }

show(null); // Chọn show(String), vì String cụ thể hơn Object.
```

Đây là đoạn minh họa trong một lớp với import phù hợp, không phải chương trình hoàn chỉnh. Nếu gọi trong phương thức thông thường, bên gọi phải xử lý hoặc khai báo `SQLException`. Việc biểu thức `null` không phải một `String` đang tồn tại không làm Java chọn `Object`.

### Không có quan hệ cụ thể hơn duy nhất: gọi mơ hồ

```java
import java.io.IOException;
import java.sql.SQLException;

class AmbiguousNullExample {
    static void show(String value) throws IOException { }

    static void show(Integer value) throws SQLException { }

    static void demo() {
        show(null); // Compile error: ambiguous method call.
    }
}
```

Đây là ví dụ **cố ý không biên dịch**. `String` và `Integer` không có quan hệ kiểu con/cha với nhau; cả hai overload đều áp dụng cho `null` nhưng không có lựa chọn cụ thể nhất duy nhất.

Hai cách sửa khi đó:

```java
show((String) null);  // Chọn overload String.
show((Integer) null); // Chọn overload Integer.
```

Đây là các dòng minh họa nằm trong lớp ví dụ nói trên, và phương thức chứa phải xử lý/khai báo `IOException` hoặc `SQLException` tương ứng.

**Không thể chữa lời gọi mơ hồ bằng cách thêm `catch(SQLException)` hoặc `throws IOException` ở ngoài.** Phải giải quyết chữ ký được chọn trước, sau đó mới kiểm tra checked exceptions.

## 11. Phân biệt widening, boxing và varargs khi overload

Lựa chọn overload còn phụ thuộc **pha áp dụng** chứ không chỉ vào mức độ gần nhau trong hệ phân cấp class. Với đối số số nguyên `int`, một overload nhận `long` có thể được chọn trong pha strict invocation trước một overload nhận `Integer` cần boxing.

Lưu thành `WideningVsBoxingDemo.java`:

```java
import java.io.IOException;
import java.sql.SQLException;

public class WideningVsBoxingDemo {
    static void process(long value) throws IOException {
        System.out.println("selected=long");
    }

    static void process(Integer value) throws SQLException {
        System.out.println("selected=Integer");
    }

    public static void main(String[] args) {
        try {
            process(7); // int -> long (primitive widening)
        } catch (IOException ex) {
            System.out.println("I/O");
        }

        Integer boxed = 7;
        try {
            process(boxed); // Integer overload is an exact match
        } catch (SQLException ex) {
            System.out.println("SQL");
        }
    }
}
```

```bash
javac -encoding UTF-8 WideningVsBoxingDemo.java
java WideningVsBoxingDemo
```

Kết quả:

```text
selected=long
selected=Integer
```

Cơ chế:

- `process(7)`: số literal có kiểu `int`; widening primitive sang `long` hợp lệ ở pha strict, nên Java không cần chuyển sang pha boxing để chọn `process(Integer)`. Nghĩa vụ checked tương ứng là `IOException`.
- `process(boxed)`: biểu thức có kiểu `Integer`, nên overload `Integer` là lựa chọn chính xác và có nghĩa vụ `SQLException`.

Varargs (`Type...`) được xét theo các quy tắc phù hợp của JLS, thường sau hai pha không cần variable arity. Đừng nhìn thấy một lời gọi có dấu `...` rồi mặc định nó luôn thắng bản nhận một tham số thường.

**Không sử dụng danh sách `throws` để dự đoán việc widening hay boxing.** Chọn overload xong mới xem hợp đồng ngoại lệ.

## 12. `var` và kiểu tĩnh được suy luận vẫn quyết định overload

Từ Java 10, `var` cho phép compiler suy luận kiểu biến cục bộ từ biểu thức khởi tạo. Điều này **không phải dynamic typing**: biến vẫn có một kiểu tĩnh xác định.

Ví dụ:

```java
var name = "alpha";   // compiler suy ra String
Object broad = name;  // kiểu khai báo Object

inspect(name);        // dùng overload inspect(String)
inspect(broad);       // dùng overload inspect(Object)
```

Đây là mẫu trong một phương thức, giả định hai overload ở phần 2 đã được khai báo và các checked exceptions được xử lý phù hợp.

Muốn chọn khác overload, bạn thay **kiểu biểu thức** qua cách khai báo hoặc ép kiểu đúng, chứ không chỉ đổi giá trị runtime của biến.

### Generic method cũng dựa trên bound ở vị trí gọi bên trong

Một tình huống dễ nhầm: một phương thức generic nhận `T` nhưng gọi một tên hàm đang có overload `Object` và `String`. Trong phần thân generic, `T` với bound mặc định `Object` **không tự biến thành `String`** chỉ vì một lần caller truyền String.

Lưu thành `GenericOverloadDemo.java`:

```java
import java.io.IOException;
import java.text.ParseException;

public class GenericOverloadDemo {
    static void print(Object value) throws IOException {
        System.out.println("overload=Object");
    }

    static void print(String value) throws ParseException {
        System.out.println("overload=String");
    }

    static <T> void forward(T value) throws IOException {
        print(value);
    }

    public static void main(String[] args) {
        try {
            print("hello");
        } catch (ParseException ex) {
            System.out.println("parse failure");
        }

        try {
            forward("hello");
        } catch (IOException ex) {
            System.out.println("I/O failure");
        }
    }
}
```

```bash
javac -encoding UTF-8 GenericOverloadDemo.java
java GenericOverloadDemo
```

Kết quả:

```text
overload=String
overload=Object
```

Trong `forward(T value)`, compiler phân tích `print(value)` theo kiểu `T` được giới hạn mặc định bởi `Object`. Nó không chuyên biệt hóa bytecode thành `print(String)` cho mỗi lần truyền tham số generic. Vì vậy `forward` khai báo `throws IOException`, không phải `throws ParseException`.

Nếu nhu cầu nghiệp vụ là xử lý riêng từng loại dữ liệu, hãy truyền chiến lược xử lý (callback/interface) hoặc sử dụng overload tại tầng có thông tin kiểu phù hợp, thay vì kỳ vọng generic call tự chọn overload theo lớp runtime.

## 13. Constructor overload cũng có những quy tắc tương tự

Constructor có thể overload theo danh sách tham số; compiler chọn constructor ở **compile-time** dựa trên kiểu biểu thức truyền vào. Từng constructor có thể khai báo checked exceptions khác nhau.

Lưu thành `ConstructorOverloadDemo.java`:

```java
import java.io.IOException;
import java.text.ParseException;

public class ConstructorOverloadDemo {
    static class Parser {
        Parser(Object source) throws IOException {
            System.out.println("constructor=Object");
        }

        Parser(String source) throws ParseException {
            System.out.println("constructor=String");
        }
    }

    public static void main(String[] args) {
        Object broad = "input";
        String narrow = "input";

        try {
            new Parser(broad);
        } catch (IOException ex) {
            System.out.println("I/O");
        }

        try {
            new Parser(narrow);
        } catch (ParseException ex) {
            System.out.println("parse");
        }
    }
}
```

```bash
javac -encoding UTF-8 ConstructorOverloadDemo.java
java ConstructorOverloadDemo
```

Kết quả:

```text
constructor=Object
constructor=String
```

Constructor không được override như instance method, nhưng nguyên tắc **chọn phiên bản có tham số phù hợp trước khi kiểm tra checked exceptions** vẫn tương tự. Nếu có nhiều overload constructor mơ hồ (ví dụ nhận `String` và `Integer` với đối số `null`), compiler cũng phải giải quyết mơ hồ chứ không dựa trên danh sách `throws` để chọn.

## 14. Giao diện và đa hình: chọn hợp đồng từ kiểu khai báo

Interface thường là nơi mô tả hợp đồng của một dịch vụ:

```java
import java.io.IOException;

interface ResourceService {
    void load(String key) throws IOException;
}
```

Implementation có thể thu hẹp checked exception:

```java
class CachedResourceService implements ResourceService {
    @Override
    public void load(String key) {
        System.out.println("cache=" + key);
    }
}
```

Đây là các khai báo minh họa trong cùng package có import phù hợp. Ở tầng gọi:

```java
ResourceService service = new CachedResourceService();

try {
    service.load("user-42");
} catch (IOException ex) {
    System.out.println("I/O failure");
}
```

Caller phải tuân thủ hợp đồng `ResourceService.load(String) throws IOException` theo kiểu khai báo của biến `service`, dù implementation hiện tại không ném checked exception. Nếu khai báo trực tiếp biến kiểu `CachedResourceService`, compiler có thể dựa vào chữ ký override hẹp hơn.

Điều đó giúp hệ thống thay implementation: một implementation khác có thể dùng I/O thực sự và phát sinh IOException, trong khi code caller dựa trên interface đã có cơ chế xử lý hợp lệ.

**Cẩn thận với API interface có nhiều overload:** những overload nào caller nhìn thấy cũng phụ thuộc interface, không phải mọi phương thức tồn tại trên implementation runtime.

## 15. Bắt lỗi ở đâu trong chuỗi gọi khi có overload?

Một tầng service có thể gọi các overload tùy ý nghĩa dữ liệu. Mỗi nhánh gọi có thể kéo theo các checked exceptions riêng:

```java
static void route(boolean fromFile) throws IOException, SQLException {
    if (fromFile) {
        load((Object) "file");
    } else {
        load("database");
    }
}
```

Đây là phương thức minh họa, giả định overload `load(Object) throws IOException` và `load(String) throws SQLException` đã được khai báo trong lớp phù hợp. Vì cả hai nhánh đều có thể được thực thi, `route` khai báo cả `IOException` và `SQLException` nếu không bắt ở trong.

Ở caller, có thể:

```java
try {
    route(sourceIsFile);
} catch (IOException ex) {
    handleIo(ex);
} catch (SQLException ex) {
    handleSql(ex);
}
```

Các tên `sourceIsFile`, `handleIo` và `handleSql` ở trên là **placeholder ngữ cảnh của một đoạn mã minh họa**, không phải lớp Java chạy độc lập. Mục đích là nhận ra **tập ngoại lệ checked của toàn phương thức** có thể là hợp của nhiều đường điều khiển; không phải caller chỉ cần bắt một ngoại lệ dựa trên dữ liệu của một lần chạy.

Nếu hai lỗi có cùng cách phục hồi, có thể dùng `catch (IOException | SQLException ex)` theo quy tắc multi-catch, miễn hành động chung có ý nghĩa nghiệp vụ. Nếu cần xử lý riêng, giữ hai `catch` tương ứng.

Không nên công bố `throws Exception` chỉ để tránh theo dõi loại ngoại lệ có thể xảy ra ở mỗi overload. Nó hợp lệ trong nhiều trường hợp nhưng làm hợp đồng kém rõ ràng và khiến caller khó quyết định chính sách xử lý.

## 16. Những điểm dễ gây lỗi biên dịch

| Tình huống | Vì sao sai? | Hướng sửa |
| --- | --- | --- |
| Viết hai phương thức khác nhau chỉ bởi `throws` | `throws` không phải yếu tố phân biệt overload | Đổi chữ ký tham số hoặc tách tên phương thức |
| Cho rằng runtime `String` trong biến `Object` sẽ chọn `load(String)` | Overload chọn theo kiểu tĩnh của biểu thức | Dùng kiểu đúng, ép kiểu có kiểm tra hoặc đổi API |
| Dùng `Parent ref = new Child(); ref.childOnlyOverload(...)` | Phương thức chỉ có ở Child không nhìn thấy qua Parent | Dùng hợp đồng phù hợp hoặc tham chiếu Child có chủ đích |
| `catch(IOException)` trong khi overload đã chọn chỉ khai báo `SQLException` checked | Compiler không đổi overload theo catch | Bắt/khai báo SQLException |
| `Child.override() throws Exception` trong khi Parent chỉ `throws IOException` | Override mở rộng checked exception | Thu hẹp hoặc bỏ loại checked mới |
| `show(null)` khi có `show(String)` và `show(Integer)` | Không có overload cụ thể nhất duy nhất | Ép kiểu có chủ đích hoặc đổi chữ ký |
| Cho rằng `var` là kiểu runtime tự thay đổi | `var` suy ra một kiểu tĩnh | Kiểm tra kiểu được suy ra |
| Cho rằng `T` generic sẽ chọn overload theo dữ liệu runtime | Compiler xử lý `T` theo type bound | Dùng overload ở tầng có kiểu cụ thể hoặc strategy |
| Chọn overload theo kiểu `catch` mà caller viết | `throws`/`catch` không tham gia overload resolution | Chọn phương thức trước, kiểm tra exception sau |
| Bắt mọi `Exception` để che lỗi thiết kế overload | Có thể bắt quá rộng và che lỗi | Bắt cụ thể các checked exceptions theo hợp đồng |
| Dùng `instanceof` để suy luận compiler sẽ chọn overload khác | `instanceof` runtime không viết lại lời gọi đã biên dịch | Phân nhánh rõ và gọi overload phù hợp nếu cần |
| Trông chờ override thay đổi những checked exception bắt buộc ở caller kiểu Parent | Caller được kiểm tra theo static type Parent | Dùng API public ổn định hoặc hẹp kiểu tham chiếu khi thích hợp |

## 17. Checklist phân tích một lời gọi bị báo lỗi checked exception

Khi gặp `unreported exception ... must be caught or declared to be thrown`:

1. **Viết rõ tên phương thức** đang gọi và những overload có thể nhìn thấy từ kiểu khai báo của receiver.
2. **Ghi kiểu tĩnh của mỗi đối số** — gồm literal, biến kiểu `Object`, `String`, biểu thức cast, `var` hoặc generic type variable.
3. **Xác định overload được chọn** theo khả năng áp dụng và độ cụ thể; nếu mơ hồ, giải quyết ambiguity trước.
4. **Đọc đúng `throws` của overload ấy**, không đọc tất cả `throws` của mọi phương thức cùng tên.
5. **Nếu có override**, phân biệt hợp đồng của chữ ký theo kiểu tĩnh với phần thân thực thi theo đối tượng runtime.
6. **Xét các lời gọi khác** trong cùng `try` hoặc phương thức có thể đóng góp loại checked exception khác.
7. **Xem quan hệ lớp ngoại lệ**: `IOException` có thể bao quát `FileNotFoundException`; các kiểu độc lập như `IOException` và `SQLException` cần xử lý riêng hoặc bằng lớp cha phù hợp.
8. **Chọn tầng xử lý**: có thể phục hồi ở đây thì `catch`; nếu không, khai báo `throws` hoặc chuyển thành ngoại lệ nghiệp vụ với cause rõ ràng.
9. **Kiểm tra hợp đồng interface/override**: không khai báo checked exception mới vượt quá quyền của lớp cha.
10. **Viết test compile-time và runtime riêng**: chạy thử chỉ cho biết overload/implementation và lỗi của dữ liệu thử, không thay việc compiler xác định nghĩa vụ checked.

## 18. Câu hỏi tự kiểm tra

**1.** `void read(Object) throws IOException` và `void read(String) throws SQLException`. Đối số `Object x = "abc"; read(x);` cần xử lý gì?  
**`IOException`**, vì kiểu tĩnh của `x` là Object nên chọn overload Object.

**2.** Với hai overload trên, `read("abc")` cần xử lý gì?  
**`SQLException`**, vì String là kiểu áp dụng cụ thể hơn.

**3.** Thêm `catch(IOException)` quanh `read("abc")` có khiến compiler chọn overload Object không?  
**Không.** Overload được chọn trước; phải xử lý hợp đồng SQLException đã được chọn.

**4.** `Parent p = new Child();`, Parent chỉ khai báo `execute(Object) throws IOException`, Child override nó và overload `execute(String) throws SQLException`. `p.execute("abc")` chọn gì?  
**Chọn chữ ký `execute(Object)`** theo Parent, và có thể chạy phần thân override Child.execute(Object). Caller phải xử lý IOException.

**5.** `Child c = new Child(); c.execute("abc")` thì sao?  
**Chọn overload `execute(String)`** nếu nó nhìn thấy và áp dụng cụ thể nhất; caller phải xử lý SQLException.

**6.** Có được viết hai overload chỉ khác `throws IOException` và `throws SQLException` không?  
**Không** nếu danh sách tham số giống nhau.

**7.** `show(String)` và `show(Integer)`, gọi `show(null)` có tự chọn kiểu có `throws` nhỏ hơn không?  
**Không.** Lời gọi mơ hồ; `throws` không giải quyết sự mơ hồ.

**8.** `Parent.fetch() throws IOException`, Child override `fetch()` không khai báo checked exceptions. Caller có biến Parent trỏ Child có còn phải xử lý IOException không?  
**Có.** Compiler kiểm tra theo Parent.fetch().

**9.** Generic method nhận `T` không giới hạn gì thêm và gọi `process(T)`, có tự chọn overload `process(String)` khi truyền String ở runtime không?  
**Không.** Phần thân được biên dịch theo type bound của T (mặc định Object).

**10.** Nếu một phương thức có hai đường gọi tới hai overload lần lượt `throws IOException` và `throws SQLException`, phương thức bao ngoài không bắt lỗi nên khai báo gì?  
Thông thường **`throws IOException, SQLException`** hoặc kiểu cha phù hợp. Nếu chính sách xử lý khác nhau, dùng `catch` ở tầng thích hợp.

## Tài liệu tham khảo chuẩn

- [Java Language Specification, Java SE 21 — §15.12 Method Invocation Expressions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.12): chọn overload ở compile-time và runtime invocation.
- [JLS 21 — §15.12.2 Choosing Method Signature](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.12.2): các pha áp dụng và phương thức cụ thể nhất.
- [JLS 21 — §11.2.1 Exception Analysis of Expressions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.2.1): tập ngoại lệ mà biểu thức lời gọi phương thức có thể ném.
- [JLS 21 — §11.2.3 Exception Checking](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.2.3): nghĩa vụ bắt/khai báo checked exception.
- [JLS 21 — §8.4.9 Overloading](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.4.9): overload và ý nghĩa của kiểu đối số lúc biên dịch.
- [JLS 21 — §8.4.8.3 Requirements in Overriding and Hiding](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.4.8.3): giới hạn checked exceptions khi override.
- [JLS 21 — §15.9 Class Instance Creation Expressions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.9): lựa chọn constructor overload.

**Ghi nhớ:** Java **chọn overload bằng kiểu tĩnh** của receiver/đối số và chữ ký phương thức tại compile-time; **kiểm tra checked exceptions theo overload đã chọn**; sau đó runtime dispatch mới có thể lựa chọn phần thân override. `throws` không giúp chọn overload và runtime type không âm thầm chuyển lời gọi sang một overload khác.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-inheritance-007">Checked exception của constructor lớp cha</a>

<details>
<summary>Xem chi tiết</summary>

# Checked exception của constructor lớp cha trong Java

## 1. Tình huống cần hiểu

Trong Java, khi tạo đối tượng của một lớp con, **constructor lớp cha phải được thực thi** như một phần của quá trình khởi tạo. Nếu constructor lớp cha khai báo một **checked exception** như `IOException`, constructor lớp con gọi nó phải tuân thủ quy tắc kiểm tra ngoại lệ. Đây là tình huống dễ bị nhầm với quy tắc `throws` của phương thức `@Override`.

Ví dụ:

```java
class Parent {
    Parent() throws java.io.IOException {
        throw new java.io.IOException("parent initialization failed");
    }
}

class Child extends Parent {
    Child() throws java.io.IOException {
        super();
    }
}
```

Đây là **hai class minh họa hợp lệ**, chưa có `main`. Lời gọi `super()` có thể truyền `IOException` ra ngoài, vì vậy `Child()` khai báo `throws IOException`. Khi caller viết `new Child()`, chính caller phải bắt hoặc khai báo checked exception.

Mục tiêu của bài:

- Hiểu chính xác thứ tự gọi constructor từ lớp cha đến lớp con.
- Biết kiểm tra checked exception ở lời gọi `super(...)` tường minh và `super()` ngầm định.
- Nhận ra vì sao constructor mặc định của lớp con có thể **không biên dịch** nếu constructor cha khai báo checked exception.
- Giải thích lý do không thể đặt `super(...)` vào một khối `try/catch` ngay trong constructor con ở **Java 17**.
- Dùng `throws`, lựa chọn overload của constructor cha hoặc thiết kế lại API khi cần.
- Phân biệt **constructor không bị override** với **phương thức override bị hạn chế checked exception**.
- Hiểu `this(...)` và chuỗi constructor qua nhiều lớp.
- Xác định cách caller xử lý lỗi, điều gì đã chạy trước khi khởi tạo thất bại và các lưu ý quản lý tài nguyên.
- Tránh nhầm lỗi biên dịch do `throws` với lỗi không có constructor cha phù hợp hoặc không có quyền truy cập.

Các chương trình đầy đủ trong bài tương thích Java 17+, có lệnh `javac`/`java` và `stdout` được mô tả tách bạch. Những đoạn mã **cố ý không biên dịch** được đánh dấu.

## 2. Constructor không phải phương thức có thể override

Trước hết cần phân biệt hai khái niệm:

- **Method overriding:** lớp con cung cấp implementation thay thế cho phương thức được kế thừa, có quan hệ hợp đồng về `throws` giữa phương thức cha và phương thức con.
- **Constructor chaining:** constructor lớp con gọi constructor lớp cha để khởi tạo phần đối tượng thuộc lớp cha; đây là một **lời gọi constructor**, không phải một lần override.

Ví dụ:

```java
class Parent {
    Parent() throws java.io.IOException {
    }

    void read() throws java.io.IOException {
    }
}

class Child extends Parent {
    Child() throws Exception { // HỢP LỆ VỀ THROWS CỦA CONSTRUCTOR.
        super();
    }

    @Override
    void read() throws Exception { // KHÔNG HỢP LỆ VỚI METHOD OVERRIDE.
    }
}
```

Đây là **mã minh họa lỗi biên dịch có chủ ý**, vì `Child.read()` khai báo `Exception` rộng hơn `IOException`. Riêng `Child() throws Exception` là hợp lệ về quan hệ constructor: `IOException` của `super()` là subtype của `Exception`, nên checked exception được xử lý bằng khai báo.

Vì sao hai kết quả khác nhau?

- Constructor không được thừa kế và không thể override; constructor con có **hợp đồng độc lập** đối với caller `new Child(...)`.
- Phương thức `read()` ở lớp con override một hợp đồng có thể được gọi qua kiểu tĩnh `Parent`. Java không cho thêm checked exception mới/rộng hơn vào phương thức override.
- Dù constructor con có thể khai báo `throws Exception`, trong API tốt bạn nên khai báo **kiểu hẹp, có ý nghĩa**, để caller không phải bắt `Exception` quá rộng.

**Quy tắc trọng tâm:** constructor con có thể khai báo checked exception bằng hoặc rộng hơn loại do `super(...)` yêu cầu; nhưng không được **bỏ qua** nghĩa vụ checked exception từ lời gọi constructor cha nếu không có cơ chế xử lý hợp lệ.

## 3. Thứ tự khởi tạo đối tượng: lớp cha trước lớp con

Khi `new Child(...)` thực hiện thành công, Java phải khởi tạo phần trạng thái lớp cha trước khi hoàn tất phần của lớp con. Một sơ đồ rút gọn:

```text
caller gọi new Child(...)
        |
        v
Child(...) bắt đầu chuỗi constructor
        |
        v
super(...) / this(...) theo ngữ nghĩa constructor
        |
        v
Parent(...) và các lớp tổ tiên cần thiết
        |
        v
khởi tạo instance field / instance initializer của Child
        |
        v
thân constructor Child
        |
        v
new Child(...) trả về tham chiếu
```

Sơ đồ không cố thể hiện chi tiết về cấp phát/giá trị mặc định trước khi chạy constructor. Điều quan trọng là nếu một constructor lớp cha kết thúc đột ngột bằng ngoại lệ, **phần khởi tạo tiếp theo của lớp con không chạy**.

### Chương trình chạy được: lỗi trong constructor cha chặn constructor con

Tạo tệp **`ParentConstructorFlowDemo.java`**:

```java
import java.io.IOException;

class Parent {
    Parent(boolean fail) throws IOException {
        System.out.println("parent start");
        if (fail) {
            throw new IOException("parent failed");
        }
        System.out.println("parent end");
    }
}

class Child extends Parent {
    private final int number = initializeNumber();

    private int initializeNumber() {
        System.out.println("child field initializer");
        return 7;
    }

    Child(boolean fail) throws IOException {
        super(fail);
        System.out.println("child constructor body");
    }

    int number() {
        return number;
    }
}

public class ParentConstructorFlowDemo {
    public static void main(String[] args) {
        try {
            new Child(true);
        } catch (IOException failure) {
            System.out.println("caught=" + failure.getMessage());
        }

        System.out.println("---");

        try {
            Child child = new Child(false);
            System.out.println("result=" + child.number());
        } catch (IOException failure) {
            System.out.println("unexpected=" + failure.getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac ParentConstructorFlowDemo.java
java ParentConstructorFlowDemo
```

`stdout` chính xác:

```text
parent start
caught=parent failed
---
parent start
parent end
child field initializer
child constructor body
result=7
```

Giải thích đường thất bại:

1. Caller gọi `new Child(true)`.
2. `Child(boolean)` gọi `super(true)`.
3. `Parent(boolean)` in `parent start` và ném `IOException("parent failed")`.
4. Constructor cha không hoàn thành bình thường, nên **initializer của field `number` và thân constructor con chưa chạy**.
5. Đối tượng `Child` không được trả về cho caller; `catch (IOException)` ở caller xử lý lỗi.

Đường thành công: constructor cha hoàn thành, sau đó initializer và thân constructor con thực thi theo thứ tự, rồi caller nhận đối tượng `Child` và đọc `number() == 7`.

**Ghi chú:** một lỗi constructor **không tự rollback** những side effect đã xảy ra trước khi ném, ví dụ ghi log, tạo file hoặc thao tác bên ngoài. Nếu constructor mở tài nguyên rồi thất bại, việc cleanup cần được thiết kế tại phạm vi đã sở hữu tài nguyên.

## 4. `super(...)` ném checked exception: constructor con làm gì?

Một constructor lớp con phải gọi constructor của lớp cha, trực tiếp hoặc gián tiếp qua `this(...)`. Khi lời gọi constructor cha có khả năng ném checked exception, constructor con cần khai báo một loại phù hợp trong `throws`, trừ khi đường thiết kế được thay đổi để không gọi constructor cha có hợp đồng ấy.

Ví dụ cơ bản:

```java
import java.io.IOException;

class ConfigBase {
    ConfigBase() throws IOException {
        // Có thể đọc cấu hình.
    }
}

class ConfigChild extends ConfigBase {
    ConfigChild() throws IOException {
        super();
    }
}
```

Đây là **mảnh khai báo class hợp lệ**, chưa có `main`.

Khi viết `new ConfigChild()`, caller phải có `try/catch` hoặc khai báo `throws IOException`. Lỗi checked có thể xuất hiện dù thân `ConfigChild()` không thực hiện thao tác I/O nào sau `super()`: **lời gọi constructor cha đã đủ tạo nghĩa vụ**.

### 4.1. Khai báo rộng hơn thì sao?

Về mặt ngôn ngữ, constructor con này cũng hợp lệ:

```java
class ConfigChild extends ConfigBase {
    ConfigChild() throws Exception {
        super();
    }
}
```

Đây là mảnh thay thế, **không đặt đồng thời với class cùng tên ở trên** khi biên dịch. Vì `IOException` kế thừa `Exception`, khai báo `throws Exception` bao phủ lỗi từ `super()`. Nhưng caller phải xét hợp đồng `Exception` rộng hơn và có thể bị buộc bắt nhiều hơn cần thiết.

Nếu constructor con có các bước riêng cũng ném checked exception, nó có thể liệt kê nhiều kiểu:

```java
class ConfigChild extends ConfigBase {
    ConfigChild() throws IOException, java.text.ParseException {
        super();
        parseOwnState();
    }

    private void parseOwnState() throws java.text.ParseException {
        throw new java.text.ParseException("invalid child config", 0);
    }
}
```

Đây là mảnh class minh họa, cần khai báo `import java.io.IOException` hoặc dùng tên đủ; `IOException` được hiểu là `java.io.IOException`. `ParseException` phát sinh **từ logic lớp con**, không nhất thiết từ constructor cha. Constructor con có thể bổ sung checked exception của chính nó vì **không phải method override**.

### 4.2. Subclass không được khai báo checked exception không liên quan rồi bỏ quên lỗi cha

Đoạn sau **cố ý không biên dịch**:

```java
class Parent {
    Parent() throws java.io.IOException {
    }
}

class Child extends Parent {
    Child() throws java.sql.SQLException {
        super(); // IOException không được SQLException bao phủ.
    }
}
```

Compiler từ chối vì `SQLException` và `IOException` là hai checked exception khác nhánh. Chỉ khai báo một kiểu checked khác không hoàn thành nghĩa vụ đối với `super()`.

Sửa bằng `throws IOException` hoặc `throws IOException, SQLException` nếu thân constructor con còn có đường ném `SQLException` phù hợp. Không thể xem “đã có `throws` rồi” là đủ.

## 5. `super()` ngầm định: lỗi dễ bị bỏ sót nhất

Trong Java 17, nếu constructor lớp con không bắt đầu bằng lời gọi constructor hợp lệ tường minh như `this(...)` hoặc `super(...)`, compiler sẽ ngầm chèn lời gọi `super()` phù hợp.

Ví dụ:

```java
class Parent {
    Parent() throws java.io.IOException {
    }
}

class Child extends Parent {
    Child() {
        System.out.println("child");
    }
}
```

Đây là **mã cố ý không biên dịch**. Mặc dù không nhìn thấy từ khóa `super`, ý nghĩa constructor con tương đương:

```java
Child() {
    super(); // Ngầm định và có thể ném IOException.
    System.out.println("child");
}
```

Vì `Child()` không khai báo `throws IOException`, javac báo lỗi checked exception không được xử lý/khai báo tại constructor. Không phải do `System.out.println`.

**Sửa:**

```java
class Child extends Parent {
    Child() throws java.io.IOException {
        super(); // Có thể bỏ dòng này; super() vẫn được gọi ngầm.
        System.out.println("child");
    }
}
```

Ở đây các đoạn class `Child` là các **phương án thay thế**, không nên đặt nhiều khai báo cùng tên trong cùng scope.

### `super()` ngầm định khác với không có constructor không tham số

Có ít nhất **hai lỗi biên dịch khác nhau**:

1. Constructor cha không tham số **tồn tại** nhưng khai báo checked exception → constructor con cần `throws` phù hợp.
2. Lớp cha **không có constructor không tham số truy cập được** → `super()` ngầm định **không khớp lời gọi**, dù thêm `throws Exception` cũng không sửa được. Phải gọi `super(arguments)` để chọn constructor hợp lệ của cha.

Ví dụ trường hợp 2:

```java
class Parent {
    Parent(String name) {
    }
}

class Child extends Parent {
    Child() {
        // Không có Parent() nên super() ngầm định không hợp lệ.
    }
}
```

Đây là **mã cố ý không biên dịch**. Sửa thành:

```java
class Child extends Parent {
    Child() {
        super("default");
    }
}
```

**Quan trọng:** không được nhầm “không có default constructor” với “default constructor ném checked exception”. Hai lỗi cần hai cách giải quyết khác nhau.

## 6. Constructor **mặc định** của lớp con cũng có thể không biên dịch

Nếu lớp con **không viết constructor nào**, Java có cơ chế tạo default constructor. Với lớp thông thường, default constructor này gọi `super()` và **không tự suy ra danh sách checked exception để thêm vào `throws`**.

Ví dụ **cố ý không biên dịch**:

```java
class Parent {
    Parent() throws java.io.IOException {
    }
}

class Child extends Parent {
    // Không khai báo constructor.
    // Default Child() phải gọi Parent(), nhưng IOException chưa khai báo.
}
```

Điều này đôi khi gây ngạc nhiên: “Tôi chưa hề khai báo `Child()`, tại sao compiler báo lỗi ở lớp con?” Vì constructor mặc định không thể thực hiện lời gọi `super()` có checked exception mà nó không khai báo.

Cách sửa là **khai báo constructor con tường minh**:

```java
class Child extends Parent {
    Child() throws java.io.IOException {
        super();
    }
}
```

Nếu constructor cha không tham số không tồn tại hoặc không truy cập được, cách sửa còn phải chọn đúng constructor cha, không chỉ thêm `throws`.

### Mẫu thử dễ hiểu

- Viết lớp cha có `Parent() throws IOException`.
- Viết lớp con trống.
- Biên dịch → lỗi liên quan default constructor/checked exception.
- Viết `Child() throws IOException { super(); }`.
- Biên dịch lại → hợp lệ nếu không có lỗi khác.

Các đoạn này là **mảnh class thay thế** để học một quy tắc; không dán tất cả phiên bản `Child` cùng tên vào một file.

## 7. Vì sao không thể bọc `super(...)` trong `try/catch` của constructor con ở Java 17?

Một phản xạ tự nhiên là thử:

```java
class Child extends Parent {
    Child() {
        try {
            super();
        } catch (java.io.IOException failure) {
            System.out.println("fallback");
        }
    }
}
```

Đây là **mã cố ý không biên dịch theo Java 17**. Lời gọi constructor cha `super(...)` không được đặt tùy ý bên trong một khối `try` trong phần thân constructor con. Ở Java 17, explicit constructor invocation phải đứng ở vị trí đầu của constructor; không thể dùng một `try/catch` bao quanh lời gọi `super(...)` để “bắt ngay trong constructor con”.

Ngay cả khi bạn đặt một `try/catch` **sau** `super()` thì nó chỉ bắt ngoại lệ từ các thao tác được thực thi trong `try` sau đó, không bao quanh lời gọi constructor cha đã xảy ra:

```java
class Child extends Parent {
    Child() throws java.io.IOException {
        super();

        try {
            doChildWork();
        } catch (java.io.IOException failure) {
            System.out.println("child work failed");
        }
    }

    private void doChildWork() throws java.io.IOException {
        throw new java.io.IOException("child operation");
    }
}
```

Đây là class minh họa hợp lệ khi `Parent()` khai báo `throws IOException`. Constructor con **vẫn** phải khai báo `throws IOException` cho `super()`.

### Nếu thật sự cần phục hồi từ lỗi của constructor cha

Bạn cần **thay đổi ranh giới xử lý** chứ không bọc trực tiếp `super()` trong constructor con:

- **Caller** bắt checked exception khi gọi `new Child(...)`.
- **Lớp cha** tự xử lý lỗi nội bộ hoặc cung cấp một constructor khác với hợp đồng phù hợp.
- **Thiết kế lại việc khởi tạo**: nếu có thể, tách hoạt động có thể thất bại sang một factory hoặc phương thức khởi tạo có chính sách lỗi riêng, thay vì bắt buộc constructor cha phải ném checked exception.
- **Chọn overload constructor cha khác** nếu semantics và hợp đồng cho phép.

Đừng tạo object giả thành công nếu phần khởi tạo bắt buộc của lớp cha thất bại. Nếu constructor cha ném, quá trình tạo đối tượng lớp con không hoàn tất bình thường.

**Phạm vi phiên bản:** giải thích cú pháp `super(...)` ở đây dành cho Java 17. Các JDK đời sau có thể mở rộng cấu trúc thân constructor qua tính năng ngôn ngữ mới; khi làm bài Java 17 hãy tuân thủ quy tắc phiên bản đó.

## 8. Chọn một overload constructor cha không ném checked exception

Một lớp cha có nhiều constructor với hợp đồng khác nhau. Lớp con được chọn một overload hợp lệ bằng `super(arguments)`:

```java
class Parent {
    Parent() throws java.io.IOException {
        throw new java.io.IOException("default failed");
    }

    Parent(String preset) {
        System.out.println("parent preset=" + preset);
    }
}
```

Nếu lớp con gọi `super()`, nó phải xử lý/khai báo `IOException`. Nếu gọi `super("cached")`, overload được chọn **không khai báo checked exception**, nên lớp con không phải khai báo `IOException` chỉ vì **overload khác** của lớp cha có `throws`.

### Chương trình chạy được: chọn constructor cha an toàn

Tạo tệp **`OverloadedSuperConstructorDemo.java`**:

```java
import java.io.IOException;

class Base {
    Base() throws IOException {
        throw new IOException("default path unavailable");
    }

    Base(String mode) {
        System.out.println("base mode=" + mode);
    }
}

class Derived extends Base {
    Derived() {
        super("cached");
        System.out.println("derived ready");
    }
}

public class OverloadedSuperConstructorDemo {
    public static void main(String[] args) {
        new Derived();
        System.out.println("done");
    }
}
```

```bash
javac OverloadedSuperConstructorDemo.java
java OverloadedSuperConstructorDemo
```

`stdout`:

```text
base mode=cached
derived ready
done
```

Giải thích: `Derived()` gọi overload `Base(String)`. Overload `Base() throws IOException` không được thực thi trên đường này. Vì vậy `Derived()` không cần `throws IOException`.

**Không lạm dụng:** chỉ chọn overload khác nếu nó thật sự thiết lập được các bất biến mà đối tượng cần. Đổi từ khởi tạo bắt buộc sang “cached” chỉ để thoát khỏi checked exception có thể tạo đối tượng không đúng trạng thái.

## 9. `this(...)`: ngoại lệ đi qua chuỗi constructor của chính lớp con

`this(arguments)` dùng để gọi một constructor khác trong **cùng lớp**. Một constructor trong chuỗi cuối cùng phải dẫn đến constructor cha phù hợp. Checked exception từ constructor được gọi qua `this(...)` cũng phải được xử lý theo quy tắc compile-time.

Ví dụ:

```java
class Child extends Parent {
    Child() throws java.io.IOException {
        this("sample");
    }

    Child(String name) throws java.io.IOException {
        super();
        System.out.println(name);
    }
}
```

Đây là mảnh class minh họa khi `Parent() throws IOException`. Constructor không tham số **không trực tiếp gọi `super()`**, nhưng nó gọi `this("sample")`; constructor được ủy quyền sẽ gọi `super()` và có thể ném `IOException`. Vì vậy cả chuỗi phải bảo đảm checked exception được khai báo tại từng lời gọi không được bắt.

### Ví dụ chạy được: truyền lỗi qua `this(...)`

Tạo tệp **`ThisConstructorChainDemo.java`**:

```java
import java.io.IOException;

class Parent {
    Parent(boolean fail) throws IOException {
        System.out.println("parent");
        if (fail) {
            throw new IOException("parent failed");
        }
    }
}

class Child extends Parent {
    Child(boolean fail) throws IOException {
        this("delegated", fail);
        System.out.println("outer child");
    }

    Child(String name, boolean fail) throws IOException {
        super(fail);
        System.out.println("inner child: " + name);
    }
}

public class ThisConstructorChainDemo {
    public static void main(String[] args) {
        try {
            new Child(false);
        } catch (IOException failure) {
            System.out.println("unexpected");
        }

        System.out.println("---");

        try {
            new Child(true);
        } catch (IOException failure) {
            System.out.println("caught=" + failure.getMessage());
        }
    }
}
```

```bash
javac ThisConstructorChainDemo.java
java ThisConstructorChainDemo
```

`stdout`:

```text
parent
inner child: delegated
outer child
---
parent
caught=parent failed
```

Ở đường thành công, thứ tự là constructor cha → phần còn lại của constructor nhận `(String, boolean)` → phần còn lại của constructor nhận `(boolean)`. Ở đường lỗi, constructor cha ném trước, nên các dòng `inner child` và `outer child` của đường thứ hai không xuất hiện.

Nếu constructor con gọi `this(...)` mà **không** khai báo checked exception mà constructor đích có thể ném, compiler sẽ báo lỗi tại constructor gọi, dù constructor con không trực tiếp viết `super()`.

### Không thể tạo vòng `this(...)`

`Child() { this(1); }` và `Child(int n) { this(); }` nếu chỉ gọi lẫn nhau sẽ là **recursive constructor invocation** không hợp lệ. Có thêm `throws` không giải quyết được vì cần một đường thực sự khởi tạo lớp cha và hoàn tất chuỗi constructor.

## 10. Chuỗi nhiều cấp kế thừa: root → middle → leaf

Đối với nhiều tầng:

```text
Root() throws IOException
     |
     v
Middle() throws IOException
     |
     v
Leaf() throws IOException
     |
     v
new Leaf() tại caller
```

Mỗi constructor gọi lên cấp cao hơn có thể phải khai báo checked exception tương thích. Nếu một tầng không thể thay đổi constructor gốc, caller vẫn phải chuẩn bị cho exception cuối cùng được công bố bởi constructor được chọn của tầng thấp nhất.

### Chương trình chạy được: ba cấp constructor

Tạo file **`ThreeLevelConstructorDemo.java`**:

```java
import java.io.IOException;

class Root {
    Root(boolean fail) throws IOException {
        System.out.println("root");
        if (fail) {
            throw new IOException("root failed");
        }
    }
}

class Middle extends Root {
    Middle(boolean fail) throws IOException {
        super(fail);
        System.out.println("middle");
    }
}

class Leaf extends Middle {
    Leaf(boolean fail) throws IOException {
        super(fail);
        System.out.println("leaf");
    }
}

public class ThreeLevelConstructorDemo {
    public static void main(String[] args) {
        try {
            new Leaf(false);
        } catch (IOException failure) {
            System.out.println("unexpected");
        }

        System.out.println("---");

        try {
            new Leaf(true);
        } catch (IOException failure) {
            System.out.println("caught=" + failure.getMessage());
        }
    }
}
```

```bash
javac ThreeLevelConstructorDemo.java
java ThreeLevelConstructorDemo
```

`stdout`:

```text
root
middle
leaf
---
root
caught=root failed
```

Khi root lỗi, **không có dòng middle/leaf** ở lượt khởi tạo thất bại. Đối tượng Leaf chưa hoàn tất tạo, không được trả về cho caller.

Điều này cũng áp dụng nếu `Root` là abstract class có constructor: abstract class không được khởi tạo trực tiếp bằng `new Root()`, nhưng constructor của nó **vẫn được thực thi** khi tạo subclass cụ thể. Abstract constructor có `throws IOException` cũng tạo nghĩa vụ kiểm tra ngoại lệ cho constructor lớp con.

## 11. Checked exception từ **biểu thức đối số** của `super(...)`

Lỗi checked không chỉ đến từ **thân constructor cha**. Khi đánh giá biểu thức dùng làm đối số của `super(arguments)` hoặc `this(arguments)`, một phương thức phụ có thể ném checked exception, và constructor đang gọi cũng phải xử lý/khai báo.

Ví dụ:

```java
class Parent {
    Parent(String value) {
        System.out.println("parent=" + value);
    }
}

class Child extends Parent {
    private static String loadValue() throws java.io.IOException {
        throw new java.io.IOException("cannot prepare argument");
    }

    Child() throws java.io.IOException {
        super(loadValue());
    }
}
```

Đây là **mảnh class hợp lệ**. `Parent(String)` không khai báo checked exception, nhưng `loadValue()` được gọi **để tạo đối số** cho constructor cha và có `throws IOException`. Vì thế `Child()` vẫn cần khai báo checked `IOException`.

### Ví dụ chạy được: đối số thất bại trước khi constructor cha được gọi

Tạo file **`SuperArgumentFailureDemo.java`**:

```java
import java.io.IOException;

class Base {
    Base(String value) {
        System.out.println("base=" + value);
    }
}

class Derived extends Base {
    private static String prepare(boolean fail) throws IOException {
        System.out.println("prepare argument");
        if (fail) {
            throw new IOException("argument failed");
        }
        return "ready";
    }

    Derived(boolean fail) throws IOException {
        super(prepare(fail));
        System.out.println("derived body");
    }
}

public class SuperArgumentFailureDemo {
    public static void main(String[] args) {
        try {
            new Derived(true);
        } catch (IOException failure) {
            System.out.println("caught=" + failure.getMessage());
        }
        System.out.println("---");
        try {
            new Derived(false);
        } catch (IOException failure) {
            System.out.println("unexpected");
        }
    }
}
```

```bash
javac SuperArgumentFailureDemo.java
java SuperArgumentFailureDemo
```

`stdout`:

```text
prepare argument
caught=argument failed
---
prepare argument
base=ready
derived body
```

Trong đường lỗi, `prepare(true)` ném trước khi lời gọi `Base(String)` thực sự thực hiện thân của constructor cha, nên **không in `base=...`**. Đây là một khác biệt quan trọng: trong phần phân tích checked exception, phải xem cả constructor được gọi lẫn **các biểu thức tính toán đối số**.

Trong Java 17, tránh dùng trực tiếp `this` hoặc thành viên instance chưa hợp lệ trong explicit constructor invocation. Phương thức `prepare` của ví dụ là `static` để không phụ thuộc vào trạng thái instance chưa khởi tạo.

## 12. Constructor lớp con có được thêm checked exception của chính mình không?

**Có.** Constructor không phải override, vì vậy không có quy tắc “lớp con chỉ được giữ hoặc thu hẹp throws của lớp cha” như với phương thức ghi đè.

Nếu `Parent()` không ném checked exception, `Child()` có thể khai báo `throws IOException` và ném nó từ chính phần thân:

```java
class Parent {
    Parent() {
    }
}

class Child extends Parent {
    Child(boolean fail) throws java.io.IOException {
        super();
        if (fail) {
            throw new java.io.IOException("child setup failed");
        }
    }
}
```

Đây là class minh họa hợp lệ. Caller `new Child(true)` phải xử lý/khai báo `IOException` theo chữ ký của `Child(boolean)`, dù `Parent()` hoàn toàn không ném checked exception.

So sánh với phương thức override:

```java
class Parent {
    void load() {
    }
}

class Child extends Parent {
    @Override
    void load() throws java.io.IOException {
    }
}
```

Đây là **mã cố ý không biên dịch**, vì phương thức `Parent.load()` không cho phép checked `IOException`. Như vậy “constructor con mở rộng checked exception được, phương thức override không được” là khác biệt cần nhớ chính xác.

### Thiết kế hợp đồng constructor

Bên gọi chỉ có thể tạo một object thành công nếu toàn bộ chuỗi khởi tạo cần thiết hoàn tất. Vì vậy, constructor con nên khai báo các checked exception **thực sự có ý nghĩa** cho quá trình khởi tạo. Khai báo `throws Exception` rộng tùy tiện làm caller khó phân loại lỗi và có thể tạo ra code xử lý quá tổng quát.

## 13. Caller phải bắt hoặc khai báo checked exception khi `new`

Nếu constructor `Child(...)` có `throws IOException`, biểu thức `new Child(...)` được kiểm tra tương tự các lời gọi có checked exception khác.

Hai cách hợp lệ:

**Cách A — caller bắt:**

```java
try {
    Child child = new Child(true);
    System.out.println("created");
} catch (java.io.IOException failure) {
    System.out.println("construction failed: " + failure.getMessage());
}
```

**Cách B — caller khai báo:**

```java
public static void main(String[] args)
        throws java.io.IOException {
    Child child = new Child(false);
    System.out.println("created");
}
```

Hai đoạn là **mảnh mã minh họa**: cần có class `Child` và method/`main` thích hợp.

**Trường hợp C — không bắt, không khai báo** trong method bình thường sẽ bị compiler từ chối:

```java
static void start() {
    Child child = new Child(true); // Không xử lý IOException.
}
```

Đây là mảnh **cố ý không biên dịch**, giả định `Child(boolean) throws IOException`.

### Caller dùng biến kiểu cha thì sao?

Với:

```java
Parent value = new Child(true);
```

Quy tắc checked exception của biểu thức `new Child(true)` phụ thuộc vào **constructor của Child được chọn**, không phải chỉ vào kiểu biến `Parent` sẽ nhận tham chiếu. Kiểu tĩnh của biến nhận không làm mất nghĩa vụ xử lý lỗi khởi tạo của constructor con.

Khi object **đã tạo thành công**, các lời gọi phương thức thông qua `Parent` lại dùng hợp đồng checked exception của phương thức được chọn qua **kiểu tĩnh** `Parent`. Đây là hai thao tác khác nhau: `new` để xây object và method invocation trên object đã tồn tại.

## 14. Constructor thất bại và quản lý tài nguyên

Một ngoại lệ từ `super(...)` khiến construction không hoàn tất. Tuy nhiên, **những tài nguyên đã được mở trước khi ném** có thể còn tồn tại nếu không được quản lý.

### 14.1. Constructor cha cần tự bảo toàn cleanup của chính mình

Nếu constructor cha mở tài nguyên A, sau đó bước khởi tạo tiếp theo thất bại, caller không có tham chiếu tới object được xây xong để gọi `close()`. Constructor hoặc factory phụ trách tài nguyên A cần cleanup trên đường lỗi.

Ví dụ **mảnh logic**:

```java
Resource opened = openFirst();
try {
    initializeFurther(opened); // Có thể thất bại.
} catch (Exception failure) {
    try {
        opened.close();
    } catch (Exception cleanup) {
        failure.addSuppressed(cleanup);
    }
    throw failure;
}
```

Đây là **mảnh minh họa** cần khai báo `Resource`, các phương thức và kiểm tra checked exception đầy đủ. Mục đích là cho thấy nguyên nhân gốc không nên bị lỗi close che mất. Khi resource thực thi `AutoCloseable` và quyền sở hữu phù hợp, `try-with-resources` thường dễ đúng hơn cleanup thủ công.

### 14.2. Resource được tạo trong header TWR trước một constructor con thất bại

Tạo file **`ConstructorWithResourcesDemo.java`**:

```java
import java.io.IOException;

class TraceResource implements AutoCloseable {
    TraceResource() {
        System.out.println("open first");
    }

    @Override
    public void close() {
        System.out.println("close first");
    }
}

class Parent {
    Parent(boolean fail) throws IOException {
        System.out.println("parent constructor");
        if (fail) {
            throw new IOException("constructor failed");
        }
    }
}

class Child extends Parent implements AutoCloseable {
    Child(boolean fail) throws IOException {
        super(fail);
        System.out.println("child constructor");
    }

    @Override
    public void close() {
        System.out.println("close child");
    }
}

public class ConstructorWithResourcesDemo {
    public static void main(String[] args) {
        try (TraceResource first = new TraceResource();
             Child child = new Child(true)) {
            System.out.println("body");
        } catch (IOException failure) {
            System.out.println("caught=" + failure.getMessage());
        }
    }
}
```

```bash
javac ConstructorWithResourcesDemo.java
java ConstructorWithResourcesDemo
```

`stdout`:

```text
open first
parent constructor
close first
caught=constructor failed
```

Ở đây:

1. `TraceResource first` được khởi tạo thành công trong header TWR.
2. Khi khởi tạo `Child child`, constructor `Parent` ném `IOException`.
3. Vì `Child` chưa tạo thành công, **không có đối tượng `Child` được gắn vào resource slot để gọi `close()`**; không xuất hiện dòng `close child`.
4. Resource `first` đã được quản lý trong header nên được đóng, và dòng `close first` xuất hiện.
5. Caller nhận checked exception từ quá trình tạo `Child`.

Nếu `first.close()` cũng ném, lỗi đóng có thể trở thành **suppressed exception** gắn vào lỗi chính do constructor ném. `try-with-resources` không thể tự đóng các tài nguyên mà chính constructor cha đã tạo nội bộ rồi làm mất tham chiếu; constructor/factory phải thiết kế cleanup riêng.

### 14.3. Không gọi phương thức trên object chưa được tạo thành công

Nếu `new Child(...)` ném, phép gán thông thường `Child child = new Child(...)` không hoàn tất và chương trình không có một đối tượng `Child` được trả lại cho caller. Không có phương thức `child.close()` hợp lệ trên kết quả xây dựng thất bại đó. Hãy đặt trách nhiệm cleanup nơi tài nguyên được sở hữu và đã có thể truy cập.

## 15. Những trường hợp dễ nhầm với ngoại lệ constructor cha

### 15.1. Lỗi không có constructor `super()` phù hợp

Nếu cha chỉ có `Parent(int)`, lớp con phải gọi `super(intValue)`. Thêm `throws IOException` không tạo ra constructor không tham số.

### 15.2. Lỗi quyền truy cập constructor cha

Nếu constructor cha là `private` và lớp con ở class khác không có quyền gọi, lỗi là **accessibility**, không phải checked exception. Cần chọn constructor truy cập được hoặc thay đổi thiết kế lớp cha.

### 15.3. Constructor trong abstract class

Abstract class vẫn có constructor. Khi tạo lớp con cụ thể, constructor abstract parent vẫn được gọi và checked exception vẫn phải được xử lý/khai báo.

### 15.4. Interface không có constructor

Lớp implements interface không gọi constructor của interface: interface không có instance constructor. Nó vẫn gọi constructor của **lớp cha thực sự** (thường là `Object` nếu không có `extends` class cụ thể).

### 15.5. Constructor không thể bị `@Override`

Đặt `@Override` trên constructor là sai. Tên constructor trùng tên lớp, không có kiểu trả về. Quy tắc giới hạn checked `throws` khi override method không áp lên constructor.

### 15.6. Ngoại lệ từ instance initializer lớp con

Nếu constructor cha hoàn thành, các instance field initializer và instance initializer của lớp con chạy **trước thân constructor con**. Chúng có thể làm construction thất bại, nhưng đó không phải là lỗi do constructor cha ném. Quy tắc checked exception của initializer có những ràng buộc riêng trong JLS; không nên suy đoán rằng mọi checked exception trong initializer đều được ném tùy ý mà không cần xét toàn bộ constructors của lớp.

### 15.7. Lỗi runtime không khai báo

Constructor cha có thể ném `RuntimeException` hoặc `Error` dù không khai báo `throws`. Những kiểu này là unchecked, nên compiler không bắt constructor con khai báo chúng. Tuy nhiên, chúng vẫn có thể làm quá trình tạo object thất bại tại runtime.

### 15.8. Thông điệp lỗi compiler

Thông báo như “unreported exception ...; must be caught or declared to be thrown” là dấu hiệu checked exception chưa được xử lý. Thông báo “constructor ... cannot be applied to given types” hoặc “... has private access” liên quan overload/access. Đọc loại lỗi thực tế, không thêm `throws Exception` một cách máy móc.

## 16. Sơ đồ quyết định khi subclass không biên dịch

```text
Child constructor có lỗi tại super()/implicit super()
                     |
                     v
Parent có constructor tương ứng, truy cập được?
         |                                 |
        KHÔNG                             CÓ
         |                                 |
         v                                 v
Chọn đúng super(args) hoặc       Constructor cha / biểu thức
sửa API/access của Parent        đối số có checked exception?
                                           |
                               +-----------+-----------+
                               |                       |
                              CÓ                      KHÔNG
                               |                       |
                               v                       v
                         Child phải khai báo       Kiểm tra lỗi cú pháp
                         checked tương thích       hoặc logic khác
                         trong throws hoặc
                         chọn đường khởi tạo
                         hợp lệ khác
                               |
                               v
                         Caller new Child(...)
                         bắt/khai báo checked
```

Khi constructor con còn tự gọi `this(...)`, hãy lần theo chuỗi ủy quyền để tìm constructor cuối cùng gọi `super(...)`; kiểm tra checked exception ở từng lời gọi.

### Quy trình sửa an toàn

1. Xác nhận lỗi phát sinh từ `super()` ngầm định, `super(arguments)` tường minh, `this(arguments)` hay biểu thức đối số.
2. Tra **chữ ký constructor thực sự được chọn** (không suy đoán theo tên class).
3. Kiểm tra constructor cha có truy cập được và tham số khớp hay không.
4. Liệt kê checked exception từ constructor được gọi và từ các biểu thức đối số.
5. Khai báo kiểu checked phù hợp trên constructor gọi, hoặc dùng một overload/thiết kế khởi tạo khác có ngữ nghĩa đúng.
6. Lần theo caller `new Child(...)` để bắt hoặc khai báo checked exception.
7. Kiểm tra đường thất bại: phần khởi tạo nào đã chạy, resource nào đã tạo, ai phải cleanup.
8. Biên dịch lại và kiểm tra kết quả theo đường thành công và đường thất bại.

## 17. Bảng tổng hợp về hợp đồng `throws`

| Tình huống | Hợp lệ theo Java 17? | Vì sao |
| --- | --- | --- |
| `Parent() throws IOException`; `Child() throws IOException { super(); }` | **Có** | Child khai báo đúng checked exception từ super |
| `Parent() throws IOException`; `Child() throws Exception { super(); }` | **Có** | Exception bao phủ IOException nhưng hợp đồng rộng |
| `Parent() throws IOException`; `Child() { super(); }` | **Không** | Bỏ nghĩa vụ checked exception |
| `Parent() throws IOException`; `Child() throws SQLException { super(); }` | **Không** | SQLException khác nhánh, không bao phủ IOException |
| `Parent() throws IOException`; `class Child extends Parent { }` | **Không** trong tình huống nêu | Default Child() không tự khai báo throws |
| Cha chỉ có `Parent(String)`; Child viết `Child() { }` | **Không** | super() ngầm định không có overload phù hợp |
| Cha có `Parent(String)` không throws; Child dùng `super("A")` | **Có** | Overload được gọi không khai báo checked |
| Cha không ném checked; Child constructor tự `throws IOException` | **Có** | Constructor con không override constructor cha |
| Parent method không ném checked; override method thêm `throws IOException` | **Không** | Quy tắc checked exception của method override |
| Constructor con gọi `this(...)` có `throws IOException` nhưng không khai báo | **Không** | Lời gọi constructor được ủy quyền có checked |
| Constructor cha không ném checked, nhưng `super(loadValue())` với `loadValue() throws IOException` | Cần `throws` tương ứng | Lỗi đến từ đánh giá đối số |
| `try { super(); } catch (IOException e) { ... }` trong Child() | **Không** ở Java 17 | Không thể bọc explicit constructor invocation theo cách này |
| `new Child()` khi Child() throws IOException trong caller không catch/throws | **Không** | Caller phải xử lý checked exception |
| Parent constructor ném `IllegalStateException`, Child không khai báo throws | **Có** về kiểm tra unchecked | RuntimeException không bắt buộc khai báo |

Bảng đánh giá **riêng những quy tắc nêu ra**; những ví dụ viết tắt giả định class, quyền truy cập và các khai báo khác phù hợp. Một đoạn hoàn chỉnh còn phải biên dịch về tham số, field, package và class names.

## 18. Những hiểu nhầm phổ biến

**“Constructor của lớp con override constructor lớp cha.”** Sai. Constructor không được kế thừa/override; lời gọi `super(...)` là constructor chaining, không phải dynamic dispatch của method.

**“Vì override không được mở rộng checked exception, Child constructor cũng không được thêm `SQLException` nếu Parent constructor chỉ throws IOException.”** Sai về quy tắc constructor. Child có thể khai báo thêm checked exception riêng vì constructor không override. Nhưng nó **vẫn phải bao phủ IOException** nếu gọi constructor cha có `throws IOException`.

**“Không viết `super()` nghĩa là không có checked exception của cha.”** Sai. Java 17 chèn `super()` ngầm định khi constructor không ủy quyền hay gọi cha tường minh.

**“Lớp con không khai báo constructor nên compiler sẽ tạo default constructor với đủ `throws`.”** Sai. Default constructor không tự suy ra `throws IOException` để đáp ứng constructor cha.

**“Có thể bọc `super()` trong `try/catch` của Child để xử lý lỗi ngay.”** Không hợp lệ ở Java 17. Cần xử lý ở caller, thay đổi API/constructor cha hoặc chọn overload phù hợp.

**“`throws SQLException` trên Child đủ vì đó cũng là checked exception.”** Sai nếu lời gọi `super()` có `throws IOException` mà SQLException không bao phủ.

**“Nếu Parent() có `throws IOException` nhưng thân rỗng, Child không cần khai báo.”** Sai. Kiểm tra dựa trên hợp đồng phương thức/constructor được gọi, không chỉ theo một lần chạy thực tế.

**“Constructor con kết thúc thân trước rồi mới gọi constructor cha.”** Sai. Constructor cha được thực hiện trước phần initializer và thân constructor con trong các ví dụ thông thường.

**“Parent constructor lỗi nhưng Child vẫn tạo thành công với field mặc định.”** Sai. Nếu lỗi thoát khỏi construction, biểu thức `new Child(...)` không hoàn tất bình thường.

**“Lỗi `super(load())` nhất định đến từ constructor cha.”** Không chắc. `load()` trong biểu thức đối số có thể ném trước khi thân constructor cha chạy.

**“Constructor abstract class không được gọi.”** Sai. Constructor của abstract parent vẫn là một mắt xích khi tạo subclass cụ thể.

**“Khi viết `Parent variable = new Child()`, compiler chỉ xét constructor của Parent.”** Sai. Nghĩa vụ checked exception của biểu thức `new Child()` dựa trên constructor Child được chọn.

**“Thêm `throws Exception` giải quyết mọi lỗi liên quan `super()`.”** Sai. Nó không sửa được thiếu overload, private constructor, sai kiểu tham số hoặc lỗi chuỗi `this(...)` đệ quy.

**“Vì Child construction thất bại, mọi tài nguyên Parent đã mở đều được rollback tự động.”** Sai. Java không tự rollback side effects hoặc đóng resource nội bộ không được quản lý. Cần thiết kế cleanup.

**“`try-with-resources` sẽ gọi `close()` trên Child ngay cả khi `new Child()` ném trước khi khởi tạo xong.”** Sai. Resource slot Child chưa hoàn tất khởi tạo nên chưa được đóng như một resource thành công; chỉ các resource trước đó đã được quản lý sẽ được đóng theo quy tắc.

**“Unchecked exception không thể làm constructor thất bại nếu không khai báo `throws`.”** Sai. `RuntimeException` và `Error` vẫn có thể phát sinh tại runtime.

## 19. Câu hỏi tự kiểm tra

1. **Parent() throws IOException, Child() gọi super() không ghi throws: hợp lệ không?** Không.
2. **Child() throws IOException { super(); } có hợp lệ không?** Có, nếu constructor cha được chọn là `Parent() throws IOException` và các điều kiện khác hợp lệ.
3. **Child() throws Exception thay IOException có hợp lệ về constructor không?** Có, nhưng rộng hơn cần thiết.
4. **Child() chỉ throws SQLException có bao phủ IOException của super không?** Không.
5. **Class Child không có constructor mà Parent() throws IOException: có vấn đề gì?** Default constructor ngầm định không khai báo checked exception, nên không biên dịch.
6. **Nếu Parent không có constructor không tham số, chỉ thêm throws có sửa được lỗi?** Không; cần gọi đúng overload `super(arguments)`.
7. **Vì sao không thể `try { super(); } catch ...` trong Child Java 17?** Explicit constructor invocation có ràng buộc vị trí, không thể được đặt trong try như vậy.
8. **Nếu có Parent(String) không throws, Child gọi super("preset") thì sao?** Không cần checked handler cho IOException của overload Parent() khác.
9. **Child() tự ném IOException nhưng Parent() không throws: được không?** Được, vì constructor không override.
10. **Parent method không throws, Child override method throws IOException: được không?** Không, vì method override không được thêm checked exception.
11. **Constructor Child(boolean) gọi this(String, boolean) throws IOException: Child(boolean) có cần throws không?** Có, nếu không có cơ chế xử lý hợp lệ khác; ở Java 17 không được bọc this(...) trong try.
12. **Parent constructor thực thi rồi ném trước khi khởi tạo field của Child: field initializer Child có chạy không?** Không.
13. **Lỗi từ super(load()) có thể do đâu?** Từ `load()` khi đánh giá đối số hoặc từ constructor cha được gọi.
14. **Caller new Child() khi Child() throws IOException phải làm gì?** Bắt hoặc khai báo IOException tương thích.
15. **Gán kết quả new Child() vào biến Parent có xóa nghĩa vụ IOException không?** Không.
16. **Nếu Parent là abstract class, constructor nó có chạy lúc tạo Child không?** Có.
17. **Parent() ném RuntimeException không khai báo; Child phải thêm throws RuntimeException?** Không bắt buộc vì unchecked.
18. **Nếu constructor cha thất bại, caller có object Child hoàn chỉnh để đóng không?** Không.
19. **Nếu resource thứ nhất trong TWR tạo xong, constructor resource thứ hai ném: resource thứ nhất có được đóng không?** Có, theo quy tắc TWR.
20. **Có nên tự thêm throws Exception vào mọi subclass constructor để khỏi kiểm tra?** Không; chọn hợp đồng cụ thể giúp caller xử lý đúng và phát hiện lỗi thiết kế.

## 20. Tài liệu chính thức Java 17

- [Java Language Specification 17 — `8.8 Constructors](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.8): cấu trúc constructor, `throws`, explicit constructor invocation và default constructor.
- [Java Language Specification 17 — `8.8.7.1 Explicit Constructor Invocations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.8.7.1): quy tắc `this(...)`, `super(...)` và các giới hạn của vị trí lời gọi trong Java 17.
- [Java Language Specification 17 — `8.8.9 Default Constructor](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.8.9): constructor ngầm định khi class không khai báo constructor.
- [Java Language Specification 17 — `11.2 Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2): nghĩa vụ khai báo/bắt checked exception trong việc gọi constructor.
- [Java Language Specification 17 — `12.5 Creation of New Class Instances](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.5): quá trình tạo instance qua các cấp kế thừa.
- [Java Language Specification 17 — `8.4.8.3 Requirements in Overriding and Hiding](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.8.3): so sánh giới hạn `throws` của phương thức override với constructor.
- [Java Language Specification 17 — `14.20.3 try-with-resources](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3): cleanup tài nguyên đã được khởi tạo trong header nếu resource sau khởi tạo thất bại.
- [Java SE 17 API — IOException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/IOException.html): checked exception thường gặp trong constructor thực hiện I/O.

**Ghi nhớ:** Constructor của lớp con không ghi đè constructor lớp cha: nó **gọi constructor cha** thông qua `super(...)` trực tiếp hoặc qua chuỗi `this(...)`. Nếu constructor cha hoặc biểu thức đối số có checked exception, constructor con cần **khai báo loại tương thích**, hoặc chọn một đường khởi tạo khác có hợp đồng đúng. `super()` có thể được chèn ngầm; default constructor lớp con không tự kế thừa `throws` của cha. Caller tạo đối tượng phải bắt/khai báo checked exception từ constructor được chọn, và nếu construction thất bại, các tài nguyên đã mở vẫn cần cleanup ở đúng phạm vi sở hữu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-inheritance-008">Checked exception trong instance initializer và các constructor</a>

<details>
<summary>Xem chi tiết</summary>

# Checked exception trong instance initializer và các constructor của Java

Trong Java, một đối tượng có thể thực hiện việc khởi tạo **trước khi thân constructor của lớp đó chạy**: các biểu thức khởi tạo instance field và các **instance initializer block** (`{ ... }` ở cấp lớp). Nếu những đoạn khởi tạo này có thể ném **checked exception**, Java áp dụng một quy tắc đặc biệt: đối với **lớp có tên (named class)**, phải có ít nhất một constructor được khai báo tường minh, và **tất cả constructor được khai báo tường minh của lớp** phải có mệnh đề `throws` bao phủ các checked exception có thể phát sinh từ instance initializer. Riêng **anonymous class** có cơ chế ngoại lệ đặc biệt vì lập trình viên không thể tự khai báo constructor của anonymous class.

Quy tắc dễ bị bỏ sót:

~~~java
import java.io.IOException;

class Config {
    String data = read(); // read() throws IOException.

    Config() throws IOException {
        // Hợp lệ nếu read() đã được định nghĩa phù hợp.
    }

    static String read() throws IOException {
        throw new IOException("không đọc được");
    }
}
~~~

Với lớp trên, lỗi `IOException` có thể được ném **trong quá trình tạo `new Config()`**, trước thân `Config()`. Vì thế constructor phải công bố nó, để caller của `new Config()` có thể `catch` hoặc `throws` theo quy tắc checked exception.

Bài này trình bày đầy đủ quan hệ giữa **instance field initializer, instance initializer block, constructor, constructor chaining và caller**; phân biệt với **static initializer**, giải thích trường hợp **nhiều constructor**, **constructor mặc định**, **anonymous class**, thứ tự chạy và xử lý thất bại khi khởi tạo. Các chương trình chạy được đã được kiểm tra với mức ngôn ngữ Java 17.

## 1. Bốn vị trí mã dễ bị nhầm là cùng một constructor

Xét một lớp đơn giản:

~~~java
class Profile {
    // 1. Instance field initializer:
    String name = loadName();

    // 2. Instance initializer block:
    {
        verifyProfile();
    }

    // 3. Constructor body:
    Profile() {
        System.out.println("Constructor body");
    }

    // 4. Static initializer block:
    static {
        System.out.println("Class initialization");
    }

    static String loadName() {
        return "guest";
    }

    void verifyProfile() {
        // Kiểm tra trạng thái.
    }
}
~~~

Đây là **lớp minh họa**, có thể biên dịch khi được đặt trong tệp Java phù hợp. Bốn thành phần làm những việc khác nhau:

| Thành phần | Thời điểm/đối tượng | Có thể nằm trong đường khởi tạo mỗi instance? |
| --- | --- | --- |
| Instance field initializer: `String name = loadName()` | Khởi tạo field trên mỗi đối tượng, theo thứ tự văn bản tương ứng | Có |
| Instance initializer block: `{ verifyProfile(); }` | Chạy khi khởi tạo từng instance, xen kẽ theo thứ tự văn bản với instance field initializers | Có |
| Constructor body: `Profile() { ... }` | Thực hiện phần thân constructor được gọi, sau giai đoạn khởi tạo instance của lớp đó, trừ trường hợp chuyển tiếp `this(...)` được giải thích sau | Có |
| Static initializer: `static { ... }` | Khởi tạo **class**, không phải chạy mỗi khi tạo đối tượng | Không, bản chất khác |

Một lớp có thể có nhiều instance initializer block, nhiều instance field có initializer, và nhiều constructor overload. Đối với một lượt khởi tạo instance bình thường, **các instance field initializer và instance initializer block của cùng lớp chạy theo thứ tự xuất hiện trong mã nguồn**.

Nếu đoạn ở cấp instance có thể ném một checked exception, trách nhiệm công bố nó không nằm ở “chữ ký của block” (block không có chữ ký), mà được kiểm tra thông qua **toàn bộ constructor của named class** theo JLS §11.2.3.

## 2. Checked exception và quy tắc catch-or-declare

Checked exception là các loại `Throwable` **không** thuộc nhánh `RuntimeException` hoặc `Error`. Các kiểu thường gặp:

- `IOException`, `FileNotFoundException`: lỗi I/O có thể cần được caller xử lý.
- `SQLException`: lỗi khi truy cập qua API cơ sở dữ liệu.
- `ClassNotFoundException`: không tìm thấy lớp trong một cơ chế nạp lớp.
- `InterruptedException`: thao tác bị gián đoạn.
- Các lớp do ứng dụng định nghĩa `extends Exception` mà không thuộc `RuntimeException`.

Ngược lại, `IllegalStateException`, `IllegalArgumentException`, `NullPointerException`, `Error` và các lớp con của nhánh unchecked **không bị compiler yêu cầu bắt hoặc khai báo** theo cùng quy tắc.

Đối với constructor:

~~~java
import java.io.IOException;

class Client {
    Client() throws IOException {
        if (System.currentTimeMillis() < 0) {
            throw new IOException("lỗi khởi tạo");
        }
    }
}
~~~

`Client()` được phép công bố checked exception trong `throws`. Khi caller viết:

~~~java
try {
    Client client = new Client();
    System.out.println("Tạo thành công");
} catch (IOException ex) {
    System.out.println("Không tạo được: " + ex.getMessage());
}
~~~

compiler có thể xác minh caller đã xử lý loại checked mà constructor cho phép truyền ra. Điều đó đúng **dù lỗi xuất hiện trong thân constructor, trong lời gọi `super(...)`, trong lời gọi `this(...)` hay trong instance initializer được thực hiện khi tạo đối tượng**.

Không được hiểu `throws IOException` là lệnh phát sinh lỗi. `throws` chỉ **công bố khả năng checked exception thoát ra**, còn `throw` hoặc lời gọi một API thất bại mới thực sự tạo đường thực thi bất thường.

## 3. Quy tắc đặc biệt của named class theo JLS §11.2.3

Nếu một **instance variable initializer** hoặc **instance initializer block** của **named class** *có thể ném* checked exception loại E theo phân tích compile time, Java yêu cầu đồng thời:

1. Named class có **ít nhất một constructor được khai báo tường minh** trong mã nguồn.
2. **Mỗi constructor được khai báo tường minh** của named class có mệnh đề `throws` ghi rõ E **hoặc một superclass bao phủ E**.
3. Các đường thực thi trong phần thân constructor cũng thỏa quy tắc checked exception thông thường; gọi `super(...)` và `this(...)` không miễn trừ điều này.

Ví dụ với E = `FileNotFoundException`:

~~~java
import java.io.FileNotFoundException;
import java.io.IOException;

class Document {
    String content = load();

    Document() throws IOException { }             // Bao phủ E.
    Document(int id) throws FileNotFoundException { } // Chính E.

    static String load() throws FileNotFoundException {
        throw new FileNotFoundException("thiếu tài liệu");
    }
}
~~~

Ở đây cả hai constructor đều công bố được `FileNotFoundException`, trực tiếp hoặc bằng superclass `IOException`. Xét riêng quy tắc checked exception trong initializer, ví dụ này hợp lệ.

**Không nhất thiết mọi constructor phải khai báo đúng cùng một chuỗi `throws IOException`.** Điều kiện là **mỗi checked exception có thể thoát từ initializer được một kiểu trong `throws` của từng constructor bao phủ**.

Nếu initializer có thể phát sinh `IOException` **và** `SQLException`, mọi constructor cần công bố cả hai loại hoặc những superclass phù hợp bao phủ chúng. Có thể viết `throws Exception`, nhưng với public API việc khai báo quá rộng thường làm caller khó xác định chiến lược xử lý.

### Một constructor thiếu `throws` làm cả lớp không hợp lệ

~~~java
import java.io.IOException;

// CỐ Ý SAI:
class OneMissingThrowsBad {
    String value = fetch();

    OneMissingThrowsBad() throws IOException { }
    OneMissingThrowsBad(int mode) { } // Không công bố IOException.

    static String fetch() throws IOException {
        throw new IOException();
    }
}
~~~

Dù constructor không tham số đã công bố `IOException`, constructor nhận `int` vẫn không thỏa quy tắc. Thậm chí nếu lập trình viên định “chỉ dùng constructor đầu”, **compiler vẫn kiểm tra tất cả constructor đã khai báo**.

Chạy `javac OneMissingThrowsBad.java` sẽ thất bại với lỗi checked exception chưa được bắt/khai báo; cách trình bày vị trí lỗi có thể trỏ vào `fetch()` trong initializer.

## 4. Tại sao constructor mặc định không đủ?

Nếu một named class **không viết bất kỳ constructor nào**, Java có thể cung cấp một **default constructor** theo quy tắc ngôn ngữ. Nhưng default constructor này **không thể được lập trình viên gắn một mệnh đề `throws IOException` vào chính khai báo ngầm**.

Vì vậy:

~~~java
import java.io.IOException;

// CỐ Ý SAI:
class NoConstructorBad {
    String value = fetch(); // Có thể ném checked IOException.

    static String fetch() throws IOException {
        throw new IOException();
    }
}
~~~

là lỗi biên dịch. Không thể giải quyết bằng cách trông cậy vào constructor mặc định.

Một cách sửa:

~~~java
import java.io.IOException;

class HasConstructor {
    String value = fetch();

    HasConstructor() throws IOException { }

    static String fetch() throws IOException {
        return "ready";
    }
}
~~~

Khi gọi `new HasConstructor()`, caller phải xử lý hoặc khai báo `IOException` theo chữ ký constructor, mặc dù `fetch()` trong ví dụ sửa luôn trả giá trị ổn định khi chạy. Compiler áp dụng phân tích khả năng ném dựa trên chữ ký `fetch() throws IOException`, không chứng minh mọi nhánh runtime bên trong phương thức đó sẽ luôn thành công.

**Cách sửa khác:** xử lý checked exception ngay trong initializer bằng `try/catch`, nếu có một giá trị dự phòng hay đường khôi phục hợp lệ. Khi checked exception không còn có thể thoát khỏi initializer, không nhất thiết phải thêm `throws` cho constructor chỉ vì initializer ấy.

## 5. `instance initializer` không được khai báo `throws` riêng

Block cấp instance là một phần của class body:

~~~java
import java.io.IOException;

class Example {
    {
        initialize();
    }

    Example() throws IOException { }

    static void initialize() throws IOException {
        // Có thể ném IOException.
    }
}
~~~

Đoạn trên hợp lệ vì constructor khai báo `throws IOException`. Không tồn tại cú pháp Java hợp lệ kiểu:

~~~java
// CỐ Ý SAI: instance initializer block không có chữ ký throws.
{
    throws IOException;
    initialize();
}
~~~

Bạn không thể gắn `throws` vào header của initializer block; đó không phải phương thức hoặc constructor. Vì vậy nơi công bố checked exception là các constructor, còn nơi **xử lý ngay** checked exception có thể là `try/catch` bên trong block.

Lưu ý khác: một instance initializer block của Java phải **có khả năng hoàn tất bình thường**, theo quy tắc riêng về khả năng đến được cuối block. Viết một block chỉ có `throw new IOException(...);` vô điều kiện làm nó không thể hoàn tất bình thường; lỗi này **khác** với lỗi thiếu `throws` ở constructor. Nếu muốn tạo đường thất bại có điều kiện, sử dụng câu lệnh điều kiện hoặc phương thức có thể ném, như những ví dụ chạy được bên dưới.

## 6. Thứ tự thực thi: superclass, instance initializers, constructor body

Khi tạo một đối tượng của named class thông thường, luồng khởi tạo cần được hiểu theo các tầng, không đơn giản là “constructor chạy trước mọi initializer”.

Một mô hình thường gặp:

~~~text
new Child(...)
    │
    ├─ Đánh giá các argument và vào constructor liên quan
    ├─ Gọi constructor khác theo chuỗi this(...) / super(...)
    ├─ Hoàn thành khởi tạo superclass phù hợp
    ├─ Thực hiện instance field initializers và instance initializer
    │  blocks của Child theo thứ tự văn bản
    └─ Thực hiện các câu lệnh còn lại trong thân constructor của Child
~~~

**Chi tiết:** Java cũng có những bước cấp phát, gán mặc định field, xử lý constructor invocation và quy tắc về outer instance. Sơ đồ trên chỉ nhấn vào phần quan trọng cho bài học: **instance initializer của một lớp được chạy trước phần thân thực hiện chính của constructor lớp đó, sau khi constructor cấp trên và các lời gọi chuyển tiếp liên quan đã được thực hiện đúng quy tắc**.

Nếu một instance field initializer ném `IOException`:

- Các initializer phía sau của lớp đó **không chạy**.
- Phần thân constructor của lớp đó **không chạy**.
- Đường tạo `new Child(...)` kết thúc bất thường; caller có thể bắt checked exception.
- Những side effect đã xảy ra trước điểm ném **không tự được rollback**.
- Đối tượng chưa được tạo thành công không được trả về từ biểu thức `new`.

Đừng nhầm ngoại lệ khi khởi tạo instance với thất bại của **static class initialization**; chúng có quy tắc kiểm tra và cách báo lỗi khác nhau.

## 7. Chương trình Java 17 hoàn chỉnh: lỗi ở field và block trước thân constructor

Tệp `NamedInitializerDemo.java`:

~~~java
import java.io.IOException;

public class NamedInitializerDemo {
    static boolean failField;
    static boolean failBlock;
    final String value = loadField();

    static String loadField() throws IOException {
        System.out.println("Khởi tạo field");
        if (failField) throw new IOException("field lỗi");
        return "ready";
    }

    {
        System.out.println("Khối khởi tạo");
        if (failBlock) throw new IOException("block lỗi");
    }

    NamedInitializerDemo() throws IOException {
        System.out.println("Thân constructor rỗng");
    }

    NamedInitializerDemo(String label) throws IOException {
        this();
        System.out.println("Thân constructor có tham số: " + label);
    }

    public static void main(String[] args) {
        try {
            System.out.println("Giá trị: " + new NamedInitializerDemo("A").value);
        } catch (IOException ex) {
            System.out.println("Lỗi: " + ex.getMessage());
        }

        failField = true;
        try {
            new NamedInitializerDemo();
        } catch (IOException ex) {
            System.out.println("Lỗi: " + ex.getMessage());
        }

        failField = false;
        failBlock = true;
        try {
            new NamedInitializerDemo();
        } catch (IOException ex) {
            System.out.println("Lỗi: " + ex.getMessage());
        }
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 NamedInitializerDemo.java
java NamedInitializerDemo
~~~

**Stdout chính xác:**

~~~text
Khởi tạo field
Khối khởi tạo
Thân constructor rỗng
Thân constructor có tham số: A
Giá trị: ready
Khởi tạo field
Lỗi: field lỗi
Khởi tạo field
Khối khởi tạo
Lỗi: block lỗi
~~~

### Phân tích ba lượt tạo đối tượng

**Lượt 1 — thành công:** `failField=false`, `failBlock=false`.

1. `value = loadField()` chạy và in `Khởi tạo field`.
2. Instance initializer block in `Khối khởi tạo`.
3. Constructor `NamedInitializerDemo()` được gọi qua `this()` và in `Thân constructor rỗng`.
4. Phần còn lại của `NamedInitializerDemo(String)` in `Thân constructor có tham số: A`.
5. Biểu thức `new ...` hoàn tất; caller đọc `value` là `ready`.

**Lượt 2 — lỗi field initializer:** `failField=true`.

1. `loadField()` in `Khởi tạo field` rồi ném checked `IOException("field lỗi")`.
2. Block phía sau không chạy, và không có dòng nào từ thân constructor.
3. Caller bắt `IOException` và in `Lỗi: field lỗi`.

**Lượt 3 — lỗi instance initializer block:** `failField=false`, `failBlock=true`.

1. Field initializer hoàn tất bình thường.
2. Block in `Khối khởi tạo` rồi ném `IOException("block lỗi")`.
3. Thân constructor không chạy; caller bắt và in `Lỗi: block lỗi`.

Cả hai constructor của named class đều khai báo `throws IOException`. Đó là điều kiện để compiler chấp nhận các checked exception có thể phát sinh từ cả field initializer và block.

### Tác động của `this()` đối với lượt khởi tạo

Constructor nhận `String` dùng `this()` để chuyển tới constructor không tham số. Chuỗi gọi constructor **không khiến instance field initializers và instance initializer block của cùng đối tượng chạy hai lần**. Chúng được thực hiện một lượt trong quá trình khởi tạo lớp đó, trước khi các phần thân constructor hoàn tất theo thứ tự chuyển tiếp.

Vì constructor `NamedInitializerDemo(String)` gọi `this()`, nó cũng có đường nhận `IOException` từ constructor đích. Do đó cả chính sách **khai báo ở mọi constructor vì initializer** lẫn nghĩa vụ **xử lý/khai báo checked exception từ lời gọi `this()`** đều cần được tuân thủ.

## 8. Kiểm tra nhiều constructor và constructor chaining

Nếu chỉ có một constructor, quy tắc đơn giản:

~~~java
class Single {
    String data = load();

    Single() throws java.io.IOException { }

    static String load() throws java.io.IOException {
        return "OK";
    }
}
~~~

Với hai constructor overload:

~~~java
class Multiple {
    String data = load();

    Multiple() throws java.io.IOException { }

    Multiple(int mode) throws java.io.IOException {
        this();
        // Tiếp tục sau khi constructor đích hoàn tất.
    }

    static String load() throws java.io.IOException {
        return "OK";
    }
}
~~~

Cả hai đều khai báo `IOException`. Nếu `Multiple(int)` không khai báo, compiler vẫn có thể báo lỗi **từ initializer** và/hoặc **lời gọi `this()`**. Không được cho rằng constructor chuyển tiếp không cần `throws` vì toàn bộ code khởi tạo đã “nằm trong constructor khác”.

Một lớp có thể dùng cả constructor gọi `super(...)` và một constructor gọi `this(...)`. **Mỗi constructor** của named class có initializer có thể ném checked exception vẫn phải đáp ứng điều kiện khai báo của JLS.

Nếu superclass constructor cũng công bố một checked exception khác, constructor lớp con còn phải xử lý hoặc khai báo **các loại checked thoát từ `super(...)`**. Trách nhiệm đó là quy tắc constructor invocation thông thường, ngoài quy tắc riêng cho initializer của lớp con.

## 9. Caller của `new` chịu hợp đồng constructor

Giả sử:

~~~java
class DocumentReader {
    String content = openDocument();

    DocumentReader() throws java.io.IOException { }

    static String openDocument() throws java.io.IOException {
        return "text";
    }
}
~~~

Caller:

~~~java
try {
    DocumentReader reader = new DocumentReader();
    System.out.println(reader.content);
} catch (java.io.IOException ex) {
    System.err.println("Không tạo được reader: " + ex.getMessage());
}
~~~

`IOException` có thể được truyền tới caller từ **instance initializer**, dù constructor rỗng. Đây là lý do chữ ký constructor không chỉ mô tả các câu lệnh **viết trực tiếp bên trong dấu ngoặc nhọn constructor**. Nó còn mô tả những lỗi checked có thể phát sinh khi **hoàn tất thao tác tạo đối tượng**.

Nếu caller không bắt và không có `throws IOException` thích hợp, compiler báo lỗi. Bản thân `new DocumentReader()` là một **class instance creation expression** có thể ném checked exception từ constructor đã chọn.

Khi thư viện thay đổi và bắt đầu tạo thêm tài nguyên trong instance field initializer, việc bổ sung checked exception vào constructor public có thể ảnh hưởng **source compatibility** của client. Do đó không nên chuyển tùy tiện một thao tác I/O có thể thất bại vào instance initializer mà chưa xem xét hợp đồng tạo đối tượng.

## 10. Cách sửa khi không muốn constructor khai báo checked exception

Có thể **xử lý lỗi ngay trong initializer**, với đường khôi phục thật sự hợp lệ. Đây không phải “né compiler” bằng cách bỏ lỗi; phải quyết định trạng thái thay thế có an toàn hay không.

Chương trình `HandledInitializerDemo.java`:

~~~java
import java.io.IOException;

public class HandledInitializerDemo {
    final String state;

    static String fetch() throws IOException {
        throw new IOException("nguồn không sẵn");
    }

    {
        String result;
        try {
            result = fetch();
        } catch (IOException ex) {
            System.out.println("Đã xử lý: " + ex.getMessage());
            result = "fallback";
        }
        state = result;
    }

    HandledInitializerDemo() {
        System.out.println("Constructor: " + state);
    }

    public static void main(String[] args) {
        System.out.println("Kết quả: " + new HandledInitializerDemo().state);
    }
}
~~~

Lệnh:

~~~bash
javac -encoding UTF-8 HandledInitializerDemo.java
java HandledInitializerDemo
~~~

Stdout:

~~~text
Đã xử lý: nguồn không sẵn
Constructor: fallback
Kết quả: fallback
~~~

Ở đây `fetch()` ném checked `IOException`, nhưng `try/catch` trong instance initializer **bắt và giải quyết nó bằng giá trị dự phòng**. Vì initializer không còn để `IOException` thoát ra, constructor `HandledInitializerDemo()` không cần `throws IOException` chỉ vì `fetch()`.

**Lưu ý:** ví dụ giả định chuỗi `"fallback"` là trạng thái hợp lệ. Trong ứng dụng thực, nếu không thể hoạt động thiếu dữ liệu khởi tạo, không nên nuốt `IOException` rồi tạo một đối tượng trông như thành công. Khi không có đường phục hồi, hãy giữ nguyên checked exception hoặc bọc nó thành một exception có ý nghĩa đúng với hợp đồng.

## 11. Có nên chuyển checked exception thành unchecked exception trong initializer?

Đôi khi một API không muốn để public constructor công bố checked exception mà chọn bọc lỗi bằng unchecked exception:

~~~java
class ConfigurationUnavailableException extends RuntimeException {
    ConfigurationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

class Configuration {
    final String content;

    {
        String value;
        try {
            value = readConfiguration();
        } catch (java.io.IOException ex) {
            throw new ConfigurationUnavailableException(
                    "Không khởi tạo được cấu hình", ex);
        }
        content = value;
    }

    Configuration() { }

    static String readConfiguration() throws java.io.IOException {
        return "mode=prod";
    }
}
~~~

Đây là **mã minh họa gồm các lớp tương ứng**, hợp lệ với các điều kiện biên dịch thông thường. Khi phát sinh `IOException`, initializer bọc lỗi thành `ConfigurationUnavailableException` (**unchecked**) và constructor không cần công bố `throws IOException`.

Tuy nhiên, đây là quyết định **thiết kế hợp đồng**, không phải cách sửa luôn luôn đúng:

- Caller không còn bị compiler buộc xử lý lỗi khởi tạo.
- Loại exception được ném ra cần biểu đạt đúng abstraction và tình huống thất bại.
- **Cause** cần được giữ qua `super(message, cause)` để không mất `IOException` ban đầu.
- Nếu việc tạo đối tượng có thể thất bại ở đường dự kiến và caller bắt buộc phục hồi, checked exception hoặc **factory method** có thể phù hợp hơn.

Có thể chuyển logic I/O ra khỏi initializer sang **factory method** có `throws IOException`, rồi chỉ tạo đối tượng sau khi các bước dễ thất bại đã thành công. Cách này thường cho phép quản lý tài nguyên và trạng thái khởi tạo rõ ràng hơn.

## 12. Khác biệt với constructor body thông thường

Một constructor body có thể trực tiếp ném checked exception:

~~~java
import java.io.IOException;

class Parser {
    Parser() throws IOException {
        if (System.nanoTime() < 0) {
            throw new IOException("khởi tạo thất bại");
        }
    }
}
~~~

Với trường hợp này, nghĩa vụ `throws IOException` phát sinh **từ phần thân constructor**. Không bắt buộc lớp phải có initializer nào.

Nếu bỏ `throws IOException` mà vẫn ném:

~~~java
import java.io.IOException;

// CỐ Ý SAI:
class ConstructorBodyBad {
    ConstructorBodyBad() {
        throw new IOException("body");
    }
}
~~~

sẽ là lỗi compile time: checked exception chưa được bắt hoặc khai báo.

Vậy có hai ngữ cảnh liên quan nhưng khác nhau:

| Điểm có thể ném checked exception | Quy tắc bắt buộc |
| --- | --- |
| Phần thân constructor | Constructor đó phải bắt hoặc khai báo kiểu được phép thoát |
| Lời gọi `super(...)` hoặc `this(...)` | Constructor chứa lời gọi phải tuân thủ catch-or-declare theo quy tắc constructor invocation |
| Instance field initializer của named class | **Phải có explicit constructor; mọi explicit constructor** phải khai báo checked exception có thể thoát khỏi initializer |
| Instance initializer block của named class | Cùng quy tắc như instance field initializer |
| Instance initializer đã tự bắt và xử lý hết checked exception | Không còn checked exception đó thoát ra, không cần khai báo nó chỉ vì có lời gọi nằm bên trong block |
| Unchecked exception ở initializer | Không buộc catch-or-declare theo quy tắc checked, nhưng có thể làm `new` thất bại ở runtime |

**Nhớ:** với initializer của named class, việc một constructor riêng đã khai báo đúng không làm các constructor còn lại được miễn khai báo.

## 13. Anonymous class là ngoại lệ đáng nhớ của quy tắc named class

Anonymous class là một lớp không có tên được khai báo ngay trong biểu thức tạo đối tượng:

~~~java
interface Action {
    void run();
}

Action action = new Action() {
    @Override
    public void run() {
        System.out.println("Action");
    }
};
~~~

Lập trình viên **không thể khai báo constructor tường minh** cho một anonymous class. Vì vậy nếu áp dụng máy móc điều kiện “phải có một constructor được khai báo tường minh”, anonymous class sẽ không bao giờ được viết initializer có checked exception.

Java có **quy tắc đặc biệt**: checked exception có thể phát sinh từ instance initializer của anonymous class được compiler phản ánh trong **constructor sinh ngầm** của anonymous class và trong phân tích khả năng ném của **biểu thức tạo anonymous class**. Do đó nơi gọi `new Interface() { ... }` có thể cần bắt hoặc khai báo checked exception đó.

Ví dụ hoàn chỉnh ở phần kế tiếp làm rõ.

## 14. Ví dụ Java 17 hoàn chỉnh: anonymous class có initializer ném `IOException`

Tệp `AnonymousInitializerDemo.java`:

~~~java
import java.io.IOException;

public class AnonymousInitializerDemo {
    interface Action {
        void run();
    }

    static String prepare(boolean fail) throws IOException {
        System.out.println("Khởi tạo anonymous");
        if (fail) throw new IOException("anonymous lỗi");
        return "OK";
    }

    static Action create(boolean fail) throws IOException {
        return new Action() {
            final String label = prepare(fail);

            @Override
            public void run() {
                System.out.println("Chạy: " + label);
            }
        };
    }

    public static void main(String[] args) {
        try {
            Action action = create(false);
            action.run();
            create(true);
        } catch (IOException ex) {
            System.out.println("Bắt lỗi: " + ex.getMessage());
        }
    }
}
~~~

Lệnh:

~~~bash
javac -encoding UTF-8 AnonymousInitializerDemo.java
java AnonymousInitializerDemo
~~~

Stdout chính xác:

~~~text
Khởi tạo anonymous
Chạy: OK
Khởi tạo anonymous
Bắt lỗi: anonymous lỗi
~~~

Lưu ý:

1. `new Action() { ... }` tạo một **anonymous class** triển khai interface `Action`.
2. `final String label = prepare(fail)` là instance field initializer của anonymous class; `prepare` có thể ném `IOException`.
3. Class anonymous không có explicit constructor, nhưng trường hợp này **được phép** theo quy tắc JLS §11.2.3.
4. Biểu thức tạo anonymous class trong `create()` có thể ném `IOException`, vì thế phương thức `create(boolean)` khai báo `throws IOException`.
5. Caller `main()` xử lý `IOException` bằng `catch`.
6. Nếu việc tạo anonymous class thất bại, phương thức `run()` của đối tượng mới **không được gọi**, vì đối tượng chưa được tạo thành công.

**Đừng nhầm:** việc interface `Action.run()` không khai báo `throws IOException` không cấm **constructor sinh ngầm của anonymous class** phát sinh `IOException` khi tạo đối tượng. Nhưng nếu đặt `throw new IOException(...)` trực tiếp vào **thân phương thức `run()`**, bạn vẫn phải tuân thủ hợp đồng override của `Action.run()`, nơi không cho phép checked exception này thoát ra.

## 15. Khác biệt với static field initializer và static initializer block

Đây là một đối chiếu rất quan trọng. Với **named class hoặc interface**, một static field initializer hoặc static initializer **không được để checked exception có thể thoát ra**. Không thể sửa bằng cách thêm `throws IOException` cho các constructor instance, bởi static initialization là cơ chế **khởi tạo lớp**.

Ví dụ sai:

~~~java
import java.io.IOException;

// CỐ Ý SAI:
class StaticInitializerBad {
    static String value = fetch();

    static String fetch() throws IOException {
        throw new IOException();
    }
}
~~~

Chạy `javac StaticInitializerBad.java` bị lỗi checked exception chưa được bắt/khai báo; không tồn tại cú pháp `static { ... } throws IOException` cho block cấp class.

Phương án có xử lý:

~~~java
import java.io.IOException;

class SafeStatic {
    static final String value;

    static {
        try {
            value = read();
        } catch (IOException ex) {
            throw new IllegalStateException("Không khởi tạo được class", ex);
        }
    }

    static String read() throws IOException {
        return "OK";
    }
}
~~~

Đoạn này là **lớp Java hợp lệ**, nhưng cần hiểu: khi static initializer thực sự ném một unchecked exception không phải `Error`, lỗi khởi tạo lớp ở runtime có thể được bao trong `ExceptionInInitializerError`; các lần dùng class tiếp theo cũng có thể gặp lỗi liên quan trạng thái khởi tạo class. Đây là **hợp đồng runtime của class initialization**, không phải cơ chế checked `throws` của constructor instance.

| Cơ chế | Có thể để checked exception thoát qua `throws` của constructor không? |
| --- | --- |
| Instance initializer của **named class** | Có, nếu tất cả explicit constructor khai báo phù hợp và class có ít nhất một constructor tường minh |
| Instance initializer của **anonymous class** | Có cơ chế riêng qua constructor sinh ngầm và biểu thức `new` anonymous |
| Static initializer của named class/interface | Không; phải xử lý checked exception tại chỗ |
| Thân constructor | Có; khai báo chính constructor phù hợp |
| Phần thân method thường | Có, theo catch-or-declare của method |

Đừng hiểu ngoại lệ checked do **static initializer** tạo ra là một trường hợp “cứ thêm `throws IOException` vào constructor thì sửa được”.

## 16. Instance initializer được chạy bao nhiêu lần với `this()` và kế thừa?

Một lượt tạo đối tượng hoàn tất bình thường bao gồm chuỗi khởi tạo của các lớp trong phân cấp. Theo logic thông thường:

- Superclass có instance initializers của nó; chúng chạy khi superclass được khởi tạo cho đối tượng.
- Sau khi phần khởi tạo superclass liên quan hoàn tất, instance initializers của subclass chạy.
- Với các constructor cùng lớp liên kết bằng `this(...)`, chúng **không làm phần initializer của chính lớp ấy chạy nhiều lần** trên cùng đối tượng.
- Nếu một initializer bị lỗi, các initializer hoặc phần constructor body phía sau nó trong cùng đường thực thi có thể không chạy.
- Caller không nhận được kết quả `new` thành công khi quá trình khởi tạo kết thúc bất thường.

Ví dụ minh họa thứ tự:

~~~java
class Base {
    {
        System.out.println("Base initializer");
    }

    Base() {
        System.out.println("Base constructor");
    }
}

class Derived extends Base {
    {
        System.out.println("Derived initializer");
    }

    Derived() {
        System.out.println("Derived constructor");
    }

    Derived(String label) {
        this();
        System.out.println("Derived constructor " + label);
    }
}
~~~

Nếu đặt trong một chương trình và gọi `new Derived("A")`, thứ tự phần in liên quan là:

~~~text
Base initializer
Base constructor
Derived initializer
Derived constructor
Derived constructor A
~~~

Đoạn này là **minh họa hai lớp**, không gồm phương thức `main`; stdout được suy ra từ thứ tự constructor chaining, không phải đầu ra của một tệp độc lập. Hành vi phù hợp với chương trình chạy hoàn chỉnh `NamedInitializerDemo` ở mục 7.

Nếu constructor của `Base` ném checked exception, constructor `Derived` gọi nó phải xử lý/khai báo theo luật constructor invocation. Nếu instance initializer của `Derived` cũng có thể ném loại checked khác, **mọi constructor explicit của `Derived`** phải công bố loại đó.

## 17. Checked exception khi tạo tài nguyên trong initializer: trách nhiệm cleanup

Một bẫy thiết kế: instance initializer có thể mở tài nguyên như stream, socket hoặc JDBC connection. Nếu mở tài nguyên con thành công rồi thao tác khởi tạo sau đó thất bại, **đối tượng bao ngoài chưa được tạo thành công** và caller không có tham chiếu đáng tin để gọi `close()` trên nó.

Mã minh họa vấn đề:

~~~java
// Minh họa trong một lớp có khai báo Path, IOException và API checkHeader.
class FragileReader implements AutoCloseable {
    java.io.InputStream in;

    {
        in = java.nio.file.Files.newInputStream(path);
        checkHeader(in); // Nếu ném IOException, tạo FragileReader thất bại.
    }

    FragileReader() throws java.io.IOException { }

    @Override
    public void close() throws java.io.IOException {
        if (in != null) in.close();
    }
}
~~~

Đoạn này **không độc lập** (cần định nghĩa `path` và `checkHeader`). Nó minh họa một nguy cơ: `try (FragileReader r = new FragileReader())` của caller **chỉ tự đóng `r` nếu resource expression khởi tạo thành công**. Nếu lỗi xảy ra **trong initializer của `FragileReader`**, `r` chưa được gán một đối tượng hoàn chỉnh; `try-with-resources` ở caller không tự `close()` tài nguyên nội bộ `in` đã mở dở.

Hướng an toàn hơn:

1. Đặt quá trình cấp phát và kiểm tra vào **factory method** hoặc constructor có kiểm soát.
2. Nếu một bước sau khi mở tài nguyên thất bại, **đóng tài nguyên đã cấp phát trong chính nơi cấp phát**.
3. Dùng `try-with-resources` cho tài nguyên con nếu có thể thiết kế đường trả quyền sở hữu an toàn.
4. Giữ lỗi chính và lỗi đóng phụ bằng cause/suppressed khi phù hợp.
5. Tránh “xuất bản” tham chiếu `this` của đối tượng chưa khởi tạo xong tới code bên ngoài.

Quy tắc checked exception giúp caller biết có thể thất bại, nhưng **không tự rollback trạng thái hoặc đóng các tài nguyên đã cấp phát dở**.

## 18. Một factory method có thể giúp hợp đồng rõ hơn

Thay vì đặt một thao tác mở dễ thất bại trong field initializer:

~~~java
class FragileClient {
    final String configuration = readConfiguration();

    FragileClient() throws java.io.IOException { }

    static String readConfiguration() throws java.io.IOException {
        return "mode=prod";
    }
}
~~~

bạn có thể thực hiện bước I/O trước, sau đó tạo đối tượng chỉ với dữ liệu đã chuẩn bị:

~~~java
import java.io.IOException;

final class Client {
    private final String configuration;

    private Client(String configuration) {
        this.configuration = configuration;
    }

    static Client open() throws IOException {
        String configuration = readConfiguration();
        return new Client(configuration);
    }

    static String readConfiguration() throws IOException {
        return "mode=prod";
    }

    String configuration() {
        return configuration;
    }
}
~~~

Đây là lớp Java độc lập hợp lệ, tuy không có `main`. Caller dùng `Client.open()`, bắt hoặc khai báo `IOException`; constructor private không có checked exception. Ưu điểm:

- Đường mở/chuẩn bị dữ liệu dễ nhìn ở method có tên và `throws` rõ ràng.
- Mỗi constructor không phải khai báo lỗi từ một initializer phức tạp.
- Có thể xử lý tài nguyên tạm trong factory trước khi chuyển sang trạng thái đối tượng hoàn chỉnh.
- Dễ viết kiểm thử từng đường thành công/thất bại và xác định điểm chuyển ownership.

Không bắt buộc mọi lớp có I/O phải dùng factory. Nếu constructor có ý nghĩa công khai “tạo object có thể lỗi I/O”, khai báo `throws IOException` trên các constructor cũng là thiết kế hợp lệ. Chọn cách nào tùy ranh giới API và khả năng quản lý trạng thái thất bại.

## 19. Các tệp cố ý sai để nhận diện lỗi compiler

Các đoạn sau là **ví dụ kiểm thử biên dịch âm**, không phải chương trình chạy.

### 19.1. Thiếu constructor explicit

~~~java
import java.io.IOException;

class NoConstructorBad {
    String value = fetch();
    static String fetch() throws IOException {
        throw new IOException();
    }
}
~~~

`javac` báo dạng:

~~~text
error: unreported exception IOException; must be caught or declared to be thrown
~~~

Nguyên nhân là initializer của named class có thể ném checked `IOException`, nhưng lớp không có explicit constructor nào khai báo nó.

### 19.2. Một trong nhiều constructor không khai báo

~~~java
import java.io.IOException;

class OneMissingThrowsBad {
    String value = fetch();
    OneMissingThrowsBad() throws IOException { }
    OneMissingThrowsBad(int mode) { }
    static String fetch() throws IOException {
        throw new IOException();
    }
}
~~~

`javac` cũng từ chối. Việc constructor đầu khai báo đúng **không bù cho constructor thứ hai**.

### 19.3. Static initializer có checked exception chưa xử lý

~~~java
import java.io.IOException;

class StaticInitializerBad {
    static String value = fetch();
    static String fetch() throws IOException {
        throw new IOException();
    }
}
~~~

`javac` từ chối; đây là quy tắc **static initialization**, không thể sửa chỉ bằng khai báo `throws` trên instance constructors.

### 19.4. Constructor body ném checked exception chưa khai báo

~~~java
import java.io.IOException;

class ConstructorBodyBad {
    ConstructorBodyBad() {
        throw new IOException("body");
    }
}
~~~

`javac` từ chối vì thân constructor trực tiếp ném checked `IOException` nhưng constructor chưa xử lý/khai báo. Đây là quy tắc constructor body thông thường, không phải điều kiện “mọi constructor” của instance initializer.

Cả bốn trường hợp được kiểm chứng với `javac --release 17`; thông báo có thể chỉ vào biểu thức khác nhau, nhưng nguyên nhân đều liên quan khả năng ném checked exception không phù hợp với ngữ cảnh.

## 20. Phân biệt lỗi trong initializer của object và lỗi class initialization

Các tên “initializer” rất dễ gây hiểu nhầm:

| Thuộc tính | Instance initializer | Static initializer |
| --- | --- | --- |
| Gắn với | Từng đối tượng | Lớp/interface theo quy tắc class initialization |
| Ví dụ | `String x = load();`, `{ check(); }` | `static String x = load();`, `static { check(); }` |
| Tạo mới object liên tiếp có chạy lại? | Có, cho mỗi lần khởi tạo đối tượng tương ứng | Không tự chạy lại cho mỗi `new` |
| Checked exception thoát ra trong named class | Có thể, nếu tất cả constructor explicit khai báo bao phủ | Không cho phép thoát trực tiếp |
| Điểm xử lý thường gặp | Caller của `new` hoặc constructor/factory | Khối `try/catch` trong static initialization, hoặc unchecked failure ở runtime |
| Có thể làm object không tạo xong? | Có | Có thể khiến class không khởi tạo thành công, ngăn sử dụng |

Nếu static initializer làm class initialization thất bại, người gọi có thể thấy `ExceptionInInitializerError` hoặc lỗi liên quan trạng thái class, tùy tình huống. **Không phải cứ thấy `ExceptionInInitializerError` là lỗi ở instance initializer**; tên exception này đặc biệt liên quan tới class initialization.

## 21. Lỗi thường gặp và cách sửa

| Sai lầm | Hệ quả | Cách sửa |
| --- | --- | --- |
| Instance field initializer gọi API checked nhưng lớp không có constructor | Lỗi compiler | Viết constructor explicit với `throws`, hoặc xử lý checked exception trong initializer |
| Chỉ constructor không tham số có `throws`, constructor overload thì không | Lỗi compiler cho named class | Mọi explicit constructor phải công bố các checked exception từ initializer |
| Nghĩ `this(...)` đã gọi constructor khác nên constructor hiện tại không cần `throws` | Bỏ qua nghĩa vụ checked exception từ initializer và lời gọi constructor đích | Khai báo/thiết kế đủ ở mọi constructor |
| Đặt `throws IOException` sau dấu `{ ... }` của instance initializer | Cú pháp Java không hợp lệ | `throws` nằm trong khai báo constructor/method |
| Đặt checked `throw` vô điều kiện trong instance initializer | Có thể vi phạm quy tắc block phải hoàn tất bình thường | Dùng đường có điều kiện và tuân thủ checked exception |
| Nghĩ constructor body luôn chạy trước field initializer | Dự đoán sai stdout hoặc trạng thái | Theo dõi thứ tự superclass → initializer cùng lớp → body constructor |
| Nghĩ instance initializer lỗi vẫn tiếp tục chạy các initializer phía sau | Đọc sai luồng điều khiển | Xem điểm ném là đường thoát bất thường |
| Bắt checked exception trong initializer rồi bỏ trống | Tạo object trạng thái sai, che lỗi thật | Phục hồi hợp lệ hoặc truyền lỗi có ngữ nghĩa |
| Bọc `IOException` thành unchecked mà bỏ cause | Mất dấu vết kỹ thuật | Dùng constructor `(message, cause)` |
| Dùng instance `throws` để sửa lỗi ở static initializer | Không có tác dụng | Bắt checked lỗi trong static initializer hoặc thiết kế lại |
| Cho rằng anonymous class phải khai báo constructor explicit | Anonymous class không được khai báo constructor | Áp dụng cơ chế constructor sinh ngầm và catch-or-declare tại biểu thức `new` |
| Khởi tạo tài nguyên dở rồi ném, mong caller tự gọi `close()` | Có thể rò rỉ tài nguyên | Quản lý cleanup trong nơi cấp phát trước khi chuyển ownership |
| Nhầm checked với unchecked | Khai báo `throws` thiếu hoặc thêm vô ích | Xem quan hệ kiểu với `RuntimeException`/`Error` |
| Đưa logic I/O lớn vào nhiều field initializers | Khó kiểm soát lỗi, side effect và test | Cân nhắc factory hoặc constructor khởi tạo rõ từng bước |

## 22. Checklist khi thiết kế class có initializer có thể lỗi

1. **Xác định chính xác chỗ nào có thể ném checked exception:** field initializer, instance block, constructor body, lời gọi `super`, `this` hay static initializer?
2. **Đây là named class hay anonymous class?** Hai trường hợp có quy tắc công bố lỗi khác nhau.
3. Với named class, **có ít nhất một constructor explicit chưa?**
4. **Mọi constructor explicit có `throws` bao phủ từng checked exception thoát từ initializer chưa?**
5. Nếu initializer gọi API I/O, **caller của `new` có đường `catch` hoặc `throws` đúng chưa?**
6. Các initializer có chạy **đúng thứ tự mã nguồn** không? Có phụ thuộc vào field chưa được gán giá trị không?
7. Nếu một initializer thất bại, **trạng thái/tài nguyên đã cấp phát trước đó** được giải phóng thế nào?
8. Nếu code khởi tạo có thể được khôi phục, giá trị dự phòng có thật sự **đảm bảo bất biến của object** không?
9. **Có nên chuyển thao tác phức tạp sang factory** để biểu diễn lỗi và quyền sở hữu tài nguyên rõ hơn?
10. Với anonymous class, checked exception từ initializer có được **bắt/khai báo tại biểu thức tạo đối tượng** chưa?
11. **Không lẫn với static initializer** hoặc `ExceptionInInitializerError`.
12. **Kiểm thử cả đường thành công, lỗi field và lỗi block** bằng ví dụ và xác minh constructor body có được chạy hay không.

## 23. Tự kiểm tra kiến thức

**Câu 1.** Named class có `String value = load();` với `load() throws IOException`, nhưng không khai báo constructor nào. Có biên dịch không?

**Đáp án:** Không. Named class phải có ít nhất một constructor khai báo tường minh, và mọi constructor explicit phải có `throws` phù hợp cho checked exception từ initializer.

**Câu 2.** Named class có hai constructor; chỉ một constructor `throws IOException`. Instance initializer có thể ném `IOException`. Hợp lệ không?

**Đáp án:** Không. **Mọi** constructor explicit đều phải khai báo `IOException` hoặc superclass phù hợp.

**Câu 3.** Có thể gắn `throws IOException` trực tiếp vào dấu ngoặc của instance initializer block không?

**Đáp án:** Không. Block không có chữ ký; phải công bố từ constructor hoặc xử lý lỗi trong block.

**Câu 4.** Nếu initializer bắt `IOException` và trả fallback hợp lệ, constructor có bắt buộc `throws IOException` không?

**Đáp án:** Không, nếu không còn đường nào từ initializer có thể để `IOException` thoát ra và không có nghĩa vụ checked exception khác.

**Câu 5.** Tại sao code `new Example()` có thể phải `catch IOException` dù thân `Example()` rỗng?

**Đáp án:** Vì instance field initializer hoặc instance block chạy trong quá trình tạo đối tượng có thể ném checked exception và được công bố qua constructor.

**Câu 6.** Constructor A gọi `this()` đến constructor B; instance initializers của cùng lớp có chạy hai lần không?

**Đáp án:** Không; chúng chạy một lượt cho một quá trình khởi tạo instance của lớp đó.

**Câu 7.** Instance field initializer ném ngoại lệ trước một instance block. Block phía sau có chạy không?

**Đáp án:** Không; quá trình khởi tạo cùng lớp đã kết thúc bất thường ở điểm ném.

**Câu 8.** Anonymous class có được viết field initializer gọi phương thức `throws IOException` không?

**Đáp án:** Có thể; Java suy ra checked exception trong constructor sinh ngầm và yêu cầu xử lý/khai báo khi biểu thức tạo anonymous class có thể ném.

**Câu 9.** Thêm `throws IOException` vào constructor có sửa được một **static field initializer** ném checked `IOException` không?

**Đáp án:** Không. Static initializer phải tự xử lý checked exception hoặc thay đổi thiết kế.

**Câu 10.** Nếu object khởi tạo dở sau khi đã mở stream, caller dùng `try-with-resources` trên object ấy có chắc đóng được stream không?

**Đáp án:** Không. Caller chưa có object hoàn chỉnh; constructor/factory phải tự quản lý tài nguyên đã cấp phát khi thất bại.

**Câu 11.** Bọc checked exception thành unchecked có tự động bảo toàn lỗi ban đầu không?

**Đáp án:** Không. Cần truyền `cause` rõ ràng, ví dụ `new IllegalStateException("không khởi tạo được", original)`.

**Câu 12.** Checked exception trong thân constructor có phải khiến **mọi constructor overload** công bố loại đó không?

**Đáp án:** Không chỉ vì lỗi nằm trong **thân một constructor**. Quy tắc “mọi constructor” là yêu cầu đặc biệt đối với checked exception thoát từ **instance initializer của named class**. Các constructor khác vẫn có nghĩa vụ riêng nếu gọi constructor đó qua `this(...)` hoặc thực hiện thao tác checked.

## 24. Tài liệu tham khảo

- [Java Language Specification 17 — §11.2.3, Exception Checking](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2.3): quy tắc **mọi explicit constructor** của named class và ngoại lệ riêng cho anonymous class.
- [Java Language Specification 17 — §8.3.2, Field Initialization](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.3.2): các biểu thức khởi tạo field.
- [Java Language Specification 17 — §8.6, Instance Initializers](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.6): quy tắc instance initializer blocks và khả năng hoàn tất bình thường.
- [Java Language Specification 17 — §8.8, Constructor Declarations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.8): constructors, explicit constructor invocation và mệnh đề `throws`.
- [Java Language Specification 17 — §12.5, Creation of New Class Instances](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.5): thứ tự và điều kiện khởi tạo instance.
- [Java Language Specification 17 — §15.9.5, Anonymous Class Declarations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.9.5): ngữ nghĩa anonymous class và constructor sinh ngầm.
- [Java Language Specification 17 — §8.7, Static Initializers](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.7): phân biệt static initialization với instance initialization.

**Kết luận:** checked exception có thể xuất hiện **trước thân constructor** ở instance field initializer hoặc instance initializer block. Với **named class**, Java yêu cầu **ít nhất một explicit constructor** và **mọi constructor explicit** đều công bố các checked exception ấy trong `throws`, hoặc xử lý để chúng không thoát khỏi initializer. Với **anonymous class**, compiler dùng constructor sinh ngầm và chuyển nghĩa vụ catch-or-declare tới biểu thức tạo đối tượng. Hiểu đúng ranh giới này giúp thiết kế constructor, factory, tài nguyên và hợp đồng lỗi an toàn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-inheritance-009">Static initializer, checked exception và lỗi khởi tạo lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Static initializer, checked exception và lỗi khởi tạo lớp trong Java

Khi JVM sử dụng một lớp Java lần đầu theo cách yêu cầu **khởi tạo lớp** (*class initialization*), nó thực thi những phần khởi tạo tĩnh: biểu thức khởi tạo của các trường `static` và các khối `static { ... }` theo thứ tự được khai báo trong mã nguồn. Những câu lệnh này chạy ở giai đoạn thiết lập trạng thái dùng chung cho lớp, **không phải mỗi lần tạo đối tượng**.

Vấn đề phát sinh khi công việc khởi tạo có thể thất bại. Đặc biệt:

- Khối `static` **không có khai báo `throws`**, và một checked exception không được phép thoát khỏi initializer nếu chưa được bắt/xử lý.
- Nếu quá trình khởi tạo lớp thất bại bởi một ngoại lệ không phải `Error`, JVM thường báo `ExceptionInInitializerError` cho lần thất bại đầu tiên.
- Nếu quá trình khởi tạo thất bại bởi một đối tượng thuộc `Error`, đối tượng `Error` đó có thể được truyền trực tiếp, không bắt buộc bị bọc lại thành `ExceptionInInitializerError`.
- Sau khi một lớp đã bị đánh dấu khởi tạo thất bại, những lần sử dụng sau cần khởi tạo lớp ấy thường nhận `NoClassDefFoundError` trong cùng runtime/class loader, **không phải thử chạy lại initializer**.

Đây là lý do việc đọc tệp, gọi dịch vụ mạng hoặc truy vấn cơ sở dữ liệu ngay trong `static` có thể biến một sự cố tạm thời thành một **lớp không thể sử dụng trong phần còn lại của vòng đời class loader**.

Bài học này nằm trong Java Core – Exception, nhóm **Kế thừa và hợp đồng**, và giải thích mối liên hệ giữa checked/unchecked exceptions, ngữ nghĩa khởi tạo lớp theo JVM và cách thiết kế API an toàn khi trạng thái tĩnh có thể gặp lỗi.

## 1. Static initializer là gì?

Có hai hình thức thông thường để khởi tạo trạng thái tĩnh:

**Biểu thức khởi tạo trường static:**

~~~java
static int cacheSize = calculateCacheSize();
~~~

**Khối static initializer:**

~~~java
static {
    System.out.println("Đang chuẩn bị lớp");
    // Gán trạng thái tĩnh, kiểm tra một số bất biến.
}
~~~

Đây là những mảnh mã để đặt **bên trong định nghĩa class**, không phải những chương trình độc lập. Một lớp có thể có nhiều trường tĩnh và nhiều khối `static`; chúng tham gia vào quá trình khởi tạo theo **thứ tự văn bản** khi JVM khởi tạo lớp.

Ví dụ hoàn chỉnh về thứ tự:

~~~java
class Preparation {
    static int value = createValue();

    static {
        System.out.println("static block: value=" + value);
    }

    static int createValue() {
        System.out.println("static field initializer");
        return 42;
    }
}
~~~

Khi `Preparation` thực sự được khởi tạo, đầu ra là:

~~~text
static field initializer
static block: value=42
~~~

Lệnh gọi `createValue()` ở trường `value` xảy ra trước khối `static` vì nó đứng trước trong mã nguồn.

**Không nhầm với constructor:** constructor thường chạy mỗi khi tạo một instance; code `static` được JVM chạy trong quá trình khởi tạo lớp (thông thường một lần **thành công** cho mỗi định nghĩa lớp gắn với một class loader), không phải mỗi lần `new`.

## 2. Những thao tác nào kích hoạt khởi tạo lớp?

Một số tình huống *active use* thường dẫn tới khởi tạo:

- Tạo đối tượng bằng `new SomeClass()` nếu lớp chưa được khởi tạo.
- Đọc trường `static` **không phải constant variable** của lớp.
- Ghi một trường `static` của lớp.
- Gọi phương thức `static` được khai báo bởi lớp.
- Một số đường reflective use, chẳng hạn `Class.forName("fully.qualified.Name")` theo overload mặc định có yêu cầu khởi tạo.

Ví dụ:

~~~java
class Startup {
    static final int CONSTANT = 7;
    static int computed = initialize();

    static int initialize() {
        System.out.println("Startup initialized");
        return 42;
    }

    static void ping() {
        System.out.println("ping");
    }
}
~~~

Nếu mã khác đọc `Startup.CONSTANT`, giá trị `7` là một **compile-time constant** (`static final int` với constant expression). Khi được biên dịch và dùng theo cách thông thường, việc đọc hằng số này **không cần kích hoạt khởi tạo `Startup`**.

Ngược lại, đọc `Startup.computed` hoặc gọi `Startup.ping()` là active use khiến lớp được khởi tạo nếu chưa khởi tạo.

### Các thao tác không nhất thiết kích hoạt initializer

Một vài cách dùng có thể làm bạn tưởng lớp đã “được sử dụng” nhưng chưa yêu cầu khởi tạo:

~~~java
Class<?> type = Startup.class;
int value = Startup.CONSTANT;
~~~

Lấy class literal `Startup.class` không tự yêu cầu thực thi các `static` initializer của `Startup`. Đọc compile-time constant cũng không bắt buộc khởi tạo lớp đó.

Phân biệt **loading**, **linking** và **initialization**: JVM có thể tải hoặc liên kết một lớp mà chưa chạy phần khởi tạo tĩnh. Cú pháp dùng class name không đồng nghĩa các `static` đã thực thi.

Ngoài ra, lớp cha thông thường được khởi tạo trước lớp con khi khởi tạo lớp con. Một số quy tắc riêng còn áp dụng cho interface và các superinterface khai báo default methods; không nên dùng ví dụ lớp thường để suy ra tuyệt đối cho mọi loại interface.

## 3. Checked exception trong static initializer: vì sao không được thoát trực tiếp?

Java sử dụng *checked exceptions* để buộc nơi gọi xử lý hoặc khai báo khi những ngoại lệ kiểm tra được có thể truyền qua ranh giới phương thức. Ví dụ `IOException` là checked:

~~~java
static String readConfig() throws java.io.IOException {
    return java.nio.file.Files.readString(
            java.nio.file.Path.of("config.txt"));
}
~~~

Đây là một phương thức minh họa, có thể đặt trong một lớp Java.

Nhưng **khối `static` không phải phương thức hoặc constructor**, nên bạn không thể khai báo:

~~~java
static throws java.io.IOException {
    // Sai cú pháp Java.
}
~~~

Đồng thời, đoạn sau **không biên dịch**:

~~~java
class WrongStaticBlock {
    static {
        java.nio.file.Files.readString(
                java.nio.file.Path.of("config.txt"));
    }
}
~~~

Lý do: `Files.readString(...)` khai báo có thể ném checked `IOException`, nhưng khối `static` không bắt nó và không có `throws` hợp lệ để chuyển cho caller.

Thông báo biên dịch thường có ý nghĩa tương tự *unreported exception IOException; must be caught or declared to be thrown*. Nội dung chính xác và số dòng có thể khác theo compiler.

### Không có cách “thêm throws cho class” để sửa

Các cách sau **không hợp lệ**:

~~~java
class WrongClass throws java.io.IOException {
    // Class không khai báo throws như phương thức.
}
~~~

Và constructor:

~~~java
class AlsoWrong {
    AlsoWrong() throws java.io.IOException {
        // Constructor có throws, nhưng không cấp quyền
        // cho static initializer ném checked exception ra ngoài.
    }
}
~~~

Constructor được phép khai báo `throws`, nhưng khai báo ấy không biến static initializer thành một phần của đường checked-exception của constructor. Lớp còn có thể bị khởi tạo bởi một lệnh gọi phương thức static mà không tạo instance nào.

**Quy tắc đúng:** mọi checked exception có thể phát sinh trong `static` initializer phải được xử lý trong phạm vi có thể bắt, hoặc được **chuyển thành ngoại lệ unchecked** theo một chính sách rõ ràng.

## 4. Cách bắt checked exception trong khối static

Một mẫu thường gặp:

~~~java
class ConfigHolder {
    static final String CONTENT;

    static {
        try {
            CONTENT = java.nio.file.Files.readString(
                    java.nio.file.Path.of("config.txt"));
        } catch (java.io.IOException cause) {
            throw new IllegalStateException(
                    "Không thể nạp cấu hình tĩnh", cause);
        }
    }
}
~~~

Đoạn class này có thể biên dịch trên Java 17+ vì `IOException` được bắt và khối `catch` ném `IllegalStateException`, một unchecked exception. Tuy vậy, việc truy cập lớp có thể thất bại **ngay lúc JVM khởi tạo** nếu không đọc được tệp.

Lưu ý:

1. `CONTENT` là `static final` và phải được gán trên mọi đường khối `static` **hoàn tất bình thường**.
2. Nếu đọc thành công, `CONTENT` nhận nội dung và lớp khởi tạo thành công.
3. Nếu đọc thất bại, `catch` giữ cause gốc trong `IllegalStateException`, rồi ném ra.
4. Trong giai đoạn khởi tạo lớp, JVM xử lý lỗi đó theo quy tắc class initialization; vì `IllegalStateException` không phải `Error`, bên gọi thường nhận `ExceptionInInitializerError` với cause là `IllegalStateException`.
5. `IOException` gốc nằm sâu hơn một tầng: `ExceptionInInitializerError → IllegalStateException → IOException`.

**Quan trọng:** không nên dùng mẫu này một cách vô thức cho cấu hình hoặc tài nguyên có thể tạm thời không sẵn sàng. Sau một lần khởi tạo thất bại, lớp thường bị đánh dấu **erroneous** và không được JVM tự khởi tạo lại trong cùng class loader.

### Nếu lỗi không đòi hỏi lớp phải thất bại?

Có thể chọn fallback khi hợp đồng cho phép:

~~~java
class DefaultConfig {
    static final int TIMEOUT;

    static {
        int selected;
        try {
            selected = readTimeout();
        } catch (java.io.IOException e) {
            selected = 5;
        }
        TIMEOUT = selected;
    }

    static int readTimeout() throws java.io.IOException {
        throw new java.io.IOException("file unavailable");
    }
}
~~~

Đây là ví dụ Java hợp lệ, nhưng chỉ đúng về nghiệp vụ khi timeout mặc định `5` được cho phép và việc không có tệp cấu hình không phải một lỗi fatal.

**Đừng bắt checked exception rồi âm thầm chọn một giá trị vô nghĩa chỉ để static initialization “đi qua”.** Nếu ứng dụng cần cấu hình đúng mới vận hành an toàn, phục hồi giả sẽ dẫn tới lỗi khó điều tra hơn về sau.

## 5. Static field initializer cũng chịu quy tắc checked exception

Không chỉ `static { ... }`, biểu thức gán cho trường `static` cũng không được để checked exception thoát ra:

~~~java
class InvalidField {
    static final String TEXT = read();

    static String read() throws java.io.IOException {
        throw new java.io.IOException("missing");
    }
}
~~~

Đây là **phản ví dụ không biên dịch**: biểu thức khởi tạo `TEXT` có thể ném checked `IOException` mà không có handler.

Một cách sửa là chuyển logic sang helper tự xử lý checked exception:

~~~java
class FieldViaHelper {
    static final String TEXT = readUnchecked();

    static String readUnchecked() {
        try {
            return read();
        } catch (java.io.IOException cause) {
            throw new IllegalStateException("Cannot read text", cause);
        }
    }

    static String read() throws java.io.IOException {
        return "ready";
    }
}
~~~

Đây là class Java hợp lệ. Phương thức `readUnchecked()` không khai báo checked exception vì đã xử lý nó. Nhưng nếu helper ném unchecked exception trong lúc khởi tạo `TEXT`, quá trình khởi tạo lớp vẫn có thể thất bại và phát sinh `ExceptionInInitializerError`.

Đưa mã vào helper **không biến lỗi khởi tạo thành lỗi thông thường có thể retry tự động**. Nó chỉ giải quyết hợp đồng checked exception và tổ chức code.

## 6. Khi static initializer gặp unchecked exception

Một lỗi thường gặp là nghĩ rằng: “Vì `IllegalStateException` là unchecked nên caller chỉ cần `catch(IllegalStateException)` khi khởi tạo lớp thất bại.”

Khi ngoại lệ không phải `Error` phát sinh và thoát khỏi quá trình khởi tạo, JVM thường tạo một `ExceptionInInitializerError` để biểu thị **thất bại trong class initialization**. Nó là subclass của `LinkageError`, cũng là `Error`.

Ví dụ:

~~~java
class Broken {
    static final int VALUE = initialize();

    static int initialize() {
        throw new IllegalStateException("bad config");
    }
}
~~~

Đây là class hợp lệ; phần khởi tạo trường gọi phương thức có thể ném unchecked exception tại runtime.

Nếu truy cập `Broken.VALUE` lần đầu:

~~~java
try {
    System.out.println(Broken.VALUE);
} catch (ExceptionInInitializerError e) {
    System.out.println(e.getClass().getSimpleName());
    System.out.println(
            e.getCause().getClass().getSimpleName());
}
~~~

Kết quả trong tình huống khởi tạo thất bại nêu trên:

~~~text
ExceptionInInitializerError
IllegalStateException
~~~

Đối tượng gốc `IllegalStateException` không bị mất; nó được đặt làm cause của `ExceptionInInitializerError`.

**Phân biệt hai tầng:**

- `IllegalStateException("bad config")`: vấn đề cụ thể phát sinh trong code khởi tạo.
- `ExceptionInInitializerError`: thông báo ở mức VM rằng việc khởi tạo một lớp đã kết thúc bất thường.

### `ExceptionInInitializerError` là checked hay unchecked?

Lớp `ExceptionInInitializerError` kế thừa `LinkageError` → `Error` → `Throwable`, vì vậy nó **unchecked**. Phương thức truy cập một lớp không phải khai báo `throws ExceptionInInitializerError`.

Một handler `catch (Exception e)` **không bắt** `ExceptionInInitializerError` vì nó không nằm trong nhánh `Exception`.

Đây là điểm đáng chú ý của chủ đề **Kế thừa và hợp đồng**: code khởi tạo có thể tạo `RuntimeException`, nhưng đối tượng quan sát được bên ngoài lần đầu lại thuộc nhóm `Error` do quy tắc khởi tạo lớp.

## 7. Nếu static initializer tự ném `Error`

JVM không phải lúc nào cũng bọc mọi lỗi thành `ExceptionInInitializerError`.

Quy tắc cốt lõi: nếu quá trình khởi tạo kết thúc bất thường bởi một đối tượng **thuộc `Error` hoặc subclass của `Error`**, JVM có thể truyền chính đối tượng `Error` đó như lý do thất bại của lần khởi tạo đầu.

Ví dụ:

~~~java
class FailsWithError {
    static {
        if (shouldFail()) {
            throw new AssertionError("broken invariant");
        }
    }

    static boolean shouldFail() {
        return true;
    }

    static void touch() {
        System.out.println("unreachable during failure");
    }
}
~~~

Đây là class Java hợp lệ. Điều kiện gọi phương thức không phải biểu thức hằng ở compile-time, nên khối `static` có một đường **có thể hoàn tất bình thường** theo phân tích compile-time. Lúc thực thi `shouldFail()` trả `true`, và `AssertionError` được ném.

Ở lần gọi `FailsWithError.touch()` đầu tiên, một `catch (AssertionError e)` phù hợp có thể nhận đối tượng `AssertionError` đó, thay vì bắt buộc nhận `ExceptionInInitializerError`.

**Không diễn giải thành “cứ có lỗi trong static thì luôn là ExceptionInInitializerError”.** Loại đối tượng bất thường thoát khỏi initializer quyết định có bước bọc vào `ExceptionInInitializerError` hay không.

## 8. Lớp thất bại một lần thường không được tự khởi tạo lại

Đây là hậu quả thực tế quan trọng nhất.

Khi JVM đã đánh dấu một lớp **khởi tạo thất bại**, trạng thái của định nghĩa lớp đó không trở lại “chưa khởi tạo” chỉ vì caller bắt được lỗi. Những lần active use tiếp theo thường thất bại nhanh với `NoClassDefFoundError`.

~~~text
Active use lần đầu
       |
       v
Thực thi static initialization
       |
       +-- Thành công ---> Lớp ở trạng thái initialized
       |
       +-- Thất bại ----> Lớp bị đánh dấu erroneous
                                  |
                                  v
                         Active use lần sau
                                  |
                                  v
                         NoClassDefFoundError
~~~

Vì thế, mã sau **không phải chiến lược retry hữu hiệu**:

~~~java
for (int attempt = 0; attempt < 3; attempt++) {
    try {
        UnstableHolder.connect();
        break;
    } catch (ExceptionInInitializerError e) {
        System.out.println("retry after static failure");
    }
}
~~~

Đây là mảnh minh họa, giả định có class `UnstableHolder`. Nếu lần đầu khởi tạo lớp đã thất bại, lần sau thường không vào cùng nhánh `catch (ExceptionInInitializerError)`; thay vào đó có thể có `NoClassDefFoundError`. Không nên xây cơ chế phục hồi bằng việc chỉ gọi lại một lớp đã bị đánh dấu erroneous.

**Trong cùng class loader**, JVM không tự “thử khởi tạo lại từ đầu” lớp đã thất bại. Khả năng tải một định nghĩa lớp mới bằng class loader khác là một cơ chế class loading riêng, không phải retry bình thường cho một ứng dụng.

## 9. `NoClassDefFoundError` không chỉ có nghĩa “thiếu file .class”

Tên `NoClassDefFoundError` dễ gây hiểu nhầm. Có ít nhất hai nhóm tình huống cần phân biệt:

1. **Không thể tìm hoặc định nghĩa lớp cần thiết tại runtime**, ví dụ classpath/module path không chứa một lớp đã được biên dịch để dùng.
2. **Một lớp đã được tìm thấy nhưng khởi tạo thất bại trước đó**; lần active use sau không thể dùng lại định nghĩa lỗi đó.

Vì vậy khi gặp `NoClassDefFoundError`:

- Đọc message và toàn bộ cause/stack trace để xác định có dấu hiệu `Could not initialize class ...` hay không.
- Tìm lỗi **đầu tiên** của quá trình khởi tạo, thường là `ExceptionInInitializerError` hoặc một `Error` khác.
- Nếu nguyên nhân gốc là lỗi I/O, cấu hình hay kết nối trong `static`, hãy xử lý thiết kế khởi tạo thay vì chỉ thêm JAR vào classpath.
- Nếu lớp thật sự không có ở runtime, hãy kiểm tra packaging, module path, classpath, phiên bản dependency và class loader.

Không được kết luận “thêm dependency” hay “đổi tên file .class” chỉ từ tên `NoClassDefFoundError`.

### Tại sao đôi khi lần đầu và lần hai có loại khác?

Ở lần đầu JVM đang **thực thi mã khởi tạo**, nên thấy lỗi trực tiếp của quá trình ấy hoặc wrapper `ExceptionInInitializerError`.

Ở lần hai JVM biết định nghĩa lớp đã ở trạng thái erroneous, nên việc active use không thực thi lại logic khởi tạo và thường dẫn tới `NoClassDefFoundError`. Dù message khác nhau theo JVM, khác biệt về pha là điểm cần hiểu.

## 10. Một chương trình Java 17+ chạy độc lập: thành công, checked failure, Error và lần dùng sau

Chương trình dưới đây chỉ dùng **lớp lồng tĩnh** để minh họa dễ chạy. Nó **không đọc tệp thật**, mà cố ý ném `IOException` từ một hàm mô phỏng nhằm có đầu ra xác định. Không cần thư viện ngoài.

Lưu thành tệp **`StaticInitializationDemo.java`**:

~~~java
import java.io.IOException;

public class StaticInitializationDemo {

    static class Good {
        static final String COMPILE_TIME = "constant";
        static int value = initializeValue();

        static {
            System.out.println("Good static block");
        }

        static int initializeValue() {
            System.out.println("Good field initializer");
            return 42;
        }

        static void touch() {
            System.out.println("Good.touch value=" + value);
        }
    }

    static class Failing {
        static {
            System.out.println("Failing static block");
            try {
                loadRequiredConfig();
            } catch (IOException cause) {
                throw new IllegalStateException(
                        "config initialization failed", cause);
            }
        }

        static void touch() {
            System.out.println("Failing.touch");
        }
    }

    static class FailingWithError {
        static {
            System.out.println("FailingWithError static block");
            if (shouldFail()) {
                throw new AssertionError("invariant broken");
            }
        }

        static boolean shouldFail() {
            return true;
        }

        static void touch() {
            System.out.println("FailingWithError.touch");
        }
    }

    public static void main(String[] args) {
        System.out.println("== compile-time constant ==");
        System.out.println("constant=" + Good.COMPILE_TIME);

        System.out.println("== successful init ==");
        Good.touch();
        Good.touch();

        System.out.println("== checked source first use ==");
        try {
            Failing.touch();
        } catch (ExceptionInInitializerError e) {
            System.out.println(
                    "first=" + e.getClass().getSimpleName());
            System.out.println(
                    "wrapped=" + e.getCause().getClass().getSimpleName());
            System.out.println(
                    "root=" + e.getCause().getCause()
                            .getClass().getSimpleName());
            System.out.println(
                    "rootMessage=" + e.getCause().getCause().getMessage());
        }

        System.out.println("== checked source second use ==");
        try {
            Failing.touch();
        } catch (NoClassDefFoundError e) {
            System.out.println(
                    "second=" + e.getClass().getSimpleName());
        }

        System.out.println("== direct Error first use ==");
        try {
            FailingWithError.touch();
        } catch (AssertionError e) {
            System.out.println(
                    "firstError=" + e.getClass().getSimpleName()
                            + ": " + e.getMessage());
        }

        System.out.println("== direct Error second use ==");
        try {
            FailingWithError.touch();
        } catch (NoClassDefFoundError e) {
            System.out.println(
                    "secondError=" + e.getClass().getSimpleName());
        }
    }

    static void loadRequiredConfig() throws IOException {
        throw new IOException("config missing");
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac StaticInitializationDemo.java
java StaticInitializationDemo
~~~

**Stdout chính xác cho chương trình này:**

~~~text
== compile-time constant ==
constant=constant
== successful init ==
Good field initializer
Good static block
Good.touch value=42
Good.touch value=42
== checked source first use ==
Failing static block
first=ExceptionInInitializerError
wrapped=IllegalStateException
root=IOException
rootMessage=config missing
== checked source second use ==
second=NoClassDefFoundError
== direct Error first use ==
FailingWithError static block
firstError=AssertionError: invariant broken
== direct Error second use ==
secondError=NoClassDefFoundError
~~~

### Phân tích kết quả theo từng đoạn

**Compile-time constant:** `Good.COMPILE_TIME` được biên dịch như một giá trị hằng. Dòng `constant=constant` xuất hiện mà `Good` chưa phải chạy phần khởi tạo `value` hay static block. Không kết luận rằng bất kỳ lần đọc trường `static final` nào cũng bỏ khởi tạo: trường đó phải là **constant variable** theo quy tắc Java.

**Successful init:** lần gọi `Good.touch()` đầu tiên kích hoạt khởi tạo lớp. Biểu thức `value = initializeValue()` chạy trước và in `Good field initializer`; khối `static` chạy sau và in `Good static block`. Phương thức `touch()` in giá trị `42`. Lần gọi `Good.touch()` thứ hai không chạy initializer lại, nên chỉ in dòng của `touch()`.

**Checked source first use:** `Failing` gọi một phương thức khai báo `throws IOException` trong `static`, nhưng đã bắt checked exception đó. Code chuyển nó thành `IllegalStateException` có cause là `IOException`. Vì unchecked exception không thuộc `Error` thoát khỏi khởi tạo, JVM báo `ExceptionInInitializerError` với một cause `IllegalStateException`, bên trong nữa là `IOException`.

**Checked source second use:** JVM đã đánh dấu định nghĩa `Failing` là erroneous. Lần active use sau dẫn tới `NoClassDefFoundError` thay vì chạy lại static block. Không có dòng `Failing static block` thứ hai.

**Direct Error first use:** `FailingWithError` cố ý ném `AssertionError` qua điều kiện runtime. Vì đó đã là một `Error`, lần đầu caller bắt `AssertionError` trực tiếp. Đây là ví dụ phân tích JVM, **không khuyến nghị dùng `AssertionError` để biểu diễn lỗi cấu hình**.

**Direct Error second use:** định nghĩa lớp vẫn đã khởi tạo thất bại, nên lần sau nhận `NoClassDefFoundError`, không chạy lại logic static block.

**Không phải mã phục hồi production:** các khối `catch (ExceptionInInitializerError)`, `catch (AssertionError)` và `catch (NoClassDefFoundError)` trong chương trình chỉ để quan sát hậu quả. Khi một lớp quan trọng không khởi tạo được, chương trình thực thường cần báo lỗi rõ ràng rồi dừng/tái khởi động có kiểm soát thay vì cố tiếp tục sử dụng cùng lớp.

## 11. Vì sao khối `static` không được chỉ có một lệnh `throw` vô điều kiện?

Java có một quy tắc compile-time riêng: **static initializer phải có khả năng hoàn tất bình thường** (*must be able to complete normally*).

Phản ví dụ:

~~~java
class AlwaysFail {
    static {
        throw new IllegalStateException("always");
    }
}
~~~

Đây là **mã không biên dịch**. Dù `IllegalStateException` là unchecked, khối `static` chỉ có `throw` vô điều kiện nên không thể hoàn tất bình thường theo quy tắc phân tích luồng của compiler.

Một ví dụ được phép biên dịch nhưng thất bại khi chạy:

~~~java
class FailsAtRuntime {
    static {
        if (shouldFail()) {
            throw new IllegalStateException("runtime failure");
        }
    }

    static boolean shouldFail() {
        return true;
    }
}
~~~

Mã này có một nhánh điều kiện không phải constant expression, vì vậy compiler có thể thấy đường hoàn tất bình thường. Khi chạy, `shouldFail()` luôn trả `true`, khiến static initialization thất bại.

**Không dùng mẹo này để chủ động làm hỏng một class trong ứng dụng.** Nó chỉ minh họa khác biệt giữa:
- Kiểm tra *có thể hoàn tất bình thường* ở compile-time.
- Việc thực tế initializer có ném lỗi trong lần chạy hay không.

Một khối có `try/catch` xung quanh thao tác có thể ném lỗi thường cũng có đường hoàn tất bình thường theo phân tích compile-time, nhưng runtime vẫn có thể luôn thất bại nếu tác vụ phụ thuộc môi trường.

## 12. Phân biệt static initializer với constructor và instance initializer

Một số người tưởng có thể sửa lỗi checked trong `static` bằng cách thêm `throws` vào constructor:

~~~java
class IncorrectFix {
    IncorrectFix() throws java.io.IOException {
        // Throws chỉ gắn với constructor này.
    }

    static {
        // Nếu gọi phương thức throws IOException ở đây
        // mà không bắt thì vẫn là lỗi biên dịch.
    }
}
~~~

Điều đó **không sửa** khả năng ném checked exception trong `static`. Khởi tạo lớp và khởi tạo instance thuộc các pha khác nhau.

Phân biệt:

| Đặc điểm | Static initializer | Constructor |
| --- | --- | --- |
| Thuộc về | Lớp | Một lần tạo instance |
| Khi chạy | Khi JVM khởi tạo lớp theo quy tắc active use | Khi tạo đối tượng, sau bước khởi tạo lớp cần thiết |
| Có cú pháp khai báo `throws`? | Không | Có |
| Checked exception chưa xử lý có được thoát? | Không | Có nếu constructor khai báo/bắt phù hợp |
| Lỗi khởi tạo có thể làm class erroneous? | Có | Constructor ném lỗi bình thường không tự làm class erroneous |
| Tự động retry khi gọi lại? | Không nếu định nghĩa class đã erroneous | Có thể thử tạo instance mới nếu class đã khởi tạo thành công |

Một **instance initializer block** khác với static initializer. Nó được thực thi trong đường khởi tạo instance; quy tắc checked exception của instance initializer phụ thuộc các constructor hợp lệ và phức tạp hơn. Không được mang quy tắc của instance initializer để suy ra static block có thể khai báo `throws`.

Ví dụ constructor có checked exception hợp lệ:

~~~java
class InstanceConfig {
    InstanceConfig() throws java.io.IOException {
        throw new java.io.IOException("instance failed");
    }
}
~~~

Đây là class Java hợp lệ. Nếu `new InstanceConfig()` ném `IOException`, lớp `InstanceConfig` **không tự bị đánh dấu khởi tạo thất bại** chỉ vì constructor của một instance ném lỗi. Caller có thể xử lý và thử tạo instance khác theo hợp đồng. Đây là lý do nên cân nhắc chuyển thao tác I/O dễ thất bại ra khỏi `static`.

## 13. Thiết kế tốt hơn cho cấu hình có thể thất bại: factory có checked exception

Thay vì để việc đọc cấu hình trong static initializer, có thể dùng một phương thức factory:

~~~java
final class Settings {
    private final String content;

    private Settings(String content) {
        this.content = content;
    }

    static Settings load(java.nio.file.Path path)
            throws java.io.IOException {
        String text = java.nio.file.Files.readString(path);
        return new Settings(text);
    }

    String content() {
        return content;
    }
}
~~~

Đây là class Java 17 hợp lệ. Việc tạo một đối tượng `Settings` bây giờ diễn ra qua `Settings.load(path)` và phương thức có thể khai báo checked `IOException`.

**Lợi ích:**

1. Caller biết rõ thao tác có khả năng thất bại thông qua `throws IOException`.
2. Caller có thể chọn retry, báo lỗi, fallback hoặc chuyển tiếp.
3. Một lần nạp cấu hình thất bại **không đánh dấu cả `Settings` là class erroneously initialized** nếu class không có static initializer ném lỗi.
4. Dữ liệu cấu hình được gắn với **instance** thay vì một trạng thái static khó thay thế.
5. Dễ kiểm thử với tệp mẫu hoặc dữ liệu giả lập; vòng đời có thể do dependency injection/container quản lý.

Nếu không muốn phơi lỗi I/O ra khỏi abstraction, factory có thể bọc checked `IOException` trong một custom checked exception miền:

~~~java
static Settings loadRequired(java.nio.file.Path path)
        throws ConfigurationLoadException {
    try {
        return load(path);
    } catch (java.io.IOException cause) {
        throw new ConfigurationLoadException(
                "Cannot load required configuration", cause);
    }
}
~~~

Đây là **phương thức minh họa**, giả định `ConfigurationLoadException` là checked exception có constructor nhận message và cause. Điểm quan trọng là lỗi phát sinh **trong lời gọi phương thức bình thường**, không phải qua một static initializer khiến class erroneous.

## 14. Lazy initialization có giải quyết hoàn toàn vấn đề không?

Không phải cứ hoãn khởi tạo tới lần truy cập đầu là sẽ có khả năng retry.

**Initialization-on-demand holder idiom:**

~~~java
class Service {
    private static class Holder {
        static final Service INSTANCE = createService();
    }

    static Service instance() {
        return Holder.INSTANCE;
    }

    static Service createService() {
        return new Service();
    }
}
~~~

Đây là class minh họa. `Holder` thường chỉ được khởi tạo khi `Service.instance()` cần `Holder.INSTANCE`. Nó là cách triển khai **lazy initialization** hữu ích khi thao tác khởi tạo đơn giản, an toàn và thực sự đáng chạy một lần.

Tuy nhiên, nếu `createService()` ném unchecked exception trong quá trình khởi tạo `Holder`, **Holder cũng có thể bị đánh dấu erroneous**. Đưa công việc thất bại vào lớp `Holder` **không biến nó thành cơ chế retry**.

Nếu bạn cần thử kết nối lại, làm mới credential, đổi endpoint hoặc phục hồi sau lỗi tạm thời, hãy thiết kế một **state machine hoặc provider quản lý vòng đời** có kiểm soát, thay vì mong rằng `static final INSTANCE` tự được tạo lại.

### Cẩn thận với double-checked locking và trạng thái dùng chung

Một dịch vụ lazy phức tạp có thể dùng `volatile`, synchronization hoặc thư viện concurrent. Đây là câu chuyện riêng về Java Memory Model, nhưng liên quan trực tiếp đến việc khởi tạo thất bại: cần quyết định rõ khi nào trạng thái được công bố, caller nào chờ, liệu có retry, và cách tránh công bố đối tượng nửa khởi tạo.

**Đừng tự chế một singleton retry phức tạp nếu một factory hoặc dependency injection lifecycle đã đủ rõ.**

## 15. Tác động tới class loading và đa luồng

Class initialization không chỉ là vài dòng được gọi tùy ý; JVM có một giao thức khởi tạo để bảo đảm các trạng thái cơ bản khi nhiều thread cùng active use lớp.

Thông thường:

- JVM đồng bộ hóa quá trình khởi tạo một định nghĩa lớp để không có hai thread độc lập cùng chạy initializer như hai lần khởi tạo riêng.
- Một thread có thể phải đợi khi thread khác đang khởi tạo cùng định nghĩa lớp.
- Nếu initializer thành công, các thread dùng lớp đã khởi tạo thấy kết quả theo bảo đảm khởi tạo lớp.
- Nếu initializer thất bại, định nghĩa lớp chuyển sang trạng thái erroneous; các thread khác không được kỳ vọng “tự chạy lại” static initializer.
- Một khối `static` làm I/O lâu, chờ lock hoặc đợi tác vụ khác có thể ảnh hưởng nặng đến thời gian startup hoặc tạo nguy cơ deadlock.

### Vì sao không nên bắt đầu thread rồi đợi trong static block?

Một mẫu đáng nghi ngại:

~~~java
class Dangerous {
    static {
        Thread worker = new Thread(() -> initialize();
        );
        worker.start();
        // Nếu chờ worker hoàn thành ở đây mà worker cần
        // chính class đang được khởi tạo, có thể deadlock.
    }

    static void initialize() {
        // Công việc phụ thuộc trạng thái lớp.
    }
}
~~~

Đoạn trên **chỉ là sơ đồ phản ví dụ về ý tưởng**, không phải mã Java biên dịch (nó cố ý trình bày một ý tưởng luồng nguy hiểm). Khi triển khai thực, bất kỳ công việc nào yêu cầu thread khác truy cập class đang chờ khởi tạo đều cần được xem xét nguy cơ khóa khởi tạo vòng tròn.

Về bản chất, class initialization thích hợp cho việc thiết lập những hằng số hoặc trạng thái đơn giản, có thể dự đoán; không phải chỗ lý tưởng để khởi động một workflow phụ thuộc đa luồng.

## 16. Cách chẩn đoán `ExceptionInInitializerError` trong thực tế

Khi nhìn log:

~~~text
java.lang.ExceptionInInitializerError
    ...
Caused by: java.lang.IllegalStateException: config initialization failed
    ...
Caused by: java.io.IOException: config missing
    ...
~~~

Đây là **minh họa cấu trúc**, không phải stack trace đầy đủ hoặc đầu ra byte-for-byte của một JVM.

Hãy đi theo những bước:

1. **Tìm lần lỗi đầu tiên**, không chỉ các `NoClassDefFoundError` sau đó.
2. Đọc `getCause()` của `ExceptionInInitializerError` để thấy lỗi gốc mà initializer đã ném; nếu đó là wrapper, tiếp tục duyệt chuỗi cause.
3. Kiểm tra các trường static/khối static **theo thứ tự khai báo**, đặc biệt những hàm đọc cấu hình, truy cập môi trường hoặc khởi tạo client.
4. Xác định lỗi là thiếu dependency, I/O, sai cấu hình, vi phạm bất biến, hay class initialization cycle.
5. Kiểm tra có phải một thread khác chờ initialization lock hoặc phát sinh deadlock không.
6. Quyết định chính sách **fail fast**, fallback hợp lệ, hay chuyển thao tác sang factory để có thể retry đúng cách.
7. Kiểm thử với môi trường mô phỏng lỗi, không phải cố tình phá JVM production.

Nếu log chỉ còn:

~~~text
java.lang.NoClassDefFoundError: Could not initialize class ...
~~~

hãy kiểm tra log **trước thời điểm đó** để tìm lỗi khởi tạo đầu tiên. Đây là chẩn đoán thường có giá trị hơn việc tìm file `.class` trong trường hợp lớp đã được tìm thấy nhưng initialization thất bại.

### `ExceptionInInitializerError.getException()` và `getCause()`

`ExceptionInInitializerError` có phương thức cũ `getException()` để lấy đối tượng được lưu khi tạo một số dạng lỗi khởi tạo. Trong mã hiện đại, `getCause()` thường là API dễ dùng hơn để lần theo nguyên nhân như các `Throwable` khác.

Không nên giả định một đối tượng `ExceptionInInitializerError` bất kỳ luôn có cause khác null: code có thể tự tạo `new ExceptionInInitializerError()` hoặc constructor nhận message mà không lưu một throwable nguyên nhân. Khi duyệt cause, kiểm tra null trước khi dùng.

## 17. Bảo đảm của `finally` và try-with-resources khi class initialization thất bại

Một exception phát sinh trong static initializer vẫn đi qua các cấu trúc `try/finally` đã được thực thi theo luật Java thông thường. Ví dụ:

~~~java
class ResourceInit {
    static {
        try {
            System.out.println("initializing");
            fail();
        } finally {
            System.out.println("cleanup during initialization");
        }
    }

    static void fail() {
        throw new IllegalStateException("cannot initialize");
    }
}
~~~

Class này hợp lệ. Khi JVM chạy initializer, nó in `initializing`, rồi `cleanup during initialization` trước khi `IllegalStateException` thoát và thường bị JVM bọc trong `ExceptionInInitializerError`.

Try-with-resources cũng gọi `close()` khi rời scope nếu mã Java còn cơ hội thực thi. Điều đó **không có nghĩa nó sửa trạng thái erroneous của lớp** sau khi lỗi đã thoát.

Cần phân biệt:

- Dọn dẹp tài nguyên khi một bước khởi tạo thất bại.
- Làm cho định nghĩa lớp trở lại trạng thái có thể khởi tạo lần đầu.

Hai việc này không tương đương. Một `finally` chạy thành công có thể dọn tài nguyên đang mở mà vẫn để quá trình class initialization kết thúc bất thường.

## 18. Những lỗi hiểu sai phổ biến

### Sai lầm 1 — “Static block có thể viết `throws IOException`”

Không. `static` initializer không có cú pháp khai báo `throws` và không được để checked exception thoát trực tiếp.

### Sai lầm 2 — “Constructor khai báo `throws` giúp static initializer hợp lệ”

Không. Constructor và static initialization là hai pha khác nhau. `throws` của constructor không xử lý checked exception của khối `static`.

### Sai lầm 3 — “Chỉ khối `static {}` mới có thể làm lớp khởi tạo thất bại”

Không. Biểu thức khởi tạo của trường `static` cũng tham gia vào quá trình khởi tạo lớp và có thể thất bại.

### Sai lầm 4 — “Một `RuntimeException` thoát khỏi static block luôn được caller nhận trực tiếp”

Không. Khi khởi tạo lớp thất bại bởi một ngoại lệ không phải `Error`, JVM thường bọc nó bằng `ExceptionInInitializerError`.

### Sai lầm 5 — “Bất kỳ `Error` nào thoát khỏi initializer cũng phải bị bọc”

Không. Một `Error` có thể là lỗi được báo trực tiếp của lần khởi tạo đầu.

### Sai lầm 6 — “Lần sau JVM sẽ khởi tạo lại class đã lỗi”

Không. Trong cùng class loader, một định nghĩa lớp đã bị đánh dấu erroneous thông thường không được khởi tạo lại; lần dùng sau thường phát sinh `NoClassDefFoundError`.

### Sai lầm 7 — “`NoClassDefFoundError` luôn do thiếu file .class”

Sai. Nó cũng xuất hiện khi class đã được tìm thấy nhưng một lần khởi tạo trước thất bại.

### Sai lầm 8 — “`catch(Exception)` chắc chắn bắt `ExceptionInInitializerError`”

Không. `ExceptionInInitializerError extends LinkageError`, tức nằm dưới `Error`, không dưới `Exception`.

### Sai lầm 9 — “`static final` lúc nào cũng khởi tạo class khi đọc”

Không. Trường compile-time constant có thể được inlined và không kích hoạt khởi tạo lớp như một static field thông thường.

### Sai lầm 10 — “`SomeClass.class` luôn chạy static block”

Không. Lấy class literal không tự kích hoạt initialization, dù cơ chế tải lớp có thể đã diễn ra.

### Sai lầm 11 — “Đưa code vào static helper là có thể retry”

Không. Nếu helper chạy **trong static field initializer** và ném lỗi ra ngoài, toàn bộ quá trình khởi tạo lớp vẫn có thể thất bại.

### Sai lầm 12 — “Static initializer chỉ chạy khi gọi constructor”

Không. Gọi phương thức static hoặc truy cập trường static không phải constant cũng có thể kích hoạt khởi tạo.

### Sai lầm 13 — “`NoClassDefFoundError` sau initialization failure nên luôn được catch rồi tiếp tục”

Không. Lớp phụ thuộc quan trọng có thể không còn sử dụng được. Cần xem xét chính sách thất bại và khởi động lại, không tiếp tục mù quáng.

### Sai lầm 14 — “Bắt `IOException` trong static rồi bỏ qua luôn là tốt”

Không. Fallback chỉ hợp lý nếu hợp đồng cho phép và trạng thái thay thế an toàn.

### Sai lầm 15 — “Một constructor ném checked exception làm cả class bị erroneous”

Không. Class initialization và instance initialization khác nhau. Một constructor thất bại không mặc định đánh dấu class initialization thất bại.

### Sai lầm 16 — “`finally` không chạy khi initializer ném ngoại lệ”

Không. `finally` liên quan vẫn được thực thi khi luồng Java rời scope theo cách thông thường. Nó không tự sửa class initialization failure.

### Sai lầm 17 — “Một static block vô điều kiện chỉ có `throw` luôn biên dịch vì RuntimeException unchecked”

Không. Java còn có yêu cầu static initializer **phải có khả năng hoàn tất bình thường** khi phân tích compile-time.

### Sai lầm 18 — “Có thể làm class có cơ chế retry bằng cách dời mọi I/O vào lazy static holder”

Không. Lazy holder vẫn bị erroneous nếu initializer của chính nó thất bại. Retry cần được thiết kế trong phương thức/provider quản lý trạng thái.

### Sai lầm 19 — “Mọi lời gọi `Class.forName` đều giống hệt đọc `SomeClass.class`”

Không. Overload mặc định `Class.forName(String)` yêu cầu initialization; class literal chỉ đại diện cho class object và không tự khởi tạo theo cùng cách. Một số overload reflective cho phép điều khiển cờ initialize.

### Sai lầm 20 — “Có thể đoán nguyên nhân chỉ từ tên `ExceptionInInitializerError`”

Không. Phải xem cause chain, khối `static` liên quan và lỗi đầu tiên. Đây là wrapper mô tả giai đoạn thất bại, không nêu đầy đủ lý do kỹ thuật.

## 19. Checklist thiết kế static state an toàn

Trước khi viết I/O hoặc logic có thể lỗi trong khối `static`, tự hỏi:

1. **Tài nguyên có thể tạm thời không sẵn sàng không?** Nếu có, một static initializer thất bại là lựa chọn khó phục hồi.
2. **Caller có cần retry không?** Dùng factory hoặc service lifecycle thay vì class initialization.
3. **Có thể dùng giá trị mặc định an toàn không?** Nếu có, fallback phải được ghi trong hợp đồng.
4. **Có checked exception cần truyền cho caller không?** Static initializer không có `throws`; hãy chuyển code sang phương thức phù hợp khi cần.
5. **Có phụ thuộc mạng, đĩa hoặc cơ sở dữ liệu không?** Những công việc đó thường không lý tưởng cho `static`.
6. **Có nguy cơ deadlock hoặc thời gian khởi tạo dài không?** Tránh chờ các thread khác hoặc gọi mạng trong initialization lock.
7. **Các constant có thể biểu diễn bằng constant expression không?** Giữ những cấu hình tĩnh đơn giản, bất biến và dễ dự đoán.
8. **Nếu thất bại, ứng dụng cần fail fast hay tiếp tục?** Quyết định ở tầng kiểm soát lifecycle.
9. **Có ghi log cause gốc trước khi gặp các lỗi `NoClassDefFoundError` tiếp theo không?** Giữ dấu vết đầu tiên.
10. **Có test cả lần sử dụng đầu và lần sử dụng sau trong cùng JVM không?** Hai lần có thể quan sát kiểu lỗi khác.
11. **Có test việc gọi từ nhiều thread không?** Class initialization là tài nguyên đồng bộ hóa ẩn có thể bị chờ.
12. **Có phải code thực sự cần static mutable state không?** Instance được quản lý rõ vòng đời có thể dễ kiểm thử hơn.

Một static initializer tốt thường ngắn, xác định, không phụ thuộc I/O dễ lỗi và không tạo side effect khó hoàn tác.

## 20. Bài tập tự kiểm tra

1. Phân biệt class initialization với instance construction trong Java.
2. Biểu thức khởi tạo trường `static` có nằm trong quy trình class initialization không?
3. Vì sao `static { Files.readString(path); }` thường không biên dịch?
4. Có thể ghi `throws IOException` trực tiếp vào phần khai báo static initializer không?
5. Thêm `throws IOException` vào constructor có cho phép checked exception thoát khỏi static block không?
6. Nếu `static` bắt `IOException` và ném `IllegalStateException`, caller active use lần đầu thường thấy loại nào?
7. `ExceptionInInitializerError` thuộc nhánh `Exception` hay `Error`?
8. `catch(Exception)` có bắt `ExceptionInInitializerError` không?
9. Nếu static initializer ném trực tiếp `AssertionError`, JVM có bắt buộc bọc nó bằng `ExceptionInInitializerError` không?
10. Sau một lần class initialization thất bại, lần dùng sau thường nhận lỗi gì?
11. Có phải mọi `NoClassDefFoundError` đều do thiếu file class?
12. Vì sao `static final int X = 7` thường không kích hoạt khởi tạo lớp khi được đọc?
13. Đọc `static int X = calculate()` có thể kích hoạt class initialization không?
14. Lấy `SomeClass.class` có tự chạy `static` block không?
15. Gọi một phương thức `static` có thể kích hoạt initialization không?
16. Vì sao một khối `static` chỉ có `throw new RuntimeException()` vô điều kiện không hợp lệ?
17. Lỗi trong constructor có tự làm class erroneous không?
18. Có thể dùng try-with-resources trong quá trình class initialization không?
19. Nếu `finally` chạy khi khởi tạo thất bại, class có trở lại dùng được không?
20. Cách nào hợp lý hơn static initializer nếu công việc đọc cấu hình có thể retry?
21. Lazy holder có bảo đảm retry khi lần tạo `INSTANCE` đầu tiên lỗi không?
22. Tại sao các công việc phụ thuộc đa luồng không nên nằm trong static initialization?
23. Khi gặp `NoClassDefFoundError: Could not initialize class ...`, nên tìm log nào?
24. Một checked exception `IOException` được giữ ở đâu trong cause chain `ExceptionInInitializerError → IllegalStateException → IOException`?
25. Khi nào fallback trong static block là hợp lý?

### Đáp án gợi ý

1. Class initialization chạy các trạng thái static theo cơ chế JVM; constructor chạy khi tạo một instance.
2. Có.
3. Vì `IOException` là checked và chưa được bắt/xử lý.
4. Không.
5. Không.
6. Thường là `ExceptionInInitializerError`.
7. Nhánh `Error` (qua `LinkageError`).
8. Không.
9. Không; một `Error` có thể được truyền trực tiếp ở lần lỗi đầu.
10. Thường là `NoClassDefFoundError`.
11. Không; nó cũng có thể báo class đã khởi tạo thất bại trước đó.
12. Vì `X` là compile-time constant có thể được inline.
13. Có.
14. Không tự kích hoạt initialization.
15. Có.
16. Do quy tắc static initializer phải có khả năng hoàn tất bình thường.
17. Không, nếu bản thân quá trình class initialization đã thành công.
18. Có; nó đóng tài nguyên trên đường rời scope Java thông thường, nhưng không sửa lỗi initialization.
19. Không; dọn dẹp và trạng thái class erroneous là hai việc khác nhau.
20. Phương thức factory/loader có khai báo `throws` và chính sách retry rõ.
21. Không; holder cũng có thể erroneous.
22. Vì initialization lock và phụ thuộc vòng có thể gây chờ hoặc deadlock.
23. Lỗi **đầu tiên** khi static initializer/field initializer thực thi, cùng cause chain.
24. Là cause của `IllegalStateException`, tức cause sâu hơn của `ExceptionInInitializerError`.
25. Khi trạng thái mặc định an toàn, có ý nghĩa và được API cho phép.

## 21. Tổng kết

**Khối `static` không có `throws` và không được để checked exception thoát trực tiếp.** Nếu công việc khởi tạo có thể ném `IOException`, phải bắt/xử lý hoặc thay đổi kiến trúc để lỗi xuất hiện trong một phương thức có hợp đồng `throws` rõ ràng. Khi class initialization thất bại do ngoại lệ không phải `Error`, JVM thường chuyển nó thành `ExceptionInInitializerError`; khi lỗi gốc là `Error`, nó có thể được truyền trực tiếp. Sau thất bại, class thường trở thành **erroneous**, khiến những active use tiếp theo nhận `NoClassDefFoundError` thay vì chạy initializer lại.

Với cấu hình, kết nối hoặc tài nguyên có khả năng thất bại tạm thời, **factory/loader và vòng đời được quản lý rõ ràng** thường an toàn, dễ retry, dễ kiểm thử hơn việc phụ thuộc vào static initializer.

</details>

- [Quay lại đầu trang](#back-to-top)
