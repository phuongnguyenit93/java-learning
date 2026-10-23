# Biểu diễn văn bản với toString

Sau `equals` và `hashCode`, ta biết Java nhận diện và tra cứu đối tượng như thế nào. Nhưng khi chương trình gặp lỗi và cần quan sát một đối tượng, chuỗi `com.example.UserId@5e2de80c` thường chưa giúp ta hiểu nó đang chứa giá trị gì. Đó là lúc `toString()` trở nên hữu ích.

`toString` là phương thức tạo **biểu diễn văn bản dành cho con người**. Kết quả của nó thường xuất hiện trong log, trình gỡ lỗi, thông báo kiểm thử và chuỗi được tạo tạm thời. Quy ước này độc lập với việc hai đối tượng có bằng nhau theo `equals` hoặc có cùng `hashCode` hay không.

## <a id="tostring-purpose">Mục đích của toString</a>

Mọi lớp Java đều thừa hưởng `public String toString()` từ `Object` (trực tiếp hoặc gián tiếp), trừ khi lớp hay lớp cha đã ghi đè. Phương thức này trả về một chuỗi mô tả đối tượng, giúp con người nhận biết trạng thái đáng chú ý khi xem log, trình gỡ lỗi hoặc thông báo kiểm thử. Nó không quyết định hai đối tượng có bằng nhau theo `equals` hay không.

### Biểu diễn mặc định và biểu diễn ghi đè

Định nghĩa `Object.toString()` mặc định ghép tên lớp lúc chạy, dấu `@` và giá trị `hashCode()` ở hệ 16:

```java
return getClass().getName() + "@" + Integer.toHexString(hashCode());
```

Chẳng hạn, kết quả có thể là `com.example.UserId@5e2de80c`. Nó cho biết kiểu của đối tượng và một mã băm, nhưng **không cho biết giá trị nghiệp vụ**. Mã băm có thể trùng giữa các đối tượng và không phải mã định danh duy nhất, bền vững qua các lần chạy. Nếu lớp ghi đè `hashCode()` nhưng giữ `Object.toString()`, phần sau dấu `@` phản ánh cách tính mã băm mới.

Hãy đặt hai cách biểu diễn cạnh nhau. Lưu đoạn sau vào `ToStringDemo.java`, rồi chạy với Java 21 bằng `javac ToStringDemo.java` và `java ToStringDemo`:

```java
public class ToStringDemo {
    static final class RawUserId {
        private final String value;

        RawUserId(String value) {
            this.value = value;
        }
        // Không ghi đè toString(): dùng bản kế thừa từ Object.
    }

    static final class UserId {
        private final String value;

        UserId(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return "UserId[value=" + value + "]";
        }
    }

    public static void main(String[] args) {
        RawUserId raw = new RawUserId("U-100");
        UserId readable = new UserId("U-100");

        System.out.println(raw);
        System.out.println(readable);
        System.out.println("Đang xử lý " + readable);
    }
}
```

Kết quả minh họa (phần mã băm ở dòng đầu có thể khác):

```text
ToStringDemo$RawUserId@7adf9f5f
UserId[value=U-100]
Đang xử lý UserId[value=U-100]
```

Hai đối tượng đều chứa giá trị `U-100`. Tuy nhiên, `RawUserId` chỉ trả về tên lớp và mã băm vì không ghi đè phương thức, còn `UserId` tự chọn cách hiển thị trường `value`. `@Override` giúp trình biên dịch xác nhận ta đang ghi đè đúng phương thức. Ở đây, giả định mã `U-100` được phép xuất hiện trong thông tin chẩn đoán; việc chọn trường nào để in vẫn phải phụ thuộc độ nhạy cảm của dữ liệu.

### Vì sao đây là một quy ước?

Tài liệu `Object` khuyến nghị `toString` trả về văn bản ngắn gọn, giàu thông tin và dễ đọc. JDK không áp đặt một định dạng nghiệp vụ chung: mỗi lớp quyết định **trạng thái nào đáng cho người đọc biết**. Các công cụ và đoạn mã sử dụng phương thức này dựa vào kỳ vọng mô tả đó.

Trong ví dụ trên, cả `System.out.println(readable)` lẫn phép nối chuỗi `"Đang xử lý " + readable` đều sử dụng biểu diễn của `readable` mà không cần viết `readable.toString()`. Các thư viện ghi log cũng có thể định dạng tham số đối tượng thành văn bản, nhưng thời điểm và cách thực hiện tùy API/logger cụ thể. Vì thế một lệnh log tưởng như đơn giản có thể làm nội dung `toString` xuất hiện trong log.

