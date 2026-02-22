# 📘 Gradle Build Lifecycle & buildSrc Guide

Tài liệu này giải thích cơ chế vận hành của Gradle, thứ tự ưu tiên của `buildSrc` và cách quản lý Dependency cho các Script tùy chỉnh (như xử lý Kafka/YAML).

---

## 🏗️ 1. Ba Giai Đoạn Của Vòng Đời Build (Build Lifecycle)

Gradle không chạy code từ trên xuống dưới một cách đơn giản, nó trải qua 3 giai đoạn:



| Giai đoạn | Tên gọi | Nhiệm vụ |
| :--- | :--- | :--- |
| **1. Initialization** | Khởi tạo | Xác định dự án nào tham gia build. Tìm `settings.gradle`. **Đây là lúc `buildSrc` được biên dịch.** |
| **2. Configuration** | Cấu hình | Thực thi tất cả file `.gradle`. Xây dựng đồ thị Task (DAG). Các lệnh `apply from` chạy ở đây. |
| **3. Execution** | Thực thi | Chạy các logic nằm trong `doLast {}` của các Task được gọi. |

---

## 🚀 2. Tại sao `buildSrc` lại đặc biệt?

`buildSrc` là một thư mục **Reserved (Dành riêng)**. Gradle tự động ưu tiên nó hơn tất cả:

1.  **Auto-Compile:** Gradle biên dịch `buildSrc` TRƯỚC khi đọc bất kỳ file `build.gradle` nào.
2.  **Global Classpath:** Mọi thư viện khai báo trong `buildSrc/build.gradle` (bằng `implementation`) sẽ tự động xuất hiện trong mọi file `.gradle` của toàn project.
3.  **No More Buildscript:** Bạn không cần viết khối `buildscript { ... }` ở từng file con nữa.

---

## 📂 3. Cấu Trúc Thư Mục Chuẩn (Best Practice)

Để xử lý logic YAML cho Kafka một cách chuyên nghiệp, hãy tổ chức như sau:

    ```text
    project-root/
    ├── buildSrc/
    │   ├── build.gradle       <-- Khai báo implementation 'org.yaml:snakeyaml:2.2'
    │   └── src/main/groovy/   <-- Viết logic xử lý YAML tại đây (file .groovy)
    ├── service/producer/
    │   └── task.gradle        <-- Script riêng, chỉ việc import và gọi logic
    ├── build.gradle           <-- File chính, chứa apply from: 'service/producer/task.gradle'
    └── settings.gradle
    ```

---

## ⚠️ 4. Phân Biệt "Nơi Đặt" Dependency

Lỗi "Unable to resolve class" thường do đặt nhầm chỗ:

* **Trong `buildSrc/build.gradle`**: Dùng cho logic của chính build tool (như Parse YAML, tạo Task).
    * *Từ khóa:* `implementation`.
* **Trong `build.gradle` (khối buildscript)**: Dùng cho plugin/thư viện của riêng file script đó.
    * *Từ khóa:* `classpath`.
* **Trong `allprojects { dependencies { ... } }`**: Dùng cho code Java của ứng dụng (Business Logic).
    * *Từ khóa:* `implementation` / `api`.

---

## 🛠️ 5. Lệnh Kiểm Tra Nhanh

Sử dụng Terminal để kiểm tra quá trình Build:

    ```bash
    # Xem chi tiết quá trình khởi tạo và kiểm tra buildSrc
    ./gradlew help --info
    
    # Kiểm tra danh sách Task sau giai đoạn Configuration
    ./gradlew tasks
    
    # Ép buộc biên dịch lại code trong buildSrc
    ./gradlew :buildSrc:build
    ```



# 🛠️ Tìm hiểu về buildscript trong Gradle

`buildscript` là khối cấu hình cho chính bản thân quá trình build. Nó định nghĩa các công cụ mà Gradle cần để có thể hiểu và biên dịch dự án của bạn.

---

## 🧐 1. buildscript là gì?

Thông thường, Gradle chỉ biết những lệnh cơ bản. Nếu bạn muốn dùng thêm các tính năng nâng cao (như đọc file YAML, kết nối Database khi build, hoặc dùng plugin bên thứ 3), bạn phải khai báo chúng trong `buildscript`.

---

## 🏗️ 2. Cấu trúc của một khối buildscript

