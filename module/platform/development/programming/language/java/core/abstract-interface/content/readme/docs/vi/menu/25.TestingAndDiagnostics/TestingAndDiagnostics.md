<a id="back-to-top"></a>

# Kiểm thử và chẩn đoán

## Menu
- [Kiểm thử cùng một hợp đồng trên nhiều cách triển khai](#testing-and-diagnostics-001)
- [Kiểm thử lớp trừu tượng qua lớp con cụ thể tối thiểu](#testing-and-diagnostics-002)
- [Kiểm thử thứ tự bước và hook của Template Method](#testing-and-diagnostics-003)
- [Dùng fake, stub hoặc mock qua interface](#testing-and-diagnostics-004)
- [Kiểm thử bất biến, null, exception và hành vi biên](#testing-and-diagnostics-005)
- [Chẩn đoán lỗi chưa triển khai phương thức và sai phạm vi truy cập](#testing-and-diagnostics-006)
- [Chẩn đoán lỗi ambiguous default, kiểu trả về và erasure](#testing-and-diagnostics-007)
- [Phân biệt lỗi biên dịch với AbstractMethodError, InstantiationError và linkage error](#testing-and-diagnostics-008)
- [Kiểm tra bytecode bằng javap: abstract flag, interface flag và bridge method](#testing-and-diagnostics-009)

## <a id="testing-and-diagnostics-001">Kiểm thử cùng một hợp đồng trên nhiều cách triển khai</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử cùng một hợp đồng trên nhiều cách triển khai

Khi một interface có nhiều lớp triển khai, mỗi lớp có thể dùng cấu trúc dữ liệu hoặc thuật toán khác nhau nhưng vẫn phải tuân thủ cùng một hành vi công khai. **Contract test** là bộ kiểm thử mô tả hành vi bắt buộc của abstraction và chạy bộ kiểm thử đó với từng implementation. Cách này giúp phát hiện một lớp triển khai vi phạm hợp đồng mà không cần viết lại cùng một nhóm kiểm thử cho từng lớp.

Ví dụ, một interface lưu trữ cặp khóa–giá trị có thể được triển khai bằng `HashMap` hoặc `TreeMap`. Cả hai cách triển khai đều phải cho cùng kết quả khi thêm, đọc, ghi đè và xóa dữ liệu, dù cấu trúc nội bộ khác nhau.

## 1. Hợp đồng là gì?

Hợp đồng là tập quy tắc mà người gọi được phép dựa vào khi sử dụng một interface. Hợp đồng thường xác định:

- **Tiền điều kiện:** dữ liệu đầu vào được chấp nhận hay bị từ chối.
- **Hậu điều kiện:** điều gì phải đúng sau khi một thao tác hoàn tất.
- **Bất biến:** điều gì luôn đúng về trạng thái của đối tượng.
- **Hành vi biên:** kết quả khi khóa không tồn tại, collection rỗng, đầu vào sai hoặc thao tác lặp lại.
- **Lỗi được quy định:** loại lỗi mà caller có thể trông đợi với đầu vào không hợp lệ.

Một interface Java chỉ khai báo chữ ký phương thức chưa chắc đã diễn đạt đầy đủ hợp đồng. Javadoc, quy ước API và kiểm thử cần làm rõ những điểm như giá trị trả về khi không tìm thấy dữ liệu, chính sách `null`, ý nghĩa của `false` khi xóa, hoặc việc gọi lại thao tác có tạo thêm phần tử hay không.

Contract test kiểm tra **hành vi quan sát được từ bên ngoài**. Nó không nên phụ thuộc vào tên trường, loại collection nội bộ, thứ tự duyệt nếu hợp đồng không bảo đảm thứ tự, hoặc cách thuật toán được triển khai.

## 2. Ví dụ về hợp đồng dùng chung

Giả sử ứng dụng cần một kho lưu trữ đơn giản:

| Thao tác | Quy tắc của hợp đồng |
| --- | --- |
| Tạo kho mới | `size()` bằng `0`. |
| `put(key, value)` | Cả khóa và giá trị phải khác `null`; lưu giá trị cho khóa. |
| Ghi đè khóa đã có | Giá trị mới thay thế giá trị cũ; số khóa không tăng. |
| `get(key)` | Trả về `Optional` có giá trị nếu khóa tồn tại, nếu không thì `Optional.empty()`. |
| `remove(key)` với khóa tồn tại | Trả về `true` và xóa khóa. |
| `remove(key)` với khóa không tồn tại | Trả về `false`, trạng thái kho không đổi. |
| Đưa `null` vào vị trí khóa hoặc giá trị | Ném `NullPointerException` theo chính sách đã chọn. |

Trong ví dụ này, hợp đồng cố ý không quy định thứ tự các khóa. Vì vậy, implementation dùng `TreeMap` không được coi là đúng hơn implementation dùng `HashMap` chỉ vì nó sắp xếp khóa. Nếu ứng dụng cần thứ tự, hãy đưa yêu cầu về thứ tự vào hợp đồng một cách tường minh và kiểm thử nó.

## 3. Chương trình Java 17+ hoàn chỉnh

Ví dụ dưới đây chạy được mà không cần thư viện kiểm thử bên ngoài. Phương thức `runContract` chứa một bộ kiểm tra duy nhất. Chương trình chạy bộ đó với hai implementation độc lập: `HashMapStore` và `TreeMapStore`.

Lưu toàn bộ mã vào tệp `ContractTestDemo.java`:

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

public class ContractTestDemo {
    public static void main(String[] args) {
        runContract("HashMapStore", HashMapStore::new);
        runContract("TreeMapStore", TreeMapStore::new);
        System.out.println("All contract checks passed.");
    }

    private static void runContract(
            String name, Supplier<KeyValueStore> factory) {
        // Mỗi implementation bắt đầu bằng một đối tượng mới.
        KeyValueStore store = factory.get();

        require(store.size() == 0, "new store must be empty");
        require(store.get("missing").isEmpty(),
                "missing key must return Optional.empty");

        store.put("alpha", "one");
        require(store.get("alpha").equals(Optional.of("one")),
                "get must return the stored value");
        require(store.size() == 1,
                "put must add one key");

        store.put("alpha", "two");
        require(store.get("alpha").equals(Optional.of("two")),
                "put must replace the existing value");
        require(store.size() == 1,
                "overwriting a key must not increase size");

        store.put("beta", "value");
        require(store.size() == 2,
                "a different key must add one entry");

        require(store.remove("alpha"),
                "removing an existing key must return true");
        require(store.get("alpha").isEmpty(),
                "removed key must no longer be present");
        require(store.size() == 1,
                "removing a key must decrease size");

        require(!store.remove("missing"),
                "removing a missing key must return false");
        require(store.size() == 1,
                "removing a missing key must not change size");

        expectNullPointer(
                () -> store.put(null, "value"),
                "null key must be rejected");
        expectNullPointer(
                () -> store.put("key", null),
                "null value must be rejected");
        expectNullPointer(
                () -> store.get(null),
                "null lookup key must be rejected");
        expectNullPointer(
                () -> store.remove(null),
                "null removal key must be rejected");

        System.out.println("PASS " + name);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectNullPointer(
            Runnable action, String message) {
        try {
            action.run();
        } catch (NullPointerException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    interface KeyValueStore {
        void put(String key, String value);
        Optional<String> get(String key);
        boolean remove(String key);
        int size();
    }

    static final class HashMapStore implements KeyValueStore {
        private final Map<String, String> data = new HashMap<>();

        @Override
        public void put(String key, String value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            data.put(key, value);
        }

        @Override
        public Optional<String> get(String key) {
            Objects.requireNonNull(key, "key");
            return Optional.ofNullable(data.get(key));
        }

        @Override
        public boolean remove(String key) {
            Objects.requireNonNull(key, "key");
            return data.remove(key) != null;
        }

        @Override
        public int size() {
            return data.size();
        }
    }

    static final class TreeMapStore implements KeyValueStore {
        private final Map<String, String> data = new TreeMap<>();

        @Override
        public void put(String key, String value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            data.put(key, value);
        }

        @Override
        public Optional<String> get(String key) {
            Objects.requireNonNull(key, "key");
            return Optional.ofNullable(data.get(key));
        }

        @Override
        public boolean remove(String key) {
            Objects.requireNonNull(key, "key");
            return data.remove(key) != null;
        }

        @Override
        public int size() {
            return data.size();
        }
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```text
javac --release 17 ContractTestDemo.java
java ContractTestDemo
```

Đầu ra chuẩn của chương trình:

```text
PASS HashMapStore
PASS TreeMapStore
All contract checks passed.
```

Nếu một implementation vi phạm một quy tắc, `require` hoặc `expectNullPointer` ném `AssertionError`; chương trình không in dòng `PASS` cho implementation đó và kết thúc bằng lỗi. Vì vậy, dòng `PASS` chỉ xuất hiện sau khi toàn bộ các kiểm tra của implementation đã hoàn thành.

## 4. Vì sao cùng một bộ kiểm thử có giá trị?

`runContract` không kiểm tra lớp đang dùng `HashMap` hay `TreeMap`. Nó chỉ gọi các phương thức công khai của `KeyValueStore` và xác nhận kết quả theo hợp đồng.

Điều này mang lại ba lợi ích:

1. **Nhất quán:** mọi implementation đều phải đáp ứng cùng một nhóm hành vi.
2. **Khả năng mở rộng:** khi thêm implementation thứ ba, chỉ cần cung cấp factory và chạy lại contract test.
3. **Bảo trì tập trung:** khi hợp đồng thay đổi có chủ đích, cập nhật một bộ kiểm thử chung rồi chạy lại trên tất cả implementation.

Tuy nhiên, contract test không thay thế mọi kiểm thử khác. Mỗi implementation vẫn có thể cần kiểm thử riêng cho thuật toán, hiệu năng, giới hạn dung lượng, đồng bộ luồng hoặc đặc tính bổ sung không nằm trong interface.

## 5. Tổ chức contract test với JUnit 5

Trong dự án thực tế, JUnit 5 giúp mỗi quy tắc trở thành một test độc lập. Một mẫu thông dụng là đặt các test chung trong lớp trừu tượng, rồi để lớp test cụ thể cung cấp implementation thông qua factory method.

Ví dụ sau giả định interface và hai implementation được tách thành các lớp production trong cùng package. Lớp kiểm thử trừu tượng có thể nằm trong `src/test/java`:

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

abstract class KeyValueStoreContractTest {
    protected abstract KeyValueStore newStore();

    @Test
    void newStoreIsEmpty() {
        assertEquals(0, newStore().size());
    }

    @Test
    void missingKeyReturnsEmpty() {
        assertTrue(newStore().get("missing").isEmpty());
    }

    @Test
    void storedValueCanBeRead() {
        KeyValueStore store = newStore();
        store.put("alpha", "one");

        assertEquals("one", store.get("alpha").orElseThrow());
    }

    @Test
    void puttingExistingKeyReplacesValue() {
        KeyValueStore store = newStore();
        store.put("alpha", "one");
        store.put("alpha", "two");

        assertEquals("two", store.get("alpha").orElseThrow());
        assertEquals(1, store.size());
    }

    @Test
    void removingExistingKeyReturnsTrue() {
        KeyValueStore store = newStore();
        store.put("alpha", "one");

        assertTrue(store.remove("alpha"));
        assertTrue(store.get("alpha").isEmpty());
    }

    @Test
    void removingMissingKeyReturnsFalse() {
        KeyValueStore store = newStore();

        assertFalse(store.remove("missing"));
        assertEquals(0, store.size());
    }

    @Test
    void nullKeyIsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> newStore().get(null));
    }

    @Test
    void nullValueIsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> newStore().put("alpha", null));
    }
}
```

Sau đó, tạo một lớp test cụ thể cho từng implementation:

```java
import org.junit.jupiter.api.Test;

class HashMapKeyValueStoreTest extends KeyValueStoreContractTest {
    @Override
    protected KeyValueStore newStore() {
        return new HashMapKeyValueStore();
    }
}

class TreeKeyValueStoreTest extends KeyValueStoreContractTest {
    @Override
    protected KeyValueStore newStore() {
        return new TreeKeyValueStore();
    }
}
```

Đây là cấu trúc minh họa; trong dự án, mỗi lớp test top-level thường được đặt trong tệp riêng có tên tương ứng. `HashMapKeyValueStore` và `TreeKeyValueStore` là tên ví dụ cho hai implementation production.

JUnit Jupiter nhận các phương thức `@Test` kế thừa từ lớp cha trừu tượng khi chạy lớp con cụ thể. Do đó, cả hai lớp test đều chạy cùng các quy tắc mà không phải sao chép phần thân test. Factory `newStore()` tạo đối tượng mới cho từng test, tránh trạng thái của test trước ảnh hưởng đến test sau.

Với Maven, cần khai báo JUnit Jupiter ở phạm vi `test` và một phiên bản Maven Surefire hỗ trợ JUnit Platform. Sau đó có thể chạy:

```text
mvn test
```

Bộ kiểm thử JUnit phía trên minh họa các quy tắc cốt lõi. Hãy bổ sung các test cho mọi cam kết còn lại của hợp đồng, chẳng hạn `size()` sau mỗi thao tác và hành vi khi xóa khóa vừa được ghi đè.

## 6. Phân biệt contract test và test riêng của implementation

Dùng contract test cho những gì mọi implementation bắt buộc phải làm giống nhau. Dùng test riêng cho các đặc tính được công bố riêng của một implementation.

| Loại kiểm thử | Ví dụ | Nên đặt ở đâu |
| --- | --- | --- |
| Hợp đồng chung | Ghi rồi đọc trả lại đúng giá trị. | Bộ contract test dùng chung. |
| Hợp đồng chung | Khóa không tồn tại trả về `Optional.empty()`. | Bộ contract test dùng chung. |
| Hợp đồng chung | Ghi đè không tạo thêm một khóa. | Bộ contract test dùng chung. |
| Đặc tính riêng | `TreeMap` trả khóa theo thứ tự sắp xếp. | Test riêng cho implementation, nếu hành vi này được cung cấp. |
| Đặc tính riêng | Cache phải loại bỏ mục theo chính sách LRU. | Test riêng cho cache. |
| Đặc tính riêng | Implementation có giới hạn dung lượng hoặc chính sách retry. | Test riêng cho tính năng đó. |
| Tích hợp | Kết nối cơ sở dữ liệu, transaction hoặc serialization hoạt động đúng. | Test tích hợp phù hợp với hạ tầng thực tế. |

Đừng thêm yêu cầu sắp xếp khóa vào contract chỉ vì implementation hiện tại dùng `TreeMap`. Làm như vậy sẽ ép tất cả implementation tuân theo một đặc tính có thể không cần thiết. Ngược lại, nếu caller được quyền dựa vào thứ tự, thứ tự phải được ghi rõ trong hợp đồng và mọi implementation phải đáp ứng nó.

## 7. Các lỗi thiết kế và kiểm thử thường gặp

**Sao chép test cho từng lớp.** Khi test được sao chép, một implementation dễ bị bỏ sót khi hợp đồng thay đổi. Đặt quy tắc chung trong một contract suite và dùng factory để cung cấp các implementation.

**Kiểm tra chi tiết nội bộ.** Một test yêu cầu implementation phải có trường tên `map`, hoặc phải dùng đúng `HashMap`, sẽ thất bại khi có cách triển khai tương đương nhưng hợp lệ. Hãy kiểm tra kết quả qua interface.

**Đưa giả định không có trong hợp đồng vào test.** Ví dụ, so sánh thứ tự khóa khi API không bảo đảm thứ tự sẽ tạo ra test mong manh. Chỉ kiểm thử những hành vi đã cam kết.

**Không tạo trạng thái mới cho mỗi test.** Nếu nhiều test chia sẻ một instance, thứ tự chạy có thể ảnh hưởng kết quả. Mỗi test nên tự tạo đối tượng hoặc dùng fixture có cơ chế đặt lại trạng thái rõ ràng.

**Chỉ kiểm tra đường đi thành công.** Các lỗi phổ biến xuất hiện ở khóa không tồn tại, dữ liệu `null`, thao tác lặp lại và trạng thái rỗng. Đưa các trường hợp biên vào hợp đồng khi chúng ảnh hưởng đến caller.

**Dùng một test suite quá rộng cho các API khác nhau.** Nếu hai implementation có hành vi khác nhau một cách có chủ đích, đừng ép chúng vào cùng một hợp đồng sai lệch. Hãy tách abstraction hoặc chia contract thành các nhóm tính năng mà mỗi implementation cam kết hỗ trợ.

**Dùng mock thay cho implementation thật trong contract test.** Mục tiêu của contract test là xác nhận implementation thực sự tuân thủ hợp đồng. Mock có thể hữu ích để kiểm thử caller, nhưng không chứng minh lớp triển khai thật đáp ứng quy tắc.

**Cho rằng test thành công chứng minh mọi hành vi đều đúng.** Contract test chỉ bao phủ các quy tắc và tình huống đã viết. Nó không tự chứng minh tính đúng đắn hình thức, an toàn luồng, hiệu năng hay mọi đầu vào có thể có.

## 8. Khi contract test thất bại, điều tra thế nào?

1. Xác định implementation nào thất bại và quy tắc hợp đồng nào bị vi phạm.
2. Kiểm tra test có dựa vào hành vi đã được quy định hay đang giả định một chi tiết triển khai.
3. Nếu test diễn đạt đúng hợp đồng, sửa implementation hoặc xác định rõ lỗi của nó.
4. Nếu hợp đồng mơ hồ, quyết định hành vi API cần cam kết rồi cập nhật tài liệu và test một cách nhất quán.
5. Chạy lại contract suite trên tất cả implementation, không chỉ lớp vừa sửa.
6. Chạy thêm test riêng hoặc test tích hợp cho các rủi ro mà hợp đồng chung không bao phủ.

Không nên xóa một assertion chỉ để làm bộ test xanh. Trước tiên hãy xác định assertion đó có đại diện cho một cam kết thật của API hay không.

## 9. Checklist khi thêm implementation mới

- Đọc hợp đồng của interface và xác định rõ tiền điều kiện, kết quả trả về, lỗi và các trường hợp biên.
- Xác nhận implementation mới thực sự đáp ứng cùng abstraction; không bỏ qua quy tắc chỉ vì lớp biên dịch được.
- Chạy toàn bộ contract suite trên implementation mới.
- Đảm bảo mỗi test có trạng thái ban đầu độc lập và không phụ thuộc thứ tự chạy.
- Giữ các kiểm tra về cấu trúc nội bộ, hiệu năng và tính năng riêng trong test phù hợp của implementation.
- Khi thêm hoặc đổi một quy tắc công khai, cập nhật hợp đồng và chạy lại suite cho mọi implementation.
- Khi một implementation không thể đáp ứng hợp đồng, xem xét lại abstraction hoặc tạo interface riêng cho khả năng khác biệt, thay vì âm thầm thay đổi ý nghĩa của một phương thức.

Contract test làm cho interface trở thành một cam kết có thể kiểm chứng. Khi cùng một bộ kiểm thử chạy qua mọi implementation, nhóm phát triển có thể thay đổi cấu trúc bên trong với độ tin cậy cao hơn mà vẫn giữ nguyên hành vi mà caller đã được hứa hẹn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-and-diagnostics-002">Kiểm thử lớp trừu tượng qua lớp con cụ thể tối thiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử lớp trừu tượng qua lớp con cụ thể tối thiểu

Không thể tạo trực tiếp một đối tượng từ abstract class, nhưng vẫn có thể kiểm thử các phương thức concrete đã được định nghĩa trong lớp đó. Cách thông dụng là tạo một **lớp con cụ thể tối thiểu** dành cho kiểm thử: lớp con chỉ cung cấp phần triển khai cần thiết cho các phương thức abstract, rồi bài kiểm thử gọi các phương thức kế thừa của lớp trừu tượng.

## 1. Vì sao cần lớp con cụ thể?

Một abstract class có thể chứa cả hai loại phương thức:

- **Phương thức abstract:** chỉ khai báo hợp đồng, không có thân phương thức; lớp con cụ thể phải triển khai tất cả phương thức abstract còn lại.
- **Phương thức concrete:** có thân phương thức và có thể chứa logic chung cần được kiểm thử trực tiếp.

Java không cho phép viết `new AbstractFormatter()` nếu `AbstractFormatter` là abstract. Thay vào đó, tạo một lớp con như `TestFormatter extends AbstractFormatter`. Lớp con này làm cho kiểu có thể khởi tạo và cho phép gọi logic concrete của lớp cha.

Điều quan trọng là lớp con kiểm thử nên **bổ sung phần còn thiếu tối thiểu**, không ghi đè phương thức concrete đang muốn kiểm thử. Nếu ghi đè phương thức đó, bài kiểm thử có thể vô tình kiểm tra logic của lớp con thay vì logic thực sự nằm trong lớp trừu tượng.

## 2. Mẫu triển khai

Giả sử lớp trừu tượng cung cấp một phương thức `format` hoàn chỉnh, nhưng giao việc chuyển đổi nội dung cho phương thức abstract `transform`:

```java
abstract class AbstractFormatter {
    public final String format(String input) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        String cleaned = input.strip();
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("input must not be blank");
        }
        return "[" + transform(cleaned) + "]";
    }

    protected abstract String transform(String value);
}
```

`format` là phương thức concrete: nó kiểm tra đầu vào, loại bỏ khoảng trắng ở hai đầu và thêm dấu ngoặc. Phương thức `transform` là điểm mở rộng do lớp con cung cấp. Bài kiểm thử có thể dùng một triển khai đơn giản, dễ dự đoán để xác nhận logic của `format`.

## 3. Ví dụ Java 17+ có thể biên dịch và chạy

Lưu toàn bộ mã sau vào tệp `AbstractClassTestDemo.java`. Chương trình sử dụng một lớp con nhỏ cùng các hàm assertion tự viết, nên không cần thư viện kiểm thử bên ngoài.

```java
public class AbstractClassTestDemo {
    abstract static class AbstractFormatter {
        public final String format(String input) {
            if (input == null) {
                throw new IllegalArgumentException("input must not be null");
            }
            String cleaned = input.strip();
            if (cleaned.isBlank()) {
                throw new IllegalArgumentException("input must not be blank");
            }
            return "[" + transform(cleaned) + "]";
        }

        protected abstract String transform(String value);
    }

    // Lớp con tối thiểu cho bài kiểm thử: chỉ triển khai hook abstract.
    static final class TestFormatter extends AbstractFormatter {
        @Override
        protected String transform(String value) {
            return "<" + value + ">";
        }
    }

    public static void main(String[] args) {
        AbstractFormatter formatter = new TestFormatter();

        assertEquals("[<hello>]", formatter.format("  hello  "), "trim và format");
        assertEquals("[<Java core>]", formatter.format(" Java core "), "nội dung thường");
        assertThrows(IllegalArgumentException.class,
                () -> formatter.format(null), "đầu vào null");
        assertThrows(IllegalArgumentException.class,
                () -> formatter.format(" \t "), "đầu vào chỉ có khoảng trắng");

        System.out.println("PASS: 4 checks");
    }

    private static void assertEquals(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected <" + expected
                    + "> but got <" + actual + ">");
        }
    }

    private static void assertThrows(
            Class<? extends RuntimeException> expectedType,
            Runnable action,
            String label) {
        try {
            action.run();
        } catch (RuntimeException actual) {
            if (expectedType.isInstance(actual)) {
                return;
            }
            throw new AssertionError(label + ": expected "
                    + expectedType.getSimpleName() + " but got "
                    + actual.getClass().getSimpleName(), actual);
        }
        throw new AssertionError(label + ": expected "
                + expectedType.getSimpleName() + " but no exception was thrown");
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac AbstractClassTestDemo.java
java AbstractClassTestDemo
```

Stdout chính xác:

```text
PASS: 4 checks
```

Bốn kiểm tra xác nhận rằng `format` xử lý khoảng trắng đúng cách, giữ nguyên nội dung sau khi đi qua hook, và từ chối cả `null` lẫn chuỗi chỉ có khoảng trắng. Nếu bất kỳ kết quả nào sai hoặc ngoại lệ mong đợi không xuất hiện, chương trình ném `AssertionError` và kết thúc với lỗi thay vì in thông báo PASS.

## 4. Điều gì đang được kiểm thử?

Trong dòng `AbstractFormatter formatter = new TestFormatter();`, biến tham chiếu có kiểu abstract class, còn đối tượng thực tế là instance của lớp con cụ thể. Khi gọi `formatter.format(...)`, Java chạy phương thức concrete `format` được định nghĩa trong `AbstractFormatter`. Khi phương thức đó gọi `transform`, Java dispatch đến phần triển khai trong `TestFormatter`.

| Phần | Vai trò trong bài kiểm thử |
| --- | --- |
| `AbstractFormatter.format` | Logic concrete cần kiểm tra: kiểm tra null, chuẩn hóa chuỗi, kiểm tra rỗng và ghép kết quả. |
| `AbstractFormatter.transform` | Điểm mở rộng abstract mà mọi lớp con cụ thể phải triển khai. |
| `TestFormatter.transform` | Triển khai tối thiểu, dễ dự đoán để cho phép tạo đối tượng và cung cấp kết quả cho phương thức concrete. |
| `assertEquals` | So sánh giá trị thực tế với kết quả mong đợi. |
| `assertThrows` | Xác nhận một thao tác ném đúng loại ngoại lệ dự kiến. |

Kiểu của biến tham chiếu không quyết định việc khởi tạo đối tượng; lớp runtime cụ thể mới là kiểu được tạo. Nhưng việc khai báo biến theo kiểu abstract class giúp bài kiểm thử thể hiện đúng API mà người sử dụng lớp cha sẽ nhìn thấy.

## 5. Cách áp dụng trong JUnit 5

Trong dự án thực tế, nên dùng framework kiểm thử hiện có như JUnit 5 thay cho hàm assertion tự viết. Giữ nguyên ý tưởng lớp con tối thiểu, rồi tách mỗi trường hợp thành một test riêng:

```java
@Test
void formatTrimsInputAndUsesTransformHook() {
    AbstractFormatter formatter = new TestFormatter();
    assertEquals("[<hello>]", formatter.format("  hello  "));
}

@Test
void formatRejectsNull() {
    AbstractFormatter formatter = new TestFormatter();
    assertThrows(IllegalArgumentException.class,
            () -> formatter.format(null));
}
```

Đoạn trên là phần minh họa dùng bên trong một lớp test JUnit 5 đã khai báo các import phù hợp; nó không phải tệp độc lập. Với JUnit, mỗi test có tên rõ ràng, báo cáo lỗi chi tiết hơn và có thể chạy tự động trong build.

## 6. Khi lớp trừu tượng phụ thuộc vào constructor hoặc dịch vụ bên ngoài

Nếu abstract class có constructor nhận tham số, lớp con kiểm thử phải gọi constructor đó bằng `super(...)`. Hãy truyền các giá trị cấu hình nhỏ, xác định hoặc một dependency giả phù hợp. Nếu phương thức concrete gọi cơ sở dữ liệu, mạng hoặc đồng hồ hệ thống, hãy xem xét tách dependency thành interface và cung cấp fake/mock trong bài kiểm thử.

```java
abstract class AbstractRepositoryClient {
    private final String endpoint;

    protected AbstractRepositoryClient(String endpoint) {
        this.endpoint = endpoint;
    }

    public String endpointLabel() {
        return "endpoint=" + endpoint;
    }

    protected abstract boolean isAvailable();
}

final class TestRepositoryClient extends AbstractRepositoryClient {
    TestRepositoryClient() {
        super("test://local");
    }

    @Override
    protected boolean isAvailable() {
        return true;
    }
}
```

Đây là ví dụ cấu trúc minh họa, không phải phần của chương trình chạy ở mục trước. Lớp con gọi constructor của cha và cung cấp một giá trị ổn định cho hook abstract. Không cần khởi chạy dịch vụ thật nếu mục tiêu là kiểm tra logic độc lập của lớp cha.

## 7. Các lỗi thường gặp

| Lỗi | Vì sao có vấn đề | Cách xử lý |
| --- | --- | --- |
| Thử tạo `new AbstractFormatter()` | Abstract class không thể được khởi tạo trực tiếp. | Tạo lớp con cụ thể triển khai các phương thức abstract còn thiếu. |
| Lớp con kiểm thử không triển khai đủ phương thức abstract | Bản thân lớp con vẫn là abstract và không thể khởi tạo. | Triển khai tất cả phương thức abstract chưa được thực hiện ở lớp cha. |
| Ghi đè phương thức concrete đang muốn kiểm thử | Bài kiểm thử có thể chạy logic của lớp con, bỏ qua logic gốc cần kiểm tra. | Chỉ triển khai hook abstract cần thiết; giữ nguyên phương thức concrete cần kiểm thử. |
| Lớp con kiểm thử chứa nhiều logic riêng | Lỗi trong fixture có thể khiến kết quả khó diễn giải. | Giữ fixture nhỏ, dễ dự đoán; chỉ thêm hành vi cần thiết để gọi được logic của lớp cha. |
| Chỉ kiểm tra kết quả thành công | Nhánh kiểm tra đầu vào và xử lý lỗi có thể bị bỏ sót. | Kiểm tra cả đầu vào hợp lệ, biên và các trường hợp phải ném ngoại lệ. |
| Kiểm tra qua lớp con nhưng quên kiểm tra hook abstract | Có thể chưa xác minh rằng lớp con cung cấp đúng hành vi mà lớp cha cần. | Viết riêng các kiểm tra cho hợp đồng hook nếu hành vi đó thuộc phạm vi trách nhiệm của module. |
| Dùng assertion của Java mà quên bật `-ea` | Câu lệnh `assert` mặc định có thể không chạy nếu thiếu cờ bật assertions. | Dùng framework kiểm thử hoặc kiểm tra điều kiện tường minh như ví dụ trên; nếu dùng `assert`, chạy `java -ea ...`. |

## 8. Checklist khi viết test cho abstract class

- [ ] Xác định phương thức concrete nào của lớp trừu tượng cần được kiểm tra.
- [ ] Liệt kê các phương thức abstract và triển khai chúng trong lớp con test tối thiểu.
- [ ] Không ghi đè phương thức concrete đang là đối tượng kiểm thử.
- [ ] Cung cấp constructor arguments hoặc dependency giả nếu lớp cha yêu cầu.
- [ ] Kiểm tra kết quả đúng, đầu vào biên và ngoại lệ theo hợp đồng.
- [ ] Chạy test bằng lệnh build hoặc `javac`/`java` tương ứng, rồi xác nhận kết quả thực tế.
- [ ] Nếu có nhiều lớp con triển khai khác nhau, cân nhắc kiểm thử hợp đồng chung qua cùng một bộ test cho từng lớp con.

Nguyên tắc cốt lõi: hãy kiểm thử logic concrete của abstract class thông qua một lớp con cụ thể tối thiểu. Lớp con chỉ nên lấp đầy những điểm mở rộng bắt buộc, để kết quả kiểm thử phản ánh hành vi thật của lớp trừu tượng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-and-diagnostics-003">Kiểm thử thứ tự bước và hook của Template Method</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử thứ tự bước và hook của Template Method

## Mục tiêu

Template Method định nghĩa khung của một thuật toán trong lớp cha, còn các phương thức được ghi đè hoặc hook cho phép lớp con tùy biến một số bước. Khi kiểm thử, cần xác nhận cả kết quả lẫn thứ tự gọi bước, hành vi mặc định của hook và điều gì xảy ra khi một bước thất bại. Bài này dùng Java 17, một bộ kiểm thử nhỏ không cần thư viện ngoài, và các sự kiện được ghi lại để quan sát luồng chạy.

## 1. Vì sao cần kiểm thử thứ tự?

Giả sử một quy trình xử lý dữ liệu phải thực hiện các bước theo thứ tự:

1. Chạy hook trước khi xử lý.
2. Đọc dữ liệu.
3. Biến đổi dữ liệu.
4. Lưu kết quả.
5. Chạy hook sau khi hoàn tất.

Nếu chỉ kiểm tra kết quả cuối cùng, bài kiểm thử có thể bỏ sót việc lưu dữ liệu trước khi biến đổi, gọi hook sai thời điểm hoặc chạy bước sau dù bước trước đã thất bại. Với Template Method, thứ tự là một phần của hợp đồng mà lớp cha cung cấp.

Một cách kiểm tra trực tiếp là cho lớp thử nghiệm ghi tên từng bước vào một danh sách. Sau khi gọi phương thức template, so sánh toàn bộ danh sách thực tế với danh sách sự kiện mong đợi. Cách này giúp phát hiện cả bước bị thiếu, bước bị lặp và thứ tự bị đảo.

## 2. Thiết kế điểm mở rộng có thể kiểm thử

Lớp cha dưới đây định nghĩa phương thức `execute()` là `final`. Lớp con không thể thay thế quy trình tổng thể, nhưng có thể triển khai bước `load()` và `persist()`, đồng thời tùy biến các hook `before()`, `transform()` và `after()`.

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

abstract class DataPipeline {
    public final String execute(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }

        before(source);

        String loaded = load(source);
        if (loaded == null) {
            throw new IllegalStateException("load returned null");
        }

        String transformed = transform(loaded);
        if (transformed == null) {
            throw new IllegalStateException("transform returned null");
        }

        persist(transformed);
        after(transformed);
        return transformed;
    }

    protected void before(String source) {
        // Hook tùy chọn: mặc định không làm gì.
    }

    protected abstract String load(String source);

    protected String transform(String loaded) {
        return loaded.trim();
    }

    protected abstract void persist(String transformed);

    protected void after(String transformed) {
        // Hook tùy chọn: mặc định không làm gì.
    }
}
~~~

Các phương thức `before()` và `after()` là hook tùy chọn có hành vi mặc định rỗng. `transform()` có hành vi mặc định là loại bỏ khoảng trắng ở hai đầu. `load()` và `persist()` là các bước bắt buộc do lớp con triển khai. Các bước kiểm tra dữ liệu nằm trong phương thức template nên mọi lớp con đều phải đi qua chúng khi gọi `execute()`.

Việc khai báo `execute()` là `final` có nghĩa trình biên dịch sẽ từ chối lớp con nếu lớp đó cố ghi đè phương thức này. Kiểm thử thứ tự vẫn cần thiết vì các phương thức template có thể gọi những hook có thể ghi đè; chính các lời gọi đó tạo ra hành vi đa hình ở thời gian chạy.

## 3. Ghi lại sự kiện để xác nhận thứ tự

Tạo một lớp thử nghiệm ghi lại từng bước. Phương thức `load()` trả về chuỗi có khoảng trắng, còn `transform()` ghi nhận dữ liệu đầu vào rồi chuẩn hóa thành chữ hoa. Nhờ vậy, kiểm thử có thể xác nhận không chỉ thứ tự mà còn dữ liệu được truyền giữa các bước.

~~~java
final class RecordingPipeline extends DataPipeline {
    final List<String> events = new ArrayList<>();

    @Override
    protected void before(String source) {
        events.add("before:" + source);
    }

    @Override
    protected String load(String source) {
        events.add("load:" + source);
        return "  hello  ";
    }

    @Override
    protected String transform(String loaded) {
        events.add("transform:" + loaded);
        return loaded.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    protected void persist(String transformed) {
        events.add("persist:" + transformed);
    }

    @Override
    protected void after(String transformed) {
        events.add("after:" + transformed);
    }
}
~~~

Danh sách sự kiện mong đợi khi gọi `execute("source")` là:

~~~text
before:source
load:source
transform:  hello  
persist:HELLO
after:HELLO
~~~

Có thể thấy hook `before()` chạy trước khi đọc, dữ liệu có khoảng trắng được truyền vào `transform()`, kết quả đã chuẩn hóa được lưu, và hook `after()` chỉ chạy sau bước lưu thành công. Nếu danh sách thực tế khác danh sách mong đợi, bài kiểm thử sẽ chỉ ra hợp đồng nào đã bị vi phạm.

## 4. Kiểm tra đường đi thất bại

Ngoài đường đi thành công, hãy xác nhận các bước phía sau không chạy khi một bước trả về dữ liệu không hợp lệ. Lớp dưới đây mô phỏng lỗi đọc dữ liệu bằng cách trả về `null`. Cờ `persisted` giúp kiểm tra rằng bước lưu không được gọi.

~~~java
final class NullLoadPipeline extends DataPipeline {
    boolean persisted;

    @Override
    protected String load(String source) {
        return null;
    }

    @Override
    protected void persist(String transformed) {
        persisted = true;
    }
}
~~~

Theo hợp đồng của `DataPipeline`, `execute()` phải ném `IllegalStateException` với thông báo `load returned null` ngay sau bước đọc. Phương thức `transform()`, `persist()` và `after()` không được chạy. Kiểm thử đường đi thất bại rất quan trọng khi thứ tự bước liên quan đến tác dụng phụ, chẳng hạn ghi dữ liệu ra cơ sở dữ liệu hoặc phát sự kiện.

## 5. Chương trình kiểm thử hoàn chỉnh

Lưu tất cả mã trong cùng một tệp `TemplateMethodOrderTest.java`: đặt ba dòng `import` của mục 2 ở **đầu tệp**, sau đó ghép lần lượt `DataPipeline` (mục 2), `RecordingPipeline` (mục 3), `NullLoadPipeline` (mục 4) và `TemplateMethodOrderTest` (mục 5). Các dòng `import` phải đứng trước mọi khai báo lớp; khi ghép, không lặp thêm `import` ở giữa tệp.

~~~java
public class TemplateMethodOrderTest {
    public static void main(String[] args) {
        RecordingPipeline pipeline = new RecordingPipeline();

        String result = pipeline.execute("source");
        check("HELLO".equals(result), "unexpected result: " + result);

        List<String> expectedEvents = List.of(
            "before:source",
            "load:source",
            "transform:  hello  ",
            "persist:HELLO",
            "after:HELLO"
        );
        check(
            pipeline.events.equals(expectedEvents),
            "unexpected order: " + pipeline.events
        );

        System.out.println("success trace: " + String.join(" -> ", pipeline.events));

        NullLoadPipeline failing = new NullLoadPipeline();
        try {
            failing.execute("source");
            throw new AssertionError("Expected load failure");
        } catch (IllegalStateException expectedFailure) {
            check(
                "load returned null".equals(expectedFailure.getMessage()),
                "unexpected error: " + expectedFailure.getMessage()
            );
        }

        check(!failing.persisted, "persist must not run after invalid load");
        System.out.println("failure path: load returned null; persist skipped");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~text
javac TemplateMethodOrderTest.java
java TemplateMethodOrderTest
~~~

Kết quả stdout chính xác:

~~~text
success trace: before:source -> load:source -> transform:  hello   -> persist:HELLO -> after:HELLO
failure path: load returned null; persist skipped
~~~

Nếu bất kỳ điều kiện nào không đúng, phương thức `check()` ném `AssertionError`, khiến chương trình kết thúc với lỗi thay vì in ra kết quả thành công. Vì vậy, stdout trên chỉ xuất hiện khi cả kết quả, thứ tự sự kiện, thông báo lỗi và việc bỏ qua bước lưu đều đúng.

## 6. Kiểm thử hook mặc định và hook được ghi đè

Một hook có hành vi mặc định cần được kiểm thử theo mục đích của nó. Nếu mặc định là không làm gì, có thể tạo một lớp con chỉ triển khai các bước bắt buộc và xác nhận kết quả vẫn đúng khi không ghi đè hook. Nếu hook mặc định chuẩn hóa dữ liệu, hãy xác nhận việc chuẩn hóa diễn ra khi lớp con không cung cấp phiên bản riêng.

Với hook được ghi đè, hãy kiểm tra:

- Hook chạy đúng một lần và ở đúng vị trí trong luồng.
- Hook nhận đúng dữ liệu đầu vào và kết quả được truyền tiếp đúng bước.
- Hành vi tùy biến không bỏ qua điều kiện bắt buộc của lớp cha.
- Nếu hook ném ngoại lệ, các bước sau nó không được chạy ngoài hợp đồng đã định.
- Nếu hook trả về dữ liệu, lớp cha kiểm tra dữ liệu đó trước khi dùng tiếp.

Không nên kiểm tra bằng cách chỉ xác nhận rằng hook đã được gọi. Một hook có thể được gọi đúng số lần nhưng sai thứ tự hoặc nhận dữ liệu sai. Danh sách sự kiện cùng giá trị đối số giúp kiểm tra hợp đồng rõ ràng hơn.

## 7. Phạm vi kiểm thử và giới hạn của final

Phương thức `final` bảo vệ điểm điều phối khỏi bị ghi đè, nhưng không tự bảo đảm mọi hook đều an toàn. Lớp con vẫn có thể thực hiện tác dụng phụ trong `load()`, `transform()`, `persist()` hoặc hook. Bởi vậy, cần kiểm tra kết quả và lỗi ở ranh giới giữa các bước, đồng thời giới hạn trạng thái mà lớp con có thể sửa.

Trong hệ thống lớn, có thể chuyển các phụ thuộc như kho dữ liệu hoặc bộ đọc vào đối tượng riêng rồi dùng fake/mock để xác minh thứ tự tương tác. Khi dùng JUnit, mỗi đường đi nên là một bài kiểm thử độc lập: đường đi thành công, đầu vào không hợp lệ, dữ liệu đọc không hợp lệ, hook phát sinh ngoại lệ và hành vi hook mặc định. Ví dụ trên dùng `main` và các kiểm tra đơn giản để có thể chạy trực tiếp mà không cần cài thư viện.

Tránh viết kiểm thử phụ thuộc vào chi tiết nội bộ không thuộc hợp đồng. Nếu thứ tự `before → load → transform → persist → after` là hành vi được cam kết, hãy kiểm tra thứ tự đó. Nếu thứ tự giữa hai thao tác không được quy định, đừng làm bài kiểm thử thất bại chỉ vì một triển khai hợp lệ chọn thứ tự khác.

## 8. Checklist rà soát

1. Xác định rõ thứ tự các bước mà Template Method cam kết.
2. Dùng fake hoặc lớp ghi sự kiện để quan sát lời gọi hook và thao tác bắt buộc.
3. So sánh toàn bộ danh sách sự kiện mong đợi với thực tế, bao gồm dữ liệu truyền vào từng bước.
4. Kiểm tra đường đi lỗi và xác nhận các bước phía sau không chạy khi điều kiện tiên quyết thất bại.
5. Kiểm thử hook mặc định và hook được ghi đè.
6. Dùng `final` cho phương thức điều phối khi lớp con không được phép thay đổi thuật toán tổng thể.
7. Chạy ví dụ bằng đúng phiên bản Java mục tiêu và xác nhận stdout khớp với kết quả dự kiến.

## Kết luận

Kiểm thử Template Method cần xác minh hợp đồng của cả quy trình: bước nào chạy, chạy theo thứ tự nào, dữ liệu nào được truyền giữa các bước và các bước sau phản ứng ra sao khi có lỗi. Ghi lại sự kiện bằng fake hoặc spy là cách trực tiếp để kiểm tra các yêu cầu này. Kết hợp phương thức điều phối `final`, hook có phạm vi rõ ràng và kiểm thử cả thành công lẫn thất bại giúp lớp cha giữ được quy trình ổn định mà vẫn cho phép lớp con tùy biến an toàn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-and-diagnostics-004">Dùng fake, stub hoặc mock qua interface</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng fake, stub hoặc mock qua interface trong Java

## 1. Vì sao dùng interface trong kiểm thử?

Một lớp thường cần cộng tác với một thành phần bên ngoài: repository, cổng thanh toán, dịch vụ gửi thông báo, đồng hồ hệ thống hoặc API từ xa. Nếu lớp nghiệp vụ tự tạo trực tiếp một triển khai cụ thể bên trong phương thức, việc thay thế thành phần đó khi kiểm thử sẽ khó hơn.

Interface tạo ra một hợp đồng nhỏ giữa bên sử dụng và bên cung cấp. Lớp nghiệp vụ phụ thuộc vào hợp đồng, không phụ thuộc vào một lớp triển khai cụ thể. Khi chạy ứng dụng, có thể truyền implementation thật; trong kiểm thử, có thể truyền một test double phù hợp với mục tiêu của bài test.

**Test double** là tên gọi chung cho đối tượng thay thế một dependency thật trong kiểm thử. Fake, stub và mock đều là các loại test double, nhưng chúng được dùng với mục đích khác nhau:

- **Stub** trả về câu trả lời đã định trước để kiểm soát tình huống đầu vào và tập trung kiểm tra kết quả của mã đang được test.
- **Fake** là một triển khai đơn giản nhưng có hoạt động thực tế. Ví dụ thường gặp là repository lưu dữ liệu trong bộ nhớ thay vì cơ sở dữ liệu thật.
- **Mock** tập trung vào việc ghi nhận hoặc xác minh tương tác: phương thức nào được gọi, với đối số nào, bao nhiêu lần hoặc theo thứ tự nào. Framework mocking có thể tự động cấu hình và xác minh các kỳ vọng đó.
- **Spy** là một lựa chọn liên quan: nó ghi nhận tương tác, thường bằng cách bọc một triển khai khác và có thể cho lời gọi tiếp tục chạy hành vi thật.

Cách gọi có thể khác đôi chút giữa các nhóm phát triển và framework. Điểm quan trọng là chọn test double dựa trên điều cần chứng minh, không chỉ dựa vào tên gọi.

## 2. Thiết kế một điểm thay thế qua interface

Ví dụ này có một interface tên **PriceCatalog**, cung cấp giá sản phẩm theo mã. **CheckoutService** nhận interface đó qua constructor và dùng nó để tính tổng giá. Lớp nghiệp vụ không cần biết giá đến từ bản đồ trong bộ nhớ, một stub, một mock hay dịch vụ thật.

Mối quan hệ có thể hình dung như sau:

~~~text
Test hoặc ứng dụng
        |
        v
  CheckoutService
        |
        v
   PriceCatalog
    /    |     \
   v     v      v
  Stub  Fake   Mock
~~~

Đây là dependency injection qua constructor: dependency được truyền từ bên ngoài thay vì được tạo cứng bên trong **CheckoutService**. Vì **PriceCatalog** chỉ có một phương thức trừu tượng, nó cũng là một functional interface và có thể được triển khai bằng lambda khi phù hợp.

## 3. Ví dụ hoàn chỉnh với Java 17+

Ví dụ chạy độc lập, không cần thư viện kiểm thử bên ngoài. Chương trình dùng cùng một **CheckoutService** với ba test double khác nhau: stub, fake và mock ghi nhận lời gọi.

Tạo file **Main.java** với nội dung sau:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Main {
    @FunctionalInterface
    interface PriceCatalog {
        int priceInCents(String sku);
    }

    static final class CheckoutService {
        private final PriceCatalog catalog;

        CheckoutService(PriceCatalog catalog) {
            this.catalog = Objects.requireNonNull(catalog, "catalog");
        }

        int totalInCents(String sku, int quantity) {
            if (sku == null || sku.isBlank()) {
                throw new IllegalArgumentException("sku must not be blank");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }

            int unitPrice = catalog.priceInCents(sku);
            if (unitPrice < 0) {
                throw new IllegalStateException("price must not be negative");
            }
            return Math.multiplyExact(unitPrice, quantity);
        }
    }

    // Stub: always returns a predetermined price.
    static final class StubPriceCatalog implements PriceCatalog {
        private final int fixedPrice;

        StubPriceCatalog(int fixedPrice) {
            if (fixedPrice < 0) {
                throw new IllegalArgumentException("price must not be negative");
            }
            this.fixedPrice = fixedPrice;
        }

        @Override
        public int priceInCents(String sku) {
            return fixedPrice;
        }
    }

    // Fake: a small, working in-memory catalog.
    static final class FakePriceCatalog implements PriceCatalog {
        private final Map<String, Integer> prices;

        FakePriceCatalog(Map<String, Integer> prices) {
            this.prices = Map.copyOf(prices);
        }

        @Override
        public int priceInCents(String sku) {
            Integer price = prices.get(sku);
            if (price == null) {
                throw new IllegalArgumentException("Unknown SKU: " + sku);
            }
            return price;
        }
    }

    // Mock: returns a controlled value and records interactions for verification.
    static final class MockPriceCatalog implements PriceCatalog {
        private final int fixedPrice;
        private final List<String> requestedSkus = new ArrayList<>();

        MockPriceCatalog(int fixedPrice) {
            if (fixedPrice < 0) {
                throw new IllegalArgumentException("price must not be negative");
            }
            this.fixedPrice = fixedPrice;
        }

        @Override
        public int priceInCents(String sku) {
            requestedSkus.add(sku);
            return fixedPrice;
        }

        List<String> requestedSkus() {
            return List.copyOf(requestedSkus);
        }
    }

    public static void main(String[] args) {
        CheckoutService stubService =
                new CheckoutService(new StubPriceCatalog(250));
        int stubTotal = stubService.totalInCents("BOOK", 3);
        if (stubTotal != 750) {
            throw new AssertionError("Unexpected stub total: " + stubTotal);
        }

        FakePriceCatalog fakeCatalog = new FakePriceCatalog(
                Map.of("BOOK", 250, "PEN", 75));
        CheckoutService fakeService = new CheckoutService(fakeCatalog);
        int fakeTotal = fakeService.totalInCents("BOOK", 2)
                + fakeService.totalInCents("PEN", 1);
        if (fakeTotal != 575) {
            throw new AssertionError("Unexpected fake total: " + fakeTotal);
        }

        MockPriceCatalog mockCatalog = new MockPriceCatalog(250);
        CheckoutService mockService = new CheckoutService(mockCatalog);
        int mockTotal = mockService.totalInCents("BOOK", 2);
        if (mockTotal != 500
                || !mockCatalog.requestedSkus().equals(List.of("BOOK"))) {
            throw new AssertionError("Unexpected mock interaction");
        }

        System.out.println("Stub total: " + stubTotal + " cents");
        System.out.println("Fake total: " + fakeTotal + " cents");
        System.out.println("Mock total: " + mockTotal + " cents");
        System.out.println("Mock calls: " + mockCatalog.requestedSkus());
    }
}
~~~

Biên dịch và chạy trong thư mục chứa file:

~~~text
javac Main.java
java Main
~~~

Stdout chính xác:

~~~text
Stub total: 750 cents
Fake total: 575 cents
Mock total: 500 cents
Mock calls: [BOOK]
~~~

Nếu có lỗi biên dịch hoặc một điều kiện kiểm tra không đạt, chương trình sẽ kết thúc với lỗi thay vì âm thầm in kết quả thành công.

## 4. Đọc từng test double trong ví dụ

### Stub: kiểm soát câu trả lời

**StubPriceCatalog** luôn trả về giá đã cấu hình khi khởi tạo. Trong ví dụ, giá được đặt là 250 xu, nên mua ba đơn vị cho kết quả 750 xu. Mục tiêu là kiểm tra phép tính của **CheckoutService** mà không phụ thuộc vào dữ liệu bên ngoài.

Stub đặc biệt hữu ích khi cần mô phỏng tình huống cụ thể, chẳng hạn giá bằng 0, bản ghi không tồn tại, một kết quả rỗng hoặc lỗi từ dependency. Hãy thiết kế stub để biểu đạt rõ tình huống đang kiểm tra; tránh biến nó thành một hệ thống mô phỏng quá phức tạp.

### Fake: hành vi đơn giản nhưng có thật

**FakePriceCatalog** lưu giá trong một **Map**. Nó thực hiện tra cứu theo mã sản phẩm và báo lỗi khi mã không có trong danh mục. Đây là một triển khai hoạt động thực tế nhưng đơn giản hơn hệ thống thật, vì không cần kết nối cơ sở dữ liệu hoặc dịch vụ từ xa.

Fake hữu ích khi cần kiểm thử nhiều tương tác với dữ liệu có cấu trúc mà không muốn phụ thuộc vào hạ tầng. Ví dụ khác gồm repository trong bộ nhớ, hàng đợi đơn giản hoặc filesystem giả lập. Fake cần giữ các quy tắc quan trọng mà bài test dựa vào; nếu hành vi của fake quá khác hệ thống thật, test có thể tạo cảm giác an toàn sai.

### Mock: kiểm tra tương tác

**MockPriceCatalog** vừa trả về giá cố định vừa ghi lại mã sản phẩm được yêu cầu. Bài kiểm tra xác minh hai điều: tổng giá là 500 xu và danh sách lời gọi đúng bằng **["BOOK"]**.

Điểm khác biệt không nằm ở việc mock có trả về giá hay không; nó nằm ở việc bài test chủ động xác minh tương tác. Trong dự án thực tế, mock có thể giúp kiểm tra một cổng thanh toán được gọi đúng một lần, một thông báo được gửi tới đúng người nhận hoặc một dependency được gọi với đúng đối số.

Mock viết tay trong ví dụ chỉ ghi nhận lời gọi đơn giản. Nó minh họa ý tưởng, không thay thế đầy đủ tính năng của các framework mocking như cấu hình kỳ vọng, xác minh số lần gọi, thứ tự gọi hoặc mô phỏng ngoại lệ.

## 5. Chọn đúng loại theo mục tiêu kiểm thử

| Điều muốn kiểm tra | Lựa chọn thường phù hợp | Lý do |
| --- | --- | --- |
| Công thức tính tổng với một giá đã biết | Stub | Kết quả đầu vào được kiểm soát |
| Luồng xử lý trên một tập dữ liệu nhỏ, nhất quán | Fake | Có hành vi đủ thực tế mà không cần hạ tầng |
| Một dependency phải được gọi đúng đối số hoặc số lần | Mock | Tương tác là một phần của hợp đồng cần kiểm chứng |
| Tích hợp với database, mạng hoặc dịch vụ thật | Integration test với dependency thật hoặc môi trường kiểm thử chuyên dụng | Test double không chứng minh rằng tích hợp thật hoạt động đúng |

Không cần mock mọi dependency. Nếu bài test chỉ quan tâm đầu ra, stub hoặc fake thường đơn giản và dễ đọc hơn. Mock đặc biệt hữu ích khi tương tác với dependency chính là hành vi cần kiểm tra, nhưng xác minh quá nhiều lời gọi nội bộ có thể khiến test giòn: một thay đổi triển khai không ảnh hưởng kết quả vẫn làm test thất bại.

## 6. Interface và dependency injection: các nguyên tắc thiết kế

- **Định nghĩa hợp đồng theo nhu cầu của bên sử dụng.** Interface nên nhỏ và biểu đạt khả năng cần thiết, không sao chép toàn bộ API của hệ thống bên ngoài.
- **Truyền dependency qua constructor.** Việc thay thế implementation trở nên rõ ràng, và đối tượng có thể giữ dependency trong trường **private final**.
- **Giữ test double bên ngoài logic nghiệp vụ.** **CheckoutService** chỉ gọi **PriceCatalog**; nó không cần **if** để phân biệt đang chạy với stub, fake, mock hay implementation thật.
- **Tách phần có tác dụng phụ.** Truy cập mạng, thời gian hệ thống, tệp và database thường là các ranh giới hữu ích để đưa ra interface khi việc thay thế giúp kiểm thử rõ ràng hơn.
- **Không tạo interface cho mọi lớp theo máy móc.** Nếu một lớp nhỏ không có nhu cầu thay thế, không nằm ở ranh giới quan trọng và không cần một hợp đồng độc lập, thêm interface có thể chỉ tăng độ phức tạp.
- **Tránh trạng thái dùng chung giữa các test.** Mỗi test nên tạo test double mới hoặc reset trạng thái một cách tường minh, để thứ tự chạy test không ảnh hưởng kết quả.
- **Xác minh hành vi có ý nghĩa.** Ưu tiên kết quả và các tương tác thuộc hợp đồng; đừng khóa test vào chi tiết triển khai không quan trọng.

Dependency injection không bắt buộc phải dùng framework. Trong ví dụ, việc tạo **CheckoutService** với một **StubPriceCatalog** đã là injection qua constructor. Một ứng dụng lớn có thể dùng container để lắp ráp dependency, nhưng nguyên tắc thiết kế vẫn giống nhau.

## 7. Giới hạn của test doubles

Test double giúp kiểm tra logic một cách nhanh chóng và có kiểm soát, nhưng không chứng minh rằng dependency thật hoạt động đúng. Một fake có thể không mô phỏng hết giao dịch, khóa đồng thời hoặc ràng buộc của database; một stub có thể bỏ qua các lỗi thực tế của API; một mock có thể xác minh lời gọi nhưng không xác minh rằng dịch vụ từ xa thực sự xử lý yêu cầu đúng.

Vì vậy, hãy kết hợp các mức kiểm thử phù hợp:

1. **Unit test:** kiểm tra logic của một đơn vị bằng stub, fake hoặc mock khi hữu ích.
2. **Integration test:** xác minh những thành phần quan trọng thực sự làm việc cùng nhau, chẳng hạn repository với database kiểm thử.
3. **Contract test hoặc kiểm thử đầu-cuối:** khi cần, xác minh hợp đồng giữa các hệ thống hoặc luồng nghiệp vụ hoàn chỉnh.

Không phải dự án nào cũng cần tất cả các mức ở cùng độ sâu. Chọn dựa trên rủi ro, chi phí và các lỗi mà mỗi mức có thể phát hiện.

## 8. Lỗi thường gặp

- **Gọi mọi test double là mock.** Hãy phân biệt mục tiêu: trả kết quả định trước, mô phỏng hành vi thực tế đơn giản hay xác minh tương tác.
- **Mock quá sâu vào chi tiết nội bộ.** Test dễ hỏng khi tái cấu trúc dù hành vi bên ngoài không thay đổi.
- **Fake không giống hợp đồng thật.** Khi cần thiết, giữ một bộ test hợp đồng để cùng xác minh fake và implementation thật.
- **Dùng test double rồi cho rằng đã kiểm tra tích hợp.** Stub không kiểm tra mạng, database, cấu hình hoặc quyền truy cập thật.
- **Để test double rò rỉ vào mã production.** Mã nghiệp vụ nên phụ thuộc vào interface; việc lựa chọn implementation thường thuộc lớp khởi tạo ứng dụng hoặc cấu hình test.
- **Chia sẻ mock có trạng thái giữa nhiều test.** Lịch sử lời gọi cũ có thể khiến kết quả phụ thuộc thứ tự chạy.
- **Kiểm tra tương tác không mang ý nghĩa nghiệp vụ.** Chỉ xác minh lời gọi khi việc gọi đó là một phần của yêu cầu, không phải chỉ vì có thể ghi nhận nó.

## 9. Checklist trước khi chọn test double

1. Điều quan trọng nhất của test là kết quả trả về, hành vi trên dữ liệu hay tương tác với dependency?
2. Có thể dùng stub đơn giản để cô lập tình huống không?
3. Một fake nhỏ có làm test dễ hiểu hơn và đủ gần với hợp đồng cần dùng không?
4. Có thật sự cần xác minh đối số, số lần gọi hoặc thứ tự tương tác để dùng mock không?
5. Các integration test cần thiết có bao phủ những khác biệt mà test double không mô phỏng được không?
6. Interface có tạo ra một ranh giới hữu ích, hay chỉ thêm một lớp trừu tượng không cần thiết?

Tóm lại, interface giúp mã nghiệp vụ phụ thuộc vào hợp đồng có thể thay thế. Stub kiểm soát câu trả lời, fake cung cấp một triển khai giản lược nhưng hoạt động, còn mock giúp xác minh tương tác. Chọn loại đơn giản nhất có thể chứng minh yêu cầu của bài test, và đừng nhầm unit test thành bằng chứng cho toàn bộ hệ thống tích hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-and-diagnostics-005">Kiểm thử bất biến, null, exception và hành vi biên</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử bất biến, null, exception và hành vi biên

## Mục tiêu

Kiểm thử tốt cần xác minh cả kết quả đúng và cách chương trình phản ứng khi dữ liệu không hợp lệ hoặc rơi vào trường hợp đặc biệt. Với Java, bốn nhóm cần chú ý là bất biến của đối tượng, đầu vào `null`, ngoại lệ và giá trị biên.

Bài này giới thiệu cách xác định hợp đồng cần kiểm tra, thiết kế các trường hợp kiểm thử có ý nghĩa, và dùng một ví dụ Java 17 chạy độc lập để minh họa. Cuối bài có mẫu tương đương bằng JUnit 5 để áp dụng trong dự án thực tế.

## 1. Bắt đầu từ hợp đồng của chương trình

Trước khi viết test, hãy mô tả điều gì phải luôn đúng đối với đối tượng hoặc phương thức. Hợp đồng có thể bao gồm:

- **Điều kiện trước (precondition):** những điều kiện đầu vào phải đáp ứng, chẳng hạn tham số không được `null` hoặc một khoảng số phải có cận dưới không lớn hơn cận trên.
- **Bất biến (invariant):** điều kiện phải đúng đối với trạng thái hợp lệ của đối tượng trong suốt vòng đời công khai của nó.
- **Điều kiện sau (postcondition):** kết quả hoặc trạng thái cần đạt sau khi phương thức hoàn tất thành công.
- **Hành vi lỗi:** loại ngoại lệ được ném ra khi điều kiện đầu vào không được đáp ứng, hoặc cách lỗi được báo cáo theo hợp đồng.

Một test hữu ích kiểm tra hành vi mà người gọi có thể quan sát. Test không nên phụ thuộc vào chi tiết triển khai riêng tư nếu chi tiết đó không thuộc hợp đồng công khai.

### Bố cục Arrange–Act–Assert

Nên tách test thành ba phần:

1. **Arrange:** tạo dữ liệu và đối tượng cần kiểm tra.
2. **Act:** gọi phương thức hoặc thực hiện hành động.
3. **Assert:** xác nhận kết quả, trạng thái hoặc ngoại lệ mong đợi.

Tách ba phần giúp test dễ đọc và khi thất bại, dễ xác định điều kiện nào không được đáp ứng. Mỗi test nên tập trung vào một hành vi chính; các kiểm tra liên quan có thể được nhóm bằng công cụ như `assertAll` trong JUnit.

## 2. Kiểm thử bất biến (invariant)

Bất biến là điều kiện phải luôn đúng đối với mọi trạng thái hợp lệ của đối tượng. Nó thường được bảo vệ trong constructor và các phương thức làm thay đổi trạng thái. Với đối tượng bất biến, constructor là điểm quan trọng vì trạng thái được thiết lập một lần và không thay đổi sau đó.

Ví dụ, một đối tượng biểu diễn khoảng số nguyên có thể yêu cầu:

`min <= max`

Nếu `min` lớn hơn `max`, đối tượng không nên được tạo thành công. Có thể kiểm tra bất biến bằng hai nhóm test:

- **Trường hợp hợp lệ:** cận dưới bằng cận trên, hoặc cận dưới nhỏ hơn cận trên.
- **Trường hợp không hợp lệ:** cận dưới lớn hơn cận trên và constructor phải ném ngoại lệ theo hợp đồng.

Khi đối tượng có nhiều phương thức thay đổi trạng thái, kiểm tra bất biến sau mỗi loại thao tác công khai có thể phát hiện lỗi làm hỏng trạng thái. Không cần kiểm tra lại mọi chi tiết nội bộ; hãy xác minh những điều kiện ảnh hưởng đến hành vi người gọi.

## 3. Kiểm thử đầu vào null

Trong Java, biến tham chiếu có thể mang giá trị `null`. Mỗi API cần quy định rõ `null` có hợp lệ hay không:

- Nếu `null` là đầu vào hợp lệ, test kết quả và trạng thái tương ứng.
- Nếu `null` bị cấm, test xác nhận phương thức từ chối nó theo hợp đồng, thường bằng `NullPointerException`.
- Nếu tham số tùy chọn được biểu diễn bằng `Optional`, kiểm tra cả trường hợp `Optional.empty()` và trường hợp có giá trị khi cả hai đều thuộc hợp đồng.

`Objects.requireNonNull(value, "value")` là cách phổ biến để từ chối `null` với lỗi rõ ràng. Không nên mặc định mọi tham số đều phải từ chối `null`; quyết định phải xuất phát từ hợp đồng của API. Cũng không nên bắt `NullPointerException` rồi bỏ qua, vì điều đó có thể che lỗi lập trình.

Test `null` nên gọi trực tiếp phương thức công khai với `null` và xác nhận hành vi được cam kết. Nếu chỉ kiểm tra rằng chương trình không bị crash ở một đường đi thông thường, test có thể bỏ sót hợp đồng xử lý đầu vào không hợp lệ.

## 4. Kiểm thử exception

Khi một thao tác không thể hoàn tất, Java có thể ném ngoại lệ. Test nên xác nhận ngoại lệ đúng loại được ném ở đúng tình huống:

- Dữ liệu không hợp lệ thường dùng `IllegalArgumentException` hoặc loại cụ thể hơn theo hợp đồng.
- Tham chiếu `null` bị cấm thường dẫn đến `NullPointerException`.
- Lỗi I/O có thể được biểu diễn bằng `IOException` hoặc một subtype phù hợp.
- Trạng thái đối tượng không cho phép thao tác có thể được biểu diễn bằng `IllegalStateException`, nếu đó là hợp đồng đã chọn.

Đừng chỉ kiểm tra rằng “có một ngoại lệ bất kỳ”. Một test bắt quá rộng như `catch (Exception)` có thể vô tình chấp nhận lỗi không liên quan. Trong JUnit, `assertThrows(ExpectedType.class, executable)` xác nhận loại ngoại lệ mong đợi và trả về ngoại lệ để kiểm tra thêm nếu cần.

Chỉ kiểm tra thông điệp ngoại lệ khi thông điệp đó là một phần của hợp đồng hoặc có giá trị thực sự cho người gọi. Nếu thông điệp chỉ là chi tiết nội bộ, kiểm tra loại ngoại lệ sẽ bền vững hơn khi mã được bảo trì. Với checked exception, chữ ký phương thức và cách gọi test cũng phải tuân theo quy tắc Java.

Ngoài ra, test cần phân biệt hai trường hợp: ngoại lệ được ném ra như dự kiến và phương thức hoàn tất bình thường. Nếu không có ngoại lệ nhưng test yêu cầu phải có, test phải thất bại.

## 5. Kiểm thử hành vi biên (boundary values)

Lỗi thường xuất hiện tại ranh giới của điều kiện, vì vậy đừng chỉ kiểm thử một giá trị ở giữa miền hợp lệ. Với một khoảng đóng từ `min` đến `max`, các giá trị đáng chú ý là:

- `min`: giá trị hợp lệ nhỏ nhất.
- `max`: giá trị hợp lệ lớn nhất.
- `min - 1`: ngay ngoài phía dưới, nếu phép tính không tràn số.
- `max + 1`: ngay ngoài phía trên, nếu phép tính không tràn số.

Cần xác định rõ ranh giới có bao gồm cận hay không. Với khoảng đóng, cả `min` và `max` đều thuộc khoảng. Với khoảng nửa mở `[min, max)`, cận trên không thuộc khoảng. Test phải phản ánh chính xác định nghĩa đó.

Các nhóm biên khác tùy dữ liệu:

| Loại dữ liệu | Các trường hợp nên cân nhắc |
|---|---|
| Số nguyên | Giá trị nhỏ nhất/lớn nhất được hỗ trợ, 0, số âm, ngay trong và ngoài giới hạn |
| Chuỗi | `null`, chuỗi rỗng, chuỗi chỉ có khoảng trắng, độ dài tối thiểu/tối đa, ký tự Unicode |
| Collection | `null`, rỗng, một phần tử, nhiều phần tử |
| Ngày giờ | Ngay trước/đúng/ngay sau mốc, cuối tháng, năm nhuận, múi giờ nếu liên quan |
| Phân trang | Trang đầu, trang cuối, kích thước 0 nếu được cho phép, chỉ số ngoài miền |
| Tài nguyên | Trạng thái chưa mở, đang mở, đã đóng; thao tác lặp lại theo hợp đồng |

Tránh tạo `min - 1` hoặc `max + 1` nếu các phép tính đó có thể tràn số. Với miền gần `Integer.MIN_VALUE` hoặc `Integer.MAX_VALUE`, hãy thiết kế test trực tiếp cho các giá trị cực trị thay vì tính một giá trị nằm ngoài kiểu dữ liệu.

## 6. Ví dụ Java 17 chạy độc lập

Ví dụ dưới đây tạo một record `IntRange` biểu diễn khoảng đóng. Bất biến của nó là `min <= max`. Phương thức `contains` nhận `Integer` để minh họa hợp đồng với `null`, và kiểm tra cả hai cận.

Lưu toàn bộ mã vào `Main.java`:

~~~java
import java.util.Objects;

record IntRange(int min, int max) {
    IntRange {
        if (min > max) {
            throw new IllegalArgumentException("min must be <= max");
        }
    }

    boolean contains(Integer value) {
        Objects.requireNonNull(value, "value");
        return value >= min && value <= max;
    }
}

public class Main {
    private static int passed = 0;

    private static void check(String name, boolean condition) {
        if (!condition) {
            throw new AssertionError("FAIL: " + name);
        }
        passed++;
        System.out.println("PASS: " + name);
    }

    private static <T extends Throwable> T expectThrows(
            Class<T> expectedType, Runnable action, String name) {
        try {
            action.run();
        } catch (Throwable actual) {
            if (expectedType.isInstance(actual)) {
                passed++;
                System.out.println("PASS: " + name);
                return expectedType.cast(actual);
            }
            throw new AssertionError(
                    name + " expected " + expectedType.getSimpleName()
                            + " but got " + actual, actual);
        }
        throw new AssertionError(
                name + " expected " + expectedType.getSimpleName()
                        + " but nothing was thrown");
    }

    public static void main(String[] args) {
        IntRange range = new IntRange(10, 20);

        check("lower boundary is included", range.contains(10));
        check("upper boundary is included", range.contains(20));
        check("below lower boundary is excluded", !range.contains(9));
        check("above upper boundary is excluded", !range.contains(21));

        expectThrows(IllegalArgumentException.class,
                () -> new IntRange(20, 10),
                "invalid range violates invariant");
        expectThrows(NullPointerException.class,
                () -> range.contains(null),
                "null input is rejected");

        System.out.println("All " + passed + " checks passed.");
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~text
javac Main.java
java Main
~~~

Đầu ra chuẩn chính xác:

~~~text
PASS: lower boundary is included
PASS: upper boundary is included
PASS: below lower boundary is excluded
PASS: above upper boundary is excluded
PASS: invalid range violates invariant
PASS: null input is rejected
All 6 checks passed.
~~~

Trong ví dụ, constructor từ chối khoảng đảo ngược trước khi tạo được đối tượng không hợp lệ. Hai test đầu xác nhận khoảng đóng bao gồm cả cận dưới và cận trên; hai test tiếp theo xác nhận giá trị ngay ngoài khoảng bị loại. Hai test cuối xác nhận đầu vào vi phạm hợp đồng gây ra đúng loại ngoại lệ.

Hàm `expectThrows` chỉ là bộ trợ giúp nhỏ cho ví dụ chạy độc lập, không phải thư viện kiểm thử thay thế JUnit. Nó ném `AssertionError` nếu không có ngoại lệ hoặc ngoại lệ sai loại, nên chương trình không thể in báo cáo thành công khi một kiểm tra thất bại.

## 7. Cách viết các kiểm thử tương tự bằng JUnit 5

Trong dự án sử dụng JUnit 5, hãy đặt lớp cần kiểm thử và lớp test theo cấu trúc dự án. Ví dụ dưới đây giả sử `IntRange` nằm trong cùng package và có thể truy cập từ test:

~~~java
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IntRangeTest {
    @Test
    void includesBothBoundaries() {
        IntRange range = new IntRange(10, 20);

        assertTrue(range.contains(10));
        assertTrue(range.contains(20));
    }

    @ParameterizedTest
    @ValueSource(ints = {9, 21})
    void excludesValuesOutsideRange(int value) {
        IntRange range = new IntRange(10, 20);

        assertFalse(range.contains(value));
    }

    @Test
    void rejectsInvertedRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IntRange(20, 10));
    }

    @Test
    void rejectsNullValue() {
        IntRange range = new IntRange(10, 20);

        assertThrows(
                NullPointerException.class,
                () -> range.contains(null));
    }
}
~~~

`@Test` đánh dấu một test. `@ParameterizedTest` chạy cùng một hành vi với nhiều dữ liệu, trong ví dụ là hai giá trị nằm ngoài khoảng. `@ValueSource` cung cấp dữ liệu cho các lần chạy. `assertThrows` xác nhận loại ngoại lệ mong đợi; nếu không có ngoại lệ hoặc loại không khớp, test thất bại.

Đoạn JUnit ở trên là mã test cho một dự án đã cấu hình JUnit 5, không phải chương trình độc lập có thể chạy chỉ với `javac` mặc định. Dùng hệ thống build của dự án (chẳng hạn Maven hoặc Gradle) để thêm JUnit và chạy test suite. Ví dụ độc lập ở mục trước không cần thư viện bên ngoài.

## 8. Chất lượng test và chẩn đoán khi thất bại

Một test tốt cần thất bại vì lý do rõ ràng khi hành vi bị phá vỡ. Hãy áp dụng các nguyên tắc sau:

- **Đặt tên theo hành vi:** chẳng hạn `rejectsInvertedRange`, thay vì `test1`.
- **Mỗi test có mục tiêu rõ:** tránh gom nhiều hành vi không liên quan vào một test dài.
- **Dùng dữ liệu xác định:** không phụ thuộc vào thời gian thực, mạng hoặc trạng thái dùng chung nếu điều đó không phải mục tiêu kiểm thử.
- **Kiểm tra kết quả quan sát được:** đầu ra, trạng thái công khai, loại ngoại lệ và các tác dụng phụ thuộc hợp đồng.
- **Tránh bắt ngoại lệ quá rộng:** không để lỗi bất ngờ được coi là thành công.
- **Giữ test độc lập:** một test không nên cần chạy sau một test khác mới thành công.
- **Đọc thông báo thất bại:** xác định đầu vào, giá trị mong đợi, giá trị thực tế và vị trí lỗi trước khi sửa mã.
- **Tái chạy nhóm test liên quan:** sau khi sửa, xác nhận test mới và các hành vi lân cận vẫn đúng.

Đừng chỉ viết test cho nhánh thành công. Test ngoại lệ và giá trị biên thường làm rõ hợp đồng của API hơn, nhưng cũng tránh khẳng định chi tiết không được cam kết. Ví dụ, nếu hợp đồng chỉ yêu cầu từ chối dữ liệu không hợp lệ, test loại ngoại lệ có thể đủ; kiểm tra chính xác chuỗi thông điệp chỉ nên làm khi thông điệp đó có ý nghĩa công khai.

## Quy trình kiểm thử gợi ý

1. Viết ra bất biến, điều kiện đầu vào, kết quả và hành vi lỗi mong đợi.
2. Tạo ít nhất một trường hợp hợp lệ và một trường hợp không hợp lệ cho mỗi hợp đồng quan trọng.
3. Với giới hạn, kiểm tra ngay tại cận và ngay bên ngoài cận ở cả hai phía khi áp dụng.
4. Kiểm tra `null` theo hợp đồng của từng tham số tham chiếu.
5. Dùng `assertThrows` hoặc bộ kiểm tra ngoại lệ tương đương để xác nhận loại lỗi.
6. Chạy test, đọc lỗi thất bại, sửa nguyên nhân rồi chạy lại test liên quan.

## Ghi nhớ

Bất biến xác định trạng thái nào được xem là hợp lệ; kiểm thử `null` xác minh hợp đồng đầu vào; kiểm thử exception xác minh cách lỗi được báo cáo; còn kiểm thử biên tập trung vào các giá trị ngay tại và quanh giới hạn. Kết hợp bốn nhóm này giúp phát hiện nhiều lỗi mà chỉ kiểm tra một vài kết quả thông thường sẽ bỏ qua.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-and-diagnostics-006">Chẩn đoán lỗi chưa triển khai phương thức và sai phạm vi truy cập</a>

<details>
<summary>Xem chi tiết</summary>

# Chẩn đoán lỗi chưa triển khai phương thức và sai phạm vi truy cập trong Java

Khi một lớp implements interface hoặc extends lớp trừu tượng, trình biên dịch kiểm tra xem lớp đó có đáp ứng đầy đủ hợp đồng phương thức hay không. Hai nhóm lỗi thường gặp là **chưa triển khai phương thức trừu tượng** và **ghi đè phương thức với phạm vi truy cập quá hẹp**. Cách chẩn đoán hiệu quả là xác định phương thức nào còn thiếu, so khớp chữ ký phương thức, kiểm tra mức truy cập, rồi biên dịch lại sau mỗi lần sửa.

## 1. Lỗi chưa triển khai phương thức là gì?

Một interface có thể khai báo các phương thức mà lớp triển khai phải cung cấp. Ví dụ:

~~~java
interface Printer {
    void print(String message);
}
~~~

Một lớp concrete triển khai Printer phải cung cấp phương thức print với đúng tên, kiểu tham số và kiểu trả về tương thích. Nếu lớp không có phương thức phù hợp, Java thường báo lỗi dạng:

~~~text
PrinterImpl is not abstract and does not override abstract method print(String) in Printer
~~~

Tên lớp và nội dung thông báo chính xác phụ thuộc mã nguồn, nhưng ý nghĩa là: lớp PrinterImpl được khai báo là lớp cụ thể, trong khi vẫn còn phương thức trừu tượng cần triển khai.

Có hai hướng xử lý hợp lệ:

- **Triển khai phương thức còn thiếu** nếu lớp được thiết kế để tạo đối tượng và thực hiện hành vi đó.
- **Khai báo lớp là abstract** nếu lớp chỉ là lớp nền chưa thể hoàn thành toàn bộ hành vi và dự kiến lớp con sẽ triển khai phần còn lại.

Ví dụ lớp trừu tượng có thể để phương thức chưa triển khai:

~~~java
abstract class BasePrinter implements Printer {
    // Chưa triển khai print(String), nên lớp phải là abstract.
}
~~~

Lớp cụ thể kế thừa BasePrinter vẫn phải hoàn thiện print(String), trừ khi chính lớp con đó cũng được khai báo abstract.

## 2. Kiểm tra chữ ký phương thức trước khi thêm mã

Tên phương thức giống nhau chưa đủ để tạo ra một lần override. Cần so sánh ít nhất tên, danh sách kiểu tham số, kiểu trả về và quan hệ kế thừa. Với phương thức kế thừa, hãy kiểm tra cả generic, kiểu tham số, kiểu trả về tương thích và các quy tắc exception của Java.

Ví dụ dưới đây **cố ý không biên dịch**:

~~~java
interface Printer {
    void print(String message);
}

class ConsolePrinter implements Printer {
    public void print() {
        System.out.println("Hello");
    }
}
~~~

ConsolePrinter định nghĩa print() không có tham số. Đây là một phương thức khác, không triển khai print(String) của Printer. Vì thế lớp vẫn còn thiếu phương thức bắt buộc.

Cách sửa là dùng đúng chữ ký:

~~~java
interface Printer {
    void print(String message);
}

class ConsolePrinter implements Printer {
    @Override
    public void print(String message) {
        System.out.println(message);
    }
}
~~~

**@Override** giúp trình biên dịch xác minh ý định ghi đè. Nếu vô tình viết sai tên hoặc sai tham số, trình biên dịch sẽ báo rằng phương thức không ghi đè hay triển khai phương thức nào từ kiểu cha. Đây là tín hiệu hữu ích để phát hiện lỗi sớm.

### Phân biệt override và overload

- **Override**: lớp con cung cấp cách thực hiện mới cho một phương thức được kế thừa, với chữ ký tương thích.
- **Overload**: cùng tên nhưng có danh sách tham số khác nhau. Overload không thay thế phương thức trừu tượng đang thiếu.

Ví dụ print() và print(String) là hai overload khác nhau. Nếu interface yêu cầu print(String), chỉ có print() thì vẫn chưa đủ.

## 3. Vì sao sai phạm vi truy cập gây lỗi?

Java không cho phép phương thức ghi đè trở nên khó truy cập hơn so với phương thức được ghi đè. Quy tắc này bảo đảm mã gọi qua kiểu cha vẫn có thể gọi phương thức sau khi đối tượng được thay bằng một thể hiện của lớp con.

Xét interface:

~~~java
interface Sender {
    void send(String message);
}
~~~

Các phương thức thông thường được khai báo trong interface mà không có thân đều là public abstract ngầm định. Vì vậy, phương thức triển khai phải là public.

Đoạn dưới đây **cố ý không biên dịch**:

~~~java
class EmailSender implements Sender {
    @Override
    void send(String message) {
        System.out.println(message);
    }
}
~~~

Phương thức send không ghi public, nên mức truy cập mặc định của nó chỉ là package-private. Điều này làm giảm khả năng truy cập so với send(String) công khai của interface. Trình biên dịch thường đưa ra thông báo tương tự:

~~~text
attempting to assign weaker access privileges; was public
~~~

Cách sửa:

~~~java
class EmailSender implements Sender {
    @Override
    public void send(String message) {
        System.out.println(message);
    }
}
~~~

Khi triển khai interface, hãy khai báo public rõ ràng cho các phương thức triển khai, kể cả khi thân phương thức rất ngắn.

## 4. Quy tắc phạm vi khi override lớp thông thường hoặc lớp trừu tượng

Mức truy cập của phương thức ghi đè có thể được giữ nguyên hoặc mở rộng, nhưng không được thu hẹp. Bảng sau tóm tắt các mức truy cập:

| Phương thức ở lớp cha | Mức truy cập tối thiểu ở lớp con | Ghi chú |
|---|---|---|
| public | public | Không được đổi thành protected, package-private hoặc private |
| protected | protected hoặc public | Không được đổi thành package-private hoặc private |
| package-private | package-private hoặc rộng hơn, nếu thật sự override trong cùng package | Quan hệ package ảnh hưởng việc phương thức có được kế thừa và ghi đè hay không |
| private | Không override theo nghĩa đa hình | Phương thức private không được lớp con kế thừa; cùng tên ở lớp con là phương thức riêng |

Ví dụ sau **cố ý không biên dịch** vì lớp con thu hẹp protected thành private:

~~~java
abstract class BaseTask {
    protected abstract void execute();
}

class ReportTask extends BaseTask {
    @Override
    private void execute() {
        System.out.println("Generate report");
    }
}
~~~

Cách sửa là giữ protected hoặc mở rộng thành public:

~~~java
abstract class BaseTask {
    protected abstract void execute();
}

class ReportTask extends BaseTask {
    @Override
    protected void execute() {
        System.out.println("Generate report");
    }
}
~~~

Cũng cần nhớ rằng một phương thức private không thể là phương thức abstract vì lớp con không thể override nó. Phương thức abstract cũng không thể đồng thời là static hoặc final: static thuộc về lớp, final cấm override, còn abstract yêu cầu lớp con cung cấp phần thực hiện.

## 5. Ví dụ hoàn chỉnh chạy được

Ví dụ này kết hợp interface, lớp trừu tượng và lớp cụ thể. BaseChannel triển khai phương thức public send của interface và yêu cầu lớp con cung cấp phương thức format với mức truy cập protected. EmailChannel hoàn thiện phương thức còn thiếu bằng đúng chữ ký và mức truy cập.

Lưu mã vào tệp **AccessDiagnosticsDemo.java**:

~~~java
interface NotificationChannel {
    void send(String message);
}

abstract class BaseChannel implements NotificationChannel {
    protected abstract String format(String message);

    @Override
    public final void send(String message) {
        System.out.println(format(message));
    }
}

final class EmailChannel extends BaseChannel {
    @Override
    protected String format(String message) {
        return "[email] " + message.trim();
    }
}

public class AccessDiagnosticsDemo {
    public static void main(String[] args) {
        NotificationChannel channel = new EmailChannel();
        channel.send("  Xin chào  ");
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~text
javac AccessDiagnosticsDemo.java
java AccessDiagnosticsDemo
~~~

Kết quả stdout chính xác:

~~~text
[email] Xin chào
~~~

Điều gì làm ví dụ này hợp lệ?

1. NotificationChannel yêu cầu send(String) công khai. BaseChannel cung cấp phương thức public send nên đã triển khai hợp đồng interface.
2. BaseChannel khai báo format(String) là protected abstract. Vì BaseChannel là abstract nên có thể để phương thức này cho lớp con thực hiện.
3. EmailChannel triển khai format(String) bằng protected, đúng với mức truy cập ở lớp cha.
4. EmailChannel là lớp concrete và đã cung cấp phần thực hiện cho mọi phương thức abstract còn lại.
5. Biến channel có kiểu NotificationChannel nhưng đối tượng thật là EmailChannel. Lời gọi send vẫn hoạt động nhờ đa hình; mã gọi không cần biết lớp triển khai cụ thể.

## 6. Quy trình chẩn đoán khi javac báo lỗi

Khi gặp lỗi trong một dự án lớn, nên đi theo trình tự sau để tránh sửa sai chỗ:

1. **Đọc tên lớp và phương thức trong thông báo.** Tìm lớp bị báo là not abstract and does not override abstract method, sau đó tìm interface hoặc lớp cha khai báo phương thức đó.
2. **Kiểm tra lớp có phải concrete hay không.** Nếu lớp cần tạo instance, phải triển khai đủ các phương thức abstract. Nếu lớp chỉ là nền tảng chưa hoàn chỉnh, cân nhắc khai báo abstract.
3. **So khớp chữ ký.** So tên, kiểu và thứ tự tham số, kiểu trả về; kiểm tra generic hoặc import nếu hai kiểu có tên giống nhau nhưng thuộc package khác nhau.
4. **Thêm @Override.** Đặt annotation ngay trên phương thức dự định ghi đè để compiler kiểm tra ý định đó.
5. **Kiểm tra phạm vi truy cập.** Phương thức public của interface phải được triển khai public. Phương thức protected ở lớp cha không thể bị thu hẹp xuống private hoặc package-private.
6. **Kiểm tra các ràng buộc override khác.** Kiểu trả về phải tương thích; không thể ném thêm checked exception rộng hơn quy tắc cho phép; không thể override phương thức final. Nếu phương thức cha là private hoặc static, cần xác minh xem đây có thực sự là override hay chỉ là phương thức riêng/che khuất.
7. **Biên dịch lại và đọc lỗi đầu tiên có liên quan.** Một lỗi chữ ký có thể kéo theo nhiều thông báo khác. Sửa nguyên nhân gốc rồi biên dịch lại giúp giảm nhiễu.

Lệnh biên dịch tệp độc lập:

~~~text
javac AccessDiagnosticsDemo.java
~~~

Nếu dự án có nhiều package hoặc phụ thuộc bên ngoài, hãy dùng lệnh build chuẩn của dự án để compiler nhận đúng classpath và source path. Một lỗi “cannot find symbol” do thiếu classpath không giống lỗi chưa triển khai phương thức, nên cần phân loại thông báo trước khi sửa mã.

## 7. Những nhầm lẫn thường gặp

- **Bỏ public khi triển khai interface.** Phương thức trong interface là public theo mặc định nếu khai báo phương thức thông thường không có thân. Lớp triển khai phải giữ mức public.
- **Cho rằng cùng tên phương thức là đủ.** Khác danh sách tham số thường tạo overload, không phải override.
- **Khai báo lớp concrete nhưng để lại phương thức abstract.** Chuyển lớp thành abstract nếu đó là chủ đích; nếu không, hoàn thiện các phương thức còn thiếu.
- **Giảm protected xuống private trong lớp con.** Việc đó vi phạm hợp đồng truy cập của phương thức được override.
- **Xóa @Override để làm biến mất lỗi.** Xóa annotation không sửa chữ ký hoặc phạm vi truy cập; phương thức vẫn có thể không đáp ứng hợp đồng và compiler tiếp tục báo lỗi khác.
- **Sửa phương thức sai lớp.** Nếu lớp cha trừu tượng đã để phương thức cho lớp con thực hiện, hãy tìm đúng lớp concrete bị báo lỗi.
- **Nhầm private với protected.** Private chỉ truy cập trong chính lớp khai báo; protected cho phép truy cập trong package và theo quy tắc kế thừa. Chúng không thể thay thế nhau tùy ý khi override.

## 8. Cách ghi nhớ

Lỗi chưa triển khai phương thức nghĩa là lớp concrete chưa đáp ứng hợp đồng kế thừa. Lỗi sai phạm vi truy cập nghĩa là phương thức ghi đè làm hẹp quyền truy cập so với phương thức cha. Dùng @Override, so khớp chữ ký, kiểm tra lớp có phải abstract và bảo toàn mức truy cập sẽ giải quyết phần lớn trường hợp.

Khi đọc thông báo javac, hãy xác định lớp bị lỗi, tìm phương thức abstract gốc, đối chiếu chữ ký rồi kiểm tra mức truy cập. Sửa đúng nguyên nhân và chạy lại trình biên dịch để xác nhận lớp đã thực hiện đầy đủ hợp đồng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-and-diagnostics-007">Chẩn đoán lỗi ambiguous default, kiểu trả về và erasure</a>

<details>
<summary>Xem chi tiết</summary>

# Chẩn đoán lỗi ambiguous default, kiểu trả về và erasure

## 1. Nhận diện ba nhóm lỗi

- **Ambiguous default:** nhiều interface không liên quan cung cấp cùng một phương thức `default`, khiến compiler không thể tự chọn phần triển khai.
- **Kiểu trả về không tương thích:** các khai báo cùng tên và danh sách tham số nhưng yêu cầu kiểu trả về không thể cùng được thỏa mãn.
- **Erasure/name clash:** các khai báo khác nhau ở tham số generic nhưng sau khi xóa kiểu lại có cùng chữ ký.

Đây là lỗi thời điểm biên dịch. Cần kiểm tra hợp đồng được kế thừa, chữ ký sau khi thay tham số kiểu, quy tắc covariance và type erasure thay vì chỉ nhìn dòng gọi gây lỗi.

## 2. Ambiguous default method

Từ Java 8, interface có thể cung cấp phần thân cho phương thức `default`. Nếu một lớp triển khai hai interface không liên quan và cả hai định nghĩa cùng một phương thức mặc định, lớp nhận hai ứng viên ngang nhau và phải giải quyết xung đột.

Ví dụ cố ý không biên dịch:

```java
interface Left {
    default void ping() { System.out.println("Left"); }
}
interface Right {
    default void ping() { System.out.println("Right"); }
}
class Broken implements Left, Right {
    // Compile error: inherits unrelated defaults for ping()
}
```

### Cách sửa: ghi đè tường minh

Lớp có thể tự định nghĩa phương thức rồi gọi một hoặc cả hai phần thân mặc định qua `InterfaceName.super.method()`:

```java
class Combined implements Left, Right {
    @Override
    public void ping() {
        Left.super.ping();
        Right.super.ping();
        System.out.println("Combined");
    }
}
```

`Left.super.ping()` và `Right.super.ping()` gọi phần triển khai mặc định tương ứng. Chỉ gọi một bên nếu đó là hành vi mong muốn; gọi cả hai có thể lặp tác dụng phụ.

### Quy tắc ưu tiên

1. Phương thức instance phù hợp được kế thừa từ lớp cha thường ưu tiên hơn default của interface. Tuy nhiên, khai báo abstract trong lớp cha có thể buộc lớp concrete cung cấp phần triển khai.
2. Nếu subinterface ghi đè default, định nghĩa ở interface cụ thể hơn được dùng.
3. Hai default không liên quan yêu cầu lớp concrete ghi đè để giải quyết xung đột.
4. Subinterface có thể khai báo lại phương thức abstract, buộc lớp triển khai cung cấp phần thân.

Đừng nhầm xung đột default với overload. Overload có danh sách tham số khác nhau; lỗi này xuất hiện khi cùng một chữ ký được thừa hưởng từ nhiều interface.

## 3. Kiểu trả về không tương thích

### Kiểu trả về không tạo overload

Java không cho khai báo hai phương thức chỉ khác kiểu trả về:

```java
class ReturnOnly {
    int value() { return 1; }
    // Compile error: same name and parameters, different return type
    String value() { return "one"; }
}
```

Tên phương thức và danh sách tham số phân biệt overload; compiler không chọn overload dựa trên kiểu trả về.

### Covariant return

Kiểu trả về reference của phương thức ghi đè có thể là kiểu con của kiểu trong hợp đồng:

```java
interface NumberSource { Number value(); }
interface IntegerSource { Integer value(); }

final class CountSource implements NumberSource, IntegerSource {
    @Override
    public Integer value() { return 42; }
}
```

Đoạn trên hợp lệ vì `Integer` là kiểu con của `Number`. Nếu hai interface yêu cầu `String value()` và `Integer value()`, không có kiểu trả về nào thỏa mãn đồng thời hai kiểu reference không liên quan. Không thể khai báo hai phương thức để phân biệt bằng return type; cần thiết kế lại API hoặc tách hợp đồng.

Với kiểu nguyên thủy như `int` và `long`, không có covariance. Không thể dùng `int` để override phương thức trả về `long` chỉ vì có chuyển đổi số.

### Generic return type

Khi đọc lỗi API generic, hãy thay tham số kiểu vào khai báo trước khi so sánh. Nếu `Source<T>` có `T value()` và được dùng dưới dạng `Source<String>`, kiểu sau substitution là `String`. Override phải đáp ứng hợp đồng đã thay thế và quy tắc return-type-substitutability.

## 4. Type erasure và name clash

Java generics chủ yếu được triển khai bằng **type erasure**. Tham số kiểu bị xóa hoặc thay bằng bound; `T` không có bound tường minh thường được xóa thành `Object`. Metadata generic có thể còn để reflection đọc, nhưng chữ ký bytecode tuân theo quy tắc erasure.

`List<String>` và `List<Integer>` đều có erasure là `List`. Không thể overload chỉ bằng đối số generic này:

```java
import java.util.List;
class Handler {
    void handle(List<String> values) { }
    // Compile error: same erasure as the method above
    void handle(List<Integer> values) { }
}
```

Compiler không thể phân biệt hai phương thức JVM bằng kiểu `String` hoặc `Integer` bên trong `List`, vì tham số kiểu đó không tạo ra hai chữ ký đã xóa khác nhau.

Name clash cũng có thể xảy ra khi hai khai báo sau erasure có cùng chữ ký nhưng không tạo thành quan hệ override hợp lệ. Một trường hợp liên quan là cố triển khai cùng generic interface hai lần với hai tham số kiểu khác nhau, như `Repository<String>` và `Repository<Integer>`; Java không cho phép lớp kế thừa cùng generic interface theo hai cách không tương thích.

### Cách xử lý

- Đổi tên phương thức nếu hai thao tác khác nhau về ý nghĩa.
- Dùng một phương thức generic như `<T> void handle(List<T> values)` nếu thao tác giống nhau cho mọi `T`.
- Dùng wrapper như `StringBatch` và `IntegerBatch` nếu cần hai overload riêng biệt.
- Sửa hierarchy generic để lớp chỉ kế thừa một cách nhất quán cùng hợp đồng generic.
- Không sửa name clash bằng cách chỉ đổi kiểu trả về hoặc tên biến tham số; chúng không đổi erasure.

## 5. Bridge method do compiler sinh

Khi lớp triển khai phương thức generic bằng kiểu cụ thể, compiler có thể sinh **bridge method** để giữ tính đa hình sau erasure. Bridge thường có cờ `bridge` và `synthetic`; lập trình viên không nên tự viết nó.

Ví dụ `Box<T>` khai báo `put(T value)`, còn `StringBox implements Box<String>` triển khai `put(String value)`. Sau erasure, hợp đồng có dạng `put(Object)`, còn lớp cụ thể có `put(String)`. Compiler sinh bridge `put(Object)` để lời gọi qua `Box` được chuyển tiếp đúng tới phương thức cụ thể.

```java
Box<String> box = new StringBox();
box.put("hello");
```

Khi dùng reflection, `Class.getDeclaredMethods()` có thể trả về cả phương thức source lẫn bridge. Kiểm tra `Method.isBridge()` và `Method.isSynthetic()` để phân biệt chúng. Framework và bộ phân tích bytecode cũng cần lưu ý điều này.

## 6. Ví dụ Java 17 hoàn chỉnh

Chương trình sau minh họa cách giải quyết hai default method, kiểu trả về covariant và bridge method do compiler sinh. Lưu toàn bộ code thành `Main.java`:

```java
import java.lang.reflect.Method;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        new Combined().ping();
        System.out.println("covariant value = " + new TextProvider().value());

        Box<String> box = new StringBox();
        box.put("hello");

        boolean hasBridge = Arrays.stream(StringBox.class.getDeclaredMethods())
                .anyMatch(Method::isBridge);
        System.out.println("bridge method generated = " + hasBridge);
    }
}

interface Left {
    default void ping() { System.out.println("Left.ping"); }
}
interface Right {
    default void ping() { System.out.println("Right.ping"); }
}
final class Combined implements Left, Right {
    @Override
    public void ping() {
        Left.super.ping();
        Right.super.ping();
        System.out.println("Combined.ping");
    }
}

interface ValueProvider { Object value(); }
final class TextProvider implements ValueProvider {
    @Override
    public String value() { return "Java 17"; }
}

interface Box<T> { void put(T value); }
final class StringBox implements Box<String> {
    @Override
    public void put(String value) { System.out.println("stored: " + value); }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```text
javac --release 17 Main.java
java Main
```

Stdout chính xác:

```text
Left.ping
Right.ping
Combined.ping
covariant value = Java 17
stored: hello
bridge method generated = true
```

Kết quả bridge được tính bằng `Method.isBridge()`, không phụ thuộc thứ tự reflection trả về các method.

## 7. Quy trình chẩn đoán

1. **Đọc lỗi đầu tiên.** Ghi lại lớp, tên phương thức, interface liên quan và cụm từ compiler như `inherits unrelated defaults`, `return type is incompatible`, `name clash` hoặc `same erasure`.
2. **Viết lại chữ ký.** Ghi tên, kiểu tham số sau substitution và erasure. Không dùng return type để phân biệt overload.
3. **Kiểm tra hierarchy.** Xác định phương thức đến từ superclass, interface cụ thể hơn hay hai interface không liên quan.
4. **Kiểm tra return type.** Xem kiểu reference con có thỏa mãn mọi hợp đồng hay không; phân biệt covariance với chuyển đổi primitive.
5. **Tính erasure.** Thay type variables bằng bound tương ứng rồi so sánh chữ ký. Nếu hai phương thức trở thành cùng chữ ký, đổi tên hoặc tái thiết kế API.
6. **Tạo ví dụ tối thiểu.** Giữ lại các interface, superclass và chữ ký gây lỗi; biên dịch riêng bằng `javac --release 17`.
7. **Chạy lại kiểm thử.** Kiểm tra lời gọi qua cả kiểu cụ thể và interface/base type. Với generic override, xác minh đa hình vẫn đúng.

## 8. Bảng tra nhanh

| Dấu hiệu compiler | Nguyên nhân thường gặp | Hướng xử lý |
| --- | --- | --- |
| `inherits unrelated defaults` | Hai interface không liên quan có cùng default | Override và chọn hành vi; có thể gọi `InterfaceName.super.method()` |
| `return type is incompatible` | Kiểu trả về không thỏa hợp đồng override | Kiểm tra covariance, substitution và thiết kế lại hợp đồng nếu cần |
| Hai phương thức chỉ khác return type | Java không overload theo kiểu trả về | Đổi tên phương thức hoặc thay thiết kế kiểu |
| `name clash` / `same erasure` | Chữ ký generic trùng sau erasure | Đổi tên hoặc thiết kế API generic/wrapper hợp lệ |
| `Method.isBridge() == true` | Compiler sinh bridge để giữ đa hình | Thường không cần sửa; lọc bridge khi phân tích method source |

## 9. Checklist

- Đã đọc khai báo interface và superclass liên quan, không chỉ dòng gọi gây lỗi.
- Đã phân biệt default, abstract và phương thức kế thừa từ lớp cha.
- Đã kiểm tra return type theo quy tắc tương thích, không dựa vào ép kiểu ở nơi gọi.
- Đã tính erasure trước khi thêm overload cho kiểu generic.
- Đã hiểu bridge method là mã compiler sinh và kiểm tra bằng reflection khi cần.
- Đã biên dịch và kiểm thử qua interface/base type sau khi sửa.

Khi chẩn đoán nhóm lỗi này, hãy bắt đầu từ hợp đồng và chữ ký phương thức sau kế thừa, substitution và erasure. Cách đó giúp xác định liệu lỗi nằm ở xung đột ngôn ngữ hay ở thiết kế API.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-and-diagnostics-008">Phân biệt lỗi biên dịch với AbstractMethodError, InstantiationError và linkage error</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt lỗi biên dịch với AbstractMethodError, InstantiationError và linkage error

Khi làm việc với abstract class và interface trong Java, cần phân biệt lỗi mà trình biên dịch phát hiện từ mã nguồn với lỗi liên kết xảy ra khi JVM chạy bytecode. Hai nhóm lỗi có thể xuất phát từ vấn đề thiết kế tương tự, nhưng thời điểm phát hiện và cách chẩn đoán khác nhau.

- **Lỗi biên dịch (compile-time error):** `javac` không thể tạo bytecode hợp lệ từ mã nguồn, chẳng hạn lớp concrete chưa triển khai đủ phương thức abstract hoặc chương trình cố khởi tạo trực tiếp một abstract class.
- **`AbstractMethodError`:** JVM đang thực thi lời gọi phương thức nhưng lớp thực tế không có phần triển khai cụ thể cần thiết cho phương thức đã được phân giải. Trường hợp phổ biến là các class được biên dịch ở những thời điểm hoặc với phiên bản API khác nhau.
- **`InstantiationError`:** bytecode cố tạo instance của một lớp mà phiên bản lớp JVM đang nạp là abstract. Mã nguồn mới được biên dịch thông thường sẽ bị từ chối trước khi tạo bytecode như vậy; lỗi này thường gợi ý sự không khớp giữa các phiên bản class.
- **`LinkageError`:** nhóm lỗi cấp thấp hơn xảy ra khi JVM nạp, liên kết hoặc sử dụng các kiểu không tương thích. `AbstractMethodError` và `InstantiationError` đều thuộc nhánh `LinkageError`.

Nguyên tắc chẩn đoán quan trọng là kiểm tra **bytecode nào đã được biên dịch với phiên bản API nào, và phiên bản class nào thực sự được JVM nạp khi chạy**. Không nên xem các lỗi runtime này như lỗi cú pháp thông thường hoặc cố sửa bằng cách thêm một phương thức ngẫu nhiên mà chưa xác định nguyên nhân.

## 1. Bức tranh tổng quan: lỗi xuất hiện ở giai đoạn nào?

Một chương trình Java thường đi qua các giai đoạn: biên dịch mã nguồn, nạp lớp, liên kết và khởi tạo lớp, sau đó thực thi các câu lệnh. Các giai đoạn này liên quan nhau nhưng không giống nhau.

| Loại lỗi | Thời điểm thường gặp | Ví dụ điển hình |
|---|---|---|
| Lỗi biên dịch | Khi chạy `javac` | Khởi tạo abstract class; lớp concrete bỏ sót phương thức abstract |
| `AbstractMethodError` | Khi lời gọi phương thức được thực thi | Bytecode mong đợi phương thức nhưng lớp thực tế không cung cấp implementation |
| `InstantiationError` | Khi lệnh tạo instance được thực thi | Bytecode dùng `new` với lớp nay đã là abstract |
| `NoSuchMethodError` | Khi JVM phân giải lời gọi phương thức | Mã đã biên dịch gọi phương thức không còn trong phiên bản thư viện đang chạy |
| `NoClassDefFoundError` | Khi JVM cần một class nhưng không thể sử dụng định nghĩa cần thiết | Class có lúc biên dịch nhưng thiếu lúc chạy, hoặc khởi tạo lớp trước đó thất bại |
| `IncompatibleClassChangeError` | Khi loại hoặc cấu trúc thành viên đã đổi theo cách bytecode không mong đợi | Thành viên hoặc kiểu thay đổi không tương thích |
| `VerifyError` | Khi bytecode không vượt qua kiểm tra xác minh | Bytecode không hợp lệ hoặc không đáp ứng ràng buộc của JVM |

Bảng này chỉ ra thời điểm thường gặp, không phải mọi đường đi có thể xảy ra. Một số lỗi liên kết được phát hiện khi lớp được nạp hoặc khi một thành viên lần đầu được sử dụng, nên thời điểm chính xác phụ thuộc vào bytecode và cách JVM thực hiện việc nạp, phân giải.

## 2. Lỗi biên dịch liên quan đến abstract class và interface

Trình biên dịch kiểm tra các quy tắc ngôn ngữ dựa trên mã nguồn và những định nghĩa kiểu nhìn thấy trên classpath lúc biên dịch.

### Khởi tạo abstract class trực tiếp

Ví dụ sau không biên dịch được:

~~~java
abstract class Shape {
    abstract double area();
}

class Demo {
    public static void main(String[] args) {
        Shape shape = new Shape(); // Lỗi biên dịch
    }
}
~~~

Abstract class có thể chứa phương thức chưa triển khai, vì vậy không thể tạo instance trực tiếp của nó. Hãy tạo một lớp con concrete và triển khai các phương thức abstract:

~~~java
abstract class Shape {
    abstract double area();
}

final class Circle extends Shape {
    private final double radius;

    Circle(double radius) {
        this.radius = radius;
    }

    @Override
    double area() {
        return Math.PI * radius * radius;
    }
}

public class ShapeDemo {
    public static void main(String[] args) {
        Shape shape = new Circle(2.0);
        System.out.printf("%.2f%n", shape.area());
    }
}
~~~

Lưu mã trong **ShapeDemo.java**, biên dịch và chạy bằng Java 17 trở lên:

~~~bash
javac ShapeDemo.java
java ShapeDemo
~~~

Kết quả stdout:

~~~text
12.57
~~~

Biến có kiểu tham chiếu `Shape` vẫn có thể trỏ đến đối tượng `Circle`. Điều bị cấm là tạo instance trực tiếp từ abstract class, không phải sử dụng abstract class làm kiểu tham chiếu.

### Lớp concrete chưa triển khai đủ phương thức abstract

~~~java
abstract class Shape {
    abstract double area();
}

class Square extends Shape {
    // Thiếu area() nên lớp concrete này không biên dịch được.
}
~~~

Để hợp lệ, `Square` phải triển khai `area()` hoặc bản thân `Square` phải được khai báo `abstract`. Trình biên dịch thường báo rằng lớp không abstract chưa ghi đè phương thức abstract trong lớp cha.

### Chữ ký override không hợp lệ

~~~java
abstract class Reader {
    abstract String read();
}

class FileReaderImpl extends Reader {
    @Override
    int read() { // Lỗi: kiểu trả về không tương thích
        return 1;
    }
}
~~~

Một lỗi chữ ký như vậy được phát hiện lúc biên dịch. Với kiểu trả về tham chiếu, Java có cho phép kiểu trả về hiệp biến (covariant return type) trong một số trường hợp, nhưng kiểu trả về nguyên thủy phải khớp; các quy tắc override còn bao gồm mức truy cập và ngoại lệ checked.

Các ví dụ này khác về bản chất với `AbstractMethodError`: khi source code nhìn thấy đầy đủ và nhất quán, `javac` ngăn các lỗi hợp đồng thông thường trước khi chương trình chạy.

## 3. LinkageError là gì?

`java.lang.LinkageError` là một lớp `Error` dành cho các vấn đề liên kết giữa những định nghĩa kiểu hoặc thành phần mà JVM cần sử dụng. Những vấn đề này thường xuất hiện khi bytecode được biên dịch với một phiên bản thư viện nhưng chạy với phiên bản khác, hoặc khi classpath chứa các phiên bản không tương thích.

Một phần quan trọng của hệ phân cấp:

~~~text
java.lang.Error
└── java.lang.LinkageError
    └── java.lang.IncompatibleClassChangeError
        ├── java.lang.AbstractMethodError
        └── java.lang.InstantiationError
~~~

Ngoài nhánh này còn có những loại linkage error khác, ví dụ `NoSuchMethodError`, `NoSuchFieldError`, `NoClassDefFoundError` và `VerifyError` nằm trong các nhánh liên quan của `LinkageError`. Không phải mọi lỗi runtime đều là linkage error: `NullPointerException`, `IllegalArgumentException` hay `ClassCastException` có nguyên nhân và hệ phân cấp khác.

Vì `LinkageError` là `Error`, thông thường không nên bắt chung nó để tiếp tục chạy như thể đó là một lỗi nghiệp vụ có thể phục hồi. Trong mã ứng dụng, cách phù hợp thường là chẩn đoán và sửa classpath, phiên bản dependency hoặc tính tương thích của bytecode. Bắt một loại lỗi cụ thể có thể hữu ích trong chương trình minh họa hoặc công cụ chẩn đoán, nhưng không thay thế việc sửa nguyên nhân.

## 4. AbstractMethodError: lời gọi không tìm được implementation cụ thể

`AbstractMethodError` là một lớp con của `IncompatibleClassChangeError`. Lỗi có thể xuất hiện khi mã đã được biên dịch dựa trên một hợp đồng phương thức, nhưng lớp thực tế lúc chạy không cung cấp implementation cụ thể mà lời gọi yêu cầu.

Một tình huống thường gặp là **binary incompatibility** (không tương thích nhị phân): phiên bản API thay đổi sau khi lớp triển khai đã được biên dịch, nhưng lớp triển khai không được biên dịch lại. Ví dụ, một abstract class được bổ sung phương thức abstract mới. Lớp con cũ vẫn có thể được JVM nạp trong một số trường hợp, nhưng khi mã mới gọi phương thức mới trên instance của lớp con cũ, JVM có thể ném `AbstractMethodError`.

Điều này khác với việc biên dịch toàn bộ source code hiện tại. Nếu lớp con được biên dịch lại với abstract method mới mà không triển khai nó và vẫn được khai báo concrete, `javac` sẽ từ chối chương trình. Runtime error xuất hiện vì bytecode cũ không được kiểm tra lại như một lần biên dịch source code mới với toàn bộ định nghĩa hiện tại.

## 5. Tái hiện AbstractMethodError bằng hai phiên bản API

Ví dụ này cố ý tạo ra hai phiên bản của cùng một abstract class để minh họa sự không khớp giữa API mới và lớp triển khai cũ. Không dùng cách bố trí nhiều phiên bản như vậy trong ứng dụng bình thường; đây là bài thực hành chẩn đoán.

Cấu trúc thư mục:

~~~text
abstract-method-demo/
├── src-v1/
│   └── demo/
│       ├── Base.java
│       └── Impl.java
├── src-v2/
│   └── demo/
│       ├── Base.java
│       └── Client.java
└── build/
~~~

Phiên bản 1, tệp **src-v1/demo/Base.java**:

~~~java
package demo;

public abstract class Base {
    public abstract void run();
}
~~~

Phiên bản 1, tệp **src-v1/demo/Impl.java**:

~~~java
package demo;

public final class Impl extends Base {
    @Override
    public void run() {
        System.out.println("old run");
    }
}
~~~

Biên dịch API và implementation cũ:

~~~bash
javac -d build/v1 src-v1/demo/Base.java src-v1/demo/Impl.java
~~~

Giờ API phiên bản 2 thêm một phương thức abstract mới. Tệp **src-v2/demo/Base.java**:

~~~java
package demo;

public abstract class Base {
    public abstract void run();
    public abstract void close();
}
~~~

Tệp **src-v2/demo/Client.java**:

~~~java
package demo;

public final class Client {
    public static void main(String[] args) throws ReflectiveOperationException {
        Base target = (Base) Class.forName("demo.Impl")
                .getDeclaredConstructor()
                .newInstance();

        try {
            target.close();
        } catch (AbstractMethodError error) {
            System.out.println(error.getClass().getSimpleName());
        }
    }
}
~~~

Biên dịch API phiên bản 2 và client bằng API mới:

~~~bash
javac -d build/v2 src-v2/demo/Base.java
javac -cp build/v2 -d build/v2 src-v2/demo/Client.java
~~~

Chạy với API mới đứng trước và implementation cũ đứng sau trên classpath. Trên Linux/macOS, dấu phân cách classpath là dấu hai chấm:

~~~bash
java -cp "build/v2:build/v1" demo.Client
~~~

Trên Windows, dấu phân cách classpath là dấu chấm phẩy:

~~~powershell
java -cp "build/v2;build/v1" demo.Client
~~~

Kết quả stdout:

~~~text
AbstractMethodError
~~~

Trong lần chạy này, JVM nạp `demo.Base` phiên bản 2 nhưng nạp `demo.Impl` đã biên dịch với phiên bản 1. `Impl` có implementation cho `run()` nhưng không có implementation cho `close()`. Lệnh gọi `target.close()` vì vậy gây ra `AbstractMethodError`, được ví dụ bắt riêng để chỉ in tên lớp lỗi.

Cách sửa thực tế là đồng bộ phiên bản API và các implementation: triển khai phương thức mới, biên dịch lại các module phụ thuộc, kiểm tra artifact được đóng gói và loại bỏ bản class cũ khỏi classpath. Không nên chỉ bắt `AbstractMethodError` rồi bỏ qua lời gọi, vì hành vi nghiệp vụ cần thiết có thể chưa được thực hiện.

## 6. InstantiationError: bytecode tạo instance của lớp nay đã abstract

`InstantiationError` cũng là một lớp con của `IncompatibleClassChangeError`. Nó có thể xảy ra khi bytecode chứa thao tác tạo instance cho một lớp, nhưng định nghĩa mà JVM nạp lúc chạy đã trở thành abstract.

Trong source code được biên dịch với định nghĩa mới, câu lệnh `new SomeAbstractClass()` không hợp lệ và bị `javac` từ chối. Nhưng nếu một client được biên dịch lúc lớp còn concrete, bytecode cũ vẫn có thể chứa lệnh `new`. Khi runtime thay định nghĩa lớp đó bằng một phiên bản abstract không tương thích, lệnh tạo instance có thể ném `InstantiationError`.

## 7. Tái hiện InstantiationError với phiên bản lớp không khớp

Phiên bản 1, tệp **src-v1/demo/Product.java**:

~~~java
package demo;

public class Product {
    public Product() {
    }
}
~~~

Phiên bản 1, tệp **src-v1/demo/Client.java**:

~~~java
package demo;

public final class Client {
    public static void main(String[] args) {
        try {
            new Product();
            System.out.println("created");
        } catch (InstantiationError error) {
            System.out.println(error.getClass().getSimpleName());
        }
    }
}
~~~

Biên dịch cả client và lớp concrete ban đầu:

~~~bash
javac -d build/v1 src-v1/demo/Product.java src-v1/demo/Client.java
~~~

Phiên bản 2, thay **src-v2/demo/Product.java** bằng một abstract class:

~~~java
package demo;

public abstract class Product {
    public Product() {
    }

    public abstract void execute();
}
~~~

Biên dịch phiên bản lớp mới vào thư mục riêng:

~~~bash
javac -d build/v2 src-v2/demo/Product.java
~~~

Chạy client cũ với lớp mới đứng trước trên classpath. Trên Linux/macOS:

~~~bash
java -cp "build/v2:build/v1" demo.Client
~~~

Trên Windows:

~~~powershell
java -cp "build/v2;build/v1" demo.Client
~~~

Kết quả stdout:

~~~text
InstantiationError
~~~

Client được biên dịch khi `Product` còn là lớp concrete, vì vậy bytecode của nó có thao tác tạo instance. Khi chạy, JVM tìm thấy `Product` phiên bản 2, hiện là abstract, và không thể thực hiện thao tác đó.

Để khắc phục, hãy đồng bộ client và dependency với API hiện tại. Nếu `Product` được chuyển thành abstract theo chủ đích, client phải tạo một lớp con concrete thích hợp thay vì tiếp tục khởi tạo `Product` trực tiếp. Với bản phát hành thông thường, cần build và triển khai các artifact tương thích cùng nhau, tránh để một phiên bản class cũ được ưu tiên trên classpath.

## 8. Những linkage error khác cần phân biệt

Không phải mọi lỗi do phiên bản thư viện không khớp đều là `AbstractMethodError` hoặc `InstantiationError`.

- **`NoSuchMethodError`:** bytecode gọi một phương thức mà định nghĩa lớp/interface đang chạy không có chữ ký được phân giải. Hãy kiểm tra thay đổi API, phiên bản dependency và các bản JAR trùng lặp.
- **`NoSuchFieldError`:** bytecode tham chiếu đến một trường đã không còn hoặc không còn phù hợp trong lớp lúc chạy.
- **`NoClassDefFoundError`:** JVM không thể sử dụng định nghĩa lớp cần thiết khi chạy. Có thể thiếu class trên runtime classpath, hoặc lần khởi tạo trước đó của lớp thất bại. Trường hợp thiếu class thường cần đối chiếu với `ClassNotFoundException`, nhưng hai lỗi không đồng nghĩa.
- **`IncompatibleClassChangeError`:** bytecode mong đợi một cấu trúc kiểu hoặc thành viên khác với cấu trúc được cung cấp lúc chạy; các lớp con cụ thể bao gồm `AbstractMethodError` và `InstantiationError`.
- **`VerifyError`:** bytecode không vượt qua xác minh của JVM, thường vì bytecode không hợp lệ hoặc có vấn đề tương thích ở mức bytecode.
- **`ExceptionInInitializerError`:** quá trình khởi tạo tĩnh của lớp thất bại do ngoại lệ; đây cũng là một lớp con của `LinkageError`, nhưng thường cần điều tra lỗi bên trong static initializer thay vì chỉ tìm chữ ký API bị thiếu.

Thông báo và stack trace cung cấp manh mối ban đầu, nhưng cần kiểm tra đúng class và JAR mà runtime thực sự nạp. Dòng đầu stack trace thường chỉ ra nơi lỗi được quan sát; nguyên nhân gốc có thể nằm ở dependency hoặc artifact đã được nạp trước đó.

## 9. Vì sao mã biên dịch được nhưng chạy lại lỗi?

Trình biên dịch và JVM có thể sử dụng các định nghĩa lớp khác nhau vào hai thời điểm khác nhau. Ví dụ:

1. Thư viện phiên bản 1 khai báo một abstract class hoặc interface.
2. Một module được biên dịch với phiên bản 1 và tạo ra bytecode.
3. Thư viện được nâng cấp lên phiên bản 2, có thay đổi không tương thích nhị phân.
4. Module cũ vẫn được triển khai mà không biên dịch lại.
5. Khi đường thực thi đi đến thành viên bị ảnh hưởng, JVM phát hiện sự không khớp và có thể ném linkage error.

Các nguồn gốc thường gặp gồm:

- Một JAR cũ bị đóng gói cùng JAR mới.
- Thứ tự classpath khiến JVM nạp phiên bản không mong muốn.
- Một module được biên dịch lại nhưng module triển khai chưa được cập nhật.
- Thay đổi một phương thức concrete thành abstract hoặc bổ sung hợp đồng abstract mà các lớp triển khai cũ chưa đáp ứng.
- Thay đổi loại thành viên hoặc chữ ký phương thức mà bytecode đã biên dịch phụ thuộc vào.
- Môi trường build và môi trường triển khai dùng tập dependency khác nhau.

Không phải mọi thay đổi API đều gây lỗi linkage. Java có những quy tắc tương thích nhị phân cụ thể, và kết quả phụ thuộc vào loại thay đổi, kiểu thành viên và đường thực thi. Khi một lỗi xuất hiện, hãy xác minh trường hợp thực tế thay vì kết luận chỉ từ tên loại lỗi.

## 10. Quy trình chẩn đoán có hệ thống

### Bước 1: Xác định chính xác loại lỗi

Đọc tên lớp ngoại lệ/lỗi đầu tiên trong stack trace và xác định lỗi xảy ra trong lúc biên dịch hay lúc chạy. Nếu `javac` báo lỗi, hãy giải quyết mã nguồn và hợp đồng kiểu. Nếu JVM ném `LinkageError`, tập trung vào bytecode và các phiên bản class đang được sử dụng.

### Bước 2: Tìm điểm sử dụng đầu tiên bị lỗi

Với `AbstractMethodError`, tìm lời gọi phương thức cụ thể và xác định kiểu khai báo cùng kiểu thực tế của đối tượng. Với `InstantiationError`, tìm lệnh `new` hoặc đường tạo instance liên quan. Dùng stack trace để thu hẹp phạm vi, nhưng vẫn kiểm tra nguồn gốc dependency.

### Bước 3: Xác minh phiên bản class runtime

Kiểm tra runtime classpath, thứ tự JAR, artifact được đóng gói và phiên bản thư viện ở môi trường triển khai. Trong chương trình chẩn đoán, có thể in vị trí class được nạp:

~~~java
System.out.println(
    SomeType.class.getProtectionDomain()
        .getCodeSource()
        .getLocation()
);
~~~

Đoạn này chỉ dùng khi `CodeSource` và thông tin lớp khả dụng; một số môi trường có thể trả về `null`. Hãy xác định đúng kiểu đang nghi ngờ và kiểm tra các lớp liên quan, không chỉ một lớp duy nhất.

### Bước 4: So sánh hợp đồng giữa phiên bản build và runtime

Kiểm tra phương thức abstract, mức truy cập, chữ ký, kiểu trả về, trạng thái abstract/concrete và thay đổi API. Xác định implementation nào được biên dịch với hợp đồng cũ và liệu nó có được triển khai cùng API mới hay không.

### Bước 5: Build sạch và triển khai đồng bộ

Xóa output build cũ thông qua quy trình build chuẩn của dự án, biên dịch lại các module liên quan và tạo artifact nhất quán. Tránh tự xóa ngẫu nhiên các file trong môi trường đang chạy trước khi xác định cách triển khai. Sau đó chạy lại trong môi trường có classpath tương ứng với production.

### Bước 6: Thêm kiểm thử tương thích

Nếu thư viện được nhiều module sử dụng, thêm kiểm thử tích hợp hoặc kiểm tra tương thích API để phát hiện các thay đổi làm implementation cũ không còn hoạt động. Kiểm thử phải sử dụng cùng cách đóng gói và dependency resolution với ứng dụng thật; chỉ chạy unit test trong một classpath khác có thể không tái hiện vấn đề.

## 11. Cách xử lý theo loại lỗi

| Phát hiện | Hướng xử lý |
|---|---|
| Lỗi biên dịch do lớp concrete bỏ sót abstract method | Triển khai đúng phương thức hoặc khai báo lớp là abstract nếu thiết kế yêu cầu |
| Lỗi biên dịch do khởi tạo abstract class | Khởi tạo lớp con concrete hoặc dùng factory trả về một implementation phù hợp |
| `AbstractMethodError` sau khi nâng cấp dependency | Tìm implementation cũ, đồng bộ phiên bản, triển khai phương thức mới và build lại các module liên quan |
| `InstantiationError` sau khi API đổi từ concrete sang abstract | Cập nhật client để tạo lớp con concrete; kiểm tra classpath và artifact cũ |
| `NoSuchMethodError` hoặc `NoSuchFieldError` | So sánh chữ ký/thành viên trong bản build với bản runtime và loại bỏ dependency không tương thích |
| `NoClassDefFoundError` | Kiểm tra classpath runtime và nguyên nhân khởi tạo lớp thất bại nếu có |
| `VerifyError` | Xác minh nguồn bytecode, công cụ biến đổi bytecode và tính nhất quán của các artifact |

Tránh giải quyết các lỗi này bằng cách thêm catch tổng quát quanh toàn bộ ứng dụng, bỏ qua lời gọi hoặc chỉ thay đổi mã nguồn ở một module. Nếu vấn đề là binary incompatibility, mã nguồn đã sửa nhưng artifact runtime cũ vẫn còn trên classpath thì lỗi có thể tiếp tục xảy ra.

## 12. Các ngộ nhận thường gặp

- **“AbstractMethodError nghĩa là tôi quên override.”** Nếu toàn bộ source code hiện tại được biên dịch cùng nhau, việc thiếu override trong lớp concrete thường bị phát hiện khi biên dịch. Khi gặp lỗi runtime này, hãy kiểm tra phiên bản bytecode và API.
- **“InstantiationError giống lỗi `new AbstractClass()` trong source.”** Source code đó bị `javac` từ chối. `InstantiationError` thường cho thấy bytecode cũ đang chạy với một định nghĩa lớp mới đã trở thành abstract.
- **“Mọi LinkageError đều do thiếu JAR.”** Thiếu class là một nguyên nhân, nhưng thay đổi chữ ký, thay đổi cấu trúc kiểu hoặc phiên bản class không tương thích cũng có thể gây lỗi.
- **“Build thành công nghĩa là runtime chắc chắn tương thích.”** Build chỉ kiểm tra theo các định nghĩa được dùng lúc build. Môi trường chạy có thể nạp các artifact khác.
- **“Có thể bắt LinkageError rồi tiếp tục bình thường.”** Tiếp tục sau khi hợp đồng kiểu bị phá vỡ có thể khiến thao tác nghiệp vụ không thực hiện hoặc trạng thái chương trình không đáng tin cậy.
- **“Chỉ cần kiểm tra phiên bản trong file build.”** Cần xác minh artifact thực tế được đóng gói và nạp lúc chạy, vì classpath có thể chứa nhiều phiên bản hoặc nguồn lớp khác nhau.

## 13. Checklist khi gặp lỗi abstract/linkage

- Xác nhận lỗi là compile-time error hay lỗi runtime.
- Ghi lại loại lỗi và vị trí lời gọi trong stack trace.
- Với `AbstractMethodError`, xác định phương thức được gọi, kiểu runtime của đối tượng và implementation đang được nạp.
- Với `InstantiationError`, xác định lớp bị khởi tạo và kiểm tra xem phiên bản runtime có còn concrete không.
- Kiểm tra thứ tự classpath, dependency tree và các JAR trùng phiên bản.
- So sánh API được dùng khi biên dịch với API thực tế lúc chạy.
- Đồng bộ phiên bản và build lại các module phụ thuộc; chạy lại kiểm thử trong môi trường triển khai tương đương.
- Ghi nhận bài học vào quy trình phát hành để tránh trộn artifact cũ và mới.

Phân biệt lỗi biên dịch với `AbstractMethodError`, `InstantiationError` và các `LinkageError` giúp chọn đúng hướng điều tra. Lỗi biên dịch thường chỉ ra source code chưa thỏa hợp đồng kiểu hiện tại; linkage error thường yêu cầu kiểm tra quan hệ giữa bytecode đã biên dịch và định nghĩa lớp thực tế lúc chạy. Hãy sửa tính nhất quán của API, implementation và artifact triển khai, thay vì chỉ xử lý biểu hiện của lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-and-diagnostics-009">Kiểm tra bytecode bằng javap: abstract flag, interface flag và bridge method</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra bytecode bằng javap: abstract flag, interface flag và bridge method

Khi đọc mã nguồn Java, ta thấy lớp trừu tượng, interface và các phương thức generic ở mức ngôn ngữ. Sau khi biên dịch, JVM làm việc với các tệp class, descriptor và cờ truy cập. Công cụ **javap** giúp kiểm tra những gì trình biên dịch thực sự tạo ra, đặc biệt là ba dấu hiệu **ACC_ABSTRACT**, **ACC_INTERFACE** và **ACC_BRIDGE**.

Bài này dùng một ví dụ nhỏ có thể biên dịch bằng Java 17+, rồi đọc phần bytecode liên quan thay vì suy luận chỉ từ mã nguồn.

## 1. javap dùng để làm gì?

**javap** là công cụ đi kèm JDK để phân tích tệp class. Một số tùy chọn hữu ích:

| Tùy chọn | Tác dụng |
|---|---|
| **-p** | Hiển thị cả thành viên private, ngoài các thành viên thông thường |
| **-v** | Hiển thị thông tin chi tiết: phiên bản class file, flags, constant pool và attributes |
| **-s** | Hiển thị descriptor nội bộ của lớp và phương thức |
| **-c** | Hiển thị các lệnh bytecode trong phần Code |
| **-classpath** hoặc **-cp** | Chỉ định nơi tìm các tệp class |

Ví dụ, **javap -v -p StringMapper** cho biết các flags của lớp và phương thức, còn **javap -c -p StringMapper** giúp nhìn các lệnh của phương thức cầu nối do compiler sinh ra. Có thể kết hợp các tùy chọn, chẳng hạn **javap -v -p -c StringMapper**.

Hãy chạy javap trên tệp **.class** đã biên dịch. Nếu vừa sửa mã nguồn nhưng chưa biên dịch lại, kết quả javap vẫn phản ánh phiên bản class cũ.

## 2. Các flag cần phân biệt

Flags là các bit trong cấu trúc class file. javap thường trình bày chúng dưới dạng giá trị hex và danh sách tên dễ đọc.

| Flag | Giá trị | Ngữ cảnh | Ý nghĩa |
|---|---:|---|---|
| **ACC_ABSTRACT** | **0x0400** | Lớp hoặc phương thức | Lớp không thể khởi tạo trực tiếp; với phương thức, phần thân được để trống và lớp cụ thể phải cung cấp cách triển khai khi cần |
| **ACC_INTERFACE** | **0x0200** | Lớp file biểu diễn interface | Đánh dấu kiểu đó là interface |
| **ACC_BRIDGE** | **0x0040** | Phương thức | Đánh dấu phương thức cầu nối do compiler sinh ra để bảo toàn cách dispatch, thường gặp cùng generic và type erasure |
| **ACC_SYNTHETIC** | **0x1000** | Thành viên | Đánh dấu thành viên được compiler sinh ra, không tương ứng trực tiếp với khai báo tường minh trong mã nguồn |
| **ACC_PUBLIC** | **0x0001** | Lớp hoặc thành viên | Thành viên có thể truy cập công khai; với lớp top-level, chỉ xuất hiện nếu lớp được khai báo public |
| **ACC_SUPER** | **0x0020** | Lớp thông thường | Flag class file thường thấy ở lớp Java; không có nghĩa là lớp abstract hoặc có lớp cha do ta tự khai báo |

Một điểm quan trọng: cách diễn giải flag phụ thuộc ngữ cảnh. Không nên đọc một giá trị bit tách khỏi việc nó đang thuộc flags của lớp, phương thức hay trường.

**ACC_ABSTRACT** và **ACC_INTERFACE** không đồng nghĩa. Một interface có flag lớp **ACC_INTERFACE** và cũng có **ACC_ABSTRACT** ở cấp class file. Một phương thức abstract có **ACC_ABSTRACT** ở flags của chính phương thức. Một lớp abstract có **ACC_ABSTRACT** ở flags của lớp, nhưng không có **ACC_INTERFACE**.

Interface vẫn mang flag lớp abstract ngay cả khi có phương thức **default**, **static** hoặc **private** có phần thân. Các phương thức interface có phần thân không nhất thiết mang **ACC_ABSTRACT**.

## 3. Ví dụ Java 17+ có thể chạy

Tạo tệp tên **BytecodeFlagsDemo.java** với nội dung sau. Interface và lớp abstract trong ví dụ được đặt cùng tệp để việc thử nghiệm đơn giản hơn.

~~~java
interface Mapper<T> {
    T map(T value);
}

abstract class Base {
    public abstract Number value();
}

final class StringMapper implements Mapper<String> {
    @Override
    public String map(String value) {
        return value.trim();
    }
}

public class BytecodeFlagsDemo {
    public static void main(String[] args) {
        Mapper<String> mapper = new StringMapper();
        System.out.println(mapper.map("  Java  "));
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~bash
javac --release 17 BytecodeFlagsDemo.java
java BytecodeFlagsDemo
~~~

Stdout:

~~~text
Java
~~~

Tùy chọn **--release 17** yêu cầu compiler tạo bytecode tương thích Java 17 và giới hạn API theo phiên bản đó. Khi dùng compiler Java 17, class file có major version **61**.

Lệnh biên dịch sẽ tạo các tệp class, gồm **Mapper.class**, **Base.class**, **StringMapper.class** và **BytecodeFlagsDemo.class**. Mỗi tệp class mô tả một kiểu riêng; vì vậy cần kiểm tra đúng tên lớp khi chạy javap.

## 4. Kiểm tra abstract flag của lớp và phương thức

Chạy:

~~~bash
javap -v -p Base
~~~

Trong phần đầu output, hãy tìm flags của lớp. Phần liên quan sẽ có dạng:

~~~text
abstract class Base
  ...
  flags: (0x0420) ACC_SUPER, ACC_ABSTRACT
~~~

Lớp **Base** có **ACC_ABSTRACT**, vì được khai báo abstract. Flag **ACC_SUPER** là flag thông thường của lớp Java và không có nghĩa rằng Base là abstract.

Tiếp theo, tìm phương thức **value()**:

~~~text
public abstract java.lang.Number value();
  descriptor: ()Ljava/lang/Number;
  flags: (0x0401) ACC_PUBLIC, ACC_ABSTRACT
~~~

Phương thức không có phần thân Java, nên class file không chứa một phần Code thông thường cho phương thức này. Descriptor **()Ljava/lang/Number;** nghĩa là không có tham số và kiểu trả về là **java.lang.Number**.

Một lớp cụ thể kế thừa Base phải triển khai phương thức abstract còn thiếu, nếu không lớp đó cũng phải được khai báo abstract. Việc nhìn flag ở cấp lớp và flag ở cấp phương thức giúp xác định hai khái niệm riêng này.

## 5. Kiểm tra interface flag

Chạy:

~~~bash
javap -v -p Mapper
~~~

Phần đầu output sẽ thể hiện kiểu là interface và các flags cấp lớp:

~~~text
interface Mapper<T>
  ...
  flags: (0x0600) ACC_INTERFACE, ACC_ABSTRACT
~~~

Đây là dấu hiệu chính để nhận biết class file đang biểu diễn một interface. Giá trị **0x0600** là tổng của **ACC_INTERFACE (0x0200)** và **ACC_ABSTRACT (0x0400)**.

Phương thức generic trong interface có dạng:

~~~text
public abstract T map(T);
  descriptor: (Ljava/lang/Object;)Ljava/lang/Object;
  flags: (0x0401) ACC_PUBLIC, ACC_ABSTRACT
  Signature: #... // (TT;)TT;
~~~

Dòng **descriptor** và dòng **Signature** trả lời hai câu hỏi khác nhau:

- **Descriptor** là kiểu dùng cho liên kết và gọi ở cấp JVM. Sau type erasure, tham số T và kết quả T của phương thức này đều trở thành **java.lang.Object**.
- **Signature** giữ lại thông tin generic để công cụ và compiler có thể biểu diễn kiểu nguồn **T**. Chỉ số constant pool trong output thật sẽ phụ thuộc quá trình biên dịch; dấu **#...** ở đây là phần lược bỏ minh họa, không phải văn bản literal của javap.

Đừng kết luận rằng interface không có phương thức thực thi chỉ vì class file có **ACC_ABSTRACT**. Flag này mô tả kiểu interface ở cấp lớp; từng phương thức vẫn có flags riêng. Phương thức abstract không có phần thân, còn phương thức default hoặc static có phần thân và không mang **ACC_ABSTRACT** ở cấp phương thức.

## 6. Vì sao compiler tạo bridge method?

Java generic chủ yếu được triển khai bằng **type erasure**. Trong ví dụ, khai báo nguồn là **Mapper&lt;T&gt;** với phương thức **T map(T value)**. Sau erasure, descriptor của phương thức trong interface là:

~~~text
(Ljava/lang/Object;)Ljava/lang/Object;
~~~

Nhưng lớp **StringMapper** triển khai phương thức chuyên biệt:

~~~java
public String map(String value)
~~~

Descriptor của phương thức này là:

~~~text
(Ljava/lang/String;)Ljava/lang/String;
~~~

Hai descriptor khác nhau. Ở cấp JVM, phương thức nhận Object không giống phương thức nhận String. Để lời gọi theo hợp đồng đã bị xóa kiểu vẫn được dispatch đến phần triển khai chuyên biệt, compiler sinh một **bridge method** có descriptor đã xóa kiểu. Phương thức cầu nối kiểm tra và chuyển đối số rồi gọi phương thức thực sự trong StringMapper.

Bridge method không cần được viết trong mã nguồn. Nó là một thành viên bổ sung ở bytecode để giữ hành vi đa hình khi generic bị xóa kiểu. Cùng ý tưởng này cũng có thể xuất hiện với một số trường hợp override có kiểu trả về hiệp biến.

## 7. Dùng javap để tìm bridge method

Chạy:

~~~bash
javap -v -p StringMapper
~~~

Trong output, sẽ thấy phương thức nguồn:

~~~text
public java.lang.String map(java.lang.String);
  descriptor: (Ljava/lang/String;)Ljava/lang/String;
  flags: (0x0001) ACC_PUBLIC
~~~

Và một phương thức bổ sung có dạng:

~~~text
public java.lang.Object map(java.lang.Object);
  descriptor: (Ljava/lang/Object;)Ljava/lang/Object;
  flags: (0x1041) ACC_PUBLIC, ACC_BRIDGE, ACC_SYNTHETIC
~~~

Ba flag trên có thể đọc như sau:

- **ACC_PUBLIC**: bridge có thể được gọi theo phạm vi truy cập công khai.
- **ACC_BRIDGE**: đây là phương thức cầu nối.
- **ACC_SYNTHETIC**: compiler đánh dấu phương thức này là thành viên được sinh ra.

Giá trị **0x1041** tương ứng **0x0001 + 0x0040 + 0x1000**. Hãy nhìn cả danh sách tên flag thay vì chỉ ghi nhớ con số.

Vì output chi tiết có thể dài, hãy tập trung vào tên phương thức, descriptor, flags và phần Code của phương thức nhận Object. Các số chỉ mục constant pool có thể khác giữa các bản biên dịch; điều cần đối chiếu là descriptor và hành vi.

## 8. Đọc phần Code của bridge

Có thể yêu cầu javap hiển thị bytecode:

~~~bash
javap -c -p StringMapper
~~~

Bridge method sẽ có các lệnh tương đương đoạn sau; chỉ số constant pool và phần chú thích có thể khác:

~~~text
public java.lang.Object map(java.lang.Object);
  Code:
     0: aload_0
     1: aload_1
     2: checkcast     #... // class java/lang/String
     5: invokevirtual #... // Method map:(Ljava/lang/String;)Ljava/lang/String;
     8: areturn
~~~

Đọc theo thứ tự:

1. **aload_0** đẩy đối tượng hiện tại lên operand stack.
2. **aload_1** đẩy tham số Object lên stack.
3. **checkcast java/lang/String** kiểm tra và chuyển kiểu tham số sang String. Nếu caller truyền một kiểu không phù hợp, lệnh này có thể ném **ClassCastException**.
4. **invokevirtual map(String)** gọi phần triển khai thực sự trong StringMapper.
5. **areturn** trả về tham chiếu kết quả. String là kiểu tham chiếu, vì vậy dùng lệnh trả về tham chiếu.

Bridge method không phải một bản sao chứa logic nghiệp vụ độc lập. Nó chuyển lời gọi theo descriptor đã xóa kiểu sang phương thức chuyên biệt. Đó là lý do phương thức bridge thường ngắn và có các lệnh cast cùng gọi phương thức khác.

## 9. Quy trình chẩn đoán bằng javap

Khi cần xác nhận compiler đã tạo đúng cấu trúc:

1. **Biên dịch lại** mã nguồn bằng JDK và tùy chọn phiên bản mong muốn.
2. **Kiểm tra đúng class** bằng javap. Nếu class nằm trong package hoặc thư mục build riêng, truyền đúng tên đầy đủ hoặc classpath.
3. **Dùng -v** để xem flags và attributes.
4. **Dùng -s** để đối chiếu descriptor, nhất là khi generic bị xóa kiểu.
5. **Dùng -c** để kiểm tra bridge thực hiện cast và gọi phương thức nào.
6. **So sánh phương thức nguồn và bridge**: tên có thể giống nhau, nhưng tham số hoặc kiểu trả về trong descriptor khác nhau.
7. **Xác nhận flag ở đúng cấp**: flag lớp không thay thế flag phương thức, và flag của bridge không phải flag của cả lớp.

Một lệnh hữu ích để kiểm tra nhanh descriptor và bytecode là:

~~~bash
javap -p -s -c StringMapper
~~~

Nếu cần xem flags chi tiết, dùng thêm **-v**. Để xem các thành viên không public, giữ **-p**; nếu thiếu tùy chọn này, một số thông tin có thể không xuất hiện như mong đợi.

## 10. Những nhầm lẫn thường gặp

- **Nhầm ACC_ABSTRACT với ACC_INTERFACE:** interface mang cả hai flag ở cấp lớp, còn một abstract class có ACC_ABSTRACT mà không có ACC_INTERFACE.
- **Chỉ nhìn tên phương thức:** hai phương thức cùng tên có thể khác descriptor. Hãy kiểm tra tham số, kiểu trả về và descriptor nội bộ.
- **Cho rằng phương thức bridge được viết trong source:** bridge thường do compiler tạo. Dùng javap hoặc Reflection API để nhận diện.
- **Cho rằng ACC_SYNTHETIC đồng nghĩa với ACC_BRIDGE:** synthetic là nhãn tổng quát cho thành viên do compiler sinh; bridge là một loại phương thức có mục đích cụ thể.
- **Đọc Signature như descriptor:** Signature có thể giữ kiểu generic, còn descriptor phản ánh kiểu sau erasure được dùng trong liên kết JVM.
- **Không biên dịch lại sau khi sửa source:** javap đọc tệp class trên đĩa, nên có thể hiển thị kết quả cũ.
- **So sánh số constant pool giữa các bản build:** các chỉ số như **#7** không phải định danh ổn định. Hãy so sánh tên phương thức, descriptor, flags và lệnh bytecode.
- **Cho rằng mọi interface method đều abstract:** phương thức default, static và private có phần thân; hãy xem flags của từng phương thức.
- **Bỏ qua ClassCastException trong bridge:** bridge có thể cast từ Object về kiểu chuyên biệt. Lời gọi thô (raw type) hoặc mã không an toàn với generic có thể truyền đối số sai kiểu.

## 11. Checklist đọc kết quả

- Lớp abstract có **ACC_ABSTRACT** ở flags cấp lớp.
- Interface có **ACC_INTERFACE** và **ACC_ABSTRACT** ở flags cấp lớp.
- Phương thức abstract có **ACC_ABSTRACT** ở flags của phương thức.
- Phương thức nguồn và bridge được so sánh bằng descriptor, không chỉ bằng tên.
- Bridge do compiler sinh thường có **ACC_BRIDGE** và **ACC_SYNTHETIC**.
- Phần Code của bridge cho thấy cách nó chuyển đối số và gọi phương thức chuyên biệt.
- Đã biên dịch lại và kiểm tra đúng tệp class.

Khi phân tích bytecode, hãy luôn xác định **flag thuộc về lớp hay phương thức**, rồi đối chiếu với **descriptor** và **Code**. Ba loại thông tin này kết hợp với nhau sẽ giải thích rõ vì sao một lớp abstract, một interface hoặc một phương thức cầu nối xuất hiện trong kết quả biên dịch.

</details>

- [Quay lại đầu trang](#back-to-top)
