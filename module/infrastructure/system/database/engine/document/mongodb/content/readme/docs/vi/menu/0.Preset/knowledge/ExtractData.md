# Trích xuất dữ liệu từ MongoDB

## <a id="extract-mongo-atlas"> Hướng dẫn: Backup dữ liệu MongoDB Atlas về máy Local </a>

Trích xuất dữ liệu từ MongoDB Atlas thông qua CLI bằng Docker hoặc Database Tool là kỹ thuật quan trọng giúp bạn chủ động quản lý bản sao lưu mà không phụ thuộc hoàn toàn vào Cloud.


## 1. Sử dụng MongoDB Database Tools (Cài trực tiếp)
Đây là cách dùng lệnh `mongodump` thuần túy từ terminal sau khi bạn đã cài đặt bộ công cụ Database Tools trên máy host.

```bash
	mongodump --uri="mongodb+srv://<db_username>:<db_password>@<db_host>/<db_database>?appName=<app_name>" --out="<backup_path>/full_backup_<timestamp>"
```
* **Lưu ý:** Với Atlas, bạn chỉ cần trỏ đến Cluster Host để dump tất cả hoặc thêm `<db_database>` vào sau dấu `/` để chỉ định đích danh một database.

## 2. Sử dụng Docker (CLI qua Container)
Cách này cực kỳ hữu hiệu vì bạn không cần cài đặt thêm bất kỳ công cụ nào lên Windows/Mac, giúp giữ máy host luôn "sạch".



**Đối với Windows (PowerShell/CMD):**
```powershell
    docker run --rm `
      -v "<local_path_to_backup>:/backup" `
      mongo:latest `
      mongodump --uri="mongodb+srv://<db_username>:<db_password>@<db_host>/<db_database>" --out="/backup"
```

**Đối với Linux/macOS:**
```bash
    docker run --rm \
      -v "<local_path_to_backup>:/backup" \
      mongo:latest \
      mongodump --uri="mongodb+srv://<db_username>:<db_password>@<db_host>/<db_database>" --out="/backup"
```

## 3. Tích hợp vào Gradle Task (Tự động hóa)
Trong project `java-learning`, bạn nên định nghĩa các biến trong `gradle.properties` hoặc `.env` để task tự động xử lý.

```gradle
	task backupAtlasGeneral(group: "database") {
	    doLast {
	        def dbUser = project.findProperty("mongo.user") ?: "<db_username>"
	        def dbPass = project.findProperty("mongo.pass") ?: "<db_password>"
	        def dbHost = project.findProperty("mongo.host") ?: "<db_host>"
	        def dbName = project.findProperty("mongo.db")   ?: "<db_database>"
	        def timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
	        
	        def localExportDir = "${project.rootDir}/backup/mongo/dump_${timestamp}"
	        file(localExportDir).mkdirs()
	
	        exec {
	            commandLine 'docker', 'run', '--rm', 
	                '-v', "${localExportDir}:/data_dump", 
	                'mongo:latest', 
	                'mongodump', 
	                '--uri', "mongodb+srv://${dbUser}:${dbPass}@${dbHost}/${dbName}", 
	                '--out', '/data_dump'
	        }
	        
	        println "--------------------------------------------------"
	        println ">>> BACKUP COMPLETED | Location: ${localExportDir}"
	        println "--------------------------------------------------"
	    }
	}
```

---

## 🔍 Giải thích các tham số (Placeholders)

| Tham số | Ý nghĩa | Ví dụ |
| :--- | :--- | :--- |
| **<db_username>** | Tên người dùng database. | `admin` |
| **<db_password>** | Mật khẩu (cần encode ký tự đặc biệt). | `P@ssw0rd123` |
| **<db_host>** | Địa chỉ Cluster Atlas. | `cluster0.abcde.mongodb.net` |
| **-v (Volume)** | Ánh xạ thư mục máy thật vào `/backup` của Docker. | `C:/backups:/backup` |

### 💡 Lưu ý bảo mật:
Khi sử dụng tham số `--uri` chứa mật khẩu, hãy cẩn thận với lịch sử lệnh (`history`) trên Terminal. Sử dụng tệp cấu hình hoặc biến môi trường là cách an toàn hơn để bảo vệ thông tin đăng nhập của bạn.

## <a id="backup-mongo-local"> Hướng dẫn: Backup MongoDB nội bộ (Local/Docker)</a>

Với MongoDB chạy nội bộ — như một Container Docker hoặc Server trong mạng LAN — cấu hình sẽ đơn giản hơn vì không cần giao thức `+srv` và thường sử dụng Port mặc định `27017`.



## 1. Sử dụng MongoDB Database Tools (Máy Host)
Nếu bạn đã cài đặt bộ Tool trực tiếp trên hệ điều hành (Windows/Linux), bạn có thể kết nối thẳng tới IP/Host của Database.

```bash
	mongodump --host="<db_host>" --port="<db_port>" --username="<db_username>" --password="<db_password>" --db="<db_database>" --out="<backup_path>"
