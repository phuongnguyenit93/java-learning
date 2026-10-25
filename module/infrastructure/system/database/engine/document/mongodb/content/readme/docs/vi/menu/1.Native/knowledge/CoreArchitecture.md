# Cấu trúc dữ liệu trong MongoDB

## <a id="bson-in-mongodb">Giải mã: Tại sao MongoDB dùng BSON thay vì JSON thuần túy?</a>

Câu trả lời nằm ở sự cân bằng giữa **tốc độ xử lý của máy tính** và **sự tiện dụng của con người**. Dưới đây là lý do tại sao BSON (Binary JSON) là "xương sống" của MongoDB.

## 1. BSON là gì?
**BSON** viết tắt của **Binary JSON**. Đây là một định dạng lưu trữ nhị phân được MongoDB dùng để lưu các Document trên đĩa cứng và truyền tải qua mạng.
* **JSON:** Định dạng văn bản dành cho con người đọc (Text-based).
* **BSON:** Định dạng nhị phân dành cho máy tính đọc (Binary-based).

## 2. 3 Lý do cốt lõi khiến BSON vượt trội

### A. Hỗ trợ nhiều kiểu dữ liệu hơn (Rich Data Types)
JSON rất hạn chế, nó chỉ hiểu: *String, Number, Boolean, Array, Object và Null*. Nó không hiểu thế nào là một ngày tháng (`Date`) hay một dãy số định danh (`ObjectId`).

* **Trong JSON:** Bạn phải lưu ngày tháng là String `"2026-03-28"`. Khi cần so sánh, máy tính phải mất công parse chuỗi đó ra lại.
* **Trong BSON:** Có kiểu dữ liệu **Date** và **Timestamp** riêng biệt dưới dạng số nguyên 64-bit. Việc so sánh ngày tháng cực kỳ nhanh. Ngoài ra còn hỗ trợ: `Decimal128` (tiền tệ), `BinData` (ảnh/file nhỏ), `RegExp`...

### B. Tốc độ quét và Duyệt dữ liệu (Efficiency & Scanning)
Hãy tưởng tượng bạn có một Document dài 1MB và muốn tìm trường cuối cùng tên là `status`.

* **Với JSON:** Máy tính phải đọc từng ký tự từ đầu đến cuối, đếm từng dấu ngoặc `{}` để biết khi nào một object kết thúc. Rất tốn CPU.
* **Với BSON:** Mỗi field đều có một **tiền tố (prefix)** ghi rõ độ dài (Size) của nó. Máy tính có thể "nhảy" (skip) qua các field không cần thiết để đi thẳng tới field nó cần. Tốc độ truy vấn tăng vọt.

### C. Tối ưu hóa cho Indexing
Nhờ cấu trúc nhị phân và độ dài cố định cho các kiểu dữ liệu số, MongoDB có thể xây dựng các cây chỉ mục (**B-Tree**) trên BSON hiệu quả hơn nhiều so với việc băm (hash) các chuỗi văn bản JSON.

## 📊 So sánh nhanh: JSON vs BSON

| Đặc điểm | JSON | BSON |
| :--- | :--- | :--- |
| **Định dạng** | Văn bản (Text) | Nhị phân (Binary) |
| **Khả năng đọc** | Con người dễ đọc | Máy tính dễ đọc |
| **Kiểu dữ liệu** | Ít (6 kiểu) | Nhiều (Date, BinData, ObjectId...) |
| **Tốc độ xử lý** | Chậm (phải parse) | Rất nhanh (nhảy cóc được) |
| **Kích thước** | Thường nhỏ hơn | Thường lớn hơn một chút (do chứa metadata) |

## 🛠️ BSON trong thực tế (Project Java của bạn)

Khi bạn dùng **Spring Data MongoDB** trong project `java-learning`:
1. Bạn viết Code Java (Object).
2. Driver sẽ chuyển Object đó thành **BSON** để gửi lên MongoDB Atlas.
3. Khi bạn xem trên Navicat/Compass, các công cụ này chuyển ngược BSON thành **JSON** để bạn dễ đọc.

---

