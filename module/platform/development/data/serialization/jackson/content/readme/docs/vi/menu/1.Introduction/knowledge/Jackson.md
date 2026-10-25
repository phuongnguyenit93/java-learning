# Jackson

## <a id="jackson-what">1. Jackson là gì?</a>

Jackson là một bộ thư viện data binding/serialization phổ biến trong hệ sinh thái Java, thường được dùng để chuyển đổi giữa Java object và JSON.

```text
Java Object
↔
JSON representation
```

`ObjectMapper` là API trung tâm thường gặp của Jackson Databind.

## <a id="jackson-why">2. Tại sao Jackson tồn tại?</a>

Application thường phải trao đổi dữ liệu qua boundary như HTTP API, message, file hoặc external system.

Các boundary đó không thể gửi trực tiếp Java object graph trong memory. Object phải được biểu diễn thành một format có thể truyền/lưu và sau đó dựng lại thành model phía nhận.

Jackson tự động hóa phần lớn quá trình binding giữa Java type và JSON structure.

## <a id="jackson-before">3. Nếu không có Jackson thì sao?</a>

Developer có thể tự tạo JSON string và tự parse từng field.

Cách đó khả thi với payload rất nhỏ nhưng nhanh chóng trở nên khó bảo trì khi có nested object, collection, generic type, naming policy, date-time hoặc schema evolution.

## <a id="jackson-model">4. Mental model</a>

```text
Java object
    ↓ serialization
JSON tokens/tree/text
    ↓ deserialization
Java object
```

Jackson có nhiều layer API:

- data binding qua `ObjectMapper`;
- tree model;
- streaming parser/generator;
- annotation và module extension.

## <a id="jackson-object-mapping-boundary">5. Jackson khác Object Mapping như thế nào?</a>

MapStruct/ModelMapper chủ yếu chuyển:

```text
Object A → Object B
```

Jackson chủ yếu chuyển:

```text
Object ↔ external data representation
```

Jackson có thể bind JSON vào DTO nhưng canonical concern ở đây vẫn là serialization/data binding chứ không phải DTO-to-DTO mapping.

## <a id="jackson-spring-boundary">6. Boundary với Spring</a>

Spring Boot có thể auto-configure Jackson cho HTTP message conversion, nhưng Jackson bản thân không thuộc Spring.

Module này sở hữu Jackson mechanics; Spring-specific HTTP integration thuộc Spring web modules.