Một khối `buildscript` tiêu chuẩn thường bao gồm hai phần: `repositories` (nơi lấy công cụ) và `dependencies` (công cụ cụ thể là gì).

    ```groovy
    buildscript {
        repositories {
            // Nơi Gradle đi tìm công cụ (như cái siêu thị)
            mavenCentral()
        }
        dependencies {
            // Công cụ cụ thể mà Gradle cần tải về để chạy script
            // Ví dụ: SnakeYAML để parse file cấu hình trong lúc build
            classpath 'org.yaml:snakeyaml:2.2'
        }
    }
    ```

---

## 🔄 3. Sự khác biệt giữa 'classpath' và 'implementation'

Đây là điểm dễ gây nhầm lẫn nhất đối với người mới:

| Từ khóa | Nằm trong khối | Mục đích |
| :--- | :--- | :--- |
| **`classpath`** | `buildscript` | Thư viện hỗ trợ quá trình build (dành cho file `.gradle`). |
| **`implementation`** | `dependencies` | Thư viện hỗ trợ chạy ứng dụng (dành cho file `.java`). |

---

## ⏳ 4. Thứ tự thực thi

Gradle luôn ưu tiên xử lý khối `buildscript` đầu tiên, trước khi đọc bất kỳ dòng code nào khác trong file `.gradle`.

1.  **Quét file**: Gradle tìm khối `buildscript`.
2.  **Tải công cụ**: Tải các thư viện khai báo trong `classpath`.
3.  **Compile Script**: Sử dụng các thư viện đó để hiểu các lệnh `import` và logic bên dưới của file `.gradle`.

---

## 💡 5. Khi nào bạn cần dùng buildscript?

Bạn cần đến nó khi:
* Muốn dùng một class bên ngoài (như `org.yaml.snakeyaml.Yaml`) ngay trong file `.gradle`.
* Muốn dùng các Plugin cũ chưa được đưa lên Gradle Plugin Portal.
* Cần thực hiện các tác vụ logic phức tạp trước khi dự án chính được biên dịch.

    ```groovy
    // Sau khi có buildscript ở trên, bạn mới có thể làm thế này:
    import org.yaml.snakeyaml.Yaml

    task printYaml {
        doLast {
            def yaml = new Yaml()
            // Logic của bạn ở đây...
        }
    }
    ```

# 🎓 Gradle Deep Dive: Lifecycle, Scopes & ClassLoaders

Tài liệu này giải thích các nguyên lý cốt lõi về vòng đời Gradle, lý do tại sao `buildSrc` hoặc `settings.gradle` đôi khi không hoạt động như mong đợi và cách tổ chức script chuyên nghiệp.

---

## 🏗️ 1. Tại sao `buildSrc` không "cứu" được `settings.gradle`?

Nhiều người lầm tưởng `buildSrc` là "kho tổng", nhưng thực tế Gradle vận hành theo thứ tự nghiêm ngặt:

1.  **Khởi chạy**: Gradle đọc `settings.gradle`. (Lúc này bạn gọi SnakeYAML, nhưng `buildSrc` **chưa** được biên dịch).
2.  **Biên dịch buildSrc**: Sau khi đọc xong `settings`, Gradle mới biên dịch code trong `buildSrc`.
3.  **Cấu hình**: Sau khi `buildSrc` xong, các class mới khả dụng cho các file `build.gradle`.

> **Kết luận**: Tại thời điểm `settings.gradle` chạy, `buildSrc` vẫn chỉ là code thô, chưa thể cung cấp thư viện.

---

## 🧬 2. Sự khác biệt giữa `settings.gradle` và `build.gradle`

| Đặc điểm | `settings.gradle` (Initialization) | `build.gradle` (Configuration) |
| :--- | :--- | :--- |
| **Thứ tự** | Chạy đầu tiên và duy nhất 1 lần. | Chạy sau, có thể có nhiều file. |
| **Đối tượng** | Điều khiển `Settings`. | Điều khiển `Project`. |
| **Nhiệm vụ** | Khai báo cấu trúc project (module nào tham gia). | Khai báo logic build (plugin, dependency, task). |
| **Tầm nhìn** | "Mù" với những gì trong `build.gradle`. | Thấy được những gì `settings` định nghĩa. |

---

