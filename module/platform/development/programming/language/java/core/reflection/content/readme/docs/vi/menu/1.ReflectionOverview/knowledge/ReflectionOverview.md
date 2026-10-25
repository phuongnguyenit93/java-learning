<a id="back-to-top"></a>

# Tổng quan

## Menu
- [Reflection là gì và vấn đề cần giải quyết](#reflection-overview-001)
- [Phân biệt thông tin kiểu lúc biên dịch và lúc chạy](#reflection-overview-002)
- [Quan sát metadata khác thực thi hoặc thay đổi đối tượng](#reflection-overview-003)
- [Vai trò của Class và java.lang.reflect](#reflection-overview-004)
- [Quan hệ giữa reflection, kế thừa, generic và annotation](#reflection-overview-005)
- [Khi nào nên dùng reflection và khi nào gọi trực tiếp](#reflection-overview-006)
- [Một ví dụ xuyên suốt: khám phá và sử dụng một kiểu chưa biết trước](#reflection-overview-007)
- [Kiến thức tiên quyết, phạm vi Java Core và trình tự học đề xuất](#reflection-overview-008)

## <a id="reflection-overview-001">Reflection là gì và vấn đề cần giải quyết</a>

<details>
<summary>Xem chi tiết</summary>

# Reflection trong Java: Là gì và vấn đề cần giải quyết?

Reflection (phản chiếu) là cơ chế cho phép chương trình Java **tìm hiểu cấu trúc của kiểu dữ liệu và thao tác với một số thành phần của kiểu đó khi chương trình đang chạy**. Thay vì chỉ gọi trực tiếp những lớp, phương thức đã được viết rõ trong mã nguồn, chương trình có thể tra cứu thông tin từ một đối tượng `Class<?>`, tìm constructor/phương thức/field theo tên hoặc đặc điểm, rồi tạo đối tượng, gọi phương thức hay truy cập giá trị khi phù hợp.

Bài này tập trung vào **lý do Reflection tồn tại, cách tư duy về nó, quy trình sử dụng cơ bản và chi phí đánh đổi**. Mục tiêu là hiểu khi nào cần một cơ chế động; chưa cần dùng Reflection để can thiệp sâu vào thành phần `private`.

## 1. Vấn đề: mã nguồn không phải lúc nào cũng biết trước mọi kiểu

Với Java thông thường, lập trình viên viết rõ kiểu và lời gọi:

```java
Greeter greeter = new Greeter();
String message = greeter.sayHello("An");
```

Compiler kiểm tra rằng `Greeter` tồn tại và có phương thức `sayHello(String)`. Đây là cách nên ưu tiên vì rõ ràng, được kiểm tra kiểu lúc biên dịch và dễ bảo trì.

Nhưng có những chương trình **chỉ biết phải xử lý lớp nào tại runtime**:

- Một thư viện chuyển đối tượng thành JSON cần biết đối tượng thực tế có những thuộc tính nào.
- Một bộ chạy kiểm thử cần tìm các phương thức được đánh dấu bằng annotation và gọi từng phương thức phù hợp.
- Một container dependency injection cần tìm constructor hoặc metadata để tạo và nối các đối tượng.
- Một hệ thống plugin có thể nhận tên lớp/cấu hình do người dùng chọn sau khi chương trình đã được biên dịch.
- Một công cụ quan sát chương trình cần in tên lớp, phương thức hoặc field mà không viết riêng cho từng lớp.

Ví dụ, khi đang viết thư viện JSON, tác giả không thể biết trước toàn bộ lớp `Customer`, `Order`, `Product` ... mà ứng dụng tương lai sẽ đưa vào. Viết `if/else` cho mọi lớp không khả thi. Thư viện cần một cách chung để **khám phá metadata của bất kỳ kiểu được hỗ trợ** và xử lý theo quy tắc.

Reflection giải quyết chính nhu cầu đó: **làm việc với cấu trúc chương trình khi cấu trúc cụ thể chỉ được xác định lúc chạy**.

## 2. Reflection là gì?

Reflection API thuộc chủ yếu về:

- `java.lang.Class`: đại diện runtime của một kiểu Java và là điểm bắt đầu để khám phá metadata.
- `java.lang.reflect`: các đối tượng mô tả thành phần của kiểu, tiêu biểu `Constructor`, `Method`, `Field`, `Modifier`.
- `java.lang.annotation` và `java.lang.reflect.AnnotatedElement`: hỗ trợ tra cứu annotation tại runtime khi annotation được khai báo với retention phù hợp.

Một đối tượng thông thường biểu diễn **dữ liệu/trạng thái**, còn `Class<?>` biểu diễn **thông tin về kiểu** của đối tượng đó. Ví dụ:

```java
String text = "Hello";
Class<?> type = text.getClass();
System.out.println(type.getName()); // java.lang.String
```

Ba cách thường gặp để có `Class<?>`:

| Cách | Khi nào dùng | Lưu ý |
| --- | --- | --- |
| `MyType.class` | Biết kiểu ngay khi viết mã | Không cần có instance; được compiler kiểm tra tên kiểu |
| `value.getClass()` | Đang có một đối tượng và muốn biết lớp thực tế | `value` không được là `null` |
| `Class.forName("com.example.MyType")` | Có tên lớp dạng chuỗi ở runtime | Có thể phát sinh `ClassNotFoundException`; biến thể một tham số có thể kích hoạt khởi tạo lớp |

Reflection **không tự động biến một chuỗi bất kỳ thành mã Java**, và cũng không có nghĩa là luôn được phép truy cập mọi thành phần nội bộ. Nó cung cấp metadata cùng các thao tác động trong giới hạn access control, module system và môi trường chạy.

### Quan hệ giữa lớp, đối tượng và metadata

```text
Mã đã biên dịch + lớp được nạp bởi ClassLoader
                      |
                      v
                 Class<?>          (metadata của kiểu)
                 /   |   \
                /    |    \
         Constructor Method Field  (mô tả thành phần)
                |       |
                v       v
        Tạo đối tượng   Gọi phương thức / đọc, ghi field
```

Cùng một lớp có thể tạo ra nhiều instance; chúng có trạng thái riêng nhưng cùng chia sẻ thông tin về định nghĩa kiểu. `Class<?>` không chứa bản sao độc lập của dữ liệu từng instance.

## 3. Reflection hoạt động theo những bước nào?

1. **Xác định kiểu cần xử lý**: nhận `Class<?>` từ `.class`, `getClass()` hoặc tên lớp.
2. **Khám phá metadata**: đọc tên lớp, superclass, interface, annotation; tìm constructor, method, field phù hợp.
3. **Lựa chọn thành phần**: ví dụ chọn phương thức tên `sayHello` có đúng một tham số `String`. Tên đơn thuần có thể chưa đủ vì Java cho phép overload.
4. **Thực hiện thao tác động nếu cần**: tạo instance bằng `Constructor.newInstance(...)`, gọi bằng `Method.invoke(...)`, hoặc đọc field bằng `Field.get(...)`.
5. **Xử lý rủi ro runtime**: tên không tồn tại, sai chữ ký, sai đối tượng nhận lời gọi, thiếu quyền truy cập, code được gọi ném exception...

Một số phương thức thường dùng:

| Nhu cầu | API ví dụ | Ý nghĩa |
| --- | --- | --- |
| Đọc tên kiểu | `clazz.getName()`, `clazz.getSimpleName()` | Tên đầy đủ hoặc tên ngắn |
| Tìm phương thức public | `clazz.getMethod(name, parameterTypes...)` | Tìm cả phương thức public được kế thừa |
| Xem phương thức khai báo trực tiếp | `clazz.getDeclaredMethods()` | Bao gồm cả phương thức non-public được khai báo trong lớp, không tự bao gồm inherited |
| Tìm constructor | `clazz.getDeclaredConstructor(parameterTypes...)` | Constructor được khai báo phù hợp |
| Tìm field | `clazz.getDeclaredField(name)` | Field được khai báo trong lớp |
| Xem annotation runtime | `clazz.getAnnotation(SomeAnnotation.class)` | Đọc annotation có `RetentionPolicy.RUNTIME` nếu hiện diện |

Phân biệt `getMethod` với `getDeclaredMethod`: cách thứ nhất chỉ tìm thành phần `public` (bao gồm inherited), cách thứ hai tìm thành phần **được khai báo trong lớp hiện tại** bất kể mức truy cập, nhưng việc tìm thấy metadata **không đồng nghĩa** việc được phép gọi/truy cập.

## 4. Ví dụ Java 17+: tìm và gọi phương thức khi đang chạy

Tạo một file tên **`ReflectionIntro.java`**:

```java
import java.lang.reflect.Method;

public class ReflectionIntro {

    public static class Greeter {
        public Greeter() {
        }

        public String sayHello(String name) {
            return "Xin chào, " + name + "!";
        }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        // 1. Lấy metadata của lớp cần làm việc.
        Class<?> type = Greeter.class;
        System.out.println("Lớp: " + type.getSimpleName());

        // 2. Tạo đối tượng qua constructor không tham số.
        Object instance = type.getDeclaredConstructor().newInstance();

        // 3. Tra cứu chính xác phương thức public sayHello(String).
        Method method = type.getMethod("sayHello", String.class);

        // 4. Gọi phương thức trên đối tượng vừa tạo.
        Object result = method.invoke(instance, "An");
        System.out.println("Phương thức: " + method.getName());
        System.out.println("Kết quả: " + result);
    }
}
```

Biên dịch và chạy (có JDK 17 trở lên):

```bash
javac ReflectionIntro.java
java ReflectionIntro
```

Kết quả chuẩn:

```text
Lớp: Greeter
Phương thức: sayHello
Kết quả: Xin chào, An!
```

**Giải thích theo trình tự:**

- `Greeter.class` cho biết runtime đang thao tác với lớp nào; kiểu biến là `Class<?>` vì đoạn xử lý muốn làm việc với metadata chung.
- `getDeclaredConstructor()` tìm constructor không tham số. `newInstance()` tạo một `Greeter` thực sự; biến `instance` được khai báo `Object` vì lời gọi động có thể trả về nhiều kiểu.
- `getMethod("sayHello", String.class)` tìm phương thức `public` theo **tên + kiểu tham số**. Nếu đổi tên hoặc đổi chữ ký, việc tìm kiếm sẽ lỗi tại runtime.
- `invoke(instance, "An")` gọi phương thức trên instance. Vì `Method.invoke` trả về `Object`, người gọi có thể phải kiểm tra và ép kiểu kết quả để dùng tiếp.

Ở đây compiler **không cần thấy lời gọi** `instance.sayHello("An")`. Việc xác định phương thức được dời sang runtime. Tuy nhiên ví dụ vẫn dùng `Greeter.class` để dễ hiểu; trong ứng dụng thật, biến `type` có thể đến từ một cấu hình/plugin và không biết trước ở mã của framework.

### So sánh lời gọi thông thường với lời gọi động

| Tiêu chí | Gọi trực tiếp | Dùng Reflection |
| --- | --- | --- |
| Cú pháp | `greeter.sayHello("An")` | `method.invoke(greeter, "An")` |
| Chọn phương thức | Compiler giải quyết từ mã nguồn và kiểu khai báo | Chương trình tra cứu `Method` ở runtime |
| Sai tên/chữ ký | Thường bị báo khi biên dịch | Có thể ném exception khi chạy |
| Kiểu kết quả | `String` đã biết | Giá trị trả về từ `invoke` là `Object` |
| Khả năng xử lý kiểu chưa biết trước | Giới hạn bởi thiết kế API tĩnh | Linh hoạt hơn nhờ metadata |
| Tính đơn giản, kiểm tra kiểu và tối ưu | Thường tốt hơn | Phức tạp và dễ lỗi hơn |

Reflection **bổ sung** cho gọi phương thức thông thường; nó không khiến thiết kế hướng đối tượng, interface hay đa hình trở nên lỗi thời.

## 5. Năm tình huống Reflection thật sự có ích

**1. Thư viện serialization/deserialization.** Một serializer có thể duyệt field/getter và annotation của lớp runtime để ánh xạ dữ liệu. Framework thực tế có thể kết hợp Reflection với accessor đã sinh hoặc những tối ưu khác; không phải mọi lần xử lý JSON đều bắt buộc dùng Reflection.

**2. Dependency injection và cấu hình dựa trên annotation.** Một container có thể tìm class/constructor được khai báo và chọn cách khởi tạo theo metadata, sau đó giải quyết dependency. Cấu trúc này cho phép thêm thành phần mới mà không phải viết từng lời gọi `new` vào container.

**3. Test runner.** JUnit và công cụ tương tự có thể phát hiện phương thức kiểm thử thông qua annotation rồi thực thi trong quy trình chung. Annotation muốn được tìm trực tiếp bằng Reflection tại runtime cần được giữ lại ở runtime; không phải annotation nào cũng như vậy.

**4. Plugin / hệ thống cấu hình mở rộng.** Tên lớp cụ thể có thể lấy từ cấu hình, được load và kiểm tra trước khi sử dụng. Nếu plugin có contract ổn định, nên tiếp tục dựa trên interface để gọi chức năng chính; Reflection có thể chỉ phục vụ bước khám phá/khởi tạo.

**5. Công cụ phân tích/debug.** Một công cụ có thể trình bày tên lớp, danh sách method/field và annotation để giúp quan sát mã đang chạy. Việc liệt kê metadata không nhất thiết phải gọi phương thức hay thay đổi trạng thái đối tượng.

Điểm chung: **code hạ tầng tổng quát cần hiểu kiểu dữ liệu mà nó chưa biết khi được viết ra**.

## 6. Những giới hạn và đánh đổi cần hiểu ngay từ đầu

### Lỗi chuyển từ compile time sang runtime

Với gọi trực tiếp, IDE/compiler thường phát hiện phương thức không tồn tại. Với Reflection, tên thường được lưu bằng chuỗi nên `getMethod("syaHello", String.class)` vẫn biên dịch nhưng sẽ ném `NoSuchMethodException` khi chạy.

Các lỗi cần phân biệt:

- `ClassNotFoundException`: không tìm thấy lớp theo tên ở `Class.forName`.
- `NoSuchMethodException` / `NoSuchFieldException`: không tìm thấy thành phần cần tra cứu.
- `IllegalAccessException` hoặc `InaccessibleObjectException`: vấn đề quyền truy cập/strong encapsulation tùy API và thời điểm.
- `IllegalArgumentException`: đối tượng nhận lời gọi hoặc số lượng/kiểu đối số không thích hợp.
- `InvocationTargetException`: method/constructor được gọi **đã ném exception**; xem `getCause()` để biết nguyên nhân bên trong.

Ví dụ ở trên khai báo `throws ReflectiveOperationException` để giữ trọng tâm ở cơ chế, không phải mẫu xử lý lỗi production. Trong chương trình thực tế, hãy bổ sung xử lý ngoại lệ phù hợp với contract của ứng dụng và ghi log có ngữ cảnh.

### Encapsulation và Java Module System (JPMS)

Tìm thấy field/method `private` qua `getDeclaredField` hoặc `getDeclaredMethod` **không có nghĩa** được tự ý truy cập. `setAccessible(true)` có thể bị từ chối, đặc biệt với package không được `opens` trong module có tên; không nên xem đây là cách vượt mọi ranh giới bảo vệ. Ứng dụng nên ưu tiên API `public` và contract đã thiết kế rõ. Trong Java modules, `exports` phục vụ truy cập API thông thường, còn `opens` cho phép deep reflection tới package được chỉ định khi cần.

### Hiệu năng

Tra cứu metadata, kiểm tra truy cập, đóng gói/unbox tham số và lời gọi động có thể gây chi phí so với lời gọi trực tiếp; JVM có tối ưu Reflection nhưng không nên mặc định mọi lời gọi đều ngang nhau. Tránh lặp lại tra cứu cùng một `Method` trong vòng lặp nóng nếu có thể cache metadata. Chỉ benchmark khi có vấn đề hiệu năng thực tế; lựa chọn kiến trúc không nên dựa trên một con số benchmark không có ngữ cảnh.

### Khả năng bảo trì, refactor và bảo mật

Tên class/method dưới dạng chuỗi thường không được công cụ refactor kiểm tra đầy đủ. Dùng Reflection trên dữ liệu do người dùng cung cấp mà không giới hạn danh sách lớp/hành động được phép có thể dẫn đến thực thi hành vi ngoài dự kiến. Nếu cần nhận class từ cấu hình, hãy xác thực danh sách được phép, kiểm tra interface/contract, giới hạn quyền và xử lý lỗi rõ ràng.

Ở một số môi trường tối ưu theo mô hình đóng (ví dụ native image), các kiểu hoặc thành phần truy cập động có thể cần được đăng ký metadata/cấu hình build theo công cụ đang dùng.

## 7. Khi nào nên ưu tiên giải pháp khác?

Nếu chương trình đã biết các kiểu và hành vi từ đầu, hãy chọn **gọi trực tiếp, interface, polymorphism, generic hoặc factory**. Ví dụ, nhiều đối tượng cùng triển khai `Notifier` thì gọi `notifier.send(...)` qua interface đơn giản và an toàn kiểu hơn là Reflection để tìm tên phương thức `send`.

Dùng Reflection khi có **lý do động thực sự**: khám phá type/annotation chưa biết trước, khởi tạo theo metadata, tích hợp framework hoặc công cụ tổng quát. Thường nên cô lập nó ở lớp hạ tầng và trả ra một API có kiểu rõ ràng cho phần còn lại của ứng dụng.

Một cách tự kiểm tra trước khi chọn Reflection:

1. Kiểu hoặc phương thức cần gọi có biết được ngay khi viết code không? Nếu có, lời gọi trực tiếp thường tốt hơn.
2. Một interface hoặc factory có biểu diễn contract cần thiết được không? Nếu có, ưu tiên contract đó.
3. Metadata nào thật sự cần đọc, và thành phần đó có public hay được mở hợp lệ không?
4. Sai tên, sai chữ ký hoặc không có class sẽ được kiểm soát như thế nào?
5. Có thể cache metadata và giới hạn lớp/hành động được phép ở ranh giới runtime không?

## 8. Điều cần nhớ

Reflection cho Java khả năng **tự quan sát metadata và thao tác động với class, constructor, method, field tại runtime**. Nó giải quyết bài toán những thư viện/công cụ tổng quát phải xử lý các kiểu chưa biết trước khi biên dịch. Cái giá là giảm kiểm tra kiểu tại compile time, thêm xử lý lỗi runtime, cân nhắc quyền truy cập và khả năng bảo trì/hiệu năng.

Hãy nhớ công thức: **chỉ cần hành vi cụ thể → gọi trực tiếp qua contract; cần khám phá cấu trúc động → cân nhắc Reflection và kiểm soát chặt ranh giới của nó**.

### Tự kiểm tra hiểu bài

- Vì sao một thư viện JSON không thể viết sẵn lời gọi tới mọi lớp của ứng dụng?
- Sự khác nhau giữa `Greeter.class`, `obj.getClass()` và `Class.forName(...)` là gì?
- Vì sao `getMethod("sayHello", String.class)` phải chỉ rõ kiểu tham số?
- Vì sao tìm thấy một field `private` chưa đảm bảo có thể đọc field đó?
- Trong dự án có interface ổn định, tại sao nên gọi qua interface sau bước khám phá plugin?


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-overview-002">Phân biệt thông tin kiểu lúc biên dịch và lúc chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt thông tin kiểu lúc biên dịch và lúc chạy trong Java

## 1. Mục tiêu

Sau bài học, bạn có thể:
- Phân biệt **kiểu tĩnh (static/compile-time type)** của một biểu thức với **lớp thực tế (runtime class)** của đối tượng mà biểu thức tham chiếu.
- Dự đoán chỗ nào Java kiểm tra kiểu khi biên dịch và chỗ nào JVM phải kiểm tra khi chạy.
- Giải thích vì sao **overload** phụ thuộc kiểu lúc biên dịch, còn **override** phụ thuộc đối tượng lúc chạy.
- Chọn đúng **Class literal**, **getClass()**, **instanceof** và các API Reflection khi cần quan sát kiểu.
- Hiểu giới hạn của Reflection do **type erasure**, và các ngoại lệ như metadata khai báo generic hoặc kiểu phần tử của mảng.

Bối cảnh: Java là ngôn ngữ kiểm tra kiểu tĩnh. Compiler và JVM có vai trò khác nhau, nhưng cùng góp phần thực thi tính an toàn kiểu. Reflection giúp chương trình truy vấn lớp, phương thức, field hoặc kiểu generic được ghi trong metadata ở thời điểm chạy; Reflection không biến mọi thông tin mà compiler từng biết thành dữ liệu có thể truy xuất từ một đối tượng.

## 2. Hai câu hỏi về “kiểu”

Xét khai báo:

~~~java
Animal pet = new Dog();
~~~

Đoạn trên giả định **Dog extends Animal**.

- **Kiểu lúc biên dịch của biểu thức pet:** **Animal**, vì đây là kiểu được khai báo của biến. Compiler dùng nó để kiểm tra phương thức nào có thể gọi, phép gán nào hợp lệ và overload nào được chọn.
- **Lớp thực tế của đối tượng lúc chạy:** **Dog**, vì đối tượng được tạo bằng **new Dog()**. Với tham chiếu khác null, **pet.getClass()** cho đối tượng Class biểu diễn Dog.
- **Một tham chiếu có thể đổi đối tượng:** nếu sau đó **pet = new Cat()** và Cat cũng extends Animal, kiểu khai báo của pet vẫn là Animal, nhưng lớp thực tế mà pet trỏ tới lúc đó là Cat.
- **Không có đối tượng khi null:** biến **Animal pet = null** vẫn có kiểu tĩnh Animal, nhưng **pet.getClass()** sẽ gây NullPointerException.

| Khía cạnh | Lúc biên dịch | Lúc chạy |
| --- | --- | --- |
| Câu hỏi chính | Biểu thức này **được phép** dùng như kiểu nào? | Giá trị/đối tượng **thực sự** là gì? |
| Nơi xử lý | **javac** (hoặc compiler Java khác) | JVM và thư viện runtime |
| Ví dụ | **pet** có kiểu Animal | Đối tượng trong pet thuộc lớp Dog |
| Quyết định điển hình | Kiểm tra phép gán, truy cập member, chọn overload | Dispatch phương thức instance được override, kiểm tra cast, instanceof |
| Công cụ quan sát | Khai báo biến, chữ ký method, kiểu biểu thức | **getClass()**, **instanceof**, Reflection |

**Lưu ý về thuật ngữ:** Với primitive như **int**, kiểu tĩnh vẫn tồn tại nhưng không có đối tượng Java tương ứng để gọi **getClass()** trực tiếp. Cũng cần phân biệt “kiểu của biểu thức lúc biên dịch” với “lớp của một đối tượng lúc chạy”: chúng không phải hai phép đo luôn cùng loại.

## 3. Ví dụ chạy được: kiểu tĩnh, override và overload

Tạo tệp **TypeStagesDemo.java**:

~~~java
public class TypeStagesDemo {
    static class Animal {
        String voice() {
            return "animal";
        }
    }

    static class Dog extends Animal {
        @Override
        String voice() {
            return "woof";
        }

        void fetch() {
            System.out.println("fetch");
        }
    }

    static String describe(Animal value) {
        return "overload Animal";
    }

    static String describe(Dog value) {
        return "overload Dog";
    }

    public static void main(String[] args) {
        Animal pet = new Dog();

        System.out.println("Runtime class: " + pet.getClass().getSimpleName());
        System.out.println("Override: " + pet.voice());
        System.out.println("Overload: " + describe(pet));
        System.out.println("instanceof Dog: " + (pet instanceof Dog));
        System.out.println("Animal.class matches: " + Animal.class.equals(pet.getClass()));
        System.out.println("Dog.class matches: " + Dog.class.equals(pet.getClass()));

        if (pet instanceof Dog dog) {
            dog.fetch();
            System.out.println("Overload after narrowing: " + describe(dog));
        }
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac TypeStagesDemo.java
java TypeStagesDemo
~~~

Kết quả:

~~~text
Runtime class: Dog
Override: woof
Overload: overload Animal
instanceof Dog: true
Animal.class matches: false
Dog.class matches: true
fetch
Overload after narrowing: overload Dog
~~~

Giải thích từng điểm:

1. **pet.getClass().getSimpleName()** đọc lớp thực tế, nên là Dog.
2. **pet.voice()** biên dịch được vì Animal khai báo voice(). Khi chạy, JVM thực thi bản override của Dog.
3. **describe(pet)** chọn overload nhận Animal vì kiểu tĩnh của pet là Animal, dù đối tượng thực tế là Dog. Chọn overload chủ yếu là quyết định lúc biên dịch; **không** tự chọn lại bằng runtime class của argument.
4. **pet instanceof Dog** kiểm tra lớp thực tế của đối tượng; pattern variable **dog** có kiểu Dog trong nhánh phù hợp.
5. Sau kiểm tra **instanceof**, **describe(dog)** chọn overload nhận Dog vì kiểu tĩnh của biến mới là Dog.
6. **pet.fetch()** sẽ **không biên dịch**: Animal không khai báo fetch(). Muốn gọi cần một tham chiếu đã được thu hẹp an toàn hoặc thực hiện cast phù hợp.

Đừng nhầm khả năng gọi phương thức với việc lựa chọn phần triển khai: compiler quyết định một lời gọi có hợp lệ theo kiểu tĩnh không; với lời gọi phương thức instance có override, runtime quyết định phần triển khai nào nhận lời gọi.

## 4. Những phép kiểm tra diễn ra ở giai đoạn nào?

### 4.1. Phép gán và member access

~~~java
Animal a = new Dog();     // Hợp lệ: Dog là subtype của Animal
// Dog d = a;             // Lỗi biên dịch: Animal chưa chắc là Dog
Dog d = (Dog) a;          // Hợp lệ khi chạy vì a đang trỏ tới Dog
~~~

Compiler cho phép phép downcast **(Dog) a** vì về mặt quan hệ kiểu phép ép có khả năng thành công; JVM kiểm tra đối tượng lúc chạy. Nếu a trỏ tới một đối tượng Animal không phải Dog, phép ép gây **ClassCastException**. Ép kiểu tham chiếu không thay đổi runtime class của đối tượng.

### 4.2. instanceof và pattern matching

~~~java
Object value = "Java";
if (value instanceof String text) {
    System.out.println(text.length()); // 4
}
~~~

**instanceof** kiểm tra giá trị thực lúc chạy. Nếu giá trị là null, kết quả của kiểm tra kiểu là false. Pattern variable text chỉ có hiệu lực ở nhánh mà compiler chứng minh điều kiện khớp.

### 4.3. Overloading, overriding, field và static method

- **Overloading**: quyết định chữ ký phương thức từ các kiểu lúc biên dịch của argument và các quy tắc phân giải overload. Ví dụ **describe(pet)** chọn bản nhận Animal.
- **Overriding**: đối với phương thức instance có thể override, JVM lựa chọn triển khai dựa trên lớp thực tế của receiver. Ví dụ **pet.voice()** chạy Dog.voice().
- **Field hiding**: truy cập field qua biểu thức thường phụ thuộc kiểu tĩnh của biểu thức; field không có đa hình như instance method.
- **Static method hiding**: lời gọi qua kiểu/tham chiếu được phân giải theo các quy tắc tĩnh; static method không bị override. Tránh gọi static method qua instance vì dễ gây hiểu nhầm.

Một câu ghi nhớ hữu ích: **kiểu tĩnh giới hạn điều được phép gọi; đa hình quyết định bản override được chạy**.

## 5. Reflection nhìn thấy kiểu gì?

### 5.1. Class literal và getClass()

Có hai cách nhận đối tượng metadata **Class** với ý nghĩa khác nhau:

~~~java
Class<?> declared = Animal.class; // Biết lớp Animal từ source code
Animal pet = new Dog();
Class<?> actual = pet.getClass(); // Lớp Dog của đối tượng lúc chạy
~~~

- **Animal.class** là class literal cho Animal, không phụ thuộc giá trị hiện tại của biến.
- **pet.getClass()** là phương thức trên Object, lấy lớp thực tế của đối tượng và yêu cầu pet khác null.
- **Class<?>** biểu diễn metadata của một kiểu có Class token; dùng wildcard khi mã không biết tham số kiểu chính xác.
- **Class.forName("com.example.Dog")** có thể tìm lớp bằng tên đầy đủ khi chạy, thông qua cơ chế class loading; bản một tham số còn khởi tạo lớp nếu chưa được khởi tạo. Nó có thể ném ClassNotFoundException.
- Class token được định danh bởi **tên lớp cùng defining class loader**. Cùng tên nhị phân nhưng do hai class loader khác nhau định nghĩa có thể là hai kiểu runtime khác nhau.

**Không nên suy luận rằng getClass() có thể trả về “kiểu khai báo của biến”.** Đối tượng không mang thông tin riêng rằng nó từng được lưu vào biến Animal, Object hay một biến generic nào đó.

### 5.2. Kiểm tra quan hệ kiểu bằng Class

~~~java
Class<?> parent = Animal.class;
Class<?> child = Dog.class;

System.out.println(parent.isAssignableFrom(child)); // true
System.out.println(child.isAssignableFrom(parent)); // false

Animal pet = new Dog();
System.out.println(child.isInstance(pet));           // true
~~~

Ý nghĩa của **X.isAssignableFrom(Y)**: một giá trị có kiểu Y có thể được gán cho biến kiểu X theo quan hệ chuyển đổi kiểu tham chiếu phù hợp. Vì Dog extends Animal, **Animal.class.isAssignableFrom(Dog.class)** trả true, còn chiều ngược lại false. Với một đối tượng cụ thể, **Dog.class.isInstance(pet)** tương tự kiểm tra **pet instanceof Dog** tại runtime, và trả false khi pet là null.

Các API bổ sung:
- **targetClass.cast(object)**: kiểm tra và cast đối tượng theo Class token; có thể ném ClassCastException.
- **someClass.asSubclass(Base.class)**: xác thực quan hệ subclass trên Class token; có thể ném ClassCastException.
- **getSuperclass()**, **getInterfaces()**: xem quan hệ kế thừa/interface trực tiếp trong metadata.
- **getDeclaredMethods()**, **getDeclaredFields()**: xem member khai báo trực tiếp trong lớp; khác với **getMethods()** vốn xét các public method được kế thừa theo quy tắc API.

### 5.3. Ví dụ kiểm tra một member đã khai báo

Tạo tệp **ReflectionInspectDemo.java**:

~~~java
import java.lang.reflect.Method;

public class ReflectionInspectDemo {
    static class Base {
        public void start() {}
    }

    static class Worker extends Base {
        private void process() {}
    }

    public static void main(String[] args) throws Exception {
        Class<?> type = Worker.class;
        Method local = type.getDeclaredMethod("process");
        Method inherited = type.getMethod("start");

        System.out.println(type.getSimpleName());
        System.out.println(local.getDeclaringClass().getSimpleName());
        System.out.println(inherited.getDeclaringClass().getSimpleName());
        System.out.println(type.getSuperclass().getSimpleName());
    }
}
~~~

~~~bash
javac ReflectionInspectDemo.java
java ReflectionInspectDemo
~~~

Kết quả xác định:

~~~text
Worker
Worker
Base
Base
~~~

Ở đây **getDeclaredMethod("process")** tìm phương thức private khai báo trong Worker. **getMethod("start")** tìm public method được kế thừa từ Base. Việc *tìm metadata* không đồng nghĩa với quyền *truy cập hoặc gọi* member đó; phản chiếu vào private member còn chịu kiểm soát truy cập và giới hạn module của Java Platform Module System. Đừng mặc định **setAccessible(true)** sẽ thành công.

## 6. Generics: thông tin nào bị xóa, thông tin nào còn?

Java thường biên dịch generic bằng cơ chế **type erasure**. Kiểu tham số hóa như **List\<String>** giúp compiler kiểm tra an toàn kiểu khi viết code, nhưng đối tượng ArrayList lúc chạy không tự mang nhãn phần tử String.

Ví dụ biên dịch hợp lệ:

~~~java
import java.util.ArrayList;
import java.util.List;

List<String> names = new ArrayList<>();
List<Integer> numbers = new ArrayList<>();

System.out.println(names.getClass().equals(numbers.getClass())); // true
System.out.println(names.getClass().getName()); // java.util.ArrayList
~~~

**List\<String>** và **List\<Integer>** khác nhau ở tầng kiểm tra kiểu lúc biên dịch, nhưng hai đối tượng **new ArrayList<>()** có cùng runtime class **java.util.ArrayList**.

Các giới hạn cần nhớ:
- **List\<String>.class** không hợp lệ; class literal dùng được với **List.class** (raw type) hoặc một Class token thích hợp khác.
- Với biến `Object value`, `value instanceof List<String>` không hợp lệ vì phép kiểm tra cần một chuyển đổi không an toàn kiểu (unchecked). Từ Java 16, với biến `List<String> value`, `value instanceof List<String>` và `value instanceof ArrayList<String>` có thể hợp lệ vì kiểu tĩnh đã đủ thông tin để kiểm tra mà không cần chuyển đổi unchecked. `value instanceof List<?>` vẫn là cách kiểm tra phù hợp khi chỉ cần biết đối tượng có phải một danh sách; không kiểm tra kiểu từng phần tử tại runtime.
- **getClass()** của ArrayList không thể tự suy ra rằng một biến bên ngoài khai báo là List\<String>.
- Không có cam kết rằng một danh sách tại runtime luôn chứa phần tử thuộc kiểu đã ghi trong source: raw type, unchecked conversion hoặc Reflection có thể tạo ra heap pollution; compiler thường phát cảnh báo trong các tình huống như vậy.

**Nhưng “type erasure” không có nghĩa mọi dấu vết generic đều biến mất.** Metadata của *khai báo* field, method, superclass hoặc interface có thể giữ generic signature trong class file và đọc được qua **java.lang.reflect.Type** (ví dụ ParameterizedType, TypeVariable, WildcardType). Điều đó khác với việc mỗi instance có một đối tượng Class riêng cho từng type argument.

### Ví dụ getType() so với getGenericType()

Tạo tệp **GenericMetadataDemo.java**:

~~~java
import java.lang.reflect.Field;
import java.util.List;

public class GenericMetadataDemo {
    private List<String> labels;

    public static void main(String[] args) throws Exception {
        Field field = GenericMetadataDemo.class.getDeclaredField("labels");

        System.out.println(field.getType().getTypeName());
        System.out.println(field.getGenericType().getTypeName());
    }
}
~~~

~~~bash
javac GenericMetadataDemo.java
java GenericMetadataDemo
~~~

Kết quả:

~~~text
java.util.List
java.util.List<java.lang.String>
~~~

**getType()** trả về Class cho kiểu đã erasure của field. **getGenericType()** đọc kiểu khai báo trong generic signature khi metadata có sẵn. Nếu khai báo dùng type variable **T**, Reflection có thể trả về **TypeVariable**; muốn tìm đối số kiểu cụ thể trong một hệ phân cấp generic, thường phải phân tích tiếp cấu trúc Type, và có trường hợp không thể khôi phục kiểu mà caller từng dùng.

## 7. Mảng và primitive: hai ngoại lệ dễ nhầm

### 7.1. Runtime component type của mảng

Mảng là đối tượng có runtime component type. Vì mảng tham chiếu trong Java có tính **covariant**, phép gán sau đây hợp lệ lúc biên dịch nhưng thao tác ghi bị kiểm tra lúc chạy:

Tạo tệp **ArrayRuntimeTypeDemo.java**:

~~~java
public class ArrayRuntimeTypeDemo {
    public static void main(String[] args) {
        Object[] items = new String[1];

        System.out.println(items.getClass().getComponentType().getName());

        try {
            items[0] = 42;
        } catch (ArrayStoreException ex) {
            System.out.println("ArrayStoreException");
        }
    }
}
~~~

~~~bash
javac ArrayRuntimeTypeDemo.java
java ArrayRuntimeTypeDemo
~~~

Kết quả:

~~~text
java.lang.String
ArrayStoreException
~~~

Biến items có kiểu tĩnh **Object[]**, nhưng đối tượng thực tế là **String[]**. JVM không cho lưu Integer vào mảng String, nên ném **ArrayStoreException**. Điều này khác với danh sách generic đã erasure: JVM thực sự biết runtime component type của mảng.

### 7.2. Primitive và wrapper

~~~java
Class<?> primitive = int.class;
Class<?> wrapper = Integer.class;

System.out.println(primitive.isPrimitive()); // true
System.out.println(wrapper.isPrimitive());   // false
System.out.println(primitive.equals(Integer.TYPE)); // true
~~~

**int.class** đại diện metadata cho primitive int, nhưng một giá trị **int** không phải đối tượng và không có phương thức **getClass()**. Khi gán int cho Object, Java thực hiện boxing thành Integer; nếu sau đó gọi getClass() trên đối tượng boxed, ta nhận **Integer.class**, không phải **int.class**. Đừng trộn lẫn primitive và wrapper khi tìm constructor hoặc method bằng signature Reflection.

## 8. Phân biệt khả năng kiểm tra và khả năng truy vấn

| Câu hỏi thực tế | Dùng gì? | Cần cảnh giác |
| --- | --- | --- |
| Biến được phép gọi member nào? | Kiểu khai báo và luật compiler | Đối tượng có thể thuộc subclass giàu member hơn |
| Đối tượng khác null hiện thuộc lớp nào? | **obj.getClass()** | Không trả kiểu khai báo biến |
| Giá trị có tương thích với một subtype? | **instanceof**, **Class.isInstance()** | null không khớp |
| Class A có nhận giá trị kiểu B? | **A.isAssignableFrom(B)** | Dễ đảo chiều |
| Lấy Class token khi đã biết kiểu trong source? | **SomeType.class** | Không phản ánh đối tượng hiện tại |
| Tìm chữ ký field generic? | **Field.getGenericType()** | Metadata thuộc khai báo; không chứng minh phần tử instance |
| Tìm runtime component type của mảng? | **array.getClass().getComponentType()** | Mảng primitive và mảng tham chiếu khác nhau |
| Tìm method khi chạy? | **getMethod/getDeclaredMethod** | Khác nhau về inherited/public; truy cập còn bị kiểm soát |

### Gợi ý khi viết API dùng Reflection

Nếu logic biết trước kiểu và hành vi cần gọi, ưu tiên interface, generic và đa hình bình thường: compiler sẽ kiểm tra giúp bạn. Reflection phù hợp hơn với việc phải khảo sát schema/metadata hoặc framework cần khám phá class, field, method lúc chạy. Khi dùng Reflection:

1. Xác định bạn đang xử lý **Class**, **Type**, một **Field/Method** khai báo, hay một **instance** cụ thể.
2. Phân biệt câu hỏi về **quan hệ assignable** với câu hỏi **lớp thực tế có đúng bằng lớp X không**. **Animal.class.equals(pet.getClass())** có thể false nhưng **pet instanceof Animal** vẫn true.
3. Chú ý **class loader**, **null**, **erasure**, **access control** và checked exceptions khi tra cứu bằng tên.
4. Không dựa vào thứ tự kết quả của **getDeclaredMethods()**/**getDeclaredFields()**: API không cam kết một thứ tự cố định.
5. Kiểm tra chữ ký parameter chính xác khi dùng **getMethod(name, parameterTypes...)**; generic argument trong source thường đã erasure.

## 9. Các hiểu lầm thường gặp

**“Biến Animal trỏ tới Dog thì compiler cho gọi mọi method Dog.”** Sai. Compiler kiểm tra theo kiểu tĩnh của biến; dùng đa hình qua method đã khai báo ở Animal, hoặc thu hẹp có kiểm tra nếu thực sự cần member của Dog.

**“Override và overload đều chọn theo runtime class.”** Sai. Overload lựa chọn chữ ký lúc biên dịch; override dispatch theo receiver lúc chạy.

**“Ép kiểu đối tượng sang Animal biến đối tượng thành Animal.”** Sai. Cast đổi cách biểu thức được xét kiểu; đối tượng Dog vẫn có runtime class Dog nếu cast hợp lệ.

**“Reflection luôn khôi phục List\<String> của một ArrayList instance.”** Sai. Generic signature của khai báo đôi khi đọc được, nhưng đó không phải nhãn type argument có trên mọi đối tượng.

**“Class.getName() trùng thì hai Class token chắc chắn cùng kiểu.”** Sai trong tình huống nhiều defining class loader.

**“getDeclaredMethods() trả toàn bộ public method kể cả kế thừa.”** Sai; cần hiểu khác biệt declared so với public/inherited.

## 10. Tự kiểm tra

Cho khai báo **Animal a = new Dog()** với Dog extends Animal, Dog override voice() và bổ sung fetch():

1. Kiểu tĩnh của a là gì? Runtime class là gì?
2. Vì sao **a.voice()** chạy bản Dog nhưng **a.fetch()** không biên dịch?
3. Hai overload **f(Animal)** và **f(Dog)** thì **f(a)** gọi bản nào?
4. **Animal.class.isAssignableFrom(Dog.class)** trả gì? **Dog.class.isAssignableFrom(Animal.class)** trả gì?
5. Nếu **List\<String>** và **List\<Integer>** cùng được tạo bằng ArrayList, getClass() có phân biệt type argument không?
6. Vì sao phép gán **Object[] values = new String[1]** hợp lệ nhưng ghi Integer vào values có thể ném lỗi?

Đáp án: (1) Animal và Dog. (2) Compiler thấy voice ở Animal và JVM dispatch override; fetch không thuộc API của kiểu tĩnh Animal. (3) f(Animal). (4) true rồi false. (5) Không, vì erasure và cùng lớp ArrayList. (6) Mảng covariant nhưng JVM kiểm tra runtime component type, nên ArrayStoreException.

## 11. Tổng kết

Trong Java, **thông tin kiểu lúc biên dịch** dùng để chứng minh code hợp lệ và xác định nhiều lựa chọn tĩnh; **thông tin kiểu lúc chạy** mô tả giá trị/đối tượng thật và phục vụ dispatch đa hình, kiểm tra kiểu động và Reflection. **Class<?>** cung cấp metadata về lớp runtime hoặc kiểu được nêu trực tiếp, còn **Type** biểu diễn thêm một số khai báo generic. Nắm rõ ranh giới này giúp bạn dự đoán lỗi biên dịch, ClassCastException, ArrayStoreException và giới hạn của các thư viện dựa trên Reflection.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-overview-003">Quan sát metadata khác thực thi hoặc thay đổi đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Quan sát metadata khác với thực thi hoặc thay đổi đối tượng trong Java Reflection

## 1. Mục tiêu và ranh giới cốt lõi

Java Reflection cho phép chương trình xem thông tin về lớp, field, method, constructor, modifier, kiểu dữ liệu và annotation tại runtime. **Lấy thông tin mô tả (metadata) không đồng nghĩa với gọi method, chạy constructor hoặc sửa trạng thái của một object.** Những hành động đó đòi hỏi API riêng và có thể tạo ra tác dụng phụ.

Ví dụ, khi gọi `getDeclaredMethod("advance")`, ta chỉ nhận một đối tượng `Method` mô tả phương thức tên `advance`. Đến khi gọi `method.invoke(target)`, phương thức đích mới được thực thi. Tương tự, `getDeclaredField("status")` trả metadata của field; `field.set(target, "DONE")` mới là thao tác ghi trạng thái.

Một điểm tinh tế: các API Reflection tự chúng vẫn chạy mã thư viện để truy xuất metadata, đôi khi có thể làm nạp hoặc liên kết lớp. Vì vậy, câu “đọc metadata không thực thi code” phải hiểu chính xác là **không tự gọi logic nghiệp vụ của method/constructor được mô tả**, chứ không phải JVM hoàn toàn không làm việc gì.

## 2. Phân biệt metadata, đọc trạng thái và hành động có tác dụng phụ

| API hoặc thao tác | Nhóm | Ý nghĩa và khả năng tác động |
| --- | --- | --- |
| `SomeType.class`, `obj.getClass()` | Lấy đối tượng `Class` | Xác định kiểu; không tạo instance mới. |
| `Class.getName()`, `getModifiers()`, `getDeclaredFields()`, `getDeclaredMethods()`, `getDeclaredConstructors()` | Quan sát metadata | Đọc khai báo/cấu trúc; không tự chạy method/constructor được tìm thấy. |
| `Class.getAnnotation(...)`, `Field.getName()`, `Method.getParameterTypes()` | Quan sát metadata | Đọc annotation, tên và chữ ký; không gọi logic nghiệp vụ của đối tượng đích. |
| `Field.get(target)` | Đọc giá trị runtime | Đọc **trạng thái hiện tại** của field; không phải chỉ metadata. Đọc field `static` có thể kích hoạt khởi tạo lớp. |
| `Method.invoke(target, args...)` | Thực thi | Thực sự gọi method đích; có thể ghi dữ liệu, in log, ném exception, gọi mạng... |
| `Constructor.newInstance(args...)` | Thực thi/tạo đối tượng | Tạo instance và chạy constructor; constructor có thể gây tác dụng phụ. |
| `Field.set(target, value)` | Thay đổi trạng thái | Ghi giá trị field (nếu được phép); khác với `Field.get`. |
| `AccessibleObject.setAccessible(true)` | Thay đổi quyền truy cập Reflection | Yêu cầu bỏ kiểm tra truy cập Java đối với thao tác Reflection sau đó; **bản thân lời gọi này không sửa giá trị field đích**. Có thể bị từ chối bởi hệ thống module/quy tắc truy cập. |

Điểm mấu chốt: `Method` và `Field` là các **đối tượng mô tả thành viên**. Có trong tay đối tượng mô tả chưa có nghĩa là đã dùng thành viên đó để tác động đến một instance.

## 3. Ví dụ hoàn chỉnh: xem metadata trước, thực thi và sửa dữ liệu sau

Lưu đoạn sau thành file `ReflectionBoundaryDemo.java` (Java 17 trở lên):

~~~java
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectionBoundaryDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @interface Topic {
        String value();
    }

    @Topic("demo")
    static class Counter {
        private String status = "NEW";
        private int count = 0;

        public void advance() {
            count++;
            System.out.println("advance executed");
        }

        public String snapshot() {
            return status + ":" + count;
        }
    }

    public static void main(String[] args) throws Exception {
        Class<Counter> type = Counter.class;
        Field field = type.getDeclaredField("status");
        Method method = type.getDeclaredMethod("advance");
        Topic annotation = type.getAnnotation(Topic.class);

        System.out.println("Class: " + type.getSimpleName());
        System.out.println("Field: " + field.getName() + " (" + field.getType().getSimpleName() + ")");
        System.out.println("Method: " + method.getName() + " -> " + method.getReturnType().getSimpleName());
        System.out.println("Annotation: " + annotation.value());

        Counter counter = new Counter();
        System.out.println("State before: " + counter.snapshot());

        method.invoke(counter);
        System.out.println("State after invoke: " + counter.snapshot());

        field.setAccessible(true);
        System.out.println("Read field: " + field.get(counter));

        field.set(counter, "DONE");
        System.out.println("State after field.set: " + counter.snapshot());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ReflectionBoundaryDemo.java
java ReflectionBoundaryDemo
~~~

Kết quả chuẩn:

~~~text
Class: Counter
Field: status (String)
Method: advance -> void
Annotation: demo
State before: NEW:0
advance executed
State after invoke: NEW:1
Read field: NEW
State after field.set: DONE:1
~~~

Giải thích theo từng mốc:

1. `getDeclaredField`, `getDeclaredMethod`, `getAnnotation` và các lời gọi lấy tên/kiểu trả về đều phục vụ **quan sát metadata**. Chúng không gọi `advance()`, nên không có dòng `advance executed` trong phần đầu.
2. `new Counter()` là bước **tạo object bằng Java thông thường**. `counter.snapshot()` là lời gọi method thông thường để kiểm tra trạng thái, không phải metadata inspection.
3. `method.invoke(counter)` thực thi `advance()`, tăng `count` từ `0` lên `1` và in ra thông báo.
4. `field.setAccessible(true)` cho phép ví dụ này truy cập field `private`; nó không đổi chuỗi `status`.
5. `field.get(counter)` đọc giá trị đang tồn tại (`NEW`); `field.set(counter, "DONE")` mới đổi thành `DONE`.

Ví dụ dùng một lớp lồng nhau trong cùng file nên thao tác truy cập private ở đây hoạt động trên Java 17+. Khi phản chiếu vào lớp thuộc một module khác, `setAccessible(true)` có thể ném `InaccessibleObjectException` nếu package chưa được mở phù hợp; không nên xem Reflection là cách vô điều kiện vượt qua đóng gói.

## 4. Đọc metadata khác với khởi tạo lớp

Phân biệt thêm ba khái niệm dễ bị gộp chung:

- **Nạp (loading):** JVM tìm và định nghĩa biểu diễn của lớp.
- **Liên kết (linking):** JVM xác minh, chuẩn bị và có thể phân giải tham chiếu.
- **Khởi tạo (initialization):** chạy static initializer và các phép gán khởi tạo tĩnh theo quy tắc Java.

Tham chiếu `SomeType.class` hoặc truy vấn metadata qua `Class` thông thường không tự khởi tạo lớp đó. Ngược lại, `Class.forName("ten.day.du")` một tham số **mặc định yêu cầu khởi tạo lớp**. Điều này rất quan trọng nếu static initializer mở kết nối, thay đổi cấu hình hoặc ghi log.

File `InitializationBoundaryDemo.java` minh họa:

~~~java
public class InitializationBoundaryDemo {
    static class Target {
        static {
            System.out.println("Target initialized");
        }
    }

    public static void main(String[] args) throws Exception {
        Class<?> type = Target.class;
        System.out.println("Class literal resolved");

        type.getDeclaredFields();
        System.out.println("Metadata inspected");

        Class.forName(Target.class.getName());
        System.out.println("Class.forName completed");
    }
}
~~~

~~~bash
javac InitializationBoundaryDemo.java
java InitializationBoundaryDemo
~~~

~~~text
Class literal resolved
Metadata inspected
Target initialized
Class.forName completed
~~~

Trong chương trình này, dòng khởi tạo tĩnh chỉ xuất hiện ở bước `Class.forName`. Nếu cần lấy `Class` từ tên lớp mà **chưa yêu cầu khởi tạo**, có thể sử dụng overload `Class.forName(className, false, classLoader)`; vẫn cần xử lý `ClassNotFoundException` và các lỗi nạp/liên kết có thể xảy ra. Không suy diễn rằng mọi API đọc thông tin hoặc mọi cách lấy `Class` đều tuyệt đối không có tác dụng phụ ở cấp JVM.

## 5. Biết rõ chính xác mình đang quan sát gì

### 5.1. Metadata của lớp khác với trạng thái của instance

`Field.getType()` nói field **được khai báo** có kiểu nào; `Field.get(obj)` đọc **giá trị hiện tại** trên object tương ứng. Một metadata field có thể được dùng để đọc nhiều instance khác nhau:

~~~java
Field statusField = Counter.class.getDeclaredField("status");
statusField.setAccessible(true);
Counter first = new Counter();
Counter second = new Counter();
statusField.set(first, "DONE");

System.out.println(statusField.get(first));  // DONE
System.out.println(statusField.get(second)); // NEW
~~~

Đoạn trên chỉ là phần **minh họa thêm trong phạm vi lớp `ReflectionBoundaryDemo`**, không phải một chương trình độc lập; cần các import, khai báo `Counter` và ngữ cảnh truy cập ở ví dụ trước. Hai instance có cùng cấu trúc field nhưng giữ giá trị riêng. Dòng `set` chỉ thay đổi `first`, không tác động `second`.

### 5.2. Khai báo trực tiếp và thành viên kế thừa

| API | Thành viên nhìn thấy |
| --- | --- |
| `getDeclaredFields()` / `getDeclaredMethods()` | Các field/method được **khai báo trực tiếp trong chính lớp**, kể cả private; không gom các thành viên kế thừa. |
| `getFields()` / `getMethods()` | Các field/method **public** có thể truy cập trong lớp và phần kế thừa (bao gồm thành viên public phù hợp từ interface). |
| `getDeclaredConstructors()` | Constructor của lớp đang xét; constructor không được kế thừa. |
| `getConstructors()` | Các constructor public của lớp đang xét. |

Lưu ý `getDeclaredMethods()` có thể bao gồm method tổng hợp (`synthetic`) hoặc bridge method do compiler sinh ra. Trước khi hiển thị metadata cho người học/người dùng, cân nhắc `Method.isSynthetic()`, `Method.isBridge()`, `Modifier` và các annotation cần thiết. **Không dựa vào thứ tự** mảng trả về từ `getDeclaredFields()` hay `getDeclaredMethods()`; nếu cần output ổn định, hãy tự sắp xếp.

### 5.3. Annotation mô tả, không mặc nhiên thực thi hành vi

Annotation có `@Retention(RetentionPolicy.RUNTIME)` mới đọc được ở runtime bằng Reflection. Đọc annotation không tự biến nội dung annotation thành luật xử lý. Ví dụ annotation `@Topic("demo")` trong chương trình chỉ cung cấp chuỗi `demo`; chính mã dùng annotation (framework hoặc ứng dụng) mới quyết định có đăng ký bean, chạy kiểm tra hay gọi hàm hay không. `getAnnotations()` và `getDeclaredAnnotations()` còn khác nhau về cách xét phần tử được kế thừa theo quy tắc `@Inherited` đối với annotation của lớp.

## 6. Ứng dụng: tách pha khám phá và pha thực thi

Giả sử xây công cụ liệt kê command trên một plugin:

1. **Pha khám phá:** dùng `Class`, `Method`, annotation và modifier để xác định tên command, số tham số, kiểu trả về; kiểm tra chữ ký và xây menu lựa chọn.
2. **Pha kiểm tra:** xác minh quyền chạy, dữ liệu đầu vào, phạm vi plugin được phép, tình trạng đối tượng và cách xử lý lỗi.
3. **Pha thực thi:** chỉ khi có yêu cầu hợp lệ mới tạo đối tượng (nếu cần) và gọi `Method.invoke`.

Cách phân pha này giúp ứng dụng **hiển thị tài liệu hoặc liệt kê API** mà không vô ý kích hoạt hành động. Đặc biệt, UI “xem cấu trúc lớp” không nên gọi thử mọi method để khám phá kết quả: getter cũng có thể tính toán nặng, ghi cache, đọc file hoặc ném exception. Tên bắt đầu bằng `get` không bảo đảm một method không có side effect.

Trong dependency injection, serializer, ORM hoặc framework kiểm thử, việc **quét annotation/field** thường là bước mô tả; **khởi tạo object, gọi lifecycle callback, đọc/ghi field** là những bước tiếp theo có ngữ nghĩa runtime khác. Khi debug, hãy xác định chính xác bước nào đã xảy ra.

## 7. Lỗi thường gặp và giới hạn thực tế

- **Nhầm “tìm method” với “gọi method”:** `getMethod`/`getDeclaredMethod` không gọi method đích. Muốn gọi phải dùng `invoke`, hoặc cú pháp gọi Java thông thường.
- **Nhầm “field metadata” với “field value”:** `getDeclaredField` cho tên, kiểu, modifier và annotation; `Field.get` đọc giá trị runtime; `Field.set` ghi giá trị runtime.
- **Bỏ qua side effect của khởi tạo:** `Class.forName(String)`, truy cập một số thành viên `static`, tạo object hoặc gọi method có thể kích hoạt khởi tạo lớp. “Chỉ đang khám phá” cần được kiểm chứng bằng API thực tế.
- **Giả định private luôn truy cập được:** việc tìm thấy private member không có nghĩa là được đọc/ghi/gọi. `setAccessible` có thể thất bại với các module đóng; ưu tiên public API và thiết kế quyền truy cập tường minh.
- **Coi Reflection là miễn kiểm tra kiểu:** `Method.invoke` phải nhận đúng receiver, số và kiểu đối số; `Field.set` phải nhận giá trị phù hợp. Các lời gọi có thể ném `IllegalAccessException`, `IllegalArgumentException` hoặc `InvocationTargetException`; lỗi từ method được gọi thường nằm trong `InvocationTargetException.getCause()`.
- **Đoán thứ tự metadata:** API trả các mảng field/method không bảo đảm thứ tự nguồn. Hãy sắp xếp nếu cần kết quả determinism.
- **Sửa field không nên sửa:** không coi Reflection là cơ chế hợp lệ để thay mọi field `final`, invariant, field ẩn hoặc cấu trúc nội bộ của JDK. Thay đổi trực tiếp có thể phá tính nhất quán của đối tượng và vướng giới hạn truy cập.
- **Đồng nhất annotation với hành vi:** annotation cung cấp dữ liệu mô tả; logic xử lý annotation mới thực hiện tác vụ thực tế.

## 8. Checklist khi đọc hoặc viết mã Reflection

Trước mỗi lời gọi, tự trả lời:

1. Mình đang hỏi **lớp/thành viên được khai báo thế nào** (`Class`, `Field`, `Method`, annotation), hay đang lấy **giá trị của một instance** (`Field.get`)?
2. API này có **gọi constructor/method** hoặc **ghi giá trị** không (`newInstance`, `invoke`, `set`)?
3. Có khả năng **khởi tạo lớp**, kích hoạt static initializer, gọi lifecycle callback hoặc thực hiện I/O không?
4. Mình đã xử lý đúng **public/private, inherited/declared, module access, receiver và exception** chưa?
5. Nếu chỉ muốn xem metadata, có thể dừng ở các API lấy mô tả mà không tạo instance hay gọi code nghiệp vụ không?

**Tự kiểm tra:** Trong ví dụ `ReflectionBoundaryDemo`, nếu bỏ hai dòng `method.invoke(counter)` và `field.set(counter, "DONE")` (cùng các dòng in trạng thái chỉ dùng để quan sát sau chúng), thì không có thao tác Reflection nào làm tăng `count` hoặc đổi `status`. Việc thu thập `Method` và `Field` vẫn hoàn toàn thực hiện được.

**Kết luận:** Reflection giúp **biết chương trình được cấu trúc như thế nào**; việc **cho chương trình làm gì** thuộc về các lời gọi thực thi hoặc thay đổi trạng thái. Tách rõ hai lớp trách nhiệm này giúp viết công cụ introspection đáng tin cậy, debug chính xác và kiểm soát tác dụng phụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-overview-004">Vai trò của Class và java.lang.reflect</a>

<details>
<summary>Xem chi tiết</summary>

# Vai trò của `Class` và `java.lang.reflect` trong Java

## 1. Reflection là gì, và hai thành phần này làm việc cùng nhau như thế nào?

Trong Java, mã thông thường đã biết kiểu và phương thức lúc biên dịch: `user.greet("Chào")`. **Reflection** cung cấp cách **khám phá cấu trúc của kiểu tại runtime**, sau đó (nếu được phép) tạo đối tượng, đọc/ghi field hoặc gọi method thông qua metadata. Tình huống điển hình là một framework chỉ biết tên lớp hoặc annotation từ cấu hình nhưng phải tìm constructor, khởi tạo đối tượng và gọi method khi chương trình chạy.

Hai lớp khái niệm có trách nhiệm riêng:

- **`java.lang.Class<T>`**: đối tượng mô tả **kiểu runtime**; là điểm vào để tìm thông tin tên, kế thừa, interface, annotation, field, method và constructor.
- **`java.lang.reflect`**: gói API cung cấp các đối tượng mô tả **thành viên và chữ ký kiểu** (`Field`, `Method`, `Constructor`, `Type`...) và thao tác phản chiếu lên chúng.

`Class` **thuộc `java.lang`**, không thuộc `java.lang.reflect`. Luồng chung là: **lấy `Class<?>` → tìm `Method`/`Field`/`Constructor` → kiểm tra quyền truy cập và kiểu → thao tác → xử lý ngoại lệ**. Tìm thấy một thành viên qua metadata không có nghĩa chương trình tự động có quyền sử dụng thành viên ấy.

## 2. `Class<T>`: định danh và thông tin của kiểu runtime

Đối tượng `Class` không phải là instance của kiểu được mô tả. `String.class` là đối tượng metadata kiểu `Class<String>`; còn `"abc"` mới là instance `String`. Mỗi kiểu mà JVM biết đến đều có biểu diễn `Class`, kể cả class, interface, enum, record, array, kiểu primitive và `void`.

Các cách lấy `Class`:

```java
Class<String> byLiteral = String.class;

String text = "Java";
Class<? extends String> byObject = text.getClass();

Class<?> byName = Class.forName("java.lang.String");

Class<?> primitiveType = int.class;
Class<?> arrayType = String[].class;
```

Đoạn trên chỉ là **minh họa trong phạm vi method**; lệnh `Class.forName(...)` phải xử lý `ClassNotFoundException`. `obj.getClass()` phản ánh kiểu runtime thực và gây `NullPointerException` nếu `obj` là `null`. `Class.forName(String)` mặc định có thể kích hoạt việc khởi tạo lớp; overload `Class.forName(name, false, loader)` yêu cầu nạp mà chưa khởi tạo ngay.

| Phương thức của `Class` | Câu hỏi trả lời |
| --- | --- |
| `getName()`, `getSimpleName()`, `getCanonicalName()` | Kiểu tên tương ứng; lớp lồng có dấu `$` trong tên nhị phân |
| `getSuperclass()`, `getInterfaces()` | Superclass và các interface trực tiếp |
| `isInterface()`, `isEnum()`, `isRecord()`, `isArray()`, `isPrimitive()` | Đây là loại kiểu nào? |
| `getComponentType()` | Kiểu phần tử của array hay `null` nếu không phải array |
| `getModifiers()` | Modifier của lớp, giải mã với `Modifier` |
| `getAnnotations()`, `getDeclaredAnnotations()` | Annotation có thể quan sát được tại runtime |
| `isInstance(obj)`, `cast(obj)`, `asSubclass(Base.class)` | Kiểm tra/biểu diễn quan hệ kiểu an toàn |
| `getClassLoader()`, `getModule()` | Bối cảnh nạp lớp và module |

Generic của `Class<T>` cung cấp kiểm tra kiểu ở compile time: khi biết chính xác lớp, ưu tiên `Class<User>`; khi nhận kiểu động, dùng `Class<?>` thay vì raw type `Class`. Lưu ý **danh tính JVM của class phụ thuộc cả class loader**: hai loader có thể định nghĩa cùng một tên lớp nhưng tạo hai kiểu không tương thích. Vì vậy so sánh mỗi chuỗi `getName()` chưa đủ để khẳng định hai class có cùng danh tính.

## 3. `java.lang.reflect`: từ metadata tới thao tác

| API | Vai trò |
| --- | --- |
| `Constructor<T>` | Mô tả constructor; `newInstance(args...)` tạo object |
| `Method` | Mô tả method; `invoke(receiver, args...)` gọi method |
| `Field` | Mô tả field; `get(receiver)` / `set(receiver, value)` khi có quyền |
| `Modifier` | Giải mã bit modifier, ví dụ `Modifier.isPublic(...)` |
| `Executable`, `Parameter` | Metadata chung của method/constructor và tham số |
| `Type`, `ParameterizedType`, `TypeVariable`, `WildcardType` | Chữ ký kiểu generic được khai báo |
| `Array` | Tạo hoặc truy cập mảng động qua reflection |
| `Proxy`, `InvocationHandler` | Proxy động dựa trên interface |
| `RecordComponent` | Metadata từng thành phần record |

`Class` trả về những metadata này, chẳng hạn `User.class.getDeclaredFields()` trả về một mảng `Field[]`. Các đối tượng phản chiếu có thể chứa tên, kiểu, modifier và annotation; một số còn có khả năng tác động tới instance khi các kiểm tra access cho phép.

### `get...` và `getDeclared...` khác nhau ra sao?

| Tra cứu | Chỉ public hay mọi visibility? | Có tìm phần kế thừa? |
| --- | --- | --- |
| `getMethod(name, parameterTypes...)` / `getMethods()` | Public | Có, method public kế thừa |
| `getDeclaredMethod(...)` / `getDeclaredMethods()` | Mọi visibility | Không: chỉ khai báo trực tiếp |
| `getField(name)` / `getFields()` | Public | Có, field public kế thừa |
| `getDeclaredField(name)` / `getDeclaredFields()` | Mọi visibility | Không: chỉ khai báo trực tiếp |
| `getConstructor(...)` / `getConstructors()` | Public | Không: constructor không kế thừa |
| `getDeclaredConstructor(...)` / `getDeclaredConstructors()` | Mọi visibility | Không: constructor không kế thừa |

**Chữ ký tra cứu phải chính xác.** Nếu có `setAge(int)`, phải dùng `getMethod("setAge", int.class)`; `Integer.class` là kiểu khác. Việc tìm method không đoán overload từ giá trị truyền vào; method lookup dùng tên và các kiểu tham số khai báo. Thứ tự các mảng trả về bởi `getDeclaredMethods()`, `getMethods()`... **không được bảo đảm**, nên nếu cần đầu ra xác định, hãy sắp xếp chúng theo tiêu chí rõ ràng.

## 4. Ví dụ Java 17+ đầy đủ, chạy được

Chương trình minh họa chuỗi điển hình: `Class` → tìm constructor → khởi tạo → tìm method → gọi động → khảo sát field private qua metadata. Để giữ tính di động, ví dụ **không truy cập dữ liệu private**, chỉ xem tên và kiểu của field.

Tạo tệp `ReflectionDemo.java`:

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectionDemo {
    public static class User {
        private final String name;

        public User(String name) {
            this.name = name;
        }

        public String greet(String prefix) {
            return prefix + ", " + name;
        }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Class<User> type = User.class;

        System.out.println(type.getName());
        System.out.println(type.getSuperclass().getSimpleName());

        Constructor<User> constructor =
                type.getDeclaredConstructor(String.class);
        User user = constructor.newInstance("Lan");

        Method greet = type.getMethod("greet", String.class);
        Object result = greet.invoke(user, "Xin chào");
        System.out.println(result);

        Field field = type.getDeclaredField("name");
        System.out.println(field.getName() + ": " +
                field.getType().getSimpleName());

        System.out.println(type.isInstance(user));
    }
}
```

Biên dịch/chạy bằng JDK 17 trở lên:

```bash
javac ReflectionDemo.java
java ReflectionDemo
```

Stdout:

```text
ReflectionDemo$User
Object
Xin chào, Lan
name: String
true
```

Giải thích chính xác từng phần:

1. `User.class` trả về `Class<User>`; do đây là lớp lồng static, tên nhị phân là `ReflectionDemo$User`. Superclass trực tiếp là `Object`.
2. `getDeclaredConstructor(String.class)` tìm constructor nhận đúng một `String`. `newInstance("Lan")` thực sự chạy constructor để tạo `User`.
3. `getMethod("greet", String.class)` tra cứu method public nhận `String`. `invoke(user, "Xin chào")` gọi method trên instance `user`, trả về `Object` chứa chuỗi kết quả.
4. `getDeclaredField("name")` trả về metadata của field private, dù không có getter. `getName()` và `getType()` không cần đọc giá trị field.
5. `isInstance(user)` kiểm tra tương thích kiểu thực của đối tượng.

Ở đây kiểu `User` đã biết từ compile time, nên mã trực tiếp `new User("Lan")` và `user.greet("Xin chào")` vẫn đơn giản, dễ refactor và có type checking tốt hơn. Reflection có giá trị khi kiểu hoặc method chỉ được xác định **lúc runtime**.

## 5. Ranh giới truy cập, module và ngoại lệ

Reflection vẫn chịu quy tắc access của Java:

- `getDeclaredField("name")` có thể tìm metadata của field private, nhưng `field.get(user)` thường ném `IllegalAccessException` khi caller không có quyền truy cập.
- `AccessibleObject.trySetAccessible()` hoặc `setAccessible(true)` cho phép **thử** truy cập sâu trong những trường hợp được JVM/module cho phép. `trySetAccessible()` có thể trả `false`, còn `setAccessible(true)` có thể ném `InaccessibleObjectException`; không phải cơ chế vượt mọi ranh giới module.
- Theo **JPMS**, `exports` cho phép code ngoài module truy cập public API của package theo quy tắc module; `opens` cho phép deep reflection đến các thành viên không public cho module được chỉ định. Framework có thể cần package `opens` phù hợp thay vì tăng quyền toàn ứng dụng.
- Với static method, `method.invoke(null, args...)` dùng receiver `null`; static field cũng dùng `field.get(null)`. Instance member đòi hỏi receiver đúng kiểu.
- Không nên dùng reflection để sửa các field `final` như một cơ chế cập nhật trạng thái: việc ghi có những hạn chế riêng, và không bảo đảm hành vi như setter thông thường.

Các lỗi cần nhận diện:

| Vấn đề | Exception / nguyên nhân |
| --- | --- |
| Sai tên class trong `Class.forName` | `ClassNotFoundException`; kiểm tra tên nhị phân và class loader |
| Sai tên/chữ ký method hoặc field | `NoSuchMethodException` / `NoSuchFieldException` |
| Caller không được phép sử dụng thành viên | `IllegalAccessException` |
| Yêu cầu deep reflection bị từ chối | `InaccessibleObjectException` (runtime) |
| Receiver, số lượng hoặc kiểu argument không hợp lệ | `IllegalArgumentException` |
| Constructor/method đích tự ném exception | `InvocationTargetException` bọc lỗi thật; xem `getCause()` |

`ReflectiveOperationException` là superclass chung cho nhiều lỗi checked ở reflection nhưng **không bao gồm mọi lỗi runtime** như `IllegalArgumentException` hoặc `InaccessibleObjectException`. Ở boundary reflection, log/chuyển tiếp lỗi gốc có ngữ cảnh; không được im lặng nuốt `InvocationTargetException`. `Class.newInstance()` đã deprecated: thay bằng `type.getDeclaredConstructor(...).newInstance(...)` để chọn constructor rõ ràng và xử lý lỗi tốt hơn.

## 6. Reflection, generic và annotation

**Type erasure** khiến thông tin tham số generic không tạo ra class runtime riêng cho mỗi kiểu tham số: chẳng hạn `ArrayList<String>` và `ArrayList<Integer>` có cùng runtime class `ArrayList`; `obj instanceof List<String>` không hợp lệ khi `obj` có kiểu tĩnh `Object`, vì cần chuyển đổi unchecked. Từ Java 16, phép kiểm tra với kiểu tham số hóa có thể hợp lệ nếu kiểu tĩnh của biểu thức đã đủ thông tin, chẳng hạn `List<String> values` được kiểm tra bằng `values instanceof ArrayList<String>`; điều này vẫn không kiểm tra kiểu từng phần tử tại runtime. Nhưng metadata **chữ ký khai báo** vẫn có thể truy xuất qua `Type`: `Field.getType()` thường trả kiểu thô `List.class`, còn `Field.getGenericType()` có thể trả `ParameterizedType` diễn tả `List<String>` nếu field được khai báo đúng như vậy. Điều đó không xác nhận từng phần tử runtime thực sự là `String`.

**Annotation** là một lý do reflection phổ biến trong framework. `getAnnotation(MyAnnotation.class)` và `isAnnotationPresent(MyAnnotation.class)` tra cứu metadata annotation khi annotation có `@Retention(RetentionPolicy.RUNTIME)`. Với retention `SOURCE` hoặc `CLASS`, không thể giả định reflection thấy annotation đó ở runtime. `@Inherited` chỉ ảnh hưởng cách kế thừa annotation trên lớp theo quy tắc riêng; không làm annotation trên method/field tự động kế thừa tương tự.

**Dynamic proxy:** `java.lang.reflect.Proxy` và `InvocationHandler` có thể tạo implementation cho một hoặc nhiều interface ở runtime, để chặn hoặc định tuyến lời gọi (ví dụ RPC, mock, interceptor). JDK dynamic proxy không phải cơ chế tự động subclass mọi concrete class.

## 7. Ứng dụng thực tế và lựa chọn thay thế

Reflection thường hiện diện trong:

- **Dependency injection:** đọc constructor, annotation, kiểu parameter để tạo và nối dependency.
- **ORM, serializer/deserializer:** ánh xạ field hoặc property dựa vào metadata, dưới những quyền truy cập cho phép.
- **Test framework:** phát hiện method có annotation runtime và gọi chúng theo quy ước.
- **Plugin, trình khám phá metadata và công cụ chẩn đoán:** nạp, kiểm tra hoặc tương tác với những kiểu chưa biết ở thời điểm biên dịch.

Cần đánh đổi: reflection làm yếu khả năng phát hiện lỗi lúc compile time, nhạy với rename/chữ ký và ranh giới module, có thêm exception runtime và có thể tốn chi phí tra cứu/gọi động. Không có một hệ số hiệu năng cố định cho mọi JVM; tránh tra cứu metadata lặp lại trên đường nóng, cache có kiểm soát và đo hiệu năng khi cần. Nếu mục tiêu chính là gọi động với hiệu năng hoặc kiểm soát access rõ hơn, nghiên cứu `MethodHandle` và `MethodHandles.Lookup` của gói **`java.lang.invoke`** (gói này khác `java.lang.reflect`).

**Quy tắc thực hành:** nếu biết kiểu và method từ compile time, ưu tiên lời gọi Java trực tiếp; nếu chỉ có metadata tại runtime, bắt đầu với `Class<?>`, tìm đúng thành viên phản chiếu, kiểm tra accessibility, gọi có chủ đích và xử lý nguyên nhân lỗi.

## 8. Câu hỏi tự kiểm tra

1. `Class` mô tả điều gì; tại sao nó không thuộc gói `java.lang.reflect`?
2. Vì sao `getDeclaredField("secret")` tìm được field private nhưng `Field.get(obj)` vẫn có thể thất bại?
3. `getMethod` và `getDeclaredMethod` khác nhau như thế nào khi method khai báo ở superclass?
4. Tra cứu `f(int)` với `getMethod` phải truyền `int.class` hay `Integer.class`?
5. Vì sao `exports` trong JPMS chưa đủ cho framework truy cập private field của một package?
6. `getGenericType()` có bảo đảm dữ liệu thực trong một `List<String>` đều là `String` không?

Nếu trả lời được, bạn đã nắm vai trò hai tầng: **`Class` định danh/khám phá kiểu; `java.lang.reflect` diễn tả và thao tác lên thành viên; JVM vẫn quản lý kiểu và quyền truy cập.**


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-overview-005">Quan hệ giữa reflection, kế thừa, generic và annotation</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ giữa reflection, kế thừa, generic và annotation trong Java

## Mục tiêu học tập

Sau bài này, bạn có thể giải thích **reflection nhìn thấy gì ở thời điểm chạy** khi một lớp có lớp cha, kiểu generic và annotation; chọn đúng API để đọc metadata; phân biệt metadata khai báo với hành vi thực khi gọi phương thức; và nhận diện các giới hạn thường gặp khi viết framework, thư viện hoặc công cụ phân tích mã Java.

Bốn khái niệm liên quan nhưng có vai trò khác nhau:

| Khái niệm | Vai trò trong chương trình | Reflection có thể quan sát |
| --- | --- | --- |
| **Kế thừa (inheritance)** | Tạo quan hệ lớp cha–con; thành viên có thể được thừa hưởng hoặc ghi đè | Cây kiểu, thành viên khai báo/truy cập được, lớp khai báo thành viên |
| **Generic** | Kiểm tra kiểu ở thời điểm biên dịch; mô tả kiểu tham số hóa | Kiểu đã bị xóa khi chạy và một phần thông tin khai báo generic được lưu trong metadata |
| **Annotation** | Gắn metadata có cấu trúc lên khai báo hoặc vị trí dùng kiểu | Annotation còn hiệu lực ở runtime, tùy `@Retention` và API truy vấn |
| **Reflection** | Đọc metadata và có thể thao tác động trên đối tượng/thành viên | Các đối tượng `Class`, `Method`, `Field`, `Constructor`, `Type`, `Annotation` |

**Điểm mấu chốt:** reflection không thay thế quy tắc kế thừa, không làm generic trở thành kiểm tra kiểu đầy đủ ở runtime, và không tự thực thi ý nghĩa nghiệp vụ của annotation. Nó chỉ cung cấp cơ chế quan sát hoặc gọi thành viên theo những quy tắc Java đã định nghĩa.

## 1. Kế thừa quyết định tập thành viên mà reflection có thể nhìn thấy

### 1.1. Hai nhóm API rất dễ nhầm

| Nhu cầu | API tiêu biểu | Ý nghĩa |
| --- | --- | --- |
| Lấy phương thức `public`, kể cả phương thức thừa hưởng | `clazz.getMethods()`, `clazz.getMethod(name, types...)` | Xét các phương thức `public` của kiểu đó và các phương thức `public` kế thừa từ lớp cha/interface |
| Lấy phương thức **khai báo trực tiếp** ở lớp đang xét | `clazz.getDeclaredMethods()`, `clazz.getDeclaredMethod(...)` | Bao gồm `private`, `protected`, package-private và `public`; không tự đi lên lớp cha |
| Lấy trường `public`, kể cả trường thừa hưởng | `clazz.getFields()`, `clazz.getField(name)` | Không bao gồm trường không `public` |
| Lấy trường khai báo trực tiếp | `clazz.getDeclaredFields()`, `clazz.getDeclaredField(name)` | Không tự tìm trường lớp cha |
| Lấy constructor | `clazz.getConstructors()` hoặc `clazz.getDeclaredConstructors()` | Chỉ constructor của chính lớp: **constructor không được kế thừa** |
| Xem quan hệ cha/interface | `getSuperclass()`, `getInterfaces()` | Trả về kiểu cha trực tiếp và các interface trực tiếp |
| Giữ thông tin generic của quan hệ cha | `getGenericSuperclass()`, `getGenericInterfaces()` | Trả về `Type`, có thể là `ParameterizedType` hoặc `Class` |

Ví dụ: nếu `Child extends Parent` và `Parent` có phương thức `public void ping()` thì `Child.class.getMethod("ping")` tìm được nó, nhưng `Child.class.getDeclaredMethod("ping")` sẽ ném `NoSuchMethodException` nếu `Child` không khai báo lại `ping`. Khi tìm metadata khai báo ở cả các lớp cha, hãy duyệt `getSuperclass()` và đọc `getDeclaredMethods()` theo từng cấp.

### 1.2. Ghi đè, che khuất và nơi khai báo là ba chuyện khác nhau

- **Override:** một phương thức instance phù hợp trong lớp con ghi đè phương thức lớp cha. Khi dùng `Method.invoke(object, args)` cho một phương thức instance, Java vẫn thực hiện **dispatch động** theo kiểu thực của đối tượng; `Method` lấy từ lớp cha vẫn có thể gọi phần triển khai override ở lớp con.
- **Overload:** cùng tên nhưng khác danh sách tham số; reflection yêu cầu **kiểu tham số chính xác** khi gọi `getMethod(name, parameterTypes...)`. Không thể chỉ dựa vào tên phương thức.
- **Field hiding:** trường cùng tên ở lớp con *che khuất* trường lớp cha, không dispatch đa hình như phương thức. `Field.getDeclaringClass()` cho biết đang đọc đúng trường nào.
- **Static method hiding:** phương thức `static` không override như phương thức instance; `Method.invoke` dùng phương thức static được khai báo, không dispatch động theo đối tượng.
- **Khả năng truy cập:** tìm thấy một `private` member không đồng nghĩa được phép đọc/gọi nó. `setAccessible(true)` hoặc `trySetAccessible()` phải tuân thủ kiểm tra truy cập và ranh giới module (JPMS).

Danh sách trả về từ `getMethods()` hoặc `getDeclaredMethods()` **không có thứ tự bảo đảm**. Nếu cần hiển thị ổn định hoặc sinh mã, hãy tự sắp xếp theo quy tắc rõ ràng.

## 2. Generic: còn metadata khai báo, nhưng kiểu tham số bị xóa phần lớn khi chạy

### 2.1. Type erasure tác động trực tiếp đến reflection

Trong Java, `List<String>` và `List<Integer>` thường cùng có raw runtime class `List`/`ArrayList`. Bạn không thể hỏi một đối tượng `new ArrayList<String>()` để lấy lại chắc chắn `String` chỉ từ `object.getClass()`. Compiler kiểm tra generic, chèn ép kiểu (cast) khi cần và xóa phần lớn thông tin tham số kiểu trong biểu diễn runtime.

Tuy nhiên, **khai báo** có thể giữ generic signature:

- `Field.getType()` trả về `Class<?>` của kiểu sau xóa, còn `Field.getGenericType()` trả về `Type` có thể chứa `List<String>`.
- `Method.getReturnType()` và `getParameterTypes()` cho các kiểu sau xóa; `getGenericReturnType()` và `getGenericParameterTypes()` trả về kiểu generic khai báo.
- `Class.getSuperclass()` trả về raw superclass, còn `Class.getGenericSuperclass()` có thể giữ quan hệ `Parent<String>`.
- `Class.getTypeParameters()` và `Method.getTypeParameters()` trả về các biến kiểu như `T`, `E` được khai báo ở lớp/phương thức.

Đừng đồng nhất `Type` với `Class<?>`. Các dạng quan trọng của `Type`:

| Dạng | Ví dụ khai báo | Ý nghĩa |
| --- | --- | --- |
| `Class<?>` | `String`, `int[]` | Kiểu cụ thể không tham số hóa trong biểu diễn đó |
| `ParameterizedType` | `List<String>` | Raw type cùng các đối số kiểu thực tế |
| `TypeVariable<?>` | `T` trong `class Box<T>` | Biến kiểu và các bound của nó |
| `WildcardType` | `? extends Number` | Wildcard và giới hạn trên/dưới |
| `GenericArrayType` | `T[]` | Kiểu mảng có thành phần là một generic type chưa thành `Class` cụ thể |

### 2.2. Generic ở lớp cha khác generic trên phương thức lớp cha

Giả sử `class Parent<T> { T read(); }` và `class Child extends Parent<String>`. Khi gọi `Child.class.getGenericSuperclass()`, metadata của *quan hệ extends* có thể chứa `String`. Nhưng `Parent.class.getDeclaredMethod("read").getGenericReturnType()` vẫn là biến kiểu `T` do `Parent` khai báo: reflection **không tự thay `T` bằng `String` trong metadata của chính `Parent`**.

Một công cụ cần suy luận kiểu trả về thực của `read` trong ngữ cảnh `Child` phải tự xây dựng ánh xạ `T -> String` rồi áp dụng ánh xạ qua từng cấp kế thừa (có thể qua nhiều lớp và interface). Với khai báo raw type hoặc thông tin generic không còn lưu, việc suy luận không phải lúc nào cũng thành công.

### 2.3. Bridge method là điểm giao giữa kế thừa và erasure

Để bảo toàn tính override khi signature bị xóa, compiler có thể sinh **bridge method**. Chẳng hạn, `Parent<T>.convert(T)` có tham số erasure là `Object`, còn `Child extends Parent<String>` override bằng `convert(String)`. Compiler tạo một bridge như `convert(Object)` trong `Child` để lời gọi qua kiểu `Parent` vẫn đến phần triển khai `convert(String)`.

Reflection có thể thấy cả phương thức nguồn và bridge trong `getDeclaredMethods()`. Khi quét phương thức để dựng API, routing, logging hoặc validation, thường cần kiểm tra `Method.isBridge()` và `Method.isSynthetic()` nhằm tránh đăng ký trùng. Nhưng khi gọi phương thức, không nên xóa/sửa bridge: nó phục vụ tương thích và dispatch của JVM.

## 3. Annotation: metadata của lớp hoặc thành viên có quy tắc kế thừa riêng

### 3.1. Retention và target phải đúng

- `@Retention(SOURCE)`: annotation chỉ có ở mã nguồn, không còn trong class file.
- `@Retention(CLASS)`: nằm trong class file nhưng **không truy cập bằng reflection runtime thông thường**; đây là mức mặc định nếu không khai báo `@Retention`.
- `@Retention(RUNTIME)`: có thể tra bằng API reflection khi chương trình chạy.
- `@Target(TYPE)` áp dụng cho khai báo kiểu; `METHOD`, `FIELD`, `PARAMETER` áp dụng cho từng loại thành viên; `TYPE_USE` dành cho các vị trí **dùng kiểu** như `List<@Checked String>`.

Annotation chỉ khai báo dữ liệu. Chẳng hạn `@Audit` không tự ghi log; phải có mã chủ động tìm annotation và thực hiện hành vi tương ứng.

### 3.2. `@Inherited` có phạm vi hẹp

`@Inherited` đặt **trên khai báo loại annotation**, và chỉ có tác dụng khi truy vấn annotation trên **khai báo class** của một lớp con; Java tìm dọc superclass nếu lớp con không khai báo annotation đó.

Vì vậy:

- `Child.class.getAnnotation(Audited.class)` có thể nhận annotation `@Audited` từ superclass nếu `Audited` được đánh dấu `@Inherited`.
- `Child.class.getDeclaredAnnotation(Audited.class)` chỉ xét lớp `Child`, nên có thể là `null`.
- `@Inherited` **không truyền annotation từ interface mà class implements**, không áp dụng tự động cho annotation trên method/field/constructor, và không khiến annotation của phương thức cha xuất hiện trên phương thức override.
- Để xử lý annotation trên interface, phương thức override, hoặc meta-annotation do ứng dụng định nghĩa, framework phải chủ động duyệt và đặt quy tắc kết hợp metadata.

Đối với annotation lặp lại (`@Repeatable`), dùng `getAnnotationsByType(...)` / `getDeclaredAnnotationsByType(...)` khi muốn lấy nhiều giá trị; tránh giả định `getAnnotation(...)` luôn đại diện đủ các annotation lặp.

### 3.3. Metadata của khai báo và của kiểu được dùng là khác nhau

Ví dụ trường `List<@Checked String> names`: annotation `@Checked` thuộc **type argument `String`**, không phải annotation khai báo trực tiếp của trường `names`. Vì thế `Field.getAnnotation(Checked.class)` không đọc nó; phải dùng `Field.getAnnotatedType()`, ép hợp lệ sang `AnnotatedParameterizedType` và truy cập `getAnnotatedActualTypeArguments()`.

Tương tự, Java có `Method.getAnnotatedReturnType()`, `getAnnotatedParameterTypes()` và `Class.getAnnotatedSuperclass()` khi cần annotation ở vị trí dùng kiểu. Các annotation này cũng phải có `RUNTIME` retention nếu muốn quan sát bằng reflection.

## 4. Ví dụ tích hợp Java 17+: kế thừa + generic + annotation + reflection

Lưu đoạn sau thành **KnowledgeReflectionDemo.java**. Chương trình dùng một lớp cha generic, một lớp con chuyên biệt kiểu `String`, một annotation trên lớp có `@Inherited` và một annotation đặt trên phương thức lớp cha.

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Locale;

public class KnowledgeReflectionDemo {
    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Audited {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Label {
        String value();
    }

    @Audited
    static class Parent<T> {
        @Label("parent")
        public T convert(T input) {
            return input;
        }

        public String ping() {
            return "parent";
        }
    }

    static class Child extends Parent<String> {
        @Override
        public String convert(String input) {
            return input.toUpperCase(Locale.ROOT);
        }
    }

    public static void main(String[] args) throws Exception {
        Class<Child> type = Child.class;

        System.out.println("Inherited annotation: "
                + type.isAnnotationPresent(Audited.class));
        System.out.println("Declared annotation: "
                + (type.getDeclaredAnnotation(Audited.class) != null));

        ParameterizedType parentType =
                (ParameterizedType) type.getGenericSuperclass();
        System.out.println("Actual parent argument: "
                + parentType.getActualTypeArguments()[0].getTypeName());

        Method parentMethod = Parent.class.getMethod("convert", Object.class);
        Method childMethod = Child.class.getDeclaredMethod("convert", String.class);
        System.out.println("Base method generic return: "
                + parentMethod.getGenericReturnType().getTypeName());
        System.out.println("Child override has @Label: "
                + childMethod.isAnnotationPresent(Label.class));
        System.out.println("Parent method label: "
                + parentMethod.getAnnotation(Label.class).value());

        Method inherited = type.getMethod("ping");
        System.out.println("Inherited method declared by Parent: "
                + (inherited.getDeclaringClass() == Parent.class));

        long bridges = Arrays.stream(type.getDeclaredMethods())
                .filter(Method::isBridge)
                .count();
        System.out.println("Bridge methods in Child: " + bridges);

        Object result = parentMethod.invoke(new Child(), "java");
        System.out.println("Reflective call result: " + result);
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac KnowledgeReflectionDemo.java
java KnowledgeReflectionDemo
~~~

Kết quả:

~~~text
Inherited annotation: true
Declared annotation: false
Actual parent argument: java.lang.String
Base method generic return: T
Child override has @Label: false
Parent method label: parent
Inherited method declared by Parent: true
Bridge methods in Child: 1
Reflective call result: JAVA
~~~

**Giải thích các quan sát:**

1. `Audited` được đánh dấu `@Inherited` nên truy vấn trên `Child` thấy annotation của `Parent`, nhưng truy vấn *declared* trên `Child` không thấy.
2. Metadata `Child extends Parent<String>` giữ đối số `java.lang.String`. Trái lại, kiểu trả về khai báo của `Parent.convert(T)` vẫn là `T`.
3. `@Label("parent")` tồn tại trên method của `Parent`; `Child.convert(String)` không tự kế thừa annotation của method bị override.
4. `ping()` là phương thức `public` kế thừa; `getMethod("ping")` lấy được `Method` có `getDeclaringClass() == Parent.class`.
5. Bridge `convert(Object)` giúp lời gọi qua kiểu `Parent` tìm đúng `Child.convert(String)`. Vì `Method.invoke` trên instance áp dụng dispatch động, kết quả là `JAVA` dù `Method` được lấy từ `Parent`.

Lưu ý: `getDeclaredMethods()` có thể chứa cả phương thức nguồn lẫn bridge/synthetic; không dựa vào thứ tự phần tử mảng để suy ra đâu là phương thức cần dùng.

## 5. Ví dụ riêng về generic field và annotation ở vị trí dùng kiểu

Ví dụ trước dùng annotation trên **khai báo class/method**. Ví dụ này làm rõ một trường có generic signature và annotation **trên type argument**. Lưu thành **TypeUseDemo.java**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.List;

public class TypeUseDemo {
    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Checked {}

    static class Input {
        List<@Checked String> names;
    }

    public static void main(String[] args) throws Exception {
        Field field = Input.class.getDeclaredField("names");
        ParameterizedType genericType =
                (ParameterizedType) field.getGenericType();
        AnnotatedParameterizedType annotatedType =
                (AnnotatedParameterizedType) field.getAnnotatedType();

        System.out.println("Raw field type: "
                + field.getType().getTypeName());
        System.out.println("Type argument: "
                + genericType.getActualTypeArguments()[0].getTypeName());
        System.out.println("Type-use annotation: "
                + annotatedType.getAnnotatedActualTypeArguments()[0]
                        .isAnnotationPresent(Checked.class));
    }
}
~~~

~~~bash
javac TypeUseDemo.java
java TypeUseDemo
~~~

~~~text
Raw field type: java.util.List
Type argument: java.lang.String
Type-use annotation: true
~~~

`getType()` chỉ thấy `List` đã xóa tham số, `getGenericType()` thấy `String` ở khai báo trường, còn `getAnnotatedType()` truy cập annotation trên vị trí dùng kiểu. Không có bước nào tự chứng minh mọi phần tử được đưa vào danh sách ở runtime đều thực sự là `String`; đó vẫn là trách nhiệm của hệ thống kiểu và logic kiểm tra dữ liệu.

## 6. Áp dụng khi xây dựng công cụ dùng reflection

Xét một thư viện cần tìm các phương thức gắn `@Action` trên một đối tượng có nhiều lớp cha. Một quy trình có thể kiểm chứng được:

1. **Xác định quy tắc kế thừa mong muốn.** Chỉ đọc phương thức `public` có thể truy cập bằng `getMethods()`, hay quét tất cả khai báo qua từng cấp bằng `getDeclaredMethods()`? Annotation của method override có được ứng dụng *chủ động* lấy từ method cha hay không?
2. **Duyệt kiểu có chủ đích.** Khi dùng `getDeclaredMethods()`, lần lượt duyệt superclass bằng `getSuperclass()`; xét interface riêng nếu yêu cầu. Lưu `getDeclaringClass()` để xác định nguồn metadata.
3. **Xử lý bridge/synthetic và override.** Kiểm tra `isBridge()`/`isSynthetic()`, đối chiếu tên + kiểu tham số + quan hệ override để tránh cùng một hành vi được đăng ký hai lần. Không giả định mọi phương thức cùng tên là một.
4. **Đọc annotation theo vị trí.** `getDeclaredAnnotation()` chỉ lấy trực tiếp; `getAnnotation()` trên class có thể tôn trọng `@Inherited`; đối với method cha/interface phải tự định nghĩa thuật toán tìm. Annotation gắn vào generic type argument cần API `AnnotatedType`.
5. **Giải kiểu generic theo ngữ cảnh.** Trước tiên kiểm tra `Type` bằng `instanceof`; nếu gặp `TypeVariable` thì duyệt các quan hệ `ParameterizedType` giữa lớp cụ thể và nơi khai báo rồi thay biến kiểu theo mapping, bao gồm các trường hợp generic lồng nhau và nhiều cấp.
6. **Thực thi có kiểm soát.** Kiểm tra modifier, quyền truy cập, tham số, kiểu receiver và lỗi có thể phát sinh. `Method.invoke` có thể ném `IllegalAccessException`, `IllegalArgumentException`, hoặc `InvocationTargetException` (bọc lỗi từ chính phương thức đích). Trong module Java, việc mở `private` member có thể bị từ chối.
7. **Chọn và kiểm thử chính sách xung đột.** Nếu cả method lớp con và lớp cha có annotation, cần quy định cái nào ưu tiên; nếu có annotation trên interface, chỉ lấy khi nghiệp vụ quy định; nếu là repeatable annotation, phải xử lý tất cả giá trị thích hợp.

**Ví dụ thực tế:** một framework mapping dữ liệu nhìn vào `Field.getGenericType()` để suy ra kiểu phần tử của `List<User>`, xem annotation trên trường hoặc trên `User` để quyết định chuyển đổi, rồi chọn setter `public` hay constructor phù hợp. Nếu lớp entity kế thừa từ entity cha, framework phải quyết định có quét field cha và xử lý field bị che khuất không. Reflection cung cấp dữ liệu để quyết định; chính framework sở hữu các quy tắc này.

## 7. Những hiểu lầm thường gặp và cách tự kiểm tra

| Hiểu lầm | Thực tế / cách kiểm tra |
| --- | --- |
| “`getDeclaredMethods()` có mọi method dùng được của object.” | Nó chỉ trả những method khai báo trong `Class` đang xét; muốn member thừa hưởng thì dùng `getMethods()` hoặc duyệt cấp cha. |
| “`Method` của lớp cha luôn gọi thân method cha.” | Với instance method bị override, `invoke` vẫn dispatch động theo receiver. Static method thì khác. |
| “Reflection lấy được `T=String` từ mọi đối tượng generic.” | Type erasure làm mất nhiều thông tin runtime; metadata quan hệ `extends Parent<String>` chỉ giúp giải được một số ngữ cảnh. |
| “`@Inherited` áp dụng cho method và interface.” | Nó chỉ có tác dụng với annotation **trên class** được tìm qua **superclass**. |
| “Không tìm thấy annotation tức annotation không tồn tại trong mã.” | Có thể annotation không có `RUNTIME` retention, đang ở method cha, hoặc nằm ở vị trí dùng kiểu thay vì khai báo. |
| “Mỗi method trong `getDeclaredMethods()` tương ứng một method mã nguồn.” | Compiler có thể thêm bridge/synthetic methods. |
| “`setAccessible(true)` luôn mở được private member.” | Kiểm tra truy cập, module JPMS và chính sách runtime có thể từ chối. |
| “Có annotation thì tính năng tự chạy.” | Phải có thành phần đọc annotation và triển khai hành vi. |

Để tự kiểm tra, hãy trả lời ba câu hỏi: (a) `getMethod` và `getDeclaredMethod` khác nhau thế nào khi method chỉ có ở cha? (b) tại sao `Parent<T>.convert(T)` vẫn có generic return `T` dù `Child extends Parent<String>`? (c) vì sao `@Inherited` cho kết quả khác nhau giữa `Child.class.getAnnotation(...)` và `Child.class.getDeclaredAnnotation(...)`? Nếu giải thích được cả ba bằng ví dụ trên, bạn đã nắm được ranh giới giữa metadata khai báo, quan hệ kế thừa và hành vi runtime.

### Tài liệu API chính thức

- Java SE 21, `Class`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html
- Java SE 21, `Method`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Method.html
- Java SE 21, `Inherited`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/annotation/Inherited.html

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-overview-006">Khi nào nên dùng reflection và khi nào gọi trực tiếp</a>

<details>
<summary>Xem chi tiết</summary>

# Khi nào nên dùng reflection và khi nào gọi trực tiếp trong Java?

Trong Java, **gọi trực tiếp** là lựa chọn mặc định khi chương trình biết kiểu và API cần dùng lúc biên dịch. **Reflection** là cơ chế kiểm tra cấu trúc của lớp và truy cập constructor, method, field hoặc annotation **khi chương trình đang chạy** thông qua các API như `Class`, `Constructor`, `Method` và `Field` trong `java.lang.reflect`.

Cả hai cách đều có thể dẫn tới cùng một hành vi. Quyết định nằm ở chỗ **mức độ động của yêu cầu**: có thật sự cần khám phá tên lớp, phương thức hoặc metadata ở runtime không? Reflection giúp giải bài toán động, nhưng tăng chi phí bảo trì, giảm kiểm tra kiểu tại compile time và có thêm giới hạn truy cập.

## 1. Phân biệt cơ chế

- **Gọi trực tiếp:** viết `service.execute()`, `new Service()` hoặc gọi qua interface. Compiler kiểm tra method có tồn tại, tham số/kiểu trả về có hợp lệ và quyền truy cập có cho phép. IDE có thể tìm tham chiếu, đổi tên và refactor một cách đáng tin cậy hơn.
- **Reflection:** lấy đối tượng mô tả lớp (`Class<?>`), tìm member bằng tên/chữ ký tại runtime rồi gọi qua `Method.invoke(...)` hoặc khởi tạo qua `Constructor.newInstance(...)`. Lỗi tên và chữ ký thường chỉ lộ ra khi chạy.
- **Đa hình không đồng nghĩa với reflection:** `Formatter formatter = ...; formatter.format(text);` vẫn là lời gọi Java thông thường. JVM có thể chọn implementation tại runtime nhờ virtual dispatch mà không cần `Method.invoke`.
- **Có `Class<?>` chưa chắc đã cần gọi method bằng reflection:** `SomeType.class` và `object.getClass()` chỉ lấy thông tin kiểu; chỉ dùng khả năng introspection nào thực sự cần.

Một lời gọi phản chiếu không tự động vượt qua giới hạn `private`, ranh giới Java module hay quyền truy cập. Cơ chế kiểm tra truy cập vẫn được áp dụng.

## 2. Chọn cách nào theo tình huống?

| Tình huống | Lựa chọn ưu tiên | Lý do |
| --- | --- | --- |
| Đã biết lớp, constructor, method và chữ ký khi viết mã | Gọi trực tiếp | An toàn kiểu, dễ refactor, dễ theo dõi luồng gọi |
| Có nhiều cách xử lý cùng một hợp đồng interface | Interface + đa hình, factory hoặc dependency injection | Chọn implementation linh hoạt mà không phải dò tên method |
| Phải đọc annotation, field, method của nhiều kiểu không biết trước | Reflection ở lớp hạ tầng/framework | Metadata chỉ có thể xác định đầy đủ lúc runtime |
| Cần nạp một implementation theo cấu hình hoặc plugin bên ngoài | `ServiceLoader` hoặc reflection có kiểm soát | Tên implementation có thể chưa biết khi biên dịch |
| Muốn gọi private method từ nghiệp vụ hoặc test | Ưu tiên thiết kế lại API/phân tách trách nhiệm | Tránh ghép chặt vào implementation và vi phạm đóng gói |
| Vòng lặp rất nóng, gọi method lặp lại thường xuyên | Ưu tiên lời gọi trực tiếp sau khi đo đạc | Hạn chế chi phí lookup/invoke và chuyển đổi kiểu |
| Truy cập API của dependency mà mã nguồn đã tham chiếu được | Gọi trực tiếp | Reflection thêm sự mong manh mà không tạo lợi ích động |

**Nguyên tắc:** chỉ đưa reflection vào nơi hệ thống thực sự phải xử lý thông tin chưa biết tại compile time; để phần còn lại giao tiếp qua kiểu hoặc interface rõ ràng.

## 3. Ví dụ: cùng một lời gọi, hai cách thực hiện

Tạo tệp `CallChoiceDemo.java`:

```java
import java.lang.reflect.Method;

public class CallChoiceDemo {
    public static class GreetingService {
        public String greet(String name) {
            return "Xin chào, " + name;
        }
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        GreetingService service = new GreetingService();

        // Gọi trực tiếp: compiler biết kiểu và chữ ký.
        String direct = service.greet("An");

        // Reflection: tìm phương thức theo tên/chữ ký lúc chạy.
        Class<?> type = service.getClass();
        Method greetMethod = type.getMethod("greet", String.class);
        Object dynamic = greetMethod.invoke(service, "An");

        System.out.println("Direct: " + direct);
        System.out.println("Reflection: " + dynamic);
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac CallChoiceDemo.java
java CallChoiceDemo
```

Kết quả:

```text
Direct: Xin chào, An
Reflection: Xin chào, An
```

Phân tích:

1. `service.greet("An")` được compiler kiểm tra: đổi tên `greet` mà chưa sửa chỗ gọi sẽ sinh lỗi biên dịch.
2. `getMethod("greet", String.class)` chỉ tìm **public method** có đúng chữ ký tham số `String`, kể cả method public kế thừa. Nếu tên/chữ ký không tồn tại, chương trình nhận `NoSuchMethodException` lúc chạy.
3. `Method.invoke(service, "An")` trả về `Object` (hoặc `null` nếu method trả về `void`). Vì vậy phía gọi có thể cần kiểm tra và ép kiểu khi biết kiểu kết quả.
4. Nếu code bên trong `greet` ném exception, `invoke` bọc exception đó trong `InvocationTargetException`; cần xem `getCause()` để biết lỗi gốc. Lỗi truy cập có thể tạo `IllegalAccessException`.

Ở ví dụ này, reflection **không có lợi ích**: biết rõ `GreetingService` và `greet` ngay từ đầu nên nên dùng gọi trực tiếp.

## 4. Trường hợp reflection có lý do chính đáng: lớp được chọn ở runtime

Một hệ thống plugin có thể nhận tên implementation từ cấu hình, còn phần nghiệp vụ chỉ biết một interface chung. Có thể dùng reflection ở **điểm khởi tạo plugin**, rồi gọi trực tiếp qua interface sau đó.

Tạo tệp `RuntimeFormatterDemo.java`:

```java
import java.util.Locale;

public class RuntimeFormatterDemo {
    public interface Formatter {
        String format(String input);
    }

    public static class UpperFormatter implements Formatter {
        public UpperFormatter() {
        }

        @Override
        public String format(String input) {
            return input.toUpperCase(Locale.ROOT);
        }
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        // Trong ứng dụng thật, giá trị này có thể lấy từ cấu hình tin cậy.
        String className = System.getProperty(
                "formatter.class", UpperFormatter.class.getName());

        Class<?> rawClass = Class.forName(className);
        Class<? extends Formatter> implementation =
                rawClass.asSubclass(Formatter.class);
        Formatter formatter =
                implementation.getDeclaredConstructor().newInstance();

        // Từ đây là đa hình thông thường, không gọi Method.invoke.
        System.out.println(formatter.format("reflection"));
    }
}
```

Chạy:

```bash
javac RuntimeFormatterDemo.java
java RuntimeFormatterDemo
```

Kết quả:

```text
REFLECTION
```

Điểm cần hiểu:

- `Class.forName(className)` tải/tìm class theo tên được biết tại runtime (với dạng này, việc khởi tạo class cũng có thể xảy ra). Class phải có trên classpath/module path phù hợp.
- `asSubclass(Formatter.class)` kiểm tra class có tương thích với `Formatter`; nếu không, phát sinh `ClassCastException`.
- `getDeclaredConstructor().newInstance()` yêu cầu constructor không tham số phù hợp và có thể truy cập từ nơi gọi. Nếu thiếu sẽ có `NoSuchMethodException`; nếu constructor ném lỗi thì lỗi được bọc trong `InvocationTargetException`.
- Sau khi tạo được `Formatter`, gọi `formatter.format(...)` qua interface là đủ. Không cần tiếp tục tìm và invoke method bằng reflection mỗi lần xử lý.
- Tên class lấy từ nguồn không đáng tin cậy có thể dẫn tới nạp/khởi tạo code ngoài ý muốn. Ứng dụng phải giới hạn provider được phép dùng, kiểm soát dependency, và không coi ép kiểu interface là cơ chế bảo mật.

Trong trường hợp hệ thống đã biết danh sách implementation, một **factory/registry** ánh xạ tên tới `Supplier<Formatter>` còn đơn giản hơn. Nếu cần cơ chế plugin chuẩn, **`ServiceLoader<Formatter>`** cho phép tìm provider qua cấu hình dịch vụ mà không phải tự ghép tên class và constructor trong mã nghiệp vụ. Framework dependency injection cũng thường xử lý bài toán chọn implementation tốt hơn reflection tự viết.

## 5. Những tình huống reflection thường được sử dụng

### Đọc metadata và xây dựng công cụ tổng quát

- Framework có thể quét annotation để thiết lập dependency injection, mapping, serialization hoặc validation.
- Công cụ debug, test runner và công cụ khám phá API có thể liệt kê method, field, constructor, annotation từ những lớp chỉ xuất hiện khi chạy.
- Thư viện chuyển đổi dữ liệu tổng quát có thể phải xử lý các kiểu domain không được liệt kê tường minh trong chính thư viện.

Ví dụ, một thư viện có thể duyệt `type.getDeclaredFields()` để đọc metadata field rồi quyết định cách ánh xạ dữ liệu. Việc này khác với nghiệp vụ biết sẵn `customer.getName()`: ở trường hợp sau, getter trực tiếp là giải pháp rõ ràng hơn.

### Plugin và phần mở rộng

Khi implementation do plugin cung cấp sau thời điểm biên dịch, ứng dụng có thể cần nạp class, kiểm tra hợp đồng, tạo instance tại runtime. Reflection hữu ích **ở biên tích hợp**, còn thao tác lâu dài nên thông qua interface đã định nghĩa. Nếu runtime thay đổi phương thức/tên kiểu không có hợp đồng chung, cân nhắc adapter hoặc giao thức ổn định thay vì rải các chuỗi tên method khắp hệ thống.

### Các lựa chọn trước khi dùng reflection

- **Interface/đa hình:** thay cho phân nhánh theo tên class và gọi method động.
- **Factory, registry, dependency injection:** thay cho dựng đối tượng bằng tên class khi tập implementation có thể khai báo được.
- **`ServiceLoader`:** cơ chế provider/plugin chuẩn của Java.
- **Annotation processing lúc biên dịch:** khi có thể tạo metadata/code sớm thay vì quét reflection mỗi lần chạy.
- **`MethodHandle` / `MethodHandles.Lookup`:** công cụ gọi động có kiểu chữ ký rõ hơn trong một số trường hợp nâng cao; vẫn cần kiểm tra quyền truy cập và đo hiệu năng, không phải lối tắt bắt buộc.

## 6. Chi phí, quyền truy cập và độ an toàn

**Kiểm tra kiểu và bảo trì.** Gọi trực tiếp giúp compiler phát hiện lỗi đổi tên hoặc thay đổi tham số sớm. Với `getMethod("save", ...)`, các tên member ở dạng chuỗi không được refactor chắc chắn; lỗi chỉ xuất hiện ở runtime. Nên tập trung chuỗi tên member ở một nơi, kiểm tra kết quả và có test tích hợp cho đường đi động.

**Hiệu năng.** Reflection có chi phí khám phá member, kiểm tra truy cập, gọi động và có thể sinh boxing/unboxing. Chi phí thực tế tùy phiên bản JDK, độ nóng của mã và khả năng tối ưu của JVM: không nên khẳng định mọi lời gọi reflection đều chậm hơn một hệ số cố định. Nếu bắt buộc dùng reflection, **tra cứu `Method`/`Constructor` một lần rồi tái sử dụng** khi hợp lý, tránh `getMethod` bên trong vòng lặp; dùng benchmark thích hợp như JMH trước khi tối ưu đoạn code nhạy cảm.

**Public, private và Java modules.**

- `getMethod(...)` tìm public method; `getDeclaredMethod(...)` tìm method được khai báo trực tiếp ở lớp đó, bao gồm cả non-public, nhưng **tìm thấy không có nghĩa là gọi được**.
- `setAccessible(true)`/`trySetAccessible()` có thể bị giới hạn bởi access checks và cơ chế module. Java 17+ áp dụng đóng gói mạnh đối với nhiều package/module; việc mở truy cập có thể thất bại với `InaccessibleObjectException` hoặc `trySetAccessible()` trả về `false`.
- Trong Java Platform Module System, `exports` cho phép sử dụng public API của package theo quy tắc module; `opens` hỗ trợ deep reflection vào package cho module được cấp quyền. Chỉ mở package thật sự cần cho framework và ưu tiên public contract rõ ràng.
- Không xây chức năng nghiệp vụ phụ thuộc vào private field/method của thư viện bên thứ ba; chúng có thể thay đổi không báo trước.

**Exception và chẩn đoán.** Ngoài `NoSuchMethodException`, cần phân biệt `ClassNotFoundException`, `IllegalAccessException`, `InstantiationException`, `InvocationTargetException` và `ClassCastException` theo bước thực hiện. Khi log `InvocationTargetException`, giữ cả cause gốc. Đưa tên class/method/chữ ký dự kiến vào thông báo lỗi sẽ giúp kiểm tra cấu hình nhanh hơn.

**An toàn và triển khai.** Không lấy tên lớp/phương thức trực tiếp từ input không tin cậy rồi thực thi tùy ý. Với native image hoặc môi trường phân tích mã theo mô hình closed world, reflective access có thể cần khai báo metadata hoặc cơ chế đăng ký phù hợp với runtime/framework; kiểm tra ngay từ giai đoạn build và test chạy thật trên mục tiêu triển khai.

## 7. Quy trình ra quyết định

1. **Tên lớp và method đã biết khi biên dịch?** Dùng gọi trực tiếp.
2. **Chỉ cần đổi implementation theo điều kiện/cấu hình?** Tạo interface + factory/DI/`ServiceLoader`; lời gọi nghiệp vụ vẫn trực tiếp qua interface.
3. **Cần đọc cấu trúc hoặc annotation của kiểu chưa biết trước?** Dùng reflection có giới hạn, gom phần truy cập động vào adapter/hạ tầng, kiểm tra lỗi và cache metadata nếu cần.
4. **Phải gọi method được xác định ở runtime?** Xác thực tên và chữ ký, kiểm tra tương thích, xử lý lỗi truy cập/exception và thu hẹp nơi gọi bằng reflection.
5. **Phải mở private member chỉ để “cho tiện”?** Xem lại ranh giới API hoặc thiết kế trước khi sử dụng deep reflection.
6. **Có nghi ngờ vấn đề hiệu năng/đóng gói/module?** Viết kiểm thử/benchmark ở đúng runtime mục tiêu rồi chọn mức động tối thiểu.

### Tự kiểm tra cách chọn

- Gọi `orderService.submit(order)` trong service đã biết kiểu: **gọi trực tiếp**.
- Chọn `PaymentProcessor` theo provider có sẵn: **interface + factory/DI**, không cần `Method.invoke`.
- Viết thư viện kiểm tra annotation trên những class của ứng dụng mà thư viện chưa biết khi biên dịch: **reflection hợp lý**.
- Nạp implementation được cài thêm sau khi build: dùng **`ServiceLoader` hoặc khởi tạo động có kiểm soát**, rồi gọi qua interface.
- Muốn sửa private field của dependency để tránh tạo API đúng nghĩa: **tránh reflection**, ưu tiên thiết kế/test seam phù hợp.

**Kết luận:** Reflection là công cụ giải quyết yêu cầu động tại runtime. Gọi trực tiếp và đa hình vẫn phù hợp cho phần lớn business logic; reflection nên nằm ở một ranh giới nhỏ, có kiểm tra và xử lý lỗi rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-overview-007">Một ví dụ xuyên suốt: khám phá và sử dụng một kiểu chưa biết trước</a>

<details>
<summary>Xem chi tiết</summary>

# Một ví dụ xuyên suốt: khám phá và sử dụng một kiểu chưa biết trước

Trong Java thông thường, ta viết `new HelloPlugin(...)` và gọi `plugin.greet(...)` khi mã nguồn đã biết lớp cùng phương thức. Reflection giải quyết tình huống khác: ứng dụng nhận **tên lớp dưới dạng dữ liệu lúc chạy**, rồi tìm hiểu kiểu đó, tạo đối tượng và gọi phương thức. Ví dụ xuyên suốt dưới đây đặt toàn bộ quá trình vào một chương trình chạy được.

## 1. Tình huống và luồng xử lý

Giả sử có một chương trình chủ (host) nhận tên một thành phần mở rộng (plugin) từ tham số dòng lệnh. Host chưa biết trước sẽ chọn `HelloPlugin` hay `LoudPlugin`; nó chỉ biết **quy ước**: lớp được chọn phải có constructor `public (String)` và phương thức `public String greet(String)`.

~~~text
Tên lớp nhận lúc chạy (String)
       │
       ▼
Class.forName(...)                 → Class<?>: mô tả kiểu
       │
       ├── getName(), getSuperclass(), getDeclaredFields()
       ├── getAnnotation(...)      → đọc metadata
       │
       ▼
getConstructor(String.class)       → Constructor<?>
       │
       ▼
newInstance("Xin chào")            → Object: đối tượng thật
       │
       ▼
getMethod("greet", String.class)   → Method
       │
       ▼
invoke(instance, "Lan")            → kết quả trả về
~~~

Reflection nằm trong các API `java.lang.Class` và `java.lang.reflect`. `Class<?>` là đối tượng mô tả **một kiểu**; còn `Object` nhận từ `newInstance` là **một instance của kiểu đó**. Hai khái niệm này khác nhau và đều cần trong ví dụ.

## 2. Chương trình Java 17+ hoàn chỉnh

Lưu tất cả nội dung sau vào **`ReflectionWalkthrough.java`**. Hai plugin được đặt trong cùng tệp dưới dạng lớp lồng tĩnh (`static nested class`) để chạy độc lập mà không cần thư viện hay JAR bên ngoài. Đây là môi trường mô phỏng plugin; trong ứng dụng thật, lớp được chọn có thể ở dependency riêng.

~~~java
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

public class ReflectionWalkthrough {
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ComponentInfo {
        String role();
    }

    @ComponentInfo(role = "greeting")
    public static class HelloPlugin {
        private final String prefix;

        public HelloPlugin(String prefix) {
            this.prefix = prefix;
        }

        public String greet(String name) {
            return prefix + ", " + name + "!";
        }
    }

    @ComponentInfo(role = "loud")
    public static class LoudPlugin {
        private final String prefix;

        public LoudPlugin(String prefix) {
            this.prefix = prefix;
        }

        public String greet(String name) {
            return (prefix + ", " + name + "!")
                    .toUpperCase(Locale.ROOT);
        }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        String className = args.length == 0
                ? "ReflectionWalkthrough$HelloPlugin"
                : args[0];

        Class<?> type = Class.forName(className);
        System.out.println("Tên lớp: " + type.getName());
        System.out.println("Lớp cha: " + type.getSuperclass().getName());
        System.out.println("Là interface: " + type.isInterface());

        for (Field field : type.getDeclaredFields()) {
            System.out.println("Field: " + Modifier.toString(field.getModifiers())
                    + " " + field.getType().getSimpleName() + " " + field.getName());
        }

        ComponentInfo info = type.getAnnotation(ComponentInfo.class);
        System.out.println("Vai trò: " + (info == null ? "(không có)" : info.role()));

        Constructor<?> constructor = type.getConstructor(String.class);
        Object instance = constructor.newInstance("Xin chào");

        Method method = type.getMethod("greet", String.class);
        System.out.println("Method: " + method.getReturnType().getSimpleName()
                + " " + method.getName() + "(String)");
        Object result = method.invoke(instance, "Lan");
        System.out.println("Kết quả: " + result);
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~bash
javac -encoding UTF-8 ReflectionWalkthrough.java
java ReflectionWalkthrough
~~~

Đầu ra (stdout):

~~~text
Tên lớp: ReflectionWalkthrough$HelloPlugin
Lớp cha: java.lang.Object
Là interface: false
Field: private final String prefix
Vai trò: greeting
Method: String greet(String)
Kết quả: Xin chào, Lan!
~~~

Chạy với tên plugin còn lại:

~~~bash
java 'ReflectionWalkthrough$LoudPlugin'
~~~

Đầu ra:

~~~text
Tên lớp: ReflectionWalkthrough$LoudPlugin
Lớp cha: java.lang.Object
Là interface: false
Field: private final String prefix
Vai trò: loud
Method: String greet(String)
Kết quả: XIN CHÀO, LAN!
~~~

**Lưu ý về shell:** Dấu nháy đơn quanh tên có ký tự `$` dành cho Bash/Zsh và PowerShell, giúp chuyển nguyên tên lớp lồng vào Java (không để shell diễn giải `$LoudPlugin`). Trên Windows CMD có thể dùng `java "ReflectionWalkthrough$LoudPlugin"`. Nếu chỉ dùng lệnh `java ReflectionWalkthrough`, chương trình tự chọn plugin mặc định.

## 3. Khám phá kiểu từ tên nhận lúc chạy

~~~java
String className = args.length == 0
        ? "ReflectionWalkthrough$HelloPlugin"
        : args[0];
Class<?> type = Class.forName(className);
~~~

- `className` là **chuỗi** cấu hình/đầu vào; host không gọi `new HelloPlugin(...)` và không tham chiếu `HelloPlugin.class` để quyết định plugin.
- `Class.forName(String)` tìm lớp bằng tên nhị phân (*binary name*) qua cơ chế nạp lớp tương ứng và, nếu cần, khởi tạo lớp. Không tìm thấy sẽ ném `ClassNotFoundException`; lỗi khởi tạo tĩnh có thể là `ExceptionInInitializerError`.
- `Class<?>` nghĩa là “một đối tượng `Class` với kiểu tham số chưa biết”. Dấu `?` không biến nó thành một instance; nó biểu diễn thông tin kiểu tại runtime.
- Tên nhị phân của lớp lồng là `ReflectionWalkthrough$HelloPlugin`. Với lớp độc lập có package, chẳng hạn `com.example.plugins.HelloPlugin`, dùng tên đầy đủ theo package thay vì tên đơn giản.
- `getName()` cho tên nhị phân; `getSimpleName()` cho tên đơn giản; `getSuperclass()` cho lớp cha trực tiếp (có thể trả `null` với `Object`, interface hoặc một số trường hợp khác). `isInterface()` phân biệt interface và class.

Điểm cốt lõi: **biết tên để tải lớp không đồng nghĩa với biết API của lớp tại thời điểm biên dịch**. Ví dụ vẫn yêu cầu quy ước constructor/phương thức; Reflection không tự đoán ý nghĩa của mọi phương thức.

## 4. Đọc metadata trước khi sử dụng

Dòng lặp trong chương trình:

~~~java
for (Field field : type.getDeclaredFields()) {
    System.out.println("Field: " + Modifier.toString(field.getModifiers())
            + " " + field.getType().getSimpleName() + " " + field.getName());
}
~~~

`getDeclaredFields()` trả các field **khai báo trực tiếp** trên lớp, kể cả `private`; nó không có nghĩa host được phép truy cập giá trị các field đó. Mảng trả về không có thứ tự được bảo đảm, vì vậy nếu cần danh sách ổn định cho nhiều field, phải tự sắp xếp theo tên. Ví dụ này mỗi plugin chỉ có một field: `private final String prefix`.

Một số phép khám phá thường gặp:

| Nhu cầu | API | Điều cần nhớ |
|---|---|---|
| Kiểm tra tên/loại lớp | `getName()`, `getSimpleName()`, `isInterface()` | Metadata về kiểu |
| Xem lớp cha và interface | `getSuperclass()`, `getInterfaces()` | Quan hệ kế thừa/triển khai trực tiếp |
| Xem field khai báo trên lớp | `getDeclaredFields()` | Có cả field không public; không bao gồm field kế thừa |
| Xem method public gồm kế thừa | `getMethods()` | Chỉ các method public có thể nhìn thấy theo API này |
| Xem method khai báo trực tiếp | `getDeclaredMethods()` | Có thể gồm private; không bao gồm method kế thừa |
| Tìm method public theo chữ ký | `getMethod("greet", String.class)` | Tên + **kiểu tham số**, không tìm theo kiểu trả về |
| Tìm constructor public | `getConstructor(String.class)` | Chữ ký phải khớp chính xác |
| Đọc annotation runtime | `getAnnotation(ComponentInfo.class)` | Annotation phải tồn tại ở runtime |

Tương ứng, `getDeclaredMethod(...)` và `getDeclaredConstructor(...)` có thể tìm thành viên khai báo trực tiếp dù không public; **tìm được không đảm bảo được phép gọi**. Quyền truy cập vẫn do Java và hệ thống module kiểm soát.

Trong ví dụ, annotation `@ComponentInfo(role = "...")` được khai báo với `@Retention(RetentionPolicy.RUNTIME)` nên có thể đọc khi chạy:

~~~java
ComponentInfo info = type.getAnnotation(ComponentInfo.class);
String role = info == null ? "(không có)" : info.role();
~~~

Không có annotation thì `getAnnotation` trả `null`. Nếu retention chỉ là `SOURCE` hoặc `CLASS`, không thể mong đọc annotation đó bằng lời gọi Reflection trên. Annotation cung cấp metadata; bản thân nó không tự tạo hoặc kích hoạt plugin.

## 5. Tạo instance bằng constructor được tìm ở runtime

~~~java
Constructor<?> constructor = type.getConstructor(String.class);
Object instance = constructor.newInstance("Xin chào");
~~~

`getConstructor(String.class)` tìm constructor **public nhận đúng một `String`**. Không có constructor như vậy sẽ ném `NoSuchMethodException`. Nếu tìm constructor nhận `int` thì phải dùng `int.class` (không tự thay bằng `Integer.class` ở bước tra cứu chữ ký). `newInstance(...)` thật sự gọi constructor, có thể chạy logic và phát sinh lỗi.

Vì biến `type` là `Class<?>`, kết quả lưu trong `Object`. Java không thể bảo đảm tại compile time rằng đối tượng ấy có `greet(String)`. Chương trình phải kiểm tra và gọi phương thức tiếp bằng Reflection. Đừng dùng cách cũ `Class.newInstance()`: API này đã deprecated, yêu cầu constructor không đối số và có cách xử lý ngoại lệ kém rõ ràng; `getDeclaredConstructor().newInstance()` là lối thay thế phù hợp khi cần constructor không đối số.

## 6. Tìm và gọi phương thức trên instance

~~~java
Method method = type.getMethod("greet", String.class);
Object result = method.invoke(instance, "Lan");
~~~

Ở đây `getMethod` chọn method public tên `greet` có một tham số `String`. Kiểu trả về có thể đọc bằng `method.getReturnType()`. `invoke` cần **đối tượng đích** đối với phương thức instance; tham số tiếp theo là các đối số gửi tới phương thức được gọi. Giá trị trả về được đóng hộp nếu là primitive, hoặc là `null` khi phương thức trả `void`. Trong ví dụ, `result` thực chất là `String`; in bằng `println` không cần ép kiểu.

Với phương thức `static`, đối tượng đích truyền cho `invoke` có thể là `null`. Nếu đích sai kiểu, đối số không tương thích hoặc sai số lượng, có thể gặp `IllegalArgumentException`; nếu method tự ném ngoại lệ, `invoke` bọc nó trong `InvocationTargetException`. Để hiểu nguyên nhân nghiệp vụ, đọc `getCause()` của wrapper này.

**Ranh giới quan trọng:** `getDeclaredMethod` hoặc `getDeclaredField` có thể trả về thành viên private, nhưng việc gọi/đọc không mặc nhiên hợp lệ. Đừng thêm `setAccessible(true)` như một “bước bắt buộc” của Reflection: trong ứng dụng dùng Java Platform Module System (JPMS), việc mở quyền truy cập sâu còn phụ thuộc package có được `opens` cho module gọi hay không; ngay cả trên classpath cũng cần cân nhắc giới hạn đóng gói và quyền truy cập.

## 7. Những lỗi thường gặp và cách xử lý

| Triệu chứng | Nguyên nhân thường gặp | Hướng xử lý |
|---|---|---|
| `ClassNotFoundException` | Sai tên nhị phân, thiếu JAR/module, class loader không nhìn thấy lớp | Kiểm tra tên, classpath/module path và loader |
| `NoSuchMethodException` | Constructor/method không tồn tại với đúng tên, visibility hoặc danh sách kiểu tham số | Kiểm tra API thực tế và chữ ký |
| `InstantiationException` | Không thể tạo instance, ví dụ cố khởi tạo abstract class | Chọn lớp concrete có constructor phù hợp |
| `IllegalAccessException` | Thành viên không thể được truy cập từ nơi gọi | Thiết kế API public/module exports; tránh phá đóng gói |
| `InvocationTargetException` | Code bên trong constructor hoặc method đã ném lỗi | Xem `getCause()` và xử lý nguyên nhân thực |
| `IllegalArgumentException` | Truyền đích/đối số không tương thích lúc `invoke`/`newInstance` | So đúng số lượng và kiểu tham số |
| `ExceptionInInitializerError` | Khởi tạo tĩnh của lớp thất bại | Kiểm tra static initializer và nguyên nhân lỗi |

Chương trình demo khai báo `throws ReflectiveOperationException` để giữ luồng học tập rõ ràng; chương trình sản xuất cần bắt lỗi theo ngữ cảnh, báo plugin nào hỏng và lý do, đồng thời xử lý riêng lỗi runtime như `ExceptionInInitializerError` khi thích hợp. Không nên nuốt ngoại lệ rồi tiếp tục như thể plugin đã tải thành công.

## 8. Từ demo sang thiết kế thực tế

**Khi dùng Reflection?** Framework DI, serializer, công cụ kiểm thử, plugin loader và công cụ phân tích cấu trúc mã thường cần khám phá type/annotation/member lúc chạy. Nhưng Reflection đánh đổi một số lợi ích: mất kiểm tra kiểu tại compile time, dễ lỗi do chuỗi tên/chữ ký, chịu giới hạn truy cập module và có chi phí tra cứu/gọi động. Đừng chọn Reflection chỉ để gọi một lớp đã biết rõ trong source.

Nếu có thể định nghĩa hợp đồng Java chung, hướng ổn định hơn cho plugin là interface:

~~~java
public interface GreetingService {
    String greet(String name);
}
~~~

Đoạn trên là **mảnh thiết kế minh họa**, không phải chương trình chạy độc lập. Khi plugin triển khai `GreetingService`, host có thể dùng `GreetingService.class.isAssignableFrom(type)` để kiểm tra tương thích trước khi tạo instance, rồi ép kiểu bằng `GreetingService.class.cast(instance)` và gọi `service.greet("Lan")` như Java thông thường. Không nên ép kiểu trực tiếp từ đối tượng vừa nạp nếu chưa xác minh interface; tên method giống nhau không khiến lớp tự động triển khai interface. Khi cần khám phá provider theo hợp đồng trên classpath/module path, `ServiceLoader` thường phù hợp hơn cơ chế tự lưu tên class trong cấu hình.

**Giới hạn an toàn:** Chỉ tải plugin từ nguồn được tin cậy; `Class.forName` có thể kích hoạt khởi tạo lớp và `newInstance`/`invoke` thực thi mã thật, không phải thao tác chỉ đọc metadata. Không lấy tên class tùy ý từ người dùng bên ngoài để rồi tùy tiện khởi chạy. Khi kiểm tra hiệu năng, đo trước khi tối ưu; có thể cache `Class`, `Constructor` hoặc `Method` đã xác minh thay vì tra cứu lặp lại, nhưng cache phải tuân theo vòng đời của class loader.

## 9. Tự kiểm tra trên chính ví dụ

1. Đổi tham số dòng lệnh giữa `ReflectionWalkthrough$HelloPlugin` và `ReflectionWalkthrough$LoudPlugin`. Xác định phần nào của host giữ nguyên, phần nào thay đổi theo metadata và hành vi plugin.
2. Đổi `getConstructor(String.class)` thành `getConstructor()`. Vì sao hiện tại ném `NoSuchMethodException`? Hãy bổ sung constructor không đối số vào một plugin rồi thử lại.
3. Đổi `getDeclaredFields()` thành `getFields()`. Vì sao trường `prefix` biến mất khỏi kết quả? Hãy giải thích sự khác biệt giữa “được khai báo” và “public”.
4. Bỏ `@Retention(RetentionPolicy.RUNTIME)` khỏi `ComponentInfo` rồi quan sát `getAnnotation(...)`. Tìm hiểu retention mặc định và lý do host không đọc được role.
5. Viết thêm một plugin có cùng constructor/method public, chạy bằng tên nhị phân mới; sau đó cố tình đổi tên `greet` để xác nhận lỗi và cách chẩn đoán.

**Điểm cần nắm:** Tên lớp ở runtime → `Class<?>` để khám phá → xác minh chữ ký/metadata → `Constructor.newInstance` tạo đối tượng → `Method.invoke` gọi hành vi. Reflection đưa quyết định về kiểu sang runtime, nên trách nhiệm xác minh hợp đồng, xử lý lỗi và kiểm soát nguồn mã được tải cũng chuyển sang runtime.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-overview-008">Kiến thức tiên quyết, phạm vi Java Core và trình tự học đề xuất</a>

<details>
<summary>Xem chi tiết</summary>

# Kiến thức tiên quyết, phạm vi Java Core và trình tự học Reflection

## 1. Mục đích của lộ trình

Reflection cho phép Java khám phá metadata của một kiểu khi chương trình đang chạy và, khi được phép, tìm constructor, method hoặc field để thao tác động. Chủ đề này nằm ở giao điểm giữa **kiểu dữ liệu**, **cơ chế gọi phương thức**, **quyền truy cập** và **metadata**. Học theo thứ tự hợp lý giúp bạn phân biệt được lúc nào chỉ quan sát cấu trúc, lúc nào chương trình thật sự thực thi hoặc thay đổi trạng thái.

Bài này đóng vai trò **bản đồ học tập**: xác định nền tảng cần có, ranh giới của Reflection trong Java Core, chuỗi bài thực hành và tiêu chí tự đánh giá. Các bài chuyên đề trong chương sẽ giải thích chi tiết từng API, cơ chế generic, annotation và những lựa chọn thiết kế liên quan.

Sau bài này, bạn nên tự trả lời được: “Tôi đã đủ kiến thức bắt đầu chưa?”, “Tôi cần học gì trước khi thử gọi method bằng Reflection?” và “Một bài thực hành thế nào mới chứng minh tôi đã hiểu thay vì chỉ chép mã chạy được?”.

## 2. Kiến thức nên có trước khi bắt đầu

| Nền tảng | Bạn cần làm được | Liên hệ với Reflection |
| --- | --- | --- |
| Class và instance | Tạo object, phân biệt biến tham chiếu với đối tượng thật | `Class<?>` đại diện kiểu; object chứa trạng thái |
| Constructor, field, method | Viết constructor có tham số và method có giá trị trả về | Có các đối tượng metadata `Constructor`, `Field`, `Method` tương ứng |
| Kế thừa và interface | Phân biệt override với overload, hiểu phương thức kế thừa | Kết quả tìm member phụ thuộc kiểu, chữ ký và quy tắc kế thừa |
| Phạm vi truy cập | Hiểu `public`, `protected`, package-private, `private`, `static` | Tìm được metadata không đồng nghĩa được phép truy cập |
| Package, JDK và classpath | Biên dịch/chạy file Java, biết tên lớp đầy đủ | `Class.forName` cần tên nhị phân và class loader phù hợp |
| Primitive, wrapper, array | Phân biệt `int` với `Integer` và kiểu mảng | Tra cứu constructor/method dùng kiểu tham số chính xác |
| Exception | Đọc stack trace, nhận biết checked và unchecked exception | Lỗi tra cứu, khởi tạo và gọi động xuất hiện ở các bước khác nhau |
| Generic và annotation | Biết `List<String>`, wildcard, cú pháp `@Annotation` | Chuẩn bị cho type erasure, `Type` và runtime retention |

**Mức tối thiểu:** Bạn có thể bắt đầu phần quan sát metadata nếu biết viết một class, tạo instance và gọi method thông thường. Trước khi đi sâu vào generic/annotation, hãy ôn thêm các khái niệm tương ứng; không nhất thiết thành thạo hai chủ đề này ngay từ bài đầu.

### Bài tự kiểm tra trong 10 phút

Tự viết một lớp `Student` có field `private String name`, constructor `public Student(String name)` và method `public String greet()`. Sau đó, không dùng Reflection, hãy:

1. Tạo hai đối tượng có tên khác nhau và giải thích vì sao chúng cùng kiểu nhưng có trạng thái riêng.
2. Thêm subclass `GraduateStudent` ghi đè `greet()`. Dự đoán kết quả khi gọi qua biến kiểu `Student`.
3. Thêm overload `greet(String prefix)` và giải thích cách compiler phân biệt hai chữ ký.
4. Thử đổi method thành `private` rồi quan sát lỗi khi gọi từ bên ngoài lớp.

Nếu các bước này còn khó, hãy củng cố class/object, đa hình, phạm vi truy cập và exception trước. Đây là nền tảng để hiểu vì sao một `Method` có thể được tìm thấy nhưng lời gọi `invoke` vẫn thất bại.

## 3. Phạm vi Reflection trong Java Core

Chương này tập trung vào các API có sẵn trong JDK:

- **`java.lang.Class`:** nhận dạng kiểu lúc chạy, tên kiểu, superclass, interface và cách khám phá các member.
- **`java.lang.reflect`:** `Constructor`, `Method`, `Field`, `Modifier` và `Type` để đọc metadata; chỉ tạo đối tượng, gọi method, đọc/ghi field khi có mục đích và quyền hợp lệ.
- **Annotation runtime:** đọc annotation theo retention và vị trí khai báo, phân biệt metadata với logic xử lý của framework.
- **Ranh giới của Reflection:** declared/inherited, access control, Java Platform Module System (JPMS), type erasure, ngoại lệ, hiệu năng và khả năng bảo trì.

**Không thuộc mục tiêu chính:** tự xây dựng một DI container/ORM hoàn chỉnh, thay đổi bytecode bằng ASM/Byte Buddy, instrumentation agent, JNI, cơ chế ClassLoader phức tạp, hoặc tối ưu nâng cao với `MethodHandle`/`VarHandle`. Những chủ đề này có thể sử dụng hoặc bổ sung Reflection, nhưng không phải điều kiện để làm chủ API cơ bản.

Reflection cũng không thay thế interface, đa hình hay lời gọi trực tiếp. Nếu chương trình đã biết kiểu và hành vi lúc biên dịch, hãy ưu tiên API Java thông thường. Khi một thư viện phải quan sát kiểu do ứng dụng khác cung cấp ở runtime, Reflection mới thể hiện rõ giá trị.

## 4. Một điểm xuất phát nhỏ: chỉ đọc metadata

Thực hành đầu tiên nên **quan sát** trước khi khởi tạo hay gọi động. Tạo file `FirstMetadataCheck.java`:

~~~java
import java.lang.reflect.Method;

public class FirstMetadataCheck {
    public static void main(String[] args) throws NoSuchMethodException {
        Class<?> type = String.class;
        Method method = type.getMethod("substring", int.class);

        System.out.println(type.getName());
        System.out.println(method.getName());
        System.out.println(method.getReturnType().getSimpleName());
    }
}
~~~

Chạy với JDK 17 trở lên:

~~~bash
javac FirstMetadataCheck.java
java FirstMetadataCheck
~~~

Kết quả:

~~~text
java.lang.String
substring
String
~~~

Bạn vừa lấy một `Class<?>`, tìm metadata của phương thức public có tham số `int` và đọc kiểu trả về của nó. Chưa có đối tượng `String` nào bị sửa, cũng chưa gọi `substring`. Thử thay `int.class` bằng `Integer.class` để quan sát `NoSuchMethodException`: tra cứu chữ ký bằng Reflection không tự coi primitive và wrapper là một kiểu.

Ví dụ này chỉ giới thiệu kỹ năng đầu tiên. Khi chuyển sang gọi phương thức hoặc tạo instance, bạn sẽ dùng `Method.invoke` hoặc `Constructor.newInstance` và cần thêm kiểm tra receiver, quyền truy cập và cách xử lý lỗi.

## 5. Trình tự học: từ hiểu kiểu đến dùng API có kiểm soát

| Chặng | Trọng tâm | Bằng chứng bạn đã hiểu |
| --- | --- | --- |
| **0. Ôn Java Core** | Class/object, overload, override, constructor, access modifier và exception | Hoàn thành bài `Student` mà không dùng Reflection |
| **1. Kiểu tĩnh và kiểu runtime** | `SomeType.class`, `obj.getClass()`, `Class.forName`; phân biệt kiểu biến với lớp thực của object | Giải thích vì sao `Animal a = new Dog()` có hai góc nhìn về kiểu |
| **2. Khám phá cấu trúc** | `Class` và họ `get...` / `getDeclared...`; tên, modifier, constructor, method, field | Liệt kê được member, ghi rõ declared/public/inherited, không dựa vào thứ tự trả về |
| **3. Ranh giới quan sát và thực thi** | Metadata so với `Field.get`, `Field.set`, `Method.invoke`, `Constructor.newInstance` | Chỉ ra dòng nào đọc metadata và dòng nào có thể thay đổi trạng thái |
| **4. Metadata trong quan hệ kiểu** | Kế thừa, generic `Type`, type erasure, annotation runtime và `@Inherited` | Giải thích được metadata khai báo có thể tồn tại dù type argument của instance đã bị erasure |
| **5. Giới hạn và lỗi** | Sai tên/chữ ký, quyền truy cập, JPMS, exception do method đích ném, input không tin cậy | Chẩn đoán đúng bước hỏng thay vì thêm `setAccessible(true)` bừa bãi |
| **6. Tích hợp và lựa chọn thiết kế** | Ví dụ plugin, inspector, interface/`ServiceLoader`, đánh đổi về hiệu năng và bảo trì | Biết khi nào Reflection cần thiết và khi nào API trực tiếp đủ dùng |

Lộ trình không bắt buộc người học thuộc hàng chục tên method. Ở mỗi chặng, hãy ghi lại ba điều: **dữ liệu đầu vào là gì**, **đối tượng/API trả về diễn tả điều gì**, và **khi nào một lời gọi có thể thực thi mã hoặc ném lỗi**.

### Bốn mốc kiểm tra cần vượt qua

**Mốc A — Biết đang xét kiểu nào.** Tạo một biến kiểu cha giữ instance lớp con. So sánh kết quả của `Parent.class` và `instance.getClass()`; giải thích bằng compiler và runtime. Nếu không hiểu khác biệt này, đừng dùng `getDeclaredMethods()` để suy ra “mọi method được phép gọi”.

**Mốc B — Tách tra cứu khỏi hành động.** Từ một `Class<?>`, chỉ đọc tên, modifier và chữ ký của member; xác nhận chưa gọi method đích. Sau đó thử một lời gọi `invoke` có chủ đích, quan sát thay đổi trạng thái để thấy điểm chuyển pha.

**Mốc C — Hiểu metadata có giới hạn.** Đọc generic signature của một field `List<String>` và so sánh với `getClass()` của đối tượng `ArrayList`; thử annotation có `RetentionPolicy.RUNTIME`. Giải thích vì sao metadata khai báo không bảo đảm giá trị runtime thỏa mọi ràng buộc generic.

**Mốc D — Thiết kế có hợp đồng.** Nạp một implementation theo cấu hình, kiểm tra nó thực sự triển khai interface đã công bố, rồi gọi nghiệp vụ qua interface. Đánh giá liệu factory/registry hoặc `ServiceLoader` có giải quyết yêu cầu đơn giản hơn không.

## 6. Bài thực hành tổng hợp: xây ClassInspector

Sau khi đã học các chặng, hãy viết `ClassInspector` nhận một `Class<?>` và tạo báo cáo về kiểu. Mục tiêu đầu tiên là một **công cụ chỉ đọc**, vì nhiệm vụ khám phá không cần tự động thực thi code nghiệp vụ.

Phiên bản đầu cần in tên nhị phân và tên đơn giản, superclass, interface trực tiếp, constructor, method và field cùng modifier. Hãy thử trên ba kiểu: lớp đơn giản, lớp con có method override, và lớp có annotation runtime.

Nâng cấp theo từng vòng:

1. Cho phép người dùng chọn **chỉ thành viên khai báo** hoặc **thành viên public có thể kế thừa**; ghi rõ lựa chọn cạnh kết quả.
2. Sắp xếp tên member trước khi hiển thị, vì Reflection không bảo đảm thứ tự mảng mà API trả về.
3. Đánh dấu method bridge/synthetic khi gặp generic override; không coi mọi phần tử `getDeclaredMethods()` đều là method được gõ trong source.
4. Chỉ đọc annotation còn hiệu lực ở runtime, phân biệt annotation của class với annotation nằm trên type argument hoặc method.
5. Nếu cần thêm chế độ thực thi, chỉ cho phép method public đã được duyệt; tách luồng xác thực, tạo đối tượng và gọi method ra khỏi luồng inspector chỉ đọc.

Khi kiểm thử, cố tình tạo ba lỗi: tên method sai, chữ ký tham số sai và method đích tự ném exception. Trình bày **lỗi ở bước tra cứu** khác **lỗi được bọc khi method chạy**. Đối với member private thuộc module khác, ưu tiên thiết kế quyền truy cập rõ ràng thay vì mặc định có thể mở bằng Reflection.

### Tiêu chí hoàn thành bài tổng hợp

- Báo cáo không thay đổi trạng thái object khi chỉ ở chế độ khám phá.
- Có thể dự đoán đúng member nào xuất hiện trong chế độ declared và public/inherited.
- Kết quả ổn định sau khi sắp xếp, kể cả khi thứ tự Reflection trả về thay đổi.
- Nhận diện được giới hạn của annotation và generic metadata.
- Lỗi được mô tả bằng **class + member + chữ ký + bước thất bại**, có nguyên nhân gốc khi thực thi.
- Bạn có thể giải thích quyết định giữ Reflection ở ranh giới công cụ và dùng lời gọi thông thường ở phần nghiệp vụ.

## 7. Cách tự đánh giá và học tiếp

Không nên chỉ kiểm tra “chạy ra đúng vài dòng console”. Bạn đã nắm một chặng khi có thể **dự đoán kết quả trước khi chạy**, giải thích kết quả sau khi chạy, và sửa một biến thể cố tình sai mà vẫn giữ được ranh giới quyền truy cập.

Các dấu hiệu nên ôn lại:

- Bạn cho rằng `Class<?>` là instance dữ liệu của lớp được mô tả.
- Bạn nghĩ `getDeclaredMethod` vừa tìm vừa gọi method hoặc luôn bỏ qua kiểm tra truy cập.
- Bạn mong `getClass()` khôi phục mọi tham số generic từng có trong biến nguồn.
- Bạn không phân biệt lỗi `NoSuchMethodException` với nguyên nhân nằm trong `InvocationTargetException.getCause()`.
- Bạn dùng Reflection cho mọi lời gọi chỉ vì muốn chọn một implementation khác khi chương trình đã có interface chung.

Trình tự đọc phù hợp với chương này là: **Reflection là gì và vấn đề cần giải quyết → thông tin kiểu biên dịch/runtime → metadata khác thực thi/trạng thái → vai trò của Class và java.lang.reflect → kế thừa/generic/annotation → Reflection hay gọi trực tiếp → ví dụ xuyên suốt**. Có thể quay lại bài này để tự đánh dấu mốc học tập.

Nếu hoàn thành `ClassInspector` và giải thích được các giới hạn trên, bạn đã có nền tảng để tìm hiểu ứng dụng Reflection trong test runner, serializer, dependency injection hoặc plugin loader thực tế. Các framework có thể dùng thêm kỹ thuật khác; hãy xác minh cách chúng xử lý metadata và quyền truy cập thay vì mặc định mọi framework đều hoạt động theo đúng ví dụ nhập môn.

</details>

- [Quay lại đầu trang](#back-to-top)
