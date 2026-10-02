<a id="back-to-top"></a>

# Java Native Interface

## Menu
- [JNI là gì và vì sao tồn tại?](#jni-definition-purpose)
- [Native method declaration và binding](#jni-native-method-binding)
- [JNIEnv và JNI function interface](#jni-environment)
- [Type mapping và data access qua JNI boundary](#jni-types-data-boundary)
- [Local reference, global reference và GC](#jni-references)
- [Java exception và native error boundary](#jni-exception-boundary)
- [Native thread và JVM thread boundary](#jni-thread-boundary)

## <a id="jni-definition-purpose">JNI là gì và vì sao tồn tại?</a>

<details>
<summary>Click for details</summary>

JNI (Java Native Interface) là contract chuẩn để code Java và code native tương tác với nhau. Phía Java có thể khai báo native method; phía native nhận các JNI type và JNIEnv để thao tác với class, object, method, array, string và exception của JVM.

JNI phù hợp khi:

- hệ thống đã có native code hoặc JNI wrapper lâu năm;
- native library cần gọi ngược vào object/method Java;
- cần dùng API JNI mà ecosystem hiện hữu đã chuẩn hóa.

Đánh đổi là JNI đặt nhiều trách nhiệm lên native code: reference lifetime, thread attachment, trạng thái exception và data conversion đều phải được xử lý đúng. Một lỗi C/C++ có thể làm hỏng cả JVM process.

Trong module này JNI được học như **bridge contract**, không phải như một khóa C/C++ hay JVM internals.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-native-method-binding">Native method declaration và binding</a>

<details>
<summary>Click for details</summary>

Một JNI native method bắt đầu từ declaration ở Java:

~~~java
public final class NativeMath {
    static {
        System.loadLibrary("native_math");
    }

    public static native int add(int a, int b);
}
~~~

Native library phải cung cấp phần triển khai tương ứng hoặc đăng ký phần triển khai bằng RegisterNatives. Với naming convention chuẩn, C function có dạng:

~~~c
JNIEXPORT jint JNICALL
Java_NativeMath_add(JNIEnv *env, jclass clazz, jint a, jint b) {
    return a + b;
}
~~~

Điểm cần giữ trong mô hình tư duy là **declaration Java chưa phải phần triển khai**. JVM phải resolve native method tới entry point hợp lệ trong library đã load. Nếu binding không khớp tên/signature hoặc symbol không tồn tại, invocation sẽ thất bại thay vì “tự tìm” một function gần giống.

RegisterNatives là lựa chọn khác khi muốn map method Java với function pointer một cách explicit; nó đặc biệt hữu ích khi naming/export strategy không dựa vào generated symbol name.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-environment">JNIEnv và JNI function interface</a>

<details>
<summary>Click for details</summary>

JNIEnv là interface pointer mà native code dùng để gọi JNI functions. Khi một Java thread đi vào native method, JVM truyền JNIEnv tương ứng với **thread hiện tại**.

Ví dụ các thao tác qua JNIEnv gồm tìm class, tạo reference, đọc field, gọi method hoặc kiểm tra exception:

~~~c
jclass cls = (*env)->GetObjectClass(env, obj);
jmethodID mid = (*env)->GetMethodID(env, cls, "name", "()Ljava/lang/String;");
jstring value = (jstring)(*env)->CallObjectMethod(env, obj, mid);
~~~

JNIEnv không phải một global handle có thể chia sẻ tùy ý giữa các thread. JNI Specification quy định interface pointer này chỉ hợp lệ trong luồng hiện tại. Native code cần giữ JavaVM nếu muốn từ một native thread khác lấy JNIEnv phù hợp thông qua luồng GetEnv/attach.

Phần JavaVM, attach/detach và vòng đời embedding được học ở chương JNI Invocation API; chương này chỉ cần hiểu JNIEnv là access point của JNI function interface.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-types-data-boundary">Type mapping và data access qua JNI boundary</a>

<details>
<summary>Click for details</summary>

JNI định nghĩa type riêng để biểu diễn dữ liệu đi qua boundary. Primitive Java tương ứng với các type như jboolean, jint, jlong, jfloat; object/reference được biểu diễn qua jobject và các subtype như jstring, jclass, jarray.

Không nên cast tùy tiện giữa Java-facing data và C type chỉ vì kích thước “có vẻ giống nhau”. JNI type contract mới là nguồn đúng.

String và array cần chú ý hơn primitive:

- các API như GetStringUTFChars làm việc với Modified UTF-8 theo JNI contract và phải đi cùng ReleaseStringUTFChars;
- GetPrimitiveArrayElements có thể trả direct pointer hoặc một bản sao tùy phần triển khai; code phải gọi Release... theo contract;
- Get/Set...ArrayRegion phù hợp khi chỉ cần copy một vùng dữ liệu.

Một cách làm an toàn là giữ thời gian “mượn” pointer native ngắn nhất có thể, không lưu pointer qua lifetime không được bảo đảm và luôn ghép cặp thao tác acquire/release.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-references">Local reference, global reference và GC</a>

<details>
<summary>Click for details</summary>

Reference JNI không phải raw pointer tới Java object. JVM cần biết reference nào còn hợp lệ để GC và object movement vẫn hoạt động đúng.

**Local reference** là reference gắn với luồng và local frame JNI. Trong một Java → native method call, JVM tự giải phóng các local reference của call đó khi native method trả về Java. Với native thread được attach qua Invocation API và không có native-method return boundary tương ứng, local reference có thể tồn tại cho tới khi bị `DeleteLocalRef`, local frame bị pop, hoặc thread detach; vì vậy thread sống lâu vẫn phải giải phóng local reference chủ động để tránh làm đầy local-reference table.

**Global reference** được tạo bằng NewGlobalRef và tồn tại cho tới khi DeleteGlobalRef được gọi. Nó phù hợp khi trạng thái native cần giữ Java object **độc lập với local frame hoặc lifetime của thread hiện tại**, nhưng chính vì giữ object reachable nên quên xóa global reference tạo ra leak logic.

Weak global reference cho phép GC thu object. Vì trạng thái có thể thay đổi giữa một lần kiểm tra và lần dùng tiếp theo, không nên chỉ kiểm tra reference rồi dereference trực tiếp. Khi cần sử dụng referent, hãy **promote** weak global reference thành một strong local/global reference, chẳng hạn bằng `NewLocalRef`; nếu kết quả là `NULL` thì object đã bị thu, còn reference mạnh hợp lệ sẽ giữ object sống trong khoảng sử dụng đó.

Quy tắc thực tế:

~~~text
chỉ dùng trong local frame/current native call → local reference
cần giữ vượt local-frame/thread lifetime → global reference + giải phóng rõ ràng
không muốn giữ object sống → cân nhắc weak global reference
~~~

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-exception-boundary">Java exception và native error boundary</a>

<details>
<summary>Click for details</summary>

Exception Java đi qua JNI dưới dạng **trạng thái pending exception** của luồng hiện tại. Khi một JNI call kích hoạt exception, native code không nên tiếp tục gọi JNI functions một cách tùy ý như thể call trước đã thành công.

Các API thường dùng:

- ExceptionCheck hoặc ExceptionOccurred để kiểm tra;
- ExceptionDescribe để hỗ trợ chẩn đoán; **lưu ý API này mô tả rồi clear pending exception**, nên không dùng nếu mục tiêu là giữ nguyên exception để propagate;
- ExceptionClear khi native code thật sự muốn xử lý và không propagate exception đó;
- Throw hoặc ThrowNew để tạo exception Java từ native side.

Nếu mục tiêu là để bên gọi Java nhận exception, native code thường giải phóng tài nguyên native cần thiết rồi return mà không clear pending exception.

Ngược lại, native error code như errno hoặc status code của một C library **không tự biến thành Java exception**. Boundary layer cần quyết định mapping rõ ràng, ví dụ chuyển status không hợp lệ thành IllegalStateException hoặc domain-specific exception.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-thread-boundary">Native thread và JVM thread boundary</a>

<details>
<summary>Click for details</summary>

Khi một Java thread gọi native method, thread đó đã thuộc JVM và nhận JNIEnv hợp lệ cho chính nó. Trường hợp khác xảy ra khi native library tự tạo OS thread và thread đó muốn gọi Java: thread phải được attach vào JVM trước.

Điểm quan trọng:

- JNIEnv gắn với thread, không cache rồi dùng từ thread khác;
- native-created thread cần attach trước khi dùng JNI;
- native thread đã attach phải detach trước khi chính thread đó kết thúc; daemon status chỉ ảnh hưởng JVM shutdown semantics, không miễn yêu cầu detach;
- JNI reference và trạng thái native chia sẻ giữa thread vẫn cần concurrency discipline riêng.

~~~text
Java thread → native method
    JVM đã attach → có JNIEnv

native-created thread → muốn gọi Java
    attach → lấy JNIEnv → gọi JNI → detach
~~~

Chi tiết GetEnv, AttachCurrentThread, AttachCurrentThreadAsDaemon và DetachCurrentThread thuộc chương Invocation API kế tiếp. Ở đây mục tiêu là hiểu rằng thread identity là một phần của JNI boundary.

Chương Invocation API sẽ mở rộng chính boundary đó từ một native method đơn lẻ thành lifecycle của native application khi host JVM, bao gồm startup, attach/detach và shutdown.

</details>

- [Quay lại đầu trang](#back-to-top)