```
* **Mẹo:** Nếu không bật chế độ xác thực (No Auth), bạn có thể bỏ qua `--username` và `--password`. Bạn cũng có thể dùng chuỗi kết nối rút gọn: `--uri="mongodb://<user>:<pass>@<host>:<port>/<db>"`

## 2. Sử dụng Docker (Backup qua Network)
Trường hợp bạn dùng một "Container thợ" (temporary container) để lấy dữ liệu từ một Container MongoDB đang chạy khác.

```bash
	docker run --rm \
	  --network="<docker_network_name>" \
	  -v "<local_path_to_backup>:/backup" \
	  mongo:latest \
	  mongodump --host="<container_name_or_ip>" --port="27017" --out="/backup"
```
* **Lưu ý:** Bạn phải cho Container backup vào cùng mạng (`--network`) với Container MongoDB thì chúng mới "thấy" nhau qua `<container_name>`.

## 3. Cách "Lười" nhưng Hiệu quả (Dùng docker exec)
Nếu bạn đã có Container MongoDB tên là `my_mongo`, bạn có thể ra lệnh cho chính nó tự dump rồi copy file ra ngoài. Cách này không cần cài thêm tool hay chạy thêm container mới.



* **Bước 1:** Ra lệnh cho container tự dump vào thư mục tạm bên trong chính nó:
```bash
  docker exec my_mongo mongodump --db="<db_database>" --out="/tmp/dump"
```
* **Bước 2:** Copy thư mục dump từ trong container ra máy thật:
```bash
  docker cp my_mongo:/tmp/dump "<local_path_on_your_machine>"
```
* **Bước 3:** Dọn dẹp rác bên trong container:
```bash
  docker exec my_mongo rm -rf /tmp/dump
```

## 4. Tích hợp vào Gradle Task cho Local DB
Trong project `java-learning`, nếu bạn dùng Docker Compose để chạy DB local, task của bạn sẽ tự động hóa cả 3 bước trên:

```gradle
	task backupLocalMongo(group: "database") {
	    doLast {
	        def dbContainerName = "my_mongodb_container" // Tên container trong docker-compose
	        def timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
	        def localPath = "${project.rootDir}/backup/local_mongo_${timestamp}"
	
	        // 1. Dump trực tiếp trong container
	        exec {
	            commandLine 'docker', 'exec', dbContainerName, 'mongodump', '--out', '/tmp/backup'
	        }
	        
	        // 2. Copy ra máy host
	        file(localPath).mkdirs()
	        exec {
	            commandLine 'docker', 'cp', "${dbContainerName}:/tmp/backup/.", localPath
	        }
	
	        // 3. Xóa rác trong container
	        exec {
	            commandLine 'docker', 'exec', dbContainerName, 'rm', '-rf', '/tmp/backup'
	        }
	
	        println ">>> Local Backup Finished: ${localPath}"
	    }
	}
```

---

## 📊 So sánh sự khác biệt

| Đặc điểm | Cloud (Atlas) | Local (Docker/Internal) |
| :--- | :--- | :--- |
| **Giao thức** | Bắt buộc `mongodb+srv://` | Thường dùng `mongodb://` |
| **Cổng (Port)** | Không cần chỉ định | Mặc định `27017` |
| **Bảo mật** | Whitelist IP trên Portal | Cấu hình Docker Network |
| **Công cụ** | Cần CLI/Tool từ xa | Có thể dùng `docker exec` |

### 💡 Lời khuyên:
Đối với môi trường Local, phương pháp **`docker exec` + `docker cp`** là an toàn và ít lỗi nhất vì nó thực hiện dump ngay tại "nguồn", tránh được các vấn đề về băng thông mạng hoặc quyền truy cập từ xa.