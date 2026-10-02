# Exception

Module này xem exception như một phần của **failure contract và control flow**, không chỉ là cú pháp `try/catch`. Mục tiêu là biết failure nào nên được throw, propagate, translate, handle hoặc cleanup tại boundary nào.

## Learning flow

1. vì sao exception tồn tại;
2. `Throwable` model;
3. checked và unchecked exception contracts;
4. throwing và propagation;
5. handling và cleanup;
6. resource-safe failure;
7. custom exception và context;
8. exception boundary design;
9. tổng hợp failure model.

## Mental model cần giữ

Catch càng sớm không đồng nghĩa với design càng tốt. Một layer chỉ nên xử lý failure khi nó có đủ context để recover, translate hoặc quyết định policy. Resource cleanup phải độc lập với việc operation thành công hay thất bại.

Module tập trung vào Java exception semantics; logging, HTTP error mapping hoặc framework-specific exception handling thuộc layer/module cao hơn.
