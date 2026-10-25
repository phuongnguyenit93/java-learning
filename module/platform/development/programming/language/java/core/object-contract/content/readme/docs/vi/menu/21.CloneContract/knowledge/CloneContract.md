<a id="back-to-top"></a>

# Hợp đồng clone

## Menu
- [clone() tạo bản sao và ý nghĩa sao chép phụ thuộc lớp](#clone-contract-001)
- [Object.clone() có quyền truy cập protected](#clone-contract-002)
- [Cloneable là marker interface và không tự khai báo phương thức clone()](#clone-contract-003)
- [CloneNotSupportedException khi Object.clone() không được phép](#clone-contract-004)
- [super.clone() sao chép nông các trường](#clone-contract-005)
- [Hàm khởi tạo thông thường không được chạy bởi Object.clone()](#clone-contract-006)
- [Quy ước bản sao khác tham chiếu, cùng lớp và thường cùng giá trị](#clone-contract-007)
- [Phân biệt quy ước sao chép với các bảo đảm tuyệt đối của API](#clone-contract-008)
- [Ghi đè clone() public và dùng kiểu trả về cụ thể](#clone-contract-009)
- [Mảng hỗ trợ clone() và mảng nhiều chiều vẫn chia sẻ mảng con](#clone-contract-010)

## <a id="clone-contract-001">clone() tạo bản sao và ý nghĩa sao chép phụ thuộc lớp</a>

<details>
<summary>Xem chi tiết</summary>

# clone() tạo bản sao và ý nghĩa sao chép phụ thuộc lớp

## 1. Clone là gì và cần hiểu đúng điều gì?

Trong Java, **clone()** thường được dùng để tạo một đối tượng mới từ trạng thái của đối tượng đang có. Nhưng tên phương thức **không bảo đảm đây là deep copy**: đối tượng mới có thể chia sẻ mảng, danh sách hoặc các object con với đối tượng gốc. Độ sâu sao chép, quy tắc giữ định danh nghiệp vụ, vòng đời tài nguyên và khả năng thay đổi của hai bản thể **phụ thuộc vào hợp đồng của lớp cụ thể**.

Ví dụ một đối tượng có **String name** và **int[] scores**. Name là String bất biến nên có thể chia sẻ tham chiếu an toàn. Scores là mảng mutable: nếu bản sao cùng trỏ đến mảng, sửa phần tử qua bản sao sẽ làm bản gốc quan sát thấy thay đổi. Nếu yêu cầu hai đối tượng độc lập về scores, cần sao chép riêng mảng.

Phân biệt ba khái niệm:

- **Shallow copy:** object ngoài là bản thể mới, nhưng các field tham chiếu thường tiếp tục trỏ tới object con cũ.
- **Deep copy:** các thành phần mutable có yêu cầu độc lập cũng được tạo bản sao. Độ sâu cần thiết phụ thuộc vào cấu trúc dữ liệu và chính sách chia sẻ của miền nghiệp vụ.
- **Immutable snapshot:** tạo một giá trị không đổi để lưu trạng thái ở một thời điểm, thường bằng copy constructor hoặc factory với dữ liệu đã sao chép/phong tỏa phù hợp.

Clone không nhất thiết đồng nghĩa với immutable, cũng không đồng nghĩa với một bản sao có cùng ID nghiệp vụ. Hãy xác định trước **những thứ được giữ**, **những thứ cần tách**, và **những thứ không được sao chép**.

## 2. Object.clone() và Cloneable thực sự hoạt động thế nào?

Trong **java.lang.Object** có phương thức:

~~~java
protected native Object clone() throws CloneNotSupportedException;
~~~

Đây là **chữ ký API**, không phải một chương trình Java độc lập. Object.clone được thiết kế để tạo một object mới và sao chép các giá trị field instance theo cách **nông**. Primitive được sao chép giá trị; field tham chiếu nhận lại cùng giá trị tham chiếu. Nó không gọi lần lượt clone của các object con.

**java.lang.Cloneable** là **marker interface**, không khai báo phương thức nào. Khi object không implements Cloneable, gọi Object.clone trên nó sẽ ném **CloneNotSupportedException**. Một lớp implements Cloneable vẫn **không tự xuất hiện phương thức clone() public** cho mã ngoài gọi, vì Object.clone vốn có mức truy cập protected. Muốn cung cấp API công khai, lớp thường override **public clone()**, có thể trả về chính kiểu lớp nhờ covariant return type.

Một mẫu thường gặp:

~~~java
@Override
public Widget clone() {
    try {
        return (Widget) super.clone();
    } catch (CloneNotSupportedException ex) {
        throw new AssertionError(ex);
    }
}
~~~

Đây là **đoạn trích trong lớp Widget implements Cloneable**, không phải file chạy độc lập. Mẫu này phù hợp khi phương thức gọi Object.clone với marker đã được bảo đảm. Một API khác có thể khai báo **throws CloneNotSupportedException** thay vì bắt.

### Các quy ước, không phải bảo đảm tuyệt đối

Tài liệu Object.clone mô tả những đặc tính **thường được kỳ vọng**:

~~~text
x.clone() != x
x.clone().getClass() == x.getClass()
x.clone().equals(x)
~~~

Những dòng trên **không phải ba yêu cầu bắt buộc cho mọi override clone()**. Chẳng hạn, lớp chưa override equals thì hai bản thể khác tham chiếu vẫn có thể trả false theo Object.equals. Một lớp copy tài nguyên có thể cố ý đặt ID mới, khiến equals theo ID trả false. Hợp đồng thực tế phải được tài liệu hóa ở lớp ấy.

## 3. Ví dụ Java 17: bản sao nông làm chia sẻ mảng

Tạo file **ShallowCloneDemo.java**:

~~~java
import java.util.Arrays;

public class ShallowCloneDemo {
    static final class Report implements Cloneable {
        private final String title;
        private int[] scores;

        Report(String title, int[] scores) {
            this.title = title;
            this.scores = scores;
        }

        @Override
        public Report clone() {
            try {
                return (Report) super.clone();
            } catch (CloneNotSupportedException ex) {
                throw new AssertionError(ex);
            }
        }
    }

    public static void main(String[] args) {
        Report original = new Report("Daily", new int[]{10, 20});
        Report copy = original.clone();

        System.out.println("different object: " + (original != copy));
        System.out.println("same array: " +
            (original.scores == copy.scores));

        copy.scores[0] = 99;

        System.out.println("original: " +
            Arrays.toString(original.scores));
        System.out.println("copy: " +
            Arrays.toString(copy.scores));
        System.out.println("title: " + copy.title);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ShallowCloneDemo.java
java ShallowCloneDemo
~~~

Kết quả:

~~~text
different object: true
same array: true
original: [99, 20]
copy: [99, 20]
title: Daily
~~~

Vì **super.clone()** chỉ copy tham chiếu **scores**, original và copy giữ cùng mảng. Việc cùng trỏ tới String title không thành vấn đề vì String bất biến. Nhưng với mảng scores, một thay đổi đã ảnh hưởng cả hai.

Đây là ví dụ **shallow copy**, không phải lỗi biên dịch. Nó chỉ không phù hợp nếu API hứa hẹn dữ liệu được tách độc lập. Ngoài ra, constructor ở ví dụ đang giữ nguyên tham chiếu mảng đầu vào; caller có thể sửa mảng gốc bên ngoài. Nếu muốn cách ly hoàn toàn từ lúc tạo, cần defensive copy cả trong constructor.

## 4. Tách field mutable sau super.clone()

Khi một lớp có mảng primitive một chiều, có thể gọi super.clone để tạo object rồi thay tham chiếu mảng của bản sao bằng một mảng mới. Tạo file **IndependentCloneDemo.java**:

~~~java
import java.util.Arrays;
import java.util.Objects;

public class IndependentCloneDemo {
    static final class Batch implements Cloneable {
        private final String id;
        private int[] values;

        Batch(String id, int[] values) {
            this.id = Objects.requireNonNull(id);
            this.values = Objects.requireNonNull(values).clone();
        }

        public int[] values() {
            return values.clone();
        }

        @Override
        public Batch clone() {
            try {
                Batch copy = (Batch) super.clone();
                copy.values = values.clone();
                return copy;
            } catch (CloneNotSupportedException ex) {
                throw new AssertionError(ex);
            }
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Batch other
                && id.equals(other.id)
                && Arrays.equals(values, other.values);
        }

        @Override
        public int hashCode() {
            return 31 * id.hashCode() + Arrays.hashCode(values);
        }
    }

    public static void main(String[] args) {
        Batch original = new Batch("B-1", new int[]{3, 5});
        Batch copy = original.clone();

        System.out.println("different object: " + (original != copy));
        System.out.println("same array: " + (original.values == copy.values));
        System.out.println("equal before: " + original.equals(copy));
        System.out.println("same hash before: " +
            (original.hashCode() == copy.hashCode()));

        copy.values[0] = 8;

        System.out.println("original: " + Arrays.toString(original.values));
        System.out.println("copy: " + Arrays.toString(copy.values));
        System.out.println("equal after: " + original.equals(copy));
    }
}
~~~

Chạy:

~~~bash
javac IndependentCloneDemo.java
java IndependentCloneDemo
~~~

Kết quả:

~~~text
different object: true
same array: false
equal before: true
same hash before: true
original: [3, 5]
copy: [8, 5]
equal after: false
~~~

Giải thích: **clone()** tạo object Batch mới, sau đó clone mảng values để tách trạng thái có thể thay đổi. **id** là String bất biến nên chia sẻ an toàn. Vì equals của Batch dựa trên **id và nội dung mảng**, trước khi sửa bản sao thì equals true và hashCode bằng nhau; sau khi bản sao bị sửa, hai giá trị khác nhau.

Field **values** không khai báo final vì phép gán **copy.values = ...** sau **super.clone()** phải hợp lệ. Nếu muốn field mảng là final và dữ liệu giá trị bất biến, **copy constructor** thường thuận tiện hơn. Đừng kết luận một lần clone mảng là “deep copy vĩnh viễn”: khi cấu trúc có thêm object mutable lồng bên trong, cách này có thể không còn đủ.

## 5. Không implements Cloneable thì chuyện gì xảy ra?

Tạo file **CloneMarkerDemo.java**:

~~~java
public class CloneMarkerDemo {
    static final class NoMarker {
        Object attemptClone() throws CloneNotSupportedException {
            return super.clone();
        }
    }

    static final class WithMarker implements Cloneable {
        private final int code;

        WithMarker(int code) {
            this.code = code;
        }

        @Override
        public WithMarker clone() {
            try {
                return (WithMarker) super.clone();
            } catch (CloneNotSupportedException ex) {
                throw new AssertionError(ex);
            }
        }
    }

    public static void main(String[] args) {
        try {
            new NoMarker().attemptClone();
        } catch (CloneNotSupportedException ex) {
            System.out.println("without marker: " +
                ex.getClass().getSimpleName());
        }

        WithMarker first = new WithMarker(42);
        WithMarker copy = first.clone();

        System.out.println("new object: " + (first != copy));
        System.out.println("copied code: " + copy.code);
    }
}
~~~

Chạy:

~~~bash
javac CloneMarkerDemo.java
java CloneMarkerDemo
~~~

Kết quả:

~~~text
without marker: CloneNotSupportedException
new object: true
copied code: 42
~~~

**NoMarker** có thể gọi protected Object.clone từ một phương thức của lớp, nhưng Object.clone từ chối vì object không implements Cloneable. **WithMarker** vừa có marker vừa public override, nên caller gọi được và nhận bản sao.

Chú ý tính **checked** của CloneNotSupportedException: lời gọi có chữ ký throws phải được bắt hoặc khai báo. Khi bản thân lớp kiểm soát Cloneable, mẫu bắt exception rồi ném AssertionError có thể dùng để bộc lộ tình huống không đúng như bất biến thiết kế.

## 6. Object.clone không gọi constructor cho bản sao

Đối với cơ chế Object.clone, object mới nhận trạng thái được sao chép mà không chạy constructor của class một lần nữa. Đây là khác biệt thiết yếu với **new** hoặc copy constructor.

Tạo file **ConstructorCloneDemo.java**:

~~~java
public class ConstructorCloneDemo {
    private static int constructorCalls;

    static final class Ticket implements Cloneable {
        private final int number;

        Ticket(int number) {
            constructorCalls++;
            this.number = number;
        }

        @Override
        public Ticket clone() {
            try {
                return (Ticket) super.clone();
            } catch (CloneNotSupportedException ex) {
                throw new AssertionError(ex);
            }
        }
    }

    public static void main(String[] args) {
        Ticket first = new Ticket(7);
        Ticket copy = first.clone();

        System.out.println("constructor calls: " + constructorCalls);
        System.out.println("different object: " + (first != copy));
        System.out.println("number: " + copy.number);
    }
}
~~~

Chạy:

~~~bash
javac ConstructorCloneDemo.java
java ConstructorCloneDemo
~~~

Kết quả:

~~~text
constructor calls: 1
different object: true
number: 7
~~~

Điều này có thể bỏ qua các bước xác thực, đăng ký listener, tạo lock/tài nguyên, sinh ID hay tính trường phụ trong constructor. Khi lớp có các **invariant** phụ thuộc constructor, phải thiết kế clone để kết quả luôn hợp lệ. Không thể mặc định clone một socket, file handle, stream, thread hay database connection theo nghĩa tạo thêm một tài nguyên độc lập.

## 7. Clone mảng một chiều và mảng lồng nhau

Mảng Java hỗ trợ **clone()**. Với **int[]**, mảng mới chứa các giá trị int được sao chép. Với **StringBuilder[]**, mảng mới chứa lại các tham chiếu tới cùng StringBuilder. Mảng ngoài được tạo mới, nhưng element object thường chưa được clone.

Với **int[][]**, mảng ngoài chứa các tham chiếu **int[]**. Gọi clone trên mảng ngoài chưa copy từng hàng.

Tạo file **MatrixCloneDemo.java**:

~~~java
import java.util.Arrays;

public class MatrixCloneDemo {
    public static void main(String[] args) {
        int[][] original = {{1, 2}, {3, 4}};
        int[][] shallow = original.clone();

        int[][] independent = new int[original.length][];
        for (int i = 0; i < original.length; i++) {
            independent[i] = original[i].clone();
        }

        System.out.println("same outer: " + (original == shallow));
        System.out.println("shared row: " + (original[0] == shallow[0]));

        shallow[0][0] = 9;
        System.out.println("original after shallow: " +
            Arrays.deepToString(original));

        independent[1][0] = 7;
        System.out.println("original after independent: " +
            Arrays.deepToString(original));
        System.out.println("independent snapshot: " +
            Arrays.deepToString(independent));
    }
}
~~~

Chạy:

~~~bash
javac MatrixCloneDemo.java
java MatrixCloneDemo
~~~

Kết quả:

~~~text
same outer: false
shared row: true
original after shallow: [[9, 2], [3, 4]]
original after independent: [[9, 2], [3, 4]]
independent snapshot: [[1, 2], [7, 4]]
~~~

Independent sao chép từng hàng **trước khi** shallow sửa dữ liệu ban đầu, nên nó giữ snapshot giá trị ban đầu. Nếu một hàng có thể null, phải xử lý trước khi gọi **original[i].clone()**. Nếu phần tử bên trong là object mutable chứ không phải primitive, clone từng tầng mảng vẫn giữ object lá được chia sẻ.

**Arrays.copyOf** và **System.arraycopy** cũng sao chép phần tử mà không tự động clone mọi object phần tử. Chọn cách copy theo ngữ nghĩa cần đạt, không dựa vào tên phương thức.

## 8. Clone collection cũng cần xem tài liệu lớp cụ thể

Interface **List**, **Set**, **Map** không bắt buộc cung cấp clone. Một số lớp cụ thể, ví dụ **ArrayList**, có phương thức clone tạo **bản sao nông** của cấu trúc danh sách. Các phần tử vẫn là tham chiếu tới object cũ.

Tạo file **ListCloneDemo.java**:

~~~java
import java.util.ArrayList;

public class ListCloneDemo {
    public static void main(String[] args) {
        ArrayList<StringBuilder> first = new ArrayList<>();
        first.add(new StringBuilder("A"));

        @SuppressWarnings("unchecked")
        ArrayList<StringBuilder> second =
            (ArrayList<StringBuilder>) first.clone();

        System.out.println("same list: " + (first == second));
        System.out.println("same element: " +
            (first.get(0) == second.get(0)));

        second.get(0).append("!");
        System.out.println("first value: " + first.get(0));
        System.out.println("second value: " + second.get(0));
    }
}
~~~

Chạy:

~~~bash
javac ListCloneDemo.java
java ListCloneDemo
~~~

Kết quả:

~~~text
same list: false
same element: true
first value: A!
second value: A!
~~~

**new ArrayList<>(first)** cũng sao chép cấu trúc nhưng không clone từng phần tử. **List.copyOf(first)** tạo danh sách không thể sửa cấu trúc, song object phần tử mutable vẫn được chia sẻ. “Collection không sửa được” khác với “đồ thị object hoàn toàn bất biến”.

## 9. Kế thừa làm hợp đồng clone phức tạp hơn

Một lớp cha có thể override clone và đảm bảo copy đúng các trường của chính nó. Nhưng khi lớp con bổ sung một mảng hoặc List mutable, bản sao do clone kế thừa tạo ra có thể vẫn đúng **kiểu runtime** nhưng chia sẻ field mới với bản gốc.

Nếu lớp con override clone:

- Nó phải tôn trọng cơ chế sao chép đã được lớp cha thiết kế.
- Nó cần xử lý những field mutable mới nếu contract yêu cầu độc lập.
- Nếu clone gọi **super.clone()**, cần phân biệt lớp cha gọi Object.clone và lớp cha đã tự thực hiện thêm bước clone field của mình.
- Các **final field** trỏ tới object mutable khiến phương án “super.clone rồi gán lại field” khó áp dụng; một constructor/factory tạo mới có thể là thiết kế phù hợp hơn.
- Với cây kế thừa mở, các subclass tương lai có thể làm hợp đồng sao chép trước đó không còn đúng.

Do đó việc override **clone()** nên gắn với **tài liệu rõ ràng về độ sâu, kiểu runtime, chia sẻ tài nguyên, và trách nhiệm của subclass**. Nếu không kiểm soát được cây kế thừa, một API copy riêng hoặc lớp final bất biến thường dễ xác minh hơn.

## 10. Copy constructor và kiểu bất biến là lựa chọn khác

Với code mới, **copy constructor** hoặc **static copy factory** có thể diễn tả chính xác các thành phần phải sao chép, đồng thời cho phép thiết lập field final và chạy validation thông thường.

Tạo file **CopyConstructorDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CopyConstructorDemo {
    static final class Snapshot {
        private final String id;
        private final List<String> tags;

        Snapshot(String id, List<String> tags) {
            this.id = Objects.requireNonNull(id);
            this.tags = List.copyOf(Objects.requireNonNull(tags));
        }

        Snapshot(Snapshot source) {
            this(source.id, source.tags);
        }

        List<String> tags() {
            return tags;
        }
    }

    public static void main(String[] args) {
        List<String> input = new ArrayList<>(List.of("READY"));
        Snapshot original = new Snapshot("S-1", input);
        Snapshot copy = new Snapshot(original);

        input.add("DONE");

        System.out.println("different object: " + (original != copy));
        System.out.println("original: " + original.tags());
        System.out.println("copy: " + copy.tags());

        try {
            copy.tags().add("LATER");
        } catch (UnsupportedOperationException ex) {
            System.out.println("copy list cannot be modified");
        }
    }
}
~~~

Chạy:

~~~bash
javac CopyConstructorDemo.java
java CopyConstructorDemo
~~~

Kết quả:

~~~text
different object: true
original: [READY]
copy: [READY]
copy list cannot be modified
~~~

Ở đây **List.copyOf** tạo biểu diễn không thể thay đổi cấu trúc và các phần tử String là bất biến. Với **List<MutableItem>**, một List không cho phép add/remove **vẫn có thể quan sát thay đổi trong MutableItem**. Muốn bất biến sâu phải thiết kế riêng cho kiểu phần tử.

**Record** cũng thường không cần Cloneable. Với record chứa các giá trị immutable như int, String hoặc Instant, có thể dựng giá trị mới bằng canonical constructor:

~~~java
record Point(int x, int y) {}

Point p = new Point(1, 2);
Point q = new Point(p.x(), p.y());
~~~

Đây là đoạn minh họa, không phải file chương trình độc lập. Hai Point có giá trị equals nhau; với record có mảng hay object mutable, constructor/accessor/equals/hashCode phải được thiết kế đồng bộ để giữ hợp đồng giá trị khi sao chép.

## 11. Quan hệ giữa clone, equals và hashCode

**clone()** xử lý cách tạo object; **equals()** xử lý khi nào hai object tương đương; **hashCode()** phải nhất quán với equals. Không suy ra một hợp đồng từ hợp đồng còn lại.

Nếu lớp dùng **Object.equals** theo danh tính, một bản sao object mới có thể **không equals** object gốc. Nếu lớp có equals theo tất cả field giá trị và clone giữ nguyên các field ấy, clone thường equals bản gốc. Khi equals trả true, hashCode phải bằng nhau.

Đừng dùng hashCode như **ID duy nhất** của object hoặc của bản sao. HashCode là int và có thể va chạm. Nếu clone tạo ID mới theo nghiệp vụ, cả equals lẫn hashCode có thể thay đổi; việc đó phải nằm trong hợp đồng của class thay vì phát sinh ngầm.

Đối với object mutable được dùng làm khóa HashMap/HashSet, sửa field tham gia equals/hashCode sau khi đã chèn có thể khiến tra cứu sai. Có một bản clone độc lập không thay đổi quy tắc: **khóa đang nằm trong cấu trúc băm phải ổn định**.

## 12. Clone phức tạp của đồ thị object và tài nguyên

Một object có thể tham chiếu đến nhiều object con cùng dùng chung một node; đồ thị có thể có chu kỳ. Nếu yêu cầu deep copy đúng đồ thị:

1. Cần xác định node nào được tạo bản sao và node nào được chia sẻ.
2. Cần giữ mối quan hệ aliasing có chủ đích: hai field trỏ cùng node trong đồ thị gốc có phải cùng trỏ một node clone trong đồ thị mới không?
3. Với chu kỳ A → B → A, phải theo dõi các node đã sao chép để tránh đệ quy vô hạn.
4. Nên có ánh xạ object gốc → object clone trong quá trình duyệt đồ thị khi cần bảo toàn cấu trúc.
5. Phải có chính sách riêng cho resource có danh tính và vòng đời: stream, lock, socket, thread, transaction, native handle hoặc listener.

Không thể giải quyết các yêu cầu này chỉ bằng việc gọi **clone()** đệ quy tùy tiện. Thông thường cần một thuật toán copy theo mô hình dữ liệu hoặc một kiểu snapshot riêng.

## 13. Lỗi thường gặp

| Lỗi | Hậu quả | Cách xử lý |
| --- | --- | --- |
| Nghĩ **implements Cloneable** tự sinh public clone | Caller không có API clone công khai | Override public clone hoặc dùng API copy |
| Chỉ gọi **super.clone()** với field mutable | Hai bản thể chia sẻ object con | Sao chép thêm các field cần độc lập |
| Clone mảng ngoài của ma trận | Các hàng vẫn được chia sẻ | Sao chép từng tầng cần thiết |
| Clone ArrayList và nghĩ phần tử cũng được clone | Thay đổi object phần tử xuất hiện ở cả hai list | Copy phần tử theo đúng contract |
| Quên checked **CloneNotSupportedException** | Lỗi biên dịch hoặc API khó dùng | Bắt exception hoặc khai báo throws |
| Dựa vào constructor trong object clone | Invariant hoặc resource chưa được thiết lập lại | Kiểm tra invariant và cân nhắc copy constructor |
| Thêm field mutable ở subclass nhưng không sửa clone | Bản sao chia sẻ state mới | Thiết kế và kiểm tra từng tầng kế thừa |
| Dùng final với chiến lược gán lại field sau super.clone | Không thể gán lại final field theo cách thông thường | Constructor/factory tạo bản sao mới |
| Tuyên bố “deep immutable” sau **List.copyOf** | Object lá mutable vẫn có thể đổi | Copy/đóng băng object phần tử |
| Tin **clone.equals(original)** luôn bắt buộc | Test thất bại dù clone đúng theo contract lớp | Kiểm tra trước tiêu chí equals của lớp |

## 14. Checklist kiểm thử hợp đồng sao chép

Khi một lớp cho phép clone, nên có các phép kiểm thử sau:

1. **Danh tính:** clone có phải object mới theo cam kết? Nếu có, **original != copy**.
2. **Kiểu:** bản sao có đúng runtime class, nhất là với subclass?
3. **Trạng thái ban đầu:** dữ liệu có ý nghĩa được giữ, trừ những trường được tài liệu quy định đặt lại.
4. **Equals/hashCode:** nếu clone equals original thì hashCode bắt buộc giống nhau; không tự yêu cầu equals true với mọi loại clone.
5. **Sửa clone:** thay đổi từng field mutable của clone và quan sát original.
6. **Sửa original:** kiểm tra clone có bị ảnh hưởng ở những tham chiếu lẽ ra đã độc lập không.
7. **Sao chép sâu:** kiểm tra mảng lồng nhau, List object mutable, các node dùng chung, object null và dữ liệu rỗng.
8. **Constructor/invariant:** object clone còn hợp lệ không, có thiếu lock/cache/resource được tạo bởi constructor không?
9. **Kế thừa:** subclass thêm trường có thay đổi kết quả sao chép không?
10. **HashMap/HashSet:** không sửa các field tham gia equals/hashCode của khóa đã được lưu.
11. **Vòng đời tài nguyên:** không vô tình nhân đôi hoặc chia sẻ tài nguyên chỉ được phép sở hữu một lần.
12. **Độ sâu theo nghiệp vụ:** tài liệu và test phải cùng xác định rõ chỗ nào chia sẻ, chỗ nào độc lập.

## 15. Chọn giải pháp phù hợp

| Tình huống | Lựa chọn thường hợp lý |
| --- | --- |
| Tất cả state là primitive/immutable | Shallow copy có thể đủ; thậm chí có thể dùng chung giá trị immutable |
| Có một mảng primitive mutable | Object copy + clone mảng; bảo vệ constructor/getter khi cần |
| Mảng nhiều chiều | Copy từng mảng con cần thiết |
| Collection có các phần tử immutable | Copy collection hoặc cấu trúc không sửa đổi được |
| Collection có phần tử mutable | Copy collection và từng phần tử theo contract |
| Cây kế thừa mở, nhiều subclass | Xác lập cơ chế copy ở mọi tầng hoặc cân nhắc factory |
| Class có resource hoặc invariant constructor phức tạp | API copy riêng, copy constructor, hoặc không hỗ trợ copy |
| Kiểu giá trị record/final immutable | Constructor, factory, hoặc sử dụng trực tiếp giá trị |

**Ghi nhớ:** **Object.clone()** cho một bản sao **nông** khi class thực sự hỗ trợ Cloneable, còn ý nghĩa của một bản sao dùng được trong ứng dụng **do chính class định nghĩa**. Khi object chứa state mutable, kế thừa hoặc tài nguyên có vòng đời, hãy xác định độ sâu sao chép và kiểm thử khả năng độc lập. Nếu khó bảo đảm hợp đồng đó, copy constructor, factory hoặc mô hình immutable thường dễ hiểu và an toàn hơn.

Tham khảo: [Object.clone trong Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#clone()) và [Cloneable trong Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Cloneable.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="clone-contract-002">Object.clone() có quyền truy cập protected</a>

<details>
<summary>Xem chi tiết</summary>

# `Object.clone()` có quyền truy cập `protected` trong Java

`clone()` là phương thức do `java.lang.Object` khai báo, nhưng khác với `equals()`, `hashCode()` và `toString()`, **`Object.clone()` có mức truy cập `protected`**, không phải `public`. Điều này ảnh hưởng trực tiếp đến việc lớp nào được phép gọi phương thức, cách xây dựng API sao chép cho người dùng bên ngoài và lý do một đối tượng `implements Cloneable` vẫn chưa chắc gọi được `.clone()` từ mã phía khách hàng.

Bài học giải thích **hai điều kiện độc lập**: *quyền truy cập khi biên dịch* và *khả năng clone khi chạy*. Cũng cần hiểu `Object.clone()` tạo bản sao nông, các mảng có quy tắc đặc biệt, và trong nhiều trường hợp **copy constructor** hoặc **factory method** dễ bảo trì hơn.

## 1. Chữ ký và ý nghĩa của `Object.clone()`

Trong API của `Object`, chữ ký tương đương:

```java
protected native Object clone()
        throws CloneNotSupportedException;
```

`native` là chi tiết khai báo của triển khai JDK phổ biến; trọng tâm hợp đồng ở đây là `protected`, kiểu trả về `Object` và checked exception `CloneNotSupportedException`.

Theo hợp đồng của `Object.clone()`:

- Nếu lớp của đối tượng thực thi **không triển khai `java.lang.Cloneable`**, lời gọi mặc định ném `CloneNotSupportedException`.
- Nếu đối tượng hỗ trợ cách clone mặc định, phương thức tạo một đối tượng mới cùng lớp runtime và sao chép giá trị các trường của đối tượng gốc. Các trường tham chiếu **vẫn trỏ tới cùng đối tượng được tham chiếu**: đó là **shallow copy (sao chép nông)**.
- Việc tạo bản sao này **không gọi constructor của lớp được clone** theo cách khởi tạo một instance mới bằng `new`.
- `Object.clone()` trả về `Object`. Một lớp ghi đè có thể dùng **covariant return type** để trả về kiểu cụ thể hơn, chẳng hạn `AccountKey clone()`.

Một số kỳ vọng thường gặp là `x.clone() != x`, `x.clone().getClass() == x.getClass()` và `x.clone().equals(x)`; nhưng đó không phải các định luật áp dụng được cho **mọi** triển khai clone tùy chỉnh. Phần quan trọng là kiểu có thể cung cấp hợp đồng sao chép cụ thể, và người dùng cần biết nó sao chép những gì.

`Object.clone()` khác `Object.equals()` ở hai khía cạnh: quyền truy cập (`protected` thay vì `public`) và checked exception. Nó cũng khác một copy constructor, vốn có chữ ký và chính sách sao chép do chính lớp thiết kế.

## 2. `protected` cho phép gọi `clone()` ở đâu?

Trong Java, thành viên `protected` có thể được truy cập từ **các lớp trong cùng package với lớp khai báo**, và từ **lớp con ở package khác**, nhưng trường hợp lớp con ở package khác còn chịu điều kiện về ngữ cảnh truy cập/kiểu biểu thức nhận.

`Object` được khai báo trong `java.lang`. Mã ứng dụng thường không thuộc `java.lang`, vì thế không thể tùy ý gọi phương thức `Object.clone()` trên một biến bất kỳ:

```java
Object obj = new Object();
// obj.clone(); // LỖI BIÊN DỊCH: clone() has protected access in Object.
```

Đây là **đoạn minh họa không chạy độc lập**; dòng lỗi được đặt trong comment để phân biệt với code Java hợp lệ. Việc mọi lớp Java đều kế thừa `Object` **không có nghĩa bất kỳ đoạn code nào cũng được gọi `clone()` trên mọi đối tượng**.

Trong một lớp con của `Object`, lời gọi `super.clone()` nằm **bên trong thân phương thức của chính lớp con** có thể hợp lệ về quyền truy cập:

```java
class Draft implements Cloneable {
    public Draft copy() throws CloneNotSupportedException {
        return (Draft) super.clone();
    }
}
```

Đây là **đoạn khai báo lớp**, có thể biên dịch khi đặt trong file Java phù hợp. Dòng `super.clone()` được phép gọi vì phương thức đang được viết trong lớp con; dấu `implements Cloneable` giúp việc clone mặc định có thể thành công lúc chạy.

### 2.1. Ghi đè thành `public` nếu muốn công khai thao tác clone

Ví dụ:

```java
final class Document implements Cloneable {
    @Override
    public Document clone() {
        try {
            return (Document) super.clone();
        } catch (CloneNotSupportedException ex) {
            throw new AssertionError(ex);
        }
    }
}
```

Đây là **mảnh khai báo**, chưa phải chương trình có `main`. `Document.clone()` được công bố `public`, nên mã khác có thể gọi phương thức trên biểu thức có kiểu tĩnh `Document`. Lớp được đánh dấu `Cloneable`, vì vậy với cấu trúc không thay đổi, việc `super.clone()` ném `CloneNotSupportedException` là tình huống bất thường và được chuyển thành `AssertionError`.

**Lưu ý quan trọng:** kiểm tra quyền truy cập ở thời điểm biên dịch dựa trên **kiểu tĩnh của biểu thức gọi**. Nếu viết:

```java
Document doc = new Document();
Document copy = doc.clone();  // Hợp lệ: Document.clone() public.

Object obj = doc;
// obj.clone();               // Không hợp lệ: Object.clone() protected.
Document copy2 = ((Document) obj).clone(); // Hợp lệ sau cast.
```

Cả ba dòng trên là **đoạn minh họa**; muốn chạy độc lập cần bao chúng trong `main` cùng khai báo `Document`. Dù `obj` trỏ tới `Document`, biến có kiểu tĩnh `Object` không làm cho `Object.clone()` thành công khai.

**Không thể giảm quyền khi override:** nếu một lớp cha đã công bố `public clone()`, lớp con ghi đè nó phải tiếp tục công bố `public`. Ngược lại, Java cho phép override phương thức `protected` thành `public`, như ví dụ trên.

### 2.2. Hai package và quy tắc truy cập dễ nhầm

Giả sử lớp `LibraryItem` nằm trong package `library`:

```java
// Tệp library/LibraryItem.java
package library;

public class LibraryItem implements Cloneable {
    @Override
    protected LibraryItem clone()
            throws CloneNotSupportedException {
        return (LibraryItem) super.clone();
    }
}
```

Còn mã gọi nằm trong package `app`:

```java
// Tệp app/UseItem.java
package app;
import library.LibraryItem;

public class UseItem {
    public static void main(String[] args) throws Exception {
        LibraryItem item = new LibraryItem();
        // item.clone(); // LỖI: protected, khác package.
    }
}
```

Đây là **hai file minh họa theo đúng đường dẫn**, trong đó lệnh gây lỗi được comment. Từ `app`, dù `LibraryItem` triển khai `Cloneable`, lời gọi `item.clone()` vẫn không hợp lệ do `clone()` chỉ `protected`.

Một lớp con của `LibraryItem` ở package `app` có thể gọi `super.clone()` **trong chính phương thức của lớp con**. Tuy nhiên, không nên suy rộng quyền truy cập đó thành việc gọi `clone()` tùy ý trên một biến `LibraryItem` khác ở cùng package `app`. Với protected member có khai báo gốc ở package khác, Java giới hạn cách truy cập qua biểu thức nhận, ngoài việc lớp gọi phải là subclass phù hợp.

Với public API cần sao chép, giải pháp thông dụng là **công bố một phương thức `public` rõ ràng** trên kiểu hoặc dùng copy constructor/factory thay vì buộc mã khách hàng dựa vào quy tắc protected.

## 3. `Cloneable` không khai báo phương thức `clone()`

`java.lang.Cloneable` là **marker interface**: nó không có phương thức `clone()` để gọi. Nó thông báo cho `Object.clone()` rằng đối tượng cho phép thao tác clone mặc định.

Phân biệt:

| Câu hỏi | Cơ chế quyết định |
| --- | --- |
| Có gọi được `x.clone()` từ vị trí mã nguồn này không? | **Quy tắc quyền truy cập của Java**, kiểu tĩnh của `x`, phương thức đã được override công khai chưa |
| `super.clone()` có ném `CloneNotSupportedException` không? | Đối tượng runtime có triển khai `Cloneable` (hoặc lớp có phương thức clone tùy chỉnh với hợp đồng khác) |
| Giá trị được sao chép thế nào? | Hợp đồng `Object.clone()` là **shallow copy**; lớp ghi đè có thể chủ động sao chép thêm |
| Bản sao trả về có kiểu khai báo gì? | Kiểu trả về của phương thức clone có thể là `Object` hoặc kiểu cụ thể hơn qua covariant return |

Chỉ viết:

```java
class MarkerOnly implements Cloneable {
    private int value;
}
```

không tạo ra một **public `clone()`**. Mã khác package không thể viết `new MarkerOnly().clone()` chỉ nhờ `implements Cloneable`. Interface `Cloneable` không có phương thức nào tên `clone()` để bảo đảm giao diện gọi công khai.

Ngược lại, một lớp có thể tự viết `public T copy()` sử dụng constructor để tạo bản sao mà **không** triển khai `Cloneable`. Vì vậy cần tránh đánh đồng “có API sao chép” và “cho phép `Object.clone()`”.

## 4. Chương trình Java 17+ phân biệt quyền truy cập và khả năng clone khi chạy

Tạo file **`CloneAccessDemo.java`**:

```java
public class CloneAccessDemo {
    static final class WithoutMarker {
        private final int id;

        WithoutMarker(int id) {
            this.id = id;
        }

        WithoutMarker copyUsingSuper()
                throws CloneNotSupportedException {
            return (WithoutMarker) super.clone();
        }
    }

    static final class WithMarker implements Cloneable {
        private final int id;

        WithMarker(int id) {
            this.id = id;
        }

        @Override
        public WithMarker clone() {
            try {
                return (WithMarker) super.clone();
            } catch (CloneNotSupportedException ex) {
                throw new AssertionError(ex);
            }
        }

        int id() {
            return id;
        }
    }

    public static void main(String[] args) {
        WithoutMarker a = new WithoutMarker(7);
        try {
            a.copyUsingSuper();
            System.out.println("unexpected success");
        } catch (CloneNotSupportedException ex) {
            System.out.println("without marker: not supported");
        }

        WithMarker original = new WithMarker(42);
        WithMarker copy = original.clone();

        System.out.println("same instance: "
                + (original == copy));
        System.out.println("same id: "
                + (original.id() == copy.id()));
        System.out.println("copy class: "
                + copy.getClass().getSimpleName());
    }
}
```

Biên dịch và chạy:

```bash
javac CloneAccessDemo.java
java CloneAccessDemo
```

Stdout:

```text
without marker: not supported
same instance: false
same id: true
copy class: WithMarker
```

**Giải thích:**

1. `WithoutMarker.copyUsingSuper()` nằm bên trong lớp con của `Object`, nên `super.clone()` **hợp lệ lúc biên dịch**, nhưng instance **không triển khai `Cloneable`**, vì vậy `Object.clone()` ném `CloneNotSupportedException`.
2. `WithMarker` triển khai `Cloneable` và ghi đè `clone()` thành `public`: mã ở `main` gọi được, kết quả là instance khác nhưng giữ lại trường `id`.
3. Kiểu trả về là `WithMarker`, không phải `Object`, nhờ **covariant return type**, nên caller không cần cast.
4. Dùng `getSimpleName()` để trình bày kiểu kết quả; đây không phải bằng chứng độc lập rằng bản sao đã sao chép sâu các trường tham chiếu.

Mã trên giải quyết **hai vấn đề khác nhau**: quyền gọi được mở rộng bằng override `public`; khả năng `super.clone()` hoạt động nhờ marker `Cloneable`.

## 5. `Object.clone()` thực hiện shallow copy

Khi mặc định sao chép các trường, biến nguyên thủy được sao chép **giá trị**; còn trường kiểu tham chiếu được sao chép **tham chiếu**. Điều này dẫn đến hiện tượng bản gốc và bản clone cùng giữ một đối tượng con mutable.

Tạo file **`ShallowCloneDemo.java`**:

```java
public class ShallowCloneDemo {
    static final class Report implements Cloneable {
        private final String name;
        private int[] scores;

        Report(String name, int[] scores) {
            this.name = name;
            this.scores = scores;
        }

        @Override
        public Report clone() {
            try {
                return (Report) super.clone();
            } catch (CloneNotSupportedException ex) {
                throw new AssertionError(ex);
            }
        }

        int[] scores() {
            return scores;
        }
    }

    public static void main(String[] args) {
        Report first = new Report("Q1", new int[] {2, 4});
        Report second = first.clone();

        System.out.println("same report: "
                + (first == second));
        System.out.println("same scores array: "
                + (first.scores() == second.scores()));

        second.scores()[0] = 9;
        System.out.println("first score: "
                + first.scores()[0]);

        int[][] grid = {{1, 2}, {3}};
        int[][] gridCopy = grid.clone();

        System.out.println("same outer array: "
                + (grid == gridCopy));
        System.out.println("same inner row: "
                + (grid[0] == gridCopy[0]));
    }
}
```

Chạy:

```bash
javac ShallowCloneDemo.java
java ShallowCloneDemo
```

Kết quả:

```text
same report: false
same scores array: true
first score: 9
same outer array: false
same inner row: true
```

`Report.clone()` tạo đối tượng `Report` mới, nhưng `scores` của cả hai instance cùng trỏ tới **một mảng `int[]`**. Vì vậy thay đổi mảng qua `second` được nhìn thấy qua `first`.

**Điểm đặc biệt về mảng:** mảng Java cung cấp thao tác `clone()` **công khai** dù `Object.clone()` là `protected`; bản sao là mảng mới cùng loại runtime. Mảng nguyên thủy một chiều sao chép các giá trị nguyên thủy nên độc lập về nội dung phần tử. Nhưng `int[][]` là mảng gồm các phần tử `int[]`: `grid.clone()` chỉ sao chép mảng ngoài, **các hàng vẫn là tham chiếu chung**. Mảng cũng triển khai `Cloneable` và `Serializable`; không cần tự viết override.

Lưu ý rằng ví dụ `Report` cố ý để lộ mảng mutable qua `scores()` để chứng minh shallow copy. Đây không phải thiết kế đóng gói dữ liệu được khuyến nghị cho mọi ứng dụng.

## 6. Nếu cần tách dữ liệu mutable, clone phải có chính sách sao chép rõ ràng

Một lớp chứa mảng mutable có thể ghi đè `clone()` để clone mảng con sau khi gọi `super.clone()`. Tạo file **`IndependentCloneDemo.java`**:

```java
public class IndependentCloneDemo {
    static final class ScoreCard implements Cloneable {
        private int[] scores;

        ScoreCard(int[] input) {
            scores = input.clone();
        }

        int get(int index) {
            return scores[index];
        }

        void set(int index, int value) {
            scores[index] = value;
        }

        @Override
        public ScoreCard clone() {
            try {
                ScoreCard copy = (ScoreCard) super.clone();
                copy.scores = scores.clone();
                return copy;
            } catch (CloneNotSupportedException ex) {
                throw new AssertionError(ex);
            }
        }
    }

    public static void main(String[] args) {
        ScoreCard original = new ScoreCard(new int[] {2, 4});
        ScoreCard copied = original.clone();

        copied.set(0, 99);
        System.out.println("original score: " + original.get(0));
        System.out.println("copied score: " + copied.get(0));
        System.out.println("same instance: "
                + (original == copied));
    }
}
```

Lệnh:

```bash
javac IndependentCloneDemo.java
java IndependentCloneDemo
```

Stdout:

```text
original score: 2
copied score: 99
same instance: false
```

Ở đây lớp sao chép mảng đầu vào để tránh chia sẻ ngoài ý muốn, sau đó `clone()` sao chép thêm mảng `scores`. Bản clone và bản gốc có thể được chỉnh sửa độc lập.

**Những giới hạn cần hiểu:**

- Với mảng chứa **đối tượng mutable**, gọi `array.clone()` chỉ tách mảng ngoài, các phần tử vẫn là cùng tham chiếu. Muốn deep copy phải xác định cách sao chép **từng phần tử** phù hợp với ngữ nghĩa nghiệp vụ.
- Với trường tham chiếu `final`, không thể gán lại nó thành bản sao riêng bên trong `clone()` như `copy.scores = ...` nếu `scores` là `final`. Cố ép thiết kế clone sâu vào một đối tượng bất biến phức tạp có thể không phù hợp; thường nên chọn copy constructor/factory.
- Đối với tài nguyên như lock, socket, thread, file handle hoặc kết nối cơ sở dữ liệu, **không được mặc nhiên sao chép tham chiếu** hay coi có thể nhân bản đối tượng. Cần định nghĩa chính sách sở hữu và vòng đời tài nguyên.
- Nếu đối tượng có invariant cần thiết lập trong constructor, `Object.clone()` không chạy constructor nên có thể không thích hợp để khởi tạo lại invariant đó.

Phân biệt **sao chép sâu** và **bất biến**: một value object chỉ tham chiếu tới các thành phần thực sự bất biến có thể sử dụng shallow copy mà vẫn an toàn về ngữ nghĩa, vì các thành phần chia sẻ không thể bị sửa. Không nhất thiết phải deep copy mọi trường.

## 7. Checked exception và chữ ký khi override

`Object.clone()` khai báo `throws CloneNotSupportedException`. Lớp con khi ghi đè có ba lựa chọn phổ biến:

1. **Giữ checked exception** nếu muốn cho người gọi xử lý trường hợp không thể clone.
2. **Không khai báo checked exception** khi class đã chủ động triển khai `Cloneable`, `super.clone()` được kỳ vọng luôn thành công theo thiết kế hiện tại; bắt và chuyển thành `AssertionError` như ví dụ.
3. **Không sử dụng `Object.clone()`** và công bố một thao tác sao chép khác (`copy()` hoặc copy constructor), tùy ý định API.

Một override không thể mở rộng tập checked exception so với phương thức cha; có thể thu hẹp hoặc bỏ khai báo checked exception. `@Override` giúp trình biên dịch xác nhận đúng chữ ký. **Ném `CloneNotSupportedException` là cơ chế của `Object.clone()`**, không phải lỗi quyền truy cập protected: lỗi truy cập thường bị phát hiện **ngay lúc biên dịch**.

Trong một hệ phân cấp thừa kế, `super.clone()` gọi triển khai clone của lớp cha gần nhất; nếu lớp cha ghi đè clone và thêm hành vi thì kết quả phụ thuộc hợp đồng đó. Việc subclass cần clone đúng còn phụ thuộc cấu trúc trường và invariant của từng lớp trên chuỗi thừa kế.

## 8. Khi nào nên dùng copy constructor hoặc `copy()`?

`Cloneable`/`Object.clone()` có một số điểm gây khó dùng:

- Interface `Cloneable` không cung cấp một phương thức `public clone()` trong contract của nó.
- `Object.clone()` là `protected`, cần override hoặc phương thức công khai khác để người gọi bên ngoài dùng.
- Clone mặc định là shallow; với dữ liệu lồng, cần tự định nghĩa độ sâu và xử lý tài nguyên.
- `Object.clone()` không gọi constructor theo cách tạo instance bình thường, có thể xung đột với bất biến và chính sách khởi tạo.
- Một hệ phân cấp có cả class mutable/final fields có thể khó định nghĩa nhất quán cách clone mở rộng.

Một copy constructor giúp chủ động chọn trường nào được sao chép, cách tạo dữ liệu mới, cách kiểm tra invariant và cách đặt tên:

```java
final class Labels {
    private final String[] values;

    Labels(String[] input) {
        values = input.clone();
    }

    Labels(Labels other) {
        this(other.values);
    }

    String[] valuesCopy() {
        return values.clone();
    }
}
```

Đây là **mảnh khai báo lớp**, không phải chương trình có `main`. Đầu vào được giả định khác `null`; nếu API cho phép null cần thêm kiểm tra hoặc chính sách chuẩn hóa. Với mảng `String[]` (các phần tử `String` bất biến), clone mảng đủ để tách khả năng thay đổi vị trí phần tử. Khi phần tử là đối tượng mutable, cần quy tắc sao chép tiếp.

Copy constructor có thể gọi constructor thường, xác thực invariant theo cùng một lộ trình. Tuy nhiên, copy constructor cũng không tự tạo deep copy nếu chỉ gán chung các tham chiếu; **độ sâu sao chép phải được thiết kế** ở bất kỳ API nào.

Nếu một đối tượng thực sự **bất biến**, việc trả lại chính instance trong một phương thức định nghĩa là lấy giá trị bất biến có thể là chính sách hợp lý, nhưng không nên giả sử đó là hành vi mặc định của `Object.clone()`.

## 9. Quy trình chọn API sao chép cho lớp mới

**Bước 1 — Xác định nhu cầu sao chép.** Người gọi có cần một instance khác hay chỉ cần một giá trị tương đương? Dữ liệu con có được phép chia sẻ không?

**Bước 2 — Xác định khả năng truy cập.** Mã gọi có ở package khác không? Nếu công bố API clone, override `public` và cân nhắc kiểu trả về cụ thể.

**Bước 3 — Chọn hỗ trợ `Cloneable` có chủ đích.** Đánh dấu `implements Cloneable` nếu lớp dự định gọi `Object.clone()`; chỉ có interface marker không đủ để công bố clone.

**Bước 4 — Chọn độ sâu sao chép.** Dữ liệu chỉ gồm primitive và immutable value có thể chấp nhận shallow; mảng/list/map mutable hoặc tài nguyên cần quyết định sao chép/chia sẻ/từ chối rõ ràng.

**Bước 5 — Kiểm tra invariant, lớp cha và subclass.** Mỗi lớp trên chuỗi thừa kế có thể có trạng thái cần xử lý. `final` fields, constructor và quyền truy cập clone cần được cân nhắc cùng nhau.

**Bước 6 — Kiểm tra ngoại lệ và hợp đồng public API.** Nếu không muốn người gọi xử lý checked exception, chỉ loại bỏ `throws CloneNotSupportedException` khi có phương án xử lý hợp lý trong override.

**Bước 7 — Đánh giá copy constructor hoặc factory.** Nếu clone đòi hỏi nhiều thao tác khởi tạo phức tạp, một phương thức sao chép có chủ đích thường dễ kiểm tra và bảo trì.

## 10. Những lỗi thường gặp

| Nhận định hoặc cách viết | Vấn đề | Cách làm phù hợp |
| --- | --- | --- |
| “Mọi object đều gọi được `.clone()`.” | `Object.clone()` là `protected` | Chỉ gọi theo quy tắc truy cập, hoặc cung cấp API `public` |
| “`implements Cloneable` tạo public `clone()`.” | `Cloneable` là marker interface, không khai báo phương thức | Override `public clone()` nếu cần |
| “`super.clone()` biên dịch được tức là chạy thành công.” | Chưa triển khai `Cloneable` có thể ném checked exception | Xác định cả quyền gọi và marker |
| “Gọi `obj.clone()` được vì runtime là lớp có public clone.” | Kiểu tĩnh `Object` khiến chỉ thấy `Object.clone()` protected | Dùng kiểu API thích hợp hoặc cast có kiểm soát |
| “Clone bản ghi có mảng là clone sâu.” | `Object.clone()` sao chép tham chiếu | Sao chép thành phần mutable khi cần |
| “Mảng hai chiều `clone()` sao chép mọi hàng.” | Chỉ sao chép mảng ngoài | Sao chép từng hàng nếu muốn độc lập |
| “Clone gọi constructor nên invariant tự khởi tạo lại.” | Object.clone không đi qua constructor bình thường | Chọn cơ chế sao chép cho phép xác thực invariant |
| “Dùng clone là tốt nhất cho mọi đối tượng.” | Khó xác định sâu/nông, vòng đời tài nguyên và thừa kế | Cân nhắc copy constructor/factory |
| “`CloneNotSupportedException` là lỗi protected.” | Đó là ngoại lệ runtime từ thao tác clone, không phải lỗi truy cập compile time | Phân biệt hai giai đoạn |
| “Sao chép `final` reference là bảo đảm độc lập.” | Object tham chiếu bên trong vẫn có thể mutable | Kiểm tra bất biến sâu hoặc copy có chủ đích |

## 11. Checklist code review

- [ ] Có phân biệt rõ **phạm vi truy cập `protected`** và **điều kiện `Cloneable`**?
- [ ] Người gọi ở package khác có truy cập được phương thức công khai dành cho việc sao chép?
- [ ] Override `clone()` có đúng chữ ký, kiểu trả về và mức truy cập không?
- [ ] `super.clone()` có được sử dụng ở nơi hợp lệ về mặt truy cập Java?
- [ ] Nếu `CloneNotSupportedException` có thể xảy ra, chính sách xử lý đã rõ?
- [ ] Độ sâu sao chép của từng trường primitive, mảng, collection, object mutable và tài nguyên đã được xác định?
- [ ] Với mảng nhiều chiều, các hàng có còn chia sẻ sau `clone()` không? Điều đó có đúng yêu cầu?
- [ ] Constructor/invariant, `final` fields và hợp đồng lớp cha có được tôn trọng?
- [ ] Có bài kiểm thử cho việc bản gốc và clone là hai instance khác nhau và cho các thay đổi độc lập khi cần?
- [ ] Có nên dùng `copy()` hoặc copy constructor để API rõ ràng hơn không?

**Ghi nhớ:** `Object.clone()` là **`protected`**, còn `Cloneable` chỉ là dấu hiệu hỗ trợ clone mặc định. Muốn người gọi bên ngoài thực hiện sao chép, lớp thường cần công bố API `public` với hợp đồng độ sâu và ngoại lệ rõ ràng. `super.clone()` chủ yếu tạo **shallow copy**; nếu dữ liệu chứa thành phần mutable, thiết kế phải quyết định chính xác phần nào được chia sẻ và phần nào được nhân bản.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="clone-contract-003">Cloneable là marker interface và không tự khai báo phương thức clone()</a>

<details>
<summary>Xem chi tiết</summary>

# `Cloneable` là marker interface và không tự khai báo phương thức `clone()`

Trong Java, tên `Cloneable` và `clone()` rất giống nhau nhưng thuộc **hai nơi khác nhau**:

- `java.lang.Cloneable` là **marker interface**: một interface rỗng dùng để đánh dấu rằng một đối tượng cho phép cơ chế `Object.clone()` tạo bản sao theo từng trường (*field-for-field copy*).
- `clone()` là phương thức được khai báo trong `java.lang.Object`, với dạng gốc `protected Object clone() throws CloneNotSupportedException`. **Không có phương thức `clone()` nào được khai báo trong `Cloneable`.**

Chỉ viết `implements Cloneable` **không tự sinh phương thức `public clone()`**, không mở rộng phạm vi truy cập của `Object.clone()` và không bảo đảm rằng bên ngoài lớp có thể gọi `object.clone()`. Muốn cung cấp API sao chép cho người dùng lớp, lập trình viên thường phải **ghi đè `clone()`**, xác định kiểu trả về và mức truy cập, đồng thời quyết định cách xử lý trạng thái có thể thay đổi.

## 1. Marker interface là gì?

Marker interface là interface không có phương thức trừu tượng nào cần triển khai; việc một kiểu `implements` interface đó cung cấp **thông tin đánh dấu** cho cơ chế khác kiểm tra. Với `Cloneable`, tín hiệu này được `Object.clone()` sử dụng.

Có thể hình dung đơn giản:

~~~java
// Chỉ để hiểu cấu trúc; không khai báo lại java.lang.Cloneable.
public interface Cloneable {
    // Không có phương thức clone().
}
~~~

Mã trên là **mô hình minh họa**, không phải một interface cần tạo trong dự án. Java đã có sẵn `java.lang.Cloneable`.

Ví dụ một lớp chỉ mang marker:

~~~java
final class MarkerOnly implements Cloneable {
    private int value = 10;
}
~~~

Lớp này hợp lệ; nó **không phải** viết thân cho phương thức nào của `Cloneable` vì interface không yêu cầu phương thức. Tuy vậy, đoạn mã sau ở một lớp gọi thông thường sẽ không biên dịch:

~~~java
MarkerOnly value = new MarkerOnly();
value.clone(); // Lỗi truy cập: Object.clone() vẫn là protected.
~~~

Chú ý: đoạn trên chỉ là **mảnh mã cố ý sai** để giải thích phạm vi truy cập, không ghép vào ví dụ chạy được. Từ khóa `implements Cloneable` không giống một lời hứa rằng lớp đã có API `public clone()`.

### Bảng phân biệt

| Thành phần | Khai báo ở đâu? | Vai trò | Có thể gọi trực tiếp từ mã bên ngoài? |
| --- | --- | --- | --- |
| `Cloneable` | Interface `java.lang.Cloneable` | Đánh dấu tính tương thích với cơ chế `Object.clone()` | Không có phương thức nào để gọi |
| `Object.clone()` | Lớp `java.lang.Object` | Thực hiện bản sao theo từng trường khi điều kiện cho phép | Thường **không**, vì `protected` |
| `public MyType clone()` | Do lớp của bạn override | Cung cấp API sao chép với kiểu trả về rõ ràng | **Có**, nếu chính lớp công khai phương thức đó |
| Copy constructor/factory | Do lớp thiết kế, chẳng hạn `new MyType(original)` | Cách sao chép chủ động, không dựa vào marker | Có, theo mức truy cập được khai báo |

**Dấu hiệu khác chức năng:** marker `Cloneable` ảnh hưởng đến cách **implementation của `Object.clone()`** kiểm tra đối tượng, nhưng không tự thêm một phương thức vào interface để người gọi sử dụng.

## 2. `Object.clone()` có chữ ký và phạm vi truy cập ra sao?

Trong Java 17, khai báo của phương thức gốc là:

~~~java
protected Object clone() throws CloneNotSupportedException
~~~

Có bốn điểm cần phân biệt:

1. `protected`: phương thức gốc không phải API public tùy ý gọi trên mọi `Object`. Quy tắc truy cập `protected` của Java còn liên quan đến package và quan hệ kế thừa; không nên hiểu rằng vì một lớp là `Cloneable` thì mã bất kỳ ở bên ngoài được gọi `clone()`.
2. `Object`: kiểu trả về của phương thức gốc rất rộng. Khi override, một lớp có thể dùng **kiểu trả về hiệp biến** (*covariant return type*), chẳng hạn `public Account clone()`.
3. `throws CloneNotSupportedException`: đây là checked exception của phương thức gốc. Một override có thể tiếp tục khai báo ngoại lệ này, hoặc thu hẹp/bỏ checked exception trong chữ ký public.
4. `Object.clone()` là phương thức **instance**; nó sao chép đối tượng hiện tại, chứ không phải factory static nằm trong `Cloneable`.

Khi một lớp override phương thức gốc để công khai API:

~~~java
final class Account implements Cloneable {
    private final String id;

    Account(String id) {
        this.id = id;
    }

    @Override
    public Account clone() {
        try {
            return (Account) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}
~~~

Đây là **một lớp minh họa có thể biên dịch trong chương trình phù hợp**, với giả định khóa `id` không `null` được kiểm tra ở tầng nghiệp vụ. `@Override` giúp trình biên dịch xác nhận phương thức ghi đè `Object.clone()`; `public` mở quyền gọi; `Account` là kiểu trả về cụ thể. Vì lớp `Account` là `final` và triển khai `Cloneable`, lời gọi `super.clone()` bình thường không ném `CloneNotSupportedException`; nhánh `AssertionError` chỉ báo một vi phạm bất thường đối với giả định này.

Không nhất thiết phải dùng đúng mẫu trên: một lớp có thể định nghĩa copy constructor hoặc một phương thức `copy()`; và một override `clone()` cũng có thể dùng cách sao chép riêng mà không gọi `super.clone()`. Do đó, **`Cloneable` là tín hiệu dành cho cơ chế `Object.clone()`**, không phải quy tắc chung rằng mọi phương thức tên `clone` đều bắt buộc có marker.

## 3. Marker cho phép điều gì khi gọi `super.clone()`?

Cơ chế `Object.clone()` có các bước/đặc điểm đáng nhớ:

- Khi được gọi trên instance có kiểu runtime **không triển khai `Cloneable`**, implementation của `Object.clone()` ném `CloneNotSupportedException`.
- Khi điều kiện marker được đáp ứng, `Object.clone()` tạo đối tượng khác có **cùng lớp runtime** và sao chép nội dung trường hiện có theo phép gán; nó không lần lượt gọi constructor để khởi tạo lại các trường như tạo object bằng `new`.
- Trường kiểu nguyên thủy (`int`, `double`, `boolean`, …) nhận bản sao giá trị.
- Trường kiểu tham chiếu (`List`, mảng, đối tượng mutable, …) nhận **bản sao tham chiếu**. Hai object khác nhau có thể vẫn cùng trỏ đến một object con.
- Đây là **shallow copy** (*sao chép nông*). Nó không tự sao chép đệ quy toàn bộ cấu trúc đối tượng.

Chẳng hạn sau khi `super.clone()` thực hiện, object nguồn và object bản sao có thể là hai `Bag` khác nhau nhưng trường `items` đều trỏ tới **cùng một `ArrayList`**. Nếu một bên sửa list đó, bên kia quan sát được nội dung đã đổi.

### Nếu không có marker thì sao?

Ví dụ:

~~~java
final class NotMarked {
    @Override
    public NotMarked clone() throws CloneNotSupportedException {
        return (NotMarked) super.clone();
    }
}
~~~

Đây là lớp minh họa độc lập với mẫu `Account`. Nó **có public `clone()`** và override đúng `Object.clone()`, nhưng không `implements Cloneable`. Vì phương thức ủy quyền cho `super.clone()`, lời gọi sẽ ném `CloneNotSupportedException` trên một instance `NotMarked`.

Điều này cho thấy **override phương thức** và **đặt marker** là hai việc khác nhau: với cách cài đặt dựa trên `Object.clone()`, cần cả quyền truy cập phù hợp lẫn điều kiện marker.

Phân biệt thêm: nếu lớp tự viết `clone()` bằng `new NotMarked(...)` mà **không gọi `Object.clone()`**, nó có thể tự tạo bản sao mà không cần marker. Vì vậy câu “không implements `Cloneable` thì không bao giờ có thể có phương thức sao chép” là khẳng định quá rộng. Điều được `Object` quy định là hành vi của **chính `Object.clone()`**.

## 4. Ví dụ Java 17 chạy được: marker, protected, checked exception và shallow copy

Tạo tệp **`CloneableMarkerDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CloneableMarkerDemo {

    // Có marker nhưng không công khai clone().
    static final class MarkerOnly implements Cloneable {
        private final int value = 7;
    }

    // Công khai clone() nhưng không có marker:
    // super.clone() sẽ ném CloneNotSupportedException.
    static final class NotMarked {
        @Override
        public NotMarked clone() throws CloneNotSupportedException {
            return (NotMarked) super.clone();
        }
    }

    // Trường nguyên thủy: shallow copy đủ cho ví dụ này.
    static final class Point implements Cloneable {
        private final int x;

        Point(int x) {
            this.x = x;
        }

        @Override
        public Point clone() {
            try {
                return (Point) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
    }

    // Trường tham chiếu là danh sách mutable.
    static final class Bag implements Cloneable {
        private final List<String> items;

        Bag(List<String> input) {
            this.items = new ArrayList<>(input);
        }

        @Override
        public Bag clone() {
            try {
                return (Bag) super.clone(); // Sao chép nông.
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }

        Bag copyWithSeparateList() {
            return new Bag(items); // Tạo danh sách riêng bằng constructor.
        }
    }

    public static void main(String[] args) {
        MarkerOnly marker = new MarkerOnly();
        System.out.println("marker present: "
                + (marker instanceof Cloneable));
        System.out.println("Cloneable declared methods: "
                + Cloneable.class.getDeclaredMethods().length);

        try {
            new NotMarked().clone();
        } catch (CloneNotSupportedException e) {
            System.out.println("not marked: "
                    + e.getClass().getSimpleName());
        }

        Point point = new Point(7);
        Point pointCopy = point.clone();
        System.out.println("point distinct: " + (point != pointCopy));
        System.out.println("point x equal: " + (point.x == pointCopy.x));
        System.out.println("point equals copy: " + point.equals(pointCopy));

        Bag original = new Bag(List.of("A"));
        Bag shallow = original.clone();
        Bag separate = original.copyWithSeparateList();

        System.out.println("shallow distinct bag: "
                + (original != shallow));
        System.out.println("shallow shares list: "
                + (original.items == shallow.items));
        System.out.println("separate shares list: "
                + (original.items == separate.items));

        original.items.add("B");
        System.out.println("shallow items: " + shallow.items);
        System.out.println("separate items: " + separate.items);

        int[] numbers = {1, 2};
        int[] numbersCopy = numbers.clone();
        System.out.println("array distinct: " + (numbers != numbersCopy));
        System.out.println("array same contents: "
                + Arrays.equals(numbers, numbersCopy));

        int[][] matrix = {{1, 2}};
        int[][] matrixCopy = matrix.clone();
        System.out.println("inner array shared: "
                + (matrix[0] == matrixCopy[0]));
    }
}
~~~

Biên dịch/chạy trong thư mục chứa tệp (JDK 17 trở lên):

~~~bash
javac CloneableMarkerDemo.java
java CloneableMarkerDemo
~~~

**Đầu ra dự kiến:**

~~~text
marker present: true
Cloneable declared methods: 0
not marked: CloneNotSupportedException
point distinct: true
point x equal: true
point equals copy: false
shallow distinct bag: true
shallow shares list: true
separate shares list: false
shallow items: [A, B]
separate items: [A]
array distinct: true
array same contents: true
inner array shared: true
~~~

Diễn giải theo nhóm:

1. `MarkerOnly` triển khai marker, nhưng interface `Cloneable` có **0 phương thức được khai báo**. Chương trình không gọi `marker.clone()`, bởi `Object.clone()` vẫn là `protected` trong trường hợp này.
2. `NotMarked` có override public nhưng gọi `super.clone()` khi thiếu marker, nên nhận checked exception `CloneNotSupportedException`.
3. `Point.clone()` dùng `super.clone()` để tạo instance mới. Hai `x` bằng nhau vì kiểu nguyên thủy được sao chép theo giá trị. Nhưng `point.equals(pointCopy)` là `false`, vì lớp `Point` **không override `equals()`**, nên kế thừa equality theo identity từ `Object`.
4. `Bag.clone()` tạo đối tượng `Bag` khác nhưng **cùng `items`** (shallow copy). Trong khi đó `copyWithSeparateList()` dùng constructor để tạo `ArrayList` khác. Khi sửa list nguồn, bản sao nông thấy `[A, B]` còn bản sao với list riêng giữ `[A]`.
5. `int[].clone()` tạo mảng một chiều khác có cùng các phần tử nguyên thủy. `int[][].clone()` chỉ tạo **mảng ngoài** khác; các mảng con bên trong vẫn được chia sẻ.

Trong ví dụ `Bag` chỉ chứa `String` (bất biến), nên sao chép riêng `ArrayList` đủ để cách ly thao tác thêm/xóa chuỗi. Nếu list chứa các đối tượng mutable, `new ArrayList<>(items)` vẫn chỉ sao chép **cấu trúc danh sách**, chưa sao chép bản thân từng phần tử.

## 5. Vì sao `Cloneable reference = ...` không có `clone()`?

Vì `Cloneable` rỗng, một biến được khai báo với kiểu tĩnh `Cloneable` không có phương thức `clone()` trong tập API của interface:

~~~java
Cloneable value = new Point(7);

// value.clone(); // Không biên dịch: Cloneable không khai báo clone().
~~~

Đây là đoạn minh họa dùng kiểu `Point` ở chương trình trước. Ngay cả khi instance runtime là `Point` đã có `public Point clone()`, **kiểu tĩnh** của biến `value` không cho phép gọi `value.clone()`. Có thể gọi `((Point) value).clone()` khi đã biết chắc kiểu runtime và thiết kế cho phép, nhưng không nên dựa vào cast như một cơ chế sao chép tổng quát.

Muốn thiết kế một API generic để client gọi sao chép mà không biết concrete class, hãy khai báo **một interface riêng có phương thức sao chép rõ ràng**:

~~~java
interface Copyable<T> {
    T copy();
}
~~~

Đây là interface minh họa. Các lớp triển khai tự quyết định ngữ nghĩa của `copy()` (sao chép nông, sao chép cấu trúc hay tạo đối tượng giá trị mới) và viết tài liệu rõ ràng. **Không** nhầm interface `Copyable<T>` tự tạo với `java.lang.Cloneable`.

## 6. Vai trò của `@Override`, kiểu trả về và ngoại lệ

Việc override chính xác quan trọng như với các phương thức khác của `Object`:

~~~java
@Override
public Point clone() {
    try {
        return (Point) super.clone();
    } catch (CloneNotSupportedException e) {
        throw new AssertionError(e);
    }
}
~~~

- `@Override` đảm bảo trình biên dịch nhận ra đây là ghi đè `Object.clone()`, không phải overload vô tình thêm tham số.
- `public` mở rộng phạm vi truy cập từ `protected`; một override không được thu hẹp quyền so với phương thức cha.
- `Point` là kiểu trả về hiệp biến tương thích với `Object`, giúp client không phải ép kiểu sau khi gọi `point.clone()`.
- Không khai báo checked exception trong override là hợp lệ: phương thức ghi đè được phép bỏ `throws CloneNotSupportedException`. Chỉ khi gọi `super.clone()` trong thân phương thức mới cần xử lý ngoại lệ được khai báo bởi phương thức gốc.
- `super.clone()` tạo shallow copy bằng cơ chế kế thừa; nó **không gọi `Point()`** để chạy lại logic khởi tạo thông thường.

Nếu một lớp **không phải `final`**, subclass và quy ước kế thừa cần được xem xét kỹ. `super.clone()` sử dụng kiểu runtime của instance và có thể sao chép trường của subclass theo cùng quy tắc nông. Một subclass chứa tài nguyên mutable hoặc bất biến nghiệp vụ phức tạp có thể cần sửa cấu trúc con sau khi sao chép. Không nên mở rộng khả năng clone tùy tiện trong một hệ phân cấp mà chưa thiết kế rõ ai chịu trách nhiệm sao chép từng tầng.

## 7. Sao chép nông, sao chép sâu và lựa chọn thay thế

| Cách làm | Cái gì được tạo mới? | Điều cần lưu ý |
| --- | --- | --- |
| `super.clone()` trên class hợp lệ | Instance bên ngoài và bản sao giá trị trường; reference field giữ nguyên tham chiếu | **Shallow copy**, có thể chia sẻ object con |
| `array.clone()` | Một mảng mới ở tầng ngoài | Mảng đa chiều vẫn chia sẻ các mảng con |
| `new ArrayList<>(oldList)` | Cấu trúc list mới | Phần tử object trong hai list có thể vẫn cùng tham chiếu |
| Copy constructor `new Type(original)` | Theo logic constructor do lập trình viên định nghĩa | Có thể chủ động sao chép sâu một số trường và giữ các trường bất biến |
| Static factory `Type.copyOf(original)` | Theo hợp đồng factory được định nghĩa | Tên `copyOf` không tự bảo đảm bản sao luôn khác instance |
| Thiết kế object immutable | Có thể chia sẻ an toàn trạng thái bất biến | Giảm nhu cầu clone; cần bảo đảm bất biến cả ở cấu trúc con |

**Deep copy** không phải một nút bật sẵn của `Cloneable`. Muốn sao chép sâu một object graph có mảng, list, map và đối tượng lồng nhau, phải xác định cách nhân bản từng thành phần cần tách riêng. Cần cân nhắc aliasing (hai đường trong object graph trỏ cùng object), cycle (tham chiếu vòng), tài nguyên ngoài JVM (socket/file handle), và bất biến nghiệp vụ. Việc làm sâu hoàn toàn đôi khi không khả thi hoặc không đúng mục tiêu.

Nếu kiểu dữ liệu chứa chủ yếu giá trị bất biến như `String`, `LocalDate` hoặc các record chứa toàn thành phần bất biến, **copy constructor/factory** thường dễ hiểu hơn `Cloneable`. Với object immutable, việc tạo bản sao riêng có thể không cần thiết vì hai bên có thể chia sẻ cùng dữ liệu bất biến một cách an toàn.

## 8. Những hiểu nhầm thường gặp

| Hiểu nhầm | Sự thật |
| --- | --- |
| “`implements Cloneable` tự cung cấp `public clone()`.” | `Cloneable` không khai báo phương thức; `Object.clone()` vốn là `protected` |
| “Interface `Cloneable` định nghĩa `clone()`.” | `clone()` được khai báo trong `Object` |
| “Cứ gọi `clone()` qua biến `Cloneable`.” | Kiểu tĩnh `Cloneable` không có API `clone()` |
| “Override `clone()` gọi `super.clone()` là đủ cho mọi lớp.” | Có thể phải sao chép riêng trạng thái mutable để tránh chia sẻ ngoài ý muốn |
| “Không implements `Cloneable` thì mọi phương thức tự viết tên `clone` đều thất bại.” | Chỉ cơ chế `Object.clone()` kiểm tra marker; cách sao chép thủ công có thể khác |
| “`clone()` luôn làm `clone.equals(original)` là `true`.” | Không bắt buộc; ví dụ `Point` mặc định so identity cho kết quả `false` |
| “Clone gọi lại constructor như `new`.” | `Object.clone()` sao chép trạng thái trường, không dùng constructor theo luồng khởi tạo thông thường |
| “`int[][]` được clone toàn bộ tới từng hàng.” | `matrix.clone()` chỉ nhân bản mảng ngoài |
| “Sao chép list là sao chép sâu mọi phần tử.” | `new ArrayList<>(...)` là bản sao cấu trúc list, phần tử mutable có thể dùng chung |

## 9. Tự kiểm tra kiến thức

1. **`Cloneable` khai báo bao nhiêu phương thức?** — Không có phương thức; đây là marker interface.
2. **Một class `implements Cloneable` nhưng không override `clone()` có chắc client bên ngoài gọi được không?** — Không. `Object.clone()` vẫn có quyền truy cập `protected`.
3. **Ai thực hiện sao chép trường nếu viết `return (Type) super.clone();`?** — Implementation của `Object.clone()`.
4. **Điều gì xảy ra nếu `super.clone()` được gọi trên runtime class không triển khai `Cloneable`?** — `CloneNotSupportedException`.
5. **Khi field là `List<String>`, `super.clone()` có tự tạo list mới không?** — Không; hai object chia sẻ tham chiếu list trừ khi override/cách sao chép tự sửa lại.
6. **Tại sao mảng `int[][]` vẫn chia sẻ hàng sau `clone()`?** — Vì mảng ngoài được copy nông, từng phần tử của nó là tham chiếu mảng con.
7. **Nếu cần một API sao chép dễ gọi, nên làm gì?** — Công khai phương thức/copy constructor/factory rõ hợp đồng, quyết định độ sâu sao chép theo dữ liệu; chỉ chọn `Cloneable`/`Object.clone()` khi hiểu rõ tính năng và giới hạn.

**Ghi nhớ:** `Cloneable` chỉ là **tín hiệu cho cơ chế của `Object.clone()`**. Nó không định nghĩa một phương thức trong interface, không tự làm `clone()` thành public và không tự cung cấp deep copy. Hãy phân biệt ba câu hỏi: **đối tượng có marker không?**, **client có quyền gọi phương thức sao chép nào?**, và **những phần dữ liệu nào thật sự được nhân bản?**

### Tài liệu gốc

- [Java SE 17 — Cloneable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Cloneable.html)
- [Java SE 17 — Object.clone()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#clone())
- [Java Language Specification 17 — Array Members `10.7`](https://docs.oracle.com/javase/specs/jls/se17/html/jls-10.html#jls-10.7)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="clone-contract-004">CloneNotSupportedException khi Object.clone() không được phép</a>

<details>
<summary>Xem chi tiết</summary>

# `CloneNotSupportedException` khi `Object.clone()` không được phép

## Mục tiêu

Trong Java, `Object.clone()` là cơ chế hỗ trợ tạo bản sao đối tượng, nhưng không phải đối tượng Java nào cũng có thể được clone bằng cơ chế này. Nếu `Object.clone()` được gọi trên một instance của lớp **không triển khai `Cloneable`**, phương thức sẽ ném `CloneNotSupportedException`. Muốn xử lý đúng, bạn cần hiểu cả **điều kiện chạy** của cơ chế clone, **quyền truy cập phương thức** và **checked exception**.

Sau bài học, bạn sẽ:

- Giải thích được chính xác khi nào `Object.clone()` ném `CloneNotSupportedException`.
- Hiểu vì sao `Cloneable` là marker interface và không tự cung cấp phương thức `clone()` công khai.
- Viết được một lớp gọi `super.clone()` an toàn khi có `implements Cloneable`.
- Phân biệt lỗi biên dịch do `protected` hoặc checked exception với `CloneNotSupportedException` xảy ra khi chạy.
- Phân tích trường hợp lớp con, mảng, bản sao nông, và phương thức `clone()` ghi đè.
- Quyết định khi nào nên dùng constructor sao chép hay phương thức `copy()` thay cho `Object.clone()`.

## 1. `Object.clone()` và điều kiện được phép tạo bản sao

Tất cả lớp Java thông thường kế thừa `java.lang.Object` trực tiếp hoặc gián tiếp. `Object` khai báo:

```java
protected Object clone() throws CloneNotSupportedException
```

Có ba yếu tố quan trọng:

1. **`protected`**: phương thức `clone()` mặc định không được gọi tùy ý trên bất kỳ đối tượng nào từ mã bên ngoài. Lớp có thể ghi đè để cung cấp API `public`.
2. **`throws CloneNotSupportedException`**: đây là **checked exception**. Mã gọi một phương thức có khai báo có thể ném exception này phải bắt nó hoặc khai báo tiếp bằng `throws`, trừ khi đã xử lý qua một API khác không khai báo exception.
3. **Điều kiện `Cloneable` ở thời điểm chạy**: khi triển khai `Object.clone()` thực sự được sử dụng cho một object không có marker `java.lang.Cloneable`, lời gọi thất bại bằng `CloneNotSupportedException`.

Điều kiện thứ ba áp dụng theo **lớp chạy thực tế của đối tượng** (runtime class). Nếu đối tượng kế thừa từ một lớp đã triển khai `Cloneable`, nó cũng là một `Cloneable` theo quan hệ kế thừa interface.

### Marker interface là gì?

`java.lang.Cloneable` là một **marker interface**, nghĩa là nó không định nghĩa phương thức nào. Viết:

```java
class Draft implements Cloneable {
    private int version;
}
```

giúp instance của `Draft` đáp ứng dấu hiệu mà `Object.clone()` nhận biết. Nhưng chỉ riêng dòng `implements Cloneable` **không tạo ra một phương thức `public clone()` trong lớp**. `clone()` vẫn là phương thức `protected` kế thừa từ `Object` nếu lớp không ghi đè.

Điều này phân biệt hai câu hỏi:

- **Có đủ điều kiện về marker để `Object.clone()` hỗ trợ sao chép?** Có nếu runtime object là `Cloneable`.
- **Mã gọi hiện tại có nhìn thấy một phương thức `clone()` để gọi không?** Phụ thuộc phạm vi truy cập và cách lớp khai báo API.

Một đối tượng có marker `Cloneable` nhưng không mở API clone công khai có thể không gọi được `obj.clone()` từ mã client, dù bản thân đối tượng đủ điều kiện khi một phương thức hợp lệ trong lớp gọi `super.clone()`.

## 2. Vì sao `CloneNotSupportedException` được ném?

Ví dụ sau minh họa chính xác trường hợp thất bại: lớp `Plain` **không** triển khai `Cloneable` nhưng vẫn khai báo một phương thức `public clone()` và gọi `super.clone()`.

```java
class Plain {
    private final int id;

    Plain(int id) {
        this.id = id;
    }

    @Override
    public Plain clone() throws CloneNotSupportedException {
        return (Plain) super.clone();
    }
}
```

Mã trên có thể **biên dịch hợp lệ**: lớp được phép ghi đè `Object.clone()` và gọi `super.clone()` từ bên trong phương thức instance của chính lớp. Tuy nhiên:

```java
Plain original = new Plain(7);
Plain copy = original.clone(); // Ném CloneNotSupportedException khi chạy
```

Đây là đoạn trong một phương thức có xử lý hoặc khai báo checked exception. Lúc gọi `super.clone()`, cơ chế trong `Object.clone()` kiểm tra object hiện tại và nhận thấy `Plain` không triển khai `Cloneable`. Vì thế exception được ném **trước khi nhận được bản sao**.

Phân biệt rõ: phương thức `clone()` được khai báo `public` ở đây **không phải** bằng chứng đối tượng clone được. Cách ghi đè chỉ mở quyền gọi; marker `Cloneable` mới là tín hiệu mà `Object.clone()` sử dụng.

### Exception có nghĩa gì?

`CloneNotSupportedException` là lớp exception trong `java.lang`, kế thừa `Exception` và thuộc nhóm checked exception. Nó biểu thị rằng một thao tác clone đã được yêu cầu nhưng không được hỗ trợ bởi cơ chế đang dùng.

Với `Object.clone()`, trường hợp chuẩn là lớp chạy thực tế của đối tượng không triển khai `Cloneable`. Tuy nhiên một lớp **tự ghi đè** `clone()` vẫn có thể chủ động ném exception vì chính sách riêng. Không nên xem mọi lần gặp exception cùng tên là bằng chứng duy nhất rằng class chắc chắn thiếu marker; cần kiểm tra stack trace và phương thức triển khai đang chạy.

## 3. Sửa trường hợp chuẩn: triển khai `Cloneable` và gọi `super.clone()`

Một mẫu clone truyền thống là:

```java
class CloneReady implements Cloneable {
    private final int id;

    CloneReady(int id) {
        this.id = id;
    }

    @Override
    public CloneReady clone() throws CloneNotSupportedException {
        return (CloneReady) super.clone();
    }
}
```

Trong trường hợp này:

- `implements Cloneable` cấp marker cho runtime object.
- `@Override public CloneReady clone()` mở API công khai và dùng **kiểu trả về hiệp biến** (`covariant return type`) thay vì `Object`.
- `super.clone()` dùng cơ chế sao chép của `Object`. Do instance là `Cloneable`, thao tác thông thường được cho phép.
- Ép kiểu `(CloneReady)` hợp lệ với bản sao của instance `CloneReady`; phương thức trả về đúng kiểu cho người gọi.

Một số lớp có thể xử lý `CloneNotSupportedException` ngay bên trong vì chúng biết marker luôn có mặt theo thiết kế. Nhưng nếu phương thức vẫn khai báo `throws` như ví dụ, mã client phải bắt hoặc khai báo exception tương ứng.

**Giới hạn:** `super.clone()` thực hiện bản sao **nông** (shallow copy) đối với các trường tham chiếu: đối tượng gốc và bản sao có hai identity khác nhau nhưng một số trường có thể cùng trỏ đến các đối tượng con. Marker `Cloneable` không tự sinh “deep copy”.

## 4. Chương trình Java 17+ hoàn chỉnh: clone thất bại, clone thành công và copy thủ công

Lưu tệp sau dưới tên `ClonePermissionDemo.java`:

```java
import java.util.Arrays;

public class ClonePermissionDemo {
    static final class Plain {
        private final int id;

        Plain(int id) {
            this.id = id;
        }

        @Override
        public Plain clone() throws CloneNotSupportedException {
            return (Plain) super.clone();
        }
    }

    static class CloneReady implements Cloneable {
        private final int id;
        private final int[] scores;

        CloneReady(int id, int[] scores) {
            this.id = id;
            this.scores = scores;
        }

        @Override
        public CloneReady clone() throws CloneNotSupportedException {
            return (CloneReady) super.clone();
        }

        int id() {
            return id;
        }

        int[] scores() {
            return scores;
        }
    }

    static final class Child extends CloneReady {
        Child(int id, int[] scores) {
            super(id, scores);
        }
    }

    static final class ManualCopy {
        private final int id;

        ManualCopy(int id) {
            this.id = id;
        }

        ManualCopy copy() {
            return new ManualCopy(id);
        }

        int id() {
            return id;
        }
    }

    public static void main(String[] args) throws CloneNotSupportedException {
        Plain plain = new Plain(7);
        try {
            plain.clone();
            System.out.println("unexpected success");
        } catch (CloneNotSupportedException ex) {
            System.out.println("plain rejected: "
                    + ex.getClass().getSimpleName());
        }

        CloneReady original = new CloneReady(11, new int[] {2, 3});
        CloneReady copy = original.clone();
        System.out.println("different instances: " + (original != copy));
        System.out.println("copied id: " + copy.id());
        System.out.println("same scores array: "
                + (original.scores() == copy.scores()));

        original.scores()[0] = 99;
        System.out.println("copy sees changed score: "
                + copy.scores()[0]);

        Child child = new Child(12, new int[] {4});
        CloneReady clonedChild = child.clone();
        System.out.println("child clone retains type: "
                + (clonedChild instanceof Child));

        int[] numbers = {5, 6};
        int[] numbersCopy = numbers.clone();
        System.out.println("array clone: "
                + (numbers != numbersCopy));
        System.out.println("array contents: "
                + Arrays.equals(numbers, numbersCopy));

        ManualCopy manual = new ManualCopy(15);
        ManualCopy manualCopy = manual.copy();
        System.out.println("manual copy works: "
                + (manual != manualCopy
                   && manual.id() == manualCopy.id()));
    }
}
```

Lệnh biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac ClonePermissionDemo.java
java ClonePermissionDemo
```

Đầu ra dự kiến:

```text
plain rejected: CloneNotSupportedException
different instances: true
copied id: 11
same scores array: true
copy sees changed score: 99
child clone retains type: true
array clone: true
array contents: true
manual copy works: true
```

### Phân tích từng kết quả

- `Plain.clone()` gọi `Object.clone()` khi instance không triển khai `Cloneable`; exception được bắt để chương trình có thể tiếp tục.
- `CloneReady` triển khai marker và có một phương thức clone hợp lệ. `original != copy` là `true` vì bản sao là một instance mới.
- `id` là `int`, nên giá trị 11 được sao chép vào đối tượng mới.
- `scores` là `int[]`; `Object.clone()` sao chép **tham chiếu** đến mảng con. Hai instance cùng giữ một mảng. Khi sửa `original.scores()[0]`, đọc từ `copy.scores()[0]` cũng thấy 99.
- `Child` kế thừa từ `CloneReady`, nên runtime object `Child` cũng triển khai `Cloneable`. `super.clone()` tạo bản sao giữ đúng lớp chạy thực tế `Child`, dù phương thức có kiểu trả về khai báo là `CloneReady`.
- Mảng Java có cơ chế `clone()` được hỗ trợ sẵn; `numbers.clone()` tạo một mảng riêng với cùng phần tử nguyên thủy.
- `ManualCopy` dùng constructor để tạo một object mới. Nó không cần `implements Cloneable` vì **không sử dụng `Object.clone()`**.

Chú ý đây là ví dụ học cơ chế, không phải mô hình encapsulation an toàn cho dữ liệu mutable: `CloneReady.scores()` trả trực tiếp tham chiếu mảng. Nếu mục tiêu là bản sao độc lập, cần có chính sách sao chép mảng/đối tượng con rõ ràng.

## 5. Ba trường hợp dễ nhầm: lỗi biên dịch, checked exception và exception lúc chạy

Không phải tình huống nào có chữ `clone` cũng dẫn đến `CloneNotSupportedException` ở runtime.

| Tình huống | Kết quả điển hình | Nguyên nhân |
| --- | --- | --- |
| Gọi `new Plain(1).clone()`, trong đó `Plain` có `public clone()` gọi `super.clone()` nhưng không có `Cloneable` | Ném `CloneNotSupportedException` khi chạy | `Object.clone()` từ chối runtime object |
| Lớp chỉ `implements Cloneable`, không mở `public clone()`, mã client gọi `instance.clone()` | Lỗi biên dịch do quyền truy cập | `Object.clone()` vẫn `protected` |
| Có `public clone() throws CloneNotSupportedException` nhưng người gọi không bắt hoặc khai báo | Lỗi biên dịch về checked exception | Java kiểm tra khai báo `throws` |
| Lớp `implements Cloneable` và gọi `super.clone()` hợp lệ | Tạo object mới | Marker thỏa điều kiện của `Object.clone()` |
| Lớp dùng `new` trong `copy()`, không gọi `Object.clone()` | Tạo object mới | Không phụ thuộc marker clone |
| Một mảng gọi `array.clone()` | Clone được hỗ trợ | Mảng Java hỗ trợ clone theo cơ chế ngôn ngữ |

### Lỗi `protected` khác exception hỗ trợ clone

```java
class MarkerOnly implements Cloneable {
    private int value;
}

class Example {
    void demo() {
        MarkerOnly x = new MarkerOnly();
        // x.clone(); // KHÔNG biên dịch: Object.clone() là protected
    }
}
```

Đoạn minh họa trên có hai lớp và chỉ dùng để chỉ ra quyền truy cập. `implements Cloneable` không tự chuyển `clone()` thành public.

### Checked exception phải được xử lý

```java
// Đặt trong một phương thức có thể gọi CloneReady.clone():
try {
    CloneReady cloned = original.clone();
    // Sử dụng cloned tại đây.
} catch (CloneNotSupportedException ex) {
    throw new IllegalStateException("Unexpected clone failure", ex);
}
```

Đây là snippet minh họa bên trong một phương thức, với `original` kiểu `CloneReady` và `CloneReady` được định nghĩa như ví dụ phần 4. Lớp có thể lựa chọn khai báo `throws CloneNotSupportedException` thay vì `try/catch` nếu contract API cho phép lỗi được xử lý ở cấp gọi phía trên.

**Không che giấu lỗi tùy tiện:** nếu exception thực sự có thể phát sinh vì thiết kế thiếu marker, bắt rồi trả về `null` sẽ chuyển lỗi thành một lỗi khác khó phát hiện hơn. Chọn cách xử lý phù hợp: sửa contract, thông báo lỗi, hoặc dùng cơ chế sao chép riêng.

## 6. `Cloneable` xét theo lớp chạy thực tế

Khi gọi `super.clone()`, kiểm tra marker không chỉ dựa trên tên biến tham chiếu. Nó liên quan đến đối tượng thực sự đang được sao chép.

Ví dụ, nếu `Base implements Cloneable` và `Derived extends Base`, instance `Derived` vẫn là một `Cloneable` ngay cả khi khai báo lớp con không nhắc lại tên interface. Ngược lại, nếu `Base` không triển khai `Cloneable`, nhưng `Derived` **có** triển khai, lời gọi `Object.clone()` từ một phương thức được kế thừa khi thực hiện trên instance `Derived` có thể được phép.

Minh họa:

```java
class Base {
    @Override
    public Base clone() throws CloneNotSupportedException {
        return (Base) super.clone();
    }
}

class Derived extends Base implements Cloneable {}

class Demo {
    static void check() throws CloneNotSupportedException {
        Base base = new Base();
        Derived derived = new Derived();

        // base.clone(); // Sẽ ném CloneNotSupportedException nếu được gọi
        Base copied = derived.clone(); // Thành công: runtime là Derived
        System.out.println(copied instanceof Derived); // true
    }
}
```

Đây là đoạn minh họa độc lập về lớp và phương thức `check()`; muốn chạy, cần một `main` gọi `Demo.check()`. Quy tắc thể hiện ở đây: **điều kiện Cloneable phụ thuộc runtime class**, không chỉ phụ thuộc lớp khai báo phương thức `clone()`.

Nó cũng cho thấy lời gọi `super.clone()` **không chỉ đơn giản tạo `new Base()`**. Khi được cho phép, đối tượng kết quả giữ đúng kiểu chạy thực tế.

## 7. Nếu đã triển khai `Cloneable`, có chắc mọi `clone()` đều thành công?

**Không có bảo đảm chung rằng mọi phương thức tên `clone()` ở mọi lớp đều thành công.** `Cloneable` là tín hiệu đặc biệt cho thao tác `Object.clone()`, nhưng một lớp có thể ghi đè `clone()` và thực hiện logic khác, bao gồm việc từ chối clone theo chính sách riêng.

Ví dụ một lớp có thể quyết định không hỗ trợ sao chép ở API công khai:

```java
class ForbiddenCopy implements Cloneable {
    @Override
    public ForbiddenCopy clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException("Copy forbidden by policy");
    }
}
```

Khi gọi `new ForbiddenCopy().clone()`, exception vẫn xảy ra **dù lớp triển khai `Cloneable`**, vì phương thức ghi đè chủ động ném ngoại lệ và không gọi `Object.clone()`. Đó là chính sách của lớp, khác với kiểm tra marker của `Object.clone()`.

Một số thư viện cũng cung cấp phương thức `clone()` tự cài đặt mà không gọi `super.clone()`. Khi đó marker `Cloneable` không phải yếu tố quyết định toàn bộ hành vi. Muốn kết luận nguyên nhân lỗi, đọc implementation và stack trace của phương thức thực sự được gọi.

## 8. Clone nông là điều kiện cần hiểu trước khi đưa vào ứng dụng

Ngay cả khi `Object.clone()` chạy thành công, kết quả không tự động là **bản sao sâu** (deep copy). Theo cơ chế mặc định, các trường nguyên thủy được sao chép giá trị; các trường tham chiếu được sao chép **tham chiếu**, không clone đệ quy từng đối tượng con.

```text
Bản gốc CloneReady          Bản sao CloneReady
  id = 11                      id = 11
  scores ──────┐               scores ──────┐
               │                           │
               └────► int[] {2, 3} ◄───────┘

Hai CloneReady khác nhau; mảng scores chỉ có một.
```

Đây là lý do ví dụ phần 4 in:

```text
same scores array: true
copy sees changed score: 99
```

Khi cần bản sao độc lập, lớp có thể triển khai sau `super.clone()` hoặc dùng constructor copy để tạo bản sao các trường mutable. Ví dụ phương pháp sau thường dễ đọc khi các trường có cấu trúc đơn giản:

```java
final class DataSnapshot {
    private final int[] values;

    DataSnapshot(int[] values) {
        this.values = values.clone();
    }

    DataSnapshot copy() {
        return new DataSnapshot(values);
    }

    int[] values() {
        return values.clone();
    }
}
```

Đoạn này minh họa một lớp không dùng `Object.clone()`: constructor và accessor sao chép mảng để tránh chia sẻ mảng mutable ra ngoài. Với một đồ thị chứa các đối tượng phức tạp hoặc chu trình tham chiếu, chính sách sao chép sâu cần được thiết kế riêng.

### Tại sao array `clone()` không ném `CloneNotSupportedException`?

Mảng Java hỗ trợ clone và có phương thức `clone()` phù hợp do ngôn ngữ cung cấp. Mảng là `Cloneable`, nên `array.clone()` tạo một mảng mới. Phần tử kiểu nguyên thủy được sao chép giá trị; với mảng đối tượng, tham chiếu phần tử được sao chép, vì thế bản sao vẫn là sao chép nông ở mức phần tử tham chiếu.

```java
StringBuilder[] builders = {new StringBuilder("A")};
StringBuilder[] copied = builders.clone();

System.out.println(builders != copied);       // true
System.out.println(builders[0] == copied[0]); // true
```

Các dòng nằm trong một phương thức. Mảng ngoài được clone thành object mới, nhưng hai mảng cùng giữ tham chiếu đến một `StringBuilder`.

## 9. Thiết kế API sao chép khi không muốn phụ thuộc vào `Object.clone()`

`Cloneable` và `Object.clone()` là cơ chế có từ lâu trong Java, nhưng không phải lúc nào cũng là lựa chọn tốt nhất cho mô hình nghiệp vụ. Các phương án phổ biến khác:

**Copy constructor:**

```java
final class PointData {
    private final int x;
    private final int y;

    PointData(int x, int y) {
        this.x = x;
        this.y = y;
    }

    PointData(PointData source) {
        this(source.x, source.y);
    }
}
```

**Factory hoặc phương thức `copy()`** có tên rõ ràng:

```java
final class UserSettings {
    private final String theme;

    UserSettings(String theme) {
        this.theme = theme;
    }

    UserSettings copy() {
        return new UserSettings(theme);
    }
}
```

Hai đoạn trên là khai báo lớp độc lập làm ví dụ. Cả hai kỹ thuật hoạt động mà không cần `implements Cloneable`. Chúng cho phép constructor kiểm tra invariant, quyết định sao chép sâu/nông và thiết kế API rõ ràng cho người dùng.

Với một **record bất biến đúng nghĩa** chứa các giá trị bất biến, đôi khi không cần tạo bản sao: cùng dữ liệu đã có thể được dùng lại theo thiết kế. Nếu record chứa đối tượng con mutable như mảng, cần xem xét defensive copy riêng. Không nên cố dùng `Object.clone()` chỉ vì muốn “copy một thứ gì đó”.

Các phương án này không làm thay đổi nguyên nhân của `CloneNotSupportedException`: exception vẫn xảy ra nếu bạn thực sự gọi `Object.clone()` với đối tượng không đáp ứng điều kiện marker.

## 10. Lỗi thường gặp

1. **Quên `implements Cloneable` nhưng vẫn gọi `super.clone()`.** Lớp có thể biên dịch, nhưng `Object.clone()` sẽ ném `CloneNotSupportedException` khi runtime object không có marker.
2. **Nghĩ `implements Cloneable` tự tạo `public clone()`.** Không đúng; `Cloneable` không khai báo phương thức. Nếu không ghi đè, phương thức kế thừa từ `Object` vẫn `protected`.
3. **Nhầm lỗi access với exception hỗ trợ clone.** Một lời gọi bị chặn bởi `protected` thất bại ở giai đoạn biên dịch, không phải đợi chạy để ném `CloneNotSupportedException`.
4. **Không bắt hoặc khai báo checked exception.** Nếu signature `clone()` khai báo `throws CloneNotSupportedException`, người gọi phải xử lý theo quy tắc Java.
5. **Cho rằng một lớp con không tự viết `implements Cloneable` thì chắc chắn clone thất bại.** Marker được kế thừa qua phân cấp kiểu.
6. **Cho rằng phương thức `clone()` ghi đè không thể từ chối khi class là `Cloneable`.** Lớp có thể định nghĩa chính sách riêng và ném exception.
7. **Cho rằng clone thành công là deep copy.** `Object.clone()` sao chép nông; các tham chiếu đến object mutable có thể còn chia sẻ.
8. **Bắt lỗi rồi bỏ qua hoặc trả `null`.** Cách này che mất nguyên nhân và có thể gây `NullPointerException` về sau. Cần sửa contract hoặc xác định chiến lược xử lý rõ ràng.
9. **Dùng clone khi chỉ cần một object mới từ dữ liệu đã biết.** Copy constructor hoặc factory thường đơn giản hơn và tránh yêu cầu marker/checked exception.
10. **Cho rằng mọi method tên `clone()` đều gọi `Object.clone()`.** Hãy xem mã ghi đè; một method có thể tự tạo object mới hoặc từ chối mà không gọi `super.clone()`.

## 11. Bài tập tự kiểm tra

### Câu 1 — Dự đoán nguyên nhân

Cho đoạn:

```java
class Document {
    @Override
    public Document clone() throws CloneNotSupportedException {
        return (Document) super.clone();
    }
}
```

Khi gọi `new Document().clone()` trong một `try/catch` hợp lệ, kết quả là gì?

**Đáp án:** `CloneNotSupportedException` vì `Document` không triển khai `Cloneable` nhưng gọi tới `Object.clone()`.

### Câu 2 — Marker nhưng không có API public

```java
class Document implements Cloneable {
    private String text;
}
```

Mã client ở lớp không có quyền truy cập `protected` có gọi trực tiếp `document.clone()` được không?

**Đáp án:** Không. `Cloneable` không khai báo một phương thức `clone()` public. Cần lớp công khai API sao chép hoặc dùng phương thức khác do lớp cung cấp.

### Câu 3 — Lớp con

```java
class Parent implements Cloneable {
    @Override
    public Parent clone() throws CloneNotSupportedException {
        return (Parent) super.clone();
    }
}

class Child extends Parent {}
```

Nếu `Child child = new Child();`, việc gọi `Parent p = child.clone();` có thể thành công không, và `p instanceof Child` là gì?

**Đáp án:** Có, vì `Child` kế thừa marker `Cloneable` và phương thức clone public. `p instanceof Child` là `true` vì cơ chế `Object.clone()` sao chép đúng lớp runtime.

### Câu 4 — Sao chép theo chính sách riêng

Một lớp không muốn đối tượng mutable bị chia sẻ sau khi copy thì chỉ thêm `implements Cloneable` đã đủ chưa?

**Đáp án:** Chưa. Cần kiểm soát sao chép các trường tham chiếu mutable, dùng clone/copy constructor có defensive copy hoặc một cách tạo bản sao sâu được thiết kế cho cấu trúc dữ liệu cụ thể.

## Ghi nhớ

`CloneNotSupportedException` xảy ra trong trường hợp tiêu biểu khi **`Object.clone()` được yêu cầu sao chép một runtime object không triển khai `Cloneable`**. `Cloneable` là marker interface, không cung cấp sẵn `public clone()`; phương thức `Object.clone()` gốc là `protected` và khai báo checked exception. Muốn mở API clone theo cơ chế mặc định, lớp cần hỗ trợ marker và thường ghi đè `clone()` để gọi `super.clone()` hợp lệ. Sau khi clone thành công, hãy nhớ đó vẫn có thể là bản sao nông; khi cần chính sách sao chép rõ ràng, copy constructor hoặc `copy()` là phương án phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="clone-contract-005">super.clone() sao chép nông các trường</a>

<details>
<summary>Xem chi tiết</summary>

# `super.clone()` sao chép nông các trường trong Java

## 1. `super.clone()` làm gì?

Trong Java, `java.lang.Object` định nghĩa phương thức `protected Object clone() throws CloneNotSupportedException`. Một lớp có thể override `clone()` rồi gọi `super.clone()` để tạo một đối tượng mới có trạng thái ban đầu được sao chép từ đối tượng đang gọi. Nếu chuỗi gọi đi tới triển khai của `Object.clone()`, thao tác này thực hiện **sao chép nông** (*shallow copy*) các trường.

Hãy tưởng tượng đối tượng `Document` có các trường:

```text
Document original
├── title = "Java"           (tham chiếu String)
├── version = 1              (int)
└── tags ──────────────┐
                       ▼
                ArrayList ["core"]

Document clone (sau super.clone)
├── title = "Java"           (cùng tham chiếu String ban đầu)
├── version = 1              (bản sao giá trị int)
└── tags ──────────────┘     (cùng đối tượng ArrayList)
```

`original` và `clone` là **hai đối tượng `Document` khác nhau** (`original != clone`), nhưng trường `tags` của cả hai lại trỏ tới **cùng một** `ArrayList`.

- **Trường nguyên thủy** (`int`, `long`, `boolean`, ...): giá trị được chép sang trường của đối tượng mới; gán lại trường ở một bản không đổi trường của bản còn lại.
- **Trường tham chiếu** (`String`, `List`, array, object nghiệp vụ, ...): chép **giá trị tham chiếu**, không tự tạo đối tượng con mới. Hai bản có thể cùng trỏ tới một đối tượng con.
- **Trường `static`** thuộc về lớp, không phải trạng thái của từng instance; `clone` không tạo bản sao độc lập của những trường này.
- **Trường `final`** cũng có giá trị được sao chép. Nếu đó là tham chiếu tới một đối tượng mutable, `final` không giúp tách đối tượng con.
- **Constructor của lớp được clone không được chạy lại** bởi cơ chế `Object.clone()`; trạng thái được tạo bằng sao chép trường, không phải khởi tạo một instance qua constructor thông thường.

Như vậy, `super.clone()` không có nghĩa là "sao chép toàn bộ đồ thị đối tượng". Nó chỉ tạo lớp vỏ đối tượng mới với các giá trị trường tương ứng, khi lời gọi đi tới cơ chế clone mặc định của `Object`.

## 2. Điều kiện để gọi được `super.clone()`

`Object.clone()` là phương thức `protected`, vì vậy lớp con có thể gọi `super.clone()` bên trong chính triển khai của mình. Tuy nhiên, nếu đối tượng thực tế **không thực hiện interface `Cloneable`**, triển khai `Object.clone()` sẽ ném `CloneNotSupportedException` (mảng Java có cơ chế clone riêng).

```java
class Example implements Cloneable {
    @Override
    public Example clone() {
        try {
            return (Example) super.clone();
        } catch (CloneNotSupportedException ex) {
            throw new AssertionError(ex);
        }
    }
}
```

Đây là một khai báo lớp hoàn chỉnh nhưng chưa có `main`. Những điểm cần hiểu:

1. `Cloneable` là **marker interface** trong `java.lang`, không khai báo phương thức `clone()`. Việc `implements Cloneable` không tự sinh một API `clone()` public để caller sử dụng.
2. Lớp chủ động override `clone()` thành `public` nếu muốn người dùng gọi được. Tăng phạm vi truy cập từ `protected` thành `public` là hợp lệ.
3. Kiểu trả về `Example` thay vì `Object` là **covariant return type** hợp lệ, giúp caller không phải ép kiểu kết quả. Ép kiểu `(Example)` bên trong cần thiết vì `super.clone()` có kiểu khai báo `Object`.
4. `super.clone()` được gọi bên trong `try` vì chữ ký của `Object.clone()` khai báo ngoại lệ checked `CloneNotSupportedException`.
5. Với lớp `Example` thực hiện `Cloneable` và dùng cơ chế mặc định, lỗi trên là bất thường; mã chuyển thành `AssertionError` để biểu thị giả định thiết kế đã bị phá. Một lớp khác cũng có thể công khai `throws CloneNotSupportedException` tùy API của mình.

Đừng nhầm **đã khai báo `Cloneable`** với việc **đã có API sao chép hoàn chỉnh, đúng nghĩa nghiệp vụ**. `Cloneable` chỉ cho phép cơ chế clone của `Object`; việc tách dữ liệu mutable, xác minh bất biến và xử lý các quan hệ giữa các trường vẫn là trách nhiệm của lớp.

## 3. Hợp đồng thông thường: khác danh tính nhưng trạng thái bắt đầu tương ứng

Nếu `x.clone()` được thiết kế theo quy ước phổ biến:

- Bản sao thường có **runtime class tương ứng với bản gốc**.
- Bản sao thường là đối tượng riêng biệt: `x.clone() != x`.
- Trong nhiều lớp giá trị, ngay sau khi clone, `x.clone().equals(x)` thường là `true`, nhưng **không phải bảo đảm tuyệt đối** cho mọi kiểu dữ liệu hay mọi override tùy biến.

Lý do là một lớp có thể định nghĩa `equals()` theo **danh tính** (mặc định `Object.equals`), lúc đó hai đối tượng khác tham chiếu không bằng nhau. Cũng có thể có lớp override `clone()` với quy tắc đặc biệt. Vì thế, **clone và equals là hai hợp đồng khác nhau**; không được suy luận rằng clone chắc chắn khiến `equals` bằng `true`.

Nếu gọi tới `Object.clone()`, đối tượng mới có cùng **runtime class** với bản gốc và các trường được chép theo cơ chế mặc định. `super.clone()` không cắt phần trạng thái ở lớp con: một lời gọi hợp lệ trên đối tượng lớp con sẽ sao chép những trường instance thuộc cả lớp cha lẫn lớp con. Tuy nhiên, nếu có lớp cha **override `clone()`** thì lời gọi `super.clone()` của lớp con đi vào override lớp cha đó, có thể có logic bổ sung; không được giả định mọi `super.clone()` đều trực tiếp gọi ngay `Object.clone()`.

## 4. Ví dụ Java 17: bản sao nông cùng giữ một danh sách

Tạo tệp độc lập `CloneShallowDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class CloneShallowDemo {
    static final class Document implements Cloneable {
        private String title;
        private List<String> tags;

        Document(String title, List<String> tags) {
            this.title = title;
            this.tags = new ArrayList<>(tags);
        }

        @Override
        public Document clone() {
            try {
                return (Document) super.clone();
            } catch (CloneNotSupportedException ex) {
                throw new AssertionError(ex);
            }
        }

        Document detachedCopy() {
            Document copy = clone();
            copy.tags = new ArrayList<>(tags);
            return copy;
        }

        void addTag(String value) {
            tags.add(value);
        }

        void rename(String value) {
            title = value;
        }
    }

    public static void main(String[] args) {
        Document original = new Document("Java", List.of("core"));
        Document shallow = original.clone();

        System.out.println("different objects: " + (original != shallow));
        System.out.println("shared tags: "
                + (original.tags == shallow.tags));

        shallow.rename("Spring");
        System.out.println("original title: " + original.title);

        shallow.addTag("concurrency");
        System.out.println("original tags after shallow: "
                + original.tags);

        Document detached = original.detachedCopy();
        System.out.println("detached tags: "
                + (original.tags != detached.tags));

        detached.addTag("collections");
        System.out.println("original tags after detached: "
                + original.tags);
        System.out.println("detached tags after edit: "
                + detached.tags);
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac CloneShallowDemo.java
java CloneShallowDemo
```

Stdout:

```text
different objects: true
shared tags: true
original title: Java
original tags after shallow: [core, concurrency]
detached tags: true
original tags after detached: [core, concurrency]
detached tags after edit: [core, concurrency, collections]
```

### Vì sao kết quả như vậy?

1. `original.clone()` tạo một `Document` mới nên `original != shallow` là `true`.
2. `original.tags == shallow.tags` là `true`: hai trường chứa cùng tham chiếu tới `ArrayList` do clone nông.
3. Gọi `shallow.rename("Spring")` chỉ gán lại trường `title` của **bản shallow**; trường `title` trong `original` vẫn là `"Java"`. `String` cũng là immutable, nên không có chuyện sửa nội dung chuỗi dùng chung.
4. Gọi `shallow.addTag("concurrency")` thay đổi chính đối tượng `ArrayList` được hai bản dùng chung, vì vậy bản gốc cũng nhìn thấy phần tử mới.
5. `detachedCopy()` bắt đầu bằng bản sao nông, sau đó thay trường `copy.tags` bằng `new ArrayList<>(tags)`. Lúc này bản sao có **danh sách độc lập**. Thêm `"collections"` chỉ ảnh hưởng danh sách của `detached`.

**Giới hạn của `new ArrayList<>(tags)`:** đây chỉ là sao chép nông ở **cấp danh sách**. Trong ví dụ các phần tử là `String` bất biến nên đủ để tách trạng thái có thể thay đổi. Nếu danh sách chứa đối tượng mutable như `Address` có setter, hai danh sách vẫn có thể giữ **cùng từng instance `Address`**; muốn tách toàn bộ trạng thái thì phải sao chép từng phần tử theo chính sách rõ ràng.

## 5. Sao chép nông, sao chép sâu và số cấp cần tách

### Sao chép nông (*shallow copy*)

`super.clone()` sao chép các trường instance một lần:

```text
A ── address ──> Address { city = "Hue" }
B ── address ──┘
```

`A` và `B` là hai instance riêng, nhưng việc sửa `address.city` qua một phía được nhìn thấy qua phía kia.

### Sao chép sâu (*deep copy*)

Nếu nghiệp vụ đòi hỏi **không chia sẻ trạng thái mutable**, phải tạo các đối tượng con mới cần thiết:

```text
A ── address ──> Address { city = "Hue" }
B ── address ──> Address { city = "Hue" }  (instance riêng)
```

Không có một mức "deep copy" phổ quát duy nhất: cần xác định những thành phần nào được phép dùng chung. Những đối tượng immutable như `String` thường có thể chia sẻ an toàn; các entity có danh tính dùng chung, cache hoặc liên kết đối tượng có thể có quy tắc khác. Với đồ thị đối tượng có chu trình, quá trình sao chép sâu cần quản lý các đối tượng đã gặp để tránh đệ quy vô hạn và giữ đúng các quan hệ chia sẻ.

### Sao chép nông rồi tách từng trường

Với các trường có thể gán lại, mã thường có dạng:

```java
@Override
public MyType clone() {
    try {
        MyType copy = (MyType) super.clone();
        copy.mutableList = new ArrayList<>(mutableList);
        copy.numbers = numbers.clone();
        return copy;
    } catch (CloneNotSupportedException ex) {
        throw new AssertionError(ex);
    }
}
```

Đoạn trên là **khung minh họa** cho lớp `MyType implements Cloneable` có trường `mutableList` và `numbers` phù hợp. Nó không phải chương trình độc lập và vẫn có thể cần sao chép tiếp những đối tượng mutable bên trong `mutableList`.

Nếu các trường tham chiếu cần tách được khai báo `final`, ta không thể đơn giản gán lại `copy.mutableList` bên trong `clone`. Lúc đó thiết kế với **copy constructor** hoặc factory tạo đối tượng mới thường rõ ràng hơn và cho phép khởi tạo trường `final` theo bất biến mong muốn.

## 6. Clone mảng: tạo mảng mới nhưng không tự clone từng phần tử

Mảng Java hỗ trợ `clone()` dù người viết không khai báo `implements Cloneable`; mỗi mảng có phương thức clone riêng, trả kiểu mảng tương ứng. Nó cũng thực hiện **sao chép nông**.

- Với `int[]`, `clone()` tạo mảng mới chứa các số được sao chép. Sửa một phần tử của bản sao không sửa mảng gốc.
- Với `String[]`, `clone()` tạo mảng mới nhưng các phần tử là tham chiếu `String` được chép; `String` immutable nên chia sẻ các chuỗi thường an toàn.
- Với `Person[]`, `clone()` tạo mảng mới nhưng từng phần tử ban đầu vẫn có thể trỏ tới đúng các đối tượng `Person` cũ. Sửa một đối tượng `Person` thông qua bản sao sẽ được nhìn thấy qua bản gốc.
- Với `int[][]`, `clone()` của mảng ngoài chỉ tạo **mảng ngoài mới**; từng hàng `int[]` vẫn là các tham chiếu dùng chung. Muốn độc lập ở hai cấp cần clone từng hàng.

Một chương trình độc lập thứ hai làm rõ cả điều kiện `Cloneable`, constructor và mảng.

Tạo tệp `CloneMechanicsDemo.java`:

```java
public class CloneMechanicsDemo {
    static final class Unmarked {
        Object attempt() throws CloneNotSupportedException {
            return super.clone();
        }
    }

    static final class Counted implements Cloneable {
        static int created;

        Counted() {
            created++;
        }

        @Override
        public Counted clone() {
            try {
                return (Counted) super.clone();
            } catch (CloneNotSupportedException ex) {
                throw new AssertionError(ex);
            }
        }
    }

    public static void main(String[] args) {
        try {
            new Unmarked().attempt();
            System.out.println("unmarked allowed: true");
        } catch (CloneNotSupportedException ex) {
            System.out.println("unmarked rejected: true");
        }

        Counted original = new Counted();
        Counted copied = original.clone();
        System.out.println("constructor count: " + Counted.created);
        System.out.println("different Counted: " + (original != copied));
        System.out.println("same runtime class: "
                + (original.getClass() == copied.getClass()));

        int[][] source = {{1, 2}, {3}};
        int[][] outer = source.clone();
        System.out.println("new outer array: " + (source != outer));
        System.out.println("shared first row: "
                + (source[0] == outer[0]));

        outer[0][0] = 99;
        System.out.println("source first number: " + source[0][0]);

        int[][] detached = source.clone();
        for (int i = 0; i < detached.length; i++) {
            detached[i] = source[i].clone();
        }
        detached[0][0] = 7;
        System.out.println("source after row copies: "
                + source[0][0]);
        System.out.println("detached after row copies: "
                + detached[0][0]);
    }
}
```

Biên dịch và chạy:

```bash
javac CloneMechanicsDemo.java
java CloneMechanicsDemo
```

Stdout:

```text
unmarked rejected: true
constructor count: 1
different Counted: true
same runtime class: true
new outer array: true
shared first row: true
source first number: 99
source after row copies: 99
detached after row copies: 7
```

Giải thích:

- `Unmarked` không implements `Cloneable`, nên `Object.clone()` từ `attempt()` ném `CloneNotSupportedException`.
- Một đối tượng `Counted` được tạo bởi constructor thông thường. Gọi `clone()` không gọi lại constructor, nên `created` vẫn bằng `1`.
- `source.clone()` trả về mảng ngoài mới, nhưng `source[0] == outer[0]` vẫn đúng. Gán `outer[0][0] = 99` thực chất sửa hàng chung với mảng nguồn.
- Ở bước `detached`, từng hàng `int[]` cũng được clone. Vì thế gán `detached[0][0] = 7` không đổi `source[0][0]`.

Ví dụ giả định không có hàng `null` khi clone từng hàng. Nếu cấu trúc là mảng răng cưa cho phép hàng `null`, phải bảo vệ `source[i] == null` trước khi gọi `clone()`. Nếu các phần tử là đối tượng mutable thay vì `int`, clone hai tầng mảng vẫn chưa đồng nghĩa với sao chép sâu từng đối tượng phần tử.

## 7. Quan hệ với kế thừa và việc override `clone()`

Nếu một lớp cha `Base` có cơ chế clone được kế thừa, còn lớp con `Derived` bổ sung trường mới, việc gọi `Object.clone()` trên instance `Derived` sẽ sao chép trường instance của **toàn đối tượng `Derived`**, bao gồm trường lớp cha và lớp con. Tuy nhiên, việc sao chép các tham chiếu bổ sung của lớp con vẫn chỉ là sao chép nông.

Ví dụ khung:

```java
class Base implements Cloneable {
    int id;

    @Override
    public Base clone() {
        try {
            return (Base) super.clone();
        } catch (CloneNotSupportedException ex) {
            throw new AssertionError(ex);
        }
    }
}

class Derived extends Base {
    java.util.ArrayList<String> notes = new java.util.ArrayList<>();

    @Override
    public Derived clone() {
        Derived copy = (Derived) super.clone();
        copy.notes = new java.util.ArrayList<>(notes);
        return copy;
    }
}
```

Đây là **các khai báo lớp có thể đặt trong cùng tệp Java** (không có `main`), minh họa tầng cha sao chép nông trước và tầng con tách trường `notes` riêng của nó.

Điểm quan trọng:

1. `super.clone()` ở `Derived` gọi phiên bản `clone` của `Base`; phiên bản đó tiếp tục gọi `Object.clone()`.
2. `Base.clone()` trả về `Base` ở kiểu khai báo nhưng đối tượng clone thực tế là `Derived` khi receiver ban đầu là `Derived`. Ép kiểu `(Derived)` lúc này hợp lệ với cơ chế đã thiết kế.
3. Sau đó `Derived.clone()` sao chép thêm `notes` vì trường này tham chiếu dữ liệu có thể thay đổi. Nếu không làm bước này, bản gốc và bản clone sẽ dùng chung một danh sách.
4. Nếu có nhiều lớp con ở nhiều tầng, mỗi tầng có thể phải chịu trách nhiệm những tài nguyên mutable được thêm tại tầng đó. Cần giữ quy ước rõ ràng; việc clone trong hệ phân cấp mở rộng tùy ý có thể khó duy trì.

Không nên mặc định gọi `super.clone()` ở mọi cấp là đủ để bất biến hoặc tài nguyên nội bộ được xử lý đúng. Nếu lớp cha có trạng thái quan hệ phức tạp, các lớp con chưa biết rõ hợp đồng clone của lớp cha có thể tạo bản sao không hợp lệ.

## 8. Những bẫy với `final`, tài nguyên và liên kết đối tượng

### Trường `final` không đảm bảo deep copy

```java
private final java.util.List<String> tags;
```

Một lớp có trường trên không thể gán lại `copy.tags = new ArrayList<>(tags)` trong một phương thức `clone` thông thường. Cả hai bản được tạo bằng clone nông vẫn trỏ cùng danh sách; chỉ việc khai báo `final` **không biến danh sách thành immutable**. Nếu thiết kế cần danh sách riêng, copy constructor có thể khởi tạo trường `final` mới đúng cách.

### Tài nguyên hệ điều hành không nên sao chép vô thức

Các đối tượng chứa `FileInputStream`, socket, lock, transaction, thread hoặc handle native không thể mặc định có ý nghĩa khi chỉ copy các trường. Clone nông có thể khiến hai bản cùng giữ một tài nguyên, từ đó xảy ra đóng trùng, ghi đồng thời, sai ownership hoặc vi phạm bất biến.

Hãy định nghĩa rõ hành vi cần thiết: không hỗ trợ clone, tạo tài nguyên độc lập mới qua factory, hoặc chỉ sao chép dữ liệu cấu hình có thể chuyển giao. Đối với tài nguyên `AutoCloseable`, cơ chế quản lý vòng đời như `try-with-resources` vẫn cần được thiết kế riêng.

### Liên kết giữa các trường và giữa các object

Giả sử `left` và `right` của bản gốc cùng tham chiếu một đối tượng con, hoặc đối tượng con trỏ ngược về bản cha. Sao chép sâu không đơn thuần là gọi `clone` độc lập cho từng trường: có thể phải giữ **quan hệ dùng chung** (`left == right`) và sửa các con trỏ ngược đúng bản mới.

Với đồ thị có chu trình, nếu clone đệ quy mà không theo dõi đối tượng đã tạo thì có thể lặp vô hạn. Đây là lý do copy constructor/factory với quy tắc về danh tính, liên kết và bất biến rõ ràng thường phù hợp hơn cho mô hình có nhiều quan hệ.

## 9. Khi nào nên dùng `clone()`, khi nào nên chọn cách sao chép khác?

| Cách làm | Ưu điểm | Hạn chế/điều kiện |
| --- | --- | --- |
| `super.clone()` + điều chỉnh trường mutable | Có thể tái sử dụng sao chép trường mặc định, không cần truyền lại từng trường đơn giản. | Dễ bỏ sót tham chiếu mutable, khó xử lý `final`, kế thừa, constructor bất biến và tài nguyên. |
| Copy constructor `new Type(source)` | Có quyền tạo trạng thái mới, gán trường `final`, sao chép có chọn lọc và kiểm tra bất biến. | Cần chủ động liệt kê các dữ liệu cần sao chép, phải xác định xử lý lớp con. |
| Factory `Type.copyOf(source)` | API có thể nêu rõ chính sách copy, đôi khi tái sử dụng đối tượng immutable hợp lệ. | Phải có hợp đồng rõ về chia sẻ/sao chép và kiểu trả về. |
| Kiểu immutable/record | Ít cần copy phòng vệ khi mọi thành phần và dữ liệu con thật sự bất biến. | Record có thể chứa component mutable; immutable không tự động là bất biến sâu. |
| Sao chép thủ công từng thành phần/đồ thị | Kiểm soát rõ mức độ tách, ownership và xử lý chu trình. | Có thể tốn công và cần bộ nhớ trung gian để giữ quan hệ danh tính. |

Đối với các kiểu dữ liệu mới, Java không bắt buộc sử dụng `Cloneable` để có tính năng sao chép. Một copy constructor hoặc factory là lựa chọn thường dễ giải thích và kiểm thử. Nếu dùng `clone` vì tương thích API cũ hoặc vì lớp đã có hợp đồng clone rõ, cần kiểm tra kỹ tất cả trường tham chiếu.

Lưu ý rằng `clone` không phải là cơ chế serialization, không gọi lại constructor để đảm bảo các ràng buộc khởi tạo, và không tự động là giải pháp thread-safe. Nếu đối tượng đang được sửa đồng thời trong lúc clone, cần đồng bộ hoặc tạo snapshot nhất quán.

## 10. Những lỗi phổ biến và checklist kiểm tra

| Lỗi thường gặp | Hậu quả | Cách xử lý |
| --- | --- | --- |
| Cho rằng `super.clone()` là deep copy. | Hai đối tượng vẫn dùng chung list, mảng hoặc object mutable. | Xác định rõ trường nào cần tạo instance con mới. |
| Chỉ `implements Cloneable` rồi gọi `obj.clone()` ở ngoài lớp. | Interface không khai báo clone public; code có thể không biên dịch. | Cung cấp phương thức `clone` public hoặc API sao chép khác. |
| Quên `Cloneable` nhưng gọi `Object.clone()`. | `CloneNotSupportedException`. | Tuân thủ cơ chế Cloneable hoặc bỏ clone, dùng factory/copy constructor. |
| Nghĩ bản clone được khởi tạo qua constructor. | Có thể bỏ qua logic thiết lập bất biến trong constructor. | Rà soát bất biến, cân nhắc khởi tạo bằng constructor tường minh. |
| Dùng `new ArrayList<>(oldList)` rồi tin rằng phần tử mutable đã được clone. | Danh sách mới nhưng các phần tử vẫn dùng chung. | Sao chép từng phần tử khi cần. |
| Clone `int[][]` một lần để tách mọi hàng. | Mảng ngoài mới nhưng các hàng `int[]` vẫn chia sẻ. | Clone mỗi hàng ngoài việc clone mảng ngoài. |
| Cố gán trường `final` của bản clone sau `super.clone()`. | Lỗi biên dịch; không thể tái gán final trong clone thông thường. | Dùng constructor/factory tạo đối tượng mới. |
| Clone object có socket/stream/lock mà không định nghĩa ownership. | Hai bản có thể chia sẻ tài nguyên ngoài ý muốn. | Thiết kế vòng đời tài nguyên; có thể cấm clone. |
| Giả định `clone.equals(original)` luôn đúng. | Không đúng khi `equals` so theo danh tính hoặc lớp có quy tắc riêng. | Xác minh hợp đồng `equals` của kiểu dữ liệu cụ thể. |

Checklist trước khi triển khai clone:

1. Instance cần clone có implements `Cloneable` theo đúng cơ chế `Object.clone` không?
2. API clone có phạm vi truy cập và kiểu trả về phù hợp cho caller không?
3. Đối tượng mới có đúng runtime type và các giá trị trường thiết yếu không?
4. Có những trường tham chiếu mutable, mảng đa chiều, collection hoặc object con nào đang được chia sẻ?
5. Trường `final` hoặc bất biến constructor nào khiến việc sửa bản clone sau đó không phù hợp?
6. Việc sao chép có bảo toàn liên kết nội bộ, alias và con trỏ ngược nếu mô hình cần không?
7. Có tài nguyên không nên copy như stream, socket, lock, native handle hay state bên ngoài không?
8. Nếu bản sao được dùng trong collection dựa trên hash, các trường tham gia `equals/hashCode` có ổn định và đúng hợp đồng không?
9. Thao tác sao chép có thể chạy đồng thời với việc sửa đối tượng gốc không? Nếu có, đã thiết kế snapshot nhất quán chưa?
10. Một copy constructor hoặc factory có truyền đạt ý nghĩa sao chép rõ ràng và dễ duy trì hơn không?

### Bài tập tự kiểm tra

1. Sau `B b = (B) a.clone()`, vì sao `a != b` nhưng `a.list == b.list` vẫn có thể là `true`?
2. Nếu clone chứa `int age` và `List<String> tags`, việc sửa `age` và thêm phần tử vào `tags` có gì khác nhau?
3. Tại sao `Cloneable` không có phương thức `clone` để gọi trực tiếp bằng biến kiểu `Cloneable`?
4. Vì sao `new ArrayList<>(tags)` chưa phải deep copy nếu `tags` chứa các đối tượng mutable?
5. Nếu clone một `int[][]` chỉ bằng `source.clone()`, những gì đã độc lập và những gì vẫn dùng chung?
6. Vì sao `final List<String>` khó xử lý khi muốn tách list bằng cách gán lại sau `super.clone()`?
7. Vì sao cloning một object có socket hoặc input stream cần chính sách ownership đặc biệt?
8. Một lớp có thể không hỗ trợ clone nhưng vẫn cung cấp phương thức `copy()` hợp lệ không? Hãy nêu ví dụ cách làm.

**Ghi nhớ:** Khi đi tới `Object.clone()`, `super.clone()` tạo **instance mới** và **sao chép nông các trường**: dữ liệu nguyên thủy được chép, còn tham chiếu vẫn trỏ tới cùng object con. Muốn bản sao có trạng thái mutable độc lập, phải tách các thành phần cần thiết hoặc tạo bản mới bằng copy constructor/factory với hợp đồng rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="clone-contract-006">Hàm khởi tạo thông thường không được chạy bởi Object.clone()</a>

<details>
<summary>Xem chi tiết</summary>

# Object.clone() không gọi constructor thông thường

## 1. Cơ chế quan trọng nhất của Object.clone()

Khi tạo đối tượng bằng `new`, Java chạy chuỗi khởi tạo đối tượng theo quy tắc của ngôn ngữ: gọi constructor thích hợp, thực hiện quá trình khởi tạo instance, và xử lý các constructor trong chuỗi kế thừa. Ngược lại, **`java.lang.Object.clone()` tạo một đối tượng mới nhưng không gọi constructor thông thường của lớp đối tượng được sao chép**. Các instance field initializer và instance initializer block cũng không được chạy lại cho bản sao do chính `Object.clone()` tạo ra.

Thay vì chạy lại logic khởi tạo, `Object.clone()` tạo đối tượng mới cùng lớp runtime và **sao chép giá trị các trường** của đối tượng gốc như bằng phép gán. Các giá trị nguyên thủy được sao chép; các trường tham chiếu được sao chép **tham chiếu**, chứ không tự tạo bản sao của object con.

Điểm khác nhau có thể nhìn qua bảng:

| Hành động | Chạy constructor của đối tượng mới? | Xử lý các trường thế nào? |
| --- | --- | --- |
| `new Example(...)` | Có | Theo các initializer và constructor của class |
| `super.clone()` đi đến `Object.clone()` | **Không** | Sao chép giá trị field từ object gốc sang object mới |
| Constructor sao chép `new Example(other)` | **Có** | Theo logic lập trình viên viết trong constructor |
| Một override `clone()` tự gọi `new Example(...)` | **Có**, vì override gọi `new` | Do chính implementation của override quyết định |

Cần đọc chính xác câu “constructor không chạy”: nó nói về **bản sao do `Object.clone()` thực sự tạo ra**. `clone()` là phương thức có thể được override; một implementation tùy biến hoàn toàn có thể gọi constructor một cách tường minh. Không được suy ra “mọi phương thức có tên `clone` trong mọi thư viện đều không bao giờ gọi constructor”.

## 2. Vì sao Java không khởi tạo lại đối tượng từ đầu?

Javadoc `Object.clone()` mô tả thao tác mặc định bằng ý tưởng:

1. Tạo một instance mới thuộc **cùng lớp runtime** với đối tượng gốc.
2. Sao chép **nội dung từng field** từ đối tượng gốc sang instance mới, tương đương phép gán.
3. Không sao chép đệ quy các object được field tham chiếu: đây là **shallow copy**.
4. Trả về đối tượng mới cho caller.

Ví dụ một object có các trường:

```java
int id = 42;
String label = "A";
int[] scores = {10, 20};
```

Khi cloning bằng `Object.clone()`, bản sao nhận `id = 42`, nhận tham chiếu đến `String` đang được giữ và nhận **cùng tham chiếu** đến mảng `scores`. Không có constructor nào tự tạo lại một mảng mới cho bản sao, dù constructor của class có logic chuẩn hóa hoặc defensive copy mảng.

Vì thế, nếu constructor có lệnh tăng bộ đếm, ghi log, đăng ký object với nơi quản lý, mở kết nối hoặc cấp tài nguyên, những side effect đó **không tự xảy ra lần thứ hai** khi `Object.clone()` sao chép object. Ta cần thiết kế riêng nếu bản sao thực sự phải được đăng ký/cấp tài nguyên độc lập.

Điều này không có nghĩa các **static initializer** là quy trình cloning. Class phải được khởi tạo theo quy tắc chung trước khi các thao tác tương ứng được thực hiện; `Object.clone()` không chạy lại static initializer, constructor hoặc instance initializer cho bản sao. Không nên dùng bộ đếm constructor làm số lượng instance tuyệt đối nếu class cho phép clone.

## 3. Java 17: chứng minh constructor và instance initializer không chạy lại

Ví dụ dưới đây có hai bộ đếm: một cho **instance initializer block**, một cho **constructor**. Cả hai đều tăng khi dùng `new`, nhưng không tăng khi gọi `clone()` kế thừa hành vi của `Object.clone()`.

```java
public class CloneConstructorDemo {
    static final class TrackedItem implements Cloneable {
        static int initializerRuns;
        static int constructorCalls;

        final int id;
        final int[] scores;

        {
            initializerRuns++;
        }

        TrackedItem(int id, int[] input) {
            constructorCalls++;
            this.id = id;
            this.scores = input.clone();
        }

        @Override
        public TrackedItem clone() {
            try {
                return (TrackedItem) super.clone();
            } catch (CloneNotSupportedException ex) {
                throw new AssertionError(ex);
            }
        }
    }

    public static void main(String[] args) {
        TrackedItem original = new TrackedItem(7, new int[] {10, 20});
        System.out.println(TrackedItem.initializerRuns);
        System.out.println(TrackedItem.constructorCalls);

        TrackedItem copy = original.clone();
        System.out.println(TrackedItem.initializerRuns);
        System.out.println(TrackedItem.constructorCalls);

        System.out.println(original != copy);
        System.out.println(original.id == copy.id);
        System.out.println(original.scores == copy.scores);

        copy.scores[0] = 99;
        System.out.println(original.scores[0]);
    }
}
```

Lưu tệp `CloneConstructorDemo.java` rồi chạy:

```bash
javac CloneConstructorDemo.java
java CloneConstructorDemo
```

Đầu ra chính xác:

```text
1
1
1
1
true
true
true
99
```

Giải thích:

1. `new TrackedItem(...)` chạy instance initializer và constructor, nên hai bộ đếm lần lượt đều bằng `1`.
2. `original.clone()` gọi `super.clone()` và đi đến `Object.clone()`. Sau cloning, hai bộ đếm **vẫn bằng `1`**: không có initializer hoặc constructor thông thường nào được chạy lại cho bản sao.
3. `original != copy` là `true`: có một object mới.
4. `original.id == copy.id` là `true`: giá trị `int` được sao chép.
5. `original.scores == copy.scores` là `true`: trường `scores` của cả hai object trỏ đến **cùng mảng**.
6. Sửa `copy.scores[0]` làm `original.scores[0]` đổi thành `99`. Đây là hậu quả của **shallow copy**, không phải chạy constructor lần thứ hai.

Constructor đã clone mảng đầu vào để bảo vệ object gốc khỏi mutation qua tham số `input`. Nhưng `Object.clone()` **không chạy lại** phần `input.clone()` đó, nên bản sao vẫn chia sẻ mảng nội bộ của object gốc.

## 4. Cloneable và quyền truy cập Object.clone()

`Object.clone()` có phạm vi truy cập `protected` và khai báo `throws CloneNotSupportedException`. Không phải bất kỳ object nào cũng có thể được gọi `clone()` công khai từ code ngoài class.

`java.lang.Cloneable` là **marker interface**: nó không khai báo phương thức `clone`. Nó chỉ đánh dấu rằng các instance của class được phép clone bằng cơ chế `Object.clone()`. Nếu đối tượng không triển khai `Cloneable`, việc gọi đến `Object.clone()` sẽ phát sinh `CloneNotSupportedException`. **Mảng Java** là ngoại lệ tiện dụng: các kiểu mảng triển khai `Cloneable` và có thao tác `clone()` public.

Đối với class hỗ trợ cloning, mẫu thường gặp là:

```java
@Override
public MyType clone() {
    try {
        return (MyType) super.clone();
    } catch (CloneNotSupportedException ex) {
        throw new AssertionError(ex);
    }
}
```

Đây là đoạn minh họa **bên trong class `MyType implements Cloneable`**, không phải tệp độc lập. Nó công khai phương thức `clone`, sử dụng kiểu trả về cụ thể và chuyển checked exception thành lỗi không mong đợi khi class đã cam kết hỗ trợ clone.

**Không được nghĩ chỉ cần `implements Cloneable` thì tự nhiên class có một phương thức `clone()` public.** Interface ấy không khai báo phương thức nào; nếu muốn code ngoài gọi được, cần API public phù hợp.

Ví dụ minh họa lỗi khi chưa thực hiện `Cloneable`:

```java
public class CloneableRequirementDemo {
    static final class NoCloneSupport {
        @Override
        public NoCloneSupport clone() throws CloneNotSupportedException {
            return (NoCloneSupport) super.clone();
        }
    }

    public static void main(String[] args) {
        try {
            new NoCloneSupport().clone();
            System.out.println("Unexpected success");
        } catch (CloneNotSupportedException ex) {
            System.out.println("CloneNotSupportedException");
        }
    }
}
```

Chạy `javac CloneableRequirementDemo.java` và `java CloneableRequirementDemo`:

```text
CloneNotSupportedException
```

Ở đây phương thức `clone()` là public nên gọi được, nhưng instance không phải `Cloneable`. Do đó `Object.clone()` từ chối cloning. Một implementation tự tạo object bằng `new` có thể chọn hành vi khác, nhưng đó là quyết định riêng của implementation, không phải cơ chế mặc định của `Object.clone()`.

## 5. Object.clone() sao chép trường final như thế nào?

Khi constructor tạo một object, nó thường gán các field `final` trong quá trình khởi tạo. Trong ví dụ đầu, `id` và `scores` đều là `final`.

Khi `Object.clone()` tạo bản sao, field `final` cũng **nhận giá trị được sao chép** từ object gốc. Điều đó không có nghĩa constructor được gọi ngầm, cũng không có nghĩa code Java của class có thể gán lại field `final` sau khi clone.

- Với `final int id`, bản sao giữ cùng số nguyên `id`.
- Với `final int[] scores`, bản sao giữ **cùng tham chiếu mảng**, dù mảng đó vẫn mutable.
- Nếu code muốn shallow clone trước rồi thay `scores` bằng một mảng khác để thành deep copy, thông thường **không thể gán lại** field `final` trong phương thức `clone()` như đối với field thông thường.

Hãy phân biệt tính **final của biến tham chiếu** và tính **bất biến của object được tham chiếu**. `final` không biến `int[]` thành mảng không thể sửa.

Nếu kiểu dữ liệu thật sự cần field `final` và defensive copy sâu, **copy constructor hoặc static factory** thường làm ý định rõ hơn việc cố điều chỉnh field `final` sau `super.clone()`.

## 6. Shallow copy không giống deep copy

`Object.clone()` mặc định sao chép giá trị của field ở **một lớp đối tượng**, không duyệt toàn bộ object graph.

| Dạng trường | Sau `Object.clone()` | Nguy cơ |
| --- | --- | --- |
| `int`, `boolean` và primitive khác | Giá trị được sao chép | Không có shared mutable object từ riêng primitive |
| `String` | Sao chép tham chiếu | Thường chấp nhận được vì `String` bất biến |
| `int[]` | Sao chép tham chiếu | Hai object chia sẻ mảng mutable |
| `ArrayList<?>` | Sao chép tham chiếu | Hai object chia sẻ cùng danh sách |
| Object con mutable | Sao chép tham chiếu | Sửa object con có thể ảnh hưởng cả hai |
| Object con bất biến | Sao chép tham chiếu | Thường có thể chia sẻ an toàn theo hợp đồng của kiểu |

“Deep copy” không phải một thao tác duy nhất mà `Object.clone()` mặc định thực hiện. Với object graph phức tạp, cần quyết định phần nào phải độc lập, phần nào được chia sẻ, xử lý vòng tham chiếu và tính chất tài nguyên bên ngoài ra sao.

Một bản sao sâu cho trường `int[]` có thể đơn giản là `array.clone()`. Nhưng với `List<MutableThing>`, chỉ `new ArrayList<>(list)` sao chép cấu trúc danh sách, **chưa sao chép các object phần tử**.

### Override clone để sao chép thêm mảng mutable

Ví dụ sau sử dụng field mảng không final để minh họa cách override `clone()` chủ động tạo bản sao độc lập cho mảng.

```java
public class DeepCopyCloneDemo {
    static final class Sample implements Cloneable {
        private int[] values;

        Sample(int[] values) {
            this.values = values.clone();
        }

        @Override
        public Sample clone() {
            try {
                Sample copied = (Sample) super.clone();
                copied.values = values.clone();
                return copied;
            } catch (CloneNotSupportedException ex) {
                throw new AssertionError(ex);
            }
        }

        int first() {
            return values[0];
        }

        void changeFirst(int number) {
            values[0] = number;
        }

        boolean sharesArrayWith(Sample other) {
            return values == other.values;
        }
    }

    public static void main(String[] args) {
        Sample original = new Sample(new int[] {4, 5});
        Sample copied = original.clone();

        System.out.println(original != copied);
        System.out.println(original.sharesArrayWith(copied));

        copied.changeFirst(99);
        System.out.println(original.first());
        System.out.println(copied.first());
    }
}
```

Lưu thành `DeepCopyCloneDemo.java` và chạy:

```bash
javac DeepCopyCloneDemo.java
java DeepCopyCloneDemo
```

Đầu ra:

```text
true
false
4
99
```

Ở đây `super.clone()` vẫn **không gọi constructor**. Sau đó code trong override tạo một mảng mới bằng `values.clone()` và gán nó vào field mutable của bản sao. Vì vậy hai object độc lập về mảng.

Không suy luận rằng “override `clone` luôn tạo deep copy”: ví dụ này chỉ làm sâu thêm đúng **một trường mảng nguyên thủy một chiều**. Với cấu trúc lồng nhau, phần tử mutable hoặc tài nguyên đặc biệt, phải xét thêm các mối liên hệ.

## 7. Copy constructor: một lựa chọn có constructor thật sự

Nếu muốn chủ động chạy logic khởi tạo và kiểm soát các trường `final`, có thể thiết kế **copy constructor**. Nó không phải `Object.clone()`, mà là một lời gọi `new` thông thường:

```java
public class CopyConstructorDemo {
    static final class Snapshot {
        static int constructorCalls;
        final int[] samples;

        Snapshot(int[] samples) {
            constructorCalls++;
            this.samples = samples.clone();
        }

        Snapshot(Snapshot source) {
            this(source.samples);
        }
    }

    public static void main(String[] args) {
        Snapshot first = new Snapshot(new int[] {1, 2});
        Snapshot second = new Snapshot(first);

        System.out.println(Snapshot.constructorCalls);
        System.out.println(first != second);
        System.out.println(first.samples != second.samples);

        second.samples[0] = 9;
        System.out.println(first.samples[0]);
        System.out.println(second.samples[0]);
    }
}
```

Lưu `CopyConstructorDemo.java` và chạy:

```bash
javac CopyConstructorDemo.java
java CopyConstructorDemo
```

Kết quả:

```text
2
true
true
1
9
```

Copy constructor gọi constructor khác bằng `this(source.samples)`, và logic `samples.clone()` thực sự chạy để giữ mảng riêng. Vì dùng `new`, bộ đếm constructor tăng hai lần cho hai object. Với `Object.clone()` ở ví dụ đầu, chỉ constructor của object ban đầu được gọi.

Ưu điểm của cách này là quy trình tạo bản sao được thể hiện rõ: chọn field nào copy, dữ liệu nào chuẩn hóa, và cách gán field `final`. Nhược điểm là cần định nghĩa chính sách cho các lớp kế thừa hoặc runtime subtype nếu class có khả năng được subclass; bản sao do copy constructor của một class không tự bảo đảm giữ nguyên lớp runtime của object con.

## 8. Mảng Java có clone() riêng

Mọi mảng Java hỗ trợ `clone()` và trả về mảng **mới** cùng kiểu, cùng độ dài, sao chép các phần tử cấp đầu tiên. Đây là trường hợp đặc biệt dễ dùng, không yêu cầu lập trình viên tự viết `implements Cloneable`.

```java
public class ArrayCloneDemo {
    public static void main(String[] args) {
        int[] source = {1, 2};
        int[] copied = source.clone();

        System.out.println(source != copied);
        System.out.println(source[0] == copied[0]);

        copied[0] = 8;
        System.out.println(source[0]);

        int[][] matrix = {{1, 2}, {3, 4}};
        int[][] outerCopy = matrix.clone();

        System.out.println(matrix != outerCopy);
        System.out.println(matrix[0] == outerCopy[0]);

        outerCopy[0][0] = 99;
        System.out.println(matrix[0][0]);
    }
}
```

Lưu `ArrayCloneDemo.java` và chạy:

```bash
javac ArrayCloneDemo.java
java ArrayCloneDemo
```

Đầu ra:

```text
true
true
1
true
true
99
```

Một mảng `int[]` có các phần tử nguyên thủy, nên clone của nó tạo bản sao đủ độc lập đối với những số nguyên bên trong. Một mảng `int[][]` lại có các phần tử cấp ngoài là các tham chiếu tới mảng con. Clone mảng ngoài chỉ sao chép những tham chiếu đó; mảng con vẫn được chia sẻ.

Đây là lý do **clone một mảng nhiều chiều không đồng nghĩa deep clone tất cả các chiều**.

## 9. Object.clone() và bất biến khởi tạo

Constructor thường giữ những trách nhiệm ngoài gán giá trị field:

- Kiểm tra tham số `null`, giới hạn hoặc định dạng dữ liệu.
- Chuẩn hóa, sao chép phòng vệ các input mutable.
- Tăng bộ đếm, ghi log hoặc ghi nhận lần tạo đối tượng.
- Gán những field phái sinh từ các tham số đầu vào.
- Đăng ký object với các thành phần khác hoặc cấp tài nguyên.

Vì `Object.clone()` không chạy constructor, **không công việc nào ở trên được tự động làm lại**. Giá trị field đã có được sao chép, nhưng các side effect khởi tạo độc lập không xuất hiện lần thứ hai.

Điều này tạo ra những câu hỏi thiết kế:

1. Nếu object có file handle hoặc socket, bản sao cần chia sẻ tài nguyên, giữ tham chiếu, hay không nên được clone?
2. Nếu constructor đăng ký object vào một registry, clone có cần đăng ký riêng hay bị cấm?
3. Nếu field là mảng mutable được constructor sao chép để phòng vệ, clone có cần thêm một lần copy để tránh aliasing không?
4. Nếu một “ID duy nhất” được constructor sinh ra, bản sao nên giữ cùng ID hay cần ID mới? Câu trả lời phụ thuộc ngữ nghĩa *entity* hay *value*.
5. Nếu một lớp cha định nghĩa `clone()` và lớp con thêm trường mutable, lớp con đã thực hiện deep copy đúng phần trạng thái mới chưa?

Không mặc định rằng việc bỏ qua constructor làm tất cả object clone trở nên không hợp lệ. Nếu object gốc có trạng thái hợp lệ và bản sao chỉ cần cùng giá trị field, shallow clone có thể đáp ứng mục tiêu. **Vấn đề là trách nhiệm thiết kế của constructor không tự được tái thực hiện.**

Không nên dùng constructor counter làm “đếm chính xác mọi instance đang tồn tại” nếu đường tạo instance bao gồm `Object.clone()`. Cũng không nên dùng cloning để lách quy tắc khởi tạo/kiểm tra bắt buộc của ứng dụng.

## 10. Phân biệt yêu cầu chính thức, khuyến nghị và chi tiết triển khai

| Phát biểu | Phân loại | Vì sao |
| --- | --- | --- |
| “`Object.clone()` tạo instance mới và sao chép giá trị field từ object gốc.” | **Hợp đồng của phương thức** | Được API mô tả trực tiếp |
| “`Object.clone()` không chạy constructor thông thường cho bản sao.” | **Hành vi của cơ chế mặc định** | Sao chép field thay vì khởi tạo qua constructor |
| “Clone mặc định sao chép sâu mọi object được tham chiếu.” | **Sai** | Clone mặc định là shallow copy |
| “Class muốn dùng `Object.clone()` cần thực hiện `Cloneable`; nếu không, phát sinh `CloneNotSupportedException`.” | **Hợp đồng của cơ chế mặc định** | `Cloneable` là marker interface |
| “`Cloneable` cung cấp sẵn phương thức `clone()` public.” | **Sai** | Interface không khai báo `clone` |
| “Mọi override `clone()` đều không bao giờ gọi constructor.” | **Sai** | Override tùy biến có thể gọi `new` |
| “Sao chép object có field mutable nên cân nhắc defensive copy.” | **Khuyến nghị thiết kế** | Tránh chia sẻ trạng thái ngoài ý muốn |
| “Chỉ cần `super.clone()` luôn an toàn với socket, thread, lock, file handle.” | **Sai** | Các tài nguyên có hợp đồng riêng, không được sao chép tự động đúng ngữ nghĩa |
| “Nên dùng copy constructor/static factory khi cần khởi tạo rõ ràng hoặc field `final`.” | **Khuyến nghị** | Thường kiểm soát invariant và chính sách sao chép dễ hơn |
| “`x.clone().equals(x)` bắt buộc đúng với mọi class.” | **Không phải cam kết tuyệt đối** | Javadoc `Object.clone` trình bày đây là ý định thường gặp, không phải yêu cầu tuyệt đối |
| “`Object.clone()` luôn giữ thứ tự của classloader, cách cấp phát địa chỉ hoặc giá trị hash identity giống object cũ.” | **Không có cam kết như vậy** | Các chi tiết JVM không phải nội dung contract của clone |

Theo Javadoc `Object.clone()`, thường mong `x.clone() != x` và cùng class runtime; các nhận định như `x.clone().equals(x)` được trình bày với mức độ kỳ vọng, không phải nghĩa vụ tuyệt đối cho mọi implementation override `clone()`. Điều quan trọng hơn khi thiết kế API là công bố **chính sách sao chép** của chính class đó.

## 11. Lỗi phổ biến

**Lỗi 1 — nghĩ clone chạy lại constructor.** Dẫn đến dự đoán sai số lần initializer/constructor gọi, logic đăng ký object, mảng được copy hoặc ID được tạo. Hãy nhớ hành vi chuẩn của `Object.clone()` là sao chép field.

**Lỗi 2 — tưởng field final không được clone.** `Object.clone()` sao chép giá trị field final; nó không cần gọi constructor để gán lại từ source code. Tuy nhiên, field final chứa tham chiếu mutable có thể tiếp tục chia sẻ object bên trong.

**Lỗi 3 — tin rằng implements Cloneable đủ để có API clone public.** `Cloneable` chỉ là marker; class cần tự công bố phương thức clone nếu caller ngoài class cần dùng.

**Lỗi 4 — trả `super.clone()` rồi cho rằng đó là deep copy.** Tất cả object con được tham chiếu vẫn có thể chia sẻ. Với `List`, mảng, object mutable và tài nguyên ngoài, phải chỉ rõ chính sách.

**Lỗi 5 — clone một mảng hai chiều rồi tưởng sao chép cả các hàng.** `matrix.clone()` chỉ sao chép mảng ngoài, các mảng con vẫn cùng tham chiếu.

**Lỗi 6 — sao chép tài nguyên mà không kiểm tra vòng đời.** File handle, lock, stream hoặc socket có thể không có ngữ nghĩa sao chép phù hợp. Có kiểu nên cung cấp một factory tạo tài nguyên mới; có kiểu nên cấm cloning.

**Lỗi 7 — dùng cloning để tạo entity mới nhưng vô tình giữ ID gốc.** Nếu ID phải duy nhất, việc giữ lại trường định danh bằng shallow clone có thể trái yêu cầu nghiệp vụ, dù cơ chế `Object.clone` hoạt động đúng như API công bố.

## 12. Câu hỏi tự luyện

**Câu 1.** Constructor tăng `static int created` mỗi khi chạy. Sau một lần `new Item()` rồi một lần `super.clone()` tạo bản sao, `created` mặc định tăng mấy lần?

**Đáp án:** Một lần, nếu không có override tự tăng bộ đếm hay gọi thêm constructor. `Object.clone()` không chạy constructor thông thường cho bản sao.

**Câu 2.** Một field `final int[] data` có trong object gốc. Sau `Object.clone()`, hai object giữ hai mảng độc lập không?

**Đáp án:** Không. Field final được sao chép ở mức **tham chiếu**, nên mặc định chúng cùng trỏ đến một mảng. Final không làm phần tử mảng bất biến.

**Câu 3.** Class triển khai `Cloneable` nhưng không công bố public `clone()`. Code bên ngoài có chắc gọi được `instance.clone()` không?

**Đáp án:** Không. `Object.clone` là `protected` và `Cloneable` không khai báo phương thức public.

**Câu 4.** Trong một override `clone()`, tác giả viết `return new MyType(this);`. Constructor có chạy không?

**Đáp án:** Có. Đó là lời gọi `new` trong implementation tùy biến; nó không phải hành vi tạo object của `Object.clone()`.

**Câu 5.** Tại sao không nên dùng shallow clone như giải pháp chung cho object đang sở hữu socket?

**Đáp án:** Bản sao có thể chia sẻ tham chiếu tài nguyên và trạng thái quản lý vòng đời. Sao chép giá trị field không tự tạo một socket độc lập hay đồng bộ các quy tắc đóng/mở.

**Câu 6.** `int[][] copied = matrix.clone();` có bảo đảm `matrix[0] != copied[0]` không?

**Đáp án:** Không. Clone mảng ngoài sao chép tham chiếu tới các mảng con. Hai hàng tương ứng vẫn có thể là cùng một object.

## 13. Ghi nhớ

- **`Object.clone()` không gọi constructor thông thường hay instance initializer của bản sao.** Nó tạo instance mới và sao chép giá trị field.
- **Shallow copy:** primitive được sao chép theo giá trị; trường tham chiếu giữ cùng object được trỏ đến, trừ khi override chủ động sao chép thêm.
- `Cloneable` là marker interface; `Object.clone` là `protected` và có thể ném `CloneNotSupportedException`.
- Field `final` vẫn được sao chép bởi cơ chế clone; final reference không tương đương deep immutability.
- Override `clone()` **có thể** tự gọi constructor, factory hoặc logic deep copy; phải đọc implementation cụ thể.
- Mảng có `clone()` riêng; `int[]` copy các số độc lập, `int[][]` chỉ copy mảng ngoài.
- Dùng **copy constructor** hoặc factory khi cần kiểm soát khởi tạo và bất biến rõ ràng, nhất là với object có field final hay tài nguyên khó sao chép.
- Khi xây API sao chép, hãy công bố rõ field nào chia sẻ, field nào độc lập, và cách xử lý ID, registry cùng tài nguyên.

Đối chiếu tài liệu chính thức: [`Object.clone()` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#clone()), [`Cloneable` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Cloneable.html), [Java Language Specification — Arrays](https://docs.oracle.com/javase/specs/jls/se17/html/jls-10.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="clone-contract-007">Quy ước bản sao khác tham chiếu, cùng lớp và thường cùng giá trị</a>

<details>
<summary>Xem chi tiết</summary>

# Quy ước bản sao khác tham chiếu, cùng lớp và thường cùng giá trị trong Java

Khi nói một object được **clone** trong Java, ta thường mong muốn một **đối tượng mới** có cùng kiểu runtime, mang dữ liệu ban đầu tương ứng với object gốc. Hợp đồng `Object.clone()` mô tả ba quy ước nổi bật:

~~~java
x.clone() != x
x.clone().getClass() == x.getClass()
x.clone().equals(x)
~~~

Ba dòng trên là các **biểu thức minh họa quy ước**, chỉ có thể gọi trực tiếp nếu phương thức `clone()` đã được lớp cung cấp ở phạm vi truy cập phù hợp. Chúng **không phải ba bảo đảm tuyệt đối của mọi phương thức mang tên `clone`**. JavaDoc của `Object.clone()` nêu đây là những điều nên đúng *theo quy ước*, và có thể có ngoại lệ, nhất là biểu thức thứ ba.

Vì thế cần phân biệt: **sao chép object** khác với **sao chép toàn bộ đồ thị object được tham chiếu**, còn **cùng dữ liệu** không luôn đồng nghĩa `equals() == true`.

## 1. Ba quy ước có ý nghĩa gì?

| Quy ước | Ý nghĩa | Giới hạn cần nhớ |
|---|---|---|
| `clone != original` | Clone thường là **instance mới**, khác tham chiếu với object gốc | Một phương thức clone tự viết có thể làm sai quy ước nếu trả chính `this` |
| `clone.getClass() == original.getClass()` | Bản sao thường có **đúng cùng lớp runtime** | Đúng với đường sao chép thông thường qua `super.clone()`; override tự chế có thể trả lớp khác |
| `clone.equals(original)` | Bản sao thường bằng bản gốc **theo định nghĩa equality của lớp** | Không bảo đảm nếu lớp không override `equals()` hoặc nếu clone cố tình đổi trường dùng để so sánh |

Ví dụ, nếu lớp `Customer` có `id = 7`, bản clone cũng có `id = 7` và lớp định nghĩa `equals()` theo `id`, thì `customer.clone().equals(customer)` thường là `true`. Nếu cùng lớp đó thừa hưởng `Object.equals()` theo **identity**, hai instance khác nhau sẽ cho `false` dù có các trường trùng nhau.

Đừng gọi ba biểu thức trên là **luật kiểm tra bắt buộc** đối với tất cả lớp Java. Đó là **quy ước thiết kế và tiêu chí kiểm thử hữu ích** khi một API hứa cung cấp bản sao theo nghĩa thông thường.

## 2. `Object.clone()` và `Cloneable` hoạt động như thế nào?

`Object` khai báo phương thức **`protected Object clone() throws CloneNotSupportedException`**. Vì là `protected`, mã bên ngoài không thể tùy ý viết `someObject.clone()` nếu lớp không công khai phương thức phù hợp.

`java.lang.Cloneable` là một **marker interface**: nó **không khai báo phương thức `clone()`**. Khi gọi cơ chế `Object.clone()` trên instance của lớp không triển khai `Cloneable` (và không phải mảng), cơ chế mặc định ném `CloneNotSupportedException`. Việc viết `implements Cloneable` một mình **không tự tạo một phương thức `public clone()`** cho người dùng bên ngoài.

Mẫu triển khai phổ biến:

~~~java
final class Item implements Cloneable {
    private final int id;

    Item(int id) {
        this.id = id;
    }

    @Override
    public Item clone() {
        try {
            return (Item) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}
~~~

Đây là khai báo lớp có thể biên dịch, không có `main`. Vì `Item` là `final` và triển khai `Cloneable`, lệnh `super.clone()` theo thiết kế này không phát sinh `CloneNotSupportedException`; bọc lỗi thành `AssertionError` là một lựa chọn thể hiện bất biến đó. Lớp dùng **kiểu trả về hiệp biến** (`Item` thay vì `Object`), đồng thời mở rộng mức truy cập lên `public`.

Cơ chế `super.clone()` tạo một object mới với các trường được sao chép từ object hiện tại; nó **không gọi constructor của lớp được clone** như cách `new Item(...)` thường làm. Tuy nhiên, gọi `super.clone()` cũng **không tự sao chép sâu** các object nằm trong trường tham chiếu.

## 3. Khác tham chiếu: bản clone là object mới

Với một triển khai tuân quy ước, hai biến có thể chứa thông tin giống nhau nhưng không trỏ cùng một instance:

~~~java
Item original = new Item(7);
Item copied = original.clone();

System.out.println(original == copied); // false
System.out.println(original != copied); // true
System.out.println(original.getClass() == copied.getClass()); // true
~~~

Đây là snippet sử dụng lớp `Item` ở mục trên và có thể đặt trong `main`. `==` với tham chiếu kiểm tra **identity**, không kiểm tra dữ liệu. Nếu hai biến cùng trỏ một instance, thay đổi trạng thái qua biến này tác động lên cùng object mà biến kia nhìn thấy. Nếu chúng khác instance, việc gán lại trường trực tiếp của bản này không tự thay đổi trường trực tiếp của bản kia.

Nhưng cần phân biệt **hai object gốc/clone khác nhau** với **các object con được chúng tham chiếu**: cả bản gốc và clone vẫn có thể cùng giữ tham chiếu đến một `List`, một array hoặc một object mutable. Đó là sao chép nông (*shallow copy*), phân tích ở phần dưới.

Một override sai có thể viết `return this;` và làm `clone != original` thất bại. Trình biên dịch Java không tự chặn kiểu triển khai đó; trách nhiệm bảo vệ hợp đồng thuộc người viết API.

## 4. Cùng lớp runtime: `getClass()`, không phải tên kiểu biến

Với `Object.clone()` thông thường, object được tạo có **cùng lớp runtime** với object nhận lời gọi. Nếu một biến được khai báo kiểu lớp cha nhưng trỏ tới instance lớp con, việc clone qua phương thức kế thừa có thể trả một object với lớp runtime **của lớp con**.

~~~java
Object a = new Item(9);
Item b = ((Item) a).clone();

System.out.println(a.getClass() == b.getClass()); // true
~~~

Ở ví dụ này biến `a` được khai báo `Object`, nhưng instance thực tế là `Item`. Quy ước cùng lớp xét `getClass()` của **object runtime**, không xét tên kiểu khai báo của biến, cũng không chỉ dùng `instanceof`: `instanceof` chấp nhận cả lớp con, còn phép so `getClass() == getClass()` yêu cầu đúng lớp runtime.

Khi lớp có kế thừa, `super.clone()` thường bảo toàn kiểu runtime vì clone tạo một đối tượng mới cùng kiểu thực tế của receiver. Nhưng triển khai lớp con phải cẩn thận sao chép các trường mutable do nó bổ sung; chỉ dựa vào `super.clone()` có thể để lại tham chiếu dùng chung.

Một phương thức tự viết dưới tên `clone` nhưng tạo `new Base(...)` khi receiver là `Child` có thể phá quy ước cùng lớp. Đây là một lý do không nên giả định mọi override tuân hợp đồng chỉ từ tên phương thức.

## 5. Thường cùng giá trị: vì sao `equals()` có thể vẫn false?

Cần tách **trạng thái được sao chép** khỏi **quy tắc `equals()`**:

- Nếu lớp định nghĩa `equals()` theo các trường giá trị và clone giữ các trường tương ứng, `clone.equals(original)` thường `true`.
- Nếu lớp **không override `equals()`**, mặc định của `Object.equals()` là identity. Clone là instance mới nên `clone.equals(original)` là `false`.
- Nếu `equals()` phụ thuộc dữ liệu mà clone cố ý thay đổi (ví dụ đặt lại mã định danh), `equals()` có thể `false` ngay cả khi bản sao hợp lý cho nghiệp vụ.
- Nếu lớp định nghĩa `equals()` sai đối xứng/bắc cầu hoặc có mutable state thay đổi trong lúc so sánh, kết quả cũng khó tin cậy. Đây là vấn đề thiết kế equality, không chỉ là vấn đề clone.

Ví dụ một lớp có clone nhưng vẫn dùng equality identity:

~~~java
final class IdentityItem implements Cloneable {
    int value;

    IdentityItem(int value) {
        this.value = value;
    }

    @Override
    public IdentityItem clone() {
        try {
            return (IdentityItem) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}
~~~

Với hai biến `IdentityItem a = new IdentityItem(5);` và `IdentityItem b = a.clone();`, ta có `a != b` là `true`, `a.value == b.value` là `true`, nhưng `a.equals(b)` là `false`. Lớp này **không vi phạm quy tắc bắt buộc của `equals()`**; nó chỉ không đạt quy ước clone “thường equals” vì equality mặc định là identity.

Nếu muốn một **value object**, hãy định nghĩa `equals()` cùng `hashCode()` trên trường giá trị ổn định; khi dùng làm khóa trong `HashSet`/`HashMap`, tránh thay đổi trường tham gia equality/hash sau khi thêm.

## 6. Sao chép nông và sao chép sâu: ý nghĩa thực tế của “bản sao”

`super.clone()` thực hiện sao chép trường theo cơ chế mặc định (*field-by-field copy*). Với trường primitive như `int`, giá trị được sao chép. Với trường tham chiếu như `int[] scores`, **giá trị tham chiếu** được sao chép: object gốc và clone có thể trỏ cùng một array.

~~~text
Trước clone:
Original ----> scores [10, 20]

Sau super.clone() (shallow):
Original ----> scores [10, 20]
Clone    -----^

Sau copy thêm scores.clone() (tách array):
Original ----> scores [10, 20]
Clone    ----> scores [10, 20]
~~~

Đây là sơ đồ minh họa tham chiếu; hai dòng `[10, 20]` ở sơ đồ cuối đại diện cho **hai array khác nhau**, không phải cùng một array.

Nếu muốn clone có thể sửa array độc lập, cần sao chép trường array (chẳng hạn `copy.scores = scores.clone()`). Với cấu trúc phức tạp chứa nested arrays, list, map hoặc object mutable, việc sao chép một array/list bên ngoài **chưa đủ**: các phần tử tham chiếu bên trong có thể còn dùng chung, cần xem xét độ sâu phù hợp.

Thế nào là “deep copy” còn phụ thuộc hợp đồng nghiệp vụ: object bất biến như `String` có thể dùng chung an toàn, còn dữ liệu mutable thường cần được sao chép hoặc thay bằng mô hình bất biến. Không sao chép toàn bộ object graph vô điều kiện, đặc biệt khi có vòng tham chiếu, tài nguyên hệ thống hoặc identity được chia sẻ có chủ đích.

## 7. Chương trình Java 17 chạy được, có kết quả xác định

Chương trình sau kiểm tra trực tiếp ba quy ước, so sánh shallow/deep copy, xác minh constructor không được gọi khi clone, và thử lớp thiếu `Cloneable`.

Lưu file **`CloneContractDemo.java`**:

~~~java
import java.util.Arrays;
import java.util.Objects;

public class CloneContractDemo {
    static final class Document implements Cloneable {
        static int constructorCalls = 0;

        String title;
        int[] pages;

        Document(String title, int[] pages) {
            constructorCalls++;
            this.title = title;
            this.pages = pages;
        }

        @Override
        public Document clone() {
            try {
                return (Document) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }

        Document deepCopy() {
            Document result = clone();
            result.pages = pages.clone();
            return result;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Document d)) return false;
            return Objects.equals(title, d.title)
                    && Arrays.equals(pages, d.pages);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hashCode(title)
                    + Arrays.hashCode(pages);
        }
    }

    static final class IdentityItem implements Cloneable {
        final int value;

        IdentityItem(int value) {
            this.value = value;
        }

        @Override
        public IdentityItem clone() {
            try {
                return (IdentityItem) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
        // Không override equals(): dùng identity của Object.
    }

    static final class MissingMarker {
        Object attemptClone() throws CloneNotSupportedException {
            return super.clone();
        }
    }

    public static void main(String[] args) {
        Document original = new Document("Guide", new int[]{10, 20});
        Document shallow = original.clone();
        Document deep = original.deepCopy();

        System.out.println("clone different reference: "
                + (shallow != original));
        System.out.println("clone same class: "
                + (shallow.getClass() == original.getClass()));
        System.out.println("clone equals original: "
                + shallow.equals(original));
        System.out.println("shallow shares array: "
                + (shallow.pages == original.pages));
        System.out.println("deep different reference: "
                + (deep != original));
        System.out.println("deep same class: "
                + (deep.getClass() == original.getClass()));
        System.out.println("deep equals original: "
                + deep.equals(original));
        System.out.println("deep separate array: "
                + (deep.pages != original.pages));
        System.out.println("constructor calls: "
                + Document.constructorCalls);

        original.pages[0] = 99;
        System.out.println("original pages: "
                + Arrays.toString(original.pages));
        System.out.println("shallow pages: "
                + Arrays.toString(shallow.pages));
        System.out.println("deep pages: "
                + Arrays.toString(deep.pages));
        System.out.println("shallow still equals: "
                + original.equals(shallow));
        System.out.println("deep now equals original: "
                + original.equals(deep));

        IdentityItem identity = new IdentityItem(7);
        IdentityItem identityCopy = identity.clone();
        System.out.println("identity copy different: "
                + (identity != identityCopy));
        System.out.println("identity same field: "
                + (identity.value == identityCopy.value));
        System.out.println("identity equals: "
                + identity.equals(identityCopy));

        int[] array = {1, 2};
        int[] arrayCopy = array.clone();
        System.out.println("array clone different: "
                + (array != arrayCopy));
        System.out.println("array clone same class: "
                + (array.getClass() == arrayCopy.getClass()));
        System.out.println("array content equal: "
                + Arrays.equals(array, arrayCopy));

        try {
            new MissingMarker().attemptClone();
            System.out.println("missing marker: unexpected success");
        } catch (CloneNotSupportedException expected) {
            System.out.println("missing marker: CloneNotSupportedException");
        }
    }
}
~~~

Biên dịch và chạy bằng **JDK 17 trở lên**:

~~~bash
javac CloneContractDemo.java
java CloneContractDemo
~~~

Đầu ra:

~~~text
clone different reference: true
clone same class: true
clone equals original: true
shallow shares array: true
deep different reference: true
deep same class: true
deep equals original: true
deep separate array: true
constructor calls: 1
original pages: [99, 20]
shallow pages: [99, 20]
deep pages: [10, 20]
shallow still equals: true
deep now equals original: false
identity copy different: true
identity same field: true
identity equals: false
array clone different: true
array clone same class: true
array content equal: true
missing marker: CloneNotSupportedException
~~~

**Phân tích kết quả:**

1. `Document.clone()` tạo instance mới (`!=`) cùng runtime class (`getClass() ==`). Khi chưa thay đổi dữ liệu, bản clone bằng bản gốc vì `Document.equals()` được định nghĩa theo `title` và nội dung array.
2. `shallow.pages == original.pages` là `true`: hai Document khác nhau nhưng **cùng tham chiếu tới một array**. Chỉnh phần tử trong array ở bản gốc làm bản shallow nhìn thấy cùng thay đổi.
3. `deepCopy()` tạo clone trước rồi tách `pages` bằng `pages.clone()`; hai array khác nhau, ban đầu có cùng nội dung. Sau khi sửa array của bản gốc, bản deep giữ dữ liệu cũ và `equals` so với bản gốc trở thành `false`.
4. Bộ đếm constructor là 1 dù đã tạo thêm hai Document qua clone/deepCopy: `Object.clone()` không gọi constructor `Document`.
5. `IdentityItem` chứng minh **cùng trường không đồng nghĩa `equals == true`** khi dùng triển khai `Object.equals()` mặc định.
6. Array có `clone()` riêng, kết quả khác tham chiếu nhưng cùng runtime array class; với mảng primitive, các phần tử primitive được sao chép độc lập.
7. `MissingMarker` cho thấy phương thức `Object.clone()` ném `CloneNotSupportedException` khi lớp không implements `Cloneable`; ngoại lệ được kiểm tra thay vì để chương trình dừng.

**Giới hạn ví dụ:** `deepCopy()` chỉ sao chép sâu một tầng array primitive, vì mô hình `Document` ở đây chỉ có `String` (bất biến) và `int[]`. Với nested object mutable, cần chiến lược rõ ràng cho từng thành phần; gọi `clone()` một lần không tự tạo deep copy tổng quát.

## 8. Kế thừa và Cloneable: vì sao dễ thiết kế sai?

Một lớp cha có `public Parent clone()` dùng `super.clone()` cho phép lớp con kế thừa phương thức đó. Khi receiver thực tế là `Child`, bản sao do `Object.clone()` tạo ra vẫn có thể là **`Child` runtime**, dù kiểu trả về khai báo là `Parent`.

Tuy vậy, kế thừa làm phát sinh hai nguy cơ:

- **Lớp con thêm trường tham chiếu mutable:** `super.clone()` chỉ sao chép giá trị tham chiếu; nếu lớp con muốn độc lập, nó phải override và sao chép phần mutable được thêm.
- **Hợp đồng equality của các lớp khác nhau:** clone có thể cùng runtime class nhưng `equals()` của lớp cha/con có lỗi hoặc không có value equality. Vì vậy quy ước `clone.equals(original)` không được lấy làm chứng minh equality đã thiết kế đúng.

Nếu một superclass không được xây dựng để hỗ trợ clone, cố override tùy tiện trong subclass có thể gây vấn đề với trạng thái nội bộ và các bất biến của constructor. Đặc biệt cần xem lớp có trường `final` trỏ đến mutable object hay không: clone nông giữ chung tham chiếu, còn thiết kế sao chép sâu với trường `final` không thể chỉ gán lại một cách tùy ý trong clone.

**`Cloneable` là marker**, không phải giao diện định nghĩa `public clone()`; mã generic nhận `Cloneable` không tự gọi được `clone()` trên tham chiếu chỉ có kiểu `Cloneable`. Đây là một hạn chế quan trọng của cơ chế clone cũ.

## 9. Array, record và các lựa chọn sao chép khác

**Array** có thể `clone()` trực tiếp. Với `int[]`, phần tử primitive được chép; với `Person[]`, bản sao là array mới nhưng mỗi phần tử ban đầu vẫn trỏ đến **cùng Person**. Không nhầm `array.clone()` với deep copy của các phần tử object.

**Record** được Java khai báo là `final`, tự có `equals()`/`hashCode()` theo component, nhưng không có một phương thức `public clone()` tự sinh để sao chép record. Với record bất biến thích hợp, thường không cần clone: có thể dùng trực tiếp cùng giá trị hoặc tạo record mới từ các component nếu cần instance khác.

Nhiều thiết kế hiện đại ưu tiên **copy constructor** hoặc **static factory** vì nêu rõ chính sách sao chép:

~~~java
final class Settings {
    private final String name;
    private final int[] flags;

    Settings(String name, int[] flags) {
        this.name = name;
        this.flags = flags.clone();
    }

    Settings(Settings other) {
        this(other.name, other.flags);
    }
}
~~~

Đây là một lớp minh họa không có `main`. Constructor nhận `Settings` tạo object mới và tạo array mới. Trong thiết kế này, cả constructor chính cũng thực hiện **defensive copy** để tránh người gọi giữ tham chiếu tới `flags` mutable. Nếu cung cấp getter trả array, cũng cần cân nhắc trả bản sao để bảo vệ bất biến.

Các lựa chọn như copy constructor/factory phù hợp khi API cần hợp đồng rõ: sao chép nông hay sâu, có giữ ID hay tạo ID mới, trường mutable nào tách, có cho sao chép qua kế thừa không.

## 10. Những lỗi thường gặp

| Lỗi hoặc giả định | Vì sao sai | Hướng xử lý |
|---|---|---|
| `implements Cloneable` là tự có `public clone()` | Interface marker không khai báo phương thức | Viết phương thức clone công khai nếu API cần |
| Gọi `super.clone()` trên object không `Cloneable` | Cơ chế Object ném `CloneNotSupportedException` | Chỉ dùng với lớp thiết kế hỗ trợ clone, hoặc dùng copy constructor |
| Clone có cùng field nên chắc `equals == true` | `Object.equals` mặc định theo identity | Đọc/định nghĩa equality đúng ngữ nghĩa |
| `clone != original` nghĩa là không chia sẻ mutable state | Sao chép nông giữ nguyên tham chiếu ở trường | Sao chép thêm phần mutable cần độc lập |
| `clone.getClass() == original.getClass()` luôn được JVM cưỡng chế | Override tự viết có thể trả kiểu khác | Duy trì hợp đồng và kiểm thử runtime class |
| Thêm trường mutable ở subclass rồi chỉ kế thừa clone của cha | Mutable object mới có thể vẫn bị chia sẻ | Review và override clone ở subclass nếu cần |
| Nghĩ clone gọi constructor | `Object.clone()` sao chép trường, không gọi constructor của lớp đó | Bảo vệ bất biến; cân nhắc copy constructor |
| Dùng clone làm cơ chế duy nhất cho deep copy phức tạp | Nested references/vòng tham chiếu cần xử lý riêng | Quy định độ sâu, dùng factory hoặc sao chép có kiểm soát |
| Dùng `equals` sai hoặc `hashCode` không khớp | Hỏng collection hash nếu dùng object làm khóa | Giữ hợp đồng equality/hash, tránh mutable key |

## 11. Cách kiểm thử hợp đồng clone

Với `x` là một object dự kiến clone được, đừng chỉ kiểm tra nội dung bề ngoài. Nên kiểm thử các lớp quan hệ sau:

1. **Instance:** `clone != original` đúng theo hợp đồng công bố?
2. **Kiểu runtime:** `clone.getClass() == original.getClass()`? Nếu thiết kế cố ý trả kiểu khác, đã ghi rõ?
3. **Giá trị ban đầu:** trường primitive, tham chiếu bất biến và phần mutable có nội dung cần bảo toàn có được sao chép đúng không?
4. **Equality:** `clone.equals(original)` có đúng theo **chính định nghĩa equals của lớp** không? Nếu không, đó là chủ đích hay lỗi?
5. **Chia sẻ trạng thái:** sau khi sửa array/list mutable ở gốc, bản sao có bị thay đổi không? Điều đó có đúng với shallow/deep contract?
6. **Kế thừa:** clone một object runtime thuộc subclass rồi xác minh kiểu thực và những trường được subclass bổ sung.
7. **Ngoại lệ/truy cập:** lớp có cho phép clone không, quyền gọi là `public` hay `protected`, có cần xử lý `CloneNotSupportedException`?
8. **Bất biến và hash:** clone hay các thay đổi sau clone có phá invariant, equality hoặc cấu trúc collection băm không?

Không dùng ba quy ước như một câu thần chú để thay cho kiểm thử hành vi. Với một API sao chép mang mục tiêu khác (ví dụ tạo đơn hàng mới với ID mới), nên đặt tên và tài liệu rõ thay vì để người dùng hiểu nhầm là clone toàn bộ ý nghĩa giá trị.

## 12. Câu hỏi tự kiểm tra

**1.** `x.clone() != x` muốn bảo đảm điều gì?  
**Đáp án:** Bản sao là instance mới, không phải chỉ một alias của object gốc. Đây là quy ước thiết kế, không phải luật tuyệt đối cho mọi override.

**2.** Nếu clone có cùng class runtime nhưng `equals()` dùng `Object.equals()` mặc định, `clone.equals(original)` thường là gì?  
**Đáp án:** `false`, vì hai instance khác nhau về identity. “Cùng dữ liệu” không mặc nhiên là value equality.

**3.** `super.clone()` có tự clone mọi object được tham chiếu trong các trường không?  
**Đáp án:** Không. Nó tạo shallow copy; các trường tham chiếu mutable có thể dùng chung object con.

**4.** Vì sao `Cloneable` chưa đủ để gọi `x.clone()` từ mã bên ngoài?  
**Đáp án:** `Cloneable` không khai báo `public clone`; `Object.clone` là `protected`. Lớp phải cung cấp API sao chép có mức truy cập phù hợp.

**5.** Có thể dùng `int[] copy = source.clone()` để có array mới độc lập không?  
**Đáp án:** Có. Mảng mới khác tham chiếu và giá trị phần tử primitive được sao chép. Với `Object[]`, các tham chiếu đến phần tử object vẫn được chia sẻ nếu không sao chép thêm.

**6.** Khi nào nên chọn copy constructor thay vì clone?  
**Đáp án:** Khi cần diễn đạt công khai quy tắc sao chép, kiểm soát trường mutable/invariant hoặc xử lý mô hình có kế thừa phức tạp.

**Ghi nhớ:** Ba đặc điểm clone **khác tham chiếu, cùng lớp runtime và thường cùng giá trị** là các **quy ước mong đợi**. Để bản sao thực sự hữu ích, phải hiểu `equals()` của lớp và mức **độc lập của trạng thái mutable**, thay vì chỉ tin vào `super.clone()`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="clone-contract-008">Phân biệt quy ước sao chép với các bảo đảm tuyệt đối của API</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt quy ước sao chép với các bảo đảm tuyệt đối của API

## 1. Vấn đề: lời hứa nào thật sự tồn tại khi gọi clone()?

Trong Java, phương thức `clone()` thường được giới thiệu là cách tạo “một bản sao giống đối tượng ban đầu”. Cách nói này tiện để bắt đầu, nhưng rất dễ dẫn đến ba suy luận quá mạnh:

- `x.clone() != x` luôn đúng.
- `x.clone().getClass() == x.getClass()` luôn đúng.
- `x.clone().equals(x)` luôn đúng.

Đây là những **kỳ vọng hoặc quy ước thiết kế** được nhắc tới trong tài liệu của `Object.clone()`, chứ **không phải ba mệnh đề tuyệt đối áp dụng cho mọi phương thức `clone()` mà một lớp tự ghi đè**. Tài liệu Java mô tả “general intent” (ý định chung) cho hai kỳ vọng đầu và nhận xét rằng điều thứ ba “typically” (thường) đúng; những điều này không được yêu cầu tuyệt đối.

Để hiểu chính xác, phải tách ba tầng:

1. **Hợp đồng ngôn ngữ/API của `Object.clone()`**: cách phương thức gốc hoạt động khi được gọi hợp lệ.
2. **Quy ước để lớp con triển khai `clone()` tử tế**: phương thức public/covariant, gọi `super.clone()` khi phù hợp, trả về một bản sao có ý nghĩa và bảo vệ các invariant.
3. **Chính sách sao chép của từng lớp**: sao chép nông hay sâu, trường nào được chia sẻ, exception nào được công bố và liệu lớp có cung cấp cách sao chép khác hay không.

Không nên nhìn vào tên `clone()` rồi tự kết luận đối tượng trả về hoàn toàn độc lập, mọi trường đều tương đương, constructor được gọi lại, hay tất cả lớp Java đều hỗ trợ thao tác đó.

## 2. Phần được API Object.clone() định nghĩa

Chữ ký của phương thức gốc trong `java.lang.Object`:

```java
protected native Object clone() throws CloneNotSupportedException;
```

Đây là **khai báo API minh họa**, không phải đoạn mã để tự biên dịch riêng. Có ba chi tiết ngay từ chữ ký:

- `protected`: một lời gọi `someObject.clone()` thường **không truy cập được từ code bên ngoài** nếu lớp không công khai phương thức này. `Cloneable` tự nó không tạo ra `public clone()`.
- `Object` là kiểu trả về khai báo ban đầu; lớp con có thể ghi đè và trả về kiểu cụ thể hơn (*covariant return type*), chẳng hạn `public Employee clone()`.
- `CloneNotSupportedException` là checked exception mà `Object.clone()` công bố. Một override có thể xử lý bên trong và không khai báo exception đó.

Với triển khai **`Object.clone()` mặc định**, theo tài liệu:

1. Khi class runtime không triển khai `Cloneable`, việc gọi cơ chế `Object.clone()` thông thường sẽ ném `CloneNotSupportedException`. **Mảng là trường hợp đặc biệt**: các array được xem là hỗ trợ clone.
2. Khi clone thành công, cơ chế gốc tạo đối tượng mới có **cùng lớp runtime** với đối tượng ban đầu.
3. Các trường của đối tượng mới được khởi tạo từ giá trị các trường tương ứng của đối tượng nguồn, **như phép gán từng trường**.
4. Nội dung của các object được tham chiếu bởi những trường đó **không tự được clone tiếp**: đây là *shallow copy* (sao chép nông).
5. Việc tạo object bằng `Object.clone()` không đi theo đường gọi constructor thông thường của class như `new`.

Các điểm trên mô tả **hành vi của cơ chế gốc** khi được gọi và trả về thành công. Một lớp có thể cung cấp **override `clone()` của riêng nó**, trong đó Java không tự chèn lời gọi `super.clone()`, không tự kiểm tra từng kỳ vọng thiết kế và không tự bổ sung deep copy.

### Cần hiểu đúng Cloneable

`java.lang.Cloneable` là **marker interface**, tức không khai báo bất kỳ phương thức nào. Nó là tín hiệu mà `Object.clone()` kiểm tra để quyết định có được thực hiện sao chép gốc hay không.

```java
final class MarkerOnly implements Cloneable {
    private final int id = 1;
}
```

*Đây là định nghĩa lớp minh họa.* Lớp `MarkerOnly` có `Cloneable` nhưng mã sử dụng bên ngoài **không tự nhiên gọi được** `new MarkerOnly().clone()`, vì phương thức `clone()` kế thừa từ `Object` vẫn là `protected`. Muốn expose API clone, lớp phải chủ động khai báo override `public` hoặc một phương thức sao chép khác.

Ngược lại, một override `clone()` tự tạo object bằng constructor mà **không dùng `super.clone()`** có thể chạy mà không cần marker `Cloneable`. Vì thế không nên nói `implements Cloneable` là điều kiện bắt buộc để *mọi phương thức có tên clone* đều chạy; nó là điều kiện được cơ chế `Object.clone()` mặc định kiểm tra (ngoại trừ array).

## 3. Ba kỳ vọng thường gặp: quy ước hay bảo đảm?

| Mệnh đề | Với Object.clone() gốc thành công | Với override clone() tùy ý | Cách diễn đạt đúng |
| --- | --- | --- | --- |
| `copy != original` | Đối tượng mới được tạo, nên đúng | Override có thể trả `this` | Ý định thông thường của clone, **không phải bảo đảm tuyệt đối** của mọi override. |
| `copy.getClass() == original.getClass()` | Đúng do đối tượng mới cùng lớp runtime | Override có thể trả một object thuộc lớp khác nếu kiểu trả về cho phép | Kỳ vọng tự nhiên, nhưng **không được cưỡng chế** cho mọi override. |
| `copy.equals(original)` | Không nhất thiết: `Object.equals()` mặc định dùng identity | Tùy quy tắc `equals()` và quyết định clone | Thường mong đợi với value object, **không phải điều kiện bắt buộc**. |
| Các trường của copy độc lập hoàn toàn | Không: chỉ shallow copy | Tùy override sao chép nông/sâu | Phải đọc chính sách sao chép của lớp. |
| Gọi constructor của class | Không theo đường gọi constructor thường của `Object.clone()` | Override dùng `new` có thể gọi constructor | Không tổng quát hóa giữa các chiến lược. |
| Luôn hỗ trợ gọi từ bên ngoài | Không: phương thức gốc `protected` | Có thể override `public` | Kiểm tra chữ ký API của lớp đang dùng. |

### Kỳ vọng 1: hai đối tượng có khác nhau không?

Với code dùng `super.clone()` đúng cách, thông thường có object mới nên `copy != source`. Tuy nhiên, một lớp có thể định nghĩa:

```java
@Override
public SomeType clone() {
    return this; // Vi phạm ý định bản sao độc lập, nhưng là code override hợp lệ.
}
```

*Đoạn này chỉ minh họa một override nằm bên trong lớp `SomeType`.* JVM không cưỡng chế lời gọi `super.clone()` trong phương thức override. Đây thường là **thiết kế clone tồi** vì người gọi có thể kỳ vọng một đối tượng riêng; nhưng nó minh họa lý do không được xem quy ước “khác tham chiếu” là bảo đảm tuyệt đối của bất kỳ API mang tên `clone()` nào.

### Kỳ vọng 2: có giữ đúng lớp runtime không?

`Object.clone()` gốc tạo bản sao đúng lớp runtime. Nhưng một override công bố kiểu trả về `Object` có thể trả một object bất kỳ:

```java
@Override
public Object clone() {
    return new Object(); // Không giữ lớp runtime của this.
}
```

*Đây là đoạn override minh họa bên trong một class; không phải thiết kế nên dùng trong ứng dụng.* Java kiểm tra kiểu trả về theo chữ ký phương thức và quy tắc override, chứ không tự yêu cầu `result.getClass() == this.getClass()` cho tất cả override.

Một override có kiểu trả về cụ thể hơn, ví dụ `public Employee clone()`, đã giới hạn được các kiểu có thể trả về ở mức biên dịch, nhưng nếu `Employee` cho phép kế thừa thì object trả về vẫn có thể là một subtype khác. Vì vậy cũng phải hiểu rõ semantics của lớp, không lấy việc phương thức tên `clone` làm bằng chứng.

### Kỳ vọng 3: clone có bằng bản gốc theo equals() không?

Ngay cả khi dùng `super.clone()` và nhận một object khác tham chiếu có **các trường giống hệt**, `clone.equals(original)` vẫn có thể là `false` nếu class không ghi đè `equals()`, bởi `Object.equals()` mặc định so identity.

Với value object triển khai `equals()` dựa trên các trường giá trị, việc sao chép giữ nguyên giá trị thường dẫn tới `equals() == true`. Nhưng đó là kết quả của **định nghĩa equality của lớp**, không phải tính chất mà tên `clone()` tự áp đặt.

Cần kiểm tra cả chiều ngược: `copy.equals(original)` và `original.equals(copy)` phải tuân theo hợp đồng đối xứng của `equals()`, nếu lớp định nghĩa equality. Một clone không cứu được thiết kế `equals()` vốn thiếu nhất quán.

## 4. Shallow copy: sao chép trường chứ không tự sao chép cả đồ thị object

Một `Object.clone()` thành công hoạt động tương tự việc sao chép giá trị từng field:

- Với `int`, `boolean`, `double`...: giá trị nguyên thủy được sao chép.
- Với field tham chiếu như `String`, `Address`, `List`: **tham chiếu** được sao chép. Hai object có thể cùng trỏ tới một object con.
- Nếu field được khai báo `final` và giữ một `List` hoặc object mutable, `final` chỉ ngăn gán lại field, **không** tự làm object mà nó trỏ tới bất biến.

Sơ đồ:

```text
Trước clone:
source -------> Employee A ------address------> Address X

Sau super.clone():
source -------> Employee A ------address------┐
                                             ├--> Address X
shallow ------> Employee B ------address------┘

Sau copy method sao chép thêm Address:
source -------> Employee A ------address------> Address X
isolated -----> Employee C ------address------> Address Y
```

Khi `Address X.city` bị thay đổi qua `shallow.address`, `source.address.city` cũng đổi vì chúng đang dùng chung một `Address`. `isolated` có `Address Y` riêng thì không bị ảnh hưởng bởi thay đổi đó.

Một **deep copy** thực sự cần định nghĩa các object con nào phải sao chép, có chia sẻ một số tham chiếu bất biến không, xử lý quan hệ vòng thế nào và xử lý tài nguyên như file/socket/lock ra sao. Java không có một `Object.deepClone()` tiêu chuẩn tự giải quyết tất cả những vấn đề này.

## 5. Ví dụ Java 17+ chạy được: minh họa cả cơ chế gốc và override bất thường

Tạo tệp `CloneGuaranteeDemo.java`:

```java
public class CloneGuaranteeDemo {

    static final class Address {
        String city;

        Address(String city) {
            this.city = city;
        }
    }

    static final class Employee implements Cloneable {
        private final String name;
        private final Address address;

        Employee(String name, Address address) {
            this.name = name;
            this.address = address;
        }

        @Override
        public Employee clone() {
            try {
                return (Employee) super.clone();
            } catch (CloneNotSupportedException ex) {
                // Employee implements Cloneable, nên không mong đợi exception.
                throw new AssertionError(ex);
            }
        }

        Employee copyWithIndependentAddress() {
            return new Employee(name, new Address(address.city));
        }
    }

    static final class NotCloneable {
        @Override
        public NotCloneable clone() throws CloneNotSupportedException {
            return (NotCloneable) super.clone();
        }
    }

    static final class ReturnsSelf implements Cloneable {
        @Override
        public ReturnsSelf clone() {
            return this; // Cố ý làm sai quy ước clone; chỉ để minh họa.
        }
    }

    static final class ReturnsOtherType implements Cloneable {
        @Override
        public Object clone() {
            return new Object(); // Cố ý làm sai kỳ vọng cùng lớp runtime.
        }
    }

    public static void main(String[] args) {
        Employee original = new Employee("An", new Address("Hà Nội"));
        Employee shallow = original.clone();
        Employee isolated = original.copyWithIndependentAddress();

        System.out.println(original != shallow);
        System.out.println(original.getClass() == shallow.getClass());
        System.out.println(original.equals(shallow));
        System.out.println(original.address == shallow.address);
        System.out.println(original.address == isolated.address);

        shallow.address.city = "Huế";
        System.out.println(original.address.city);
        System.out.println(isolated.address.city);

        try {
            new NotCloneable().clone();
        } catch (CloneNotSupportedException ex) {
            System.out.println("CloneNotSupportedException");
        }

        ReturnsSelf self = new ReturnsSelf();
        System.out.println(self.clone() == self);

        ReturnsOtherType odd = new ReturnsOtherType();
        System.out.println(odd.clone().getClass() == odd.getClass());

        int[][] matrix = {{1, 2}};
        int[][] arrayCopy = matrix.clone();
        System.out.println(matrix != arrayCopy);
        System.out.println(matrix[0] == arrayCopy[0]);
    }
}
```

Biên dịch và chạy từ thư mục chứa file bằng JDK 17 trở lên:

```bash
javac CloneGuaranteeDemo.java
java CloneGuaranteeDemo
```

Stdout chính xác:

```text
true
true
false
true
false
Huế
Hà Nội
CloneNotSupportedException
true
false
true
true
```

Giải thích:

1. `Employee` triển khai `Cloneable`, expose `public clone()` và gọi `super.clone()`. Bản `shallow` khác tham chiếu nhưng giữ cùng lớp runtime.
2. `Employee` không ghi đè `equals()`. Dù bản sao nông giữ nguyên giá trị trường ban đầu, `original.equals(shallow)` vẫn là `false` theo identity equality mặc định.
3. `original.address == shallow.address` là `true`; chỉ tham chiếu `address` được copy. Ngược lại `copyWithIndependentAddress()` tạo `Address` mới, nên phép so tham chiếu trả `false`.
4. Sau khi sửa `shallow.address.city` thành `Huế`, bản gốc cũng thấy `Huế` vì chia sẻ object con; bản `isolated` vẫn `Hà Nội`.
5. `NotCloneable` cố gọi `super.clone()` nhưng không triển khai `Cloneable`: phương thức gốc ném `CloneNotSupportedException`. Lớp vẫn có thể khai báo override `public`; marker ảnh hưởng **cơ chế gốc**, không quyết định tên/chữ ký method có tồn tại hay không.
6. `ReturnsSelf` có override trả `this`; phép kiểm tra `self.clone() == self` in `true`. Đây là phương thức hợp lệ về cú pháp, nhưng **không thực hiện ý định tạo bản sao mới**.
7. `ReturnsOtherType` ghi đè `clone()` trả `new Object()`; phép kiểm tra cùng lớp runtime in `false`. Như vậy “clone luôn giữ đúng lớp” cũng không được áp đặt lên override tùy ý.
8. Một mảng hai chiều `int[][]` vẫn có clone công khai theo cơ chế array. `matrix != arrayCopy` là `true`, nhưng `matrix[0] == arrayCopy[0]` cũng `true`: mảng ngoài mới, các mảng con vẫn được chia sẻ.

Hai lớp `ReturnsSelf` và `ReturnsOtherType` chỉ phục vụ chứng minh ranh giới API. **Không nên triển khai clone theo hai kiểu này trong code ứng dụng thông thường**: chúng phá kỳ vọng mà caller dễ dựa vào.

## 6. Điều gì là quy ước triển khai clone() tốt?

Một lớp chủ động hỗ trợ clone nên có chính sách có thể hiểu và kiểm chứng. Các lựa chọn thường gặp:

1. **Public API rõ ràng:** nếu người dùng bên ngoài cần gọi clone, cung cấp `public clone()` với kiểu trả về cụ thể, thay vì mặc nhiên trông chờ vào `protected Object.clone()`.
2. **Gọi super.clone khi dùng cơ chế chuẩn:** override của lớp có `Cloneable` thường bắt đầu bằng `super.clone()` để lấy bản sao nông của đúng lớp runtime. Điều này đặc biệt quan trọng khi có kế thừa; không thể luôn thay an toàn bằng `new Base(...)` vì object thực có thể là subclass.
3. **Sao chép thành phần mutable theo chính sách:** chỉ chia sẻ object bất biến (như `String`) hoặc những tài nguyên được thiết kế cho việc chia sẻ; copy thêm khi yêu cầu độc lập.
4. **Giữ invariant:** xác định xem các trường `final` mutable, cache, lock, handle file, thread và tài nguyên runtime có thể được sao chép hay không. Một bản chép từng field không tự có ngữ nghĩa an toàn.
5. **Kiểm tra hậu điều kiện:** với lớp có hợp đồng clone, có thể kiểm tra `copy != source`, lớp runtime tương thích, dữ liệu cần giữ nguyên, các object con cần độc lập và các trường được phép chia sẻ.
6. **Ghi rõ mức sao chép:** “shallow”, “deep cho Address”, “giữ chung cache” hoặc “không hỗ trợ clone” giúp caller không phải tự đoán.

Một mẫu triển khai ở lớp `final` đơn giản:

```java
final class Point implements Cloneable {
    private final int x;
    private final int y;

    Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public Point clone() {
        try {
            return (Point) super.clone();
        } catch (CloneNotSupportedException ex) {
            throw new AssertionError(ex);
        }
    }
}
```

*Đây là định nghĩa lớp minh họa, không phải chương trình có `main`.* `Point` chỉ chứa hai trường nguyên thủy, nên shallow copy đã đủ độc lập về các giá trị đó. Tuy vậy, lớp **chưa ghi đè `equals()`**, nên dù cả hai có cùng `x/y` thì `clone.equals(original)` vẫn `false`. Nếu domain muốn value equality, nên định nghĩa thêm `equals()/hashCode()` tương thích hoặc cân nhắc sử dụng một `record Point(int x, int y)` bất biến.

### Có cần mọi lớp implements Cloneable không?

Không. Có những lớp không nên cho phép sao chép: kết nối đang mở, đối tượng quản lý khóa, các thành phần chứa resource sở hữu duy nhất, các đối tượng điều phối vòng đời đặc biệt. Có lớp muốn sao chép nhưng không muốn dùng `Cloneable` vì API lịch sử này có giới hạn về quyền truy cập, checked exception, invariant và sao chép nông. Copy constructor hoặc factory thường mô tả chính sách rõ hơn:

```java
Employee duplicate = original.copyWithIndependentAddress();
```

Trong trường hợp ví dụ ở mục 5, method này chủ động tạo `Address` mới, cho caller biết rõ phạm vi độc lập của bản sao. Nhưng đây **không phải deep clone tổng quát**: nếu `Employee` có thêm một đồ thị object mutable khác, cần định nghĩa riêng việc xử lý nó.

## 7. Array.clone() là ngoại lệ API cần biết

Các kiểu mảng Java là đối tượng. Mảng được xem là triển khai `Cloneable` và hỗ trợ phương thức `clone()` công khai; một biểu thức như:

```java
int[] values = {1, 2, 3};
int[] copy = values.clone();

System.out.println(values == copy); // false
System.out.println(java.util.Arrays.equals(values, copy)); // true
```

*Đoạn trên là các câu lệnh minh họa trong một phương thức.* Phần tử nguyên thủy được sao chép theo giá trị, nên sửa `copy[0]` không thay `values[0]`.

Với mảng chứa tham chiếu như `StringBuilder[]` hoặc `Address[]`, chỉ **mảng ngoài** là object mới; phần tử trong mảng vẫn trỏ đến các object cũ:

```java
StringBuilder[] a = {new StringBuilder("A")};
StringBuilder[] b = a.clone();

System.out.println(a != b);       // true
System.out.println(a[0] == b[0]); // true
```

Mảng hai chiều cũng là mảng các mảng; `matrix.clone()` không tự sao chép từng hàng. Nếu cần bản sao không chia sẻ mảng con, phải clone từng hàng hoặc chuyển đổi bằng cấu trúc dữ liệu phù hợp. **Không được chuyển từ “array có clone” sang kết luận “array clone luôn sâu”.**

## 8. Copy constructor, factory method và immutable object

Trong code mới, đôi khi không cần `clone()`:

| Nhu cầu | Cách tiếp cận | Lưu ý |
| --- | --- | --- |
| Sao chép một value object nhỏ | Copy constructor hoặc `static copyOf(...)` | Chính sách sao chép nằm rõ trong constructor/factory. |
| Đối tượng bất biến | Thường có thể giữ nguyên đối tượng; tạo bản mới khi muốn thay giá trị | Tránh sao chép không cần thiết; tùy hợp đồng API. |
| Đối tượng có field tham chiếu mutable | Copy method chủ động copy trường cần tách | Phải bảo vệ bất biến và tránh chia sẻ không mong muốn. |
| Lớp hiện có với `public clone()` được tài liệu hóa | Gọi theo chính sách của lớp | Kiểm tra shallow/deep và exception cụ thể. |
| Đồ thị phức tạp, tài nguyên hoặc quan hệ vòng | Sao chép chuyên biệt theo nghiệp vụ | Clone mặc định không thể suy ra ownership hay vòng đời. |

Copy constructor có ưu điểm là biểu đạt **rõ constructor nào được gọi, trường nào được tái tạo**. Nhưng với hierarchy mở, constructor của superclass không tự động tạo bản sao đúng subtype nếu không có cơ chế riêng; ngược lại `super.clone()` tạo đối tượng cùng runtime class nhưng có rủi ro copy state quá máy móc. Không có một giải pháp áp dụng tốt cho mọi loại đối tượng.

Một lớp bất biến có thể lựa chọn trả lại `this` từ phương thức có tên `copy()` nếu hợp đồng phương thức nói rõ như vậy. Tuy nhiên, nếu API hứa “tạo object khác tham chiếu” thì trả `this` không đạt yêu cầu. Vì vậy **ngôn ngữ mô tả hợp đồng của phương thức quan trọng hơn chính tên của nó**.

## 9. Các sai lầm phổ biến khi suy diễn bảo đảm từ clone()

### Sai lầm 1: implements Cloneable nghĩa là có public clone()

`Cloneable` không khai báo method. Lớp vẫn phải expose một `public clone()` nếu code bên ngoài cần gọi; `Object.clone()` gốc là `protected`.

### Sai lầm 2: clone luôn trả object mới và khác tham chiếu

`super.clone()` thành công tạo object mới, nhưng override có thể trả `this`. Tài liệu `Object.clone()` gọi đây là ý định thông thường, **không phải yêu cầu tuyệt đối lên mọi override**.

### Sai lầm 3: clone luôn có cùng lớp và bằng nhau theo equals()

Cùng lớp runtime là tính chất của `Object.clone()` gốc khi thành công; override có thể trả kiểu khác. `equals()` thường là identity equality nếu lớp không ghi đè, nên copy khác tham chiếu sẽ `false` dù các field có cùng giá trị.

### Sai lầm 4: clone tự gọi constructor và chạy lại validation

`Object.clone()` sao chép state ở mức field, không đi qua constructor thông thường. Nếu invariant phụ thuộc việc khởi tạo mới, bản sao có thể không phù hợp. Override tự tạo bằng `new` lại có semantics khác, vì vậy không thể khẳng định chung mọi `clone()` đều bỏ qua constructor.

### Sai lầm 5: clone luôn là deep copy

Không. `Object.clone()` chỉ sao chép nông. `final List<Address>` hoặc `final Address` vẫn có thể chia sẻ nội dung mutable. Muốn độc lập phải sao chép các tầng liên quan theo chính sách của lớp.

### Sai lầm 6: clone nhất định sẽ ném CloneNotSupportedException nếu không implements Cloneable

Đúng khi gọi cơ chế `Object.clone()` gốc trên đối tượng không hỗ trợ, trừ trường hợp đặc biệt array. Nhưng một override tự tạo object bằng constructor có thể hoạt động mà không dùng marker. Phải phân biệt cơ chế gốc với phương thức do lớp tự cung cấp.

### Sai lầm 7: không cần test vì clone chỉ chép object

Một object phức tạp có thể chứa cấu trúc mutable, cache, reference vòng, lock hay resource không thể chia sẻ vô điều kiện. Hãy kiểm thử đúng chính sách sao chép, chẳng hạn sửa một bản có ảnh hưởng bản kia ở các field không được phép chia sẻ hay không.

## 10. Kiểm chứng hậu điều kiện theo chính sách cụ thể

Với một lớp có `clone()`, hãy xác định rõ “bản sao đúng” gồm những điều kiện nào và đánh giá từng điều kiện riêng:

| Điều kiện cần hỏi | Kiểm tra thích hợp |
| --- | --- |
| Có cần object khác tham chiếu? | `copy != original`. |
| Có cần cùng lớp runtime? | `copy.getClass() == original.getClass()`. |
| Có cần bình đẳng logic tại lúc sao chép? | `copy.equals(original)` và kiểm tra hợp đồng `equals/hashCode`. |
| Field nguyên thủy có giữ nguyên? | Đối chiếu từng giá trị được định nghĩa trong contract. |
| Field tham chiếu nào được phép chia sẻ? | Kiểm tra `copy.ref == original.ref` khi cần. |
| Field tham chiếu nào buộc phải độc lập? | Kiểm tra khác tham chiếu và thử thay đổi bản sao. |
| Constructor/invariant có được bảo đảm? | Kiểm tra cách dựng object thực tế của phương thức được dùng. |
| Có thể trả về exception hoặc giá trị không hợp lệ? | Đọc chữ ký và tài liệu lớp; kiểm thử các điều kiện từ chối. |

Một lời kiểm tra `copy != original` đơn lẻ không chứng minh nội dung được sao chép đúng. Tương tự, `copy.equals(original)` là `true` cũng không chứng minh hai bản có object con độc lập: chúng có thể cùng giữ một `List` mutable và cùng thay đổi về sau. Mỗi tiêu chí là một phần của **hợp đồng sao chép của lớp**, không phải một hệ quả tự động từ tên `clone()`.

Nếu lớp sử dụng kế thừa, nên kiểm thử với instance subclass thực sự. Override của superclass gọi `super.clone()` có thể tạo đúng runtime class nhưng các field mới của subclass được chép nông mà subclass chưa dự tính; trong một số thiết kế cần override bổ sung để sao chép các thành phần đó đúng cách.

## 11. Tự kiểm tra

**Câu 1.** `Cloneable` có định nghĩa một phương thức `clone()` công khai không?  
**Đáp án:** Không. Đây là marker interface; phương thức `Object.clone()` kế thừa vẫn là `protected` trừ khi lớp expose nó.

**Câu 2.** `x.clone() != x` có đúng với mọi override `clone()` không?  
**Đáp án:** Không. Đây là ý định thông thường, nhưng override có thể trả chính `this`. Với `Object.clone()` gốc thành công, object mới được tạo.

**Câu 3.** `clone.getClass() == original.getClass()` có phải bảo đảm tuyệt đối của mọi phương thức mang tên `clone` không?  
**Đáp án:** Không. `Object.clone()` gốc tạo bản sao cùng runtime class, nhưng override tự viết có thể trả kiểu khác nếu chữ ký cho phép.

**Câu 4.** `Employee` không ghi đè `equals()`; nếu `super.clone()` trả object khác tham chiếu nhưng cùng trường, `clone.equals(original)` là gì?  
**Đáp án:** `false` vì `Object.equals()` mặc định so identity.

**Câu 5.** Field `private final Address address` có khiến `super.clone()` tạo `Address` mới không?  
**Đáp án:** Không. `final` không làm nội dung mutable của `Address` thành bất biến; shallow clone chia sẻ cùng tham chiếu.

**Câu 6.** Một class không implements `Cloneable` nhưng ghi `public X clone() { return new X(...); }` có thể chạy không?  
**Đáp án:** Có, khi phương thức tự dựng object và không gọi `Object.clone()` gốc. Đây là chính sách clone tùy biến; nên tài liệu hóa rõ.

**Câu 7.** Clone mảng hai chiều có tự clone từng hàng không?  
**Đáp án:** Không. `int[][]` là mảng chứa `int[]`; `clone()` tạo mảng ngoài mới, còn các mảng hàng vẫn chia sẻ.

**Câu 8.** Kiểm tra clone bằng `equals() == true` đã đủ đảm bảo deep copy chưa?  
**Đáp án:** Chưa. Equality có thể đúng ngay cả khi bản gốc và bản sao chia sẻ object mutable; phải kiểm tra chính sách độc lập của từng thành phần.

**Câu 9.** Vì sao copy constructor có thể dễ hiểu hơn `Object.clone()` với object có tài nguyên?  
**Đáp án:** Constructor/factory cho phép chỉ rõ object con hoặc tài nguyên nào cần tạo lại, chia sẻ hoặc từ chối sao chép, thay vì copy mọi field tự động.

**Câu 10.** Nếu một API chỉ nói “hỗ trợ clone”, điều gì cần xem trước khi dựa vào nó?  
**Đáp án:** Quyền truy cập và kiểu trả về, khả năng ném exception, liệu phương thức gọi cơ chế gốc hay tự dựng object, mức shallow/deep, chính sách chia sẻ mutable state và các hậu điều kiện lớp thực sự cam kết.

**Ghi nhớ:** `Object.clone()` gốc có hành vi xác định khi gọi thành công: tạo object mới cùng lớp runtime và sao chép nông các field. Nhưng những phát biểu kiểu “mọi clone khác tham chiếu”, “mọi clone cùng lớp”, “mọi clone equals bản gốc” là các **quy ước/kỳ vọng**, không phải bảo đảm cưỡng chế đối với mọi override. Một API sao chép đáng tin phải công bố chính sách thực tế, giữ invariant và được kiểm tra theo nhu cầu của lớp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="clone-contract-009">Ghi đè clone() public và dùng kiểu trả về cụ thể</a>

<details>
<summary>Xem chi tiết</summary>

# Ghi đè `clone()` thành `public` và dùng kiểu trả về cụ thể trong Java

## 1. Bối cảnh: `Object.clone()` không phải API sao chép công khai

Mọi class Java đều kế thừa `java.lang.Object`. `Object` khai báo phương thức:

```java
protected native Object clone() throws CloneNotSupportedException;
```

Đây là **chữ ký API minh họa**, không phải một phương thức người học cần tự định nghĩa. Có ba đặc điểm quan trọng:

- **`protected`**: phương thức `clone()` của `Object` không được gọi tùy ý từ mọi nơi. Mã ứng dụng bên ngoài không thể viết `someObject.clone()` chỉ vì biến có kiểu `Object`.
- **Kiểu trả về `Object`**: ở chữ ký gốc, muốn dùng kết quả như kiểu cụ thể cần ép kiểu. Java cho phép override bằng **kiểu trả về hiệp biến (covariant return type)** để tránh ép kiểu ở phía người gọi.
- **`throws CloneNotSupportedException`**: triển khai của `Object.clone()` kiểm tra đối tượng thực tế có thực hiện marker interface `Cloneable` hay không; nếu không, lời gọi có thể ném ngoại lệ checked này.

Bài học tập trung vào cách một lớp **tự công bố khả năng clone một cách tường minh**, thường bằng `implements Cloneable`, `public T clone()`, gọi `super.clone()` và xử lý trường tham chiếu có thể thay đổi khi cần.

Lưu ý: `Cloneable` chỉ là **marker interface**, không khai báo phương thức `clone()`. Việc viết `implements Cloneable` không tự làm `clone()` trở thành `public`. Đây là một hạn chế của thiết kế API clone truyền thống của Java; với mã mới, copy constructor hoặc static factory thường là lựa chọn dễ kiểm soát hơn.

## 2. Từ `protected Object clone()` sang `public T clone()`

Một mẫu thường gặp:

```java
public class Point implements Cloneable {
    private final int x;
    private final int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public Point clone() {
        try {
            return (Point) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}
```

Đây là một lớp có thể biên dịch trong tệp `Point.java` trên Java 17+. Các quyết định có ý nghĩa rõ ràng:

1. **`implements Cloneable`** đánh dấu đối tượng có thể được `Object.clone()` sao chép theo cơ chế chuẩn.
2. **`@Override`** kiểm tra đúng chữ ký phương thức được override; giúp phát hiện sai tên/tham số.
3. **`public`** mở rộng mức truy cập của phương thức kế thừa `protected`. Java cho phép mở rộng quyền truy cập khi override; **không** cho giảm từ `protected` xuống `private` hoặc mặc định package-private.
4. **`Point` làm kiểu trả về** thay vì `Object`. Vì `Point` là subtype của `Object`, đây là *covariant return type* hợp lệ. Phía gọi có thể viết `Point copy = original.clone();` mà không cần cast.
5. **`super.clone()`** thực hiện bản sao nông của đối tượng. Nó trả `Object` theo chữ ký gốc nên trong thân phương thức vẫn cần ép kiểu `(Point)`; loại bỏ cast **ở người gọi**, không nhất thiết loại bỏ cast **bên trong implementation**.
6. **Khối `catch`** chuyển một ngoại lệ được dự kiến không xảy ra thành `AssertionError`, bởi class trực tiếp triển khai `Cloneable`. Có thể chọn công bố `throws CloneNotSupportedException` thay vì bắt, nhưng khi đó người dùng API phải xử lý ngoại lệ.

Với `Point` chỉ chứa hai `int`, sao chép nông đã đủ: mỗi bản sao giữ bản sao riêng của các giá trị nguyên thủy.

### Vì sao đổi kiểu trả về là hợp lệ?

Java hỗ trợ override một phương thức trả về kiểu tham chiếu bằng phương thức trả về subtype. `Object.clone()` trả `Object`, còn `Point.clone()` trả `Point`, nên hợp lệ. Tương tự, nếu `Report extends Document`, `Report.clone()` có thể trả `Report` khi `Document.clone()` trả `Document`. Cơ chế này gọi là **kiểu trả về hiệp biến**.

Không thể tùy tiện thay kiểu trả về bằng kiểu không liên quan, chẳng hạn từ `Object` thành `int`. Và việc covariant return chỉ áp dụng đúng quy tắc override; không phải một cách tạo overload riêng.

### Phải dùng `public` ở đâu?

Khi API cần cho mã bên ngoài gọi `clone()`, đặt `public` trên **phương thức override**. Nếu một class chỉ `implements Cloneable` nhưng không viết phương thức công khai, mã bên ngoài class không thể gọi `clone()` thông qua reference kiểu class đó theo cách thông thường, vì chỉ còn phiên bản `protected` kế thừa từ `Object`.

`Cloneable` cũng **không phải** kiểu có thể gọi `clone()` trực tiếp: biến kiểu `Cloneable` không có phương thức `clone` trong interface. Chính class cần công khai phương thức; hoặc ứng dụng nên cung cấp một API sao chép có tên và hợp đồng rõ ràng hơn.

## 3. Quy tắc `Cloneable` và ngoại lệ

`Object.clone()` mặc định thực hiện kiểm tra marker `Cloneable` trên kiểu runtime, rồi tạo một đối tượng mới có cùng kiểu runtime và sao chép các field. Không có marker, nó ném `CloneNotSupportedException`.

Hai lựa chọn xử lý ngoại lệ trong override:

**Cách A — để checked exception trong API:**

```java
@Override
public Point clone() throws CloneNotSupportedException {
    return (Point) super.clone();
}
```

Đây là **mảnh mã minh họa** đặt bên trong một lớp `Point implements Cloneable`. Người gọi phải bắt hoặc khai báo `CloneNotSupportedException`. Override có thể giữ checked exception theo hợp đồng của `Object.clone()`.

**Cách B — ẩn checked exception vì class bảo đảm Cloneable:**

```java
@Override
public Point clone() {
    try {
        return (Point) super.clone();
    } catch (CloneNotSupportedException e) {
        throw new AssertionError("Unexpected clone failure", e);
    }
}
```

Đây cũng là **mảnh mã đặt trong class**, chưa phải chương trình độc lập. Vì class đang xét đã implements `Cloneable`, khối `catch` thường là cách phản ánh lỗi lập trình bất thường, thay vì bắt người gọi xử lý checked exception vốn không dự kiến xảy ra với cơ chế `Object.clone()` chuẩn.

Dù theo lựa chọn nào, **không nuốt ngoại lệ rồi trả `null`**: như vậy các lời gọi sau có thể gặp `NullPointerException` ở vị trí xa nguyên nhân thật, và người gọi không thể tin vào hợp đồng trả bản sao.

## 4. Bản sao nông: `super.clone()` sao chép những gì?

`Object.clone()` thông thường tạo một **đối tượng mới cùng kiểu runtime** và sao chép giá trị các field:

- Với `int`, `boolean`, `double` và primitive khác: giá trị được sao chép.
- Với field tham chiếu như `String`, `List`, mảng hoặc object tùy chỉnh: **tham chiếu được sao chép**, không tự nhân bản đối tượng mà tham chiếu đó trỏ tới.
- Nếu tham chiếu trỏ tới object **bất biến** như `String`, việc chia sẻ object thường an toàn.
- Nếu tham chiếu trỏ tới object **mutable** như `int[]` hoặc `ArrayList`, clone và bản gốc có thể nhìn thấy thay đổi của nhau qua tham chiếu chung.

`super.clone()` không tự gọi constructor công khai của chính lớp đang clone như một lệnh `new T(...)` bình thường; vì vậy không thể dựa vào logic constructor để tự tái tạo hoặc sao chép lại các thành phần mutable. Mọi bất biến và ràng buộc với trạng thái sau clone phải được xét trong override.

Ví dụ, nếu class có field `int[] scores` và chỉ gọi `super.clone()`, cả bản gốc lẫn bản sao ban đầu cùng giữ tham chiếu đến **một mảng**. Muốn hai đối tượng chỉnh sửa mảng độc lập, sau khi clone phải tạo bản sao mảng:

```java
@Override
public Report clone() {
    Report copy = (Report) super.clone();
    copy.scores = scores.clone();
    return copy;
}
```

Đoạn mã này giả định class `Report` có field `int[] scores` không final và `super.clone()` gọi tới một override hợp lệ, trả về object runtime là `Report`. Nếu `scores` có thể null thì phải xử lý trường hợp null. Đối với mảng của các đối tượng mutable, `scores.clone()` (nếu là mảng tham chiếu) **chỉ nhân bản mảng**, vẫn chia sẻ các phần tử; muốn deep copy phải xác định cách sao chép từng phần tử.

**Đặc biệt:** `final` field tham chiếu không thể gán lại sau `super.clone()` từ bên trong clone bình thường. Nếu cần clone sâu đối tượng có các field `final` mutable, copy constructor hoặc factory thường tự nhiên hơn `Object.clone()`.

## 5. Java 17: chương trình chạy được với lớp cha và lớp con

Ví dụ sau minh họa cùng lúc: public clone, kiểu trả về cụ thể, covariant return trong lớp con, và tự nhân bản một mảng mutable. Tạo tệp `PublicCloneDemo.java`:

```java
import java.util.Arrays;

public class PublicCloneDemo {
    static class Document implements Cloneable {
        private final String title;

        Document(String title) {
            this.title = title;
        }

        @Override
        public Document clone() {
            try {
                return (Document) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }

        public String title() {
            return title;
        }
    }

    static final class Report extends Document {
        private int[] scores;

        Report(String title, int[] scores) {
            super(title);
            this.scores = scores.clone();
        }

        @Override
        public Report clone() {
            Report copy = (Report) super.clone();
            copy.scores = scores.clone();
            return copy;
        }

        public void setFirstScore(int score) {
            scores[0] = score;
        }

        public int firstScore() {
            return scores[0];
        }

        public int[] scoresSnapshot() {
            return scores.clone();
        }
    }

    public static void main(String[] args) {
        Document source = new Document("Guide");
        Document clonedDocument = source.clone();

        System.out.println("document different reference: "
                + (source != clonedDocument));
        System.out.println("document title: " + clonedDocument.title());

        Report report = new Report("Metrics", new int[] {2, 4});
        Report clonedReport = report.clone();

        System.out.println("report different reference: "
                + (report != clonedReport));
        System.out.println("same runtime type: "
                + (report.getClass() == clonedReport.getClass()));
        System.out.println("before: "
                + report.firstScore() + "/" + clonedReport.firstScore());

        report.setFirstScore(9);
        System.out.println("after: "
                + report.firstScore() + "/" + clonedReport.firstScore());
        System.out.println("copy scores: "
                + Arrays.toString(clonedReport.scoresSnapshot()));
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 PublicCloneDemo.java
java PublicCloneDemo
```

Kết quả chuẩn:

```text
document different reference: true
document title: Guide
report different reference: true
same runtime type: true
before: 2/2
after: 9/2
copy scores: [2, 4]
```

Diễn giải:

1. `Document implements Cloneable` và công khai `public Document clone()`. Phía gọi gán trực tiếp `Document clonedDocument = source.clone();`, không cần ép kiểu.
2. `Report` kế thừa chính sách clone của `Document`. `Report.clone()` override và **thu hẹp kiểu trả về** từ `Document` thành `Report`, nên `Report clonedReport = report.clone();` hợp lệ.
3. Bên trong `Document.clone()`, lệnh `Object.clone()` tạo đối tượng **cùng kiểu runtime** với receiver. Khi receiver là `Report`, đối tượng được tạo vẫn là `Report`. Vì thế phép ép kiểu `(Report) super.clone()` trong `Report.clone()` là hợp lệ theo cấu trúc này.
4. Dữ liệu `title` là `String` bất biến nên chia sẻ tham chiếu an toàn cho ví dụ. Ngược lại, `scores` là mảng mutable; `Report.clone()` tạo mảng riêng bằng `scores.clone()`.
5. Sau khi thay điểm đầu của bản gốc thành `9`, bản sao vẫn giữ `2`. Đây là bằng chứng cho việc copy đã độc lập ở **cấp mảng `int[]`**, không phải khẳng định `Object.clone()` luôn thực hiện deep copy.

Nếu bỏ dòng `copy.scores = scores.clone();`, phần tử đầu của mảng trong hai object sẽ cùng thay đổi vì cả hai cùng trỏ vào mảng cũ. Nếu array chứa object có thể thay đổi, việc clone mảng cũng chưa sao chép sâu từng object bên trong.

## 6. Kiểu trả về hiệp biến và vai trò của thừa kế

Xét hệ phân cấp:

```text
Object.clone(): Object
    ↓ override
Document.clone(): Document
    ↓ override
Report.clone(): Report
```

Mỗi mức override được phép trả về subtype của kiểu ở mức cha. Đây là quy tắc của Java, không phải phép ép kiểu runtime tự phát. Các phương thức đều tên `clone`, không có tham số và override nhau; `@Override` giúp trình biên dịch kiểm tra.

Điều này cải thiện trải nghiệm người dùng API:

```java
Report report = new Report("Metrics", new int[] {2, 4});
Report copied = report.clone(); // Không cần (Report)
```

Đây là đoạn code giả định các lớp từ `PublicCloneDemo` ở trên. Nếu biến được khai báo dưới kiểu tĩnh `Document`:

```java
Document baseView = report;
Document copiedThroughBase = baseView.clone();
```

Biểu thức `baseView.clone()` có **kiểu trả về tại thời điểm biên dịch** là `Document` do chữ ký trên `Document`; tại runtime Java vẫn dispatch tới override `Report.clone()`. Muốn biến kiểu `Report` mà không cast, cần giữ reference có kiểu tĩnh `Report` hoặc thiết kế factory/API với typing phù hợp. Covariant return không khiến trình biên dịch đoán một subtype từ đối tượng runtime khi kiểu tĩnh rộng hơn.

Ở cấp bytecode, trình biên dịch có thể sinh **bridge method** để bảo đảm hành vi override và tương thích lời gọi qua kiểu cha. Đây là chi tiết triển khai, không làm thay đổi quy tắc viết code Java bên trên.

## 7. Bảo vệ hợp đồng clone với lớp có thể kế thừa

Với class **không final**, thiết kế clone phải cân nhắc lớp con. Một override gọi `super.clone()` thường tạo đối tượng của lớp runtime thực sự, kể cả nếu code đang nằm trong lớp cha. Lớp con có trạng thái mutable mới nên tự override `clone()` để hoàn tất việc copy; nếu không, bản sao của lớp con có thể chia sẻ các đối tượng mutable với bản gốc.

Với class `final`, bạn không phải xử lý subclass trong tương lai và có thể xác định chính xác tập field cần sao chép. Với class cho phép kế thừa, hãy ghi rõ hợp đồng: subclass có được phép override không, phải làm gì với mutable field và có cần bảo toàn trạng thái đặc biệt không.

Không nên tuyên bố `Cloneable` đồng nghĩa mọi clone là **deep copy**. `Cloneable` chỉ báo khả năng thực hiện cơ chế copy bằng `Object.clone()`; lớp tự chịu trách nhiệm ý nghĩa sao chép của dữ liệu mà nó quản lý.

Một số nguyên tắc cho API clone công khai:

- **Không trả `this`** nếu giao ước mong một instance mới; với mutable object thông thường, hai biến trỏ cùng đối tượng không phải bản sao.
- **Không tự động gọi constructor**: kiểm tra invariants nếu trạng thái bình thường được tạo bởi constructor, validation hoặc liên kết giữa các field.
- **Đảm bảo các field liên quan nhất quán**: một clone có thể cần khởi tạo lại cache, lock, handle hệ thống hoặc ID tài nguyên thay vì chia sẻ trực tiếp.
- **Ghi rõ copy là nông hay sâu đến mức nào**: “deep copy” phải định nghĩa theo cấu trúc dữ liệu cụ thể, không chỉ dựa trên việc gọi `clone()` nhiều lần.
- **Tránh clone object sở hữu tài nguyên ngoài JVM** nếu không có chính sách sao chép rõ ràng; socket, file descriptor, transaction, thread và resource handle không mặc nhiên có thể nhân bản an toàn.

## 8. `clone()` khác copy constructor và factory

Java không yêu cầu mọi object phải hỗ trợ clone. Một phương án đơn giản hơn là copy constructor hoặc một phương thức static rõ tên:

```java
public final class Settings {
    private final String name;
    private final java.util.List<String> tags;

    public Settings(String name, java.util.List<String> tags) {
        this.name = java.util.Objects.requireNonNull(name);
        this.tags = java.util.List.copyOf(tags);
    }

    public Settings(Settings original) {
        this(original.name, original.tags);
    }

    public static Settings copyOf(Settings original) {
        return new Settings(original);
    }
}
```

Đây là class `Settings.java` hợp lệ trong Java 17. Bản sao dùng constructor có thể giữ field `final`, kiểm tra input và sao chép danh sách bất biến từ đầu. Với `List<String>` (và chuỗi bất biến), mô hình an toàn hơn việc dùng `Object.clone()` rồi phải gán lại field `final`.

| Lựa chọn | Đặc điểm | Phù hợp |
| --- | --- | --- |
| `public T clone()` + `Cloneable` | Tận dụng `Object.clone()`; kiểu trả về cụ thể; cần quản lý shallow/deep copy và checked exception | API cũ hoặc kiểu đã có quy ước clone rõ |
| Copy constructor `new T(original)` | Gọi constructor, cho phép kiểm tra invariant và làm bản sao theo chính sách đã xác định | Value object hoặc đối tượng quản lý field `final` |
| Static factory `T.copyOf(source)` | Tên API diễn đạt ý định, có thể tái dùng đối tượng bất biến nếu hợp đồng cho phép | API ứng dụng mới, kiểu bất biến |

Với record hoặc đối tượng immutable, đôi khi **không cần bản sao mới**: có thể chia sẻ cùng instance an toàn nếu chỉ cần giữ giá trị. Nếu API yêu cầu danh tính object khác nhau thì phải nói rõ yêu cầu đó.

## 9. Những lỗi thường gặp

### Chỉ viết `implements Cloneable` mà không override `clone()`

Marker không thêm một `public clone()` vào lớp. Vì `Object.clone()` là `protected`, người dùng bên ngoài vẫn không gọi được `obj.clone()` qua API công khai thông thường. Muốn hỗ trợ tính năng, công bố phương thức với mức truy cập phù hợp.

### Override nhưng tiếp tục trả `Object`

`public Object clone()` là một override hợp lệ về kiểu nhưng khiến người gọi cần cast về kiểu đích. Khi class trả về chính subtype của nó, khai báo `public T clone()` thường thân thiện và kiểm tra kiểu tốt hơn.

### Quên `Cloneable`

Gọi `super.clone()` trong một class không thực hiện `Cloneable` thường dẫn đến `CloneNotSupportedException` khi thực thi. Phải hiểu rằng clone là một hợp đồng opt-in; không thể chỉ tăng quyền truy cập và mong cơ chế copy tự hoạt động.

### Không xử lý `CloneNotSupportedException`

Nếu gọi trực tiếp `super.clone()`, lập trình viên phải bắt hoặc khai báo checked exception theo luật Java. Với class implements `Cloneable`, mẫu bắt và chuyển thành `AssertionError` thường được dùng khi ngoại lệ được xem là bất thường.

### Tưởng `super.clone()` là deep copy

Bản sao nông giữ chung tham chiếu field với object gốc. Với list, array, map hoặc đối tượng trạng thái thay đổi, hai bản có thể ảnh hưởng nhau. Cần copy tiếp theo mục tiêu, hoặc dùng constructor/factory.

### Bỏ qua lớp con

Một lớp cha `clone()` công khai có thể sao chép đúng field của cha nhưng để field mutable thêm bởi subclass bị chia sẻ. Vì vậy subclass thường phải override và trả về kiểu subclass, sao chép bổ sung trạng thái mà nó sở hữu.

### Clone class có invariant hoặc tài nguyên đặc biệt

`Object.clone()` không chạy lại constructor của class được clone để tái thiết lập mọi điều kiện. Với object có resource, handle, cache phụ thuộc trạng thái hoặc quan hệ giữa nhiều trường, clone nông có thể không hợp lệ. Định nghĩa chính sách copy cụ thể hoặc tránh cơ chế clone cho kiểu đó.

## 10. Checklist khi triển khai `public T clone()`

1. Xác định **vì sao** đối tượng cần clone: phải tạo object khác reference, giữ cùng giá trị, hay cần hai trạng thái độc lập?
2. Nếu sử dụng `Object.clone()`, kiểm tra lớp thuộc nhánh thực hiện `Cloneable`.
3. Override bằng **`@Override public T clone()`** và bảo đảm `T` là kiểu trả về hiệp biến hợp lệ.
4. Gọi `super.clone()` và xử lý `CloneNotSupportedException`, hoặc khai báo checked exception trong API nếu phù hợp.
5. Kiểm tra field primitive, immutable và mutable: field mutable nào cần sao chép cấp phát riêng?
6. Với mảng các đối tượng mutable hoặc collection lồng nhau, kiểm tra cả **phần tử**, không dừng ở `array.clone()` hoặc `new ArrayList<>(list)`.
7. Với subclass, cân nhắc `super.clone()` và bổ sung sao chép field riêng; kiểm tra kiểu runtime thực của clone.
8. Thử `original != clone`, so dữ liệu ban đầu và sửa mutable state ở một phía để xác minh mức độ độc lập.
9. Nếu class chứa field `final`, invariant phức tạp hoặc resource ngoại vi, đánh giá copy constructor/static factory trước.
10. Ghi rõ hành vi clone trong tài liệu API; đừng suy diễn từ marker `Cloneable` rằng clone chắc chắn sâu hay an toàn.

**Kết luận:** Muốn người dùng class gọi `clone()` trực tiếp và nhận đúng kiểu, thường viết `public T clone()` để override `Object.clone()` với **quyền truy cập rộng hơn** và **kiểu trả về hiệp biến**. Khi dùng cơ chế `super.clone()`, cần `Cloneable`, xử lý ngoại lệ và hiểu mặc định đây là **shallow copy**. Khả năng copy sâu hay bảo toàn bất biến của đối tượng là trách nhiệm riêng của thiết kế lớp, không phải kết quả tự động của `public` hoặc covariant return.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="clone-contract-010">Mảng hỗ trợ clone() và mảng nhiều chiều vẫn chia sẻ mảng con</a>

<details>
<summary>Xem chi tiết</summary>

# Mảng hỗ trợ clone() và mảng nhiều chiều vẫn chia sẻ mảng con

## 1. Khái niệm cốt lõi

Mảng trong Java là **đối tượng**. Dù mảng không khai báo lớp bằng cú pháp thông thường, mọi kiểu mảng đều hỗ trợ `clone()`; mảng cũng triển khai giao diện đánh dấu `Cloneable`. Khi gọi `array.clone()`, Java tạo **một đối tượng mảng mới có cùng kiểu runtime, cùng độ dài**, rồi sao chép từng phần tử của mảng ban đầu sang mảng mới.

Điểm mấu chốt là **`clone()` của mảng là sao chép nông (shallow copy), chỉ sao chép một cấp**:

- Với `int[]`, `double[]`, `boolean[]`..., các phần tử là giá trị nguyên thủy nên mỗi mảng giữ một bản giá trị riêng.
- Với `String[]`, `Person[]`, `int[][]`..., các phần tử là **tham chiếu**; `clone()` sao chép giá trị tham chiếu, **không nhân bản đối tượng được tham chiếu**.
- Với mảng nhiều chiều như `int[][]` hoặc `String[][][]`, phần tử của mảng ngoài chính là **tham chiếu tới các mảng con**. Do đó clone mảng ngoài **không clone các mảng con**.

Ví dụ trực quan:

```text
int[][] original = { {1, 2}, {3, 4} };
int[][] copy = original.clone();

original ──► mảng ngoài A ──► [tham chiếu hàng X, tham chiếu hàng Y]
copy     ──► mảng ngoài B ──► [tham chiếu hàng X, tham chiếu hàng Y]
                                          │               │
                                          ▼               ▼
                                      [1, 2]          [3, 4]
```

`original` và `copy` là hai mảng ngoài khác nhau, **nhưng `original[0] == copy[0]` và `original[1] == copy[1]` đều đúng**.

**Ghi nhớ trước:** `clone()` tạo vỏ mảng mới; nó không tự đi sâu vào các phần tử là object hay các chiều phía trong.

## 2. Mảng là trường hợp đặc biệt so với Object.clone()

Trong `java.lang.Object`, `clone()` được khai báo `protected`, trả `Object`, và cơ chế mặc định thường liên quan đến `Cloneable`. Nhưng **kiểu mảng được Java hỗ trợ đặc biệt**:

```java
int[] a = {1, 2, 3};
int[] b = a.clone();  // Không cần ép kiểu; kết quả là int[].
```

Đây là đoạn minh họa có thể đặt trong phương thức Java. Với mảng:

1. Có thể gọi `clone()` trực tiếp như một phương thức công khai.
2. Biểu thức `a.clone()` mang **kiểu mảng tương ứng** (`int[]` trong ví dụ), không bắt buộc ép từ `Object`.
3. Mảng mới khác tham chiếu với mảng gốc, có cùng `length` và cùng loại thành phần.
4. Không phải tự khai báo `implements Cloneable` cho mảng, cũng không phải bắt `CloneNotSupportedException` khi gọi `clone()` của mảng.
5. Nội dung được sao chép **một cấp**, không phải deep copy đệ quy.

Giao diện `Cloneable` là **marker interface** (không khai báo một phương thức `clone()` công khai). Không nên suy rằng mọi đối tượng triển khai `Cloneable` đều cung cấp API clone công khai thuận tiện giống mảng. Đặc biệt, với lớp tự định nghĩa, `clone()` cần được thiết kế và công bố riêng nếu thật sự muốn hỗ trợ.

## 3. Ví dụ 1: mảng nguyên thủy và mảng tham chiếu

Tạo tệp `OneDimensionalCloneDemo.java`:

```java
import java.util.Arrays;

public class OneDimensionalCloneDemo {
    static final class Box {
        int value;

        Box(int value) {
            this.value = value;
        }
    }

    public static void main(String[] args) {
        int[] numbers = {1, 2, 3};
        int[] numberCopy = numbers.clone();
        numberCopy[0] = 99;

        System.out.println(numbers == numberCopy);
        System.out.println(Arrays.toString(numbers));
        System.out.println(Arrays.toString(numberCopy));

        Box[] boxes = {new Box(10), new Box(20)};
        Box[] boxCopy = boxes.clone();

        System.out.println(boxes == boxCopy);
        System.out.println(boxes[0] == boxCopy[0]);

        boxCopy[0].value = 77;
        System.out.println(boxes[0].value);

        boxCopy[1] = new Box(88);
        System.out.println(boxes[1].value);
        System.out.println(boxCopy[1].value);
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac OneDimensionalCloneDemo.java
java OneDimensionalCloneDemo
```

Kết quả:

```text
false
[1, 2, 3]
[99, 2, 3]
false
true
77
20
88
```

Diễn giải:

- `numbers` và `numberCopy` là hai mảng `int[]` riêng. Gán `numberCopy[0] = 99` không sửa phần tử `numbers[0]`, vì mỗi ô lưu trực tiếp giá trị `int`.
- `boxes` và `boxCopy` cũng là **hai mảng khác nhau**, nhưng ô số 0 của chúng chứa cùng tham chiếu `Box`. Sửa `boxCopy[0].value` chính là sửa object mà `boxes[0]` cũng trỏ đến; đầu ra là `77`.
- `boxCopy[1] = new Box(88)` chỉ **thay tham chiếu trong mảng copy**. Nó không thay ô `boxes[1]`, nên giá trị trong object gốc vẫn là `20`.

Phân biệt rõ **gán lại một ô mảng** và **sửa object nằm sau tham chiếu trong ô đó**. Đây cũng là chìa khóa để hiểu mảng nhiều chiều.

## 4. Mảng nhiều chiều thực chất là mảng của các mảng

Java không cần lưu `int[][]` như một khối hình chữ nhật liên tục. Một `int[][]` là mảng chứa tham chiếu tới các `int[]`; mỗi mảng hàng có thể dài ngắn khác nhau, thậm chí một hàng có thể là `null`. Dạng có các hàng khác độ dài thường được gọi là **jagged/ragged array**.

Xét:

```java
int[][] original = {
    {1, 2},
    {3, 4}
};
int[][] outerCopy = original.clone();
```

Đây là đoạn minh họa có thể đặt trong một phương thức. Sau câu lệnh clone:

| Biểu thức | Kết quả | Lý do |
| --- | --- | --- |
| `original == outerCopy` | `false` | Hai mảng ngoài là hai object |
| `original.length == outerCopy.length` | `true` | Số hàng được sao chép |
| `original[0] == outerCopy[0]` | `true` | Hai ô giữ cùng tham chiếu hàng 0 |
| `original[1] == outerCopy[1]` | `true` | Hai ô giữ cùng tham chiếu hàng 1 |
| `original[0][0] == outerCopy[0][0]` | `true` | Đang đọc cùng phần tử ở cùng mảng con |

Nếu gán `outerCopy[0][0] = 90`, mảng gốc cũng thấy `original[0][0] == 90`. Ngược lại, `outerCopy[1] = new int[] {7, 8}` chỉ thay tham chiếu đến hàng 1 **ở mảng ngoài mới**; hàng 1 của `original` vẫn giữ `{3, 4}`.

Với `int[][][]`, `clone()` cũng **chỉ sao chép chiều ngoài cùng**. Các đối tượng `int[][]` bên trong vẫn được chia sẻ, cùng với các `int[]` mà chúng đang trỏ tới.

## 5. Ví dụ 2: thay phần tử mảng con và thay cả mảng con

Tạo tệp `MultidimensionalCloneDemo.java`:

```java
import java.util.Arrays;

public class MultidimensionalCloneDemo {
    public static void main(String[] args) {
        int[][] original = {
            {1, 2},
            {3, 4}
        };

        int[][] copy = original.clone();

        System.out.println(original == copy);
        System.out.println(original[0] == copy[0]);
        System.out.println(Arrays.deepEquals(original, copy));

        copy[0][0] = 90;
        System.out.println(original[0][0]);

        copy[1] = new int[] {7, 8};
        System.out.println(Arrays.toString(original[1]));
        System.out.println(Arrays.toString(copy[1]));

        System.out.println(Arrays.deepToString(original));
        System.out.println(Arrays.deepToString(copy));
    }
}
```

Chạy:

```bash
javac MultidimensionalCloneDemo.java
java MultidimensionalCloneDemo
```

Kết quả:

```text
false
true
true
90
[3, 4]
[7, 8]
[[90, 2], [3, 4]]
[[90, 2], [7, 8]]
```

Đặc biệt chú ý hai thao tác có ý nghĩa rất khác:

1. **`copy[0][0] = 90`** đi xuyên qua tham chiếu được chia sẻ để sửa **phần tử của chính mảng hàng 0**. Vì thế `original[0][0]` cũng là `90`.
2. **`copy[1] = new int[] {7, 8}`** chỉ gán một tham chiếu hàng mới vào **mảng ngoài `copy`**. Mảng ngoài `original` tiếp tục trỏ đến hàng cũ `[3, 4]`.

`Arrays.deepEquals(original, copy)` ban đầu là `true` vì cấu trúc giá trị ở các hàng giống nhau. Nhưng `deepEquals` **không chứng minh rằng mảng được deep-clone**: ngay cả hai mảng còn chia sẻ tham chiếu hàng cũng có thể bằng nhau về nội dung.

## 6. Muốn copy sâu int[][] thì clone từng hàng

Đối với `int[][]` mà từng hàng chỉ chứa các giá trị nguyên thủy, có thể tạo mảng ngoài mới và clone **mỗi mảng con không phải null**. Khi đó mỗi hàng `int[]` là một object mới, dữ liệu `int` độc lập.

Tạo tệp `DeepCopyRowsDemo.java`:

```java
import java.util.Arrays;

public class DeepCopyRowsDemo {
    static int[][] copyAllRows(int[][] source) {
        if (source == null) {
            return null;
        }
        int[][] copy = source.clone();
        for (int row = 0; row < source.length; row++) {
            if (source[row] != null) {
                copy[row] = source[row].clone();
            }
        }
        return copy;
    }

    public static void main(String[] args) {
        int[][] original = {
            {1, 2},
            null,
            {3}
        };
        int[][] deepCopy = copyAllRows(original);

        System.out.println(original == deepCopy);
        System.out.println(original[0] == deepCopy[0]);
        System.out.println(deepCopy[1] == null);
        System.out.println(Arrays.deepEquals(original, deepCopy));

        deepCopy[0][0] = 42;
        System.out.println(original[0][0]);
        System.out.println(deepCopy[0][0]);
        System.out.println(Arrays.deepToString(original));
        System.out.println(Arrays.deepToString(deepCopy));
    }
}
```

Chạy:

```bash
javac DeepCopyRowsDemo.java
java DeepCopyRowsDemo
```

Kết quả:

```text
false
false
true
true
1
42
[[1, 2], null, [3]]
[[42, 2], null, [3]]
```

Hàm `copyAllRows` thực hiện:

1. Trả `null` nếu đầu vào `null`; đây là chính sách do ví dụ lựa chọn.
2. Sao chép mảng ngoài bằng `source.clone()`.
3. Duyệt các hàng, sao chép từng `int[]` bằng `clone()`, giữ nguyên hàng `null`.
4. Trả một mảng mới không dùng chung **bất kỳ mảng hàng không null nào** với đầu vào, đối với trường hợp này.

Do dữ liệu bên trong cùng chỉ là `int`, việc clone từng hàng đủ để **cô lập các phần tử số** của mảng hai chiều. Với `int[][][]`, cần tiếp tục sao chép sâu thêm cấp nữa. Với `Person[][]`, clone hai cấp mảng vẫn có thể **chia sẻ các object `Person`**; nếu object có thể sửa thì cần chiến lược sao chép riêng cho object đó.

### Giới hạn quan trọng: alias nội bộ

Nếu `source[0]` và `source[1]` vốn cùng trỏ tới một mảng hàng, cách clone từng hàng ở trên sẽ tạo **hai hàng mới khác nhau**. Như vậy dữ liệu số vẫn được sao chép, nhưng quan hệ **hai ô cùng trỏ đến một object** có thể không được giữ nguyên. Khi yêu cầu là sao chép **đồ thị đối tượng** và bảo toàn các quan hệ chia sẻ/tham chiếu vòng, cần thiết kế thuật toán copy chuyên biệt (thường ghi nhớ các object đã sao chép). Không nên tuyên bố rằng mọi “deep copy” đều có cùng nghĩa trong tất cả ứng dụng.

## 7. clone(), Arrays.copyOf() và System.arraycopy() khác gì?

Một số lựa chọn khác cũng chỉ sao chép **một cấp** khi phần tử là tham chiếu.

| Cách làm | Tác dụng chính | Với `int[][]` |
| --- | --- | --- |
| `source.clone()` | Mảng mới cùng độ dài, giữ cùng loại mảng | Mảng ngoài mới, **chia sẻ các hàng** |
| `Arrays.copyOf(source, source.length)` | Tạo mảng mới và chép các phần tử, có thể chỉ định độ dài mới | Mảng ngoài mới, **chia sẻ các hàng** |
| `System.arraycopy(source, 0, dest, 0, n)` | Chép phần tử vào một mảng đích đã có | Chép **tham chiếu hàng**, không clone hàng |
| `source.clone()` rồi clone từng `source[row]` | Tạo mảng ngoài và từng hàng mới | Các hàng `int[]` độc lập |

Các phương thức trên không tự clone mọi object nằm sau tham chiếu. Dùng `Arrays.copyOf` thay `clone` không phải cách “sửa” chia sẻ mảng con.

Ví dụ minh họa độc lập về ý nghĩa (đặt trong phương thức):

```java
int[][] source = {{1}, {2}};
int[][] viaCopyOf = Arrays.copyOf(source, source.length);
int[][] viaArrayCopy = new int[source.length][];
System.arraycopy(source, 0, viaArrayCopy, 0, source.length);

System.out.println(source[0] == viaCopyOf[0]);    // true
System.out.println(source[0] == viaArrayCopy[0]); // true
```

Đoạn trên cần `import java.util.Arrays;`; kết quả hai lệnh `println` đều là `true`.

Cũng nên phân biệt với các phương thức hỗ trợ xem hoặc so nội dung:

- `Arrays.toString(int[])` hiển thị một chiều, ví dụ `[1, 2]`.
- `Arrays.deepToString(Object[])` hữu ích để hiển thị mảng nhiều chiều, ví dụ `[[1, 2], [3, 4]]`.
- `Arrays.deepEquals(Object[], Object[])` so sánh đệ quy **nội dung các mảng lồng nhau**; **không cho biết** hai mảng có dùng chung các mảng con hay không.
- `Arrays.equals(Object[], Object[])` trên `int[][]` so các phần tử hàng theo `equals`; vì mảng `int[]` thông thường không định nghĩa equality theo nội dung, hai hàng khác tham chiếu sẽ không bằng nhau theo phép so này dù chứa cùng các số. Dùng `Arrays.deepEquals` khi cần so cấu trúc mảng nhiều chiều theo giá trị phần tử.

## 8. Kiểm tra alias đúng cách

Khi cần xác minh bản sao có thật sự độc lập hay không, hãy kiểm tra **tham chiếu** và thực hiện một phép sửa có kiểm soát, thay vì chỉ kiểm tra nội dung in ra:

```java
int[][] a = {{1, 2}};
int[][] b = a.clone();

System.out.println(a == b);       // false: khác mảng ngoài
System.out.println(a[0] == b[0]); // true: cùng mảng con
```

Đây là đoạn minh họa chạy trong một phương thức Java.

Nếu sau này chỉ thay `b[0]` bằng một `int[]` khác, `a[0]` không tự thay theo. Nhưng khi thay `b[0][0]`, bạn sửa object hàng vốn đang được dùng chung.

**Cẩn trọng khi nhiều luồng cùng truy cập:** vì clone nông có thể chia sẻ các phần tử mutable, hai mảng khác tham chiếu không có nghĩa là hai luồng đã được cách ly. Việc tạo một bản clone cũng không cung cấp đồng bộ hóa hoặc snapshot nguyên tử khi luồng khác đang sửa dữ liệu đồng thời.

## 9. Khi nào shallow copy đủ và khi nào cần deep copy?

**Shallow copy đủ** trong các trường hợp như:

- Mảng một chiều chứa giá trị nguyên thủy.
- Mảng chứa các object bất biến như `String`, khi ứng dụng chỉ cần tách các **ô tham chiếu** và không cần nhân bản object bất biến.
- Ứng dụng chủ ý chia sẻ các phần tử để tránh sao chép bộ nhớ, với hợp đồng rõ ràng về quyền sửa.

**Cần copy thêm các cấp** khi:

- Muốn sửa một ô trong mảng con mà **không làm thay đổi mảng gốc**.
- Muốn truyền dữ liệu cho thành phần khác mà không cho phép thành phần đó sửa các hàng gốc.
- Muốn tạo snapshot độc lập của một cấu trúc lồng nhau có thể thay đổi, với định nghĩa rõ về tính độc lập và các quan hệ tham chiếu cần bảo toàn.

Với mảng các object có thể thay đổi, clone mảng chỉ là **bước đầu**. Cần biết cách copy bản thân các object; không phải object nào cũng có `clone()`, và gọi clone đệ quy không tự động xử lý vòng tham chiếu hoặc quan hệ chia sẻ.

Nếu được thiết kế API mới, các factory, copy constructor hoặc phương thức `copy()` mô tả rõ shallow/deep semantics thường dễ hiểu hơn việc mặc định rằng `clone()` sẽ “copy hết”.

## 10. Lỗi phổ biến và cách tránh

| Nhầm lẫn | Hệ quả | Cách xử lý |
| --- | --- | --- |
| “Gọi `clone()` sẽ nhân bản mọi mảng con.” | Sửa `copy[0][0]` làm dữ liệu gốc thay đổi | Với `int[][]`, clone mảng ngoài rồi clone từng hàng |
| “Hai mảng ngoài khác nhau thì không chia sẻ dữ liệu.” | Hai mảng vẫn có thể dùng chung cùng hàng hoặc object phần tử | Kiểm tra `original[i] == copy[i]` |
| “`Arrays.copyOf` là deep copy.” | Vẫn chia sẻ các tham chiếu hàng | Xác định độ sâu cần sao chép, copy từng cấp |
| “`Arrays.deepEquals` chứng minh deep copy.” | Hai cấu trúc có thể bằng nội dung dù đang alias cùng mảng con | Kiểm tra identity và thử sửa độc lập |
| “`int[][]` luôn là hình chữ nhật.” | Dễ gặp `NullPointerException` hoặc sai chỉ số | Hỗ trợ hàng `null`/khác độ dài theo dữ liệu thật |
| “Clone `Person[][]` tới hai cấp thì Person cũng được clone.” | Các object `Person` vẫn được tham chiếu chung | Copy object phần tử bằng chính sách riêng |
| “Có `Cloneable` là gọi `clone()` công khai được.” | Với lớp thông thường, `Object.clone()` là `protected` | Dùng API sao chép do lớp công bố; mảng là trường hợp đặc biệt |
| “Cứ clone từng hàng là bảo toàn mọi quan hệ object.” | Các hàng vốn alias nhau có thể không còn alias | Khi cần bảo toàn alias/cycle, dùng thuật toán sao chép đồ thị thích hợp |

## 11. Checklist thực hành

1. **Xác định cấp sao chép cần thiết:** chỉ mảng ngoài, mọi mảng con hay cả object phần tử?
2. **Nhìn vào kiểu phần tử của từng cấp:** `int` là giá trị nguyên thủy; `int[]` và `Person` đều là đối tượng được trỏ tới bằng tham chiếu.
3. **Đối với mảng nhiều chiều, nhớ rằng `clone()` chỉ sao chép mảng đầu tiên.**
4. **Kiểm tra `null` và mảng ragged:** không giả định mọi hàng đều có cùng độ dài hoặc khác `null`.
5. **Kiểm thử cả sửa nội dung và gán lại tham chiếu:** `copy[i][j] = value` khác `copy[i] = newRow`.
6. **Phân biệt `==` và `Arrays.deepEquals`:** phép đầu kiểm tra cùng object; phép sau kiểm tra giá trị lồng nhau.
7. **Giữ hợp đồng ownership rõ ràng:** bên nhận có được sửa object/mảng con được chia sẻ không?
8. **Nếu cần bản sao đồ thị**, thiết kế cách xử lý chia sẻ tham chiếu, chu trình và trạng thái mutable trước khi code.

**Tự kiểm tra:**

- `int[] a = {1}; int[] b = a.clone(); b[0] = 2;` thì `a[0]` là bao nhiêu? **1**, vì hai mảng nguyên thủy riêng biệt.
- `int[][] a = {{1}}; int[][] b = a.clone(); b[0][0] = 2;` thì `a[0][0]` là bao nhiêu? **2**, vì hai mảng ngoài cùng giữ tham chiếu tới mảng con.
- `b[0] = new int[] {9};` sau khi clone mảng ngoài có làm `a[0]` trỏ đến mảng mới không? **Không**, vì chỉ thay ô của mảng ngoài `b`.
- `Arrays.deepEquals(a, b)` trả `true` thì có chắc `a` và `b` độc lập không? **Không**, phép so chỉ xét nội dung.
- Với `int[][]`, muốn sửa hàng của bản sao mà không sửa hàng gốc cần gì? **Sao chép mảng ngoài và từng hàng `int[]` không null**.

**Ghi nhớ:** Mảng Java có `clone()` tiện lợi, nhưng **clone mảng chỉ sao chép một cấp**. Một `int[][]` là một mảng chứa tham chiếu tới các mảng `int[]`, vì vậy clone mảng ngoài vẫn dùng chung các hàng. Muốn bản sao độc lập ở cấp nào, hãy sao chép rõ ràng ở cấp đó.

</details>

- [Quay lại đầu trang](#back-to-top)