### 💡 Sự thật thú vị:
Cái tên `_id` với giá trị `ObjectId("...")` mà bạn thấy chính là một kiểu dữ liệu 12-byte đặc trưng của BSON. Nó giúp đảm bảo tính duy nhất trên toàn cầu mà không cần cơ chế tăng số tự động (**Auto-increment**) vốn rất khó thực hiện trong hệ thống phân tán.

```java
	// Ví dụ minh họa ObjectId trong BSON (12-byte)
	// 4 byte: Timestamp | 5 byte: Random value | 3 byte: Increment counter
	ObjectId myId = new ObjectId();
	System.out.println("BSON ObjectId: " + myId.toHexString());
```

## <a id="document-in-mongodb"> Khám phá Document: Đơn vị lưu trữ cốt lõi trong MongoDB</a>

Trong MongoDB, một **Document** là đơn vị lưu trữ dữ liệu cơ bản (tương đương với một *Row* trong SQL), nhưng linh hoạt hơn nhiều nhờ cấu trúc phân cấp và khả năng lồng ghép.



## 1. Field (Trường) và Value (Giá trị)
Đây là cặp Key-Value cơ bản nhất.
* **Field:** Tên của thuộc tính (luôn là kiểu String). **Lưu ý:** Có phân biệt chữ hoa/chữ thường (`name` khác `Name`).
* **Value:** Giá trị của thuộc tính (String, Number, Boolean, Date, hoặc Object/Array).

  ```json
  {
    "name": "Laptop Gaming",  // Field: "name", Value: "Laptop Gaming"
    "price": 2500,            // Field: "price", Value: 2500
    "in_stock": true          // Field: "in_stock", Value: true
  }
  ```

## 2. Nested Documents (Document lồng nhau)
Đây là điểm "ăn tiền" nhất. Một Field có thể chứa một Document khác bên trong, giúp gom dữ liệu liên quan mà không cần tách bảng (No JOIN).



* **Ví dụ:** Thay vì để `city`, `street` rời rạc, ta gom chúng vào object `address`.
* **Dot Notation:** Bạn dùng dấu chấm để truy cập sâu: `address.city`.

  ```json
  {
    "customer_name": "Nguyen Van A",
    "address": {
      "street": "123 Đường ABC",
      "city": "Ho Chi Minh",
      "zipcode": 70000
    }
  }
  ```

## 3. Arrays (Mảng)
MongoDB cho phép một Field chứa một danh sách các giá trị, từ đơn giản đến phức tạp.
* **Mảng đơn giản:** Lưu danh sách các nhãn (`tags`).
* **Mảng Document:** Lưu danh sách các đối tượng con (ví dụ: các `reviews` của sản phẩm).

  ```json
  {
    "product": "iPhone 15",
    "tags": ["apple", "smartphone", "ios"],
    "reviews": [
      { "user": "Tuấn", "rating": 5, "comment": "Rất tốt" },
      { "user": "Hoa", "rating": 4, "comment": "Hơi đắt" }
    ]
  }
  ```

## 4. So sánh nhanh với SQL

| Thành phần | Trong SQL (Relational) | Trong MongoDB (Document) |
| :--- | :--- | :--- |
| **Đơn vị lưu trữ** | Dòng (Row) | Document (BSON) |
| **Cấu trúc con** | Phải tách ra bảng khác (**JOIN**) | Lồng trực tiếp (**Nested**) |
| **Danh sách con** | Phải tạo bảng quan hệ 1-N | Lưu dạng **Mảng (Array)** |



## 5. Tại sao cấu trúc này lại mạnh mẽ?

* **Tính cục bộ của dữ liệu (Data Locality):** Khi đọc một bản ghi, bạn có toàn bộ thông tin liên quan ngay lập tức. CPU không cần tốn công đi "tìm kiếm" dữ liệu ở các bảng khác qua lệnh JOIN phức tạp.
* **Tính đa hình (Polymorphism):** Document A có thể có mảng `tags`, nhưng Document B trong cùng Collection có thể không cần field đó hoặc thay bằng field `category`. MongoDB không bắt lỗi Schema (Schema-less).

