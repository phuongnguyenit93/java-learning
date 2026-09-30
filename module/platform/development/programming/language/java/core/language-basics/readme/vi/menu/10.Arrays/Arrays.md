# Mảng

Mảng là một đối tượng đặc biệt biểu diễn **một dãy có kích thước cố định** và các phần tử cùng kiểu thành phần. Biến mảng vẫn là biến tham chiếu, vì vậy các quy tắc về sao chép tham chiếu và `null` tiếp tục áp dụng; truyền bằng giá trị sẽ được nối lại sau khi học phương thức.

## <a id="array-type-model">Mô hình kiểu mảng</a>

```java
int[] numbers = new int[3];
String[] names = new String[3];
```

Mảng có:

- kiểu mảng thực tế ở thời điểm chạy;
- `length` cố định sau khi tạo;
- chỉ số bắt đầu từ `0`;
- giá trị mặc định của phần tử theo kiểu thành phần.

Mảng tự nó là một đối tượng, nên biến mảng có thể mang `null` và nhiều biến tham chiếu có thể cùng trỏ tới một mảng.

Phân biệt hai trạng thái:

```java
String[] a = null;          // không có đối tượng mảng
String[] b = new String[1]; // có đối tượng mảng, nhưng b[0] == null
int[] empty = new int[0];   // có đối tượng mảng thật, length = 0
```

`null` và mảng rỗng không mang cùng nghĩa: `null` là không có đối tượng mảng để truy cập, còn `new int[0]` tạo ra một đối tượng mảng hợp lệ chỉ đơn giản là không có phần tử.

### Khai báo khác tạo mảng

```java
int[] a;          // chỉ khai báo biến tham chiếu
a = new int[3];   // tạo đối tượng mảng rồi gán tham chiếu

int b[] = new int[3]; // hợp lệ nhưng cách viết int[] b thường dễ đọc hơn
```

`length` là thuộc tính cố định của mảng sau khi tạo:

```java
System.out.println(a.length);
```

Không có phương thức `length()` cho mảng.

### Mảng là đối tượng có thể thay đổi

```java
int[] first = {1, 2};
int[] second = first;
second[0] = 99;
```

`first[0]` cũng thành `99` vì phép gán chỉ sao chép tham chiếu. Muốn mảng độc lập cần sao chép phần tử bằng API phù hợp.

## <a id="array-initialization">Tạo, khởi tạo và duyệt mảng</a>

Có thể tạo mảng với độ dài rồi gán từng phần tử:

```java
int[] values = new int[3];
values[0] = 10;
```

hoặc dùng biểu thức khởi tạo:

```java
int[] values = {10, 20, 30};
```

Mảng kiểu nguyên thủy nhận giá trị mặc định của kiểu nguyên thủy; mảng tham chiếu nhận `null` cho từng phần tử ban đầu.

Truy cập chỉ số ngoài `[0, length)` gây `ArrayIndexOutOfBoundsException` ở thời điểm chạy.

### Giá trị mặc định của phần tử

```java
int[] numbers = new int[2];       // [0, 0]
boolean[] flags = new boolean[2]; // [false, false]
String[] names = new String[2];   // [null, null]
```

Điều này khác definite assignment của biến cục bộ: đối tượng mảng đã được khởi tạo và từng ô phần tử có giá trị mặc định.

Với mảng tham chiếu, giá trị `null` của phần tử vẫn có hệ quả khi truy cập qua tham chiếu:

```java
User[] users = new User[1];
users[0].getName(); // NullPointerException vì users[0] == null
```

### Duyệt mảng

```java
for (int i = 0; i < values.length; i++) {
    System.out.println(values[i]);
}

for (int value : values) {
    System.out.println(value);
}
```

Dùng chỉ số khi cần vị trí hoặc thay đổi phần tử. Enhanced-for phù hợp khi chỉ cần đọc từng phần tử.

Biến của enhanced-for là một biến cục bộ nhận giá trị phần tử ở mỗi lượt. Gán lại biến đó **không thay phần tử trong mảng**:

```java
int[] numbers = {1, 2, 3};
for (int number : numbers) {
    number = 0; // chỉ đổi biến cục bộ number
}
```

Muốn thay ô phần tử của mảng, dùng chỉ số hoặc API phù hợp.

