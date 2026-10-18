# Java Instrumentation

Module này xây dựng mô hình đầy đủ về **Java Agent và cơ chế Instrumentation của JVM**: agent được đóng gói và khởi động như thế nào, JVM cung cấp Instrumentation ra sao, ClassFileTransformer tham gia vào quá trình nạp/thay đổi class ở đâu, và vì sao redefine/retransform luôn chịu các giới hạn cấu trúc của JVM.

Điểm xuyên suốt của module là phân biệt **cơ chế instrumentation** với các domain lân cận. Instrumentation sở hữu việc chèn/biến đổi bytecode và vòng đời của Java Agent; JVM sở hữu mô hình thực thi class file/bytecode; ClassLoader sở hữu ngữ nghĩa nạp class; Runtime Diagnostics sở hữu việc diễn giải bằng chứng runtime và quy trình khắc phục sự cố. Profiler, công cụ đo độ bao phủ mã, hệ thống giám sát hoặc APM agent có thể dùng Instrumentation để chèn điểm đo và thu thập dữ liệu, nhưng điều đó không biến toàn bộ observability/diagnostics thành trách nhiệm của module này.

## Vì sao nên học module này?

Reflection hoặc proxy chỉ giải quyết được một số kiểu thích nghi ở runtime. Khi cần can thiệp vào class lúc được nạp, bổ sung điểm đo mà không sửa mã nguồn ứng dụng, thay đổi bytecode của class đã được nạp, hoặc xây agent hoạt động trên nhiều application class, learner cần một mô hình tư duy thấp hơn và gần JVM hơn.

Instrumentation cũng là nền tảng để hiểu nhiều công cụ production quen thuộc như profiler, coverage agent, monitoring/APM agent và một số runtime tooling. Tuy nhiên module này tập trung vào **cơ chế và ranh giới thiết kế**, không dạy một sản phẩm vendor cụ thể.

## Prerequisite

Learner nên nắm trước:

- Java Core: class, method, exception, JAR/manifest và vòng đời tài nguyên cơ bản;
- JVM: class file, bytecode và quá trình thực thi runtime ở mức nền tảng;
- ClassLoader: biết class được nạp bởi loader/ngữ cảnh cụ thể trước khi instrumentation tác động;
- Java Module System ở mức cơ bản: hiểu module readability, exports và opens trước khi học phần redefineModule;
- concurrency fundamentals đủ để hiểu việc nạp class và lời gọi transformer có thể xảy ra trên nhiều thread.

Không cần học sâu ASM, Byte Buddy hoặc JVMTI trước khi bắt đầu. Chúng chỉ xuất hiện khi cần đặt Java Instrumentation vào đúng ranh giới triển khai.

## Learning flow

Học theo thứ tự:

1. hiểu Instrumentation là gì, vì sao tồn tại và ranh giới của nó;
2. xây mô hình Java Agent và thỏa thuận giữa agent class với JVM;
3. hiểu agent JAR, manifest, điểm vào, tham số và khai báo khả năng;
4. theo dõi ba đường khởi động chính: -javaagent/premain, executable JAR với Launcher-Agent-Class, và nạp động qua agentmain/Attach API;
5. hiểu Instrumentation API ở mức capability, khảo sát class đã nạp và khả năng sửa đổi theo từng class;
6. học quy ước của ClassFileTransformer;
7. theo dõi chuỗi biến đổi, thứ tự transformer, chaining và hành vi retransformation;
8. phân biệt redefine với retransform và tác động của chúng lên code đang chạy;
9. hiểu giới hạn cấu trúc, verifier/liên kết và các class không thể sửa đổi;
10. đặt kỹ nghệ bytecode vào đúng phạm vi của Instrumentation;
11. dùng ASM/Byte Buddy như công cụ hỗ trợ thay vì học chúng như curriculum độc lập;
12. nối cơ chế với profiling, coverage, monitoring, tracing và tooling dựa trên agent;
13. đánh giá an toàn vận hành, chính sách dynamic attach, overhead, rollback và khả năng tương thích;
14. tổng hợp thành mô hình quyết định: khi nào dùng Instrumentation, khi nào dùng Reflection/Proxy, Runtime Diagnostics hoặc chuyển xuống JVMTI/native agent.

## Ranh giới module

Instrumentation sở hữu mô hình Java Agent, vòng đời startup/attach, java.lang.instrument.Instrumentation, ClassFileTransformer, chuỗi biến đổi, redefine/retransform và các đánh đổi vận hành trực tiếp của agent.

Các phần sau được chuyển sang module/domain sở hữu:

- định dạng class file, ngữ nghĩa bytecode và JVM execution internals → JVM;
- class-loading lifecycle/delegation chuyên sâu → Java Core ClassLoader;
- JPMS/module readability, exports, opens và module-system semantics chuyên sâu → Java Version / Java 9 Module System;
- diễn giải bằng chứng runtime, dump/JFR/tooling và quy trình troubleshooting → Runtime Diagnostics;
- curriculum độc lập về ASM/Byte Buddy → không thuộc Instrumentation; module chỉ dùng chúng ở mức công cụ hỗ trợ triển khai;
- Class-File API (preview ở Java 22/23, được chuẩn hóa trong Java 24) → Java Version / Java 24 Class-File API;
- JVMTI và native-agent internals → ranh giới native/JVM tooling, chỉ được tham chiếu khi Java Instrumentation API không còn đủ.

Mục tiêu cuối cùng là learner có thể giải thích xuyên suốt một Java Agent: từ đóng gói → khởi động/attach → nhận Instrumentation → đăng ký transformer → transform/retransform/redefine class → chịu các ràng buộc cấu trúc/vận hành → quyết định khi nào cơ chế này thực sự phù hợp.