---

### 💡 Áp dụng vào dự án java-learning:
Khi bạn sử dụng **Spring Data MongoDB**, các POJO Java của bạn sẽ được ánh xạ (mapping) trực tiếp:
* `@Document`: Ánh xạ Class thành một Collection.
* `@Id`: Ánh xạ trường ID (thường là `ObjectId`).
* `List<Review>`: Sẽ tự động được chuyển thành một **Array** các Nested Documents trong MongoDB.

```java
  @Document(collection = "products")
  public class Product {
      @Id
      private String id;
      private String name;
      private Address address; // Nested Document
      private List<String> tags; // Array
  }
```

## <a id="objectId-in-mongodb"> Giải mã ObjectId: "Chứng minh thư" 12-byte thông minh của MongoDB</a>

Thay vì dùng kiểu số tự động tăng (Auto-increment) dễ gây xung đột khi mở rộng nhiều server, MongoDB sử dụng **ObjectId** — một cấu trúc 12-byte (hiển thị dưới dạng 24 ký tự Hex) đảm bảo tính duy nhất trên toàn cầu.

## 1. Phân rã cấu trúc 12-byte

Cấu trúc này được chia thành 3 phần chiến lược:

| Thành phần | Kích thước | Ý nghĩa |
| :--- | :--- | :--- |
| **Timestamp** | 4 bytes | Lưu thời gian (giây) khi Document được tạo (Unix Epoch). |
| **Random Value** | 5 bytes | Mã định danh duy nhất cho mỗi máy chủ/tiến trình (sinh ngẫu nhiên). |
| **Counter** | 3 bytes | Số đếm tự tăng, bắt đầu bằng một giá trị ngẫu nhiên. |

## 2. Sức mạnh của 4 byte Timestamp đầu tiên

Đây là phần "vi diệu" nhất trong thiết kế của MongoDB:
* **Tiết kiệm dung lượng:** Bạn không nhất thiết phải tạo thêm trường `created_at` (tốn thêm 8 byte) nếu chỉ cần biết thời gian tạo cơ bản.
* **Sắp xếp mặc định:** Vì Timestamp nằm ở đầu, các ObjectId tạo sau thường có giá trị lớn hơn. Khi bạn thực hiện `sort({ _id: 1 })`, thực chất bạn đang sắp xếp theo thời gian tạo mà không cần thêm Index phụ.

## 3. Cách trích xuất thời gian từ ObjectId trong Java

Trong project `java-learning`, nếu bạn dùng MongoDB Java Driver, việc lấy thời gian cực kỳ đơn giản:

```java
	// Trích xuất thông tin từ ObjectId
	ObjectId id = document.getObjectId("_id");
	
	// Trả về đối tượng Date chứa ngày giờ tạo
	Date creationDate = id.getDate(); 
	
	// Trả về thời gian dạng Unix timestamp (giây)
	long epochTime = id.getTimestamp();
```

* **Mẹo:** Trong Mongo Shell, bạn chỉ cần gõ: `ObjectId("...").getTimestamp()`.

## 4. Tại sao cần 5 byte Random và 3 byte Counter?

* **5 byte Random:** Đảm bảo nếu hai máy chủ khác nhau cùng tạo bản ghi vào **cùng một giây**, chúng vẫn không trùng ID nhờ mã máy ngẫu nhiên khác nhau.
* **3 byte Counter:** Đảm bảo trên **cùng một máy**, nếu bạn tạo hàng ngàn bản ghi trong cùng một giây, số đếm này sẽ tăng dần (tối đa $16,777,216$ giá trị trong 1 giây) để tránh trùng lặp tuyệt đối.

---

### 💡 Mẹo tối ưu cho Backend Developer:

Nếu bạn cần lọc dữ liệu theo ngày (ví dụ: lấy các đơn hàng tạo trong ngày hôm nay), việc lọc qua trường `_id` sẽ nhanh hơn rất nhiều so với lọc qua một trường `date` tự tạo. Lý do là `_id` luôn có **Index mặc định** và dữ liệu được sắp xếp vật lý theo thứ tự thời gian này, giúp giảm thiểu việc quét đĩa (Disk I/O).

