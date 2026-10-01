# Tác dụng phụ, ngoại lệ, đánh đổi và cách lựa chọn

## <a id="side-effects-in-lambdas">Tác dụng phụ bên trong lambda</a>

Lambda chỉ là cú pháp để thực hiện hợp đồng của một giao diện hàm. Java không bắt buộc phần cài đặt đó phải thuần túy. Lambda có thể thay đổi đối tượng, ghi tệp, cập nhật cơ sở dữ liệu, ghi nhật ký hoặc gọi dịch vụ bên ngoài.

```java
Consumer<Order> publish = order -> eventBus.publish(order);
```

Điều đó có thể hoàn toàn đúng với yêu cầu. Vấn đề xuất hiện khi một lambda *trông giống* phép biến đổi hoặc điều kiện đơn giản nhưng lại thực hiện công việc ẩn mà thời điểm hay số lần chạy có ý nghĩa.

Một quy tắc hữu ích là để tác dụng phụ lộ rõ qua tên và vị trí. Nếu hành vi làm thay đổi trạng thái bên ngoài, người đọc nên nhận ra điều đó mà không phải mở từng lambda để kiểm tra.

## <a id="hidden-mutation-state">Thay đổi trạng thái bị che giấu</a>

Quy tắc không bị gán lại sau khi khởi tạo (effectively final) giới hạn việc gán lại biến cục bộ được bắt giữ. Nó không làm đối tượng mà biến tham chiếu tới trở thành bất biến.

```java
List<String> seen = new ArrayList<>();
Consumer<String> remember = value -> seen.add(value);
```

`seen` không bị gán lại nên có thể được lambda bắt giữ. Tuy nhiên, chính `ArrayList` vẫn bị thay đổi mỗi lần `remember` chạy.

Khác biệt này quan trọng vì trạng thái có thể thay đổi bị bắt giữ tạo ra phụ thuộc khó nhìn thấy. Kết quả có thể phụ thuộc vào các lần gọi trước, thứ tự thực thi hoặc truy cập đồng thời. Nếu hành vi cần giữ trạng thái, hãy thiết kế rõ ai sở hữu trạng thái đó thay vì cho rằng dùng lambda sẽ làm nó an toàn hơn.

## <a id="checked-exception-constraint">Ngoại lệ được kiểm tra và kiểu hàm đích</a>

Lambda hoặc tham chiếu phương thức phải tuân theo hợp đồng `throws` của kiểu hàm đích. Các giao diện chuẩn như `Function<T, R>` không khai báo tùy ý các ngoại lệ được kiểm tra (checked exception).

```java
Function<Path, String> reader = path -> Files.readString(path); // không biên dịch
```

`Files.readString` có thể ném `IOException`, nhưng `Function.apply` không khai báo `IOException`. Vì vậy lambda không thể đơn giản truyền ngoại lệ được kiểm tra đó ra ngoài qua kiểu đích này.

Đây là vấn đề của hợp đồng kiểu, không phải hạn chế riêng của cú pháp lambda. Một lớp có tên cài đặt cùng `Function` cũng gặp đúng ràng buộc đó.

## <a id="checked-exception-adaptation">Xử lý rõ ràng ngoại lệ được kiểm tra khi kiểu hàm không cho phép</a>

Khi mã theo phong cách hàm gặp ngoại lệ được kiểm tra, nên chọn cách xử lý phù hợp với ranh giới API thay vì che lỗi bằng một thủ thuật chung.

Một lựa chọn là chuyển ngoại lệ được kiểm tra thành ngoại lệ không được kiểm tra phù hợp:

```java
Function<Path, String> reader = path -> {
    try {
        return Files.readString(path);
    } catch (IOException ex) {
        throw new UncheckedIOException(ex);
    }
};
```

Một lựa chọn khác là tạo giao diện hàm theo miền sử dụng và cho phép hợp đồng khai báo ngoại lệ được kiểm tra:

```java
@FunctionalInterface
interface IOFunction<T, R> {
    R apply(T value) throws IOException;
}
```

Cũng có thể giữ thao tác trong phương thức thông thường nếu cách xử lý ngoại lệ ở đó rõ ràng hơn. Nên tránh các hàm hỗ trợ dùng thủ thuật ném lén ngoại lệ ("sneaky throw") vì chúng che khuất ngoại lệ được kiểm tra; lúc đó kiểu khai báo cho người gọi thấy một mô hình lỗi khác với hành vi thật sự.

## <a id="debugging-composed-behavior">Gỡ lỗi hành vi được kết hợp</a>

