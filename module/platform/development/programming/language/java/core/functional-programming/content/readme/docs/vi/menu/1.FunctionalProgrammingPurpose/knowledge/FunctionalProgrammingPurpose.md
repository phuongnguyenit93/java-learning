# Lập trình hàm trong Java là gì và vì sao cần?

## <a id="functional-programming-purpose">Lập trình hàm trong Java là gì?</a>

Trong Java, lập trình hàm là cách viết một phần chương trình theo hướng **biểu diễn hành vi thành giá trị có kiểu**, để hành vi đó có thể được truyền vào, lựa chọn, kết hợp hoặc thực thi sau. Java không tạo ra một hệ kiểu hàm tách biệt khỏi hệ kiểu thông thường. Thay vào đó, hành vi được biểu diễn thông qua **giao diện hàm (functional interface)**, còn lambda hoặc tham chiếu phương thức cung cấp phần cài đặt cho hợp đồng hàm đó.

Điểm thay đổi quan trọng là chương trình không chỉ truyền dữ liệu, mà còn có thể truyền **cách xử lý dữ liệu**.

```java
boolean isAdult(int age) {
    return age >= 18;
}

Predicate<Integer> rule = age -> age >= 18;
```

Đoạn đầu là một phương thức thông thường. Đoạn sau đóng gói hành vi tương đương phía sau hợp đồng `Predicate<Integer>`, nhờ vậy một API khác có thể nhận hành vi này như một giá trị.

Java vẫn là ngôn ngữ đa mô hình. Phong cách hàm bổ sung cho cách viết hướng đối tượng và mệnh lệnh; nó đặc biệt hữu ích khi phần cần thay đổi chính là **hành vi**.

## <a id="hard-coded-behavior-problem">Vấn đề của hành vi bị gắn cứng</a>

Giả sử một phương thức tự chứa toàn bộ quy tắc lựa chọn bên trong luồng điều khiển:

```java
List<String> selectNames(List<String> names, String mode) {
    List<String> result = new ArrayList<>();

    for (String name : names) {
        if (mode.equals("LONG") && name.length() >= 5) {
            result.add(name);
        } else if (mode.equals("STARTS_WITH_A") && name.startsWith("A")) {
            result.add(name);
        }
    }

    return result;
}
```

Ở đây phần duyệt dữ liệu và quy tắc chọn dữ liệu bị trộn vào nhau. Mỗi quy tắc mới lại buộc ta sửa phương thức, thêm nhánh điều kiện và kiểm thử lại một luồng vốn không thay đổi.

Cơ chế lập trình hàm cho phép tách hai trách nhiệm:

- phương thức quyết định **khi nào và ở đâu** áp dụng quy tắc;
- bên gọi cung cấp **quy tắc nào** cần chạy.

Điều này không có nghĩa mọi câu `if` đều nên biến thành lambda. Tham số hóa hành vi có giá trị khi một thao tác thật sự có khả năng thay đổi hoặc cần tái sử dụng.

## <a id="behavior-before-lambdas">Trước lambda: lớp vô danh để đóng gói hành vi</a>

Trước khi có lambda, Java vẫn truyền được hành vi bằng cách tạo lớp vô danh triển khai một giao diện:

```java
Comparator<String> byLength = new Comparator<String>() {
    @Override
    public int compare(String left, String right) {
        return Integer.compare(left.length(), right.length());
    }
};
```

Cách này hợp lệ vì đối tượng vô danh có kiểu Java bình thường và phương thức `compare` chứa hành vi cần truyền. Điểm bất tiện là cú pháp khá dài: tạo đối tượng, ghi đè phương thức, lặp lại kiểu tham số rồi mới tới phần logic thật sự.

Lambda không phát minh ra việc truyền hành vi. Nó cung cấp cú pháp ngắn gọn hơn khi kiểu đích là một giao diện hàm:

```java
Comparator<String> byLength =
        (left, right) -> Integer.compare(left.length(), right.length());
```

Vì vậy không nên hiểu lambda là một "phương thức không tên" tồn tại độc lập với hệ kiểu Java. Lambda vẫn cần một giao diện hàm làm kiểu đích.

## <a id="behavior-parameterization">Truyền hành vi như một tham số</a>

Khi hành vi có một kiểu rõ ràng, phương thức có thể nhận hành vi đó như tham số:

```java
List<String> select(
        List<String> names,
        Predicate<String> rule) {

    List<String> result = new ArrayList<>();
    for (String name : names) {
        if (rule.test(name)) {
            result.add(name);
        }
    }
    return result;
}
```

Bên gọi quyết định biến thể xử lý mà không cần sửa `select`:

```java
select(names, name -> name.length() >= 5);
select(names, name -> name.startsWith("A"));
```

Cách tổ chức này thường được gọi là **tham số hóa hành vi (behavior parameterization)**: thuật toán ổn định nằm ở một chỗ, còn quy tắc hoặc hành động có thể thay đổi được truyền từ bên ngoài vào.

Ý tưởng này xuất hiện ở hàm gọi lại, `Comparator`, quy tắc kiểm tra dữ liệu, công việc trì hoãn và nhiều API khác trong Java. Các chương sau sẽ giải thích hệ kiểu giúp những hành vi này an toàn như thế nào.

## <a id="functional-interface-bridge">Giao diện hàm cung cấp kiểu cho hành vi</a>

