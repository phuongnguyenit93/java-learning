<a id="back-to-top"></a>

# Tương thích phiên bản giữa Host và Plugin

## Menu
- [Compatibility Contract giữa Host và Plugin](#compatibility-contract)
- [Tiến hóa SPI mà không phá Provider hiện có](#spi-evolution-rules)
- [Version Negotiation giữa Host và Provider](#host-provider-version-negotiation)
- [Dependency Version Conflict](#dependency-version-conflicts)
- [Capability Versioning thay vì chỉ so sánh Version Number](#capability-versioning)
- [Kiểm thử Compatibility Matrix](#compatibility-testing)

## <a id="compatibility-contract">Compatibility Contract giữa Host và Plugin</a>

<details>
<summary>Click for details</summary>

Compatibility contract là tập giả định mà host và plugin cùng dựa vào để có thể chạy với nhau qua nhiều phiên bản.

Nó rộng hơn việc “cùng compile”:

```text
binary compatibility
→ class/method signature vẫn link được

source compatibility
→ provider source cũ vẫn compile lại được

semantic compatibility
→ cùng API nhưng hành vi/ý nghĩa không đổi theo cách phá host

configuration/data compatibility
→ plugin vẫn hiểu config/data contract cần thiết
```

Ví dụ thêm một method vào SPI có thể ảnh hưởng source/binary compatibility; đổi meaning của `supports()` mà giữ nguyên signature lại là semantic break.

Host-plugin contract nên ghi rõ:

- API version hoặc capability version;
- giả định về vòng đời;
- quy tắc về thread và tài nguyên;
- hành vi khi xảy ra lỗi;
- required vs optional features.

`ServiceLoader` không kiểm tra compatibility ở mức ứng dụng. Loader chỉ xác nhận provider phù hợp contract nạp service của platform; ứng dụng vẫn phải tự kiểm tra compatibility mà nó yêu cầu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spi-evolution-rules">Tiến hóa SPI mà không phá Provider hiện có</a>

<details>
<summary>Click for details</summary>

SPI evolution nên ưu tiên thay đổi mà provider cũ vẫn có thể hoạt động hợp lý.

Để cô lập bài toán evolution, ví dụ sau dùng một **biến thể rút gọn** của `ReportExporter` rồi thêm capability tùy chọn có hành vi mặc định hợp lý; đây là evolution sketch, không phải signature hiện tại của running example:

```java
public interface ReportExporter {
    void export(Report report);

    default boolean supportsStreaming() {
        return false;
    }
}
```

Default method có thể giảm source/binary disruption, nhưng chỉ dùng khi default semantic thật sự đúng. Một default “giả” có thể che lỗi compatibility.

Các strategy khác:

- thêm interface capability riêng (`StreamingExporter`);
- thêm method vào version mới của SPI và support song song hai contract trong giai đoạn migration;
- giữ DTO backward-compatible;
- deprecate trước khi remove;
- tránh thay đổi exception/`null` semantics âm thầm.

Đừng ép kiểu về class triển khai để “mở rộng” SPI ngoài contract. Nếu capability mới đủ quan trọng cho host, hãy biểu diễn nó rõ ràng trong contract.

Mọi evolution rule nên được contract test với provider cũ, không chỉ dựa vào code review.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="host-provider-version-negotiation">Version Negotiation giữa Host và Provider</a>

<details>
<summary>Click for details</summary>

Version negotiation xảy ra khi host và plugin cần xác định chúng có thể làm việc cùng nhau ở mức capability nào.

Một contract đơn giản có thể expose version:

```java
interface PluginDescriptor {
    int apiMajor();
    int apiMinor();
}
```

Host có thể áp dụng chính sách:

```text
major khác
→ reject

major giống, minor thấp hơn
→ cho phép nếu capability bắt buộc vẫn có
```

Nhưng version number tự nó không đủ. Hai plugin cùng `2.1` vẫn có thể khác capability hoặc yêu cầu cấu hình.

Vì vậy negotiation thường tốt hơn khi kết hợp:

```text
contract version
+ capability set
+ environment constraint
+ chính sách của host
```

Validation nên diễn ra trước activation để tránh plugin chỉ fail khi request đầu tiên đi vào.

Cách biểu diễn version (SemVer hay scheme riêng) là quyết định của ứng dụng; Java platform không cung cấp một chính sách host-plugin negotiation dùng chung cho mọi hệ thống.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dependency-version-conflicts">Dependency Version Conflict</a>

<details>
<summary>Click for details</summary>

Dependency conflict xảy ra khi plugin và host/plugin khác cần version incompatible của cùng library.

Ví dụ:

```text
host → library X 1.x
plugin A → library X 1.x
plugin B → library X 2.x
```

Nếu tất cả cùng một class loader, runtime chỉ resolve một class definition theo loader/class-path rules; plugin có thể gặp `NoSuchMethodError`, `NoClassDefFoundError` hoặc hành vi sai.

Các hướng xử lý:

- align dependency version toàn hệ thống;
- shade/relocate library private;
- isolate plugin bằng loader riêng;
- đưa shared API ra contract module tối thiểu;
- tránh truyền type của thư viện private qua ranh giới plugin.

Cô lập không tự động giải quyết mọi conflict: type cần trao đổi qua ranh giới vẫn phải có identity tương thích.

Dependency conflict là lý do kiến trúc plugin phải thiết kế chính sách shared/private dependency từ đầu, không đợi tới lỗi production mới thêm custom class loader.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="capability-versioning">Capability Versioning thay vì chỉ so sánh Version Number</a>

<details>
<summary>Click for details</summary>

Capability versioning tập trung vào **provider làm được gì**, thay vì chỉ “provider phiên bản bao nhiêu”.

Ví dụ:

```java
enum ExportCapability {
    PDF_A,
    STREAMING,
    ENCRYPTION
}

Set<ExportCapability> capabilities();
```

Host có thể yêu cầu:

```text
request cần PDF_A + ENCRYPTION
→ filter provider theo capability
→ rồi mới apply version/priority policy
```

Cách này hữu ích khi feature được rollout không hoàn toàn trùng với release number hoặc nhiều provider implement subset khác nhau.

Không nên version từng method một cách quá chi tiết. Mục tiêu là model những compatibility dimension host thực sự cần quyết định.

Kết hợp capability + contract version giúp migration mềm hơn: provider cũ có thể tiếp tục phục vụ request cơ bản, còn request cần feature mới được route tới provider mới.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compatibility-testing">Kiểm thử Compatibility Matrix</a>

<details>
<summary>Click for details</summary>

Compatibility phải được test như một **matrix**, vì lỗi thường chỉ xuất hiện ở tổ hợp host/provider cụ thể.

Ví dụ:

```text
Host 2.0 × Plugin 1.8 → supported
Host 2.0 × Plugin 2.0 → supported
Host 2.0 × Plugin 3.0 → rejected
```

Test nên bao gồm:

- discovery từ artifact thật;
- contract method invocation;
- capability negotiation;
- lifecycle start/stop;
- old provider với new host;
- new provider với minimum supported host;
- dependency/class-loader isolation nếu có;
- invalid/incompatible provider phải bị reject rõ.

Contract test có thể chạy cho mọi provider implementation dùng cùng test suite.

Ngoài unit test, hãy có integration test dùng JAR/module packaging thật vì lỗi `META-INF/services`, module descriptor và class-loader visibility không xuất hiện nếu test chỉ `new Provider()` trực tiếp.

</details>

- [Quay lại đầu trang](#back-to-top)