```java
	// Tư duy tối ưu: Tìm theo ID thay vì tìm theo Date field
	Query query = new Query(Criteria.where("_id").gte(minObjectIdForToday));
```

## <a id="dynamic-schema"> Khám phá Dynamic Schema: Sự tự do tuyệt đối của MongoDB</a>

Trong MongoDB, **Schema (Lược đồ) là động**. Điều này có nghĩa là trong cùng một **Collection** (tương đương với Bảng), mỗi **Document** (tương đương với Dòng) có thể có cấu trúc hoàn toàn khác nhau.



## 1. Sự khác biệt về tư duy: Cố định vs. Linh hoạt

* **SQL (Static Schema):** Giống như một cuốn biểu mẫu in sẵn. Nếu bạn muốn thêm một thông tin mới (ví dụ: số điện thoại thứ 2), bạn phải sửa lại toàn bộ biểu mẫu bằng lệnh `ALTER TABLE`.
* **MongoDB (Dynamic Schema):** Giống như một tờ giấy trắng. Bạn muốn viết gì vào đó cũng được. Document 1 có thể có 3 trường, Document 2 có 10 trường, và Document 3 lại có các trường lồng nhau phức tạp.

## 2. Ví dụ thực tế trong Project java-learning

Giả sử bạn quản lý một Collection `products`. Mỗi loại sản phẩm sẽ có những thông số kỹ thuật (specs) đặc thù:

```json
	// Document 1: Laptop (Cần RAM, CPU)
	{
	  "name": "MacBook M3",
	  "type": "Electronics",
	  "specs": { "cpu": "M3", "ram": "16GB" }
	}
	
	// Document 2: Áo thun (Cần Size, Chất liệu)
	{
	  "name": "Polo Shirt",
	  "type": "Clothing",
	  "size": "L",
	  "material": "Cotton"
	}
```
Trong SQL, bạn sẽ phải tạo rất nhiều bảng con (Join) hoặc để cực kỳ nhiều cột `NULL`. Trong MongoDB, bạn chỉ việc đẩy chúng vào cùng một chỗ.

## 3. Lợi ích của Schema động

* **Phát triển cực nhanh (Agility):** Cứ code đến đâu, thêm trường đến đó. Không cần tốn thời gian họp bàn để thống nhất Schema hay chạy script migrate DB phức tạp.
* **Hỗ trợ dữ liệu biến đổi:** Cực kỳ phù hợp cho hệ thống **IoT** (mỗi cảm biến gửi dữ liệu khác nhau) hoặc **E-commerce** (sản phẩm đa dạng chủng loại).
* **Tiết kiệm bộ nhớ:** Bạn chỉ lưu những gì bạn thực sự có. Không có các giá trị `NULL` dư thừa chiếm dụng không gian như trong SQL.

## 4. Mặt trái cần lưu ý (Cảnh báo cho Backend Developer)

"Quyền năng lớn đi kèm trách nhiệm lớn". Schema linh hoạt không có nghĩa là bạn nên làm việc cẩu thả:

* **Gánh nặng dồn lên Code Java:** Database không bắt lỗi cấu trúc, nên Code Java phải kiểm tra kỹ (`null-check`). Nếu gọi `product.getSpecs()` trên một bản ghi "Áo thun", bạn sẽ dính ngay `NullPointerException`.
* **Khó thống kê:** Nếu đặt tên trường không đồng nhất (lúc thì `price`, lúc thì `cost`), việc viết câu query tính tổng doanh thu sẽ là một thảm họa.



---

### 💡 Mẹo: Schema Validation cho dự án lớn

Dù là Dynamic Schema, nhưng khi dự án `java-learning` của bạn lớn dần, bạn nên sử dụng tính năng **Schema Validation**. Nó cho phép bạn bắt buộc một số trường cốt lõi "phải có" (ví dụ: mọi sản phẩm đều phải có `name` và `price`).