Đọc đến đây, bạn cần phân biệt được hai câu hỏi: **`equals` nói hai đối tượng có cùng giá trị logic hay không; `toString` nói con người sẽ nhìn thấy mô tả gì về một đối tượng**. Việc thay cách in không tự thay quy tắc bằng nhau hoặc mã băm.

## <a id="tostring-design">Thiết kế biểu diễn hữu ích</a>

Khi kiểm tra một bản ghi sách, câu hỏi thường là “mã nào, tiêu đề nào?” chứ không phải “toàn bộ trường, ghi chú và mọi đối tượng liên quan gồm những gì?”. Thiết kế `toString` bắt đầu từ **nhu cầu chẩn đoán**, sau đó mới chọn các trường cần hiển thị.

### Chọn những trường đủ để hiểu tình huống

Ví dụ với một cuốn sách, người vận hành chỉ cần mã sách và tiêu đề để nhận biết bản ghi. Nội dung ghi chú nội bộ không cần đi theo mỗi lần in đối tượng. Lưu đoạn sau vào `BookLogDemo.java` rồi chạy bằng `javac BookLogDemo.java` và `java BookLogDemo`:

```java
public class BookLogDemo {
    static final class Book {
        private final String id;
        private final String title;
        private final String internalNote;

        Book(String id, String title, String internalNote) {
            this.id = id;
            this.title = title;
            this.internalNote = internalNote;
        }

        @Override
        public String toString() {
            return "Book[id=" + id + ", title=" + title + "]";
        }
    }

    public static void main(String[] args) {
        Book book = new Book("B-7", "Java 21", "Chờ kiểm kê");
        System.out.println("Kiểm tra: " + book);
    }
}
```

```text
Kiểm tra: Book[id=B-7, title=Java 21]
```

Trong `main`, ba giá trị đều được đưa vào đối tượng `Book`. Nhưng lúc nối `"Kiểm tra: " + book`, Java lấy chuỗi do `Book.toString()` trả về; phương thức chỉ đọc `id` và `title`. Ghi chú `internalNote` không được đưa vào kết quả. Bằng cách đó, người đọc vẫn nhận ra cuốn sách mà không phải xem thêm dữ liệu ít liên quan. Nếu ghi chú cũng nhạy cảm, việc bỏ qua nó còn giúp giảm rủi ro phơi bày.

Một `toString` thực dụng thường ngắn gọn, kết quả dễ dự đoán khi trạng thái không đổi, không có tác dụng phụ và đủ nhẹ để dùng lúc chẩn đoán lỗi. Đặc biệt, tránh I/O mạng, truy vấn cơ sở dữ liệu, đọc tệp hoặc tính toán nặng bên trong phương thức. Khi log lỗi đang diễn ra, một `toString` ném thêm ngoại lệ còn có thể che khuất nguyên nhân ban đầu. Đây là **nguyên tắc thiết kế** chứ không phải ràng buộc được trình biên dịch Java kiểm tra.

### Tránh in cả đồ thị đối tượng

Đối tượng có thể trỏ đến nhiều đối tượng khác. Nếu `Parent.toString()` in toàn bộ `Child`, còn `Child.toString()` lại in toàn bộ `Parent`, đường gọi sẽ lặp:

```text
Parent.toString()
  → Child.toString()
      → Parent.toString()
          → ... (tiếp tục lặp)
```

Kết quả có thể là `StackOverflowError`, một chuỗi quá lớn hoặc log rất khó đọc. Nếu cần thể hiện quan hệ, hãy ưu tiên in **mã định danh của đối tượng liên quan** thay vì nối toàn bộ đối tượng đó vào chuỗi.

### Phân biệt văn bản chẩn đoán với tuần tự hóa

Chuỗi `Book[id=B-7, title=Java 21]` giúp con người đọc log. Nó **không có lược đồ dữ liệu bắt buộc**, quy tắc escape, định dạng tương thích phiên bản hay cam kết để chương trình khác đọc ngược thành `Book`. Nếu sau này nhóm phát triển đổi cách in thành `Book(B-7 — Java 21)`, nhu cầu chẩn đoán có thể vẫn được đáp ứng nhưng mã cố phân tích chuỗi cũ sẽ hỏng.

Khi cần truyền hoặc lưu dữ liệu theo hợp đồng có thể đọc bởi máy, hãy dùng cơ chế phù hợp như JSON, protobuf hoặc bộ tuần tự hóa chuyên dụng với lược đồ và quy tắc tương thích đã chọn. Chỉ dựa vào `toString` như một định dạng ổn định nếu chính API cụ thể **công bố rõ** cam kết ấy; đừng suy ra điều đó từ phương thức `Object.toString()`.

