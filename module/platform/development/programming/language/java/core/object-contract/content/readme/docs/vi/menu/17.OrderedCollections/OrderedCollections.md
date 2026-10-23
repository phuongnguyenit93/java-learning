<a id="back-to-top"></a>

# Tập hợp có thứ tự

## Menu
- [TreeSet và TreeMap dùng phép so sánh để nhận diện khóa tương đương](#ordered-collections-001)
- [Hai đối tượng equals() false nhưng Comparator trả về 0](#ordered-collections-002)
- [Hai đối tượng equals() true nhưng Comparator phân biệt](#ordered-collections-003)
- [Khác biệt kết quả giữa HashSet với TreeSet](#ordered-collections-004)
- [Ví dụ BigDecimal có scale khác nhau](#ordered-collections-005)
- [Comparator không nhất quán với equals() và hợp đồng Set hoặc Map](#ordered-collections-006)
- [Thay đổi trường sắp xếp khi đối tượng đang nằm trong cây](#ordered-collections-007)
- [Phân biệt loại phần tử trùng với giữ tất cả phần tử rồi sắp xếp](#ordered-collections-008)

## <a id="ordered-collections-001">TreeSet và TreeMap dùng phép so sánh để nhận diện khóa tương đương</a>

<details>
<summary>Xem chi tiết</summary>

# TreeSet và TreeMap dùng phép so sánh để nhận diện khóa tương đương

## 1. Điều cần hiểu trước: thứ tự và “trùng khóa” có chung một quy tắc

Trong Java, `TreeSet<E>` và `TreeMap<K,V>` là các cấu trúc dữ liệu **có thứ tự sắp xếp** dựa trên **thứ tự tự nhiên** (`Comparable`) hoặc một **bộ so sánh** (`Comparator`). Phép so sánh ấy không chỉ quyết định phần tử nằm trước hay sau; nó còn quyết định hai khóa được xem là **tương đương để lưu trữ và tra cứu**.

Quy tắc cốt lõi:

> Với hai khóa `a`, `b` hợp lệ trong cùng cấu trúc có thứ tự, `compare(a, b) == 0` nghĩa là cấu trúc sẽ đối xử với chúng như **một khóa tương đương**.

Trong đó `compare` là `comparator.compare(a,b)` nếu cấu trúc có comparator; hoặc là kết quả của thứ tự tự nhiên qua `Comparable.compareTo` nếu không truyền comparator.

Điều này có thể khác hoàn toàn với `a.equals(b)`:

- `TreeSet` không lưu hai phần tử mà bộ so sánh đánh giá bằng `0`; `add` phần tử thứ hai trả về `false`.
- `TreeMap` xem hai khóa có kết quả so sánh `0` là cùng vị trí khóa; `put` bằng khóa thứ hai cập nhật **giá trị của mục nhập** thay vì tăng số khóa.
- `contains`, `containsKey` và `get` có thể coi một đối tượng truy vấn **khác theo `equals`** là khóa đã tồn tại nếu phép so sánh cho kết quả `0`.
- `HashSet` và `HashMap` thông thường dựa vào `hashCode` và `equals`, nên có thể đưa ra kết quả khác với `TreeSet`/`TreeMap` trên cùng dữ liệu.

Phần quan trọng nhất của bài là phân biệt **tương đương do phép so sánh** với **bằng nhau do `equals`**, biết lúc nào cần hai quan hệ nhất quán và hiểu hậu quả nếu chúng khác nhau.

## 2. Cơ chế định vị khóa trong cây có thứ tự

Hãy hình dung `TreeMap` lưu một cấu trúc cây tìm kiếm cân bằng, thường là cây đỏ-đen. Khi thêm hay tìm một khóa `query`, cấu trúc sử dụng phép so sánh với khóa của nút đang xét:

| Kết quả so sánh khóa truy vấn với khóa nút | Ý nghĩa với tìm kiếm |
| --- | --- |
| Giá trị âm | Khóa truy vấn đứng **trước** khóa nút; tiếp tục tìm nhánh thích hợp phía trước (theo thứ tự cây). |
| `0` | Hai khóa **tương đương theo thứ tự so sánh**; đã gặp khóa logic mà cấu trúc đang tìm. |
| Giá trị dương | Khóa truy vấn đứng **sau** khóa nút; tiếp tục tìm nhánh thích hợp phía sau. |

Đây là sơ đồ khái niệm, không phải mã nguồn cụ thể của `TreeMap`:

```text
search(query):
    node = root
    while node != null:
        result = compare(query, node.key)

        if result < 0:
            node = node.left
        else if result > 0:
            node = node.right
        else:
            return FOUND(node)  // result == 0: tương đương khóa
    return NOT_FOUND
```

Đối với `TreeSet`, yếu tố quyết định là cách so sánh **phần tử**; đối với `TreeMap` là cách so sánh **khóa**. Hai cấu trúc này **không dùng `hashCode` để xác định vị trí trong cây**. Chúng cũng không đòi mỗi cặp khóa khác nhau phải có mã băm khác nhau.

Ví dụ với comparator chỉ xét tên không phân biệt hoa/thường: `"Java"` và `"JAVA"` đều nằm tại một vị trí khóa vì `compare("Java","JAVA") == 0`, dù `String.equals` của chúng trả `false`.

### Dấu của `compare` mới là điều quan trọng

Theo hợp đồng `Comparator`/`Comparable`, kết quả âm, `0` và dương biểu diễn thứ tự. Không được đòi chính xác `-1` hay `1`; bộ so sánh hợp lệ có thể trả mọi số nguyên âm/dương. Viết `if (compare(a,b) == -1)` là sai cách diễn giải hợp đồng. Viết `result < 0`, `result == 0` hoặc `result > 0`.

## 3. So với HashSet và HashMap

| Cấu trúc | Điều dùng để định hướng tìm kiếm | Điều quyết định trùng khóa/phần tử |
| --- | --- | --- |
| `HashSet<E>` | `hashCode()` của phần tử | Bình đẳng theo `equals` (và kiểm tra cùng tham chiếu khi phù hợp) |
| `HashMap<K,V>` | `hashCode()` của khóa | Bình đẳng theo `equals` của khóa |
| `TreeSet<E>` | `Comparable` hoặc `Comparator` | Kết quả so sánh `0` |
| `TreeMap<K,V>` | `Comparable` hoặc `Comparator` | Kết quả so sánh `0` của khóa |

**Hai điểm cần giữ riêng:**

1. Hash trùng nhau **không chứng minh** hai khóa bằng nhau; cấu trúc băm vẫn phải so sánh bằng nhau.
2. Với cây có thứ tự, `compare(a,b) == 0` là **điều kiện nhận diện cùng khóa theo chính sách sắp xếp**, cho dù `a.equals(b)` có thể sai.

Nếu bộ so sánh “nhìn” ít thuộc tính hơn `equals`, các đối tượng khác nhau theo `equals` có thể bị gộp thành một khóa. Nếu bộ so sánh phân biệt những đối tượng mà `equals` coi là bằng nhau, cây có thể giữ hai mục mà một `HashSet` gộp thành một.

Java khuyến nghị thứ tự dùng trong sorted set/map **nhất quán với `equals`** để các cấu trúc đáp ứng kỳ vọng chung của `Set`/`Map`. Tuy nhiên API vẫn cho phép comparator không nhất quán: khi đó thao tác theo thứ tự **vẫn có cơ chế xác định**, nhưng kết quả “trùng phần tử” có thể gây bất ngờ hoặc không tuân theo hợp đồng chung dựa trên `equals`.

## 4. Ví dụ đặc trưng: chuỗi không phân biệt hoa/thường

`String.equals` phân biệt chữ hoa/thường, trong khi `String.CASE_INSENSITIVE_ORDER` là comparator sắp xếp không phân biệt hoa/thường theo quy tắc của `String.compareToIgnoreCase`.

Vì vậy:

```java
String a = "Java";
String b = "JAVA";

boolean sameByEquals = a.equals(b); // false
boolean sameByComparator =
        String.CASE_INSENSITIVE_ORDER.compare(a, b) == 0; // true
```

Đây chỉ là các biểu thức minh họa trong một phương thức, chưa phải chương trình độc lập. Nếu tạo:

```java
TreeSet<String> names =
        new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

names.add("Java"); // true
names.add("JAVA"); // false
```

`TreeSet` chỉ giữ một phần tử vì lần thêm thứ hai bị nhận diện là trùng theo comparator. Khi hỏi `names.contains("jAvA")`, kết quả là `true` dù không có chuỗi chính xác `"jAvA"` nào được thêm. Điều kiện là comparator coi truy vấn này tương đương với khóa hiện có.

Với `TreeMap`:

```java
TreeMap<String, Integer> counts =
        new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

counts.put("Java", 1);
counts.put("JAVA", 2);
```

Cấu trúc chỉ có **một mục nhập**, và giá trị gắn với khóa tương đương được cập nhật thành `2`. Trên `TreeMap` Java, đối tượng khóa của mục nhập hiện có được giữ khi cập nhật theo khóa comparator-tương-đương; vì thế trong ví dụ khóa nhìn thấy từ `firstKey()` vẫn là `"Java"`, còn `get("java")` trả `2`. Đừng dựa vào việc thêm khóa khác kiểu chữ để thay đổi cách viết khóa đã lưu; nếu nghiệp vụ cần khóa chuẩn hóa, hãy chuẩn hóa trước khi thêm.

### Khi nào đây là chính sách hợp lý?

Trường hợp cho phép: một từ khóa tìm kiếm, nhãn đã được quy định rõ không phân biệt chữ hoa/chữ thường, hoặc tập tên theo chính sách riêng.

Trường hợp cần cân nhắc: mã sản phẩm, mã giảm giá, tài khoản, định danh bên ngoài và các chuỗi Unicode đa ngôn ngữ. Comparator không phân biệt case **không đồng nghĩa** chuẩn hóa Unicode toàn diện, loại bỏ dấu, hoặc đối chiếu ngôn ngữ theo locale. Chính sách bằng nhau của hệ thống phải được đặc tả trước.

## 5. Ví dụ khác: BigDecimal có thứ tự tự nhiên không nhất quán với equals

`BigDecimal.compareTo` so sánh **giá trị số**, trong khi `BigDecimal.equals` còn xét **scale** (với các cách biểu diễn khác scale, có thể coi là khác nhau).

Ví dụ:

```java
BigDecimal x = new BigDecimal("2.0");
BigDecimal y = new BigDecimal("2.00");

System.out.println(x.compareTo(y)); // 0
System.out.println(x.equals(y));    // false
```

Đây là đoạn nằm trong phương thức có import `java.math.BigDecimal`. Hai giá trị số bằng nhau theo thứ tự tự nhiên nhưng **khác nhau theo `equals`**. Do đó:

- `TreeSet<BigDecimal>` dùng thứ tự tự nhiên, chỉ giữ một phần tử cho `2.0` và `2.00`.
- `HashSet<BigDecimal>` dựa vào `equals`/`hashCode`, nên giữ được cả hai.
- `TreeMap<BigDecimal, V>` xem hai số này là một khóa sắp xếp; `HashMap<BigDecimal, V>` xem chúng là hai khóa khác nhau.

Đây là đặc điểm **có chủ ý của API**, không phải lỗi của `TreeSet` hay `HashSet`. Khi chọn làm khóa, phải tự quyết định dữ liệu có cần phân biệt scale hay không.

Nếu miền nghiệp vụ coi mọi biểu diễn cùng giá trị số là một giá trị duy nhất, có thể cân nhắc chuẩn hóa biểu diễn `BigDecimal` trước khi lưu vào các collection khác nhau (ví dụ `stripTrailingZeros` nếu phù hợp chính sách). Tuy vậy `stripTrailingZeros` có thể đổi biểu diễn scale và kiểu hiển thị, nên cần kiểm tra các yêu cầu về tiền tệ, định dạng và biểu diễn dữ liệu. Không mặc định việc chuẩn hóa này luôn an toàn cho mọi domain.

## 6. Chương trình Java 17+ chạy độc lập

Chương trình dưới đây cùng lúc quan sát bốn tình huống:

1. `TreeSet` và `TreeMap` sử dụng comparator không phân biệt case, nên `"Java"` và `"JAVA"` chỉ chiếm một khóa.
2. `HashSet` của chuỗi phân biệt hai biểu diễn đó.
3. `BigDecimal` có `compareTo == 0` nhưng `equals == false`.
4. Comparator chỉ xét tuổi khiến hai `Person` khác nhau bị xem là tương đương; bổ sung tiêu chí `name` tạo thứ tự phân biệt tốt hơn cho ví dụ.

Lưu thành `TreeKeyEquivalenceDemo.java`:

```java
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class TreeKeyEquivalenceDemo {
    record Person(String name, int age) {}

    public static void main(String[] args) {
        String a = "Java";
        String b = "JAVA";

        Set<String> sortedNames =
                new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        System.out.println("TreeSet add Java: " + sortedNames.add(a));
        System.out.println("TreeSet add JAVA: " + sortedNames.add(b));
        System.out.println("TreeSet size: " + sortedNames.size());
        System.out.println("TreeSet first: " + sortedNames.iterator().next());
        System.out.println("TreeSet contains jAvA: "
                + sortedNames.contains("jAvA"));
        System.out.println("String equals: " + a.equals(b));

        Set<String> hashedNames = new HashSet<>();
        hashedNames.add(a);
        hashedNames.add(b);
        System.out.println("HashSet size: " + hashedNames.size());

        TreeMap<String, Integer> scores =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        System.out.println("TreeMap first put old: "
                + scores.put("Java", 1));
        System.out.println("TreeMap second put old: "
                + scores.put("JAVA", 2));
        System.out.println("TreeMap size: " + scores.size());
        System.out.println("TreeMap firstKey: " + scores.firstKey());
        System.out.println("TreeMap get java: " + scores.get("java"));

        BigDecimal x = new BigDecimal("2.0");
        BigDecimal y = new BigDecimal("2.00");
        System.out.println("BigDecimal compareTo: " + x.compareTo(y));
        System.out.println("BigDecimal equals: " + x.equals(y));

        Set<BigDecimal> sortedNumbers = new TreeSet<>();
        sortedNumbers.add(x);
        sortedNumbers.add(y);
        Set<BigDecimal> hashedNumbers = new HashSet<>();
        hashedNumbers.add(x);
        hashedNumbers.add(y);
        System.out.println("BigDecimal TreeSet size: "
                + sortedNumbers.size());
        System.out.println("BigDecimal HashSet size: "
                + hashedNumbers.size());

        Person an = new Person("An", 20);
        Person binh = new Person("Binh", 20);

        Comparator<Person> byAge =
                Comparator.comparingInt(Person::age);
        TreeSet<Person> sameAgeSet = new TreeSet<>(byAge);
        sameAgeSet.add(an);
        sameAgeSet.add(binh);

        System.out.println("Person equals: " + an.equals(binh));
        System.out.println("age-only compare: " + byAge.compare(an, binh));
        System.out.println("age-only TreeSet size: "
                + sameAgeSet.size());
        System.out.println("age-only contains Binh: "
                + sameAgeSet.contains(binh));

        Comparator<Person> byAgeThenName =
                Comparator.comparingInt(Person::age)
                          .thenComparing(Person::name);
        TreeSet<Person> distinctPersons =
                new TreeSet<>(byAgeThenName);
        distinctPersons.add(an);
        distinctPersons.add(binh);
        System.out.println("age+name TreeSet size: "
                + distinctPersons.size());
    }
}
```

Lệnh biên dịch và chạy (JDK 17+):

```bash
javac -encoding UTF-8 TreeKeyEquivalenceDemo.java
java TreeKeyEquivalenceDemo
```

Kết quả chính xác của chương trình:

```text
TreeSet add Java: true
TreeSet add JAVA: false
TreeSet size: 1
TreeSet first: Java
TreeSet contains jAvA: true
String equals: false
HashSet size: 2
TreeMap first put old: null
TreeMap second put old: 1
TreeMap size: 1
TreeMap firstKey: Java
TreeMap get java: 2
BigDecimal compareTo: 0
BigDecimal equals: false
BigDecimal TreeSet size: 1
BigDecimal HashSet size: 2
Person equals: false
age-only compare: 0
age-only TreeSet size: 1
age-only contains Binh: true
age+name TreeSet size: 2
```

### Phân tích những kết quả dễ gây bất ngờ

**`TreeSet add JAVA: false`:** không có nghĩa chuỗi `"JAVA"` đã nằm trong cây theo đúng tham chiếu hoặc đúng `String.equals`. Nó nghĩa là đã có phần tử tương đương với `"JAVA"` theo comparator. Vì comparator không phân biệt chữ hoa/thường, `"Java"` đủ để lần thêm thứ hai được xem là trùng.

**`TreeMap second put old: 1`:** khi thêm `"JAVA"`, map tìm thấy khóa comparator-tương-đương `"Java"`. Nó trả giá trị cũ `1` và cập nhật thành `2`. Số mục nhập vẫn bằng `1`; lệnh `get("java")` tra cứu theo cùng comparator và nhận `2`.

**`BigDecimal TreeSet size: 1` so với `HashSet size: 2`:** thứ tự tự nhiên của `BigDecimal` coi hai biểu diễn số là tương đương; `equals` và hash code của nó phân biệt scale.

**`age-only contains Binh: true`:** `Person("An",20)` và `Person("Binh",20)` khác nhau theo `record.equals`, nhưng comparator chỉ xét tuổi, cho ra `0`. Mặc dù `binh` không được lưu như phần tử riêng, truy vấn `contains(binh)` được xem là tìm thấy khóa tương đương là `an`.

**`age+name TreeSet size: 2`:** bổ sung `name` làm tiêu chí phụ đủ để phân biệt hai đối tượng của bộ dữ liệu ví dụ. Trong một domain thực, cần kiểm tra **đầy đủ các trường quyết định `equals`**; `age + name` chỉ nhất quán với `equals` nếu quy tắc bằng nhau của kiểu đó cũng dùng hai trường tương ứng.

## 7. “Nhất quán với equals” nghĩa chính xác là gì?

Một `Comparator` hoặc thứ tự tự nhiên được gọi là **nhất quán với `equals`** khi, với các đối tượng hợp lệ đang xét:

```text
compare(a, b) == 0   nếu và chỉ nếu   a.equals(b) == true
```

Đây là điều kiện cần giữa **tập cặp tương đương theo so sánh** và **tập cặp bằng nhau theo `equals`**. Nó mạnh hơn yêu cầu comparator chỉ sắp các phần tử theo một thứ tự hợp lý.

Có hai cách sai:

1. **Comparator gộp quá mức:** `compare(a,b) == 0` nhưng `a.equals(b) == false`. Ví dụ so `Person` chỉ theo tuổi. `TreeSet` mất khả năng giữ nhiều người cùng tuổi; `TreeMap` có thể ghi đè dữ liệu của người khác.
2. **Comparator phân biệt quá mức:** `a.equals(b) == true` nhưng `compare(a,b) != 0`. Ví dụ lớp có `equals` chỉ dùng `userId` nhưng comparator còn dùng thuộc tính tên hiển thị có thể khác. `TreeSet` có thể giữ hai phần tử bằng nhau theo `equals`, gây sai kỳ vọng chung của một `Set`.

Một comparator chỉ xét tuổi vẫn có thể đúng về thứ tự số học của tuổi; **sai là khi dùng nó để xác định danh tính Person** trong ứng dụng vốn định nghĩa người bằng dữ liệu khác.

Nếu mục tiêu là chỉ một người trên mỗi tuổi, comparator theo tuổi có thể phản ánh một **quy tắc khóa nghiệp vụ khác**. Khi đó nên cân nhắc biểu diễn trực tiếp khóa `age` và lưu giá trị người trong `TreeMap<Integer, Person>`, thay vì dùng `TreeSet<Person>` rồi làm người đọc hiểu nhầm rằng hai `Person` bằng nhau.

### Mối liên hệ với hợp đồng Map/Set chung

Các interface `Set`/`Map` mô tả sự hiện diện/trùng khóa theo `equals`. Nhưng `TreeSet`/`TreeMap` được thiết kế dựa trên comparator hoặc natural ordering. Tài liệu Java khuyến nghị phép so sánh **nhất quán với `equals`** nếu muốn triển khai đáp ứng đầy đủ những kỳ vọng hợp đồng đó.

Sự không nhất quán **không có nghĩa** Java buộc phải ném lỗi. Phần lớn thao tác vẫn chạy và làm đúng theo comparator, tạo ra những kết quả quan sát được như ví dụ. Rủi ro chủ yếu là **ngữ nghĩa**, đặc biệt khi đổi qua lại giữa tập/map có thứ tự và cấu trúc dựa trên hash.

## 8. Điều kiện để comparator không làm hỏng cấu trúc cây

Không phải hàm trả số nguyên nào cũng là comparator hợp lệ. Java yêu cầu các tính chất tương ứng một thứ tự nhất quán. Các nguyên tắc cốt lõi:

- **Phản đối xứng theo dấu:** `sign(compare(a,b)) == -sign(compare(b,a))`. Đổi chiều hai đối tượng phải đảo chiều quan hệ trước/sau, hoặc cả hai trả `0`.
- **Bắc cầu của thứ tự:** nếu `compare(a,b) > 0` và `compare(b,c) > 0` thì `compare(a,c) > 0`; tương tự cho chiều âm.
- **Nhất quán khi so bằng `0`:** nếu `compare(a,b) == 0`, dấu so sánh `a` với một đối tượng `z` phải giống dấu so sánh `b` với `z`. Nếu không, hai đối tượng bị coi là một khóa nhưng lại có vị trí tương quan khác nhau với các khóa khác.
- **Ổn định trong thời gian một khóa nằm trong cây:** dữ liệu làm căn cứ sắp xếp không nên thay đổi sau khi thêm, nếu cây không được cập nhật phù hợp.

Ví dụ comparator viết như sau là nguy hiểm:

```java
Comparator<Integer> bad = (a, b) -> a - b;
```

Phép trừ `int` có thể **tràn số**, trả dấu sai và phá tính bắc cầu của thứ tự. Hãy dùng:

```java
Comparator<Integer> good = Integer::compare;
```

Hai đoạn trên là các biểu thức minh họa trong phương thức với import `java.util.Comparator`. Đối với đối tượng phức tạp, dùng `Comparator.comparing`, `comparingInt`, `thenComparing`, `nullsFirst`/`nullsLast` khi đúng với policy; chúng giúp mã rõ nghĩa và giảm khả năng viết sai quy tắc.

Một lỗi nghiêm trọng khác là **comparator ngẫu nhiên hoặc dựa vào thời gian**. Nếu cùng cặp khóa mỗi lần so lại cho thứ tự khác, cây không thể duy trì giả định cần thiết để tìm/đặt dữ liệu đúng.

## 9. Thiết kế comparator khớp với danh tính của domain

Trước khi chọn `TreeSet` hay `TreeMap`, hãy định nghĩa “hai khóa tương đương” bằng chính ngôn ngữ nghiệp vụ.

### Trường hợp A: khóa là một chuỗi phân biệt case

Nếu mã phân biệt chữ hoa/thường, dùng thứ tự tự nhiên của `String`. Với hai chuỗi `"Java"` và `"JAVA"`, `String.compareTo` không trả `0`, còn `String.equals` cũng `false`. Trong trường hợp này, thứ tự và quan hệ bằng nhau nhất quán theo đúng kiểu `String`.

### Trường hợp B: username không phân biệt case

Nếu username không phân biệt case, có thể dùng comparator phù hợp để thực hiện thao tác trong một `TreeMap<String,V>`, nhưng toàn hệ thống phải nhận thức rằng `String.equals` vẫn phân biệt case. Nếu cùng lúc dùng `HashMap<String,V>`, hành vi trùng khóa sẽ khác.

Một thiết kế khác là **chuẩn hóa khóa** theo chính sách đã công bố (ví dụ lowercase trong miền ký tự ASCII hợp lệ), lưu khóa chuẩn hóa trong một value object có `equals`/`hashCode` tương ứng, và dùng comparator nhất quán với chính quan hệ đó. Cách này giúp các cấu trúc băm và sắp xếp có cùng khái niệm danh tính khi ứng dụng thật sự cần thế.

Với văn bản Unicode nhiều ngôn ngữ, đừng giả định một quy tắc `equalsIgnoreCase` hoặc `toLowerCase(Locale.ROOT)` sẽ xử lý mọi trường hợp Unicode case folding, chuẩn hóa dạng tổ hợp, accent hoặc locale. Quy tắc so sánh và canonicalization phải được thiết kế, ghi rõ và kiểm thử.

### Trường hợp C: đối tượng có nhiều trường

Nếu domain record `Person(name, age)` dùng `equals` của cả hai component, comparator xét `age` rồi `name` sẽ phù hợp với **ví dụ dùng String và int không-null** nếu comparator và `equals` cùng phân biệt tên giống nhau. Nhưng nếu thêm component khác vào record, `equals` sinh tự động thay đổi mà comparator không cập nhật, tính nhất quán có thể mất.

Thay vì “sắp theo tuổi” bằng comparator để làm khóa duy nhất, cân nhắc dùng `TreeMap<Integer, List<Person>>` khi nghiệp vụ muốn **nhóm nhiều người theo tuổi**, hoặc sử dụng một khóa duy nhất đủ tiêu chí với `thenComparing` khi muốn một `TreeSet<Person>` không gộp người.

## 10. Các phép tra cứu, giới hạn khoảng và first/last đều tuân comparator

`TreeSet` và `TreeMap` thuộc họ cấu trúc có thứ tự, nên các thao tác sau cũng dựa trên quy tắc so sánh:

- `first()`/`last()` của `TreeSet`, `firstKey()`/`lastKey()` của `TreeMap` lấy cực trị **theo comparator hoặc natural ordering**, không theo thời gian thêm hay theo `equals`.
- `lower`, `floor`, `ceiling`, `higher` tìm phần tử nằm trước/gần trước/gần sau/sau theo thứ tự, với các quy tắc bao gồm hoặc loại trừ điểm ranh giới.
- `subSet`, `headSet`, `tailSet` và `subMap`, `headMap`, `tailMap` xác định các vùng khóa bằng **thứ tự comparator**. Các view này gắn với cấu trúc cơ sở và phản ánh những thay đổi được phép.
- `contains`/`containsKey` kiểm tra xem có phần tử/khóa **tương đương theo phép so sánh** không, chứ không nhất thiết gọi `equals` để xác nhận như cấu trúc băm.

Đối với comparator không phân biệt case, ranh giới truy vấn `"JAVA"` có thể tương đương với khóa đang lưu `"Java"`. Việc một đầu khoảng có được bao gồm phụ thuộc overload và cờ inclusive/exclusive, còn kết quả so sánh `0` quyết định chúng thuộc cùng mốc thứ tự.

### Độ phức tạp và nhu cầu sử dụng

`TreeMap` bảo đảm thời gian `O(log n)` cho các thao tác `containsKey`, `get`, `put` và `remove`, giả sử chi phí của một phép so sánh được xem là hằng số. `TreeSet` có thời gian `O(log n)` bảo đảm cho `add`, `remove` và `contains` theo cùng giả định. Nếu comparator tốn kém, thời gian thực còn phụ thuộc chi phí so sánh.

`HashMap`/`HashSet` thường cho thời gian kỳ vọng gần `O(1)` với phân phối hash phù hợp, nhưng không cung cấp thứ tự sắp xếp theo comparator. Nếu cần duyệt theo thứ tự, truy vấn khoảng hoặc tìm phần tử kế cận, cây có thứ tự đem lại khả năng khác biệt.

## 11. Khóa mutable, null và lỗi trong so sánh

### 11.1. Khóa thay đổi sau khi thêm vào cây

Ví dụ `TreeSet<Person>` sắp theo `person.age`. Nếu lưu đối tượng `Person` mutable rồi sửa tuổi mà không xóa và chèn lại, thứ tự nội tại của cây vẫn được xây theo giá trị cũ. Kết quả `contains`, `remove`, truy vấn khoảng hoặc thứ tự duyệt có thể bất thường.

Tương tự với `TreeMap` nếu khóa mutable thay đổi trường được comparator dùng. **Cấu trúc cây không tự theo dõi thay đổi trong đối tượng khóa**. Nên dùng khóa bất biến; khi cần thay đổi khóa logic, thường phải xóa mục nhập theo khóa cũ và thêm lại bằng khóa mới.

### 11.2. Null và khóa không thể so sánh

Khi dùng **natural ordering**, `TreeSet`/`TreeMap` thường không chấp nhận khóa `null` vì không thể so sánh tự nhiên, và có thể ném `NullPointerException`. Với comparator riêng có xử lý `null` (ví dụ `Comparator.nullsFirst`) và chính sách phù hợp, có thể cho phép khóa `null` trong `TreeMap`/`TreeSet`.

`TreeMap` có thể cho phép **giá trị** `null`, độc lập với vấn đề khóa `null`. Khi `get(key)` trả `null`, muốn phân biệt không có khóa với có khóa nhưng giá trị `null`, dùng `containsKey(key)`.

Nếu dùng natural ordering mà các đối tượng không triển khai `Comparable` phù hợp, hoặc chúng không so sánh được với nhau, thao tác trên tree có thể ném `ClassCastException`. Comparator nên xác định rõ miền kiểu và không đưa các kiểu không tương thích vào cấu trúc.

### 11.3. Comparator có tác dụng phụ

Không để `compare` thay đổi trạng thái khóa, ghi database, gọi dịch vụ ngoài hoặc thay đổi kết quả theo lần gọi. Cấu trúc có thể cần nhiều phép so sánh trong một thao tác, và không có hợp đồng rằng comparator chỉ được gọi đúng một lần cho mỗi cặp.

Comparator sử dụng thuộc tính phụ thuộc môi trường (timezone, locale hiện hành có thể đổi, trạng thái cấu hình thay đổi) cũng khiến thứ tự thiếu ổn định. Xác định chính sách so sánh nhất quán trong suốt thời gian dùng cấu trúc; nếu thay đổi chính sách, xây lại cấu trúc theo comparator mới.

## 12. Những lỗi thường gặp

| Lỗi | Tác động quan sát được | Cách giải quyết |
| --- | --- | --- |
| Tưởng `TreeSet` chỉ loại trùng khi `equals` đúng | Có thể mất các đối tượng khác nhau nếu comparator trả `0`. | Kiểm tra `compare(a,b) == 0` có phản ánh đúng danh tính logic không. |
| Dùng comparator chỉ theo một trường như tuổi | Mọi đối tượng cùng tuổi bị xem là một phần tử, dù tên/ID khác. | Thêm tiêu chí phân biệt phù hợp hoặc dùng `TreeMap<Age,List<Person>>` để nhóm. |
| Gọi `TreeMap.put(k2,v2)` với khóa cùng comparator và nghĩ sẽ thêm mục mới | Giá trị cũ bị thay thế, map không tăng kích thước. | Đặc tả trùng khóa; dùng đủ tiêu chí khóa. |
| Nghĩ `TreeSet` và `HashSet` luôn có cùng số phần tử trên một bộ dữ liệu | `compare==0` có thể không tương ứng với `equals`. | Chọn bộ so sánh nhất quán hoặc chấp nhận chủ ý sự khác biệt. |
| So sánh chỉ với `result == -1`/`result == 1` | Hiểu sai kết quả comparator hợp lệ trả trị tuyệt đối lớn hơn 1. | So dấu âm/0/dương. |
| Viết comparator bằng phép trừ số nguyên | Tràn số có thể làm sai thứ tự, phá hợp đồng. | Dùng `Integer.compare`, `Long.compare` hoặc comparator built-in. |
| Comparator dùng trường mutable hoặc phụ thuộc đồng hồ | Cây có thể không tìm được khóa hoặc duyệt theo thứ tự sai. | Dùng khóa bất biến và chính sách so sánh ổn định. |
| Dùng `String.CASE_INSENSITIVE_ORDER` cho mọi chuỗi Unicode và kỳ vọng quy tắc locale cụ thể | Có thể gộp hoặc phân biệt dữ liệu ngoài mong muốn. | Định nghĩa chuẩn hóa/locale rõ và kiểm thử với dữ liệu thật. |
| Dùng `BigDecimal` rồi bất ngờ khi `TreeSet` và `HashSet` khác nhau | `compareTo` bỏ qua khác biệt scale mà `equals` xét đến. | Quy định khóa theo giá trị số hay theo biểu diễn; chuẩn hóa khi phù hợp. |
| Mong `hashCode` ảnh hưởng đến `TreeMap` | Cây định hướng theo comparator, hash không quyết định nhánh. | Dùng đúng mô hình cơ chế của collection. |
| Đổi comparator sau khi đã lưu phần tử | Thứ tự trong cây không tự cập nhật theo policy mới. | Tạo cấu trúc mới với comparator mới rồi nạp lại dữ liệu. |

## 13. Kiểm thử để xác nhận hợp đồng và chính sách nghiệp vụ

Khi thiết kế comparator cho một kiểu khóa riêng, cần kiểm tra **thứ tự** và **ngữ nghĩa trùng khóa**:

1. **Dấu so sánh:** nếu `compare(a,b) < 0`, thì `compare(b,a) > 0`. Kiểm tra cả trường hợp bằng `0` và các giá trị biên.
2. **Bắc cầu:** với nhiều bộ ba, nếu `a < b` và `b < c` theo comparator, thì `a < c`. Cả các bộ ba có giá trị comparator `0` cũng cần nhất quán.
3. **Tương đương qua comparator:** nếu `compare(a,b) == 0`, kiểm tra truy vấn `contains(b)`/`get(b)` khi đã lưu `a`; xác nhận behavior đúng nghiệp vụ.
4. **Nhất quán với `equals`:** với các cặp cùng ID, khác ID, cùng tiêu chí phụ và khác tiêu chí phụ, so cả `compare==0` lẫn `equals`. Nếu hai quan hệ khác nhau, tài liệu phải ghi rõ vì sao.
5. **Khả năng trùng:** thêm hai khóa cùng comparator trong `TreeSet` phải không tăng kích thước; `TreeMap.put` phải cập nhật giá trị tương ứng.
6. **Khác biệt với cấu trúc băm:** khi mong đợi semantics giống nhau, dùng cùng dữ liệu để so kích thước/khóa giữa `TreeSet` và `HashSet`; nếu khác nhau, tìm cặp khiến hai quan hệ bất đồng.
7. **Khóa mutable:** đảm bảo thuộc tính dùng để so sánh không bị sửa trong lúc còn ở tree. Nếu có giao diện thay khóa, test quy trình xóa khóa cũ và thêm khóa mới.
8. **Các biên null và kiểu:** kiểm tra chính sách `null`, giá trị đầu khoảng, ký tự Unicode, case và các mức số học lớn/nhỏ.
9. **Thao tác vùng khóa:** test `floor`, `ceiling`, `subSet`/`subMap` với khóa so sánh bằng `0` để bảo đảm ranh giới đúng.
10. **Chi phí và ổn định:** comparator phải có tính xác định, không dựa I/O và không đổi kết quả trong cùng trạng thái khóa.

Với domain quan trọng, có thể dùng property-based tests sinh nhiều bộ đôi/bộ ba và kiểm thử cả tính chất quan hệ thứ tự. Một ví dụ đơn lẻ chạy đúng chưa chứng minh toàn bộ comparator có hợp đồng hợp lệ.

## 14. Ghi nhớ

`TreeSet` và `TreeMap` dùng **thứ tự tự nhiên hoặc `Comparator`** để vừa tìm vị trí trong cây vừa **xác nhận khóa tương đương bằng kết quả so sánh `0`**. Đây là điểm khác với `HashSet` và `HashMap` vốn dựa trên hợp đồng `equals`/`hashCode`. Khi `compare(a,b)==0` không trùng với `a.equals(b)`, các collection có thể cho số phần tử và kết quả tra cứu khác nhau; ví dụ điển hình là chuỗi so không phân biệt case và `BigDecimal` khác scale. Hãy xác định rõ danh tính khóa, chọn comparator đáp ứng quan hệ đó, đảm bảo thứ tự hợp lệ và ổn định, ưu tiên khóa bất biến, rồi kiểm thử cả hành vi thêm, tra cứu, ghi đè và truy vấn khoảng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ordered-collections-002">Hai đối tượng equals() false nhưng Comparator trả về 0</a>

<details>
<summary>Xem chi tiết</summary>

# Hai đối tượng equals() false nhưng Comparator trả về 0

## 1. Vấn đề: “khác nhau” theo equals nhưng “tương đương” theo thứ tự

Trong Java, hai cách kiểm tra sau có thể cho kết quả khác nhau:

- `a.equals(b)`: hai đối tượng có **bằng nhau theo hợp đồng bình đẳng của lớp** hay không?
- `comparator.compare(a, b) == 0`: hai đối tượng có **ngang hạng (tương đương) theo tiêu chí sắp xếp** hay không?

Ví dụ hai sản phẩm cùng mã `"P-01"` nhưng khác phiên bản `"red"` và `"blue"`. Nếu phương thức `equals` xem cặp **(mã, phiên bản)** là danh tính, hai sản phẩm đó **không bằng nhau**. Nhưng nếu comparator chỉ xét **mã**, nó sẽ trả về `0` vì cả hai cùng `"P-01"`.

~~~text
a = Product(sku="P-01", variant="red")
b = Product(sku="P-01", variant="blue")

a.equals(b)                   -> false
compareBySku.compare(a, b)    -> 0
~~~

**Không có mâu thuẫn cú pháp:** hai phép so sánh đang trả lời hai câu hỏi khác nhau. Tuy nhiên, sự khác biệt có hậu quả lớn trong các collection có thứ tự như `TreeSet` và `TreeMap`, vì chúng dùng **kết quả so sánh thứ tự bằng 0** để nhận biết phần tử/khóa trùng nhau.

## 2. equals và Comparator định nghĩa hai quan hệ riêng

`equals(Object)` tuân hợp đồng bình đẳng của `Object`: phản xạ, đối xứng, bắc cầu, nhất quán và `x.equals(null) == false` với `x` khác `null`. Khi ghi đè `equals`, lớp cũng cần ghi đè `hashCode` tương thích: hai đối tượng bằng nhau phải có cùng mã băm.

`Comparator<T>` định nghĩa **một thứ tự** trên các đối tượng kiểu `T`. Kết quả âm nghĩa là đứng trước, kết quả dương nghĩa là đứng sau, còn `0` nghĩa là hai đối tượng **không phân biệt được theo thứ tự mà comparator đã chọn**. Nó không tự gọi `equals` để kiểm tra liệu hai đối tượng có giống nhau ở mọi trường hay không.

| Tình huống | equals | Comparator | Diễn giải |
| --- | --- | --- | --- |
| Cùng sku, khác variant; equals xét cả hai, comparator chỉ xét sku | false | 0 | Khác đối tượng theo giá trị đầy đủ, ngang hạng khi xếp theo sku |
| Cùng sku và variant | true | 0 | Bình đẳng và tương đương thứ tự trùng nhau |
| Khác sku | false | Âm hoặc dương | Comparator phân biệt được theo sku |
| Comparator xét (sku, variant), equals cũng xét (sku, variant) | false khi khác variant | Khác 0 khi khác variant | Hai quan hệ được thiết kế nhất quán |

Java khuyến nghị comparator có thứ tự **nhất quán với equals** khi dùng trong `SortedSet`/`SortedMap`: 

~~~text
compare(a, b) == 0    khi và chỉ khi    a.equals(b) == true
~~~

Đây là điều kiện **đồng nhất quan hệ tương đương**, không phải yêu cầu mọi comparator trong mọi ngữ cảnh đều phải xét toàn bộ trường của đối tượng. Một comparator dùng để **nhóm, sắp xếp hiển thị hoặc xếp theo khóa phụ** có thể chủ đích trả về 0 cho hai đối tượng `equals` khác nhau. Chỉ cần hiểu rằng khi đưa comparator đó vào `TreeSet`/`TreeMap`, bạn đang áp dụng một định nghĩa khác về “khóa trùng”.

Một thứ tự hợp lệ còn cần tuân tính đổi chiều dấu, tính bắc cầu và sự nhất quán của nhóm kết quả bằng 0. Không thể chữa nhầm lẫn về bình đẳng bằng một comparator tùy tiện vi phạm hợp đồng thứ tự.

## 3. Vì sao TreeSet coi hai đối tượng khác nhau là một?

`HashSet` sử dụng `hashCode` kết hợp với `equals` để quản lý phần tử. Trong khi đó, `TreeSet` sử dụng **thứ tự tự nhiên** (`Comparable`) hoặc **comparator được cung cấp** để sắp xếp và xác định hai phần tử có trùng nhau trong cây hay không.

Giả sử:

~~~text
a.equals(b) == false
comparator.compare(a, b) == 0
~~~

Nếu thêm `a` trước rồi thêm `b`:

1. `TreeSet` chèn `a`.
2. Khi xét `b`, comparator trả về `0` so với phần tử khóa tương ứng. `TreeSet` coi `b` là tương đương với phần tử đã có và không thêm một phần tử thứ hai.
3. `set.size()` vẫn bằng `1`.
4. `set.contains(b)` có thể trả về `true` mặc dù `a.equals(b)` là `false`: phép tra cứu của cây dựa vào comparator.

Khi comparator không nhất quán với `equals`, Java vẫn có thể thực thi những thao tác theo quy tắc so sánh đã cho, nhưng `TreeSet` **không còn giữ đúng kỳ vọng của hợp đồng `Set` tổng quát về bình đẳng phần tử**. Đây là nguyên nhân của nhiều lỗi dữ liệu bị “mất” khi chuyển từ `HashSet` sang `TreeSet`.

Đặc biệt, **kết quả `contains(b)` không chứng minh đã có đúng đối tượng `b` trong cây**. Nó chỉ cho biết có một phần tử được comparator xem là tương đương với `b`.

## 4. Ví dụ Java 17 chạy được với TreeSet và TreeMap

Chương trình dùng một `record Product` với hai thành phần `sku` và `variant`. Record tự sinh `equals` và `hashCode` dựa trên **cả hai thành phần**. Comparator `BY_SKU` chỉ so `sku`; comparator `BY_SKU_AND_VARIANT` so tiếp `variant` khi sku giống nhau.

Lưu thành **ComparatorZeroDemo.java**:

~~~java
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class ComparatorZeroDemo {
    record Product(String sku, String variant) {
        Product {
            Objects.requireNonNull(sku, "sku");
            Objects.requireNonNull(variant, "variant");
        }
    }

    static final Comparator<Product> BY_SKU =
            Comparator.comparing(Product::sku);

    static final Comparator<Product> BY_SKU_AND_VARIANT =
            Comparator.comparing(Product::sku)
                    .thenComparing(Product::variant);

    public static void main(String[] args) {
        Product red = new Product("P-01", "red");
        Product blue = new Product("P-01", "blue");
        Product other = new Product("P-02", "black");

        System.out.println("equals red/blue: " + red.equals(blue));
        System.out.println("compare by sku zero: "
                + (BY_SKU.compare(red, blue) == 0));
        System.out.println("compare by full key zero: "
                + (BY_SKU_AND_VARIANT.compare(red, blue) == 0));

        Set<Product> hash = new HashSet<>();
        hash.add(red);
        hash.add(blue);
        System.out.println("HashSet size: " + hash.size());

        Set<Product> bySkuSet = new TreeSet<>(BY_SKU);
        System.out.println("TreeSet add red: " + bySkuSet.add(red));
        System.out.println("TreeSet add blue: " + bySkuSet.add(blue));
        System.out.println("TreeSet add other: " + bySkuSet.add(other));
        System.out.println("TreeSet size: " + bySkuSet.size());
        System.out.println("TreeSet contains blue: "
                + bySkuSet.contains(blue));
        System.out.println("TreeSet contains red: "
                + bySkuSet.contains(red));

        Map<Product, String> map = new TreeMap<>(BY_SKU);
        map.put(red, "red-value");
        map.put(blue, "blue-value");
        System.out.println("TreeMap size: " + map.size());
        System.out.println("TreeMap get red: " + map.get(red));
        System.out.println("TreeMap get blue: " + map.get(blue));

        Set<Product> fullSet = new TreeSet<>(BY_SKU_AND_VARIANT);
        fullSet.add(red);
        fullSet.add(blue);
        System.out.println("Full-key TreeSet size: " + fullSet.size());

        Map<Product, String> fullMap =
                new TreeMap<>(BY_SKU_AND_VARIANT);
        fullMap.put(red, "red-value");
        fullMap.put(blue, "blue-value");
        System.out.println("Full-key TreeMap size: " + fullMap.size());
    }
}
~~~

Dùng JDK 17 trở lên:

~~~bash
javac ComparatorZeroDemo.java
java ComparatorZeroDemo
~~~

Kết quả chuẩn:

~~~text
equals red/blue: false
compare by sku zero: true
compare by full key zero: false
HashSet size: 2
TreeSet add red: true
TreeSet add blue: false
TreeSet add other: true
TreeSet size: 2
TreeSet contains blue: true
TreeSet contains red: true
TreeMap size: 1
TreeMap get red: blue-value
TreeMap get blue: blue-value
Full-key TreeSet size: 2
Full-key TreeMap size: 2
~~~

### Giải thích kết quả từng bước

- `red.equals(blue)` là `false`: record so cả `sku` và `variant`; trường `variant` khác nhau.
- `BY_SKU.compare(red, blue) == 0` là `true`: comparator chỉ nhìn vào `sku`. Khi thêm `thenComparing(Product::variant)`, hai khóa được phân biệt nên kết quả **khác 0**.
- `HashSet` giữ cả `red` lẫn `blue` vì chúng khác nhau theo `equals`; kích thước bằng `2`.
- `TreeSet` với `BY_SKU` chấp nhận `red` nhưng từ chối lần thêm `blue`, vì cả hai tương đương theo thứ tự. `other` có sku khác nên được thêm. Kích thước cũng là `2`, nhưng **tập phần tử khác với HashSet**: hai phần tử ở đây là `red` và `other`.
- `TreeSet.contains(blue)` trả `true` vì có `red` cùng sku; kết quả đó không khẳng định đối tượng `blue` đã được chèn thành công.
- `TreeMap` coi hai `Product` cùng sku là một khóa. `put(blue, "blue-value")` cập nhật giá trị của mục đã được tạo bằng `red`, khiến kích thước còn `1`. Khi truy vấn theo `red` hoặc `blue`, đều nhận `"blue-value"`.
- Comparator đầy đủ `BY_SKU_AND_VARIANT` phân biệt cả hai, nên `TreeSet` và `TreeMap` cho phép giữ riêng hai mục.

Ví dụ không đưa các trường mutable vào khóa; record có hai trường `String` là bất biến. Nhờ đó, kết quả không bị ảnh hưởng bởi việc sửa khóa sau khi chèn.

## 5. TreeMap xử lý “trùng khóa” theo comparator thế nào?

Tương tự `TreeSet`, `TreeMap` sử dụng comparator (hoặc thứ tự tự nhiên của khóa) để tổ chức cây và tìm một khóa tương đương. Nếu `compare(k1, k2) == 0`, hai khóa được coi là cùng vị trí tra cứu. Khi `put(k2, value2)` sau `put(k1, value1)`, thao tác thông thường cập nhật **giá trị cho khóa tương đương** thay vì tạo mục thứ hai.

Hệ quả:

| Thao tác | Comparator chỉ xét sku | Ý nghĩa |
| --- | --- | --- |
| `put(red, "red-value")` | Thêm mục sku P-01 | Giá trị ban đầu |
| `put(blue, "blue-value")` | Cập nhật mục cùng sku | Không tạo khóa thứ hai |
| `size()` | 1 | Chỉ một lớp tương đương theo sku |
| `get(red)` | `"blue-value"` | Giá trị đã cập nhật |
| `get(blue)` | `"blue-value"` | Cùng lớp tương đương theo comparator |

Điều này nguy hiểm nếu nghiệp vụ cần phân biệt từng `variant`. Thao tác ghi có thể vô tình thay thế dữ liệu trước đó dù `equals` cho biết hai khóa khác nhau. Nếu thực sự muốn mỗi sku chỉ có **một** bản ghi, comparator theo sku có thể phù hợp, nhưng lúc đó nên mô hình hóa khóa trực tiếp là `String sku` hoặc một kiểu khóa chỉ chứa sku để quy tắc dễ hiểu hơn.

Đừng dựa vào thứ tự lặp để phân biệt hai khóa có comparator trả về 0: theo chính sách của cây, chúng vốn là một mục. Muốn lưu nhiều phiên bản dưới cùng sku, có thể dùng `Map<String, List<Product>>`, `Map<String, Set<Product>>` hoặc khóa ghép gồm `(sku, variant)` theo nhu cầu nghiệp vụ.

## 6. Khi nào comparator trả về 0 cho đối tượng equals false là có chủ ý?

Không phải comparator như vậy luôn là lỗi. Một comparator xếp hạng học viên **chỉ theo điểm** có thể coi hai học viên cùng điểm là ngang hạng, dù họ là hai người khác nhau theo `equals`. Khi chỉ dùng comparator để nhóm hoặc sắp xếp danh sách theo điểm, việc ngang hạng có ý nghĩa rõ ràng.

Ví dụ:

~~~java
Comparator<Student> byScore =
        Comparator.comparingInt(Student::score);
~~~

Đây là **khai báo minh họa** với lớp `Student` giả định có phương thức `score()`, không phải chương trình độc lập. Nó cho `0` khi hai học viên cùng điểm. Nếu dùng với `List.sort(byScore)`, danh sách vẫn chứa cả hai học viên; phép sắp xếp không tự xóa phần tử. Nhưng nếu chuyển danh sách đó vào `TreeSet<>(byScore)`, tập sẽ chỉ giữ một phần tử trong mỗi nhóm được xem là tương đương theo điểm.

Tương tự, `String.CASE_INSENSITIVE_ORDER.compare("Java", "java") == 0`, trong khi `"Java".equals("java")` là `false`. Comparator không phân biệt chữ hoa/thường có thể phù hợp cho sắp xếp hiển thị hoặc tìm kiếm không phân biệt hoa/thường, nhưng `TreeSet` dùng comparator đó sẽ không giữ cả hai chuỗi như hai phần tử riêng biệt.

Do đó, trước khi sử dụng một comparator hãy tự hỏi: **tôi đang sắp xếp để hiển thị, đang gom nhóm theo một tiêu chí, hay đang định nghĩa danh tính duy nhất của phần tử/khóa trong một collection có thứ tự?** Ba mục đích này có thể cần ba chính sách khác nhau.

## 7. Cách thiết kế Comparator đúng khi dùng TreeSet/TreeMap

### 7.1. Thêm khóa phụ để phân biệt đối tượng cần giữ riêng

Nếu `equals` của `Product` xét sku và variant, nên dùng:

~~~java
Comparator<Product> byFullKey =
        Comparator.comparing(Product::sku)
                .thenComparing(Product::variant);
~~~

Đây là **biểu thức khai báo comparator trong ngữ cảnh có kiểu Product như chương trình chính**, không phải chương trình độc lập. Khi cả hai chuỗi bằng nhau thì `compare == 0`; với record `Product` đã cho, điều đó cũng đồng nghĩa `equals == true`. Khóa phụ khiến những sản phẩm khác phiên bản không bị nhập thành một mục.

Nếu `equals` còn so nhiều trường hơn, comparator muốn nhất quán phải bảo đảm **không trả 0 cho hai đối tượng mà equals xem là khác nhau**, và cũng không tách hai đối tượng bằng nhau thành hai hạng. Vì thế không thể tự ý chọn một khóa phụ ngẫu nhiên mà bỏ qua những trường bình đẳng thực tế.

### 7.2. Dùng đúng loại collection theo hợp đồng mong muốn

- Cần nhận diện phần tử bằng `equals` và không cần duy trì thứ tự sắp: sử dụng `HashSet`/`HashMap` với `equals` và `hashCode` được cài đặt đúng.
- Cần phần tử/khóa được duyệt theo thứ tự, đồng thời phân biệt đầy đủ các đối tượng theo `equals`: sử dụng `TreeSet`/`TreeMap` với comparator nhất quán với `equals`.
- Cần giữ tất cả đối tượng nhưng hiển thị theo một thuộc tính: lưu trong `List` rồi `sort` bằng comparator theo thuộc tính đó; việc sắp danh sách không loại bỏ các phần tử ngang hạng.
- Cần nhóm nhiều đối tượng theo một thuộc tính: dùng cấu trúc ánh xạ từ thuộc tính nhóm tới một danh sách/tập các phần tử, thay vì dùng một `TreeMap` một-giá-trị rồi vô tình ghi đè nhóm.

`TreeSet` và `TreeMap` yêu cầu comparator cung cấp thứ tự ổn định, đổi chiều đúng dấu và có tính bắc cầu. Việc thêm tie-breaker phải giữ được các tính chất này; `Comparator.comparing(...).thenComparing(...)` với các khóa có thứ tự chuẩn thường dễ kiểm chứng hơn logic thủ công.

### 7.3. Giữ khóa sắp xếp ổn định

Nếu sửa trường tham gia comparator sau khi đưa đối tượng vào `TreeSet` hoặc làm khóa `TreeMap`, cây không tự tái cấu trúc theo giá trị mới. Có thể xảy ra lỗi `contains`, `get` hoặc việc duyệt không còn theo thứ tự mong muốn. Hãy dùng khóa bất biến, hoặc xóa khóa khỏi cấu trúc trước khi thay đổi rồi chèn lại dưới trạng thái mới.

Lưu ý phân biệt: `HashSet` dựa vào tính ổn định của `equals`/`hashCode`; `TreeSet` còn dựa vào tính ổn định của tiêu chí so sánh thứ tự. Một lớp có mã băm ổn định nhưng comparator đọc một trường thay đổi vẫn có rủi ro trong `TreeSet`.

## 8. Những lỗi thường gặp

### Lỗi 1: Giả định TreeSet dùng equals để kiểm tra trùng

Khi `comparator.compare(a, b) == 0`, `TreeSet` xem hai phần tử là tương đương dù `a.equals(b)` là `false`. Kiểm tra `set.contains(b)` có thể trả `true` trong tình huống chưa từng chèn `b` thành công.

### Lỗi 2: Chỉ so khóa chính khi khóa thực tế gồm nhiều trường

Nếu một bản ghi định danh bằng `(userId, orderId)` mà comparator chỉ so `userId`, các đơn hàng của cùng một người dùng sẽ bị gom thành một khóa. Hãy định nghĩa rõ khóa ghép và áp dụng `thenComparing` theo thứ tự ưu tiên thích hợp.

### Lỗi 3: Cố ép comparator trả 1 khi hai đối tượng khác tham chiếu

Một cách “chữa cháy” như `if (a != b) return 1;` không bảo đảm dấu đổi chiều: với hai đối tượng khác nhau có thể cả `compare(a,b)` và `compare(b,a)` đều dương. Đây là vi phạm hợp đồng Comparator. Cần một tiêu chí phụ **có thứ tự xác định**, không phải giá trị trả về tùy tiện.

### Lỗi 4: Thêm tham số ngẫu nhiên hoặc thời gian vào kết quả

Nếu comparator trả kết quả khác nhau cho cùng cặp đối tượng qua các lần gọi, cây sắp xếp có thể không tìm đúng vị trí lưu. Tiêu chí thứ tự phải nhất quán theo trạng thái được phép của đối tượng, và tốt nhất là dựa trên khóa bất biến.

### Lỗi 5: Dùng comparator theo nhóm rồi cho rằng mọi phần tử đã được giữ nguyên

`TreeSet` chỉ lưu một đại diện cho mỗi nhóm tương đương theo comparator. `TreeMap` chỉ lưu một mục cho mỗi khóa tương đương. Muốn giữ toàn bộ thành viên một nhóm, hãy dùng `List`, hoặc `Map<GroupKey, List<Member>>` để biểu diễn rõ quan hệ một–nhiều.

## 9. Kiểm thử trước khi đưa Comparator vào SortedSet hoặc SortedMap

Với các giá trị khác nhau về nghiệp vụ, thử những ca sau:

1. Chọn hai đối tượng `a`, `b` mà `!a.equals(b)`. So kết quả `compare(a,b)`; nếu là `0`, xác định xem việc coi chúng là một khóa trong cây có chủ ý hay không.
2. Chọn hai đối tượng bằng nhau theo `equals`. Nếu cần tương thích hợp đồng `Set`/`Map`, xác nhận `compare(a,b) == 0`.
3. Thử `TreeSet.add(a)`, `TreeSet.add(b)`, `size()` và `contains(b)`; đừng suy luận sự tồn tại riêng biệt chỉ dựa vào `contains`.
4. Thử `TreeMap.put(a,v1)`, `put(b,v2)`, `size()` và `get`; xác định rõ có thể ghi đè giá trị vì comparator coi hai khóa là một hay không.
5. Thêm tie-breaker cần thiết và kiểm tra lại với các đối tượng cùng khóa chính nhưng khác khóa phụ.
6. Kiểm tra tính bắc cầu, đổi chiều dấu và ổn định của comparator; đặc biệt với giá trị biên, thuộc tính nullable và khóa mutable.
7. Kiểm tra mọi trường hợp `null`: `Comparator.nullsFirst`/`nullsLast` khi muốn hỗ trợ đối tượng hoặc khóa `null` phù hợp với collection, nhưng lưu ý **TreeMap/TreeSet có thể không chấp nhận null tùy comparator**; phải kiểm tra chính sách thực tế thay vì suy đoán.

**Ghi nhớ:** `a.equals(b) == false` và `comparator.compare(a,b) == 0` hoàn toàn có thể cùng đúng vì hai phép so sánh có mục đích khác nhau. Nhưng khi comparator được dùng bởi `TreeSet` hoặc `TreeMap`, **0 nghĩa là cùng một phần tử/khóa theo cây**. Nếu cần giữ hai đối tượng như hai giá trị riêng biệt, hãy định nghĩa tiêu chí thứ tự phù hợp với `equals`, thường bằng cách thêm các khóa phụ cần thiết.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ordered-collections-003">Hai đối tượng equals() true nhưng Comparator phân biệt</a>

<details>
<summary>Xem chi tiết</summary>

# Hai đối tượng `equals()` true nhưng `Comparator` phân biệt

Một tình huống dễ gây bất ngờ trong Java: **hai đối tượng được `equals()` coi là bằng nhau, nhưng `Comparator.compare(a, b)` lại trả về số khác `0`**. Khi đưa chúng vào `HashSet`, có thể chỉ còn một phần tử; khi đưa vào `TreeSet` dùng comparator đó, cả hai lại cùng tồn tại.

Đây không phải vì `TreeSet` bỏ qua việc kiểm tra khóa trùng do lỗi. **`TreeSet` xác định phần tử tương đương bằng kết quả so sánh thứ tự**, còn `HashSet` xác định phần tử bằng `equals` và `hashCode`. Nếu hai định nghĩa về sự tương đương không thống nhất, hai cấu trúc có thể cho kết quả khác nhau. Với một `SortedSet` muốn tuân thủ đầy đủ hợp đồng chung của `Set`, thứ tự dùng để xác định trùng phần tử **phải nhất quán với `equals`**.

Bài học này tập trung vào chiều dễ bỏ sót: **`a.equals(b) == true` nhưng `comparator.compare(a, b) != 0`**, lý do gây ra, hệ quả khi dùng tập hợp/bản đồ có thứ tự, và cách thiết kế để tránh lỗi nghiệp vụ.

## 1. Hai quan hệ “bằng nhau” khác nhau

Trong Java có hai câu hỏi riêng:

- `a.equals(b)`: hai đối tượng có được kiểu dữ liệu xem là **cùng giá trị logic** không?
- `comparator.compare(a, b)`: hai đối tượng **đứng trước, tương đương, hay đứng sau** nhau theo một quy tắc sắp xếp cụ thể?

`Comparator<T>.compare(a, b)` trả về số âm nếu `a` đứng trước `b`, `0` nếu chúng tương đương theo **thứ tự**, hoặc số dương nếu `a` đứng sau `b`. Không phải mọi comparator đều phải dùng cùng các trường với `equals`. Điều đó có thể phù hợp khi **chỉ sắp xếp `List`**. Nhưng khi comparator làm quy tắc phân biệt key trong `TreeSet` hoặc `TreeMap`, sự khác biệt có thể thay đổi ý nghĩa của “trùng”.

Một quan hệ thứ tự **nhất quán với `equals`** khi, với hai đối tượng hợp lệ:

~~~text
comparator.compare(a, b) == 0  ⇔  a.equals(b) == true
~~~

Dấu `⇔` nghĩa là **hai chiều đều phải đúng**: `equals` bằng nhau thì `compare` bằng `0`, và `compare` bằng `0` thì `equals` bằng nhau. Bảng phân biệt:

| `a.equals(b)` | `compare(a, b) == 0` | Diễn giải |
| --- | --- | --- |
| `true` | `true` | Hai quan hệ cùng coi đối tượng là tương đương. |
| `false` | `false` | Hai quan hệ cùng phân biệt đối tượng. |
| **`true`** | **`false`** | **Trường hợp trọng tâm:** `equals` gộp, comparator lại tách. |
| `false` | `true` | Chiều ngược lại: comparator gộp hai đối tượng `equals` xem là khác. |

Một comparator **không nhất quán với `equals`** chưa hẳn là comparator sai về thứ tự. Nó vẫn có thể hợp lệ cho việc sắp xếp danh sách. Sự rủi ro xuất hiện khi đem thứ tự đó làm cơ sở xác định **tính duy nhất** trong tập hợp có thứ tự hoặc key trong bản đồ có thứ tự.

## 2. Ví dụ Java 17+: `equals` true, `compare` khác 0

Giả sử `Item` có `id` và `revision`:

- Hai `Item` có **cùng `id`** được hệ thống coi là cùng thực thể logic. Vì thế `equals` và `hashCode` chỉ xét `id`.
- Một màn hình lại muốn sắp xếp theo `id`, rồi theo `revision` để biết phiên bản nào đứng trước. Comparator này xét **cả hai trường**.

Tệp đầy đủ **`ComparatorEqualsMismatchDemo.java`**, biên dịch và chạy trên Java 17+:

~~~java
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class ComparatorEqualsMismatchDemo {
    static final class Item {
        private final int id;
        private final int revision;

        Item(int id, int revision) {
            this.id = id;
            this.revision = revision;
        }

        int id() {
            return id;
        }

        int revision() {
            return revision;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Item that)) {
                return false;
            }
            return id == that.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }

        @Override
        public String toString() {
            return "Item(" + id + "," + revision + ")";
        }
    }

    public static void main(String[] args) {
        Item first = new Item(7, 1);
        Item second = new Item(7, 2);
        Item query = new Item(7, 3);

        Comparator<Item> byIdThenRevision =
            Comparator.comparingInt(Item::id)
                      .thenComparingInt(Item::revision);

        System.out.println(first == second);
        System.out.println(first.equals(second));
        System.out.println(first.hashCode() == second.hashCode());
        System.out.println(byIdThenRevision.compare(first, second));
        System.out.println(byIdThenRevision.compare(second, first));

        Set<Item> hashSet = new HashSet<>();
        hashSet.add(first);
        hashSet.add(second);
        System.out.println(hashSet.size());
        System.out.println(hashSet.contains(query));

        Set<Item> treeSet = new TreeSet<>(byIdThenRevision);
        treeSet.add(first);
        treeSet.add(second);
        System.out.println(treeSet.size());
        System.out.println(treeSet);
        System.out.println(treeSet.contains(second));
        System.out.println(treeSet.contains(query));

        Comparator<Item> byIdOnly = Comparator.comparingInt(Item::id);
        Set<Item> consistentTreeSet = new TreeSet<>(byIdOnly);
        consistentTreeSet.add(first);
        consistentTreeSet.add(second);
        System.out.println(consistentTreeSet.size());
        System.out.println(consistentTreeSet.contains(query));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ComparatorEqualsMismatchDemo.java
java ComparatorEqualsMismatchDemo
~~~

Kết quả chính xác:

~~~text
false
true
true
-1
1
1
true
2
[Item(7,1), Item(7,2)]
true
false
1
true
~~~

**Đọc kết quả theo ba phần:**

1. `first == second` là `false`: chúng là hai đối tượng riêng. `first.equals(second)` là `true`: `equals` chỉ xét `id = 7`. Vì `hashCode` cũng dùng `id`, hai đối tượng bằng nhau có cùng hash, đáp ứng hợp đồng `equals`/`hashCode`.
2. `byIdThenRevision.compare(first, second)` là `-1` vì `1 < 2` ở trường `revision`; chiều ngược lại là `1`. **Comparator phân biệt hai đối tượng mà `equals` coi là bằng nhau.**
3. `HashSet` dùng ngữ nghĩa `equals`/`hashCode`, nên sau hai lần `add` chỉ có **1 phần tử**; `contains(query)` trả `true` vì `query.id` cũng là `7`.
4. `TreeSet` sử dụng `byIdThenRevision`: vì `compare(first, second) != 0`, cả hai được giữ. Kích thước thành **2**; tập duyệt theo thứ tự `Item(7,1)`, rồi `Item(7,2)`.
5. `treeSet.contains(second)` là `true`. Nhưng `treeSet.contains(query)` là **`false`** dù `query.equals(first)` và `query.equals(second)` đều là `true`: theo comparator, `Item(7,3)` là một giá trị thứ tự khác.
6. Khi thay comparator bằng **`byIdOnly`**, hai `Item` cùng `id = 7` được so bằng `0`, trùng với cách `equals` định nghĩa sự bằng nhau. `TreeSet` khi đó chỉ có **1 phần tử**, và tìm `query` trả `true`.

Ví dụ có `Item` là `final`, các trường `final` và `hashCode` ổn định; do đó sự khác biệt giữa hai tập ở đây **chỉ đến từ quy tắc tương đương của comparator**, không phải do sửa đổi đối tượng sau khi chèn hay do lỗi hash.

## 3. Vì sao `TreeSet` có thể chứa hai phần tử `equals() == true`?

Giao diện `Set` mô tả tập hợp không có hai phần tử trùng nhau theo `equals`. Tuy nhiên, `SortedSet` và `TreeSet` cần dùng **thứ tự sắp xếp** để tìm đúng vị trí khi thêm, tìm hoặc xóa.

Với `TreeSet`:

1. Nếu được truyền `Comparator`, tập dùng `comparator.compare(x, y)` để sắp xếp và phân biệt phần tử.
2. Nếu không có `Comparator`, tập dùng thứ tự tự nhiên từ `Comparable.compareTo` của phần tử.
3. Khi kết quả so sánh là `0`, tập coi hai đối tượng là **tương đương theo thứ tự**, nên thêm đối tượng sau không tăng kích thước.
4. Khi kết quả so sánh khác `0`, tập có thể lưu hai phần tử ở hai vị trí phân biệt, **kể cả khi `equals` trả `true`**.

Đây là lý do tài liệu của `SortedSet` yêu cầu **thứ tự phải nhất quán với `equals` nếu muốn cấu trúc tuân thủ đúng hợp đồng chung của `Set`**. Hành vi của một sorted set với comparator không nhất quán vẫn có thể được xác định rõ bởi comparator, nhưng các kỳ vọng từ `Set` thông thường sẽ bị vi phạm.

### Minh họa sự khác nhau về “đơn vị duy nhất”

| Cấu trúc | Hai `Item(7,1)` và `Item(7,2)` |
| --- | --- |
| `HashSet<Item>` | Coi trùng vì `equals` theo `id`; giữ một phần tử. |
| `TreeSet<Item>` với comparator `id` rồi `revision` | Coi khác vì kết quả so sánh khác `0`; giữ hai phần tử. |
| `TreeSet<Item>` với comparator chỉ theo `id` | Coi trùng vì kết quả so sánh bằng `0`; giữ một phần tử. |

Đừng giải thích hiện tượng bằng câu “`TreeSet` dùng `hashCode` khác `HashSet`”. `TreeSet` là cấu trúc dựa trên thứ tự, **không dùng `hashCode` để xác định phần tử tương đương**. Một `hashCode` hoàn toàn đúng vẫn không sửa được comparator sai so với ngữ nghĩa tập hợp.

## 4. Điều gì xảy ra với `TreeMap` và các khóa `equals` bằng nhau?

Tình huống tương tự xảy ra ở `TreeMap`: một `Comparator` không nhất quán với `equals` có thể khiến hai key logic bằng nhau trở thành **hai entry**.

Tệp **`TreeMapComparatorDemo.java`** có thể biên dịch độc lập trên Java 17+:

~~~java
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class TreeMapComparatorDemo {
    record Key(int id, int revision) {
        @Override
        public boolean equals(Object obj) {
            return obj instanceof Key that && id == that.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    public static void main(String[] args) {
        Key first = new Key(7, 1);
        Key second = new Key(7, 2);

        Comparator<Key> byBoth =
            Comparator.comparingInt(Key::id)
                      .thenComparingInt(Key::revision);

        Map<Key, String> hash = new HashMap<>();
        System.out.println(hash.put(first, "old"));
        System.out.println(hash.put(second, "new"));
        System.out.println(hash.size());
        System.out.println(hash.get(first));

        Map<Key, String> tree = new TreeMap<>(byBoth);
        System.out.println(tree.put(first, "old"));
        System.out.println(tree.put(second, "new"));
        System.out.println(tree.size());
        System.out.println(tree.get(first));
        System.out.println(tree.get(second));
        System.out.println(tree.containsKey(new Key(7, 3)));
    }
}
~~~

Chạy:

~~~bash
javac TreeMapComparatorDemo.java
java TreeMapComparatorDemo
~~~

Kết quả chuẩn:

~~~text
null
old
1
new
null
null
2
old
new
false
~~~

Ở `HashMap`, `second` trùng khóa logic với `first`, nên `put(second, "new")` thay giá trị `"old"`, kích thước vẫn là `1`.

Ở `TreeMap` dùng comparator `id` rồi `revision`, hai key khác nhau theo quan hệ thứ tự, nên `put(second, "new")` thêm entry mới, kích thước thành `2`. Truy vấn với `new Key(7,3)` không tìm thấy key có cùng vị trí thứ tự, dù key đó `equals` với cả hai key đã lưu.

Điều này có thể ảnh hưởng đến chức năng cập nhật theo ID, chống trùng, tra cứu và xóa: **quy tắc chọn comparator cho `TreeMap` là một quyết định về danh tính khóa trong cấu trúc đó**, không chỉ là lựa chọn hiển thị.

Trong ví dụ, record `Key` đã **override cả `equals` lẫn `hashCode`** để dùng `id` làm khóa logic; đây là khác biệt có chủ đích so với `equals` tự sinh của record mặc định vốn xét cả `id` và `revision`.

## 5. Khi nào comparator không nhất quán với `equals` vẫn hữu ích?

Không có quy định rằng **mọi** comparator dùng để sắp xếp phải nhất quán với `equals`. Một comparator có thể sắp xếp theo nhiều tiêu chí để trình bày tốt hơn, miễn là nó đáp ứng hợp đồng về **thứ tự** như tính đối xứng về dấu, tính bắc cầu và tính nhất quán của các nhóm `compare == 0`.

Chẳng hạn, nếu muốn hiển thị tất cả revision của những đối tượng có cùng `id`, bạn có thể sắp xếp **một danh sách** bằng comparator xét `id` và `revision`:

~~~java
List<Item> items = new ArrayList<>();
items.add(new Item(7, 2));
items.add(new Item(7, 1));

items.sort(
    Comparator.comparingInt(Item::id)
              .thenComparingInt(Item::revision)
);
System.out.println(items);
~~~

Đoạn trên dùng lớp `Item` đã định nghĩa ở mục 2 và cần các import `java.util.List`, `java.util.ArrayList`, `java.util.Comparator`. Khi đặt trong phương thức `main` cùng lớp `Item`, đầu ra là:

~~~text
[Item(7,1), Item(7,2)]
~~~

Danh sách **không xóa phần tử trùng theo `equals`**, nên việc giữ cả hai revision là hành vi bình thường và comparator nhiều tiêu chí giúp hiển thị đúng ý định. Vấn đề phát sinh khi **cũng chính comparator này** được đưa vào `TreeSet`/ `TreeMap` nhưng chương trình lại trông đợi tập hợp hoặc bản đồ thực thi khái niệm trùng theo `id`.

### “Bằng nhau về đối tượng” và “phân biệt bản trình bày”

Nếu `id` xác định thực thể, còn `revision` là thuộc tính phiên bản, có thể hợp lý khi:

- Dùng `equals` theo `id` cho đối tượng thực thể.
- Dùng `Comparator` theo `id` rồi `revision` để **xếp thứ tự các bản trình bày trong danh sách**.
- Dùng `TreeSet`/ `TreeMap` **theo `id`** nếu cần lưu đúng một phần tử/key cho mỗi thực thể logic.
- Hoặc thiết kế kiểu khóa riêng `(id, revision)` nếu mục tiêu thật sự là giữ **mỗi phiên bản như một khóa khác nhau**.

Như vậy, cần tách việc “muốn nhìn các đối tượng theo thứ tự nào” khỏi việc “muốn lưu bao nhiêu giá trị khác nhau theo quy tắc nào”.

## 6. Cách sửa khi cần một `TreeSet` nhất quán với `equals`

Với `Item.equals` chỉ xét `id`, comparator tương ứng có thể là:

~~~java
Comparator<Item> byLogicalIdentity =
    Comparator.comparingInt(Item::id);

Set<Item> set = new TreeSet<>(byLogicalIdentity);
set.add(new Item(7, 1));
set.add(new Item(7, 2));

System.out.println(set.size()); // 1
~~~

Ở đây `compare(a, b) == 0` khi và chỉ khi hai đối tượng có cùng `id`, trùng với cách `equals` đã định nghĩa. Comparator không còn phân biệt các revision có cùng `id`.

Tùy yêu cầu nghiệp vụ, có ba hướng sửa hợp lý:

1. **Giữ `equals` theo ID, sửa comparator của sorted set/map theo ID.** Thích hợp khi bạn muốn một entry cho mỗi thực thể.
2. **Nếu revision thực sự là thành phần của danh tính**, đưa revision vào **cả `equals`, `hashCode` và comparator**. Khi đó `Item(7,1)` và `Item(7,2)` thực sự là hai giá trị khác nhau, phù hợp việc giữ cả hai.
3. **Nếu chỉ cần thứ tự trình bày**, giữ comparator theo ID + revision cho thao tác sắp xếp `List`. Dùng collection khác cho việc khử trùng theo `equals`, hoặc tách kiểu khóa biểu diễn mỗi revision.

Không nên vá bằng cách thay `hashCode` nhưng giữ `equals` và comparator cũ: `TreeSet` không dựa vào hash để quyết định trùng. Cũng không nên tự thêm `if (a.equals(b)) return 0;` vào đầu một comparator bất kỳ mà chưa chứng minh tính bắc cầu và nhất quán của các nhóm tương đương. Một comparator trộn quy tắc theo cách tùy tiện có thể phá hợp đồng so sánh.

## 7. Hợp đồng `Comparator` vẫn bắt buộc dù không nhất quán với `equals`

Tính nhất quán với `equals` là điều rất nên có cho sorted set/map, nhưng `Comparator` vẫn phải thỏa các quy tắc về quan hệ thứ tự:

- **Đổi chiều đổi dấu:** `sgn(compare(a,b)) == -sgn(compare(b,a))`.
- **Bắc cầu:** nếu `a > b` và `b > c` thì `a > c`.
- **Nhóm tương đương nhất quán:** nếu `compare(a,b) == 0`, thì dấu so sánh của `a` với một đối tượng `z` phải bằng dấu so sánh của `b` với `z`.
- **Tính ổn định:** kết quả nên không thay đổi khi những trường dùng để so sánh không đổi.

`Comparator.comparingInt(Item::id).thenComparingInt(Item::revision)` ở ví dụ là một thứ tự hợp lý, không vi phạm các tính chất trên. Nó chỉ **không nhất quán với `Item.equals`** vì xét thêm `revision`.

Tránh viết `return a.id() - b.id();`: phép trừ số nguyên có thể tràn `int` và đảo dấu kết quả, khiến comparator bị sai. `Integer.compare` hoặc `Comparator.comparingInt` xử lý so sánh số nguyên an toàn hơn.

Với đối tượng đặt trong `TreeSet` hoặc làm key của `TreeMap`, không thay đổi các trường tham gia comparator sau khi thêm vào cấu trúc. Nếu thứ tự bị đổi ngầm mà cây không được xây lại, thao tác `contains`, `get` hoặc `remove` có thể trả kết quả ngoài mong đợi. Các ví dụ trong bài dùng trường `final` để tránh hiện tượng này.

## 8. Nhận diện tình huống lỗi trong mã thực tế

Một số dấu hiệu đáng kiểm tra:

- `HashSet.size()` khác `TreeSet.size()` dù chèn cùng các giá trị. Kiểm tra xem comparator có trả `0` đúng cho từng cặp `equals == true` hay không.
- `TreeSet.contains(x)` là `false` dù bạn tìm được một phần tử `y` trong tập mà `x.equals(y) == true`. Có thể comparator phân biệt `x` và `y`.
- `TreeMap.put(k2, v2)` tạo entry mới dù `k1.equals(k2) == true`. Nếu dùng comparator không nhất quán với `equals`, hai key có thể đứng ở hai vị trí khác nhau trong cây.
- Một comparator `thenComparing(...)` được thêm “để phân biệt khi trùng tiêu chí” nhưng tiêu chí phụ lại **không có trong `equals`**. Đây là điểm cần xem lại khi comparator phục vụ sorted set/map.
- Comparator chỉ so một trường trong khi `equals` so nhiều trường, tạo tình huống ngược lại: `compare == 0` dù `equals == false`, khiến sorted set/map gộp các giá trị khác nhau theo nghiệp vụ.

Không phải cứ thấy `equals == true` và `compare != 0` là phải chỉnh lại mọi comparator. Hãy kiểm tra comparator được dùng **chỉ để sắp xếp** hay còn được dùng để **quyết định tính duy nhất và tra cứu**.

## 9. Bài tập kiểm chứng

Với `Item.equals` chỉ xét `id`, `hashCode` theo `id`, và comparator xét lần lượt `id`, `revision`, hãy tự trả lời trước khi chạy code:

1. `new Item(7,1).equals(new Item(7,2))` cho kết quả gì? Comparator của hai đối tượng trả dấu gì?
2. Thêm hai `Item` trên vào `HashSet` rồi `TreeSet` dùng comparator hai trường. Kích thước từng tập là bao nhiêu? Vì sao?
3. `TreeSet.contains(new Item(7,3))` trả gì nếu tập đang chứa `Item(7,1)` và `Item(7,2)`? Trong khi đó, `HashSet.contains(new Item(7,3))` trả gì?
4. Nếu sửa `TreeSet` để so theo `id` duy nhất, kích thước có thay đổi không? Hành vi nào trở nên nhất quán với `equals`?
5. Nếu nhu cầu thật là lưu từng revision như một phần tử độc lập, có nên thay `equals`/`hashCode` để cùng xét `id` và `revision` hay không? Điều này phụ thuộc quy tắc nghiệp vụ nào?
6. Tại sao việc chỉ chỉnh `hashCode` không giải quyết được trường hợp `TreeSet` giữ hai đối tượng `equals == true`?

**Ghi nhớ:** **`equals(a,b) == true` không tự buộc một comparator tùy biến phải trả `0`**. Nhưng `TreeSet` và `TreeMap` dùng **kết quả `compare == 0`** để xác định phần tử/key tương đương, nên comparator không nhất quán với `equals` có thể tạo ra “trùng theo `equals` nhưng khác trong cây”. Khi cần sorted set/map tuân thủ đúng ngữ nghĩa tập hợp/bản đồ theo giá trị, hãy bảo đảm **`compare(a,b) == 0` khi và chỉ khi `a.equals(b)`**; nếu chỉ cần sắp xếp khác biệt, hãy dùng comparator đó với danh sách.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ordered-collections-004">Khác biệt kết quả giữa HashSet với TreeSet</a>

<details>
<summary>Xem chi tiết</summary>

# Khác biệt kết quả giữa HashSet với TreeSet

## 1. Cùng là Set, vì sao kết quả có thể khác?

Trong Java, cả `HashSet<E>` và `TreeSet<E>` đều triển khai giao diện `Set<E>`: một tập hợp không chứa các phần tử được xem là trùng nhau. Tuy nhiên, chúng xác định phần tử “trùng” và tổ chức dữ liệu theo những cơ chế khác nhau.

- **`HashSet`** dựa vào `hashCode()` để xác định nhóm ứng viên và `equals(Object)` để nhận diện phần tử bằng nhau. Nó **không bảo đảm thứ tự duyệt**, dù trong một lần chạy bạn thấy phần tử tình cờ được in theo thứ tự nào đó.
- **`TreeSet`** là một `NavigableSet` có thứ tự, dùng **`compareTo()`** theo thứ tự tự nhiên hoặc **`Comparator.compare()`** được truyền vào. Hai phần tử được coi là cùng một phần tử của cây khi phép so sánh trả về **0**.

Vì thế, với một tập đầu vào giống nhau, hai set có thể khác **thứ tự duyệt**, khác **số phần tử**, khác kết quả `contains()` hoặc `remove()`, đặc biệt khi `compareTo`/`Comparator` không nhất quán với `equals`.

**Điểm cần nhớ:** Không thể kết luận “HashSet và TreeSet chỉ khác nhau ở chỗ TreeSet tự sắp xếp”. Cách nhận diện phần tử trùng nhau cũng là khác biệt mang tính hợp đồng.

## 2. So sánh nhanh hai cấu trúc

| Tiêu chí | `HashSet` | `TreeSet` |
| --- | --- | --- |
| Cơ chế tổ chức | Bảng băm, dựa trên `HashMap` | Cây tìm kiếm cân bằng, dựa trên `TreeMap` |
| Tiêu chí nhận diện trùng | Cùng tham chiếu hoặc bằng nhau theo `equals(Object)` trong cơ chế băm | `compareTo == 0` hoặc `Comparator.compare == 0` |
| Hợp đồng liên quan | `equals()` và `hashCode()` phải nhất quán | Thứ tự so sánh phải hợp lệ; nên nhất quán với `equals()` |
| Thứ tự duyệt | Không bảo đảm | Theo thứ tự tăng dần của natural ordering hoặc comparator |
| Thêm, tìm, xóa thông thường | Trung bình gần `O(1)` với băm phân bố tốt | `O(log n)` |
| Tìm phần tử gần nhất (`lower`, `floor`, `ceiling`, `higher`) | Không có API này trực tiếp | Có qua `NavigableSet` |
| `null` | Cho phép tối đa một phần tử `null` | Thứ tự tự nhiên thường không chấp nhận `null`; comparator riêng có thể quy định cách xử lý |
| Khi thích hợp | Cần kiểm tra tồn tại hoặc khử trùng theo equality, không cần thứ tự | Cần dữ liệu luôn theo thứ tự và các truy vấn khoảng, phần tử lân cận |

Các độ phức tạp trên là đánh giá thông thường, không phải cam kết mọi thao tác luôn mất đúng một lượng thời gian cố định. Hiệu năng thực tế còn phụ thuộc dữ liệu, số lần va chạm hash, chi phí tính hash, equality và comparator.

Nếu cần giữ **thứ tự chèn** chứ không phải thứ tự sắp xếp, hãy cân nhắc `LinkedHashSet`. Nó là một lựa chọn khác với `TreeSet`.

## 3. Trường hợp đơn giản: phần tử số nguyên có thứ tự tự nhiên

Với các `Integer` thông thường, `equals()` và `compareTo()` nhất quán: hai số nguyên có cùng giá trị thì bằng nhau theo cả hai cách. Vì vậy hai set sẽ khử trùng giống nhau, nhưng thứ tự duyệt có thể khác.

Tạo tệp `HashSetTreeSetDemo.java`:

```java
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class HashSetTreeSetDemo {
    public static void main(String[] args) {
        int[] numbers = {4, 1, 3, 2, 3};

        Set<Integer> hash = new HashSet<>();
        TreeSet<Integer> sorted = new TreeSet<>();

        for (int n : numbers) {
            hash.add(n);
            sorted.add(n);
        }

        System.out.println(hash.size());
        System.out.println(sorted.size());
        System.out.println(sorted);
        System.out.println(sorted.first());
        System.out.println(sorted.ceiling(3));
        System.out.println(sorted.higher(3));
        System.out.println(hash.contains(3));
        System.out.println(sorted.contains(3));

        BigDecimal a = new BigDecimal("2.0");
        BigDecimal b = new BigDecimal("2.00");
        Set<BigDecimal> decimalHash = new HashSet<>();
        Set<BigDecimal> decimalTree = new TreeSet<>();

        decimalHash.add(a);
        decimalHash.add(b);
        decimalTree.add(a);
        decimalTree.add(b);

        System.out.println(decimalHash.size());
        System.out.println(decimalTree.size());
        System.out.println(
                decimalHash.contains(new BigDecimal("2.000")));
        System.out.println(
                decimalTree.contains(new BigDecimal("2.000")));
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac HashSetTreeSetDemo.java
java HashSetTreeSetDemo
```

Kết quả:

```text
4
4
[1, 2, 3, 4]
1
3
4
true
true
2
1
false
true
```

Giải thích nửa đầu chương trình:

1. Dãy đầu vào có năm lần thêm, trong đó số `3` lặp hai lần. Cả hai set đều có kích thước **4**.
2. `sorted` in thành `[1, 2, 3, 4]` vì `TreeSet<Integer>` dùng thứ tự tăng dần của `Integer`.
3. `first()` trả phần tử nhỏ nhất `1`; `ceiling(3)` trả phần tử nhỏ nhất lớn hơn hoặc bằng 3, tức `3`; `higher(3)` trả phần tử nhỏ nhất lớn hơn 3, tức `4`.
4. Cả hai `contains(3)` đều trả `true`.

Ví dụ **cố ý không in trực tiếp `hash`**: thứ tự duyệt của `HashSet` không được Java bảo đảm. Ngay cả nếu tình cờ hiện ra `[1, 2, 3, 4]`, bạn cũng không được dựa vào đó trong chương trình.

Nửa sau chương trình minh họa khác biệt sâu hơn về **định nghĩa phần tử trùng**, không chỉ về thứ tự. Phần tiếp theo giải thích nguyên nhân.

## 4. BigDecimal: compareTo bằng 0 nhưng equals là false

`BigDecimal` lưu cả giá trị số học và **scale** (số chữ số thập phân theo cách biểu diễn nội bộ):

```java
BigDecimal a = new BigDecimal("2.0");  // scale = 1
BigDecimal b = new BigDecimal("2.00"); // scale = 2
```

Đoạn trên là minh họa và cần `import java.math.BigDecimal;`.

```text
a.compareTo(b) == 0  → true
a.equals(b)          → false
```

Lý do: `compareTo()` xét hai số bằng nhau về **giá trị toán học**, còn `BigDecimal.equals()` xét cả **giá trị và scale**.

Điều này tác động trực tiếp đến set:

- `HashSet<BigDecimal>` dùng equality theo `equals/hashCode`, nên hai phần tử `2.0` và `2.00` **khác nhau**: kích thước là **2**.
- `TreeSet<BigDecimal>` theo natural ordering dùng `compareTo == 0`, nên hai giá trị này được xem là **tương đương trong cây**: kích thước là **1**.
- `decimalHash.contains(new BigDecimal("2.000"))` là `false` vì scale 3 khác hai scale 1 và 2.
- `decimalTree.contains(new BigDecimal("2.000"))` là `true` vì giá trị số học bằng 2, so sánh với phần tử đã có trả 0.

`BigDecimal` là ví dụ tiêu biểu có **natural ordering inconsistent with equals**, được JavaDoc ghi nhận. Nó không phải lỗi của `HashSet` hoặc `TreeSet`: mỗi lớp đang thực hiện đúng cơ chế của mình.

Nếu nghiệp vụ xem `2.0`, `2.00` và `2` là **cùng một giá trị**, cần chọn rõ tiêu chí ở lớp xử lý dữ liệu. Nếu dùng `HashSet` và muốn khử trùng theo giá trị số học, có thể chuẩn hóa các `BigDecimal` bằng `stripTrailingZeros()` trước khi thêm và tra cứu. Tuy nhiên, phép chuẩn hóa có thể thay đổi scale, kể cả thành scale âm đối với số như `1000.00`; không áp dụng tùy tiện khi scale có ý nghĩa nghiệp vụ.

## 5. Comparator có thể khiến TreeSet “mất” phần tử khác nhau

Ngay cả khi kiểu phần tử có `equals` và natural ordering nhất quán, một `TreeSet` với **comparator do lập trình viên cung cấp** vẫn có thể coi những object khác nhau là trùng.

Ví dụ: mỗi học viên có một `id` và một `score`. Nếu comparator **chỉ** so điểm, hai học viên khác mã nhưng cùng điểm được so sánh bằng 0. `TreeSet` sẽ chỉ giữ một học viên đại diện cho nhóm điểm đó.

Tạo tệp `ComparatorSetDemo.java`:

```java
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class ComparatorSetDemo {
    record Student(String id, int score) {}

    public static void main(String[] args) {
        Student a = new Student("S01", 90);
        Student b = new Student("S02", 90);

        System.out.println(a.equals(b));

        Set<Student> byEquality = new HashSet<>();
        byEquality.add(a);
        byEquality.add(b);
        System.out.println(byEquality.size());

        Comparator<Student> byScore =
                Comparator.comparingInt(Student::score);
        TreeSet<Student> byScoreOnly = new TreeSet<>(byScore);
        System.out.println(byScoreOnly.add(a));
        System.out.println(byScoreOnly.add(b));
        System.out.println(byScoreOnly.size());
        System.out.println(byScoreOnly.contains(b));

        Comparator<Student> byScoreThenId =
                Comparator.comparingInt(Student::score)
                        .thenComparing(Student::id);
        TreeSet<Student> fullOrder = new TreeSet<>(byScoreThenId);
        fullOrder.add(a);
        fullOrder.add(b);
        System.out.println(fullOrder.size());
        System.out.println(fullOrder.contains(b));
    }
}
```

Biên dịch và chạy:

```bash
javac ComparatorSetDemo.java
java ComparatorSetDemo
```

Kết quả:

```text
false
2
true
false
1
true
2
true
```

Phân tích:

- Record `Student` tự cung cấp `equals` và `hashCode` theo cả `id` lẫn `score`. `a.equals(b)` là `false`, nên `HashSet` chứa 2 học viên.
- `byScore.compare(a, b) == 0` vì cả hai đạt 90 điểm. `byScoreOnly.add(b)` trả `false` dù `b` khác `a` theo `equals`. `TreeSet` chỉ có 1 phần tử.
- `byScoreOnly.contains(b)` trả `true` vì đã có học viên **tương đương theo comparator**; nó không khẳng định chính đối tượng `b` đang được lưu.
- `byScoreThenId` phá hòa theo `id` sau `score`. Vì hai đối tượng khác mã, `fullOrder` chứa cả hai và vẫn giữ dữ liệu theo thứ tự điểm rồi mã.

**Quy tắc thiết kế:** Nếu mục tiêu là **sắp xếp theo điểm nhưng vẫn giữ mọi học viên**, comparator của `TreeSet` phải phá hòa bằng tiêu chí phân biệt đúng các đối tượng logic. Hoặc dùng một `List<Student>` rồi gọi `sort(byScore)` khi cần giữ nhiều học viên cùng điểm.

Dùng comparator chỉ theo điểm có thể hợp lý nếu **nghiệp vụ thật sự muốn tối đa một học viên cho mỗi mức điểm**, nhưng lúc ấy phải công bố rõ chính sách loại trùng.

## 6. Yêu cầu nhất quán giữa compareTo và equals

Với một lớp triển khai `Comparable<T>`, Java **khuyến nghị mạnh**:

```text
x.compareTo(y) == 0  ⇔  x.equals(y) == true
```

Với `TreeSet` dùng comparator, yêu cầu tương ứng là:

```text
comparator.compare(x, y) == 0  ⇔  x.equals(y) == true
```

Ở đây ký hiệu `⇔` nghĩa là điều kiện đúng theo **cả hai chiều**. Khi điều đó được bảo đảm:

1. Hai object bằng nhau theo `equals` không bị `TreeSet` coi là hai phần tử khác nhau.
2. Hai object khác nhau theo `equals` không bị `TreeSet` gộp do comparator trả 0.
3. `HashSet` và `TreeSet` có thể đồng thuận về tiêu chí phần tử trùng, dù cách sắp xếp và độ phức tạp khác nhau.

Một thứ tự **không nhất quán với equals** vẫn có thể tạo ra cây hoạt động theo thứ tự đã chỉ định, nhưng hành vi có thể không phù hợp với hợp đồng bình đẳng chung của `Set`. Đây là lý do tài liệu `TreeSet` đặc biệt lưu ý comparator hoặc natural ordering cần nhất quán với `equals` nếu muốn đáp ứng hợp đồng `Set` đầy đủ.

Cũng cần phân biệt **thứ tự so sánh hợp lệ** với **nhất quán cùng equals**. Một comparator phải có tính nhất quán, bắc cầu và dấu đối xứng theo các quy tắc của `Comparator`. Comparator sai các quy tắc đó còn gây vấn đề nghiêm trọng hơn chỉ lệch equality.

## 7. Null, khả năng so sánh và lỗi thường thấy khi thêm phần tử

`HashSet` cho phép **một phần tử `null`**. Thêm `null` lần hai không làm thay đổi kích thước set.

Với `TreeSet`, cây cần biết cách so sánh phần tử. Do đó:

- Khi dùng thứ tự tự nhiên như `TreeSet<String>` hoặc `TreeSet<Integer>`, thêm `null` sẽ gây `NullPointerException` trong triển khai tiêu chuẩn.
- Một comparator như `Comparator.nullsFirst(...)` có thể định nghĩa thứ tự cho `null` nếu nghiệp vụ cần; khi đó một `TreeSet` được tạo với comparator phù hợp có thể chứa `null`.
- Nếu các phần tử không có natural ordering tương thích, hoặc không so sánh được với nhau, `TreeSet` có thể ném `ClassCastException`. Ví dụ record `Student` ở trên không triển khai `Comparable`, nên phải dùng `new TreeSet<>(comparator)` nếu muốn chứa nhiều học viên.

Không được suy rằng `TreeSet` tự biết cách so sánh hai object tùy ý. Muốn `TreeSet` nhận dữ liệu, phải cung cấp **thứ tự tự nhiên** hoặc một **comparator** rõ ràng.

## 8. Thay đổi trạng thái object sau khi thêm vào set

Nếu object được sử dụng làm phần tử set mà các trường liên quan đến equality hoặc ordering có thể thay đổi, hành vi tìm kiếm có thể trở nên sai lệch:

- Trong `HashSet`, nếu `hashCode()`/ `equals()` thay đổi do sửa trường của phần tử sau khi chèn, việc `contains` hoặc `remove` có thể không tìm được đối tượng như kỳ vọng.
- Trong `TreeSet`, nếu tiêu chí `compareTo` hoặc `Comparator` thay đổi sau khi chèn, vị trí của phần tử trong cây có thể không còn phù hợp thứ tự; thao tác tìm, xóa hoặc duyệt có thể cho kết quả khó dự đoán.

Với khóa và phần tử được sử dụng lâu dài, nên **ưu tiên dữ liệu bất biến**. Nếu thật sự cần thay đổi tiêu chí định danh hoặc thứ tự, hãy xóa phần tử khỏi set **trước khi** sửa rồi thêm lại, hoặc tạo một object bất biến mới.

`Comparator` cũng cần dựa trên tiêu chí ổn định. Nếu nó phụ thuộc vào trạng thái bên ngoài thay đổi (ví dụ cấu hình động), cấu trúc cây có thể mất tính nhất quán ngay cả khi object không đổi.

## 9. Chọn HashSet hay TreeSet trong tình huống thực tế?

**Chọn `HashSet` khi:**

- Mục tiêu là kiểm tra tồn tại, loại trùng, thêm/xóa theo equality của đối tượng.
- Không cần thứ tự duyệt hay truy vấn phần tử gần nhất.
- Bạn muốn chi phí trung bình thấp cho các thao tác thông thường, với `equals/hashCode` hợp lệ và hash phân bố tốt.

**Chọn `TreeSet` khi:**

- Cần các phần tử **luôn có thứ tự** mà không phải sort lại mỗi lần truy cập.
- Cần `first`, `last`, `floor`, `ceiling`, `lower`, `higher`, `headSet`, `tailSet`, `subSet` hoặc truy vấn khoảng.
- Bạn có `Comparable` hoặc `Comparator` hợp lệ và hiểu rõ quan hệ `compare == 0` với `equals`.

**Chọn `LinkedHashSet` khi:** bạn cần tính duy nhất như set và **duy trì thứ tự thêm**. Nó không thay thế khả năng truy vấn theo giá trị thứ tự của `TreeSet`.

**Chọn `List` rồi sort khi:** bạn muốn giữ **mọi bản ghi**, kể cả các bản ghi cùng tiêu chí sắp xếp, và cần hiển thị theo một comparator. `TreeSet` là tập hợp, vì vậy `compare == 0` sẽ làm các phần tử bị coi là trùng.

Cả `HashSet` và `TreeSet` thông thường đều không tự bảo đảm an toàn khi nhiều luồng đồng thời sửa set; cần sử dụng cơ chế đồng bộ hoặc cấu trúc concurrent phù hợp theo yêu cầu.

## 10. Checklist kiểm thử hai set

1. **Kiểm tra hai object cùng giá trị logic:** `equals` và `hashCode` có nhất quán không? `compareTo` hoặc comparator có trả 0 không?
2. **Kiểm tra hai object khác giá trị logic:** comparator có tiêu chí phá hòa đủ để không gộp nhầm chúng không?
3. **Thử phần tử trùng và số lượng:** sau khi `add`, đối chiếu kết quả `boolean` và `size()` của mỗi set.
4. **Kiểm tra thứ tự:** chỉ kỳ vọng thứ tự từ `TreeSet` (hoặc `LinkedHashSet` theo thứ tự thêm); không viết kiểm thử giả định thứ tự duyệt `HashSet`.
5. **Kiểm tra contains/remove:** đặc biệt với các cặp `BigDecimal("2.0")` và `BigDecimal("2.00")`, hoặc comparator chỉ so một trường.
6. **Kiểm tra `null` và dữ liệu không so sánh được:** cân nhắc loại phần tử, natural ordering và chính sách comparator.
7. **Kiểm tra dữ liệu mutable:** giữ bất biến các trường tham gia equality và ordering khi object còn trong set.
8. **Kiểm tra mục tiêu nghiệp vụ:** cần tập phần tử duy nhất, tập phần tử có thứ tự, thứ tự chèn hay danh sách có thể chứa giá trị trùng?

**Tự kiểm tra:**

- Thêm `2.0` và `2.00` dạng `BigDecimal` vào `HashSet` và `TreeSet` theo thứ tự tự nhiên: kích thước lần lượt bao nhiêu? **2 và 1.**
- Hai `Student` khác `id` nhưng cùng `score`; `TreeSet` chỉ so `score` giữ bao nhiêu phần tử? **1.**
- `HashSet` in ra theo thứ tự tăng dần trong một lần chạy thì có bảo đảm lần sau vẫn vậy không? **Không.**
- Muốn giữ mọi bản ghi cùng điểm và chỉ sắp xếp để hiển thị, nên dùng gì? **Một `List` và `sort` hoặc comparator phù hợp với yêu cầu giữ đủ bản ghi.**

**Ghi nhớ:** `HashSet` nhận diện phần tử trùng bằng `equals/hashCode`, còn `TreeSet` dùng kết quả so sánh bằng 0. Vì vậy chúng không chỉ khác nhau ở thứ tự duyệt: **số lượng phần tử và kết quả tìm kiếm cũng có thể khác**, đặc biệt với `BigDecimal` hoặc comparator không nhất quán với `equals`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ordered-collections-005">Ví dụ BigDecimal có scale khác nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Ví dụ BigDecimal có scale khác nhau trong tập hợp có thứ tự

## 1. Mục tiêu

`BigDecimal` đại diện cho số thập phân có độ chính xác tùy ý bằng hai yếu tố: **unscaled value** (một số nguyên) và **scale** (số chữ số thập phân theo phép biểu diễn). Hai đối tượng có cùng **giá trị số học** vẫn có thể khác scale. Đây là tình huống điển hình cho việc **quan hệ bằng nhau của `equals()` không đồng nhất với quan hệ tương đương của thứ tự `compareTo()`**.

Bài học giúp bạn:

- Hiểu `new BigDecimal("1.0")` và `new BigDecimal("1.00")` vì sao có `equals() == false` nhưng `compareTo() == 0`.
- Dự đoán chính xác khi đưa hai giá trị đó vào `HashSet`, `TreeSet`, `HashMap`, `TreeMap`.
- Hiểu tập hợp có thứ tự dùng `compareTo` hoặc `Comparator` để xét cả vị trí sắp xếp lẫn việc **một key đã tồn tại hay chưa**.
- Chọn chính sách phù hợp khi cần phân biệt scale hoặc khi cần coi các cách biểu diễn cùng trị số là một.
- Áp dụng `stripTrailingZeros()` hoặc comparator tường minh đúng mục đích; tránh làm mất thông tin scale có ý nghĩa nghiệp vụ.

## 2. BigDecimal gồm trị số và scale

Đối với `BigDecimal`, trị số được diễn đạt theo công thức:

```text
numeric value = unscaledValue × 10^(-scale)
```

Ví dụ:

| Cách tạo | Unscaled value | Scale | Trị số học |
| --- | ---: | ---: | ---: |
| `new BigDecimal("1.0")` | `10` | `1` | `1` |
| `new BigDecimal("1.00")` | `100` | `2` | `1` |
| `new BigDecimal("1")` | `1` | `0` | `1` |
| `new BigDecimal("1E+2")` | `1` | `-2` | `100` |
| `new BigDecimal("0.000")` | `0` | `3` | `0` |

Trong Java:

```java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

System.out.println(a.unscaledValue()); // 10
System.out.println(a.scale());         // 1
System.out.println(b.unscaledValue()); // 100
System.out.println(b.scale());         // 2
```

Đây là đoạn minh họa cần `import java.math.BigDecimal;`. Phân biệt **scale** với **precision**: precision là số chữ số của unscaled value (không tính dấu), còn scale xác định vị trí dấu thập phân. `"1.00"` có precision 3, scale 2; `"1.0"` có precision 2, scale 1.

Scale có thể là thông tin đáng lưu giữ: biểu diễn độ chính xác đo lường, số chữ số được ghi nhận hay chính sách hiển thị tiền tệ. Vì vậy Java cung cấp cả cách so “bằng nhau về biểu diễn số thập phân” lẫn cách so “bằng nhau về trị số”.

## 3. `equals()` và `compareTo()` khác nhau thế nào?

### 3.1. `BigDecimal.equals(Object)` xét cả trị số và scale

Với hai `BigDecimal`, `equals()` trả `true` chỉ khi chúng có **cùng unscaled value và cùng scale**. Do đó:

```java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

a.equals(b);                // false
a.hashCode() == b.hashCode(); // false trong ví dụ này
```

`hashCode()` của `BigDecimal` phù hợp với `equals()`: nếu hai giá trị `equals` bằng nhau, hash của chúng bắt buộc bằng nhau. Khi chỉ khác scale, hash **có thể** khác và trong ví dụ `1.0` với `1.00` quả thực khác. Đừng suy ra hash là định danh duy nhất vì collision vẫn có thể xảy ra.

### 3.2. `compareTo()` so giá trị số học

`BigDecimal.compareTo(BigDecimal)` thực hiện so sánh thứ tự theo **trị số**, không coi scale là yếu tố phân biệt khi trị số bằng nhau:

```java
a.compareTo(b);  // 0
```

Quy tắc:

- Giá trị âm: `a.compareTo(b) < 0` nghĩa là `a` nhỏ hơn `b`.
- Trả `0`: `a` và `b` có cùng trị số đối với phép so sánh này.
- Giá trị dương: `a.compareTo(b) > 0` nghĩa là `a` lớn hơn `b`.

Không dựa vào độ lớn tuyệt đối của kết quả âm/dương; hợp đồng chỉ bảo đảm **dấu**. Với `BigDecimal`, quan hệ `compareTo() == 0` có thể rộng hơn `equals() == true`.

`BigDecimal` là ví dụ tiêu biểu cho **natural ordering inconsistent with equals**: hai phần tử có thể tương đương về thứ tự nhưng không bằng nhau theo `equals`.

### 3.3. Mối quan hệ chính xác

| Phép kiểm tra | `1.0` với `1.00` | Ý nghĩa |
| --- | --- | --- |
| `a == b` | `false` | Hai đối tượng được tạo riêng có danh tính khác nhau. |
| `a.equals(b)` | `false` | Unscaled value và scale không cùng bộ giá trị. |
| `a.compareTo(b) == 0` | `true` | Cùng trị số số học. |
| `a.scale() == b.scale()` | `false` | Một bên scale 1, bên kia scale 2. |
| `a.stripTrailingZeros().equals(b.stripTrailingZeros())` | `true` | Hai giá trị quy về cùng dạng chuẩn với ví dụ này. |

Lưu ý `Objects.equals(a,b)` vẫn gọi `a.equals(b)` khi cả hai không null; nó **không tự chuyển sang `compareTo()`**.

## 4. Tại sao `HashSet` và `TreeSet` cho kết quả khác?

### 4.1. HashSet dựa trên `equals` và `hashCode`

`HashSet` sử dụng hash để phân phối phần tử, và dùng quan hệ `equals` để phân biệt những khóa có thể trùng nhau. Với:

```java
Set<BigDecimal> hashed = new HashSet<>();
hashed.add(new BigDecimal("1.0"));
hashed.add(new BigDecimal("1.00"));
```

`hashed.size()` là **2** vì hai `BigDecimal` không `equals()` bằng nhau. Đây là hai phần tử khác nhau theo **quan hệ bằng nhau của `BigDecimal`**.

### 4.2. TreeSet dựa trên thứ tự `compareTo` hoặc `Comparator`

`TreeSet` là một `SortedSet`; nếu không cung cấp comparator, nó dùng **thứ tự tự nhiên** (natural ordering) của phần tử, ở đây là `BigDecimal.compareTo`.

```java
TreeSet<BigDecimal> sorted = new TreeSet<>();
boolean first = sorted.add(new BigDecimal("1.0"));
boolean second = sorted.add(new BigDecimal("1.00"));
```

Kết quả:

- `first` là `true`: phần tử đầu được thêm.
- `second` là `false`: đối tượng thứ hai có `compareTo(...) == 0` với phần tử sẵn có.
- `sorted.size()` là **1**: `TreeSet` coi hai giá trị là cùng một khóa thứ tự, dù `equals` trả `false`.
- `sorted.contains(new BigDecimal("1.000"))` là `true`: tìm kiếm cũng dựa trên quan hệ thứ tự.
- Nếu thêm `1.0` trước, phần tử được giữ lại có dạng `1.0` (scale 1); nếu thêm `1.00` trước thì phần tử được giữ lại có dạng `1.00` (scale 2). Điều này cần lưu ý nếu mã ứng dụng quan sát `scale()` của phần tử được lưu.

Quy tắc tổng quát của `TreeSet` là **comparator trả 0 ⇒ phần tử bị coi là trùng đối với cấu trúc cây**, không phụ thuộc việc `equals` của chúng trả gì.

### 4.3. Điều này có vi phạm hợp đồng `Set` không?

Hợp đồng tổng quát của `Set` định nghĩa trùng theo `equals`. Các tài liệu Java nêu rằng thứ tự dùng cho một sorted set **nên nhất quán với `equals`** nếu muốn sorted set tuân thủ đầy đủ hợp đồng chung của `Set`. Khi thứ tự không nhất quán như `BigDecimal`, `TreeSet` vẫn có hành vi được xác định bởi comparator, nhưng cách coi phần tử trùng **khác** với một `HashSet` chứa cùng giá trị.

Không kết luận rằng `TreeSet` bị lỗi hoặc `BigDecimal.compareTo` triển khai sai. Đó là kết quả của **hai định nghĩa tương đương khác nhau**. Người thiết kế phải chọn quan hệ bằng nhau cần áp dụng cho collection.

## 5. `TreeMap`: key mới có thể thay value của key cũ

`TreeMap` cũng dựa vào `compareTo` hoặc comparator để sắp xếp và xác định khóa tương đương:

```java
TreeMap<BigDecimal, String> map = new TreeMap<>();
map.put(new BigDecimal("1.0"), "first");
map.put(new BigDecimal("1.00"), "second");
```

Kết quả:

- `map.size() == 1`: hai key so sánh bằng 0.
- Giá trị của key tương đương được cập nhật thành `"second"`.
- Khi thêm theo đúng thứ tự trên, key giữ trong entry là `1.0` (scale 1), vì cập nhật giá trị không thay đối tượng key đã lưu.
- `map.get(new BigDecimal("1.000"))` trả `"second"`.

Ngược lại, `HashMap<BigDecimal, String>` với hai key này sẽ có **hai entry** khi dùng `equals/hashCode` của `BigDecimal`.

Tình huống thực tế: Nếu bạn lưu mức giá `1.0` và `1.00` như hai mốc riêng cần giữ cả precision/scale, `TreeMap` theo thứ tự tự nhiên có thể **gộp chúng thành một key**. Nếu bạn lưu các ngưỡng chỉ dựa trên trị số, việc gộp lại có thể chính là hành vi mong muốn.

## 6. Chương trình Java 17+ chạy được

Tạo tệp **`BigDecimalSortedSetDemo.java`**:

```java
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class BigDecimalSortedSetDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("1.0");
        BigDecimal b = new BigDecimal("1.00");

        System.out.println("a scale: " + a.scale());
        System.out.println("b scale: " + b.scale());
        System.out.println("equals: " + a.equals(b));
        System.out.println("compareTo: " + a.compareTo(b));
        System.out.println("hash equals: "
                + (a.hashCode() == b.hashCode()));

        Set<BigDecimal> hashed = new HashSet<>();
        hashed.add(a);
        hashed.add(b);
        System.out.println("HashSet size: " + hashed.size());

        TreeSet<BigDecimal> sorted = new TreeSet<>();
        System.out.println("TreeSet add a: " + sorted.add(a));
        System.out.println("TreeSet add b: " + sorted.add(b));
        System.out.println("TreeSet size: " + sorted.size());
        System.out.println("TreeSet stored: "
                + sorted.first().toPlainString());
        System.out.println("TreeSet contains 1.000: "
                + sorted.contains(new BigDecimal("1.000")));

        TreeMap<BigDecimal, String> sortedMap = new TreeMap<>();
        sortedMap.put(a, "first");
        sortedMap.put(b, "second");
        System.out.println("TreeMap size: " + sortedMap.size());
        System.out.println("TreeMap key scale: "
                + sortedMap.firstKey().scale());
        System.out.println("TreeMap lookup 1.000: "
                + sortedMap.get(new BigDecimal("1.000")));

        Map<BigDecimal, String> hashedMap = new HashMap<>();
        hashedMap.put(a, "first");
        hashedMap.put(b, "second");
        System.out.println("HashMap size: " + hashedMap.size());

        BigDecimal cleanA = a.stripTrailingZeros();
        BigDecimal cleanB = b.stripTrailingZeros();
        System.out.println("canonical equals: "
                + cleanA.equals(cleanB));
        System.out.println("canonical hash equals: "
                + (cleanA.hashCode() == cleanB.hashCode()));

        Comparator<BigDecimal> byValueThenScale =
                Comparator.<BigDecimal>naturalOrder()
                        .thenComparingInt(BigDecimal::scale);
        TreeSet<BigDecimal> strict = new TreeSet<>(byValueThenScale);
        strict.add(a);
        strict.add(b);
        System.out.println("strict TreeSet size: " + strict.size());

        BigDecimal large = new BigDecimal("600.0")
                .stripTrailingZeros();
        System.out.println("600.0 canonical: " + large.toString());
        System.out.println("0.000 canonical: "
                + new BigDecimal("0.000")
                        .stripTrailingZeros().toString());
    }
}
```

Biên dịch và chạy:

```bash
javac BigDecimalSortedSetDemo.java
java BigDecimalSortedSetDemo
```

Kết quả:

```text
a scale: 1
b scale: 2
equals: false
compareTo: 0
hash equals: false
HashSet size: 2
TreeSet add a: true
TreeSet add b: false
TreeSet size: 1
TreeSet stored: 1.0
TreeSet contains 1.000: true
TreeMap size: 1
TreeMap key scale: 1
TreeMap lookup 1.000: second
HashMap size: 2
canonical equals: true
canonical hash equals: true
strict TreeSet size: 2
600.0 canonical: 6E+2
0.000 canonical: 0
```

Giải thích các điểm chính:

1. `a` và `b` biểu diễn cùng trị số nhưng có scale 1 và 2 nên `equals()` là `false`, `compareTo()` là 0.
2. `HashSet` và `HashMap` giữ hai mục phân biệt vì dựa trên equality của `BigDecimal`.
3. `TreeSet` mặc định thêm `a` thành công, từ chối `b` vì natural ordering coi hai giá trị tương đương; nó còn `contains` giá trị `1.000`.
4. `TreeMap` giữ key đầu tiên với scale 1 và cập nhật value của key tương đương thành `"second"`.
5. Hai bản chuẩn hóa qua `stripTrailingZeros()` có cùng `equals` và hash.
6. Comparator tùy chỉnh `byValueThenScale` tách thêm các key có cùng trị số nhưng khác scale, nên `strict` có hai phần tử.
7. `stripTrailingZeros()` có thể trả số dạng lũy thừa 10 như `6E+2`; số 0 thường được chuẩn hóa thành `0`. Đây là thay đổi **biểu diễn**, dù trị số không đổi.

Kết quả trên không in trực tiếp thứ tự băm hay con số hash không cần thiết; ví dụ chỉ kiểm tra quan hệ hash có bằng nhau hay không.

## 7. Muốn TreeSet phân biệt scale thì dùng comparator nào?

Nếu nghiệp vụ yêu cầu xem `1.0` và `1.00` là **hai giá trị khác nhau**, comparator của tập hợp có thứ tự phải trả khác 0 khi scale khác nhau:

```java
Comparator<BigDecimal> byValueThenScale =
        Comparator.<BigDecimal>naturalOrder()
                .thenComparingInt(BigDecimal::scale);

TreeSet<BigDecimal> values = new TreeSet<>(byValueThenScale);
values.add(new BigDecimal("1.0"));
values.add(new BigDecimal("1.00"));
// size() == 2
```

Đầu tiên comparator so trị số, sau đó nếu cùng trị số thì so scale. Với `BigDecimal`, cùng trị số **và** cùng scale tương đương với `equals()` bằng nhau, do vậy comparator này **nhất quán với equals** trong miền các đối tượng `BigDecimal`.

Lợi ích: vừa có thứ tự số học để duyệt, vừa bảo toàn cách phân biệt scale của `BigDecimal.equals()`. Hạn chế: hai biểu diễn cùng trị số sẽ chiếm hai vị trí khác nhau; điều này đúng hoặc sai tùy bài toán. Comparator không làm thay đổi `BigDecimal.equals()` bên ngoài `TreeSet`.

**Cảnh báo:** không dùng `Comparator.comparing(BigDecimal::scale)` đơn lẻ nếu bạn cần phân biệt mọi giá trị. Hai số `1.00` và `2.00` có scale 2 nhưng trị số khác nhau; chỉ so scale sẽ coi chúng là trùng và đánh mất một phần tử.

## 8. Muốn coi cùng trị số là cùng key ở mọi collection?

Đôi khi nghiệp vụ xác định `1.0`, `1.00` và `1` là một. Khi đó bạn có thể chuẩn hóa trước khi lưu:

```java
static BigDecimal canonical(BigDecimal input) {
    return java.util.Objects.requireNonNull(
            input, "input").stripTrailingZeros();
}
```

Đây là phương thức minh họa đặt trong một lớp Java. Từ đó:

```java
Set<BigDecimal> numericKeys = new HashSet<>();
numericKeys.add(canonical(new BigDecimal("1.0")));
numericKeys.add(canonical(new BigDecimal("1.00")));
// size() == 1
```

Các key đã chuẩn hóa có `equals()`/`hashCode()` phù hợp với quan hệ “cùng trị số” đã chọn; `HashSet` và `TreeSet` (dùng natural ordering) giờ thống nhất hơn về nhóm key.

Tuy nhiên, quy tắc này **xóa mất khác biệt scale**. Nếu `1.00` mang thông tin đo lường, hiển thị hoặc định dạng hóa đơn thì không được âm thầm bỏ nó. Có thể giữ nguyên giá trị gốc ở một trường riêng để hiển thị và dùng canonical key cho việc tìm kiếm, miễn là phân tách rõ hai vai trò.

Một hướng khác là dùng value object định nghĩa equality theo trị số:

```java
record NumericKey(BigDecimal canonicalValue) {
    public NumericKey {
        canonicalValue = java.util.Objects.requireNonNull(
                canonicalValue, "canonicalValue")
                .stripTrailingZeros();
    }
}
```

Với record này, field được chuẩn hóa ngay khi khởi tạo, nên record sinh `equals`/`hashCode` dựa trên canonical value. Nếu cần sắp xếp `NumericKey`, phải cung cấp comparator phù hợp hoặc triển khai `Comparable`; việc nó là record **không tự khiến nó có natural ordering**.

### Lưu ý về `stripTrailingZeros()`

- `new BigDecimal("600.0").stripTrailingZeros()` có thể là `6E+2` với scale âm; nếu cần in dạng thập phân không dùng ký pháp khoa học thì dùng `toPlainString()` hoặc định dạng theo yêu cầu.
- `BigDecimal.ZERO` có scale 0; chuẩn hóa một giá trị 0 như `new BigDecimal("0.000")` bằng `stripTrailingZeros()` có thể cho zero với scale 0.
- Nếu cần scale cố định như 2 chữ số cho tiền tệ, cân nhắc `setScale(2, ...)` với chính sách làm tròn rõ ràng. Thao tác này không tương đương “bỏ số 0 vô nghĩa” trong mọi trường hợp.
- Tránh tự `new BigDecimal(0.1)` từ `double` khi cần đúng biểu diễn thập phân `0.1`; nó phản ánh giá trị nhị phân của `double`. Ưu tiên chuỗi thập phân hoặc `BigDecimal.valueOf(0.1)` khi phù hợp.
- Với tiền, currency cũng thuộc danh tính của giá trị nghiệp vụ: `1.00 USD` không giống `1.00 EUR` chỉ vì cùng `BigDecimal`.

## 9. Áp dụng vào bài toán cụ thể

### Tình huống A: danh sách ngưỡng số học

Bạn dùng `TreeSet<BigDecimal>` để lưu các ngưỡng so sánh. Cùng trị số `1.0` và `1.00` chỉ đại diện cho **một ngưỡng**, vì vậy natural ordering của `BigDecimal` là phù hợp. Dù vậy hãy nhớ khi lấy phần tử, scale có thể phản ánh **giá trị nào được chèn trước** chứ không phải một biểu diễn chuẩn duy nhất.

### Tình huống B: dữ liệu đo lường có ý nghĩa về độ chính xác

Bạn cần giữ `1.0` (một chữ số sau dấu thập phân) khác `1.00` (hai chữ số). Nếu cả hai đều phải nằm trong tập hợp có thứ tự, dùng comparator so **trị số trước, scale sau** để không mất một bản ghi.

### Tình huống C: khóa cache thống nhất trên HashMap và TreeMap

Nếu cùng trị số phải tra cứu như cùng key trong cả `HashMap` và `TreeMap`, chuẩn hóa dữ liệu ở **biên nhập** trước khi tạo key. Đảm bảo cả ghi và đọc dùng cùng quy tắc. Không trộn khóa chuẩn hóa và khóa gốc tùy nơi vì dễ tạo hành vi khó dự đoán.

### Tình huống D: kiểm thử và phát hiện lỗi

Dùng test có tối thiểu các giá trị `1`, `1.0`, `1.00`, `2.00`, `0.000` và một số có scale âm. Thử thêm, chứa, xóa, tìm map key; xác minh **đối tượng key được giữ lại** và **value sau khi put trùng**. Nếu dùng comparator custom, kiểm tra phản xạ, đối xứng về dấu, tính bắc cầu và nhất quán với yêu cầu equality.

## 10. Lỗi phổ biến

| Sai lầm | Điều xảy ra | Cách phòng tránh |
| --- | --- | --- |
| Nghĩ `BigDecimal.equals()` chỉ xét trị số | `1.0` và `1.00` bị xem là khác | Nhớ `equals` xét cả unscaled value và scale. |
| Nghĩ `TreeSet` xác định trùng bằng `equals()` | `TreeSet` bất ngờ giảm kích thước | Kiểm tra `compareTo`/`Comparator` vì so 0 được xem là trùng. |
| Dùng `TreeMap` và mong giữ hai key khác scale | Lệnh `put` sau cập nhật entry của key tương đương | Dùng comparator phân biệt scale nếu nghiệp vụ cần. |
| Nhìn `HashSet.size()` rồi suy ra `TreeSet.size()` giống hệt | Hai tập dùng quan hệ tương đương khác nhau | Xác định quy tắc equality dự kiến trước khi chọn cấu trúc. |
| Chỉ sắp theo scale | Các trị số khác nhưng cùng scale bị coi là trùng | Sắp theo trị số rồi dùng scale phá hòa. |
| Chuẩn hóa bằng `stripTrailingZeros` nhưng vẫn cần lưu scale gốc | Mất thông tin biểu diễn ban đầu | Giữ giá trị gốc riêng cho hiển thị/kiểm toán. |
| So `hashCode` để kết luận hai số bằng nhau | Hash collision có thể xảy ra | Dùng `equals` hoặc `compareTo` tùy ngữ nghĩa, không dùng hash như tiêu chí equality. |
| Tạo `BigDecimal` từ `double` tùy tiện | Giá trị thập phân có thể khác điều mong đợi | Dùng chuỗi chính xác hoặc `valueOf` theo mục đích. |
| Dùng `setScale` với chính sách làm tròn ngầm | Có thể làm mất thông tin số học hoặc gây `ArithmeticException` khi cần làm tròn | Nêu rõ scale và `RoundingMode` khi áp dụng. |

## 11. Checklist khi chọn SortedSet/SortedMap cho BigDecimal

1. Xác định **trùng nhau theo trị số** hay **trùng nhau theo trị số và scale**.
2. Kiểm tra nguồn dữ liệu có thể gửi các biểu diễn như `1.0`, `1.00`, `1E+0` hay không.
3. Nếu dùng natural ordering, hiểu `compareTo() == 0` quyết định trùng ở `TreeSet`/`TreeMap`.
4. Nếu cần nhất quán với `BigDecimal.equals()`, dùng comparator theo trị số rồi theo scale.
5. Nếu cần equality theo trị số trên cả các collection hash, dùng canonical key được chuẩn hóa nhất quán.
6. Không dùng hash hoặc ordinal hay định dạng hiển thị để thay cho quy tắc so sánh.
7. Tạo test cho thao tác `add`, `contains`, `put`, `get`, `remove` và quy tắc chèn trùng.
8. Giữ nguyên scale khi nó mang ý nghĩa nghiệp vụ; chỉ chuẩn hóa khi được phép.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` trả gì? `compareTo` thì sao?

**Đáp án:** `equals` trả `false` vì khác scale; `compareTo` trả `0` vì cùng trị số.

**Câu 2.** Thêm hai số đó vào `HashSet` và `TreeSet` mặc định sẽ có kích thước bao nhiêu?

**Đáp án:** `HashSet` có 2; `TreeSet` có 1.

**Câu 3.** Vì sao `TreeMap.put(1.00, value2)` có thể thay value đang gắn với key `1.0`?

**Đáp án:** Vì `TreeMap` nhận diện key tương đương qua so sánh thứ tự. `1.0.compareTo(1.00) == 0`.

**Câu 4.** Cần giữ hai giá trị cùng trị số khác scale trong `TreeSet`; chọn comparator nào?

**Đáp án:** So trị số theo natural order, sau đó so scale bằng `thenComparingInt(BigDecimal::scale)`.

**Câu 5.** Khi nào dùng `stripTrailingZeros` cho key?

**Đáp án:** Khi nghiệp vụ muốn những biểu diễn cùng trị số là cùng key, và chấp nhận bỏ khác biệt scale. Phải chuẩn hóa thống nhất khi ghi và tra cứu.

**Câu 6.** `BigDecimal` có bị lỗi vì `compareTo` không nhất quán với `equals` không?

**Đáp án:** Không. Đây là hai quan hệ được tài liệu API xác định. Lập trình viên cần hiểu rằng collection sắp xếp sử dụng comparator làm cơ sở tương đương và chọn cấu trúc/phép so phù hợp.

**Ghi nhớ:** `BigDecimal.equals()` xét **trị số và scale**, còn `BigDecimal.compareTo()` xét **trị số**. Vì `TreeSet`/`TreeMap` coi kết quả so sánh **0** là cùng phần tử/key, `1.0` và `1.00` chỉ chiếm một vị trí với natural ordering.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ordered-collections-006">Comparator không nhất quán với equals() và hợp đồng Set hoặc Map</a>

<details>
<summary>Xem chi tiết</summary>

# Comparator không nhất quán với `equals()` và hợp đồng `Set` hoặc `Map`

## 1. Vấn đề cốt lõi: hai cách định nghĩa “cùng một phần tử”

Trong Java, `TreeSet<E>` và `TreeMap<K,V>` là các cấu trúc có thứ tự. Chúng dùng **thứ tự tự nhiên** (`Comparable.compareTo`) hoặc **thứ tự do `Comparator.compare` chỉ định** để tìm kiếm và phân biệt các phần tử/khóa. Với hai giá trị `a` và `b`, chúng xem là **tương đương theo thứ tự** khi:

```java
comparator.compare(a, b) == 0
```

Đây là điều kiện dùng để xác định một vị trí tương đương trong cây; nó **không nhất thiết giống** `a.equals(b)`. Trong khi đó, giao diện `Set` và `Map` định nghĩa sự bằng nhau của phần tử và khóa theo hợp đồng `equals` (với cách xử lý `null` phù hợp):

- `Set` không được chứa hai phần tử bằng nhau theo `equals`.
- `Map` có mỗi khóa là duy nhất theo `equals`; ghi thêm cùng khóa logic sẽ thay thế giá trị của khóa ấy.
- `TreeSet` và `TreeMap` dùng kết quả so sánh bằng 0 để ra quyết định tương đương, vì vậy nếu `Comparator` không nhất quán với `equals`, hành vi trong cây có thể **không đáp ứng được hợp đồng chung của `Set` hoặc `Map`**.

Một bộ so sánh gọi là **nhất quán với `equals`** khi, đối với mọi cặp phần tử hợp lệ trong miền so sánh:

```text
comparator.compare(a, b) == 0    ⇔    a.equals(b) == true
```

Ký hiệu `⇔` là “khi và chỉ khi”: **hai chiều đều phải đúng**. Cần phân biệt điều này với yêu cầu cơ bản của một bộ so sánh là giữ thứ tự nhất quán (dấu đảo chiều, bắc cầu, và tương đương theo thứ tự ứng xử nhất quán với phần tử thứ ba). Một `Comparator` vẫn có thể xác định một thứ tự nội tại hợp lệ nhưng **không nhất quán với `equals`**; vấn đề lộ ra khi dùng nó làm quy tắc duy nhất của `TreeSet` hoặc `TreeMap`.

Theo tài liệu Java, cấu trúc cây vẫn có hành vi được xác định theo comparator ngay cả khi thứ tự không nhất quán với `equals`; tuy nhiên khi đó chúng có thể **vi phạm hợp đồng tổng quát của `Set` hoặc `Map`**. Đây là vấn đề về ngữ nghĩa API, không nhất thiết là ngoại lệ lúc chạy.

## 2. Hai dạng không nhất quán cần phân biệt

### Dạng A: `compare(a, b) == 0` nhưng `!a.equals(b)`

Ví dụ: hai chuỗi `"cat"` và `"dog"` có cùng độ dài, nhưng nội dung khác nhau. Một `Comparator` chỉ so độ dài:

```java
Comparator<String> byLength = Comparator.comparingInt(String::length);
System.out.println(byLength.compare("cat", "dog")); // 0
System.out.println("cat".equals("dog"));            // false
```

Đây là đoạn minh họa khi đặt trong một phương thức và import `java.util.Comparator`. Với `TreeSet` dùng `byLength`, chuỗi thứ hai không được thêm thành phần tử mới vì comparator cho kết quả 0. Tập có thể có kích thước 1 dù `equals` coi hai chuỗi là khác nhau; `contains("dog")` có thể là `true` mặc dù tập thực tế giữ chuỗi `"cat"`.

Với `TreeMap` dùng cùng comparator, `put("dog", value2)` có thể **ghi đè giá trị** gắn với khóa `"cat"`, vì bản đồ xem hai khóa có cùng thứ tự. Đối tượng khóa đã được lưu thường vẫn là khóa ban đầu, còn giá trị được cập nhật.

Về mặt hợp đồng `Set` và `Map`, đây là sự **gộp nhầm các giá trị khác nhau**.

### Dạng B: `a.equals(b)` là `true` nhưng `compare(a, b) != 0`

Giả sử lớp `Ticket` định nghĩa danh tính theo `id`, nhưng comparator lại ưu tiên `priority` trước `id`. Hai vé có cùng `id="T7"` nhưng priority khác nhau được `equals` coi là cùng vé, còn comparator lại phân biệt.

`TreeSet` có thể giữ **cả hai vé bằng nhau theo `equals`** vì phép so sánh của cây không trả 0. `TreeMap` cũng có thể chứa hai entry có khóa `equals` bằng nhau, trái với kỳ vọng từ giao diện `Map`. Một `HashSet` hoặc `HashMap` với `equals`/`hashCode` đúng lại xem chúng là cùng phần tử/khóa.

Đây là chiều ngược lại của lỗi: **tách nhầm một giá trị logic thành nhiều mục**.

| Điều kiện của cặp `a,b` | Theo `equals` | Theo comparator | Hậu quả có thể gặp trong cấu trúc cây |
| --- | --- | --- | --- |
| `compare(a,b)==0`, `!a.equals(b)` | Khác nhau | Tương đương | `TreeSet` bỏ qua một phần tử, `TreeMap` thay giá trị của khóa khác |
| `a.equals(b)`, `compare(a,b)!=0` | Bằng nhau | Khác vị trí | `TreeSet` giữ hai phần tử bằng nhau, `TreeMap` giữ hai khóa bằng nhau |
| `a.equals(b)`, `compare(a,b)==0` | Bằng nhau | Tương đương | Hai cơ chế đồng thuận |
| `!a.equals(b)`, `compare(a,b)!=0` | Khác nhau | Khác vị trí | Hai cơ chế đồng thuận |

Bảng trên giả định comparator vẫn tuân thủ **hợp đồng thứ tự** và khóa không thay đổi trong khi nằm trong cây. Một comparator vi phạm bắc cầu hoặc đổi kết quả tùy thời điểm có thể gây những vấn đề nghiêm trọng khác, nằm ngoài sự không nhất quán với `equals`.

## 3. Chương trình Java 17+ minh họa cả hai lỗi và cách sửa

Lưu toàn bộ mã sau thành **`ComparatorContractDemo.java`**. Nó dùng `record Word` (bình đẳng theo chuỗi `text`) và `Ticket` (bình đẳng theo `id`). Phần đầu chỉ so độ dài; phần sau dùng thứ tự theo priority trái với danh tính vé.

```java
import java.util.*;

public class ComparatorContractDemo {
    record Word(String text) {}

    static final class Ticket {
        private final String id;
        private final int priority;

        Ticket(String id, int priority) {
            this.id = Objects.requireNonNull(id);
            this.priority = priority;
        }

        String id() {
            return id;
        }

        int priority() {
            return priority;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Ticket t && id.equals(t.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    public static void main(String[] args) {
        Word cat = new Word("cat");
        Word dog = new Word("dog");
        Comparator<Word> byLength =
                Comparator.comparingInt(w -> w.text().length());

        TreeSet<Word> byLengthSet = new TreeSet<>(byLength);
        boolean first = byLengthSet.add(cat);
        boolean second = byLengthSet.add(dog);

        System.out.println("words equals = " + cat.equals(dog));
        System.out.println("words compare = " + byLength.compare(cat, dog));
        System.out.println("tree set add = " + first + ", " + second);
        System.out.println("tree set size = " + byLengthSet.size());
        System.out.println("tree set contains dog = "
                + byLengthSet.contains(dog));
        System.out.println("hash set size = "
                + new HashSet<>(List.of(cat, dog)).size());

        TreeMap<Word, String> wordMap = new TreeMap<>(byLength);
        wordMap.put(cat, "first");
        String previous = wordMap.put(dog, "second");

        System.out.println("tree map previous = " + previous);
        System.out.println("tree map size = " + wordMap.size());
        System.out.println("tree map get dog = " + wordMap.get(dog));
        System.out.println("tree map stored key = " + wordMap.firstKey());

        Comparator<Word> byLengthThenText =
                byLength.thenComparing(Word::text);
        TreeSet<Word> fixedWords = new TreeSet<>(byLengthThenText);
        fixedWords.add(cat);
        fixedWords.add(dog);
        System.out.println("fixed words size = " + fixedWords.size());

        Ticket low = new Ticket("T7", 1);
        Ticket high = new Ticket("T7", 9);
        Comparator<Ticket> byPriorityThenId =
                Comparator.comparingInt(Ticket::priority)
                          .thenComparing(Ticket::id);

        TreeSet<Ticket> ticketSet = new TreeSet<>(byPriorityThenId);
        ticketSet.add(low);
        ticketSet.add(high);

        TreeMap<Ticket, String> ticketMap =
                new TreeMap<>(byPriorityThenId);
        ticketMap.put(low, "low");
        ticketMap.put(high, "high");

        System.out.println("tickets equals = " + low.equals(high));
        System.out.println("tickets compare = "
                + byPriorityThenId.compare(low, high));
        System.out.println("ticket tree set size = " + ticketSet.size());
        System.out.println("ticket hash set size = "
                + new HashSet<>(List.of(low, high)).size());
        System.out.println("ticket tree map size = " + ticketMap.size());

        TreeSet<Ticket> fixedTickets =
                new TreeSet<>(Comparator.comparing(Ticket::id));
        fixedTickets.add(low);
        fixedTickets.add(high);
        System.out.println("fixed tickets size = " + fixedTickets.size());
    }
}
```

Trong thư mục chứa tệp, biên dịch và chạy:

```bash
javac ComparatorContractDemo.java
java ComparatorContractDemo
```

Đầu ra của chương trình:

```text
words equals = false
words compare = 0
tree set add = true, false
tree set size = 1
tree set contains dog = true
hash set size = 2
tree map previous = first
tree map size = 1
tree map get dog = second
tree map stored key = Word[text=cat]
fixed words size = 2
tickets equals = true
tickets compare = -1
ticket tree set size = 2
ticket hash set size = 1
ticket tree map size = 2
fixed tickets size = 1
```

### Diễn giải kết quả đầu tiên: comparator gộp nhầm

- `cat.equals(dog)` trả `false` vì `Word` là `record`, so thành phần `text`; `"cat"` và `"dog"` khác nhau.
- `byLength.compare(cat,dog)` trả `0` vì cả hai có độ dài 3.
- `byLengthSet.add(cat)` trả `true`; `add(dog)` trả `false` vì cây xem `dog` tương đương với `cat` đã có. Kích thước cuối là 1.
- `byLengthSet.contains(dog)` là `true` theo cách tìm kiếm của cây, mặc dù phần tử được lưu là `cat`. Tương phản, `HashSet` dựa vào `equals`/`hashCode` của `Word` nên có kích thước 2.
- `wordMap.put(dog,"second")` trả giá trị trước đó `"first"`; kích thước vẫn 1 và `get(dog)` là `"second"`. Đối tượng khóa trong entry là `Word[text=cat]`, cho thấy **giá trị mới thay thế theo comparator, nhưng không tự biến khóa cũ thành khóa mới**.

Đây là điểm dễ nhầm: `contains`, `get` và `put` của cây tuân theo comparator đã cung cấp. Chúng không thực hiện bước kiểm tra cuối `equals` để chữa trường hợp comparator trả 0 sai với danh tính logic.

### Diễn giải kết quả thứ hai: comparator tách nhầm

- `low.equals(high)` là `true` vì `Ticket.equals` chỉ dựa trên `id="T7"`; `hashCode` cũng dùng `id` nên hợp đồng hash được giữ.
- `byPriorityThenId.compare(low,high)` trả `-1` vì priority 1 nhỏ hơn 9, dù cùng `id`.
- `TreeSet` và `TreeMap` vẫn giữ 2 mục vì comparator coi chúng khác nhau. `HashSet` coi chúng là một giá trị logic, nên kích thước 1.
- `Comparator.comparing(Ticket::id)` chỉ so `id`; hai vé cùng `id` sẽ có kết quả 0, nên `fixedTickets` có kích thước 1, khớp chính sách equality của `Ticket`.

Hai tình huống này minh họa **cả hai hướng cần kiểm tra** khi yêu cầu comparator nhất quán với `equals`.

## 4. Hợp đồng `Set` và `Map` bị ảnh hưởng cụ thể thế nào?

### 4.1. `Set`: thêm, tìm kiếm, xóa và duy nhất

Giao diện `Set` xác định một tập các phần tử **không trùng nhau theo `equals`**. Về bản chất, `add(e)` trả về `false` nếu tập đã chứa phần tử bằng `e`; `contains(o)` có nghĩa tìm sự hiện diện của phần tử bằng `o`.

Với `TreeSet`, cây sử dụng comparator để xác định xem phần tử có tương đương với mục đã có không:

- Nếu `compare(a,b)==0` nhưng `!a.equals(b)`, việc thêm `b` sau `a` có thể bị từ chối; `contains(b)` có thể trả `true` dù `b` khác mọi đối tượng hiện đang lưu theo `equals`.
- `remove(b)` cũng có thể xóa mục đại diện `a` nếu `compare(a,b)==0`, vì mục tiêu tìm kiếm được xác định bằng thứ tự.
- Nếu `a.equals(b)` nhưng `compare(a,b)!=0`, tập có thể chứa cả hai phần tử, phá kỳ vọng không trùng nhau.

Với `HashSet`, điều kiện bằng nhau được xây dựng trên `equals` và `hashCode`; comparator không được dùng. Sự khác biệt giữa `HashSet` và `TreeSet` trong ví dụ là kết quả trực tiếp của hai quy tắc định danh.

### 4.2. `Map`: khóa tương đương bị thay hoặc giữ trùng

Giao diện `Map` định nghĩa khóa duy nhất theo `equals`. Với `HashMap`, hai khóa bằng nhau theo `equals` và có hash tương thích đại diện cùng một mapping.

`TreeMap` cần một thứ tự để định vị khóa, nên dùng `compare(k1,k2)==0`:

- Khi comparator trả 0 cho hai khóa khác nhau theo `equals`, `put(k2,v2)` có thể cập nhật entry của `k1`. Giá trị trước đó bị thay; `size()` không tăng. `get(k2)` tìm thấy entry bằng quy tắc của comparator.
- Khi comparator trả khác 0 cho hai khóa bằng nhau theo `equals`, `TreeMap` có thể giữ cả hai entry. Khi đó phép tra cứu bằng một đối tượng thứ ba bằng các khóa theo `equals` có thể phụ thuộc kết quả comparator và không khớp với kỳ vọng của giao diện `Map`.
- Các view `keySet()`, `entrySet()` và các thao tác thứ tự như `firstKey()`, `subMap()`, `headMap()`, `tailMap()` phụ thuộc vào quan hệ thứ tự, nên phạm vi dữ liệu cũng có thể khác trực giác nếu người dùng nghĩ “bằng khóa” có nghĩa `equals`.

**Lưu ý quan trọng:** Đây là lỗi ở **chính sách định nghĩa khóa** hoặc cách dùng cấu trúc, không nhất thiết là lỗi triển khai nội bộ của `TreeMap`. Nếu ứng dụng cố ý dùng đồng nhất theo comparator (ví dụ chuỗi không phân biệt hoa thường), kết quả có thể phù hợp yêu cầu nghiệp vụ, nhưng cần hiểu nó **khác ngữ nghĩa `equals` của kiểu khóa** và có thể phá hợp đồng `Map` tổng quát.

### 4.3. Vì sao “chỉ cần comparator sắp xếp đúng” vẫn chưa đủ?

Một comparator so theo độ dài có thể giữ hoàn toàn các quy tắc của thứ tự: dấu đảo chiều, bắc cầu, các chuỗi cùng độ dài ứng xử giống nhau khi so với chuỗi thứ ba. Nó **đúng như một phép sắp xếp theo độ dài**, nhưng không phù hợp nếu `TreeSet` phải chứa mọi chuỗi khác nội dung.

Ngược lại, comparator theo priority rồi id cũng có thể là một thứ tự hợp lệ, nhưng không phù hợp khi `Ticket.equals` định nghĩa hai vé cùng id là cùng một vé.

Do đó, cần tách hai câu hỏi: **(1) Bộ so sánh có tuân thủ hợp đồng `Comparator` không? (2) Kết quả 0 có đại diện đúng equality của khóa/phần tử trong cấu trúc cây không?**

## 5. Cách sửa: thiết kế tiêu chí so sánh theo danh tính logic

### 5.1. Thêm tie-breaker cho những đối tượng `equals` khác nhau

Nếu `Word.equals` coi toàn bộ `text` là danh tính, đừng chỉ so chiều dài. Tạo comparator theo **độ dài trước, nội dung sau**:

```java
Comparator<Word> byLengthThenText =
        Comparator.comparingInt((Word w) -> w.text().length())
                  .thenComparing(Word::text);
```

Đây là một biểu thức minh họa trong ngữ cảnh có `Word`, cần `import java.util.Comparator`. Nó trả về 0 **chỉ khi** cả độ dài và nội dung bằng nhau; vì nội dung giống nhau tất yếu cùng độ dài, phép so sánh 0 tương đương với `Word.equals` trong ví dụ.

`TreeSet` vẫn sắp xếp theo độ dài tăng dần. Khi bằng độ dài, nó phân biệt `"cat"` và `"dog"` theo thứ tự từ điển. Kết quả đúng là cả hai cùng được giữ.

Không nên dùng các tie-breaker tùy tiện như `System.identityHashCode` để “ép khác nhau”: identity hash vẫn có thể va chạm, và hai đối tượng bằng nhau theo giá trị lại có thể bị so thành khác. Tie-breaker phải phù hợp với **chính sách equality**, không phải tìm một số ngẫu nhiên để tránh kết quả 0.

### 5.2. Loại trường ngoài danh tính khỏi comparator dùng cho cây

Trong `Ticket`, `equals` chỉ dựa trên `id`. Muốn dùng `TreeSet<Ticket>` như một `Set` theo danh tính vé, comparator phải cho 0 khi id bằng nhau:

```java
Comparator<Ticket> byId = Comparator.comparing(Ticket::id);
```

Đây là dòng minh họa trong ngữ cảnh đã định nghĩa lớp `Ticket` với getter `id()`. Khi `low` và `high` có cùng id, bộ so sánh cho 0, thống nhất với `equals`. Vì `id` là chuỗi không null và tham gia duy nhất vào `equals`, comparator này còn phân biệt mọi giá trị id khác nhau.

Nếu yêu cầu **hiển thị** vé theo `priority` nhưng vẫn muốn định danh theo id, không nên buộc `TreeSet` vừa sắp theo priority vừa phải coi cùng id là trùng khi priority khác. Có thể lưu vé theo id bằng `Map<String,Ticket>`, rồi tạo danh sách và sắp xếp bản trình bày bằng comparator priority. Quy tắc tập hợp/khóa và quy tắc hiển thị lúc đó có thể khác nhau mà không gây nhập nhằng.

### 5.3. Có nên sửa `equals` để khớp comparator?

Có thể sửa `equals` **chỉ khi yêu cầu nghiệp vụ thật sự thay đổi định nghĩa danh tính**. Ví dụ, nếu một vé có id và phiên bản, muốn hai vé cùng id nhưng phiên bản khác nhau là hai giá trị riêng, có thể để cả `equals`, `hashCode` và comparator xem phiên bản là thành phần danh tính.

Không nên sửa `equals` chỉ để chữa một comparator dùng cho màn hình sắp xếp: thay đổi `equals` còn ảnh hưởng tới `HashSet`, `HashMap`, caching, deduplication và toàn bộ mã dựa vào giá trị logic của kiểu đó.

## 6. Khi nào chủ động chấp nhận comparator không nhất quán với `equals`?

### Sắp xếp danh sách thông thường

Khi `List.sort(comparator)` hoặc `Collections.sort(list,comparator)`, hai đối tượng có kết quả so sánh 0 có thể cùng xuất hiện trong danh sách. Sắp xếp không có hợp đồng “mỗi giá trị chỉ xuất hiện một lần” như `Set`. Vì vậy, **comparator chỉ theo độ dài** hoàn toàn có thể phù hợp khi cần nhóm các chuỗi theo chiều dài trong một `List`.

Điều kiện vẫn là comparator phải tạo một thứ tự nhất quán theo hợp đồng so sánh. Sắp xếp không tự sửa được comparator có vòng lặp hay thiếu tính bắc cầu.

### Muốn cố ý coi khóa khác `equals` là cùng khóa

Ví dụ, một ứng dụng xem `"Admin"` và `"admin"` là cùng tên người dùng khi xử lý trong một `TreeMap<String,V>` với `String.CASE_INSENSITIVE_ORDER`. Đây có thể là lựa chọn có chủ đích, nhưng `String.equals` vẫn phân biệt chữ hoa và thường. Khi giao tiếp với các API khác sử dụng `HashMap`/`HashSet`, kết quả gộp và nhận diện có thể khác nhau.

Nếu định danh theo chữ thường là quy tắc chính của nghiệp vụ, thường dễ kiểm soát hơn khi **chuẩn hóa khóa trước khi lưu** (với chính sách locale/Unicode phù hợp) hoặc thiết kế một kiểu khóa có `equals`, `hashCode` và comparator cùng mô tả chính sách. Tránh đổi ý nghĩa equality ngầm giữa các collection khác nhau.

### `Comparable` và `BigDecimal`

Vấn đề tương tự cũng xảy ra với **thứ tự tự nhiên** do `compareTo` cung cấp mà không cần comparator riêng. Chẳng hạn:

```java
BigDecimal x = new BigDecimal("2.0");
BigDecimal y = new BigDecimal("2.00");
System.out.println(x.compareTo(y)); // 0
System.out.println(x.equals(y));    // false
```

Đoạn này cần `import java.math.BigDecimal` và đặt trong một phương thức. `BigDecimal.compareTo` so giá trị số; `BigDecimal.equals` còn xét `scale`. `TreeSet<BigDecimal>` coi hai giá trị này tương đương theo thứ tự, còn `HashSet<BigDecimal>` có thể giữ cả hai. Đây là một ngoại lệ quen thuộc giúp thấy yêu cầu **nhất quán với `equals` là rất quan trọng với cấu trúc cây**.

## 7. Kiểm thử và rà soát bộ so sánh cho tập/bản đồ có thứ tự

Không chỉ kiểm tra danh sách được sắp đẹp mắt. Với mọi kiểu khóa/phần tử muốn đưa vào `TreeSet` hoặc `TreeMap`, hãy kiểm tra **hai chiều tương thích**:

```java
boolean sameByEquals = Objects.equals(a, b);
boolean sameByComparator = comparator.compare(a, b) == 0;

if (sameByEquals != sameByComparator) {
    throw new AssertionError("Comparator inconsistent with equals");
}
```

Đây là **đoạn kiểm thử** có các biến `a`, `b` và `comparator` được chuẩn bị trước; không phải chương trình chạy độc lập. Phải thử các cặp cùng giá trị, khác giá trị, cùng khóa chính nhưng khác trường phụ, khác khóa chính nhưng cùng trường sắp xếp, và giá trị biên. Đồng thời xác minh comparator có thể so sánh các đối tượng trong miền mà collection hỗ trợ.

Ngoài consistency-with-equals, hãy kiểm tra đầy đủ hợp đồng thứ tự:

1. **Dấu đảo chiều:** `Integer.signum(compare(a,b)) == -Integer.signum(compare(b,a))`. So **dấu**, không so `compare(a,b) == -compare(b,a)` vì phủ định `Integer.MIN_VALUE` có thể tràn.
2. **Bắc cầu:** nếu `compare(a,b) > 0` và `compare(b,c) > 0` thì `compare(a,c) > 0`.
3. **Nhất quán của tương đương theo thứ tự:** nếu `compare(a,b) == 0` thì dấu của `compare(a,c)` phải giống dấu của `compare(b,c)` với mọi `c` có thể so sánh.
4. **Tính ổn định:** khóa không được đổi trường tham gia so sánh khi đang được lưu trong cây. Nếu thay khóa, cần xóa entry cũ rồi thêm lại hoặc xây lại cấu trúc.
5. **Tương thích với equality:** đối với `Set`/`Map` theo danh tính logic, kiểm tra `compare(a,b)==0` khi và chỉ khi `a.equals(b)`.

Có thể viết một bộ kiểm thử nhỏ để chèn cả các cặp vào `TreeSet` lẫn `HashSet`, kiểm tra kích thước, `contains` và `remove`; và chèn vào `TreeMap`, kiểm tra `put` trả giá trị trước đó, `size`, `get`, `firstKey`. Khi mục tiêu là dùng `TreeSet`/`TreeMap` đúng hợp đồng chung, kết quả của bộ kiểm thử phải phản ánh **cùng một chính sách bình đẳng**.

Một phép kiểm thử ví dụ về điều kiện bất nhất:

```java
Comparator<String> byLength = Comparator.comparingInt(String::length);
String a = "cat";
String b = "dog";

assert !a.equals(b);
assert byLength.compare(a, b) == 0;
```

Đoạn trên cần import `java.util.Comparator`, chạy trong phạm vi phương thức và khi bật assertions (`-ea`). Nó cố ý chứng minh comparator theo độ dài **không nhất quán** với `String.equals`. Để bộ kiểm thử luôn kiểm tra dù assertions bị tắt, dùng một `if` và ném `AssertionError` như ví dụ trước.

## 8. Lỗi phổ biến và cách khắc phục

| Sai sót | Hiện tượng | Cách xử lý |
| --- | --- | --- |
| `Comparator.comparingInt(String::length)` dùng làm thứ tự của `TreeSet<String>` cần lưu mọi chuỗi khác nội dung | Hai chuỗi cùng độ dài bị gộp | Thêm tie-breaker `thenComparing(Comparator.naturalOrder())` hoặc so cả nội dung |
| Comparator trả 0 cho khóa mà `equals` coi là khác | `TreeMap.put` thay giá trị ngoài ý muốn | Dùng các trường định danh làm tiêu chí phân biệt cuối cùng |
| Comparator phân biệt các khóa `equals` bằng nhau | `TreeSet` hoặc `TreeMap` giữ hai mục bằng nhau | Loại trường không tham gia danh tính khỏi tiêu chí phân biệt của cây |
| Cho rằng `TreeSet.contains` luôn gọi `equals` | `contains` có thể trả kết quả khó hiểu | Hiểu nó dựa vào `compare==0` trong thứ tự cây |
| Thêm tie-breaker bằng identity hash | Không bảo đảm uniqueness; phá equality theo giá trị | Dùng thành phần danh tính bền vững và tương thích `equals` |
| Thay đổi khóa sau khi thêm vào `TreeSet`/`TreeMap` | Vị trí cây không còn khớp thứ tự mới | Dùng khóa bất biến; xóa và thêm lại nếu cần |
| Comparator không giữ bắc cầu hoặc tính nhất quán | Sort/lookup có thể sai, một số sort có thể báo lỗi hợp đồng | Kiểm thử cặp và bộ ba, dùng `Comparator.comparing` / `thenComparing` |
| Coi `TreeSet` và `HashSet` là hoàn toàn thay thế được với mọi comparator | Kích thước/tra cứu khác nhau giữa hai kiểu tập | Bảo đảm `compare==0 ⇔ equals` khi cần cùng ngữ nghĩa Set |
| Cố ý gộp khóa không phân biệt hoa thường mà không ghi rõ chính sách | Dữ liệu mất phân biệt chữ hoa và thường ở cây nhưng vẫn phân biệt trong hash | Chuẩn hóa khóa hoặc thiết kế kiểu khóa nhất quán |

## 9. Bài tập tự kiểm tra

**Tình huống 1.** `TreeSet<String>` dùng comparator so độ dài, thêm `"red"` rồi `"sun"`. `size()` và `contains("sun")` có thể là gì?

`size()` bằng 1; `contains("sun")` là `true` vì độ dài đều 3. Tuy nhiên `"red".equals("sun")` là `false`. Nếu muốn cả hai chuỗi, cần thêm tiêu chí phân biệt nội dung trong comparator.

**Tình huống 2.** Hai `Ticket` có cùng `id` nhưng priority 3 và 8. `equals` chỉ so id, comparator so priority rồi id. `TreeSet` có thể lưu mấy phần tử?

Hai phần tử, dù `equals` coi chúng là một. Comparator phân biệt vì priority khác nhau. Nếu khóa logic là id, một tập theo danh tính nên so id làm tiêu chí tương đương.

**Tình huống 3.** Một comparator nhất quán về dấu và bắc cầu nhưng trả 0 cho hai khóa khác nhau theo `equals`. Có phải comparator bị lỗi mọi lúc?

Không. Nó có thể phù hợp khi **chỉ sắp xếp `List`** hoặc ứng dụng chủ ý gom khóa theo chính sách riêng. Nhưng khi dùng làm thứ tự của `TreeSet`/`TreeMap` mà kỳ vọng hợp đồng `Set`/`Map` dựa trên `equals`, nó là không phù hợp.

**Tình huống 4.** Khi thêm khóa mới được comparator xem là bằng khóa đã có trong `TreeMap`, đối tượng khóa ban đầu có chắc được thay bằng đối tượng khóa mới không?

Không. Trong ví dụ ở mục 3, giá trị được thay nhưng entry vẫn giữ `Word[text=cat]` làm khóa. Cần phân biệt **cập nhật giá trị** với **thay đối tượng khóa được lưu**.

**Nguyên tắc áp dụng:** Với tập hoặc bản đồ có thứ tự, hãy xác định ý nghĩa “một phần tử/khóa duy nhất” **trước khi** viết comparator. Khi cần tương thích đúng hợp đồng `Set`/`Map`, bảo đảm `compare(a,b)==0` đúng **khi và chỉ khi** `a.equals(b)`. Nếu chỉ cần sắp xếp để hiển thị, có thể dùng tiêu chí khác trên `List` mà không làm thay đổi danh tính logic của dữ liệu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ordered-collections-007">Thay đổi trường sắp xếp khi đối tượng đang nằm trong cây</a>

<details>
<summary>Xem chi tiết</summary>

# Thay đổi trường sắp xếp khi đối tượng đang nằm trong cây `TreeSet` hoặc `TreeMap`

## 1. Điều gì xảy ra khi trường dùng để sắp xếp bị thay đổi?

`TreeSet<E>` và `TreeMap<K, V>` của Java duy trì dữ liệu trong cấu trúc cây tìm kiếm có cân bằng, được sắp xếp theo **thứ tự tự nhiên** (`Comparable.compareTo`) hoặc một **`Comparator`** được cung cấp. Khi thêm phần tử hoặc khóa, cây dùng kết quả so sánh để xác định vị trí và cân bằng cấu trúc.

Nếu bạn **thay đổi một trường tham gia `compareTo`/`Comparator` khi đối tượng vẫn còn trong cây**, thứ tự đúng theo giá trị mới có thể không còn khớp vị trí đã xác định từ trước. Cây **không tự phát hiện thay đổi bên trong đối tượng**, không tự rút nút ra rồi chèn lại, và không tự sắp xếp lại toàn bộ mỗi lần đối tượng đổi trạng thái.

Ví dụ, tại lúc chèn một cây có ba công việc:

```text
Các khóa theo thứ tự: A(10), B(20), C(30)
Trong đó số trong ngoặc là độ ưu tiên dùng để so sánh.

Đổi A.priority: 10 -> 25 khi A vẫn nằm trong cây.
Giá trị mới cần xếp: B(20), A(25), C(30).
Nhưng cây vẫn có thể duyệt theo vị trí cũ: A(25), B(20), C(30).
```

Khi đó, giả định mỗi nhánh trái chứa các khóa nhỏ hơn và nhánh phải chứa các khóa lớn hơn theo bộ so sánh hiện tại đã bị phá vỡ. **Không nên tiếp tục dựa vào kết quả tìm, thêm, xóa, duyệt hoặc truy vấn khoảng** của tập hợp đó như thể cấu trúc vẫn hợp lệ.

Đây là vấn đề **đột biến khóa sắp xếp khi đã lưu trong cây** (mutable ordering key), không phụ thuộc vào việc trường đó có được khai báo là `private` hay không. Setter, phản chiếu (reflection), thao tác qua tham chiếu khác, hoặc thay đổi trạng thái bên ngoài mà `Comparator` đọc đều có thể gây tình huống tương tự.

## 2. Vì sao `TreeSet`/`TreeMap` không sắp xếp lại tự động?

Trong cấu trúc có thứ tự, việc tìm kiếm dựa trên các so sánh nối tiếp từ một nút tới các nhánh cây. Thao tác như `TreeSet.contains(value)` hoặc `TreeMap.get(key)` không quét tất cả nút để kiểm tra lại mọi trường; chúng dựa vào **quan hệ thứ tự đã dùng để tổ chức cây**.

Khi một khóa từng nằm ở nhánh dành cho giá trị nhỏ bị sửa thành giá trị lớn, phép tìm với khóa mới có thể đi theo nhánh khác và **không gặp đúng đối tượng đã lưu**. Cũng có thể xuất hiện các hậu quả:

- `contains()` / `get()` trả kết quả bất ngờ (chẳng hạn báo không tìm thấy dù đối tượng còn trong tập hợp).
- `remove()` hoặc `TreeMap.remove()` không định vị được phần tử/khóa, vì thuật toán tìm theo giá trị đã thay đổi.
- Thứ tự duyệt không còn tăng dần theo `compareTo()`/`Comparator` hiện tại.
- Việc thêm giá trị mới có thể gặp xung đột với khóa cũ, để lại những phần tử mà phép so sánh hiện nay coi là tương đương hoặc khiến tập hợp có hành vi khó dự đoán.
- `first()`, `last()`, `floor()`, `ceiling()`, `headSet()`, `tailSet()`, `subSet()` và các thao tác khoảng trên `NavigableMap` có thể không còn phản ánh đúng thứ tự kỳ vọng.

Những triệu chứng cụ thể có thể thay đổi theo cấu trúc cây và đường đi thuật toán. Việc một lần gọi `contains()` tình cờ vẫn trả `true` **không chứng minh việc sửa khóa tại chỗ là an toàn**.

## 3. Điều kiện phải giữ ổn định

Trong thời gian một đối tượng được dùng làm **phần tử của `TreeSet`** hoặc **khóa của `TreeMap`**, các dữ liệu quyết định kết quả so sánh với phần tử/khóa khác phải ổn định.

Ví dụ nếu `compareTo()` so theo `priority` rồi `id`, cả `priority` lẫn `id` phải có ngữ nghĩa cố định đối với vị trí trong cây. Nếu `Comparator` so theo `user.getDepartment().getName()`, việc đổi tên phòng ban cũng có thể thay đổi kết quả so sánh **dù trường trực tiếp trên đối tượng `user` không đổi**.

Đây là lý do nên ưu tiên **khóa bất biến**: các trường dùng để so sánh là `final`, các thành phần lồng bên trong cũng không thể bị sửa theo cách ảnh hưởng thứ tự. `record` có thể hữu ích cho khóa giá trị bất biến nông; cần lưu ý rằng trường `final` trỏ tới một đối tượng mutable vẫn có thể khiến dữ liệu được comparator đọc thay đổi.

## 4. Ví dụ Java 17+ có thể biên dịch và chạy

Chương trình sau cho thấy việc thay đổi trường sắp xếp tại chỗ làm thứ tự duyệt không còn tăng dần, và hai cách tạo lại thứ tự đúng. Lưu dưới tên **`MutableTreeKeyDemo.java`**:

```java
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

public class MutableTreeKeyDemo {
    private static final class Job implements Comparable<Job> {
        private final String id;
        private int priority;

        Job(String id, int priority) {
            this.id = Objects.requireNonNull(id);
            this.priority = priority;
        }

        void setPriority(int priority) {
            this.priority = priority;
        }

        @Override
        public int compareTo(Job other) {
            int byPriority = Integer.compare(priority, other.priority);
            return byPriority != 0 ? byPriority : id.compareTo(other.id);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Job other)) return false;
            return priority == other.priority && id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, priority);
        }

        @Override
        public String toString() {
            return id + "(" + priority + ")";
        }
    }

    private static boolean isSorted(TreeSet<Job> set) {
        Job previous = null;
        for (Job current : set) {
            if (previous != null && previous.compareTo(current) > 0) {
                return false;
            }
            previous = current;
        }
        return true;
    }

    public static void main(String[] args) {
        Job a = new Job("A", 10);
        Job b = new Job("B", 20);
        Job c = new Job("C", 30);
        TreeSet<Job> broken = new TreeSet<>();
        broken.add(a);
        broken.add(b);
        broken.add(c);
        System.out.println("before mutation: " + broken);

        a.setPriority(25); // Sai: phần tử vẫn nằm trong cây
        System.out.println("after mutation: " + broken);
        System.out.println("order still valid: " + isSorted(broken));

        TreeSet<Job> rebuilt = new TreeSet<>();
        for (Job job : broken) {
            rebuilt.add(job);
        }
        System.out.println("rebuilt: " + rebuilt);
        System.out.println("rebuilt order valid: " + isSorted(rebuilt));

        Job safeA = new Job("A", 10);
        TreeSet<Job> safe = new TreeSet<>();
        safe.add(safeA);
        safe.add(new Job("B", 20));
        safe.add(new Job("C", 30));
        boolean removed = safe.remove(safeA); // Xóa bằng giá trị cũ
        safeA.setPriority(25);
        boolean inserted = safe.add(safeA);   // Chèn bằng giá trị mới
        System.out.println("safe remove: " + removed);
        System.out.println("safe reinsert: " + inserted);
        System.out.println("safe result: " + safe);

        Job mapKey = new Job("K", 5);
        TreeMap<Job, String> map = new TreeMap<>();
        map.put(mapKey, "pending");
        String oldValue = map.remove(mapKey); // Xóa trước khi sửa khóa
        mapKey.setPriority(7);
        map.put(mapKey, oldValue);
        map.replace(mapKey, "done");
        System.out.println("TreeMap value: " + map.get(mapKey));
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac MutableTreeKeyDemo.java
java MutableTreeKeyDemo
```

Kết quả với chương trình này trên các triển khai Java thông dụng tuân theo hợp đồng collection:

```text
before mutation: [A(10), B(20), C(30)]
after mutation: [A(25), B(20), C(30)]
order still valid: false
rebuilt: [B(20), A(25), C(30)]
rebuilt order valid: true
safe remove: true
safe reinsert: true
safe result: [B(20), A(25), C(30)]
TreeMap value: done
```

Ở đây, `Job.compareTo` xét `priority` trước rồi `id` để hai công việc trùng ưu tiên nhưng khác mã vẫn được phân biệt. `equals()` và `hashCode()` được cài đặt phù hợp với hai trường đó; điều này **không làm phép đổi `priority` khi phần tử đang trong cây trở nên an toàn**.

Chương trình cố ý không dựa vào kết quả `broken.contains(a)` sau khi sửa trường, vì kết quả có thể tùy thuộc cách tổ chức cây. Việc thứ tự duyệt `[A(25), B(20), C(30)]` không tăng dần đã đủ minh họa vi phạm bất biến thứ tự; cây cũ không nên tiếp tục được sử dụng. Ví dụ tạo `rebuilt` bằng cách chèn **từng phần tử** theo so sánh hiện tại, không dựa vào việc sao chép nguyên trạng cây đã sai.

Trong phần `safe`, việc `remove(safeA)` diễn ra **trước** khi `safeA` thay đổi, nên phép xóa có thể tìm phần tử bằng các dữ liệu sắp xếp cũ. Chèn lại sau khi đổi giá trị giúp cấu trúc cây tổ chức vị trí mới. Nếu phép xóa thất bại, phải xử lý lỗi thay vì tiếp tục sửa và chèn như thể nó đã thành công.

## 5. Cách sửa an toàn trong ứng dụng

### Cách 1: Dùng đối tượng khóa bất biến, thay bằng đối tượng mới

Nếu muốn thay độ ưu tiên của phần tử, thiết kế một giá trị mới rồi cập nhật tập hợp:

```java
record JobKey(String id, int priority) { }

// Minh họa trong một phương thức:
// TreeSet<JobKey> sorted = new TreeSet<>(
//     Comparator.comparingInt(JobKey::priority).thenComparing(JobKey::id));
// JobKey oldKey = new JobKey("A", 10);
// sorted.add(oldKey);
// sorted.remove(oldKey);
// sorted.add(new JobKey("A", 25));
```

`JobKey` là một `record` và phần chú thích bên dưới mô tả **các dòng đặt trong phương thức**; muốn chạy đầy đủ, khai báo comparator, import `TreeSet`/`Comparator` và đặt mã trong lớp/phương thức thích hợp. Ý chính là không thay đổi trường dùng để xếp thứ tự trên đối tượng đang được lưu. Với `record` chỉ chứa `String` và `int` như trên, cả hai thành phần đều bất biến.

### Cách 2: `remove` trước, mutate sau, rồi `add` lại

Nếu bắt buộc phải dùng đối tượng có thể thay đổi:

```text
1. Tìm phần tử/khóa với trạng thái cũ.
2. Loại bỏ khỏi cây bằng trạng thái cũ.
3. Chỉ sau khi xóa thành công, thay đổi các trường so sánh.
4. Thêm trở lại với trạng thái mới.
5. Kiểm tra kết quả thêm/cập nhật và xử lý xung đột compareTo == 0.
```

Đây là trình tự áp dụng được với `TreeSet`; đối với `TreeMap`, cần lưu lại **giá trị gắn với khóa** trước khi xóa, rồi `put` khóa đã thay đổi cùng giá trị cần thiết. Đừng đánh đồng `TreeMap.remove(k)` trả `null` với việc chắc chắn không có khóa nếu map cho phép giá trị `null`: dùng `containsKey(k)` trước khi xóa hoặc quản lý trạng thái theo nghiệp vụ.

**Quan trọng:** Không sửa khóa **trước** rồi mới `remove`; lúc đó thuật toán xóa cũng có thể không tìm thấy nó. Nếu lỡ sửa rồi, hãy xây dựng một cây mới từ dữ liệu nguồn tin cậy, kiểm tra xung đột theo comparator và thay thế cây cũ có kiểm soát.

### Cách 3: Xây lại toàn bộ cây khi cần cập nhật hàng loạt

Khi nhiều khóa cần đổi theo đợt, có thể thu thập các phần tử, hoàn tất cập nhật **bên ngoài tập hợp đang được sử dụng**, rồi chèn từng phần tử vào một `TreeSet`/`TreeMap` mới với comparator ổn định. Điều này bảo đảm việc xác định vị trí dựa trên giá trị mới.

Ví dụ `new TreeSet<>(someSortedSet)` không nên được xem là một biện pháp sửa lỗi chắc chắn nếu nguồn **đã vi phạm thứ tự**: một số đường triển khai có thể tận dụng tính “đã sắp xếp” của nguồn. Thay vào đó, khởi tạo cây trống và `add`/`put` từng phần tử như ví dụ `rebuilt`.

Đối với `TreeMap`, khi xây lại phải xác định chính sách nếu **nhiều khóa sau thay đổi cùng so sánh bằng `0`**, vì `put` có thể ghi đè giá trị của khóa tương đương; việc mất một ánh xạ có thể gây lỗi nghiệp vụ.

## 6. Cần phân biệt thay đổi khóa và thay đổi giá trị

| Thay đổi | Ảnh hưởng tới thứ tự cây |
| --- | --- |
| Đổi `priority` của phần tử `TreeSet` khi comparator xét `priority` | **Không an toàn**, cây không tự cập nhật vị trí |
| Đổi trường của khóa `TreeMap` mà `compareTo`/`Comparator` sử dụng | **Không an toàn**, tra cứu có thể sai |
| Đổi một trường trên phần tử mà comparator **hoàn toàn không đọc** | Không làm hỏng thứ tự vì riêng thay đổi đó, miễn quan hệ so sánh thực sự vẫn ổn định |
| Gọi `map.put(existingKey, newValue)` hoặc `map.replace(existingKey, newValue)` với khóa ổn định | **An toàn cho thứ tự cây** vì chỉ cập nhật giá trị |
| Thay đổi nội dung đối tượng làm **value** trong `TreeMap` và comparator chỉ xét khóa | Không tự làm hỏng thứ tự các khóa |
| Đổi trạng thái bên ngoài mà comparator sử dụng (cấu hình, bảng ưu tiên, thuộc tính lồng nhau) | Có thể **không an toàn** dù đối tượng khóa không thay trực tiếp |

Lưu ý: một đối tượng có `equals()` và `hashCode()` phụ thuộc vào trường mutable còn có thể gặp vấn đề tương tự trong `HashSet`/`HashMap`, nhưng ở đó nguyên nhân chính là **bucket băm** theo hash cũ. Với cây có thứ tự, nguyên nhân chính là **quan hệ so sánh và vị trí nút**.

## 7. `compareTo() == 0` và xung đột khi chèn lại

`TreeSet` và `TreeMap` dùng `compareTo()`/`Comparator.compare()` để xác định hai khóa **tương đương theo thứ tự**, tức kết quả so sánh bằng `0`. Sau khi đổi một trường, phần tử cũ có thể trở nên tương đương với một phần tử khác đã có trong cây.

Ví dụ hai công việc có cùng `priority` nếu comparator **chỉ so sánh `priority`** thì có thể bị coi là một khóa, ngay cả khi `id` khác nhau. `TreeSet.add()` khi đó trả `false`, còn `TreeMap.put()` có thể cập nhật giá trị của khóa đã có.

Vì vậy, xác định từ trước liệu cùng độ ưu tiên có được phép chứa nhiều công việc không. Nếu có, comparator cần một tiêu chí phụ ổn định, ví dụ:

```java
Comparator<Job> order = Comparator
        .comparingInt((Job job) -> job.priority)
        .thenComparing(job -> job.id);
```

Đây là **đoạn đặt trong ngữ cảnh có lớp `Job` và `import java.util.Comparator`**; nếu `Job` là lớp như ví dụ phía trên và mã nằm ngoài lớp lồng, quyền truy cập trường private cần được xử lý phù hợp. Dù có tiêu chí phụ, `priority` vẫn là trường tham gia so sánh và **vẫn không được sửa khi phần tử đang nằm trong cây**.

Bên cạnh đó, hợp đồng `Comparable`/`Comparator` yêu cầu tính phản đối xứng về dấu, tính bắc cầu và sự nhất quán khi hai giá trị so sánh bằng `0` với mọi giá trị thứ ba. Bộ so sánh sai hợp đồng có thể làm cây không ổn định **ngay cả khi trường không thay đổi**.

## 8. Checklist kỹ thuật khi cập nhật một khóa có thứ tự

- Xác nhận dữ liệu nào thực sự tham gia `compareTo`/`Comparator`, kể cả thuộc tính lồng nhau hay trạng thái bên ngoài.
- Ưu tiên tạo một **khóa bất biến mới** và thay thế bằng thao tác xóa/chèn.
- Nếu cần dùng cùng đối tượng mutable, luôn kiểm tra `remove` thành công **trước** khi thay đổi trường so sánh.
- Với `TreeMap`, giữ nguyên giá trị cần thiết khi xóa rồi chèn khóa đã cập nhật; phân biệt key absence với null value.
- Kiểm tra xem khóa mới có bị `compareTo == 0` với khóa khác hay không và xử lý đúng chính sách nghiệp vụ.
- Khi cây đã hỏng vì thay đổi tại chỗ, xây lại từ dữ liệu chuẩn bằng cách **chèn lại từng khóa**; đừng dựa vào truy vấn cây cũ để chứng minh tính đúng.
- Trong chương trình đa luồng, đồng bộ hóa/điều phối thao tác cập nhật và truy cập phù hợp; `TreeSet`/`TreeMap` thường không tự đồng bộ các thay đổi song song.

## 9. Câu hỏi tự kiểm tra

1. Đã chèn `Task(priority=10)` vào `TreeSet`, sau đó đổi `priority` thành `50` bằng setter. Cây có tự chuyển nút sang vị trí đúng không? **Không**; cần xóa trước rồi chèn lại hoặc xây tập hợp mới.
2. Đã đổi trường khóa rồi mới gọi `remove(key)`. Có bảo đảm xóa được không? **Không**; đường tìm kiếm có thể không còn đi đến nút đã lưu.
3. Thay đổi `value` trong `TreeMap` có làm hỏng thứ tự khóa không? **Không**, nếu bộ so sánh chỉ phụ thuộc khóa và khóa vẫn ổn định.
4. Nếu comparator chỉ xét `priority`, sau khi sửa hai công việc có cùng ưu tiên rồi chèn lại thì sao? **Có thể trùng khóa theo `compare == 0`**; cần xử lý hoặc thêm tiêu chí phụ.
5. Sao chép trực tiếp từ một `SortedSet` đã sai thứ tự có luôn sửa được không? **Không**; nên tạo tập hợp trống và chèn từng phần tử với comparator hiện tại.

**Ghi nhớ:** `TreeSet`/`TreeMap` xây cấu trúc dựa trên kết quả so sánh **tại thời điểm thao tác với khóa**. Thay đổi dữ liệu quyết định thứ tự khi đối tượng còn nằm trong cây làm mất bất biến của cây. Hãy dùng khóa bất biến, hoặc **remove → thay đổi → add/put** theo đúng thứ tự; nếu cấu trúc đã không hợp lệ, xây lại từ dữ liệu đáng tin cậy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ordered-collections-008">Phân biệt loại phần tử trùng với giữ tất cả phần tử rồi sắp xếp</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt loại phần tử trùng với giữ tất cả phần tử rồi sắp xếp

Khi làm việc với dữ liệu Java, **sắp xếp** và **loại phần tử trùng** là hai thao tác khác nhau. Sắp xếp chỉ thay đổi thứ tự các phần tử theo một quy tắc; loại trùng làm giảm số phần tử bằng cách giữ một đại diện cho mỗi nhóm được coi là tương đương. Một `TreeSet` thực hiện cả hai hành vi: duy trì thứ tự và chỉ giữ **một phần tử trong mỗi nhóm có kết quả so sánh bằng `0`**. Ngược lại, `List.sort(...)` hoặc `Stream.sorted(...)` sắp xếp nhưng **giữ nguyên các lần xuất hiện**, kể cả giá trị lặp.

Đây là khác biệt quan trọng trong chủ đề **tập hợp có thứ tự** và hợp đồng đối tượng: việc quyết định hai phần tử “trùng” trong `TreeSet` dựa trên **`compareTo` hoặc `Comparator.compare`**, không nhất thiết trùng với `equals`.

## 1. Hai yêu cầu khác nhau dẫn tới hai kết quả khác nhau

Giả sử dữ liệu đầu vào là:

```text
[pear, apple, pear, banana, apple]
```

Ta có thể muốn:

| Yêu cầu | Kết quả | Ý nghĩa |
| --- | --- | --- |
| Giữ nguyên dữ liệu | `[pear, apple, pear, banana, apple]` | Thứ tự nhập và số lần xuất hiện không đổi |
| **Chỉ sắp xếp** tăng dần | `[apple, apple, banana, pear, pear]` | Giữ đủ 5 phần tử, chỉ đổi vị trí |
| **Loại trùng rồi sắp xếp** | `[apple, banana, pear]` | Còn 3 giá trị khác nhau |
| **Đếm số lần xuất hiện theo thứ tự khóa** | `{apple=2, banana=1, pear=2}` | Giữ thông tin tần suất dù mỗi khóa hiển thị một lần |

Nếu đang xử lý đơn hàng, lượt truy cập, bản ghi log hoặc điểm số, việc tự ý cho dữ liệu vào `Set` có thể làm **mất những bản ghi lặp có ý nghĩa**. Trái lại, nếu mục tiêu là tập các tên kỹ năng không trùng, việc loại trùng có thể chính là yêu cầu.

**Câu hỏi thiết kế đầu tiên phải là:** cần *giữ mọi lần xuất hiện*, *giữ một phần tử đại diện*, hay *giữ số lần xuất hiện của mỗi nhóm*?

## 2. `List.sort` và `Stream.sorted`: sắp thứ tự, không xóa phần tử

Một `List` cho phép nhiều phần tử bằng nhau xuất hiện tại các vị trí khác nhau. Với `ArrayList` mutable, ta có thể sắp xếp tại chỗ:

```java
// Minh họa một đoạn trong phương thức, không phải chương trình độc lập.
List<String> words = new ArrayList<>(
        List.of("pear", "apple", "pear", "banana", "apple"));
words.sort(Comparator.naturalOrder());
// words: [apple, apple, banana, pear, pear]
```

`List.sort(comparator)` thay đổi thứ tự **ngay trong list**. Các phần tử trùng vẫn chiếm từng vị trí riêng. Nó là phép sắp xếp ổn định (*stable sort*): khi comparator trả `0` cho hai phần tử, thứ tự tương đối ban đầu của chúng được giữ. Nếu list không cho phép thay đổi cấu trúc/nội dung, ví dụ `List.of(...)`, không gọi `sort` trực tiếp trên đó; tạo `new ArrayList<>(source)` hoặc dùng stream.

`Stream.sorted()` tạo một luồng sắp xếp mới, không có tác dụng loại trùng:

```java
// Minh họa một đoạn trong phương thức:
List<String> sorted = source.stream()
        .sorted()
        .toList();
```

Với stream có thứ tự (*ordered stream*), `sorted` được bảo đảm ổn định; với stream không có encounter order, tính ổn định không được bảo đảm. `toList()` trả về danh sách không sửa được bằng các phương thức thay đổi list theo hợp đồng của `Stream.toList()`, nên nếu cần chỉnh sửa sau khi sắp, thu về `ArrayList` thích hợp.

**Không có bước `distinct()` trong hai thao tác trên**, vì vậy số lượng phần tử được giữ nguyên.

## 3. `TreeSet`: vừa sắp xếp vừa loại trùng theo thứ tự so sánh

`TreeSet<E>` thuộc họ `NavigableSet`/`SortedSet`, duy trì các phần tử theo:

- **Thứ tự tự nhiên** của `Comparable`, khi dùng `new TreeSet<>()` và các phần tử có thể so sánh với nhau.
- **Thứ tự do `Comparator` chỉ định**, khi dùng `new TreeSet<>(comparator)`.

Quy tắc phân biệt phần tử của `TreeSet` là:

```text
compare(a, b) < 0  -> a đứng trước b
compare(a, b) = 0  -> a và b được xem là cùng một phần tử theo thứ tự của set
compare(a, b) > 0  -> a đứng sau b
```

Với thứ tự tự nhiên, `compare(a,b)` có nghĩa là `a.compareTo(b)`; với comparator, là `comparator.compare(a,b)`.

Khi thêm một phần tử mới mà phép so sánh với phần tử đang có trả `0`, `TreeSet.add` trả `false` và không thêm một entry thứ hai. `size()` không tăng. Nếu cần biết một phần tử đã có đại diện hay chưa, có thể dùng `contains` theo cùng quy tắc thứ tự, nhưng **không suy ra hai đối tượng giống nhau hoàn toàn về mọi trường**.

Các phép thêm/xóa/tìm kiếm cơ bản trong `TreeSet` được tài liệu Java bảo đảm chi phí theo bậc `log(n)` trong cấu trúc cây. Còn toàn bộ quá trình tạo tập từ `n` phần tử sẽ có chi phí tổng hợp tương ứng với các lần chèn. Sự khác biệt **không chỉ nằm ở hiệu năng**: quan trọng hơn là `TreeSet` loại bớt dữ liệu theo quan hệ tương đương của comparator.

## 4. Ví dụ Java 17+ chạy được: nhìn rõ số lượng và thứ tự

Lưu toàn bộ chương trình thành **`SortVersusSetDemo.java`**:

```java
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class SortVersusSetDemo {
    public static void main(String[] args) {
        List<String> source = List.of(
                "pear", "apple", "pear", "banana", "apple");

        List<String> sorted = new ArrayList<>(source);
        sorted.sort(Comparator.naturalOrder());

        TreeSet<String> uniqueSorted = new TreeSet<>(source);

        System.out.println("Original: " + source);
        System.out.println("Sorted List: " + sorted);
        System.out.println("TreeSet: " + uniqueSorted);
        System.out.println("Original size: " + source.size());
        System.out.println("TreeSet size: " + uniqueSorted.size());
        System.out.println("Stream distinct then sorted: "
                + source.stream().distinct().sorted().toList());

        TreeMap<String, Integer> counts = new TreeMap<>();
        for (String word : source) {
            counts.merge(word, 1, Integer::sum);
        }
        System.out.println("Sorted frequencies: " + counts);

        List<String> spellings =
                List.of("Java", "java", "JAVA", "Kotlin");
        TreeSet<String> ignoreCase =
                new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        ignoreCase.addAll(spellings);

        System.out.println("Case-sensitive equals: "
                + "Java".equals("java"));
        System.out.println("Case-insensitive compare zero: "
                + (String.CASE_INSENSITIVE_ORDER
                        .compare("Java", "java") == 0));
        System.out.println("Case-insensitive TreeSet: " + ignoreCase);
        System.out.println("Case-insensitive TreeSet size: "
                + ignoreCase.size());
        System.out.println("Case-insensitive sorted List: "
                + spellings.stream()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList());

        BigDecimal a = new BigDecimal("1.0");
        BigDecimal b = new BigDecimal("1.00");
        System.out.println("BigDecimal equals: " + a.equals(b));
        System.out.println("BigDecimal compareTo zero: "
                + (a.compareTo(b) == 0));

        Set<BigDecimal> hash = new HashSet<>(List.of(a, b));
        Set<BigDecimal> tree = new TreeSet<>(List.of(a, b));
        System.out.println("BigDecimal HashSet size: " + hash.size());
        System.out.println("BigDecimal TreeSet size: " + tree.size());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac SortVersusSetDemo.java
java SortVersusSetDemo
```

Đầu ra xác định:

```text
Original: [pear, apple, pear, banana, apple]
Sorted List: [apple, apple, banana, pear, pear]
TreeSet: [apple, banana, pear]
Original size: 5
TreeSet size: 3
Stream distinct then sorted: [apple, banana, pear]
Sorted frequencies: {apple=2, banana=1, pear=2}
Case-sensitive equals: false
Case-insensitive compare zero: true
Case-insensitive TreeSet: [Java, Kotlin]
Case-insensitive TreeSet size: 2
Case-insensitive sorted List: [Java, java, JAVA, Kotlin]
BigDecimal equals: false
BigDecimal compareTo zero: true
BigDecimal HashSet size: 2
BigDecimal TreeSet size: 1
```

**Giải thích:**

1. `sorted` giữ nguyên 5 phần tử của `source`, chỉ xếp theo thứ tự tự nhiên của `String`. Hai từ `apple` và hai từ `pear` vẫn còn nguyên.
2. `new TreeSet<>(source)` chỉ giữ một bản của mỗi chuỗi trùng theo thứ tự tự nhiên, còn 3 phần tử.
3. `source.stream().distinct().sorted()` cho kết quả giống `TreeSet` **trong ví dụ chuỗi với thứ tự tự nhiên**, vì `String.equals` và `String.compareTo` nhất quán. Hai cách không được coi là tương đương cho mọi comparator.
4. `TreeMap<String,Integer>` dùng mỗi chuỗi làm khóa và cộng số lần xuất hiện; kết quả có thứ tự khóa tăng dần, **không mất tần suất**.
5. `String.CASE_INSENSITIVE_ORDER.compare("Java","java") == 0` nhưng `"Java".equals("java")` là `false`. `TreeSet` dùng comparator này chỉ giữ một phần tử đại diện cho nhóm `Java/java/JAVA`. Vì `"Java"` được thêm đầu tiên, nó là đại diện còn giữ trong ví dụ; `"Kotlin"` là nhóm thứ hai.
6. `spellings.stream().sorted(String.CASE_INSENSITIVE_ORDER)` sắp theo cùng comparator nhưng **giữ cả bốn phần tử**. Với stream có thứ tự từ `List`, các chuỗi có kết quả compare `0` giữ thứ tự tương đối ban đầu.
7. Hai `BigDecimal` có giá trị số học bằng nhau theo `compareTo` nhưng khác `scale`, nên `equals` trả `false`. Vì thế `HashSet` (theo `equals`/`hashCode`) giữ 2 phần tử, trong khi `TreeSet` (theo `compareTo`) giữ 1.

## 5. `equals()` và `compareTo()`: hai quan hệ tương đương có thể khác nhau

Trong Java, `Set` thường gợi liên tưởng “không chứa hai phần tử `equals` bằng nhau”. Nhưng `TreeSet` dùng quan hệ so sánh của nó để thực hiện mọi thao tác xác định phần tử. Do đó, để một `SortedSet` đáp ứng đầy đủ hợp đồng `Set` theo nghĩa của `equals`, thứ tự sắp xếp nên **nhất quán với `equals`**:

```java
// Quy tắc mong muốn khi nói thứ tự nhất quán với equals:
(a.compareTo(b) == 0) == a.equals(b)
```

Nếu hai vế khác nhau, hành vi nội bộ của `TreeSet` vẫn được xác định theo comparator, nhưng nó có thể gây bất ngờ khi so với `HashSet` hoặc quy ước set dựa trên `equals`.

**Trường hợp 1: `compare(a,b) == 0` dù `a.equals(b)` là `false`.** Hai giá trị bị `TreeSet` gộp thành một nhóm. Ví dụ: so sánh `String` không phân biệt hoa/thường, hoặc `BigDecimal("1.0")` và `BigDecimal("1.00")`.

**Trường hợp 2: `a.equals(b)` là `true` nhưng comparator cho kết quả khác `0`.** `TreeSet` có thể giữ hai phần tử mà `equals` coi là bằng nhau, nếu comparator dùng một tiêu chí khác. Đây cũng là sự không nhất quán giữa quan hệ sắp xếp và quan hệ bằng nhau.

Đặc biệt, không được dùng `hashCode()` để đoán `TreeSet` sẽ loại trùng ra sao. `TreeSet` không phân biệt phần tử bằng `hashCode`; nó dùng **thứ tự so sánh**.

### Comparator chỉ so một phần thuộc tính

Giả sử mỗi bản ghi có `name` và `id` độc lập:

```java
record Item(long id, String name) {}

// Một ví dụ về comparator chỉ xét tên:
Comparator<Item> byName = Comparator.comparing(Item::name);
```

Đây là **đoạn minh họa cần đặt trong lớp/phương thức thích hợp**; `record Item` có thể khai báo bên ngoài hoặc trong phạm vi cục bộ hợp lệ. Hai `Item` với ID khác nhau nhưng cùng `name` có `byName.compare(a,b) == 0`, nên `TreeSet<>(byName)` sẽ chỉ giữ một đại diện nếu cả hai được thêm vào. Điều đó đúng nếu tên là khóa phân biệt theo nghiệp vụ, nhưng **sai** nếu bản ghi có ID khác nhau vẫn cần giữ cả hai.

Nếu muốn **sắp xếp theo tên nhưng giữ tất cả bản ghi**, dùng một `List<Item>` và `sort(byName)`. Nếu muốn mỗi ID duy nhất trong `TreeSet` và thứ tự trước hết theo tên, có thể dùng comparator thêm tiêu chí phá hòa (*tie-breaker*) phù hợp:

```java
Comparator<Item> byNameThenId = Comparator
        .comparing(Item::name)
        .thenComparingLong(Item::id);
```

Khi `id` duy nhất và cùng xác định tính bằng nhau, comparator này tránh gộp hai bản ghi chỉ vì trùng tên. Tuy nhiên, nếu `equals` của lớp sử dụng quy tắc khác thì vẫn phải kiểm tra tính nhất quán; thêm `id` không tự động giải quyết mọi mô hình.

## 6. Khi nào dùng `Stream.distinct()`, và cần nhớ điều gì?

`Stream.distinct()` trả về các phần tử phân biệt theo **`Object.equals`**, không nhận một comparator để định nghĩa cách gộp nhóm. Với stream có encounter order (như `List.stream()`), nó giữ phần tử xuất hiện **đầu tiên** trong mỗi nhóm theo `equals`. Sau đó có thể sắp xếp:

```java
// Giữ một bản cho mỗi giá trị equals tương đương, rồi sắp xếp:
List<String> result = source.stream()
        .distinct()
        .sorted()
        .toList();
```

Đây là chuỗi thao tác dễ hiểu khi quy tắc loại trùng là `equals` và tiêu chí sắp xếp là thứ tự tự nhiên. Tuy nhiên:

- `distinct()` **không loại trùng theo comparator tùy chọn**. Với `"Java"` và `"java"`, `distinct()` coi đó là hai chuỗi khác nhau vì `String.equals` phân biệt hoa/thường.
- `sorted(comparator).distinct()` vẫn loại theo `equals`, **không đổi sang quy tắc comparator trả `0`**. Thứ tự hai thao tác có thể ảnh hưởng đại diện/encounter order trong các trường hợp khác nhau; hãy xác định ngữ nghĩa trước.
- Nếu cần loại trùng **theo một khóa nghiệp vụ**, có thể nhóm theo khóa trong `Map`, hoặc dùng comparator phù hợp trong `TreeSet`, nhưng phải xác định rõ đại diện nào được giữ. Không giả định một API ngẫu nhiên chọn “bản mới nhất” hay “bản tốt nhất”.
- Với stream song song có thứ tự, `distinct` có thể đòi hỏi xử lý trạng thái và chi phí đáng kể; hiệu năng không quan trọng bằng tính đúng của quy tắc loại trùng.

`distinct()` cũng dựa trên `equals`/`hashCode` đúng hợp đồng. Nếu lớp tự viết cài đặt `equals` theo giá trị mà quên `hashCode`, kết quả loại trùng dựa trên hash có thể sai. Với `TreeSet`, vấn đề tương ứng là `compareTo`/`Comparator` phải biểu diễn đúng tính tương đương mong muốn.

## 7. Nếu cần cả thứ tự và số lượng của các giá trị lặp

Không nhất thiết phải chọn giữa “mất các bản sao” và “không sắp xếp”. Có nhiều mô hình phù hợp:

| Nhu cầu | Công cụ | Điều cần nhớ |
| --- | --- | --- |
| Sắp xếp và giữ toàn bộ bản ghi | `ArrayList.sort`, `List.sort`, `Stream.sorted` | Có thể có nhiều phần tử bằng nhau |
| Giữ một đại diện mỗi nhóm so sánh và duy trì thứ tự | `TreeSet` | Trùng do comparator trả `0` |
| Loại trùng theo `equals`, sau đó sắp | `stream().distinct().sorted(...)` | Quy tắc loại trùng của `distinct` không phải comparator |
| Đếm tần suất theo khóa có thứ tự | `TreeMap<K,Integer>` cộng dồn | Dữ liệu đếm giữ tổng lần xuất hiện |
| Đếm tần suất theo `equals` rồi trình bày theo thứ tự riêng | `HashMap<K,Integer>` rồi sắp các entry | Quy tắc đếm và quy tắc trình bày có thể khác nhau |
| Loại trùng mà vẫn giữ thứ tự xuất hiện đầu tiên | `LinkedHashSet` hoặc ordered `Stream.distinct` | Thứ tự đầu vào, không phải thứ tự đã sắp |

`TreeMap` cũng dùng comparator/ `compareTo` để xác định **khóa tương đương**. Nếu comparator gộp hai chuỗi khác nhau, `merge` vào `TreeMap` sẽ cộng số lần của cùng một nhóm comparator, không còn đếm từng giá trị `equals` riêng biệt. Đây có thể là chủ đích của báo cáo không phân biệt chữ hoa/thường; nếu không, nó là lỗi phân nhóm.

Nếu yêu cầu là **multiset có thứ tự** (giữ nhiều bản sao của mỗi phần tử), Java Collections Framework không có lớp `TreeMultiset` chuẩn sẵn. Một `TreeMap<K,Integer>` lưu bộ đếm là cách triển khai đơn giản; muốn giữ từng bản ghi chi tiết, `List` đã sắp xếp thường phù hợp hơn. `PriorityQueue` có thể giữ giá trị lặp và lấy phần tử ưu tiên bằng `poll`, nhưng **việc duyệt iterator của queue không được bảo đảm là theo thứ tự sắp xếp**, nên không thay thế trực tiếp danh sách được sắp.

## 8. Những nhầm lẫn thường gặp

1. **“`TreeSet` chỉ sắp xếp, không xóa dữ liệu.”** Sai. Nó có ngữ nghĩa set, loại phần tử tương đương theo comparator.
2. **“`List.sort` tự loại bản ghi trùng.”** Sai. List vẫn giữ đủ số lần xuất hiện.
3. **“`TreeSet` luôn dùng `equals` để phát hiện trùng.”** Sai. `compareTo == 0` hoặc `Comparator.compare == 0` mới là điều kiện xác định cùng phần tử theo thứ tự.
4. **“`TreeSet` trùng với `HashSet` về kết quả.”** Không chắc. `HashSet` dùng `equals`/`hashCode`; `TreeSet` dùng so sánh thứ tự.
5. **“`sorted(comparator).distinct()` loại trùng theo comparator.”** Sai. `distinct` vẫn dùng `equals`.
6. **“Comparator chỉ so tên vẫn giữ mọi người có tên giống nhau.”** Sai nếu cho vào `TreeSet`, vì compare `0` sẽ gộp nhóm.
7. **“Có thể tự do thay đổi trường sắp xếp sau khi thêm vào `TreeSet`.”** Không an toàn. Cấu trúc cây phụ thuộc thứ tự; thay đổi trường tham gia comparator/`compareTo` khi phần tử đang nằm trong set có thể làm tra cứu, xóa, duyệt sai kỳ vọng.
8. **“Có thể dùng `Set` để đếm số lần xuất hiện.”** `Set` chỉ lưu một đại diện mỗi nhóm; để biết tần suất phải đếm riêng hoặc dùng `Map<K,Integer>`.
9. **“Hash bằng nhau nghĩa là `TreeSet` gộp phần tử.”** Không. `TreeSet` không dùng mã băm để phân biệt phần tử.
10. **“Comparator trả `0` chỉ là hai phần tử đứng gần nhau.”** Trong `TreeSet`, `0` còn có nghĩa chúng **không thể cùng tồn tại như hai entry riêng biệt**.

## 9. Checklist trước khi chọn cấu trúc dữ liệu

- **Dữ liệu lặp có ý nghĩa không?** Nếu có, tránh dùng `Set` làm bước sắp xếp mặc định.
- **Quy tắc nào xác định trùng?** `equals`, khóa ID nghiệp vụ, hay comparator (ví dụ không phân biệt hoa/thường)?
- **Một nhóm trùng cần giữ bản đại diện nào?** Bản đầu tiên, bản mới nhất, bản có mức ưu tiên cao nhất hay tổng tần suất?
- **Cần thứ tự khi nào?** Ngay khi thêm phần tử (`TreeSet`, `TreeMap`) hay chỉ khi xuất kết quả (`List.sort`)?
- **Lớp tự viết có nhất quán giữa `equals` và `compareTo` không?** Nếu không, phải hiểu rõ khác biệt giữa `HashSet` và `TreeSet`.
- **Các trường quyết định thứ tự có bất biến không?** Một phần tử trong `TreeSet` không nên bị sửa làm thay đổi vị trí logic của nó.
- **Sau khi xử lý, số phần tử phải bằng số đầu vào hay số nhóm duy nhất?** Đây là kiểm tra nhanh giúp phát hiện thao tác loại trùng ngoài ý muốn.

**Ghi nhớ:** **Sắp xếp** không đồng nghĩa **loại trùng**. Dùng `List.sort`/`Stream.sorted` khi cần giữ tất cả phần tử và chỉ thay đổi thứ tự; dùng `TreeSet` khi thật sự cần tập phần tử duy nhất **theo quan hệ so sánh**; dùng `TreeMap<K,Integer>` khi cần giữ tần suất theo khóa đã sắp xếp. Trước khi đưa đối tượng vào `TreeSet`, luôn kiểm tra `compare(a,b) == 0` có đúng là tiêu chí “trùng” mà nghiệp vụ mong muốn hay không.


</details>

- [Quay lại đầu trang](#back-to-top)
