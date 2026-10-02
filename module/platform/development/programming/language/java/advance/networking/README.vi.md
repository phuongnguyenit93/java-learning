# 📂 README MODULE STRUCTURE (VI)

* **1.NetworkingModel**
    * [NetworkingModel](readme/vi/menu/1.NetworkingModel/NetworkingModel.md)
* **2.AddressingEndpoints**
    * [AddressingEndpoints](readme/vi/menu/2.AddressingEndpoints/AddressingEndpoints.md)
* **3.TcpSockets**
    * [TcpSockets](readme/vi/menu/3.TcpSockets/TcpSockets.md)
* **4.UdpDatagrams**
    * [UdpDatagrams](readme/vi/menu/4.UdpDatagrams/UdpDatagrams.md)
* **5.FailureControl**
    * [FailureControl](readme/vi/menu/5.FailureControl/FailureControl.md)
* **6.NioNetworking**
    * [NioNetworking](readme/vi/menu/6.NioNetworking/NioNetworking.md)
* **7.HttpClient**
    * [HttpClient](readme/vi/menu/7.HttpClient/HttpClient.md)
* **8.WebSocketClient**
    * [WebSocketClient](readme/vi/menu/8.WebSocketClient/WebSocketClient.md)
* **9.NetworkingChoices**
    * [NetworkingChoices](readme/vi/menu/9.NetworkingChoices/NetworkingChoices.md)

# Java Networking nâng cao

Module này xây dựng mental model thực tế cho các API mạng của JDK: từ địa chỉ và phân giải tên, TCP/UDP socket, các mô hình I/O của NIO, đến java.net.http.HttpClient và WebSocket client.

Mục tiêu không phải học thuộc danh sách class trong java.net, mà là hiểu **một chương trình Java giao tiếp qua mạng như thế nào, đang chờ ở đâu, dữ liệu được biểu diễn theo mô hình nào, tài nguyên thuộc về ai và nên chọn abstraction nào cho từng nhu cầu**.

## Prerequisite

Learner nên nắm Java Core I/O, đặc biệt byte stream, Buffer, Channel và resource lifecycle; đồng thời có nền tảng concurrency đủ để phân biệt thread, executor, blocking và asynchronous completion. Phần virtual threads chỉ được dùng để đánh giá trade-off của network I/O trên Java 21, còn concurrency model đầy đủ thuộc module Concurrency.

## Learning flow

Flow của module đi từ nền tảng tới quyết định thực tế:

1. xây dựng mô hình endpoint, transport và kiểu hoàn tất I/O;
2. hiểu địa chỉ IP, name resolution, network interface, URI/URL và socket address;
3. học TCP dưới dạng byte stream và UDP dưới dạng datagram;
4. xử lý timeout, socket option, failure, cancellation và vòng đời tài nguyên;
5. chuyển sang NIO với channel, selector và asynchronous channel, đồng thời đặt virtual threads vào đúng trade-off;
6. sử dụng HTTP Client và WebSocket client mức cao của JDK;
7. tổng hợp cách chọn abstraction và handoff đúng sang Security, Concurrency và Integration.

## Boundary

Networking sở hữu **JDK networking APIs và runtime mechanics**. Module không dạy lại Java I/O nền tảng, Java Memory Model, TLS/JSSE và cryptography chuyên sâu, retry/resilience architecture, API gateway/service mesh, hay thiết kế giao thức HTTP/WebSocket ở tầng hệ thống.

Khi learner hoàn thành module, họ nên có thể giải thích end-to-end từ hostname → resolved endpoint → transport/API model → data transfer/completion → failure/cancellation → resource release, và lựa chọn được API phù hợp mà không mặc định “non-blocking luôn tốt hơn”.