Enhanced-for cũng áp dụng cho `Iterable` như các Collection sẽ học sau này. Quy tắc về biến vòng lặp vẫn giống nhau: gán lại biến cục bộ không thay thế phần tử đang nằm trong cấu trúc chứa.

```java
List<String> items = List.of("A", "B"); // xem trước API Collection
for (String item : items) {
    item = item.toLowerCase(); // không thay phần tử trong items
}
```

### So sánh và sao chép

`==` trên mảng so tính đồng nhất của tham chiếu, không so từng phần tử. Khi cần so nội dung hoặc sao chép, dùng tiện ích phù hợp như `Arrays.equals`, `Arrays.copyOf` hoặc `System.arraycopy` tùy trường hợp.

### Sao chép mảng không đồng nghĩa sao chép sâu phần tử

Với mảng kiểu nguyên thủy, sao chép phần tử tạo ra giá trị nguyên thủy độc lập ở mảng mới. Với mảng tham chiếu, mảng mới nhận **bản sao của từng giá trị tham chiếu**:

```java
User[] original = {
    new User("A")
};

User[] copied = Arrays.copyOf(original, original.length);

copied[0].setName("B");
System.out.println(original[0].getName()); // B
```

Ở đây `original` và `copied` là hai đối tượng mảng khác nhau, nhưng phần tử chỉ số `0` của cả hai chứa tham chiếu tới cùng đối tượng `User`:

```text
mảng original            mảng copied
┌─────────────┐            ┌─────────────┐
│ ref R ──────┼──────┐     │ ref R ──────┼──────┐
└─────────────┘      │     └─────────────┘      │
                     └────────→ User ←───────────┘
```

Vì vậy `Arrays.copyOf`/`System.arraycopy` sao chép nội dung mảng theo giá trị phần tử; chúng không tự sao chép sâu đối tượng được các phần tử tham chiếu tới. Chiến lược sao chép nông/sâu của đối tượng được đào sâu ở mô-đun `class-object`.

## <a id="array-covariance-risk">Hiệp biến của mảng và rủi ro</a>

Mảng tham chiếu trong Java có tính hiệp biến:

```java
String[] strings = new String[1];
Object[] objects = strings;
```

Phép gán này biên dịch được, nhưng kiểu thực tế của mảng ở thời điểm chạy vẫn là `String[]`.

```java
objects[0] = Integer.valueOf(1); // ArrayStoreException
```

Trình biên dịch cho phép quan hệ kiểu này, còn kiểm tra ở thời điểm chạy bảo vệ kiểu thành phần thực tế của mảng.

Đây là một khác biệt quan trọng với collection generic, vốn bất biến theo cách khác.

### Vì sao lỗi xuất hiện ở thời điểm chạy?

Biến `objects` có kiểu lúc biên dịch `Object[]`, nên trình biên dịch cho phép gán `Integer`. Nhưng đối tượng thực tế vẫn là `String[]` và mảng mang kiểu thành phần ở thời điểm chạy, vì vậy JVM phải kiểm tra mỗi lần ghi để giữ an toàn kiểu.

```text
góc nhìn lúc biên dịch: Object[]
đối tượng lúc chạy:       String[]
ghi Integer
        ↓
kiểm tra lúc chạy → ArrayStoreException
```

Điều này minh họa rõ ranh giới kiểu tĩnh so với kiểu lúc chạy mà chương cuối sẽ tổng kết.

## <a id="multidimensional-arrays">Mảng nhiều chiều</a>

Mảng nhiều chiều trong Java thực chất là **mảng chứa mảng**:

```java
int[][] matrix = new int[2][3];
```

Mỗi hàng là một `int[]` riêng, nên mảng “răng cưa” (jagged array) hoàn toàn hợp lệ:

```java
int[][] data = {
    {1, 2},
    {3, 4, 5}
};
```

Không nên mặc định nó là một ma trận chữ nhật liên tục trong bộ nhớ như ở một số mô hình ngôn ngữ khác.

Mỗi hàng có thể có độ dài khác nhau hoặc thậm chí là `null`:

```java
int[][] data = new int[3][];
data[0] = new int[2];
data[1] = new int[5];
// data[2] vẫn null
```

Do đó mã vòng lặp lồng nhau nên dùng `data[row].length` cho từng hàng và xử lý hàng `null` nếu hợp đồng cho phép.

Chương tiếp theo dùng mảng làm nền để giải thích `varargs` và cách dữ liệu đi qua lời gọi phương thức.
