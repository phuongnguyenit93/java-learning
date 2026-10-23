<a id="back-to-top"></a>

# Duyệt phần tử

## Menu
- [Vòng lặp theo chỉ số, enhanced for và Iterable](#iteration-001)
- [Iterator: hasNext, next và forEachRemaining](#iteration-002)
- [Iterator.remove và điều kiện gọi hợp lệ](#iteration-003)
- [ListIterator: duyệt hai chiều, chỉ số và con trỏ](#iteration-004)
- [ListIterator.add, set và remove](#iteration-005)
- [forEach, lambda và giới hạn thay đổi nguồn khi duyệt](#iteration-006)
- [Cơ chế fail-fast và giới hạn bảo đảm của ConcurrentModificationException](#iteration-007)
- [Phân biệt iterator fail-fast, ảnh chụp và nhất quán yếu](#iteration-008)

## <a id="iteration-001">Vòng lặp theo chỉ số, enhanced for và Iterable</a>

<details>
<summary>Xem chi tiết</summary>

# Vòng lặp theo chỉ số, enhanced for và Iterable

**Duyệt phần tử** là lần lượt lấy các phần tử từ một nguồn dữ liệu để xử lý. Trong Java, hai cách quen thuộc là vòng lặp dùng chỉ số và vòng lặp for-each, còn được gọi là **enhanced for**.

Chúng không chỉ khác cú pháp:

- Vòng lặp theo chỉ số dựa vào khả năng truy cập một vị trí.
- Enhanced for diễn đạt việc xử lý từng phần tử mà không yêu cầu người viết tự quản lý vị trí.
- `Iterable<T>` là hợp đồng giúp một đối tượng cung cấp cách duyệt các phần tử của nó.

Hiểu quan hệ này giúp chọn cách duyệt phù hợp, tránh truy cập kém hiệu quả và không nhầm việc thay biến vòng lặp với thay dữ liệu trong collection.

Bài sử dụng Java 17. Ba chương trình hoàn chỉnh đã được biên dịch với `--release 17` và chạy để đối chiếu stdout. Các đoạn rút gọn còn lại chỉ minh họa một thao tác hoặc một phần của phương thức, không phải tệp chạy độc lập.

Sau bài học, bạn cần có thể:

- Viết vòng lặp đúng giới hạn của mảng hoặc danh sách.
- Giải thích nguồn dữ liệu nào dùng được với enhanced for.
- Mô tả vai trò khác nhau của `Iterable` và `Iterator`.
- Chọn cách duyệt theo nhu cầu chỉ số, thay thế phần tử và cấu trúc dữ liệu.
- Tự xây dựng một `Iterable` nhỏ với các lượt duyệt độc lập.

## 1. Bắt đầu từ nhu cầu, không bắt đầu từ cú pháp

Giả sử có các bước xử lý:

```text
read
write
run
```

Hai yêu cầu khác nhau dẫn đến cách viết khác nhau:

| Yêu cầu | Thông tin cần dùng |
| --- | --- |
| In tên từng bước | Chỉ cần phần tử |
| In vị trí và tên bước | Cần chỉ số cùng phần tử |
| Thay phần tử thứ hai | Cần thao tác theo vị trí |
| Duyệt một tập hợp không có chỉ số | Cần cơ chế lấy phần tử tiếp theo |
| Duyệt một dải số do chương trình sinh ra | Cần một nguồn có thể cung cấp phần tử lần lượt |

Không phải dữ liệu nào cũng có vị trí truy cập bằng `get(index)`. Nếu chỉ cần phần tử, việc bắt nguồn dữ liệu phải cung cấp chỉ số có thể tạo phụ thuộc thừa.

## 2. Vòng lặp theo chỉ số

### Với mảng

Đoạn minh họa trong một phương thức:

```java
String[] steps = {"read", "write", "run"};

for (int index = 0; index < steps.length; index++) {
    String step = steps[index];
    System.out.println(index + " -> " + step);
}
```

Ba phần của vòng lặp:

1. `int index = 0`: bắt đầu từ vị trí đầu.
2. `index < steps.length`: chỉ dùng các vị trí hợp lệ.
3. `index++`: chuyển sang vị trí kế tiếp sau mỗi lượt.

Mảng có độ dài `n` dùng chỉ số từ `0` đến `n - 1`. Nếu điều kiện là `index <= steps.length`, vòng lặp sẽ cố truy cập vị trí `n`, nằm ngoài mảng.

Mảng rỗng có `length == 0`; điều kiện sai ngay từ đầu nên thân vòng lặp không chạy.

### Với List

```java
List<String> steps = new ArrayList<>(List.of("read", "write", "run"));

for (int index = 0; index < steps.size(); index++) {
    String step = steps.get(index);
    System.out.println(index + " -> " + step);
}
```

Đoạn này cần các import `List` và `ArrayList` từ `java.util`.

Phân biệt:

- Mảng dùng thuộc tính `length` và cú pháp `array[index]`.
- Danh sách dùng phương thức `size()` và `get(index)`.
- Giao diện `List` có thao tác truy cập theo chỉ số.
- Giao diện `Collection` chung không khai báo `get(index)`; `Set` không phải một danh sách có chỉ số.

### Khi chỉ số có giá trị học tập hoặc nghiệp vụ

Vòng lặp chỉ số phù hợp khi cần:

- In vị trí phần tử.
- Truy cập các vị trí lân cận, sau khi kiểm tra giới hạn.
- Gán vào một ô mảng hoặc thay phần tử danh sách.
- Duyệt một đoạn, duyệt ngược hoặc bước qua nhiều vị trí.
- Phối hợp nhiều nguồn theo cùng một chỉ số khi chúng thực sự có quan hệ vị trí.

Ví dụ duyệt ngược một danh sách:

```java
for (int index = steps.size() - 1; index >= 0; index--) {
    System.out.println(steps.get(index));
}
```

Khi danh sách rỗng, giá trị bắt đầu là `-1`, điều kiện sai ngay. Không có lần truy cập `get(-1)` trong đoạn này.

Việc điều khiển chỉ số đem lại sự chủ động, nhưng cũng làm người viết chịu trách nhiệm về cận, bước nhảy và các thay đổi vị trí trong lúc duyệt.

## 3. Enhanced for: thể hiện ý định xử lý từng phần tử

Cú pháp:

```java
for (String step : steps) {
    System.out.println(step);
}
```

Đọc là: với mỗi phần tử lấy từ `steps`, gán giá trị đó cho biến `step` của lượt hiện tại rồi thực hiện thân vòng lặp.

Cú pháp này dùng được với:

1. Mảng, kể cả mảng nguyên thủy như `int[]`.
2. Đối tượng có kiểu phù hợp với `Iterable`.

Nó không có biến chỉ số được cung cấp sẵn. Nếu thật sự cần một chỉ số truy cập vị trí, hãy cân nhắc vòng lặp theo chỉ số. Nếu chỉ muốn một số thứ tự để hiển thị, có thể đếm số lượt, nhưng số đếm đó không tạo thêm khả năng truy cập chỉ số cho nguồn.

### Những bảo đảm cần đọc đúng

Enhanced for:

- Lấy phần tử theo cơ chế duyệt của nguồn.
- Không tự sắp xếp.
- Không tự loại trùng.
- Không tự tạo bản sao độc lập của toàn bộ dữ liệu.
- Không mặc định làm cho thao tác trở nên an toàn khi nhiều luồng cùng sử dụng.
- Không tự chuyển một đối tượng bất kỳ thành nguồn duyệt được.

Danh sách thường được duyệt theo thứ tự danh sách. Với một tập hợp, thứ tự phải dựa vào hợp đồng cụ thể của tập hợp đó; cú pháp for-each không bổ sung bảo đảm thứ tự mới.

### Break và continue

Vẫn có thể dùng các lệnh điều khiển thông thường:

- `break`: thoát vòng lặp theo phạm vi của lệnh.
- `continue`: bỏ phần còn lại của lượt hiện tại và chuyển sang lượt tiếp theo.

Ví dụ dừng khi gặp bước cần tìm:

```java
for (String step : steps) {
    if (step.equals("write")) {
        break;
    }
    System.out.println(step);
}
```

Đoạn minh họa giả định `step` không `null`. Điều kiện dừng là quyết định của thân vòng lặp, không phải việc xóa phần tử khỏi nguồn.

## 4. So sánh trực tiếp hai cách duyệt

Tạo tệp **`TraversalStylesDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TraversalStylesDemo {
    public static void main(String[] args) {
        List<String> steps = new ArrayList<>(List.of("read", "write", "run"));

        System.out.println("indexed:");
        for (int index = 0; index < steps.size(); index++) {
            System.out.println(index + " -> " + steps.get(index));
        }

        System.out.println("enhanced:");
        for (String step : steps) {
            System.out.println(step);
        }

        int[] numbers = {2, 4, 6};

        for (int number : numbers) {
            number *= 10;
        }
        System.out.println("after local assignment = " + Arrays.toString(numbers));

        for (int index = 0; index < numbers.length; index++) {
            numbers[index] *= 10;
        }
        System.out.println("after indexed assignment = " + Arrays.toString(numbers));

        for (int index = steps.size() - 1; index >= 0; index--) {
            System.out.println("reverse " + index + " -> " + steps.get(index));
        }
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 TraversalStylesDemo.java
java -Dfile.encoding=UTF-8 TraversalStylesDemo
```

Stdout:

```text
indexed:
0 -> read
1 -> write
2 -> run
enhanced:
read
write
run
after local assignment = [2, 4, 6]
after indexed assignment = [20, 40, 60]
reverse 2 -> run
reverse 1 -> write
reverse 0 -> read
```

### Đọc chương trình

- Vòng lặp chỉ số in được cả vị trí lẫn tên bước.
- Enhanced for lấy từng chuỗi mà không viết `get(index)`.
- Vòng lặp trên mảng số dùng biến `number` nhận **giá trị** của phần tử ở mỗi lượt.
- Gán `number *= 10` chỉ đổi biến cục bộ, nên mảng vẫn là `[2, 4, 6]`.
- Gán `numbers[index] *= 10` thay đúng vị trí trong mảng, nên kết quả là `[20, 40, 60]`.
- Vòng lặp cuối duyệt danh sách ngược bằng chỉ số.

Cả hai cách đều có thể biểu diễn việc duyệt, nhưng không cung cấp cùng quyền truy cập vào vị trí của nguồn.

## 5. Biến vòng lặp không phải một ô trong collection

Điểm này đặc biệt dễ nhầm khi phần tử là đối tượng.

### Với giá trị nguyên thủy

Trong:

```java
for (int number : numbers) {
    number = 100;
}
```

`number` là biến cục bộ. Nó không phải một tên khác có thể gán thay cho `numbers[index]`.

### Với đối tượng

Trong:

```java
for (Counter counter : counters) {
    counter.value += 10;
}
```

biến `counter` nhận một bản sao của **tham chiếu** tới đối tượng. Tham chiếu đó vẫn trỏ tới cùng đối tượng đang được collection giữ, nên sửa trạng thái của đối tượng có thể được quan sát qua collection.

Nhưng:

```java
for (Counter counter : counters) {
    counter = new Counter(99);
}
```

chỉ đổi tham chiếu trong biến cục bộ. Phần tử trong danh sách không được thay bằng đối tượng mới.

Cần phân biệt ba hành động:

| Hành động | Có thay phần tử được lưu ở vị trí của danh sách không? |
| --- | --- |
| Gán lại biến cục bộ trong for-each | Không |
| Sửa trạng thái đối tượng được tham chiếu | Giữ nguyên tham chiếu, nhưng đối tượng có thể thay đổi |
| `list.set(index, other)` trên danh sách hỗ trợ thay thế | Có |

### Chương trình hoàn chỉnh

Tạo tệp **`ElementReferenceDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class ElementReferenceDemo {
    private static final class Counter {
        private int value;

        private Counter(int value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return Integer.toString(value);
        }
    }

    public static void main(String[] args) {
        List<Counter> counters = new ArrayList<>(
                List.of(new Counter(1), new Counter(2)));

        for (Counter counter : counters) {
            counter.value += 10;
        }
        System.out.println("after object mutation = " + counters);

        for (Counter counter : counters) {
            counter = new Counter(99);
        }
        System.out.println("after local reassignment = " + counters);

        for (int index = 0; index < counters.size(); index++) {
            counters.set(index, new Counter(20 + index));
        }
        System.out.println("after list replacement = " + counters);
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 ElementReferenceDemo.java
java -Dfile.encoding=UTF-8 ElementReferenceDemo
```

Stdout:

```text
after object mutation = [11, 12]
after local reassignment = [11, 12]
after list replacement = [20, 21]
```

Kết quả cho thấy:

1. Sửa thuộc tính của hai đối tượng làm dữ liệu thành `[11, 12]`.
2. Gán `counter` sang đối tượng mới không thay danh sách.
3. `set` theo chỉ số mới thật sự thay các phần tử danh sách thành hai đối tượng mới.

Ở các kiểu bất biến như `String`, không có thao tác sửa trực tiếp nội dung của đối tượng chuỗi. Ví dụ, nhận một chuỗi mới từ một phương thức rồi chỉ gán vào biến for-each cũng không cập nhật danh sách chuỗi.

## 6. Iterable và Iterator giữ hai vai trò khác nhau

### Iterable<T>: nguồn có thể cung cấp lượt duyệt

Chữ ký cốt lõi, ở dạng rút gọn:

```java
public interface Iterable<T> {
    Iterator<T> iterator();
}
```

Đây là trích dạng chữ ký để giải thích hợp đồng, không phải giao diện cần tự định nghĩa lại.

`iterator()` trả về một đối tượng có khả năng lần lượt cung cấp phần tử. Nguồn không cần tiết lộ nó lưu bằng mảng, danh sách liên kết hay sinh giá trị khi được yêu cầu.

`Iterable` không bắt buộc có:

- `size()`.
- `get(index)`.
- `add` hoặc `remove`.
- Dữ liệu đã được nạp hết trong bộ nhớ.
- Một số lượng phần tử hữu hạn.
- Một snapshot cố định cho mọi lượt duyệt.

### Iterator<T>: trạng thái của một lượt duyệt

Hai thao tác quan trọng:

| Thao tác | Ý nghĩa |
| --- | --- |
| `hasNext()` | Cho biết còn phần tử để lấy không |
| `next()` | Trả phần tử tiếp theo và tiến trạng thái duyệt |

`next()` khi đã hết cần ném `NoSuchElementException`. Không dùng `null` như tín hiệu kết thúc chung vì một nguồn có thể hợp lệ chứa phần tử `null`.

`hasNext()` không có nhiệm vụ tiến sang phần tử tiếp theo. Nếu bỏ quên `next()` trong một vòng lặp thủ công, chương trình có thể không tiến được.

Một `Iterator` thường được sử dụng như con trỏ của một lượt duyệt đã tiến dần. Bản thân kiểu `Iterator` không mặc định là `Iterable`; không thể cho rằng có iterator là có thể đặt trực tiếp nó bên phải dấu `:` của enhanced for.

### Quan hệ với Collection

```text
Collection<E> mở rộng Iterable<E>

Nguồn Iterable<E>
    -> iterator()
    -> một Iterator<E> giữ vị trí duyệt
    -> next() cung cấp từng phần tử
```

`List`, `Set` và nhiều loại collection do đó dùng được với enhanced for thông qua hợp đồng kế thừa này.

Cần tránh nhầm “nguồn” với “con trỏ đang ở đâu trong nguồn”. Hai biến cùng trỏ tới một iterator sẽ cùng chia sẻ trạng thái duyệt của chính iterator đó.

## 7. Enhanced for hoạt động như thế nào?

### Trên một Iterable

Vòng lặp:

```java
for (String step : steps) {
    System.out.println(step);
}
```

có thể được hiểu gần với thao tác tường minh:

```java
Iterator<String> cursor = steps.iterator();

while (cursor.hasNext()) {
    String step = cursor.next();
    System.out.println(step);
}
```

Đoạn rút gọn cần `import java.util.Iterator;` và một nguồn `steps` có kiểu tương ứng. Nó minh họa ý tưởng của cơ chế duyệt, không cố liệt kê mọi chi tiết chuyển đổi kiểu của trình biên dịch.

Những điểm quan trọng:

- Một iterator được lấy cho lượt vòng lặp này.
- `hasNext()` quyết định có tiếp tục không.
- `next()` cung cấp phần tử ở mỗi lượt.
- Biến `step` là biến của thân vòng lặp, không phải một ô có thể gán ngược vào nguồn.
- Lượt for-each sau sẽ yêu cầu iterator qua một lần gọi khác tới `iterator()`.

### Trên một mảng

Mảng dùng được với enhanced for nhờ quy tắc riêng của ngôn ngữ Java; mảng **không triển khai `Iterable`**.

Về ý tưởng, trình biên dịch giữ tham chiếu tới mảng cần duyệt rồi đi qua các vị trí của mảng. Nó không cần tạo một `Iterator` của Java Collections Framework cho mảng.

Hệ quả:

- `int[]` dùng được trong `for (int value : array)`.
- Không thể vì vậy mà truyền `int[]` vào một phương thức yêu cầu `Iterable<Integer>`.
- Gán lại biến nguồn mảng ở nơi khác không có nghĩa vòng for-each đã bắt đầu sẽ tự chuyển sang một mảng khác.
- Đừng giả định có snapshot sâu: các giá trị ở những vị trí chưa đọc vẫn thuộc mảng đang được duyệt.

Hai loại nguồn cùng dùng một cú pháp nhưng không phải cùng một cơ chế bên dưới.

## 8. Tự định nghĩa một Iterable đơn giản

Ta tạo một dải số nguyên:

```text
[startInclusive, endExclusive)
```

Ví dụ `[3, 6)` cung cấp `3`, `4`, `5`. Dải không cần giữ sẵn một collection chứa ba số; nó có thể tạo từng giá trị từ trạng thái con trỏ.

Thiết kế này cần:

1. Nguồn giữ hai cận không thay đổi.
2. Mỗi lời gọi `iterator()` tạo một iterator mới.
3. Mỗi iterator có biến `current` riêng.
4. `hasNext()` kiểm tra cận.
5. `next()` từ chối khi hết, nếu chưa hết thì trả giá trị rồi tiến con trỏ.

### Chương trình hoàn chỉnh

Tạo tệp **`IterableRangeDemo.java`**:

```java
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.StringJoiner;

public class IterableRangeDemo {
    public record IntRange(int startInclusive, int endExclusive)
            implements Iterable<Integer> {
        public IntRange {
            if (endExclusive < startInclusive) {
                throw new IllegalArgumentException("Invalid range");
            }
        }

        @Override
        public Iterator<Integer> iterator() {
            return new Iterator<>() {
                private int current = startInclusive;

                @Override
                public boolean hasNext() {
                    return current < endExclusive;
                }

                @Override
                public Integer next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return current++;
                }
            };
        }
    }

    public static void main(String[] args) {
        IntRange range = new IntRange(3, 6);

        System.out.println("range pass 1 = " + join(range));
        System.out.println("range pass 2 = " + join(range));
        System.out.println("list = " + join(List.of(8, 9)));

        Iterator<Integer> first = range.iterator();
        Iterator<Integer> second = range.iterator();

        System.out.println("first iterator = " + first.next() + ", " + first.next());
        System.out.println("second iterator = " + second.next());

        Iterator<Integer> empty = new IntRange(5, 5).iterator();
        System.out.println("empty hasNext = " + empty.hasNext());

        try {
            empty.next();
        } catch (NoSuchElementException exception) {
            System.out.println("empty next = "
                    + exception.getClass().getSimpleName());
        }
    }

    private static String join(Iterable<Integer> source) {
        StringJoiner result = new StringJoiner(", ", "[", "]");
        for (int number : source) {
            result.add(Integer.toString(number));
        }
        return result.toString();
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 IterableRangeDemo.java
java -Dfile.encoding=UTF-8 IterableRangeDemo
```

Stdout:

```text
range pass 1 = [3, 4, 5]
range pass 2 = [3, 4, 5]
list = [8, 9]
first iterator = 3, 4
second iterator = 3
empty hasNext = false
empty next = NoSuchElementException
```

### Vì sao ví dụ này thực hiện đúng vai trò?

- `IntRange` triển khai `Iterable<Integer>` nhưng không phải một `Collection`.
- Dải không cung cấp thao tác thêm/xóa hoặc truy cập bằng chỉ số.
- Hàm tạo từ chối cận cuối nhỏ hơn cận đầu.
- Dải có hai cận bằng nhau là rỗng.
- `iterator()` tạo đối tượng iterator mới, nên hai lượt `join(range)` đều bắt đầu từ `3`.
- Hai iterator tường minh cũng có trạng thái độc lập: iterator đầu đã lấy `3`, `4`, nhưng iterator thứ hai vẫn bắt đầu từ `3`.
- `next()` trên dải rỗng gây đúng `NoSuchElementException`.
- Phương thức `join` chỉ cần `Iterable<Integer>`, nên dùng được với cả dải tự định nghĩa và một `List<Integer>`.

Do cận cuối là cận loại trừ, phần tử cuối được trả về nhỏ hơn `endExclusive`. Với phạm vi `int` hợp lệ trong thiết kế này, thao tác tăng sau phần tử cuối không cần vượt quá `Integer.MAX_VALUE`.

Ví dụ không cung cấp khả năng xóa phần tử qua iterator. Nguồn là một dải giá trị được mô tả bằng cận, không phải collection khả biến cần gỡ phần tử.

### Không suy rộng tính lặp lại cho mọi Iterable

Trong `IntRange`, các iterator độc lập và có thể duyệt lại vì ta đã thiết kế như vậy. Chỉ nhìn thấy kiểu `Iterable` chưa đủ để kết luận mọi nguồn đều:

- Lặp lại được bao nhiêu lần cũng cho cùng dữ liệu.
- Đọc dữ liệu miễn phí.
- Tạo các iterator độc lập trong mọi điều kiện.
- Không phụ thuộc tài nguyên bên ngoài hoặc thời điểm đọc.

Khi thiết kế API, hãy tài liệu hóa các điều kiện này. Nếu nguồn có tài nguyên cần đóng, riêng `Iterable` cũng không có hợp đồng tự đóng tài nguyên thay cho bên sử dụng.

## 9. Chọn cách duyệt theo cấu trúc và yêu cầu

| Nhu cầu | Hướng lựa chọn |
| --- | --- |
| Đọc tuần tự mọi phần tử | Enhanced for thường diễn đạt ý định rõ |
| Cần sửa một vị trí mảng | Vòng lặp theo chỉ số |
| Cần thay phần tử của một `List` | Chỉ số với `set`, hoặc cơ chế phù hợp của danh sách |
| Cần duyệt nguồn chỉ có `Iterable` | Enhanced for hoặc iterator tường minh |
| Cần tự điều khiển con trỏ, chẳng hạn xóa qua iterator được hỗ trợ | Iterator tường minh |
| Cần duyệt ngược một nguồn | Cơ chế ngược mà nguồn hỗ trợ; không mặc định mọi nguồn đều có chỉ số |
| Cần số thứ tự để hiển thị | Có thể đếm số lượt; không coi số đếm là chỉ số truy cập của nguồn |

### Hiệu quả của get(index) phụ thuộc lớp triển khai

Với `ArrayList`, truy cập một chỉ số có chi phí không tăng theo số phần tử trong mô hình sử dụng thông thường. Duyệt bằng `get(index)` liên tiếp là một lựa chọn hợp lý khi cần chỉ số.

Với `LinkedList`, việc lấy phần tử theo chỉ số cần tìm tới vị trí trong chuỗi liên kết. Nếu cứ gọi `get(0)`, `get(1)`, ..., `get(n - 1)`, tổng công việc có thể tăng theo bậc hai của số phần tử.

Duyệt tuần tự bằng iterator của `LinkedList` tránh việc tìm lại từ đầu hoặc cuối cho từng chỉ số và có chi phí tuyến tính cho toàn lượt duyệt.

Do đó, nếu một phương thức nhận `List` bất kỳ và chỉ cần đọc lần lượt, enhanced for thường tránh được giả định không cần thiết về khả năng truy cập ngẫu nhiên.

Không nên kết luận mọi enhanced for luôn nhanh hơn mọi vòng lặp chỉ số. Kiểu dữ liệu, công việc trong thân vòng lặp và tối ưu hóa lúc chạy đều ảnh hưởng. Trước hết hãy chọn cách đúng với hợp đồng và tránh cấu trúc truy cập có chi phí không phù hợp.

### Lưu size trước vòng lặp không phải cách chữa mọi vấn đề

Viết `int count = list.size()` trước vòng lặp chỉ có nghĩa bạn đang dùng một cận đã lưu. Nó không làm `get(index)` của danh sách liên kết trở thành truy cập ngẫu nhiên nhanh.

Nếu danh sách thay đổi trong lúc duyệt, cận đã lưu còn có thể không phù hợp với kích thước hiện tại. Chỉ dùng cách tổ chức này khi điều kiện dữ liệu và mục đích rõ ràng.

## 10. Những nguồn không dùng được trực tiếp với enhanced for

### Map không triển khai Iterable

Không duyệt trực tiếp một `Map` bằng:

```text
for (entry : map)
```

theo cách suy đoán đó. `Map` cung cấp các góc nhìn:

| Góc nhìn | Nội dung |
| --- | --- |
| `keySet()` | Các khóa |
| `values()` | Các giá trị |
| `entrySet()` | Các cặp khóa–giá trị |

Nếu cần cả khóa và giá trị, một mẫu minh họa là:

```java
for (Map.Entry<String, Integer> entry : counts.entrySet()) {
    System.out.println(entry.getKey() + " = " + entry.getValue());
}
```

Đoạn này cần `import java.util.Map;` và biến `counts` có kiểu map phù hợp. Thứ tự duyệt vẫn phụ thuộc hợp đồng của loại map và góc nhìn, không phải do enhanced for quyết định.

### String không tự là một Iterable<Character>

Một đối tượng `String` không thể được đặt trực tiếp vào enhanced for với giả định nó sẽ cung cấp từng ký tự.

Có thể lựa chọn cách duyệt phù hợp với yêu cầu ký tự, đơn vị mã hoặc điểm mã, nhưng đó là bài toán cần xác định rõ. Không nên mặc định một ký tự hiển thị luôn tương đương một `char`.

### Iterator không tự là nguồn Iterable

Nếu bạn đã có một iterator, hãy điều khiển nó qua `hasNext()` và `next()`, hoặc cơ chế riêng mà API đó cung cấp.

`Iterable` có nhiệm vụ cấp cách duyệt; iterator cụ thể là trạng thái đang được tiến. Hai vai trò không thể thay thế chỉ bằng việc đổi tên biến.

## 11. Null, kiểu phần tử và dữ liệu thay đổi

### Nguồn null khác nguồn rỗng

- Nguồn rỗng: không có lượt xử lý phần tử.
- Nguồn `null`: không có đối tượng mảng hoặc `Iterable` để duyệt; enhanced for sẽ gây `NullPointerException`.

Hãy thống nhất API trả về nguồn rỗng hay cho phép `null`. Đừng dùng hai trạng thái đó như thể giống nhau.

### Phần tử null có thể gây lỗi trong thân vòng lặp

Với nguồn có thể chứa `null`, enhanced for vẫn có thể nhận phần tử đó. Lỗi có thể phát sinh khi gọi phương thức trên biến hoặc mở hộp.

Ví dụ minh họa:

```java
for (int number : values) {
    System.out.println(number);
}
```

Nếu `values` là một `Iterable<Integer>` chứa `null`, việc chuyển `Integer` sang `int` ở lượt tương ứng cần mở hộp và gây `NullPointerException`.

Enhanced for không tự xác thực hoặc bỏ qua dữ liệu thiếu.

### Thêm/xóa trong lúc đang duyệt cần một cơ chế được hỗ trợ

Không nên trực tiếp thêm/xóa cấu trúc collection trong một for-each rồi kỳ vọng con trỏ luôn tự cập nhật đúng.

Một số iterator có cơ chế phát hiện sửa đổi và có thể ném `ConcurrentModificationException`, kể cả khi chỉ có một luồng. Đây không phải bảo đảm rằng mọi lần sửa sai đều được phát hiện.

Các hướng xử lý tùy mục tiêu:

- Xóa qua iterator tường minh khi nguồn hỗ trợ thao tác đó.
- Dùng `removeIf` trên collection hỗ trợ xóa.
- Thu thập thay đổi rồi áp dụng sau lượt duyệt.
- Chọn loại collection có hợp đồng duyệt phù hợp với yêu cầu đồng thời.

Không nên chỉ đổi sang vòng lặp chỉ số để “né ngoại lệ”. Xóa một phần tử làm các phần tử phía sau đổi chỉ số; nếu tăng chỉ số như cũ, có thể bỏ sót phần tử hoặc truy cập sai giới hạn.

Sửa trạng thái của đối tượng phần tử, thay phần tử của danh sách và thêm/xóa cấu trúc là các loại thay đổi khác nhau. Cách xử lý phải dựa vào hợp đồng của nguồn và mục tiêu cụ thể.

## 12. Enhanced for khác phương thức forEach

Cú pháp:

```java
for (String step : steps) {
    System.out.println(step);
}
```

khác với lời gọi phương thức:

```java
steps.forEach(step -> System.out.println(step));
```

Mẫu thứ hai dùng một hàm được truyền vào API; nó không biến thân lambda thành thân vòng lặp enhanced for.

| Đặc điểm | Enhanced for | `forEach` nhận hàm xử lý |
| --- | --- | --- |
| Hình thức | Cấu trúc điều khiển của ngôn ngữ | Lời gọi phương thức |
| Dừng bằng `break` của vòng lặp | Có | Không có vòng lặp đó để `break` bên trong lambda |
| Bỏ phần còn lại của lượt bằng `continue` | Có | Không dùng `continue` như thân vòng lặp |
| Yêu cầu biết chỉ số | Không | Cũng không tự cung cấp chỉ số |
| Cách hiểu ngữ cảnh xử lý | Thân vòng lặp đang viết | Hàm xử lý do API gọi |

Không cần đổi sang lambda chỉ để code trông ngắn. Hãy chọn hình thức thể hiện rõ luồng điều khiển cần có, nhất là khi muốn dừng sớm hoặc có nhiều nhánh.

## 13. Lỗi thường gặp

### Dùng <= thay vì < với length hoặc size

Chỉ số hợp lệ cuối cùng là số phần tử trừ một. Kiểm tra điều kiện trước khi truy cập, kể cả khi nhóm rỗng.

### Gán biến for-each để thay phần tử

Gán `element = other` chỉ thay biến cục bộ. Với mảng hoặc danh sách cần thay thế, dùng cơ chế gán vị trí được hỗ trợ.

### Cho rằng collection không sửa đổi được thì đối tượng bên trong cũng bất biến

Không cho thêm/xóa hoặc thay vị trí không đồng nghĩa các đối tượng khả biến bên trong không thể bị sửa. Cần phân biệt hợp đồng của nhóm với hợp đồng phần tử.

### Dùng get(index) trên mọi kiểu Collection

`Collection` không có thao tác đó. Chỉ yêu cầu `List` khi cần đúng khả năng của danh sách; nếu chỉ đọc lần lượt, `Iterable` có thể đã đủ.

### Tạo một iterator dùng chung cho mọi iterator()

Nếu nguồn muốn cung cấp các lượt duyệt độc lập nhưng luôn trả cùng một iterator đang tiến, lượt sau có thể bắt đầu ở giữa hoặc hết dữ liệu. Vòng lặp lồng nhau cũng có thể can thiệp vào cùng trạng thái.

Hãy thiết kế và tài liệu hóa chính sách iterator. Ví dụ `IntRange` tạo mới để phù hợp với nhu cầu duyệt lại.

### Trả null khi iterator hết dữ liệu

`null` có thể là một phần tử. Khi hết, `hasNext()` phải thể hiện trạng thái và `next()` cần ném `NoSuchElementException`.

### Gọi hasNext mà không lấy phần tử

`hasNext()` không phải thao tác tiến. Trong vòng lặp iterator thủ công, phải có đường gọi `next()` phù hợp nếu muốn chuyển phần tử.

### Mặc định foreach là snapshot an toàn

Enhanced for không tự sao chép dữ liệu. Thứ tự, tính nhất quán khi có sửa đổi và khả năng dùng nhiều luồng đều phải được xác định từ nguồn.

## 14. Thực hành

### Bài 1: chọn cách duyệt

Với từng yêu cầu, chọn vòng lặp và giải thích:

1. In các tên trong một `Set<String>`.
2. Đổi mọi giá trị trong `int[]` thành giá trị gấp đôi.
3. In chỉ số và giá trị của một `ArrayList<String>`.
4. Đọc lần lượt một `LinkedList<String>` mà không cần vị trí.
5. Viết phương thức nhận bất kỳ nguồn `Iterable<Integer>` nào và tính tổng theo chính sách số học đã chọn.

Nêu rõ yêu cầu nào cần vị trí và yêu cầu nào chỉ cần phần tử.

### Bài 2: dự đoán gán biến và sửa đối tượng

Trong `ElementReferenceDemo`:

- Đổi phần sửa thuộc tính từ cộng `10` thành nhân `10`.
- Giữ nguyên vòng lặp gán lại biến cục bộ.
- Dự đoán trạng thái danh sách sau mỗi vòng trước khi chạy.
- Giải thích vì sao `new Counter(99)` không tự thay phần tử trong danh sách.

### Bài 3: kiểm tra dải rỗng và lượt duyệt độc lập

Dùng `IntRange` với các cận:

```text
[2, 2)
[-2, 1)
[4, 3)
```

Dự đoán:

- Nguồn nào rỗng.
- Nguồn nào trả `-2, -1, 0`.
- Nguồn nào bị từ chối khi tạo.
- Điều gì xảy ra nếu gọi `next()` sau khi iterator đã hết.

Sau đó tạo hai iterator của cùng một dải và lấy phần tử xen kẽ để kiểm chứng trạng thái riêng.

### Bài 4: phát hiện giả định không cần thiết

Bạn có một phương thức chỉ in các phần tử nhưng tham số yêu cầu `ArrayList<String>`.

Hãy:

1. Viết lại để nhận `Iterable<String>`.
2. Giữ việc duyệt bằng enhanced for.
3. Truyền thử một danh sách, một tập hợp có thứ tự rõ và một nguồn tự định nghĩa.
4. Giải thích những thao tác không còn được giả định, như `get`, `size` hoặc `add`.

Tiêu chí hoàn thành là chọn đúng hợp đồng cần dùng, duyệt hết theo cơ chế nguồn cung cấp và không dựa vào khả năng không được giao diện cam kết.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="iteration-002">Iterator: hasNext, next và forEachRemaining</a>

<details>
<summary>Xem chi tiết</summary>

# Iterator: hasNext, next và forEachRemaining

`Iterator<E>` là đối tượng giữ trạng thái của **một lượt duyệt** các phần tử kiểu `E`. Nó giúp mã sử dụng dữ liệu lấy lần lượt từng phần tử mà không cần biết nguồn được lưu bằng mảng, danh sách liên kết, tập hợp hay được tạo ra theo một cách khác.

Ba phương thức trọng tâm:

| Phương thức | Câu hỏi hoặc thao tác |
| --- | --- |
| `boolean hasNext()` | Còn phần tử để lấy không? |
| `E next()` | Lấy phần tử tiếp theo và tiến trạng thái duyệt. |
| `void forEachRemaining(Consumer<? super E> action)` | Áp dụng hành động cho toàn bộ phần còn lại của lượt duyệt. |

Một iterator không phải bản sao của tập hợp và không tự bắt đầu lại sau khi đã hết. Cần hiểu trạng thái duyệt trước khi chọn vòng lặp thủ công hay `forEachRemaining`.

## 1. Iterable cung cấp lượt duyệt, Iterator giữ trạng thái lượt đó

Với một danh sách:

~~~java
// Đoạn mã trong một phương thức; cần import java.util.List và java.util.Iterator.
List<String> values = List.of("A", "B", "C");
Iterator<String> iterator = values.iterator();
~~~

Có hai đối tượng với hai vai trò:

- `values` là danh sách chứa hoặc cung cấp dữ liệu theo hợp đồng `List`.
- `iterator` là bộ duyệt giữ thông tin lần lấy tiếp theo của lượt duyệt này.

`Iterable<E>` là hợp đồng cung cấp `iterator()`. `Collection<E>`, `List<E>` và `Set<E>` là những ví dụ thuộc nhóm có thể duyệt theo cách đó.

`Iterator<E>` thuộc gói `java.util`, không phải `java.lang`. Tạo iterator từ một danh sách không tự tạo danh sách mới.

### Hình dung vị trí phần tử tiếp theo

Với danh sách A, B, C:

~~~text
Mới tạo:
phần tiếp theo -> A, B, C

Sau next() trả A:
đã đi qua: A
phần tiếp theo -> B, C

Sau next() trả B:
đã đi qua: A, B
phần tiếp theo -> C

Sau next() trả C:
đã đi qua: A, B, C
phần còn lại: rỗng
~~~

Đây là mô hình để đọc chương trình. Không phải mọi lớp iterator đều dùng một biến chỉ số nội bộ. Với một danh sách liên kết, cấu trúc duyệt có thể dựa vào nút; với nguồn khác, trạng thái còn có thể khác.

## 2. hasNext: hỏi còn phần tử, không lấy phần tử

~~~java
Iterator<String> iterator = List.of("A", "B").iterator();

System.out.println(iterator.hasNext()); // true
System.out.println(iterator.hasNext()); // true
System.out.println(iterator.next());    // A
~~~

Trong lượt duyệt bình thường của nguồn không bị thay đổi, gọi `hasNext()` lặp lại không làm bỏ qua A. Phương thức hỏi liệu `next()` còn có thể trả một phần tử, thay vì lấy phần tử đó cho người gọi.

Không gọi `hasNext()` với kỳ vọng nhận được chính phần tử tiếp theo. Giá trị trả về là `boolean`, không phải `E`.

### hasNext không phải cơ chế chờ

Với một iterator thông thường trên collection, `hasNext()` không có nghĩa “chờ đến khi có dữ liệu mới”. Nó cũng không tạo một đảm bảo đồng bộ rằng một luồng khác không thể thay đổi nguồn sau lần kiểm tra.

Không suy ra mọi iterator đều có cùng chi phí `hasNext()`. Một nguồn được tính toán hoặc đọc lười có thể phải chuẩn bị dữ liệu bên trong. Ý nghĩa với người sử dụng vẫn là hỏi còn phần tử, không phải cam kết mọi triển khai chỉ đọc một biến và có chi phí cố định.

### Vòng lặp không tiến thì không kết thúc

~~~java
// Ví dụ sai về logic: nếu iterator đang có phần tử, trạng thái không tiến.
while (iterator.hasNext()) {
    System.out.println("still has data");
}
~~~

Điều kiện không tự tiêu thụ dữ liệu. Một vòng duyệt cần gọi `next()` hoặc một thao tác tiêu thụ thích hợp để tiến.

## 3. next: lấy một phần tử và tiến một bước

Dạng thường gặp:

~~~java
while (iterator.hasNext()) {
    String value = iterator.next();
    System.out.println(value);
}
~~~

Mỗi vòng:

1. Kiểm tra còn phần tử.
2. Lấy đúng một phần tử.
3. Xử lý giá trị đã lấy.

Không bắt buộc phải gọi `hasNext()` ngay trước mọi `next()`. Nếu mã có cơ sở chắc chắn còn phần tử, có thể gọi `next()` trực tiếp. Nhưng trách nhiệm xử lý trường hợp không còn dữ liệu vẫn thuộc về người gọi.

Khi đã hết, `next()` ném `NoSuchElementException`; nó không tự trả `null` làm dấu kết thúc. Các hợp đồng ngoại lệ và phần còn lại được mô tả trong [Iterator API của Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html).

### Không nhầm lấy phần tử với xóa khỏi nguồn

~~~java
List<String> values = List.of("A", "B");
Iterator<String> iterator = values.iterator();

String first = iterator.next();

System.out.println(first);  // A
System.out.println(values); // [A, B]
~~~

`next()` đã đi qua A trong lượt duyệt, nhưng không xóa A khỏi danh sách.

Trong bài này, “tiêu thụ lượt duyệt” nói về trạng thái iterator. Nó không có nghĩa nguồn collection bị xóa từng phần tử.

### Gọi next hai lần là đi hai bước

~~~java
// Ví dụ lỗi thường gặp nếu muốn xử lý mỗi phần tử đúng một lần.
while (iterator.hasNext()) {
    System.out.println(iterator.next());
    process(iterator.next());
}
~~~

`process` ở đây là tên một hàm minh họa của ứng dụng, không phải API có sẵn. Đoạn mã lấy một phần tử để in rồi lấy thêm phần tử khác để xử lý. Nó có thể bỏ qua phần tử theo ý định ban đầu, và lần `next()` thứ hai có thể gặp hết dữ liệu.

Lấy một lần rồi dùng lại biến:

~~~java
while (iterator.hasNext()) {
    String value = iterator.next();
    System.out.println(value);
    process(value);
}
~~~

Các đoạn có `process` chỉ minh họa cấu trúc trong phương thức, không phải chương trình độc lập.

## 4. Null có thể là dữ liệu, không phải tín hiệu đã hết

Nếu nguồn cho phép phần tử `null`, iterator có thể trả `null` như một phần tử hợp lệ:

~~~java
// Cần import java.util.Arrays.
Iterator<String> iterator = Arrays.asList("X", null, "Y").iterator();

while (iterator.hasNext()) {
    System.out.println(iterator.next());
}
~~~

Stdout của đoạn này:

~~~text
X
null
Y
~~~

Viết điều kiện như sau là sai với nguồn có thể chứa `null`:

~~~java
// Không dùng null làm tín hiệu kết thúc Iterator.
while (iterator.next() != null) {
    // Không biểu đạt đúng việc còn phần tử.
}
~~~

Nó có thể dừng trước Y, hoặc ném ngoại lệ khi không gặp `null` trước lúc hết dữ liệu. Dùng `hasNext()` để kiểm tra kết thúc.

Phân biệt thêm:

- `List.of(...)` không chấp nhận phần tử `null`.
- `Arrays.asList(...)` có thể chứa phần tử `null`.
- Iterator không áp đặt một chính sách `null` chung cho mọi nguồn.

Vấn đề nằm ở nguồn dữ liệu và cách dùng giá trị trả về, không phải ở việc Java dùng `null` để đánh dấu hết iterator.

## 5. forEachRemaining: xử lý từ vị trí hiện tại đến hết

Ví dụ đã lấy một phần tử:

~~~java
Iterator<String> iterator = List.of("A", "B", "C", "D").iterator();

System.out.println(iterator.next()); // A
iterator.forEachRemaining(System.out::println);
~~~

Stdout:

~~~text
A
B
C
D
~~~

Dòng A đến từ `next()`. `forEachRemaining` chỉ xử lý B, C, D.

Nếu muốn xem riêng phần mà callback đã xử lý:

~~~java
Iterator<String> remainderIterator = List.of("A", "B", "C", "D").iterator();
remainderIterator.next(); // Đã lấy A.
List<String> remaining = new ArrayList<>();
remainderIterator.forEachRemaining(remaining::add);
System.out.println(remaining); // [B, C, D]
~~~

Danh sách `remaining` nhận phần chưa được lấy từ **iterator đó**. Nó không tự lấy lại các phần tử mà iterator đã đi qua.

### Ý tưởng của triển khai mặc định

Có thể đọc hành vi theo dạng vòng lặp:

~~~java
while (iterator.hasNext()) {
    action.accept(iterator.next());
}
~~~

`action` là hành động truyền vào, không phải một kết quả mới. Triển khai cụ thể có thể ghi đè phương thức để thực hiện phù hợp với cấu trúc nội bộ, nên không suy ra mọi iterator đều dùng đúng một đoạn bytecode giống vòng lặp trên.

Điều cần giữ là ý nghĩa xử lý phần còn lại và thứ tự duyệt đã được nguồn cam kết.

### Sau khi hoàn tất bình thường

Với một lượt duyệt hữu hạn trên nguồn ổn định, khi `forEachRemaining` hoàn tất bình thường:

- Phần còn lại đã được xử lý.
- `hasNext()` trả `false`.
- Gọi lại `forEachRemaining` với một hành động hợp lệ không có phần tử mới để xử lý.
- `next()` trên lượt duyệt đã hết ném `NoSuchElementException`.

Không dùng `forEachRemaining` nếu cần chỉ xử lý một phần giới hạn hoặc một nguồn có thể vô hạn. Nó không tự có điều kiện dừng nghiệp vụ ngoài việc hết dữ liệu hoặc phát sinh ngoại lệ.

## 6. Consumer: hành động trên phần tử, không phải phép biến đổi trả kết quả

Chữ ký:

~~~java
void forEachRemaining(Consumer<? super E> action)
~~~

Đây là chữ ký để đọc hiểu; không cần tự khai báo lại phương thức trong mã sử dụng iterator.

`Consumer` nhận một giá trị để thực hiện hành động và không trả về giá trị qua phương thức `accept`. Ví dụ:

~~~java
Iterator<String> iterator = List.of("A", "B").iterator();

iterator.forEachRemaining(value -> System.out.println(value));
~~~

Method reference tương đương trong ngữ cảnh này:

~~~java
iterator.forEachRemaining(System.out::println);
~~~

Nhưng nếu hai đoạn được gọi nối tiếp trên **cùng iterator**, đoạn thứ hai không tự chạy lại từ đầu. Tương đương nói về cách viết callback, không phải khả năng dùng lại dữ liệu đã đi qua.

### Consumer của kiểu cha

Vì có `? super E`, iterator của `String` có thể nhận một hành động phù hợp với `Object`:

~~~java
// Cần import java.util.function.Consumer.
Consumer<Object> print = value -> System.out.println(value);
Iterator<String> iterator = List.of("A", "B").iterator();

iterator.forEachRemaining(print);
~~~

Hành động nhận được mọi `String` vì `String` cũng là `Object`. Chiều ngược lại không an toàn: một hành động chỉ xử lý `String` không thể mặc nhiên nhận mọi phần tử từ `Iterator<Object>`.

### Không có List kết quả tự động

`forEachRemaining` trả `void`. Nếu cần tạo một danh sách mới, phải cung cấp nơi nhận:

~~~java
List<String> result = new ArrayList<>();
iterator.forEachRemaining(value -> result.add(value.toLowerCase()));
~~~

Đây là ví dụ cú pháp, giả định phần tử không `null`. Với việc chuẩn hóa chuỗi trong ứng dụng thực, cần chọn quy tắc ngôn ngữ và xử lý dữ liệu thiếu theo yêu cầu; không phải mọi phép chuyển chữ thường đều có cùng ý nghĩa nghiệp vụ.

Khi chỉ cần tích lũy hoặc biến đổi, vòng lặp thông thường cũng có thể là cách rõ ràng hơn. Không cần dùng callback cho mọi tác vụ.

## 7. Dừng sớm: vòng lặp có break, lambda return không dừng lượt duyệt

Nếu cần dừng tại B:

~~~java
Iterator<String> iterator = List.of("A", "B", "C", "D").iterator();

while (iterator.hasNext()) {
    String value = iterator.next();
    if (value.equals("B")) {
        break;
    }
    System.out.println(value);
}
~~~

A được in. B đã được lấy rồi mới làm vòng lặp dừng, nên phần còn lại của iterator là C, D.

Nếu sau đó gọi `iterator.forEachRemaining(...)`, nó bắt đầu từ C.

### Return trong lambda chỉ kết thúc lần gọi callback hiện tại

~~~java
List<String> accepted = new ArrayList<>();

List.of("A", "B", "C", "D").iterator().forEachRemaining(value -> {
    if (value.equals("B")) {
        return;
    }
    accepted.add(value);
});

System.out.println(accepted); // [A, C, D]
~~~

`return` không trả khỏi phương thức bên ngoài, không tự kết thúc `forEachRemaining`, và không mang nghĩa `break` của vòng lặp. Nó chỉ kết thúc xử lý callback cho B. Callback vẫn được gọi tiếp cho C và D.

Một lệnh `break` không thể nhảy từ bên trong lambda ra vòng lặp bên ngoài nó.

Có thể làm callback ném ngoại lệ, nhưng không nên dùng ngoại lệ giả làm lựa chọn mặc định cho điều kiện dừng bình thường. Với yêu cầu dừng sớm và tiếp tục quản lý trạng thái duyệt, vòng lặp thủ công diễn đạt mục đích trực tiếp hơn.

## 8. Ngoại lệ trong callback và công việc đã thực hiện

Ví dụ một hành động bị lỗi khi gặp B:

~~~java
List<String> completed = new ArrayList<>();
Iterator<String> iterator = List.of("A", "B", "C").iterator();

try {
    iterator.forEachRemaining(value -> {
        if (value.equals("B")) {
            throw new IllegalStateException("stop at B");
        }
        completed.add(value);
    });
} catch (IllegalStateException exception) {
    System.out.println(exception.getMessage());
}

System.out.println(completed); // [A]
~~~

Ngoại lệ của callback được đưa về cho người gọi. Hành động đã thực hiện với A không tự được hoàn tác. `forEachRemaining` không tạo một giao dịch cho toàn bộ dữ liệu.

Không nên sau đó suy đoán chắc chắn iterator đang đứng trước B, sau B hay tại C rồi tiếp tục xử lý theo giả định đó. Hợp đồng chung không bảo đảm hành vi tiếp theo của iterator sau khi callback ném ngoại lệ.

Nếu công việc cần retry:

- Xác định rõ phần nào đã thành công ở tầng nghiệp vụ.
- Không dùng vị trí nội bộ không được bảo đảm của iterator làm checkpoint.
- Thiết kế xử lý lặp an toàn hoặc lưu định danh/checkpoint theo yêu cầu.
- Tạo lại một lượt đọc phù hợp khi nguồn cho phép.

Đối với bài toán collection nhỏ trong bộ nhớ, có thể đơn giản chọn vòng lặp thủ công và xử lý lỗi rõ ở từng phần tử, thay vì cố tiếp tục một iterator vừa thất bại trong callback.

### Action không được null

~~~java
// Đoạn mã minh họa lỗi trên một iterator mới.
List.of("A").iterator().forEachRemaining(null);
~~~

Lời gọi ném `NullPointerException`. Phân biệt `action == null` với một phần tử nguồn là `null`: cái đầu là thiếu hành động, cái sau có thể là dữ liệu hợp lệ nếu nguồn cho phép.

## 9. Đừng thay đổi nguồn trong callback để “duyệt và sửa” tùy ý

Một hành động như sau không có ngữ nghĩa an toàn chung:

~~~java
// Ví dụ không nên dùng với một List thông thường.
iterator.forEachRemaining(value -> values.remove(value));
~~~

Việc thay đổi collection khi đang duyệt phải theo chính sách của iterator và nguồn cụ thể. Hợp đồng chung của `forEachRemaining` không bảo đảm hành vi nếu callback sửa collection, kể cả gọi thao tác thay đổi qua iterator, trừ khi một triển khai có quy định riêng.

Nếu mục tiêu là lọc phần tử, hãy xem một cơ chế phù hợp như:

- Vòng lặp iterator tường minh với `remove()` khi iterator thực sự hỗ trợ.
- `Collection.removeIf(...)` khi collection hỗ trợ thay đổi và điều kiện xử lý phù hợp.
- Tạo collection kết quả mới mà không sửa nguồn đang duyệt.

`Iterator.remove()` là thao tác tùy chọn. Không phải mọi iterator đều hỗ trợ, và việc gọi nó sau `forEachRemaining` không có bảo đảm chung để dùng làm cách xóa phần tử cuối đã xử lý.

Những iterator phát hiện thay đổi có thể ném `ConcurrentModificationException`, nhưng không dùng ngoại lệ đó như một cơ chế đồng bộ hoặc bằng chứng chắc chắn mọi thay đổi sai đều được phát hiện.

## 10. Một iterator dùng một lượt, tạo iterator mới không sao chép dữ liệu

~~~java
List<String> values = List.of("A", "B");

Iterator<String> first = values.iterator();
Iterator<String> second = values.iterator();

System.out.println(first.next());  // A
System.out.println(first.next());  // B
System.out.println(second.next()); // A
~~~

Hai iterator của danh sách này có trạng thái duyệt riêng. `second` chưa đi qua phần tử nào khi `first` đã đi đến cuối.

Tuy nhiên, cả hai cùng đọc nguồn `values`. Chúng không phải hai bản sao độc lập của các phần tử hay của collection.

### Gán biến khác không tạo iterator mới

~~~java
Iterator<String> first = values.iterator();
Iterator<String> alias = first;

System.out.println(first.next()); // A
System.out.println(alias.next()); // B
~~~

`alias` và `first` cùng tham chiếu một iterator. Việc một biến gọi `next` thay đổi trạng thái mà biến kia quan sát được.

Nếu cần hai lượt độc lập, tạo hai iterator từ nguồn có hỗ trợ. Không mặc nhiên mọi nguồn `Iterable` đều cung cấp một snapshot giống nhau qua nhiều lần duyệt; với ví dụ `List.of` không thay đổi ở đây, nội dung giữa các lượt là xác định.

### Bẫy tạo lại iterator trong vòng lặp

~~~java
// Ví dụ sai nếu values không rỗng và không bị thay đổi.
while (values.iterator().hasNext()) {
    System.out.println(values.iterator().next());
}
~~~

Mỗi lần lại tạo một iterator từ đầu. Vòng lặp không duy trì tiến độ, nên có thể in mãi phần tử đầu.

Tạo iterator một lần trước vòng lặp:

~~~java
Iterator<String> iterator = values.iterator();
while (iterator.hasNext()) {
    System.out.println(iterator.next());
}
~~~

## 11. Quan hệ với for-each và forEach của nguồn

For-each trên một nguồn `Iterable` có ý tưởng duyệt tương ứng:

~~~java
for (String value : values) {
    System.out.println(value);
}
~~~

Có thể đọc gần với:

~~~java
for (Iterator<String> iterator = values.iterator(); iterator.hasNext(); ) {
    String value = iterator.next();
    System.out.println(value);
}
~~~

Đây là giải thích cho trường hợp nguồn `Iterable`; for-each còn hỗ trợ mảng bằng cơ chế ngôn ngữ riêng. Mảng không vì vậy mà triển khai `Iterable`. Quy tắc được nêu trong [JLS 17, enhanced for statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.14.2).

Điểm khác về phạm vi sử dụng:

| Cách duyệt | Điểm bắt đầu trong ví dụ List | Điều khiển dừng |
| --- | --- | --- |
| For-each trên `values` | Một lượt duyệt của danh sách | Có `break` và `continue`. |
| `values.forEach(action)` | Xử lý danh sách theo hợp đồng của nguồn | Callback, không có `break` thoát khỏi lời gọi. |
| `iterator.forEachRemaining(action)` | Vị trí hiện tại của iterator đã có | Callback cho đến hết hoặc có ngoại lệ. |
| `while (iterator.hasNext())` | Vị trí hiện tại của iterator đã có | Chủ động từng bước và dừng sớm bằng `break`. |

Nếu đã gọi `iterator.next()`, sau đó gọi `values.forEach(...)` trên danh sách nguồn, thao tác nguồn không tự dùng tiến độ của iterator riêng đó. Ngược lại, gọi `iterator.forEachRemaining(...)` sẽ dùng đúng tiến độ hiện tại.

Chọn cách viết dựa trên mức điều khiển cần thiết. Chỉ cần duyệt toàn bộ nguồn thì for-each thường gọn. Cần chia một lượt thành nhiều giai đoạn, dừng sớm hoặc truyền phần còn lại sang một hàm thì iterator tường minh có ý nghĩa hơn.

## 12. Chương trình hoàn chỉnh

Lưu thành **IteratorMethodsDemo.java**. Phần `Countdown` minh họa một iterator tự tạo có nguồn là trạng thái số đếm, không cần một collection chứa sẵn mọi phần tử.

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class IteratorMethodsDemo {
    public static void main(String[] args) {
        List<String> letters = List.of("A", "B", "C", "D");
        Iterator<String> iterator = letters.iterator();

        System.out.println("hasNext first = " + iterator.hasNext());
        System.out.println("hasNext again = " + iterator.hasNext());
        System.out.println("next = " + iterator.next());

        List<String> remaining = new ArrayList<>();
        iterator.forEachRemaining(remaining::add);
        System.out.println("Remaining = " + remaining);
        System.out.println("hasNext after drain = " + iterator.hasNext());

        List<String> secondDrain = new ArrayList<>();
        iterator.forEachRemaining(secondDrain::add);
        System.out.println("Second drain = " + secondDrain);

        try {
            iterator.next();
        } catch (NoSuchElementException exception) {
            System.out.println("next when exhausted = "
                    + exception.getClass().getSimpleName());
        }

        Iterator<String> fresh = letters.iterator();
        System.out.println("Fresh iterator next = " + fresh.next());
        System.out.println("Original iterator still exhausted = "
                + !iterator.hasNext());

        Iterator<String> withNull = Arrays.asList("X", null, "Y").iterator();
        List<String> nullData = new ArrayList<>();
        while (withNull.hasNext()) {
            nullData.add(withNull.next());
        }
        System.out.println("Null is data = " + nullData);

        Iterator<String> partial = letters.iterator();
        while (partial.hasNext()) {
            String value = partial.next();
            if (value.equals("B")) {
                break;
            }
        }
        List<String> afterBreak = new ArrayList<>();
        partial.forEachRemaining(afterBreak::add);
        System.out.println("Remaining after break at B = " + afterBreak);

        List<String> accepted = new ArrayList<>();
        letters.iterator().forEachRemaining(value -> {
            if (value.equals("B")) {
                return;
            }
            accepted.add(value);
        });
        System.out.println("Lambda return skips B only = " + accepted);

        List<String> beforeFailure = new ArrayList<>();
        Iterator<String> failing = letters.iterator();
        try {
            failing.forEachRemaining(value -> {
                if (value.equals("B")) {
                    throw new IllegalStateException("stop at B");
                }
                beforeFailure.add(value);
            });
        } catch (IllegalStateException exception) {
            System.out.println("Consumer error = " + exception.getMessage());
        }
        System.out.println("Completed actions before error = " + beforeFailure);
        // Do not use the failing iterator again.

        try {
            letters.iterator().forEachRemaining(null);
        } catch (NullPointerException exception) {
            System.out.println("Null action = "
                    + exception.getClass().getSimpleName());
        }

        Iterator<Integer> countdown = new Countdown(3);
        System.out.println("Countdown next = " + countdown.next());
        List<Integer> tail = new ArrayList<>();
        countdown.forEachRemaining(tail::add);
        System.out.println("Countdown remaining = " + tail);
        System.out.println("Countdown exhausted = " + !countdown.hasNext());
        System.out.println("Source unchanged = " + letters);
    }

    private static final class Countdown implements Iterator<Integer> {
        private int remaining;

        private Countdown(int start) {
            if (start < 0) {
                throw new IllegalArgumentException("start must be non-negative");
            }
            remaining = start;
        }

        @Override
        public boolean hasNext() {
            return remaining > 0;
        }

        @Override
        public Integer next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return remaining--;
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~shell
javac --release 17 -encoding UTF-8 IteratorMethodsDemo.java
java IteratorMethodsDemo
~~~

Stdout:

~~~text
hasNext first = true
hasNext again = true
next = A
Remaining = [B, C, D]
hasNext after drain = false
Second drain = []
next when exhausted = NoSuchElementException
Fresh iterator next = A
Original iterator still exhausted = true
Null is data = [X, null, Y]
Remaining after break at B = [C, D]
Lambda return skips B only = [A, C, D]
Consumer error = stop at B
Completed actions before error = [A]
Null action = NullPointerException
Countdown next = 3
Countdown remaining = [2, 1]
Countdown exhausted = true
Source unchanged = [A, B, C, D]
~~~

### Giải thích từng nhóm kết quả

**Hai lần hasNext**

Cả hai trả `true`, và `next` vẫn lấy A. Việc hỏi còn dữ liệu không tự đi qua A.

**Remaining và Second drain**

A đã được lấy trước, nên `forEachRemaining` thu B, C, D. Lần xử lý tiếp theo có danh sách kết quả rỗng vì iterator đã hết.

**next khi hết**

Chương trình bắt đúng `NoSuchElementException` và in tên ngoại lệ. Không dùng ngoại lệ này làm cách điều khiển thông thường thay cho `hasNext`.

**Fresh iterator**

Iterator mới từ danh sách bắt đầu ở A; iterator cũ vẫn đã hết. Hai trạng thái riêng không làm danh sách nguồn bị sao chép hoặc thay đổi.

**Null is data**

Iterator đi qua X, `null`, Y. Điều kiện vòng lặp kiểm tra `hasNext`, nên không dừng nhầm ở phần tử `null`.

**Break và lambda return**

Vòng lặp lấy B rồi dừng, để lại C, D. Lambda chỉ bỏ hành động thêm B, vẫn thêm A, C, D khi các callback tương ứng chạy.

**Callback thất bại**

Hành động với A đã hoàn tất trước khi lỗi xảy ra ở B. Chương trình không sử dụng lại iterator `failing`, tránh dựa vào hành vi tiếp theo không được hợp đồng chung bảo đảm.

**Countdown**

`next()` lấy `3`, sau đó `forEachRemaining` nhận `2, 1`. Lớp chỉ cài `hasNext` và `next`, nên sử dụng phương thức mặc định cho phần còn lại. Khi không còn giá trị, `next` tự kiểm tra và ném đúng ngoại lệ thay vì trả một giá trị giả.

**Source unchanged**

Những lời gọi lấy hoặc duyệt phần tử không xóa nội dung danh sách. Các danh sách phụ được dùng để thu kết quả, còn nguồn vẫn là A, B, C, D.

## 13. Tự tạo iterator: những trách nhiệm tối thiểu

Một iterator tự cài đặt cần có mô hình trạng thái rõ:

- Phần tử tiếp theo là gì.
- Khi nào hết.
- `hasNext` xác định còn phần tử như thế nào.
- `next` chuyển trạng thái ra sao và xử lý khi đã hết.
- Nguồn có cho phép sửa trong khi duyệt hay không.

Ví dụ `Countdown` nhận số bắt đầu không âm. Trạng thái `remaining` vừa cho biết giá trị tiếp theo, vừa xác định đã hết khi bằng `0`. Không có phần tử số `0` trong miền của iterator mẫu này.

Không cần ghi đè `forEachRemaining` chỉ để có khả năng sử dụng nó. Nếu ghi đè để tối ưu, vẫn phải tôn trọng các hợp đồng liên quan của phương thức, bao gồm xử lý hành động `null` và thứ tự nếu iterator đã cam kết.

Một interface `Iterator` không tự cung cấp `reset()`, `previous()`, `size()` hoặc `close()`. Nhu cầu duyệt ngược, biết số lượng hay quản lý tài nguyên cần xem hợp đồng và lớp cụ thể. Chẳng hạn, `ListIterator` có thêm khả năng phù hợp với danh sách, nhưng không phải mọi iterator đều là `ListIterator`.

Đặc biệt, đừng giả định chỉ cần giữ một `Iterator` là đã quản lý đầy đủ vòng đời của tài nguyên bên ngoài như tệp hoặc kết nối. Hợp đồng tài nguyên phải được xử lý ở cấp API đã tạo ra nguồn đó.

## 14. Kiểm thử và lỗi thường gặp

Các ca kiểm thử cơ bản:

1. Nguồn rỗng: `hasNext` là `false`; callback hợp lệ không được gọi; `next` ném ngoại lệ.
2. Một phần tử: nhiều lần `hasNext` trước `next` không làm mất phần tử.
3. Nhiều phần tử: lấy một vài phần tử rồi `forEachRemaining` chỉ xử lý phần dư đúng thứ tự được cam kết.
4. Phần tử `null` khi nguồn cho phép: không nhầm với kết thúc.
5. Hành động `null`: bị từ chối bằng ngoại lệ.
6. Callback có lỗi: lỗi được chuyển về người gọi; không viết ca kiểm thử ép một vị trí tiếp tục mà hợp đồng không bảo đảm.
7. Hai iterator và hai biến cùng trỏ một iterator: phân biệt trạng thái riêng với trạng thái dùng chung.

| Lỗi | Hệ quả | Cách sửa |
| --- | --- | --- |
| Chỉ gọi `hasNext` trong vòng lặp | Không tiến trạng thái, có thể lặp vô hạn | Lấy phần tử bằng `next` đúng vị trí. |
| Gọi `next` hai lần cho cùng một bước xử lý | Bỏ qua phần tử hoặc gặp hết dữ liệu bất ngờ | Lưu giá trị vào biến rồi dùng lại. |
| Dùng `null` làm dấu hết | Dừng sai với nguồn có phần tử `null` | Dùng `hasNext`. |
| Nghĩ `next` xóa phần tử khỏi collection | Nhầm trạng thái duyệt với dữ liệu nguồn | Phân biệt lấy qua iterator với thao tác thay đổi nguồn. |
| Nghĩ `forEachRemaining` chạy lại từ đầu | Bỏ qua phần đã tiêu thụ hoặc kỳ vọng sai kết quả | Xét vị trí hiện tại của đúng iterator. |
| Dùng `return` trong lambda để dừng toàn bộ | Các phần tử sau vẫn được xử lý | Dùng vòng lặp tường minh khi cần `break`. |
| Tạo lại iterator mỗi lần trong điều kiện hoặc thân vòng lặp | Không duy trì được tiến độ | Tạo một iterator cho một lượt. |
| Sửa collection trong callback mà không xét hợp đồng | Có thể có hành vi không được bảo đảm hoặc ngoại lệ | Chọn cơ chế sửa phù hợp với nguồn. |
| Tiếp tục iterator sau callback ném lỗi theo một vị trí tự đoán | Có thể lặp, bỏ sót hoặc xử lý sai dữ liệu | Thiết kế xử lý lỗi và checkpoint ở tầng thích hợp. |

## 15. Thực hành

1. Với nguồn A, B, C, D, E, gọi `next` hai lần rồi thu `forEachRemaining` vào danh sách mới. Dự đoán và chạy kiểm tra phần còn lại.
2. Viết vòng lặp xử lý tối đa hai phần tử nhưng giữ iterator để một đoạn mã sau xử lý phần dư.
3. Tạo nguồn có `null` ở đầu, giữa và cuối. Chứng minh vòng lặp dựa trên `hasNext` vẫn duyệt đúng số phần tử.
4. So sánh hai iterator độc lập với hai biến cùng trỏ một iterator bằng các lời gọi `next` xen kẽ.
5. Chạy một callback có `return` ở điều kiện cụ thể và chứng minh nó chỉ bỏ qua hành động hiện tại.
6. Viết một iterator đếm tăng trong một khoảng hữu hạn, kiểm tra nguồn rỗng và `next` sau khi hết. Dùng luôn `forEachRemaining` mặc định.
7. Với yêu cầu “dừng ở phần tử khớp điều kiện”, xác định phần tử khớp đã bị lấy khỏi lượt duyệt chưa và viết kiểm thử phần dư tương ứng.

Một lượt duyệt có một trạng thái tiến dần. `hasNext` kiểm tra, `next` lấy một bước, còn `forEachRemaining` xử lý phần dư. Khi giữ rõ ba vai trò này, các tình huống dừng sớm, dùng lại biến và nối nhiều giai đoạn xử lý sẽ dễ suy luận hơn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="iteration-003">Iterator.remove và điều kiện gọi hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Iterator.remove và điều kiện gọi hợp lệ

Khi vừa duyệt vừa cần xóa phần tử khỏi một collection, `Iterator.remove()` cung cấp một thao tác gắn với **chính quá trình duyệt**.

Nó không nhận phần tử hoặc chỉ số. Nếu được hỗ trợ, nó xóa khỏi collection nền **phần tử do lần next() gần nhất của iterator đó trả về**, với giới hạn một lần xóa cho mỗi lần lấy phần tử.

Muốn sử dụng đúng, cần phân biệt ba câu hỏi:

1. Iterator này có hỗ trợ xóa không?
2. Trạng thái hiện tại đã cho phép gọi remove chưa?
3. Collection có bị sửa qua một đường khác làm mất bảo đảm của quá trình duyệt không?

## 1. Vì sao có thao tác xóa trên iterator?

Giả sử danh sách số gồm:

```text
[-2, 0, 3, -1, 4]
```

Ta muốn duyệt từng số và xóa số âm ngay tại chỗ. Iterator đang theo dõi vị trí để lấy phần tử tiếp theo. Việc xóa có thể làm các phần tử phía sau dịch chuyển hoặc thay đổi liên kết bên trong collection.

Nếu dùng thao tác xóa của chính iterator, lớp triển khai có cơ hội cập nhật trạng thái duyệt phù hợp với việc xóa vừa thực hiện.

Mẫu minh họa trong một phương thức:

```java
Iterator<Integer> iterator = numbers.iterator();

while (iterator.hasNext()) {
    int number = iterator.next();
    if (number < 0) {
        iterator.remove();
    }
}
```

Ở đây, `numbers` là một danh sách có iterator hỗ trợ xóa. Thao tác xóa gắn với số vừa được trả về, không cần tìm lại số đó theo giá trị.

Điều này hữu ích cả khi có nhiều phần tử bằng nhau: iterator biết lần xuất hiện nào đã được duyệt tới.

## 2. Ba phương thức và ba vai trò khác nhau

| Phương thức | Vai trò | Có tạo một phần tử mới đủ điều kiện xóa không? |
| --- | --- | --- |
| `hasNext()` | Hỏi còn phần tử để lấy không | Không |
| `next()` | Lấy phần tử tiếp theo | Có, nếu lấy thành công |
| `remove()` | Xóa phần tử đã được lấy gần nhất | Dùng quyền xóa của phần tử đó |

`remove()` có kiểu trả về `void`. Nó không trả phần tử bị xóa và không trả boolean.

Đây là thao tác **tùy chọn** của interface `Iterator`. Nếu iterator không hỗ trợ, lời gọi có thể bị từ chối bằng `UnsupportedOperationException`.

Các quy định chính thức về phần tử bị xóa, số lần gọi, ngoại lệ và thay đổi collection được nêu trong [API Iterator.remove của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html#remove()).

## 3. Nhìn remove như một máy trạng thái nhỏ

Với iterator hỗ trợ xóa và không bị sửa collection trái hợp đồng, có thể dùng hai trạng thái:

| Trạng thái | Ý nghĩa |
| --- | --- |
| Chưa có phần tử được phép xóa | Chưa next thành công, hoặc phần tử gần nhất đã bị remove |
| Có phần tử được phép xóa | Next đã trả một phần tử và phần tử đó chưa bị remove |

Các chuyển trạng thái:

```text
Mới tạo iterator
    |
    v
CHƯA ĐƯỢC REMOVE
    |
    | next() thành công
    v
ĐƯỢC REMOVE PHẦN TỬ VỪA TRẢ VỀ
    |
    | remove() thành công
    v
CHƯA ĐƯỢC REMOVE
```

Có thể gọi `next()` lần nữa mà chưa xóa phần tử cũ. Khi đó, mục tiêu của remove chuyển sang phần tử mới được trả về. Không có việc tích lũy nhiều lượt xóa.

### 3.1. Chưa next thì không có mục tiêu xóa

Đoạn sau sai trạng thái đối với iterator hỗ trợ remove:

```java
Iterator<String> iterator = values.iterator();
iterator.remove();
```

Chưa phần tử nào được iterator trả về. Không được diễn giải remove là “xóa phần tử đầu tiên” hoặc “xóa phần tử sắp được đọc”.

Với iterator của `ArrayList`, tình huống này ném `IllegalStateException`.

### 3.2. Một lần next chỉ cho một lần remove

Chuỗi hợp lệ:

```text
next -> remove -> next -> remove
```

Chuỗi sai trạng thái:

```text
next -> remove -> remove
```

Lần remove thứ hai không có phần tử mới để xóa. Nó không có nghĩa là tiếp tục xóa phần tử kế tiếp.

Nếu muốn xóa phần tử tiếp theo, phải lấy nó bằng next rồi quyết định xóa.

### 3.3. Nhiều lần next không tạo kho lượt remove

Với dữ liệu:

```text
[A, B, C]
```

sau hai lần next trả A rồi B, một lần remove xóa B. Không thể gọi remove tiếp để quay lại xóa A.

Nếu cần quyết định xóa A, phải thực hiện khi A vẫn là phần tử vừa được trả về, hoặc chọn một chiến lược xử lý khác.

### 3.4. hasNext không thay thế next

Gọi `hasNext()` một hoặc nhiều lần không tạo ra phần tử được trả về mới.

Sau:

```text
next trả A -> hasNext -> hasNext -> remove
```

remove vẫn hướng tới A nếu chưa có thao tác khác làm trạng thái không hợp lệ.

Tương tự, sau khi lấy phần tử cuối, `hasNext()` có thể trả false nhưng remove vẫn hợp lệ đối với phần tử cuối vừa lấy. “Không còn phần tử phía sau” khác với “không còn phần tử gần nhất để xóa”.

Một lần next thất bại không trả một phần tử mới; không dùng lời gọi đó như một cách cấp thêm lượt remove.

## 4. Chương trình hoàn chỉnh về trạng thái hợp lệ

Lưu vào **IteratorRemoveStateDemo.java**. Chương trình dùng `ArrayList` để các thao tác xóa được hỗ trợ và tương thích Java 17 trở lên.

```java
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class IteratorRemoveStateDemo {
    public static void main(String[] args) {
        List<String> values =
                new ArrayList<>(List.of("A", "B", "A", "C"));
        Iterator<String> iterator = values.iterator();

        try {
            iterator.remove();
        } catch (IllegalStateException ex) {
            System.out.println("Before next: " + ex.getClass().getSimpleName());
        }

        System.out.println("Next: " + iterator.next());
        System.out.println("hasNext: " + iterator.hasNext());
        iterator.remove();
        System.out.println("After first remove: " + values);

        try {
            iterator.remove();
        } catch (IllegalStateException ex) {
            System.out.println("Twice: " + ex.getClass().getSimpleName());
        }

        System.out.println("Skip removal for: " + iterator.next());
        System.out.println("Next: " + iterator.next());
        iterator.remove();
        System.out.println("After removing second A: " + values);

        System.out.println("Last next: " + iterator.next());
        System.out.println("hasNext at end: " + iterator.hasNext());
        iterator.remove();
        System.out.println("After remove at end: " + values);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 IteratorRemoveStateDemo.java
java IteratorRemoveStateDemo
```

Stdout:

```text
Before next: IllegalStateException
Next: A
hasNext: true
After first remove: [B, A, C]
Twice: IllegalStateException
Skip removal for: B
Next: A
After removing second A: [B, C]
Last next: C
hasNext at end: false
After remove at end: [B]
```

### 4.1. Đối chiếu từng giai đoạn

**Trước next:** chưa có phần tử vừa được lấy, nên remove báo sai trạng thái.

**Sau next trả A:** có mục tiêu xóa. Lời gọi hasNext chỉ hỏi phần tử tiếp theo, nên không làm mất mục tiêu A.

**Sau lần xóa đầu:** danh sách còn `[B, A, C]`. Iterator tiếp tục lấy B, không bỏ qua nó vì việc xóa đã được thực hiện qua chính iterator.

**Hai remove liên tiếp:** lần thứ hai báo `IllegalStateException`.

**B rồi A:** chương trình không xóa B. Khi next tiếp tục trả A, remove hướng tới A mới đó.

**Sau phần tử cuối C:** hasNext false nhưng C chưa bị xóa, nên một remove vẫn hợp lệ. Kết quả còn B.

Các ngoại lệ được bắt trong chương trình chỉ để quan sát hợp đồng. Trong mã ứng dụng bình thường, nên tổ chức luồng gọi đúng thay vì dùng ngoại lệ như cách điều khiển vòng lặp.

## 5. remove xóa lần xuất hiện vừa duyệt, không tìm theo giá trị

Xét:

```text
[A, B, A]
```

Nếu iterator đã đi qua A đầu tiên, B, rồi A thứ hai, `iterator.remove()` nhắm tới **A thứ hai**.

Trong khi đó, `list.remove("A")` trên `ArrayList` tìm lần xuất hiện đầu phù hợp theo giá trị. Dù cùng là “xóa A”, hai lời gọi có thể tác động lên hai vị trí khác nhau.

Ngoài ra, với `List<Integer>`, `list.remove(number)` còn có thể bị chọn overload xóa theo chỉ số nếu `number` có kiểu int. `Iterator.remove()` không có tham số, nên không có nhầm lẫn chọn giá trị hay chỉ số ở lời gọi đó.

Thao tác xóa khỏi collection cũng không tự hủy đối tượng mà mọi nơi khác đang tham chiếu. Một biến cục bộ đã nhận đối tượng từ next vẫn có thể tham chiếu đến đối tượng sau khi phần tử được loại khỏi collection.

## 6. Vì sao không gọi collection.remove trong khi đang duyệt?

Mẫu sau không phải cách thay thế an toàn chung:

```java
// Đoạn minh họa cách dùng sai đối với ArrayList thông thường.
for (String value : values) {
    if ("A".equals(value)) {
        values.remove(value);
    }
}
```

Vòng for-each đang dùng một quá trình duyệt phía sau. Lời gọi remove ở đây đi thẳng vào danh sách, không phải thao tác remove của iterator đó.

Với `ArrayList`, iterator có cơ chế phát hiện một số thay đổi cấu trúc ngoài đường được hỗ trợ và có thể ném `ConcurrentModificationException`. Ngoại lệ này không đòi phải có nhiều luồng: một luồng tự sửa danh sách trong lúc duyệt cũng có thể gây ra.

Tuy nhiên, phát hiện kiểu fail-fast chỉ là cơ chế hỗ trợ tìm lỗi theo khả năng tốt nhất. Một vòng lặp sai không báo ngoại lệ ở một dữ liệu nhỏ không trở thành cách dùng đúng. Không viết tính đúng đắn của chương trình dựa vào việc chắc chắn sẽ bắt được `ConcurrentModificationException`. Xem [API ArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html).

Cách rõ ràng khi cần xóa trong một lượt duyệt là giữ một iterator tường minh và gọi remove của chính nó.

### Hai iterator cũng là hai trạng thái riêng

Nếu hai iterator cùng duyệt một danh sách thông thường, việc iterator thứ nhất xóa hợp lệ không tự cập nhật trạng thái của iterator thứ hai.

“Xóa qua iterator” phải hiểu là xóa qua **iterator đang thực hiện lượt duyệt đó**, không phải miễn gọi một remove từ bất kỳ iterator nào cũng an toàn cho mọi lượt duyệt khác.

Nếu lớp collection có chính sách duyệt đồng thời riêng, phải theo hợp đồng của lớp đó; không áp dụng một quy tắc fail-fast cho tất cả iterator trong JDK.

## 7. Hỗ trợ sửa collection và hỗ trợ iterator.remove là hai việc khác

Một đối tượng có phương thức `remove` không có nghĩa mọi iterator của nó đều cho xóa.

| Nguồn iterator thường gặp | Iterator.remove | Lý do cần nhớ |
| --- | --- | --- |
| `new ArrayList<>(...)` | Có hỗ trợ | Có thể loại phần tử và cập nhật quá trình duyệt |
| `Arrays.asList(...)` | Không hỗ trợ xóa cấu trúc | Danh sách có kích thước cố định |
| `List.of(...)` | Không hỗ trợ | Danh sách không cho sửa |
| `CopyOnWriteArrayList` | Không hỗ trợ trên iterator | Iterator quan sát một ảnh chụp |
| View từ một map có thể sửa như `LinkedHashMap.entrySet()` | Có thể hỗ trợ | Xóa ánh xạ qua view của map |

Phải đọc hợp đồng của nguồn cụ thể thay vì suy luận từ kiểu khai báo `Iterator<E>`.

### 7.1. Kích thước cố định khác hoàn toàn không cho sửa

`Arrays.asList` trả danh sách liên kết với mảng, có thể thay phần tử bằng set nhưng không thể giảm kích thước bằng remove. Việc iterator xóa bị từ chối không chứng minh mọi thao tác cập nhật đều bị cấm.

Nếu cần lọc và giảm số phần tử, có thể tạo `new ArrayList<>(source)` để làm việc trên một danh sách có cấu trúc riêng. Xem [API Arrays.asList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)).

### 7.2. Collection có thể sửa nhưng iterator chỉ đọc ảnh chụp

`CopyOnWriteArrayList` có thể thêm hoặc xóa qua các phương thức của chính collection. Iterator của nó đi qua ảnh chụp tại thời điểm tạo và không hỗ trợ remove.

Vì vậy, “collection có thể xóa” không kéo theo “iterator có thể xóa”. Nếu cần thay đổi một collection có chính sách đồng thời, chọn thao tác được lớp đó quy định thay vì cố dùng một mẫu cho mọi nguồn. Xem [API CopyOnWriteArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html).

### 7.3. Không trộn lỗi khả năng với lỗi trạng thái

`IllegalStateException` thường phản ánh thứ tự gọi sai khi thao tác được hỗ trợ. `UnsupportedOperationException` phản ánh iterator không cung cấp thao tác đó.

Nếu một lời gọi vừa sai trạng thái vừa nằm trên iterator không hỗ trợ remove, không nên dựa vào một ưu tiên ngoại lệ chung để đoán tình trạng. Kiểm tra khả năng hỗ trợ bằng tài liệu, và viết thứ tự gọi hợp lệ.

## 8. Chương trình hoàn chỉnh: lọc dữ liệu và quan sát khả năng hỗ trợ

Lưu vào **IteratorRemoveUsageDemo.java**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class IteratorRemoveUsageDemo {
    private static void showUnsupported(
            String label, Iterable<String> source) {
        Iterator<String> iterator = source.iterator();
        iterator.next();
        try {
            iterator.remove();
        } catch (UnsupportedOperationException ex) {
            System.out.println(label + ": " + ex.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        List<Integer> numbers =
                new ArrayList<>(List.of(-2, 0, 3, -1, 4));
        int removed = 0;
        Iterator<Integer> iterator = numbers.iterator();
        while (iterator.hasNext()) {
            int number = iterator.next();
            if (number < 0) {
                iterator.remove();
                removed++;
            }
        }
        System.out.println("Numbers: " + numbers);
        System.out.println("Removed negatives: " + removed);

        Map<String, Integer> stock = new LinkedHashMap<>();
        stock.put("pen", 0);
        stock.put("book", 3);
        stock.put("bag", 0);

        Iterator<Map.Entry<String, Integer>> entries =
                stock.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, Integer> entry = entries.next();
            if (entry.getValue() == 0) {
                entries.remove();
            }
        }
        System.out.println("Stock: " + stock);

        showUnsupported("Arrays.asList", Arrays.asList("A", "B"));
        showUnsupported("List.of", List.of("A", "B"));

        CopyOnWriteArrayList<String> snapshots =
                new CopyOnWriteArrayList<>(List.of("A", "B"));
        showUnsupported("CopyOnWrite iterator", snapshots);
        snapshots.remove("A");
        System.out.println("CopyOnWrite direct remove: " + snapshots);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 IteratorRemoveUsageDemo.java
java IteratorRemoveUsageDemo
```

Stdout:

```text
Numbers: [0, 3, 4]
Removed negatives: 2
Stock: {book=3}
Arrays.asList: UnsupportedOperationException
List.of: UnsupportedOperationException
CopyOnWrite iterator: UnsupportedOperationException
CopyOnWrite direct remove: [B]
```

### 8.1. Lọc số âm

Vòng lặp gọi next đúng một lần trong mỗi lượt. Chỉ những số âm mới được remove, nên các số 0, 3 và 4 được giữ.

Biến đếm tăng sau khi remove thành công. Nó không tăng dựa vào kết quả boolean vì `Iterator.remove()` không trả boolean.

### 8.2. Xóa ánh xạ qua entrySet

Map không có phương thức iterator trực tiếp; chương trình lấy iterator từ view `entrySet()`.

Khi `entries.remove()` loại entry có số lượng 0, nó xóa ánh xạ tương ứng khỏi `stock`. Sau lượt duyệt, chỉ còn `book=3`.

Đây không phải xóa khỏi một bản sao entry tách rời. View gắn với map nền. Khả năng loại entry phụ thuộc map và view có hỗ trợ thao tác hay không; xem [API Map.entrySet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#entrySet()).

Sau khi đã xóa entry, không giữ nó như một cách chắc chắn để tiếp tục cập nhật cùng ánh xạ trong map. Nếu cần dữ liệu cho báo cáo, lấy các giá trị cần thiết theo hợp đồng trước hoặc trong lúc xử lý.

### 8.3. Phân biệt iterator với collection

Hàm `showUnsupported` gọi next trước, rồi mới thử remove. Nhờ đó, nó không cố tình tạo lỗi chưa đọc phần tử.

Ba nguồn trong ví dụ từ chối thao tác vì khả năng của iterator. Cuối cùng, xóa trực tiếp trên `CopyOnWriteArrayList` vẫn thành công sau lượt thử, cho thấy collection có thể sửa dù iterator không cung cấp remove.

Các ví dụ không dùng việc bắt ngoại lệ để suy ra mọi đặc tính của nguồn. Chúng kiểm chứng những hợp đồng đã biết của các lớp cụ thể.

## 9. forEachRemaining không phải đường tắt để gọi remove

Có thể nhìn phần triển khai mặc định của `forEachRemaining` như một lượt lấy các phần tử còn lại rồi gọi hành động. Tuy nhiên, không vì vậy mà suy ra luôn được gọi remove bên trong hành động hoặc ngay sau khi kết thúc nó.

Hợp đồng `Iterator` nêu rằng:

- Hành vi không được xác định chung nếu action sửa collection, kể cả thông qua remove, trừ khi lớp ghi đè có chính sách riêng.
- Hành vi không được xác định chung nếu remove được gọi sau một lần forEachRemaining.

Vì vậy, nếu mục đích là duyệt và loại phần tử có điều kiện theo hợp đồng iterator thông thường, dùng vòng:

```java
while (iterator.hasNext()) {
    String value = iterator.next();
    if ("obsolete".equals(value)) {
        iterator.remove();
    }
}
```

Đây là đoạn minh họa với một `Iterator<String>` hỗ trợ remove.

Không dựa vào một lớp triển khai hoặc một phiên bản JDK tình cờ cho phép remove sau forEachRemaining để coi đó là bảo đảm của interface. Xem [API Iterator.forEachRemaining](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html#forEachRemaining(java.util.function.Consumer)).

## 10. Quan hệ với ListIterator và removeIf

### 10.1. ListIterator có hợp đồng rộng hơn

`ListIterator` mở rộng Iterator, hỗ trợ cả next và previous. Remove của nó hướng tới phần tử vừa được trả về bởi một trong hai chiều duyệt.

Nó còn có add và set, nên điều kiện trạng thái cũng cần đọc riêng. Chẳng hạn, một add sau thao tác đọc có thể làm lần remove kế tiếp không hợp lệ.

Không bê máy trạng thái chỉ dùng next của bài này sang toàn bộ API ListIterator mà bỏ qua previous hoặc add. Nếu chỉ đang dùng Iterator tiến một chiều, không cần gọi những API mở rộng đó. Xem [API ListIterator.remove](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ListIterator.html#remove()).

### 10.2. removeIf phù hợp với điều kiện lọc đơn giản

Nếu chỉ cần loại tất cả phần tử thỏa một điều kiện, có thể diễn đạt:

```java
numbers.removeIf(number -> number < 0);
```

Dòng này là đoạn minh họa với danh sách số nguyên không null.

Nó rõ ý định “xóa phần tử thỏa điều kiện”, và một số lớp có cách triển khai tối ưu. Phần triển khai mặc định của `Collection.removeIf` duyệt và dùng iterator.remove cho phần tử khớp.

Điều đó không bảo đảm removeIf luôn chạy được trên mọi nguồn. Với nguồn không hỗ trợ thao tác thay đổi cần thiết, nó có thể bị từ chối. Predicate cũng không nên tự sửa cấu trúc collection đang lọc. Xem [API Collection.removeIf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#removeIf(java.util.function.Predicate)).

Chọn iterator tường minh khi cần điều khiển từng bước duyệt hoặc thực hiện xử lý đi kèm phần tử. Chọn removeIf khi điều kiện lọc đã mô tả đủ ý định.

## 11. Đồng thời và hiệu năng: giới hạn của bảo đảm

### 11.1. Hợp lệ trong một lượt duyệt không có nghĩa an toàn đa luồng

Iterator.remove giúp phối hợp việc xóa với trạng thái của iterator. Nó không tự khóa một `ArrayList` trước các luồng khác.

Ngay cả khi dùng `Collections.synchronizedList`, việc duyệt cần được đồng bộ theo hướng dẫn của lớp bao. Với mẫu tiêu chuẩn, toàn bộ lượt lấy iterator và duyệt phải nằm trong vùng synchronized trên đúng đối tượng list được bao, và các nơi truy cập khác phải tuân thủ cùng cơ chế.

Chỉ đồng bộ từng lời gọi riêng lẻ không biến cả chuỗi hasNext–next–remove thành một giao dịch nguyên tử. Xem [API Collections.synchronizedList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedList(java.util.List)).

### 11.2. Đúng ngữ nghĩa không phải lúc nào cũng tối ưu chi phí

Với `ArrayList`, xóa một phần tử có thể phải dịch các tham chiếu ở phía sau. Xóa nhiều phần tử bằng nhiều lần remove có thể tốn chi phí đáng kể.

Nếu mục tiêu là lọc nhiều dữ liệu:

- Cân nhắc removeIf khi có thể mô tả điều kiện rõ ràng.
- Nếu cần giữ nguồn, tạo một kết quả mới chứa những phần tử được giữ.
- Đo với dữ liệu và kiểu collection thực tế khi hiệu năng quan trọng.

Không kết luận mọi Iterator.remove đều có cùng độ phức tạp. Interface quy định ý nghĩa, còn cách tổ chức dữ liệu quyết định chi phí.

## 12. Lỗi thường gặp và cách sửa

| Lỗi | Điều bị hiểu sai | Hướng sửa |
| --- | --- | --- |
| Remove ngay sau khi tạo iterator | Xem remove như xóa phần tử sắp đọc | Gọi next để lấy phần tử trước |
| Gọi remove hai lần liên tiếp | Tưởng mỗi remove tự tiến sang phần tử kế | Mỗi phần tử được next trả về chỉ có một lượt remove |
| Gọi hasNext rồi remove | Tưởng kiểm tra còn dữ liệu cũng tạo mục tiêu xóa | HasNext không thay next |
| Gọi next trong điều kiện rồi next thêm để xóa | Đã tiến qua phần tử cần xử lý | Lưu kết quả một lần next vào biến cục bộ |
| Gọi list.remove trong for-each | Sửa cấu trúc ngoài đường của iterator đang duyệt | Dùng iterator tường minh hoặc removeIf phù hợp |
| Dùng iterator từ List.of hoặc Arrays.asList | Bỏ qua giới hạn thao tác của nguồn | Chọn nguồn hỗ trợ giảm kích thước hoặc tạo bản sao |
| Nghĩ collection concurrent nào cũng hỗ trợ iterator.remove | Nhầm bảo đảm đồng thời với khả năng chỉnh sửa iterator | Đọc hợp đồng lớp cụ thể |
| Gọi remove trong hoặc sau forEachRemaining | Suy từ mã mặc định thành bảo đảm chung | Dùng vòng while rõ ràng khi cần xóa |
| Dựa vào việc không thấy ConcurrentModificationException | Coi fail-fast như cơ chế đồng bộ hoặc kiểm chứng đúng | Sửa cách duyệt, không dựa vào ngoại lệ |
| Cho iterator khác xóa rồi tiếp tục iterator hiện tại | Nhầm trạng thái nhiều iterator là một | Tránh thay đổi ngoài lượt duyệt hiện tại hoặc dùng chính sách phù hợp |

## 13. Tự kiểm tra

1. Với `[A, B, C]`, sau next trả A rồi next trả B, remove xóa phần tử nào?
2. Sau next cuối cùng và hasNext trả false, còn được remove không? Cần những điều kiện gì?
3. Vì sao hasNext không tạo quyền remove mới sau một lần remove?
4. Với `[A, B, A]`, so sánh mục tiêu xóa của iterator ở A thứ hai và `list.remove("A")`.
5. Giải thích vì sao CopyOnWriteArrayList có thể xóa trực tiếp nhưng iterator không cho remove.
6. Vì sao một lần chạy for-each kết hợp list.remove không báo lỗi vẫn không chứng minh cách viết đó an toàn?
7. Khi chỉ cần xóa các số âm, chọn iterator tường minh hay removeIf? Nêu lý do và điều kiện của nguồn dữ liệu.

Điều cần giữ rõ là **khả năng hỗ trợ**, **phần tử vừa được trả về** và **trạng thái lượt duyệt**. Khi cả ba được kiểm soát, Iterator.remove cho phép loại phần tử đúng vị trí mà không phải tìm lại nó hay tự đoán cách điều chỉnh quá trình duyệt.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="iteration-004">ListIterator: duyệt hai chiều, chỉ số và con trỏ</a>

<details>
<summary>Xem chi tiết</summary>

# ListIterator: duyệt hai chiều, chỉ số và con trỏ

`Iterator` giúp lấy lần lượt các phần tử theo một chiều. Với danh sách, đôi khi ta cần đi tới, quay lại, biết vị trí kế tiếp hoặc chỉnh sửa gần vị trí đang duyệt. `ListIterator<E>` cung cấp các khả năng đó.

Điểm cần nắm trước mọi tên phương thức là:

> **Con trỏ của ListIterator nằm giữa hai phần tử, không nằm trên một phần tử hiện tại.**

Mô hình này giải thích vì sao `next()` rồi `previous()` có thể trả về cùng một phần tử, vì sao có thể tạo iterator tại `list.size()`, và vì sao `remove()` phải nhớ phần tử được trả về gần nhất.

Bài dùng API Java 17. Ba chương trình hoàn chỉnh đã được biên dịch bằng `--release 17` và chạy để đối chiếu stdout. Các đoạn mã rút gọn khác minh họa thao tác trong một phương thức, không phải tệp chạy độc lập.

Sau bài học, bạn có thể:

- Vẽ vị trí con trỏ trước và sau `next`, `previous`.
- Phân biệt vị trí khởi tạo iterator với chỉ số phần tử.
- Dùng đúng `nextIndex` và `previousIndex`, kể cả ở các biên.
- Duyệt xuôi, ngược và đổi chiều có chủ đích.
- Theo dõi riêng con trỏ và phần tử được trả về gần nhất khi chỉnh sửa.

## 1. ListIterator nằm ở đâu trong mô hình collection?

`ListIterator<E>` mở rộng `Iterator<E>`, bổ sung khả năng phù hợp với một chuỗi phần tử có thứ tự vị trí.

| Khả năng | Iterator | ListIterator |
| --- | --- | --- |
| Kiểm tra còn phần tử phía trước | `hasNext()` | `hasNext()` |
| Lấy và tiến tới | `next()` | `next()` |
| Xóa phần tử vừa trả về, nếu hỗ trợ | `remove()` | `remove()` |
| Kiểm tra còn phần tử phía sau | Không có phương thức tương ứng | `hasPrevious()` |
| Lấy và lùi lại | Không có phương thức tương ứng | `previous()` |
| Hỏi chỉ số ở hai phía con trỏ | Không | `nextIndex()`, `previousIndex()` |
| Thay phần tử vừa trả về, nếu hỗ trợ | Không | `set(element)` |
| Chèn tại vị trí con trỏ, nếu hỗ trợ | Không | `add(element)` |

Các cách lấy từ một `List<E>`:

```java
ListIterator<String> fromBeginning = values.listIterator();
ListIterator<String> fromPosition = values.listIterator(2);
```

Đoạn minh họa giả định `values` có kiểu `List<String>`, kích thước ít nhất là `2`, và đã import `ListIterator`.

Không phải mọi `Collection` đều cung cấp `ListIterator`. Chẳng hạn, một `Set` không có chỉ số danh sách và không tự có hợp đồng này.

Enhanced for trên danh sách chỉ cần cơ chế `Iterator`; nó không cho người viết điều khiển trực tiếp các khả năng hai chiều của `ListIterator`.

## 2. Mô hình con trỏ: n phần tử có n + 1 vị trí

Với danh sách:

```text
[A, B, C]
```

có bốn vị trí con trỏ:

```text
| A B C
A | B C
A B | C
A B C |
```

Dấu `|` biểu diễn khoảng trống mà con trỏ đang nằm.

Gọi vị trí đó là `p`:

- Có `p` phần tử nằm trước con trỏ.
- Nếu còn phần tử theo chiều next, `next()` trả phần tử ở chỉ số `p`.
- Nếu còn phần tử theo chiều previous, `previous()` trả phần tử ở chỉ số `p - 1`.

Khi thao tác, hãy ưu tiên hai hướng tường minh:

- **Chiều next:** đi từ đầu tới cuối.
- **Chiều previous:** đi từ cuối về đầu.

### Bảng trạng thái cho danh sách ba phần tử

| Con trỏ | nextIndex | previousIndex | hasNext | hasPrevious |
| --- | --- | --- | --- | --- |
| `\| A B C` | `0` | `-1` | `true` | `false` |
| `A \| B C` | `1` | `0` | `true` | `true` |
| `A B \| C` | `2` | `1` | `true` | `true` |
| `A B C \|` | `3` | `2` | `false` | `true` |

Có thể hình dung:

```text
nextIndex()     = p
previousIndex() = p - 1
```

Khi dùng một iterator còn hợp lệ, hai chỉ số mô tả các vị trí liền kề của cùng con trỏ. Chúng không phải hai con trỏ riêng.

## 3. Khởi tạo ở một vị trí khác đầu danh sách

`listIterator(index)` đặt con trỏ ngay trước phần tử có chỉ số đó.

Với `[A, B, C]`:

```java
ListIterator<String> cursor = values.listIterator(1);
```

tạo trạng thái:

```text
A | B C
```

Ngay sau đó:

- `next()` sẽ trả `B`.
- `previous()` sẽ trả `A`.
- `nextIndex()` là `1`.
- `previousIndex()` là `0`.

### Vì sao listIterator(size) hợp lệ?

Vị trí khởi tạo được phép nằm trong đoạn:

```text
0 <= index <= list.size()
```

`index == size` nghĩa là con trỏ ở cuối danh sách, sau phần tử cuối. Không có phần tử để `next`, nhưng có thể bắt đầu `previous`.

Ngược lại, `get(size)` không hợp lệ vì `get` yêu cầu **một phần tử** tại chỉ số đó, mà chỉ số phần tử cuối chỉ là `size - 1`.

| Lời gọi trên danh sách kích thước n | Miền chỉ số hợp lệ |
| --- | --- |
| `get(index)` | `0..n-1` |
| `listIterator(index)` | `0..n` |

Với danh sách rỗng, chỉ có một vị trí iterator hợp lệ là `0`. Khởi tạo với chỉ số âm hoặc lớn hơn kích thước gây `IndexOutOfBoundsException`.

## 4. Next và previous vừa trả phần tử vừa di chuyển

Bắt đầu tại:

```text
A | B C
```

Gọi `next()`:

```text
Giá trị trả về: B
Con trỏ mới:   A B | C
```

Gọi tiếp `previous()`:

```text
Giá trị trả về: B
Con trỏ mới:   A | B C
```

Hai lời gọi trả về cùng `B` vì con trỏ vừa đi qua phần tử đó rồi đi ngược lại qua chính nó.

### Previous không có nghĩa “lấy phần tử khác với lần next vừa rồi”

Một nhầm lẫn thường gặp là nghĩ sau khi `next()` trả `B`, `previous()` phải trả `A`. Mô hình con trỏ cho thấy điều đó sai: sau lần next, `B` nằm ngay bên trái con trỏ nên previous phải đi qua `B`.

Muốn lấy `A` theo chuỗi thao tác này, cần gọi previous thêm lần nữa.

### Các phương thức hỏi trạng thái không làm con trỏ di chuyển

- `hasNext()`.
- `hasPrevious()`.
- `nextIndex()`.
- `previousIndex()`.

Chúng không thay thế cho `next()` hoặc `previous()`. Một vòng lặp chỉ hỏi trạng thái mà không có thao tác tiến/lùi phù hợp sẽ không tự duyệt qua phần tử.

### Đổi chiều có thể khiến cùng phần tử được xử lý nhiều lần

Nếu cứ luân phiên `next()`, `previous()` ở cùng một khoảng, bạn có thể lấy lại cùng phần tử mãi.

Đây không phải lỗi của iterator. Chương trình cần một điều kiện hoặc chiến lược thay đổi hướng rõ ràng để thực sự tiến trong bài toán của mình.

## 5. Chương trình hoàn chỉnh: đi tới rồi quay lại

Tạo tệp **`ListCursorWalkDemo.java`**:

```java
import java.util.List;
import java.util.ListIterator;

public class ListCursorWalkDemo {
    public static void main(String[] args) {
        List<String> values = List.of("A", "B", "C");
        ListIterator<String> cursor = values.listIterator(1);

        show("initial", cursor);
        System.out.println("next = " + cursor.next());
        show("after next", cursor);
        System.out.println("previous = " + cursor.previous());
        show("after previous", cursor);

        while (cursor.hasNext()) {
            System.out.println("forward = " + cursor.next());
        }
        show("at end", cursor);

        while (cursor.hasPrevious()) {
            System.out.println("backward = " + cursor.previous());
        }
        show("at beginning", cursor);
    }

    private static void show(String label, ListIterator<String> cursor) {
        System.out.println(label
                + ": nextIndex=" + cursor.nextIndex()
                + ", previousIndex=" + cursor.previousIndex()
                + ", hasNext=" + cursor.hasNext()
                + ", hasPrevious=" + cursor.hasPrevious());
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 ListCursorWalkDemo.java
java -Dfile.encoding=UTF-8 ListCursorWalkDemo
```

Stdout:

```text
initial: nextIndex=1, previousIndex=0, hasNext=true, hasPrevious=true
next = B
after next: nextIndex=2, previousIndex=1, hasNext=true, hasPrevious=true
previous = B
after previous: nextIndex=1, previousIndex=0, hasNext=true, hasPrevious=true
forward = B
forward = C
at end: nextIndex=3, previousIndex=2, hasNext=false, hasPrevious=true
backward = C
backward = B
backward = A
at beginning: nextIndex=0, previousIndex=-1, hasNext=true, hasPrevious=false
```

### Theo dõi các giai đoạn

1. Iterator bắt đầu ở khoảng `A | B C`.
2. `next` trả `B` và đưa con trỏ sang phải `B`.
3. `previous` trả lại `B`, đưa con trỏ về khoảng ban đầu.
4. Vòng lặp xuôi từ vị trí đó lấy `B`, `C`; nó không tự quay về đầu để lấy `A`.
5. Ở cuối, `nextIndex` bằng `size`, nhưng `hasNext` là `false`.
6. Vòng lặp ngược lấy `C`, `B`, `A`.
7. Ở đầu, `previousIndex` là `-1` và `hasPrevious` là `false`.

Danh sách từ `List.of` không cho sửa đổi, nhưng vẫn hỗ trợ việc di chuyển iterator trong ví dụ. Khả năng duyệt và khả năng chỉnh sửa là hai phần hợp đồng cần phân biệt.

## 6. Dùng chỉ số để mô tả vị trí, không để đoán trạng thái

### nextIndex không phải lúc nào cũng là chỉ số có thể get

Ở cuối danh sách:

```text
nextIndex() == list.size()
```

Đây là giá trị báo vị trí kế tiếp nếu có, không phải một chỉ số phần tử hợp lệ để gọi `get`.

Tương tự, `previousIndex() == -1` ở đầu không có nghĩa có một phần tử tại `-1`.

Cách dùng đúng là kiểm tra hướng còn phần tử:

```java
if (cursor.hasNext()) {
    int index = cursor.nextIndex();
    String value = cursor.next();
    System.out.println(index + " -> " + value);
}
```

Hoặc theo chiều ngược:

```java
if (cursor.hasPrevious()) {
    int index = cursor.previousIndex();
    String value = cursor.previous();
    System.out.println(index + " -> " + value);
}
```

Đọc chỉ số **trước** thao tác giúp lưu đúng vị trí của phần tử sắp được trả về.

### Chỉ số sau thao tác đã mô tả khoảng mới

Nếu gọi `next()` rồi mới đọc `nextIndex()`, chỉ số đó là vị trí của phần tử kế tiếp nữa, không phải phần tử vừa nhận.

Ví dụ:

```text
Trước next: nextIndex = 1
next trả: B tại chỉ số 1
Sau next: nextIndex = 2
```

Không nên dùng tên chung như “currentIndex” mà không chỉ rõ nó là chỉ số của phần tử nào hoặc được đọc trước hay sau thao tác.

## 7. Con trỏ khác phần tử được trả về gần nhất

Để hiểu chỉnh sửa, cần theo dõi hai trạng thái riêng:

1. **Vị trí con trỏ:** khoảng trống giữa các phần tử.
2. **Phần tử được trả về gần nhất:** do `next()` hoặc `previous()` vừa cung cấp.

`set` và `remove` tác động tới phần tử được trả về gần nhất, với điều kiện trạng thái cho phép. Chúng không đơn giản có nghĩa “sửa/xóa phần tử bên phải con trỏ”.

### Vì sao cần nhớ chiều di chuyển?

- Sau `next()`, phần tử vừa trả về nằm bên trái con trỏ.
- Sau `previous()`, phần tử vừa trả về nằm bên phải con trỏ.

Vì vậy, cùng gọi `remove()` nhưng cách điều chỉnh chỉ số con trỏ khác nhau tùy thao tác trả về trước đó.

Nếu chỉ lưu “iterator đang ở chỉ số 2” mà quên lịch sử next/previous, bạn chưa có đủ thông tin để dự đoán chính xác phần tử bị `set` hoặc `remove`.

### Đi rồi quay lại không nhất thiết khôi phục toàn bộ trạng thái chỉnh sửa

`next()` rồi `previous()` có thể đưa **con trỏ** về cùng khoảng, nhưng lịch sử phần tử vừa trả về đã được cập nhật.

Do đó, không nên dùng chuỗi này như một phép “nhìn thử hoàn toàn không tác động” nếu sau đó còn dựa vào điều kiện của `set` hoặc `remove`. Các phương thức hỏi chỉ số không gây loại thay đổi lịch sử đó.

## 8. Set, add và remove ảnh hưởng thế nào đến con trỏ?

Các thao tác sau là tùy chọn. Chỉ sử dụng khi iterator của danh sách thực tế hỗ trợ chúng.

### Set: thay phần tử vừa được trả về

Sau một `next` hoặc `previous` hợp lệ:

```java
cursor.set("replacement");
```

thay giá trị của phần tử vừa được trả về.

- Không làm tăng hoặc giảm kích thước.
- Không di chuyển con trỏ.
- Không tự lấy thêm phần tử.
- Không hợp lệ nếu chưa có phần tử được next/previous trả về trong trạng thái cần thiết.
- Không hợp lệ ngay sau `add` hoặc `remove` khi chưa gọi next/previous trở lại.

Một lần `set` hợp lệ không tự xóa quyền thay thế tiếp phần tử đó. Quy tắc trạng thái không phải “mọi thao tác chỉ được gọi một lần”; mỗi thao tác có điều kiện riêng.

### Add: chèn ngay trước con trỏ

Nếu đang có:

```text
A B | C
```

gọi `add("X")` tạo:

```text
A B X | C
```

Hệ quả:

- Phần tử mới nằm ngay trước con trỏ, phía mà `previous` sẽ đi tới.
- `next()` tiếp theo vẫn trả `C`, như trước lần chèn.
- `previous()` tiếp theo sẽ trả phần tử `X` vừa chèn.
- `nextIndex` tăng một.
- Không thể gọi ngay `set` hoặc `remove` dựa trên phần tử cũ; cần một lần next/previous phù hợp trước.

`add` không có nghĩa phần tử vừa chèn tự trở thành phần tử vừa được trả về. Đây là hai trạng thái khác nhau.

### Remove sau next

Ví dụ:

```text
A B | C
```

Nếu khoảng này có được sau khi `next()` vừa trả `B`, thì `remove()` xóa `B`:

```text
A | C
```

`nextIndex` giảm một vì phần tử nằm trước con trỏ đã bị loại bỏ.

### Remove sau previous

Nếu có:

```text
A B | C D
```

sau khi `previous()` vừa trả `C`, thì `remove()` xóa `C`:

```text
A B | D
```

`nextIndex` giữ nguyên giá trị số trong tình huống này, vì phần tử vừa bị xóa nằm ngay sau con trỏ.

### Không remove hai lần cho cùng một lần trả về

Sau khi xóa, không còn phần tử vừa trả về hợp lệ để xóa tiếp. Muốn `remove` tiếp, cần gọi next/previous để nhận một phần tử khác trước.

Tương tự, `set` sau `remove` mà chưa có lần trả về mới cũng không hợp lệ.

## 9. Chương trình hoàn chỉnh: theo dõi chỉnh sửa và chỉ số

Tạo tệp **`ListCursorEditDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class ListCursorEditDemo {
    public static void main(String[] args) {
        List<String> values = new ArrayList<>(List.of("A", "B", "C"));
        ListIterator<String> cursor = values.listIterator(1);

        System.out.println("next = " + cursor.next());
        cursor.set("B*");
        show("after set", values, cursor);

        cursor.add("X");
        show("after add", values, cursor);

        try {
            cursor.set("blocked");
        } catch (IllegalStateException exception) {
            System.out.println("set immediately after add = "
                    + exception.getClass().getSimpleName());
        }

        System.out.println("previous = " + cursor.previous());
        cursor.remove();
        show("after remove(previous)", values, cursor);

        System.out.println("next = " + cursor.next());
        cursor.remove();
        show("after remove(next)", values, cursor);

        try {
            cursor.remove();
        } catch (IllegalStateException exception) {
            System.out.println("remove twice = "
                    + exception.getClass().getSimpleName());
        }
    }

    private static void show(
            String label, List<String> values, ListIterator<String> cursor) {
        System.out.println(label + ": " + values
                + ", nextIndex=" + cursor.nextIndex()
                + ", previousIndex=" + cursor.previousIndex());
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 ListCursorEditDemo.java
java -Dfile.encoding=UTF-8 ListCursorEditDemo
```

Stdout:

```text
next = B
after set: [A, B*, C], nextIndex=2, previousIndex=1
after add: [A, B*, X, C], nextIndex=3, previousIndex=2
set immediately after add = IllegalStateException
previous = X
after remove(previous): [A, B*, C], nextIndex=2, previousIndex=1
next = C
after remove(next): [A, B*], nextIndex=2, previousIndex=1
remove twice = IllegalStateException
```

### Diễn biến theo mô hình con trỏ

| Thao tác vừa hoàn tất | Dữ liệu và con trỏ | nextIndex |
| --- | --- | --- |
| Khởi tạo tại 1 | `A \| B C` | `1` |
| `next()` trả B | `A B \| C` | `2` |
| `set("B*")` | `A B* \| C` | `2` |
| `add("X")` | `A B* X \| C` | `3` |
| `previous()` trả X | `A B* \| X C` | `2` |
| `remove()` xóa X | `A B* \| C` | `2` |
| `next()` trả C | `A B* C \|` | `3` |
| `remove()` xóa C | `A B* \|` | `2` |

Hai ngoại lệ trong chương trình là lỗi trạng thái có chủ đích:

- `set` ngay sau `add` chưa có một phần tử mới được next/previous trả về.
- `remove` lần thứ hai chưa có lần trả về mới.

Danh sách thực tế là `ArrayList`, hỗ trợ các chỉnh sửa qua iterator được dùng trong chương trình. Không nên lấy hỗ trợ đó để suy rộng sang mọi danh sách.

## 10. Đầu, cuối, danh sách rỗng và các ngoại lệ

| Tình huống | Kết quả cần hiểu |
| --- | --- |
| `next()` khi không còn phía next | `NoSuchElementException` |
| `previous()` khi không còn phía previous | `NoSuchElementException` |
| Tạo iterator ở vị trí âm hoặc lớn hơn size | `IndexOutOfBoundsException` |
| `set/remove` khi không đủ trạng thái | `IllegalStateException`, trong iterator hỗ trợ thao tác và kiểm tra trạng thái đó |
| Thao tác chỉnh sửa không được hỗ trợ | `UnsupportedOperationException` |

Một thao tác có thể đồng thời vi phạm nhiều điều kiện. Không nên thiết kế chương trình dựa vào việc mọi lớp triển khai phải ưu tiên cùng một loại kiểm tra trong mọi tình huống không hợp lệ. Hãy gọi thao tác đúng trạng thái trên nguồn được xác định hỗ trợ.

### Chương trình hoàn chỉnh

Tạo tệp **`ListCursorBoundaryDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

public class ListCursorBoundaryDemo {
    public static void main(String[] args) {
        List<String> empty = new ArrayList<>();
        ListIterator<String> cursor = empty.listIterator(0);

        System.out.println("empty hasNext = " + cursor.hasNext());
        System.out.println("empty hasPrevious = " + cursor.hasPrevious());
        System.out.println("empty nextIndex = " + cursor.nextIndex());
        System.out.println("empty previousIndex = " + cursor.previousIndex());

        try {
            cursor.next();
        } catch (NoSuchElementException exception) {
            System.out.println("empty next = "
                    + exception.getClass().getSimpleName());
        }

        try {
            cursor.previous();
        } catch (NoSuchElementException exception) {
            System.out.println("empty previous = "
                    + exception.getClass().getSimpleName());
        }

        try {
            empty.listIterator(1);
        } catch (IndexOutOfBoundsException exception) {
            System.out.println("invalid start position = "
                    + exception.getClass().getSimpleName());
        }

        ListIterator<String> readOnly = List.of("A", "B").listIterator(2);
        System.out.println("read-only previous = " + readOnly.previous());

        try {
            readOnly.set("changed");
        } catch (UnsupportedOperationException exception) {
            System.out.println("read-only set = "
                    + exception.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 ListCursorBoundaryDemo.java
java -Dfile.encoding=UTF-8 ListCursorBoundaryDemo
```

Stdout:

```text
empty hasNext = false
empty hasPrevious = false
empty nextIndex = 0
empty previousIndex = -1
empty next = NoSuchElementException
empty previous = NoSuchElementException
invalid start position = IndexOutOfBoundsException
read-only previous = B
read-only set = UnsupportedOperationException
```

### Những điểm được kiểm chứng

- Danh sách rỗng có một khoảng con trỏ tại `0`.
- Không còn phần tử theo cả hai chiều.
- `nextIndex` là `0`, `previousIndex` là `-1`.
- Gọi next/previous không có phần tử đều thất bại bằng ngoại lệ phù hợp.
- Tạo iterator ở vị trí `1` trên danh sách rỗng là vượt giới hạn.
- Iterator từ `List.of("A", "B")` có thể duyệt ngược từ cuối và nhận `B`.
- Dù đã có phần tử vừa trả về, `set` vẫn không được hỗ trợ vì danh sách này không cho sửa đổi.

Không có dữ liệu trong danh sách rỗng khác với một danh sách có phần tử `null`. Giá trị `null` không được dùng làm dấu báo hết của `next` hoặc `previous`.

## 11. Hỗ trợ chỉnh sửa phụ thuộc nguồn và iterator

| Nguồn thường gặp | Duyệt hai chiều | set qua iterator | add/remove qua iterator |
| --- | --- | --- | --- |
| `ArrayList` thông thường | Có | Có | Có |
| `LinkedList` thông thường | Có | Có | Có |
| Danh sách từ `Arrays.asList` | Có | Có | Không, do kích thước cố định |
| Danh sách từ `List.of` | Có | Không | Không |
| Iterator snapshot của `CopyOnWriteArrayList` | Có | Không | Không |

Bảng nhấn mạnh rằng ngay cả khi bản thân danh sách có thể thay đổi, iterator của nó vẫn có thể không hỗ trợ chỉnh sửa.

Với `Arrays.asList`, thay phần tử qua `set` tác động đến dữ liệu được chia sẻ với mảng nguồn. Khả năng thay phần tử không có nghĩa danh sách có thể tăng hoặc giảm kích thước.

Với iterator snapshot, dữ liệu được duyệt là một góc nhìn có hợp đồng riêng tại thời điểm tạo iterator. Không tự cho rằng các chỉ số nó báo luôn mô tả danh sách sống đang được sửa ở nơi khác.

Khi viết phương thức nhận `List`, hãy ghi rõ nó cần hỗ trợ chỉ duyệt, thay phần tử hay cả thêm/xóa. Chỉ có kiểu `List` chưa đủ để bảo đảm mọi thao tác thay đổi sẽ thành công.

## 12. Không thay đổi cấu trúc từ bên ngoài rồi chờ con trỏ tự sửa

Với iterator của những lớp như `ArrayList`, việc thêm hoặc xóa trực tiếp qua danh sách sau khi tạo iterator có thể khiến iterator bị phát hiện là không còn hợp lệ.

Ví dụ về cách tổ chức cần tránh:

```java
ListIterator<String> cursor = values.listIterator();
values.add("new");
cursor.next();
```

Nếu đang duyệt bằng một iterator có cơ chế phát hiện sửa đổi, hãy dùng các thao tác chỉnh sửa được hỗ trợ của **chính iterator đó** trong lượt duyệt.

Những điểm cần nhớ:

- Có thể gặp `ConcurrentModificationException` ngay trong một luồng.
- Cơ chế phát hiện sửa đổi là cách hỗ trợ tìm lỗi, không phải khóa đồng bộ hoặc bảo đảm an toàn luồng.
- Không được dựa vào việc mọi thay đổi sai đều chắc chắn bị phát hiện ở cùng một phương thức.
- Hai iterator của cùng danh sách không mặc định phối hợp chỉnh sửa với nhau. Một iterator sửa cấu trúc có thể làm iterator còn lại không còn hợp lệ.
- Snapshot hoặc iterator chuyên biệt có hợp đồng khác; cần đọc đúng loại nguồn thực tế.

### Chỉ số iterator không phải mã định danh ổn định của phần tử

Khi chèn hoặc xóa, chỉ số các phần tử có thể thay đổi. Không nên lưu một `nextIndex` rồi coi nó là danh tính lâu dài của một đối tượng.

Nếu cần nhận diện công việc, người dùng hoặc bản ghi sau nhiều thay đổi, hãy dùng mã định danh phù hợp. Chỉ số mô tả vị trí trong một trạng thái danh sách, không thay thế định danh nghiệp vụ.

## 13. Hiệu quả và cách sử dụng phù hợp

### Với ArrayList

- Di chuyển next/previous và thay phần tử có chi phí trực tiếp theo vị trí trong cấu trúc mảng.
- Chèn hoặc xóa ở giữa thường cần dịch các phần tử.
- Dùng `ListIterator` không biến thao tác chèn giữa danh sách thành thao tác không phải di chuyển dữ liệu.

### Với LinkedList

- Khởi tạo iterator ở một vị trí cần tìm tới nút tương ứng.
- Sau khi đã ở vị trí đó, đi tiếp hoặc lùi theo liên kết không cần tìm lại bằng chỉ số cho từng bước.
- Chèn hoặc xóa qua iterator tại vị trí đã biết có thể nối lại liên kết mà không phải tìm lại từ đầu theo chỉ số.

Các đặc tính này thuộc lớp triển khai; giao diện `ListIterator` không tự hứa cùng độ phức tạp cho mọi danh sách.

### Tránh khởi tạo lại iterator cho từng phần tử

Nếu muốn duyệt một đoạn danh sách liên kết, lấy một iterator tại điểm bắt đầu rồi tiến/lùi. Việc lặp đi lặp lại `listIterator(index)` có thể tạo lại chi phí tìm vị trí nhiều lần.

### Khi nào nên dùng?

| Nhu cầu | Lựa chọn phù hợp |
| --- | --- |
| Chỉ đọc tuần tự toàn bộ danh sách | Enhanced for hoặc iterator thông thường thường đủ |
| Cần đi tới rồi quay lại trên cùng lượt duyệt | `ListIterator` |
| Cần bắt đầu gần giữa và duyệt hai chiều | `listIterator(startPosition)` |
| Cần thay hoặc chèn gần vị trí đang duyệt | `ListIterator`, khi nguồn hỗ trợ |
| Cần truy cập một vị trí bất kỳ độc lập | Có thể dùng `get` nếu phù hợp cấu trúc |
| Cần duyệt ngược chỉ để đọc | Iterator tại `size` rồi previous là một cách rõ ràng |

Chọn `ListIterator` khi trạng thái con trỏ thực sự giúp giải bài toán. Không cần tăng độ phức tạp của một vòng lặp chỉ đọc nếu bạn không cần các khả năng bổ sung.

## 14. Lỗi thường gặp

### Tưởng con trỏ đang trỏ vào phần tử vừa lấy

Con trỏ nằm giữa phần tử; “phần tử vừa lấy” là trạng thái khác. Hãy vẽ dấu `|` sau từng bước để tránh suy đoán sai.

### Tưởng next rồi previous phải trả hai phần tử khác nhau

Hai thao tác ngược liên tiếp thường đi qua cùng phần tử theo hai hướng. Muốn tiến sang phần tử khác, phải thay đổi chuỗi thao tác có chủ đích.

### Gọi previous trên iterator mới tạo ở đầu

Ở vị trí `0`, không có phần tử bên previous. Muốn duyệt ngược toàn bộ, tạo iterator ở `list.size()`.

### Dùng nextIndex bằng size để gọi get

`size` là vị trí con trỏ hợp lệ ở cuối, không phải chỉ số phần tử. Dựa vào `hasNext` hoặc `hasPrevious` trước khi lấy.

### Dùng add rồi remove để mong xóa ngay phần tử vừa chèn

`add` không trả về phần tử qua next/previous và làm mất điều kiện set/remove dựa vào lần trả về cũ. Nếu cần thao tác với phần tử mới, phải di chuyển và lấy nó theo đúng chiều trước.

### Nghĩ set luôn thay phần tử phía next

`set` thay phần tử vừa được next/previous trả về trong trạng thái hợp lệ. Nó có thể nằm ở hai phía con trỏ tùy chiều đã đi.

### Nhầm duyệt hai chiều với không cần điều kiện dừng

Các vòng lặp đi tới rồi lùi lại có thể lặp mãi quanh cùng vị trí. Phải xác định hướng, phạm vi và điều kiện kết thúc.

### Nghĩ iterator tự cập nhật theo mọi thay đổi của list

Không có bảo đảm đó. Giữ thao tác trong hợp đồng của iterator và nguồn, nhất là khi có nhiều lượt duyệt hoặc nhiều bên sửa.

## 15. Thực hành

### Bài 1: điền bảng con trỏ

Với `[10, 20, 30, 40]`, tạo iterator tại vị trí `2`, rồi thực hiện:

```text
previous()
next()
next()
previous()
```

Với mỗi bước, ghi:

- Giá trị trả về.
- Vị trí dấu `|`.
- `nextIndex`.
- `previousIndex`.

Các giá trị trả về lần lượt cần là `20`, `20`, `30`, `30`. Hãy giải thích bằng chuyển động con trỏ.

### Bài 2: duyệt ngược từ cuối

Tạo một danh sách có ba chuỗi, lấy iterator tại `size` và in ngược bằng `hasPrevious/previous`.

Kiểm tra thêm danh sách rỗng. Giải thích vì sao không cần trừ `1` khi truyền vị trí khởi tạo iterator ở cuối.

### Bài 3: dự đoán thay đổi qua iterator

Với danh sách khả biến `[A, B, C]` và con trỏ ban đầu ở vị trí `1`:

```text
add(X)
previous()
set(Y)
next()
```

Dự đoán nội dung danh sách và con trỏ sau mỗi bước.

Sau chuỗi này, danh sách cần là `[A, Y, B, C]`; thao tác next cuối trả lại `Y`, không phải `B`. Hãy xác định lý do từ khoảng con trỏ sau previous.

### Bài 4: phân biệt lỗi trạng thái và không hỗ trợ

Thử các trường hợp trên nguồn phù hợp:

1. Iterator của `ArrayList` gọi `remove` trước khi next/previous.
2. Gọi `next`, rồi `remove`, rồi `remove` tiếp.
3. Iterator của `List.of` đã nhận phần tử rồi gọi `set`.
4. Danh sách rỗng gọi `previous`.
5. Khởi tạo iterator với vị trí lớn hơn `size`.

Ghi loại lỗi và lý do, phân biệt thiếu trạng thái hợp lệ, không có phần tử, chỉ số khởi tạo sai và thao tác không được nguồn hỗ trợ.

Tiêu chí hoàn thành là theo dõi được vị trí con trỏ cùng lịch sử phần tử vừa trả về, dự đoán chính xác chiều lấy dữ liệu và không dùng chỉ số hoặc thao tác chỉnh sửa ngoài hợp đồng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="iteration-005">ListIterator.add, set và remove</a>

<details>
<summary>Xem chi tiết</summary>

# ListIterator.add, set và remove

`ListIterator<E>` cho phép duyệt một `List` theo hai chiều và chỉnh sửa danh sách tại vị trí đang duyệt. Ba thao tác thay đổi có mục tiêu khác nhau:

- `add(e)`: chèn một phần tử mới vào khoảng trống tại con trỏ.
- `set(e)`: thay phần tử mà `next()` hoặc `previous()` vừa trả về.
- `remove()`: xóa lần xuất hiện mà `next()` hoặc `previous()` vừa trả về.

Muốn dùng đúng, cần theo dõi **vị trí con trỏ** và **phần tử vừa được trả về** như hai thông tin riêng. Chỉ nhìn vị trí con trỏ rồi đoán `set/remove` tác động bên trái hay bên phải sẽ dẫn đến lỗi khi đổi chiều duyệt.

Các ví dụ dùng Java 17 trở lên và một danh sách có thể chỉnh sửa, trừ phần riêng minh họa thao tác không được hỗ trợ.

## 1. Con trỏ nằm giữa các phần tử

Với danh sách A, B, C, có bốn vị trí con trỏ:

~~~text
| A B C
A | B C
A B | C
A B C |
~~~

Dấu `|` là con trỏ minh họa, không phải một phần tử trong danh sách.

Từ vị trí:

~~~text
A | B C
~~~

- `next()` sẽ trả B rồi dịch con trỏ sang phải.
- `previous()` sẽ trả A rồi dịch con trỏ sang trái.

Với danh sách dài `n`, có `n + 1` khoảng trống như vậy. Không có một “phần tử hiện tại” duy nhất mà mọi thao tác cùng tác động.

Tạo bộ duyệt:

~~~java
// Đoạn mã trong một phương thức; cần các import java.util tương ứng.
List<String> values = new ArrayList<>(List.of("A", "B", "C"));

ListIterator<String> start = values.listIterator();  // | A B C
ListIterator<String> middle = values.listIterator(1); // A | B C
ListIterator<String> end = values.listIterator(values.size()); // A B C |
~~~

Chỉ số truyền cho `listIterator(index)` là vị trí mà phần tử được trả bởi `next()` sẽ có, nếu còn phần tử. Vì con trỏ cuối dãy hợp lệ, `index == size()` được phép; đây không phải một chỉ số phần tử hợp lệ cho `get(index)`. Xem hợp đồng [List.listIterator trong Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#listIterator(int)).

Những bộ duyệt ở trên có trạng thái riêng nhưng cùng tham chiếu một danh sách. Chúng không phải các bản sao dữ liệu; không nên dùng đồng thời để thay cấu trúc nguồn rồi cho rằng các bộ duyệt khác vẫn tự cập nhật trạng thái.

## 2. Chỉ số của con trỏ và phần tử vừa trả về

Hai phương thức quan sát hữu ích:

| Phương thức | Ý nghĩa |
| --- | --- |
| `nextIndex()` | Chỉ số của phần tử mà `next()` sẽ trả; bằng `size()` ở cuối. |
| `previousIndex()` | Chỉ số của phần tử mà `previous()` sẽ trả; bằng `-1` ở đầu. |

Ví dụ:

~~~text
Danh sách: A | B C
nextIndex()     = 1
previousIndex() = 0
~~~

Các chỉ số này mô tả khoảng trống tại con trỏ. Chúng không trực tiếp nói phần tử nào là mục tiêu của `set/remove`.

Hai chuỗi điều hướng có thể đưa đến cùng khoảng trống nhưng có mục tiêu chỉnh sửa khác nhau:

~~~text
Cách 1:
| A B C
next() trả A
A | B C
Phần tử vừa trả về: A

Cách 2:
A B | C
previous() trả B
A | B C
Phần tử vừa trả về: B
~~~

Cùng con trỏ `A | B C`, nhưng `set/remove` trong cách 1 nhắm A, còn trong cách 2 nhắm B.

Đây là điểm cốt lõi của bài. Mô tả chính thức của [ListIterator API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ListIterator.html) cũng phân biệt con trỏ với phần tử được trả về gần nhất.

## 3. add: chèn tại con trỏ, không cần đã duyệt một phần tử

Bắt đầu:

~~~text
P | Q
~~~

Gọi `add("X")`:

~~~text
P X | Q
~~~

Phần tử mới nằm:

- Sau phần tử mà `previous()` sẽ trả trước lúc chèn.
- Trước phần tử mà `next()` sẽ trả trước lúc chèn.
- Bên trái con trỏ sau khi chèn.

Vì vậy, sau `add("X")`:

- `next()` vẫn lấy Q nếu chưa có thao tác khác.
- `previous()` lấy X nếu chưa có thao tác khác.
- Kích thước danh sách tăng một.
- `nextIndex()` và `previousIndex()` đều tăng một so với trước khi chèn.

Ví dụ:

~~~java
List<String> values = new ArrayList<>(List.of("P", "Q"));
ListIterator<String> iterator = values.listIterator(1);

iterator.add("X");

System.out.println(values);               // [P, X, Q]
System.out.println(iterator.nextIndex());  // 2
System.out.println(iterator.previous());   // X
~~~

Dòng `previous()` cuối ví dụ đã làm con trỏ dịch sang trái. Khi suy luận bước tiếp theo, phải dùng vị trí mới, không tiếp tục coi nó đang đứng sau X.

### Add liên tiếp giữ thứ tự các lần chèn theo chiều tiến

~~~java
List<String> values = new ArrayList<>(List.of("P", "Q"));
ListIterator<String> iterator = values.listIterator(1);

iterator.add("X");
iterator.add("Y");

System.out.println(values);         // [P, X, Y, Q]
System.out.println(iterator.next()); // Q
~~~

Diễn biến:

~~~text
P | Q
P X | Q
P X Y | Q
~~~

Lần add thứ hai chèn tại con trỏ đã dịch sau X. Kết quả không phải P, Y, X, Q.

Không cần gọi `next/previous` giữa hai lần `add`. Có thể thêm ngay khi mới tạo bộ duyệt, ở đầu, giữa, cuối hoặc trong danh sách rỗng, miễn thao tác được danh sách hỗ trợ và phần tử hợp lệ.

### Add không đặt phần tử mới làm mục tiêu hợp lệ cho set/remove

Ngay sau một lần `add` thành công, không được lập tức gọi `set` hoặc `remove` dựa vào một phần tử đã được trả trước đó.

~~~java
iterator.add("X");

// iterator.set("Y"); // Trạng thái không hợp lệ nếu chưa next/previous lại.
// iterator.remove(); // Tương tự.
~~~

Nếu muốn sửa hoặc xóa X vừa chèn, có thể chủ động lấy nó bằng `previous()`, rồi thực hiện thao tác hợp lệ. Nhưng nhớ rằng thao tác điều hướng này cũng thay vị trí con trỏ.

## 4. set: thay đúng phần tử do next/previous trả về gần nhất

Ví dụ duyệt tiến:

~~~java
List<String> values = new ArrayList<>(List.of("A", "B", "C"));
ListIterator<String> iterator = values.listIterator();

String first = iterator.next(); // A
iterator.set("A*");

System.out.println(first);                // A
System.out.println(values);               // [A*, B, C]
System.out.println(iterator.nextIndex());  // 1
~~~

`set` thay phần tử trong danh sách. Biến `first` vẫn giữ tham chiếu hoặc giá trị đã nhận từ lời gọi trước, không tự được gán lại bởi thao tác của iterator.

`set`:

- Không thêm phần tử.
- Không xóa phần tử.
- Không đổi kích thước.
- Không dịch con trỏ.
- Cần một lần `next` hoặc `previous` thành công đủ điều kiện trước đó.

Không gọi `set` ngay khi mới tạo iterator, vì chưa có phần tử nào được trả để thay.

### Set sau khi duyệt lùi

~~~java
List<String> values = new ArrayList<>(List.of("A", "B", "C"));
ListIterator<String> iterator = values.listIterator(2); // A B | C

System.out.println(iterator.previous()); // B
iterator.set("B*");

System.out.println(values);              // [A, B*, C]
System.out.println(iterator.nextIndex()); // 1
~~~

B là mục tiêu vì `previous()` vừa trả B. Con trỏ sau `previous` là A | B C; `set` không dịch nó.

Nhìn vị trí con trỏ mà luôn thay phần tử bên trái sẽ sai trong trường hợp này. Phần tử vừa trả bởi `previous` đang nằm bên phải con trỏ.

### Có thể set nhiều lần

~~~java
ListIterator<String> iterator = values.listIterator();

iterator.next();
iterator.set("first replacement");
iterator.set("second replacement");
~~~

Nếu các phần tử thay thế đều hợp lệ và iterator hỗ trợ, lần `set` thứ hai vẫn hợp lệ. `set` không tự xóa quyền tác động tới lần xuất hiện đã được `next/previous` xác định.

Sau một hoặc nhiều lần `set`, `remove` cũng vẫn có thể xóa lần xuất hiện đó, miễn chưa có `add/remove` làm mất điều kiện.

### Thay phần tử khác với sửa đối tượng bên trong

Giả sử phần tử là một đối tượng có thể thay đổi:

- `iterator.set(otherObject)` thay tham chiếu được danh sách giữ ở lần xuất hiện đó.
- Gọi phương thức cập nhật trên đối tượng vừa lấy là sửa nội dung đối tượng, không gọi `ListIterator.set`.

Một danh sách không cho thay phần tử có thể vẫn chứa các đối tượng bên trong có thể thay đổi. Hai mức khả năng chỉnh sửa cần được xem xét riêng.

## 5. remove: xóa lần xuất hiện vừa được trả về

Ví dụ sau `next()`:

~~~java
List<String> values = new ArrayList<>(List.of("A", "B", "C"));
ListIterator<String> iterator = values.listIterator();

System.out.println(iterator.next()); // A
iterator.remove();

System.out.println(values);              // [B, C]
System.out.println(iterator.nextIndex()); // 0
System.out.println(iterator.next());      // B
~~~

A được trả rồi bị xóa. Con trỏ được điều chỉnh để B vẫn là phần tử tiếp theo; mã không cần tự giảm một biến chỉ số bên ngoài để tránh bỏ qua B.

Diễn biến:

~~~text
| A B C
next() trả A -> A | B C
remove()     -> | B C
~~~

### Sau previous: mục tiêu nằm bên phải con trỏ

~~~java
List<String> values = new ArrayList<>(List.of("A", "B", "C"));
ListIterator<String> iterator = values.listIterator(2); // A B | C

System.out.println(iterator.previous()); // B
iterator.remove();

System.out.println(values);              // [A, C]
System.out.println(iterator.nextIndex()); // 1
System.out.println(iterator.next());      // C
~~~

Diễn biến:

~~~text
A B | C
previous() trả B -> A | B C
remove()         -> A | C
~~~

Sau `previous`, con trỏ đã lùi. Xóa B không phải lùi con trỏ thêm một bước; C trở thành phần tử tiếp theo ở khoảng trống hiện tại.

### Remove không tìm theo equals

~~~java
List<String> values = new ArrayList<>(List.of("A", "B", "A"));
ListIterator<String> iterator = values.listIterator(values.size());

iterator.previous(); // Trả lần xuất hiện A cuối danh sách.
iterator.remove();

System.out.println(values); // [A, B]
~~~

Iterator xóa **lần xuất hiện đã được trả**, không gọi một phép tìm lại từ đầu theo giá trị A.

Nếu dùng `values.remove("A")`, ý nghĩa là xóa một phần tử khớp giá trị theo hợp đồng của danh sách; với danh sách này, đó là A đầu tiên. Ngoài việc xóa sai lần xuất hiện mong muốn, sửa cấu trúc trực tiếp qua danh sách còn có thể làm iterator đang dùng không hợp lệ.

`ListIterator.remove()` không có tham số và trả `void`. Không nhầm nó với `List.remove(index)` hoặc `List.remove(Object)`.

### Chỉ remove một lần cho một lần điều hướng

~~~java
iterator.next();
iterator.remove();

// iterator.remove(); // IllegalStateException nếu chưa next/previous lại.
~~~

Sau khi xóa mục tiêu, không còn mục tiêu hợp lệ từ lần điều hướng đó. Muốn xóa tiếp, phải lấy một phần tử tiếp theo hoặc trước đó một cách hợp lệ.

## 6. Bảng trạng thái của ba thao tác

Bảng giả định iterator hỗ trợ thao tác tương ứng, phần tử truyền vào hợp lệ và các bước được nói tới đều hoàn tất thành công.

| Trạng thái gần nhất | add | set | remove |
| --- | --- | --- | --- |
| Mới tạo, chưa next/previous | Được | Không có mục tiêu hợp lệ | Không có mục tiêu hợp lệ |
| Vừa next hoặc previous | Được | Được trên phần tử vừa trả | Được trên phần tử vừa trả |
| Vừa set, chưa add/remove sau lần điều hướng | Được | Được tiếp trên cùng lần xuất hiện | Được trên cùng lần xuất hiện |
| Vừa add, chưa next/previous lại | Được tiếp | Không hợp lệ | Không hợp lệ |
| Vừa remove, chưa next/previous lại | Được | Không hợp lệ | Không hợp lệ |

Quy tắc ngắn để theo dõi:

1. `next/previous` thành công chọn một mục tiêu cho `set/remove`.
2. `set` thay mục tiêu nhưng không làm mất điều kiện tiếp tục tác động tới lần xuất hiện đó.
3. `add` và `remove` thành công làm mất mục tiêu chỉnh sửa cũ.
4. `hasNext`, `hasPrevious`, `nextIndex` và `previousIndex` chỉ quan sát; chúng không tự chọn một mục tiêu mới.

Không dùng việc một thao tác ném lỗi làm cách điều khiển trạng thái bình thường. Đặc biệt, với một triển khai tùy biến hoặc thao tác không được hỗ trợ, không suy rộng toàn bộ trạng thái sau lỗi chỉ từ một lần chạy trên ArrayList.

### Điều hướng xen kẽ có thể trả lại cùng phần tử

~~~java
List<String> values = new ArrayList<>(List.of("A", "B"));
ListIterator<String> iterator = values.listIterator();

System.out.println(iterator.next());     // A
System.out.println(iterator.previous()); // A
iterator.set("A*");

System.out.println(values); // [A*, B]
~~~

`previous` đi ngược qua chính phần tử `next` vừa vượt qua. Đây không phải hai phần tử khác nhau. Thao tác điều hướng thành công gần nhất là `previous`, nên trạng thái con trỏ và mục tiêu phải được đọc theo bước đó.

## 7. add, set và remove đều là thao tác tùy chọn

Có phương thức trong interface không có nghĩa mọi nguồn đều cho phép thực hiện.

| Nguồn thường gặp | set qua list iterator | add/remove qua list iterator |
| --- | --- | --- |
| `ArrayList` có thể sửa | Hỗ trợ | Hỗ trợ |
| `LinkedList` có thể sửa | Hỗ trợ | Hỗ trợ |
| Danh sách từ `Arrays.asList(array)` | Hỗ trợ thay phần tử; phản ánh về mảng | Không hỗ trợ đổi kích thước |
| Danh sách từ `List.of(...)` hoặc `List.copyOf(...)` | Không hỗ trợ | Không hỗ trợ |
| Góc nhìn `Collections.unmodifiableList(...)` | Không hỗ trợ qua góc nhìn | Không hỗ trợ qua góc nhìn |

Danh sách có kích thước cố định không đồng nghĩa hoàn toàn không thể thay phần tử. `Arrays.asList` là ví dụ quan trọng.

Chương trình đầy đủ ở dưới kiểm chứng:

- `set` thay được một phần tử của danh sách có mảng phía sau.
- Mảng gốc cũng thấy giá trị mới.
- `add/remove` trên nguồn đó ném `UnsupportedOperationException`.
- `set` trên iterator của `List.of` cũng không được hỗ trợ.

Nếu không cần giữ nguồn có khả năng hạn chế, có thể tạo bản sao có thể sửa:

~~~java
List<String> editable = new ArrayList<>(source);
ListIterator<String> iterator = editable.listIterator();
~~~

Bản sao này chứa các tham chiếu phần tử từ nguồn; nó không tự sao chép sâu nội dung từng đối tượng.

## 8. Phân biệt lỗi trạng thái, lỗi khả năng và lỗi dữ liệu

| Tình huống | Loại lỗi cần phân biệt |
| --- | --- |
| set/remove khi chưa có phần tử trả về hợp lệ | `IllegalStateException` trên iterator hỗ trợ thao tác và kiểm tra trạng thái tương ứng. |
| Thao tác thay đổi không được triển khai hỗ trợ | `UnsupportedOperationException`. |
| Không còn phần tử ở chiều next/previous | `NoSuchElementException`. |
| Vị trí bắt đầu listIterator nằm ngoài miền cho phép | `IndexOutOfBoundsException`. |
| Giá trị thêm/thay không đáp ứng giới hạn kiểu hoặc nội dung của nguồn | Có thể có ngoại lệ tương ứng như `ClassCastException` hoặc `IllegalArgumentException`, tùy hợp đồng. |
| Nguồn cấm null nhưng truyền null | Phải theo chính sách và ngoại lệ của nguồn; không tự giả định null luôn được phép. |

Không viết kiểm thử đòi một ngoại lệ cụ thể theo một thứ tự ưu tiên tự đoán khi cùng lúc vi phạm nhiều điều kiện, chẳng hạn iterator vừa không hỗ trợ thao tác vừa chưa có mục tiêu hợp lệ.

Trong chương trình minh họa, các lỗi trạng thái dùng `ArrayList` hỗ trợ thay đổi, còn các lỗi không hỗ trợ được kiểm tra riêng trên nguồn có giới hạn. Cách tách đó giúp biết chính xác điều gì đang được chứng minh.

## 9. Ứng dụng: duyệt, xóa, thay và chèn trong một lượt

Giả sử cần xử lý danh sách chuỗi:

- Xóa chuỗi rỗng.
- Chuyển các chuỗi còn lại sang chữ hoa theo quy tắc trung lập của ví dụ.
- Thêm ký hiệu `"|"` sau mỗi chuỗi còn lại.

Ký hiệu trong ví dụ này là dữ liệu thật, khác với con trỏ được dùng trong sơ đồ.

~~~java
// Cần import java.util.Locale cùng các import danh sách.
List<String> tokens = new ArrayList<>(List.of("a", "", "b"));
ListIterator<String> editor = tokens.listIterator();

while (editor.hasNext()) {
    String token = editor.next();
    if (token.isEmpty()) {
        editor.remove();
    } else {
        editor.set(token.toUpperCase(Locale.ROOT));
        editor.add("|");
    }
}

System.out.println(tokens); // [A, |, B, |]
~~~

Vì sao không lặp vô hạn trên các ký hiệu mới chèn?

Sau `next()` lấy chuỗi gốc, con trỏ đã đứng sau nó. `add("|")` chèn ký hiệu trước con trỏ rồi để con trỏ ở sau ký hiệu. Lần `next()` tiếp theo lấy phần tử gốc phía sau, không quay lại ký hiệu vừa chèn.

Ở nhánh chuỗi rỗng, `remove()` xóa đúng phần tử vừa lấy và điều chỉnh bộ duyệt để không bỏ qua phần tử tiếp theo.

Ở nhánh còn lại, phải gọi `set` trước `add`. Nếu đổi thành `add` rồi `set`, lần `add` sẽ làm mất mục tiêu chỉnh sửa cũ, nên `set` không còn hợp lệ cho tới khi điều hướng lại.

Ví dụ giả định các token không `null`. Nếu miền dữ liệu cho phép `null`, cần định nghĩa một nhánh xử lý tương ứng trước khi gọi `isEmpty`.

## 10. Chương trình hoàn chỉnh

Lưu thành **ListIteratorMutationDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;

public class ListIteratorMutationDemo {
    public static void main(String[] args) {
        List<String> values = new ArrayList<>(List.of("A", "B", "C"));
        ListIterator<String> iterator = values.listIterator();
        show("Start", values, iterator);

        expectIllegalState("set before navigation",
                () -> iterator.set("bad"));
        expectIllegalState("remove before navigation", iterator::remove);

        System.out.println("next -> " + iterator.next());
        iterator.set("A1");
        iterator.set("A2");
        show("After repeated set", values, iterator);

        iterator.remove();
        show("After remove following next", values, iterator);
        expectIllegalState("remove twice", iterator::remove);

        iterator.add("X");
        show("After add X", values, iterator);
        expectIllegalState("set immediately after add",
                () -> iterator.set("bad"));
        expectIllegalState("remove immediately after add", iterator::remove);

        System.out.println("previous -> " + iterator.previous());
        iterator.set("Y");
        show("After set following previous", values, iterator);

        iterator.remove();
        show("After remove following previous", values, iterator);
        System.out.println("next after removing Y -> " + iterator.next());

        List<String> insertions = new ArrayList<>(List.of("P", "Q"));
        ListIterator<String> insertion = insertions.listIterator(1);
        insertion.add("X");
        insertion.add("Y");
        show("Two adds at the same gap", insertions, insertion);
        System.out.println("next after adds -> " + insertion.next());
        System.out.println("previous after next -> " + insertion.previous());
        System.out.println("previous again -> " + insertion.previous());
        insertion.remove();
        show("After removing returned Y", insertions, insertion);

        List<String> duplicates =
                new ArrayList<>(List.of("A", "B", "A"));
        ListIterator<String> last = duplicates.listIterator(duplicates.size());
        last.previous();
        last.remove();
        System.out.println("Remove last returned occurrence = " + duplicates);

        String[] array = {"A", "B"};
        List<String> fixed = Arrays.asList(array);
        ListIterator<String> fixedSet = fixed.listIterator();
        fixedSet.next();
        fixedSet.set("A*");
        System.out.println("Fixed-size set = " + fixed);
        System.out.println("Backing array = " + Arrays.toString(array));

        expectUnsupported("fixed-size add",
                () -> fixed.listIterator().add("X"));

        ListIterator<String> fixedRemove = fixed.listIterator();
        fixedRemove.next();
        expectUnsupported("fixed-size remove", fixedRemove::remove);

        ListIterator<String> readOnly = List.of("A").listIterator();
        readOnly.next();
        expectUnsupported("unmodifiable set", () -> readOnly.set("X"));

        List<String> tokens = new ArrayList<>(List.of("a", "", "b"));
        ListIterator<String> editor = tokens.listIterator();
        while (editor.hasNext()) {
            String token = editor.next();
            if (token.isEmpty()) {
                editor.remove();
            } else {
                editor.set(token.toUpperCase(Locale.ROOT));
                editor.add("|");
            }
        }
        System.out.println("Edited tokens = " + tokens);
    }

    private static void show(
            String label,
            List<String> values,
            ListIterator<String> iterator
    ) {
        System.out.println(label + " = " + values
                + "; nextIndex=" + iterator.nextIndex()
                + "; previousIndex=" + iterator.previousIndex());
    }

    private static void expectIllegalState(String label, Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalStateException");
        } catch (IllegalStateException exception) {
            System.out.println(label + " -> "
                    + exception.getClass().getSimpleName());
        }
    }

    private static void expectUnsupported(String label, Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException exception) {
            System.out.println(label + " -> "
                    + exception.getClass().getSimpleName());
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~shell
javac --release 17 -encoding UTF-8 ListIteratorMutationDemo.java
java ListIteratorMutationDemo
~~~

Stdout:

~~~text
Start = [A, B, C]; nextIndex=0; previousIndex=-1
set before navigation -> IllegalStateException
remove before navigation -> IllegalStateException
next -> A
After repeated set = [A2, B, C]; nextIndex=1; previousIndex=0
After remove following next = [B, C]; nextIndex=0; previousIndex=-1
remove twice -> IllegalStateException
After add X = [X, B, C]; nextIndex=1; previousIndex=0
set immediately after add -> IllegalStateException
remove immediately after add -> IllegalStateException
previous -> X
After set following previous = [Y, B, C]; nextIndex=0; previousIndex=-1
After remove following previous = [B, C]; nextIndex=0; previousIndex=-1
next after removing Y -> B
Two adds at the same gap = [P, X, Y, Q]; nextIndex=3; previousIndex=2
next after adds -> Q
previous after next -> Q
previous again -> Y
After removing returned Y = [P, X, Q]; nextIndex=2; previousIndex=1
Remove last returned occurrence = [A, B]
Fixed-size set = [A*, B]
Backing array = [A*, B]
fixed-size add -> UnsupportedOperationException
fixed-size remove -> UnsupportedOperationException
unmodifiable set -> UnsupportedOperationException
Edited tokens = [A, |, B, |]
~~~

Hai hàm `expectIllegalState` và `expectUnsupported` trong chương trình chỉ phục vụ minh họa: nếu thao tác không ném ngoại lệ như mong đợi, chương trình ném `AssertionError` để không lặng lẽ báo một kết quả sai. Không cần sao chép cách bắt lỗi này vào một luồng xử lý nghiệp vụ bình thường.

## 11. Đọc trạng thái trong chương trình

### Mới tạo

Danh sách A, B, C có con trỏ ở đầu. `nextIndex == 0`, `previousIndex == -1`. Chưa có mục tiêu, nên `set/remove` không hợp lệ.

### Next, set nhiều lần rồi remove

`next` trả A. Hai lần `set` vẫn tác động cùng vị trí, cuối cùng danh sách là A2, B, C và con trỏ còn đứng sau A2.

`remove` xóa lần xuất hiện đó. B, C còn lại, con trỏ trở về khoảng trống đầu dãy. Lần remove tiếp theo không có mục tiêu.

### Add rồi previous

`add("X")` tạo X, B, C và con trỏ ở sau X. Không được set/remove ngay.

`previous` trả X và đưa con trỏ về đầu. Từ đây set Y là hợp lệ, rồi remove xóa Y. B vẫn là phần tử kế tiếp.

### Hai lần add trong P, Q

Bộ duyệt ban đầu ở P | Q. Thêm X rồi Y tạo P, X, Y | Q. `next` trả Q. `previous` ngay sau đó lại trả Q; `previous` thêm lần nữa mới trả Y.

`remove` xóa Y chứ không xóa Q, vì mục tiêu là phần tử của lời gọi điều hướng thành công gần nhất. Sau xóa, danh sách là P, X | Q.

### Phần tử lặp

Bộ duyệt từ cuối trả A cuối trong A, B, A. Remove xóa lần xuất hiện cuối, để lại A, B. Nó không tìm một A khác từ đầu danh sách.

### Các nguồn có giới hạn

`Arrays.asList` cho thay A thành A*, đồng thời mảng gốc đổi. Nhưng thêm/xóa làm đổi kích thước không được hỗ trợ. Iterator của `List.of` từ chối cả việc thay A bằng X.

### Một lượt chỉnh sửa token

Thứ tự next → set → add hoặc next → remove đúng với điều kiện trạng thái, nên đầu vào a, chuỗi rỗng, b được biến thành A, ký hiệu, B, ký hiệu.

## 12. Sửa qua iterator và sửa trực tiếp qua List là hai đường khác nhau

Khi dùng iterator của một danh sách thông thường có cơ chế phát hiện thay đổi, nên thực hiện chỉnh sửa cần thiết bằng chính iterator đang duyệt, trong phạm vi nó hỗ trợ.

Không thay thế:

~~~java
iterator.remove();
~~~

bằng:

~~~java
values.remove(value);
~~~

rồi mặc nhiên cho rằng iterator vẫn hợp lệ. Sửa cấu trúc nguồn từ đường khác có thể khiến nó phát hiện thay đổi và ném `ConcurrentModificationException`.

Ngoại lệ này không phải công cụ đồng bộ, và không có nghĩa mọi lỗi truy cập đồng thời đều được phát hiện ngay. Cũng không có bảo đảm rằng sửa qua một iterator làm mọi iterator khác đang giữ cùng nguồn tự điều chỉnh đúng trạng thái.

`set` thường không thay kích thước, nhưng “không thay cấu trúc” không có nghĩa tự an toàn với nhiều luồng hay không có tác động tới thuật toán đang đọc nguồn.

Nếu cần nhiều luồng, phải chọn mô hình đồng bộ hoặc cấu trúc dữ liệu có hợp đồng phù hợp. Ví dụ, không suy ra iterator của một danh sách dùng cơ chế ảnh chụp sẽ hỗ trợ add/set/remove chỉ vì nó là `ListIterator`.

### Không dùng forEachRemaining như nơi thay đổi tùy ý

Với yêu cầu thay đổi danh sách theo từng phần tử, vòng lặp tường minh giúp theo dõi chính xác mục tiêu và thứ tự thao tác. Không dựa vào `forEachRemaining` rồi tùy ý gọi các phương thức thay đổi trong callback; hợp đồng của iterator có các giới hạn riêng về sửa nguồn trong quá trình đó.

Bài này chỉ xét chỉnh sửa giữa những lời gọi `next/previous` mà người viết mã điều khiển trực tiếp.

## 13. Hiệu năng: đúng vị trí không đồng nghĩa cùng chi phí

`ListIterator` giúp thực hiện thuật toán theo lượt duyệt, nhưng không làm mọi lớp danh sách có cùng chi phí:

- Với `ArrayList`, thêm/xóa ở giữa có thể cần dịch nhiều phần tử.
- Với `LinkedList`, khi đã có iterator ở đúng nút, thao tác liên kết có đặc điểm khác; việc tìm đến vị trí ban đầu vẫn có chi phí.
- Thay phần tử không phải cùng một bài toán với đổi kích thước.

Không chọn `LinkedList` chỉ vì thấy mã có `add/remove`, cũng không khẳng định dùng iterator sẽ tự làm mọi thao tác giữa danh sách thành hằng số.

Với chỉnh sửa hàng loạt, cân nhắc liệu tạo danh sách kết quả mới, dùng thao tác chuyên dụng hoặc gom thay đổi có rõ và hiệu quả hơn không. Chọn sau khi đã xác định kích thước, mẫu duyệt và yêu cầu giữ nguồn.

## 14. Cách tự kiểm tra một chuỗi thao tác

Sau mỗi bước, ghi bốn thông tin:

1. Nội dung và kích thước danh sách.
2. Khoảng trống con trỏ, hoặc `nextIndex/previousIndex`.
3. Phần tử do next/previous thành công gần nhất trả về.
4. Có add/remove thành công nào từ lần điều hướng đó làm mất mục tiêu hay chưa.

Dùng dữ liệu có phần tử trùng để phát hiện việc xóa theo giá trị thay vì theo lần xuất hiện. Dùng cả hướng tiến và hướng lùi để phát hiện việc coi mục tiêu luôn nằm bên trái con trỏ.

Các trường hợp biên nên có:

- Danh sách rỗng rồi add.
- Con trỏ đầu và con trỏ cuối.
- Chỉ có một phần tử rồi remove thành rỗng.
- Set ngay khi mới tạo.
- Set nhiều lần sau một lần điều hướng.
- Remove sau set.
- Remove hai lần.
- Set/remove ngay sau add.
- Add nhiều lần liên tiếp.
- Previous ngay sau add.
- Next rồi previous liên tiếp.
- Nguồn chỉ hỗ trợ một phần thao tác thay đổi.

Không tự thử ghi đè dữ liệu người dùng để quan sát ngoại lệ; các ca học tập nên dùng danh sách nhỏ tạo mới như chương trình trong bài.

## 15. Lỗi thường gặp

| Lỗi | Nguyên nhân | Cách suy luận đúng |
| --- | --- | --- |
| Set ngay khi vừa tạo iterator | Chưa có phần tử trả về gần nhất | Điều hướng thành công trước khi sửa. |
| Coi set/remove luôn tác động phần tử bên trái con trỏ | Nhầm con trỏ với mục tiêu | Theo dõi lời gọi next hoặc previous cuối. |
| Mong next trả phần tử vừa add | Add đặt phần tử mới ở trước con trỏ sau thao tác | Previous mới quay lại phần tử vừa chèn nếu chưa có bước khác. |
| Gọi set ngay sau add | Mục tiêu cũ đã bị làm mất hiệu lực | Điều hướng lại, hoặc set trước add nếu đó là ý định. |
| Cho rằng set chỉ được gọi một lần | Nhầm quy tắc của remove với set | Set có thể lặp khi chưa add/remove làm mất điều kiện. |
| Remove liên tiếp mà không điều hướng | Lần xuất hiện mục tiêu đã bị xóa | Lấy phần tử tiếp theo hoặc trước đó trước khi xóa tiếp. |
| Dùng list.remove(value) thay iterator.remove | Đổi ngữ nghĩa và đường sửa nguồn | Xóa đúng lần xuất hiện qua iterator đang duyệt. |
| Cứ thấy ListIterator là cho rằng đủ add/set/remove | Các thao tác là tùy chọn | Kiểm tra khả năng của danh sách và iterator cụ thể. |
| Tự sửa một biến chỉ số sau khi iterator.remove | Quản lý hai mô hình tiến độ dễ lệch nhau | Để iterator quản lý vị trí duyệt, chỉ dùng chỉ số ngoài khi thật sự cần. |
| Dùng khả năng sửa qua iterator làm bảo đảm an toàn nhiều luồng | Nhầm trạng thái lượt duyệt với đồng bộ | Thiết kế truy cập đồng thời theo hợp đồng riêng. |

## 16. Thực hành

1. Với A, B, C và con trỏ A | B C, hãy tạo hai chuỗi điều hướng có cùng con trỏ nhưng mục tiêu set khác nhau.
2. Dự đoán kết quả: add X, add Y, previous, remove. Ghi danh sách và con trỏ sau từng bước.
3. Lấy một phần tử, set hai lần rồi remove. Giải thích vì sao hợp lệ, trong khi remove hai lần lại không hợp lệ.
4. Bắt đầu từ cuối A, B, A và xóa A do previous trả về. So sánh với ý nghĩa của remove theo giá trị trên List.
5. Viết thuật toán thay chuỗi không rỗng bằng dạng đã chuẩn hóa, xóa chuỗi rỗng và chèn một nhãn sau mỗi chuỗi được giữ. Kiểm tra nhãn có bị duyệt lại theo chiều tiến không.
6. Chạy cùng ý tưởng trên ArrayList, LinkedList, Arrays.asList và List.of; tách khả năng thao tác khỏi chi phí và trạng thái hợp lệ.
7. Viết các ca kiểm thử cho add vào danh sách rỗng, remove phần tử duy nhất và đổi chiều next/previous.

Trước khi gọi một thao tác thay đổi, hãy xác định nó tác động tới **khoảng trống con trỏ** hay **lần xuất hiện vừa được trả về**. Sau đó kiểm tra nguồn có hỗ trợ thao tác và trạng thái có hợp lệ không. Hai câu hỏi này giải thích phần lớn hành vi của add, set và remove.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="iteration-006">forEach, lambda và giới hạn thay đổi nguồn khi duyệt</a>

<details>
<summary>Xem chi tiết</summary>

# forEach, lambda và giới hạn thay đổi nguồn khi duyệt

`forEach` diễn đạt một yêu cầu đơn giản: **thực hiện một hành động với từng phần tử hoặc từng ánh xạ**. Lambda mô tả hành động đó.

Cú pháp ngắn hơn không làm mất các quy tắc của quá trình duyệt. Đặc biệt, một lambda có thể biên dịch được nhưng vẫn sửa nguồn dữ liệu theo cách không được bảo đảm.

Để dùng đúng, cần phân biệt:

- Duyệt nguồn để tạo tác dụng bên ngoài.
- Sửa trạng thái của đối tượng phần tử.
- Thêm, xóa hoặc thay đổi chính nguồn đang được duyệt.
- Duyệt tuần tự và xử lý qua stream song song.

## 1. forEach nào đang được gọi?

Các biểu thức có tên gần nhau nhưng hợp đồng không hoàn toàn giống nhau:

| Biểu thức | Đối tượng nhận lời gọi | Tham số hành động | Phạm vi cần đọc |
| --- | --- | --- | --- |
| `list.forEach(action)` | Một Iterable, chẳng hạn List | `Consumer<E>` | Hợp đồng Iterable và lớp collection |
| `map.forEach(action)` | Một Map | `BiConsumer<K, V>` | Hợp đồng Map và lớp triển khai |
| `stream.forEach(action)` | Một Stream | `Consumer<T>` | Hợp đồng stream, nhất là không can thiệp nguồn |
| `stream.forEachOrdered(action)` | Một Stream | `Consumer<T>` | Thứ tự gặp phần tử và cách xử lý stream |
| `iterator.forEachRemaining(action)` | Một Iterator đã có vị trí duyệt | `Consumer<E>` | Hợp đồng iterator và phần còn lại |

Ngoài ra còn có vòng lặp ngôn ngữ:

```java
for (String name : names) {
    System.out.println(name);
}
```

Đây là vòng **for-each**, không phải lời gọi phương thức `forEach`.

Vòng for-each cho phép dùng break hoặc continue theo quy tắc vòng lặp. Lambda truyền vào forEach là một thân hàm khác, nên không thể áp dụng các từ khóa điều khiển như thể vẫn đang viết trực tiếp trong vòng lặp bên ngoài.

## 2. Lambda đóng vai trò gì trong forEach?

### 2.1. Consumer nhận một phần tử và không trả kết quả

Ví dụ minh họa trong một phương thức:

```java
names.forEach(name -> System.out.println(name));
```

Lambda nhận một tên tại mỗi lần được gọi. Nó không trả về danh sách mới.

Có thể viết bằng method reference:

```java
names.forEach(System.out::println);
```

`Consumer<T>` có phương thức chức năng `accept(T)` trả về void. Nó phù hợp các hành động có tác dụng quan sát được như in dữ liệu, gửi thông tin hoặc ghi vào một đích kết quả riêng. Xem [API Consumer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/function/Consumer.html).

Một biểu thức gọi phương thức trả boolean, chẳng hạn `result.add(value)`, vẫn có thể dùng trong lambda phù hợp Consumer; giá trị trả về của lời gọi đó không được dùng làm kết quả của forEach.

### 2.2. Map truyền khóa và giá trị

```java
levels.forEach((name, level) ->
        System.out.println(name + "=" + level));
```

Đoạn này nhận hai đối số của mỗi ánh xạ, không phải một `Map.Entry`.

`BiConsumer<K, V>` cũng trả void. Nếu cần tính và thay giá trị của mỗi khóa, `Map.replaceAll` thể hiện ý định đó tốt hơn việc giả định lambda của forEach trả giá trị mới. Xem [API BiConsumer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/function/BiConsumer.html).

### 2.3. Gán lại tham số không thay phần tử trong nguồn

Trong:

```java
names.forEach(name -> {
    name = name.toUpperCase(Locale.ROOT);
});
```

biến tham số `name` nhận một giá trị mới trong lần gọi lambda đó. Nó không phải một “ô danh sách” để phép gán tự ghi ngược vào list.

Với các chuỗi, `toUpperCase` tạo hoặc trả một giá trị chuỗi phù hợp; nó không sửa chính đối tượng String cũ. Muốn tạo danh sách chữ hoa, dùng kết quả riêng hoặc phép biến đổi phù hợp. Muốn thay từng phần tử tại chỗ, dùng `List.replaceAll` trên list hỗ trợ thay thế.

Các đoạn ngắn trong phần này cần import và biến phù hợp; chương trình độc lập xuất hiện ở phần sau.

## 3. Biến được lambda sử dụng: final không có nghĩa dữ liệu bất biến

Biến cục bộ bên ngoài được lambda sử dụng phải final hoặc **effectively final**: không bị gán lại sau khi khởi tạo theo quy tắc ngôn ngữ.

Mẫu hợp lệ về cú pháp:

```java
List<String> result = new ArrayList<>();
names.forEach(name -> result.add(name));
```

`result` không bị gán sang một danh sách khác, dù nội dung đối tượng mà nó tham chiếu có thay đổi.

Mẫu không biên dịch:

```java
// Minh họa quy tắc bắt biến cục bộ, không phải chương trình để chạy.
// int count = 0;
// names.forEach(name -> count++);
```

Cấm sửa biến cục bộ được bắt không đồng nghĩa cấm mọi tác dụng phụ. Ngược lại, việc một lời gọi như `source.add(...)` biên dịch được không chứng minh nó được phép trong lượt duyệt nguồn.

Dùng một mảng một phần tử hoặc AtomicInteger chỉ để vượt hạn chế bắt biến có thể che giấu thiết kế khó hiểu. Nếu mục tiêu là đếm hoặc tính tổng, cân nhắc `count`, `sum` hoặc phép reduce phù hợp, thay vì thêm trạng thái chia sẻ khi không cần.

Quy tắc của biến cục bộ được lambda sử dụng thuộc [JLS 17, phần lambda body](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.27.2).

## 4. return, break và ngoại lệ không có cùng tác dụng

### 4.1. return chỉ kết thúc lần gọi lambda hiện tại

```java
names.forEach(name -> {
    if (name.length() == 2) {
        return;
    }
    System.out.println(name);
});
```

Tên có độ dài 2 bị bỏ qua ở hành động đó. ForEach vẫn tiếp tục với phần tử sau.

Return không:

- Thoát khỏi toàn bộ phương thức đang gọi forEach.
- Dừng lượt duyệt của forEach.
- Trả một kết quả tổng hợp ra ngoài.

### 4.2. Cần dừng sớm thì chọn cấu trúc phù hợp

Nếu điều khiển luồng cần break hoặc return khỏi phương thức hiện tại, vòng lặp thường rõ hơn.

Nếu mục tiêu là kiểm tra một điều kiện, có thể dùng thao tác stream có ý nghĩa dừng sớm:

```java
boolean hasLongName =
        names.stream().anyMatch(name -> name.length() > 3);
```

Không cần duyệt toàn bộ bằng forEach rồi dùng một biến cờ ngoài để giả lập việc tìm kiếm.

Không nên cố tình ném ngoại lệ bình thường chỉ để dùng như break khi có cách điều khiển rõ ràng hơn.

### 4.3. Ngoại lệ làm gián đoạn nhưng không hoàn tác

Với `Iterable.forEach`, nếu hành động ném ngoại lệ, ngoại lệ được chuyển ra bên gọi và lượt duyệt không tiếp tục như một lần hoàn tất bình thường.

Những hành động đã thực hiện trước lỗi không tự được hoàn tác. Nếu lambda đã ghi vào danh sách kết quả hoặc gửi một yêu cầu ra ngoài, forEach không cung cấp cơ chế giao dịch để quay lại trạng thái đầu.

Consumer và BiConsumer không khai báo tùy ý checked exception trong chữ ký phương thức chức năng. Nếu hành động cần I/O có checked exception, phải quyết định xử lý, chuyển thành loại phù hợp hoặc dùng vòng lặp/phương thức có hợp đồng lỗi rõ ràng. Việc đổi sang lambda không loại bỏ trách nhiệm xử lý lỗi.

## 5. Chương trình hoàn chỉnh về lambda và luồng thực thi

Lưu vào **ForEachLambdaDemo.java**. Các API được dùng tương thích Java 17 trở lên:

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ForEachLambdaDemo {
    public static void main(String[] args) {
        List<String> names = new ArrayList<>(List.of("An", "Binh", "Chi"));

        List<String> uppercase = new ArrayList<>();
        names.forEach(name -> uppercase.add(name.toUpperCase(Locale.ROOT)));
        System.out.println("Source: " + names);
        System.out.println("Separate result: " + uppercase);

        names.forEach(name -> {
            name = name.toUpperCase(Locale.ROOT);
        });
        System.out.println("After parameter assignment: " + names);

        List<String> accepted = new ArrayList<>();
        names.forEach(name -> {
            if (name.length() == 2) {
                return;
            }
            accepted.add(name);
        });
        System.out.println("After lambda return: " + accepted);

        Map<String, Integer> levels = new LinkedHashMap<>();
        levels.put("An", 1);
        levels.put("Binh", 2);
        List<String> rows = new ArrayList<>();
        levels.forEach((name, level) -> rows.add(name + "=" + level));
        System.out.println("Map rows: " + rows);

        List<String> processed = new ArrayList<>();
        try {
            names.forEach(name -> {
                if (name.equals("Binh")) {
                    throw new IllegalArgumentException("stop");
                }
                processed.add(name);
            });
        } catch (IllegalArgumentException ex) {
            System.out.println("Failure: " + ex.getMessage());
        }
        System.out.println("Processed before failure: " + processed);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ForEachLambdaDemo.java
java ForEachLambdaDemo
```

Stdout:

```text
Source: [An, Binh, Chi]
Separate result: [AN, BINH, CHI]
After parameter assignment: [An, Binh, Chi]
After lambda return: [Binh, Chi]
Map rows: [An=1, Binh=2]
Failure: stop
Processed before failure: [An]
```

### 5.1. Tạo kết quả khác không làm đổi nguồn

Danh sách `uppercase` độc lập với `names`. Lambda chỉ thêm vào đích khác, nên nguồn vẫn là `[An, Binh, Chi]`.

Đây là trường hợp tuần tự và các danh sách không bị luồng khác truy cập trong chương trình. Không suy ra rằng cùng kiểu ghi kết quả này vẫn an toàn khi chuyển ngay sang parallelStream.

### 5.2. Gán tham số và return

Phép gán `name = ...` không thay nội dung list, đúng như dòng `After parameter assignment`.

Return bỏ qua An nhưng Binh và Chi vẫn được thêm vào `accepted`. Nó kết thúc lần xử lý tên hiện tại, không kết thúc cả forEach.

### 5.3. Map và lỗi giữa chừng

`LinkedHashMap` trong ví dụ duyệt theo thứ tự thêm ánh xạ, nên `rows` có hai chuỗi đúng thứ tự đã in.

Ở lượt cuối, An được thêm vào `processed`; đến Binh thì phát sinh lỗi trước khi thêm. Chi không được xử lý tiếp. Sau khi bắt ngoại lệ, `processed` vẫn chứa An: phần tác dụng trước lỗi không bị hoàn tác.

## 6. Thay đổi nguồn trong action: giới hạn quan trọng

Hợp đồng `Iterable.forEach` không đưa ra bảo đảm chung nếu action tạo tác dụng phụ làm thay đổi nguồn phần tử, trừ khi lớp ghi đè có quy định riêng về thay đổi đồng thời. Xem [API Iterable.forEach](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Iterable.html#forEach(java.util.function.Consumer)).

Mẫu cần tránh với ArrayList thông thường:

```java
// Minh họa thay đổi chính nguồn đang được duyệt.
numbers.forEach(number -> {
    if (number % 2 == 0) {
        numbers.remove(number);
    }
});
```

Nguồn là `numbers`; action lại xóa khỏi `numbers`. Đây không phải Iterator.remove của một iterator đang được điều khiển tường minh.

Thêm phần tử vào chính nguồn cũng có vấn đề tương tự:

```java
// Không dùng để kỳ vọng phần tử mới luôn được duyệt trong cùng lượt.
numbers.forEach(number -> numbers.add(number * 10));
```

Không đặt ra giả định rằng:

- Mọi phần tử mới chắc chắn được xử lý.
- Vòng lặp sẽ chỉ nhìn dữ liệu lúc bắt đầu.
- Luôn ném ngoại lệ ngay tại lời gọi sửa.
- Chạy xong không lỗi là bằng chứng cách dùng hợp lệ.

Với các collection thông thường, chọn một chiến lược thay đổi nguồn được nêu rõ trong hợp đồng thay vì dựa vào kết quả tình cờ của một lần chạy.

### 6.1. ConcurrentModificationException không chỉ liên quan nhiều luồng

Một luồng vừa duyệt vừa sửa nguồn có thể gây ngoại lệ này. Từ “Concurrent” ở đây không chứng minh nhất thiết có hai luồng đang chạy.

Với ArrayList, cơ chế phát hiện thay đổi ngoài lượt duyệt hỗ trợ tìm lỗi nhưng không bảo đảm phát hiện mọi hành vi sai. Không dùng việc bắt ngoại lệ như cách đồng bộ hoặc điều khiển việc xóa. Xem [API ArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html).

### 6.2. Không đổi kích thước chưa chắc đã đủ

Thay một ô bằng `set`, sắp xếp lại nguồn hoặc thay đổi thứ tự cũng ảnh hưởng những gì lượt duyệt đọc. Không suy từ “không có add/remove” thành “được phép làm mọi thứ bên trong forEach”.

Nếu mục tiêu là thay từng phần tử, dùng `replaceAll` hoặc một cơ chế cập nhật có hợp đồng phù hợp. Nếu cần sắp xếp, thực hiện ở một bước riêng với điều kiện sử dụng rõ ràng.

### 6.3. Nguồn có thể bị sửa gián tiếp

Action không nhất thiết gọi đúng tên biến nguồn để gây thay đổi.

Các trường hợp cần nhận diện:

- Hai biến cùng tham chiếu một danh sách.
- Sửa một `subList` chia sẻ dữ liệu nền với list đang duyệt.
- Duyệt map nhưng sửa map qua `keySet` hoặc `values`.
- Gọi một phương thức phụ trợ hoặc callback mà bên trong sửa nguồn.
- Một luồng khác sửa nguồn trong khi action đang chạy.

Đổi tên biến không tạo ra một collection độc lập.

## 7. Sửa phần tử khác với sửa cấu trúc chứa phần tử

Có thể cần đánh dấu một đối tượng đã xử lý, tăng một bộ đếm thuộc đối tượng hoặc cập nhật trạng thái nghiệp vụ của nó.

Những việc đó không tự thêm hoặc xóa một vị trí trong list. Tuy nhiên, vẫn phải xem ảnh hưởng của thay đổi:

- Nếu phần tử cũng là phần tử của HashSet hoặc khóa của HashMap, sửa dữ liệu tham gia equals/hashCode có thể làm hỏng ý nghĩa tra cứu.
- Nếu phần tử hoặc khóa nằm trong cây có thứ tự, sửa trường quyết định thứ tự không tự tổ chức lại cây.
- Nếu nhiều phần tử tham chiếu cùng một đối tượng, một lần sửa có thể nhìn thấy ở nhiều nơi.
- Nếu xử lý song song, đối tượng phần tử có thể là trạng thái dùng chung cần phối hợp.

Vì vậy, không gộp mọi thay đổi thành một loại, cũng không mặc định mọi thao tác sửa đối tượng trong lambda đều an toàn.

Một danh sách không cho thêm/xóa cũng chưa chắc chứa các đối tượng bất biến. “Collection không cho sửa” và “toàn bộ dữ liệu bên trong không thể thay đổi” là hai bảo đảm khác nhau.

## 8. Chọn thao tác theo ý định thay đổi

| Ý định | Cách thường rõ ràng hơn |
| --- | --- |
| Chỉ thực hiện hành động với từng phần tử | `forEach` hoặc vòng lặp |
| Xóa các phần tử thỏa điều kiện | `removeIf` trên nguồn hỗ trợ |
| Vừa duyệt vừa xóa chính phần tử vừa lấy | Iterator tường minh và `Iterator.remove` nếu được hỗ trợ |
| Thay từng phần tử của list | `List.replaceAll` |
| Thay giá trị của từng khóa map | `Map.replaceAll` |
| Biến đổi/lọc để tạo dữ liệu mới | `map`, `filter`, collect/toList hoặc đích kết quả riêng tuần tự |
| Thêm vào nguồn theo dữ liệu cũ | Tích lũy riêng, thêm sau lượt duyệt |
| Duyệt bản dữ liệu theo thời điểm | Tạo bản sao cấu trúc trong điều kiện đồng thời phù hợp |

### 8.1. removeIf và replaceAll

`removeIf` nêu rõ điều kiện loại phần tử. Predicate trả boolean nhưng không nên tự sửa thêm cấu trúc nguồn đang được xử lý. Xem [API Collection.removeIf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#removeIf(java.util.function.Predicate)).

`List.replaceAll` dùng giá trị do hàm trả về để thay mỗi phần tử; đây là điều mà việc gán lại tham số trong Consumer không thực hiện. Nguồn phải hỗ trợ thay thế. Xem [API List.replaceAll](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#replaceAll(java.util.function.UnaryOperator)).

Các thao tác chuyên dụng vẫn có điều kiện và giới hạn riêng. Chúng không phải quyền cho lambda thực hiện thêm mọi thay đổi ngoài nhiệm vụ mà API đang điều khiển.

### 8.2. Tích lũy thay đổi rồi áp dụng sau

Nếu cần tạo phần tử mới dựa trên dữ liệu hiện tại, có thể thêm chúng vào một danh sách `additions` khác. Sau khi lượt duyệt nguồn kết thúc, mới gọi `source.addAll(additions)`.

Phần tử vừa thêm không tự được xử lý lại trong lượt cũ. Nếu nghiệp vụ yêu cầu xử lý một hàng đợi liên tục sinh tác vụ, hãy thiết kế bằng hàng đợi và điều kiện dừng phù hợp.

### 8.3. Duyệt bản sao cấu trúc

Với một chương trình tuần tự, có thể lấy `new ArrayList<>(source)`, duyệt bản sao đó rồi sửa nguồn gốc. Lượt duyệt lúc này không đi trên cùng cấu trúc bị sửa.

Giới hạn cần nhớ:

- Tạo bản sao có chi phí bộ nhớ và thời gian.
- Bản sao thường chỉ sao chép tham chiếu, không nhân bản sâu các đối tượng phần tử.
- Nếu nguồn bị sửa đồng thời ngay trong lúc sao chép, việc tạo bản sao không tự trở thành một thao tác chụp dữ liệu nguyên tử.
- Xóa theo giá trị trong nguồn có phần tử bằng nhau cần đúng ý định về lần xuất hiện.

Không dùng view hoặc một biến alias thay cho bản sao rồi kỳ vọng có sự tách rời.

## 9. Chương trình hoàn chỉnh về các cách thay đổi có chủ đích

Lưu vào **SafeTraversalChangesDemo.java**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SafeTraversalChangesDemo {
    public static void main(String[] args) {
        List<Integer> numbers =
                new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));
        numbers.removeIf(number -> number % 2 == 0);
        System.out.println("After removeIf: " + numbers);

        List<String> labels = new ArrayList<>(List.of("a", "b", "c"));
        labels.replaceAll(label -> "[" + label + "]");
        System.out.println("After replaceAll: " + labels);

        List<Integer> source = new ArrayList<>(List.of(1, 2, 3));
        List<Integer> additions = new ArrayList<>();
        source.forEach(number -> {
            if (number % 2 != 0) {
                additions.add(number * 10);
            }
        });
        source.addAll(additions);
        System.out.println("After staged additions: " + source);

        List<String> tasks =
                new ArrayList<>(List.of("keep", "done", "later"));
        new ArrayList<>(tasks).forEach(task -> {
            if (task.equals("done")) {
                tasks.remove(task);
            }
        });
        System.out.println("After snapshot traversal: " + tasks);

        List<String> tokens =
                new ArrayList<>(Arrays.asList("A", null, "B", null));
        Iterator<String> iterator = tokens.iterator();
        while (iterator.hasNext()) {
            if (iterator.next() == null) {
                iterator.remove();
            }
        }
        System.out.println("After iterator removal: " + tokens);

        Map<String, Integer> stock = new LinkedHashMap<>();
        stock.put("pen", 0);
        stock.put("book", 3);
        stock.put("bag", 0);
        stock.entrySet().removeIf(entry -> entry.getValue() == 0);
        stock.replaceAll((code, quantity) -> quantity + 1);
        System.out.println("Map after explicit changes: " + stock);

        List<Integer> squares = List.of(1, 2, 3, 4)
                .parallelStream()
                .map(number -> number * number)
                .toList();
        System.out.println("Parallel collected result: " + squares);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SafeTraversalChangesDemo.java
java SafeTraversalChangesDemo
```

Stdout:

```text
After removeIf: [1, 3, 5]
After replaceAll: [[a], [b], [c]]
After staged additions: [1, 2, 3, 10, 30]
After snapshot traversal: [keep, later]
After iterator removal: [A, B]
Map after explicit changes: {book=4}
Parallel collected result: [1, 4, 9, 16]
```

### Đọc các bước

- `removeIf` điều khiển việc xóa số chẵn, nên danh sách còn 1, 3 và 5.
- `replaceAll` thay các chuỗi bằng giá trị mới có dấu ngoặc.
- Action thêm vào `additions`, không thêm vào nguồn đang duyệt; sau đó mới áp dụng một lần.
- Lượt xử lý tasks đi trên một bản ArrayList riêng. Các giá trị chuỗi trong ví dụ không lặp nên việc xóa `"done"` theo giá trị có ý nghĩa rõ.
- Iterator tường minh xóa null qua chính `iterator.remove()`, không qua forEach.
- Map loại entry hết hàng rồi tăng các giá trị bằng thao tác dành cho mục tiêu tương ứng.
- Stream song song tạo kết quả bằng `map(...).toList()`, không dùng nhiều luồng cùng gọi add vào một ArrayList ngoài.

Chương trình dùng các collection và dữ liệu không bị luồng khác thay đổi. Các ví dụ là những chiến lược khác nhau, không phải lời khuyên ghép tất cả bước vào mọi tác vụ xử lý.

## 10. Map.forEach cũng cần giới hạn thay đổi nguồn

Mẫu xóa key của chính map bên trong `map.forEach` không phải cách viết portable cho một map thông thường.

Nếu cần loại ánh xạ theo giá trị, có thể dùng:

```java
stock.entrySet().removeIf(entry -> entry.getValue() == 0);
```

Đoạn minh họa giả định map có thể sửa, các giá trị Integer không null và view hỗ trợ xóa.

Nếu cần thay giá trị của mọi khóa:

```java
stock.replaceAll((code, quantity) -> quantity + 1);
```

Gán `quantity = quantity + 1` trong BiConsumer chỉ đổi tham số cục bộ. Lambda của replaceAll trả giá trị để API thực hiện cập nhật.

Default method của Map không tự cung cấp bảo đảm đồng bộ hay giao dịch cho cả lượt. Map cụ thể có thể có chính sách khác, nên hãy đọc đúng lớp khi dùng đồng thời. Xem [API Map.forEach](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#forEach(java.util.function.BiConsumer)) và [Map.replaceAll](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#replaceAll(java.util.function.BiFunction)).

## 11. Stream.forEach: thêm quy tắc không can thiệp nguồn

Stream xử lý một nguồn qua một pipeline. Các hành vi truyền vào pipeline thường cần **không can thiệp nguồn** và không phụ thuộc trạng thái thay đổi trong quá trình xử lý.

Mẫu cần tránh:

```java
// Không dùng để xóa chính nguồn của stream.
numbers.stream().forEach(number -> {
    if (number < 0) {
        numbers.remove(number);
    }
});
```

Stream tuần tự không làm việc sửa nguồn thành hợp lệ. Non-interference liên quan đến hợp đồng xử lý nguồn, không chỉ là tránh tranh chấp giữa nhiều luồng.

Nếu muốn tạo danh sách không có số âm:

```java
List<Integer> nonNegative = numbers.stream()
        .filter(number -> number >= 0)
        .toList();
```

Nếu muốn xóa ngay trên nguồn có hỗ trợ, removeIf thường mô tả đúng ý định hơn.

`Stream.toList()` trả danh sách không cho sửa; khi cần một ArrayList có thể sửa, có thể chọn collector phù hợp như `Collectors.toCollection(ArrayList::new)`. Đích kết quả là một quyết định riêng với việc giữ nguồn không bị can thiệp.

Các quy tắc về nguồn, trạng thái và tác dụng phụ được giải thích trong [tài liệu java.util.stream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/package-summary.html).

## 12. forEach song song và trạng thái chia sẻ

Mẫu không an toàn khi dùng ArrayList thông thường làm đích chung:

```java
// Không dùng làm cách thu kết quả song song.
List<Integer> result = new ArrayList<>();
numbers.parallelStream().forEach(number ->
        result.add(number * number));
```

Các action có thể chạy trên nhiều luồng. Lambda không tự cấp khóa cho result. Có thể xảy ra mất dữ liệu, tranh chấp hoặc lỗi khác; không nên gán một stdout cố định cho đoạn dùng sai này.

Cách tạo dữ liệu mới thường rõ hơn:

```java
List<Integer> result = numbers.parallelStream()
        .map(number -> number * number)
        .toList();
```

API thu kết quả điều khiển quá trình kết hợp, thay vì để các action tùy ý sửa một ArrayList dùng chung.

### Thứ tự thực hiện khác thứ tự nguồn

`Stream.forEach` không bảo đảm thứ tự gặp phần tử khi xử lý song song. Một List có thứ tự không làm các action song song tự hoàn thành theo thứ tự đó.

Nếu cần thực hiện action theo encounter order của stream, xem `forEachOrdered`. Nó không cho phép bỏ qua giới hạn thay đổi nguồn, không biến mọi dữ liệu bên ngoài thành an toàn trước các hoạt động khác, và có thể ảnh hưởng khả năng chạy song song.

Không đoán thứ tự từ một lần quan sát output. Xem [Stream.forEach](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#forEach(java.util.function.Consumer)) và [Stream.forEachOrdered](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#forEachOrdered(java.util.function.Consumer)).

## 13. Lớp concurrent có chính sách riêng, không phải ngoại lệ tùy ý

Một số cấu trúc được thiết kế để hỗ trợ thay đổi trong khi duyệt. Chúng có thể dùng ảnh chụp hoặc cơ chế duyệt phản ánh thay đổi theo những bảo đảm riêng.

Chẳng hạn:

- Iterator của CopyOnWriteArrayList làm việc với ảnh chụp, không phản ánh các thay đổi sau khi tạo iterator.
- ConcurrentHashMap cung cấp các thao tác duyệt phù hợp môi trường đồng thời, nhưng một lượt duyệt không mặc định là ảnh chụp nguyên tử của toàn bộ map.

Những đặc điểm này phải lấy từ hợp đồng lớp và phương thức thực tế. Không suy từ “thread-safe” thành “action nào sửa dữ liệu cũng đúng nghiệp vụ”, hoặc từ một iterator snapshot thành bảo đảm cho mọi API tương tự.

Ngay cả khi collection cho phép thay đổi đồng thời, action vẫn phải an toàn với trạng thái bên ngoài mà nó sửa. Các thay đổi hợp lệ có thể không được nhìn thấy đầy đủ trong lượt đang chạy, tùy chính sách.

Xem [CopyOnWriteArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html) và [ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html).

### Iterator.forEachRemaining không cấp quyền remove trong action

Không dùng sự tồn tại của một biến iterator để kết luận rằng có thể gọi remove tùy ý trong lambda của forEachRemaining.

Hợp đồng chung không bảo đảm hành vi nếu action sửa nguồn, kể cả gọi remove, trừ chính sách riêng được công bố. Cũng không có bảo đảm chung cho remove sau khi gọi forEachRemaining.

Khi cần xóa phần tử vừa đọc bằng iterator, vòng while với next và remove tường minh thể hiện đúng cơ chế. Xem [API Iterator.forEachRemaining](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html#forEachRemaining(java.util.function.Consumer)).

## 14. Chẩn đoán và tự rà soát

Trước một lời gọi forEach, hỏi:

1. Đây là Iterable.forEach, Map.forEach, Stream.forEach hay Iterator.forEachRemaining?
2. Action chỉ quan sát, sửa phần tử, sửa nguồn hay ghi sang một cấu trúc khác?
3. Cấu trúc “khác” có thật sự độc lập, hay là alias/view của nguồn?
4. Có cần dừng sớm hoặc trả kết quả ra ngoài không?
5. Có đang nhầm gán tham số với cập nhật phần tử/ánh xạ không?
6. Nếu một action lỗi giữa chừng, các tác dụng trước đó có chấp nhận được không?
7. Nếu chuyển sang song song, trạng thái bên ngoài nào trở thành dùng chung?
8. Thứ tự được yêu cầu là thứ tự dữ liệu hay thứ tự hoàn thành action?
9. Lớp collection cụ thể công bố chính sách thay đổi đồng thời nào?
10. Một thao tác chuyên dụng như removeIf, replaceAll, map/filter hoặc vòng lặp có làm ý định rõ hơn không?

## 15. Bài tập tự kiểm tra

1. Trong chương trình đầu, vì sao `name = ...` không đổi nguồn nhưng `uppercase.add(...)` lại đổi được danh sách kết quả?
2. Nếu return xuất hiện ở phần tử đầu tiên, những phần tử sau có còn được xử lý không?
3. Vì sao có thể nhận ConcurrentModificationException dù chương trình chỉ có một luồng?
4. Đổi đoạn xóa số âm trong forEach thành removeIf và thành cách tạo một kết quả mới; so sánh tác động lên nguồn.
5. Với danh sách chứa cùng một đối tượng ở hai vị trí, một bản sao ArrayList có làm dữ liệu đối tượng tách rời không?
6. Giải thích vì sao đổi `stream()` thành `parallelStream()` có thể làm một đích kết quả ArrayList trước đó dùng được trở nên không an toàn.
7. Nêu cách bảo toàn ý định “xử lý theo thứ tự ưu tiên” mà không dựa vào thứ tự output tình cờ.

ForEach hữu ích khi hành động trên từng phần tử đã rõ. Khi hành động bắt đầu quyết định cấu trúc nguồn, điều khiển dừng sớm hoặc kết hợp trạng thái giữa nhiều lần gọi, hãy chọn đúng API và cơ chế phối hợp cho yêu cầu đó.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="iteration-007">Cơ chế fail-fast và giới hạn bảo đảm của ConcurrentModificationException</a>

<details>
<summary>Xem chi tiết</summary>

# Cơ chế fail-fast và giới hạn bảo đảm của ConcurrentModificationException

Khi đang duyệt một collection, việc thêm hoặc xóa phần tử qua một đường khác có thể làm vị trí mà iterator đang giữ không còn tương ứng với nguồn dữ liệu. Nhiều iterator của các collection thông thường dùng cơ chế **fail-fast** để phát hiện một số thay đổi không phù hợp và ném `ConcurrentModificationException`.

Điểm quan trọng nhất là: **fail-fast là cơ chế hỗ trợ phát hiện lỗi, không phải một cơ chế bảo đảm việc truy cập hoặc duyệt dữ liệu luôn đúng**. Ngoại lệ có thể xuất hiện trong chương trình chỉ có một luồng. Ngược lại, không có ngoại lệ không chứng minh rằng quá trình duyệt không bỏ sót phần tử hoặc rằng truy cập nhiều luồng là an toàn.

Trong bài, viết tắt `CME` chỉ `ConcurrentModificationException`.

## 1. Vấn đề mà fail-fast cố phát hiện

Giả sử một iterator đang duyệt A, B, C:

~~~text
Nguồn lúc tạo iterator: [A, B, C]
Iterator đã lấy: A
Phần dự kiến tiếp theo: B, C
~~~

Một đoạn mã khác xóa B trực tiếp từ danh sách:

~~~text
Nguồn hiện tại: [A, C]
Iterator vẫn mang trạng thái từ lượt duyệt trước thay đổi
~~~

Iterator có thể đang giữ chỉ số, nút, số thay đổi kỳ vọng hoặc thông tin khác của một cấu trúc đã bị đổi. Nếu cứ tiếp tục mà không có chính sách rõ, chương trình có thể bỏ sót, đọc lặp, nhìn thấy dữ liệu không theo kỳ vọng hoặc gặp lỗi.

Fail-fast chọn hướng phát hiện dấu hiệu không còn phù hợp tại một số điểm kiểm tra rồi báo lỗi. Điều đó giúp tìm lỗi sớm hơn trong nhiều tình huống, nhưng không biến iterator thành một ảnh chụp độc lập của nguồn.

## 2. ConcurrentModificationException không đòi hỏi nhiều luồng

Ví dụ chỉ có một luồng:

~~~java
// Đoạn mã trong một phương thức; cần các import java.util tương ứng.
List<String> values = new ArrayList<>(List.of("A", "B", "C"));
Iterator<String> iterator = values.iterator();

System.out.println(iterator.next()); // A
values.remove("B"); // Sửa trực tiếp qua List, không qua iterator đang duyệt.

iterator.next(); // Có thể phát hiện thay đổi và ném CME ở triển khai fail-fast.
~~~

Không có `Thread`, executor hay chạy song song. Chính cùng một luồng đã:

1. Tạo iterator.
2. Đi qua một phần dữ liệu.
3. Sửa cấu trúc nguồn qua `values`.
4. Tiếp tục iterator cũ.

Tên “concurrent modification” không chỉ mô tả tình huống có nhiều luồng thực thi cùng lúc. Một luồng cũng có thể vi phạm cách sử dụng nguồn trong khi một lượt duyệt còn đang tồn tại. Tài liệu của [ConcurrentModificationException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ConcurrentModificationException.html) nêu rõ giới hạn này và việc không được dựa vào ngoại lệ để quyết định tính đúng.

`CME` là ngoại lệ không kiểm tra thuộc nhóm `RuntimeException`. Không có yêu cầu phải bắt nó ở mọi lời gọi iterator.

## 3. Mô hình thường gặp: bộ đếm thay đổi và giá trị kỳ vọng

Trong một triển khai như iterator của `ArrayList`, có thể hình dung:

- Collection giữ bộ đếm thay đổi, thường thấy với tên `modCount`.
- Khi tạo iterator, nó lưu giá trị kỳ vọng, thường thấy với tên `expectedModCount`.
- Một số thao tác của iterator kiểm tra xem hai giá trị còn phù hợp không.
- Nếu phát hiện chênh lệch, iterator ném `CME`.

Mã minh họa ý tưởng:

~~~java
// Mã giả giải thích cơ chế, không phải chương trình độc lập.
void checkForModification() {
    if (collectionVersion != iteratorExpectedVersion) {
        throw new ConcurrentModificationException();
    }
}
~~~

Đây không phải một giao thức API mà mọi collection phải dùng. Không truy cập hoặc sửa các bộ đếm nội bộ từ mã ứng dụng để “sửa” một iterator đã bị vô hiệu hóa.

Một flow đơn giản:

~~~mermaid
flowchart TD
    A["Tạo iterator và lưu phiên bản kỳ vọng"] --> B["Đi tới một thao tác có kiểm tra"]
    B --> C{"Phiên bản nguồn còn phù hợp?"}
    C -->|Có| D["Tiếp tục thao tác duyệt"]
    C -->|Không| E["Ném ConcurrentModificationException"]
    D --> B
~~~

Sơ đồ không nói rằng mọi phương thức đều kiểm tra hoặc rằng việc sửa nguồn tự ném lỗi ngay tại thời điểm sửa.

### Bộ đếm không chỉ là kích thước

Thêm một phần tử rồi xóa chính phần tử đó có thể đưa nội dung và kích thước về như cũ, nhưng đã có những lần sửa cấu trúc từ sau khi iterator được tạo.

~~~java
List<String> values = new ArrayList<>(List.of("A", "B", "C"));
Iterator<String> iterator = values.iterator();

values.add("D");
values.remove("D");

// Nội dung lại là [A, B, C], nhưng iterator không được tự coi là hợp lệ trở lại.
~~~

Chương trình kiểm chứng bên dưới phát hiện thay đổi khi tiếp tục iterator này. Không so sánh `size()` trước–sau để kết luận không hề có thay đổi liên quan đến lượt duyệt.

Cũng không giả định mọi phương thức cập nhật bộ đếm theo đúng một công thức hoặc luôn tăng đúng một đơn vị. Đó là chi tiết triển khai, không phải giao diện kiểm soát phiên bản dành cho ứng dụng.

## 4. Thay đổi cấu trúc và thay giá trị không giống nhau

Với `ArrayList`, thêm/xóa phần tử hoặc thay đổi rõ ràng cấu trúc mảng phía sau là các thay đổi cấu trúc. Chỉ thay một phần tử bằng `set` không được coi là thay đổi cấu trúc theo định nghĩa của lớp. Đồng thời, tài liệu nhấn mạnh fail-fast là nỗ lực phát hiện tốt nhất có thể, không phải bảo đảm tuyệt đối. Xem [ArrayList API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html).

Bảng cho ngữ cảnh `ArrayList` thông thường:

| Thao tác | Ý nghĩa liên quan đến lượt duyệt |
| --- | --- |
| `list.add(value)` | Có thay đổi cấu trúc nguồn. |
| `list.remove(...)` thực sự xóa phần tử | Có thay đổi cấu trúc nguồn. |
| `list.set(index, value)` | Thay giá trị ở vị trí có sẵn, không thay cấu trúc theo định nghĩa của ArrayList. |
| Sửa thuộc tính của đối tượng nằm trong danh sách | Không tự làm thay đổi cấu trúc danh sách; nội dung đối tượng vẫn có thể đã đổi. |
| Sửa qua iterator khác của cùng nguồn | Vẫn là một đường thay đổi khác đối với iterator hiện tại. |

Trong ví dụ tuần tự:

~~~java
List<String> values = new ArrayList<>(List.of("A", "B", "C"));
Iterator<String> iterator = values.iterator();

iterator.next(); // A
values.set(1, "B*");
System.out.println(iterator.next()); // B* với ArrayList trong ví dụ.
~~~

Iterator không phải snapshot bất biến. Không có `CME` sau `set` không có nghĩa iterator vẫn thấy nội dung ban đầu.

Không suy rộng bảng này thành quy tắc “mọi loại collection chỉ có thể vô hiệu iterator khi size thay đổi”. Mỗi loại nguồn có cấu trúc, hợp đồng phương thức và chính sách duyệt riêng.

Đặc biệt, không dùng việc một kiểu sửa đổi không bị bộ đếm phát hiện để kết luận rằng kiểu sửa đổi đó an toàn giữa các luồng hoặc đúng với thuật toán nghiệp vụ đang chạy.

## 5. Vì sao sửa qua chính iterator có thể hợp lệ?

Khi iterator hỗ trợ `remove()`, nó có thể vừa sửa nguồn vừa điều chỉnh trạng thái duyệt và phiên bản kỳ vọng của chính nó:

~~~java
List<String> values = new ArrayList<>(List.of("A", "B", "C"));
Iterator<String> iterator = values.iterator();

while (iterator.hasNext()) {
    String value = iterator.next();
    if (value.equals("B")) {
        iterator.remove();
    }
}

System.out.println(values); // [A, C]
~~~

Không thay `iterator.remove()` thành `values.remove(value)` rồi cho rằng hai đường thao tác tương đương.

Một `ListIterator` hỗ trợ chỉnh sửa còn có `add` và `set` với các quy tắc trạng thái riêng. Việc chỉnh sửa được phép này phải đi qua **chính bộ duyệt đang được dùng**, trong đúng điều kiện và trên nguồn có hỗ trợ.

### Một iterator hợp lệ không bảo vệ mọi iterator khác

Nếu có hai iterator `first` và `second` trên cùng một ArrayList, `first.remove()` có thể cập nhật đúng trạng thái của first. Nhưng second không vì vậy mà tự được cập nhật tất cả trạng thái kỳ vọng.

Tương tự, việc một danh sách được sửa đúng cách ở một đoạn mã không tự hợp thức hóa những iterator cũ còn giữ ở nơi khác.

Đây là lý do cần xác định vòng đời và quyền sửa nguồn của từng lượt duyệt, thay vì chỉ tìm xem “ở đâu đó đã dùng iterator.remove hay chưa”.

## 6. Fail-fast không có nghĩa kiểm tra ở mọi lời gọi

Iterator có thể kiểm tra tại `next()`, `remove()`, một số bước của `forEachRemaining` hoặc các điểm khác. Không có quy tắc chung rằng `hasNext()` luôn kiểm tra đầy đủ mọi thay đổi.

Với bản OpenJDK 24.0.2 dùng để chạy ví dụ bài này, kiểm tra bytecode của `ArrayList$Itr` cho thấy:

- `hasNext()` so vị trí con trỏ với kích thước hiện tại.
- `next()` gọi kiểm tra thay đổi trước khi lấy dữ liệu.

Có thể tự xem trên JDK đang dùng:

~~~shell
javap -c -p 'java.util.ArrayList$Itr'
~~~

Tên lớp nội bộ và chi tiết bytecode chỉ phục vụ tìm hiểu phiên bản đang chạy. Mã ứng dụng không nên dựa vào chúng để chọn những đường sửa nguồn “né được kiểm tra”.

### Ví dụ có thể bỏ sót mà không có CME

~~~java
List<String> values = new ArrayList<>(List.of("A", "B"));
List<String> visited = new ArrayList<>();

for (String value : values) {
    visited.add(value);
    if (value.equals("A")) {
        values.remove(value);
    }
}
~~~

Trong lần chạy kiểm chứng của bài:

~~~text
visited = [A]
values  = [B]
Không có CME
~~~

Diễn biến ở triển khai được quan sát:

1. Iterator lấy A, con trỏ tiến đến vị trí `1`.
2. Xóa A trực tiếp làm danh sách còn B ở vị trí `0`, size là `1`.
3. `hasNext()` so con trỏ `1` với size `1`, nên kết thúc.
4. Không gọi `next()` nữa để chạm điểm phát hiện phiên bản đã lệch.

B còn trong danh sách nhưng không được đưa vào `visited`.

Đây là minh họa giới hạn phát hiện, không phải hành vi hợp lệ để ứng dụng khai thác. Không viết logic cần “vòng này sẽ im lặng bỏ qua phần tử” hoặc kiểm thử đa nền tảng ép mọi JDK phải cho đúng hiện tượng này.

## 7. Best-effort: giới hạn bảo đảm cần hiểu

“Nỗ lực phát hiện tốt nhất có thể” trong ngữ cảnh fail-fast có các hệ quả:

- Một dấu hiệu thay đổi bị phát hiện có thể làm lượt duyệt thất bại.
- Không có cam kết mọi sửa đổi sai đều được phát hiện.
- Không có cam kết ngoại lệ xuất hiện đúng tại lệnh sửa nguồn.
- Không có cam kết mọi phương thức iterator kiểm tra ở cùng một điểm giữa các phiên bản.
- Không có cam kết không ném lỗi nghĩa là có một ảnh chụp dữ liệu nhất quán.

Trong tình huống nhiều luồng không đồng bộ, còn có vấn đề nhìn thấy dữ liệu và thứ tự thực thi. Một bộ đếm phát hiện sửa đổi không tự cung cấp khóa, thao tác nguyên tử, quan hệ đồng bộ hoặc một giao dịch đọc.

Ngay cả nếu đổi một trường quan sát thành `volatile` ở một thiết kế riêng, việc thấy bộ đếm mới cũng không tự biến cả chuỗi “kiểm tra rồi lấy phần tử” thành một thao tác nguyên tử. Bài toán đồng bộ phải được thiết kế ở cấp phù hợp.

Đừng xây tính đúng của chương trình theo mô hình:

~~~text
Có CME -> dữ liệu không an toàn
Không có CME -> dữ liệu an toàn
~~~

Không có nhánh thứ hai như một bảo đảm chung.

## 8. Fail-fast không hoàn tác công việc đã thực hiện

Trong ví dụ xóa B sau khi đã xử lý A, lỗi có thể chỉ xảy ra ở lời gọi iterator tiếp theo. Trước thời điểm đó:

- Hành động với A có thể đã chạy.
- Danh sách có thể đã bị thay đổi.
- Log, bộ đếm, tệp hoặc tác vụ bên ngoài có thể đã được ghi.

Bắt `CME` không khôi phục dữ liệu nguồn hay hoàn tác những tác động đó.

Không dùng cách bắt rồi bỏ qua:

~~~java
// Ý tưởng không đủ để bảo đảm thuật toán đúng.
try {
    // Duyệt và thực hiện công việc.
} catch (ConcurrentModificationException exception) {
    // Bỏ qua lỗi không chứng minh mọi phần tử đã được xử lý.
}
~~~

Cũng không tự động chạy lại toàn bộ vòng lặp chỉ vì bắt được CME. Nếu lần trước đã gửi thông báo cho A, lần thử lại có thể gửi A thêm một lần nữa; đồng thời dữ liệu nguồn có thể tiếp tục thay đổi.

Chỉ retry khi đã có một thiết kế phù hợp về quyền sở hữu dữ liệu, ảnh chụp, tính lặp an toàn và checkpoint nghiệp vụ. Trong nhiều lỗi một luồng, cách sửa đúng đơn giản là thay đường chỉnh sửa nguồn hoặc tách giai đoạn đọc và cập nhật.

## 9. Các đường sửa nguồn khó nhìn thấy

Không chỉ lời gọi `values.add/remove` ngay trong thân vòng lặp mới có liên quan.

### Alias cùng đối tượng

Hai biến có tên khác nhau có thể cùng trỏ một collection:

~~~java
List<String> values = new ArrayList<>(List.of("A", "B", "C"));
List<String> alias = values;
Iterator<String> iterator = values.iterator();

alias.remove("B"); // Vẫn sửa đúng nguồn mà iterator đang dùng.
~~~

Đổi tên biến không tạo bản sao hoặc ranh giới sở hữu mới.

### View và collection phía sau

Các góc nhìn như `subList`, `keySet`, `values` hoặc `entrySet` có thể liên hệ với nguồn phía sau. Sửa qua một đường có thể ảnh hưởng lượt duyệt ở một đường khác, theo hợp đồng của loại nguồn cụ thể.

Không tự coi một view là một snapshot độc lập. Với ArrayList, thay cấu trúc danh sách cha trong lúc còn dùng một subList cũng có những giới hạn hợp đồng cần xem xét, không chỉ lỗi của một iterator riêng.

### Callback hoặc lời gọi lồng nhau

Thân vòng lặp có thể chỉ gọi:

~~~java
process(value);
~~~

nhưng `process` lại sửa collection nguồn, hoặc phát sự kiện tới một đoạn mã sửa nó. Khi điều tra, phải lần theo các đường ghi thực tế, không chỉ tìm chữ `remove` ở cùng một phương thức.

### Góc nhìn không cho sửa không phải snapshot

`Collections.unmodifiableList(backing)` chặn thay đổi qua góc nhìn đó. Nếu nơi khác vẫn giữ và sửa `backing`, dữ liệu và iterator của góc nhìn không tự trở thành một bản sao bất biến.

Giữ một lớp bao bọc chỉ đọc khác với tạo một dữ liệu độc lập để duyệt.

## 10. Chọn cách xử lý đúng cho một luồng

| Nhu cầu | Cách xử lý thường phù hợp |
| --- | --- |
| Xóa phần tử thỏa điều kiện trong nguồn có thể sửa | `removeIf`, hoặc vòng lặp với `iterator.remove` nếu được hỗ trợ. |
| Vừa duyệt List vừa chèn/thay/xóa ở vị trí duyệt | `ListIterator` với thứ tự thao tác hợp lệ. |
| Giữ nguyên nguồn và tạo dữ liệu đã lọc | Tạo collection kết quả mới. |
| Cần thêm nhiều phần tử sau khi duyệt nguồn | Thu vào một collection phụ rồi thêm ở giai đoạn sau. |
| Cần đọc một trạng thái cố định | Chụp dữ liệu theo một quy trình phù hợp, rồi duyệt bản chụp. |

Ví dụ xóa bằng điều kiện:

~~~java
List<String> values = new ArrayList<>(List.of("A", "B", "C"));
values.removeIf(value -> value.equals("B"));

System.out.println(values); // [A, C]
~~~

Đừng tự sửa chính nguồn trong predicate của `removeIf`. Dùng API chuyên dụng không có nghĩa callback được quyền thực hiện mọi thay đổi khác lên collection đang được xử lý.

Nếu chỉ sửa vòng lặp thành duyệt theo chỉ số để không còn iterator, chương trình có thể không còn gặp cùng ngoại lệ. Nhưng logic vẫn có thể bỏ sót vì chỉ số bị dịch, và truy cập nhiều luồng vẫn chưa được giải quyết. Mục tiêu là đúng thuật toán, không phải biến mất thông báo lỗi.

## 11. Khi nhiều luồng: khóa phải bảo vệ cả lượt duyệt

`ArrayList` không tự cung cấp đồng bộ cho việc nhiều luồng cùng truy cập và sửa cấu trúc. Một lựa chọn là bao bọc bằng `Collections.synchronizedList`, nhưng khi duyệt cần giữ khóa đúng phạm vi và mọi đường truy cập nguồn phải theo cùng giao thức. Xem [Collections.synchronizedList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedList(java.util.List)).

~~~java
// Minh họa một phần thiết kế; cần các import java.util tương ứng.
List<String> shared =
        Collections.synchronizedList(new ArrayList<>());

// Các đoạn mã khác cũng phải truy cập qua shared theo quy tắc đồng bộ phù hợp.
synchronized (shared) {
    Iterator<String> iterator = shared.iterator();
    while (iterator.hasNext()) {
        String value = iterator.next();
        System.out.println(value);
    }
}
~~~

Chỉ khóa lúc tạo iterator rồi nhả trước vòng lặp không bảo vệ toàn bộ lượt duyệt.

Chỉ khóa từng lời gọi `hasNext` và `next` riêng lẻ cũng không tương đương giữ một khóa xuyên suốt nếu thuật toán cần một lượt đọc không bị xen thay đổi.

Ngay cả đang giữ khóa, chính luồng đó vẫn có thể sửa nguồn sai cách trong thân vòng lặp và làm iterator mất hiệu lực. Khóa giải quyết việc xen kẽ truy cập theo giao thức; nó không bỏ quy tắc trạng thái của iterator.

### Chụp nhanh trong khóa rồi xử lý ngoài khóa

Nếu phần xử lý lâu và chỉ cần nội dung ở thời điểm chụp:

~~~java
List<String> snapshot;
synchronized (shared) {
    snapshot = new ArrayList<>(shared);
}

for (String value : snapshot) {
    System.out.println(value);
}
~~~

Bản chụp độc lập về cấu trúc danh sách, nhưng là bản sao nông: các đối tượng phần tử vẫn có thể được chia sẻ. Cần xác định chính sách cho nội dung có thể thay đổi bên trong phần tử.

Cũng không tạo snapshot của một ArrayList đang bị luồng khác sửa mà không có giao thức phù hợp rồi cho rằng thao tác copy tự bảo đảm một ảnh chụp nhất quán.

## 12. Các chính sách iterator khác với fail-fast

Không phải mọi iterator đều chọn cách phát hiện rồi ném CME.

| Chính sách | Ví dụ | Điều cần hiểu |
| --- | --- | --- |
| Fail-fast | Iterator của nhiều collection thông thường như ArrayList | Hỗ trợ phát hiện sửa đổi không phù hợp, theo nỗ lực tốt nhất có thể. |
| Ảnh chụp tại thời điểm tạo iterator | Iterator của CopyOnWriteArrayList | Duyệt trạng thái mà iterator đã chụp; không cập nhật thành viên theo các thay đổi cấu trúc sau đó. |
| Nhất quán yếu (weakly consistent) | Iterator của ConcurrentHashMap | Có thể hoạt động trong lúc cập nhật theo chính sách của nguồn; không phải một snapshot toàn bộ map tại một thời điểm chung. |

Iterator của [CopyOnWriteArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html) giữ một ảnh chụp cấu trúc khi được tạo; các thao tác thay đổi qua iterator như remove không được hỗ trợ. Cơ chế này có chi phí ghi và bộ nhớ cần cân nhắc, không phải giải pháp mặc định cho mọi danh sách.

Ví dụ:

~~~text
Nguồn lúc tạo iterator: [A, B]
Sau đó nguồn thêm C và xóa A: [B, C]
Iterator ảnh chụp vẫn duyệt: [A, B]
~~~

Ảnh chụp là về tập các tham chiếu phần tử của lượt duyệt, không tự sao chép sâu toàn bộ đối tượng.

Iterator của [ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html) không ném CME theo chính sách của nó. Điều đó không có nghĩa một lượt duyệt cho ảnh chụp giao dịch của toàn bộ map, và bản thân một iterator không phải đối tượng để tùy ý chia sẻ giữa nhiều luồng cùng điều khiển con trỏ.

Không gom các loại này thành một nhãn “fail-safe” rồi mặc nhiên suy ra mọi bảo đảm về nhìn thấy cập nhật, snapshot, thao tác ghi hoặc đồng bộ đều giống nhau. Hãy dùng đúng mô tả chính sách của lớp cụ thể.

## 13. Chương trình kiểm chứng hoàn chỉnh

Lưu thành **FailFastLimitsDemo.java**.

Mã dùng API có từ Java 17. Những đoạn cố ý vi phạm lượt duyệt là **thí nghiệm quan sát**, không phải mã ứng dụng mẫu cần dùng. Kết quả quan sát dưới đây được kiểm chứng trên OpenJDK 24.0.2; đặc biệt hiện tượng vòng lặp sai nhưng không ném CME là chi tiết cần quan sát lại khi đổi triển khai.

Không dùng một hiện tượng không được bảo đảm làm yêu cầu hành vi đa phiên bản.

~~~java
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FailFastLimitsDemo {
    public static void main(String[] args) {
        System.out.println("Runtime feature = " + Runtime.version().feature());
        directModification();
        sameSizeAfterTwoChanges();
        nonStructuralReplacement();
        skippedWithoutException();
        safeIteratorRemoval();
        safeRemoveIf();
        snapshotIteration();
    }

    private static void directModification() {
        List<String> values = new ArrayList<>(List.of("A", "B", "C"));
        Iterator<String> iterator = values.iterator();
        System.out.println("Direct edit first = " + iterator.next());
        values.remove("B");
        try {
            System.out.println("Direct edit next = " + iterator.next());
        } catch (ConcurrentModificationException exception) {
            System.out.println("Direct edit detected = "
                    + exception.getClass().getSimpleName());
        }
        System.out.println("After detected edit = " + values);
    }

    private static void sameSizeAfterTwoChanges() {
        List<String> values = new ArrayList<>(List.of("A", "B", "C"));
        Iterator<String> iterator = values.iterator();
        values.add("D");
        values.remove("D");
        System.out.println("Same contents restored = " + values);
        try {
            iterator.next();
            System.out.println("Restored contents detected = false");
        } catch (ConcurrentModificationException exception) {
            System.out.println("Restored contents detected = true");
        }
    }

    private static void nonStructuralReplacement() {
        List<String> values = new ArrayList<>(List.of("A", "B", "C"));
        Iterator<String> iterator = values.iterator();
        iterator.next();
        values.set(1, "B*");
        System.out.println("Next after ArrayList.set = " + iterator.next());
    }

    private static void skippedWithoutException() {
        List<String> values = new ArrayList<>(List.of("A", "B"));
        List<String> visited = new ArrayList<>();
        boolean detected = false;
        try {
            for (String value : values) {
                visited.add(value);
                if (value.equals("A")) {
                    values.remove(value);
                }
            }
        } catch (ConcurrentModificationException exception) {
            detected = true;
        }
        System.out.println("Short loop visited = " + visited);
        System.out.println("Short loop remaining = " + values);
        System.out.println("Short loop detected = " + detected);
    }

    private static void safeIteratorRemoval() {
        List<String> values = new ArrayList<>(List.of("A", "B", "C"));
        Iterator<String> iterator = values.iterator();
        while (iterator.hasNext()) {
            String value = iterator.next();
            if (value.equals("B")) {
                iterator.remove();
            }
        }
        System.out.println("Iterator removal = " + values);
    }

    private static void safeRemoveIf() {
        List<String> values = new ArrayList<>(List.of("A", "B", "C"));
        values.removeIf(value -> value.equals("B"));
        System.out.println("removeIf = " + values);
    }

    private static void snapshotIteration() {
        CopyOnWriteArrayList<String> values =
                new CopyOnWriteArrayList<>(List.of("A", "B"));
        Iterator<String> snapshot = values.iterator();
        values.add("C");
        values.remove("A");

        List<String> observed = new ArrayList<>();
        snapshot.forEachRemaining(observed::add);
        System.out.println("Snapshot observed = " + observed);
        System.out.println("Copy-on-write current = " + values);
    }
}
~~~

Biên dịch bằng JDK 17 trở lên:

~~~shell
javac --release 17 -encoding UTF-8 FailFastLimitsDemo.java
java FailFastLimitsDemo
~~~

`--release 17` kiểm tra mức ngôn ngữ/API đích khi biên dịch; nó không biến JVM chạy chương trình thành Java 17.

Stdout trong lần chạy kiểm chứng trên OpenJDK 24.0.2:

~~~text
Runtime feature = 24
Direct edit first = A
Direct edit detected = ConcurrentModificationException
After detected edit = [A, C]
Same contents restored = [A, B, C]
Restored contents detected = true
Next after ArrayList.set = B*
Short loop visited = [A]
Short loop remaining = [B]
Short loop detected = false
Iterator removal = [A, C]
removeIf = [A, C]
Snapshot observed = [A, B]
Copy-on-write current = [B, C]
~~~

### Đọc bằng chứng theo đúng giới hạn

**Direct edit**

A đã được lấy, rồi xóa B trực tiếp. Lần next tiếp theo phát hiện thay đổi. Danh sách đã thành A, C trước lúc ngoại lệ được báo; ngoại lệ không hoàn tác việc xóa B.

**Same contents restored**

Thêm D rồi xóa D đưa nội dung về A, B, C. Iterator tạo trước hai thay đổi vẫn bị phát hiện không còn phù hợp. Kích thước hoặc nội dung cuối cùng không thể thay cho lịch sử thay đổi liên quan đến iterator.

**ArrayList.set**

Thay B bằng B* không đổi cấu trúc theo chính sách của ArrayList. Lần next tuần tự quan sát B*, thể hiện iterator không giữ bản sao ban đầu của các phần tử.

**Short loop**

Chỉ A được đưa vào visited, B còn lại trong nguồn, và không có CME trong lần chạy này. Đây là bằng chứng cụ thể rằng không ném ngoại lệ không đồng nghĩa thuật toán đã duyệt đủ.

**Iterator removal và removeIf**

Hai cách được dùng theo hợp đồng trên ArrayList có thể sửa nguồn và cho A, C như mong muốn. Chúng không dựa vào việc một lỗi fail-fast có xuất hiện hay không.

**Copy-on-write snapshot**

Iterator được tạo khi nguồn có A, B. Nguồn sau đó thành B, C nhưng iterator vẫn thu được A, B theo chính sách ảnh chụp. Đây là chính sách khác với fail-fast, không phải fail-fast “không hoạt động”.

Chương trình không có lịch chạy nhiều luồng ngẫu nhiên. Nhờ vậy các ví dụ tách được lỗi dùng iterator trong một luồng khỏi vấn đề đồng bộ. Khi học truy cập đồng thời, cần kiểm thử các bảo đảm tương ứng của thiết kế riêng, không dùng xác suất xuất hiện CME làm thước đo.

## 14. Cách điều tra khi gặp CME

1. **Xác định nguồn thực tế:** ArrayList, HashMap, một view, một lớp bao bọc hay cấu trúc concurrent?
2. **Xác định lúc iterator được tạo:** có được giữ qua nhiều giai đoạn, callback hoặc lời gọi khác không?
3. **Tìm mọi đường ghi:** biến alias, danh sách cha, view, callback, listener, luồng khác.
4. **Đối chiếu chính sách:** thao tác thay đổi có được thực hiện qua đúng iterator đang duyệt và được hỗ trợ không?
5. **Xem công việc đã chạy trước lỗi:** có tác động nào cần xử lý hoặc tránh lặp lại khi sửa không?
6. **Chọn mô hình đúng:** sửa qua iterator, tách giai đoạn, chụp dữ liệu, khóa đúng phạm vi hoặc dùng cấu trúc concurrent phù hợp.

Stack trace thường chỉ điểm nơi iterator **phát hiện** bất nhất, không nhất thiết trỏ trực tiếp vào lệnh đã sửa nguồn. Vì vậy chỉ sửa dòng đang ném mà không lần theo đường ghi có thể che lỗi mà không giải quyết nguyên nhân.

Sau khi sửa, kiểm tra nội dung kết quả, số lượng phần tử được xử lý, số lần tác động và chính sách nhìn thấy cập nhật. Đừng dừng ở việc “chạy không còn ngoại lệ”.

## 15. Kiểm thử và lỗi thường gặp

Các ca cần có:

- Sửa trực tiếp trong cùng luồng sau khi đã tạo iterator.
- Sửa qua alias hoặc collection phía sau một view.
- Xóa phần tử đầu, giữa và gần cuối trong các danh sách có kích thước khác nhau.
- Thêm rồi xóa để nội dung cuối trở lại như cũ.
- Dùng iterator.remove đúng điều kiện.
- Hai iterator cùng nguồn nhưng chỉ một iterator thực hiện thay đổi.
- Chụp dữ liệu và kiểm tra đúng chính sách nhìn thấy trạng thái cũ.
- Các đường ghi và đọc đồng thời theo giao thức được thiết kế.

Một ca thí nghiệm có thể ghi nhận CME để minh họa. Nhưng kiểm thử tính đúng của mã ứng dụng phải kiểm tra các bất biến nghiệp vụ và kết quả. Không yêu cầu một lỗi dùng sai nguồn luôn phải ném CME tại đúng dòng trên mọi phiên bản Java.

| Hiểu nhầm | Cách hiểu cần giữ |
| --- | --- |
| CME chỉ do nhiều luồng | Một luồng sửa nguồn trong lúc còn dùng iterator cũng có thể gây lỗi. |
| Sửa nguồn là ném ngay lập tức | Thường lỗi được phát hiện tại điểm kiểm tra của lượt duyệt sau đó. |
| hasNext luôn kiểm tra bộ đếm | Điểm kiểm tra tùy triển khai; không dựa vào giả định này. |
| Không có CME thì đã duyệt đủ | Có thể bỏ sót hoặc có quan sát không đúng mà không ném lỗi. |
| Size cuối không đổi thì iterator còn hợp lệ | Những lần sửa cấu trúc vẫn có thể đã làm phiên bản kỳ vọng lệch. |
| set không gây CME nên tự an toàn nhiều luồng | Không đổi cấu trúc không đồng nghĩa có đồng bộ hoặc snapshot. |
| Bắt rồi retry là cách sửa chung | Công việc đã thực hiện có thể bị lặp, còn nguồn vẫn tiếp tục thay đổi. |
| Synchronized wrapper tự khóa cả vòng lặp | Cần bảo vệ đúng phạm vi duyệt theo hợp đồng và mọi đường truy cập. |
| Cấu trúc concurrent luôn cho snapshot toàn bộ | Chính sách weakly consistent khác với snapshot. |
| Đổi sang vòng lặp chỉ số là xong | Có thể vẫn sai về dịch chỉ số, bỏ sót và truy cập đồng thời. |

## 16. Thực hành

1. Chạy chương trình mẫu trên JDK đang dùng và ghi rõ phiên bản. Phân biệt kết quả quan sát với các cam kết API.
2. Thay danh sách hai phần tử trong `skippedWithoutException` bằng ba hoặc bốn phần tử. Không đặt trước yêu cầu “phải luôn ném”; hãy kiểm tra những phần tử thực sự được duyệt.
3. Sửa ví dụ xóa trực tiếp thành iterator.remove, rồi thành removeIf. Kiểm chứng kết quả thay vì chỉ kiểm chứng không có ngoại lệ.
4. Tạo hai biến trỏ cùng danh sách và sửa qua biến còn lại. Giải thích vì sao iterator vẫn liên quan đến cùng nguồn.
5. Thiết kế một lượt đọc cần ảnh chụp nhất quán và một lượt theo dõi chấp nhận cập nhật trong khi duyệt. Chọn chính sách dữ liệu cho từng trường hợp.
6. Với một vòng lặp có gửi thông báo, phân tích nguy cơ nếu bắt CME rồi chạy lại từ đầu. Xác định cần giữ bằng chứng xử lý hoặc tính lặp an toàn ở đâu.

CME cho biết một dấu hiệu sử dụng collection không phù hợp đã được phát hiện. Tính đúng phải đến từ quyền sở hữu nguồn, cách chỉnh sửa, chính sách duyệt và giao thức đồng bộ; việc có hay không có ngoại lệ chỉ là một phần bằng chứng khi tìm lỗi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="iteration-008">Phân biệt iterator fail-fast, ảnh chụp và nhất quán yếu</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt iterator fail-fast, ảnh chụp và nhất quán yếu

Một iterator được tạo khi collection đang ở trạng thái `S0`. Trước khi iterator duyệt hết, chương trình thêm, xóa hoặc thay dữ liệu để collection chuyển sang `S1`. Iterator sẽ làm gì?

Có ba hợp đồng cần phân biệt:

- **Fail-fast:** cố phát hiện sửa đổi không được phép để báo lỗi sử dụng.
- **Ảnh chụp (snapshot):** tiếp tục duyệt trạng thái mà iterator đã chụp.
- **Nhất quán yếu (weakly consistent):** cho phép duyệt cùng với cập nhật theo bảo đảm của nguồn, nhưng không cung cấp một ảnh chụp toàn bộ tại một thời điểm.

Chúng khác nhau về ý nghĩa dữ liệu nhìn thấy, cách xử lý thay đổi và chi phí. Không thể chọn chỉ bằng câu hỏi “có ném ngoại lệ hay không”.

Bài lấy Java 17 làm mốc. Bốn chương trình hoàn chỉnh đã được biên dịch với `--release 17` và chạy. Với ví dụ nhất quán yếu, stdout của một lần chạy được trình bày riêng với những bảo đảm có thể dựa vào; không biến một kết quả quan sát thành lời hứa của mọi lần thực thi.

Sau bài học, bạn có thể:

- Nhận diện ba kiểu duyệt qua hợp đồng của nguồn thực tế.
- Giải thích vì sao `ConcurrentModificationException` không đồng nghĩa luôn có nhiều luồng.
- Phân biệt snapshot cấu trúc với bản sao sâu của phần tử.
- Giải thích vì sao collection an toàn luồng chưa tạo ra lượt duyệt nguyên tử.
- Chọn giữa khóa, sao chép và cấu trúc đồng thời theo yêu cầu dữ liệu.

## 1. Bắt đầu từ một thay đổi trong vòng đời iterator

Giả sử nguồn ban đầu là:

```text
[A, B, C]
```

Trình tự:

```text
t0: tạo iterator
t1: iterator nhận A
t2: nguồn bị thêm D hoặc xóa B
t3: iterator tiếp tục
```

Câu hỏi không chỉ là “vòng lặp còn chạy được không”. Ta còn cần biết:

1. Dữ liệu mà iterator tiếp tục nhận thuộc trạng thái nào?
2. Thay đổi ở `t2` có được hợp đồng của nguồn cho phép không?
3. Có thể dùng kết quả để kết luận về toàn nguồn tại một thời điểm không?
4. Ai bảo vệ các đối tượng phần tử nếu chúng cũng thay đổi?

Cùng một cú pháp enhanced for có thể đi qua iterator với những hợp đồng rất khác. Cú pháp vòng lặp không tự quyết định cách nhất quán.

## 2. Bảng đối chiếu

| Câu hỏi | Fail-fast | Snapshot | Nhất quán yếu |
| --- | --- | --- | --- |
| Mục tiêu chính | Phát hiện sửa đổi không hợp lệ | Duyệt một trạng thái cấu trúc đã xác định | Tiếp tục duyệt trong bối cảnh nguồn được cập nhật |
| Có phải ảnh chụp toàn nguồn? | Không | Có đối với phạm vi snapshot được cam kết | Không mặc định |
| Thay đổi sau khi tạo iterator | Có thể bị phát hiện là lỗi | Không đổi tập tham chiếu đã chụp | Có thể được phản ánh theo hợp đồng nguồn |
| CME do thay đổi nguồn | Cơ chế phát hiện ở mức cố gắng | Không xảy ra với thay đổi được hợp đồng snapshot cho phép | Không xảy ra đối với cập nhật mà hợp đồng duyệt cho phép |
| Chỉnh sửa qua iterator | Tùy nguồn và thao tác | Iterator của CopyOnWriteArrayList không hỗ trợ | Tùy loại cụ thể |
| Ví dụ trong bài | ArrayList | CopyOnWriteArrayList | Các góc nhìn của ConcurrentHashMap |

Bảng là cách định hướng. Khi viết mã, vẫn phải xác định đúng lớp và đúng API tạo iterator. Không suy từ tên giao diện `List`, `Set` hoặc `Map` ra toàn bộ chính sách duyệt.

“Không có CME” cũng không trả lời được câu hỏi snapshot: cả snapshot và nhất quán yếu đều có thể tiếp tục khi nguồn thay đổi, nhưng kết quả dữ liệu mang ý nghĩa khác nhau.

## 3. Fail-fast: công cụ phát hiện lỗi sử dụng

Iterator của `ArrayList` phát hiện những sửa đổi cấu trúc không đi qua cơ chế được iterator cho phép. Thêm hoặc xóa là những ví dụ điển hình; thay giá trị một phần tử bằng `set` không được ArrayList xem là sửa đổi cấu trúc. Cơ chế này không có bảo đảm phát hiện mọi vi phạm, đặc biệt khi cập nhật không được đồng bộ. Xem [hợp đồng ArrayList của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html).

### Mô hình giải thích bằng số phiên bản

Có thể hình dung một lớp triển khai theo dõi:

```text
Phiên bản cấu trúc của nguồn
Phiên bản mà iterator đang kỳ vọng
```

Khi phát hiện hai thông tin không còn phù hợp, iterator báo rằng nó không thể tiếp tục theo cách sử dụng hiện tại.

Đây là mô hình để hiểu ý định phát hiện lỗi, không phải yêu cầu mọi iterator phải có cùng tên trường, thời điểm kiểm tra hoặc cách cài đặt.

Nó giúp giải thích hai tình huống:

- Sửa qua chính iterator đang dùng, nếu được hỗ trợ, có thể cập nhật trạng thái duyệt một cách phối hợp.
- Sửa trực tiếp qua nguồn có thể làm iterator đang tồn tại không còn hợp lệ.

### Một luồng cũng có thể gây ConcurrentModificationException

Một luồng có thể tạo iterator, thêm trực tiếp vào danh sách rồi gọi iterator tiếp. Không cần có luồng thứ hai để vi phạm hợp đồng. Tài liệu [ConcurrentModificationException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ConcurrentModificationException.html) cũng nhấn mạnh đây là cơ chế tìm lỗi, không phải điều kiện để bảo đảm tính đúng đắn.

Vì vậy, không nên:

- Xem có ngoại lệ là bằng chứng chắc chắn đã có hai luồng chạy cùng lúc.
- Xem không có ngoại lệ là bằng chứng chương trình không có lỗi.
- Bắt ngoại lệ rồi tự tiếp tục trên iterator cũ.
- Dùng việc thử đến khi không còn ngoại lệ làm cơ chế đồng bộ.

Một thao tác có thể đã gây tác dụng phụ trước khi lỗi được phát hiện. Việc chạy lại toàn bộ vòng lặp còn có thể lặp lại công việc, nên “catch rồi retry” không phải cách sửa tổng quát.

## 4. Chương trình fail-fast trong một luồng

Tạo tệp **`FailFastIteratorDemo.java`**:

```java
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;

public class FailFastIteratorDemo {
    public static void main(String[] args) {
        List<Integer> values = new ArrayList<>(List.of(1, 2, 3));
        Iterator<Integer> cursor = values.iterator();

        System.out.println("first = " + cursor.next());
        values.add(4);

        try {
            cursor.next();
        } catch (ConcurrentModificationException exception) {
            System.out.println("external add = "
                    + exception.getClass().getSimpleName());
        }

        Iterator<Integer> supported = values.iterator();
        while (supported.hasNext()) {
            if (supported.next() % 2 == 0) {
                supported.remove();
            }
        }
        System.out.println("after iterator removal = " + values);

        List<String> labels = new ArrayList<>(List.of("A", "B"));
        Iterator<String> labelCursor = labels.iterator();
        labels.set(1, "B*");

        List<String> observed = new ArrayList<>();
        labelCursor.forEachRemaining(observed::add);
        System.out.println("replacement observed = " + observed);
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 FailFastIteratorDemo.java
java -Dfile.encoding=UTF-8 FailFastIteratorDemo
```

Stdout đã kiểm chứng:

```text
first = 1
external add = ConcurrentModificationException
after iterator removal = [1, 3]
replacement observed = [A, B*]
```

### Đọc ba phần thử nghiệm

**Phần thứ nhất:**

1. Iterator nhận `1`.
2. Chính luồng đó thêm `4` trực tiếp vào danh sách.
3. Lần `next` tiếp theo phát hiện sửa đổi và ném CME trong tình huống cụ thể này.

Đây là ví dụ tái hiện có kiểm soát của ArrayList, không phải khẳng định rằng mọi kiểu thay đổi trên mọi iterator sẽ báo lỗi tại cùng điểm.

**Phần thứ hai:**

- Chương trình tạo iterator mới trên danh sách hiện tại.
- Nó xóa số chẵn bằng `supported.remove()`, qua chính iterator đang duyệt.
- Kết quả là `[1, 3]`.

Việc tạo iterator mới ở đây bắt đầu một lượt xử lý đã được thiết kế lại sau thử nghiệm. Nó không minh họa một thuật toán tự phục hồi bằng cách lặp vô hạn sau mỗi lỗi.

**Phần thứ ba:**

- Iterator được tạo trước khi `labels.set(1, "B*")`.
- Lượt duyệt vẫn đọc `[A, B*]` trong thử nghiệm.
- Ví dụ cho thấy fail-fast không có nghĩa “đóng băng tất cả dữ liệu kể từ thời điểm tạo”.

### Không biến đặc tính của ArrayList thành quy tắc cho mọi collection

Khái niệm sửa đổi cấu trúc phụ thuộc loại nguồn. Đặc biệt:

- Thay giá trị không phải thêm/xóa phần tử.
- Sửa nội dung một đối tượng phần tử còn là một lớp thay đổi khác.
- Những thao tác như sắp xếp hoặc thay đổi qua góc nhìn cần được xem theo hợp đồng cụ thể.

Không nên đoán rằng chỉ cần giữ nguyên `size` thì mọi thay đổi đều được iterator chấp nhận.

## 5. Snapshot: giữ trạng thái cấu trúc của một thời điểm

Iterator của `CopyOnWriteArrayList` giữ tham chiếu tới trạng thái mảng tại lúc nó được tạo. Các lần thêm, xóa hoặc thay phần tử của danh sách về sau không đổi dữ liệu cấu trúc mà iterator đó duyệt. Các thao tác thay đổi qua iterator như remove, set, add không được hỗ trợ. Xem [CopyOnWriteArrayList Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html).

Có thể vẽ mô hình:

```text
t0:
iterator cũ -> mảng chứa A, B, C
danh sách   -> mảng chứa A, B, C

t1 sau thay đổi:
iterator cũ -> mảng cũ chứa A, B, C
danh sách   -> trạng thái mới chứa A*, C, D
```

Các phần tử mới thuộc danh sách hiện tại không tự xuất hiện trong iterator cũ.

### Snapshot không có nghĩa danh sách sống ngừng thay đổi

Hai sự thật có thể đồng thời đúng:

- Một bên tiếp tục cập nhật danh sách.
- Một iterator đang tồn tại vẫn duyệt một tập tham chiếu ổn định.

Điều này phù hợp với những bài toán mà một lượt xử lý được phép dùng danh sách tại lúc bắt đầu, chẳng hạn danh sách bộ lắng nghe sự kiện cho một lần thông báo. Một bộ lắng nghe mới được đăng ký có thể chỉ cần tham gia lượt sau, theo chính sách ứng dụng đã chọn.

### Snapshot cấu trúc không tự là bản sao sâu

Nếu phần tử là một đối tượng khả biến, snapshot vẫn có thể giữ tham chiếu tới chính đối tượng đó.

Do đó cần phân biệt:

1. Thay phần tử ở một vị trí của danh sách.
2. Sửa thuộc tính bên trong đối tượng mà vị trí đó đang tham chiếu.

Chương trình sau kiểm chứng sự khác biệt trong một luồng, để loại khỏi ví dụ những câu hỏi riêng về khả năng nhìn thấy dữ liệu giữa các luồng.

## 6. Chương trình snapshot và phần tử khả biến

Tạo tệp **`SnapshotIteratorDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SnapshotIteratorDemo {
    private static final class Score {
        private int value;

        private Score(int value) {
            this.value = value;
        }
    }

    public static void main(String[] args) {
        CopyOnWriteArrayList<String> values =
                new CopyOnWriteArrayList<>(List.of("A", "B", "C"));
        Iterator<String> beforeChanges = values.iterator();

        values.set(0, "A*");
        values.remove("B");
        values.add("D");

        List<String> oldView = new ArrayList<>();
        beforeChanges.forEachRemaining(oldView::add);

        System.out.println("old iterator = " + oldView);
        System.out.println("current list = " + values);

        Iterator<String> readOnlyCursor = values.iterator();
        readOnlyCursor.next();
        try {
            readOnlyCursor.remove();
        } catch (UnsupportedOperationException exception) {
            System.out.println("iterator remove = "
                    + exception.getClass().getSimpleName());
        }

        Score sharedScore = new Score(10);
        CopyOnWriteArrayList<Score> scores =
                new CopyOnWriteArrayList<>(List.of(sharedScore));
        Iterator<Score> oldScores = scores.iterator();

        sharedScore.value = 99;
        scores.set(0, new Score(20));

        System.out.println("old element state = " + oldScores.next().value);
        System.out.println("current element state = " + scores.get(0).value);
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 SnapshotIteratorDemo.java
java -Dfile.encoding=UTF-8 SnapshotIteratorDemo
```

Stdout đã kiểm chứng:

```text
old iterator = [A, B, C]
current list = [A*, C, D]
iterator remove = UnsupportedOperationException
old element state = 99
current element state = 20
```

### Nhóm chuỗi

- Iterator được tạo khi danh sách là `[A, B, C]`.
- Danh sách sống đổi thành `[A*, C, D]`.
- Iterator cũ vẫn duyệt `[A, B, C]`.
- Gọi remove qua iterator mới vẫn bị từ chối: khả năng sửa danh sách không kéo theo khả năng sửa qua iterator.

### Nhóm Score

1. Iterator cũ giữ tham chiếu tới đối tượng Score ban đầu.
2. Thuộc tính của đối tượng đó được đổi từ `10` thành `99`.
3. Danh sách sống được thay phần tử bằng đối tượng Score mới mang giá trị `20`.
4. Iterator cũ nhận đối tượng ban đầu, nên thấy `99`; danh sách hiện tại có đối tượng khác mang `20`.

Đây là bằng chứng rằng ảnh chụp các tham chiếu không tự sao chép trạng thái sâu của phần tử.

Nếu nhiều luồng cùng sửa thuộc tính Score, ứng dụng còn cần chính sách bất biến hoặc đồng bộ cho chính Score. Dùng collection đồng thời không tự biến mọi trường dữ liệu bên trong thành an toàn luồng.

### Chi phí cần cân nhắc

Từ mô hình giữ mảng cũ, có thể suy ra một iterator tồn tại lâu có thể kéo dài thời gian những tham chiếu cũ còn được giữ lại. Việc ghi trong mô hình copy-on-write cũng có chi phí sao chép cấu trúc.

Do đó, hãy cân nhắc tỷ lệ đọc/ghi, số phần tử và thời gian giữ iterator. Đừng chọn copy-on-write chỉ vì muốn bỏ một ngoại lệ trong mã đang sử dụng sai nguồn.

## 7. Nhất quán yếu: duyệt được trong khi nguồn cập nhật

Các cấu trúc đồng thời có thể cung cấp iterator cho phép duyệt cùng những thao tác cập nhật được hỗ trợ. Gói [java.util.concurrent](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html) gọi đây là duyệt nhất quán yếu: dữ liệu sau khi tạo iterator có thể được phản ánh, nhưng không phải mọi thay đổi đều được bảo đảm xuất hiện.

Với `ConcurrentHashMap`, iterator không ném CME vì các cập nhật được hỗ trợ; những phần tử trả về phản ánh các trạng thái tại hoặc sau lúc tạo iterator. Nó không cung cấp một lần chụp nguyên tử toàn map. Các iterator của lớp này được thiết kế để một luồng sử dụng tại một thời điểm. Xem [hợp đồng ConcurrentHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html).

### Những kết luận không được tự thêm

Không nên suy ra rằng iterator nhất quán yếu:

- Luôn nhìn thấy toàn bộ dữ liệu mới nhất khi vòng lặp kết thúc.
- Luôn bỏ qua mọi dữ liệu bị xóa.
- Luôn giữ nguyên dữ liệu tại lúc bắt đầu.
- Tự chụp toàn bộ map tại một thời điểm duy nhất.
- Tự bảo đảm bất biến nghiệp vụ liên quan nhiều khóa.

“Yếu” không có nghĩa dữ liệu tùy tiện hoặc không có hợp đồng. Nó có nghĩa mức quan sát được cam kết khác với snapshot nguyên tử; phải dựa vào bảo đảm của đúng lớp.

### Nhất quán và thứ tự là hai câu hỏi riêng

Một map có thể không hứa thứ tự duyệt theo tên khóa, trong khi vẫn bảo đảm việc duyệt phù hợp với các cập nhật được hỗ trợ.

Nếu cần trình bày kết quả theo thứ tự, có thể sắp xếp **những gì đã quan sát được**. Tuy nhiên, bước sắp xếp đó không quay ngược thời gian để làm cho dữ liệu đã thu thập trở thành một snapshot nguyên tử.

## 8. Chương trình quan sát dữ liệu cũ và mới trong cùng lượt duyệt

Tạo tệp **`WeakIteratorDemo.java`**:

```java
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class WeakIteratorDemo {
    public static void main(String[] args) throws InterruptedException {
        ConcurrentHashMap<String, Integer> values = new ConcurrentHashMap<>();
        values.put("A", 10);
        values.put("B", 20);
        values.put("C", 30);

        Iterator<Map.Entry<String, Integer>> cursor =
                values.entrySet().iterator();
        Map<String, Integer> observed = new TreeMap<>();

        Map.Entry<String, Integer> first = cursor.next();
        observed.put(first.getKey(), first.getValue());

        Thread writer = new Thread(() -> {
            values.replaceAll((key, value) -> value + 100);
            values.put("D", 40);
        });

        writer.start();
        writer.join();

        while (cursor.hasNext()) {
            Map.Entry<String, Integer> entry = cursor.next();
            observed.put(entry.getKey(), entry.getValue());
        }

        System.out.println("observed in this run = " + observed);
        System.out.println("after writer = " + new TreeMap<>(values));
        System.out.println("traversal completed = true");
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 WeakIteratorDemo.java
java -Dfile.encoding=UTF-8 WeakIteratorDemo
```

Stdout của **lần chạy đã kiểm chứng**:

```text
observed in this run = {A=10, B=120, C=130, D=40}
after writer = {A=110, B=120, C=130, D=40}
traversal completed = true
```

Dòng `observed in this run` là dữ liệu quan sát trong lần chạy này, không phải giá trị cố định để khẳng định cho mọi triển khai hoặc mọi cách thực thi. Map không có thứ tự duyệt bắt buộc theo bảng chữ cái; TreeMap trong mã chỉ sắp xếp đầu ra sau khi thu thập.

### Trình tự được chương trình kiểm soát

1. Luồng chính tạo iterator và nhận một entry đầu tiên.
2. Nó giữ lại khóa và giá trị entry đó trong bản ghi quan sát.
3. Luồng ghi cập nhật các giá trị cũ rồi thêm khóa D.
4. `join()` chờ luồng ghi hoàn tất.
5. Luồng chính dùng **iterator đã tạo từ trước** để duyệt tiếp.

Không dùng một khoảng ngủ để đoán rằng luồng ghi “chắc đã xong”. `join` xác định rõ thời điểm hoàn tất và tạo quan hệ nhìn thấy cần thiết giữa hai luồng trong ví dụ.

### Ý nghĩa của kết quả đã quan sát

Trong lần chạy này:

- Giá trị A được thu trước cập nhật, nên bản ghi quan sát giữ A bằng `10`.
- Các giá trị B và C quan sát sau đó là giá trị đã tăng.
- D mới thêm cũng xuất hiện.
- Map sau khi luồng ghi kết thúc có A bằng `110`.

Vì D chỉ được thêm sau khi đã tăng tất cả các giá trị cũ, không có thời điểm nào nguồn vừa có A bằng `10` vừa có D bằng `40`. Vậy tổ hợp đã quan sát không phải ảnh chụp toàn map tại một thời điểm.

Đây là lập luận về **kết quả thực tế của lần chạy đã trình bày**. Nếu một lần chạy khác không nhìn thấy D hoặc có quan sát khác được hợp đồng cho phép, không thể kết luận iterator bị lỗi chỉ vì khác ví dụ.

### Những gì không đổi trong ví dụ

Sau khi `writer.join()` hoàn tất và không còn bên ghi nào:

- Mọi giá trị A, B, C đã được tăng trong map sống.
- Khóa D đã được thêm.
- Việc in map sống bằng TreeMap lúc đó phản ánh trạng thái cuối đang ổn định.

Sự kiểm tra trạng thái cuối này khác với đòi hỏi iterator cũ phải nhìn thấy một trạng thái cuối hoàn chỉnh.

## 9. Ba loại iterator không phải ba mức “an toàn” tăng dần

Không có cách xếp chung rằng fail-fast kém, snapshot khá, nhất quán yếu tốt nhất.

### Khi chương trình chỉ có một bên sở hữu dữ liệu

Nếu một phương thức quản lý riêng danh sách của nó, một ArrayList thông thường và việc sửa đúng qua iterator có thể là đủ. Đổi sang cấu trúc đồng thời không tự làm thuật toán đơn giản hơn.

### Khi một lượt xử lý cần danh sách tại lúc bắt đầu

Một snapshot phù hợp nếu các cập nhật được chấp nhận cho lượt sau. Cần xét cả phần tử khả biến và chi phí giữ các phiên bản cấu trúc.

### Khi cần đọc trong lúc nhiều bên cập nhật

Duyệt nhất quán yếu có thể phù hợp cho những quan sát chấp nhận dữ liệu trong một khoảng thời gian, ví dụ hiển thị trạng thái hoạt động với yêu cầu được định nghĩa tương ứng.

Nếu muốn một báo cáo chính xác của toàn bộ dữ liệu tại cùng thời điểm, không nên tự coi lượt duyệt đó đáp ứng yêu cầu.

### Khi cần thay đổi nghiệp vụ gồm nhiều bước

Việc “tìm thấy một phần tử rồi cập nhật” hoặc “kiểm tra rồi thêm” cần chính sách phối hợp riêng.

Hợp đồng iterator trả lời cách duyệt khi nguồn thay đổi. Nó không tự biến một chuỗi thao tác của ứng dụng thành giao dịch nguyên tử.

## 10. Khóa cả lượt duyệt hoặc tạo bản sao được bảo vệ

Một lựa chọn khác là ngăn các cập nhật được phối hợp trong lúc duyệt hoặc trong lúc tạo bản sao.

Với danh sách bọc bằng `Collections.synchronizedList`, tài liệu yêu cầu người dùng đồng bộ thủ công trên chính danh sách bọc khi duyệt. Các lời gọi được đồng bộ riêng lẻ không mặc định giữ khóa suốt cả vòng lặp. Xem [Collections.synchronizedList Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedList(java.util.List)).

Có hai chiến lược:

| Chiến lược | Đánh đổi |
| --- | --- |
| Giữ khóa trong suốt lượt duyệt | Nguồn được giữ ổn định theo giao thức khóa; bên ghi có thể phải chờ |
| Giữ khóa để sao chép, rồi xử lý bản sao ngoài khóa | Thêm chi phí sao chép; giảm thời gian khóa cho phần xử lý dài |

Điều kiện là các bên ghi phải tuân theo cùng giao thức. Một khóa mà bên ghi không sử dụng không tự chặn chúng.

### Chương trình hoàn chỉnh

Tạo tệp **`LockedSnapshotDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LockedSnapshotDemo {
    public static void main(String[] args) {
        List<String> shared = Collections.synchronizedList(
                new ArrayList<>(List.of("A", "B", "C")));

        List<String> snapshot;
        synchronized (shared) {
            snapshot = new ArrayList<>(shared);
        }

        shared.add("D");

        System.out.println("snapshot = " + snapshot);
        System.out.println("live list = " + shared);

        synchronized (shared) {
            for (String value : shared) {
                System.out.println("locked visit = " + value);
            }
        }
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 LockedSnapshotDemo.java
java -Dfile.encoding=UTF-8 LockedSnapshotDemo
```

Stdout đã kiểm chứng:

```text
snapshot = [A, B, C]
live list = [A, B, C, D]
locked visit = A
locked visit = B
locked visit = C
locked visit = D
```

### Đọc hai cách sử dụng

- Bản sao được tạo trong khối khóa.
- Sau khi thêm D vào nguồn, bản sao vẫn chỉ có A, B, C.
- Vòng lặp cuối giữ khóa trên danh sách bọc để minh họa vùng đồng bộ bao trùm cả lượt duyệt.

Ví dụ dùng một luồng để trình bày rõ ranh giới thao tác và đầu ra. Khi áp dụng nhiều luồng, các bên phải dùng danh sách bọc hoặc cùng cơ chế bảo vệ; không giữ một tham chiếu khác tới mảng/danh sách nền rồi sửa qua đường bỏ qua khóa.

Đây vẫn là sao chép nông của phần tử. Nếu phần tử có dữ liệu khả biến cần ảnh chụp sâu, ứng dụng phải thiết kế cách chụp trạng thái phần tử phù hợp.

### Không mặc định new ArrayList(concurrentValues) là snapshot nguyên tử

Một bản sao được tạo bằng cách thu thập từ nguồn nhất quán yếu có thể giữ cố định **kết quả thu thập**, nhưng kết quả đó vẫn có thể được lấy từ nhiều thời điểm.

Tương tự, chỉ viết `synchronized(map)` quanh bên đọc không làm những lời gọi cập nhật ConcurrentHashMap ở nơi khác tự sử dụng khóa đó. Muốn một ảnh chụp phối hợp, cần giao thức mà tất cả các bên liên quan cùng tuân thủ, hoặc một thiết kế công bố trạng thái bất biến theo phiên bản.

## 11. Các ngộ nhận cần loại bỏ

### “Fail-safe” là một hợp đồng đầy đủ

Đôi khi tên này được dùng để gộp những iterator không ném CME. Nó không trả lời được iterator dùng snapshot hay nhất quán yếu, có hỗ trợ xóa không, hoặc bảo đảm dữ liệu gì.

Khi phân tích, hãy nói đúng hợp đồng cụ thể và tên lớp thay vì dừng ở một nhãn chung.

### Không có CME nghĩa là thread-safe

Không có ngoại lệ không chứng minh truy cập đang hợp lệ. Có thể bạn đang dùng snapshot, một cấu trúc đồng thời, hoặc đơn giản chưa gặp đường phát hiện lỗi.

Ngoài ra, collection an toàn luồng không có nghĩa một đối tượng iterator nên được nhiều luồng dùng chung tùy ý.

### Snapshot nghĩa là mọi dữ liệu bên trong bị đóng băng

Ví dụ Score cho thấy tham chiếu cũ vẫn dẫn tới một đối tượng khả biến. Bất biến cấu trúc và bất biến phần tử cần được đánh giá riêng.

### Nhất quán yếu luôn nhìn thấy bản mới nhất

Một lượt duyệt không mặc định là một thao tác đọc nguyên tử của toàn nguồn. Nếu yêu cầu nghiệp vụ dùng chữ “mới nhất”, cần định nghĩa mới nhất ở thời điểm nào và mức phối hợp cần thiết.

### Chuyển vòng for-each thành vòng chỉ số là hết lỗi

Thay cú pháp không tự tạo chính sách đồng bộ. Thêm/xóa còn làm thay đổi chỉ số và có thể gây bỏ sót hoặc truy cập sai.

### Bắt CME rồi bỏ qua là đã xử lý

Bỏ qua lỗi có thể để một phần dữ liệu chưa được xử lý hoặc làm mất khả năng nhận biết tác dụng phụ đã xảy ra. Cần sửa cách sở hữu, phối hợp hoặc duyệt dữ liệu theo hợp đồng.

### Chỉ cần kiểm tra size trước và sau

Hai thời điểm có cùng kích thước vẫn có thể chứa các phần tử hoặc giá trị khác nhau. Một số thay đổi còn có thể thêm rồi xóa, khiến số lượng trở lại như cũ.

Size không phải số phiên bản nghiệp vụ hay bằng chứng toàn bộ lượt duyệt đã nhất quán.

## 12. Cách kiểm chứng mà không phụ thuộc vào may rủi lịch chạy

Khi viết thử nghiệm:

1. Xác định rõ thời điểm tạo iterator.
2. Ghi lại thay đổi nào được làm trước khi lấy phần tử tiếp theo.
3. Dùng `join`, latch hoặc cơ chế phối hợp phù hợp nếu cần một thứ tự sự kiện.
4. Phân biệt giá trị được hợp đồng bảo đảm với một kết quả quan sát.
5. Không dùng ngủ một khoảng thời gian làm bằng chứng đồng bộ.
6. Không khẳng định hợp đồng tổng quát từ một lần không có ngoại lệ.

Ví dụ trong bài cố định trình tự sự kiện để làm rõ **vòng đời iterator**, nhưng vẫn không ép iterator nhất quán yếu thành snapshot.

Một số tiêu chí kiểm tra:

| Loại thử nghiệm | Điều nên xác minh |
| --- | --- |
| Fail-fast cụ thể | Tái hiện vi phạm ở nguồn và chuỗi thao tác đã xác định |
| Snapshot | Iterator cũ giữ tập tham chiếu đã chụp sau các cập nhật nguồn |
| Phần tử khả biến | Phân biệt thay phần tử với sửa đối tượng chung |
| Nhất quán yếu | Chỉ đòi các bảo đảm của lớp; không ép một tập kết quả tùy ý thành cố định |
| Khóa hoặc bản sao | Kiểm tra mọi bên ghi đi qua cùng giao thức bảo vệ |

Với hệ thống thực, cần thêm kiểm tra rằng các tác vụ không làm lặp hoặc bỏ công việc ngoài chính sách, và rằng dữ liệu phần tử cũng được công bố, đọc, sửa một cách hợp lệ.

## 13. Thực hành

### Bài 1: phân loại yêu cầu

Với mỗi tình huống, giải thích hợp đồng phù hợp:

- Một phương thức lọc danh sách do chính nó sở hữu.
- Một lượt gửi sự kiện dùng danh sách bộ lắng nghe tại lúc bắt đầu.
- Một màn hình trạng thái chấp nhận quan sát các cập nhật trong thời gian duyệt.
- Một báo cáo cần trạng thái toàn bộ dữ liệu tại một thời điểm được xác định.

Nêu lý do chọn, không chỉ tên collection.

### Bài 2: thử thay cấu trúc và thay phần tử

Từ `FailFastIteratorDemo`, tạo các tình huống tách biệt:

1. Thêm phần tử sau khi tạo iterator.
2. Thay một phần tử bằng set.
3. Xóa qua chính iterator khi được hỗ trợ.

Giải thích các kết quả theo hợp đồng ArrayList và không suy rộng thời điểm ném lỗi sang mọi lớp.

### Bài 3: phân biệt hai lớp snapshot

Từ `SnapshotIteratorDemo`:

- Thay đối tượng ở một vị trí sau khi tạo iterator.
- Trong một thử nghiệm riêng, chỉ sửa thuộc tính của đối tượng mà iterator đang giữ.
- Vẽ các tham chiếu cũ và mới.
- Giải thích trường hợp nào đổi cấu trúc quan sát và trường hợp nào đổi trạng thái đối tượng chung.

### Bài 4: kiểm tra phát biểu về nhất quán yếu

Đánh giá các phát biểu:

1. “Không có CME nên dữ liệu là snapshot.”
2. “Dùng TreeMap để in kết quả nên lượt đọc đã nguyên tử.”
3. “Sau join, map sống đã hoàn tất cập nhật nên iterator cũ bắt buộc thấy toàn bộ trạng thái đó.”
4. “Một kết quả quan sát không trùng trạng thái cuối chưa đủ để kết luận ConcurrentHashMap bị lỗi.”
5. “Collection đồng thời thì các bước kiểm tra rồi cập nhật của ứng dụng tự trở thành một thao tác nguyên tử.”

Hãy liên hệ từng phát biểu với trình tự trong `WeakIteratorDemo`.

### Bài 5: thiết kế một bản sao có ý nghĩa

Giả sử có một danh sách dùng chung và mỗi phần tử là đối tượng khả biến.

Hãy mô tả:

- Thời điểm muốn lấy dữ liệu.
- Bên nào phải bị chặn hoặc phối hợp trong lúc chụp.
- Chỉ cần sao chép tham chiếu hay phải chụp cả thuộc tính phần tử.
- Bản sao sẽ được xử lý ngoài khóa như thế nào.
- Giới hạn nào cần ghi rõ cho bên sử dụng.

Tiêu chí hoàn thành là phân biệt được hợp đồng iterator, tính nguyên tử của lượt đọc và tính an toàn của đối tượng phần tử, rồi chọn cơ chế đáp ứng đúng mức nhất quán cần có.


</details>

- [Quay lại đầu trang](#back-to-top)