Kết hợp hành vi giúp giảm lặp mã, nhưng chuỗi dài có thể làm khó xác định bước nào tạo dữ liệu sai hoặc ném ngoại lệ. Trình gỡ lỗi (debugger) và dấu vết ngăn xếp (stack trace) cũng thường cung cấp ít ngữ cảnh hơn nếu mọi thao tác đều là lambda vô danh.

Nên đặt tên cho các hành vi quan trọng khi việc đó giúp điều tra lỗi:

```java
Function<Request, Request> validate = this::validateRequest;
Function<Request, Command> toCommand = this::mapToCommand;

Function<Request, Command> prepare = validate.andThen(toCommand);
```

Các hàm có tên tạo điểm dừng (breakpoint) rõ ràng và cung cấp từ vựng cho mã. Khi gỡ lỗi, tách một chuỗi lớn thành biến trung gian thường dễ theo dõi hơn việc nhét ghi nhật ký vào mọi lambda.

Ngắn hơn không đồng nghĩa với dễ gỡ lỗi hơn. Cần giữ đủ cấu trúc để người đọc cô lập được từng bước có ý nghĩa.

## <a id="readability-vs-chaining">Khả năng đọc và chuỗi xử lý quá dài</a>

API kiểu hàm khiến việc nối nhiều thao tác trở nên dễ dàng, nhưng một chuỗi chỉ dễ đọc khi người đọc vẫn theo được hình dạng dữ liệu, cách lỗi xảy ra và mục đích của từng bước.

Một số dấu hiệu nên dừng và tách chuỗi:

- nhiều trách nhiệm không liên quan bị đặt trong cùng một chuỗi;
- lambda lồng nhau khiến khó theo dõi biến nào là biến nào;
- liên tục chuyển đổi lớp bọc chỉ để giữ được cách viết chuỗi lời gọi;
- tác dụng phụ nằm trong thao tác trông giống phép biến đổi;
- tên các bước không thể hiện ý nghĩa nghiệp vụ.

Hãy tách thành phương thức có tên, thêm biến trung gian hoặc dùng luồng điều khiển thông thường nếu điều đó làm logic dễ kiểm tra hơn. Ít dòng hơn không tự động có nghĩa là mã đơn giản hơn.

## <a id="functional-vs-imperative-choice">Chọn phong cách hàm hay cách viết mệnh lệnh?</a>

Phong cách hàm phù hợp khi bài toán có thể biểu diễn rõ bằng các giá trị hành vi nhỏ, phép biến đổi, điều kiện hoặc chính sách có thể ghép với nhau.

Cách viết mệnh lệnh có thể rõ hơn khi câu chuyện chính là một chuỗi thay đổi trạng thái: bộ đếm thử lại, vòng đời tài nguyên, máy trạng thái, nhiều nhánh thoát sớm hoặc quy trình mà mỗi bước phụ thuộc vào thay đổi của bước trước.

```java
for (Task task : tasks) {
    if (!task.ready()) {
        continue;
    }
    execute(task);
    completed++;
}
```

Không có yêu cầu nào buộc phải chuyển đoạn mã như vậy thành chuỗi lambda. Nên chọn cách thể hiện làm luồng điều khiển và các bất biến của bài toán lộ rõ nhất.

## <a id="functional-vs-oop-choice">Kết hợp hành vi hay tổ chức bằng hướng đối tượng?</a>

Truyền một giao diện hàm phù hợp khi phần thay đổi chủ yếu là **một hành vi**: quy tắc so sánh, phép biến đổi, điều kiện kiểm tra, hàm gọi lại hoặc chiến lược có rất ít trạng thái độc lập.

Một khái niệm trừu tượng hướng đối tượng thường rõ hơn khi khái niệm có định danh, nhiều thao tác liên quan, vòng đời hoặc trạng thái đáng kể.

```java
Comparator<Order> ordering = Comparator.comparing(Order::createdAt);
```

Một hành vi đơn lẻ như trên rất hợp với hợp đồng dạng hàm. Ngược lại, một nhà cung cấp thanh toán có các thao tác ủy quyền (authorize), ghi nhận thanh toán (capture), hoàn tiền (refund), thông tin xác thực (credentials) và quy tắc vòng đời thường phù hợp hơn với một hợp đồng đối tượng giàu hành vi.

