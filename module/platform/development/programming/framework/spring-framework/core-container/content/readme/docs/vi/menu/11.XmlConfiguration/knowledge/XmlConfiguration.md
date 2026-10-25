<a id="back-to-top"></a>

# Cấu hình XML

## Menu
- [Cấu trúc beans, namespace và kiểm tra schema](#xml-configuration-001)
- [Khai báo bean bằng id, class và alias](#xml-configuration-002)
- [Cấp tham số qua constructor-arg](#xml-configuration-003)
- [Cấp thuộc tính qua property, value và ref](#xml-configuration-004)
- [Giá trị null, tập hợp lồng nhau và bean nội bộ](#xml-configuration-005)
- [Nhập tài nguyên cấu hình bằng import](#xml-configuration-006)
- [Autowiring XML theo tên, theo kiểu và giới hạn của cơ chế](#xml-configuration-007)
- [Bật annotation-config và component-scan](#xml-configuration-008)
- [Chuyển đổi cấu hình XML sang Java mà giữ nguyên hành vi](#xml-configuration-009)

## <a id="xml-configuration-001">Cấu trúc beans, namespace và kiểm tra schema</a>

<details>
<summary>Xem chi tiết</summary>

# Cấu trúc `<beans>`, XML namespace và kiểm tra XSD schema trong Spring Core Container

## 1. Vì sao vẫn cần hiểu Spring XML?

Spring Framework hỗ trợ mô tả bean definitions bằng Java configuration, annotation và XML. Trong dự án hiện đại, Java configuration thường nổi bật; nhưng các hệ thống lâu năm, thư viện tích hợp, ứng dụng lai Java/XML hoặc file cấu hình hạ tầng vẫn có thể sử dụng XML.

Một file Spring XML không chỉ là “danh sách class”. Nó là tài liệu XML có **phần tử gốc**, **XML namespaces**, **khai báo schema**, **bean definitions**, **dependency references** và những phần mở rộng namespace do Spring hoặc thư viện cung cấp.

Ví dụ cấu trúc tối thiểu đúng chuẩn:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="messageRepository"
          class="demo.MessageRepository"/>

    <bean id="messageService"
          class="demo.MessageService">
        <constructor-arg ref="messageRepository"/>
    </bean>
</beans>
```

Đoạn trên là **XML minh họa**: để chạy, các class `demo.MessageRepository` và `demo.MessageService` phải tồn tại và constructor của service phải nhận repository phù hợp. `<beans>` là root, hai `<bean>` là bean definitions, `<constructor-arg>` khai báo dependency.

Bài này trả lời những câu hỏi cốt lõi:

1. Vì sao `<beans>` phải nằm trong đúng namespace?
2. `xmlns`, `xmlns:xsi` và `xsi:schemaLocation` khác nhau thế nào?
3. Vì sao một file có nhiều namespaces nhưng vẫn chỉ có một root?
4. Thế nào là XSD validation, và nó **kiểm tra được/không kiểm tra được** những điều gì?
5. Spring tìm các XSD ở đâu, tại sao URI `www.springframework.org` không nhất thiết có nghĩa phải tải mạng mỗi lần?
6. `beans`, `p`, `c`, `util`, `context`, `tx`, `aop` khác nhau thế nào?
7. Làm sao viết, chạy và xác minh một dự án Java 17+ với XML và schema đúng?
8. Chẩn đoán lỗi XML parse, schema, namespace handler và bean creation ra sao?

## 2. Ba tầng xử lý khác nhau: XML, XSD và Spring beans

Khi Spring đọc một XML resource, có ít nhất ba mức kiểm tra cần phân biệt:

**Tầng 1 — XML well-formedness:** XML parser kiểm tra cấu trúc cơ bản: tag đóng mở cân bằng, attribute có dấu nháy, ký tự được escape, chỉ một document element. Lỗi như thiếu `</bean>`, dấu `&` chưa escape hoặc có hai phần tử root khiến XML không well-formed.

**Tầng 2 — XML Schema/XSD validation:** Nếu validation được bật và schema phù hợp được nạp, parser kiểm tra những phần tử/thuộc tính/kiểu/quan hệ mà XSD mô tả. Ví dụ, đặt một thẻ không thuộc vocabulary hoặc một attribute không được định nghĩa có thể gây lỗi validation.

**Tầng 3 — Spring bean-definition processing và runtime:** Spring đọc metadata XML, áp dụng namespace handlers, đăng ký bean definitions, rồi BeanFactory tạo/ghép instances. Lỗi class không tồn tại, thiếu constructor, tham chiếu bean sai, circular dependency hoặc exception trong constructor thuộc tầng này; XML hoàn toàn hợp lệ **không bảo đảm ứng dụng chạy được**.

```text
application-context.xml
        |
        v
  XML parser: well-formed?
        | no -> XML parse error
        v
  XML Schema validation?
        | no -> SAXParseException / schema error
        v
  Spring namespace parsers / bean definition reader
        | unknown namespace / invalid bean metadata
        v
  BeanDefinitionRegistry
        |
        v
  BeanFactory + ApplicationContext refresh
        | missing class/ref / DI / lifecycle exception
        v
  Managed beans sẵn sàng
```

Một thông báo “XML schema validation succeeded” chỉ khẳng định file đáp ứng schema trong phạm vi được kiểm tra. Nó **không** kiểm chứng rằng `class="demo.X"` trỏ đến class tồn tại, hoặc `ref="y"` thực sự có bean tên `y`.

## 3. Phần tử gốc `<beans>`

Định dạng XML Spring thông thường có một root `<beans>`. Phần tử này chứa các child elements như `<bean>`, `<alias>`, `<import>`, nested `<beans>` trong trường hợp hỗ trợ profile, hoặc các phần tử mở rộng namespace.

```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="clock" class="java.time.Clock"
          factory-method="systemUTC"/>

    <alias name="clock" alias="applicationClock"/>
</beans>
```

Đây là **đoạn minh họa**. Bean `clock` sử dụng static factory method `Clock.systemUTC()`; alias `applicationClock` tham chiếu cùng bean name. Để kiểm tra application startup trong thực tế cần khởi tạo một context có file này.

### 3.1. `id`, `name`, `class`, `ref`

| Cấu trúc | Ý nghĩa | Ghi nhớ |
| --- | --- | --- |
| `<bean id="service" .../>` | Bean name/identifier chính của definition | Phải giải quyết duy nhất trong registry phù hợp |
| `<bean name="one,two" .../>` | Cho phép dùng tên và alias theo cú pháp được Spring hỗ trợ | `name` không đồng nghĩa Java class name |
| `class="com.example.Service"` | Tên class đầy đủ, thường là implementation | XSD nhìn như chuỗi, Spring/JVM mới kiểm tra class |
| `<constructor-arg ref="repo"/>` | Tham chiếu một bean khác | `ref` là bean name, không phải URL hoặc Java package |
| `<property name="title" value="Demo"/>` | Set JavaBean property/giá trị cấu hình | Cần setter/cơ chế gán tương thích |
| `<property name="repo" ref="repo"/>` | Gán bean reference cho property | Spring resolve bean theo tên trong context |
| `<alias name="service" alias="gateway"/>` | Thêm một tên phụ cho cùng bean | Không sinh instance thứ hai |
| `<import resource="other.xml"/>` | Nhập thêm file Spring XML | Khác `@Import(Config.class)` trong JavaConfig |

Các attributes khác như `scope`, `lazy-init`, `init-method` và `destroy-method` được xử lý theo Spring bean semantics. `<beans>` cũng hỗ trợ một số mặc định như `default-lazy-init`, `default-init-method` hoặc `default-destroy-method` trong các trường hợp phù hợp.

### 3.2. Cấu trúc lồng nhau không được phép tùy tiện

Không phải phần tử XML bất kỳ cũng có thể đặt ở mọi vị trí:

```xml
<!-- SAI về ý tưởng: <property> không phải child trực tiếp
     thông thường của <beans>. -->
<beans xmlns="http://www.springframework.org/schema/beans">
    <property name="title" value="X"/>
</beans>
```

Đây là **ví dụ sai có chủ đích**, không phải file để chạy. `<property>` là child của một `<bean>`, không phải top-level definition độc lập. XSD giúp phát hiện lỗi đặt phần tử không phù hợp vị trí (khi schema validation hoạt động).

### 3.3. Nested `<beans>` và profile

Spring hỗ trợ nested `<beans profile="...">` để biểu diễn bean definitions theo môi trường:

```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">
    <beans profile="dev">
        <bean id="storage" class="demo.InMemoryStorage"/>
    </beans>
    <beans profile="prod">
        <bean id="storage" class="demo.JdbcStorage"/>
    </beans>
</beans>
```

Đoạn này minh họa cần class `demo.InMemoryStorage`/`demo.JdbcStorage`. Các nested `<beans>` có quy tắc thứ tự/placement do XSD quy định; không rải nested `<beans>` tùy ý xen giữa những định nghĩa khác. Các profile active ảnh hưởng nhóm beans được đăng ký. **XML profile selection khác XSD validation:** một tài liệu có thể hợp lệ về schema nhưng chứa bean definitions chỉ hoạt động khi profile tương ứng active.

## 4. XML namespace là URI, không phải tên prefix

XML namespace phân biệt các phần tử/thuộc tính theo URI định danh.

```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xmlns:util="http://www.springframework.org/schema/util"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/context
           https://www.springframework.org/schema/context/spring-context.xsd
           http://www.springframework.org/schema/util
           https://www.springframework.org/schema/util/spring-util.xsd">

    <bean id="name" class="java.lang.String">
        <constructor-arg value="Spring"/>
    </bean>
    <util:list id="regions">
        <value>north</value>
        <value>south</value>
    </util:list>
    <context:annotation-config/>
</beans>
```

Ở ví dụ:

- `xmlns="http://www.springframework.org/schema/beans"` chọn **default namespace** cho các phần tử không có prefix như `<beans>`, `<bean>`, `<constructor-arg>` và `<value>`.
- `xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"` cho phép sử dụng attribute `xsi:schemaLocation` của XML Schema instance vocabulary.
- `xmlns:context="http://www.springframework.org/schema/context"` ánh xạ prefix `context` sang Spring context namespace, cho phép dùng `<context:annotation-config/>`.
- `xmlns:util="http://www.springframework.org/schema/util"` ánh xạ prefix `util`, cho phép dùng `<util:list/>`.

Đây là **mã XML minh họa riêng**, không phải phần chương trình hoàn chỉnh ở mục 11. Nó đòi các dependency Spring tương ứng để namespace handlers có sẵn.

### 4.1. Prefix là tên cục bộ, URI quyết định nghĩa

Về XML, có thể viết:

```xml
xmlns:ctx="http://www.springframework.org/schema/context"
```

và dùng `<ctx:annotation-config/>` thay cho `<context:annotation-config/>`. Bản chất namespace vẫn như nhau vì URI không đổi. Convention `context` dễ đọc hơn và được tài liệu Spring sử dụng phổ biến, nhưng parser không dựa vào chữ “context” của prefix để nhận diện handler.

Ngược lại:

```xml
xmlns:context="http://example.invalid/not-spring-context"
```

dù prefix tên `context` nhưng URI đã khác Spring context namespace; handler/schema tương ứng không được Spring cung cấp chỉ vì prefix giống nhau.

### 4.2. Namespace URI không nhất thiết là file để tải

`http://www.springframework.org/schema/beans` là namespace name/identifier, còn `https://www.springframework.org/schema/beans/spring-beans.xsd` là địa điểm được đề xuất để tham chiếu schema. Trong `xsi:schemaLocation`, hai giá trị này tạo thành một **cặp**.

Bản thân default namespace URI không khiến XML parser yêu cầu tải nội dung của nó như một HTML page. Trình xử lý XML phân biệt namespace URI, schema location hint và các entity/schema resolution mechanisms.

### 4.3. Có thể đặt prefix cho beans namespace không?

Được. Ví dụ:

```xml
<beans:beans
    xmlns:beans="http://www.springframework.org/schema/beans"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="
        http://www.springframework.org/schema/beans
        https://www.springframework.org/schema/beans/spring-beans.xsd">
    <beans:bean id="clock" class="java.time.Clock"
                factory-method="systemUTC"/>
</beans:beans>
```

Nhưng cấu hình Spring tiêu chuẩn thường chọn beans namespace làm **default namespace** để tránh phải viết `beans:` trước từng thẻ. Tên namespace URI chứ không phải cách viết prefix quyết định nghĩa.

## 5. `xsi:schemaLocation`: đọc đúng từng cặp

Hình thức:

```xml
xsi:schemaLocation="
    NAMESPACE_URI_1   LOCATION_OF_XSD_1
    NAMESPACE_URI_2   LOCATION_OF_XSD_2"
```

Đây là **khung cú pháp**, không phải XML chạy độc lập; mỗi namespace được khai báo có thể có mapping tới XSD liên quan để editor/parser thực hiện validation.

Ví dụ thực tế:

```xml
xsi:schemaLocation="
    http://www.springframework.org/schema/beans
    https://www.springframework.org/schema/beans/spring-beans.xsd
    http://www.springframework.org/schema/util
    https://www.springframework.org/schema/util/spring-util.xsd"
```

Suy ra:

| Namespace URI | XSD location |
| --- | --- |
| `http://www.springframework.org/schema/beans` | `https://www.springframework.org/schema/beans/spring-beans.xsd` |
| `http://www.springframework.org/schema/util` | `https://www.springframework.org/schema/util/spring-util.xsd` |

**Không được** trộn URI beans với XSD util hoặc đảo từng phần tử vì parser/schema resolver cần biết XSD ứng với namespace nào.

### 5.1. Tại sao URI namespace thường dùng `http` nhưng XSD có thể dùng `https`?

Đây là quy ước được tài liệu Spring sử dụng. **Namespace URI là định danh cố định**, không phải cam kết về giao thức tải mạng; XSD location là schema hint/resource location. Đừng tự đổi `xmlns` thành `https://.../schema/beans` chỉ vì muốn “an toàn hơn”: đó là **namespace URI khác**, Spring handler có thể không nhận ra.

Cấu hình đúng:

```xml
xmlns="http://www.springframework.org/schema/beans"
```

và mapping theo tài liệu:

```xml
http://www.springframework.org/schema/beans
https://www.springframework.org/schema/beans/spring-beans.xsd
```

Hai đoạn này là các **fragment minh họa**, không phải XML document hoàn chỉnh.

### 5.2. Versionless XSD và versioned XSD

Spring thường khuyên dùng `spring-beans.xsd` không gắn số phiên bản trong URL để schema được ánh xạ tương thích với Spring artifacts của project. Một số hệ thống legacy có thể ghi `spring-beans-3.0.xsd` hoặc dạng versioned khác.

Khi bảo trì:

1. Kiểm tra Spring Framework version thực tế trong dependency graph.
2. Ưu tiên schema URL chính thức tương ứng với phiên bản Spring đang chạy; với cấu hình mới, dùng versionless URL theo reference hiện tại.
3. Không copy nguyên schemaLocation cũ từ một bài hướng dẫn rất lâu trước khi đối chiếu thư viện.
4. Kiểm tra sự hiện diện của namespace handler cho phần tử sử dụng, không chỉ sự tồn tại URL schema.
5. Nếu framework phụ thuộc một custom schema, phải có provider JAR/resource phù hợp.

Đừng nhầm **XSD version** với XML declaration `<?xml version="1.0"?>`: version của ngôn ngữ XML khác hoàn toàn phiên bản schema của Spring.

### 5.3. Schema location là hint, không phải toàn bộ hợp đồng runtime

Theo XML Schema, `xsi:schemaLocation` là gợi ý ánh xạ namespace → schema document, không biến XML metadata thành Java bean definition một cách tự động. Spring XML reader còn có namespace parser/handler riêng cho những elements tùy biến.

Một file có cặp schemaLocation đúng nhưng thiếu Spring module cung cấp namespace handler (ví dụ một namespace của Spring Integration hoặc thư viện bên thứ ba) vẫn có thể không được Spring xử lý như mong đợi.

## 6. Spring giải quyết schema trên classpath ra sao?

Spring có cơ chế ánh xạ schema URLs tới resources bên trong thư viện để tránh phụ thuộc bắt buộc vào network mỗi lần khởi động. Nhiều Spring modules đi kèm cấu hình `META-INF/spring.schemas` và các XSD resources của chính chúng. `PluggableSchemaResolver` có thể đọc mapping này từ classpath.

Cơ chế khái niệm:

```text
XML khai báo:
https://www.springframework.org/schema/beans/spring-beans.xsd
                        |
                        v
             Spring EntityResolver
                        |
                        v
         Tìm mapping META-INF/spring.schemas
                        |
          +-------------+-------------+
          |                           |
          v                           v
   Có mapping classpath         Không có mapping
          |                           |
          v                           v
   Nạp XSD local resource      Resolver/parser có thể
                               cần xử lý location gốc
```

Sơ đồ không bảo đảm mọi XML parser/custom resolver đều offline được. Đối với schema bên thứ ba, nếu thiếu mapping/resource hoặc ứng dụng tự thay thế resolver, việc tìm schema có thể thất bại hoặc cần network. Hãy kiểm tra dependency và resource thay vì mặc định suy ra “Spring luôn tải XSD qua Internet”.

### 6.1. `META-INF/spring.handlers` khác `spring.schemas`

Hai file mapping phục vụ mục đích khác nhau:

| Resource | Vai trò |
| --- | --- |
| `META-INF/spring.schemas` | Ánh xạ schema URL sang XSD resource có thể nạp từ classpath |
| `META-INF/spring.handlers` | Ánh xạ XML namespace URI sang Spring `NamespaceHandler` implementation |
| XSD file | Định nghĩa các phần tử/thuộc tính/vocabulary hợp lệ |
| Namespace handler/parser | Chuyển custom XML elements thành bean definitions hoặc cấu hình hạ tầng |

Ví dụ có XSD cho một custom namespace giúp editor biết thẻ nào hợp lệ; nhưng Spring vẫn cần namespace handler tương ứng để hiểu semantics và đăng ký beans. Có handler nhưng thiếu XSD mapping cũng gây vấn đề validation/editor/resolution tùy cấu hình.

### 6.2. Vì sao IDE báo đỏ nhưng application chạy?

Một XML editor có thể tự tải XSD từ URL trong `schemaLocation`, và mạng/proxy/certificate/cached schema ảnh hưởng kết quả. Spring runtime có thể resolve XSD từ classpath mapping, dẫn đến tình huống IDE báo “cannot resolve schema” nhưng application vẫn load thành công.

Ngược lại, IDE cache một XSD cũ có thể không phản ánh đúng Spring libraries hiện tại; application có thể lỗi do namespace handler không có trên classpath. Hãy kiểm tra **cả IDE schema configuration lẫn runtime dependencies**, đừng chỉ nhìn một dấu gạch đỏ.

### 6.3. Những vấn đề an toàn với XML parser

XML có khả năng khai báo external entities/DOCTYPE và kéo tài nguyên bên ngoài. Khi xử lý XML **không tin cậy**, phải chủ động hạn chế XXE, external entity resolution và quyền truy cập filesystem/network theo API parser đang dùng. Spring XML config thường là tài nguyên do developer kiểm soát; không nên cho user upload XML tùy ý rồi load trực tiếp thành bean definitions.

Đừng tắt validation hoặc mở network/entity expansion vô hạn để “chữa” một lỗi schema. Trước tiên tìm đúng namespace, XSD, module và mapping.

## 7. Một số namespace thường gặp trong Spring XML

| Namespace | URI theo quy ước | Công dụng phổ biến |
| --- | --- | --- |
| `beans` (thường default) | `http://www.springframework.org/schema/beans` | `<bean>`, `<alias>`, `<import>`, dependency metadata |
| `context` | `http://www.springframework.org/schema/context` | `<context:component-scan>`, `<context:annotation-config>`, cấu hình hạ tầng context |
| `util` | `http://www.springframework.org/schema/util` | `<util:list>`, `<util:map>`, `<util:properties>`, constants |
| `tx` | `http://www.springframework.org/schema/tx` | Khai báo transaction infrastructure như `<tx:annotation-driven/>` |
| `aop` | `http://www.springframework.org/schema/aop` | Khai báo các cấu hình AOP liên quan |
| `p` | `http://www.springframework.org/schema/p` | Attributes rút gọn để đặt JavaBean property |
| `c` | `http://www.springframework.org/schema/c` | Attributes rút gọn để truyền constructor arguments |

Những namespaces như `mvc`, `security`, `jms`, `batch` hay integration namespaces còn phụ thuộc vào module/framework tương ứng; không thể mặc định chúng có sẵn chỉ vì application dùng `spring-context`.

### 7.1. `p` namespace: property injection bằng attribute

Dạng đầy đủ:

```xml
<bean id="worker" class="demo.Worker">
    <property name="label" value="primary"/>
</bean>
```

Dạng `p` namespace tương đương về mục đích:

```xml
<bean id="worker"
      class="demo.Worker"
      p:label="primary"/>
```

Đây là hai fragments minh họa; nếu dùng fragment thứ hai, root `<beans>` phải khai báo:

```xml
xmlns:p="http://www.springframework.org/schema/p"
```

`p` namespace **không có XSD riêng**: Spring xử lý cú pháp attribute này ở core. Đừng thêm cặp `.../schema/p .../spring-p.xsd` vào `schemaLocation` rồi kỳ vọng file XSD có tồn tại theo cùng quy ước như beans/util/context.

Chú ý sự khác biệt giữa `p:label="primary"` (literal string/property) và `p:repository-ref="repository"` (tham chiếu bean `repository`). Hậu tố `-ref` phân biệt value với bean reference theo quy tắc của p-namespace.

### 7.2. `c` namespace: constructor argument bằng attribute

Dạng cơ bản:

```xml
<bean id="service" class="demo.MessageService">
    <constructor-arg ref="repository"/>
</bean>
```

Dạng rút gọn dùng chỉ số:

```xml
<bean id="service"
      class="demo.MessageService"
      c:_0-ref="repository"/>
```

`c` namespace cũng **không có XSD riêng**. Root khai báo:

```xml
xmlns:c="http://www.springframework.org/schema/c"
```

`c:_0-ref` trỏ tới tham số constructor index 0, còn `c:repository-ref` dùng tên tham số (phụ thuộc metadata parameter names khả dụng và constructor matching thích hợp). Với Java 17+ và Gradle, cần chú ý compiler option `-parameters` nếu dựa vào tên tham số Java. **Dùng index hoặc `<constructor-arg>` có `index` rõ ràng** giúp tránh phụ thuộc vào parameter-name metadata trong những ví dụ nhỏ.

### 7.3. `util` namespace: collection/configuration utilities

```xml
<util:list id="channels">
    <value>sms</value>
    <value>email</value>
</util:list>
```

Cần khai báo `xmlns:util` và mapping `spring-util.xsd` trong `schemaLocation`. Spring đăng ký một bean dạng `List` có thể lookup qua `getBean("channels")`.

Một số utilities khác gồm `<util:map>`, `<util:set>`, `<util:properties>` và `<util:constant>`. Chúng giúp XML ngắn và diễn đạt rõ hơn một số factory beans tương đương.

### 7.4. `context` namespace: kích hoạt annotation processing

Ví dụ:

```xml
<context:annotation-config/>
```

Khi namespace handler tương ứng hoạt động, Spring đăng ký infrastructure để xử lý nhiều dependency-injection/lifecycle annotations trên **những bean đã được đăng ký**. Nhưng phần tử này **không tự động quét package**. Để scan component candidates, dùng:

```xml
<context:component-scan base-package="demo.services"/>
```

Cả hai đoạn là fragments minh họa. Cần có component classes ở package tương ứng; `<context:component-scan>` mới thực hiện component scanning. Nó cũng thường kích hoạt annotation configuration phù hợp theo mặc định. Đừng thêm cả hai chỉ vì “càng nhiều càng tốt” mà không hiểu scope.

### 7.5. `tx` và `aop` là infrastructure, không phải thay thế toàn bộ application setup

```xml
<tx:annotation-driven/>
```

đòi đúng `xmlns:tx`, schema mapping, module dependencies và transaction manager phù hợp. Nó không tự tạo JDBC datasource, database hoặc mọi transaction resource. Tương tự, `aop` namespace cần module hạ tầng tương ứng; không cứ khai báo prefix `aop` là toàn bộ Spring AOP đang chạy.

## 8. Cách Spring kiểm tra schema: AUTO, XSD, DTD, NONE

Spring cung cấp `org.springframework.beans.factory.xml.XmlBeanDefinitionReader` với các validation modes:

- `VALIDATION_AUTO`: tự xác định cơ chế phù hợp.
- `VALIDATION_XSD`: yêu cầu XSD validation.
- `VALIDATION_DTD`: dùng DTD validation khi có tài liệu DTD tương ứng.
- `VALIDATION_NONE`: không thực hiện validation theo mode ấy; XML vẫn cần well-formed và Spring vẫn phải hiểu bean metadata.

API ví dụ:

```java
DefaultListableBeanFactory factory =
        new DefaultListableBeanFactory();

XmlBeanDefinitionReader reader =
        new XmlBeanDefinitionReader(factory);

reader.setValidationMode(XmlBeanDefinitionReader.VALIDATION_XSD);
reader.loadBeanDefinitions(new ClassPathResource("application-context.xml"));
```

Đây là **trích đoạn Java minh họa**; cần imports của `DefaultListableBeanFactory`, `XmlBeanDefinitionReader`, `ClassPathResource` và resource XML. Nó tải bean definitions vào registry; không tương đương toàn bộ `ApplicationContext.refresh()` với post-processors, events và lifecycle callbacks.

### 8.1. Dùng explicit validation khi học/debug

Khi cấu hình Spring XML truyền thống, `ClassPathXmlApplicationContext` mặc định dùng reader và validation strategy phù hợp. Để chứng minh rõ XSD validation, có thể tạo `XmlBeanDefinitionReader` và gọi `setValidationMode(VALIDATION_XSD)`.

Một trang XML hợp lệ về schema nhưng có class name không tồn tại vẫn có thể qua giai đoạn XSD parsing; khi BeanFactory tạo bean mới phát hiện class không load được. Nếu muốn kiểm tra cả semantics, hãy **refresh một Spring application context thật** và gọi các beans.

### 8.2. Cách phân biệt ba lỗi khi debug

**Sai cấu trúc XML:**

```xml
<beans>
    <bean id="x" class="demo.X">
</beans>
```

Thiếu `</bean>` nên XML không well-formed: parser sẽ lỗi trước khi Spring hiểu bean definitions.

**Đúng XML nhưng sai XSD:**

```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">
    <bean id="x" class="demo.X" unknown-attribute="bad"/>
</beans>
```

Đây là ví dụ **cố ý sai** ở tầng schema: thuộc tính không được XSD của Spring beans chấp nhận. Parser validating có thể báo kiểu `cvc-complex-type...`, tùy XML library và phiên bản.

**Đúng XML và XSD nhưng sai Spring runtime:**

```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">
    <bean id="x" class="demo.ClassDoesNotExist"/>
</beans>
```

Đây là ví dụ **cố ý sai** ở tầng bean creation/class loading. XSD không biết class Java có tồn tại trên classpath hay không. Application context sẽ thất bại khi cố resolve hoặc tạo bean theo metadata đó.

### 8.3. Không nên sửa lỗi bằng cách tắt validation trước tiên

Gặp `cvc-complex-type.2.4.a` hay `Cannot find declaration of element 'beans'` không nên phản xạ tắt validation để ứng dụng chạy. Lỗi có thể do namespace URI sai, cặp schemaLocation nhầm hoặc thiếu module cung cấp XSD/namespace handler. Vô hiệu validation làm mất cơ chế phát hiện cấu trúc sai mà không giải quyết missing class, missing bean hoặc namespace handler.

Dùng `VALIDATION_NONE` chỉ khi có lý do kỹ thuật rõ và hiểu mức kiểm tra bị bỏ. Với XML do bên ngoài cung cấp, tắt XSD validation cũng không biến việc deserialize/instantiate tùy ý thành an toàn.

## 9. Những lỗi namespace và schema kinh điển

| Lỗi / triệu chứng | Nguyên nhân thường gặp | Cách kiểm tra và sửa |
| --- | --- | --- |
| `Cannot find declaration of element 'beans'` | Root sai namespace hoặc schema resolver không nạp đúng XSD | So sánh `xmlns` và cặp beans `schemaLocation` theo docs |
| `cvc-complex-type...` | Phần tử/attribute sai, đặt sai vị trí hoặc vi phạm XSD | Đọc tên element/attribute và vị trí lỗi trong XML |
| `The prefix "context" ... is not bound` | Dùng `<context:...>` mà thiếu `xmlns:context` | Khai báo namespace prefix trên root |
| Namespace prefix có nhưng tag không được hiểu | URI không đúng hoặc handler JAR thiếu | Kiểm tra URI thực tế, `META-INF/spring.handlers` và dependencies |
| `Unable to locate Spring NamespaceHandler` | Thiếu module chứa handler hoặc namespace URI không khớp | Bổ sung dependency đúng module, không chỉ XSD |
| `schema_reference.4: Failed to read schema document` | XSD URL/mapping sai, network/SSL IDE hoặc thiếu schema resource | Kiểm tra `spring.schemas`, schemaLocation, environment |
| IDE đỏ nhưng runtime chạy | IDE không resolve XSD trong khi Spring dùng classpath mapping | Kiểm tra IDE catalog/network, không vội sửa XML đúng |
| `xmlns="https://www.springframework.org/schema/beans"` | Đổi nhầm namespace URI từ http sang https | Dùng `http://www.springframework.org/schema/beans` theo docs |
| `xmlns:p` có nhưng cố thêm `spring-p.xsd` | p namespace không có XSD riêng | Giữ prefix, bỏ cặp p XSD không tồn tại |
| `xmlns:c` có nhưng cố thêm `spring-c.xsd` | c namespace cũng không có XSD riêng | Dùng constructor args đúng cú pháp, không khai c XSD |
| `ref="wrongName"` lỗi khi refresh | Tên bean không tồn tại trong context | Kiểm tra `id`/`alias`, files được import, active profiles |
| Class name sai trong `class=...` | Typo FQCN hoặc dependency runtime thiếu | Kiểm tra classpath và `package` thực tế |
| `<context:annotation-config/>` không tạo component | Nó không tự quét classpath | Thêm `<context:component-scan>` khi thực sự muốn scan |
| XSD đúng nhưng property setter lỗi | JavaBean property name/type không phù hợp | Đọc exception creation/`NotWritablePropertyException` |
| Hai beans trùng `id` | Registry name collision/overriding theo policy | Đổi tên/namespace bean hợp lý; không dựa vào XSD phát hiện |
| `util:list` gây lỗi parsing | Chưa khai `xmlns:util`/schema handler hoặc sai cấu trúc | Thêm mapping đúng và Spring module cần thiết |
| XML từ tutorial cũ dùng schema versions lạ | Schema không khớp Spring runtime hiện tại | Đối chiếu Spring 6.2 docs và JAR resources |
| `DOCTYPE` làm tải external resource | XML/parser entity resolution và input không đáng tin | Không load XML user-supplied tùy ý; hạn chế XXE/DTD theo nhu cầu |

### 9.1. Checklist debug trong năm phút

1. **Mở đúng XML file** thực sự được application context đọc: có thể có nhiều `applicationContext.xml` trên classpath.
2. Kiểm tra XML **well-formed** trước: đóng mở tag, dấu nháy, escape `&amp;`, chỉ một root.
3. Kiểm tra namespace URI chính xác ở `xmlns` và mọi prefixed `xmlns:...`.
4. Đọc `xsi:schemaLocation` theo từng **cặp** namespace → XSD, không chỉ nhìn dòng cuối.
5. Kiểm tra dependency Spring có module namespace handler và schema resource không.
6. Xem stack trace phát sinh ở XML parse, schema validation, bean-definition parsing hay bean creation.
7. Nếu context load được, kiểm tra beans bằng `containsBean`/`getBeanNamesForType` và thử method ứng dụng.
8. Với IDE-only warning, kiểm tra cache/catalog/proxy của IDE riêng với Spring runtime.
9. Nếu XML là legacy, đối chiếu tài liệu Spring đúng phiên bản thay vì sửa hằng loạt namespace tùy tiện.
10. Không dùng `VALIDATION_NONE` để che lỗi chưa hiểu.

## 10. Định dạng XML và Java: vài quy tắc quan trọng

### 10.1. XML escaping

Trong attribute literal hoặc text, những ký tự đặc biệt phải escape đúng:

| Muốn biểu diễn | Trong XML |
| --- | --- |
| `&` | `&amp;` |
| `<` | `&lt;` |
| `>` | `&gt;` (khi cần/để rõ nghĩa) |
| `"` trong attribute dùng dấu kép | `&quot;` |
| `'` trong attribute dùng dấu nháy đơn | `&apos;` |

Ví dụ đúng:

```xml
<bean id="formatter" class="demo.Formatter">
    <property name="label" value="A &amp; B"/>
</bean>
```

Đây là đoạn minh họa; cần class `Formatter` và JavaBean property `label`. Spring sẽ nhìn thấy string `A & B` sau khi XML parser xử lý entity.

### 10.2. Java class names trong `class`

`class="demo.MessageService"` phải trỏ đến class Java có thể load được. Đối với nested Java classes, tên nhị phân có thể chứa `$`; trong ứng dụng thật nên tách class độc lập để XML dễ đọc hoặc dùng đúng JVM binary name nếu cần.

### 10.3. Bảo đảm resource nằm trên classpath

Với Gradle Java project:

```text
src/main/java/demo/MessageService.java
src/main/resources/application-context.xml
```

Gradle Java plugin đưa resource trong `src/main/resources` lên runtime classpath. Khi code gọi:

```java
new ClassPathXmlApplicationContext("application-context.xml")
```

Spring tìm file theo classpath resource name, không phải tự tìm mọi file cùng tên trong toàn workspace.

### 10.4. Hợp nhất nhiều XML files

```xml
<import resource="repository-context.xml"/>
```

Đây là child element của `<beans>`, không phải Java `@Import`. File được import phải là Spring XML hợp lệ; đường dẫn tương đối được giải quyết theo file định nghĩa và resource system. Với ứng dụng lớn, chia XML theo module có thể giúp ownership rõ, nhưng phải quản lý tránh import vòng, definitions trùng tên và environment/profile khác nhau.

## 11. Ví dụ Java 17+ chạy được: beans, p/c/util/context và XSD validation

Dự án này dùng **Spring Framework 6.2.19** qua `spring-context`, không dùng Spring Boot. File `application-context.xml` có:

- default `beans` namespace và `xsi` mapping;
- `p` namespace để inject setter property;
- `c` namespace để inject constructor dependency;
- `util` namespace tạo bean `List<String>`;
- `context` namespace bật annotation configuration infrastructure;
- `<alias>` làm tên phụ;
- chương trình Java load XML bằng `ClassPathXmlApplicationContext` rồi gọi managed beans;
- một lượt đọc riêng bằng `XmlBeanDefinitionReader` đặt `VALIDATION_XSD` nhằm chứng minh schema parse không đồng nghĩa bean instantiation.

### 11.1. Cấu trúc thư mục

```text
spring-xml-namespaces-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        ├── java/
        │   └── demo/
        │       ├── MessageRepository.java
        │       ├── MessageService.java
        │       └── XmlApplication.java
        └── resources/
            └── application-context.xml
```

File `settings.gradle`:

```groovy
rootProject.name = 'spring-xml-namespaces-demo'
```

File `build.gradle`:

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.19'
}

application {
    mainClass = 'demo.XmlApplication'
}
```

Dùng JDK 17+ và Gradle tương thích. `spring-context` đem theo các module core/beans cần thiết để parse Spring XML, đăng ký bean definitions và sử dụng util/context namespace handlers.

### 11.2. Java repository

File `src/main/java/demo/MessageRepository.java`:

```java
package demo;

public final class MessageRepository {
    public String lookup(String key) {
        return "order-" + key;
    }
}
```

Đây là class Java thông thường, không có `@Component`. Nó được Spring đăng ký bằng `<bean>` trong XML. Có public no-arg constructor ngầm định để BeanFactory tạo instance.

### 11.3. Java service với constructor và setter

File `src/main/java/demo/MessageService.java`:

```java
package demo;

import java.util.Objects;

public final class MessageService {

    private final MessageRepository repository;
    private String prefix = "";

    public MessageService(MessageRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public void setPrefix(String prefix) {
        this.prefix = Objects.requireNonNull(prefix);
    }

    public String render(String key) {
        return prefix + repository.lookup(key);
    }
}
```

`MessageService` có một constructor bắt buộc nhận `MessageRepository`. `setPrefix` là JavaBean setter được dùng bởi `p:prefix` trong XML. `render("42")` lấy dữ liệu `order-42` từ repository và thêm prefix.

### 11.4. Spring XML resource

File `src/main/resources/application-context.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:p="http://www.springframework.org/schema/p"
       xmlns:c="http://www.springframework.org/schema/c"
       xmlns:util="http://www.springframework.org/schema/util"
       xmlns:context="http://www.springframework.org/schema/context"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/util
           https://www.springframework.org/schema/util/spring-util.xsd
           http://www.springframework.org/schema/context
           https://www.springframework.org/schema/context/spring-context.xsd">

    <bean id="repository" class="demo.MessageRepository"/>

    <bean id="service"
          class="demo.MessageService"
          c:_0-ref="repository"
          p:prefix="Hello: "/>

    <alias name="service" alias="messageGateway"/>

    <util:list id="channels">
        <value>sms</value>
        <value>email</value>
    </util:list>

    <context:annotation-config/>
</beans>
```

**Vì sao không có cặp XSD cho `p` và `c`?** Hai namespaces này không được định nghĩa bằng XSD riêng; chúng dùng cú pháp attribute shorthand được Spring hỗ trợ. Trái lại, `util` và `context` có schema mappings cụ thể.

**Vì sao `c:_0-ref`?** Nó chỉ tham số constructor ở index 0, tham chiếu bean `repository`. Cách dùng index tránh phụ thuộc vào Java parameter name metadata. `p:prefix="Hello: "` set literal value, không phải ref bean.

**Vì sao `<context:annotation-config/>`?** Để minh họa một thẻ custom namespace làm kích hoạt annotation-processing infrastructure. Hai Java class của demo không có annotation injection nên nó **không bắt buộc cho logic repository/service**. Nếu bỏ thẻ này và đồng thời bỏ khai báo `context`/schema mapping, hành vi render chính của ứng dụng vẫn có thể giữ nguyên.

### 11.5. Java main: kiểm tra XSD riêng rồi load context

File `src/main/java/demo/XmlApplication.java`:

```java
package demo;

import java.util.List;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.ClassPathResource;

public final class XmlApplication {

    public static void main(String[] args) {
        DefaultListableBeanFactory registry =
                new DefaultListableBeanFactory();

        XmlBeanDefinitionReader reader =
                new XmlBeanDefinitionReader(registry);
        reader.setValidationMode(XmlBeanDefinitionReader.VALIDATION_XSD);
        reader.loadBeanDefinitions(
                new ClassPathResource("application-context.xml"));

        boolean definitionsLoaded =
                registry.containsBeanDefinition("repository") &&
                registry.containsBeanDefinition("service");
        System.out.println("xmlDefinitionsLoaded = " +
                definitionsLoaded);

        try (ClassPathXmlApplicationContext context =
                     new ClassPathXmlApplicationContext(
                             "application-context.xml")) {

            MessageService service =
                    context.getBean("service", MessageService.class);
            MessageService alias =
                    context.getBean("messageGateway",
                            MessageService.class);

            List<?> channels =
                    context.getBean("channels", List.class);

            System.out.println("message = " +
                    service.render("42"));
            System.out.println("aliasSame = " +
                    (service == alias));
            System.out.println("channels = " + channels);
            System.out.println("repositoryExists = " +
                    context.containsBean("repository"));
        }
    }
}
```

Đoạn `XmlBeanDefinitionReader` thứ nhất chỉ tải **definitions** vào một registry riêng và bật validation XSD; nó không thay thế toàn bộ `ApplicationContext.refresh` và không được dùng để tạo service trong demo. Đoạn `ClassPathXmlApplicationContext` thứ hai là context thật quản lý beans và lifecycle; khi đóng context, Spring thực hiện các bước kết thúc phù hợp cho managed beans.

### 11.6. Chạy và đầu ra kỳ vọng

Trong thư mục `spring-xml-namespaces-demo`:

```bash
gradle run --quiet
```

Nếu có Gradle Wrapper, dùng `./gradlew run --quiet` (Linux/macOS) hoặc `.\gradlew.bat run --quiet` (Windows PowerShell).

Các dòng do chương trình in:

```text
xmlDefinitionsLoaded = true
message = Hello: order-42
aliasSame = true
channels = [sms, email]
repositoryExists = true
```

Một Gradle build hoặc logging backend có thể hiển thị thêm dòng ngoài những `System.out.println` trên.

**Giải thích:**

1. `xmlDefinitionsLoaded = true`: reader parse XML với XSD validation và registry chứa hai definitions được đặt tên đúng. Đây là chứng cứ parse/registration, không phải chứng cứ bean instances đã tạo trong registry này.
2. `message = Hello: order-42`: constructor dependency theo `c:_0-ref` đưa managed repository vào service; setter `p:prefix` nhận literal `Hello: `.
3. `aliasSame = true`: `messageGateway` là alias của `service`, không tạo bản sao service.
4. `channels = [sms, email]`: `util:list` tạo collection bean với hai phần tử theo thứ tự khai báo.
5. `repositoryExists = true`: bean repository có trong context dù class Java không mang stereotype annotation.

### 11.7. Một thay đổi nhỏ để thấy khác biệt XSD và Spring runtime

**Thử A — XML không well-formed:** xóa dấu `>` của một tag. Parser fail trước khi đăng ký bean.

**Thử B — XML sai XSD:** đổi `<alias>` thành `<aliases>` nhưng giữ beans namespace; validating parser không nhận child element này.

**Thử C — XML/XSD hợp lệ nhưng sai runtime:** đổi `class="demo.MessageRepository"` thành `class="demo.MissingRepository"`. Schema có thể vẫn chấp nhận string class; context có khả năng lỗi class loading/creation.

**Thử D — Bỏ `p:prefix`:** service vẫn tạo được, nhưng default Java field `prefix=""` khiến message là `order-42`. Đây là thay đổi cấu hình bean hợp lệ, không phải lỗi XSD.

**Thử E — Sai schema mapping:** thay `spring-util.xsd` bằng một schema khác hoặc bỏ namespace mapping phù hợp. Parser/IDE có thể báo lỗi XSD/namespace; xem stack trace và local schema resolver.

**Thử F — Bỏ `<context:annotation-config/>`:** giữ beans, util, p và c; logic service vẫn hoạt động do không dùng annotated injection trong hai domain classes. Cần bỏ thêm context namespace declaration/schema mapping nếu muốn tối giản file.

## 12. Cách kiểm tra XML mà không cần đoán từ IDE

### 12.1. Dùng Spring làm nguồn kiểm chứng chính

Với XML Spring, một XML validator độc lập có thể hữu ích để phát hiện syntax/schema issues. Tuy nhiên, validator chung **không biết** Spring namespace handlers, bean classpath, constructor signatures hoặc dependency graph.

Vì vậy trong quá trình phát triển:

1. Thực hiện validation trong IDE nếu nó đọc đúng XSD.
2. Chạy `XmlBeanDefinitionReader` với XSD mode nếu cần xác nhận cú pháp/schema.
3. Khởi tạo `ClassPathXmlApplicationContext` để kiểm tra bean definitions và dependency resolution.
4. Gọi một use case nhỏ trên managed beans để chắc semantics đúng.
5. Lặp với các profiles/conditions và dependencies thực sự dùng khi deploy.

### 12.2. Khi nào dùng `xmllint` hoặc XML editor?

Công cụ `xmllint --noout file.xml` (nếu cài) kiểm tra XML well-formedness, không chứng minh bean definitions đúng. Cú pháp `xmllint --schema ...` có thể validate với XSD resource cụ thể, nhưng khi có nhiều namespaces/custom schemas, bạn cần cấu hình catalog/resolver đầy đủ. Trong một Spring project thực tế, phép thử tạo context thường có giá trị xác minh tổng thể cao hơn.

Không ép dùng network validator công khai cho file XML chứa secrets hoặc thông tin nội bộ. Nếu có dữ liệu nhạy cảm, dùng công cụ local và chính sách bảo mật của dự án.

### 12.3. Kiểm tra Gradle dependencies

Ví dụ bài này chỉ dùng `spring-context`, là đủ cho `beans`/`context`/`util` trong Spring Framework Core. Nếu bổ sung `tx`, `aop`, `jdbc` hay namespace của thư viện khác, cần module phù hợp có handler và runtime classes.

Đọc dependency graph khi gặp lỗi namespace handler:

```bash
gradle dependencies --configuration runtimeClasspath
```

Lệnh trên chỉ có ý nghĩa khi chạy trong Gradle project có cấu hình `runtimeClasspath` thích hợp. Nó chứng minh dependency graph, **không xác nhận** rằng file XML đã được load hoặc bean đã tạo thành công.

## 13. Bài tập có đáp án

### Bài 1 — Root của Spring XML

Tài liệu có hai top-level `<bean>` và không có `<beans>`. Hợp lệ như một Spring beans document chuẩn không?

**Đáp án:** Không. XML nói chung yêu cầu một document root, và định dạng Spring beans thông thường dùng root `<beans>` chứa các bean definitions.

### Bài 2 — Namespace URI bị đổi thành HTTPS

Người học đổi `xmlns="http://www.springframework.org/schema/beans"` thành `xmlns="https://www.springframework.org/schema/beans"` vì nghĩ URL mới an toàn hơn. Điều gì sai?

**Đáp án:** XML namespace URI là identifier; hai chuỗi khác nhau là hai namespaces khác nhau. Cần dùng URI mà Spring/schema quy định; XSD location dùng HTTPS có thể được khai báo riêng trong schemaLocation.

### Bài 3 — Schema mapping

Có `xmlns:util="http://www.springframework.org/schema/util"` và muốn sử dụng `<util:list>`. Một cặp `xsi:schemaLocation` phù hợp là gì?

**Đáp án:** `http://www.springframework.org/schema/util` ghép với `https://www.springframework.org/schema/util/spring-util.xsd` trong tài liệu Spring 6.2.

### Bài 4 — `p` namespace và XSD

Dùng `p:name="alpha"` trong bean. Có bắt buộc thêm một `spring-p.xsd` không?

**Đáp án:** Không. `p` namespace là shorthand property attributes do Spring core hỗ trợ và không có XSD riêng như beans/util/context.

### Bài 5 — `c` namespace chọn tham số constructor

Một class có constructor `Service(Repository repository)`. Cần truyền bean tên `repositoryBean` mà không dựa vào parameter name metadata. Thuộc tính c nào phù hợp?

**Đáp án:** `c:_0-ref="repositoryBean"` (khi constructor phù hợp), hoặc dùng `<constructor-arg index="0" ref="repositoryBean"/>`.

### Bài 6 — Lỗi XML hay lỗi runtime?

File có `<bean id="foo" class="no.such.Class"/>` và mọi namespace/XSD đều đúng. XSD có chắc báo class không tồn tại không?

**Đáp án:** Không. XSD xem `class` như metadata dạng string. Spring/JVM kiểm tra class loading/creation ở tầng container.

### Bài 7 — `context:annotation-config` có tự scan package?

Một XML chỉ có `<context:annotation-config/>`, class `demo.UserService` mang `@Service` nhưng không được khai báo bean/import/scan. Có chắc bean `userService` xuất hiện?

**Đáp án:** Không. Để tự quét component candidates, cần `<context:component-scan base-package="demo"/>` hoặc cơ chế đăng ký khác.

### Bài 8 — Prefix hay URI?

Người học khai báo `xmlns:ctx="http://www.springframework.org/schema/context"` rồi dùng `<ctx:annotation-config/>`. Có cần đổi prefix thành `context` mới chạy không?

**Đáp án:** Không, prefix là tên cục bộ; URI đúng mới quan trọng. Tuy vậy theo convention `context` dễ đọc hơn.

### Bài 9 — Alias và singleton

`<alias name="service" alias="gateway"/>` có sinh hai instances singleton không?

**Đáp án:** Không. Hai names trỏ tới cùng bean trong container; alias không tạo bean definition riêng.

### Bài 10 — XSD validation và bean creation

`XmlBeanDefinitionReader` đọc thành công XML và registry có definitions. Điều này có bảo đảm `ApplicationContext.refresh()` thành công không?

**Đáp án:** Không. Constructor dependency sai, thiếu bean ref, factory method lỗi hoặc lifecycle exception vẫn có thể xảy ra khi Spring khởi tạo beans.

### Bài 11 — Thiếu handler

XML có `xmlns:tx`, mapping `spring-tx.xsd` đúng nhưng runtime classpath không có module cung cấp tx namespace handler. Có chắc parser hiểu `<tx:annotation-driven/>` không?

**Đáp án:** Không. Ngoài schema, Spring cần namespace handler và các dependencies hạ tầng tương ứng.

### Bài 12 — Không có mạng

Ứng dụng chạy trong môi trường offline, file XML tham chiếu Spring XSD qua location HTTPS. Có phải chắc chắn không khởi động được?

**Đáp án:** Không. Spring thường dùng classpath `META-INF/spring.schemas` để resolve XSD Spring chính thức. Nếu handler/schema mapping của module khác không có, có thể vẫn lỗi; phải kiểm tra actual resolver và dependencies.

### Bài 13 — Profile khác XSD

XML có `<beans profile="prod">` chứa một bean quan trọng; file qua validation. Trong môi trường chỉ active `dev`, bean đó có chắc được đăng ký không?

**Đáp án:** Không. Profile selection là điều kiện xử lý bean definitions của Spring, khác với việc cấu trúc XML có hợp lệ với XSD hay không.

### Bài 14 — XML escape

Muốn setter nhận chuỗi `Sales & Marketing` trong attribute `value`, viết nguyên dấu `&` có hợp lệ không?

**Đáp án:** Không. Trong XML cần `Sales &amp; Marketing` để parser tái tạo thành chuỗi mong muốn.

### Bài 15 — Kiểm tra bằng công cụ nào?

Bạn muốn chắc Java constructor của `demo.MessageService` nhận bean repository đã cấu hình. Chỉ cần kiểm tra syntax XML không?

**Đáp án:** Không. Cần load bean definitions và khởi tạo Spring context thật, rồi lookup/gọi method hoặc viết integration test.

## 14. Checklist dùng Spring XML bền vững

- Mỗi file có **một root `<beans>`** và XML well-formed.
- Default `xmlns` là URI beans **đúng**.
- `xmlns:xsi` được khai báo khi dùng `xsi:schemaLocation`.
- Mỗi namespace có schema mapping đúng khi cần XSD; cặp namespace→XSD không bị trộn.
- `p` và `c` được dùng đúng để rút gọn injection, **không** bịa XSD riêng cho chúng.
- `class` trỏ đến FQCN có trên runtime classpath, `ref` trỏ đúng bean name/alias.
- Các namespace handlers như `context`, `tx`, `aop` có module/JAR tương ứng.
- Resource `application-context.xml` nằm trên classpath và app load đúng file ấy.
- Không đồng nhất IDE schema warning với runtime Spring error; kiểm tra cả hai.
- Không tắt XSD validation chỉ để che lỗi cấu trúc hoặc thiếu handler.
- Đọc đầy đủ exception chain, phân biệt parsing, validation, registration và instantiation.
- Viết test Spring context nhỏ cho cấu hình quan trọng, cả positive và negative cases.
- Không load Spring XML từ input không tin cậy mà không có kiểm soát an toàn.
- Khi ghép nhiều XML files hoặc profile, kiểm soát trùng bean names và phạm vi các definitions.

## 15. Ghi nhớ cuối cùng

**`<beans>` là root của Spring XML configuration.** Các phần tử như `<bean>` được đặt trong namespace `http://www.springframework.org/schema/beans`. `xmlns` chọn default namespace, `xmlns:prefix` ánh xạ prefix sang một namespace URI, còn `xsi:schemaLocation` ghép từng URI với XSD được dùng cho validation.

**Schema validation chỉ kiểm tra những ràng buộc XSD mô tả.** Nó không tự khẳng định Java class tồn tại, bean references hợp lệ, constructors resolve thành công hoặc application context chạy được. Muốn đảm bảo cấu hình hoạt động, kiểm tra cả XML/XSD lẫn bean-definition parsing, tạo context và behavior ứng dụng.

**`p` và `c` là shorthands không có XSD riêng**, trong khi `util`/`context`/`tx`/`aop` có schema và handler tương ứng. Spring có thể resolve XSD chính thức từ classpath mapping, giảm phụ thuộc mạng; custom namespaces vẫn phải cung cấp handler/schema đúng.

Thực hành tốt nhất khi xử lý Spring XML là luôn trả lời được ba câu: **File có đúng XML namespace/XSD không? Spring có đăng ký đúng bean definitions không? BeanFactory có thực sự tạo và nối được những dependencies không?**

### Tài liệu chính thức

- [Spring Framework 6.2 — Container Overview / XML as an External Configuration DSL](https://docs.spring.io/spring-framework/reference/6.2/core/beans/basics.html).
- [Spring Framework 6.2 — Bean Overview / Naming Beans](https://docs.spring.io/spring-framework/reference/6.2/core/beans/definition.html).
- [Spring Framework 6.2 — Dependencies and Configuration in Detail: p/c namespaces](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-properties-detailed.html).
- [Spring Framework 6.2 — XML Schemas appendix (util, context, aop, beans)](https://docs.spring.io/spring-framework/reference/6.2/core/appendix/xsd-schemas.html).
- [Spring Framework 6.2 — Environment and XML Bean Definition Profiles](https://docs.spring.io/spring-framework/reference/6.2/core/beans/environment.html).
- [Spring Framework 6.2 — `XmlBeanDefinitionReader` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/xml/XmlBeanDefinitionReader.html).
- [Spring Framework 6.2 — `PluggableSchemaResolver` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/xml/PluggableSchemaResolver.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="xml-configuration-002">Khai báo bean bằng id, class và alias</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo bean bằng id, class và alias trong Spring XML

## 1. Vì sao cần hiểu id, class và alias?

Trong Spring Core Container, XML là một cách khai báo **bean definitions** — metadata hướng dẫn container tìm class, tạo object, inject dependency và quản lý scope/lifecycle. Ví dụ:

```xml
<bean id="messageFormatter" class="com.example.MessageFormatter"/>
```

Bốn khái niệm cần phân biệt:

- **`<bean>`**: một bean definition trong XML.
- **`id`**: tên định danh tường minh của bean.
- **`class`**: tên Java class đầy đủ dùng để tạo bean theo cấu hình (hoặc class chứa factory method).
- **`name` / `<alias/>`**: những **tên thay thế** dùng để tìm cùng bean definition, không phải lời yêu cầu tạo thêm bean.

Phân biệt sai dẫn đến lỗi `NoSuchBeanDefinitionException`, `ClassNotFoundException`, phụ thuộc XML không resolve được hoặc vô tình tạo nhiều object khi chỉ muốn nhiều tên.

### Kết quả học tập

Sau bài học, bạn có thể:

1. Viết XML khai báo bean dùng `id` và `class` đúng cách.
2. Hiểu `name` hỗ trợ nhiều bean identifiers và các ký tự phân cách.
3. Đăng ký alias ngoài bean definition bằng `<alias name="..." alias="..."/>`.
4. Tham chiếu bean theo id hoặc alias bằng `ref`; phân biệt `value` và `ref`.
5. Giải thích tại sao alias không làm tăng bean-definition count.
6. Phân biệt **same definition** với **same instance** trong singleton/prototype scopes.
7. Xử lý các trường hợp trùng bean name, sai FQCN, constructor arguments và alias chains.
8. Chạy ví dụ Java 17+ với Spring Framework 6.2.9, XML `beans.xml` và `ClassPathXmlApplicationContext`; dự đoán đúng stdout.

## 2. Bean definition và bean instance là hai tầng

Tập hợp XML định nghĩa **cách** tạo bean. Container chưa nhất thiết tạo object ngay khi XML được parse.

```text
beans.xml
  |
  |  <bean id="formatter"
  |        class="demo.MessageFormatter"/>
  v
XML BeanDefinitionReader
  |
  v
BeanDefinitionRegistry
  |
  | bean name, class, constructor args, scope, lifecycle...
  v
BeanFactory / ApplicationContext
  |
  | Khi cần tạo bean
  v
Java bean instance
```

Ở `singleton` mặc định, nhiều lần tra **cùng bean** trong **cùng context** trả một managed instance. Ở `prototype`, mỗi lần lookup thông thường lại tạo một instance. Alias luôn tham chiếu **bean definition** tương ứng, không tự đổi scope hay số instance cần tạo.

Một Java class có thể được khai báo thành nhiều bean definitions khác nhau. Ngược lại, một bean definition có thể có nhiều names/aliases. Cần xác định mình đang nói về **số definitions**, **số names** hay **số instances**.

## 3. XML beans namespace chuẩn

Một file Spring XML tối thiểu:

**`src/main/resources/beans.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="formatter"
          class="com.example.MessageFormatter"/>

</beans>
```

Phần tử `<beans>` là root của file cấu hình, dùng Spring Beans namespace. `xsi:schemaLocation` khai báo cặp namespace/schema để XML parser hiểu cấu trúc. Các URL thuộc schema, không phải endpoint kinh doanh hay bean names.

`class="com.example.MessageFormatter"` yêu cầu Java class ấy tồn tại trên runtime classpath. Đoạn XML có thể không khởi tạo được nếu class không có constructor phù hợp; phải bổ sung constructor arguments khi cần.

### Khởi động XML context

```java
import org.springframework.context.support.ClassPathXmlApplicationContext;

try (ClassPathXmlApplicationContext context =
         new ClassPathXmlApplicationContext("beans.xml")) {
    Object bean = context.getBean("formatter");
}
```

Spring tìm `beans.xml` trên classpath. Trong Gradle project chuẩn, đặt file ở `src/main/resources/beans.xml` để nó được đưa vào runtime classpath. `try-with-resources` đóng context sau khi sử dụng và kích hoạt destruction callbacks cho những beans/loại scope được quản lý thích hợp.

## 4. id: tên chính của bean

```xml
<bean id="orderService" class="com.example.OrderService"/>
```

`orderService` là bean name, **không phải Java class name**. Nếu đăng ký thành công, có thể tra:

```java
Object service = context.getBean("orderService");
```

`id` cung cấp **một tên**. Convention nên dùng lower camel case: `orderService`, `customerRepository`, `paymentGateway`. Đặt tên theo chức năng/contract thay vì theo một implementation có thể thay trong tương lai.

### id và class có nhất thiết giống nhau?

Không:

```xml
<bean id="billingGateway"
      class="com.example.adapters.CardPaymentGateway"/>
```

Bean name là `billingGateway`, Java type do `class` khai báo là `CardPaymentGateway`. Consumer có thể tiếp tục tham chiếu id `billingGateway` ngay khi đổi implementation sang một class khác có contract tương thích.

### id có cần duy nhất?

Bean name phải được quản lý không xung đột trong container. Việc hai XML files cùng khai báo `id="billingGateway"` có thể gây bean-definition overriding hoặc lỗi tùy cấu hình và môi trường. **Không dựa vào thứ tự nạp XML để âm thầm chọn một definition**.

Trong Spring XML hiện đại, `id` là kiểu `xsd:string`, không còn các ràng buộc cũ của XML `xsd:ID`. Nhưng đó không phải lý do để đặt dấu phân cách hoặc ký tự khó đọc một cách tùy tiện. Tên ổn định, rõ ràng giúp giảm lỗi trong `ref` và monitoring.

## 5. class: nguồn tạo Java object

`class` trong phần lớn XML bean declarations là **fully qualified class name**:

```xml
<bean id="paymentClient"
      class="com.example.integration.PaymentClient"/>
```

Class phải có trên classpath. Khi dùng constructor-based instantiation, Spring chọn và gọi constructor thích hợp. Với class chỉ có constructor nhận `String endpoint`, phải cung cấp argument:

```xml
<bean id="paymentClient"
      class="com.example.integration.PaymentClient">
    <constructor-arg value="https://api.example.test"/>
</bean>
```

Đây là snippet minh họa, cần có class `PaymentClient` với constructor phù hợp. Endpoint chỉ là chuỗi ví dụ; trong production, URL/credential thường đến từ configuration/secret manager, không hard-code nhạy cảm trong source.

### Những lỗi liên quan class

- Thiếu tên package hoặc đánh sai FQCN.
- Class nằm trong JAR chưa có trên runtime classpath.
- Class là interface/abstract nhưng khai báo như bean sẽ instantiate bằng constructor.
- Constructor tham số bắt buộc nhưng không có `<constructor-arg>`.
- Constructor overload không được phân biệt rõ type/index/name.
- Class có dependency khác chưa được resolve.
- Module Java hạn chế truy cập reflection tới constructor cần dùng.

### Một class, nhiều bean definitions

```xml
<bean id="clientA" class="com.example.Client"/>
<bean id="clientB" class="com.example.Client"/>
```

Hai dòng này tạo **hai bean definitions**, có thể dẫn tới hai singleton instances độc lập. Không gọi chúng là aliases. Chỉ dùng hai definitions nếu cần hai cấu hình/ownership/scope khác nhau.

### Trường hợp class là factory class

```xml
<bean id="client"
      class="com.example.ClientFactory"
      factory-method="create"/>
```

Nếu `ClientFactory.create()` là static factory method hợp lệ, Spring có thể gọi nó để lấy target bean. Lúc đó `class` mô tả **factory class**, không nhất thiết là runtime class của object cuối cùng. Cũng có instance factory methods qua `factory-bean`/`factory-method` và interface `FactoryBean<T>`.

Vì vậy, công thức “`class` luôn bằng `context.getBean(name).getClass()`” không chính xác trong mọi tình huống, đặc biệt khi có factory/proxy.

## 6. name: nhiều định danh trên cùng <bean>

`name` là thuộc tính XML hỗ trợ một hoặc nhiều tên:

```xml
<bean id="formatter"
      name="textFormatter, preferredFormatter"
      class="com.example.MessageFormatter"/>
```

Trong đó:

- `formatter` là id.
- `textFormatter` và `preferredFormatter` là những aliases hợp lệ.
- Chúng **không** tạo thêm `MessageFormatter` object hay `BeanDefinition`.

Spring chấp nhận nhiều tên trong `name` được phân cách bằng **dấu phẩy**, **dấu chấm phẩy** hoặc **khoảng trắng**. Ví dụ:

```xml
<bean id="formatter"
      name="textFormatter; preferredFormatter backupFormatter"
      class="com.example.MessageFormatter"/>
```

Dòng này có ba tên thay thế của bean `formatter`. Nên chọn dấu phẩy hoặc cách trình bày rõ ràng nhất cho đội ngũ, tránh alias list dài không cần thiết.

### Chỉ dùng name, không có id

```xml
<bean name="reportService, legacyReportService"
      class="com.example.ReportService"/>
```

Spring có thể lấy tên chính và aliases từ `name`. Không bắt buộc vừa có `id` vừa có `name`. Tuy nhiên nếu một bean có vai trò công khai trong hệ thống, đặt id tường minh thường dễ đọc hơn.

### Không có id lẫn name

```xml
<bean class="com.example.Helper"/>
```

Container có thể **tự sinh bean name**. Điều đó hợp lệ cho một số cấu hình, nhưng không nên xây integration contract giữa nhiều module dựa trên tên tự sinh: khó nhớ và có thể phụ thuộc metadata/nạp cấu hình. Nếu dùng `ref` hay lookup theo string name, định danh explicit là lựa chọn dễ bảo trì.

## 7. <alias/>: đặt tên ngoài nơi định nghĩa bean

Một XML file khác có thể khai báo:

```xml
<alias name="formatter" alias="externalFormatter"/>
```

Ý nghĩa hai thuộc tính trong phần tử này **khác ý nghĩa `name` trong phần tử `<bean>`**:

- `name="formatter"` là **tên bean đích đang muốn đặt alias**.
- `alias="externalFormatter"` là **tên thay thế được đăng ký**.

Sau khi hai phần tử được nạp vào cùng context:

```xml
<bean id="formatter" class="com.example.MessageFormatter"/>
<alias name="formatter" alias="externalFormatter"/>
```

`context.getBean("externalFormatter")` resolve tới **cùng bean definition** với `context.getBean("formatter")`.

### Alias không phải bean mới

```text
Tên chính: "formatter" ----------------------+
                                              |
Alias từ name: "textFormatter" ---------------+--> BeanDefinition formatter
                                              |         |
Alias từ <alias>: "externalFormatter" --------+         v
                                                     target bean
```

Các tên là những khóa khác nhau để lookup một registration. Alias không phải alias class, Java inheritance, wrapper, proxy, HTTP redirect hoặc copy bean.

### Khi nào <alias/> có giá trị hơn name?

Khi bean ở module A nhưng module B muốn gọi bằng tên riêng **mà không sửa file XML của module A**.

**`core-beans.xml`:**

```xml
<bean id="coreFormatter" class="com.example.Formatter"/>
```

**`report-aliases.xml`:**

```xml
<alias name="coreFormatter" alias="reportFormatter"/>
```

Nạp **cả hai** vào cùng `ApplicationContext` thì report code có thể dùng `reportFormatter`. Snippets này giả định `Formatter` có constructor no-arg phù hợp; trong ví dụ đầy đủ sau, class thực tế có constructor cần tham số và XML sẽ cung cấp.

### Alias chain và cycle

```xml
<bean id="mainClient" class="com.example.Client"/>
<alias name="mainClient" alias="oldClient"/>
<alias name="oldClient" alias="reportClient"/>
```

Spring alias registry có thể resolve alias chain theo canonical name. Nhưng alias nhiều tầng làm việc debug phức tạp; alias cycles hoặc xung đột có thể bị từ chối. Tốt hơn nên có một canonical name và aliases trực tiếp với mục đích rõ ràng.

## 8. Ví dụ hoàn chỉnh Java 17+ với Spring Framework 6.2.9

Demo dùng XML `beans.xml` thuần. Có:

- Một bean `MessageFormatter` được đăng ký bằng `id="formatter"`.
- Hai aliases được khai báo qua `name`.
- Một alias được đăng ký bằng `<alias/>`.
- Một `GreetingClient` constructor-inject formatter **qua alias**.
- Một chương trình Java kiểm tra số definitions, aliases, reference identity và kết quả greeting.

Không cần `@ComponentScan`, Spring Boot hay web server.

### 8.1. Cấu trúc

```text
xml-bean-naming-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        ├── java/
        │   └── demo/
        │       ├── MessageFormatter.java
        │       ├── GreetingClient.java
        │       └── XmlBeanNamingDemo.java
        └── resources/
            └── beans.xml
```

### 8.2. Gradle setup

**`settings.gradle`**

```groovy
rootProject.name = 'xml-bean-naming-demo'
```

**`build.gradle`**

```groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.9'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'demo.XmlBeanNamingDemo'
}
```

`spring-context` có những dependency transitive cần thiết cho `ClassPathXmlApplicationContext`, XML BeanDefinitionReader và bean container. Với Java 17+ và Gradle tương thích toolchains, chương trình có thể được compile/run mà không cài Spring Boot.

### 8.3. MessageFormatter

**`src/main/java/demo/MessageFormatter.java`**

```java
package demo;

import java.util.Objects;

public final class MessageFormatter {
    private final String prefix;

    public MessageFormatter(String prefix) {
        this.prefix = Objects.requireNonNull(prefix);
    }

    public String format(String name) {
        return prefix + ", " + name + "!";
    }
}
```

Đây là POJO bình thường không có Spring annotation. Class có constructor nhận `String`, nên XML phải cấp constructor argument.

### 8.4. GreetingClient

**`src/main/java/demo/GreetingClient.java`**

```java
package demo;

import java.util.Objects;

public final class GreetingClient {
    private final MessageFormatter formatter;

    public GreetingClient(MessageFormatter formatter) {
        this.formatter = Objects.requireNonNull(formatter);
    }

    public MessageFormatter formatter() {
        return formatter;
    }

    public String greet(String name) {
        return formatter.format(name);
    }
}
```

`GreetingClient` chỉ biết dependency Java `MessageFormatter`. Nó không biết bean đó được gọi là `formatter` hay `externalFormatter` trong XML.

### 8.5. XML declarations

**`src/main/resources/beans.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <!-- One bean definition, multiple lookup names. -->
    <bean id="formatter"
          name="textFormatter, preferredFormatter"
          class="demo.MessageFormatter">
        <constructor-arg value="Xin chào"/>
    </bean>

    <!-- Adds a name, NOT another bean definition. -->
    <alias name="formatter" alias="externalFormatter"/>

    <!-- Uses an alias to inject the same managed dependency. -->
    <bean id="greetingClient" class="demo.GreetingClient">
        <constructor-arg ref="externalFormatter"/>
    </bean>

</beans>
```

Có đúng hai `<bean>` elements: `formatter` và `greetingClient`. `name` và `<alias/>` chỉ bổ sung tên cho `formatter`.

### 8.6. Main class

**`src/main/java/demo/XmlBeanNamingDemo.java`**

```java
package demo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public final class XmlBeanNamingDemo {
    public static void main(String[] args) {
        try (ClassPathXmlApplicationContext context =
                     new ClassPathXmlApplicationContext("beans.xml")) {

            MessageFormatter primary =
                    context.getBean("formatter", MessageFormatter.class);
            MessageFormatter viaName =
                    context.getBean("textFormatter", MessageFormatter.class);
            MessageFormatter viaSecondName =
                    context.getBean("preferredFormatter", MessageFormatter.class);
            MessageFormatter viaAlias =
                    context.getBean("externalFormatter", MessageFormatter.class);

            Set<String> aliases = new HashSet<>(
                    Arrays.asList(context.getAliases("formatter"))
            );

            GreetingClient client =
                    context.getBean("greetingClient", GreetingClient.class);

            System.out.println("bean-definitions="
                    + context.getBeanDefinitionCount());
            System.out.println("id-exists="
                    + context.containsBean("formatter"));
            System.out.println("alias-exists="
                    + context.containsBean("externalFormatter"));

            System.out.println("name-alias-registered="
                    + aliases.contains("textFormatter"));
            System.out.println("second-name-alias-registered="
                    + aliases.contains("preferredFormatter"));
            System.out.println("alias-element-registered="
                    + aliases.contains("externalFormatter"));

            System.out.println("all-names-same-instance="
                    + (primary == viaName
                    && primary == viaSecondName
                    && primary == viaAlias));

            System.out.println("alias-is-definition="
                    + context.containsBeanDefinition("externalFormatter"));
            System.out.println("client-uses-managed-bean="
                    + (client.formatter() == primary));
            System.out.println("greeting=" + client.greet("Spring"));
        }
    }
}
```

### 8.7. Chạy

Từ thư mục `xml-bean-naming-demo`:

```bash
gradle run --quiet
```

Nếu có Gradle Wrapper, dùng `./gradlew run --quiet` trên Linux/macOS hoặc `gradlew.bat run --quiet` trên Windows.

Chạy `javac XmlBeanNamingDemo.java` riêng không đủ vì phải compile ba Java files và có Spring dependencies trên classpath. Gradle sẽ đưa cả source và `src/main/resources/beans.xml` vào runtime classpath.

### 8.8. Stdout chương trình

```text
bean-definitions=2
id-exists=true
alias-exists=true
name-alias-registered=true
second-name-alias-registered=true
alias-element-registered=true
all-names-same-instance=true
alias-is-definition=false
client-uses-managed-bean=true
greeting=Xin chào, Spring!
```

Các log của Gradle hoặc logging framework, nếu có, không thuộc mười dòng được in bởi `System.out.println`.

### 8.9. Phân tích kết quả

**`bean-definitions=2`:** Chỉ có hai bean definitions, vì `<alias/>` không đăng ký thêm một definition.

**`id-exists=true`:** `formatter` được đăng ký từ `id`.

**`alias-exists=true`:** `externalFormatter` đã được registry biết là alias.

**`name-alias-registered=true` và `second-name-alias-registered=true`:** `textFormatter` và `preferredFormatter` là những tên từ `name`.

**`alias-element-registered=true`:** `<alias name="formatter" alias="externalFormatter"/>` tạo thêm alias bên ngoài bean definition.

**`all-names-same-instance=true`:** Scope là singleton mặc định; lookup qua cả bốn tên trả cùng object.

**`alias-is-definition=false`:** `containsBeanDefinition("externalFormatter")` không thấy definition mang tên alias. Khác với `containsBean("externalFormatter")` trả true do alias resolution.

**`client-uses-managed-bean=true`:** `<constructor-arg ref="externalFormatter"/>` cấp managed instance cho `GreetingClient`. Alias không cản constructor injection.

**`greeting=Xin chào, Spring!`:** Giá trị `Xin chào` đến từ XML constructor argument; Java method nối với `Spring` rồi thêm dấu chấm than.

## 9. Tên thay thế không đồng nghĩa instance thay thế

Một bean có thể được gọi bằng nhiều aliases nhưng vẫn chỉ có **một definition**:

```xml
<bean id="primaryClient" class="com.example.Client"/>
<alias name="primaryClient" alias="legacyClient"/>
<alias name="primaryClient" alias="reportClient"/>
```

Sơ đồ:

```text
"primaryClient" ------+
"legacyClient" -------+--> bean definition: primaryClient
"reportClient" -------+                  |
                                         v
                              singleton instance (nếu singleton)
```

Ngược lại:

```xml
<bean id="primaryClient" class="com.example.Client"/>
<bean id="legacyClient" class="com.example.Client"/>
<bean id="reportClient" class="com.example.Client"/>
```

là **ba bean definitions** và có thể tạo ba managed singleton instances. Đừng copy bean definitions chỉ để giữ tên cũ khi điều cần là alias.

## 10. Alias và scope: singleton so với prototype

Với singleton:

```xml
<bean id="client"
      name="clientAlias"
      class="com.example.Client"
      scope="singleton"/>
```

hai lần gọi `getBean("client")` và `getBean("clientAlias")` trong cùng context thường trả một instance.

Với prototype:

```xml
<bean id="requestBuilder"
      name="builderAlias"
      class="com.example.RequestBuilder"
      scope="prototype"/>

<alias name="requestBuilder" alias="externalBuilder"/>
```

Tất cả names chỉ tới **một prototype definition**; nhưng mỗi lần lookup thông thường tạo prototype mới. Vì vậy:

```java
Object first = context.getBean("requestBuilder");
Object second = context.getBean("externalBuilder");
System.out.println(first == second);  // thông thường false
```

Aliases không “ép singleton” và không giữ reference tới object đã tạo. Chúng chỉ giải quyết tên trước khi BeanFactory áp dụng scope của target definition.

### Prototype được inject vào singleton

Nếu singleton service nhận một prototype bean thông qua `ref` trong constructor, instance prototype đó được tạo khi singleton được dựng và reference ấy thường được giữ suốt vòng đời singleton. Nó không tự thay bằng prototype mới mỗi lần service method chạy. Nếu muốn lookup prototype động, hãy dùng `ObjectProvider<T>`/`Provider<T>` hoặc cơ chế thích hợp, không phụ thuộc alias.

## 11. Tham chiếu qua ref: name và value rất khác nhau

### Constructor injection

```xml
<bean id="repository" class="com.example.Repository"/>
<alias name="repository" alias="primaryRepository"/>

<bean id="service" class="com.example.Service">
    <constructor-arg ref="primaryRepository"/>
</bean>
```

`Service` phải có constructor phù hợp nhận `Repository`. Spring resolve alias `primaryRepository` và đưa bean object vào constructor.

### Dạng lồng <ref/>

```xml
<bean id="service" class="com.example.Service">
    <constructor-arg>
        <ref bean="primaryRepository"/>
    </constructor-arg>
</bean>
```

Có hiệu ứng lookup tương đương trong use case trên.

### Property/setter injection

```xml
<bean id="service" class="com.example.Service">
    <property name="repository" ref="primaryRepository"/>
</bean>
```

Ở đây `name="repository"` là tên **JavaBean property** tương ứng setter của `Service`; `ref="primaryRepository"` là tên bean dependency. Không nhầm `property@name` với `bean@name`.

### Dùng value khi muốn literal

```xml
<constructor-arg value="primaryRepository"/>
```

sẽ cung cấp **chuỗi** `primaryRepository`, không lookup bean. Nếu constructor nhận `Repository`, câu lệnh này có thể tạo lỗi conversion/constructor matching. Đổi thành `ref` khi yêu cầu object dependency.

## 12. Đăng ký aliases giữa nhiều XML files

Một dự án legacy có thể muốn module A định nghĩa bean còn module B cấp tên riêng:

**`shared.xml`**

```xml
<beans xmlns="http://www.springframework.org/schema/beans">
    <bean id="sharedFormatter"
          class="com.example.MessageFormatter"/>
</beans>
```

**`reporting.xml`**

```xml
<beans xmlns="http://www.springframework.org/schema/beans">
    <alias name="sharedFormatter" alias="reportFormatter"/>
</beans>
```

Hai snippets chỉ minh họa phân tách sources; class `MessageFormatter` cần constructor phù hợp trong dự án thực. Nạp cả hai files vào **cùng context**, ví dụ:

```java
try (ClassPathXmlApplicationContext context =
         new ClassPathXmlApplicationContext(
                 "shared.xml", "reporting.xml")) {
    Object formatter = context.getBean("reportFormatter");
}
```

Đừng tạo hai `ClassPathXmlApplicationContext` độc lập rồi mong aliases từ context này tự được biết ở context kia.

### XML import

Một file root cũng có thể nạp XML con:

```xml
<beans xmlns="http://www.springframework.org/schema/beans">
    <import resource="shared.xml"/>
    <alias name="sharedFormatter" alias="reportFormatter"/>
</beans>
```

Trong dự án thật, có thể bổ sung namespace/schemaLocation đầy đủ như demo chính. Ý nghĩa: bean definitions và aliases được nạp vào cùng registry.

### Java configuration kết hợp XML

Nếu Java `@Configuration` làm composition root, có thể dùng Spring `@ImportResource("classpath:shared.xml")`. Điều này nhập XML metadata vào cùng context, **không** tạo XML context thứ hai. Aliases trong XML vẫn hoạt động theo bean registry tương ứng.

## 13. XML names so với Java @Bean names

| Cơ chế | Ý nghĩa |
| --- | --- |
| `<bean id="client" class="..."/>` | XML bean definition tên `client`. |
| `<bean id="client" name="legacyClient" class="..."/>` | XML bean tên `client` và alias `legacyClient`. |
| `<alias name="client" alias="externalClient"/>` | Thêm alias ngoài phần tử bean. |
| `@Bean("client")` | Java configuration method cung cấp bean tên `client`. |
| `@Bean({"client", "legacyClient"})` | Java bean với tên chính và alias. |
| `@Component("client")` | Component có tên explicit khi được scan. |

Spring quản lý bean names và aliases ở registry; **nguồn khai báo** khác nhau không nhất thiết tạo bean type khác. Nhưng cùng một tên có thể bị đăng ký qua nhiều nguồn và xung đột.

Không được nghĩ rằng vì một bean có alias trong XML nên Java `@Bean` cùng tên sẽ tự hợp nhất với XML bean đó. Nếu hai nguồn đăng ký **hai definitions**, Spring áp quy tắc name conflicts/overriding. Đặt owner rõ ràng cho mỗi bean.

## 14. API kiểm tra bean và alias khi debug

### getBean(name)

```java
Object target = context.getBean("externalFormatter");
```

Lookup bean name hoặc alias. Với singleton thông thường, alias trả cùng instance với canonical name.

### getBean(name, type)

```java
MessageFormatter target =
        context.getBean("externalFormatter", MessageFormatter.class);
```

Tra bằng tên/alias, đồng thời yêu cầu type phù hợp. Nếu bean không tồn tại có thể gặp `NoSuchBeanDefinitionException`; nếu type sai có thể gặp `BeanNotOfRequiredTypeException`.

### containsBean(name)

```java
boolean present = context.containsBean("externalFormatter");
```

Kiểm tra name/alias có thể resolve tới bean.

### containsBeanDefinition(name)

```java
boolean ownDefinition =
        context.containsBeanDefinition("externalFormatter");
```

Kiểm tra definition **trực tiếp** mang name ấy trong registry hiện tại. Với alias thông thường, kết quả là false dù `containsBean` trả true. Trong parent-child hierarchy, hai methods còn khác nhau về phạm vi lookup.

### getAliases(name)

```java
String[] aliases = context.getAliases("formatter");
```

Có thể liệt kê các tên thay thế của canonical bean. Không dựa vào thứ tự phần tử của mảng để làm hợp đồng; nếu cần output ổn định trong test, sort.

### getBeanDefinitionNames và getBeanDefinitionCount

```java
String[] names = context.getBeanDefinitionNames();
int count = context.getBeanDefinitionCount();
```

Chúng quan sát **bean definitions**, không coi aliases là definitions mới. Trong demo thuần XML có đúng hai top-level `<bean>` nên count là 2.

## 15. Lỗi thường gặp

### Lỗi 1 — Đổi nhầm vị trí id và class

```xml
<bean id="demo.MessageFormatter" class="formatter"/>
```

Bean name và Java class name bị lẫn. Sửa `class` thành FQCN thật, `id` thành tên logic.

### Lỗi 2 — Dùng hai definitions khi chỉ muốn hai names

Hai `<bean>` với class giống nhau có thể tạo hai instances. Nếu thực sự chỉ muốn tên cũ cùng tên mới, dùng `<alias/>` hoặc `name`.

### Lỗi 3 — Alias conflict

Đặt alias `reportService` trong khi một bean khác đã có name `reportService` gây ambiguity/conflict. Rà soát names của toàn context, gồm XML imports, Java config và scanned beans.

### Lỗi 4 — Alias trỏ tới target không hiện diện

Một file aliases được load nhưng bean target không được nạp hoặc bị profile/conditional loại bỏ. Alias không tự tạo definition target. Kiểm tra các nguồn được nạp và actual bean registration.

### Lỗi 5 — Trùng bean definition name

Hai XML files cùng khai báo `id="client"` có thể gây lỗi hoặc overriding tùy cấu hình. Không coi thứ tự files là API ổn định để quyết định implementation.

### Lỗi 6 — Sai ref

`<constructor-arg ref="externalFormater"/>` sai chính tả so với `externalFormatter` làm Spring không resolve dependency. Có thể bắt lỗi ngay bằng integration test tạo context.

### Lỗi 7 — Dùng value thay ref

`value="client"` là literal, `ref="client"` mới lấy bean. Phân biệt theo kiểu tham số Java.

### Lỗi 8 — Constructor không khớp

Nếu class chỉ có constructor `MessageFormatter(String)`, XML `<bean class="demo.MessageFormatter"/>` không đủ thông tin để tạo instance. Khai báo `<constructor-arg value="..."/>`.

### Lỗi 9 — Sai fully qualified class name

Package/class name trong XML khác file Java, hoặc JAR không nằm trên classpath. Xác nhận build output/runtime classpath.

### Lỗi 10 — Tưởng alias thay bean scope

Alias không biến prototype thành singleton và không kéo dài vòng đời. Hai lookup prototype từ hai aliases có thể tạo hai objects khác nhau.

### Lỗi 11 — Nghĩ không có id thì bean không tồn tại

XML có thể dùng `name` hoặc tên sinh tự động. Tuy nhiên với bean cần dùng qua `ref`, định danh explicit giúp code dễ hiểu hơn.

### Lỗi 12 — Tưởng getAliases trả danh sách bean definitions

Aliases là names thay thế, không phải registrations độc lập. Dùng `getBeanDefinitionNames` để xem definitions, `getAliases` để xem names.

### Lỗi 13 — Dùng alias để che hai tài nguyên khác nhau

Nếu `readDatabase` và `writeDatabase` có connection pools khác nhau, đừng tạo alias khiến chúng cùng trỏ một pool chỉ để tránh cấu hình thêm bean. Alias chỉ đúng khi hai tên thật sự đại diện **cùng dependency**.

### Lỗi 14 — Nạp alias trong context độc lập

Một alias tồn tại trong context A không làm context B tự tìm được bean. Cấu hình composition/parent-child đúng ý định; không tự xây context thứ hai trong factory method.

### Lỗi 15 — Nhầm FactoryBean với alias

Tiền tố `&` trong lookup `&factoryName` dùng để yêu cầu **FactoryBean instance** thay vì product, không phải alias thông thường.

### Lỗi 16 — Dùng names mang dữ liệu nhạy cảm

Bean names thường xuất hiện trong logs, error messages và monitoring. Không đặt API tokens, password hoặc credentials làm id/alias.

## 16. Kiểm thử XML definitions và naming

Kiểm thử bean names không chỉ bằng string lookup mà còn bằng identity và số definitions.

- Kiểm tra `containsBean("formatter")` và `containsBean("externalFormatter")`.
- Kiểm tra aliases đều xuất hiện trong `getAliases("formatter")`.
- Kiểm tra `getBeanDefinitionCount()` không tăng chỉ vì thêm `<alias/>`.
- Với singleton, so reference bằng `==` giữa lookup id và alias.
- Với prototype, assert hai lookup là hai instances hợp lệ theo scope, không ép identity.
- Kiểm tra constructor injection qua `ref` bằng behavior của consumer.
- Test context thất bại khi thiếu target bean hoặc class để phát hiện lỗi XML sớm.

### Tình huống test migration

Khi đổi bean name `oldClient` thành `primaryClient`, có thể giữ:

```xml
<bean id="primaryClient" class="com.example.Client"/>
<alias name="primaryClient" alias="oldClient"/>
```

Trong integration test, kiểm tra cả hai names resolve và cùng managed bean (khi singleton). Khi xóa alias cũ, test nên chỉ fail ở những module vẫn dùng tên cũ, giúp lộ dependency cần migrate.

## 17. Bài tập tự luyện

**Bài 1.** Cho XML:

```xml
<bean id="primary" name="secondary,tertiary"
      class="demo.Client"/>
<alias name="primary" alias="legacy"/>
```

Có bao nhiêu bean definitions? Bao nhiêu tên lookup?

**Đáp án:** Một definition, bốn tên: `primary`, `secondary`, `tertiary`, `legacy`.

**Bài 2.** Nếu tạo thêm:

```xml
<bean id="another" class="demo.Client"/>
```

Có phải `another` là alias của `primary` không?

**Đáp án:** Không. Đây là definition thứ hai dù cùng Java class.

**Bài 3.** `<alias name="primary" alias="legacy"/>` làm constructor của bean primary chạy thêm lần nữa không?

**Đáp án:** Không. Alias chỉ đăng ký tên thay thế.

**Bài 4.** Với prototype, `getBean("primary") == getBean("legacy")` luôn true không?

**Đáp án:** Không. Mỗi lookup prototype thông thường tạo instance mới.

**Bài 5.** XML có `<constructor-arg value="legacy"/>`, constructor yêu cầu `Client`. Có đúng không?

**Đáp án:** Không. Phải dùng `ref="legacy"` để lookup bean object.

**Bài 6.** `<property name="client" ref="legacy"/>`: hai `name` có cùng nghĩa không?

**Đáp án:** Không. `property@name` chọn JavaBean property; bean `id`/`alias` đặt tên dependency được `ref` resolve.

**Bài 7.** Spring có thể tạo bean khi không khai báo `id` và `name` không?

**Đáp án:** Có thể tự sinh tên trong một số tình huống. Nhưng tên explicit được khuyến nghị cho bean cần tham chiếu công khai.

**Bài 8.** Tại sao `containsBean("legacy")` có thể true nhưng `containsBeanDefinition("legacy")` false?

**Đáp án:** Vì legacy chỉ là alias, không có definition trực tiếp mang tên ấy.

**Bài 9.** `class` luôn là runtime class của bean từ factory method không?

**Đáp án:** Không. Nếu dùng factory-method/FactoryBean/proxy, declared class có thể là factory class hoặc runtime bean type khác.

**Bài 10.** Có nên đặt alias cho tên `readClient` trỏ tới bean `writeClient` nếu hai clients có policies khác nhau?

**Đáp án:** Không, trừ khi đó thực sự là cùng resource/contract. Alias không phù hợp để đánh đồng hai dependencies cần tách biệt.

## 18. Tài liệu chính thức

- [Spring Framework 6.2 — Bean Overview và Naming Beans](https://docs.spring.io/spring-framework/reference/6.2/core/beans/definition.html): ý nghĩa `id`, `name`, aliases, `class` và naming conventions.
- [Spring Framework 6.2 — Dependency Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies.html): `value`, `ref`, constructors, properties.
- [Spring Framework 6.2 — Bean Scopes](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html): singleton và prototype.
- [Spring Framework 6.2 — Using the @Bean Annotation](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/bean-annotation.html): đặt tên và aliases cho Java configuration để đối chiếu.
- [Spring Framework 6.2 API — ClassPathXmlApplicationContext](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/support/ClassPathXmlApplicationContext.html): tạo context từ classpath XML.
- [Spring Framework 6.2 API — BeanFactory](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/BeanFactory.html): lookup theo name/type và `getAliases`.
- [Spring Framework 6.2 API — AliasRegistry](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/core/AliasRegistry.html): alias registration và canonical name.

**Ghi nhớ:** `id` là tên định danh, `class` mô tả Java type/factory để tạo bean, còn `name` và `<alias/>` cung cấp tên tra cứu thay thế. **Một alias không tạo bean definition hoặc object mới**; số object thật sự do scope và lifecycle quyết định. Khi cấu hình XML lớn lên, hãy giữ một canonical name rõ ràng, chỉ thêm aliases có mục đích và kiểm thử lookup/identity trước khi đổi tên beans.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="xml-configuration-003">Cấp tham số qua constructor-arg</a>

<details>
<summary>Xem chi tiết</summary>

# Cấp tham số qua constructor-arg trong Spring XML Core Container

## 1. Mục tiêu bài học

Trong Spring Framework, **constructor injection** là hình thức Dependency Injection (DI) mà container tạo object bằng cách gọi constructor với các tham số đã được phân giải. Khi cấu hình bean bằng XML, phần tử **`<constructor-arg>`** mô tả những giá trị hoặc references cần truyền vào constructor (và trong các trường hợp factory method, mô tả các tham số factory tương ứng).

Ví dụ đơn giản:

```xml
<bean id="catalogRepository"
      class="demo.CatalogRepositoryImpl"/>

<bean id="catalogService"
      class="demo.CatalogService">
    <constructor-arg ref="catalogRepository"/>
    <constructor-arg value="CATALOG"/>
</bean>
```

Đây là **ví dụ minh họa**, yêu cầu `demo.CatalogRepositoryImpl` và `demo.CatalogService` được định nghĩa với constructors tương thích. Spring không chỉ “chèn XML vào Java”; nó đọc bean definitions, giải quyết dependency rồi gọi constructor thích hợp để tạo bean.

Bài này giúp bạn:

1. Hiểu khác biệt giữa `<constructor-arg ref="..."/>` và `<constructor-arg value="..."/>`.
2. Biết Spring chọn constructor và ánh xạ arguments theo **vị trí**, **kiểu** hoặc **tên tham số**.
3. Sử dụng `index` (bắt đầu từ 0), `type` và `name` để loại bỏ nhập nhằng.
4. Hiểu điều kiện sử dụng `name` với Java 17: compiler option `-parameters` hoặc annotation JDK `@ConstructorProperties`.
5. Dùng dạng nested `<ref>`, `<value>`, `<null/>`, `<list>`, `<map>` và inner bean khi cần.
6. Biết shortcut **`c:` namespace** và sự khác biệt giữa `c:_0-ref` với `c:name-ref`.
7. Phân biệt constructor injection, setter/property injection và XML constructor autowiring.
8. Hiểu cách Spring chuyển chuỗi XML thành `int`, `long`, `boolean`, enum hoặc loại dữ liệu có conversion phù hợp.
9. Nhận biết overloaded constructors, null ambiguity, missing bean, cycles và những lỗi khởi tạo thường gặp.
10. Chạy ví dụ đầy đủ với **Java 17 + Spring Framework 6.2.x**; xác nhận dependency identity và stdout.
11. Thiết kế XML dễ đọc khi kết hợp với cấu hình Java, import XML, scope và lifecycle.
12. Biết cách kiểm thử, debug và chuyển sang Java-based configuration mà vẫn giữ nguyên hợp đồng dependency.

**Ý chính:** `<constructor-arg>` biểu diễn **hợp đồng khởi tạo bean**. Nó không phải setter, không phải tên biến Java, cũng không phải lệnh tự gọi constructor ngay khi file XML được đọc.

## 2. Constructor injection diễn ra như thế nào?

Ví dụ Java:

```java
package example;

public final class ShippingService {
    private final ShippingRepository repository;

    public ShippingService(ShippingRepository repository) {
        this.repository = repository;
    }

    public String dispatch(String trackingCode) {
        return repository.saveTracking(trackingCode);
    }
}
```

Đoạn này chưa độc lập: cần định nghĩa interface `ShippingRepository` với method `saveTracking`. Class `ShippingService` tự tuyên bố **bắt buộc cần repository** ngay ở constructor. Nó không gọi `new ShippingRepositoryImpl()` bên trong, cũng không tìm BeanFactory bằng chuỗi tên.

Khai báo XML:

```xml
<bean id="shippingRepository"
      class="example.InMemoryShippingRepository"/>

<bean id="shippingService"
      class="example.ShippingService">
    <constructor-arg ref="shippingRepository"/>
</bean>
```

Trong một `ApplicationContext` đọc XML này, Spring thực hiện về mặt khái niệm:

```text
Đọc XML <bean id="shippingService">
                  |
                  v
BeanDefinition shippingService
  beanClass = example.ShippingService
  constructorArgs = [ref(shippingRepository)]
                  |
                  v
BeanFactory cần tạo shippingService
                  |
                  v
Resolve bean shippingRepository
                  |
                  v
Chọn constructor phù hợp
                  |
                  v
new ShippingService(managedRepository)
                  |
                  v
Apply bean initialization/post-processors
                  |
                  v
Managed bean shippingService
```

Nếu `shippingRepository` là singleton mặc định, instance được truyền vào `ShippingService` là đối tượng do **cùng BeanFactory** quản lý. Constructor dependency graph còn định hướng thứ tự khởi tạo cần thiết: để xây service, Spring phải phân giải repository trước.

### Vì sao constructor injection thường được ưu tiên?

- Dependency bắt buộc **hiển thị ngay trên API của class**.
- Cho phép các field `final`; object có trạng thái hợp lệ sớm hơn.
- Fail-fast khi thiếu collaborator thay vì tạo bean nửa hoàn chỉnh.
- Unit test có thể gọi constructor với fake/stub mà không phải mở Spring.
- Giảm khả năng setter bị quên gọi.
- Làm đồ thị phụ thuộc dễ phân tích và phát hiện vòng hơn.

Constructor injection không bảo đảm mọi thứ tự động đúng. Nếu hai constructors yêu cầu nhau, Spring sẽ không thể hoàn tất; hoặc nếu overloaded constructors/values không rõ, XML cần disambiguation.

## 3. Cú pháp chuẩn của <constructor-arg>

Một cấu hình XML Spring thông dụng:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
          http://www.springframework.org/schema/beans
          https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="backend" class="example.MemoryBackend"/>

    <bean id="gateway" class="example.Gateway">
        <constructor-arg ref="backend"/>
        <constructor-arg value="production"/>
    </bean>

</beans>
```

Hai dạng thuộc tính phổ biến:

| Cú pháp | Spring nhận được gì? | Ví dụ |
| --- | --- | --- |
| `ref="beanName"` | Reference đến **bean khác** trong container | `<constructor-arg ref="backend"/>` |
| `value="text"` | **Chuỗi ký tự**, được convert sang type constructor parameter | `<constructor-arg value="15"/>` |
| `index="0"` | Vị trí parameter trong constructor, **0-based** | `<constructor-arg index="0" ref="backend"/>` |
| `type="int"` | Gợi ý kiểu parameter để chọn constructor/match | `<constructor-arg type="int" value="15"/>` |
| `name="timeout"` | Match theo tên Java constructor parameter | `<constructor-arg name="timeout" value="15"/>` |

Ví dụ `Gateway`, `MemoryBackend` là các kiểu minh họa, chưa thuộc project chạy đầy đủ bên dưới.

Bên trong một `<constructor-arg>` cũng có thể sử dụng các **sub-elements**:

```xml
<bean id="gateway" class="example.Gateway">
    <constructor-arg>
        <ref bean="backend"/>
    </constructor-arg>
    <constructor-arg>
        <value>production</value>
    </constructor-arg>
</bean>
```

Ở đây `<ref bean="backend"/>` tương đương về ý định với `ref="backend"`. `<value>production</value>` tương đương với chuỗi literal. Dạng nested hữu ích khi cần collection, inner bean hoặc cấu trúc giá trị dài.

**Không viết cả `ref` và `value` trên cùng `<constructor-arg>`** để đại diện cùng parameter. Mỗi argument cần một giá trị/bean reference có nghĩa rõ ràng.

## 4. Phân biệt ref và value — lỗi quan trọng nhất

Giả sử class:

```java
public final class EngineController {
    private final Engine engine;

    public EngineController(Engine engine) {
        this.engine = engine;
    }
}
```

Đây là minh họa, cần kiểu `Engine`. Cấu hình đúng:

```xml
<bean id="engine" class="example.Engine"/>
<bean id="controller" class="example.EngineController">
    <constructor-arg ref="engine"/>
</bean>
```

Container tìm bean **mang tên `engine`**, rồi truyền object vào constructor. Nếu thay:

```xml
<constructor-arg value="engine"/>
```

Spring nhận **chuỗi `"engine"`**, không phải bean. Nó sẽ thử convert chuỗi sang type parameter. Với kiểu `Engine` không có converter phù hợp, bean creation thất bại; trong vài dự án custom converters, chuỗi có thể được convert với nghĩa khác ngoài dự định.

Ngược lại, khi constructor cần `String endpoint`, XML nên dùng `value="https://example.test"`. Đừng dùng `ref="https://example.test"` nếu không hề có bean name ấy.

### Dạng nested <ref>

```xml
<constructor-arg>
    <ref bean="engine"/>
</constructor-arg>
```

Tương đương về hợp đồng reference với `<constructor-arg ref="engine"/>`. Bean được tham chiếu có thể được định nghĩa ở file XML khác nhưng **cùng ApplicationContext**, hoặc trong parent context theo semantics của `<ref>`.

### Dạng nested <idref> khác <ref>

```xml
<constructor-arg>
    <idref bean="engine"/>
</constructor-arg>
```

`<idref>` truyền **bean name dạng String** đã được kiểm tra tồn tại, chứ không truyền object bean. Phù hợp cho một component thực sự cần **tên của bean** như một giá trị cấu hình. Nếu constructor cần `Engine` object, dùng `<ref>`.

### Dạng <null/>

```xml
<constructor-arg>
    <null/>
</constructor-arg>
```

`<null/>` truyền Java `null`. Chỉ dùng khi API cho phép null rõ ràng. `null` làm overload resolution phức tạp hơn nếu có nhiều constructors với reference types tương thích; hãy dùng `index`/`type` hoặc thiết kế constructor rõ ràng. Không dùng null để che dependency bắt buộc bị thiếu.

## 5. Matching tham số khi không có index/type/name

Nếu constructor có các tham số **khác type và không nhập nhằng**, có thể dùng thứ tự khai báo:

```java
// Minh họa:
public ShipmentPlanner(RouteRepository routeRepository,
                       RateProvider rateProvider) {
}
```

```xml
<bean id="planner" class="example.ShipmentPlanner">
    <constructor-arg ref="routeRepository"/>
    <constructor-arg ref="rateProvider"/>
</bean>
```

Spring có thể dùng type metadata của references để chọn tham số phù hợp. Trong tình huống không nhập nhằng, danh sách arguments theo thứ tự được truyền tới constructor tương ứng.

Vấn đề xuất hiện khi **nhiều tham số cùng type** hoặc khi các giá trị đều là text từ XML. Xem class sau:

```java
public final class ReportOptions {
    private final int maxRows;
    private final String title;

    public ReportOptions(int maxRows, String title) {
        this.maxRows = maxRows;
        this.title = title;
    }
}
```

Hai XML values:

```xml
<bean id="options" class="example.ReportOptions">
    <constructor-arg value="100"/>
    <constructor-arg value="Daily"/>
</bean>
```

có thể được Spring chuyển kiểu dựa trên constructor không nhập nhằng. Nhưng khi có overload `ReportOptions(String, int)` hoặc hai arguments đều là chuỗi số, quyết định có thể khó hiểu, phụ thuộc conversion và candidate matching. **Đừng dựa vào heuristics nếu thiết kế đã có ambiguity**; chỉ rõ `index`, `type` hoặc `name`.

## 6. index: ánh xạ trực tiếp theo vị trí bắt đầu từ 0

`index` giúp Spring biết argument tương ứng với parameter vị trí nào:

```java
public ReportOptions(int maxRows, String title) {
    // ...
}
```

```xml
<bean id="options" class="example.ReportOptions">
    <constructor-arg index="1" value="Daily"/>
    <constructor-arg index="0" value="100"/>
</bean>
```

Dù hai thẻ xuất hiện theo thứ tự 1 rồi 0, Spring map vào:

```text
constructor:
   [0] int maxRows       <- "100" -> 100
   [1] String title      <- "Daily"
```

Các quy tắc:

- Index **0-based**, giống array indices trong Java.
- `index="0"` là parameter đầu tiên, `index="1"` là thứ hai.
- Hai `<constructor-arg index="0">` cùng định nghĩa một vị trí là lỗi metadata cấu hình hoặc sinh conflict; không dùng để ghi đè có chủ đích.
- Index nằm ngoài constructor signature phù hợp sẽ gây lỗi resolution/bean creation.
- Index không phụ thuộc việc compiler còn giữ parameter names, nên bền khi chưa bật `-parameters`.
- Index vẫn là **hợp đồng theo vị trí**. Nếu developer đổi thứ tự parameters cùng type trong Java, XML có thể không phát hiện lỗi compile nhưng hành vi thay đổi.

### Khi nào nên dùng index?

- Hai arguments có cùng kiểu `String`.
- Constructor nhận `String id, String region`, nơi thứ tự quan trọng.
- Không thể giữ metadata tên parameter ở runtime.
- Dùng c-namespace positional syntax `c:_0` hoặc `c:_0-ref`.
- Cần chọn rõ arg của overloaded constructors.

Ví dụ thứ tự thẻ không quan trọng khi `index` explicit, nhưng để dễ review, thường viết theo thứ tự 0→1→2.

## 7. type: giúp phân giải kiểu tham số

`type` mô tả kiểu parameter muốn Spring match, đặc biệt hữu ích khi literal values không tự tiết lộ type.

```java
public final class CalculationSettings {
    public CalculationSettings(int attempts, String label) {
        // ...
    }
}
```

```xml
<bean id="calculationSettings" class="example.CalculationSettings">
    <constructor-arg type="int" value="3"/>
    <constructor-arg type="java.lang.String" value="gold"/>
</bean>
```

Các tên primitive Java như `int`, `long`, `boolean` được dùng trực tiếp; các reference types có thể dùng fully qualified name như `java.lang.String`. Spring chuyển `value="3"` sang `int` khi chọn/invoke constructor.

### type không giống “kiểu object mới”

`type="java.lang.String"` **không khai báo một bean String độc lập**. Nó chỉ cung cấp gợi ý về signature parameter của constructor cần gọi. Object bean được tạo vẫn thuộc class nêu trong `<bean class="...">`.

### Kiểu số dễ gây nhầm

Giả sử class overload:

```java
// Minh họa, không đặt cùng ví dụ runnable.
public Limits(int max, long delayMillis) {
}
public Limits(long max, int delayMillis) {
}
```

XML:

```xml
<constructor-arg value="5"/>
<constructor-arg value="1000"/>
```

chứa hai chuỗi số, nên không biểu diễn rõ overload nào được mong đợi. Sử dụng `index` kết hợp `type` (hoặc tốt hơn là thiết kế một constructor không nhập nhằng) để Spring có thêm thông tin.

### type và ref kết hợp

Bạn có thể chỉ rõ type bên cạnh `ref` nếu nó giúp làm rõ overload. Dù vậy, với bean reference mà Spring đã biết kiểu bean, thường **không cần** lặp type. Metadata thừa làm XML dài và dễ sai sau refactor interface/implementation.

## 8. name: ánh xạ theo tên constructor parameter

Thay vì vị trí, XML có thể viết:

```java
public final class RetrySettings {
    public RetrySettings(int maxAttempts, long delayMillis) {
    }
}
```

```xml
<bean id="retrySettings" class="example.RetrySettings">
    <constructor-arg name="delayMillis" value="1500"/>
    <constructor-arg name="maxAttempts" value="3"/>
</bean>
```

Tên `delayMillis`/`maxAttempts` phải khớp metadata parameter names mà Spring nhìn thấy. Điểm khác với `index`: source XML tự thể hiện **ý nghĩa** của argument và không lệ thuộc thứ tự viết thẻ. Nhưng **Java parameter names không được bảo toàn ở mọi bản build theo mặc định**.

### 8.1. Biên dịch với -parameters

Từ Java 8 trở đi, `javac -parameters` giữ tên parameters trong bytecode để reflection/Spring có thể đọc. Với Java 17:

```bash
javac -parameters RetrySettings.java
```

Nếu dùng Maven, khai báo trong `maven-compiler-plugin`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.13.0</version>
    <configuration>
        <release>17</release>
        <parameters>true</parameters>
    </configuration>
</plugin>
```

Ví dụ runnable ở mục 13 dùng cấu hình Maven này; nhờ đó `<constructor-arg name="maxAttempts">` hoạt động theo metadata rõ ràng.

### 8.2. @ConstructorProperties khi không dùng -parameters

Spring cũng nhận tên constructor arguments được khai báo bằng **`@ConstructorProperties`** từ JDK:

```java
import java.beans.ConstructorProperties;

public final class RetrySettings {
    private final int maxAttempts;
    private final long delayMillis;

    @ConstructorProperties({"maxAttempts", "delayMillis"})
    public RetrySettings(int maxAttempts, long delayMillis) {
        this.maxAttempts = maxAttempts;
        this.delayMillis = delayMillis;
    }
}
```

Annotation phải phản ánh **đúng thứ tự constructor parameters**. Nó khác `@Autowired`: không yêu cầu Spring code trong domain class, vì `@ConstructorProperties` thuộc `java.beans`.

### 8.3. Nếu metadata names không có?

XML `name="maxAttempts"` có thể không match constructor đúng như mong đợi và bean creation thất bại. Giải pháp là:

- Bật `-parameters` (khuyến nghị trong build Java hiện đại khi sử dụng parameter-name metadata).
- Dùng `@ConstructorProperties` nếu cần giữ contract rõ trong mã.
- Hoặc dùng `index` thay cho `name` nếu không quản lý được compiler settings.

Không sửa lỗi bằng cách đổi `name` bừa cho tới khi khởi động được; cần biết tên runtime thật sự và các overload liên quan.

### 8.4. Khác nhau giữa XML name và bean id

Trong:

```xml
<bean id="retrySettings" class="example.RetrySettings">
    <constructor-arg name="maxAttempts" value="3"/>
</bean>
```

`retrySettings` là **tên bean trong container**; `maxAttempts` là **tên parameter bên trong Java constructor**. Một bên phục vụ lookup bean, bên kia phục vụ argument matching. Không hoán đổi.

## 9. index, type và name nên chọn cách nào?

| Tình huống | Lựa chọn hữu ích | Giới hạn |
| --- | --- | --- |
| Các dependencies khác type, ít nguy cơ nhầm | `<constructor-arg ref="..."/>` theo thứ tự | Phải giữ đủ arguments |
| Hai `String` có ngữ nghĩa khác nhau | `index` hoặc `name` | Index phụ thuộc vị trí; name phụ thuộc metadata |
| Literal số cho overloaded constructors | `type`, kết hợp `index` khi cần | Metadata dài hơn; vẫn phải có overload hợp lệ |
| Muốn XML tự mô tả semantic của arguments | `name` | Cần `-parameters`/`@ConstructorProperties` |
| Không quản lý được compiler flags | `index` | Refactor thứ tự Java constructor dễ gây regression |
| Một argument là Spring bean khác | `ref` | Bean referenced phải tồn tại và type tương thích |
| Một argument là literal | `value` | Spring cần convert; định dạng phải hợp lệ |
| Một argument là list/map | Nested `<list>`/`<map>` | Generic conversion/ordering cần kiểm chứng |
| Giá trị intentionally null | Nested `<null/>` | Dễ nhập nhằng với overloaded reference parameters |

**Thực hành tốt:** Với những constructor phức tạp, lựa chọn tối ưu thường là **giảm overload ambiguity ở phía Java**, rồi dùng XML đủ tường minh cho người đọc. Không nên biến XML thành mê cung index/type để bù cho một API constructor khó hiểu.

## 10. Constructor overloads và thuật toán lựa chọn

Trong Java, một class có thể có nhiều constructors:

```java
// Minh họa API có overload.
public final class EndpointClient {
    public EndpointClient(String url) { /* ... */ }
    public EndpointClient(String url, int timeoutMillis) { /* ... */ }
    public EndpointClient(URI uri, int timeoutMillis) { /* ... */ }
}
```

Ở đây cần import `java.net.URI`. Với XML:

```xml
<bean id="endpointClient" class="example.EndpointClient">
    <constructor-arg value="https://example.test"/>
    <constructor-arg value="1000"/>
</bean>
```

Spring sẽ xem xét số lượng arguments, types, conversion và metadata available để tìm constructor phù hợp. Bạn không nên giả định rằng “constructor khai báo trước” hoặc “constructor dài nhất” luôn được chọn bất kể metadata.

### Làm rõ overload bằng index/type

```xml
<bean id="endpointClient" class="example.EndpointClient">
    <constructor-arg index="0"
                     type="java.lang.String"
                     value="https://example.test"/>
    <constructor-arg index="1"
                     type="int"
                     value="1000"/>
</bean>
```

Nhờ đó ý định chọn signature `EndpointClient(String, int)` rõ hơn. Nếu khai báo type không khớp bất kỳ overload hợp lệ nào, Spring sẽ báo lỗi; metadata không ép JVM chạy một constructor không tồn tại.

### Thiết kế API phù hợp DI

Nếu bạn sở hữu source class, hãy cân nhắc:

- Một constructor chính với các dependencies bắt buộc.
- Group những primitive/string settings thành một **immutable settings object** nếu nhiều tham số cùng type.
- Dùng builder hoặc static factory có tên khi nhiều cấu hình object tạo ra có ý nghĩa rất khác.
- Giữ legacy overloads chỉ khi cần tương thích và test đúng signature được Spring chọn.

**Tách lựa chọn implementation khỏi cấu hình primitive:** Thay vì một class có hàng chục overloads, có thể đưa `EndpointSettings` làm một bean riêng, rồi truyền settings đó bằng `ref` vào constructor client.

## 11. Truyền nhiều bean references và kết hợp với value

Một bean có thể cần cả collaborator object và literal:

```java
// Minh họa:
public final class NotificationService {
    public NotificationService(MessageGateway gateway,
                               String sender,
                               int retries) {
    }
}
```

XML:

```xml
<bean id="gateway" class="example.DefaultMessageGateway"/>

<bean id="notificationService" class="example.NotificationService">
    <constructor-arg index="0" ref="gateway"/>
    <constructor-arg index="1" value="no-reply"/>
    <constructor-arg index="2" value="3"/>
</bean>
```

Spring giải quyết `gateway` thành bean tương thích `MessageGateway`, còn `no-reply` và `3` là literal values; converter biến `"3"` thành `int`.

### Phân biệt vòng đời và dependency identity

Một reference bằng `ref` là request lấy bean managed. Nếu bean referenced mặc định singleton, Spring thường truyền **cùng instance** tới mọi consumer phù hợp trong cùng ApplicationContext.

Nếu bạn viết:

```java
NotificationService service =
        new NotificationService(new DefaultMessageGateway(), "no-reply", 3);
```

ngoài container, bạn tự xây object khác với managed singleton. Đó là Java construction bình thường, không kiểm chứng XML DI.

### Thiếu bean reference

Nếu `ref="gateway"` không có bean/alias cùng tên được phân giải, Spring thường ném lỗi dependency resolution hoặc bean creation. Đừng đổi `ref` thành `value` chỉ để XML parse qua; điều đó đổi hẳn ý nghĩa dữ liệu.

## 12. Truyền collections, inner bean và property-like values

Không phải constructor parameters luôn là scalar hay bean đơn.

### 12.1. List<String>

Giả sử Java constructor:

```java
// Minh họa:
public AllowedRegions(java.util.List<String> regions) {
}
```

XML:

```xml
<bean id="allowedRegions" class="example.AllowedRegions">
    <constructor-arg>
        <list>
            <value>ap-southeast-1</value>
            <value>eu-west-1</value>
            <value>us-east-1</value>
        </list>
    </constructor-arg>
</bean>
```

Spring cung cấp collection khi tạo bean; generic metadata `List<String>` có thể giúp conversion của phần tử theo type. Với `List<Handler>`, thường dùng `<ref bean="..."/>` trong `<list>`.

### 12.2. Map<String, Integer>

```java
// Minh họa:
public ThrottleLimits(java.util.Map<String, Integer> perRegion) {
}
```

```xml
<bean id="throttleLimits" class="example.ThrottleLimits">
    <constructor-arg>
        <map>
            <entry key="ap-southeast-1" value="10"/>
            <entry key="eu-west-1" value="20"/>
        </map>
    </constructor-arg>
</bean>
```

Spring có thể chuyển các chuỗi `10`, `20` thành `Integer` nhờ generic type metadata của constructor. Đây là một bean `ThrottleLimits` chứa map, không có nghĩa Spring đăng ký thêm các bean có tên `ap-southeast-1`.

### 12.3. Inner bean

```xml
<bean id="filterService" class="example.FilterService">
    <constructor-arg>
        <bean class="example.DefaultFilterPolicy">
            <constructor-arg value="strict"/>
        </bean>
    </constructor-arg>
</bean>
```

`DefaultFilterPolicy` là một **inner bean definition** được tạo riêng cho bean ngoài; không cần `id` và không được lấy độc lập bằng `getBean("defaultFilterPolicy")`. Inner bean thường có vòng đời gắn với bean ngoài, không phải một singleton công khai có thể dùng ở nhiều nơi.

Khi nhiều consumers cần cùng một policy bean, tốt hơn là đăng ký **bean có tên riêng** và dùng `ref` thay vì sao chép inner bean.

### 12.4. Null và literal rỗng là khác nhau

```xml
<constructor-arg>
    <null/>
</constructor-arg>
```

truyền `null`, trong khi:

```xml
<constructor-arg value=""/>
```

truyền chuỗi rỗng `""` (hoặc giá trị convert phù hợp). Không coi hai trường hợp là tương đương. Với input bắt buộc, nên validate trong constructor thay vì dùng `null` như một giá trị im lặng.

## 13. Ví dụ chạy hoàn chỉnh: Java 17 + Spring Framework 6.2

Ví dụ không dùng Spring Boot, database hoặc external API. Chúng ta sẽ khai báo toàn bộ các bean bằng XML để thấy rõ `constructor-arg`, nhưng Java classes vẫn là POJO độc lập với Spring.

Ví dụ kiểm chứng:

- `ref` và `index`: `CatalogService` nhận đúng singleton `Catalog`.
- `value`: truyền prefix `SKU-` vào constructor.
- `name`: `RetrySettings` nhận `maxAttempts` và `delayMillis` từ XML; Maven bật `-parameters`.
- `type`: `AuditLabel` nhận `String` và `int`.
- `c:` namespace: `Reporter` nhận bean bằng `c:_0-ref` và literal bằng `c:_1`.
- Spring thực hiện conversion `String -> int/long` đúng.
- Scope singleton: tra cứu repository qua service và trực tiếp từ context cho cùng reference.

### 13.1. Cấu trúc dự án

```text
xml-constructor-args/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── demo/
        │       └── XmlConstructorArgsDemo.java
        └── resources/
            └── beans.xml
```

### 13.2. Tạo pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="
             http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>xml-constructor-args</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.release>17</maven.compiler.release>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.19</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <release>17</release>
                    <parameters>true</parameters>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
            </plugin>
        </plugins>
    </build>
</project>
```

### 13.3. Java source đầy đủ

Tạo `src/main/java/demo/XmlConstructorArgsDemo.java`:

```java
package demo;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class XmlConstructorArgsDemo {

    public interface Catalog {
        String find(String sku);
    }

    public static final class MemoryCatalog implements Catalog {
        public MemoryCatalog() {
        }

        @Override
        public String find(String sku) {
            return switch (sku) {
                case "BK-7" -> "Java book";
                case "MUG-2" -> "Coffee mug";
                default -> "Unknown item";
            };
        }
    }

    public static final class CatalogService {
        private final Catalog catalog;
        private final String prefix;

        public CatalogService(Catalog catalog, String prefix) {
            this.catalog = catalog;
            this.prefix = prefix;
        }

        public String describe(String sku) {
            return prefix + catalog.find(sku);
        }

        public Catalog catalog() {
            return catalog;
        }
    }

    public static final class RetrySettings {
        private final int maxAttempts;
        private final long delayMillis;

        public RetrySettings(int maxAttempts, long delayMillis) {
            this.maxAttempts = maxAttempts;
            this.delayMillis = delayMillis;
        }

        public String describe() {
            return "attempts=" + maxAttempts
                    + ",delayMillis=" + delayMillis;
        }
    }

    public static final class AuditLabel {
        private final String name;
        private final int level;

        public AuditLabel(String name, int level) {
            this.name = name;
            this.level = level;
        }

        public String describe() {
            return name + "#" + level;
        }
    }

    public static final class Reporter {
        private final CatalogService service;
        private final String label;

        public Reporter(CatalogService service, String label) {
            this.service = service;
            this.label = label;
        }

        public String line(String sku) {
            return label + service.describe(sku);
        }

        public CatalogService service() {
            return service;
        }
    }

    public static void main(String[] args) {
        try (var context = new ClassPathXmlApplicationContext("beans.xml")) {
            Catalog catalog = context.getBean(Catalog.class);
            CatalogService service = context.getBean(CatalogService.class);
            RetrySettings retry = context.getBean(RetrySettings.class);
            AuditLabel audit = context.getBean(AuditLabel.class);
            Reporter reporter = context.getBean(Reporter.class);

            System.out.println("service=" + service.describe("BK-7"));
            System.out.println("retry=" + retry.describe());
            System.out.println("audit=" + audit.describe());
            System.out.println("report=" + reporter.line("MUG-2"));
            System.out.println("sameCatalog=" +
                    (catalog == service.catalog()));
            System.out.println("sameService=" +
                    (service == reporter.service()));
        }
    }
}
```

Lưu ý về Java types: Tất cả classes mà XML trực tiếp instantiate đều là **`public static` nested classes** có public constructors. Nhờ vậy Spring không cần tạo một instance `XmlConstructorArgsDemo` bên ngoài. Trong XML, tên binary class của chúng dùng dấu `$`, ví dụ `demo.XmlConstructorArgsDemo$MemoryCatalog`.

### 13.4. Spring XML đầy đủ

Tạo `src/main/resources/beans.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:c="http://www.springframework.org/schema/c"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <!-- Bean với public no-arg constructor. -->
    <bean id="catalog"
          class="demo.XmlConstructorArgsDemo$MemoryCatalog"/>

    <!-- Explicit position: constructor CatalogService(Catalog, String). -->
    <bean id="catalogService"
          class="demo.XmlConstructorArgsDemo$CatalogService">
        <constructor-arg index="0" ref="catalog"/>
        <constructor-arg index="1" value="SKU-"/>
    </bean>

    <!-- Name matching: Maven compiler bật -parameters. -->
    <bean id="retrySettings"
          class="demo.XmlConstructorArgsDemo$RetrySettings">
        <constructor-arg name="delayMillis" value="1500"/>
        <constructor-arg name="maxAttempts" value="3"/>
    </bean>

    <!-- Explicit type matching cho các giá trị đơn giản. -->
    <bean id="auditLabel"
          class="demo.XmlConstructorArgsDemo$AuditLabel">
        <constructor-arg type="java.lang.String" value="AUDIT"/>
        <constructor-arg type="int" value="7"/>
    </bean>

    <!-- c-namespace positional shortcut, _0 là tham số đầu tiên. -->
    <bean id="reporter"
          class="demo.XmlConstructorArgsDemo$Reporter"
          c:_0-ref="catalogService"
          c:_1="REPORT:"/>
</beans>
```

XML khai báo năm beans chính: `catalog`, `catalogService`, `retrySettings`, `auditLabel`, `reporter`. Không có `@ComponentScan`; trong ví dụ Spring tạo chúng vì `ClassPathXmlApplicationContext` đọc `beans.xml`.

### 13.5. Chạy

Yêu cầu JDK 17+ và Maven. Từ thư mục có `pom.xml`:

```bash
mvn -q clean compile exec:java -Dexec.mainClass=demo.XmlConstructorArgsDemo
```

Output **do ứng dụng tự in**, không tính log Maven/Spring phụ thuộc môi trường:

```text
service=SKU-Java book
retry=attempts=3,delayMillis=1500
audit=AUDIT#7
report=REPORT:SKU-Coffee mug
sameCatalog=true
sameService=true
```

### 13.6. Giải thích từng dòng

**`service=SKU-Java book`:** XML `catalogService` có `index="0" ref="catalog"` nên Spring truyền managed `MemoryCatalog` vào constructor `CatalogService(Catalog, String)`. `index="1" value="SKU-"` truyền prefix literal. Method `describe("BK-7")` tạo `SKU-Java book`.

**`retry=attempts=3,delayMillis=1500`:** Thẻ `name="delayMillis"` đứng trước trong XML, nhưng Spring map đúng tên parameter nhờ `-parameters`. Chuỗi `"1500"` được chuyển thành `long`; `"3"` chuyển thành `int`. Vì thế thứ tự khai báo thẻ trong XML **không làm đổi ý nghĩa** khi `name` được xác định rõ.

**`audit=AUDIT#7`:** `type="java.lang.String"` và `type="int"` giúp Spring match signature `AuditLabel(String, int)`; literal `7` chuyển sang `int`.

**`report=REPORT:SKU-Coffee mug`:** Dạng `c:_0-ref="catalogService"` truyền bean `CatalogService` đã được Spring quản lý, còn `c:_1="REPORT:"` truyền chuỗi. Reporter dùng service để tra `MUG-2`, trả `Coffee mug`.

**`sameCatalog=true`:** `CatalogService` giữ đúng `Catalog` singleton do `context.getBean(Catalog.class)` cung cấp. Không có object repository mới được tạo bằng `new` trong consumer.

**`sameService=true`:** `Reporter` giữ đúng singleton `catalogService`, vì `c:_0-ref` biểu diễn bean reference chứ không phải chuỗi. Vì vậy hai cách lấy service (trực tiếp từ context và thông qua reporter) trả cùng reference trong một ApplicationContext.

### 13.7. Vì sao constructor có thể nhận interface?

`CatalogService` khai báo constructor nhận `Catalog` interface. XML cung cấp bean `MemoryCatalog` implement `Catalog`. Spring phân giải reference theo tên `catalog` và xác minh object tương thích với type parameter. Đó là DI dựa trên abstraction; consumer không cần biết class implementation đã được khai báo ở đâu.

### 13.8. Thử thay đổi có kiểm soát

1. Đổi `value="SKU-"` thành `value="ITEM-"`. Output `service` và `report` cùng đổi prefix; không cần biên dịch lại Java.
2. Đổi thứ tự hai thẻ `name` của `retrySettings`. Với `-parameters` đúng, output không thay.
3. Xóa `<parameters>true</parameters>` rồi clean compile. Đoạn `name` có thể không resolve như mong đợi khi metadata tên không còn; sửa bằng `index` hoặc `@ConstructorProperties`.
4. Đổi `c:_0-ref="catalogService"` thành `c:_0="catalogService"`. Bây giờ Spring nhận chuỗi, không phải managed bean; sẽ lỗi type conversion hoặc constructor resolution.
5. Đổi `index="0" ref="catalog"` thành `ref="missingCatalog"`. Spring báo lỗi vì dependency không tồn tại.
6. Thay `delayMillis` bằng chuỗi không chuyển được sang long. Context startup sẽ thất bại với conversion/bean creation exception.
7. Đổi `reporter` từ singleton sang `scope="prototype"` và lấy hai lần để quan sát instance identity, nhưng nhớ rằng service referenced vẫn là singleton nếu scope service không thay.

## 14. c-namespace: rút gọn constructor arguments

Spring hỗ trợ namespace `c:` để thay cấu trúc `<constructor-arg>` bằng XML attributes.

Dạng đầy đủ:

```xml
<bean id="reporter" class="example.Reporter">
    <constructor-arg index="0" ref="catalogService"/>
    <constructor-arg index="1" value="REPORT:"/>
</bean>
```

Dạng `c:` tương đương về ý định:

```xml
<bean id="reporter"
      class="example.Reporter"
      c:_0-ref="catalogService"
      c:_1="REPORT:"/>
```

Phải khai báo namespace trong root `<beans>`:

```xml
xmlns:c="http://www.springframework.org/schema/c"
```

`c:` là shortcut của Spring, **không cần một XSD riêng cho c-namespace**. Dù vậy, file XML vẫn cần `beans` namespace/schema hợp lệ.

### 14.1. c:name và c:name-ref

Khi parameter names có sẵn:

```java
public Reporter(CatalogService service, String label) {
}
```

có thể viết:

```xml
<bean id="reporter"
      class="example.Reporter"
      c:service-ref="catalogService"
      c:label="REPORT:"/>
```

Trong đó:

- `c:service-ref` biểu diễn **reference tới bean** cho constructor parameter tên `service`.
- `c:label` biểu diễn **literal string** cho parameter tên `label`.
- Tên `service` và `label` phải match metadata parameter names; chú ý `-parameters` hoặc cách khai báo metadata tương đương.

### 14.2. c:_0 và c:_0-ref

Khi không có parameter names, hoặc muốn mapping theo vị trí:

```xml
<bean id="reporter"
      class="example.Reporter"
      c:_0-ref="catalogService"
      c:_1="REPORT:"/>
```

Dấu gạch dưới `_` là bắt buộc trong ký hiệu theo index vì tên XML attribute không được bắt đầu bằng chữ số. Vị trí đầu tiên là `_0`, không phải `_1`.

### 14.3. So sánh khi chọn cách viết

| Đặc tính | `<constructor-arg>` | `c:` namespace |
| --- | --- | --- |
| Tên tham số | `name="service"` | `c:service-ref="..."` |
| Vị trí | `index="0"` | `c:_0-ref="..."` |
| Tham chiếu bean | `ref="bean"` | Hậu tố `-ref` |
| Literal | `value="text"` | Không có hậu tố `-ref` |
| Inner bean / list / map dài | Dùng nested XML | Không linh hoạt như nested form |
| Dễ đọc khi có nhiều config strings ngắn | Tùy nhóm | Có thể gọn hơn |
| Dễ phân biệt reference với literal | Rất rõ ràng | Cần chú ý hậu tố `-ref` |

Nếu codebase đã thống nhất với `<constructor-arg>`, đừng trộn cả hai styles trong một file chỉ để rút ngắn vài ký tự. Sự thống nhất quan trọng hơn việc mỗi bean dùng cú pháp ngắn nhất.

### 14.4. Sự khác biệt với p-namespace

`p:` áp dụng cho **properties/setters**, không phải constructor arguments. Ví dụ:

```xml
<bean id="client" class="example.Client"
      p:timeout="1000"/>
```

đòi class có property setter (hoặc property injection tương thích); nó không tự gọi constructor `Client(int timeout)`. Muốn truyền constructor parameter, dùng `<constructor-arg>` hoặc `c:`.

## 15. Constructor injection so với setter injection

Constructor-based DI:

```java
public final class PricingService {
    private final TaxPolicy taxPolicy;

    public PricingService(TaxPolicy taxPolicy) {
        this.taxPolicy = taxPolicy;
    }
}
```

```xml
<bean id="pricingService" class="example.PricingService">
    <constructor-arg ref="taxPolicy"/>
</bean>
```

Setter-based DI:

```java
public class PricingService {
    private TaxPolicy taxPolicy;

    public void setTaxPolicy(TaxPolicy taxPolicy) {
        this.taxPolicy = taxPolicy;
    }
}
```

```xml
<bean id="pricingService" class="example.PricingService">
    <property name="taxPolicy" ref="taxPolicy"/>
</bean>
```

Hai class snippets ở đây là **hai phiên bản thiết kế thay thế**, không đặt cùng tên trong cùng một Java file. Chúng không phải ví dụ runnable độc lập.

| Tiêu chí | Constructor injection | Setter/property injection |
| --- | --- | --- |
| Dependency bắt buộc | Diễn đạt rõ trong constructor | Có thể bị thiếu nếu không validate |
| Field `final` | Dễ hỗ trợ | Thường không phù hợp khi setter phải thay field sau constructor |
| Kiểm tra tính hợp lệ ngay khi tạo | Dễ | Có thể cần init callback |
| Unit test không Spring | Chỉ gọi constructor | Phải gọi setters |
| Optional configuration | Có thể dùng overload/settings | Setter đôi khi thuận tiện |
| Vòng constructor A↔B | Thường không giải quyết được | Một số vòng setter có thể được early references xử lý, nhưng nên refactor |
| XML syntax | `<constructor-arg>` | `<property>` hoặc `p:` |

Đối với dependencies không thể thiếu, Spring documentation thường khuyên constructor injection. Với những thuộc tính tùy chọn của legacy JavaBeans, setter injection có thể hợp lý. Không cần ép mọi thứ vào một kiểu DI; cần giữ invariant và dễ kiểm thử.

## 16. XML autowire="constructor" khác explicit constructor-arg

XML hỗ trợ constructor autowiring:

```xml
<bean id="processor"
      class="example.Processor"
      autowire="constructor"/>
```

Spring cố giải các constructor parameters bằng **beans có kiểu phù hợp** trong container. Nếu có đúng một bean thỏa type, nó có thể được inject tự động.

So sánh:

```xml
<bean id="processor" class="example.Processor">
    <constructor-arg ref="primaryRepository"/>
</bean>
```

Với explicit `ref`, XML công bố ngay instance nào được chọn. Còn `autowire="constructor"` yêu cầu container suy ra dependency theo type từ các candidates.

### Lợi và rủi ro của autowire constructor

Ưu điểm:

- Giảm lặp lại nhiều `<constructor-arg ref="..."/>`.
- Có thể phù hợp nếu constructors rõ và beans duy nhất theo type.

Rủi ro:

- Nếu không có bean phù hợp, khởi tạo có thể thất bại.
- Nếu có nhiều beans cùng kiểu mà không có cách chọn rõ, xảy ra ambiguity.
- Không tự autowire `String` và primitive values như một collaborator bean từ những literal config numbers; loại này nên được truyền explicit.
- Một thay đổi ở module khác thêm bean cùng interface có thể làm wiring cũ lỗi.
- Quan hệ phụ thuộc kém hiển thị trong XML, gây khó bảo trì code legacy.

**Quan trọng:** Explicit `<constructor-arg>` settings được ưu tiên hơn tự autowire cho những arguments đã khai báo. Nếu application có API phức tạp, explicit constructor args thường dễ review và debug hơn.

## 17. Constructor-arg với static factory method và instance factory method

Spring XML có thể tạo bean bằng **constructor** hoặc **factory method**. Trong trường hợp factory method, `<constructor-arg>` cũng có thể biểu diễn arguments mà Spring truyền cho factory method (dù tên phần tử vẫn là `constructor-arg`).

### 17.1. Static factory method

Java minh họa:

```java
public final class ClientFactory {
    private ClientFactory() {
    }

    public static ApiClient create(String endpoint, int timeoutMillis) {
        return new ApiClient(endpoint, timeoutMillis);
    }
}
```

XML:

```xml
<bean id="apiClient"
      class="example.ClientFactory"
      factory-method="create">
    <constructor-arg index="0" value="https://example.test"/>
    <constructor-arg index="1" value="1000"/>
</bean>
```

Ở đây Spring **không gọi `new ClientFactory(...)`**. Nó invoke static method `ClientFactory.create(String, int)`. Hai `<constructor-arg>` được dùng làm **factory method arguments**.

### 17.2. Instance factory method

Java minh họa:

```java
public final class ClientFactory {
    public ApiClient create(String region) {
        return new ApiClient(region);
    }
}
```

XML:

```xml
<bean id="clientFactory" class="example.ClientFactory"/>

<bean id="apiClient"
      factory-bean="clientFactory"
      factory-method="create">
    <constructor-arg value="ap-southeast-1"/>
</bean>
```

Bean `clientFactory` tồn tại như một managed factory object, còn `apiClient` là bean được tạo bằng instance method `create`. Những mã này là **minh họa riêng**, yêu cầu các class API tương ứng; không trộn hai phiên bản `ClientFactory` cùng tên vào một file.

**Đừng nhầm:** `factory-bean` trỏ tới bean factory object; `factory-method` là tên method; `<constructor-arg>` là argument. Đây không giống interface `FactoryBean<T>` của Spring, vốn có hợp đồng product vs factory bean riêng.

## 18. Type conversion và placeholders trong constructor-arg

XML `value="..."` bắt đầu là chuỗi. Khi tạo bean, Spring dựa vào metadata type target và conversion infrastructure để chuyển thành `int`, `long`, `boolean`, enum và các kiểu khác nếu có converter phù hợp.

```xml
<bean id="retrySettings" class="example.RetrySettings">
    <constructor-arg index="0" value="3"/>
    <constructor-arg index="1" value="1500"/>
</bean>
```

Nếu signature là `RetrySettings(int, long)`, hai literals được chuyển sang numeric types.

### Chuyển đổi thất bại

```xml
<constructor-arg index="0" value="three"/>
```

không tự chuyển thành `int 3`; nếu không có custom converter, Spring sẽ báo type mismatch/conversion/bean creation error. Không che lỗi này bằng việc biến mọi constructor parameter thành `String` rồi parse bất kỳ lúc nào trong business method.

### Property placeholders

`<constructor-arg>` có thể dùng placeholders:

```xml
<bean id="retrySettings" class="example.RetrySettings">
    <constructor-arg index="0" value="${retry.maxAttempts}"/>
    <constructor-arg index="1" value="${retry.delayMillis}"/>
</bean>
```

Bạn cần một cơ chế giải quyết placeholder tương ứng, ví dụ `<context:property-placeholder location="classpath:app.properties"/>`, `PropertySourcesPlaceholderConfigurer`, hoặc hạ tầng cấu hình của ứng dụng.

File `app.properties` minh họa:

```properties
retry.maxAttempts=3
retry.delayMillis=1500
```

Placeholder thay giá trị vào metadata; Spring sau đó mới dùng conversion để tạo tham số thực của constructor. **Không đồng nghĩa** bản thân `<constructor-arg>` sẽ tự mở/đọc file properties nếu chưa có processor.

### Khi cần kiểu đặc thù

Với `Duration`, `URI` hoặc custom value object, cần xác nhận conversion service / property editor / constructor contract. Không giả định mọi chuỗi đều tự chuyển thành mọi Java class tùy ý. Nếu cấu hình phức tạp, một **settings bean có factory method** sẽ rõ và dễ test hơn parsing rải rác.

## 19. Bean scope, lifecycle và identity

XML constructor arguments tham chiếu managed beans, nhưng behavior cụ thể còn phụ thuộc **scope của bean**.

### Singleton dependency

```xml
<bean id="repo" class="example.MemoryRepo"/>

<bean id="serviceA" class="example.ServiceA">
    <constructor-arg ref="repo"/>
</bean>

<bean id="serviceB" class="example.ServiceB">
    <constructor-arg ref="repo"/>
</bean>
```

Nếu cả hai services được tạo trong cùng một BeanFactory, `repo` mặc định là singleton nên hai constructors thường nhận **cùng repo reference**.

### Prototype dependency được inject một lần vào singleton

```xml
<bean id="worker" class="example.Worker" scope="prototype"/>

<bean id="scheduler" class="example.Scheduler">
    <constructor-arg ref="worker"/>
</bean>
```

Nếu `scheduler` là singleton, Spring phân giải prototype `worker` tại lúc tạo scheduler, rồi scheduler giữ reference đó. Việc gọi method của scheduler sau này không tự inject worker mới mỗi lần.

Muốn lấy prototype theo từng job, dùng `ObjectProvider<Worker>` hoặc factory chuyên dụng, đừng trông chờ chỉ khai báo `scope="prototype"` sẽ làm object được thay thế liên tục.

### Lifecycle của collaborator

Một `ref` tới bean có `init-method` hoặc `destroy-method` cho phép Spring quản lý lifecycle theo definition. Nếu tự viết `new MemoryRepo()` bên trong constructor của service, object phụ đó **không tự nhận** Spring lifecycle từ bean `repo`.

### Inner bean khác shared named bean

Như mục 12, inner bean gắn với outer bean và không dùng như singleton được chia sẻ trên toàn context. Nếu cần reuse hoặc quản lý lifecycle độc lập, định nghĩa bean name riêng và truyền bằng `ref`.

## 20. Phụ thuộc vòng: lỗi cần sửa ở đồ thị, không chỉ đổi XML

Constructor injection phơi bày dependency cycle sớm. Xem:

```java
public final class ServiceA {
    public ServiceA(ServiceB b) {
    }
}

public final class ServiceB {
    public ServiceB(ServiceA a) {
    }
}
```

XML:

```xml
<bean id="serviceA" class="example.ServiceA">
    <constructor-arg ref="serviceB"/>
</bean>

<bean id="serviceB" class="example.ServiceB">
    <constructor-arg ref="serviceA"/>
</bean>
```

Đồ thị:

```text
serviceA --> serviceB --> serviceA
```

BeanFactory cần B để xây A, nhưng phải có A để xây B. Đây là **constructor cycle** thường bất khả giải. Có thể thấy `BeanCurrentlyInCreationException` ở root cause, được bọc trong bean creation/dependency exceptions.

### Cách sửa đáng ưu tiên

- Tách phần trách nhiệm chung thành `SharedPolicy` hoặc `DomainRule` để hai services cùng phụ thuộc nó.
- Đưa orchestration vào một bean thứ ba đứng trên hai services, thay vì services gọi lẫn nhau.
- Nếu một phía chỉ cần thông báo sự kiện, cân nhắc application events với hợp đồng consistency/idempotency rõ.
- Nếu dependency thật sự phải lazy, xét provider/proxy **có chủ đích**, nhưng đừng dùng để che vòng kiến trúc.

### Có giúp nếu đổi sang setter injection?

Một số vòng setter/field injection có thể được Spring xử lý bằng cơ chế **early singleton references** nếu được cho phép, nhưng điều này có thể dẫn đến object chưa hoàn toàn khởi tạo, tương tác phức tạp với AOP/proxy và khó kiểm thử. Không xem nó là lựa chọn thiết kế mặc định.

### @DependsOn không giải quyết cycle

`@DependsOn` chỉ biểu diễn thứ tự khởi tạo, không thay object bắt buộc. Nếu A depends on B và B depends on A, bạn tạo thêm vòng ordering; không sửa vòng bằng cách thêm `depends-on="..."` hoặc đảo thứ tự các thẻ `<bean>` trong XML.

## 21. Dùng @ImportResource khi chuyển XML sang Java config

Một ứng dụng Java-centric có thể giữ XML chứa `constructor-arg`:

```java
@Configuration(proxyBeanMethods = false)
@ImportResource("classpath:beans.xml")
public class RootConfig {
    // Các @Bean khác có thể khai báo ở đây.
}
```

Đây là **đoạn minh họa**; cần import `org.springframework.context.annotation.Configuration` và `ImportResource`. Khi root được Spring register vào `AnnotationConfigApplicationContext`, XML definitions được nhập vào **cùng container**. Những `ref` references có thể trỏ tới beans từ Java `@Bean` nếu bean names phù hợp.

Ví dụ:

```java
@Configuration(proxyBeanMethods = false)
@ImportResource("classpath:legacy-service.xml")
class RootConfig {
    @Bean("reportRepository")
    ReportRepository reportRepository() {
        return new InMemoryReportRepository();
    }
}
```

XML:

```xml
<bean id="reportService" class="example.ReportService">
    <constructor-arg ref="reportRepository"/>
</bean>
```

Spring tìm bean `reportRepository` do Java khai báo rồi truyền vào constructor `ReportService` từ XML. Không cần tạo context thứ hai và cũng không cần viết thêm một factory method để duplicate repo.

### Khi chuyển constructor-arg sang @Bean parameters

XML:

```xml
<bean id="catalogService" class="example.CatalogService">
    <constructor-arg ref="catalog"/>
    <constructor-arg value="SKU-"/>
</bean>
```

Java config tương đương về ý định:

```java
@Bean("catalogService")
CatalogService catalogService(Catalog catalog) {
    return new CatalogService(catalog, "SKU-");
}
```

Spring inject `Catalog` vào parameter của factory method; Java code truyền literal tới constructor. Đảm bảo **không còn bean definition XML cùng tên** nếu đã chuyển qua Java để tránh trùng tên/overriding ngoài ý định.

### Giữ hợp đồng khi migration

Nếu XML có constructor index/type/name, nên kiểm tra rằng Java factory mới vẫn truyền đúng ý nghĩa arguments, đặc biệt khi có nhiều `String` hoặc enum settings. Đừng nghĩ code compile thành công là đủ: migration có thể làm đổi default values, profiles hoặc lifecycle mà compiler không nhìn thấy.

## 22. Debug lỗi constructor-arg theo triệu chứng

| Triệu chứng | Nguyên nhân khả dĩ | Cách xử lý |
| --- | --- | --- |
| `BeanCreationException` nói không tìm được constructor phù hợp | Số lượng/type arguments không match constructor | Kiểm tra signature thực, `index`/`type`/`name` và class package |
| `UnsatisfiedDependencyException` | Ref đến bean thiếu hoặc dependency chain khởi tạo lỗi | Kiểm tra bean definitions và root cause |
| `NoSuchBeanDefinitionException` | `ref="..."` trỏ tên bean không tồn tại | Sửa bean id/alias; import config đúng |
| `BeanCurrentlyInCreationException` | Constructor cycle hoặc early bean creation | Vẽ đồ thị dependencies, tách trách nhiệm |
| `BeanNotOfRequiredTypeException` | Ref tồn tại nhưng type không tương thích hoặc proxy expose interface khác | Điều chỉnh interface/bean type |
| `TypeMismatchException`/conversion error | `value` không thể chuyển sang parameter type | Sửa literal, type, converter |
| `constructor-arg name` không match | Build thiếu `-parameters` hoặc `@ConstructorProperties` không đúng | Bật metadata hoặc dùng `index` |
| Constructor overloaded bị chọn sai/ambiguous | Nhiều candidates nhận string/number literals | Dùng `type`/`index` hoặc sửa API để rõ |
| Hai Strings bị đảo nhau | Không đánh dấu index/name | Đặt `index="0"/"1"` hoặc `name` có metadata |
| Index vượt giới hạn | `index` ngoài số parameters hợp lệ | Kiểm tra constructor argument count |
| Duplicate index | Hai thẻ cùng vị trí | Loại duplicate, một value cho mỗi parameter |
| `c:_0-ref` bị thay bằng `c:_0` | Chuỗi literal thay cho bean ref | Thêm hậu tố `-ref` |
| XML c-namespace không nhận | Thiếu `xmlns:c` | Khai báo c namespace tại `<beans>` |
| Không thấy bean class | Sai fully qualified class name/JAR thiếu | Sửa dependency và classpath |
| Nested class trong XML không instantiate | Dùng dấu chấm thay `$` hoặc thiếu public/static | Dùng binary class name và constructor hợp lệ |
| Output đổi sau refactor parameter order | Dùng `index` nhưng constructor đổi thứ tự | Update XML cùng refactor hoặc dùng name metadata |
| App chạy trên IDE nhưng lỗi sau packaging | XML không nằm ở runtime resources classpath | Kiểm tra `src/main/resources` và JAR |
| Bean tạo sớm bất ngờ | Singleton eager hoặc collaborator bắt buộc | Xem lazy/scope/dependency graph |
| Missing placeholder | Property processor/source chưa khai báo | Kiểm tra Environment và configurer |
| Wrong singleton/prototype behavior | Nhầm `ref` với inner bean hoặc scope | Kiểm tra bean definitions và lifecycle |

### Quy trình debug có thứ tự

1. Xác minh file XML đã được `ApplicationContext` đọc và bean name được đăng ký.
2. Xác minh `class="..."` có tồn tại, là kiểu phù hợp và Spring có thể instantiate.
3. Đọc tất cả constructors của class; kiểm tra số arguments và overloads.
4. Đọc `<constructor-arg>` có khai báo `index`, `type`, `name` hay không; không tự giả định Spring chọn theo thứ tự thẻ trong mọi tình huống.
5. Xác minh `ref` trỏ đúng bean name và type; đừng nhầm với `value`.
6. Với `name`, xác minh Maven/Javac có `-parameters` hoặc `@ConstructorProperties`.
7. Với literal, xem conversion có thể thành công với type được yêu cầu không.
8. Kiểm tra scope/proxy và xem consumer cần singleton hay prototype.
9. Nếu có cycle, vẽ đồ thị constructor dependencies thay vì thêm `@DependsOn`/`@Lazy` vô tội vạ.
10. Test bằng ApplicationContext thật; xem root cause thay vì chỉ wrapper message.

## 23. Kiểm thử và kiểm tra an toàn khi cấu hình XML

### 23.1. Test bằng ApplicationContext

Một chương trình nhỏ có thể khởi tạo context rồi assert:

```java
// Đoạn bổ sung đặt bên trong main khi biến context còn mở.
Catalog catalog = context.getBean(Catalog.class);
CatalogService service = context.getBean(CatalogService.class);

if (catalog != service.catalog()) {
    throw new AssertionError("Expected same managed Catalog singleton");
}
if (!"SKU-Java book".equals(service.describe("BK-7"))) {
    throw new AssertionError("Wrong constructor arguments");
}
```

Đây là **đoạn mở rộng** của file `XmlConstructorArgsDemo.java`, không phải chương trình độc lập.

### 23.2. Kiểm tra XML bindings theo name

Với `RetrySettings`, thử đảo hai thẻ `name` (không thay value) rồi so sánh output. Test này chứng minh `name` mapping không phụ thuộc thứ tự khi metadata đúng.

Tương tự, với `index`, đảo thứ tự các XML tags nhưng giữ nguyên `index="0"/"1"`. Kết quả phải giống ban đầu, bởi parameter positions được khai báo tường minh.

### 23.3. Negative tests

Tạo context test riêng cho từng lỗi:

- Bean ref không tồn tại.
- Primitive conversion sai.
- Duplicate index.
- Overloaded constructors không rõ.
- Vòng A→B→A.
- XML c-namespace thiếu prefix declaration.
- Constructor name mapping thiếu metadata.
- Inner bean được tra cứu như named bean.

**Không sửa file cấu hình production để thử lỗi**. Dùng bản sao test resources hoặc embedded test configurations rồi assert context startup fail với nguyên nhân phù hợp. Thông báo exception đầy đủ có thể đổi theo Spring version; test nên kiểm tra loại lỗi/nguyên nhân quan trọng thay vì toàn bộ stack trace string.

### 23.4. Unit test không cần Spring

Nếu business class chỉ phụ thuộc interface:

```java
Catalog fake = sku -> "Fake item";
CatalogService service = new CatalogService(fake, "TEST-");

if (!"TEST-Fake item".equals(service.describe("anything"))) {
    throw new AssertionError("Unexpected service output");
}
```

Đây là đoạn trong phạm vi class demo có các kiểu `Catalog`/`CatalogService` sẵn. Nó không cần XML/BeanFactory vì unit test kiểm tra business logic; integration test mới cần xác nhận cấu hình `<constructor-arg>`.

### 23.5. Tránh mở context nhiều lần hoặc quên đóng

Trong Spring Framework thuần:

```java
try (var context = new ClassPathXmlApplicationContext("beans.xml")) {
    // Tra cứu và kiểm tra bean.
}
```

`try-with-resources` bảo đảm `close()` trên context. Nếu bean giữ file/socket/pool, Spring sẽ gọi destroy callbacks theo bean scope/lifecycle phù hợp. Một test mở nhiều contexts có thể tạo nhiều singleton sets độc lập; không nhầm “singleton” là một instance trên toàn JVM.

## 24. Những hiểu lầm phổ biến

### “ref và value đều chỉ là text nên như nhau”

Sai. Cả hai được viết thành XML attribute strings, nhưng Spring diễn giải `ref` như **bean reference** và `value` như **literal cần convert**. Chọn sai thay đổi toàn bộ dependency graph.

### “index bắt đầu từ 1 cho dễ hiểu”

Sai. `index` bắt đầu từ **0**. Với signature `Client(String, int)`, `index="0"` là `String`, `index="1"` là `int`.

### “name luôn hoạt động vì source Java có tên tham số”

Sai. Compiler không bắt buộc đưa parameter names vào runtime metadata nếu không cấu hình `-parameters`; có thể thay bằng JDK `@ConstructorProperties` hoặc dùng `index`.

### “type=int tạo một bean kiểu int”

Sai. `type` chỉ hỗ trợ constructor argument matching. `<bean class="...">` quyết định class/object bean chính.

### “Đổi thứ tự <constructor-arg> không bao giờ quan trọng”

Sai nếu bạn không khai báo index/name/type và Spring phải dựa vào thứ tự để map arguments. Khi `index` hoặc `name` rõ ràng, thứ tự trình bày các thẻ có thể ít ảnh hưởng hơn; tuy nhiên, XML cần được test sau refactor.

### “<constructor-arg> chỉ dùng cho new Class(...)”

Không hoàn toàn. Spring dùng cấu trúc argument metadata này cả cho **static/instance factory methods** khi bean được định nghĩa qua `factory-method` hoặc `factory-bean`.

### “Constructor injection làm Spring tự tạo interface”

Không. Spring cần một bean definition hoặc factory cung cấp implementation tương thích với interface. XML `<constructor-arg ref="catalog"/>` phải trỏ đúng bean có thể được instantiate.

### “Một prototype ref trong singleton sẽ thay instance mỗi request”

Sai. Constructor được gọi khi singleton được tạo; prototype dependency lúc đó được phân giải một lần cho constructor call. Muốn lấy prototype liên tục cần dynamic lookup factory/provider đúng nghĩa.

### “Có cycle thì đổi hết sang setter injection”

Không nên. Một số vòng setter có thể qua được với early references, nhưng mô hình vẫn coupling và có nguy cơ incomplete state. Constructor cycle nên được sửa bằng dependency inversion/coordinator.

### “c:_0-ref và c:_0 khác nhau chỉ ở tên attribute”

Sai về semantics. Hậu tố `-ref` chỉ **bean reference**; không có nó là **literal**. Hai cách có thể trùng kiểu XML string nhưng hoàn toàn khác kiểu giá trị Spring truyền vào constructor.

### “@ImportResource là một context khác”

Sai. Khi Java config import XML trong cùng ApplicationContext, bean definitions từ XML và `@Bean` được quản lý bởi cùng BeanFactory; không cần tạo `ClassPathXmlApplicationContext` bên trong một `@Bean`.

## 25. Bài tập có đáp án định hướng

### Bài 1 — Hai tham số cùng String

Constructor:

```java
public UserLabel(String firstName, String lastName) { }
```

Hãy viết XML không phụ thuộc thứ tự thẻ.

**Đáp án:**

```xml
<bean id="userLabel" class="example.UserLabel">
    <constructor-arg index="1" value="Nguyen"/>
    <constructor-arg index="0" value="An"/>
</bean>
```

Constructor nhận `firstName = "An"` và `lastName = "Nguyen"`. Index 0-based.

### Bài 2 — Bean reference

XML:

```xml
<bean id="formatter" class="example.Formatter"/>
<bean id="printer" class="example.Printer">
    <constructor-arg value="formatter"/>
</bean>
```

Nếu `Printer(Formatter)` cần object bean, cần sửa gì?

**Đáp án:** `<constructor-arg ref="formatter"/>`; `value` chỉ truyền chuỗi, không phải instance `Formatter`.

### Bài 3 — Type matching

Constructor `Options(int attempts, String label)`. Viết XML làm rõ target types.

**Đáp án:**

```xml
<bean id="options" class="example.Options">
    <constructor-arg type="int" value="4"/>
    <constructor-arg type="java.lang.String" value="fast"/>
</bean>
```

Spring có type metadata để match đúng signature và convert literal `4` sang int.

### Bài 4 — name và build metadata

Với `RetrySettings(int maxAttempts, long delayMillis)`, XML dùng `name`. Compiler cần gì?

**Đáp án:** Bật `javac -parameters` hoặc đặt `@ConstructorProperties({"maxAttempts","delayMillis"})` trên constructor, hoặc chuyển sang explicit `index` nếu không giữ metadata names.

### Bài 5 — c-namespace

Hãy đổi:

```xml
<bean id="reporter" class="example.Reporter">
    <constructor-arg index="0" ref="service"/>
    <constructor-arg index="1" value="DEBUG:"/>
</bean>
```

thành c-namespace.

**Đáp án:**

```xml
<bean id="reporter"
      class="example.Reporter"
      c:_0-ref="service"
      c:_1="DEBUG:"/>
```

Nhớ khai báo `xmlns:c="http://www.springframework.org/schema/c"` trên root `<beans>`.

### Bài 6 — Tên bean và tên parameter

`<bean id="retry" ...><constructor-arg name="limit" value="3"/></bean>`. Hai chuỗi `retry` và `limit` có vai trò gì?

**Đáp án:** `retry` là bean name trong Spring; `limit` là tên Java constructor parameter. Không dùng tên bean để suy ra parameter index.

### Bài 7 — Inner bean hay shared bean?

Hai services cần cùng một `FilterPolicy` singleton. Nên lặp hai inner `<bean>` definitions hay đặt một named bean?

**Đáp án:** Đặt một named bean `filterPolicy` và dùng `<constructor-arg ref="filterPolicy"/>` ở cả hai services. Inner bean gắn riêng với outer bean; không cho shared container singleton identity như named singleton.

### Bài 8 — Static factory

Một class `ClientFactory` có `public static Client build(String url)`. XML có thể truyền argument bằng `<constructor-arg>` không?

**Đáp án:** Có, khi bean được khai báo `class="...ClientFactory" factory-method="build"`; argument truyền vào **factory method**, không phải constructor của `ClientFactory`.

### Bài 9 — Prototype

Một singleton `Scheduler` nhận `Worker` prototype trong constructor. Scheduler có tự nhận một worker mới sau mỗi lời gọi `run()` không?

**Đáp án:** Không. Prototype được giải quyết khi constructor singleton chạy; Scheduler giữ reference đó. Muốn worker mới từng job, dùng dynamic lookup/factory phù hợp.

### Bài 10 — Placeholder

`<constructor-arg value="${service.timeout}"/>` có tự đọc key từ app.properties không?

**Đáp án:** Không. Cần hạ tầng placeholder/environment configuration, ví dụ property-placeholder processor hoặc cấu hình tương đương. Sau resolve, Spring có thể convert chuỗi thành type constructor parameter.

### Bài 11 — Vòng constructor

`A(B)` và `B(A)` được wiring qua `ref`. Đổi thứ tự `<bean>` có sửa được không?

**Đáp án:** Không. Đồ thị dependency có vòng; phải sửa trách nhiệm/abstraction hoặc orchestration chứ không chỉ đổi thứ tự XML.

### Bài 12 — Dự đoán output

Trong project mục 13, `service.describe("BK-7")` và `reporter.line("MUG-2")` trả gì?

**Đáp án:** `SKU-Java book` và `REPORT:SKU-Coffee mug`. Prefix `SKU-` đến từ `<constructor-arg index="1" value="SKU-"/>`, còn `REPORT:` từ `c:_1="REPORT:"`.

### Bài 13 — Đổi Java signature

Bạn thay `Reporter(CatalogService service, String label)` thành `Reporter(String label, CatalogService service)` nhưng giữ nguyên `c:_0-ref` và `c:_1`. Có an toàn không?

**Đáp án:** Không. Positional mapping giờ đảo type: `c:_0-ref` trỏ service vào parameter `String` và `c:_1` literal string vào parameter `CatalogService`. Cần sửa XML hoặc dùng name-based mapping có metadata tương ứng.

### Bài 14 — Refactor XML sang Java config

XML:

```xml
<bean id="catalogService" class="example.CatalogService">
    <constructor-arg ref="catalog"/>
    <constructor-arg value="SKU-"/>
</bean>
```

Viết Java factory tương đương về wiring.

**Đáp án:**

```java
@Bean("catalogService")
CatalogService catalogService(Catalog catalog) {
    return new CatalogService(catalog, "SKU-");
}
```

Cần đảm bảo Java config được Spring đăng ký và loại bỏ XML definition trùng tên khi chuyển.

## 26. Checklist trước khi đưa XML vào dự án

- [ ] XML `<beans>` root, namespaces/schema và resource path hợp lệ.
- [ ] Bean class tồn tại và có constructor/factory method phù hợp.
- [ ] Mọi dependency bắt buộc được biểu diễn qua `ref` hoặc explicit value phù hợp.
- [ ] `ref` không bị nhầm với literal `value`.
- [ ] `index` (nếu dùng) bắt đầu từ 0 và khớp Java parameter order.
- [ ] `type` đủ rõ khi có overload hoặc multiple primitive/string values.
- [ ] `name` có metadata Java parameter names nhờ `-parameters` hoặc `@ConstructorProperties`.
- [ ] Không có duplicate index/name hoặc arguments bị bỏ sót.
- [ ] Nhiều `String` parameters đã được làm rõ theo semantic.
- [ ] Type conversion của literal values được test trong ApplicationContext.
- [ ] `c:` namespace được khai báo đúng; biết hậu tố `-ref`.
- [ ] Collections/inner beans chỉ được dùng khi phù hợp ownership/lifecycle.
- [ ] Constructor autowiring có ứng viên duy nhất hoặc qualifier rõ.
- [ ] Không có constructor cycles trong dependency graph.
- [ ] Scope singleton/prototype phù hợp cách consumer giữ reference.
- [ ] Không tự tạo Spring context thứ hai để lấy dependency.
- [ ] Khi chuyển XML sang `@Bean`, giữ đúng bean names và dependency semantics.
- [ ] Integration test kiểm tra managed identity, args và output.
- [ ] Context được đóng để cleanup resources theo lifecycle phù hợp.

## Tổng kết

`<constructor-arg>` là công cụ trọng tâm của **constructor-based dependency injection trong Spring XML**. Nó cho phép mô tả rõ các dependencies bắt buộc của một bean mà không yêu cầu class Java tự tạo collaborator.

Ba thuộc tính phân giải quan trọng là:

- **`index`:** vị trí argument **0-based**, không cần metadata tên tham số.
- **`type`:** giúp Spring tìm signature đúng, nhất là với literals và overloaded constructors.
- **`name`:** giúp XML mô tả ý nghĩa parameter; cần `-parameters` hoặc `@ConstructorProperties` để Spring nhận diện tên đáng tin cậy.

Và hai dạng giá trị cơ bản nhất:

- **`ref`:** lấy một **managed bean reference**.
- **`value`:** truyền **literal**, chuyển kiểu theo target parameter.

Có thể dùng **`c:` namespace** làm shortcut, nested `<list>`/`<map>` khi cấu trúc phức tạp, và `<constructor-arg>` cũng áp dụng cho arguments của factory methods. Dù chọn dạng nào, hãy ưu tiên **constructor signatures rõ ràng, dependency graph không có vòng, scope/lifecycle đúng và integration tests**. XML nên làm nổi bật quan hệ phụ thuộc, không che chúng bằng overload hoặc magic conversion khó đoán.

## Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — Dependency Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html): constructor-based DI, argument type/index/name matching, `-parameters` và `@ConstructorProperties`.
- [Spring Framework 6.2 — Dependencies and Configuration in Detail](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-properties-detailed.html): `ref`, `value`, `idref`, `inner bean`, collections và c-namespace.
- [Spring Framework 6.2 — Autowiring Collaborators](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-autowire.html): `autowire="constructor"`, giới hạn và ambiguity.
- [Spring Framework 6.2 — Bean Definition Basics](https://docs.spring.io/spring-framework/reference/6.2/core/beans/basics.html): bean definitions, XML configuration và container.
- [Spring Framework 6.2 — Bean Scopes](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html): singleton, prototype và lifetime semantics.
- [Spring Framework 6.2 — Composing Java-based Configurations](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/composing-configuration-classes.html): kết hợp XML với `@ImportResource`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="xml-configuration-004">Cấp thuộc tính qua property, value và ref</a>

<details>
<summary>Xem chi tiết</summary>

# Cấp thuộc tính qua `<property>`, `value` và `ref` trong Spring XML

## 1. Vì sao XML cần phân biệt giá trị và tham chiếu bean?

Trong Spring IoC Container, XML bean definition mô tả cách tạo object, những thuộc tính cần cấu hình và các bean mà object phụ thuộc. Khi dùng JavaBeans-style setter injection, phần tử `<property>` là cách trực tiếp để thiết lập một property sau khi Spring tạo object.

Hai dạng quan trọng nhất:

```xml
<bean id="mailService" class="demo.MailService">
    <property name="senderName" value="System"/>
    <property name="repository" ref="mailRepository"/>
</bean>

<bean id="mailRepository" class="demo.InMemoryMailRepository"/>
```

Đây là **đoạn minh họa**, không chạy độc lập khi chưa có hai Java classes tương ứng. Nó diễn đạt hai hành động khác nhau:

- `value="System"`: đưa **giá trị dạng văn bản** vào property `senderName`, Spring chuyển kiểu khi đích cần kiểu khác `String`.
- `ref="mailRepository"`: tìm **bean đã đăng ký** dưới tên `mailRepository`, rồi đưa **reference đến object đó** vào property `repository`.

Khi `MailService` định nghĩa `setSenderName(String)` và `setRepository(MailRepository)`, Spring có thể gọi hai setters sau khi tạo service. Nếu `mailRepository` là singleton, service được nối tới đúng managed singleton đó, không phải một bản sao được tạo từ chuỗi `"mailRepository"`.

Mục tiêu của bài học:

1. Hiểu cú pháp `<bean>`/`<property>` và hợp đồng JavaBean property.
2. Dùng `value` cho `String`, số, boolean, enum và những kiểu có converter/editor phù hợp.
3. Dùng `ref` để nối các beans, hiểu bean name, alias, scope và khởi tạo dependency.
4. Biết dạng attribute, nested elements `<value/>`, `<ref/>`, `<idref/>`, `<null/>` và inner bean.
5. Cấp collection `<list/>`, `<set/>`, `<map/>`, `<props/>` bằng values và bean references.
6. So sánh `p:property`/`p:property-ref` với cú pháp `<property>` truyền thống.
7. Phân biệt injection lúc tạo bean, property placeholders và Spring Expression Language.
8. Chẩn đoán lỗi setter, type conversion, bean references, schema và vòng phụ thuộc.
9. Chạy ví dụ Java 17+ với Spring Framework 6.2 và XML hoàn chỉnh, xác minh kết quả và identity.

## 2. Đọc một `<property>` từ trong ra ngoài

Cấu hình:

```xml
<bean id="reportService" class="demo.ReportService">
    <property name="title" value="Monthly"/>
</bean>
```

Ý nghĩa mỗi thành phần:

| Thành phần | Diễn giải |
| --- | --- |
| `bean` | Khai báo một bean definition trong Spring XML beans namespace |
| `id="reportService"` | Tên dùng để lookup/`ref` tới bean |
| `class="demo.ReportService"` | Class Spring cần load và tạo instance theo bean definition |
| `property` | Một thuộc tính trên object mới tạo cần được thiết lập |
| `name="title"` | Tên JavaBean property: thông thường tương ứng `setTitle(...)` |
| `value="Monthly"` | Giá trị literal sẽ được chuyển sang kiểu property đích nếu cần |

Cấu hình này gần với thao tác Java:

```java
ReportService service = new ReportService();
service.setTitle("Monthly");
```

Hai đoạn trên chỉ minh họa ý tưởng. Spring thực tế còn quản lý bean definition, scopes, lifecycle callbacks, bean post-processors và proxying phù hợp. **Không** coi XML property injection là một macro đơn giản thay thế mọi cơ chế Spring bằng `new`.

### 2.1. JavaBean property không bắt buộc là public field

Ví dụ class:

```java
package demo;

public class ReportService {
    private String title;

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
```

Setter `setTitle(String)` tạo property writable tên `title`, dù field `title` là `private`. Spring thường thiết lập property thông qua JavaBeans property descriptor / setter. Chỉ có field tên `title` mà không có setter phù hợp **không đủ** cho một `<property name="title">` thông thường.

Không phải cứ field `private` là bất khả thi để inject bằng Spring nói chung: annotation field injection và một số custom binding mechanisms có thể làm khác. Nhưng ở **Spring XML `<property>` setter injection chuẩn**, điều người viết cần kiểm tra đầu tiên là **writable property**.

### 2.2. Phân biệt constructor injection và setter injection

Hai cách có thể cùng tạo quan hệ dependency nhưng khác thời điểm:

```xml
<!-- Constructor injection -->
<bean id="serviceA" class="demo.ServiceA">
    <constructor-arg ref="repository"/>
</bean>

<!-- Setter/property injection -->
<bean id="serviceB" class="demo.ServiceB">
    <property name="repository" ref="repository"/>
</bean>
```

Trong constructor injection, Spring phải resolve argument trước khi gọi constructor `ServiceA`. Với property injection, Spring tạo `ServiceB` trước, rồi điền property `repository` khi populate bean.

**Thiết kế:** dependency cốt lõi mà object không thể hợp lệ nếu thiếu thường phù hợp constructor injection; setter injection thích hợp với cấu hình phụ trợ, dependency có thể thay thế, hoặc lớp thư viện được thiết kế theo JavaBeans setters. Việc chọn cách nào còn phụ thuộc class API thực tế và bean lifecycle, không phải quy tắc “mọi setter đều xấu”.

### 2.3. Spring XML có cần `@Autowired` không?

Không. Bean definitions và property values trong XML có thể được Spring áp dụng mà class hoàn toàn không chứa Spring annotations.

```java
public class Sender {
    private String signature;
    public void setSignature(String signature) {
        this.signature = signature;
    }
}
```

Cùng:

```xml
<bean id="sender" class="demo.Sender">
    <property name="signature" value="Team"/>
</bean>
```

Nếu package/classpath phù hợp, bean được cấu hình bằng XML setter. Annotation processing infrastructure chỉ cần khi ứng dụng **cũng sử dụng** `@Autowired`, `@Value`, `@PostConstruct` và những annotations liên quan. Không cần bật component scan để đăng ký class đã được khai báo tường minh trong XML.

## 3. `value`: truyền một literal rồi chuyển kiểu

Dạng ngắn thường gặp:

```xml
<property name="displayName" value="Catalog"/>
<property name="retryLimit" value="3"/>
<property name="active" value="true"/>
```

Với các setter tương ứng:

```java
public void setDisplayName(String displayName) { ... }
public void setRetryLimit(int retryLimit) { ... }
public void setActive(boolean active) { ... }
```

Đây là các **fragments minh họa**; các Java method bodies `...` đại diện phần triển khai được bỏ qua, không phải một chương trình hoàn chỉnh. Trên bean class thực, Spring đọc `"3"` dưới dạng text và sử dụng type conversion infrastructure để đặt vào `int`, và đọc `"true"` để đặt vào `boolean`.

Không cần khai báo kiểu trong từng attribute `value` nếu Spring có thể suy ra từ writable property và converter/editor tương ứng. Tuy nhiên, một text literal **không phải Java expression** được tính tùy tiện. `value="1+2"` gán chuỗi `1+2` cho `String`; không tự biến thành số 3 chỉ vì nó trông giống phép toán.

### 3.1. Ngữ nghĩa của dấu nháy XML

Ví dụ:

```xml
<property name="name" value="System"/>
```

Dấu nháy ở đây thuộc **cú pháp XML attribute**, không phải một phần của giá trị String được gán vào setter. Setter nhận `System`, không nhận hai ký tự dấu `"` bao quanh.

### 3.2. Chuyển kiểu và lỗi conversion

Một property Java `int retryLimit` nhận `value="3"` thường hoạt động. Nhưng:

```xml
<property name="retryLimit" value="three"/>
```

có thể gây `TypeMismatchException` / `ConversionFailedException` trong chuỗi `BeanCreationException` hoặc exception tương đương khi Spring thiết lập property. **XSD không tự hiểu** rằng `retryLimit` là Java `int` của class đó; việc xác minh giá trị xảy ra ở tầng binding/bean creation.

Các kiểu thường gặp:

| Kiểu property đích | Literal mẫu | Kết quả |
| --- | --- | --- |
| `String` | `value="hello"` | Chuỗi `hello` |
| `int`/`Integer` | `value="42"` | Số nguyên 42 nếu conversion hợp lệ |
| `boolean`/`Boolean` | `value="true"` | Boolean true |
| `long`/`Long` | `value="5000"` | Số nguyên dài |
| `double`/`Double` | `value="2.5"` | Số thực theo converter/editor |
| `Duration`/`Path`/kiểu tùy biến | Chuỗi phụ thuộc conversion service | Chỉ dùng khi có hỗ trợ conversion thích hợp |
| Enum `Mode` | `value="FAST"` | Có thể chuyển sang enum constant FAST bằng cơ chế phù hợp |
| `Class<?>` | `value="java.lang.String"` | Có thể load class khi type converter/editor hỗ trợ |

**Không mặc định mọi kiểu đều tự chuyển được.** Với domain-specific types, có thể cần `ConversionService`, `Converter`, `PropertyEditor` hoặc factory method phù hợp. Hãy kiểm thử trường hợp thật thay vì suy luận chỉ từ literal.

### 3.3. Dạng nested `<value/>`

Thay vì attribute:

```xml
<property name="displayName" value="Catalog"/>
```

có thể dùng sub-element:

```xml
<property name="displayName">
    <value>Catalog</value>
</property>
```

Hai dạng đều biểu đạt literal value. Dạng nested thuận tiện khi muốn dùng XML content, cấu trúc rõ hoặc typing hints thích hợp. Ví dụ:

```xml
<property name="retryLimit">
    <value type="int">3</value>
</property>
```

`type="int"` là gợi ý/khai báo type ở phần tử `<value>` theo schema tương ứng, không phải lý do để đặt string `three` vào một setter int và mong nó tự hợp lệ.

### 3.4. Empty String khác null

Một property kiểu `String`:

```xml
<property name="note" value=""/>
```

nghĩa là truyền **chuỗi rỗng** `""`, còn:

```xml
<property name="note">
    <null/>
</property>
```

nghĩa là truyền **Java `null`**. Hai trạng thái có thể làm ứng dụng khác nhau:

```java
"".isEmpty()      // true
note == null     // true nếu note được set null
```

Các dòng Java chỉ minh họa. Một setter nhận primitive như `int` không thể nhận Java null theo cách bình thường; nullable là vấn đề của wrapper/reference type và contract của property.

### 3.5. XML escaping không phải Spring conversion

Trong XML attribute, ký tự `&` phải viết là `&amp;`; dấu `<` là `&lt;`. Ví dụ:

```xml
<property name="displayName" value="Sales &amp; Operations"/>
```

Spring nhận chuỗi `Sales & Operations` sau khi XML parser xử lý entity. Nếu viết `value="Sales & Operations"`, file có thể không well-formed; lỗi xảy ra **trước** khi setter injection được xét.

## 4. `ref`: đưa vào object của một bean khác

Ví dụ:

```xml
<bean id="repository" class="demo.InMemoryRepository"/>

<bean id="service" class="demo.CatalogService">
    <property name="repository" ref="repository"/>
</bean>
```

Với Java API:

```java
public void setRepository(Repository repository) {
    this.repository = repository;
}
```

Đây là các đoạn minh họa; cần class/interface liên quan. `ref="repository"` là **tên bean** đã được đăng ký; Spring resolve tên đó rồi inject reference đến instance phù hợp.

Không được nhầm:

```xml
<property name="repository" value="repository"/>
```

Dòng này biểu diễn literal string `"repository"`. Nó **không** tự tìm bean có tên `repository`. Nếu setter cần `Repository` thì type conversion không mặc định đổi string bean name thành managed dependency đúng như `ref`.

### 4.1. Các bean không cần cùng một XML file

`ref` lookup theo **bean registry/hierarchy**, không phụ thuộc yêu cầu hai bean definitions nằm cạnh nhau trong cùng file. Spring có thể tải nhiều XML resources vào cùng context hoặc dùng parent context; `ref` có thể trỏ bean thuộc cùng container hoặc bean có thể nhìn thấy từ parent.

Ví dụ:

```xml
<import resource="repository-context.xml"/>

<bean id="service" class="demo.CatalogService">
    <property name="repository" ref="repository"/>
</bean>
```

Đây là đoạn minh họa phải nằm trong một `<beans>` root hợp lệ. File `repository-context.xml` có thể cung cấp definition `repository`. Spring cần đọc và xử lý cả hai resources phù hợp; chỉ ghi `ref` không tự động tìm kiếm một JAR/file tùy ý.

### 4.2. Bean name và alias

```xml
<bean id="repository" class="demo.InMemoryRepository"/>
<alias name="repository" alias="catalogStore"/>

<bean id="service" class="demo.CatalogService">
    <property name="repository" ref="catalogStore"/>
</bean>
```

Với alias `catalogStore`, `ref` có thể dùng alias của bean; nó vẫn hướng tới cùng definition/managed instance, không tạo một repository mới.

### 4.3. Dạng nested `<ref bean="..."/>`

Hai cách tương đương về ý nghĩa:

```xml
<property name="repository" ref="repository"/>
```

và:

```xml
<property name="repository">
    <ref bean="repository"/>
</property>
```

Dạng thứ hai hữu ích khi muốn viết cấu hình lồng nhau nhất quán với `<list>`, `<map>` hoặc nested bean. Có thể gặp:

```xml
<property name="repository">
    <ref parent="repository"/>
</property>
```

`<ref parent="repository"/>` có ý nghĩa đặc biệt: chọn definition tên `repository` từ **parent BeanFactory/ApplicationContext**, hữu ích khi child context có một bean cùng tên che khuất bean parent. Đây là tình huống nâng cao; không dùng `parent` thay `bean` theo thói quen.

Các XML snippets trên không phải file standalone; cần root `<beans>` và bean definitions thực tế.

### 4.4. Thời điểm resolve reference

Khi Spring tạo bean `service` với một writable property `repository` nhận `ref="repository"`:

1. BeanFactory xác định definition của `service`.
2. Tạo instance `service` theo constructor/factory method phù hợp.
3. Khi populate property, BeanFactory cần một instance `repository`.
4. Nếu dependency chưa được tạo và phải eager-resolve, Spring tạo/chuẩn bị repository theo scope và lifecycle.
5. Spring gọi `setRepository(managedRepository)`.
6. Bean tiếp tục init callbacks/post-processors theo cơ chế Spring.

Vì vậy `ref` là một dependency edge trong bean graph. Một bean được gắn `lazy-init="true"` vẫn có thể được tạo khi có bean eager khác cần nó qua `ref`.

### 4.5. Scope và reference identity

Nếu `repository` là singleton mặc định, mọi consumer được inject đúng bean đó thường chia sẻ reference managed. Nếu repository là prototype, mỗi lần container resolve prototype dependency sẽ tạo instance theo scope; một singleton service đã nhận prototype qua setter **không tự nhận prototype mới ở mỗi lần gọi business method**.

Nếu dependency được AOP proxy/scoped proxy, reference mà service nhận có thể là proxy do BeanFactory cung cấp, không phải raw object trả về từ constructor. `ref` mang semantics của container, khác hẳn tự gọi `new Repository()` trong setter.

### 4.6. Missing reference khác missing setter

Với:

```xml
<property name="repository" ref="doesNotExist"/>
```

Spring có thể báo `NoSuchBeanDefinitionException` trong nguyên nhân `BeanCreationException` / `BeanDefinitionStoreException` tùy giai đoạn. Nhưng nếu bean tồn tại mà class `CatalogService` không có writable property `repository`, đó là loại lỗi **invalid property name** (ví dụ `NotWritablePropertyException`), không phải missing bean.

Cách debug đúng là đọc **root cause** và xác định bean name/property name bị lỗi.

## 5. `<idref>`: truyền bean name dưới dạng chuỗi, không phải instance

Spring có phần tử:

```xml
<property name="repositoryBeanName">
    <idref bean="repository"/>
</property>
```

Ý nghĩa: property `repositoryBeanName` nhận chuỗi `"repository"`, **không** nhận một object `Repository`. Spring có thể xác minh bean name được trỏ tới tồn tại khi xử lý dependency, giúp giảm lỗi typo so với:

```xml
<property name="repositoryBeanName" value="repository"/>
```

Trong ví dụ thứ hai, `value` chỉ cung cấp string mà không diễn đạt một `idref` để validate sự tồn tại của bean name. `idref` hữu ích cho infrastructure nhận **tên bean dưới dạng String**, chẳng hạn khi một thành phần khác sẽ tự dùng tên đó theo contract.

| Cú pháp | Setter nhận gì? | Có resolve object như dependency không? |
| --- | --- | --- |
| `value="repository"` | Chuỗi literal `"repository"` | Không |
| `ref="repository"` | Bean object/proxy dưới tên `repository` | Có |
| `<ref bean="repository"/>` | Bean object/proxy dưới tên `repository` | Có |
| `<idref bean="repository"/>` | String `"repository"`, với kiểm tra tên phù hợp | Không đưa object vào property |
| `<value>repository</value>` | Chuỗi literal `"repository"` | Không |

**Đừng viết `<idref>` chỉ để thay `ref` khi cần inject dependency object.** Tên giống nhau nhưng semantics khác nhau hoàn toàn.

## 6. Cấu hình collection bằng `value`, `ref` và nested elements

Một property không nhất thiết là scalar hoặc một bean duy nhất. Spring XML hỗ trợ `<list>`, `<set>`, `<map>`, `<props>` dưới `<property>`.

### 6.1. `<list>` chứa nhiều values

```xml
<bean id="reportService" class="demo.ReportService">
    <property name="regions">
        <list>
            <value>north</value>
            <value>south</value>
        </list>
    </property>
</bean>
```

Nếu Java setter là `setRegions(List<String>)`, Spring có thể tạo list và truyền vào bean. Thứ tự `north`, `south` có ý nghĩa vì `List` bảo tồn thứ tự phần tử.

Không nhầm với **collection injection by-type** như `@Autowired List<SomeInterface>`: ở đây collection elements được **khai báo tường minh trong XML**, không tự động thu thập mọi bean cùng kiểu chỉ vì property mang kiểu `List`.

### 6.2. `<list>` chứa references tới beans

```xml
<bean id="smsChannel" class="demo.SmsChannel"/>
<bean id="mailChannel" class="demo.MailChannel"/>

<bean id="dispatcher" class="demo.Dispatcher">
    <property name="channels">
        <list>
            <ref bean="smsChannel"/>
            <ref bean="mailChannel"/>
        </list>
    </property>
</bean>
```

Đây là XML minh họa cần các classes `SmsChannel`, `MailChannel`, `Dispatcher` và setter `setChannels(List<...>)` phù hợp. Các list entries là **bean references**, không phải string `"smsChannel"` và `"mailChannel"`.

### 6.3. `<map>`: value hoặc bean reference

```xml
<property name="regionLabels">
    <map>
        <entry key="north" value="N"/>
        <entry key="south" value="S"/>
    </map>
</property>
```

Ở đây map keys/values là những literals. Nếu cần tham chiếu một bean trong map:

```xml
<property name="handlers">
    <map>
        <entry key="main" value-ref="mainHandler"/>
    </map>
</property>
```

`value-ref` trên `<entry>` biểu diễn bean reference. Nó khác `value="mainHandler"`, vốn là string literal. Một số dạng cấu hình cho phép `key-ref` nếu map key cũng là bean object.

### 6.4. `<set>` và `<props>`

```xml
<property name="roles">
    <set>
        <value>reader</value>
        <value>writer</value>
    </set>
</property>

<property name="codes">
    <props>
        <prop key="north">N</prop>
        <prop key="south">S</prop>
    </props>
</property>
```

`<set>` diễn đạt tập phần tử; `<props>` phù hợp Java `Properties` và các giá trị chuỗi. Cả hai là **XML fragments** cần bean class có setter tương ứng.

Đối với map/set, đừng xây nghiệp vụ dựa vào iteration order nếu contract không quy định. Nếu cần thứ tự rõ, dùng `List` hoặc cấu trúc domain có thứ tự.

### 6.5. Type conversion trong generic collections

Nếu setter nhận `List<Integer>`, Spring có thể chuyển các `<value>1</value>`, `<value>2</value>` sang Integer nhờ generic type information và conversion infrastructure phù hợp.

```xml
<property name="levels">
    <list value-type="java.lang.Integer">
        <value>1</value>
        <value>2</value>
    </list>
</property>
```

`value-type` trong `<list>` giúp chỉ rõ intended element type theo Spring XML schema. Nhưng nếu class thiếu setter hoặc converter không hỗ trợ kiểu tùy biến, việc khai báo generic/`value-type` không tự sửa được mọi lỗi runtime.

### 6.6. Collection merging là một chủ đề khác

Khi dùng **parent-child bean definitions** qua `parent="..."` trong XML, Spring cho phép một số collection metadata từ child merge với parent nếu `merge="true"` được cấu hình đúng. Đây là **merging bean definition values**, không giống Java `List.addAll` chạy tùy lúc.

Đừng viết `merge="true"` tùy tiện rồi mong merge các collections của hai singleton beans khác nhau. Cần hiểu parent/child bean definition relationship và quy tắc từng collection type.

## 7. Inner bean: tạo collaborator dùng riêng trong một property

Đôi khi collaborator không cần tên độc lập trong registry:

```xml
<bean id="service" class="demo.CatalogService">
    <property name="formatter">
        <bean class="demo.SimpleFormatter">
            <property name="prefix" value="SVC: "/>
        </bean>
    </property>
</bean>
```

Spring xử lý `<bean>` lồng trong `<property>` như một **inner bean**. Đặc trưng:

- Thường không có bean name công khai để lookup độc lập từ `ApplicationContext`.
- Instance có mối quan hệ vòng đời/ownership với bean chứa theo semantics của inner bean.
- Không dùng inner bean khi nhiều consumers cần chia sẻ cùng một managed singleton named bean.
- Nếu chỉ có một consumer và collaborator hoàn toàn nội bộ, cấu hình này có thể ngắn gọn.

Một inner bean không đồng nghĩa **mọi lần gọi service method đều tạo mới formatter**. Bean construction vẫn diễn ra khi Spring xây bean chứa và populate property, không phải tự động mỗi business method call.

### 7.1. Inner bean khác `ref`

Hai kiểu nối khác nhau:

```xml
<!-- Tham chiếu bean named, có thể chia sẻ. -->
<property name="formatter" ref="sharedFormatter"/>
```

và:

```xml
<!-- Định nghĩa object riêng bên trong consumer. -->
<property name="formatter">
    <bean class="demo.SimpleFormatter"/>
</property>
```

Dạng đầu lấy managed reference của bean `sharedFormatter`. Dạng thứ hai tạo một inner bean definition, không tự tạo một top-level bean `sharedFormatter`.

### 7.2. Inner bean và scopes

Tài liệu Spring mô tả inner bean thường dùng cùng lifecycle context với bean chứa; ngay cả khi bạn chỉ định một scope khác ở inner definition, hãy hiểu các quy tắc thực tế của Spring trước khi dựa vào đó. Với dependency cần tái sử dụng, hoặc scope không tầm thường (request/session), named beans và scoped proxies có thể rõ ràng hơn.

## 8. `p` namespace: cú pháp gọn của `<property>`

Với Spring XML, bạn có thể chọn dạng rõ ràng:

```xml
<bean id="service" class="demo.CatalogService">
    <property name="label" value="Catalog"/>
    <property name="repository" ref="repository"/>
</bean>
```

Hoặc dùng `p` namespace:

```xml
<bean id="service" class="demo.CatalogService"
      p:label="Catalog"
      p:repository-ref="repository"/>
```

Cần khai báo ở root `<beans>`:

```xml
xmlns:p="http://www.springframework.org/schema/p"
```

**`p` namespace không có XSD riêng**. Nó được hỗ trợ trực tiếp bởi Spring bean XML parser; không cần tự bịa `spring-p.xsd` trong `xsi:schemaLocation`.

### 8.1. Quy tắc value và ref

| Dạng đầy đủ | Dạng `p` |
| --- | --- |
| `<property name="title" value="Hello"/>` | `p:title="Hello"` |
| `<property name="repository" ref="repository"/>` | `p:repository-ref="repository"` |
| `<property name="enabled" value="true"/>` | `p:enabled="true"` |
| `<property name="timeout" value="30"/>` | `p:timeout="30"` |

`p:repository="repository"` **không** tương đương với `p:repository-ref="repository"` nếu property cần object Repository. Hậu tố `-ref` chính là dấu hiệu yêu cầu container lookup bean.

### 8.2. Hạn chế của shorthand

`p` tiện cho scalar properties và references ngắn. Với list/map/inner beans hoặc cấu hình dài, nested `<property>` dễ đọc hơn.

Một tên JavaBean property tự kết thúc bằng `Ref` (ví dụ `accountRef`) có thể gây nhầm với quy ước hậu tố `-ref` trong p namespace, vì vậy XML dạng đầy đủ đáng tin cậy hơn khi đặt tên phức tạp. Đừng trộn quá nhiều styles trong cùng một file nếu team khó bảo trì.

## 9. Property placeholders `${...}` và SpEL `#{...}`

Đây là hai cơ chế mở rộng áp dụng cho giá trị, nhưng không nên nhầm với `ref`.

### 9.1. `value` chứa placeholder

```xml
<property name="endpointUrl" value="${client.endpoint}"/>
```

Nếu application cài đặt một `PropertySourcesPlaceholderConfigurer` hoặc cấu hình môi trường tương ứng, `${client.endpoint}` có thể được resolve từ properties sources thành chuỗi thực.

Trong môi trường Spring Framework thuần dùng XML, bạn có thể cấu hình:

```xml
<context:property-placeholder location="classpath:client.properties"/>
```

Cần khai báo `xmlns:context`, mapping XSD đúng và file `client.properties` ở classpath; context namespace handler hỗ trợ cơ chế đó. Khi placeholder không được resolve và không có fallback policy phù hợp, application có thể thất bại lúc xử lý/tạo bean.

**Không nhầm:** `value="${client.repository}"` cung cấp một giá trị chuỗi từ properties, không tự động biến nó thành bean reference, trừ khi bạn dùng thêm một cơ chế biểu thức/lookup có chủ đích.

### 9.2. SpEL bên trong value

```xml
<property name="region" value="#{'north'.toUpperCase()}"/>
```

Trong môi trường `ApplicationContext` có bean expression resolver thích hợp, Spring có thể đánh giá SpEL ở vị trí giá trị và trả `NORTH`. Đây là ví dụ minh họa cú pháp; SpEL không giống literal `value="north"`.

SpEL cũng có thể biểu diễn bean references, ví dụ `value="#{@repository}"` trong môi trường hỗ trợ expression resolver. Tuy nhiên, khi mục đích là inject một bean named đơn giản, `ref="repository"` rõ ràng hơn, có contract bean dependency trực tiếp và dễ debug.

### 9.3. Placeholder và bean reference thay đổi ý nghĩa cấu hình

| Cú pháp | Nguồn quyết định giá trị |
| --- | --- |
| `value="hello"` | Literal do XML cung cấp |
| `value="${app.label}"` | Property placeholder được resolver xử lý nếu cấu hình |
| `value="#{1 + 2}"` | SpEL expression được đánh giá nếu context hỗ trợ |
| `ref="repository"` | BeanFactory resolve bean name |
| `<idref bean="repository"/>` | String bean name có xác minh hợp lệ |

Một hệ thống có thể kết hợp placeholder với ref khi tên dependency đến từ cấu hình, nhưng lúc đó cần kiểm soát chặt chẽ tính hợp lệ và thời điểm resolve; không giả định mọi trường hợp `${...}` đều giống injection từ `@Autowired`.

## 10. Thứ tự khởi tạo, vòng đời và optional properties

### 10.1. Bean được tạo trước khi setter được gọi

Đối với setter injection thông thường:

```text
BeanDefinition được chọn
           |
           v
Constructor/factory method tạo instance
           |
           v
BeanFactory resolve property values / refs
           |
           v
Gọi các writable setters tương ứng
           |
           v
Aware callbacks, BeanPostProcessors, init callbacks
           |
           v
Managed bean sẵn sàng
```

Thứ tự chính xác của infrastructure callbacks/processor hooks có thể phức tạp hơn sơ đồ, nhưng **property population diễn ra trước các bước initialization tiêu chuẩn**. Vì vậy, nếu class cần property để hoạt động, không nên sử dụng nó trong constructor trước khi Spring gọi setter.

### 10.2. Setter không được khai báo thì sao?

Nếu XML không có `<property name="..." .../>`, Spring sẽ **không tự gán property đó** chỉ vì class có setter, trừ khi autowire/annotation/processor khác có nhiệm vụ làm vậy. Java field giữ giá trị mặc định (`null`, 0, false) hoặc giá trị do constructor/field initializer đặt.

Một dependency bắt buộc bị bỏ quên trong setter injection có thể không làm context fail ngay mà chỉ gây `NullPointerException` khi business method được gọi. Đó là lý do constructor injection thường thích hợp cho dependency không thể thiếu; với setter injection, cần validate state trong init method/`@PostConstruct` hoặc test context.

### 10.3. Constructor vs setter cycles

Một vòng phụ thuộc constructor A→B→A thường không thể resolve bằng cách tạo object thông thường. Setter-based cycles giữa singletons **có thể** được Spring xử lý trong một số cấu hình nhờ early references, nhưng không nên dựa vào đó làm thiết kế mặc định; đặc biệt khi có AOP proxies, scope khác và framework settings, vòng phụ thuộc vẫn có thể thất bại hoặc tạo object chưa hoàn tất lifecycle.

**Khuyến nghị:** refactor graph, tách responsibilities hoặc dùng dependency abstraction có chiều rõ, thay vì cố chuyển constructor injection sang setters chỉ để che vòng A↔B.

### 10.4. Property được gán một lần khi tạo bean, không phải cấu hình live

Thông thường `<property name="timeout" value="30"/>` đặt giá trị khi Spring tạo bean. Sau khi context đã refresh, sửa file XML trên ổ đĩa **không** tự động gọi lại setter hoặc thay đổi managed singleton. Nếu cần hot reload/dynamic configuration, phải thiết kế cơ chế refresh/lookup hoặc source of truth runtime riêng.

### 10.5. `@Autowired` và XML property có thể cùng tồn tại

Một class có thể được đăng ký bằng XML đồng thời chứa annotation injection, nếu context có post-processor hạ tầng cần thiết. Nhưng nếu cùng một dependency được set qua XML và annotation, thứ tự/ưu tiên giữa các injection mechanisms có thể gây bất ngờ. Hãy chọn **một nguồn cấu hình chịu trách nhiệm** cho từng property nếu có thể, và kiểm thử khi chuyển dần từ XML sang annotations.

## 11. Ví dụ Java 17+ hoàn chỉnh: literal value, ref, list/map, idref, null và p namespace

Dự án sử dụng Spring Framework 6.2.19, Gradle, Java 17. Không cần Spring Boot hoặc server. Tất cả domain classes là **plain Java classes**, không gắn `@Component` hay `@Autowired`.

Kết quả cần quan sát:

- String `prefix`, int `retryLimit`, boolean `enabled` nhận qua literal `value`.
- Object `CatalogRepository` và `DeliveryChannel` được nhận qua `ref`, cùng managed singletons.
- `tags` nhận một `List<String>`; `routes` nhận `Map<String,String>`.
- `repositoryName` nhận string của bean id bằng `<idref>`.
- `memo` nhận Java `null` qua `<null/>`.
- Bean `backupNotifier` dùng shorthand `p:repo-ref`, `p:prefix` và `p:enabled`.
- Chương trình kiểm tra cả kết quả nghiệp vụ và **reference identity** để phân biệt value/ref.

### 11.1. Cấu trúc dự án

```text
spring-xml-properties-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        ├── java/
        │   └── demo/
        │       ├── CatalogRepository.java
        │       ├── DeliveryChannel.java
        │       ├── NotificationService.java
        │       └── Application.java
        └── resources/
            └── application-context.xml
```

File `settings.gradle`:

```groovy
rootProject.name = 'spring-xml-properties-demo'
```

File `build.gradle`:

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.19'
}

application {
    mainClass = 'demo.Application'
}
```

Chạy bằng JDK 17+ và bản Gradle hỗ trợ JDK đang dùng. `spring-context` kéo theo Spring Beans/Core cần để đọc XML, resolve dependency và quản lý `ApplicationContext`.

### 11.2. `CatalogRepository.java`

`src/main/java/demo/CatalogRepository.java`:

```java
package demo;

public final class CatalogRepository {
    public String find(String id) {
        return "order-" + id;
    }
}
```

Class này không chứa annotation Spring. Có constructor no-argument ngầm định và được tạo bởi XML `<bean id="catalogRepository" .../>`.

### 11.3. `DeliveryChannel.java`

`src/main/java/demo/DeliveryChannel.java`:

```java
package demo;

public final class DeliveryChannel {
    private final String name;

    public DeliveryChannel(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }
}
```

`DeliveryChannel` được khai báo với constructor argument bằng XML; `NotificationService` nhận nó bằng **property ref**. Hai cách injection có thể kết hợp trong cùng cấu hình nếu domain API thực sự cần.

### 11.4. `NotificationService.java`

`src/main/java/demo/NotificationService.java`:

```java
package demo;

import java.util.List;
import java.util.Map;

public final class NotificationService {

    private CatalogRepository repo;
    private DeliveryChannel channel;
    private String prefix = "";
    private int retryLimit;
    private boolean enabled;
    private List<String> tags = List.of();
    private Map<String, String> routes = Map.of();
    private String repositoryName;
    private String memo;

    public void setRepo(CatalogRepository repo) {
        this.repo = repo;
    }

    public void setChannel(DeliveryChannel channel) {
        this.channel = channel;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void setRetryLimit(int retryLimit) {
        this.retryLimit = retryLimit;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public void setRoutes(Map<String, String> routes) {
        this.routes = routes;
    }

    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public CatalogRepository getRepo() {
        return repo;
    }

    public DeliveryChannel getChannel() {
        return channel;
    }

    public String getPrefix() {
        return prefix;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public List<String> getTags() {
        return tags;
    }

    public String notifyFor(String orderId) {
        if (!enabled) {
            return "disabled";
        }

        return prefix + repo.find(orderId)
                + " via " + channel.name()
                + " retries=" + retryLimit
                + " tags=" + tags
                + " north=" + routes.get("north")
                + " memo=" + (memo == null ? "none" : memo);
    }
}
```

Các setters là writable JavaBean properties. `notifyFor` cố ý không dùng Spring API: nó minh họa domain object chỉ nhận dependency đã được container cấp từ XML.

### 11.5. `application-context.xml`

`src/main/resources/application-context.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:p="http://www.springframework.org/schema/p"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="catalogRepository" class="demo.CatalogRepository"/>

    <bean id="emailChannel" class="demo.DeliveryChannel">
        <constructor-arg value="email"/>
    </bean>

    <bean id="notifier" class="demo.NotificationService">
        <property name="repo" ref="catalogRepository"/>

        <property name="channel">
            <ref bean="emailChannel"/>
        </property>

        <property name="prefix" value="INFO: "/>
        <property name="retryLimit" value="3"/>

        <property name="enabled">
            <value>true</value>
        </property>

        <property name="tags">
            <list>
                <value>ops</value>
                <value>mail</value>
            </list>
        </property>

        <property name="routes">
            <map>
                <entry key="north" value="N"/>
                <entry key="south" value="S"/>
            </map>
        </property>

        <property name="repositoryName">
            <idref bean="catalogRepository"/>
        </property>

        <property name="memo">
            <null/>
        </property>
    </bean>

    <bean id="backupNotifier"
          class="demo.NotificationService"
          p:repo-ref="catalogRepository"
          p:channel-ref="emailChannel"
          p:prefix="WARN: "
          p:retryLimit="1"
          p:enabled="false"/>
</beans>
```

**Vì sao không có XSD mapping cho `p`?** `p` là shorthand namespace do Spring core xử lý, không có file `spring-p.xsd` riêng. XML root khai `xmlns:p` nhưng `xsi:schemaLocation` chỉ cần cặp beans namespace và `spring-beans.xsd`.

**Vì sao `<idref bean="catalogRepository"/>` không nhận object repository?** Nó cấp **bean name string** vào `setRepositoryName(String)`. Ngược lại, `<property name="repo" ref="catalogRepository"/>` cấp một managed `CatalogRepository` vào `setRepo(...)`.

**Vì sao `<null/>` không giống `value=""`?** `setMemo(null)` sẽ khiến code in `memo=none`. Nếu đổi thành empty string, code in `memo=`; đó là khác biệt về dữ liệu.

### 11.6. `Application.java` và kết quả đầu ra

`src/main/java/demo/Application.java`:

```java
package demo;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public final class Application {

    public static void main(String[] args) {
        try (var context =
                new ClassPathXmlApplicationContext(
                        "application-context.xml")) {

            CatalogRepository repository =
                    context.getBean("catalogRepository",
                            CatalogRepository.class);
            DeliveryChannel channel =
                    context.getBean("emailChannel",
                            DeliveryChannel.class);
            NotificationService notifier =
                    context.getBean("notifier",
                            NotificationService.class);
            NotificationService backup =
                    context.getBean("backupNotifier",
                            NotificationService.class);

            System.out.println("message = " +
                    notifier.notifyFor("42"));
            System.out.println("sameRepo = " +
                    (repository == notifier.getRepo()));
            System.out.println("sameChannel = " +
                    (channel == notifier.getChannel()));
            System.out.println("repositoryName = " +
                    notifier.getRepositoryName());
            System.out.println("enabled = " +
                    notifier.isEnabled());
            System.out.println("backupEnabled = " +
                    backup.isEnabled());
            System.out.println("backupPrefix = [" +
                    backup.getPrefix() + "]");
            System.out.println("backupSameRepo = " +
                    (repository == backup.getRepo()));
            System.out.println("tags = " +
                    notifier.getTags());
        }
    }
}
```

Chương trình tạo Spring application context, load bean definitions từ XML trong classpath và gọi các managed beans sau khi context được refresh. Nó không tự `new NotificationService()` ở main.

### 11.7. Chạy Gradle

Tại root `spring-xml-properties-demo`:

```bash
gradle run --quiet
```

Nếu có Gradle Wrapper:

```bash
./gradlew run --quiet
```

Windows PowerShell:

```powershell
.\gradlew.bat run --quiet
```

Stdout do `System.out.println` của chương trình tạo:

```text
message = INFO: order-42 via email retries=3 tags=[ops, mail] north=N memo=none
sameRepo = true
sameChannel = true
repositoryName = catalogRepository
enabled = true
backupEnabled = false
backupPrefix = [WARN: ]
backupSameRepo = true
tags = [ops, mail]
```

Gradle hoặc Spring logging có thể in thêm dòng ngoài những câu lệnh `System.out.println`. Kết quả trên mô tả output nghiệp vụ xác định cho source + XML đã cung cấp.

### 11.8. Giải thích kết quả

**`message`:** literal `prefix="INFO: "`, `retryLimit="3"` được Spring chuyển sang int, nested `<value>true</value>` được chuyển sang boolean, bean references tới repository/channel được cấp đúng, `tags` là list theo thứ tự khai báo, `routes.get("north")` là `N`, memo do `<null/>` nên hiển thị `none`.

**`sameRepo = true`:** `notifier` giữ managed singleton `catalogRepository` do `ref` cung cấp; không giữ chuỗi bean name.

**`sameChannel = true`:** nested `<ref bean="emailChannel"/>` giống về ngữ nghĩa object reference với `ref="emailChannel"` trong property. Nó không tạo thêm channel instance.

**`repositoryName = catalogRepository`:** đây là chuỗi nhờ `<idref>`, không phải object CatalogRepository. Khi dùng bean name làm dữ liệu hạ tầng, `idref` biểu đạt mục đích rõ hơn literal string.

**`backupEnabled = false` và `backupPrefix = [WARN: ]`:** `p:enabled="false"` và `p:prefix="WARN: "` là dạng ngắn của literal properties; dấu ngoặc vuông trong stdout cho phép nhìn rõ khoảng trắng cuối prefix.

**`backupSameRepo = true`:** `p:repo-ref="catalogRepository"` cũng cấp reference managed bean, tương đương dạng `<property name="repo" ref="catalogRepository"/>`.

**`tags = [ops, mail]`:** XML list đã được gán vào property và giữ thứ tự. Không cần component scanning hoặc annotation injection trong ba domain classes.

## 12. Chẩn đoán lỗi thường gặp

### 12.1. Sai tên property

XML:

```xml
<property name="retry" value="3"/>
```

trong khi class chỉ có `setRetryLimit(int)`. Spring có thể ném `NotWritablePropertyException` (thường được bọc trong `BeanCreationException`). Cách sửa là xác định **JavaBeans property name**, không phải đoán từ tên field hoặc tự bịa một thuộc tính XML.

### 12.2. Sai kiểu literal

XML:

```xml
<property name="retryLimit" value="many"/>
```

setter yêu cầu `int`, nên conversion thất bại. Đừng đổi setter sang `String` chỉ để startup thành công nếu domain thực sự cần số; sửa cấu hình/validations và test biến thể lỗi.

### 12.3. `value` thay vì `ref`

XML:

```xml
<property name="repo" value="catalogRepository"/>
```

trong khi setter cần `CatalogRepository`. Spring nhận một literal String rồi cố convert sang kiểu đích; thông thường nó không tự lookup bean. Sửa thành `ref="catalogRepository"`.

### 12.4. Sai bean name trong `ref`

XML:

```xml
<property name="repo" ref="catalogRepositorry"/>
```

Tên bị typo, nên BeanFactory không resolve được bean. Kiểm tra `id`/`name`/`alias` của bean đã khai báo, các XML files được import, profiles active và bean definitions sau khi parse.

### 12.5. Setter sai visibility/signature

Nếu class có method `void setRepo(CatalogRepository x)` nhưng không public trong một bối cảnh JavaBeans introspection thông thường, hoặc chỉ có `getRepo()`, property có thể không writable bằng standard setter binding. Sửa contract API phù hợp, hoặc chuyển sang constructor injection nếu dependency bắt buộc.

### 12.6. Lẫn `ref` với `idref`

`idref` truyền String bean name, không truyền instance. Nếu setter là `setRepo(CatalogRepository)`, `<idref bean="catalogRepository"/>` không đúng semantic. Hãy dùng `<ref bean="catalogRepository"/>`.

### 12.7. Literal chứa placeholder nhưng chưa có resolver

`value="${app.repo}"` có thể không được xử lý nếu cấu hình placeholder resolution thiếu hoặc property không được cung cấp. Khi dùng Spring XML thuần, kiểm tra `context:property-placeholder` hoặc `PropertySourcesPlaceholderConfigurer`, đường dẫn file properties, và các property sources có hiệu lực.

### 12.8. Collection entries dùng sai value/ref

`<entry key="main" value="mainChannel"/>` tạo String. Nếu setter đòi `Map<String, Channel>`, nên dùng `value-ref="mainChannel"` để đưa object bean vào map.

### 12.9. Vòng phụ thuộc và object chưa sẵn sàng

Setter cycles đôi lúc được Spring cho phép với singleton early references nhưng có thể tạo coupling và side effects khó kiểm soát. Đừng dựa vào việc “Spring startup được” để khẳng định bean graph hợp lý; kiểm thử trạng thái sau initialization và refactor vòng khi có thể.

### 12.10. Bean có nhưng setter không chạy

Một trong các nguyên nhân: bean class được tạo qua source khác, XML resource không được load, property thuộc bean definition khác cùng tên, profile bỏ qua XML block, hoặc bean vẫn lazy chưa instantiated. Kiểm tra context đang load file nào và các bean definitions thực tế, không chỉ nhìn một XML snippet.

### 12.11. Tưởng p-namespace thiếu schema

Khi IDE gạch đỏ `p:repo-ref`, hãy kiểm tra `xmlns:p="http://www.springframework.org/schema/p"` và tên property đúng. Không thêm `spring-p.xsd`: namespace `p` được Spring xử lý như shortcut XML, không có XSD riêng.

### 12.12. XML escape bị sai

`value="A & B"` không well-formed. Sửa `value="A &amp; B"`. Đây là lỗi XML parsing, không phải lỗi setter hoặc converter.

## 13. Bảng đối chiếu nhanh: chọn cú pháp nào?

| Nhu cầu | Cú pháp XML đề nghị | Setter tương ứng |
| --- | --- | --- |
| Cấp tên hiển thị | `<property name="name" value="Demo"/>` | `setName(String)` |
| Cấp số lần retry | `<property name="retryLimit" value="3"/>` | `setRetryLimit(int)` |
| Cấp boolean | `<property name="enabled" value="true"/>` | `setEnabled(boolean)` |
| Inject repository bean | `<property name="repo" ref="repo"/>` | `setRepo(Repository)` |
| Inject theo nested ref | `<property name="repo"><ref bean="repo"/></property>` | `setRepo(Repository)` |
| Truyền bean name String | `<property name="targetName"><idref bean="repo"/></property>` | `setTargetName(String)` |
| Truyền Java null | `<property name="memo"><null/></property>` | `setMemo(String)` |
| Truyền list literals | `<property name="tags"><list>...</list></property>` | `setTags(List<String>)` |
| Truyền map literals | `<property name="routes"><map>...</map></property>` | `setRoutes(Map<...>)` |
| Dùng p value shorthand | `p:name="Demo"` | `setName(String)` |
| Dùng p ref shorthand | `p:repo-ref="repo"` | `setRepo(Repository)` |
| Truyền property external | `value="${app.name}"` | `setName(String)` sau placeholder resolution |
| Tạo inner collaborator | `<property name="formatter"><bean .../></property>` | `setFormatter(Formatter)` |

Không chọn syntax chỉ để rút ngắn số dòng. Với property nhiều yếu tố hoặc dữ liệu cấu trúc, nested `<property>` có thể giúp code review chính xác hơn p namespace.

## 14. Bài tập kèm đáp án

### Bài 1 — Một literal không phải bean reference

Có bean `<bean id="store" class="demo.Store"/>` và service setter `setStore(Store)`. Viết:

```xml
<property name="store" value="store"/>
```

có đúng không?

**Đáp án:** Không theo ý định inject bean. `value` là literal String. Cần `ref="store"` hoặc nested `<ref bean="store"/>`.

### Bài 2 — Bean reference có tạo object khác?

Hai services đều `<property name="repo" ref="repository"/>` và `repository` mặc định singleton. Có chắc mỗi service nhận hai instances Repository khác nhau không?

**Đáp án:** Không. Thông thường cả hai nhận cùng managed singleton reference trong một container, trừ khi scope/proxy/config thay đổi semantics.

### Bài 3 — Setter thuộc tính tên gì?

Class có `setTimeoutSeconds(int seconds)` và field `timeout`. Nên dùng `name="timeout"` hay `name="timeoutSeconds"`?

**Đáp án:** `timeoutSeconds` theo JavaBeans setter naming, nếu không có một writable `timeout` property riêng.

### Bài 4 — Literal boolean

Setter `setEnabled(boolean)` và XML `value="false"`. Method nhận gì?

**Đáp án:** Java boolean `false` sau conversion, không phải String literal `"false"`.

### Bài 5 — Empty string và null

So sánh `<property name="memo" value=""/>` với nested `<null/>`. Chúng có tương đương không?

**Đáp án:** Không. Một cái là empty String, cái kia là null. Logic validation/serialization có thể phân biệt.

### Bài 6 — Ref nested và attribute

Hai XML:

```xml
<property name="repo" ref="repository"/>
```

```xml
<property name="repo">
    <ref bean="repository"/>
</property>
```

khác nhau về object được inject không?

**Đáp án:** Trong bối cảnh lookup cùng bean name, chúng cùng diễn đạt một managed bean reference.

### Bài 7 — `idref` dùng khi nào?

Infrastructure class có setter `setTargetBeanName(String)` và phải chứa tên bean `worker` để dùng sau. Nên dùng `ref` hay `idref`?

**Đáp án:** `<idref bean="worker"/>` khi muốn truyền String bean name và kiểm tra sự tồn tại; không truyền object `worker` như `ref`.

### Bài 8 — Map entry reference

Một setter nhận `Map<String, EmailSender>`, bean `emailSender` đã được đăng ký. XML `<entry key="default" value="emailSender"/>` có đúng không?

**Đáp án:** Không nếu cần EmailSender object. Dùng `<entry key="default" value-ref="emailSender"/>`.

### Bài 9 — p namespace

Để `p:repo-ref="repository"` hoạt động, cần khai báo namespace gì? Có cần XSD riêng không?

**Đáp án:** `xmlns:p="http://www.springframework.org/schema/p"`, không cần `spring-p.xsd`.

### Bài 10 — No setter

Class có field private `name` nhưng không có `setName`, XML cố đặt `<property name="name" value="A"/>`. Kết quả?

**Đáp án:** Standard property binding có thể fail vì property không writable. Cần thêm setter hợp lệ hoặc dùng constructor/factory injection.

### Bài 11 — Property reference và lazy

Bean `repository` được đánh dấu `lazy-init="true"`, còn bean `service` non-lazy có `ref="repository"` bắt buộc. Repository có được bảo đảm chưa khởi tạo khi context refresh xong không?

**Đáp án:** Không. Dependency của eager service có thể khiến repository lazy phải được tạo để inject.

### Bài 12 — Thay XML sau refresh

App đã tạo singleton service và set `retryLimit=3`. Bạn sửa XML thành `retryLimit=8` lúc app đang chạy. Giá trị service có tự cập nhật không?

**Đáp án:** Không. XML property injection mặc định xảy ra trong bean creation, không phải live reload.

### Bài 13 — Vòng setter injection

Hai singleton A và B đặt `property ref` lẫn nhau. Có nên xem đây là thiết kế sạch chỉ vì một vài phiên bản/configuration của Spring giải quyết được?

**Đáp án:** Không. Nó tạo coupling và nguy cơ lifecycle/proxy; nên refactor dependency graph có chiều rõ.

### Bài 14 — Property placeholder

`<property name="url" value="${api.url}"/>` có luôn tự resolve trong mọi BeanFactory thuần không?

**Đáp án:** Không. Cần placeholder processing/environment phù hợp. Kiểm tra `PropertySourcesPlaceholderConfigurer` hoặc namespace property-placeholder.

### Bài 15 — Identity trong demo

Vì sao output `sameRepo = true` và `backupSameRepo = true`?

**Đáp án:** Cả XML `ref` truyền thống lẫn `p:repo-ref` đều dùng BeanFactory để lấy **cùng singleton** `catalogRepository`, không tạo bản copy từ tên chuỗi.

### Bài 16 — Dấu cách cuối literal

Trong demo, `p:prefix="WARN: "` có một space sau dấu hai chấm. Vì sao output hiển thị `backupPrefix = [WARN: ]`?

**Đáp án:** Getter trả String có dấu cách cuối theo literal, và cặp ngoặc vuông của println làm khoảng trắng nhìn thấy rõ. Đây là nội dung thật của property, không phải một newline tách từ XML.

## 15. Checklist trước khi merge XML property configuration

- File XML có root `<beans>`, đúng default namespace và schemaLocation không?
- `bean id`/`alias` có nhất quán với các `ref`/`idref` không?
- Mọi `<property name="...">` có **writable JavaBean property** chính xác không?
- `value` được dùng cho literal/converted scalar; `ref` cho managed object, `idref` cho bean name String?
- Các kiểu `int`, `boolean`, enum, collection element đã có conversion hợp lệ?
- Có phân biệt `value=""` với `<null/>` và đã kiểm tra nullable contract?
- Với list/map, đã sử dụng `<ref>` hoặc `value-ref` thay vì literal bean name khi cần object?
- Có đang dùng `p` shorthand và đã khai `xmlns:p`, không thêm XSD giả?
- Có tạo inner bean khi nên dùng named/shared bean hoặc ngược lại không?
- Bean references có ảnh hưởng scope/lazy/lifecycle như mong đợi không?
- Có property bắt buộc nhưng class vẫn cho phép null dẫn đến runtime exception muộn không?
- Property placeholders/SpEL có được cấu hình/resolved đúng môi trường không?
- Đã kiểm tra với `ClassPathXmlApplicationContext`, lookup beans và assertion identity/behavior?
- Mọi lỗi được phân loại đúng: XML parse, XSD, writable property, type mismatch, missing bean hay circular reference?
- Khi chuyển từ XML sang JavaConfig, đã tránh cấu hình cùng một dependency hai lần không?

## 16. Tổng kết

**`<property name="x" value="..."/>`** cấp một giá trị literal, Spring chuyển sang kiểu phù hợp với writable property khi có converter/editor. **`<property name="x" ref="someBean"/>`** cấp reference đến bean `someBean` do container quản lý. Dạng `<ref bean="..."/>` tương đương với `ref` attribute; **`<idref bean="..."/>`** lại cấp **tên bean dưới dạng String**.

Setter injection xảy ra trong quá trình tạo bean, sau construction và trước các initialization callbacks tiêu chuẩn. Bean references tuân theo scope/lifecycle/proxy semantics của BeanFactory. `<list>`, `<map>`, `<set>`, `<props>` hỗ trợ dữ liệu có cấu trúc, `<null/>` khác empty String và inner beans phục vụ collaborator chỉ dùng trong một phạm vi cấu hình. `p` namespace là shorthand dễ đọc cho một số scalar/ref properties, nhưng không có XSD riêng và không thay thế mọi nested cấu trúc.

Khi một ứng dụng cần dependency object, hãy chọn **`ref` thay vì `value`**; khi chỉ muốn lưu tên bean để dùng như dữ liệu cấu hình, cân nhắc **`idref`**. Tách rõ hai ý nghĩa này giúp tránh phần lớn lỗi Spring XML setter injection.

### Tài liệu chính thức

- [Spring Framework 6.2 — Dependencies and Configuration in Detail](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-properties-detailed.html).
- [Spring Framework 6.2 — Dependency Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html).
- [Spring Framework 6.2 — Bean Overview](https://docs.spring.io/spring-framework/reference/6.2/core/beans/definition.html).
- [Spring Framework 6.2 — Bean Scopes](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html).
- [Spring Framework 6.2 — Bean Lifecycle Callbacks](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-nature.html).
- [Spring Framework 6.2 — PropertySourcesPlaceholderConfigurer Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/support/PropertySourcesPlaceholderConfigurer.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="xml-configuration-005">Giá trị null, tập hợp lồng nhau và bean nội bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Giá trị null, tập hợp lồng nhau và bean nội bộ trong Spring XML

## 1. Bối cảnh và mục tiêu

Spring Core Container cho phép khai báo không chỉ những bean đơn giản dạng `<bean id="..." class="..."/>` mà cả **giá trị null**, **các cấu trúc collection lồng nhau** và **bean được định nghĩa trực tiếp bên trong một property/constructor argument**. Đây là những kỹ thuật hữu ích khi tích hợp thư viện Java cũ, cấu hình các rule sets hoặc nối object graph bằng XML mà không cần thêm Java configuration class.

Ví dụ:

```xml
<bean id="notificationPlan" class="demo.NotificationPlan">
    <constructor-arg index="0"><null/></constructor-arg>
    <constructor-arg index="1">
        <map>
            <entry key="weekday">
                <list>
                    <value>email</value>
                    <value>sms</value>
                </list>
            </entry>
        </map>
    </constructor-arg>
    <constructor-arg index="2">
        <bean class="demo.Contact">
            <constructor-arg value="ops@example.test"/>
        </bean>
    </constructor-arg>
</bean>
```

Đây là snippet **minh họa cấu trúc**, với `NotificationPlan` và `Contact` là hai Java types cần định nghĩa có constructors phù hợp. Một phiên bản hoàn chỉnh, biên dịch/chạy được với Java 17 và Spring Framework 6.2, sẽ xuất hiện ở phần thực hành.

### Mục tiêu học tập

Sau bài này, bạn có thể:

1. Phân biệt `<null/>` với chuỗi rỗng, chuỗi chữ `"null"`, property không được cấu hình và XML `xsi:nil`.
2. Gán `null` qua `<constructor-arg>`, `<property>`, bên trong `<list>`, `<map>` và những collections phù hợp.
3. Cấu hình `<list>`, `<set>`, `<map>`, `<props>` cùng các phần tử lồng nhau; hiểu `<value>`, `<ref>`, `<bean>`, `<null>`.
4. Giải thích cách Spring dùng generic types như `Map<String, List<String>>` để hỗ trợ chuyển đổi dữ liệu khi inject.
5. Hiểu inner bean: bean định nghĩa ngay bên trong một bean khác, **anonymous**, **không lookup độc lập bằng ID**, được tạo cùng enclosing bean.
6. Phân biệt inner bean với top-level bean + `ref`; hiểu tương tác với scope và lifecycle.
7. Thực hành một XML config hoàn chỉnh với `null`, map chứa list, list chứa list và inner bean.
8. Chẩn đoán lỗi XML liên quan structure/schema, nullability, type conversion, thiếu dependencies và collection merging.

## 2. Ba tầng cần phân biệt

Khi Spring đọc XML, dữ liệu di chuyển qua ba tầng:

```text
XML resource
    |
    +--> <bean>          : bean definition
    +--> <constructor-arg> / <property> : injection point
    +--> <null/> / <list/> / <map/> ... : value metadata
    |
    v
BeanDefinition / PropertyValue / ConstructorArgumentValues
    |
    v
BeanFactory resolve dependencies + convert types
    |
    v
Java object và các trường dữ liệu của nó
```

Ở pha parsing, một phần tử `<list>` không nhất thiết đã là `ArrayList` thực tế; nó có thể được biểu diễn bằng metadata dạng managed collection. Khi bean được tạo, Spring giải quyết các references, inner bean definitions, nested values và chuyển đổi phù hợp với kiểu của constructor parameter/setter.

**Điểm quan trọng:** metadata XML khai báo *giá trị đầu vào của object graph*, chứ không tự thay đổi hợp đồng Java. Nếu constructor Java cấm `null`, XML `<null/>` không làm điều kiện đó hợp lệ.

## 3. <null/>: null Java thật sự

Để truyền giá trị `null` vào constructor:

```xml
<bean id="userProfile" class="com.example.UserProfile">
    <constructor-arg index="0">
        <null/>
    </constructor-arg>
</bean>
```

Giả sử class có constructor `UserProfile(String nickname)`, Spring đưa Java reference `null` vào argument thứ nhất.

### 3.1. Bốn trạng thái rất khác nhau

| XML hoặc tình huống | Ý nghĩa |
| --- | --- |
| `<constructor-arg><null/></constructor-arg>` | Explicit Java `null`. |
| `<constructor-arg value=""/>` | Chuỗi rỗng `""`, không phải null. |
| `<constructor-arg value="null"/>` | Chuỗi có bốn ký tự `n`, `u`, `l`, `l`, trừ khi có conversion/custom logic khác. |
| Không khai báo constructor arg | Không cung cấp value tại vị trí đó; Spring phải chọn constructor và resolve những tham số còn lại theo metadata/hạ tầng. Không đồng nghĩa tự điền null. |

Đối với setter/property injection, “không khai báo `<property>`” có thể giữ nguyên giá trị default do constructor/field initialization thiết lập; “khai báo `<property><null/></property>`” chủ động yêu cầu ghi `null` vào property.

### 3.2. Property setter nhận null

```java
package com.example;

public class MailSettings {
    private String replyTo = "noreply@example.test";

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    public String getReplyTo() {
        return replyTo;
    }
}
```

Nếu XML không cấu hình `replyTo`, field giữ giá trị mặc định. Nếu dùng:

```xml
<bean id="mailSettings" class="com.example.MailSettings">
    <property name="replyTo"><null/></property>
</bean>
```

Spring gọi setter với `null`. Điều này **khác** với:

```xml
<property name="replyTo" value=""/>
```

vì đoạn sau yêu cầu chuỗi rỗng.

### 3.3. Kiểu primitive không chấp nhận null

```java
public final class RetrySettings {
    public RetrySettings(int attempts) {
    }
}
```

Với constructor `int attempts`, XML `<null/>` không thể tạo một giá trị `int` hợp lệ. Spring có thể ném lỗi tạo bean / mismatch trong lúc resolve constructor arguments. Nếu biểu đạt “không có giá trị” là yêu cầu hợp lệ, cân nhắc `Integer` hoặc một model rõ nghĩa như `Optional` ở lớp Java, rồi cấu hình theo hợp đồng cụ thể.

**Lưu ý về `Optional`:** không coi `<null/>` tự động tương đương `Optional.empty()` cho mọi constructor. Việc chuyển đổi, resolution và cách viết factory/constructor cần được thiết kế rõ ràng.

### 3.4. null và validation

Annotation như `@NotNull` thuộc validation stack khi có provider và cơ chế validation phù hợp; nó không tự ngăn XML parser đọc `<null/>`. Constructor tự kiểm tra `Objects.requireNonNull(...)` sẽ ném exception khi Spring tạo bean, trừ khi có conversion/cấu hình khác.

Vì vậy, trước khi cố đưa `null` vào object, hãy kiểm tra:

- Java parameter/field có nullable về mặt hợp đồng không?
- Có default được thiết lập trong constructor không?
- Có logic fallback nếu null không?
- Có validation/lifecycle callback kiểm tra non-null không?
- Error nên phát hiện ngay lúc startup hay runtime?

## 4. null trong collections

Spring collection elements có thể chứa `<null/>`:

```xml
<property name="fallbacks">
    <list>
        <value>primary</value>
        <null/>
        <value>secondary</value>
    </list>
</property>
```

Ví dụ này đặt trong `<bean>` có property `fallbacks` tương ứng. Nếu Java nhận `List<String>`, phần tử giữa thường là Java `null`. Một số Java APIs không hỗ trợ null trong collection, do đó việc XML chấp nhận cú pháp không có nghĩa consumer chấp nhận semantics.

Một map value null có thể khai báo:

```xml
<property name="thresholds">
    <map>
        <entry key="low" value="10"/>
        <entry key="unknown">
            <null/>
        </entry>
    </map>
</property>
```

Ví dụ yêu cầu setter/constructor nhận một map với value type nullable thích hợp, chẳng hạn `Map<String, Integer>`. `unknown` tồn tại như **một map key với value null**, khác việc map không có key đó. Khi xử lý, dùng `containsKey("unknown")` để phân biệt hai trường hợp.

### null trong XML không phải xsi:nil mặc định

Không nên lấy cú pháp của JAXB/XML Schema nillable elements rồi áp máy móc vào Spring Bean XML. Với Spring `beans` namespace, cách rõ ràng để cung cấp null tại value position là `<null/>`.

## 5. Các phần tử collection cơ bản

Spring XML có thể gán values cho Java collections qua:

| Phần tử | Java contract thường gặp | Đặc điểm |
| --- | --- | --- |
| `<list>` | `List<T>` | Có thứ tự; cho phép trùng phần tử. |
| `<set>` | `Set<T>` | Không chứa phần tử trùng theo equality semantics; không nên dựa vào thứ tự nếu không có hợp đồng riêng. |
| `<map>` | `Map<K,V>` | Các cặp key-value; key duy nhất. |
| `<props>` | `java.util.Properties` | Các property keys/values chủ yếu dưới dạng String. |
| `<array>` | Mảng Java | Hữu ích khi API yêu cầu array; cũng có thể dùng `<list>` và để Spring chuyển đổi khi tương thích. |

**Chú ý:** các kiểu collection có thể được Spring tạo bằng internal managed metadata trong khi parse và được chuyển thành implementation thích hợp cho injection; đừng viết logic phụ thuộc concrete implementation như `LinkedHashMap` chỉ vì quan sát thấy như vậy trong một lần chạy.

### 5.1. List

```xml
<property name="servers">
    <list>
        <value>primary.example.test</value>
        <value>backup.example.test</value>
        <value>backup.example.test</value>
    </list>
</property>
```

Java property dạng `List<String>` nhận các phần tử theo thứ tự; hai chuỗi trùng nhau vẫn có thể tồn tại.

### 5.2. Set

```xml
<property name="regions">
    <set>
        <value>ap-southeast-1</value>
        <value>eu-west-1</value>
    </set>
</property>
```

Nếu Java property là `Set<String>`, nghiệp vụ không nên dựa vào vị trí `get(0)` (vì Set không có index). Khi in log/test, sort nếu cần output ổn định.

### 5.3. Map

```xml
<property name="timeouts">
    <map>
        <entry key="connect" value="500"/>
        <entry key="read" value="2000"/>
    </map>
</property>
```

Nếu setter nhận `Map<String,Integer>`, conversion service/type conversion infrastructure có thể chuyển các chuỗi `500`, `2000` thành `Integer`. Nếu loại numeric sai, Spring báo lỗi khi tạo/inject bean.

### 5.4. Properties

```xml
<property name="mailSettings">
    <props>
        <prop key="mail.host">smtp.example.test</prop>
        <prop key="mail.protocol">smtp</prop>
    </props>
</property>
```

`<props>` gắn với `java.util.Properties`. Khi cần dữ liệu có nested objects hoặc non-string values, dùng `<map>` thay vì ép mọi thứ thành Properties.

### 5.5. Các phần tử có thể nằm trong collection

Bên trong list/set/map value, Spring XML hỗ trợ các cấu trúc như:

- `<value>`: giá trị literal; conversion khi inject theo target type.
- `<ref bean="..."/>`: tham chiếu một top-level bean/alias đã đăng ký.
- `<bean class="...">`: tạo **inner bean** tại vị trí phần tử.
- `<null/>`: Java null.
- `<list>`, `<set>`, `<map>`, `<props>`: nested collection.
- `<idref bean="..."/>`: một **String bean name** được kiểm tra tồn tại, không phải object reference.

Cần phân biệt `<idref/>` với `<ref/>`: cái trước tạo string identifier, cái sau resolve object bean.

## 6. Tập hợp lồng nhau: List trong Map, Map trong List

Một ứng dụng định tuyến thông báo có thể yêu cầu:

```java
Map<String, List<String>> channelsByDay;
```

XML:

```xml
<property name="channelsByDay">
    <map>
        <entry key="weekday">
            <list>
                <value>email</value>
                <value>sms</value>
            </list>
        </entry>
        <entry key="weekend">
            <list>
                <value>push</value>
            </list>
        </entry>
    </map>
</property>
```

Ở đây, hai `<list>` nằm trong hai `<entry>` của `<map>`. Đây là **collection of collections**, không phải hai bean definitions. Spring tạo/resolve nested values rồi chuyển sang target type `Map<String,List<String>>`.

### 6.1. List chứa List

```xml
<property name="stages">
    <list>
        <list>
            <value>validate</value>
            <value>build</value>
        </list>
        <list>
            <value>deploy</value>
        </list>
    </list>
</property>
```

Java target `List<List<String>>` có hai phần tử: list thứ nhất chứa `validate` và `build`; list thứ hai chứa `deploy`. Phân biệt với một list phẳng có ba strings.

### 6.2. Map trong Map

```xml
<property name="routes">
    <map>
        <entry key="internal">
            <map>
                <entry key="primary" value="host-internal"/>
            </map>
        </entry>
        <entry key="external">
            <map>
                <entry key="primary" value="host-public"/>
            </map>
        </entry>
    </map>
</property>
```

Một Java property kiểu `Map<String,Map<String,String>>` có thể nhận cấu trúc này. Nếu nhiều cấp lồng sâu, hãy cân nhắc một class cấu hình có tên/validation thay vì XML lồng nhiều tầng khó bảo trì.

### 6.3. Bean trong collection

```xml
<property name="handlers">
    <list>
        <bean class="com.example.EmailHandler"/>
        <ref bean="sharedSmsHandler"/>
    </list>
</property>
```

Phần tử đầu là **inner bean definition**, phần tử sau là **reference đến một bean khác đã được container đăng ký**. Nếu `handlers` nhận `List<Handler>`, cả hai cùng phải là implementation hợp lệ. Không được suy ra mỗi phần tử trong list luôn là managed top-level bean với tên tra cứu độc lập.

### 6.4. Nested value không phải nested bean trong mọi trường hợp

`<map><entry><list>...</list></entry></map>` tạo nested *collection values* chứ không có bean definition riêng cho mỗi list. `<bean>` đặt bên trong `<property>` hoặc `<constructor-arg>` mới là **inner bean**. Đừng gọi mọi phần tử XML lồng nhau là bean.

## 7. Map entry: value, value-ref và phần tử con

Hai cách này có ý nghĩa khác nhau:

```xml
<entry key="clientName" value="httpClient"/>
```

lưu **literal** chuỗi `httpClient`.

```xml
<entry key="clientObject" value-ref="httpClient"/>
```

lookup bean có tên `httpClient` để dùng **object reference** làm value.

Dạng lồng tương đương với value-ref:

```xml
<entry key="clientObject">
    <ref bean="httpClient"/>
</entry>
```

Với `key-ref`, key cũng có thể là một bean reference; dùng khi kiểu key thực sự là object thay vì String. Nếu cấu hình chỉ cần String keys, `key="..."` đơn giản và rõ nghĩa hơn.

### Cặp null value trong Map

```xml
<entry key="disabled">
    <null/>
</entry>
```

Khác với việc bỏ hẳn `disabled` khỏi map. Nếu business logic dùng `get("disabled") == null`, hai trường hợp vẫn cần phân biệt bằng `containsKey` khi cần.

### Map có key trùng nhau

Một map về bản chất chỉ giữ một value cho mỗi key theo equality. Việc khai báo XML lặp key có thể gây behavior ghi đè/validation tùy parser và dữ liệu. Không dựa vào thứ tự entries làm chính sách override; đặt key duy nhất hoặc merge collections theo quy tắc rõ.

## 8. Generic types và chuyển đổi phần tử

Spring tận dụng generic type information trên constructor/setter nếu Java metadata cho phép:

```java
public final class RetryPolicy {
    private final Map<String, Integer> attempts;

    public RetryPolicy(Map<String, Integer> attempts) {
        this.attempts = attempts;
    }
}
```

Một XML config:

```xml
<bean id="retryPolicy" class="com.example.RetryPolicy">
    <constructor-arg>
        <map>
            <entry key="read" value="3"/>
            <entry key="write" value="5"/>
        </map>
    </constructor-arg>
</bean>
```

Có thể chuyển các string values thành Integer nhờ target constructor signature `Map<String,Integer>`. Đây là khả năng type conversion được tài liệu Spring mô tả cho strongly typed collections. Nếu chuỗi là `"many"`, Integer conversion không hợp lệ và việc tạo bean có thể lỗi.

### Các rủi ro thường gặp

- Java dùng raw type `Map` hoặc `List` nên type metadata không rõ.
- Nested list có lẫn String và bean object dù target là `List<String>`.
- Code giả định keys luôn là String khi XML khai báo `key-ref`.
- Converter tùy chỉnh không được đăng ký hoặc kết quả conversion không như mong đợi.
- Java field là `Map<String,List<Integer>>` nhưng XML chứa giá trị chữ không parse được.
- Consumer thay đổi collection sau injection mà không rõ ownership/thread safety.

**Khuyến nghị:** ưu tiên types cụ thể trên constructor/setter, validates inputs sớm, và test bean creation với các cấu trúc lồng nhau.

## 9. Inner bean là gì?

Một bean nằm trực tiếp trong `<property>` hoặc `<constructor-arg>` của bean khác:

```xml
<bean id="mailDispatcher" class="com.example.MailDispatcher">
    <constructor-arg>
        <bean class="com.example.DeliveryContact">
            <constructor-arg value="ops@example.test"/>
        </bean>
    </constructor-arg>
</bean>
```

là **inner bean**. Nó phù hợp khi collaborator:

- chỉ thuộc về bean bao ngoài;
- không cần tên bean public;
- không cần reuse độc lập từ nhiều consumers;
- có lifecycle gắn với việc tạo bean bao ngoài;
- làm object graph dễ nhìn khi cấu hình nhỏ.

Theo tài liệu Spring, inner bean là **anonymous**; dù bạn điền `id`/`name`, container không dùng chúng như định danh lookup độc lập. `scope` khai báo trên inner bean cũng bị bỏ qua để bảo đảm inner bean được tạo cùng enclosing bean.

### 9.1. Vì sao không cần id?

```xml
<bean id="dispatcher" class="com.example.Dispatcher">
    <property name="contact">
        <bean class="com.example.Contact"/>
    </property>
</bean>
```

Đây là một inner bean được tạo để inject vào `dispatcher.contact`. Nó không xuất hiện như top-level bean tên `contact` chỉ vì property có tên đó.

### 9.2. Nếu inner bean có id?

```xml
<bean id="dispatcher" class="com.example.Dispatcher">
    <property name="contact">
        <bean id="embeddedContact" class="com.example.Contact"/>
    </property>
</bean>
```

Trong Spring XML, `embeddedContact` **không trở thành một tên public dùng `getBean("embeddedContact")`**. Inner bean vẫn được xử lý như bean định nghĩa nội bộ, không được đăng ký thành top-level lookup name.

**Không viết:** “Đặt ID cho inner bean rồi dùng `ref` từ bean khác”. Nếu nhiều consumers cần reuse cùng collaborator, hãy dùng **top-level bean + `ref`**.

### 9.3. Scope của inner bean

Tài liệu Spring ghi rõ inner beans **always anonymous and always created with the outer bean**, và `scope` trên inner bean bị bỏ qua.

Điều này có nghĩa:

- Outer **singleton** được dựng một lần → inner collaborator thường được tạo khi dựng outer và được giữ trong outer.
- Outer **prototype** được tạo qua nhiều lookups → mỗi outer instance thông thường có inner collaborator được tạo cùng nó.
- Việc gắn `scope="singleton"` hay `scope="prototype"` lên inner bean không tạo ra một public scoped bean độc lập.

Một vài scope tùy chỉnh có thể có tương tác destruction callbacks đặc biệt; không nên khẳng định mọi inner bean trên mọi custom scope luôn có hành vi hủy hoàn toàn giống top-level bean. Thiết kế resource ownership và kiểm thử lifecycle cụ thể.

## 10. Inner bean và top-level ref: so sánh

| Tiêu chí | Inner bean | Top-level bean + ref |
| --- | --- | --- |
| Vị trí | `<bean>` lồng trong property/constructor arg | `<bean id="...">` trực tiếp dưới `<beans>` |
| Tên public | Không có | Có `id`/`name`/aliases |
| Lookup độc lập | Không | Có |
| Có thể chia sẻ giữa nhiều consumers | Không thông qua cùng một public bean definition | Có nếu scope và wiring phù hợp |
| Scope | Gắn với tạo outer bean; scope flag nội bộ bị bỏ qua | Tuân scope của chính bean definition |
| Cấu hình cùng owner | Rõ nếu collaborator chỉ dùng một nơi | Rõ nếu collaborator là tài nguyên dùng chung |
| Test riêng theo bean name | Không | Có |
| Lifecycle | Tạo khi outer được tạo; lưu ý custom destruction edge cases | BeanFactory quản lý độc lập theo scope |

### Top-level bean phù hợp khi nào?

```xml
<bean id="sharedContact" class="com.example.Contact">
    <constructor-arg value="shared@example.test"/>
</bean>

<bean id="dispatcherA" class="com.example.Dispatcher">
    <constructor-arg ref="sharedContact"/>
</bean>

<bean id="dispatcherB" class="com.example.Dispatcher">
    <constructor-arg ref="sharedContact"/>
</bean>
```

Hai consumers có thể nhận cùng singleton `sharedContact`. Nếu thay `ref` bằng inner `<bean>` riêng trong từng consumer, đó là hai collaborator definitions nội bộ khác nhau.

### Inner bean phù hợp khi nào?

Một formatter nhỏ gắn với đúng một parser, hoặc local strategy gắn với đúng một service và không cần tái sử dụng có thể là inner bean. Nhưng nếu object giữ database connection pool, thread executor, client có lifecycle phức tạp hoặc được dùng chung bởi nhiều modules, top-level bean có owner rõ thường tốt hơn.

## 11. Ví dụ chạy được Java 17+: null, Map<List>, List<List>, inner bean

Ví dụ sau **không cần Spring Boot**, không dùng annotation configuration, không kết nối mạng/database. Nó dùng **Spring Framework 6.2.9** và **ClassPathXmlApplicationContext**.

Ta định nghĩa:

- `ReleasePlan`: nhận nullable `String note`; `Map<String,List<String>> channels`; `List<List<String>> stages`; `Contact contact`.
- `Contact`: class Java bình thường, chứa email.
- Bean `releasePlan` dùng XML constructor arguments và một inner `Contact`.
- Bean `sharedContact` là top-level để so sánh với inner bean.
- Main in các kiểm tra có stdout xác định.

### 11.1. Cấu trúc dự án

```text
spring-xml-values-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        ├── java/
        │   └── demo/
        │       ├── Contact.java
        │       ├── ReleasePlan.java
        │       └── XmlValuesDemo.java
        └── resources/
            └── beans.xml
```

Có ba public top-level Java classes, file name đúng tên class. XML ở `src/main/resources/beans.xml` để Gradle đưa lên classpath.

### 11.2. Gradle

**`settings.gradle`**

```groovy
rootProject.name = 'spring-xml-values-demo'
```

**`build.gradle`**

```groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.9'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'demo.XmlValuesDemo'
}
```

Spring Context kéo theo Spring Beans/Core và các dependencies cần thiết. Java 17+ tương thích Spring Framework 6.2. Đây là **một project độc lập**, không cần wrapper nếu hệ thống đã có Gradle; nếu có wrapper, dùng lệnh wrapper tương ứng.

### 11.3. Contact.java

**`src/main/java/demo/Contact.java`**

```java
package demo;

import java.util.Objects;

public final class Contact {
    private final String email;

    public Contact(String email) {
        this.email = Objects.requireNonNull(email);
    }

    public String email() {
        return email;
    }
}
```

Constructor yêu cầu email non-null. XML cung cấp một chuỗi hợp lệ.

### 11.4. ReleasePlan.java

**`src/main/java/demo/ReleasePlan.java`**

```java
package demo;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ReleasePlan {
    private final String note;
    private final Map<String, List<String>> channels;
    private final List<List<String>> stages;
    private final Contact contact;

    public ReleasePlan(
            String note,
            Map<String, List<String>> channels,
            List<List<String>> stages,
            Contact contact) {
        this.note = note; // có thể null theo hợp đồng ví dụ
        this.channels = Objects.requireNonNull(channels);
        this.stages = Objects.requireNonNull(stages);
        this.contact = Objects.requireNonNull(contact);
    }

    public String note() {
        return note;
    }

    public Map<String, List<String>> channels() {
        return channels;
    }

    public List<List<String>> stages() {
        return stages;
    }

    public Contact contact() {
        return contact;
    }
}
```

Đây là cấu trúc immutable về **references** (các fields final). Nó **không bảo đảm collections bất biến sâu**, vì Map/List được truyền vào có thể còn mutable. Với production, có thể tạo defensive copies và nested immutable copies khi cần, nhưng điều đó không thay đổi khả năng inject của XML.

### 11.5. beans.xml đầy đủ

**`src/main/resources/beans.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <!-- Một top-level Contact có thể lookup theo tên. -->
    <bean id="sharedContact" class="demo.Contact">
        <constructor-arg value="shared@example.test"/>
    </bean>

    <bean id="releasePlan" class="demo.ReleasePlan">

        <!-- Constructor arg #0: Java null, không phải chuỗi "null". -->
        <constructor-arg index="0">
            <null/>
        </constructor-arg>

        <!-- Constructor arg #1: Map<String, List<String>>. -->
        <constructor-arg index="1">
            <map>
                <entry key="weekday">
                    <list>
                        <value>email</value>
                        <value>sms</value>
                    </list>
                </entry>
                <entry key="weekend">
                    <list>
                        <value>push</value>
                    </list>
                </entry>
            </map>
        </constructor-arg>

        <!-- Constructor arg #2: List<List<String>>. -->
        <constructor-arg index="2">
            <list>
                <list>
                    <value>validate</value>
                    <value>build</value>
                </list>
                <list>
                    <value>deploy</value>
                </list>
            </list>
        </constructor-arg>

        <!-- Constructor arg #3: bean nội bộ không lookup bằng ID. -->
        <constructor-arg index="3">
            <bean id="embeddedContact" class="demo.Contact">
                <constructor-arg value="ops@example.test"/>
            </bean>
        </constructor-arg>

    </bean>
</beans>
```

Có **hai top-level `<bean>` declarations**: `sharedContact` và `releasePlan`. Phần tử `<bean id="embeddedContact">` nằm **bên trong** constructor argument nên là anonymous inner bean; Spring không đăng ký nó thành top-level bean có thể lookup theo ID `embeddedContact`.

### 11.6. Main class

**`src/main/java/demo/XmlValuesDemo.java`**

```java
package demo;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public final class XmlValuesDemo {
    public static void main(String[] args) {
        try (ClassPathXmlApplicationContext context =
                     new ClassPathXmlApplicationContext("beans.xml")) {

            ReleasePlan plan =
                    context.getBean("releasePlan", ReleasePlan.class);
            Contact shared =
                    context.getBean("sharedContact", Contact.class);

            System.out.println("note-null="
                    + (plan.note() == null));

            System.out.println("weekday-channels="
                    + String.join(",", plan.channels().get("weekday")));

            System.out.println("weekend-channels="
                    + String.join(",", plan.channels().get("weekend")));

            System.out.println("stages-count="
                    + plan.stages().size());

            System.out.println("stage-order="
                    + plan.stages().get(0).get(1) + ">"
                    + plan.stages().get(1).get(0));

            System.out.println("inner-email="
                    + plan.contact().email());

            System.out.println("inner-lookup-visible="
                    + context.containsBean("embeddedContact"));

            System.out.println("inner-is-shared="
                    + (plan.contact() == shared));

            System.out.println("release-plan-singleton="
                    + (plan == context.getBean(ReleasePlan.class)));

            System.out.println("top-level-definitions="
                    + context.getBeanDefinitionCount());
        }
    }
}
```

### 11.7. Biên dịch và chạy

Từ thư mục `spring-xml-values-demo`:

```bash
gradle run --quiet
```

Nếu dự án có Gradle Wrapper:

```bash
./gradlew run --quiet
```

Trên Windows, thay bằng `gradlew.bat run --quiet`. Không thể biên dịch riêng `XmlValuesDemo.java` bằng `javac` mà không cung cấp Spring libraries vào compile classpath và biên dịch các source files khác; Gradle xử lý cả classpath lẫn resource copying.

### 11.8. Stdout từ chương trình

```text
note-null=true
weekday-channels=email,sms
weekend-channels=push
stages-count=2
stage-order=build>deploy
inner-email=ops@example.test
inner-lookup-visible=false
inner-is-shared=false
release-plan-singleton=true
top-level-definitions=2
```

Đây là các dòng do những lệnh `System.out.println` trong ví dụ tạo ra. Các log khác của Gradle hoặc runtime không thuộc nội dung này.

### 11.9. Diễn giải output

- **`note-null=true`:** constructor argument 0 nhận Java `null` vì XML dùng `<null/>`.
- **`weekday-channels=email,sms`:** key `weekday` chứa list hai phần tử đúng thứ tự.
- **`weekend-channels=push`:** key `weekend` chứa list một phần tử.
- **`stages-count=2`:** outer list có hai inner lists.
- **`stage-order=build>deploy`:** list thứ nhất `[validate, build]` và list thứ hai `[deploy]`, nên lấy index 1 của list 0 là `build`, index 0 của list 1 là `deploy`.
- **`inner-email=ops@example.test`:** Spring tạo inner `Contact` với constructor argument của nó.
- **`inner-lookup-visible=false`:** tên `embeddedContact` đặt trên inner bean không trở thành một top-level bean ID có thể lookup.
- **`inner-is-shared=false`:** top-level `sharedContact` và inner collaborator là hai object khác nhau.
- **`release-plan-singleton=true`:** bean `releasePlan` dùng default scope singleton và được lookup hai lần trong cùng context.
- **`top-level-definitions=2`:** context XML thuần có đúng hai top-level beans; inner bean không xuất hiện như một top-level bean definition.

**Điểm kiểm chứng quan trọng:** bean nội bộ có thể được Spring instantiate và inject hợp lệ **dù nó không được công bố làm bean name độc lập**.

## 12. Inner bean kết hợp với nested collection

Inner beans không chỉ xuất hiện trực tiếp dưới một `<property>`/`<constructor-arg>`; chúng còn có thể nằm trong collection được dùng làm property/argument.

Ví dụ danh sách policies:

```xml
<bean id="policyEngine" class="com.example.PolicyEngine">
    <constructor-arg>
        <list>
            <bean class="com.example.MinValuePolicy">
                <constructor-arg value="10"/>
            </bean>
            <bean class="com.example.MaxValuePolicy">
                <constructor-arg value="100"/>
            </bean>
        </list>
    </constructor-arg>
</bean>
```

Đây là snippet kiến trúc minh họa, cần các class thật và constructor nhận `List<Policy>` phù hợp. Mỗi `<bean>` bên trong list là inner bean; các policies không có tên top-level để consumers khác lookup độc lập.

Nếu một policy cần được dùng chung bởi nhiều engines, nên định nghĩa top-level policy bean:

```xml
<bean id="sharedPolicy" class="com.example.SharedPolicy"/>

<bean id="policyEngine" class="com.example.PolicyEngine">
    <constructor-arg>
        <list>
            <ref bean="sharedPolicy"/>
        </list>
    </constructor-arg>
</bean>
```

`<ref>` giữ liên kết tới registered bean theo scope của nó; `<bean>` lồng tại điểm sử dụng tạo một inner definition.

### Khi nào nested collection quá sâu?

Nếu XML có nhiều tầng `<map>` và `<list>` đến mức không còn đọc được validation rules, cân nhắc:

- class settings có tên rõ thay vì map of maps;
- Java configuration factory method trả một typed object;
- configuration properties mapping/binding theo stack đang dùng;
- tách XML resource theo boundary;
- tests kiểm tra structure và các invariants nghiệp vụ.

Nested collection hữu ích cho dữ liệu có cấu trúc, không nên biến thành một ngôn ngữ lập trình phức tạp trong XML.

## 13. Collection merging trong XML bean inheritance

Spring XML hỗ trợ **merging** của các collections tương thích khi sử dụng parent/child bean definitions.

Ví dụ property `rules` là `List<String>`:

```xml
<bean id="basePolicy"
      abstract="true"
      class="com.example.RuleConfig">
    <property name="rules">
        <list>
            <value>audit</value>
            <value>validate</value>
        </list>
    </property>
</bean>

<bean id="prodPolicy" parent="basePolicy">
    <property name="rules">
        <list merge="true">
            <value>enforce</value>
        </list>
    </property>
</bean>
```

Đây là snippet cần `RuleConfig` public class có setter `setRules(List<String>)` hoặc property tương thích. `basePolicy` được khai báo abstract để làm template definition; `prodPolicy` thừa hưởng metadata và merge list.

Với `merge="true"` trên **child list**, danh sách sau merge theo thứ tự:

```text
audit
validate
enforce
```

Điểm quan trọng: merge phải được bật **trên collection con** thuộc bean definition kế thừa. Gắn merge flag vào parent list không có tác dụng mong đợi. Không thể merge collection khác loại, ví dụ parent là `<map>` mà child là `<list>`.

### Map, Set và Props merging

- **List:** giữ các phần tử parent trước, rồi nối các phần tử child.
- **Map:** child keys có thể override parent keys.
- **Set:** hợp nhất các phần tử theo set semantics.
- **Properties:** child entries có thể override keys của parent.

Đừng thiết kế logic phụ thuộc vào iteration order của `Map`, `Set` hoặc `Properties` nếu không có contract đảm bảo. Collection merging là cơ chế metadata inheritance, không phải Spring tự merge bất kỳ hai collections được inject từ các beans độc lập.

### Không nhầm merge với nested collection

`<list><list>...</list></list>` là **list lồng trong list**. `<list merge="true">` trong bean child kế thừa **gộp metadata collection của parent và child**. Đây là hai mục đích riêng, có thể xuất hiện cùng nhau nhưng không tự đồng nghĩa.

## 14. Null trong Map và cách phân biệt key vắng mặt

Ví dụ một map cấu hình:

```xml
<property name="featureLimits">
    <map>
        <entry key="basic" value="10"/>
        <entry key="premium">
            <null/>
        </entry>
    </map>
</property>
```

Java code:

```java
Map<String, Integer> limits = settings.getFeatureLimits();

boolean premiumExplicitlyPresent =
        limits.containsKey("premium"); // true

Integer premium = limits.get("premium"); // null
Integer trial = limits.get("trial");     // null
```

Đoạn này là minh họa, giả định `settings` có accessor trả `Map<String,Integer>`. Hai lần `get()` đều trả null, nhưng trường hợp `premium` là key có mặt và value null, trong khi `trial` là key vắng mặt. Nếu app dùng null để diễn đạt “không giới hạn”, hãy viết validation và comments rõ.

**Không cố ép Java primitive `int` chứa null.** Với map value type là `Integer`, null có thể là hợp lệ theo hợp đồng.

## 15. Null và empty collections

Cùng một property `recipients` có ít nhất ba trạng thái:

**Không được cấu hình:**

```xml
<bean id="mailer" class="com.example.Mailer"/>
```

Java property có thể giữ default do constructor/field khởi tạo; không phải Spring tự suy luận empty list.

**Được cấu hình null:**

```xml
<bean id="mailer" class="com.example.Mailer">
    <property name="recipients"><null/></property>
</bean>
```

Setter nhận Java null nếu type phù hợp.

**Được cấu hình empty list:**

```xml
<bean id="mailer" class="com.example.Mailer">
    <property name="recipients"><list/></property>
</bean>
```

Setter nhận một list trống thay vì null. Điều này thường tốt hơn nếu consumer định duyệt trực tiếp recipients, nhưng phải tuân hợp đồng class.

**Nhầm lẫn thực tế:** `<list/>` không phải `<null/>`; empty object collection không phải absence. Trong unit/integration tests nên assert đúng trạng thái mà nghiệp vụ cần.

## 16. Tên bean, alias và inner bean

### 16.1. Top-level id và alias

```xml
<bean id="supportContact" class="com.example.Contact"/>
<alias name="supportContact" alias="emergencyContact"/>
```

Đây là top-level bean definition với một alias. Cả hai names có thể được dùng qua `<ref bean="..."/>` trong cùng context.

### 16.2. Inner bean không xuất hiện như alias

```xml
<bean id="releasePlan" class="com.example.ReleasePlan">
    <property name="contact">
        <bean id="localContact" class="com.example.Contact"/>
    </property>
</bean>
```

`localContact` không trở thành public alias/top-level definition chỉ vì có id attribute. Không thể kỳ vọng `context.getBean("localContact")` hoạt động. Nếu dùng chung, hãy chuyển nó ra top-level.

### 16.3. XML resources và context boundary

Nạp hai XML resources vào một `ClassPathXmlApplicationContext` thì top-level bean definitions/aliases có thể tham chiếu nhau theo registry của context. Inner bean vẫn gắn với enclosing definition.

Tạo hai context độc lập không làm inner bean của context A trở nên visible tại context B. Khái niệm parent-child context cũng không biến một inner bean thành bean lookup công khai.

## 17. Lifecycle và tài nguyên của inner bean

### 17.1. Inner bean có được tạo vào thời điểm parse XML không?

Không mặc định. Parser tạo/ghi metadata. Inner bean instance được tạo khi outer bean được resolve/khởi tạo theo dependency graph; với outer singleton eager, điều này thường xảy ra trong `refresh()`.

### 17.2. Scope attribute trên inner bean có quyết định lookup không?

Không. Scope flag của inner bean bị Spring bỏ qua: inner bean được tạo **cùng outer bean**. Một inner bean không có bean name công khai để lấy qua `getBean()` nhiều lần, nên không có semantics lookup độc lập như top-level prototype.

### 17.3. Còn initialization/destruction callbacks?

Spring vẫn có thể xử lý nhiều khâu initialization/post-processing đối với inner beans do container tạo. Nhưng không nên coi vòng đời destroy của mọi inner collaborator giống hoàn toàn một singleton top-level độc lập: documentation nêu corner case custom scopes có thể nhận destruction callbacks, nhưng inner beans thông thường gắn scope với outer.

Với tài nguyên có cần `close()`, thread pools, socket, connection pools hay SDK clients, việc dùng **top-level named bean** với init/destroy contract rõ ràng thường dễ quản trị hơn. Đừng assume rằng khai báo inline automagically giải quyết resource ownership.

### 17.4. Nếu outer bean là prototype?

Mỗi yêu cầu outer prototype thường tạo outer instance mới và các inner dependencies của nó. Nếu muốn share cùng collaborator singleton giữa nhiều outer prototypes, dùng top-level singleton + `<ref>` thay vì khai báo inner `<bean>` lặp lại.

## 18. Lỗi phổ biến

### Lỗi 1 — Dùng literal "null" thay cho null

```xml
<constructor-arg value="null"/>
```

là string "null" trong trường hợp điển hình. Nếu muốn Java null, dùng `<constructor-arg><null/></constructor-arg>`.

### Lỗi 2 — Truyền null vào primitive

`int`/`boolean`/`long` không nhận Java null. Nếu optional là hợp lệ, thiết kế wrapper types và validation đúng ý định.

### Lỗi 3 — Dùng <value/> để biểu diễn empty collection

`<value/>` là value metadata, không phải empty list. Dùng `<list/>` nếu muốn một `List` rỗng.

### Lỗi 4 — Nhầm ref với value

`<ref bean="sharedClient"/>` là managed dependency, còn `<value>sharedClient</value>` là chuỗi literal. Điều này áp dụng cả trong nested `<list>`/`<map>`.

### Lỗi 5 — Nested collection không phù hợp Java generic type

Constructor yêu cầu `Map<String,List<Integer>>` nhưng XML chứa words `email`/`sms`, gây conversion failure khi Spring tạo bean.

### Lỗi 6 — Trộn các loại phần tử không có chủ đích

Một `List<String>` chứa `<bean class="..."/>` có thể không chuyển thành String hợp lệ. Nếu list cần heterogeneous objects, thiết kế interface/record rõ hoặc đúng target type.

### Lỗi 7 — Lấy inner bean qua getBean("id")

ID của inner bean bị bỏ qua làm tên lookup độc lập. Chuyển thành top-level `<bean id>` rồi dùng `<ref>` nếu thật sự cần lấy theo tên.

### Lỗi 8 — Cố đặt scope singleton để share inner bean

Scope trên inner bean không được dùng theo cách của top-level bean. Nếu cần share một resource, top-level singleton và `ref` là thiết kế đúng hơn.

### Lỗi 9 — Tưởng inner bean giống nested collection

`<list><list>...</list></list>` chỉ mô tả dữ liệu lồng nhau, không tạo bean definition cho mỗi list. `<bean>` lồng mới là inner bean.

### Lỗi 10 — Dùng inner bean cho tài nguyên cần ownership rõ

Một DB pool/client có shutdown phức tạp nên có top-level configuration/lifecycle thích hợp. Đừng ẩn resource quan trọng trong một constructor argument chỉ vì XML ngắn hơn.

### Lỗi 11 — Đặt collection merge trên parent

Phải đặt `merge="true"` trên child inherited collection. Parent flag không thực hiện việc merge mong muốn.

### Lỗi 12 — Merge khác loại collection

Một parent `<map>` và child `<list merge="true">` không thể merge theo cơ chế collection inheritance.

### Lỗi 13 — Cho rằng Map/Set giữ insertion order như List

List có thứ tự; Map/Set không có ordering guarantee phù hợp cho nghiệp vụ trong abstraction chung. Dùng một cấu trúc có order rõ nếu requirement cần.

### Lỗi 14 — Không phân biệt null value với absent key

`map.get(key)==null` không nói key có tồn tại không. Dùng `containsKey` nếu semantics cần phân biệt.

### Lỗi 15 — Đọc chỉ XML mà không xét Java constructors

XML parse hợp lệ không đảm bảo BeanFactory tạo bean thành công. Java parameter types, constructor overloads, null constraints và validation có thể làm startup lỗi.

### Lỗi 16 — Viết sai namespace/schema hoặc đặt phần tử không đúng cấp

`<null/>`, `<entry>`, `<list>` và `<bean>` phải nằm trong cấu trúc mà Spring Beans XML schema hỗ trợ. Khi parser báo lỗi validation, đối chiếu đúng nesting trước khi sửa Java code.

### Lỗi 17 — Trông chờ cấu hình collection luôn immutable

`final Map` chỉ cố định reference. Nếu cần immutable configuration, defensive copy sâu hoặc immutable records theo thiết kế.

### Lỗi 18 — Lạm dụng quá nhiều lớp XML lồng nhau

Đến khi XML thành map of map of list of map, việc hiểu và test mất nhiều công. Cân nhắc Java configuration hoặc typed properties với schema/validation rõ.

## 19. Cách kiểm thử nhanh

### Kiểm thử null

Sau khi tạo context:

```java
ReleasePlan plan = context.getBean(ReleasePlan.class);
if (plan.note() != null) {
    throw new AssertionError("note must be null");
}
```

Đây là snippet đặt trong project demo với biến `context` phù hợp. Nó kiểm chứng **Java value**, không chỉ rằng XML có `<null/>`.

### Kiểm thử nested collections

```java
if (!plan.channels().get("weekday")
        .equals(java.util.List.of("email", "sms"))) {
    throw new AssertionError("weekday channels");
}

if (!plan.stages().get(0).get(1).equals("build")) {
    throw new AssertionError("stage order");
}
```

Kiểm tra shape ở **nhiều mức nesting**, tránh chỉ assert collection size.

### Kiểm thử inner bean

```java
if (context.containsBean("embeddedContact")) {
    throw new AssertionError("inner bean must not be top-level");
}

if (plan.contact() == context.getBean("sharedContact")) {
    throw new AssertionError("inner should be separate from shared contact");
}
```

Các checks này dựa trên XML của demo. Việc cố tình đặt `id="embeddedContact"` trong inner definition làm rõ ID ấy không trở thành bean name công khai.

### Kiểm thử context startup

Nếu XML chứa null trong non-null constructor, nested value sai type hoặc schema nesting không hợp lệ, integration test tạo `ClassPathXmlApplicationContext("beans.xml")` sẽ giúp phát hiện sớm khi startup.

## 20. Bài tập áp dụng

### Bài 1 — Phân biệt null với empty string

Đổi:

```xml
<constructor-arg index="0"><null/></constructor-arg>
```

thành:

```xml
<constructor-arg index="0" value=""/>
```

Dự đoán: `plan.note() == null` chuyển thành false; `plan.note().isEmpty()` thành true. Giải thích vì sao đây là thay đổi giá trị chứ không phải vấn đề XML parsing.

### Bài 2 — Map chứa list có phần tử null

Thêm:

```xml
<entry key="maintenance">
    <list>
        <value>email</value>
        <null/>
    </list>
</entry>
```

Kiểm tra `plan.channels().get("maintenance").size()==2` và phần tử index 1 là null. Lưu ý `String.join` có semantics riêng với null elements; không dùng output của demo gốc để suy luận trường hợp có phần tử null.

### Bài 3 — Thay inner bằng top-level ref

Đổi constructor arg số 3 của `releasePlan` thành:

```xml
<constructor-arg index="3">
    <ref bean="sharedContact"/>
</constructor-arg>
```

Dự đoán `inner-is-shared` trong demo đổi thành true vì `releasePlan` nhận top-level singleton `sharedContact` thay vì tự tạo inner `Contact`.

### Bài 4 — Thêm tầng Map vào List

Mở rộng Java model để nhận `List<Map<String,String>>`, cấu hình `<list>` gồm nhiều `<map>`. Assert keys/values từng map và giải thích khác với `Map<String,List<String>>` hiện có.

### Bài 5 — Thử collection merging

Tạo parent bean definition abstract với property `List<String> rules`, tạo child với `<list merge="true">`. Verify parent items đứng trước child items. Di chuyển `merge="true"` lên parent và so kết quả để hiểu semantics.

### Bài 6 — Khởi tạo không hợp lệ

Đổi `ReleasePlan` constructor parameter note từ `String` thành primitive `int` nhưng vẫn dùng `<null/>`. Dự đoán bean creation failure và thảo luận cách thiết kế optional numeric value an toàn.

## 21. Câu hỏi ôn tập có đáp án

**Câu 1.** `<null/>` khác `value=""` như thế nào?

**Đáp:** `<null/>` tạo Java null; `value=""` truyền chuỗi rỗng với String target.

**Câu 2.** XML `<map><entry key="x"><list>...</list></entry></map>` phù hợp với type Java nào?

**Đáp:** `Map<String,List<T>>` khi các element types phù hợp và conversion thành công.

**Câu 3.** `<list><list>...</list></list>` có tạo inner beans không?

**Đáp:** Không. Nó tạo nested collections. Inner bean yêu cầu phần tử `<bean>` lồng.

**Câu 4.** Inner bean có thể lấy bằng `getBean("embeddedContact")` nếu nó có id không?

**Đáp:** Không. ID/name của inner bean không trở thành public bean identifier.

**Câu 5.** Scope gắn trên inner bean có điều khiển singleton/prototype lookup độc lập không?

**Đáp:** Không. Inner bean được tạo với enclosing bean và scope flag nội bộ bị bỏ qua.

**Câu 6.** Một top-level bean `sharedContact` được tham chiếu hai lần bằng `ref` khác gì khai báo hai inner beans?

**Đáp:** Nếu sharedContact singleton, hai references thường tới cùng managed instance. Hai inner definitions tách biệt sẽ tạo collaborator instances theo enclosing objects.

**Câu 7.** Merge `<list>` đặt ở đâu?

**Đáp:** Trên child collection trong bean definition kế thừa, không phải parent.

**Câu 8.** Có thể merge parent map và child list không?

**Đáp:** Không. Merge yêu cầu compatible collection types.

**Câu 9.** `<entry key="unknown"><null/></entry>` khác không khai báo key đó không?

**Đáp:** Có. Trong trường hợp thứ nhất `containsKey("unknown")` là true nhưng `get` trả null; trường hợp thứ hai key vắng mặt.

**Câu 10.** Spring có thể dùng Java generic types để chuyển string values thành Integer trong collection không?

**Đáp:** Có khi metadata của injection point rõ và type conversion hỗ trợ. Dữ liệu sai format vẫn gây lỗi.

**Câu 11.** `<ref bean="sharedClient"/>` và `<value>sharedClient</value>` có tương đương không?

**Đáp:** Không. Ref lấy object bean; value là string literal.

**Câu 12.** Nếu outer singleton được tạo một lần, inner bean có nhất thiết được đăng ký như singleton top-level không?

**Đáp:** Không. Inner bean là anonymous definition được tạo khi outer được dựng; nó không có public bean ID độc lập.

**Câu 13.** `final List` có luôn immutable không?

**Đáp:** Không. `final` giữ reference; có thể sửa nội dung list nếu implementation mutable. Dùng defensive copies khi cần.

**Câu 14.** Có nên dùng inner bean cho connection pool shared bởi cả hệ thống không?

**Đáp:** Thường không. Nên có top-level managed resource bean với lifecycle/ownership rõ ràng và inject qua `ref`.

## 22. Tài liệu chính thức

- [Spring Framework 6.2 — Dependencies and Configuration in Detail](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-properties-detailed.html): giải thích `<null/>`, inner beans, collections, nested values, collection merging và strongly typed collections.
- [Spring Framework — Bean Overview](https://docs.spring.io/spring-framework/reference/core/beans/definition.html): BeanDefinition, bean metadata và scope.
- [Spring Framework — Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html): singleton, prototype, resource lifecycle.
- [Spring Framework — Dependencies](https://docs.spring.io/spring-framework/reference/core/beans/dependencies.html): constructor/property injection và reference semantics.
- [Spring Framework 6.2 Javadoc — ClassPathXmlApplicationContext](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/support/ClassPathXmlApplicationContext.html): XML context bootstrap.
- [Spring Framework 6.2 Javadoc — BeanDefinition](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/config/BeanDefinition.html): metadata bean definition.
- [Spring Framework — Type Conversion](https://docs.spring.io/spring-framework/reference/core/validation/convert.html): chuyển đổi kiểu khi bind/inject values.

**Kết luận:** Spring XML không chỉ mô tả bean names và classes mà còn mô tả **đồ thị giá trị object**. `<null/>` là Java null thực, nested `<list>`/`<map>` biểu diễn collection structures, còn `<bean>` lồng tạo một anonymous collaborator gắn với enclosing bean. Muốn cấu hình đúng, luôn đối chiếu **Java constructor/setter types**, phân biệt **inner bean với top-level ref**, và kiểm thử cả **giá trị, shape collection, scope và lifecycle**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="xml-configuration-006">Nhập tài nguyên cấu hình bằng import</a>

<details>
<summary>Xem chi tiết</summary>

# Nhập tài nguyên cấu hình bằng import trong Spring XML

## 1. Mục tiêu và bối cảnh

Trong một ứng dụng dùng Spring Framework Core Container, cấu hình XML không bắt buộc phải dồn hết vào một file `applicationContext.xml`. Khi số lượng beans tăng, bạn có thể chia definitions theo module — repositories, services, messaging, security, integrations — rồi ghép chúng bằng phần tử **`<import resource="..."/>`**.

Ví dụ:

```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <import resource="repositories.xml"/>
    <import resource="services.xml"/>

</beans>
```

Nếu file trên ở `classpath:config/root.xml`, hai imports có đường dẫn tương đối `repositories.xml` và `services.xml` được giải quyết dựa trên **vị trí tài nguyên `root.xml` đang thực hiện import**. Các bean definitions từ những file con đi vào **cùng BeanFactory** với definitions trong file cha.

Sau bài học, bạn sẽ:

1. Hiểu chính xác `<import resource="..."/>` khác với `<bean>`, `<ref>`, `@Import` và `@ImportResource` ra sao.
2. Dùng import để chia XML thành modules và ghép lại từ một root configuration.
3. Phân biệt đường dẫn **tương đối so với file XML import**, `classpath:` và `file:`.
4. Biết vì sao một slash đầu trong đường dẫn tương đối không nhất thiết đổi sang “gốc classpath”.
5. Tránh `../`, đường dẫn phụ thuộc thư mục hiện hành, wildcard thiếu kiểm soát và cấu hình import vòng.
6. Hiểu import được Spring xử lý khi **đọc bean definitions**, không phải runtime lookup và không tạo ApplicationContext con.
7. Viết ví dụ Java 17 + Spring Framework 6.2 chạy được: ba file module XML, một root XML, references qua file, alias, singleton identity và stdout rõ ràng.
8. Xử lý bean-name collisions, scopes, lifecycle và ambiguity khi gộp definitions.
9. Tìm nguyên nhân lỗi resource not found, XML schema, bean class loading, missing refs, duplicate definitions và circular dependencies.
10. Kiểm thử mô hình cấu hình XML nhiều module và biết khi nào chuyển sang Java `@ImportResource`.

**Quy tắc quan trọng nhất:** `<import resource="..."/>` nhập **bean definitions từ một tài nguyên cấu hình** vào container đang đọc XML. Một import không tự tạo bean nghiệp vụ, không chạy workflow, không bảo đảm singleton riêng cho module và không thay thế dependency injection.

## 2. Vì sao nên tách một file XML lớn?

Một file duy nhất có hàng trăm hoặc hàng nghìn `<bean>` definitions thường gây:

- Khó tìm một dependency hoặc bean đang được tham chiếu.
- Các module bị trộn, ownership của cấu hình không rõ.
- Merge conflicts khi nhiều nhóm cùng sửa file.
- Khó tái sử dụng cùng một tập definitions trong môi trường thử nghiệm.
- Khó cô lập configuration errors theo package/feature.
- Dễ vô tình khai báo nhiều lần cùng bean name hoặc import các thành phần không cần thiết.

Có thể tổ chức tài nguyên:

```text
src/main/resources/
└── config/
    ├── root.xml
    ├── modules/
    │   ├── repositories.xml
    │   ├── services.xml
    │   └── reporting.xml
    └── environments/
        └── development.xml
```

Trong `config/root.xml`:

```xml
<import resource="modules/repositories.xml"/>
<import resource="modules/services.xml"/>
<import resource="modules/reporting.xml"/>
```

Đây là mẫu kiến trúc: root mô tả module nào được đưa vào application, mỗi module sở hữu những bean definitions tương ứng. File không được import sẽ **không tự động được Spring nạp** chỉ vì nó nằm cạnh những XML khác trong `src/main/resources`.

### File XML là đơn vị tổ chức, không phải container riêng

Ba file XML khác nhau **không đồng nghĩa** ba BeanFactory khác nhau. Khi một `ClassPathXmlApplicationContext("config/root.xml")` đọc root và các imports, mọi definitions được nạp vào **cùng một registry/BeanFactory**.

```text
ClassPathXmlApplicationContext
              |
              v
       config/root.xml
              |
       +------+------+ 
       |      |      |
       v      v      v
  repo.xml service.xml reporting.xml
       |      |      |
       +------+------+ 
              |
              v
    Một BeanDefinitionRegistry
              |
              v
        Một BeanFactory
              |
              v
    Dependency injection chung
```

Một `<constructor-arg ref="catalogRepository"/>` trong `services.xml` có thể trỏ tới bean `catalogRepository` khai báo trong `repositories.xml`. Đó là dependency resolution thông thường, không phải một phương thức “import bean” riêng.

## 3. Cú pháp <import> và cấu trúc XML hợp lệ

Cú pháp của Spring XML là:

```xml
<import resource="relative-or-qualified-location.xml"/>
```

`resource` là thuộc tính bắt buộc chứa **resource location**. Một file import hợp lệ vẫn là tài liệu Spring bean definitions đầy đủ:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="clock" class="example.FixedClock"/>
</beans>
```

Ví dụ `example.FixedClock` chỉ minh họa cấu trúc; Java class đó phải tồn tại trong dự án thật. Không được viết file import chỉ gồm một vài thẻ `<bean>` đứng tự do **bên ngoài root `<beans>`**, trừ khi đang dùng một định dạng được Spring reader xử lý theo hợp đồng riêng.

### Import là phần tử bên trong <beans>

```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <import resource="modules/infrastructure.xml"/>

    <bean id="localService" class="example.LocalService">
        <constructor-arg ref="sharedRepository"/>
    </bean>
</beans>
```

File `infrastructure.xml` được nhập vào root, còn `localService` được khai báo ngay ở root. Nếu file imported cung cấp bean tên `sharedRepository` thì reference có thể được phân giải trong cùng container khi BeanFactory tạo service.

### Có thể có nhiều <import> trong cùng file

```xml
<import resource="base.xml"/>
<import resource="features/orders.xml"/>
<import resource="features/notifications.xml"/>
```

Mỗi lần import yêu cầu Spring đọc resource tương ứng. Trong ứng dụng lớn, đừng ngầm dựa vào việc import cuối “ghi đè thắng” các bean names của import trước; cần kiểm soát collision policy rõ ràng.

### Import có thể lồng nhau

```text
root.xml
  -> modules/orders.xml
       -> repositories.xml
  -> modules/notifications.xml
```

Lưu ý: nếu `orders.xml` ở thư mục `modules`, thì `<import resource="repositories.xml"/>` bên trong `orders.xml` tương đối với **`orders.xml`**, không phải nhất thiết tương đối với `root.xml` ở tầng ngoài.

## 4. Resource location tương đối với file đang import

Đây là quy tắc cốt lõi trong tài liệu Spring Framework.

Giả sử:

```text
src/main/resources/
└── spring/
    ├── application.xml
    ├── repositories.xml
    └── features/
        ├── notifications.xml
        └── templates.xml
```

Trong `spring/application.xml`:

```xml
<import resource="repositories.xml"/>
<import resource="features/notifications.xml"/>
```

Hai paths tương ứng:

- `repositories.xml` → `spring/repositories.xml`.
- `features/notifications.xml` → `spring/features/notifications.xml`.

Nếu trong `spring/features/notifications.xml` có:

```xml
<import resource="templates.xml"/>
```

thì tài nguyên được tham chiếu là `spring/features/templates.xml`.

**Không phải**:

- `./repositories.xml` tính theo working directory của Java process.
- `src/main/resources/repositories.xml`.
- Một resource tự động ở gốc classpath bất kể vị trí file đang import.

Tính tương đối giúp bạn chuyển cả một module directory sang một vị trí khác mà các imports nội bộ không cần đổi, miễn quan hệ thư mục giữa các file còn giữ nguyên.

### Slash ở đầu không nên được hiểu như chuyển về classpath root

Tài liệu Spring minh họa `<import resource="/resources/themeSource.xml"/>` và giải thích rằng với loại **đường dẫn relative import** ấy, **leading slash được bỏ qua**. Vì thế:

```xml
<import resource="/features/notifications.xml"/>
```

không nên được dùng làm cách nói “từ root của classpath” khi file đang import nằm ở `spring/application.xml`. Hãy dùng đường dẫn relative dễ đọc **không có slash đầu**:

```xml
<import resource="features/notifications.xml"/>
```

Nếu muốn địa chỉ classpath rõ ràng và không phụ thuộc vị trí file import, dùng prefix `classpath:` theo phần sau.

## 5. Đường dẫn classpath: — vị trí rõ tại runtime

Bạn có thể sử dụng resource location đầy đủ:

```xml
<import resource="classpath:config/shared/metrics.xml"/>
```

Nó yêu cầu Spring đọc tài nguyên từ runtime **Java classpath**, không diễn giải như chuỗi đường dẫn relative theo file XML hiện tại.

Giả sử `metrics.xml` ở:

```text
src/main/resources/config/shared/metrics.xml
```

Maven thường copy `src/main/resources` vào output classpath, vì vậy location:

```xml
<import resource="classpath:config/shared/metrics.xml"/>
```

phù hợp sau cả khi chạy trong IDE và đóng gói JAR với resource được bundle đúng.

### classpath: không phải src/main/resources

Cách viết sau dễ sai trong JAR:

```xml
<import resource="src/main/resources/config/shared/metrics.xml"/>
```

`src/main/resources` là cấu trúc **source tree** của Maven, không phải runtime classpath path. Nếu chương trình tình cờ chạy được khi working directory ở project root, việc đó không chứng minh cấu hình portable sang JAR/container.

### classpath: và classpath:/ có gì khác?

Trong nhiều Spring resource-loading contexts, `classpath:/config/shared/metrics.xml` cũng nhận diện location tính từ gốc classpath. Tuy vậy, hãy chọn **một convention** nhất quán trong codebase và xác minh bằng context test; không phối hợp slash/prefix ngẫu nhiên.

### Dùng classpath relative hay fully qualified?

| Nhu cầu | Cách viết | Nhận xét |
| --- | --- | --- |
| File cùng thư mục với importer | `resource="repositories.xml"` | Đơn giản, module dễ di chuyển |
| File nằm thư mục con | `resource="modules/services.xml"` | Rõ cấu trúc module |
| File ở một vị trí classpath ổn định, không phụ thuộc importer | `resource="classpath:config/shared/services.xml"` | Vị trí absolute trong classpath |
| File do deployment đặt ngoài package | `resource="file:/opt/app/conf/extra.xml"` | Phụ thuộc filesystem/runtime |
| File cha bằng `../` | `resource="../shared.xml"` | Có thể hoạt động nhưng nên hạn chế |

**Khuyến nghị:** Với một cây XML thuộc cùng module, ưu tiên relative imports. Với tài nguyên chia sẻ được package như một contract rõ trong classpath, cân nhắc `classpath:`. Đừng dùng location tuyệt đối khi chỉ cần một tài nguyên nằm cạnh importer.

## 6. file: — nhập cấu hình từ filesystem

Ví dụ:

```xml
<import resource="file:/etc/myapp/spring/external-services.xml"/>
```

Đây là đường dẫn **minh họa cho hệ Linux/container**, không tồn tại sẵn trong project chạy ở mục 12.

Một ứng dụng dùng `file:` có thể thay đổi file cấu hình theo deployment mà không thay JAR; tuy nhiên:

- File phải tồn tại và có quyền đọc trên **máy/container thực thi**.
- Volume mounts và permissions cần được triển khai đúng.
- Đường dẫn khác nhau giữa Linux/Windows và giữa các môi trường.
- Có thể có rủi ro cấu hình bị sửa ngoài quy trình triển khai.
- Nếu file chứa bean classes của thư viện ngoài, các class đó vẫn phải có trong runtime classpath.
- Resource import xảy ra lúc container nạp configuration; sửa file sau khi app chạy **không tự cập nhật** BeanFactory.
- Đừng nạp XML không tin cậy như một dạng “cấu hình vô hại”: Spring bean definitions có khả năng instantiate classes và chạy init callbacks, mang tính thực thi rất mạnh.

Spring documentation cũng dùng ví dụ file URL trên Windows như `file:C:/config/services.xml`; hãy chuẩn hóa location phù hợp hệ điều hành và tooling thật.

### Không lạm dụng absolute paths

Absolute file path giúp định vị rõ nhưng tăng coupling giữa application và deployment layout. Khi đường dẫn phải thay theo môi trường, có thể dùng biến môi trường/system property hoặc một cơ chế chỉ định resource location thích hợp, rồi test **giá trị thực cuối cùng** được resolver sử dụng.

## 7. Vì sao nên tránh ../ trong imports?

Tài liệu Spring cho phép đề cập `../` ở relative imports nhưng **không khuyến nghị**, đặc biệt với classpath URL.

Ví dụ:

```xml
<import resource="../shared/common.xml"/>
```

Nó phụ thuộc mạnh vào vị trí vật lý của file importer và cấu trúc thư mục bên ngoài module. Trong môi trường classpath gồm nhiều JAR, dependency order, classloaders hoặc packaging, việc “đi lên thư mục cha” có thể cho ra vị trí khác với dự đoán hoặc thất bại.

Đặc biệt, cách viết dạng:

```xml
<import resource="classpath:../services.xml"/>
```

không nên coi là portable. Spring docs giải thích resource resolution có thể chọn classpath root gần nhất rồi tìm lên parent, và classpath thay đổi có thể làm đường dẫn trỏ sai nơi.

**Giải pháp thực dụng:**

1. Nếu tài nguyên cùng feature, sắp xếp dưới cùng thư mục và import relative không cần `../`.
2. Nếu dùng shared resource có vị trí công khai, chọn `classpath:shared/...`.
3. Nếu do runtime cung cấp, chọn `file:` hoặc location từ configuration mà startup nắm rõ.
4. Test dưới artifact được đóng gói thật, không chỉ chạy từ IDE.

## 8. Import và ClassPathXmlApplicationContext nhận nhiều locations

Có **hai phương án phổ biến** để ghép nhiều XML.

### Phương án A — Một root XML điều phối imports

```java
try (var context =
        new ClassPathXmlApplicationContext("config/root.xml")) {
    // Sử dụng beans từ root và tất cả imported files.
}
```

Ưu điểm:

- Java bootstrap chỉ biết một location.
- Root XML nêu rõ module topology.
- Có thể import nested modules.
- Cấu trúc dễ review vì nhìn root thấy những components được bật.

### Phương án B — Truyền nhiều locations cho constructor

```java
try (var context = new ClassPathXmlApplicationContext(
        "config/modules/repositories.xml",
        "config/modules/services.xml",
        "config/modules/reporting.xml")) {
    // Cùng một ApplicationContext xử lý ba XML locations.
}
```

Ưu điểm:

- Java code chọn danh sách resources.
- Không cần file root chỉ để liệt kê imports.
- Có thể phù hợp với ứng dụng nhỏ hoặc test cấu hình module.

### Không nên dùng đồng thời hai cách để nhập trùng

Nếu `root.xml` **đã import** `modules/repositories.xml` mà Java lại truyền thêm `modules/repositories.xml` cùng với `root.xml`, cùng bean definition có thể được đọc lặp. Tác động phụ thuộc bean-name override policy; bạn có thể thấy lỗi, definition bị thay thế hoặc cấu hình không như mong đợi.

**Chọn một composition entry point rõ ràng.** Trong ví dụ chính, ta chỉ nạp `config/root.xml`.

### Nhập ba file không tạo ba ApplicationContexts

Cả root import và nhiều constructor locations trong **một** `ClassPathXmlApplicationContext` đều nhằm xây một BeanFactory. Khác hoàn toàn với:

```java
// Phản ví dụ nếu mục tiêu là một container chung.
var a = new ClassPathXmlApplicationContext(
        "config/modules/repositories.xml");
var b = new ClassPathXmlApplicationContext(
        "config/modules/services.xml");
```

Đoạn trên tạo **hai contexts** độc lập, nên bean `service` trong context `b` không tự tìm được `repository` thuộc context `a`, trừ khi bạn cấu hình parent-child relationship có chủ đích. Nếu mục tiêu là một graph dependency bình thường, không cần chia contexts.

## 9. Import được xử lý khi nào?

Quy trình khái niệm:

```text
Mở ApplicationContext
         |
         v
Đọc resource config/root.xml
         |
         v
XML reader thấy <import resource="..."/>
         |
         v
Resolve từng Resource location
         |
         v
Parse/import XML bean definitions từ file con
         |
         v
Gộp definitions vào BeanDefinitionRegistry
         |
         v
BeanFactoryPostProcessors / BeanPostProcessors
         |
         v
Resolve dependencies, instantiate bean theo scope/lifecycle
         |
         v
ApplicationContext sẵn sàng cho client code
```

Điểm cần nhớ:

- `<import>` là hành động **nạp configuration metadata**.
- Nó không tương đương một lời gọi `getBean()` cho từng bean ngay tại chỗ.
- Một bean có thể là singleton non-lazy và được tạo sau khi context đọc xong definitions, hoặc lazy/prototype và được tạo theo nhu cầu.
- Bean được import tham gia dependency resolution với bean được khai báo tại root và với nhau.
- Không có namespace/container tự động tách biệt theo file.
- Resource không tìm thấy/parse không được thường làm quá trình tạo context thất bại; đây không phải cú pháp import optional.

Trong XML reader, imported resources được xử lý ngay trong quy trình parse/import bean definitions. Nhưng **thứ tự parse** không được dùng làm công cụ bảo đảm thứ tự business initialization. Khi bean A cần bean B, thể hiện quan hệ bằng `ref`, `constructor-arg`, dependency metadata, không chỉ sắp xếp thẻ imports.

## 10. Liên hệ giữa import và ref/constructor-arg/property

Import chỉ giúp Spring **biết bean definitions từ nhiều file**. Việc sử dụng beans vẫn tuân theo DI.

Repositories XML:

```xml
<bean id="orderRepository"
      class="example.InMemoryOrderRepository"/>
```

Services XML:

```xml
<bean id="orderService" class="example.OrderService">
    <constructor-arg ref="orderRepository"/>
</bean>
```

Reporting XML:

```xml
<bean id="orderReporter" class="example.OrderReporter">
    <property name="orderService" ref="orderService"/>
</bean>
```

Các classes `example.*` trong ba đoạn trên là minh họa. Khi root import đủ ba files, `orderService` có thể nhận repository và `orderReporter` có thể nhận service dù definitions nằm ở files khác nhau.

### ref là bean name, không phải tên XML file

```xml
<constructor-arg ref="orderRepository"/>
```

tìm bean **tên `orderRepository`**, không tìm `orderRepository.xml`. File chứa bean đó có thể đổi vị trí nếu root import đúng và bean name được giữ ổn định.

### value không tương đương ref

```xml
<constructor-arg value="orderRepository"/>
```

truyền **chuỗi literal** thay vì managed repository bean. Nếu constructor mong interface `OrderRepository`, thường sẽ có lỗi type conversion/constructor matching. Đừng thay `ref` bằng `value` để “sửa” một bean name missing.

### Dependency bean có thể xuất hiện trước hoặc sau thẻ import

Đừng phụ thuộc vào thứ tự thẻ XML để nghĩ rằng mỗi bean phải được instantiated ngay khi declaration được đọc. BeanFactory đăng ký definitions trước, rồi resolve dependencies và lifecycle khi tạo beans. Tuy nhiên duplicate definition/overriding theo thứ tự nạp vẫn là rủi ro cần tránh.

## 11. Bean names và aliases khi imports nhiều modules

Một BeanFactory có **không gian tên bean chung**. Nếu hai files khai báo:

```xml
<!-- modules/catalog.xml -->
<bean id="client" class="example.CatalogClient"/>
```

và:

```xml
<!-- modules/payment.xml -->
<bean id="client" class="example.PaymentClient"/>
```

bạn có **xung đột bean name** khi cùng import vào một root. Spring Framework và các tầng cấu hình (đặc biệt Boot) có chính sách override khác nhau; không nên dựa vào việc “file import sau sẽ luôn thắng”.

### Tên theo module

Ưu tiên:

```xml
<bean id="catalogClient" class="example.CatalogClient"/>
<bean id="paymentClient" class="example.PaymentClient"/>
```

Cách đặt tên làm rõ ownership và giảm collision trong hệ thống lớn.

### Alias không đăng ký một bean độc lập

Repositories XML:

```xml
<bean id="catalogRepository"
      class="example.InMemoryCatalogRepository"/>
<alias name="catalogRepository"
       alias="legacyCatalogRepository"/>
```

Trong cùng context, `legacyCatalogRepository` là tên bổ sung trỏ tới bean `catalogRepository`. Với singleton scope:

```java
Object a = context.getBean("catalogRepository");
Object b = context.getBean("legacyCatalogRepository");
System.out.println(a == b);
```

Kết quả là:

```text
true
```

Alias có ích khi module cũ cần tên lịch sử, trong khi cấu hình mới sử dụng tên chuẩn. Nhưng alias **không** tạo thêm bean definition, không tạo thêm singleton và không giải quyết việc hai implementations cùng interface bị nhập nhằng.

### Type matching và @Primary/qualifier

Khi các imports bổ sung nhiều beans cùng interface, autowiring theo type có thể không còn duy nhất. Trong XML, bạn có thể dùng `primary="true"` khi phù hợp hoặc chỉ rõ tên qua `ref`. Trong Java injection, có thể dùng `@Qualifier`/`@Primary`. Việc import nhiều files chỉ tăng tập candidates; nó không tự chọn implementation đúng theo nghiệp vụ.

## 12. Ví dụ hoàn chỉnh Java 17 + Spring Framework 6.2

Ví dụ này dùng **Spring Framework thuần**, không cần Spring Boot. Ta có một root XML và ba XML modules chứa các beans phụ thuộc xuyên file. Tất cả tạo thành **một ApplicationContext**, trong đó repository và service là singleton mặc định.

### 12.1. Yêu cầu môi trường

- JDK 17 trở lên.
- Maven 3.8+ hoặc Maven tương thích với JDK đang dùng.
- Dependency Spring Framework `spring-context`, ví dụ phiên bản 6.2.19.
- Không yêu cầu database, internet ở runtime, web server hoặc file ngoài project.

Maven cần tải dependencies ở lần build đầu nếu local cache chưa có; sau khi tải về, demo là chương trình Java console thuần.

### 12.2. Cấu trúc thư mục

```text
spring-xml-import-demo/
├── pom.xml
└── src/main/
    ├── java/
    │   └── demo/
    │       └── XmlImportDemo.java
    └── resources/
        └── config/
            ├── root.xml
            └── modules/
                ├── repositories.xml
                ├── services.xml
                └── reporting.xml
```

Vị trí này quan trọng: `root.xml` ở `config/`, vì vậy `resource="modules/repositories.xml"` sẽ tìm ở `config/modules/repositories.xml`.

### 12.3. File pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="
           http://maven.apache.org/POM/4.0.0
           https://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-xml-import-demo</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.19</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
            </plugin>
        </plugins>
    </build>
</project>
```

### 12.4. File Java đầy đủ

Tạo `src/main/java/demo/XmlImportDemo.java`:

```java
package demo;

import java.util.Arrays;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class XmlImportDemo {

    public interface CatalogRepository {
        String titleFor(String sku);
    }

    // Class public static nested để Spring đọc được tên binary có dấu $.
    public static final class InMemoryCatalogRepository
            implements CatalogRepository {

        public InMemoryCatalogRepository() {
        }

        @Override
        public String titleFor(String sku) {
            return switch (sku) {
                case "BK-7" -> "Java book";
                case "MUG-2" -> "Coffee mug";
                default -> "Unknown item";
            };
        }
    }

    public static final class CatalogService {
        private final CatalogRepository repository;
        private final String prefix;

        public CatalogService(CatalogRepository repository, String prefix) {
            this.repository = repository;
            this.prefix = prefix;
        }

        public String describe(String sku) {
            return prefix + repository.titleFor(sku);
        }

        public CatalogRepository repository() {
            return repository;
        }
    }

    public static final class ReportBoard {
        private final CatalogService service;

        public ReportBoard(CatalogService service) {
            this.service = service;
        }

        public String report(String sku) {
            return "REPORT->" + service.describe(sku);
        }

        public CatalogService service() {
            return service;
        }
    }

    public static final class RootTag {
        private final String label;

        public RootTag(String label) {
            this.label = label;
        }

        public String value() {
            return label;
        }
    }

    public static void main(String[] args) {
        try (var context =
                     new ClassPathXmlApplicationContext("config/root.xml")) {

            CatalogRepository repository =
                    context.getBean(CatalogRepository.class);
            CatalogService service =
                    context.getBean(CatalogService.class);
            ReportBoard board =
                    context.getBean(ReportBoard.class);
            RootTag tag =
                    context.getBean(RootTag.class);

            String[] repositoryNames =
                    context.getBeanNamesForType(CatalogRepository.class);
            Arrays.sort(repositoryNames);

            System.out.println("root=" + tag.value());
            System.out.println("catalog=" + service.describe("BK-7"));
            System.out.println("report=" + board.report("MUG-2"));
            System.out.println("sameRepo=" +
                    (repository == service.repository()));
            System.out.println("sameService=" +
                    (service == board.service()));
            System.out.println("aliasSame=" +
                    (context.getBean("catalogRepository") ==
                     context.getBean("legacyCatalogRepository")));
            System.out.println("repositoryBeans=" +
                    Arrays.toString(repositoryNames));
        }
    }
}
```

**Lưu ý:** Các class XML tham chiếu dùng tên binary nested class, chẳng hạn `demo.XmlImportDemo$InMemoryCatalogRepository`. Chúng được khai báo `public static` và có public constructors nên Spring có thể instantiate mà không cần object ngoài `XmlImportDemo`.

### 12.5. File root: config/root.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <!-- Import ba tài nguyên so với thư mục của root.xml. -->
    <import resource="modules/repositories.xml"/>
    <import resource="modules/services.xml"/>
    <import resource="modules/reporting.xml"/>

    <!-- Bean định nghĩa ngay ở root để chứng minh cùng context. -->
    <bean id="rootTag" class="demo.XmlImportDemo$RootTag">
        <constructor-arg value="ready"/>
    </bean>

</beans>
```

Chỉ `root.xml` được nạp bởi Java `ClassPathXmlApplicationContext`. Ba modules được đọc thông qua `<import>`, không cần truyền thêm paths từ `main`.

### 12.6. File module: config/modules/repositories.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="catalogRepository"
          class="demo.XmlImportDemo$InMemoryCatalogRepository"/>

    <alias name="catalogRepository"
           alias="legacyCatalogRepository"/>

</beans>
```

Bean `catalogRepository` implement `CatalogRepository`. `legacyCatalogRepository` là alias của cùng definition/instance singleton.

### 12.7. File module: config/modules/services.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="catalogService"
          class="demo.XmlImportDemo$CatalogService">
        <constructor-arg index="0" ref="catalogRepository"/>
        <constructor-arg index="1" value="CAT:"/>
    </bean>

</beans>
```

`ref="catalogRepository"` yêu cầu Spring tìm bean repository dù declaration của nó nằm ở **XML khác**. Prefix `CAT:` là literal, được truyền qua constructor.

### 12.8. File module: config/modules/reporting.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="reportBoard" class="demo.XmlImportDemo$ReportBoard">
        <constructor-arg ref="catalogService"/>
    </bean>

</beans>
```

`ReportBoard` được Spring inject managed `CatalogService` bằng constructor. Nó không instantiate service mới trong Java business code.

### 12.9. Lệnh chạy

Trong thư mục chứa `pom.xml`:

```bash
mvn -q clean compile exec:java -Dexec.mainClass=demo.XmlImportDemo
```

Output **do chính chương trình in** (không tính Maven/Spring logs có thể xuất hiện tùy logging configuration):

```text
root=ready
catalog=CAT:Java book
report=REPORT->CAT:Coffee mug
sameRepo=true
sameService=true
aliasSame=true
repositoryBeans=[catalogRepository]
```

### 12.10. Giải thích output

**`root=ready`:** Bean `rootTag` được khai báo ngay trong `root.xml`, tạo một object `RootTag` với literal `ready`.

**`catalog=CAT:Java book`:** Service bean khai báo ở `services.xml` nhận repository từ `repositories.xml` và prefix literal `CAT:`. Repository nhận `BK-7` rồi trả `Java book`.

**`report=REPORT->CAT:Coffee mug`:** `ReportBoard` từ `reporting.xml` nhận managed service từ `services.xml`. Service nhận `MUG-2`, repository trả `Coffee mug`, rồi board thêm `REPORT->`.

**`sameRepo=true`:** Tham số `ref` của service trỏ tới managed singleton repository. Lấy bean từ context và lấy reference mà service giữ cho cùng Java object.

**`sameService=true`:** Board giữ đúng managed `CatalogService` singleton, chứ không tạo một instance mới vì ở module XML khác.

**`aliasSame=true`:** `legacyCatalogRepository` không phải một bean độc lập; hai tên alias/main resolve cùng singleton reference.

**`repositoryBeans=[catalogRepository]`:** Có một bean definition tương thích interface `CatalogRepository`. `getBeanNamesForType` trả tên chính; alias không tăng số bean. `Arrays.sort` bảo đảm thứ tự output khi có nhiều tên, không phụ thuộc thứ tự register.

### 12.11. Những kiểm chứng có thể làm thêm

Thêm vào `main` khi `context` còn mở:

```java
if (!context.containsBean("catalogRepository")) {
    throw new AssertionError("Imported repository missing");
}
if (!context.containsBean("catalogService")) {
    throw new AssertionError("Imported service missing");
}
if (!context.containsBean("reportBoard")) {
    throw new AssertionError("Imported reporter missing");
}
if (context.getBean(CatalogService.class).repository()
        != context.getBean(CatalogRepository.class)) {
    throw new AssertionError("Repository singleton not shared");
}
```

Nếu tất cả điều kiện đúng, đoạn assert không in gì. Đây là **đoạn bổ sung**, không phải class Java độc lập. Nó kiểm tra việc XML imports đã làm các bean cùng hiện diện trong context.

### 12.12. Thử thay đổi import

- **Bỏ import reporting:** Xóa `<import resource="modules/reporting.xml"/>`. `context.getBean(ReportBoard.class)` sau đó sẽ không có bean phù hợp, chương trình không in đủ output. Điều này chứng minh file XML khác không được tự tải chỉ vì có trên classpath.
- **Đổi prefix:** Sửa `value="CAT:"` thành `value="ITEM:"` ở `services.xml`. Output catalog/report thay đổi theo, Java source giữ nguyên.
- **Đổi alias:** Sửa `legacyCatalogRepository` thành `oldCatalogRepository` ở `repositories.xml` và sửa lookup tương ứng trong Java. Số bean repository vẫn là một.
- **Nhập cùng file hai lần:** Thêm dòng import `repositories.xml` lần nữa. Cần quan sát/kiểm soát duplicate definition handling; không coi việc import trùng là cách tạo bean thứ hai có ý nghĩa.
- **Đổi đường dẫn sai:** `resource="module/repositories.xml"` (thiếu “s”) dẫn đến resource not found; context không khởi động thành công.
- **Đổi tên bean service:** Sửa `id="catalogService"` thành `id="catalogSrv"` nhưng không sửa `reporting.xml`; reference `ref="catalogService"` bị thiếu. Đây là một regression trong cross-file contract.

## 13. Cách chia module XML mà không tạo phụ thuộc vòng

Một phân chia dễ bảo trì:

```text
config/root.xml
    imports repositories.xml
    imports services.xml
    imports reporting.xml

Bean dependencies:
reportBoard ---> catalogService ---> catalogRepository
```

Ở đây import graph và dependency graph là hai khái niệm khác nhau.

**Import graph:** file nào cần đọc file nào? Nên có root composition một chiều.

**Bean dependency graph:** bean nào cần instance của bean nào? Được mô tả bởi `ref`, `property`, `constructor-arg` hoặc các cơ chế DI tương đương.

Nếu service XML import repository XML và repository XML lại import service XML, bạn tạo **import cycle** dù các bean trong hai files chưa chắc cần nhau. Không nên có quan hệ “mỗi module import module kia để lấy bean” khi một root có thể nhập cả hai.

### Một root thay vì import chéo

Không nên:

```text
services.xml ---> repositories.xml
repositories.xml ---> services.xml
```

Nên:

```text
root.xml ---> services.xml
         \-> repositories.xml
```

Bean `catalogService` vẫn có thể `ref="catalogRepository"`. Tham chiếu cross-file không bắt buộc file service phải tự import file repo, miễn root đưa cả hai definitions vào cùng container.

### Import cycle không giống circular constructor dependency

Nếu XML import graph không có vòng nhưng bean A có constructor nhận B và bean B có constructor nhận A, Spring vẫn gặp **circular constructor dependency**.

```text
Import graph:     root -> a.xml; root -> b.xml  (không vòng)
Bean graph:       bean A -> bean B -> bean A    (có vòng)
```

Đừng chữa một trong hai bằng cách đổi tên file hoặc đảo thứ tự imports. Import cycle phải sửa cách ghép resources; bean cycle phải sửa dependency direction, trích abstraction hoặc coordinator.

## 14. Chia XML theo feature hay theo technical layer?

Hai cách tổ chức đều có thể hợp lý.

### Theo technical layer

```text
root.xml
├── infrastructure.xml
├── repositories.xml
├── services.xml
└── presentation.xml
```

Phù hợp với ứng dụng nhỏ hoặc cấu trúc tầng đã ổn định. Cần tránh để tầng thấp phụ thuộc ngược tầng cao.

### Theo feature/module

```text
root.xml
├── billing/billing-context.xml
├── catalog/catalog-context.xml
└── shipping/shipping-context.xml
```

Phù hợp khi nhóm chịu trách nhiệm từng feature và muốn module boundaries rõ. Mỗi feature config nên công bố những bean names/abstractions mà module khác thật sự cần, tránh import lẫn nhau chỉ để lấy một dependency.

### Một mẫu dung hòa

```text
root.xml
├── core/infrastructure.xml
├── catalog/catalog.xml
├── billing/billing.xml
└── integrations/external.xml
```

Root chọn hệ thống cần những module nào. Khi cần đổi integration triển khai theo môi trường, có thể thay một file import ở root hoặc chọn resource location từ bootstrap config, nhưng giữ một **entry point rõ ràng**.

## 15. Imports và Profiles/điều kiện môi trường

Không phải môi trường nào cũng cần tất cả bean definitions. Spring XML có thuộc tính `profile` trên `<beans>` giúp chọn subset definitions phù hợp với active profiles.

Ví dụ minh họa:

```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <beans profile="dev">
        <import resource="environments/development.xml"/>
    </beans>

    <beans profile="prod">
        <import resource="environments/production.xml"/>
    </beans>

</beans>
```

Khi Spring XML reader xử lý profile-specific `<beans>`, những bean definitions trong nhánh profile không active không được đưa vào cùng tập active definitions theo hợp đồng `profile`. Cấu hình chi tiết phụ thuộc cách context đặt active profiles.

### Kích hoạt profile đúng thời điểm

Với `ClassPathXmlApplicationContext`, nếu bạn muốn set active profiles **trước khi refresh**, có thể tạo context chưa auto-refresh:

```java
ClassPathXmlApplicationContext context =
        new ClassPathXmlApplicationContext(
                new String[]{"config/root.xml"}, false);
context.getEnvironment().setActiveProfiles("dev");
context.refresh();
try {
    // Sử dụng beans của profile dev.
} finally {
    context.close();
}
```

Đây là đoạn minh họa luồng bootstrap. Nó **khác** constructor `ClassPathXmlApplicationContext("config/root.xml")` mặc định refresh ngay. Đặt active profiles sau refresh không có tác dụng quay ngược các import/bean definitions đã được xử lý.

### Nếu chỉ muốn chọn một import ngoài XML?

Có thể chọn resource list từ Java bootstrap/environment khi khởi tạo context, hoặc dùng profile-specific nested `<beans>` nếu phù hợp. Không phải cứ dùng `<import>` là có một thuộc tính `optional="true"` hay `condition="..."` trên chính thẻ import; đừng bịa attributes không thuộc Spring XML schema.

### Thận trọng với dependency bắt buộc

Nếu profile dev nạp `devRepository` nhưng profile prod không nạp bất kỳ `OrderRepository` nào, một `OrderService` bắt buộc cần repository vẫn fail startup ở prod. Hãy test từng profile hợp lệ.

## 16. Property placeholders trong đường dẫn import

Spring XML import location có thể chứa placeholders theo cơ chế environment/system-property resolution trong quá trình đọc XML, ví dụ:

```xml
<import resource="classpath:config/${module.resource}"/>
```

Nếu `module.resource` là một JVM system property được Spring environment thấy và có giá trị `modules/services.xml`, location dự kiến sau resolve là:

```text
classpath:config/modules/services.xml
```

**Lưu ý mốc thời gian:** XML imports được resolve **trong lúc đọc bean definitions**. Một `PropertySourcesPlaceholderConfigurer` do chính tài liệu XML đang import khai báo có thể chỉ được tạo/chạy ở giai đoạn BeanFactory post-processing **sau khi import đã cần location**. Vì vậy đừng phụ thuộc vào configurer khai báo muộn để resolve đường dẫn import sớm.

### Ví dụ chạy thử với JVM system property

Khi có file `config/root.xml` chứa:

```xml
<import resource="classpath:config/${module.resource}"/>
```

và tài nguyên nằm ở `config/modules/services.xml`, hãy bảo đảm system property đã được thiết lập **trước khi context refresh**, ví dụ qua JVM startup args hoặc:

```java
System.setProperty("module.resource", "modules/services.xml");
// Sau đó mới tạo/refresh ClassPathXmlApplicationContext.
```

Đoạn này là minh họa; project runnable chính ở mục 12 **không** cần property placeholder. Tránh đặt system properties từ business code khi có nhiều contexts chạy song song; dùng một startup configuration source nhất quán.

### Giá trị chưa resolve

Nếu placeholder không có giá trị, import location có thể không tìm được resource và context startup fail. Hãy coi placeholder dùng để chọn file configuration là một **yêu cầu bootstrap** và validate trong quá trình triển khai.

## 17. classpath*: và wildcard resource patterns

Spring `ResourcePatternResolver` hỗ trợ prefix **`classpath*:`** để tìm **tất cả** resources phù hợp trên classpath trong những ngữ cảnh hỗ trợ resource patterns.

Ví dụ minh họa:

```xml
<import resource="classpath*:META-INF/spring/module-context.xml"/>
```

Khi được xử lý bởi resolver có hỗ trợ `classpath*:`, location có thể trả **nhiều resources** ở những JAR/modules khác nhau. Đây là cơ chế khác với `classpath:` thường nhắm đến một resource tại path được yêu cầu.

### Vì sao cần cẩn trọng?

- Một thư viện khác có thể đóng gói cùng resource path.
- Thứ tự resources từ classloader không nên dùng làm nghiệp vụ precedence.
- Có thể nạp những XML không dự tính, thậm chí cùng bean names.
- Một số patterns/wildcards trong JAR còn phụ thuộc classloader và packaging.
- Resource set ở IDE test khác với JAR/container/runtime server.
- Không phù hợp nếu bạn muốn cho phép đúng **một** cấu hình module từ một package.

**Khuyến nghị:** Đối với module lõi có dependencies rõ, dùng đường dẫn cụ thể. Dùng wildcard resource discovery chỉ khi “nạp tất cả match” là yêu cầu rõ ràng, có kiểm thử package và quy tắc collision.

Không trộn nghĩa `classpath*:` với kiểu glob shell `*.xml`. Các dạng pattern cụ thể phải được hỗ trợ bởi Spring resource resolver và được test trên packaging mục tiêu.

## 18. Thiếu file import có được bỏ qua không?

Với Spring XML `<import>` bình thường, file bị thiếu hoặc lỗi parse thường dẫn đến **thất bại khi tạo ApplicationContext**, ví dụ exception liên quan bean definition store hoặc resource loading.

```xml
<import resource="modules/non-existent.xml"/>
```

Không nên mặc định rằng Spring sẽ bỏ qua vì tên file trông giống “tùy chọn”. Trong Spring XML schema thông dụng, `<import>` không có một cơ chế `optional="true"` chung tương đương với conditional import.

Nếu một feature không bắt buộc:

- Không include dòng import của nó trong root của môi trường đó.
- Hoặc dùng `<beans profile="...">` để chỉ import khi profile phù hợp.
- Hoặc cấu hình root/resources list được xác định ở bootstrap cho môi trường.
- Hoặc tách optional infrastructure thành mô-đun khác có enable flag/conditional configuration rõ ràng.

**Đừng che mọi exception và tiếp tục với một container nạp thiếu definitions**: các bean bắt buộc có thể fail muộn và khó chẩn đoán hơn.

## 19. XML schema và namespace phải hợp lệ trong từng imported file

Một imported XML vẫn phải là một tài liệu Spring configuration parse được.

Ví dụ có namespace `context:`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/context
           https://www.springframework.org/schema/context/spring-context.xsd">

    <context:annotation-config/>

</beans>
```

Đây là ví dụ cú pháp của một file module. Nếu file imported dùng namespace custom, nó phải khai báo `xmlns`/`xsi:schemaLocation` phù hợp và dependency cung cấp namespace handler trên classpath.

Một root có namespace `context` **không mặc định làm mọi file XML con tự có khai báo prefix `context:`**. Từng XML document phải hợp lệ riêng về mặt XML namespaces.

### Import không thay thế dependency JAR

Nếu imported XML có:

```xml
<bean id="legacyConnector" class="com.vendor.LegacyConnector"/>
```

mà `com.vendor.LegacyConnector` không tồn tại trong runtime classpath, Spring vẫn báo class loading/bean creation failure. `<import>` không tải code JAR từ mạng, không tự cài dependency và không sửa Java classpath.

## 20. Duplicate imports và bean overriding

Có hai tình huống khác nhau.

### 20.1. Cùng bean name xuất hiện trong hai tài nguyên

```xml
<!-- a.xml -->
<bean id="paymentGateway" class="example.DefaultGateway"/>
```

```xml
<!-- b.xml -->
<bean id="paymentGateway" class="example.LegacyGateway"/>
```

Nếu root import cả hai, tên bean trùng. Tùy BeanFactory và cấu hình, Spring có thể từ chối hoặc cho phép override. Chính sách ở Spring Boot có thể khác plain Framework. **Không thiết kế chọn gateway theo thứ tự imports**.

### 20.2. Một tài nguyên bị import hai lần

```xml
<import resource="modules/gateway.xml"/>
<import resource="modules/gateway.xml"/>
```

Điều này có thể khiến metadata được xử lý nhiều lần. Không hiểu “import hai lần” là cách có hai bean độc lập: cùng `id` sẽ tranh cùng tên trong registry.

### Để cần hai implementations có chủ đích

Định nghĩa tên riêng:

```xml
<bean id="standardGateway" class="example.StandardGateway"/>
<bean id="fallbackGateway" class="example.FallbackGateway"/>
```

và chọn qua explicit ref/qualifier/registry logic. Tên riêng làm rõ rằng đây là **hai bean definitions**, không phải kết quả ngẫu nhiên của importing cùng file nhiều lần.

### Giữ quy tắc một nguồn sở hữu một bean name

Ở dự án nhiều nhóm, nên tài liệu hóa module nào sở hữu mỗi public bean name quan trọng. Khi refactor XML, chuyển definition cùng với tests và xóa/điều chỉnh import cũ tránh duplicate.

## 21. Import không định nghĩa thứ tự khởi tạo nghiệp vụ

Thứ tự khai báo:

```xml
<import resource="services.xml"/>
<import resource="repositories.xml"/>
```

không nên được dùng để yêu cầu “services khởi động trước repositories” hay ngược lại theo nghiệp vụ. Spring thường resolve constructor/property references theo dependency graph dù definitions đến từ files khác nhau.

Nếu một bean phải nhận repository ở constructor:

```xml
<bean id="service" class="example.Service">
    <constructor-arg ref="repository"/>
</bean>
```

BeanFactory biết service cần repository. Không cần đổi thứ tự XML imports chỉ để biểu diễn dependency này.

### Khi cần explicit ordering

`depends-on` hoặc `@DependsOn` có thể chỉ rõ những ràng buộc khởi tạo ngoài DI reference, nhưng không nên dùng như một workflow engine. Nếu yêu cầu là seed data, gọi remote API, start listeners hoặc migrations, hãy chọn lifecycle/startup coordination phù hợp (ví dụ startup coordinator hoặc SmartLifecycle) thay vì nhồi hành vi nghiệp vụ vào constructor/import.

### Vòng A ↔ B vẫn là vòng

Nếu repository phụ thuộc vào service, đồng thời service phụ thuộc repository, chuyển hai definitions vào các file khác nhau không phá dependency cycle. Cần sửa thiết kế ứng dụng.

## 22. Import và @ImportResource trong cấu hình Java

Hai annotation/cơ chế rất dễ nhầm:

| Công cụ | Nơi khai báo | Nhận gì? | Mục tiêu |
| --- | --- | --- | --- |
| XML `<import resource="modules/a.xml"/>` | Một XML `<beans>` file | Resource XML | Ghép XML vào XML |
| Java `@Import(InfraConfig.class)` | Class cấu hình Java | Configuration/component class | Ghép Java configs |
| Java `@ImportResource("classpath:legacy.xml")` | Class cấu hình Java | XML resource location | Nhập XML từ Java config |
| Java `@ComponentScan(...)` | Java config/component | Package/classes markers | Tìm stereotype bean candidates |

Nếu ứng dụng khởi động bằng Java configuration:

```java
@Configuration(proxyBeanMethods = false)
@ImportResource("classpath:config/root.xml")
public class AppConfig {
}
```

thì Spring nạp `config/root.xml`; từ đó, nếu file này có `<import resource="modules/services.xml"/>`, các imported XML modules cũng được xử lý theo cơ chế XML reader thông thường.

### ImportResource không tạo container phụ

`@ImportResource` và XML `<import>` đều đưa bean definitions vào cùng container mà class/configuration root được nạp. Nếu một Java `@Bean` method nhận dependency thuộc imported XML, có thể inject nó theo type/qualifier như bình thường.

### Chiều ngược XML-centric

Nếu XML là entry point chính, như ví dụ `ClassPathXmlApplicationContext("config/root.xml")`, bạn không cần `@ImportResource`. Đừng thêm Java annotation chỉ vì muốn Spring hiểu `<import>`; XML reader tự xử lý import tags.

### Khi nào nên chuyển sang Java?

Khi một XML chỉ còn vài bean definitions đơn giản, có thể cân nhắc `@Bean` methods vì IDE/type safety/refactor dễ hơn. Nhưng XML import vẫn hữu dụng cho legacy namespaces/configurations. Không có yêu cầu tất cả cấu hình phải được chuyển trong một lượt.

## 23. Chẩn đoán lỗi import theo triệu chứng

| Triệu chứng | Nguyên nhân có thể | Cách kiểm tra/sửa |
| --- | --- | --- |
| XML root tìm được nhưng module missing | `resource` sai relative path | Tính đường dẫn từ chính file đang import |
| `FileNotFoundException` trong JAR | Dùng source tree path hoặc resource không được package | Kiểm tra `src/main/resources`, target/classes và nội dung JAR |
| Đường dẫn `../` chỉ lỗi ở prod | Package/classpath layout khác IDE | Dùng relative trong module hoặc `classpath:` rõ ràng |
| Resource path bắt đầu slash nhưng không tìm như kỳ vọng | Nhầm relative leading slash là classpath root | Bỏ slash hoặc dùng `classpath:` |
| `BeanDefinitionStoreException` | Resource không đọc được, XML invalid, import cycle hoặc reader failure | Xem exception cause/resource trong stack trace |
| `SAXParseException` | XML malformed hoặc sai schema namespace | Kiểm tra XML syntax và XSD mapping |
| `CannotLoadBeanClassException` | Class trong `<bean class=...>` thiếu/sai | Kiểm tra runtime JAR và FQCN |
| `NoSuchBeanDefinitionException` | File module chưa import hoặc bean id/ref sai | Xem root imports và tên bean chính/alias |
| `NoUniqueBeanDefinitionException` | Nhiều beans cùng interface sau khi import | Dùng qualifier/primary, giảm duplicate candidates |
| Bean name collision | Hai files dùng cùng `id` | Đặt tên theo module/giải quyết override policy |
| Bean bị nạp hai lần | Import cùng file ở nhiều đường | Rà soát root composition và nested imports |
| Bean count khác mong đợi | Alias bị đếm sai hoặc wildcard nhập nhiều files | Kiểm tra `getBeanNamesForType` và actual resource set |
| `ClassCastException`/bean not required type | XML `ref` trỏ bean khác type | Sửa FQCN, ref, exposed proxy/interface |
| Constructor cycle | A cần B, B cần A qua `ref` | Sửa dependency design, không đảo thứ tự imports |
| Placeholder location không được resolve | Property chưa có lúc XML parse | Đưa setting vào Environment/system properties trước refresh |
| `classpath*:` nạp dư mô-đun | Trùng resource path trong nhiều JAR | Giới hạn resources cụ thể, test package |
| Lỗi chỉ xảy ra theo profile | Nhánh profile import thiếu hoặc thừa beans | Test từng active profile trước refresh |
| Test thành công nhưng app lỗi | Boot root/scan/import topology khác test | Test đúng root context thực và artifact runtime |

### Quy trình debug từng bước

1. Xác nhận **đúng root XML** mà Java đang tải.
2. Xem `<import resource="..."/>` trong root và xác định location trên runtime classpath/filesystem.
3. Với nested imports, lần theo từng file importer và relative path từ **file đó**.
4. Đọc root cause trong exception — file missing, XML parse, class loading, duplicate definition hay missing bean.
5. Mở từng XML độc lập để kiểm tra `<beans>`, namespaces, schema và class names.
6. Kiểm tra BeanFactory có các bean names expected bằng `containsBean`/`getBeanDefinitionNames`.
7. Xem cross-file `ref`/`property`/`constructor-arg` đúng bean name và type.
8. Kiểm tra profile/placeholder có được set **trước refresh**.
9. Kiểm tra import cycle, duplicate paths và wildcard resources.
10. Chạy integration test bằng đúng root/context và dependency versions của ứng dụng.

## 24. Kiểm thử cấu hình nhiều file

### Test khởi tạo một context

```java
try (var context =
        new ClassPathXmlApplicationContext("config/root.xml")) {
    if (!context.containsBean("catalogRepository")) {
        throw new AssertionError("Repository XML not imported");
    }
    if (!context.containsBean("catalogService")) {
        throw new AssertionError("Service XML not imported");
    }
    if (!context.containsBean("reportBoard")) {
        throw new AssertionError("Reporting XML not imported");
    }
}
```

Đây là **đoạn test minh họa** dùng code/types từ project mục 12. Dựng context thật tốt hơn chỉ kiểm tra từng file XML có tồn tại; file hợp lệ về syntax vẫn có thể chứa sai reference hoặc thiếu class.

### Test identity qua module

```java
CatalogRepository repository =
        context.getBean(CatalogRepository.class);
CatalogService service =
        context.getBean(CatalogService.class);
ReportBoard board =
        context.getBean(ReportBoard.class);

if (repository != service.repository()) {
    throw new AssertionError("Service uses a different repository");
}
if (service != board.service()) {
    throw new AssertionError("Board uses a different service");
}
```

Đoạn này cần chạy **bên trong block đang mở context** của ví dụ, không phải file Java độc lập. Nó xác nhận các beans được inject thực sự từ cùng BeanFactory.

### Test đường dẫn sau packaging

Một cấu hình chỉ hoạt động trong IDE chưa đủ. Hãy xác nhận file XML được Maven copy đúng dưới `target/classes/config/...` và nằm trong artifact JAR theo đường dẫn runtime:

```bash
jar tf target/spring-xml-import-demo-1.0-SNAPSHOT.jar
```

Lệnh này đọc artifact JAR mà Maven đã đóng gói; nếu chỉ chạy `compile` thì file JAR có thể chưa được tạo. Dùng:

```bash
mvn -q package
jar tf target/spring-xml-import-demo-1.0-SNAPSHOT.jar
```

Tìm paths `config/root.xml` và `config/modules/*.xml` trong danh sách. Mục đích là xác minh resource packaging, không phải thay đổi XML file.

### Test negative paths

Trong test resources riêng, có thể kiểm tra:

- Một import sai tên file phải làm context startup thất bại.
- Một bean `ref` sang module không được import phải lỗi.
- Hai bean definitions cùng tên bị phát hiện theo policy dự án.
- Profile dev/prod tải đúng modules.
- Không có vòng import A→B→A.
- Không có vòng constructor dependencies không được giải quyết.

Đừng cố dựa vào nguyên văn toàn bộ exception message: loại exception và wrapper stack có thể đổi theo Spring version. Hãy kiểm tra root cause và behavior quan trọng.

## 25. Hướng dẫn migration và tổ chức repository

Khi một dự án legacy đang có một XML lớn:

### Bước 1 — Lập danh sách beans và dependencies

Ghi nhận bean name, aliases, class, scope, ref dependencies, lifecycle, property placeholders và namespaces của từng bean. Chú ý những public bean names mà các consumer khác đang dùng.

### Bước 2 — Nhóm definitions theo ownership

Chia thành các files theo feature hoặc layer có ý nghĩa. Đừng thay đổi Java classes và XML wiring cùng lúc nếu không cần thiết; một thay đổi chỉ tái cấu trúc file thường dễ xác minh hơn.

### Bước 3 — Tạo root.xml

```xml
<import resource="modules/infrastructure.xml"/>
<import resource="modules/repositories.xml"/>
<import resource="modules/services.xml"/>
```

Root là nơi ứng dụng chọn modules cần dùng. Đừng để từng module import lẫn nhau chỉ để đáp ứng `ref`.

### Bước 4 — Bảo toàn bean-name contracts

Khi di chuyển `<bean id="accountRepository".../>` sang XML module khác, giữ `id="accountRepository"` nếu những consumers đang tham chiếu tên này. Nếu cần đổi tên, cân nhắc alias migration và kiểm thử.

### Bước 5 — Chỉ load một root

Đừng vừa nạp root mới vừa nạp những XML con riêng rẽ mà root đã import, tránh duplicate definitions.

### Bước 6 — Test context và hành vi chính

Khởi tạo từ đúng root; xác minh các dependencies, singleton identity, profiles, lifecycle và những output nghiệp vụ quan trọng. Dùng negative tests cho missing import/duplicate name.

### Bước 7 — Kiểm tra artifact deployment

Phải bảo đảm tài nguyên XML được copy vào JAR hoặc volume ngoài như cấu hình. Với file URLs, xác minh quyền đọc và tính ổn định của paths ở môi trường thực.

### Bước 8 — Nếu cần, chuyển dần sang Java configuration

Có thể dùng Java `@ImportResource` để tiếp tục nhập XML root; dần thay từng bean bằng `@Bean`/component scanning. Mỗi lần chuyển một definition, tránh để lại XML definition cùng tên nếu không có override policy thật sự. Không cần xóa mọi XML chỉ vì đã có Java config.

## 26. Bài tập có đáp án

### Bài 1 — Đường dẫn cùng thư mục

`root.xml` nằm ở `classpath:settings/root.xml` và `db.xml` ở `classpath:settings/db.xml`. Hãy viết import relative.

**Đáp án:**

```xml
<import resource="db.xml"/>
```

Đường dẫn được giải quyết tương đối với `root.xml`.

### Bài 2 — File trong thư mục con

`root.xml` ở `config/root.xml`, `inventory.xml` ở `config/modules/inventory.xml`.

**Đáp án:**

```xml
<import resource="modules/inventory.xml"/>
```

### Bài 3 — Đường dẫn classpath rõ ràng

Một module ở vị trí `config/shared/common.xml` phải được nạp bất kể root hiện nằm đâu.

**Đáp án:**

```xml
<import resource="classpath:config/shared/common.xml"/>
```

### Bài 4 — Slash đầu

`root.xml` nằm ở `config/root.xml`. Developer viết `<import resource="/modules/services.xml"/>` và cho rằng nó đi tới `classpath:/modules/services.xml`. Nhận định này chắc đúng không?

**Đáp án:** Không. Với relative XML import, tài liệu Spring giải thích leading slash bị bỏ qua. Nên viết `modules/services.xml` để đi tới `config/modules/services.xml` hoặc dùng `classpath:modules/services.xml` nếu thực sự muốn tài nguyên ở root classpath.

### Bài 5 — XML import có tạo context mới?

Một root có ba `<import>`. Có mấy BeanFactories khi chỉ có một `ClassPathXmlApplicationContext("root.xml")`?

**Đáp án:** Một BeanFactory trong một ApplicationContext (giả sử ứng dụng không tự dựng thêm contexts). Các files cung cấp bean definitions chung.

### Bài 6 — Ref giữa modules

`service.xml` khai báo bean `orderService` với `ref="orderRepository"`. Bean `orderRepository` nằm ở `repo.xml`. Có cần import `repo.xml` trực tiếp bên trong `service.xml` không?

**Đáp án:** Không nếu root đã nhập cả hai vào cùng context. `ref` được BeanFactory chung phân giải theo tên; không cần file service tự import file repo.

### Bài 7 — Số bean và alias

`repo.xml` khai báo `id="repo"` và alias `oldRepo`. Có thêm một bean independent không?

**Đáp án:** Không. Alias chỉ là tên thứ hai trỏ tới cùng bean definition; singleton reference từ hai tên là cùng object.

### Bài 8 — Duplicate import

Java tạo `new ClassPathXmlApplicationContext("root.xml","repo.xml")`, trong khi `root.xml` đã import `repo.xml`. Rủi ro gì?

**Đáp án:** Definition từ `repo.xml` bị đọc lặp, gây duplicate bean names/overriding phụ thuộc policy. Chỉ nạp root hoặc chọn danh sách leaf resources, tránh cả hai.

### Bài 9 — Missing resource

Trong root có `<import resource="does-not-exist.xml"/>`. Spring tự bỏ qua vì “optional feature” không?

**Đáp án:** Không. Import missing/parse invalid thường làm việc tạo context thất bại. Chọn root/profile phù hợp thay vì dựa vào optional import không có trong schema thông dụng.

### Bài 10 — File trong JAR

Sau đóng gói, `<import resource="src/main/resources/config/db.xml"/>` lỗi. Sửa như thế nào?

**Đáp án:** Nếu importer ở `config/root.xml` và target `config/db.xml`, dùng `resource="db.xml"`. Hoặc dùng `resource="classpath:config/db.xml"` để chỉ rõ runtime classpath.

### Bài 11 — import vòng và bean vòng

Hai XML imports không có cycle nhưng bean A constructor nhận B, B nhận A. Đổi thứ tự imports có sửa được không?

**Đáp án:** Không. Constructor dependency cycle là vấn đề đồ thị bean. Phải sửa hướng dependencies/abstraction, không phải thứ tự đọc XML.

### Bài 12 — Profiles

Root có `<beans profile="dev"><import resource="development.xml"/></beans>`. Khi nào cần đặt active profile `dev`?

**Đáp án:** Trước `context.refresh()`. Nếu dùng constructor tự refresh, phải cung cấp profile qua environment/system settings trước bootstrap hoặc dùng constructor chưa refresh rồi set profile và gọi `refresh()`.

### Bài 13 — classpath*: có phải “một file bất kỳ”?

`<import resource="classpath*:META-INF/spring/module.xml"/>` có thể tìm được nhiều files cùng tên ở nhiều JAR. Có nên dựa vào một file bất kỳ “được chọn đầu tiên” không?

**Đáp án:** Không. Ý nghĩa của `classpath*:` là tìm tất cả matching resources trong những ngữ cảnh hỗ trợ; resource ordering/collisions phải được kiểm thử và quản lý rõ.

### Bài 14 — @ImportResource khác @Import

Một Java `@Configuration` muốn import XML `legacy.xml`. Có dùng `@Import("legacy.xml")` không?

**Đáp án:** Không. Dùng `@ImportResource("classpath:legacy.xml")` cho XML resource; `@Import` dành cho class/configuration/component/selector/registrar theo API Spring.

### Bài 15 — Property placeholder ở import

XML muốn chọn file qua `resource="classpath:config/${module.file}"`. Configurer bean được khai báo trong chính file XML đó có chắc đã chạy trước khi import được resolve không?

**Đáp án:** Không. Import location được resolve trong lúc XML definitions đang được đọc, còn BeanFactory post-processors có thể chạy sau. Cung cấp property ở Environment/system properties trước bootstrap.

### Bài 16 — Tạo một module mới

Có root `config/root.xml`, module mới `config/modules/payments.xml`. Muốn root sử dụng module mới cần làm gì?

**Đáp án:** Đặt `payments.xml` đúng runtime resources, bổ sung `<import resource="modules/payments.xml"/>` trong root, đảm bảo XML hợp lệ, bean names không xung đột, dependencies được resolve và integration tests pass.

## 27. Checklist kiểm tra trước khi triển khai

- [ ] XML root có `<beans>` và schema hợp lệ.
- [ ] Mỗi imported file cũng là Spring XML hợp lệ với namespaces cần thiết.
- [ ] Mọi `<import resource="..."/>` được resolve từ đúng file importer hoặc dùng prefix `classpath:`/`file:` rõ.
- [ ] Không dùng `src/main/resources` làm runtime resource path.
- [ ] Không mặc định leading slash trong relative import nghĩa là classpath root.
- [ ] Tránh `../` đặc biệt với classpath-relative locations.
- [ ] Không import cùng XML nhiều lần qua root và bootstrap locations.
- [ ] Không có XML import cycles.
- [ ] Bean names/aliases không xung đột ngoài ý định.
- [ ] Cross-file `ref`, `constructor-arg` và `property` trỏ đúng managed beans/type.
- [ ] Không dựa vào import order để biểu diễn thứ tự business initialization.
- [ ] Không có constructor dependency cycles ở tầng bean.
- [ ] Profiles/property placeholders được set trước khi đọc imports.
- [ ] Optional features được lựa chọn bằng root/profile/bootstrap logic, không giả định import silently skipped.
- [ ] Mọi bean classes và namespace handlers có trong runtime dependencies.
- [ ] Tài nguyên ngoài JAR được quyền đọc và nằm ở path deployment xác định.
- [ ] Wildcard patterns chỉ dùng khi chủ đích là load toàn bộ matches.
- [ ] Integration tests mở đúng root ApplicationContext.
- [ ] Artifact JAR có các XML resources theo paths đã định.
- [ ] Lifecycle và singleton/prototype scopes còn đúng sau modularization.
- [ ] Không tạo ApplicationContext thứ hai chỉ để lấy bean của một XML module.
- [ ] Khi migrate Java configuration, dùng `@ImportResource` đúng trường hợp và loại definitions bị trùng.

## Tổng kết

`<import resource="..."/>` là cách chuẩn để **ghép các tài nguyên Spring XML thành một cấu hình logic**. Nó tải và đăng ký bean definitions từ XML khác, vẫn trong **cùng ApplicationContext/BeanFactory**, cho phép dependencies, scopes, aliases và lifecycle hoạt động xuyên file.

Hai điều dễ nhớ nhất:

- **Relative import** được giải quyết từ **file XML đang thực hiện import**.
- **`classpath:` / `file:`** cho biết resource location cụ thể khi muốn tránh phụ thuộc vào relative layout.

Thiết kế tốt cần một root composition dễ hiểu, module ownership rõ, không import chéo thành vòng, không có duplicate bean names và có integration test từ root thật. Khi dùng Java-centric configuration, `@ImportResource` có thể nạp toàn bộ root XML đó mà không cần tạo container phụ. XML import giúp tổ chức definitions; nó **không** thay thế cho DI, không tự chạy nghiệp vụ và không giải quyết các bean dependency cycles.

## Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — Container Overview, Composing XML-based Configuration Metadata](https://docs.spring.io/spring-framework/reference/6.2/core/beans/basics.html): `<import>`, relative resource paths, leading slash và tránh `../`.
- [Spring Framework 6.2 — Resources](https://docs.spring.io/spring-framework/reference/6.2/core/resources.html): `Resource`, `classpath:`, `file:`, `classpath*:` và application-context resource paths.
- [Spring Framework 6.2 — Composing Java-based Configurations](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/composing-configuration-classes.html): `@Import` và `@ImportResource` khi kết hợp Java/XML.
- [Spring Framework 6.2 — XML Bean Definition Basics](https://docs.spring.io/spring-framework/reference/6.2/core/beans/basics.html): `<beans>`, bean name, class metadata và import resources.
- [Spring Framework 6.2 — Bean Scopes](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html): singleton/prototype scopes xuyên definitions.
- [Spring Framework 6.2 — Dependency Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html): constructor/property wiring và circular dependencies.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="xml-configuration-007">Autowiring XML theo tên, theo kiểu và giới hạn của cơ chế</a>

<details>
<summary>Xem chi tiết</summary>

# Autowiring XML theo tên, theo kiểu và giới hạn của cơ chế trong Spring Framework

## 1. Khi không muốn viết mọi `<property ref="..."/>` bằng tay

Spring Core Container quản lý các đối tượng (beans) và quan hệ phụ thuộc của chúng. Với XML bean definitions, cách rõ ràng nhất để nối hai bean là khai báo tường minh:

```xml
<bean id="inventoryRepository" class="demo.InventoryRepository"/>

<bean id="inventoryService" class="demo.InventoryService">
    <property name="repository" ref="inventoryRepository"/>
</bean>
```

Đây là **XML minh họa**; muốn dùng thực tế, `InventoryService` phải có setter `setRepository(...)` phù hợp và hai class phải tồn tại trên runtime classpath.

Spring cũng cho phép **XML autowiring**: trên một `<bean>` definition, đặt thuộc tính `autowire` để BeanFactory tìm collaborator theo tên property, theo kiểu property hoặc theo constructor. Ví dụ:

```xml
<bean id="repository" class="demo.InventoryRepository"/>

<bean id="inventoryService"
      class="demo.InventoryService"
      autowire="byName"/>
```

Khi object `inventoryService` có writable JavaBean property `repository` (ví dụ `setRepository(...)`), Spring có thể tìm bean có tên chính xác `repository` rồi gọi setter. Không cần viết `<property name="repository" ref="repository"/>` trên bean này.

Nhưng autowiring **không có nghĩa Spring chọn đại một bean phù hợp**, cũng không mặc nhiên thay thế những nguyên tắc về bean name, type, scope hoặc lifecycle. Autowiring XML có các giới hạn rõ: scalar values không được tự đoán, nhiều ứng viên có thể gây lỗi, một số property bị bỏ qua nếu không có ứng viên và quan hệ phụ thuộc trở nên ít tường minh hơn.

Sau bài này, người học có thể:

1. Phân biệt `autowire="no"`, `"byName"`, `"byType"` và `"constructor"`.
2. Hiểu chuẩn JavaBean property name và cách Spring tìm bean theo name/type.
3. Nhận diện missing candidate, ambiguous candidate, primary candidate và reference bị loại khỏi autowiring.
4. Sử dụng `autowire-candidate="false"`, `primary="true"`, `default-autowire-candidates` đúng nghĩa.
5. Biết vì sao `<property>`/`<constructor-arg>` tường minh được ưu tiên.
6. Hiểu collection/array/map autowiring và giới hạn của scalar properties.
7. Phân biệt XML autowiring với annotation `@Autowired` và JavaConfig.
8. Chạy demo Java 17+ / Spring Framework 6.2 có stdout kiểm chứng tên, kiểu, explicit ref và candidate filtering.
9. Biết kiểm tra cấu hình, thiết kế dependency rõ ràng và tránh lỗi runtime.

## 2. Bốn chế độ chính của XML autowiring

Trong Spring Framework 6.2, thuộc tính `autowire` của phần tử `<bean>` chấp nhận bốn mode chính:

| XML mode | Cách tìm dependency | Khi không có bean phù hợp | Khi nhiều bean phù hợp |
| --- | --- | --- | --- |
| `no` | Không tự wire; cần `ref`/`constructor-arg` hoặc cơ chế khác | Property không tự được cấp | Không có lookup tự động cho property ấy |
| `byName` | Tìm bean có **tên trùng tên JavaBean property** | Property không được set bởi byName | Không lấy ngẫu nhiên theo type; name match quyết định |
| `byType` | Tìm bean theo **kiểu của writable property** | Với dependency đơn, có thể bỏ qua setter | Nếu không có một ứng viên thắng, lỗi ambiguity |
| `constructor` | Giải quyết các **tham số constructor** tương tự by-type resolution | Constructor bắt buộc không resolve được thường làm tạo bean thất bại | Không có ứng viên thắng cho dependency đơn thì thất bại |

Chế độ mặc định của `<bean>` là **`no`**. Spring không yêu cầu mọi XML bean tự động được wire.

Ví dụ cú pháp:

```xml
<bean id="manual" class="demo.Service" autowire="no"/>
<bean id="named" class="demo.Service" autowire="byName"/>
<bean id="typed" class="demo.Service" autowire="byType"/>
<bean id="constructed" class="demo.ConstructorService"
      autowire="constructor"/>
```

Đây là những bean definitions **minh họa**; các class chỉ là tên giả định. Trên mỗi bean definition, `autowire` biểu thị **phương pháp Spring tìm dependency**, không khai báo rằng chính bean ấy đã trở thành một dependency bắt buộc của bean khác.

### 2.1. `autowire="no"` không cấm injection

`no` nghĩa là **không tự tìm collaborator bằng cơ chế XML autowiring**. Bạn vẫn có thể cấu hình:

```xml
<bean id="service" class="demo.Service" autowire="no">
    <property name="repository" ref="repository"/>
</bean>
```

hoặc cấp constructor arguments. Đồng thời, một bean có `autowire="no"` vẫn có thể nhận annotation-based injection nếu Spring đã bật các post-processors liên quan và class mang annotations phù hợp.

Không gộp `autowire="no"` với `autowire-candidate="false"`. Thuộc tính thứ nhất nói **bean này có tự nối dependencies của nó bằng XML không**; thuộc tính thứ hai nói **bean này có được chọn làm ứng viên để inject cho bean khác theo type không**.

### 2.2. Autowiring được cấu hình theo từng bean

`<bean id="a" ... autowire="byType"/>` chỉ yêu cầu BeanFactory xem xét dependency của **bean a** theo byType; nó không ép mọi bean còn lại chuyển sang byType.

Có thể dùng `default-autowire="byType"` trên root `<beans>` nếu muốn đặt mặc định cho các bean definitions không chỉ định riêng:

```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       default-autowire="byType">
    <bean id="a" class="demo.A"/>
    <bean id="b" class="demo.B" autowire="no"/>
</beans>
```

Đây là **XML minh họa**, không cần dùng cấu hình này trong bài demo chính. Mặc định cấp root giúp giảm lặp nhưng có thể làm dependency graph khó đọc và gây wiring ngoài ý muốn khi dự án lớn lên. Tài liệu Spring khuyến khích sự tường minh ở các hệ thống lớn.

## 3. `byName`: bean name phải trùng JavaBean property name

Với Java class:

```java
package demo;

public final class InvoiceService {
    private InvoiceRepository repository;

    public void setRepository(InvoiceRepository repository) {
        this.repository = repository;
    }

    public InvoiceRepository getRepository() {
        return repository;
    }
}
```

Spring nhìn nhận một **writable property tên `repository`** bởi method `setRepository(...)`. Nếu XML khai báo:

```xml
<bean id="repository" class="demo.InvoiceRepository"/>
<bean id="invoiceService"
      class="demo.InvoiceService"
      autowire="byName"/>
```

thì Spring tìm bean có tên đúng `repository` và thực hiện setter injection. Đó là ý nghĩa của “theo tên”.

**Lưu ý:** hai fragment ở đây mô tả một class giả định `InvoiceRepository`; chúng chưa tạo thành một project compile độc lập cho đến khi class ấy được định nghĩa.

### 3.1. Tên JavaBean property không nhất thiết là tên field

Với:

```java
private Repository primaryStore;

public void setRepository(Repository value) {
    this.primaryStore = value;
}
```

thì tên property vẫn là `repository` vì method tên `setRepository`. XML `autowire="byName"` tìm bean `repository`, không tìm bean `primaryStore` chỉ vì field có tên như vậy.

Nếu đổi setter thành `setPrimaryStore(...)` thì property name là `primaryStore`. Bean naming convention vì vậy có tác động trực tiếp đến XML byName wiring.

### 3.2. Không có bean trùng tên thì sao?

Giả sử class có setter `setRepository(...)` nhưng registry chỉ chứa bean tên `mainRepository`. Chế độ `byName` không tự nhìn sang kiểu `Repository` rồi lấy `mainRepository` như `byType`. Setter ấy có thể bị bỏ qua, giữ default field value — thường là `null`.

```xml
<bean id="mainRepository" class="demo.InvoiceRepository"/>
<bean id="invoiceService"
      class="demo.InvoiceService"
      autowire="byName"/>
```

Kết quả **không** phải lỗi cấu hình chắc chắn ngay tại refresh. Nếu code nghiệp vụ sau đó giả định repository không null, có thể gặp `NullPointerException` muộn. Hãy viết integration test/validation cho dependency quan trọng.

Cách sửa có chủ đích: đổi bean name thành `repository`, thêm alias `<alias name="mainRepository" alias="repository"/>`, hoặc dùng explicit `<property name="repository" ref="mainRepository"/>`.

### 3.3. byName không có nghĩa “tên class gần giống”

Tên property `repository` không được xem như một gợi ý fuzzy matching. Bean `repositoryImpl` hoặc `Repository` khác chữ hoa/thường **không tự được chọn** chỉ vì tên giống. Nếu không bảo đảm naming convention, dùng explicit ref hoặc byType/qualifier phù hợp.

### 3.4. Bean phải tương thích với setter type

Bean name có thể đúng nhưng instance không phù hợp kiểu setter:

```xml
<bean id="repository" class="java.lang.String">
    <constructor-arg value="oops"/>
</bean>
<bean id="invoiceService"
      class="demo.InvoiceService"
      autowire="byName"/>
```

Nếu setter muốn `InvoiceRepository`, Spring không thể gán một bean `String` vào property kiểu đó theo cách bình thường. Lúc này có thể có `TypeMismatchException`/`BeanCreationException` hoặc lỗi conversion tương ứng. byName chọn **tên**, nhưng vẫn phải đáp ứng hợp đồng kiểu Java khi gán object.

### 3.5. Bean alias cũng tham gia name lookup

Nếu:

```xml
<bean id="mainRepository" class="demo.InvoiceRepository"/>
<alias name="mainRepository" alias="repository"/>
```

thì bean có thể được lookup bằng `repository`; chế độ byName có thể tận dụng alias đã đăng ký. Alias trỏ tới cùng bean definition, không tạo thêm object chỉ vì có hai tên.

## 4. `byType`: một dependency đơn cần một ứng viên thắng

Với:

```java
public class InvoiceService {
    private Repository store;

    public void setStore(Repository store) {
        this.store = store;
    }
}
```

chế độ `autowire="byType"` sẽ tìm các bean kiểu `Repository` phù hợp với parameter của writable property `store`. Tên property `store` **không cần** trùng bean name.

Ví dụ:

```xml
<bean id="dbRepository" class="demo.DatabaseRepository"/>
<bean id="invoiceService"
      class="demo.InvoiceService"
      autowire="byType"/>
```

Nếu `DatabaseRepository implements Repository` và chỉ có một autowire candidate phù hợp, Spring có thể inject bean `dbRepository` vào `setStore(Repository)` mặc dù tên khác `store`.

### 4.1. Không có candidate

Nếu không có bean kiểu `Repository` phù hợp, Spring XML byType cho property đơn thường **không gọi setter**, thay vì luôn ném exception. Class có thể giữ `store == null`. Đây là điểm khác với `@Autowired` mặc định `required=true` hoặc constructor dependency bắt buộc.

**Hệ quả thiết kế:** với dependency thực sự không thể thiếu, đừng dựa duy nhất vào byType setter rồi bỏ qua test. Constructor injection hoặc khai báo explicit dependency giúp lỗi xuất hiện sớm hơn.

### 4.2. Có đúng một candidate

Nếu bean `dbRepository` là ứng viên phù hợp duy nhất, Spring inject nó. Kiểu có thể là interface/superclass mà implementation implement/extends. Không yêu cầu bean name trùng property name.

### 4.3. Có nhiều candidate

Ví dụ:

```xml
<bean id="primaryRepository" class="demo.DatabaseRepository"/>
<bean id="backupRepository" class="demo.InMemoryRepository"/>

<bean id="invoiceService"
      class="demo.InvoiceService"
      autowire="byType"/>
```

Nếu cả hai implement `Repository` và đều là candidates, Spring không được tùy tiện chọn một. Trong cấu hình không có điều kiện lựa chọn khác, bạn có thể gặp `NoUniqueBeanDefinitionException` hoặc `UnsatisfiedDependencyException`/`BeanCreationException` bọc nguyên nhân liên quan.

Các hướng xử lý:
- Dùng `<property name="store" ref="primaryRepository"/>` để chỉ định chính xác.
- Dùng `primary="true"` để chọn một default trong tình huống phù hợp.
- Đặt `autowire-candidate="false"` trên bean không muốn được dùng trong type-based autowiring.
- Đổi thiết kế từ single dependency sang collection nếu consumer thật sự cần nhiều implementations.
- Với annotation-based injection, `@Qualifier` và các cơ chế refinement khác có thể diễn đạt ngữ nghĩa cụ thể.

### 4.4. `primary="true"` là một ưu tiên lựa chọn

```xml
<bean id="fastRepository"
      class="demo.FastRepository"
      primary="true"/>

<bean id="safeRepository"
      class="demo.SafeRepository"/>

<bean id="invoiceService"
      class="demo.InvoiceService"
      autowire="byType"/>
```

Nếu hai implementations cùng kiểu và bean `fastRepository` là primary candidate thích hợp duy nhất, Spring có thể chọn nó cho dependency đơn byType. Không nên gắn `primary="true"` lên nhiều beans ngang nhau rồi mong ambiguity tự biến mất.

**Primary không phải filter collection:** một injection point kiểu `List<Repository>` có thể nhận nhiều repository candidates kể cả khi một bean primary, bởi request đó là “tập các beans”, không phải “một bean mặc định”.

### 4.5. Property name fallback và sự khác biệt với byName

Cơ chế candidate resolution hiện đại có thể dùng nhiều tín hiệu để phân giải single bean dependency, tùy phiên bản Spring và injection point. Không vì vậy coi `autowire="byType"` đồng nghĩa chính xác `byName`: byType xuất phát từ **kiểu của property**, không phải lookup luôn dựa trên tên property; trong trường hợp nhiều ứng viên, cần thiết kế rõ bằng primary/qualifier/explicit ref.

Đối với bài học thực hành, hãy chủ động test hai bean cùng interface thay vì đặt hy vọng vào chi tiết fallback name/parameter-name của một phiên bản Spring.

## 5. `constructor`: dependency được phân giải trước khi tạo object

Với Java class:

```java
package demo;

public final class CheckoutService {
    private final PaymentGateway gateway;

    public CheckoutService(PaymentGateway gateway) {
        this.gateway = gateway;
    }

    public PaymentGateway getGateway() {
        return gateway;
    }
}
```

Và XML:

```xml
<bean id="paymentGateway" class="demo.LocalPaymentGateway"/>
<bean id="checkoutService"
      class="demo.CheckoutService"
      autowire="constructor"/>
```

Spring chọn constructor thích hợp, phân giải các constructor parameters theo type-based resolution và truyền dependency khi tạo instance. Nếu class có một constructor bắt buộc nhận `PaymentGateway` và không có ứng viên phù hợp, context thường thất bại khi tạo bean.

**Sự khác biệt quan trọng:** byName/byType XML chủ yếu tác động đến **writable setters**; constructor mode tác động **constructor arguments**. Đây không phải cùng một phương thức được viết bằng hai cách gọi.

### 5.1. Constructor injection bảo đảm dependency tại creation time

Nếu `CheckoutService` chỉ có constructor `CheckoutService(PaymentGateway gateway)`, nó không thể được Spring tạo thành công mà không cung cấp gateway. Nhờ vậy dependency bắt buộc rõ trong API Java và lỗi cấu hình xuất hiện lúc container tạo bean.

Setter injection cho một field không được set có thể để null và phát hiện lỗi muộn hơn. Đây là lý do nhiều codebases hiện đại ưu tiên constructor injection cho dependencies thiết yếu, ngay cả khi XML vẫn được dùng làm configuration format.

### 5.2. Nhiều constructors và overloads

Nếu class có nhiều constructors, Spring phải chọn signature tương thích với candidates/metadata. XML `autowire="constructor"` có thể xét nhiều constructor candidates; ambiguity hoặc kiểu không chuyển được có thể dẫn đến lỗi, không phải lúc nào cũng chọn “constructor dài nhất” bất kể khả năng resolve.

Khi có overloaded constructors hoặc các types đơn giản như `String`/`int`, hãy khai báo `<constructor-arg index="..." type="..." value="..."/>` hay `ref` tường minh để tránh lệ thuộc vào thuật toán chọn constructor và parameter name metadata.

### 5.3. Constructor autowiring không tự suy ra literals

Nếu constructor `PaymentClient(String endpoint, int timeout)` thì Spring XML `autowire="constructor"` không tự đoán endpoint/timeout từ bean names hoặc sinh giá trị mặc định. Các scalar đơn giản không thuộc nhóm collaborator beans mà autowiring sẽ tự cấp tùy ý.

Ví dụ:

```xml
<bean id="client" class="demo.PaymentClient"
      autowire="constructor">
    <constructor-arg index="0" value="https://api.example.test"/>
    <constructor-arg index="1" value="2500"/>
</bean>
```

Đây là **minh họa** và cần `demo.PaymentClient` với constructor hợp lệ. Hai literal được cấp bằng explicit constructor arguments; autowiring có thể giải quyết những arguments khác nếu signature còn cần bean dependencies phù hợp.

### 5.4. Constructor injection không phải XML bean-method interception

Khác với JavaConfig `@Bean A a(B b)`, XML constructor autowiring chọn Java constructor trên target bean class. Nó không phụ thuộc `@Configuration(proxyBeanMethods=true)` hoặc CGLIB intercept các method Java cấu hình. BeanFactory vẫn quản lý scope/lifecycle theo định nghĩa XML.

## 6. Giới hạn: Spring không autowire các “simple properties”

Theo Spring Reference, cơ chế XML autowiring không tự cấp những thuộc tính đơn giản như primitive values, `String`, `Class` và mảng các simple types chỉ dựa trên tên/kiểu. Đây là chủ ý thiết kế: các giá trị cấu hình có thể mang nhiều ý nghĩa, Spring không thể suy đoán một default string/số đúng cho business logic.

Giả sử class:

```java
public class MailConfig {
    private String sender;
    private int retries;
    private MailRepository repository;

    public void setSender(String sender) { this.sender = sender; }
    public void setRetries(int retries) { this.retries = retries; }
    public void setRepository(MailRepository repository) {
        this.repository = repository;
    }
}
```

Dù XML viết `autowire="byType"`, Spring có thể tự cấp collaborator `MailRepository` nếu tìm được ứng viên phù hợp, nhưng không tự gán “sender” và “retries” chỉ nhờ nhìn property types.

Cần explicit values:

```xml
<bean id="mailConfig" class="demo.MailConfig"
      autowire="byType">
    <property name="sender" value="no-reply@example.test"/>
    <property name="retries" value="3"/>
</bean>
```

Điều tương tự áp dụng `autowire="byName"`: đừng tạo bean tên `sender` kiểu `String` rồi coi đó là mô hình config value injection ổn định, bởi Spring deliberately tránh tự wiring simple properties theo quy tắc XML autowire.

**Không nhầm:** người dùng vẫn có thể viết `<property name="sender" ref="someStringBean"/>` nếu thiết kế cố ý tham chiếu một bean object kiểu `String`; đây là **explicit ref**, khác với tự tìm bằng XML autowire.

### 6.1. `value` được chuyển kiểu theo setter/constructor

`<property name="retries" value="3"/>` chứa XML string literal. Spring conversion infrastructure chuyển nó thành `int` khi setter cần `int`. Nếu viết `value="three"`, có thể gặp type mismatch. Đó là lỗi conversion, không phải lỗi autowiring candidates.

### 6.2. Có thể có optional setter dependencies

Với setter `setAuditSink(AuditSink sink)`, XML byType có thể bỏ qua setter khi không tìm được bean `AuditSink` phù hợp. Trong code, field default null hoặc một NoOp implementation do constructor cung cấp vẫn giữ nguyên. Nếu đó là một feature optional thật, hãy biểu diễn policy rõ và kiểm thử hai trạng thái có/không provider; đừng biến dependency bắt buộc thành optional chỉ để context startup.

## 7. Explicit wiring luôn thắng autowiring

Ví dụ:

```xml
<bean id="fastGateway" class="demo.FastGateway"/>
<bean id="slowGateway" class="demo.SlowGateway"/>

<bean id="service" class="demo.CheckoutService"
      autowire="byType">
    <property name="gateway" ref="slowGateway"/>
</bean>
```

Nếu class có setter `setGateway(Gateway)`, thì explicit property value đã xác định dependency `slowGateway`; Spring **không ghi đè** nó bằng một lookup byType vì bean được đánh dấu `autowire="byType"`.

Quy tắc tương tự với explicit `<constructor-arg>` khi sử dụng constructor autowiring. Điều này rất quan trọng khi nâng cấp cấu hình XML: bạn có thể bật autowire cho phần lớn collaborators, nhưng giữ explicit wiring với dependency có vai trò đặc biệt.

### 7.1. Tại sao explicit configuration vẫn hữu ích?

- Đọc XML biết service đang dùng bean nào.
- Không bị ngầm đổi implementation khi thêm bean cùng interface.
- Có thể chỉ định một bean bị loại khỏi type-based autowiring.
- Hữu ích trong các vùng yêu cầu kiểm soát chặt như payments, transactions, security.
- Công cụ phân tích cấu hình có metadata quan hệ rõ ràng hơn.

Autowiring không thay thế giá trị của việc **tài liệu hóa dependency graph** trong cấu hình; nó chỉ giảm verbosity khi candidate resolution đơn giản và có quy ước ổn định.

## 8. `autowire-candidate="false"`: loại khỏi chọn theo kiểu, không xóa bean

Một thuộc tính ít được hiểu đúng:

```xml
<bean id="backupStore"
      class="demo.BackupStore"
      autowire-candidate="false"/>
```

Bean vẫn được đăng ký trong container và có thể được lookup bằng tên/type trong các API phù hợp. Nhưng BeanFactory **không coi bean đó là candidate cho type-based autowiring** khi tìm collaborator cho injection point.

Theo Spring Framework Reference 6.2, flag này:

- loại bean khỏi **type-based** autowire candidate resolution, bao gồm annotation injection points như `@Autowired`;
- **không cấm explicit ref theo tên**, ví dụ `<property name="store" ref="backupStore"/>`;
- **không ngăn byName autowiring** nếu property có tên trùng `backupStore`;
- không cấm chính bean `backupStore` tự sử dụng `autowire="byType"` để tìm dependencies của nó.

### 8.1. Bảng phân biệt

Giả sử bean `backupStore` có `autowire-candidate="false"`:

| Cách yêu cầu backupStore | Có thể nhận backupStore? | Giải thích |
| --- | --- | --- |
| `autowire="byType"` cho property kiểu `Store` | Không chọn bean bị loại làm candidate | Type-based resolution bỏ qua |
| `autowire="constructor"` cần `Store` | Không chọn bean bị loại làm candidate | Type-based constructor dependency |
| `autowire="byName"` cho property tên `backupStore` | Có | Name matching không bị flag này chặn |
| `<property name="store" ref="backupStore"/>` | Có | Explicit named reference |
| `context.getBean("backupStore")` | Có | Lookup trực tiếp theo tên vẫn hợp lệ |

**Điểm cần tránh:** không dùng `autowire-candidate=false` với ý định cấm truy cập bean ở mọi nơi. Nó không phải security boundary hay access-control mechanism.

### 8.2. `primary="true"` và flag candidate kết hợp

Nếu `Store A` primary nhưng `autowire-candidate=false`, nó không trở thành ứng viên cho type-based injection chỉ nhờ primary. Type-based resolver trước hết phải xét những candidates đủ điều kiện; ưu tiên primary được áp dụng trong bước lựa chọn thích hợp.

Nếu có hai candidates và một bean primary duy nhất, Spring thường có thể chọn primary. Nếu tất cả candidates của một dependency đều bị loại, byType setter có thể không được set và constructor bắt buộc có thể thất bại.

## 9. `default-autowire-candidates` trên root `<beans>`

Spring XML có thể lọc ứng viên theo **bean name patterns** ở cấp root:

```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       default-autowire-candidates="*Repository,core*"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="orderRepository" class="demo.OrderRepository"/>
    <bean id="diagnosticRepository" class="demo.DiagnosticRepository"/>
    <bean id="billingService" class="demo.BillingService"/>
</beans>
```

Đây là XML minh họa; patterns `*Repository` và `core*` mô tả **bean names đủ điều kiện** đối với các definitions không chỉ định `autowire-candidate` riêng. Nó không ngầm quét package hoặc tạo beans mới.

**Lưu ý chính sách:** theo tài liệu Spring, một `autowire-candidate` được chỉ định **tường minh** trên chính `<bean>` có ưu tiên so với patterns mặc định. Nếu muốn cố định một bean bị loại, dùng `autowire-candidate="false"` trực tiếp trên bean definition.

`default-autowire-candidates` khác `default-autowire`:
- `default-autowire` chỉ chế độ **nối dependencies cho beans**.
- `default-autowire-candidates` chỉ nhóm beans **được xét làm type-based candidates**.

Chúng là hai hướng của một mối quan hệ DI, không phải hai cách viết thay thế nhau.

## 10. Collection, array và `Map<String,T>` trong byType/constructor mode

XML `byType` và `constructor` không chỉ biết chọn một object. Khi injection point là array/typed collection, Spring có thể tập hợp **nhiều beans cùng kiểu**:

```java
public void setHandlers(List<Handler> handlers) {
    this.handlers = handlers;
}
```

hoặc:

```java
public void setHandlers(Handler[] handlers) {
    this.handlers = handlers;
}
```

Nếu context có các `Handler` candidates phù hợp, Spring có thể inject toàn bộ vào collection/mảng. Với `Map<String, Handler>`, keys là **bean names** và values là các `Handler` instances tương ứng.

Ví dụ XML:

```xml
<bean id="emailHandler" class="demo.EmailHandler"/>
<bean id="smsHandler" class="demo.SmsHandler"/>

<bean id="pipeline" class="demo.Pipeline"
      autowire="byType"/>
```

Giả sử `Pipeline` có setter `setHandlers(List<Handler>)`, nó có thể nhận cả hai handlers khi types/generics đúng và candidates không bị lọc. Tên property không cần là `emailHandler` hay `smsHandler`.

### 10.1. Collection cần dependency element type rõ ràng

Một raw `List` hoặc raw `Map` thiếu generic type information khiến Spring khó biết beans loại nào cần tập hợp. Để tránh injection không chắc chắn, dùng `List<Handler>` hoặc `Map<String, Handler>` trong Java API.

### 10.2. Primary không bỏ bớt thành viên collection

Nếu `emailHandler` có `primary="true"`, collection `List<Handler>` vẫn có thể chứa cả email và sms; primary chủ yếu hữu ích để **chọn một dependency đơn** giữa nhiều candidates.

### 10.3. autowire-candidate filtering có hiệu lực với collection

Nếu `smsHandler` bị đánh dấu `autowire-candidate="false"`, một collection được **autowired theo type** sẽ không thu thập nó làm candidate, ngay cả khi bean vẫn tồn tại trong context. Ngược lại, một explicit `<list><ref bean="smsHandler"/></list>` vẫn có thể chứa nó.

### 10.4. Không có phần tử và các ngoại lệ

Đừng áp dụng một câu “mọi collection tự nhận list rỗng” cho mọi injection mechanism. XML setter byType, annotation constructor injection và Spring framework version có thể có quy tắc required/optional khác nhau. Nếu nghiệp vụ chấp nhận rỗng, hãy khai báo rõ contract, đặt default collection, hoặc cấp collection tường minh trong XML rồi kiểm thử hành vi thực.

### 10.5. Thứ tự list

Với collection injection, thứ tự có thể chịu ảnh hưởng `Ordered`, `@Order`, `@Priority` hoặc comparator phù hợp. Đừng dựa vào thứ tự ngẫu nhiên của component scanning hoặc bean registration để triển khai pipeline có yêu cầu thứ tự nghiệp vụ; khi thứ tự là hợp đồng, khai báo/tạo assertions rõ.

## 11. XML autowiring khác gì với `@Autowired` và `@Resource`?

Có ba lớp khái niệm cần tách:

1. **XML autowiring**: thuộc tính `autowire` trên XML `<bean>` hướng dẫn BeanFactory chọn setters/constructors theo mode `byName`/`byType`/`constructor`.
2. **`@Autowired`**: annotation-based injection, được xử lý bởi annotation infrastructure như `AutowiredAnnotationBeanPostProcessor`, có lựa chọn candidate và required semantics riêng.
3. **`@Resource`**: annotation (Jakarta) với semantics name-oriented trong các tình huống phù hợp; cần infrastructure xử lý annotation.

Các cơ chế có thể cùng tồn tại trong một context, nhưng không nên coi chúng **đồng nghĩa hoàn toàn**.

| Chủ đề | XML `autowire="byName"` | XML `autowire="byType"` | Annotation `@Autowired` |
| --- | --- | --- | --- |
| Điểm khai báo | `<bean>` definition | `<bean>` definition | Java field/constructor/method/parameter phù hợp |
| Căn cứ đầu tiên | JavaBean property name | Property type | Type-based dependency resolution |
| Thiếu ứng viên cho setter đơn | Có thể bỏ qua setter | Có thể bỏ qua setter | Mặc định `required=true` thường báo lỗi |
| Nhiều ứng viên cùng type | Name match chi phối | Cần ứng viên thắng | Qualifier/primary/name fallback và quy tắc resolver |
| Cần Spring annotations trên domain class? | Không | Không | Có |
| Cần annotation processing infrastructure? | Không cho XML autowiring thuần | Không cho XML autowiring thuần | Có |
| Dùng scalar type tự động | Không tùy tiện | Không tùy tiện | Các simple types thường cần explicit `@Value`/config mechanisms |

### 11.1. Bật `<context:annotation-config/>` có tác động gì?

Nếu một XML file có `<context:annotation-config/>` và namespace `context` được khai báo đúng, Spring sẽ đăng ký hạ tầng để xử lý các annotations trên **managed beans đã được đăng ký**. Nó không tự biến mọi class trên classpath thành bean; cần bean definitions hoặc component scanning.

Ngược lại, **XML `autowire="byType"` không bắt buộc cần `<context:annotation-config/>`**. Demo bài này không dùng annotation injection trên domain classes.

### 11.2. Cơ chế nào nên dùng trong code mới?

Constructor injection thường dễ hiểu hơn cho required dependencies. Với Java 17+ và JavaConfig, có thể dùng một constructor rõ, bean definitions từ `@Component`/`@Bean` và inject theo type/qualifier. Nếu đang bảo trì legacy Spring XML, biết autowire modes giúp đọc/di trú hệ thống chính xác; không nhất thiết phải chuyển toàn bộ XML sang annotations chỉ vì muốn giảm số dòng.

## 12. Ví dụ chạy được với Java 17+ và Spring Framework 6.2

Ví dụ này cố ý **không dùng `@Autowired` hoặc `@ComponentScan`** để phân lập hành vi XML autowiring. Mọi beans được khai báo trong một XML file.

Ta sẽ chứng minh:

- `byName` nhận bean `backupStore` theo đúng tên JavaBean property, **ngay cả khi** backup bị `autowire-candidate="false"`.
- `byType` nhận bean `mainStore` theo interface `Store` vì backup bị loại khỏi type-based candidate resolution.
- `constructor` nhận `mainStore` trước khi constructor chạy.
- Explicit `<property ref="backupStore"/>` override autowiring và vẫn trỏ được tới backup.
- `byType` có thể inject `List<Store>` từ các eligible candidates.
- Simple property `caption` phải được khai báo bằng `value`; một optional `AuditPort` không có matching bean vẫn để null.

Mỗi bean trong example dùng singleton scope mặc định.

### 12.1. Cấu trúc dự án

```text
xml-autowiring-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        ├── java/
        │   └── demo/
        │       └── XmlAutowiringDemo.java
        └── resources/
            └── autowire-context.xml
```

File `settings.gradle`:

```groovy
rootProject.name = 'xml-autowiring-demo'
```

File `build.gradle`:

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.19'
}

application {
    mainClass = 'demo.XmlAutowiringDemo'
}
```

Dùng JDK 17+ và bản Gradle tương thích. `spring-context` kéo theo `spring-beans`/`spring-core`; không cần Spring Boot, server hay database. Example chọn một file Java có các **public static nested classes** để tránh quá nhiều files, nên tên class dùng trong XML sẽ chứa ký tự `$` theo Java binary naming rules.

### 12.2. Mã Java hoàn chỉnh

Tạo `src/main/java/demo/XmlAutowiringDemo.java`:

```java
package demo;

import java.util.List;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public final class XmlAutowiringDemo {

    public interface Store {
        String label();
    }

    public record MainStore() implements Store {
        @Override
        public String label() {
            return "main";
        }
    }

    public record BackupStore() implements Store {
        @Override
        public String label() {
            return "backup";
        }
    }

    public interface AuditPort {
        String audit();
    }

    public static class ByNameConsumer {
        private Store backupStore;

        public void setBackupStore(Store backupStore) {
            this.backupStore = backupStore;
        }

        public Store getBackupStore() {
            return backupStore;
        }
    }

    public static class ByTypeConsumer {
        private Store selected;

        public void setSelected(Store selected) {
            this.selected = selected;
        }

        public Store getSelected() {
            return selected;
        }
    }

    public static class ConstructorConsumer {
        private final Store selected;

        public ConstructorConsumer(Store selected) {
            this.selected = selected;
        }

        public Store getSelected() {
            return selected;
        }
    }

    public static class ExplicitConsumer {
        private Store selected;

        public void setSelected(Store selected) {
            this.selected = selected;
        }

        public Store getSelected() {
            return selected;
        }
    }

    public static class FleetConsumer {
        private List<Store> stores = List.of();

        public void setStores(List<Store> stores) {
            this.stores = stores;
        }

        public List<Store> getStores() {
            return stores;
        }
    }

    public static class SettingsConsumer {
        private String caption = "unset";
        private AuditPort auditPort;

        public void setCaption(String caption) {
            this.caption = caption;
        }

        public void setAuditPort(AuditPort auditPort) {
            this.auditPort = auditPort;
        }

        public String getCaption() {
            return caption;
        }

        public AuditPort getAuditPort() {
            return auditPort;
        }
    }

    public static void main(String[] args) {
        try (var context = new ClassPathXmlApplicationContext(
                "autowire-context.xml")) {

            Store main = context.getBean("mainStore", Store.class);
            Store backup = context.getBean("backupStore", Store.class);

            ByNameConsumer byName =
                    context.getBean(ByNameConsumer.class);
            ByTypeConsumer byType =
                    context.getBean(ByTypeConsumer.class);
            ConstructorConsumer constructor =
                    context.getBean(ConstructorConsumer.class);
            ExplicitConsumer explicit =
                    context.getBean(ExplicitConsumer.class);
            FleetConsumer fleet =
                    context.getBean(FleetConsumer.class);
            SettingsConsumer settings =
                    context.getBean(SettingsConsumer.class);

            System.out.println("byName = " +
                    byName.getBackupStore().label());
            System.out.println("byType = " +
                    byType.getSelected().label());
            System.out.println("constructor = " +
                    constructor.getSelected().label());
            System.out.println("explicit = " +
                    explicit.getSelected().label());
            System.out.println("byNameSameBackup = " +
                    (byName.getBackupStore() == backup));
            System.out.println("byTypeSameMain = " +
                    (byType.getSelected() == main));
            System.out.println("explicitSameBackup = " +
                    (explicit.getSelected() == backup));
            System.out.println("fleet = " +
                    fleet.getStores().stream()
                         .map(Store::label).toList());
            System.out.println("caption = " +
                    settings.getCaption());
            System.out.println("missingAudit = " +
                    (settings.getAuditPort() == null));
        }
    }
}
```

Mã Java trên có đúng một top-level public class. Hai records `MainStore`/`BackupStore` là nested types implement interface `Store`; chúng có no-arg canonical constructors vì records không khai báo components. Những consumer classes có setter hoặc constructor tương ứng để BeanFactory thực hiện đúng mode.

### 12.3. XML bean definitions đầy đủ

Tạo `src/main/resources/autowire-context.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="mainStore"
          class="demo.XmlAutowiringDemo$MainStore"/>

    <bean id="backupStore"
          class="demo.XmlAutowiringDemo$BackupStore"
          autowire-candidate="false"/>

    <bean id="byName"
          class="demo.XmlAutowiringDemo$ByNameConsumer"
          autowire="byName"/>

    <bean id="byType"
          class="demo.XmlAutowiringDemo$ByTypeConsumer"
          autowire="byType"/>

    <bean id="constructor"
          class="demo.XmlAutowiringDemo$ConstructorConsumer"
          autowire="constructor"/>

    <bean id="explicit"
          class="demo.XmlAutowiringDemo$ExplicitConsumer"
          autowire="byType">
        <property name="selected" ref="backupStore"/>
    </bean>

    <bean id="fleet"
          class="demo.XmlAutowiringDemo$FleetConsumer"
          autowire="byType"/>

    <bean id="settings"
          class="demo.XmlAutowiringDemo$SettingsConsumer"
          autowire="byType">
        <property name="caption" value="XML-configured"/>
    </bean>
</beans>
```

Lưu ý các tên class có `$`: đó là **Java binary names của nested classes**, không phải lỗi đánh máy. Nếu refactor thành top-level classes, tên XML phải thay bằng tên fully qualified mới.

### 12.4. Lệnh chạy và stdout

Trong thư mục dự án:

```bash
gradle run --quiet
```

Nếu có Gradle Wrapper:

```bash
./gradlew run --quiet
```

Windows PowerShell:

```powershell
.\gradlew.bat run --quiet
```

**Các dòng do chương trình in ra:**

```text
byName = backup
byType = main
constructor = main
explicit = backup
byNameSameBackup = true
byTypeSameMain = true
explicitSameBackup = true
fleet = [main]
caption = XML-configured
missingAudit = true
```

Đây là stdout nghiệp vụ từ `System.out.println`. Tùy cấu hình Gradle/logging, môi trường có thể in thêm messages không thuộc các dòng trên.

### 12.5. Phân tích từng kết quả

**`byName = backup`:** `ByNameConsumer` có writable property `backupStore` thông qua `setBackupStore(Store)`, nên byName tìm đúng bean tên `backupStore`. Thuộc tính `autowire-candidate="false"` không ngăn name-based wiring, theo Spring documentation.

**`byType = main`:** `ByTypeConsumer` cần một `Store` trong setter `setSelected(Store)`. `mainStore` là eligible candidate; `backupStore` bị loại khỏi type-based autowiring, nên Spring chọn main.

**`constructor = main`:** `ConstructorConsumer(Store selected)` nhận Store từ constructor autowiring. Dù backup vẫn tồn tại trong registry, nó không phải candidate kiểu Store cho injection point này.

**`explicit = backup`:** `ExplicitConsumer` được đặt `autowire="byType"` nhưng XML đồng thời khai báo `<property name="selected" ref="backupStore"/>`. Explicit ref có ưu tiên; dù backup excluded from type-based autowiring, vẫn lookup theo tên và inject thành công.

**Ba dòng `Same... = true`:** identity `==` chứng minh cùng những managed singleton references, không phải copy object mới từ tên/kiểu. Bean scopes mặc định singleton và mỗi context chỉ có một `MainStore`, một `BackupStore`.

**`fleet = [main]`:** setter `setStores(List<Store>)` nhận các Store candidates phù hợp. Vì backup bị loại khỏi type-based candidate selection, list có một phần tử main. Không liên quan tới property name `stores`.

**`caption = XML-configured`:** property đơn giản kiểu `String` được cấp bằng explicit `<property value="..."/>`, không phải byType tự tìm String bean.

**`missingAudit = true`:** không có bean `AuditPort`, nên byType setter dành cho optional-looking property `auditPort` không được gọi; field vẫn null. Nếu ứng dụng thật không chấp nhận null, cần sửa contract và wiring cho chắc chắn.

### 12.6. Biến thể 1: hai candidates và ambiguity

Thay XML `backupStore` để bỏ `autowire-candidate="false"`, rồi giữ `byType` và `constructor` như cũ:

```xml
<bean id="mainStore"
      class="demo.XmlAutowiringDemo$MainStore"/>
<bean id="backupStore"
      class="demo.XmlAutowiringDemo$BackupStore"/>
<bean id="byType"
      class="demo.XmlAutowiringDemo$ByTypeConsumer"
      autowire="byType"/>
```

Đây là **fragment cho bài thử** (không phải file hoàn chỉnh). Trong tình huống hai eligible beans cùng `Store` mà không có lựa chọn thắng, tạo `byType` có thể thất bại với ambiguity. Tình huống này nên được chạy trong **XML test riêng**, không sửa trực tiếp demo chính nếu muốn giữ stdout ở mục 12.4.

### 12.7. Biến thể 2: đặt primary

Nếu cả hai Store được phép làm candidates, bạn có thể:

```xml
<bean id="mainStore"
      class="demo.XmlAutowiringDemo$MainStore"
      primary="true"/>

<bean id="backupStore"
      class="demo.XmlAutowiringDemo$BackupStore"/>
```

Trong dependency đơn theo type, Spring có thể chọn `mainStore` là primary duy nhất. Tuy nhiên, nếu cần collection `List<Store>`, thông thường cả hai candidates vẫn là phần tử của collection (primary không lọc backup ra khỏi list).

### 12.8. Biến thể 3: byName thiếu tên tương ứng

Đổi bean `backupStore` thành `spareStore` nhưng giữ setter `setBackupStore(Store)`. Khi không có alias `backupStore` và không có explicit property, byName không tìm thấy tên tương ứng nên field có thể giữ null. Ngược lại, byType không quan tâm tên `spareStore` nếu kiểu và candidate status đúng.

### 12.9. Biến thể 4: explicit wiring vẫn hoạt động

Giữ `autowire-candidate="false"` của backup, nhưng trong một bean khác khai báo `<property name="selected" ref="backupStore"/>`. Reference đó vẫn hợp lệ, vì flag không hủy quyền lookup trực tiếp theo tên.

### 12.10. Biến thể 5: loại cả mainStore

Đặt thêm `autowire-candidate="false"` lên `mainStore`. Lúc này byType setter `selected` có thể không được cấp, nhưng `constructor` cần Store bắt buộc có thể làm context fail khi Spring cố tạo bean. Thử nghiệm này giúp thấy khác biệt về required semantics giữa setter byType và constructor.

## 13. Lỗi thường gặp và cách xử lý

| Lỗi hoặc triệu chứng | Nguyên nhân thường gặp | Cách sửa |
| --- | --- | --- |
| Setter byName không chạy | Không có bean name trùng property name | Kiểm tra setter và `id`/`alias`; dùng explicit ref nếu cần |
| byType gặp hai implementations | Nhiều eligible candidates và không có một ứng viên thắng | `primary`, candidate filtering, qualifier/explicit ref |
| Constructor fails khi dependency thiếu | Constructor argument bắt buộc không resolve được | Đăng ký bean phù hợp hoặc explicit constructor-arg |
| Bean bị excluded mà byName vẫn nhận | `autowire-candidate=false` chỉ ảnh hưởng type-based selection | Đây là behavior thiết kế; cần kiểm soát byName riêng |
| Explicit ref vẫn lấy bean excluded | Explicit named reference không bị flag chặn | Đây là behavior hợp lệ, không phải lỗi |
| `value="3"` không tự inject bằng byType | Scalar simple property không thuộc XML autowiring | Dùng `<property value>`/`<constructor-arg value>` |
| `NotWritablePropertyException` | Không có setter JavaBean đúng tên/signature | Kiểm tra property descriptor/setter visibility |
| `TypeMismatchException` | Bean/giá trị không phù hợp setter type | Kiểm tra class/interface và converters |
| `NoUniqueBeanDefinitionException` | Resolver không chọn được single candidate | Loại ambiguity có chủ đích |
| `NoSuchBeanDefinitionException` | Dependency bean chưa đăng ký/đã bị lọc | Kiểm tra imports, profiles, conditions, XML resources |
| Autowiring không làm field private được set | XML setter mode không giống annotation field injection | Tạo setter/constructor injection phù hợp |
| Cùng một property vừa explicit vừa autowire | Explicit metadata có ưu tiên | Xác định một nơi cấu hình chịu trách nhiệm |
| Collection bỏ mất implementation | Bean đó bị loại bởi `autowire-candidate`/patterns | Kiểm tra candidate flags |
| Đổi XML sau context refresh không cập nhật bean | Autowiring xảy ra lúc bean creation | Cần reload/rebuild hoặc cơ chế runtime config riêng |
| Property type là raw `List` | Spring không biết rõ element type | Dùng `List<Interface>` hoặc explicit collection |
| byName phụ thuộc tên setter, không phụ thuộc field | JavaBeans property naming | Kiểm tra `setXxx` và introspection |
| Mọi bean đều autowired ngoài ý muốn | `default-autowire` ở root không được quản lý | Giữ default `no` hoặc review scope cấu hình |
| Bean có `autowire="no"` vẫn được inject vào bean khác | Không cấm nó làm candidate | Nếu cần loại by-type selection, dùng `autowire-candidate=false` |
| Bean primary vẫn bị excluded | Không là candidate trước bước primary preference | Đừng dùng primary để vượt flag excluded |
| Spring AOP proxy và raw bean khác identity | Container có post-processors/proxying | So sánh references theo lifecycle/proxy policy phù hợp |

### 13.1. Phân loại lỗi theo giai đoạn

Lỗi Spring XML có thể xảy ra ở ba giai đoạn lớn:

1. **XML parse/XSD:** sai `autowire` attribute, sai namespace URI, thẻ không đóng, XSD không resolve.
2. **Bean definition / candidate resolution:** không có candidates, nhiều candidates, property không writable, constructor không phân giải được.
3. **Bean creation / business runtime:** constructor ném exception, setter logic lỗi, field null vì optional autowire bị bỏ qua, bean chạy vào method sử dụng dependency chưa có.

Đừng nhìn thấy `BeanCreationException` là lập tức kết luận “Spring không đọc XML”. Nó có thể đã đọc XML rất tốt, nhưng không thể giải quyết dependency hoặc class logic bị lỗi sau đó.

### 13.2. Kỹ thuật debug

**Bước 1:** Kiểm tra application context thực sự load file `autowire-context.xml` nào. Nhiều file cùng tên trên classpath có thể gây nhầm.

**Bước 2:** Xem bean name và implementation type đang tồn tại. `context.getBeanNamesForType(Store.class)` có thể giúp thấy beans theo type, nhưng lưu ý **truy vấn type names không đồng nhất với lựa chọn autowire candidates**: một excluded bean có thể vẫn tồn tại và được liệt kê.

**Bước 3:** Kiểm tra property name từ `setXxx` và kiểu parameter. Với `byName`, tên rất quan trọng; với `byType`, type và eligible candidates quan trọng.

**Bước 4:** Kiểm tra `primary`, `autowire-candidate`, `default-autowire-candidates` và những `<property ref>` explicit.

**Bước 5:** Nếu dependency bắt buộc, hãy viết một integration test assert rằng field/constructor dependency không null và đúng implementation — thay vì chỉ nhìn context refresh không lỗi.

**Bước 6:** Xem root cause của stack trace. Ambiguity, missing bean, missing setter, bad type conversion và cycle là những nguyên nhân khác nhau.

### 13.3. Bean name và type tự định nghĩa khác nhau

Một bean XML có thể được đặt tên `legacyJdbcStore` và class implement `Store`. Nếu setter tên `setStore`:

- byName tìm `store`, không tự tìm `legacyJdbcStore`;
- byType có thể chọn `legacyJdbcStore` nếu là candidate theo `Store`.

Một thay đổi bean name có thể phá byName nhưng không phá byType; thay đổi implementation type có thể phá byType nhưng không phá name matching (sau đó có thể vấp type compatibility). Đây là trade-off cần cân nhắc khi chọn mode.

## 14. Hạn chế thiết kế và chiến lược dùng thực tế

### 14.1. Giảm cấu hình có đánh đổi

Autowiring làm XML ngắn hơn, nhưng quan hệ giữa beans không còn xuất hiện tường minh ở nơi đọc config. Người review phải biết setter/constructor API và cả registry candidates mới suy luận được dependency thực.

Khi project phát triển thêm một bean cùng interface, một byType dependency từng unique có thể trở thành ambiguous hoặc chọn implementation khác do primary/candidate changes. Với hệ thống cần behavior dự đoán cao, cấu hình explicit giúp giảm bất ngờ.

### 14.2. Không trộn mode tùy tiện

Một module dùng explicit `ref`, module khác dùng byName, module thứ ba dùng byType là hợp lệ về mặt Spring, nhưng không có coding conventions rõ thì dễ gây nhầm. Có thể chọn:

- Constructor injection cho dependencies không thể thiếu.
- Explicit XML references cho collaborators có nhiều implementations.
- byType cho các utility beans có candidate duy nhất và ranh giới đã được test.
- byName khi naming convention cố định/ổn định.
- Explicit values/config properties cho strings, numbers và secrets.

Đây là gợi ý, không phải luật bắt buộc. Ưu tiên tính minh bạch và integration tests.

### 14.3. Type-based injection không phải an ninh

`autowire-candidate=false` giúp lọc dependency resolution; nó không ngăn một bean khác chủ động lấy object bằng `context.getBean("backupStore")`, cũng không ngăn bean được khai báo explicit `ref`. Nếu đối tượng nhạy cảm, cần kiểm soát quyền/cấu trúc module, không chỉ candidate flags.

### 14.4. Cần phân biệt startup với runtime

Một dependency setter được Spring inject khi bean được tạo. Nếu bạn đăng ký thêm một bean `Store` sau khi context đã refresh, singleton `ByTypeConsumer` không tự chọn lại candidate hay gọi lại setter. Muốn runtime routing, thiết kế một abstraction/provider/factory riêng, không dùng XML autowiring như một bộ định tuyến động.

### 14.5. Không dùng XML autowiring làm lời giải cho circular dependencies

Setter cycles đôi khi có thể được Spring giải quyết trong một số singleton configurations, nhưng chúng mang rủi ro lifecycle, proxy, state partially initialized. Constructor cycles thường không thể giải quyết bằng autowiring thông thường. Hãy refactor dependency graph có chiều rõ ràng, dùng events hoặc chia service responsibilities.

### 14.6. Không đồng nhất candidate selection giữa Spring versions

Spring có những cải tiến thuật toán resolve nhiều ứng viên qua các phiên bản (ví dụ Spring 6.2 xử lý thêm trường hợp qualifier/name/priority). Cần kiểm tra **phiên bản framework thực tế** thay vì dựa vào một blog dùng Spring 2.x. Tài liệu bài này đối chiếu Spring Framework 6.2.19; các hành vi cụ thể vẫn nên có integration tests trong dự án của bạn.

## 15. Kiểm thử XML autowiring có chủ đích

### 15.1. Happy-path identity tests

Đối với demo ở mục 12, có thể bổ sung assertions:

```java
if (byName.getBackupStore() != backup) {
    throw new AssertionError("byName did not select backup");
}

if (byType.getSelected() != main) {
    throw new AssertionError("byType did not select main");
}

if (explicit.getSelected() != backup) {
    throw new AssertionError("Explicit ref was not honored");
}

if (fleet.getStores().size() != 1) {
    throw new AssertionError("Expected one eligible Store");
}
```

Đây là **trích đoạn test** dùng các biến đã khai báo trong `XmlAutowiringDemo.main`. Test giúp bảo vệ assumptions về type/name/candidate status khi XML được chỉnh sửa.

### 15.2. Negative test cho ambiguity

Có thể dùng một XML fixture riêng chứa hai eligible `Store` beans và một `ByTypeConsumer`. Khi chạy:

```java
try (var context = new ClassPathXmlApplicationContext(
        "ambiguous-context.xml")) {
    throw new AssertionError("Expected ambiguity");
} catch (org.springframework.beans.BeansException expected) {
    System.out.println("Ambiguous configuration rejected");
}
```

Đây là **trích đoạn test**, không phải phần `main` của demo; cần resource `ambiguous-context.xml` riêng. Trong test nghiêm túc, kiểm tra đúng type/root cause của exception để không lầm một lỗi XML parse với lỗi ambiguity. Không nên chỉ catch mọi exception rồi coi test đạt.

### 15.3. Missing dependency test

Với `byType` setter, nếu không có `Store`, test nên kiểm tra getter còn null theo contract; với constructor dependency bắt buộc, test nên kỳ vọng failure. Phân biệt kết quả hai modes là trọng tâm.

### 15.4. Test candidate exclusion

Với bean `backupStore` excluded:
- `context.containsBean("backupStore")` phải đúng.
- `context.getBean("backupStore", Store.class)` vẫn hoạt động.
- byName property `backupStore` nhận object đó.
- byType dependency Store nhận main.
- explicit ref tới backup vẫn hoạt động.

Nếu cả năm assertions cùng đạt, bạn đã kiểm tra đúng sự khác nhau giữa **tồn tại trong registry** và **đủ điều kiện để autowire theo type**.

## 16. Bài tập có lời giải

### Bài 1 — byName dùng tên gì?

Class có field `primaryRepository` và setter `setRepository(Repository)`. XML bật `autowire="byName"`. Spring tìm bean tên nào?

**Đáp án:** `repository`, theo writable JavaBean property name. Field name không trực tiếp điều khiển byName.

### Bài 2 — byType có cần bean name giống setter?

Setter `setStore(Store)`, registry có một bean `jdbcMain` implement `Store`. XML `autowire="byType"` có thể inject nó không?

**Đáp án:** Có, khi đó là eligible candidate duy nhất/phù hợp. Tên `jdbcMain` không cần trùng `store`.

### Bài 3 — byName không có bean name tương ứng

Setter `setNotifier(Notifier)`, XML chỉ có bean tên `smsNotifier`. Với `byName`, setter có chắc được gọi không?

**Đáp án:** Không. Không có bean tên `notifier` thì byName có thể bỏ qua property; dùng alias/explicit ref hoặc đổi mode khi hợp lý.

### Bài 4 — byType có hai candidates

Có hai eligible `PaymentGateway` beans và không có ứng viên thắng rõ. Điều gì có thể xảy ra?

**Đáp án:** Ambiguous dependency; Spring có thể ném exception khi tạo bean. Không chọn ngẫu nhiên theo thứ tự XML.

### Bài 5 — primary

Hai `Store` candidates, một bean có `primary="true"` duy nhất. Với dependency đơn kiểu `Store`, Spring có thể chọn bean nào?

**Đáp án:** Bean primary nếu hợp lệ trong tập candidates; primary không làm bean khác biến mất khỏi registry.

### Bài 6 — candidate false

Bean `backupStore` có `autowire-candidate="false"`. Một property byName tên `backupStore` có thể vẫn được inject không?

**Đáp án:** Có. Flag này giới hạn type-based autowiring, không chặn byName match.

### Bài 7 — explicit ref tới excluded bean

`backupStore` bị loại khỏi type-based autowiring. `<property name="store" ref="backupStore"/>` có còn hợp lệ không?

**Đáp án:** Có. Explicit reference theo tên không bị candidate flag vô hiệu hóa.

### Bài 8 — simple property

Bean `Settings` có `setTimeout(int)`. Root XML đặt `default-autowire="byType"`. Timeout có tự nhận giá trị 3000 không?

**Đáp án:** Không. Simple property không được tự suy đoán. Cần explicit `<property name="timeout" value="3000"/>` hoặc cơ chế config rõ ràng.

### Bài 9 — constructor dependency thiếu

`CheckoutService(Store store)` chỉ có một constructor, XML dùng `autowire="constructor"` nhưng không có Store bean. Context có chắc tạo service với store null không?

**Đáp án:** Không. Constructor dependency bắt buộc không resolve được thường khiến bean creation thất bại.

### Bài 10 — explicit property và autowire

Bean `service` vừa có `autowire="byType"` vừa có `<property name="gateway" ref="backupGateway"/>`. Khi Spring tạo bean, có được tự thay backup bằng primary Gateway khác không?

**Đáp án:** Không. Explicit property setting override autowiring cho property đó.

### Bài 11 — List<Handler>

Có ba eligible bean `Handler`. Injection point là `List<Handler>` qua byType. Có nhất thiết chỉ chọn một `@Primary` không?

**Đáp án:** Không. Collection có thể chứa cả ba eligible handlers; primary không lọc collection.

### Bài 12 — Map<String,Handler>

Vì sao Spring dùng `String` keys trong autowired map?

**Đáp án:** Keys biểu diễn **bean names**, values là các handler bean references. Cần generic types rõ để resolver biết element type.

### Bài 13 — Candidate false có hủy bean không?

Bạn gọi `context.getBean("backupStore")` trên excluded backup bean. Có bắt buộc lỗi `NoSuchBeanDefinitionException` không?

**Đáp án:** Không. Bean definition vẫn tồn tại; flag không phải cơ chế xóa bean hay security control.

### Bài 14 — default-autowire-candidates

Root `<beans default-autowire-candidates="*Repository">` có nghĩa chỉ những bean có tên kết thúc `Repository` mới được đăng ký trong context không?

**Đáp án:** Không. Nó giới hạn **candidate selection** theo pattern cho type-based autowiring, không chặn registration của bean khác.

### Bài 15 — annotation và XML

Một bean `autowire="no"` nhưng class có `@Autowired` và infrastructure xử lý annotation đã bật. Có thể được annotation injection không?

**Đáp án:** Có. `autowire=no` tắt XML autowire mode, không tự cấm annotation BeanPostProcessor.

### Bài 16 — thời điểm cấp bean

Bạn thay XML file lúc ứng dụng đã refresh context và đang giữ singleton `service`. Setter `setStore` có tự được chạy lại không?

**Đáp án:** Không. Autowiring thông thường diễn ra trong quá trình bean creation/population; muốn reload phải có cơ chế riêng.

### Bài 17 — scope prototype

Một singleton Service nhận một prototype Store vào setter khi nó được tạo. Mỗi lần `service.find()` có tự nhận Store mới không?

**Đáp án:** Không. Service giữ reference đã inject; muốn lookup mới theo mỗi lần dùng, cần provider/factory phù hợp.

### Bài 18 — vì sao ưu tiên constructor injection?

Một Service không thể chạy đúng nếu thiếu Repository, nhưng setter byType có thể để repository null khi thiếu candidate. Cách cải thiện?

**Đáp án:** Khai báo dependency qua constructor bắt buộc, hoặc explicit XML reference và kiểm thử/init validation. Điều quan trọng là contract “repository bắt buộc” được bảo đảm trước khi service sử dụng.

## 17. Checklist áp dụng vào dự án thật

- Bean cần autowire đang dùng đúng `autowire` mode chưa?
- Với `byName`, đã xác nhận JavaBeans property name và bean `id`/`alias` chính xác chưa?
- Với `byType`, đã xác nhận số lượng eligible candidates, types, `primary` và candidate flags chưa?
- Với `constructor`, đã xem constructor signatures, arguments scalar và overloads chưa?
- Simple `String`/`int`/`boolean` properties có explicit values không?
- Những dependency đặc biệt cần một implementation cụ thể đã dùng `ref`/`constructor-arg` tường minh chưa?
- Có bean nào `autowire-candidate=false` nhưng vẫn được byName/explicit ref lấy theo thiết kế không?
- `default-autowire` và `default-autowire-candidates` có bị nhầm không?
- Với collection injection, generic element type và tập eligible beans có đúng không?
- Bean mới được thêm có làm một byType dependency trước đó trở thành ambiguous không?
- Một setter dependency bắt buộc bị thiếu có thể khiến field null muộn không?
- Test context có assert bean identity, implementation type và behavior chưa?
- Nếu XML có `<context:annotation-config/>`, có hiểu annotation injection khác XML autowiring không?
- Scopes/proxies/lazy/circular references có làm khác đi thời điểm resolve dependency không?
- Khi debug, đã đọc root cause thay vì gộp mọi exception thành “XML lỗi” chưa?

## 18. Kết luận

**Autowiring XML** cho phép BeanFactory tìm collaborators từ bean registry thay vì yêu cầu tất cả `<property ref="..."/>` xuất hiện tường minh. `byName` khớp **JavaBean property name với bean name**. `byType` khớp **property type với autowire candidate beans**. `constructor` giải quyết **constructor arguments theo type-based dependency resolution**. `no` là mặc định và không cấm explicit DI.

Giới hạn lớn nhất: **simple properties không được tự đoán**; missing byType setter candidate có thể để property trống; nhiều eligible beans cho dependency đơn có thể gây ambiguity; và wiring ngầm làm dependency graph khó nhìn hơn. Spring cung cấp `primary`, `autowire-candidate` và `default-autowire-candidates` để tinh chỉnh selection, nhưng **explicit ref luôn giúp chỉ ra dependency cụ thể**.

Một quy tắc rất đáng nhớ: **`autowire-candidate="false"` không xóa bean, không chặn byName và không chặn explicit ref**; nó chủ yếu loại bean khỏi type-based autowiring. Bài demo kiểm chứng đồng thời cả ba đường resolve đó.

Trong code mới, constructor injection cho dependencies bắt buộc và cấu hình tường minh cho những lựa chọn nghiệp vụ quan trọng thường cho kết quả dễ bảo trì hơn. Khi duy trì hệ thống Spring XML, hiểu chính xác từng autowire mode sẽ giúp tránh những lỗi chỉ xuất hiện khi có thêm một bean cùng interface, khi thay bean name hoặc khi thay candidate-selection rules.

### Tài liệu chính thức

- [Spring Framework 6.2 — Autowiring Collaborators](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-autowire.html).
- [Spring Framework 6.2 — Dependency Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html).
- [Spring Framework 6.2 — Using `@Autowired`](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired.html).
- [Spring Framework 6.2 — Spring Bean Scopes](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html).
- [Spring Framework 6.2 — Fine-tuning Annotation-based Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-qualifiers.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="xml-configuration-008">Bật annotation-config và component-scan</a>

<details>
<summary>Xem chi tiết</summary>

# Bật annotation-config và component-scan trong Spring XML

## 1. Vấn đề cần giải quyết

Spring IoC Container quản lý các bean thông qua **bean definitions**. Khi chuyển dần từ XML sang annotations, hai cấu hình thường được nhắc tới là:

```xml
<context:annotation-config/>
<context:component-scan base-package="com.example"/>
```

Hai phần tử này rất dễ bị nhầm là tương đương, nhưng chúng xử lý **hai nhu cầu khác nhau**:

- **`<context:annotation-config/>`**: thêm các hạ tầng xử lý annotation để Spring nhận biết `@Autowired`, `@Value`, một số Jakarta lifecycle annotations và cấu hình Java **trên những bean mà context đã đăng ký**. Nó **không quét classpath** để tìm mọi class có `@Component`.
- **`<context:component-scan base-package="..."/>`**: tìm những class được đánh dấu `@Component` hoặc stereotypes phù hợp trong package, **đăng ký chúng làm beans**, đồng thời **mặc định kích hoạt chức năng annotation-config**. Vì thế thường không cần viết cả hai phần tử cùng lúc.

Ví dụ: class `OrderService` được XML khai báo qua `<bean class="...OrderService"/>` và có `@Autowired OrderRepository repository`. Nếu container thiếu `AutowiredAnnotationBeanPostProcessor`, field injection đó có thể không diễn ra. Nhưng khi đã bật annotation-config và có bean `OrderRepository` phù hợp, Spring sẽ inject dependency. Ngược lại, một class `@Service` chỉ có mặt trên classpath mà chưa được scan/import/register thì không tự trở thành Spring bean.

### Mục tiêu học tập

Sau bài này, bạn sẽ:

1. Khai báo Spring XML Beans/Context namespaces và schemas hợp lệ.
2. Phân biệt **component discovery**, **bean registration**, **annotation injection** và **bean lifecycle**.
3. Biết `annotation-config` đăng ký những processor nào và **không** làm việc gì.
4. Hiểu `component-scan` chọn những stereotype candidates nào, quét package/subpackages ra sao và đặt bean name như thế nào.
5. Giải thích vì sao `component-scan` mặc định bao hàm `annotation-config`.
6. Phân biệt `annotation-config="false"` với `use-default-filters="false"`.
7. Sử dụng `include-filter`/`exclude-filter` và biết giới hạn của scan.
8. Chạy ví dụ Java 17+, Spring Framework 6.2.9 đối chiếu **bốn XML contexts**.
9. Xử lý xung đột bean definitions, sai package, missing dependency, duplicate components và vấn đề parent-child context.
10. Lựa chọn cấu hình phù hợp khi chuyển dần từ XML-only sang annotation-based configuration.

**Phạm vi:** Spring Framework **Core Container**. Không giả định Spring Boot, HTTP server, transaction proxy, persistence provider hoặc scheduler tự hoạt động chỉ nhờ hai phần tử này.

## 2. Mô hình: bốn giai đoạn khác nhau

```text
A. CÓ JAVA CLASS TRÊN CLASSPATH
       |
       +-- XML <bean> trực tiếp
       |
       +-- <context:component-scan> nhận @Component candidates
       |
       +-- @Configuration / @Import / registration APIs (khi đã bật)
       |
       v
B. BEAN DEFINITIONS TRONG REGISTRY
       |
       | Processor được đăng ký bởi annotation-config
       | hoặc bởi component-scan mặc định
       v
C. BEANFACTORY TẠO INSTANCE VÀ INJECT
       |
       | @Autowired, @Value, các lifecycle callbacks hợp lệ
       v
D. MANAGED BEAN TRONG CONTEXT
```

Một annotation như `@Component` là metadata trên **class**, không tự đăng ký class trong mọi ApplicationContext. Một annotation như `@Autowired` là metadata trên **injection point**, cần BeanPostProcessor phù hợp và ứng viên dependency đã được đăng ký.

### Phân biệt hai câu hỏi

**Bean có được đăng ký không?**

```java
boolean exists = context.containsBean("orderService");
```

Nếu false, kiểm tra XML `<bean>`, `component-scan`, `@Import` hoặc registration APIs.

**Bean đã được inject không?**

```java
OrderService service = context.getBean(OrderService.class);
boolean ready = service.hasRepository();
```

Đoạn này là snippet minh họa; `OrderService` phải có method `hasRepository()` trong dự án. Nếu bean tồn tại mà dependency null, kiểm tra annotation processors và cách bean được tạo, không chỉ package scanning.

## 3. Cú pháp namespace XML chuẩn

`<context:annotation-config/>` và `<context:component-scan/>` thuộc **Spring Context namespace**, không phải Spring Beans namespace mặc định.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/context
           https://www.springframework.org/schema/context/spring-context.xsd">

    <context:annotation-config/>

    <bean id="orderRepository"
          class="com.example.OrderRepository"/>
    <bean id="orderService"
          class="com.example.OrderService"/>

</beans>
```

Trong ví dụ cấu trúc này, các class phải có trên runtime classpath và có constructors/wiring hợp lệ. Các namespace URI và schemaLocation giúp XML parser hiểu `<beans>`, `<bean>` và các phần tử `context:`.

### Lỗi XML thường thấy

Nếu báo lỗi prefix `context` chưa được khai báo: thiếu `xmlns:context`. Nếu báo phần tử không được phép trong `<beans>`: kiểm tra schema pair, vị trí/nesting, dependencies và Spring versions. Đừng thay namespaced element thành tên trần `<component-scan>` mà không hiểu XML namespace.

Spring cung cấp cơ chế tìm schema resources phù hợp với các modules trên classpath; các URL schema **không phải** application API endpoints hoặc nơi định nghĩa service nghiệp vụ.

## 4. annotation-config đăng ký hạ tầng gì?

Theo tài liệu Spring 6.2, `<context:annotation-config/>` kích hoạt các post-processors phục vụ annotation-based configuration trong **ApplicationContext hiện tại**. Những thành phần chính được mô tả gồm:

- `ConfigurationClassPostProcessor`: xử lý các class cấu hình đã được biết, bao gồm metadata `@Configuration`, `@Bean`, `@Import`.
- `AutowiredAnnotationBeanPostProcessor`: xử lý `@Autowired`, `@Value` và các annotation tương ứng được hỗ trợ trên injection points.
- `CommonAnnotationBeanPostProcessor`: liên quan `@Resource`, `@PostConstruct`, `@PreDestroy` khi Jakarta annotations APIs phù hợp hiện diện.
- `PersistenceAnnotationBeanPostProcessor` khi có các dependency Jakarta Persistence cần thiết.
- `EventListenerMethodProcessor` cùng các processor hỗ trợ event-listener metadata.

**Điểm tinh tế:** hạ tầng chính xác còn phụ thuộc classpath/Spring version; không nên kết luận rằng mọi processor của mọi framework stack chắc chắn được tạo dù thiếu thư viện hoặc infrastructure chuyên biệt.

### 4.1. Bean XML có @Autowired

```java
package com.example;

import org.springframework.beans.factory.annotation.Autowired;

public class PaymentService {
    @Autowired
    private PaymentRepository repository;

    public boolean isReady() {
        return repository != null;
    }
}
```

Đây là snippet minh họa, cần một `PaymentRepository` bean hợp lệ. Nếu XML khai báo:

```xml
<context:annotation-config/>
<bean id="paymentRepository"
      class="com.example.PaymentRepository"/>
<bean id="paymentService"
      class="com.example.PaymentService"/>
```

thì processor có thể inject `PaymentRepository`. **Không cần `@Component` trên PaymentService** vì XML đã đăng ký class làm bean.

### 4.2. @Value và property placeholders

```java
@Value("${client.timeout:30}")
private int timeout;
```

Đây là snippet minh họa trong một Spring-managed bean. Annotation processor giúp xử lý injection, còn `Environment`/property source infrastructure cung cấp giá trị và conversion. Muốn lấy file property ngoài hệ thống mặc định, có thể dùng `<context:property-placeholder location="classpath:app.properties"/>` hoặc hạ tầng property source thích hợp.

**Không nhầm:** đọc một properties file không tự tạo bean definition; bật `@Value` không tự dựng những external service clients được mô tả bằng properties.

### 4.3. Lifecycle annotations

`@PostConstruct` và `@PreDestroy` thuộc Jakarta annotation APIs trong Spring Framework 6+. Java 17 không tự tích hợp tất cả Jakarta APIs; khi viết chương trình có annotations đó cần khai báo dependency như `jakarta.annotation-api` tương thích.

Nếu class được tạo bằng `new` ngoài container, `@PostConstruct` **không tự chạy** chỉ vì class có annotation. Nó cần Spring quản lý lifecycle với processor phù hợp.

### 4.4. Java @Configuration vẫn cần được đăng ký

`<context:annotation-config/>` có thể hỗ trợ xử lý metadata của class cấu hình Java **đã được đăng ký trong context**, chẳng hạn:

```xml
<context:annotation-config/>
<bean class="com.example.JavaFeatureConfiguration"/>
```

Giả sử `JavaFeatureConfiguration` có `@Configuration`/`@Bean` hợp lệ. Đây là cách dùng kết hợp XML và Java configuration. Nhưng `annotation-config` **không tự tìm mọi `@Configuration` trên classpath**: nguồn class ở đây là `<bean>` XML.

## 5. Những điều annotation-config không làm

Nó **không phải công tắc bật toàn bộ Spring Framework**. Chỉ ghi:

```xml
<context:annotation-config/>
```

không có nghĩa:

- Tất cả class `@Service` trong mọi package được tự động scan.
- `@Repository` luôn được exception translation nếu thiếu hạ tầng thích hợp.
- `@Transactional` chắc chắn có transaction AOP; còn cần transaction manager, proxy infrastructure/`<tx:annotation-driven/>` phù hợp.
- `@Scheduled` tự chạy mà không có scheduling support.
- `@Async` tự chạy không cần async processing/executor.
- `@Controller` tự nhận HTTP requests trong một Core Container chưa có Spring MVC/DispatcherServlet infrastructure.
- Java objects tự `new` bên ngoài container bỗng được inject.
- Processors trong child context tự áp lên beans thuộc parent context.

**Chốt ý:** annotation-config bật **phần xử lý annotations**, không bật **tìm class**.

## 6. component-scan tìm và đăng ký class nào?

```xml
<context:component-scan base-package="com.example.orders"/>
```

Yêu cầu scanner tìm candidate classes trong package `com.example.orders` và các subpackages. Mặc định các stereotype candidates thường gồm:

- `@Component`.
- `@Service`.
- `@Repository`.
- `@Controller`.
- `@Configuration` (meta-annotated bằng `@Component`).
- Custom annotations được meta-annotated bằng `@Component`; trong môi trường có thêm JSR-330/Jakarta annotations, những filters được Spring hỗ trợ còn có thể tác động.

Ví dụ:

```text
com.example
├── orders
│   ├── OrderService.java          <-- thuộc package scan
│   └── persistence
│       └── OrderRepository.java   <-- thuộc subpackage scan
└── billing
    └── BillingService.java        <-- KHÔNG thuộc base-package orders
```

Để scanner chọn `OrderService` hoặc `OrderRepository`, class phải phù hợp với stereotype/filter và là candidate component hợp lệ. Package name trong Java source, runtime classpath, scan root và filters đều phải khớp.

### Tên bean của scanned classes

Một class `@Service public class OrderService` được scan thường có bean name `orderService` theo naming convention; có thể chỉ định `@Service("primaryOrderService")`.

Nếu hai classes ở packages khác nhau cùng tên ngắn `OrderService`, bean names tự sinh có thể va chạm. Đừng dựa vào Java package khác nhau để bảo đảm bean name duy nhất khi scan nhiều packages vào **một** context.

### Component scan không nhất thiết tạo instance ngay

Scanner tạo bean definitions; Spring tạo instances sau theo scope/lifecycle. Mặc định non-lazy singleton thường được khởi tạo trong refresh. `@Lazy`, `@Scope` và dependencies có thể thay đổi timing; không suy ra scanner tự gọi constructor ngay khi bắt gặp annotation.

## 7. Vì sao component-scan mặc định bao hàm annotation-config?

Theo Spring Reference, `<context:component-scan>` **implicitly enables** hầu hết chức năng của `<context:annotation-config/>`. Điều này làm hai ví dụ khác nhau:

**Chỉ processing:**

```xml
<context:annotation-config/>
<bean id="manualService" class="demo.ManualService"/>
```

Spring có thể xử lý `@Autowired` trong `manualService` nếu bean dependency hiện diện, **nhưng không tìm** bất kỳ `@Component` class nào ngoài XML.

**Discovery + processing:**

```xml
<context:component-scan base-package="demo.scanned"/>
<bean id="manualService" class="demo.ManualService"/>
```

Scanner tìm beans trong `demo.scanned`; processors được kích hoạt mặc định nên có thể inject `@Autowired` cả vào những scanned components **và** `manualService` đã được XML đăng ký.

| Cấu hình XML | Đăng ký stereotype candidates | Xử lý @Autowired trên XML bean |
| --- | --- | --- |
| Chỉ `<bean>` | Không | Không, nếu không có processor từ nguồn khác |
| `annotation-config` + `<bean>` | Không | Có, khi có dependency hợp lệ |
| `component-scan` + `<bean>` | Có | Có, mặc định |
| `component-scan annotation-config="false"` + `<bean>` | Có | Không do scanner tự thêm processor; có thể vẫn hoạt động nếu processor đã được đăng ký ở nguồn khác |
| Cả scan mặc định và annotation-config | Có | Có; dòng annotation-config thường dư thừa |

### Component scan có chỉ inject scanned beans không?

Không. Processors đã đăng ký hoạt động với những Spring-managed beans tương ứng trong cùng context, bất kể bean được đăng ký từ XML hay scan. **Cơ chế registration khác cơ chế injection**.

## 8. annotation-config="false" nghĩa là gì?

```xml
<context:component-scan
    base-package="demo.scanned"
    annotation-config="false"/>
```

Phần tử này **vẫn scan package** và đăng ký candidate components. Flag chỉ nói rằng **phần tử scanner đó không tự đăng ký những annotation processors mà nó mặc định sẽ thêm**.

Vì vậy, một class được scan có `@Autowired` field có thể vẫn được tạo mà field chưa được inject. Tuy nhiên, nếu có `<context:annotation-config/>` từ XML khác hoặc có `AutowiredAnnotationBeanPostProcessor` được đăng ký bằng một đường khác, injection vẫn có thể chạy.

**Không nên phát biểu tuyệt đối rằng annotation-config="false" sẽ làm mọi annotation ngừng hoạt động**. Điều đúng là nó tắt *auto-registration từ scanner đó*.

### Cảnh báo về tên attribute giống nhau

- `annotation-config="false"`: vẫn chọn stereotype candidates, nhưng không tự thêm annotation processing infrastructure.
- `use-default-filters="false"`: tắt **các candidate filters mặc định** để scanner không tự chọn mọi `@Component`/`@Service`; chỉ những classes match include filters phù hợp mới được đăng ký.

Một flag nói về **post-processors**, flag còn lại nói về **cách tìm bean definitions**.

## 9. Ví dụ hoàn chỉnh Java 17+ so sánh bốn XML configurations

Demo dùng **Spring Framework 6.2.9**, **Java 17+**, **Gradle** và `ClassPathXmlApplicationContext`. Để phân biệt từng tình huống không có nhiễu từ hạ tầng đăng ký chung, ta mở bốn contexts **độc lập và tuần tự**:

1. `plain.xml` — chỉ XML `<bean>`; không scan, không annotation-config.
2. `annotations.xml` — bật annotation-config nhưng không scan.
3. `scanned.xml` — bật component-scan mặc định.
4. `scan-no-annotations.xml` — bật scan nhưng dùng `annotation-config="false"`.

Trong production, nếu các modules cần chia sẻ beans, thường nên nạp các nguồn vào **một context**. Bốn contexts ở đây chỉ là các test fixtures để quan sát rõ từng cơ chế.

### 9.1. Cấu trúc file

```text
xml-context-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        ├── java/
        │   └── demo/
        │       ├── App.java
        │       ├── Formatter.java
        │       ├── ManualConsumer.java
        │       └── scanned/
        │           └── ScannedConsumer.java
        └── resources/
            ├── plain.xml
            ├── annotations.xml
            ├── scanned.xml
            └── scan-no-annotations.xml
```

Java types là public top-level classes; từng file ở đúng package.

### 9.2. Gradle setup

**`settings.gradle`**

```groovy
rootProject.name = 'xml-context-demo'
```

**`build.gradle`**

```groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.9'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'demo.App'
}
```

Dependency này mang `spring-beans`/`spring-core` và hạ tầng annotations cần cho `@Autowired`/`@Component`. Không cần Spring Boot hay Jakarta annotation APIs bổ sung vì chương trình không dùng `@PostConstruct`.

### 9.3. Formatter.java — bean chỉ được đăng ký qua XML

**`src/main/java/demo/Formatter.java`**

```java
package demo;

import java.util.Objects;

public final class Formatter {
    private final String prefix;

    public Formatter(String prefix) {
        this.prefix = Objects.requireNonNull(prefix);
    }

    public String format(String value) {
        return prefix + ":" + value;
    }
}
```

Class không có `@Component`. XML `<bean id="formatter">` bên dưới là nguồn bean definition duy nhất.

### 9.4. ManualConsumer.java — XML bean có field @Autowired

**`src/main/java/demo/ManualConsumer.java`**

```java
package demo;

import org.springframework.beans.factory.annotation.Autowired;

public final class ManualConsumer {
    @Autowired
    private Formatter formatter;

    public boolean wired() {
        return formatter != null;
    }

    public String describe() {
        return formatter.format("manual");
    }
}
```

`ManualConsumer` không có `@Component`, nhưng cả bốn XML files đều đăng ký `<bean id="manualConsumer" class="demo.ManualConsumer"/>`. Nếu context không có processor hỗ trợ field `@Autowired`, field này giữ `null`.

**Chỉ gọi `describe()` trong những contexts đã xác nhận `wired()==true`** để không tự tạo `NullPointerException`.

### 9.5. ScannedConsumer.java — chỉ được tìm bởi component scan

**`src/main/java/demo/scanned/ScannedConsumer.java`**

```java
package demo.scanned;

import demo.Formatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("scannedConsumer")
public final class ScannedConsumer {
    @Autowired
    private Formatter formatter;

    public boolean wired() {
        return formatter != null;
    }

    public String describe() {
        return formatter.format("scan");
    }
}
```

Class có stereotype và bean name explicit. Trong `annotations.xml` không có scanner hoặc XML `<bean>` cho class này nên nó không phải bean. Trong `scanned.xml` và `scan-no-annotations.xml`, scanner tìm thấy class.

### 9.6. plain.xml — không annotation-config/scan

**`src/main/resources/plain.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="formatter" class="demo.Formatter">
        <constructor-arg value="xml"/>
    </bean>
    <bean id="manualConsumer" class="demo.ManualConsumer"/>
</beans>
```

Ở đây không có annotation processors từ XML này, cũng không có scan package. `ManualConsumer` được tạo nhưng không field-autowired, `ScannedConsumer` vắng mặt.

### 9.7. annotations.xml — có annotation-config, không scan

**`src/main/resources/annotations.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/context
           https://www.springframework.org/schema/context/spring-context.xsd">

    <context:annotation-config/>

    <bean id="formatter" class="demo.Formatter">
        <constructor-arg value="xml"/>
    </bean>
    <bean id="manualConsumer" class="demo.ManualConsumer"/>
</beans>
```

`AutowiredAnnotationBeanPostProcessor` xử lý field của `ManualConsumer` đã khai báo XML. `ScannedConsumer` vẫn chưa được register.

### 9.8. scanned.xml — scan ngầm bật annotation processing

**`src/main/resources/scanned.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/context
           https://www.springframework.org/schema/context/spring-context.xsd">

    <context:component-scan base-package="demo.scanned"/>

    <bean id="formatter" class="demo.Formatter">
        <constructor-arg value="xml"/>
    </bean>
    <bean id="manualConsumer" class="demo.ManualConsumer"/>
</beans>
```

Vừa có XML beans vừa có scanned bean. Processors được scanner tự đăng ký, nên cả hai consumer fields `@Autowired` đều được inject từ cùng bean `Formatter` singleton.

### 9.9. scan-no-annotations.xml — scan nhưng không tự thêm processors

**`src/main/resources/scan-no-annotations.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/context
           https://www.springframework.org/schema/context/spring-context.xsd">

    <context:component-scan
        base-package="demo.scanned"
        annotation-config="false"/>

    <bean id="formatter" class="demo.Formatter">
        <constructor-arg value="xml"/>
    </bean>
    <bean id="manualConsumer" class="demo.ManualConsumer"/>
</beans>
```

`ScannedConsumer` vẫn được scanner đăng ký thành bean. Nhưng vì fixture này không có một nguồn khác đăng ký `AutowiredAnnotationBeanPostProcessor`, cả hai fields `@Autowired` không được xử lý.

### 9.10. App.java

**`src/main/java/demo/App.java`**

```java
package demo;

import demo.scanned.ScannedConsumer;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public final class App {
    public static void main(String[] args) {
        try (ClassPathXmlApplicationContext plain =
                     new ClassPathXmlApplicationContext("plain.xml")) {

            ManualConsumer consumer = plain.getBean(ManualConsumer.class);
            System.out.println("plain.manual-wired=" + consumer.wired());
            System.out.println("plain.scanned-count="
                    + plain.getBeanNamesForType(ScannedConsumer.class).length);
        }

        try (ClassPathXmlApplicationContext annotations =
                     new ClassPathXmlApplicationContext("annotations.xml")) {

            ManualConsumer consumer =
                    annotations.getBean(ManualConsumer.class);
            System.out.println("annotation.manual-wired=" + consumer.wired());
            System.out.println("annotation.manual-message="
                    + consumer.describe());
            System.out.println("annotation.scanned-count="
                    + annotations.getBeanNamesForType(
                            ScannedConsumer.class).length);
        }

        try (ClassPathXmlApplicationContext scanned =
                     new ClassPathXmlApplicationContext("scanned.xml")) {

            ManualConsumer manual = scanned.getBean(ManualConsumer.class);
            ScannedConsumer component =
                    scanned.getBean(ScannedConsumer.class);

            System.out.println("scan.manual-wired=" + manual.wired());
            System.out.println("scan.scanned-count="
                    + scanned.getBeanNamesForType(
                            ScannedConsumer.class).length);
            System.out.println("scan.scanned-wired=" + component.wired());
            System.out.println("scan.scanned-message="
                    + component.describe());
        }

        try (ClassPathXmlApplicationContext scanOff =
                     new ClassPathXmlApplicationContext(
                             "scan-no-annotations.xml")) {

            ManualConsumer manual = scanOff.getBean(ManualConsumer.class);
            ScannedConsumer component =
                    scanOff.getBean(ScannedConsumer.class);

            System.out.println("scan-off.manual-wired=" + manual.wired());
            System.out.println("scan-off.scanned-count="
                    + scanOff.getBeanNamesForType(
                            ScannedConsumer.class).length);
            System.out.println("scan-off.scanned-wired="
                    + component.wired());
        }
    }
}
```

### 9.11. Lệnh chạy

Trong thư mục `xml-context-demo`:

```bash
gradle run --quiet
```

Nếu có Gradle Wrapper:

```bash
./gradlew run --quiet
```

Trên Windows:

```bat
gradlew.bat run --quiet
```

Dùng `javac App.java` độc lập không đủ vì cần compile các files khác và cung cấp Spring JAR dependencies. Gradle thiết lập classpath, compile và copy resources.

### 9.12. Stdout dự kiến chính xác từ chương trình

```text
plain.manual-wired=false
plain.scanned-count=0
annotation.manual-wired=true
annotation.manual-message=xml:manual
annotation.scanned-count=0
scan.manual-wired=true
scan.scanned-count=1
scan.scanned-wired=true
scan.scanned-message=xml:scan
scan-off.manual-wired=false
scan-off.scanned-count=1
scan-off.scanned-wired=false
```

Đây là **12 dòng được chủ động in từ Java**. Build/runtime có thể xuất hiện thêm log từ framework; chúng không phải một phần của các println này.

### 9.13. Giải thích kết quả

**plain**: XML đăng ký `Formatter`/`ManualConsumer` nhưng không có processor/scanner, nên `manual-wired=false` và `scanned-count=0`.

**annotations**: `annotation-config` bật processor; `ManualConsumer` được inject và trả `xml:manual`. Nhưng không có scan, nên `ScannedConsumer` không thành bean.

**scanned**: `component-scan` vừa đăng ký `ScannedConsumer` vừa mặc định bật processor; các consumer có `@Autowired` đều được inject `Formatter` từ XML.

**scan-no-annotations**: scanner vẫn đăng ký `ScannedConsumer` (count 1), nhưng không tự thêm processors; cả `ManualConsumer` và `ScannedConsumer` đều có field null trong fixture này. Main chỉ kiểm tra `wired()`, không gọi `describe()` trong context này.

**Đừng nhầm bốn contexts của demo là bốn contexts trong một ứng dụng.** Chúng là phép thử độc lập để tách ảnh hưởng từng XML element.

## 10. include-filter, exclude-filter và use-default-filters

Scanner mặc định dùng những stereotype filters. Khi cần kiểm soát candidates, có thể thêm filters.

### 10.1. Exclude một nhóm classes

```xml
<context:component-scan base-package="com.example.orders">
    <context:exclude-filter type="regex"
        expression="com\.example\.orders\.testsupport\..*"/>
</context:component-scan>
```

Đây là snippet cấu trúc: điều kiện chỉ có tác dụng nếu package và class names match pattern thật. Ký tự `\` trong XML attribute là một phần của regex, không phải Java string escape.

### 10.2. Chỉ tìm custom annotation

```xml
<context:component-scan base-package="com.example.jobs"
                        use-default-filters="false">
    <context:include-filter type="annotation"
        expression="com.example.jobs.WorkerTask"/>
</context:component-scan>
```

Với đoạn này, scanner không mặc định chọn mọi `@Component`, mà tìm classes có annotation `WorkerTask` theo điều kiện include. `WorkerTask` phải là một annotation type có thật. `use-default-filters="false"` không tự tắt annotation processors của scanner: đó là flag khác.

### 10.3. Các filter types phổ biến

| `type` | `expression` | Ý nghĩa |
| --- | --- | --- |
| `annotation` | Fully qualified annotation class | Chọn/loại annotations |
| `assignable` | Fully qualified base class/interface | Chọn class có quan hệ type phù hợp |
| `regex` | Regular expression cho class name | Quy tắc theo tên class |
| `aspectj` | AspectJ type pattern | Lọc theo pattern type |
| `custom` | TypeFilter implementation class | Custom predicate |

Một policy có thể kết hợp include/exclude filters. Nhưng filter không thay cho module packaging hay dependency governance; scanner vẫn dựa trên tập classes có trên classpath.

### 10.4. Ví dụ phân biệt hai flags

**Chỉ tắt processors:**

```xml
<context:component-scan
    base-package="com.example"
    annotation-config="false"/>
```

Class `@Component` vẫn có thể được đăng ký.

**Chỉ tắt mặc định chọn stereotypes:**

```xml
<context:component-scan
    base-package="com.example"
    use-default-filters="false">
    <context:include-filter type="annotation"
        expression="com.example.SpecialComponent"/>
</context:component-scan>
```

Annotation processors vẫn được scanner tự đăng ký theo mặc định, nhưng chỉ candidates match filter mới được tìm theo policy đã nêu.

## 11. Kết hợp XML bean với scanned bean trong cùng context

Trong thực tế, có thể dùng XML cho thư viện vendor/client cần cấu hình constructor, còn business services dùng annotation:

```xml
<context:component-scan base-package="com.example.checkout"/>

<bean id="paymentGateway"
      class="com.example.vendor.ExternalGateway">
    <constructor-arg value="https://gateway.example.test"/>
</bean>
```

Một `@Service` trong package scan có thể constructor-inject `PaymentGateway` nếu type/qualifier phù hợp. Ngược lại, XML `<bean>` có thể `<constructor-arg ref="scannedService"/>` để tham chiếu bean được quét và đăng ký.

**Quy tắc:** injection diễn ra dựa trên tập bean definitions của context, không cần cả hai bên phải có cùng nguồn khai báo. Nếu scanner không quét vendor class mà XML đã đăng ký vendor bean, đó vẫn là candidate hợp lệ.

### Tránh đăng ký cùng class hai lần

Giả sử `EmailService` có `@Service` nằm trong package đang scan, nhưng XML lại khai báo:

```xml
<bean id="emailService" class="com.example.EmailService"/>
```

Có nguy cơ trùng name/definition hoặc hai candidates, tùy cơ chế registration/overriding. Nếu XML cần override configuration chủ động, thiết kế theo chính sách rõ; đừng để duplicate chỉ vì quên xóa definition cũ lúc migration.

### Multiple beans cùng type

Nếu hai beans cùng implement `NotificationSender`, injection theo type có thể không tìm được ứng viên duy nhất. Dùng `@Qualifier`, `@Primary` hoặc chỉ đăng ký implementation cần thiết.

## 12. Các giới hạn của annotation processing

### 12.1. Object phải được Spring quản lý

Một `new ManualConsumer()` do Java code tự tạo **không tự được inject** chỉ vì XML có `annotation-config`. BeanPostProcessors được Spring gọi theo lifecycle quản lý của BeanFactory; không chạy như Java annotation interpreter cho mọi object.

### 12.2. Thư viện Jakarta cần tồn tại

Trong Java 17/Spring 6+, nếu dùng `jakarta.annotation.PostConstruct` hoặc `jakarta.inject.Inject`, khai báo đúng Jakarta dependencies. Có `@Autowired` không đồng nghĩa mọi package Jakarta đều có sẵn.

### 12.3. Transaction, cache, async, scheduling cần infrastructure riêng

- `@Transactional`: cần transaction manager/transaction advice infrastructure.
- `@Cacheable`: cần cache infrastructure.
- `@Async`: cần async-enabled annotation processors/executor phù hợp.
- `@Scheduled`: cần scheduling support.
- `@RequestMapping`: cần Spring MVC/web request mapping infrastructure.

Cả annotation-config lẫn component-scan trong Core Container **không bảo đảm** các chức năng trên tự vận hành.

### 12.4. @Repository exception translation không tự động trong mọi project

`@Repository` đánh dấu vai trò persistence/DAO. Exception translation có thể cần `PersistenceExceptionTranslationPostProcessor` và các adapters/infrastructure thích hợp. Không nên suy luận cứ scan `@Repository` là mọi exception từ JDBC/JPA đều được đổi sang một dạng cụ thể.

## 13. Parent-child ApplicationContexts

Một ứng dụng web truyền thống có thể có root context và child servlet context. `<context:annotation-config/>` chỉ đăng ký processors **vào context chứa nó**.

```text
Root ApplicationContext
  ├── dataSource
  ├── orderRepository
  └── orderService

Child WebApplicationContext
  ├── orderController
  └── viewResolver
```

Nếu chỉ bật annotation-config ở child, nó không tự trở thành BeanPostProcessor của root context. Root beans cần processor ở root khi dùng annotation injection. Bean visibility giữa parent/child cũng không giống một registry duy nhất: child thường lookup bean từ parent, parent không nhìn thấy bean chỉ có ở child.

Đây là một lỗi khó chẩn đoán khi thấy XML child có `annotation-config` nhưng service root vẫn không được xử lý injection như mong đợi.

## 14. Những lỗi phổ biến

### Lỗi 1: Bật annotation-config nhưng quên component-scan

`@Component` không tự được tìm từ classpath. Nếu type chưa được XML khai báo hoặc import/register, ứng dụng có thể báo thiếu bean.

### Lỗi 2: Quét sai base-package

`com.example.order` khác `com.example.orders`. Phải khớp Java package declaration và runtime classpath.

### Lỗi 3: Scan quá rộng

Quét root package lớn có thể kéo những test components, optional configuration, adapters hoặc services chưa muốn bật vào context.

### Lỗi 4: Khai báo cả annotation-config và component-scan mặc định rồi nghĩ sẽ scan nhiều hơn

Hai phần tử không cộng thêm phạm vi scan. `component-scan` mặc định đã bật processors; annotation-config tường minh thường dư.

### Lỗi 5: Hiểu sai annotation-config="false"

Scanner vẫn tìm/register classes. Flag chỉ ngăn scanner tự thêm annotation processors. Nếu processor đến từ nguồn khác, injection vẫn có thể hoạt động.

### Lỗi 6: Nhầm với use-default-filters="false"

Flag này thay cách chọn candidates, không tương đương disable annotation processors.

### Lỗi 7: Bean được XML đăng ký nhưng @Autowired vẫn null

Kiểm tra object có do Spring tạo không, annotation processor có mặt không và có dependency bean phù hợp trong context không.

### Lỗi 8: Hai beans cùng type gây NoUniqueBeanDefinitionException

Đăng ký nhiều implementations có thể hợp lệ, nhưng injection point cần qualifier/primary hoặc chọn một bean duy nhất.

### Lỗi 9: Hai classes cùng bean name

Name collision có thể xảy ra dù hai classes ở packages khác nhau. Dùng scoped scan boundaries và explicit names/qualifiers khi cần.

### Lỗi 10: Dùng @PostConstruct thiếu Jakarta APIs

Java 17 không tự có mọi Jakarta annotation API. Thêm dependency đúng version và processor tương ứng.

### Lỗi 11: Cho rằng scan tạo bean instance ngay

Scan chủ yếu đăng ký definitions; BeanFactory tạo instances theo scope, lazy/depends-on/dependencies.

### Lỗi 12: Bật @Transactional chỉ bằng annotation-config

Transaction support là stack riêng. Cần transaction manager/advice configuration phù hợp.

### Lỗi 13: Có @Controller bean là tự có HTTP endpoint

Controller bean không đồng nghĩa đã có Spring MVC DispatcherServlet và mapping infrastructure.

### Lỗi 14: Mơ hồ context ownership

Bean trong root context không tự được xử lý bởi processors chỉ có ở child context.

### Lỗi 15: Test bằng new object rồi kết luận processor không hoạt động

Object không thuộc Spring lifecycle sẽ không tự được inject. Dùng integration test thật với ApplicationContext.

### Lỗi 16: Bỏ annotation-config trên component-scan nhưng quên filters

`use-default-filters="false"` cần include filters phù hợp; nếu không scanner có thể không tìm bean mong muốn.

### Lỗi 17: Component nằm trong JAR nhưng scanner không nhìn thấy

Kiểm tra dependency được đóng gói trên runtime classpath, directory/module entries và classpath scanning resources.

### Lỗi 18: Dùng @Value nhưng không có property source đúng

Annotation injection hỗ trợ `@Value`, nhưng giá trị vẫn cần nguồn property/placeholder resolution hợp lệ.

## 15. Quy trình debug theo từng bước

Khi `@Autowired` không hoạt động, hãy hỏi theo thứ tự:

1. **Bean tồn tại chưa?** Kiểm tra `getBeanNamesForType` và `containsBean`.
2. **Bean được đăng ký bằng cách nào?** XML `<bean>`, scan, `@Bean` hay programmatic registration?
3. **Object có do Spring tạo không?** Không dùng object tự `new` để kết luận DI.
4. **Processor có tồn tại trong đúng context không?** `annotation-config` hoặc `component-scan` mặc định, trừ khi bị tắt.
5. **Dependency candidate có tồn tại không?** Nếu không, scan/package/registration source nào thiếu?
6. **Có nhiều candidates cùng type không?** Xem `@Qualifier` hoặc `@Primary`.
7. **Có filter hoặc profile/condition làm bean vắng mặt không?**
8. **Có root/child contexts khác nhau không?**
9. **Có sai namespace/JAR/annotation package không?**
10. **Có hiểu nhầm một stack chuyên biệt là Core annotation-config không?**

### Đo trực tiếp processors (khi cần)

Trong một context đang hoạt động:

```java
String[] names = context.getBeanNamesForType(
        org.springframework.beans.factory.annotation
            .AutowiredAnnotationBeanPostProcessor.class);
System.out.println(names.length);
```

Đây là đoạn chẩn đoán minh họa; số lượng chính xác phụ thuộc infrastructure của context/version. Nó không thay thế test thực tế rằng service đã được inject đúng dependency.

### Unit test vs integration test

Unit test tạo trực tiếp một service với constructor-injected dependency để kiểm tra business logic. Integration test nên dựng đúng XML context để kiểm tra scanning, bean registration, annotation processing và lifecycle. Đừng dùng `new` rồi mong field `@Autowired` sẽ được Spring tự điền.

## 16. Lộ trình chuyển XML legacy sang annotations

Một chiến lược có kiểm soát:

1. Ban đầu XML đăng ký hầu hết service/repository/adapters qua `<bean>`.
2. Bật `<context:annotation-config/>` để sử dụng annotation injection trên **beans đã biết** trong khi vẫn giữ XML registrations.
3. Chọn một feature package rõ ràng và bật `<context:component-scan>` cho các components mới.
4. Vì scan đã bật processors theo mặc định, có thể bỏ annotation-config tường minh nếu không có lý do đặc biệt cần giữ.
5. Khi class chuyển hoàn toàn sang `@Component` và được scan, xóa XML `<bean>` trùng tương ứng để tránh xung đột.
6. Test bean names/types, qualifiers, scopes và lifecycle trước/sau migration.
7. Chỉ mở rộng base-package khi có kiểm thử xác nhận không cuốn unwanted components/configurations.
8. Giữ XML cho một số vendor beans cần constructor/factory logic nếu điều đó hữu ích; Spring không bắt buộc chuyển toàn bộ sang annotations.

**Lưu ý:** chuyển nguồn bean definitions không nên làm thay đổi tài nguyên do ai sở hữu. Đặc biệt, đừng tạo hai HTTP clients, DataSources hoặc pools chỉ vì cả XML và scan đang cùng đăng ký một implementation.

## 17. Checklist thiết kế XML cấu hình

- XML có đủ `beans` và `context` namespaces/schema locations.
- Biết rõ những classes do XML đăng ký và classes do scanner tìm.
- `annotation-config` được bật khi cần annotation processing nhưng chưa có scanner/infrastructure khác.
- Nếu đã dùng `component-scan` mặc định thì không thêm processors trùng không cần thiết.
- `base-package` bao phủ đúng feature và subpackages.
- `annotation-config="false"` không bị nhầm là tắt scanning.
- `use-default-filters="false"` đi kèm include filters khi yêu cầu.
- XML `<bean>` và scanned components không tạo duplicates ngoài ý định.
- Injection candidates có type/name/qualifier rõ ràng.
- Parent/child context boundaries được kiểm tra nếu có.
- Các annotations chuyên biệt có hạ tầng tương ứng, không tự suy ra từ Core Container.
- Code có integration tests khởi tạo ApplicationContext và test injection thực tế.
- Không dùng tên bean từ scan như hợp đồng bất biến nếu không có naming policy.
- Startup logs được đọc kèm with bean-definition/injection evidence, không chỉ nhìn thấy “scan succeeded”.

## 18. Câu hỏi ôn tập

**Câu 1.** `annotation-config` có tự scan package để tìm `@Service` không?

**Đáp:** Không. Nó đăng ký annotation processors phục vụ beans đã được context đăng ký.

**Câu 2.** `component-scan` có mặc định bật annotation-config functionality không?

**Đáp:** Có. Đó là behavior được Spring Reference mô tả, nên thường không cần viết cả hai.

**Câu 3.** XML bean có field `@Autowired`, cần bản thân class có `@Component` không?

**Đáp:** Không, nếu bean đã đăng ký từ XML và context có processor thích hợp.

**Câu 4.** Class mang `@Component` nhưng chưa scan/import/register, có tự xuất hiện trong context không?

**Đáp:** Không.

**Câu 5.** `annotation-config="false"` trên scanner làm scanner ngừng tìm classes không?

**Đáp:** Không. Nó ngăn scanner tự thêm annotation processors; candidate detection vẫn có thể diễn ra.

**Câu 6.** `use-default-filters="false"` làm gì?

**Đáp:** Tắt default stereotype candidate filters; cần include filters để chủ động chọn classes.

**Câu 7.** Trong demo, tại sao `annotation.scanned-count=0`?

**Đáp:** `annotations.xml` không có component scan hoặc registration source cho `ScannedConsumer`.

**Câu 8.** Trong demo, tại sao `scan-off.scanned-count=1` nhưng `scan-off.scanned-wired=false`?

**Đáp:** Scanner đăng ký bean nhưng không tự thêm processor; context đó không có nguồn processor khác.

**Câu 9.** Scanner chỉ inject `@Autowired` cho scanned beans đúng không?

**Đáp:** Sai. Processor trong context xử lý injection của các Spring-managed beans được tạo trong context, kể cả beans đăng ký từ XML.

**Câu 10.** Có hai implementations của một interface trong context, `@Autowired` phải xử lý thế nào?

**Đáp:** Cần candidate selection phù hợp như `@Qualifier`, `@Primary` hoặc kiến trúc chỉ đăng ký một implementation khi yêu cầu.

**Câu 11.** Cứ bật annotation-config là `@Transactional` có transaction behavior?

**Đáp:** Không. Cần infrastructure transaction management phù hợp.

**Câu 12.** Bật annotation-config ở child context có tự xử lý fields trên bean root context không?

**Đáp:** Không. Processor có phạm vi context nơi nó được đăng ký.

**Câu 13.** Vì sao test bằng `new Service()` thường không phản ánh field injection?

**Đáp:** Vì không có Spring BeanFactory lifecycle và BeanPostProcessor tác động lên object tự tạo.

**Câu 14.** Khi XML dùng vendor bean còn scanned service cần nó, dependency có inject được không?

**Đáp:** Có, nếu cả hai definitions nằm trong context liên quan và candidate type/qualifier phù hợp.

## 19. Tài liệu chính thức

- [Spring Framework 6.2 — Annotation-based Container Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config.html): `<context:annotation-config/>` và processors.
- [Spring Framework 6.2 — Classpath Scanning and Managed Components](https://docs.spring.io/spring-framework/reference/6.2/core/beans/classpath-scanning.html): component scan, implicit annotation-config, filters và flags.
- [Spring Framework 6.2 — @Autowired](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired.html): constructor/field/method injection.
- [Spring Framework 6.2 — XML-based Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/basics.html): XML bean definitions và container.
- [Spring Framework 6.2 — BeanFactory Post-processors](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-extension.html): registry/instance post-processing.
- [Spring Framework 6.2 Javadoc — AutowiredAnnotationBeanPostProcessor](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/annotation/AutowiredAnnotationBeanPostProcessor.html).
- [Spring Framework 6.2 Javadoc — ClassPathXmlApplicationContext](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/support/ClassPathXmlApplicationContext.html).

**Ghi nhớ cuối cùng:** Trong XML Spring, `<context:annotation-config/>` bật **annotation processing trên các bean đã có**; `<context:component-scan/>` **tìm/register components** và mặc định bật annotation processing. Hai thuộc tính `annotation-config="false"` và `use-default-filters="false"` điều khiển **hai tầng khác nhau**: processors và scanner candidate selection. Phân biệt đúng **đăng ký bean** với **inject dependency** là chìa khóa để sử dụng chúng an toàn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="xml-configuration-009">Chuyển đổi cấu hình XML sang Java mà giữ nguyên hành vi</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển đổi cấu hình XML sang Java mà giữ nguyên hành vi — Spring Core Container

## 1. Mục tiêu: giữ hợp đồng runtime, không chỉ đổi cú pháp

Spring Framework hỗ trợ XML bean definitions và Java-based configuration (`@Configuration`, `@Bean`, `@Import`). Khi chuyển đổi một ứng dụng từ XML sang Java, việc thay thế `<bean>` bằng `@Bean` chỉ là phần dễ nhìn thấy. Phần khó là **giữ đúng hành vi của container**: beans nào tồn tại, chúng tên gì, được tạo khi nào, scope gì, nhận dependency nào và được khởi tạo/hủy như thế nào.

Ví dụ XML:

```xml
<bean id="repository" class="demo.Repository"/>
<bean id="service" class="demo.Service">
    <constructor-arg ref="repository"/>
    <property name="prefix" value="INV:"/>
</bean>
```

JavaConfig có thể viết:

```java
@Configuration(proxyBeanMethods = false)
class ApplicationConfig {
    @Bean("repository")
    Repository repository() {
        return new Repository();
    }

    @Bean("service")
    Service service(Repository repository) {
        Service service = new Service(repository);
        service.setPrefix("INV:");
        return service;
    }
}
```

Hai ví dụ này là **mã minh họa**, cần định nghĩa domain classes và imports. Chúng có thể tạo ra cùng object graph, nhưng chưa chứng minh mọi hợp đồng đều giống nhau: XML cũ còn có thể có aliases, profiles, `depends-on`, custom init/destroy callbacks, prototype scopes, namespace infrastructure hoặc properties được resolve theo một thứ tự ưu tiên cụ thể.

**Chuyển đổi thành công** là khi cùng bộ đầu vào và môi trường, consumer vẫn quan sát thấy hành vi đúng như trước, trừ những khác biệt đã được đội dự án chủ động phê duyệt.

Bài học tập trung vào:

- Kiểm kê đầy đủ nguồn XML, bean definitions và metadata.
- Bảng ánh xạ XML → JavaConfig cho dependency injection, bean names, aliases, scopes, lazy, profiles, ordering và lifecycle.
- Các trường hợp không có phép chuyển 1:1 như `FactoryBean`, lookup-method injection và custom namespaces.
- Tích hợp XML/Java tạm thời bằng `@ImportResource` và chiến lược tránh đăng ký trùng.
- Ví dụ Java 17+ Spring Framework 6.2 với **hai context độc lập** và assertions so sánh output/identity/lifecycle.
- Test hồi quy, checklist triển khai, lỗi phổ biến và bài tập có lời giải.

## 2. “Parity” là gì và nên đo ở đâu?

Một phép so sánh class names đơn thuần không đủ. Cần phân biệt các hợp đồng:

| Nhóm hợp đồng | Điều cần giữ | Ví dụ lỗi khi migrate |
| --- | --- | --- |
| Bean registration | Những bean names/types có mặt trong đúng context | Bỏ bean cũ hoặc vô tình scan thêm bean |
| Naming/alias | Bean id, alias và lookup theo chuỗi | `getBean("billingGateway")` không còn hoạt động |
| Injection | Consumer nhận chính collaborator trước đây | Chuyển `ref="safeStore"` sang inject by type rồi nhận `fastStore` |
| Scope | Singleton, prototype hoặc web/custom scope | Prototype biến thành singleton |
| Lazy/eager | Thời điểm bean được tạo | Bean lazy bị tạo ngay lúc refresh |
| Lifecycle | Init/destroy method, callbacks, shutdown | Bỏ `stop()` hoặc gọi `start()` hai lần |
| Ordering | `depends-on` và các dependencies gián tiếp | Infrastructure chưa sẵn sàng khi service init |
| Config values | Literals, nulls, collections, property sources | Endpoint thay đổi vì precedence khác |
| Conditions | Active profile/conditional metadata | Production nhận dev bean |
| Candidate rules | Primary/qualifier/autowire-candidate | Hai implementations trở nên ambiguous |
| Framework infrastructure | Transactions/AOP/cache/async/schedulers | Logic vẫn chạy nhưng transaction không rollback |
| Ownership | ApplicationContext cha/con, scope boundary | Bean được chuyển sang context khác |
| External behavior | I/O, database, messaging, side effects | Job/listener chạy hai lần sau migration |

Không nhất thiết giữ nguyên tất cả **chi tiết triển khai**. Chẳng hạn `AnnotationConfigApplicationContext` có thể đăng ký thêm những Spring infrastructure definitions so với `ClassPathXmlApplicationContext`. Thay vì đòi `getBeanDefinitionCount()` bằng nhau tuyệt đối, hãy so sánh **bean contract** và kết quả sử dụng thực tế.

### 2.1. Phân biệt behavioral và structural parity

Hai configurations có thể khác nhau về cấu trúc mà vẫn đúng hành vi:

- XML `<constructor-arg ref=...>` chuyển thành factory-method parameter.
- XML `<property value=...>` chuyển thành lời gọi setter trong `@Bean` method.
- XML `<import resource=...>` chuyển thành `@Import(JavaConfig.class)` sau khi nguồn đó được migrate.
- XML name/alias được giữ trong `@Bean(name={"primary","alias"})`.

Ngược lại, giữ cùng implementation class nhưng thay scope, bỏ alias hoặc mất lifecycle hook là **không tương đương**.

### 2.2. Định nghĩa ranh giới kiểm chứng

Trong một application có cả root và servlet child contexts, cùng một type có thể tồn tại hai instances hợp lệ. Hãy kiểm tra từ **cùng điểm nhìn consumer**: context nào sở hữu bean, tên nào được lookup, proxy nào được truyền cho service. Không so một singleton XML ở child context với singleton JavaConfig ở root rồi tuyên bố tương đương chỉ vì class giống nhau.

## 3. Trước khi viết Java: kiểm kê cấu hình XML thực sự được load

Một dự án có thể chứa:

```text
config/
├── root.xml
├── persistence.xml
├── services.xml
├── integration/
│   ├── messaging.xml
│   └── transactions.xml
└── environments/
    ├── dev.xml
    └── prod.xml
```

Entry point có thể gọi:

```java
new ClassPathXmlApplicationContext(
        "config/root.xml",
        "config/services.xml");
```

hoặc XML chứa `<import resource="..."/>` bắc cầu. Một library JAR cũng có thể mang XML được nhập từ application.

**Nên kiểm kê:**

1. Những XML resources được load ở runtime, không chỉ file có tên “applicationContext”.
2. `<import>` bắc cầu và nhóm `<beans profile=...>`.
3. Bean ids, aliases, types, scopes, `lazy-init`, `depends-on`, init/destroy, primary, qualifiers.
4. Các constructor/property values và explicit `ref` theo tên.
5. Property placeholders, bean factory post-processors và precedence của property sources.
6. Các namespace handler `context`, `tx`, `aop`, `task` hoặc thư viện thứ ba.
7. `FactoryBean`, `factory-bean`, `factory-method`, inner beans, lookup-method, bean definition inheritance.
8. Các test/integrations dùng `getBean("name")` hay SpEL bean references theo chuỗi.
9. Beans được đăng ký qua component scan ngoài XML explicit definitions.
10. Parent-child contexts và những beans có external side effects.

Ví dụ bảng kiểm kê:

| Bean | Tên khác | Scope | Dependencies | Init/Destroy | Đặc điểm |
| --- | --- | --- | --- | --- | --- |
| `catalogRepository` | — | singleton | — | — | Managed repository |
| `catalogService` | `publicCatalog` | singleton | `catalogRepository` | `start/stop` | `depends-on="startupMarker"` |
| `ticket` | — | prototype | — | — | Hai lookups phải khác instances |
| `lazyFormatter` | — | lazy singleton | — | — | Tạo khi lookup |
| `startupMarker` | — | singleton | — | — | Initialization dependency |

Demo ở mục 10 sẽ hiện thực toàn bộ bảng này trên cả XML và JavaConfig, rồi kiểm tra tự động.

## 4. Ánh xạ XML → JavaConfig: bản tra cứu thực hành

| XML | JavaConfig | Kiểm tra |
| --- | --- | --- |
| `<bean id="x" class="pkg.X"/>` | `@Bean("x") X x()` | Bean type và name |
| `<alias name="x" alias="publicX"/>` | `@Bean(name={"x","publicX"})` | Hai names cùng bean |
| `<constructor-arg ref="repo"/>` | Factory method nhận `Repo repo` | Đúng bean instance |
| `<constructor-arg value="5"/>` | `new X(5)` hoặc config value | Conversion/default |
| `<property name="enabled" value="true"/>` | `obj.setEnabled(true)` | Giá trị và timing |
| `<property name="store" ref="store"/>` | Inject Store param rồi `setStore(store)` | Không tự tạo raw dependency |
| `<list>/<map>/<props>` | Java collections/Properties | Content, mutability, order |
| `scope="singleton"` | `@Bean` mặc định | Per-container singleton |
| `scope="prototype"` | `@Scope("prototype")` | Unique lookup behavior |
| `lazy-init="true"` | `@Lazy` | Tránh pre-instantiation khi chưa cần |
| `depends-on="manager"` | `@DependsOn("manager")` | Startup/shutdown relation |
| `init-method="start"` | `@Bean(initMethod="start")` | Callback một lần |
| `destroy-method="stop"` | `@Bean(destroyMethod="stop")` | Callback khi context close |
| `primary="true"` | `@Primary` | Single-candidate selection |
| `autowire-candidate="false"` | `@Bean(autowireCandidate=false)` | Type-based selection |
| `<beans profile="dev">` | `@Profile("dev")` | Bean tập hợp theo profile |
| XML `<import resource="x.xml"/>` | `@Import(XConfig.class)` sau khi migrate | Đúng sources |
| Giữ XML chưa migrate | `@ImportResource("classpath:x.xml")` | Không duplicate beans |
| `<context:component-scan>` | `@ComponentScan` | Packages và filters |
| `<tx:annotation-driven/>` | `@EnableTransactionManagement` khi phù hợp | Transaction manager/proxy behavior |

Các mảnh Java trong bảng là **cú pháp minh họa**. Chúng cần import annotation từ các Spring packages tương ứng và class definitions đúng.

### 4.1. Cẩn thận với những XML default values

XML bean definitions mặc định singleton và thường eager initialize non-lazy singletons trong `ApplicationContext`. `@Bean` cũng mặc định singleton, nhưng destroy inference/bean candidate metadata có thể khác. Không vì cả hai có default singleton mà bỏ qua kiểm tra các defaults khác.

## 5. Bean names và aliases là một phần API

XML:

```xml
<bean id="paymentService" class="demo.PaymentService"/>
<alias name="paymentService" alias="billingGateway"/>
```

JavaConfig gần tương ứng:

```java
@Bean(name = {"paymentService", "billingGateway"})
PaymentService paymentService() {
    return new PaymentService();
}
```

Tên đầu trong mảng là primary bean name; tên sau là aliases. Với singleton, hai lookups theo tên nhận cùng instance. Không cần tạo hai factory methods chỉ vì XML có alias.

### 5.1. Đặt rõ `@Bean("oldName")`

Nếu chuyển:

```xml
<bean id="accountDao" class="demo.AccountDao"/>
```

sang:

```java
@Bean
AccountDao repository() {
    return new AccountDao();
}
```

thì bean mặc định được đặt tên `repository` chứ không phải `accountDao`. Injection theo type có thể vẫn chạy, nhưng `getBean("accountDao")` hoặc XML `ref="accountDao"` ở file chưa migrate sẽ thất bại.

Giữ tên bằng `@Bean("accountDao")` khi legacy contract có tên này. Đừng tự ý đổi naming convention cùng lúc migrate.

### 5.2. Kiểm tra cả lookup theo type lẫn theo tên

Nếu JavaConfig thêm một implementation thứ hai, lookup theo tên có thể vẫn đúng nhưng type-based injection trở nên ambiguous. Kiểm tra tên/alias và số ứng viên cùng interface, nhất là khi có `@Primary`/`@Qualifier`.

## 6. Chuyển dependency injection mà không đổi lựa chọn collaborator

### 6.1. Constructor arguments

XML:

```xml
<bean id="repository" class="demo.SqlRepository"/>
<bean id="service" class="demo.OrderService">
    <constructor-arg ref="repository"/>
</bean>
```

JavaConfig:

```java
@Configuration(proxyBeanMethods = false)
class OrderConfiguration {

    @Bean("repository")
    SqlRepository repository() {
        return new SqlRepository();
    }

    @Bean("service")
    OrderService service(SqlRepository repository) {
        return new OrderService(repository);
    }
}
```

Method-parameter injection nói với BeanFactory resolve repository trước khi chạy service factory. Đây là lựa chọn đơn giản và tránh inter-bean self-invocation khi `proxyBeanMethods=false`.

### 6.2. Một XML `ref` có thể mang ý nghĩa **chọn theo tên**

XML:

```xml
<bean id="service" class="demo.OrderService">
    <constructor-arg ref="safeRepository"/>
</bean>
```

Nếu Java viết:

```java
@Bean("service")
OrderService service(Repository repository) {
    return new OrderService(repository);
}
```

mà container có `fastRepository` và `safeRepository`, Spring có thể chọn ứng viên khác (primary/qualifier/name resolution rules), hoặc fail vì không có ứng viên thắng. Để giữ intent, dùng:

```java
@Bean("service")
OrderService service(
        @Qualifier("safeRepository") Repository repository) {
    return new OrderService(repository);
}
```

`@Qualifier` giúp ràng buộc candidate theo name/metadata. Với kịch bản đặc biệt cần lookup chính xác bằng string, có thể sử dụng BeanFactory named lookup có chủ đích. Không biến business code thành service locator nếu factory-method parameters đủ diễn đạt dependency.

### 6.3. Setter injection giữ thời điểm populate property

XML:

```xml
<bean id="client" class="demo.Client">
    <property name="timeoutMillis" value="5000"/>
    <property name="repository" ref="repository"/>
</bean>
```

JavaConfig:

```java
@Bean("client")
Client client(Repository repository) {
    Client client = new Client();
    client.setTimeoutMillis(5000);
    client.setRepository(repository);
    return client;
}
```

Giữ cả literal và dependency reference. Nếu refactor sang constructor injection trong lúc chuyển, behavior có thể vẫn đúng nhưng initialization sequencing/class API thay đổi; nên làm bước refactor riêng và có tests.

### 6.4. Tránh `new` một collaborator thay cho managed bean

Sai nếu XML cũ inject singleton repository:

```java
@Bean
OrderService orderService() {
    return new OrderService(new SqlRepository());
}
```

`new SqlRepository()` tạo object độc lập, không phải managed bean `repository` do Spring đăng ký, không nhất thiết có AOP proxy/injection/init/destroy đúng.

Đúng hơn:

```java
@Bean
OrderService orderService(SqlRepository repository) {
    return new OrderService(repository);
}
```

**Ngoại lệ cần xét riêng:** XML inner bean cố ý tạo collaborator chỉ cho một consumer. Trong trường hợp đó, Java factory tự tạo object có thể phù hợp về functional effect, nhưng lifecycle/post-processing của inner bean có thể khác — test trước khi kết luận parity.

### 6.5. Full `@Configuration` và lite mode

`@Configuration` mặc định `proxyBeanMethods=true` và có thể được CGLIB tăng cường để các lời gọi giữa instance `@Bean` methods được chuyển về container. Với `proxyBeanMethods=false`, lời gọi Java `someBean()` là direct method call, có thể tạo thêm raw object.

Để migration dễ theo dõi, ưu tiên:

```java
@Bean
Service service(Repository repository) {
    return new Service(repository);
}
```

hơn:

```java
@Bean
Service service() {
    return new Service(repository()); // Direct @Bean method call.
}
```

Phương pháp đầu làm graph rõ trong signature và không phụ thuộc proxy mode.

## 7. Scalar values, nulls, collections và cấu hình bên ngoài

### 7.1. Literal conversion

XML:

```xml
<property name="retries" value="3"/>
<property name="enabled" value="true"/>
<property name="label" value="Invoices"/>
```

Trong Java:

```java
service.setRetries(3);
service.setEnabled(true);
service.setLabel("Invoices");
```

Spring XML dùng type-conversion infrastructure để đổi text theo setter type; Java compiler kiểm tra kiểu arguments. Với custom `PropertyEditor`/`ConversionService`, một literal có thể được biến thành domain value đặc biệt. Hãy giữ **ý nghĩa sau conversion**, không chỉ copy raw XML string.

### 7.2. Empty string khác Java null

XML `<property name="memo" value=""/>` diễn đạt chuỗi rỗng. XML:

```xml
<property name="memo">
    <null/>
</property>
```

diễn đạt null. Khi Java migrate, dùng `setMemo("")` hay `setMemo(null)` đúng theo dữ liệu cũ. Phân biệt hai trường hợp nếu validation/serialization/business logic phụ thuộc chúng.

### 7.3. Collection mutability và order

XML:

```xml
<property name="channels">
    <list>
        <value>email</value>
        <value>sms</value>
    </list>
</property>
```

Java:

```java
service.setChannels(List.of("email", "sms"));
```

Hai collections này có thể tương đương nếu service **chỉ đọc** và chỉ cần đúng nội dung/thứ tự. Nhưng `List.of(...)` là unmodifiable; nếu legacy service gọi `channels.add("push")`, behavior đổi.

Nếu mutation là contract:

```java
service.setChannels(
        new ArrayList<>(List.of("email", "sms")));
```

Với XML `<map>` và Java `Map.of(...)`, chú ý iteration order, null keys/values, duplicate keys và mutability. Khi cần order rõ, cân nhắc `LinkedHashMap`; khi cần mutable object, tạo mutable copy và có tests.

### 7.4. Placeholder và property source precedence

XML có thể khai:

```xml
<context:property-placeholder
    location="classpath:legacy.properties"/>

<bean id="client" class="demo.Client">
    <property name="endpoint"
              value="${client.endpoint}"/>
</bean>
```

JavaConfig có thể dùng:

```java
@Configuration(proxyBeanMethods = false)
@PropertySource("classpath:legacy.properties")
class ClientConfiguration {

    @Bean
    Client client(@Value("${client.endpoint}") String endpoint) {
        Client client = new Client();
        client.setEndpoint(endpoint);
        return client;
    }
}
```

Đây là các đoạn minh họa; XML cần khai namespace `context`, Java cần imports và domain class. Cú pháp Spring placeholder là `${client.endpoint}` (không có backslash ở source thật). Hệ thống resolve placeholder dựa trên property sources và infrastructure đang dùng.

Chú ý **source/precedence**: property từ system env, system properties, file trong classpath, Spring `Environment`, `PropertySourcesPlaceholderConfigurer` hoặc Boot config system có thể khác nhau. Không đổi tên key, default fallback hoặc nguồn ưu tiên chỉ vì syntax mới ngắn hơn.

### 7.5. SpEL expressions không phải Java expressions tương đương vô điều kiện

XML `value="#{@settings.timeoutMillis}"` có thể resolve SpEL qua container. Một Java call `settings.timeoutMillis()` có thể khác về proxy, lazy/scoped references và lifecycle. Giữ `@Value("#{...}")` khi có lý do rõ hoặc thay bằng DI + test, không tìm–thay thế biểu thức theo text.

## 8. Bean scope, lazy, ordering và lifecycle

### 8.1. Scope

XML:

```xml
<bean id="ticket" class="demo.Ticket"
      scope="prototype"/>
```

Java:

```java
@Bean("ticket")
@Scope("prototype")
Ticket ticket() {
    return new Ticket();
}
```

Hai calls `context.getBean("ticket")` phải trả hai objects khác nhau theo prototype semantics. `@Bean` mặc định singleton nên quên `@Scope` là regression.

Nếu một singleton nhận prototype trong constructor, nó thường giữ một prototype đã inject; để lookup mỗi operation, có thể cần `ObjectProvider<T>`/factory/method injection. Chỉ chuyển scope flag chưa đủ nếu XML cũ dùng `<lookup-method>`.

### 8.2. Lazy

XML:

```xml
<bean id="lazyFormatter"
      class="demo.LazyFormatter"
      lazy-init="true"/>
```

Java:

```java
@Bean("lazyFormatter")
@Lazy
LazyFormatter lazyFormatter() {
    return new LazyFormatter();
}
```

Bean lazy có thể bị tạo tại startup nếu một eager singleton cần nó để giải quyết dependency. Test trên actual graph, không tuyên bố mọi lazy bean “không bao giờ” được tạo lúc refresh.

### 8.3. Depends-on

XML:

```xml
<bean id="service" class="demo.Service"
      depends-on="startupMarker"/>
```

Java:

```java
@Bean("service")
@DependsOn("startupMarker")
Service service(Repository repository) {
    return new Service(repository);
}
```

`@DependsOn` là ordering dependency theo bean names. Nó không tự truyền object `StartupMarker` cho service. Quan hệ này còn có thể ảnh hưởng destruction order của singletons. Đừng xóa nó chỉ vì service constructor không dùng marker.

### 8.4. Init/destroy callbacks

XML:

```xml
<bean id="service" class="demo.Service"
      init-method="start"
      destroy-method="stop"/>
```

Java:

```java
@Bean(name = "service",
      initMethod = "start",
      destroyMethod = "stop")
Service service(Repository repository) {
    return new Service(repository);
}
```

Không gọi `service.start()` trong factory method rồi đồng thời giữ `initMethod="start"`: callback có thể chạy **hai lần**. Phải để container gọi ở đúng lifecycle phase.

### 8.5. Destroy inference của `@Bean` là một bẫy

`@Bean` có thể tự suy luận destroy method dạng public no-arg `close()` hoặc `shutdown()`. Điều đó có thể thêm behavior so với XML cũ không cấu hình callback như vậy.

Nếu muốn ngăn **inference** cho một bean:

```java
@Bean(destroyMethod = "")
LegacyResource sharedResource() {
    return new LegacyResource();
}
```

Giá trị rỗng chỉ vô hiệu cơ chế inference đó; nó không tắt callbacks từ `DisposableBean` hoặc lifecycle khác. Với resource XML đã có `destroy-method="close"`, nên khai `@Bean(destroyMethod="close")` rõ để giữ hành vi.

### 8.6. Prototype destroy không giống singleton destroy

Spring không tự theo dõi đầy đủ mọi prototype đã cấp để thực hiện destroy trên chúng khi context đóng như với singleton. Nếu dự án có prototype resource cần cleanup, application có thể phải thực hiện riêng. Không dùng `@Bean(destroyMethod="...")` để hứa hẹn cleanup mọi prototype.

## 9. Profiles, candidates và infrastructure quan trọng

### 9.1. XML profile sang `@Profile`

XML:

```xml
<beans profile="dev">
    <bean id="store" class="demo.InMemoryStore"/>
</beans>
```

Java:

```java
@Configuration(proxyBeanMethods = false)
@Profile("dev")
class DevConfiguration {
    @Bean("store")
    Store store() {
        return new InMemoryStore();
    }
}
```

Test cả dev/prod/no profile, cộng multi-profile khi ứng dụng thực tế hỗ trợ. Nếu imported configuration mang `@Conditional` hoặc `@Profile`, nó còn chịu điều kiện riêng; import không ép bean tồn tại.

### 9.2. Primary/qualifier

XML `primary="true"` gần với Java `@Primary`. Với consumer cần rõ một bean cụ thể, `@Qualifier("safeStore")` hoặc explicit named lookup có thể cần thiết hơn một default by-type injection.

`autowire-candidate="false"` trong XML chủ yếu loại bean khỏi type-based injection, không xóa bean và không cấm explicit `ref`. Trong Spring 6.2, `@Bean(autowireCandidate=false)` có thể giữ candidate metadata tương ứng.

### 9.3. Infrastructure XML không đổi sang annotation bằng đoán tên

`<tx:annotation-driven/>` có thể tương ứng `@EnableTransactionManagement`, nhưng hãy xác minh mode proxy/AspectJ, transaction manager, advisor ordering, proxyTargetClass và actual commit/rollback.

`<aop:aspectj-autoproxy/>` có cơ chế Java như `@EnableAspectJAutoProxy` khi phù hợp, nhưng không có nghĩa mọi raw object trở thành proxy. Self-invocation trong Spring AOP proxy mode thường bypass proxy.

`<context:component-scan>` có thể chuyển sang `@ComponentScan`, nhưng giữ đúng package roots, include/exclude filters và bean naming. Component scan quá rộng có thể đăng ký thêm cả XML configs/implementations chưa muốn dùng.

Với custom namespace từ Spring Integration, Security, Batch hoặc vendor library, một Java DSL/API tương đương **không được bảo đảm** cho mọi version. Có thể giữ một XML fragment qua `@ImportResource` rồi migrate khi đã có test.

## 10. Demo Java 17+: cùng ứng dụng, hai cách đăng ký bean

Mục tiêu là so sánh **một XML context** và **một JavaConfig context**, không nhập cả hai vào chung một registry. Bản XML và JavaConfig cùng định nghĩa:

- `catalogRepository`: singleton repository.
- `catalogService`: singleton service, alias `publicCatalog`, nhận repository, prefix, retries, channels.
- `startupMarker`: có quan hệ `depends-on` với service.
- `ticket`: prototype.
- `lazyFormatter`: lazy singleton.
- Lifecycle của service: `start` khi initialize, `stop` khi context close.

### 10.1. Cấu trúc file

```text
xml-to-java-parity/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        ├── java/
        │   └── demo/
        │       └── XmlJavaParityDemo.java
        └── resources/
            └── legacy-context.xml
```

`settings.gradle`:

```groovy
rootProject.name = 'xml-to-java-parity'
```

`build.gradle`:

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.19'
}

application {
    mainClass = 'demo.XmlJavaParityDemo'
}
```

Dùng JDK 17+ và Gradle tương thích. `spring-context` bao gồm những Spring module dependencies cần để đọc XML và JavaConfig; không cần Spring Boot.

### 10.2. File XML gốc

`src/main/resources/legacy-context.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="startupMarker"
          class="demo.XmlJavaParityDemo$StartupMarker"/>

    <bean id="catalogRepository"
          class="demo.XmlJavaParityDemo$CatalogRepository"/>

    <bean id="catalogService"
          class="demo.XmlJavaParityDemo$CatalogService"
          depends-on="startupMarker"
          init-method="start"
          destroy-method="stop">
        <constructor-arg ref="catalogRepository"/>
        <property name="prefix" value="INV:"/>
        <property name="retries" value="2"/>
        <property name="channels">
            <list>
                <value>email</value>
                <value>sms</value>
            </list>
        </property>
    </bean>

    <alias name="catalogService" alias="publicCatalog"/>

    <bean id="ticket"
          class="demo.XmlJavaParityDemo$Ticket"
          scope="prototype"/>

    <bean id="lazyFormatter"
          class="demo.XmlJavaParityDemo$LazyFormatter"
          lazy-init="true"/>
</beans>
```

Các `$` trong attribute `class` là **binary names của nested Java classes**. Đây là chính xác trong demo; nếu chuyển domain classes thành top-level classes thì phải đổi XML class names.

### 10.3. Toàn bộ mã Java

`src/main/java/demo/XmlJavaParityDemo.java`:

```java
package demo;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class XmlJavaParityDemo {

    public static final class StartupMarker {
        public StartupMarker() {
        }
    }

    public static final class CatalogRepository {
        public CatalogRepository() {
        }

        public String find(String id) {
            return "sku-" + id;
        }
    }

    public static final class Ticket {
        public Ticket() {
        }
    }

    public static final class LazyFormatter {
        public LazyFormatter() {
        }
    }

    public static final class CatalogService {
        private final CatalogRepository repository;
        private String prefix = "";
        private int retries;
        private List<String> channels = List.of();
        private boolean started;
        private boolean stopped;

        public CatalogService(CatalogRepository repository) {
            this.repository = Objects.requireNonNull(repository);
        }

        public void setPrefix(String prefix) {
            this.prefix = Objects.requireNonNull(prefix);
        }

        public void setRetries(int retries) {
            this.retries = retries;
        }

        public void setChannels(List<String> channels) {
            this.channels = Objects.requireNonNull(channels);
        }

        public CatalogRepository getRepository() {
            return repository;
        }

        public void start() {
            started = true;
        }

        public void stop() {
            stopped = true;
        }

        public boolean isStarted() {
            return started;
        }

        public boolean isStopped() {
            return stopped;
        }

        public String report(String id) {
            return prefix + repository.find(id)
                    + " retries=" + retries
                    + " channels=" + channels;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class JavaConfig {

        @Bean("startupMarker")
        StartupMarker startupMarker() {
            return new StartupMarker();
        }

        @Bean("catalogRepository")
        CatalogRepository catalogRepository() {
            return new CatalogRepository();
        }

        @Bean(
            name = {"catalogService", "publicCatalog"},
            initMethod = "start",
            destroyMethod = "stop"
        )
        @DependsOn("startupMarker")
        CatalogService catalogService(
                CatalogRepository catalogRepository) {

            CatalogService service =
                    new CatalogService(catalogRepository);
            service.setPrefix("INV:");
            service.setRetries(2);
            service.setChannels(List.of("email", "sms"));
            return service;
        }

        @Bean("ticket")
        @Scope("prototype")
        Ticket ticket() {
            return new Ticket();
        }

        @Bean("lazyFormatter")
        @Lazy
        LazyFormatter lazyFormatter() {
            return new LazyFormatter();
        }
    }

    private static void inspect(
            String label,
            ConfigurableApplicationContext context) {

        CatalogService service;

        try (context) {
            boolean lazyBefore = context.getBeanFactory()
                    .containsSingleton("lazyFormatter");

            service = context.getBean(
                    "catalogService", CatalogService.class);

            CatalogRepository repository =
                    context.getBean(CatalogRepository.class);

            boolean aliasSame =
                    service == context.getBean("publicCatalog");
            boolean repositorySame =
                    service.getRepository() == repository;
            boolean prototypeDistinct =
                    context.getBean("ticket")
                    != context.getBean("ticket");
            boolean dependsOnCorrect = Arrays.equals(
                    context.getBeanFactory()
                            .getBeanDefinition("catalogService")
                            .getDependsOn(),
                    new String[] {"startupMarker"});

            context.getBean("lazyFormatter");
            boolean lazyAfter = context.getBeanFactory()
                    .containsSingleton("lazyFormatter");

            String expected =
                    "INV:sku-42 retries=2 channels=[email, sms]";

            if (!expected.equals(service.report("42"))
                    || !aliasSame || !repositorySame
                    || !prototypeDistinct || !dependsOnCorrect
                    || !service.isStarted()
                    || lazyBefore || !lazyAfter) {
                throw new AssertionError(
                        label + " parity failed");
            }

            System.out.println(label + " report=" +
                    service.report("42"));
            System.out.println(label + " aliasSame=" + aliasSame);
            System.out.println(label + " repositorySame=" +
                    repositorySame);
            System.out.println(label + " prototypeDistinct=" +
                    prototypeDistinct);
            System.out.println(label + " dependsOnCorrect=" +
                    dependsOnCorrect);
            System.out.println(label + " started=" +
                    service.isStarted());
            System.out.println(label + " lazyBefore=" + lazyBefore);
            System.out.println(label + " lazyAfter=" + lazyAfter);
        }

        if (!service.isStopped()) {
            throw new AssertionError(
                    label + " destroy callback not invoked");
        }
        System.out.println(label + " stopped=" +
                service.isStopped());
    }

    public static void main(String[] args) {
        inspect("XML", new ClassPathXmlApplicationContext(
                "legacy-context.xml"));
        inspect("JAVA", new AnnotationConfigApplicationContext(
                JavaConfig.class));
    }
}
```

Đây là một Java file đầy đủ với đúng một top-level public class. Domain types là static nested classes; các factory methods trong `JavaConfig` không tự gọi lẫn nhau. `@Bean` method `catalogService(...)` nhận managed repository từ BeanFactory; không tạo một repository thứ hai.

### 10.4. Cách chạy

Từ root project:

```bash
gradle run --quiet
```

Khi có Gradle Wrapper:

```bash
./gradlew run --quiet
```

Trong Windows PowerShell:

```powershell
.\gradlew.bat run --quiet
```

Stdout của chương trình:

```text
XML report=INV:sku-42 retries=2 channels=[email, sms]
XML aliasSame=true
XML repositorySame=true
XML prototypeDistinct=true
XML dependsOnCorrect=true
XML started=true
XML lazyBefore=false
XML lazyAfter=true
XML stopped=true
JAVA report=INV:sku-42 retries=2 channels=[email, sms]
JAVA aliasSame=true
JAVA repositorySame=true
JAVA prototypeDistinct=true
JAVA dependsOnCorrect=true
JAVA started=true
JAVA lazyBefore=false
JAVA lazyAfter=true
JAVA stopped=true
```

Các build tool/logging messages nằm ngoài những `System.out.println` có thể xuất hiện tùy môi trường. Assertions sẽ ném `AssertionError` nếu parity contract ở ví dụ không thỏa.

### 10.5. Giải thích kết quả

**`report`:** cả hai contexts cung cấp cùng prefix, retries và ordered channels. Repository behavior trả `sku-42`, nên business result bằng nhau.

**`aliasSame=true`:** XML `<alias>` và Java `@Bean(name=...)` cùng giữ alias `publicCatalog` cho `catalogService`.

**`repositorySame=true`:** constructor của `CatalogService` được cấp đúng managed singleton `CatalogRepository`. Không có `new CatalogRepository()` trong service factory method.

**`prototypeDistinct=true`:** hai lookups của `ticket` trong cùng context trả hai objects khác nhau.

**`dependsOnCorrect=true`:** cả hai definitions có metadata phụ thuộc `startupMarker`.

**`started=true`:** Spring gọi init method trên singleton service; code factory không gọi `start()` thủ công.

**`lazyBefore=false`/`lazyAfter=true`:** trước lookup, bean lazy chưa có singleton instance; sau lookup đã được tạo.

**`stopped=true`:** try-with-resources đóng context và thực hiện destroy callback.

### 10.6. Giới hạn của bài demo

Demo xác minh **contract đã khai báo**, không chứng minh hai Spring contexts có mọi bean infrastructure/metadata giống tuyệt đối. Nó không thử transactions, component scan, FactoryBean, profiles hay web scopes. Java `List.of` là unmodifiable; do demo không mutate channels nên parity được chứng minh về nội dung/thứ tự, **không** về mutability. Với hệ thống thật, thêm assertions theo business contract còn thiếu.

## 11. Chạy XML và JavaConfig song song bằng `@ImportResource`

Không nhất thiết xóa mọi XML trong một commit. Trong Java-centric application, có thể:

```java
@Configuration(proxyBeanMethods = false)
@ImportResource("classpath:legacy-infrastructure.xml")
class ApplicationConfig {

    @Bean
    NewService newService(LegacyRepository repository) {
        return new NewService(repository);
    }
}
```

Đây là **mã minh họa**, cần class/domain types và imports. `@ImportResource` nạp XML bean definitions; nó khác Java `@Import(AnotherConfig.class)`.

### 11.1. Mỗi bean chỉ có một nguồn registration có chủ đích

Khi chuyển một bean từ XML sang Java:
- Đăng ký bean mới trong JavaConfig.
- Loại chính bean definition tương ứng khỏi XML đang load.
- Rà soát scanner/auto-config có đăng ký thêm type/name đó không.
- Kiểm thử named lookup, injection, scopes, callbacks, side effects.

Đừng để hai bean definitions cùng tên rồi trông chờ bean override order tự chọn đúng. Chính sách bean definition overriding phụ thuộc context/framework settings và không nên là migration strategy.

### 11.2. XML-centric app vẫn có thể nhận JavaConfig

Trong ứng dụng mà `ClassPathXmlApplicationContext` là entry point, Spring có thể xử lý một `@Configuration` class được đăng ký như bean hoặc quét từ package nếu configuration post-processing phù hợp đã bật. Đừng chỉ thêm một class vào XML rồi cho rằng toàn bộ `@Bean` methods chắc chắn sẽ được nhận trong mọi legacy bootstrap setup.

Java-centric `AnnotationConfigApplicationContext` với `@ImportResource` thường là điểm xuất phát gọn khi mục tiêu cuối là JavaConfig. Nhưng migration có thể đi hai chiều tùy ranh giới hiện tại.

### 11.3. Chia module nhỏ

Một lộ trình an toàn:

1. Giữ legacy XML infrastructure hoạt động.
2. Migrate nhóm domain bean nhỏ, ít side effects.
3. Chạy context parity tests.
4. Thay đổi composition root để dùng bean Java mới.
5. Xóa XML definitions của nhóm đó.
6. Tiếp tục với nhóm kế tiếp, để hạ tầng namespace phức tạp sau cùng.

Không kết hợp nâng cấp major Spring version, sửa business logic và đổi property precedence trong cùng một đợt nếu cần chứng minh hành vi được bảo toàn.

## 12. Những trường hợp không có ánh xạ một-một

### 12.1. `FactoryBean` và ký hiệu `&`

XML có thể đăng ký implementation của `FactoryBean<T>`. Khi gọi `getBean("connection")`, Spring trả product; khi gọi `getBean("&connection")`, có thể lấy chính factory bean. Một JavaConfig chuyển nó thành `@Bean Connection connection()` trực tiếp có thể làm mất factory contract.

Nếu consumers cần `&name`, hãy giữ `FactoryBean` semantics hoặc thay consumers có kế hoạch. Đừng đánh đồng `FactoryBean` và Java factory method.

### 12.2. Lookup-method injection

XML `<lookup-method>` có thể giúp singleton lấy prototype mới ở mỗi lần gọi method. Java `@Scope("prototype")` trên bean được tạo chưa tự làm một singleton đổi dependency mỗi lần dùng. Cân nhắc `ObjectProvider<T>`/factory abstraction hoặc `@Lookup` với constraints phù hợp. Kiểm tra behavior qua nhiều lookups.

### 12.3. XML bean inheritance

XML child bean definitions có thể thừa hưởng/ghi đè metadata từ parent `parent="..."`. Java class inheritance không mô phỏng mọi rule của bean definition inheritance, đặc biệt collection merging hoặc abstract parent definitions. Có thể thay bằng helper methods/factory patterns, nhưng phải đối chiếu metadata merged.

### 12.4. Custom XML namespaces

Một số XML namespaces được vendor triển khai bằng `NamespaceHandler` tạo nhiều bean definitions/proxies. Chỉ khi vendor có Java DSL/API tương ứng đúng version mới nên chuyển từng phần. Có thể giữ XML qua `@ImportResource` nếu hiện chưa có cách an toàn; không tự dựng một `@Bean` và giả định thay thế toàn bộ namespace semantics.

### 12.5. Post-processors, proxies và infrastructure

Bean XML có thể được một `BeanPostProcessor` thay bằng proxy; nếu đổi context bootstrapping làm post-processor đó không còn được đăng ký, returned type/transaction behavior sẽ khác dù target class giống. Test qua **managed bean reference**, không chỉ trực tiếp gọi Java factory method.

### 12.6. Metadata ít phổ biến

`role`, `description`, abstract definitions, lookup/replaced methods, custom qualifiers, `primary`, `autowire-candidate` và scope-specific proxies có thể mang hợp đồng đối với libraries/tooling. Không phải trường nào cũng có annotation có tên y hệt; nếu cần, sử dụng API bean definition registration hoặc giữ XML tạm thời.

## 13. Test hồi quy: dùng bằng chứng thay vì tin vào mắt

### 13.1. Golden-master trước migration

Với context XML cũ, thu kết quả:

- `getBean("name")` và alias lookup.
- Type/identity của managed dependency quan trọng.
- Kết quả gọi method nghiệp vụ với input ổn định.
- Singleton/prototype differences.
- Lifecycle init/destroy, lazy/eager và `depends-on`.
- Active profiles và property precedence ở dev/test/prod.
- Transaction rollback/commit, cache, scheduler hoặc messaging side effects khi có.
- Có hay không nhiều candidates cùng interface.

Sau đó chạy cùng kiểm tra trên JavaConfig mới **với cùng phiên bản framework, input và runtime dependencies**.

### 13.2. Differential test ở hai contexts

Trích đoạn từ demo:

```java
try (var legacy = new ClassPathXmlApplicationContext(
        "legacy-context.xml");
     var modern = new AnnotationConfigApplicationContext(
        XmlJavaParityDemo.JavaConfig.class)) {

    String oldValue = legacy.getBean(
            XmlJavaParityDemo.CatalogService.class)
            .report("42");
    String newValue = modern.getBean(
            XmlJavaParityDemo.CatalogService.class)
            .report("42");

    if (!oldValue.equals(newValue)) {
        throw new AssertionError("Business output changed");
    }
}
```

Đây là **test fragment**, cần imports và class scope phù hợp khi đặt ở file riêng. Một business assertion là cần nhưng chưa đủ: mục 10 còn kiểm tra alias, scope, depends-on, lazy và destroy.

### 13.3. Test theo profile/property matrix

| Tình huống | Input environment | Kỳ vọng |
| --- | --- | --- |
| Local | `dev` | Dev provider, không tạo production resource |
| CI | `test` | Deterministic fake/stub |
| Production | `prod` | Real provider và transaction infrastructure |
| Thiếu required property | Không có endpoint | Fail-fast cùng policy như XML |
| Nhiều profiles | `dev,metrics` | Candidate selection nhất quán |

Bảng là mẫu; điều chỉnh theo môi trường thực. Không so sánh cả hai context với những property sources khác nhau rồi quy lỗi cho JavaConfig.

### 13.4. Test cả shutdown

Nhiều regression chỉ xuất hiện khi context đóng: threads còn chạy, connection pool chưa giải phóng hoặc shared resource bị đóng ngoài ý muốn do inferred destroy callback. Chạy test tạo/đóng context nhiều lần và assert cleanup.

### 13.5. Không so số lượng bean definitions một cách máy móc

`@Configuration` processing có infrastructure bean definitions khác XML. Ưu tiên so set các **business bean names/types** và behavior. Nếu có dependency vào infrastructure, kiểm tra infrastructure cụ thể đó.

## 14. Lỗi điển hình khi chuyển XML sang Java

| Triệu chứng | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| `NoSuchBeanDefinitionException` theo tên | `@Bean` method đổi bean name | Chỉ định `@Bean("oldName")` và alias |
| Type injection ambiguous | XML explicit ref → Java by-type parameter | Dùng qualifier/named injection |
| Prototype thành singleton | Quên `@Scope("prototype")` | Khôi phục scope và test identities |
| Lazy bean tạo ngay | Quên `@Lazy` hoặc có eager consumer | Xem metadata và graph |
| Setter property thiếu | Factory không gọi setter | Kiểm kê XML `<property>` |
| Endpoint sai | Property source precedence đổi | So source/order/defaults |
| Init chạy hai lần | Manual call + `initMethod` | Để container gọi một lần |
| Destroy không chạy | Quên `destroyMethod` | Dùng explicit callback và close test |
| Destroy chạy ngoài ý muốn | `@Bean` suy luận `close/shutdown` | Xem `destroyMethod=""` |
| Service tạo dependency ngoài Spring | Factory dùng `new Dependency()` | Inject managed bean qua parameter |
| Bean trùng tên | XML vẫn được import cùng Java | Một owner cho mỗi definition |
| Scope đổi vì chuyển context | Bean được đưa từ child lên root | Giữ context boundary hoặc refactor có test |
| Transactions ngừng tác dụng | Mất `tx`/AOP infrastructure | Đăng ký Java equivalent, test rollback |
| Scan đăng ký thêm beans | `@ComponentScan` quá rộng | Kiểm tra package/filter |
| `FactoryBean` contract hỏng | Thay factory bằng product | Giữ factory/product semantics |
| List không mutable | Dùng `List.of` thay mutable XML collection | Dùng mutable collection khi cần |
| Prototype “không đổi” mỗi call | Singleton giữ prototype dependency một lần | Dùng provider/lookup abstraction |
| Profile sai | Đổi XML profile block thành `@Profile` không đúng phạm vi | Test tất cả profile combos |
| Custom namespace mất behavior | Chưa thay namespace handler | Giữ XML hoặc dùng vendor API đúng |

## 15. Lộ trình phát hành thực tế

**Giai đoạn 1 — Chụp baseline.** Ghi phiên bản Spring, cấu trúc contexts, file XML, property sources, profiles, danh sách bean contracts, integration tests và startup/shutdown behavior. Không làm nhiều thay đổi ngoài phạm vi.

**Giai đoạn 2 — Xây JavaConfig song song trong test.** Viết Java definitions nhưng **không đăng ký chung với XML production**. So sánh outputs qua hai test contexts.

**Giai đoạn 3 — Rà metadata.** Check aliases, scopes, lazy, lifecycle, explicit depends-on, candidates và nontrivial namespace infrastructure.

**Giai đoạn 4 — Chuyển một module.** Thay root import để dùng JavaConfig cho nhóm bean đã test; bỏ XML definitions cùng nhóm khỏi active load path.

**Giai đoạn 5 — Quan sát.** Kiểm tra startup, duplicate bean issues, transactions, worker threads, shutdown, memory/cpu, logs và metrics.

**Giai đoạn 6 — Rollback khi cần.** Có khả năng đảo nguồn config mà không bật cả XML và Java cùng lúc. Sau khi ổn định mới dọn XML cũ và cập nhật documentation ownership.

## 16. Bài tập có lời giải

### Bài 1 — Tên bean

XML `id="accountDao"` chuyển thành `@Bean AccountDao repository()`. `getBean("accountDao")` có được bảo đảm chạy?

**Đáp án:** Không. Default name thành `repository`. Giữ `@Bean("accountDao")`.

### Bài 2 — Alias

XML `<alias name="service" alias="publicService"/>`. Cách Java thuận tiện?

**Đáp án:** `@Bean(name={"service","publicService"})` trên một factory method. Đừng tạo hai beans nếu XML chỉ có alias.

### Bài 3 — Candidate sai

XML `ref="safeStore"`. Java đổi thành injection `Store store`, trong context có `fastStore` @Primary. Có giữ collaborator không?

**Đáp án:** Chưa chắc. `@Primary` có thể khiến nhận `fastStore`. Dùng qualifier/named wiring và test.

### Bài 4 — Prototype

XML `scope="prototype"`, Java chỉ `@Bean Ticket ticket()`. Sai ở đâu?

**Đáp án:** Quên `@Scope("prototype")`; Java default singleton.

### Bài 5 — Lazy

XML `lazy-init="true"`, Java có `@Lazy`. Nhưng một eager service inject bean đó. Có chắc bean lazy chưa tạo lúc refresh không?

**Đáp án:** Không; dependency bắt buộc có thể kích hoạt việc tạo sớm.

### Bài 6 — Init

Java factory gọi `start()` và `@Bean(initMethod="start")`. Điều gì xảy ra?

**Đáp án:** Callback có thể chạy hai lần; giữ một nguồn lifecycle invocation.

### Bài 7 — Destroy inference

Một bean có `public void close()`, XML cũ không khai custom destroy, Java chuyển thành `@Bean`. Cần kiểm tra gì?

**Đáp án:** Inferred destroy callback của `@Bean`. Nếu legacy yêu cầu không tự đóng, xem `destroyMethod=""` và các lifecycle interfaces liên quan.

### Bài 8 — Depends-on

XML service không inject marker nhưng có `depends-on="marker"`. Java xóa ordering này vì “không có constructor dependency”. Đúng không?

**Đáp án:** Không; có thể đổi thứ tự init/shutdown. Giữ `@DependsOn("marker")` nếu cần parity.

### Bài 9 — Property null

XML dùng nested `<null/>` cho memo. Java dùng `setMemo("")`. Có tương đương không?

**Đáp án:** Không nếu consumer phân biệt null/empty.

### Bài 10 — Collection mutability

XML list mutable, Java dùng `List.of(...)`, business code thêm phần tử. Hậu quả?

**Đáp án:** Java list không thể sửa; hành vi khác. Chọn collection mutable thích hợp và test.

### Bài 11 — ImportResource

Một repository chưa migrate còn trong `legacy.xml`; JavaConfig đã migrate service. Dùng cơ chế nào?

**Đáp án:** `@ImportResource("classpath:legacy.xml")`, đồng thời không đăng ký trùng service giữa hai nguồn.

### Bài 12 — XML namespace hạ tầng

Legacy dùng `<tx:annotation-driven/>`. JavaConfig chỉ khai `@Bean` cho services. Có bảo đảm transaction còn tác dụng?

**Đáp án:** Không. Cần transaction configuration/proxy/manager tương ứng và integration test commit/rollback.

### Bài 13 — FactoryBean

Một consumer gọi `getBean("&factory")`, Java thay `FactoryBean` bằng raw product factory method. Có parity?

**Đáp án:** Không nếu consumer phụ thuộc vào factory object; cần giữ contract hoặc refactor consumers có kế hoạch.

### Bài 14 — Parent context

Bean cũ nằm ở servlet child context, bean mới ở root; tên và class giống. Có tự tương đương?

**Đáp án:** Không; visibility, identity và lifecycle boundary có thể khác.

### Bài 15 — Tổng số bean definitions

XML context có ít definitions hơn JavaConfig context vì infrastructure. Có thể tự kết luận migration sai?

**Đáp án:** Không. So sánh business contracts và infrastructure quan trọng, không so raw count thiếu ngữ cảnh.

### Bài 16 — Runtime property

Legacy lấy config từ một property file còn Java lấy từ environment variable khác, local defaults trùng nhau. Đã đủ parity?

**Đáp án:** Chưa. Test nguồn/precedence ở các môi trường có giá trị khác defaults.

## 17. Checklist trước khi xóa XML

- Đã tìm đủ XML files thực sự được load trong mọi environments?
- Bean names, aliases và lookup theo string đã được giữ?
- Dependency injection giữ đúng provider, nhất là nơi có multiple candidates?
- Scope, lazy, depends-on và lifecycle init/destroy đúng?
- Property literals, type conversion, null/empty và collections đúng semantics?
- JavaConfig có thêm inferred destroy callbacks ngoài ý muốn?
- XML namespace infrastructure đã được chuyển có kiểm thử hoặc còn giữ qua `@ImportResource`?
- Components auto-scanned có tạo duplicate beans không?
- Không có XML và Java cùng đăng ký một bean name trong một context?
- Test cùng Spring version, profiles, inputs và property precedence?
- Assertions đã bao phủ behavior, identity, prototype và shutdown?
- Đã có rollback plan không bật đồng thời hai nguồn bean definitions?
- Config classes được tổ chức thành module boundaries dễ theo dõi?
- Những chỗ vẫn giữ XML đã được ghi rõ lý do và owner?

## 18. Kết luận

Đổi Spring XML sang JavaConfig là thay đổi **cách mô tả bean graph**. Để giữ hành vi, phải giữ bean names/aliases, injection lựa chọn đúng candidate, scope, lazy, lifecycle, ordering, profile, property precedence và các Spring infrastructure contracts. Nhiều chỗ có ánh xạ trực tiếp sang `@Bean`/`@Scope`/`@Lazy`/`@DependsOn`/`@Profile`, nhưng custom namespaces, FactoryBeans, lookup-method injection và bean inheritance cần thiết kế riêng.

Đừng chỉ xem context startup thành công là đủ. Hãy tạo **hai context riêng**, chạy cùng inputs, so sánh business output và metadata/identity/lifecycle quan trọng. Với migration từng phần, `@ImportResource` cho phép giữ XML cũ nhưng phải kiểm soát trùng definitions. Sau khi parity tests đạt và production vận hành ổn định, mới loại bỏ XML đã thay thế.

### Tài liệu Spring chính thức

- [Spring Framework 6.2 — Composing Java-based Configurations and XML](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/composing-configuration-classes.html).
- [Spring Framework 6.2 — Using `@Bean`](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/bean-annotation.html).
- [Spring Framework 6.2 — Basic Concepts: `@Bean` and `@Configuration`](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/basic-concepts.html).
- [Spring Framework 6.2 — Lazy-initialized Beans](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-lazy-init.html).
- [Spring Framework 6.2 — Using `depends-on`](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-dependson.html).
- [Spring Framework 6.2 — Bean Lifecycle](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-nature.html).
- [Spring Framework 6.2 — `Bean` Javadoc, names/aliases and destroy inference](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/Bean.html).


</details>

- [Quay lại đầu trang](#back-to-top)
