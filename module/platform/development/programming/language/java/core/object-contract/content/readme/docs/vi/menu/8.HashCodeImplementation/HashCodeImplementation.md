<a id="back-to-top"></a>

# Cài đặt hashCode

## Menu
- [Chọn dữ liệu băm phù hợp với quan hệ equals()](#hash-code-implementation-001)
- [Không đưa dữ liệu làm phân biệt hai đối tượng đang được coi là bằng nhau](#hash-code-implementation-002)
- [Kết hợp hash của nhiều trường](#hash-code-implementation-003)
- [Dùng phương thức hashCode() của các lớp bao cho kiểu nguyên thủy](#hash-code-implementation-004)
- [Trường null và Objects.hashCode()](#hash-code-implementation-005)
- [Objects.hash() cho nhiều trường và khác biệt với hashCode() của một trường](#hash-code-implementation-006)
- [Dùng Arrays.hashCode() hoặc deepHashCode() theo cách so sánh mảng](#hash-code-implementation-007)
- [Cân nhắc boxing, mảng varargs và chi phí trên đường chạy thường xuyên](#hash-code-implementation-008)
- [Chỉ lưu sẵn hash code khi trạng thái và cơ chế đồng bộ cho phép](#hash-code-implementation-009)

## <a id="hash-code-implementation-001">Chọn dữ liệu băm phù hợp với quan hệ equals()</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn dữ liệu băm phù hợp với quan hệ equals() trong Java

## 1. Vấn đề cần giải quyết

Khi tự cài đặt hai phương thức **equals(Object)** và **hashCode()**, câu hỏi quan trọng không phải là “lấy càng nhiều trường vào hash càng tốt” mà là: **những đối tượng nào được coi là bằng nhau theo equals và làm sao bảo đảm chúng luôn có cùng hashCode?**

Ví dụ, một sản phẩm có các trường:

- tenantId: cửa hàng sở hữu sản phẩm;
- sku: mã sản phẩm, quy ước không phân biệt chữ hoa/chữ thường;
- displayName: tên trình bày trên giao diện;
- lastViewedAt: thời điểm gần nhất người dùng mở trang sản phẩm.

Nếu quan hệ equals xác định cùng sản phẩm bằng cặp **(tenantId, sku sau chuẩn hóa)**, hashCode cũng phải tạo cùng kết quả đối với mọi cặp giá trị tương đương này. displayName hoặc lastViewedAt không được khiến hai sản phẩm cùng định danh có mã băm khác nhau.

Ngược lại, nếu equals thực sự so sánh cả displayName thì hashCode không thể tự ý dựa vào một quan hệ bình đẳng khác. Cần chốt **định nghĩa equality trước**, sau đó chọn dữ liệu băm phù hợp.

## 2. Hợp đồng equals–hashCode: hai nguyên tắc không thể đảo ngược

Hợp đồng Java Object.hashCode() yêu cầu:

1. **Nếu x.equals(y) là true thì x.hashCode() phải bằng y.hashCode().**
2. Khi không có thông tin nào dùng trong equals thay đổi, nhiều lần gọi hashCode trên cùng đối tượng trong một lần thực thi Java phải trả cùng số nguyên.

Điều **không** được yêu cầu:

- Hai đối tượng khác nhau theo equals phải có mã băm khác nhau. **Va chạm băm (collision) được phép xảy ra.**
- hashCode phải giống nhau giữa các lần chạy chương trình khác nhau; Java không bảo đảm mọi cách triển khai đều ổn định giữa các phiên chạy.
- hashCode phải là mã định danh duy nhất, địa chỉ bộ nhớ hay số không âm.

Có thể hình dung quan hệ bằng nhau chia các đối tượng thành những *nhóm tương đương*. Mỗi nhóm phải nhận **một** giá trị hash ổn định trong các điều kiện nêu trên; nhiều nhóm khác nhau vẫn có thể dùng chung một giá trị hash.

| Quan hệ giữa x và y | Yêu cầu đối với hashCode | Ghi chú |
| --- | --- | --- |
| x.equals(y) == true | hashCode **bắt buộc bằng nhau** | Điều kiện hợp đồng |
| x.equals(y) == false | hashCode có thể bằng hoặc khác | Không được kết luận từ hash |
| Trạng thái tham gia equals giữ nguyên | hashCode phải ổn định trong lần chạy | Không dùng random/thời gian để băm |
| Trạng thái tham gia equals bị sửa | Hash có thể đổi | Dễ gây lỗi khi đang dùng làm khóa băm |

Cần phân biệt: **“Dữ liệu tham gia equals” là dữ liệu quyết định quan hệ bằng nhau về mặt logic**, không đơn giản là “toàn bộ trường của đối tượng”.

## 3. Quy trình chọn dữ liệu để băm

### Bước 1: Xác định tiêu chí bằng nhau

Hãy trả lời trước: hai đối tượng đại diện cho cùng một giá trị khi những thuộc tính nào khớp nhau? Ví dụ:

~~~java
return tenantId.equals(other.tenantId)
        && normalizedSku.equals(other.normalizedSku);
~~~

Nếu đây là phần quyết định equality, hai đối tượng có cùng tenantId và normalizedSku phải có hash bằng nhau.

### Bước 2: Xem xét cách equals so sánh từng trường

Cùng một trường dữ liệu có thể được so sánh theo những quy tắc khác nhau:

- So sánh String phân biệt hoa/thường: hash có thể dựa trên chính String đó.
- So sánh mã SKU không phân biệt hoa/thường: hash phải dùng cùng dạng chuẩn hóa hoặc một phép băm tương thích với quan hệ không phân biệt hoa/thường.
- So sánh nội dung mảng: dùng **Arrays.equals** trong equals và **Arrays.hashCode** tương ứng trong hashCode.
- So sánh sâu mảng lồng nhau: dùng **Arrays.deepEquals** cùng **Arrays.deepHashCode**.
- So sánh nhiều trường nullable: dùng **Objects.equals** khi cần so sánh và Objects.hash/Objects.hashCode phù hợp khi băm.

**Quy tắc quan trọng:** cùng nghĩa *bằng nhau* thì dữ liệu băm phải cho cùng kết quả. Không nhất thiết phải gọi y hệt phương thức ở cả hai nơi, nhưng cách chuẩn hóa và xử lý giá trị phải tương thích.

### Bước 3: Loại bỏ các trường không tạo nên equality

Không thêm trường phục vụ hiển thị, ghi log, bộ đếm, trạng thái UI, cache, thời điểm truy cập hoặc dữ liệu khác mà equals bỏ qua.

Ví dụ, nếu equals chỉ dùng tenantId và SKU chuẩn hóa:

~~~java
// Sai khi equals không xét displayName:
return Objects.hash(tenantId, normalizedSku, displayName);

// Đúng với tiêu chí equals đã định nghĩa:
return Objects.hash(tenantId, normalizedSku);
~~~

Đây là các đoạn thân phương thức minh họa, không phải chương trình độc lập. Biểu thức đầu có thể làm hai đối tượng equals trả true nhưng hashCode khác nhau chỉ vì displayName khác.

### Bước 4: Chọn thuật toán băm hợp lệ, đủ phân tán

Về tính **đúng**, dùng tập trường phù hợp và giữ ổn định là ưu tiên đầu tiên. Về **hiệu năng**, nên kết hợp các thành phần định danh hợp lý để giảm va chạm, đặc biệt với HashMap/HashSet chứa nhiều phần tử.

Nếu equals so sánh cả id và version, hashCode chỉ trả id.hashCode() có thể vẫn đúng về hợp đồng vì hai đối tượng bằng nhau luôn có cùng id. Tuy vậy, nó bỏ qua version nên nhiều cặp không bằng nhau có thể dồn chung hash, làm phân tán kém. Thực tế thường nên kết hợp cả id và version.

Nói cách khác, **hashCode không bắt buộc phải dùng tất cả trường trong equals**, nhưng tuyệt đối không được phân biệt hai đối tượng mà equals coi là bằng nhau. Dùng đủ các thuộc tính định danh thường là lựa chọn rõ ràng và có phân bố tốt hơn.

### Bước 5: Giữ dữ liệu băm ổn định khi đối tượng nằm trong bộ sưu tập băm

Khi đối tượng được dùng làm khóa HashMap hoặc phần tử HashSet, tránh sửa dữ liệu xác định equality/hash trong suốt thời gian nó nằm trong collection. Nếu thay đổi giá trị đó, collection có thể không tìm lại được khóa vì vị trí lưu ban đầu gắn với hash cũ.

Ưu tiên khóa bất biến: các trường final với kiểu dữ liệu bất biến hoặc bản sao phòng vệ của dữ liệu mutable.

## 4. Ví dụ hoàn chỉnh Java 17+: cùng quan hệ equals thì cùng hash

Quy tắc cho ProductKey: **cùng tenantId và sku không phân biệt chữ hoa/thường** là cùng khóa. Tên hiển thị không tham gia equality. Ví dụ dùng SKU thuộc bộ ký tự ASCII, chuẩn hóa qua Locale.ROOT.

Tạo tệp **HashCodeFieldSelectionDemo.java**:

~~~java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class HashCodeFieldSelectionDemo {
    public static void main(String[] args) {
        ProductKey a = new ProductKey("shop-1", "ax-7", "Mũ xanh");
        ProductKey b = new ProductKey("shop-1", "AX-7", "Mũ đỏ");
        ProductKey c = new ProductKey("shop-2", "ax-7", "Mũ xanh");

        System.out.println("a equals b: " + a.equals(b));
        System.out.println("a hash equals b hash: "
                + (a.hashCode() == b.hashCode()));
        System.out.println("a equals c: " + a.equals(c));

        Set<ProductKey> set = new HashSet<>();
        set.add(a);
        set.add(b);
        set.add(c);
        System.out.println("HashSet size: " + set.size());

        Map<ProductKey, String> map = new HashMap<>();
        map.put(a, "Đã lưu");
        ProductKey lookup = new ProductKey(
                "shop-1", "aX-7", "Tên hiển thị khác");
        System.out.println("HashMap lookup: " + map.get(lookup));

        System.out.println("Aa hash equals BB hash: "
                + ("Aa".hashCode() == "BB".hashCode()));
        System.out.println("Aa equals BB: " + "Aa".equals("BB"));
    }

    static final class ProductKey {
        private final String tenantId;
        private final String normalizedSku;
        private final String displayName;

        ProductKey(String tenantId, String sku, String displayName) {
            this.tenantId = Objects.requireNonNull(tenantId);
            this.normalizedSku = Objects.requireNonNull(sku)
                    .toUpperCase(Locale.ROOT);
            this.displayName = Objects.requireNonNull(displayName);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ProductKey other)) {
                return false;
            }
            return tenantId.equals(other.tenantId)
                    && normalizedSku.equals(other.normalizedSku);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, normalizedSku);
        }

        @Override
        public String toString() {
            return "ProductKey{" + tenantId + ", "
                    + normalizedSku + ", " + displayName + "}";
        }
    }
}
~~~

Biên dịch và chạy tại thư mục chứa tệp:

~~~bash
javac -encoding UTF-8 HashCodeFieldSelectionDemo.java
java HashCodeFieldSelectionDemo
~~~

Kết quả chuẩn:

~~~text
a equals b: true
a hash equals b hash: true
a equals c: false
HashSet size: 2
HashMap lookup: Đã lưu
Aa hash equals BB hash: true
Aa equals BB: false
~~~

**Vì sao kết quả như trên?**

1. a và b có tenantId cùng là shop-1; sku ax-7 và AX-7 đều được chuẩn hóa thành AX-7. Do đó, equals trả true và hashCode phải giống nhau. Tên hiển thị khác nhau không ảnh hưởng.
2. c thuộc shop-2, nên khác định danh so với a. Vì a và c không bằng nhau, không bắt buộc hash của chúng phải khác.
3. HashSet chứa hai giá trị logic: một cho shop-1/AX-7 và một cho shop-2/AX-7. Thêm b không làm tăng kích thước vì b bằng a.
4. HashMap tra lại khóa bằng một đối tượng mới vẫn tìm được giá trị, dù chuỗi SKU viết kiểu khác và tên hiển thị khác.
5. Hai String "Aa" và "BB" có cùng String.hashCode (2112) nhưng không bằng nhau. Đây là va chạm hợp lệ: hash bằng nhau **không có nghĩa** equals là true.

**Vì sao chọn normalizedSku thay vì sku gốc?** Nếu equals so sánh không phân biệt hoa/thường nhưng hashCode lại gọi hashCode trên chuỗi gốc, hai giá trị như ax-7 và AX-7 có thể có mã băm khác nhau dù equals coi chúng bằng nhau. Chuẩn hóa một lần tại constructor vừa bảo đảm hash và equals dùng cùng giá trị, vừa tránh chạy lại logic trong mỗi lời gọi.

**Vì sao ProductKey là final và các trường là final?** Để minh họa một khóa có trạng thái định danh ổn định. Chuỗi String bất biến; trường final không bị gán lại sau constructor. Tuy nhiên, với kiểu trường là List hoặc mảng thì chỉ final tham chiếu **không ngăn được sửa phần tử bên trong**.

## 5. Các cặp triển khai đúng và sai cần phân biệt

### 5.1. equals bỏ qua một trường, hashCode lại dùng trường ấy

Giả sử equals chỉ so sánh id:

~~~java
@Override
public boolean equals(Object obj) {
    return obj instanceof Item other && id.equals(other.id);
}

// Sai với equals ở trên nếu label có thể khác giữa các Item cùng id.
@Override
public int hashCode() {
    return Objects.hash(id, label);
}
~~~

Hai Item có cùng id nhưng khác label sẽ bằng nhau theo equals, trong khi mã băm có thể khác. Các đoạn trên chỉ minh họa trong lớp có các trường id, label; không phải toàn bộ một lớp độc lập.

Sửa:

~~~java
@Override
public int hashCode() {
    return Objects.hashCode(id);
}
~~~

Nếu id bảo đảm không null, có thể dùng id.hashCode(). Nếu id được phép null thì phải bảo đảm equals xử lý null tương ứng, ví dụ Objects.equals(id, other.id).

### 5.2. equals so sánh hai trường, hashCode chỉ dùng một trường

~~~java
// Trong một lớp mà equals so sánh id và version:
@Override
public int hashCode() {
    return Objects.hashCode(id);
}
~~~

Cách trên **có thể đúng hợp đồng**: khi cả id lẫn version giống nhau thì id đương nhiên giống nhau. Nhưng hai phiên bản khác nhau của cùng id cùng hash, làm tăng va chạm.

Thiết kế có phân bố tốt hơn:

~~~java
@Override
public int hashCode() {
    return Objects.hash(id, version);
}
~~~

Trọng tâm là **tính tương thích với equals**, sau đó mới xét chất lượng phân bố. Không nên diễn giải sai rằng thiếu bất kỳ trường nào của equals trong công thức băm đều tự động vi phạm hợp đồng.

### 5.3. equals bỏ qua kiểu chữ nhưng hashCode dùng chuỗi gốc

~~~java
// equals xác định name bằng equalsIgnoreCase,
// nhưng hashCode dưới đây băm name nguyên gốc:
@Override
public int hashCode() {
    return Objects.hashCode(name); // Có thể vi phạm!
}
~~~

Nếu "ONE" và "one" bằng nhau theo quy tắc equals của lớp, mã băm nguyên gốc của chúng khác nhau. Cách sửa là **thống nhất quy tắc giá trị**. Với mã ASCII không phân biệt hoa/thường, có thể chuẩn hóa bằng toUpperCase(Locale.ROOT) khi tạo đối tượng rồi dùng giá trị chuẩn hóa trong **cả equals và hashCode**.

Với văn bản Unicode, locale hoặc quy tắc so sánh phức tạp, không được tự giả định đổi hoa/thường bằng một bước đơn giản luôn tương đương với mọi phương thức so sánh. Phải định nghĩa phép chuẩn hóa/collation và phép băm tương ứng cùng một quan hệ.

### 5.4. equals dùng nội dung mảng nhưng hashCode dùng định danh mảng

~~~java
// Nếu tags là String[] và hai mảng được coi bằng nhau theo nội dung:
return Arrays.equals(this.tags, other.tags); // Trong equals

// Hash đúng theo nội dung:
return Arrays.hashCode(tags);                 // Trong hashCode
~~~

Các đoạn trên minh họa hai phần cài đặt trong ngữ cảnh đã khai báo tags và other; cần import java.util.Arrays.

Không dùng Objects.hashCode(tags) cho trường hợp này: với mảng thông thường, nó gọi hashCode mặc định của đối tượng mảng, dựa trên định danh tham chiếu, trong khi Arrays.equals so sánh phần tử.

Với mảng lồng nhau, dùng cặp Arrays.deepEquals / Arrays.deepHashCode khi chủ ý so sánh sâu. Với List có equals theo phần tử, List.hashCode cũng được định nghĩa nhất quán theo các phần tử đó; có thể sử dụng Objects.hashCode(list) nếu đó là trường đang xét. Lưu ý phần tử mutable vẫn có thể khiến hash thay đổi.

### 5.5. equals có ngữ nghĩa số đặc biệt

Ví dụ, BigDecimal.equals phân biệt cả giá trị **và scale**: new BigDecimal("1.0") không equals new BigDecimal("1.00"), dù compareTo trả 0.

Nếu một lớp tùy chỉnh định nghĩa equals dựa trên BigDecimal.compareTo(...) == 0, hashCode không được dùng trực tiếp amount.hashCode() vì hai BigDecimal khác scale có thể cho hash khác nhau. Có thể thiết kế dạng chuẩn hóa bằng stripTrailingZeros() rồi dùng dạng đã chuẩn hóa ở cả phép so sánh lẫn phép băm. Khi làm vậy cần xác định rõ cách xử lý null, số 0 và các trường khác. Đừng thay ngữ nghĩa BigDecimal.equals mặc định nếu không có yêu cầu nghiệp vụ cụ thể.

Tương tự với số thực, hãy thống nhất cách xét NaN, +0.0/-0.0 nếu lớp có định nghĩa equals tùy chỉnh. Ưu tiên cặp Double.compare/Double.hashCode hoặc Float.compare/Float.hashCode khi chính cách phân biệt của chúng phù hợp với quan hệ muốn thiết kế.

## 6. Nên dùng Objects.hash, Objects.hashCode hay công thức thủ công?

### Objects.hash(...)

Phù hợp với **nhiều trường**:

~~~java
@Override
public int hashCode() {
    return Objects.hash(tenantId, normalizedSku);
}
~~~

Ưu điểm là ngắn, xử lý trường null và nhất quán với các hash của thành phần. Nó sử dụng cơ chế varargs và băm theo các phần tử, vì vậy trong mã rất nhạy về hiệu năng cần đo đạc thay vì mặc định đó là lựa chọn tối ưu nhất.

### Objects.hashCode(value)

Phù hợp với **một giá trị có thể null**:

~~~java
@Override
public int hashCode() {
    return Objects.hashCode(id);
}
~~~

Nếu id null, kết quả là 0; nếu không null, kết quả là id.hashCode(). Objects.hash(id) **không hoàn toàn tương đương** Objects.hashCode(id), nên hãy dùng công thức có chủ ý và nhất quán với equals. Không trộn lẫn hai cách một cách tùy tiện trong test so sánh hash tuyệt đối.

### Kết hợp thủ công

Với nhiều trường, có thể áp dụng phép kết hợp tuần tự, ví dụ:

~~~java
@Override
public int hashCode() {
    int result = Objects.hashCode(tenantId);
    result = 31 * result + Objects.hashCode(normalizedSku);
    return result;
}
~~~

Đây là minh họa cho lớp có hai trường nói trên. Phép nhân/cộng int có thể overflow, đó là hành vi số nguyên Java xác định được và không khiến công thức băm sai hợp đồng. Công thức không cần tạo mã duy nhất; điều cốt lõi là dữ liệu bằng nhau theo equals cho kết quả băm bằng nhau.

**Không bao giờ dùng** một giá trị ngẫu nhiên, thời gian hệ thống hay bộ đếm tự tăng trong mỗi lần gọi hashCode của đối tượng có trạng thái so sánh không đổi. Cũng không dùng System.identityHashCode(this) để băm lớp có equals theo giá trị: hai đối tượng riêng biệt nhưng bằng nhau có thể nhận identity hash khác nhau.

## 7. Thiết kế khóa mutable và cache hashCode

Nếu trường dùng trong equals bị thay đổi khi đối tượng nằm trong HashMap hoặc HashSet, hashCode có thể đổi theo, làm việc tra cứu hoặc xóa theo khóa trở nên sai kỳ vọng.

Ví dụ về quy trình nguy hiểm:

~~~java
// Minh họa logic, không phải chương trình độc lập:
set.add(key);
key.setId("new-id"); // id được equals và hashCode sử dụng
set.contains(key);    // Không thể dựa vào kết quả để tra cứu như trước
~~~

Không thể chỉ “đóng băng” hashCode trong một cache rồi tiếp tục cho phép id đổi: sau khi trạng thái thay đổi, các đối tượng có cùng id mới theo equals có thể mang hash khác nhau nếu từng cache kết quả cũ. Điều này lại gây vi phạm hợp đồng.

Cách xử lý thực tế:

1. Chọn các trường định danh bất biến khi có thể. Với khóa chứa mảng hoặc List, cân nhắc sao chép phòng vệ và không để lộ cấu trúc mutable.
2. Nếu đối tượng đã nằm trong HashSet/HashMap, hạn chế sửa các trường quyết định equality; nếu bắt buộc đổi định danh, gỡ bằng trạng thái cũ trước khi sửa rồi thêm lại theo trạng thái mới.
3. Chỉ cache hashCode khi mọi dữ liệu ảnh hưởng đến giá trị băm được giữ bất biến trong suốt vòng đời phù hợp của đối tượng. Cache không thay thế hợp đồng equals–hashCode.

Cũng cần cân nhắc đối tượng được truy cập đồng thời: trạng thái mutable cần bảo đảm đồng bộ hoặc thiết kế bất biến; một công thức hashCode đúng trên lý thuyết không tự tạo ra thread safety.

## 8. Kiểm thử để xác nhận chọn trường băm đúng

Đừng chỉ kiểm tra hashCode một lần. Hãy tạo **nhiều đối tượng khác tham chiếu** nhưng bằng nhau theo mọi kịch bản có thể: đổi chữ hoa/thường nếu equality không phân biệt case; thay đổi trường hiển thị bị bỏ qua; thử các giá trị null hợp lệ; thử mảng có nội dung giống nhau; thử lớp con nếu có hệ phân cấp.

Với hai đối tượng a và b có cùng danh tính logic:

~~~java
if (!a.equals(b)) {
    throw new AssertionError("Đầu vào kiểm thử chưa bằng nhau theo equals");
}
if (a.hashCode() != b.hashCode()) {
    throw new AssertionError("Vi phạm hợp đồng equals/hashCode");
}
~~~

Đoạn này chạy trong ngữ cảnh có a và b tương ứng; không phải chương trình đứng riêng. Sau đó kiểm tra hành vi thực tế của HashSet.contains hoặc HashMap.get bằng một đối tượng **mới nhưng bằng nhau**.

Bảng trường hợp nên bao phủ:

| Trường hợp | Điều cần xác nhận |
| --- | --- |
| Cùng các trường định danh, khác displayName | equals true, hash bằng nhau |
| Cùng tenantId, SKU khác cách viết hoa/thường theo quy tắc | equals true, hash bằng nhau |
| Khác tenantId, cùng SKU | equals false; hash có thể trùng |
| Khác SKU và cùng tenantId | equals false; hash có thể trùng |
| Hai String "Aa" và "BB" | hash trùng là hợp lệ, equals false |
| Các giá trị null được hỗ trợ | Không ném NPE; equals/hash thống nhất |
| Dữ liệu so sánh giữ nguyên, gọi hash nhiều lần | Mã băm ổn định |
| Tạo khóa mới bằng khóa đã lưu trong Map | Lấy được giá trị tương ứng |

**Không viết bài test yêu cầu x khác y thì hashCode bắt buộc khác nhau**. Điều đó không phải hợp đồng của Java và sẽ bác bỏ sai các triển khai hợp lệ có collision.

Ngoài ra, hợp đồng equals còn có phản xạ, đối xứng, bắc cầu, nhất quán và so sánh null. Việc chọn đúng các trường trong hashCode không thay thế kiểm tra các điều kiện đó. Nếu dùng kế thừa và một lớp con thêm trường vào equals, cả định nghĩa equality lẫn tập trường băm phải được thiết kế để không phá vỡ quan hệ đối xứng/bắc cầu.

## 9. Checklist khi review hashCode

- **Bắt đầu từ equals:** chính xác những giá trị và quy tắc nào quyết định hai đối tượng bằng nhau?
- **Dùng biểu diễn tương đương:** nếu equals chuẩn hóa, so sánh theo nội dung hay theo quy tắc số riêng, hash cũng cần phản ánh cùng quan hệ.
- **Không thêm dữ liệu ngoài equality** khiến hai đối tượng bằng nhau có hash khác nhau.
- **Ưu tiên dùng đủ dữ liệu định danh** để có phân bố hợp lý, nhưng nhớ không dùng hết trường không tự động làm hash sai hợp đồng.
- **Chọn API đúng kiểu:** Objects.hash cho nhiều trường, Objects.hashCode cho một trường nullable, Arrays.hashCode cho mảng một chiều, Arrays.deepHashCode cho mảng lồng nhau nếu equals so sánh sâu.
- **Bảo đảm ổn định:** tránh dữ liệu ngẫu nhiên, thời gian, identity hash khi equality theo giá trị, hoặc trạng thái mutable của khóa đã lưu.
- **Kiểm tra bằng ví dụ cùng giá trị, khác tham chiếu:** equals true bắt buộc kéo theo hash bằng nhau; false không bắt buộc hash khác.

**Ghi nhớ:** lựa chọn dữ liệu băm đúng là lựa chọn một hàm *không phân biệt hai đối tượng mà equals coi là bằng nhau*. Sự tương thích với equals là điều bắt buộc; chất lượng phân bố, tốc độ và cách cài đặt là những mục tiêu cần tối ưu tiếp theo.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-code-implementation-002">Không đưa dữ liệu làm phân biệt hai đối tượng đang được coi là bằng nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Không đưa dữ liệu làm phân biệt hai đối tượng đang được coi là bằng nhau vào `hashCode()`

Khi cài đặt `hashCode()`, hãy xuất phát từ **định nghĩa bằng nhau trong `equals(Object)`**. Nếu hai đối tượng được `equals()` coi là bằng nhau, chúng **bắt buộc** trả về cùng một mã băm, ngay cả khi những thuộc tính khác của chúng khác nhau.

Quy tắc trọng tâm:

> Nếu `a.equals(b) == true` thì `a.hashCode() == b.hashCode()` phải đúng.

Đây là yêu cầu một chiều. Hai đối tượng **khác nhau** vẫn có thể trùng mã băm; Java cho phép *hash collision*. Sai lầm là đưa vào `hashCode()` một thuộc tính như tên hiển thị, ghi chú hoặc thời điểm cập nhật có thể **khác giữa các đối tượng mà `equals()` vẫn trả `true`**. Khi đó, `HashSet` và `HashMap` có thể nhận diện sai các đối tượng logic trùng nhau.

## 1. Xuất phát từ quan hệ bằng nhau của đối tượng

Giả sử ứng dụng quản lý khách hàng có:

- `customerId`: mã định danh ổn định của khách hàng.
- `displayName`: tên dùng để hiển thị, có thể chỉnh sửa.

Yêu cầu nghiệp vụ: **hai đối tượng `Customer` cùng `customerId` là cùng một khách hàng**, dù tên hiển thị khác nhau do thông tin được đồng bộ ở thời điểm khác nhau. Do đó:

~~~java
@Override
public boolean equals(Object obj) {
    if (this == obj) {
        return true;
    }
    if (!(obj instanceof Customer other)) {
        return false;
    }
    return customerId.equals(other.customerId);
}
~~~

Đây là **mảnh mã minh họa** nằm trong lớp `Customer`. Phép so sánh chỉ dựa vào `customerId`. Nếu đối tượng A mang mã `"C-01"`, tên `"An"` và đối tượng B mang cùng mã `"C-01"`, tên `"Bình"`, thì `A.equals(B)` phải là `true`.

Một `hashCode()` phù hợp là:

~~~java
@Override
public int hashCode() {
    return customerId.hashCode();
}
~~~

Vì cùng `customerId` thì cùng `customerId.hashCode()`, hai đối tượng được `equals()` coi là bằng nhau chắc chắn nhận mã băm giống nhau.

Không được tự ý bổ sung tên hiển thị vào mã băm:

~~~java
@Override
public int hashCode() {
    return Objects.hash(customerId, displayName); // SAI với equals() đang chỉ so ID.
}
~~~

`Objects.hash(...)` là API hợp lệ; **lỗi nằm ở việc chọn thuộc tính**, không phải ở API. Hai đối tượng cùng ID nhưng tên khác có thể nhận hai mã băm khác nhau, vi phạm hợp đồng bắt buộc.

### Hiểu theo nhóm tương đương

Nếu `equals()` xem tất cả đối tượng có ID `"C-01"` là bằng nhau, ta có một **nhóm tương đương** gồm nhiều bản thể hiện của cùng khách hàng. Hàm `hashCode()` phải có **cùng kết quả cho mọi phần tử của nhóm đó**. Nó có thể ánh xạ nhiều nhóm khác nhau vào cùng một số nguyên, nhưng **không được tách một nhóm bằng nhau thành nhiều mã băm**.

Nói cách khác, mọi dữ liệu ảnh hưởng tới mã băm phải thỏa điều kiện: khi `equals(a, b)` là `true`, phần dữ liệu được dùng phải tạo ra **kết quả băm giống nhau**. Cách dễ kiểm soát nhất là băm cùng khóa bằng nhau mà `equals()` sử dụng.

## 2. Ví dụ Java 17: đúng và sai trong cùng một chương trình

Tạo tệp **`HashCodeEqualityDemo.java`**:

~~~java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class HashCodeEqualityDemo {

    // ĐÚNG: nhận dạng khách hàng bằng customerId.
    static final class Customer {
        private final String customerId;
        private String displayName;

        Customer(String customerId, String displayName) {
            this.customerId = Objects.requireNonNull(customerId);
            this.displayName = Objects.requireNonNull(displayName);
        }

        void rename(String newName) {
            displayName = Objects.requireNonNull(newName);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Customer other)) {
                return false;
            }
            return customerId.equals(other.customerId);
        }

        @Override
        public int hashCode() {
            return customerId.hashCode();
        }
    }

    // SAI: equals() chỉ dùng ID nhưng hashCode() dùng cả tên.
    static final class BrokenCustomer {
        private final String customerId;
        private final String displayName;

        BrokenCustomer(String customerId, String displayName) {
            this.customerId = Objects.requireNonNull(customerId);
            this.displayName = Objects.requireNonNull(displayName);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof BrokenCustomer other)) {
                return false;
            }
            return customerId.equals(other.customerId);
        }

        @Override
        public int hashCode() {
            return 31 * customerId.hashCode() + displayName.hashCode();
        }
    }

    public static void main(String[] args) {
        Customer first = new Customer("C-01", "An");
        Customer sameId = new Customer("C-01", "Binh");

        System.out.println("GOOD equals: " + first.equals(sameId));
        System.out.println("GOOD same hash: "
                + (first.hashCode() == sameId.hashCode()));

        Set<Customer> goodSet = new HashSet<>();
        goodSet.add(first);
        System.out.println("GOOD contains same ID: " + goodSet.contains(sameId));

        first.rename("An moi");
        System.out.println("GOOD contains after rename: "
                + goodSet.contains(first));

        BrokenCustomer badFirst = new BrokenCustomer("C-01", "An");
        BrokenCustomer badSameId = new BrokenCustomer("C-01", "Binh");

        System.out.println("BAD equals: " + badFirst.equals(badSameId));
        System.out.println("BAD same hash: "
                + (badFirst.hashCode() == badSameId.hashCode()));

        Set<BrokenCustomer> badSet = new HashSet<>();
        badSet.add(badFirst);
        System.out.println("BAD contains same ID: "
                + badSet.contains(badSameId));

        badSet.add(badSameId);
        System.out.println("BAD set size: " + badSet.size());
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac HashCodeEqualityDemo.java
java HashCodeEqualityDemo
~~~

**Đầu ra:**

~~~text
GOOD equals: true
GOOD same hash: true
GOOD contains same ID: true
GOOD contains after rename: true
BAD equals: true
BAD same hash: false
BAD contains same ID: false
BAD set size: 2
~~~

**Đọc kết quả:**

1. `first` và `sameId` có cùng `customerId` nên `equals()` là `true`, mặc dù tên hiển thị khác nhau.
2. `Customer.hashCode()` chỉ dựa trên ID, nên hai giá trị hash chắc chắn bằng nhau. `HashSet.contains(sameId)` tìm thấy khách hàng đã thêm.
3. Thay đổi tên hiển thị của `first` **không đổi** `hashCode()` hoặc `equals()` theo định nghĩa ID; việc tra cứu chính phần tử đó trong `HashSet` vẫn hoạt động.
4. Hai `BrokenCustomer` vẫn được `equals()` xem bằng nhau, nhưng công thức hash thêm tên cho ra các kết quả khác nhau với `"An"` và `"Binh"`. Trong ví dụ này, `HashSet` tìm không thấy đối tượng có cùng ID và còn lưu được hai đối tượng mà `equals()` cho là bằng nhau.

Ví dụ sai dùng công thức `31 * id.hashCode() + displayName.hashCode()` để minh họa rõ sự phụ thuộc vào tên; nó không phải khuyến nghị băm tùy biến. Giá trị mã băm cụ thể không phải điều quan trọng; **việc hai đối tượng bằng nhau trả hash khác nhau** mới là vi phạm.

## 3. Tại sao `HashSet` và `HashMap` chịu ảnh hưởng?

Các collection dùng hash tổ chức tra cứu thành hai tầng khái niệm:

1. Chúng dùng mã từ `hashCode()` để xác định nơi cần tìm trong cấu trúc dữ liệu.
2. Trong khu vực tìm kiếm phù hợp, chúng dùng `equals()` để quyết định có phải cùng khóa/phần tử logic hay không.

Nếu `a.equals(b)` là `true` nhưng hai hash khác nhau, quá trình tìm `b` có thể đi theo một vị trí khác nơi `a` đang được lưu, dẫn tới `contains`, `get` hoặc loại trùng hoạt động sai kỳ vọng. Không có bảo đảm rằng `equals()` sẽ “cứu” một mã băm không hợp lệ; collection không có nghĩa vụ quét mọi phần tử để bù cho việc phá hợp đồng.

Ví dụ ở trên cố ý lựa chọn tên khiến hai đối tượng có hash khác nhau và `HashSet` biểu hiện lỗi. Trong chương trình khác, vị trí bucket và kết quả quan sát có thể phụ thuộc giá trị hash hoặc cách cài đặt collection, nhưng **bản thân sự vi phạm hợp đồng vẫn là lỗi trong lớp**, dù một thử nghiệm đơn lẻ tình cờ cho kết quả đúng.

Lưu ý thêm: sau khi đưa đối tượng vào `HashSet` hay làm khóa `HashMap`, **không thay đổi bất kỳ dữ liệu nào tham gia `equals()`/`hashCode()`** trong thời gian nó còn nằm trong cấu trúc đó. Ví dụ tốt sử dụng `customerId` là `final` và không có setter. Chỉ thay đổi `displayName` vốn bị bỏ qua trong cả hai phương thức nên không gây ra lỗi này.

## 4. Quy tắc chọn trường cho `hashCode()`

| Dữ liệu hoặc tình huống | Có nên tham gia mã băm? | Vì sao? |
| --- | --- | --- |
| `customerId` là khóa duy nhất mà `equals()` so sánh | **Có** | Cùng ID thì hash của ID giống nhau |
| `displayName` có thể khác ở hai khách hàng được coi là cùng một người | **Không** | Có thể làm hash khác trong khi `equals()` là `true` |
| `lastUpdated`, ghi chú, trạng thái giao diện bị `equals()` bỏ qua | **Không** | Chúng có thể đổi độc lập với quan hệ bằng nhau |
| Giá trị được suy ra hoàn toàn từ khóa bằng nhau bằng phép tính ổn định | **Có thể**, nhưng thường không cần | Hai đối tượng bằng nhau luôn tạo cùng giá trị suy ra |
| Một trường `equals()` có dùng nhưng không được đưa vào hash | **Có thể hợp lệ** | Hai đối tượng bằng nhau vẫn cho cùng hash; chỉ có thể tăng va chạm |
| Mã băm hằng số như `return 1;` | **Đúng hợp đồng nhưng không hiệu quả** | Mọi đối tượng trùng hash, làm tăng va chạm |

Vì vậy, “dùng đúng các trường trong `equals()`” là **quy tắc thực hành an toàn**, không phải luật bắt buộc phải băm mọi trường được `equals()` so sánh. Luật thật sự là: **bằng nhau thì mã băm phải bằng nhau**. Việc không băm một trường cần thiết để phân biệt đối tượng chỉ tăng khả năng va chạm; còn băm một trường có thể thay đổi giữa các đối tượng bằng nhau sẽ phá hợp đồng.

Ví dụ nếu một `Person` được so sánh bằng cả `countryCode` và `personalNumber`, mã băm chỉ theo `countryCode` vẫn hợp lệ nhưng dễ có nhiều va chạm. Mã băm theo cả hai trường thường phân bố tốt hơn:

~~~java
@Override
public int hashCode() {
    return Objects.hash(countryCode, personalNumber);
}
~~~

Đây là **mảnh mã minh họa**, giả định `equals()` thực sự so cả hai giá trị và hai trường có cách biểu diễn/so sánh nhất quán. Trong trường hợp `Customer` có `equals()` chỉ dùng ID, `Objects.hash(customerId, displayName)` lại không hợp lệ.

## 5. Các trường hợp dễ nhầm khi thiết kế

### A. So sánh không phân biệt hoa thường

Nếu `equals()` coi hai mã `"ab-1"` và `"AB-1"` bằng nhau, nhưng `hashCode()` băm trực tiếp chuỗi nguyên gốc, hợp đồng có thể bị phá. Cần dùng **cùng một dạng khóa chuẩn hóa** ở cả hai phương thức, ví dụ chuyển một mã ASCII được quy định chỉ gồm `A–Z`, `a–z`, chữ số và dấu gạch nối về chữ thường theo `Locale.ROOT` trước khi lưu.

~~~java
import java.util.Locale;

// Minh họa chuẩn hóa khóa trước khi lưu; đã kiểm tra định dạng input ở nơi gọi.
String canonicalId = rawId.toLowerCase(Locale.ROOT);
~~~

Sau đó dùng `canonicalId.equals(...)` và `canonicalId.hashCode()`. Với dữ liệu có quy tắc Unicode/phạm vi ngôn ngữ phức tạp hơn, cần một chính sách chuẩn hóa rõ ràng, nhất quán; chuyển về chữ thường chỉ là ví dụ cho miền mã đơn giản đã được kiểm soát. Tránh trường hợp `equals()` dùng `equalsIgnoreCase` còn hash dùng `String.hashCode()` nguyên gốc.

### B. Mảng và đối tượng lồng nhau

Nếu `equals()` dùng `Arrays.equals(bytesA, bytesB)` để so **nội dung mảng**, không dùng hash dựa trên **danh tính mảng**. Hãy dùng `Arrays.hashCode(bytes)` tương ứng. Tương tự, nếu `equals()` so các trường dữ liệu lồng nhau theo giá trị, `hashCode()` cần dùng cách băm nhất quán với phép bằng nhau đó.

Ví dụ với mảng `byte[] payload` bất biến về nội dung trong vòng đời làm khóa:

~~~java
@Override
public int hashCode() {
    return java.util.Arrays.hashCode(payload);
}
~~~

Đây là mảnh mã riêng, với giả định `equals()` dựa trên `Arrays.equals(payload, other.payload)`. Cần phòng ngừa việc mảng bị sửa bên ngoài, chẳng hạn sao chép phòng vệ ở constructor/getter.

### C. Trường dẫn xuất và dữ liệu cache

Một trường không xuất hiện trực tiếp trong `equals()` **không đương nhiên bị cấm** trong `hashCode()`. Nếu nó được suy ra xác định từ các trường bằng nhau và không đổi khi các trường đó không đổi, dùng nó vẫn có thể đúng. Nhưng đưa cache có thể chưa khởi tạo, thời điểm làm mới hoặc giá trị không đồng nhất giữa hai đối tượng bằng nhau vào công thức hash là nguy hiểm.

Cache chính **kết quả `hashCode()`** là vấn đề khác: nó có thể hợp lệ với một khóa bằng nhau thật sự bất biến và cách khởi tạo an toàn. Không nên dùng cache đó như lý do để bỏ qua hợp đồng hay cho phép khóa bằng nhau thay đổi.

### D. Dữ liệu đổi sau khi đưa vào collection

Có hai lỗi độc lập cần phân biệt:

- **Lỗi quan hệ bằng nhau:** hai đối tượng bằng nhau nhưng `hashCode()` phụ thuộc một trường khác nhau giữa chúng.
- **Lỗi thay đổi khóa:** một đối tượng bị sửa trường dùng trong `equals()`/`hashCode()` sau khi đưa vào `HashSet` hoặc `HashMap`.

Ngay cả khi `equals()` và `hashCode()` được viết hoàn toàn tương ứng, thay đổi các trường khóa trong khi đang dùng làm key vẫn có thể khiến lần tra cứu sau thất bại. Giải pháp phổ biến là dùng khóa bất biến và thay đổi thông tin hiển thị bên ngoài khóa.

## 6. Kiểm thử hợp đồng đúng trọng tâm

Đừng chỉ kiểm tra rằng một đối tượng bằng chính nó. Với lớp `Customer`, các ca quan trọng là:

1. Tạo hai đối tượng **cùng ID, khác tên hiển thị**. Xác nhận `a.equals(b)` và `b.equals(a)` đều `true`; `a.hashCode() == b.hashCode()` cũng phải `true`.
2. Tạo đối tượng **khác ID, cùng tên**. `equals()` phải `false` theo định nghĩa ID. Mã băm được phép bằng hoặc khác; không kiểm thử yêu cầu “khác đối tượng thì hash khác”.
3. Đưa `a` vào `HashSet`, dùng `b` cùng ID để kiểm tra `contains(b)` là `true`.
4. Nếu tên hiển thị có thể sửa, đổi tên và xác nhận phép bằng nhau/hash vẫn không bị ảnh hưởng, bởi vì tên không phải khóa.
5. Kiểm tra `equals(null) == false` và `equals` với một đối tượng không phải `Customer` là `false`.
6. Với ba đối tượng cùng ID, kiểm tra phản xạ, đối xứng và bắc cầu của `equals()`. Sau đó xác nhận hash bằng nhau cho mọi cặp đã được xác nhận bằng nhau.

Không nên viết kiểm thử giả định mọi ID khác đều sinh mã băm khác nhau: trong Java, `hashCode()` trả một `int`, vì vậy va chạm là điều không thể tránh khỏi ở miền dữ liệu đủ lớn. Hãy kiểm thử **bất biến một chiều** và hành vi sử dụng collection.

## 7. Cách ghi nhớ và áp dụng

Khi chuẩn bị ghi đè `hashCode()`, thực hiện theo thứ tự:

1. Xác định **định nghĩa bằng nhau** trước: những trường nào quyết định hai đối tượng thuộc cùng thực thể hoặc cùng giá trị logic?
2. Đảm bảo `equals(Object)` phản ánh chính xác định nghĩa đó và tuân thủ phản xạ, đối xứng, bắc cầu, nhất quán và hành vi với `null`.
3. Tạo mã băm từ những khóa/giá trị **chắc chắn giống nhau khi `equals()` là `true`**.
4. Loại khỏi mã băm các trường chỉ để hiển thị, ghi chú, trạng thái hoặc thông tin có thể khác giữa những đối tượng đã được coi là bằng nhau.
5. Ưu tiên khóa bất biến trong suốt vòng đời sử dụng đối tượng làm key của collection.
6. Viết test cho **hai bản thể hiện khác nhau nhưng cùng giá trị logic**, không chỉ test một tham chiếu so với chính nó.

**Ghi nhớ:** `hashCode()` không phải cách liệt kê mọi thuộc tính của một object. Nó phải tôn trọng cách `equals()` gom các đối tượng thành những nhóm bằng nhau. Băm thêm một trường có thể phân biệt các thành viên trong cùng nhóm là lỗi; cùng nhóm bằng nhau thì phải cùng mã băm, còn khác nhóm vẫn được phép va chạm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-code-implementation-003">Kết hợp hash của nhiều trường</a>

<details>
<summary>Xem chi tiết</summary>

# Kết hợp hash của nhiều trường trong Java

## Mục tiêu

Khi đối tượng được xem là bằng nhau dựa trên **nhiều thuộc tính**, phương thức `hashCode()` cần tạo ra một mã băm phù hợp với định nghĩa bằng nhau đó. Bài học này giải thích cách phối hợp hash của nhiều trường, khi nào dùng `Objects.hash(...)`, khi nào tự kết hợp bằng phép nhân `31`, cách xử lý `null`, kiểu nguyên thủy, mảng, đối tượng lồng nhau và những lỗi làm `HashMap`/`HashSet` hoạt động sai kỳ vọng.

Sau bài học, bạn có thể:

- Chọn các trường cần kết hợp dựa vào `equals(Object)`.
- Viết `hashCode()` kết hợp nhiều trường bằng Java 17+ và kiểm tra tính nhất quán.
- Phân biệt cách băm trường thông thường với cách băm **nội dung mảng**.
- Hiểu vì sao thứ tự, tính ổn định và va chạm hash ảnh hưởng tới thiết kế, nhưng mã băm không phải định danh duy nhất.

## 1. Vì sao phải kết hợp nhiều trường?

Giả sử khóa nghiệp vụ `PersonKey` gồm ba trường:

```java
String region;
int number;
boolean active;
```

Nếu `equals` quyết định hai khóa bằng nhau khi **cả ba trường** tương ứng bằng nhau, ta muốn `hashCode` tính mã băm từ bộ giá trị đó. Một cách mô tả là:

```text
hash(PersonKey) = combine(
    hash(region),
    hash(number),
    hash(active)
)
```

Điều bắt buộc theo hợp đồng `Object` là:

> Nếu `a.equals(b)` là `true` thì `a.hashCode() == b.hashCode()` cũng phải là `true`.

Chiều đảo **không bắt buộc**: hai đối tượng khác nhau vẫn có thể có cùng mã băm (**collision**). `hashCode()` trả về một số nguyên `int` 32-bit; số lượng giá trị hữu hạn khiến không thể kỳ vọng mã băm duy nhất cho mọi đối tượng khác nhau.

Có thể viết `hashCode()` trả về hằng số cho mọi đối tượng mà vẫn thỏa điều kiện “bằng nhau thì cùng hash”, nhưng điều này tạo nhiều va chạm và thường làm giảm hiệu quả của cấu trúc dữ liệu dựa trên hash. Vì vậy, lựa chọn thực hành tốt là kết hợp **các thành phần quyết định `equals`** để tạo phân bố hash hữu ích.

Hai câu hỏi phải trả lời trước khi viết hàm:

1. `equals` đang so sánh những trường nào?
2. Các trường ấy có ổn định trong thời gian đối tượng được sử dụng làm **khóa `HashMap`** hoặc **phần tử `HashSet`** hay không?

Không nên băm thêm một trường có thể khác nhau giữa hai đối tượng mà `equals` vẫn coi là bằng nhau: như vậy dễ tạo ra **hai hash khác nhau cho hai đối tượng bằng nhau**, vi phạm hợp đồng. Ngược lại, bỏ bớt trường khỏi hash chưa chắc làm sai hợp đồng, nhưng có thể tăng số lượng va chạm.

## 2. Cách nhanh và rõ ràng: `Objects.hash(...)`

Thư viện chuẩn có `java.util.Objects.hash(Object... values)`. Phương thức này nhận nhiều giá trị, tính hash kết hợp theo thứ tự đối số và hỗ trợ giá trị `null`. Các đối số nguyên thủy được **boxing** thành wrapper khi đi qua varargs kiểu `Object...`.

Ví dụ:

```java
@Override
public int hashCode() {
    return Objects.hash(region, number, active);
}
```

Cách này thường đủ tốt với value object thông thường khi số trường ít và đường thực thi không quá nhạy với chi phí cấp phát.

**Lưu ý:** `Objects.hash(region, number, active)` khác `Objects.hashCode(region)`:

- `Objects.hash(a, b, c)` **kết hợp nhiều giá trị** theo cách tương thích với `Arrays.hashCode(Object[])` cho mảng đối số varargs.
- `Objects.hashCode(x)` tính hash của **một đối tượng**; trả về `0` nếu `x == null`, nếu không trả về `x.hashCode()`.
- `Objects.hash(x)` với đúng một giá trị **không nhất thiết bằng** `Objects.hashCode(x)`: phép kết hợp varargs khởi đầu từ `1` và áp dụng công thức phối hợp, nên thường thêm bước `31`. Điều này không sai nếu cả `equals` và `hashCode` của lớp dùng nhất quán.

## 3. Cách thủ công: tích lũy hash theo hệ số 31

Một cách phổ biến là khởi tạo một số nguyên, sau đó lần lượt gộp từng trường:

```java
int result = 1;
result = 31 * result + hashOf(field1);
result = 31 * result + hashOf(field2);
result = 31 * result + hashOf(field3);
return result;
```

Trong đó `hashOf(...)` có thể là:

| Kiểu trường | Cách tính hash của một giá trị |
| --- | --- |
| `int` | `Integer.hashCode(value)` (hoặc chính giá trị `value`) |
| `long` | `Long.hashCode(value)` |
| `boolean` | `Boolean.hashCode(value)` |
| `byte`, `short`, `char` | `Byte.hashCode`, `Short.hashCode`, `Character.hashCode` |
| `float`, `double` | `Float.hashCode(value)`, `Double.hashCode(value)` |
| Đối tượng có thể `null` | `Objects.hashCode(value)` |
| Mảng nguyên thủy hoặc mảng đối tượng cần so theo phần tử | `Arrays.hashCode(array)`; xem mục 6 |
| Mảng đối tượng lồng nhau cần so sâu | `Arrays.deepHashCode(array)`; xem mục 6 |

Hệ số `31` là quy ước thực dụng đã được dùng rộng rãi trong Java. Nó giúp **thứ tự các giá trị** có ảnh hưởng đến kết quả, giảm bớt một số kiểu va chạm đơn giản so với phép cộng trần. Không có gì bảo đảm mọi tập giá trị khác nhau đều sinh hash khác nhau.

Khi phép nhân/cộng `int` tràn giới hạn, Java dùng phép toán `int` với kết quả theo 32-bit; overflow số nguyên trong cách phối hợp này là **bình thường** và vẫn có tính xác định. Không cần cố loại bỏ mọi overflow; đặc biệt không nên chuyển sang phép tính ngẫu nhiên làm mã băm thay đổi giữa các lần gọi cho cùng trạng thái đối tượng.

**Thứ tự trường:** Nếu bạn dùng `region → number → active` thì nên duy trì cùng thứ tự đó trong mỗi lần tính toán. Mã băm thay đổi khi đổi thứ tự trường vẫn có thể thỏa hợp đồng nếu triển khai nhất quán, nhưng thứ tự không ổn định giữa các lần tính là lỗi. Khi kiểm tra hai phiên bản công thức, chỉ có thể so số hash cụ thể khi cùng quy tắc và cùng thứ tự.

## 4. Ví dụ đầy đủ: `equals`, `Objects.hash` và cách thủ công

Tạo tệp **`MultiFieldHashDemo.java`**:

```java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class MultiFieldHashDemo {
    static final class PersonKey {
        private final String region;
        private final int number;
        private final boolean active;

        PersonKey(String region, int number, boolean active) {
            this.region = region;
            this.number = number;
            this.active = active;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof PersonKey other)) {
                return false;
            }
            return number == other.number
                    && active == other.active
                    && Objects.equals(region, other.region);
        }

        @Override
        public int hashCode() {
            return Objects.hash(region, number, active);
        }

        int manualHashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(region);
            result = 31 * result + Integer.hashCode(number);
            result = 31 * result + Boolean.hashCode(active);
            return result;
        }
    }

    public static void main(String[] args) {
        PersonKey first = new PersonKey("VN", 7, true);
        PersonKey equal = new PersonKey("VN", 7, true);
        PersonKey different = new PersonKey("VN", 7, false);

        System.out.println("first.equals(equal): "
                + first.equals(equal));
        System.out.println("same hash for equal keys: "
                + (first.hashCode() == equal.hashCode()));
        System.out.println("Objects.hash matches manual: "
                + (first.hashCode() == first.manualHashCode()));
        System.out.println("first.equals(different): "
                + first.equals(different));

        Set<PersonKey> keys = new HashSet<>();
        keys.add(first);
        System.out.println("set contains equal key: "
                + keys.contains(equal));

        PersonKey nullRegionA = new PersonKey(null, 7, true);
        PersonKey nullRegionB = new PersonKey(null, 7, true);
        System.out.println("null region keys equal: "
                + nullRegionA.equals(nullRegionB));
        System.out.println("null region hashes equal: "
                + (nullRegionA.hashCode() == nullRegionB.hashCode()));
    }
}
```

Chạy bằng JDK 17+:

```bash
javac MultiFieldHashDemo.java
java MultiFieldHashDemo
```

**Đầu ra:**

```text
first.equals(equal): true
same hash for equal keys: true
Objects.hash matches manual: true
first.equals(different): false
set contains equal key: true
null region keys equal: true
null region hashes equal: true
```

Giải thích:

- `equals` kiểm tra **cùng ba trường** mà `hashCode` dùng để kết hợp. `Objects.equals(region, other.region)` xử lý được trường hợp một hoặc cả hai chuỗi `null`.
- `Objects.hash` vận dụng công thức tích lũy tương tự ví dụ thủ công: `result` ban đầu là `1`, với mỗi phần tử của varargs là `result = 31 * result + elementHash`. Ở đây thứ tự trường và hash từng thành phần trùng nhau nên kết quả bằng nhau.
- `Integer.hashCode` trả chính giá trị `int`, còn `Boolean.hashCode` trả hằng số xác định cho `true` và `false`. `Objects.hashCode(null)` trả `0`.
- Hai `PersonKey` bằng nhau có hash giống nhau. Nhờ đó, `HashSet.contains(equal)` có thể tìm ra phần tử được thêm từ một instance khác nhưng cùng giá trị.
- Ví dụ dùng trường `final` và kiểu thành phần bất biến (`String`, `int`, `boolean`), giúp giữ trạng thái khóa ổn định.

**Không hiểu nhầm:** dòng `first.equals(different): false` không tự bảo đảm hai hash tương ứng phải khác nhau. Việc khác nhau về giá trị có thể vẫn trùng hash; đó không phải vi phạm hợp đồng.

## 5. Vì sao cộng thẳng hay chỉ lấy một trường thường kém?

Một triển khai tự phát như:

```java
return x + y; // Ví dụ kém: không phân biệt (1, 2) với (2, 1).
```

có nhiều va chạm hiển nhiên: `(1, 2)` và `(2, 1)` cho cùng tổng. Một triển khai khác như `return region.hashCode();` bỏ qua `number` và `active`, khiến nhiều đối tượng khác nhau có chung mã băm. Bỏ trường không nhất thiết là **lỗi hợp đồng**, nhưng có thể làm giảm độ phân tán nếu những trường bị bỏ có giá trị phân biệt cao.

Phép nhân 31 phân biệt thứ tự tốt hơn trong nhiều tình huống:

```java
int result = 1;
result = 31 * result + x;
result = 31 * result + y;
return result;
```

Tuy nhiên **không tồn tại hàm băm `int` đảm bảo không va chạm cho mọi bộ giá trị có thể có**. Mục tiêu là một hàm nhanh, ổn định, có phân bố phù hợp, cùng thỏa hợp đồng `equals/hashCode`.

Với khóa ít trường, `Objects.hash(...)` thường dễ đọc và giảm nguy cơ quên trường. Với đoạn mã gọi hash rất thường xuyên, phối hợp thủ công có thể giảm chi phí cấp phát varargs và boxing; hãy dựa trên **đo đạc thực tế** trước khi tối ưu. Tránh tạo ra một thuật toán phức tạp chỉ để “đẹp” số hash.

## 6. Trường mảng: băm **nội dung** khác băm **tham chiếu**

Đây là bẫy quan trọng khi kết hợp nhiều trường.

Mảng trong Java là đối tượng. Nếu một lớp quyết định bằng nhau theo **nội dung các phần tử** (`Arrays.equals`), thì `hashCode` cũng phải dùng hàm hash nội dung tương ứng (`Arrays.hashCode`). Băm mảng bằng phương thức kế thừa từ `Object` về cơ bản dựa trên danh tính tham chiếu, không phản ánh các phần tử.

Ví dụ sai về nguyên tắc:

```java
// Nếu equals dùng Arrays.equals(bytes, other.bytes) thì:
return Objects.hash(name, bytes); // Sai ý nghĩa: bytes là mảng.
```

Với `byte[]`, đối số `bytes` được coi như **một đối tượng mảng** của `Objects.hash`. Mảng byte kế thừa `hashCode` dựa trên danh tính từ `Object`; hai mảng có nội dung bằng nhau không được đảm bảo cho hash bằng nhau.

Đúng hơn:

```java
int result = Objects.hashCode(name);
result = 31 * result + Arrays.hashCode(bytes);
return result;
```

Với mảng `Object[]` có phần tử là những mảng con cần so sánh sâu, dùng cặp tương ứng:

- `Arrays.deepEquals(a, b)` với `Arrays.deepHashCode(a)`.
- `Arrays.equals(a, b)` với `Arrays.hashCode(a)` nếu muốn so theo phần tử ở một cấp (các mảng lồng nhau trong `Object[]` vẫn được so bằng quan hệ của chính chúng nếu không dùng phiên bản `deep`).

**Một lưu ý về varargs:** Khi truyền trực tiếp `Object[]` vào `Objects.hash(Object...)`, chính các phần tử của nó có thể được coi là các đối số varargs. Do đó cách gọi `Objects.hash(array)` dễ gây nhầm trong ngữ cảnh mảng. Khi yêu cầu là hash theo nội dung mảng, hãy gọi rõ `Arrays.hashCode(array)` hoặc `Arrays.deepHashCode(array)` tùy ngữ nghĩa.

### 6.1. Ví dụ hoàn chỉnh với trường mảng

Tạo tệp **`ArrayFieldHashDemo.java`**:

```java
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ArrayFieldHashDemo {
    static final class PacketKey {
        private final String protocol;
        private final byte[] payload;

        PacketKey(String protocol, byte[] payload) {
            this.protocol = Objects.requireNonNull(protocol);
            this.payload = Objects.requireNonNull(payload).clone();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof PacketKey other)) {
                return false;
            }
            return protocol.equals(other.protocol)
                    && Arrays.equals(payload, other.payload);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(protocol);
            result = 31 * result + Arrays.hashCode(payload);
            return result;
        }

        int firstByte() {
            return payload[0];
        }
    }

    public static void main(String[] args) {
        byte[] source = {1, 2, 3};
        PacketKey first = new PacketKey("tcp", source);
        PacketKey same = new PacketKey("tcp", new byte[] {1, 2, 3});
        PacketKey changed = new PacketKey("tcp", new byte[] {1, 2, 4});

        System.out.println("equal payloads: " + first.equals(same));
        System.out.println("equal hashes: "
                + (first.hashCode() == same.hashCode()));
        System.out.println("different payloads: "
                + first.equals(changed));

        Set<PacketKey> packets = new HashSet<>();
        packets.add(first);
        System.out.println("set finds equal payload: "
                + packets.contains(same));

        source[0] = 99; // Đổi mảng bên ngoài.
        System.out.println("internal first byte: " + first.firstByte());
        System.out.println("set still finds original: "
                + packets.contains(same));
    }
}
```

Chạy:

```bash
javac ArrayFieldHashDemo.java
java ArrayFieldHashDemo
```

**Đầu ra:**

```text
equal payloads: true
equal hashes: true
different payloads: false
set finds equal payload: true
internal first byte: 1
set still finds original: true
```

Ở đây `PacketKey` tạo **bản sao phòng vệ** (`clone`) của mảng trong constructor. Vì mã bên ngoài chỉ giữ mảng `source`, thay đổi `source` không làm đổi trạng thái nội bộ của khóa. Điều này đặc biệt quan trọng khi khóa đã được đưa vào `HashSet` hoặc `HashMap`.

**Giới hạn ví dụ:** `firstByte()` giả định payload không rỗng, chỉ phục vụ quan sát nội dung trong ví dụ đã dùng mảng ba phần tử. Trong API thực tế, phương thức truy cập cần xử lý trường hợp rỗng và việc xuất mảng ra bên ngoài cũng phải được kiểm soát (ví dụ trả bản sao).

## 7. Trường đối tượng và cấu trúc lồng nhau

Đối với trường là một đối tượng thông thường, dùng `Objects.hashCode(field)` hoặc `field.hashCode()` (nếu biết chắc khác `null`). Để toàn bộ mã băm phù hợp với `equals`, chính kiểu của trường đó cũng cần tuân thủ hợp đồng `equals/hashCode`.

Ví dụ:

```java
final class Address {
    private final String city;
    private final String street;

    // Constructor, equals và hashCode đầy đủ sẽ cần được triển khai.
}
```

Nếu một `CustomerKey` so sánh `customerId` và `address` bằng `Objects.equals`, mã băm có thể là:

```java
return Objects.hash(customerId, address);
```

Hai đoạn trên **chỉ minh họa thiết kế**, chưa phải chương trình độc lập để biên dịch/chạy; cần khai báo phần còn lại của lớp và bảo đảm `Address` có `equals/hashCode` phù hợp với ý nghĩa giá trị.

Không nên gọi `address.toString().hashCode()` chỉ để “lấy hash” của đối tượng. `toString()` vốn phục vụ trình bày/chẩn đoán, thường không được thiết kế làm định nghĩa bằng nhau. Cũng không nên dùng `System.identityHashCode(address)` nếu `equals` của lớp coi hai địa chỉ khác instance nhưng có nội dung giống nhau là bằng nhau.

Đối với tập hợp tiêu chuẩn:

- `List.hashCode()` kết hợp hash của các phần tử **có xét thứ tự**, phù hợp với `List.equals`.
- `Set.hashCode()` dựa trên các phần tử và **không phụ thuộc thứ tự duyệt**, phù hợp với `Set.equals`.
- `Map.hashCode()` dựa trên các entry, phù hợp với `Map.equals`.
- Một đối tượng giữ collection mutable làm thành phần bình đẳng có nguy cơ bị đổi hash khi collection bị chỉnh sửa. Tránh để bên ngoài sửa collection nội bộ khi nó là thành phần của khóa.

## 8. Kiểu đặc biệt và giá trị `null`

### 8.1. Kiểu nguyên thủy

Hãy dùng hàm hash chuẩn cho kiểu tương ứng, nhất là `long`, `float`, `double`. Chẳng hạn:

```java
int result = 1;
result = 31 * result + Long.hashCode(orderId);
result = 31 * result + Double.hashCode(amount);
result = 31 * result + Boolean.hashCode(confirmed);
```

Đây là **đoạn minh họa** giả định các biến trường đã được khai báo trong lớp.

Tránh viết `(int) orderId` cho trường `long`: phép ép cắt bỏ các bit cao và dễ tạo va chạm không cần thiết. Tránh ép số thực về `int` nếu quan hệ bằng nhau phân biệt các giá trị thực.

Với `float`/`double`, `Float.hashCode` và `Double.hashCode` có ngữ nghĩa bit nhất quán với cách so sánh giá trị theo `Float.equals`/`Double.equals`. Nếu `equals` của lớp dùng trực tiếp toán tử `==` trên giá trị dấu phẩy động thì cần rất cẩn thận: chẳng hạn `+0.0 == -0.0` theo `==` là `true`, nhưng hai giá trị đó có hash khác nhau theo `Double.hashCode`. Một phương thức `hashCode` dùng `Double.hashCode` khi `equals` dùng `==` có thể vi phạm hợp đồng ở trường hợp này. Hãy thống nhất chính sách so sánh (ví dụ dùng `Double.compare` trong `equals` cùng `Double.hashCode` trong hash) thay vì ghép các cách tính không tương thích.

### 8.2. `null`

`Objects.hash(region, category)` tự xử lý các giá trị `null`. Với công thức thủ công:

```java
int result = 1;
result = 31 * result + Objects.hashCode(region);
result = 31 * result + Objects.hashCode(category);
return result;
```

`Objects.hashCode(null)` trả `0`; không có `NullPointerException`. Tránh `region.hashCode()` khi có khả năng `region == null`.

### 8.3. `record`

Java `record` tự cung cấp `equals` và `hashCode` từ **toàn bộ record component**, trừ khi bạn chủ động ghi đè:

```java
record ProductKey(String code, int revision, boolean active) {}
```

Đây là cách gọn để mô hình hóa value object đơn giản. Tuy nhiên:

- Nếu record có component dạng **mảng**, `equals`/`hashCode` mặc định của record dùng ngữ nghĩa của component mảng (so theo tham chiếu), **không tự so nội dung mảng**. Nếu muốn bình đẳng theo phần tử, phải tự thiết kế cặp `equals/hashCode` thích hợp hoặc dùng cấu trúc khác.
- `record` có các field thành phần `final`, nhưng **không tự làm bất biến sâu** một component tham chiếu tới collection/mảng mutable.
- Nếu nghiệp vụ cần một tập trường khác với toàn bộ component tham gia bình đẳng, nên thiết kế value object/cấu trúc rõ ràng và duy trì nhất quán giữa `equals` với `hashCode`.

## 9. Trường thay đổi sau khi đưa đối tượng vào `HashMap`/`HashSet`

Xét lớp mutable mà `equals` và `hashCode` đều dựa trên `id`:

```java
final class MutableKey {
    int id;

    MutableKey(int id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof MutableKey other && id == other.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
```

Đoạn trên là **minh họa lớp**; có thể dùng trong một tệp Java thích hợp với khai báo lớp ngoài hoặc trong `main` của một chương trình.

Khi `id` thay đổi sau khi thêm khóa vào `HashMap`, việc tra cứu có thể không tìm ra khóa nữa vì mã băm lúc tra cứu khác mã băm khi khóa được phân vào cấu trúc dữ liệu. Kể cả `equals/hashCode` hiện vẫn nhất quán với nhau, thay đổi trạng thái tham gia hash vẫn khiến ứng dụng gặp vấn đề.

**Quy tắc thực hành:** Với khóa theo giá trị, ưu tiên thiết kế bất biến; các trường tham gia `equals/hashCode` nên được cố định trong suốt thời gian dùng làm khóa. Nếu buộc phải sửa, loại bỏ khóa bằng trạng thái cũ rồi tái thêm theo trạng thái mới ở thời điểm thích hợp, với cơ chế quản lý nhất quán.

## 10. Cách kiểm tra chất lượng triển khai

Ngoài việc chạy ví dụ, nên kiểm tra các nhóm tình huống sau:

| Tình huống | Điều cần xác minh |
| --- | --- |
| Hai instance có các trường theo `equals` giống nhau | `equals == true` và `hashCode` bằng nhau |
| Khác đúng một trường tham gia `equals` | `equals == false`; hash **có thể** bằng hoặc khác, không cần khẳng định |
| Trường tham chiếu có thể `null` | Không có `NullPointerException`; cùng trạng thái `null` cho hash nhất quán |
| Có mảng so theo nội dung | Cặp `Arrays.equals/hashCode` hoặc `deepEquals/deepHashCode` được dùng tương ứng |
| Có trường mutable | Không bị chỉnh sửa khi làm khóa hoặc có chiến lược quản lý rõ ràng |
| Dùng lớp thành phần | Thành phần đó cũng có `equals/hashCode` tương thích |
| Dùng `HashSet`/`HashMap` | Có thể tìm phần tử/khóa bằng một instance khác mà `equals` coi là bằng nhau |

Những lỗi thường thấy trong review:

1. `equals` kiểm tra `name` và `age`, nhưng `hashCode` lại băm `name` với `createdAt`. Hai đối tượng bằng nhau có thể có hash khác nhau nếu `createdAt` khác.
2. Dùng `Objects.hash(bytes)` trong khi `equals` dùng `Arrays.equals(bytes, ...)`, dẫn đến băm danh tính mảng thay vì nội dung mảng.
3. Gọi `field.hashCode()` cho một trường có thể `null`.
4. Tính hash một lần rồi cache nhưng các trường liên quan vẫn mutable và có thể thay đổi, khiến hash cache không còn phản ánh trạng thái so sánh.
5. Tin rằng hash khác nhau là điều kiện cần để các đối tượng khác nhau; thực tế collision là hợp lệ.
6. Đưa dữ liệu hash vào định danh lâu dài, chữ ký bảo mật hoặc phương thức kiểm tra duy nhất: `hashCode()` không được thiết kế cho các mục đích đó.

## 11. Bài tập tự kiểm tra

**Bài 1.** Lớp `BookKey` so sánh theo `isbn` và `edition`. Mã băm nào dễ đọc và phù hợp?

```java
@Override
public int hashCode() {
    return Objects.hash(isbn, edition);
}
```

**Đáp án:** Phù hợp nếu `equals` cũng xét đúng hai trường đó với quy tắc tương ứng, còn các giá trị ổn định khi đối tượng làm khóa.

**Bài 2.** `equals` chỉ so `username` và `tenantId`. Có được thêm `lastLoginTime` vào `hashCode` không?

**Đáp án:** Không nên. Hai đối tượng có cùng `username`/`tenantId` nhưng `lastLoginTime` khác có thể `equals == true` trong khi hash khác nhau, gây vi phạm hợp đồng.

**Bài 3.** Lớp chứa `int[] scores` và dùng `Arrays.equals(scores, other.scores)`. Mã băm của trường `scores` cần tính bằng gì?

**Đáp án:** `Arrays.hashCode(scores)`, sau đó kết hợp với hash các trường còn lại. `scores.hashCode()` băm theo danh tính mảng và không phù hợp nếu bằng nhau được xác định theo nội dung.

**Bài 4.** Hai đối tượng khác nhau có được trả cùng hash không?

**Đáp án:** Có. Collision không vi phạm hợp đồng; quan hệ bắt buộc chỉ đi theo chiều `equals == true ⇒ hash giống nhau`.

**Bài 5.** Tại sao `Objects.hash(...)` thuận tiện nhưng công thức `31 * result + ...` vẫn phổ biến?

**Đáp án:** `Objects.hash` ngắn gọn, xử lý `null` và boxing tự động, dễ giảm lỗi bỏ sót trường. Công thức thủ công cho phép kiểm soát chính xác kiểu dữ liệu và tránh một số chi phí varargs/boxing ở đường nóng, đổi lại cần cẩn thận chọn đúng trường, đúng thứ tự, đúng hàm hash thành phần.

## Ghi nhớ

**Bắt đầu từ `equals`, rồi chọn cùng những thuộc tính quyết định bằng nhau để kết hợp thành `hashCode`.** Với trường thông thường, `Objects.hash(field1, field2, ...)` là lựa chọn mặc định dễ dùng; với nhu cầu kiểm soát hiệu năng hoặc dữ liệu đặc biệt, kết hợp thủ công bằng `31` và các hàm hash theo kiểu. Với mảng, phải dùng `Arrays.hashCode`/`Arrays.deepHashCode` nếu định nghĩa bằng nhau dựa trên nội dung. Bảo đảm hash ổn định khi đối tượng làm khóa; chấp nhận collision như một phần bình thường của hashing.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-code-implementation-004">Dùng phương thức hashCode() của các lớp bao cho kiểu nguyên thủy</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng phương thức `hashCode()` của các lớp bao cho kiểu nguyên thủy

## 1. Mục tiêu

Khi tự cài đặt `hashCode()` cho một lớp Java, ta cần chuyển từng trường tham gia quan hệ bằng nhau thành giá trị băm kiểu `int`, rồi kết hợp các giá trị ấy theo một quy tắc ổn định. Với trường có kiểu nguyên thủy (`primitive`), Java cung cấp ngay các phương thức `hashCode(primitive)` **tĩnh** trên lớp bao tương ứng, ví dụ `Integer.hashCode(int)` hoặc `Double.hashCode(double)`.

Sau bài này, bạn cần:

- Biết đủ tám cặp primitive và wrapper; gọi đúng phương thức static mà không cần đóng hộp thủ công.
- Hiểu vì sao kết quả `hashCode` của từng loại có thể khác cách ép kiểu thô sang `int`, đặc biệt `long`, `float` và `double`.
- Cài đặt `equals()` và `hashCode()` nhất quán khi lớp có nhiều trường primitive.
- Tránh nhầm lẫn giữa hash của **một giá trị**, hash kết hợp của **một đối tượng nhiều trường**, và danh tính đối tượng.
- Biết xử lý `NaN`, `+0.0` và `-0.0` để không phá hợp đồng `equals`/`hashCode`.

## 2. Phương thức static của tám lớp bao

Tất cả lớp dưới đây thuộc `java.lang`, không cần `import`. Mỗi phương thức nhận trực tiếp một primitive và trả về `int`:

| Kiểu nguyên thủy | Lớp bao | Cách lấy hash từ primitive | Quy tắc chính |
| --- | --- | --- | --- |
| `boolean` | `Boolean` | `Boolean.hashCode(value)` | `true → 1231`, `false → 1237`. |
| `byte` | `Byte` | `Byte.hashCode(value)` | Trả về trị số `byte` dưới dạng `int`. |
| `short` | `Short` | `Short.hashCode(value)` | Trả về trị số `short` dưới dạng `int`. |
| `char` | `Character` | `Character.hashCode(value)` | Trả về mã số của đơn vị mã UTF-16 kiểu `char`. |
| `int` | `Integer` | `Integer.hashCode(value)` | Trả về chính giá trị `int`. |
| `long` | `Long` | `Long.hashCode(value)` | Trộn 32 bit cao và 32 bit thấp. |
| `float` | `Float` | `Float.hashCode(value)` | Dựa trên `Float.floatToIntBits(value)`. |
| `double` | `Double` | `Double.hashCode(value)` | Dựa trên bit của `Double.doubleToLongBits(value)`, rồi trộn hai nửa 32 bit. |

Ví dụ gọi trực tiếp:

```java
int ageHash = Integer.hashCode(25);
int enabledHash = Boolean.hashCode(true);
int idHash = Long.hashCode(5_000_000_000L);
int ratioHash = Float.hashCode(1.5f);
int amountHash = Double.hashCode(1.5);
```

Đây là đoạn minh họa, không phải chương trình độc lập. Các phương thức `hashCode(primitive)` tĩnh đã có từ Java 8, do đó dùng được trong Java 17+.

### Static `hashCode(primitive)` và instance `hashCode()`

Lớp bao còn có phương thức `hashCode()` **của đối tượng**:

```java
int a = Integer.hashCode(42);        // static: tham số int
int b = Integer.valueOf(42).hashCode(); // instance: đối tượng Integer
// a == b
```

Hai cách cho cùng kết quả đối với giá trị tương ứng. Tuy nhiên, khi dữ liệu gốc đã là primitive, hãy ưu tiên lời gọi static: diễn đạt ý định rõ, không cần tạo một biến wrapper, tránh yêu cầu đóng hộp chỉ để gọi instance method. Không khẳng định rằng mỗi lần autoboxing chắc chắn cấp phát đối tượng: JVM có thể dùng cache hoặc tối ưu hóa.

Một biến tham chiếu wrapper có thể `null`; gọi `wrapper.hashCode()` trực tiếp khi null sẽ ném `NullPointerException`. Primitive không bao giờ null, vì vậy `Integer.hashCode(int)` không gặp trường hợp đó.

## 3. Hiểu cơ chế từng nhóm kiểu

### 3.1. `boolean`, `byte`, `short`, `char` và `int`

`Boolean.hashCode(true)` trả `1231`; `Boolean.hashCode(false)` trả `1237`. Đây là các hằng số do Java định nghĩa, **không phải** `1` và `0`.

`Byte.hashCode(byte)` và `Short.hashCode(short)` trả về giá trị số sau khi chuyển sang `int`. Với giá trị âm, dấu âm được bảo toàn. `Character.hashCode(char)` trả về mã số của một `char` (0–65535), vốn là **một đơn vị mã UTF-16**, không nhất thiết đại diện cho toàn bộ một ký tự Unicode nằm ngoài Basic Multilingual Plane. `Integer.hashCode(int)` trả về đúng giá trị `int` đầu vào.

Ví dụ:

```java
Boolean.hashCode(true);          // 1231
Boolean.hashCode(false);         // 1237
Byte.hashCode((byte) -5);        // -5
Short.hashCode((short) 300);     // 300
Character.hashCode('A');        // 65
Integer.hashCode(42);           // 42
```

Các con số này là giá trị hash của từng primitive; **không đảm bảo khác nhau giữa mọi đầu vào hoặc mọi kiểu dữ liệu**. Các đối tượng phức hợp phải kết hợp hash theo những trường tạo nên giá trị của chúng.

### 3.2. `long`: không ép kiểu mất 32 bit cao

`long` có 64 bit, còn `hashCode()` phải trả `int` có 32 bit. Công thức chuẩn của `Long.hashCode(long value)` tương đương:

```java
(int) (value ^ (value >>> 32))
```

Đây là biểu thức minh họa: XOR 32 bit cao với 32 bit thấp, rồi lấy 32 bit kết quả. Khi ép thẳng `(int) value`, 32 bit cao bị bỏ đi; vì vậy hai giá trị `long` chỉ khác nhau ở phần cao có thể bị trùng hash một cách không cần thiết.

Ví dụ:

```java
long id = 0x1_0000_0002L;
int lowOnly = (int) id;       // 2
int mixed = Long.hashCode(id); // 3: 1 XOR 2
```

`Long.hashCode` vẫn có thể xảy ra collision, vì không thể ánh xạ không trùng mọi giá trị 64 bit vào miền 32 bit. Mục tiêu là tạo kết quả băm xác định và phù hợp với hợp đồng, không phải tạo mã định danh duy nhất.

### 3.3. `float` và `double`: dùng bit chuẩn hóa của kiểu dấu phẩy động

Không dùng `(int) someFloat` hay `(int) someDouble` làm phương án mặc định. Ép kiểu số thực sang `int` chuyển đổi trị số, có thể mất phần thập phân và không giữ các phân biệt quan trọng trong hợp đồng của wrapper.

Về ngữ nghĩa, các cách tính là:

```java
int floatHash = Float.floatToIntBits(valueFloat);

// Hành vi tương đương Double.hashCode(valueDouble):
long bits = Double.doubleToLongBits(valueDouble);
int doubleHash = (int) (bits ^ (bits >>> 32));
```

Đây là đoạn minh họa công thức; giả sử hai biến `valueFloat` và `valueDouble` đã được khai báo đúng kiểu.

`Float.floatToIntBits` và `Double.doubleToLongBits` **quy các biểu diễn NaN khác nhau về một biểu diễn bit NaN chuẩn** cho mục đích so sánh/hash. Chúng vẫn phân biệt dấu của zero:

- `Float.hashCode(+0.0f)` khác `Float.hashCode(-0.0f)`.
- `Double.hashCode(+0.0)` khác `Double.hashCode(-0.0)`.
- Các payload NaN khác nhau được chuẩn hóa về cùng bit pattern, nên có cùng hash theo phương thức wrapper tương ứng.

Điểm này đặc biệt quan trọng khi viết `equals()`:

- Toán tử primitive `0.0 == -0.0` là `true`; `Double.compare(0.0, -0.0)` **khác 0**.
- Toán tử primitive `Double.NaN == Double.NaN` là `false`; `Double.compare(Double.NaN, Double.NaN)` là `0`.
- Nếu `equals()` của lớp dùng `==` để so trường `double` nhưng `hashCode()` dùng `Double.hashCode`, hai đối tượng chứa `+0.0` và `-0.0` có thể được `equals` coi bằng nhau nhưng có hash khác nhau, **vi phạm hợp đồng**.

Với ý định dùng ngữ nghĩa wrapper, so `double` bằng `Double.compare(a, b) == 0` và so `float` bằng `Float.compare(a, b) == 0`, đồng thời dùng `Double.hashCode`/`Float.hashCode`. Nếu bài toán cần một quan hệ bằng nhau khác (chẳng hạn coi `+0.0` và `-0.0` bằng nhau), hãy **chuẩn hóa nhất quán cả trong `equals` và `hashCode`**. Không tự dùng so sánh sai số `epsilon` cho `equals` vì “gần bằng” thông thường có thể không bắc cầu.

## 4. Từ hash từng trường đến hash của đối tượng

Một lớp có nhiều trường cần tạo hash tổng hợp. Một mẫu phổ biến:

```java
int result = 1;
result = 31 * result + Integer.hashCode(count);
result = 31 * result + Long.hashCode(id);
result = 31 * result + Boolean.hashCode(active);
```

`31` là hệ số thường gặp trong Java, **không phải yêu cầu bắt buộc của hợp đồng**. Phép nhân/cộng `int` có thể tràn số; Java thực hiện tràn `int` theo quy tắc 32 bit và đó là điều bình thường trong một phép băm. Cần giữ **đúng thứ tự và cùng tập trường** mỗi lần tính.

Quan trọng hơn công thức cụ thể là hợp đồng của `Object.hashCode()`:

> Nếu `a.equals(b)` trả `true`, thì `a.hashCode() == b.hashCode()` bắt buộc là `true`.

Chiều ngược lại không bắt buộc: hai đối tượng không bằng nhau vẫn có thể chung hash. Hash cũng **không** chứng minh hai đối tượng là cùng một tham chiếu.

Các trường dùng để so `equals` phải tương ứng với các trường dùng để tính `hashCode`. Đừng thêm trường tạm, số lần truy cập, địa chỉ đối tượng hoặc timestamp thay đổi vào hash khi chúng không phải một phần giá trị bằng nhau. Với đối tượng làm khóa của `HashMap` hoặc phần tử của `HashSet`, tránh thay đổi trạng thái có ảnh hưởng đến hash sau khi thêm vào collection.

## 5. Chương trình Java 17+ đầy đủ

Tạo tệp `WrapperHashCodeDemo.java`. Ví dụ gọi cả tám wrapper và cài đặt `equals`/`hashCode` cho một value object có các trường primitive:

```java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class WrapperHashCodeDemo {

    static final class ReadingKey {
        private final long deviceId;
        private final int sequence;
        private final boolean valid;
        private final double reading;

        ReadingKey(long deviceId, int sequence,
                   boolean valid, double reading) {
            this.deviceId = deviceId;
            this.sequence = sequence;
            this.valid = valid;
            this.reading = reading;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ReadingKey other)) {
                return false;
            }

            return deviceId == other.deviceId
                    && sequence == other.sequence
                    && valid == other.valid
                    && Double.compare(reading, other.reading) == 0;
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(deviceId);
            result = 31 * result + Integer.hashCode(sequence);
            result = 31 * result + Boolean.hashCode(valid);
            result = 31 * result + Double.hashCode(reading);
            return result;
        }
    }

    public static void main(String[] args) {
        System.out.println("boolean: " + Boolean.hashCode(true));
        System.out.println("byte: " + Byte.hashCode((byte) 7));
        System.out.println("short: " + Short.hashCode((short) 300));
        System.out.println("char: " + Character.hashCode('A'));
        System.out.println("int: " + Integer.hashCode(42));
        System.out.println("long: "
                + Long.hashCode(0x1_0000_0002L));
        System.out.println("float: " + Float.hashCode(1.5f));
        System.out.println("double: " + Double.hashCode(1.5));

        ReadingKey a = new ReadingKey(100L, 2, true, 1.5);
        ReadingKey b = new ReadingKey(100L, 2, true, 1.5);
        Set<ReadingKey> keys = new HashSet<>();
        keys.add(a);

        System.out.println("a == b: " + (a == b));
        System.out.println("a.equals(b): " + a.equals(b));
        System.out.println("same hash: "
                + (a.hashCode() == b.hashCode()));
        System.out.println("set contains b: " + keys.contains(b));

        System.out.println("primitive zeros equal: "
                + (0.0 == -0.0));
        System.out.println("double zero hashes equal: "
                + (Double.hashCode(0.0)
                        == Double.hashCode(-0.0)));

        double otherNaN =
                Double.longBitsToDouble(0x7ff0000000000001L);
        System.out.println("NaNs compare equal: "
                + (Double.compare(Double.NaN, otherNaN) == 0));
        System.out.println("NaN hashes equal: "
                + (Double.hashCode(Double.NaN)
                        == Double.hashCode(otherNaN)));

        System.out.println("Objects.hash(42): "
                + Objects.hash(42));
    }
}
```

Biên dịch và chạy:

```bash
javac WrapperHashCodeDemo.java
java WrapperHashCodeDemo
```

Kết quả mong đợi trên Java 17+:

```text
boolean: 1231
byte: 7
short: 300
char: 65
int: 42
long: 3
float: 1069547520
double: 1073217536
a == b: false
a.equals(b): true
same hash: true
set contains b: true
primitive zeros equal: true
double zero hashes equal: false
NaNs compare equal: true
NaN hashes equal: true
Objects.hash(42): 73
```

### Giải thích kết quả

1. `Boolean` dùng hai hằng số hash; `Byte`/`Short`/`Character`/`Integer` phản ánh trị số theo quy tắc riêng từng kiểu.
2. `Long.hashCode(0x1_0000_0002L)` trộn phần cao `1` và phần thấp `2` thành `3`.
3. `Float.hashCode(1.5f)` và `Double.hashCode(1.5)` dựa trên mẫu bit chuẩn của số thực, không phải ép `1.5` thành số nguyên `1`.
4. `a` và `b` là hai đối tượng khác danh tính (`==` là `false`), nhưng mọi trường tham gia `equals` giống nhau, nên `a.equals(b)` là `true` và hash bằng nhau.
5. Vì hash và equality phù hợp, `HashSet` tìm được `b` dù chỉ thêm `a`.
6. Primitive `0.0 == -0.0` đúng, nhưng wrapper hash phân biệt dấu zero. Tương tự, `Double.compare` và `Double.hashCode` cùng coi mọi NaN là tương đương theo quy tắc bit chuẩn.
7. `Objects.hash(42)` trả `73`, khác `Integer.hashCode(42) == 42`, vì đây là **hàm kết hợp hash của danh sách tham số**, kể cả khi chỉ có một phần tử: với một tham số, phép hash mảng bắt đầu từ `1` rồi nhân `31` và cộng hash của phần tử.

## 6. So sánh với `Objects.hash` và `Objects.hashCode`

Có ba API dễ bị nhầm:

| Lời gọi | Ý nghĩa | Lưu ý |
| --- | --- | --- |
| `Integer.hashCode(value)` | Hash trực tiếp một `int` | Rõ ràng, không yêu cầu biến wrapper. |
| `Objects.hashCode(object)` | Gọi hash của **một đối tượng**, trả `0` nếu null | Primitive truyền vào sẽ cần boxing để thành `Object`. |
| `Objects.hash(a, b, ...)` | Kết hợp hash của nhiều đối tượng trong danh sách tham số | Dùng varargs/mảng, primitive phải boxing; kết quả một phần tử vẫn có thể khác hash riêng của phần tử. |

Ví dụ:

```java
Integer.valueOf(42).hashCode(); // 42
Integer.hashCode(42);           // 42
Objects.hashCode(42);           // 42 (boxing)
Objects.hash(42);               // 73 (một phần tử trong mảng)
Objects.hash(10, true, 1.5);    // hash tổng hợp các giá trị
```

Đoạn minh họa cần `import java.util.Objects;`.

`Objects.hash(...)` thuận tiện để viết ít mã, nhưng tạo mảng varargs và có thể boxing các primitive; với mã hash được tính rất thường xuyên hoặc nơi có yêu cầu hiệu năng nghiêm ngặt, cách kết hợp tường minh từ các phương thức static wrapper có thể giảm các đối tượng trung gian. JVM có thể tối ưu hóa cấp phát, do đó cần **benchmark đúng workload** trước khi kết luận về chênh lệch hiệu năng thực tế.

`Arrays.hashCode(primitiveArray)` lại là API riêng để tính hash theo các **phần tử của một mảng**. Đừng dùng `array.hashCode()` nếu `equals()` của lớp so nội dung mảng: mảng kế thừa cách hash theo danh tính của `Object`. Trong trường hợp đó, `Arrays.equals(a, b)` thường cần đi cùng `Arrays.hashCode(a)` (mảng lồng nhau có những API `deepEquals`/`deepHashCode` riêng).

## 7. Những lỗi cần tránh

### Lỗi 1: Ép `long` xuống `int`

```java
// Dễ bỏ mất ảnh hưởng của 32 bit cao:
int hash = (int) orderId;

// Bao gồm cả phần cao và thấp:
int hash = Long.hashCode(orderId);
```

### Lỗi 2: Ép `float` hoặc `double` xuống `int`

```java
// Mất phần thập phân và nhiều phân biệt của số thực:
int hash = (int) temperature;

// Hành vi theo wrapper:
int hash = Double.hashCode(temperature);
```

### Lỗi 3: `equals` so dấu phẩy động bằng `==` nhưng hash dùng wrapper

```java
// Không nhất quán khi dữ liệu có +0.0 và -0.0:
return this.value == other.value;

// Nếu hash dùng Double.hashCode(value), có thể chọn:
return Double.compare(this.value, other.value) == 0;
```

Các đoạn trên chỉ là **dòng minh họa bên trong phương thức của một lớp có trường `double value`**, không phải chương trình Java độc lập. Phải xác định trước quan hệ bằng nhau mong muốn; nếu yêu cầu coi hai zero bằng nhau, thay vì sử dụng `Double.compare`, hãy chuẩn hóa chúng cùng về `+0.0` cho cả so sánh và hash.

### Lỗi 4: Chỉ dùng hash của một trường trong khi `equals` xét nhiều trường

Điều này có thể **vẫn không vi phạm** hợp đồng `equals/hashCode` nếu hash đó bất biến cho mọi cặp được coi là bằng nhau, nhưng thường làm tăng collision và suy giảm hiệu năng collection. Hãy cân nhắc kết hợp đầy đủ các trường tạo nên equality. Chiều ngược lại nguy hiểm hơn: thêm trường **không tham gia `equals`** nhưng có thể khác nhau giữa hai đối tượng bằng nhau vào hash sẽ **vi phạm hợp đồng**.

### Lỗi 5: Nghĩ rằng “hash khác nhau” mới chứng minh hai đối tượng khác nhau

Nếu hai hash **khác nhau** thì hai đối tượng không thể bằng nhau theo một triển khai tuân thủ hợp đồng; tuy nhiên, hai hash **bằng nhau** chưa chứng minh hai đối tượng bằng nhau. `HashMap` và `HashSet` vẫn cần kiểm tra equality khi thích hợp.

### Lỗi 6: Hash dựa vào trạng thái có thể thay đổi

Nếu trường tham gia `equals/hashCode` bị sửa sau khi đối tượng vào `HashSet`, lời gọi `contains` có thể không tìm được phần tử cũ. Ưu tiên khóa bất biến với trường `final` và không để lộ thành phần mutable gây ảnh hưởng đến hash.

## 8. Checklist cài đặt `hashCode` cho lớp có primitive

1. Liệt kê đúng các trường quyết định quan hệ bằng nhau của lớp.
2. Với mỗi trường primitive, dùng `Boolean/Byte/Short/Character/Integer/Long/Float/Double.hashCode(value)` tương ứng.
3. Với trường là tham chiếu có thể null, dùng quy tắc null-safe, ví dụ `Objects.hashCode(field)`; với chuỗi, collection hoặc mảng, xem chính hợp đồng equality của chúng.
4. Kết hợp các giá trị hash theo thứ tự ổn định, ví dụ nhân `31` rồi cộng.
5. Kiểm tra hai đối tượng bằng nhau **luôn** có hash bằng nhau, kể cả các ca biên: số âm, `long` có bit cao, `NaN`, `+0.0`/`-0.0` và null (nếu được phép).
6. Kiểm thử việc thêm một key và tìm bằng một key khác có cùng giá trị trong `HashSet`/`HashMap`.
7. Nếu hash nằm trên đường nóng hiệu năng, đo trước khi quyết định dùng `Objects.hash` hay kết hợp thủ công.

## 9. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao nên dùng `Long.hashCode(id)` thay vì `(int) id` cho trường `long`?

**Trả lời:** Vì `Long.hashCode` kết hợp cả hai nửa 32 bit. Ép kiểu xuống `int` chỉ giữ nửa thấp.

**Câu 2.** Với `double`, dùng `value == other.value` trong `equals` và `Double.hashCode(value)` trong `hashCode` có luôn an toàn không?

**Trả lời:** Không. Primitive `+0.0 == -0.0` trả `true` trong khi hai giá trị có hash wrapper khác nhau. Nếu chọn ngữ nghĩa wrapper, dùng `Double.compare(...) == 0` và `Double.hashCode(...)` nhất quán.

**Câu 3.** `Integer.hashCode(42)` và `Objects.hash(42)` có bằng nhau không?

**Trả lời:** Không. Trường hợp này lần lượt là `42` và `73` vì `Objects.hash` dùng thuật toán hash mảng chứa một phần tử.

**Câu 4.** Có thể dùng cùng một mã hash cho nhiều đối tượng khác nhau không?

**Trả lời:** Có. Collision được phép. Điều bắt buộc là những đối tượng được `equals` coi bằng nhau phải có mã hash bằng nhau.

**Câu 5.** Khi trường `boolean enabled` tham gia `equals`, cách hash trực tiếp là gì?

**Trả lời:** `Boolean.hashCode(enabled)`, trả về `1231` nếu `true` và `1237` nếu `false`.

**Ghi nhớ:** Các phương thức `hashCode(primitive)` **tĩnh trên lớp bao** cho phép chuyển primitive thành `int` theo quy tắc Java đã định nghĩa. Hãy kết hợp chúng nhất quán với các trường trong `equals()`, đặc biệt cẩn thận với `long`, `float` và `double`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-code-implementation-005">Trường null và Objects.hashCode()</a>

<details>
<summary>Xem chi tiết</summary>

# Trường `null` và `Objects.hashCode()` trong Java

## 1. Vấn đề khi trường tham gia `hashCode()` có thể bằng `null`

Một đối tượng có thể chứa trường tham chiếu chưa được gán giá trị, chẳng hạn `customerId` hoặc `region`. Nếu trường này là một phần bản sắc logic của đối tượng, `equals()` và `hashCode()` phải xử lý nó **nhất quán**, kể cả khi giá trị là `null`.

Gọi trực tiếp phương thức trên tham chiếu rỗng:

```java
String region = null;
int hash = region.hashCode(); // NullPointerException
```

Phần code trên là **fragment minh họa lỗi**, không phải chương trình chạy an toàn. Lỗi xảy ra vì `region` không trỏ đến đối tượng để gọi phương thức. Ta có thể dùng điều kiện `region == null ? 0 : region.hashCode()`, nhưng Java đã cung cấp một công cụ chuyên cho thao tác này: **`java.util.Objects.hashCode(Object)`**.

## 2. `Objects.hashCode(Object)` hoạt động thế nào?

Phương thức tĩnh:

```java
int hash = java.util.Objects.hashCode(value);
```

trả về:

- **`0`** nếu `value == null`;
- **`value.hashCode()`** nếu `value != null`.

Có thể hiểu quy tắc bằng biểu thức tương đương:

```java
static int nullSafeHash(Object value) {
    return value == null ? 0 : value.hashCode();
}
```

Đây là một đoạn khai báo phương thức minh họa; chỉ chứa một phương thức, chưa phải tệp Java hoàn chỉnh có `main`. `Objects.hashCode(value)` bảo vệ **lời gọi hash khi giá trị null**. Nó không tự tính “mã băm tối ưu”, không kiểm tra hay sửa hợp đồng của lớp cung cấp `value`.

Ví dụ:

| Giá trị `value` | `Objects.hashCode(value)` |
| --- | --- |
| `null` | `0` |
| `"A1"` | `2064`, bằng `"A1".hashCode()` |
| `Integer.valueOf(7)` | `7`, bằng `Integer.hashCode()` |
| Một đối tượng khác `null` | Kết quả của chính `value.hashCode()` |

Với chuỗi `"A1"`, `String.hashCode()` theo công thức đa thức: `31 * 'A' + '1' = 31 * 65 + 49 = 2064`.

**Đừng diễn giải `0` thành “chắc chắn null”.** Một đối tượng khác null vẫn được phép có mã băm `0`; các đối tượng không bằng nhau cũng có thể có cùng hash. Hash chỉ là giá trị phục vụ phân vùng/tìm kiếm, không phải bằng chứng tuyệt đối về tính bằng nhau.

## 3. Ví dụ Java 17+ chạy được

Trong ví dụ sau, `CustomerKey` đại diện cho khóa logic gồm hai trường `tenant` và `externalId`; cả hai có thể null. Ta dùng `Objects.equals()` khi so sánh và `Objects.hashCode()` khi tính mã băm từng trường. Hai phương thức tuân theo cùng tập trường nên tránh được việc `equals()` cho là bằng nhau nhưng `hashCode()` tính theo dữ liệu khác.

Tạo tệp `NullHashCodeDemo.java` chứa toàn bộ nội dung:

```java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class NullHashCodeDemo {

    static final class CustomerKey {
        private final String tenant;
        private final String externalId;

        CustomerKey(String tenant, String externalId) {
            this.tenant = tenant;
            this.externalId = externalId;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CustomerKey that)) {
                return false;
            }
            return Objects.equals(tenant, that.tenant)
                    && Objects.equals(externalId, that.externalId);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(tenant);
            result = 31 * result + Objects.hashCode(externalId);
            return result;
        }
    }

    public static void main(String[] args) {
        System.out.println("Objects.hashCode(null): "
                + Objects.hashCode(null));
        System.out.println("Objects.hashCode(A1): "
                + Objects.hashCode("A1"));
        System.out.println("Objects.hash((Object) null): "
                + Objects.hash((Object) null));
        System.out.println("Objects.hash(A1): "
                + Objects.hash("A1"));

        CustomerKey a = new CustomerKey(null, "A1");
        CustomerKey b = new CustomerKey(null, "A1");
        CustomerKey c = new CustomerKey(null, "B2");
        CustomerKey d = new CustomerKey(null, null);

        System.out.println("a equals b: " + a.equals(b));
        System.out.println("a hash: " + a.hashCode());
        System.out.println("b hash: " + b.hashCode());
        System.out.println("a equals c: " + a.equals(c));
        System.out.println("d hash: " + d.hashCode());
        System.out.println("Objects.hash(null, A1): "
                + Objects.hash(null, "A1"));

        Set<CustomerKey> keys = new HashSet<>();
        keys.add(a);
        System.out.println("set contains b: " + keys.contains(b));
        System.out.println("set contains c: " + keys.contains(c));
    }
}
```

Chạy từ thư mục lưu tệp:

```bash
javac NullHashCodeDemo.java
java NullHashCodeDemo
```

Stdout dự kiến chính xác:

```text
Objects.hashCode(null): 0
Objects.hashCode(A1): 2064
Objects.hash((Object) null): 31
Objects.hash(A1): 2095
a equals b: true
a hash: 3025
b hash: 3025
a equals c: false
d hash: 961
Objects.hash(null, A1): 3025
set contains b: true
set contains c: false
```

Giải thích phép tính:

- Với `a`, ta khởi tạo `result = 1`; trường `tenant = null` cho `31 * 1 + 0 = 31`. Tiếp theo `externalId = "A1"` có hash `2064` nên kết quả là `31 * 31 + 2064 = 3025`.
- Với `d`, cả hai trường null: `31 * (31 * 1 + 0) + 0 = 961`. Điều này **không mâu thuẫn** với việc `Objects.hashCode(null) = 0`: `d.hashCode()` là hash **của cả đối tượng**, được kết hợp từ nhiều thành phần, còn `Objects.hashCode(null)` chỉ hash **một giá trị null**.
- `a` và `b` là hai instance khác nhau nhưng cùng `tenant` và `externalId`, vì vậy `equals()` đúng và hai hash đều bằng `3025`.
- `a` và `c` không bằng nhau vì `"A1"` khác `"B2"`. Mã băm khác hay trùng trong trường hợp không bằng nhau đều hợp lệ.
- `HashSet.contains(b)` trả về `true` sau khi thêm `a` vì cặp bằng nhau về logic và cùng hash. `contains(c)` là `false` vì không có khóa tương đương `c` trong tập.

Ví dụ cố ý dùng các trường `final String` để bản sắc logic không thay đổi khi khóa đang ở trong collection. `String` bất biến, do đó `hashCode()` cũng ổn định cho vòng đời đối tượng.

## 4. Đặc biệt chú ý: `Objects.hashCode(value)` khác `Objects.hash(values...)`

Hai phương thức có tên gần giống nhau nhưng chức năng **khác nhau**:

| Phương thức | Cơ chế | Khi nào dùng |
| --- | --- | --- |
| `Objects.hashCode(Object value)` | Null → `0`; còn lại gọi `value.hashCode()` | Lấy hash an toàn cho **một** giá trị nullable |
| `Objects.hash(Object... values)` | Kết hợp các phần tử qua thuật toán tương đương `Arrays.hashCode(Object[])` | Lấy hash kết hợp của **nhiều** trường |

Ví dụ:

```java
Objects.hashCode(null);       // 0
Objects.hash((Object) null);  // 31
Objects.hashCode("A1");       // 2064
Objects.hash("A1");           // 2095
Objects.hash(null, "A1");     // 3025
```

Đoạn trên là **fragment các biểu thức và kết quả**, không phải một chương trình riêng.

Tại sao `Objects.hash("A1") = 2095`? Với mảng varargs một phần tử, thuật toán `Arrays.hashCode` bắt đầu `result = 1` và tính `31 * 1 + 2064 = 2095`. Tương tự, mảng varargs một phần tử null cho `31 * 1 + 0 = 31`.

Phép gọi `Objects.hash((Object) null)` cố ý ép kiểu thành `Object`: nó chỉ định rõ **một phần tử null được truyền vào mảng varargs**, tránh nhầm với việc truyền trực tiếp một tham số có kiểu mảng null. Khi làm việc với varargs, `Objects.hash((Object[]) null)` có nghĩa khác: truyền **mảng tham số null**, và `Arrays.hashCode` của mảng null trả `0`. Không dựa vào các cách gọi mơ hồ để định nghĩa hash.

Quan trọng: chỉ cần tuân thủ **bằng nhau thì hash bằng nhau**. Không có yêu cầu `hashCode()` phải dùng đúng công thức của `Objects.hash()`; cách kết hợp `31 * result + ...` ở ví dụ và `Objects.hash(tenant, externalId)` là tương đương ở đây vì cùng khởi tạo `result = 1` và lần lượt xử lý hai trường.

`Objects.hash(...)` gọn và thường dễ đọc với vài trường, nhưng tạo một mảng varargs (có thể gây cấp phát/tốn tài nguyên tùy tối ưu runtime). Trong đoạn code thực thi cực thường xuyên, phép kết hợp thủ công có thể hữu ích sau khi đo lường. Không nên “tối ưu” khi chưa xác định nhu cầu.

## 5. Quan hệ giữa `equals()` và `hashCode()` khi có null

Hợp đồng cốt lõi của `Object` quy định: nếu `x.equals(y)` trả `true` thì **`x.hashCode() == y.hashCode()` bắt buộc đúng**. Chiều ngược lại không đúng: hai hash trùng nhau không buộc hai đối tượng bằng nhau.

Với trường nullable, hãy duy trì **cùng quy tắc xử lý null và cùng tập trường**:

```java
// Hai đoạn sau thuộc cùng class minh họa có field 'code':
@Override
public boolean equals(Object obj) {
    return obj instanceof Item that
            && Objects.equals(code, that.code);
}

@Override
public int hashCode() {
    return Objects.hashCode(code);
}
```

Đây là **fragment minh họa đặt trong class tên `Item` có trường `code`**, không phải chương trình hoàn chỉnh. Nó đúng về tính nhất quán trường `code` khi lớp là `final` hoặc chính sách bình đẳng theo lớp đã được thiết kế phù hợp. Trong thực tế vẫn cần xem xét lớp cha/lớp con, các thuộc tính khác và tính bất biến.

`Objects.equals(a, b)` hoạt động như sau: nếu hai tham chiếu cùng bằng null thì `true`; nếu chỉ một bên null thì `false`; nếu cả hai có giá trị thì gọi `a.equals(b)`. `Objects.hashCode(a)` không phải “phiên bản hash của hai đối số”, mà chỉ lấy hash một tham chiếu theo quy tắc null-safe.

Một cách cài đặt trực tiếp cũng hợp lệ:

```java
int codeHash = code == null ? 0 : code.hashCode();
```

Tuy nhiên `Objects.hashCode(code)` ngắn và dễ đọc hơn, đặc biệt khi có nhiều trường nullable. Đừng viết `code.hashCode()` trước khi kiểm tra null.

### Điều gì xảy ra nếu một trường không tham gia `equals()`?

Nếu `equals()` chỉ dựa trên `id` nhưng `hashCode()` tính thêm `description`, hai đối tượng có cùng `id` nhưng `description` khác nhau có thể có hash khác nhau, **vi phạm hợp đồng**. Ngược lại, hash bỏ bớt một số trường tham gia `equals()` có thể vẫn đúng về hợp đồng, nhưng gây nhiều va chạm. Tốt nhất nên tính hash trên các trường nhận diện giống `equals()`, trừ khi có lý do thiết kế được chứng minh.

Cả `equals()` và `hashCode()` đều phải ổn định khi những thông tin liên quan không thay đổi. Với đối tượng làm khóa trong `HashMap` hoặc phần tử của `HashSet`, tránh sửa các trường liên quan đến hash sau khi thêm vào collection; nếu giá trị thay đổi, tìm kiếm và xóa phần tử có thể thất bại.

## 6. Các bẫy cần tránh

### Bẫy A — nhầm `Objects.hashCode(null)` với hash toàn bộ đối tượng

`Objects.hashCode(null)` cho `0` nhưng object có hai trường null không nhất thiết cho hash `0`. Công thức kết hợp có seed, hệ số nhân hoặc dữ liệu kiểu khác có thể cho `961`, `31` hoặc giá trị khác. **Chỉ tính nhất quán với `equals()` mới là ràng buộc bắt buộc**, không có con số cố định dành cho đối tượng “có các trường null”.

### Bẫy B — gọi `Objects.hashCode(this)` bên trong chính `hashCode()`

Ví dụ sai:

```java
@Override
public int hashCode() {
    return Objects.hashCode(this); // Sai: đệ quy vô hạn
}
```

Vì `this` không null, `Objects.hashCode(this)` gọi lại `this.hashCode()` liên tục cho đến khi gây `StackOverflowError`. Hãy hash **các trường xác định bản sắc**, không gọi hash của chính đối tượng từ trong hàm đó.

### Bẫy C — mảng không được hash theo nội dung nếu chỉ dùng `Objects.hashCode(array)`

Một mảng Java thông thường kế thừa `Object.equals()/hashCode()`, nên hai mảng khác tham chiếu dù cùng phần tử vẫn không được xem là bằng nhau và có thể có hash khác nhau. Nếu định nghĩa bằng nhau **theo nội dung mảng**, chọn cặp thuật toán phù hợp:

- Mảng một chiều: `Arrays.equals(a, b)` và `Arrays.hashCode(a)` (có overload cho mảng nguyên thủy và object).
- Mảng nhiều tầng chứa mảng: khi cần so sánh nội dung lồng nhau, dùng `Arrays.deepEquals(a, b)` và `Arrays.deepHashCode(a)` cho các mảng `Object[]` thích hợp.

Nếu `equals()` so sánh nội dung mảng bằng `Arrays.equals` còn `hashCode()` dùng `Objects.hashCode(array)` thì hai đối tượng có mảng khác tham chiếu nhưng cùng nội dung có thể bằng nhau mà hash không bằng nhau. Đây là lỗi hợp đồng.

### Bẫy D — dùng hash làm kết quả so sánh bằng nhau

Không viết `return this.hashCode() == other.hashCode();` để cài đặt `equals()`. Hai đối tượng khác nhau có thể va chạm hash, nên phương thức như vậy có nguy cơ coi nhầm chúng bằng nhau. Cần so sánh dữ liệu nghiệp vụ xác định bản sắc.

### Bẫy E — tin rằng `Objects.hashCode` sửa được một lớp có `hashCode()` sai

`Objects.hashCode(nonNullValue)` chỉ ủy quyền sang `nonNullValue.hashCode()`. Nếu lớp đó vi phạm hợp đồng với `equals()` hoặc tạo kết quả không ổn định, `Objects.hashCode` không thể tự khắc phục. Chức năng đảm bảo duy nhất ở đây là **không gọi phương thức trên null**.

## 7. Khi nào không cần viết `hashCode()` thủ công?

Với một value object có vài trường phù hợp để so sánh theo giá trị, Java `record` có thể tự sinh `equals()` và `hashCode()`:

```java
record CustomerKeyRecord(String tenant, String externalId) {}
```

Đây là **đoạn khai báo type minh họa**, có thể đặt trong một tệp Java phù hợp. Hai record cùng kiểu và cùng cặp giá trị thành phần được xem bằng nhau, kể cả khi một hoặc hai thành phần null. Cách này giảm khả năng một phương thức dùng thiếu trường so với phương thức còn lại.

Tuy vậy, record **không tự biến dữ liệu mutable thành immutable sâu**. Nếu thành phần là mảng hoặc đối tượng có trạng thái thay đổi, cần xem xét chính sách sao chép, so sánh và hash. Record cũng không thay thế việc quyết định trường nào tạo nên bản sắc của bài toán.

## 8. Checklist kiểm tra nhanh

1. **Xác định trường nào tham gia bản sắc:** `equals()` so sánh những trường nào và `hashCode()` có sử dụng cùng thông tin tương ứng không?
2. **Kiểm tra null cả hai phía:** hai đối tượng cùng null ở một trường vẫn nên được xử lý an toàn và theo đúng quy tắc bằng nhau của trường đó; một bên null, một bên khác null cần phân biệt.
3. **Dùng đúng API:** `Objects.hashCode(field)` để hash một field nullable; `Objects.hash(field1, field2, ...)` để kết hợp nhiều trường. Không mặc định hai phương thức cho cùng kết quả nếu chỉ có một đối số.
4. **Xác nhận hợp đồng:** với mọi cặp `x, y` có `x.equals(y)` đúng, cần `x.hashCode() == y.hashCode()`. Hash giống nhau ở cặp không bằng nhau được phép.
5. **Tránh trạng thái thay đổi khi làm khóa:** ưu tiên đối tượng bất biến; nếu có trường mảng/nested object, kiểm tra định nghĩa so sánh và khả năng thay đổi nội dung.
6. **Thử trong collection:** thêm một key vào `HashSet`, tìm bằng một instance khác cùng dữ liệu và thử cả trường null/không null. Đây là bài kiểm chứng bổ sung, không thay thế việc kiểm tra hợp đồng.

Một kiểm thử nhỏ về cặp tương đương có thể viết như sau (fragment đặt trong test khi đã có `x` và `y`):

```java
if (x.equals(y) && x.hashCode() != y.hashCode()) {
    throw new AssertionError("equals/hashCode contract violated");
}
```

Thử thêm bộ dữ liệu null và không null. Một số test hữu hạn không đủ chứng minh mọi trường hợp; cần xem lại logic chọn trường và tính ổn định.

## 9. Tóm tắt kiến thức

- `Objects.hashCode(value)` trả `0` nếu `value` null, còn lại trả `value.hashCode()`. Nó giúp viết `hashCode()` không gặp `NullPointerException` với trường nullable.
- `Objects.hash(values...)` kết hợp nhiều giá trị theo thuật toán của `Arrays.hashCode(Object[])`; kể cả khi chỉ truyền một giá trị, kết quả **thường không trùng** `Objects.hashCode(value)`.
- Khi override `hashCode()`, dùng **cùng bản sắc logic** với `equals()`, luôn bảo đảm `x.equals(y) => x.hashCode() == y.hashCode()` và hạn chế dùng dữ liệu có thể thay đổi làm khóa.
- `null` cho mã băm `0` ở cấp **một giá trị** không có nghĩa cả đối tượng chứa nhiều trường null phải hash ra `0`. Các va chạm mã băm là hợp lệ và khác với quan hệ bằng nhau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-code-implementation-006">Objects.hash() cho nhiều trường và khác biệt với hashCode() của một trường</a>

<details>
<summary>Xem chi tiết</summary>

# `Objects.hash()` cho nhiều trường và khác biệt với `hashCode()` của một trường

## 1. Ba lời gọi dễ nhầm lẫn

Trong Java, `java.util.Objects` cung cấp hai phương thức tiện ích khác nhau:

```java
public static int Objects.hash(Object... values);
public static int Objects.hashCode(Object o);
```

Các dòng trên chỉ mô tả **chữ ký**, không phải mã nguồn có thể đặt nguyên vào một lớp; tên `Objects.` được viết để chỉ rõ lớp sở hữu phương thức.

Phân biệt chúng với **phương thức thể hiện** `public int hashCode()` kế thừa từ `java.lang.Object`:

| Cách gọi | Ý nghĩa | Khi dữ liệu `null` |
| --- | --- | --- |
| `x.hashCode()` | Dùng thuật toán băm do đối tượng `x` cung cấp | Nếu **receiver `x` là `null`**, ném `NullPointerException` |
| `Objects.hashCode(x)` | Trả `x.hashCode()` nếu `x != null`; trả `0` nếu `x == null` | An toàn với `null` |
| `Objects.hash(x)` | Tính hash tổng hợp của **danh sách đối số** gồm một phần tử `x` | Nếu truyền một `null` như phần tử, kết quả thường khác `Objects.hashCode(null)` |
| `Objects.hash(a, b, c)` | Tạo hash tổng hợp theo thứ tự từ nhiều giá trị | Có thể chứa những phần tử `null` |

**Quy tắc cần nhớ:** `Objects.hash(x)` không có nghĩa là `Objects.hashCode(x)` và cũng không bảo đảm bằng `x.hashCode()`, kể cả khi chỉ có một tham số. `Objects.hash(...)` xử lý một dãy phần tử thông qua `Arrays.hashCode(Object[])`; nó áp dụng thêm bước khởi tạo và kết hợp.

## 2. Vì sao `Objects.hash(x)` khác `x.hashCode()`?

`Objects.hash(Object... values)` nhận tham số biến độ dài (**varargs**). Khi truyền các đối số riêng lẻ, Java đóng gói chúng vào một mảng `Object[]`, sau đó `Objects.hash` tính như `Arrays.hashCode(values)`.

Với dãy phần tử `values` khác `null`, thuật toán `Arrays.hashCode(Object[])` tương đương:

```java
int result = 1;
for (Object element : values) {
    result = 31 * result + (element == null ? 0 : element.hashCode());
}
return result;
```

Đây là **đoạn minh hoạ thuật toán**, không phải chương trình Java độc lập. Các phép tính là số nguyên `int`; nếu xảy ra tràn số thì Java thực hiện theo quy tắc số nguyên 32-bit, điều này được phép trong hash.

Vì `result` bắt đầu bằng `1`, với **một phần tử `x`** được truyền dưới dạng đối số đơn:

```text
Objects.hash(x) = 31 + Objects.hashCode(x)  (tính bằng số nguyên int 32-bit)
```

Đặc biệt, nếu `x` không phải `null`, `Objects.hashCode(x) == x.hashCode()`; còn `Objects.hash(x)` thêm bước kết hợp `31`. Đây là lý do trực tiếp cho việc kết quả khác nhau. Công thức này giả định một phần tử thực sự được truyền vào mảng varargs; có ngoại lệ cú pháp khi đối số là một mảng kiểu tham chiếu hoặc một giá trị `null` không ép kiểu, được giải thích ở phần lưu ý.

Ví dụ với chuỗi `"abc"`:

- `"abc".hashCode()` trả về `96354`.
- `Objects.hashCode("abc")` cũng trả về `96354`.
- `Objects.hash("abc")` trả về `31 + 96354 = 96385`.

Với một phần tử `null` được ghi rõ `(Object) null`, `Objects.hash((Object) null)` trả về `31`, trong khi `Objects.hashCode(null)` trả về `0`; biểu thức `((String) null).hashCode()` lại ném `NullPointerException`.

## 3. `Objects.hash(a, b, ...)` kết hợp nhiều trường

Khi `equals()` của một lớp xác định sự bằng nhau bằng **nhiều trường**, `hashCode()` cần tính toán nhất quán từ những trường đó. Ví dụ một khoá sách được nhận diện bằng `code` và `edition`:

```java
@Override
public int hashCode() {
    return Objects.hash(code, edition);
}
```

Đoạn trên là **phương thức nằm trong một lớp có các trường `code` và `edition`**, chưa phải chương trình độc lập. `Objects.hash(code, edition)` tạo ra một số `int` phụ thuộc cả hai giá trị và thứ tự của chúng. Trình biên dịch đóng hộp (`boxing`) trường nguyên thủy `edition` thành `Integer` khi cần đưa vào mảng varargs.

Ví dụ công thức với `code = "ab"`, `edition = 7`:

```text
"ab".hashCode() = 3105
result ban đầu = 1
result sau code = 31 * 1 + 3105 = 3136
result sau edition = 31 * 3136 + 7 = 97223
Objects.hash("ab", 7) = 97223
```

Không thể chỉ lấy `code.hashCode()` nếu `equals()` còn xét `edition`: hai đối tượng cùng `code` nhưng khác `edition` có thể cho chung hash một cách hợp lệ, tuy nhiên bỏ qua một trường thường làm phân phối hash kém hơn. Điều quan trọng bắt buộc là: **nếu hai đối tượng bằng nhau theo `equals()`, hash của chúng phải bằng nhau**. Chiều đảo không đúng: trùng hash không chứng minh hai đối tượng bằng nhau.

## 4. Ví dụ Java 17+ hoàn chỉnh, có thể chạy

Lưu mã sau vào tệp `ObjectsHashDemo.java`:

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ObjectsHashDemo {
    static final class BookKey {
        private final String code;
        private final int edition;

        BookKey(String code, int edition) {
            this.code = Objects.requireNonNull(code);
            this.edition = edition;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof BookKey other)) return false;
            return edition == other.edition && code.equals(other.code);
        }

        @Override
        public int hashCode() {
            return Objects.hash(code, edition);
        }
    }

    public static void main(String[] args) {
        String value = "abc";
        System.out.println("instance: " + value.hashCode());
        System.out.println("Objects.hashCode: " + Objects.hashCode(value));
        System.out.println("Objects.hash(one): " + Objects.hash(value));
        System.out.println("Objects.hashCode(null): " + Objects.hashCode(null));
        System.out.println("Objects.hash((Object) null): " + Objects.hash((Object) null));
        System.out.println("Objects.hash(ab, 7): " + Objects.hash("ab", 7));

        BookKey first = new BookKey("ab", 7);
        BookKey equal = new BookKey("ab", 7);
        BookKey different = new BookKey("ab", 8);

        Map<BookKey, String> books = new HashMap<>();
        books.put(first, "available");
        System.out.println("equal keys: " + first.equals(equal));
        System.out.println("equal hashes: " + (first.hashCode() == equal.hashCode()));
        System.out.println("lookup equal: " + books.get(equal));
        System.out.println("lookup different: " + books.get(different));
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac ObjectsHashDemo.java
java ObjectsHashDemo
```

Kết quả:

```text
instance: 96354
Objects.hashCode: 96354
Objects.hash(one): 96385
Objects.hashCode(null): 0
Objects.hash((Object) null): 31
Objects.hash(ab, 7): 97223
equal keys: true
equal hashes: true
lookup equal: available
lookup different: null
```

`BookKey` có `equals()` xét cả `code` và `edition`; `hashCode()` cũng nhận cả hai trường vào `Objects.hash`. Vì vậy hai khoá tương đương có thể tra cứu cùng một mục trong `HashMap`. Tạo khoá mới khác `edition` không tra cứu được mục đã lưu.

Kết quả `lookup different: null` nghĩa là `Map.get` không tìm thấy ánh xạ cho khoá đó trong ví dụ, không phải vì kết quả hash nhất định khác nhau. Dù có va chạm, `HashMap` vẫn kiểm tra tính bằng nhau của khoá để phân biệt.

## 5. So với tự viết công thức `hashCode()`

`Objects.hash(code, edition)` đơn giản và giúp tránh quên trường. Với hai trường ví dụ, có thể viết thủ công thuật toán tương đương:

```java
@Override
public int hashCode() {
    int result = 1;
    result = 31 * result + code.hashCode();
    result = 31 * result + Integer.hashCode(edition);
    return result;
}
```

Đây là **phương thức thay thế** trong lớp `BookKey`, không dán thêm bên cạnh phương thức `hashCode()` cũ. Với các trường `code` khác `null` và `edition` kiểu `int` như đã khai báo, công thức thủ công này cho đúng kết quả của `Objects.hash(code, edition)`.

So sánh ưu nhược điểm:

| Tiêu chí | `Objects.hash(code, edition)` | Viết công thức thủ công |
| --- | --- | --- |
| Độ ngắn gọn | Ngắn, dễ đọc khi nhiều trường | Dài hơn nhưng thấy rõ thuật toán |
| Xử lý `null` ở trường tham chiếu | Các phần tử `null` đóng góp `0` | Phải tự xử lý nếu trường có thể `null` |
| Chi phí bổ sung | Varargs tạo mảng; giá trị nguyên thủy có thể cần boxing | Có thể tránh tạo mảng và boxing |
| Hợp đồng `equals`/`hashCode` | Phải tự chọn đúng trường nhất quán với `equals()` | Cũng phải tự chọn đúng trường nhất quán với `equals()` |

Trong nghiệp vụ thông thường, ưu tiên mã rõ ràng và đúng hợp đồng. Ở đoạn mã được gọi liên tục với yêu cầu hiệu năng cao, cân nhắc đo đạc rồi mới thay `Objects.hash` bằng cách tính thủ công; không khẳng định mọi trường hợp `Objects.hash` đều là nút thắt.

## 6. Những bẫy quan trọng khi dùng varargs, `null` và mảng

### `Objects.hash(null)` có thể mang ý nghĩa bất ngờ

Đối số `null` trần có thể được trình biên dịch diễn giải là **mảng varargs `Object[]` bị `null`**, thay vì một phần tử `null`, kèm cảnh báo biên dịch. Khi đó `Arrays.hashCode((Object[]) null)` trả về `0`. Để biểu đạt rõ một đối số là giá trị `null`, viết `Objects.hash((Object) null)` và nhận `31`. Nếu mục tiêu chỉ là lấy hash của **một** tham chiếu có thể `null`, viết `Objects.hashCode(x)` thường phù hợp hơn.

### Mảng không mặc nhiên được băm theo nội dung

Với một biến `Object[] elements`, lời gọi `Objects.hash(elements)` có thể dùng chính mảng đó như **toàn bộ mảng varargs**, tức băm từng phần tử, không phải tạo một mảng một phần tử chứa `elements`. Trái lại, `Objects.hash((Object) elements)` băm một phần tử có kiểu mảng; phương thức `hashCode()` mặc định của mảng dựa trên danh tính mảng.

Nếu `equals()` so sánh nội dung mảng, hãy ghép đúng cặp `Arrays.equals(a, b)` với `Arrays.hashCode(a)` (hoặc `Arrays.deepEquals`/`Arrays.deepHashCode` phù hợp cho cấu trúc mảng lồng nhau), thay vì tự động dùng `Objects.hash(array)` mà chưa xét cách varargs được truyền.

### `Objects.hash()` không thay thế hợp đồng `equals()`

Ghi đè `equals()` mà không ghi đè `hashCode()` nhất quán có thể làm `HashMap.get` và `HashSet.contains` tìm sai. Ngay cả khi dùng `Objects.hash`, vẫn phải chọn các trường quyết định tính bằng nhau, đồng thời tránh thay đổi chúng sau khi đã dùng đối tượng làm khoá trong cấu trúc băm.

`Objects.hash()` cũng không phải hàm băm mật mã, không bảo đảm duy nhất, không dùng làm mã định danh ổn định giữa các hệ thống. Kết quả `int` có thể âm và có thể va chạm.

## 7. Cách chọn nhanh

- **Một trường tham chiếu chắc chắn không `null`:** `field.hashCode()` là cách trực tiếp, không có bước kết hợp varargs.
- **Một trường có thể `null`:** `Objects.hashCode(field)` trả `0` khi `null` và hash thông thường khi khác `null`.
- **Nhiều trường tham gia `equals()`:** `Objects.hash(fieldA, fieldB, ...)` giúp tạo hàm băm tổng hợp rõ ràng, miễn là nhất quán với `equals()`.
- **Tối ưu mã nóng:** Có thể viết phép kết hợp `31 * result + ...` hoặc dùng `Integer.hashCode`, `Long.hashCode` và các phương thức chuyên biệt sau khi đo hiệu năng.
- **Trường là mảng:** Chọn `Arrays.hashCode`/`Arrays.deepHashCode` đúng với phép so sánh nội dung đã chọn.

## 8. Tự kiểm tra

1. Vì sao `Objects.hash("abc")` là `96385`, trong khi `"abc".hashCode()` là `96354`? — Vì thuật toán của mảng varargs bắt đầu với `result = 1`, sau đó cộng thêm `31 * 1` trước hash phần tử.
2. `Objects.hashCode(null)` và `Objects.hash((Object) null)` có bằng nhau? — Không: lần lượt là `0` và `31`.
3. Nếu `equals()` xét cả `code` và `edition`, hash chỉ dựa trên `code` có tự động vi phạm hợp đồng? — Không tự động: các khoá bằng nhau vẫn cùng `code`. Nhưng bỏ `edition` có thể tăng va chạm; dùng cả hai thường phân phối tốt hơn.
4. Nếu hai khoá trùng kết quả `Objects.hash`, chúng có bắt buộc bằng nhau? — Không. Cấu trúc băm vẫn phải xét `equals()`.

**Ghi nhớ:** `x.hashCode()` lấy hash do một đối tượng cung cấp; `Objects.hashCode(x)` là phiên bản an toàn với tham chiếu `null`; còn `Objects.hash(...)` là phép **kết hợp các phần tử trong dãy đối số**. Chúng khác nhau cả về mục đích lẫn giá trị trả về khi chỉ có một phần tử.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-code-implementation-007">Dùng Arrays.hashCode() hoặc deepHashCode() theo cách so sánh mảng</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng Arrays.hashCode() hoặc deepHashCode() theo cách so sánh mảng

## 1. Vì sao mảng cần cách tính hashCode riêng?

Trong Java, mảng là đối tượng, nhưng **mảng không override Object.equals(Object) hay Object.hashCode() để so nội dung**. Vì vậy hai mảng được tạo riêng dù chứa các phần tử giống hệt nhau vẫn là hai đối tượng khác nhau theo equals mặc định. Nếu ta thiết kế một lớp giá trị chứa mảng và muốn hai instance bằng nhau **dựa trên nội dung mảng**, phải tự chọn **phép so sánh nội dung** và **hàm hash tương ứng**.

Hợp đồng quan trọng nhất:

> Nếu a.equals(b) trả về true thì a.hashCode() bắt buộc bằng b.hashCode().

Điều ngược lại **không bắt buộc**: hai giá trị khác nhau có thể trùng hash do va chạm băm. Không được dùng **hashCode() bằng nhau** làm bằng chứng để kết luận hai mảng bằng nhau.

Đối với mảng, cần thống nhất **độ sâu của phép so sánh**:

- Mảng một chiều như **int[]**, **byte[]**, **char[]**: dùng **Arrays.equals(...)** để so từng giá trị và **Arrays.hashCode(...)** với cùng loại mảng để tính hash theo nội dung.
- Mảng đối tượng một chiều như **String[]**: **Arrays.equals(Object[], Object[])** dùng **Objects.equals(...)** cho từng phần tử; **Arrays.hashCode(Object[])** gọi hashCode của các phần tử tương ứng.
- Mảng lồng nhau như **int[][]**, **String[][]** hoặc **Object[]** chứa các mảng: muốn so **sâu theo nội dung của các mảng con**, dùng **Arrays.deepEquals(Object[], Object[])** và **Arrays.deepHashCode(Object[])**.

Không nên chọn hàm hash theo số dấu ngoặc vuông một cách máy móc: **String[]** là mảng đối tượng một chiều và so sánh nông thường đã đủ vì phần tử String tự so nội dung; còn **Object[]** một chiều nhưng chứa **int[]** vẫn cần so sánh sâu nếu quy tắc giá trị đòi so nội dung của các mảng con.

## 2. Bảng đối chiếu phép so sánh và phép băm

| Kiểu dữ liệu và ngữ nghĩa mong muốn | So sánh giá trị | Tính hash tương ứng |
| --- | --- | --- |
| **int[]** / **long[]** / **byte[]** / **char[]** / các mảng primitive một chiều | **Arrays.equals(a, b)** | **Arrays.hashCode(a)** |
| **String[]** hoặc **Integer[]**, so từng phần tử bằng equals | **Arrays.equals(a, b)** | **Arrays.hashCode(a)** |
| **Object[]** có phần tử là các đối tượng thường, so trực tiếp mỗi phần tử | **Arrays.equals(a, b)** | **Arrays.hashCode(a)** |
| **int[][]**, **long[][]**, **String[][]**, so sâu các hàng | **Arrays.deepEquals(a, b)** | **Arrays.deepHashCode(a)** |
| **Object[]** chứa mảng ở một hay nhiều mức và muốn so sâu | **Arrays.deepEquals(a, b)** | **Arrays.deepHashCode(a)** |
| **Object[]** có mảng con nhưng nghiệp vụ **chỉ so tham chiếu mảng con** | **Arrays.equals(a, b)** | **Arrays.hashCode(a)** |

**“Nông” (shallow)** trong bảng nghĩa là hàm xét lần lượt **các phần tử của mảng ngoài**: nếu một phần tử lại là mảng, equals/hashCode mặc định của chính mảng con vẫn theo đối tượng, tức thường là theo tham chiếu. **“Sâu” (deep)** nghĩa là khi phần tử cũng là mảng, Java đi sâu vào các mảng con và xử lý phần tử lồng bên trong.

Lưu ý về chữ **deep**: đây là so sánh sâu **các lớp mảng**, không tự động duyệt mọi trường của mọi object tùy ý. Khi gặp phần tử là đối tượng thông thường, **Arrays.deepEquals** vẫn dựa trên phương thức **equals** của đối tượng ấy, và **Arrays.deepHashCode** dựa trên **hashCode** của nó. Nếu lớp phần tử tự cài sai hợp đồng equals/hashCode, phép deep không thể tự sửa lỗi đó.

## 3. Ví dụ 1: mảng primitive một chiều dùng Arrays.equals và Arrays.hashCode

Tạo tệp **PrimitiveArrayHashDemo.java**:

~~~java
import java.util.Arrays;

public class PrimitiveArrayHashDemo {
    public static void main(String[] args) {
        int[] first = {1, 2, 3};
        int[] second = {1, 2, 3};
        int[] third = {1, 2, 4};

        System.out.println(first == second);
        System.out.println(first.equals(second));
        System.out.println(Arrays.equals(first, second));
        System.out.println(Arrays.hashCode(first));
        System.out.println(Arrays.hashCode(second));
        System.out.println(Arrays.equals(first, third));
        System.out.println(Arrays.hashCode(first) == Arrays.hashCode(second));
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac PrimitiveArrayHashDemo.java
java PrimitiveArrayHashDemo
~~~

Kết quả:

~~~text
false
false
true
30817
30817
false
true
~~~

**Giải thích:** **first** và **second** là hai instance mảng khác nhau, nên cả **==** lẫn **first.equals(second)** đều false. Nhưng **Arrays.equals(first, second)** duyệt từng phần tử int và trả true. **Arrays.hashCode(int[])** cũng dựa trên cùng dãy giá trị nên cho kết quả giống nhau.

Đối với int[], Java định nghĩa phép tích lũy hash dạng:

~~~text
result = 1
với từng phần tử v:
    result = 31 * result + v
~~~

Phép nhân/cộng thực hiện theo số nguyên **int** của Java, nên tràn số là hợp lệ và có thể xảy ra. Với **{1, 2, 3}**:

~~~text
31 * 1   + 1 = 32
31 * 32  + 2 = 994
31 * 994 + 3 = 30817
~~~

Công thức minh họa cho **int[]**; các overload primitive khác dùng cách đưa từng phần tử về số băm phù hợp với kiểu phần tử. **Không cần tự viết vòng lặp tính hash**, vì Arrays.hashCode đã cung cấp cơ chế nhất quán với Arrays.equals.

### Mảng rỗng, null và thứ tự

- Hai **int[] rỗng** bằng nhau theo **Arrays.equals** và có cùng Arrays.hashCode.
- **Arrays.equals((int[]) null, (int[]) null)** trả true; một bên null, một bên không null thì false.
- **Arrays.hashCode((int[]) null)** trả 0.
- Thứ tự phần tử là một phần của giá trị: **{1, 2}** khác **{2, 1}** theo Arrays.equals. Hai mảng khác thứ tự có thể vô tình cùng hash, nhưng vẫn không bằng nhau.
- Nếu nghiệp vụ coi mảng là **tập hợp không có thứ tự**, Arrays.equals/hashCode theo thứ tự **không phù hợp** với định nghĩa đó. Cần một mô hình giá trị hoặc khóa chuẩn hóa khác, kèm equals/hashCode nhất quán.

## 4. Ví dụ 2: vì sao mảng lồng nhau cần deepEquals và deepHashCode?

Với **int[][]**, mỗi phần tử của mảng ngoài là một **int[]**. Nếu dùng **Arrays.equals(int[][], int[][])**, Java chọn overload **Object[]**; ở cấp mảng ngoài, các phần tử là mảng con. Hai hàng **new int[]{1, 2}** được tạo riêng không bằng nhau theo Object.equals mặc định.

Tạo file **NestedArrayHashDemo.java**:

~~~java
import java.util.Arrays;

public class NestedArrayHashDemo {
    public static void main(String[] args) {
        int[][] first = {
            {1, 2},
            {3, 4}
        };
        int[][] second = {
            {1, 2},
            {3, 4}
        };
        int[][] changed = {
            {1, 2},
            {3, 9}
        };

        System.out.println("shallow equals: " +
            Arrays.equals(first, second));
        System.out.println("deep equals: " +
            Arrays.deepEquals(first, second));
        System.out.println("deep hash equal: " +
            (Arrays.deepHashCode(first) == Arrays.deepHashCode(second)));
        System.out.println("changed deep equals: " +
            Arrays.deepEquals(first, changed));
        System.out.println("matrix: " +
            Arrays.deepToString(first));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac NestedArrayHashDemo.java
java NestedArrayHashDemo
~~~

Kết quả:

~~~text
shallow equals: false
deep equals: true
deep hash equal: true
changed deep equals: false
matrix: [[1, 2], [3, 4]]
~~~

**Cơ chế hoạt động:**

1. **Arrays.equals(first, second)** xét các phần tử của mảng ngoài. Các phần tử này là mảng con khác tham chiếu, nên kết quả false.
2. **Arrays.deepEquals(first, second)** nhận **Object[]** ở cấp ngoài (int[][] là Object[]) và nhận biết mỗi phần tử con là **int[]**, rồi gọi cách so primitive thích hợp.
3. **Arrays.deepHashCode(first)** đi sâu qua các mảng con để băm từng giá trị int. Nếu hai cấu trúc bằng nhau theo deepEquals và các object phần tử tuân thủ hợp đồng, chúng có deep hash bằng nhau.
4. **Arrays.deepToString** rất tiện để in cấu trúc lồng nhau phục vụ kiểm tra dữ liệu. Đây là hàm biểu diễn chuỗi, **không thay thế** equals/hashCode.

Không có overload **Arrays.deepHashCode(int[])**: phương thức deep nhận **Object[]**, trong khi **int[] không phải Object[]**. Đối với một **int[]** độc lập, dùng **Arrays.hashCode(int[])**. Đối với **int[][]**, có thể dùng **Arrays.deepHashCode(int[][])** vì **int[][] là mảng chứa các phần tử int[]**, nên tương thích với tham số Object[].

## 5. Ví dụ 3: Object[] một chiều nhưng chứa các mảng khác nhau

Chiều khai báo của mảng ngoài không phải toàn bộ câu chuyện. Một **Object[]** có thể chứa **int[]**, **String[]**, mảng nhiều chiều và đối tượng thường cùng lúc.

Tạo file **MixedNestedArrayDemo.java**:

~~~java
import java.util.Arrays;

public class MixedNestedArrayDemo {
    public static void main(String[] args) {
        Object[] left = {
            "header",
            new int[]{5, 8},
            new String[]{"A", "B"}
        };

        Object[] right = {
            "header",
            new int[]{5, 8},
            new String[]{"A", "B"}
        };

        System.out.println("shallow: " +
            Arrays.equals(left, right));
        System.out.println("deep: " +
            Arrays.deepEquals(left, right));
        System.out.println("deep hash equal: " +
            (Arrays.deepHashCode(left) == Arrays.deepHashCode(right)));
        System.out.println("display: " +
            Arrays.deepToString(left));
    }
}
~~~

Kết quả khi chạy **javac MixedNestedArrayDemo.java** rồi **java MixedNestedArrayDemo**:

~~~text
shallow: false
deep: true
deep hash equal: true
display: [header, [5, 8], [A, B]]
~~~

Vì có các phần tử **int[]** và **String[]** được tạo độc lập, so sánh nông trả false. So sánh sâu biết cách xử lý cả mảng primitive và mảng tham chiếu lồng nhau.

**Arrays.deepEquals/deepHashCode vẫn quan tâm kiểu và cấu trúc mảng.** Chẳng hạn **new int[]{1}** không tự động được coi là bằng **new Integer[]{1}**; việc “cùng con số” không đủ để hai mảng khác kiểu có cùng ngữ nghĩa giá trị. Nếu nghiệp vụ muốn coi chúng tương đương, cần chuyển chúng về một biểu diễn giá trị chung **trước** khi so sánh/băm.

## 6. Cài equals và hashCode cho lớp có trường mảng một chiều

Đây là trường hợp thường gặp nhất: một đối tượng chứa **int[] samples**, và nội dung dãy số cấu thành giá trị đối tượng. Nếu chỉ dùng **Objects.equals(samples, other.samples)**, Java so tham chiếu mảng; nếu chỉ dùng **Objects.hash(samples)**, hash có thể phụ thuộc vào danh tính mảng chứ không phải các số bên trong.

Tạo tệp **IntVectorDemo.java** với một kiểu khóa bất biến về mặt quan sát từ bên ngoài:

~~~java
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class IntVectorDemo {
    static final class IntVector {
        private final int[] values;

        IntVector(int[] values) {
            this.values = Arrays.copyOf(
                Objects.requireNonNull(values, "values"),
                values.length
            );
        }

        public int[] values() {
            return values.clone();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof IntVector other)) {
                return false;
            }
            return Arrays.equals(values, other.values);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(values);
        }

        @Override
        public String toString() {
            return Arrays.toString(values);
        }
    }

    public static void main(String[] args) {
        int[] input = {2, 4};
        IntVector one = new IntVector(input);
        IntVector two = new IntVector(new int[]{2, 4});

        System.out.println("equal: " + one.equals(two));
        System.out.println("hash equal: " +
            (one.hashCode() == two.hashCode()));

        Set<IntVector> unique = new HashSet<>();
        System.out.println("add one: " + unique.add(one));
        System.out.println("add two: " + unique.add(two));
        System.out.println("set size: " + unique.size());

        input[0] = 99;
        System.out.println("after editing input: " + one);
        int[] exposedCopy = one.values();
        exposedCopy[1] = 77;
        System.out.println("after editing getter copy: " + one);
    }
}
~~~

Chạy:

~~~bash
javac IntVectorDemo.java
java IntVectorDemo
~~~

Kết quả:

~~~text
equal: true
hash equal: true
add one: true
add two: false
set size: 1
after editing input: [2, 4]
after editing getter copy: [2, 4]
~~~

**Tại sao cần sao chép mảng?** Nếu giữ nguyên tham chiếu đầu vào hoặc trả thẳng mảng nội bộ qua getter, mã ngoài có thể sửa phần tử sau khi object được đặt làm khóa HashMap/HashSet. Khi ấy **hashCode thay đổi theo nội dung** và việc tìm kiếm có thể không còn đúng. Lớp trên copy trong constructor và copy khi trả dữ liệu, nên các thành phần tham gia hashCode không thể bị thay đổi bởi mã bên ngoài.

**final field không đồng nghĩa với mảng bất biến.** Khai báo **private final int[] values** chỉ ngăn gán lại tham chiếu values bên trong lớp, không ngăn **values[0] = ...**. Nếu chính class vẫn cung cấp phương thức sửa mảng nội bộ, nó vẫn là mutable key; muốn khóa ổn định thì phải đảm bảo không có đường thay đổi nội dung.

**Nếu có nhiều trường giá trị:** tính cả những trường được equals so sánh. Ví dụ một lớp có **String id** và **int[] samples** có thể cài:

~~~java
// Đoạn trích trong equals(Object), sau khi đã xác nhận đúng kiểu:
return Objects.equals(id, other.id)
    && Arrays.equals(samples, other.samples);

// Đoạn trích trong hashCode():
return 31 * Objects.hashCode(id) + Arrays.hashCode(samples);
~~~

Đây là **đoạn mã minh họa trong một lớp**, không phải chương trình độc lập. Cách kết hợp bằng hệ số 31 là một lựa chọn thông dụng, không phải duy nhất. Điều bắt buộc là **mọi cặp bằng nhau theo equals đều có cùng hashCode**, chứ không phải có một số công thức băm cụ thể.

## 7. Cài equals và hashCode cho lớp chứa mảng lồng nhau

Với **int[][] matrix** được coi là một giá trị cấu trúc, cần dùng **Arrays.deepEquals** và **Arrays.deepHashCode**. Muốn object ổn định khi làm khóa, cũng cần **sao chép từng hàng**: chỉ clone mảng ngoài chưa đủ vì mảng con vẫn được chia sẻ.

Tạo file **MatrixKeyDemo.java**:

~~~java
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class MatrixKeyDemo {
    static final class MatrixKey {
        private final int[][] matrix;

        MatrixKey(int[][] source) {
            Objects.requireNonNull(source, "source");
            this.matrix = copyMatrix(source);
        }

        private static int[][] copyMatrix(int[][] source) {
            int[][] copy = new int[source.length][];
            for (int i = 0; i < source.length; i++) {
                copy[i] = source[i] == null
                    ? null
                    : source[i].clone();
            }
            return copy;
        }

        public int[][] getMatrix() {
            return copyMatrix(matrix);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof MatrixKey other)) {
                return false;
            }
            return Arrays.deepEquals(matrix, other.matrix);
        }

        @Override
        public int hashCode() {
            return Arrays.deepHashCode(matrix);
        }
    }

    public static void main(String[] args) {
        int[][] input = {
            {1, 2},
            null,
            {3}
        };

        MatrixKey one = new MatrixKey(input);
        MatrixKey two = new MatrixKey(new int[][]{
            {1, 2},
            null,
            {3}
        });

        System.out.println("equal: " + one.equals(two));
        System.out.println("hash equal: " +
            (one.hashCode() == two.hashCode()));

        Set<MatrixKey> keys = new HashSet<>();
        keys.add(one);
        keys.add(two);
        System.out.println("set size: " + keys.size());

        input[0][0] = 999;
        int[][] copy = one.getMatrix();
        copy[2][0] = 888;

        System.out.println("stable: " + one.equals(two));
    }
}
~~~

Lệnh chạy:

~~~bash
javac MatrixKeyDemo.java
java MatrixKeyDemo
~~~

Kết quả:

~~~text
equal: true
hash equal: true
set size: 1
stable: true
~~~

Trong ví dụ, cả mảng ngoài lẫn từng **int[]** bên trong đều được copy. Hàm copy chấp nhận các hàng null để phù hợp với dữ liệu mà **deepEquals/deepHashCode** hỗ trợ. Sau khi khởi tạo, sửa mảng input hoặc bản sao từ getter không ảnh hưởng tới giá trị của MatrixKey.

Nếu ma trận chứa các đối tượng **mutable** ở lá, việc clone mảng ngoài và mảng con vẫn chỉ sao chép **tham chiếu đến object lá**. Khi yêu cầu bất biến sâu, phải có chính sách copy hoặc kiểu immutable phù hợp cho các object lá; **Arrays.deepHashCode không làm cho nội dung thành bất biến**.

## 8. Cạm bẫy với Objects.hash, Objects.hashCode và record

### 8.1. Objects.hash không phải hàm thay thế cho Arrays.hashCode của mọi mảng

**Objects.hash(Object... values)** là phương thức varargs, về bản chất băm một **Object[] tham số**. Với **int[]**, nó coi mảng primitive là **một object phần tử**, nên hash có thể dựa trên **hashCode danh tính của mảng**. Vì vậy:

~~~java
int[] first = {1, 2};
int[] second = {1, 2};

// Đoạn trích minh họa:
boolean contentEqual = java.util.Arrays.equals(first, second);  // true
boolean correctHashEqual =
    java.util.Arrays.hashCode(first) ==
    java.util.Arrays.hashCode(second);                           // true
~~~

Đây là đoạn trích, không phải file độc lập. **Không nên thay Arrays.hashCode(first) bằng Objects.hash(first)** rồi kỳ vọng nhận được cùng quy tắc băm nội dung primitive.

Với **Object[]**, lời gọi varargs như **Objects.hash(objectArray)** có thể dùng chính các phần tử của Object[] làm danh sách tham số, nên hành vi còn tùy kiểu mảng và cách gọi. Dùng overload **Arrays.hashCode(Object[])** hoặc **Arrays.deepHashCode(Object[])** tường minh để tránh hiểu nhầm và giữ đúng cặp với phép equals.

**Objects.hashCode(array)** (số ít, nhận một Object) chỉ gọi **array.hashCode()** khi khác null, nên đối với mảng cũng **không tính băm theo nội dung**.

### 8.2. Record có trường mảng không tự so nội dung mảng

Record sinh **equals/hashCode** từ các component, nhưng một component kiểu mảng vẫn dùng ngữ nghĩa **equals/hashCode của mảng** thay vì tự chuyển thành **Arrays.equals** và **Arrays.hashCode**.

Tạo file **RecordArrayDemo.java**:

~~~java
import java.util.Arrays;

public class RecordArrayDemo {
    record RawReading(int[] values) {}

    record ValueReading(int[] values) {
        ValueReading {
            values = values.clone();
        }

        @Override
        public int[] values() {
            return values.clone();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ValueReading other
                && Arrays.equals(values, other.values);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(values);
        }
    }

    public static void main(String[] args) {
        RawReading raw1 = new RawReading(new int[]{1, 2});
        RawReading raw2 = new RawReading(new int[]{1, 2});
        ValueReading good1 = new ValueReading(new int[]{1, 2});
        ValueReading good2 = new ValueReading(new int[]{1, 2});

        System.out.println("generated equals: " + raw1.equals(raw2));
        System.out.println("custom equals: " + good1.equals(good2));
        System.out.println("custom hash equal: " +
            (good1.hashCode() == good2.hashCode()));
    }
}
~~~

Chạy:

~~~bash
javac RecordArrayDemo.java
java RecordArrayDemo
~~~

Kết quả:

~~~text
generated equals: false
custom equals: true
custom hash equal: true
~~~

Trong **ValueReading**, compact constructor sao chép component mảng trước khi lưu, accessor override trả bản sao, equals/hashCode tự dùng cùng nội dung int[]. Cần xem xét tương tự với **record có mảng lồng nhau**: clone mảng ngoài chưa đủ; khi dùng deepEquals/deepHashCode thì cần chính sách bất biến sâu tương ứng.

### 8.3. Các loại số thực và phần tử null

- **Arrays.equals(float[], float[])** và **Arrays.equals(double[], double[])** sử dụng quy tắc tương ứng với **Float.compare** và **Double.compare** cho phần tử. Điều này xử lý NaN và phân biệt **+0.0** với **-0.0** theo quy tắc của các wrapper, thay vì dùng trực tiếp toán tử **==** của số thực.
- **Arrays.hashCode(float[])** và **Arrays.hashCode(double[])** được định nghĩa tương thích với chính phép so sánh mảng đó. Không tự thay equals bằng vòng lặp dùng **==** trong khi giữ Arrays.hashCode mà không hiểu sự khác biệt.
- **Arrays.equals(Object[], Object[])** có thể xử lý phần tử null: hai null ở cùng vị trí là bằng nhau; một null và một giá trị khác null thì không. **Arrays.hashCode(Object[])** và deepHashCode tính hash tương ứng cho vị trí null.
- Đối với mảng top-level null, **Arrays.deepEquals((Object[]) null, (Object[]) null)** trả true và **Arrays.deepHashCode((Object[]) null)** trả 0. Khi biến khai báo không rõ kiểu, nên chỉ định đúng overload hoặc ép kiểu có chủ đích.

## 9. Những giới hạn cần biết của deepEquals và deepHashCode

**Không dùng cho mảng tham chiếu vòng lặp (cyclic arrays).** Một Object[] có thể tự chứa chính nó hoặc tạo chu kỳ qua nhiều mảng. Các API deep không thiết kế để xử lý cấu trúc mảng đệ quy như đồ thị chu kỳ theo cách tổng quát; deepHashCode có thể đệ quy không dừng và gây **StackOverflowError**. Với đồ thị có chu kỳ, cần mô hình so sánh/băm riêng có cơ chế theo dõi các node đã thăm, cùng định nghĩa rõ thế nào là hai đồ thị bằng nhau.

**Độ sâu có chi phí.** Arrays.hashCode trên mảng một chiều cần duyệt các phần tử ở cấp tương ứng; deepHashCode phải đi qua các mảng lồng nhau. Việc gọi hashCode nhiều lần trên khóa là một chi phí có thật. Chỉ cân nhắc cache hash sau khi thiết kế object thực sự bất biến và đã có lý do đo đạc; cache trên dữ liệu mutable có thể khiến equals/hashCode không nhất quán.

**Các phần tử object phải có hợp đồng đúng.** Nếu Object[] chứa một lớp custom có equals theo trường A nhưng hashCode theo trường B, cả Arrays.hashCode và deepHashCode đều không thể cứu hợp đồng. Cặp phương thức mảng chỉ kế thừa tính đúng đắn từ các phần tử.

**Hai mảng khác nhau có thể cùng hash.** Ví dụ, băm dựa trên 31 là hàm nén từ rất nhiều dãy giá trị xuống một int 32-bit. Không được dùng hash để thay cho equals, kiểm tra trùng nội dung, hay làm dấu vân tay bảo mật.

**Chiều và hình dạng là một phần của cấu trúc.** Một ma trận gồm một hàng hai phần tử và một ma trận gồm hai hàng một phần tử không có cấu trúc deepEquals giống nhau, ngay cả khi liệt kê phẳng các số có thể trông tương tự. Nếu miền nghiệp vụ muốn bỏ qua hình dạng, phải tự chuyển dữ liệu về biểu diễn chuẩn trước.

**Arrays.compare, Arrays.mismatch và Comparator** giải quyết các bài toán khác: thứ tự từ điển, vị trí khác nhau đầu tiên hay so theo tiêu chí tùy chọn. Không mặc định thay đổi equals/hashCode của khóa chỉ vì dùng comparator trong TreeMap/TreeSet. Các cấu trúc sắp xếp xác định sự trùng khóa bằng **compare == 0**, có thể khác hợp đồng equals nếu comparator không nhất quán.

## 10. Kiểm thử hợp đồng và lựa chọn trong bài toán thật

Với một lớp có thành phần mảng, hãy xây ít nhất ba đối tượng **a, b, c**:

- **a** và **b** có dữ liệu nội dung bằng nhau nhưng mảng bên trong được cấp phát độc lập.
- **c** khác **a** ở một phần tử, một hàng lồng nhau hoặc ở một trường giá trị khác.
- Thêm trường hợp mảng rỗng, mảng con null (nếu được phép), phần tử object null, thứ tự khác và object có mảng bị thay đổi sau khởi tạo.

Kiểm tra những điều sau:

1. **a.equals(b)** true khi và chỉ khi nội dung theo tiêu chí nghiệp vụ thực sự bằng nhau; nếu dùng deep, thay mảng con bằng mảng mới cùng dữ liệu không làm đổi kết quả.
2. Khi **a.equals(b)** true, **a.hashCode() == b.hashCode()** luôn true.
3. **a.equals(c)** false nếu tiêu chí giá trị phân biệt a và c. Không kiểm tra rằng hash bắt buộc khác: va chạm hợp lệ.
4. Tính phản xạ, đối xứng, bắc cầu, tính nhất quán và quy tắc với null của **equals** vẫn phải thỏa.
5. Cho các instance bằng nhau vào **HashSet** thì tập chỉ giữ một khóa logic; HashMap tra cứu bằng instance khác nhưng bằng giá trị phải thành công.
6. Thử sửa **mảng đầu vào** và **mảng từ getter** sau khi tạo khóa; nếu lớp được thiết kế bất biến, equals/hashCode của khóa không đổi.
7. Với mảng lồng nhau, chắc chắn mọi tầng mảng phải tham gia so sánh theo đúng ngữ nghĩa đã chọn; không chỉ test một hàng hoặc những trường hợp dùng lại cùng tham chiếu mảng.

### Quy trình quyết định nhanh

| Câu hỏi | Cách chọn |
| --- | --- |
| Có phải mảng primitive **một chiều** và so từng phần tử? | **Arrays.equals** + **Arrays.hashCode** |
| Có phải mảng **Object[]** không chứa mảng con cần duyệt sâu? | **Arrays.equals** + **Arrays.hashCode** |
| Có phải mảng nhiều chiều hoặc Object[] chứa mảng con **và muốn so nội dung lồng nhau**? | **Arrays.deepEquals** + **Arrays.deepHashCode** |
| Trường mảng thuộc **record** hoặc lớp custom? | Override **cả equals lẫn hashCode** nếu muốn so theo nội dung mảng |
| Object được dùng làm khóa HashMap/HashSet? | Đảm bảo các mảng tham gia so sánh **ổn định/bất biến**, sao chép phòng vệ khi cần |
| Dữ liệu chứa cấu trúc chu kỳ hoặc so sánh theo tiêu chí đặc biệt? | Thiết kế **kiểu giá trị và thuật toán riêng**, không dựa trực tiếp vào deep API |

**Ghi nhớ:** **equals và hashCode phải nhìn cùng một “hình dạng giá trị”**. Nếu equals so nội dung một chiều thì hashCode cũng băm nội dung một chiều; nếu equals đi sâu vào các mảng con thì hashCode cũng phải đi sâu bằng quy tắc tương ứng. **Arrays.hashCode** đi cùng **Arrays.equals**; **Arrays.deepHashCode** đi cùng **Arrays.deepEquals**. Trong một lớp chứa mảng, việc chọn đúng hàm mới là bước đầu: giữ dữ liệu khóa ổn định mới giúp hợp đồng hoạt động tin cậy trong các cấu trúc băm.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-code-implementation-008">Cân nhắc boxing, mảng varargs và chi phí trên đường chạy thường xuyên</a>

<details>
<summary>Xem chi tiết</summary>

# Cân nhắc boxing, mảng varargs và chi phí trên đường chạy thường xuyên khi cài đặt `hashCode()`

## 1. Bối cảnh: `hashCode()` đúng trước, tối ưu sau

Trong Java, `hashCode()` trả về một số nguyên 32 bit đại diện cho đối tượng phục vụ những cấu trúc dữ liệu dựa trên hash, chẳng hạn `HashMap` và `HashSet`. Khi ghi đè `equals()`, thông thường cũng phải ghi đè `hashCode()`. Hợp đồng cốt lõi là:

- Nếu `a.equals(b)` trả về `true` thì **bắt buộc** `a.hashCode() == b.hashCode()`.
- Các lời gọi `hashCode()` lặp lại phải cho cùng giá trị khi các thông tin ảnh hưởng đến phép so sánh bằng nhau không đổi.
- Hai đối tượng **không bằng nhau** vẫn có thể trùng hash; mã băm không phải ID duy nhất và không phải mã băm mật mã.
- Việc tràn số nguyên (`int overflow`) trong phép nhân/cộng hash là bình thường trong Java; công thức phổ biến dùng kiểu `int`.

Ở bài này, trọng tâm là **chi phí thực thi** khi tính hash nhiều lần, đặc biệt với phương thức tiện dụng `Objects.hash(Object... values)`. Cần phân biệt **tính đúng** của kết quả với **hiệu suất** của cách tạo ra kết quả. Không được bỏ qua trường dùng trong `equals()` chỉ để tăng tốc nếu điều đó khiến những đối tượng bằng nhau nhận mã băm khác nhau.

Hai cách phổ biến:

~~~java
// Ngắn gọn, tiện khi mã hashCode không nằm trên đường chạy nóng:
return Objects.hash(id, version, name);

// Kết hợp trực tiếp, tránh dựng bộ tham số varargs ở mã nguồn:
int result = Integer.hashCode(id);
result = 31 * result + Long.hashCode(version);
result = 31 * result + Objects.hashCode(name);
return result;
~~~

Cả hai đều có thể **đúng hợp đồng** khi dùng các trường phù hợp, nhưng **không bắt buộc tạo cùng con số**: thuật toán khởi tạo và kết hợp khác nhau. Không trộn hai thuật toán trong các đối tượng cần bằng nhau. Nếu lớp đã phát hành hash dùng để lưu trữ bền vững hay truyền qua hệ thống, còn có ràng buộc riêng về khả năng tương thích; hợp đồng `Object.hashCode()` nói chung **không** hứa hẹn ổn định giữa các lần chạy/JVM hay triển khai.

## 2. `Objects.hash(...)` thực hiện những gì?

Chữ ký:

~~~java
public static int hash(Object... values)
~~~

Cú pháp `Object...` là **varargs**. Khi gọi với danh sách đối số thông thường, trình biên dịch cần cung cấp một mảng `Object[]` chứa các đối số. Về ý nghĩa, lời gọi:

~~~java
Objects.hash(userId, attempt, region)
~~~

tương đương với thao tác tạo tập các tham số:

~~~java
Objects.hash(new Object[] {
    Integer.valueOf(userId),
    Long.valueOf(attempt),
    region
})
~~~

Giả sử `userId` là `int`, `attempt` là `long` và `region` là `String`. Các wrapper `Integer` và `Long` xuất hiện do **autoboxing** từ kiểu nguyên thủy sang kiểu tham chiếu; biến `region` đã là tham chiếu nên không cần boxing.

Bên trong `Objects.hash`, JDK sử dụng `Arrays.hashCode(Object[])` để duyệt các phần tử và kết hợp hash của chúng. Với mảng đối tượng, công thức kết hợp tương ứng là:

~~~text
result = 1
for each element:
    result = 31 * result + (element == null ? 0 : element.hashCode())
~~~

Nếu đối số là primitive đã boxing, hash được lấy từ wrapper tương ứng. Một số chi phí tiềm năng:

1. **Tạo mảng varargs `Object[]`** cho lời gọi ở mã nguồn có các đối số riêng lẻ.
2. **Boxing primitive** thành đối tượng wrapper/giá trị tham chiếu tương ứng.
3. **Đọc các phần tử của mảng** và gọi logic `hashCode()` của từng phần tử.
4. **Chi phí bộ nhớ và GC** nếu JVM không thể loại bỏ các đối tượng tạm; nhiều lời gọi có thể tạo áp lực cấp phát.

Điểm cần hiểu chính xác: **không phải mọi lời gọi `Objects.hash(...)` đều chắc chắn cấp phát trên heap khi chạy thực tế**. JIT (đặc biệt escape analysis, inlining và scalar replacement khi điều kiện cho phép) có thể khử một phần hoặc toàn bộ đối tượng tạm. Boxing cũng có cơ chế cache cho một số giá trị. Khả năng tối ưu phụ thuộc phiên bản JVM, mã thực tế, độ nóng của phương thức, kiểu dữ liệu và ngữ cảnh lời gọi. Vì thế đánh giá chi phí cần dựa vào **profile/benchmark trên ứng dụng đang xét**, không khẳng định một hệ số chậm cố định.

## 3. `Objects.hashCode(Object)` khác `Objects.hash(Object...)`

Hai API có tên gần giống nhưng **không giống nhau**:

| API | Ý nghĩa |
| --- | --- |
| `Objects.hashCode(value)` | Nếu `value == null` thì trả `0`, ngược lại gọi `value.hashCode()`. Một tham chiếu, không cần mảng varargs. |
| `Objects.hash(values...)` | Kết hợp mã băm của **danh sách đối số** bằng `Arrays.hashCode(Object[])`; áp dụng thuật toán mảng có giá trị bắt đầu bằng `1`. |
| `Integer.hashCode(int)`, `Long.hashCode(long)`, `Boolean.hashCode(boolean)` | Tính hash từ primitive với API chuyên biệt, không cần tạo wrapper chỉ để gọi hash. |
| `Arrays.hashCode(int[])` và các overload cho mảng primitive | Tính hash **theo nội dung mảng**, không boxing mỗi phần tử vào `Object[]`. |
| `Arrays.hashCode(Object[])` | Tính hash lần lượt theo các phần tử object; nếu phần tử là mảng lồng nhau thì không tự đi sâu vào nội dung. |

Ví dụ với một đối tượng `String` không null, `Objects.hashCode("a")` trả mã băm trực tiếp của chuỗi, còn `Objects.hash("a")` trả công thức `31 * 1 + "a".hashCode()`. Hai kết quả khác nhau không phải lỗi.

Ví dụ biểu thức `Objects.hashCode(name)` hữu ích khi kết hợp hash trực tiếp vì xử lý được `name == null` mà không phải gọi varargs.

## 4. Ví dụ chạy được: tiện dụng với `Objects.hash` và mã kết hợp trực tiếp

Tạo tệp `HashCostDemo.java`:

~~~java
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class HashCostDemo {
    static final class UserKey {
        private final int id;
        private final long revision;
        private final String region;

        UserKey(int id, long revision, String region) {
            this.id = id;
            this.revision = revision;
            this.region = region;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof UserKey other)) return false;
            return id == other.id
                    && revision == other.revision
                    && Objects.equals(region, other.region);
        }

        // Không cần tạo Object[] và boxing trong logic nguồn.
        @Override
        public int hashCode() {
            int result = Integer.hashCode(id);
            result = 31 * result + Long.hashCode(revision);
            result = 31 * result + Objects.hashCode(region);
            return result;
        }

        // Phương án ngắn gọn: có thể mang chi phí varargs/boxing.
        int hashWithObjects() {
            return Objects.hash(id, revision, region);
        }
    }

    public static void main(String[] args) {
        UserKey first = new UserKey(7, 11L, "VN");
        UserKey same = new UserKey(7, 11L, "VN");
        UserKey other = new UserKey(8, 11L, "VN");

        System.out.println("equals same: " + first.equals(same));
        System.out.println("equals other: " + first.equals(other));
        System.out.println("manual hashes match: "
                + (first.hashCode() == same.hashCode()));
        System.out.println("Objects.hash hashes match: "
                + (first.hashWithObjects() == same.hashWithObjects()));

        Map<UserKey, String> map = new HashMap<>();
        map.put(first, "present");
        System.out.println("lookup equal key: " + map.get(same));
    }
}
~~~

Biên dịch và chạy bằng Java 17+:

~~~bash
javac -encoding UTF-8 HashCostDemo.java
java HashCostDemo
~~~

Stdout:

~~~text
equals same: true
equals other: false
manual hashes match: true
Objects.hash hashes match: true
lookup equal key: present
~~~

Giải thích:

- `equals()` sử dụng các trường `id`, `revision` và `region`; `hashCode()` cũng kết hợp đúng các trường đó.
- `Integer.hashCode(id)` và `Long.hashCode(revision)` tính trực tiếp trên primitive; `Objects.hashCode(region)` xử lý tham chiếu có thể null.
- `hashWithObjects()` cho thấy cách viết gọn cũng sinh hash nhất quán cho các giá trị bằng nhau. **Không có yêu cầu** `hashWithObjects()` phải bằng `hashCode()` vì hai công thức có thể khác nhau.
- `HashMap` tìm lại được giá trị bằng khóa `same` dù `first` và `same` là hai đối tượng riêng biệt, vì `equals` và `hashCode` của lớp tuân hợp đồng.
- Lớp `UserKey` là `final` và các trường là `final` nên ví dụ tránh thay đổi dữ liệu hash sau khi đưa vào map.

## 5. Mảng primitive, mảng object và bẫy varargs

### Bẫy A: `Objects.hash(primitiveArray)` không băm theo từng phần tử primitive

Mảng `int[]` hay `byte[]` là **một đối tượng duy nhất**, không phải `Object[]`. Vì thế gọi `Objects.hash(bytes)` với `bytes` có kiểu `byte[]` về bản chất đưa **một tham chiếu mảng** vào varargs. `Arrays.hashCode(Object[])` ở bên trong sẽ dùng `bytes.hashCode()` (hash của bản thân đối tượng mảng, không dựa trên nội dung các byte).

Đặc biệt nguy hiểm khi `equals()` của lớp dùng `Arrays.equals(a, b)` để so **nội dung**, nhưng `hashCode()` lại dùng `Objects.hash(a)`: hai mảng có cùng nội dung có thể có mã băm khác nhau, **vi phạm hợp đồng**.

Đúng: nếu `equals()` so nội dung mảng primitive, hash cũng dùng `Arrays.hashCode(primitiveArray)`. Với mảng nhiều chiều chứa mảng lồng nhau và equals dùng `Arrays.deepEquals`, cân nhắc `Arrays.deepHashCode`.

### Bẫy B: mảng kiểu tham chiếu có thể được dùng làm chính mảng varargs

Với `String[] tags`, lời gọi `Objects.hash(tags)` có thể được Java coi là truyền thẳng `tags` làm **mảng varargs `Object[]`**, tức hash theo **các phần tử của tags**; khác hoàn toàn với `Objects.hash((Object) tags)`, nơi ép kiểu buộc `tags` thành **một đối số đối tượng**. Trong trường hợp gọi mơ hồ, trình biên dịch có thể cảnh báo về lời gọi varargs.

Do đó:

~~~java
String[] tags = {"core", "java"};

int elementsHash = Objects.hash((Object[]) tags); // Các phần tử là đối số.
int arrayObjectHash = Objects.hash((Object) tags); // Toàn bộ mảng là một đối số.
int contentHash = java.util.Arrays.hashCode(tags); // Rõ ý định băm nội dung.
~~~

Ba biểu thức đều hợp lệ nhưng không cùng ý nghĩa. Khi cần hash nội dung mảng, **viết rõ `Arrays.hashCode(tags)`** sẽ dễ đọc, ít nhầm lẫn hơn. Nếu phần tử lại là mảng, kiểm tra thêm nhu cầu băm nông/sâu.

### Ví dụ chạy được: băm nội dung mảng

Tạo tệp `ArrayHashDemo.java`:

~~~java
import java.util.Arrays;
import java.util.Objects;

public class ArrayHashDemo {
    static final class PayloadKey {
        private final byte[] bytes;

        PayloadKey(byte[] bytes) {
            this.bytes = bytes.clone(); // Sao chép phòng vệ, tránh sửa từ bên ngoài.
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof PayloadKey other)) return false;
            return Arrays.equals(bytes, other.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }

    public static void main(String[] args) {
        byte[] first = {1, 2, 3};
        byte[] sameContent = {1, 2, 3};

        PayloadKey a = new PayloadKey(first);
        PayloadKey b = new PayloadKey(sameContent);
        System.out.println("content equal: "
                + Arrays.equals(first, sameContent));
        System.out.println("keys equal: " + a.equals(b));
        System.out.println("content hashes equal: "
                + (a.hashCode() == b.hashCode()));

        String[] tags = {"core", "java"};
        System.out.println("object array elements: "
                + (Objects.hash((Object[]) tags)
                   == Arrays.hashCode(tags)));
    }
}
~~~

~~~bash
javac -encoding UTF-8 ArrayHashDemo.java
java ArrayHashDemo
~~~

Stdout:

~~~text
content equal: true
keys equal: true
content hashes equal: true
object array elements: true
~~~

Không dùng mã hash theo danh tính mảng để thử sự khác nhau bằng cách in một số hash và suy ra “luôn khác”: các mã hash có thể va chạm. Điều chắc chắn là hash mặc định của đối tượng mảng **không định nghĩa phép băm theo nội dung**.

## 6. Lựa chọn thuật toán hash trực tiếp

Khi `hashCode()` là điểm nóng và dữ liệu gồm các primitive/trường đơn giản, một cách viết rõ ràng là:

~~~java
int result = Integer.hashCode(id);
result = 31 * result + Long.hashCode(sequence);
result = 31 * result + Boolean.hashCode(active);
result = 31 * result + Objects.hashCode(name);
result = 31 * result + Arrays.hashCode(payload);
return result;
~~~

Đây là đoạn **minh họa** trong một lớp có các trường `id` (`int`), `sequence` (`long`), `active` (`boolean`), `name` (tham chiếu String) và `payload` (`byte[]`), với import `Objects` và `Arrays` tương ứng. Đoạn này giả định `equals()` cũng so toàn bộ các trường trên và dùng `Arrays.equals` cho `payload`.

Ý nghĩa:

- `Integer.hashCode(int)` trả về giá trị của `int`.
- `Long.hashCode(long)` kết hợp hai nửa 32 bit của `long`.
- `Boolean.hashCode(boolean)` dùng hai giá trị hash xác định cho `true` và `false`.
- `Objects.hashCode(name)` nhận `null` an toàn và không cần varargs.
- `Arrays.hashCode(payload)` dùng thuật toán theo nội dung mảng và các overload chuyên biệt cho kiểu primitive.
- Hệ số `31` là cách kết hợp quen thuộc, cho phép phân biệt phần nào vị trí/trật tự trường; nó không thể đảm bảo tránh va chạm hoàn toàn.

**Cân nhắc công thức khi lớp chỉ có một trường:** `return Integer.hashCode(id);` hoặc `return Objects.hashCode(name);` thường rõ ràng và tránh vòng kết hợp thừa. Nếu đối tượng có nhiều trường, thứ tự kết hợp phải nhất quán giữa các đối tượng cùng kiểu; `equals()` vẫn là căn cứ để chọn tập trường phù hợp.

### So sánh băm số thực

Với `float`/`double`, đừng tự ép kiểu về `int` vì có thể làm mất quá nhiều thông tin và tạo hash không tương thích với chính sách `equals()`. Nếu `equals()` dựa vào `Float.compare` hoặc `Double.compare` cho trường tương ứng, dùng `Float.hashCode` hoặc `Double.hashCode` là lựa chọn thường phù hợp. Phải nhất quán về cách xử lý `NaN`, dấu của zero và trường hợp giá trị thay đổi; tuyệt đối không mặc định mọi kiểu so sánh số thực là như nhau.

## 7. Quan sát bytecode và hiểu giới hạn của phân tích tĩnh

Trong mã nguồn, lời gọi `Objects.hash(id, sequence, name)` với `int`, `long` và `String` thường khiến trình biên dịch sinh mã để:

1. Tạo một `Object[]` kích thước 3.
2. Đóng gói `id` qua `Integer.valueOf`, `sequence` qua `Long.valueOf`.
3. Đưa các phần tử vào mảng.
4. Gọi `Objects.hash(Object...)`.

Có thể tự quan sát hình dạng bytecode trên Java 17+ với ví dụ `HashCostDemo.java` ở trên:

~~~bash
javac -encoding UTF-8 HashCostDemo.java
javap -c -p 'HashCostDemo$UserKey'
~~~

Lệnh `javap` hiển thị bytecode. Tìm trong phương thức `hashWithObjects` các chỉ dẫn tương ứng với `anewarray`, `Integer.valueOf`, `Long.valueOf` và lời gọi `Objects.hash`. Phương thức `hashCode` thủ công có thể cho thấy các lời gọi `Integer.hashCode`, `Long.hashCode`, `Objects.hashCode` và phép toán `imul/iadd`.

Cần phân biệt:

- **Bytecode** chứng minh hình dạng công việc mà trình biên dịch phát ra.
- **Máy ảo khi chạy** có thể inline, loại bỏ cấp phát hoặc tối ưu khác; vì vậy bytecode có lệnh tạo mảng **không chứng minh** có một cấp phát heap cho mỗi lần gọi lúc chương trình đã được JIT tối ưu.
- Các chỉ dẫn chi tiết có thể thay đổi theo trình biên dịch và JDK. Đừng dựa vào một danh sách opcode cố định làm tiêu chí hiệu năng.

Nếu chạy qua PowerShell và dấu nháy của tên lớp lồng gây rắc rối, dùng đúng tên file `HashCostDemo$UserKey.class` với `javap` theo cú pháp phù hợp shell; dấu `$` trong tên lớp lồng cần được bảo vệ khỏi cơ chế nội suy biến của shell.

## 8. Khi nào chi phí này thực sự quan trọng?

`hashCode()` có thể được gọi nhiều lần trong các thao tác tra cứu, chèn, xóa trên `HashMap`/`HashSet`, các bộ cache, phép đối chiếu khóa và tập dữ liệu lớn. Chi phí của mảng tạm và boxing có thể đáng chú ý khi đồng thời thỏa các điều kiện:

- Lời gọi hash nằm trên **đường chạy thường xuyên** (hot path).
- Khóa có các trường primitive được truyền qua `Objects.hash(...)` liên tục.
- Phần tính hash khác tương đối rẻ, làm overhead mảng/boxing chiếm tỷ trọng rõ ràng.
- JVM không khử hoàn toàn những đối tượng tạm trong môi trường thực.
- Tốc độ hoặc allocation/GC thực sự là yếu tố giới hạn quan sát được.

Ngược lại, chi phí thường **không đáng ưu tiên** nếu hash rất hiếm khi tính, chương trình chủ yếu chờ I/O, chi phí xử lý trường nặng hơn nhiều, hoặc JIT đã loại bỏ overhead. Trường hợp này, `Objects.hash(...)` thường dễ viết và dễ bảo trì hơn. Tối ưu không có số đo có thể làm mã phức tạp hơn mà không cải thiện thời gian phản hồi thực tế.

### Cách đo đáng tin

1. **Xác định đúng phạm vi:** đo riêng cách tính hash nếu muốn so chi phí triển khai; đo cả `Map.get/put` nếu quan tâm trải nghiệm lookup thực tế.
2. **Dùng JMH** (Java Microbenchmark Harness) khi đo microbenchmark: warmup, đo nhiều lần, dùng nhiều fork, kiểm soát tình huống dữ liệu và trạng thái JIT.
3. **Tiêu thụ kết quả** bằng JMH `Blackhole` hoặc cơ chế hợp lệ để tránh dead-code elimination; không dùng vòng lặp tính một hằng số rồi suy ra thời gian hash thực.
4. **So tương đương:** hai cách hash cùng các trường, cùng chính sách `equals()`, cùng dữ liệu null/không null, cùng dạng phân phối, cùng tỷ lệ nóng/lạnh.
5. **Đo thêm cấp phát/GC** (chẳng hạn profiler hoặc công cụ đo allocation) để phân biệt hiệu ứng boxing/varargs với tốc độ tính toán thuần.
6. **Kiểm tra môi trường gần thực tế:** cùng JDK, kiến trúc CPU, heap/GC và điều kiện vận hành có ý nghĩa với ứng dụng.
7. **Đọc thống kê có độ dao động:** nhiều lần thử, so khoảng biến động; một kết quả nanosecond đơn lẻ không đủ để đưa ra kết luận chung.

Một vòng lặp tự viết với `System.nanoTime()` mà thiếu warmup, tiêu thụ kết quả và kiểm soát tối ưu JIT **không đủ tin cậy** để tuyên bố `Objects.hash` nhanh/chậm hơn bao nhiêu lần. Không có một con số tốc độ cố định áp dụng cho mọi JDK và mọi chương trình.

## 9. Cache mã hash có phải giải pháp tốt hơn không?

Với **đối tượng bất biến** được truy cập nhiều lần và hash tốn kém (ví dụ chứa mảng lớn), lưu sẵn mã băm có thể là một lựa chọn. Nhưng phải cân nhắc:

- Tất cả dữ liệu tham gia `equals/hashCode` phải ổn định sau khi cache; nếu dữ liệu thay đổi, hash cache sẽ trở nên sai.
- Nếu dùng `0` như dấu hiệu “chưa tính”, hãy nhớ **0 là hash hợp lệ**. Trường hợp đó có thể làm tính lại nhiều lần; muốn phân biệt đầy đủ cần cờ trạng thái riêng hoặc thiết kế khác.
- Với đối tượng chia sẻ giữa các luồng, cơ chế xuất bản và đồng bộ giá trị cache phải được thiết kế an toàn, đặc biệt nếu cần bảo đảm tính đúng đắn trước việc đọc đồng thời.
- Cache tốn bộ nhớ mỗi đối tượng và làm mã phức tạp; nếu hash rẻ hoặc hiếm dùng thì tính lại có thể tốt hơn.
- Với khóa mutable, việc cache không khắc phục vấn đề thay đổi trạng thái sau khi cho vào `HashMap`; nên ưu tiên khóa bất biến hoặc chính sách quản lý khóa phù hợp.

Đừng tự động cache hash chỉ vì phương thức được gọi thường xuyên. Trước tiên so chi phí tính trực tiếp/`Objects.hash` và độ ổn định của dữ liệu.

## 10. Lỗi thường gặp và cách khắc phục

| Lỗi | Hệ quả và cách xử lý |
| --- | --- |
| Dùng `Objects.hash(intValue, longValue, name)` trong đường chạy nóng mà không quan sát chi phí. | Có thể tạo `Object[]` và wrapper tạm. Dùng profiler/JMH rồi cân nhắc hash trực tiếp với các API primitive. |
| Cho rằng mỗi lời gọi `Objects.hash` luôn tạo đủ đối tượng heap. | Sai trong ngữ cảnh JIT có thể tối ưu cấp phát. Phải đo allocation thực tế. |
| Dùng `Objects.hash(payload)` với `payload` là `byte[]` nhưng `equals` so nội dung mảng. | Hash theo tham chiếu mảng, có thể phá hợp đồng. Sử dụng `Arrays.hashCode(payload)`. |
| Không hiểu `Objects.hash(tags)` khi `tags` là `String[]`. | Có thể truyền mảng làm chính varargs. Viết `Arrays.hashCode(tags)` nếu mục tiêu là nội dung mảng. |
| Nhầm `Objects.hashCode(value)` với `Objects.hash(value)`. | API đầu hash một đối tượng null-safe, API sau kết hợp danh sách varargs; số trả về có thể khác. |
| Tối ưu bằng cách bỏ trường đang tham gia `equals()` mà không xét chính sách hash. | Phải bảo đảm mọi cặp bằng nhau vẫn có cùng hash; chất lượng phân phối/collision cũng có thể xấu đi. |
| Dùng `Objects.hash` hoặc hash thủ công như định danh duy nhất, mã bảo mật. | Mã băm 32 bit dễ va chạm và không có tính chất bảo mật. |
| Benchmark chỉ dùng một vòng lặp `nanoTime()` với đầu vào không đổi. | Có nguy cơ đo sai vì JIT, constant folding và dead-code elimination; dùng JMH và tải dữ liệu phù hợp. |
| Cache hash cho đối tượng có trường mutable. | Cache bị stale; khóa hash collection có thể hoạt động sai. Ưu tiên giá trị bất biến. |

## 11. Quy trình quyết định thực tế

Khi triển khai một value object hoặc khóa dùng trong map/set:

1. **Định nghĩa `equals()` trước:** xác định chính xác những trường quyết định sự bằng nhau, kể cả nội dung mảng và chính sách với `null`.
2. **Viết `hashCode()` đúng hợp đồng:** trường primitive dùng hash chuyên biệt, tham chiếu null-safe, mảng dùng `Arrays.hashCode/deepHashCode` tương ứng với phép so sánh.
3. **Ưu tiên rõ ràng:** với đoạn mã thường, `Objects.hash(...)` có thể phù hợp vì ít mã lặp và khó bỏ sót trường; đừng tối ưu khi chưa có lý do.
4. **Xác định hot path bằng quan sát:** đo thời gian và allocation của hệ thống hoặc đoạn code liên quan, thay vì phỏng đoán.
5. **Nếu overhead đáng kể, dùng công thức trực tiếp:** kết hợp theo thứ tự nhất quán bằng hệ số như `31` và các hàm chuyên biệt, sau đó kiểm tra lại hợp đồng `equals/hashCode`.
6. **Benchmark trước/sau:** so cùng workload, JDK và điều kiện thực tế; không cần thay đổi mọi lớp chỉ vì một lớp xuất hiện vấn đề.
7. **Duy trì mã dễ kiểm chứng:** nếu phép hash trở nên quá phức tạp, ghi lại tiêu chí bằng nhau và bổ sung kiểm thử giá trị bằng nhau, `null`, mảng, va chạm và thao tác map/set.

**Kết luận học tập:** `Objects.hash(...)` tiện dụng nhưng dạng `Object...` có thể làm phát sinh mảng varargs, boxing và công việc duyệt mảng; đó là chi phí **tiềm năng**, không phải bảo đảm cấp phát trong mọi lần chạy. Trên đường chạy nóng, biểu thức hash trực tiếp với `Integer.hashCode`, `Long.hashCode`, `Objects.hashCode`, `Arrays.hashCode` có thể giúp giảm công việc trung gian. Chọn cách viết dựa trên **độ đúng của hợp đồng**, **kiểu dữ liệu**, **đường chạy thực tế** và **số đo**, không chỉ dựa vào độ ngắn của mã nguồn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-code-implementation-009">Chỉ lưu sẵn hash code khi trạng thái và cơ chế đồng bộ cho phép</a>

<details>
<summary>Xem chi tiết</summary>

# Chỉ lưu sẵn hash code khi trạng thái và cơ chế đồng bộ cho phép

`hashCode()` là một phần của hợp đồng bằng nhau giữa các đối tượng Java. Khi tính mã băm tốn chi phí, ta có thể **lưu sẵn (cache, memoization)** kết quả để tái sử dụng. Nhưng một trường `cachedHash` chỉ đúng nếu kết quả đó còn đại diện cho trạng thái mà `equals()` xem là danh tính logic, và nếu nhiều luồng truy cập thì việc công bố cũng như đọc/ghi giá trị đã được thiết kế đúng.

Ba câu hỏi phải được giải quyết trước khi lưu hash:

1. **Trạng thái nào quyết định `equals()` và `hashCode()`?** Trạng thái ấy có giữ nguyên trong suốt vòng đời đối tượng hoặc ít nhất trong thời gian sử dụng cache không?
2. **Hash được tính và công bố như thế nào?** Có luồng khác truy cập trước hoặc đồng thời khi cache được gán không?
3. **Đối tượng được dùng ở đâu?** Nếu đã làm khóa `HashMap` hoặc phần tử `HashSet`, việc thay đổi giá trị quyết định hash có thể làm mất khả năng tra cứu ngay cả khi cache được cập nhật đúng.

Mục tiêu là bảo vệ tính đúng đắn trước rồi mới tối ưu hiệu năng.

## 1. Hợp đồng `equals()` – `hashCode()` quyết định việc có được cache

Với hai đối tượng `a`, `b`:

- Nếu `a.equals(b)` trả `true`, **bắt buộc** `a.hashCode() == b.hashCode()`.
- Hai đối tượng khác nhau **được phép** có mã băm giống nhau: va chạm hash không có nghĩa chúng bằng nhau.
- Trong một lần chạy Java, nếu những thông tin được sử dụng cho phép so sánh bằng nhau **không đổi**, việc gọi `hashCode()` nhiều lần trên cùng đối tượng phải cho kết quả nhất quán.
- Nếu các thông tin dùng trong phép so sánh **thay đổi**, kết quả hash có thể thay đổi; nhưng cần tránh thay đổi khi đối tượng đang được sử dụng như khóa của cấu trúc hash.

Hash được cache là một **giá trị dẫn xuất** từ trạng thái. Khi trạng thái logic đã đổi, cache có thể thành **giá trị cũ (stale cache)**. Ví dụ, đối tượng đang có `code = "B"` nhưng trường cache chứa hash của `"A"`. Một đối tượng khác cũng có `code = "B"` sẽ bằng nhau theo `equals()` nhưng có thể trả một mã băm khác. Đó là lỗi hợp đồng, dù mọi phương thức đều có thể chạy không ném exception.

Cũng không được hiểu `hashCode()` là một ID duy nhất, bất biến theo mọi trường hợp. Mã hash có thể va chạm; tính ổn định phải được thiết kế theo **các trường tham gia equality**.

## 2. Chọn chiến lược theo tính chất trạng thái

| Trường hợp | Khuyến nghị | Lý do |
| --- | --- | --- |
| Value object bất biến; hash thường được sử dụng | Có thể **tính trước** và lưu trong trường `final` | Không phải xử lý invalidation và visibility của một lần ghi cache muộn |
| Value object bất biến; hash đắt nhưng ít được gọi | Có thể **tính lười** với cơ chế công bố an toàn | Tránh tính hash cho những instance không bao giờ cần |
| Object mutable chỉ dùng nội bộ một luồng | Cache có điều kiện; phải vô hiệu hóa khi trạng thái liên quan đổi | Tránh trả lại kết quả cũ |
| Object mutable nhiều luồng cùng truy cập | Cân nhắc thiết kế lại; muốn cache cần nhất quán cho trạng thái, cache và `equals()` | `volatile` riêng cache không đồng bộ các trường trạng thái khác |
| Object mutable đã nằm trong `HashMap`/`HashSet` | Tránh thay đổi các trường dùng cho equality/hash | Invalidation không điều chỉnh vị trí phần tử trong cấu trúc hash |
| Hash tính rẻ, hiếm gọi | Thường cứ tính trực tiếp | Cache tốn bộ nhớ, nhánh điều kiện và đôi khi đồng bộ |

Không nên chỉ thấy `private final List<String> items` mà kết luận trạng thái bất biến: `final` cố định **tham chiếu**, nhưng `List` bên trong vẫn có thể thay đổi. Nếu nội dung danh sách tham gia phép tính hash, cần sao chép phòng vệ và giữ nội dung thực sự bất biến, hoặc chọn một thiết kế khác.

## 3. Cache tính sớm: đơn giản nhất với đối tượng bất biến

Khi mọi trường tham gia `equals()` đều bất biến, ta có thể tính hash một lần trong constructor, lưu vào một trường `final`. Đó là cách tránh việc phải xử lý cache bị lỗi thời.

Tạo file **`EagerHashDemo.java`**:

```java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

final class AccountKey {
    private final String tenant;
    private final String account;
    private final int cachedHash;

    AccountKey(String tenant, String account) {
        this.tenant = Objects.requireNonNull(tenant);
        this.account = Objects.requireNonNull(account);
        this.cachedHash = Objects.hash(tenant, account);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof AccountKey other
                && tenant.equals(other.tenant)
                && account.equals(other.account);
    }

    @Override
    public int hashCode() {
        return cachedHash;
    }
}

public class EagerHashDemo {
    public static void main(String[] args) {
        AccountKey a = new AccountKey("north", "001");
        AccountKey b = new AccountKey("north", "001");
        AccountKey c = new AccountKey("south", "001");

        System.out.println("a equals b: " + a.equals(b));
        System.out.println("same hash: "
                + (a.hashCode() == b.hashCode()));

        Set<AccountKey> keys = new HashSet<>();
        keys.add(a);
        keys.add(b);
        keys.add(c);
        System.out.println("set size: " + keys.size());
    }
}
```

Biên dịch và chạy bằng Java 17+:

```bash
javac EagerHashDemo.java
java EagerHashDemo
```

Stdout:

```text
a equals b: true
same hash: true
set size: 2
```

**Vì sao mẫu này hợp lệ?**

- `tenant` và `account` không null, thuộc kiểu `String` bất biến và được gán một lần.
- `equals()` và phép tính `Objects.hash(tenant, account)` sử dụng **cùng hai thành phần**.
- `cachedHash` là `final`, được gán ngay trong constructor, không có setter.
- `AccountKey` là lớp `final`, ngăn subclass bổ sung trạng thái nhưng kế thừa quy tắc bằng nhau cũ.
- Đối tượng được tạo đúng cách: không để tham chiếu `this` thoát khỏi constructor. Java Memory Model có quy tắc khởi tạo đặc biệt cho `final` fields; tuy vậy, cần tuân thủ cách công bố đối tượng an toàn cho các phần trạng thái khác nếu có.

`Objects.hash()` dễ đọc, nhưng do sử dụng varargs, nó có chi phí bổ sung so với một công thức hash thủ công. Chỉ tối ưu công thức sau khi đo đạc, luôn giữ nhất quán với `equals()`.

**Phân biệt:** tính sớm làm constructor thêm việc, kể cả khi không ai gọi `hashCode()`. Với đối tượng nhỏ và phép hash đơn giản, không có lý do phải lưu sẵn hash nếu đo đạc không cho thấy lợi ích.

## 4. Cache tính lười: phải giải quyết công bố kết quả giữa các luồng

**Lazy caching** chỉ tính hash ở lần gọi đầu tiên. Nếu đối tượng bất biến, hai luồng có thể cùng tính hash dựa trên cùng dữ liệu và nhận cùng kết quả. Trường hợp đó, tính trùng chỉ làm hao công; tính đúng đắn không bị ảnh hưởng nếu phép tính **thuần, tất định**, và kết quả được công bố an toàn.

Một cách dễ kiểm chứng là lưu hash trong trường **`volatile Integer`**, dùng `null` biểu thị chưa tính:

```java
private volatile Integer cachedHash;

@Override
public int hashCode() {
    Integer local = cachedHash;
    if (local != null) {
        return local;
    }

    int computed = immutableCode.hashCode();
    cachedHash = computed;
    return computed;
}
```

Đây là **mảnh code minh họa**, giả định lớp có trường `immutableCode` bất biến. Từ khóa `volatile` đảm bảo khi một luồng đọc được tham chiếu cache đã được công bố, nó quan sát giá trị phù hợp; việc gán một tham chiếu vào trường volatile là nguyên tử. Hai luồng vẫn có thể cùng thấy `null` và cùng tính, nhưng với dữ liệu bất biến, cả hai đều công bố cùng giá trị.

### 4.1. Ví dụ đầy đủ, bao gồm hash bằng 0

Tạo file **`LazyHashDemo.java`**:

```java
import java.util.Objects;

final class LazyKey {
    private final String code;
    private volatile Integer cachedHash;

    LazyKey(String code) {
        this.code = Objects.requireNonNull(code);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof LazyKey other
                && code.equals(other.code);
    }

    @Override
    public int hashCode() {
        Integer local = cachedHash;
        if (local != null) {
            return local;
        }

        int computed = code.hashCode();
        cachedHash = computed;
        return computed;
    }
}

public class LazyHashDemo {
    public static void main(String[] args) {
        LazyKey first = new LazyKey("");
        LazyKey second = new LazyKey("");

        System.out.println("first hash: " + first.hashCode());
        System.out.println("repeat hash: " + first.hashCode());
        System.out.println("equals: " + first.equals(second));
        System.out.println("same hash: "
                + (first.hashCode() == second.hashCode()));
    }
}
```

Lệnh:

```bash
javac LazyHashDemo.java
java LazyHashDemo
```

Stdout:

```text
first hash: 0
repeat hash: 0
equals: true
same hash: true
```

`String.hashCode()` của chuỗi rỗng là `0`. Nếu dùng một trường `int cachedHash = 0` rồi cho `0` nghĩa là “chưa tính”, đối tượng có hash thực sự bằng 0 có thể phải tính lại mãi. Điều đó **không nhất thiết sai kết quả**, nhưng phá kỳ vọng “chỉ tính một lần”. Dùng `Integer` nullable, một cờ đã khởi tạo riêng hoặc một cấu trúc kết quả khác để phân biệt.

**Chi phí cần cân nhắc:** `Integer` có boxing, trường `volatile` có chi phí truy cập và lazy init có nhánh điều kiện. Đối với hash rẻ, việc dùng trực tiếp `code.hashCode()` có thể đơn giản hơn. Đây là **mẫu đúng về semantics**, không phải tuyên bố rằng cache luôn nhanh hơn.

### 4.2. Khi nào cần `synchronized`?

Nếu bản thân phép tính hash rất nặng hoặc có giới hạn tài nguyên nên cần **đúng một lần tính**, có thể bảo vệ việc kiểm tra và cập nhật cache bằng `synchronized` hoặc một cơ chế khởi tạo đồng bộ tương đương. Tuy vậy, đồng bộ không miễn phí và cũng không thay thế yêu cầu dữ liệu dùng tính hash phải có snapshot ổn định.

Không áp dụng double-checked locking một cách máy móc: mẫu đó đòi hỏi thiết kế visibility/công bố đúng với Java Memory Model và chỉ đáng dùng nếu thật sự có lợi ích. Nếu `hashCode()` có side effect, lấy giá trị ngẫu nhiên, đọc đồng hồ hoặc phụ thuộc dữ liệu bên ngoài biến đổi, vấn đề còn nằm ở **thiết kế hàm hash không nhất quán**.

## 5. Lỗi cache stale khi đối tượng mutable

Nếu thuộc tính tham gia `equals()` thay đổi mà cache không được invalidation, hai đối tượng có thể bằng nhau nhưng báo hash khác nhau.

Tạo file **`StaleHashDemo.java`**:

```java
import java.util.Objects;

final class MutableKey {
    private String code;
    private Integer cachedHash;

    MutableKey(String code) {
        this.code = Objects.requireNonNull(code);
    }

    void changeCode(String newCode) {
        code = Objects.requireNonNull(newCode);
        // Lỗi cố ý: không xóa cachedHash.
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof MutableKey other
                && code.equals(other.code);
    }

    @Override
    public int hashCode() {
        if (cachedHash == null) {
            cachedHash = code.hashCode();
        }
        return cachedHash;
    }
}

public class StaleHashDemo {
    public static void main(String[] args) {
        MutableKey a = new MutableKey("A");
        System.out.println("before: " + a.hashCode());

        a.changeCode("B");
        MutableKey b = new MutableKey("B");

        System.out.println("after: " + a.hashCode());
        System.out.println("b hash: " + b.hashCode());
        System.out.println("equals: " + a.equals(b));
        System.out.println("same hash: "
                + (a.hashCode() == b.hashCode()));
    }
}
```

Lệnh:

```bash
javac StaleHashDemo.java
java StaleHashDemo
```

Stdout:

```text
before: 65
after: 65
b hash: 66
equals: true
same hash: false
```

`"A".hashCode()` là 65, `"B".hashCode()` là 66. Sau khi `a` đổi `code` thành `"B"`, `equals()` đánh giá `a` và `b` bằng nhau, nhưng `a.hashCode()` tiếp tục dùng cache cũ 65. **Vi phạm trực tiếp hợp đồng `equals()`/`hashCode()`.** Đây là ví dụ lỗi có chủ ý, chỉ dùng để học.

### 5.1. Vô hiệu hóa cache ở setter là cần nhưng chưa đủ

Với đối tượng mutable và sử dụng trong một luồng được kiểm soát, có thể vô hiệu hóa cache khi giá trị liên quan thay đổi:

```java
void changeCode(String newCode) {
    code = Objects.requireNonNull(newCode);
    cachedHash = null;
}
```

Đây chỉ là **mảnh code cho bối cảnh một luồng hoặc có đồng bộ bao quanh**, chưa phải giải pháp đầy đủ. Cần xét tiếp:

- Mọi setter của **mọi** trường tham gia equality/hash phải vô hiệu hóa cache.
- Với nhiều luồng, một luồng có thể đang tính hash cũ, luồng khác đổi trạng thái và xóa cache; luồng đầu có thể ghi lại cache cũ **sau khi cache đã bị xóa**.
- Không có bảo đảm rằng một lần đọc nhiều trường mutable sẽ tạo thành một **snapshot nhất quán** nếu thiếu cùng một giao thức đồng bộ.
- Nếu đối tượng đã được chèn vào `HashMap`/`HashSet`, tính lại hash sau khi trạng thái đổi không tự chuyển vị trí phần tử trong bảng băm.

Suy ra **vô hiệu hóa cache đúng** vẫn chưa có nghĩa **đổi trạng thái khóa trong collection là an toàn**.

## 6. Nhiều luồng: `volatile` không thay thế đồng bộ trạng thái

Hãy hình dung equality của một đối tượng phụ thuộc vào hai trường mutable `name` và `revision`. Luồng A cập nhật `name` rồi cập nhật `revision`; luồng B tính hash đúng giữa hai bước đó. B có thể lấy cặp giá trị chưa phải trạng thái nghiệp vụ hoàn chỉnh. Thêm `volatile cachedHash` chỉ công bố **giá trị cache**, không biến phép đọc hai trường mutable thành một thao tác nguyên tử.

Muốn cache một hash phụ thuộc trạng thái mutable và chia sẻ nhiều luồng, cần thiết kế và kiểm chứng **toàn bộ** các quan hệ sau:

1. **Đọc/ghi trạng thái:** setter và `hashCode()` sử dụng cùng khóa đồng bộ hoặc snapshot bất biến được công bố nguyên tử.
2. **Cache gắn với phiên bản trạng thái:** không có khả năng một luồng tính từ phiên bản cũ rồi công bố sau lần invalidation của phiên bản mới.
3. **`equals()` phù hợp:** phép so sánh hai đối tượng cũng phải đọc những trạng thái logic nhất quán; nếu cần khóa hai đối tượng, phải giải quyết thứ tự khóa để tránh deadlock.
4. **Công bố đối tượng:** khởi tạo và chia sẻ instance cho luồng khác an toàn, không để trạng thái xây dựng dang dở lọt ra.
5. **Ngữ nghĩa collection:** bất kể cách đồng bộ phía trên, nếu thuộc tính xác định khóa bị sửa **trong thời gian khóa ở trong map/set**, vị trí lưu nội bộ vẫn có thể không tương ứng với giá trị hash mới.

`volatile` giải quyết visibility/order cho **một trường hoặc một bước công bố**, không tạo một transaction cho tập nhiều trường. `synchronized` chỉ có tác dụng khi mọi nơi truy cập liên quan đều tuân một giao thức đồng bộ phù hợp. `ConcurrentHashMap` bảo vệ thao tác của cấu trúc map, **không** tự bảo vệ đối tượng khóa mutable khỏi thay đổi hash/equality.

**Hướng thiết kế thường dễ chứng minh hơn:** giữ khóa bất biến, còn giá trị mutable nằm ở value của map. Ví dụ `Map<AccountKey, MutableAccountState>` dùng `AccountKey` như mục 3. Nếu cập nhật danh tính, tạo khóa mới và thực hiện thao tác thay thế phù hợp theo chính sách đồng bộ của ứng dụng. Hoặc giữ một **snapshot bất biến** mỗi lần cập nhật; mỗi snapshot có trạng thái và mã băm riêng cố định.

## 7. Kiểm tra sự khác biệt giữa bất biến và mutable khi làm khóa

Cấu trúc hash thường tính/phân phối theo hash **ở thời điểm chèn** rồi khi tra cứu sẽ tính hash của khóa truyền vào. Nếu cùng instance làm khóa nhưng giá trị tham gia hash đã đổi, việc tìm có thể đến bucket khác. Cho dù setter xóa cache đúng, dữ liệu vẫn nằm trong vị trí từ lần chèn trước đó. Việc gọi `hashCode()` chuẩn ở hiện tại không “sửa chữa” lịch sử chèn.

Vì vậy:

- `HashMap`, `HashSet` và cả `ConcurrentHashMap` đều cần khóa giữ ngữ nghĩa `equals()`/`hashCode()` ổn định **trong thời gian sử dụng làm khóa**.
- Không được suy luận rằng dùng `Collections.synchronizedMap(...)` làm việc thay đổi khóa trở nên an toàn.
- Nếu buộc phải chuyển định danh, thiết kế một thao tác cập nhật có kiểm soát: loại khóa cũ trong trạng thái cũ, tạo hoặc chọn khóa mới bất biến, chèn lại vào collection theo giao thức đồng bộ phù hợp. Không nên đổi trường hash của khóa đang lưu rồi hy vọng `remove()` tìm đúng đối tượng.
- Nếu đối tượng mutable chỉ đóng vai trò value trong map, thay đổi value không gây ra chính vấn đề bucket theo khóa, dù an toàn luồng của value vẫn phải được thiết kế riêng.

## 8. Quy trình quyết định cache `hashCode()` trong dự án

**Bước 1: Xác định dữ liệu equality.** Nếu chỉ `id` là danh tính, hash nên dựa trên `id`, không đưa thêm tên hoặc timestamp có thể đổi. Nếu là value object, cần xét những thành phần thật sự mang ý nghĩa giá trị.

**Bước 2: Kiểm tra khả năng thay đổi sâu.** Các field dùng để tính hash có bất biến thật không? Có array, list, object con hoặc reference mutable không? Có `this` thoát khỏi constructor không? Subclass có thể đổi định nghĩa equality không?

**Bước 3: Đo chi phí.** Chạy benchmark/profile với workload thực tế. Hash đơn giản đôi khi rẻ hơn việc duy trì cache; ghi thêm trường có thể làm tăng bộ nhớ cho số lượng object lớn.

**Bước 4: Chọn cách lưu nếu phù hợp.** Với object bất biến và hash thường dùng, ưu tiên tính sớm trong trường `final`. Với object bất biến và hash đắt nhưng hiếm dùng, cân nhắc tính lười cùng cơ chế công bố rõ ràng. Nếu phép hash rất rẻ, cứ tính trực tiếp.

**Bước 5: Nếu mutable, thận trọng với invalidation và concurrency.** Mọi đường ghi liên quan phải phối hợp với cache và các đường đọc, tránh ghi trả lại kết quả từ phiên bản cũ sau invalidation. Trong phần lớn ứng dụng, tách khóa bất biến đơn giản và ít lỗi hơn.

**Bước 6: Kiểm tra tập và map.** Đối tượng có được dùng làm khóa trong suốt thời gian nó có thể đổi trường equality/hash không? Nếu có, hãy thay đổi thiết kế khóa thay vì chỉ đổi cách lưu cache.

**Bước 7: Kiểm thử trường hợp biên.** Bao gồm hai instance bằng nhau, va chạm hash, hash bằng 0, cache sau cập nhật trạng thái, nhiều luồng tính đồng thời, và quan hệ `equals()`/`hashCode()` của các lớp kế thừa nếu có.

## 9. Các ngộ nhận thường gặp

| Ngộ nhận | Cách hiểu đúng |
| --- | --- |
| “Tính hash một lần rồi lưu là tối ưu vô hại.” | Lợi ích phụ thuộc workload; cache có chi phí, và có thể sai nếu trạng thái đổi. |
| “Có `final` field nghĩa là mọi dữ liệu bên trong đều bất biến.” | `final` cố định tham chiếu, không ngăn sửa nội dung object được trỏ tới. |
| “`volatile cachedHash` biến object thành thread-safe.” | Nó chỉ bảo vệ công bố/visibility của cache theo mô hình bộ nhớ. |
| “Nếu setter xóa cache là khóa `HashMap` luôn đúng.” | Bucket lưu từ lúc chèn không tự được cập nhật theo hash mới. |
| “`ConcurrentHashMap` giải quyết khóa mutable.” | Thread safety của map không làm cho hash/equality của khóa trở nên ổn định. |
| “Hash bằng 0 tức là chưa tính.” | 0 là mã hash hợp lệ; cần cờ hoặc sentinel phù hợp nếu cần cache cả 0. |
| “Hai mã hash bằng nhau chứng minh hai object bằng nhau.” | Va chạm hash hợp lệ; vẫn cần `equals()`. |
| “Đã đặt `synchronized` trên `hashCode()` thì mọi vấn đề hết.” | Nếu trạng thái và equals không dùng cùng cơ chế phù hợp, vẫn có thể đọc dữ liệu không nhất quán. |

## 10. Checklist code review

- [ ] `equals()` và `hashCode()` dùng dữ liệu phù hợp; các object bằng nhau luôn có cùng hash.
- [ ] Thuộc tính xác định hash ổn định hoặc mọi thay đổi được phối hợp với invalidation đúng cách.
- [ ] Không nhầm `final` reference với bất biến sâu.
- [ ] Cache tính sớm được khởi tạo hoàn chỉnh trước khi object được công bố.
- [ ] Cache tính lười có cơ chế visibility thích hợp, xử lý giá trị hash bằng 0 và xét việc tính trùng.
- [ ] Khi nhiều luồng truy cập, nhất quán cho dữ liệu gốc, cache và `equals()`, không chỉ cho một trường.
- [ ] Các khóa trong `HashMap`/`HashSet` không đổi những trường làm nên equality/hash trong thời gian lưu.
- [ ] Việc lưu hash có lợi ích hiệu năng đã được đo và không làm mã khó bảo trì vô ích.

**Nguyên tắc cần nhớ:** chỉ cache `hashCode()` khi **trạng thái được băm đủ ổn định** và **cơ chế công bố/đồng bộ cho phép tái sử dụng an toàn**. Nếu chưa có lời giải rõ cho hai điều đó, hãy giữ cách tính hash trực tiếp hoặc chọn đối tượng khóa bất biến.


</details>

- [Quay lại đầu trang](#back-to-top)
