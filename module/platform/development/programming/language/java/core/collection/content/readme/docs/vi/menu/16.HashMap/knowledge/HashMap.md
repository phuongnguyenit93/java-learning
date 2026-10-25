<a id="back-to-top"></a>

# HashMap

## Menu
- [Bảng băm, bucket và quy trình tìm khóa](#hash-map-001)
- [Phối hợp hashCode và equals khi put, get, remove](#hash-map-002)
- [Capacity, load factor, ngưỡng mở rộng và rehash](#hash-map-003)
- [Hiệu năng mong đợi, trường hợp xấu và chất lượng hàm băm](#hash-map-004)
- [Va chạm, cây hóa bucket và giới hạn của chi tiết cài đặt](#hash-map-005)
- [Chính sách một khóa null và nhiều giá trị null](#hash-map-006)
- [Vì sao thứ tự duyệt quan sát được không phải hợp đồng](#hash-map-007)
- [Ước lượng kích thước ban đầu và chi phí duyệt theo capacity](#hash-map-008)
- [Lỗi khóa khả biến và rủi ro cập nhật đồng thời](#hash-map-009)

## <a id="hash-map-001">Bảng băm, bucket và quy trình tìm khóa</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng băm, bucket và quy trình tìm khóa

## 1. HashMap giải quyết bài toán tra cứu như thế nào?

Giả sử cần tra tên sản phẩm từ mã sản phẩm. Với một danh sách các cặp, cách đơn giản là đi lần lượt cho tới khi gặp mã phù hợp. Nếu dữ liệu lớn và phải tra nhiều lần, việc luôn bắt đầu từ đầu danh sách sẽ tốn công.

Bảng băm dùng một thông tin tính từ khóa để thu hẹp nơi cần tìm. Thay vì xét tất cả ánh xạ, nó chọn một vùng chứa ứng viên rồi chỉ kiểm tra các khóa trong vùng đó.

Trong `HashMap`, vùng đó thường được gọi là **bucket**: một vị trí trong bảng chứa các ánh xạ cùng được phân vào vị trí ấy. Có thể hình dung bucket như một ngăn lưu hồ sơ, còn mã băm là dữ liệu giúp chọn ngăn. Tới đúng ngăn vẫn phải kiểm tra đúng hồ sơ; số ngăn không thay thế cho danh tính khóa.

`HashMap` là một triển khai Map dựa trên bảng băm. Hiệu quả tra cứu phụ thuộc vào việc các khóa được phân bố phù hợp giữa các bucket. Nó không bảo đảm thứ tự chèn hoặc thứ tự duyệt ổn định. [Tài liệu HashMap, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html).

Sau bài học, bạn cần giải thích được đường đi:

~~~text
khóa cần tìm
    -> mã băm từ khóa
    -> mã băm được xử lý nội bộ
    -> chỉ số bucket
    -> kiểm tra ứng viên trong bucket
    -> tìm đúng khóa hoặc kết luận không có.
~~~

Trọng tâm không phải học thuộc một công thức rồi tự tính bucket trong mã ứng dụng. Khi dùng HashMap, ứng dụng vẫn gọi `put`, `get` hoặc `containsKey`; hiểu cơ chế giúp thiết kế khóa đúng và chẩn đoán tra cứu sai.

## 2. Những khái niệm cần tách riêng

| Khái niệm | Ý nghĩa |
| --- | --- |
| Khóa (key) | Đối tượng dùng để xác định ánh xạ muốn tra |
| Giá trị (value) | Dữ liệu gắn với khóa |
| Mã băm (hash code) | Một số nguyên do `hashCode()` của khóa cung cấp |
| Mã băm nội bộ | Số được HashMap xử lý để sử dụng trong bảng |
| Dung lượng bảng (capacity) | Số vị trí bucket của bảng |
| Chỉ số bucket | Vị trí cụ thể được chọn trong bảng |
| Nút (node) | Đơn vị giữ một ánh xạ và thông tin phục vụ tìm kiếm |
| Số ánh xạ (size) | Số cặp khóa–giá trị đang được lưu |

Dung lượng không đồng nghĩa số ánh xạ. Một bảng có 16 bucket có thể mới chứa bốn ánh xạ. Bốn ánh xạ đó có thể nằm ở bốn bucket khác nhau hoặc cùng dồn vào một bucket.

Mã băm cũng không phải chỉ số bucket. `hashCode()` trả một giá trị `int`, còn bảng chỉ có một số hữu hạn vị trí. Cần một bước ánh xạ mã băm vào phạm vi chỉ số hợp lệ.

Trong OpenJDK 17, một nút thông thường giữ mã băm nội bộ đã tính, khóa, giá trị và một liên kết tới nút kế tiếp khi bucket có chuỗi nút. Những bucket được chuyển sang dạng cây dùng một loại nút có thêm thông tin cây. Đây là mô hình triển khai được dùng để giải thích cơ chế, không phải các trường công khai để mã ứng dụng truy cập.

Đừng nhầm HashMap với một mảng có thể truy cập bằng khóa số nguyên. Khóa `Integer.valueOf(100)` không bắt buộc được lưu ở vị trí 100, và HashMap không tạo tự động 101 phần tử chỉ vì có khóa số 100.

## 3. hashCode và equals có trách nhiệm khác nhau

Mã băm giúp chọn và lọc nơi tìm. `equals` giúp xác định hai đối tượng có được coi là cùng khóa theo cách định nghĩa dữ liệu hay không.

Hợp đồng cần giữ:

- Nếu hai đối tượng bằng nhau theo `equals`, chúng phải có cùng `hashCode`.
- Hai đối tượng có cùng mã băm vẫn có thể không bằng nhau.
- Những thông tin ảnh hưởng tới phép so bằng và mã băm cần được dùng nhất quán.

Hợp đồng không yêu cầu mọi đối tượng khác nhau có mã băm khác nhau. Có thể có nhiều khóa khác nhau cùng một mã băm; cấu trúc bảng băm phải xử lý trường hợp này. [Hợp đồng Object.hashCode và Object.equals, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html).

Ví dụ minh họa:

~~~text
Khóa A: hashCode = 1
Khóa C: hashCode = 1
A.equals(C) = false

Kết luận: A và C là hai khóa khác nhau, dù trùng mã băm.
~~~

Ngược lại, một đối tượng mới biểu diễn cùng dữ liệu khóa với đối tượng đã lưu phải được tra được nếu equals/hashCode được cài đúng và thông tin khóa không bị thay đổi.

Không cần cùng tham chiếu đối tượng để tìm cùng một khóa. Phép `==` có thể giúp nhận ra nhanh trường hợp đúng cùng đối tượng, nhưng nó không thay thế cho khái niệm bằng nhau theo dữ liệu.

Mã băm không phải mã định danh duy nhất, không phải thứ tự sắp xếp và không phải giá trị mã hóa bảo mật. Một mã băm bằng 0 cũng không có nghĩa khóa không tồn tại.

## 4. Từ mã băm của khóa tới mã nội bộ trong OpenJDK 17

Phần chi tiết bên dưới được đối chiếu với `HashMap` của OpenJDK 17. Công thức, bố trí nút và các tối ưu có thể thay đổi giữa triển khai hoặc phiên bản; hợp đồng về tra khóa đúng theo equals/hashCode mới là điều mã ứng dụng cần dựa vào. [Mã nguồn HashMap, OpenJDK 17](https://raw.githubusercontent.com/openjdk/jdk/jdk-17%2B35/src/java.base/share/classes/java/util/HashMap.java).

Với một khóa khác null, mô hình xử lý mã băm là:

~~~java
int mixed = rawHash ^ (rawHash >>> 16);
~~~

Trong đó:

- `rawHash` là mã do `key.hashCode()` cung cấp.
- `>>> 16` dịch phải không dấu 16 bit, đưa phần bit cao xuống thấp.
- `^` là phép XOR.
- `mixed` là mã được dùng tiếp trong cơ chế bảng.

Vì chỉ số bucket ở dung lượng lũy thừa của hai phụ thuộc một số bit thấp, việc trộn phần bit cao xuống giúp những khác biệt ở bit cao có thể tham gia chọn bucket. Nó không tạo ra một mã duy nhất cho mọi khóa và không sửa được một hàm hashCode luôn trả cùng số cho tất cả đối tượng.

Ví dụ:

~~~text
rawHash = 65536 = 0x00010000
rawHash >>> 16 = 1
mixed = 65536 XOR 1 = 65537 = 0x00010001
~~~

Nếu chỉ xét bốn bit thấp của rawHash thì chúng đều là 0; sau bước trộn, bốn bit thấp có thêm thông tin từ bit cao.

Với khóa null, OpenJDK xử lý mã nội bộ là 0, không gọi `hashCode()` trên null. Điều này giúp HashMap hỗ trợ khóa null nhưng không có nghĩa bucket 0 chỉ dành riêng cho null. Những khóa khác có mã nội bộ đưa về chỉ số 0 vẫn có thể cùng bucket.

Mã băm có thể âm. Đây không phải một tình trạng lỗi; toàn bộ 32 bit của int vẫn có thể tham gia cơ chế chọn vị trí.

## 5. Chọn bucket bằng dung lượng hiện tại

Trong mô hình OpenJDK 17, khi dung lượng bảng n là một lũy thừa của hai, chỉ số được tính bằng:

~~~java
int index = mixedHash & (n - 1);
~~~

Đây là phép AND bit, không phải toán tử logic `&&`. Với n = 16:

~~~text
n - 1 = 15 = 0b1111

mixedHash & 15 giữ lại bốn bit thấp.
Kết quả nằm từ 0 tới 15.
~~~

Ví dụ:

| Mã hashCode ban đầu | Mã sau trộn | Bucket nếu n = 16 | Bucket nếu n = 32 |
| --- | --- | --- | --- |
| 1 | 1 | 1 | 1 |
| 17 | 17 | 1 | 17 |
| 65536 | 65537 | 1 | 1 |
| 2 | 2 | 2 | 2 |
| -1 | -65536 | 0 | 0 |

Hai điểm rút ra từ bảng:

**Cùng bucket không nhất thiết cùng mã băm.** Mã nội bộ 1, 17 và 65537 đều vào bucket 1 khi n = 16.

**Bucket phụ thuộc dung lượng.** Mã 17 ở bucket 1 với n = 16 nhưng chuyển sang bucket 17 khi n = 32. Không thể xem chỉ số bucket là một thuộc tính cố định của khóa.

Với -1, phép dịch không dấu đưa 16 bit 1 xuống thành 65535; XOR cho kết quả -65536. AND với 15 hoặc 31 vẫn cho chỉ số hợp lệ bằng 0. Không cần gọi Math.abs để xử lý số âm trong công thức này.

Không thay công thức trên bằng `Math.abs(hashCode) % n` rồi cho rằng đã mô tả chính xác OpenJDK. Đó là một thuật toán khác, và `Math.abs(Integer.MIN_VALUE)` vẫn là số âm do miền int không chứa đối số dương tương ứng. Khi dùng HashMap thật, hãy để chính triển khai tính vị trí.

Hàm `bucket` trong chương trình chỉ mô phỏng công thức cho các dung lượng 16 và 32 được chọn sẵn. Nó kiểm tra điều kiện lũy thừa của hai và không dùng phản chiếu để đọc bảng riêng tư của đối tượng HashMap.

## 6. Va chạm: tới đúng bucket vẫn chưa tìm đúng khóa

Giả sử bốn khóa được thêm theo thứ tự A, B, C, D:

~~~text
A: rawHash 1      -> mixed 1
B: rawHash 17     -> mixed 17
C: rawHash 1      -> mixed 1
D: rawHash 65536  -> mixed 65537
~~~

Với dung lượng 16, cả bốn cùng vào bucket 1. Một sơ đồ chuỗi nút tương ứng cho ví dụ nhỏ:

~~~text
table[0]  -> rỗng
table[1]  -> [hash=1, A, alpha]
            -> [hash=17, B, beta]
            -> [hash=1, C, gamma]
            -> [hash=65537, D, delta]
table[2]  -> rỗng
...
table[15] -> rỗng
~~~

Sơ đồ diễn giải dạng chuỗi của ví dụ khi đối chiếu triển khai OpenJDK; nó không phải một bản in trực tiếp các trường riêng tư. HashMap không có cam kết API về thứ tự chèn của mọi chuỗi nội bộ hoặc thứ tự duyệt.

Có thể phân biệt hai mức va chạm:

1. Khác mã nội bộ nhưng rơi vào cùng bucket, như A và B.
2. Cùng toàn bộ mã nội bộ nhưng khóa không bằng nhau, như A và C.

Mức thứ nhất có thể loại ứng viên sớm bằng cách so mã nội bộ. Mức thứ hai còn phải phân biệt bằng khóa. Bởi vậy, chỉ số bucket bằng nhau chưa đủ và ngay cả mã băm bằng nhau cũng chưa đủ để kết luận đã tìm được ánh xạ.

HashMap không ghi đè mọi khóa chung bucket. Một ánh xạ chỉ được thay giá trị khi khóa được nhận diện là bằng khóa đã có. Nếu A và C khác nhau theo equals thì chúng cùng tồn tại dù mã băm giống hệt.

## 7. Quy trình get(key) trên một bucket dạng chuỗi

Có thể đọc quy trình theo những bước sau:

1. Xác định bảng; nếu đã có bảng với dung lượng hợp lệ, tính vị trí cần tìm.
2. Lấy nút đầu của bucket được chọn.
3. Nếu bucket rỗng, kết luận không tìm thấy khóa ở đây.
4. Nếu có nút, so mã băm nội bộ lưu trong nút với mã của khóa đang dò.
5. Chỉ khi mã phù hợp mới xét cùng tham chiếu hoặc so bằng khóa.
6. Nếu nút hiện tại chưa khớp, xét các ứng viên tiếp theo trong bucket.
7. Tìm được nút thì trả giá trị của nó; hết ứng viên mà không khớp thì trả null.

Cách kiểm tra khóa trong một nút có hai đường chính:

~~~text
mã nội bộ trùng
    và
    (khóa lưu cùng tham chiếu với khóa dò
     hoặc khóa dò khác null và equals với khóa lưu)
~~~

Đó là mô hình logic, không yêu cầu ứng dụng gọi equals theo một số lần cố định. HashMap có quyền tránh phép so bằng khi một bước lọc đã loại ứng viên.

### Tìm lại đúng đối tượng A đã lưu

Mã nội bộ phù hợp và tham chiếu khóa trùng ngay ở nút đầu. Không cần gọi A.equals(A) để biết đây là cùng đối tượng.

### Tìm một đối tượng A mới nhưng bằng khóa đã lưu

Hai tham chiếu khác nhau, mã băm trùng, nên phải dùng equals để xác định cùng khóa. Kết quả vẫn là alpha nếu hợp đồng khóa đúng.

### Tìm C bằng một đối tượng mới

Nút A có cùng mã nội bộ 1 nhưng equals trả false. Nút B có mã nội bộ 17, khác 1, nên không cần gọi equals trên B. Nút C có mã 1 và khóa bằng khóa dò, nên tìm được gamma.

### Không tìm thấy nhưng bucket vẫn có dữ liệu

Với khóa X có mã nội bộ 1 và khác A, C, thuật toán phải bác bỏ các ứng viên có mã phù hợp. Với khóa Z có mã 33, bucket 1 vẫn chứa dữ liệu nhưng không nút nào có mã 33, nên có thể kết luận không khớp mà không gọi equals cho các khóa đó.

Bởi vậy, dấu vết “không có equals được gọi” không chứng minh bucket chắc chắn rỗng. Nó cũng có thể có những nút bị loại ngay từ mã băm.

### Nếu bucket là cây

Một số bucket có thể được chuyển sang dạng cây thay vì luôn duyệt như chuỗi. Trong trường hợp đó, tìm kiếm đi theo cơ chế của nút cây. Không mô tả mọi HashMap như một mảng của danh sách mãi mãi, và không dùng dấu vết chuỗi của ví dụ nhỏ như cam kết cho bucket đã chuyển dạng.

## 8. Chương trình Java 17+ quan sát hashCode và equals

Tệp `HashMapLookupDemo.java` dùng một record Key bất biến. Trong ví dụ học cơ chế, cả `id` lẫn `rawHash` là dữ liệu tham gia equals; hashCode trả trường rawHash được cố ý chọn để tạo các trường hợp va chạm.

Cách thiết kế này giữ hợp đồng: nếu hai Key bằng nhau thì rawHash của chúng bằng nhau. Những id khác nhau vẫn có thể cùng rawHash. Nó là công cụ thí nghiệm, không phải gợi ý đưa một “mã băm do người dùng nhập” vào mọi khóa nghiệp vụ.

Danh sách `events` chỉ ghi lại các lời gọi trong một chương trình một luồng. Việc ghi dấu vết không tham gia quyết định equals; đây không phải một cách viết nhật ký dùng chung giữa nhiều luồng.

~~~java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class HashMapLookupDemo {
    private static final List<String> events = new ArrayList<>();

    record Key(String id, int rawHash) {
        Key {
            Objects.requireNonNull(id, "id");
        }

        @Override
        public int hashCode() {
            events.add("hash(" + id + ")=" + rawHash);
            return rawHash;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key that)) {
                return false;
            }
            events.add("equals(" + id + "," + that.id + ")");
            return rawHash == that.rawHash && id.equals(that.id);
        }
    }

    static int spread(int rawHash) {
        return rawHash ^ (rawHash >>> 16);
    }

    static int bucket(int mixedHash, int capacity) {
        if (capacity <= 0 || (capacity & (capacity - 1)) != 0) {
            throw new IllegalArgumentException("capacity must be a power of two");
        }
        return mixedHash & (capacity - 1);
    }

    static void lookup(Map<Key, String> map, String label, Key key) {
        events.clear();
        String value = map.get(key);
        System.out.println(label + " get=" + value + " events=" + events);
    }

    public static void main(String[] args) {
        for (int raw : new int[] {1, 17, 65536, 2, -1}) {
            int mixed = spread(raw);
            System.out.println("raw=" + raw + " mixed=" + mixed
                    + " bucket16=" + bucket(mixed, 16)
                    + " bucket32=" + bucket(mixed, 32));
        }

        Key a = new Key("A", 1);
        Key b = new Key("B", 17);
        Key c = new Key("C", 1);
        Key d = new Key("D", 65536);
        Map<Key, String> map = new HashMap<>(16);
        map.put(a, "alpha");
        map.put(b, "beta");
        map.put(c, "gamma");
        map.put(d, "delta");

        System.out.println("initial size=" + map.size());
        lookup(map, "same reference A", a);
        lookup(map, "equal new A", new Key("A", 1));
        lookup(map, "equal new C", new Key("C", 1));
        lookup(map, "missing same hash X", new Key("X", 1));
        lookup(map, "missing other hash Z", new Key("Z", 33));
        lookup(map, "empty bucket E", new Key("E", 2));

        String previous = map.put(new Key("B", 17), "beta2");
        System.out.println("replace B previous=" + previous
                + " size=" + map.size());
        lookup(map, "B after replacement", new Key("B", 17));

        map.put(null, "nil");
        lookup(map, "null key", null);
        System.out.println("size with null key=" + map.size());

        map.put(new Key("C", 1), null);
        String cValue = map.get(new Key("C", 1));
        boolean cPresent = map.containsKey(new Key("C", 1));
        String missingValue = map.get(new Key("missing", 1));
        boolean missingPresent = map.containsKey(new Key("missing", 1));
        System.out.println("C value=" + cValue + " present=" + cPresent);
        System.out.println("missing value=" + missingValue
                + " present=" + missingPresent);

        Key aCopy = new Key("A", 1);
        Key cCopy = new Key("C", 1);
        events.clear();
        System.out.println("A equals new A=" + a.equals(aCopy));
        System.out.println("A equals C=" + a.equals(cCopy));
        System.out.println("A raw hash equals C raw hash="
                + (a.rawHash() == c.rawHash()));
    }
}
~~~

Biên dịch và chạy:

~~~shell
javac --release 17 -encoding UTF-8 HashMapLookupDemo.java
java HashMapLookupDemo
~~~

Dùng JDK 17 hoặc mới hơn hỗ trợ phiên bản đích 17. Chương trình tương thích API và cú pháp Java 17. Dấu vết dưới đây được đối chiếu với đường tìm chuỗi trong mã nguồn OpenJDK 17; chi tiết số lần gọi không phải một lời hứa chung cho mọi triển khai hoặc phiên bản HashMap.

Stdout chính xác:

~~~text
raw=1 mixed=1 bucket16=1 bucket32=1
raw=17 mixed=17 bucket16=1 bucket32=17
raw=65536 mixed=65537 bucket16=1 bucket32=1
raw=2 mixed=2 bucket16=2 bucket32=2
raw=-1 mixed=-65536 bucket16=0 bucket32=0
initial size=4
same reference A get=alpha events=[hash(A)=1]
equal new A get=alpha events=[hash(A)=1, equals(A,A)]
equal new C get=gamma events=[hash(C)=1, equals(C,A), equals(C,C)]
missing same hash X get=null events=[hash(X)=1, equals(X,A), equals(X,C)]
missing other hash Z get=null events=[hash(Z)=33]
empty bucket E get=null events=[hash(E)=2]
replace B previous=beta size=4
B after replacement get=beta2 events=[hash(B)=17, equals(B,B)]
null key get=nil events=[]
size with null key=5
C value=null present=true
missing value=null present=false
A equals new A=true
A equals C=false
A raw hash equals C raw hash=true
~~~

### Cách đọc bằng chứng

Năm dòng đầu là kết quả tính mô hình bucket cho hai dung lượng, không phải đọc dung lượng thật của Map bằng một API công khai.

`same reference A` chỉ ghi một lời gọi hashCode. `equal new A` còn có một phép equals. Cặp số hiệu A trong `equals(A,A)` là hai id cùng chữ A, không khẳng định hai đối tượng cùng tham chiếu.

`equal new C` xét equals với A rồi C; B bị loại bằng mã băm khác. `missing other hash Z` không có equals dù bucket mô hình cùng là 1. `empty bucket E` cũng không có equals nhưng vì một lý do khác: vị trí chọn không có ứng viên.

`replace B` trả giá trị cũ beta và kích thước vẫn bằng bốn, xác nhận thay giá trị theo khóa bằng nhau không thêm một khóa trùng. Khóa null được tìm mà không gọi phương thức trên một đối tượng Key nào. Hai dòng có giá trị null cuối cho thấy cần phân biệt “tìm được nút chứa null” với “không tìm được nút”.


## 9. Null trong kết quả tìm kiếm

Trên đường `get(key)`, có hai kết quả nội bộ khác nhau: không tìm thấy node phù hợp, hoặc tìm thấy node có giá trị `null`. API `get` đều có thể trả `null` cho cả hai. Khi cần biết khóa có mặt, dùng `containsKey` thay vì suy luận chỉ từ `get`.

~~~text
C → null, đã có trong Map       get(C) → null; containsKey(C) → true
X không có trong Map             get(X) → null; containsKey(X) → false
~~~

Khóa `null` cũng được `HashMap` hỗ trợ: triển khai OpenJDK chọn mã băm nội bộ 0 để định vị, nhưng vẫn phải kiểm tra đúng khóa trong bucket. Quy tắc chỉ một khóa `null`, nhiều giá trị `null` và hành vi của các phương thức cập nhật là trọng tâm của bài riêng về chính sách null.

## 10. Khóa biến đổi có thể làm hỏng đường tìm

Khi thêm ánh xạ, node lưu mã băm đã tính; khi tìm lại, `HashMap` tính mã từ khóa truy vấn. Nếu những trường tham gia `hashCode` hoặc `equals` bị sửa trong lúc đối tượng làm khóa, đường tìm có thể không nhận diện được ánh xạ cũ.

Ví dụ trong mô hình OpenJDK với bảng 16 bucket: node đã lưu mã 1, sau khi sửa khóa mã truy vấn thành 17. Cả hai vẫn chọn bucket 1, nhưng mã lưu và mã dò khác nhau nên ngay cả cùng tham chiếu khóa cũng có thể bị loại trước phép so bằng. Không dựa vào một kết quả lỗi cụ thể của khóa đã vi phạm điều kiện; hãy thiết kế dữ liệu định danh ổn định. Bài về khóa khả biến phân tích các lỗi và cách khắc phục kỹ hơn.

## 11. Resize làm thay đổi vị trí bucket

Chỉ số bucket phụ thuộc vào dung lượng của bảng hiện tại. Ví dụ mã nội bộ 17 chọn bucket `17 & 15 = 1` khi dung lượng 16 nhưng chọn `17 & 31 = 17` khi dung lượng 32. Khi bảng tăng kích thước, triển khai phải bố trí lại các ánh xạ; chương trình ứng dụng không được lưu chỉ số bucket tự tính như một định danh cố định.

Tăng dung lượng có thể tách các khóa có mã khác nhau đang cùng bucket, nhưng không tự tách những khóa có mã băm nội bộ giống hệt. Cơ chế ngưỡng mở rộng, chia bucket và chi phí resize được triển khai chi tiết ở bài capacity/load factor.

## 12. Chi phí của một lần tìm

Với phân bố mã băm thích hợp và chi phí `hashCode`/`equals` hợp lý, tra cứu có hiệu năng kỳ vọng hằng số. Điều đó không hứa mọi lời gọi `get` thực hiện đúng một phép so hay mất thời gian bằng nhau.

~~~text
chi phí tìm = tính hashCode + chọn bucket + xét ứng viên + so equals khi cần
~~~

Bucket chuỗi có L node có thể cần xét đến L ứng viên; mã băm khác nhau có thể bị loại trước `equals`. Trong bucket cây, đường tìm còn phụ thuộc điều kiện của triển khai và khả năng phân biệt khóa. Ví dụ đếm lời gọi ở trên chỉ chứng minh một dấu vết cụ thể, không phải phép đo thời gian. Các bài hiệu năng và cây hóa bucket sẽ phân tích sâu những giới hạn này.

## 13. Chẩn đoán sai lệch trong đường tìm

| Quan sát hoặc suy luận sai | Kiểm tra chính xác hơn |
| --- | --- |
| `hashCode` chính là bucket | Bước trộn mã và dung lượng bảng còn quyết định chỉ số |
| Hai khóa cùng bucket phải cùng ánh xạ | So tiếp mã nội bộ và khóa theo `equals` |
| Cùng `hashCode` thì `equals` phải true | Khóa khác nhau được phép va chạm |
| Không thấy gọi `equals` nghĩa là bucket rỗng | Ứng viên có thể bị loại vì mã băm khác |
| Dùng chính tham chiếu khóa đã sửa sẽ luôn `get` được | Mã băm lưu trong node có thể khác mã mới |
| `get` bằng `null` là chắc chắn không có khóa | `containsKey` phân biệt ánh xạ chứa null với vắng mặt |

Khi một khóa đáng lẽ có nhưng không được tìm thấy, hãy kiểm tra định nghĩa danh tính, tính nhất quán `equals`/`hashCode`, việc thay đổi khóa sau khi chèn và sự khác nhau giữa `null` với vắng mặt. Không cần phản chiếu đọc trường private để chẩn đoán các lỗi này.

## 14. Bài tập và lời giải

### Bài 1 — Phân biệt size và capacity

Một HashMap có bốn ánh xạ, bảng mô hình có dung lượng 16. Có thể kết luận có 12 bucket rỗng không?

**Lời giải:** không. Bốn ánh xạ có thể rơi vào cùng bucket hoặc nhiều bucket khác nhau. Nếu ở bốn bucket khác nhau mới có 12 vị trí rỗng; nếu cả bốn ở một bucket thì có 15 vị trí rỗng. Size đếm ánh xạ, không đếm bucket đã dùng.

### Bài 2 — Tính chỉ số

Mã nội bộ là 17. Với dung lượng 16 và 32, chỉ số trong mô hình OpenJDK được tính ra bao nhiêu?

**Lời giải:** `17 & 15 = 1` và `17 & 31 = 17`. Cùng mã không có nghĩa chỉ số cố định khi dung lượng đổi.

### Bài 3 — Cùng bucket, khác mã

A có mixedHash 1, B có mixedHash 17. Ở bảng 16 bucket, chúng cùng bucket nào? Khi tìm một khóa có mã 1, có bắt buộc gọi equals với B không?

**Lời giải:** cùng bucket 1. Không bắt buộc gọi equals với B trong đường tìm đã mô tả, vì mã 17 lưu trong nút B khác mã 1 đang tìm và có thể loại B sớm.

### Bài 4 — Cùng mã, khác khóa

A và C cùng hashCode 1 nhưng equals false. Thêm A rồi C có làm giá trị của A bị thay không?

**Lời giải:** không chỉ vì mã băm giống nhau. Chúng là hai khóa khác nhau theo hợp đồng đã chọn nên có thể cùng tồn tại. Chỉ khi khóa được nhận diện là bằng nhau thì put mới thay giá trị cho cùng ánh xạ.

### Bài 5 — Đọc dấu vết tìm C

Vì sao chương trình in `equals(C,A)` rồi `equals(C,C)` mà không có `equals(C,B)`?

**Lời giải:** A và C có mã nội bộ 1 nên cần so khóa; B có mã 17 nên bị loại trước khi gọi equals. Hai chữ C trong dấu vết thứ hai là id giống nhau của hai đối tượng khác tham chiếu.

### Bài 6 — Không có equals có chứng minh không có ứng viên?

Tìm Z có mã 33, cùng bucket mô hình với A/B/C/D ở dung lượng 16, nhưng nhật ký chỉ có hashCode. Có phải bảng hỏng không?

**Lời giải:** không. Bucket có nút nhưng không nút nào giữ mã nội bộ 33, nên các nút có thể bị loại mà không cần equals. Đường tìm hoạt động đúng theo bộ lọc mã băm.

### Bài 7 — Khóa mới bằng khóa cũ

Đã lưu Key("A", 1). Dùng một đối tượng mới Key("A", 1) để get có cần cùng địa chỉ tham chiếu không?

**Lời giải:** không. Hai Key bằng nhau theo equals và có mã nhất quán; HashMap có thể tìm qua bước so bằng khi hai tham chiếu khác nhau. Dùng == để quyết định khóa có tra được hay không sẽ bỏ qua cơ chế này.

### Bài 8 — Hai kết quả null

C đang ánh xạ tới null, X vắng mặt. Cả get(C) và get(X) đều trả null. Kết quả nào giúp phân biệt và điều này liên hệ gì với nút nội bộ?

**Lời giải:** containsKey(C) là true, containsKey(X) là false khi nguồn không đổi. C tìm được một nút có value null; X không có nút khớp.

### Bài 9 — Hash âm và Math.abs

Có cần chuyển mã băm âm thành dương trước khi dùng HashMap không? Vì sao không tự mô phỏng bằng Math.abs rồi chia dư?

**Lời giải:** không. HashMap xử lý int theo cơ chế nội bộ; mặt nạ với n - 1 tạo chỉ số hợp lệ trong mô hình này. Math.abs rồi chia dư là công thức khác, và Math.abs của Integer.MIN_VALUE vẫn âm.

### Bài 10 — Khóa bị thay đổi nhưng bucket chưa đổi

Khóa lúc thêm có mã nội bộ 1; sau đó dữ liệu khóa bị sửa làm mã thành 17. Cả hai mã vẫn vào bucket 1 ở dung lượng 16. Có chắc get sẽ tìm thấy vì cùng bucket không?

**Lời giải:** không. Nút còn giữ mã 1 có thể bị loại bởi mã 17 của lần dò trước khi xét cùng tham chiếu hoặc equals. Đây là một lý do tránh khóa thay đổi, không phải một tình trạng hợp lệ để ứng dụng dựa vào kết quả cụ thể.

### Bài 11 — Tăng bảng và mã băm giống hệt

Có rất nhiều khóa khác nhau nhưng tất cả hashCode đều bằng 7. Chỉ tăng dung lượng bảng có tự chia chúng ra nhiều bucket không?

**Lời giải:** không theo công thức đã xét: cùng mã ban đầu cho cùng mã sau trộn và cùng chỉ số ở mỗi dung lượng. Cần xem lại cách băm và mô hình khóa, không chỉ tăng số vị trí.

### Bài 12 — Mở rộng thí nghiệm

Hãy thêm hai khóa có rawHash khác nhau nhưng cùng bucket ở dung lượng 16, rồi tìm bằng một đối tượng mới bằng một trong hai khóa. Cần giữ điều gì để thí nghiệm có ý nghĩa?

**Lời giải:** có thể chọn rawHash 3 và 19, cả hai có mã sau trộn tương ứng 3 và 19 và cùng bucket 3 ở dung lượng 16. Dùng hai id riêng, giữ rawHash trong dữ liệu so bằng như Key của chương trình, không thay đổi khóa sau khi thêm. Quan sát dấu vết để thấy ứng viên khác mã có thể bị bỏ qua. Không suy ra số lần gọi hoặc thứ tự nút là một bảo đảm API cho mọi phiên bản.

## 15. Tự kiểm tra mô hình tìm khóa

Hãy tự giải thích một lần get theo ba tầng: chọn phạm vi bằng mã băm, lọc và so khóa trong bucket, rồi đọc giá trị của nút đã khớp. Nếu trộn lẫn ba tầng này, rất dễ đồng nhất bucket với khóa hoặc giá trị null với việc không tìm thấy.

Khi thiết kế khóa, ưu tiên tính đúng của equals/hashCode và sự ổn định của dữ liệu nhận diện. Khi đánh giá hiệu năng, xét cả phân bố bucket và chi phí các phương thức của khóa. Khi đọc mã nguồn, ghi rõ phiên bản và tách chi tiết triển khai khỏi hợp đồng công khai.

Bucket giúp thu hẹp nơi tìm; mã băm giúp lọc ứng viên; phép nhận diện khóa quyết định ánh xạ nào thực sự khớp. Cả ba vai trò kết hợp mới tạo nên quy trình tìm khóa của HashMap.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-map-002">Phối hợp hashCode và equals khi put, get, remove</a>

<details>
<summary>Xem chi tiết</summary>

# Phối hợp hashCode và equals khi put, get, remove

## 1. Hai nhiệm vụ: tìm vùng ứng viên và nhận diện đúng khóa

Một HashMap cần trả lời câu hỏi: “Khóa đang hỏi có phải cùng khóa logic với một ánh xạ đã lưu không?”. Nếu phải so sánh với mọi khóa mỗi lần đọc, việc tra cứu sẽ tốn nhiều công sức khi dữ liệu lớn.

HashMap phối hợp hai công cụ:

- `hashCode()` giúp định hướng đến một vùng chứa ứng viên.
- `equals()` xác định sự bằng nhau về mặt logic khi cần phân biệt các khóa ứng viên.

Ví dụ một đối tượng khóa mang mã khách hàng C01. Hai đối tượng được tạo riêng nhưng cùng biểu thị C01 phải có thể tìm cùng một ánh xạ, nếu mô hình định danh của ứng dụng quy định như vậy.

Ngược lại, hai khách hàng C01 và C02 có thể tình cờ cho cùng mã băm. HashMap vẫn phải giữ chúng như hai khóa khác nhau nếu equals trả false.

Bài này giải thích đường đi của put, get và remove, phân biệt các loại va chạm, và cho thấy vì sao khóa sai hợp đồng hoặc khóa bị sửa sau khi chèn dễ làm tra cứu không còn như mong đợi.

## 2. Hợp đồng phải đúng trước khi xét thuật toán

### 2.1. Bằng nhau kéo theo cùng mã băm

Hai quy tắc quan trọng:

```text
a.equals(b) == true  →  a.hashCode() == b.hashCode()
cùng hashCode       ↛  bắt buộc equals bằng true
```

Khi thông tin liên quan đến phép bằng không đổi, các lần gọi hashCode trên cùng đối tượng trong một lần chạy cũng phải nhất quán. Một hashCode tự đổi theo số lần gọi hoặc theo ngẫu nhiên sẽ làm đường tìm không ổn định.

Chiều thứ hai không phải bảo đảm. Mã băm là số nguyên có miền giá trị hữu hạn, không phải một định danh duy nhất không thể trùng.

Một hàm hashCode luôn trả 7 có thể vẫn đúng điều kiện “hai khóa bằng nhau có cùng mã băm”. Nhưng phân bố đó kém và có thể làm tìm kiếm chậm. Đúng hợp đồng và có hiệu năng tốt là hai yêu cầu cần phân biệt.

Hợp đồng gốc được mô tả trong [Object.equals và Object.hashCode Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html).

### 2.2. equals phải là một quan hệ nhất quán

Khi xây khóa theo giá trị, equals cần đáp ứng các tính chất như phản xạ, đối xứng, bắc cầu, nhất quán khi thông tin nhận diện không đổi, và trả false khi so với null.

Đừng viết một lớp mà a bằng b nhưng b không bằng a rồi mong HashMap sửa sự mâu thuẫn ấy. Hướng gọi equals có thể làm kết quả trở nên khó hiểu nếu bản thân quan hệ đã sai.

Nếu ghi đè equals, phải đồng thời bảo đảm hashCode phù hợp. Chỉ ghi đè hashCode mà vẫn dùng phép bằng theo tham chiếu của Object không tự tạo ra khóa theo nội dung.

### 2.3. Cùng tham chiếu khác với cùng khóa logic

`a == b` kiểm tra hai tham chiếu cùng đối tượng. `a.equals(b)` có thể quy định hai đối tượng khác nhau vẫn là cùng định danh logic.

Một lớp thường chưa ghi đè equals sẽ kế thừa hành vi bằng theo tham chiếu từ Object. Khi đó hai đối tượng có các trường giống nhau chưa chắc là cùng khóa.

Record có thể giúp khi các thành phần của record đúng là những dữ liệu nhận diện cần dùng. Phép bằng và mã băm mặc định của record dựa trên các thành phần; vẫn cần quan tâm dữ liệu bên trong có thay đổi được hay không.

## 3. Từ hashCode đến bucket trong OpenJDK

### 3.1. Bucket là vùng chứa ứng viên

HashMap tổ chức một bảng các vùng chứa, thường gọi là **bucket**. Một bucket có thể chứa không có, một hoặc nhiều ánh xạ.

Mô hình:

```text
khóa cần thao tác
    ↓ hashCode
mã băm của khóa
    ↓ trộn một số bit
hash dùng trong cấu trúc
    ↓ chọn chỉ số
bucket ứng viên
    ↓ kiểm tra hash và khóa
ánh xạ đúng hoặc không tìm thấy
```

HashMap không dùng một mã băm làm chỗ duy nhất chỉ chứa được một entry. Va chạm là trường hợp cấu trúc phải xử lý bình thường.

### 3.2. Công thức là chi tiết cài đặt, không phải API bắt người gọi tự tính

Trong OpenJDK 17u, với khóa khác null, một phép trộn được dùng có dạng:

```java
int h = key.hashCode();
int hash = h ^ (h >>> 16);
```

Khi bảng đã được cấp phát và có chiều dài n là lũy thừa của 2:

```java
int index = (n - 1) & hash;
```

Đây là các biểu thức minh họa từ cơ chế cài đặt, không phải một chương trình độc lập. Người gọi HashMap không cần tự tính index trước put/get/remove.

Có thể đối chiếu [mã nguồn HashMap OpenJDK 17u](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/HashMap.java). Hợp đồng công khai về khóa và thao tác map không buộc mọi phiên bản cài đặt phải giữ nguyên một công thức nội bộ.

Mã băm có thể là số âm. Người viết khóa không phải biến hashCode thành một số không âm để HashMap dùng được. Cũng không dùng Math.abs như một yêu cầu bắt buộc của hợp đồng hashCode.

### 3.3. Cùng bucket chưa chắc cùng hash

Giả sử n bằng 16 và các hash đã trộn là 1 và 17:

```text
15 & 1  = 1
15 & 17 = 1
```

Cả hai hướng vào bucket 1 nhưng hai hash vẫn khác nhau. Bảng chỉ dùng một phần thông tin để chọn vùng chứa; node còn giữ hash để hỗ trợ lọc ứng viên.

Vì vậy phải phân biệt:

| Quan hệ | Điều suy ra được |
| --- | --- |
| Hai khóa bằng nhau và đúng hợp đồng | Chúng có cùng hashCode và được định hướng phù hợp để tìm nhau |
| Cùng hashCode | Có thể là hai khóa khác nhau |
| Cùng hash đã trộn | Vẫn có thể cần kiểm tra khóa |
| Cùng chỉ số bucket | Càng không đủ để kết luận cùng khóa |

“Va chạm bucket” rộng hơn trường hợp “hai khóa trả cùng hashCode”.

## 4. Cách một ứng viên được xác nhận

Trong đường xử lý thông thường của OpenJDK, điều kiện kiểm tra có dạng khái niệm:

```text
hash lưu trong node bằng hash đang tìm
VÀ
(
    khóa lưu cùng tham chiếu với khóa đang tìm
    HOẶC
    khóa đang tìm khác null và bằng khóa lưu theo equals
)
```

Những điểm cần giữ:

1. Hash lọc vùng và ứng viên; nó không thay thế định nghĩa bằng nhau.
2. Cùng tham chiếu có thể là một đường xác nhận nhanh.
3. Nếu cần gọi equals, việc so khóa diễn ra sau khi đã có các điều kiện định hướng phù hợp.
4. Một lookup không có nghĩa bắt buộc gọi equals với mọi khóa đã lưu.

Trong các đoạn tìm thông thường của OpenJDK, equals được gọi từ khóa của thao tác tới khóa đang lưu, dạng `lookupKey.equals(storedKey)`. Khi equals đúng tính đối xứng, hướng đó không làm thay đổi kết luận logic.

Không xây kiểm thử nghiệp vụ yêu cầu chính xác bao nhiêu lần equals/hashCode phải được gọi hoặc gọi theo một thứ tự nội bộ cố định. Cấu trúc có thể tối ưu, thay đổi đường tìm hoặc xử lý bucket dạng cây khi phù hợp.

Bucket có nhiều phần tử không phải lúc nào cũng chỉ là một danh sách liên kết. Chi tiết danh sách/cây giúp cài đặt xử lý va chạm, nhưng không thay đổi nghĩa “cùng khóa logic theo hợp đồng”.

## 5. put: cập nhật khóa cũ hoặc thêm một khóa khác

### 5.1. Khóa đã có theo phép bằng

Giả sử đã lưu:

```text
Key("A") → first-A
```

Gọi put với một đối tượng Key("A") khác, nhưng bằng nhau và có hash đúng hợp đồng:

```text
put(new Key("A"), updated-A)
```

HashMap tìm được ánh xạ ứng viên, xác nhận cùng khóa và thay giá trị. Size không tăng. Giá trị trả về là first-A, tức giá trị trước thao tác.

Không có yêu cầu phải giữ lại đúng tham chiếu ban đầu trong mã gọi rồi mới cập nhật được. Đó là lợi ích của mô hình khóa theo giá trị.

### 5.2. Cùng hash nhưng equals false

Giả sử Key("A") và Key("B") đều có hashCode bằng 7, nhưng không bằng nhau. Put B phải giữ một ánh xạ riêng:

```text
Key("A") → first-A
Key("B") → first-B
```

B không ghi đè A chỉ vì cùng hash hoặc cùng bucket. HashMap xử lý va chạm rồi thêm entry cho khóa khác.

### 5.3. Giá trị không quyết định tính trùng của khóa

HashMap chọn ánh xạ theo khóa. Hai value bằng nhau không làm hai khóa bị nhập lại; hai value khác nhau cũng không làm một khóa đã bằng nhau trở thành hai khóa.

Nếu put “làm mất” một dữ liệu, hãy kiểm tra quy tắc equals/hashCode của khóa và miền định danh, thay vì chỉ nhìn giá trị đang ghi.

Hành vi put và tính duy nhất của khóa có thể đối chiếu ở [HashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html).

## 6. get: cùng đường nhận diện nhưng chỉ đọc giá trị

Get thực hiện việc tìm ứng viên theo hash và nhận diện khóa. Nếu tìm thấy, nó trả value của entry. Nếu không tìm thấy, nó trả null.

Một đối tượng khóa được tạo mới vẫn có thể get được dữ liệu nếu bằng khóa đã lưu và có mã băm phù hợp. Ngược lại, một đối tượng có cùng hash nhưng không bằng khóa không phải một kết quả khớp.

Với một map có khóa A và B cùng hash:

```text
get(Key("A")) → giá trị của A
get(Key("B")) → giá trị của B
get(Key("C")) → không tìm thấy, nếu C khác cả A và B
```

Get không tra cứu bằng hashCode của value. Nếu cần tìm các khóa theo value, đó là một bài toán khác với đường tìm khóa của HashMap.

### Null không tự chứng minh khóa vắng mặt

HashMap cho phép giá trị null. Vì vậy get trả null có thể là:

- Không có entry phù hợp.
- Có entry phù hợp nhưng value đang là null.

ContainsKey giúp phân biệt sự tồn tại trong ngữ cảnh cần biết điều đó. Bản thân thông tin tìm đúng khóa và thông tin value khác null là hai vấn đề riêng.

## 7. remove: tìm đúng khóa rồi tháo ánh xạ đó

Remove một đối số dùng khóa để tìm entry như trên, sau đó loại ánh xạ phù hợp và trả giá trị cũ.

Nếu A và B cùng hash nhưng không bằng nhau, remove(A) không được xóa B chỉ vì B cùng bucket. Nếu không có khóa cần tìm, remove trả null và không xóa các khóa khác.

Với value null, remove cũng có thể trả null sau khi đã xóa thành công. Không dùng riêng kết quả null để khẳng định thao tác không tìm thấy khóa.

Biến thể `remove(key, expectedValue)` còn bổ sung điều kiện về value sau khi xác định ánh xạ theo key. Điều đó không biến quá trình tìm khóa thành tìm theo hashCode của value.

Nếu thao tác remove thất bại do khóa tự thay đổi hoặc vi phạm hợp đồng, gọi lặp lại với những đối tượng trông giống nhau không sửa được thiết kế khóa. Cần xem dữ liệu nhận diện và mã băm đang được tính từ đâu.

## 8. Chương trình chạy: va chạm hợp lệ và khóa bằng nhau

Lưu thành **HashLookupDemo.java**:

```java
import java.util.HashMap;
import java.util.Map;

public class HashLookupDemo {
    record CollisionKey(String code) {
        @Override
        public int hashCode() {
            return 7;
        }
    }

    public static void main(String[] args) {
        CollisionKey a = new CollisionKey("A");
        CollisionKey equalA = new CollisionKey("A");
        CollisionKey b = new CollisionKey("B");
        Map<CollisionKey, String> map = new HashMap<>();

        System.out.println("same reference A: " + (a == equalA));
        System.out.println("equal logical A: " + a.equals(equalA));
        System.out.println("A and B same hash: " + (a.hashCode() == b.hashCode()));
        System.out.println("A equals B: " + a.equals(b));
        System.out.println("first put A old: " + map.put(a, "first-A"));
        System.out.println("first put B old: " + map.put(b, "first-B"));
        System.out.println("size with collision: " + map.size());
        System.out.println("get equal A: " + map.get(equalA));
        System.out.println("equal A put old: " + map.put(equalA, "updated-A"));
        System.out.println("size after equal-key put: " + map.size());
        System.out.println("get original A: " + map.get(a));
        System.out.println("get B: " + map.get(new CollisionKey("B")));

        System.out.println("remove equal A: " + map.remove(new CollisionKey("A")));
        System.out.println("A still present: " + map.containsKey(a));
        System.out.println("B still present: " + map.containsKey(b));
        System.out.println("remove missing C: " + map.remove(new CollisionKey("C")));
        System.out.println("size after removals: " + map.size());

        map.put(null, "first-null");
        System.out.println("get null key: " + map.get(null));
        System.out.println("second null-key put old: " + map.put(null, "second-null"));
        System.out.println("size with null key: " + map.size());
        System.out.println("remove null key: " + map.remove(null));

        CollisionKey n = new CollisionKey("N");
        map.put(n, null);
        System.out.println("get mapped null: " + map.get(new CollisionKey("N")));
        System.out.println("contains mapped-null key: " + map.containsKey(n));
        System.out.println("remove mapped null old: " + map.remove(new CollisionKey("N")));
        System.out.println("mapped-null key remains: " + map.containsKey(n));
        System.out.println("final size: " + map.size());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 HashLookupDemo.java
java HashLookupDemo
```

Stdout chính xác:

```text
same reference A: false
equal logical A: true
A and B same hash: true
A equals B: false
first put A old: null
first put B old: null
size with collision: 2
get equal A: first-A
equal A put old: first-A
size after equal-key put: 2
get original A: updated-A
get B: first-B
remove equal A: updated-A
A still present: false
B still present: true
remove missing C: null
size after removals: 1
get null key: first-null
second null-key put old: first-null
size with null key: 2
remove null key: second-null
get mapped null: null
contains mapped-null key: true
remove mapped null old: null
mapped-null key remains: false
final size: 1
```

CollisionKey là record có thành phần code. Equals mặc định của record phân biệt A với B và nhận hai bản A là bằng nhau. HashCode cố ý luôn trả 7 để tạo va chạm có thể quan sát.

Hàm băm hằng ở đây dùng cho thí nghiệm; không phải khuyến nghị phân bố hash cho khóa trong ứng dụng.

Các kết quả giải thích được bằng cùng một mô hình:

- a và equalA khác tham chiếu nhưng bằng khóa logic.
- A và B có cùng hash nhưng là hai khóa nên size bằng 2.
- Put equalA thay value của A và giữ size.
- Get/remove bằng một bản A mới vẫn tìm đúng ánh xạ.
- Xóa A không ảnh hưởng B.
- Khóa null được lưu/cập nhật/xóa như một khóa riêng được HashMap hỗ trợ.
- Value null vẫn là một ánh xạ thực, và remove value null có thể trả null dù đã xóa khóa.

Các chương trình trong bài được biên dịch với `--release 17` và chạy trên OpenJDK 24.0.2. Cờ đó kiểm tra ngôn ngữ/API mục tiêu Java 17 và sinh bytecode tương ứng; JVM chạy ví dụ vẫn là bản đã nêu.

## 9. Null key đi qua nhánh riêng

HashMap chấp nhận một khóa null. Vì không thể gọi null.hashCode(), cài đặt OpenJDK dùng hash 0 cho nhánh khóa null.

Điều này không có nghĩa bucket 0 chỉ được chứa null. Một khóa khác null có hash phù hợp vẫn có thể cùng bucket, nhưng phép nhận diện khóa phải phân biệt nó với null.

Theo hợp đồng equals hợp lệ, một đối tượng khác null không được coi null là bằng chính nó. Vì vậy một khóa thường có hash 0 không tự trở thành khóa null.

Nhiều lần put(null, value) cập nhật cùng ánh xạ null, không tạo ra nhiều khóa null. Các lớp map khác có thể cấm null; đây là chính sách của HashMap, không phải điều mọi Map đều cho phép.

## 10. Hai lỗi thiết kế khóa và dấu hiệu quan sát

### 10.1. Equals đúng ý nhưng hashCode không nhất quán

Giả sử hai đối tượng cùng mã "same" có equals true nhưng hashCode lần lượt là 1 và 2.

Định nghĩa khóa đã vi phạm hợp đồng. HashMap có thể định hướng thao tác tới các vùng khác nhau và không gọi equals giữa hai đối tượng như người viết mong đợi. Get bằng bản “bằng nhau” có thể không tìm thấy; put có thể để xuất hiện các entry nhìn như trùng định danh logic.

Không sửa bằng cách ép HashMap quét hết mọi khóa. Cần sửa hashCode để mọi khóa bằng nhau cùng mã băm theo hợp đồng.

### 10.2. Khóa bị sửa sau khi đã lưu

Giả sử một khóa có id bằng 1 khi đưa vào map và hashCode dựa trên id. Sau đó id bị đổi thành 17.

Node đã được đặt vào cấu trúc theo hash ở lúc thêm. Thao tác get/remove mới tính hash từ trạng thái mới của khóa. Hai trạng thái không còn nhất quán với nhau.

Ngay cả cùng tham chiếu cũng không phải một bảo đảm cứu được tra cứu: đường lọc hash có thể không đến được bước xác nhận tham chiếu.

Trong mô hình bảng 16 ô của OpenJDK, hash 1 và 17 thậm chí có thể cùng bucket, nhưng hash đầy đủ lưu ở node và hash đang tìm vẫn khác. Như vậy “vẫn cùng bucket” cũng không đủ để khóa bị sửa hoạt động đúng.

Map cảnh báo hành vi không được xác định khi khóa bị thay đổi theo cách ảnh hưởng phép bằng trong lúc làm khóa. Xem [ghi chú khóa thay đổi được của Map Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html).

## 11. Chương trình quan sát lỗi: không phải hợp đồng để dựa vào

Lưu thành **BrokenHashKeyObservation.java**:

```java
import java.util.HashMap;
import java.util.Map;

public class BrokenHashKeyObservation {
    static final class BadHashKey {
        private final String code;
        private final int forcedHash;

        BadHashKey(String code, int forcedHash) {
            this.code = code;
            this.forcedHash = forcedHash;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof BadHashKey key && code.equals(key.code);
        }

        @Override
        public int hashCode() {
            return forcedHash;
        }
    }

    static final class MutableKey {
        int id;

        MutableKey(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MutableKey key && id == key.id;
        }

        @Override
        public int hashCode() {
            return id;
        }
    }

    public static void main(String[] args) {
        BadHashKey first = new BadHashKey("same", 1);
        BadHashKey equalButDifferentHash = new BadHashKey("same", 2);
        Map<BadHashKey, String> invalid = new HashMap<>();
        invalid.put(first, "stored");

        System.out.println("bad keys equals: " + first.equals(equalButDifferentHash));
        System.out.println("bad keys same hash: "
                + (first.hashCode() == equalButDifferentHash.hashCode()));
        System.out.println("observed bad lookup: " + invalid.get(equalButDifferentHash));
        invalid.put(equalButDifferentHash, "second");
        System.out.println("observed bad map size: " + invalid.size());

        MutableKey key = new MutableKey(1);
        Map<MutableKey, String> mutable = new HashMap<>(16);
        mutable.put(key, "payload");
        System.out.println("before key change: " + mutable.get(key));
        key.id = 17;
        System.out.println("observed changed-key get: " + mutable.get(key));
        System.out.println("observed changed-key remove: " + mutable.remove(key));
        System.out.println("observed changed-key size: " + mutable.size());
        System.out.println("observed entry still holds reference: "
                + (mutable.keySet().iterator().next() == key));
    }
}
```

Lệnh chạy:

```shell
javac --release 17 -encoding UTF-8 BrokenHashKeyObservation.java
java BrokenHashKeyObservation
```

Stdout quan sát trên môi trường kiểm tra:

```text
bad keys equals: true
bad keys same hash: false
observed bad lookup: null
observed bad map size: 2
before key change: payload
observed changed-key get: null
observed changed-key remove: null
observed changed-key size: 1
observed entry still holds reference: true
```

BadHashKey cố ý sai hợp đồng: equals chỉ xét code nhưng hashCode còn tùy một số được truyền riêng. Hai khóa "same" có equals true mà hash khác.

MutableKey cố ý sửa id sau khi chèn. Trong lần quan sát, get và remove không tìm được, nhưng size vẫn là 1 và entry còn giữ chính tham chiếu khóa ấy. Điều này giúp thấy dữ liệu không nhất thiết bị xóa khỏi cấu trúc chỉ vì tra cứu không ra.

**Những dòng observed minh họa hậu quả trên môi trường đã nêu, không phải hành vi được bảo đảm cho khóa sai hợp đồng hoặc khóa bị sửa trên mọi Java 17+.** Không viết kiểm thử sản phẩm bắt HashMap phải tái hiện chính xác các kết quả lỗi ấy.

Không dùng hai lớp này làm mẫu thiết kế đúng. Chúng là thí nghiệm để hiểu vì sao yêu cầu đối với khóa phải được tôn trọng.

## 12. Thiết kế khóa dùng được cho cả ba thao tác

### 12.1. Chọn đúng các thành phần định danh

Ví dụ một khóa ghép:

```java
// Khai báo minh họa đặt trong một lớp:
record CustomerKey(String organization, long customerId) {}
```

Hai CustomerKey cùng organization và customerId là cùng khóa logic. Khác organization có thể là hai khách hàng khác nhau dù cùng số customerId.

Những thành phần này cần phản ánh đúng miền dữ liệu, không chọn tùy ý để tạo nhiều hash khác nhau. Một hàm hash tốt không sửa được một mô hình equals đang nhập hai thực thể khác nhau làm một.

### 12.2. Giữ thông tin nhận diện ổn định

Ưu tiên String, số, enum hoặc các kiểu giá trị phù hợp và ổn định làm thành phần khóa.

Một trường final chỉ ngăn đổi tham chiếu của trường, không tự làm đối tượng bên trong bất biến. Nếu khóa chứa một List thay đổi được và equals/hashCode phụ thuộc nội dung List, sửa List vẫn có thể đổi ý nghĩa khóa.

Khi định danh phải thay đổi, thiết kế việc bỏ ánh xạ với khóa cũ rồi thêm ánh xạ bằng khóa mới theo chính sách ứng dụng. Không sửa khóa đang nằm trong map rồi trông chờ việc tăng dung lượng sẽ tự sửa lại mọi thứ.

### 12.3. Không đưa trường thay đổi thường xuyên vào khóa nếu nó không phải định danh

Tên hiển thị, bộ đếm, trạng thái phiên làm việc hoặc ngày cập nhật có thể thay đổi trong quá trình sử dụng. Nếu chúng không thuộc định danh cần phân biệt, không nên đưa chúng vào equals/hashCode của khóa chỉ vì chúng có sẵn trong lớp.

Có thể giữ mã định danh làm khóa và đặt dữ liệu thay đổi trong value. Khi value đổi, đường nhận diện khóa vẫn ổn định.

Nhưng nếu nghiệp vụ thật sự cần một ảnh chụp đầy đủ làm khóa, phải tạo một giá trị ảnh chụp ổn định và hiểu ý nghĩa đó. Không có quy tắc rằng mọi lớp có nhiều trường đều phải so tất cả hoặc chỉ một trường; mô hình định danh quyết định.

## 13. Hiệu năng và kiểm thử: đừng kiểm tra nhầm một chi tiết tối ưu

HashMap có hiệu năng tra cứu tốt khi mã băm phân bố phù hợp. Nhiều khóa cùng hash làm tăng công việc phân biệt ứng viên. Những cơ chế xử lý va chạm có thể giúp một số trường hợp, nhưng không biến một hàm băm hằng thành lựa chọn tốt cho mọi dữ liệu.

Một lần equals/hashCode phức tạp cũng có chi phí riêng. Không coi “tra cứu O(1) trung bình” là bằng không công việc hoặc luôn nhanh bất kể cách định nghĩa khóa.

Các kiểm thử có ý nghĩa cho một kiểu khóa gồm:

- Hai đối tượng có cùng định danh: equals true và hashCode bằng nhau.
- Hai định danh khác nhau: không bị equals nhận nhầm; hash vẫn được phép trùng.
- Put bằng bản khóa tương đương cập nhật một ánh xạ, size không tăng.
- Get và remove bằng bản khóa tương đương đều tìm đúng.
- Các khóa va chạm vẫn giữ/tìm/xóa độc lập.
- Những thành phần định danh không bị thay đổi trong vòng đời làm khóa.

Không cần buộc mọi cặp khóa khác nhau phải có hashCode khác nhau. Cũng không buộc hashCode phải giống giữa mọi lần chạy chương trình nếu kiểu đó không có hợp đồng như vậy.

Đừng dựa vào thứ tự duyệt HashMap hoặc số lần gọi equals chính xác để xác nhận đúng mô hình khóa. Hãy kiểm tra kết quả nghiệp vụ và hợp đồng bằng nhau.

HashMap cũng không tự an toàn cho cập nhật đồng thời. Một equals/hashCode đúng không thay thế yêu cầu chọn cấu trúc hoặc đồng bộ phù hợp khi nhiều luồng cùng sửa dữ liệu.

## 14. Lỗi phổ biến và cách chẩn đoán

| Hiện tượng | Nguyên nhân cần xem | Cách sửa suy luận |
| --- | --- | --- |
| Hai khóa cùng mã băm bị tưởng ghi đè nhau | Nhầm hash bằng định danh | Kiểm tra equals, không chỉ hashCode |
| Tạo đối tượng khóa mới rồi get không ra | Chưa có equals theo giá trị hoặc hash không phù hợp | Kiểm tra cả hai phương thức |
| Equals true nhưng map có hai entry nhìn trùng | HashCode có thể sai hợp đồng | Bảo đảm bằng nhau kéo theo cùng mã băm |
| Put cập nhật giá trị mà size không tăng | Khóa tương đương đã tồn tại | Đây có thể đúng hành vi Map |
| Cùng tham chiếu nhưng get/remove thất bại | Khóa đã đổi thông tin nhận diện | Giữ khóa ổn định |
| Dữ liệu vẫn còn khi duyệt nhưng get không ra | Có thể là khóa thay đổi được | Không xem việc còn entry là bằng chứng đường tìm vẫn đúng |
| Remove trả null nên tưởng không xóa | Value cũ có thể là null | Phân biệt tồn tại khóa với giá trị |
| Cùng bucket nên tưởng chắc gọi equals | Node còn có điều kiện hash | Tách index bucket khỏi hash đầy đủ |
| Hàm băm hằng “đúng nên chắc nhanh” | Đúng hợp đồng nhưng phân bố kém | Thiết kế hash phù hợp dữ liệu |
| Key có List final nhưng vẫn đổi hash | Nội dung đối tượng bên trong thay đổi | Xét tính ổn định sâu của dữ liệu nhận diện |

## 15. Bài tập tự kiểm tra có lời giải

### Bài 1: Cùng hash, khác khóa

A và B có hashCode bằng 7 nhưng equals false. Put cả hai vào HashMap có bắt buộc chỉ giữ một entry không?

**Lời giải:** không. Va chạm không phải bằng nhau. Map giữ hai ánh xạ nếu hai khóa khác nhau theo hợp đồng.

### Bài 2: Khác tham chiếu, cùng khóa

A và A2 khác đối tượng, equals true và có cùng hashCode. Put A rồi put A2 có giá trị mới. Size và giá trị theo A thế nào?

**Lời giải:** vẫn một ánh xạ cho khóa logic đó; get bằng A cho giá trị mới. Không cần cùng tham chiếu để cập nhật.

### Bài 3: Bucket và hash

Với bảng 16 ô, hai hash đã trộn 1 và 17 đều vào bucket nào? Có đủ để nói hai khóa bằng nhau không?

**Lời giải:** cùng bucket 1 vì dùng mặt nạ 15. Không đủ; hash đầy đủ còn khác nhau và khóa cần có quan hệ nhận diện hợp lệ.

### Bài 4: Một nửa hợp đồng

Một lớp ghi đè equals để hai mã giống nhau là bằng nhau, nhưng hashCode còn xét tên hiển thị có thể khác. Có an toàn không?

**Lời giải:** không nếu tên làm hai đối tượng bằng nhau có hashCode khác. Cần đồng bộ các thành phần và quy tắc của hashCode với equals.

### Bài 5: Khóa bị sửa

Một khóa dùng id trong equals/hashCode, đã được put khi id là 1 rồi đổi id thành 17. Có được dựa vào việc vẫn cầm đúng tham chiếu để get chắc thành công không?

**Lời giải:** không. Hash tính ở thời điểm tra có thể không khớp đường lưu ban đầu. Đây là thiết kế khóa thay đổi trong khi làm khóa, không có bảo đảm sử dụng bình thường.

### Bài 6: Giá trị null

Map có K → null. Get(K) và remove(K) đều trả null. Có thể kết luận K chưa từng tồn tại không?

**Lời giải:** không. Get có thể đã tìm đúng entry có value null; remove có thể đã xóa entry ấy. Khi cần biết tồn tại, phải xem thông tin khóa thích hợp.

### Bài 7: Khóa ghép

Hai tổ chức có cùng customerId 42. Nếu chỉ dùng customerId làm khóa, một giá trị bị thay sau put thứ hai. HashMap có vi phạm khóa duy nhất không?

**Lời giải:** không. Mô hình khóa đang coi hai thực thể là cùng khóa. Nếu cần phân biệt theo tổ chức, dùng khóa ghép phù hợp như CustomerKey.

### Bài 8: Kiểm thử hash

Một kiểm thử yêu cầu mọi khóa khác nhau có hashCode khác nhau. Điều này có đúng hợp đồng hashCode không?

**Lời giải:** không. Hai khóa khác vẫn được phép trùng hash. Kiểm thử nên yêu cầu các khóa bằng nhau có cùng hash và xác nhận map xử lý các khóa khác, kể cả va chạm, đúng kết quả.

## 16. Tài liệu đối chiếu

- [HashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html): hợp đồng lớp và các thao tác.
- [Object Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html): equals và hashCode.
- [Map Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): tính duy nhất của khóa, null và giới hạn với khóa thay đổi được.
- [HashMap trong OpenJDK 17u](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/HashMap.java): đối chiếu hash, getNode, putVal và removeNode.

Hash giúp tìm vùng ứng viên; equals giúp nhận diện đúng khóa logic. Put, get và remove chỉ phối hợp hiệu quả khi hai phương thức của khóa thống nhất với nhau và thông tin nhận diện được giữ ổn định trong lúc khóa nằm trong map.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-map-003">Capacity, load factor, ngưỡng mở rộng và rehash</a>

<details>
<summary>Xem chi tiết</summary>

# Capacity, load factor, ngưỡng mở rộng và rehash

## 1. Vì sao HashMap cần một chính sách mở rộng?

Một `HashMap<K, V>` lưu các ánh xạ khóa–giá trị trên một bảng băm. Bảng có các vị trí đầu mối gọi là **bucket**. Từ mã băm của khóa, hiện thực xác định bucket và tìm ánh xạ tương ứng trong đó.

Nhiều khóa có thể đi vào cùng một bucket. Vì thế bảng băm không phải một mảng trong đó “mỗi ô chứa đúng một ánh xạ, đầy ô mới được thêm tiếp”. Một bucket có thể quản lý nhiều ánh xạ; ngược lại, một số bucket có thể rỗng.

Khi số ánh xạ tăng, giữ nguyên một bảng nhỏ có thể làm nhiều khóa tập trung hơn vào các bucket và tăng công việc tra cứu. Mở rộng bảng giúp có thêm không gian phân bố, nhưng phải cấp phát và tổ chức lại dữ liệu. `HashMap` cần cân bằng hai chi phí đó.

Bốn khái niệm của bài liên hệ như sau:

```text
Số ánh xạ hiện có: size
             ↓ so với
Ngưỡng mở rộng: threshold
             ↑ chịu ảnh hưởng bởi
Số bucket: capacity × chính sách load factor
             ↓ khi cần mở rộng
Bảng lớn hơn + phân bố lại ánh xạ
             = resize / rehash theo ngữ cảnh
```

Bài tách rõ hai tầng: **hợp đồng API HashMap** và **chi tiết hiện thực OpenJDK**. Các con số bucket, trường nội bộ và thuật toán chia bucket được giải thích theo mã nguồn OpenJDK 17u đã đối chiếu. Các chương trình được biên dịch với mức tương thích Java 17 và chạy kiểm chứng trên JDK 24.0.2; phép quan sát nội bộ được ghi rõ để không nhầm thành API công khai ổn định cho mọi Java runtime.

## 2. Bốn đại lượng phải phân biệt

| Đại lượng | Ý nghĩa | Có thể đọc trực tiếp bằng API công khai không? |
|---|---|---|
| `size` | Số ánh xạ hiện đang có | Có: `map.size()` |
| `capacity` | Số bucket của bảng đã cấp phát | Không có `HashMap.capacity()` công khai |
| `load factor` | Tham số chính sách ảnh hưởng mức tải trước khi mở rộng | Chọn khi dựng map; không có getter/setter công khai tương ứng |
| `threshold` | Ngưỡng kích thước mà hiện thực dùng để quyết định mở rộng | Không có getter công khai |

### Capacity không phải số phần tử tối đa

Một bảng có capacity `16` không có nghĩa map chỉ lưu tối đa `16` ánh xạ. Khi chính sách yêu cầu, nó có thể mở rộng và tiếp tục chứa thêm. Ngay cả khi số ánh xạ vượt số bucket, mô hình nhiều ánh xạ trong một bucket vẫn có thể biểu diễn dữ liệu.

Tương tự, `new HashMap<>(100)` không tạo sẵn `100` ánh xạ. Map mới có `size() == 0`. Tham số này liên quan dung lượng ban đầu, không phải việc thêm khóa, cũng không phải một giới hạn cứng chặn lần thêm thứ `101`.

### Size thay đổi theo khóa mới, không theo số lần gọi put

Một triệu lần `put` vào cùng một khóa logic có thể vẫn chỉ để lại một ánh xạ. Nếu mỗi lần chỉ thay giá trị của khóa đã có, kích thước không tăng.

Khi tính dung lượng, điều cần ước lượng là số khóa logic khác nhau cùng tồn tại ở một thời điểm, thường là mức đỉnh dự kiến. Số yêu cầu xử lý tích lũy hay số lần gọi phương thức không tự là số bucket cần có.

## 3. Load factor là chính sách tải, không phải phần trăm bucket đang có dữ liệu

Gọi:

- `n`: số ánh xạ hiện có.
- `m`: số bucket đã cấp phát.
- `α = n / m`: mức tải hiện tại, khi bảng đã tồn tại.
- `f`: load factor được chọn.

`α` là trạng thái hiện tại; `f` là một tham số chính sách. Chúng không phải hai tên của cùng một đại lượng.

Với `n = 12`, `m = 16`, ta có mức tải trung bình `α = 0.75`. Điều đó **không khẳng định đúng 12 bucket có dữ liệu**. Nếu nhiều khóa va chạm, số bucket không rỗng có thể ít hơn.

Load factor mặc định của `HashMap` là `0.75f`. Nó cân bằng chi phí bộ nhớ của bảng với công việc tìm trong các bucket trong nhiều tình huống thông thường.

| Chọn load factor | Xu hướng đánh đổi |
|---|---|
| Thấp hơn | Mở rộng với tải thấp hơn; thường cần nhiều bucket hơn cho cùng số ánh xạ |
| Cao hơn | Có thể dùng ít bucket hơn; trung bình có thể tăng công việc xử lý trong bucket |
| Mặc định `0.75f` | Điểm bắt đầu hợp lý khi chưa có dữ liệu đo cần thay đổi |

Đây là xu hướng, không phải cam kết một lựa chọn luôn nhanh hơn. Hàm băm, kiểu khóa, mẫu truy cập, tần suất duyệt, bộ nhớ và chi phí của giá trị đều có thể ảnh hưởng.

Load factor không bị giới hạn về mặt ý nghĩa ở khoảng “từ 0 đến 1” như phần trăm ô được lấp. Một giá trị lớn hơn `1` không có nghĩa map không thể hoạt động, vì một bucket có thể có nhiều ánh xạ. Tuy nhiên, không nên chọn một hệ số cực đoan chỉ để trì hoãn mở rộng mà bỏ qua chi phí tra cứu.

Constructor từ chối dung lượng âm và load factor không dương; mã nguồn OpenJDK cũng kiểm tra `NaN`. Capacity ban đầu bằng `0` được phép, nhưng không làm map thành một cấu trúc không thể thêm dữ liệu. Cấu hình rất nhỏ và các giá trị biên cần theo đúng cách làm tròn của hiện thực.

## 4. Ngưỡng mở rộng: size bằng ngưỡng khác size vượt ngưỡng

Mô hình thường dùng là:

```text
threshold ≈ capacity × load factor
```

Với dung lượng là lũy thừa của hai và hệ số mặc định trong các ví dụ thông thường, các ngưỡng là:

| Capacity đã cấp phát | Load factor | Threshold |
|---:|---:|---:|
| 4 | 0.75 | 3 |
| 8 | 0.75 | 6 |
| 16 | 0.75 | 12 |
| 32 | 0.75 | 24 |
| 64 | 0.75 | 48 |

Xét đường gọi `put` thông thường của OpenJDK, không có chính sách va chạm khác kích hoạt mở rộng sớm và chưa ở giới hạn dung lượng: sau khi thêm **khóa mới**, hiện thực tăng `size` rồi kiểm tra việc vượt ngưỡng.

Với capacity `16`, threshold `12`:

- Sau khóa thứ `12`: `size == 12`, chưa vượt ngưỡng này.
- Sau khóa thứ `13`: `size == 13 > 12`, cần mở rộng theo đường kiểm tra tải.
- Ghi lại một khóa đã có chỉ thay giá trị, không tăng `size` để đi theo đường thêm mới đó.

Đừng dùng `>=` thay cho `>` khi dự đoán chính chuỗi `put` của ví dụ. Đồng thời, đừng biến mô hình thành một giả định rằng mọi phương thức khác đều kiểm tra ở cùng đúng một vị trí trong mã nguồn: thao tác hàng loạt và các phương thức tính/cập nhật có thể đi qua những đường hiện thực khác.

### Vì sao dùng dấu xấp xỉ thay vì công thức tuyệt đối cho mọi cấu hình?

Threshold là một số nguyên. Hiện thực có các bước làm tròn, có thể tăng gấp đôi threshold cũ trong một đường mở rộng, và có xử lý riêng ở giới hạn.

Ví dụ, với load factor `0.6f`, một bảng `16` có thể có threshold ban đầu là `9`. Khi mở rộng lên `32` theo đường nhân đôi threshold cũ, ngưỡng là `18`, không phải cứ tính lại `floor(32 × 0.6)` rồi bắt buộc nhận `19`.

Vì vậy:

- Dùng tích `capacity × load factor` để hiểu chính sách và lập kế hoạch thông thường.
- Dùng các giá trị đã xác định của cấu hình mặc định khi giải bài cụ thể.
- Khi cần số nội bộ chính xác cho một cấu hình tùy biến, đối chiếu đúng hiện thực thay vì suy mọi trường hợp từ một công thức rút gọn.

## 5. Initial capacity, làm tròn và cấp phát trễ

OpenJDK tổ chức chiều dài bảng băm theo lũy thừa của hai, trong phạm vi giới hạn của hiện thực. Tham số constructor được chuyển thành dung lượng mục tiêu phù hợp.

Ví dụ các dung lượng mục tiêu thường gặp:

| Đối số initialCapacity | Dung lượng mục tiêu theo cách làm tròn OpenJDK |
|---:|---:|
| 4 | 4 |
| 13 | 16 |
| 16 | 16 |
| 17 | 32 |
| 100 | 128 |
| 134 | 256 |

Điều này không nói bảng đã được cấp phát ngay ở constructor. Trong các constructor quan sát ở OpenJDK, việc cấp phát mảng bucket được trì hoãn đến khi cần chèn dữ liệu.

Với `new HashMap<>(17)`:

```text
Vừa dựng:
    size = 0
    chưa có mảng bucket
    có thông tin dung lượng mục tiêu 32

Sau lần put đầu:
    có bảng dài 32
    threshold thông thường = 24 với hệ số mặc định
```

### Trường nội bộ tên threshold có thể giữ thông tin khác trước cấp phát

Trước khi bảng tồn tại, OpenJDK có thể dùng trường `threshold` để giữ dung lượng mục tiêu. Vì vậy đọc thấy `thresholdField = 32` khi `tableLength = 0` không có nghĩa một bảng rỗng đang cho phép `32` ánh xạ trước khi mở rộng.

Sau khi cấp phát bảng, trường này mới mang vai trò ngưỡng phù hợp với trạng thái bảng trong các ví dụ đang xét.

Đây là một lý do không nên biến các trường private thành giao diện nghiệp vụ. Tên một trường nội bộ không phải hợp đồng về ý nghĩa duy nhất của nó trong mọi trạng thái vòng đời.

### Capacity mặc định 16 có nghĩa gì?

API mô tả dung lượng ban đầu mặc định là `16`, hệ số tải `0.75`. Với hiện thực cấp phát trễ, điều đó tương ứng việc bảng đầu tiên thường có chiều dài `16` khi cần tạo, chứ không buộc một map vừa dựng phải đã có mảng 16 bucket trong bộ nhớ.

Hai phát biểu này không mâu thuẫn: một phát biểu về cấu hình ban đầu, một phát biểu về thời điểm hiện thực cấp phát.

### Không mở rộng vô hạn

Mã nguồn OpenJDK 17u dùng giới hạn `MAXIMUM_CAPACITY = 1 << 30` cho chiều dài bảng. Khi đã tới dung lượng tối đa, hiện thực không tiếp tục nhân đôi mảng vô hạn và có đường điều chỉnh ngưỡng tương ứng.

Giới hạn này nói về số bucket, không phải một lời hứa lưu được từng ấy đối tượng trong heap, cũng không phải cách đồng nhất capacity với số ánh xạ tối đa. Bộ nhớ có thể không đủ từ rất lâu trước khi chạm giới hạn đó. Không cần và không nên cấp một bảng cực lớn để kiểm chứng các công thức của bài.

Hàm lập kế hoạch ở phần sau chủ động từ chối yêu cầu vượt miền của nó. Kiểm tra số học chỉ bảo vệ phép tính và miền mô hình, không bảo đảm một lần cấp phát thực tế sẽ thành công.

## 6. Thí nghiệm đầy đủ: quan sát các mốc mở rộng

Chương trình dưới đây dùng reflection để quan sát hai trường private của hiện thực `HashMap`: `table` và `threshold`.

Đây là **thí nghiệm về hiện thực**, không phải cách viết mã ứng dụng phụ thuộc dung lượng. `HashMap` không công khai hai trường này. Lệnh chạy có `--add-opens java.base/java.util=ALL-UNNAMED` để cho chương trình thí nghiệm trong tiến trình đó truy cập phản chiếu vào gói tương ứng. Chỉ biên dịch mà bỏ tùy chọn khi chạy có thể bị cơ chế đóng gói module từ chối truy cập.

Mã nguồn OpenJDK 17u được dùng để giải thích cơ chế; kết quả dưới đây được đo trên JDK 24.0.2. Tên trường private và các số quan sát không phải giao diện được bảo đảm cho mọi hiện thực hoặc mọi phiên bản.

```java
import java.lang.reflect.Field;
import java.util.HashMap;

public class HashMapCapacityObservationDemo {
    private static final Field TABLE;
    private static final Field THRESHOLD;

    static {
        try {
            TABLE = HashMap.class.getDeclaredField("table");
            THRESHOLD = HashMap.class.getDeclaredField("threshold");
            TABLE.setAccessible(true);
            THRESHOLD.setAccessible(true);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void show(String label, HashMap<?, ?> map)
            throws IllegalAccessException {
        Object[] table = (Object[]) TABLE.get(map);
        int length = table == null ? 0 : table.length;
        System.out.println(label + ": size=" + map.size()
                + ", tableLength=" + length
                + ", thresholdField=" + THRESHOLD.getInt(map));
    }

    record SameHashKey(int id) {
        @Override
        public int hashCode() {
            return 0;
        }
    }

    public static void main(String[] args) throws IllegalAccessException {
        HashMap<Integer, String> map = new HashMap<>(4, 0.75f);
        show("beforeAnyPut", map);
        map.put(1, "v1");
        show("afterPut1", map);
        map.put(2, "v2");
        map.put(3, "v3");
        show("afterPut3", map);
        map.put(1, "updated");
        show("afterOverwrite", map);
        map.put(4, "v4");
        show("afterPut4", map);
        map.put(5, "v5");
        map.put(6, "v6");
        show("afterPut6", map);
        map.put(7, "v7");
        show("afterPut7", map);
        map.remove(7);
        show("afterRemove", map);
        map.clear();
        show("afterClear", map);

        HashMap<Integer, Integer> defaults = new HashMap<>();
        show("defaultBeforePut", defaults);
        defaults.put(1, 1);
        show("defaultAfterPut", defaults);

        HashMap<Integer, Integer> rounded = new HashMap<>(17);
        show("requested17BeforePut", rounded);
        rounded.put(1, 1);
        show("requested17AfterPut", rounded);

        for (float factor : new float[] {0.5f, 0.75f, 1.0f}) {
            HashMap<Integer, Integer> sample = new HashMap<>(16, factor);
            sample.put(1, 1);
            show("factor" + factor, sample);
        }

        HashMap<Integer, Integer> fractional = new HashMap<>(16, 0.6f);
        for (int i = 0; i < 10; i++) {
            fractional.put(i, i);
        }
        show("factor0.6After10", fractional);

        HashMap<SameHashKey, Integer> collision = new HashMap<>(16);
        for (int i = 0; i < 9; i++) {
            collision.put(new SameHashKey(i), i);
        }
        show("collisionAfter9", collision);
    }
}
```

Lưu thành `HashMapCapacityObservationDemo.java`, biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 HashMapCapacityObservationDemo.java
java --add-opens java.base/java.util=ALL-UNNAMED HashMapCapacityObservationDemo
```

Đầu ra đã kiểm chứng:

```text
beforeAnyPut: size=0, tableLength=0, thresholdField=4
afterPut1: size=1, tableLength=4, thresholdField=3
afterPut3: size=3, tableLength=4, thresholdField=3
afterOverwrite: size=3, tableLength=4, thresholdField=3
afterPut4: size=4, tableLength=8, thresholdField=6
afterPut6: size=6, tableLength=8, thresholdField=6
afterPut7: size=7, tableLength=16, thresholdField=12
afterRemove: size=6, tableLength=16, thresholdField=12
afterClear: size=0, tableLength=16, thresholdField=12
defaultBeforePut: size=0, tableLength=0, thresholdField=0
defaultAfterPut: size=1, tableLength=16, thresholdField=12
requested17BeforePut: size=0, tableLength=0, thresholdField=32
requested17AfterPut: size=1, tableLength=32, thresholdField=24
factor0.5: size=1, tableLength=16, thresholdField=8
factor0.75: size=1, tableLength=16, thresholdField=12
factor1.0: size=1, tableLength=16, thresholdField=16
factor0.6After10: size=10, tableLength=32, thresholdField=18
collisionAfter9: size=9, tableLength=32, thresholdField=24
```

### 6.1. Chuỗi capacity 4 → 8 → 16

Trước lần `put` đầu, chưa có bảng, nên `tableLength=0`; `thresholdField=4` là thông tin chuẩn bị cho dung lượng mục tiêu.

Sau lần chèn đầu, bảng dài `4`, ngưỡng là `3`. Ba khóa vẫn nằm ở bảng dài `4`; ghi lại khóa `1` không làm tăng kích thước.

Khóa thứ `4` làm kích thước vượt ngưỡng `3`, bảng tăng lên `8`, ngưỡng `6`. Khóa thứ `6` chưa vượt `6`, còn khóa thứ `7` làm bảng tăng lên `16`, ngưỡng `12`.

### 6.2. Xóa và clear không thu nhỏ bảng trong quan sát này

Sau `remove`, kích thước giảm nhưng chiều dài bảng vẫn là `16`. Sau `clear`, map rỗng nhưng vẫn giữ bảng đã cấp phát.

Trong hiện thực OpenJDK được đối chiếu, `remove` và `clear` không tự thực hiện một cơ chế co bảng đối xứng với tăng bảng. `clear` bỏ các tham chiếu tới ánh xạ trong các bucket, không có nghĩa mọi phần bộ nhớ của cấu trúc lập tức được giải phóng.

Vì vậy không được dùng `size() == 0` để suy ra map chưa cấp phát hay đã trả hết bộ nhớ bảng băm.

### 6.3. Hệ số tải 0.6 và đường làm tròn

Ba hệ số `0.5`, `0.75`, `1.0` tạo các ngưỡng `8`, `12`, `16` cho bảng dài `16` trong mẫu.

Dòng `factor0.6After10` cho thấy bảng đã dài `32` và trường ngưỡng là `18`. Nó minh họa việc không áp công thức làm tròn lại tích một cách tuyệt đối ở mỗi lần tăng dung lượng.

### 6.4. Resize không chỉ có một nguyên nhân

Dòng `collisionAfter9` có kích thước `9`, bảng dài `32`, ngưỡng `24`. Các khóa có mã băm giống nhau nên tập trung vào một bucket.

Ở OpenJDK, khi bucket có quá nhiều nút, đường xử lý để chuyển sang cấu trúc cây còn xét dung lượng tối thiểu của bảng. Nếu bảng còn nhỏ, nó có thể chọn mở rộng trước. Trong mã nguồn đang xét, `MIN_TREEIFY_CAPACITY` là `64`; đây là một ngưỡng khác hoàn toàn với ngưỡng tải toàn map.

Vì vậy một bảng có thể tăng dung lượng dù `size` chưa vượt ngưỡng tải mà ta vừa tính. Không được gộp threshold của cả map với số nút trong một bucket thành cùng một khái niệm.

Bài không đi sâu thuật toán cây. Điều cần rút ra cho chủ đề này là **đủ dung lượng theo load factor không đồng nghĩa đã loại bỏ mọi khả năng mở rộng liên quan va chạm**.

## 7. Dự tính dung lượng khi biết số ánh xạ dự kiến

Giả sử dự kiến tối đa `N` khóa logic khác nhau cùng tồn tại và dùng load factor `f`. Mục tiêu thông thường là chọn một bảng có ngưỡng tải không nhỏ hơn `N`.

Một cách lập kế hoạch là:

```text
Dung lượng cần xét ≈ ceil(N / f)
        ↓
Chọn dung lượng bảng phù hợp với cách làm tròn của hiện thực
        ↓
Kiểm tra ngưỡng thực tế của cấu hình đó có đáp ứng N không
```

Với hệ số mặc định `0.75`, có thể dùng:

```text
ceil(N / 0.75) = ceil(4N / 3)
```

Đây là lập kế hoạch tránh mở rộng **do vượt ngưỡng tải** trong phạm vi cấu hình thông thường, không phải chứng minh mọi thao tác đều không thể mở rộng vì bất kỳ nguyên nhân nào. Va chạm, thao tác hàng loạt, giới hạn hiện thực và cách sử dụng dữ liệu vẫn phải được xem xét.

### 7.1. Vì sao new HashMap<>(100) chưa hẳn đủ cho 100 khóa mà không mở rộng?

Trong hiện thực đang xét:

```text
Đối số 100
    → dung lượng mục tiêu 128
    → ngưỡng mặc định 96

Đưa 100 khóa khác nhau bằng put:
    → lần thêm vượt 96 có thể làm mở rộng theo tải
```

Nếu lấy `ceil(100 / 0.75) = 134` làm đối số:

```text
Đối số 134
    → dung lượng mục tiêu 256
    → ngưỡng mặc định 192
```

Cách thứ hai cấp bảng lớn hơn từ lần cấp phát đầu, tránh bước từ `128` lên `256` do số khóa vượt `96`. Cả hai cuối cùng có thể đi tới bảng dài `256`, nhưng quá trình cấp phát và tổ chức lại dữ liệu khác nhau.

Không nên so sánh `100` và `134` rồi kết luận bộ nhớ cuối chỉ khác nhau `34` ô. Hiện thực làm tròn theo lũy thừa của hai, nên các bước dung lượng có tính rời rạc.

### 7.2. Đúng ngưỡng không bắt buộc cộng thêm một cách máy móc

Với `N=12`, lấy đối số `16` cho bảng có ngưỡng `12` ở hệ số mặc định. Trong chuỗi `put` thông thường đã mô tả, khóa thứ `12` chưa vượt ngưỡng; không phải cứ đạt đúng ngưỡng là phải mở rộng.

Một công thức bảo thủ có thể dự trù thêm, nhưng cần biết mình đang dự trù vì lý do gì. Cộng `1` tùy tiện có thể đẩy dung lượng mục tiêu qua một mốc lũy thừa của hai và tăng đáng kể bảng cấp phát.

Ngược lại, ép kiểu `(int)(N / 0.75)` làm tròn xuống không thể thay thế tùy ý cho phép lấy trần. Hãy kiểm tra phép toán, miền giá trị và cách constructor làm tròn thay vì chỉ sao chép một biểu thức.

### 7.3. N là số khóa đồng thời, không phải số lần ghi

Nếu xử lý một triệu bản cập nhật nhưng chỉ giữ `500` mã hàng khác nhau, không có cơ sở tự động chọn dung lượng theo một triệu lần cập nhật.

Nếu số khóa tăng lên một triệu rồi giảm xuống vài trăm, đó lại là một bài toán vòng đời khác: dung lượng đã tăng có thể được giữ lại. Cần xem mức đỉnh, thời gian sống của map và các lượt duyệt sau khi giảm dữ liệu.

Đoán dung lượng quá cao không miễn phí. Nó có thể làm lần cấp phát đầu tốn hơn và làm việc duyệt bảng kém hiệu quả dù `size` nhỏ.

## 8. Chương trình đầy đủ: lập kế hoạch với hệ số mặc định

Chương trình sau dùng phép tính số nguyên trên `long` để tính `ceil(4N/3)`, tránh việc nhân `int` bị tràn trước khi chia.

Các trường `modeledTable` và `modeledThreshold` là **giá trị mô hình tính theo quy tắc OpenJDK trong phạm vi ví dụ**, không phải trường nội bộ được chương trình đọc ra. `actualSize` mới là số ánh xạ được lấy từ map thật bằng API công khai.

Các trường hợp được chạy đều nhỏ và có `N>0`. Hàm kiểm tra miền lập kế hoạch không phải cam kết rằng hệ thống có đủ heap cho mọi giá trị được chấp nhận.

```java
import java.util.HashMap;

public class HashMapSizingDemo {
    private static int capacityArgumentForDefaultFactor(int expected) {
        if (expected < 0) {
            throw new IllegalArgumentException("negative expected count");
        }
        long requested = (4L * expected + 2) / 3;
        long maximumCapacity = 1L << 30;
        if (requested > maximumCapacity) {
            throw new IllegalArgumentException("outside planning range");
        }
        return (int) requested;
    }

    private static int modeledTableLength(int requested) {
        int capacity = 1;
        while (capacity < requested) {
            capacity <<= 1;
        }
        return capacity;
    }

    public static void main(String[] args) {
        for (int expected : new int[] {1, 3, 4, 12, 13, 100, 1000}) {
            int requested = capacityArgumentForDefaultFactor(expected);
            int tableLength = modeledTableLength(requested);
            int threshold = (int) (3L * tableLength / 4);

            HashMap<Integer, Integer> map = new HashMap<>(requested);
            for (int i = 0; i < expected; i++) {
                map.put(i, i);
            }
            System.out.println("expected=" + expected
                    + ", argument=" + requested
                    + ", modeledTable=" + tableLength
                    + ", modeledThreshold=" + threshold
                    + ", actualSize=" + map.size());
        }

        try {
            capacityArgumentForDefaultFactor(-1);
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            System.out.println("negativeExpected=IllegalArgumentException");
        }
    }
}
```

Lưu thành `HashMapSizingDemo.java`, biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 HashMapSizingDemo.java
java HashMapSizingDemo
```

Đầu ra đã kiểm chứng:

```text
expected=1, argument=2, modeledTable=2, modeledThreshold=1, actualSize=1
expected=3, argument=4, modeledTable=4, modeledThreshold=3, actualSize=3
expected=4, argument=6, modeledTable=8, modeledThreshold=6, actualSize=4
expected=12, argument=16, modeledTable=16, modeledThreshold=12, actualSize=12
expected=13, argument=18, modeledTable=32, modeledThreshold=24, actualSize=13
expected=100, argument=134, modeledTable=256, modeledThreshold=192, actualSize=100
expected=1000, argument=1334, modeledTable=2048, modeledThreshold=1536, actualSize=1000
negativeExpected=IllegalArgumentException
```

### Giải thích các mốc

- `N=3`: đối số `4`, bảng mô hình `4`, ngưỡng `3`.
- `N=4`: đối số tính được `6`, bảng mô hình làm tròn lên `8`, ngưỡng `6`.
- `N=12`: bảng mô hình `16`, ngưỡng `12`.
- `N=13`: đối số `18`, bảng mô hình `32`, ngưỡng `24`.
- `N=100`: đối số `134`, bảng mô hình `256`, ngưỡng `192`.
- `N=1000`: đối số `1334`, bảng mô hình `2048`, ngưỡng `1536`.

Việc map thật có đúng `N` ánh xạ xác nhận vòng chèn và dữ liệu đã tạo. Riêng dòng `actualSize` **không chứng minh map không mở rộng**; muốn quan sát dung lượng thực phải có một cơ chế quan sát phù hợp như thí nghiệm riêng ở phần 6.

Với `N=0`, một map rỗng có thể chưa cần cấp bảng. Không dùng hàm mô hình làm tròn của ví dụ để suy ra một bảng đang tồn tại khi chưa hề chèn dữ liệu.

### Vì sao không cấp một bảng khổng lồ “cho chắc”?

Vì chi phí liên quan không chỉ là số lần resize:

- Bảng lớn hơn giữ nhiều tham chiếu bucket hơn.
- Duyệt `HashMap` có chi phí liên quan cả capacity và size.
- Thời điểm cấp bảng có thể tạo áp lực cấp phát bộ nhớ.
- Ước lượng sai lớn có thể lãng phí suốt thời gian sống của map.
- Giá trị và đối tượng khóa vẫn cần bộ nhớ riêng.

Chọn trước dung lượng là một tối ưu có mục đích, không phải nguyên tắc “càng lớn càng tốt”. Với số lượng nhỏ hoặc khó biết trước, cấu hình mặc định thường đơn giản hơn; thay đổi khi có yêu cầu và bằng chứng cụ thể.

## 9. Resize và rehash thực sự làm gì?

Trong bối cảnh bảng băm, “rehash” thường được dùng để nói việc xây dựng lại cách phân bố các ánh xạ khi dung lượng thay đổi. Nhưng từ này dễ bị hiểu thành “gọi lại phương thức `hashCode` trên mọi khóa”.

Ở OpenJDK đang xét, mỗi nút lưu thông tin băm đã xử lý. Khi tăng dung lượng bảng, hiện thực có thể dùng thông tin đó để xác định vị trí mới mà không gọi lại `key.hashCode()` của từng khóa cũ.

Các bước khái niệm:

1. Xác định dung lượng mới và ngưỡng tương ứng.
2. Cấp mảng bucket mới.
3. Duyệt bảng cũ.
4. Đưa các nút hoặc các nhóm nút về bucket thích hợp trong bảng mới.
5. Để map tiếp tục truy cập ánh xạ thông qua bảng mới.

Các đối tượng khóa và giá trị không được sao chép sâu chỉ vì resize. Với các nhánh thông thường, hiện thực tái sử dụng cấu trúc nút; các nhánh cây có cách xử lý riêng. Không nên mô tả resize như việc tạo lại toàn bộ đối tượng nghiệp vụ.

### 9.1. Vì sao tăng gấp đôi thuận lợi với dung lượng lũy thừa hai?

Trong OpenJDK đang xét, với mã băm đã trộn `h` và chiều dài bảng `m` là lũy thừa hai, chỉ số được tính theo dạng:

```java
// Công thức hiện thực, không phải API Map chung.
int index = h & (m - 1);
```

Khi tăng từ `m` lên `2m`, mặt nạ có thêm đúng một bit cần xét. Một phần tử từ bucket cũ `j` có thể:

- Ở lại bucket `j` nếu bit bổ sung bằng `0`.
- Chuyển sang bucket `j + m` nếu bit bổ sung khác `0`.

Có thể biểu diễn phép phân nhóm bằng `h & m`.

Đây không phải cách chia ngẫu nhiên và không có nghĩa mọi phần tử trong một bucket đều đi cùng nhau. Các phần tử vốn trùng bucket ở bảng nhỏ có thể tách ra khi thêm bit phân biệt mới.

### 9.2. Ví dụ từ capacity 4 lên 8

Với các mã băm nhỏ `1`, `5`, `9`, `13`, phép trộn minh họa của OpenJDK không thay chúng vì phần bit cao đang bằng `0`.

```text
Bảng cũ 4: mặt nạ 3 = ...0011
Bảng mới 8: mặt nạ 7 = ...0111
Bit bổ sung:       4 = ...0100
```

| h | Chỉ số cũ: h & 3 | Bit xét thêm: h & 4 | Chỉ số mới: h & 7 |
|---:|---:|---:|---:|
| 1 | 1 | 0 | 1 |
| 5 | 1 | 4 | 5 |
| 9 | 1 | 0 | 1 |
| 13 | 1 | 4 | 5 |

Cả bốn phần tử đều ở bucket `1` trước đó. Sau tăng bảng, hai phần tử ở `1`, hai phần tử ở `5`.

Việc có thể tách như vậy không đảm bảo mọi va chạm sẽ biến mất. Nếu nhiều khóa có cùng toàn bộ mã băm đã xử lý, tăng số bit dùng để đánh chỉ số vẫn không phân biệt được chúng theo băm. Vì vậy tăng capacity không thay thế một cách phân bố mã băm phù hợp.

### 9.3. Raw hash, mã đã trộn và bucket index khác nhau

OpenJDK có bước trộn mã băm dạng `h ^ (h >>> 16)` cho khóa khác null. Thông tin lưu và dùng để phân bố không nên bị gọi lẫn với một chỉ số bucket cố định.

- `key.hashCode()` là mã băm do lớp khóa cung cấp.
- Phép xử lý của map tạo thông tin băm dùng trong hiện thực.
- Chiều dài bảng cùng thông tin đó quyết định bucket index.

Bucket index có thể đổi khi dung lượng đổi, dù định danh khóa và `key.hashCode()` không đổi. Cách đánh chỉ số bằng mặt nạ cũng không phải phép `hashCode % size`; dùng `size` thay capacity hoặc dùng phần dư âm thiếu xử lý là những mô hình sai.

## 10. Chương trình đầy đủ: mô hình chia bucket khi nhân đôi

Chương trình này **không đọc hoặc sửa một HashMap thật**. Nó tính các chỉ số theo công thức để minh họa bước phân nhóm khi tăng bảng từ `4` lên `8`.

```java
public class HashMapResizeSplitDemo {
    private static int spread(int rawHash) {
        return rawHash ^ (rawHash >>> 16);
    }

    public static void main(String[] args) {
        int oldCapacity = 4;
        int newCapacity = oldCapacity * 2;

        for (int rawHash : new int[] {1, 5, 9, 13}) {
            int storedHash = spread(rawHash);
            int oldIndex = storedHash & (oldCapacity - 1);
            int newIndex = storedHash & (newCapacity - 1);
            int extraBit = storedHash & oldCapacity;
            System.out.println("raw=" + rawHash
                    + ", oldIndex=" + oldIndex
                    + ", extraBit=" + extraBit
                    + ", newIndex=" + newIndex);
        }
    }
}
```

Lưu thành `HashMapResizeSplitDemo.java`, biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 HashMapResizeSplitDemo.java
java HashMapResizeSplitDemo
```

Đầu ra đã kiểm chứng:

```text
raw=1, oldIndex=1, extraBit=0, newIndex=1
raw=5, oldIndex=1, extraBit=4, newIndex=5
raw=9, oldIndex=1, extraBit=0, newIndex=1
raw=13, oldIndex=1, extraBit=4, newIndex=5
```

Hai dòng có `extraBit=0` giữ chỉ số `1`; hai dòng có `extraBit=4` chuyển sang `5 = 1 + 4`.

Mô hình giúp giải thích thuật toán, không phải một API cho ứng dụng tự quản lý bucket. Khi dùng `HashMap` bình thường, ứng dụng chỉ thao tác qua khóa và giá trị; không cần tính trước bucket để tra cứu.

Thứ tự duyệt có thể thay đổi khi cấu trúc bảng thay đổi. `HashMap` vốn không bảo đảm thứ tự; việc một nhóm nút giữ thứ tự tương đối trong một nhánh xử lý nội bộ không tạo cam kết về thứ tự toàn map.

## 11. Chương trình đầy đủ: resize có gọi lại hashCode của khóa cũ không?

Ví dụ dưới đây dùng khóa có bộ đếm lời gọi `hashCode`. Mã băm trả về vẫn là `id` bất biến; bộ đếm chỉ để quan sát, không tham gia quan hệ bằng nhau hoặc giá trị mã băm.

Đây là thí nghiệm về số lần gọi trên hiện thực đang chạy, không phải số lượng lời gọi được API `HashMap` bảo đảm vĩnh viễn. Không viết nghiệp vụ phụ thuộc vào việc một phương thức băm được gọi đúng một số lần nhất định.

```java
import java.util.HashMap;

public class HashMapStoredHashDemo {
    static final class TrackedKey {
        private final int id;
        private int hashCalls;

        TrackedKey(int id) {
            this.id = id;
        }

        @Override
        public int hashCode() {
            hashCalls++;
            return id;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TrackedKey key && id == key.id;
        }
    }

    public static void main(String[] args) {
        TrackedKey[] keys = {
            new TrackedKey(1),
            new TrackedKey(5),
            new TrackedKey(9),
            new TrackedKey(13)
        };
        HashMap<TrackedKey, String> map = new HashMap<>(4, 0.75f);

        for (int i = 0; i < 3; i++) {
            map.put(keys[i], "v" + keys[i].id);
        }
        System.out.println("sizeBeforeFourth=" + map.size());

        map.put(keys[3], "v13");
        System.out.println("sizeAfterFourth=" + map.size());
        for (TrackedKey key : keys) {
            System.out.println("id=" + key.id
                    + ", observedHashCalls=" + key.hashCalls);
        }

        boolean allPresent = true;
        for (TrackedKey key : keys) {
            String value = map.get(new TrackedKey(key.id));
            allPresent &= ("v" + key.id).equals(value);
        }
        System.out.println("allMappingsPresent=" + allPresent);
    }
}
```

Lưu thành `HashMapStoredHashDemo.java`, biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 HashMapStoredHashDemo.java
java HashMapStoredHashDemo
```

Đầu ra đã kiểm chứng:

```text
sizeBeforeFourth=3
sizeAfterFourth=4
id=1, observedHashCalls=1
id=5, observedHashCalls=1
id=9, observedHashCalls=1
id=13, observedHashCalls=1
allMappingsPresent=true
```

### Điều được quan sát

Map được tạo với dung lượng mục tiêu `4` và load factor `0.75f`. Chuỗi thêm bốn khóa khác nhau đi qua mốc mở rộng do tải đã giải thích ở các phần trước.

Sau lần thêm thứ tư, mỗi đối tượng khóa vẫn chỉ ghi nhận một lời gọi `hashCode` từ lần đưa vào của nó. Các khóa cũ không bị gọi lại hàm băm chỉ để phân bố lại trong lần resize này.

Những lần `get` sau đó dùng các đối tượng thăm dò mới. Bộ đếm của các đối tượng mới không được trộn vào các dòng bộ đếm đã in của đối tượng khóa ban đầu. `allMappingsPresent=true` xác nhận các ánh xạ vẫn tra được sau chuỗi chèn.

Từ kết quả này có thể hiểu đúng cách dùng từ “rehash” trong hiện thực đang xét: tổ chức lại bucket dựa trên thông tin băm đã lưu, không bắt buộc tính lại hàm băm nghiệp vụ của mọi khóa cũ.

### Resize không sửa một khóa có hợp đồng sai

Nếu đối tượng khóa bị thay đổi thông tin tham gia `equals` hoặc `hashCode` sau khi đưa vào map, không được mong resize sẽ tự tính lại và sửa trạng thái cho ứng dụng.

Thực tế việc sử dụng thông tin băm đã lưu là một lý do càng không nên dựa vào kỳ vọng đó. Điều cần làm là giữ định danh khóa ổn định trong thời gian làm khóa, không tìm một thời điểm resize để sửa hộ lỗi thiết kế.

Bộ đếm trong ví dụ là dụng cụ thí nghiệm một luồng. Một cách triển khai `hashCode` dùng trong ứng dụng thường nên đơn giản, ổn định và không mang tác động nghiệp vụ theo số lần gọi.

## 12. Chi phí mở rộng và ý nghĩa của O(1) khấu hao

Một lần `put` không phải lúc nào cũng chỉ sửa một nút. Nếu nó kích hoạt mở rộng, map có thể phải cấp bảng mới và đi qua các bucket cùng các ánh xạ cũ.

Chi phí một lần resize liên quan dung lượng bảng cũ và số ánh xạ phải tổ chức lại. Nó có thể đáng kể so với một lần chèn thông thường.

Vì dung lượng thường tăng theo cấp số nhân, các lần mở rộng không xảy ra sau mọi lần thêm. Với một chuỗi chèn và điều kiện phân bố băm hợp lý, tổng chi phí mở rộng được phân bổ qua nhiều lần thao tác. Đó là ý nghĩa của **khấu hao (amortized)** khi nói về chi phí chèn bình thường.

Ví dụ về tổng các chiều dài bảng theo một chuỗi nhân đôi:

```text
16 + 32 + 64 + ... + M
```

Tổng này cùng bậc với `M`, không phải mỗi lần chèn đều phải di chuyển toàn bộ dữ liệu từ đầu. Nhưng một thao tác cụ thể tại mốc mở rộng vẫn có thể chậm hơn hẳn thao tác ngay trước nó.

Cần phân biệt:

- Giả định phân bố băm tốt nói về khả năng các khóa phân bố vào bucket.
- Khấu hao nói về cách chia chi phí các đợt mở rộng trên cả chuỗi thao tác.
- Cả hai không biến từng lần `put` thành một bảo đảm thời gian cố định bất kể dữ liệu và công việc của `hashCode`/`equals`.

Nếu ứng dụng nhạy với một đợt trễ riêng lẻ, giảm số resize bằng ước lượng dung lượng có thể hữu ích. Muốn kết luận về độ trễ thật vẫn cần đo dưới tải phù hợp, gồm cấp phát và thu gom rác.

### Bộ nhớ đỉnh cũng cần xét

Trong lúc mở rộng, bảng mới được cấp phát trong khi việc chuyển dữ liệu từ bảng cũ chưa hoàn tất. Vì vậy tạm thời có thể tồn tại cả hai mảng bucket, làm nhu cầu bộ nhớ đỉnh cao hơn trạng thái ổn định sau đó.

Không nên suy rằng mọi khóa và giá trị đều được nhân đôi sâu; đó không phải cơ chế đã mô tả. Nhưng cũng không nên chỉ nhìn dung lượng cuối rồi bỏ qua chi phí chuyển tiếp.

Số byte thực tế phụ thuộc cấu trúc hiện thực, kích thước tham chiếu, đối tượng khóa/giá trị, cấu hình JVM và dữ liệu. Capacity là số bucket, không phải một đơn vị byte.

## 13. Chọn load factor và vòng đời map theo yêu cầu

### Khi nên cân nhắc initial capacity

Có ích khi biết khá rõ số ánh xạ sẽ được nạp, map có kích thước đáng kể, hoặc muốn giảm các đợt mở rộng trong một giai đoạn xử lý nhạy về độ trễ.

Ước lượng nên tính theo số khóa cùng tồn tại và có biên dự phòng có lý do. Không cấp theo toàn bộ số sự kiện tích lũy nếu chúng liên tục cập nhật cùng một tập khóa nhỏ.

### Khi chưa nên tùy biến load factor

Nếu chưa có vấn đề đo được, thay hệ số mặc định có thể chỉ đổi một chi phí chưa hiểu sang một chi phí khác.

- Giảm hệ số có thể tăng số bucket và chi phí duyệt bảng.
- Tăng hệ số có thể giảm bộ nhớ bảng nhưng tăng công việc trong bucket.
- Mã băm kém hoặc nhiều khóa cùng một hash không được chữa đơn giản bằng việc thay hệ số.
- Khóa có hợp đồng sai vẫn sai bất kể hệ số nào.

Nếu chọn một hệ số khác, hãy kiểm tra cả giai đoạn xây map, tra cứu, cập nhật, duyệt và mức bộ nhớ phù hợp với ứng dụng, thay vì chỉ một phép đo nhỏ.

### Khi map từng rất lớn rồi thu nhỏ dữ liệu

Ở OpenJDK đang xét, xóa các ánh xạ không tự co bảng. Nếu map sống lâu, giảm từ rất nhiều khóa xuống rất ít khóa và tiếp tục được duyệt thường xuyên, bảng lớn có thể vẫn là một chi phí.

Một lựa chọn có thể cân nhắc sau khi đo là dựng một map mới theo quy mô dữ liệu hiện tại và chuyển các ánh xạ còn cần giữ. Đây không phải một lời gọi “trim” công khai trên `HashMap`.

Đoạn minh họa khi đang sở hữu độc quyền nguồn:

```java
HashMap<K, V> rebuilt = new HashMap<>(source);
```

Đoạn trên dùng các tham số kiểu `K`, `V` và biến `source` có sẵn trong ngữ cảnh ứng dụng; không phải chương trình độc lập. Nó chép các ánh xạ sang map khác, không sao chép sâu khóa/giá trị và không tự cập nhật các tham chiếu hoặc view vẫn trỏ tới map cũ.

Constructor chép có chính sách của nó; không mặc định mọi tham số tùy biến của map nguồn đều được giữ nguyên. Việc chuyển sang map mới cũng có chi phí cấp phát và chép, nên không nên thực hiện máy móc sau mỗi lần xóa.

### Resize không phải cơ chế đồng bộ

`HashMap` không tự cung cấp đồng bộ cho nhiều luồng thay đổi cấu trúc. Chọn capacity lớn để “chắc không resize” không làm nó trở thành một cấu trúc an toàn cho mọi truy cập đồng thời.

Một lần chèn mới đã là thay đổi cấu trúc, bất kể có resize hay không. Khi nhiều luồng chia sẻ dữ liệu, cần thiết kế phối hợp hoặc chọn cấu trúc có hợp đồng phù hợp. Các ví dụ của bài dùng một luồng để tập trung vào chính sách dung lượng.

## 14. Những cách hiểu sai cần tránh

| Cách hiểu sai | Cách đọc đúng |
|---|---|
| Capacity là size | Capacity đếm bucket; size đếm ánh xạ |
| Constructor `HashMap<>(N)` thêm sẵn N phần tử | Map vẫn rỗng; N liên quan dung lượng ban đầu |
| Load factor 0.75 nghĩa 75% bucket chắc chắn có dữ liệu | Đây là hệ số tải theo số ánh xạ và dung lượng, không phải phép đếm bucket không rỗng |
| Đạt đúng threshold là luôn resize | Chuỗi `put` thông thường xét việc vượt ngưỡng; các đường hiện thực khác cần xem riêng |
| Chỉ vượt load threshold mới được resize | Xử lý va chạm có thể kích hoạt mở rộng khi bảng còn nhỏ |
| Rehash luôn gọi lại hashCode của mọi khóa cũ | OpenJDK có thể phân bố lại bằng thông tin băm đã lưu |
| HashMap tự giảm capacity sau remove/clear | Hiện thực quan sát giữ bảng; không có cơ chế co tự động đối xứng ở các thao tác đó |
| Capacity càng lớn thì càng nhanh | Bộ nhớ và chi phí duyệt có thể tăng |
| Tránh resize là đủ để HashMap an toàn đa luồng | Đồng bộ là yêu cầu riêng, không được giải quyết bằng dung lượng |
| Đổi load factor sẽ chữa equals/hashCode sai | Hợp đồng khóa phải được sửa ở lớp khóa và cách sử dụng |

## 15. Tự kiểm tra trước khi chạy ví dụ

### Câu 1: Capacity 16, hệ số 0.75

Trong chuỗi `put` khóa mới thông thường, giả sử không có mở rộng sớm do va chạm, threshold là `12`. Sau khóa thứ `12` có bắt buộc mở rộng do tải chưa? Sau khóa thứ `13` thì sao?

**Đáp án:** Khóa thứ `12` mới đạt ngưỡng, chưa vượt. Khóa thứ `13` vượt ngưỡng và đi qua đường mở rộng do tải đã mô tả.

### Câu 2: Ghi một khóa nhiều lần

Map có `size=12`, threshold `12`. Gọi `put` một nghìn lần chỉ để thay giá trị của một khóa đã có. Có thể suy số ánh xạ tăng thêm một nghìn không?

**Đáp án:** Không. Ghi lại khóa logic đang có thay giá trị, không tạo thêm một nghìn ánh xạ và không đi theo đường tăng size do thêm khóa mới.

### Câu 3: Load factor không phải occupancy

Map có `12` ánh xạ và bảng dài `16`. Có chắc có đúng `12` bucket không rỗng không?

**Đáp án:** Không. Nhiều ánh xạ có thể nằm cùng bucket. Mức tải trung bình `12/16` không đồng nghĩa số bucket không rỗng.

### Câu 4: Đối số 100

Theo quy tắc OpenJDK đang xét, đối số initial capacity `100` thường hướng tới bảng dài bao nhiêu và ngưỡng mặc định bao nhiêu?

**Đáp án:** Bảng mục tiêu `128`, threshold thông thường `96` sau khi cấp phát. Vì vậy chưa thể dùng đối số `100` như lời hứa chứa `100` khóa khác nhau mà không mở rộng theo tải.

### Câu 5: Kế hoạch cho 100 khóa

Với load factor `0.75`, `ceil(100/0.75)` bằng bao nhiêu? Dung lượng mô hình sau làm tròn là bao nhiêu?

**Đáp án:** Đối số dự tính `134`, bảng mô hình `256`, ngưỡng mặc định `192`. Điều này lập kế hoạch theo tải, không loại trừ mọi nguyên nhân mở rộng khác.

### Câu 6: Mảng chưa tồn tại

Reflection thấy `tableLength=0`, `thresholdField=32` ngay sau `new HashMap<>(17)`. Có được diễn giải là một bảng dài `32` đã cấp phát không?

**Đáp án:** Không. Đó là thông tin dung lượng mục tiêu trước cấp phát trong hiện thực này. Phải phân biệt trạng thái trường nội bộ trước và sau khi bảng tồn tại.

### Câu 7: Bucket cũ 1, capacity 4

Một mã băm đã xử lý có `h & 3 = 1` và `h & 4 = 4`. Khi bảng tăng từ `4` lên `8`, chỉ số mới là gì?

**Đáp án:** `1 + 4 = 5`. Bit bổ sung quyết định chuyển sang nhóm cao.

### Câu 8: Tất cả hash giống nhau

Nếu các khóa khác nhau có cùng toàn bộ mã băm đã xử lý, nhân đôi bảng có chắc phân tán chúng sang các bucket khác nhau không?

**Đáp án:** Không. Cùng thông tin băm dẫn tới cùng các bit dùng để chọn chỉ số. Cần phân biệt giữa thêm dung lượng và chất lượng phân bố mã băm.

### Câu 9: Rehash có sửa khóa bị đổi không?

Một khóa bị sửa thông tin xác định mã băm khi đang trong map. Có nên chèn thêm dữ liệu để mong resize gọi lại hashCode và chữa vấn đề không?

**Đáp án:** Không. Hiện thực có thể dùng mã băm đã lưu, và điều kiện ổn định của khóa đã bị vi phạm. Phải sửa thiết kế và cách sử dụng khóa.

### Câu 10: clear rồi size 0

Sau khi map từng lớn, `clear()` đưa size về `0`. Có bảo đảm mảng bucket cũng về trạng thái chưa cấp phát không?

**Đáp án:** Không. Thí nghiệm và mã nguồn OpenJDK cho thấy bảng có thể được giữ lại. Kích thước logic bằng 0 không đủ mô tả bộ nhớ cấu trúc.

### Câu 11: Hệ số 0.6

Một bảng `16` có threshold `9`, sau đó đường mở rộng nhân đôi threshold cũ khi bảng lên `32`. Ngưỡng có bắt buộc là `floor(32 × 0.6)=19` không?

**Đáp án:** Không. Đường nhân đôi đang xét tạo `18`. Công thức tích là mô hình chính sách; số nội bộ chính xác phụ thuộc các bước hiện thực.

### Câu 12: Một put chậm tại mốc mở rộng

Điều đó có mâu thuẫn với việc nói chi phí chèn thường được phân tích O(1) khấu hao trong điều kiện thích hợp không?

**Đáp án:** Không. Khấu hao phân bổ chi phí các đợt mở rộng qua chuỗi thao tác; không hứa mọi lần chèn riêng lẻ có cùng thời gian.

## 16. Thực hành: lập bảng dự báo rồi đối chiếu

### Phần A — Cấu hình mặc định

Không dùng reflection trước khi dự đoán. Xét một chuỗi thêm khóa khác nhau bằng `put`, không có xử lý va chạm kích hoạt mở rộng sớm:

1. Bảng hiện tại dài `8`, load factor `0.75`.
2. Viết threshold tương ứng.
3. Dự đoán chiều dài bảng sau khi size lần lượt là `5`, `6`, `7`.
4. Sau size `7`, thay giá trị một khóa đã có rồi xóa một khóa.
5. Nêu điều gì thay đổi ở size, điều gì không tự thay đổi về capacity trong hiện thực đang xét.

**Kết quả cần đạt:** ngưỡng ban đầu `6`; size `5` và `6` vẫn ở bảng `8`; lần thêm để lên `7` đi tới bảng `16`. Ghi lại khóa không tăng size; xóa làm size giảm, không tự co bảng theo mô tả OpenJDK của bài.

Sau đó có thể sửa chương trình quan sát để đối chiếu trên runtime đang dùng, vẫn ghi rõ đây là quan sát trường private.

### Phần B — Dung lượng cho một đợt nạp

Dự kiến `500` khóa khác nhau, hệ số mặc định:

1. Tính `ceil(500/0.75)`.
2. Làm tròn dung lượng mục tiêu theo lũy thừa của hai.
3. Tính ngưỡng mặc định của bảng đó.
4. So với việc truyền thẳng `500` làm initial capacity.

**Đáp án:** đối số dự tính `667`, bảng mục tiêu `1024`, ngưỡng `768`. Truyền thẳng `500` hướng tới bảng `512`, ngưỡng `384`, nên đợt nạp `500` khóa có thể phải mở rộng theo tải.

### Phần C — Đo mà không nhầm điều đã đo

Nếu dùng chương trình cộng thời gian để so hai cấu hình, hãy ghi rõ:

- Thời gian đang gồm tạo map, cấp phát bảng, thêm khóa hay chỉ tra cứu?
- Dữ liệu có cùng số khóa logic và cùng đặc tính băm không?
- Có phải đang so một lần chạy ngắn chịu ảnh hưởng khởi động/JIT/GC không?
- Có quan sát bộ nhớ và các lượt duyệt sau khi nạp không?
- Kết luận là về dữ liệu và môi trường nào, hay đang suy quá rộng?

Mục tiêu của bài thực hành là hiểu và kiểm chứng chính sách dung lượng, không biến một phép đo ngẫu nhiên thành lời khẳng định mọi chương trình phải dùng cùng một cấu hình.

## Tài liệu đối chiếu

- [Java 17 — HashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html): capacity, load factor, chính sách rehash, constructor và chi phí duyệt.
- [Mã nguồn OpenJDK 17u — HashMap.java](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/HashMap.java): cấp phát trễ, `tableSizeFor`, `putVal`, `resize`, chia bucket và xử lý dung lượng tối thiểu khi chuyển bucket sang cây.

Các chương trình có lệnh biên dịch/chạy đầy đủ. Mã được kiểm chứng bằng `javac --release 17 -encoding UTF-8` và chạy trên JDK 24.0.2. Những công thức mô hình, phép đo lời gọi và truy cập private được đánh dấu theo đúng phạm vi; không xem chúng là API công khai của mọi `Map` hay mọi Java runtime.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-map-004">Hiệu năng mong đợi, trường hợp xấu và chất lượng hàm băm</a>

<details>
<summary>Xem chi tiết</summary>

# Hiệu năng mong đợi, trường hợp xấu và chất lượng hàm băm

## 1. Vì sao câu “HashMap có O(1)” chưa đủ?

HashMap được dùng rộng rãi vì có thể tra khóa và cập nhật ánh xạ rất hiệu quả. Tuy nhiên, nếu chỉ ghi nhớ “get là O(1)” mà bỏ điều kiện, người học dễ ngạc nhiên khi bộ dữ liệu khác làm chương trình chậm đi rõ rệt.

Độ phức tạp không chỉ phụ thuộc tên lớp. Nó còn phụ thuộc cách phân bố khóa, số ánh xạ so với dung lượng bảng, chi phí hashCode/equals, thời điểm tăng bảng và đường tìm bên trong bucket.

Tài liệu HashMap nêu hiệu năng hằng số cho các thao tác cơ bản khi hàm băm phân tán các phần tử phù hợp giữa các bucket. Đây là điều kiện quan trọng để hiểu kỳ vọng sử dụng. [Tài liệu HashMap, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html).

Trong bài này, ta sẽ tách ba câu hỏi:

- Khi dữ liệu được phân bố phù hợp, mỗi thao tác thường cần xét bao nhiêu ứng viên?
- Khi nhiều khóa cùng dồn vào một nơi, công việc có thể tăng tới đâu?
- Làm sao thiết kế và kiểm chứng hàm băm mà không nhầm số phép so sánh với thời gian chạy?

Ví dụ sẽ đếm các lời gọi hashCode, equals và compareTo. Đây là bằng chứng về một phần công việc thuật toán, không phải phép đo độ trễ của hệ thống.

## 2. Mô hình chi phí và các đại lượng cần biết

Gọi:

| Ký hiệu | Ý nghĩa |
| --- | --- |
| N | Số ánh xạ trong Map |
| C | Số bucket của bảng khi bảng đã được cấp phát |
| L | Số ứng viên tập trung trong một bucket đang xét |
| α = N/C | Số ánh xạ trung bình trên một bucket |
| H | Chi phí tính mã băm của khóa dò |
| E | Chi phí một phép so bằng khóa |
| P | Chi phí một phép so thứ tự nếu được dùng |

Đừng đọc α như số phần tử bắt buộc trong từng bucket. Một bảng có N = 8, C = 16 có α = 0,5, nhưng vẫn có thể dồn cả tám ánh xạ vào một bucket và để 15 bucket khác rỗng.

Một lần tìm có thể được hình dung là:

~~~text
tính mã băm khóa
+ chọn bucket
+ lọc ứng viên bằng thông tin mã băm
+ so bằng hoặc so thứ tự khi cần
+ đọc giá trị nếu đã tìm được khóa.
~~~

Phần tính vị trí thường là một số thao tác bit nhỏ. Phần đắt có thể nằm ở số ứng viên hoặc chính các phương thức do khóa cung cấp.

Nếu tạm coi H, E và P đều là hằng số nhỏ, ta có thể tập trung vào số nút cần xét. Nếu một khóa chứa chuỗi dài hoặc cấu trúc phức tạp, cần quay lại tính cả chi phí của chính khóa.

Ký hiệu O(1) theo N cũng không nói rằng thao tác bằng không hoặc không cấp phát bộ nhớ. Nó chỉ mô tả tốc độ tăng công việc theo đại lượng được chọn trong mô hình.

## 3. Hiệu năng mong đợi và chi phí khấu hao khác nhau

### Mong đợi: dựa vào phân bố dữ liệu

Nếu các khóa được phân vào bucket đủ đều và mức tải được kiểm soát, một lần tra thường chỉ gặp một nhóm ứng viên nhỏ. Trong mô hình bảng băm dùng chuỗi với giả thiết phân bố đều, số ứng viên trung bình có liên hệ với α; khi α bị giữ trong một khoảng hằng số, công việc tìm cũng có kỳ vọng hằng số.

Không có nghĩa mỗi bucket luôn dài bằng α, hoặc mỗi lần get chỉ kiểm tra đúng một nút. Đó là một đánh giá trên phân bố và điều kiện sử dụng, không phải bảo đảm tuyệt đối cho một khóa cụ thể.

Dữ liệu phân bố đều cũng không bắt buộc phải được sinh ngẫu nhiên. Chẳng hạn, với một miền số nguyên thích hợp và mã băm dùng đủ thông tin, các mã liên tiếp có thể phân bố tốt. Điều cần xem là phân bố thực tế sau khi đi qua cơ chế chọn bucket.

### Khấu hao: phân bổ công việc đắt lên cả chuỗi thao tác

Một lần put thêm khóa mới có thể cần tăng bảng, cấp phát và bố trí lại các nút. Lần đó có thể tốn nhiều công việc hơn các lần thêm thông thường.

Nếu tăng dung lượng theo từng bước phù hợp và băm được phân bố tốt, tổng công việc của các đợt tăng bảng được chia trên nhiều lần thêm. Vì vậy, put thường được mô tả là O(1) kỳ vọng theo khấu hao trong mô hình phù hợp.

“Khấu hao” không có nghĩa mọi lần put đều nhanh như nhau. Một yêu cầu đơn lẻ vẫn có thể gặp lần tăng bảng và có độ trễ cao hơn.

Hai khái niệm không thay thế nhau:

| Cụm từ | Giải thích điều gì? |
| --- | --- |
| Chi phí mong đợi | Điều kiện phân bố khóa làm trung bình phải xét ít ứng viên |
| Chi phí khấu hao | Một số thao tác đắt được phân bổ trên cả chuỗi thao tác |
| Chi phí trường hợp xấu | Một thao tác hoặc một dữ liệu bất lợi có thể đòi bao nhiêu công việc |

Khi đánh giá một API theo độ trễ yêu cầu đơn lẻ, không lấy một thông lượng trung bình làm bằng chứng rằng sẽ không có lần cập nhật chậm.

## 4. Các thao tác HashMap không có cùng chi phí

Bảng dưới là mô hình thực hành với khóa có hashCode/equals chi phí hằng số, và các điều kiện phân bố được nêu rõ:

| Thao tác | Đánh giá thường dùng | Điều cần giữ trong đầu |
| --- | --- | --- |
| get, containsKey, remove theo khóa | Kỳ vọng O(1) khi phân bố tốt | Trường hợp va chạm bất lợi có thể phải xét O(N) ứng viên |
| put thêm ánh xạ | Kỳ vọng O(1) theo khấu hao khi phân bố tốt | Một lần có thể phải tăng bảng hoặc xử lý bucket đông |
| put thay giá trị của khóa đã có | Cần tìm đúng khóa trước | Không được gọi là miễn phí chỉ vì size không tăng |
| containsValue | Có thể phải quét bảng và nút | Không có cùng chỉ mục băm theo giá trị |
| Duyệt toàn bộ Map | Phụ thuộc O(C + N) | Cấp phát bảng quá thưa có thể làm việc duyệt tốn hơn |
| size | O(1) trong triển khai thông thường này | Không cần duyệt để đếm lại mọi ánh xạ |

Ở một đợt tăng bảng, công việc cấp phát và phân bố lại có thể phụ thuộc C + N. Với mức tải được kiểm soát, C thường tăng cùng bậc với N nên ta hay nói gọn chi phí đợt tăng là O(N). Nếu người gọi chủ động yêu cầu một dung lượng ban đầu rất lớn, chi phí cấp phát còn phải được đánh giá theo dung lượng yêu cầu, không chỉ số ánh xạ hiện có.

Một vòng “với mỗi giá trị cần tìm, gọi containsValue” có thể biến bài toán thành nhiều lượt quét. Đừng áp dụng kết luận O(1) của tra khóa cho một phép hỏi ở phía giá trị.

Khi chỉ cần đọc cả khóa và giá trị, duyệt entrySet giúp lấy chúng từ cùng ánh xạ, tránh thêm lượt get theo từng khóa. Dù vậy, một lượt duyệt HashMap vẫn phụ thuộc cả bảng và số nút, chứ không tự trở thành một phép tra hằng số.

## 5. Chất lượng hàm băm: đúng hợp đồng trước, phân bố tốt sau

### Điều kiện đúng bắt buộc

Nếu hai đối tượng bằng nhau theo equals thì hashCode của chúng phải bằng nhau. Ngược lại, các đối tượng không bằng nhau vẫn được phép có cùng mã băm.

Một hàm băm trả 0 cho mọi khóa có thể giữ điều kiện bắt buộc này. Nó không sai chỉ vì gây va chạm; vấn đề là hiệu năng có thể rất kém. [Hợp đồng Object.hashCode, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html).

Trong khi đó, nếu equals coi hai khóa là cùng một đối tượng nghiệp vụ nhưng hashCode cho hai mã khác nhau, đó là lỗi tính đúng. HashMap có thể tìm ở những nơi không phù hợp trước khi có cơ hội dùng equals. Không xử lý lỗi này như một chuyện tối ưu tốc độ.

### Phân bố có ích cho dữ liệu thực

Một hàm băm phù hợp cần dùng thông tin nhận diện có ý nghĩa và tạo đủ khác biệt trên miền khóa thực tế. Các lựa chọn sau thường tạo phân bố xấu:

~~~text
Trả một hằng số cho mọi khóa.
Chỉ dùng một cờ boolean cho hàng triệu khóa.
Chỉ dùng độ dài tên khi đa số tên có cùng độ dài.
Bỏ qua phần số thứ tự trong khóa gồm nhóm và số thứ tự.
Ghép nhiều trường bằng một phép có quá nhiều đối xứng trên dữ liệu thực.
~~~

Ví dụ khóa có hai trường x, y. Nếu hash chỉ là x + y thì các cặp (1, 9), (2, 8), (3, 7) cùng mã 10. Điều này không tự vi phạm hợp đồng, nhưng dữ liệu có cấu trúc như vậy có thể làm va chạm nhiều hơn dự kiến.

Mục tiêu không phải “mã băm khác nhau cho mọi khóa”. Điều đó không thể bảo đảm cho một miền đối tượng lớn hơn miền int. Mục tiêu là giữ đúng hợp đồng và tránh một cách băm tự làm mất phần lớn thông tin phân biệt của dữ liệu cần lưu.

### Mã khác nhau vẫn có thể chung bucket

Bảng có số bucket hữu hạn. Hai mã khác nhau vẫn có thể bị ánh xạ tới cùng một chỉ số. Đánh giá chất lượng chỉ bằng việc đếm số hashCode phân biệt chưa mô tả hết phân bố bucket.

OpenJDK có xử lý một phần bit của mã băm trước khi chọn vị trí. Cơ chế đó có thể giúp những khác biệt ở phần bit cao tham gia phân bố, nhưng không tạo ra khác biệt khi mọi khóa đều trả cùng một mã. Một bước trộn nội bộ không thay được thiết kế hashCode phù hợp.

### Dữ liệu khóa phải ổn định

Tính đúng và hiệu năng đều khó diễn giải nếu khóa thay đổi các trường tham gia equals/hashCode sau khi được đưa vào Map. Khi đó, bạn không chỉ đang gặp một phép tra chậm; bạn có thể không tìm được ánh xạ qua đường tra bình thường.

Dùng khóa bất biến hoặc dữ liệu nhận diện ổn định giúp phân tích phân bố có ý nghĩa. Một record có trường String và số nguyên là ví dụ đơn giản; record chứa một collection có thể sửa vẫn cần xem xét nội dung bên trong.

## 6. Trường hợp xấu: khi nhiều khóa chung một mã

Trong một bucket dạng chuỗi, nếu có L khóa cần phân biệt, tìm một khóa không tồn tại có thể phải xét toàn bộ L ứng viên. Khi L = N, số ứng viên cần xét có thể tăng tuyến tính theo tổng số ánh xạ.

Không chỉ số bucket chung mới quan trọng. Nếu nhiều ứng viên có cùng toàn bộ mã băm nội bộ, bước so mã không loại được chúng và cần thêm phép nhận diện khóa. Một hàm băm hằng số tạo đúng tình huống đó.

Sự khác biệt khi N tăng:

~~~text
Phân bố tốt:
N tăng nhưng nhóm ứng viên trung bình vẫn nhỏ
-> chi phí tra kỳ vọng không tăng theo N.

Tất cả chung mã:
N tăng làm nhóm khó phân biệt lớn hơn
-> một lần tìm vắng có thể phải xét gần toàn bộ nhóm.
~~~

Nếu mỗi lần thêm một khóa mới cũng phải loại trừ nhiều khóa cũ trước khi xác nhận chưa tồn tại, tổng công việc xây Map trong một dữ liệu bất lợi có thể tăng mạnh, thậm chí theo bậc hai. Không thể lấy chi phí kỳ vọng của một lần put trong điều kiện tốt để cam kết tổng thời gian cho dữ liệu va chạm có hệ thống.

Nâng dung lượng không tự giải quyết tình trạng mọi khóa có cùng mã. Chúng tiếp tục chọn cùng một bucket ở mỗi dung lượng. Muốn hiểu cải thiện từ việc tăng bảng, phải phân biệt va chạm do mặt nạ bucket với trùng hoàn toàn mã băm.

## 7. Bucket dạng cây giúp gì và không bảo đảm điều gì?

OpenJDK có thể chuyển bucket đông sang cấu trúc cây theo các điều kiện triển khai. Cây cân bằng giảm chiều cao, nhưng muốn tìm theo một nhánh còn cần đủ thông tin để quyết định nên đi về phía nào.

Khi mã băm khác nhau, hoặc khi các khóa có quan hệ Comparable phù hợp để phân biệt các khóa cùng mã, thuật toán có thể dùng thông tin đó chọn hướng. Với các khóa có cùng mã, không so thứ tự được và không bằng khóa đang xét, một phép tìm có thể phải thăm nhiều nhánh để không bỏ sót một khóa bằng nhau.

Đường tìm `TreeNode.find` trong OpenJDK 17 thể hiện nhánh thử nhiều cây con khi không xác định được hướng so sánh phù hợp. Vì thế, “bucket đã thành cây” không tự suy ra “mọi get chắc chắn O(log N)” với mọi loại khóa. [Mã nguồn HashMap, OpenJDK 17](https://raw.githubusercontent.com/openjdk/jdk/jdk-17%2B35/src/java.base/share/classes/java/util/HashMap.java).

Một hình minh họa:

~~~text
                 nhiều khóa có cùng mã
                         |
          +--------------+--------------+
          |                             |
so được thứ tự phân biệt        không có hướng phân biệt
          |                             |
có thể chọn một nhánh            có thể phải xét nhiều nhánh
mỗi bước tìm                     để loại trừ khóa bằng nhau
~~~

Đây là cách đọc thuật toán, không phải một tuyên bố rằng Comparable luôn cần được thêm vào mọi khóa chỉ để tối ưu HashMap.

Nếu dùng Comparable, quan hệ so sánh phải đúng và phù hợp dữ liệu. Một compareTo luôn trả 0 cho những khóa khác nhau không cung cấp khả năng chọn hướng đang cần. Không thêm một thứ tự tùy tiện hoặc vi phạm hợp đồng rồi kỳ vọng nó sửa được một hàm băm kém.

Trong bài thí nghiệm, lớp khóa có thứ tự dùng id, cũng là dữ liệu xác định equals. Điều đó tạo một ví dụ rõ ràng về hai trường hợp cùng mã: không có thứ tự phân biệt và có thứ tự phân biệt.

## 8. Chương trình Java 17+ đếm công việc khi tra cứu

Tệp `HashMapCostDemo.java` tạo ba kiểu khóa bất biến:

| Lớp | Cách băm | Có Comparable không? |
| --- | --- | --- |
| SpreadKey | Trả id | Không |
| SameHashKey | Luôn trả 0 | Không |
| OrderedSameHashKey | Luôn trả 0 | Có, so id |

Chương trình dùng các id từ 0 tới N - 1, rồi tìm id -1 không có trong Map. Trước lượt tìm này, các bộ đếm được đặt lại nên không gồm chi phí dựng Map. Sau đó chương trình còn kiểm tra một khóa có thật để bảo đảm thí nghiệm không chỉ tạo một tập dữ liệu không tra được.

Dung lượng khởi tạo 64 được chọn để các trường hợp nhiều khóa cùng mã có thể đi vào cơ chế bucket cây của OpenJDK 17. Không dùng phản chiếu truy cập bảng riêng tư. Các con số đếm cụ thể là quan sát của thí nghiệm được đối chiếu với mã nguồn, không phải hợp đồng số lần gọi cố định cho mọi phiên bản JVM.

~~~java
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;

public class HashMapCostDemo {
    static long hashCalls;
    static long equalsCalls;
    static long compareCalls;

    record SpreadKey(int id) {
        @Override
        public int hashCode() {
            hashCalls++;
            return id;
        }

        @Override
        public boolean equals(Object other) {
            equalsCalls++;
            return other instanceof SpreadKey key && id == key.id;
        }
    }

    record SameHashKey(int id) {
        @Override
        public int hashCode() {
            hashCalls++;
            return 0;
        }

        @Override
        public boolean equals(Object other) {
            equalsCalls++;
            return other instanceof SameHashKey key && id == key.id;
        }
    }

    record OrderedSameHashKey(int id) implements Comparable<OrderedSameHashKey> {
        @Override
        public int hashCode() {
            hashCalls++;
            return 0;
        }

        @Override
        public boolean equals(Object other) {
            equalsCalls++;
            return other instanceof OrderedSameHashKey key && id == key.id;
        }

        @Override
        public int compareTo(OrderedSameHashKey other) {
            compareCalls++;
            return Integer.compare(id, other.id);
        }
    }

    static void resetCounts() {
        hashCalls = 0;
        equalsCalls = 0;
        compareCalls = 0;
    }

    static <K> void measure(
            String label, int n, IntFunction<K> keyFactory) {
        Map<K, Integer> map = new HashMap<>(64);
        for (int id = 0; id < n; id++) {
            map.put(keyFactory.apply(id), id);
        }

        resetCounts();
        Integer missing = map.get(keyFactory.apply(-1));
        long missHash = hashCalls;
        long missEquals = equalsCalls;
        long missCompare = compareCalls;

        Integer found = map.get(keyFactory.apply(n - 1));
        boolean correct = map.size() == n
                && missing == null
                && found != null
                && found == n - 1;
        if (!correct) {
            throw new AssertionError("Unexpected map result");
        }

        System.out.println(label + " n=" + n
                + " missingHash=" + missHash
                + " missingEquals=" + missEquals
                + " missingCompare=" + missCompare
                + " correct=" + correct);
    }

    public static void main(String[] args) {
        for (int n : new int[] {32, 64, 128}) {
            measure("spread", n, SpreadKey::new);
            measure("sameHash", n, SameHashKey::new);
            measure("orderedSameHash", n, OrderedSameHashKey::new);
        }

        Map<SameHashKey, Integer> small = new HashMap<>(64);
        for (int id = 0; id < 4; id++) {
            small.put(new SameHashKey(id), id);
        }
        resetCounts();
        Integer missing = small.get(new SameHashKey(-1));
        System.out.println("small collision n=4 missing=" + missing
                + " equals=" + equalsCalls);

        Map<SpreadKey, Integer> map = new HashMap<>();
        map.put(new SpreadKey(7), 10);
        Integer previous = map.put(new SpreadKey(7), 20);
        System.out.println("equal key replacement old=" + previous
                + " size=" + map.size()
                + " value=" + map.get(new SpreadKey(7)));
    }
}
~~~

Biên dịch và chạy với JDK 17 hoặc mới hơn hỗ trợ phiên bản đích:

~~~shell
javac --release 17 -encoding UTF-8 HashMapCostDemo.java
java HashMapCostDemo
~~~

Chương trình dùng cú pháp và API tương thích Java 17. Nó không cần thư viện ngoài, không đo thời gian và không in thứ tự duyệt Map.

Stdout chính xác:

~~~text
spread n=32 missingHash=1 missingEquals=0 missingCompare=0 correct=true
sameHash n=32 missingHash=1 missingEquals=33 missingCompare=0 correct=true
orderedSameHash n=32 missingHash=1 missingEquals=5 missingCompare=3 correct=true
spread n=64 missingHash=1 missingEquals=0 missingCompare=0 correct=true
sameHash n=64 missingHash=1 missingEquals=65 missingCompare=0 correct=true
orderedSameHash n=64 missingHash=1 missingEquals=6 missingCompare=4 correct=true
spread n=128 missingHash=1 missingEquals=0 missingCompare=0 correct=true
sameHash n=128 missingHash=1 missingEquals=129 missingCompare=0 correct=true
orderedSameHash n=128 missingHash=1 missingEquals=7 missingCompare=5 correct=true
small collision n=4 missing=null equals=4
equal key replacement old=10 size=1 value=20
~~~

### Giải thích các cột

`missingHash` là số lời gọi hashCode trong lần tìm khóa vắng. `missingEquals` là số lời gọi equals của khóa. `missingCompare` là số lời gọi compareTo. Chúng được chụp lại trước khi chạy phép kiểm tra khóa có thật.

`correct=true` xác nhận size đúng N, khóa -1 không được tìm thấy và khóa N - 1 vẫn lấy đúng giá trị. Nó không có nghĩa dữ liệu có hiệu năng tốt.

Các bộ đếm tĩnh chỉ dành cho quan sát trong chương trình một luồng. Không đưa cách đếm này vào một phép đo hiệu năng nhiều luồng rồi coi nó là không có tác động lên việc chạy.


## 9. Đọc kết quả: công việc tăng theo dữ liệu nào?

### Khóa phân bố phù hợp

Với SpreadKey, các id của ví dụ tạo mã băm đủ phân biệt cho những bảng đang dùng. Tìm -1 cho `missingEquals=0` ở cả ba kích thước.

Không được kết luận lượt tìm không làm việc gì. Nó vẫn gọi hashCode một lần, xử lý vị trí và kiểm tra thông tin trong bảng. Trong ví dụ, mã của khóa vắng không khớp các mã đã lưu ở nơi được chọn, nên không cần equals để loại ứng viên.

Điều quan sát có giá trị là khi N tăng trong những bộ này, công việc so bằng không tăng theo N. Nó phù hợp với kỳ vọng của một bảng được phân bố tốt, nhưng không chứng minh mọi bộ khóa dùng hashCode trả id đều không bao giờ có va chạm.

### Khóa cùng mã và không có thứ tự

Với SameHashKey:

| N | missingEquals |
| --- | --- |
| 32 | 33 |
| 64 | 65 |
| 128 | 129 |

Số phép so bằng tăng gần tuyến tính theo N. Phép tìm vắng cần loại trừ các khóa cùng mã, thay vì được dẫn thẳng theo một quan hệ thứ tự phân biệt.

Tại sao N + 1 thay vì N? Trong đường tìm quan sát ở triển khai này, có một kiểm tra nút đầu trước khi đi vào hàm tìm trên cây; nút đó có thể được so bằng lại trong đường tìm cây. Hằng số thêm một không thay đổi kết luận về tốc độ tăng.

Trường hợp nhỏ N = 4 có bốn lần equals, phù hợp với việc xét một chuỗi nhỏ. Từ các con số này, không tự rút ra số nút chính xác cần để mọi Map chuyển cây. Chuyển dạng còn phụ thuộc điều kiện triển khai, và ta không đo trực tiếp mọi sự kiện nội bộ bằng bộ đếm equals.

### Khóa cùng mã nhưng có thứ tự nhất quán

Với OrderedSameHashKey:

| N | missingEquals | missingCompare |
| --- | --- | --- |
| 32 | 5 | 3 |
| 64 | 6 | 4 |
| 128 | 7 | 5 |

Trong thí nghiệm, khi số khóa tăng gấp đôi, số phép so tăng chậm. Quan hệ compareTo theo id cho phép chọn hướng thay vì thử nhiều nhánh.

Đó là minh chứng cho tác dụng của thông tin thứ tự trong một trường hợp cụ thể. Không phải một khuyến nghị viết hashCode hằng số rồi thêm Comparable để biến nó thành thiết kế băm tốt. Cả nhóm vẫn dồn vào một bucket, và quá trình hoạt động còn tốn các phép so thứ tự.

Muốn phát biểu một bảo đảm cho ứng dụng, cần xác định loại khóa, các quan hệ so sánh, miền dữ liệu và phiên bản triển khai. Ba dòng đo ở đây không thay thế cho phân tích đó.

### Thay giá trị theo khóa bằng nhau

Dòng cuối cho thấy put một khóa mới bằng khóa cũ giữ size bằng một và thay giá trị từ 10 thành 20. Một phép thêm thử theo khóa cần phát hiện khóa đã có; chất lượng băm không được phép làm thay đổi ngữ nghĩa “mỗi khóa có một ánh xạ”.

Sự đúng của kết quả và số công việc là hai tiêu chí riêng. Nhóm SameHashKey vẫn tra đúng nhưng cần nhiều phép so hơn.

## 10. Thiết kế một khóa băm thực tế

### Chọn đúng dữ liệu nhận diện

Ví dụ một khách hàng được xác định bởi khu vực và số khách hàng:

~~~java
record CustomerKey(String region, long number) {
}
~~~

Với dữ liệu thành phần phù hợp, record tạo equals/hashCode dựa trên các thành phần. Đây thường là điểm khởi đầu dễ kiểm tra hơn một lớp tự viết thiếu một trong hai phương thức.

Nếu tự cài hashCode, hãy dùng cùng ý nghĩa nhận diện như equals. Một hàm băm chỉ lấy độ dài region và bỏ qua number có thể khiến nhiều khách hàng của cùng khu vực đổ vào rất ít mã.

Đừng thêm dữ liệu không thuộc danh tính chỉ để “làm mã đa dạng”: nếu equals bỏ qua trường đó mà hashCode lại phụ thuộc nó, hai đối tượng bằng nhau có thể có mã khác nhau. Tối ưu phân bố không được phá điều kiện đúng.

Nếu nghiệp vụ so một chuỗi không phân biệt hoa thường hoặc có chuẩn hóa mã, quy tắc đó phải được áp dụng nhất quán khi tạo khóa và tính cả equals/hashCode. Một phía chuẩn hóa còn phía kia không chuẩn hóa sẽ tạo dữ liệu khó tra hoặc đồng nhất sai khóa.

### Trộn trường có chủ đích

`Objects.hash` hoặc cơ chế sinh mã của record có thể giúp triển khai một khóa nhiều trường theo mẫu quen thuộc. Chúng không bảo đảm không có va chạm trên mọi bộ dữ liệu, và vẫn phải được dùng trên đúng các trường nhận diện.

Không tự sửa hashCode bằng một công thức ngẫu nhiên chỉ vì một phép đo nhỏ cho ít va chạm hơn. Hãy giữ một thiết kế có hợp đồng rõ, thử trên dữ liệu đại diện và chỉ tối ưu thêm khi đã thấy vấn đề đáng kể.

Với dữ liệu có cấu trúc, chú ý những nhóm thường gặp: mã cùng tiền tố, nhiều số liên tiếp, nhiều khóa cùng loại hoặc nhiều trường có giá trị cố định. Chất lượng cần được đánh giá trên những nhóm đó, không chỉ trên một tập sinh ngẫu nhiên.

### Đúng bằng nhau chưa đủ rẻ để so

Một khóa có mã phân bố tốt nhưng hashCode mỗi lần phải đi qua hàng nghìn phần tử có thể vẫn làm get chậm. Tương tự, equals có thể phải xét nhiều dữ liệu dù chỉ được gọi một lần.

Có thể chọn một định danh bất biến gọn hơn nếu nó thật sự biểu diễn cùng danh tính cần tra. Không cắt bớt thông tin cần thiết rồi coi việc hai thực thể bị nhập làm một là một cải thiện hiệu năng.

Đối với khóa bất biến mà việc tính hash thực sự đắt, một thiết kế có thể tính và giữ lại mã một cách an toàn. Việc đó phải bảo đảm dữ liệu liên quan không thay đổi; giữ một mã cũ trong khóa có thể sửa sẽ làm lỗi hợp đồng, không phải một tối ưu hợp lệ.

Cũng không suy ra mọi kiểu khóa đều tính lại cùng lượng dữ liệu ở mọi lần hashCode. Chẳng hạn, một số lớp có cơ chế tối ưu nội bộ; muốn đánh giá chi phí cụ thể phải đọc hợp đồng hoặc triển khai của đúng kiểu đang dùng.

## 11. Dung lượng và hệ số tải: tối ưu theo cả thời gian lẫn bộ nhớ

Hệ số tải liên quan thời điểm tăng bảng. Giữ bảng quá chật có thể tăng số ứng viên; giữ bảng quá thưa làm tốn nhiều vị trí hơn và ảnh hưởng các công việc phải duyệt bảng.

Nếu dự kiến N ánh xạ và hệ số tải f, mô hình tính dung lượng cần thiết để tránh chạm ngưỡng là khoảng:

~~~text
C cần ít nhất gần N / f.
~~~

Ví dụ N = 1000, f = 0,75:

~~~text
1000 / 0,75 ≈ 1333,33.
~~~

Trong OpenJDK 17, dung lượng bảng theo lũy thừa của hai, nên mức dung lượng thực phù hợp có thể lớn hơn con số chia này. Đây là lý do không đồng nhất đối số dung lượng khởi tạo với “số phần tử được chứa mà chắc chắn không tăng bảng”.

Khi làm việc với Java 17, constructor `new HashMap<>(initialCapacity)` nhận một dung lượng ban đầu yêu cầu. Nó không phải hạn ngạch số ánh xạ và không làm Map tự từ chối khóa mới khi size đạt đối số đó.

Chọn dung lượng nên dựa trên số liệu thực và kiểu công việc:

| Công việc chính | Điều cần cân nhắc |
| --- | --- |
| Nạp một lần nhiều ánh xạ đã biết trước quy mô | Dung lượng phù hợp có thể giảm các đợt tăng bảng |
| Map nhỏ và thay đổi thường xuyên | Cấp phát rất lớn thường không có cơ sở |
| Duyệt báo cáo nhiều lần | Bảng quá thưa có thể làm phải xét nhiều vị trí trống |
| Dữ liệu va chạm hoàn toàn mã băm | Chỉ tăng dung lượng không tách được các khóa đó |
| Hệ thống nhạy độ trễ một yêu cầu | Quan sát các lần tăng bảng, cấp phát và thu gom bộ nhớ, không chỉ trung bình |

Việc cấp sẵn nhiều hơn có thể giảm một số công việc về sau nhưng tăng chi phí bộ nhớ và cấp phát đầu kỳ. Không có một dung lượng hoặc hệ số tải được chọn tùy tiện là tốt nhất cho mọi ứng dụng.

Trong các cấu trúc dùng vùng nhớ động, số đối tượng nút, số ô bảng và dữ liệu khóa/giá trị cùng góp phần vào tổng bộ nhớ. Không đánh giá bộ nhớ chỉ bằng size.

Nếu Map đã quá lớn so với nhu cầu thật, hãy xem xét vòng đời và cách thay thế cấu trúc khi phù hợp. Điều đó là một quyết định quản lý dữ liệu; không tự xóa rồi dựng lại giữa lúc người khác đang sử dụng hoặc giả định một thao tác dọn dữ liệu sẽ cung cấp mọi bảo đảm về dung lượng.

## 12. Đo thời gian để trả lời đúng câu hỏi

Bộ đếm phương thức giúp nhìn ra số ứng viên cần so, nhưng không phản ánh toàn bộ thời gian chạy. Một lần equals rất rẻ có thể khác xa một lần equals trên dữ liệu lớn; số lần gọi bằng nhau cũng chưa bao gồm bộ nhớ đệm CPU, cấp phát, JIT hoặc thu gom bộ nhớ.

Khi cần đo tốc độ trong JVM, JMH là công cụ do OpenJDK phát triển để xây dựng phép đo ở nhiều quy mô. [Dự án JMH](https://github.com/openjdk/jmh).

Một phép đo có ý nghĩa nên định nghĩa rõ:

- Đo tra thành công, tra không thành công hay một tỷ lệ hỗn hợp?
- Đo get trên Map đã dựng, hay đo cả tạo khóa và dựng Map?
- Đo put thêm khóa mới, thay giá trị cũ hay một đợt có tăng bảng?
- Dữ liệu là khóa cùng tham chiếu đã lưu hay đối tượng mới bằng dữ liệu khóa?
- Kích thước và phân bố khóa có đại diện cho ứng dụng không?
- Có cần quan sát độ trễ từng lượt và mức sử dụng bộ nhớ ngoài thông lượng không?

Tách bước dựng dữ liệu khỏi phần cần đo nếu mục tiêu là tra cứu. Nếu mục tiêu là nạp dữ liệu, phải đưa việc dựng vào phần đo. Cùng một mã có thể trả lời hai câu hỏi khác nhau tùy cách đặt ranh giới đo.

Để kết quả có thể diễn giải, cần giai đoạn làm nóng, các lần chạy độc lập phù hợp, giữ kết quả được sử dụng và kiểm soát những công việc ngoài mục tiêu như in văn bản trong phần đo. Ghi lại JDK, cấu hình và dữ liệu thử để so sánh có cơ sở.

Không suy ra một kết luận chung về HashMap từ việc bấm giờ vài thao tác trên một Map rất nhỏ. Một mẫu mà toàn bộ dữ liệu vừa nằm trong bộ nhớ đệm và không có resize đang mô tả một điều kiện thuận lợi cụ thể.

Chương trình bài học cố ý không dùng System.nanoTime để đưa ra một con số nanosecond có vẻ tuyệt đối. Nó chọn câu hỏi hẹp hơn có thể kiểm tra rõ: số phép gọi phương thức khóa thay đổi thế nào khi phân bố và khả năng so thứ tự thay đổi.

Nếu kết quả thời gian không giống dự đoán từ số lần equals, hãy tìm thành phần chi phí còn thiếu trong mô hình. Không vội kết luận phân tích độ phức tạp sai chỉ vì một dữ liệu nhỏ có hằng số khác.

## 13. Những suy luận cần tránh

| Suy luận | Điều bị bỏ qua | Cách phát biểu chính xác hơn |
| --- | --- | --- |
| Mọi get đều O(1) trong mọi dữ liệu | Điều kiện phân bố và chi phí khóa | Kỳ vọng hằng số khi các điều kiện phù hợp |
| Put O(1) nên không có lần chậm | Chi phí tăng bảng và cấp phát | Đánh giá khấu hao và quan sát lượt riêng |
| Bucket thành cây nên mọi tra cứu O(log N) | Khả năng định hướng tìm theo mã/thứ tự | Nêu rõ loại khóa và đường tìm hỗ trợ |
| HashCode hằng số là sai hợp đồng | Hợp đồng chỉ yêu cầu khóa bằng nhau cùng mã | Có thể đúng ngữ nghĩa nhưng kém hiệu năng |
| Nhiều mã khác nhau nghĩa là không có va chạm bucket | Mã vẫn phải nén về số bucket hữu hạn | Xét phân bố sau chọn bucket |
| Tăng dung lượng sẽ chữa mọi va chạm | Trùng toàn bộ mã băm | Cần xem lại thiết kế mã và dữ liệu |
| Ít equals là tổng chi phí nhỏ | Hash, so thứ tự, bộ nhớ và công việc khác | Dùng mô hình chi phí đầy đủ |
| correct=true nghĩa là nhanh | Chỉ kiểm tra kết quả chức năng | Đánh giá tính đúng và hiệu năng riêng |
| Comparable là cách thay thế một hashCode tốt | Thứ tự chỉ hỗ trợ một số trường hợp | Giữ chất lượng băm là mục tiêu chính |
| HashMap an toàn nhiều luồng vì phép tra nhanh | Tính đồng thời độc lập với độ phức tạp | Dùng hợp đồng truy cập phù hợp |
| Đo get bằng cùng đối tượng đã lưu đại diện mọi truy vấn | Cùng tham chiếu có thể tránh equals | Thử cả khóa cùng đối tượng và khóa mới bằng dữ liệu |
| Chỉ cần dữ liệu ngẫu nhiên | Dữ liệu thực có cấu trúc và nhóm va chạm | Kết hợp dữ liệu đại diện và trường hợp bất lợi |

Các thông số hiệu năng cần gắn với điều kiện cụ thể. Một hệ thống có nhiều truy vấn khóa vắng có thể bộc lộ va chạm khác với một hệ thống chủ yếu tra cùng vài khóa nóng. Một Map phải dựng lại liên tục cũng khác một Map được dựng một lần rồi chỉ đọc.

## 14. Bài tập và lời giải

### Bài 1 — Giải thích O(1) đúng điều kiện

Một người nói: “HashMap.get luôn mất cùng một số bước dù dữ liệu thế nào”. Cần sửa câu này ra sao?

**Lời giải:** get có hiệu quả kỳ vọng hằng số khi mã băm phân bố tốt và chi phí thao tác khóa được tính phù hợp. Một bucket đông hoặc hàm hashCode/equals đắt có thể làm một lượt tra tốn hơn. O(1) cũng không khẳng định mọi lần có đúng cùng số bước.

### Bài 2 — α có giới hạn bucket dài nhất không?

N = 8, C = 16, α = 0,5. Có thể kết luận mỗi bucket chứa tối đa một phần tử không?

**Lời giải:** không. α là trung bình trên toàn bảng; dữ liệu có thể dồn nhiều hoặc toàn bộ ánh xạ vào một bucket. Muốn đánh giá trường hợp xấu phải xét phân bố chứ không chỉ tỷ lệ trung bình.

### Bài 3 — Kỳ vọng và khấu hao

Một lần put gặp tăng bảng nên chậm hơn rõ rệt. Có mâu thuẫn với mô tả put có chi phí kỳ vọng khấu hao hằng số không?

**Lời giải:** không. Khấu hao phân bổ các lần đắt lên cả chuỗi thao tác; không hứa mọi thao tác đơn lẻ đều rẻ. Tính kỳ vọng về phân bố khóa là một điều kiện khác cần giữ.

### Bài 4 — Hash hằng số có luôn làm sai kết quả?

Mọi khóa trả hashCode bằng 0 nhưng equals so đúng dữ liệu bất biến. Map có bắt buộc trả sai kết quả không?

**Lời giải:** không. Các khóa bằng nhau vẫn cùng mã, nên điều kiện cần có thể được giữ. Kết quả vẫn có thể đúng nhưng tra cứu phải làm nhiều công việc hơn. Chương trình cho correct=true với SameHashKey nhưng số equals tăng tuyến tính.

### Bài 5 — Diễn giải N + 1

Ở N = 128, SameHashKey có missingEquals bằng 129. Có phải HashMap đang lưu 129 khóa không?

**Lời giải:** không; size vẫn 128. Đường tìm của triển khai có thể kiểm tra lại một nút ở giai đoạn khác, tạo thêm một lần equals. Số lần gọi không phải số nút phân biệt được lưu.

### Bài 6 — Cây có đủ bảo đảm logarit không?

Hai khóa không bằng nhau có cùng mã, không so thứ tự được. Vì sao cây cân bằng vẫn chưa bảo đảm mỗi bước chỉ đi một nhánh tìm?

**Lời giải:** chiều cao nhỏ không tự cho biết khóa bằng dữ liệu đang nằm về phía nào khi mã và thông tin thứ tự không phân biệt được. Để không bỏ sót khóa bằng nhau, thuật toán có thể phải tìm nhiều nhánh. Cần xét quy tắc định hướng, không chỉ hình dạng cây.

### Bài 7 — Thiết kế khóa nhiều trường

CustomerKey có region và number, equals xét cả hai. HashCode chỉ dùng độ dài region. Đây là lỗi đúng/sai hay trước hết là nguy cơ hiệu năng?

**Lời giải:** nếu các đối tượng bằng nhau luôn có cùng region và number thì độ dài region cũng bằng nhau, nên cách băm có thể vẫn giữ điều kiện bắt buộc. Nhưng nó mất gần hết thông tin phân biệt giữa các khách hàng và có thể phân bố rất kém. Một thiết kế dùng các thành phần nhận diện phù hợp sẽ dễ đạt phân bố hữu ích hơn.

### Bài 8 — Hàm băm dùng trường equals không xét

Equals chỉ xét mã khách hàng, còn hashCode dùng thêm thời điểm cập nhật. Hai đối tượng cùng mã nhưng thời điểm khác nhau có thể gặp vấn đề gì?

**Lời giải:** chúng bằng nhau theo equals nhưng có thể có mã băm khác, vi phạm hợp đồng. Đây là lỗi tính đúng, không chỉ va chạm hay chậm. Loại bỏ sự không nhất quán trước khi tối ưu tốc độ.

### Bài 9 — Chọn dung lượng

Dự kiến 1000 ánh xạ và hệ số tải 0,75. Vì sao không nên mặc định đối số constructor 1000 là số ánh xạ chắc chắn chứa được trước khi tăng bảng?

**Lời giải:** đối số là dung lượng yêu cầu, còn ngưỡng tăng phụ thuộc dung lượng thực và hệ số tải. Mô hình cần khoảng N/f, rồi còn quy tắc dung lượng của triển khai. Đồng thời phải cân nhắc bộ nhớ, không tăng tùy tiện tới một con số rất lớn.

### Bài 10 — Đo nhầm công việc

Một phép đo dựng Map, sinh khóa, gọi get và in kết quả cho mỗi lượt. Có thể gọi toàn bộ thời gian đó là độ trễ get của Map đã dựng không?

**Lời giải:** không. Nó gồm nhiều công việc khác với tra cứu. Nếu cần đo get, chuẩn bị dữ liệu ngoài phần đo và sử dụng kết quả theo một phương pháp phù hợp. Nếu muốn đo cả quy trình, mô tả kết quả là thời gian quy trình đó.

### Bài 11 — Đếm equals bằng 0

SpreadKey cho missingEquals bằng 0. Có thể kết luận lượt tra không đọc bảng hoặc không có chi phí không?

**Lời giải:** không. Nó vẫn tính hashCode, xử lý vị trí và có các kiểm tra nội bộ. Bộ đếm chỉ cho biết không cần gọi equals trong trường hợp đã quan sát.

### Bài 12 — Cấu trúc giá trị và khóa đắt

Map có N rất nhỏ, nhưng hashCode của khóa phải duyệt một danh sách dài M phần tử mỗi lần. Khi nói get là O(1) theo N, còn thiếu điều gì?

**Lời giải:** còn chi phí theo M của chính khóa. Một mô hình đầy đủ phải tính H, không chỉ số nút trong bucket. Nếu danh sách còn có thể đổi khi khóa đang được lưu, đó còn là nguy cơ vi phạm tính ổn định của khóa.

## 15. Quy trình đánh giá hiệu năng có cơ sở

Trước hết, kiểm tra equals/hashCode và sự ổn định của khóa để bảo đảm kết quả đúng. Sau đó mô tả dữ liệu thật: số ánh xạ, nhóm khóa, tỷ lệ truy vấn thành công và thất bại, số lần cập nhật, nhu cầu duyệt.

Dùng một thí nghiệm kiểm soát được để nhìn số công việc khi cần giải thích cơ chế. Khi cần con số thời gian, tạo phép đo có ranh giới rõ và phù hợp JVM. Tách kết luận về một bộ dữ liệu cụ thể khỏi lời hứa cho toàn ứng dụng.

HashMap có hiệu năng mong đợi tốt khi mã băm giữ đúng hợp đồng, phân bố phù hợp và chi phí khóa được kiểm soát. Trường hợp xấu vẫn cần được hiểu, nhất là khi nhiều khóa cùng mã hoặc khi một lần cập nhật phải làm thêm công việc cấp phát và tăng bảng.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-map-005">Va chạm, cây hóa bucket và giới hạn của chi tiết cài đặt</a>

<details>
<summary>Xem chi tiết</summary>

# Va chạm, cây hóa bucket và giới hạn của chi tiết cài đặt

## 1. Vì sao một HashMap cần xử lý nhiều khóa cùng bucket?

Một HashMap có số bucket hữu hạn, trong khi ứng dụng có thể đưa vào rất nhiều khóa. Nhiều khóa có thể cùng được định hướng vào một bucket. Đó là **va chạm (collision)**, một tình huống bình thường mà cấu trúc phải xử lý.

Nếu một bucket chỉ có vài ánh xạ, một chuỗi node đơn giản có thể đáp ứng tốt. Nếu rất nhiều khóa dồn vào cùng bucket, việc phải kiểm tra nhiều ứng viên liên tiếp có thể làm tra cứu chậm.

Trong các cài đặt OpenJDK hiện đại, một bucket đông có thể được chuyển sang cấu trúc cây cân bằng, gọi là **cây hóa bucket (treeification)**. Cơ chế này giúp đối phó với một số trường hợp va chạm nhiều.

Cần giữ hai tầng kiến thức riêng:

| Tầng | Điều cần dựa vào |
| --- | --- |
| Hợp đồng công khai | Khóa duy nhất theo phép bằng, put/get/remove đúng ánh xạ, chính sách null và thứ tự |
| Chi tiết cài đặt | Bucket dạng danh sách hay cây, các ngưỡng chuyển đổi, bố trí node và thuật toán bên trong |

Bài này giải thích cơ chế cây hóa và cách quan sát nó, đồng thời chỉ ra những điều không được biến thành giả định của mã ứng dụng.

## 2. Va chạm không có nghĩa hai khóa bằng nhau

### 2.1. Cùng hash và cùng bucket là hai mức khác nhau

Trong mô hình OpenJDK 17u, một hash sau bước trộn được dùng với độ dài bảng n để chọn chỉ số:

```java
int index = (n - 1) & hash;
```

Đây là biểu thức giải thích cơ chế nội bộ, không phải API người gọi HashMap phải tự tính.

Với n bằng 64:

```text
63 & 1  = 1
63 & 65 = 1
```

Hai hash khác nhau vẫn cùng bucket. Nếu bảng tăng lên 128:

```text
127 & 1  = 1
127 & 65 = 65
```

chúng có thể tách sang hai bucket.

Ngược lại, nếu nhiều khóa trả cùng hashCode và có cùng hash sau trộn, tăng số bucket không tự làm các hash ấy khác nhau. Chúng vẫn cùng đường chọn chỉ số.

### 2.2. Equals vẫn quyết định cùng khóa logic

Hai khóa cùng hash nhưng equals false vẫn phải giữ các ánh xạ riêng. Put một khóa bằng khóa đã có cập nhật ánh xạ; put một khóa khác trong cùng bucket thêm ánh xạ khác.

Cây hóa không đổi quy tắc này. Nó đổi cách tổ chức ứng viên trong bucket, không đổi một HashMap thành cấu trúc loại trùng theo một tiêu chí mới.

Các đặc tính công khai của lớp có thể đối chiếu ở [HashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html).

## 3. Từ chuỗi node tới cây cân bằng

### 3.1. Bucket dạng danh sách

Một hình dung đơn giản:

```text
table[index]
     ↓
   node A → node B → node C → ... → node M
```

Nếu phải tìm một khóa ở gần cuối hoặc chứng minh khóa không có, cấu trúc có thể phải xét nhiều node trong nhóm. Với m ứng viên trong một bucket, một lượt tìm tuyến tính có thể tốn O(m).

Độ dài của một bucket khác với số ánh xạ toàn map. Map có thể có nhiều bucket gần rỗng nhưng một bucket rất đông do hàm băm phân bố kém.

### 3.2. Bucket dạng cây

OpenJDK dùng cây đỏ–đen cho các tree bin. Đây là một dạng cây tìm kiếm tự cân bằng, giữ chiều cao theo cấp logarit thay vì để chuỗi nhánh kéo dài tùy ý.

Hình khái niệm:

```text
table[index]
     ↓
   một node gốc
      /    \
   nhánh  nhánh
    / \    / \
   ...    ...
```

Hình này không mô tả một bố trí bắt buộc của các khóa cụ thể. Cài đặt còn giữ các liên kết phục vụ những thao tác khác như duyệt bucket; không phải bỏ toàn bộ liên kết node cũ rồi lộ ra một API cây mới cho người gọi.

Tree node có nhiều liên kết và thông tin cân bằng hơn node thông thường. Vì thế cây hóa có chi phí bộ nhớ và duy trì cấu trúc; không có lý do phải dùng cây cho mọi bucket chỉ có một, hai phần tử.

### 3.3. Đây là cải tiến cài đặt, không phải một kiểu Map mới

Các cải tiến trong Java 8 giới thiệu việc dùng cây cân bằng khi nhiều khóa va chạm. Tài liệu lịch sử cũng nhấn mạnh thứ tự duyệt HashMap vốn không được cam kết; xem [Collections Framework Enhancements in Java SE 8 của OpenJDK](https://cr.openjdk.org/~naoto/idna2008/docs_www/technotes/guides/collections/changes8.html).

Người gọi vẫn dùng HashMap với cùng thao tác put/get/remove. Không có yêu cầu đổi mã nghiệp vụ sang một lớp TreeBucket hoặc tự duy trì màu của node.

## 4. Ba hằng số 8, 6, 64 phải được đọc đúng ngữ cảnh

Trong mã nguồn OpenJDK 17u:

| Hằng số | Giá trị | Vai trò |
| --- | --- | --- |
| TREEIFY_THRESHOLD | 8 | Tham gia quyết định chuyển bucket đông sang cây khi thêm |
| UNTREEIFY_THRESHOLD | 6 | Ngưỡng chuyển nhóm đã tách về node thường trong đường resize |
| MIN_TREEIFY_CAPACITY | 64 | Độ dài bảng tối thiểu để cây hóa theo đường treeifyBin |

Đối chiếu [HashMap.java của OpenJDK 17u](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/HashMap.java).

Những số này không phải các hằng public trong giao diện Map hoặc một cam kết số học của Java SE cho mọi phiên bản. Chúng là điểm bắt đầu để đọc một cài đặt cụ thể.

### 4.1. 64 là số bucket của bảng, không phải size bắt buộc

Một map có thể chỉ có chín ánh xạ nhưng bảng đã có 64 bucket do lựa chọn dung lượng ban đầu. Khi các ánh xạ cùng dồn vào một bucket, điều kiện độ dài bảng có thể đã đủ.

Ngược lại, không hiểu “phải có ít nhất 64 entry trong map thì mới được cây hóa”. Đây là nhầm capacity với size.

Khởi tạo HashMap với một dung lượng cũng không có nghĩa constructor lập tức công khai một mảng có thể truy cập. Việc cấp phát và làm tròn dung lượng là chi tiết cài đặt; ví dụ chỉ dùng tham số để tạo điều kiện cho thí nghiệm.

### 4.2. Bảng nhỏ có thể được tăng thay vì cây hóa

Trong đường treeifyBin của OpenJDK 17u, nếu độ dài bảng nhỏ hơn 64 thì cài đặt gọi resize thay vì lập cây cho bucket đó.

Lý do hợp lý về cơ chế là: nhiều hash khác nhau đang va chạm vì bảng còn nhỏ có thể tách được khi bảng có thêm bit chỉ số. Tăng bảng là một cách xử lý trước khi trả chi phí tree node.

Với những khóa có hash hoàn toàn giống nhau, resize không giải tán được nhóm ấy. Sau khi bảng đủ lớn và nhóm tiếp tục đông, cây hóa vẫn có thể diễn ra.

### 4.3. Không học “put phần tử thứ 8 luôn cây hóa”

TREEIFY_THRESHOLD bằng 8 không thay thế cho việc đọc nơi sử dụng nó.

Trong đường putVal thông thường của OpenJDK 17u, kiểm tra chuyển đổi nằm khi thêm một node mới vào một chuỗi đã có. Với bucket đang có tám node, lần put thêm khóa khác tiếp theo có thể kích hoạt đường treeify. Các đường cập nhật khác còn có cách đếm và thời điểm kiểm tra riêng.

Vì vậy cần nêu đủ: cài đặt nào, đường thao tác nào, số node trước hay sau thêm, độ dài bảng là bao nhiêu. Không dùng một câu thuộc lòng bỏ qua các điều kiện ấy.

## 5. Thí nghiệm quan sát Node và TreeNode

### 5.1. Cách quan sát và giới hạn

Chương trình sau dùng khóa có hashCode hằng để ép va chạm. Khóa triển khai Comparable theo id, nhất quán với phép bằng của record.

Nó lấy một Map.Entry qua API duyệt rồi in tên lớp chạy thực tế bằng getClass().getSimpleName(). Các tên Node/TreeNode là chi tiết cài đặt được quan sát, không phải kiểu public mà mã nghiệp vụ nên kiểm tra để quyết định xử lý.

Chương trình không truy cập trường private của HashMap, không cần reflection mở module hoặc cờ mở gói. Dù vậy, việc in được tên lớp không biến tên lớp ấy thành một hợp đồng ổn định.

Lưu thành **BucketTreeObservation.java**:

```java
import java.util.HashMap;
import java.util.Map;

public class BucketTreeObservation {
    record Key(int id) implements Comparable<Key> {
        @Override
        public int hashCode() {
            return 7;
        }

        @Override
        public int compareTo(Key other) {
            return Integer.compare(id, other.id);
        }
    }

    private static String observedEntryClass(Map<Key, Integer> map) {
        return map.entrySet().iterator().next().getClass().getSimpleName();
    }

    public static void main(String[] args) {
        Map<Key, Integer> largeStart = new HashMap<>(64);
        for (int id = 1; id <= 7; id++) {
            largeStart.put(new Key(id), id * 10);
        }
        System.out.println("observed start-64 after 7: " + observedEntryClass(largeStart));
        largeStart.put(new Key(8), 80);
        System.out.println("observed start-64 after 8: " + observedEntryClass(largeStart));
        largeStart.put(new Key(9), 90);
        System.out.println("observed start-64 after 9: " + observedEntryClass(largeStart));
        System.out.println("get equal key 5: " + largeStart.get(new Key(5)));
        System.out.println("replace equal key 5 old: " + largeStart.put(new Key(5), 500));
        System.out.println("size after replacement: " + largeStart.size());
        System.out.println("remove equal key 5: " + largeStart.remove(new Key(5)));
        System.out.println("key 6 preserved: " + largeStart.get(new Key(6)));

        Map<Key, Integer> smallStart = new HashMap<>(16);
        for (int id = 1; id <= 9; id++) {
            smallStart.put(new Key(id), id);
        }
        System.out.println("observed start-16 after 9: " + observedEntryClass(smallStart));
        smallStart.put(new Key(10), 10);
        System.out.println("observed start-16 after 10: " + observedEntryClass(smallStart));
        smallStart.put(new Key(11), 11);
        System.out.println("observed start-16 after 11: " + observedEntryClass(smallStart));
        System.out.println("small-start size: " + smallStart.size());
        System.out.println("small-start get 11: " + smallStart.get(new Key(11)));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 BucketTreeObservation.java
java BucketTreeObservation
```

Các chương trình trong bài được biên dịch với --release 17 và chạy trên OpenJDK 24.0.2. Stdout quan sát được:

```text
observed start-64 after 7: Node
observed start-64 after 8: Node
observed start-64 after 9: TreeNode
get equal key 5: 50
replace equal key 5 old: 50
size after replacement: 9
remove equal key 5: 500
key 6 preserved: 60
observed start-16 after 9: Node
observed start-16 after 10: Node
observed start-16 after 11: TreeNode
small-start size: 11
small-start get 11: 11
```

**Các dòng observed ghi nhận cài đặt của môi trường đã nêu, không phải thứ tự chuyển lớp mà mọi Java 17+ phải bảo đảm.** Cờ --release 17 kiểm tra ngôn ngữ/API mục tiêu và sinh bytecode tương ứng; JVM chạy chương trình vẫn là bản 24.0.2.

### 5.2. Đọc kết quả theo đường thao tác

Với điểm bắt đầu dung lượng 64, trong thí nghiệm put:

- Sau bảy và tám khóa va chạm, entry quan sát vẫn là Node.
- Sau khóa thứ chín, entry quan sát là TreeNode.
- Get, cập nhật bằng khóa tương đương và remove vẫn giữ đúng ý nghĩa Map.

Với điểm bắt đầu nhỏ hơn, sau chín và mười khóa vẫn quan sát Node; sau mười một mới quan sát TreeNode. Kết quả phù hợp với việc đường xử lý bảng nhỏ ưu tiên tăng bảng trước khi tạo cây.

Chỉ từ một tên lớp được in không thể kết luận mọi thông số capacity bên trong. Phần giải thích đường resize cần đối chiếu mã nguồn của cài đặt; chương trình không cung cấp API kiểm tra capacity.

Không dùng những dòng observed làm kiểm thử khả năng tương thích cho sản phẩm. Các kết quả nghiệp vụ như size, get và remove mới thuộc phần hợp đồng cần giữ khi cấu trúc bên trong thay đổi.

## 6. Cây hóa không làm HashMap thành TreeMap

Một tree bin là một nhóm trong một bảng băm. Nó không tạo thứ tự tăng dần cho toàn bộ map.

| Câu hỏi | HashMap có tree bin |
| --- | --- |
| Iterator có phải đi theo thứ tự khóa tăng dần không? | Không có bảo đảm đó |
| Có public API floorKey/ceilingKey vì đã có cây không? | Không |
| Có thể truyền Comparator qua constructor HashMap để điều khiển cây không? | Không |
| CompareTo bằng 0 có tự loại khóa khác equals không? | Không đổi hợp đồng nhận diện của HashMap |
| Put/get/remove theo khóa tương đương có còn đúng không? | Vẫn phải đúng khi khóa tuân hợp đồng |

Trong tìm kiếm tree bin, hash vẫn là thông tin quan trọng để chọn hướng. Khi hash bằng nhau, cài đặt có thể dùng một dạng Comparable phù hợp của khóa để hỗ trợ phân biệt nhánh.

Không cần người gọi tự chọn một comparator nội bộ cho HashMap. Cũng không biến compareTo thành phép thay thế equals cho việc cùng khóa logic.

Nếu cần truy vấn thứ tự khóa hoặc khoảng khóa như một tính năng ứng dụng, hãy chọn một Map có hợp đồng công khai đáp ứng yêu cầu đó. Việc một bucket vô tình chuyển thành cây không bổ sung những tính năng ấy.

## 7. Giới hạn của phát biểu “cây thì tìm luôn O(log n)”

### 7.1. Chiều cao cây và tổng công việc tìm không phải cùng một thứ

Cây cân bằng giới hạn chiều cao. Nếu mỗi bước tìm xác định được nhánh phù hợp, tìm trong m node có thể theo một đường dài O(log m).

Nhưng trong tree bin, nhiều khóa có thể cùng hash và không có quan hệ Comparable hữu ích để phân biệt. Mã nguồn tìm cây của OpenJDK có đường phải tìm thêm ở nhánh khác khi không chọn được hướng đủ thông tin.

Vì thế không phát biểu vô điều kiện: “từ Java 8 mọi get của HashMap trong trường hợp xấu đều O(log n), bất kể kiểu khóa và hàm hash”.

Đặc biệt, cùng hash và không so sánh được có thể khiến việc tìm một khóa không có phải khảo sát nhiều node. Cây đỏ–đen vẫn cân bằng nhưng tổng số node được xem không nhất thiết chỉ là một đường từ gốc xuống lá.

### 7.2. Comparable hỗ trợ cấu trúc, không sửa khóa sai

Nếu khóa có một thứ tự tự nhiên phù hợp, cài đặt có thể dùng nó để xử lý các trường hợp hash trùng. Chương trình thí nghiệm dùng Comparable<Key> theo id để có một mô hình nhất quán và dễ đọc.

Một equals/hashCode vi phạm hợp đồng, hoặc một khóa bị sửa thông tin định danh sau khi put, vẫn có thể làm tra cứu không còn như mong đợi. Treeification không tự sửa vấn đề đó.

### 7.3. Hàm băm tốt vẫn quan trọng

Mã băm phân bố phù hợp giúp tránh việc rất nhiều khóa dồn vào một bucket. Cây hóa là một cơ chế giảm tác động trong những điều kiện nhất định, không phải lý do chủ đích trả cùng hashCode cho mọi khóa của ứng dụng.

Một lần hashCode/equals/compareTo tốn công cũng làm chi phí thực tăng lên. Khi phân tích O(1) trung bình hoặc O(log m), phải hiểu giả định về chi phí các phép so sánh.

Tài liệu HashMap chỉ mô tả hiệu năng hằng số cho các thao tác cơ bản dưới điều kiện hàm băm phân bố thích hợp. Không bỏ điều kiện ấy khi diễn giải.

## 8. Khi nào một bucket trở về dạng thường?

### 8.1. Resize có thể chia một nhóm thành hai nhóm nhỏ

Với bảng tăng từ 64 lên 128, các hash khác nhau đang cùng chỉ số cũ có thể rẽ sang hai chỉ số mới. Trong mô hình hash 1 và 65:

```text
Trước: bucket 1 của bảng 64
       ├─ bốn khóa có hash 1
       └─ năm khóa có hash 65

Sau:   bucket 1  của bảng 128: bốn khóa hash 1
       bucket 65 của bảng 128: năm khóa hash 65
```

Trong đường split tree bin của OpenJDK 17u, các nhóm nhỏ có số node không vượt UNTREEIFY_THRESHOLD có thể được đổi về node thường.

Đây là một lý do hợp lý để dùng ngưỡng vào và ra khác nhau: không phải mọi biến động nhỏ đều cần trả chi phí đổi cấu trúc qua lại.

### 8.2. Không áp “xóa còn 6 thì luôn thành danh sách” cho mọi đường xóa

Hằng UNTREEIFY_THRESHOLD bằng 6 được dùng trong đường tách khi resize. Đường removeTreeNode còn có kiểm tra dựa trên hình dạng cây và cách thao tác.

Không biến ba con số 8/6/64 thành một máy trạng thái đơn giản chỉ nhìn size. Phải xem chúng được dùng ở hàm nào.

Một cài đặt có thể chuyển dạng vì resize, vì nhóm trở nên nhỏ sau xóa, hoặc giữ dạng trong những trường hợp khác. Đây không phải chính sách ứng dụng nên điều khiển bằng số entry cố định.

### 8.3. Chuyển dạng không được làm mất ánh xạ hợp lệ

Dù node được đổi biểu diễn, những khóa và giá trị còn tồn tại phải giữ được ý nghĩa tra cứu. Kiểm thử tính đúng nên kiểm tra ánh xạ được bảo toàn, thay vì bắt một dạng node cụ thể.

## 9. Thí nghiệm tách tree bin khi bảng tăng

Lưu thành **BucketSplitObservation.java**:

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BucketSplitObservation {
    record Key(int id, int rawHash) implements Comparable<Key> {
        @Override
        public int hashCode() {
            return rawHash;
        }

        @Override
        public int compareTo(Key other) {
            int byHash = Integer.compare(rawHash, other.rawHash);
            return byHash != 0 ? byHash : Integer.compare(id, other.id);
        }
    }

    private static String observedEntryClass(Map<Key, Integer> map, Key key) {
        for (Map.Entry<Key, Integer> entry : map.entrySet()) {
            if (entry.getKey().equals(key)) {
                return entry.getClass().getSimpleName();
            }
        }
        return "missing";
    }

    public static void main(String[] args) {
        Map<Key, Integer> map = new HashMap<>(64);
        List<Key> original = new ArrayList<>();
        for (int id = 0; id < 9; id++) {
            Key key = new Key(id, id < 4 ? 1 : 65);
            original.add(key);
            map.put(key, id);
        }

        Key low = new Key(2, 1);
        Key high = new Key(7, 65);
        System.out.println("before growth size: " + map.size());
        System.out.println("observed low entry before: " + observedEntryClass(map, low));
        System.out.println("observed high entry before: " + observedEntryClass(map, high));

        for (int rawHash = 2; rawHash <= 41; rawHash++) {
            map.put(new Key(100 + rawHash, rawHash), 100 + rawHash);
        }

        System.out.println("after growth size: " + map.size());
        System.out.println("observed low entry after: " + observedEntryClass(map, low));
        System.out.println("observed high entry after: " + observedEntryClass(map, high));

        boolean preserved = true;
        for (Key key : original) {
            preserved &= Integer.valueOf(key.id()).equals(map.get(new Key(key.id(), key.rawHash())));
        }
        System.out.println("original mappings preserved: " + preserved);
        System.out.println("get low key: " + map.get(low));
        System.out.println("get high key: " + map.get(high));
    }
}
```

Lệnh chạy:

```shell
javac --release 17 -encoding UTF-8 BucketSplitObservation.java
java BucketSplitObservation
```

Stdout quan sát trên cùng môi trường:

```text
before growth size: 9
observed low entry before: TreeNode
observed high entry before: TreeNode
after growth size: 49
observed low entry after: Node
observed high entry after: Node
original mappings preserved: true
get low key: 2
get high key: 7
```

Chín khóa đầu gồm bốn khóa trả hash 1 và năm khóa trả hash 65. Với các giá trị nhỏ này, bước trộn của cài đặt được đối chiếu không đổi hai hash đó; khi bảng có 64 bucket, cả hai nhóm cùng vào chỉ số 1.

Các khóa thêm sau dùng hash từ 2 đến 41, không dồn vào bucket ban đầu ấy trong mô hình đang xét. Chúng làm tổng số entry tăng từ 9 lên 49. Với cài đặt và cấu hình thí nghiệm này, đường tăng bảng dẫn tới việc tách nhóm; các entry gốc được quan sát chuyển từ TreeNode thành Node.

Lần nữa, tên lớp và thời điểm chuyển là kết quả quan sát, không phải API kiểm tra cấu trúc. Phần original mappings preserved cùng các lệnh get chứng minh điều quan trọng đối với mã gọi: dữ liệu gốc vẫn được tìm thấy bằng khóa tương đương.

Chương trình duyệt entrySet để tìm entry cần quan sát. Nó không dựa vào thứ tự duyệt HashMap, vì thứ tự ấy không được cam kết.

## 10. Những thứ không nên đưa vào hợp đồng của ứng dụng

### 10.1. Không rẽ nhánh nghiệp vụ theo tên lớp node

Các tên `HashMap$Node` hoặc `HashMap$TreeNode`, việc một entry có thêm trường màu hay liên kết nào, và cấu trúc gốc của cây là nội bộ.

Mã như “nếu entry là TreeNode thì chọn cách xác thực khác” biến một tối ưu bộ nhớ thành điều kiện nghiệp vụ. Nó có thể hỏng khi nâng JDK dù các thao tác Map công khai vẫn hoạt động đúng.

Không cần đọc trường private của HashMap hoặc mở các gói nội bộ để sử dụng Map đúng.

### 10.2. Không dựa vào thứ tự iterator

HashMap không bảo đảm thứ tự duyệt. Resize hoặc đổi cấu trúc có thể làm thứ tự quan sát khác đi; một thứ tự có vẻ ổn trên dữ liệu nhỏ không trở thành cam kết.

Nếu đầu ra cần một thứ tự, phải biểu đạt yêu cầu đó bằng cấu trúc hoặc bước xử lý có hợp đồng thứ tự rõ ràng.

### 10.3. Không dùng threshold để đoán hiệu năng mà không xét dữ liệu

Biết ngưỡng 8 không cho biết một chương trình sẽ nhanh hay chậm ở entry thứ chín. Còn có phân bố hash, số bucket, equals/compareTo, kiểu đường thao tác, cấp phát và nhiều yếu tố khác.

Không suy ra thời gian chạy từ một lần đo rất ngắn hoặc từ việc lớp entry đã đổi tên. Khi hiệu năng là yêu cầu thật, dùng phép đo phù hợp với dữ liệu và môi trường đại diện.

### 10.4. Không xem cây hóa là cơ chế đồng bộ

HashMap không tự an toàn cho việc nhiều luồng cùng sửa chỉ vì một bucket đã thành cây. Cấu trúc đồng thời và cơ chế đồng bộ là một yêu cầu độc lập.

Cũng không dùng ConcurrentModificationException như một bằng chứng rằng mọi truy cập sai đều được phát hiện. Đây là cơ chế hỗ trợ phát hiện lỗi theo khả năng, không phải bảo đảm đồng bộ.

## 11. Cách đọc nguồn cài đặt mà vẫn giữ mô hình đúng

Một trình tự đọc hữu ích:

1. Đọc hợp đồng công khai của HashMap và Map.
2. Xem hash/index để biết nhiều khóa có thể cùng vùng như thế nào.
3. Xem putVal và treeifyBin để biết lúc nào có đề nghị chuyển dạng.
4. Xem TreeNode.find để hiểu điều kiện chọn nhánh.
5. Xem split và removeTreeNode để hiểu đường quay về dạng thường.
6. Kiểm tra dữ liệu bằng public API; dùng chẩn đoán nội bộ chỉ để học cơ chế.

Hãy ghi rõ phiên bản nguồn khi giải thích. “Nguồn OpenJDK 17u có hằng này” chính xác hơn “Java luôn làm như vậy”.

Bản nguồn trên nhánh cập nhật cũng có thể thay đổi theo thời gian. Khi cần tái hiện một điều tra hiệu năng, ghi lại bản JDK, bản nguồn hoặc commit liên quan thay vì chỉ tên một ngôn ngữ.

Đừng chép nguyên một đoạn điều kiện ra rồi bỏ ngữ cảnh: một threshold có thể là đếm node trước thêm; một đường khác có thể dùng biến đếm khác; một điều kiện capacity có thể chuyển thao tác sang resize.

## 12. Kiểm thử nên bảo vệ kết quả nào?

Các thuộc tính bền vững để kiểm tra:

- Nhiều khóa khác nhau cùng hash vẫn giữ đủ entry.
- Put bằng khóa tương đương thay đúng giá trị, không tăng số khóa logic.
- Get và remove bằng bản khóa tương đương vẫn đúng trước và sau nhiều lần thêm.
- Khóa ở các bucket khác không bị ảnh hưởng bởi xóa trong một nhóm va chạm.
- Null và giá trị trả về được xử lý theo đúng lớp map.
- Mã gọi không giả định thứ tự iterator của HashMap.

Các kiểm thử khóa đúng này vẫn có giá trị nếu cài đặt thay ngưỡng hoặc thay cách tổ chức bucket.

Ngược lại, kiểm thử đòi “entry thứ chín phải mang class tên TreeNode” thuộc về một thí nghiệm cài đặt có môi trường được cố định. Không dùng nó làm yêu cầu của một thư viện chỉ cam kết sử dụng Map API.

Có thể giữ cả hai loại kiểm tra trong một hoạt động học, nhưng phải đặt mục tiêu rõ: một loại xác nhận hợp đồng dữ liệu; một loại ghi nhận cơ chế của môi trường cụ thể.

## 13. Lỗi phổ biến và cách sửa

| Lỗi suy luận | Điều bị bỏ qua | Cách hiểu phù hợp |
| --- | --- | --- |
| Cùng hash nên khóa sau ghi đè khóa trước | Equals vẫn nhận diện khóa | Va chạm hợp lệ không nhập khóa khác nhau |
| Cứ tám entry là thành cây | Đường đếm, capacity và thao tác | Đọc điều kiện tại nơi dùng threshold |
| Phải có 64 entry mới cây hóa | 64 là độ dài bảng | Phân biệt size với capacity |
| Bảng nhỏ gặp nhiều va chạm luôn lập cây | Có đường resize trước | Xem MIN_TREEIFY_CAPACITY |
| Có tree bin nên HashMap đã sắp xếp | Cây chỉ là cấu trúc một bucket | Không có hợp đồng thứ tự toàn map |
| Cây cân bằng nên mọi lookup chắc O(log n) | Có nhánh tìm không phân biệt được bằng thứ tự | Xét hash và Comparable phù hợp |
| Xóa còn sáu luôn đổi về danh sách | Đường remove khác split | Không dùng một ngưỡng thay toàn cơ chế |
| Treeification sửa được khóa bị mutate | Hợp đồng khóa vẫn bị phá | Giữ định danh khóa ổn định |
| Tên Node là API công khai | Đó là tên lớp chạy thực tế | Chỉ dùng cho quan sát, không cho nghiệp vụ |
| Cây hóa làm HashMap an toàn nhiều luồng | Không có bảo đảm đồng bộ mới | Chọn cấu trúc/đồng bộ phù hợp riêng |

## 14. Bài tập tự kiểm tra có lời giải

### Bài 1: Hai mức va chạm

Hash đã trộn 1 và 65 có cùng bucket khi bảng dài 64 không? Khi bảng dài 128 thì sao?

**Lời giải:** ở 64, cả hai vào bucket 1. Ở 128, chúng vào 1 và 65. Va chạm do mask của bảng nhỏ có thể được tách khi tăng bảng.

### Bài 2: Hash hoàn toàn giống nhau

Hai mươi khóa khác nhau đều có cùng hashCode hợp lệ. Tăng bảng có bảo đảm chia chúng vào nhiều bucket không?

**Lời giải:** không. Hash sau trộn giống nhau vẫn cho cùng chỉ số với cùng mask. Resize không tự tạo sự khác nhau giữa các hash bằng nhau.

### Bài 3: Ý nghĩa 64

Map có chín entry nhưng đã có bảng đủ lớn. Có mâu thuẫn nếu bucket của nó được cây hóa không?

**Lời giải:** không. Điều kiện 64 nói về độ dài bảng của đường cài đặt được đối chiếu, không phải map phải có 64 entry.

### Bài 4: Ngưỡng 8

Một quan sát put thấy sau tám khóa còn Node, sau chín khóa là TreeNode. Điều đó có tự mâu thuẫn với TREEIFY_THRESHOLD bằng 8 không?

**Lời giải:** không. Phải xem ngưỡng được kiểm tra khi thêm vào chuỗi đã có bao nhiêu node và biến đếm được dùng thế nào. Một con số riêng chưa mô tả đủ thời điểm chuyển.

### Bài 5: Thứ tự

Một bucket là cây đỏ–đen. Có được dùng vòng for trên HashMap để lấy các khóa tăng dần không?

**Lời giải:** không. Tree bin không tạo hợp đồng duyệt khóa có thứ tự cho toàn map.

### Bài 6: Tìm khóa thiếu

Một nhóm có nhiều khóa cùng hash và không có thứ tự Comparable hữu ích. Cây cân bằng có bảo đảm việc tìm khóa thiếu chỉ xem một đường O(log m) không?

**Lời giải:** không thể khẳng định như vậy. Đường tìm của cài đặt có thể cần tìm thêm nhánh khi không phân biệt được hướng. Chiều cao cân bằng không đồng nghĩa mọi lượt tìm đều chỉ đi một nhánh.

### Bài 7: Tách nhóm

Một tree bin tách thành nhóm bốn node và nhóm năm node trong resize của OpenJDK 17u. Chúng có thể trở về node thường không?

**Lời giải:** có, theo đường split với UNTREEIFY_THRESHOLD. Điều này không tạo quy tắc rằng mọi phép xóa đến sáu node trong mọi hoàn cảnh đều phải đổi dạng.

### Bài 8: Kiểm thử nâng JDK

Một bản JDK khác không in tên entry như thí nghiệm nhưng put/get/remove với khóa đúng vẫn giữ dữ liệu. Có đủ để kết luận JDK vi phạm Map không?

**Lời giải:** không. Tên lớp entry và thời điểm cây hóa không thuộc cam kết của Map. Phải đánh giá các thuộc tính công khai mà ứng dụng được phép dựa vào.

## 15. Tài liệu đối chiếu

- [HashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html): hợp đồng lớp, hiệu năng có điều kiện và thứ tự không được bảo đảm.
- [Map Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): ý nghĩa ánh xạ và giới hạn với khóa thay đổi được.
- [HashMap.java OpenJDK 17u](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/HashMap.java): treeifyBin, TreeNode.find, split, removeTreeNode và các hằng số.
- [Collections Framework Enhancements in Java SE 8 của OpenJDK](https://cr.openjdk.org/~naoto/idna2008/docs_www/technotes/guides/collections/changes8.html): bối cảnh cải tiến bucket va chạm.

Cây hóa là một cách tổ chức lại bucket để cải thiện xử lý va chạm trong cài đặt cụ thể. Hiểu nó giúp giải thích và chẩn đoán, còn mã sử dụng HashMap nên tiếp tục dựa vào hợp đồng ánh xạ, quy tắc khóa đúng và yêu cầu thứ tự/đồng thời được biểu đạt rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-map-006">Chính sách một khóa null và nhiều giá trị null</a>

<details>
<summary>Xem chi tiết</summary>

# Chính sách một khóa null và nhiều giá trị null

## 1. Hai vị trí null có hai vai trò khác nhau

Một `HashMap<K, V>` lưu các ánh xạ từ khóa đến giá trị. Nó cho phép:

- Tối đa một ánh xạ có khóa là `null` trong cùng một map.
- Nhiều ánh xạ khác nhau cùng có giá trị là `null`.
- Một ánh xạ đồng thời có khóa `null` và giá trị `null`.

Cần đọc “nhiều giá trị null” là **nhiều ánh xạ có phần giá trị null**, không phải một khóa tự chứa nhiều giá trị hiện tại. Mỗi khóa vẫn chỉ xác định nhiều nhất một giá trị.

Ví dụ, một map có thể biểu diễn:

```text
null     → "nội dung mặc định"
"draft"  → null
"review" → null
```

Khóa nằm bên trái để nhận diện ánh xạ. Giá trị bên phải là dữ liệu được gắn với khóa đó. Việc giá trị được phép lặp không làm khóa được phép trùng.

### Vì sao cần hiểu chính sách này?

Nếu ứng dụng cho phép dữ liệu chưa biết, `null` có thể xuất hiện trong lúc lưu và đọc. Khi đó `get` trả `null` chưa đủ để kết luận không có khóa; việc ghi `null` cũng không đồng nghĩa xóa ánh xạ.

Ngược lại, nếu nghiệp vụ cấm `null`, việc `HashMap` chấp nhận nó không có nghĩa mọi đầu vào null đều hợp lệ. Khả năng của thư viện và quy tắc dữ liệu của ứng dụng là hai tầng cần tách.

Bài đi từ cấu trúc khóa–giá trị đến cách đọc, cập nhật, duyệt và chuyển dữ liệu. Các ví dụ đều dùng `HashMap` thật, không dựa vào thứ tự duyệt mà lớp này không bảo đảm.

## 2. Vì sao chỉ có một khóa null?

Map không lưu hai ánh xạ riêng cho cùng một khóa logic. Gọi `put(null, value)` lần đầu có thể tạo ánh xạ; gọi lại với khóa `null` thay giá trị của ánh xạ đó.

```text
put(null, "first")
    → null → "first"

put(null, "second")
    → null → "second"
    → không tạo thêm một khóa null thứ hai
```

Quy tắc này áp dụng trong từng map, không phải một giới hạn “toàn chương trình chỉ được có một null”. Hai đối tượng `HashMap` khác nhau có thể mỗi đối tượng chứa ánh xạ khóa null của riêng nó.

`null` là một tham chiếu không trỏ tới đối tượng; không có một lớp riêng của null để tạo các khóa null theo từng loại. Nếu nhiều biến đều có giá trị null và được dùng làm khóa trong cùng một map chấp nhận chúng, chúng không trở thành những định danh khác nhau chỉ vì tên biến khác nhau.

### Không dùng null làm mã tạm cho nhiều bản ghi khác nhau

Nếu ba bản ghi chưa có mã đều được ghi bằng khóa `null`, các lần ghi sau sẽ thay ánh xạ trước. Bạn không có ba bản ghi độc lập chỉ vì ba đối tượng giá trị khác nhau.

Nếu nghiệp vụ phải giữ nhiều bản ghi chưa xác định mã, cần một cách nhận diện thích hợp, chẳng hạn mã tạm duy nhất, kiểu khóa riêng hoặc cấu trúc danh sách nếu bản chất dữ liệu là một chuỗi bản ghi. Không mong một map bỏ qua tính duy nhất của khóa chỉ cho trường hợp null.

### null khác chuỗi "null"

Đoạn sau là minh họa đặt trong một phương thức, với một map rỗng:

```java
Map<String, String> map = new HashMap<>();
map.put(null, "default");
map.put("null", "literal");

// map.size() là 2.
// map.get(null) là "default".
// map.get("null") là "literal".
```

`"null"` là một đối tượng chuỗi có bốn ký tự, còn `null` là tham chiếu không có đối tượng. Tự chuyển null thành chuỗi `"null"` có thể làm mất khác biệt này nếu dữ liệu thật cũng được phép dùng chuỗi ấy làm khóa.

### HashMap nhận null không có nghĩa có thể gọi phương thức trên null

`map.get(null)`, `map.put(null, value)` và `map.containsKey(null)` được `HashMap` hỗ trợ. Hiện thực xử lý trường hợp khóa null theo hợp đồng của nó; ứng dụng không cần và không thể gọi `null.hashCode()` hay `null.equals(...)`.

Một `NullPointerException` do mã của ứng dụng gọi phương thức trên khóa null không phải bằng chứng rằng `HashMap` không chấp nhận khóa đó.

## 3. Vì sao nhiều giá trị null vẫn hợp lệ?

Giá trị của map không bắt buộc duy nhất. Cũng như hai khóa có thể cùng mang số `3`, hai hoặc nhiều khóa có thể cùng mang giá trị `null`.

```text
"draft"  → null
"review" → null
"final"  → null
```

Map trên có ba ánh xạ. Kích thước là `3`, dù không có giá trị nào khác null.

Nếu thêm ánh xạ `null → null`, kích thước có thể thành `4`: khóa null là một khóa khác với ba chuỗi đang có. Ghi lại `put("draft", null)` sau đó không làm kích thước tăng nữa, vì khóa `"draft"` đã tồn tại.

| Câu hỏi | Phương thức phù hợp |
|---|---|
| Có ánh xạ cho khóa null không? | `containsKey(null)` |
| Giá trị của khóa null hiện là gì? | `get(null)` |
| Có ít nhất một ánh xạ mang giá trị null không? | `containsValue(null)` |
| Có bao nhiêu ánh xạ? | `size()` |
| Có bao nhiêu ánh xạ mang giá trị null? | Duyệt giá trị hoặc entry và đếm theo điều kiện |

`containsValue(null)` chỉ trả `boolean`: nó không cho biết có bao nhiêu null, không chỉ ra khóa nào, và không chứng minh khóa null có tồn tại.

Các mối quan hệ độc lập có thể là:

- Không có khóa null nhưng vẫn có nhiều giá trị null.
- Có khóa null nhưng không có giá trị null nào.
- Có cả khóa null và giá trị null.
- Không có cả hai.

## 4. Chương trình đầy đủ: một khóa null, nhiều giá trị null

```java
import java.util.HashMap;
import java.util.Map;

public class HashMapNullPolicyDemo {
    public static void main(String[] args) {
        Map<String, String> messages = new HashMap<>();

        System.out.println("firstNullKeyOld="
                + messages.put(null, "default"));
        System.out.println("secondNullKeyOld="
                + messages.put(null, "fallback"));
        System.out.println("sizeAfterNullKeyWrites=" + messages.size());
        System.out.println("nullKeyValue=" + messages.get(null));

        messages.put("draft", null);
        messages.put("review", null);
        System.out.println("sizeAfterNamedNullValues=" + messages.size());
        System.out.println("hasNullValue=" + messages.containsValue(null));

        System.out.println("oldBeforeNullNull="
                + messages.put(null, null));
        System.out.println("hasNullKey=" + messages.containsKey(null));
        System.out.println("nullKeyNow=" + messages.get(null));
        System.out.println("finalSize=" + messages.size());

        int nullKeyCount = 0;
        int nullValueCount = 0;
        for (Map.Entry<String, String> entry : messages.entrySet()) {
            if (entry.getKey() == null) {
                nullKeyCount++;
            }
            if (entry.getValue() == null) {
                nullValueCount++;
            }
        }
        System.out.println("nullKeyCount=" + nullKeyCount);
        System.out.println("nullValueCount=" + nullValueCount);
    }
}
```

Lưu thành `HashMapNullPolicyDemo.java`, rồi biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 HashMapNullPolicyDemo.java
java HashMapNullPolicyDemo
```

Đầu ra chính xác:

```text
firstNullKeyOld=null
secondNullKeyOld=default
sizeAfterNullKeyWrites=1
nullKeyValue=fallback
sizeAfterNamedNullValues=3
hasNullValue=true
oldBeforeNullNull=fallback
hasNullKey=true
nullKeyNow=null
finalSize=3
nullKeyCount=1
nullValueCount=3
```

### Giải thích các mốc

Hai lần ghi khóa null tạo một ánh xạ duy nhất:

- Lần đầu trả `null` vì chưa có giá trị cũ.
- Lần thứ hai trả `"default"`, rồi giá trị hiện tại thành `"fallback"`.
- Kích thước sau hai lần ghi vẫn là `1`.

Hai khóa chuỗi `"draft"` và `"review"` được thêm với giá trị null, đưa kích thước lên `3`.

Cuối cùng `put(null, null)` thay giá trị của khóa null từ `"fallback"` thành null. Kích thước vẫn `3`; lúc này cả ba ánh xạ đều mang giá trị null.

Hai bộ đếm cuối có ý nghĩa khác nhau:

```text
nullKeyCount=1
nullValueCount=3
```

Không cần dựa vào việc khóa null được in ở đâu trong thứ tự duyệt. Chương trình chỉ đếm hoặc đọc theo khóa cụ thể, nên đầu ra không phụ thuộc một thứ tự tình cờ của `HashMap`.

## 5. Ba trạng thái khi đọc một khóa

Với một khóa `k`, cần phân biệt:

| Trạng thái | `get(k)` | `containsKey(k)` |
|---|---|---|
| Không có ánh xạ của `k` | `null` | `false` |
| Có ánh xạ `k → null` | `null` | `true` |
| Có ánh xạ `k → "ready"` | `"ready"` | `true` |

Bảng áp dụng cả khi `k` là khóa null trong một `HashMap`. Khóa null không làm biến mất sự khác nhau giữa “chưa có ánh xạ” và “đã có ánh xạ với giá trị null”.

Nếu nghiệp vụ phân biệt “chưa ghi nhận” với “đã ghi nhận nhưng chưa có giá trị”, hãy giữ sự khác nhau đó khi đọc. Chỉ kiểm tra `get(k) != null` sẽ gộp hai trạng thái đầu.

### getOrDefault không thay tất cả null bằng giá trị dự phòng

`getOrDefault(k, fallback)` chọn `fallback` khi **không có ánh xạ** của khóa. Nếu khóa có ánh xạ với giá trị null, kết quả vẫn là null.

Vì vậy tên “default” không đồng nghĩa “mọi kết quả null sẽ được đổi thành một giá trị khác”. Phải chọn đúng quy tắc:

- Dự phòng khi không có khóa.
- Dự phòng khi giá trị null, bất kể có khóa hay chưa.
- Giữ hai tình huống riêng để xử lý khác nhau.

Hai quy tắc đầu khác nhau về ý nghĩa, dù trong một ứng dụng cấm giá trị null chúng có thể cho cùng kết quả ở các dữ liệu hợp lệ.

Đọc bằng `get` hay `getOrDefault` cũng không tự chèn một khóa mới. Một giá trị dự phòng được trả ra chưa có nghĩa nó đã được lưu vào map.

### Optional không tự bảo toàn thông tin sự tồn tại

Nếu bọc `Optional.ofNullable(map.get(k))`, cả không có ánh xạ lẫn có ánh xạ giá trị null đều tạo `Optional.empty()`. Cách bọc có thể phù hợp nếu ứng dụng chủ động gộp hai trạng thái, nhưng không giúp giữ lại sự khác nhau đã bị bỏ qua.

Khi cần mô tả nhiều trạng thái nghiệp vụ, có thể dùng một kiểu kết quả thể hiện rõ trạng thái hoặc kết hợp thông tin sự tồn tại theo một cơ chế truy cập được kiểm soát.

## 6. Ghi null khác xóa ánh xạ

Với một `HashMap` sửa được:

| Thao tác thành công | Trạng thái sau thao tác |
|---|---|
| `put(k, null)` | Khóa `k` có ánh xạ; giá trị là null |
| `remove(k)` | Khóa `k` không còn ánh xạ |
| `remove(k, null)` | Chỉ xóa nếu đang có ánh xạ của `k` với giá trị null |

`put(k, null)` không phải bí danh của `remove(k)`. Nếu dữ liệu dùng null để biểu diễn trạng thái “đã có bản ghi, chưa có giá trị”, xóa ánh xạ sẽ làm mất chính thông tin “đã có bản ghi”.

`put` và `remove` một tham số trả giá trị cũ. Kết quả null ở đây vẫn có hai khả năng: trước đó không có ánh xạ, hoặc có ánh xạ giá trị null.

Nếu cần biết trước đó có khóa không, trong tình huống một luồng sở hữu dữ liệu có thể ghi nhận `containsKey(k)` trước thao tác. Kiểm tra sau `remove` không khôi phục được sự khác nhau, vì cả “vốn không có” và “vừa bị xóa” đều có thể không có khóa sau đó.

Cặp lời gọi riêng không tự là thao tác nguyên tử. `HashMap` không cung cấp cơ chế đồng bộ cho nhiều luồng cùng thay đổi dữ liệu; không suy khả năng phân biệt trạng thái an toàn đồng thời chỉ từ hai dòng `containsKey` rồi `remove`.

### Xóa qua values chỉ xóa một hay tất cả?

`map.values().remove(null)` xóa một lần xuất hiện giá trị null nếu có, tức bỏ một ánh xạ tương ứng trong map. Nó không tự xóa hết các ánh xạ đang có giá trị null.

Nếu cần xóa tất cả, có thể dùng `removeIf(Objects::isNull)` trên view giá trị của map sửa được. Phải quyết định rõ điều này có đúng nghiệp vụ hay không: lọc hết null là bỏ các ánh xạ, không chỉ dọn một cách biểu diễn.

## 7. Chương trình đầy đủ: tồn tại, giá trị dự phòng và xóa

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class HashMapNullPresenceDemo {
    public static void main(String[] args) {
        Map<String, String> states = new HashMap<>();
        states.put("draft", null);

        System.out.println("draftGet=" + states.get("draft"));
        System.out.println("missingGet=" + states.get("missing"));
        System.out.println("hasDraft=" + states.containsKey("draft"));
        System.out.println("hasMissing=" + states.containsKey("missing"));
        System.out.println("draftOrDefault="
                + states.getOrDefault("draft", "fallback"));
        System.out.println("missingOrDefault="
                + states.getOrDefault("missing", "fallback"));
        System.out.println("sizeAfterReads=" + states.size());

        boolean existed = states.containsKey("draft");
        String removedOld = states.remove("draft");
        System.out.println("existedBeforeRemove=" + existed);
        System.out.println("removedOld=" + removedOld);
        System.out.println("hasDraftAfterRemove="
                + states.containsKey("draft"));

        states.put("draft", null);
        System.out.println("conditionalNullRemove="
                + states.remove("draft", null));
        System.out.println("sizeAfterConditionalRemove=" + states.size());

        states.put("a", null);
        states.put("b", null);
        states.put("c", null);
        System.out.println("oneNullRemoved=" + states.values().remove(null));
        System.out.println("sizeAfterOneNullRemove=" + states.size());
        System.out.println("stillHasNullValue=" + states.containsValue(null));
        System.out.println("allNullsRemoved="
                + states.values().removeIf(Objects::isNull));
        System.out.println("finalSize=" + states.size());
    }
}
```

Lưu thành `HashMapNullPresenceDemo.java`, rồi biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 HashMapNullPresenceDemo.java
java HashMapNullPresenceDemo
```

Đầu ra chính xác:

```text
draftGet=null
missingGet=null
hasDraft=true
hasMissing=false
draftOrDefault=null
missingOrDefault=fallback
sizeAfterReads=1
existedBeforeRemove=true
removedOld=null
hasDraftAfterRemove=false
conditionalNullRemove=true
sizeAfterConditionalRemove=0
oneNullRemoved=true
sizeAfterOneNullRemove=2
stillHasNullValue=true
allNullsRemoved=true
finalSize=0
```

### Đọc đúng các kết quả giống nhau

`draftGet` và `missingGet` đều null, nhưng `hasDraft=true`, `hasMissing=false`. Hai phép đọc không làm kích thước tăng.

`draftOrDefault=null`, còn `missingOrDefault=fallback`, đúng theo quy tắc dự phòng chỉ khi không có ánh xạ.

Lần xóa `"draft"` trả `removedOld=null` nhưng thực sự có tác dụng vì `existedBeforeRemove=true` và sau đó khóa không còn. Overload `remove("draft", null)` lại trả một cờ `boolean`: `true` nói việc xóa theo điều kiện đã thực hiện được, không phải một giá trị cũ kiểu chuỗi.

Sau khi thêm `"a"`, `"b"`, `"c"` đều có giá trị null, xóa một lần qua `values().remove(null)` để lại kích thước `2` và vẫn còn null. Chương trình cố ý không khẳng định khóa nào trong ba khóa bị chọn. Lần `removeIf` sau đó mới lọc hết những ánh xạ còn lại.

## 8. Các phương thức cập nhật không diễn giải null giống nhau

Việc `HashMap` cho lưu giá trị null không có nghĩa mọi phương thức coi null là một giá trị bình thường trong mọi vị trí.

Bảng dưới giả sử map sửa được, hàm truyền vào hợp lệ, không tự sửa map trong lúc tính và không có truy cập đồng thời gây thay đổi:

| Phương thức | Nếu đang có ánh xạ với giá trị null |
|---|---|
| `get(k)` | Trả null |
| `getOrDefault(k, fallback)` | Vẫn trả null vì khóa có ánh xạ |
| `putIfAbsent(k, v)` | Có thể ghi `v`; coi giá trị null là chưa có giá trị để giữ |
| `computeIfAbsent(k, fn)` | Gọi hàm để thử tính giá trị khác null |
| `computeIfPresent(k, fn)` | Không gọi hàm, vì cần giá trị hiện tại khác null |
| `compute(k, fn)` | Gọi hàm với giá trị cũ null; kết quả null có ý nghĩa xóa ánh xạ |
| `merge(k, incoming, fn)` | Dùng `incoming` khác null mà không cần ghép với giá trị cũ null |

### 8.1. putIfAbsent không đồng nghĩa containsKey là false

Tên phương thức dễ khiến người đọc nghĩ nó chỉ ghi khi không có khóa. Hợp đồng còn gồm trường hợp khóa đang ánh xạ tới null.

Nếu ứng dụng muốn giữ null như một trạng thái đã ghi nhận có ý nghĩa, không nên dùng `putIfAbsent` rồi mong nó luôn bảo vệ trạng thái đó khỏi bị thay. Cần chọn quy tắc dựa trên yêu cầu thật về sự tồn tại của ánh xạ.

### 8.2. computeIfAbsent trả null không tạo một giá trị null mới

Nếu hàm trả một giá trị khác null, phương thức có thể ghi giá trị đó khi trước đó thiếu giá trị hoặc đang có null.

Nếu hàm trả null:

- Khóa vốn chưa có vẫn không được tạo ánh xạ mới.
- Khóa vốn đã có ánh xạ giá trị null vẫn giữ ánh xạ null đó.

Điều này khác với việc hiểu “trả null thì xóa hết dấu vết của khóa”. Chương trình tiếp theo kiểm chứng riêng hai trường hợp.

Một hệ quả khi dùng làm bộ nhớ đệm: nếu hàm tải trả null cho kết quả không tìm thấy, `computeIfAbsent` không tự lưu một kết quả khác null để đánh dấu đã kiểm tra. Lời gọi sau vẫn có thể phải gọi hàm tải lại. Nếu cần ghi nhớ “đã kiểm tra nhưng không có kết quả”, nên dùng một cách biểu diễn trạng thái rõ ràng theo thiết kế của ứng dụng.

### 8.3. computeIfPresent cần giá trị khác null

Một khóa vẫn có thể `containsKey(k) == true` nhưng `computeIfPresent` không chạy hàm vì giá trị hiện tại là null.

Vì vậy chữ “Present” trong tên phương thức không thể được đọc tách khỏi điều kiện cụ thể của hợp đồng. Nó không chỉ là kiểm tra sự tồn tại của khóa.

Nếu hàm thực sự được gọi cho giá trị cũ khác null và trả null, ánh xạ bị xóa.

### 8.4. compute trả null là yêu cầu loại bỏ ánh xạ

`compute` đưa null vào vị trí giá trị cũ khi không có ánh xạ hoặc có ánh xạ null. Chỉ từ tham số giá trị cũ, hàm không phân biệt được hai tình huống này.

Nếu hàm trả null, ánh xạ bị bỏ nếu có; nếu vốn chưa có thì vẫn không có. Không dùng `compute(k, ... -> null)` để “lưu một giá trị null” như `put(k, null)`.

### 8.5. merge nhận giá trị đầu vào khác null

Dù `HashMap.put(k, null)` hợp lệ, `merge(k, null, fn)` không vì thế hợp lệ. Tham số giá trị đưa vào của `merge` phải khác null.

Khi giá trị cũ null hoặc chưa có ánh xạ, `merge` gắn giá trị đầu vào khác null trực tiếp. Khi đã có giá trị khác null, nó mới dùng hàm ghép. Nếu hàm ghép trả null, ánh xạ được xóa.

Phải phân biệt ba vị trí:

1. Khóa truyền vào có thể null với `HashMap`.
2. Giá trị đầu vào của `merge` phải khác null.
3. Kết quả null từ hàm ghép có nghĩa loại bỏ ánh xạ.

Cùng một từ null nhưng vai trò khác nhau; đọc đúng vị trí tham số và kiểu hợp đồng sẽ tránh nhầm.

## 9. Chương trình đầy đủ: kiểm chứng các quy tắc cập nhật

```java
import java.util.HashMap;
import java.util.Map;

public class HashMapNullUpdateMethodsDemo {
    public static void main(String[] args) {
        Map<String, String> states = new HashMap<>();
        states.put("draft", null);
        System.out.println("putIfAbsentOld="
                + states.putIfAbsent("draft", "ready"));
        System.out.println("draftNow=" + states.get("draft"));

        states.put("loadable", null);
        String loaded = states.computeIfAbsent("loadable", key -> "loaded");
        System.out.println("computeIfAbsentValue=" + loaded);

        String missingResult = states.computeIfAbsent("missing", key -> null);
        System.out.println("missingComputeResult=" + missingResult);
        System.out.println("hasMissingAfterCompute="
                + states.containsKey("missing"));

        states.put("explicit-null", null);
        String nullResult = states.computeIfAbsent("explicit-null", key -> null);
        System.out.println("existingNullComputeResult=" + nullResult);
        System.out.println("hasExplicitNullAfterComputeIfAbsent="
                + states.containsKey("explicit-null"));

        String skipped = states.computeIfPresent("explicit-null", (key, old) -> {
            throw new AssertionError("Must not run for a null value");
        });
        System.out.println("computeIfPresentResult=" + skipped);
        System.out.println("hasExplicitNullAfterComputeIfPresent="
                + states.containsKey("explicit-null"));

        states.compute("explicit-null", (key, old) -> null);
        System.out.println("hasExplicitNullAfterCompute="
                + states.containsKey("explicit-null"));

        String nullKeyValue = states.computeIfAbsent(
                null, key -> key == null ? "default" : key);
        System.out.println("computedNullKeyValue=" + nullKeyValue);

        Map<String, Integer> counts = new HashMap<>();
        counts.put("hits", null);
        System.out.println("firstMerge="
                + counts.merge("hits", 1, Integer::sum));
        System.out.println("secondMerge="
                + counts.merge("hits", 2, Integer::sum));
        System.out.println("mergeRemovalResult="
                + counts.merge("hits", 1, (old, incoming) -> null));
        System.out.println("hasHitsAfterMergeRemoval="
                + counts.containsKey("hits"));

        try {
            counts.merge("invalid", null, Integer::sum);
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            System.out.println("nullMergeInput=NullPointerException");
        }
    }
}
```

Lưu thành `HashMapNullUpdateMethodsDemo.java`, rồi biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 HashMapNullUpdateMethodsDemo.java
java HashMapNullUpdateMethodsDemo
```

Đầu ra chính xác:

```text
putIfAbsentOld=null
draftNow=ready
computeIfAbsentValue=loaded
missingComputeResult=null
hasMissingAfterCompute=false
existingNullComputeResult=null
hasExplicitNullAfterComputeIfAbsent=true
computeIfPresentResult=null
hasExplicitNullAfterComputeIfPresent=true
hasExplicitNullAfterCompute=false
computedNullKeyValue=default
firstMerge=1
secondMerge=3
mergeRemovalResult=null
hasHitsAfterMergeRemoval=false
nullMergeInput=NullPointerException
```

### 9.1. Đang có khóa mà putIfAbsent vẫn thay giá trị

`"draft"` đã có ánh xạ null, nhưng `putIfAbsent("draft", "ready")` vẫn ghi `"ready"`. Giá trị cũ trả về là null; nó không chứng minh trước đó chưa có khóa.

### 9.2. Hai computeIfAbsent trả null nhưng sự tồn tại khác nhau

Đối với `"missing"`, hàm trả null và khóa vẫn không có. Đối với `"explicit-null"`, hàm cũng trả null nhưng khóa vẫn có ánh xạ null:

```text
hasMissingAfterCompute=false
hasExplicitNullAfterComputeIfAbsent=true
```

Sau đó `computeIfPresent` không chạy hàm cho `"explicit-null"`; thân hàm có `AssertionError` để chương trình thất bại nếu giả thiết “không chạy” bị sai.

Cuối cùng, `compute(... -> null)` xóa ánh xạ của `"explicit-null"`, nên `containsKey` thành false.

### 9.3. Hàm tính phải xử lý được khóa null nếu ứng dụng cho phép

Lời gọi `computeIfAbsent(null, ...)` dùng hàm có kiểm tra `key == null`, nên tạo giá trị `"default"` cho khóa null.

Nếu hàm thay bằng `key -> key.toUpperCase()`, ngoại lệ có thể đến từ thân hàm do gọi phương thức trên null, dù map chấp nhận khóa null. Quy tắc của container không thay thế việc kiểm tra dữ liệu trong hàm mà ứng dụng truyền vào.

### 9.4. Giá trị cũ null không được truyền vào Integer::sum ở lần merge đầu

`counts` bắt đầu với `"hits" → null`. Lần `merge` đầu nhận giá trị `1` trực tiếp, nên kết quả là `1`; không cố cộng một `Integer` null.

Lần tiếp theo có giá trị cũ `1`, ghép với `2` thành `3`. Lần sau hàm ghép trả null, xóa ánh xạ. Truyền giá trị đầu vào null cho một lần `merge` khác bị từ chối bằng `NullPointerException`.

Các hàm tính trong ví dụ không tự thêm/xóa ánh xạ của map trong lúc chạy. Hãy trả kết quả theo hợp đồng thay vì sửa chính map từ trong hàm rồi mong thao tác vẫn có ngữ nghĩa đơn giản.

## 10. Duyệt và tính toán phải xét khả năng có null

`HashMap.forEach` và các view không tự bỏ qua ánh xạ có khóa hoặc giá trị null.

Các đoạn dưới là **mẫu có thể lỗi, chỉ để phân tích**, với giả thiết map cho phép và đang chứa null:

```java
// Có thể lỗi vì key là null.
map.forEach((key, value) -> System.out.println(key.length()));

// Có thể lỗi mở hộp vì một giá trị Integer là null.
for (Integer value : stock.values()) {
    int quantity = value;
}
```

Nếu cần hiển thị, có thể dùng chuỗi thay thế chỉ cho trình bày. Nếu cần tính toán, phải quyết định null có nghĩa gì trước.

Ví dụ “số lượng chưa biết” khác với “số lượng bằng 0”. Tự đổi null thành `0` có thể làm tổng trông như đã đầy đủ dù một phần dữ liệu chưa xác định.

### getOrDefault có thể vẫn gây lỗi mở hộp

Đoạn minh họa sau cũng có thể gây `NullPointerException` nếu `"draft"` đã ánh xạ tới null:

```java
// stock là Map<String, Integer> và có "draft" -> null.
int count = stock.getOrDefault("draft", 0);
```

Giá trị dự phòng `0` không được chọn chỉ vì giá trị hiện có là null. Phương thức trả `Integer null`, sau đó phép gán cần mở hộp thành `int` và thất bại.

Khi lỗi xảy ra ở bước đọc/mở hộp này, bản thân ánh xạ null không tự bị xóa.

## 11. Chương trình đầy đủ: hiển thị và cộng phần dữ liệu đã biết

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class HashMapNullPresentationDemo {
    public static void main(String[] args) {
        Map<String, Integer> stock = new HashMap<>();
        stock.put(null, null);
        stock.put("pen", 3);
        stock.put("draft", null);

        List<String> rows = new ArrayList<>();
        stock.forEach((key, value) -> {
            String keyText = Objects.toString(key, "<null-key>");
            String valueText = Objects.toString(value, "<null-value>");
            rows.add(keyText + "=" + valueText);
        });
        Collections.sort(rows);
        System.out.println("rows=" + rows);

        int knownTotal = 0;
        int unknownCount = 0;
        for (Integer value : stock.values()) {
            if (value == null) {
                unknownCount++;
            } else {
                knownTotal += value;
            }
        }
        System.out.println("knownTotal=" + knownTotal);
        System.out.println("unknownCount=" + unknownCount);

        try {
            int count = stock.get("draft");
            System.out.println("unexpectedCount=" + count);
        } catch (NullPointerException expected) {
            System.out.println("nullUnboxing=NullPointerException");
        }
        System.out.println("draftStillExists=" + stock.containsKey("draft"));
    }
}
```

Lưu thành `HashMapNullPresentationDemo.java`, rồi biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 HashMapNullPresentationDemo.java
java HashMapNullPresentationDemo
```

Đầu ra chính xác:

```text
rows=[<null-key>=<null-value>, draft=<null-value>, pen=3]
knownTotal=3
unknownCount=2
nullUnboxing=NullPointerException
draftStillExists=true
```

### Vì sao các dòng được đưa vào danh sách rồi sắp xếp?

`HashMap` không bảo đảm thứ tự duyệt. Chương trình thu các dòng hiển thị vào một danh sách riêng rồi sắp xếp chuỗi để có stdout xác định.

Các nhãn `<null-key>`, `<null-value>` chỉ là cách hiển thị. Chương trình không thay khóa null thành chuỗi nhãn và không thay giá trị null trong map thành một chuỗi khác.

Trong ứng dụng thật, nhãn cần chọn sao cho người đọc hiểu. Nếu dữ liệu thật có thể chứa cùng chuỗi nhãn, cần cách trình bày phân biệt; không lấy nhãn hiển thị làm định danh dữ liệu.

### knownTotal không phải lời khẳng định mọi số lượng đều đã biết

Hai ánh xạ có giá trị null được đếm vào `unknownCount=2`; giá trị `3` góp vào `knownTotal=3`.

Tên biến và đầu ra cho biết đây là tổng của phần dữ liệu đã biết. Chúng không âm thầm giả định hai giá trị chưa biết bằng `0`.

Phần mở hộp cố ý gây lỗi ở `stock.get("draft")`. Sau khi bắt ngoại lệ, `draftStillExists=true` xác nhận việc đọc không làm ánh xạ biến mất.

## 12. Chuyển dữ liệu sang một Map khác phải kiểm tra chính sách null

Kiểu khai báo `Map<K, V>` không áp đặt mọi hiện thực phải cho khóa và giá trị null. `HashMap` có chính sách riêng đã nêu; đổi cách tạo hoặc đổi hiện thực có thể thay chính sách.

| Cách tạo hoặc loại map | Khóa/giá trị null liên quan |
|---|---|
| `new HashMap<>()` | Cho phép một khóa null, nhiều giá trị null |
| `new HashMap<>(source)` | Có thể chép các ánh xạ null hợp lệ từ một map nguồn có thật |
| `Map.of(...)` | Không chấp nhận khóa hoặc giá trị null |
| `Map.copyOf(source)` | Không chấp nhận các ánh xạ có khóa hoặc giá trị null |
| `Collections.unmodifiableMap(source)` | Là lớp bọc đọc qua nguồn; không tự xóa hay cấm các null nguồn đã có |
| `ConcurrentHashMap` | Không chấp nhận khóa hoặc giá trị null |

Không nên đọc bảng này thành việc các lựa chọn có thể thay thế nhau chỉ bằng cách đổi một dòng:

- Một map cho sửa khác với một map không cho sửa.
- Một bản chép ánh xạ khác với một lớp bọc sống.
- Chính sách null là một trục khác cần xem rõ.

### Sao chép null không có nghĩa nguồn có thể là null

`new HashMap<>(source)` có thể chép một ánh xạ khóa null hoặc giá trị null từ nguồn. Nhưng `source` vẫn phải là một map hợp lệ; việc tham số map nguồn là null là một lỗi khác với việc một entry trong nguồn mang null.

Tương tự, `putAll` vào một `HashMap` sửa được có thể chép các giá trị null từ nguồn; nó không tự bỏ qua hay lọc bỏ chúng. Nếu khóa đã có, giá trị null nguồn có thể thay giá trị trước đó ở đích.

### Không cho sửa không đồng nghĩa không có null

Một lớp bọc `unmodifiableMap` ngăn sửa ánh xạ qua lớp bọc, nhưng nếu nguồn cho null thì phép đọc qua lớp bọc vẫn có thể quan sát null. Nếu nguồn được sửa hợp lệ qua một tham chiếu khác, lớp bọc có thể thấy thay đổi đó.

Ngược lại, các factory như `Map.of` vừa tạo map không cho sửa vừa có chính sách từ chối null. Không được suy chính sách của mọi map không cho sửa chỉ từ một nhóm factory.

### Khi chuyển sang ConcurrentHashMap

Việc `ConcurrentHashMap` không cho null là khác biệt phải xử lý theo nghĩa dữ liệu. Không nên chỉ bỏ các ánh xạ null hoặc thay chúng bằng `0`, chuỗi rỗng hay một đối tượng bất kỳ nếu chưa xác định nghiệp vụ.

Có thể cần một trạng thái tường minh như “chưa tải”, “không tìm thấy”, “đã có dữ liệu”, hoặc một kiểu kết quả riêng. Bài này không thay thế thiết kế truy cập đồng thời; điểm cần nhớ là chính sách chấp nhận dữ liệu cũng thay đổi khi đổi hiện thực.

## 13. Chương trình đầy đủ: bản chép, lớp bọc và kiểm tra đầu vào

```java
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class HashMapNullCopyPoliciesDemo {
    private static void expectNullPointer(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            System.out.println(label + "=NullPointerException");
        }
    }

    private static void putNonNull(
            Map<String, String> target, String key, String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        target.put(key, value);
    }

    public static void main(String[] args) {
        Map<String, String> source = new HashMap<>();
        source.put(null, "default");
        source.put("draft", null);

        Map<String, String> copied = new HashMap<>(source);
        Map<String, String> readOnly = Collections.unmodifiableMap(source);
        System.out.println("copyHasNullKey=" + copied.containsKey(null));
        System.out.println("copyHasDraft=" + copied.containsKey("draft"));
        System.out.println("copyDraftValue=" + copied.get("draft"));

        source.put("draft", "ready");
        source.put("later", null);
        System.out.println("copyDraftAfterSourceUpdate=" + copied.get("draft"));
        System.out.println("readOnlyDraft=" + readOnly.get("draft"));
        System.out.println("readOnlyHasLater=" + readOnly.containsKey("later"));

        try {
            readOnly.put("new", "value");
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            System.out.println("readOnlyPut=UnsupportedOperationException");
        }

        expectNullPointer("mapCopyOf", () -> Map.copyOf(source));
        expectNullPointer("mapOfNullValue", () -> Map.of("draft", null));
        expectNullPointer("mapOfNullKey", () -> Map.of(null, "default"));

        Map<String, String> concurrent = new ConcurrentHashMap<>();
        expectNullPointer("concurrentNullKey",
                () -> concurrent.put(null, "value"));
        expectNullPointer("concurrentNullValue",
                () -> concurrent.put("draft", null));

        Map<String, String> validated = new HashMap<>();
        putNonNull(validated, "ok", "ready");
        expectNullPointer("boundaryNullKey",
                () -> putNonNull(validated, null, "value"));
        expectNullPointer("boundaryNullValue",
                () -> putNonNull(validated, "draft", null));
        System.out.println("validatedSize=" + validated.size());
        System.out.println("validatedHasNullKey="
                + validated.containsKey(null));
        System.out.println("validatedHasNullValue="
                + validated.containsValue(null));
    }
}
```

Lưu thành `HashMapNullCopyPoliciesDemo.java`, rồi biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 HashMapNullCopyPoliciesDemo.java
java HashMapNullCopyPoliciesDemo
```

Đầu ra chính xác:

```text
copyHasNullKey=true
copyHasDraft=true
copyDraftValue=null
copyDraftAfterSourceUpdate=null
readOnlyDraft=ready
readOnlyHasLater=true
readOnlyPut=UnsupportedOperationException
mapCopyOf=NullPointerException
mapOfNullValue=NullPointerException
mapOfNullKey=NullPointerException
concurrentNullKey=NullPointerException
concurrentNullValue=NullPointerException
boundaryNullKey=NullPointerException
boundaryNullValue=NullPointerException
validatedSize=1
validatedHasNullKey=false
validatedHasNullValue=false
```

### 13.1. HashMap chép được các ánh xạ null

`copied` chứa khóa null và khóa `"draft"` có giá trị null. Sau khi nguồn thay `"draft"` thành `"ready"`, bản chép vẫn giữ ánh xạ cũ của riêng nó.

Đây là sự độc lập của cấu trúc ánh xạ, không phải sao chép sâu các đối tượng. Ví dụ dùng giá trị `String` để tránh trộn chủ đề null với việc sửa nội dung đối tượng khả biến.

### 13.2. Lớp bọc vẫn nhìn thấy nguồn

`readOnlyDraft=ready` và `readOnlyHasLater=true` vì lớp bọc đọc qua nguồn. Thử ghi qua lớp bọc bị `UnsupportedOperationException`.

Hai yếu tố này không mâu thuẫn: không cho sửa qua tham chiếu lớp bọc không phải việc chụp và đóng băng toàn bộ dữ liệu nguồn.

### 13.3. Factory và map đồng thời từ chối null

Ba phép thử `Map.copyOf`, `Map.of` và hai phép thử trên `ConcurrentHashMap` kiểm chứng các giới hạn null tương ứng.

Các hàm kiểm tra ném `AssertionError` nếu thao tác được mong là từ chối null lại chạy bình thường. Vì vậy đầu ra không chỉ là thông báo in sẵn; nó phản ánh ngoại lệ thực sự được quan sát.

### 13.4. Kiểm tra ở biên có phạm vi bảo đảm

`putNonNull` gọi `Objects.requireNonNull` trên khóa và giá trị trước khi ghi. Trong ví dụ, hai đầu vào null bị từ chối, nên map được kiểm tra còn đúng một ánh xạ hợp lệ.

Tuy nhiên, một hàm trợ giúp không tự biến mọi `HashMap` thành một loại map cấm null. Nếu nơi khác giữ tham chiếu sửa được và gọi thẳng `put`, `putAll`, `replaceAll` hoặc đổi giá trị qua entry, nó vẫn có thể ghi null.

Muốn giữ một quy tắc “không có null” cho toàn bộ ứng dụng, phải kiểm soát các đường ghi và quyền sở hữu dữ liệu. Khai báo generic `Map<String, String>` tự nó không khiến Java cấm truyền null vào vị trí khóa hay giá trị.

## 14. Chọn ý nghĩa null một cách nhất quán

Thư viện không tự quyết định null có nghĩa là “chưa nhập”, “không tồn tại”, “không áp dụng” hay “lỗi”. Nếu nhiều phần của ứng dụng tự hiểu khác nhau, cùng một ánh xạ null có thể gây các kết quả trái ngược.

Trước khi chọn biểu diễn, cần xác định:

1. Có cần phân biệt chưa có bản ghi với có bản ghi nhưng chưa có giá trị không?
2. Khóa null có một ý nghĩa nghiệp vụ duy nhất hay chỉ đang che việc thiếu định danh?
3. Có thể có nhiều bản ghi thiếu định danh cần giữ riêng không?
4. Những phép tính, hàm tải và phép ghép có đối xử với null đúng ý nghĩa ấy không?
5. Dữ liệu có được chuyển sang factory hoặc hiện thực khác có chính sách null khác không?

### Khi null có thể là biểu diễn phù hợp

Nếu một map nội bộ được sở hữu và truy cập có kiểm soát, các trạng thái được định nghĩa rõ, các đường đọc/xử lý đều xét đầy đủ và việc dùng null không làm mất dữ liệu cần phân biệt, nó có thể là một lựa chọn biểu diễn.

Ví dụ, trạng thái “đã có bản ghi nhưng chưa nhập mô tả” có thể được phân biệt với “chưa có bản ghi” bằng sự tồn tại của khóa, nếu đây là chính sách đã được thống nhất.

### Khi nên biểu diễn trạng thái tường minh

Nếu có nhiều lý do chưa có giá trị hoặc nhiều bước chuyển trạng thái, một null duy nhất có thể không đủ thông tin. Một kiểu dữ liệu có trường trạng thái hoặc một kiểu kết quả riêng thường giúp chương trình diễn đạt đúng hơn.

Không thay null bằng chuỗi rỗng, `0` hoặc một khóa đặc biệt một cách máy móc: các giá trị ấy có thể đã là dữ liệu thật hợp lệ. Mỗi cách thay cần quy tắc để tránh làm hai trạng thái khác nhau trở thành một.

Một khóa enum hoặc kiểu khóa có tên ý nghĩa đôi khi diễn đạt vai trò “mặc định” rõ hơn null. Nhưng lựa chọn phải theo dữ liệu và cách sử dụng, không phải chỉ để tránh nhìn thấy từ null trong mã.

## 15. Những lỗi thường gặp

| Biểu hiện | Nguyên nhân có thể gặp | Cách xử lý đúng ý nghĩa |
|---|---|---|
| Nhiều bản ghi thiếu mã chỉ còn bản ghi cuối | Tất cả dùng cùng khóa null | Giữ định danh riêng hoặc đổi cấu trúc theo nghiệp vụ |
| `get` trả null rồi kết luận khóa không có | Bỏ qua khả năng giá trị null | Kiểm tra sự tồn tại khi cần phân biệt |
| `getOrDefault` vẫn trả null | Khóa có ánh xạ null | Chọn quy tắc dự phòng rõ ràng |
| `remove` trả null rồi kết luận không xóa gì | Giá trị cũ có thể là null | Ghi nhận thông tin tồn tại theo cơ chế truy cập phù hợp |
| `compute(... -> null)` làm mất khóa | Null là tín hiệu xóa trong phương thức này | Dùng đúng API nếu muốn lưu giá trị null |
| `merge(k, null, fn)` ném ngoại lệ | Tham số đầu vào này bắt buộc khác null | Không suy chính sách `put` cho mọi phương thức |
| Đọc số lượng bị NullPointerException | Mở hộp `Integer null` | Xử lý null trước phép tính |
| Chuyển sang `Map.copyOf` bị lỗi | Nguồn có khóa/giá trị null | Thiết kế cách chuyển trạng thái hoặc chọn cấu trúc phù hợp |
| Lớp bọc không cho sửa vẫn thấy null | Hạn chế sửa và chính sách null là hai trục | Hiểu lớp bọc đọc qua nguồn, không tự làm sạch dữ liệu |

## 16. Tự kiểm tra

### Câu 1: Ba lần ghi khóa null

Map rỗng lần lượt `put(null, "A")`, `put(null, "B")`, `put(null, "C")`. Kích thước và giá trị hiện tại của khóa null là gì?

**Đáp án:** Kích thước `1`, giá trị `"C"`. Ba lần ghi cùng khóa không tạo ba ánh xạ.

### Câu 2: Ba khóa khác nhau, cùng giá trị null

Map rỗng ghi `"a" → null`, `"b" → null`, `"c" → null`. Size là bao nhiêu? Có vi phạm tính duy nhất không?

**Đáp án:** Size là `3`. Khóa khác nhau; giá trị được phép lặp nên không vi phạm.

### Câu 3: null và "null"

Map có khóa null và khóa chuỗi `"null"`. Chúng có bắt buộc là một khóa không?

**Đáp án:** Không. Chuỗi `"null"` là đối tượng có nội dung; null là tham chiếu không có đối tượng.

### Câu 4: get(null) trả null

Có thể kết luận không có khóa null không?

**Đáp án:** Không. Có thể đang có ánh xạ `null → null`. Dùng `containsKey(null)` khi cần phân biệt sự tồn tại, trong một cơ chế truy cập phù hợp.

### Câu 5: Có ánh xạ null và giá trị dự phòng

Khóa `"draft"` tồn tại với giá trị null. `getOrDefault("draft", "fallback")` trả gì?

**Đáp án:** Null. Giá trị dự phòng được dùng khi không có ánh xạ, không tự thay mọi null.

### Câu 6: Xóa trả null

Khóa tồn tại với giá trị null, rồi `remove(k)` trả null. Có thể phép xóa đã làm size giảm không?

**Đáp án:** Có. Giá trị cũ được trả ra là null; ánh xạ vẫn có thể vừa được loại bỏ.

### Câu 7: computeIfAbsent và compute

Khóa đang có ánh xạ null. Hai lời gọi riêng trong hai tình huống độc lập là `computeIfAbsent(k, x -> null)` và `compute(k, (x, old) -> null)`. Sự tồn tại của khóa sau đó khác nhau thế nào?

**Đáp án:** Lời gọi đầu giữ ánh xạ null đã có; lời gọi thứ hai xóa ánh xạ. Không thể diễn giải mọi kết quả null từ hàm tính như cùng một tín hiệu.

### Câu 8: putIfAbsent

Khóa đã có nhưng giá trị null. `putIfAbsent(k, "ready")` có thể ghi `"ready"` không?

**Đáp án:** Có. Phương thức xét cả trường hợp đang ánh xạ tới null, không chỉ trường hợp `containsKey` là false.

### Câu 9: merge với đầu vào null

`HashMap` cho null value, vậy `merge(k, null, fn)` có được chấp nhận chỉ vì điều đó không?

**Đáp án:** Không. Giá trị đầu vào của `merge` phải khác null. Cần đọc hợp đồng của từng phương thức.

### Câu 10: Xóa null qua values

Có ba ánh xạ giá trị null. Một lần `values().remove(null)` có chắc xóa cả ba không?

**Đáp án:** Không. Nó xóa một lần xuất hiện phù hợp nếu có. Muốn lọc hết phải thực hiện một phép lọc tương ứng và chấp nhận việc bỏ các ánh xạ đó.

### Câu 11: Không cho sửa có nghĩa không null?

Một lớp bọc `Collections.unmodifiableMap` trên `HashMap` có null. Phép đọc qua lớp bọc có thể trả null không?

**Đáp án:** Có. Lớp bọc không tự lọc hoặc thay null; nó ngăn sửa ánh xạ qua tham chiếu lớp bọc.

### Câu 12: Đếm số lượng chưa biết

Map chứa `"pen" → 3`, `"draft" → null`. Có được kết luận tổng số lượng thật bằng `3` nếu null có nghĩa “chưa biết” không?

**Đáp án:** Chỉ biết tổng phần đã biết là `3`. Còn một giá trị chưa xác định; không tự biến nó thành số `0` nếu nghiệp vụ chưa quy định.

## 17. Thực hành: giữ đủ trạng thái khi đọc

Viết một chương trình dùng `HashMap<String, Integer>` và tạo:

```text
null    → 5
"draft" → null
"ready" → 8
```

Thực hiện:

1. Đếm số ánh xạ, số khóa null và số giá trị null.
2. Đọc `"draft"` và một khóa `"missing"` chưa có; in cả giá trị lẫn sự tồn tại.
3. So kết quả `getOrDefault` của hai khóa đó với cùng một số dự phòng.
4. Thay giá trị khóa null thành null; đếm lại null values.
5. Xóa riêng `"draft"` và giải thích vì sao kết quả trả về có thể null dù size giảm.
6. Thu các dòng để hiển thị theo một thứ tự xác định mà không giả định thứ tự HashMap.
7. Thử sao chép bằng `new HashMap<>(source)` và so chính sách với `Map.copyOf(source)`.

Các mốc cần đạt:

```text
Ban đầu: size = 3, null keys = 1, null values = 1.
draft tồn tại; missing không tồn tại.
get của cả hai có thể cùng trả null.
getOrDefault chỉ dùng dự phòng cho missing.
Sau khi put(null, null): null values = 2, size vẫn = 3.
Sau khi remove("draft"): size = 2, còn khóa null.
```

Cuối cùng viết rõ một lựa chọn nghiệp vụ:

- Nếu null mang trạng thái có ý nghĩa, mô tả cách mọi đường đọc giữ sự khác nhau với thiếu khóa.
- Nếu ứng dụng cấm null, chỉ ra nơi kiểm tra và cách ngăn các đường ghi khác bỏ qua quy tắc.
- Nếu cần nhiều trạng thái thiếu giá trị, đề xuất một biểu diễn có tên trạng thái rõ thay vì gộp tất cả vào null.

Mục tiêu là bảo toàn thông tin dữ liệu và dùng API theo đúng hợp đồng, không chỉ làm cho chương trình hết ném ngoại lệ.

## Tài liệu đối chiếu

- [Java 17 — HashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html): hỗ trợ khóa/giá trị null và các phương thức cập nhật.
- [Java 17 — Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): phân biệt thiếu ánh xạ với giá trị null; `getOrDefault`, `compute`, `merge` và các factory không chấp nhận null.
- [Java 17 — Objects](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html): hiển thị tham chiếu null và kiểm tra đầu vào bằng `requireNonNull`.
- [Java 17 — Collections.unmodifiableMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableMap(java.util.Map)): lớp bọc không cho sửa đọc qua map nguồn.
- [Java 17 — ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): chính sách không chấp nhận khóa hoặc giá trị null.

Năm chương trình độc lập dùng API tương thích Java 17 trở lên, đã được biên dịch bằng `javac --release 17 -encoding UTF-8` và chạy trên JDK 24.0.2. Các khối đầu ra là kết quả thực tế; không phụ thuộc vào thứ tự duyệt không được bảo đảm của `HashMap`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-map-007">Vì sao thứ tự duyệt quan sát được không phải hợp đồng</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao thứ tự duyệt quan sát được không phải hợp đồng

## 1. Nhìn thấy một thứ tự chưa có nghĩa được quyền dựa vào nó

Khi in một HashMap nhỏ, bạn có thể thấy các khóa hiện ra theo thứ tự rất quen: đúng thứ tự tăng dần, giống thứ tự chèn hoặc lặp lại y hệt qua nhiều lần chạy. Từ đó rất dễ viết thêm mã giả định phần tử được duyệt đầu tiên là phần tử đã thêm đầu tiên.

Điều quan sát là một kết quả thật của lần chạy. Nhưng một kết quả lặp lại chưa tự trở thành lời hứa của API.

HashMap không bảo đảm một thứ tự duyệt cụ thể, cũng không bảo đảm thứ tự đó luôn giữ nguyên theo thời gian. Mã ứng dụng cần đúng với mọi thứ tự mà hợp đồng cho phép, chứ không chỉ với thứ tự tình cờ thấy trong bộ dữ liệu thử. [Tài liệu HashMap, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html).

Ví dụ yêu cầu “chọn hồ sơ nhập sớm nhất” không được giải bằng cách lấy phần tử đầu của `map.entrySet()` nếu map là HashMap và thời điểm nhập không được lưu riêng. Tương tự, xuất một báo cáo có thứ tự cố định cần một quy tắc sắp hoặc một cấu trúc giữ đúng thứ tự đó.

Bài học này giải thích vì sao thứ tự trông ổn định, cách tự tạo một phản ví dụ, và cách thiết kế việc duyệt cùng kiểm thử theo đúng yêu cầu.

## 2. Ba mức thông tin: quan sát, triển khai và hợp đồng

| Mức thông tin | Ví dụ | Có thể dùng để làm gì? |
| --- | --- | --- |
| Quan sát trong một lần chạy | “Tôi thấy [0, 16, 1]” | Ghi nhận dữ liệu thực nghiệm |
| Giải thích theo triển khai | “Các khóa này ở những bucket này trong OpenJDK đang xét” | Hiểu vì sao quan sát xuất hiện |
| Bảo đảm công khai của API | “Loại Map này duyệt theo thứ tự chèn” | Thiết kế hành vi cần đúng và kiểm thử theo hợp đồng |

Một giải thích nội bộ chính xác vẫn không tự nâng cấp thành bảo đảm công khai. Bạn có thể đọc mã nguồn một phiên bản, dự đoán đúng thứ tự của một dữ liệu và vẫn không có quyền yêu cầu mọi phiên bản HashMap tương lai giữ thứ tự đó.

Ngược lại, một bảo đảm thứ tự công khai là điều có thể đưa vào hợp đồng ứng dụng khi đã chọn đúng loại cấu trúc và cách cấu hình.

### “Không quy định” không đồng nghĩa “ngẫu nhiên mỗi lần”

HashMap không có nghĩa vụ xáo thứ tự trong mỗi lần duyệt hoặc mỗi lần chạy. Nếu đầu vào, lịch sử cập nhật và triển khai không đổi, việc thường thấy một thứ tự giống nhau hoàn toàn không mâu thuẫn với việc API không bảo đảm thứ tự.

Đừng kiểm thử bằng cách yêu cầu hai lần duyệt HashMap phải khác nhau để chứng minh nó không có hợp đồng thứ tự. Hai lần có thể giống nhau. Đừng yêu cầu chúng phải giống nhau chỉ vì đã quan sát nhiều lần như thế.

Điểm cần nhớ là tính không được bảo đảm, không phải một thuật toán bắt buộc tạo ngẫu nhiên.

## 3. Vì sao HashMap nhỏ đôi khi trông như đã sắp xếp?

HashMap tổ chức các ánh xạ để tra theo khóa thông qua mã băm và bucket. Thứ tự duyệt quan sát được chịu ảnh hưởng của cách triển khai đi qua bảng và các nút trong bucket, không phải một quy tắc công khai “sắp theo khóa” hay “nhớ thứ tự chèn”.

Trong OpenJDK 17, iterator đi qua các vị trí của bảng và các nút mà nó gặp. Điều đó có thể làm một tập khóa số nguyên nhỏ hiện ra theo một mẫu dễ đoán. [Mã nguồn HashMap, OpenJDK 17](https://raw.githubusercontent.com/openjdk/jdk/jdk-17%2B35/src/java.base/share/classes/java/util/HashMap.java).

Giả sử thêm các khóa theo thứ tự:

~~~text
0, 16, 1
~~~

Trong mô hình của ví dụ ở dung lượng 16, 0 và 16 cùng được phân tới một bucket, còn 1 ở một bucket khác. Với dung lượng lớn hơn, 16 có thể nằm ở vị trí tách khỏi 0. Đường duyệt bảng lúc đó có thể cho một thứ tự khác.

Vì thế, cùng ba ánh xạ nhưng hai cấu hình dung lượng ban đầu có thể tạo ra:

~~~text
Một lần quan sát ở Map thứ nhất: [0, 16, 1]
Một lần quan sát ở Map thứ hai: [0, 1, 16]
~~~

Thứ tự thứ hai giống tăng dần không chứng minh HashMap là một bản đồ sắp xếp. Nó là kết quả của dữ liệu và cấu trúc bảng đang xét. Thứ tự thứ nhất cũng không chứng minh HashMap giữ thứ tự chèn nói chung; đó chỉ là sự trùng hợp với lịch sử thêm của ví dụ.

Những yếu tố có thể ảnh hưởng một quan sát gồm dung lượng, cách xử lý va chạm, các lần tăng bảng, lịch sử thêm/xóa và chi tiết phiên bản triển khai. Không cần mọi yếu tố đều thay đổi thứ tự ở mọi trường hợp; chỉ cần API không hứa thứ tự thì mã không được phụ thuộc nó.

### Thêm rồi xóa cũng có thể để lại một lịch sử khác

Hai Map có cùng ánh xạ hiện tại không nhất thiết có cùng cấu trúc bên trong. Một Map từng lớn lên rồi được xóa bớt có thể còn một bảng khác với Map vừa được tạo nhỏ.

Đây là lý do một bộ kiểm thử chỉ tạo Map mới với vài phần tử không đại diện đầy đủ cho một Map đã sống lâu trong ứng dụng.

Không dùng sự khác nhau quan sát được sau một lịch cập nhật như một điều bắt buộc mọi HashMap phải thể hiện. Mục đích là thấy có những thay đổi được phép mà không làm sai ngữ nghĩa của Map.

## 4. Hai Map bằng nhau vẫn có thể duyệt khác nhau

Map mô tả quan hệ khóa–giá trị. Theo hợp đồng equals thông thường của Map, hai Map bằng nhau khi chúng biểu diễn cùng các ánh xạ, không cần cùng thứ tự duyệt. [Hợp đồng Map.equals, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#equals(java.lang.Object)).

Ví dụ hai Map đều có:

~~~text
0  -> zero
16 -> sixteen
1  -> one
~~~

Một lần duyệt cho `[0, 16, 1]`, lần kia cho `[0, 1, 16]`. Vẫn có thể có:

~~~java
first.equals(second) == true
~~~

Trong khi:

~~~java
new ArrayList<>(first.keySet())
        .equals(new ArrayList<>(second.keySet())) == false
~~~

Hai câu đang so những đối tượng có hợp đồng khác nhau:

- So Map: hỏi cùng ánh xạ không.
- So List: hỏi cùng các phần tử ở cùng vị trí không.

Việc chuyển sang List đã đưa thứ tự quan sát vào dữ liệu được đem so. Nó không chứng minh một trong hai Map chứa sai ánh xạ.

Nếu cần kiểm tra kết quả tính toán không có yêu cầu thứ tự, so Map trực tiếp thường phù hợp hơn so chuỗi `toString()` hoặc so List được tạo từ một lượt duyệt bất kỳ.

Nếu có yêu cầu thứ tự, phải kiểm tra yêu cầu đó riêng, sau khi đã chọn cách tạo thứ tự theo hợp đồng.

## 5. Chương trình Java 17+ để tự quan sát

Chương trình dùng khóa Integer khác null và các giá trị chuỗi đơn giản. Nó tạo hai HashMap với cùng lịch thêm ba ánh xạ nhưng dung lượng yêu cầu khác nhau, sau đó tạo một lịch tăng rồi xóa trên một Map.

Phần có nhãn `observed` ghi **thứ tự quan sát trong lần chạy**. Các dòng đó có thể khác ở một triển khai hoặc phiên bản khác mà chương trình vẫn dùng HashMap đúng hợp đồng.

Các phép `check` không yêu cầu một thứ tự cụ thể của HashMap. Chúng chỉ kiểm tra ánh xạ và những thứ tự đã được thiết lập rõ bằng LinkedHashMap, TreeMap hoặc sorted.

Lưu thành `HashMapOrderDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class HashMapOrderDemo {
    static void seed(Map<Integer, String> map) {
        map.put(0, "zero");
        map.put(16, "sixteen");
        map.put(1, "one");
    }

    static List<Integer> observedKeys(Map<Integer, String> map) {
        return new ArrayList<>(map.keySet());
    }

    static List<String> sortedReport(Map<Integer, String> source) {
        Map<Integer, String> sorted = new TreeMap<>(source);
        List<String> result = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : sorted.entrySet()) {
            result.add(entry.getKey() + "=" + entry.getValue());
        }
        return result;
    }

    static List<Integer> observedOriginalKeys(Map<Integer, String> map) {
        Set<Integer> original = Set.of(0, 16, 1);
        List<Integer> result = new ArrayList<>();
        for (Integer key : map.keySet()) {
            if (original.contains(key)) {
                result.add(key);
            }
        }
        return result;
    }

    static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Wrong mapping or explicit order");
        }
    }

    public static void main(String[] args) {
        Map<Integer, String> small = new HashMap<>(16);
        Map<Integer, String> large = new HashMap<>(64);
        seed(small);
        seed(large);

        List<Integer> observedSmall = observedKeys(small);
        List<Integer> observedLarge = observedKeys(large);
        System.out.println("observed small=" + observedSmall);
        System.out.println("observed large=" + observedLarge);
        System.out.println("same mappings=" + small.equals(large));
        System.out.println("same observed list="
                + observedSmall.equals(observedLarge));
        System.out.println("sorted small=" + sortedReport(small));
        System.out.println("sorted large=" + sortedReport(large));

        List<Integer> captured = List.copyOf(observedSmall);
        for (int key = 2; key <= 11; key++) {
            small.put(key, "extra-" + key);
        }
        System.out.println("observed original keys after growth="
                + observedOriginalKeys(small));
        for (int key = 2; key <= 11; key++) {
            small.remove(key);
        }
        System.out.println("same mappings after removing extras="
                + small.equals(large));
        System.out.println("captured earlier list=" + captured);
        System.out.println("fresh observed list=" + observedKeys(small));

        Map<Integer, String> linked = new LinkedHashMap<>();
        seed(linked);
        System.out.println("linked insertion order=" + observedKeys(linked));
        linked.put(16, "updated-sixteen");
        System.out.println("linked after existing put=" + observedKeys(linked));
        linked.remove(0);
        linked.put(0, "reinserted-zero");
        System.out.println("linked after remove and put=" + observedKeys(linked));

        Map<Integer, String> linkedCopy = new LinkedHashMap<>(large);
        System.out.println("linked copy of large=" + observedKeys(linkedCopy));
        Map<Integer, String> tree = new TreeMap<>(large);
        System.out.println("tree key order=" + observedKeys(tree));

        List<Integer> sortedKeys = small.keySet().stream().sorted().toList();
        System.out.println("explicit sorted stream=" + sortedKeys);
        Map<Integer, String> expected = Map.of(
                0, "zero", 16, "sixteen", 1, "one");
        check(small.equals(expected));
        check(large.equals(expected));
        check(sortedReport(small).equals(sortedReport(large)));
        check(observedKeys(linked).equals(List.of(16, 1, 0)));
        check(observedKeys(tree).equals(List.of(0, 1, 16)));
        check(sortedKeys.equals(List.of(0, 1, 16)));
        System.out.println("contract checks=PASS");
    }
}
~~~

Biên dịch và chạy:

~~~shell
javac --release 17 -encoding UTF-8 HashMapOrderDemo.java
java HashMapOrderDemo
~~~

Dùng JDK 17 hoặc mới hơn hỗ trợ phiên bản đích 17. Mã dùng cú pháp và API tương thích Java 17.

Stdout chính xác của lần chạy kiểm chứng:

~~~text
observed small=[0, 16, 1]
observed large=[0, 1, 16]
same mappings=true
same observed list=false
sorted small=[0=zero, 1=one, 16=sixteen]
sorted large=[0=zero, 1=one, 16=sixteen]
observed original keys after growth=[0, 1, 16]
same mappings after removing extras=true
captured earlier list=[0, 16, 1]
fresh observed list=[0, 1, 16]
linked insertion order=[0, 16, 1]
linked after existing put=[0, 16, 1]
linked after remove and put=[16, 1, 0]
linked copy of large=[0, 1, 16]
tree key order=[0, 1, 16]
explicit sorted stream=[0, 1, 16]
contract checks=PASS
~~~

Không biến các dòng quan sát HashMap thành chuỗi bắt buộc cho mọi máy. Ý nghĩa của bài chính là phân biệt bản ghi thực nghiệm này với bảo đảm API. Dòng `contract checks=PASS` dựa vào các yêu cầu hợp đồng được viết trong check, không dựa vào việc hai dòng observed đầu phải khác nhau.

### Các kết quả cần đối chiếu

`same mappings=true` và `same observed list=false` cùng xuất hiện, minh chứng khác nhau về thứ tự không làm mất tính bằng nhau của ánh xạ.

Hai dòng `sorted` giống nhau vì hàm báo cáo dùng TreeMap với thứ tự khóa tự nhiên. Thứ tự đã trở thành một bước xử lý có chủ đích, không phải đoán theo HashMap.

Sau khi thêm các khóa phụ rồi xóa chúng, Map nhỏ lại có cùng ánh xạ như Map lớn. Trong lần chạy, danh sách khóa mới quan sát khác danh sách đã chụp trước đó. Việc Map quay lại cùng dữ liệu không làm lịch sử cấu trúc tự quay ngược.

Danh sách `captured` vẫn giữ những phần tử và thứ tự đã chụp. Nó là một List riêng; việc giữ một quan sát trong một List không làm HashMap nguồn phải duyệt theo List đó về sau.

Các dòng LinkedHashMap, TreeMap và sorted stream có ý nghĩa khác: thứ tự của chúng đến từ những cơ chế được chọn rõ ràng. Chúng sẽ được dùng để thiết kế hành vi ổn định ở phần sau.

## 6. Khi cần thứ tự, chọn đúng loại bảo đảm

### Giữ thứ tự chèn bằng LinkedHashMap mặc định

Nếu thứ tự người dùng hoặc nguồn dữ liệu đưa vào là một phần của yêu cầu, có thể dùng:

~~~java
Map<Integer, String> map = new LinkedHashMap<>();
map.put(0, "zero");
map.put(16, "sixteen");
map.put(1, "one");
~~~

LinkedHashMap mặc định có thứ tự duyệt theo thứ tự chèn của các khóa. Việc put lại một khóa đang tồn tại thay giá trị mà không tự đưa khóa đó xuống cuối. Xóa khóa rồi thêm lại tạo một lần chèn mới, nên khóa xuất hiện ở vị trí chèn mới. [Tài liệu LinkedHashMap, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html).

Trong chương trình:

~~~text
Chèn 0, 16, 1: [0, 16, 1]
Put lại 16:    [0, 16, 1]
Xóa rồi thêm 0:[16, 1, 0]
~~~

Đây là các thao tác tuần tự trên LinkedHashMap mặc định. Loại này còn có chế độ thứ tự truy cập được chọn bằng constructor khác; chế độ đó có quy tắc riêng và không nên được gọi chung là “luôn giữ thứ tự chèn”.

### Sắp theo khóa bằng TreeMap

Nếu yêu cầu là luôn duyệt theo khóa tăng dần hoặc theo Comparator được định nghĩa:

~~~java
Map<Integer, String> sorted = new TreeMap<>(source);
~~~

Với các khóa Integer khác null trong ví dụ, TreeMap sắp theo thứ tự số tự nhiên, nên kết quả là 0, 1, 16. Thứ tự này độc lập với thứ tự đưa ba khóa vào.

TreeMap cung cấp thứ tự theo thứ tự tự nhiên của khóa hoặc Comparator đã chọn. Nó không tự sắp theo giá trị và không đồng nghĩa với thứ tự chèn. [Tài liệu TreeMap, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html).

Khi dùng khóa hoặc Comparator khác, cần bảo đảm các khóa so được với nhau và quan hệ so sánh phù hợp với ý nghĩa khóa. Không thay HashMap bằng TreeMap với một comparator coi các khóa khác nhau là đồng hạng rồi mặc định mọi ánh xạ sẽ giữ nguyên.

TreeMap với thứ tự tự nhiên cũng không phải lựa chọn sao chép mọi HashMap có khóa null mà không cần điều chỉnh. Ví dụ đầy đủ cố ý dùng các khóa Integer khác null để tập trung vào thứ tự.

### Giữ HashMap để tra, sắp lúc xuất

Nếu chủ yếu cần tra nhanh nhưng chỉ cần một báo cáo có thứ tự ở ranh giới xuất dữ liệu, có thể giữ HashMap rồi tạo kết quả đã sắp:

~~~java
List<Integer> keys = map.keySet().stream()
        .sorted()
        .toList();
~~~

Chỉ List kết quả được tạo theo thứ tự tăng của các khóa. Map nguồn không bị biến thành một Map sắp xếp. Nếu cần cả cặp, dùng cách tạo báo cáo từ các cặp phù hợp hoặc một Map đã sắp như hàm `sortedReport` trong chương trình.

Việc sắp có chi phí theo số phần tử và bộ nhớ kết quả. Đó là một phần công việc cần tính khi yêu cầu thật sự đòi một trình tự ổn định.


## 7. Sao chép có thể giữ một quan sát mà không phục hồi lịch sử

### new LinkedHashMap<>(hashMap) giữ thứ tự nào?

Xét:

~~~java
Map<Integer, String> copy = new LinkedHashMap<>(hashMap);
~~~

LinkedHashMap mới nhận các ánh xạ theo thứ tự nguồn được duyệt trong lúc sao chép. Sau đó, bản đồ mới giữ thứ tự chèn của chính nó.

Điều đó không khôi phục thứ tự người dùng đã chèn vào HashMap trước kia. Nếu HashMap đã bỏ thông tin thứ tự đó khỏi hợp đồng dữ liệu của bạn, một thao tác sao chép về sau không tự suy ra lại lịch sử.

Trong chương trình, seed thêm 0, 16, 1, nhưng Map large được quan sát theo 0, 1, 16. Sao chép large sang LinkedHashMap cho 0, 1, 16 trong lần chạy. Đây là thứ tự LinkedHashMap mới đã nhận, không phải lịch sử seed đã được phục hồi.

Nếu cần thứ tự nhập ban đầu, giữ nó ngay từ nơi tiếp nhận: dùng một cấu trúc có thứ tự phù hợp hoặc lưu thông tin vị trí/thời điểm cần dùng. Đừng đi qua một HashMap rồi trông chờ có thể dựng lại một thứ tự chưa được lưu.

### List chụp lại một lần duyệt

~~~java
List<Integer> snapshot = new ArrayList<>(hashMap.keySet());
~~~

List có vị trí phần tử cụ thể. Những lần duyệt List đó sẽ theo các vị trí đã lưu, miễn là chính List không bị sửa.

Nhưng việc tạo List mới từ HashMap ở một thời điểm khác có thể cho một quan sát khác. “Bản chụp này có thứ tự” khác với “nguồn có một thứ tự được bảo đảm”.

`List.copyOf` trong chương trình dùng các khóa khác null và tạo một List không cho sửa cấu trúc. Nó giữ các phần tử cùng thứ tự từ danh sách quan sát được truyền vào, không ràng buộc HashMap phải phát lại thứ tự đó về sau.

### Map.copyOf và chỉ đọc không đồng nghĩa giữ thứ tự

Một Map không cho cập nhật vẫn cần được xem hợp đồng thứ tự riêng. Không lấy thuộc tính “không sửa được” làm bằng chứng rằng nó giữ thứ tự chèn của nguồn.

Tương tự, bọc một Map thành giao diện chỉ đọc có thể giữ lại khả năng nhìn dữ liệu nguồn nhưng không tự tạo một thứ tự mà nguồn không hứa. Thứ tự, khả năng sửa đổi và sự độc lập với nguồn là các thuộc tính cần lựa chọn riêng.

| Kết quả cần có | Cần quyết định |
| --- | --- |
| Không được sửa qua đối tượng trả về | Chọn lớp bọc hoặc kiểu kết quả phù hợp |
| Không đổi ánh xạ khi nguồn đổi | Chọn một bản sao phù hợp |
| Có một thứ tự hiển thị ổn định | Chọn quy tắc thứ tự rõ ràng |
| Giữ đúng lịch sử nhập | Lưu lịch sử hoặc dùng cấu trúc giữ lịch sử ngay từ đầu |

Một cách tạo dữ liệu có thể đáp ứng một hàng nhưng không đáp ứng các hàng khác. Đó là lý do tên biến readOnly hoặc snapshot chưa đủ để mô tả hợp đồng.

## 8. Stream không tự biến thứ tự quan sát thành thứ tự nghiệp vụ

### forEach và forEachOrdered

`forEachOrdered` thực hiện hành động theo thứ tự gặp phần tử nếu stream có một thứ tự gặp được xác định. Từ “Ordered” không có nghĩa tự sắp khóa và cũng không có nghĩa tự suy ra lịch sử chèn của một HashMap. [Tài liệu Stream.forEachOrdered, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#forEachOrdered(java.util.function.Consumer)).

Do đó:

~~~java
hashMap.keySet().stream().forEachOrdered(System.out::println);
~~~

không phải cách yêu cầu HashMap phải xuất khóa theo thứ tự chèn hay thứ tự tăng dần. Nó chưa nêu quy tắc nghiệp vụ nào để tạo ra thứ tự đó.

Nếu cần khóa tăng dần, phải tạo thứ tự rõ:

~~~java
hashMap.keySet().stream()
        .sorted()
        .forEachOrdered(System.out::println);
~~~

Ví dụ giả sử các khóa so được theo thứ tự tự nhiên và không có null không được comparator hỗ trợ. Cách viết sorted đã thêm một quy tắc mà nguồn HashMap không có.

Với xử lý song song, `forEach` cũng không phải lựa chọn để yêu cầu hiệu ứng in ra bám theo một thứ tự nguồn đã chọn. Cần phân biệt thứ tự của dữ liệu kết quả với cách các tác vụ được thực thi.

### findFirst và limit không phải “sớm nhất” hay “nhỏ nhất”

Một phần tử do HashMap duyệt trước không mặc nhiên là:

- Khóa nhỏ nhất.
- Ánh xạ được thêm đầu tiên.
- Bản ghi có thời gian sớm nhất.
- Mục có độ ưu tiên cao nhất.

`findFirst` hoặc `limit` trên một nguồn không có quy tắc thứ tự cần thiết không thể tự cung cấp các ý nghĩa đó. Nếu cần phần tử có thời gian sớm nhất, hãy so trường thời gian; nếu cần khóa nhỏ nhất, hãy tính theo thứ tự khóa hoặc dùng cấu trúc có hợp đồng phù hợp.

### Sắp theo một tiêu chí nhưng còn đồng hạng

Giả sử báo cáo phải xếp theo điểm giảm. Hai mục cùng điểm 90 vẫn chưa có thứ tự tương đối được quyết định chỉ từ điểm.

Nếu yêu cầu đầu ra phải tái lập hoàn toàn, bổ sung tiêu chí phụ, chẳng hạn mã mục tăng dần. Một thuật toán sắp ổn định có thể giữ thứ tự đầu vào giữa các phần tử đồng hạng; nếu đầu vào lấy từ HashMap với thứ tự chưa được bảo đảm thì cách đó vẫn chưa tạo một thứ tự nghiệp vụ đầy đủ.

Trong chương trình, các khóa Integer là phân biệt và sorted theo khóa tự nhiên, nên không có sự mơ hồ tương tự giữa hai khóa khác nhau. Với kiểu khóa hoặc comparator khác, cần xem lại tính đầy đủ của quy tắc so sánh.

## 9. Kiểm thử đúng ngữ nghĩa

### Không có yêu cầu thứ tự: so ánh xạ

Với một hàm trả Map, nếu yêu cầu là trả đúng các cặp dữ liệu, có thể kiểm tra:

~~~java
Map<Integer, String> expected = Map.of(
        0, "zero",
        16, "sixteen",
        1, "one");

if (!actual.equals(expected)) {
    throw new AssertionError("Wrong mappings");
}
~~~

Đây là đoạn minh họa đặt trong một ngữ cảnh đã có actual. Không dùng thứ tự đối số của Map.of như một cách yêu cầu thứ tự duyệt của expected.

So riêng một tập giá trị có thể chưa đủ: hai khóa bị gắn nhầm giá trị vẫn có thể tạo cùng tập giá trị. So Map giữ được việc kiểm tra đúng quan hệ khóa–giá trị.

Cũng không so `actual.toString()` với một chuỗi được viết theo thứ tự bạn tình cờ quan sát. Nếu chuỗi chỉ được dùng để kiểm tra dữ liệu Map, phép kiểm tra đó đã đưa vào một yêu cầu ngoài hợp đồng.

### Có yêu cầu thứ tự: kiểm tra sau khi đã tạo đúng thứ tự

Nếu hàm trả một báo cáo theo khóa tăng dần, kiểm tra List kết quả theo vị trí là hợp lý:

~~~java
List<Integer> expectedOrder = List.of(0, 1, 16);
if (!sortedKeys.equals(expectedOrder)) {
    throw new AssertionError("Wrong key order");
}
~~~

Lúc này cần chỉ ra sortedKeys được tạo bằng quy tắc nào, như sorted hoặc TreeMap. Không sửa phép kiểm thử để bỏ qua thứ tự nếu chính yêu cầu sản phẩm cần một thứ tự rõ ràng.

Với một chức năng giữ thứ tự nhập, kiểm tra chuỗi sự kiện nhập cùng cấu trúc giữ thứ tự đó. Nếu dữ liệu đi qua một nguồn không có thứ tự, hãy quyết định nơi nào cần áp dụng quy tắc, thay vì đổ lỗi cho HashMap.

### Dùng dữ liệu giúp lộ giả định ngầm

Những bộ nhỏ gồm các khóa 1, 2, 3 rất dễ che lỗi “HashMap tự sắp tăng”. Một phép rà soát có giá trị nên có cả khóa chia sẻ bucket ở một cấu hình, lịch chèn khác nhau hoặc nguồn từng tăng rồi xóa.

Điều cần khẳng định là kết quả của thuật toán vẫn đúng với những lịch đó. Không cần khẳng định rằng mọi cặp lịch thử phải tạo thứ tự duyệt khác nhau, vì sự khác nhau cũng không phải điều HashMap bắt buộc cung cấp.

Chương trình đầy đủ tuân theo nguyên tắc này: nó in quan sát nhưng các check chức năng không đòi hai HashMap phải khác thứ tự.

### Kiểm thử không sửa được một hợp đồng bị bỏ trống

Một trăm lần chạy thành công có thể giúp phát hiện một số lỗi, nhưng không biến hành vi chưa được API bảo đảm thành điều được bảo đảm. Khi một tính năng thật sự phụ thuộc thứ tự, phải thể hiện quyết định đó trong cấu trúc hoặc thuật toán của tính năng.

Cố định phiên bản JDK giúp tái lập môi trường, nhưng vẫn nên phân biệt một phụ thuộc có chủ đích vào triển khai với một hợp đồng thư viện công khai. Nếu có phụ thuộc triển khai phục vụ một thí nghiệm, hãy ghi rõ điều kiện của thí nghiệm.

## 10. Áp dụng tại ranh giới xuất dữ liệu và lựa chọn cấu trúc

### Báo cáo và giao diện

Nếu danh sách cột, danh sách mục hoặc nhóm hiển thị cần theo một thứ tự, hãy nêu rõ “theo mã”, “theo tên”, “theo thời gian” hoặc “theo vị trí nhập”. Những cụm này mới cho biết phải chọn comparator, lưu trường dữ liệu nào hoặc dùng cấu trúc gì.

“Hiện như lúc tôi in Map” không phải một đặc tả đủ bền vững. Đổi dung lượng để tối ưu hiệu năng không nên làm thay đổi trình tự nghiệp vụ của màn hình.

### Văn bản, bộ nhớ đệm và so sánh đầu ra

Hai Map bằng nhau có thể có chuỗi hiển thị khác thứ tự. Nếu đầu ra văn bản được đem so khác biệt từng dòng, dùng làm đầu vào tính mã băm hoặc làm khóa bộ nhớ đệm, thứ tự cần được chuẩn hóa theo một quy tắc được công bố.

Sắp các khóa là một phần của chuẩn hóa, nhưng không tự giải quyết mọi thứ: nếu giá trị chứa Map lồng nhau thì còn thứ tự của lớp bên trong; nếu dùng một định dạng văn bản thì còn quy tắc biểu diễn ký tự và giá trị.

Hàm sortedReport trong ví dụ là một báo cáo dễ đọc cho dữ liệu Integer/String cụ thể, không được giới thiệu như một định dạng mã hóa chuẩn dùng chung. Không lấy phép nối `key + "=" + value` làm một định dạng duy nhất cho mọi dữ liệu có thể chứa dấu phân cách.

### Chi phí và trách nhiệm

| Yêu cầu chính | Hướng xử lý |
| --- | --- |
| Tra theo khóa, kết quả không phụ thuộc thứ tự | HashMap và thuật toán không dựa vào vị trí duyệt |
| Duyệt theo thứ tự chèn | LinkedHashMap mặc định, nhận dữ liệu theo thứ tự cần giữ |
| Duyệt theo khóa | TreeMap với thứ tự tự nhiên hoặc comparator phù hợp |
| Chỉ một số lần xuất cần có thứ tự | Giữ cấu trúc tra cứu, sắp bản kết quả lúc xuất |
| Cần một lịch ưu tiên khác với thứ tự chèn và thứ tự khóa | Lưu thông tin ưu tiên và dùng cấu trúc/thuật toán thể hiện quy tắc đó |

Các lựa chọn có chi phí bộ nhớ và thao tác khác nhau. LinkedHashMap giữ thêm quan hệ thứ tự; TreeMap thực hiện các thao tác theo cấu trúc cây; sắp một bản báo cáo cần công việc tạo kết quả và so sánh. Đổi loại Map nên xuất phát từ yêu cầu, không chỉ để làm một phép kiểm thử chuỗi tình cờ vượt qua.

### Thứ tự không thay thế các bảo đảm khác

Có một thứ tự rõ không tự làm dữ liệu an toàn luồng. Một iterator phát hiện thay đổi không tạo bản chụp nhất quán. Một Map chỉ đọc không tự là bản sao độc lập.

Trong bài, mọi thao tác diễn ra tuần tự và các nguồn được giữ ổn định trong mỗi lượt duyệt. Nếu ứng dụng có cập nhật đồng thời, cần cơ chế đọc/cập nhật phù hợp ngoài quyết định về thứ tự.

## 11. Bài tập và lời giải

### Bài 1 — Cùng thứ tự qua nhiều lần chạy

Bạn thêm 3, 1, 2 vào HashMap và luôn quan sát [1, 2, 3] trên máy hiện tại. Có thể ghi đặc tả hàm trả khóa tăng dần mà không thêm bước xử lý nào không?

**Lời giải:** không. Quan sát có thể phù hợp với cách phân bucket hiện tại nhưng HashMap không có hợp đồng sắp tăng. Muốn hàm cam kết tăng dần, dùng một cấu trúc sắp theo khóa hoặc sắp kết quả rõ ràng.

### Bài 2 — Không bảo đảm có phải luôn đổi không?

Một phép kiểm thử khẳng định hai lần tạo HashMap phải cho hai thứ tự khác nhau vì “HashMap không có thứ tự”. Phép kiểm thử sai ở đâu?

**Lời giải:** không bảo đảm thứ tự không có nghĩa bắt buộc ngẫu nhiên hoặc bắt buộc khác nhau. Hai lần có thể giống nhau. Điều đúng là thuật toán không được cần một thứ tự cụ thể chưa được hợp đồng cung cấp.

### Bài 3 — Map bằng nhau, List khác nhau

Hai Map có cùng cặp dữ liệu nhưng List tạo từ keySet khác thứ tự. Map.equals trả true, List.equals trả false. Có mâu thuẫn không?

**Lời giải:** không. Map so cùng ánh xạ; List so các phần tử theo vị trí. Việc chuyển sang List đã đưa một quan sát thứ tự vào đối tượng đang kiểm tra.

### Bài 4 — Sao chép sang LinkedHashMap

Dữ liệu được chèn vào HashMap theo A, B, C, nhưng một lần duyệt thấy C, A, B. Sao chép sang LinkedHashMap có khôi phục A, B, C không?

**Lời giải:** không. Map mới nhận theo thứ tự đang duyệt được là C, A, B trong lần đó và giữ lịch chèn của chính nó. Muốn giữ A, B, C phải lưu thứ tự ngay từ nguồn hoặc dùng cấu trúc phù hợp từ đầu.

### Bài 5 — Put lại và xóa rồi thêm

LinkedHashMap mặc định đang có A, B, C. Put lại B với giá trị mới cho thứ tự gì? Sau đó xóa A rồi thêm lại A thì sao?

**Lời giải:** put lại B giữ A, B, C. Xóa rồi thêm A cho B, C, A. Đây là hợp đồng của chế độ thứ tự chèn đã chọn, không phải suy luận từ HashMap.

### Bài 6 — forEachOrdered có sắp không?

Có thể thay vòng for-each trên HashMap bằng stream.forEachOrdered để bảo đảm in theo thứ tự chèn không?

**Lời giải:** không. forEachOrdered không tự tạo lịch sử chèn hoặc sắp khóa cho nguồn thiếu bảo đảm đó. Chọn nguồn giữ thứ tự cần có, hoặc tạo quy tắc như sorted trước khi xuất.

### Bài 7 — Chỉ sắp theo điểm

Một báo cáo lấy mục từ HashMap rồi sắp giảm theo điểm. Hai mục cùng điểm nhưng không có tiêu chí phụ. Đã bảo đảm toàn bộ đầu ra luôn có cùng thứ tự chưa?

**Lời giải:** chưa. Thứ tự tương đối của hai mục đồng điểm vẫn có thể dựa vào một thứ tự đầu vào không được bảo đảm. Nếu cần tái lập toàn bộ, thêm tiêu chí phụ phù hợp, chẳng hạn mã tăng.

### Bài 8 — Kiểm thử đúng yêu cầu

Một hàm chỉ được yêu cầu trả đúng Map mã–tên. Phép kiểm thử hiện so toString với một chuỗi viết theo thứ tự chèn. Sửa thế nào? Nếu yêu cầu thật sự là trả một báo cáo theo tên thì sao?

**Lời giải:** trường hợp đầu so Map trực tiếp với các ánh xạ mong đợi, không yêu cầu thứ tự thừa. Trường hợp thứ hai cần thể hiện việc sắp theo tên trong mã tạo báo cáo, quy định cách xử lý đồng tên nếu cần, rồi kiểm tra List hoặc văn bản đầu ra theo đúng thứ tự đó.

Một thứ tự quan sát là dữ liệu thực nghiệm để giải thích triển khai. Một thứ tự dùng làm yêu cầu phải có nguồn bảo đảm rõ: hợp đồng của cấu trúc hoặc một bước xử lý được thiết kế cho mục tiêu đó.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-map-008">Ước lượng kích thước ban đầu và chi phí duyệt theo capacity</a>

<details>
<summary>Xem chi tiết</summary>

# Ước lượng kích thước ban đầu và chi phí duyệt theo capacity

## 1. Bài toán: dành chỗ bao nhiêu cho một HashMap?

Một chương trình đọc dữ liệu rồi tạo HashMap để tra cứu theo mã. Nếu dự kiến có 1.000 khóa khác nhau, có thể viết new HashMap<>(1000). Câu lệnh này hợp lệ, nhưng ý nghĩa của số 1.000 dễ bị hiểu sai: đó là tham số dung lượng ban đầu, không phải lời cam kết chứa đúng 1.000 ánh xạ mà không cần tăng bảng.

Chọn quá nhỏ có thể làm map tăng bảng nhiều lần trong giai đoạn nạp. Chọn quá lớn lại dành nhiều chỗ cho bucket rỗng, tăng bộ nhớ và có thể làm việc duyệt toàn map tốn công.

Mục tiêu là **chọn dung lượng phù hợp với số ánh xạ dự kiến và cách dùng map**. Không phải tìm một con số càng lớn càng tốt.

Bài này dùng Java 17 làm mốc cho các chương trình chính. Những con số làm tròn theo lũy thừa hai và đường tăng bảng được giải thích theo cài đặt OpenJDK 17u; không coi chúng là API đọc cấu trúc nội bộ.

## 2. Phân biệt size, capacity, load factor và threshold

| Tên | Ý nghĩa | Ví dụ trong mô hình thông thường |
| --- | --- | --- |
| size | Số ánh xạ khóa–giá trị đang có | 1.000 khóa khác nhau tạo size bằng 1.000 |
| capacity | Số bucket của bảng băm đã cấp phát | 2.048 bucket có thể chứa 1.000 ánh xạ |
| initialCapacity | Tham số yêu cầu dung lượng khi tạo map | Truyền 1.334 vào constructor |
| load factor | Hệ số tải dùng để quyết định khi nào tăng bảng | Mặc định 0,75 |
| threshold | Ngưỡng kích thước gắn với bảng hiện tại trong đường xử lý thông thường | 2.048 × 0,75 = 1.536 |

Một bucket có thể chứa nhiều ánh xạ, nên capacity không phải số ánh xạ tối đa. Map cũng không phải mảng có một ô cho mỗi khóa.

### 2.1. Size là điều quan sát được qua API

Gọi size() để biết số ánh xạ hiện có. Ghi lại một giá trị mới cho khóa đã tồn tại thường không làm tăng size; thêm khóa khác mới tăng số ánh xạ.

Số lượt gọi put có thể lớn hơn nhiều so với size cuối cùng.

### 2.2. Capacity không có getter công khai của HashMap

Không có phương thức HashMap.capacity() để mã ứng dụng hỏi số bucket. Những chương trình bên dưới không dùng reflection hoặc mở trường private.

Ta có thể dùng mô hình để lập kế hoạch dung lượng, rồi kiểm tra tính đúng của dữ liệu qua size/get/entrySet. Mô hình không phải kết quả đo trực tiếp bảng của một đối tượng.

### 2.3. Constructor chưa có nghĩa đã nạp dữ liệu

Một HashMap mới vẫn rỗng dù truyền initialCapacity lớn. Trong OpenJDK 17u, bảng được khởi tạo khi cần dùng; constructor với tham số chủ yếu ghi lại cấu hình cho lần cấp phát sau.

Điều này giải thích vì sao chỉ đo thời gian chạy constructor là chưa đo chi phí dành bảng cho dữ liệu. Lần thêm đầu tiên có thể phải trả chi phí cấp phát đó.

Tham số initialCapacity cũng không đặt giới hạn cứng. Nếu tiếp tục thêm, map có thể tăng bảng; nó không tự từ chối khóa thứ N chỉ vì constructor nhận N.

## 3. Ước lượng N từ số khóa cùng tồn tại

Trước khi áp dụng công thức, hãy xác định N là gì: **số ánh xạ tối đa hợp lý mà map sẽ cùng giữ trong giai đoạn đang xét**.

| Tình huống | Đại lượng cần ước lượng |
| --- | --- |
| Lập chỉ mục người dùng theo userId | Số userId khác nhau được giữ |
| Đếm tần suất từ | Số từ khác nhau, không phải tổng số từ đọc |
| Ghi đè trạng thái theo deviceId | Số deviceId cùng có trong map |
| Gộp hai map | Số khóa trong hợp của hai tập khóa |
| Giữ dữ liệu trong một cửa sổ thời gian | Số khóa cùng tồn tại ở đỉnh của cửa sổ |

Ví dụ, đọc một triệu bản ghi nhưng chỉ cập nhật trạng thái của 500 thiết bị thì N liên quan tới 500 thiết bị. Đặt dung lượng theo một triệu lượt ghi có thể lãng phí rất nhiều.

Ngược lại, biết size hiện tại là 1.000 chưa đủ nếu chương trình còn nạp thêm 9.000 khóa mới. Cần ước lượng giai đoạn tăng trưởng phía trước.

Với hai map có size a và b, a + b chỉ là chặn trên khi gộp, vì khóa trùng sẽ cập nhật ánh xạ. Nếu biết mức chồng lấp, có thể ước lượng sát hơn.

Không nhất thiết phải quét dữ liệu hai lần chỉ để biết N chính xác. Chi phí đọc trước hoặc tạo thêm một Set để đếm khóa cũng cần được tính. Khi chưa biết quy mô đáng tin cậy, dùng cấu hình mặc định và để tăng trưởng có thể là lựa chọn hợp lý.

## 4. Từ số ánh xạ dự kiến tới yêu cầu dung lượng

### 4.1. Bắt đầu từ ngưỡng tải

Trong mô hình dung lượng thông thường, đặt:

- N: số ánh xạ dự kiến.
- C: số bucket.
- f: load factor.
- T: ngưỡng tăng theo tải.

Ta dùng gần đúng có kiểm soát:

T = floor(C × f)

Muốn N chưa vượt T, chọn C sao cho:

C ≥ ceil(N / f)

Với f = 0,75 và N = 1.000:

N / f = 1.333,333...

Yêu cầu dung lượng tính ra là 1.334, rồi cài đặt có thể làm tròn tiếp theo quy tắc bảng của nó.

Đây là cách **tránh tăng bảng do vượt ngưỡng tải trong phạm vi đã dự kiến**, không phải bảo đảm mọi đường nội bộ đều không thể tăng bảng. Ví dụ, trong OpenJDK, xử lý một nhóm va chạm quá đông khi bảng còn nhỏ có thể dẫn tới tăng bảng theo đường riêng.

### 4.2. Tại sao new HashMap<>(1000) khác ước lượng 1.000 ánh xạ?

Trong mô hình OpenJDK 17u với tải mặc định:

1. Yêu cầu dung lượng 1.000 được làm tròn thành bảng 1.024 bucket khi cấp phát.
2. Ngưỡng tải thông thường là 1.024 × 0,75 = 768.
3. Khi đường put thêm khóa khác làm size tăng lên 769, nó đã vượt ngưỡng ấy.
4. Bảng có thể tăng lên 2.048 bucket trước khi nạp đủ 1.000 khóa.

Nếu thay bằng yêu cầu 1.334, bảng ban đầu theo mô hình là 2.048 bucket, có ngưỡng 1.536. Vì thế 1.000 ánh xạ nằm trong khoảng tải dự kiến.

Hai cách có thể đi tới cùng dung lượng cuối. Khác biệt cần quan tâm là có đi qua một đợt tăng và chuyển dữ liệu giữa chừng hay không.

### 4.3. Hiểu đúng dấu bằng tại ngưỡng

Trong đường putVal của nguồn OpenJDK 17u được đối chiếu, thêm một khóa mới rồi kiểm tra size > threshold. Với bảng 16 và ngưỡng 12:

- Size bằng 12 chưa vượt ngưỡng tải.
- Thêm khóa khác làm size bằng 13 thì vượt.
- Thay giá trị của một khóa có sẵn không biến 12 khóa thành 13 khóa.

Vì vậy không tự cộng thêm 1 trong mọi công thức chỉ vì sợ trường hợp bằng ngưỡng. Với N = 12, ceil(12 / 0,75) bằng 16. Tính thêm một bucket thành 17 rồi làm tròn lũy thừa hai có thể đẩy bảng tới 32.

Tuy vậy, từng API xây dựng hoặc đường xử lý nội bộ có thể chọn cách dự phòng riêng. Khi dùng API công khai, ưu tiên đúng ý nghĩa tham số thay vì buộc nó phải đạt một dung lượng bí mật chính xác.

## 5. Lũy thừa hai làm ước lượng thay đổi theo nấc

Trong cài đặt OpenJDK 17u, bảng đã cấp phát có độ dài là lũy thừa hai. Vì vậy mức dành chỗ không tăng mượt theo từng ánh xạ.

Bảng dưới là **phép tính theo mô hình tải mặc định**, không phải kết quả gọi một getter của HashMap:

| N dự kiến | ceil(N / 0,75) | C sau làm tròn theo mô hình | floor(C × 0,75) |
| --- | --- | --- | --- |
| 1 | 2 | 2 | 1 |
| 12 | 16 | 16 | 12 |
| 13 | 18 | 32 | 24 |
| 768 | 1.024 | 1.024 | 768 |
| 769 | 1.026 | 2.048 | 1.536 |
| 1.000 | 1.334 | 2.048 | 1.536 |
| 10.000 | 13.334 | 16.384 | 12.288 |

Tăng dự kiến từ 768 lên 769 khóa có thể đẩy lựa chọn bảng từ 1.024 lên 2.048. Khoảng trống sau đó không nhất thiết là sai; nó đến từ bước làm tròn.

Điều này cũng giải thích vì sao hai giá trị initialCapacity khác nhau đôi khi dẫn tới cùng lựa chọn bảng. Tối ưu tham số từng đơn vị mà không xét nấc dung lượng có thể không thay đổi gì.

Các giá trị sát giới hạn tối đa của cài đặt có xử lý đặc biệt. Không kéo dài bảng tính thông thường đến mọi số int rồi kết luận đó là hành vi chính xác của HashMap.

## 6. Bài toán thực tế: 20.000 sự kiện nhưng chỉ giữ 180 khóa

Giả sử bộ tổng hợp xử lý 20.000 sự kiện trong một ca làm việc. Mỗi sự kiện cập nhật một bộ đếm theo mã thiết bị; các thiết bị cũ được loại khỏi Map khi không còn hoạt động. Đã có 240 thiết bị khác nhau xuất hiện trong cả ca, nhưng quan sát cho thấy **tối đa 180 thiết bị cùng tồn tại** trong Map.

Để ước lượng dung lượng, dùng đỉnh số **khóa đồng thời** `N = 180`, không dùng 20.000 lần gọi hay 240 mã từng xuất hiện. Với load factor mặc định 0.75:

~~~text
ceil(180 / 0.75) = 240
tableSizeFor(240) = 256       (mô hình làm tròn OpenJDK 17)
floor(256 × 0.75) = 192      (ngưỡng tải thông thường)
~~~

Vì `180 ≤ 192`, chọn yêu cầu dung lượng ban đầu khoảng 240 giúp bảng mục tiêu 256 bucket có đủ khoảng trống theo ngưỡng tải trong kịch bản này. Phép tính không chứng minh Map sẽ không bao giờ resize: bộ dữ liệu, va chạm hoặc đường triển khai cụ thể vẫn phải xét riêng. Constructor `new HashMap<>(240)` nhận **yêu cầu dung lượng**, không tự chèn 240 cặp.

| Cách chọn đầu vào | Điều xảy ra trong mô hình OpenJDK 17 |
| --- | --- |
| Dùng 20.000 sự kiện làm số ánh xạ | Ước lượng thừa vì nhiều sự kiện cập nhật cùng khóa |
| Dùng 240 khóa từng xuất hiện | Có thể thừa nếu không bao giờ cùng tồn tại |
| Dùng đỉnh 180 khóa cùng tồn tại | Phản ánh mức tải thực tế cần dự phòng |
| Dùng mặc định và không cấp trước | Đúng về ánh xạ; có thể phát sinh resize khi nạp nhiều khóa |

Ví dụ runnable ở mục 10 tiếp tục khai thác mặt còn lại của lựa chọn: khi Map trở nên rất thưa, chi phí duyệt có thể chịu ảnh hưởng của bảng đã cấp lớn. Chương trình đầy đủ tính dung lượng theo hệ số tải và quan sát các lần resize được trình bày trong bài về capacity/load factor.

## 7. Resize tốn gì, dù put thường nhanh?

Khi tăng bảng, cài đặt phải cấp phát một bảng mới và tổ chức lại việc đặt các node vào bucket. Công việc này liên quan tới bảng và những ánh xạ đang có, nên một lần put kích hoạt tăng bảng có thể tốn nhiều hơn một lần put thông thường.

Trong đường resize của OpenJDK 17u, các node giữ thông tin hash đã tính. Không nên giải thích tăng bảng thành “luôn gọi lại hashCode của mọi khóa từ đầu”; tổ chức lại bucket và gọi lại hàm người dùng là hai việc khác nhau.

### 7.1. Chi phí phân bổ theo nhiều lần thêm

Bảng tăng theo cấp số nhân trong miền thông thường. Khi nhìn cả chuỗi nhiều lần thêm, tổng công tăng trưởng có thể được phân bổ cho nhiều thao tác. Đây là lý do tăng động vẫn hữu ích.

Nhưng chi phí phân bổ trung bình không có nghĩa từng lệnh put đều có độ trễ giống nhau. Một chương trình nhạy với đột biến độ trễ có thể quan tâm tới thời điểm cấp phát và chuyển bảng.

Ước lượng đúng từ đầu giúp giảm các đợt tăng không cần thiết. Nó cũng dồn phần cấp phát bảng lớn hơn về giai đoạn đầu, nên vẫn có đánh đổi.

### 7.2. Bộ nhớ không chỉ gồm bucket rỗng

Một map còn có đối tượng map, bảng tham chiếu, các node, khóa, giá trị và những cấu trúc phụ có thể phát sinh. Không lấy capacity nhân một số byte tùy đoán rồi gọi đó là tổng bộ nhớ của map.

Khi tăng bảng hoặc tạo bản sao để thu gọn, hai cấu trúc có thể cùng tồn tại trong một khoảng thời gian. Ước lượng bộ nhớ đỉnh cũng có giá trị, đặc biệt với dữ liệu lớn.

Kích thước đối tượng phụ thuộc JVM và cấu hình. Nếu cần con số byte, phải đo trong môi trường liên quan thay vì biến một con số học được ở máy khác thành hằng số chung.

## 8. Vì sao duyệt HashMap phụ thuộc capacity + size?

### 8.1. Tìm một khóa khác duyệt tất cả

Get dùng hash để đi tới khu vực có thể chứa khóa. Với phân bố phù hợp, thao tác cơ bản có hiệu năng kỳ vọng tốt.

Duyệt toàn bộ entrySet, keySet hoặc values lại phải tìm những bucket có dữ liệu và đi qua các ánh xạ bên trong. Bucket rỗng cũng có thể phải được kiểm tra trong quá trình quét bảng.

Tài liệu [HashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html) nêu thời gian duyệt collection view tỉ lệ với capacity cộng size.

Mô hình học tập:

Chi phí duyệt toàn map ≈ chi phí quét C bucket + chi phí thăm N ánh xạ

Viết theo ký hiệu độ phức tạp: O(C + N).

Không đọc công thức này thành “mất đúng C + N nano giây” hay “mỗi bucket tốn đúng bằng mỗi entry”. Đây là mô hình số lượng công việc, không phải đơn vị thời gian.

### 8.2. Cùng 1.000 ánh xạ nhưng bảng khác nhau

Giả sử có hai trạng thái bảng:

| Trạng thái | C | N | C + N trong mô hình |
| --- | --- | --- | --- |
| Dành chỗ vừa mức dự kiến | 2.048 | 1.000 | 3.048 |
| Dành chỗ rất rộng | 1.048.576 | 1.000 | 1.049.576 |

Cả hai có cùng số entry để xử lý, nhưng trạng thái thứ hai có rất nhiều bucket rỗng. Nếu liên tục xuất báo cáo bằng cách duyệt toàn bộ map, khoảng trống đó có thể trở thành chi phí lặp lại.

Không suy ra tỉ lệ thời gian thực từ tỉ lệ hai tổng trên. Bộ nhớ đệm CPU, bố trí đối tượng, công việc trong thân vòng lặp và JVM đều ảnh hưởng phép đo.

### 8.3. Khi nào thường viết gọn là O(N)?

Nếu trong giai đoạn đang xét C luôn cùng bậc với N, thì C + N cũng cùng bậc với N. Lúc đó viết O(N) có thể là cách rút gọn phù hợp với giả định.

Nhưng C không tự luôn bám sát N. Nó có thể rất lớn vì cấu hình ban đầu hoặc vì map từng tăng mạnh rồi bị xóa nhiều dữ liệu. Bỏ C trong những tình huống đó làm mất điều cần giải thích.

### 8.4. Phạm vi của một lượt duyệt

Công thức đang nói về lượt duyệt đầy đủ, với công việc tại mỗi ánh xạ được xem riêng. Nếu thân vòng lặp ghi mạng, xử lý chuỗi dài hoặc làm phép toán đắt, chi phí ấy có thể lớn hơn phần duyệt cấu trúc.

Nếu chỉ lấy một vài phần tử rồi dừng, lượng quét phụ thuộc vị trí bucket có dữ liệu. Không xem O(C + N) như số bước chính xác cho mọi tiền tố của iterator.

Map hoàn toàn rỗng cũng có đường xử lý nhanh trong cài đặt được đối chiếu. Không khẳng định mọi iterator rỗng đều phải quét hết bảng cũ chỉ vì công thức tổng quát có chữ C.

Duyệt song song cũng không tự xóa chi phí bucket rỗng. Việc chia và xử lý dữ liệu là một bài toán khác cần đo theo khối lượng thực tế.

## 9. Xóa dữ liệu có tự làm bảng nhỏ lại không?

### 9.1. Size giảm chưa nói capacity giảm

Trong cài đặt OpenJDK 17u được đối chiếu, remove không tự thu nhỏ bảng băm chỉ vì tổng số ánh xạ giảm. Clear bỏ các ánh xạ nhưng giữ đối tượng bảng để có thể dùng lại.

Một map từng chứa rất nhiều khóa có thể trở nên thưa sau khi xóa. Khi còn một ít ánh xạ và tiếp tục duyệt toàn bộ, việc nhìn size nhỏ rồi cho rằng lượt duyệt chắc rẻ là thiếu dữ kiện.

Việc đổi biểu diễn bên trong một bucket và thu nhỏ toàn bộ bảng là hai việc khác nhau. Không dùng một thay đổi của nhóm va chạm làm bằng chứng bảng đã có ít bucket hơn.

### 9.2. Giữ lại hay tạo lại phụ thuộc vòng đời

Nếu ngay sau clear sẽ nạp lại lượng dữ liệu lớn tương tự, giữ bảng cũ có thể tránh cấp phát lại.

Nếu lượng dữ liệu đã giảm lâu dài và map tiếp tục được duyệt nhiều, tạo một HashMap mới từ các ánh xạ còn lại có thể giúp đưa dung lượng về mức hợp với quy mô mới.

HashMap không có phương thức public trimToSize tương tự một số cấu trúc khác. Một cách tạo cấu trúc mới là:

```java
Map<K, V> rebuilt = new HashMap<>(oldMap);
```

Đây là đoạn minh họa trong ngữ cảnh đã có kiểu K, V và biến oldMap, không phải chương trình độc lập.

Constructor sao chép giữ các ánh xạ theo hợp đồng công khai. Không yêu cầu nó phải chọn chính xác dung lượng tối thiểu theo bảng tính ở trên; quy tắc dự phòng có thể khác giữa đường xây dựng và phiên bản.

### 9.3. Sao chép có chi phí và thay đổi định danh đối tượng

Để xây bản mới, vẫn phải đọc ánh xạ từ bản cũ. Nếu bản cũ rất thưa, chính lượt duyệt phục vụ sao chép cũng có thể tốn theo bảng cũ.

Trong lúc sao chép, cấu trúc cũ và mới cùng tồn tại. Khóa và giá trị là các đối tượng được tham chiếu lại, không tự trở thành bản sao sâu.

Gán một biến sang bản mới không chuyển mọi người dùng khác sang cùng đối tượng ấy. Những nơi đang giữ map cũ, keySet/values/entrySet của nó hoặc iterator cũ vẫn gắn với bản cũ. Cần xác định rõ ai sở hữu map trước khi thay cấu trúc.

Bảng cũ chỉ có cơ hội được thu gom khi không còn đường tham chiếu giữ cấu trúc đó; không có cam kết thu gom ngay tại câu lệnh gán.

## 10. Ví dụ Java 17: map thưa và bản sao mới

Chương trình chủ động yêu cầu dung lượng ban đầu lớn, nạp 1.000 khóa rồi bỏ 990 khóa. Sau đó nó tạo một map mới từ 10 ánh xạ còn lại.

Mục đích của stdout là kiểm tra **ánh xạ và quan hệ giữa hai đối tượng**, không đo thời gian hay đọc capacity bí mật.

Lưu thành **SparseHashMapDemo.java**:

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SparseHashMapDemo {
    static long checksum(Map<Integer, Integer> map) {
        long sum = 0;
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            sum += entry.getValue();
        }
        return sum;
    }

    public static void main(String[] args) {
        Map<Integer, Integer> original = new HashMap<>(1 << 18);
        for (int key = 0; key < 1000; key++) {
            original.put(key, key);
        }
        System.out.println("before removal size: " + original.size());

        for (int key = 0; key < 990; key++) {
            original.remove(key);
        }
        System.out.println("after removal size: " + original.size());
        System.out.println("remaining checksum: " + checksum(original));

        Set<Integer> oldKeysView = original.keySet();
        Map<Integer, Integer> rebuilt = new HashMap<>(original);
        System.out.println("same mappings after copy: " + original.equals(rebuilt));
        System.out.println("rebuilt size: " + rebuilt.size());

        original.clear();
        System.out.println("old view size after clear: " + oldKeysView.size());
        System.out.println("rebuilt size after original clear: " + rebuilt.size());
        System.out.println("rebuilt checksum: " + checksum(rebuilt));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SparseHashMapDemo.java
java SparseHashMapDemo
```

Stdout trên môi trường đã nêu:

```text
before removal size: 1000
after removal size: 10
remaining checksum: 9945
same mappings after copy: true
rebuilt size: 10
old view size after clear: 0
rebuilt size after original clear: 10
rebuilt checksum: 9945
```

### 10.1. Đọc kết quả

Các khóa còn lại là 990 tới 999, tổng giá trị 9.945. Bản sao giữ cùng các ánh xạ nên equals trả true và tổng vẫn đúng.

Biến oldKeysView là view của map gốc. Khi clear map gốc, view ấy có size bằng 0. Map mới vẫn giữ 10 ánh xạ vì cấu trúc chứa ánh xạ của nó độc lập với map gốc.

Việc clear map cũ trong ví dụ giúp quan sát quan hệ đó; đây không phải yêu cầu phải clear trước mỗi lần thay map. Nếu nơi khác còn cần dữ liệu cũ, clear sẽ có tác dụng thật lên nơi đó.

### 10.2. Điều stdout không chứng minh

Nó không chứng minh số bucket chính xác của hai map hoặc mức tăng tốc bao nhiêu phần trăm.

Lý do map có thể thưa sau xóa và lý do constructor có thể chọn bảng mới phù hợp hơn được giải thích bằng mô hình cùng mã nguồn cài đặt. Kết quả chạy kiểm tra tính đúng của các ánh xạ.

Một ví dụ in ra hai thời gian nano giây rồi khẳng định bản mới luôn nhanh hơn sẽ không đủ đáng tin. Tính đúng và phép đo hiệu năng cần có mục tiêu kiểm tra riêng.

## 11. Chọn dung lượng theo công việc của map

| Cách dùng chính | Điều nên cân nhắc |
| --- | --- |
| Nạp một lần, biết khá rõ số khóa | Ước lượng dung lượng để giảm tăng bảng trong giai đoạn nạp |
| Quy mô nhỏ hoặc chưa dự đoán được | Cấu hình mặc định thường là điểm khởi đầu hợp lý |
| Duyệt toàn map nhiều lần | Tránh dành dư quá mức; xem cả capacity và size |
| Thường clear rồi nạp lại cùng quy mô | Tái sử dụng bảng có thể có lợi |
| Tăng rất lớn một lần rồi giảm lâu dài | Cân nhắc tạo cấu trúc mới nếu lợi ích bù chi phí sao chép |
| Cần thứ tự duyệt ổn định | Chọn cấu trúc có hợp đồng thứ tự phù hợp, không dựa vào capacity |

### 11.1. Không giảm load factor một cách máy móc

Giảm f làm công thức N/f tăng. Với cùng N, cài đặt có thể phải chọn bảng lớn hơn. Điều đó có thể giảm mức dồn trung bình nhưng làm tăng bộ nhớ bảng và công quét khi duyệt.

Tăng f cũng không phải tiết kiệm miễn phí: nhiều ánh xạ hơn trên cùng số bucket có thể làm tra cứu tốn hơn. Hàm hash và đặc điểm dữ liệu vẫn quan trọng.

Giữ mặc định 0,75 thường giúp bắt đầu với một đánh đổi đã có sẵn. Chỉ đổi khi có yêu cầu và bằng chứng liên quan tới khối lượng công việc.

### 11.2. Một đối chiếu hữu ích với LinkedHashMap

Theo [LinkedHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html), duyệt collection view đi theo các liên kết của entry và có chi phí tỉ lệ với size, không phụ thuộc capacity theo cách quét bảng của HashMap.

Đó là một khác biệt đáng xem xét khi cần thứ tự duyệt hoặc thường duyệt map thưa. Tuy nhiên LinkedHashMap có thêm liên kết và chi phí duy trì chúng; không suy ra nó luôn dùng ít bộ nhớ hơn hoặc nhanh hơn mọi thao tác.

Việc lựa chọn cấu trúc phải xét cả hợp đồng cần có và công việc thực hiện, không chỉ một dòng độ phức tạp.

## 12. Java 19+: diễn đạt trực tiếp số ánh xạ dự kiến

Từ Java 19, HashMap có factory newHashMap(int numMappings). Tham số ở đây là **số ánh xạ dự kiến**, khác ý nghĩa initialCapacity của constructor.

Đoạn khai báo minh họa trong ngữ cảnh đã import Map và HashMap:

```java
Map<String, Integer> counts = HashMap.newHashMap(1000);
```

API dùng load factor mặc định và chọn dung lượng ban đầu phù hợp nói chung để nạp lượng ánh xạ dự kiến. Không cần tự truyền kết quả N/0,75 vào factory thêm lần nữa.

Đối chiếu mục newHashMap, Since 19 trong [HashMap Java 19](https://docs.oracle.com/en/java/javase/19/docs/api/java.base/java/util/HashMap.html#newHashMap(int)).

Đoạn này **không biên dịch với --release 17**, kể cả máy đang chạy JDK mới hơn. Hai chương trình đầy đủ phía trên chỉ dùng API có trong Java 17.

Factory vẫn trả về một map rỗng có thể tăng trưởng. Số 1.000 không tạo giới hạn cứng hay tự thu nhỏ map sau các lần xóa.

## 13. Đánh giá hiệu năng mà không nhầm mục tiêu

Trước hết kiểm tra các cấu hình chứa cùng ánh xạ, có cùng công việc trong thân vòng lặp và cho cùng kết quả. Nếu không, chênh lệch thời gian có thể chỉ là do xử lý lượng dữ liệu khác nhau.

Khi đo dung lượng, tách những câu hỏi sau:

- Nạp dữ liệu ban đầu tốn bao nhiêu?
- Một lượt duyệt sau khi nạp tốn bao nhiêu?
- Xóa nhiều rồi tiếp tục duyệt có ảnh hưởng thế nào?
- Sao chép để tạo map mới tốn bao nhiêu và được dùng lại bao nhiêu lần?

Đừng đặt việc dựng map trong mỗi lượt đo nếu câu hỏi là chi phí chỉ duyệt một map đã dựng xong. Ngược lại, nếu muốn đánh giá toàn bộ giai đoạn nạp, không loại chi phí cấp phát khỏi phép đo rồi gọi đó là tổng chi phí.

Cần có giai đoạn làm nóng JVM, nhiều lượt đo, kết quả được sử dụng để tránh công việc bị tối ưu bỏ, và dữ liệu đại diện. Ghi lại JDK, cấu hình và phân bố khóa.

Nếu thân vòng lặp in từng dòng ra console thì phần in có thể che gần hết khác biệt cấu trúc. Những chương trình trong bài dùng stdout để chứng minh kết quả, không dùng thời gian in làm phép đo HashMap.

Không cần tăng dung lượng lên mức cực lớn trên máy chỉ để chứng minh O(C + N). Mô hình giải thích quan hệ; phép đo có kiểm soát dùng để đánh giá mức ảnh hưởng thực tế của ứng dụng.

## 14. Lỗi phổ biến và cách sửa

| Hiểu nhầm | Hệ quả | Cách sửa |
| --- | --- | --- |
| initialCapacity là số ánh xạ không cần tăng bảng | Nạp vượt ngưỡng tải sớm hơn dự tính | Xét số khóa và load factor |
| Capacity là giới hạn số khóa | Thiết kế nhầm cơ chế từ chối hoặc loại dữ liệu | Đặt chính sách giới hạn riêng nếu cần |
| Một triệu bản ghi nghĩa là một triệu entry | Dành dư nếu nhiều bản ghi có cùng khóa | Ước lượng số khóa khác nhau cùng giữ |
| Cứ cộng 1 sau N/f | Có thể nhảy sang nấc bảng lớn hơn không cần thiết | Làm tròn lên đúng ngữ cảnh và hiểu điều kiện ngưỡng |
| Nhân int rồi mới chuyển long | Giá trị đã tràn trước khi chuyển kiểu | Dùng toán hạng long trước phép nhân |
| Map có size nhỏ nên duyệt chắc rẻ | Bỏ qua bảng lớn còn giữ sau xóa | Xét vòng đời và capacity |
| clear đồng nghĩa giải phóng bảng ngay | Dự đoán sai bộ nhớ và khả năng tái dùng | Phân biệt bỏ ánh xạ, giữ bảng và thu gom rác |
| Mô hình in capacity là đã đo đối tượng | Kết luận quá mức từ phép tính | Ghi rõ mô hình và phép quan sát public |
| Tạo bản sao là miễn phí | Bỏ qua lượt duyệt cũ và bộ nhớ đỉnh | Tính chi phí sao chép và số lần sử dụng sau đó |
| newHashMap(1000) cần tự chia 0,75 thêm | Dành chỗ quá mức do điều chỉnh hai lần | Factory nhận số ánh xạ dự kiến |
| Vòng duyệt nhanh một lần chứng minh mọi trường hợp | Dựa vào phép đo nhiễu hoặc sai phạm vi | Đo với mục tiêu, môi trường và dữ liệu rõ ràng |

## 15. Bài tập tự kiểm tra có lời giải

### Bài 1: Nạp đúng ngưỡng

Theo mô hình OpenJDK 17u với tải mặc định, bảng 16 bucket có ngưỡng thông thường 12. Nạp 12 khóa khác nhau có vượt ngưỡng tải không? Thêm khóa khác thứ 13 thì sao?

**Lời giải:** 12 chưa lớn hơn 12. Khóa mới thứ 13 làm size vượt ngưỡng theo đường put đang xét. Kết luận chỉ nói về đường tăng theo tải; không loại bỏ những cơ chế nội bộ khác trong dữ liệu va chạm đặc biệt.

### Bài 2: Tính cho 2.000 ánh xạ

Dùng f = 0,75. Yêu cầu dung lượng tính ra là bao nhiêu? Theo mô hình lũy thừa hai, bảng nào đủ?

**Lời giải:** ceil(2.000 / 0,75) = 2.667. Làm tròn lên thành 4.096, ngưỡng thông thường là 3.072.

### Bài 3: Bốn lượt ghi, hai khóa

Dữ liệu khóa lần lượt là a, a, b, a. Nếu chỉ giữ giá trị mới nhất theo khóa, N cuối cùng là 4 hay 2?

**Lời giải:** 2 khóa khác nhau. Số bản ghi đầu vào không bằng số ánh xạ cuối. Nếu bài toán là đếm tần suất, map vẫn có hai ánh xạ, còn các giá trị đếm là 3 và 1.

### Bài 4: Hai map cùng size

Map A có C = 4.096, N = 2.000; map B có C = 1.048.576, N = 2.000. Có được nói một lượt duyệt của chúng chắc bằng nhau vì cùng 2.000 entry không?

**Lời giải:** không. Duyệt HashMap còn liên quan tới bảng bucket. Tuy nhiên từ mô hình này cũng chưa tính được tỉ lệ thời gian chạy chính xác.

### Bài 5: Vừa xóa một lượng lớn

Một map giảm từ hàng trăm nghìn khóa xuống vài trăm rồi được dùng để xuất báo cáo nhiều lần. Có thể chỉ dựa vào size để kết luận chi phí duyệt đã giảm tương ứng không?

**Lời giải:** không. Cài đặt được đối chiếu không tự thu nhỏ bảng khi remove. Có thể cân nhắc map mới phù hợp với quy mô còn lại, sau khi xét chi phí sao chép, bộ nhớ đỉnh và các tham chiếu tới map cũ.

### Bài 6: Clear trước khi nạp lại

Một tác vụ lặp đều clear rồi nạp lại khoảng cùng số khóa như trước. Luôn thay map sau clear có chắc tốt hơn không?

**Lời giải:** không. Giữ bảng có thể giảm cấp phát lại. Chọn theo vòng đời dữ liệu và phép đo phù hợp.

### Bài 7: Dùng helper sai load factor

Đã tính dung lượng theo f = 0,75 nhưng lại truyền vào constructor có f = 0,5. Có được giữ nguyên kết luận về ngưỡng không?

**Lời giải:** không. Ngưỡng phụ thuộc f. Nếu thay f, phải tính lại hoặc dùng cách tạo map phù hợp với cấu hình thật.

### Bài 8: Factory và phiên bản Java

Một đoạn gọi HashMap.newHashMap(1000) chạy trên máy có JDK 24. Có chắc biên dịch được với --release 17 không?

**Lời giải:** không. API đó bắt đầu từ Java 19. JDK dùng để chạy công cụ và mức API đích khi biên dịch là hai thông tin cần phân biệt.

## 16. Tài liệu đối chiếu

- [HashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html): ý nghĩa dung lượng, hệ số tải, constructor và chi phí duyệt.
- [HashMap.java OpenJDK 17u](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/HashMap.java): cách làm tròn, khởi tạo bảng, tăng bảng, iterator và clear trong cài đặt được phân tích.
- [HashMap Java 19 — newHashMap](https://docs.oracle.com/en/java/javase/19/docs/api/java.base/java/util/HashMap.html#newHashMap(int)): API nhận số ánh xạ dự kiến.
- [LinkedHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html): đối chiếu chi phí duyệt theo size.

Ước lượng dung lượng bắt đầu từ số khóa cùng tồn tại, rồi xét hệ số tải và cách sử dụng map. Giảm những lần tăng bảng không cần thiết có ích, nhưng dành chỗ quá rộng có thể chuyển chi phí sang bộ nhớ và các lượt duyệt về sau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-map-009">Lỗi khóa khả biến và rủi ro cập nhật đồng thời</a>

<details>
<summary>Xem chi tiết</summary>

# Lỗi khóa khả biến và rủi ro cập nhật đồng thời

## 1. Hai nguồn lỗi khác nhau cần được nhận diện

Một HashMap có thể trả kết quả bất ngờ dù chương trình chỉ có một luồng: đối tượng dùng làm khóa đã bị sửa những thông tin xác định nó là ai.

Một HashMap cũng có thể gặp lỗi khi nhiều luồng cùng truy cập và cập nhật mà không có cơ chế phối hợp phù hợp: thao tác đọc, sửa và duyệt không còn diễn ra theo giả định tuần tự.

Hai vấn đề không phải một:

| Vấn đề | Câu hỏi cần trả lời |
| --- | --- |
| Khóa khả biến | Dữ liệu tham gia equals/hashCode có còn ổn định khi khóa đang nằm trong Map không? |
| Cập nhật đồng thời | Các luồng có một hợp đồng truy cập bảo vệ được dữ liệu và toàn bộ thao tác nghiệp vụ không? |

Khóa bất biến không tự làm HashMap an toàn luồng. Một ConcurrentHashMap cũng không tự làm đối tượng khóa đang thay đổi trở nên hợp lệ cho tra cứu băm.

Bài học giải thích cơ chế, cung cấp thí nghiệm chạy được và chỉ ra những cách tổ chức dữ liệu phù hợp. Các ví dụ sai về khóa được ghi nhận như quan sát của một trạng thái đã vi phạm điều kiện sử dụng, không phải một hành vi mà mọi Map phải bảo đảm.

## 2. Khóa khả biến sai ở điểm nào?

HashMap định vị ánh xạ bằng mã băm từ khóa, rồi kiểm tra khóa để tìm đúng dữ liệu. Khi đưa một khóa vào, cấu trúc nội bộ lưu thông tin phục vụ định vị tại thời điểm đó.

Giả sử khóa có trường id; hashCode và equals đều dựa vào id:

~~~text
Lúc thêm:
key.id = 1
ánh xạ được bố trí theo mã của id 1.

Sau khi sửa:
key.id = 2
lần tra mới tính mã theo id 2.
~~~

Đối tượng khóa vẫn là cùng một đối tượng trong bộ nhớ, nhưng dữ liệu định vị mới không còn khớp với dữ liệu khi ánh xạ được tạo.

Điều này có thể dẫn tới tình huống quan sát được: get(key) không tìm thấy giá trị, size vẫn còn một ánh xạ, và duyệt khóa vẫn gặp chính đối tượng đó. Nó không phải bằng chứng HashMap “đánh mất tham chiếu”; vấn đề là đường tìm theo khóa đã không còn dùng cùng định danh.

Hợp đồng Map nêu hành vi không được xác định khi một đối tượng khóa bị thay đổi theo cách ảnh hưởng tới so bằng trong lúc đang được dùng làm khóa. Không thiết kế ứng dụng dựa vào một kết quả hỏng cụ thể của tình trạng này. [Tài liệu Map, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html).

### Cùng tham chiếu chưa chắc cứu được phép tìm

Một hiểu nhầm phổ biến là “tôi truyền đúng đối tượng đã put nên chắc chắn get được”. Trong cách tìm băm, còn có bước chọn vị trí và kiểm tra mã đã lưu trước khi cùng tham chiếu trở thành thông tin hữu ích.

Nếu khóa tạo mã khác sau khi bị sửa, đối tượng có thể nằm ở nơi mà lần tra mới không tìm tới, hoặc bị loại bởi mã băm không còn khớp. Vì vậy, đừng dùng == như một lý do cho phép thay đổi dữ liệu nhận diện khóa.

### Không phải mọi thay đổi của đối tượng khóa đều phá hợp đồng băm

Một đối tượng có thể có mã định danh bất biến và một nhãn mô tả có thể sửa. Nếu equals/hashCode chỉ dùng mã định danh ổn định, việc thay nhãn không nhất thiết làm hỏng tra cứu.

Tuy nhiên, nhãn vẫn là dữ liệu có thể chia sẻ giữa các luồng và cần một chính sách truy cập riêng nếu bị sửa đồng thời. Tính ổn định của nhận diện khóa và tính an toàn khi sửa mọi trường của đối tượng là hai điều khác nhau.

## 3. Chọn khóa ổn định và tránh bất biến nông

Với một tài khoản có id ổn định:

~~~java
record AccountKey(int id) {
}
~~~

Khóa gồm dữ liệu bất biến đơn giản, nên một đối tượng AccountKey mới có cùng id có thể được dùng để tìm ánh xạ cũ. Không cần giữ và dùng lại cùng một tham chiếu khóa.

Ngược lại, chỉ khai báo biến khóa bằng final chưa làm đối tượng được trỏ tới bất biến:

~~~java
final MutableKey key = new MutableKey(1);
key.changeId(2);
~~~

Final ở đây ngăn gán biến key sang đối tượng khác; nó không chặn lời gọi sửa đối tượng.

### Record chứa collection vẫn cần xem xét nội dung

Đoạn sau không tự đóng băng danh sách:

~~~java
record GroupKey(List<String> parts) {
}
~~~

Nếu một danh sách được truyền vào rồi bị sửa qua tham chiếu bên ngoài, nội dung tham gia equals/hashCode của record có thể đổi theo.

Một cách thiết kế với thành phần String trong ví dụ:

~~~java
record GroupKey(List<String> parts) {
    GroupKey {
        parts = List.copyOf(parts);
    }
}
~~~

Constructor lấy một bản sao cấu trúc danh sách không cho sửa. Do các phần tử String là bất biến, nội dung dùng để nhận diện khóa trong ví dụ được giữ ổn định trước việc bên ngoài sửa danh sách đầu vào.

List.copyOf không phải sao chép sâu mọi phần tử. Nếu phần tử là một đối tượng có thể sửa và equals/hashCode của GroupKey phụ thuộc nội dung đối tượng đó, còn phải quyết định cách bảo vệ thành phần ấy.

Chính sách null cũng cần rõ: List.copyOf trong ví dụ không nhận danh sách null hoặc phần tử null. Đó là một phần của hợp đồng tạo khóa đã chọn, không phải quy định chung cho mọi khóa Map.

### Cùng một khóa có thể được dùng ở nhiều Map

Sao chép các ánh xạ sang một Map khác thường vẫn dùng chung đối tượng khóa. Nếu khóa bị sửa sau đó, nhiều cấu trúc có thể cùng gặp vấn đề.

Đổi lớp Map hoặc tạo một bản sao nông không thay thế cho việc tạo một giá trị khóa ổn định. Cần quyết định dữ liệu nhận diện tại nơi xây khóa, thay vì hy vọng cấu trúc chứa nó tự bảo vệ.

## 4. HashMap không cung cấp cơ chế đồng bộ nội bộ

HashMap không được đồng bộ hóa sẵn. Khi nhiều luồng cùng truy cập và có cập nhật cấu trúc, cần đồng bộ từ bên ngoài theo một cơ chế phù hợp. [Tài liệu HashMap, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html).

Cập nhật cấu trúc gồm thêm hoặc xóa ánh xạ. Thay giá trị cho một khóa đang có không tăng size và thường không được gọi là thay đổi cấu trúc, nhưng điều đó **không** tự làm thao tác thay giá trị an toàn để nhiều luồng thực hiện thiếu phối hợp.

Có ba yêu cầu dễ bị trộn lẫn:

| Yêu cầu | Ý nghĩa |
| --- | --- |
| Trạng thái nội bộ nhất quán | Cấu trúc chứa ánh xạ được truy cập theo hợp đồng |
| Khả năng nhìn thấy cập nhật | Luồng đọc có quan hệ bộ nhớ phù hợp để nhìn thấy trạng thái được công bố |
| Tính nguyên tử của thao tác nghiệp vụ | Những bước cần đi liền nhau không bị một thao tác khác chen vào làm sai kết quả |

Một khóa String bất biến chỉ giúp phần nhận diện; nó không cung cấp cả ba điều trên cho HashMap.

Khai báo biến tham chiếu Map là volatile cũng không tự biến chuỗi get rồi put thành một thao tác nguyên tử. Volatile có quan hệ công bố/nhìn thấy theo mô hình bộ nhớ, không thay được một quy tắc cập nhật ghép nhiều bước. Cơ chế khóa và quan hệ happens-before cần được áp dụng đúng với dữ liệu chia sẻ. [Java Language Specification 17, chương Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html).

Không mô tả lỗi đồng thời bằng một kết quả duy nhất bắt buộc như “luôn ném ngoại lệ” hoặc “luôn mất đúng một cập nhật”. Khi thiếu hợp đồng truy cập, kết quả có thể phụ thuộc lịch thực thi. Một kiểm tra chạy qua được chưa chứng minh rằng cách truy cập đã đúng.

## 5. Khóa từng lời gọi chưa đủ cho thao tác ghép

Giả sử bộ đếm đang bằng 0. Hai tác vụ đều muốn tăng một:

~~~text
Tác vụ A đọc 0.
Tác vụ B đọc 0.
Tác vụ A ghi 1.
Tác vụ B ghi 1.
Kết quả cuối: 1, dù đã có hai yêu cầu tăng.
~~~

Lỗi này gọi là mất cập nhật: tác vụ sau ghi đè một kết quả được tính từ trạng thái cũ.

Nó vẫn có thể xảy ra khi mỗi get và mỗi put riêng lẻ đều được bảo vệ bằng khóa, nếu phần “đọc rồi tính rồi ghi” không nằm trong cùng một vùng được bảo vệ.

Ví dụ minh họa phạm vi khóa chưa đủ:

~~~java
int before;
synchronized (lock) {
    before = map.get("count");
}

// Một tác vụ khác có thể đọc hoặc cập nhật ở khoảng giữa.

synchronized (lock) {
    map.put("count", before + 1);
}
~~~

Các biến map và lock được giả sử đã có. Mã này bảo vệ từng truy cập Map bằng cùng lock, nhưng không bảo vệ quyết định dựa trên before.

Cách thể hiện toàn bộ phép tăng dưới một khóa:

~~~java
synchronized (lock) {
    map.put("count", map.get("count") + 1);
}
~~~

Điều kiện để cách này đúng là mọi bên liên quan đến dữ liệu đó tuân thủ cùng cơ chế khóa và các tiền điều kiện của phép tăng, như khóa count luôn có một số nguyên hợp lệ.

Nếu một bên dùng lock A, bên khác dùng lock B, hai vùng synchronized không tự ngăn nhau. Nếu một bên vẫn truy cập thẳng Map ngoài quy tắc, hợp đồng chung lại bị phá.

## 6. Chương trình Java 17+ có kết quả kiểm chứng rõ

Chương trình sau có ba nhóm:

1. Thay id của một khóa đang ở HashMap và ConcurrentHashMap, thực hiện tuần tự để quan sát lỗi nhận diện khóa.
2. Dùng khóa bất biến và sao chép danh sách thành phần để giữ khóa ổn định.
3. Hai tác vụ tăng bộ đếm bằng vùng khóa bị tách, vùng khóa đầy đủ và ConcurrentHashMap.compute.

Ở ví dụ mất cập nhật, mỗi truy cập HashMap vẫn nằm trong synchronized. CountDownLatch buộc hai tác vụ đọc xong trước khi tác vụ nào được ghi, nên kết quả 1 được tạo bởi lịch phối hợp có chủ đích. Đây là minh chứng phạm vi nguyên tử sai, không chạy một cuộc ghi không đồng bộ lên HashMap rồi chờ lỗi tình cờ.

Tệp `HashMapSafetyDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class HashMapSafetyDemo {
    static final class MutableKey {
        private int id;

        MutableKey(int id) {
            this.id = id;
        }

        void changeId(int id) {
            this.id = id;
        }

        @Override
        public int hashCode() {
            return id;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MutableKey key && id == key.id;
        }
    }

    record AccountKey(int id) {
    }

    record GroupKey(List<String> parts) {
        GroupKey {
            parts = List.copyOf(parts);
        }
    }

    static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Unexpected safe-case result");
        }
    }

    static void awaitBoth(Future<Void> first, Future<Void> second)
            throws Exception {
        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);
    }

    static int splitCriticalSections(ExecutorService pool) throws Exception {
        Map<String, Integer> map = new HashMap<>();
        map.put("count", 0);
        Object lock = new Object();
        CountDownLatch bothRead = new CountDownLatch(2);
        Callable<Void> task = () -> {
            int before;
            synchronized (lock) {
                before = map.get("count");
            }
            bothRead.countDown();
            if (!bothRead.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Both readers did not arrive");
            }
            synchronized (lock) {
                map.put("count", before + 1);
            }
            return null;
        };
        Future<Void> first = pool.submit(task);
        Future<Void> second = pool.submit(task);
        awaitBoth(first, second);
        synchronized (lock) {
            return map.get("count");
        }
    }

    static int wholeCriticalSection(ExecutorService pool) throws Exception {
        Map<String, Integer> map = new HashMap<>();
        map.put("count", 0);
        Object lock = new Object();
        Callable<Void> task = () -> {
            for (int i = 0; i < 1000; i++) {
                synchronized (lock) {
                    map.put("count", map.get("count") + 1);
                }
            }
            return null;
        };
        Future<Void> first = pool.submit(task);
        Future<Void> second = pool.submit(task);
        awaitBoth(first, second);
        synchronized (lock) {
            return map.get("count");
        }
    }

    static int atomicCompute(ExecutorService pool) throws Exception {
        Map<String, Integer> map = new ConcurrentHashMap<>();
        map.put("count", 0);
        Callable<Void> task = () -> {
            for (int i = 0; i < 1000; i++) {
                map.compute("count", (key, old) -> old + 1);
            }
            return null;
        };
        Future<Void> first = pool.submit(task);
        Future<Void> second = pool.submit(task);
        awaitBoth(first, second);
        return map.get("count");
    }

    public static void main(String[] args) throws Exception {
        MutableKey key = new MutableKey(1);
        Map<MutableKey, String> hash = new HashMap<>();
        Map<MutableKey, String> concurrent = new ConcurrentHashMap<>();
        hash.put(key, "value");
        concurrent.put(key, "value");
        System.out.println("before mutation hash=" + key.hashCode()
                + " hashMap=" + hash.get(key)
                + " concurrent=" + concurrent.get(key));

        key.changeId(2);
        System.out.println("observed after mutation hash=" + key.hashCode()
                + " hashMap=" + hash.get(key)
                + " concurrent=" + concurrent.get(key));
        System.out.println("observed sizes after mutation="
                + hash.size() + "," + concurrent.size());
        System.out.println("observed stored key same reference="
                + hash.keySet().stream().anyMatch(stored -> stored == key));

        Map<AccountKey, String> accounts = new HashMap<>();
        accounts.put(new AccountKey(7), "stable");
        String stable = accounts.get(new AccountKey(7));
        check(Objects.equals(stable, "stable"));
        System.out.println("immutable key lookup=" + stable);

        List<String> input = new ArrayList<>(List.of("team", "A"));
        GroupKey group = new GroupKey(input);
        Map<GroupKey, String> groups = new HashMap<>();
        groups.put(group, "group-value");
        input.set(1, "B");
        String groupValue = groups.get(new GroupKey(List.of("team", "A")));
        check(Objects.equals(groupValue, "group-value"));
        System.out.println("defensive key lookup=" + groupValue);
        System.out.println("defensive key parts=" + group.parts());
        try {
            group.parts().add("C");
            throw new AssertionError("Expected unmodifiable parts");
        } catch (UnsupportedOperationException exception) {
            System.out.println("parts modification=UnsupportedOperationException");
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            int split = splitCriticalSections(pool);
            int whole = wholeCriticalSection(pool);
            int atomic = atomicCompute(pool);
            check(split == 1);
            check(whole == 2000);
            check(atomic == 2000);
            System.out.println("split critical sections count=" + split
                    + " expected=2");
            System.out.println("whole critical section count=" + whole);
            System.out.println("concurrent compute count=" + atomic);
            System.out.println("safe-case checks=PASS");
        } finally {
            pool.shutdownNow();
        }
    }
}
~~~

Biên dịch và chạy:

~~~shell
javac --release 17 -encoding UTF-8 HashMapSafetyDemo.java
java HashMapSafetyDemo
~~~

Dùng JDK 17 hoặc mới hơn hỗ trợ phiên bản đích 17. Mã dùng API và cú pháp tương thích Java 17.

Stdout của lần chạy kiểm chứng:

~~~text
before mutation hash=1 hashMap=value concurrent=value
observed after mutation hash=2 hashMap=null concurrent=null
observed sizes after mutation=1,1
observed stored key same reference=true
immutable key lookup=stable
defensive key lookup=group-value
defensive key parts=[team, A]
parts modification=UnsupportedOperationException
split critical sections count=1 expected=2
whole critical section count=2000
concurrent compute count=2000
safe-case checks=PASS
~~~

Các dòng có nhãn observed ghi lại hành vi đã thấy sau khi sửa khóa; không phải một yêu cầu rằng mọi triển khai Map phải xử lý một khóa vi phạm điều kiện theo đúng cách đó. Những check trong mã không dùng các kết quả quan sát ấy làm điều kiện đúng.

### Vì sao thí nghiệm đồng thời có kết quả xác định?

Cả hai tác vụ chỉ ghi sau khi CountDownLatch xác nhận cả hai đã đọc. Vì chưa có lần ghi nào trước đó, chúng cùng giữ before bằng 0. Hai lần ghi sau được khóa riêng nhưng đều ghi 1; thứ tự của chúng không thay kết quả.

Ví dụ wholeCriticalSection thực hiện 1000 lượt tăng cho mỗi tác vụ và giữ cả get/put trong cùng vùng khóa. Hai tác vụ hoàn tất cho 2000.

Ví dụ atomicCompute dùng ConcurrentHashMap với khóa count đã được khởi tạo bằng 0, không có bên nào xóa khóa. Mỗi compute tăng từ giá trị hiện tại theo hợp đồng nguyên tử của loại Map đó, nên hai nhóm 1000 lượt cũng cho 2000.

Chương trình chờ Future hoàn tất để nhận kết quả và truyền lỗi tác vụ ra luồng chính. Giới hạn chờ giúp lỗi phối hợp không biến thành chờ vô hạn trong ví dụ; nó không tham gia tính đúng của bộ đếm. ExecutorService được dừng trong finally.

### Vì sao ConcurrentHashMap vẫn gặp lỗi khóa?

Hai Map ở phần đầu giữ cùng một đối tượng MutableKey. Sửa id làm dữ liệu nhận diện đổi ở cả hai nơi. Việc ConcurrentHashMap bảo vệ truy cập cấu trúc đồng thời không tự chuyển ánh xạ sang một định danh mới hợp lệ.

Trong lần chạy, size vẫn là một nhưng get bằng khóa đã đổi trả null ở cả hai Map. Từ đó phải rút ra yêu cầu giữ khóa ổn định, không phải một quy trình “đổi id rồi dùng size để sửa lại Map”.


## 7. Chọn một hợp đồng truy cập phù hợp

### Một nơi sở hữu dữ liệu và truy cập tuần tự

Nếu Map thuộc một tác vụ, một đối tượng có quyền sở hữu rõ hoặc một luồng duy nhất, HashMap là lựa chọn tự nhiên khi những yêu cầu khác phù hợp.

Hạn chế làm lộ chính Map hoặc đối tượng khóa có thể sửa ra ngoài nơi sở hữu. Một phương thức trả thẳng keySet, values hoặc entrySet của Map nội bộ có thể làm lộ thêm đường thay đổi ánh xạ.

Quyền sở hữu rõ giúp giảm nhu cầu đồng bộ, nhưng vẫn cần khóa có nhận diện ổn định. Một luồng duy nhất cũng có thể tạo lỗi khóa khả biến như phần đầu chương trình.

### HashMap cùng một khóa bảo vệ chung

Với dữ liệu được nhiều luồng truy cập, có thể giữ một đối tượng lock riêng và yêu cầu mọi thao tác liên quan tuân theo nó.

Điều cần xác định không chỉ là “có synchronized hay chưa”, mà là:

- Dữ liệu nào được bảo vệ bởi lock?
- Một thao tác nghiệp vụ gồm những bước nào phải nằm trong cùng vùng?
- Những lượt duyệt hoặc sao chép có dùng cùng cơ chế không?
- Có đường nào bỏ qua lock qua Map gốc, góc nhìn hoặc đối tượng giá trị không?

Với phép tăng trong chương trình, phạm vi cần bảo vệ là đọc số hiện tại, tính số mới và ghi số mới. Với thao tác chuyển giữa hai khóa, có thể còn nhiều trạng thái phải đi cùng nhau hơn.

Đặt lock ở nơi sở hữu dữ liệu và giữ nó ổn định thường rõ hơn việc mỗi phương thức tạo một `new Object()` để synchronized. Các lock mới khác nhau không phối hợp truy cập tới cùng Map.

### Collections.synchronizedMap

Có thể bọc:

~~~java
Map<String, Integer> counters =
        Collections.synchronizedMap(new HashMap<>());
~~~

Mọi truy cập vào cấu trúc nền cần đi qua đối tượng được trả về hoặc các góc nhìn của nó theo đúng quy tắc. Không giữ một tham chiếu khác rồi dùng thẳng HashMap nền để bỏ qua lớp bọc.

Nếu tự ghép nhiều lời gọi thành một phép tăng, bảo vệ cả chuỗi bằng monitor của lớp bọc:

~~~java
synchronized (counters) {
    int current = counters.getOrDefault("count", 0);
    counters.put("count", current + 1);
}
~~~

Đoạn này giả sử bộ đếm không được lưu null và mọi bên tuân thủ cùng hợp đồng. Việc lớp bọc đồng bộ từng get và put không tự làm một chuỗi get–tính–put tự viết trở thành một thao tác không thể xen ngang.

Khi duyệt các collection view bằng iterator, spliterator hoặc stream, hợp đồng synchronizedMap yêu cầu đồng bộ trên chính Map được trả về. Khóa trên `counters.keySet()` là một monitor khác và không thay cho việc khóa counters. [Tài liệu Collections.synchronizedMap, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedMap(java.util.Map)).

Nếu cần một bản dữ liệu để xử lý lâu bên ngoài vùng khóa, có thể sao chép trong vùng được bảo vệ rồi xử lý bản sao:

~~~java
Map<String, Integer> snapshot;
synchronized (counters) {
    snapshot = new HashMap<>(counters);
}
~~~

Với khóa String và giá trị Integer bất biến trong ví dụ, bản sao cấu trúc có cách diễn giải đơn giản. Nếu khóa hoặc giá trị chứa đối tượng có thể sửa, bản sao nông chưa tự bảo vệ nội dung sâu khỏi các tác vụ khác.

### ConcurrentHashMap và thao tác nguyên tử theo hợp đồng

ConcurrentHashMap hỗ trợ truy cập đồng thời và có các thao tác như putIfAbsent, replace có điều kiện, compute hoặc merge với bảo đảm phù hợp. Với một bộ đếm theo khóa, compute như chương trình giúp giữ phép tính và cập nhật trong thao tác nguyên tử được lớp đó định nghĩa. [Tài liệu ConcurrentHashMap, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html).

Những giới hạn cần hiểu:

- Chuỗi get rồi put tự viết vẫn là hai thao tác, dù từng thao tác riêng an toàn luồng.
- Một compute cho một khóa không tự tạo giao dịch nhiều khóa.
- Hàm tính cần ngắn, không sửa lại chính Map bên trong.
- ConcurrentHashMap không nhận khóa hoặc giá trị null được lưu.
- Khóa vẫn phải giữ đúng hợp đồng nhận diện và mã băm.
- Đối tượng giá trị có thể sửa vẫn cần chính sách truy cập riêng.

Không thay HashMap bằng ConcurrentHashMap rồi giữ nguyên mọi giả định nghiệp vụ cũ. Ví dụ một ứng dụng dùng null như trạng thái đã tra cứu nhưng không tìm thấy cần mô hình giá trị khác trước khi đổi lớp Map.

Các phương thức compute của Map nói chung cũng không có một lời hứa nguyên tử chung cho mọi triển khai. Cần dựa vào hợp đồng của loại được dùng, thay vì chỉ thấy tên phương thức giống nhau.

## 8. Map an toàn luồng không tự bảo vệ giá trị bên trong

Giả sử Map chứa danh sách công việc:

~~~java
Map<String, List<String>> tasks = new ConcurrentHashMap<>();
tasks.computeIfAbsent("team", key -> new ArrayList<>()).add("job");
~~~

ConcurrentHashMap có thể bảo vệ việc đặt hoặc lấy ánh xạ team tới danh sách. Nhưng add tác động lên ArrayList được trả về; nó không tự được mọi thao tác Map bảo vệ như một hành động trên danh sách.

Nếu nhiều luồng cùng sửa danh sách đó, cần một kiểu giá trị hỗ trợ cách dùng đồng thời hoặc cơ chế khóa đúng cho nó. Một hướng khác là cập nhật bằng các giá trị bất biến mới và chỉ thay ánh xạ theo một quy tắc nguyên tử phù hợp.

Tương tự, một giá trị có trường count có thể sửa không tự được bảo vệ chỉ vì nó được lưu trong ConcurrentHashMap. Cần phân biệt bốn lớp trách nhiệm:

~~~text
Nhận diện khóa
    -> ổn định của equals/hashCode.

Cấu trúc Map
    -> hợp đồng truy cập và cập nhật ánh xạ.

Thao tác nghiệp vụ nhiều bước
    -> phạm vi nguyên tử đúng.

Nội dung đối tượng giá trị
    -> cơ chế đọc/sửa riêng phù hợp.
~~~

Sửa được một lớp không đồng nghĩa các lớp còn lại đều đã đúng.

Điều này cũng giải thích vì sao một lớp bọc chỉ đọc chưa làm dữ liệu sâu bất biến. Người giữ một đối tượng giá trị có thể vẫn sửa chính đối tượng đó qua một tham chiếu khác.

## 9. ConcurrentModificationException là tín hiệu bắt lỗi, không phải hàng rào an toàn

Một iterator của HashMap có thể phát hiện thay đổi cấu trúc trái quy tắc và ném ConcurrentModificationException. Nhưng việc phát hiện là cơ chế hỗ trợ bắt lỗi theo khả năng của triển khai, không phải một hệ thống đồng bộ.

Ngoại lệ này không nhất thiết có hai luồng. Một luồng duy nhất vừa dùng for-each vừa xóa trực tiếp qua Map cũng có thể vi phạm hợp đồng iterator. [Tài liệu ConcurrentModificationException, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ConcurrentModificationException.html).

Ví dụ minh họa cách dùng không phù hợp:

~~~java
for (String key : map.keySet()) {
    if (key.startsWith("old-")) {
        map.remove(key);
    }
}
~~~

Muốn xóa khi đang duyệt trong một ngữ cảnh tuần tự phù hợp, dùng iterator.remove của chính iterator đang xét hoặc một phép lọc được hỗ trợ. Nếu có cập nhật từ các luồng khác, còn phải tuân thủ cơ chế phối hợp của nguồn; đổi phương thức xóa không tự giải quyết mọi cạnh tranh.

Đừng xử lý bằng cách bắt ConcurrentModificationException rồi tiếp tục coi kết quả duyệt dở là một bản chụp đầy đủ. Cũng đừng kết luận “không có ngoại lệ nên cách dùng nhiều luồng an toàn”. Lỗi nhìn thấy dữ liệu hoặc mất cập nhật không cần phát sinh ngoại lệ này.

Đối với một loại Map đồng thời, iterator có thể có hợp đồng duyệt khác. Việc một iterator không ném ngoại lệ khi có cập nhật cũng không tự có nghĩa nó chụp toàn bộ Map ở một thời điểm duy nhất.

Nếu cần dữ liệu báo cáo nhất quán theo một thời điểm hoặc cần bất biến trên nhiều ánh xạ, hãy thiết kế cơ chế lấy bản chụp hoặc vùng đọc phù hợp với yêu cầu ấy.

## 10. Đổi khóa và xử lý dữ liệu đã vi phạm điều kiện

### Khi định danh thật sự cần thay đổi

Thay vì sửa id của một đối tượng đang làm khóa, có thể dùng các đối tượng khóa bất biến và một thao tác thay ánh xạ rõ ràng.

Với khóa mới khác khóa cũ, quy trình nghiệp vụ cần quyết định:

1. Khóa cũ có tồn tại không?
2. Khóa mới đã có ánh xạ chưa và xử lý xung đột thế nào?
3. Giá trị nào được chuyển?
4. Các bên đọc có được thấy trạng thái giữa lúc xóa và thêm không?
5. Nếu một bước thất bại thì trạng thái mong muốn là gì?

Nếu toàn bộ quá trình phải xuất hiện như một thay đổi không thể xen ngang, bảo vệ nó bằng cơ chế thích hợp. Hai lời gọi remove và put liên tiếp trên ConcurrentHashMap vẫn không tự tạo giao dịch thay khóa.

Nếu định danh cũ vốn cần được giữ để liên kết dữ liệu ngoài, việc thay nó còn là một quyết định của mô hình nghiệp vụ. Không coi đổi một trường Java là đủ để cập nhật mọi quan hệ liên quan.

### Nếu khóa đã bị sửa khi còn trong Map

Đừng giả định gọi remove bằng khóa đã đổi chắc chắn tìm được ánh xạ cũ, hoặc put lại đối tượng đó chắc chắn chỉ “sửa vị trí” mà không có hậu quả khác. Bạn đã rời khỏi điều kiện sử dụng được hợp đồng bảo đảm.

Một lần dựng lại Map từ các tham chiếu khóa đang hỏng có thể không khôi phục được danh tính ban đầu. Hai khóa đã bị sửa thành bằng nhau còn có thể gây xung đột khi dựng lại.

Cách xử lý cần bắt đầu từ nguồn dữ liệu có thể xác định đúng danh tính, dừng những cập nhật gây vấn đề và xây lại trạng thái theo một chính sách xung đột rõ ràng trong ngữ cảnh kiểm soát được. Đừng dựa vào một thủ thuật nội bộ của một phiên bản HashMap để coi dữ liệu đã được sửa đúng về mặt nghiệp vụ.

Giải pháp lâu dài nằm ở nơi tạo và sở hữu khóa: giới hạn sửa các trường nhận diện, sao chép dữ liệu thành phần khi cần và kiểm tra các đường làm lộ tham chiếu có thể sửa.

### Đọc dùng chung một trạng thái không đổi

Có thể chia sẻ dữ liệu để chỉ đọc khi trạng thái đã xây dựng hoàn chỉnh, được công bố qua cơ chế bảo đảm nhìn thấy phù hợp và không tiếp tục bị sửa trái hợp đồng. Khóa và giá trị cũng cần ổn định theo mức mà các bên đọc giả định.

Điều kiện này khác với việc “không ai sửa trong lần kiểm thử vừa rồi”. Một tham chiếu Map bị làm lộ và được sửa từ một đường không kiểm soát sẽ phá giả định chỉ đọc.

## 11. Bảng nhận diện lỗi nhanh

| Triệu chứng hoặc cách viết | Cần kiểm tra |
| --- | --- |
| Size còn nhưng get bằng khóa cũ trả null | Dữ liệu equals/hashCode của khóa có bị sửa không? Giá trị có thể null không? |
| Đổi sang ConcurrentHashMap mà khóa vẫn tra sai | Khóa có còn ổn định không, hay chỉ lớp chứa được thay? |
| Khai báo final nhưng dữ liệu khóa đổi | Final đang bảo vệ tham chiếu hay thật sự bảo vệ trạng thái bên trong? |
| Record có thành phần List rồi tra cứu lỗi | Danh sách hoặc phần tử có bị sửa qua tham chiếu khác không? |
| Hai lần tăng nhưng chỉ tăng một | Phạm vi khóa có bao trọn đọc–tính–ghi không? |
| Mỗi phương thức đều synchronized nhưng vẫn sai nghiệp vụ | Những lời gọi cần nguyên tử có bị tách thành nhiều vùng không? |
| Có synchronized ở cả hai nơi nhưng vẫn không phối hợp | Hai bên có dùng cùng monitor và cùng quy tắc dữ liệu không? |
| SynchronizedMap nhưng duyệt bất ngờ | Lượt duyệt có được bảo vệ trên chính Map lớp bọc theo hợp đồng không? |
| Compute trên HashMap được xem là tự an toàn luồng | Đang dựa vào hợp đồng lớp nào? |
| ConcurrentHashMap chứa ArrayList được sửa trực tiếp | Cơ chế bảo vệ danh sách giá trị là gì? |
| Không gặp ConcurrentModificationException | Không đủ thông tin để kết luận an toàn |
| Sao chép Map để sửa lỗi khóa | Bản sao có còn dùng chung khóa đã bị thay đổi không? |

Các câu hỏi này giúp phân loại lỗi trước khi chọn cách sửa. Không phải mọi kết quả null đều do đồng thời, và không phải mọi vấn đề nhiều luồng đều do khóa khả biến.

## 12. Bài tập và lời giải

### Bài 1 — final có đủ không?

Một biến final trỏ tới MutableKey có setter id. Sau khi put, gọi setter. Biến không được gán lại, vậy tra cứu có chắc còn hợp lệ không?

**Lời giải:** không. Final chỉ ngăn đổi tham chiếu biến. Đối tượng vẫn bị sửa dữ liệu nhận diện, có thể vi phạm điều kiện sử dụng khóa trong Map.

### Bài 2 — Đổi mô tả có làm hỏng khóa không?

Khóa có id bất biến và description có thể sửa. Equals/hashCode chỉ xét id. Việc thay description có nhất thiết phá tra cứu băm không?

**Lời giải:** không nhất thiết, vì dữ liệu nhận diện không đổi. Tuy nhiên, nếu description được đọc và sửa giữa các luồng thì cần hợp đồng truy cập riêng cho trường đó.

### Bài 3 — Record chứa danh sách

`record Key(List<String> parts)` nhận một ArrayList từ bên ngoài. Bên ngoài sửa ArrayList sau khi Key được put. Vì sao từ khóa record chưa đủ?

**Lời giải:** record không tự sao chép sâu các đối tượng thành phần. Nội dung danh sách tham gia so bằng và mã băm có thể đổi. Có thể lấy bản sao danh sách không cho sửa khi xây khóa; với phần tử có thể sửa còn cần xử lý sâu hơn.

### Bài 4 — ConcurrentHashMap sửa được khóa khả biến không?

Thay HashMap bằng ConcurrentHashMap nhưng vẫn sửa id dùng trong equals/hashCode sau put. Vấn đề đã được giải quyết chưa?

**Lời giải:** chưa. An toàn cập nhật cấu trúc đồng thời không tự duy trì lại vị trí của một khóa đã thay danh tính. Vẫn cần khóa ổn định.

### Bài 5 — Vì sao ví dụ tách vùng khóa luôn cho 1?

Trong chương trình, mọi get và put đều dùng cùng lock, nhưng kết quả vẫn là 1 sau hai tác vụ. Latch có vai trò gì?

**Lời giải:** latch buộc cả hai đọc trước khi ai được ghi, nên cả hai tính từ 0 và đều ghi 1. Lock ngăn hai truy cập Map diễn ra cùng lúc, nhưng không bảo vệ khoảng đọc–tính–ghi như một đơn vị. Đây là lỗi phạm vi nguyên tử.

### Bài 6 — Mỗi bên một lock

Luồng A synchronized(lockA), luồng B synchronized(lockB), cả hai sửa cùng HashMap. Có thể coi Map đã được bảo vệ vì ở đâu cũng có synchronized không?

**Lời giải:** không. Hai monitor khác nhau không loại trừ nhau. Cần một hợp đồng chung cho dữ liệu chia sẻ và phạm vi thao tác cần bảo vệ.

### Bài 7 — Duyệt synchronizedMap

Có synchronizedMap m, muốn duyệt m.keySet(). Khóa trên đối tượng keySet có thay được khóa trên m theo hợp đồng lớp bọc không?

**Lời giải:** không. Cần bảo vệ lượt duyệt theo monitor của Map được trả về, như synchronized(m), và bảo đảm không có đường truy cập Map nền bỏ qua lớp bọc.

### Bài 8 — Không có ngoại lệ

Một phép thử nhiều luồng với HashMap chạy hàng trăm lần mà không ném ConcurrentModificationException. Có đủ để kết luận đúng không?

**Lời giải:** không. Ngoại lệ chỉ là cơ chế bắt một số vi phạm; mất cập nhật và vấn đề nhìn thấy dữ liệu có thể không làm nó xuất hiện. Phải kiểm tra hợp đồng truy cập và tính nguyên tử, không chỉ chờ lỗi biểu hiện.

### Bài 9 — Map an toàn, giá trị có an toàn không?

ConcurrentHashMap chứa một ArrayList. Hai luồng lấy cùng danh sách rồi cùng add. Tính an toàn của Map đã bao trọn việc này chưa?

**Lời giải:** chưa. Add là thao tác trên đối tượng giá trị. Cần một kiểu giá trị hoặc cơ chế bảo vệ phù hợp cho danh sách, hay một thiết kế cập nhật bằng giá trị mới.

### Bài 10 — Chuyển từ khóa cũ sang khóa mới

Cần đổi định danh, muốn xóa oldKey rồi put newKey trên ConcurrentHashMap. Hai lời gọi có tự là một giao dịch để người đọc không thấy trạng thái giữa chừng không?

**Lời giải:** không. Cần quy định tính nhất quán nhiều bước và cơ chế phù hợp. Nên dùng các đối tượng khóa bất biến, kiểm tra xung đột khóa mới và chính sách thất bại, thay vì sửa chính đối tượng khóa đang nằm trong Map.

## 13. Giữ đúng bốn ranh giới

Trước khi sửa một lỗi HashMap, xác định dữ liệu nhận diện khóa có ổn định không, cấu trúc Map được truy cập theo cơ chế nào, thao tác nghiệp vụ cần nguyên tử tới đâu và giá trị bên trong có bị sửa riêng không.

Ví dụ khóa khả biến giải thích vì sao đúng cùng một tham chiếu vẫn có thể không tra được. Ví dụ bộ đếm giải thích vì sao những lời gọi riêng được bảo vệ vẫn có thể ghép thành một thao tác sai. Hai cách sửa phải nhắm đúng hai nguyên nhân đó.

Khóa ổn định, quyền sở hữu rõ và một hợp đồng cập nhật nhất quán giúp HashMap được dùng đúng. Một lớp chứa dữ liệu không thể tự thay thế cho toàn bộ các quyết định này.



</details>

- [Quay lại đầu trang](#back-to-top)
