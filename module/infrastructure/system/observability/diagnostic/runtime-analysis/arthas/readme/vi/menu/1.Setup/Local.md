# Cách cài đặt Arthas

## <a id="arthas-local">Hướng dẫn: Cài đặt và Chẩn đoán Java App với Arthas (Local) </a>

Arthas là công cụ mạnh mẽ của Alibaba giúp bạn soi thấu "nội tạng" của ứng dụng Java ngay khi nó đang chạy.

## 1. Tải Arthas (Chỉ cần 1 file bootstrap)
Arthas cung cấp một file mồi (`arthas-boot.jar`) rất nhẹ. Bạn không cần cài đặt rườm rà.

* **Tải bằng trình duyệt:** Truy cập [arthas-boot.jar](https://arthas.aliyun.com/arthas-boot.jar) và lưu vào máy.
* **Tải bằng dòng lệnh:**
```bash
  curl -O [https://arthas.aliyun.com/arthas-boot.jar](https://arthas.aliyun.com/arthas-boot.jar)
```

## 2. Chuẩn bị Ứng dụng trong IntelliJ
Để Arthas có thể "soi", ứng dụng Spring Boot của bạn phải đang ở trạng thái **Running**.
1. Mở dự án `java-learning` trong IntelliJ.
2. Chạy (Run) các service của bạn (ví dụ: `InventoryApplication`).

## 3. Khởi động Arthas và "Attach" vào App
Mở Terminal và chạy lệnh sau:
```bash
  java -jar arthas-boot.jar
```
**Điều gì sẽ xảy ra?**
* Arthas liệt kê tất cả tiến trình Java đang chạy trên máy bạn.
* Bạn sẽ thấy danh sách dạng:
```plaintext
  [1]: 12345 org.jetbrains.jps.cmdline.Launcher
  [2]: 67890 com.example.inventory.InventoryApplication
```
* **Việc của bạn:** Nhập số thứ tự tương ứng (ví dụ: `2`) rồi nhấn **Enter**.



## 4. Kiểm tra sức khỏe với Dashboard
Sau khi thấy logo Arthas ASCII hiện ra, hãy thử gõ lệnh cơ bản nhất:
```bash
  dashboard
```
Lệnh này hiện ra bảng điều khiển thời gian thực về: **CPU, Memory, Threads, và Runtime**.
* Nhấn `q` hoặc `Ctrl + C` để thoát khỏi Dashboard và quay lại dòng lệnh.

## 5. Lưu ý "Sống còn" cho môi trường Windows
Vì bạn đang làm việc trên Windows, hãy chú ý các điểm sau để tránh lỗi:

* **Quyền Admin:** Đôi khi bạn cần mở Terminal (CMD/PowerShell) bằng quyền **Administrator** để Arthas có đủ quyền can thiệp vào JVM.
* **Xung đột Cổng (Port):** Arthas mặc định dùng port `3658`. Nếu bạn muốn soi 2 service cùng lúc, cái thứ 2 sẽ báo lỗi.
    * **Cách xử lý:** Chỉ định port khác:
```bash
  java -jar arthas-boot.jar --telnet-port 3659 --http-port 8564
```
* **Tắt sạch sẽ:** Khi xong việc, hãy gõ lệnh `stop`. Lệnh này cực kỳ quan trọng vì nó sẽ gỡ bỏ các "móc" (instrumentation) mà Arthas đã gắn vào App, trả lại hiệu năng ban đầu cho máy.

---

### 💡 Mẹo nhỏ cho dự án java-learning:
Nếu bạn thấy một API chạy chậm mà không biết chậm ở đâu, sau khi attach Arthas, hãy thử dùng lệnh `trace`:
```bash
  trace com.example.service.ProductService getProductDetail
```
Arthas sẽ vẽ ra một cái cây thời gian, cho bạn thấy chính xác từng dòng code mất bao nhiêu mili giây.

## <a id="arthas-web-console">Hướng dẫn: Sử dụng Arthas Web Console cho chẩn đoán từ xa</a>

Sau khi bạn đã chọn xong Process ID (PID), Arthas mặc định sẽ mở một "cổng trời" để bạn truy cập thông qua trình duyệt web. Đây là cách cấu hình và thao tác chi tiết.



## 1. Kiểm tra trạng thái mặc định
Khi Arthas khởi động thành công, nó sẽ mở hai cổng giao tiếp chính:
* **Cổng Web (HTTP):** `8563` (Giao diện người dùng).
* **Cổng Telnet:** `3658` (Kết nối điều khiển).

**Địa chỉ truy cập nhanh:** `http://127.0.0.1:8563`

## 2. Cách cấu hình để truy cập từ xa (Remote Access)
Nếu bạn chạy Arthas trên Server/Docker và muốn ngồi máy cá nhân để soi, bạn không thể dùng `127.0.0.1` (vốn chỉ cho phép kết nối nội bộ). Bạn cần yêu cầu Arthas lắng nghe trên tất cả các card mạng.

Chạy lệnh khởi động kèm tham số `--target-ip`:
```bash
  java -jar arthas-boot.jar --target-ip 0.0.0.0
```
* **Lưu ý:** Hãy đảm bảo Firewall của Server đã mở cổng `8563` và `3658`.



## 3. Thao tác trên giao diện Web Console
Khi truy cập vào `http://<IP-SERVER>:8563`, bạn sẽ thấy giao diện Terminal trực quan. Hãy nhập thông số:
* **IP:** Nhập IP của server (mặc định là `127.0.0.1`).
* **Port:** Nhập `3658` (Cổng điều khiển).
* **Connect:** Nhấn nút Connect để bắt đầu phiên làm việc.

Bây giờ, toàn bộ các lệnh như `dashboard`, `thread`, hay `watch` sẽ hiển thị mượt mà ngay trên trình duyệt của bạn.



## 4. Một số mẹo nhỏ cho bạn

* **Đổi cổng Web:** Nếu cổng `8563` bị chiếm bởi ứng dụng khác, hãy đổi bằng tham số:
```bash
  java -jar arthas-boot.jar --http-port 9999
```
* **Tắt Arthas sạch sẽ:** Khi dùng xong, đừng quên gõ lệnh `stop`. Nếu bạn chỉ đóng trình duyệt, Arthas vẫn chạy ngầm và chiếm tài nguyên JVM.
* **Cảnh báo bảo mật:** Web Console mặc định không có mật khẩu. Đừng bao giờ mở cổng này ra Internet công cộng nếu không có lớp bảo mật hoặc SSH Tunnel che chắn.

---

### 💡 Mẹo cho dự án java-learning:
Khi dùng Web Console, bạn có thể mở nhiều tab trình duyệt để theo dõi các thông số khác nhau cùng lúc. Ví dụ:
* **Tab 1:** Để lệnh `dashboard` chạy liên tục để xem CPU.
* **Tab 2:** Dùng lệnh `trace` để soi một API đang nghi vấn.
* **Tab 3:** Dùng `watch` để bắt dữ liệu đầu vào.

## <a id="shutdown-arthas">Hướng dẫn: Tắt Arthas và Dọn dẹp Tài nguyên JVM</a>

Để đảm bảo hiệu năng cho ứng dụng, bạn cần phân biệt rõ giữa việc "ngắt kết nối" và "dừng hoạt động" hoàn toàn của Arthas trong RAM.

[Image showing the difference between exit and stop commands in Arthas]

## 1. Thoát tạm thời: `quit` hoặc `exit`
* **Tác dụng:** Ngắt kết nối giữa Terminal (PowerShell) của bạn và Arthas Server.
* **Trạng thái:** Arthas **vẫn chạy ngầm** (attached) bên trong JVM. Các Agent vẫn bám vào class để sẵn sàng phục vụ. Bạn có thể kết nối lại ngay lập tức mà không cần quá trình Attach lại từ đầu.

## 2. Dừng hoàn toàn: `stop` hoặc `shutdown`
Đây là lệnh **quan trọng nhất** bạn cần dùng khi đã hoàn thành việc chẩn đoán.
* **Hành động:** * Hủy bỏ toàn bộ các thay đổi Bytecode (**Class Enhancement**) mà Arthas đã thực hiện.
  * Giải phóng tài nguyên RAM và đóng cổng Web Console (`8563`).
  * Ngắt kết nối hoàn toàn khỏi JVM.
* **Lưu ý:** Sau khi `stop`, các class được trả về trạng thái nguyên bản, đảm bảo an toàn cho môi trường Production hoặc Staging.

## 3. Cách tắt đối với "Arthas Spring Boot Starter"
Nếu bạn tích hợp Arthas như một Dependency trong dự án, cơ chế sẽ hơi khác:
* **Vô hiệu hóa:** Bạn nên cấu hình trong file `application.yml` (thường là ở profile `prod`) để tránh nó tự kích hoạt:
  ```yaml
  arthas:
    enabled: false
  ```

## 4. Mẹo "dọn dẹp" nếu PowerShell/Terminal bị treo
Nếu bạn lỡ tay tắt cửa sổ Terminal mà chưa gõ `stop`, hoặc tiến trình bị treo, hãy sử dụng lệnh sau trong PowerShell để truy quét:

	```powershell
	# Tìm và tắt các tiến trình liên quan đến Arthas còn sót lại
	Get-Process | Where-Object {$_.ProcessName -like "*arthas*"} | Stop-Process
	```

---

### 💡 Lời khuyên cho Backend Developer:
Luôn ưu tiên dùng lệnh **`stop`** bên trong giao diện Arthas trước khi đóng Terminal. Điều này giúp JVM "sạch sẽ" nhất có thể. Trong môi trường Docker của dự án `java-learning`, việc quên `stop` có thể khiến Container của bạn bị ngốn RAM dần dần theo thời gian (Memory Leak).