Java cho phép hai phong cách phối hợp: giao diện hàm vẫn là giao diện, việc đánh giá lambda cho ra một giá trị tham chiếu tới thực thể của kiểu giao diện hàm đích, và mã hướng đối tượng vẫn có thể nhận hành vi làm tham số khi điều đó tăng tính linh hoạt. Java không bảo đảm mỗi lần đánh giá đều cấp phát một đối tượng lambda mới, nên không được suy ra lớp hiện thực hay danh tính đối tượng ổn định từ cơ chế này.

## <a id="purity-immutability-boundary">Hàm thuần túy và tính bất biến: ranh giới với mô hình lập trình hàm</a>

Hàm thuần túy, tính trong suốt tham chiếu, tính bất biến như nguyên tắc thiết kế chung và các đánh đổi không phụ thuộc ngôn ngữ thuộc mô-đun Mô hình lập trình hàm.

Ở Java Core, ranh giới cần nhớ đơn giản hơn: **cú pháp lambda không đảm bảo tính thuần túy hay bất biến**. Lambda Java có thể đọc trường có thể thay đổi, thay đổi đối tượng được bắt giữ, thực hiện I/O hoặc gọi phương thức có tác dụng phụ. Ngược lại, một phương thức Java có tên bình thường vẫn có thể là hàm thuần túy dù không dùng lambda.

Khi đọc mã, cần tách hai lớp kiến thức đó. Các cơ chế trong mô-đun này giúp hành vi có thể được truyền và kết hợp như giá trị; chúng không tự động mang lại các thuộc tính ngữ nghĩa thuộc mô hình lập trình hàm.

## <a id="common-functional-pitfalls">Các lỗi thường gặp khi viết Java theo phong cách hàm</a>

Nhiều lỗi lặp lại xuất phát từ việc đồng nhất cú pháp ngắn với thiết kế tốt:

- cho rằng mọi lambda đều thuần túy hoặc an toàn luồng;
- thay đổi đối tượng được bắt giữ rồi quên rằng các lần gọi sau cùng chia sẻ trạng thái đó;
- chọn sai thứ tự `compose`/`andThen` dù kiểu dữ liệu vẫn biên dịch;
- ép dùng tham chiếu phương thức khi lambda thể hiện ánh xạ đối số rõ hơn;
- che việc chuyển ngoại lệ được kiểm tra trong hàm hỗ trợ dùng chung khó nhìn thấy;
- gọi `Optional.get()` hoặc dùng `Optional` khắp nơi mà không có hợp đồng vắng mặt rõ ràng;
- tạo chuỗi dài đến mức khó gỡ lỗi và mất ý nghĩa nghiệp vụ;
- cho rằng biết lambda là đủ để suy ra cơ chế thực thi của Stream.

Cách sửa chung không phải là "tránh phong cách hàm", mà là giữ hợp đồng kiểu, trạng thái, tác dụng phụ, thứ tự thực thi và ranh giới mô-đun thật rõ ràng.

## <a id="functional-programming-synthesis">Tổng hợp mô hình tư duy về lập trình hàm trong Java</a>

Mô hình tư duy đặc thù Java có thể nối lại thành một luồng duy nhất:

```text
hành vi thay đổi
    -> hợp đồng giao diện hàm
    -> lambda hoặc tham chiếu phương thức/hàm tạo
    -> kiểu đích kiểm tra tham số và kết quả
    -> có thể bắt giữ giá trị xung quanh
    -> truyền, trả về hoặc kết hợp hành vi
    -> gọi qua phương thức của giao diện hàm
    -> giữ rõ trạng thái, tác dụng phụ, ngoại lệ và khả năng đọc
```

Các giao diện chuẩn trong `java.util.function` cung cấp hợp đồng tái sử dụng; giao diện hàm tự định nghĩa phù hợp khi miền nghiệp vụ hoặc quy tắc ngoại lệ cần hợp đồng khác. `Optional` áp dụng cùng tinh thần đó cho sự vắng mặt tại một số ranh giới API. Stream API tái sử dụng các cơ chế này bằng cách nhận giá trị giao diện hàm, còn ngữ nghĩa xử lý riêng của Stream thuộc mô-đun Stream API.

Nên dùng phong cách hàm trong Java khi việc tham số hóa và kết hợp hành vi giảm lặp mã và làm ý định rõ hơn. Nên dùng cấu trúc mệnh lệnh hoặc hướng đối tượng khi quy trình có trạng thái, trách nhiệm đối tượng phong phú hoặc nhu cầu gỡ lỗi khiến chúng phù hợp hơn. Mục tiêu không phải là có thật nhiều lambda, mà là để hành vi và các ràng buộc của mã luôn dễ hiểu.