**Tư duy đúng:** Hãy dùng NoSQL để linh hoạt ở những chỗ cần linh hoạt, nhưng hãy giữ kỷ luật thép ở những dữ liệu quan trọng nhất.

```javascript
	// Ví dụ lệnh tạo Validation trong MongoDB Shell
	db.createCollection("products", {
	   validator: {
	      $jsonSchema: {
	         required: [ "name", "price" ]
	      }
	   }
	})
```

## <a id="acid-in-mongodb">ACID: "Giấy bảo hiểm" cho tính toàn vẹn dữ liệu</a>

**ACID** là tập hợp 4 đặc tính vàng đảm bảo các giao dịch trên cơ sở dữ liệu (**Database Transaction**) được thực hiện một cách tin cậy, ngay cả khi có sự cố phần cứng hay lỗi phần mềm.



## 1. Giải mã 4 chữ cái trong ACID

### A - Atomicity (Tính nguyên tử)
Quy tắc **"Tất cả hoặc không có gì"**.
* Một giao dịch gồm nhiều bước (Trừ tiền ví A -> Cộng tiền ví B).
* Nếu một bước thất bại, toàn bộ giao dịch sẽ bị hủy bỏ (**Rollback**). Dữ liệu quay lại trạng thái ban đầu như chưa có chuyện gì xảy ra.

### C - Consistency (Tính nhất quán)
Đảm bảo dữ liệu luôn tuân thủ các quy tắc nghiệp vụ sau khi giao dịch kết thúc.
* **Ví dụ:** Tổng số tiền trong hệ thống không đổi sau khi chuyển khoản, hoặc các ràng buộc `NOT NULL`, `UNIQUE` luôn được thỏa mãn.

### I - Isolation (Tính cô lập)
Đảm bảo các giao dịch chạy đồng thời không ảnh hưởng lẫn nhau.
* Nếu hai người cùng rút tiền từ một tài khoản, hệ thống sẽ cách ly sao cho số dư cuối cùng luôn chính xác, không bị tình trạng "ghi đè" (Race Condition).



### D - Durability (Tính bền vững)
Khi hệ thống báo "Thành công", dữ liệu đó phải được lưu **vĩnh viễn** vào ổ cứng.
* Ngay cả khi Server sập hay mất điện ngay sau giây đó, dữ liệu vẫn phải tồn tại khi hệ thống khởi động lại.

---

## 2. MongoDB có hỗ trợ ACID không?

Đây là một hiểu lầm phổ biến: *"NoSQL thì không có ACID"*. Thực tế là:
* **Trước đây:** MongoDB chỉ hỗ trợ ACID trên đơn bản ghi (**Single-document**).
* **Hiện nay (Từ v4.0+):** MongoDB đã hỗ trợ **Multi-document Transactions**. Bạn có thể thực hiện thay đổi trên nhiều Collection khác nhau trong cùng một Transaction một cách an toàn.

## 3. Thực hành ACID trong Spring Boot (Dự án java-learning)

Khi dùng **Spring Data MongoDB**, bạn có thể áp dụng ACID cực kỳ dễ dàng bằng Annotation `@Transactional`:

```java
	@Service
	public class BankService {
	
	    @Transactional // <--- Kích hoạt cơ chế ACID cho phương thức này
	    public void transferMoney(String fromId, String toId, double amount) {
	        // 1. Trừ tiền ví A
	        accountRepository.decreaseBalance(fromId, amount);
	        
	        // Giả sử ở đây xảy ra lỗi Logic hoặc lỗi Mạng...
	        
	        // 2. Cộng tiền ví B
	        accountRepository.increaseBalance(toId, amount);
	        
	        // Nhờ @Transactional, nếu bước 2 lỗi, bước 1 sẽ tự động Rollback!
	    }
	}
```

---

## 📊 Khi nào có thể "nới lỏng" ACID?

