# equals và hashCode

## <a id="equals-hashcode-consistency">Object equal phải có cùng hash code</a>
Override `equals` mà không có compatible `hashCode` sẽ vi phạm contract của `HashMap`, `HashSet`, cache và nhiều framework. Chiều ngược lại không bắt buộc: same hash không suy ra equality.

## <a id="hash-collection-lookup">Boundary mental model lookup của HashMap/HashSet</a>
Về concept, hash collection dùng hash để thu hẹp candidate location rồi dùng equality xác nhận key/element. Implementation hiện đại có thêm collision detail, nhưng invariant cần nhớ là hash thu hẹp, equals quyết định logical match.

## <a id="broken-contract-effects">Hệ quả observable khi contract bị phá</a>
Nếu equal object trả hash khác nhau, `HashSet` có thể trông như chứa duplicate và `HashMap` lookup bằng equal key có thể miss entry đã lưu. Đây không phải collection bug; collection đang dựa vào key contract sai.
