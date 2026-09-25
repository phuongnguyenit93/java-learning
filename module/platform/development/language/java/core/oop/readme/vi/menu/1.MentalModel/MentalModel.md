# Mental Model OOP

## <a id="oop-object-collaboration">Object như state + behavior hợp tác</a>
Object-oriented design model system bằng object có responsibility, state và behavior, hợp tác qua method call. Câu hỏi hữu ích không chỉ là “có field nào?” mà là “object nào sở hữu rule này và bảo vệ state này?”.

## <a id="oop-boundaries">Responsibility và boundary</a>
Class boundary nên ẩn implementation detail và expose behavior giữ invariant. Cohesion tăng khi state/rule liên quan nằm cùng nơi; coupling tăng khi nhiều object biết internal detail của nhau. OOP tốt không phải tạo thật nhiều class mà là chia responsibility dễ hiểu.

## <a id="oop-vs-procedural">Trade-off OOP và procedural decomposition</a>
OOP tổ chức theo object/responsibility; procedural decomposition tổ chức theo operation/data flow. Không style nào luôn tốt hơn. Hãy chọn decomposition làm change localized và behavior dễ reasoning. Java hỗ trợ cả hai và code hiện đại thường mix có chủ ý.