Dù ACID rất tốt, nhưng nó làm hệ thống chạy chậm hơn vì tốn tài nguyên để "canh giữ". Bạn có thể ưu tiên hiệu năng hơn ACID khi:
* **Lưu Log hệ thống:** Mất một vài dòng log không gây hậu quả nghiêm trọng.
* **Lưu lượt Like, View:** Sai lệch 1-2 đơn vị không quá quan trọng đối với trải nghiệm người dùng.
* **Dữ liệu thống kê:** Không liên quan đến tiền bạc, pháp lý hay các ràng buộc chặt chẽ.



### 💡 Lời khuyên cho Backend Developer:
Trong Microservices, việc duy trì ACID trên nhiều Service khác nhau là cực kỳ khó (Distributed Transaction). Lúc này, người ta thường dùng **Saga Pattern** mà chúng ta đã thảo luận để thay thế cho ACID truyền thống.

# Giải mã: Tại sao MongoDB không sử dụng HikariCP?

Khi chuyển từ RDBMS (MySQL, PostgreSQL) sang NoSQL (MongoDB), một trong những thay đổi lớn nhất ở tầng hạ tầng là cách quản lý kết nối. MongoDB **không cần** HikariCP vì nó có "vũ khí" riêng.



## <a id="mongodb-with-hikaricp"> Tại sao MongoDB không cần HikariCP? </a>

* **Bộ quản lý kết nối bản sắc (Internal Connection Pool):** MongoDB Java Driver (thư viện lõi mà Spring Data MongoDB sử dụng) đã tích hợp sẵn một bộ quản lý kết nối cực kỳ mạnh mẽ.
* **Sự khác biệt về giao thức:** * **HikariCP** được thiết kế riêng cho **JDBC** (Java Database Connectivity) – chuẩn kết nối đồng bộ dành cho SQL.
  * **MongoDB** sử dụng giao thức truyền tải nhị phân riêng và hỗ trợ các hoạt động **Bất đồng bộ (Async)**, nên nó cần một bộ Pool riêng để tối ưu hiệu suất đặc thù.

## 2. Cách MongoDB quản lý Connection trong Spring Boot

Thay vì cấu hình qua nhánh `hikari`, bạn sẽ tinh chỉnh trực tiếp trong `spring.data.mongodb`. Các thông số này có vai trò tương đương nhưng thuộc về Driver của Mongo:

```yaml
	spring:
	  data:
	    mongodb:
	      uri: mongodb+srv://<user>:<pass>@cluster.mongodb.net/database
	      # Các thông số Pool tương đương với Hikari:
	      additional-settings:
	        # Tương đương maximum-pool-size của Hikari
	        max-connection-pool-size: 100 
	        # Tương đương minimum-idle của Hikari
	        min-connection-pool-size: 10  
	        # Thời gian chờ để lấy một kết nối từ pool (ms)
	        max-wait-time: 120000         
```

## 📊 So sánh nhanh để ghi nhớ

| Đặc điểm | RDBMS (MySQL/PostgreSQL) | MongoDB |
| :--- | :--- | :--- |
| **Thư viện kết nối** | JDBC Driver | MongoDB Java Driver |
| **Connection Pool** | **HikariCP** (Thư viện bên ngoài) | **Internal Pool** (Tích hợp sẵn) |
| **Cấu hình Spring** | `spring.datasource.hikari.*` | `spring.data.mongodb.*` |
| **Cơ chế xử lý** | Chặn (Blocking/Synchronous) | Hỗ trợ cả Chặn và Bất đồng bộ (Reactive) |

---

### 💡 Lưu ý cho Backend Developer:
Mặc dù không dùng HikariCP, nhưng triết lý tối ưu hóa vẫn giống nhau:
1.  **Đừng đặt `max-connection-pool-size` quá lớn** nếu Database server của bạn không đủ mạnh (CPU/RAM).
2.  Nếu bạn đang dùng **Spring WebFlux** (Lập trình phản ứng), hãy đảm bảo sử dụng `spring-boot-starter-data-mongodb-reactive` để tận dụng tối đa khả năng Non-blocking của bộ Pool này.

```java
    // Ví dụ kiểm tra trạng thái Pool trong Code (nếu cần)
    MongoClient mongoClient = MongoClients.create(uri);
    // Driver sẽ tự động quản lý việc mượn/trả kết nối ngầm định
```