Bản thân một lambda chưa cung cấp đủ thông tin để xác định toàn bộ kiểu Java của nó. Ngữ cảnh xung quanh phải cung cấp **kiểu đích**, và kiểu đích đó phải là một giao diện hàm tương thích.

```java
Predicate<String> nonEmpty = text -> !text.isEmpty();
Function<String, Integer> length = text -> text.length();
```

Hai lambda đều có một tham số nhưng đại diện cho hai hợp đồng khác nhau:

- `Predicate<String>` nhận `String` và trả về `boolean`;
- `Function<String, Integer>` nhận `String` và tạo ra `Integer`.

Giao diện hàm vì thế là cầu nối giữa API Java thông thường và cú pháp hành vi ngắn gọn. Nó cho trình biên dịch biết kiểu tham số, yêu cầu về kết quả và hợp đồng ngoại lệ được kiểm tra mà lambda hoặc tham chiếu phương thức phải tuân theo.

Chương 2 sẽ đi sâu vào hợp đồng này trước khi Chương 3 áp dụng nó cho cơ chế xác định kiểu đích của lambda.

## <a id="functional-multi-paradigm-boundary">Phong cách hàm bên cạnh các mô hình khác trong Java</a>

Phong cách hàm bổ sung cho thiết kế Java hiện có. Một ứng dụng thực tế có thể đồng thời dùng:

- đối tượng để mô hình hóa trạng thái và trách nhiệm nghiệp vụ;
- câu lệnh mệnh lệnh cho các quy trình tuần tự rõ ràng;
- giao diện hàm và lambda khi hành vi là phần cần thay đổi;
- phương thức thông thường khi việc đặt tên giúp mã dễ đọc và tái sử dụng hơn.

Ví dụ, một dịch vụ vẫn có thể giữ phụ thuộc và trạng thái như thiết kế hướng đối tượng, nhưng nhận thêm `Predicate<Order>` để tùy biến một quyết định. Việc có lambda không biến toàn bộ hệ thống thành chương trình "thuần hàm".

Mô-đun này tập trung vào **cơ chế lập trình hàm của Java**. Các khái niệm độc lập với ngôn ngữ như hàm thuần túy, tính bất biến hay tính trong suốt tham chiếu thuộc mô-đun Mô hình lập trình hàm (Functional Programming). Ở đây chỉ nhắc tới chúng khi cần giải thích một lựa chọn cụ thể trong Java.

## <a id="functional-style-use-cases">Khi nào phong cách hàm giúp mã rõ hơn?</a>

Phong cách hàm thường hữu ích khi phần thay đổi có thể biểu diễn bằng một hợp đồng hành vi nhỏ và rõ ràng. Một số trường hợp điển hình:

- biểu diễn điều kiện bằng `Predicate<T>`;
- biến đổi một giá trị sang giá trị khác bằng `Function<T, R>`;
- cung cấp công việc tạo ra giá trị khi được gọi bằng `Supplier<T>`;
- truyền quy tắc sắp xếp bằng `Comparator<T>`;
- đăng ký hàm gọi lại hoặc hành động;
- kết hợp các hành vi nhỏ thành thao tác lớn hơn.

Ngược lại, nếu một quy trình có trạng thái vốn đã rõ ràng mà bị chia thành quá nhiều lambda nhỏ, luồng thực thi có thể khó theo dõi hơn. Lambda nên giảm phần cú pháp thừa hoặc làm rõ điểm biến thiên, không nên che giấu các bước nghiệp vụ quan trọng chỉ để mã trông "theo phong cách hàm" hơn.

Câu hỏi thực dụng là: **truyền hành vi này vào có làm ý định của bên gọi và phần mã tái sử dụng rõ hơn không?** Nếu có, giao diện hàm thường là lựa chọn phù hợp.

## <a id="functional-programming-module-boundary">Ranh giới của mô-đun Lập trình hàm</a>

Mô-đun Java Core này chịu trách nhiệm cho các cơ chế ổn định dùng để biểu diễn hành vi có kiểu trong Java:

- quy tắc giao diện hàm và SAM;
- biểu thức lambda và cơ chế xác định kiểu theo ngữ cảnh (target typing);
- bắt giữ biến và phạm vi của lambda;
- các giao diện chuẩn trong `java.util.function`;
- tham chiếu phương thức và hàm tạo;
- kết hợp hành vi ở mức Java;
- `Optional` như API Java để biểu diễn khả năng vắng giá trị;
- các vấn đề thực tế như tác dụng phụ, ngoại lệ được kiểm tra (checked exception), khả năng đọc và lựa chọn phong cách viết.

Hai vùng lân cận được giữ thành ranh giới rõ ràng:

- lý thuyết lập trình hàm độc lập với ngôn ngữ thuộc mô-đun Mô hình lập trình hàm (Functional Programming) ở tầng mô hình lập trình;
- cơ chế chuỗi xử lý, đánh giá lười, phép rút gọn, bộ thu thập, thứ tự và xử lý song song thuộc mô-đun Stream API.

Nhờ tách như vậy, lộ trình học rõ ràng hơn: trước hết hiểu Java biểu diễn và kiểm tra kiểu của hành vi như thế nào, sau đó mới áp dụng cơ chế đó trong các API nhận hành vi.