## 🔐 3. Quy tắc "Thừa kế" ClassLoader

Gradle quản lý thư viện theo quan hệ Cha - Con:
* **ClassLoader Cha**: Chứa thư viện khai báo trong `buildscript` của `settings.gradle`.
* **ClassLoader Con**: Chứa thư viện trong `buildSrc` và `buildscript` của `build.gradle`.

**⚠️ Quy tắc vàng:** Con có thể thấy đồ của Cha, nhưng **Cha không thể thấy đồ của Con**. Đó là lý do khai báo thư viện ở `buildSrc` thì `settings.gradle` sẽ bị lỗi "Unable to resolve class".

---

## 🛠️ 4. Giải mã `apply from` vs "Copy-Paste"

Lệnh `apply from: "script.gradle"` **không** phải là copy-paste nội dung.
* Gradle tạo ra một **Script Object mới**.
* Biên dịch nó trong một **ClassLoader riêng biệt**.
* **Hệ quả**: Thư viện ở file cha không "chảy" xuống file con. Nếu file con cần dùng `SnakeYAML`, nó phải tự khai báo `buildscript` của chính nó.

---

## 🚀 5. Giải pháp triệt để (The Clean Way)

Nếu bạn có nhiều file con cùng cần dùng một thư viện xử lý YAML, hãy dùng `buildSrc` theo cách sau:

1.  **Trong `buildSrc/src/main/groovy/MyYamlParser.groovy`**: Viết class bao bọc.
    ```groovy
    import org.yaml.snakeyaml.Yaml
    class MyYamlParser {
        static Map parse(File file) {
            return new Yaml().load(file.newInputStream())
        }
    }
    ```
2.  **Trong `build.gradle`**: Di chuyển logic từ các file script rời rạc vào các class trong `buildSrc` để tận dụng tính năng tự động biên dịch và nạp class của Gradle.

---

## 📋 6. Bảng quyết định nhanh: Đặt logic ở đâu?

| Hành động | `settings.gradle` | `build.gradle` |
| :--- | :---: | :---: |
| Đọc YAML để quyết định `include` module | **X** | |
| Generate file `application.yml` cho app | | **X** |
| Đọc `.env` để set thông số JVM cho Build | **X** | |
| Tạo thư mục module `src/main/java` | **X** | |
| Copy file config vào thư mục `build/resources` | | **X** |

---

# 🧩 Chuyên sâu về Gradle: ClassLoader Isolation

Đây là một trong những điểm gây bối rối nhất khi làm việc với Gradle: **Tính cô lập của ClassLoader (ClassLoader Isolation)**.

---

## 1. Tại sao không được kế thừa?

Mặc dù `settings.gradle` gọi `apply from`, nhưng Gradle đối xử với mỗi file `.gradle` rời rạc như một **đơn vị biên dịch độc lập**.



* Khi Gradle thấy lệnh `import org.yaml.snakeyaml.Yaml` ở dòng 1 của `properties.gradle`, nó sẽ cố gắng biên dịch file đó ngay lập tức.
* Tại thời điểm đó, nó chỉ nhìn vào "túi hành lý" (**classpath**) của riêng file `properties.gradle`.
* Nó không hề biết rằng file "cha" (`settings.gradle`) đã tải thư viện đó về rồi.

---

## 2. Sự khác biệt giữa `apply from` và "Copy-Paste"

Nhiều người lầm tưởng `apply from` giống như lệnh `include` trong C++ hay PHP (copy nội dung file con dán vào file cha). Thực tế cơ chế vận hành hoàn toàn khác:

> **Cơ chế `apply from`**:
> 1. Gradle tạo ra một **Script Object** mới.
> 2. Biên dịch nó trong một môi trường (**ClassLoader**) riêng biệt.
> 3. Sau đó mới thực thi nội dung.

**Hệ quả**: Thư viện ở file cha không thể "chảy" xuống file con thông qua lệnh `apply from` này được.

---

## 💡 Giải pháp khuyến nghị

Để tránh việc lặp lại khai báo `buildscript` ở nhiều nơi, hãy cân nhắc:

* **Sử dụng `buildSrc`**: Đưa logic xử lý vào các class Groovy/Java chính thống.
* **Plugin hóa**: Viết một Plugin riêng để quản lý các thư viện dùng chung cho toàn bộ script build.