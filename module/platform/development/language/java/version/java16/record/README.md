1. Record là gì?

record là một kiểu dữ liệu đặc biệt trong Java dùng để định nghĩa immutable data class (class bất biến — không thể thay đổi sau khi tạo).

Ví dụ Lombok:

@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
private String name;
private int age;
}


👉 Với record, bạn có thể viết ngắn gọn hơn nhiều:

public record User(String name, int age) { }

⚡ 2. Record tự động làm gì?

Khi bạn dùng record, Java tự động tạo:

private final fields

Constructor

getters (không phải là getName() mà là name())

toString()

equals()

hashCode()

💡 Không cần @Data, @Getter, @AllArgsConstructor, @EqualsAndHashCode, v.v.

🧩 3. Khi nào record thay thế được Lombok
Mục đích	Lombok	Record	Ghi chú
Tạo class chỉ chứa dữ liệu	✅	✅	Record là lựa chọn tốt hơn
Tự động sinh constructor/getter/toString	✅	✅	Record làm sẵn
Bất biến (immutable)	⚠️ (phải tự set final)	✅	Record mặc định bất biến
Data Transfer Object (DTO)	✅	✅	Record rất phù hợp
Entity JPA (cần setter, default constructor)	✅	❌	Record không phù hợp
Builder pattern (@Builder)	✅	❌	Record không hỗ trợ builder
Logging (@Slf4j)	✅	❌	Record không thay thế được
Setter / Mutable object	✅	❌	Record là immutable
💬 4. Khi nên dùng cái nào?

Dùng record khi:

Class chỉ chứa dữ liệu đơn giản (DTO, request/response object, value object…)

Không cần setter hay builder

Muốn code ngắn gọn, rõ ràng

Dùng Lombok khi:

Cần builder (@Builder)

Làm việc với JPA Entities (cần setter, default constructor)

Muốn mutable object (có thể thay đổi sau khi tạo)

🔍 Ví dụ thực tế:

Record DTO:

public record LoginRequest(String username, String password) { }


Lombok Entity:

@Entity 
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {
@Id
private Long id;
private String username;
private String password;
}


👉 Tóm lại:

record có thể thay thế Lombok cho các lớp dữ liệu đơn giản (DTO)

Nhưng không thể thay Lombok hoàn toàn, đặc biệt trong các class mutable, JPA entity, hoặc builder pattern.