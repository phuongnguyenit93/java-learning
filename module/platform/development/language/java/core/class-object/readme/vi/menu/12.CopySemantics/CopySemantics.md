# Copy Semantics, Shallow Copy và Deep Copy

## <a id="reference-copy">Copy reference</a>
Assign object reference sang variable khác chỉ copy reference value. Không tạo domain object mới; cả hai biến nhận diện cùng object. Đây là default behavior của assignment và parameter passing cho reference value.

## <a id="shallow-copy">Shallow copy</a>
Shallow copy tạo outer object mới nhưng copy field value nguyên trạng. Primitive field độc lập, còn referenced mutable object vẫn shared. Vì vậy mutate nested list/address sau shallow copy có thể thấy từ cả hai outer object.

## <a id="deep-copy">Deep copy</a>
Deep copy duplicate mutable object graph đến độ sâu mà ownership contract yêu cầu. “Deep” phụ thuộc domain: immutable object thường có thể share, cycle cần xử lý và external resource thường không thể copy có ý nghĩa.

## <a id="copy-strategies">Copy constructor/factory/manual mapping</a>
Ưu tiên explicit copy constructor/factory hoặc mapping code thể hiện rõ state nào share, state nào duplicate. `Cloneable`/serialization trick dễ che policy và bypass invariant. Copy API tốt document ownership, depth, identity và derived/transient state.
