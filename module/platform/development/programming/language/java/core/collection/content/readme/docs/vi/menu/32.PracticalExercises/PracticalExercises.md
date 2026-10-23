<a id="back-to-top"></a>

# Thực hành tổng hợp

## Menu
- [Quản lý danh sách có chèn, cập nhật, tìm kiếm và xóa](#practical-exercises-001)
- [Loại trùng dữ liệu nhưng giữ thứ tự xuất hiện](#practical-exercises-002)
- [Đếm tần suất và nhóm dữ liệu bằng Map](#practical-exercises-003)
- [Xây dựng chỉ mục nhiều giá trị với Map&lt;K,List&lt;V&gt;&gt;](#practical-exercises-004)
- [Tìm khoảng, phần tử gần nhất và phân trang bằng cấu trúc có thứ tự](#practical-exercises-005)
- [Giải bài toán top-K và hàng đợi tác vụ theo ưu tiên](#practical-exercises-006)
- [So sánh LRU đơn giản với yêu cầu của bộ nhớ đệm thực tế](#practical-exercises-007)
- [Xây dựng bài toán sản xuất–tiêu thụ bằng BlockingQueue](#practical-exercises-008)
- [Lập bảng quyết định chọn collection và tự đánh giá qua tình huống](#practical-exercises-009)

## <a id="practical-exercises-001">Quản lý danh sách có chèn, cập nhật, tìm kiếm và xóa</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành tổng hợp: Quản lý danh sách có chèn, cập nhật, tìm kiếm và xóa trong Java

## 1. Bài toán và mục tiêu

Xây dựng một chương trình Java quản lý **danh sách sách** bằng Java Collections. Mỗi sách có **mã** (`id`), **tên** (`title`) và **năm xuất bản** (`year`). Đây là dữ liệu minh họa; có thể thay sách bằng sinh viên, sản phẩm hoặc công việc mà vẫn giữ nguyên nguyên lý quản lý danh sách.

Sau bài thực hành, bạn cần có khả năng:

- Dùng `List<T>` và `ArrayList<T>` để lưu các đối tượng cùng kiểu, giữ thứ tự và truy cập theo chỉ số.
- **Thêm cuối** và **chèn tại vị trí** được chỉ định, hiểu cách các phần tử phía sau dịch chuyển.
- **Cập nhật** phần tử theo mã định danh, không nhầm cập nhật với chèn mới.
- **Tìm kiếm** theo mã chính xác và tìm nhiều kết quả theo một phần tên, không phân biệt chữ hoa/chữ thường.
- **Xóa** đúng phần tử theo mã; xử lý trường hợp không tìm thấy.
- Kiểm soát mã trùng, dữ liệu không hợp lệ, vị trí vượt phạm vi và việc sửa danh sách từ bên ngoài.
- Hiểu độ phức tạp của từng thao tác để chọn cấu trúc dữ liệu phù hợp.

### Quy tắc nghiệp vụ

1. Mỗi sách có mã **duy nhất**. Mã `B01` khác `b01` (so sánh có phân biệt chữ hoa/chữ thường).
2. Mã, tên phải có nội dung sau khi bỏ khoảng trắng đầu/cuối; năm xuất bản là số nguyên dương.
3. Vị trí chèn là chỉ số **bắt đầu từ 0**. Với danh sách có `n` phần tử, chỉ số chèn hợp lệ là `0` đến `n`; còn chỉ số của phần tử hiện có là `0` đến `n - 1`.
4. Khi sửa sách, **mã giữ nguyên**; chỉ tên và năm xuất bản được cập nhật. Nếu không tồn tại mã, thao tác trả về `false`.
5. Tìm kiếm hoặc xóa mã không tồn tại phải cho kết quả xác định (`Optional.empty()` hoặc `false`), không làm hỏng danh sách.
6. Danh sách trả ra cho mã gọi là **bản chụp chỉ đọc**, tránh để bên ngoài tùy ý thêm/xóa và phá vỡ quy tắc mã duy nhất.

## 2. Vì sao chọn `ArrayList`?

`List<Book>` diễn tả giao diện thao tác với danh sách; `new ArrayList<>()` cung cấp cách cài đặt bằng mảng động. `ArrayList` giữ thứ tự chèn và cho phép truy cập `get(index)` nhanh. Nó phù hợp với bài toán quy mô nhỏ và vừa, nơi việc duyệt và tìm tuyến tính là chấp nhận được.

Ví dụ, ban đầu danh sách có `[B01, B03]`:

- `add(B02)` tạo `[B01, B03, B02]`: thêm **cuối**.
- `add(1, B02)` tạo `[B01, B02, B03]`: **chèn** tại vị trí 1, đẩy `B03` sang phải.
- `set(1, B02Moi)` **thay thế** phần tử đang ở vị trí 1; không đổi độ dài danh sách.
- `remove(1)` xóa phần tử ở vị trí 1, các phần tử phía sau dịch sang trái.

Điểm quan trọng: **mã sách là danh tính nghiệp vụ; chỉ số là vị trí hiện tại**. Sau một lần chèn hay xóa, chỉ số có thể đổi, vì vậy chương trình tìm theo mã rồi mới xác định vị trí cần cập nhật/xóa. Không sử dụng chỉ số lưu từ lần truy cập cũ để nhận diện một cuốn sách.

## 3. Chương trình hoàn chỉnh chạy với Java 17+

Tạo tệp `Main.java` chứa toàn bộ mã bên dưới. Ví dụ cố ý dùng dữ liệu xác định để bạn có thể đối chiếu đầu ra. `Book` là `record` bất biến; `BookManager` đóng gói danh sách và cung cấp các thao tác thêm, chèn, cập nhật, tìm kiếm, xóa.

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public class Main {

    record Book(String id, String title, int year) {
        Book {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Mã sách không được trống");
            }
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("Tên sách không được trống");
            }
            if (year <= 0) {
                throw new IllegalArgumentException("Năm xuất bản phải dương");
            }
            id = id.strip();
            title = title.strip();
        }
    }

    static final class BookManager {
        private final List<Book> books = new ArrayList<>();

        // Thêm sách vào cuối danh sách.
        void add(Book book) {
            Objects.requireNonNull(book, "book");
            rejectDuplicateId(book.id());
            books.add(book);
        }

        // Chèn trước phần tử đang ở index; index == size() nghĩa là thêm cuối.
        void insert(int index, Book book) {
            Objects.requireNonNull(book, "book");
            if (index < 0 || index > books.size()) {
                throw new IndexOutOfBoundsException("Vị trí chèn: " + index);
            }
            rejectDuplicateId(book.id());
            books.add(index, book);
        }

        // Trả về false khi không có mã tương ứng; giữ nguyên mã sách khi sửa.
        boolean update(String id, String newTitle, int newYear) {
            int index = indexOfId(id);
            if (index == -1) {
                return false;
            }
            Book replacement = new Book(id, newTitle, newYear);
            books.set(index, replacement);
            return true;
        }

        // Tra cứu theo mã chính xác.
        Optional<Book> findById(String id) {
            int index = indexOfId(id);
            return index == -1
                    ? Optional.empty()
                    : Optional.of(books.get(index));
        }

        // Tìm tất cả sách có tên chứa từ khóa, không phân biệt hoa/thường.
        List<Book> searchByTitle(String keyword) {
            Objects.requireNonNull(keyword, "keyword");
            String needle = keyword.strip().toLowerCase(Locale.ROOT);
            if (needle.isEmpty()) {
                return List.of();
            }

            List<Book> matches = new ArrayList<>();
            for (Book book : books) {
                if (book.title().toLowerCase(Locale.ROOT).contains(needle)) {
                    matches.add(book);
                }
            }
            return List.copyOf(matches);
        }

        // Xóa theo mã; không thay đổi danh sách nếu mã không tồn tại.
        boolean delete(String id) {
            int index = indexOfId(id);
            if (index == -1) {
                return false;
            }
            books.remove(index);
            return true;
        }

        // Trả về bản chụp chỉ đọc, không lộ danh sách nội bộ.
        List<Book> list() {
            return List.copyOf(books);
        }

        private void rejectDuplicateId(String id) {
            if (indexOfId(id) != -1) {
                throw new IllegalArgumentException(
                        "Mã sách đã tồn tại: " + id);
            }
        }

        private int indexOfId(String id) {
            Objects.requireNonNull(id, "id");
            for (int i = 0; i < books.size(); i++) {
                if (books.get(i).id().equals(id)) {
                    return i;
                }
            }
            return -1;
        }
    }

    public static void main(String[] args) {
        BookManager manager = new BookManager();

        manager.add(new Book("B01", "Effective Java", 2018));
        manager.add(new Book("B03", "Clean Code", 2008));
        manager.insert(1, new Book(
                "B02", "Java Concurrency in Practice", 2006));

        System.out.println("Sau khi chèn: " + manager.list());
        System.out.println("Cập nhật B03: "
                + manager.update("B03", "Clean Code - revised", 2009));
        System.out.println("Tìm mã B02: "
                + manager.findById("B02").orElseThrow());
        System.out.println("Tìm tên java: "
                + manager.searchByTitle("java"));
        System.out.println("Xóa B01: " + manager.delete("B01"));
        System.out.println("Còn lại: " + manager.list());
        System.out.println("Xóa mã lạ: " + manager.delete("B99"));

        try {
            manager.add(new Book("B02", "Duplicate", 2020));
        } catch (IllegalArgumentException ex) {
            System.out.println("Trùng mã: " + ex.getMessage());
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa `Main.java`:

```bash
javac Main.java
java Main
```

Đầu ra tương ứng (mỗi dòng dưới đây là **một dòng stdout**; những dòng dài có thể tự xuống dòng do độ rộng terminal):

```text
Sau khi chèn: [Book[id=B01, title=Effective Java, year=2018], Book[id=B02, title=Java Concurrency in Practice, year=2006], Book[id=B03, title=Clean Code, year=2008]]
Cập nhật B03: true
Tìm mã B02: Book[id=B02, title=Java Concurrency in Practice, year=2006]
Tìm tên java: [Book[id=B01, title=Effective Java, year=2018], Book[id=B02, title=Java Concurrency in Practice, year=2006]]
Xóa B01: true
Còn lại: [Book[id=B02, title=Java Concurrency in Practice, year=2006], Book[id=B03, title=Clean Code - revised, year=2009]]
Xóa mã lạ: false
Trùng mã: Mã sách đã tồn tại: B02
```

### Diễn giải luồng thực thi

1. `add` đưa `B01` và `B03` vào cuối: `[B01, B03]`.
2. `insert(1, B02)` đưa `B02` vào giữa: `[B01, B02, B03]`; không ghi đè `B03`.
3. `update("B03", ...)` tìm chỉ số của `B03` và gọi `set` với bản ghi mới. Vị trí và kích thước danh sách giữ nguyên.
4. `findById("B02")` trả về `Optional<Book>` chứa đúng một sách; `searchByTitle("java")` trả về cả `B01` và `B02` theo thứ tự danh sách.
5. `delete("B01")` xóa sách đầu, để lại `[B02, B03]`; xóa `B99` trả về `false` và không thay đổi dữ liệu.
6. Thêm lại `B02` bị chặn vì trùng mã; ngoại lệ được bắt ở ranh giới chạy thử để người dùng nhìn thấy lý do.

## 4. Hiểu từng thao tác CRUD

### 4.1. Thêm cuối và chèn có vị trí — Create

`books.add(book)` thêm vào cuối. `books.add(index, book)` chèn **trước** vị trí `index`. Nếu `index == books.size()` thì chèn cuối là hợp lệ, kể cả danh sách rỗng với `index == 0`. Nếu `index < 0` hoặc `index > size()`, cần báo lỗi; không tự ý đổi một vị trí không hợp lệ thành vị trí cuối.

Phần kiểm tra mã trùng đặt **trước khi sửa danh sách**. Như vậy, cùng mã sẽ không vô tình tạo ra hai bản ghi khiến `update` hoặc `delete` trở nên mơ hồ. Trong ví dụ, kiểm tra trùng dùng tìm tuyến tính; cách này đơn giản và giữ mọi dữ liệu trong một danh sách duy nhất.

### 4.2. Cập nhật theo mã — Update

Các bước: tìm chỉ số bằng `indexOfId(id)` → nếu `-1` thì trả về `false` → tạo `Book` mới đã được kiểm tra dữ liệu → `books.set(index, replacement)`. Vì `Book` là record bất biến, cập nhật nghĩa là **thay đối tượng tại đúng vị trí**, không thay trực tiếp trường của đối tượng.

`set(index, value)` khác `add(index, value)`: `set` giữ số lượng phần tử, còn `add` tăng số lượng thêm một. Khi sửa, không dùng `add` vì sẽ tạo bản ghi thừa và có thể làm trùng mã.

### 4.3. Tìm kiếm theo mã và theo tên — Read

- **Theo mã:** dùng `equals` để khớp chính xác và trả `Optional<Book>`. Bên gọi có thể dùng `orElse(...)`, `ifPresent(...)` hoặc `orElseThrow()` tùy việc không tìm thấy có phải lỗi hay không. Chỉ dùng `orElseThrow()` khi chắc mã tồn tại, như phần chạy thử.
- **Theo tên:** chuyển từ khóa và tên về chữ thường bằng `Locale.ROOT` rồi dùng `contains` để so khớp một phần; trả nhiều kết quả, giữ thứ tự của danh sách ban đầu. Từ khóa chỉ chứa khoảng trắng cho danh sách rỗng, tránh biến truy vấn trống thành “tìm tất cả” ngoài ý muốn.
- **Xem toàn bộ:** `list()` trả `List.copyOf(books)`. Đây là danh sách không cho sửa cấu trúc và là bản chụp tại thời điểm gọi; nó không tự cập nhật khi `BookManager` thay đổi về sau. Các phần tử `Book` cũng bất biến nên người gọi không thể sửa ngầm dữ liệu nội bộ qua tham chiếu của chúng.

### 4.4. Xóa theo mã — Delete

`indexOfId` xác định đối tượng theo mã, sau đó `books.remove(index)` loại bỏ đúng vị trí. Khi xóa phần tử đầu hoặc giữa, các phần tử sau dịch trái và chỉ số thay đổi. Hàm trả `true` nếu có sách được xóa; `false` nếu không có mã tương ứng. Không truyền thẳng mã `String` cho `remove(Object)` vì danh sách đang chứa `Book`.

Nếu cần xóa nhiều sách theo điều kiện, `removeIf` hoặc `Iterator.remove()` là lựa chọn phù hợp. Không nên gọi `books.remove(...)` trực tiếp trong vòng `for-each` trên cùng danh sách, vì đó là sửa cấu trúc trong khi bộ lặp đang duyệt và có thể phát sinh `ConcurrentModificationException`.

## 5. Kiểm tra biên và kiểm thử chức năng

Các tình huống tối thiểu cần thử, bên cạnh luồng thành công trong `main`:

| Tình huống | Thao tác | Kết quả mong đợi |
| --- | --- | --- |
| Thêm vào danh sách rỗng | `add(B01)` | Danh sách có một sách |
| Chèn đầu | `insert(0, B00)` | `B00` đứng trước các sách cũ |
| Chèn cuối | `insert(size(), B99)` | `B99` đứng cuối |
| Chèn vượt phạm vi | `insert(-1, ...)` hoặc `insert(size()+1, ...)` | `IndexOutOfBoundsException`, danh sách không đổi |
| Trùng mã | Thêm/chèn sách có mã đã có | `IllegalArgumentException`, danh sách không đổi |
| Sửa thành công | `update("B03", ..., 2009)` | Thay tên/năm, giữ mã, giữ vị trí và số lượng |
| Sửa mã không tồn tại | `update("B99", ..., 2024)` | `false`, danh sách không đổi |
| Tìm mã không tồn tại | `findById("B99")` | `Optional.empty()` |
| Tìm tên không phân biệt hoa/thường | `searchByTitle("JAVA")` | Nhận các sách có tên chứa “java” |
| Từ khóa rỗng | `searchByTitle("   ")` | Danh sách kết quả rỗng |
| Xóa thành công | `delete("B01")` | `true`, chỉ xóa `B01` |
| Xóa lại cùng mã | `delete("B01")` lần nữa | `false` |
| Mã/tên rỗng hoặc năm ≤ 0 | `new Book(... )` | `IllegalArgumentException` |
| Cố thay đổi danh sách trả về | `manager.list().clear()` | `UnsupportedOperationException`; dữ liệu trong manager giữ nguyên |

Có thể tự bổ sung các lời gọi kiểm thử sau vào `main` **sau khi** đã chạy ví dụ gốc (không cần thêm thư viện kiểm thử):

```java
BookManager check = new BookManager();
check.insert(0, new Book("S1", "Java Basics", 2023));
if (check.list().size() != 1) {
    throw new AssertionError("Chèn vào danh sách rỗng thất bại");
}
if (check.update("S9", "Missing", 2024)) {
    throw new AssertionError("Không được sửa mã không tồn tại");
}
if (!check.findById("S1").isPresent()) {
    throw new AssertionError("Không tìm được sách vừa thêm");
}
if (!check.delete("S1") || check.delete("S1")) {
    throw new AssertionError("Xóa phải thành công đúng một lần");
}
System.out.println("Các kiểm tra bổ sung đã qua");
```

Đoạn trên là **mảnh mã chèn thêm bên trong `main`**, dùng chung các lớp `Book` và `BookManager` đã khai báo; không phải một tệp độc lập. Sau khi thêm, chỉ có thêm dòng `Các kiểm tra bổ sung đã qua` ở cuối đầu ra của chương trình.

### Bất biến cần giữ sau mọi thao tác

- Không có hai phần tử cùng `id`.
- Không có phần tử `null` hay sách vi phạm ràng buộc mã/tên/năm.
- Chèn tăng `size()` đúng 1; cập nhật giữ `size()`; xóa thành công giảm `size()` đúng 1.
- Thao tác không thành công vì không tìm thấy hoặc do tham số không hợp lệ không được âm thầm sửa danh sách.
- Thứ tự phần tử còn lại không bị đảo khi cập nhật hoặc xóa.

## 6. Độ phức tạp và lựa chọn mở rộng

Gọi `n` là số sách trong danh sách, `L` là độ dài tên hoặc từ khóa khi xét tìm kiếm chuỗi.

| Thao tác trong `BookManager` | Thời gian điển hình/trường hợp xấu | Giải thích |
| --- | --- | --- |
| `add` | `O(n)` | Việc thêm cuối vào `ArrayList` có chi phí trung bình phân bổ `O(1)`, nhưng kiểm tra trùng mã duyệt `O(n)` |
| `insert` | `O(n)` | Kiểm tra trùng và dịch các phần tử phía sau |
| `update` | `O(n)` | Tìm mã `O(n)`, thay tại chỉ số `O(1)` |
| `findById` | `O(n)` | Duyệt cho đến khi tìm thấy hoặc hết danh sách |
| `searchByTitle` | Khoảng `O(n × L)` | Duyệt `n` tên và so khớp chuỗi; chi phí thực phụ thuộc độ dài từng tên và thuật toán `contains` |
| `delete` | `O(n)` | Tìm mã và dịch phần tử sau khi xóa |
| `list` | `O(n)` | Tạo bản chụp bằng `List.copyOf` |

Bộ nhớ lưu sách là `O(n)`; mỗi lần trả danh sách kết quả hoặc bản chụp có thể dùng thêm `O(n)` bộ nhớ. Bảng tính thời gian thể hiện toàn bộ **hàm đang viết**, không chỉ lệnh `ArrayList.add` hay `set` bên trong.

Khi danh sách lớn và việc tra cứu theo mã xảy ra thường xuyên, cân nhắc kết hợp `List<Book>` để giữ thứ tự với `Map<String, Book>` để tìm theo mã nhanh hơn. Tuy nhiên, phải cập nhật **cả hai cấu trúc một cách nhất quán** ở mọi lần thêm, sửa và xóa. Với bài thực hành này, một `ArrayList` duy nhất dễ kiểm chứng hơn.

`ArrayList` không tự bảo đảm an toàn khi nhiều luồng cùng sửa; chương trình ví dụ chạy một luồng. Nếu dùng trong ứng dụng nhiều luồng, cần thiết kế đồng bộ hóa thích hợp thay vì giả định thao tác CRUD ở đây tự an toàn.

## 7. Lỗi thường gặp và cách tránh

1. **Nhầm chèn với ghi đè:** `add(index, x)` chèn, `set(index, x)` thay thế; kiểm tra `size()` sau mỗi thao tác.
2. **Dựa vào chỉ số để nhận diện phần tử:** chỉ số đổi sau chèn/xóa; hãy tìm theo mã ổn định.
3. **Không chặn trùng mã:** `update/delete` có thể chỉ xử lý bản ghi đầu tiên và bỏ sót bản ghi trùng; kiểm tra trước khi thêm.
4. **Dùng `==` để so sánh chuỗi:** dùng `String.equals`, vì `==` so sánh tham chiếu đối tượng.
5. **Đọc trực tiếp danh sách nội bộ:** trả `books` sẽ cho mã bên ngoài bỏ qua xác thực; sử dụng bản chụp chỉ đọc.
6. **Đổi nội dung trong vòng `for-each` theo cách không hợp lệ:** thao tác xóa cấu trúc nên dùng chỉ số sau khi đã tìm, `Iterator.remove()` hoặc `removeIf`.
7. **Xóa theo giá trị mà chưa hiểu `equals`:** `remove(Object)` so sánh đối tượng theo `equals`; với yêu cầu xóa theo mã, tìm chỉ số trước là rõ ràng hơn.
8. **Bắt mọi ngoại lệ rồi im lặng:** nhập sai hoặc chỉ số sai cần được báo rõ, trong khi không tìm thấy mã được thể hiện bằng `false`/`Optional.empty()` theo hợp đồng hàm.
9. **Dùng `List.of(...)` làm danh sách để sửa:** các danh sách tạo bởi `List.of` không hỗ trợ `add`/`set`/`remove`; sử dụng `new ArrayList<>()` để có danh sách thay đổi được.

## 8. Bài tập mở rộng tự làm

Sau khi ví dụ gốc chạy đúng, tự thực hiện lần lượt:

1. Thêm menu console bằng `Scanner`: xem danh sách, thêm cuối, chèn theo vị trí, sửa theo mã, tìm theo mã/tên, xóa theo mã và thoát. Kiểm tra số nhập vào trước khi gọi manager.
2. Viết chức năng `insertBeforeId(String existingId, Book newBook)`: tìm chỉ số của sách mốc rồi chèn sách mới; nếu sách mốc không có, trả trạng thái thất bại và không thay đổi danh sách.
3. Thêm tìm kiếm theo khoảng năm xuất bản, trả danh sách kết quả giữ thứ tự gốc.
4. Viết kiểm thử tự động (JUnit hoặc bộ kiểm tra độc lập) cho các trường hợp trong bảng ở mục 5, đặc biệt là mã trùng, chỉ số không hợp lệ và bất biến sau lỗi.
5. Nếu muốn tối ưu, dùng thêm `HashMap<String, Book>` và thiết kế phương án cập nhật đồng bộ giữa map và list; chứng minh rằng thêm/xóa không để lại dữ liệu lệch.

**Tiêu chí hoàn thành:** chương trình biên dịch và chạy bằng Java 17+, đủ bốn thao tác chính (chèn, cập nhật, tìm kiếm, xóa), có tình huống thành công và thất bại, bảo toàn mã duy nhất và thứ tự danh sách, thông báo kết quả rõ ràng, không cho bên ngoài sửa trực tiếp trạng thái nội bộ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-002">Loại trùng dữ liệu nhưng giữ thứ tự xuất hiện</a>

<details>
<summary>Xem chi tiết</summary>

# Loại trùng dữ liệu nhưng giữ thứ tự xuất hiện trong Java Collections

## 1. Bài toán và tiêu chí đúng

Cho một dãy phần tử có thể lặp lại, hãy loại các phần tử trùng nhau nhưng **giữ nguyên thứ tự xuất hiện lần đầu** của từng phần tử. Kết quả chứa mỗi giá trị logic đúng một lần, tại vị trí tương ứng với lần đầu giá trị đó xuất hiện trong dữ liệu nguồn.

Ví dụ:

```text
Đầu vào: [Java, SQL, Java, null, Go, SQL, null, Kotlin, Go]
Đầu ra: [Java, SQL, null, Go, Kotlin]
```

Trong ví dụ này, `Java` thứ hai, `SQL` thứ hai, `null` thứ hai và `Go` thứ hai bị bỏ. `null` đầu tiên vẫn tồn tại nếu bộ sưu tập và chính sách nghiệp vụ cho phép giá trị `null`.

Cần phân biệt ba yêu cầu thường bị nhầm lẫn:

- **Không trùng:** mỗi nhóm phần tử được xem là bằng nhau chỉ xuất hiện một lần.
- **Giữ thứ tự:** nếu lần đầu `A` đứng trước lần đầu `B` thì `A` vẫn đứng trước `B` trong kết quả.
- **Giữ bản ghi đầu tiên:** khi hai đối tượng có cùng khóa nhưng thuộc tính khác nhau, kết quả lấy đối tượng xuất hiện trước, trừ khi đề bài yêu cầu quy tắc khác.

Đây là bài thực hành Java Collections. Chọn cấu trúc dữ liệu phụ thuộc vào việc cần trả về `List`, `Set`, có được sửa dữ liệu đầu vào hay không, và khái niệm “trùng” là theo toàn bộ đối tượng hay theo một khóa nghiệp vụ.

## 2. Cách đơn giản: `LinkedHashSet`

`LinkedHashSet` là tập hợp loại bỏ phần tử trùng theo `equals()` và `hashCode()`, đồng thời duy trì **thứ tự chèn** khi duyệt. Khi một phần tử đã có trong tập hợp được thêm lại, tập hợp không tạo phần tử mới và không dời vị trí của phần tử cũ.

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public class LinkedHashSetDemo {
    public static void main(String[] args) {
        List<String> input = Arrays.asList(
                "Java", "SQL", "Java", null, "Go",
                "SQL", null, "Kotlin", "Go"
        );

        List<String> unique = new ArrayList<>(new LinkedHashSet<>(input));

        System.out.println("Input : " + input);
        System.out.println("Unique: " + unique);
    }
}
```

Lưu thành `LinkedHashSetDemo.java`; chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 LinkedHashSetDemo.java
java LinkedHashSetDemo
```

Kết quả:

```text
Input : [Java, SQL, Java, null, Go, SQL, null, Kotlin, Go]
Unique: [Java, SQL, null, Go, Kotlin]
```

Giải thích:

1. Bộ khởi tạo `new LinkedHashSet<>(input)` duyệt các phần tử của `input` và lần lượt thử thêm vào tập hợp.
2. Lần đầu gặp một giá trị, tập hợp lưu nó; các lần gặp tiếp theo mà `equals()` xác định bằng nhau sẽ không được thêm.
3. `LinkedHashSet` giữ thứ tự chèn, nên lần duyệt tập hợp cho thứ tự lần đầu xuất hiện.
4. `new ArrayList<>(...)` trả kết quả về kiểu `List` khi cần truy cập theo chỉ số hoặc tương thích API nhận `List`.

Nếu đầu ra có thể là `Set`, chỉ cần giữ `LinkedHashSet`:

```java
LinkedHashSet<String> uniqueSet = new LinkedHashSet<>(input);
```

`LinkedHashSet` cho phép một phần tử `null`; tuy nhiên `List.of(...)` không cho phép phần tử `null` ngay từ lúc khởi tạo. Vì vậy ví dụ dùng `Arrays.asList(...)`. Cách trên tạo một bộ sưu tập mới, không sửa `input`.

### Vì sao không dùng `HashSet` hoặc `TreeSet` để lấy kết quả trực tiếp?

- `HashSet` loại trùng nhưng **không cam kết thứ tự duyệt**. Một kết quả trông như đúng thứ tự trong một lần chạy không phải là bảo đảm của API.
- `TreeSet` sắp xếp theo thứ tự tự nhiên hoặc `Comparator`, không giữ thứ tự gặp đầu tiên. Nó còn có điều kiện bổ sung về khả năng so sánh, và cách nhận diện trùng dựa trên so sánh `compare(...) == 0`.
- `LinkedHashSet` đáp ứng đồng thời yêu cầu không trùng và giữ thứ tự chèn. Đừng dùng mã kiểu `new ArrayList<>(new HashSet<>(input))` nếu đầu ra bắt buộc giữ nguyên thứ tự.

## 3. Cách duyệt một lần: `HashSet` đánh dấu + `ArrayList` kết quả

Nếu muốn giải thích rõ thuật toán, hoặc cần điều kiện giữ/bỏ tùy chỉnh, hãy duyệt đầu vào từ trái sang phải, dùng một `Set` để đánh dấu giá trị đã gặp, và chỉ đưa giá trị vào `List` kết quả ở lần xuất hiện đầu tiên.

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SeenSetDemo {
    public static void main(String[] args) {
        List<String> input = Arrays.asList(
                "Java", "SQL", "Java", null, "Go",
                "SQL", null, "Kotlin", "Go"
        );
        Set<String> seen = new HashSet<>();
        List<String> unique = new ArrayList<>();

        for (String value : input) {
            if (seen.add(value)) {
                unique.add(value);
            }
        }
        System.out.println(unique);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 SeenSetDemo.java
java SeenSetDemo
```

```text
[Java, SQL, null, Go, Kotlin]
```

`Set.add(value)` trả `true` nếu tập hợp thay đổi vì thêm một phần tử chưa có; trả `false` nếu phần tử đã tồn tại. Đây là phép kiểm tra và đánh dấu trong cùng một thao tác, tránh viết thừa `contains(value)` rồi mới `add(value)`.

Lưu ý: `HashSet` ở đây **không** được duyệt để xây dựng kết quả; nó chỉ giữ thông tin “đã thấy”. Chính `ArrayList` được thêm phần tử theo thứ tự vòng lặp mới quyết định thứ tự đầu ra. Vì vậy sử dụng `HashSet` làm bộ đánh dấu vẫn hoàn toàn đáp ứng yêu cầu giữ thứ tự.

Một cách khác để sửa trực tiếp danh sách **có thể thay đổi**:

```java
List<String> mutable = new ArrayList<>(input);
Set<String> seen = new HashSet<>();
mutable.removeIf(value -> !seen.add(value));
```

`removeIf` loại lần xuất hiện thứ hai trở đi và giữ thứ tự tương đối của các phần tử còn lại. Cách này làm thay đổi `mutable`; không áp dụng trực tiếp cho `List.of(...)` hoặc danh sách cố định kích thước do `Arrays.asList(...)` trả về, vì chúng không hỗ trợ thao tác xóa. Ưu tiên tạo danh sách kết quả mới nếu cần giữ đầu vào nguyên vẹn hoặc muốn mã dễ hiểu.

## 4. Stream API với `distinct()`

Với Java Stream, toán tử `distinct()` loại phần tử trùng dựa trên `Object.equals(Object)`. Với stream **có thứ tự** (ordered stream), `distinct()` giữ phần tử đầu tiên trong mỗi nhóm trùng nhau theo encounter order, tức thứ tự mà nguồn dữ liệu đưa phần tử vào stream.

```java
import java.util.Arrays;
import java.util.List;

public class StreamDistinctDemo {
    public static void main(String[] args) {
        List<String> input = Arrays.asList(
                "Java", "SQL", "Java", null, "Go",
                "SQL", null, "Kotlin", "Go"
        );

        List<String> unique = input.stream()
                .distinct()
                .toList();

        System.out.println(unique);
    }
}
```

```bash
javac -encoding UTF-8 StreamDistinctDemo.java
java StreamDistinctDemo
```

```text
[Java, SQL, null, Go, Kotlin]
```

Các điểm cần nhớ:

- `List.stream()` là stream có thứ tự theo trình tự duyệt danh sách. Với nguồn này, `distinct()` giữ lần xuất hiện đầu.
- `Stream.toList()` có từ Java 16 và trả về `List` **không thể sửa**; gọi `add` hoặc `remove` trên kết quả sẽ ném `UnsupportedOperationException`. Nếu cần danh sách thay đổi được, dùng `new ArrayList<>(input.stream().distinct().toList())` hoặc `collect(Collectors.toCollection(ArrayList::new))`.
- `stream().unordered()` bỏ yêu cầu bảo toàn encounter order. Trên stream không có thứ tự, đừng dựa vào việc phần tử nào được giữ trong một nhóm trùng hoặc thứ tự đầu ra.
- `parallelStream().distinct()` trên stream có thứ tự vẫn phải bảo đảm tính ổn định của `distinct`, nhưng việc phối hợp để giữ thứ tự có thể tốn bộ nhớ và chi phí đồng bộ; không mặc định song song sẽ nhanh hơn.
- `distinct()` không nhận hàm trích xuất khóa. Nếu “trùng” dựa trên `id`, `email` hoặc quy tắc chuẩn hóa riêng thì cần thuật toán theo khóa như phần tiếp theo.

## 5. Trùng đối tượng: `equals()`, `hashCode()` và khóa nghiệp vụ

Với phần tử kiểu đối tượng, “trùng” phụ thuộc vào cách định nghĩa sự bằng nhau. `HashSet`, `LinkedHashSet` dùng `equals()` và `hashCode()`: nếu hai đối tượng bằng nhau theo `equals()`, chúng **phải** có cùng `hashCode()`. Nếu lớp tự định nghĩa một trong hai phương thức mà không tuân thủ hợp đồng chung, bộ sưu tập dựa trên hash có thể hoạt động sai.

Ví dụ `record` trong Java tự sinh `equals()` và `hashCode()` dựa trên các thành phần khai báo. Hai `Employee` có cùng `id` nhưng khác `name` vẫn **khác nhau** nếu dùng bình đẳng mặc định của record. Để loại trùng theo `id` mà vẫn giữ bản ghi đầu tiên, hãy tách rõ khóa nghiệp vụ khỏi bình đẳng của toàn đối tượng.

```java
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public class DistinctByKeyDemo {
    record Employee(long id, String name) {}

    static <T, K> List<T> distinctByKey(
            Iterable<T> items,
            Function<? super T, ? extends K> keyExtractor) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(keyExtractor, "keyExtractor");

        Set<K> seenKeys = new HashSet<>();
        List<T> result = new ArrayList<>();

        for (T item : items) {
            K key = keyExtractor.apply(item);
            if (seenKeys.add(key)) {
                result.add(item);
            }
        }
        return result;
    }

    public static void main(String[] args) {
        List<Employee> employees = List.of(
                new Employee(1, "An"),
                new Employee(2, "Bình"),
                new Employee(1, "An cập nhật"),
                new Employee(3, "Chi"),
                new Employee(2, "Bình mới")
        );

        System.out.println(distinctByKey(employees, Employee::id));
    }
}
```

```bash
javac -encoding UTF-8 DistinctByKeyDemo.java
java DistinctByKeyDemo
```

```text
[Employee[id=1, name=An], Employee[id=2, name=Bình], Employee[id=3, name=Chi]]
```

Thuật toán giữ chính đối tượng đầu tiên của mỗi `id`; bản ghi có cùng `id` nhưng xuất hiện sau bị bỏ dù `name` khác. Hàm nhận `Iterable<T>`, nên cũng dùng được cho `List<T>` hoặc một nguồn có thể duyệt theo thứ tự xác định.

Một số quyết định cần làm rõ trước khi áp dụng:

- **Khóa nào định nghĩa trùng?** Toàn đối tượng, `id`, email đã chuẩn hóa, hay tổ hợp nhiều thuộc tính? Hai quy tắc khác nhau có thể cho kết quả khác nhau.
- **Giữ bản ghi nào?** Bài này giữ **bản ghi đầu tiên**. Nếu yêu cầu giữ bản ghi cuối hoặc gộp thông tin, phải thay đổi thuật toán và nêu rõ thứ tự đầu ra mong muốn.
- **Khóa `null` có hợp lệ không?** `HashSet` chấp nhận một `null`, nên toàn bộ phần tử có khóa `null` sẽ rơi vào cùng một nhóm; hãy kiểm tra/từ chối khóa rỗng trước khi thêm nếu nghiệp vụ không cho phép.
- **Khóa có ổn định không?** Tránh sửa các thuộc tính tham gia `equals()/hashCode()` khi đối tượng đang nằm trong `HashSet`/`LinkedHashSet`. Việc thay đổi hash hoặc bình đẳng sau khi chèn có thể khiến tìm kiếm/loại trùng không còn đúng.
- **Thứ tự nguồn có xác định không?** Nếu duyệt từ một `HashSet` đầu vào, nguồn vốn không cam kết thứ tự; thuật toán chỉ giữ thứ tự mà iterator thực sự cung cấp, không thể khôi phục thứ tự chèn đã mất.

## 6. Chuẩn hóa trước khi xét trùng

Đôi khi `"Java"` và `"java"` được xem là trùng nhau, nhưng vẫn phải giữ đúng cách viết của **lần xuất hiện đầu tiên**. Không cần biến đổi phần tử đưa vào kết quả; chỉ chuẩn hóa **khóa** để so sánh.

```java
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CaseInsensitiveDemo {
    public static void main(String[] args) {
        List<String> input = List.of("Java", "java", "GO", "go", "SQL");

        Set<String> seenKeys = new HashSet<>();
        List<String> unique = new ArrayList<>();

        for (String value : input) {
            String key = value.toLowerCase(Locale.ROOT);
            if (seenKeys.add(key)) {
                unique.add(value);
            }
        }
        System.out.println(unique);
    }
}
```

```bash
javac -encoding UTF-8 CaseInsensitiveDemo.java
java CaseInsensitiveDemo
```

```text
[Java, GO, SQL]
```

`Locale.ROOT` tạo quy tắc lowercase ít phụ thuộc ngôn ngữ/địa phương khi xử lý khóa kỹ thuật. Tuy nhiên việc chuyển sang chữ thường đơn thuần **không phải** chuẩn hóa Unicode hoàn chỉnh cho mọi ngôn ngữ. Nếu dữ liệu có dấu, ký tự tổ hợp, khoảng trắng hoặc email, hãy định nghĩa rõ quy tắc tương đương; tùy yêu cầu có thể cần `trim()`, `Normalizer`, hoặc quy tắc theo miền nghiệp vụ. Đừng tự loại dấu hay bỏ khoảng trắng nếu bài toán không yêu cầu vì có thể gộp nhầm các giá trị khác nhau.

Đầu vào có thể có `null` thì phải đặt chính sách trước khi gọi phương thức trên phần tử, chẳng hạn giữ một `null` riêng hoặc bỏ `null`. Ví dụ trên dùng `List.of` và chỉ chứa chuỗi khác `null`.

## 7. Độ phức tạp và cách chọn

Gọi `n` là số phần tử đầu vào và `u` là số giá trị/khóa khác nhau (`u ≤ n`).

| Phương án | Giữ thứ tự lần đầu? | Thời gian dự kiến | Bộ nhớ phụ | Đặc điểm |
| --- | --- | --- | --- | --- |
| `LinkedHashSet` rồi chuyển `ArrayList` | Có | Trung bình `O(n)` | `O(u)` | Ngắn gọn, giữ nguyên đầu vào, mặc định so theo toàn đối tượng |
| `HashSet` + `ArrayList` khi duyệt | Có | Trung bình `O(n)` | `O(u)` | Linh hoạt về khóa/điều kiện; dễ thấy quy tắc giữ lần đầu |
| `stream().distinct().toList()` trên stream có thứ tự | Có | Thường gần `O(n)` | Phụ thuộc triển khai và số phần tử khác nhau | Diễn đạt gọn; `toList()` không thể sửa |
| Duyệt rồi dùng `result.contains(value)` | Có | Tệ nhất `O(n²)` | `O(u)` | Dễ viết nhưng không phù hợp dữ liệu lớn |

Các ước lượng hash giả định `hashCode()` phân bố đủ tốt và phép tính hash/`equals()` có chi phí hợp lý; không có cam kết rằng mọi đầu vào đều đạt `O(n)` trong mọi tình huống. Bộ nhớ phụ chưa tính bản thân đầu vào, và kết quả `List` có thể tốn thêm `O(u)` ngoài cấu trúc đánh dấu.

Không thể suy ra rằng `LinkedHashSet` luôn nhanh hơn Stream cho mọi cỡ dữ liệu hoặc môi trường; nếu hiệu năng là yêu cầu quan trọng, hãy đo bằng dữ liệu đại diện. Còn trong bài toán thường gặp, `LinkedHashSet` là lựa chọn trực tiếp nhất; khi cần loại trùng **theo khóa nghiệp vụ**, dùng vòng lặp với `seenKeys` và `result`.

## 8. Những lỗi dễ gặp

1. **Dùng `HashSet` làm đầu ra rồi tưởng rằng vẫn giữ thứ tự.** API không đảm bảo điều đó, kể cả khi vài bộ dữ liệu thử cho kết quả “đúng”.
2. **Dùng `TreeSet` trong khi yêu cầu giữ lần xuất hiện đầu.** Thứ tự sắp xếp không phải thứ tự xuất hiện; một `Comparator` trả 0 cho hai đối tượng còn có thể khiến chúng bị xem là trùng.
3. **Không định nghĩa thế nào là “trùng”.** Hai record có cùng `id` nhưng tên khác nhau sẽ không trùng theo `equals` mặc định; đôi khi cần khóa thay vì toàn đối tượng.
4. **Bỏ yêu cầu thứ tự của Stream.** Với `unordered()`, không có bảo đảm giữ phần tử gặp đầu tiên.
5. **Làm biến đổi đầu vào ngoài ý muốn.** `removeIf` là phép sửa danh sách; chỉ dùng khi được phép và danh sách hỗ trợ xóa.
6. **Cố sửa danh sách từ `Stream.toList()`.** Danh sách này không thể thay đổi; tạo `ArrayList` mới nếu cần thao tác tiếp.
7. **Quên xử lý `null`.** `List.of` không chứa được `null`; hàm chuẩn hóa kiểu `value.toLowerCase(...)` cũng không gọi được trên `null`.
8. **Dựa vào thứ tự của nguồn vốn không có thứ tự.** Nếu đầu vào là `HashSet`, không thể khẳng định kết quả giữ “thứ tự ban đầu” theo nghĩa thứ tự dữ liệu được thêm trước đó.
9. **Sử dụng trạng thái `seen` dùng chung giữa nhiều lần gọi.** Nếu không xóa đúng lúc, các lần xử lý sau sẽ mất phần tử hợp lệ; mỗi lượt khử trùng thường cần tập `seen` riêng.
10. **Chạy nhiều luồng cùng thêm vào `HashSet`/`ArrayList` không đồng bộ.** Các bộ sưu tập này không thread-safe. Khi cần xử lý đồng thời, phải thiết kế riêng cách đồng bộ và thứ tự kết quả; không chia vòng lặp tùy tiện.

## 9. Tự kiểm tra bằng ca thử

Một lời giải tốt cần vượt qua nhiều loại đầu vào, không chỉ ví dụ có vài chuỗi:

| Đầu vào | Kết quả cần đạt | Mục đích |
| --- | --- | --- |
| `[]` | `[]` | Danh sách rỗng |
| `[A]` | `[A]` | Một phần tử |
| `[A, A, A]` | `[A]` | Mọi phần tử trùng nhau |
| `[B, A, B, C, A]` | `[B, A, C]` | Không sắp xếp lại |
| `[null, A, null]` | `[null, A]` | Chấp nhận một `null` nếu chính sách cho phép |
| `[Java, java, JAVA]` | `[Java, java, JAVA]` | So sánh chuỗi mặc định có phân biệt hoa/thường |
| `[Java, java, JAVA]` với khóa lowercase | `[Java]` | Trùng theo khóa, giữ cách viết đầu tiên |

Bạn có thể viết phương thức tái sử dụng đơn giản để tự kiểm tra:

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public class UniqueInOrder {
    static <T> List<T> uniqueInOrder(Collection<? extends T> input) {
        return new ArrayList<>(new LinkedHashSet<>(input));
    }

    public static void main(String[] args) {
        System.out.println(uniqueInOrder(List.of("B", "A", "B", "C", "A")));
        System.out.println(uniqueInOrder(List.<String>of()));
    }
}
```

```bash
javac -encoding UTF-8 UniqueInOrder.java
java UniqueInOrder
```

```text
[B, A, C]
[]
```

`Collection` phù hợp khi đầu vào đã là một bộ sưu tập; tham số `Collection<? extends T>` nhận cả tập phần tử kiểu con của `T`. Kết quả `ArrayList` có thể sửa được. Nếu đối số đầu vào `null`, bộ khởi tạo `LinkedHashSet` sẽ ném `NullPointerException`; tùy yêu cầu API mà chủ động kiểm tra và báo lỗi rõ ràng trước khi gọi.

## 10. Bài tập mở rộng và kết luận

1. Với `List<Integer> [4, 2, 4, 1, 2, 5]`, tạo kết quả `[4, 2, 1, 5]` bằng cả `LinkedHashSet` và vòng lặp dùng `seen`. Giải thích vì sao không cần sắp xếp.
2. Với các bản ghi `(id=1, tên=An)`, `(id=2, tên=Bình)`, `(id=1, tên=An mới)`, loại theo `id` và nêu rõ vì sao vẫn giữ bản ghi `An` đầu tiên.
3. Với dữ liệu văn bản hỗn hợp chữ hoa/thường, định nghĩa hàm chuẩn hóa khóa, rồi chứng minh rằng phần tử được đưa vào kết quả vẫn là bản gốc của lần xuất hiện đầu.

**Quy tắc cốt lõi:** duyệt dữ liệu theo thứ tự đã cho; lần đầu thấy một giá trị hoặc khóa thì giữ, những lần sau thì bỏ. `LinkedHashSet` đóng gói quy tắc này cho bình đẳng mặc định, còn `Set` đánh dấu kết hợp `List` kết quả cho phép áp dụng quy tắc trùng tùy chỉnh mà vẫn giữ thứ tự lần đầu xuất hiện.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-003">Đếm tần suất và nhóm dữ liệu bằng Map</a>

<details>
<summary>Xem chi tiết</summary>

# Đếm tần suất và nhóm dữ liệu bằng Map

Bài toán thực tế thường không chỉ cần lưu một danh sách, mà còn phải trả lời: một giá trị xuất hiện **bao nhiêu lần**, những bản ghi nào thuộc **cùng nhóm**, hoặc mỗi mã khách hàng gắn với **bao nhiêu đơn hàng**. `Map<K,V>` là mô hình thích hợp vì nó ánh xạ **khóa K** sang **một giá trị V**. Giá trị ấy có thể là số đếm, một danh sách, tập hợp hoặc cấu trúc kết quả khác.

Điểm cần thiết kế trước khi viết mã là *khóa đại diện cho điều gì*, *cách xem hai khóa là cùng một*, *có cần giữ thứ tự hay không*, và *giá trị nhóm phải giữ cả phần tử trùng hay loại trùng*.

## 1. Từ dữ liệu thô đến bảng tần suất

Cho luồng nhãn công nghệ:

```text
java, spring, java, jvm, spring, java
```

Mong muốn:

| Khóa | Tần suất |
| --- | ---: |
| java | 3 |
| spring | 2 |
| jvm | 1 |

Nếu duyệt danh sách và mỗi lần lại đếm từ đầu, mã lặp và chi phí tăng nhanh. Với `Map<String,Integer>`, ta giữ một kết quả tích lũy cho mỗi khóa. Bất biến sau khi xử lý `n` phần tử: tổng các giá trị đếm phải bằng `n` nếu mỗi đầu vào đều được tính đúng một lần và số đếm không tràn.

Một vòng lặp dễ đọc:

```java
Map<String, Integer> counts = new LinkedHashMap<>();
for (String technology : technologies) {
    int previous = counts.getOrDefault(technology, 0);
    counts.put(technology, previous + 1);
}
```

`getOrDefault` lấy giá trị đang lưu, hoặc giá trị mặc định khi không có ánh xạ tương ứng; `put` ghi lại kết quả mới. Nếu giá trị đang được ánh xạ là `null`, `getOrDefault` **có thể trả `null`**, vì khóa thực sự có ánh xạ: khi viết bộ đếm, hãy chọn mô hình không lưu các giá trị `null`, hoặc xử lý riêng.

Với `HashMap`, thứ tự các khóa khi duyệt không được đảm bảo. `LinkedHashMap` mặc định giữ thứ tự chèn, thích hợp khi muốn in các khóa theo thứ tự xuất hiện đầu tiên. `TreeMap` sắp khóa theo comparator/`Comparable`, hữu ích khi báo cáo yêu cầu thứ tự chữ cái hoặc thứ tự số.

## 2. Dùng merge để rút gọn phép cộng dồn

`Map.merge(key, value, remappingFunction)` biểu đạt trực tiếp ý định gộp một giá trị mới vào giá trị đã có:

```java
Map<String, Integer> counts = new LinkedHashMap<>();
for (String name : technologies) {
    counts.merge(name, 1, Integer::sum);
}
```

Cơ chế:

- Nếu khóa chưa có ánh xạ, hoặc giá trị được ánh xạ là `null`, `merge` nhận giá trị ban đầu `1`.
- Nếu khóa đã có giá trị khác `null`, hàm kết hợp nhận **giá trị cũ** và **giá trị mới** (ở đây là `1`) rồi trả kết quả.
- Nếu hàm kết hợp trả `null`, ánh xạ được **xóa**, vì vậy không được dùng `null` như một số đếm hợp lệ.
- Hàm kết hợp nên rõ ràng, tránh tự sửa cùng Map hoặc tạo hiệu ứng phụ khó dự đoán.

`Integer::sum` cộng bằng số nguyên `int` nên **có thể tràn** nếu số lần tăng vượt phạm vi `int`. Với hệ thống có thể đếm rất lớn, dùng `Long` và `Long::sum`, hoặc kiểm tra tràn theo yêu cầu.

## 3. Chương trình đếm tần suất chạy hoàn chỉnh

Tạo `FrequencyDemo.java`:

```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FrequencyDemo {
    public static void main(String[] args) {
        List<String> technologies = List.of(
                "java", "spring", "java", "jvm", "spring", "java"
        );

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String technology : technologies) {
            counts.merge(technology, 1, Integer::sum);
        }

        System.out.println(counts);
        System.out.println("java: " + counts.getOrDefault("java", 0));
        System.out.println("kotlin: " + counts.getOrDefault("kotlin", 0));
        System.out.println("số nhóm: " + counts.size());

        int total = counts.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        System.out.println("tổng lượt: " + total);
    }
}
```

Biên dịch và chạy với JDK 17+:

```text
javac -encoding UTF-8 --release 17 FrequencyDemo.java
java -Dfile.encoding=UTF-8 FrequencyDemo
```

Đầu ra:

```text
{java=3, spring=2, jvm=1}
java: 3
kotlin: 0
số nhóm: 3
tổng lượt: 6
```

`counts.size()` là **số khóa phân biệt**, không phải tổng lượt. Tổng số lần xuất hiện nằm ở tổng các giá trị. Nếu bạn dùng `Set` cho dữ liệu đầu vào, các lần xuất hiện trùng có thể đã bị loại từ trước khi đếm; hãy giữ một `List` hoặc luồng sự kiện đầy đủ khi nghiệp vụ quan tâm tần suất.

Trong `LinkedHashMap` trên, lần xuất hiện đầu tiên quyết định vị trí hiển thị của khóa. `merge` tăng số đếm mà không tạo khóa mới, nên thứ tự chèn của các khóa không đổi.

## 4. Nhóm nhiều phần tử theo một khóa

Đếm lưu **một số** cho mỗi khóa. Nhóm dữ liệu lưu **một collection** cho mỗi khóa, thường là `Map<K,List<V>>`.

Ví dụ đầu vào `["Lan", "Linh", "Minh", "Mai", "Lan"]` và khóa là chữ cái đầu. Cần kết quả:

```text
L -> [Lan, Linh, Lan]
M -> [Minh, Mai]
```

Mã cốt lõi:

```java
Map<Character, List<String>> groups = new LinkedHashMap<>();
for (String name : names) {
    char initial = name.charAt(0);
    groups.computeIfAbsent(initial, ignored -> new ArrayList<>())
          .add(name);
}
```

Giải thích từng bước:

1. `computeIfAbsent` tìm giá trị đang gắn với khóa.
2. Nếu chưa có ánh xạ hữu dụng, hàm `ignored -> new ArrayList<>()` tạo list để chứa nhóm đó và lưu lại trong Map.
3. Kết quả trả về là list ứng với khóa; `add(name)` đưa phần tử vào nhóm.
4. Lần gặp lại khóa `L` sẽ sử dụng chính danh sách đã tạo trước đó; `Lan` xuất hiện hai lần nên vẫn có hai mục.

Với `computeIfAbsent`, nếu hàm mapping trả `null` thì **không tạo ánh xạ mới**; đoạn code nối `.add` khi đó có thể phát sinh `NullPointerException`. Ở ví dụ này `new ArrayList<>()` bảo đảm giá trị khác `null`. Không sửa cấu trúc Map ngay trong mapping function; Javadoc của từng implementation có các ràng buộc riêng.

## 5. Chương trình nhóm dữ liệu chạy hoàn chỉnh

Tạo `GroupingDemo.java`:

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GroupingDemo {
    public static void main(String[] args) {
        List<String> names = List.of(
                "Lan", "Linh", "Minh", "Mai", "Lan"
        );

        Map<Character, List<String>> groups = new LinkedHashMap<>();
        for (String name : names) {
            char first = name.charAt(0);
            groups.computeIfAbsent(first, key -> new ArrayList<>())
                  .add(name);
        }

        System.out.println(groups);
        System.out.println("nhóm L: " + groups.get('L'));
        System.out.println("số nhóm: " + groups.size());
        System.out.println("số phần tử nhóm L: " + groups.get('L').size());
    }
}
```

Chạy:

```text
javac -encoding UTF-8 --release 17 GroupingDemo.java
java -Dfile.encoding=UTF-8 GroupingDemo
```

Đầu ra:

```text
{L=[Lan, Linh, Lan], M=[Minh, Mai]}
nhóm L: [Lan, Linh, Lan]
số nhóm: 2
số phần tử nhóm L: 3
```

Ở đây mọi tên đều không rỗng, vì vậy `charAt(0)` hợp lệ. Nếu đầu vào đến từ người dùng, phải định nghĩa chính sách cho `null`, chuỗi rỗng, khoảng trắng, chữ cái Unicode không nằm trong một `char` đơn, và cách chuẩn hóa. Không coi “ký tự đầu” tương đương “chữ cái đầu” với mọi ngôn ngữ: Unicode có ký tự ngoài BMP, dấu kết hợp và khác biệt chữ hoa/thường.

## 6. Map<K,List<V>> so với Map<K,Set<V>>

Cùng khóa nhóm, giá trị collection quyết định chính sách trùng lặp:

| Mô hình | Thích hợp khi | Điều phải nhớ |
| --- | --- | --- |
| `Map<K,Integer>` | Chỉ cần số lần xuất hiện | Không giữ các bản ghi gốc |
| `Map<K,Long>` | Số đếm có thể lớn | Vẫn phải xét khả năng tràn `long` trong cực trị |
| `Map<K,List<V>>` | Giữ mọi bản ghi, kể cả bản ghi trùng | Một phần tử có thể xuất hiện nhiều lần |
| `Map<K,LinkedHashSet<V>>` | Loại trùng trong từng nhóm, giữ thứ tự chèn | Trùng dựa trên `equals`/`hashCode` của V |
| `TreeMap<K,List<V>>` | Nhóm được hiển thị theo thứ tự khóa | Khóa cần so sánh ổn định, comparator phù hợp |

Ví dụ, “đếm 5 lượt check-in” không tương đương “5 người duy nhất”; cùng một người có thể check-in nhiều lần. Nếu khóa nhóm là mã lớp và danh sách là học viên, việc dùng `Set` có thể đúng; nếu danh sách là lịch sử lần điểm danh, dùng `List` sẽ phản ánh đầy đủ sự kiện.

Giá trị `List` là đối tượng có thể sửa. Khi trả `groups.get(key)` cho mã bên ngoài, mã đó có thể thay đổi trạng thái Map gián tiếp bằng cách sửa list. Nếu API chỉ được phép đọc, cân nhắc trả `List.copyOf(list)` khi không chứa null, hoặc một bản sao độc lập theo hợp đồng phù hợp. `Collections.unmodifiableList(list)` là view không cho sửa qua view, nhưng vẫn phản ánh thay đổi từ list gốc.

## 7. Chọn khóa nhóm cho bài toán thực tế

Khóa tốt phải thể hiện chính xác tiêu chí phân nhóm:

- Thống kê theo phòng ban: khóa là **mã phòng ban ổn định**, không phải tên hiển thị dễ đổi.
- Thống kê theo ngày: khóa là `LocalDate` trong múi giờ nghiệp vụ đã xác định, không tự lấy `Instant.toString().substring(...)`.
- Nhóm đơn hàng theo khách và trạng thái: dùng khóa ghép bất biến, chẳng hạn `record GroupKey(String customerId, Status status) {}`.
- Nhóm không phân biệt hoa/thường: chuẩn hóa đầu vào **theo chính sách rõ ràng** trước khi đưa vào Map, ví dụ `toLowerCase(Locale.ROOT)` cho mã kỹ thuật trong ngữ cảnh phù hợp. Với tên người và văn bản đa ngôn ngữ, cần quy tắc locale và chuẩn hóa riêng.

Hai khóa mà `equals` xem bằng nhau phải có cùng `hashCode`; khóa không được thay đổi theo cách phá hợp đồng khi còn ở trong HashMap. Không dùng đối tượng khả biến có `equals`/`hashCode` phụ thuộc trường có thể thay đổi làm khóa nhóm.

## 8. Stream groupingBy và toMap: khi nào nên dùng?

Stream cho phép diễn đạt gom nhóm ở mức khai báo:

```java
Map<Character, List<String>> groups =
        names.stream().collect(
                java.util.stream.Collectors.groupingBy(
                        name -> name.charAt(0)
                )
        );
```

Kết quả nhóm về mặt phần tử có cùng mục đích với vòng lặp; nhưng overload mặc định của `groupingBy` **không cam kết loại Map hoặc thứ tự duyệt**. Nếu thứ tự chèn là yêu cầu, dùng overload có nhà cung cấp map:

```java
Map<Character, List<String>> groups =
        names.stream().collect(
                java.util.stream.Collectors.groupingBy(
                        name -> name.charAt(0),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                )
        );
```

Với `toMap`, nếu nhiều phần tử tạo cùng khóa mà không có hàm xử lý trùng, phép gom có thể ném `IllegalStateException`. Đếm và nhóm vốn **chủ động chấp nhận khóa trùng**, vì vậy `groupingBy` hoặc `merge` thường rõ ý nghĩa hơn.

`groupingByConcurrent` và `ConcurrentHashMap` phục vụ một số bài toán đa luồng, nhưng không tự bảo đảm thứ tự chèn và có những điều kiện về collector, tính an toàn của giá trị. Đừng chuyển `HashMap` sang nhiều luồng ghi trực tiếp chỉ vì vòng lặp đơn luồng đúng; nếu đồng thời, xem xét hợp đồng `ConcurrentMap.merge` và chiến lược đồng bộ.

## 9. Lỗi thường gặp và cách tự kiểm chứng

1. **Nhầm `size()` với tổng số bản ghi:** `size()` của Map chỉ đếm khóa. Tổng tần suất = tổng các count.
2. **Đếm thiếu do dùng `Set` đầu vào:** hãy giữ dữ liệu sự kiện thô nếu cần cả phần tử lặp.
3. **Dựa vào thứ tự `HashMap`:** dùng `LinkedHashMap` cho thứ tự xuất hiện hoặc `TreeMap` cho thứ tự theo khóa.
4. **Gộp nhầm người bằng tên:** tên không phải mã định danh duy nhất, và chuẩn hóa phải rõ ràng.
5. **Thêm vào nhóm null:** kiểm tra mapping function không trả `null` trước khi gọi phương thức trên collection kết quả.
6. **Cộng dồn bị tràn:** dùng kiểu số và kiểm tra phù hợp với số liệu thực tế.
7. **Sửa khóa sau khi thêm:** có thể làm tra cứu HashMap không đáng tin cậy.
8. **Chia sẻ list nội bộ ra ngoài:** người gọi có thể sửa các nhóm; tạo bản sao/view tùy hợp đồng.
9. **Bỏ qua dữ liệu rỗng:** xác định việc bỏ qua, phân vào nhóm `UNKNOWN`, hay báo lỗi trước khi chạy.

### Bài thực hành

Với đầu vào `["A", "B", "A", "C", "B", "A"]`, dự đoán `counts` khi dùng `LinkedHashMap` với `merge`. Đáp án: `{A=3, B=2, C=1}`, tổng là 6 lượt và 3 khóa.

Sau đó, mở rộng bài tập thành nhóm theo chữ cái đầu của danh sách tên, giữ cả phần tử trùng. Thử thay giá trị `ArrayList` bằng `LinkedHashSet` và giải thích sự khác nhau giữa “số lượt” và “số đối tượng duy nhất”.

### Tự giải thích lại

Nếu bạn có thể trả lời bằng lời của mình ba câu sau, phần cốt lõi đã rõ: **(1)** Vì sao một Map có 3 khóa có thể đại diện cho 6 lượt? **(2)** `merge` và `computeIfAbsent` khác nhau ở đâu? **(3)** Khi nào dùng `Map<K,List<V>>` thay vì `Map<K,Set<V>>`?

## Tài liệu tham khảo

- Javadoc `Map` (Java SE 21) — `getOrDefault`, `merge` và `computeIfAbsent`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html
- Javadoc `LinkedHashMap` (Java SE 21) — thứ tự chèn: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedHashMap.html
- Javadoc `Collectors` (Java SE 21) — `groupingBy`, `toMap` và các overload: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collectors.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-004">Xây dựng chỉ mục nhiều giá trị với Map&lt;K,List&lt;V&gt;&gt;</a>

<details>
<summary>Xem chi tiết</summary>

# Xây dựng chỉ mục nhiều giá trị với `Map<K, List<V>>`

## 1. Mục tiêu và bài toán

Sau bài này, bạn có thể tự xây dựng một **chỉ mục một khóa – nhiều giá trị** bằng Java Collections, tra cứu dữ liệu theo khóa, thêm/xóa phần tử, xử lý khóa không tồn tại và chọn giữa tự duy trì chỉ mục với gom nhóm bằng Stream.

Giả sử mỗi bản ghi đăng ký có **mã khóa học** và **sinh viên**. Một khóa học có nhiều sinh viên; một sinh viên cũng có thể học nhiều khóa. Ta muốn trả lời nhanh câu hỏi “Khóa Java có những sinh viên nào?” mà không quét lại toàn bộ danh sách đăng ký mỗi lần.

Dữ liệu ban đầu:

| Khóa học (K) | Sinh viên (V) |
| --- | --- |
| Java | S01 – An |
| Java | S02 – Binh |
| SQL | S03 – Chi |
| SQL | S01 – An |

Chỉ mục cần có dạng:

```text
Java -> [An, Binh]
SQL  -> [Chi, An]
```

Trong `Map<K, List<V>>`:

- `K` là khóa tra cứu, chẳng hạn `String` tên khóa học.
- `V` là **một** phần tử được lưu dưới khóa, chẳng hạn một `Student`.
- `List<V>` là nhóm nhiều phần tử ứng với một khóa; nó giữ **thứ tự chèn** và **cho phép phần tử trùng lặp**.
- `Map` giúp tìm nhóm theo khóa; `List` quản lý các phần tử bên trong nhóm.

Nếu dùng `Map<String, Student>` rồi gọi `put("Java", student)` nhiều lần, phần tử sau sẽ **ghi đè** phần tử trước. Vì vậy, với quan hệ một–nhiều, giá trị của map phải là một tập hợp, ở đây là `List<Student>`.

## 2. Quy tắc thiết kế trước khi viết mã

Một chỉ mục hữu dụng phải có hành vi rõ ràng:

1. **Thêm:** khóa chưa có thì tạo danh sách; khóa đã có thì thêm vào danh sách sẵn có.
2. **Tìm:** khóa chưa có trả về danh sách rỗng, tránh bắt người gọi xử lý `null`.
3. **Xóa:** xóa một giá trị bằng `equals`; nếu nhóm rỗng, xóa luôn khóa để không giữ mục không có dữ liệu.
4. **Trả kết quả:** đưa ra **bản chụp chỉ đọc** để mã bên ngoài không làm hỏng cấu trúc nội bộ.
5. **Thứ tự:** ví dụ này dùng `LinkedHashMap` để thứ tự các khóa khi duyệt theo thứ tự xuất hiện; mỗi danh sách dùng `ArrayList`.
6. **Giá trị null:** ví dụ chủ động từ chối khóa/giá trị `null` để API dễ kiểm tra và tương thích với `List.copyOf`.
7. **Trùng lặp:** `List` cho phép một giá trị xuất hiện nhiều lần; bài này giữ nguyên đặc tính đó. Nếu nghiệp vụ yêu cầu duy nhất, cần quy tắc riêng.

Để kiểm soát cập nhật, chương trình đóng gói `Map` trong một lớp `MultiValueIndex<K, V>`. Mọi thao tác qua `add`, `find`, `remove` và `counts`.

## 3. Chương trình Java 17 hoàn chỉnh

Tạo tệp **`MultiValueIndexDemo.java`** với nội dung sau:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MultiValueIndexDemo {
    record Student(String id, String name) {}

    record Enrollment(String course, Student student) {}

    static final class MultiValueIndex<K, V> {
        private final Map<K, List<V>> entries = new LinkedHashMap<>();

        public void add(K key, V value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            entries.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        }

        public List<V> find(K key) {
            Objects.requireNonNull(key, "key");
            List<V> values = entries.get(key);
            return values == null ? List.of() : List.copyOf(values);
        }

        public boolean remove(K key, V value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            List<V> values = entries.get(key);
            if (values == null) {
                return false;
            }

            boolean removed = values.remove(value);
            if (values.isEmpty()) {
                entries.remove(key);
            }
            return removed;
        }

        public Map<K, Integer> counts() {
            Map<K, Integer> result = new LinkedHashMap<>();
            entries.forEach((key, values) -> result.put(key, values.size()));
            return Collections.unmodifiableMap(result);
        }
    }

    public static void main(String[] args) {
        Student an = new Student("S01", "An");
        Student binh = new Student("S02", "Binh");
        Student chi = new Student("S03", "Chi");

        List<Enrollment> registrations = List.of(
            new Enrollment("Java", an),
            new Enrollment("Java", binh),
            new Enrollment("SQL", chi),
            new Enrollment("SQL", an)
        );

        MultiValueIndex<String, Student> byCourse = new MultiValueIndex<>();
        for (Enrollment registration : registrations) {
            byCourse.add(registration.course(), registration.student());
        }

        System.out.println("Java: " + byCourse.find("Java"));
        System.out.println("SQL: " + byCourse.find("SQL"));
        System.out.println("Kotlin: " + byCourse.find("Kotlin"));
        System.out.println("So luong: " + byCourse.counts());

        List<Student> before = byCourse.find("Java");
        System.out.println("Xoa S01 khoi Java: " + byCourse.remove("Java", an));
        System.out.println("Java sau xoa: " + byCourse.find("Java"));
        System.out.println("Ban chup truoc do: " + before);

        byCourse.add("Java", chi);
        System.out.println("Java sau them: " + byCourse.find("Java"));
        System.out.println("So luong moi: " + byCourse.counts());
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp (JDK 17 trở lên):

```bash
javac -encoding UTF-8 MultiValueIndexDemo.java
java MultiValueIndexDemo
```

Kết quả chuẩn:

```text
Java: [Student[id=S01, name=An], Student[id=S02, name=Binh]]
SQL: [Student[id=S03, name=Chi], Student[id=S01, name=An]]
Kotlin: []
So luong: {Java=2, SQL=2}
Xoa S01 khoi Java: true
Java sau xoa: [Student[id=S02, name=Binh]]
Ban chup truoc do: [Student[id=S01, name=An], Student[id=S02, name=Binh]]
Java sau them: [Student[id=S02, name=Binh], Student[id=S03, name=Chi]]
So luong moi: {Java=2, SQL=2}
```

`Student` và `Enrollment` là hai `record`: Java tự tạo constructor, phương thức truy cập (`id()`, `course()`, …), `equals`, `hashCode` và `toString` theo các thành phần record. Vì thế `values.remove(an)` có thể dùng so sánh giá trị, và kết quả in ra có định dạng `Student[id=..., name=...]`. Mã chạy độc lập, không cần thư viện bên ngoài.

## 4. Giải thích cơ chế cập nhật

### 4.1. `computeIfAbsent` tạo danh sách đúng lúc

Lệnh trọng tâm là:

```java
entries.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
```

- Nếu `key` chưa có ánh xạ, hàm tạo `ArrayList` mới, đưa danh sách vào map rồi trả về danh sách đó.
- Nếu `key` đã ánh xạ tới một danh sách khác `null`, phương thức trả lại **chính danh sách hiện có**.
- `add(value)` nối một phần tử vào cuối danh sách vừa nhận.
- Lambda `ignored -> ...` nhận khóa nhưng không cần sử dụng nó để tạo danh sách.

Cách viết thủ công tương đương về ý tưởng, trong trường hợp khóa/giá trị không `null` như ví dụ:

```java
List<V> values = entries.get(key);
if (values == null) {
    values = new ArrayList<>();
    entries.put(key, values);
}
values.add(value);
```

Không dùng `entries.put(key, new ArrayList<>(List.of(value)))` cho **mọi** lần thêm: lần thứ hai sẽ thay cả nhóm cũ, làm mất dữ liệu.

### 4.2. Tại sao kết quả `find` được sao chép?

`entries.get(key)` truy cập trực tiếp danh sách **có thể thay đổi** bên trong chỉ mục. Nếu trả tham chiếu đó ra ngoài, bên gọi có thể `clear()` hoặc `add()` mà không đi qua API của chỉ mục.

Trong ví dụ, `List.copyOf(values)` tạo danh sách **không cho sửa bằng các thao tác List** và lưu ảnh chụp thành viên tại thời điểm gọi. Vì thế biến `before` vẫn chứa An và Binh sau khi ta xóa An khỏi chỉ mục. `List.of()` là kết quả rỗng, cũng không chỉnh sửa được.

Lưu ý: đây là **sao chép nông**. Các đối tượng `V` bên trong không tự động được sao chép; nếu `V` là đối tượng mutable, trạng thái của chính đối tượng có thể vẫn đổi sau đó. Khi cần bất biến sâu, phải tự tạo bản sao phù hợp của từng phần tử.

Không nhầm hai lựa chọn:

- `List.copyOf(list)`: danh sách kết quả tách biệt về cấu trúc khỏi danh sách gốc.
- `Collections.unmodifiableList(list)`: chỉ là lớp bọc chỉ đọc; nếu danh sách gốc đổi, nội dung nhìn qua lớp bọc cũng đổi.

### 4.3. Xóa một lần xuất hiện và dọn khóa rỗng

`values.remove(value)` trả về `true` nếu tìm và xóa được **phần tử đầu tiên thỏa `equals`**; nếu không có, trả `false`. Hai bản ghi giống nhau được thêm hai lần sẽ cần hai lượt xóa để loại hết.

Sau khi xóa, `if (values.isEmpty()) entries.remove(key)` giữ bất biến: **không tồn tại khóa có nhóm rỗng**. Vì vậy `find` không cần phân biệt trường hợp khóa vắng mặt với nhóm còn tồn tại nhưng rỗng.

Ví dụ kiểm tra riêng (đoạn minh họa, dùng trong cùng lớp hoặc sau khi tạo một chỉ mục phù hợp):

```java
MultiValueIndex<String, String> tags = new MultiValueIndex<>();
tags.add("java", "collections");
tags.add("java", "collections");
tags.remove("java", "collections");
System.out.println(tags.find("java")); // [collections]
tags.remove("java", "collections");
System.out.println(tags.counts());      // {}
```

### 4.4. Tính số lượng theo từng khóa

`counts()` duyệt mỗi cặp `key -> list`, gán `list.size()` vào một map mới và trả về lớp bọc chỉ đọc. Đây là một **ảnh chụp số lượng**, không tự cập nhật sau khi chỉ mục đổi. Dùng `LinkedHashMap` cho kết quả đếm để giữ thứ tự khóa như chỉ mục gốc.

## 5. Cách khác: lập chỉ mục một lượt bằng Stream

Khi **đã có** toàn bộ danh sách `registrations` và chỉ cần nhóm một lần, có thể viết ngắn hơn bằng `Collectors.groupingBy`. Đoạn sau dùng các kiểu `Enrollment`, `Student` và biến `registrations` trong chương trình ở mục 3; cần thêm `import java.util.stream.Collectors;`:

```java
Map<String, List<Student>> grouped = registrations.stream()
    .collect(Collectors.groupingBy(
        Enrollment::course,
        LinkedHashMap::new,
        Collectors.mapping(
            Enrollment::student,
            Collectors.toCollection(ArrayList::new)
        )
    ));
```

- `Enrollment::course` chọn khóa cho mỗi bản ghi.
- `LinkedHashMap::new` giữ thứ tự xuất hiện của khóa.
- `Collectors.mapping` lấy `Student` từ `Enrollment`.
- `Collectors.toCollection(ArrayList::new)` tạo danh sách có thể cập nhật và giữ thứ tự gặp trong luồng tuần tự.

Với dữ liệu đang có, `grouped.get("Java")` chứa `[An, Binh]` (hiển thị bằng `Student.toString()` khi in ra). Luồng ví dụ là **sequential** nên thứ tự đầu vào được giữ như mô tả. `groupingBy` không nên dùng khi hàm phân nhóm có thể trả `null` mà chưa được xử lý; nó cũng không tự duy trì chỉ mục khi danh sách nguồn đổi.

Chọn **vòng lặp + `computeIfAbsent`** khi vừa đọc vừa thêm bản ghi, cập nhật tăng dần, xóa hoặc cần đóng gói quy tắc nghiệp vụ. Chọn **`groupingBy`** khi cần gom toàn bộ nguồn thành map trong một lần xử lý.

## 6. Độ phức tạp và lựa chọn collection

Gọi `n` là tổng số giá trị và `m` là số giá trị dưới một khóa:

| Thao tác | Chi phí điển hình | Nguyên nhân |
| --- | --- | --- |
| `add(key, value)` | O(1) trung bình, khấu hao | Tra map theo hash và thêm cuối `ArrayList` |
| Tìm danh sách nội bộ theo khóa | O(1) trung bình | `LinkedHashMap` dựa trên bảng băm |
| `find(key)` **trong ví dụ** | O(m) | Tra map rồi `List.copyOf` m phần tử |
| `remove(key, value)` | O(m) | Dò `equals`; `ArrayList` có thể dịch phần tử sau khi xóa |
| `counts()` | O(số khóa) | Duyệt từng nhóm và đọc `size()` |
| Bộ nhớ chỉ mục | O(n + số khóa) | Lưu tham chiếu giá trị và các danh sách/map |

Các ước lượng thao tác hash là **trung bình** khi `hashCode` phân bố phù hợp; không phải bảo đảm thời gian xấu nhất. `ArrayList` có lúc mở rộng mảng, vì vậy thêm cuối là O(1) **khấu hao**.

Chọn cấu trúc theo yêu cầu thực tế:

| Yêu cầu | Cấu trúc tham khảo |
| --- | --- |
| Cho phép trùng, cần thứ tự giá trị | `Map<K, List<V>>` |
| Không cho phép giá trị trùng theo `equals/hashCode` | `Map<K, Set<V>>`, ví dụ `LinkedHashSet<V>` |
| Cần duyệt khóa theo thứ tự chèn | `LinkedHashMap<K, List<V>>` |
| Cần sắp khóa theo comparator/thứ tự tự nhiên | `TreeMap<K, List<V>>` |
| Chỉ cần một giá trị cho một khóa | `Map<K, V>` |

**Không có một lựa chọn tốt nhất cho mọi trường hợp.** Nếu cần tra cứu hoặc xóa từng giá trị rất thường xuyên, `Set<V>` có thể thích hợp hơn. Nếu hai giá trị bằng nhau vẫn là hai lượt đăng ký khác nhau, `List<V>` là lựa chọn có ý nghĩa.

## 7. Các lỗi dễ gặp trong thực tế

**Ghi đè danh sách:** gọi `put` với danh sách mới ở mỗi bản ghi làm mất những giá trị trước đó. Hãy thêm vào nhóm sẵn có bằng `computeIfAbsent`.

**Sửa danh sách rỗng bất biến:** `map.getOrDefault(key, List.of()).add(value)` ném `UnsupportedOperationException` khi khóa chưa tồn tại vì `List.of()` là danh sách không thể sửa. `getOrDefault` cũng **không lưu** giá trị mặc định vào map. Dùng `computeIfAbsent` để khởi tạo nhóm có thể thay đổi.

**Rò rỉ dữ liệu nội bộ:** trả thẳng `entries.get(key)` khiến mã ngoài có thể sửa chỉ mục. Trả bản chụp `List.copyOf` nếu mục tiêu là API chỉ đọc có ảnh chụp.

**Dùng giá trị trùng mà không chủ đích:** `List` chấp nhận phần tử trùng. Nếu bài toán là đăng ký một sinh viên tối đa một lần cho mỗi khóa học, phải kiểm tra trùng bằng `contains` hoặc dùng `Set`. `contains` là O(m) và dựa vào `equals`.

**Mất đồng bộ khi nguồn đổi:** chỉ mục là dữ liệu dẫn xuất. Nếu một đăng ký chuyển từ Java sang SQL, phải **xóa phần tử khỏi nhóm Java rồi thêm vào nhóm SQL**, hoặc dựng lại chỉ mục từ nguồn thật. Chỉ sửa trường `course` trong đối tượng nguồn không tự chuyển phần tử giữa các danh sách.

**Khóa mutable:** `HashMap`/`LinkedHashMap` dựa trên `equals` và `hashCode` của khóa. Nếu thay đổi các trường tham gia hai phương thức đó sau khi đưa khóa vào map, việc tra cứu có thể sai. Ưu tiên khóa ổn định, chẳng hạn `String` hoặc record bất biến theo dữ liệu phù hợp.

**Nhầm an toàn luồng:** `LinkedHashMap` và `ArrayList` trong ví dụ **không thread-safe** khi nhiều luồng cùng thêm/xóa. Chỉ đổi map sang `ConcurrentHashMap` vẫn chưa làm `ArrayList` bên trong an toàn. Nếu thực sự có cập nhật đồng thời, cần thiết kế khóa đồng bộ hoặc cấu trúc concurrent tương ứng, xét cả thao tác tạo nhóm, sửa danh sách và xóa khóa rỗng.

**Nhầm chỉ mục với cơ sở dữ liệu:** map trong ví dụ nằm trong bộ nhớ; khi chương trình kết thúc, nó không tự được lưu xuống tệp hay cơ sở dữ liệu. Muốn bền vững phải lưu nguồn hoặc dữ liệu chỉ mục bằng cơ chế riêng.

## 8. Bài tập thực hành có tiêu chí kiểm tra

1. Tạo `MultiValueIndex<String, String>` lưu nhãn cho bài viết. Thêm `("p1", "java")`, `("p1", "map")`, `("p2", "java")`. Xác nhận `find("p1")` trả hai nhãn đúng thứ tự và `find("p3")` trả danh sách rỗng.
2. Thêm hai lần `("p1", "java")`. Quan sát `find("p1")` có hai giá trị `"java"`. Đổi yêu cầu thành **mỗi nhãn chỉ một lần**, rồi thử cách kiểm tra `contains` trước khi thêm hoặc thay `List` bằng `LinkedHashSet`.
3. Xóa lần lượt mọi giá trị thuộc `"p2"`. Xác nhận `counts()` không còn khóa `"p2"`. Gọi `remove` với khóa không có và kiểm tra kết quả `false`.
4. Ghi lại một kết quả `find("p1")` vào biến, thêm tiếp dữ liệu vào chỉ mục rồi in biến cũ. Giải thích vì sao ảnh chụp không đổi và vì sao người gọi không thể `add` trực tiếp vào danh sách đó.
5. Xây dựng chỉ mục ngược **`Map<String, List<String>>` từ nhãn sang mã bài viết**, ví dụ `"java" -> ["p1", "p2"]`. Xác định rõ một bài viết được gắn lại cùng nhãn nhiều lần thì có được lưu trùng hay không.

Tự kiểm tra: bạn cần giải thích được sự khác nhau giữa `Map<K,V>` và `Map<K,List<V>>`, cách `computeIfAbsent` tránh ghi đè, vì sao `find` ở đây tốn O(m), quy tắc dọn khóa rỗng và thời điểm nên dùng `groupingBy`. Khi tự triển khai chỉ mục cho bài toán khác, **hãy chốt trước** quy tắc trùng lặp, thứ tự, cập nhật và quyền sửa kết quả truy vấn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-005">Tìm khoảng, phần tử gần nhất và phân trang bằng cấu trúc có thứ tự</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành tổng hợp: Tìm khoảng, phần tử gần nhất và phân trang bằng cấu trúc có thứ tự

## 1. Bài toán và mục tiêu

Giả sử một hệ thống lưu các mã sản phẩm dạng số, cần đồng thời trả lời ba loại yêu cầu:

1. **Tìm khoảng:** lấy mọi mã từ `20` (bao gồm) đến `60` (không bao gồm).
2. **Tìm phần tử gần nhất:** với mã yêu cầu `35`, tìm mã hiện có có khoảng cách số học nhỏ nhất; nếu cách đều hai phía thì chọn mã nhỏ hơn.
3. **Phân trang:** trả các sản phẩm theo mã tăng dần, mỗi trang tối đa ba sản phẩm, và dùng mã cuối trang trước làm con trỏ để lấy trang sau.

Nếu mỗi truy vấn lại sắp xếp cả danh sách rồi quét từ đầu, ta lặp lại công việc không cần thiết. Java Collections cung cấp `NavigableSet` và `NavigableMap` để duy trì thứ tự và tìm mốc trước/sau một khóa.

Sau bài này, bạn có thể:

- Chọn `TreeSet` khi chỉ cần các giá trị duy nhất có thứ tự; chọn `TreeMap` khi mỗi khóa có dữ liệu đi kèm.
- Sử dụng `subSet`/`subMap`, `headSet`/`headMap`, `tailSet`/`tailMap` để lấy các khoảng có biên rõ ràng.
- Phân biệt `lower`, `floor`, `ceiling`, `higher` và xử lý trường hợp không có phần tử ở một phía.
- Xây dựng truy vấn gần nhất và phân trang theo khóa (keyset/cursor pagination), hiểu các giới hạn khi dữ liệu thay đổi.

## 2. Chọn đúng cấu trúc có thứ tự

| Nhu cầu | Giao diện/API | Cài đặt điển hình |
| --- | --- | --- |
| Lưu các mã *duy nhất*, duy trì thứ tự | `NavigableSet<E>` | `TreeSet<E>` |
| Ánh xạ một mã *duy nhất* sang dữ liệu | `NavigableMap<K,V>` | `TreeMap<K,V>` |
| Chỉ cần duyệt theo thứ tự và không tìm mốc điều hướng | `SortedSet` / `SortedMap` | `TreeSet` / `TreeMap` vẫn dùng được |

`TreeSet` và `TreeMap` thường dùng cây đỏ–đen cân bằng. Với `n` phần tử, thao tác tìm kiếm, thêm, xóa theo khóa có độ phức tạp `O(log n)`; sau khi tìm mốc, duyệt `k` phần tử liên tiếp mất thêm khoảng `O(k)`. Điều này phù hợp với câu hỏi “tìm từ khóa X trở đi rồi lấy vài phần tử”.

Thứ tự đến từ `Comparable` (thứ tự tự nhiên) hoặc `Comparator` được truyền vào constructor, **không phải thứ tự thêm phần tử**. Ví dụ `new TreeSet<>(List.of(40, 10, 30))` duyệt thành `[10, 30, 40]`.

**Điều kiện quan trọng:** trong một `TreeSet` hoặc `TreeMap`, hai khóa được coi là trùng khi phép so sánh trả về `0`. Ví dụ dùng `String.CASE_INSENSITIVE_ORDER` thì `"A"` và `"a"` là cùng một khóa theo cấu trúc này. Nên dùng cách so sánh nhất quán với `equals` nếu cần tuân thủ đầy đủ hợp đồng `Set`/`Map`. Nếu nhiều sản phẩm có cùng mức giá, không dùng riêng giá làm khóa duy nhất; có thể dùng khóa tổng hợp `(giá, mã sản phẩm)` hoặc lưu nhóm sản phẩm theo giá.

## 3. Tìm các phần tử trong khoảng

Một quy ước thường dùng là khoảng nửa mở `[from, to)`: nhận `from` nhưng bỏ `to`. Quy ước này giúp các khoảng liền kề không bị trùng phần tử ở đường biên.

~~~java
NavigableSet<Integer> ids = new TreeSet<>(
    List.of(10, 20, 30, 40, 50, 60, 70, 80)
);

NavigableSet<Integer> range = ids.subSet(20, true, 60, false);
// [20, 30, 40, 50]

NavigableSet<Integer> before = ids.headSet(40, false);
// [10, 20, 30]

NavigableSet<Integer> fromHere = ids.tailSet(40, true);
// [40, 50, 60, 70, 80]
~~~

Với `subSet(from, fromInclusive, to, toInclusive)`, hai tham số boolean quyết định có lấy các đầu mút hay không. `headSet(bound, inclusive)` lấy phía trước mốc; `tailSet(bound, inclusive)` lấy từ mốc về sau. Phiên bản tương ứng của `NavigableMap` là `subMap`, `headMap` và `tailMap`, áp dụng trên **khóa**:

~~~java
NavigableMap<Integer, String> products = new TreeMap<>();
products.put(10, "P10");
products.put(20, "P20");
products.put(30, "P30");
products.put(40, "P40");
products.put(50, "P50");

NavigableMap<Integer, String> selected =
    products.subMap(20, true, 50, false);
// keys: [20, 30, 40]
~~~

Một số chi tiết dễ bỏ sót:

- `subSet`/`subMap` trả về **view được hỗ trợ bởi cấu trúc gốc**, không phải bản sao độc lập. Thay đổi qua view làm thay đổi cấu trúc gốc, và thay đổi cấu trúc gốc có thể hiện ra trong view. Nếu cần bản chụp độc lập, tạo `new TreeSet<>(range)` hoặc `new TreeMap<>(selected)`.
- Biên phải đứng sau hoặc bằng biên trái **theo comparator đang dùng**. Với thứ tự tăng dần tự nhiên, gọi `subSet(60, true, 20, false)` sẽ ném `IllegalArgumentException`.
- Trên một view đã bị giới hạn, cố tạo view con vượt khỏi giới hạn hiện có cũng có thể ném `IllegalArgumentException`.
- Phạm vi rỗng là kết quả hợp lệ: chẳng hạn `subSet(20, false, 20, false)` không có phần tử. Đừng tự suy ra “không tồn tại dữ liệu” chỉ từ một khoảng rỗng.
- Muốn duyệt theo thứ tự giảm dần, cân nhắc `descendingSet()` hoặc `descendingMap()` và xác định lại cách hiểu điểm bắt đầu/kết thúc theo comparator của view đó.

## 4. Tìm phần tử thấp hơn, cao hơn và gần nhất

Các hàm điều hướng nhận một khóa `x` và tìm phần tử theo thứ tự so sánh:

| Hàm của `NavigableSet` | Điều kiện của phần tử trả về | Ví dụ khi `x = 40` |
| --- | --- | --- |
| `lower(x)` | Lớn nhất nhưng **nhỏ hơn** `x` | `30` |
| `floor(x)` | Lớn nhất và **≤ x** | `40` |
| `ceiling(x)` | Nhỏ nhất và **≥ x** | `40` |
| `higher(x)` | Nhỏ nhất nhưng **lớn hơn** `x` | `50` |

Nếu không tìm thấy phần tử thỏa điều kiện, mỗi hàm trả về `null`. Với `NavigableMap`, dùng `lowerKey`/`floorKey`/`ceilingKey`/`higherKey` nếu cần khóa, hoặc `lowerEntry`/`floorEntry`/`ceilingEntry`/`higherEntry` nếu cần cả cặp khóa–giá trị.

Đối với **khóa số**, một phần tử gần nhất về khoảng cách tuyệt đối phải là ứng viên `floor(target)` hoặc `ceiling(target)`: mọi phần tử còn lại ở xa hơn hoặc bằng một trong hai mốc này. Cần xác định quy tắc khi bằng khoảng cách (ở đây ưu tiên khóa nhỏ hơn).

~~~java
static Integer nearest(NavigableSet<Integer> sorted, int target) {
    Integer left = sorted.floor(target);
    Integer right = sorted.ceiling(target);

    if (left == null) return right;  // không có khóa phía dưới
    if (right == null) return left;  // không có khóa phía trên

    long leftDistance = (long) target - left;
    long rightDistance = (long) right - target;
    return leftDistance <= rightDistance ? left : right;
}
~~~

Ví dụ, `nearest([10,20,30,40,50], 35)` là `30` vì `30` và `40` đều cách `35` năm đơn vị, và quy tắc chọn phía nhỏ hơn. `nearest(..., 47)` là `50`; tập rỗng trả về `null`. Ép sang `long` trước khi trừ tránh tràn `int` khi hai khóa cách nhau rất xa.

**Phân biệt thứ tự và khoảng cách:** `Comparator` chỉ nói khóa nào đứng trước; nó **không tự định nghĩa “gần nhất” về mặt nghiệp vụ**. Với chuỗi, ngày giờ, tọa độ hay khóa tổng hợp, cần xác định thước đo khoảng cách rõ ràng trước khi áp dụng thuật toán gần nhất. Nếu muốn phần tử liền trước/liền sau theo thứ tự, `lower`/`higher` mới là phép toán phù hợp.

## 5. Phân trang theo con trỏ trên khóa có thứ tự

Giả sử khóa đang tăng dần: `10, 20, 30, 40, 50, 60, 70, 80` và mỗi trang tối đa ba khóa.

- Trang 1: bắt đầu từ đầu; lấy `10, 20, 30`; gửi `nextCursor = 30` nếu còn dữ liệu.
- Trang 2: chỉ lấy khóa **> 30** bằng `tailMap(30, false)`; nhận `40, 50, 60`; `nextCursor = 60`.
- Trang 3: chỉ lấy khóa **> 60**; nhận `70, 80`; hết dữ liệu nên `nextCursor = null`.

Cursor là khóa **cuối cùng đã trả về**, không phải chỉ số phần tử hoặc vị trí trang. Biến thể này còn gọi là *keyset pagination*. Nó tìm điểm bắt đầu theo khóa khoảng `O(log n)` rồi duyệt tối đa `pageSize + 1` phần tử để vừa lấy trang vừa biết còn trang sau, thay vì bỏ qua liên tiếp hàng nghìn phần tử bằng chỉ số offset. Với `NavigableMap`, dùng `tailMap(cursor, false)`; với `NavigableSet`, dùng `tailSet(cursor, false)`.

Hợp đồng API nên làm rõ:

- `cursor = null` ở **đầu vào** nghĩa là bắt đầu từ đầu danh sách; `nextCursor = null` ở **đầu ra** nghĩa là không còn trang tiếp theo.
- `pageSize > 0` và nên có giới hạn tối đa để tránh yêu cầu quá lớn.
- Thứ tự khóa phải ổn định; nếu sắp xếp bằng nhiều trường, cursor phải chứa đủ các trường đó cộng khóa phân biệt bản ghi. Ví dụ sắp xếp theo giá tăng dần rồi ID tăng dần, cursor phải là `(lastPrice, lastId)`.
- Cursor phải cùng thứ tự lọc/sắp xếp giữa các request. Khi đổi bộ lọc hoặc comparator, phải bắt đầu phân trang lại.
- Khi đang lọc trong một khoảng `[min, max)`, cursor phải được kiểm tra thuộc phạm vi hợp lệ trước khi gọi `tailMap` trên một bounded view, vì cursor ngoài giới hạn có thể làm phát sinh `IllegalArgumentException`.

Phân trang trên cấu trúc đang bị thay đổi **không bảo đảm cùng một ảnh chụp dữ liệu giữa các lần gọi**: bản ghi thêm sau cursor có thể xuất hiện, bản ghi thêm trước hoặc bằng cursor có thể không được nhìn thấy, bản ghi bị xóa sẽ biến mất. Nếu nghiệp vụ cần kết quả nhất quán tuyệt đối giữa nhiều trang, phải có snapshot/version hoặc cơ chế transaction phù hợp ở tầng lưu trữ. `TreeMap`/`TreeSet` không tự đồng bộ hóa; không nên đồng thời sửa cấu trúc trong lúc duyệt iterator mà thiếu cơ chế đồng bộ phù hợp.

## 6. Chương trình Java 17 hoàn chỉnh

Lưu đoạn sau thành **`OrderedQueriesDemo.java`**. Chương trình dùng một tập khóa có thứ tự cho truy vấn khoảng và gần nhất, đồng thời tạo map có thứ tự từ cùng dữ liệu để thực hiện phân trang. Hàm `pageAfter` chụp lại các `Map.Entry` được trả về bằng `SimpleImmutableEntry`; danh sách kết quả không bị ràng buộc trực tiếp với `TreeMap` sau khi hàm trả về.

~~~java
import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class OrderedQueriesDemo {
    record Page(List<Map.Entry<Integer, String>> items, Integer nextCursor) {}

    static Integer nearest(NavigableSet<Integer> sorted, int target) {
        Integer left = sorted.floor(target);
        Integer right = sorted.ceiling(target);

        if (left == null) return right;
        if (right == null) return left;

        long leftDistance = (long) target - left;
        long rightDistance = (long) right - target;
        return leftDistance <= rightDistance ? left : right;
    }

    static Page pageAfter(
            NavigableMap<Integer, String> rows,
            Integer afterExclusive,
            int pageSize) {

        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive");
        }

        NavigableMap<Integer, String> window =
            afterExclusive == null
                ? rows
                : rows.tailMap(afterExclusive, false);

        Iterator<Map.Entry<Integer, String>> it =
            window.entrySet().iterator();

        List<Map.Entry<Integer, String>> items = new ArrayList<>();
        while (items.size() < pageSize && it.hasNext()) {
            Map.Entry<Integer, String> entry = it.next();
            items.add(new AbstractMap.SimpleImmutableEntry<>(entry));
        }

        Integer nextCursor =
            it.hasNext() && !items.isEmpty()
                ? items.get(items.size() - 1).getKey()
                : null;

        return new Page(List.copyOf(items), nextCursor);
    }

    public static void main(String[] args) {
        NavigableSet<Integer> ids = new TreeSet<>(
            List.of(10, 20, 30, 40, 50, 60, 70, 80)
        );

        System.out.println(
            "Khoang [20, 60): " + ids.subSet(20, true, 60, false)
        );
        System.out.println(
            "floor(35): " + ids.floor(35)
                + ", ceiling(35): " + ids.ceiling(35)
        );
        System.out.println(
            "lower(40): " + ids.lower(40)
                + ", higher(40): " + ids.higher(40)
        );
        System.out.println("Gan 35: " + nearest(ids, 35));
        System.out.println("Gan 68: " + nearest(ids, 68));

        NavigableMap<Integer, String> products = new TreeMap<>();
        for (Integer id : ids) {
            products.put(id, "P" + id);
        }

        Integer cursor = null;
        int pageNumber = 1;
        while (true) {
            Page page = pageAfter(products, cursor, 3);
            System.out.println(
                "Trang " + pageNumber + ": "
                    + page.items().stream().map(Map.Entry::getKey).toList()
                    + ", next=" + page.nextCursor()
            );
            if (page.nextCursor() == null) {
                break;
            }
            cursor = page.nextCursor();
            pageNumber++;
        }
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~bash
javac OrderedQueriesDemo.java
java OrderedQueriesDemo
~~~

Kết quả chuẩn:

~~~text
Khoang [20, 60): [20, 30, 40, 50]
floor(35): 30, ceiling(35): 40
lower(40): 30, higher(40): 50
Gan 35: 30
Gan 68: 70
Trang 1: [10, 20, 30], next=30
Trang 2: [40, 50, 60], next=60
Trang 3: [70, 80], next=null
~~~

**Giải thích:** truy vấn khoảng bỏ `60` do biên trên không bao gồm. Với `35`, `floor = 30` và `ceiling = 40` cách đều nhau nên chọn `30`; với `68`, `70` gần hơn `60`. Ở trang 1, sau khi lấy ba bản ghi iterator vẫn còn dữ liệu, vì vậy `nextCursor = 30`. Trang cuối đã duyệt hết dữ liệu nên `nextCursor = null`. Vòng lặp không truy cập lại các bản ghi trước con trỏ.

Trong hàm phân trang, `items` là các cặp khóa–giá trị sao chép vào `SimpleImmutableEntry` bất biến và được đưa vào `List.copyOf`. Đây là ảnh chụp của *riêng trang hiện tại*, không phải snapshot của toàn bộ map qua nhiều lượt phân trang.

## 7. Độ phức tạp và lựa chọn cách làm

| Thao tác | Với `TreeSet` / `TreeMap` | Ghi chú |
| --- | --- | --- |
| `floor`, `ceiling`, `lower`, `higher` | `O(log n)` | Mỗi truy vấn định vị một mốc |
| Tìm phần tử gần nhất kiểu số | `O(log n)` | Hai lần tìm mốc và so sánh `O(1)` |
| Tìm mốc rồi duyệt `k` phần tử của khoảng | Khoảng `O(log n + k)` | Không cần sao chép toàn bộ tập |
| Lấy một trang kích thước `p` theo cursor | Khoảng `O(log n + p)` | Duyệt thêm tối đa một phần tử để biết còn trang sau |
| Bộ nhớ phụ của `pageAfter` | `O(p)` | Lưu bản sao các entry của một trang |

Các độ phức tạp trên mô tả thao tác trên cấu trúc cây đang có sẵn; nếu đầu vào là danh sách chưa được sắp xếp thì việc dựng `TreeSet`/`TreeMap` cũng có chi phí. Nếu chỉ chạy một truy vấn trên một danh sách nhỏ, quét tuyến tính có thể đơn giản hơn. Nếu hệ thống phải truy vấn liên tục trong lúc thêm/xóa dữ liệu, lưu dữ liệu theo cấu trúc cây có thứ tự đem lại lợi ích rõ ràng hơn.

Khi số lượng dữ liệu vượt bộ nhớ hoặc cần phân trang giữa nhiều máy, hãy dùng **chỉ mục có thứ tự của cơ sở dữ liệu** với điều kiện tương đương `WHERE id > :cursor ORDER BY id LIMIT :pageSize`. Ý tưởng cursor vẫn giống nhau, nhưng `TreeMap` không phải giải pháp lưu trữ bền vững hay chỉ mục phân tán.

## 8. Các lỗi thường gặp và cách tránh

1. **Nhầm biên inclusive/exclusive.** `tailMap(cursor, true)` khiến bản ghi cuối trang trước có thể lặp lại ở trang sau. Dùng `false` khi cursor có nghĩa “đã xuất bản ghi này”.
2. **Dùng `floor`/`ceiling` thay cho “liền trước/liền sau”.** Khi khóa trùng đúng `x`, cả `floor(x)` và `ceiling(x)` có thể trả về `x`. Muốn *khác x*, dùng `lower(x)`/`higher(x)`.
3. **Quên kiểm tra `null`.** Với tập rỗng hoặc không có dữ liệu một phía, các hàm điều hướng trả về `null`. Không unbox sang `int` trước khi xử lý.
4. **Tính khoảng cách bằng `int`.** Phép trừ hai số nguyên cực trị có thể tràn; chuyển sang `long` trước khi trừ, đồng thời định nghĩa quy tắc xử lý trường hợp cách đều.
5. **Dùng khóa không duy nhất.** `TreeMap` chỉ giữ một giá trị ứng với mỗi khóa theo comparator; `put` vào khóa trùng có thể thay dữ liệu cũ. Nếu sắp theo giá, dùng giá cộng ID hoặc map giá đến một nhóm nhiều sản phẩm.
6. **Hiểu view là danh sách bất biến.** `subSet`, `subMap` và `tailMap` là view sống; sửa cấu trúc nền có thể ảnh hưởng nội dung khi duyệt. Khi cần bản sao độc lập, sao chép rõ ràng.
7. **Phân trang bằng số thứ tự trên tập có thứ tự.** `TreeMap` không cung cấp truy xuất “phần tử thứ i” trong `O(log n)`. `pageIndex * pageSize` không biến `TreeMap` thành danh sách truy cập ngẫu nhiên; dùng khóa cuối trang trước khi phù hợp.
8. **Thay đổi thứ tự hoặc bộ lọc giữa các trang.** Cursor của thứ tự tăng không thể tái sử dụng bừa cho thứ tự giảm hoặc bộ lọc khác. Với nhiều cột sắp xếp, phải dùng khóa cursor tổng hợp.
9. **Quên giới hạn của concurrent access.** `TreeSet` và `TreeMap` không tự thread-safe; iterator thường phát hiện một số chỉnh sửa đồng thời bằng `ConcurrentModificationException` theo cơ chế best effort, không được dựa vào ngoại lệ đó để bảo đảm tính đúng đắn.

## 9. Bài tập tự kiểm tra

**Bài A — truy vấn theo khoảng.** Với `ids = [10,20,30,40,50,60,70,80]`, hãy dự đoán kết quả của:

~~~java
ids.subSet(20, false, 60, true);
ids.headSet(40, true);
ids.tailSet(40, false);
~~~

Đáp án lần lượt là `[30, 40, 50, 60]`, `[10, 20, 30, 40]` và `[50, 60, 70, 80]`. Mỗi câu kiểm tra một cách chọn biên khác nhau.

**Bài B — gần nhất ở biên.** Dùng hàm `nearest` trong chương trình, thử `target = 5`, `target = 80`, `target = 85` và một `TreeSet<Integer>` rỗng. Kết quả lần lượt là `10`, `80`, `80` và `null`. Giải thích vì sao phải xử lý ứng viên `null` trước khi trừ khoảng cách.

**Bài C — phân trang thực tế.** Đổi `pageSize` thành `2`. Dự đoán các trang `[10,20]`, `[30,40]`, `[50,60]`, `[70,80]`. Cursor đầu ra lần lượt là `20`, `40`, `60` và `null`. Sau đó thử gọi trực tiếp `pageAfter(products, 35, 3)`; kết quả phải bắt đầu bằng `40`, không yêu cầu khóa `35` tồn tại trong map.

**Bài D — mở rộng.** Một kho hàng phải lọc giá trong khoảng `[100, 500)`, sắp tăng dần theo giá và phân trang 20 sản phẩm. Hãy thiết kế `Comparator`/khóa dạng `(price, productId)` để hai sản phẩm cùng giá không đè lên nhau. Con trỏ cần chứa cả `price` và `productId`; truy vấn trang sau dùng so sánh lớn hơn **toàn bộ khóa tổng hợp**, không chỉ lớn hơn giá. Khi áp dụng trên database, giữ cùng điều kiện lọc và thứ tự cho mọi trang.

## 10. Ghi nhớ khi triển khai

`NavigableSet` và `NavigableMap` phù hợp khi dữ liệu có thứ tự và cần truy vấn theo mốc: dùng `subSet`/`subMap` để tìm khoảng, `floor`/`ceiling` để chọn hai ứng viên gần nhất, `tailSet`/`tailMap` với biên **exclusive** để phân trang theo cursor. Chọn comparator rõ ràng, xử lý biên và `null`, bảo đảm khóa phân biệt bản ghi, và xác định mức nhất quán dữ liệu khi phân trang qua nhiều lần gọi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-006">Giải bài toán top-K và hàng đợi tác vụ theo ưu tiên</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành tổng hợp: Giải bài toán top-K và hàng đợi tác vụ theo ưu tiên

Khi một hệ thống nhận rất nhiều dữ liệu, đôi khi ta chỉ cần **K phần tử tốt nhất**, hoặc phải chọn **công việc nào được thực hiện tiếp theo** theo mức độ khẩn cấp. Cả hai bài toán đều có thể giải bằng `PriorityQueue` của Java, nhưng **mục tiêu và hướng so sánh khác nhau**.

Bài thực hành này áp dụng Java Collections Framework vào hai tình huống cụ thể. Sau bài học, bạn có thể:

- Mô tả rõ tiêu chí chọn top-K, kể cả giá trị trùng, đầu vào rỗng và trường hợp K vượt số phần tử.
- Dùng min-heap kích thước không quá K để tìm K giá trị lớn nhất.
- Phân tích bất biến, chứng minh tính đúng và tính độ phức tạp theo N, K.
- Xây dựng hàng đợi tác vụ lấy mức ưu tiên cao trước, giữ thứ tự đến khi bằng mức ưu tiên.
- Giải thích `offer()`, `peek()`, `poll()` và vì sao duyệt một `PriorityQueue` không cho kết quả đã sắp xếp.
- Kiểm tra bằng mã Java 17 biên dịch và chạy được, rồi mở rộng sang các trường hợp nghiệp vụ khác.

Các ví dụ được thiết kế cho chương trình chạy một luồng; vấn đề đồng thời được phân biệt ở phần lưu ý.

## 1. Hai đề bài và quy tắc đầu ra

### Bài toán A: chọn top-K điểm cao nhất

Một hệ thống lưu điểm các lượt kiểm tra trong mảng:

~~~text
[12, 7, 25, 10, 25, 3, 19, 18]
~~~

Hãy trả về **ba điểm lớn nhất**, sắp theo thứ tự giảm dần. Hai lượt đạt `25` là hai phần tử của đầu vào, vì vậy bài toán **cho phép trùng** và kết quả đúng là:

~~~text
[25, 25, 19]
~~~

Quy ước cần thống nhất trước khi lập trình:

| Trường hợp | Hành vi mong muốn |
| --- | --- |
| `k = 3` với mảng trên | `[25, 25, 19]` |
| `k = 0` | Danh sách rỗng |
| `k > n` (n là số phần tử) | Trả tất cả n phần tử, giảm dần |
| Mảng rỗng và `k ≥ 0` | Danh sách rỗng |
| `k < 0` | Ném `IllegalArgumentException` |
| Mảng đầu vào `null` | Ném `IllegalArgumentException` |

Hàm không được sắp xếp tại chỗ hay sửa mảng gốc. Bài toán chọn **K lần xuất hiện có giá trị cao nhất**, khác với chọn K giá trị *phân biệt*.

### Bài toán B: xử lý công việc theo độ ưu tiên

Hàng đợi nhận lần lượt:

| Thứ tự nhận | Tác vụ | Mức ưu tiên |
| --- | --- | --- |
| 0 | Sao lưu dữ liệu | 2 |
| 1 | Xử lý sự cố | 5 |
| 2 | Gửi email | 2 |
| 3 | Khôi phục dịch vụ | 5 |
| 4 | Dọn tệp tạm | 1 |

Quy tắc là **số ưu tiên lớn hơn được xử lý trước**. Nếu bằng mức ưu tiên, tác vụ nhận sớm hơn được xử lý trước. Kết quả lấy lần lượt khỏi hàng đợi phải là:

1. Xử lý sự cố (mức 5, đến số 1).
2. Khôi phục dịch vụ (mức 5, đến số 3).
3. Sao lưu dữ liệu (mức 2, đến số 0).
4. Gửi email (mức 2, đến số 2).
5. Dọn tệp tạm (mức 1, đến số 4).

Hàng đợi ưu tiên khác hàng đợi vào trước ra trước (FIFO) thông thường: công việc ưu tiên cao đến sau vẫn có thể vượt các công việc ưu tiên thấp đang chờ. FIFO chỉ là **tiêu chí phụ** bên trong một nhóm có cùng ưu tiên.

## 2. Vì sao `PriorityQueue` phù hợp?

`PriorityQueue<E>` thuộc `java.util`. Nó quản lý phần tử theo thứ tự tự nhiên hoặc một `Comparator` được cung cấp lúc khởi tạo. Triển khai tiêu chuẩn dùng binary heap để duy trì phần tử **đứng đầu theo comparator** mà không phải sắp xếp toàn bộ tập hợp sau mỗi lần thêm.

| API | Ý nghĩa | Độ phức tạp điển hình |
| --- | --- | --- |
| `offer(e)` | Thêm phần tử | O(log m) |
| `peek()` | Xem đầu hàng đợi, không xóa; rỗng trả `null` | O(1) |
| `poll()` | Xem và xóa đầu hàng đợi; rỗng trả `null` | O(log m) |
| `size()` / `isEmpty()` | Đếm / kiểm tra rỗng | O(1) |
| `contains(e)` | Tìm phần tử theo quy tắc bằng nhau | O(m) |
| `remove(e)` | Xóa một phần tử cụ thể | O(m) |

Trong bảng, m là số phần tử hiện đang lưu; các độ phức tạp mô tả triển khai `PriorityQueue` chuẩn.

Theo mặc định, `new PriorityQueue<Integer>()` là **min-heap**: `peek()` cho **số nhỏ nhất**. Với `Comparator.reverseOrder()`, nó hoạt động như **max-heap** theo thứ tự số nguyên: `peek()` cho **số lớn nhất**.

Cần phân biệt ba thứ tự:

- **Thứ tự nhận:** khi phần tử được đưa vào bằng `offer()`.
- **Thứ tự ưu tiên:** do `Comparable` hoặc `Comparator` xác định.
- **Thứ tự duyệt nội bộ:** thứ tự `iterator`, `for-each` hoặc `toString()` nhìn thấy; **không được bảo đảm đã sắp xếp theo ưu tiên**.

Chỉ khi dùng `poll()` liên tiếp mới nhận lần lượt các phần tử đứng đầu theo comparator. Nếu phải giữ hàng đợi để sử dụng sau, hãy tạo bản sao rồi `poll()` trên bản sao hoặc dùng cách sắp xếp khác.

Mặc định, `PriorityQueue` cũng **không giữ FIFO cho các phần tử bằng ưu tiên**. Muốn ổn định thứ tự đến trong cùng một mức, ta phải đưa thứ tự nhận vào comparator.

## 3. Top-K lớn nhất: dùng min-heap kích thước K

### 3.1. Trực giác lựa chọn cấu trúc

Một cách làm dễ hiểu là sao chép rồi sắp xếp toàn bộ n phần tử giảm dần, sau đó lấy K phần tử đầu. Cách này thường cần O(n log n) thời gian và phải giữ hoặc thao tác trên toàn bộ n phần tử.

Khi K nhỏ so với n, ta không cần nhớ tất cả. Ta chỉ cần duy trì **K ứng viên lớn nhất trong số phần tử đã đọc**. Để có thể thay nhanh phần tử yếu nhất trong nhóm ứng viên, ta đặt **phần tử nhỏ nhất của nhóm đó ở đầu heap**.

Vì thế, tìm **K số lớn nhất** lại sử dụng **min-heap**, không phải max-heap, ở giai đoạn chọn ứng viên. Max-heap toàn bộ n phần tử có thể lấy K số lớn nhất, nhưng phải giữ O(n) phần tử nếu đưa toàn bộ dữ liệu vào trước.

### 3.2. Thuật toán từng bước

Với K dương và mảng đầu vào:

1. Tạo `PriorityQueue<Integer> minHeap = new PriorityQueue<>()`.
2. Duyệt từng số `x`. Nếu heap có ít hơn K số, thêm `x` bằng `offer(x)`.
3. Nếu heap đã đủ K số, xem `minHeap.peek()`, tức **ứng viên nhỏ nhất**.
4. Nếu `x > minHeap.peek()`, xứng đáng thay ứng viên yếu nhất: gọi `poll()` rồi `offer(x)`.
5. Nếu `x ≤ minHeap.peek()`, giữ nguyên K ứng viên đã có. Với bài toán chỉ xét **giá trị số**, phần tử mới bằng ngưỡng không làm thay đổi tập kết quả.
6. Duyệt xong, sao chép các ứng viên sang `List<Integer>` rồi sắp xếp **giảm dần** để tạo đầu ra đúng hợp đồng.

### 3.3. Lần vết dữ liệu

Với K = 3, ta có bảng diễn biến sau. Cột cuối được **trình bày tăng dần để dễ nhìn**, không mô tả thứ tự trong mảng nội bộ của heap:

| Số vừa đọc | Quyết định | Các ứng viên đang giữ |
| --- | --- | --- |
| 12 | Chưa đủ 3, thêm | [12] |
| 7 | Chưa đủ 3, thêm | [7, 12] |
| 25 | Chưa đủ 3, thêm | [7, 12, 25] |
| 10 | Lớn hơn 7, thay 7 | [10, 12, 25] |
| 25 | Lớn hơn 10, thay 10 | [12, 25, 25] |
| 3 | Không lớn hơn 12, bỏ qua | [12, 25, 25] |
| 19 | Lớn hơn 12, thay 12 | [19, 25, 25] |
| 18 | Không lớn hơn 19, bỏ qua | [19, 25, 25] |

Sắp xếp ba ứng viên giảm dần, ta được `[25, 25, 19]`.

### 3.4. Bất biến và lý do thuật toán đúng

**Bất biến:** sau khi xử lý bất kỳ tiền tố nào của đầu vào, heap chứa đúng `min(K, số phần tử đã đọc)` giá trị lớn nhất của tiền tố ấy, **tính cả giá trị trùng**.

- Khi heap chưa đủ K, tất cả số đã gặp đều là ứng viên hợp lệ và được thêm.
- Khi heap đủ K, nếu số mới `x` không lớn hơn số nhỏ nhất đang giữ, đã tồn tại K ứng viên có giá trị không nhỏ hơn `x`; việc thêm x không cải thiện kết quả.
- Khi `x` lớn hơn số nhỏ nhất đang giữ, thay số nhỏ nhất bằng x sẽ giữ đúng K giá trị tốt nhất của tiền tố mới.

Tính chất được duy trì sau mỗi lượt; do đó khi duyệt hết mảng, heap giữ đúng các giá trị cần tìm. Việc sắp xếp cuối cùng chỉ đổi thứ tự trình bày, không đổi tập kết quả.

### 3.5. Phân tích chi phí

Đặt n là số phần tử, `h = min(K, n)` khi K ≥ 0. Heap có nhiều nhất h phần tử. Mỗi số có thể gây một thao tác thêm, lấy, thay với chi phí O(log h) khi h ≥ 2. Sắp xếp h kết quả tốn O(h log h).

**Thời gian:** O(n log h + h log h) khi h ≥ 2; các trường hợp h = 0 hoặc h = 1 được xử lý đơn giản hơn.

**Bộ nhớ phụ:** O(h) để giữ heap và danh sách kết quả. Nếu K = 10 mà n rất lớn, bộ nhớ lưu ứng viên vẫn tỉ lệ với 10, trong khi việc sắp xếp toàn bộ thường phải xử lý tập dữ liệu lớn hơn nhiều.

Không gọi `peek()` trên heap rỗng: đoạn mã mẫu sẽ kiểm tra K = 0, mảng rỗng và chỉ so sánh với `peek()` sau khi heap đã đạt đủ K phần tử.

## 4. Hàng đợi tác vụ: ưu tiên chính và tiêu chí phụ

### 4.1. Biểu diễn tác vụ

Mỗi tác vụ cần:

- `name`: tên mô tả.
- `priority`: mức ưu tiên số nguyên, số lớn hơn là khẩn cấp hơn.
- `sequence`: số thứ tự nhận vào, tăng dần và **duy nhất trong một hàng đợi**.

Java 17 hỗ trợ `record` để định nghĩa đối tượng chứa dữ liệu không đổi sau khi tạo. Dùng record giúp tránh trường hợp thay đổi độ ưu tiên khi đối tượng đang nằm trong heap.

### 4.2. Comparator đúng yêu cầu

Comparator phải xét **mức ưu tiên giảm dần**, rồi **số thứ tự nhận tăng dần**:

~~~java
Comparator<Task> taskOrder =
        Comparator.comparingInt((Task task) -> task.priority())
                .reversed()
                .thenComparingLong(Task::sequence);
~~~

Đây là đoạn minh họa nằm trong phạm vi lớp có kiểu `Task`; chương trình chạy độc lập đầy đủ ở mục 5.

Các tác vụ cùng mức 5 sẽ được lấy theo `sequence` tăng dần: 1 trước 3. Sau đó mới đến tác vụ mức 2, rồi mức 1. Bản thân việc đưa một tác vụ mới vào hàng đợi **không khởi chạy** tác vụ đó; `poll()` chỉ chọn và lấy phần tử kế tiếp.

Nếu một tác vụ đã bắt đầu chạy, tác vụ mới ưu tiên cao hơn không tự ngắt nó. Hàng đợi quản lý **việc chọn tác vụ tiếp theo**, không thực hiện cơ chế tiền nhiệm (preemption) cho tác vụ đang chạy.

## 5. Chương trình Java 17 hoàn chỉnh

Tạo tệp tên **`TopKAndPriorityDemo.java`** trong thư mục thực hành.

~~~java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class TopKAndPriorityDemo {

    private record Task(long sequence, String name, int priority) {
    }

    public static List<Integer> topKLargest(int[] numbers, int k) {
        if (numbers == null) {
            throw new IllegalArgumentException("numbers không được null");
        }
        if (k < 0) {
            throw new IllegalArgumentException("k phải không âm");
        }
        if (k == 0 || numbers.length == 0) {
            return List.of();
        }

        PriorityQueue<Integer> minHeap = new PriorityQueue<>();

        for (int number : numbers) {
            if (minHeap.size() < k) {
                minHeap.offer(number);
            } else if (number > minHeap.peek()) {
                minHeap.poll();
                minHeap.offer(number);
            }
        }

        List<Integer> result = new ArrayList<>(minHeap);
        result.sort(Comparator.reverseOrder());
        return result;
    }

    public static void main(String[] args) {
        int[] scores = {12, 7, 25, 10, 25, 3, 19, 18};

        System.out.println("Top 3 điểm: " + topKLargest(scores, 3));
        System.out.println("Top 0 điểm: " + topKLargest(scores, 0));

        Comparator<Task> taskOrder =
                Comparator.comparingInt((Task task) -> task.priority())
                        .reversed()
                        .thenComparingLong(Task::sequence);

        PriorityQueue<Task> pending = new PriorityQueue<>(taskOrder);

        pending.offer(new Task(0, "Sao lưu dữ liệu", 2));
        pending.offer(new Task(1, "Xử lý sự cố", 5));
        pending.offer(new Task(2, "Gửi email", 2));
        pending.offer(new Task(3, "Khôi phục dịch vụ", 5));
        pending.offer(new Task(4, "Dọn tệp tạm", 1));

        System.out.println("Thứ tự xử lý tác vụ:");
        while (!pending.isEmpty()) {
            Task task = pending.poll();
            System.out.println("- " + task.name()
                    + " | ưu tiên=" + task.priority()
                    + " | đến=" + task.sequence());
        }
    }
}
~~~

Trong thư mục chứa tệp trên, biên dịch và chạy:

~~~text
javac -encoding UTF-8 --release 17 TopKAndPriorityDemo.java
java -Dfile.encoding=UTF-8 TopKAndPriorityDemo
~~~

**Kết quả stdout:**

~~~text
Top 3 điểm: [25, 25, 19]
Top 0 điểm: []
Thứ tự xử lý tác vụ:
- Xử lý sự cố | ưu tiên=5 | đến=1
- Khôi phục dịch vụ | ưu tiên=5 | đến=3
- Sao lưu dữ liệu | ưu tiên=2 | đến=0
- Gửi email | ưu tiên=2 | đến=2
- Dọn tệp tạm | ưu tiên=1 | đến=4
~~~

### Đọc đoạn code top-K

- `topKLargest(int[] numbers, int k)` từ chối mảng `null` và K âm.
- Khi K bằng 0 hoặc mảng rỗng, hàm trả `List.of()`. Danh sách rỗng này không cần thêm hoặc xóa phần tử.
- `minHeap.size() < k` cho phép thêm cho đến khi đủ K ứng viên.
- Khi đã đủ K, `number > minHeap.peek()` quyết định có thay phần tử nhỏ nhất hay không.
- `new ArrayList<>(minHeap)` chép dữ liệu khỏi heap; `sort(Comparator.reverseOrder())` là bước riêng để đáp ứng đầu ra giảm dần.
- `scores` là mảng đầu vào; thuật toán không sửa các vị trí của mảng này.

### Đọc đoạn code hàng đợi tác vụ

- `Task` có ba thành phần bất biến: `sequence`, `name`, `priority`.
- `Comparator.comparingInt(...).reversed()` đưa mức ưu tiên cao hơn lên trước.
- `thenComparingLong(Task::sequence)` bảo đảm thứ tự đến tăng dần khi mức ưu tiên bằng nhau.
- `pending.offer(...)` chỉ đưa tác vụ vào hàng đợi.
- Mỗi vòng `while (!pending.isEmpty())` gọi `poll()`, lấy một tác vụ có thứ tự ưu tiên cao nhất theo comparator và in thông tin của nó.
- Sau năm lần `poll()`, hàng đợi rỗng. Đầu ra trong chương trình ổn định vì comparator đã xác định cả ưu tiên và thứ tự đến.

Không được thay đoạn `poll()` bằng cách duyệt trực tiếp `pending` nếu cần in các tác vụ theo đúng thứ tự xử lý.

## 6. Đối chiếu hai cách dùng cùng một cấu trúc

| Khía cạnh | Top-K lớn nhất | Hàng đợi tác vụ |
| --- | --- | --- |
| Mục đích | Giữ lại K ứng viên tốt nhất | Chọn tác vụ kế tiếp |
| Phần tử đầu heap | Số nhỏ nhất trong nhóm K tốt nhất | Tác vụ ưu tiên nhất |
| Comparator | Thứ tự tự nhiên tăng dần của `Integer` | Ưu tiên giảm dần, rồi thứ tự đến tăng dần |
| Kích thước | Giới hạn không quá K | Tăng/giảm theo tác vụ chờ |
| Khi có phần tử mới | Có thể loại ứng viên yếu nhất | Thêm vào để xét thứ tự xử lý |
| Khi lấy phần tử | Loại ứng viên thấp trong quá trình cập nhật | Lấy tác vụ cần xử lý tiếp |
| Đầu ra | Chuyển thành `List` rồi sắp xếp giảm dần | `poll()` cho từng tác vụ |

Điểm quyết định là đặt đúng **phần tử cần xem thường xuyên** tại `peek()`. Với top-K, đó là giá trị thấp nhất trong K ứng viên. Với xử lý tác vụ, đó là công việc khẩn cấp nhất.

## 7. Biến thể bài toán và ảnh hưởng tới thiết kế

### 7.1. Top-K **không trùng giá trị**

Nếu đề bài đổi thành “K điểm **phân biệt** cao nhất”, kết quả ví dụ phải là `[25, 19, 18]`. Ta cần một bước loại giá trị trùng, chẳng hạn ghi nhận các giá trị đã thấy bằng `HashSet<Integer>` trước khi cân nhắc đưa vào heap. Một tập `HashSet` nhớ **tất cả** giá trị đã đọc có thể tốn O(s) bộ nhớ với s là số giá trị phân biệt, nên không còn chỉ dùng O(K) bộ nhớ tổng cộng. Cần nêu đúng chi phí của biến thể này.

### 7.2. Top-K **bản ghi** thay vì số

Nếu cần lấy K sinh viên có điểm cao nhất, mỗi phần tử có thể là `Student(id, score, sequence)`. Khi nhiều sinh viên cùng điểm và K cắt đúng vùng hòa điểm, phải xác định tiêu chí: mã sinh viên nhỏ hơn, đến trước hoặc chấp nhận bất kỳ? Việc so sánh `number > peek()` chỉ giải quyết **giá trị số**, chưa phải quy tắc lựa chọn các bản ghi bằng điểm. Comparator và điều kiện thay thế phải thống nhất cùng một chính sách tie-break.

### 7.3. Top-K **tần suất**

Nếu đề yêu cầu K từ xuất hiện nhiều nhất, ta phải đếm trước bằng `Map<String, Integer>`, rồi chọn K cặp (từ, tần suất) theo số lần xuất hiện. Cần quy định cách chọn khi hai từ có cùng tần suất; dùng giá trị từ làm tiêu chí chính là sai đề.

### 7.4. Ưu tiên thay đổi khi tác vụ đang chờ

`PriorityQueue` không tự sắp lại heap khi thay đổi các trường ảnh hưởng tới comparator của một đối tượng đã đưa vào. Với tác vụ đổi mức ưu tiên, phải thiết kế thao tác hủy/thêm lại hoặc cơ chế đánh dấu bản ghi cũ và thêm bản cập nhật. Record bất biến trong ví dụ tránh khả năng thay đổi ngầm làm hỏng thứ tự.

### 7.5. Đa luồng và nguồn tác vụ liên tục

`PriorityQueue` không bảo đảm an toàn khi nhiều luồng đồng thời thêm và lấy. Với nhiều producer/consumer, có thể cân nhắc `PriorityBlockingQueue` và mô hình worker phù hợp. Tuy nhiên, việc ưu tiên tác vụ **không tự bảo đảm giới hạn dung lượng hàng chờ, công bằng tuyệt đối hoặc tác vụ ít ưu tiên chắc chắn đến lượt**. Nếu liên tục xuất hiện tác vụ khẩn cấp, tác vụ ưu tiên thấp có thể chờ rất lâu; đó là vấn đề chính sách lập lịch, không giải quyết chỉ bằng comparator.

## 8. Lỗi thường gặp

**Sai 1 — Dùng max-heap chứa cả N phần tử để gọi là tối ưu bộ nhớ top-K.** Cách đó có thể đúng kết quả nhưng giữ O(N) phần tử nếu nạp toàn bộ mảng. Khi K nhỏ, min-heap giới hạn K tiết kiệm hơn.

**Sai 2 — In trực tiếp heap và tin rằng đã được sắp xếp.** `PriorityQueue` chỉ bảo đảm phần tử đầu; thứ tự iterator không phải thứ tự `poll()`. Muốn kết quả có thứ tự, phải sắp xếp danh sách hoặc lấy phần tử đúng cách.

**Sai 3 — Viết comparator bằng phép trừ `b - a`.** Số nguyên có thể tràn, làm thứ tự sai. Dùng `Integer.compare(b, a)` hoặc `Comparator.comparingInt(...).reversed()`. Với số thứ tự kiểu long, dùng `Long.compare` hoặc `thenComparingLong`.

**Sai 4 — Chỉ so sánh mức ưu tiên mà quên quy tắc khi bằng nhau.** Java không cam kết FIFO cho các tác vụ comparator xem là bằng nhau. Nếu yêu cầu “đến trước xử lý trước” trong cùng mức, thêm `sequence` vào comparator.

**Sai 5 — Gọi `peek()` ở hàng đợi rỗng hoặc bỏ qua K = 0.** Khi rỗng, `peek()` trả `null`; tự mở hộp số nguyên có thể gây `NullPointerException`. Xử lý các biên trước khi đi vào nhánh so sánh.

**Sai 6 — Thay đổi giá trị ưu tiên của phần tử đang nằm trong heap.** Cấu trúc có thể không tự tái lập thứ tự như mong đợi. Nên lưu dữ liệu ưu tiên bất biến hoặc cập nhật lại hàng đợi theo quy trình rõ ràng.

**Sai 7 — Coi hàng đợi ưu tiên là bộ lập lịch đang chạy.** Việc thêm vào hàng đợi hoặc lấy bằng `poll()` không tự tạo luồng, không tự quản lý retry, timeout hay ngắt tác vụ đang thực thi.

## 9. Bài tập củng cố và kết quả để tự đối chiếu

### Bài 1. Top-2 với phần tử trùng

Cho `[4, 15, 2, 15, 9, 20]` và K = 2. Ghi diễn biến ứng viên sau từng số rồi chạy hàm. Đầu ra mong đợi: `[20, 15]`. Giải thích vì sao một trong hai lần xuất hiện của `15` bị thay bởi `20`.

### Bài 2. Biên của top-K

Với đầu vào `[8, 1, 8]`, chạy hàm với K = 0, 1, 3, 5. Kết quả phải lần lượt là `[]`, `[8]`, `[8, 8, 1]`, `[8, 8, 1]`. Với K = -1, xác minh hàm ném `IllegalArgumentException` và mảng đầu vào không thay đổi.

### Bài 3. Trộn ưu tiên và thứ tự đến

Thêm A (đến 0, ưu tiên 2), B (đến 1, ưu tiên 4), C (đến 2, ưu tiên 4), D (đến 3, ưu tiên 1), E (đến 4, ưu tiên 2). Trước khi chạy, dự đoán chuỗi `poll()` là **B, C, A, E, D**. Sau khi chạy, giải thích hai cặp bằng ưu tiên B/C và A/E.

### Bài 4. Thêm hạn xử lý

Bổ sung trường `deadline` cho tác vụ. Yêu cầu mới: **mức ưu tiên giảm dần → hạn xử lý sớm hơn → thứ tự nhận tăng dần**. Hãy sửa comparator và tạo trường hợp ba tác vụ cùng ưu tiên, có hai hạn bằng nhau, để kiểm tra cả hai tiêu chí phụ. Đừng dùng đối tượng có deadline có thể bị thay đổi bên trong heap.

### Bài 5. Chọn cấu trúc khi dữ liệu rất lớn

Với n = 1.000.000 điểm và K = 10, so sánh lượng bộ nhớ cần giữ giữa việc sắp xếp toàn bộ bản sao mảng và min-heap giới hạn K. Nêu vì sao bước sắp xếp cuối cùng của chỉ 10 kết quả vẫn cần thiết nếu hợp đồng yêu cầu thứ tự giảm dần.

## 10. Checklist đánh giá lời giải

- Đề bài xác định rõ K lớn nhất/nhỏ nhất, có trùng hay phân biệt, và thứ tự xuất kết quả.
- Thuật toán top-K giữ đúng bất biến K ứng viên tốt nhất sau từng phần tử.
- Heap không vượt K phần tử; nhánh K = 0 và mảng rỗng được xử lý an toàn.
- Danh sách kết quả top-K được sắp giảm dần, không dựa vào thứ tự iterator của heap.
- Comparator tác vụ có cả ưu tiên chính và tie-break phản ánh yêu cầu.
- Mỗi tác vụ có số thứ tự đến xác định; không sửa các khóa sắp xếp khi tác vụ còn trong heap.
- Đã thử đầu vào trùng, K vượt N, nhiều tác vụ cùng ưu tiên và hàng đợi rỗng.
- Phân tích thời gian/bộ nhớ phản ánh đúng n, K và cấu trúc phụ thực sự sử dụng.

Khi hiểu điều kiện “phần tử nào phải đứng đầu” và quy tắc bất biến, bạn có thể dùng cùng một `PriorityQueue` để giữ nhóm K ứng viên tốt nhất hoặc lấy tác vụ cần xử lý tiếp theo, đồng thời kiểm chứng được tính đúng của cả hai giải pháp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-007">So sánh LRU đơn giản với yêu cầu của bộ nhớ đệm thực tế</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh LRU đơn giản với yêu cầu của bộ nhớ đệm thực tế

## 1. Bài toán và mục tiêu

Một ứng dụng thường đọc đi đọc lại cùng dữ liệu: cấu hình, danh mục sản phẩm, kết quả tính toán hoặc thông tin tra cứu từ cơ sở dữ liệu. Đặt kết quả gần nơi sử dụng giúp giảm độ trễ và số lần gọi nguồn dữ liệu. Nhưng RAM hữu hạn: khi cache đầy, phải quyết định giữ mục nào và loại mục nào.

**LRU (Least Recently Used)** là một chính sách loại bỏ: khi cần chỗ, bỏ phần tử ít được truy cập gần đây nhất. LRU đơn giản rất hữu ích để luyện Java Collections và hiểu chính sách eviction, nhưng **một bộ nhớ đệm thực tế là cả hệ thống quản lý dữ liệu tạm**, còn phải xử lý thời hạn dữ liệu, tải đồng thời, giới hạn RAM, lỗi nguồn và quan sát vận hành.

Sau bài này, người học cần có thể:
- Cài một LRU theo số mục bằng Java Collections, dự đoán đúng thứ tự sau thao tác đọc/ghi.
- Phân biệt **eviction** (loại bỏ để lấy chỗ), **expiration** (hết hạn theo thời gian) và **invalidation** (chủ động làm dữ liệu cũ mất hiệu lực).
- Chỉ ra nơi LRU tối giản sẽ thất bại khi triển khai thật và lựa chọn biện pháp phù hợp với yêu cầu.
- Thiết kế phép đo và ca kiểm thử trước khi chuyển từ ví dụ học thuật sang hệ thống có tải.

### Tình huống xuyên suốt

Giả sử dịch vụ sản phẩm đọc bản ghi từ database, giữ tối đa ba sản phẩm trong một cache ở bộ nhớ tiến trình. Nếu mỗi truy vấn đều có kích thước tương tự nhau, không có cập nhật và chỉ một luồng sử dụng cache, LRU theo số mục là điểm khởi đầu hợp lý. Bây giờ thêm các yêu cầu: giá sản phẩm thay đổi, truy vấn đồng thời tăng mạnh, payload lớn nhỏ khác nhau và dịch vụ có nhiều instance. Từng yêu cầu đó làm lộ ra một giới hạn của LRU cơ bản.

## 2. LRU hoạt động như thế nào?

Với capacity = 3, giả sử thứ tự từ **ít dùng gần đây nhất → mới dùng gần đây nhất**:

| Thao tác | Thứ tự sau thao tác | Diễn giải |
| --- | --- | --- |
| put(A), put(B), put(C) | A → B → C | A là mục cũ nhất. |
| get(A) | B → C → A | Đọc trúng A khiến A trở thành mục mới dùng. |
| put(D) | C → A → D | Cache đầy, loại B rồi đưa D vào. |
| get(B) | C → A → D | B không còn trong cache: cache miss. |
| put(A, giá trị mới) | C → D → A | Cập nhật A cũng tính là một lần truy cập trong LinkedHashMap access-order. |

Cần phân biệt:
- **Hit**: khóa tồn tại và giá trị đáp ứng điều kiện hợp lệ của cache; không phải gọi nguồn dữ liệu.
- **Miss**: không có giá trị dùng được; thường phải tải từ nguồn.
- **Eviction**: loại bỏ mục theo chính sách dung lượng. Trong ví dụ là mục lâu chưa được dùng.
- **Expiration**: mục quá hạn theo chính sách thời gian, dù có thể chưa từng bị eviction.
- **Invalidation**: xóa hoặc làm mất hiệu lực mục do sự kiện cập nhật, xóa, đổi quyền hoặc yêu cầu nghiệp vụ.

LRU không biết giá trị còn đúng về nghiệp vụ hay không. Một mục được đọc liên tục có thể luôn ở cuối danh sách LRU mà vẫn chứa giá cũ.

### LRU khác các lựa chọn nào?

| Cơ chế | Căn cứ quyết định | Dùng khi nào | Giới hạn đáng nhớ |
| --- | --- | --- | --- |
| FIFO | Thời điểm đưa vào | Muốn cách đơn giản, không ưu tiên truy cập lại | Có thể loại mục vừa được đọc nhiều lần. |
| LRU | Lần truy cập gần nhất | Tập truy cập có tính cục bộ theo thời gian | Một đợt quét tuần tự có thể đẩy dữ liệu nóng ra ngoài. |
| LFU | Tần suất truy cập | Dữ liệu có độ phổ biến ổn định | Phải quản lý bộ đếm, tránh dữ liệu từng rất nóng chiếm chỗ mãi. |
| TTL / expire-after-write | Thời gian từ lúc ghi | Muốn giới hạn tuổi dữ liệu | Không tự đảm bảo giới hạn số mục hoặc số byte. |
| Expire-after-access / TTI | Thời gian từ lần truy cập | Muốn bỏ mục lâu không dùng | Dữ liệu có thể cũ mãi nếu cứ được truy cập. |
| Invalidate theo sự kiện | Biến đổi dữ liệu/nghiệp vụ | Cần làm mới ngay khi có thay đổi đã biết | Cần luồng sự kiện tin cậy và xử lý sự kiện thất lạc. |

TTL, giới hạn kích thước và chính sách eviction có thể **kết hợp** với nhau. Chúng giải những vấn đề khác nhau.

## 3. Cài LRU đơn giản bằng Java 17+

Java cung cấp **LinkedHashMap**. Constructor với tham số accessOrder = true giữ thứ tự theo lần truy cập thay vì theo thứ tự chèn. Ghi đè removeEldestEntry cho phép tự động loại mục đứng đầu khi chèn xong và vượt capacity.

Tạo tệp **LruComparisonDemo.java**:

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class LruComparisonDemo {

    static final class LruCache<K, V> {
        private final int capacity;
        private final LinkedHashMap<K, V> entries;

        LruCache(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be positive");
            }
            this.capacity = capacity;
            this.entries = new LinkedHashMap<K, V>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                    return size() > LruCache.this.capacity;
                }
            };
        }

        synchronized V get(K key) {
            return entries.get(Objects.requireNonNull(key, "key"));
        }

        synchronized void put(K key, V value) {
            entries.put(
                Objects.requireNonNull(key, "key"),
                Objects.requireNonNull(value, "value")
            );
        }

        synchronized List<K> keysOldestFirst() {
            return new ArrayList<>(entries.keySet());
        }

        synchronized int size() {
            return entries.size();
        }
    }

    public static void main(String[] args) {
        LruCache<String, Integer> cache = new LruCache<>(3);

        cache.put("A", 10);
        cache.put("B", 20);
        cache.put("C", 30);
        System.out.println("Ban dau : " + cache.keysOldestFirst());

        System.out.println("get(A)  : " + cache.get("A"));
        System.out.println("Sau doc : " + cache.keysOldestFirst());

        cache.put("D", 40);
        System.out.println("Sau D   : " + cache.keysOldestFirst());
        System.out.println("get(B)  : " + cache.get("B"));

        cache.put("A", 100);
        System.out.println("Sua A   : " + cache.keysOldestFirst());
        System.out.println("Kich thuoc: " + cache.size());
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac LruComparisonDemo.java
java LruComparisonDemo
```

Kết quả chuẩn:

```text
Ban dau : [A, B, C]
get(A)  : 10
Sau doc : [B, C, A]
Sau D   : [C, A, D]
get(B)  : null
Sua A   : [C, D, A]
Kich thuoc: 3
```

**Giải thích các chi tiết dễ nhầm:**
1. Map được cấu hình access-order, nên **get thành công thay đổi thứ tự**. Trong LinkedHashMap access-order, cả cập nhật put cho khóa đang tồn tại cũng làm mới vị trí của mục.
2. removeEldestEntry được xét sau khi chèn một mục mới. Khi kích thước vượt capacity, mục đầu danh sách bị loại. Điều kiện phải là size() > capacity, không phải >= capacity, nếu không sẽ không giữ đủ ba mục.
3. Ví dụ dùng synchronized cho mọi thao tác trên map, kể cả get và tạo ảnh chụp thứ tự. Điều này tránh truy cập cấu trúc access-order đồng thời không an toàn, nhưng một khóa toàn cục có thể làm giảm thông lượng.
4. Demo cố tình từ chối key/value null. Kết quả get(B) = null được hiểu là miss; nếu cho phép giá trị null, phải dùng containsKey hoặc kiểu kết quả rõ ràng để phân biệt miss với giá trị null.
5. keysOldestFirst tạo bản sao trong lúc khóa đang giữ; trả trực tiếp tập keySet ra ngoài sẽ khiến người gọi duyệt trên một cấu trúc có thể biến đổi sau khi phương thức kết thúc.
6. Thao tác đọc/ghi map trung bình gần O(1), song điều đó **không** bao gồm chi phí lấy dữ liệu, cấp phát đối tượng, tranh chấp khóa hoặc thu gom rác của cả dịch vụ.

Đây là một **cache LRU theo số entry, trong một JVM**. Nó chưa có loader, TTL, cân theo byte, metrics hoặc đồng bộ liên tiến trình. Khóa synchronized giúp an toàn cho các phương thức hiện có trong lớp, không bảo đảm một chuỗi thao tác nhiều bước ở phía gọi trở thành nguyên tử.

## 4. Tại sao LRU chạy đúng vẫn chưa đủ cho production?

### 4.1. Giới hạn theo số mục không phải giới hạn dung lượng RAM

Ba entry có thể lần lượt giữ 1 KB, 3 KB và 200 MB. Giữ tối đa ba key không giới hạn chắc chắn heap dùng cho các value, object header, index, string hay bản sao của payload. Với dữ liệu lớn hoặc biến thiên mạnh, hãy đặt **maximum weight** dựa trên chi phí ước tính của từng mục; thêm giới hạn thời gian và giới hạn tổng tài nguyên theo bối cảnh.

Weight là con số mô hình hóa (ví dụ kích thước byte của payload); nó không tự phản ánh chính xác mọi overhead JVM. Cần đo heap thực tế, tần suất GC và điều chỉnh ngưỡng qua vận hành. Đừng lấy sức chứa của ví dụ nhỏ làm cấu hình hệ thống.

### 4.2. LRU không bảo đảm dữ liệu còn mới

Giả sử cache giữ Product P với giá 100. Database đổi giá thành 120, nhưng người dùng tiếp tục get(P) mỗi giây. P luôn là mục rất mới theo LRU và có thể sống lâu tùy ý, trong khi giá là sai.

Các lựa chọn thường kết hợp:
- **Expire-after-write / TTL**: hết hạn sau một khoảng từ lúc ghi hoặc tải thành công; giới hạn tuổi bản lưu, nhưng chấp nhận cửa sổ dữ liệu cũ.
- **Invalidation sau cập nhật thành công**: xóa P khỏi cache khi cập nhật P trong database; lần đọc kế tiếp nạp bản mới.
- **Version/sequence**: kèm phiên bản, ETag hoặc nguồn sự thật có phiên bản; kiểm tra khi cần tính nhất quán cao.
- **Refresh có kiểm soát**: làm mới nền trước hoặc sau khi hết hạn tùy khả năng chấp nhận stale data và hỗ trợ của thư viện.

**Expiration không phải eviction vì đầy bộ nhớ**. Một entry có thể bị loại trước TTL do áp lực capacity; ngược lại nó có thể hết hạn dù cache còn rất nhiều chỗ. Nếu dữ liệu liên quan quyền truy cập, số dư hay quy tắc an toàn, TTL ngắn vẫn có thể là chưa đủ: định nghĩa mức nhất quán trước.

### 4.3. Thread-safe không tự giải quyết cache stampede

Mẫu dễ mắc lỗi:

```java
// Mảnh mã minh họa: cache và loadFromDatabase được định nghĩa ở nơi khác.
Value value = cache.get(key);
if (value == null) {
    value = loadFromDatabase(key);
    cache.put(key, value);
}
```

Hai mươi request có thể cùng thấy miss và cùng gọi database hai mươi lần. Dù get và put riêng lẻ được synchronized như demo, **toàn bộ chuỗi get → load → put không nguyên tử**.

Hướng xử lý:
- Hợp nhất các lần tải cùng key (single-flight / per-key loading) để nhiều request chờ chung một kết quả.
- Đặt timeout, giới hạn số request đang tải, retry có giới hạn và chính sách khi nguồn lỗi.
- Tránh khóa toàn bộ cache trong suốt một lệnh I/O chậm; dùng cơ chế loader hoặc cache concurrent phù hợp.
- Với nhiều process/instance, khóa trong một JVM không ngăn các instance cùng nạp một key. Cần đánh giá thêm shared cache, khóa phân tán hoặc giới hạn tải ở tầng nguồn nếu thực sự cần.

Một cache trong production cần xác định cách xử lý loader trả null, ném exception, bị timeout và kết quả đến muộn sau khi dữ liệu đã được cập nhật.

### 4.4. Tải theo key, negative cache và dữ liệu không tồn tại

Cache API chỉ trả null rất dễ nhập nhằng: không có entry, entry có giá trị null, hoặc nguồn trả “không có bản ghi”. Với key thường bị yêu cầu nhưng không tồn tại, nếu không dùng **negative caching** thì mọi request lại truy vấn database. Nếu cache trạng thái “không tồn tại”, hãy đặt thời hạn riêng, thường ngắn hơn dữ liệu bình thường, để bản ghi mới xuất hiện không bị che quá lâu.

Cần quyết định riêng trường hợp nguồn lỗi: lỗi tạm thời không đồng nghĩa “không tồn tại”. Đừng mặc định lưu exception hoặc dữ liệu chưa xác thực như một kết quả thành công.

### 4.5. Dữ liệu nào được phép chia sẻ?

Cache ở bộ nhớ tiến trình chỉ phục vụ process hiện tại; restart, scale-out hoặc deploy sẽ làm mất cache cục bộ. Với nhiều instance:
- Mỗi instance có LRU riêng nên hit rate, dữ liệu và thứ tự loại bỏ có thể khác nhau.
- Invalidation ở một instance không tự lan tới instance khác.
- Một shared cache như Redis có thể giảm sự phân tán bản lưu nhưng bổ sung network latency, vận hành, giới hạn dung lượng, serialization và failure mode mới.
- Một hệ thống có thể kết hợp cache local cấp L1 và shared cache cấp L2, song lúc đó cần chiến lược làm mới và vô hiệu hóa ở cả hai cấp.

Khóa cache phải có đủ thông tin ảnh hưởng kết quả: ví dụ tenant, locale, quyền/nhóm quyền khi thích hợp, version, tham số lọc. **Không cache nhầm dữ liệu cá nhân theo khóa thiếu ngữ cảnh**, vì đó là lỗi phân tách dữ liệu chứ không chỉ là hit rate thấp.

### 4.6. Hành vi khi cache hoặc nguồn dữ liệu gặp sự cố

Cache là một tối ưu hiệu năng, không nên mặc định trở thành nơi duy nhất lưu dữ liệu có giá trị. Thiết kế rõ:
- Khi cache miss: tải nguồn nào, timeout bao lâu, số lần retry tối đa và request nào được phép chờ.
- Khi cache lỗi: bypass về nguồn khi an toàn, trả stale có kiểm soát (chỉ nếu nghiệp vụ cho phép) hay trả lỗi rõ ràng.
- Khi nguồn dữ liệu lỗi: có cho dùng bản stale hay không; stale cần giới hạn tuổi tuyệt đối nếu dữ liệu nhạy cảm.
- Khi nhiều mục cùng hết hạn: thêm jitter cho TTL hoặc refresh phân tán để tránh dồn tải đột ngột.
- Khi ghi dữ liệu: cập nhật DB trước rồi invalidate đúng key hoặc áp dụng chiến lược write-through/write-behind có đánh giá mất dữ liệu, thứ tự thao tác và retry.

Không có một chính sách fallback đúng cho mọi lĩnh vực: ảnh đại diện có thể stale vài phút, còn hạn mức giao dịch hoặc quyền truy cập có thể đòi hỏi điều kiện nghiêm ngặt hơn.

## 5. Bảng đối chiếu: từ bài tập LRU đến cache dùng thật

| Khía cạnh | LRU LinkedHashMap của bài học | Nhu cầu cache production và câu hỏi thiết kế |
| --- | --- | --- |
| Mục tiêu | Hiểu thứ tự truy cập và loại mục | SLA về latency, tải nguồn, tính đúng đắn |
| Capacity | Đếm số entry | Đo chi phí bộ nhớ/weight, headroom heap, GC |
| Eviction | Bỏ entry ít dùng gần đây | Chọn LRU/LFU/thuật toán thích ứng theo workload; đo hiệu quả |
| Expiration | Chưa có | TTL, expire-after-access, refresh, jitter |
| Consistency | Không liên quan cập nhật nguồn | Invalidate/version/định nghĩa mức stale chấp nhận được |
| Concurrency | Một khóa synchronized | Tránh lock contention, gộp load cùng key, xử lý race |
| Loading | Người gọi tự viết | Loader, timeout, lỗi, null, negative caching |
| Topology | Trong một JVM | Nhiều instance, local/shared/two-level, invalidation |
| Observability | In danh sách key | Hit/miss, eviction theo lý do, load time, error, heap, p95/p99 |
| Security | Khóa là chuỗi ví dụ | Tenant isolation, cache key, quyền, dữ liệu nhạy cảm |
| Lifecycle | Mất khi ứng dụng dừng | Warm-up, restart, autoscale, cold start, giới hạn burst |

Bảng này **không có nghĩa** phải xây hết tính năng mới được dùng cache. Hãy chọn theo yêu cầu thực; một cache nhỏ trong ứng dụng nội bộ có thể chỉ cần vài điều kiện và số liệu cơ bản.

## 6. Lựa chọn công cụ: tự viết, thư viện, hay shared cache

**Tự cài bằng LinkedHashMap** phù hợp để học Collections, chạy demo hoặc làm cache rất nhỏ với ngữ nghĩa rõ ràng, ít luồng và chấp nhận thiếu tính năng. Khi viết thật, bạn sở hữu mọi race, TTL, metric, loader và vận hành.

**Thư viện cache cục bộ như Caffeine** phù hợp khi cần cache trong một JVM với quản lý dung lượng, thời hạn, loader, thống kê và concurrency đã được thư viện xử lý. Ví dụ sau là **mảnh minh họa API, cần dependency Caffeine và hàm repository thật**, không thể biên dịch độc lập chỉ bằng JDK:

```java
Cache<String, Product> products = Caffeine.newBuilder()
    .maximumSize(10_000)
    .expireAfterWrite(Duration.ofMinutes(5))
    .recordStats()
    .build();

Product product = products.get(id, key -> repository.findById(key));
```

Trong mã thật phải import kiểu Cache, Caffeine, Duration, Product; cần xác định repository trả về object, null hoặc exception thế nào. Đừng sao chép thông số 10.000 và 5 phút mà không có dữ liệu tải, dung lượng, yêu cầu nhất quán. Caffeine tối ưu cho cache **local**; không tự đồng bộ dữ liệu giữa các JVM.

**Shared cache như Redis** hữu ích khi nhiều instance cần chia sẻ bản lưu hoặc khi bộ nhớ ứng dụng không phù hợp để giữ lượng dữ liệu lớn. Đổi lại phải quản lý network, serialization, outage, TTL, áp lực RAM và eviction policy của chính hệ thống shared. Không đồng nhất “bật LRU” trong một sản phẩm với chính xác thuật toán LinkedHashMap của bài tập.

**Không dùng cache** cũng có thể là lựa chọn tốt nếu truy vấn rẻ, dữ liệu đổi liên tục, khó invalidate, hit rate thấp hoặc chi phí rủi ro dữ liệu cũ vượt phần hiệu năng tiết kiệm.

## 7. Đo hiệu quả thay vì đoán

Một cache được đánh giá bằng **hiệu quả toàn hệ thống**:
- Hit ratio = hits / (hits + misses). Phải định nghĩa rõ miss do absent, expired, invalidated; bỏ qua phép chia nếu chưa có lượt đọc.
- Load success/failure, thời gian truy cập nguồn, số request tải đồng thời, số lần tải trùng cùng key.
- Eviction count theo lý do: capacity/weight/expiration/manual invalidation nếu thư viện cung cấp.
- p50/p95/p99 latency của request, QPS tới database, CPU, heap, GC pause và network nếu có shared cache.
- Dung lượng thực tế và phân phối kích thước entry; tỷ lệ key nóng so với truy cập tuần tự.

Ví dụ: tỷ lệ hit 95% vẫn có thể xấu nếu 5% miss đồng thời tạo burst quá lớn tới database; tỷ lệ hit 70% có thể đủ nếu truy vấn miss rẻ và cache đơn giản, dễ duy trì. Đo bằng workload gần thực tế, có cả giai đoạn cold cache và tải tăng đột ngột. Benchmark chi tiết về chi phí map/lock nên dùng công cụ như JMH, tránh kết luận từ vòng lặp microbenchmark tự viết chưa warm-up.

## 8. Thực hành kiểm chứng và các ca biên

Bắt đầu bằng việc chạy chương trình mục 3, sau đó kiểm chứng các trường hợp sau. **Expected** bên dưới xét riêng một cache có capacity = 3 và không có TTL.

| Test | Chuỗi thao tác | Kỳ vọng |
| --- | --- | --- |
| Eviction đúng LRU | put(A), put(B), put(C), get(A), put(D) | B bị loại; thứ tự C, A, D |
| Ghi đè khóa cũ | put(A), put(B), put(C), put(A, mới) | Không tăng size; A thành mới nhất |
| Đọc khóa thiếu | put(A), get(X) | Trả null, A vẫn còn |
| Input không hợp lệ | Tạo cache capacity = 0 | Ném IllegalArgumentException |
| Value null | put(A, null) | Ném NullPointerException theo quyết định API demo |
| Đúng giới hạn | Chèn 100 key khác nhau với capacity = 3 | size không vượt 3 |
| Hai luồng dùng cùng cache | Đọc/ghi xen kẽ vào một LruCache | Không làm hỏng trạng thái map nhờ synchronized, song thứ tự cuối phụ thuộc lịch chạy |

**Bài tập mở rộng 1 – TTL:** thêm tuổi tối đa cho value và một Clock/Ticker có thể điều khiển trong test. Trường hợp cần kiểm thử: mục còn trong thứ tự LRU nhưng đã quá thời hạn thì get phải được coi là miss. Đừng dùng Thread.sleep để tạo kiểm thử phụ thuộc timing. Xác định rõ expire-after-write hay expire-after-access trước khi code.

**Bài tập mở rộng 2 – cache stampede:** giả lập mười request cùng truy vấn một key chưa có. Đếm số lần loader thực sự chạy. Chỉ synchronized get và synchronized put riêng lẻ sẽ không bảo đảm loader chạy đúng một lần. Thử cơ chế loading theo key rồi đo thời gian chờ của các request khác.

**Bài tập mở rộng 3 – cân theo weight:** thay ba value kích thước rất khác nhau. Chỉ ra tại sao capacity = 3 không thể là ngân sách RAM. Thiết kế quy tắc weight và kiểm thử mục quá lớn so với ngân sách sẽ được xử lý ra sao.

**Bài tập mở rộng 4 – tính nhất quán:** cache giá sản phẩm, cập nhật database rồi đọc lại. Xác định yêu cầu: có thể stale tối đa bao lâu, có sự kiện invalidation không, khi sự kiện đến trễ thì xử lý gì, và khi DB lỗi có được trả giá cũ không.

## 9. Checklist quyết định trước khi đưa cache vào ứng dụng

1. **Vì sao cache?** Biết bottleneck cụ thể và chỉ số muốn cải thiện (latency hay chi phí gọi nguồn).
2. **Cache cái gì?** Chọn key, value, tenant và dữ liệu nào không được lưu do yêu cầu quyền riêng tư/độ mới.
3. **Giới hạn tài nguyên?** Đếm số entry hay weight, có giới hạn heap/network và quan sát GC không?
4. **Bao giờ giá trị sai?** Xác định TTL, sự kiện invalidate, phiên bản và mức stale có thể chấp nhận.
5. **Ai cùng truy cập?** Một luồng, nhiều luồng hay nhiều instance? Chống duplicate load ở phạm vi nào?
6. **Khi lỗi thì sao?** Quyết định timeout, retry, fallback và có trả stale hay không.
7. **Đã chứng minh lợi ích?** Thử cold/warm cache, concurrent misses, phân phối key thật và đọc số liệu latency/load.

### Kết luận

**LRU trả lời một câu hỏi hẹp: “Khi thiếu chỗ, loại mục nào?”** Cache production còn phải trả lời “dữ liệu có đúng không, được giữ bao lâu, tốn bao nhiêu RAM, tải đồng thời xử lý ra sao và hệ thống quan sát/phục hồi thế nào?”. LinkedHashMap là công cụ rất tốt để hiểu eviction theo truy cập; việc chọn cơ chế cache thật cần bắt đầu từ đặc tính workload và yêu cầu tính đúng đắn, rồi mới chọn thư viện hoặc hạ tầng.

## Tài liệu đọc thêm

- Java 17 API – LinkedHashMap (insertion-order, access-order, removeEldestEntry): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html
- Caffeine (thư viện cache Java, chính sách eviction/expiration): https://github.com/ben-manes/caffeine
- Redis eviction policies (giới hạn bộ nhớ và lựa chọn chính sách): https://redis.io/docs/latest/develop/reference/eviction/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-008">Xây dựng bài toán sản xuất–tiêu thụ bằng BlockingQueue</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành tổng hợp: Producer–Consumer với BlockingQueue trong Java 17

## 1. Đề bài và yêu cầu

Xây dựng mô hình **sản xuất–tiêu thụ (producer–consumer)**, trong đó producer tạo công việc và consumer xử lý công việc trên các luồng khác nhau. Nếu hai phía chạy với tốc độ khác nhau, dữ liệu phải được trao đổi an toàn: producer chờ khi vùng đệm đầy, consumer chờ khi chưa có công việc, và chương trình có cách kết thúc rõ ràng.

Yêu cầu cụ thể của bài thực hành:

- **2 producer**, mỗi producer sinh **6 công việc** có định danh riêng; tổng cộng **12 công việc**.
- **3 consumer** lấy và xử lý các công việc song song.
- Vùng đệm chỉ giữ tối đa **4 công việc** đang chờ, tránh phát sinh hàng đợi vô hạn khi producer nhanh hơn consumer.
- Chờ toàn bộ producer xong mới phát tín hiệu dừng; mọi consumer phải thoát được.
- Tổng hợp số lượng đã xử lý và kiểm tra hàng đợi rỗng sau khi kết thúc.

Trọng tâm là `BlockingQueue`, `ArrayBlockingQueue`, `put`, `take`, *backpressure*, `ExecutorService`, `Future` và cơ chế **poison pill**. Không cần tự dùng `synchronized`, `wait()` hoặc `notify()` để cài đặt vùng đệm.

## 2. Mô hình và nguyên lý

~~~text
Producer 1 ──┐
             ├── put() ──> [ ArrayBlockingQueue, capacity = 4 ] ── take() ──> Consumer 1
Producer 2 ──┘                                          ├─ take() ──> Consumer 2
                                                       └─ take() ──> Consumer 3
~~~

`BlockingQueue<E>` là một giao diện hàng đợi an toàn khi nhiều luồng cùng truy cập. `ArrayBlockingQueue` triển khai hàng đợi FIFO có dung lượng cố định:

- `put(e)` đưa công việc vào hàng đợi. Khi đầy, producer **chờ** đến khi có chỗ hoặc bị ngắt.
- `take()` lấy công việc khỏi hàng đợi. Khi rỗng, consumer **chờ** đến khi có phần tử hoặc bị ngắt.
- Queue tự bảo đảm đồng bộ cho các thao tác của chính nó. Người dùng không phải tự khóa quanh từng lệnh `put` và `take`.

Khi consumer xử lý chậm, bốn vị trí chờ có thể đầy và producer bị chặn tại `put`. Đây là **backpressure**: hệ thống giới hạn số lượng công việc đang xếp hàng và làm chậm phía cung cấp khi phía xử lý chưa theo kịp. Sức chứa 4 chỉ đếm công việc *nằm trong queue*, không bao gồm công việc đã được lấy ra và đang xử lý.

Consumer **không thể coi queue đang rỗng là producer đã làm xong**, vì một producer có thể bổ sung công việc ngay sau đó. Chương trình dùng một đối tượng riêng `STOP` (poison pill) để thông báo kết thúc. Trình tự bắt buộc:

1. Các producer đưa mọi công việc thật vào queue.
2. Luồng main chờ từng `Future` của producer hoàn tất.
3. Main đưa **một tín hiệu `STOP` cho mỗi consumer** vào sau các công việc thật.
4. Mỗi consumer đọc được `STOP` thì thoát; main chờ kết quả của cả ba consumer.

Do hàng đợi FIFO, các công việc thật đã đưa vào trước `STOP` sẽ được lấy ra trước tín hiệu này. Ba consumer cần ba tín hiệu để không ai tiếp tục chờ ở `take()` vô hạn.

## 3. Cài đặt hoàn chỉnh chạy trên Java 17+

Tạo tệp **`BlockingQueueDemo.java`** với nội dung sau. `Job` là một `record` bất biến, chứa ID producer và số thứ tự để nhận diện từng công việc. Pool dùng **5 luồng** để đủ chỗ chạy đồng thời cả hai producer và ba consumer; submit consumer trước giúp sẵn sàng đón việc.

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BlockingQueueDemo {
    private static final int PRODUCER_COUNT = 2;
    private static final int CONSUMER_COUNT = 3;
    private static final int JOBS_PER_PRODUCER = 6;
    private static final int QUEUE_CAPACITY = 4;

    private record Job(int producerId, int sequence) {}

    // Tín hiệu dừng: chỉ đưa vào queue sau khi mọi producer hoàn tất.
    private static final Job STOP = new Job(-1, -1);

    public static void main(String[] args)
            throws InterruptedException, ExecutionException {

        BlockingQueue<Job> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        ExecutorService pool = Executors.newFixedThreadPool(
                PRODUCER_COUNT + CONSUMER_COUNT);

        List<Future<?>> producers = new ArrayList<>();
        List<Future<Integer>> consumers = new ArrayList<>();

        try {
            for (int i = 1; i <= CONSUMER_COUNT; i++) {
                final int consumerId = i;

                consumers.add(pool.submit(() -> {
                    int processed = 0;
                    while (true) {
                        Job job = queue.take();  // Đợi nếu queue rỗng.

                        if (job == STOP) {
                            return processed;
                        }

                        System.out.printf(
                                "Consumer-%d xử lý P%d-#%d%n",
                                consumerId, job.producerId(), job.sequence());

                        // Giả lập một công việc tương đối chậm.
                        Thread.sleep(50);
                        processed++;
                    }
                }));
            }

            for (int i = 1; i <= PRODUCER_COUNT; i++) {
                final int producerId = i;

                producers.add(pool.submit(() -> {
                    for (int n = 1; n <= JOBS_PER_PRODUCER; n++) {
                        queue.put(new Job(producerId, n)); // Đợi nếu đầy.
                    }
                    return null;
                }));
            }

            // get() chờ producer xong và bộc lộ lỗi trong task.
            for (Future<?> producer : producers) {
                producer.get();
            }

            // Có ba consumer nên phải có đúng ba tín hiệu dừng.
            for (int i = 0; i < CONSUMER_COUNT; i++) {
                queue.put(STOP);
            }

            int processedTotal = 0;
            for (Future<Integer> consumer : consumers) {
                processedTotal += consumer.get();
            }

            int expected = PRODUCER_COUNT * JOBS_PER_PRODUCER;
            if (processedTotal != expected || !queue.isEmpty()) {
                throw new IllegalStateException(
                        "Sai số lượng: processed=" + processedTotal
                                + ", queueSize=" + queue.size());
            }

            System.out.printf(
                    "Hoàn tất: %d/%d công việc; hàng đợi còn %d phần tử%n",
                    processedTotal, expected, queue.size());
        } finally {
            // Đánh thức các task có thể đang kẹt trong put/take/sleep
            // nếu producer hoặc consumer gặp lỗi hay main bị interrupt.
            // Với lượt chạy thành công, các Future đều đã hoàn thành.
            pool.shutdownNow();
        }
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~bash
javac BlockingQueueDemo.java
java BlockingQueueDemo
~~~

Ví dụ một phần kết quả:

~~~text
Consumer-2 xử lý P1-#1
Consumer-1 xử lý P2-#1
...
Hoàn tất: 12/12 công việc; hàng đợi còn 0 phần tử
~~~

Chương trình in **12 dòng xử lý** và cuối cùng in **`12/12`**, hàng đợi còn **0** phần tử khi chạy bình thường. Thứ tự các dòng xử lý (và consumer nào nhận từng việc) **không cố định**, phụ thuộc lịch chạy của các luồng. Những dòng minh họa đầu tiên không phải đầu ra bắt buộc; dòng kết luận là bất biến cần kiểm tra.

## 4. Giải thích chi tiết từng giai đoạn

**Consumer khởi động:** Các consumer gọi `queue.take()` trong vòng lặp; nếu chưa có việc, luồng được chặn mà không phải liên tục hỏi `isEmpty()`. Consumer chỉ trở lại làm việc khi phần tử xuất hiện hoặc luồng bị interrupt.

**Producer đưa việc vào:** Hai producer tạo đúng các cặp ID `(1,1)` đến `(1,6)` và `(2,1)` đến `(2,6)`. Khi queue đủ bốn công việc đang chờ, `put()` bị chặn. Một consumer lấy ra một phần tử sẽ giải phóng vị trí cho producer. `Thread.sleep(50)` chỉ mô phỏng thời gian xử lý để dễ quan sát quá tải; queue không yêu cầu sleep để vận hành.

**Main xác định thời điểm có thể dừng:** `producer.get()` chờ producer hoàn thành hoặc ném `ExecutionException` nếu task thất bại. Vì main đợi tất cả producer trước khi gửi `STOP`, không có công việc thật của các producer này được xếp vào queue phía sau tín hiệu dừng.

**Consumer nhận tín hiệu:** Mỗi `STOP` đi qua queue như một phần tử bình thường, nhưng consumer nhận diện rồi **trả về ngay** thay vì xử lý hay cộng bộ đếm. So sánh `job == STOP` là có chủ ý: cả ba tín hiệu đều là **chính cùng đối tượng** sentinel; các producer chỉ tạo đối tượng `Job` mới với ID dương.

**Main kiểm tra kết quả:** Khi cả ba `consumer.get()` trả về, các consumer đã dừng. Mỗi lần consumer xử lý xong một việc thật, nó tăng bộ đếm của riêng mình; main cộng các bộ đếm thành `processedTotal`. Chỉ sau khi mọi task hoàn tất mới kiểm tra `queue.isEmpty()`; kiểm tra `size()` hoặc `isEmpty()` khi các luồng còn chạy sẽ không cho biết trạng thái cuối cùng ổn định.

**Dọn tài nguyên:** `shutdownNow()` ở `finally` gọi interrupt lên các task còn chạy. `put`, `take` và `sleep` là các lệnh có thể bị interrupt. Với chương trình minh họa thành công, main đã đợi mọi Future hoàn tất trước khi giải phóng pool. Với dịch vụ chạy lâu, cần bổ sung quy trình dừng có thời hạn, ghi nhận thất bại và giám sát các task không phản hồi interrupt.

**Tính nhìn thấy bộ nhớ:** Các thao tác trước khi một phần tử được đưa vào `BlockingQueue` có quan hệ *happens-before* với thao tác của luồng khác sau khi phần tử đó được lấy ra. Tuy vậy, queue chỉ bảo đảm an toàn của thao tác hàng đợi; nếu nhiều luồng cùng sửa một payload mutable thì vẫn cần cơ chế đồng bộ hoặc thiết kế bất biến. `record Job` tránh chia sẻ trạng thái mutable trong bài này.

## 5. Những API liên quan và cách chọn

| Tác vụ | API | Khi queue đầy/rỗng |
| --- | --- | --- |
| Đưa phần tử và sẵn sàng chờ | `put(e)` | Chờ khi đầy; có thể ném `InterruptedException` |
| Thử đưa ngay | `offer(e)` | Trả `false` nếu đầy |
| Đưa với thời gian chờ tối đa | `offer(e, time, unit)` | Hết hạn trả `false`, cũng có thể bị interrupt |
| Lấy phần tử và sẵn sàng chờ | `take()` | Chờ khi rỗng; có thể ném `InterruptedException` |
| Thử lấy ngay | `poll()` | Trả `null` nếu rỗng |
| Lấy với thời gian chờ tối đa | `poll(time, unit)` | Hết hạn trả `null`, cũng có thể bị interrupt |

`add(e)` có thể ném `IllegalStateException` nếu hàng đợi bounded đã đầy, trong khi `remove()` không đối số ném `NoSuchElementException` nếu hàng đợi rỗng. Chúng không thay thế `put/take` trong mô hình cần chờ có kiểm soát.

Ví dụ dùng timeout (**đoạn minh họa**; `queue` và `job` phải được khai báo trong ngữ cảnh gọi):

~~~java
boolean accepted = queue.offer(
        job, 200, java.util.concurrent.TimeUnit.MILLISECONDS);
if (!accepted) {
    // Hết thời gian chờ: thông báo quá tải hoặc thử lại có giới hạn.
}

Job next = queue.poll(
        300, java.util.concurrent.TimeUnit.MILLISECONDS);
if (next == null) {
    // Chưa có công việc; KHÔNG có nghĩa mọi producer đã kết thúc.
}
~~~

Cả hai overload có timeout có thể ném `InterruptedException`. Khi xử lý interrupt trong một task, thường cần cho exception lan ra hoặc khôi phục trạng thái bằng `Thread.currentThread().interrupt()` rồi thoát một cách phù hợp. **Không âm thầm nuốt interrupt** và chạy tiếp mãi.

## 6. Các lỗi thực tế dễ gặp

| Sai lầm | Vì sao sai | Cách làm đúng |
| --- | --- | --- |
| Consumer thấy `queue.isEmpty()` rồi tự thoát | Queue tạm rỗng nhưng producer có thể còn đang tạo việc | Phân biệt chưa có việc với hết nguồn; dùng giao thức dừng |
| Đưa `STOP` trước khi mọi producer kết thúc | Consumer có thể dừng trước khi xử lý hết dữ liệu | Đợi đủ `producer.get()` rồi mới gửi |
| Chỉ đưa một `STOP` cho ba consumer | Các consumer còn lại có thể kẹt ở `take()` | Một poison pill cho mỗi consumer |
| Pool chỉ có 2 thread, submit cả hai producer trước consumer | Hai producer có thể chặn khi queue đầy, khiến consumer không có thread để chạy | Dùng pool đủ chỗ hoặc pool riêng cho từng vai trò |
| Dùng queue không giới hạn khi nguồn việc tăng nhanh | Công việc tồn đọng có thể tăng bộ nhớ vô hạn | Chọn dung lượng bounded và chính sách backpressure |
| Tự kiểm tra `remainingCapacity()` rồi mới `put()` | Kết quả kiểm tra đã có thể thay đổi bởi luồng khác | Dùng ngay `put` hoặc `offer` nguyên tử |
| Gọi `shutdown()` và kỳ vọng `take()` tự trả về | `shutdown()` dừng việc nhận task mới, không phải tín hiệu cuối luồng cho queue | Dùng sentinel hoặc interrupt có quản lý |
| Bỏ qua `ExecutionException` hay `InterruptedException` | Không quan sát được lỗi, task có thể chờ không kết thúc | Xử lý lỗi, đóng worker, báo trạng thái rõ ràng |
| Giả định BlockingQueue tự lưu bền/retry/ack | Dữ liệu vẫn có thể mất khi JVM chết hoặc worker thất bại | Thiết kế retry, lưu bền, xác nhận xử lý hoặc message broker nếu cần |

Trong `LinkedBlockingQueue`, nếu không truyền capacity thì mặc định rất lớn (`Integer.MAX_VALUE`), nên dễ bỏ lỡ nhu cầu kiểm soát tồn đọng. `ArrayBlockingQueue` với capacity cụ thể cho thấy backpressure rõ ràng. Tham số fairness của `ArrayBlockingQueue` có thể ảnh hưởng thứ tự luồng được phục vụ, **không** bảo đảm thứ tự in kết quả hay cải thiện hiệu năng trong mọi trường hợp.

## 7. Bài tập kiểm chứng và mở rộng

1. **Tăng sức ép lên queue:** đặt `QUEUE_CAPACITY = 1`, đổi thời gian xử lý thành `Thread.sleep(300)`. Giải thích tại sao tổng kết vẫn là 12 công việc và tại sao producer có thể chờ lâu hơn.
2. **Tăng quy mô:** thử 4 producer, 2 consumer, 10 việc/producer. Kết quả hợp lệ cuối chương trình là **`40/40`**. Phải điều chỉnh số sentinel thành 2 và đủ luồng trong pool.
3. **Chính sách quá tải:** thay `put()` bằng `offer(e, timeout, unit)` và chủ động quyết định khi quá thời hạn. Nếu bỏ một việc, phải thống kê việc bị từ chối; không được báo số lượng xử lý như thể mọi công việc đều đã được chấp nhận.
4. **Phát hiện lặp:** bổ sung `ConcurrentHashMap.newKeySet()` để lưu các cặp mã công việc đã được xử lý; kiểm tra `add` không trả false và tập cuối chứa đủ các mã hợp lệ. Kiểm tra này mạnh hơn đối chiếu mỗi tổng bộ đếm.
5. **Thử lỗi producer:** cố ý ném exception ở một producer và quan sát `Future.get()` trả lỗi `ExecutionException`, `finally` yêu cầu hủy các task đang chờ. Đề xuất một quy trình báo lỗi, retry và dừng dịch vụ thích hợp cho production.

**Tiêu chí hoàn thành:** biên dịch và chạy được Java 17+, kết thúc bình thường với **12/12 công việc**, queue rỗng; đồng thời giải thích được `put/take`, backpressure, thời điểm gửi poison pill, số poison pill tương ứng số consumer, và cách xử lý interrupt. Đây là mô hình phối hợp luồng **trong một tiến trình**: không tự cung cấp cơ chế lưu bền, transaction, retry hay bảo đảm xử lý chính xác một lần khi hệ thống gặp sự cố.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-009">Lập bảng quyết định chọn collection và tự đánh giá qua tình huống</a>

<details>
<summary>Xem chi tiết</summary>

# Lập bảng quyết định chọn Java Collection và tự đánh giá qua tình huống

> **Mục tiêu:** Chọn collection từ yêu cầu thực tế; giải thích lựa chọn theo tính duy nhất, thứ tự, thao tác thường gặp, chi phí, `null`, mutability và đa luồng; sau đó tự đánh giá bằng tình huống và thang điểm.

## 1. Đặt đúng câu hỏi trước khi chọn lớp

Trong Java Collections Framework, `List`, `Set` và `Queue` thuộc nhánh `Collection`; `Deque` mở rộng `Queue`. `Map` lưu các cặp khóa–giá trị và **không kế thừa `Collection`**.

Khi nhận một bài toán lưu trữ, hãy lần lượt hỏi:

1. **Hình dạng dữ liệu?** Dãy, tập không trùng, tra cứu theo khóa, hàng đợi, ngăn xếp, hay hàng chờ ưu tiên?
2. **Trùng lặp?** Có cần giữ nhiều lần xuất hiện cùng một phần tử? Khóa trong map có bắt buộc duy nhất?
3. **Thứ tự?** Cần theo vị trí, thứ tự chèn, sắp xếp theo comparator, FIFO/LIFO hay độ ưu tiên?
4. **Thao tác chủ đạo?** `get(i)`, `contains`, `get(key)`, thêm/xóa ở hai đầu, truy vấn khoảng, xử lý job tiếp theo?
5. **Khối lượng và cập nhật?** Dữ liệu lớn hay nhỏ, đọc nhiều hay ghi nhiều, có giới hạn dung lượng hoặc cần bản không chỉnh sửa?
6. **Đồng thời và ràng buộc?** Nhiều luồng cùng sửa? Cần thao tác nguyên tử, chặn producer/consumer, dùng `null` hay enum?

Quy trình: **chọn interface theo hợp đồng dữ liệu → xác định thứ tự/trùng → xác định thao tác nóng → xem concurrency và dung lượng → chọn implementation → kiểm chứng giả định**. Không phải ứng dụng nào cũng chỉ cần một cấu trúc; có thể dùng map để tra khóa và queue để điều phối công việc.

## 2. Bảng quyết định: chọn theo yêu cầu, không theo thói quen

| Nhu cầu | Lớp thường chọn | Vì sao | Cảnh báo |
|---|---|---|---|
| Dãy có thứ tự, giữ trùng, truy cập index | `ArrayList<E>` | `get(i)` nhanh, append cuối hiệu quả | Chèn/xóa đầu hoặc giữa dịch phần tử |
| Dãy không cần thay đổi cấu trúc sau khi tạo | `List.of`, `List.copyOf` | Không cho `add/remove/set` | Không cho null; không deep-copy các đối tượng con |
| Phần tử duy nhất, không quan tâm thứ tự | `HashSet<E>` | `add/contains/remove` trung bình nhanh | Không hứa thứ tự duyệt |
| Phần tử duy nhất, giữ thứ tự xuất hiện đầu tiên | `LinkedHashSet<E>` | Set + insertion order | Không tự sắp theo giá trị |
| Phần tử duy nhất, luôn sắp xếp hoặc truy vấn khoảng | `TreeSet<E>` | Có `first/last/subSet` và thứ tự comparator | Thao tác khoảng `O(log n)`; comparator quyết định phần tử “trùng” |
| Tra cứu theo khóa duy nhất, không cần thứ tự | `HashMap<K,V>` | `get/put` trung bình `O(1)` | Không dựa vào thứ tự iteration |
| Map theo thứ tự chèn, hoặc truy cập gần đây | `LinkedHashMap<K,V>` | Cho thứ tự duyệt ổn định; hỗ trợ nền tảng LRU | LRU cần bật access-order và cài eviction |
| Map khóa sắp xếp, cần `subMap` | `TreeMap<K,V>` | Navigable, duyệt theo key | Không sắp theo value |
| FIFO hoặc LIFO một luồng | `ArrayDeque<E>` | Nhanh ở hai đầu, triển khai `Deque` | Không cho null, không thread-safe |
| Công việc lấy ra theo priority | `PriorityQueue<E>` | `offer/poll` khoảng `O(log n)` | Iterator không bảo đảm sorted; bằng priority không tự FIFO |
| Hàng đợi nhiều luồng, giới hạn dung lượng, cần chặn | `ArrayBlockingQueue<E>` | `put/take` đợi khi đầy/rỗng | Phải thiết kế hủy/đóng tác vụ để tránh chờ mãi |
| Hàng đợi concurrent không chặn, không giới hạn cố định | `ConcurrentLinkedQueue<E>` | FIFO không chặn | Không tự cung cấp backpressure |
| Map nhiều luồng ghi | `ConcurrentHashMap<K,V>` | `merge/compute/putIfAbsent` hỗ trợ cập nhật nguyên tử theo khóa | Không cho null; “đọc rồi ghi” riêng lẻ có thể race |
| Danh sách nhiều luồng đọc nhiều, hiếm sửa | `CopyOnWriteArrayList<E>` | Iterator theo snapshot | Mỗi lần sửa sao chép mảng |
| Tập giá trị của một enum | `EnumSet<E>` | Gọn, nhanh, duyệt theo thứ tự khai báo enum | Chỉ nhận enum, không nhận null |
| Map với khóa enum | `EnumMap<K,V>` | Gọn, nhanh, key theo thứ tự khai báo | Khóa phải đúng enum, không null |

**Mặc định có điều kiện:** `ArrayList` cho dãy, `HashSet` cho tập, `HashMap` cho ánh xạ, `ArrayDeque` cho FIFO/LIFO. Hãy chuyển sang lớp khác khi *yêu cầu cụ thể* khiến nó cần thiết.

`LinkedList` không phải cách tăng tốc mặc định cho `List`. `get(i)` mất `O(n)` để đi qua các nút, bộ nhớ mỗi nút lớn hơn; chèn/xóa tại nút đã tìm được có thể `O(1)` nhưng tìm nút thường vẫn `O(n)`. Với stack/queue thông thường, `ArrayDeque` thường phù hợp hơn lớp `Stack` cũ.

## 3. Bảng chi phí và các điều kiện đi kèm

`n` là số phần tử; độ phức tạp đối với hash giả định hàm băm hợp lý và phân bố bình thường. Một thao tác `O(1)` trung bình vẫn có chi phí resize, va chạm, cấp phát và ảnh hưởng bộ nhớ.

| Cấu trúc | Tìm/truy cập | Thêm | Xóa | Ngữ nghĩa thứ tự |
|---|---|---|---|---|
| `ArrayList` | `get(i): O(1)`, `contains: O(n)` | Cuối `O(1)` amortized; giữa `O(n)` | Đầu/giữa `O(n)` | Theo vị trí |
| `HashSet` / `HashMap` | `contains/get: O(1)` trung bình | `O(1)` trung bình | `O(1)` trung bình | Không cam kết |
| `LinkedHashSet` / `LinkedHashMap` | `O(1)` trung bình | `O(1)` trung bình | `O(1)` trung bình | Theo chèn; map có thể theo truy cập |
| `TreeSet` / `TreeMap` | `O(log n)` | `O(log n)` | `O(log n)` | Theo comparator/key |
| `ArrayDeque` | Tìm phần tử `O(n)` | Hai đầu `O(1)` amortized | Hai đầu `O(1)` amortized | Theo deque |
| `PriorityQueue` | `peek: O(1)`, `contains: O(n)` | `offer: O(log n)` | `poll: O(log n)` | Khi lấy đầu theo comparator |

Các lỗi hợp đồng cần kiểm tra:

- `HashSet`/`HashMap` phụ thuộc `equals` và `hashCode`: nếu `a.equals(b)` thì hai đối tượng phải có hash code bằng nhau. Không đổi trường tham gia hash của phần tử/khóa đã lưu.
- `TreeSet`/`TreeMap` coi hai phần tử/khóa tương đương nếu comparator trả về `0`. Comparator chỉ so điểm có thể làm mất học viên có cùng điểm. Để đúng hợp đồng Set/Map về equality, nên nhất quán comparator với `equals`.
- `ArrayList`, `HashMap`, `HashSet` cho phép null (HashMap một khóa null và nhiều value null). `ArrayDeque`, `PriorityQueue`, `ConcurrentHashMap` và `List.of/Set.of/Map.of` không cho null. `TreeSet/TreeMap` thứ tự tự nhiên không dùng null; comparator tùy chỉnh có thể xác định thứ tự cho null.
- `List.copyOf` tạo danh sách không chỉnh sửa cấu trúc, không sao chép sâu các phần tử mutable. `Collections.unmodifiableList(original)` tạo view: nếu list gốc đổi, view có thể phản ánh thay đổi.
- `HashSet` đôi khi duyệt có vẻ ổn định nhưng API không cam kết. `LinkedHashSet` giữ insertion order; `TreeSet` sắp theo comparator.
- `PriorityQueue` chỉ đảm bảo **lấy ra bằng `poll`** theo ưu tiên; lặp với iterator không ra thứ tự ưu tiên. Cùng priority cần comparator có tiêu chí phụ như số thứ tự.
- Iterator fail-fast không phải công cụ đồng bộ hay bảo đảm phát hiện mọi race. Collection concurrent có thể dùng weakly consistent iterator; `CopyOnWriteArrayList` dùng snapshot iterator. Kiểm tra semantics trước khi chọn.

## 4. Tự lập bảng quyết định cho một chức năng

Mỗi hàng gắn với **một yêu cầu đo được**, thay vì chỉ ghi tên lớp:

| Câu hỏi | Dữ kiện ví dụ | Ứng viên | Quyết định |
|---|---|---|---|
| Giữ cả lần xuất hiện trùng và xem mục thứ i? | Lịch sử thao tác | `List` / `Set` | `ArrayList` |
| Loại trùng nhưng giữ lần đầu xuất hiện? | Thẻ bài viết | `HashSet` / `LinkedHashSet` | `LinkedHashSet` |
| Tra cứu theo mã hàng nghìn lần? | Mã SKU → sản phẩm | `ArrayList` / `HashMap` | `HashMap` |
| Truy vấn thời gian từ A tới B? | Các mốc được sắp | `HashSet` / `TreeSet` | `TreeSet` hoặc `TreeMap` |
| Lấy job đến trước trước? | FIFO đơn luồng | `ArrayDeque` / `PriorityQueue` | `ArrayDeque` |
| Mỗi lúc chỉ được 500 việc chờ, nhiều luồng? | Producer/consumer | `ConcurrentLinkedQueue` / `ArrayBlockingQueue` | `ArrayBlockingQueue<>(500)` |
| Lấy việc quan trọng nhất trước? | Ticket có priority | `ArrayDeque` / `PriorityQueue` | `PriorityQueue` + tie-break |
| Nhiều luồng tăng bộ đếm theo khóa? | Đếm page view | `HashMap` / `ConcurrentHashMap` | `ConcurrentHashMap` + `merge` |

Quyết định nên trình bày dạng: “Tôi dùng **X** vì cần **tính chất Y** và chủ yếu gọi **phép Z**; tôi chấp nhận **giới hạn W**”. Với dữ liệu lớn hoặc độ trễ quan trọng, thêm benchmark cho đường chạy thật. Đừng tối ưu chỉ theo Big-O.

## 5. Java 17: một bài toán, nhiều collection đúng mục đích

Dưới đây là chương trình đầy đủ. Tên file là `CollectionChoiceDemo.java`.

~~~java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;

public class CollectionChoiceDemo {
    record Job(String name, int priority, long sequence) {}

    public static void main(String[] args) {
        List<String> history = new ArrayList<>();
        history.add("đọc");
        history.add("viết");
        history.add("đọc");

        Set<String> unique = new LinkedHashSet<>(history);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String action : history) {
            counts.merge(action, 1, Integer::sum);
        }

        Deque<String> fifo = new ArrayDeque<>();
        fifo.addLast("A");
        fifo.addLast("B");

        Set<Integer> scores = new TreeSet<>(List.of(7, 9, 8, 7));

        Queue<Job> jobs = new PriorityQueue<>(
            Comparator.comparingInt(Job::priority)
                .reversed()
                .thenComparingLong(Job::sequence)
        );
        jobs.offer(new Job("normal", 2, 0));
        jobs.offer(new Job("urgent", 5, 1));
        jobs.offer(new Job("urgent2", 5, 2));

        System.out.println("Lịch sử: " + history);
        System.out.println("Duy nhất: " + unique);
        System.out.println("Số lần: " + counts);
        System.out.println("FIFO: " + fifo.removeFirst() + " -> " + fifo.removeFirst());
        System.out.println("Điểm tăng dần: " + scores);
        System.out.println("Ưu tiên: "
            + jobs.remove().name() + ", "
            + jobs.remove().name() + ", "
            + jobs.remove().name());
    }
}
~~~

Biên dịch và chạy với JDK 17+:

~~~bash
javac -encoding UTF-8 CollectionChoiceDemo.java
java CollectionChoiceDemo
~~~

Đầu ra chính xác:

~~~text
Lịch sử: [đọc, viết, đọc]
Duy nhất: [đọc, viết]
Số lần: {đọc=2, viết=1}
FIFO: A -> B
Điểm tăng dần: [7, 8, 9]
Ưu tiên: urgent, urgent2, normal
~~~

**Giải thích:** `ArrayList` giữ hai lần “đọc” trong lịch sử; `LinkedHashSet` khử trùng và giữ thứ tự gặp; `LinkedHashMap` cộng số lần và giữ thứ tự thêm khóa. `ArrayDeque` xử lý FIFO ở hai đầu; `TreeSet` khử trùng đồng thời sắp điểm; `PriorityQueue` lấy độ ưu tiên lớn trước. Hai job priority 5 được xếp bằng `sequence`; nếu comparator chỉ so priority thì không thể giả định job đến trước luôn lấy trước.

## 6. 12 tình huống tự đánh giá

**Cách làm:** Trước khi đọc đáp án mục 7, viết cho mỗi câu (a) interface/lớp, (b) tính chất quyết định, (c) một rủi ro hoặc giả định cần xác minh. Nhiều lời giải có thể đúng nếu giữ đủ hợp đồng.

1. **Nhật ký thao tác:** Giữ thứ tự thực hiện và nhiều lần lặp; giao diện cần lấy thao tác thứ i.
2. **Hashtag:** Loại trùng, hiển thị theo thứ tự xuất hiện lần đầu, không sắp alphabet.
3. **Kho sản phẩm:** Từ SKU tra sản phẩm nhanh; không yêu cầu thứ tự hiển thị; mỗi SKU một sản phẩm.
4. **Mốc lịch:** Sắp tăng theo thời gian và tìm các mốc trong khoảng từ A đến B.
5. **Undo:** Lấy thao tác mới nhất ra trước; chỉ một luồng, không cần tìm theo chỉ số.
6. **Xử lý ảnh:** Producer và consumer chạy khác luồng; tối đa 1000 tác vụ chờ; producer cần đợi khi đầy.
7. **Ticket hỗ trợ:** Priority cao xử lý trước, cùng priority theo thứ tự đến; không cần duyệt toàn hàng chờ theo thứ tự.
8. **Đếm lượt xem:** Nhiều luồng tăng bộ đếm cho cùng khóa; không được làm mất increment.
9. **Danh sách listener:** Nhiều luồng duyệt liên tục, rất ít ghi; mỗi lần duyệt muốn snapshot ổn định.
10. **Quyền người dùng:** Chỉ có các hằng enum `ADMIN/EDITOR/VIEWER`; một người có tập vai trò không trùng.
11. **Bảng top điểm:** Nhiều học viên có cùng điểm; hiển thị điểm giảm dần và giữ đủ học viên.
12. **Cache LRU:** Tra theo key; chỉ một luồng; khi vượt 100 mục thì bỏ mục lâu nhất **không được truy cập** (không phải mục được thêm cũ nhất).

Nếu tình huống chưa quy định null, mutation, kích thước hoặc concurrency, hãy ghi giả định. Không chấm một lớp là “sai” chỉ vì khác đáp án mẫu khi nó đáp ứng mọi ràng buộc.

## 7. Đáp án tham khảo và lý do

| Câu | Collection phù hợp | Giải thích / điều cần tránh |
|---|---|---|
| 1 | `List<Action>` bằng `ArrayList<>` | Giữ thứ tự và trùng; `get(i)` nhanh. `Set` sẽ xóa lịch sử lặp. |
| 2 | `LinkedHashSet<String>` | Không trùng và vẫn giữ thứ tự xuất hiện đầu tiên. `HashSet` không cam kết thứ tự. |
| 3 | `HashMap<String, Product>` | Tra theo khóa trung bình `O(1)`; key phải giữ `equals/hashCode` ổn định. |
| 4 | `TreeSet<Instant>` hoặc `TreeMap<Instant, List<Event>>` | Sorted và có API range. Nếu trùng mốc thời gian, phải giữ nhiều event thay vì ghi đè. |
| 5 | `Deque<Action>` bằng `ArrayDeque<>` | `push/pop` là LIFO; tránh dùng `Stack` cũ làm mặc định. |
| 6 | `BlockingQueue<Task>` bằng `ArrayBlockingQueue<>(1000)` | `put` đợi nếu đầy, `take` đợi nếu rỗng. Có kế hoạch kết thúc, hủy và xử lý interrupt. |
| 7 | `PriorityQueue<Ticket>` | Comparator priority giảm dần rồi sequence tăng dần. Không dùng iterator để hiện kết quả sorted. |
| 8 | `ConcurrentHashMap<Key, Long>` với `merge(key, 1L, Long::sum)` | Increment an toàn theo khóa; `put(k, get(k) + 1)` làm hai bước dễ race. Với contention lớn có thể cân nhắc `LongAdder`. |
| 9 | `CopyOnWriteArrayList<Listener>` | Snapshot iterator thích hợp với nhiều lần duyệt và ít cập nhật; ghi nhiều sẽ tốn sao chép. |
| 10 | `EnumSet<Role>` | Biểu diễn nhỏ gọn, nhanh và chỉ chứa hằng enum; không nhận null. |
| 11 | `List<Student>` được `sort` theo điểm giảm dần; hoặc `TreeMap<Integer, List<Student>>` dùng reverse order | Giữ mọi học viên cùng điểm. `TreeMap<Integer, Student>` sẽ ghi đè key điểm trùng. |
| 12 | `LinkedHashMap<K,V>` access-order, override `removeEldestEntry` | Truy cập gần nhất phải đổi vị trí; dùng map đơn luồng hoặc có đồng bộ hóa thích hợp. |

Ví dụ Java 17 độc lập minh họa câu 12 (giới hạn 2 cho dễ xem; đổi thành 100 khi áp dụng đề):

~~~java
import java.util.LinkedHashMap;
import java.util.Map;

public class LruExample {
    public static void main(String[] args) {
        Map<String, Integer> cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
                return size() > 2;
            }
        };
        cache.put("A", 1);
        cache.put("B", 2);
        cache.get("A");
        cache.put("C", 3);
        System.out.println(cache);
    }
}
~~~

Chạy `javac -encoding UTF-8 LruExample.java` rồi `java LruExample` cho `{A=1, C=3}`. “B” bị loại vì “A” được truy cập sau. `LinkedHashMap` access-order có thể cập nhật thứ tự ngay trong `get`, nên không tùy tiện chia sẻ nó giữa nhiều luồng mà không có cơ chế đồng bộ.

## 8. Thang điểm và phiếu tự chấm

Mỗi tình huống **tối đa 2 điểm**, tổng **24**:

- **1 điểm chọn đúng:** lớp/interface thỏa toàn bộ yêu cầu nêu rõ. Chấp nhận giải pháp khác đáp án nếu có lập luận đúng; chọn loại mất trùng, mất thứ tự, mất cập nhật hoặc sai concurrency là 0 điểm phần này.
- **1 điểm giải thích:** nói rõ một đặc tính cốt lõi *và* một rủi ro/điều kiện (ví dụ hash contract, comparator, capacity, atomicity, snapshot, thứ tự). Chỉ trả lời tên lớp không đủ điểm giải thích.

| Tổng /24 | Mức nắm vững | Bài tập tiếp theo |
|---|---|---|
| 21–24 | Quyết định và lý do vững | Thử dữ liệu lớn và nhiều luồng, so đo thực tế |
| 17–20 | Phần lớn đúng | Giải thích lại các rủi ro ở câu sai |
| 12–16 | Biết họ collection nhưng còn nhầm hợp đồng | Ôn thứ tự vs sắp xếp, Set/Map/Queue |
| 0–11 | Chưa chọn theo yêu cầu | Làm lại sáu câu hỏi mục 1 trước mỗi tình huống |

**Phiếu tự đánh giá để tự điền:**

| Câu | Lớp đã chọn | Tính chất quyết định | Rủi ro/giả định | Điểm /2 |
|---|---|---|---|---|
| 1, 2, 3, 4 | Ghi riêng từng câu | Trùng/thứ tự/key/range | Equality, comparator | ... |
| 5, 6, 7, 8 | Ghi riêng từng câu | LIFO/bounded blocking/priority/concurrent | Null, đóng queue, tie-break, atomicity | ... |
| 9, 10, 11, 12 | Ghi riêng từng câu | Snapshot/enum/giữ trùng điểm/LRU | Chi phí ghi, key trùng, đồng bộ | ... |

Khi sai một câu, viết lại theo mẫu: “Tôi cần ___ vì yêu cầu ___; tôi không chọn ___ vì sẽ vi phạm ___”. Sau đó **đổi một điều kiện** trong đề: nếu danh sách cần sắp xếp thay vì insertion order thì chọn gì? Nếu thêm nhiều luồng thì cấu trúc/đồng bộ thay thế ra sao? Nếu consumer chậm thì có giới hạn queue không? Đây là cách kiểm tra mình hiểu cơ chế, thay vì học thuộc đáp án.

## 9. Các lỗi phổ biến khi áp dụng

1. **Nhầm insertion order và sorted order:** `LinkedHashSet` giữ thứ tự chèn, `TreeSet` sắp theo comparator; `PriorityQueue` chỉ lấy đầu theo ưu tiên.
2. **Dùng `HashSet`/`HashMap` rồi dựa vào thứ tự duyệt “trông có vẻ ổn định”:** API không cam kết.
3. **Đưa khóa mutable vào hash collection và sửa trường tham gia hash:** lần `get/contains` sau có thể thất bại.
4. **Dùng comparator chỉ so một thuộc tính khi các đối tượng vẫn cần cùng tồn tại:** `TreeSet` có thể bỏ phần tử; `TreeMap` có thể ghi đè value.
5. **Nhìn `map.get(key) == null` rồi kết luận khóa vắng:** Với `HashMap`, key có thể tồn tại nhưng value null; cần `containsKey` nếu phân biệt hai trường hợp.
6. **Nghĩ `Collections.synchronizedList` biến cả chuỗi thao tác thành nguyên tử:** `contains` rồi `add`, hoặc iteration, có thể cần đồng bộ xung quanh toàn bộ đoạn.
7. **Dùng `HashMap` cho tăng đếm cạnh tranh vì test đơn luồng vẫn đúng:** thao tác đọc–sửa–ghi không nguyên tử; dùng API cập nhật atomic thích hợp.
8. **Dùng `CopyOnWriteArrayList` cho workload ghi liên tục:** mỗi lần ghi sao chép mảng, thường tốn kém.
9. **Dùng `PriorityQueue` như một danh sách được sắp có thể duyệt trực tiếp:** iterator không hứa thứ tự; hãy poll một bản sao hoặc sort một list.
10. **Nhầm unmodifiable với immutable sâu:** không thêm/xóa được từ `List.copyOf`, nhưng đối tượng mutable bên trong vẫn có thể thay đổi.

## 10. Checklist cuối cho mỗi quyết định

- [ ] Đã xác định giữ trùng, key lookup, index, thứ tự chèn, sắp xếp, FIFO/LIFO hay priority?
- [ ] Đã nêu phép truy cập thường gặp và Big-O của **phép đó**, không chỉ của lớp?
- [ ] Đã kiểm tra `equals/hashCode`, `Comparator`, null, khóa mutable và các giá trị trùng?
- [ ] Đã phân biệt thread-safe, thao tác nguyên tử và queue có backpressure?
- [ ] Đã kiểm tra chi phí ghi, bộ nhớ và yêu cầu giữ snapshot?
- [ ] Đã khai báo biến bằng interface phù hợp (`List`/`Set`/`Map`/`Queue`/`Deque`) khi có thể và kiểm tra ví dụ nhỏ?

Một quyết định tốt luôn gắn **yêu cầu → cấu trúc → lý do → giới hạn**. Bảng quyết định và điểm tự chấm giúp bạn giải thích lựa chọn, kể cả khi cần đổi implementation vì yêu cầu mới.

</details>

- [Quay lại đầu trang](#back-to-top)
