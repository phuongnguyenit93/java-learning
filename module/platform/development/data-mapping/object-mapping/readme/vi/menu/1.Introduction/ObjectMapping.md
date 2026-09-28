# Object Mapping

## <a id="object-mapping-what">1. Object Mapping là gì?</a>

Object Mapping là quá trình chuyển dữ liệu từ một object model sang một object model khác nhưng vẫn giữ hoặc biến đổi ý nghĩa dữ liệu theo contract mong muốn.

Ví dụ:

```text
Entity → DTO
Domain Model → API Response
Request DTO → Command
External Model → Internal Model
```

Object Mapping không phải serialization. Hai phía vẫn là object model trong memory.

## <a id="object-mapping-why">2. Tại sao cần Object Mapping?</a>

Một model thường không nên phục vụ mọi boundary cùng lúc.

Database entity có concern về persistence, API DTO có concern về contract bên ngoài, còn domain model có concern về business invariant.

Nếu dùng chung một class cho tất cả, thay đổi ở một boundary dễ leak sang boundary khác.

Object Mapping cho phép mỗi model giữ responsibility riêng nhưng vẫn chuyển dữ liệu giữa chúng.

## <a id="object-mapping-without">3. Nếu không có mapper thì sao?</a>

Cách đơn giản nhất là mapping thủ công:

```java
UserDto dto = new UserDto();
dto.setId(entity.getId());
dto.setName(entity.getName());
```

Manual mapping thường là lựa chọn tốt khi mapping ít, semantic rõ và cần kiểm soát tuyệt đối.

Vấn đề xuất hiện khi số field, nested object, collection và conversion rule tăng lên, khiến boilerplate và nguy cơ bỏ sót field tăng theo.

## <a id="object-mapping-model">4. Mental model</a>

```text
Source Model
     ↓
mapping rules
     ↓
conversion / transformation
     ↓
Target Model
```

Mapping rule có thể là:

- copy trực tiếp field;
- rename field;
- convert type;
- map nested object;
- map collection;
- derive một field từ nhiều source;
- bỏ qua field không thuộc target contract.

## <a id="object-mapping-tools">5. MapStruct và ModelMapper nằm ở đâu?</a>

**MapStruct** thiên về compile-time code generation: mapping implementation được tạo trong quá trình compile.

**ModelMapper** thiên về runtime mapping dựa trên convention và reflection/configuration.

Hai thư viện giải quyết cùng object-mapping concern nhưng có trade-off khác nhau về explicitness, type safety, performance và runtime behavior.

## <a id="object-mapping-boundary">6. Boundary</a>

Object Mapping:

```text
Java Object A
→ Java Object B
```

Serialization:

```text
Java Object
→ JSON / bytes / external representation
```

ORM Mapping:

```text
Object model
↔ relational persistence model
```

Ba concern liên quan nhưng không có cùng canonical owner.
