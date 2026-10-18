<a id="back-to-top"></a>

# Phụ thuộc khi thay đổi và mức độ độc lập phát hành

## Menu
- [Coupling khi sửa mã so với coupling khi phát hành](#change-coupling-vs-release-coupling)
- [Lợi ích và điều kiện của phát hành độc lập](#why-independent-release-matters)
- [Khả năng phát hành độc lập từng thành phần trong monorepo](#monorepo-can-release-independently)
- [Phụ thuộc phát hành vẫn tồn tại trong polyrepo](#polyrepo-can-be-release-coupled)
- [Rủi ro lịch phát hành khi thay đổi interface hoặc thư viện](#shared-interface-upgrade-release-risks)
- [Phạm vi kiểm chứng và tiêu chí sẵn sàng phát hành](#validation-boundaries-and-readiness)
- [Đánh đổi giữa nhịp phát hành khác nhau và chi phí phối hợp](#release-cadence-and-team-coordination)
- [Tình huống đối chiếu phát hành phối hợp và độc lập](#analyze-release-independence-scenario)

## <a id="change-coupling-vs-release-coupling">Coupling khi sửa mã so với coupling khi phát hành</a>

<details>
<summary>Xem chi tiết</summary>

**Change coupling** nghĩa một yêu cầu thường buộc sửa nhiều thành phần; **release coupling** nghĩa các thành phần phải được đưa ra môi trường theo một mốc hoặc thứ tự nhất định. Hai loại có liên quan nhưng khác nhau. Monorepo cho phép commit nhiều thành phần cùng lúc nhưng có thể phát hành từng service; polyrepo tách commits nhưng có thể phải triển khai chung vì interface phá vỡ.

Ví dụ API và web cùng sửa schema (change coupling), nhưng API hỗ trợ đồng thời schema cũ/mới nên web phát hành sau vài ngày (release decoupling). Đo cả số lần sửa chung lẫn số deployment buộc đồng bộ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="why-independent-release-matters">Lợi ích và điều kiện của phát hành độc lập</a>

<details>
<summary>Xem chi tiết</summary>

**Phát hành độc lập** giúp sửa lỗi hoặc đưa tính năng của một thành phần ra mà không phải chờ mọi thành phần khác. Điều kiện là dependency contract đủ ổn định, test/sẵn sàng phát hành xác định theo thành phần, và có khả năng triển khai, giám sát, rollback phù hợp. Chỉ tách repo không tạo các điều kiện này.

Ví dụ lỗi UI cần hotfix nhưng thanh toán không thay đổi: hệ thống nên đưa bản UI mới mà không buộc redeploy payment. Nếu UI phụ thuộc thay đổi API chưa có, việc độc lập chỉ là tên gọi; cần bảo đảm backward compatibility.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="monorepo-can-release-independently">Khả năng phát hành độc lập từng thành phần trong monorepo</a>

<details>
<summary>Xem chi tiết</summary>

Monorepo là **một lịch sử mã nguồn**, không bắt buộc **một artifact hoặc một nút Release**. Nếu `/services/catalog` và `/services/payments` có ranh giới build/deploy riêng, cùng commit SHA vẫn có thể tạo hai release khác thời điểm; cần biết phiên bản mỗi component được build từ phần code nào.

Dấu hiệu kiểm chứng là metadata release ghi component, source revision và kết quả test riêng; không phải chỉ vì PR chung mà cả hai phải lên production. Chi tiết pipeline chọn phạm vi và build artifact thuộc module CI/build, ở đây chỉ xác định yêu cầu chiến lược.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="polyrepo-can-be-release-coupled">Phụ thuộc phát hành vẫn tồn tại trong polyrepo</a>

<details>
<summary>Xem chi tiết</summary>

Polyrepo tạo **lịch sử và quyền riêng** chứ không loại bỏ phụ thuộc runtime. Nếu `orders-api.git` trả trường mới bắt buộc và `orders-web.git` không hiểu trường cũ, hai nhóm vẫn phải phối hợp thứ tự triển khai hoặc tạo lớp tương thích. Một repo cho mỗi microservice không đồng nghĩa **deploy độc lập trong thực tế**.

Thu thập các đợt release phải đi theo cặp, sự cố 400/500 vì lệch contract và số lần rollback dây chuyền. Nếu coupling là do giao diện, tách thêm repository chỉ làm tăng các điểm phải phối hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="shared-interface-upgrade-release-risks">Rủi ro lịch phát hành khi thay đổi interface hoặc thư viện</a>

<details>
<summary>Xem chi tiết</summary>

Thay đổi **shared interface** có thể gây release skew: service cung cấp đã lên bản mới, consumer vẫn dùng bản cũ; hoặc consumer lên trước khi provider có API cần thiết. Không chỉ kiểm tra compile: cần xét JSON schema, feature flag, database contract, thời gian hỗ trợ phiên bản cũ và khả năng rollback qua nhiều phiên bản.

Ví dụ thư viện payment v3 bỏ phương thức cũ: nếu API consumer chưa sẵn sàng, rollout v3 sẽ làm gián đoạn. Kế hoạch an toàn nêu trình tự provider mở rộng contract → consumer chuyển → provider thu hẹp, cùng bằng chứng adoption.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="validation-boundaries-and-readiness">Phạm vi kiểm chứng và tiêu chí sẵn sàng phát hành</a>

<details>
<summary>Xem chi tiết</summary>

**Validation boundary** cho biết phần nào phải được kiểm tra khi một dependency thay đổi; **release readiness** kết luận thành phần có thể phát hành dựa trên test, contract, review và rủi ro còn lại. Monorepo có thể tính tập components bị ảnh hưởng; polyrepo có thể chạy kiểm chứng contract/version tại mỗi repo. Việc công cụ dùng lệnh gì là bài học khác.

Ví dụ đổi DTO dùng chung: hãy biết consumers API/web nào cần test, có test contract cross-version hay không và ai phê duyệt release. “Chỉ module library build xanh” không đủ bằng chứng sẵn sàng cho toàn bộ migration.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="release-cadence-and-team-coordination">Đánh đổi giữa nhịp phát hành khác nhau và chi phí phối hợp</a>

<details>
<summary>Xem chi tiết</summary>

Các nhóm có **release cadence** khác nhau: mobile có chu kỳ phát hành chậm, API phát hành mỗi ngày, thư viện ổn định theo tháng. Repository topology chỉ thay đổi độ dễ phối hợp source; để nhịp khác nhau bền vững cần contract ổn định và giao tiếp về deprecation. Bắt mọi team cùng cadence vì chung repo có thể tạo bottleneck; tách repo cũng không bảo vệ client khỏi API breaking.

Dùng bảng version support và thời gian đợi change để lượng hóa chi phí. Ưu tiên decouple bằng contract trước khi thay topology do lịch release lệch nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="analyze-release-independence-scenario">Tình huống đối chiếu phát hành phối hợp và độc lập</a>

<details>
<summary>Xem chi tiết</summary>

So sánh tình huống API–web cùng cần sửa thuế. **Kịch bản A:** API thêm field mới nhưng giữ field cũ; web đổi sau một tuần, hai release độc lập được dù cùng monorepo. **Kịch bản B:** API xóa field ngay; web phải deploy cùng lúc dù hai polyrepos. Khác biệt quyết định nằm ở **compatibility**, không phải nơi đặt `.git`.

Bằng chứng: source revision từng component, thời điểm deployment, ma trận client/server versions và lỗi thực tế. Kết luận chỉ dựa trên repo count dễ dẫn đến quyết định sai về autonomy.

</details>

- [Quay lại đầu trang](#back-to-top)
