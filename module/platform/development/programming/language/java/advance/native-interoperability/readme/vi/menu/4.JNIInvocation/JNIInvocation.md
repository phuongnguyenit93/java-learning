<a id="back-to-top"></a>

# JNI Invocation API

## Menu
- [Invocation API dùng để làm gì?](#jni-invocation-purpose)
- [VM initialization options và default arguments](#jni-vm-init-args)
- [Native application tạo và host JVM](#jni-create-jvm)
- [Khám phá JVM đã được tạo trong process hiện tại](#jni-get-created-vms)
- [GetEnv, attach/detach và daemon native thread](#jni-attach-detach-thread)
- [DestroyJavaVM và VM termination](#jni-destroy-jvm)
- [JNI_OnLoad, JNI_OnUnload và library/version management](#jni-library-lifecycle)
- [Lifecycle và responsibility khi nhúng JVM](#jni-invocation-lifecycle)

## <a id="jni-invocation-purpose">Invocation API dùng để làm gì?</a>

<details>
<summary>Click for details</summary>

JNI Invocation API giải quyết chiều ngược với native method thông thường: thay vì JVM khởi chạy trước rồi Java gọi native code, một **ứng dụng native** có thể tạo/host JVM và sau đó dùng JNI để gọi Java.

API này xoay quanh JavaVM, là interface ở cấp VM. Native host có thể:

- tạo JVM và nhận JNIEnv cho thread khởi tạo;
- kiểm tra JVM đã được tạo trong process hiện tại;
- attach/detach các native thread;
- lấy JNIEnv của thread hiện tại bằng GetEnv;
- yêu cầu JVM shutdown bằng DestroyJavaVM.

Đây vẫn là native interoperability, không phải process administration. Mục tiêu là quản lý vòng đời của JVM **bên trong cùng native process** và giữ boundary giữa thread/tài nguyên rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-vm-init-args">VM initialization options và default arguments</a>

<details>
<summary>Click for details</summary>

Trước khi JNI_CreateJavaVM, native host mô tả cấu hình bằng JavaVMInitArgs. Cấu trúc này chứa version JNI mong muốn, danh sách option và cách xử lý option không được nhận diện.

~~~c
JavaVMOption options[1];
options[0].optionString = "-Dapp.mode=embedded";

JavaVMInitArgs args;
args.version = JNI_VERSION_21;
args.nOptions = 1;
args.options = options;
args.ignoreUnrecognized = JNI_FALSE;
~~~

JNI_GetDefaultJavaVMInitArgs có thể được dùng để kiểm tra/lấy cấu hình mặc định tương ứng với version được yêu cầu. Với JDK hiện đại, host thường xây JavaVMInitArgs trực tiếp nhưng vẫn phải đặt version hợp lệ.

VM option là một phần contract triển khai. Các option module như --add-opens hoặc --module-path khi truyền qua Invocation API cần dùng form option=value theo JNI Specification. Không nên copy command line option một cách máy móc mà bỏ qua syntax của JavaVMOption.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-create-jvm">Native application tạo và host JVM</a>

<details>
<summary>Click for details</summary>

JNI_CreateJavaVM load và initialize JVM trong native process:

~~~c
JavaVM *vm = NULL;
JNIEnv *env = NULL;

jint rc = JNI_CreateJavaVM(&vm, (void **) &env, &args);
if (rc != JNI_OK) {
    /* xử lý lỗi khởi động */
}
~~~

Khi thành công, luồng hiện tại được attach vào JVM và trở thành main thread của JVM; env là JNIEnv dành cho thread đó.

JNI Specification không hỗ trợ tạo nhiều JVM trong cùng một process. Vì vậy giả định kiến trúc nên là **một native process host một JVM**, thay vì xem JNI_CreateJavaVM như factory có thể tạo nhiều VM độc lập.

Host cũng phải quyết định rõ ai sở hữu vòng đời: component nào start VM, component nào giữ JavaVM pointer và ai chịu trách nhiệm shutdown.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-get-created-vms">Khám phá JVM đã được tạo trong process hiện tại</a>

<details>
<summary>Click for details</summary>

JNI_GetCreatedJavaVMs chỉ nhìn thấy JVM đã được tạo **trong process hiện tại**. Nó không phải API để scan JVM của process khác hay thay thế tool diagnostics.

~~~c
JavaVM *vms[1];
jsize count = 0;

jint rc = JNI_GetCreatedJavaVMs(vms, 1, &count);
if (rc == JNI_OK && count > 0) {
    JavaVM *vm = vms[0];
}
~~~

Hàm ghi tối đa bufLen JavaVM pointer vào buffer và trả tổng số VM đã tạo qua nVMs. Do việc tạo nhiều JVM trong một process không được hỗ trợ, ứng dụng thông thường chỉ cần lập luận quanh tối đa một JVM đang hoạt động.

Use case điển hình là native component được load vào một process đã host JVM và cần lấy JavaVM pointer thay vì tự tạo VM mới.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-attach-detach-thread">GetEnv, attach/detach và daemon native thread</a>

<details>
<summary>Click for details</summary>

JavaVM có ba nhóm thao tác cần phân biệt: **kiểm tra trạng thái**, **attach**, và **detach**.

- GetEnv trả JNIEnv nếu luồng hiện tại đã attach; nếu chưa, kết quả là JNI_EDETACHED.
- AttachCurrentThread attach current native thread dưới dạng non-daemon và trả JNIEnv.
- AttachCurrentThreadAsDaemon attach với daemon status.
- DetachCurrentThread tách thread khỏi JVM trước khi native thread kết thúc; thread không thể detach khi Java methods vẫn còn trên call stack của chính nó.

~~~c
JNIEnv *env = NULL;
jint rc = (*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_21);

if (rc == JNI_EDETACHED) {
    rc = (*vm)->AttachCurrentThread(vm, (void **) &env, NULL);
}
~~~

Daemon attachment quan trọng ở shutdown semantics: daemon thread không ngăn JVM termination giống non-daemon attached thread. Tuy nhiên daemon không có nghĩa là “không cần giải phóng”; tài nguyên native vẫn phải được release đúng vòng đời.

Luôn lấy JNIEnv theo luồng hiện tại. Không chuyển một JNIEnv pointer đã lấy ở thread A sang thread B.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-destroy-jvm">DestroyJavaVM và VM termination</a>

<details>
<summary>Click for details</summary>

DestroyJavaVM bắt đầu vòng đời shutdown của JVM và thực hiện best-effort giải phóng tài nguyên của VM. Nó không đơn giản là “free một object JavaVM”.

Theo JNI Specification, function chờ các **non-daemon thread** khác kết thúc trước khi JVM thực sự shutdown. Nhóm này bao gồm cả Java thread và native thread đã attach dưới dạng non-daemon.

Điều đó có hai hệ quả thực tế:

1. quên detach hoặc để non-daemon work chạy vô hạn có thể làm shutdown bị giữ lại;
2. tài nguyên native phải được giải phóng trước khi thread kết thúc, vì JVM không thể tự giải phóng mọi tài nguyên thuộc native code.

Native thread chưa attach không thuộc vòng đời Java thread theo cùng cách. Sau khi JVM termination bắt đầu, code native còn chạy không được giả định rằng nó có thể quay lại gọi Java an toàn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-library-lifecycle">JNI_OnLoad, JNI_OnUnload và library/version management</a>

<details>
<summary>Click for details</summary>

JNI library có thể định nghĩa hook vòng đời để khai báo version JNI cần dùng và thực hiện khởi tạo/giải phóng.

**JNI_OnLoad** được JVM gọi khi dynamically linked JNI library được load. Function trả về JNI version tối thiểu mà library yêu cầu:

~~~c
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    return JNI_VERSION_21;
}
~~~

Nếu VM không nhận diện version được trả về, library bị xem như load thất bại.

**JNI_OnUnload** có thể được gọi khi class loader chứa native library bị garbage collected. Đây là nơi phù hợp để giải phóng tài nguyên thuộc library, nhưng JNI Specification yêu cầu thận trọng vì hook có thể chạy trong context không xác định; không nên thực hiện callback Java tùy ý tại đây.

Library/version management cũng gắn với class loader: mỗi class loader quản lý tập native library của nó, và cùng một JNI library không thể được load vào nhiều class loader theo cách tùy ý. Vì vậy hook vòng đời không chỉ là startup/shutdown callback mà là một phần của loading contract.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-invocation-lifecycle">Lifecycle và responsibility khi nhúng JVM</a>

<details>
<summary>Click for details</summary>

Khi nhúng JVM, nên xem JVM như một subsystem có vòng đời rõ ràng:

~~~text
chuẩn bị VM options
    ↓
JNI_CreateJavaVM
    ↓
lưu JavaVM + dùng JNIEnv của main thread
    ↓
native worker cần Java → GetEnv / attach
    ↓
thực hiện JNI work
    ↓
detach worker + giải phóng tài nguyên native
    ↓
DestroyJavaVM khi non-daemon work đã kết thúc
~~~

Các lỗi hay gặp là tạo thread nhưng quên detach, cache JNIEnv toàn cục, shutdown khi callback/native work vẫn còn sống hoặc để quyền sở hữu JavaVM pointer không rõ ràng.

Một thiết kế tốt nên có một thành phần host duy nhất sở hữu vòng đời JVM và cung cấp API nhỏ cho các native component khác. Điều này tránh việc mỗi library tự quyết định create/destroy VM độc lập.

Sau khi hoàn chỉnh JNI ở cả hai chiều, chương tiếp theo chuyển sang FFM như một model Java-facing khác cho foreign memory và C-style function contract, chứ không phải cơ chế thay thế cho JVM embedding.

</details>

- [Quay lại đầu trang](#back-to-top)
