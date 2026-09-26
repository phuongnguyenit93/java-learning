# equals và hashCode

`equals` và `hashCode` không nên được thiết kế độc lập. Với hash-based collection, chúng tạo thành **một hợp đồng chung**: hash giúp tìm candidate, còn equals quyết định object có thực sự khớp hay không.

## <a id="equals-hashcode-consistency">equals và hashCode</a>

Nếu hai object equal nhưng trả hash khác nhau, `HashMap`/`HashSet` có thể tìm chúng ở hai vùng khác nhau và không bao giờ đi tới bước so `equals`.

Vì vậy khi override `equals`, gần như luôn cần xem lại `hashCode` cùng lúc.

```text
equals == true
→ hashCode bắt buộc bằng nhau

hashCode bằng nhau
→ chưa đủ kết luận equals == true
```

## <a id="hash-collection-lookup">Cách Hash Collection tìm phần tử</a>

Ở mức mô hình tư duy, có thể hiểu lookup như sau:

```text
hashCode
→ thu hẹp vùng/bucket cần tìm
        ↓
equals
→ xác nhận logical match trong nhóm candidate
```

cách triển khai thực tế của `HashMap` hiện đại có thêm nhiều chi tiết tối ưu collision, nhưng mô hình tư duy trên là phần hợp đồng mà object của bạn phải tôn trọng.

## <a id="broken-contract-effects">Hệ quả khi hợp đồng bị phá</a>

Nếu equal object có hash khác nhau, ta có thể thấy những hiện tượng như:

- `HashSet` trông như chứa duplicate;
- `HashMap.get(equalKey)` không tìm thấy entry đã insert;
- `contains` trả false dù một object logically equal đang tồn tại.

Đây không phải bug của collection. Collection đang vận hành đúng trên assumption rằng key giữ hợp đồng.

Sau equality và hashing, chương tiếp theo chuyển sang một hợp đồng khác: **object nên tự mô tả mình ra sao cho con người, log và công cụ debug?**
