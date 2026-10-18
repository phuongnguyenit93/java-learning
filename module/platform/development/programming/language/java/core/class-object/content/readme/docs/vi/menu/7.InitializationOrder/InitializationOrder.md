# Thứ tự khởi tạo

Nhiều lỗi trong quá trình khởi tạo xuất phát từ việc “đúng mã nhưng sai thời điểm”. Java có thứ tự rõ ràng cho **khởi tạo lớp** và **khởi tạo từng đối tượng**; phân biệt hai quá trình này giúp tránh hiểu nhầm rằng phần `static` chạy lại cho mỗi lần `new`.

## <a id="class-initialization-order">Thứ tự khởi tạo lớp</a>

Khi một lớp cần được khởi tạo, lớp cha được khởi tạo trước nếu cần, sau đó phần khởi tạo trường `static` và khối `static` của lớp chạy theo thứ tự xuất hiện trong mã nguồn.

```text
lớp cha được khởi tạo nếu cần
        ↓
trường/khối static của lớp con theo thứ tự trong mã nguồn
```

Chi tiết về nạp lớp, liên kết và khởi tạo ở mức JVM thuộc mô-đun `classloader`; ở đây chỉ cần mô hình tư duy về thời điểm trạng thái `static` trở nên sẵn sàng.

## <a id="instance-initialization-order">Thứ tự khởi tạo từng đối tượng</a>

Trong một lớp, phần khởi tạo trường của đối tượng và khối khởi tạo đối tượng chạy theo thứ tự xuất hiện trong mã nguồn trước thân hàm khởi tạo của lớp đó.

```text
trạng thái mặc định 0/null/false
→ khởi tạo trường / khối khởi tạo đối tượng
→ thân hàm khởi tạo
```

Nhưng phần thuộc lớp cha vẫn phải được khởi tạo trước phần thuộc lớp con.

## <a id="inheritance-initialization-order">Thứ tự khởi tạo khi có kế thừa</a>

Với `new Child()`:

```text
khởi tạo lớp nếu cần
        ↓
khởi tạo phần đối tượng Parent
        ↓
thân hàm khởi tạo Parent
        ↓
khởi tạo phần đối tượng Child
        ↓
thân hàm khởi tạo Child
```

Hiểu thứ tự này giải thích vì sao gọi phương thức có thể bị ghi đè quá sớm trong hàm khởi tạo nguy hiểm: phương thức của lớp con có thể chạy trước khi các trường của lớp con được khởi tạo như mong đợi.

Có thể quan sát thứ tự đó bằng một đoạn ghi vết nhỏ:

```java
class Trace {
    static int log(String step) {
        System.out.println(step);
        return 0;
    }
}

class Parent {
    int parentField = Trace.log("parent field");
    { Trace.log("parent block"); }
    Parent() { Trace.log("parent constructor"); }
}

class Child extends Parent {
    int childField = Trace.log("child field");
    { Trace.log("child block"); }
    Child() { Trace.log("child constructor"); }
}
```

Nếu lớp đã được khởi tạo trước đó, `new Child()` sẽ in các bước trường/khối/hàm khởi tạo của `Parent` trước rồi mới tới các bước tương ứng của `Child`. Đoạn mã này là minh chứng cho thứ tự ở trên, không thay thế cho việc hiểu quy tắc.

Chương tiếp theo nhìn toàn bộ quá trình tạo đối tượng và rủi ro khi `this` bị chia sẻ ra ngoài quá sớm.