Record trong Java tự tạo `toString` dựa trên các thành phần (`component`), rất tiện để quan sát giá trị. Nhưng tự động in tất cả thành phần không đồng nghĩa với an toàn để đưa vào log. Vấn đề này sẽ rõ hơn ở phần tiếp theo.

## <a id="tostring-sensitive-data">Dữ liệu nhạy cảm và ghi log</a>

Một đối tượng có thể mang mật khẩu, access token, thông tin thanh toán hoặc dữ liệu cá nhân. Khi `toString` đưa các giá trị đó vào chuỗi, chúng có thể đi vào log, thông báo ngoại lệ, lỗi kiểm thử hoặc công cụ quan sát ngay cả khi lập trình viên không gọi trực tiếp `.toString()`.

### Quan sát một lần lộ thông tin và cách sửa

Giả sử ứng dụng cần biết yêu cầu `login` đã được xử lý, nhưng **không được ghi lại token**. Hai record dưới đây chứa cùng dữ liệu mẫu. Record đầu giữ `toString` tự sinh, record thứ hai tự ghi đè để che token. Lưu vào `SafeToStringDemo.java` rồi chạy với Java 21:

```java
public class SafeToStringDemo {
    record UnsafeLoginRequest(String operation, String accessToken) {}

    record LoginRequest(String operation, String accessToken) {
        @Override
        public String toString() {
            return "LoginRequest[operation=" + operation
                    + ", accessToken=<redacted>]";
        }
    }

    public static void main(String[] args) {
        var unsafe = new UnsafeLoginRequest("login", "demo-only-token");
        var safe = new LoginRequest("login", "demo-only-token");

        System.out.println(unsafe);
        System.out.println(safe);
    }
}
```

```text
UnsafeLoginRequest[operation=login, accessToken=demo-only-token]
LoginRequest[operation=login, accessToken=<redacted>]
```

Ở dòng đầu, Java tạo biểu diễn record từ **cả hai** thành phần nên token mẫu xuất hiện nguyên vẹn. Ở dòng hai, phương thức ghi đè chỉ in thao tác `operation` và chuỗi cố định `<redacted>`; giá trị thật của `accessToken` không được nối vào kết quả. Thay token đầu vào bằng một chuỗi khác sẽ không làm lộ token mới qua `LoginRequest.toString()`.

### Vì sao cần quyết định từ lúc thiết kế lớp?

Khi một log in ra đối tượng, khi phép kiểm tra thất bại kèm giá trị thực tế, hoặc khi một danh sách chứa đối tượng được đưa thành chuỗi, biểu diễn của các phần tử có thể được sử dụng gián tiếp. Vì vậy **không có lời gọi `.toString()` trong mã đang xem** không chứng minh rằng dữ liệu sẽ không bị đưa ra ngoài.

Nguyên tắc thực hành là chỉ đưa những trường **thực sự cần thiết và được phép công bố trong bối cảnh chẩn đoán** vào `toString`. Có thể lược bỏ hẳn hoặc che cố định trường bí mật; với dữ liệu nhận diện, chỉ in một phần đã được chính sách cho phép. Ví dụ, thẻ thanh toán có thể chỉ hiện bốn số cuối **nếu** chính sách ghi log cho phép, chứ không in số thẻ đầy đủ.

Lưu ý ranh giới: một `toString` đã che token chỉ bảo vệ **đường biểu diễn thông qua phương thức đó**. Mã khác vẫn có thể log riêng trường `accessToken`, hoặc một bộ tuần tự hóa có thể truy cập trực tiếp dữ liệu. Vì thế chính sách logging và kiểm soát dữ liệu nhạy cảm vẫn cần được áp dụng ở những nơi sử dụng dữ liệu.

Một `toString` tốt trả lời được “đối tượng nào, trạng thái nào liên quan đến vấn đề?” mà không đưa thông tin bí mật vào chuỗi. Đó là cách giữ cho biểu diễn hữu ích khi gỡ lỗi, đồng thời không nhầm `toString` với một cơ chế bảo mật hoặc định dạng lưu trữ dữ liệu.

Sau cách đối tượng tự mô tả, hai chương tiếp theo chuyển sang một nhóm quy ước khác: **thứ tự sắp xếp** — đối tượng nào đứng trước đối tượng nào, và ai sở hữu chính sách đó.
