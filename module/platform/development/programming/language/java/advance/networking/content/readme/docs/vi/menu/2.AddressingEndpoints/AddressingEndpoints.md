<a id="back-to-top"></a>

# Địa chỉ, phân giải tên và endpoint

## Menu
- [InetAddress và mô hình địa chỉ IP](#inet-address-model)
- [Phân giải tên, lỗi và cache](#name-resolution)
- [InetAddress Resolver SPI và custom resolver](#name-resolution-spi)
- [NetworkInterface và địa chỉ cục bộ](#network-interface-model)
- [SocketAddress và InetSocketAddress](#socket-address-model)
- [URI, URL và định danh tài nguyên](#uri-url-resource-identity)
- [URLConnection và ranh giới truy cập tài nguyên](#urlconnection-boundary)
- [UnixDomainSocketAddress và endpoint không dùng IP](#unix-domain-addresses)

## <a id="inet-address-model">InetAddress và mô hình địa chỉ IP</a>

<details>
<summary>Click for details</summary>

InetAddress là abstraction của JDK cho **địa chỉ IP**. Một đối tượng InetAddress biểu diễn địa chỉ IPv4 32-bit hoặc IPv6 128-bit và có thể kèm hostname tương ứng. Trong phần lớn mã nguồn, nên làm việc qua InetAddress thay vì rẽ nhánh trực tiếp theo Inet4Address hay Inet6Address.

Ví dụ:

~~~java
InetAddress address = InetAddress.getByName("example.com");

System.out.println(address.getHostAddress());
System.out.println(address.getHostName());
~~~

Hostname và IP không phải quan hệ một-một. Một hostname có thể được phân giải thành nhiều địa chỉ, vì vậy getAllByName(...) đôi khi phù hợp hơn getByName(...):

~~~java
for (InetAddress address : InetAddress.getAllByName("example.com")) {
    System.out.println(address.getHostAddress());
}
~~~

InetAddress không chứa port. Khi cần địa chỉ transport cụ thể, Java ghép host/IP với port qua InetSocketAddress.

Không nên dùng isReachable() như một health-check tuyệt đối. Kết quả phụ thuộc cấu hình mạng, firewall và cách triển khai. Trạng thái của giao thức ứng dụng nên được kiểm tra bằng tín hiệu ở tầng ứng dụng/protocol phù hợp hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="name-resolution">Phân giải tên, lỗi và cache</a>

<details>
<summary>Click for details</summary>

Khi mã nguồn dùng hostname, hệ thống phải biến tên đó thành một hoặc nhiều địa chỉ IP. InetAddress.getByName(...) có thể thực hiện name resolution và có thể thất bại với UnknownHostException.

Name resolution là I/O phụ thuộc hệ thống, không phải phép biến đổi chuỗi thuần túy:

~~~text
hostname
   ↓
resolver được JVM/platform sử dụng
   ↓
one or more IP addresses
~~~

JDK có cơ chế cache kết quả lookup thành công và thất bại theo các cấu hình networking/security liên quan. Vì vậy thay đổi DNS bên ngoài không đảm bảo một JVM đang chạy sẽ thấy kết quả mới ngay lập tức.

Một điểm phân biệt quan trọng: core InetAddress lookup API không có tham số timeout cho từng lần getByName(). Đừng lấy socket connect timeout rồi giả định nó cũng giới hạn DNS lookup; resolution và connect là hai giai đoạn khác nhau.

Phân loại lỗi theo giai đoạn:

- phân giải thất bại → thường là UnknownHostException;
- phân giải thành công nhưng connect thất bại → ConnectException, timeout hoặc lỗi transport khác;
- phân giải ra nhiều IP → endpoint thực tế phụ thuộc địa chỉ nào được thử.

Khi troubleshoot, log hostname **và** địa chỉ đã phân giải giúp biết lỗi nằm trước hay sau bước resolution.

Resolution cũng có thể xảy ra theo chiều ngược lại. Nếu một `InetAddress` chỉ được tạo từ địa chỉ IP, `getHostName()` có thể thực hiện reverse name lookup; `getCanonicalHostName()` là best-effort lookup để tìm fully qualified host name. Điểm này quan trọng trong luồng logging/diagnostic vì một method trông giống thao tác định dạng đơn giản vẫn có thể đi qua resolver boundary. Khi mã nguồn chỉ cần giữ nguyên chuỗi host của `InetSocketAddress`, `getHostString()` tránh kích hoạt reverse lookup.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="name-resolution-spi">InetAddress Resolver SPI và custom resolver</a>

<details>
<summary>Click for details</summary>

Java hiện đại cho phép thay resolver hệ thống bằng **InetAddress Resolver SPI** trong package java.net.spi. Đây là điểm mở rộng dành cho hạ tầng hoặc mã thư viện cần kiểm soát cách lookup hostname/IP; ứng dụng thông thường hiếm khi phải tự triển khai.

Hai abstraction chính:

- InetAddressResolver: thực hiện host → address và reverse lookup;
- InetAddressResolverProvider: service provider tạo resolver dùng ở mức JVM.

Mental model:

~~~text
ứng dụng
→ InetAddress API
→ resolver provider được nạp qua service mechanism
→ resolver
→ hệ thống phân giải tên
~~~

Provider tham gia cơ chế service provider của Java. Điều đó cho phép custom resolver hoạt động phía sau API InetAddress mà không buộc ứng dụng thay mọi lời gọi lookup.

JVM duy trì một resolver system-wide cho `InetAddress`. Sau khi VM khởi tạo xong, lần lookup đầu tiên khiến `InetAddress` tìm provider bằng `ServiceLoader` qua system class loader; provider đầu tiên được tìm thấy sẽ cung cấp resolver đó. Nếu không có provider, JDK dùng built-in resolver. Vì vậy custom resolver là hạ tầng ở phạm vi JVM, không phải resolver theo từng request mà mỗi bên gọi tự chọn độc lập.

Thay resolver chỉ đổi **cách phân giải**, không đổi ngữ nghĩa ở tầng ứng dụng: lookup vẫn có thể thất bại, trả nhiều địa chỉ và vẫn là giai đoạn riêng trước connect.

Repo có module Java Version riêng cho lịch sử tính năng này ở Java 18. Networking chỉ sở hữu mô hình tư duy và cách dùng runtime hiện tại; “tính năng được đưa vào release nào” thuộc Java Version.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="network-interface-model">NetworkInterface và địa chỉ cục bộ</a>

<details>
<summary>Click for details</summary>

NetworkInterface mô tả một network interface mà JVM nhìn thấy trên máy: Ethernet, Wi-Fi, loopback, virtual interface hoặc interface khác do hệ điều hành cung cấp.

Nó hữu ích khi ứng dụng cần:

- liệt kê địa chỉ local;
- chọn interface cho multicast;
- kiểm tra loopback/up/down;
- tránh giả định rằng một máy chỉ có đúng một IP.

Ví dụ:

~~~java
for (NetworkInterface nic :
        Collections.list(NetworkInterface.getNetworkInterfaces())) {

    System.out.println(nic.getName());

    for (InetAddress address : Collections.list(nic.getInetAddresses())) {
        System.out.println("  " + address.getHostAddress());
    }
}
~~~

Máy phát triển, container host và cloud server thường có nhiều interface. Vì vậy lấy “địa chỉ đầu tiên” rồi coi đó là định danh máy ổn định là một giả định yếu.

NetworkInterface thuộc Networking vì nó ảnh hưởng trực tiếp tới bind, multicast và việc chọn địa chỉ local. Nó không phải abstraction để mô hình hóa topology, route table hay service discovery ở cấp hạ tầng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="socket-address-model">SocketAddress và InetSocketAddress</a>

<details>
<summary>Click for details</summary>

SocketAddress là abstraction cho **địa chỉ mà socket/channel có thể bind hoặc connect**. Với Internet socket, cách triển khai thường dùng InetSocketAddress.

~~~java
InetSocketAddress remote =
        new InetSocketAddress("example.com", 443);
~~~

InetSocketAddress có hai trạng thái đáng chú ý:

- **resolved**: hostname đã gắn với một InetAddress;
- **unresolved**: giữ hostname + port nhưng chưa resolve.

Có thể tạo unresolved address bằng InetSocketAddress.createUnresolved(...). Điều này hữu ích khi một tầng khác, ví dụ proxy logic, cần giữ nguyên hostname.

Bind local và connect tới remote là hai vai trò khác nhau của cùng abstraction:

~~~java
server.bind(new InetSocketAddress("0.0.0.0", 8080));
client.connect(new InetSocketAddress("example.com", 443));
~~~

Bind wildcard address nghĩa là lắng nghe trên các interface local phù hợp; không có nghĩa remote peer là 0.0.0.0.

Giữ rõ sự khác nhau giữa local endpoint và remote endpoint sẽ giúp chương TCP và NIO dễ hiểu hơn: socket/channel có local endpoint và, sau connect/accept, có remote endpoint tương ứng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="uri-url-resource-identity">URI, URL và định danh tài nguyên</a>

<details>
<summary>Click for details</summary>

URI và URL nằm gần networking APIs nhưng bài toán chính của chúng là **định danh tài nguyên**, không phải socket addressing.

Một URI có thể có dạng:

~~~text
scheme://authority/path?query#fragment
~~~

Ví dụ:

~~~java
URI uri =
        URI.create("https://api.example.com/orders/42?detail=true");
~~~

Trong URI này, authority có thể dẫn tới network endpoint, còn path/query biểu diễn định danh và ngữ nghĩa ở tầng protocol/ứng dụng.

URL gắn với việc truy cập tài nguyên thông qua protocol handler và có thể mở URLConnection. URI phù hợp hơn cho việc biểu diễn, kiểm tra và thao tác identifier. Các API hiện đại như HttpRequest nhận URI làm request target.

Quy tắc thực tế:

- cần biểu diễn identifier → ưu tiên URI;
- cần socket endpoint → SocketAddress / InetSocketAddress;
- cần HTTP client hiện đại → HttpClient + HttpRequest với URI.

Đừng parse URL bằng cách cắt chuỗi thủ công; abstraction chuẩn giúp xử lý encoding, authority, IPv6 literal và ngữ nghĩa của relative reference đúng hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="urlconnection-boundary">URLConnection và ranh giới truy cập tài nguyên</a>

<details>
<summary>Click for details</summary>

URLConnection là abstraction tổng quát lâu đời để truy cập tài nguyên thông qua protocol handler của URL. HttpURLConnection mở rộng mô hình đó với hành vi riêng cho HTTP.

Ví dụ:

~~~java
URLConnection connection =
        URI.create("https://example.com")
           .toURL()
           .openConnection();

connection.setConnectTimeout(3_000);
connection.setReadTimeout(3_000);
~~~

Hai timeout này thuộc **thao tác URLConnection**, không phải một DNS timeout toàn cục. Với HttpURLConnection, một đối tượng đại diện cho một request, trong khi kết nối transport phía dưới có thể được cách triển khai tái sử dụng.

Trong Java hiện đại, mã HTTP mới thường dễ mô hình hóa hơn với java.net.http.HttpClient vì API đó tách rõ cấu hình client có thể tái sử dụng, request, xử lý response body, hoàn tất đồng bộ/bất đồng bộ và tích hợp WebSocket.

Phần này giữ URLConnection vì nó vẫn là một phần của mô hình networking trong JDK và còn xuất hiện trong mã cũ/library APIs. Cơ chế HTTP hiện đại được học ở chương HttpClient.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unix-domain-addresses">UnixDomainSocketAddress và endpoint không dùng IP</a>

<details>
<summary>Click for details</summary>

Không phải mọi socket endpoint đều đi qua IP network. Java hỗ trợ **Unix-domain sockets**, nơi endpoint được định danh bằng pathname trên hệ điều hành có hỗ trợ.

~~~java
UnixDomainSocketAddress address =
        UnixDomainSocketAddress.of("/tmp/my-service.sock");
~~~

UnixDomainSocketAddress vẫn là một SocketAddress, vì vậy mô hình tư duy bind/connect giữ nguyên dù protocol family không còn là Internet socket.

Unix-domain socket thường hữu ích cho giao tiếp giữa các tiến trình trên cùng máy:

- không cần cấp TCP port;
- endpoint nằm trong filesystem-like namespace;
- phù hợp cho daemon hoặc local service.

Trong JDK, hỗ trợ chủ yếu nằm ở SocketChannel và ServerSocketChannel khi mở với protocol family tương ứng. Khả năng hỗ trợ phụ thuộc platform/OS, vì vậy mã portable phải chấp nhận trường hợp không được hỗ trợ.

Repo có module Java Version cho lịch sử Unix-domain socket channels ở Java 16. Networking sở hữu cách dùng và mô hình tư duy hiện tại; Java Version sở hữu góc nhìn lịch sử release.

Sau khi đã phân biệt tên, địa chỉ và endpoint, chương tiếp theo chuyển sang TCP để theo dõi đầy đủ vòng đời của một kết nối stream-oriented từ bind/connect/accept đến read/write và close.

</details>

- [Quay lại đầu trang](#back-to-top)
